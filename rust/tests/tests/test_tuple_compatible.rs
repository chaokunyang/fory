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
    let long: (Vec<i32>, Vec<String>, Vec<f64>) =
        fory.deserialize(&bin).expect("deserialize short to long");
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
    let tuple_long: (Vec<i32>, Vec<String>, Vec<f64>) =
        fory.deserialize(&bin).expect("deserialize");
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
    assert_eq!(short.0 .0, 42);
    assert_eq!(short.0 .1, "test");
    assert_eq!(short.1 .0, true);

    // Short to long
    let short = ((100i32, "hello".to_string()), (false,));
    let bin = fory.serialize(&short).unwrap();
    let long: ((i32, String, f64), (bool, i32)) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(long.0 .0, 100);
    assert_eq!(long.0 .1, "hello");
    assert_eq!(long.0 .2, 0.0); // default
    assert_eq!(long.1 .0, false);
    assert_eq!(long.1 .1, 0); // default
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
    assert_eq!(shallow.1 .0, 2);
    assert_eq!(shallow.1 .1 .0, 3);
    assert_eq!(shallow.1 .1 .1, 4);

    // Reverse: shallow to deep
    let shallow = (10i32, (20i32, (30i32,)));
    let bin = fory.serialize(&shallow).unwrap();
    let deep: (i32, (i32, (i32, i32, i32))) = fory.deserialize(&bin).expect("deserialize");
    assert_eq!(deep.0, 10);
    assert_eq!(deep.1 .0, 20);
    assert_eq!(deep.1 .1 .0, 30);
    assert_eq!(deep.1 .1 .1, 0); // default
    assert_eq!(deep.1 .1 .2, 0); // default
}

/// Test 4: Tuples with Option/Arc elements
#[test]
fn test_tuple_with_option_arc_compatible() {
    let fory = Fory::default().compatible(true);

    // Tuple with Options
    let tuple_opt = (Some(42i32), None::<String>, Some(3.14f64));
    let bin = fory.serialize(&tuple_opt).unwrap();
    let obj: (Option<i32>, Option<String>, Option<f64>) =
        fory.deserialize(&bin).expect("deserialize");
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
    let long = (
        Some(42i32),
        Some("hello".to_string()),
        Some(3.14f64),
        Some(true),
    );
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
    let v2: (i32, String, f64, bool, Vec<i32>) =
        fory.deserialize(&bin).expect("deserialize v1 to v2");
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
    assert_eq!(smaller.0 .0, 1);
    assert_eq!(smaller.0 .1, 2);
    assert_eq!(smaller.1 .0, "a");
}

// ============================================================================
// Struct-based tests for tuple field compatibility
// ============================================================================

/// Helper: Test struct with missing tuple field
fn run_struct_missing_tuple_field(xlang: bool) {
    // V1: Struct with tuple field
    #[derive(ForyObject, Debug, PartialEq)]
    struct StructV1 {
        id: i32,
        name: String,
        coordinates: (f64, f64),
        metadata: (String, i32),
    }

    // V2: Struct with missing tuple field (coordinates removed)
    #[derive(ForyObject, Debug, PartialEq)]
    struct StructV2 {
        id: i32,
        name: String,
        metadata: (String, i32),
    }

    let mut fory = Fory::default().xlang(xlang).compatible(true);
    fory.register::<StructV1>(10).unwrap();
    fory.register::<StructV2>(11).unwrap();

    // Serialize V1 and deserialize as V2
    let v1 = StructV1 {
        id: 42,
        name: "test".to_string(),
        coordinates: (10.5, 20.3),
        metadata: ("meta".to_string(), 100),
    };
    let bytes = fory.serialize(&v1).unwrap();
    let v2: StructV2 = fory.deserialize(&bytes).expect("deserialize V1 to V2");
    assert_eq!(v2.id, 42);
    assert_eq!(v2.name, "test");
    assert_eq!(v2.metadata, ("meta".to_string(), 100));

    // Serialize V2 and deserialize as V1 (coordinates should be default)
    let v2 = StructV2 {
        id: 99,
        name: "test2".to_string(),
        metadata: ("data".to_string(), 200),
    };
    let bytes = fory.serialize(&v2).unwrap();
    let v1: StructV1 = fory.deserialize(&bytes).expect("deserialize V2 to V1");
    assert_eq!(v1.id, 99);
    assert_eq!(v1.name, "test2");
    assert_eq!(v1.coordinates, (0.0, 0.0)); // default
    assert_eq!(v1.metadata, ("data".to_string(), 200));
}

