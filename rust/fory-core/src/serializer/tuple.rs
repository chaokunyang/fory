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

/// Trait for checking type-level properties of tuples.
/// Provides compile-time information about tuple homogeneity and size.
#[allow(dead_code)]
pub trait TupleTypeTraits: 'static {
    /// Returns true if all elements in the tuple have the same type.
    fn is_homogeneous() -> bool;

    /// Returns the number of elements in the tuple.
    fn tuple_len() -> usize;
}

/// Macro to implement TupleTypeTraits for tuples of various sizes.
macro_rules! impl_tuple_type_traits {
    // Single element tuple - always homogeneous
    ($T0:ident) => {
        impl<$T0: 'static> TupleTypeTraits for ($T0,) {
            #[inline(always)]
            fn is_homogeneous() -> bool {
                true
            }

            #[inline(always)]
            fn tuple_len() -> usize {
                1
            }
        }
    };

    // Multiple element tuples - check if all types are the same as T0
    ($T0:ident, $($T:ident),+) => {
        impl<$T0: 'static, $($T: 'static),+> TupleTypeTraits for ($T0, $($T),+) {
            #[inline(always)]
            fn is_homogeneous() -> bool {
                let type0 = std::any::TypeId::of::<$T0>();
                $(
                    if type0 != std::any::TypeId::of::<$T>() {
                        return false;
                    }
                )+
                true
            }

            #[inline(always)]
            fn tuple_len() -> usize {
                1 $(+ { let _ = stringify!($T); 1 })+
            }
        }
    };
}

// Implement TupleTypeTraits for tuples of size 1-22
impl_tuple_type_traits!(T0);
impl_tuple_type_traits!(T0, T1);
impl_tuple_type_traits!(T0, T1, T2);
impl_tuple_type_traits!(T0, T1, T2, T3);
impl_tuple_type_traits!(T0, T1, T2, T3, T4);
impl_tuple_type_traits!(T0, T1, T2, T3, T4, T5);
impl_tuple_type_traits!(T0, T1, T2, T3, T4, T5, T6);
impl_tuple_type_traits!(T0, T1, T2, T3, T4, T5, T6, T7);
impl_tuple_type_traits!(T0, T1, T2, T3, T4, T5, T6, T7, T8);
impl_tuple_type_traits!(T0, T1, T2, T3, T4, T5, T6, T7, T8, T9);
impl_tuple_type_traits!(T0, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10);
impl_tuple_type_traits!(T0, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11);
impl_tuple_type_traits!(T0, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12);
impl_tuple_type_traits!(T0, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13);
impl_tuple_type_traits!(T0, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14);
impl_tuple_type_traits!(T0, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15);
impl_tuple_type_traits!(T0, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16);
impl_tuple_type_traits!(
    T0, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17
);
impl_tuple_type_traits!(
    T0, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18
);
impl_tuple_type_traits!(
    T0, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19
);
impl_tuple_type_traits!(
    T0, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20
);
impl_tuple_type_traits!(
    T0, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20,
    T21
);

use crate::error::Error;
use crate::resolver::context::{ReadContext, WriteContext};
use crate::resolver::type_resolver::TypeResolver;
use crate::serializer::collection::{
    read_collection_type_info, write_collection_type_info, IS_SAME_TYPE,
};
use crate::serializer::{ForyDefault, Serializer};
use crate::types::TypeId;
use std::mem;

