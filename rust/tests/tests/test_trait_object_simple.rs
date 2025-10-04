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
use fory_core::register_trait_type;
use fory_core::types::Mode;
use fory_derive::fory_trait;
use fory_derive::Fory;
use std::rc::Rc;
use std::sync::Arc;

fn fory_compatible() -> Fory {
    Fory::default().mode(Mode::Compatible)
}

#[fory_trait]
trait Animal {
    fn speak(&self) -> String;
    fn name(&self) -> &str;
}

#[derive(Fory, Debug, Clone, PartialEq)]
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

#[derive(Fory, Debug, Clone, PartialEq)]
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

register_trait_type!(Animal, Dog, Cat);

#[test]
fn test_box_trait_object_basic() {
    let mut fory = fory_compatible();
    fory.register::<Dog>(8001);
    fory.register::<Cat>(8002);

    let animal: Box<dyn Animal> = Box::new(Dog {
        name: "Rex".to_string(),
        breed: "Golden Retriever".to_string(),
    });

    let serialized = fory.serialize(&animal);
    let deserialized: Box<dyn Animal> = fory.deserialize(&serialized).unwrap();

    assert_eq!(deserialized.name(), "Rex");
    assert_eq!(deserialized.speak(), "Woof!");
}

#[test]
fn test_automatic_rc_wrapper_basic() {
    // Test that wrapper types are automatically generated with trait-specific names
    let dog_rc: Rc<dyn Animal> = Rc::new(Dog {
        name: "Rex".to_string(),
        breed: "Golden Retriever".to_string(),
    });

    // Convert to wrapper
    let wrapper = AnimalRc::from(dog_rc.clone() as Rc<dyn Animal>);

    // Test wrapper functionality
    assert_eq!(wrapper.as_ref().name(), "Rex");
    assert_eq!(wrapper.as_ref().speak(), "Woof!");

    // Test unwrap method (as suggested by user)
    let unwrapped = wrapper.clone().unwrap();
    assert_eq!(unwrapped.name(), "Rex");
    assert_eq!(unwrapped.speak(), "Woof!");

    // Convert back to Rc<dyn Animal> using From trait
    let back_to_rc = Rc::<dyn Animal>::from(wrapper);
    assert_eq!(back_to_rc.name(), "Rex");
    assert_eq!(back_to_rc.speak(), "Woof!");
}

#[test]
fn test_automatic_arc_wrapper_basic() {
    // Test that Arc wrapper types are automatically generated with trait-specific names
    let cat_arc: Arc<dyn Animal + Send + Sync> = Arc::new(Cat {
        name: "Whiskers".to_string(),
        color: "Orange".to_string(),
    });

    // Convert to wrapper
    let wrapper = AnimalArc::from(cat_arc.clone() as Arc<dyn Animal + Send + Sync>);

    // Test wrapper functionality
    assert_eq!(wrapper.as_ref().name(), "Whiskers");
    assert_eq!(wrapper.as_ref().speak(), "Meow!");

    // Test unwrap method (as suggested by user)
    let unwrapped = wrapper.clone().unwrap();
    assert_eq!(unwrapped.name(), "Whiskers");
    assert_eq!(unwrapped.speak(), "Meow!");

    // Convert back to Arc<dyn Animal> using From trait
    let back_to_arc = Arc::<dyn Animal + Send + Sync>::from(wrapper);
    assert_eq!(back_to_arc.name(), "Whiskers");
    assert_eq!(back_to_arc.speak(), "Meow!");
}

#[test]
fn test_wrapper_polymorphism() {
    // Test that different concrete types work through the wrapper interface
    let dog_wrapper = AnimalRc::from(Rc::new(Dog {
        name: "Buddy".to_string(),
        breed: "Labrador".to_string(),
    }) as Rc<dyn Animal>);

    let cat_wrapper = AnimalRc::from(Rc::new(Cat {
        name: "Mittens".to_string(),
        color: "Gray".to_string(),
    }) as Rc<dyn Animal>);

    // Test that both wrappers work correctly with polymorphism
    assert_eq!(dog_wrapper.as_ref().name(), "Buddy");
    assert_eq!(dog_wrapper.as_ref().speak(), "Woof!");

    assert_eq!(cat_wrapper.as_ref().name(), "Mittens");
    assert_eq!(cat_wrapper.as_ref().speak(), "Meow!");

    // Test conversion back to trait objects
    let dog_back = dog_wrapper.unwrap();
    let cat_back = cat_wrapper.unwrap();

    assert_eq!(dog_back.name(), "Buddy");
    assert_eq!(dog_back.speak(), "Woof!");
    assert_eq!(cat_back.name(), "Mittens");
    assert_eq!(cat_back.speak(), "Meow!");
}

#[test]
fn test_wrapper_default_implementations() {
    // Test that wrapper types have proper Default implementations
    let default_rc = AnimalRc::default();
    // Dog::default() should have empty name
    assert_eq!(default_rc.as_ref().name(), "");

    let default_arc = AnimalArc::default();
    // Dog::default() should have empty name
    assert_eq!(default_arc.as_ref().name(), "");
}

#[test]
fn test_wrapper_debug_formatting() {
    let dog_wrapper = AnimalRc::from(Rc::new(Dog {
        name: "Rex".to_string(),
        breed: "Golden Retriever".to_string(),
    }) as Rc<dyn Animal>);

    let debug_string = format!("{:?}", dog_wrapper);
    println!("Debug string: {}", debug_string);
    // Debug shows memory address, not content - this is expected for trait objects
    assert!(debug_string.contains("Animal")); // Debug should show the trait name
}

// Test automatic wrapper conversion in struct fields
#[derive(Fory)]
struct Zoo {
    name: String,
    featured_animal: Box<dyn Animal>,
}

#[test]
fn test_struct_with_trait_object_fields() {
    let mut fory = fory_compatible();
    fory.register::<Dog>(8001);
    fory.register::<Cat>(8002);
    fory.register::<Zoo>(8003);

    let zoo = Zoo {
        name: "Safari Zoo".to_string(),
        featured_animal: Box::new(Dog {
            name: "Buddy".to_string(),
            breed: "Labrador".to_string(),
        }),
    };

    // Test serialization and deserialization
    let serialized = fory.serialize(&zoo);
    let deserialized: Zoo = fory.deserialize(&serialized).unwrap();

    // Verify all fields are correctly preserved
    assert_eq!(deserialized.name, "Safari Zoo");

    // Test featured_animal (Box<dyn Animal>)
    assert_eq!(deserialized.featured_animal.name(), "Buddy");
    assert_eq!(deserialized.featured_animal.speak(), "Woof!");
}

#[test]
fn test_struct_with_mixed_trait_objects() {
    let mut fory = fory_compatible();
    fory.register::<Dog>(8001);
    fory.register::<Cat>(8002);
    fory.register::<Zoo>(8003);

    // Test with different concrete types for each field
    let zoo = Zoo {
        name: "Mixed Zoo".to_string(),
        featured_animal: Box::new(Cat {
            name: "Shadow".to_string(),
            color: "Black".to_string(),
        }),
    };

    let serialized = fory.serialize(&zoo);
    let deserialized: Zoo = fory.deserialize(&serialized).unwrap();

    assert_eq!(deserialized.name, "Mixed Zoo");

    // Should work through trait interface
    assert_eq!(deserialized.featured_animal.speak(), "Meow!");
    assert_eq!(deserialized.featured_animal.name(), "Shadow");
}