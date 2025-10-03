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
/// 1. `from_any_internal()` for deserializing trait objects
/// 2. `Default` implementation for `Box<dyn Trait>` (uses first registered type)
///
/// **Note**: Your trait must provide `as_any()` method returning `&dyn Any`.
/// You can use the `fory_trait!` macro to automatically add this.
///
/// # Example
///
/// ```rust,ignore
/// use fory_core::{register_trait_type, fory_trait};
/// use fory_derive::Fory;
///
/// // Define trait with as_any support
/// fory_trait! {
///     trait Animal {
///         fn speak(&self);
///     }
/// }
///
/// #[derive(Fory, Default)]
/// struct Dog { name: String }
///
/// impl Animal for Dog {
///     fn speak(&self) { println!("Woof!"); }
/// }
///
/// // Register the trait and its implementations
/// register_trait_type!(Animal, (Dog, 5001), (Cat, 5002));
/// ```
#[macro_export]
macro_rules! register_trait_type {
    ($trait_name:ident, $(($impl_type:ty, $type_id:expr)),+ $(,)?) => {
        // Default implementation using first registered type
        impl std::default::Default for Box<dyn $trait_name> {
            fn default() -> Self {
                register_trait_type!(@first_default $(($impl_type)),+)
            }
        }

        // Create a module with helper functions for this trait
        mod __fory_trait_helpers {
            use super::*;

            #[allow(dead_code)]
            pub fn from_any_internal(
                any_box: Box<dyn std::any::Any>,
                fory_type_id: u32,
            ) -> Result<Box<dyn $trait_name>, $crate::error::Error> {
                match fory_type_id {
                    $(
                        $type_id => {
                            let concrete = any_box.downcast::<$impl_type>()
                                .map_err(|_| $crate::error::Error::Other(
                                    anyhow::anyhow!("Failed to downcast to {}", stringify!($impl_type))
                                ))?;
                            Ok(concrete as Box<dyn $trait_name>)
                        }
                    )+
                    _ => Err($crate::error::Error::Other(anyhow::anyhow!(
                        "Type ID {} is not registered for trait {}",
                        fory_type_id,
                        stringify!($trait_name)
                    )))
                }
            }
        }
    };

    // Helper to get first type for Default impl
    (@first_default ($first_type:ty) $(, $rest:tt)*) => {
        Box::new(<$first_type as std::default::Default>::default())
    };
}

/// Macro to define a Fory-compatible trait.
///
/// This automatically adds the `as_any()` method required for trait object serialization.
///
/// # Example
///
/// ```rust,ignore
/// use fory_core::fory_trait;
///
/// fory_trait! {
///     pub trait Animal {
///         fn speak(&self) -> String;
///         fn name(&self) -> &str;
///     }
/// }
/// ```
#[macro_export]
macro_rules! fory_trait {
    (
        $(#[$meta:meta])*
        $vis:vis trait $trait_name:ident $(: $($supertrait:path),+)? {
            $(
                $(#[$method_meta:meta])*
                fn $method_name:ident(&self $(, $arg_name:ident: $arg_type:ty)*) $(-> $ret_type:ty)?;
            )*
        }
    ) => {
        $(#[$meta])*
        $vis trait $trait_name $(: $($supertrait +)+)? {
            $(
                $(#[$method_meta])*
                fn $method_name(&self $(, $arg_name: $arg_type)*) $(-> $ret_type)?;
            )*

            /// Get a reference to this object as `&dyn Any` for runtime type identification
            fn as_any(&self) -> &dyn std::any::Any;
        }
    };
}
