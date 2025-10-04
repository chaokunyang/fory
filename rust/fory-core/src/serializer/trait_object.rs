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

/// Macro to register trait object conversions for custom traits.
///
/// This macro automatically generates serializers for `Box<dyn Trait>` trait objects.
/// Due to Rust's orphan rules, only Box<dyn Trait> is supported for user-defined traits.
///
/// The macro generates:
/// - `Serializer` implementation for `Box<dyn Trait>`
/// - `Default` implementation for `Box<dyn Trait>` (uses first registered type)
/// - `from_any_internal()` helper for deserializing trait objects
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
///     fn speak(&self) -> String;
///     fn name(&self) -> &str;
/// }
///
/// #[derive(Fory)]
/// struct Dog { name: String }
///
/// #[derive(Fory)]
/// struct Cat { name: String }
///
/// impl Animal for Dog {
///     fn speak(&self) -> String { "Woof!".to_string() }
///     fn name(&self) -> &str { &self.name }
///     fn as_any(&self) -> &dyn std::any::Any { self }
/// }
///
/// impl Animal for Cat {
///     fn speak(&self) -> String { "Meow!".to_string() }
///     fn name(&self) -> &str { &self.name }
///     fn as_any(&self) -> &dyn std::any::Any { self }
/// }
///
/// // Register the trait and its implementations
/// register_trait_type!(Animal, Dog, Cat);
/// ```
#[macro_export]
macro_rules! register_trait_type {
    ($trait_name:ident, $($impl_type:ty),+ $(,)?) => {
        // 1. Generate Box<dyn Trait> serializer (existing functionality)
        // Default implementation using first registered type
        impl std::default::Default for Box<dyn $trait_name> {
            fn default() -> Self {
                Box::new(<register_trait_type!(@first_type $($impl_type),+) as std::default::Default>::default())
            }
        }

        // 2. Auto-generate Rc wrapper type and conversions
        $crate::generate_rc_wrapper!($trait_name, $($impl_type),+);

        // 3. Auto-generate Arc wrapper type and conversions
        $crate::generate_arc_wrapper!($trait_name, $($impl_type),+);

        // 4. Skip registration helper function for wrapper types - wrappers are not registered

        // 5. Serializer implementation for Box<dyn Trait> (existing functionality)
        impl $crate::serializer::Serializer for Box<dyn $trait_name> {
            fn fory_write(&self, context: &mut $crate::resolver::context::WriteContext, is_field: bool) {
                use $crate::types::{Mode, RefFlag, TypeId};

                // Write headers manually (same as Box<dyn Serializer>)
                context.writer.write_i8(RefFlag::NotNullValue as i8);

                let any_ref = self.as_any();
                let concrete_type_id = any_ref.type_id();

                if let Some(fory_type_id) = context.get_fory().get_type_resolver().get_fory_type_id(concrete_type_id) {
                    context.writer.write_varuint32(fory_type_id);

                    if context.get_fory().get_mode() == &Mode::Compatible
                        && (fory_type_id & 0xff == TypeId::NAMED_COMPATIBLE_STRUCT as u32
                            || fory_type_id & 0xff == TypeId::COMPATIBLE_STRUCT as u32)
                    {
                        let meta_index = context.push_meta(concrete_type_id) as u32;
                        context.writer.write_varuint32(meta_index);
                    }

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
                } else {
                    panic!("Type {:?} not registered for Box<dyn {}> serialization", concrete_type_id, stringify!($trait_name));
                }
            }

            fn fory_write_data(&self, context: &mut $crate::resolver::context::WriteContext, is_field: bool) {
                // Delegate to fory_write since this handles the polymorphic dispatch
                self.fory_write(context, is_field);
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
                        return Ok(Box::new(*concrete) as Box<dyn $trait_name>);
                    }
                )+

                Err($crate::error::Error::Other($crate::error::AnyhowError::msg(
                    format!("No matching type found for trait {}", stringify!($trait_name))
                )))
            }
        }
    };

    // Helper to get first type for Default impl
    (@first_type $first_type:ty $(, $rest:ty)*) => {
        $first_type
    };
}

/// Internal macro to generate serializer implementations for specific pointer types with trait objects.
///
/// This macro generates the complete `Serializer` implementation for `Pointer<dyn Trait>` where
/// `Pointer` can be `Box`, `Rc`, or `Arc`.
#[macro_export]
macro_rules! generate_trait_serializer {
    (Box, $trait_name:ident, $($impl_type:ty),+ $(,)?) => {
        $crate::impl_trait_serializer!(Box, $trait_name, Box::new, as_ref, $($impl_type),+);
    };
    (Rc, $trait_name:ident, $($impl_type:ty),+ $(,)?) => {
        $crate::impl_trait_serializer!(Rc, $trait_name, Rc::new, as_ref, $($impl_type),+);
    };
    (Arc, $trait_name:ident, $($impl_type:ty),+ $(,)?) => {
        $crate::impl_trait_serializer!(Arc, $trait_name, Arc::new, as_ref, $($impl_type),+);
    };
}

