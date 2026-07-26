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

//! Internal field codecs used by macro-generated serializers.
//!
//! Manual serialization belongs in [`crate::Serializer`]. Codecs are
//! Fory-owned building blocks that allow generated code to apply field-local and
//! nested collection configuration without creating wrapper value types.

use super::collection::{
    check_count_write_bytes, compatible_list_array_field, read_primitive_array_vec_mismatch,
    read_vec_compatible_mismatch,
};
use crate::context::{ReadContext, WriteContext};
use crate::error::Error;
use crate::meta::{FieldInfo, FieldType};
use crate::resolver::{RefFlag, RefMode, TypeResolver};
use crate::serializer::{primitive_list, Serializer};
use crate::type_id::{self, need_to_write_type_for_field, TypeId, SIZE_OF_REF_AND_TYPE, UNKNOWN};
use std::any::Any;
use std::borrow::Cow;
use std::marker::PhantomData;
use std::rc::Rc;
use std::sync::Arc;

#[doc(hidden)]
pub use super::arc::ArcCodec;
#[doc(hidden)]
pub use super::array::ArrayCodec;
#[doc(hidden)]
pub use super::box_::BoxCodec;
#[doc(hidden)]
pub use super::heap::BinaryHeapCodec;
#[doc(hidden)]
pub use super::list::{LinkedListCodec, VecDequeCodec};
#[doc(hidden)]
pub use super::map::{BTreeMapCodec, HashMapCodec};
#[doc(hidden)]
pub use super::mutex::MutexCodec;
#[doc(hidden)]
pub use super::rc::RcCodec;
#[doc(hidden)]
pub use super::refcell::RefCellCodec;
#[doc(hidden)]
pub use super::set::{BTreeSetCodec, HashSetCodec};
#[doc(hidden)]
pub use super::tuple::{
    Tuple10Codec, Tuple11Codec, Tuple12Codec, Tuple13Codec, Tuple14Codec, Tuple15Codec,
    Tuple16Codec, Tuple17Codec, Tuple18Codec, Tuple19Codec, Tuple1Codec, Tuple20Codec,
    Tuple21Codec, Tuple22Codec, Tuple2Codec, Tuple3Codec, Tuple4Codec, Tuple5Codec, Tuple6Codec,
    Tuple7Codec, Tuple8Codec, Tuple9Codec,
};
#[doc(hidden)]
pub use super::weak::{ArcWeakCodec, RcWeakCodec};

pub(super) const TRACKING_REF: u8 = 0b1;
pub(super) const HAS_NULL: u8 = 0b10;
pub(super) const DECL_ELEMENT_TYPE: u8 = 0b100;
pub(super) const IS_SAME_TYPE: u8 = 0b1000;

#[cold]
#[inline(never)]
fn graph_memory_overflow() -> Error {
    Error::invalid_data("graph memory estimate overflows")
}

#[inline(always)]
fn reserve_graph_storage(
    context: &mut ReadContext,
    len: u32,
    elem_bytes: usize,
) -> Result<usize, Error> {
    let len = len as usize;
    let bytes = len
        .checked_mul(elem_bytes)
        .ok_or_else(graph_memory_overflow)?;
    context.reserve_graph_memory(bytes)?;
    Ok(len)
}

#[inline(always)]
pub fn field_ref_mode(field_type: &FieldType) -> RefMode {
    if field_type.track_ref {
        RefMode::Tracking
    } else if crate::serializer::util::field_need_write_ref_into(
        field_type.type_id,
        field_type.nullable,
    ) {
        RefMode::NullOnly
    } else {
        RefMode::None
    }
}

#[inline(always)]
pub(super) fn field_type_with_ref_flags(
    field_type: &FieldType,
    nullable: bool,
    track_ref: bool,
) -> Cow<'_, FieldType> {
    // Collection and map headers own the actual per-body reference envelope.
    // A peer's field override or a nullable collection with no null elements
    // can legitimately differ from the declared generic metadata.
    if field_type.nullable == nullable && field_type.track_ref == track_ref {
        Cow::Borrowed(field_type)
    } else {
        let mut adjusted = field_type.clone();
        adjusted.nullable = nullable;
        adjusted.track_ref = track_ref;
        Cow::Owned(adjusted)
    }
}

#[inline(always)]
fn field_read_type_info<T: Serializer>(context: &ReadContext, field_type: &FieldType) -> bool {
    if context.is_compatible() {
        crate::serializer::util::field_need_read_type_info(field_type.type_id)
    } else {
        T::is_polymorphic()
    }
}

#[inline(always)]
fn field_write_type_info<T: Serializer>(context: &WriteContext) -> bool {
    if context.is_compatible() {
        crate::serializer::util::field_need_write_type_info(T::static_type_id())
    } else {
        T::is_polymorphic()
    }
}

#[inline(always)]
fn serializer_static_field_type_id<T: Serializer>() -> u32 {
    let type_id = T::static_type_id() as u32;
    // Static fields already carry the union schema in their FieldType, so field
    // metadata uses UNION. TYPED_UNION/NAMED_UNION are root or dynamic Any
    // identities where no field owner supplies the schema.
    if type_id == TypeId::TYPED_UNION as u32 || type_id == TypeId::NAMED_UNION as u32 {
        TypeId::UNION as u32
    } else {
        type_id
    }
}

#[inline(always)]
fn serializer_ref_mode<T: Serializer, const NULLABLE: bool, const TRACK_REF: bool>() -> RefMode {
    if TRACK_REF {
        RefMode::Tracking
    } else if crate::serializer::util::field_need_write_ref_into(
        serializer_static_field_type_id::<T>(),
        NULLABLE,
    ) {
        RefMode::NullOnly
    } else {
        RefMode::None
    }
}

#[inline(always)]
fn serializer_read_type_info<T: Serializer>(context: &ReadContext) -> bool {
    if context.is_compatible() {
        crate::serializer::util::field_need_read_type_info(serializer_static_field_type_id::<T>())
    } else {
        T::is_polymorphic()
    }
}

#[inline(always)]
fn read_serializer_type_info<S: Serializer>(
    context: &mut ReadContext,
) -> Result<Option<Rc<crate::TypeInfo>>, Error> {
    // Static built-in carrier headers are compact type IDs, not registered
    // harnesses. Compatible TypeInfo exists only for metadata-bearing IDs.
    if context.is_compatible() && !type_id::is_internal_type(S::static_type_id() as u32) {
        return context.read_any_type_info().map(Some);
    }
    S::read_type_info(context)?;
    Ok(None)
}

#[inline(always)]
pub(super) fn codec_read_type_info<T, C>(context: &ReadContext, field_type: &FieldType) -> bool
where
    T: 'static,
    C: Codec<T>,
{
    if context.is_compatible() {
        field_type.type_id == type_id::UNKNOWN
            || crate::serializer::util::field_need_read_type_info(field_type.type_id)
    } else {
        C::is_polymorphic()
    }
}

#[inline(always)]
fn codec_static_field_type_id<T, C>() -> u32
where
    T: 'static,
    C: Codec<T>,
{
    let type_id = C::static_type_id() as u32;
    // Keep typed union identity out of static field metadata; the owning field
    // already supplies the schema for the union body.
    if type_id == TypeId::TYPED_UNION as u32 || type_id == TypeId::NAMED_UNION as u32 {
        TypeId::UNION as u32
    } else {
        type_id
    }
}

#[inline(always)]
pub(super) fn codec_read_type_info_static<T, C>(context: &ReadContext) -> bool
where
    T: 'static,
    C: Codec<T>,
{
    if context.is_compatible() {
        let type_id = codec_static_field_type_id::<T, C>();
        type_id == type_id::UNKNOWN || crate::serializer::util::field_need_read_type_info(type_id)
    } else {
        C::is_polymorphic()
    }
}

#[inline(always)]
pub(super) fn codec_write_type_info<T, C>(context: &WriteContext) -> bool
where
    T: 'static,
    C: Codec<T>,
{
    if context.is_compatible() {
        crate::serializer::util::field_need_write_type_info(C::static_type_id())
    } else {
        C::is_polymorphic()
    }
}

#[inline(always)]
pub(super) fn codec_ref_mode<T, C, const NULLABLE: bool, const TRACK_REF: bool>() -> RefMode
where
    T: 'static,
    C: Codec<T>,
{
    if TRACK_REF {
        RefMode::Tracking
    } else if crate::serializer::util::field_need_write_ref_into(
        codec_static_field_type_id::<T, C>(),
        NULLABLE,
    ) {
        RefMode::NullOnly
    } else {
        RefMode::None
    }
}

#[inline(always)]
pub(super) fn same_numeric_family(local: u32, remote: u32) -> bool {
    matches!(
        (local, remote),
        (type_id::INT32, type_id::INT32 | type_id::VARINT32)
            | (type_id::VARINT32, type_id::INT32 | type_id::VARINT32)
            | (
                type_id::INT64,
                type_id::INT64 | type_id::VARINT64 | type_id::TAGGED_INT64
            )
            | (
                type_id::VARINT64,
                type_id::INT64 | type_id::VARINT64 | type_id::TAGGED_INT64
            )
            | (
                type_id::TAGGED_INT64,
                type_id::INT64 | type_id::VARINT64 | type_id::TAGGED_INT64
            )
            | (type_id::UINT32, type_id::UINT32 | type_id::VAR_UINT32)
            | (type_id::VAR_UINT32, type_id::UINT32 | type_id::VAR_UINT32)
            | (
                type_id::UINT64,
                type_id::UINT64 | type_id::VAR_UINT64 | type_id::TAGGED_UINT64
            )
            | (
                type_id::VAR_UINT64,
                type_id::UINT64 | type_id::VAR_UINT64 | type_id::TAGGED_UINT64
            )
            | (
                type_id::TAGGED_UINT64,
                type_id::UINT64 | type_id::VAR_UINT64 | type_id::TAGGED_UINT64
            )
    )
}

#[inline(always)]
pub(super) fn allows_missing_generics(type_id: u32) -> bool {
    type_id == type_id::LIST || type_id == type_id::SET || type_id == type_id::MAP
}

#[inline(always)]
pub fn field_types_compatible(local: &FieldType, remote: &FieldType) -> bool {
    local.exact_shape_match(remote)
}

#[inline(always)]
fn compatible_byte_sequence_field(local: &FieldType, remote: &FieldType) -> bool {
    !local.track_ref
        && !remote.track_ref
        && local.nullable == remote.nullable
        && ((local.type_id == type_id::BINARY && remote.type_id == type_id::UINT8_ARRAY)
            || (local.type_id == type_id::UINT8_ARRAY && remote.type_id == type_id::BINARY))
}

