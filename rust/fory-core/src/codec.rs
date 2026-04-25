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

use crate::context::{ReadContext, WriteContext};
use crate::error::Error;
use crate::resolver::{RefMode, TypeInfo, TypeResolver};
use crate::serializer::{ForyDefault, Serializer};
use crate::type_id::TypeId;
use std::collections::{BTreeMap, HashMap};
use std::hash::Hash;
use std::marker::PhantomData;
use std::rc::Rc;

pub trait Codec<T> {
    fn write(
        value: &T,
        context: &mut WriteContext,
        ref_mode: RefMode,
        write_type_info: bool,
        has_generics: bool,
    ) -> Result<(), Error>;

    fn write_data(value: &T, context: &mut WriteContext, has_generics: bool) -> Result<(), Error>;

    fn write_type_info(context: &mut WriteContext) -> Result<(), Error>;

    fn read(context: &mut ReadContext, ref_mode: RefMode, read_type_info: bool)
        -> Result<T, Error>;

    fn read_with_type_info(
        context: &mut ReadContext,
        ref_mode: RefMode,
        type_info: Rc<TypeInfo>,
    ) -> Result<T, Error>;

    fn read_data(context: &mut ReadContext) -> Result<T, Error>;

    fn read_type_info(context: &mut ReadContext) -> Result<(), Error>;

    fn reserved_space() -> usize;

    fn get_type_id(type_resolver: &TypeResolver) -> Result<TypeId, Error>;

    fn static_type_id() -> TypeId;

    fn is_polymorphic() -> bool {
        false
    }

    fn is_shared_ref() -> bool {
        false
    }

    fn is_optional() -> bool {
        false
    }

    fn is_none(value: &T) -> bool {
        let _ = value;
        false
    }

    fn concrete_type_id(value: &T) -> std::any::TypeId
    where
        T: 'static,
    {
        let _ = value;
        std::any::TypeId::of::<T>()
    }
}

pub struct SerializerCodec<T>(PhantomData<T>);

impl<T> Codec<T> for SerializerCodec<T>
where
    T: Serializer + ForyDefault,
{
    #[inline(always)]
    fn write(
        value: &T,
        context: &mut WriteContext,
        ref_mode: RefMode,
        write_type_info: bool,
        has_generics: bool,
    ) -> Result<(), Error> {
        value.fory_write(context, ref_mode, write_type_info, has_generics)
    }

    #[inline(always)]
    fn write_data(value: &T, context: &mut WriteContext, has_generics: bool) -> Result<(), Error> {
        value.fory_write_data_generic(context, has_generics)
    }

    #[inline(always)]
    fn write_type_info(context: &mut WriteContext) -> Result<(), Error> {
        T::fory_write_type_info(context)
    }

    #[inline(always)]
    fn read(
        context: &mut ReadContext,
        ref_mode: RefMode,
        read_type_info: bool,
    ) -> Result<T, Error> {
        T::fory_read(context, ref_mode, read_type_info)
    }

    #[inline(always)]
    fn read_with_type_info(
        context: &mut ReadContext,
        ref_mode: RefMode,
        type_info: Rc<TypeInfo>,
    ) -> Result<T, Error> {
        T::fory_read_with_type_info(context, ref_mode, type_info)
    }

    #[inline(always)]
    fn read_data(context: &mut ReadContext) -> Result<T, Error> {
        T::fory_read_data(context)
    }

    #[inline(always)]
    fn read_type_info(context: &mut ReadContext) -> Result<(), Error> {
        T::fory_read_type_info(context)
    }

    #[inline(always)]
    fn reserved_space() -> usize {
        T::fory_reserved_space()
    }

    #[inline(always)]
    fn get_type_id(type_resolver: &TypeResolver) -> Result<TypeId, Error> {
        T::fory_get_type_id(type_resolver)
    }

    #[inline(always)]
    fn static_type_id() -> TypeId {
        T::fory_static_type_id()
    }

    #[inline(always)]
    fn is_polymorphic() -> bool {
        T::fory_is_polymorphic()
    }

    #[inline(always)]
    fn is_shared_ref() -> bool {
        T::fory_is_shared_ref()
    }

    #[inline(always)]
    fn is_optional() -> bool {
        T::fory_is_option()
    }

    #[inline(always)]
    fn is_none(value: &T) -> bool {
        value.fory_is_none()
    }

    #[inline(always)]
    fn concrete_type_id(value: &T) -> std::any::TypeId
    where
        T: 'static,
    {
        value.fory_concrete_type_id()
    }
}