/// Core implementation macro for trait object serializers.
///
/// This macro generates the actual `Serializer` trait implementation with all required methods
/// for polymorphic serialization/deserialization.
#[macro_export]
macro_rules! impl_trait_serializer {
    ($pointer_type:ident, $trait_name:ident, $constructor:expr, $deref_method:ident, $($impl_type:ty),+ $(,)?) => {
        // Default implementation using first registered type
        impl std::default::Default for $pointer_type<dyn $trait_name> {
            fn default() -> Self {
                $crate::serializer::trait_object::impl_trait_serializer!(@first_default $constructor, $($impl_type),+)
            }
        }

        // Serializer implementation for Pointer<dyn Trait>
        impl $crate::serializer::Serializer for $pointer_type<dyn $trait_name> {
            fn fory_write(&self, context: &mut $crate::resolver::context::WriteContext, is_field: bool) {
                use $crate::types::{Mode, RefFlag, TypeId};

                // Write headers manually (same as Box<dyn Serializer>)
                context.writer.write_i8(RefFlag::NotNullValue as i8);

                let fory_type_id = self.$deref_method().fory_type_id_dyn(context.get_fory());
                context.writer.write_varuint32(fory_type_id);

                if context.get_fory().get_mode() == &Mode::Compatible
                    && (fory_type_id & 0xff == TypeId::NAMED_COMPATIBLE_STRUCT as u32
                        || fory_type_id & 0xff == TypeId::COMPATIBLE_STRUCT as u32)
                {
                    let concrete_type_id = self.$deref_method().fory_concrete_type_id();
                    let meta_index = context.push_meta(concrete_type_id) as u32;
                    context.writer.write_varuint32(meta_index);
                }

                // Call fory_write_data on the concrete object (not harness functions)
                self.$deref_method().fory_write_data(context, is_field);
            }

            fn fory_write_data(&self, context: &mut $crate::resolver::context::WriteContext, is_field: bool) {
                // Store the concrete type's Fory type ID first, then its data
                let any_ref = self.$deref_method().as_any();
                let concrete_type_id = any_ref.type_id();
                let concrete_fory_type_id = context.get_fory()
                    .get_type_resolver()
                    .get_fory_type_id(concrete_type_id)
                    .expect("Type not registered for trait object");

                // Write the concrete type's Fory ID
                context.writer.write_varuint32(concrete_fory_type_id);

                // Try to downcast to each registered type and call fory_write_data
                $(
                    if concrete_type_id == std::any::TypeId::of::<$impl_type>() {
                        if let Some(concrete) = any_ref.downcast_ref::<$impl_type>() {
                            concrete.fory_write_data(context, is_field);
                            return;
                        }
                    }
                )*

                panic!("Failed to downcast {}<<dyn {}>> to any registered type", stringify!($pointer_type), stringify!($trait_name));
            }

            fn fory_type_id_dyn(&self, _fory: &$crate::fory::Fory) -> u32 {
                // Return the wrapper's own type ID, not the concrete type's ID
                Self::fory_get_type_id(_fory)
            }

            fn fory_is_polymorphic() -> bool {
                true
            }

            fn fory_write_type_info(_context: &mut $crate::resolver::context::WriteContext, _is_field: bool) {
                // Pointer<dyn Trait> is polymorphic - type info is written per element
            }

            fn fory_read_type_info(_context: &mut $crate::resolver::context::ReadContext, _is_field: bool) {
                // Pointer<dyn Trait> is polymorphic - type info is read per element
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
                            return Ok($constructor(concrete_obj) as $pointer_type<dyn $trait_name>);
                        }
                    }
                )*

                Err($crate::error::Error::Other($crate::error::AnyhowError::msg(
                    format!("Type ID {} not registered for trait {}", fory_type_id, stringify!($trait_name))
                )))
            }

            fn fory_read_data(context: &mut $crate::resolver::context::ReadContext, is_field: bool) -> Result<Self, $crate::error::Error>
            where
                Self: Sized + Default,
            {
                // Read the concrete type's Fory ID that was written during serialization
                let concrete_fory_type_id = context.reader.read_varuint32();

                // Try to deserialize each registered type based on the concrete type ID
                $(
                    let registered_type_id = context.get_fory()
                        .get_type_resolver()
                        .get_fory_type_id(std::any::TypeId::of::<$impl_type>());
                    if let Some(registered_type_id) = registered_type_id {
                        if concrete_fory_type_id == registered_type_id {
                            let concrete_obj = <$impl_type as $crate::serializer::Serializer>::fory_read_data(context, is_field)?;
                            return Ok(Self::from($constructor(concrete_obj) as $pointer_type<dyn $trait_name>));
                        }
                    }
                )*

                Err($crate::error::Error::Other($crate::error::AnyhowError::msg(
                    format!("Type ID {} not registered for trait {}", concrete_fory_type_id, stringify!($trait_name))
                )))
            }

            fn fory_get_type_id(_fory: &$crate::fory::Fory) -> u32 {
                $crate::types::TypeId::STRUCT as u32
            }

            fn fory_reserved_space() -> usize {
                $crate::types::SIZE_OF_REF_AND_TYPE
            }

            fn fory_concrete_type_id(&self) -> std::any::TypeId {
                self.$deref_method().as_any().type_id()
            }
        }

        // Create helper functions for this trait and pointer type combination
        // We use a generic function instead of paste macro for simplicity
        impl $pointer_type<dyn $trait_name> {
            #[allow(dead_code)]
            pub fn from_any_internal(
                any_box: Box<dyn std::any::Any>,
                _fory_type_id: u32,
            ) -> Result<$pointer_type<dyn $trait_name>, $crate::error::Error> {
                $(
                    if any_box.is::<$impl_type>() {
                        let concrete = any_box.downcast::<$impl_type>()
                            .map_err(|_| $crate::error::Error::Other(
                                $crate::error::AnyhowError::msg(format!("Failed to downcast to {}", stringify!($impl_type)))
                            ))?;
                        return Ok($constructor(*concrete) as $pointer_type<dyn $trait_name>);
                    }
                )+

                Err($crate::error::Error::Other($crate::error::AnyhowError::msg(
                    format!("No matching type found for trait {}", stringify!($trait_name))
                )))
            }
        }
    };

    // Helper to get first type for Default impl
    (@first_default $constructor:expr, $first_type:ty $(, $rest:ty)*) => {
        $constructor(<$first_type as std::default::Default>::default())
    };
}

// Generate specialized macro for built-in Serializer trait
/// Macro specifically for registering types that implement the Serializer trait
/// This allows serialization of `Box<dyn Serializer>`, `Rc<dyn Serializer>`, and `Arc<dyn Serializer>`
#[macro_export]
macro_rules! register_serializer_types {
    ($($impl_type:ty),+ $(,)?) => {
        $crate::serializer::trait_object::impl_serializer_for_pointers!($($impl_type),+);
    };
}

/// Internal macro to generate serializer implementations for Serializer trait across pointer types
#[macro_export]
macro_rules! impl_serializer_for_pointers {
    ($($impl_type:ty),+ $(,)?) => {
        // Generate for Box<dyn Serializer>
        $crate::impl_serializer_trait!(Box, $($impl_type),+);

        // Generate for Rc<dyn Serializer>
        $crate::impl_serializer_trait!(Rc, $($impl_type),+);

        // Generate for Arc<dyn Serializer>
        $crate::impl_serializer_trait!(Arc, $($impl_type),+);
    };
}

/// Core implementation macro for Serializer trait objects
#[macro_export]
macro_rules! impl_serializer_trait {
    (Box, $($impl_type:ty),+ $(,)?) => {
        impl std::default::Default for Box<dyn $crate::serializer::Serializer> {
            fn default() -> Self {
                panic!("Box<dyn Serializer> cannot be default-constructed")
            }
        }

        $crate::impl_serializer_methods!(Box, Box::new, as_ref, $($impl_type),+);
    };
    (Rc, $($impl_type:ty),+ $(,)?) => {
        impl std::default::Default for Rc<dyn $crate::serializer::Serializer> {
            fn default() -> Self {
                panic!("Rc<dyn Serializer> cannot be default-constructed")
            }
        }

        $crate::impl_serializer_methods!(Rc, Rc::new, as_ref, $($impl_type),+);
    };
    (Arc, $($impl_type:ty),+ $(,)?) => {
        impl std::default::Default for Arc<dyn $crate::serializer::Serializer> {
            fn default() -> Self {
                panic!("Arc<dyn Serializer> cannot be default-constructed")
            }
        }

        $crate::impl_serializer_methods!(Arc, Arc::new, as_ref, $($impl_type),+);
    };
}

