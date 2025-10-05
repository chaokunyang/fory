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

/// Helper functions for trait object serialization to reduce code duplication
///
/// Writes common trait object headers (ref flag, type ID, compatibility metadata)
pub fn write_trait_object_headers(
    context: &mut WriteContext,
    fory_type_id: u32,
    concrete_type_id: std::any::TypeId,
) {
    use crate::types::{Mode, RefFlag, TypeId};

    context.writer.write_i8(RefFlag::NotNullValue as i8);
    context.writer.write_varuint32(fory_type_id);

    if context.get_fory().get_mode() == &Mode::Compatible
        && (fory_type_id & 0xff == TypeId::NAMED_COMPATIBLE_STRUCT as u32
            || fory_type_id & 0xff == TypeId::COMPATIBLE_STRUCT as u32)
    {
        let meta_index = context.push_meta(concrete_type_id) as u32;
        context.writer.write_varuint32(meta_index);
    }
}

/// Reads common trait object headers and returns the type ID
pub fn read_trait_object_headers(context: &mut ReadContext) -> Result<u32, Error> {
    use crate::types::{Mode, RefFlag, TypeId};

    let ref_flag = context.reader.read_i8();
    if ref_flag != RefFlag::NotNullValue as i8 {
        return Err(Error::Other(crate::error::AnyhowError::msg(format!(
            "Expected NotNullValue ref flag, got {}",
            ref_flag
        ))));
    }

    let fory_type_id = context.reader.read_varuint32();

    if context.get_fory().get_mode() == &Mode::Compatible
        && (fory_type_id & 0xff == TypeId::NAMED_COMPATIBLE_STRUCT as u32
            || fory_type_id & 0xff == TypeId::COMPATIBLE_STRUCT as u32)
    {
        let _meta_index = context.reader.read_varuint32();
    }

    Ok(fory_type_id)
}

/// Helper macro for common type resolution and downcasting pattern
#[macro_export]
macro_rules! downcast_and_serialize {
    ($any_ref:expr, $context:expr, $is_field:expr, $trait_name:ident, $($impl_type:ty),+) => {{
        $(
            if $any_ref.type_id() == std::any::TypeId::of::<$impl_type>() {
                if let Some(concrete) = $any_ref.downcast_ref::<$impl_type>() {
                    concrete.fory_write_data($context, $is_field);
                    return;
                }
            }
        )*
        panic!("Failed to downcast to any registered type for trait {}", stringify!($trait_name));
    }};
}

