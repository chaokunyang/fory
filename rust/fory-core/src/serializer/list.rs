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

use super::codec::{Codec, SerializerCodec, VecCodec};
use crate::context::{ReadContext, WriteContext};
use crate::error::Error;
use crate::meta::FieldType;
use crate::resolver::{RefMode, TypeInfo, TypeResolver};
use crate::serializer::Serializer;
use crate::type_id::TypeId;
use std::collections::{LinkedList, VecDeque};
use std::marker::PhantomData;
use std::rc::Rc;

type RootVecCodec<S> = VecCodec<
    <S as Serializer>::Target,
    SerializerCodec<S, false, false>,
    false,
    false,
    false,
    false,
>;

type FieldVecCodec<S, const NULLABLE: bool, const TRACK_REF: bool> = VecCodec<
    <S as Serializer>::Target,
    SerializerCodec<S, false, false>,
    false,
    false,
    NULLABLE,
    TRACK_REF,
>;

/// Statically serializes `Vec<S::Target>` at roots or recursive carrier nodes.
///
/// This zero-sized carrier composes the child serializer `S` and is not
/// registered independently.
pub struct VecSerializer<S>(PhantomData<fn() -> S>);

impl<S: Serializer> Serializer for VecSerializer<S> {
    type Target = Vec<S::Target>;

    #[inline(always)]
    fn write_data(value: &Self::Target, context: &mut WriteContext) -> Result<(), Error> {
        <RootVecCodec<S> as Codec<Self::Target>>::write_data(value, context)
    }

    #[inline(always)]
    fn read_data(context: &mut ReadContext) -> Result<Self::Target, Error> {
        <RootVecCodec<S> as Codec<Self::Target>>::read_data(context)
    }

    #[inline(always)]
    fn default_value(context: &mut ReadContext) -> Result<Self::Target, Error> {
        <RootVecCodec<S> as Codec<Self::Target>>::default_value(context)
    }

    #[inline(always)]
    fn write(
        value: &Self::Target,
        context: &mut WriteContext,
        ref_mode: RefMode,
        write_type_info: bool,
        has_generics: bool,
    ) -> Result<(), Error> {
        <RootVecCodec<S> as Codec<Self::Target>>::write_with_mode(
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
        <RootVecCodec<S> as Codec<Self::Target>>::write_with_mode(
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
        <RootVecCodec<S> as Codec<Self::Target>>::read_with_mode(context, ref_mode, read_type_info)
    }

    #[inline(always)]
    fn read_with_type_info(
        context: &mut ReadContext,
        ref_mode: RefMode,
        type_info: &Rc<TypeInfo>,
    ) -> Result<Self::Target, Error> {
        <RootVecCodec<S> as Codec<Self::Target>>::read_with_type_info(context, ref_mode, type_info)
    }

    #[inline(always)]
    fn field_type<const NULLABLE: bool, const TRACK_REF: bool>(
        type_resolver: &TypeResolver,
    ) -> Result<FieldType, Error> {
        <FieldVecCodec<S, NULLABLE, TRACK_REF> as Codec<Self::Target>>::field_type(type_resolver)
    }

    #[inline(always)]
    fn read_data_with_field_type(
        context: &mut ReadContext,
        remote_field_type: &FieldType,
    ) -> Result<Self::Target, Error> {
        <RootVecCodec<S> as Codec<Self::Target>>::read_data_with_type(context, remote_field_type)
    }

    #[inline(always)]
    fn write_type_info(context: &mut WriteContext) -> Result<(), Error> {
        <RootVecCodec<S> as Codec<Self::Target>>::write_type_info(context)
    }

    #[inline(always)]
    fn read_type_info(context: &mut ReadContext) -> Result<(), Error> {
        <RootVecCodec<S> as Codec<Self::Target>>::read_type_info(context)
    }

    #[inline(always)]
    fn static_type_id() -> TypeId {
        <RootVecCodec<S> as Codec<Self::Target>>::static_type_id()
    }

    #[inline(always)]
    fn reserved_space() -> usize {
        <RootVecCodec<S> as Codec<Self::Target>>::reserved_space()
    }
}

impl<T> Serializer for Vec<T>
where
    T: Serializer<Target = T>,
{
    type Target = Self;

    #[inline(always)]
    fn write_data(value: &Self, context: &mut WriteContext) -> Result<(), Error> {
        <VecSerializer<T> as Serializer>::write_data(value, context)
    }

    #[inline(always)]
    fn read_data(context: &mut ReadContext) -> Result<Self, Error> {
        <VecSerializer<T> as Serializer>::read_data(context)
    }

    #[inline(always)]
    fn default_value(context: &mut ReadContext) -> Result<Self, Error> {
        <VecSerializer<T> as Serializer>::default_value(context)
    }

    #[inline(always)]
    fn write(
        value: &Self,
        context: &mut WriteContext,
        ref_mode: RefMode,
        write_type_info: bool,
        has_generics: bool,
    ) -> Result<(), Error> {
        <VecSerializer<T> as Serializer>::write(
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
        <VecSerializer<T> as Serializer>::write_data_with_generics(value, context, has_generics)
    }

    #[inline(always)]
    fn read(
        context: &mut ReadContext,
        ref_mode: RefMode,
        read_type_info: bool,
    ) -> Result<Self, Error> {
        <VecSerializer<T> as Serializer>::read(context, ref_mode, read_type_info)
    }

    #[inline(always)]
    fn read_with_type_info(
        context: &mut ReadContext,
        ref_mode: RefMode,
        type_info: &Rc<TypeInfo>,
    ) -> Result<Self, Error> {
        <VecSerializer<T> as Serializer>::read_with_type_info(context, ref_mode, type_info)
    }

    #[inline(always)]
    fn field_type<const NULLABLE: bool, const TRACK_REF: bool>(
        type_resolver: &TypeResolver,
    ) -> Result<FieldType, Error> {
        <VecSerializer<T> as Serializer>::field_type::<NULLABLE, TRACK_REF>(type_resolver)
    }

    #[inline(always)]
    fn read_data_with_field_type(
        context: &mut ReadContext,
        remote_field_type: &FieldType,
    ) -> Result<Self, Error> {
        <VecSerializer<T> as Serializer>::read_data_with_field_type(context, remote_field_type)
    }

    #[inline(always)]
    fn write_type_info(context: &mut WriteContext) -> Result<(), Error> {
        <VecSerializer<T> as Serializer>::write_type_info(context)
    }

    #[inline(always)]
    fn read_type_info(context: &mut ReadContext) -> Result<(), Error> {
        <VecSerializer<T> as Serializer>::read_type_info(context)
    }

    #[inline(always)]
    fn static_type_id() -> TypeId {
        <VecSerializer<T> as Serializer>::static_type_id()
    }

    #[inline(always)]
    fn reserved_space() -> usize {
        <VecSerializer<T> as Serializer>::reserved_space()
    }
}

impl_collection_carrier_codec!(VecDequeCodec, VecDeque, LIST, zst_no_backing = true);
impl_collection_carrier_codec!(LinkedListCodec, LinkedList, LIST, zst_no_backing = false);

impl_single_carrier_serializer!(VecDequeSerializer, VecDeque, VecDequeCodec, wrapper = false);

impl_single_carrier_serializer!(
    LinkedListSerializer,
    LinkedList,
    LinkedListCodec,
    wrapper = false
);