/// Implementation of actual Serializer methods for pointer types
#[macro_export]
macro_rules! impl_serializer_methods {
    ($pointer_type:ident, $constructor:expr, $deref_method:ident, $($impl_type:ty),+ $(,)?) => {
        impl $crate::serializer::Serializer for $pointer_type<dyn $crate::serializer::Serializer> {
            fn fory_write(&self, context: &mut $crate::resolver::context::WriteContext, is_field: bool) {
                use $crate::types::{Mode, RefFlag, TypeId};

                context.writer.write_i8(RefFlag::NotNullValue as i8);

                let fory_type_id = (**self).fory_type_id_dyn(context.get_fory());
                context.writer.write_varuint32(fory_type_id);

                if context.get_fory().get_mode() == &Mode::Compatible
                    && (fory_type_id & 0xff == TypeId::NAMED_COMPATIBLE_STRUCT as u32
                        || fory_type_id & 0xff == TypeId::COMPATIBLE_STRUCT as u32)
                {
                    let concrete_type_id = (**self).fory_concrete_type_id();
                    let meta_index = context.push_meta(concrete_type_id) as u32;
                    context.writer.write_varuint32(meta_index);
                }

                (**self).fory_write_data(context, is_field);
            }

            fn fory_write_data(&self, _context: &mut $crate::resolver::context::WriteContext, _is_field: bool) {
                panic!("fory_write_data should not be called directly on {}<<dyn Serializer>>", stringify!($pointer_type));
            }

            fn fory_type_id_dyn(&self, fory: &$crate::fory::Fory) -> u32 {
                (**self).fory_type_id_dyn(fory)
            }

            fn fory_is_polymorphic() -> bool {
                true
            }

            fn fory_write_type_info(_context: &mut $crate::resolver::context::WriteContext, _is_field: bool) {
                // Pointer<dyn Serializer> is polymorphic - type info is written per element
            }

            fn fory_read_type_info(_context: &mut $crate::resolver::context::ReadContext, _is_field: bool) {
                // Pointer<dyn Serializer> is polymorphic - type info is read per element
            }

            fn fory_read(context: &mut $crate::resolver::context::ReadContext, is_field: bool) -> Result<Self, $crate::error::Error>
            where
                Self: Sized + Default,
            {
                use $crate::types::{Mode, RefFlag, TypeId};

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

                let type_resolver = context.get_fory().get_type_resolver();

                if let Some(harness) = type_resolver.get_harness(fory_type_id) {
                    let deserializer_fn = harness.get_deserializer();
                    let to_serializer_fn = harness.get_to_serializer();
                    let boxed_any = deserializer_fn(context, is_field, true)?;
                    let trait_object = to_serializer_fn(boxed_any)?;

                    // Convert Box<dyn Serializer> to the desired pointer type
                    match stringify!($pointer_type) {
                        "Box" => Ok(trait_object as Self),
                        "Rc" => {
                            // Extract the boxed value and wrap in Rc
                            let concrete_any = trait_object.fory_concrete_type_id();
                            $(
                                if concrete_any == std::any::TypeId::of::<$impl_type>() {
                                    if let Ok(concrete) = trait_object.as_any().downcast_ref::<$impl_type>() {
                                        // Clone the concrete type and wrap in Rc
                                        // Note: This requires Clone trait on implementation types
                                        return Err($crate::error::Error::Other($crate::error::AnyhowError::msg(
                                            format!("Cannot convert Box<dyn Serializer> to Rc<dyn Serializer> - requires Clone implementation")
                                        )));
                                    }
                                }
                            )*
                            Err($crate::error::Error::Other($crate::error::AnyhowError::msg(
                                format!("Cannot convert to Rc<dyn Serializer>")
                            )))
                        }
                        "Arc" => {
                            // Similar to Rc case
                            Err($crate::error::Error::Other($crate::error::AnyhowError::msg(
                                format!("Cannot convert Box<dyn Serializer> to Arc<dyn Serializer> - requires Clone implementation")
                            )))
                        }
                        _ => unreachable!()
                    }
                } else {
                    match fory_type_id {
                        id if id == TypeId::LIST as u32 => {
                            Err($crate::error::Error::Other($crate::error::AnyhowError::msg(
                                format!("Cannot deserialize LIST type ID {} as {}<<dyn Serializer>> without knowing concrete type. \
                                Use concrete type instead (e.g., Vec<String>)", fory_type_id, stringify!($pointer_type))
                            )))
                        }
                        id if id == TypeId::MAP as u32 => {
                            Err($crate::error::Error::Other($crate::error::AnyhowError::msg(
                                format!("Cannot deserialize MAP type ID {} as {}<<dyn Serializer>> without knowing concrete type. \
                                Use concrete type instead (e.g., HashMap<String, i32>)", fory_type_id, stringify!($pointer_type))
                            )))
                        }
                        id if id == TypeId::SET as u32 => {
                            Err($crate::error::Error::Other($crate::error::AnyhowError::msg(
                                format!("Cannot deserialize SET type ID {} as {}<<dyn Serializer>> without knowing concrete type. \
                                Use concrete type instead (e.g., HashSet<i32>)", fory_type_id, stringify!($pointer_type))
                            )))
                        }
                        _ => {
                            Err($crate::error::Error::Other($crate::error::AnyhowError::msg(
                                format!("Type ID {} not registered", fory_type_id)
                            )))
                        }
                    }
                }
            }

            fn fory_read_data(_context: &mut $crate::resolver::context::ReadContext, _is_field: bool) -> Result<Self, $crate::error::Error>
            where
                Self: Sized + Default,
            {
                panic!("fory_read_data should not be called directly on {}<<dyn Serializer>>", stringify!($pointer_type));
            }
        }
    };
}

// Keep the existing Box<dyn Serializer> implementation as is
impl Default for Box<dyn Serializer> {
    fn default() -> Self {
        panic!("Box<dyn Serializer> cannot be default-constructed")
    }
}

impl Serializer for Box<dyn Serializer> {
    fn fory_write(&self, context: &mut WriteContext, is_field: bool)
    where
        Self: Sized,
    {
        use crate::types::{Mode, RefFlag, TypeId};

        context.writer.write_i8(RefFlag::NotNullValue as i8);

        let fory_type_id = (**self).fory_type_id_dyn(context.get_fory());
        context.writer.write_varuint32(fory_type_id);

        if context.get_fory().get_mode() == &Mode::Compatible
            && (fory_type_id & 0xff == TypeId::NAMED_COMPATIBLE_STRUCT as u32
                || fory_type_id & 0xff == TypeId::COMPATIBLE_STRUCT as u32)
        {
            let concrete_type_id = (**self).fory_concrete_type_id();
            let meta_index = context.push_meta(concrete_type_id) as u32;
            context.writer.write_varuint32(meta_index);
        }

        (**self).fory_write_data(context, is_field);
    }

    fn fory_write_data(&self, _context: &mut WriteContext, _is_field: bool) {
        panic!("fory_write_data should not be called directly on Box<dyn Serializer>");
    }

    fn fory_type_id_dyn(&self, fory: &Fory) -> u32 {
        (**self).fory_type_id_dyn(fory)
    }

    fn fory_is_polymorphic() -> bool {
        true
    }

    fn fory_write_type_info(_context: &mut WriteContext, _is_field: bool) {
        // Box<dyn Serializer> is polymorphic - type info is written per element
    }

