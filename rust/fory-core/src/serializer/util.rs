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

use crate::error::Error;
use crate::meta::{NAMESPACE_DECODER, TYPE_NAME_DECODER};
use crate::resolver::context::{ReadContext, WriteContext};
use crate::serializer::{bool, ForyDefault, Serializer};
use crate::types::{is_primitive_type, TypeId};
use crate::types::{RefFlag, PRIMITIVE_TYPES};
use crate::{ensure, TypeResolver};
use std::any::Any;

#[inline(always)]
pub fn write_ref_info_data<T: Serializer + 'static>(
    record: &T,
    context: &mut WriteContext,
    skip_ref_flag: bool,
    skip_type_info: bool,
) -> Result<(), Error> {
    if record.fory_is_none() {
        context.writer.write_i8(RefFlag::Null as i8);
    } else {
        if !skip_ref_flag {
            context.writer.write_i8(RefFlag::NotNullValue as i8);
        }
        if !skip_type_info {
            T::fory_write_type_info(context)?;
        }
        record.fory_write_data(context)?;
    }
    Ok(())
}

#[inline(always)]
pub fn read_ref_info_data<T: Serializer + ForyDefault>(
    context: &mut ReadContext,
    skip_ref_flag: bool,
    skip_type_info: bool,
) -> Result<T, Error> {
    if !skip_ref_flag {
        let ref_flag = context.reader.read_i8()?;
        if ref_flag == RefFlag::Null as i8 {
            Ok(T::fory_default())
        } else if ref_flag == (RefFlag::NotNullValue as i8) {
            if !skip_type_info {
                T::fory_read_type_info(context)?;
            }
            T::fory_read_data(context)
        } else if ref_flag == (RefFlag::RefValue as i8) {
            // First time seeing this referenceable object
            if !skip_type_info {
                T::fory_read_type_info(context)?;
            }
            T::fory_read_data(context)
        } else if ref_flag == (RefFlag::Ref as i8) {
            // This is a reference to a previously deserialized object
            // For now, just return default - this should be handled by specific types
            Ok(T::fory_default())
        } else {
            unimplemented!("Unknown ref flag: {}", ref_flag)
        }
    } else {
        if !skip_type_info {
            T::fory_read_type_info(context)?;
        }
        T::fory_read_data(context)
    }
}

#[inline(always)]
pub fn write_type_info<T: Serializer>(context: &mut WriteContext) -> Result<(), Error> {
    let type_id = T::fory_get_type_id(context.get_type_resolver())?;
    context.writer.write_varuint32(type_id);
    Ok(())
}

#[inline(always)]
pub fn read_type_info<T: Serializer>(context: &mut ReadContext) -> Result<(), Error> {
    let local_type_id = T::fory_get_type_id(context.get_type_resolver())?;
    let remote_type_id = context.reader.read_varuint32()?;
    ensure!(
        local_type_id == remote_type_id,
        Error::TypeMismatch(local_type_id, remote_type_id)
    );
    Ok(())
}

pub(super) fn write_ext_type_info<T: Serializer>(context: &mut WriteContext) -> Result<(), Error>
where
    T: Sized,
{
    // default implementation only for ext/named_ext
    let type_id = T::fory_get_type_id(context.get_type_resolver())?;
    context.writer.write_varuint32(type_id);
    if type_id & 0xff == TypeId::EXT as u32 {
        return Ok(());
    }
    let rs_type_id = std::any::TypeId::of::<T>();
    if context.is_share_meta() {
        let meta_index = context.push_meta(rs_type_id)? as u32;
        context.writer.write_varuint32(meta_index);
    } else {
        let type_info = context.get_type_resolver().get_type_info(&rs_type_id)?;
        let namespace = type_info.get_namespace().to_owned();
        let type_name = type_info.get_type_name().to_owned();
        context.write_meta_string_bytes(&namespace)?;
        context.write_meta_string_bytes(&type_name)?;
    }
    Ok(())
}

