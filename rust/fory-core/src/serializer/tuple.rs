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
use crate::resolver::context::{ReadContext, WriteContext};
use crate::resolver::type_resolver::TypeResolver;
use crate::serializer::collection::{read_collection_type_info, write_collection_type_info};
use crate::serializer::skip::skip_any_value;
use crate::serializer::{ForyDefault, Serializer};
use crate::types::TypeId;
use std::mem;

/// Helper function to write a tuple element based on its type characteristics.
/// This handles the different serialization strategies for various element types.
#[inline(always)]
fn write_tuple_element<T: Serializer>(elem: &T, context: &mut WriteContext) -> Result<(), Error> {
    if T::fory_is_option() || T::fory_is_shared_ref() || T::fory_static_type_id() == TypeId::UNKNOWN
    {
        // For Option, shared references, or unknown static types, use full write with ref tracking
        elem.fory_write(context, true, false, false)
    } else {
        // For concrete types with known static type IDs, directly write data
        elem.fory_write_data(context)
    }
}

/// Helper function to read a tuple element based on its type characteristics.
#[inline(always)]
fn read_tuple_element<T: Serializer + ForyDefault>(
    context: &mut ReadContext,
    _has_generics: bool,
) -> Result<T, Error> {
    if T::fory_is_option() || T::fory_is_shared_ref() || T::fory_static_type_id() == TypeId::UNKNOWN
    {
        // For Option, shared references, or unknown static types, use full read with ref tracking
        T::fory_read(context, true, false)
    } else {
        // For concrete types with known static type IDs, directly read data
        T::fory_read_data(context)
    }
}

impl<T0: Serializer + ForyDefault> Serializer for (T0,) {
    #[inline(always)]
    fn fory_write_data(&self, context: &mut WriteContext) -> Result<(), Error> {
        if !context.is_compatible() && !context.is_xlang() {
            // Non-compatible mode: write elements directly
            write_tuple_element(&self.0, context)?;
        } else {
            // Compatible mode: use collection protocol (heterogeneous)
            context.writer.write_varuint32(1);
            let header = 0u8; // No IS_SAME_TYPE flag
            context.writer.write_u8(header);
            self.0.fory_write(context, true, true, false)?;
        }
        Ok(())
    }

    #[inline(always)]
    fn fory_write_type_info(context: &mut WriteContext) -> Result<(), Error> {
        write_collection_type_info(context, TypeId::LIST as u32)
    }

    #[inline(always)]
    fn fory_read_data(context: &mut ReadContext) -> Result<Self, Error> {
        if !context.is_compatible() && !context.is_xlang() {
            // Non-compatible mode: read elements directly
            let elem0 = read_tuple_element::<T0>(context, false)?;
            Ok((elem0,))
        } else {
            // Compatible mode: read collection protocol (heterogeneous)
            let len = context.reader.read_varuint32()?;
            let _header = context.reader.read_u8()?;

            let elem0 = if len > 0 {
                T0::fory_read(context, true, true)?
            } else {
                T0::fory_default()
            };

            // Skip any extra elements beyond the first
            for _ in 1..len {
                skip_any_value(context, true)?;
            }

            Ok((elem0,))
        }
    }

    #[inline(always)]
    fn fory_read_type_info(context: &mut ReadContext) -> Result<(), Error> {
        read_collection_type_info(context, TypeId::LIST as u32)
    }

    #[inline(always)]
    fn fory_reserved_space() -> usize {
        mem::size_of::<u32>()
    }

    #[inline(always)]
    fn fory_get_type_id(_: &TypeResolver) -> Result<u32, Error> {
        Ok(TypeId::LIST as u32)
    }

    #[inline(always)]
    fn fory_type_id_dyn(&self, _: &TypeResolver) -> Result<u32, Error> {
        Ok(TypeId::LIST as u32)
    }

    #[inline(always)]
    fn fory_static_type_id() -> TypeId {
        TypeId::LIST
    }

    #[inline(always)]
    fn fory_is_wrapper_type() -> bool
    where
        Self: Sized,
    {
        true
    }

    #[inline(always)]
    fn as_any(&self) -> &dyn std::any::Any {
        self
    }
}

impl<T0: ForyDefault> ForyDefault for (T0,) {
    #[inline(always)]
    fn fory_default() -> Self {
        (T0::fory_default(),)
    }
}