/// Helper: Test struct with added tuple field
fn run_struct_added_tuple_field(xlang: bool) {
    // V1: Struct without extra tuple field
    #[derive(ForyObject, Debug, PartialEq)]
    struct StructV1 {
        id: i32,
        name: String,
    }

    // V2: Struct with added tuple field
    #[derive(ForyObject, Debug, PartialEq)]
    struct StructV2 {
        id: i32,
        name: String,
        coordinates: (f64, f64),
        tags: (String, String, i32),
    }

    let mut fory = Fory::default().xlang(xlang).compatible(true);
    fory.register::<StructV1>(20).unwrap();
    fory.register::<StructV2>(21).unwrap();

    // Serialize V1 and deserialize as V2 (new tuple fields should be default)
    let v1 = StructV1 {
        id: 42,
        name: "test".to_string(),
    };
    let bytes = fory.serialize(&v1).unwrap();
    let v2: StructV2 = fory.deserialize(&bytes).expect("deserialize V1 to V2");
    assert_eq!(v2.id, 42);
    assert_eq!(v2.name, "test");
    assert_eq!(v2.coordinates, (0.0, 0.0)); // default
    assert_eq!(v2.tags, (String::new(), String::new(), 0)); // default

    // Serialize V2 and deserialize as V1
    let v2 = StructV2 {
        id: 99,
        name: "test2".to_string(),
        coordinates: (1.5, 2.5),
        tags: ("tag1".to_string(), "tag2".to_string(), 123),
    };
    let bytes = fory.serialize(&v2).unwrap();
    let v1: StructV1 = fory.deserialize(&bytes).expect("deserialize V2 to V1");
    assert_eq!(v1.id, 99);
    assert_eq!(v1.name, "test2");
}

/// Helper: Test struct with tuple field element increase
fn run_struct_tuple_element_increase(xlang: bool) {
    // V1: Struct with 2-element tuple
    #[derive(ForyObject, Debug, PartialEq)]
    struct StructV1 {
        id: i32,
        coordinates: (f64, f64),
    }

    // V2: Struct with 4-element tuple (increased from 2)
    #[derive(ForyObject, Debug, PartialEq)]
    struct StructV2 {
        id: i32,
        coordinates: (f64, f64, f64, f64),
    }

    let mut fory = Fory::default().xlang(xlang).compatible(true);
    fory.register::<StructV1>(30).unwrap();
    fory.register::<StructV2>(31).unwrap();

    // Serialize V1 and deserialize as V2 (extra elements should be default)
    let v1 = StructV1 {
        id: 42,
        coordinates: (10.5, 20.3),
    };
    let bytes = fory.serialize(&v1).unwrap();
    let v2: StructV2 = fory.deserialize(&bytes).expect("deserialize V1 to V2");
    assert_eq!(v2.id, 42);
    assert_eq!(v2.coordinates.0, 10.5);
    assert_eq!(v2.coordinates.1, 20.3);
    assert_eq!(v2.coordinates.2, 0.0); // default
    assert_eq!(v2.coordinates.3, 0.0); // default

    // Serialize V2 and deserialize as V1 (extra elements should be truncated)
    let v2 = StructV2 {
        id: 99,
        coordinates: (1.0, 2.0, 3.0, 4.0),
    };
    let bytes = fory.serialize(&v2).unwrap();
    let v1: StructV1 = fory.deserialize(&bytes).expect("deserialize V2 to V1");
    assert_eq!(v1.id, 99);
    assert_eq!(v1.coordinates, (1.0, 2.0));
}