#[cold]
#[inline(never)]
pub fn compatible_field_pair(local: &FieldType, remote: &FieldType) -> bool {
    field_types_compatible(local, remote)
        || compatible_byte_sequence_field(local, remote)
        || crate::meta::compatible_scalar_field_pair(local, remote)
        || compatible_list_array_field(local, remote)
        || local.compatible_shape_match(remote)
}

macro_rules! compatible_scalar_reader {
    ($read:ident, $read_option:ident, $target:ident, $target_option:ident, $ty:ty) => {
        #[inline(always)]
        pub fn $read(
            context: &mut ReadContext,
            local_type: u32,
            remote_field: &FieldInfo,
        ) -> Result<$ty, Error> {
            super::scalar_conversion::$target(context, local_type, remote_field)
        }

        #[inline(always)]
        pub fn $read_option(
            context: &mut ReadContext,
            local_type: u32,
            remote_field: &FieldInfo,
        ) -> Result<Option<$ty>, Error> {
            super::scalar_conversion::$target_option(context, local_type, remote_field)
        }
    };
}

compatible_scalar_reader!(
    read_bool_compatible_scalar,
    read_bool_option_compatible_scalar,
    read_bool_target,
    read_bool_option_target,
    bool
);
compatible_scalar_reader!(
    read_string_compatible_scalar,
    read_string_option_compatible_scalar,
    read_string_target,
    read_string_option_target,
    String
);
compatible_scalar_reader!(
    read_i8_compatible_scalar,
    read_i8_option_compatible_scalar,
    read_i8_target,
    read_i8_option_target,
    i8
);
compatible_scalar_reader!(
    read_i16_compatible_scalar,
    read_i16_option_compatible_scalar,
    read_i16_target,
    read_i16_option_target,
    i16
);
compatible_scalar_reader!(
    read_i32_compatible_scalar,
    read_i32_option_compatible_scalar,
    read_i32_target,
    read_i32_option_target,
    i32
);
compatible_scalar_reader!(
    read_i64_compatible_scalar,
    read_i64_option_compatible_scalar,
    read_i64_target,
    read_i64_option_target,
    i64
);
compatible_scalar_reader!(
    read_u8_compatible_scalar,
    read_u8_option_compatible_scalar,
    read_u8_target,
    read_u8_option_target,
    u8
);
compatible_scalar_reader!(
    read_u16_compatible_scalar,
    read_u16_option_compatible_scalar,
    read_u16_target,
    read_u16_option_target,
    u16
);
compatible_scalar_reader!(
    read_u32_compatible_scalar,
    read_u32_option_compatible_scalar,
    read_u32_target,
    read_u32_option_target,
    u32
);
compatible_scalar_reader!(
    read_u64_compatible_scalar,
    read_u64_option_compatible_scalar,
    read_u64_target,
    read_u64_option_target,
    u64
);
compatible_scalar_reader!(
    read_f32_compatible_scalar,
    read_f32_option_compatible_scalar,
    read_f32_target,
    read_f32_option_target,
    f32
);
compatible_scalar_reader!(
    read_f64_compatible_scalar,
    read_f64_option_compatible_scalar,
    read_f64_target,
    read_f64_option_target,
    f64
);
compatible_scalar_reader!(
    read_float16_compatible_scalar,
    read_float16_option_compatible_scalar,
    read_float16_target,
    read_float16_option_target,
    crate::types::float16::float16
);
compatible_scalar_reader!(
    read_bfloat16_compatible_scalar,
    read_bfloat16_option_compatible_scalar,
    read_bfloat16_target,
    read_bfloat16_option_target,
    crate::types::bfloat16::bfloat16
);
compatible_scalar_reader!(
    read_decimal_compatible_scalar,
    read_decimal_option_compatible_scalar,
    read_decimal_target,
    read_decimal_option_target,
    crate::types::Decimal
);

#[cold]
#[inline(never)]
fn missing_generic_type(owner: &str, index: usize) -> Error {
    Error::invalid_data(format!(
        "{owner} field metadata is missing generic type at index {index}"
    ))
}

#[inline(always)]
pub(super) fn generic_field_type<'a>(
    field_type: &'a FieldType,
    index: usize,
    owner: &str,
) -> Result<&'a FieldType, Error> {
    field_type
        .generics
        .get(index)
        .ok_or_else(|| missing_generic_type(owner, index))
}

pub enum CodecReadType {
    Field(FieldType),
    TypeInfo(Rc<crate::TypeInfo>),
}

enum ElementReadType<'a> {
    Direct,
    Field(Cow<'a, FieldType>),
    TypeInfo(Rc<crate::TypeInfo>),
}

#[inline(always)]
fn element_read_type<T, C>(
    context: &mut ReadContext,
    read_type: CodecReadType,
) -> Result<ElementReadType<'static>, Error>
where
    T: 'static,
    C: Codec<T>,
{
    match read_type {
        CodecReadType::Field(field_type) => Ok(ElementReadType::Field(Cow::Owned(field_type))),
        CodecReadType::TypeInfo(type_info) => {
            if C::type_info_exact(context, &type_info)? {
                Ok(ElementReadType::Direct)
            } else {
                Ok(ElementReadType::TypeInfo(type_info))
            }
        }
    }
}

pub trait Codec<T: 'static>: 'static {
    fn field_type(type_resolver: &TypeResolver) -> Result<FieldType, Error>;

    #[inline(always)]
    fn reserved_space() -> usize {
        std::mem::size_of::<T>()
    }

    fn write_field(value: &T, context: &mut WriteContext) -> Result<(), Error>;

    fn read_field(context: &mut ReadContext) -> Result<T, Error>;

    #[inline(always)]
    fn read_compatible(
        context: &mut ReadContext,
        local_field_type: &FieldType,
        remote_field_type: &FieldType,
    ) -> Result<Option<T>, Error> {
        if field_types_compatible(local_field_type, remote_field_type)
            || local_field_type.compatible_shape_match(remote_field_type)
        {
            return Self::read_field_with_type(context, remote_field_type).map(Some);
        }
        super::scalar_conversion::read_scalar_field::<T, Self>(
            context,
            local_field_type,
            remote_field_type,
        )
    }

    fn write_data(value: &T, context: &mut WriteContext) -> Result<(), Error>;

    fn read_data(context: &mut ReadContext) -> Result<T, Error>;

    #[inline(always)]
    fn read_data_with_type(
        context: &mut ReadContext,
        _remote_data_type: &FieldType,
    ) -> Result<T, Error> {
        Self::read_data(context)
    }

    #[inline(always)]
    fn read_data_with_type_info(
        context: &mut ReadContext,
        type_info: &Rc<crate::TypeInfo>,
    ) -> Result<T, Error> {
        Self::read_with_type_info(context, RefMode::None, type_info)
    }

    #[inline(always)]
    fn type_info_exact(
        _context: &ReadContext,
        _type_info: &Rc<crate::TypeInfo>,
    ) -> Result<bool, Error> {
        Ok(false)
    }

    fn read_field_with_type(
        context: &mut ReadContext,
        remote_field_type: &FieldType,
    ) -> Result<T, Error>;

    /// `has_generics` means enclosing field metadata owns this value's recursive schema.
    /// Carrier codecs must preserve it when they write child carrier bodies.
    fn write_with_mode(
        value: &T,
        context: &mut WriteContext,
        ref_mode: RefMode,
        write_type_info: bool,
        has_generics: bool,
    ) -> Result<(), Error>;

    #[doc(hidden)]
    #[inline(always)]
    fn write_type_info_value(
        context: &mut WriteContext,
        target_type_id: std::any::TypeId,
    ) -> Result<Rc<crate::TypeInfo>, Error> {
        context.write_target_type_info(Self::static_type_id() as u32, target_type_id)
    }

    #[doc(hidden)]
    #[inline(always)]
    fn write_with_type_info(
        value: &T,
        context: &mut WriteContext,
        ref_mode: RefMode,
        type_info: &Rc<crate::TypeInfo>,
        has_generics: bool,
    ) -> Result<(), Error> {
        let _ = type_info;
        Self::write_with_mode(value, context, ref_mode, false, has_generics)
    }

    fn read_with_mode(
        context: &mut ReadContext,
        ref_mode: RefMode,
        read_type_info: bool,
    ) -> Result<T, Error>;

    fn read_with_type_info(
        context: &mut ReadContext,
        ref_mode: RefMode,
        type_info: &std::rc::Rc<crate::TypeInfo>,
    ) -> Result<T, Error>;

    fn default_value(context: &mut ReadContext) -> Result<T, Error>;

    fn write_type_info(context: &mut WriteContext) -> Result<(), Error>;

    fn read_type_info(context: &mut ReadContext) -> Result<(), Error>;

    #[inline(always)]
    fn read_type_info_value(context: &mut ReadContext) -> Result<CodecReadType, Error> {
        Self::read_type_info_as_field_type(context).map(CodecReadType::Field)
    }

    #[inline(always)]
    fn read_type_info_as_field_type(context: &mut ReadContext) -> Result<FieldType, Error> {
        Self::read_type_info(context)?;
        Self::field_type(context.get_type_resolver())
    }

    #[inline(always)]
    fn static_type_id() -> TypeId {
        TypeId::UNKNOWN
    }

    #[inline(always)]
    fn is_option() -> bool {
        false
    }

    #[inline(always)]
    fn is_none(_value: &T) -> bool {
        false
    }

    #[inline(always)]
    fn is_polymorphic() -> bool {
        false
    }

    #[inline(always)]
    fn is_shared_ref() -> bool {
        false
    }

    #[inline(always)]
    fn is_wrapper_type() -> bool {
        Self::is_shared_ref()
    }

    #[inline(always)]
    fn dynamic_type_id(value: &T) -> Result<Option<std::any::TypeId>, Error> {
        let _ = value;
        Ok(Some(std::any::TypeId::of::<T>()))
    }

    #[inline(always)]
    fn dynamic_type_is_direct() -> bool {
        true
    }
}

pub struct SerializerCodec<S, const NULLABLE: bool, const TRACK_REF: bool>(PhantomData<fn() -> S>);

impl<S, const NULLABLE: bool, const TRACK_REF: bool> Codec<S::Target>
    for SerializerCodec<S, NULLABLE, TRACK_REF>
