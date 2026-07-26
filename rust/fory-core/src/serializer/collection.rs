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

use super::codec::{
    field_ref_mode, field_type_with_ref_flags, generic_field_type, same_numeric_family, Codec,
    CodecReadType,
};
use super::primitive_list;
use crate::context::ReadContext;
use crate::context::WriteContext;
use crate::error::Error;
use crate::meta::FieldType;
use crate::resolver::{RefFlag, RefMode};
use crate::type_id::{self, need_to_write_type_for_field, PRIMITIVE_ARRAY_TYPES};

pub const TRACKING_REF: u8 = 0b1;

pub const HAS_NULL: u8 = 0b10;

// Whether collection elements type is declare type.
pub const DECL_ELEMENT_TYPE: u8 = 0b100;

//  Whether collection elements type same.
pub const IS_SAME_TYPE: u8 = 0b1000;

#[inline(always)]
fn count_needs_bytes<T, const COUNT_ALLOCATES: bool, const ZST_NO_BACKING: bool>() -> bool {
    COUNT_ALLOCATES && (!ZST_NO_BACKING || std::mem::size_of::<T>() != 0)
}

#[inline(always)]
fn check_collection_len<T, const COUNT_ALLOCATES: bool, const ZST_NO_BACKING: bool>(
    context: &ReadContext,
    len: u32,
) -> Result<(), Error> {
    let len = len as usize;
    // Fixed arrays do not allocate from the wire count. Variable Vec-like ZST
    // owners also have no count-derived backing, while node and bucket owners do.
    if count_needs_bytes::<T, COUNT_ALLOCATES, ZST_NO_BACKING>() {
        context.reader.check_bound(len)?;
    }
    Ok(())
}

#[inline(always)]
pub(super) fn check_count_write_bytes(
    context: &WriteContext,
    body_offset: usize,
    len: usize,
) -> Result<(), Error> {
    if context.writer.len() - body_offset < len {
        return Err(insufficient_count_bytes());
    }
    Ok(())
}

#[cold]
#[inline(never)]
fn insufficient_count_bytes() -> Error {
    Error::invalid_data("count-derived collection allocation requires proportional encoded bytes")
}

#[inline(always)]
fn check_collection_write_len<T, const COUNT_ALLOCATES: bool, const ZST_NO_BACKING: bool>(
    context: &WriteContext,
    body_offset: usize,
    len: usize,
) -> Result<(), Error> {
    if count_needs_bytes::<T, COUNT_ALLOCATES, ZST_NO_BACKING>() {
        return check_count_write_bytes(context, body_offset, len);
    }
    Ok(())
}

#[cold]
#[inline(never)]
fn graph_memory_overflow() -> Error {
    Error::invalid_data("graph memory estimate overflows")
}

#[cold]
#[inline(never)]
fn missing_collection_type() -> Error {
    Error::type_error("Unable to determine concrete type for polymorphic collection elements")
}

#[cold]
#[inline(never)]
fn primitive_collection_mismatch() -> Error {
    Error::type_error(
        "Vec<number> belongs to the `number_array` type, \
         and Vec<Option<number>> belongs to the `list` type. \
         You should not read data of type `number_array` as data of type `list`.",
    )
}

#[cold]
#[inline(never)]
fn collection_type_mismatch(expected: u32, actual: u32) -> Error {
    Error::type_mismatch(expected, actual)
}

#[cold]
#[inline(never)]
fn non_polymorphic_collection() -> Error {
    Error::type_error("Type inconsistent, target type is not polymorphic")
}

#[cold]
#[inline(never)]
fn not_primitive_array() -> Error {
    Error::type_error("array-compatible field is not a primitive array")
}

#[cold]
#[inline(never)]
fn invalid_primitive_array_len() -> Error {
    Error::invalid_data("Invalid data length")
}

#[cold]
#[inline(never)]
fn list_array_error(message: &'static str) -> Error {
    Error::type_error(message)
}

#[inline(always)]
fn reserve_collection_storage(
    context: &mut ReadContext,
    len: u32,
    elem_bytes: usize,
) -> Result<(), Error> {
    let len = len as usize;
    let bytes = len
        .checked_mul(elem_bytes)
        .ok_or_else(graph_memory_overflow)?;
    context.reserve_graph_memory(bytes)?;
    Ok(())
}

pub fn write_collection_type_info(
    context: &mut WriteContext,
    collection_type_id: u32,
) -> Result<(), Error> {
    context.writer.write_u8(collection_type_id as u8);
    Ok(())
}

