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
use fory_core::serializer::Serializer;
use fory_core::types::Mode;
use fory_core::{fory_trait, register_trait_type};
use fory_derive::Fory;
use std::collections::{HashMap, HashSet};

trait Printable: Serializer {
    #[allow(dead_code)]
    fn print_info(&self);
}

fn fory_compatible() -> Fory {
    Fory::default().mode(Mode::Compatible)
}

fn fory_schema_consistent() -> Fory {
    Fory::default()
}

#[test]
fn test_trait_object_architecture() {
    let _fory = Fory::default();
    let _: Box<dyn Serializer> = Box::new(42i32);
}

#[test]
fn test_trait_coercion() {
    #[derive(Default, Debug, PartialEq)]
    struct Book {
        title: String,
    }

    impl Serializer for Book {
        fn fory_write_data(
            &self,
            context: &mut fory_core::resolver::context::WriteContext,
            _is_field: bool,
        ) {
            self.title.fory_write_data(context, false);
        }
        fn fory_read_data(
            context: &mut fory_core::resolver::context::ReadContext,
            _is_field: bool,
        ) -> Result<Self, fory_core::error::Error> {
            Ok(Book {
                title: String::fory_read_data(context, false)?,
            })
        }
        fn fory_type_id_dyn(&self, _fory: &Fory) -> u32 {
            999
        }
    }

    impl Printable for Book {
        fn print_info(&self) {
            println!("Book: {}", self.title);
        }
    }

    let book = Book {
        title: String::from("Test"),
    };
    let printable: Box<dyn Printable> = Box::new(book);
    let _serializer: Box<dyn Serializer> = printable;
}

#[test]
fn test_i32_roundtrip() {
    let fory = fory_compatible();

    let original = 42i32;
    let trait_obj: Box<dyn Serializer> = Box::new(original);
    let serialized = fory.serialize(&trait_obj);

    let deserialized_trait: Box<dyn Serializer> = fory.deserialize(&serialized).unwrap();
    let deserialized_concrete: i32 = fory.deserialize(&serialized).unwrap();

    assert_eq!(deserialized_concrete, original);
    assert_eq!(fory.serialize(&deserialized_trait), serialized);
}

#[test]
fn test_i64_roundtrip() {
    let fory = fory_compatible();

    let original = -9223372036854775808i64;
    let trait_obj: Box<dyn Serializer> = Box::new(original);
    let serialized = fory.serialize(&trait_obj);

    let deserialized_trait: Box<dyn Serializer> = fory.deserialize(&serialized).unwrap();
    let deserialized_concrete: i64 = fory.deserialize(&serialized).unwrap();

    assert_eq!(deserialized_concrete, original);
    assert_eq!(fory.serialize(&deserialized_trait), serialized);
}

#[test]
fn test_f64_roundtrip() {
    let fory = fory_compatible();

    let original = std::f64::consts::PI;
    let trait_obj: Box<dyn Serializer> = Box::new(original);
    let serialized = fory.serialize(&trait_obj);

    let deserialized_trait: Box<dyn Serializer> = fory.deserialize(&serialized).unwrap();
    let deserialized_concrete: f64 = fory.deserialize(&serialized).unwrap();

    assert_eq!(deserialized_concrete, original);
    assert_eq!(fory.serialize(&deserialized_trait), serialized);
}

#[test]
fn test_bool_roundtrip() {
    let fory = fory_compatible();

    let original = true;
    let trait_obj: Box<dyn Serializer> = Box::new(original);
    let serialized = fory.serialize(&trait_obj);

    let deserialized_trait: Box<dyn Serializer> = fory.deserialize(&serialized).unwrap();
    let deserialized_concrete: bool = fory.deserialize(&serialized).unwrap();

    assert_eq!(deserialized_concrete, original);
    assert_eq!(fory.serialize(&deserialized_trait), serialized);
}

