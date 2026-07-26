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
    field_ref_mode, field_type_with_ref_flags, generic_field_type, Codec, CodecReadType,
    SerializerCodec,
};
use super::collection::check_count_write_bytes;
use crate::context::{ReadContext, WriteContext};
use crate::error::Error;
use crate::meta::FieldType;
use crate::resolver::{RefFlag, RefMode, TypeInfo, TypeResolver};
use crate::serializer::Serializer;
use crate::type_id::{need_to_write_type_for_field, TypeId, SIZE_OF_REF_AND_TYPE};
use std::borrow::Cow;
use std::collections::{BTreeMap, HashMap};
use std::marker::PhantomData;
use std::rc::Rc;

const MAX_CHUNK_SIZE: u8 = 255;
pub(crate) const TRACKING_KEY_REF: u8 = 0b1;
pub(crate) const KEY_NULL: u8 = 0b10;
pub(crate) const DECL_KEY_TYPE: u8 = 0b100;
pub(crate) const TRACKING_VALUE_REF: u8 = 0b1000;
pub(crate) const VALUE_NULL: u8 = 0b10000;
pub(crate) const DECL_VALUE_TYPE: u8 = 0b100000;

pub struct BTreeMapCodec<K, V, KC, VC, const NULLABLE: bool, const TRACK_REF: bool>(
    PhantomData<(K, V, KC, VC)>,
);

pub struct HashMapCodec<K, V, KC, VC, const NULLABLE: bool, const TRACK_REF: bool>(
    PhantomData<(K, V, KC, VC)>,
);

trait MapTarget<K, V>: Sized {
    fn with_capacity(capacity: usize) -> Self;
    fn insert(&mut self, key: K, value: V);
}

impl<K: Eq + std::hash::Hash, V> MapTarget<K, V> for HashMap<K, V> {
    #[inline(always)]
    fn with_capacity(capacity: usize) -> Self {
        HashMap::with_capacity(capacity)
    }

    #[inline(always)]
    fn insert(&mut self, key: K, value: V) {
        HashMap::insert(self, key, value);
    }
}

impl<K: Ord, V> MapTarget<K, V> for BTreeMap<K, V> {
    #[inline(always)]
    fn with_capacity(_: usize) -> Self {
        BTreeMap::new()
    }

    #[inline(always)]
    fn insert(&mut self, key: K, value: V) {
        BTreeMap::insert(self, key, value);
    }
}

#[inline(always)]
fn write_entry_type<T: 'static, C: Codec<T>>(
    context: &mut WriteContext,
    target_type_id: Option<std::any::TypeId>,
) -> Result<Option<Rc<TypeInfo>>, Error> {
    if let Some(target_type_id) = target_type_id {
        C::write_type_info_value(context, target_type_id).map(Some)
    } else {
        C::write_type_info(context)?;
        Ok(None)
    }
}

#[inline(always)]
fn write_entry<T: 'static, C: Codec<T>>(
    value: &T,
    context: &mut WriteContext,
    track_ref: bool,
    has_generics: bool,
    type_info: Option<&Rc<TypeInfo>>,
) -> Result<(), Error> {
    let ref_mode = if track_ref {
        RefMode::Tracking
    } else {
        RefMode::None
    };
    if let Some(type_info) = type_info {
        C::write_with_type_info(value, context, ref_mode, type_info, has_generics)
    } else {
        C::write_with_mode(value, context, ref_mode, false, has_generics)
    }
}