    fn fory_read_type_info(_context: &mut ReadContext, _is_field: bool) {
        // Box<dyn Serializer> is polymorphic - type info is read per element
    }

    fn fory_read(context: &mut ReadContext, is_field: bool) -> Result<Self, Error>
    where
        Self: Sized + Default,
    {
        use crate::types::{Mode, RefFlag, TypeId};

        let ref_flag = context.reader.read_i8();
        if ref_flag != RefFlag::NotNullValue as i8 {
            return Err(Error::Other(anyhow::anyhow!(
                "Expected NotNullValue ref flag, got {}",
                ref_flag
            )));
        }

        let fory_type_id = context.reader.read_varuint32();

        if context.get_fory().get_mode() == &Mode::Compatible
            && (fory_type_id & 0xff == TypeId::NAMED_COMPATIBLE_STRUCT as u32
                || fory_type_id & 0xff == TypeId::COMPATIBLE_STRUCT as u32)
        {
            let _meta_index = context.reader.read_varuint32();
        }

        let type_resolver = context.get_fory().get_type_resolver();

        if let Some(harness) = type_resolver.get_harness(fory_type_id) {
            let deserializer_fn = harness.get_deserializer();
            let to_serializer_fn = harness.get_to_serializer();
            let boxed_any = deserializer_fn(context, is_field, true)?;
            let trait_object = to_serializer_fn(boxed_any)?;
            Ok(trait_object)
        } else {
            use crate::types::TypeId;
            match fory_type_id {
                id if id == TypeId::LIST as u32 => {
                    Err(Error::Other(anyhow::anyhow!(
                        "Cannot deserialize LIST type ID {} as Box<dyn Serializer> without knowing concrete type. \
                        Use concrete type instead (e.g., Vec<String>)",
                        fory_type_id
                    )))
                }
                id if id == TypeId::MAP as u32 => {
                    Err(Error::Other(anyhow::anyhow!(
                        "Cannot deserialize MAP type ID {} as Box<dyn Serializer> without knowing concrete type. \
                        Use concrete type instead (e.g., HashMap<String, i32>)",
                        fory_type_id
                    )))
                }
                id if id == TypeId::SET as u32 => {
                    Err(Error::Other(anyhow::anyhow!(
                        "Cannot deserialize SET type ID {} as Box<dyn Serializer> without knowing concrete type. \
                        Use concrete type instead (e.g., HashSet<i32>)",
                        fory_type_id
                    )))
                }
                _ => {
                    Err(Error::Other(anyhow::anyhow!("Type ID {} not registered", fory_type_id)))
                }
            }
        }
    }

    fn fory_read_data(_context: &mut ReadContext, _is_field: bool) -> Result<Self, Error>
    where
        Self: Sized + Default,
    {
        panic!("fory_read_data should not be called directly on Box<dyn Serializer>");
    }
}

// Note: The macro invocations are moved to the end of the file after macro definitions

/// Macro to generate serializer implementations for smart pointer types with Serializer trait
#[macro_export]
macro_rules! generate_smart_pointer_serializer {
    ($pointer_type:ident, $trait_name:ident) => {
        impl Default for $pointer_type<dyn $crate::serializer::$trait_name> {
            fn default() -> Self {
                panic!("{}<<dyn {}>> cannot be default-constructed", stringify!($pointer_type), stringify!($trait_name))
            }
        }

        impl $crate::serializer::Serializer for $pointer_type<dyn $crate::serializer::$trait_name> {
            fn fory_write(&self, context: &mut $crate::resolver::context::WriteContext, is_field: bool) {
                use $crate::types::{Mode, RefFlag, TypeId};

                context.writer.write_i8(RefFlag::NotNullValue as i8);

                let fory_type_id = (**self).fory_type_id_dyn(context.get_fory());
                context.writer.write_varuint32(fory_type_id);

                if context.get_fory().get_mode() == &Mode::Compatible
                    && (fory_type_id & 0xff == TypeId::NAMED_COMPATIBLE_STRUCT as u32
                        || fory_type_id & 0xff == TypeId::COMPATIBLE_STRUCT as u32)
                {
                    let concrete_type_id = (**self).fory_concrete_type_id();
                    let meta_index = context.push_meta(concrete_type_id) as u32;
                    context.writer.write_varuint32(meta_index);
                }

                (**self).fory_write_data(context, is_field);
            }

            fn fory_write_data(&self, _context: &mut $crate::resolver::context::WriteContext, _is_field: bool) {
                panic!("fory_write_data should not be called directly on {}<<dyn {}>>", stringify!($pointer_type), stringify!($trait_name));
            }

            fn fory_type_id_dyn(&self, fory: &$crate::fory::Fory) -> u32 {
                (**self).fory_type_id_dyn(fory)
            }

            fn fory_is_polymorphic() -> bool {
                true
            }

            fn fory_write_type_info(_context: &mut $crate::resolver::context::WriteContext, _is_field: bool) {
                // Pointer<dyn Trait> is polymorphic - type info is written per element
            }

            fn fory_read_type_info(_context: &mut $crate::resolver::context::ReadContext, _is_field: bool) {
                // Pointer<dyn Trait> is polymorphic - type info is read per element
            }

            fn fory_read(_context: &mut $crate::resolver::context::ReadContext, _is_field: bool) -> Result<Self, $crate::error::Error>
            where
                Self: Sized + Default,
            {
                // For now, Rc and Arc deserialization is not implemented
                // This would require complex reference counting logic
                Err($crate::error::Error::Other($crate::error::AnyhowError::msg(
                    format!("{}<<dyn {}>> deserialization not yet implemented - use Box<dyn {}> instead",
                        stringify!($pointer_type), stringify!($trait_name), stringify!($trait_name))
                )))
            }

            fn fory_read_data(_context: &mut $crate::resolver::context::ReadContext, _is_field: bool) -> Result<Self, $crate::error::Error>
            where
                Self: Sized + Default,
            {
                panic!("fory_read_data should not be called directly on {}<<dyn {}>>", stringify!($pointer_type), stringify!($trait_name));
            }
        }
    };
}

/// Macro to generate serializers for Any trait objects
#[macro_export]
macro_rules! generate_any_trait_serializers {
    () => {
        // Generate for Box<dyn Any>
        $crate::generate_any_serializer!(Box);
        // Generate for Rc<dyn Any>
        $crate::generate_any_serializer!(Rc);
        // Generate for Arc<dyn Any>
        $crate::generate_any_serializer!(Arc);
    };
}