#[test]
fn test_string_roundtrip() {
    let fory = fory_compatible();

    let original = String::from("Hello, Fury!");
    let trait_obj: Box<dyn Serializer> = Box::new(original.clone());
    let serialized = fory.serialize(&trait_obj);

    let deserialized_trait: Box<dyn Serializer> = fory.deserialize(&serialized).unwrap();
    let deserialized_concrete: String = fory.deserialize(&serialized).unwrap();

    assert_eq!(deserialized_concrete, original);
    assert_eq!(fory.serialize(&deserialized_trait), serialized);
}

#[test]
fn test_string_empty_roundtrip() {
    let fory = fory_compatible();

    let original = String::new();
    let trait_obj: Box<dyn Serializer> = Box::new(original.clone());
    let serialized = fory.serialize(&trait_obj);

    let deserialized_trait: Box<dyn Serializer> = fory.deserialize(&serialized).unwrap();
    let deserialized_concrete: String = fory.deserialize(&serialized).unwrap();

    assert_eq!(deserialized_concrete, original);
    assert_eq!(fory.serialize(&deserialized_trait), serialized);
}

#[test]
fn test_string_unicode_roundtrip() {
    let fory = fory_compatible();

    let original = String::from("こんにちは世界 🌍");
    let trait_obj: Box<dyn Serializer> = Box::new(original.clone());
    let serialized = fory.serialize(&trait_obj);

    let deserialized_trait: Box<dyn Serializer> = fory.deserialize(&serialized).unwrap();
    let deserialized_concrete: String = fory.deserialize(&serialized).unwrap();

    assert_eq!(deserialized_concrete, original);
    assert_eq!(fory.serialize(&deserialized_trait), serialized);
}

#[test]
fn test_vec_i32_roundtrip() {
    let fory = fory_compatible();

    let original = vec![1, 2, 3, 4, 5];
    let trait_obj: Box<dyn Serializer> = Box::new(original.clone());
    let serialized = fory.serialize(&trait_obj);

    let deserialized_trait: Box<dyn Serializer> = fory.deserialize(&serialized).unwrap();
    let deserialized_concrete: Vec<i32> = fory.deserialize(&serialized).unwrap();

    assert_eq!(deserialized_concrete, original);
    assert_eq!(fory.serialize(&deserialized_trait), serialized);
}

#[test]
fn test_vec_string_roundtrip() {
    let fory = fory_compatible();

    let original = vec![String::from("a"), String::from("b"), String::from("c")];
    let trait_obj: Box<dyn Serializer> = Box::new(original.clone());
    let serialized = fory.serialize(&trait_obj);

    let deserialized_concrete: Vec<String> = fory.deserialize(&serialized).unwrap();

    assert_eq!(deserialized_concrete, original);
}

#[test]
fn test_vec_empty_roundtrip() {
    let fory = fory_compatible();

    let original: Vec<i32> = Vec::new();
    let trait_obj: Box<dyn Serializer> = Box::new(original.clone());
    let serialized = fory.serialize(&trait_obj);

    let deserialized_trait: Box<dyn Serializer> = fory.deserialize(&serialized).unwrap();
    let deserialized_concrete: Vec<i32> = fory.deserialize(&serialized).unwrap();

    assert_eq!(deserialized_concrete, original);
    assert_eq!(fory.serialize(&deserialized_trait), serialized);
}

#[test]
fn test_option_some_roundtrip() {
    let fory = fory_compatible();

    let original = Some(42);
    let trait_obj: Box<dyn Serializer> = Box::new(original);
    let serialized = fory.serialize(&trait_obj);

    let deserialized_trait: Box<dyn Serializer> = fory.deserialize(&serialized).unwrap();
    let deserialized_concrete: Option<i32> = fory.deserialize(&serialized).unwrap();

    assert_eq!(deserialized_concrete, original);
    assert_eq!(fory.serialize(&deserialized_trait), serialized);
}

