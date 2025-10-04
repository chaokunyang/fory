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

//! # Fory Core
//!
//! This is the core implementation of the Fory serialization framework.
//! It provides the fundamental building blocks for high-performance serialization
//! and deserialization in Rust.
//!
//! ## Architecture
//!
//! The core library is organized into several key modules:
//!
//! - **`fory`**: Main serialization engine and public API
//! - **`buffer`**: Efficient binary buffer management with Reader/Writer
//! - **`row`**: Row-based serialization for zero-copy operations
//! - **`serializer`**: Type-specific serialization implementations
//! - **`resolver`**: Type resolution and metadata management
//! - **`meta`**: Metadata handling for schema evolution
//! - **`types`**: Core type definitions and constants
//! - **`error`**: Error handling and result types
//! - **`util`**: Utility functions and helpers
//!
//! ## Key Concepts
//!
//! ### Serialization Modes
//!
//! Fory supports two serialization modes:
//!
//! - **SchemaConsistent**: Requires exact type matching between peers
//! - **Compatible**: Allows schema evolution with field additions/deletions
//!
//! ### Type System
//!
//! The framework uses a comprehensive type system that supports:
//! - Primitive types (bool, integers, floats, strings)
//! - Collections (Vec, HashMap, BTreeMap)
//! - Optional types (`Option<T>`)
//! - Date/time types (chrono integration)
//! - Custom structs and enums
//!
//! ### Performance Optimizations
//!
//! - **Zero-copy deserialization** in row mode
//! - **Buffer pre-allocation** to minimize allocations
//! - **Variable-length encoding** for compact representation
//! - **Little-endian byte order** for cross-platform compatibility
//!
//! ## Usage
//!
//! This crate is typically used through the higher-level `fory` crate,
//! which provides derive macros and a more convenient API. However,
//! you can use the core types directly for advanced use cases.
//!
//! ```rust
//! use fory_core::{fory::Fory, error::Error, types::Mode};
//! use fory_core::row::{to_row, from_row};
//!
//! // Create a Fory instance
//! let mut fory = Fory::default().mode(Mode::Compatible);
//!
//! // Register types for object serialization
//! // fory.register::<MyStruct>(type_id);
//!
//! // Use row-based serialization for zero-copy operations
//! // let row_data = to_row(&my_data);
//! // let row = from_row::<MyStruct>(&row_data);
//! ```

pub mod buffer;
pub mod error;
pub mod fory;
pub mod meta;
pub mod resolver;
pub mod row;
pub mod serializer;
pub mod types;
pub mod util;

