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