macro_rules! impl_scalar_codec {
    ($name:ident, $ty:ty, $type_id:expr, $write:expr, $read:expr) => {
        pub struct $name;
        impl Codec<$ty> for $name {
            #[inline(always)]
            fn write(
                value: &$ty,
                context: &mut WriteContext,
                _ref_mode: RefMode,
                write_type_info: bool,
                _has_generics: bool,
            ) -> Result<(), Error> {
                if write_type_info {
                    Self::write_type_info(context)?;
                }
                Self::write_data(value, context, false)
            }

            #[inline(always)]
            fn write_data(
                value: &$ty,
                context: &mut WriteContext,
                _has_generics: bool,
            ) -> Result<(), Error> {
                $write(&mut context.writer, *value);
                Ok(())
            }

            #[inline(always)]
            fn write_type_info(context: &mut WriteContext) -> Result<(), Error> {
                context.writer.write_var_u32($type_id as u32);
                Ok(())
            }

            #[inline(always)]
            fn read(
                context: &mut ReadContext,
                _ref_mode: RefMode,
                read_type_info: bool,
            ) -> Result<$ty, Error> {
                if read_type_info {
                    Self::read_type_info(context)?;
                }
                Self::read_data(context)
            }

            #[inline(always)]
            fn read_with_type_info(
                context: &mut ReadContext,
                _ref_mode: RefMode,
                _type_info: Rc<TypeInfo>,
            ) -> Result<$ty, Error> {
                Self::read_data(context)
            }

            #[inline(always)]
            fn read_data(context: &mut ReadContext) -> Result<$ty, Error> {
                $read(&mut context.reader)
            }

            #[inline(always)]
            fn read_type_info(context: &mut ReadContext) -> Result<(), Error> {
                <$ty as Serializer>::fory_read_type_info(context)
            }

            #[inline(always)]
            fn reserved_space() -> usize {
                std::mem::size_of::<$ty>()
            }

            #[inline(always)]
            fn get_type_id(_: &TypeResolver) -> Result<TypeId, Error> {
                Ok($type_id)
            }

            #[inline(always)]
            fn static_type_id() -> TypeId {
                $type_id
            }
        }
    };
}

impl_scalar_codec!(
    FixedI32Codec,
    i32,
    TypeId::INT32,
    crate::buffer::Writer::write_i32,
    crate::buffer::Reader::read_i32
);
impl_scalar_codec!(
    VarI32Codec,
    i32,
    TypeId::VARINT32,
    crate::buffer::Writer::write_var_i32,
    crate::buffer::Reader::read_var_i32
);
impl_scalar_codec!(
    FixedU32Codec,
    u32,
    TypeId::UINT32,
    crate::buffer::Writer::write_u32,
    crate::buffer::Reader::read_u32
);
impl_scalar_codec!(
    VarU32Codec,
    u32,
    TypeId::VAR_UINT32,
    crate::buffer::Writer::write_var_u32,
    crate::buffer::Reader::read_var_u32
);
impl_scalar_codec!(
    FixedI64Codec,
    i64,
    TypeId::INT64,
    crate::buffer::Writer::write_i64,
    crate::buffer::Reader::read_i64
);
impl_scalar_codec!(
    VarI64Codec,
    i64,
    TypeId::VARINT64,
    crate::buffer::Writer::write_var_i64,
    crate::buffer::Reader::read_var_i64
);
impl_scalar_codec!(
    TaggedI64Codec,
    i64,
    TypeId::TAGGED_INT64,
    crate::buffer::Writer::write_tagged_i64,
    crate::buffer::Reader::read_tagged_i64
);
impl_scalar_codec!(
    FixedU64Codec,
    u64,
    TypeId::UINT64,
    crate::buffer::Writer::write_u64,
    crate::buffer::Reader::read_u64
);
impl_scalar_codec!(
    VarU64Codec,
    u64,
    TypeId::VAR_UINT64,
    crate::buffer::Writer::write_var_u64,
    crate::buffer::Reader::read_var_u64
);
impl_scalar_codec!(
    TaggedU64Codec,
    u64,
    TypeId::TAGGED_UINT64,
    crate::buffer::Writer::write_tagged_u64,
    crate::buffer::Reader::read_tagged_u64
);

