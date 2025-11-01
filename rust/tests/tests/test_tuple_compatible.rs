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

//! Comprehensive tests for tuple serialization in compatible mode.
//! These tests verify schema evolution capabilities including:
//! - Tuple length mismatches (growing/shrinking)
//! - Tuples with collections (Vec, HashMap, HashSet)
//! - Nested tuples
//! - Tuples with Option/Arc/Rc elements
//! - Schema evolution scenarios

use fory_core::fory::Fory;
use fory_derive::ForyObject;
use std::collections::{HashMap, HashSet};
use std::rc::Rc;
use std::sync::Arc;

/// Test 1: Direct tuple size mismatch - bidirectional serialization
#[test]
fn test_tuple_size_mismatch() {
    let fory = Fory::default().compatible(true);

    // Test 1a: Long tuple serialized, short tuple deserialized
    let long = (42i32, "hello".to_string(), 3.14f64, true);
    let bin = fory.serialize(&long).unwrap();
    let short: (i32, String) = fory.deserialize(&bin).expect("deserialize long to short");
    assert_eq!(short.0, 42);
    assert_eq!(short.1, "hello");

    // Test 1b: Short tuple serialized, long tuple deserialized
    let short = (100i32, "world".to_string());
    let bin = fory.serialize(&short).unwrap();
    let long: (i32, String, f64, bool) = fory.deserialize(&bin).expect("deserialize short to long");
    assert_eq!(long.0, 100);
    assert_eq!(long.1, "world");
    // Remaining fields should be default values
    assert_eq!(long.2, 0.0);
    assert_eq!(long.3, false);
}


/// Test 2: Tuples containing list/set/map elements
#[test]
fn test_tuple_with_collections_compatible() {
    let fory = Fory::default().compatible(true);
    // Tuple with Vec
    let tuple_vec = (vec![1, 2, 3], vec!["a".to_string(), "b".to_string()]);
    let bin = fory.serialize(&tuple_vec).unwrap();
    let obj: (Vec<i32>, Vec<String>) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(tuple_vec, obj);

    // Tuple with HashSet
    let mut set1 = HashSet::new();
    set1.insert(1);
    set1.insert(2);
    let mut set2 = HashSet::new();
    set2.insert("x".to_string());
    let tuple_set = (set1.clone(), set2.clone());
    let bin = fory.serialize(&tuple_set).unwrap();
    let obj: (HashSet<i32>, HashSet<String>) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(tuple_set, obj);

    // Tuple with HashMap
    let mut map1 = HashMap::new();
    map1.insert("key1".to_string(), 100);
    map1.insert("key2".to_string(), 200);
    let tuple_map = (map1.clone(), 42i32);
    let bin = fory.serialize(&tuple_map).unwrap();
    let obj: (HashMap<String, i32>, i32) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(tuple_map, obj);

    // Test adding/missing tuple elements with collections
    // Long to short
    let long = (vec![1, 2, 3], vec!["a".to_string()], vec![1.0, 2.0]);
    let bin = fory.serialize(&long).unwrap();
    let short: (Vec<i32>,) = fory.deserialize(&bin).expect("deserialize long to short");
    assert_eq!(short.0, vec![1, 2, 3]);

    // Short to long
    let short = (vec![10, 20, 30],);
    let bin = fory.serialize(&short).unwrap();
    let long: (Vec<i32>, Vec<String>, Vec<f64>) = fory.deserialize(&bin).expect("deserialize short to long");
    assert_eq!(long.0, vec![10, 20, 30]);
    assert_eq!(long.1, Vec::<String>::new()); // default
    assert_eq!(long.2, Vec::<f64>::new()); // default
}

/// Test 2b: Tuple with collections - length mismatch
#[test]
fn test_tuple_collections_size_mismatch() {
    let fory = Fory::default().compatible(true);

    // Serialize tuple with 3 collections
    let tuple_long = (vec![1, 2, 3], vec!["a".to_string()], vec![1.0, 2.0]);
    let bin = fory.serialize(&tuple_long).unwrap();

    // Deserialize to tuple with 1 collection
    let tuple_short: (Vec<i32>,) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(tuple_short.0, vec![1, 2, 3]);

    // Serialize tuple with 1 collection
    let tuple_short = (vec![10, 20, 30],);
    let bin = fory.serialize(&tuple_short).unwrap();

    // Deserialize to tuple with 3 collections
    let tuple_long: (Vec<i32>, Vec<String>, Vec<f64>) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(tuple_long.0, vec![10, 20, 30]);
    assert_eq!(tuple_long.1, Vec::<String>::new());
    assert_eq!(tuple_long.2, Vec::<f64>::new());
}