/// Helper function to write a tuple element based on its type characteristics.
/// This handles the different serialization strategies for various element types.
#[inline(always)]
fn write_tuple_element<T: Serializer>(
    elem: &T,
    context: &mut WriteContext,
    _has_generics: bool,
) -> Result<(), Error> {
    if T::fory_is_option() || T::fory_is_shared_ref() || T::fory_static_type_id() == TypeId::UNKNOWN
    {
        // For Option, shared references, or unknown static types, use full write with ref tracking
        elem.fory_write(context, true, false, false)
    } else {
        // For concrete types with known static type IDs, directly write data
        elem.fory_write_data_generic(context, false)
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

/// Macro to implement Serializer for tuples of various sizes.
///
/// This handles two serialization modes:
/// 1. Non-compatible mode: Write elements one by one without collection headers
/// 2. Compatible mode: Use full collection protocol with headers and type info
macro_rules! impl_tuple_serializer {
    // Single element tuple
    ($T0:ident; 0) => {
        impl<$T0: Serializer + ForyDefault> Serializer for ($T0,) {
            #[inline(always)]
            fn fory_write_data(&self, context: &mut WriteContext) -> Result<(), Error> {
                if !context.is_compatible() {
                    // Non-compatible mode: write elements directly
                    write_tuple_element(&self.0, context, false)?;
                } else {
                    // Compatible mode: use collection protocol
                    let is_homogeneous = Self::is_homogeneous();
                    let len = Self::tuple_len();
                    context.writer.write_varuint32(len as u32);

                    if is_homogeneous {
                        // Homogeneous tuple: write header + type + data
                        let header = IS_SAME_TYPE;

                        context.writer.write_u8(header);
                        $T0::fory_write_type_info(context)?;
                        write_tuple_element(&self.0, context, false)?;
                    } else {
                        // Single element is always homogeneous, this branch shouldn't be reached
                        let header = IS_SAME_TYPE;
                        context.writer.write_u8(header);
                        $T0::fory_write_type_info(context)?;
                        write_tuple_element(&self.0, context, false)?;
                    }
                }
                Ok(())
            }

            #[inline(always)]
            fn fory_write_data_generic(
                &self,
                context: &mut WriteContext,
                _has_generics: bool,
            ) -> Result<(), Error> {
                self.fory_write_data(context)
            }

            #[inline(always)]
            fn fory_write_type_info(context: &mut WriteContext) -> Result<(), Error> {
                write_collection_type_info(context, TypeId::LIST as u32)
            }

            #[inline(always)]
            fn fory_read_data(context: &mut ReadContext) -> Result<Self, Error> {
                if !context.is_compatible() {
                    // Non-compatible mode: read elements directly
                    let elem0 = read_tuple_element::<$T0>(context, false)?;
                    Ok((elem0,))
                } else {
                    // Compatible mode: read collection protocol
                    let len = context.reader.read_varuint32()?;
                    if len != 1 {
                        return Err(Error::type_error("Tuple length mismatch"));
                    }

                    let _header = context.reader.read_u8()?;
                    $T0::fory_read_type_info(context)?;

                    let elem0 = read_tuple_element::<$T0>(context, false)?;
                    Ok((elem0,))
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
            fn as_any(&self) -> &dyn std::any::Any {
                self
            }
        }

        impl<$T0: ForyDefault> ForyDefault for ($T0,) {
            #[inline(always)]
            fn fory_default() -> Self {
                ($T0::fory_default(),)
            }
        }
    };

    // Multiple element tuples
    ($T0:ident $(, $T:ident)*; $($idx:tt),*) => {
        impl<$T0: Serializer + ForyDefault, $($T: Serializer + ForyDefault),*> Serializer for ($T0, $($T),*) {
            #[inline(always)]
            fn fory_write_data(&self, context: &mut WriteContext) -> Result<(), Error> {
                if !context.is_compatible() {
                    // Non-compatible mode: write elements directly one by one
                    write_tuple_element(&self.0, context, false)?;
                    $(
                        write_tuple_element(&self.$idx, context, false)?;
                    )*
                } else {
                    // Compatible mode: use collection protocol
                    let is_homogeneous = Self::is_homogeneous();
                    let len = Self::tuple_len();
                    context.writer.write_varuint32(len as u32);

                    if is_homogeneous {
                        // Homogeneous tuple: write header + type + data
                        let header = IS_SAME_TYPE;
                        context.writer.write_u8(header);
                        $T0::fory_write_type_info(context)?;

                        write_tuple_element(&self.0, context, false)?;
                        $(
                            write_tuple_element(&self.$idx, context, false)?;
                        )*
                    } else {
                        // Heterogeneous tuple: write header + elements with individual types
                        let header = 0u8; // No IS_SAME_TYPE flag
                        context.writer.write_u8(header);

                        // Write each element with its type info
                        self.0.fory_write(context, false, true, false)?;
                        $(
                            self.$idx.fory_write(context, false, true, false)?;
                        )*
                    }
                }
                Ok(())
            }

            #[inline(always)]
            fn fory_write_data_generic(
                &self,
                context: &mut WriteContext,
                _has_generics: bool,
            ) -> Result<(), Error> {
                self.fory_write_data(context)
            }

            #[inline(always)]
            fn fory_write_type_info(context: &mut WriteContext) -> Result<(), Error> {
                write_collection_type_info(context, TypeId::LIST as u32)
            }

            #[inline(always)]
            fn fory_read_data(context: &mut ReadContext) -> Result<Self, Error> {
                if !context.is_compatible() {
                    // Non-compatible mode: read elements directly
                    let elem0 = read_tuple_element::<$T0>(context, false)?;
                    $(
                        let $T = read_tuple_element::<$T>(context, false)?;
                    )*
                    Ok((elem0, $($T),*))
                } else {
                    // Compatible mode: read collection protocol
                    let len = context.reader.read_varuint32()?;
                    let expected_len = Self::tuple_len();
                    if len != expected_len as u32 {
                        return Err(Error::type_error("Tuple length mismatch"));
                    }

                    let header = context.reader.read_u8()?;
                    let is_same_type = (header & IS_SAME_TYPE) != 0;

                    if is_same_type {
                        // Homogeneous tuple: read type info once, then read all elements
                        $T0::fory_read_type_info(context)?;

                        let elem0 = read_tuple_element::<$T0>(context, false)?;
                        $(
                            let $T = read_tuple_element::<$T>(context, false)?;
                        )*
                        Ok((elem0, $($T),*))
                    } else {
                        // Heterogeneous tuple: read each element with its type info
                        let elem0 = $T0::fory_read(context, false, true)?;
                        $(
                            let $T = $T::fory_read(context, false, true)?;
                        )*
                        Ok((elem0, $($T),*))
                    }
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

// Implement Serializer for tuples of size 1-22
impl_tuple_serializer!(T0; 0);
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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_homogeneous_tuples() {
        // Single element is always homogeneous
        assert!(<(i32,) as TupleTypeTraits>::is_homogeneous());
        assert_eq!(<(i32,) as TupleTypeTraits>::tuple_len(), 1);

        // All same types
        assert!(<(i32, i32) as TupleTypeTraits>::is_homogeneous());
        assert_eq!(<(i32, i32) as TupleTypeTraits>::tuple_len(), 2);

        assert!(<(i32, i32, i32) as TupleTypeTraits>::is_homogeneous());
        assert_eq!(<(i32, i32, i32) as TupleTypeTraits>::tuple_len(), 3);

        assert!(<(String, String, String, String) as TupleTypeTraits>::is_homogeneous());
        assert_eq!(
            <(String, String, String, String) as TupleTypeTraits>::tuple_len(),
            4
        );
    }

    #[test]
    fn test_heterogeneous_tuples() {
        // Different types
        assert!(!<(i32, i64) as TupleTypeTraits>::is_homogeneous());
        assert_eq!(<(i32, i64) as TupleTypeTraits>::tuple_len(), 2);

        assert!(!<(i32, String) as TupleTypeTraits>::is_homogeneous());
        assert_eq!(<(i32, String) as TupleTypeTraits>::tuple_len(), 2);

        assert!(!<(i32, i32, String) as TupleTypeTraits>::is_homogeneous());
        assert_eq!(<(i32, i32, String) as TupleTypeTraits>::tuple_len(), 3);

        assert!(!<(i32, i64, String, bool) as TupleTypeTraits>::is_homogeneous());
        assert_eq!(
            <(i32, i64, String, bool) as TupleTypeTraits>::tuple_len(),
            4
        );
    }

    #[test]
    fn test_larger_tuples() {
        // Test 8-element tuple
        assert!(<(i32, i32, i32, i32, i32, i32, i32, i32) as TupleTypeTraits>::is_homogeneous());
        assert_eq!(
            <(i32, i32, i32, i32, i32, i32, i32, i32) as TupleTypeTraits>::tuple_len(),
            8
        );

        assert!(
            !<(i32, i32, i32, i32, i32, i32, i32, String) as TupleTypeTraits>::is_homogeneous()
        );

        // Test 12-element tuple
        assert!(
            <(i32, i32, i32, i32, i32, i32, i32, i32, i32, i32, i32, i32) as TupleTypeTraits>::is_homogeneous()
        );
        assert_eq!(
            <(i32, i32, i32, i32, i32, i32, i32, i32, i32, i32, i32, i32) as TupleTypeTraits>::tuple_len(),
            12
        );
        // Test 22-element tuple (homogeneous)
        assert!(<(
            i32,
            i32,
            i32,
            i32,
            i32,
            i32,
            i32,
            i32,
            i32,
            i32,
            i32,
            i32,
            i32,
            i32,
            i32,
            i32,
            i32,
            i32,
            i32,
            i32,
            i32,
            i32
        ) as TupleTypeTraits>::is_homogeneous());
        // Test 22-element tuple (heterogeneous)
        assert!(!<(
            i32,
            i32,
            i32,
            i32,
            i32,
            i32,
            i32,
            i32,
            i32,
            i32,
            i32,
            i32,
            i32,
            i32,
            i32,
            i32,
            i32,
            i32,
            i32,
            i32,
            i32,
            String
        ) as TupleTypeTraits>::is_homogeneous());
        assert_eq!(
            <(
                i32,
                i32,
                i32,
                i32,
                i32,
                i32,
                i32,
                i32,
                i32,
                i32,
                i32,
                i32,
                i32,
                i32,
                i32,
                i32,
                i32,
                i32,
                i32,
                i32,
                i32,
                String
            ) as TupleTypeTraits>::tuple_len(),
            22
        );
    }
}