where
    S: Serializer,
{
    #[inline(always)]
    fn field_type(type_resolver: &TypeResolver) -> Result<FieldType, Error> {
        S::field_type::<NULLABLE, TRACK_REF>(type_resolver)
    }

    #[inline(always)]
    fn reserved_space() -> usize {
        S::reserved_space() + SIZE_OF_REF_AND_TYPE
    }

    #[inline(always)]
    fn write_field(value: &S::Target, context: &mut WriteContext) -> Result<(), Error> {
        S::write_value(
            value,
            context,
            serializer_ref_mode::<S, NULLABLE, TRACK_REF>(),
            field_write_type_info::<S>(context),
            false,
        )
    }

    // Avoid forcing this fast serializer path into large debug-mode generated readers.
    #[cfg_attr(debug_assertions, inline(never))]
    #[cfg_attr(not(debug_assertions), inline(always))]
    fn read_field(context: &mut ReadContext) -> Result<S::Target, Error> {
        let ref_mode = serializer_ref_mode::<S, NULLABLE, TRACK_REF>();
        let read_type_info = serializer_read_type_info::<S>(context);
        if ref_mode == RefMode::None && !S::is_polymorphic() {
            if read_type_info {
                if let Some(type_info) = read_serializer_type_info::<S>(context)? {
                    return Self::read_data_with_type_info(context, &type_info);
                }
            }
            return S::read(context);
        }
        S::read_value(context, ref_mode, read_type_info)
    }

    #[inline(always)]
    fn read_compatible(
        context: &mut ReadContext,
        local_field_type: &FieldType,
        remote_field_type: &FieldType,
    ) -> Result<Option<S::Target>, Error> {
        if field_types_compatible(local_field_type, remote_field_type)
            || local_field_type.compatible_shape_match(remote_field_type)
        {
            return Self::read_field_with_type(context, remote_field_type).map(Some);
        }
        super::scalar_conversion::read_scalar_field::<S::Target, Self>(
            context,
            local_field_type,
            remote_field_type,
        )
    }

    #[inline(always)]
    fn write_data(value: &S::Target, context: &mut WriteContext) -> Result<(), Error> {
        S::write_with_generics(value, context, false)
    }

    #[inline(always)]
    fn read_data(context: &mut ReadContext) -> Result<S::Target, Error> {
        S::read(context)
    }

    #[inline(always)]
    fn read_data_with_type(
        context: &mut ReadContext,
        remote_data_type: &FieldType,
    ) -> Result<S::Target, Error> {
        S::read_data_with_field_type(context, remote_data_type)
    }

    #[inline(always)]
    fn read_data_with_type_info(
        context: &mut ReadContext,
        type_info: &Rc<crate::TypeInfo>,
    ) -> Result<S::Target, Error> {
        if Self::type_info_exact(context, type_info)? {
            return S::read(context);
        }
        S::read_with_type_info(context, RefMode::None, type_info)
    }

    #[inline(always)]
    fn type_info_exact(
        context: &ReadContext,
        type_info: &Rc<crate::TypeInfo>,
    ) -> Result<bool, Error> {
        if !context.is_compatible() {
            return Ok(false);
        }
        Ok(type_info.has_exact_local_schema())
    }

    #[cfg_attr(debug_assertions, inline(never))]
    #[cfg_attr(not(debug_assertions), inline(always))]
    fn read_field_with_type(
        context: &mut ReadContext,
        remote_field_type: &FieldType,
    ) -> Result<S::Target, Error> {
        let ref_mode = field_ref_mode(remote_field_type);
        let read_type_info = field_read_type_info::<S>(context, remote_field_type);
        if ref_mode == RefMode::None && !S::is_polymorphic() {
            if read_type_info {
                if let Some(type_info) = read_serializer_type_info::<S>(context)? {
                    return Self::read_data_with_type_info(context, &type_info);
                }
            }
            return S::read_data_with_field_type(context, remote_field_type);
        }
        S::read_value(context, ref_mode, read_type_info)
    }

    #[inline(always)]
    fn write_with_mode(
        value: &S::Target,
        context: &mut WriteContext,
        ref_mode: RefMode,
        write_type_info: bool,
        has_generics: bool,
    ) -> Result<(), Error> {
        S::write_value(value, context, ref_mode, write_type_info, has_generics)
    }

    #[inline(always)]
    fn write_type_info_value(
        context: &mut WriteContext,
        target_type_id: std::any::TypeId,
    ) -> Result<Rc<crate::TypeInfo>, Error> {
        S::write_type_info_value(context, target_type_id)
    }

    #[inline(always)]
    fn write_with_type_info(
        value: &S::Target,
        context: &mut WriteContext,
        ref_mode: RefMode,
        type_info: &Rc<crate::TypeInfo>,
        has_generics: bool,
    ) -> Result<(), Error> {
        S::write_with_type_info(value, context, ref_mode, type_info, has_generics)
    }

    #[cfg_attr(debug_assertions, inline(never))]
    #[cfg_attr(not(debug_assertions), inline(always))]
    fn read_with_mode(
        context: &mut ReadContext,
        ref_mode: RefMode,
        read_type_info: bool,
    ) -> Result<S::Target, Error> {
        if ref_mode == RefMode::None && !S::is_polymorphic() {
            if read_type_info {
                if let Some(type_info) = read_serializer_type_info::<S>(context)? {
                    return Self::read_data_with_type_info(context, &type_info);
                }
            }
            return S::read(context);
        }
        S::read_value(context, ref_mode, read_type_info)
    }

    #[inline(always)]
    fn read_with_type_info(
        context: &mut ReadContext,
        ref_mode: RefMode,
        type_info: &std::rc::Rc<crate::TypeInfo>,
    ) -> Result<S::Target, Error> {
        S::read_with_type_info(context, ref_mode, type_info)
    }

    #[inline(always)]
    fn default_value(context: &mut ReadContext) -> Result<S::Target, Error> {
        S::default_value(context)
    }

    #[inline(always)]
    fn write_type_info(context: &mut WriteContext) -> Result<(), Error> {
        S::write_type_info(context)
    }

    #[inline(always)]
    fn read_type_info(context: &mut ReadContext) -> Result<(), Error> {
        S::read_type_info(context)
    }

    #[inline(always)]
    fn read_type_info_value(context: &mut ReadContext) -> Result<CodecReadType, Error> {
        if let Some(type_info) = read_serializer_type_info::<S>(context)? {
            return Ok(CodecReadType::TypeInfo(type_info));
        }
        Self::field_type(context.get_type_resolver()).map(CodecReadType::Field)
    }

    #[inline(always)]
    fn static_type_id() -> TypeId {
        S::static_type_id()
    }

    #[inline(always)]
    fn is_option() -> bool {
        S::is_option()
    }

    #[inline(always)]
    fn is_none(value: &S::Target) -> bool {
        S::is_none(value)
    }

    #[inline(always)]
    fn is_polymorphic() -> bool {
        S::is_polymorphic()
    }

    #[inline(always)]
    fn is_shared_ref() -> bool {
        S::is_shared_ref()
    }

    #[inline(always)]
    fn is_wrapper_type() -> bool {
        S::is_wrapper_type()
    }

    #[inline(always)]
    fn dynamic_type_id(value: &S::Target) -> Result<Option<std::any::TypeId>, Error> {
        S::dynamic_type_id(value)
    }

    #[inline(always)]
    fn dynamic_type_is_direct() -> bool {
        S::dynamic_type_is_direct()
    }
}

pub struct OptionCodec<T, C, const TRACK_REF: bool>(PhantomData<(T, C)>);

#[cold]
#[inline(never)]
fn missing_option_value() -> Error {
    Error::invalid_data("Option::None cannot be written as non-null data")
}