pub(super) fn read_ext_type_info<T: Serializer>(context: &mut ReadContext) -> Result<(), Error>
where
    T: Sized,
{
    // default implementation only for ext/named_ext
    let local_type_id = T::fory_get_type_id(context.get_type_resolver())?;
    let remote_type_id = context.reader.read_varuint32()?;
    ensure!(
        local_type_id == remote_type_id,
        Error::TypeMismatch(local_type_id, remote_type_id)
    );
    if local_type_id & 0xff == TypeId::EXT as u32 {
        return Ok(());
    }
    if context.is_share_meta() {
        let _meta_index = context.reader.read_varuint32()?;
    } else {
        let _namespace_msb = context.read_meta_string_bytes()?;
        let _type_name_msb = context.read_meta_string_bytes()?;
    }
    Ok(())
}

// only used by struct/enum/ext
pub(super) fn read_compatible_default<T: Serializer>(context: &mut ReadContext) -> Result<T, Error>
where
    T: Sized,
{
    // default logic only for ext/named_ext
    let remote_type_id = context.reader.read_varuint32()?;
    let local_type_id = T::fory_get_type_id(context.get_type_resolver())?;
    ensure!(
        local_type_id == remote_type_id,
        Error::TypeMismatch(local_type_id, remote_type_id)
    );
    if local_type_id & 0xff == TypeId::EXT as u32 {
        context
            .get_type_resolver()
            .get_ext_harness(local_type_id)?
            .get_read_data_fn()(context)
        .and_then(|b: Box<dyn Any>| {
            b.downcast::<T>()
                .map(|boxed_self| *boxed_self)
                .map_err(|_| Error::TypeError("downcast to Self failed".into()))
        })
    } else {
        let (namespace, type_name) = if context.is_share_meta() {
            let meta_index = context.reader.read_varuint32()?;
            let type_meta = context.get_meta(meta_index as usize)?;
            (type_meta.get_namespace(), type_meta.get_type_name())
        } else {
            let nsb = context.read_meta_string_bytes()?;
            let tsb = context.read_meta_string_bytes()?;
            let ns = NAMESPACE_DECODER.decode(&nsb.bytes, nsb.encoding)?;
            let ts = TYPE_NAME_DECODER.decode(&tsb.bytes, tsb.encoding)?;
            (ns, ts)
        };
        context
            .get_type_resolver()
            .get_ext_name_harness(&namespace, &type_name)?
            .get_read_data_fn()(context)
        .and_then(|b: Box<dyn Any>| {
            b.downcast::<T>()
                .map(|boxed_self| *boxed_self)
                .map_err(|_| Error::TypeError("downcast to Self failed".into()))
        })
    }
}

#[inline(always)]
pub fn get_skip_ref_flag<T: Serializer>() -> bool {
    let type_id = T::fory_static_type_id();
    !T::fory_is_option() && is_primitive_type(type_id)
}

/// Check at runtime whether type info should be skipped for a given type id.
///
/// According to xlang_serialization_spec.md:
/// - For enums (ENUM/NAMED_ENUM), we should skip writing type info
/// - For structs and ext types, we should write type info
#[inline]
pub fn should_skip_type_info_at_runtime(type_id: u32) -> bool {
    let internal_type_id = (type_id & 0xff) as i8;
    if internal_type_id == TypeId::ENUM as i8 || internal_type_id == TypeId::NAMED_ENUM as i8 {
        true
    } else {
        false
    }
}

#[inline(always)]
pub fn write_dyn_data_generic<T: Serializer>(
    value: &T,
    context: &mut WriteContext,
    has_generics: bool,
) -> Result<(), Error> {
    let concrete_type_id: std::any::TypeId = value.type_id();
    let serializer_fn = context
        .write_any_typeinfo(concrete_type_id)?
        .get_harness()
        .get_write_data_fn();
    serializer_fn(value, context, has_generics)
}