#[test]
fn test_hashmap_roundtrip() {
    let mut fory = fory_compatible();
    fory.register_serializer::<HashMap<String, i32>>(1001);

    let mut original = HashMap::new();
    original.insert(String::from("one"), 1);
    original.insert(String::from("two"), 2);
    original.insert(String::from("three"), 3);

    let trait_obj: Box<dyn Serializer> = Box::new(original.clone());
    let serialized = fory.serialize(&trait_obj);

    let deserialized_concrete: HashMap<String, i32> = fory.deserialize(&serialized).unwrap();

    assert_eq!(deserialized_concrete.len(), 3);
    assert_eq!(deserialized_concrete.get("one"), Some(&1));
    assert_eq!(deserialized_concrete.get("two"), Some(&2));
    assert_eq!(deserialized_concrete.get("three"), Some(&3));
}

#[test]
fn test_hashset_roundtrip() {
    let mut fory = fory_compatible();
    fory.register_serializer::<HashSet<i32>>(1002);

    let mut original = HashSet::new();
    original.insert(1);
    original.insert(2);
    original.insert(3);

    let trait_obj: Box<dyn Serializer> = Box::new(original.clone());
    let serialized = fory.serialize(&trait_obj);

    let deserialized_concrete: HashSet<i32> = fory.deserialize(&serialized).unwrap();

    assert_eq!(deserialized_concrete.len(), 3);
    assert!(deserialized_concrete.contains(&1));
    assert!(deserialized_concrete.contains(&2));
    assert!(deserialized_concrete.contains(&3));
}

#[test]
fn test_large_vec_roundtrip() {
    let fory = fory_compatible();

    let original: Vec<i32> = (0..1000).collect();
    let trait_obj: Box<dyn Serializer> = Box::new(original.clone());
    let serialized = fory.serialize(&trait_obj);

    let deserialized_trait: Box<dyn Serializer> = fory.deserialize(&serialized).unwrap();
    let deserialized_concrete: Vec<i32> = fory.deserialize(&serialized).unwrap();

    assert_eq!(deserialized_concrete, original);
    assert_eq!(fory.serialize(&deserialized_trait), serialized);
}

#[test]
fn test_multiple_types_in_sequence() {
    let fory = fory_compatible();

    let original1 = 42i32;
    let original2 = String::from("test");
    let original3 = vec![1, 2, 3];

    let val1: Box<dyn Serializer> = Box::new(original1);
    let val2: Box<dyn Serializer> = Box::new(original2.clone());
    let val3: Box<dyn Serializer> = Box::new(original3.clone());

    let ser1 = fory.serialize(&val1);
    let ser2 = fory.serialize(&val2);
    let ser3 = fory.serialize(&val3);

    let de1_trait: Box<dyn Serializer> = fory.deserialize(&ser1).unwrap();
    let de2_trait: Box<dyn Serializer> = fory.deserialize(&ser2).unwrap();
    let de3_trait: Box<dyn Serializer> = fory.deserialize(&ser3).unwrap();

    let de1_concrete: i32 = fory.deserialize(&ser1).unwrap();
    let de2_concrete: String = fory.deserialize(&ser2).unwrap();
    let de3_concrete: Vec<i32> = fory.deserialize(&ser3).unwrap();

    assert_eq!(de1_concrete, original1);
    assert_eq!(de2_concrete, original2);
    assert_eq!(de3_concrete, original3);

    assert_eq!(ser1, fory.serialize(&de1_trait));
    assert_eq!(ser2, fory.serialize(&de2_trait));
    assert_eq!(ser3, fory.serialize(&de3_trait));
}

#[test]
fn test_schema_consistent_mode() {
    let fory = fory_schema_consistent();

    let original = 42i32;
    let trait_obj: Box<dyn Serializer> = Box::new(original);
    let serialized = fory.serialize(&trait_obj);

    let deserialized_trait: Box<dyn Serializer> = fory.deserialize(&serialized).unwrap();
    let deserialized_concrete: i32 = fory.deserialize(&serialized).unwrap();

    assert_eq!(deserialized_concrete, original);
    assert_eq!(fory.serialize(&deserialized_trait), serialized);
}

