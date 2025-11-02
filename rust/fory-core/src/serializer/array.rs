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
use crate::resolver::context::ReadContext;
use crate::resolver::context::WriteContext;
use crate::resolver::type_resolver::TypeResolver;
use crate::serializer::primitive_list;
use crate::serializer::{ForyDefault, Serializer};
use crate::types::TypeId;
use std::mem;

use super::collection::{
    read_collection_data, read_collection_type_info, write_collection_data,
    write_collection_type_info,
};

#[inline(always)]
fn get_primitive_type_id<T: Serializer>() -> TypeId {
    if T::fory_is_wrapper_type() {
        return TypeId::UNKNOWN;
    }
    match T::fory_static_type_id() {
        TypeId::BOOL => TypeId::BOOL_ARRAY,
        TypeId::INT8 => TypeId::INT8_ARRAY,
        TypeId::INT16 => TypeId::INT16_ARRAY,
        TypeId::INT32 => TypeId::INT32_ARRAY,
        TypeId::INT64 => TypeId::INT64_ARRAY,
        TypeId::FLOAT32 => TypeId::FLOAT32_ARRAY,
        TypeId::FLOAT64 => TypeId::FLOAT64_ARRAY,
        TypeId::U16 => TypeId::U16_ARRAY,
        TypeId::U32 => TypeId::U32_ARRAY,
        TypeId::U64 => TypeId::U64_ARRAY,
        TypeId::USIZE => TypeId::USIZE_ARRAY,
        _ => TypeId::UNKNOWN,
    }
}

#[inline(always)]
pub fn is_primitive_type<T: Serializer>() -> bool {
    if T::fory_is_wrapper_type() {
        return false;
    }
    matches!(
        T::fory_static_type_id(),
        TypeId::BOOL
            | TypeId::INT8
            | TypeId::INT16
            | TypeId::INT32
            | TypeId::INT64
            | TypeId::FLOAT32
            | TypeId::FLOAT64
            | TypeId::U8
            | TypeId::U16
            | TypeId::U32
            | TypeId::U64
            | TypeId::USIZE,
    )
}

// Implement Serializer for fixed-size arrays [T; N] where N is a const generic parameter
impl<T: Serializer + ForyDefault, const N: usize> Serializer for [T; N] {
    #[inline(always)]
    fn fory_write_data(&self, context: &mut WriteContext) -> Result<(), Error> {
        if is_primitive_type::<T>() {
            primitive_list::fory_write_data(self.as_slice(), context)
        } else {
            write_collection_data(self.iter(), context, false)
        }
    }

    #[inline(always)]
    fn fory_write_data_generic(
        &self,
        context: &mut WriteContext,
        has_generics: bool,
    ) -> Result<(), Error> {
        if is_primitive_type::<T>() {
            primitive_list::fory_write_data(self.as_slice(), context)
        } else {
            write_collection_data(self.iter(), context, has_generics)
        }
    }

    #[inline(always)]
    fn fory_write_type_info(context: &mut WriteContext) -> Result<(), Error> {
        let id = get_primitive_type_id::<T>();
        if id != TypeId::UNKNOWN {
            primitive_list::fory_write_type_info(context, id)
        } else {
            write_collection_type_info(context, TypeId::LIST as u32)
        }
    }

    #[inline(always)]
    fn fory_read_data(context: &mut ReadContext) -> Result<Self, Error> {
        if is_primitive_type::<T>() {
            // Read primitive array data
            let vec: Vec<T> = primitive_list::fory_read_data(context)?;
            // Ensure the vec has exactly N elements
            if vec.len() != N {
                return Err(Error::invalid_data(format!(
                    "Array length mismatch: expected {}, got {}",
                    N,
                    vec.len()
                )));
            }
            // Convert Vec<T> to [T; N]
            vec.try_into().map_err(|_| {
                Error::invalid_data(format!("Failed to convert Vec to array of size {}", N))
            })
        } else {
            // Read collection data as Vec and convert to array
            let vec: Vec<T> = read_collection_data(context)?;
            if vec.len() != N {
                return Err(Error::invalid_data(format!(
                    "Array length mismatch: expected {}, got {}",
                    N,
                    vec.len()
                )));
            }
            vec.try_into().map_err(|_| {
                Error::invalid_data(format!("Failed to convert Vec to array of size {}", N))
            })
        }
    }

    #[inline(always)]
    fn fory_read_type_info(context: &mut ReadContext) -> Result<(), Error> {
        let id = get_primitive_type_id::<T>();
        if id != TypeId::UNKNOWN {
            primitive_list::fory_read_type_info(context, id)
        } else {
            read_collection_type_info(context, TypeId::LIST as u32)
        }
    }

    #[inline(always)]
    fn fory_reserved_space() -> usize {
        if is_primitive_type::<T>() {
            primitive_list::fory_reserved_space::<T>()
        } else {
            // size of the array length
            mem::size_of::<u32>()
        }
    }

    #[inline(always)]
    fn fory_get_type_id(_: &TypeResolver) -> Result<u32, Error> {
        let id = get_primitive_type_id::<T>();
        if id != TypeId::UNKNOWN {
            Ok(id as u32)
        } else {
            Ok(TypeId::LIST as u32)
        }
    }

    #[inline(always)]
    fn fory_type_id_dyn(&self, _: &TypeResolver) -> Result<u32, Error> {
        let id = get_primitive_type_id::<T>();
        if id != TypeId::UNKNOWN {
            Ok(id as u32)
        } else {
            Ok(TypeId::LIST as u32)
        }
    }

    #[inline(always)]
    fn fory_static_type_id() -> TypeId
    where
        Self: Sized,
    {
        let id = get_primitive_type_id::<T>();
        if id != TypeId::UNKNOWN {
            id
        } else {
            TypeId::LIST
        }
    }

    #[inline(always)]
    fn as_any(&self) -> &dyn std::any::Any {
        self
    }
}

impl<T, const N: usize> ForyDefault for [T; N]
where
    T: ForyDefault,
{
    #[inline(always)]
    fn fory_default() -> Self {
        // Create an array by calling fory_default() for each element
        // We use MaybeUninit for safe initialization
        use std::mem::MaybeUninit;

        let mut arr: [MaybeUninit<T>; N] = unsafe { MaybeUninit::uninit().assume_init() };

        for elem in &mut arr {
            elem.write(T::fory_default());
        }

        // Safety: all elements are initialized
        unsafe {
            // Transmute from [MaybeUninit<T>; N] to [T; N]
            std::ptr::read(&arr as *const _ as *const [T; N])
        }
    }
}
