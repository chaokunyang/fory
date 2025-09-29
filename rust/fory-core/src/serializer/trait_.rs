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
use crate::fory::Fory;
use crate::resolver::context::{ReadContext, WriteContext};
use crate::serializer::Serializer;
use crate::types::TypeId;
use anyhow::anyhow;
use std::any::Any;
use std::rc::Rc;
use std::sync::Arc;

/// Trait that must be implemented by types that can be used as trait objects in Fory serialization.
/// This trait provides the ability to downcast to the concrete Serializer type.
pub trait ForyTrait: Any + Send + Sync {
    /// Downcasts to Any for concrete type access
    fn as_any(&self) -> &dyn Any;

    /// Returns the concrete type name for registration and deserialization
    fn type_name(&self) -> &'static str;

    /// Creates a default instance for the null case
    fn default_instance() -> Box<dyn ForyTrait> where Self: Sized {
        Box::new(NullTraitObject)
    }
}

/// Helper function to serialize trait objects by trying registered types
fn serialize_trait_object(
    trait_obj: &dyn ForyTrait,
    context: &mut WriteContext,
    _is_field: bool,
) -> Result<(), Error> {
    let type_name = trait_obj.type_name();

    // Write the type name for deserialization
    context.writer.var_uint32(type_name.len() as u32);
    context.writer.bytes(type_name.as_bytes());

    // Try to find and serialize the concrete type through the type resolver
    // For now, this is a placeholder that would need actual registry implementation
    Err(Error::from(anyhow!("Trait object serialization requires type registry implementation for type: {}", type_name)))
}

/// Helper function to deserialize trait objects by type name
fn deserialize_trait_object(
    context: &mut ReadContext,
    _is_field: bool,
) -> Result<Box<dyn ForyTrait>, Error> {
    // Read the type name
    let type_name_len = context.reader.var_uint32() as usize;
    let type_name_bytes = context.reader.bytes(type_name_len);
    let type_name = std::str::from_utf8(type_name_bytes)
        .map_err(|e| Error::from(anyhow!("Invalid type name UTF-8: {}", e)))?;

    // Deserialize using registered deserializer
    // For now, this is a placeholder that would need actual registry implementation
    Err(Error::from(anyhow!("Trait object deserialization requires type registry implementation for type: {}", type_name)))
}

// Box<dyn ForyTrait> implementation
impl Serializer for Box<dyn ForyTrait> {
    fn reserved_space() -> usize {
        64 // Reserve space for type info + typical object size
    }

    fn write(&self, context: &mut WriteContext, is_field: bool) {
        serialize_trait_object(self.as_ref(), context, is_field)
            .expect("Failed to serialize trait object");
    }

    fn write_type_info(context: &mut WriteContext, _is_field: bool) {
        // Write UNKNOWN type since this is a trait object field
        context.writer.var_uint32(TypeId::UNKNOWN as u32);
    }

    fn read(context: &mut ReadContext) -> Result<Self, Error> {
        deserialize_trait_object(context, false)
    }

    fn read_type_info(context: &mut ReadContext, _is_field: bool) {
        let type_id = context.reader.var_uint32();
        assert_eq!(type_id, TypeId::UNKNOWN as u32, "Expected UNKNOWN type for trait object");
    }

    fn get_type_id(_fory: &Fory) -> u32 {
        TypeId::UNKNOWN as u32
    }

    fn is_option() -> bool {
        false
    }

    fn is_none(&self) -> bool {
        false
    }
}

impl Default for Box<dyn ForyTrait> {
    fn default() -> Self {
        Box::new(NullTraitObject)
    }
}

/// Wrapper for Arc<dyn ForyTrait> to work around orphan rules
#[derive(Clone)]
pub struct ForyTraitArc(pub Arc<dyn ForyTrait>);

impl Default for ForyTraitArc {
    fn default() -> Self {
        ForyTraitArc(Arc::new(NullTraitObject))
    }
}

impl Serializer for ForyTraitArc {
    fn reserved_space() -> usize {
        <Box<dyn ForyTrait>>::reserved_space()
    }

    fn write(&self, context: &mut WriteContext, is_field: bool) {
        serialize_trait_object(self.0.as_ref(), context, is_field)
            .expect("Failed to serialize trait object");
    }

    fn write_type_info(context: &mut WriteContext, is_field: bool) {
        <Box<dyn ForyTrait>>::write_type_info(context, is_field);
    }

    fn read(context: &mut ReadContext) -> Result<Self, Error> {
        deserialize_trait_object(context, false).map(|boxed| {
            ForyTraitArc(Arc::from(boxed))
        })
    }

    fn read_type_info(context: &mut ReadContext, is_field: bool) {
        <Box<dyn ForyTrait>>::read_type_info(context, is_field);
    }

    fn get_type_id(fory: &Fory) -> u32 {
        <Box<dyn ForyTrait>>::get_type_id(fory)
    }

    fn is_option() -> bool {
        false
    }

    fn is_none(&self) -> bool {
        false
    }
}

/// Wrapper for Rc<dyn ForyTrait> to work around orphan rules
#[derive(Clone)]
pub struct ForyTraitRc(pub Rc<dyn ForyTrait>);

impl Default for ForyTraitRc {
    fn default() -> Self {
        ForyTraitRc(Rc::new(NullTraitObject))
    }
}

impl Serializer for ForyTraitRc {
    fn reserved_space() -> usize {
        <Box<dyn ForyTrait>>::reserved_space()
    }

    fn write(&self, context: &mut WriteContext, is_field: bool) {
        serialize_trait_object(self.0.as_ref(), context, is_field)
            .expect("Failed to serialize trait object");
    }

    fn write_type_info(context: &mut WriteContext, is_field: bool) {
        <Box<dyn ForyTrait>>::write_type_info(context, is_field);
    }

    fn read(context: &mut ReadContext) -> Result<Self, Error> {
        deserialize_trait_object(context, false).map(|boxed| {
            ForyTraitRc(Rc::from(boxed))
        })
    }

    fn read_type_info(context: &mut ReadContext, is_field: bool) {
        <Box<dyn ForyTrait>>::read_type_info(context, is_field);
    }

    fn get_type_id(fory: &Fory) -> u32 {
        <Box<dyn ForyTrait>>::get_type_id(fory)
    }

    fn is_option() -> bool {
        false
    }

    fn is_none(&self) -> bool {
        false
    }
}


/// A null/empty trait object used for error cases
#[derive(Default)]
struct NullTraitObject;

impl ForyTrait for NullTraitObject {
    fn as_any(&self) -> &dyn Any {
        self
    }

    fn type_name(&self) -> &'static str {
        "NullTraitObject"
    }
}

impl Serializer for NullTraitObject {
    fn reserved_space() -> usize { 0 }

    fn write(&self, _context: &mut WriteContext, _is_field: bool) {}

    fn write_type_info(_context: &mut WriteContext, _is_field: bool) {}

    fn read(_context: &mut ReadContext) -> Result<Self, Error> {
        Ok(NullTraitObject)
    }

    fn read_type_info(_context: &mut ReadContext, _is_field: bool) {}

    fn get_type_id(_fory: &Fory) -> u32 { TypeId::UNKNOWN as u32 }
}