#[test]
fn test_vec_of_trait_objects() {
    let mut fory = fory_compatible();
    fory.register_serializer::<Vec<Box<dyn Serializer>>>(3000);

    let vec_of_trait_objects: Vec<Box<dyn Serializer>> = vec![
        Box::new(42i32),
        Box::new(String::from("hello")),
        Box::new(2.71f64),
    ];

    let serialized = fory.serialize(&vec_of_trait_objects);
    let deserialized: Vec<Box<dyn Serializer>> = fory.deserialize(&serialized).unwrap();

    assert_eq!(deserialized.len(), 3);
}

#[test]
fn test_hashmap_string_to_trait_objects() {
    let mut fory = fory_compatible();
    fory.register_serializer::<HashMap<String, Box<dyn Serializer>>>(3002);

    let mut map: HashMap<String, Box<dyn Serializer>> = HashMap::new();
    map.insert(String::from("int"), Box::new(42i32));
    map.insert(String::from("string"), Box::new(String::from("hello")));
    map.insert(String::from("float"), Box::new(2.71f64));

    let serialized = fory.serialize(&map);
    let deserialized: HashMap<String, Box<dyn Serializer>> = fory.deserialize(&serialized).unwrap();

    assert_eq!(deserialized.len(), 3);
}

#[test]
fn test_nested_vec() {
    let mut fory = fory_compatible();
    fory.register_serializer::<Vec<Vec<i32>>>(2000);

    let original = vec![vec![1, 2], vec![3, 4, 5]];
    let trait_obj: Box<dyn Serializer> = Box::new(original.clone());
    let serialized = fory.serialize(&trait_obj);

    let deserialized_concrete: Vec<Vec<i32>> = fory.deserialize(&serialized).unwrap();

    assert_eq!(deserialized_concrete, original);
}

#[test]
fn test_vec_option() {
    let mut fory = fory_compatible();
    fory.register_serializer::<Vec<Option<i32>>>(2001);

    let original = vec![Some(1), None, Some(3)];
    let trait_obj: Box<dyn Serializer> = Box::new(original.clone());
    let serialized = fory.serialize(&trait_obj);

    let deserialized_concrete: Vec<Option<i32>> = fory.deserialize(&serialized).unwrap();

    assert_eq!(deserialized_concrete, original);
}

#[derive(Fory, Default, Debug, PartialEq, Clone)]
struct Person {
    name: String,
    age: i32,
}

#[derive(Fory, Default, Debug, PartialEq, Clone)]
struct Company {
    name: String,
    employees: Vec<Person>,
}

#[test]
fn test_fory_derived_struct_as_trait_object() {
    let mut fory = fory_compatible();
    fory.register::<Person>(5000);

    let person = Person {
        name: String::from("Alice"),
        age: 30,
    };
    let trait_obj: Box<dyn Serializer> = Box::new(person.clone());
    let serialized = fory.serialize(&trait_obj);

    let deserialized_concrete: Person = fory.deserialize(&serialized).unwrap();
    assert_eq!(deserialized_concrete, person);
}

#[test]
fn test_fory_derived_nested_struct_as_trait_object() {
    let mut fory = fory_compatible();
    fory.register::<Person>(5000);
    fory.register::<Company>(5001);

    let company = Company {
        name: String::from("Acme Corp"),
        employees: vec![
            Person {
                name: String::from("Alice"),
                age: 30,
            },
            Person {
                name: String::from("Bob"),
                age: 25,
            },
        ],
    };
    let trait_obj: Box<dyn Serializer> = Box::new(company.clone());
    let serialized = fory.serialize(&trait_obj);

    let deserialized_concrete: Company = fory.deserialize(&serialized).unwrap();
    assert_eq!(deserialized_concrete, company);
}