/// Macro to implement Serializer for tuples of various sizes.
///
/// This handles two serialization modes:
/// 1. Non-compatible mode: Write elements one by one without collection headers and type metadata
/// 2. Compatible mode: Use full collection protocol with headers and type info (always heterogeneous)
///
/// # User Usage
///
/// Fory supports tuples up to 22 elements by default. For longer tuples (23+ elements),
/// invoke this macro manually:
///
/// ```rust,ignore
/// fory::impl_tuple_serializer!(T0, T1, T2, ..., T23; 1, 2, 3, ..., 23);
/// ```
#[macro_export]
macro_rules! impl_tuple_serializer {
    // Multiple element tuples (2+)
    ($T0:ident $(, $T:ident)+; $($idx:tt),+) => {
        impl<$T0: Serializer + ForyDefault, $($T: Serializer + ForyDefault),*> Serializer for ($T0, $($T),*) {
            #[inline(always)]
            fn fory_write_data(&self, context: &mut WriteContext) -> Result<(), Error> {
                if !context.is_compatible() && !context.is_xlang() {
                    // Non-compatible mode: write elements directly one by one
                    write_tuple_element(&self.0, context)?;
                    $(
                        write_tuple_element(&self.$idx, context)?;
                    )*
                } else {
                    // Compatible mode: use collection protocol (always heterogeneous)
                    let len = 1 $(+ { let _ = stringify!($idx); 1 })*;
                    context.writer.write_varuint32(len as u32);

                    // Write header without IS_SAME_TYPE flag
                    let header = 0u8;
                    context.writer.write_u8(header);

                    // Write each element with its type info
                    self.0.fory_write(context, true, true, false)?;
                    $(
                        self.$idx.fory_write(context, true, true, false)?;
                    )*
                }
                Ok(())
            }

            #[inline(always)]
            fn fory_write_type_info(context: &mut WriteContext) -> Result<(), Error> {
                write_collection_type_info(context, TypeId::LIST as u32)
            }

            #[inline(always)]
            fn fory_read_data(context: &mut ReadContext) -> Result<Self, Error> {
                if !context.is_compatible() && !context.is_xlang() {
                    // Non-compatible mode: read elements directly
                    let elem0 = read_tuple_element::<$T0>(context, false)?;
                    $(
                        #[allow(non_snake_case)]
                        let $T = read_tuple_element::<$T>(context, false)?;
                    )*
                    Ok((elem0, $($T),*))
                } else {
                    // Compatible mode: read collection protocol (always heterogeneous)
                    // Handle flexible length: use defaults for missing elements, skip extras
                    let len = context.reader.read_varuint32()?;
                    let _header = context.reader.read_u8()?;

                    // Track how many elements we've read
                    let mut index = 0u32;

                    // Read first element or use default
                    let elem0 = if index < len {
                        index += 1;
                        $T0::fory_read(context, true, true)?
                    } else {
                        $T0::fory_default()
                    };

                    // Read remaining elements or use defaults
                    $(
                        #[allow(non_snake_case)]
                        let $T = if index < len {
                            index += 1;
                            $T::fory_read(context, true, true)?
                        } else {
                            $T::fory_default()
                        };
                    )*

                    // Skip any extra elements beyond what we expect
                    for _ in index..len {
                        skip_any_value(context, true)?;
                    }

                    Ok((elem0, $($T),*))
                }
            }

            #[inline(always)]
            fn fory_read_type_info(context: &mut ReadContext) -> Result<(), Error> {
                read_collection_type_info(context, TypeId::LIST as u32)
            }

            #[inline(always)]
            fn fory_reserved_space() -> usize {
                mem::size_of::<u32>() // Size for length
            }

            #[inline(always)]
            fn fory_get_type_id(_: &TypeResolver) -> Result<u32, Error> {
                Ok(TypeId::LIST as u32)
            }

            #[inline(always)]
            fn fory_type_id_dyn(&self, _: &TypeResolver) -> Result<u32, Error> {
                Ok(TypeId::LIST as u32)
            }

            #[inline(always)]
            fn fory_static_type_id() -> TypeId {
                TypeId::LIST
            }

            #[inline(always)]
            fn fory_is_wrapper_type() -> bool
                where
                    Self: Sized, {
                true
            }

            #[inline(always)]
            fn as_any(&self) -> &dyn std::any::Any {
                self
            }
        }

        impl<$T0: ForyDefault, $($T: ForyDefault),*> ForyDefault for ($T0, $($T),*) {
            #[inline(always)]
            fn fory_default() -> Self {
                ($T0::fory_default(), $($T::fory_default()),*)
            }
        }
    };
}

// Implement Serializer for tuples of size 2-22
impl_tuple_serializer!(T0, T1; 1);
impl_tuple_serializer!(T0, T1, T2; 1, 2);
impl_tuple_serializer!(T0, T1, T2, T3; 1, 2, 3);
impl_tuple_serializer!(T0, T1, T2, T3, T4; 1, 2, 3, 4);
impl_tuple_serializer!(T0, T1, T2, T3, T4, T5; 1, 2, 3, 4, 5);
impl_tuple_serializer!(T0, T1, T2, T3, T4, T5, T6; 1, 2, 3, 4, 5, 6);
impl_tuple_serializer!(T0, T1, T2, T3, T4, T5, T6, T7; 1, 2, 3, 4, 5, 6, 7);
impl_tuple_serializer!(T0, T1, T2, T3, T4, T5, T6, T7, T8; 1, 2, 3, 4, 5, 6, 7, 8);
impl_tuple_serializer!(T0, T1, T2, T3, T4, T5, T6, T7, T8, T9; 1, 2, 3, 4, 5, 6, 7, 8, 9);
impl_tuple_serializer!(T0, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10; 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
impl_tuple_serializer!(T0, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11; 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
impl_tuple_serializer!(T0, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12; 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
impl_tuple_serializer!(T0, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13; 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13);
impl_tuple_serializer!(T0, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14; 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14);
impl_tuple_serializer!(T0, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15; 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);
impl_tuple_serializer!(T0, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16; 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16);
impl_tuple_serializer!(T0, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17; 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17);
impl_tuple_serializer!(T0, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18; 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18);
impl_tuple_serializer!(T0, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19; 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19);
impl_tuple_serializer!(T0, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20; 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20);
impl_tuple_serializer!(T0, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21; 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21);
