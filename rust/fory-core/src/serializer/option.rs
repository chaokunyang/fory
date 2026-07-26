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
use crate::meta::FieldType;
use crate::resolver::{RefMode, TypeInfo, TypeResolver};
use crate::serializer::codec::{Codec, OptionCodec, SerializerCodec};
use crate::serializer::Serializer;
use crate::type_id::TypeId;
use std::marker::PhantomData;
use std::rc::Rc;

/// Fory-owned static composition for `Option<S::Target>`.
pub struct OptionSerializer<S>(PhantomData<fn() -> S>);

type RootCodec<S> = OptionCodec<<S as Serializer>::Target, SerializerCodec<S, false, false>, false>;

impl<S: Serializer> Serializer for OptionSerializer<S> {
    type Target = Option<S::Target>;

    #[inline(always)]
    fn write_data(value: &Self::Target, context: &mut WriteContext) -> Result<(), Error> {
        <RootCodec<S> as Codec<Self::Target>>::write_data(value, context)
    }

    #[inline(always)]
    fn read_data(context: &mut ReadContext) -> Result<Self::Target, Error> {
        <RootCodec<S> as Codec<Self::Target>>::read_data(context)
    }

    #[inline(always)]
    fn default_value(context: &mut ReadContext) -> Result<Self::Target, Error> {
        <RootCodec<S> as Codec<Self::Target>>::default_value(context)
    }

    #[inline(always)]
    fn write(
        value: &Self::Target,
        context: &mut WriteContext,
        ref_mode: RefMode,
        write_type_info: bool,
        has_generics: bool,
    ) -> Result<(), Error> {
        <RootCodec<S> as Codec<Self::Target>>::write_with_mode(
            value,
            context,
            ref_mode,
            write_type_info,
            has_generics,
        )
    }

    #[inline(always)]
    fn write_type_info_value(
        context: &mut WriteContext,
        target_type_id: std::any::TypeId,
    ) -> Result<Rc<TypeInfo>, Error> {
        <RootCodec<S> as Codec<Self::Target>>::write_type_info_value(context, target_type_id)
    }

    #[inline(always)]
    fn write_with_type_info(
        value: &Self::Target,
        context: &mut WriteContext,
        ref_mode: RefMode,
        type_info: &Rc<TypeInfo>,
        has_generics: bool,
    ) -> Result<(), Error> {
        <RootCodec<S> as Codec<Self::Target>>::write_with_type_info(
            value,
            context,
            ref_mode,
            type_info,
            has_generics,
        )
    }

    #[inline(always)]
    fn read(
        context: &mut ReadContext,
        ref_mode: RefMode,
        read_type_info: bool,
    ) -> Result<Self::Target, Error> {
        <RootCodec<S> as Codec<Self::Target>>::read_with_mode(context, ref_mode, read_type_info)
    }

    #[inline(always)]
    fn read_with_type_info(
        context: &mut ReadContext,
        ref_mode: RefMode,
        type_info: &Rc<TypeInfo>,
    ) -> Result<Self::Target, Error> {
        <RootCodec<S> as Codec<Self::Target>>::read_with_type_info(context, ref_mode, type_info)
    }

    #[inline(always)]
    fn field_type<const NULLABLE: bool, const TRACK_REF: bool>(
        type_resolver: &TypeResolver,
    ) -> Result<FieldType, Error> {
        let _ = NULLABLE;
        <OptionCodec<S::Target, SerializerCodec<S, false, false>, TRACK_REF> as Codec<
            Self::Target,
        >>::field_type(type_resolver)
    }

    #[inline(always)]
    fn read_data_with_field_type(
        context: &mut ReadContext,
        remote_field_type: &FieldType,
    ) -> Result<Self::Target, Error> {
        <RootCodec<S> as Codec<Self::Target>>::read_data_with_type(context, remote_field_type)
    }

    #[inline(always)]
    fn write_type_info(context: &mut WriteContext) -> Result<(), Error> {
        <RootCodec<S> as Codec<Self::Target>>::write_type_info(context)
    }

    #[inline(always)]
    fn read_type_info(context: &mut ReadContext) -> Result<(), Error> {
        <RootCodec<S> as Codec<Self::Target>>::read_type_info(context)
    }

    #[inline(always)]
    fn static_type_id() -> TypeId {
        <RootCodec<S> as Codec<Self::Target>>::static_type_id()
    }

    #[inline(always)]
    fn reserved_space() -> usize {
        <RootCodec<S> as Codec<Self::Target>>::reserved_space()
    }

    #[inline(always)]
    fn is_option() -> bool {
        true
    }

    #[inline(always)]
    fn is_none(value: &Self::Target) -> bool {
        value.is_none()
    }

    #[inline(always)]
    fn is_polymorphic() -> bool {
        <RootCodec<S> as Codec<Self::Target>>::is_polymorphic()
    }