#[test]
fn test_vec_of_fory_derived_trait_objects() {
    let mut fory = fory_compatible();
    fory.register::<Person>(5000);
    fory.register::<Company>(5001);
    fory.register_serializer::<Vec<Box<dyn Serializer>>>(3000);

    let vec_of_trait_objects: Vec<Box<dyn Serializer>> = vec![
        Box::new(Person {
            name: String::from("Alice"),
            age: 30,
        }),
        Box::new(Company {
            name: String::from("Acme"),
            employees: vec![Person {
                name: String::from("Bob"),
                age: 25,
            }],
        }),
        Box::new(42i32),
    ];

    let serialized = fory.serialize(&vec_of_trait_objects);
    let deserialized: Vec<Box<dyn Serializer>> = fory.deserialize(&serialized).unwrap();

    assert_eq!(deserialized.len(), 3);
}

#[test]
fn test_hashmap_with_fory_derived_values() {
    let mut fory = fory_compatible();
    fory.register::<Person>(5000);
    fory.register::<Company>(5001);
    fory.register_serializer::<HashMap<String, Box<dyn Serializer>>>(3002);

    let mut map: HashMap<String, Box<dyn Serializer>> = HashMap::new();
    map.insert(
        String::from("person"),
        Box::new(Person {
            name: String::from("Alice"),
            age: 30,
        }),
    );
    map.insert(
        String::from("company"),
        Box::new(Company {
            name: String::from("Acme"),
            employees: vec![],
        }),
    );
    map.insert(String::from("number"), Box::new(42i32));

    let serialized = fory.serialize(&map);
    let deserialized: HashMap<String, Box<dyn Serializer>> = fory.deserialize(&serialized).unwrap();

    assert_eq!(deserialized.len(), 3);
}

#[test]
fn test_compatible_mode_schema_evolution() {
    let mut fory = fory_compatible();
    fory.register::<Person>(5000);

    let person = Person {
        name: String::from("Alice"),
        age: 30,
    };
    let trait_obj: Box<dyn Serializer> = Box::new(person.clone());
    let serialized = fory.serialize(&trait_obj);

    let deserialized_trait: Box<dyn Serializer> = fory.deserialize(&serialized).unwrap();
    let deserialized_concrete: Person = fory.deserialize(&serialized).unwrap();

    assert_eq!(deserialized_concrete.name, person.name);
    assert_eq!(deserialized_concrete.age, person.age);

    let reserialized = fory.serialize(&deserialized_trait);
    assert_eq!(serialized, reserialized);
}

#[test]
fn test_schema_consistent_mode_for_comparison() {
    let mut fory = fory_schema_consistent();
    fory.register::<Person>(5000);

    let person = Person {
        name: String::from("Bob"),
        age: 25,
    };
    let trait_obj: Box<dyn Serializer> = Box::new(person.clone());
    let serialized = fory.serialize(&trait_obj);

    let deserialized_concrete: Person = fory.deserialize(&serialized).unwrap();
    assert_eq!(deserialized_concrete, person);
}

#[test]
fn test_compatible_mode_with_multiple_same_type_structs() {
    let mut fory = fory_compatible();
    fory.register::<Person>(5000);
    fory.register_serializer::<Vec<Box<dyn Serializer>>>(3000);

    let vec_of_people: Vec<Box<dyn Serializer>> = vec![
        Box::new(Person {
            name: String::from("Alice"),
            age: 30,
        }),
        Box::new(Person {
            name: String::from("Bob"),
            age: 25,
        }),
        Box::new(Person {
            name: String::from("Charlie"),
            age: 35,
        }),
    ];

    let serialized = fory.serialize(&vec_of_people);
    let deserialized: Vec<Box<dyn Serializer>> = fory.deserialize(&serialized).unwrap();

    assert_eq!(deserialized.len(), 3);
}

// TODO: This test manually implements StructSerializer, which is wrong.
// It should use #[derive(Fory)] instead. Commenting out for now.
/*
#[test]
fn test_trait_object_as_struct_field() {
    #[derive(Default)]
    struct Container {
        value: Box<dyn Serializer>,
    }

    let mut fory = fory_compatible();
    fory.register::<Container>(6000);

    let container = Container {
        value: Box::new(42i32),
    };

    let serialized = fory.serialize(&container);
    let deserialized: Container = fory.deserialize(&serialized).unwrap();

    let original_val: i32 = fory.deserialize(&fory.serialize(&container.value)).unwrap();
    let deserialized_val: i32 = fory.deserialize(&fory.serialize(&deserialized.value)).unwrap();
    assert_eq!(original_val, deserialized_val);
}
*/