/// Helper: Test struct with tuple field element decrease
fn run_struct_tuple_element_decrease(xlang: bool) {
    // V1: Struct with 5-element tuple
    #[derive(ForyObject, Debug, PartialEq)]
    struct StructV1 {
        id: i32,
        data: (i32, String, f64, bool, Vec<i32>),
    }

    // V2: Struct with 2-element tuple (decreased from 5)
    #[derive(ForyObject, Debug, PartialEq)]
    struct StructV2 {
        id: i32,
        data: (i32, String),
    }

    let mut fory = Fory::default().xlang(xlang).compatible(true);
    fory.register::<StructV1>(40).unwrap();
    fory.register::<StructV2>(41).unwrap();

    // Serialize V1 and deserialize as V2 (extra elements should be dropped)
    let v1 = StructV1 {
        id: 42,
        data: (100, "hello".to_string(), 3.14, true, vec![1, 2, 3]),
    };
    let bytes = fory.serialize(&v1).unwrap();
    let v2: StructV2 = fory.deserialize(&bytes).expect("deserialize V1 to V2");
    assert_eq!(v2.id, 42);
    assert_eq!(v2.data.0, 100);
    assert_eq!(v2.data.1, "hello");

    // Serialize V2 and deserialize as V1 (missing elements should be default)
    let v2 = StructV2 {
        id: 99,
        data: (200, "world".to_string()),
    };
    let bytes = fory.serialize(&v2).unwrap();
    let v1: StructV1 = fory.deserialize(&bytes).expect("deserialize V2 to V1");
    assert_eq!(v1.id, 99);
    assert_eq!(v1.data.0, 200);
    assert_eq!(v1.data.1, "world");
    assert_eq!(v1.data.2, 0.0); // default
    assert_eq!(v1.data.3, false); // default
    assert_eq!(v1.data.4, Vec::<i32>::new()); // default
}

/// Helper: Test struct with complex nested tuple evolution
fn run_struct_nested_tuple_evolution(xlang: bool) {
    // V1: Struct with simple nested tuple
    #[derive(ForyObject, Debug, PartialEq)]
    struct StructV1 {
        id: i32,
        nested: ((i32, String), (f64, bool)),
    }

    // V2: Struct with evolved nested tuple (more elements)
    #[derive(ForyObject, Debug, PartialEq)]
    struct StructV2 {
        id: i32,
        nested: ((i32, String, Vec<i32>), (f64, bool, Option<String>)),
    }

    let mut fory = Fory::default().xlang(xlang).compatible(true);
    fory.register::<StructV1>(50).unwrap();
    fory.register::<StructV2>(51).unwrap();

    // Serialize V1 and deserialize as V2
    let v1 = StructV1 {
        id: 42,
        nested: ((100, "test".to_string()), (3.14, true)),
    };
    let bytes = fory.serialize(&v1).unwrap();
    let v2: StructV2 = fory.deserialize(&bytes).expect("deserialize V1 to V2");
    assert_eq!(v2.id, 42);
    assert_eq!(v2.nested.0 .0, 100);
    assert_eq!(v2.nested.0 .1, "test");
    assert_eq!(v2.nested.0 .2, Vec::<i32>::new()); // default
    assert_eq!(v2.nested.1 .0, 3.14);
    assert_eq!(v2.nested.1 .1, true);
    assert_eq!(v2.nested.1 .2, None); // default

    // Serialize V2 and deserialize as V1
    let v2 = StructV2 {
        id: 99,
        nested: (
            (200, "world".to_string(), vec![1, 2, 3]),
            (2.71, false, Some("extra".to_string())),
        ),
    };
    let bytes = fory.serialize(&v2).unwrap();
    let v1: StructV1 = fory.deserialize(&bytes).expect("deserialize V2 to V1");
    assert_eq!(v1.id, 99);
    assert_eq!(v1.nested.0 .0, 200);
    assert_eq!(v1.nested.0 .1, "world");
    assert_eq!(v1.nested.1 .0, 2.71);
    assert_eq!(v1.nested.1 .1, false);
}

