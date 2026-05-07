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

use super::codec::{field_ref_mode, generic_field_type, same_numeric_family, Codec};
use crate::context::ReadContext;
use crate::error::Error;
use crate::meta::FieldType;
use crate::resolver::{RefFlag, RefMode};
use crate::serializer::{ForyDefault, Serializer};
use crate::type_id::{self, TypeId};
use crate::types::{bfloat16::bfloat16, float16::float16};

fn primitive_array_element_type_id(type_id: u32) -> Option<u32> {
    match type_id {
        type_id::BOOL_ARRAY => Some(type_id::BOOL),
        type_id::INT8_ARRAY => Some(type_id::INT8),
        type_id::INT16_ARRAY => Some(type_id::INT16),
        type_id::INT32_ARRAY => Some(type_id::INT32),
        type_id::INT64_ARRAY => Some(type_id::INT64),
        type_id::UINT8_ARRAY => Some(type_id::UINT8),
        type_id::UINT16_ARRAY => Some(type_id::UINT16),
        type_id::UINT32_ARRAY => Some(type_id::UINT32),
        type_id::UINT64_ARRAY => Some(type_id::UINT64),
        type_id::FLOAT16_ARRAY => Some(type_id::FLOAT16),
        type_id::BFLOAT16_ARRAY => Some(type_id::BFLOAT16),
        type_id::FLOAT32_ARRAY => Some(type_id::FLOAT32),
        type_id::FLOAT64_ARRAY => Some(type_id::FLOAT64),
        _ => None,
    }
}

fn primitive_array_element_size(type_id: u32) -> Option<usize> {
    match type_id {
        type_id::BOOL_ARRAY | type_id::INT8_ARRAY | type_id::UINT8_ARRAY => Some(1),
        type_id::INT16_ARRAY
        | type_id::UINT16_ARRAY
        | type_id::FLOAT16_ARRAY
        | type_id::BFLOAT16_ARRAY => Some(2),
        type_id::INT32_ARRAY | type_id::UINT32_ARRAY | type_id::FLOAT32_ARRAY => Some(4),
        type_id::INT64_ARRAY | type_id::UINT64_ARRAY | type_id::FLOAT64_ARRAY => Some(8),
        _ => None,
    }
}

fn primitive_array_type_matches_rust_type<T: 'static>(type_id: u32) -> bool {
    let rust_type = std::any::TypeId::of::<T>();
    match type_id {
        type_id::BOOL_ARRAY => rust_type == std::any::TypeId::of::<bool>(),
        type_id::INT8_ARRAY => rust_type == std::any::TypeId::of::<i8>(),
        type_id::INT16_ARRAY => rust_type == std::any::TypeId::of::<i16>(),
        type_id::INT32_ARRAY => rust_type == std::any::TypeId::of::<i32>(),
        type_id::INT64_ARRAY => rust_type == std::any::TypeId::of::<i64>(),
        type_id::UINT8_ARRAY => rust_type == std::any::TypeId::of::<u8>(),
        type_id::UINT16_ARRAY => rust_type == std::any::TypeId::of::<u16>(),
        type_id::UINT32_ARRAY => rust_type == std::any::TypeId::of::<u32>(),
        type_id::UINT64_ARRAY => rust_type == std::any::TypeId::of::<u64>(),
        type_id::FLOAT16_ARRAY => rust_type == std::any::TypeId::of::<float16>(),
        type_id::BFLOAT16_ARRAY => rust_type == std::any::TypeId::of::<bfloat16>(),
        type_id::FLOAT32_ARRAY => rust_type == std::any::TypeId::of::<f32>(),
        type_id::FLOAT64_ARRAY => rust_type == std::any::TypeId::of::<f64>(),
        _ => false,
    }
}

fn read_primitive_array_data_bulk<T: 'static>(
    context: &mut ReadContext,
    type_id: u32,
    size_bytes: usize,
    len: usize,
) -> Option<Result<Vec<T>, Error>> {
    if !primitive_array_type_matches_rust_type::<T>(type_id) {
        return None;
    }
    #[cfg(target_endian = "little")]
    {
        let mut vec: Vec<T> = Vec::with_capacity(len);
        let src = match context.reader.read_bytes(size_bytes) {
            Ok(src) => src,
            Err(error) => return Some(Err(error)),
        };
        unsafe {
            std::ptr::copy_nonoverlapping(src.as_ptr(), vec.as_mut_ptr() as *mut u8, size_bytes);
            vec.set_len(len);
        }
        Some(Ok(vec))
    }
    #[cfg(target_endian = "big")]
    {
        let _ = (context, size_bytes, len);
        None
    }
}

fn list_element_type_matches_array(list: &FieldType, array: &FieldType) -> bool {
    primitive_array_element_type_id(array.type_id).is_some_and(|element_type_id| {
        list.type_id == type_id::LIST
            && list.generics.len() == 1
            && primitive_array_element_type_matches(element_type_id, list.generics[0].type_id)
    })
}