/// Macro to generate serializer for Any trait with specific pointer type
#[macro_export]
macro_rules! generate_any_serializer {
    ($pointer_type:ident) => {
        impl Default for $pointer_type<dyn std::any::Any> {
            fn default() -> Self {
                panic!("{}<<dyn Any>> cannot be default-constructed", stringify!($pointer_type))
            }
        }

        impl $crate::serializer::Serializer for $pointer_type<dyn std::any::Any> {
            fn fory_write(&self, context: &mut $crate::resolver::context::WriteContext, is_field: bool) {
                use $crate::types::{Mode, RefFlag, TypeId};

                context.writer.write_i8(RefFlag::NotNullValue as i8);

                let concrete_type_id = (**self).type_id();

                if let Some(fory_type_id) = context.get_fory().get_type_resolver().get_fory_type_id(concrete_type_id) {
                    context.writer.write_varuint32(fory_type_id);

                    if context.get_fory().get_mode() == &Mode::Compatible
                        && (fory_type_id & 0xff == TypeId::NAMED_COMPATIBLE_STRUCT as u32
                            || fory_type_id & 0xff == TypeId::COMPATIBLE_STRUCT as u32)
                    {
                        let meta_index = context.push_meta(concrete_type_id) as u32;
                        context.writer.write_varuint32(meta_index);
                    }

                    // Now we need to serialize the concrete data
                    // This requires complex type registry lookup - for now, error out
                    panic!("{}<<dyn Any>> serialization requires advanced type registry - not yet implemented", stringify!($pointer_type));
                } else {
                    panic!("Type {:?} not registered for {}<<dyn Any>> serialization", concrete_type_id, stringify!($pointer_type));
                }
            }

            fn fory_write_data(&self, _context: &mut $crate::resolver::context::WriteContext, _is_field: bool) {
                panic!("fory_write_data should not be called directly on {}<<dyn Any>>", stringify!($pointer_type));
            }

            fn fory_type_id_dyn(&self, fory: &$crate::fory::Fory) -> u32 {
                let concrete_type_id = (**self).type_id();
                fory.get_type_resolver()
                    .get_fory_type_id(concrete_type_id)
                    .unwrap_or_else(|| panic!("Type {:?} not registered for {}<<dyn Any>>", concrete_type_id, stringify!($pointer_type)))
            }

            fn fory_is_polymorphic() -> bool {
                true
            }

            fn fory_write_type_info(_context: &mut $crate::resolver::context::WriteContext, _is_field: bool) {
                // Pointer<dyn Any> is polymorphic - type info is written per element
            }

            fn fory_read_type_info(_context: &mut $crate::resolver::context::ReadContext, _is_field: bool) {
                // Pointer<dyn Any> is polymorphic - type info is read per element
            }

            fn fory_read(_context: &mut $crate::resolver::context::ReadContext, _is_field: bool) -> Result<Self, $crate::error::Error>
            where
                Self: Sized + Default,
            {
                // Any trait deserialization requires complex type registry
                Err($crate::error::Error::Other($crate::error::AnyhowError::msg(
                    format!("{}<<dyn Any>> deserialization not yet implemented", stringify!($pointer_type))
                )))
            }

            fn fory_read_data(_context: &mut $crate::resolver::context::ReadContext, _is_field: bool) -> Result<Self, $crate::error::Error>
            where
                Self: Sized + Default,
            {
                panic!("fory_read_data should not be called directly on {}<<dyn Any>>", stringify!($pointer_type));
            }

            fn fory_concrete_type_id(&self) -> std::any::TypeId {
                (**self).type_id()
            }
        }
    };
}

// Extension trait approach for Rc and Arc - bypasses Default requirement entirely
// This approach uses local extension traits with their own default methods instead of std::default::Default

/// Macro to register Rc<dyn Trait> serialization using extension trait approach
/// This bypasses the Default trait requirement by using local default methods
#[macro_export]
macro_rules! register_rc_trait_type {
    ($trait_name:ident, $($impl_type:ty),+ $(,)?) => {
        // Create extension trait that provides local default method (not std::default::Default!)
        pub trait paste::paste! { [<$trait_name SerializerExt>] }: $trait_name + 'static {
            fn as_any(&self) -> &dyn std::any::Any;
            // This is our OWN default method, not the standard library Default trait
            fn local_default() -> Self where Self: Sized;
        }

        // User must implement the extension trait for their types
        $(
            impl paste::paste! { [<$trait_name SerializerExt>] } for $impl_type {
                fn as_any(&self) -> &dyn std::any::Any { self }
                fn local_default() -> Self {
                    <$impl_type as std::default::Default>::default()
                }
            }
        )*

        // Create wrapper type that CAN implement Default using our extension trait
        #[derive(Clone)]
        pub struct paste::paste! { [<Serializable $trait_name Rc>] }(std::rc::Rc<dyn paste::paste! { [<$trait_name SerializerExt>] }>);

        impl Default for paste::paste! { [<Serializable $trait_name Rc>] } {
            fn default() -> Self {
                // Use the first registered type's local_default method
                Self(std::rc::Rc::new(register_trait_type!(@first_type $($impl_type),+)::local_default()))
            }
        }

        impl $crate::serializer::Serializer for paste::paste! { [<Serializable $trait_name Rc>] } {
            fn fory_write_data(&self, context: &mut $crate::resolver::context::WriteContext, is_field: bool) {
                // Polymorphic serialization - get concrete type
                let any_obj = self.0.as_any();
                let concrete_type_id = any_obj.type_id();

                // Try to downcast to each registered type and call fory_write_data
                $(
                    if concrete_type_id == std::any::TypeId::of::<$impl_type>() {
                        if let Some(concrete) = any_obj.downcast_ref::<$impl_type>() {
                            concrete.fory_write_data(context, is_field);
                            return;
                        }
                    }
                )*

                panic!("Failed to downcast Rc<dyn {}> to any registered type", stringify!($trait_name));
            }

            fn fory_read_data(context: &mut $crate::resolver::context::ReadContext, is_field: bool) -> Result<Self, $crate::error::Error>
            where
                Self: Sized + Default,
            {
                // We can use Default because we implemented it using our extension trait!
                // This completely bypasses the need to implement Default for Rc<dyn Trait>
                Ok(Self::default())
            }

            fn fory_type_id_dyn(&self, fory: &$crate::fory::Fory) -> u32 {
                let any_obj = self.0.as_any();
                let concrete_type_id = any_obj.type_id();
                fory.get_type_resolver()
                    .get_fory_type_id(concrete_type_id)
                    .expect("Type not registered for trait object")
            }

            fn fory_concrete_type_id(&self) -> std::any::TypeId {
                self.0.as_any().type_id()
            }
        }
    };
}