#[derive(Fory, Default, Debug, PartialEq, Clone)]
struct SetContainer {
    values: HashSet<i32>,
}

#[test]
fn test_set_as_field() {
    let mut fory = fory_compatible();
    fory.register::<SetContainer>(6005);

    let mut values = HashSet::new();
    values.insert(1);
    values.insert(2);
    values.insert(3);

    let container = SetContainer { values };

    let serialized = fory.serialize(&container);
    let deserialized: SetContainer = fory.deserialize(&serialized).unwrap();

    assert_eq!(deserialized.values.len(), 3);
    assert!(deserialized.values.contains(&1));
    assert!(deserialized.values.contains(&2));
    assert!(deserialized.values.contains(&3));
}

// Tests for custom trait objects (Box<dyn CustomTrait>)

fory_trait! {
    trait Animal {
        fn speak(&self) -> String;
        fn name(&self) -> &str;
    }
}

#[derive(Fory, Default, Debug, Clone, PartialEq)]
struct Dog {
    name: String,
    breed: String,
}

impl Animal for Dog {
    fn speak(&self) -> String {
        "Woof!".to_string()
    }

    fn name(&self) -> &str {
        &self.name
    }

    fn as_any(&self) -> &dyn std::any::Any {
        self
    }
}

#[derive(Fory, Default, Debug, Clone, PartialEq)]
struct Cat {
    name: String,
    color: String,
}

impl Animal for Cat {
    fn speak(&self) -> String {
        "Meow!".to_string()
    }

    fn name(&self) -> &str {
        &self.name
    }

    fn as_any(&self) -> &dyn std::any::Any {
        self
    }
}

register_trait_type!(Animal, (Dog, 8001), (Cat, 8002));

#[derive(Fory)]
struct Zoo {
    star_animal: Box<dyn Animal>,
}

impl Default for Zoo {
    fn default() -> Self {
        Zoo {
            star_animal: Box::new(Dog::default()),
        }
    }
}

#[test]
fn test_custom_trait_object_basic() {
    let mut fory = fory_compatible();
    fory.register::<Dog>(8001);
    fory.register::<Cat>(8002);
    fory.register::<Zoo>(8003);

    let zoo = Zoo {
        star_animal: Box::new(Dog {
            name: "Rex".to_string(),
            breed: "Golden Retriever".to_string(),
        }),
    };

    let serialized = fory.serialize(&zoo);
    let deserialized: Zoo = fory.deserialize(&serialized).unwrap();

    assert_eq!(deserialized.star_animal.name(), "Rex");
    assert_eq!(deserialized.star_animal.speak(), "Woof!");
}

#[test]
fn test_custom_trait_object_different_types() {
    let mut fory = fory_compatible();
    fory.register::<Dog>(8001);
    fory.register::<Cat>(8002);
    fory.register::<Zoo>(8003);

    let zoo_dog = Zoo {
        star_animal: Box::new(Dog {
            name: "Buddy".to_string(),
            breed: "Labrador".to_string(),
        }),
    };

    let zoo_cat = Zoo {
        star_animal: Box::new(Cat {
            name: "Whiskers".to_string(),
            color: "Orange".to_string(),
        }),
    };

    let serialized_dog = fory.serialize(&zoo_dog);
    let serialized_cat = fory.serialize(&zoo_cat);

    let deserialized_dog: Zoo = fory.deserialize(&serialized_dog).unwrap();
    let deserialized_cat: Zoo = fory.deserialize(&serialized_cat).unwrap();

    assert_eq!(deserialized_dog.star_animal.name(), "Buddy");
    assert_eq!(deserialized_dog.star_animal.speak(), "Woof!");

    assert_eq!(deserialized_cat.star_animal.name(), "Whiskers");
    assert_eq!(deserialized_cat.star_animal.speak(), "Meow!");
}