impl<T, C, const TRACK_REF: bool> Codec<Option<T>> for OptionCodec<T, C, TRACK_REF>
where
    T: 'static,
    C: Codec<T>,
{
    #[inline(always)]
    fn field_type(type_resolver: &TypeResolver) -> Result<FieldType, Error> {
        let mut field_type = C::field_type(type_resolver)?;
        field_type.nullable = true;
        field_type.track_ref = TRACK_REF;
        Ok(field_type)
    }

    #[inline(always)]
    fn reserved_space() -> usize {
        C::reserved_space() + 1
    }

    #[inline(always)]
    fn write_field(value: &Option<T>, context: &mut WriteContext) -> Result<(), Error> {
        Self::write_with_mode(
            value,
            context,
            if TRACK_REF {
                RefMode::Tracking
            } else {
                RefMode::NullOnly
            },
            codec_write_type_info::<T, C>(context),
            true,
        )
    }

    #[inline(always)]
    fn read_field(context: &mut ReadContext) -> Result<Option<T>, Error> {
        Self::read_with_mode(
            context,
            if TRACK_REF {
                RefMode::Tracking
            } else {
                RefMode::NullOnly
            },
            codec_read_type_info_static::<T, C>(context),
        )
    }

    #[inline(always)]
    fn write_data(value: &Option<T>, context: &mut WriteContext) -> Result<(), Error> {
        let value = value.as_ref().ok_or_else(missing_option_value)?;
        C::write_data(value, context)
    }

    #[inline(always)]
    fn read_data(context: &mut ReadContext) -> Result<Option<T>, Error> {
        Ok(Some(C::read_data(context)?))
    }

    #[inline(always)]
    fn read_data_with_type(
        context: &mut ReadContext,
        remote_data_type: &FieldType,
    ) -> Result<Option<T>, Error> {
        Ok(Some(C::read_data_with_type(context, remote_data_type)?))
    }

    #[inline(always)]
    fn read_field_with_type(
        context: &mut ReadContext,
        remote_field_type: &FieldType,
    ) -> Result<Option<T>, Error> {
        let ref_mode = field_ref_mode(remote_field_type);
        if ref_mode != RefMode::None {
            let ref_flag = context.reader.read_i8()?;
            if ref_flag == RefFlag::Null as i8 {
                return Ok(None);
            }
            context.reader.move_back(1);
        }
        Ok(Some(C::read_field_with_type(context, remote_field_type)?))
    }

    #[inline(always)]
    fn read_compatible(
        context: &mut ReadContext,
        local_field_type: &FieldType,
        remote_field_type: &FieldType,
    ) -> Result<Option<Option<T>>, Error> {
        if field_types_compatible(local_field_type, remote_field_type)
            || local_field_type.compatible_shape_match(remote_field_type)
        {
            return Self::read_field_with_type(context, remote_field_type).map(Some);
        }
        let child_type_id = C::static_type_id() as u32;
        if child_type_id != local_field_type.type_id
            && !same_numeric_family(child_type_id, local_field_type.type_id)
        {
            return Ok(None);
        }
        super::scalar_conversion::read_scalar_option_field::<T>(
            context,
            local_field_type,
            remote_field_type,
        )
    }

    #[inline(always)]
    fn write_with_mode(
        value: &Option<T>,
        context: &mut WriteContext,
        ref_mode: RefMode,
        write_type_info: bool,
        has_generics: bool,
    ) -> Result<(), Error> {
        match ref_mode {
            RefMode::None => {
                let value = value.as_ref().ok_or_else(missing_option_value)?;
                C::write_with_mode(value, context, RefMode::None, write_type_info, has_generics)
            }
            RefMode::NullOnly => {
                if let Some(value) = value {
                    context.writer.write_i8(RefFlag::NotNullValue as i8);
                    C::write_with_mode(value, context, RefMode::None, write_type_info, has_generics)
                } else {
                    context.writer.write_i8(RefFlag::Null as i8);
                    Ok(())
                }
            }
            RefMode::Tracking => {
                if let Some(value) = value {
                    C::write_with_mode(
                        value,
                        context,
                        RefMode::Tracking,
                        write_type_info,
                        has_generics,
                    )
                } else {
                    context.writer.write_i8(RefFlag::Null as i8);
                    Ok(())
                }
            }
        }
    }

    #[inline(always)]
    fn write_type_info_value(
        context: &mut WriteContext,
        target_type_id: std::any::TypeId,
    ) -> Result<Rc<crate::TypeInfo>, Error> {
        C::write_type_info_value(context, target_type_id)
    }

    #[inline(always)]
    fn write_with_type_info(
        value: &Option<T>,
        context: &mut WriteContext,
        ref_mode: RefMode,
        type_info: &Rc<crate::TypeInfo>,
        has_generics: bool,
    ) -> Result<(), Error> {
        match ref_mode {
            RefMode::None => {
                let value = value.as_ref().ok_or_else(missing_option_value)?;
                C::write_with_type_info(value, context, RefMode::None, type_info, has_generics)
            }
            RefMode::NullOnly => {
                if let Some(value) = value {
                    context.writer.write_i8(RefFlag::NotNullValue as i8);
                    C::write_with_type_info(value, context, RefMode::None, type_info, has_generics)
                } else {
                    context.writer.write_i8(RefFlag::Null as i8);
                    Ok(())
                }
            }
            RefMode::Tracking => {
                if let Some(value) = value {
                    C::write_with_type_info(
                        value,
                        context,
                        RefMode::Tracking,
                        type_info,
                        has_generics,
                    )
                } else {
                    context.writer.write_i8(RefFlag::Null as i8);
                    Ok(())
                }
            }
        }
    }

    #[inline(always)]
    fn read_with_mode(
        context: &mut ReadContext,
        ref_mode: RefMode,
        read_type_info: bool,
    ) -> Result<Option<T>, Error> {
        match ref_mode {
            RefMode::None => Ok(Some(C::read_with_mode(
                context,
                RefMode::None,
                read_type_info,
            )?)),
            RefMode::NullOnly => {
                let ref_flag = context.reader.read_i8()?;
                if ref_flag == RefFlag::Null as i8 {
                    return Ok(None);
                }
                Ok(Some(C::read_with_mode(
                    context,
                    RefMode::None,
                    read_type_info,
                )?))
            }
            RefMode::Tracking => {
                let ref_flag = context.reader.read_i8()?;
                if ref_flag == RefFlag::Null as i8 {
                    return Ok(None);
                }
                context.reader.move_back(1);
                Ok(Some(C::read_with_mode(
                    context,
                    RefMode::Tracking,
                    read_type_info,
                )?))
            }
        }
    }

    #[inline(always)]
    fn read_with_type_info(
        context: &mut ReadContext,
        ref_mode: RefMode,
        type_info: &std::rc::Rc<crate::TypeInfo>,
    ) -> Result<Option<T>, Error> {
        match ref_mode {
            RefMode::None => Ok(Some(C::read_with_type_info(
                context,
                RefMode::None,
                type_info,
            )?)),
            RefMode::NullOnly => {
                let ref_flag = context.reader.read_i8()?;
                if ref_flag == RefFlag::Null as i8 {
                    return Ok(None);
                }
                Ok(Some(C::read_with_type_info(
                    context,
                    RefMode::None,
                    type_info,
                )?))
            }
            RefMode::Tracking => {
                let ref_flag = context.reader.read_i8()?;
                if ref_flag == RefFlag::Null as i8 {
                    return Ok(None);
                }
                context.reader.move_back(1);
                Ok(Some(C::read_with_type_info(
                    context,
                    RefMode::Tracking,
                    type_info,
                )?))
            }
        }
    }

    #[inline(always)]
    fn default_value(_: &mut ReadContext) -> Result<Option<T>, Error> {
        Ok(None)
    }

    #[inline(always)]
    fn write_type_info(context: &mut WriteContext) -> Result<(), Error> {
        C::write_type_info(context)
    }

    #[inline(always)]
    fn read_type_info(context: &mut ReadContext) -> Result<(), Error> {
        C::read_type_info(context)
    }

    #[inline(always)]
    fn read_type_info_value(context: &mut ReadContext) -> Result<CodecReadType, Error> {
        C::read_type_info_value(context)
    }

    #[inline(always)]
    fn static_type_id() -> TypeId {
        C::static_type_id()
    }

    #[inline(always)]
    fn is_option() -> bool {
        true
    }

    #[inline(always)]
    fn is_none(value: &Option<T>) -> bool {
        value.is_none()
    }

    #[inline(always)]
    fn is_polymorphic() -> bool {
        C::is_polymorphic()
    }

    #[inline(always)]
    fn is_shared_ref() -> bool {
        C::is_shared_ref()
    }

    #[inline(always)]
    fn is_wrapper_type() -> bool {
        true
    }

    #[inline(always)]
    fn dynamic_type_id(value: &Option<T>) -> Result<Option<std::any::TypeId>, Error> {
        match value {
            Some(value) => C::dynamic_type_id(value),
            None => Ok(None),
        }
    }

    #[inline(always)]
    fn dynamic_type_is_direct() -> bool {
        C::dynamic_type_is_direct()
    }
}

macro_rules! signed_int_codec {
    ($name:ident, $ty:ty, $default_type:expr, $fixed_type:expr, $tagged_type:expr, $write_fixed:ident, $read_fixed:ident, $write_var:ident, $read_var:ident, $write_tagged:ident, $read_tagged:ident) => {
        pub struct $name<const WIRE_TYPE_ID: u8, const NULLABLE: bool, const TRACK_REF: bool>;

        impl<const WIRE_TYPE_ID: u8, const NULLABLE: bool, const TRACK_REF: bool> Codec<$ty>
            for $name<WIRE_TYPE_ID, NULLABLE, TRACK_REF>
        {
            #[inline(always)]
            fn field_type(_: &TypeResolver) -> Result<FieldType, Error> {
                Ok(FieldType::new_with_ref(
                    WIRE_TYPE_ID as u32,
                    NULLABLE,
                    TRACK_REF,
                    Vec::new(),
                ))
            }

            #[inline(always)]
            fn reserved_space() -> usize {
                std::mem::size_of::<$ty>() + 1
            }

            #[inline(always)]
            fn write_field(value: &$ty, context: &mut WriteContext) -> Result<(), Error> {
                if NULLABLE {
                    context.writer.write_i8(RefFlag::NotNullValue as i8);
                }
                Self::write_data(value, context)
            }

            #[inline(always)]
            fn read_field(context: &mut ReadContext) -> Result<$ty, Error> {
                if NULLABLE {
                    let ref_flag = context.reader.read_i8()?;
                    if ref_flag == RefFlag::Null as i8 {
                        return Self::default_value(context);
                    }
                }
                Self::read_data(context)
            }

            #[inline(always)]
            fn write_data(value: &$ty, context: &mut WriteContext) -> Result<(), Error> {
                match WIRE_TYPE_ID as u32 {
                    x if x == $fixed_type => context.writer.$write_fixed(*value),
                    x if x == $tagged_type => context.writer.$write_tagged(*value),
                    _ => context.writer.$write_var(*value),
                }
                Ok(())
            }

            #[inline(always)]
            fn read_data(context: &mut ReadContext) -> Result<$ty, Error> {
                match WIRE_TYPE_ID as u32 {
                    x if x == $fixed_type => context.reader.$read_fixed(),
                    x if x == $tagged_type => context.reader.$read_tagged(),
                    _ => context.reader.$read_var(),
                }
            }

            #[inline(always)]
            fn read_data_with_type(
                context: &mut ReadContext,
                remote_data_type: &FieldType,
            ) -> Result<$ty, Error> {
                match remote_data_type.type_id {
                    x if x == $fixed_type => context.reader.$read_fixed(),
                    x if x == $tagged_type => context.reader.$read_tagged(),
                    _ => context.reader.$read_var(),
                }
            }

            #[inline(always)]
            fn read_field_with_type(
                context: &mut ReadContext,
                remote_field_type: &FieldType,
            ) -> Result<$ty, Error> {
                if field_ref_mode(remote_field_type) != RefMode::None {
                    let ref_flag = context.reader.read_i8()?;
                    if ref_flag == RefFlag::Null as i8 {
                        return Self::default_value(context);
                    }
                }
                match remote_field_type.type_id {
                    x if x == $fixed_type => context.reader.$read_fixed(),
                    x if x == $tagged_type => context.reader.$read_tagged(),
                    _ => context.reader.$read_var(),
                }
            }

            #[inline(always)]
            fn write_with_mode(
                value: &$ty,
                context: &mut WriteContext,
                ref_mode: RefMode,
                write_type_info: bool,
                _has_generics: bool,
            ) -> Result<(), Error> {
                if ref_mode != RefMode::None {
                    context.writer.write_i8(RefFlag::NotNullValue as i8);
                }
                if write_type_info {
                    Self::write_type_info(context)?;
                }
                Self::write_data(value, context)
            }

            #[inline(always)]
            fn read_with_mode(
                context: &mut ReadContext,
                ref_mode: RefMode,
                read_type_info: bool,
            ) -> Result<$ty, Error> {
                if ref_mode != RefMode::None {
                    let ref_flag = context.reader.read_i8()?;
                    if ref_flag == RefFlag::Null as i8 {
                        return Self::default_value(context);
                    }
                }
                if read_type_info {
                    let remote_field_type = Self::read_type_info_as_field_type(context)?;
                    return Self::read_data_with_type(context, &remote_field_type);
                }
                Self::read_data(context)
            }

            #[inline(always)]
            fn read_with_type_info(
                context: &mut ReadContext,
                ref_mode: RefMode,
                _type_info: &std::rc::Rc<crate::TypeInfo>,
            ) -> Result<$ty, Error> {
                Self::read_with_mode(context, ref_mode, false)
            }

            #[inline(always)]
            fn default_value(_: &mut ReadContext) -> Result<$ty, Error> {
                Ok(0 as $ty)
            }

            #[inline(always)]
            fn write_type_info(context: &mut WriteContext) -> Result<(), Error> {
                context.writer.write_var_u32(WIRE_TYPE_ID as u32);
                Ok(())
            }

            #[inline(always)]
            fn read_type_info(context: &mut ReadContext) -> Result<(), Error> {
                let remote = context.reader.read_var_u32()?;
                if !same_numeric_family($default_type, remote) {
                    return Err(numeric_type_mismatch($default_type, remote));
                }
                Ok(())
            }

            #[inline(always)]
            fn read_type_info_as_field_type(context: &mut ReadContext) -> Result<FieldType, Error> {
                let remote = context.reader.read_var_u32()?;
                if !same_numeric_family($default_type, remote) {
                    return Err(numeric_type_mismatch($default_type, remote));
                }
                Ok(FieldType::new(remote, false, Vec::new()))
            }

            #[inline(always)]
            fn static_type_id() -> TypeId {
                TypeId::try_from(WIRE_TYPE_ID).unwrap_or(TypeId::UNKNOWN)
            }
        }
    };
}