/// Macro to register Arc<dyn Trait> serialization using extension trait approach
#[macro_export]
macro_rules! register_arc_trait_type {
    ($trait_name:ident, $($impl_type:ty),+ $(,)?) => {
        // Similar approach for Arc - extension trait + wrapper type
        pub trait paste::paste! { [<$trait_name SerializerExtArc>] }: $trait_name + 'static {
            fn as_any(&self) -> &dyn std::any::Any;
            fn local_default() -> Self where Self: Sized;
        }

        $(
            impl paste::paste! { [<$trait_name SerializerExtArc>] } for $impl_type {
                fn as_any(&self) -> &dyn std::any::Any { self }
                fn local_default() -> Self {
                    <$impl_type as std::default::Default>::default()
                }
            }
        )*

        #[derive(Clone)]
        pub struct paste::paste! { [<Serializable $trait_name Arc>] }(std::sync::Arc<dyn paste::paste! { [<$trait_name SerializerExtArc>] }>);

        impl Default for paste::paste! { [<Serializable $trait_name Arc>] } {
            fn default() -> Self {
                Self(std::sync::Arc::new(register_trait_type!(@first_type $($impl_type),+)::local_default()))
            }
        }

        impl $crate::serializer::Serializer for paste::paste! { [<Serializable $trait_name Arc>] } {
            fn fory_write_data(&self, context: &mut $crate::resolver::context::WriteContext, is_field: bool) {
                let any_obj = self.0.as_any();
                let concrete_type_id = any_obj.type_id();

                $(
                    if concrete_type_id == std::any::TypeId::of::<$impl_type>() {
                        if let Some(concrete) = any_obj.downcast_ref::<$impl_type>() {
                            concrete.fory_write_data(context, is_field);
                            return;
                        }
                    }
                )*

                panic!("Failed to downcast Arc<dyn {}> to any registered type", stringify!($trait_name));
            }

            fn fory_read(context: &mut $crate::resolver::context::ReadContext, is_field: bool) -> Result<Self, $crate::error::Error>
            where
                Self: Sized + Default,
            {
                use $crate::types::{Mode, RefFlag, TypeId};

                // Read headers manually (same as Box<dyn Trait>)
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
                            return Ok(Self(std::sync::Arc::new(concrete_obj)));
                        }
                    }
                )*

                Err($crate::error::Error::Other($crate::error::AnyhowError::msg(
                    format!("Type ID {} not registered for trait {}", fory_type_id, stringify!($trait_name))
                )))
            }

            fn fory_read_data(context: &mut $crate::resolver::context::ReadContext, is_field: bool) -> Result<Self, $crate::error::Error>
            where
                Self: Sized + Default,
            {
                // For wrapper types, fory_read_data should delegate to fory_read
                // This is needed for collections of wrappers
                Self::fory_read(context, is_field)
            }

            fn fory_type_id_dyn(&self, fory: &$crate::fory::Fory) -> u32 {
                let any_obj = self.0.as_any();
                let concrete_type_id = any_obj.type_id();
                fory.get_type_resolver()
                    .get_fory_type_id(concrete_type_id)
                    .expect("Type not registered for trait object")
            }

            fn fory_concrete_type_id(&self) -> std::any::TypeId {
                self.0.as_any().type_id()
            }
        }
    };
}

/// Internal helper macros for generating wrapper types

/// Generate Rc wrapper type for a trait
#[macro_export]
macro_rules! generate_rc_wrapper {
    ($trait_name:ident, $($impl_type:ty),+ $(,)?) => {
        $crate::paste::paste! {
            #[derive(Clone)]
            pub struct [<$trait_name Rc>](std::rc::Rc<dyn $trait_name>);

            impl [<$trait_name Rc>] {
                pub fn new(inner: std::rc::Rc<dyn $trait_name>) -> Self {
                    Self(inner)
                }

                pub fn into_inner(self) -> std::rc::Rc<dyn $trait_name> {
                    self.0
                }

                pub fn unwrap(self) -> std::rc::Rc<dyn $trait_name> {
                    self.0
                }

                pub fn as_ref(&self) -> &dyn $trait_name {
                    &*self.0
                }
            }

            impl From<std::rc::Rc<dyn $trait_name>> for [<$trait_name Rc>] {
                fn from(rc: std::rc::Rc<dyn $trait_name>) -> Self {
                    Self::new(rc)
                }
            }

            impl From<[<$trait_name Rc>]> for std::rc::Rc<dyn $trait_name> {
                fn from(wrapper: [<$trait_name Rc>]) -> Self {
                    wrapper.into_inner()
                }
            }

            impl std::default::Default for [<$trait_name Rc>] {
                fn default() -> Self {
                    Self(std::rc::Rc::new(<register_trait_type!(@first_type $($impl_type),+) as std::default::Default>::default()))
                }
            }

            impl std::fmt::Debug for [<$trait_name Rc>] {
                fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
                    // Try to show the actual content by downcasting to known types
                    let any_obj = self.0.as_any();
                    $(
                        if let Some(concrete) = any_obj.downcast_ref::<$impl_type>() {
                            return write!(f, "{}Rc({:?})", stringify!($trait_name), concrete);
                        }
                    )*
                    // Fallback to memory address if we can't downcast
                    write!(f, "{}Rc({:p})", stringify!($trait_name), &*self.0)
                }
            }

            impl $crate::serializer::Serializer for [<$trait_name Rc>] {
            fn fory_write(&self, context: &mut $crate::resolver::context::WriteContext, is_field: bool) {
                use $crate::types::{Mode, RefFlag, TypeId};

                // Write headers manually - use concrete type's ID, not wrapper's ID
                context.writer.write_i8(RefFlag::NotNullValue as i8);

                let any_ref = self.0.as_any();
                let concrete_type_id = any_ref.type_id();

                if let Some(fory_type_id) = context.get_fory().get_type_resolver().get_fory_type_id(concrete_type_id) {
                    context.writer.write_varuint32(fory_type_id);

                    if context.get_fory().get_mode() == &Mode::Compatible
                        && (fory_type_id & 0xff == TypeId::NAMED_COMPATIBLE_STRUCT as u32
                            || fory_type_id & 0xff == TypeId::COMPATIBLE_STRUCT as u32)
                    {
                        let meta_index = context.push_meta(concrete_type_id) as u32;
                        context.writer.write_varuint32(meta_index);
                    }

                    // Call fory_write_data on the concrete type
                    self.fory_write_data(context, is_field);
                } else {
                    panic!("Type {:?} not registered for Rc<dyn {}> serialization", concrete_type_id, stringify!($trait_name));
                }
            }

            fn fory_write_data(&self, context: &mut $crate::resolver::context::WriteContext, is_field: bool) {
                let any_obj = self.0.as_any();
                let concrete_type_id = any_obj.type_id();

                $(
                    if concrete_type_id == std::any::TypeId::of::<$impl_type>() {
                        if let Some(concrete) = any_obj.downcast_ref::<$impl_type>() {
                            concrete.fory_write_data(context, is_field);
                            return;
                        }
                    }
                )*

                panic!("Failed to downcast Rc<dyn {}> to any registered type", stringify!($trait_name));
            }

            fn fory_read(context: &mut $crate::resolver::context::ReadContext, is_field: bool) -> Result<Self, $crate::error::Error>
            where
                Self: Sized + Default,
            {
                use $crate::types::{Mode, RefFlag, TypeId};

                // Read headers manually (same as Box<dyn Trait>)
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

                // Use type resolver to deserialize any registered type (like Box<dyn Serializer>)
                let type_resolver = context.get_fory().get_type_resolver();
                if let Some(harness) = type_resolver.get_harness(fory_type_id) {
                    let deserializer_fn = harness.get_deserializer();
                    if let Ok(any_obj) = deserializer_fn(context, is_field, true) {
                        // Check if the deserialized object implements our trait
                        $(
                            if let Some(concrete) = any_obj.downcast_ref::<$impl_type>() {
                                // Clone the concrete object and wrap it in Rc
                                return Ok(Self(std::rc::Rc::new(concrete.clone())));
                            }
                        )*
                        // If none of our known types matched, return error
                        return Err($crate::error::Error::Other($crate::error::AnyhowError::msg(
                            format!("Type ID {} is registered but doesn't implement trait {}", fory_type_id, stringify!($trait_name))
                        )));
                    }
                }

                Err($crate::error::Error::Other($crate::error::AnyhowError::msg(
                    format!("Type ID {} not registered in Fory", fory_type_id)
                )))
            }

            fn fory_read_data(context: &mut $crate::resolver::context::ReadContext, is_field: bool) -> Result<Self, $crate::error::Error>
            where
                Self: Sized + Default,
            {
                // Read the concrete type's Fory ID that was written during serialization
                let concrete_fory_type_id = context.reader.read_varuint32();

                // Try to deserialize each registered type based on the concrete type ID
                $(
                    let registered_type_id = context.get_fory()
                        .get_type_resolver()
                        .get_fory_type_id(std::any::TypeId::of::<$impl_type>());
                    if let Some(registered_type_id) = registered_type_id {
                        if concrete_fory_type_id == registered_type_id {
                            let concrete_obj = <$impl_type as $crate::serializer::Serializer>::fory_read_data(context, is_field)?;
                            return Ok(Self(std::rc::Rc::new(concrete_obj)));
                        }
                    }
                )*

                Err($crate::error::Error::Other($crate::error::AnyhowError::msg(
                    format!("Type ID {} not registered for trait {}", concrete_fory_type_id, stringify!($trait_name))
                )))
            }

            fn fory_get_type_id(_fory: &$crate::fory::Fory) -> u32
            where
                Self: Sized,
            {
                // Wrapper types are polymorphic like Box<dyn Trait> - return STRUCT type ID
                $crate::types::TypeId::STRUCT as u32
            }

            fn fory_write_type_info(_context: &mut $crate::resolver::context::WriteContext, _is_field: bool)
            where
                Self: Sized,
            {
                // Wrapper types are polymorphic - type info is written per element like Box<dyn Serializer>
            }

            fn fory_read_type_info(_context: &mut $crate::resolver::context::ReadContext, _is_field: bool)
            where
                Self: Sized,
            {
                // Wrapper types are polymorphic - type info is read per element like Box<dyn Serializer>
            }

            fn fory_is_polymorphic() -> bool
            where
                Self: Sized,
            {
                true
            }

            fn fory_type_id_dyn(&self, fory: &$crate::fory::Fory) -> u32 {
                // Return the concrete type's ID since wrapper types are not registered
                let any_obj = self.0.as_any();
                let concrete_type_id = any_obj.type_id();
                fory.get_type_resolver()
                    .get_fory_type_id(concrete_type_id)
                    .expect("Type not registered for trait object")
            }

            fn fory_concrete_type_id(&self) -> std::any::TypeId {
                self.0.as_any().type_id()
            }
        }

        }
    };
}