/// Helper: Test struct with multiple tuple fields evolution
fn run_struct_multiple_tuple_fields_evolution(xlang: bool) {
    // V1: Struct with two simple tuple fields
    #[derive(ForyObject, Debug, PartialEq)]
    struct StructV1 {
        id: i32,
        coords: (f64, f64),
        tags: (String, i32),
    }

    // V2: Struct with modified tuple fields (coords expanded, tags reduced, new field added)
    #[derive(ForyObject, Debug, PartialEq)]
    struct StructV2 {
        id: i32,
        coords: (f64, f64, f64),       // 3D coordinates
        tags: (String,),               // reduced to single tag
        metadata: (bool, Vec<String>), // new tuple field
    }

    let mut fory = Fory::default().xlang(xlang).compatible(true);
    fory.register::<StructV1>(60).unwrap();
    fory.register::<StructV2>(61).unwrap();

    // Serialize V1 and deserialize as V2
    let v1 = StructV1 {
        id: 42,
        coords: (1.5, 2.5),
        tags: ("tag1".to_string(), 100),
    };
    let bytes = fory.serialize(&v1).unwrap();
    let v2: StructV2 = fory.deserialize(&bytes).expect("deserialize V1 to V2");
    assert_eq!(v2.id, 42);
    assert_eq!(v2.coords.0, 1.5);
    assert_eq!(v2.coords.1, 2.5);
    assert_eq!(v2.coords.2, 0.0); // default
    assert_eq!(v2.tags.0, "tag1");
    assert_eq!(v2.metadata, (false, Vec::<String>::new())); // default

    // Serialize V2 and deserialize as V1
    let v2 = StructV2 {
        id: 99,
        coords: (10.0, 20.0, 30.0),
        tags: ("newtag".to_string(),),
        metadata: (true, vec!["m1".to_string(), "m2".to_string()]),
    };
    let bytes = fory.serialize(&v2).unwrap();
    let v1: StructV1 = fory.deserialize(&bytes).expect("deserialize V2 to V1");
    assert_eq!(v1.id, 99);
    assert_eq!(v1.coords, (10.0, 20.0));
    assert_eq!(v1.tags.0, "newtag");
    assert_eq!(v1.tags.1, 0); // default
}

// Test functions (non-xlang mode)
#[test]
fn test_struct_missing_tuple_field() {
    run_struct_missing_tuple_field(false);
}

#[test]
fn test_struct_added_tuple_field() {
    run_struct_added_tuple_field(false);
}

#[test]
fn test_struct_tuple_element_increase() {
    run_struct_tuple_element_increase(false);
}

#[test]
fn test_struct_tuple_element_decrease() {
    run_struct_tuple_element_decrease(false);
}

#[test]
fn test_struct_nested_tuple_evolution() {
    run_struct_nested_tuple_evolution(false);
}

#[test]
fn test_struct_multiple_tuple_fields_evolution() {
    run_struct_multiple_tuple_fields_evolution(false);
}

// Test functions (xlang mode)
#[test]
fn test_struct_missing_tuple_field_xlang() {
    run_struct_missing_tuple_field(true);
}

#[test]
fn test_struct_added_tuple_field_xlang() {
    run_struct_added_tuple_field(true);
}

#[test]
fn test_struct_tuple_element_increase_xlang() {
    run_struct_tuple_element_increase(true);
}

#[test]
fn test_struct_tuple_element_decrease_xlang() {
    run_struct_tuple_element_decrease(true);
}

#[test]
fn test_struct_nested_tuple_evolution_xlang() {
    run_struct_nested_tuple_evolution(true);
}

#[test]
fn test_struct_multiple_tuple_fields_evolution_xlang() {
    run_struct_multiple_tuple_fields_evolution(true);
}