#[cold]
#[inline(never)]
fn numeric_type_mismatch(expected: u32, actual: u32) -> Error {
    Error::type_mismatch(expected, actual)
}

signed_int_codec!(
    I32Codec,
    i32,
    type_id::VARINT32,
    type_id::INT32,
    UNKNOWN,
    write_i32,
    read_i32,
    write_var_i32,
    read_var_i32,
    write_var_i32,
    read_var_i32
);
signed_int_codec!(
    I64Codec,
    i64,
    type_id::VARINT64,
    type_id::INT64,
    type_id::TAGGED_INT64,
    write_i64,
    read_i64,
    write_var_i64,
    read_var_i64,
    write_tagged_i64,
    read_tagged_i64
);
signed_int_codec!(
    U32Codec,
    u32,
    type_id::VAR_UINT32,
    type_id::UINT32,
    UNKNOWN,
    write_u32,
    read_u32,
    write_var_u32,
    read_var_u32,
    write_var_u32,
    read_var_u32
);
signed_int_codec!(
    U64Codec,
    u64,
    type_id::VAR_UINT64,
    type_id::UINT64,
    type_id::TAGGED_UINT64,
    write_u64,
    read_u64,
    write_var_u64,
    read_var_u64,
    write_tagged_u64,
    read_tagged_u64
);

pub struct VecCodec<
    T,
    C,
    const STRUCTURAL_LIST: bool,
    const DENSE_ARRAY: bool,
    const NULLABLE: bool,
    const TRACK_REF: bool,
>(PhantomData<(T, C)>);

#[cold]
#[inline(never)]
fn dense_array_requires_primitive<T: 'static>() -> Error {
    Error::type_error(format!(
        "explicit dense array encoding requires a canonical primitive target, got {}",
        std::any::type_name::<T>(),
    ))
}

#[cold]
#[inline(never)]
fn non_polymorphic_vec() -> Error {
    Error::type_error("Type inconsistent, target collection element type is not polymorphic")
}

#[cold]
#[inline(never)]
fn list_type_mismatch(remote: u32) -> Error {
    Error::type_mismatch(TypeId::LIST as u32, remote)
}

#[cold]
#[inline(never)]
fn missing_vec_dynamic_type() -> Error {
    Error::type_error("Unable to determine concrete type for polymorphic collection elements")
}

#[cold]
#[inline(never)]
fn declared_polymorphic_vec() -> Error {
    Error::invalid_data("polymorphic collection element metadata must precede collection elements")
}

#[inline(always)]
fn selected_vec_type_id<T, C, const STRUCTURAL_LIST: bool, const DENSE_ARRAY: bool>(
) -> Result<Option<TypeId>, Error>
where
    T: 'static,
    C: Codec<T>,
{
    if STRUCTURAL_LIST {
        return Ok(None);
    }
    match primitive_list::array_type_id::<T, C>(DENSE_ARRAY) {
        Some(type_id) => Ok(Some(type_id)),
        None if DENSE_ARRAY => Err(dense_array_requires_primitive::<T>()),
        None => Ok(None),
    }
}

#[inline(always)]
fn check_sequence_len<T>(context: &ReadContext, len: u32) -> Result<usize, Error> {
    let len = len as usize;
    // RawVec does not allocate backing storage for a ZST. Non-ZST Vec bodies
    // still need proportional readable bytes before reserving from wire length.
    if std::mem::size_of::<T>() != 0 {
        context.reader.check_bound(len)?;
    }
    Ok(len)
}

#[inline(always)]
fn check_vec_write_len<T>(
    context: &WriteContext,
    body_offset: usize,
    len: usize,
) -> Result<(), Error> {
    if std::mem::size_of::<T>() != 0 {
        return check_count_write_bytes(context, body_offset, len);
    }
    Ok(())
}

#[inline(always)]
fn read_vec_items<T, C>(
    context: &mut ReadContext,
    len: u32,
    has_null: bool,
    read_type: Option<ElementReadType<'_>>,
) -> Result<Vec<T>, Error>
where
    T: 'static,
    C: Codec<T>,
{
    // The owning Vec reader validates the declared length before consuming
    // shared element metadata. Rechecking here would reject valid ZST bodies
    // after that metadata reaches the end of the input.
    let mut vec = Vec::with_capacity(len as usize);
    match read_type {
        None | Some(ElementReadType::Direct) => {
            if has_null {
                for _ in 0..len {
                    let flag = context.reader.read_i8()?;
                    if flag == RefFlag::Null as i8 {
                        vec.push(C::default_value(context)?);
                    } else {
                        vec.push(C::read_data(context)?);
                    }
                }
            } else {
                for _ in 0..len {
                    vec.push(C::read_data(context)?);
                }
            }
        }
        Some(ElementReadType::Field(field_type)) => {
            if has_null {
                for _ in 0..len {
                    let flag = context.reader.read_i8()?;
                    if flag == RefFlag::Null as i8 {
                        vec.push(C::default_value(context)?);
                    } else {
                        vec.push(C::read_data_with_type(context, &field_type)?);
                    }
                }
            } else {
                for _ in 0..len {
                    vec.push(C::read_data_with_type(context, &field_type)?);
                }
            }
        }
        Some(ElementReadType::TypeInfo(type_info)) => {
            if has_null {
                for _ in 0..len {
                    let flag = context.reader.read_i8()?;
                    if flag == RefFlag::Null as i8 {
                        vec.push(C::default_value(context)?);
                    } else {
                        vec.push(C::read_data_with_type_info(context, &type_info)?);
                    }
                }
            } else {
                for _ in 0..len {
                    vec.push(C::read_data_with_type_info(context, &type_info)?);
                }
            }
        }
    }
    Ok(vec)
}

#[inline(always)]
fn write_vec_data<T, C, const STRUCTURAL_LIST: bool, const DENSE_ARRAY: bool>(
    value: &Vec<T>,
    context: &mut WriteContext,
    has_generics: bool,
) -> Result<(), Error>
where
    T: 'static,
    C: Codec<T>,
{
    if let Some(type_id) = selected_vec_type_id::<T, C, STRUCTURAL_LIST, DENSE_ARRAY>()? {
        return primitive_list::write_data::<T, C>(value, context, type_id);
    }
    write_object_vec_data::<T, C>(value, context, has_generics)
}

fn write_object_vec_data<T, C>(
    value: &Vec<T>,
    context: &mut WriteContext,
    has_generics: bool,
) -> Result<(), Error>
where
    T: 'static,
    C: Codec<T>,
{
    let len = value.len();
    context.writer.write_var_u32(len as u32);
    if len == 0 {
        return Ok(());
    }
    let body_offset = context.writer.len();
    if C::is_polymorphic() || C::is_shared_ref() {
        write_vec_dynamic::<T, C>(value, context, has_generics)?;
        return check_vec_write_len::<T>(context, body_offset, len);
    }
    let mut header = IS_SAME_TYPE;
    let mut has_null = false;
    if C::is_option() {
        for item in value {
            if C::is_none(item) {
                has_null = true;
                break;
            }
        }
    }
    if has_null {
        header |= HAS_NULL;
    }
    if has_generics && !need_to_write_type_for_field(C::static_type_id()) {
        header |= DECL_ELEMENT_TYPE;
        context.writer.write_u8(header);
    } else {
        context.writer.write_u8(header);
        C::write_type_info(context)?;
    }
    context.writer.reserve(len * C::reserved_space());
    if has_null {
        // Null detection already inspected nullable holders. Let the codec
        // own the null envelope so each holder is accessed once here.
        for item in value {
            C::write_with_mode(item, context, RefMode::NullOnly, false, has_generics)?;
        }
    } else if has_generics {
        for item in value {
            C::write_with_mode(item, context, RefMode::None, false, true)?;
        }
    } else {
        for item in value {
            C::write_data(item, context)?;
        }
    }
    check_vec_write_len::<T>(context, body_offset, len)
}

#[inline(always)]
fn read_vec_data<
    T,
    C,
    const STRUCTURAL_LIST: bool,
    const DENSE_ARRAY: bool,
    const NULLABLE: bool,
    const TRACK_REF: bool,
>(
    context: &mut ReadContext,
) -> Result<Vec<T>, Error>
where
    T: 'static,
    C: Codec<T>,
{
    if let Some(type_id) = selected_vec_type_id::<T, C, STRUCTURAL_LIST, DENSE_ARRAY>()? {
        return primitive_list::read_vec::<T, C>(context, type_id);
    }
    read_object_vec_data::<T, C, STRUCTURAL_LIST, DENSE_ARRAY, NULLABLE, TRACK_REF>(context)
}

fn read_object_vec_data<
    T,
    C,
    const STRUCTURAL_LIST: bool,
    const DENSE_ARRAY: bool,
    const NULLABLE: bool,
    const TRACK_REF: bool,