/// Helper macro for common type resolution and deserialization pattern
#[macro_export]
macro_rules! resolve_and_deserialize {
    ($fory_type_id:expr, $context:expr, $is_field:expr, $constructor:expr, $trait_name:ident, $($impl_type:ty),+) => {{
        $(
            if let Some(registered_type_id) = $context.get_fory().get_type_resolver().get_fory_type_id(std::any::TypeId::of::<$impl_type>()) {
                if $fory_type_id == registered_type_id {
                    let concrete_obj = <$impl_type as $crate::serializer::Serializer>::fory_read_data($context, $is_field)?;
                    return Ok($constructor(concrete_obj));
                }
            }
        )*
        Err($crate::error::Error::Other($crate::error::AnyhowError::msg(
            format!("Type ID {} not registered for trait {}", $fory_type_id, stringify!($trait_name))
        )))
    }};
}

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
        $crate::generate_smart_pointer_wrapper!(Rc, $trait_name, $($impl_type),+);

        // 3. Auto-generate Arc wrapper type and conversions
        $crate::generate_smart_pointer_wrapper!(Arc, $trait_name, $($impl_type),+);

        // 4. Skip registration helper function for wrapper types - wrappers are not registered

        // 5. Serializer implementation for Box<dyn Trait> (existing functionality)
        impl $crate::serializer::Serializer for Box<dyn $trait_name> {
            fn fory_write(&self, context: &mut $crate::resolver::context::WriteContext, is_field: bool) {
                let any_ref = self.as_any();
                let concrete_type_id = any_ref.type_id();

                if let Some(fory_type_id) = context.get_fory().get_type_resolver().get_fory_type_id(concrete_type_id) {
                    $crate::serializer::trait_object::write_trait_object_headers(context, fory_type_id, concrete_type_id);
                    $crate::downcast_and_serialize!(any_ref, context, is_field, $trait_name, $($impl_type),+);
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
                let fory_type_id = $crate::serializer::trait_object::read_trait_object_headers(context)?;
                $crate::resolve_and_deserialize!(
                    fory_type_id, context, is_field,
                    |obj| Box::new(obj) as Box<dyn $trait_name>,
                    $trait_name, $($impl_type),+
                )
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

/// Unified macro to generate smart pointer wrapper types for traits
/// Supports both Rc and Arc pointer types
#[macro_export]
macro_rules! generate_smart_pointer_wrapper {
    (Rc, $trait_name:ident, $($impl_type:ty),+ $(,)?) => {
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
                fn from(ptr: std::rc::Rc<dyn $trait_name>) -> Self {
                    Self::new(ptr)
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
                    let any_obj = self.0.as_any();
                    $(
                        if let Some(concrete) = any_obj.downcast_ref::<$impl_type>() {
                            return write!(f, "{}Rc({:?})", stringify!($trait_name), concrete);
                        }
                    )*
                    write!(f, "{}Rc({:p})", stringify!($trait_name), &*self.0)
                }
            }

            $crate::impl_smart_pointer_serializer!([<$trait_name Rc>], std::rc::Rc::new, std::rc::Rc<dyn $trait_name>, $trait_name, $($impl_type),+);
        }
    };

    (Arc, $trait_name:ident, $($impl_type:ty),+ $(,)?) => {
        $crate::paste::paste! {
            #[derive(Clone)]
            pub struct [<$trait_name Arc>](std::sync::Arc<dyn $trait_name>);

            impl [<$trait_name Arc>] {
                pub fn new(inner: std::sync::Arc<dyn $trait_name>) -> Self {
                    Self(inner)
                }

                pub fn into_inner(self) -> std::sync::Arc<dyn $trait_name> {
                    self.0
                }

                pub fn unwrap(self) -> std::sync::Arc<dyn $trait_name> {
                    self.0
                }

                pub fn as_ref(&self) -> &dyn $trait_name {
                    &*self.0
                }
            }

            impl From<std::sync::Arc<dyn $trait_name>> for [<$trait_name Arc>] {
                fn from(ptr: std::sync::Arc<dyn $trait_name>) -> Self {
                    Self::new(ptr)
                }
            }

            impl From<[<$trait_name Arc>]> for std::sync::Arc<dyn $trait_name> {
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
                    let any_obj = self.0.as_any();
                    $(
                        if let Some(concrete) = any_obj.downcast_ref::<$impl_type>() {
                            return write!(f, "{}Arc({:?})", stringify!($trait_name), concrete);
                        }
                    )*
                    write!(f, "{}Arc({:p})", stringify!($trait_name), &*self.0)
                }
            }

            $crate::impl_smart_pointer_serializer!([<$trait_name Arc>], std::sync::Arc::new, std::sync::Arc<dyn $trait_name>, $trait_name, $($impl_type),+);
        }
    };
}

