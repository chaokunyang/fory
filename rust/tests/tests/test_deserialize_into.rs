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

use fory_core::fory::Fory;
use fory_derive::Fory;
use std::collections::HashMap;

#[test]
fn test_deserialize_into_basic_types() {
    let fory = Fory::default();

    // Test with String
    let original_string = "Hello, World!".to_string();
    let bin = fory.serialize(&original_string);
    let mut output_string = String::new();
    fory.deserialize_into(&bin, &mut output_string)
        .expect("should work");
    assert_eq!(original_string, output_string);

    // Test with i32
    let original_i32 = 42i32;
    let bin = fory.serialize(&original_i32);
    let mut output_i32 = 0i32;
    fory.deserialize_into(&bin, &mut output_i32)
        .expect("should work");
    assert_eq!(original_i32, output_i32);

    // Test with f64
    let original_f64 = std::f64::consts::PI;
    let bin = fory.serialize(&original_f64);
    let mut output_f64 = 0.0f64;
    fory.deserialize_into(&bin, &mut output_f64)
        .expect("should work");
    assert_eq!(original_f64, output_f64);
}

#[test]
fn test_deserialize_into_vec() {
    let fory = Fory::default();

    // Test with Vec
    let original_vec = vec![1, 2, 3, 4, 5];
    let bin = fory.serialize(&original_vec);
    let mut output_vec = Vec::new();
    fory.deserialize_into(&bin, &mut output_vec)
        .expect("should work");
    assert_eq!(original_vec, output_vec);
}

#[test]
fn test_deserialize_into_hashmap() {
    let fory = Fory::default();

    // Test with HashMap
    let mut original_map = HashMap::new();
    original_map.insert("key1".to_string(), "value1".to_string());
    original_map.insert("key2".to_string(), "value2".to_string());
    let bin = fory.serialize(&original_map);
    let mut output_map = HashMap::new();
    fory.deserialize_into(&bin, &mut output_map)
        .expect("should work");
    assert_eq!(original_map, output_map);
}

#[test]
fn test_deserialize_into_option() {
    let fory = Fory::default();

    // Test with Some
    let original_option = Some("test".to_string());
    let bin = fory.serialize(&original_option);
    let mut output_option = None;
    fory.deserialize_into(&bin, &mut output_option)
        .expect("should work");
    assert_eq!(original_option, output_option);

    // Test with None
    let original_none: Option<String> = None;
    let bin = fory.serialize(&original_none);
    let mut output_none = Some("should be cleared".to_string());
    fory.deserialize_into(&bin, &mut output_none)
        .expect("should work");
    assert_eq!(original_none, output_none);
}

#[test]
fn test_deserialize_into_struct() {
    #[derive(Fory, Debug, PartialEq, Default)]
    struct Person {
        name: String,
        age: i32,
        scores: Vec<f64>,
        metadata: HashMap<String, String>,
    }

    let mut fory = Fory::default();
    fory.register::<Person>(999);

    let original_person = Person {
        name: "Alice".to_string(),
        age: 30,
        scores: vec![95.5, 87.2, 92.1],
        metadata: HashMap::from([
            ("city".to_string(), "New York".to_string()),
            ("country".to_string(), "USA".to_string()),
        ]),
    };

    let bin = fory.serialize(&original_person);
    let mut output_person = Person::default();
    fory.deserialize_into(&bin, &mut output_person)
        .expect("should work");
    assert_eq!(original_person, output_person);
}

#[test]
fn test_deserialize_into_enum() {
    #[derive(Fory, Debug, PartialEq)]
    enum Color {
        Red,
        Green,
        Blue,
    }

    let mut fory = Fory::default();
    fory.register::<Color>(888);

    // Test with Red
    let original_color = Color::Red;
    let bin = fory.serialize(&original_color);
    let mut output_color = Color::Blue; // Start with different variant
    fory.deserialize_into(&bin, &mut output_color)
        .expect("should work");
    assert_eq!(original_color, output_color);

    // Test with Green
    let original_color = Color::Green;
    let bin = fory.serialize(&original_color);
    let mut output_color = Color::Red; // Start with different variant
    fory.deserialize_into(&bin, &mut output_color)
        .expect("should work");
    assert_eq!(original_color, output_color);

    // Test with Blue
    let original_color = Color::Blue;
    let bin = fory.serialize(&original_color);
    let mut output_color = Color::Green; // Start with different variant
    fory.deserialize_into(&bin, &mut output_color)
        .expect("should work");
    assert_eq!(original_color, output_color);
}

#[test]
fn test_deserialize_into_complex_struct_with_enum() {
    #[derive(Fory, Debug, PartialEq, Default)]
    enum Status {
        Active,
        Inactive,
        #[default]
        Pending,
    }

    #[derive(Fory, Debug, PartialEq, Default)]
    struct User {
        id: i32,
        name: String,
        status: Status,
        tags: Vec<String>,
        settings: HashMap<String, String>,
    }

    let mut fory = Fory::default();
    fory.register::<Status>(777);
    fory.register::<User>(666);

    let original_user = User {
        id: 12345,
        name: "John Doe".to_string(),
        status: Status::Active,
        tags: vec!["admin".to_string(), "verified".to_string()],
        settings: HashMap::from([
            ("theme".to_string(), "dark".to_string()),
            ("language".to_string(), "en".to_string()),
        ]),
    };

    let bin = fory.serialize(&original_user);
    let mut output_user = User::default();
    fory.deserialize_into(&bin, &mut output_user)
        .expect("should work");
    assert_eq!(original_user, output_user);
}

#[test]
fn test_deserialize_into_reuse_allocation() {
    let fory = Fory::default();

    // Test that we can reuse the same allocation
    let original_vec = vec![
        "item1".to_string(),
        "item2".to_string(),
        "item3".to_string(),
    ];
    let bin = fory.serialize(&original_vec);

    let mut output_vec: Vec<String> = Vec::new();

    // First deserialization
    fory.deserialize_into(&bin, &mut output_vec)
        .expect("should work");
    assert_eq!(original_vec, output_vec);

    // Second deserialization into the same vector (should clear and reuse)
    let original_vec2 = vec!["new1".to_string(), "new2".to_string()];
    let bin2 = fory.serialize(&original_vec2);
    fory.deserialize_into(&bin2, &mut output_vec)
        .expect("should work");
    assert_eq!(original_vec2, output_vec);

    // Verify the vector was cleared and reused
    assert_eq!(output_vec.len(), 2);
    assert_eq!(output_vec[0], "new1");
    assert_eq!(output_vec[1], "new2");
}