fn write_map_data<'a, K, V, KC, VC, I>(
    iter: I,
    len: usize,
    context: &mut WriteContext,
    has_generics: bool,
) -> Result<(), Error>
where
    K: 'static + 'a,
    V: 'static + 'a,
    KC: Codec<K>,
    VC: Codec<V>,
    I: Iterator<Item = (&'a K, &'a V)>,
{
    context.writer.write_var_u32(len as u32);
    if len == 0 {
        return Ok(());
    }
    let body_offset = context.writer.len();
    context
        .writer
        .reserve(len.saturating_mul(KC::reserved_space().saturating_add(VC::reserved_space())));

    let key_declared = has_generics && !need_to_write_type_for_field(KC::static_type_id());
    let value_declared = has_generics && !need_to_write_type_for_field(VC::static_type_id());
    let key_polymorphic = KC::is_polymorphic();
    let value_polymorphic = VC::is_polymorphic();
    let key_shared = KC::is_shared_ref();
    let value_shared = VC::is_shared_ref();
    let mut key_type = None;
    let mut value_type = None;
    let mut key_info = None;
    let mut value_info = None;
    let mut header_offset = 0;
    let mut pair_count = 0u8;
    let mut need_header = true;

    for (key, value) in iter {
        // MAP emits both type headers before either body. Holder codecs release
        // this inspection immediately; resolved body writes revalidate the
        // value, so matching entries can still share one chunk TypeInfo.
        let next_key_type = if key_polymorphic {
            KC::dynamic_type_id(key)?
        } else {
            None
        };
        let next_value_type = if value_polymorphic {
            VC::dynamic_type_id(value)?
        } else {
            None
        };
        let key_none = if key_polymorphic {
            next_key_type.is_none()
        } else {
            KC::is_none(key)
        };
        let value_none = if value_polymorphic {
            next_value_type.is_none()
        } else {
            VC::is_none(value)
        };
        if key_none || value_none {
            if pair_count != 0 {
                context.writer.set_bytes(header_offset + 1, &[pair_count]);
                pair_count = 0;
                need_header = true;
            }
            if key_none && value_none {
                context.writer.write_u8(KEY_NULL | VALUE_NULL);
                continue;
            }
            if value_none {
                let mut header = VALUE_NULL;
                if key_shared {
                    header |= TRACKING_KEY_REF;
                }
                if key_declared && !key_polymorphic {
                    header |= DECL_KEY_TYPE;
                    context.writer.write_u8(header);
                    write_entry::<K, KC>(key, context, key_shared, has_generics, None)?;
                } else {
                    context.writer.write_u8(header);
                    if key_shared {
                        KC::write_with_mode(key, context, RefMode::Tracking, true, has_generics)?;
                    } else {
                        let key_info = write_entry_type::<K, KC>(context, next_key_type)?;
                        write_entry::<K, KC>(key, context, false, has_generics, key_info.as_ref())?;
                    }
                }
                continue;
            }
            let mut header = KEY_NULL;
            if value_shared {
                header |= TRACKING_VALUE_REF;
            }
            if value_declared && !value_polymorphic {
                header |= DECL_VALUE_TYPE;
                context.writer.write_u8(header);
                write_entry::<V, VC>(value, context, value_shared, has_generics, None)?;
            } else {
                context.writer.write_u8(header);
                if value_shared {
                    VC::write_with_mode(value, context, RefMode::Tracking, true, has_generics)?;
                } else {
                    let value_info = write_entry_type::<V, VC>(context, next_value_type)?;
                    write_entry::<V, VC>(value, context, false, has_generics, value_info.as_ref())?;
                }
            }
            continue;
        }

        let types_changed = (key_polymorphic || value_polymorphic)
            && (next_key_type != key_type || next_value_type != value_type);
        if need_header || types_changed {
            if pair_count != 0 {
                context.writer.set_bytes(header_offset + 1, &[pair_count]);
                pair_count = 0;
            }
            header_offset = context.writer.len();
            context.writer.write_i16(-1);
            let mut header = 0;
            if key_shared {
                header |= TRACKING_KEY_REF;
            }
            if value_shared {
                header |= TRACKING_VALUE_REF;
            }
            if key_declared && !key_polymorphic {
                header |= DECL_KEY_TYPE;
                key_info = None;
            } else {
                key_info = write_entry_type::<K, KC>(context, next_key_type)?;
            }
            if value_declared && !value_polymorphic {
                header |= DECL_VALUE_TYPE;
                value_info = None;
            } else {
                value_info = write_entry_type::<V, VC>(context, next_value_type)?;
            }
            context.writer.set_bytes(header_offset, &[header]);
            need_header = false;
            key_type = next_key_type;
            value_type = next_value_type;
        }

        write_entry::<K, KC>(key, context, key_shared, has_generics, key_info.as_ref())?;
        write_entry::<V, VC>(
            value,
            context,
            value_shared,
            has_generics,
            value_info.as_ref(),
        )?;
        pair_count += 1;
        if pair_count == MAX_CHUNK_SIZE {
            context.writer.set_bytes(header_offset + 1, &[pair_count]);
            pair_count = 0;
            need_header = true;
            key_type = None;
            value_type = None;
            key_info = None;
            value_info = None;
        }
    }
    if pair_count != 0 {
        context.writer.set_bytes(header_offset + 1, &[pair_count]);
    }
    check_count_write_bytes(context, body_offset, len)
}