pub fn write_collection_data<'a, T, C, I, const COUNT_ALLOCATES: bool, const ZST_NO_BACKING: bool>(
    iter: I,
    context: &mut WriteContext,
    has_generics: bool,
) -> Result<(), Error>
where
    T: 'static + 'a,
    C: Codec<T>,
    I: IntoIterator<Item = &'a T>,
    I::IntoIter: ExactSizeIterator + Clone,
{
    let iter = iter.into_iter();
    let len = iter.len();
    context.writer.write_var_u32(len as u32);
    if len == 0 {
        return Ok(());
    }
    let body_offset = context.writer.len();
    if C::is_polymorphic() || C::is_shared_ref() {
        write_collection_data_dyn_ref::<T, C, _>(iter, context, has_generics)?;
        return check_collection_write_len::<T, COUNT_ALLOCATES, ZST_NO_BACKING>(
            context,
            body_offset,
            len,
        );
    }
    let mut header = IS_SAME_TYPE;
    let mut has_null = false;
    let elem_static_type_id = C::static_type_id();
    let is_elem_declared = has_generics && !need_to_write_type_for_field(elem_static_type_id);
    if C::is_option() {
        // iter.clone() is zero-copy
        for item in iter.clone() {
            if C::is_none(item) {
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
        C::write_type_info(context)?;
    }
    // Pre-reserve buffer space to avoid per-element capacity checks in the write loop.
    context.writer.reserve(len * C::reserved_space());
    if !has_null {
        if has_generics {
            for item in iter {
                C::write_with_mode(item, context, RefMode::None, false, true)?;
            }
        } else {
            for item in iter {
                C::write_data(item, context)?;
            }
        }
    } else {
        // Null detection already inspected nullable holders. Let the codec own
        // the null envelope so each holder is accessed only once in this loop.
        for item in iter {
            C::write_with_mode(item, context, RefMode::NullOnly, false, has_generics)?;
        }
    }

    check_collection_write_len::<T, COUNT_ALLOCATES, ZST_NO_BACKING>(context, body_offset, len)
}

/// Slow but versatile collection serialization for dynamic trait object and shared/circular reference.
pub fn write_collection_data_dyn_ref<'a, T, C, I>(
    iter: I,
    context: &mut WriteContext,
    has_generics: bool,
) -> Result<(), Error>
where
    T: 'static + 'a,
    C: Codec<T>,
    I: IntoIterator<Item = &'a T>,
    I::IntoIter: ExactSizeIterator + Clone,
{
    let elem_static_type_id = C::static_type_id();
    let is_elem_declared = has_generics && !need_to_write_type_for_field(elem_static_type_id);
    let elem_is_polymorphic = C::is_polymorphic();
    let elem_is_shared_ref = C::is_shared_ref();
    let dynamic_type_is_direct = !elem_is_polymorphic || C::dynamic_type_is_direct();

    let iter = iter.into_iter();
    let mut has_null = elem_is_polymorphic && !dynamic_type_is_direct;
    let mut is_same_type = dynamic_type_is_direct;
    let mut first_type_id: Option<std::any::TypeId> = None;

    if dynamic_type_is_direct {
        for item in iter.clone() {
            if elem_is_polymorphic {
                if let Some(dynamic_type_id) = C::dynamic_type_id(item)? {
                    if is_same_type {
                        if let Some(first_id) = first_type_id {
                            if first_id != dynamic_type_id {
                                is_same_type = false;
                            }
                        } else {
                            first_type_id = Some(dynamic_type_id);
                        }
                    }
                } else {
                    has_null = true;
                }
            } else if C::is_none(item) {
                has_null = true;
            }
        }
    }

    if elem_is_polymorphic && is_same_type && first_type_id.is_none() {
        // All elements are null, so each element must carry its own type metadata.
        is_same_type = false;
    }

    let mut header = 0u8;
    if has_null {
        header |= HAS_NULL;
    }
    if is_elem_declared {
        header |= DECL_ELEMENT_TYPE;
    }
    if is_same_type {
        header |= IS_SAME_TYPE;
    }
    if elem_is_shared_ref {
        header |= TRACKING_REF;
    }

    context.writer.write_u8(header);

    let type_info = if is_same_type && !is_elem_declared {
        if elem_is_polymorphic {
            let type_id = first_type_id.ok_or_else(missing_collection_type)?;
            Some(C::write_type_info_value(context, type_id)?)
        } else {
            C::write_type_info(context)?;
            None
        }
    } else {
        None
    };
    // Write elements data
    // Compute RefMode from flags
    let elem_ref_mode = if elem_is_shared_ref {
        RefMode::Tracking
    } else if has_null {
        RefMode::NullOnly
    } else {
        RefMode::None
    };

    if is_same_type {
        // All elements are same type
        if let Some(type_info) = type_info.as_ref() {
            for item in iter {
                C::write_with_type_info(item, context, elem_ref_mode, type_info, has_generics)?;
            }
        } else if elem_ref_mode == RefMode::None {
            // No null elements, no tracking
            if has_generics {
                for item in iter {
                    C::write_with_mode(item, context, RefMode::None, false, true)?;
                }
            } else {
                for item in iter {
                    C::write_data(item, context)?;
                }
            }
        } else {
            // Has null or tracking
            for item in iter {
                C::write_with_mode(item, context, elem_ref_mode, false, has_generics)?;
            }
        }
    } else {
        // Different types (polymorphic elements with different types)
        for item in iter {
            C::write_with_mode(item, context, elem_ref_mode, true, has_generics)?;
        }
    }

    Ok(())
}

pub fn read_collection_type_info(
    context: &mut ReadContext,
    collection_type_id: u32,
) -> Result<(), Error> {
    let remote_collection_type_id = context.reader.read_u8()? as u32;
    if PRIMITIVE_ARRAY_TYPES.contains(&remote_collection_type_id) {
        return Err(primitive_collection_mismatch());
    }
    if collection_type_id != remote_collection_type_id {
        return Err(collection_type_mismatch(
            collection_type_id,
            remote_collection_type_id,
        ));
    }
    Ok(())
}

#[inline(always)]
fn read_collection_element<T, C>(
    context: &mut ReadContext,
    read_type: Option<&CodecReadType>,
) -> Result<T, Error>
where
    T: 'static,
    C: Codec<T>,
{
    match read_type {
        None => C::read_data(context),
        Some(CodecReadType::Field(field_type)) => C::read_data_with_type(context, field_type),
        Some(CodecReadType::TypeInfo(type_info)) => C::read_data_with_type_info(context, type_info),
    }
}

pub fn read_collection_data<R, T, C, const COUNT_ALLOCATES: bool, const ZST_NO_BACKING: bool>(
    context: &mut ReadContext,
) -> Result<R, Error>
where
    T: 'static,
    C: Codec<T>,
    R: FromIterator<T>,
{
    let len = context.reader.read_var_u32()?;
    check_collection_len::<T, COUNT_ALLOCATES, ZST_NO_BACKING>(context, len)?;
    reserve_collection_storage(context, len, std::mem::size_of::<T>())?;
    if len == 0 {
        return Ok(R::from_iter(std::iter::empty()));
    }
    if C::is_polymorphic() || C::is_shared_ref() {
        return read_collection_data_dyn_ref::<R, T, C>(context, len);
    }
    let header = context.reader.read_u8()?;
    let declared = (header & DECL_ELEMENT_TYPE) != 0;
    let read_type = if declared {
        None
    } else {
        Some(C::read_type_info_value(context)?)
    };
    let has_null = (header & HAS_NULL) != 0;
    if (header & IS_SAME_TYPE) == 0 {
        return Err(non_polymorphic_collection());
    }
    if !has_null {
        (0..len)
            .map(|_| read_collection_element::<T, C>(context, read_type.as_ref()))
            .collect::<Result<R, Error>>()
    } else {
        (0..len)
            .map(|_| {
                let flag = context.reader.read_i8()?;
                if flag == RefFlag::Null as i8 {
                    return C::default_value(context);
                }
                read_collection_element::<T, C>(context, read_type.as_ref())
            })
            .collect::<Result<R, Error>>()
    }
}

/// Read a LIST/SET body using the recursive field metadata supplied by its owner.
pub fn read_collection_data_with_type<
    R,
    T,
    C,
    const COUNT_ALLOCATES: bool,
    const ZST_NO_BACKING: bool,
>(
    context: &mut ReadContext,
    remote_field_type: &FieldType,
) -> Result<R, Error>
where
    T: 'static,
    C: Codec<T>,
    R: FromIterator<T>,
{
    let element_type = generic_field_type(remote_field_type, 0, "collection")?;
    let len = context.reader.read_var_u32()?;
    check_collection_len::<T, COUNT_ALLOCATES, ZST_NO_BACKING>(context, len)?;
    reserve_collection_storage(context, len, std::mem::size_of::<T>())?;
    if len == 0 {
        return Ok(R::from_iter(std::iter::empty()));
    }

    let header = context.reader.read_u8()?;
    let track_ref = (header & TRACKING_REF) != 0;
    let same_type = (header & IS_SAME_TYPE) != 0;
    let has_null = (header & HAS_NULL) != 0;
    let declared = (header & DECL_ELEMENT_TYPE) != 0;
    let ref_mode = if track_ref {
        RefMode::Tracking
    } else if has_null {
        RefMode::NullOnly
    } else {
        RefMode::None
    };

    if C::is_polymorphic() || C::is_shared_ref() {
        if same_type {
            if declared {
                let element_type = field_type_with_ref_flags(element_type, has_null, track_ref);
                return (0..len)
                    .map(|_| C::read_field_with_type(context, &element_type))
                    .collect::<Result<R, Error>>();
            }
            let type_info = context.read_any_type_info()?;
            return (0..len)
                .map(|_| C::read_with_type_info(context, ref_mode, &type_info))
                .collect::<Result<R, Error>>();
        }
        return (0..len)
            .map(|_| C::read_with_mode(context, ref_mode, true))
            .collect::<Result<R, Error>>();
    }

    if !same_type {
        return Err(non_polymorphic_collection());
    }
    if declared {
        if has_null {
            return (0..len)
                .map(|_| {
                    if context.reader.read_i8()? == RefFlag::Null as i8 {
                        C::default_value(context)
                    } else {
                        C::read_data_with_type(context, element_type)
                    }
                })
                .collect::<Result<R, Error>>();
        }
        return (0..len)
            .map(|_| C::read_data_with_type(context, element_type))
            .collect::<Result<R, Error>>();
    }

    let read_type = C::read_type_info_value(context)?;
    if has_null {
        (0..len)
            .map(|_| {
                if context.reader.read_i8()? == RefFlag::Null as i8 {
                    C::default_value(context)
                } else {
                    match &read_type {
                        super::codec::CodecReadType::Field(field_type) => {
                            C::read_data_with_type(context, field_type)
                        }
                        super::codec::CodecReadType::TypeInfo(type_info) => {
                            C::read_data_with_type_info(context, type_info)
                        }
                    }
                }
            })
            .collect::<Result<R, Error>>()
    } else {
        (0..len)
            .map(|_| match &read_type {
                super::codec::CodecReadType::Field(field_type) => {
                    C::read_data_with_type(context, field_type)
                }
                super::codec::CodecReadType::TypeInfo(type_info) => {
                    C::read_data_with_type_info(context, type_info)
                }
            })
            .collect::<Result<R, Error>>()
    }
}

/// Slow but versatile collection deserialization for dynamic trait object and shared/circular reference.
pub fn read_collection_data_dyn_ref<R, T, C>(
    context: &mut ReadContext,
    len: u32,
) -> Result<R, Error>
where
    T: 'static,
    C: Codec<T>,
    R: FromIterator<T>,
{
    // Read header
    let header = context.reader.read_u8()?;
    // IMPORTANT: dynamic/shared-ref collection reads still obey the wire
    // header first. Local Rust type information must not override whether the
    // sender wrote ref flags for these elements. DO NOT REMOVE this comment.
    let is_track_ref = (header & TRACKING_REF) != 0;
    let is_same_type = (header & IS_SAME_TYPE) != 0;
    let has_null = (header & HAS_NULL) != 0;
    let is_declared = (header & DECL_ELEMENT_TYPE) != 0;

    // Compute RefMode from flags
    let elem_ref_mode = if is_track_ref {
        RefMode::Tracking
    } else if has_null {
        RefMode::NullOnly
    } else {
        RefMode::None
    };

    // Read elements
    if is_same_type {
        if is_declared {
            (0..len)
                .map(|_| C::read_with_mode(context, elem_ref_mode, false))
                .collect::<Result<R, Error>>()
        } else {
            let type_info = context.read_any_type_info()?;
            (0..len)
                .map(|_| C::read_with_type_info(context, elem_ref_mode, &type_info))
                .collect::<Result<R, Error>>()
        }
    } else {
        (0..len)
            .map(|_| C::read_with_mode(context, elem_ref_mode, true))
            .collect::<Result<R, Error>>()
    }
}

fn list_element_type_matches_array(
    list: &FieldType,
    array: &FieldType,
    require_unframed_element: bool,
) -> bool {
    primitive_list::element_type_id(array.type_id).is_some_and(|element_type_id| {
        if list.type_id != type_id::LIST
            || list.generics.len() != 1
            || list.nullable
            || list.track_ref
            || array.nullable
            || array.track_ref
        {
            return false;
        }
        let element = &list.generics[0];
        // Nullable element schema is allowed for list<T?> -> array<T>; actual
        // null elements fail in the dense-array reader. Ref-tracked
        // element framing is rejected here because this path stays primitive-only.
        if require_unframed_element && element.track_ref {
            return false;
        }
        primitive_element_type_matches(element_type_id, element.type_id)
    })
}

pub(super) fn compatible_list_array_field(local: &FieldType, remote: &FieldType) -> bool {
    (local.type_id == type_id::LIST && list_element_type_matches_array(local, remote, false))
        || (remote.type_id == type_id::LIST && list_element_type_matches_array(remote, local, true))
}

fn primitive_element_type_matches(array_element_type_id: u32, list_element_type_id: u32) -> bool {
    array_element_type_id == list_element_type_id
        || same_numeric_family(array_element_type_id, list_element_type_id)
}

fn read_primitive_array_with_codec<T, C>(
    context: &mut ReadContext,
    remote_field_type: &FieldType,
) -> Result<Vec<T>, Error>
where
    T: 'static,
    C: Codec<T>,
{
    let size_bytes = context.reader.read_var_u32()? as usize;
    let elem_size =
        primitive_list::element_size(remote_field_type.type_id).ok_or_else(not_primitive_array)?;
    if size_bytes % elem_size != 0 {
        return Err(invalid_primitive_array_len());
    }
    context.reader.check_bound(size_bytes)?;
    let len = size_bytes / elem_size;
    let element_type_id = primitive_list::element_type_id(remote_field_type.type_id)
        .ok_or_else(not_primitive_array)?;
    let element_type = FieldType::new(element_type_id, false, Vec::new());
    let mut vec = Vec::with_capacity(len);
    for _ in 0..len {
        vec.push(C::read_data_with_type(context, &element_type)?);
    }
    Ok(vec)
}

pub(super) fn read_list_as_primitive_vec<T, C>(
    context: &mut ReadContext,
    remote_field_type: &FieldType,
) -> Result<Vec<T>, Error>
where
    T: 'static,
    C: Codec<T>,
{
    let element_type = generic_field_type(remote_field_type, 0, "list")?;
    let len = context.reader.read_var_u32()?;
    let len_usize = len as usize;
    context.reader.check_bound(len_usize)?;
    if len == 0 {
        return Ok(Vec::new());
    }
    let header = context.reader.read_u8()?;
    if (header & HAS_NULL) != 0 {
        return Err(list_array_error(
            "compatible list to array field requires non-null elements",
        ));
    }
    if (header & TRACKING_REF) != 0 {
        return Err(list_array_error(
            "array-compatible list declares reference-tracked elements",
        ));
    }
    if (header & IS_SAME_TYPE) == 0 {
        return Err(list_array_error(
            "array-compatible list must declare same-type elements",
        ));
    }
    if (header & DECL_ELEMENT_TYPE) == 0 {
        return Err(list_array_error(
            "array-compatible list must declare element type",
        ));
    }
    let mut vec = Vec::with_capacity(len_usize);
    for _ in 0..len {
        vec.push(C::read_data_with_type(context, element_type)?);
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
        && list_element_type_matches_array(local_field_type, remote_field_type, false)
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
            return Err(collection_type_mismatch(remote_field_type.type_id, remote));
        }
    }
    read_primitive_array_with_codec::<T, C>(context, remote_field_type)
}

#[cold]
#[inline(never)]
pub(super) fn read_primitive_array_vec_mismatch<T, C>(
    context: &mut ReadContext,
    local_field_type: &FieldType,
    remote_field_type: &FieldType,
) -> Result<Option<Vec<T>>, Error>
where
    T: 'static,
    C: Codec<T>,
{
    if remote_field_type.type_id == type_id::LIST
        && !remote_field_type.generics.is_empty()
        && list_element_type_matches_array(remote_field_type, local_field_type, true)
    {
        if field_ref_mode(remote_field_type) != RefMode::None {
            let ref_flag = context.reader.read_i8()?;
            if ref_flag == RefFlag::Null as i8 {
                return Ok(Some(Vec::new()));
            }
        }
        return read_list_as_primitive_vec::<T, C>(context, remote_field_type).map(Some);
    }
    Ok(None)
}