/// Macro to register trait object conversions for custom traits.
///
/// This macro automatically generates:
/// 1. `from_any_internal()` for deserializing trait objects using Fory's type system
/// 2. `Default` implementation for `Box<dyn Trait>` (uses first registered type)
///
/// **Note**: Your trait must provide `as_any()` method returning `&dyn Any`.
/// Use the `#[fory_trait]` attribute to automatically add this.
///
/// # Example
///
/// ```rust,ignore
/// use fory_core::register_trait_type;
/// use fory_derive::{fory_trait, Fory};
///
/// #[fory_trait]
/// trait Animal {
///     fn speak(&self);
/// }
///
/// #[derive(Fory)]
/// struct Dog { name: String }
///
/// #[derive(Fory)]
/// struct Cat { name: String }
///
/// impl Animal for Dog {
///     fn speak(&self) { println!("Woof!"); }
///     fn as_any(&self) -> &dyn std::any::Any { self }
/// }
///
/// impl Animal for Cat {
///     fn speak(&self) { println!("Meow!"); }
///     fn as_any(&self) -> &dyn std::any::Any { self }
/// }
///
/// // Register the trait and its implementations (no type IDs needed!)
/// register_trait_type!(Animal, Dog, Cat);
/// ```
#[macro_export]
macro_rules! register_trait_type {
    ($trait_name:ident, $($impl_type:ty),+ $(,)?) => {
        // Default implementation using first registered type
        impl std::default::Default for Box<dyn $trait_name> {
            fn default() -> Self {
                register_trait_type!(@first_default $($impl_type),+)
            }
        }

        // Serializer implementation for Box<dyn Trait>
        impl $crate::serializer::Serializer for Box<dyn $trait_name> {
            fn fory_write(&self, context: &mut $crate::resolver::context::WriteContext, is_field: bool) {
                use $crate::types::{Mode, RefFlag, TypeId};

                // Write headers manually (same as Box<dyn Serializer>)
                context.writer.write_i8(RefFlag::NotNullValue as i8);

                let fory_type_id = self.fory_type_id_dyn(context.get_fory());
                context.writer.write_varuint32(fory_type_id);

                if context.get_fory().get_mode() == &Mode::Compatible
                    && (fory_type_id & 0xff == TypeId::NAMED_COMPATIBLE_STRUCT as u32
                        || fory_type_id & 0xff == TypeId::COMPATIBLE_STRUCT as u32)
                {
                    let concrete_type_id = self.fory_concrete_type_id();
                    let meta_index = context.push_meta(concrete_type_id) as u32;
                    context.writer.write_varuint32(meta_index);
                }

                // Call fory_write_data on the concrete object (not harness functions)
                self.fory_write_data(context, is_field);
            }

            fn fory_write_data(&self, context: &mut $crate::resolver::context::WriteContext, is_field: bool) {
                // Delegate to the concrete type's fory_write_data
                let any_ref = self.as_any();
                let concrete_type_id = any_ref.type_id();

                // Try to downcast to each registered type and call fory_write_data
                $(
                    if concrete_type_id == std::any::TypeId::of::<$impl_type>() {
                        if let Some(concrete) = any_ref.downcast_ref::<$impl_type>() {
                            concrete.fory_write_data(context, is_field);
                            return;
                        }
                    }
                )*

                panic!("Failed to downcast Box<dyn {}> to any registered type", stringify!($trait_name));
            }

            fn fory_type_id_dyn(&self, fory: &$crate::fory::Fory) -> u32 {
                let any_ref = self.as_any();
                let concrete_type_id = any_ref.type_id();
                fory.get_type_resolver()
                    .get_fory_type_id(concrete_type_id)
                    .expect("Type not registered for trait object")
            }

            fn fory_is_polymorphic() -> bool {
                true
            }

            fn fory_write_type_info(_context: &mut $crate::resolver::context::WriteContext, _is_field: bool) {
                // Box<dyn Trait> is polymorphic - type info is written per element
            }

            fn fory_read_type_info(_context: &mut $crate::resolver::context::ReadContext, _is_field: bool) {
                // Box<dyn Trait> is polymorphic - type info is read per element
            }

            fn fory_read(context: &mut $crate::resolver::context::ReadContext, is_field: bool) -> Result<Self, $crate::error::Error>
            where
                Self: Sized + Default,
            {
                use $crate::types::{Mode, RefFlag, TypeId};

                // Read headers manually (same as Box<dyn Serializer>)
                let ref_flag = context.reader.read_i8();
                if ref_flag != RefFlag::NotNullValue as i8 {
                    return Err($crate::error::Error::Other($crate::error::AnyhowError::msg(
                        format!("Expected NotNullValue ref flag, got {}", ref_flag)
                    )));
                }

                let fory_type_id = context.reader.read_varuint32();

                if context.get_fory().get_mode() == &Mode::Compatible
                    && (fory_type_id & 0xff == TypeId::NAMED_COMPATIBLE_STRUCT as u32
                        || fory_type_id & 0xff == TypeId::COMPATIBLE_STRUCT as u32)
                {
                    let _meta_index = context.reader.read_varuint32();
                }

                // Now determine which concrete type this is and call its fory_read_data
                $(
                    // Check if this type ID matches any of our registered types
                    if let Some(registered_type_id) = context.get_fory().get_type_resolver().get_fory_type_id(std::any::TypeId::of::<$impl_type>()) {
                        if fory_type_id == registered_type_id {
                            let concrete_obj = <$impl_type as $crate::serializer::Serializer>::fory_read_data(context, is_field)?;
                            return Ok(Box::new(concrete_obj) as Box<dyn $trait_name>);
                        }
                    }
                )*

                Err($crate::error::Error::Other($crate::error::AnyhowError::msg(
                    format!("Type ID {} not registered for trait {}", fory_type_id, stringify!($trait_name))
                )))
            }

            fn fory_read_data(_context: &mut $crate::resolver::context::ReadContext, _is_field: bool) -> Result<Self, $crate::error::Error>
            where
                Self: Sized + Default,
            {
                // This should not be called for polymorphic types like Box<dyn Trait>
                // The fory_read method handles the polymorphic dispatch
                panic!("fory_read_data should not be called directly on polymorphic Box<dyn {}> trait object", stringify!($trait_name));
            }

            fn fory_get_type_id(_fory: &$crate::fory::Fory) -> u32 {
                $crate::types::TypeId::STRUCT as u32
            }

            fn fory_reserved_space() -> usize {
                $crate::types::SIZE_OF_REF_AND_TYPE
            }

            fn fory_concrete_type_id(&self) -> std::any::TypeId {
                self.as_any().type_id()
            }
        }

        // Create helper functions for this trait
        #[allow(non_snake_case)]
        mod __fory_trait_helpers {
            use super::*;

            #[allow(dead_code)]
            pub fn from_any_internal(
                any_box: Box<dyn std::any::Any>,
                _fory_type_id: u32,
            ) -> Result<Box<dyn $trait_name>, $crate::error::Error> {
                $(
                    if any_box.is::<$impl_type>() {
                        let concrete = any_box.downcast::<$impl_type>()
                            .map_err(|_| $crate::error::Error::Other(
                                $crate::error::AnyhowError::msg(format!("Failed to downcast to {}", stringify!($impl_type)))
                            ))?;
                        return Ok(concrete as Box<dyn $trait_name>);
                    }
                )+

                Err($crate::error::Error::Other($crate::error::AnyhowError::msg(
                    format!("No matching type found for trait {}", stringify!($trait_name))
                )))
            }
        }
    };

    // Helper to get first type for Default impl
    (@first_default $first_type:ty $(, $rest:ty)*) => {
        Box::new(<$first_type as std::default::Default>::default())
    };
}
