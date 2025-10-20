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

//! Core serialization traits and default implementations for Apache Fory.
//!
//! This module defines the fundamental traits and interfaces for Apache Fory serialization framework. 
//! It provides the building blocks for serialization with support for polymorphism, reference tracking, and 
//! complex object graphs.
//!
//! # Architecture Overview
//!
//! Fory's serialization system is built around two main traits:
//!
//! - [`Serializer`]: The core trait that all serializable types must implement
//! - [`StructSerializer`]: An extension trait for struct/enum types
//!
//! # Key Features
//!
//! ## Reference Tracking
//!
//! Fory supports tracking shared and circular references to avoid duplication and infinite recursion:
//! - **Null values**: Represented by `RefFlag::Null` (-3)
//! - **Reference to existing object**: `RefFlag::Ref` (-2) followed by reference ID
//! - **Non-referenceable value**: `RefFlag::NotNullValue` (-1)
//! - **Referenceable value (first occurrence)**: `RefFlag::RefValue` (0) - will be stored for future reference
//!
//! ## Type Information
//!
//! Types can write type metadata to support polymorphism and cross-language serialization:
//! - **Static types**: Use predefined `TypeId` constants (e.g., `TypeId::STRING`, `TypeId::INT32`)
//! - **Dynamic types**: Write full type metadata including Rust's `std::any::TypeId`
//! - **Polymorphic types**: Trait objects (`Box<dyn Trait>`) write concrete type info per instance
//!
//! ## Generic Collections
//!
//! Collections (Vec, HashMap, etc.) can optimize serialization based on element type:
//! - If element type is declared in type system, skip writing type info per element
//! - If all elements are same type, write type once in collection header
//! - For polymorphic elements, write type info per element
//!
//! # Implementation Patterns
//!
//! ## Simple Value Types
//!
//! For simple value types (primitives, strings), implement [`Serializer`] directly:
//!
//! ```rust,ignore
//! impl Serializer for MyType {
//!     fn fory_write_data(&self, context: &mut WriteContext) -> Result<(), Error> {
//!         context.writer.write_bytes(&self.data)?;
//!         Ok(())
//!     }
//!
//!     fn fory_read_data(context: &mut ReadContext) -> Result<Self, Error> {
//!         let data = context.reader.read_bytes(N)?;
//!         Ok(MyType { data })
//!     }
//!
//!     fn fory_static_type_id() -> TypeId { TypeId::EXT }
//!     fn as_any(&self) -> &dyn Any { self }
//!     fn fory_type_id_dyn(&self, type_resolver: &TypeResolver) -> Result<u32, Error> {
//!         Self::fory_get_type_id(type_resolver)
//!     }
//! }
//! ```
//!
//! ## Option Types
//!
//! Option types override `fory_write` to handle null values:
//!
//! ```rust,ignore
//! impl<T: Serializer> Serializer for Option<T> {
//!     fn fory_write(&self, context: &mut WriteContext, write_ref_info: bool,
//!                   write_type_info: bool, has_generics: bool) -> Result<(), Error> {
//!         match self {
//!             Some(v) => v.fory_write(context, write_ref_info, write_type_info, has_generics),
//!             None => {
//!                 if write_ref_info {
//!                     context.writer.write_u8(RefFlag::Null as u8);
//!                 }
//!                 Ok(())
//!             }
//!         }
//!     }
//!
//!     fn fory_is_option() -> bool { true }
//!     // ... other methods
//! }
//! ```
//!
//! ## Shared Reference Types (Rc/Arc)
//!
//! Reference-counted types implement reference tracking:
//!
//! ```rust,ignore
//! impl<T: Serializer> Serializer for Rc<T> {
//!     fn fory_write(&self, context: &mut WriteContext, write_ref_info: bool,
//!                   write_type_info: bool, has_generics: bool) -> Result<(), Error> {
//!         if !write_ref_info || !context.ref_writer.try_write_rc_ref(&mut context.writer, self) {
//!             // First occurrence - serialize the value
//!             T::fory_write(&**self, context, false, write_type_info, has_generics)
//!         } else {
//!             // Already serialized - ref ID was written
//!             Ok(())
//!         }
//!     }
//!
//!     fn fory_is_shared_ref() -> bool { true }
//!     // ... other methods
//! }
//! ```
//!
//! ## Trait Objects
//!
//! Polymorphic trait objects use dynamic dispatch:
//!
//! ```rust,ignore
//! impl Serializer for Box<dyn MyTrait> {
//!     fn fory_write(&self, context: &mut WriteContext, write_ref_info: bool,
//!                   write_type_info: bool, has_generics: bool) -> Result<(), Error> {
//!         if write_ref_info {
//!             context.writer.write_i8(RefFlag::NotNullValue as i8);
//!         }
//!         let concrete_type_id = (**self).type_id();
//!         if write_type_info {
//!             context.write_any_typeinfo(TypeId::UNKNOWN as u32, concrete_type_id)?;
//!         }
//!         // Use type resolver to get serializer for concrete type
//!         let typeinfo = context.get_type_info(&concrete_type_id)?;
//!         typeinfo.get_harness().get_write_data_fn()(&**self, context, has_generics)
//!     }
//!
//!     fn fory_is_polymorphic() -> bool { true }
//!     // ... other methods
//! }
//! ```
//!
//! ## Collections
//!
//! Collections optimize based on element characteristics:
//!
//! ```rust,ignore
//! impl<T: Serializer> Serializer for Vec<T> {
//!     fn fory_write_data_generic(&self, context: &mut WriteContext,
//!                                has_generics: bool) -> Result<(), Error> {
//!         write_collection_data(self.iter(), context, has_generics)
//!         // Inside write_collection_data:
//!         // - Write length
//!         // - Write collection header with flags (same type, has null, declared type)
//!         // - Write type info once if all elements are same type
//!         // - Write elements
//!     }
//!     // ... other methods
//! }
//! ```
//!
//! # Type Flags
//!
//! Several boolean methods control serialization behavior:
//!
//! - `fory_is_option()`: Returns true for `Option<T>` - enables null handling
//! - `fory_is_polymorphic()`: Returns true for trait objects - requires per-instance type info
//! - `fory_is_shared_ref()`: Returns true for `Rc<T>`/`Arc<T>` - enables reference tracking
//! - `fory_is_none()`: Instance method to check if Option is None
//!
//! # Examples
//!
//! Basic serialization:
//!
//! ```rust,ignore
//! use fory_core::{Fory, Serializer};
//!
//! let value = "hello".to_string();
//! let mut fory = Fory::default();
//! let bytes = fory.serialize(&value)?;
//! let deserialized: String = fory.deserialize(&bytes)?;
//! assert_eq!(value, deserialized);
//! ```
//!
//! With reference tracking:
//!
//! ```rust,ignore
//! use std::rc::Rc;
//!
//! let shared = Rc::new(vec![1, 2, 3]);
//! let data = (shared.clone(), shared.clone());  // Same Rc referenced twice
//! let bytes = fory.serialize(&data)?;
//! // Deserializes with single allocation, both tuple elements point to same Rc
//! ```

