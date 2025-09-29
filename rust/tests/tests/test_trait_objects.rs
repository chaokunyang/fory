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
use fory_core::serializer::trait_::{ForyTrait, ForyTraitArc, ForyTraitRc};
use fory_core::serializer::Serializer;
use fory_derive::Fory as ForyObject;
use std::any::Any;
use std::rc::Rc;
use std::sync::Arc;

// Define a simple trait for testing
trait MyTrait {
    fn get_value(&self) -> i32;
}

// Concrete implementations
#[derive(ForyObject, Default)]
struct ConcreteA {
    value: i32,
}

impl MyTrait for ConcreteA {
    fn get_value(&self) -> i32 {
        self.value
    }
}

impl ForyTrait for ConcreteA {
    fn as_any(&self) -> &dyn Any {
        self
    }

    fn type_name(&self) -> &'static str {
        "ConcreteA"
    }
}

#[derive(ForyObject, Default)]
struct ConcreteB {
    value: i32,
    name: String,
}

impl MyTrait for ConcreteB {
    fn get_value(&self) -> i32 {
        self.value
    }
}

impl ForyTrait for ConcreteB {
    fn as_any(&self) -> &dyn Any {
        self
    }

    fn type_name(&self) -> &'static str {
        "ConcreteB"
    }
}

// Test struct with normal fields first
#[derive(ForyObject, Default)]
struct SimpleContainer {
    normal_field: i32,
    name: String,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_simple_container_compilation() {
        // This test verifies that basic structs still compile correctly
        let container = SimpleContainer::default();

        // If we get here without panicking, the basic struct compilation worked
        assert_eq!(container.normal_field, 0);
    }

    #[test]
    fn test_box_trait_object_default() {
        let _fory = Fory::default();
        let boxed: Box<dyn ForyTrait> = Box::default();

        // Test that we can create a default trait object
        assert_eq!(boxed.type_name(), "NullTraitObject");
    }

    #[test]
    fn test_arc_trait_object_wrapper() {
        let _fory = Fory::default();
        let arc_wrapper = ForyTraitArc::default();

        // Test that we can create a default Arc wrapper
        assert_eq!(arc_wrapper.0.type_name(), "NullTraitObject");
    }

    #[test]
    fn test_rc_trait_object_wrapper() {
        let _fory = Fory::default();
        let rc_wrapper = ForyTraitRc::default();

        // Test that we can create a default Rc wrapper
        assert_eq!(rc_wrapper.0.type_name(), "NullTraitObject");
    }

    #[test]
    fn test_concrete_type_implements_fory_trait() {
        let concrete_a = ConcreteA { value: 42 };
        let concrete_b = ConcreteB {
            value: 100,
            name: "test".to_string()
        };

        // Test that concrete types implement ForyTrait correctly
        assert_eq!(concrete_a.type_name(), "ConcreteA");
        assert_eq!(concrete_b.type_name(), "ConcreteB");

        // Test downcasting
        assert!(concrete_a.as_any().downcast_ref::<ConcreteA>().is_some());
        assert!(concrete_b.as_any().downcast_ref::<ConcreteB>().is_some());
    }

    #[test]
    fn test_trait_object_serialization_placeholder() {
        // This test verifies that the trait object serialization framework is set up
        // Even though full serialization isn't implemented yet, we can test the structure

        let fory = Fory::default();
        let boxed: Box<dyn ForyTrait> = Box::new(ConcreteA { value: 42 });

        // Test type ID
        assert_eq!(Box::<dyn ForyTrait>::get_type_id(&fory), fory_core::types::TypeId::UNKNOWN as u32);

        // Test type name extraction
        assert_eq!(boxed.type_name(), "ConcreteA");
    }
}