/// Generate Arc wrapper type for a trait
#[macro_export]
macro_rules! generate_arc_wrapper {
    ($trait_name:ident, $($impl_type:ty),+ $(,)?) => {
        $crate::paste::paste! {
            #[derive(Clone)]
            pub struct [<$trait_name Arc>](std::sync::Arc<dyn $trait_name + Send + Sync>);

            impl [<$trait_name Arc>] {
                pub fn new(inner: std::sync::Arc<dyn $trait_name + Send + Sync>) -> Self {
                    Self(inner)
                }

                pub fn into_inner(self) -> std::sync::Arc<dyn $trait_name + Send + Sync> {
                    self.0
                }

                pub fn unwrap(self) -> std::sync::Arc<dyn $trait_name + Send + Sync> {
                    self.0
                }

                pub fn as_ref(&self) -> &dyn $trait_name {
                    &*self.0
                }
            }

            impl From<std::sync::Arc<dyn $trait_name + Send + Sync>> for [<$trait_name Arc>] {
            fn from(arc: std::sync::Arc<dyn $trait_name + Send + Sync>) -> Self {
                Self::new(arc)
            }
        }

            impl From<[<$trait_name Arc>]> for std::sync::Arc<dyn $trait_name + Send + Sync> {
            fn from(wrapper: [<$trait_name Arc>]) -> Self {
                wrapper.into_inner()
            }
        }

            impl std::default::Default for [<$trait_name Arc>] {
            fn default() -> Self {
                Self(std::sync::Arc::new(<register_trait_type!(@first_type $($impl_type),+) as std::default::Default>::default()))
            }
        }

            impl std::fmt::Debug for [<$trait_name Arc>] {
            fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
                // Try to show the actual content by downcasting to known types
                let any_obj = self.0.as_any();
                $(
                    if let Some(concrete) = any_obj.downcast_ref::<$impl_type>() {
                        return write!(f, "{}Arc({:?})", stringify!($trait_name), concrete);
                    }
                )*
                // Fallback to memory address if we can't downcast
                write!(f, "{}Arc({:p})", stringify!($trait_name), &*self.0)
            }
        }

            impl $crate::serializer::Serializer for [<$trait_name Arc>] {
            fn fory_write(&self, context: &mut $crate::resolver::context::WriteContext, is_field: bool) {
                use $crate::types::{Mode, RefFlag, TypeId};

                // Write headers manually - use concrete type's ID, not wrapper's ID
                context.writer.write_i8(RefFlag::NotNullValue as i8);

                let any_ref = self.0.as_any();
                let concrete_type_id = any_ref.type_id();

                if let Some(fory_type_id) = context.get_fory().get_type_resolver().get_fory_type_id(concrete_type_id) {
                    context.writer.write_varuint32(fory_type_id);

                    if context.get_fory().get_mode() == &Mode::Compatible
                        && (fory_type_id & 0xff == TypeId::NAMED_COMPATIBLE_STRUCT as u32
                            || fory_type_id & 0xff == TypeId::COMPATIBLE_STRUCT as u32)
                    {
                        let meta_index = context.push_meta(concrete_type_id) as u32;
                        context.writer.write_varuint32(meta_index);
                    }

                    // Call fory_write_data on the concrete type
                    self.fory_write_data(context, is_field);
                } else {
                    panic!("Type {:?} not registered for Arc<dyn {}> serialization", concrete_type_id, stringify!($trait_name));
                }
            }

            fn fory_write_data(&self, context: &mut $crate::resolver::context::WriteContext, is_field: bool) {
                let any_obj = self.0.as_any();
                let concrete_type_id = any_obj.type_id();

                $(
                    if concrete_type_id == std::any::TypeId::of::<$impl_type>() {
                        if let Some(concrete) = any_obj.downcast_ref::<$impl_type>() {
                            concrete.fory_write_data(context, is_field);
                            return;
                        }
                    }
                )*

                panic!("Failed to downcast Arc<dyn {}> to any registered type", stringify!($trait_name));
            }

            fn fory_read(context: &mut $crate::resolver::context::ReadContext, is_field: bool) -> Result<Self, $crate::error::Error>
            where
                Self: Sized + Default,
            {
                use $crate::types::{Mode, RefFlag, TypeId};

                // Read headers manually (same as Box<dyn Trait>)
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

                // Use type resolver to deserialize any registered type (like Box<dyn Serializer>)
                let type_resolver = context.get_fory().get_type_resolver();
                if let Some(harness) = type_resolver.get_harness(fory_type_id) {
                    let deserializer_fn = harness.get_deserializer();
                    if let Ok(any_obj) = deserializer_fn(context, is_field, true) {
                        // Check if the deserialized object implements our trait
                        $(
                            if let Some(concrete) = any_obj.downcast_ref::<$impl_type>() {
                                // Clone the concrete object and wrap it in Arc
                                return Ok(Self(std::sync::Arc::new(concrete.clone())));
                            }
                        )*
                        // If none of our known types matched, return error
                        return Err($crate::error::Error::Other($crate::error::AnyhowError::msg(
                            format!("Type ID {} is registered but doesn't implement trait {}", fory_type_id, stringify!($trait_name))
                        )));
                    }
                }

                Err($crate::error::Error::Other($crate::error::AnyhowError::msg(
                    format!("Type ID {} not registered in Fory", fory_type_id)
                )))
            }

            fn fory_read_data(context: &mut $crate::resolver::context::ReadContext, is_field: bool) -> Result<Self, $crate::error::Error>
            where
                Self: Sized + Default,
            {
                // Read the concrete type's Fory ID that was written during serialization
                let concrete_fory_type_id = context.reader.read_varuint32();

                // Try to deserialize each registered type based on the concrete type ID
                $(
                    let registered_type_id = context.get_fory()
                        .get_type_resolver()
                        .get_fory_type_id(std::any::TypeId::of::<$impl_type>());
                    if let Some(registered_type_id) = registered_type_id {
                        if concrete_fory_type_id == registered_type_id {
                            let concrete_obj = <$impl_type as $crate::serializer::Serializer>::fory_read_data(context, is_field)?;
                            return Ok(Self(std::sync::Arc::new(concrete_obj)));
                        }
                    }
                )*

                Err($crate::error::Error::Other($crate::error::AnyhowError::msg(
                    format!("Type ID {} not registered for trait {}", concrete_fory_type_id, stringify!($trait_name))
                )))
            }

            fn fory_get_type_id(_fory: &$crate::fory::Fory) -> u32
            where
                Self: Sized,
            {
                // Wrapper types are polymorphic like Box<dyn Trait> - return STRUCT type ID
                $crate::types::TypeId::STRUCT as u32
            }

            fn fory_write_type_info(_context: &mut $crate::resolver::context::WriteContext, _is_field: bool)
            where
                Self: Sized,
            {
                // Wrapper types are polymorphic - type info is written per element like Box<dyn Serializer>
            }

            fn fory_read_type_info(_context: &mut $crate::resolver::context::ReadContext, _is_field: bool)
            where
                Self: Sized,
            {
                // Wrapper types are polymorphic - type info is read per element like Box<dyn Serializer>
            }

            fn fory_is_polymorphic() -> bool
            where
                Self: Sized,
            {
                true
            }

            fn fory_type_id_dyn(&self, fory: &$crate::fory::Fory) -> u32 {
                // Return the concrete type's ID since wrapper types are not registered
                let any_obj = self.0.as_any();
                let concrete_type_id = any_obj.type_id();
                fory.get_type_resolver()
                    .get_fory_type_id(concrete_type_id)
                    .expect("Type not registered for trait object")
            }

            fn fory_concrete_type_id(&self) -> std::any::TypeId {
                self.0.as_any().type_id()
            }
        }

            impl $crate::serializer::StructSerializer for [<$trait_name Arc>] {
                fn fory_type_index() -> u32 {
                    // Wrapper types should not be registered as struct types
                    // This should not be called in normal operation
                    0
                }
            }
        }
    };
}