    #[inline(always)]
    fn is_shared_ref() -> bool {
        <RootCodec<S> as Codec<Self::Target>>::is_shared_ref()
    }

    #[inline(always)]
    fn is_wrapper_type() -> bool {
        true
    }

    #[inline(always)]
    fn dynamic_type_id(value: &Self::Target) -> Result<Option<std::any::TypeId>, Error> {
        match value {
            Some(value) => S::dynamic_type_id(value),
            None => Ok(None),
        }
    }

    #[inline(always)]
    fn dynamic_type_is_direct() -> bool {
        S::dynamic_type_is_direct()
    }
}

impl<T> Serializer for Option<T>
where
    T: Serializer<Target = T>,
{
    type Target = Self;

    #[inline(always)]
    fn write_data(value: &Self, context: &mut WriteContext) -> Result<(), Error> {
        OptionSerializer::<T>::write_data(value, context)
    }

    #[inline(always)]
    fn read_data(context: &mut ReadContext) -> Result<Self, Error> {
        OptionSerializer::<T>::read_data(context)
    }

    #[inline(always)]
    fn default_value(context: &mut ReadContext) -> Result<Self, Error> {
        OptionSerializer::<T>::default_value(context)
    }

    #[inline(always)]
    fn write(
        value: &Self,
        context: &mut WriteContext,
        ref_mode: RefMode,
        write_type_info: bool,
        has_generics: bool,
    ) -> Result<(), Error> {
        OptionSerializer::<T>::write(value, context, ref_mode, write_type_info, has_generics)
    }

    #[inline(always)]
    fn write_type_info_value(
        context: &mut WriteContext,
        target_type_id: std::any::TypeId,
    ) -> Result<Rc<TypeInfo>, Error> {
        OptionSerializer::<T>::write_type_info_value(context, target_type_id)
    }

    #[inline(always)]
    fn write_with_type_info(
        value: &Self,
        context: &mut WriteContext,
        ref_mode: RefMode,
        type_info: &Rc<TypeInfo>,
        has_generics: bool,
    ) -> Result<(), Error> {
        OptionSerializer::<T>::write_with_type_info(
            value,
            context,
            ref_mode,
            type_info,
            has_generics,
        )
    }

    #[inline(always)]
    fn read(
        context: &mut ReadContext,
        ref_mode: RefMode,
        read_type_info: bool,
    ) -> Result<Self, Error> {
        OptionSerializer::<T>::read(context, ref_mode, read_type_info)
    }

    #[inline(always)]
    fn read_with_type_info(
        context: &mut ReadContext,
        ref_mode: RefMode,
        type_info: &Rc<TypeInfo>,
    ) -> Result<Self, Error> {
        OptionSerializer::<T>::read_with_type_info(context, ref_mode, type_info)
    }

    #[inline(always)]
    fn field_type<const NULLABLE: bool, const TRACK_REF: bool>(
        type_resolver: &TypeResolver,
    ) -> Result<FieldType, Error> {
        OptionSerializer::<T>::field_type::<NULLABLE, TRACK_REF>(type_resolver)
    }

    #[inline(always)]
    fn read_data_with_field_type(
        context: &mut ReadContext,
        remote_field_type: &FieldType,
    ) -> Result<Self, Error> {
        OptionSerializer::<T>::read_data_with_field_type(context, remote_field_type)
    }

    #[inline(always)]
    fn write_type_info(context: &mut WriteContext) -> Result<(), Error> {
        OptionSerializer::<T>::write_type_info(context)
    }

    #[inline(always)]
    fn read_type_info(context: &mut ReadContext) -> Result<(), Error> {
        OptionSerializer::<T>::read_type_info(context)
    }

    #[inline(always)]
    fn static_type_id() -> TypeId {
        OptionSerializer::<T>::static_type_id()
    }

    #[inline(always)]
    fn reserved_space() -> usize {
        OptionSerializer::<T>::reserved_space()
    }

    #[inline(always)]
    fn is_option() -> bool {
        true
    }

    #[inline(always)]
    fn is_none(value: &Self) -> bool {
        value.is_none()
    }

    #[inline(always)]
    fn is_polymorphic() -> bool {
        OptionSerializer::<T>::is_polymorphic()
    }

    #[inline(always)]
    fn is_shared_ref() -> bool {
        OptionSerializer::<T>::is_shared_ref()
    }

    #[inline(always)]
    fn is_wrapper_type() -> bool {
        true
    }

    #[inline(always)]
    fn dynamic_type_id(value: &Self) -> Result<Option<std::any::TypeId>, Error> {
        OptionSerializer::<T>::dynamic_type_id(value)
    }

    #[inline(always)]
    fn dynamic_type_is_direct() -> bool {
        OptionSerializer::<T>::dynamic_type_is_direct()
    }
}