enum EntryReadType<'a> {
    Direct,
    Field(Cow<'a, FieldType>),
    TypeInfo(Rc<TypeInfo>),
}

#[inline(always)]
fn read_entry_type<'a, T: 'static, C: Codec<T>>(
    context: &mut ReadContext,
    declared: bool,
    remote_field_type: Option<&'a FieldType>,
    index: usize,
    track_ref: bool,
) -> Result<EntryReadType<'a>, Error> {
    if declared {
        return match remote_field_type {
            Some(field_type) => {
                let field_type = generic_field_type(field_type, index, "map")?;
                Ok(EntryReadType::Field(field_type_with_ref_flags(
                    field_type,
                    field_type.nullable,
                    track_ref,
                )))
            }
            None => Ok(EntryReadType::Direct),
        };
    }
    match C::read_type_info_value(context)? {
        CodecReadType::Field(mut field_type) => {
            field_type.track_ref = track_ref;
            Ok(EntryReadType::Field(Cow::Owned(field_type)))
        }
        CodecReadType::TypeInfo(type_info) => Ok(EntryReadType::TypeInfo(type_info)),
    }
}

#[inline(always)]
fn read_entry<T: 'static, C: Codec<T>>(
    context: &mut ReadContext,
    read_type: &EntryReadType<'_>,
    track_ref: bool,
) -> Result<T, Error> {
    let ref_mode = if track_ref {
        RefMode::Tracking
    } else {
        RefMode::None
    };
    match read_type {
        EntryReadType::Direct => C::read_with_mode(context, ref_mode, false),
        EntryReadType::TypeInfo(type_info) => C::read_with_type_info(context, ref_mode, type_info),
        EntryReadType::Field(field_type) if track_ref => {
            C::read_field_with_type(context, field_type)
        }
        EntryReadType::Field(field_type) => C::read_data_with_type(context, field_type),
    }
}

#[cold]
#[inline(never)]
fn invalid_map_chunk() -> Error {
    Error::invalid_data("map chunk size must be within the remaining entry count")
}

#[cold]
#[inline(never)]
fn map_memory_overflow() -> Error {
    Error::invalid_data("graph memory estimate overflows")
}

#[cold]
#[inline(never)]
fn map_type_mismatch(remote: u32) -> Error {
    Error::type_mismatch(TypeId::MAP as u32, remote)
}

