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

use super::codec::{codec_read_type_info_static, codec_ref_mode, codec_write_type_info, Codec};
use crate::context::{ReadContext, WriteContext};
use crate::error::Error;
use crate::meta::FieldType;
use crate::resolver::{RefMode, TypeInfo, TypeResolver};
use crate::type_id::TypeId;
use std::marker::PhantomData;
use std::rc::Rc;
use std::sync::{Mutex, MutexGuard};

pub struct MutexCodec<T, C, const NULLABLE: bool, const TRACK_REF: bool>(PhantomData<(T, C)>);

#[inline(always)]
fn lock_for_write<T>(value: &Mutex<T>) -> Result<MutexGuard<'_, T>, Error> {
    match value.lock() {
        Ok(value) => Ok(value),
        Err(_) => Err(mutex_poison_error()),
    }
}

#[inline(always)]
fn lock_for_inspection<T>(value: &Mutex<T>) -> MutexGuard<'_, T> {
    match value.lock() {
        Ok(value) => value,
        Err(error) => error.into_inner(),
    }
}

#[cold]
#[inline(never)]
fn mutex_poison_error() -> Error {
    Error::invalid_data("cannot serialize a poisoned Mutex")
}

impl<T, C, const NULLABLE: bool, const TRACK_REF: bool> Codec<Mutex<T>>
    for MutexCodec<T, C, NULLABLE, TRACK_REF>
where
    T: 'static,
    C: Codec<T>,
{
    #[inline(always)]
    fn field_type(type_resolver: &TypeResolver) -> Result<FieldType, Error> {
        let mut field_type = C::field_type(type_resolver)?;
        field_type.nullable = NULLABLE;
        field_type.track_ref = TRACK_REF;
        Ok(field_type)
    }

    #[inline(always)]
    fn reserved_space() -> usize {
        C::reserved_space()
    }

    #[inline(always)]
    fn write_field(value: &Mutex<T>, context: &mut WriteContext) -> Result<(), Error> {
        let value = lock_for_write(value)?;
        C::write_with_mode(
            &value,
            context,
            codec_ref_mode::<T, C, NULLABLE, TRACK_REF>(),
            codec_write_type_info::<T, C>(context),
            true,
        )
    }

    #[inline(always)]
    fn read_field(context: &mut ReadContext) -> Result<Mutex<T>, Error> {
        Ok(Mutex::new(C::read_with_mode(
            context,
            codec_ref_mode::<T, C, NULLABLE, TRACK_REF>(),
            codec_read_type_info_static::<T, C>(context),
        )?))
    }

    #[inline(always)]
    fn write_data(value: &Mutex<T>, context: &mut WriteContext) -> Result<(), Error> {
        let value = lock_for_write(value)?;
        C::write_data(&value, context)
    }

    #[inline(always)]
    fn read_data(context: &mut ReadContext) -> Result<Mutex<T>, Error> {
        Ok(Mutex::new(C::read_data(context)?))
    }

    #[inline(always)]
    fn read_data_with_type(
        context: &mut ReadContext,
        remote_data_type: &FieldType,
    ) -> Result<Mutex<T>, Error> {
        Ok(Mutex::new(C::read_data_with_type(
            context,
            remote_data_type,
        )?))
    }

    #[inline(always)]
    fn read_field_with_type(
        context: &mut ReadContext,
        remote_field_type: &FieldType,
    ) -> Result<Mutex<T>, Error> {
        Ok(Mutex::new(C::read_field_with_type(
            context,
            remote_field_type,
        )?))
    }

    #[inline(always)]
    fn write_with_mode(
        value: &Mutex<T>,
        context: &mut WriteContext,
        ref_mode: RefMode,
        write_type_info: bool,
        has_generics: bool,
    ) -> Result<(), Error> {
        let value = lock_for_write(value)?;
        C::write_with_mode(&value, context, ref_mode, write_type_info, has_generics)
    }

    #[inline(always)]
    fn write_type_info_value(
        context: &mut WriteContext,
        target_type_id: std::any::TypeId,
    ) -> Result<Rc<TypeInfo>, Error> {
        C::write_type_info_value(context, target_type_id)
    }

    #[inline(always)]
    fn write_with_type_info(
        value: &Mutex<T>,
        context: &mut WriteContext,
        ref_mode: RefMode,
        type_info: &Rc<TypeInfo>,
        has_generics: bool,
    ) -> Result<(), Error> {
        let value = lock_for_write(value)?;
        C::write_with_type_info(&value, context, ref_mode, type_info, has_generics)
    }

    #[inline(always)]
    fn read_with_mode(
        context: &mut ReadContext,
        ref_mode: RefMode,
        read_type_info: bool,
    ) -> Result<Mutex<T>, Error> {
        Ok(Mutex::new(C::read_with_mode(
            context,
            ref_mode,
            read_type_info,
        )?))
    }

    #[inline(always)]
    fn read_with_type_info(
        context: &mut ReadContext,
        ref_mode: RefMode,
        type_info: &Rc<TypeInfo>,
    ) -> Result<Mutex<T>, Error> {
        Ok(Mutex::new(C::read_with_type_info(
            context, ref_mode, type_info,
        )?))
    }

    #[inline(always)]
    fn default_value(context: &mut ReadContext) -> Result<Mutex<T>, Error> {
        Ok(Mutex::new(C::default_value(context)?))
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
    fn read_type_info_value(
        context: &mut ReadContext,
    ) -> Result<super::codec::CodecReadType, Error> {
        C::read_type_info_value(context)
    }

    #[inline(always)]
    fn static_type_id() -> TypeId {
        C::static_type_id()
    }

    #[inline(always)]
    fn is_option() -> bool {
        C::is_option()
    }

    #[inline(always)]
    fn is_none(value: &Mutex<T>) -> bool {
        if !C::is_option() {
            return false;
        }
        // Codec inspection hooks cannot return poison errors. Inspect the
        // guarded value, then let the fallible write path reject the poison.
        C::is_none(&lock_for_inspection(value))
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
    fn dynamic_type_id(value: &Mutex<T>) -> Result<Option<std::any::TypeId>, Error> {
        let value = lock_for_write(value)?;
        C::dynamic_type_id(&value)
    }

    #[inline(always)]
    fn dynamic_type_is_direct() -> bool {
        false
    }
}

impl_single_carrier_serializer!(MutexSerializer, Mutex, MutexCodec, wrapper = true);