>(
    context: &mut ReadContext,
) -> Result<Vec<T>, Error>
where
    T: 'static,
    C: Codec<T>,
{
    let len = context.reader.read_var_u32()?;
    check_sequence_len::<T>(context, len)?;
    reserve_graph_storage(context, len, std::mem::size_of::<T>())?;
    if len == 0 {
        return Ok(Vec::new());
    }
    let header = context.reader.read_u8()?;
    if C::is_polymorphic() || C::is_shared_ref() {
        let field_type =
            VecCodec::<T, C, STRUCTURAL_LIST, DENSE_ARRAY, NULLABLE, TRACK_REF>::field_type(
                context.get_type_resolver(),
            )?;
        return read_vec_dynamic_items::<T, C>(context, len, header, &field_type);
    }
    if (header & IS_SAME_TYPE) == 0 {
        return Err(non_polymorphic_vec());
    }
    let read_type = if (header & DECL_ELEMENT_TYPE) == 0 {
        let codec_read_type = C::read_type_info_value(context)?;
        Some(element_read_type::<T, C>(context, codec_read_type)?)
    } else {
        None
    };
    let has_null = (header & HAS_NULL) != 0;
    read_vec_items::<T, C>(context, len, has_null, read_type)
}

impl<
        T,
        C,
        const STRUCTURAL_LIST: bool,
        const DENSE_ARRAY: bool,
        const NULLABLE: bool,
        const TRACK_REF: bool,
    > Codec<Vec<T>> for VecCodec<T, C, STRUCTURAL_LIST, DENSE_ARRAY, NULLABLE, TRACK_REF>
where
    T: 'static,
    C: Codec<T>,
{
    #[inline(always)]
    fn field_type(type_resolver: &TypeResolver) -> Result<FieldType, Error> {
        if let Some(type_id) = selected_vec_type_id::<T, C, STRUCTURAL_LIST, DENSE_ARRAY>()? {
            return Ok(FieldType::new_with_ref(
                type_id as u32,
                NULLABLE,
                TRACK_REF,
                Vec::new(),
            ));
        }
        let element_type = C::field_type(type_resolver)?;
        Ok(FieldType::new_with_ref(
            TypeId::LIST as u32,
            NULLABLE,
            TRACK_REF,
            vec![element_type],
        ))
    }

    #[inline(always)]
    fn reserved_space() -> usize {
        if !STRUCTURAL_LIST && primitive_list::array_type_id::<T, C>(DENSE_ARRAY).is_some() {
            primitive_list::reserved_space::<T>() + SIZE_OF_REF_AND_TYPE
        } else {
            std::mem::size_of::<u32>() + SIZE_OF_REF_AND_TYPE
        }
    }

    #[inline(always)]
    fn write_field(value: &Vec<T>, context: &mut WriteContext) -> Result<(), Error> {
        if NULLABLE || TRACK_REF {
            context.writer.write_i8(RefFlag::NotNullValue as i8);
        }
        write_vec_data::<T, C, STRUCTURAL_LIST, DENSE_ARRAY>(value, context, true)
    }

    #[inline(always)]
    fn read_field(context: &mut ReadContext) -> Result<Vec<T>, Error> {
        if NULLABLE || TRACK_REF {
            let ref_flag = context.reader.read_i8()?;
            if ref_flag == RefFlag::Null as i8 {
                return Ok(Vec::new());
            }
        }
        Self::read_data(context)
    }

    #[inline(always)]
    fn read_compatible(
        context: &mut ReadContext,
        local_field_type: &FieldType,
        remote_field_type: &FieldType,
    ) -> Result<Option<Vec<T>>, Error> {
        if selected_vec_type_id::<T, C, STRUCTURAL_LIST, DENSE_ARRAY>()?.is_some() {
            if field_types_compatible(local_field_type, remote_field_type) {
                return Self::read_field_with_type(context, remote_field_type).map(Some);
            }
            return read_primitive_array_vec_mismatch::<T, C>(
                context,
                local_field_type,
                remote_field_type,
            );
        }
        if field_types_compatible(local_field_type, remote_field_type)
            || local_field_type.compatible_shape_match(remote_field_type)
        {
            return Self::read_field_with_type(context, remote_field_type).map(Some);
        }
        if local_field_type.type_id == remote_field_type.type_id
            && allows_missing_generics(local_field_type.type_id)
            && (local_field_type.generics.is_empty() || remote_field_type.generics.is_empty())
        {
            return Self::read_field_with_type(context, remote_field_type).map(Some);
        }
        read_vec_compatible_mismatch::<T, C>(context, local_field_type, remote_field_type)
    }

    #[inline(always)]
    fn write_data(value: &Vec<T>, context: &mut WriteContext) -> Result<(), Error> {
        write_vec_data::<T, C, STRUCTURAL_LIST, DENSE_ARRAY>(value, context, false)
    }

    #[inline(always)]
    fn read_data(context: &mut ReadContext) -> Result<Vec<T>, Error> {
        read_vec_data::<T, C, STRUCTURAL_LIST, DENSE_ARRAY, NULLABLE, TRACK_REF>(context)
    }

    fn read_data_with_type(
        context: &mut ReadContext,
        remote_field_type: &FieldType,
    ) -> Result<Vec<T>, Error> {
        if let Some(type_id) = selected_vec_type_id::<T, C, STRUCTURAL_LIST, DENSE_ARRAY>()? {
            if remote_field_type.type_id == TypeId::LIST as u32 {
                return super::collection::read_list_as_primitive_vec::<T, C>(
                    context,
                    remote_field_type,
                );
            }
            return primitive_list::read_vec::<T, C>(context, type_id);
        }
        let len = context.reader.read_var_u32()?;
        check_sequence_len::<T>(context, len)?;
        reserve_graph_storage(context, len, std::mem::size_of::<T>())?;
        if len == 0 {
            return Ok(Vec::new());
        }
        let header = context.reader.read_u8()?;
        let has_null = (header & HAS_NULL) != 0;
        let is_same_type = (header & IS_SAME_TYPE) != 0;
        let is_declared = (header & DECL_ELEMENT_TYPE) != 0;
        if C::is_polymorphic() || C::is_shared_ref() {
            return read_vec_dynamic_items::<T, C>(context, len, header, remote_field_type);
        }
        if !is_same_type {
            return Err(non_polymorphic_vec());
        }
        let read_type = if is_declared {
            ElementReadType::Field(Cow::Borrowed(generic_field_type(
                remote_field_type,
                0,
                "list",
            )?))
        } else {
            let codec_read_type = C::read_type_info_value(context)?;
            element_read_type::<T, C>(context, codec_read_type)?
        };
        read_vec_items::<T, C>(context, len, has_null, Some(read_type))
    }

    #[inline(always)]
    fn read_field_with_type(
        context: &mut ReadContext,
        remote_field_type: &FieldType,
    ) -> Result<Vec<T>, Error> {
        if field_ref_mode(remote_field_type) != RefMode::None {
            let ref_flag = context.reader.read_i8()?;
            if ref_flag == RefFlag::Null as i8 {
                return Ok(Vec::new());
            }
        }
        Self::read_data_with_type(context, remote_field_type)
    }

    #[inline(always)]
    fn write_with_mode(
        value: &Vec<T>,
        context: &mut WriteContext,
        ref_mode: RefMode,
        write_type_info: bool,
        has_generics: bool,
    ) -> Result<(), Error> {
        if ref_mode != RefMode::None {
            context.writer.write_i8(RefFlag::NotNullValue as i8);
        }
        if write_type_info {
            Self::write_type_info(context)?;
        }
        write_vec_data::<T, C, STRUCTURAL_LIST, DENSE_ARRAY>(value, context, has_generics)
    }

    #[inline(always)]
    fn read_with_mode(
        context: &mut ReadContext,
        ref_mode: RefMode,
        read_type_info: bool,
    ) -> Result<Vec<T>, Error> {
        if ref_mode != RefMode::None {
            let ref_flag = context.reader.read_i8()?;
            if ref_flag == RefFlag::Null as i8 {
                return Ok(Vec::new());
            }
        }
        if read_type_info {
            Self::read_type_info(context)?;
        }
        Self::read_data(context)
    }

    #[inline(always)]
    fn read_with_type_info(
        context: &mut ReadContext,
        ref_mode: RefMode,
        _type_info: &std::rc::Rc<crate::TypeInfo>,
    ) -> Result<Vec<T>, Error> {
        Self::read_with_mode(context, ref_mode, false)
    }

    #[inline(always)]
    fn default_value(_: &mut ReadContext) -> Result<Vec<T>, Error> {
        Ok(Vec::new())
    }

    #[inline(always)]
    fn write_type_info(context: &mut WriteContext) -> Result<(), Error> {
        match selected_vec_type_id::<T, C, STRUCTURAL_LIST, DENSE_ARRAY>()? {
            Some(type_id) => primitive_list::write_type_info(context, type_id),
            None => {
                context.writer.write_u8(TypeId::LIST as u8);
                Ok(())
            }
        }
    }

    #[inline(always)]
    fn read_type_info(context: &mut ReadContext) -> Result<(), Error> {
        if let Some(type_id) = selected_vec_type_id::<T, C, STRUCTURAL_LIST, DENSE_ARRAY>()? {
            return primitive_list::read_type_info(context, type_id);
        }
        let remote = context.reader.read_u8()? as u32;
        if remote != TypeId::LIST as u32 {
            return Err(list_type_mismatch(remote));
        }
        Ok(())
    }

    #[inline(always)]
    fn static_type_id() -> TypeId {
        if STRUCTURAL_LIST {
            return TypeId::LIST;
        }
        match primitive_list::array_type_id::<T, C>(DENSE_ARRAY) {
            Some(type_id) => type_id,
            None => TypeId::LIST,
        }
    }
}