/// Shared serializer implementation for smart pointer wrappers
#[macro_export]
macro_rules! impl_smart_pointer_serializer {
    ($wrapper_name:ident, $constructor_expr:expr, $pointer_type:ty, $trait_name:ident, $($impl_type:ty),+) => {
        impl $crate::serializer::Serializer for $wrapper_name {
            fn fory_write(&self, context: &mut $crate::resolver::context::WriteContext, is_field: bool) {
                let any_ref = self.0.as_any();
                let concrete_type_id = any_ref.type_id();

                if let Some(fory_type_id) = context.get_fory().get_type_resolver().get_fory_type_id(concrete_type_id) {
                    $crate::serializer::trait_object::write_trait_object_headers(context, fory_type_id, concrete_type_id);
                    $crate::downcast_and_serialize!(any_ref, context, is_field, $trait_name, $($impl_type),+);
                } else {
                    panic!("Type {:?} not registered for {} serialization", concrete_type_id, stringify!($wrapper_name));
                }
            }

            fn fory_write_data(&self, context: &mut $crate::resolver::context::WriteContext, is_field: bool) {
                let any_obj = self.0.as_any();
                $crate::downcast_and_serialize!(any_obj, context, is_field, $trait_name, $($impl_type),+);
            }

            fn fory_read(context: &mut $crate::resolver::context::ReadContext, is_field: bool) -> Result<Self, $crate::error::Error>
            where
                Self: Sized + Default,
            {
                let fory_type_id = $crate::serializer::trait_object::read_trait_object_headers(context)?;

                // Use type resolver to deserialize any registered type
                let type_resolver = context.get_fory().get_type_resolver();
                if let Some(harness) = type_resolver.get_harness(fory_type_id) {
                    let deserializer_fn = harness.get_deserializer();
                    if let Ok(any_obj) = deserializer_fn(context, is_field, true) {
                        $(
                            if any_obj.is::<$impl_type>() {
                                match any_obj.downcast::<$impl_type>() {
                                    Ok(boxed) => {
                                        let pointer = $constructor_expr(*boxed) as $pointer_type;
                                        return Ok(Self::from(pointer));
                                    }
                                    Err(recovered) => {
                                        return Err($crate::error::Error::Other($crate::error::AnyhowError::msg(
                                            format!("Failed to downcast type for trait {}", stringify!($trait_name))
                                        )));
                                    }
                                }
                            }
                        )*
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
                let concrete_fory_type_id = context.reader.read_varuint32();
                $crate::resolve_and_deserialize!(
                    concrete_fory_type_id, context, is_field,
                    |obj| {
                        let pointer = $constructor_expr(obj) as $pointer_type;
                        Self::from(pointer)
                    },
                    $trait_name, $($impl_type),+
                )
            }

            fn fory_get_type_id(_fory: &$crate::fory::Fory) -> u32 {
                $crate::types::TypeId::STRUCT as u32
            }

            fn fory_write_type_info(_context: &mut $crate::resolver::context::WriteContext, _is_field: bool) {
                // Wrapper types are polymorphic - type info is written per element
            }

            fn fory_read_type_info(_context: &mut $crate::resolver::context::ReadContext, _is_field: bool) {
                // Wrapper types are polymorphic - type info is read per element
            }

            fn fory_is_polymorphic() -> bool {
                true
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
        let fory_type_id = (**self).fory_type_id_dyn(context.get_fory());
        let concrete_type_id = (**self).fory_concrete_type_id();

        write_trait_object_headers(context, fory_type_id, concrete_type_id);
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
        let fory_type_id = read_trait_object_headers(context)?;

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

/// Helper macros for automatic conversions in derive code
/// These are used by fory-derive to generate transparent conversions
///
/// Convert field of type Rc<dyn Trait> to wrapper for serialization
#[macro_export]
macro_rules! wrap_rc {
    ($field:expr, $trait_name:ident) => {
        $crate::paste::paste! {
            [<$trait_name Rc>]::from($field)
        }
    };
}

/// Convert wrapper back to Rc<dyn Trait> for deserialization
#[macro_export]
macro_rules! unwrap_rc {
    ($wrapper:expr, $trait_name:ident) => {
        std::rc::Rc::<dyn $trait_name>::from($wrapper)
    };
}

/// Convert Arc<dyn Trait> to wrapper for serialization
#[macro_export]
macro_rules! wrap_arc {
    ($field:expr, $trait_name:ident) => {
        $crate::paste::paste! {
            [<$trait_name Arc>]::from($field)
        }
    };
}

/// Convert Vec<Rc<dyn Trait>> to Vec<wrapper> for serialization
#[macro_export]
macro_rules! wrap_vec_rc {
    ($vec:expr, $trait_name:ident) => {
        $crate::paste::paste! {
            $vec.into_iter().map(|item| [<$trait_name Rc>]::from(item)).collect()
        }
    };
}