pub struct OptionCodec<C>(PhantomData<C>);

impl<T, C> Codec<Option<T>> for OptionCodec<C>
where
    T: ForyDefault + 'static,
    C: Codec<T>,
{
    #[inline(always)]
    fn write(
        value: &Option<T>,
        context: &mut WriteContext,
        ref_mode: RefMode,
        write_type_info: bool,
        has_generics: bool,
    ) -> Result<(), Error> {
        match ref_mode {
            RefMode::None => match value {
                Some(v) => C::write(v, context, RefMode::None, write_type_info, has_generics),
                None => Err(Error::invalid_data("Option::None with RefMode::None")),
            },
            RefMode::NullOnly => match value {
                Some(v) => {
                    context
                        .writer
                        .write_i8(crate::resolver::RefFlag::NotNullValue as i8);
                    C::write(v, context, RefMode::None, write_type_info, has_generics)
                }
                None => {
                    context
                        .writer
                        .write_i8(crate::resolver::RefFlag::Null as i8);
                    Ok(())
                }
            },
            RefMode::Tracking => match value {
                Some(v) => C::write(v, context, RefMode::Tracking, write_type_info, has_generics),
                None => {
                    context
                        .writer
                        .write_i8(crate::resolver::RefFlag::Null as i8);
                    Ok(())
                }
            },
        }
    }

    #[inline(always)]
    fn write_data(
        value: &Option<T>,
        context: &mut WriteContext,
        has_generics: bool,
    ) -> Result<(), Error> {
        match value {
            Some(v) => C::write_data(v, context, has_generics),
            None => Err(Error::invalid_data("Option::None has no payload")),
        }
    }

    #[inline(always)]
    fn write_type_info(context: &mut WriteContext) -> Result<(), Error> {
        C::write_type_info(context)
    }

    #[inline(always)]
    fn read(
        context: &mut ReadContext,
        ref_mode: RefMode,
        read_type_info: bool,
    ) -> Result<Option<T>, Error> {
        match ref_mode {
            RefMode::None => Ok(Some(C::read(context, RefMode::None, read_type_info)?)),
            RefMode::NullOnly => {
                let flag = context.reader.read_i8()?;
                if flag == crate::resolver::RefFlag::Null as i8 {
                    Ok(None)
                } else {
                    Ok(Some(C::read(context, RefMode::None, read_type_info)?))
                }
            }
            RefMode::Tracking => {
                let flag = context.reader.read_i8()?;
                if flag == crate::resolver::RefFlag::Null as i8 {
                    Ok(None)
                } else {
                    context.reader.move_back(1);
                    Ok(Some(C::read(context, RefMode::Tracking, read_type_info)?))
                }
            }
        }
    }

    #[inline(always)]
    fn read_with_type_info(
        context: &mut ReadContext,
        ref_mode: RefMode,
        type_info: Rc<TypeInfo>,
    ) -> Result<Option<T>, Error> {
        match ref_mode {
            RefMode::None => Ok(Some(C::read_with_type_info(
                context,
                RefMode::None,
                type_info,
            )?)),
            RefMode::NullOnly => {
                let flag = context.reader.read_i8()?;
                if flag == crate::resolver::RefFlag::Null as i8 {
                    Ok(None)
                } else {
                    Ok(Some(C::read_with_type_info(
                        context,
                        RefMode::None,
                        type_info,
                    )?))
                }
            }
            RefMode::Tracking => {
                let flag = context.reader.read_i8()?;
                if flag == crate::resolver::RefFlag::Null as i8 {
                    Ok(None)
                } else {
                    context.reader.move_back(1);
                    Ok(Some(C::read_with_type_info(
                        context,
                        RefMode::Tracking,
                        type_info,
                    )?))
                }
            }
        }
    }

    #[inline(always)]
    fn read_data(context: &mut ReadContext) -> Result<Option<T>, Error> {
        Ok(Some(C::read_data(context)?))
    }

    #[inline(always)]
    fn read_type_info(context: &mut ReadContext) -> Result<(), Error> {
        C::read_type_info(context)
    }

    #[inline(always)]
    fn reserved_space() -> usize {
        C::reserved_space()
    }

    #[inline(always)]
    fn get_type_id(type_resolver: &TypeResolver) -> Result<TypeId, Error> {
        C::get_type_id(type_resolver)
    }

    #[inline(always)]
    fn static_type_id() -> TypeId {
        C::static_type_id()
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
    fn is_optional() -> bool {
        true
    }

    #[inline(always)]
    fn is_none(value: &Option<T>) -> bool {
        value.is_none()
    }

    #[inline(always)]
    fn concrete_type_id(value: &Option<T>) -> std::any::TypeId
    where
        Option<T>: 'static,
    {
        match value {
            Some(v) => C::concrete_type_id(v),
            None => std::any::TypeId::of::<Option<T>>(),
        }
    }
}

pub struct VecCodec<C>(PhantomData<C>);
pub struct HashMapCodec<KC, VC>(PhantomData<(KC, VC)>);
pub struct BTreeMapCodec<KC, VC>(PhantomData<(KC, VC)>);

impl<T, C> Codec<Vec<T>> for VecCodec<C>
where
    T: ForyDefault + 'static,
    C: Codec<T>,
{
    #[inline(always)]
    fn write(
        value: &Vec<T>,
        context: &mut WriteContext,
        ref_mode: RefMode,
        write_type_info: bool,
        has_generics: bool,
    ) -> Result<(), Error> {
        if write_type_info {
            Self::write_type_info(context)?;
        }
        match ref_mode {
            RefMode::None => Self::write_data(value, context, has_generics),
            _ => Self::write_data(value, context, has_generics),
        }
    }

    #[inline(always)]
    fn write_data(
        value: &Vec<T>,
        context: &mut WriteContext,
        _has_generics: bool,
    ) -> Result<(), Error> {
        context.writer.write_var_u32(value.len() as u32);
        for item in value {
            let elem_ref_mode = if C::is_optional() || C::is_shared_ref() {
                RefMode::NullOnly
            } else {
                RefMode::None
            };
            C::write(item, context, elem_ref_mode, false, false)?;
        }
        Ok(())
    }

    #[inline(always)]
    fn write_type_info(context: &mut WriteContext) -> Result<(), Error> {
        context.writer.write_u8(TypeId::LIST as u8);
        C::write_type_info(context)
    }

    #[inline(always)]
    fn read(
        context: &mut ReadContext,
        ref_mode: RefMode,
        read_type_info: bool,
    ) -> Result<Vec<T>, Error> {
        if read_type_info {
            Self::read_type_info(context)?;
        }
        let _ = ref_mode;
        Self::read_data(context)
    }

    #[inline(always)]
    fn read_with_type_info(
        context: &mut ReadContext,
        ref_mode: RefMode,
        _type_info: Rc<TypeInfo>,
    ) -> Result<Vec<T>, Error> {
        Self::read(context, ref_mode, false)
    }

    #[inline(always)]
    fn read_data(context: &mut ReadContext) -> Result<Vec<T>, Error> {
        let len = context.reader.read_var_u32()?;
        let max = context.max_collection_size();
        if len > max {
            return Err(Error::size_limit_exceeded(format!(
                "Collection size {} exceeds limit {}",
                len, max
            )));
        }
        let mut result = Vec::with_capacity(len as usize);
        for _ in 0..len {
            let elem_ref_mode = if C::is_optional() || C::is_shared_ref() {
                RefMode::NullOnly
            } else {
                RefMode::None
            };
            result.push(C::read(context, elem_ref_mode, false)?);
        }
        Ok(result)
    }

    #[inline(always)]
    fn read_type_info(context: &mut ReadContext) -> Result<(), Error> {
        let remote = context.reader.read_u8()? as u32;
        if remote != TypeId::LIST as u32 {
            return Err(Error::type_mismatch(TypeId::LIST as u32, remote));
        }
        C::read_type_info(context)
    }

    #[inline(always)]
    fn reserved_space() -> usize {
        std::mem::size_of::<u32>()
    }

    #[inline(always)]
    fn get_type_id(_: &TypeResolver) -> Result<TypeId, Error> {
        Ok(TypeId::LIST)
    }

    #[inline(always)]
    fn static_type_id() -> TypeId {
        TypeId::LIST
    }
}

impl<K, V, KC, VC> Codec<HashMap<K, V>> for HashMapCodec<KC, VC>
where
    K: Eq + Hash + ForyDefault + 'static,
    V: ForyDefault + 'static,
    KC: Codec<K>,
    VC: Codec<V>,
{
    #[inline(always)]
    fn write(
        value: &HashMap<K, V>,
        context: &mut WriteContext,
        ref_mode: RefMode,
        write_type_info: bool,
        has_generics: bool,
    ) -> Result<(), Error> {
        if write_type_info {
            Self::write_type_info(context)?;
        }
        let _ = ref_mode;
        Self::write_data(value, context, has_generics)
    }

    #[inline(always)]
    fn write_data(
        value: &HashMap<K, V>,
        context: &mut WriteContext,
        _has_generics: bool,
    ) -> Result<(), Error> {
        context.writer.write_var_u32(value.len() as u32);
        for (k, v) in value {
            let key_ref_mode = if KC::is_optional() || KC::is_shared_ref() {
                RefMode::NullOnly
            } else {
                RefMode::None
            };
            let val_ref_mode = if VC::is_optional() || VC::is_shared_ref() {
                RefMode::NullOnly
            } else {
                RefMode::None
            };
            KC::write(k, context, key_ref_mode, false, false)?;
            VC::write(v, context, val_ref_mode, false, false)?;
        }
        Ok(())
    }

    #[inline(always)]
    fn write_type_info(context: &mut WriteContext) -> Result<(), Error> {
        context.writer.write_u8(TypeId::MAP as u8);
        KC::write_type_info(context)?;
        VC::write_type_info(context)
    }

    #[inline(always)]
    fn read(
        context: &mut ReadContext,
        ref_mode: RefMode,
        read_type_info: bool,
    ) -> Result<HashMap<K, V>, Error> {
        if read_type_info {
            Self::read_type_info(context)?;
        }
        let _ = ref_mode;
        Self::read_data(context)
    }

    #[inline(always)]
    fn read_with_type_info(
        context: &mut ReadContext,
        ref_mode: RefMode,
        _type_info: Rc<TypeInfo>,
    ) -> Result<HashMap<K, V>, Error> {
        Self::read(context, ref_mode, false)
    }

    #[inline(always)]
    fn read_data(context: &mut ReadContext) -> Result<HashMap<K, V>, Error> {
        let len = context.reader.read_var_u32()?;
        let max = context.max_collection_size();
        if len > max {
            return Err(Error::size_limit_exceeded(format!(
                "Map size {} exceeds limit {}",
                len, max
            )));
        }
        let mut map = HashMap::with_capacity(len as usize);
        for _ in 0..len {
            let key_ref_mode = if KC::is_optional() || KC::is_shared_ref() {
                RefMode::NullOnly
            } else {
                RefMode::None
            };
            let val_ref_mode = if VC::is_optional() || VC::is_shared_ref() {
                RefMode::NullOnly
            } else {
                RefMode::None
            };
            let key = KC::read(context, key_ref_mode, false)?;
            let value = VC::read(context, val_ref_mode, false)?;
            map.insert(key, value);
        }
        Ok(map)
    }

    #[inline(always)]
    fn read_type_info(context: &mut ReadContext) -> Result<(), Error> {
        let remote = context.reader.read_u8()? as u32;
        if remote != TypeId::MAP as u32 {
            return Err(Error::type_mismatch(TypeId::MAP as u32, remote));
        }
        KC::read_type_info(context)?;
        VC::read_type_info(context)
    }

    #[inline(always)]
    fn reserved_space() -> usize {
        std::mem::size_of::<u32>()
    }

    #[inline(always)]
    fn get_type_id(_: &TypeResolver) -> Result<TypeId, Error> {
        Ok(TypeId::MAP)
    }

    #[inline(always)]
    fn static_type_id() -> TypeId {
        TypeId::MAP
    }
}

impl<K, V, KC, VC> Codec<BTreeMap<K, V>> for BTreeMapCodec<KC, VC>
where
    K: Ord + ForyDefault + 'static,
    V: ForyDefault + 'static,
    KC: Codec<K>,
    VC: Codec<V>,
{
    #[inline(always)]
    fn write(
        value: &BTreeMap<K, V>,
        context: &mut WriteContext,
        ref_mode: RefMode,
        write_type_info: bool,
        has_generics: bool,
    ) -> Result<(), Error> {
        if write_type_info {
            Self::write_type_info(context)?;
        }
        let _ = ref_mode;
        Self::write_data(value, context, has_generics)
    }

    #[inline(always)]
    fn write_data(
        value: &BTreeMap<K, V>,
        context: &mut WriteContext,
        _has_generics: bool,
    ) -> Result<(), Error> {
        context.writer.write_var_u32(value.len() as u32);
        for (k, v) in value {
            let key_ref_mode = if KC::is_optional() || KC::is_shared_ref() {
                RefMode::NullOnly
            } else {
                RefMode::None
            };
            let val_ref_mode = if VC::is_optional() || VC::is_shared_ref() {
                RefMode::NullOnly
            } else {
                RefMode::None
            };
            KC::write(k, context, key_ref_mode, false, false)?;
            VC::write(v, context, val_ref_mode, false, false)?;
        }
        Ok(())
    }

    #[inline(always)]
    fn write_type_info(context: &mut WriteContext) -> Result<(), Error> {
        context.writer.write_u8(TypeId::MAP as u8);
        KC::write_type_info(context)?;
        VC::write_type_info(context)
    }

    #[inline(always)]
    fn read(
        context: &mut ReadContext,
        ref_mode: RefMode,
        read_type_info: bool,
    ) -> Result<BTreeMap<K, V>, Error> {
        if read_type_info {
            Self::read_type_info(context)?;
        }
        let _ = ref_mode;
        Self::read_data(context)
    }

    #[inline(always)]
    fn read_with_type_info(
        context: &mut ReadContext,
        ref_mode: RefMode,
        _type_info: Rc<TypeInfo>,
    ) -> Result<BTreeMap<K, V>, Error> {
        Self::read(context, ref_mode, false)
    }

    #[inline(always)]
    fn read_data(context: &mut ReadContext) -> Result<BTreeMap<K, V>, Error> {
        let len = context.reader.read_var_u32()?;
        let max = context.max_collection_size();
        if len > max {
            return Err(Error::size_limit_exceeded(format!(
                "Map size {} exceeds limit {}",
                len, max
            )));
        }
        let mut map = BTreeMap::new();
        for _ in 0..len {
            let key_ref_mode = if KC::is_optional() || KC::is_shared_ref() {
                RefMode::NullOnly
            } else {
                RefMode::None
            };
            let val_ref_mode = if VC::is_optional() || VC::is_shared_ref() {
                RefMode::NullOnly
            } else {
                RefMode::None
            };
            let key = KC::read(context, key_ref_mode, false)?;
            let value = VC::read(context, val_ref_mode, false)?;
            map.insert(key, value);
        }
        Ok(map)
    }

    #[inline(always)]
    fn read_type_info(context: &mut ReadContext) -> Result<(), Error> {
        let remote = context.reader.read_u8()? as u32;
        if remote != TypeId::MAP as u32 {
            return Err(Error::type_mismatch(TypeId::MAP as u32, remote));
        }
        KC::read_type_info(context)?;
        VC::read_type_info(context)
    }

    #[inline(always)]
    fn reserved_space() -> usize {
        std::mem::size_of::<u32>()
    }

    #[inline(always)]
    fn get_type_id(_: &TypeResolver) -> Result<TypeId, Error> {
        Ok(TypeId::MAP)
    }

    #[inline(always)]
    fn static_type_id() -> TypeId {
        TypeId::MAP
    }
}
