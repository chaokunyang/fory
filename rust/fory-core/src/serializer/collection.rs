// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

use crate::ensure;
use crate::error::Error;
use crate::resolver::context::ReadContext;
use crate::resolver::context::WriteContext;
use crate::serializer::{ForyDefault, Serializer};
use crate::types::{need_to_write_type_for_field, RefFlag, PRIMITIVE_ARRAY_TYPES};

const TRACKING_REF: u8 = 0b1;

pub const HAS_NULL: u8 = 0b10;

// Whether collection elements type is declare type.
const DECL_ELEMENT_TYPE: u8 = 0b100;

//  Whether collection elements type same.
pub const IS_SAME_TYPE: u8 = 0b1000;

pub fn write_collection_type_info(
    context: &mut WriteContext,
    collection_type_id: u32,
) -> Result<(), Error> {
    context.writer.write_varuint32(collection_type_id);
    Ok(())
}

pub fn write_collection_data<'a, T, I>(
    iter: I,
    context: &mut WriteContext,
    has_generics: bool,
) -> Result<(), Error>
where
    T: Serializer + 'a,
    I: IntoIterator<Item = &'a T>,
    I::IntoIter: ExactSizeIterator + Clone,
{
    let iter = iter.into_iter();
    let len = iter.len();
    context.writer.write_varuint32(len as u32);
    if len == 0 {
        return Ok(());
    }
    if T::fory_is_polymorphic() || T::fory_is_shared_ref() {
        return write_collection_data_dyn_ref(iter, context, has_generics);
    }
    let mut header = IS_SAME_TYPE;
    let mut has_null = false;
    let elem_static_type_id = T::fory_static_type_id();
    let is_elem_declared = has_generics && !need_to_write_type_for_field(elem_static_type_id);
    if T::fory_is_option() {
        // iter.clone() is zero-copy
        for item in iter.clone() {
            if item.fory_is_none() {
                has_null = true;
                break;
            }
        }
    }
    if has_null {
        header |= HAS_NULL;
    }
    if is_elem_declared {
        header |= DECL_ELEMENT_TYPE;
        context.writer.write_u8(header);
    } else {
        context.writer.write_u8(header);
        T::fory_write_type_info(context)?;
    }
    if !has_null {
        for item in iter {
            item.fory_write_data_generic(context, has_generics)?;
        }
    } else {
        for item in iter {
            if item.fory_is_none() {
                context.writer.write_u8(RefFlag::Null as u8);
                continue;
            }
            context.writer.write_u8(RefFlag::NotNullValue as u8);
            item.fory_write_data_generic(context, has_generics)?;
        }
    }

    Ok(())
}

/// Slow but versatile collection serialization for dynamic trait object and shared/circular reference.
pub fn write_collection_data_dyn_ref<'a, T, I>(
    iter: I,
    context: &mut WriteContext,
    has_generics: bool,
) -> Result<(), Error>
where
    T: Serializer + 'a,
    I: IntoIterator<Item = &'a T>,
    I::IntoIter: ExactSizeIterator + Clone,
{
    let elem_static_type_id = T::fory_static_type_id();
    let is_elem_declared = has_generics && !need_to_write_type_for_field(elem_static_type_id);
    let elem_is_polymorphic = T::fory_is_polymorphic();
    let elem_is_shared_ref = T::fory_is_shared_ref();

    let iter = iter.into_iter();
    let mut header = 0u8;
    let mut has_null = false;
    let mut is_same_type = true;
    let mut first_type_id: Option<std::any::TypeId> = None;

    // Analyze elements to determine header flags
    for item in iter.clone() {
        if item.fory_is_none() {
            has_null = true;
        } else if elem_is_polymorphic && is_same_type {
            let type_id = item.fory_concrete_type_id();
            if let Some(first_id) = first_type_id {
                if first_id != type_id {
                    is_same_type = false;
                }
            } else {
                first_type_id = Some(type_id);
            }
        }
    }

    // Set header flags
    if has_null {
        header |= HAS_NULL;
    }
    if is_same_type {
        header |= IS_SAME_TYPE;
    }
    if elem_is_shared_ref {
        header |= TRACKING_REF;
    }
    if is_elem_declared && !elem_is_polymorphic {
        header |= DECL_ELEMENT_TYPE;
        // Write header
        context.writer.write_u8(header);
        if elem_is_polymorphic {
            if let Some(type_id) = first_type_id {
                context.write_any_typeinfo(type_id)?;
            } else {
                // All elements are null, write any simple type as stub
                i8::fory_write_type_info(context)?;
            }
        } else {
            T::fory_write_type_info(context)?;
        }
    } else {
        // Write header
        context.writer.write_u8(header);
    }
    // Write elements data
    if is_same_type {
        // All elements are same type
        if !has_null {
            // No null elements
            if elem_is_shared_ref {
                for item in iter {
                    item.fory_write(context, true, false, has_generics)?;
                }
            } else {
                for item in iter {
                    item.fory_write_data_generic(context, has_generics)?;
                }
            }
        } else {
            // Has null elements
            for item in iter {
                item.fory_write(context, true, false, has_generics)?;
            }
        }
    } else {
        // Different types (polymorphic elements with different types)
        if !has_null {
            // No null elements
            if elem_is_shared_ref {
                for item in iter {
                    item.fory_write(context, true, true, has_generics)?;
                }
            } else {
                for item in iter {
                    item.fory_write(context, false, true, has_generics)?;
                }
            }
        } else {
            // Has null elements
            for item in iter {
                item.fory_write(context, true, true, has_generics)?;
            }
        }
    }

    Ok(())
}