use crate::error::Error;
use crate::meta::FieldInfo;
use crate::resolver::context::{ReadContext, WriteContext};
use crate::resolver::type_resolver::TypeInfo;
use crate::serializer::{bool, struct_};
use crate::types::{RefFlag, TypeId};
use crate::TypeResolver;
use std::any::Any;
use std::rc::Rc;

/// A trait for providing default values for Fory types.
///
/// This trait is similar to [`std::default::Default`] but with a different name to avoid
/// coherence conflicts. Fory cannot provide a blanket implementation like:
///
/// ```rust,ignore
/// impl<T: Default + Sized> ForyDefault for T {
///     fn fory_default() -> Self {
///         Default::default()
///     }
/// }
/// ```
///
/// because upstream crates might add new implementations that conflict (e.g., if std adds
/// `impl Default for Rc<dyn Any>`). Instead, Fory provides explicit implementations for
/// each supported type.
///
/// # Purpose
///
/// This trait is primarily used in deserialization when:
/// - Reading null values for `Option<T>` types
/// - Creating default instances for error recovery
/// - Initializing containers before populating them
///
/// # Implementation
///
/// For most types, `fory_default()` should return the same value as `Default::default()`:
///
/// ```rust,ignore
/// impl ForyDefault for MyType {
///     fn fory_default() -> Self {
///         MyType::new()  // or MyType { field: default_value }
///     }
/// }
/// ```
///
/// For `Option<T>`, it always returns `None`:
///
/// ```rust,ignore
/// impl<T: ForyDefault> ForyDefault for Option<T> {
///     fn fory_default() -> Self {
///         None
///     }
/// }
/// ```
pub trait ForyDefault: Sized {
    /// Creates a default value for this type.
    ///
    /// # Returns
    ///
    /// A default instance of `Self`.
    fn fory_default() -> Self;
}

// We can't add blanket impl for all T: Default because it conflicts with other impls.
// For example, upstream crates may add a new impl of trait `std::default::Default` for
// type `std::rc::Rc<(dyn std::any::Any + 'static)>` in future versions.
// impl<T: Default + Sized> ForyDefault for T {
//     fn fory_default() -> Self {
//         Default::default()
//     }
// }