fn primitive_array_element_type_matches(
    array_element_type_id: u32,
    list_element_type_id: u32,
) -> bool {
    array_element_type_id == list_element_type_id
        || same_numeric_family(array_element_type_id, list_element_type_id)
}

fn read_primitive_array_data_with_codec<T, C>(
    context: &mut ReadContext,
    remote_field_type: &FieldType,
) -> Result<Vec<T>, Error>
where
    T: 'static,
    C: Codec<T>,
{
    let size_bytes = context.reader.read_var_u32()? as usize;
    let elem_size = primitive_array_element_size(remote_field_type.type_id)
        .ok_or_else(|| Error::type_error("array-compatible field is not a primitive array"))?;
    if size_bytes % elem_size != 0 {
        return Err(Error::invalid_data("Invalid data length"));
    }
    let max = context.max_binary_size() as usize;
    if size_bytes > max {
        return Err(Error::size_limit_exceeded(format!(
            "Binary size {} exceeds limit {}",
            size_bytes, max
        )));
    }
    let remaining = context.reader.slice_after_cursor().len();
    if size_bytes > remaining {
        let cursor = context.reader.get_cursor();
        return Err(Error::buffer_out_of_bound(
            cursor,
            size_bytes,
            cursor + remaining,
        ));
    }
    let len = size_bytes / elem_size;
    let element_type_id = primitive_array_element_type_id(remote_field_type.type_id)
        .ok_or_else(|| Error::type_error("array-compatible field is not a primitive array"))?;
    if let Some(result) =
        read_primitive_array_data_bulk::<T>(context, remote_field_type.type_id, size_bytes, len)
    {
        return result;
    }
    let element_type = FieldType::new(element_type_id, false, Vec::new());
    let mut vec = Vec::with_capacity(len);
    for _ in 0..len {
        vec.push(C::read_data_with_type(context, &element_type)?);
    }
    Ok(vec)
}

pub(super) trait CompatibleListArrayElement: Serializer + ForyDefault {
    fn read_list_array_element(
        context: &mut ReadContext,
        remote_type_id: u32,
    ) -> Result<Self, Error>;
}

macro_rules! compatible_exact_element {
    ($ty:ty, $type_id:expr, $reader:ident) => {
        impl CompatibleListArrayElement for $ty {
            #[inline(always)]
            fn read_list_array_element(
                context: &mut ReadContext,
                remote_type_id: u32,
            ) -> Result<Self, Error> {
                if remote_type_id == $type_id {
                    context.reader.$reader()
                } else {
                    Err(Error::type_mismatch(
                        <$ty as Serializer>::fory_static_type_id() as u32,
                        remote_type_id,
                    ))
                }
            }
        }
    };
}

macro_rules! compatible_integer_element {
    ($ty:ty, $fixed_type:expr, $var_type:expr, $fixed_reader:ident, $var_reader:ident) => {
        impl CompatibleListArrayElement for $ty {
            #[inline(always)]
            fn read_list_array_element(
                context: &mut ReadContext,
                remote_type_id: u32,
            ) -> Result<Self, Error> {
                match remote_type_id {
                    x if x == $fixed_type => context.reader.$fixed_reader(),
                    x if x == $var_type => context.reader.$var_reader(),
                    _ => Err(Error::type_mismatch(
                        <$ty as Serializer>::fory_static_type_id() as u32,
                        remote_type_id,
                    )),
                }
            }
        }
    };
}

macro_rules! compatible_tagged_integer_element {
    (
        $ty:ty,
        $fixed_type:expr,
        $var_type:expr,
        $tagged_type:expr,
        $fixed_reader:ident,
        $var_reader:ident,
        $tagged_reader:ident
    ) => {
        impl CompatibleListArrayElement for $ty {
            #[inline(always)]
            fn read_list_array_element(
                context: &mut ReadContext,
                remote_type_id: u32,
            ) -> Result<Self, Error> {
                match remote_type_id {
                    x if x == $fixed_type => context.reader.$fixed_reader(),
                    x if x == $var_type => context.reader.$var_reader(),
                    x if x == $tagged_type => context.reader.$tagged_reader(),
                    _ => Err(Error::type_mismatch(
                        <$ty as Serializer>::fory_static_type_id() as u32,
                        remote_type_id,
                    )),
                }
            }
        }
    };
}

impl CompatibleListArrayElement for bool {
    #[inline(always)]
    fn read_list_array_element(
        context: &mut ReadContext,
        remote_type_id: u32,
    ) -> Result<Self, Error> {
        if remote_type_id == type_id::BOOL {
            Ok(context.reader.read_u8()? == 1)
        } else {
            Err(Error::type_mismatch(type_id::BOOL, remote_type_id))
        }
    }
}

