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
//   Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

use fory_core::fory::Fory;
use std::rc::Rc;

// Test homogeneous tuples with primitive types
#[test]
fn test_homogeneous_tuple_i32() {
    let fory = Fory::default();
    let tuple = (1i32, 2i32, 3i32);
    let bin = fory.serialize(&tuple).unwrap();
    let obj: (i32, i32, i32) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(tuple, obj);
}

#[test]
fn test_homogeneous_tuple_f64() {
    let fory = Fory::default();
    let tuple = (1.5f64, 2.5f64, 3.5f64, 4.5f64);
    let bin = fory.serialize(&tuple).unwrap();
    let obj: (f64, f64, f64, f64) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(tuple, obj);
}

#[test]
fn test_homogeneous_tuple_string() {
    let fory = Fory::default();
    let tuple = ("hello".to_string(), "world".to_string(), "fory".to_string());
    let bin = fory.serialize(&tuple).unwrap();
    let obj: (String, String, String) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(tuple, obj);
}

// Test heterogeneous tuples with different types
#[test]
fn test_heterogeneous_tuple_simple() {
    let fory = Fory::default();
    let tuple = (42i32, "hello".to_string());
    let bin = fory.serialize(&tuple).unwrap();
    let obj: (i32, String) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(tuple, obj);
}

#[test]
fn test_heterogeneous_tuple_complex() {
    let fory = Fory::default();
    let tuple = (42i32, "hello".to_string(), 3.14f64, true, vec![1, 2, 3]);
    let bin = fory.serialize(&tuple).unwrap();
    let obj: (i32, String, f64, bool, Vec<i32>) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(tuple, obj);
}

// Test single element tuple
#[test]
fn test_single_element_tuple() {
    let fory = Fory::default();
    let tuple = (42i32,);
    let bin = fory.serialize(&tuple).unwrap();
    let obj: (i32,) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(tuple, obj);
}

// Test tuples with Option types
#[test]
fn test_tuple_with_options() {
    let fory = Fory::default();
    let tuple = (Some(42i32), None::<i32>, Some(100i32));
    let bin = fory.serialize(&tuple).unwrap();
    let obj: (Option<i32>, Option<i32>, Option<i32>) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(tuple, obj);
}

#[test]
fn test_heterogeneous_tuple_with_options() {
    let fory = Fory::default();
    let tuple = (Some(42i32), "hello".to_string(), None::<String>);
    let bin = fory.serialize(&tuple).unwrap();
    let obj: (Option<i32>, String, Option<String>) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(tuple, obj);
}

// Test tuples with collections
#[test]
fn test_tuple_with_vectors() {
    let fory = Fory::default();
    let tuple = (vec![1, 2, 3], vec![4, 5, 6]);
    let bin = fory.serialize(&tuple).unwrap();
    let obj: (Vec<i32>, Vec<i32>) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(tuple, obj);
}

#[test]
fn test_tuple_with_mixed_collections() {
    let fory = Fory::default();
    let tuple = (vec![1, 2, 3], vec!["a".to_string(), "b".to_string()]);
    let bin = fory.serialize(&tuple).unwrap();
    let obj: (Vec<i32>, Vec<String>) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(tuple, obj);
}

// Test nested tuples
#[test]
fn test_nested_tuples() {
    let fory = Fory::default();
    let tuple = ((1i32, 2i32), (3i32, 4i32));
    let bin = fory.serialize(&tuple).unwrap();
    let obj: ((i32, i32), (i32, i32)) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(tuple, obj);
}

#[test]
fn test_deeply_nested_tuples() {
    let fory = Fory::default();
    let tuple = (1i32, (2i32, (3i32, 4i32)));
    let bin = fory.serialize(&tuple).unwrap();
    let obj: (i32, (i32, (i32, i32))) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(tuple, obj);
}

// Test large tuples (up to 22 elements)
#[test]
fn test_large_homogeneous_tuple() {
    let fory = Fory::default();
    let tuple = (
        1i32, 2i32, 3i32, 4i32, 5i32, 6i32, 7i32, 8i32, 9i32, 10i32, 11i32, 12i32,
    );
    let bin = fory.serialize(&tuple).unwrap();
    let obj: (i32, i32, i32, i32, i32, i32, i32, i32, i32, i32, i32, i32) =
        fory.deserialize(&bin).expect("deserialize");
    assert_eq!(tuple, obj);
}

#[test]
fn test_large_heterogeneous_tuple() {
    let fory = Fory::default();
    let tuple = (
        1i32,
        2i64,
        3u32,
        4u64,
        5.0f32,
        6.0f64,
        "seven".to_string(),
        true,
    );
    let bin = fory.serialize(&tuple).unwrap();
    let obj: (i32, i64, u32, u64, f32, f64, String, bool) =
        fory.deserialize(&bin).expect("deserialize");
    assert_eq!(tuple, obj);
}

// Test tuples with Rc/Arc (shared references)
#[test]
fn test_tuple_with_rc() {
    let fory = Fory::default();
    let value = Rc::new(42i32);
    let tuple = (Rc::clone(&value), Rc::clone(&value));
    let bin = fory.serialize(&tuple).unwrap();
    let obj: (Rc<i32>, Rc<i32>) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(*obj.0, 42);
    assert_eq!(*obj.1, 42);
    // Note: deserialization creates independent Rc instances, not shared ones
}

// Test tuples in compatible mode
#[test]
fn test_tuple_compatible_mode() {
    let fory = Fory::default().compatible(true);
    let tuple = (1i32, 2i32, 3i32);
    let bin = fory.serialize(&tuple).unwrap();
    let obj: (i32, i32, i32) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(tuple, obj);
}

#[test]
fn test_heterogeneous_tuple_compatible_mode() {
    let fory = Fory::default().compatible(true);
    let tuple = (42i32, "hello".to_string(), 3.14f64);
    let bin = fory.serialize(&tuple).unwrap();
    let obj: (i32, String, f64) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(tuple, obj);
}

// Test tuples with bool
#[test]
fn test_homogeneous_tuple_bool() {
    let fory = Fory::default();
    let tuple = (true, false, true, false);
    let bin = fory.serialize(&tuple).unwrap();
    let obj: (bool, bool, bool, bool) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(tuple, obj);
}

// Test tuples with u8, u16, u32, u64
#[test]
fn test_homogeneous_tuple_unsigned() {
    let fory = Fory::default();
    let tuple_u8 = (1u8, 2u8, 3u8);
    let bin = fory.serialize(&tuple_u8).unwrap();
    let obj: (u8, u8, u8) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(tuple_u8, obj);

    let tuple_u16 = (100u16, 200u16, 300u16);
    let bin = fory.serialize(&tuple_u16).unwrap();
    let obj: (u16, u16, u16) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(tuple_u16, obj);

    let tuple_u32 = (1000u32, 2000u32, 3000u32);
    let bin = fory.serialize(&tuple_u32).unwrap();
    let obj: (u32, u32, u32) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(tuple_u32, obj);

    let tuple_u64 = (10000u64, 20000u64, 30000u64);
    let bin = fory.serialize(&tuple_u64).unwrap();
    let obj: (u64, u64, u64) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(tuple_u64, obj);
}

// Test that tuples are serialized with LIST type ID
#[test]
fn test_tuple_type_id() {
    use fory_core::serializer::Serializer;
    use fory_core::types::TypeId;
    assert_eq!(<(i32, i32)>::fory_static_type_id(), TypeId::LIST);
    assert_eq!(<(i32, String)>::fory_static_type_id(), TypeId::LIST);
    assert_eq!(<(i32,)>::fory_static_type_id(), TypeId::LIST);
}