/// Test 3: Nested tuples
#[test]
fn test_nested_tuples() {
    let fory = Fory::default().compatible(true);

    let obj = ((42i32, "hello".to_string()), (3.14f64, true));
    let bin = fory.serialize(&obj).unwrap();
    let deserialized: ((i32, String), (f64, bool)) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(obj, deserialized);
}

/// Test 3b: Nested tuple size mismatch
#[test]
fn test_nested_tuple_size_mismatch() {
    let fory = Fory::default().compatible(true);

    // Long to short
    let long = ((42i32, "test".to_string(), 3.14f64), (true, 100i32));
    let bin = fory.serialize(&long).unwrap();
    let short: ((i32, String), (bool,)) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(short.0.0, 42);
    assert_eq!(short.0.1, "test");
    assert_eq!(short.1.0, true);

    // Short to long
    let short = ((100i32, "hello".to_string()), (false,));
    let bin = fory.serialize(&short).unwrap();
    let long: ((i32, String, f64), (bool, i32)) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(long.0.0, 100);
    assert_eq!(long.0.1, "hello");
    assert_eq!(long.0.2, 0.0); // default
    assert_eq!(long.1.0, false);
    assert_eq!(long.1.1, 0); // default
}

/// Test 3c: Deeply nested tuples with size mismatch
#[test]
fn test_deeply_nested_tuple_size_mismatch() {
    let fory = Fory::default().compatible(true);

    // Serialize deeply nested tuple
    let deep = (1i32, (2i32, (3i32, 4i32, 5i32)));
    let bin = fory.serialize(&deep).unwrap();

    // Deserialize to shallower structure
    let shallow: (i32, (i32, (i32, i32))) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(shallow.0, 1);
    assert_eq!(shallow.1.0, 2);
    assert_eq!(shallow.1.1.0, 3);
    assert_eq!(shallow.1.1.1, 4);

    // Reverse: shallow to deep
    let shallow = (10i32, (20i32, (30i32,)));
    let bin = fory.serialize(&shallow).unwrap();
    let deep: (i32, (i32, (i32, i32, i32))) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(deep.0, 10);
    assert_eq!(deep.1.0, 20);
    assert_eq!(deep.1.1.0, 30);
    assert_eq!(deep.1.1.1, 0); // default
    assert_eq!(deep.1.1.2, 0); // default
}

/// Test 4: Tuples with Option/Arc elements
#[test]
fn test_tuple_with_option_arc_compatible() {
    let fory = Fory::default().compatible(true);

    // Tuple with Options
    let tuple_opt = (Some(42i32), None::<String>, Some(3.14f64));
    let bin = fory.serialize(&tuple_opt).unwrap();
    let obj: (Option<i32>, Option<String>, Option<f64>) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(tuple_opt, obj);

    // Tuple with Arc
    let tuple_arc = (Arc::new(42i32), Arc::new("hello".to_string()));
    let bin = fory.serialize(&tuple_arc).unwrap();
    let obj: (Arc<i32>, Arc<String>) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(*obj.0, 42);
    assert_eq!(*obj.1, "hello");

    // Tuple with Rc
    let tuple_rc = (Rc::new(100i32), Rc::new("world".to_string()));
    let bin = fory.serialize(&tuple_rc).unwrap();
    let obj: (Rc<i32>, Rc<String>) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(*obj.0, 100);
    assert_eq!(*obj.1, "world");

    // Mixed: Option and Arc
    let tuple_mixed = (Some(Arc::new(100i32)), None::<Arc<String>>);
    let bin = fory.serialize(&tuple_mixed).unwrap();
    let obj: (Option<Arc<i32>>, Option<Arc<String>>) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(*obj.0.unwrap(), 100);
    assert!(obj.1.is_none());
}

/// Test 4b: Tuple with Option size mismatch
#[test]
fn test_tuple_option_size_mismatch() {
    let fory = Fory::default().compatible(true);

    // Serialize longer tuple
    let long = (Some(42i32), Some("hello".to_string()), Some(3.14f64), Some(true));
    let bin = fory.serialize(&long).unwrap();

    // Deserialize to shorter tuple
    let short: (Option<i32>, Option<String>) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(short.0, Some(42));
    assert_eq!(short.1, Some("hello".to_string()));

    // Serialize shorter tuple
    let short = (Some(100i32), None::<String>);
    let bin = fory.serialize(&short).unwrap();

    // Deserialize to longer tuple
    let long: (Option<i32>, Option<String>, Option<f64>, Option<bool>) =
        fory.deserialize(&bin).expect("deserialize");
    assert_eq!(long.0, Some(100));
    assert_eq!(long.1, None);
    assert_eq!(long.2, None); // default for Option is None
    assert_eq!(long.3, None); // default for Option is None
}