compatible_exact_element!(i8, type_id::INT8, read_i8);
compatible_exact_element!(i16, type_id::INT16, read_i16);
compatible_integer_element!(
    i32,
    type_id::INT32,
    type_id::VARINT32,
    read_i32,
    read_var_i32
);
compatible_tagged_integer_element!(
    i64,
    type_id::INT64,
    type_id::VARINT64,
    type_id::TAGGED_INT64,
    read_i64,
    read_var_i64,
    read_tagged_i64
);
compatible_exact_element!(u8, type_id::UINT8, read_u8);
compatible_exact_element!(u16, type_id::UINT16, read_u16);
compatible_integer_element!(
    u32,
    type_id::UINT32,
    type_id::VAR_UINT32,
    read_u32,
    read_var_u32
);
compatible_tagged_integer_element!(
    u64,
    type_id::UINT64,
    type_id::VAR_UINT64,
    type_id::TAGGED_UINT64,
    read_u64,
    read_var_u64,
    read_tagged_u64
);
compatible_exact_element!(float16, type_id::FLOAT16, read_f16);
compatible_exact_element!(bfloat16, type_id::BFLOAT16, read_bf16);
compatible_exact_element!(f32, type_id::FLOAT32, read_f32);
compatible_exact_element!(f64, type_id::FLOAT64, read_f64);
compatible_exact_element!(i128, type_id::INT128, read_i128);
compatible_exact_element!(u128, TypeId::U128 as u32, read_u128);
compatible_exact_element!(isize, type_id::ISIZE, read_isize);
compatible_exact_element!(usize, type_id::USIZE, read_usize);

fn read_non_nullable_list_data_with_type<T>(
    context: &mut ReadContext,
    remote_field_type: &FieldType,
) -> Result<Vec<T>, Error>
where
    T: CompatibleListArrayElement,
{
    let element_type = generic_field_type(remote_field_type, 0, "list")?;
    let len = context.reader.read_var_u32()?;
    if len == 0 {
        return Ok(Vec::new());
    }
    let max = context.max_collection_size();
    if len > max {
        return Err(Error::size_limit_exceeded(format!(
            "Collection size {} exceeds limit {}",
            len, max
        )));
    }
    let header = context.reader.read_u8()?;
    if (header & super::codec::HAS_NULL) != 0 {
        return Err(Error::type_error(
            "compatible list to array field requires non-null elements",
        ));
    }
    if (header & super::codec::TRACKING_REF) != 0 {
        return Err(Error::type_error(
            "array-compatible list declares reference-tracked elements",
        ));
    }
    if (header & super::codec::IS_SAME_TYPE) == 0 {
        return Err(Error::type_error(
            "array-compatible list must declare same-type elements",
        ));
    }
    if (header & super::codec::DECL_ELEMENT_TYPE) == 0 {
        return Err(Error::type_error(
            "array-compatible list must declare element type",
        ));
    }
    let mut vec = Vec::with_capacity(len as usize);
    for _ in 0..len {
        vec.push(T::read_list_array_element(context, element_type.type_id)?);
    }
    Ok(vec)
}

#[cold]
#[inline(never)]
pub(super) fn read_vec_compatible_mismatch<T, C>(
    context: &mut ReadContext,
    local_field_type: &FieldType,
    remote_field_type: &FieldType,
) -> Result<Option<Vec<T>>, Error>
where
    T: 'static,
    C: Codec<T>,
{
    if local_field_type.type_id == type_id::LIST
        && list_element_type_matches_array(local_field_type, remote_field_type)
    {
        return read_array_data_as_vec_bridge::<T, C>(context, remote_field_type).map(Some);
    }
    Ok(None)
}

fn read_array_data_as_vec_bridge<T, C>(
    context: &mut ReadContext,
    remote_field_type: &FieldType,
) -> Result<Vec<T>, Error>
where
    T: 'static,
    C: Codec<T>,
{
    if field_ref_mode(remote_field_type) != RefMode::None {
        let ref_flag = context.reader.read_i8()?;
        if ref_flag == RefFlag::Null as i8 {
            return Ok(Vec::new());
        }
    }
    if crate::serializer::util::field_need_read_type_info(remote_field_type.type_id) {
        let remote = context.reader.read_u8()? as u32;
        if remote != remote_field_type.type_id {
            return Err(Error::type_mismatch(remote_field_type.type_id, remote));
        }
    }
    read_primitive_array_data_with_codec::<T, C>(context, remote_field_type)
}

#[cold]
#[inline(never)]
pub(super) fn read_primitive_array_vec_compatible_mismatch<T>(
    context: &mut ReadContext,
    local_field_type: &FieldType,
    remote_field_type: &FieldType,
) -> Result<Option<Vec<T>>, Error>
where
    T: CompatibleListArrayElement,
{
    if remote_field_type.type_id == type_id::LIST
        && !remote_field_type.generics.is_empty()
        && list_element_type_matches_array(remote_field_type, local_field_type)
    {
        if field_ref_mode(remote_field_type) != RefMode::None {
            let ref_flag = context.reader.read_i8()?;
            if ref_flag == RefFlag::Null as i8 {
                return Ok(Some(Vec::new()));
            }
        }
        return read_non_nullable_list_data_with_type::<T>(context, remote_field_type).map(Some);
    }
    Ok(None)
}