fn read_map_data<M, K, V, KC, VC>(
    context: &mut ReadContext,
    remote_field_type: Option<&FieldType>,
) -> Result<M, Error>
where
    K: 'static,
    V: 'static,
    KC: Codec<K>,
    VC: Codec<V>,
    M: MapTarget<K, V>,
{
    let len = context.reader.read_var_u32()?;
    let capacity = len as usize;
    context.reader.check_bound(capacity)?;
    let elem_bytes = std::mem::size_of::<K>()
        .checked_add(std::mem::size_of::<V>())
        .and_then(|bytes| bytes.checked_mul(capacity))
        .ok_or_else(map_memory_overflow)?;
    context.reserve_graph_memory(elem_bytes)?;
    let mut map = M::with_capacity(capacity);
    let mut read = 0u32;
    while read < len {
        let header = context.reader.read_u8()?;
        if header & KEY_NULL != 0 && header & VALUE_NULL != 0 {
            map.insert(KC::default_value(context)?, VC::default_value(context)?);
            read += 1;
            continue;
        }
        let key_declared = header & DECL_KEY_TYPE != 0;
        let value_declared = header & DECL_VALUE_TYPE != 0;
        let key_tracked = header & TRACKING_KEY_REF != 0;
        let value_tracked = header & TRACKING_VALUE_REF != 0;
        if header & KEY_NULL != 0 {
            let value = if value_tracked && !value_declared {
                VC::read_with_mode(context, RefMode::Tracking, true)?
            } else {
                let value_type = read_entry_type::<V, VC>(
                    context,
                    value_declared,
                    remote_field_type,
                    1,
                    value_tracked,
                )?;
                read_entry::<V, VC>(context, &value_type, value_tracked)?
            };
            map.insert(KC::default_value(context)?, value);
            read += 1;
            continue;
        }
        if header & VALUE_NULL != 0 {
            let key = if key_tracked && !key_declared {
                KC::read_with_mode(context, RefMode::Tracking, true)?
            } else {
                let key_type = read_entry_type::<K, KC>(
                    context,
                    key_declared,
                    remote_field_type,
                    0,
                    key_tracked,
                )?;
                read_entry::<K, KC>(context, &key_type, key_tracked)?
            };
            map.insert(key, VC::default_value(context)?);
            read += 1;
            continue;
        }
        let chunk_size = context.reader.read_u8()? as u32;
        if chunk_size == 0 {
            return Err(invalid_map_chunk());
        }
        let end = read
            .checked_add(chunk_size)
            .filter(|end| *end <= len)
            .ok_or_else(invalid_map_chunk)?;
        let key_type =
            read_entry_type::<K, KC>(context, key_declared, remote_field_type, 0, key_tracked)?;
        let value_type =
            read_entry_type::<V, VC>(context, value_declared, remote_field_type, 1, value_tracked)?;
        while read < end {
            let key = read_entry::<K, KC>(context, &key_type, key_tracked)?;
            let value = read_entry::<V, VC>(context, &value_type, value_tracked)?;
            map.insert(key, value);
            read += 1;
        }
    }
    Ok(map)
}

