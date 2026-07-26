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

use crate::buffer::{Reader, Writer};
use crate::context::{ReadContext, WriteContext};
use crate::error::Error;
use crate::meta::FieldType;
use crate::serializer::util::read_basic_type_info;
use crate::serializer::{Serializer, SerializerOwner};
use crate::type_id::{self, TypeId};
use crate::types::bfloat16::bfloat16;
use crate::types::float16::float16;
use std::sync::Arc;

macro_rules! impl_num_serializer {
    (
        $ty:ty,
        $writer:expr,
        $reader:expr,
        $type_id:expr,
        $array_type_id:expr,
        $default:expr,
        $compatible_reader:expr
    ) => {
        impl Serializer for $ty {
            type Target = Self;

            const OWNER: SerializerOwner = SerializerOwner::Fory;
            const PRIMITIVE_ARRAY_TYPE_ID: Option<TypeId> = Some($array_type_id);

            #[inline(always)]
            fn write(value: &Self, context: &mut WriteContext) -> Result<(), Error> {
                $writer(&mut context.writer, *value);
                Ok(())
            }

            #[inline(always)]
            fn read(context: &mut ReadContext) -> Result<Self, Error> {
                $reader(&mut context.reader)
            }

            #[inline(always)]
            fn read_data_with_field_type(
                context: &mut ReadContext,
                remote_field_type: &FieldType,
            ) -> Result<Self, Error> {
                ($compatible_reader)(context, remote_field_type)
            }

            #[inline(always)]
            fn default_value(_: &mut ReadContext) -> Result<Self, Error> {
                Ok($default)
            }

            #[inline(always)]
            fn read_arc_any(
                context: &mut ReadContext,
            ) -> Result<Arc<dyn std::any::Any + Send + Sync>, Error> {
                Ok(Arc::new(Self::read(context)?))
            }

            #[inline(always)]
            fn reserved_space() -> usize {
                std::mem::size_of::<Self>()
            }

            #[inline(always)]
            fn static_type_id() -> TypeId {
                $type_id
            }

            #[inline(always)]
            fn write_type_info(context: &mut WriteContext) -> Result<(), Error> {
                context.writer.write_var_u32($type_id as u32);
                Ok(())
            }

            #[inline(always)]
            fn read_type_info(context: &mut ReadContext) -> Result<(), Error> {
                read_basic_type_info::<Self>(context)
            }
        }
    };
}

impl_num_serializer!(
    i8,
    Writer::write_i8,
    Reader::read_i8,
    TypeId::INT8,
    TypeId::INT8_ARRAY,
    0,
    |context: &mut ReadContext, _: &FieldType| context.reader.read_i8()
);
impl_num_serializer!(
    i16,
    Writer::write_i16,
    Reader::read_i16,
    TypeId::INT16,
    TypeId::INT16_ARRAY,
    0,
    |context: &mut ReadContext, _: &FieldType| context.reader.read_i16()
);
impl_num_serializer!(
    i32,
    Writer::write_var_i32,
    Reader::read_var_i32,
    TypeId::VARINT32,
    TypeId::INT32_ARRAY,
    0,
    |context: &mut ReadContext, field_type: &FieldType| match field_type.type_id {
        type_id::INT32 => context.reader.read_i32(),
        type_id::VARINT32 => context.reader.read_var_i32(),
        remote => Err(Error::type_mismatch(type_id::VARINT32, remote)),
    }
);
impl_num_serializer!(
    i64,
    Writer::write_var_i64,
    Reader::read_var_i64,
    TypeId::VARINT64,
    TypeId::INT64_ARRAY,
    0,
    |context: &mut ReadContext, field_type: &FieldType| match field_type.type_id {
        type_id::INT64 => context.reader.read_i64(),
        type_id::VARINT64 => context.reader.read_var_i64(),
        type_id::TAGGED_INT64 => context.reader.read_tagged_i64(),
        remote => Err(Error::type_mismatch(type_id::VARINT64, remote)),
    }
);
impl_num_serializer!(
    f32,
    Writer::write_f32,
    Reader::read_f32,
    TypeId::FLOAT32,
    TypeId::FLOAT32_ARRAY,
    0.0,
    |context: &mut ReadContext, _: &FieldType| context.reader.read_f32()
);
impl_num_serializer!(
    f64,
    Writer::write_f64,
    Reader::read_f64,
    TypeId::FLOAT64,
    TypeId::FLOAT64_ARRAY,
    0.0,
    |context: &mut ReadContext, _: &FieldType| context.reader.read_f64()
);
impl_num_serializer!(
    float16,
    Writer::write_f16,
    Reader::read_f16,
    TypeId::FLOAT16,
    TypeId::FLOAT16_ARRAY,
    float16::ZERO,
    |context: &mut ReadContext, _: &FieldType| context.reader.read_f16()
);
impl_num_serializer!(
    bfloat16,
    Writer::write_bf16,
    Reader::read_bf16,
    TypeId::BFLOAT16,
    TypeId::BFLOAT16_ARRAY,
    bfloat16::ZERO,
    |context: &mut ReadContext, _: &FieldType| context.reader.read_bf16()
);
impl_num_serializer!(
    i128,
    Writer::write_i128,
    Reader::read_i128,
    TypeId::INT128,
    TypeId::INT128_ARRAY,
    0,
    |context: &mut ReadContext, _: &FieldType| context.reader.read_i128()
);
impl_num_serializer!(
    isize,
    Writer::write_isize,
    Reader::read_isize,
    TypeId::ISIZE,
    TypeId::ISIZE_ARRAY,
    0,
    |context: &mut ReadContext, _: &FieldType| context.reader.read_isize()
);