pub fn read_collection_type_info(
    context: &mut ReadContext,
    collection_type_id: u32,
) -> Result<(), Error> {
    let remote_collection_type_id = context.reader.read_varuint32()?;
    if PRIMITIVE_ARRAY_TYPES.contains(&remote_collection_type_id) {
        return Err(Error::type_error(
            "Vec<number> belongs to the `number_array` type, \
            and Vec<Option<number>> belongs to the `list` type. \
            You should not read data of type `number_array` as data of type `list`.",
        ));
    }
    ensure!(
        collection_type_id == remote_collection_type_id,
        Error::type_mismatch(collection_type_id, remote_collection_type_id)
    );
    Ok(())
}

pub fn read_collection_data<C, T>(context: &mut ReadContext) -> Result<C, Error>
where
    T: Serializer + ForyDefault,
    C: FromIterator<T>,
{
    let len = context.reader.read_varuint32()?;
    if len == 0 {
        return Ok(C::from_iter(std::iter::empty()));
    }
    if T::fory_is_polymorphic() || T::fory_is_shared_ref() {
        return read_collection_data_dyn_ref(context, len);
    }
    let header = context.reader.read_u8()?;
    let declared = (header & DECL_ELEMENT_TYPE) != 0;
    if !declared {
        // context.read_any_typeinfo();
        // TODO check whether type info consistent with T
        T::fory_read_type_info(context)?;
    }
    let has_null = (header & HAS_NULL) != 0;
    ensure!(
        (header & IS_SAME_TYPE) != 0,
        Error::type_error("Type inconsistent, target type is not polymorphic")
    );
    if !has_null {
        (0..len)
            .map(|_| T::fory_read_data(context))
            .collect::<Result<C, Error>>()
    } else {
        (0..len)
            .map(|_| {
                let flag = context.reader.read_i8()?;
                if flag == RefFlag::Null as i8 {
                    return Ok(T::fory_default());
                }
                T::fory_read_data(context)
            })
            .collect::<Result<C, Error>>()
    }
}

/// Slow but versatile collection deserialization for dynamic trait object and shared/circular reference.
pub fn read_collection_data_dyn_ref<C, T>(context: &mut ReadContext, len: u32) -> Result<C, Error>
where
    T: Serializer + ForyDefault,
    C: FromIterator<T>,
{
    // Read header
    let header = context.reader.read_u8()?;
    let is_track_ref = (header & TRACKING_REF) != 0;
    let is_same_type = (header & IS_SAME_TYPE) != 0;
    let has_null = (header & HAS_NULL) != 0;
    let is_declared = (header & DECL_ELEMENT_TYPE) != 0;
    // Read elements
    if is_same_type {
        let type_info = if !is_declared {
            context.read_any_typeinfo()?
        } else {
            let rs_type_id = std::any::TypeId::of::<T>();
            context.get_type_resolver().get_type_info(&rs_type_id)?
        };
        // All elements are same type
        if is_track_ref {
            (0..len)
                .map(|_| T::fory_read_with_type_info(context, true, type_info.clone()))
                .collect::<Result<C, Error>>()
        } else {
            if !has_null {
                // No null elements
                (0..len)
                    .map(|_| T::fory_read_with_type_info(context, false, type_info.clone()))
                    .collect::<Result<C, Error>>()
            } else {
                // Has null elements
                (0..len)
                    .map(|_| {
                        let flag = context.reader.read_i8()?;
                        if flag == RefFlag::Null as i8 {
                            Ok(T::fory_default())
                        } else {
                            T::fory_read_with_type_info(context, false, type_info.clone())
                        }
                    })
                    .collect::<Result<C, Error>>()
            }
        }
    } else {
        (0..len)
            .map(|_| T::fory_read(context, is_track_ref, true))
            .collect::<Result<C, Error>>()
    }
}