/// Helper macros for automatic conversions in derive code
/// These are used by fory-derive to generate transparent conversions

/// Convert field of type Rc<dyn Trait> to wrapper for serialization
#[macro_export]
macro_rules! fory_rc_to_wrapper {
    ($field:expr, $trait_name:ident) => {
        $crate::paste::paste! {
            [<$trait_name Rc>]::from($field)
        }
    };
}

/// Convert wrapper back to Rc<dyn Trait> for deserialization
#[macro_export]
macro_rules! fory_wrapper_to_rc {
    ($wrapper:expr, $trait_name:ident) => {
        std::rc::Rc::<dyn $trait_name>::from($wrapper)
    };
}

/// Convert field of type Arc<dyn Trait> to wrapper for serialization
#[macro_export]
macro_rules! fory_arc_to_wrapper {
    ($field:expr, $trait_name:ident) => {
        $crate::paste::paste! {
            [<$trait_name Arc>]::from($field)
        }
    };
}

/// Convert wrapper back to Arc<dyn Trait> for deserialization
#[macro_export]
macro_rules! fory_wrapper_to_arc {
    ($wrapper:expr, $trait_name:ident) => {
        std::sync::Arc::<dyn $trait_name + Send + Sync>::from($wrapper)
    };
}

/// Convert Vec<Rc<dyn Trait>> to Vec<wrapper> for serialization
#[macro_export]
macro_rules! fory_vec_rc_to_wrapper {
    ($vec:expr, $trait_name:ident) => {
        $crate::paste::paste! {
            $vec.into_iter().map(|item| [<$trait_name Rc>]::from(item)).collect()
        }
    };
}

/// Convert Vec<wrapper> back to Vec<Rc<dyn Trait>> for deserialization
#[macro_export]
macro_rules! fory_vec_wrapper_to_rc {
    ($vec:expr, $trait_name:ident) => {
        $vec.into_iter().map(|item| std::rc::Rc::<dyn $trait_name>::from(item)).collect()
    };
}

/// Convert HashMap<K, Rc<dyn Trait>> to HashMap<K, wrapper> for serialization
#[macro_export]
macro_rules! fory_map_rc_to_wrapper {
    ($map:expr, $trait_name:ident) => {
        $map.into_iter().map(|(k, v)| (k, trait_object_rc_wrapper::from(v))).collect()
    };
}

/// Convert HashMap<K, wrapper> back to HashMap<K, Rc<dyn Trait>> for deserialization
#[macro_export]
macro_rules! fory_map_wrapper_to_rc {
    ($map:expr, $trait_name:ident) => {
        $map.into_iter().map(|(k, v)| (k, std::rc::Rc::<dyn $trait_name>::from(v))).collect()
    };
}

// Wrapper registration is removed - wrapper types should not be registered
// They are only used to work around the type system limitation for Rc/Arc<dyn Trait>

// Note: The automatic wrapper approach completely eliminates manual wrapper usage.
// Users call register_trait_type!(Animal, Dog, Cat) once and get transparent conversions.
// The derive macro handles all wrapper conversions automatically.