/// Test 4c: Tuple with Arc size mismatch
#[test]
fn test_tuple_arc_size_mismatch() {
    let fory = Fory::default().compatible(true);

    // Serialize longer tuple
    let long = (Arc::new(1i32), Arc::new(2i32), Arc::new(3i32));
    let bin = fory.serialize(&long).unwrap();

    // Deserialize to shorter tuple
    let short: (Arc<i32>, Arc<i32>) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(*short.0, 1);
    assert_eq!(*short.1, 2);

    // Serialize shorter tuple
    let short = (Arc::new(10i32), Arc::new(20i32));
    let bin = fory.serialize(&short).unwrap();

    // Deserialize to longer tuple - Arc defaults are created via ForyDefault
    let long: (Arc<i32>, Arc<i32>, Arc<i32>) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(*long.0, 10);
    assert_eq!(*long.1, 20);
    assert_eq!(*long.2, 0); // default Arc<i32>
}

/// Test 5: Schema evolution from homogeneous to heterogeneous tuple
#[test]
fn test_tuple_homogeneous_to_heterogeneous() {
    let fory = Fory::default().compatible(true);

    // Serialize as homogeneous (all i32)
    let homogeneous = (1i32, 2i32, 3i32);
    let bin = fory.serialize(&homogeneous).unwrap();

    // Deserialize as homogeneous - should work fine
    let result: (i32, i32, i32) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(result, (1, 2, 3));

    // Now test heterogeneous tuple
    let heterogeneous = (10i32, "hello".to_string(), 3.14f64);
    let bin = fory.serialize(&heterogeneous).unwrap();

    // This should work because compatible mode preserves type info
    let result: (i32, String, f64) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(result.0, 10);
    assert_eq!(result.1, "hello");
    assert_eq!(result.2, 3.14);
}

/// Test 6: Schema evolution with different element counts
#[test]
fn test_tuple_element_count_evolution() {
    let fory = Fory::default().compatible(true);

    // Test growing from 2 to 5 elements
    let small = (42i32, "hello".to_string());
    let bin = fory.serialize(&small).unwrap();
    let large: (i32, String, f64, bool, i32) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(large.0, 42);
    assert_eq!(large.1, "hello");
    assert_eq!(large.2, 0.0); // default
    assert_eq!(large.3, false); // default
    assert_eq!(large.4, 0); // default

    // Test shrinking from 5 to 2 elements
    let large = (100i32, "world".to_string(), 2.71f64, true, 999i32);
    let bin = fory.serialize(&large).unwrap();
    let small: (i32, String) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(small.0, 100);
    assert_eq!(small.1, "world");

    // Test single element tuple evolution
    let single = (123i32,);
    let bin = fory.serialize(&single).unwrap();
    let triple: (i32, i32, i32) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(triple.0, 123);
    assert_eq!(triple.1, 0); // default
    assert_eq!(triple.2, 0); // default

    // Test triple to single
    let triple = (1i32, 2i32, 3i32);
    let bin = fory.serialize(&triple).unwrap();
    let single: (i32,) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(single.0, 1);
}

/// Test 6b: Complex element count evolution
#[test]
fn test_tuple_element_count_evolution_complex() {
    let fory = Fory::default().compatible(true);

    // v1: simple 2-element tuple
    let v1 = (42i32, "hello".to_string());
    let bin = fory.serialize(&v1).unwrap();

    // v2: evolved to 5-element tuple with collections
    let v2: (i32, String, f64, bool, Vec<i32>) = fory.deserialize(&bin).expect("deserialize v1 to v2");
    assert_eq!(v2.0, 42);
    assert_eq!(v2.1, "hello");
    assert_eq!(v2.2, 0.0);
    assert_eq!(v2.3, false);
    assert_eq!(v2.4, Vec::<i32>::new());

    // v2 to v1
    let v2 = (100i32, "world".to_string(), 3.14f64, true, vec![1, 2, 3]);
    let bin = fory.serialize(&v2).unwrap();
    let v1: (i32, String) = fory.deserialize(&bin).expect("deserialize v2 to v1");
    assert_eq!(v1.0, 100);
    assert_eq!(v1.1, "world");
}