/// The core serialization trait for Apache Fory.
///
/// This trait defines the complete serialization and deserialization interface for all Fory-serializable
/// types. It provides a flexible, high-performance serialization system with support for:
///
/// - **Zero-copy deserialization**: Types can read directly from buffers without intermediate allocations
/// - **Reference tracking**: Shared (`Rc`/`Arc`) and circular references are automatically handled
/// - **Polymorphism**: Trait objects and `dyn Any` types preserve concrete type information
/// - **Cross-language compatibility**: Serialized data can be read by Fory implementations in other languages
/// - **Optimized collections**: Generic collections avoid redundant type information
///
/// # Type Categories
///
/// Different type categories should override different methods:
///
/// ## Simple Value Types
///
/// Primitives, strings, and simple structs only need to implement:
/// - [`fory_write_data`](Self::fory_write_data): Serialize the value
/// - [`fory_read_data`](Self::fory_read_data): Deserialize the value
/// - [`fory_static_type_id`](Self::fory_static_type_id): Return the type's static ID
/// - [`as_any`](Self::as_any): Enable downcasting
/// - [`fory_type_id_dyn`](Self::fory_type_id_dyn): Get dynamic type ID
///
/// ## Optional Types (`Option<T>`)
///
/// Must override:
/// - [`fory_write`](Self::fory_write): Write null flag for `None` values
/// - [`fory_read`](Self::fory_read): Handle null flag on read
/// - [`fory_is_option`](Self::fory_is_option): Return `true`
/// - [`fory_is_none`](Self::fory_is_none): Check if value is `None`
///
/// ## Shared References (`Rc<T>`, `Arc<T>`)
///
/// Must override:
/// - [`fory_write`](Self::fory_write): Track references and avoid duplication
/// - [`fory_read`](Self::fory_read): Restore shared references
/// - [`fory_is_shared_ref`](Self::fory_is_shared_ref): Return `true`
///
/// ## Polymorphic Types (Trait Objects)
///
/// Must override:
/// - [`fory_write`](Self::fory_write): Write concrete type information dynamically
/// - [`fory_read`](Self::fory_read): Dispatch to concrete type's deserializer
/// - [`fory_is_polymorphic`](Self::fory_is_polymorphic): Return `true`
/// - [`fory_concrete_type_id`](Self::fory_concrete_type_id): Return actual type ID
///
/// ## Collections (`Vec`, `HashMap`, etc.)
///
/// Must override:
/// - [`fory_write_data_generic`](Self::fory_write_data_generic): Optimize based on `has_generics` flag
///
/// # Serialization Control Flow
///
/// The typical serialization flow is:
///
/// 1. **Entry point**: [`fory_write`](Self::fory_write) is called with control flags
/// 2. **Reference flag**: If `write_ref_info=true`, writes ref flag (null/not-null/ref)
/// 3. **Type information**: If `write_type_info=true`, writes type metadata
/// 4. **Data payload**: Calls [`fory_write_data_generic`](Self::fory_write_data_generic) or
///    [`fory_write_data`](Self::fory_write_data) to write actual data
///
/// The deserialization flow mirrors this:
///
/// 1. **Entry point**: [`fory_read`](Self::fory_read) is called with control flags
/// 2. **Reference flag**: If `read_ref_info=true`, reads and handles ref flag
/// 3. **Type information**: If `read_type_info=true`, reads type metadata
/// 4. **Data payload**: Calls [`fory_read_data`](Self::fory_read_data) to read actual data
///
/// # Performance Considerations
///
/// - **Inline annotations**: Most methods are marked `#[inline(always)]` for maximum performance
/// - **Reserved space**: [`fory_reserved_space`](Self::fory_reserved_space) helps pre-allocate buffers
/// - **Generic optimization**: The `has_generics` flag enables collection optimizations
/// - **Zero-copy**: Strings and byte arrays can be deserialized without copying
///
/// # Thread Safety
///
/// The trait requires `'static` lifetime but not `Send` or `Sync`. For multi-threaded serialization:
/// - Use `Arc<T>` instead of `Rc<T>` for shared references
/// - Each thread should have its own serialization context
///
/// # Examples
///
/// See the module-level documentation for comprehensive examples of implementing this trait.
pub trait Serializer: 'static {
    /// Primary entry point for serialization with full control over metadata.
    ///
    /// This method orchestrates the complete serialization process, including reference tracking,
    /// type information, and the actual data payload. Most types can use the default implementation,
    /// but types with special handling (Option, Rc, Arc, trait objects) must override this.
    ///
    /// # Parameters
    ///
    /// * `context` - Mutable serialization context containing the output buffer and metadata
    /// * `write_ref_info` - Controls reference flag writing:
    ///   - `true`: Writes `RefFlag` byte indicating null/not-null/ref status
    ///   - `false`: Skips ref flag (used for nested non-referenceable values)
    /// * `write_type_info` - Controls type metadata writing:
    ///   - `true`: Writes type ID and metadata for polymorphic deserialization
    ///   - `false`: Skips type info (used when type is statically known)
    /// * `has_generics` - Optimization hint for collections:
    ///   - `true`: Type has generic parameters (enables collection header optimization)
    ///   - `false`: Monomorphic type (standard serialization)
    ///
    /// # Default Implementation
    ///
    /// The default implementation performs standard serialization:
    /// 1. If `write_ref_info`: writes `RefFlag::NotNullValue` (-1)
    /// 2. If `write_type_info`: calls [`fory_write_type_info`](Self::fory_write_type_info)
    /// 3. Calls [`fory_write_data_generic`](Self::fory_write_data_generic) to write data
    ///
    /// # Types That Must Override
    ///
    /// - **`Option<T>`**: Writes `RefFlag::Null` for `None` instead of `NotNullValue`
    /// - **`Rc<T>`/`Arc<T>`**: Checks reference cache and writes ref ID if already serialized
    /// - **`Box<dyn Trait>`**: Writes concrete type info instead of trait type
    /// - **`Rc<dyn Any>`/`Arc<dyn Any>`**: Combines reference tracking with polymorphism
    ///
    /// # Errors
    ///
    /// Returns `Error` if:
    /// - Buffer write operations fail
    /// - Type registration is missing (for dynamic types)
    /// - Recursion depth exceeds limits
    ///
    /// # Examples
    ///
    /// Override for `Option<T>`:
    /// ```rust,ignore
    /// fn fory_write(&self, context: &mut WriteContext, write_ref_info: bool,
    ///               write_type_info: bool, has_generics: bool) -> Result<(), Error> {
    ///     match self {
    ///         Some(v) => v.fory_write(context, write_ref_info, write_type_info, has_generics),
    ///         None => {
    ///             if write_ref_info {
    ///                 context.writer.write_u8(RefFlag::Null as u8);
    ///             }
    ///             Ok(())
    ///         }
    ///     }
    /// }
    /// ```
    ///
    /// Override for `Rc<T>` with reference tracking:
    /// ```rust,ignore
    /// fn fory_write(&self, context: &mut WriteContext, write_ref_info: bool,
    ///               write_type_info: bool, has_generics: bool) -> Result<(), Error> {
    ///     if !write_ref_info || !context.ref_writer.try_write_rc_ref(&mut context.writer, self) {
    ///         // First occurrence - serialize the value
    ///         T::fory_write(&**self, context, false, write_type_info, has_generics)
    ///     } else {
    ///         // Already serialized - reference ID was written by try_write_rc_ref
    ///         Ok(())
    ///     }
    /// }
    /// ```
    fn fory_write(
        &self,
        context: &mut WriteContext,
        write_ref_info: bool,
        write_type_info: bool,
        has_generics: bool,
    ) -> Result<(), Error>
    where
        Self: Sized,
    {
        if write_ref_info {
            // skip check option/pointer, the Serializer for such types will override `fory_write`.
            context.writer.write_i8(RefFlag::NotNullValue as i8);
        }
        if write_type_info {
            // Serializer for dynamic types should override `fory_write` to write actual typeinfo.
            Self::fory_write_type_info(context)?;
        }
        self.fory_write_data_generic(context, has_generics)
    }

    /// Writes the data payload with generic parameter optimization.
    ///
    /// This method is primarily used by collection and map types to optimize serialization
    /// when generic type parameters are known at compile time. For non-collection types,
    /// the default implementation simply forwards to [`fory_write_data`](Self::fory_write_data).
    ///
    /// # Parameters
    ///
    /// * `context` - Mutable serialization context
    /// * `has_generics` - Optimization flag for collections:
    ///   - `true`: Type has generic parameters - collection can write optimized header
    ///   - `false`: No generics - standard serialization
    ///
    /// # Collection Optimization
    ///
    /// When `has_generics=true`, collections can:
    /// - Mark element type as "declared" in collection header (saves repeated type info)
    /// - Skip writing type info for each element if all elements are same type
    /// - Write type info once in header instead of per-element
    ///
    /// # Override Pattern
    ///
    /// Collections should override this to pass `has_generics` to collection writing logic:
    ///
    /// ```rust,ignore
    /// impl<T: Serializer> Serializer for Vec<T> {
    ///     fn fory_write_data_generic(&self, context: &mut WriteContext,
    ///                                has_generics: bool) -> Result<(), Error> {
    ///         write_collection_data(self.iter(), context, has_generics)
    ///     }
    /// }
    /// ```
    ///
    /// # Default Implementation
    ///
    /// For non-collection types, ignores `has_generics` and calls `fory_write_data`:
    ///
    /// ```rust,ignore
    /// fn fory_write_data_generic(&self, context: &mut WriteContext,
    ///                            _has_generics: bool) -> Result<(), Error> {
    ///     self.fory_write_data(context)
    /// }
    /// ```
    #[allow(unused_variables)]
    fn fory_write_data_generic(
        &self,
        context: &mut WriteContext,
        has_generics: bool,
    ) -> Result<(), Error> {
        self.fory_write_data(context)
    }

    /// Writes the actual data payload to the buffer.
    ///
    /// This is the core method that writes the serialized representation of the value.
    /// It should write only the data, not reference flags or type information (those are
    /// handled by [`fory_write`](Self::fory_write)).
    ///
    /// # Implementation Requirements
    ///
    /// - **Must** be implemented by all types (no default)
    /// - Should be deterministic (same input → same output)
    /// - Should use buffer methods efficiently
    /// - Should minimize allocations
    ///
    /// # Wire Format
    ///
    /// The wire format depends on the type:
    ///
    /// - **Primitives**: Fixed-size binary representation (e.g., i32 as 4 bytes little-endian)
    /// - **Strings**: Length prefix + encoding marker + bytes (Latin1/UTF-8/UTF-16)
    /// - **Collections**: Length + header byte + type info + elements
    /// - **Structs**: Field count + field values in sorted field name order
    /// - **Enums**: Variant index + variant data
    ///
    /// # Examples
    ///
    /// Simple struct:
    /// ```rust,ignore
    /// struct Point { x: i32, y: i32 }
    ///
    /// impl Serializer for Point {
    ///     fn fory_write_data(&self, context: &mut WriteContext) -> Result<(), Error> {
    ///         self.x.fory_write(context, false, false, false)?;
    ///         self.y.fory_write(context, false, false, false)?;
    ///         Ok(())
    ///     }
    /// }
    /// ```
    ///
    /// String with encoding:
    /// ```rust,ignore
    /// impl Serializer for String {
    ///     fn fory_write_data(&self, context: &mut WriteContext) -> Result<(), Error> {
    ///         let len = self.len();
    ///         // Write length with encoding marker in lower 2 bits
    ///         let bitor = (len as u64) << 2 | EncodingType::Utf8 as u64;
    ///         context.writer.write_varuint36_small(bitor);
    ///         context.writer.write_utf8_string(self);
    ///         Ok(())
    ///     }
    /// }
    /// ```
    fn fory_write_data(&self, context: &mut WriteContext) -> Result<(), Error>;

    /// Writes type metadata to the buffer for this type.
    ///
    /// This method writes the Fory type ID and (for external types) the Rust `TypeId`
    /// to enable deserialization and type checking. Internal Fory types (primitives,
    /// collections) can override this for faster serialization.
    ///
    /// # Default Implementation
    ///
    /// For user-defined types, writes:
    /// 1. The Fory type ID from [`fory_static_type_id`](Self::fory_static_type_id)
    /// 2. The Rust `std::any::TypeId` for this type
    ///
    /// This enables the deserializer to verify type compatibility and dispatch to the
    /// correct deserialization logic.
    ///
    /// # Override Pattern
    ///
    /// Internal types override for performance:
    ///
    /// ```rust,ignore
    /// impl Serializer for String {
    ///     fn fory_write_type_info(context: &mut WriteContext) -> Result<(), Error> {
    ///         context.writer.write_varuint32(TypeId::STRING as u32);
    ///         Ok(())
    ///     }
    /// }
    /// ```
    ///
    /// # Polymorphic Types
    ///
    /// Polymorphic types (trait objects) should not call this directly - instead they
    /// override [`fory_write`](Self::fory_write) to write concrete type info per instance.
    ///
    /// # Wire Format
    ///
    /// - **Internal types**: 1-5 bytes (varuint32 of type ID)
    /// - **External types**: 1-5 bytes (type ID) + 8 bytes (Rust TypeId hash)
    #[inline(always)]
    fn fory_write_type_info(context: &mut WriteContext) -> Result<(), Error>
    where
        Self: Sized,
    {
        // Serializer for internal types should overwrite this method for faster performance.
        let rs_type_id = std::any::TypeId::of::<Self>();
        context.write_any_typeinfo(Self::fory_static_type_id() as u32, rs_type_id)?;
        Ok(())
    }

    /// Primary entry point for deserialization with full control over metadata.
    ///
    /// This method orchestrates the complete deserialization process, mirroring
    /// [`fory_write`](Self::fory_write). It reads reference flags, type information,
    /// and the actual data payload to reconstruct the value.
    ///
    /// # Parameters
    ///
    /// * `context` - Mutable deserialization context containing the input buffer and metadata
    /// * `read_ref_info` - Controls reference flag reading:
    ///   - `true`: Reads and processes `RefFlag` byte (null/not-null/ref)
    ///   - `false`: Skips ref flag (used when caller knows value is not null/ref)
    /// * `read_type_info` - Controls type metadata reading:
    ///   - `true`: Reads and validates type information from buffer
    ///   - `false`: Skips type info (used when type is statically known)
    ///
    /// # Default Implementation
    ///
    /// The default implementation handles standard deserialization:
    ///
    /// 1. If `read_ref_info=true`:
    ///    - Reads `RefFlag` byte
    ///    - Returns default value if flag is `Null` (-3)
    ///    - Continues if flag is `NotNullValue` (-1) or `RefValue` (0)
    ///    - Returns error if flag is `Ref` (-2) for non-reference types
    /// 2. If `read_type_info=true`: calls [`fory_read_type_info`](Self::fory_read_type_info)
    /// 3. Calls [`fory_read_data`](Self::fory_read_data) to read data payload
    ///
    /// # Types That Must Override
    ///
    /// - **`Option<T>`**: Returns `None` for `Null` flag, `Some(T)` for others
    /// - **`Rc<T>`/`Arc<T>`**: Checks reference cache and returns existing instance if `Ref` flag
    /// - **Polymorphic types**: Dispatches to concrete type deserializer based on type info
    ///
    /// # Errors
    ///
    /// Returns `Error` if:
    /// - Buffer read operations fail (truncated data)
    /// - Unknown ref flag value encountered
    /// - `Ref` flag found for non-reference type
    /// - Type validation fails
    /// - Recursion depth exceeds limits
    ///
    /// # Examples
    ///
    /// Override for `Option<T>`:
    /// ```rust,ignore
    /// fn fory_read(context: &mut ReadContext, read_ref_info: bool,
    ///              read_type_info: bool) -> Result<Self, Error> {
    ///     if read_ref_info {
    ///         let ref_flag = context.reader.read_i8()?;
    ///         if ref_flag == RefFlag::Null as i8 {
    ///             return Ok(None);
    ///         }
    ///         // Handle shared ref types specially
    ///         if T::fory_is_shared_ref() {
    ///             context.reader.move_back(1); // Rewind for nested read
    ///             return Ok(Some(T::fory_read(context, true, read_type_info)?));
    ///         }
    ///     }
    ///     Ok(Some(T::fory_read(context, false, read_type_info)?))
    /// }
    /// ```
    ///
    /// Override for `Rc<T>` with reference tracking:
    /// ```rust,ignore
    /// fn fory_read(context: &mut ReadContext, read_ref_info: bool,
    ///              read_type_info: bool) -> Result<Self, Error> {
    ///     let ref_flag = if read_ref_info {
    ///         context.ref_reader.read_ref_flag(&mut context.reader)?
    ///     } else {
    ///         RefFlag::NotNullValue
    ///     };
    ///     match ref_flag {
    ///         RefFlag::Ref => {
    ///             let ref_id = context.ref_reader.read_ref_id(&mut context.reader)?;
    ///             context.ref_reader.get_rc_ref::<T>(ref_id)
    ///                 .ok_or_else(|| Error::invalid_ref("Reference not found"))
    ///         }
    ///         RefFlag::RefValue => {
    ///             let ref_id = context.ref_reader.reserve_ref_id();
    ///             let inner = read_inner::<T>(context, read_type_info)?;
    ///             let rc = Rc::new(inner);
    ///             context.ref_reader.store_rc_ref_at(ref_id, rc.clone());
    ///             Ok(rc)
    ///         }
    ///         _ => Ok(Rc::new(read_inner::<T>(context, read_type_info)?))
    ///     }
    /// }
    /// ```
    ///
    /// # Notes
    ///
    /// Unlike [`fory_write`](Self::fory_write), this method doesn't need a `has_generics`
    /// parameter because the metadata is already in the buffer. Collections read the header
    /// to determine how elements are encoded.
    fn fory_read(
        context: &mut ReadContext,
        read_ref_info: bool,
        read_type_info: bool,
    ) -> Result<Self, Error>
    where
        Self: Sized + ForyDefault,
    {
        if read_ref_info {
            let ref_flag = context.reader.read_i8()?;
            match ref_flag {
                flag if flag == RefFlag::Null as i8 => Ok(Self::fory_default()),
                flag if flag == RefFlag::NotNullValue as i8 || flag == RefFlag::RefValue as i8 => {
                    if read_type_info {
                        Self::fory_read_type_info(context)?;
                    }
                    Self::fory_read_data(context)
                }
                flag if flag == RefFlag::Ref as i8 => {
                    Err(Error::invalid_ref("Invalid ref, current type is not a ref"))
                }
                other => Err(Error::invalid_data(format!("Unknown ref flag: {}", other))),
            }
        } else {
            if read_type_info {
                Self::fory_read_type_info(context)?;
            }
            Self::fory_read_data(context)
        }
    }

    /// Deserializes a value when type information has been pre-read.
    ///
    /// This is an optimization for polymorphic types where the type information is read before
    /// deciding which concrete type to deserialize. By passing the pre-read type info, we avoid
    /// reading it twice from the buffer.
    ///
    /// # Parameters
    ///
    /// * `context` - Mutable deserialization context
    /// * `read_ref_info` - Whether to read reference flag from buffer
    /// * `type_info` - Pre-read type information (DO NOT read type info from buffer again)
    ///
    /// # When to Use
    ///
    /// This method is primarily used for:
    /// - **Trait objects** (`Box<dyn Trait>`) - Type info is read to dispatch to concrete deserializer
    /// - **`dyn Any` types** - Type info determines which concrete type to construct
    /// - **Polymorphic collections** - Element type is read once, then used for all elements
    ///
    /// # Default Implementation
    ///
    /// For monomorphic types, the default implementation ignores `type_info` and calls
    /// [`fory_read`](Self::fory_read) with `read_type_info=false` since the static type
    /// is known at compile time:
    ///
    /// ```rust,ignore
    /// fn fory_read_with_type_info(context: &mut ReadContext, read_ref_info: bool,
    ///                              _type_info: Rc<TypeInfo>) -> Result<Self, Error> {
    ///     Self::fory_read(context, read_ref_info, false)
    /// }
    /// ```
    ///
    /// # Types That Must Override
    ///
    /// - **Trait objects**: Dispatch to concrete type based on `type_info`
    /// - **`Rc<T>`/`Arc<T>` with polymorphic `T`**: Pass type info to inner read
    /// - **`Option<T>` with polymorphic `T`**: Pass type info to `T`'s deserializer
    ///
    /// # Examples
    ///
    /// Trait object deserialization:
    /// ```rust,ignore
    /// impl Serializer for Box<dyn MyTrait> {
    ///     fn fory_read_with_type_info(context: &mut ReadContext, read_ref_info: bool,
    ///                                  type_info: Rc<TypeInfo>) -> Result<Self, Error> {
    ///         let ref_flag = if read_ref_info {
    ///             context.reader.read_i8()?
    ///         } else {
    ///             RefFlag::NotNullValue as i8
    ///         };
    ///
    ///         // Use type_info to get concrete type's deserializer
    ///         let harness = type_info.get_harness();
    ///         let boxed_any = harness.get_read_data_fn()(context)?;
    ///
    ///         // Downcast to trait object
    ///         Ok(Box::from(boxed_any))
    ///     }
    /// }
    /// ```
    ///
    /// # Important
    ///
    /// This method must NOT read type info from the buffer since it's already provided
    /// via the `type_info` parameter. Doing so would cause deserialization to fail.
    #[allow(unused_variables)]
    fn fory_read_with_type_info(
        context: &mut ReadContext,
        read_ref_info: bool,
        type_info: Rc<TypeInfo>,
    ) -> Result<Self, Error>
    where
        Self: Sized + ForyDefault,
    {
        // Default implementation ignores the provided typeinfo because the static type matches.
        // Honor the supplied `read_ref_info` flag so callers can control whether ref metadata is present.
        Self::fory_read(context, read_ref_info, false)
    }

    /// Reads and reconstructs the value from its serialized data payload.
    ///
    /// This is the core deserialization method that reads the binary representation and
    /// reconstructs the value. It should only read the data payload, not reference flags
    /// or type information (those are handled by [`fory_read`](Self::fory_read)).
    ///
    /// # Implementation Requirements
    ///
    /// - **Must** be implemented by all types (no default)
    /// - Must read bytes in the same order they were written by [`fory_write_data`](Self::fory_write_data)
    /// - Should handle malformed data gracefully (return errors, not panic)
    /// - Should validate data constraints (e.g., string length, collection size)
    ///
    /// # Examples
    ///
    /// Simple struct:
    /// ```rust,ignore
    /// struct Point { x: i32, y: i32 }
    ///
    /// impl Serializer for Point {
    ///     fn fory_read_data(context: &mut ReadContext) -> Result<Self, Error> {
    ///         let x = i32::fory_read(context, false, false)?;
    ///         let y = i32::fory_read(context, false, false)?;
    ///         Ok(Point { x, y })
    ///     }
    /// }
    /// ```
    ///
    /// String with encoding detection:
    /// ```rust,ignore
    /// impl Serializer for String {
    ///     fn fory_read_data(context: &mut ReadContext) -> Result<Self, Error> {
    ///         let bitor = context.reader.read_varuint36small()?;
    ///         let len = (bitor >> 2) as usize;
    ///         let encoding = bitor & 0b11;
    ///
    ///         match encoding {
    ///             0 => context.reader.read_latin1_string(len),
    ///             1 => context.reader.read_utf16_string(len),
    ///             2 => context.reader.read_utf8_string(len),
    ///             _ => Err(Error::encoding_error("Invalid string encoding"))
    ///         }
    ///     }
    /// }
    /// ```
    fn fory_read_data(context: &mut ReadContext) -> Result<Self, Error>
    where
        Self: Sized + ForyDefault;

    /// Reads type metadata from the buffer and validates it.
    ///
    /// This method reads the Fory type ID and (for external types) the Rust `TypeId`
    /// from the buffer to enable type checking. Internal types can override this for
    /// faster deserialization.
    ///
    /// # Default Implementation
    ///
    /// For user-defined types, reads and validates both:
    /// 1. The Fory type ID (varuint32)
    /// 2. The Rust `std::any::TypeId` (8 bytes)
    ///
    /// Validates that the read type info matches the expected type `Self`.
    ///
    /// # Override Pattern
    ///
    /// Internal types override for performance (no validation needed):
    ///
    /// ```rust,ignore
    /// impl Serializer for String {
    ///     fn fory_read_type_info(context: &mut ReadContext) -> Result<(), Error> {
    ///         read_basic_type_info::<Self>(context)
    ///     }
    /// }
    /// ```
    ///
    /// # Polymorphic Types
    ///
    /// Polymorphic types (trait objects) should not call this directly - instead they
    /// use [`context.read_any_typeinfo()`] in [`fory_read`](Self::fory_read) to get
    /// concrete type information.
    ///
    /// # Errors
    ///
    /// Returns `Error` if:
    /// - Type ID doesn't match expected type
    /// - Rust `TypeId` doesn't match
    /// - Buffer is truncated
    #[inline(always)]
    fn fory_read_type_info(context: &mut ReadContext) -> Result<(), Error>
    where
        Self: Sized,
    {
        // Serializer for internal types should overwrite this method for faster performance.
        context.read_any_typeinfo()?;
        Ok(())
    }

    /// Returns whether this type is an `Option<T>`.
    ///
    /// This flag enables special null handling for optional types. When true:
    /// - `fory_write` writes `RefFlag::Null` for `None` values
    /// - `fory_read` returns default (None) when reading `RefFlag::Null`
    /// - Collection serializers check [`fory_is_none`](Self::fory_is_none) to optimize null handling
    ///
    /// # Default
    ///
    /// Returns `false` for all non-Option types.
    ///
    /// # Override
    ///
    /// Only `Option<T>` should override this:
    ///
    /// ```rust,ignore
    /// impl<T: Serializer> Serializer for Option<T> {
    ///     fn fory_is_option() -> bool { true }
    /// }
    /// ```
    #[inline(always)]
    fn fory_is_option() -> bool
    where
        Self: Sized,
    {
        false
    }

    /// Returns whether this specific instance is `None` (for Option types).
    ///
    /// Used by collection serializers to detect null elements and optimize serialization.
    /// Collections can pre-scan elements to set the `HAS_NULL` flag in the header.
    ///
    /// # Default
    ///
    /// Returns `false` for non-Option types (they cannot be null).
    ///
    /// # Override
    ///
    /// Only `Option<T>` should override this:
    ///
    /// ```rust,ignore
    /// impl<T: Serializer> Serializer for Option<T> {
    ///     fn fory_is_none(&self) -> bool {
    ///         self.is_none()
    ///     }
    /// }
    /// ```
    #[inline(always)]
    fn fory_is_none(&self) -> bool {
        false
    }

    /// Returns whether this type is polymorphic (trait object or dyn Any).
    ///
    /// Polymorphic types cannot write static type information because the concrete type
    /// varies per instance. When true:
    /// - `fory_write` must write concrete type info dynamically
    /// - `fory_read` must dispatch to concrete type's deserializer
    /// - Type info cannot be written statically via `fory_write_type_info`
    ///
    /// # Default
    ///
    /// Returns `false` for monomorphic types.
    ///
    /// # Types That Override
    ///
    /// - `Box<dyn Trait>` / `Rc<dyn Trait>` / `Arc<dyn Trait>`
    /// - `Box<dyn Any>` / `Rc<dyn Any>` / `Arc<dyn Any>`
    /// - `Box<dyn Serializer>`
    ///
    /// # Examples
    ///
    /// ```rust,ignore
    /// impl Serializer for Box<dyn MyTrait> {
    ///     fn fory_is_polymorphic() -> bool { true }
    /// }
    /// ```
    #[inline(always)]
    fn fory_is_polymorphic() -> bool
    where
        Self: Sized,
    {
        false
    }

    /// Returns whether this type is a shared reference (`Rc<T>` or `Arc<T>`).
    ///
    /// Shared references need special handling to track and deduplicate references:
    /// - First occurrence: Full serialization + ref ID assignment
    /// - Subsequent occurrences: Just write ref ID
    /// - Deserialization: Check ref cache before creating new instance
    ///
    /// # Default
    ///
    /// Returns `false` for non-reference types.
    ///
    /// # Types That Override
    ///
    /// - `Rc<T>` and `Arc<T>` for any `T`
    /// - Wrapper types for trait objects (e.g., `MyTraitRc`, `MyTraitArc`)
    ///
    /// # Examples
    ///
    /// ```rust,ignore
    /// impl<T: Serializer> Serializer for Rc<T> {
    ///     fn fory_is_shared_ref() -> bool { true }
    /// }
    /// ```
    #[inline(always)]
    fn fory_is_shared_ref() -> bool
    where
        Self: Sized,
    {
        false
    }

    /// Returns the static Fory type ID for this type.
    ///
    /// Type IDs are 16-bit integers that identify types in Fory's type system:
    /// - **0-999**: Reserved for Fory internal types (primitives, collections, etc.)
    /// - **1000+**: User-defined types registered via `fory.register::<T>(type_id)`
    /// - **`TypeId::EXT`**: Default for user types (dynamic registration)
    /// - **`TypeId::UNKNOWN`**: For polymorphic types (type varies per instance)
    ///
    /// # Default
    ///
    /// Returns `TypeId::EXT` to enable dynamic type registration.
    ///
    /// # Internal Types
    ///
    /// Internal types override with their specific type ID:
    ///
    /// ```rust,ignore
    /// impl Serializer for String {
    ///     fn fory_static_type_id() -> TypeId { TypeId::STRING }
    /// }
    /// impl Serializer for i32 {
    ///     fn fory_static_type_id() -> TypeId { TypeId::INT32 }
    /// }
    /// ```
    ///
    /// # Polymorphic Types
    ///
    /// Polymorphic types return `TypeId::UNKNOWN`:
    ///
    /// ```rust,ignore
    /// impl Serializer for Box<dyn Any> {
    ///     fn fory_static_type_id() -> TypeId { TypeId::UNKNOWN }
    /// }
    /// ```
    #[inline(always)]
    fn fory_static_type_id() -> TypeId
    where
        Self: Sized,
    {
        // set to ext to simplify the user defined serializer.
        // serializer for other types will override this method.
        TypeId::EXT
    }

    /// Gets the registered Fory type ID for this type from the type resolver.
    ///
    /// This looks up the type's registration in the type resolver and returns the
    /// assigned type ID. Used during type registration and validation.
    ///
    /// # Parameters
    ///
    /// * `type_resolver` - The type resolver containing type registrations
    ///
    /// # Returns
    ///
    /// The u32 type ID assigned to this type during registration.
    ///
    /// # Errors
    ///
    /// Returns `Error` if the type is not registered in the resolver.
    ///
    /// # Default Implementation
    ///
    /// Queries the resolver using `std::any::TypeId::of::<Self>()`:
    ///
    /// ```rust,ignore
    /// fn fory_get_type_id(type_resolver: &TypeResolver) -> Result<u32, Error> {
    ///     Ok(type_resolver.get_type_info(&std::any::TypeId::of::<Self>())?.get_type_id())
    /// }
    /// ```
    #[inline(always)]
    fn fory_get_type_id(type_resolver: &TypeResolver) -> Result<u32, Error>
    where
        Self: Sized,
    {
        Ok(type_resolver
            .get_type_info(&std::any::TypeId::of::<Self>())?
            .get_type_id())
    }

    /// Gets the dynamic Fory type ID for this specific instance.
    ///
    /// Unlike [`fory_static_type_id`](Self::fory_static_type_id) which returns a
    /// compile-time constant, this method returns the runtime type ID for the actual
    /// concrete type of this instance.
    ///
    /// This is crucial for polymorphic types where the concrete type varies:
    /// - For `Box<dyn Trait>`: Returns type ID of the concrete type inside
    /// - For `Rc<dyn Any>`: Returns type ID of the actual value
    /// - For monomorphic types: Same as `fory_get_type_id`
    ///
    /// # Parameters
    ///
    /// * `type_resolver` - Type resolver to look up the concrete type's ID
    ///
    /// # Returns
    ///
    /// The u32 type ID of the concrete runtime type.
    ///
    /// # Errors
    ///
    /// Returns `Error` if the concrete type is not registered.
    ///
    /// # Implementation
    ///
    /// Monomorphic types:
    /// ```rust,ignore
    /// fn fory_type_id_dyn(&self, type_resolver: &TypeResolver) -> Result<u32, Error> {
    ///     Self::fory_get_type_id(type_resolver)
    /// }
    /// ```
    ///
    /// Polymorphic types:
    /// ```rust,ignore
    /// impl Serializer for Box<dyn Any> {
    ///     fn fory_type_id_dyn(&self, type_resolver: &TypeResolver) -> Result<u32, Error> {
    ///         let concrete_type_id = (**self).type_id();
    ///         type_resolver.get_fory_type_id(concrete_type_id)
    ///             .ok_or_else(|| Error::type_error("Type not registered"))
    ///     }
    /// }
    /// ```
    fn fory_type_id_dyn(&self, type_resolver: &TypeResolver) -> Result<u32, Error>;

    /// Returns the concrete `std::any::TypeId` for this instance.
    ///
    /// For monomorphic types, this returns `TypeId::of::<Self>()`. For polymorphic types
    /// (trait objects), this returns the `TypeId` of the concrete type behind the trait.
    ///
    /// # Default Implementation
    ///
    /// Returns the type ID of `Self`:
    ///
    /// ```rust,ignore
    /// fn fory_concrete_type_id(&self) -> std::any::TypeId {
    ///     std::any::TypeId::of::<Self>()
    /// }
    /// ```
    ///
    /// # Override Pattern
    ///
    /// Trait objects override to return the concrete type:
    ///
    /// ```rust,ignore
    /// impl Serializer for Box<dyn MyTrait> {
    ///     fn fory_concrete_type_id(&self) -> std::any::TypeId {
    ///         (**self).as_any().type_id()
    ///     }
    /// }
    /// ```
    #[inline(always)]
    fn fory_concrete_type_id(&self) -> std::any::TypeId {
        std::any::TypeId::of::<Self>()
    }

    /// Returns the estimated maximum serialized size for this type in bytes.
    ///
    /// This is used to pre-allocate buffer space to avoid reallocations during serialization,
    /// which significantly improves performance. The value should be a reasonable upper bound
    /// but doesn't need to be exact.
    ///
    /// # Return Value
    ///
    /// - **0** (default): No size hint available
    /// - **Fixed size**: For types with constant size (primitives, fixed arrays)
    /// - **Variable size**: Reasonable estimate for variable-size types
    ///
    /// # Examples
    ///
    /// Primitives:
    /// ```rust,ignore
    /// impl Serializer for i64 {
    ///     fn fory_reserved_space() -> usize { 8 }
    /// }
    /// ```
    ///
    /// Strings:
    /// ```rust,ignore
    /// impl Serializer for String {
    ///     fn fory_reserved_space() -> usize {
    ///         std::mem::size_of::<i32>()  // Length prefix
    ///     }
    /// }
    /// ```
    ///
    /// Shared references (avoid infinite recursion):
    /// ```rust,ignore
    /// impl<T> Serializer for Rc<T> {
    ///     fn fory_reserved_space() -> usize {
    ///         4  // Just ref tracking overhead, not inner type
    ///     }
    /// }
    /// ```
    ///
    /// # Default
    ///
    /// Returns 0 (no hint).
    #[inline(always)]
    fn fory_reserved_space() -> usize
    where
        Self: Sized,
    {
        0
    }

    /// Converts this value to a `&dyn Any` reference for downcasting.
    ///
    /// This enables runtime type checking and downcasting, which is essential for:
    /// - Polymorphic serialization (trait objects need to serialize concrete type)
    /// - Type validation during deserialization
    /// - Generic collection handling
    ///
    /// # Implementation
    ///
    /// For concrete types, simply return `self`:
    ///
    /// ```rust,ignore
    /// fn as_any(&self) -> &dyn Any {
    ///     self
    /// }
    /// ```
    ///
    /// For trait objects, forward to the concrete type:
    ///
    /// ```rust,ignore
    /// impl Serializer for Box<dyn MyTrait> {
    ///     fn as_any(&self) -> &dyn Any {
    ///         (**self).as_any()  // Delegate to concrete type
    ///     }
    /// }
    /// ```
    fn as_any(&self) -> &dyn Any;
}

pub trait StructSerializer: Serializer + 'static {
    #[allow(unused_variables)]
    fn fory_fields_info(type_resolver: &TypeResolver) -> Result<Vec<FieldInfo>, Error> {
        Ok(Vec::default())
    }

    fn fory_type_index() -> u32 {
        unimplemented!()
    }

    fn fory_actual_type_id(type_id: u32, register_by_name: bool, compatible: bool) -> u32 {
        struct_::actual_type_id(type_id, register_by_name, compatible)
    }

    fn fory_get_sorted_field_names() -> &'static [&'static str] {
        &[]
    }

    // only used by struct
    fn fory_read_compatible(
        context: &mut ReadContext,
        type_info: Rc<TypeInfo>,
    ) -> Result<Self, Error>
    where
        Self: Sized;
}