fn write_vec_dynamic<T, C>(
    value: &Vec<T>,
    context: &mut WriteContext,
    has_generics: bool,
) -> Result<(), Error>
where
    T: 'static,
    C: Codec<T>,
{
    let elem_is_polymorphic = C::is_polymorphic();
    let dynamic_type_is_direct = !elem_is_polymorphic || C::dynamic_type_is_direct();
    let mut has_null = elem_is_polymorphic && !dynamic_type_is_direct;
    let mut is_same_type = dynamic_type_is_direct;
    let mut first_type_id: Option<std::any::TypeId> = None;
    if dynamic_type_is_direct {
        for item in value {
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
        is_same_type = false;
    }
    let mut header = 0u8;
    if has_null {
        header |= HAS_NULL;
    }
    if has_generics && !need_to_write_type_for_field(C::static_type_id()) {
        header |= DECL_ELEMENT_TYPE;
    }
    if is_same_type {
        header |= IS_SAME_TYPE;
    }
    if C::is_shared_ref() {
        header |= TRACKING_REF;
    }
    context.writer.write_u8(header);
    let type_info = if is_same_type && (header & DECL_ELEMENT_TYPE) == 0 {
        if elem_is_polymorphic {
            let type_id = first_type_id.ok_or_else(missing_vec_dynamic_type)?;
            Some(C::write_type_info_value(context, type_id)?)
        } else {
            C::write_type_info(context)?;
            None
        }
    } else {
        None
    };
    let elem_ref_mode = if C::is_shared_ref() {
        RefMode::Tracking
    } else if has_null {
        RefMode::NullOnly
    } else {
        RefMode::None
    };
    if is_same_type {
        if let Some(type_info) = type_info.as_ref() {
            for item in value {
                C::write_with_type_info(item, context, elem_ref_mode, type_info, has_generics)?;
            }
        } else if elem_ref_mode == RefMode::None {
            if has_generics {
                for item in value {
                    C::write_with_mode(item, context, RefMode::None, false, true)?;
                }
            } else {
                for item in value {
                    C::write_data(item, context)?;
                }
            }
        } else {
            for item in value {
                C::write_with_mode(item, context, elem_ref_mode, false, has_generics)?;
            }
        }
    } else {
        for item in value {
            C::write_with_mode(item, context, elem_ref_mode, true, has_generics)?;
        }
    }
    Ok(())
}

fn read_vec_dynamic_items<T, C>(
    context: &mut ReadContext,
    len: u32,
    header: u8,
    remote_field_type: &FieldType,
) -> Result<Vec<T>, Error>
where
    T: 'static,
    C: Codec<T>,
{
    let is_track_ref = (header & TRACKING_REF) != 0;
    let has_null = (header & HAS_NULL) != 0;
    let is_same_type = (header & IS_SAME_TYPE) != 0;
    let is_declared = (header & DECL_ELEMENT_TYPE) != 0;
    let elem_ref_mode = if is_track_ref {
        RefMode::Tracking
    } else if has_null {
        RefMode::NullOnly
    } else {
        RefMode::None
    };
    // Length validation happens before the shared dynamic metadata above this
    // helper is consumed, including for zero-sized concrete targets.
    let mut vec = Vec::with_capacity(len as usize);
    if is_same_type {
        if C::is_polymorphic() {
            if is_declared {
                return Err(declared_polymorphic_vec());
            }
            let type_info = context.read_any_type_info()?;
            for _ in 0..len {
                vec.push(C::read_with_type_info(context, elem_ref_mode, &type_info)?);
            }
        } else if is_declared {
            let field_type = generic_field_type(remote_field_type, 0, "list")?;
            let field_type = field_type_with_ref_flags(field_type, has_null, is_track_ref);
            for _ in 0..len {
                vec.push(C::read_field_with_type(context, &field_type)?);
            }
        } else {
            match C::read_type_info_value(context)? {
                CodecReadType::TypeInfo(type_info) => {
                    for _ in 0..len {
                        vec.push(C::read_with_type_info(context, elem_ref_mode, &type_info)?);
                    }
                }
                CodecReadType::Field(mut field_type) => {
                    field_type.nullable = has_null;
                    field_type.track_ref = is_track_ref;
                    for _ in 0..len {
                        vec.push(C::read_field_with_type(context, &field_type)?);
                    }
                }
            }
        }
    } else {
        for _ in 0..len {
            vec.push(C::read_with_mode(context, elem_ref_mode, true)?);
        }
    }
    Ok(vec)
}

#[inline(always)]
fn any_field_type<const NULLABLE: bool, const TRACK_REF: bool>() -> FieldType {
    FieldType::new_with_ref(TypeId::UNKNOWN as u32, NULLABLE, TRACK_REF, Vec::new())
}

#[inline(always)]
fn any_ref_mode<const NULLABLE: bool, const TRACK_REF: bool>() -> RefMode {
    if TRACK_REF {
        RefMode::Tracking
    } else if NULLABLE {
        RefMode::NullOnly
    } else {
        RefMode::None
    }
}

macro_rules! any_codec {
    ($name:ident, $ty:ty) => {
        pub struct $name<const NULLABLE: bool, const TRACK_REF: bool>;

        impl<const NULLABLE: bool, const TRACK_REF: bool> Codec<$ty>
            for $name<NULLABLE, TRACK_REF>
        {
            #[inline(always)]
            fn field_type(_: &TypeResolver) -> Result<FieldType, Error> {
                Ok(any_field_type::<NULLABLE, TRACK_REF>())
            }

            #[inline(always)]
            fn reserved_space() -> usize {
                <$ty as Serializer>::reserved_space() + SIZE_OF_REF_AND_TYPE
            }

            #[inline(always)]
            fn write_field(value: &$ty, context: &mut WriteContext) -> Result<(), Error> {
                <$ty as Serializer>::write_value(
                    value,
                    context,
                    any_ref_mode::<NULLABLE, TRACK_REF>(),
                    field_write_type_info::<$ty>(context),
                    false,
                )
            }

            #[inline(always)]
            fn write_type_info_value(
                context: &mut WriteContext,
                target_type_id: std::any::TypeId,
            ) -> Result<Rc<crate::TypeInfo>, Error> {
                <$ty as Serializer>::write_type_info_value(context, target_type_id)
            }

            #[inline(always)]
            fn write_with_type_info(
                value: &$ty,
                context: &mut WriteContext,
                ref_mode: RefMode,
                type_info: &Rc<crate::TypeInfo>,
                has_generics: bool,
            ) -> Result<(), Error> {
                <$ty as Serializer>::write_with_type_info(
                    value,
                    context,
                    ref_mode,
                    type_info,
                    has_generics,
                )
            }

            #[inline(always)]
            fn read_field(context: &mut ReadContext) -> Result<$ty, Error> {
                <$ty as Serializer>::read_value(
                    context,
                    any_ref_mode::<NULLABLE, TRACK_REF>(),
                    codec_read_type_info_static::<$ty, Self>(context),
                )
            }

            #[inline(always)]
            fn write_data(value: &$ty, context: &mut WriteContext) -> Result<(), Error> {
                <$ty as Serializer>::write_with_generics(value, context, false)
            }

            #[inline(always)]
            fn read_data(context: &mut ReadContext) -> Result<$ty, Error> {
                <$ty as Serializer>::read(context)
            }

            #[inline(always)]
            fn read_field_with_type(
                context: &mut ReadContext,
                remote_field_type: &FieldType,
            ) -> Result<$ty, Error> {
                <$ty as Serializer>::read_value(
                    context,
                    field_ref_mode(remote_field_type),
                    codec_read_type_info::<$ty, Self>(context, remote_field_type),
                )
            }

            #[inline(always)]
            fn write_with_mode(
                value: &$ty,
                context: &mut WriteContext,
                ref_mode: RefMode,
                write_type_info: bool,
                has_generics: bool,
            ) -> Result<(), Error> {
                <$ty as Serializer>::write_value(
                    value,
                    context,
                    ref_mode,
                    write_type_info,
                    has_generics,
                )
            }

            #[inline(always)]
            fn read_with_mode(
                context: &mut ReadContext,
                ref_mode: RefMode,
                read_type_info: bool,
            ) -> Result<$ty, Error> {
                <$ty as Serializer>::read_value(context, ref_mode, read_type_info)
            }

            #[inline(always)]
            fn read_with_type_info(
                context: &mut ReadContext,
                ref_mode: RefMode,
                type_info: &Rc<crate::TypeInfo>,
            ) -> Result<$ty, Error> {
                <$ty as Serializer>::read_with_type_info(context, ref_mode, type_info)
            }

            #[inline(always)]
            fn default_value(context: &mut ReadContext) -> Result<$ty, Error> {
                <$ty as Serializer>::default_value(context)
            }

            #[inline(always)]
            fn write_type_info(_context: &mut WriteContext) -> Result<(), Error> {
                Ok(())
            }

            #[inline(always)]
            fn read_type_info(_context: &mut ReadContext) -> Result<(), Error> {
                Ok(())
            }

            #[inline(always)]
            fn read_type_info_value(context: &mut ReadContext) -> Result<CodecReadType, Error> {
                context.read_any_type_info().map(CodecReadType::TypeInfo)
            }

            #[inline(always)]
            fn static_type_id() -> TypeId {
                TypeId::UNKNOWN
            }

            #[inline(always)]
            fn is_polymorphic() -> bool {
                true
            }

            #[inline(always)]
            fn is_shared_ref() -> bool {
                <$ty as Serializer>::is_shared_ref()
            }

            #[inline(always)]
            fn dynamic_type_id(value: &$ty) -> Result<Option<std::any::TypeId>, Error> {
                <$ty as Serializer>::dynamic_type_id(value)
            }

            #[inline(always)]
            fn dynamic_type_is_direct() -> bool {
                <$ty as Serializer>::dynamic_type_is_direct()
            }
        }
    };
}

any_codec!(AnyBoxCodec, Box<dyn Any>);
any_codec!(AnyRcCodec, Rc<dyn Any>);
any_codec!(AnyArcCodec, Arc<dyn Any + Send + Sync>);

#[cfg(test)]
mod tests {
    use super::{
        compatible_field_pair, field_types_compatible, Codec, CodecReadType, VecCodec,
        DECL_ELEMENT_TYPE, IS_SAME_TYPE, TRACKING_REF,
    };
    use crate::buffer::Reader;
    use crate::config::Config;
    use crate::context::{ReadContext, WriteContext};
    use crate::error::Error;
    use crate::meta::FieldType;
    use crate::resolver::{RefMode, TypeResolver};
    use crate::type_id::{self, TypeId};
    use std::cell::{Cell, RefCell};
    use std::rc::Rc;

    #[derive(Debug, PartialEq)]
    struct MetadataProbe(u8);

    struct MetadataProbeCodec;

    thread_local! {
        static EXPECTED_FIELD: Cell<*const FieldType> =
            const { Cell::new(std::ptr::null()) };
        static PROBE_FIELD_READS: Cell<usize> = const { Cell::new(0) };
        static PROBE_TYPE_INFO: RefCell<Option<Rc<crate::TypeInfo>>> =
            const { RefCell::new(None) };
        static PROBE_INFO_READS: Cell<usize> = const { Cell::new(0) };
    }

    impl Codec<MetadataProbe> for MetadataProbeCodec {
        fn field_type(_type_resolver: &TypeResolver) -> Result<FieldType, Error> {
            Ok(FieldType::new_with_ref(
                type_id::INT8,
                false,
                true,
                Vec::new(),
            ))
        }

        fn write_field(value: &MetadataProbe, context: &mut WriteContext) -> Result<(), Error> {
            Self::write_data(value, context)
        }

        fn read_field(context: &mut ReadContext) -> Result<MetadataProbe, Error> {
            Self::read_data(context)
        }

        fn write_data(value: &MetadataProbe, context: &mut WriteContext) -> Result<(), Error> {
            context.writer.write_u8(value.0);
            Ok(())
        }

        fn read_data(context: &mut ReadContext) -> Result<MetadataProbe, Error> {
            context.reader.read_u8().map(MetadataProbe)
        }

        fn read_data_with_type(
            context: &mut ReadContext,
            remote_data_type: &FieldType,
        ) -> Result<MetadataProbe, Error> {
            EXPECTED_FIELD.with(|expected| {
                assert!(std::ptr::eq(remote_data_type, expected.get()));
            });
            PROBE_FIELD_READS.with(|reads| reads.set(reads.get() + 1));
            Self::read_data(context)
        }

        fn read_field_with_type(
            context: &mut ReadContext,
            remote_field_type: &FieldType,
        ) -> Result<MetadataProbe, Error> {
            Self::read_data_with_type(context, remote_field_type)
        }

        fn write_with_mode(
            value: &MetadataProbe,
            context: &mut WriteContext,
            _ref_mode: RefMode,
            _write_type_info: bool,
            _has_generics: bool,
        ) -> Result<(), Error> {
            Self::write_data(value, context)
        }

        fn read_with_mode(
            context: &mut ReadContext,
            _ref_mode: RefMode,
            _read_type_info: bool,
        ) -> Result<MetadataProbe, Error> {
            Self::read_data(context)
        }

        fn read_with_type_info(
            context: &mut ReadContext,
            ref_mode: RefMode,
            type_info: &Rc<crate::TypeInfo>,
        ) -> Result<MetadataProbe, Error> {
            assert_eq!(ref_mode, RefMode::Tracking);
            PROBE_TYPE_INFO.with(|canonical| {
                let canonical = canonical.borrow();
                let canonical = canonical.as_ref().expect("canonical TypeInfo");
                assert!(Rc::ptr_eq(type_info, canonical));
                assert_eq!(Rc::strong_count(type_info), 2);
            });
            PROBE_INFO_READS.with(|reads| reads.set(reads.get() + 1));
            Self::read_data(context)
        }

        fn default_value(_context: &mut ReadContext) -> Result<MetadataProbe, Error> {
            Ok(MetadataProbe(0))
        }

        fn write_type_info(context: &mut WriteContext) -> Result<(), Error> {
            context.writer.write_u8(TypeId::INT8 as u8);
            Ok(())
        }

        fn read_type_info(context: &mut ReadContext) -> Result<(), Error> {
            let _ = context.reader.read_u8()?;
            Ok(())
        }

        fn read_type_info_value(_context: &mut ReadContext) -> Result<CodecReadType, Error> {
            PROBE_TYPE_INFO.with(|canonical| {
                let canonical = canonical.borrow();
                Ok(CodecReadType::TypeInfo(Rc::clone(
                    canonical.as_ref().expect("canonical TypeInfo"),
                )))
            })
        }

        fn static_type_id() -> TypeId {
            TypeId::INT8
        }

        fn is_shared_ref() -> bool {
            true
        }
    }

    type ProbeVecCodec = VecCodec<MetadataProbe, MetadataProbeCodec, true, false, false, false>;

    fn probe_context(bytes: &[u8]) -> ReadContext<'_> {
        let config = Config::default();
        let graph_memory = config.max_graph_memory_bytes;
        let mut context = ReadContext::new(TypeResolver::default(), config);
        context.remaining_graph_memory_bytes = graph_memory;
        context.attach_reader(Reader::new(bytes));
        context
    }

    #[test]
    fn byte_sequence_compatibility() {
        let bytes = FieldType::new(type_id::BINARY, false, vec![]);
        let uint8_array = FieldType::new(type_id::UINT8_ARRAY, false, vec![]);
        let int8_array = FieldType::new(type_id::INT8_ARRAY, false, vec![]);

        assert!(!field_types_compatible(&bytes, &uint8_array));
        assert!(!field_types_compatible(&uint8_array, &bytes));
        assert!(compatible_field_pair(&bytes, &uint8_array));
        assert!(compatible_field_pair(&uint8_array, &bytes));
        assert!(!field_types_compatible(&bytes, &int8_array));
        assert!(!field_types_compatible(&int8_array, &bytes));
        assert!(!compatible_field_pair(&bytes, &int8_array));
        assert!(!compatible_field_pair(&int8_array, &bytes));
    }

    #[test]
    fn scalar_ref_tracking_rules() {
        let bool_value = FieldType::new(type_id::BOOL, false, vec![]);
        let ref_bool = FieldType::new_with_ref(type_id::BOOL, false, true, vec![]);

        assert!(!field_types_compatible(&bool_value, &ref_bool));
        assert!(!field_types_compatible(&ref_bool, &bool_value));
        assert!(field_types_compatible(&ref_bool, &ref_bool));
        let nullable_ref_bool = FieldType::new_with_ref(type_id::BOOL, true, true, vec![]);
        assert!(field_types_compatible(
            &nullable_ref_bool,
            &nullable_ref_bool
        ));
        assert!(!field_types_compatible(&ref_bool, &nullable_ref_bool));
        assert!(!field_types_compatible(&nullable_ref_bool, &ref_bool));

        let fixed_i32 = FieldType::new(type_id::INT32, false, vec![]);
        let var_i32 = FieldType::new(type_id::VARINT32, false, vec![]);
        assert!(!field_types_compatible(&fixed_i32, &var_i32));
        assert!(compatible_field_pair(&fixed_i32, &var_i32));

        let ref_fixed_i32 = FieldType::new_with_ref(type_id::INT32, false, true, vec![]);
        let ref_var_i32 = FieldType::new_with_ref(type_id::VARINT32, false, true, vec![]);
        assert!(!field_types_compatible(&ref_fixed_i32, &ref_var_i32));
    }

    #[test]
    fn compatible_field_pair_rules() {
        let int8 = FieldType::new(type_id::INT8, false, vec![]);
        let int16 = FieldType::new(type_id::INT16, false, vec![]);
        assert!(compatible_field_pair(&int16, &int8));

        let ref_int16 = FieldType::new_with_ref(type_id::INT16, false, true, vec![]);
        assert!(!compatible_field_pair(&ref_int16, &int8));

        let list_i8 = FieldType::new(type_id::LIST, false, vec![int8]);
        let list_i16 = FieldType::new(type_id::LIST, false, vec![int16]);
        assert!(!compatible_field_pair(&list_i16, &list_i8));

        let list_fixed_i32 = FieldType::new(
            type_id::LIST,
            false,
            vec![FieldType::new(type_id::INT32, false, vec![])],
        );
        let list_var_i32 = FieldType::new(
            type_id::LIST,
            false,
            vec![FieldType::new(type_id::VARINT32, false, vec![])],
        );
        assert!(!field_types_compatible(&list_fixed_i32, &list_var_i32));
        assert!(!compatible_field_pair(&list_fixed_i32, &list_var_i32));

        let list_nullable_i32 = FieldType::new(
            type_id::LIST,
            false,
            vec![FieldType::new(type_id::INT32, true, vec![])],
        );
        assert!(!field_types_compatible(&list_fixed_i32, &list_nullable_i32));
        assert!(compatible_field_pair(&list_fixed_i32, &list_nullable_i32));

        let int32_array = FieldType::new(type_id::INT32_ARRAY, false, vec![]);
        let list_i32 = FieldType::new(
            type_id::LIST,
            false,
            vec![FieldType::new(type_id::INT32, false, vec![])],
        );
        assert!(compatible_field_pair(&list_i32, &int32_array));
        assert!(compatible_field_pair(&list_nullable_i32, &int32_array));
        assert!(compatible_field_pair(&int32_array, &list_i32));
    }

    #[test]
    fn vec_metadata_is_borrowed() {
        let remote_field_type = FieldType::new(
            type_id::LIST,
            false,
            vec![FieldType::new_with_ref(
                type_id::INT8,
                false,
                true,
                Vec::new(),
            )],
        );
        EXPECTED_FIELD.with(|expected| {
            expected.set(&remote_field_type.generics[0]);
        });
        PROBE_FIELD_READS.with(|reads| reads.set(0));

        let declared = [3, IS_SAME_TYPE | DECL_ELEMENT_TYPE | TRACKING_REF, 4, 5, 6];
        let mut context = probe_context(&declared);
        let values = <ProbeVecCodec as Codec<Vec<MetadataProbe>>>::read_data_with_type(
            &mut context,
            &remote_field_type,
        )
        .unwrap();
        assert_eq!(
            values,
            vec![MetadataProbe(4), MetadataProbe(5), MetadataProbe(6)]
        );
        PROBE_FIELD_READS.with(|reads| assert_eq!(reads.get(), 3));
        EXPECTED_FIELD.with(|expected| expected.set(std::ptr::null()));

        let resolver = TypeResolver::default();
        let type_info = resolver
            .get_type_info_by_id(TypeId::INT8 as u32)
            .expect("INT8 TypeInfo");
        drop(resolver);
        PROBE_TYPE_INFO.with(|canonical| {
            *canonical.borrow_mut() = Some(type_info);
        });
        PROBE_INFO_READS.with(|reads| reads.set(0));

        let indexed = [3, IS_SAME_TYPE | TRACKING_REF, 7, 8, 9];
        let mut context = probe_context(&indexed);
        let values = <ProbeVecCodec as Codec<Vec<MetadataProbe>>>::read_data(&mut context).unwrap();
        assert_eq!(
            values,
            vec![MetadataProbe(7), MetadataProbe(8), MetadataProbe(9)]
        );
        PROBE_INFO_READS.with(|reads| assert_eq!(reads.get(), 3));
        PROBE_TYPE_INFO.with(|canonical| {
            let canonical = canonical.borrow();
            assert_eq!(Rc::strong_count(canonical.as_ref().unwrap()), 1);
        });
        PROBE_TYPE_INFO.with(|canonical| *canonical.borrow_mut() = None);
    }
}