macro_rules! impl_map_codec {
    ($codec:ident, $target:ident, [$($key_bound:tt)+]) => {
        impl<K, V, KC, VC, const NULLABLE: bool, const TRACK_REF: bool>
            Codec<$target<K, V>> for $codec<K, V, KC, VC, NULLABLE, TRACK_REF>
        where
            K: $($key_bound)+ + 'static,
            V: 'static,
            KC: Codec<K>,
            VC: Codec<V>,
        {
            #[inline(always)]
            fn field_type(type_resolver: &TypeResolver) -> Result<FieldType, Error> {
                Ok(FieldType::new_with_ref(
                    TypeId::MAP as u32,
                    NULLABLE,
                    TRACK_REF,
                    vec![
                        KC::field_type(type_resolver)?,
                        VC::field_type(type_resolver)?,
                    ],
                ))
            }

            #[inline(always)]
            fn reserved_space() -> usize {
                std::mem::size_of::<u32>() + SIZE_OF_REF_AND_TYPE
            }

            #[inline(always)]
            fn write_field(
                value: &$target<K, V>,
                context: &mut WriteContext,
            ) -> Result<(), Error> {
                if NULLABLE || TRACK_REF {
                    context.writer.write_i8(RefFlag::NotNullValue as i8);
                }
                write_map_data::<K, V, KC, VC, _>(
                    value.iter(),
                    value.len(),
                    context,
                    true,
                )
            }

            #[inline(always)]
            fn read_field(context: &mut ReadContext) -> Result<$target<K, V>, Error> {
                if (NULLABLE || TRACK_REF)
                    && context.reader.read_i8()? == RefFlag::Null as i8
                {
                    return Ok(
                        <$target<K, V> as MapTarget<K, V>>::with_capacity(0)
                    );
                }
                read_map_data::<$target<K, V>, K, V, KC, VC>(context, None)
            }

            #[inline(always)]
            fn write_data(
                value: &$target<K, V>,
                context: &mut WriteContext,
            ) -> Result<(), Error> {
                write_map_data::<K, V, KC, VC, _>(
                    value.iter(),
                    value.len(),
                    context,
                    false,
                )
            }

            #[inline(always)]
            fn read_data(context: &mut ReadContext) -> Result<$target<K, V>, Error> {
                read_map_data::<$target<K, V>, K, V, KC, VC>(context, None)
            }

            #[inline(always)]
            fn read_data_with_type(
                context: &mut ReadContext,
                remote_data_type: &FieldType,
            ) -> Result<$target<K, V>, Error> {
                read_map_data::<$target<K, V>, K, V, KC, VC>(
                    context,
                    Some(remote_data_type),
                )
            }

            #[inline(always)]
            fn read_field_with_type(
                context: &mut ReadContext,
                remote_field_type: &FieldType,
            ) -> Result<$target<K, V>, Error> {
                if field_ref_mode(remote_field_type) != RefMode::None
                    && context.reader.read_i8()? == RefFlag::Null as i8
                {
                    return Ok(
                        <$target<K, V> as MapTarget<K, V>>::with_capacity(0)
                    );
                }
                Self::read_data_with_type(context, remote_field_type)
            }

            #[inline(always)]
            fn write_with_mode(
                value: &$target<K, V>,
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
                write_map_data::<K, V, KC, VC, _>(
                    value.iter(),
                    value.len(),
                    context,
                    has_generics,
                )
            }

            #[inline(always)]
            fn read_with_mode(
                context: &mut ReadContext,
                ref_mode: RefMode,
                read_type_info: bool,
            ) -> Result<$target<K, V>, Error> {
                if ref_mode != RefMode::None
                    && context.reader.read_i8()? == RefFlag::Null as i8
                {
                    return Ok(
                        <$target<K, V> as MapTarget<K, V>>::with_capacity(0)
                    );
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
                _type_info: &Rc<TypeInfo>,
            ) -> Result<$target<K, V>, Error> {
                Self::read_with_mode(context, ref_mode, false)
            }

            #[inline(always)]
            fn default_value(
                _: &mut ReadContext,
            ) -> Result<$target<K, V>, Error> {
                Ok(<$target<K, V> as MapTarget<K, V>>::with_capacity(0))
            }

            #[inline(always)]
            fn write_type_info(context: &mut WriteContext) -> Result<(), Error> {
                context.writer.write_u8(TypeId::MAP as u8);
                Ok(())
            }

            #[inline(always)]
            fn read_type_info(context: &mut ReadContext) -> Result<(), Error> {
                let remote = context.reader.read_u8()? as u32;
                if remote == TypeId::MAP as u32 {
                    Ok(())
                } else {
                    Err(map_type_mismatch(remote))
                }
            }

            #[inline(always)]
            fn static_type_id() -> TypeId {
                TypeId::MAP
            }
        }
    };
}

impl_map_codec!(HashMapCodec, HashMap, [Eq + std::hash::Hash]);
impl_map_codec!(BTreeMapCodec, BTreeMap, [Ord]);

macro_rules! impl_map_serializer {
    ($provider:ident, $target:ident, $codec:ident, [$($key_bound:tt)+]) => {
        #[doc = concat!(
            "Statically serializes `",
            stringify!($target),
            "<KS::Target, VS::Target>` at roots or recursive carrier nodes. ",
            "This zero-sized carrier composes its key and value serializers and is not ",
            "registered independently."
        )]
        pub struct $provider<KS, VS>(PhantomData<fn() -> (KS, VS)>);

        impl<KS, VS> Serializer for $provider<KS, VS>
        where
            KS: Serializer,
            VS: Serializer,
            KS::Target: $($key_bound)+,
        {
            type Target = $target<KS::Target, VS::Target>;

            #[inline(always)]
            fn write_data(value: &Self::Target, context: &mut WriteContext) -> Result<(), Error> {
                <$codec<
                    KS::Target,
                    VS::Target,
                    SerializerCodec<KS, false, false>,
                    SerializerCodec<VS, false, false>,
                    false,
                    false,
                > as Codec<Self::Target>>::write_data(value, context)
            }

            #[inline(always)]
            fn read_data(context: &mut ReadContext) -> Result<Self::Target, Error> {
                <$codec<
                    KS::Target,
                    VS::Target,
                    SerializerCodec<KS, false, false>,
                    SerializerCodec<VS, false, false>,
                    false,
                    false,
                > as Codec<Self::Target>>::read_data(context)
            }

            #[inline(always)]
            fn default_value(context: &mut ReadContext) -> Result<Self::Target, Error> {
                <$codec<
                    KS::Target,
                    VS::Target,
                    SerializerCodec<KS, false, false>,
                    SerializerCodec<VS, false, false>,
                    false,
                    false,
                > as Codec<Self::Target>>::default_value(context)
            }

            #[inline(always)]
            fn write(
                value: &Self::Target,
                context: &mut WriteContext,
                ref_mode: RefMode,
                write_type_info: bool,
                has_generics: bool,
            ) -> Result<(), Error> {
                <$codec<
                    KS::Target,
                    VS::Target,
                    SerializerCodec<KS, false, false>,
                    SerializerCodec<VS, false, false>,
                    false,
                    false,
                > as Codec<Self::Target>>::write_with_mode(
                    value,
                    context,
                    ref_mode,
                    write_type_info,
                    has_generics,
                )
            }

            #[inline(always)]
            fn write_data_with_generics(
                value: &Self::Target,
                context: &mut WriteContext,
                has_generics: bool,
            ) -> Result<(), Error> {
                <$codec<
                    KS::Target,
                    VS::Target,
                    SerializerCodec<KS, false, false>,
                    SerializerCodec<VS, false, false>,
                    false,
                    false,
                > as Codec<Self::Target>>::write_with_mode(
                    value,
                    context,
                    RefMode::None,
                    false,
                    has_generics,
                )
            }

            #[inline(always)]
            fn read(
                context: &mut ReadContext,
                ref_mode: RefMode,
                read_type_info: bool,
            ) -> Result<Self::Target, Error> {
                <$codec<
                    KS::Target,
                    VS::Target,
                    SerializerCodec<KS, false, false>,
                    SerializerCodec<VS, false, false>,
                    false,
                    false,
                > as Codec<Self::Target>>::read_with_mode(
                    context,
                    ref_mode,
                    read_type_info,
                )
            }

            #[inline(always)]
            fn read_with_type_info(
                context: &mut ReadContext,
                ref_mode: RefMode,
                type_info: &Rc<TypeInfo>,
            ) -> Result<Self::Target, Error> {
                <$codec<
                    KS::Target,
                    VS::Target,
                    SerializerCodec<KS, false, false>,
                    SerializerCodec<VS, false, false>,
                    false,
                    false,
                > as Codec<Self::Target>>::read_with_type_info(
                    context,
                    ref_mode,
                    type_info,
                )
            }

            #[inline(always)]
            fn field_type<const NULLABLE: bool, const TRACK_REF: bool>(
                type_resolver: &TypeResolver,
            ) -> Result<FieldType, Error> {
                <$codec<
                    KS::Target,
                    VS::Target,
                    SerializerCodec<KS, false, false>,
                    SerializerCodec<VS, false, false>,
                    NULLABLE,
                    TRACK_REF,
                > as Codec<Self::Target>>::field_type(type_resolver)
            }

            #[inline(always)]
            fn read_data_with_field_type(
                context: &mut ReadContext,
                remote_field_type: &FieldType,
            ) -> Result<Self::Target, Error> {
                <$codec<
                    KS::Target,
                    VS::Target,
                    SerializerCodec<KS, false, false>,
                    SerializerCodec<VS, false, false>,
                    false,
                    false,
                > as Codec<Self::Target>>::read_data_with_type(
                    context,
                    remote_field_type,
                )
            }

            #[inline(always)]
            fn write_type_info(context: &mut WriteContext) -> Result<(), Error> {
                <$codec<
                    KS::Target,
                    VS::Target,
                    SerializerCodec<KS, false, false>,
                    SerializerCodec<VS, false, false>,
                    false,
                    false,
                > as Codec<Self::Target>>::write_type_info(context)
            }

            #[inline(always)]
            fn read_type_info(context: &mut ReadContext) -> Result<(), Error> {
                <$codec<
                    KS::Target,
                    VS::Target,
                    SerializerCodec<KS, false, false>,
                    SerializerCodec<VS, false, false>,
                    false,
                    false,
                > as Codec<Self::Target>>::read_type_info(context)
            }

            #[inline(always)]
            fn static_type_id() -> TypeId {
                TypeId::MAP
            }

            #[inline(always)]
            fn reserved_space() -> usize {
                std::mem::size_of::<u32>() + SIZE_OF_REF_AND_TYPE
            }
        }

        impl<K, V> Serializer for $target<K, V>
        where
            K: Serializer<Target = K> + $($key_bound)+,
            V: Serializer<Target = V>,
        {
            type Target = Self;

            #[inline(always)]
            fn write_data(value: &Self, context: &mut WriteContext) -> Result<(), Error> {
                <$provider<K, V> as Serializer>::write_data(value, context)
            }

            #[inline(always)]
            fn read_data(context: &mut ReadContext) -> Result<Self, Error> {
                <$provider<K, V> as Serializer>::read_data(context)
            }

            #[inline(always)]
            fn default_value(context: &mut ReadContext) -> Result<Self, Error> {
                <$provider<K, V> as Serializer>::default_value(context)
            }

            #[inline(always)]
            fn write(
                value: &Self,
                context: &mut WriteContext,
                ref_mode: RefMode,
                write_type_info: bool,
                has_generics: bool,
            ) -> Result<(), Error> {
                <$provider<K, V> as Serializer>::write(
                    value,
                    context,
                    ref_mode,
                    write_type_info,
                    has_generics,
                )
            }

            #[inline(always)]
            fn write_data_with_generics(
                value: &Self,
                context: &mut WriteContext,
                has_generics: bool,
            ) -> Result<(), Error> {
                <$provider<K, V> as Serializer>::write_data_with_generics(
                    value,
                    context,
                    has_generics,
                )
            }

            #[inline(always)]
            fn read(
                context: &mut ReadContext,
                ref_mode: RefMode,
                read_type_info: bool,
            ) -> Result<Self, Error> {
                <$provider<K, V> as Serializer>::read(
                    context,
                    ref_mode,
                    read_type_info,
                )
            }

            #[inline(always)]
            fn read_with_type_info(
                context: &mut ReadContext,
                ref_mode: RefMode,
                type_info: &Rc<TypeInfo>,
            ) -> Result<Self, Error> {
                <$provider<K, V> as Serializer>::read_with_type_info(
                    context,
                    ref_mode,
                    type_info,
                )
            }

            #[inline(always)]
            fn field_type<const NULLABLE: bool, const TRACK_REF: bool>(
                type_resolver: &TypeResolver,
            ) -> Result<FieldType, Error> {
                <$provider<K, V> as Serializer>::field_type::<NULLABLE, TRACK_REF>(
                    type_resolver,
                )
            }

            #[inline(always)]
            fn read_data_with_field_type(
                context: &mut ReadContext,
                remote_field_type: &FieldType,
            ) -> Result<Self, Error> {
                <$provider<K, V> as Serializer>::read_data_with_field_type(
                    context,
                    remote_field_type,
                )
            }

            #[inline(always)]
            fn write_type_info(context: &mut WriteContext) -> Result<(), Error> {
                <$provider<K, V> as Serializer>::write_type_info(context)
            }

            #[inline(always)]
            fn read_type_info(context: &mut ReadContext) -> Result<(), Error> {
                <$provider<K, V> as Serializer>::read_type_info(context)
            }

            #[inline(always)]
            fn static_type_id() -> TypeId {
                TypeId::MAP
            }

            #[inline(always)]
            fn reserved_space() -> usize {
                std::mem::size_of::<u32>() + SIZE_OF_REF_AND_TYPE
            }
        }
    };
}

impl_map_serializer!(
    HashMapSerializer,
    HashMap,
    HashMapCodec,
    [Eq + std::hash::Hash]
);
impl_map_serializer!(BTreeMapSerializer, BTreeMap, BTreeMapCodec, [Ord]);