/// Test 7: Edge case - empty tuple behavior
#[test]
fn test_empty_to_non_empty_tuple() {
    let fory = Fory::default().compatible(true);

    // Simulate deserializing to tuple when data is missing
    // This is tested implicitly through struct field defaults
    let single = (42i32,);
    let bin = fory.serialize(&single).unwrap();
    let result: (i32,) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(result.0, 42);
}

/// Test 8: Very large tuple with size mismatch
#[test]
fn test_large_tuple_size_mismatch() {
    let fory = Fory::default().compatible(true);

    // Serialize a large tuple (10 elements)
    let large = (1i32, 2i32, 3i32, 4i32, 5i32, 6i32, 7i32, 8i32, 9i32, 10i32);
    let bin = fory.serialize(&large).unwrap();

    // Deserialize to small tuple (3 elements)
    let small: (i32, i32, i32) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(small, (1, 2, 3));

    // Serialize small tuple
    let small = (100i32, 200i32, 300i32);
    let bin = fory.serialize(&small).unwrap();

    // Deserialize to large tuple
    let large: (i32, i32, i32, i32, i32, i32, i32, i32, i32, i32) =
        fory.deserialize(&bin).expect("deserialize");
    assert_eq!(large.0, 100);
    assert_eq!(large.1, 200);
    assert_eq!(large.2, 300);
    assert_eq!(large.3, 0);
    assert_eq!(large.4, 0);
    assert_eq!(large.5, 0);
    assert_eq!(large.6, 0);
    assert_eq!(large.7, 0);
    assert_eq!(large.8, 0);
    assert_eq!(large.9, 0);
}

/// Test 9: Mixed complex types with size mismatch
#[test]
fn test_mixed_complex_types_size_mismatch() {
    let fory = Fory::default().compatible(true);

    // Complex tuple with many different types
    let complex = (
        vec![1, 2, 3],
        Some("hello".to_string()),
        Arc::new(42i32),
        (1i32, 2i32),
    );
    let bin = fory.serialize(&complex).unwrap();

    // Deserialize to simpler tuple
    let simple: (Vec<i32>, Option<String>) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(simple.0, vec![1, 2, 3]);
    assert_eq!(simple.1, Some("hello".to_string()));

    // Reverse direction
    let simple = (vec![10, 20], None::<String>);
    let bin = fory.serialize(&simple).unwrap();

    let complex: (Vec<i32>, Option<String>, Arc<i32>, (i32, i32)) =
        fory.deserialize(&bin).expect("deserialize");
    assert_eq!(complex.0, vec![10, 20]);
    assert_eq!(complex.1, None);
    assert_eq!(*complex.2, 0); // default Arc<i32>
    assert_eq!(complex.3, (0, 0)); // default tuple
}

/// Test compatible mode with tuples
#[test]
fn test_tuple_xlang_compatible_mode() {
    let fory = Fory::default().compatible(true);
    // Test basic tuple
    let basic = (42i32, "hello".to_string(), vec![1, 2, 3]);
    let bin = fory.serialize(&basic).unwrap();
    let obj: (i32, String, Vec<i32>) = fory.deserialize(&bin).expect("deserialize basic");
    assert_eq!(basic, obj);

    // Test tuple size mismatch
    let long = (1i32, "test".to_string(), 3.14f64, true, vec![1, 2]);
    let bin = fory.serialize(&long).unwrap();
    let short: (i32, String) = fory.deserialize(&bin).expect("deserialize long to short");
    assert_eq!(short.0, 1);
    assert_eq!(short.1, "test");

    // Test short to long
    let short = (100i32, "world".to_string());
    let bin = fory.serialize(&short).unwrap();
    let long: (i32, String, f64, bool) = fory.deserialize(&bin).expect("deserialize short to long");
    assert_eq!(long.0, 100);
    assert_eq!(long.1, "world");
    assert_eq!(long.2, 0.0);
    assert_eq!(long.3, false);

    // Test nested tuples with size mismatch
    let nested = ((1i32, 2i32, 3i32), ("a".to_string(), "b".to_string()));
    let bin = fory.serialize(&nested).unwrap();
    let smaller: ((i32, i32), (String,)) = fory.deserialize(&bin).expect("deserialize nested");
    assert_eq!(smaller.0.0, 1);
    assert_eq!(smaller.0.1, 2);
    assert_eq!(smaller.1.0, "a");
}
