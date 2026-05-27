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

//! # Fory Derive Macros
//!
//! This crate provides procedural macros for the Fory serialization framework.
//! It generates serialization and deserialization code for Rust types.
//!
//! ## Available Macros
//!
//! ### `#[derive(ForyStruct)]`, `#[derive(ForyEnum)]`, `#[derive(ForyUnion)]`
//!
//! Generates `Serializer` implementations for structs, pure enums, and tagged
//! unions with payload variants.
//!
//! **Supported Types:**
//! - `ForyStruct`: named, tuple, and unit structs
//! - `ForyEnum`: pure unit enums
//! - `ForyUnion`: enums with payload variants
//!
//! **Example:**
//! ```rust
//! use fory_derive::{ForyEnum, ForyStruct};
//! use std::collections::HashMap;
//!
//! #[derive(ForyStruct, Debug, PartialEq)]
//! struct Person {
//!     name: String,
//!     age: i32,
//!     address: Address,
//!     hobbies: Vec<String>,
//!     metadata: HashMap<String, String>,
//! }
//!
//! #[derive(ForyStruct, Debug, PartialEq)]
//! struct Address {
//!     street: String,
//!     city: String,
//! }
//!
//! #[derive(ForyEnum, Debug, PartialEq, Default)]
//! enum Status {
//!     #[default]
//!     Active,
//!     Inactive,
//!     Suspended,
//! }
//! ```
//!
//! ### `#[derive(ForyRow)]`
//!
//! Generates row-based serialization code for structs. This macro implements
//! the `Row` trait, enabling zero-copy deserialization for maximum performance.
//!
//! **Supported Types:**
//! - Structs with named fields only
//! - All field types must implement the `Row` trait
//!
//! **Example:**
//! ```rust
//! use fory_derive::ForyRow;
//! use std::collections::BTreeMap;
//!
//! #[derive(ForyRow)]
//! struct UserProfile {
//!     id: i64,
//!     username: String,
//!     email: String,
//!     scores: Vec<i32>,
//!     preferences: BTreeMap<String, String>,
//!     is_active: bool,
//! }
//! ```
//!
//! ## Generated Code
//!
//! ### For `#[derive(ForyStruct)]`, `#[derive(ForyEnum)]`, and `#[derive(ForyUnion)]`
//!
//! The macro generates:
//! - `Serializer` trait implementation
//! - Serialization methods for writing data to buffers
//! - Deserialization methods for reading data from buffers
//! - Type ID management for cross-language compatibility
//!
//! ### For `#[derive(ForyRow)]`
//!
//! The macro generates:
//! - `Row` trait implementation
//! - A getter struct for zero-copy field access
//! - Field accessor methods that return references to the underlying data
//! - Efficient serialization without object allocation
//!
//! ## Attributes
//!
//! - **`#[fory(debug)]` / `#[fory(debug = true)]`**: Enables per-field debug instrumentation
//!   for the annotated struct, allowing you to install custom hooks via
//!   `fory_core::serializer::struct_`.
//! - **`#[fory(evolving = false)]`**: Disables compatible struct type IDs for the annotated
//!   struct, forcing STRUCT/NAMED_STRUCT even when compatible mode is enabled.
//! - **`#[fory(skip)]`**: Marks an individual field (or enum variant) to be ignored by the
//!   generated serializer, retaining compatibility with previous releases.
//! - **`#[fory(generate_default)]`**: Enables the macro to generate `Default` implementation.
//!   By default, `ForyStruct` does NOT generate `impl Default` to avoid conflicts with existing
//!   `Default` implementations. Use this attribute when you want the macro to generate both
//!   `ForyDefault` and `Default` for you.
//! - **`#[fory(default)]`**: Marks the default `ForyUnion` variant. `ForyUnion` requires exactly
//!   one default variant so schema evolution and null fallback have an explicit owner.
//!
//! ## Field Types
//!
//! Both macros support a wide range of field types:
//!
//! **Primitive Types:**
//! - `bool`, `i8`, `i16`, `i32`, `i64`, `f32`, `f64`
//! - `String`, `&str` (in row format)
//! - `Vec<u8>` for binary data
//!
//! **Collections:**
//! - `Vec<T>` where `T` implements the appropriate trait
//! - `HashMap<K, V>` and `BTreeMap<K, V>` where keys and values implement the trait
//! - `Option<T>` for nullable values
//!
//! **Date/Time:**
//! - `fory::Date`
//! - `fory::Timestamp`
//! - `fory::Duration`
//! - `chrono::NaiveDate`, `chrono::NaiveDateTime`, and `chrono::Duration` when the `chrono` feature is enabled
//!
//! **Custom Types:**
//! - Any type that implements `Serializer` (for `Fory`) or `Row` (for `ForyRow`)
//!
//! ## Usage with Fory
//!
//! After deriving the macros, you can use the types with the Fory serialization
//! framework:
//!
//! ```rust
//! use fory_core::{fory::Fory, error::Error};
//! use fory_derive::{ForyEnum, ForyStruct, ForyUnion};
//!
//! #[derive(ForyStruct, Debug, PartialEq)]
//! struct MyData {
//!     value: i32,
//!     text: String,
//! }
//!
//! fn main() -> Result<(), Error> {
//!     let mut fory = Fory::builder().xlang(true).build();
//!     fory.register_by_name::<MyData>("example", "MyData")?;
//!     
//!     let data = MyData {
//!         value: 42,
//!         text: "Hello, Fory!".to_string(),
//!     };
//!     
//!     let serialized = fory.serialize(&data)?;
//!     let deserialized: MyData = fory.deserialize(&serialized)?;
//!     
//!     assert_eq!(data, deserialized);
//!     Ok(())
//! }
//! ```
//!
//! ## Performance Considerations
//!
//! - **`Fory`**: Best for complex object graphs with references and nested structures
//! - **`ForyRow`**: Best for high-throughput scenarios requiring zero-copy access
//! - Both macros generate optimized code with minimal runtime overhead
//! - Field access in row format is extremely fast as it involves no allocations

use fory_row::derive_row;
use proc_macro::TokenStream;
use syn::{
    parse_macro_input, spanned::Spanned, Attribute, Data, DeriveInput, Fields, LitBool, Type,
};

mod fory_row;
mod object;
mod util;

fn is_fory_default_variant(attrs: &[Attribute]) -> bool {
    attrs.iter().any(|attr| {
        attr.path().is_ident("fory") && {
            let mut is_default = false;
            let _ = attr.parse_nested_meta(|meta| {
                if meta.path.is_ident("default") {
                    is_default = true;
                } else if !meta.input.is_empty() {
                    let value = meta.value()?;
                    let _ = value.parse::<syn::Expr>()?;
                }
                Ok(())
            });
            is_default
        }
    })
}

fn is_unknown_case_type(ty: &Type) -> bool {
    let Type::Path(type_path) = ty else {
        return false;
    };
    type_path
        .path
        .segments
        .last()
        .is_some_and(|segment| segment.ident == "UnknownCase")
}

// Only the runtime-owned forward-compatibility carrier is excluded from default
// selection. A user variant named Unknown with a different id or payload type is
// still a real union case.
fn is_runtime_unknown_variant(index: usize, variant: &syn::Variant) -> bool {
    if variant.ident != "Unknown" {
        return false;
    }
    let Fields::Unnamed(fields) = &variant.fields else {
        return false;
    };
    if fields.unnamed.len() != 1 || !is_unknown_case_type(&fields.unnamed[0].ty) {
        return false;
    }
    object::util::enum_variant_id(variant).unwrap_or(index as u32) == 0
}

/// Derive macro for struct serialization.
#[proc_macro_derive(ForyStruct, attributes(fory))]
pub fn proc_macro_derive_fory_struct(input: proc_macro::TokenStream) -> TokenStream {
    let input = parse_macro_input!(input as DeriveInput);
    if !matches!(input.data, Data::Struct(_)) {
        return syn::Error::new(
            input.ident.span(),
            "ForyStruct can only be derived for structs; use ForyEnum for pure enums or ForyUnion for data-carrying enums",
        )
        .into_compile_error()
        .into();
    }
    derive_serializer(input)
}

/// Derive macro for pure enum serialization.
#[proc_macro_derive(ForyEnum, attributes(fory))]
pub fn proc_macro_derive_fory_enum(input: proc_macro::TokenStream) -> TokenStream {
    let input = parse_macro_input!(input as DeriveInput);
    let Data::Enum(data_enum) = &input.data else {
        return syn::Error::new(input.ident.span(), "ForyEnum can only be derived for enums")
            .into_compile_error()
            .into();
    };
    if data_enum
        .variants
        .iter()
        .any(|variant| !matches!(variant.fields, Fields::Unit))
    {
        return syn::Error::new(
            input.ident.span(),
            "ForyEnum is only for pure unit enums; use ForyUnion for enum variants with payloads",
        )
        .into_compile_error()
        .into();
    }
    derive_serializer(input)
}

/// Derive macro for tagged union serialization.
#[proc_macro_derive(ForyUnion, attributes(fory))]
pub fn proc_macro_derive_fory_union(input: proc_macro::TokenStream) -> TokenStream {
    let input = parse_macro_input!(input as DeriveInput);
    let Data::Enum(data_enum) = &input.data else {
        return syn::Error::new(
            input.ident.span(),
            "ForyUnion can only be derived for enums",
        )
        .into_compile_error()
        .into();
    };
    if data_enum
        .variants
        .iter()
        .all(|variant| matches!(variant.fields, Fields::Unit))
    {
        return syn::Error::new(
            input.ident.span(),
            "ForyUnion requires at least one payload variant; use ForyEnum for pure unit enums",
        )
        .into_compile_error()
        .into();
    }
    let non_unknown_count = data_enum
        .variants
        .iter()
        .enumerate()
        .filter(|(index, variant)| !is_runtime_unknown_variant(*index, variant))
        .count();
    if non_unknown_count == 0 {
        return syn::Error::new(
            input.ident.span(),
            "ForyUnion requires at least one non-Unknown case; Unknown is a forward-compatibility carrier and cannot be the default",
        )
        .into_compile_error()
        .into();
    }
    let default_count = data_enum
        .variants
        .iter()
        .filter(|variant| is_fory_default_variant(&variant.attrs))
        .count();
    if default_count != 1 {
        return syn::Error::new(
            input.ident.span(),
            "ForyUnion requires exactly one #[fory(default)] variant for ForyDefault and Default semantics",
        )
        .into_compile_error()
        .into();
    }
    if data_enum
        .variants
        .iter()
        .enumerate()
        .any(|(index, variant)| {
            is_runtime_unknown_variant(index, variant) && is_fory_default_variant(&variant.attrs)
        })
    {
        return syn::Error::new(
            input.ident.span(),
            "ForyUnion Unknown case cannot be marked #[fory(default)]",
        )
        .into_compile_error()
        .into();
    }
    derive_serializer(input)
}

fn derive_serializer(input: DeriveInput) -> TokenStream {
    let attrs = match parse_fory_attrs(&input.attrs) {
        Ok(attrs) => attrs,
        Err(err) => return err.into_compile_error().into(),
    };

    object::derive_serializer(&input, attrs)
}

/// Derive macro for row-based serialization.
///
/// This macro generates code to implement the `Row` trait for the annotated
/// type, enabling zero-copy deserialization for maximum performance in
/// high-throughput scenarios.
///
/// # Example
///
/// ```rust
/// use fory_derive::ForyRow;
///
/// #[derive(ForyRow)]
/// struct UserProfile {
///     id: i64,
///     username: String,
///     email: String,
///     is_active: bool,
/// }
/// ```
#[proc_macro_derive(ForyRow)]
pub fn proc_macro_derive_fory_row(input: proc_macro::TokenStream) -> TokenStream {
    let input = parse_macro_input!(input as DeriveInput);
    derive_row(&input)
}

#[cfg(test)]
mod tests {
    use super::*;
    use syn::parse_quote;

    #[test]
    fn detects_runtime_unknown_variant() {
        let variant: syn::Variant = parse_quote!(
            #[fory(id = 0)]
            Unknown(::fory::UnknownCase)
        );

        assert!(is_runtime_unknown_variant(0, &variant));
    }

    #[test]
    fn user_unknown_variant_remains_real_case() {
        let wrong_id: syn::Variant = parse_quote!(
            #[fory(id = 7)]
            Unknown(::fory::UnknownCase)
        );
        let wrong_payload: syn::Variant = parse_quote!(
            #[fory(id = 0)]
            Unknown(String)
        );

        assert!(!is_runtime_unknown_variant(0, &wrong_id));
        assert!(!is_runtime_unknown_variant(0, &wrong_payload));
    }
}

/// Parsed fory attributes
pub(crate) struct ForyAttrs {
    pub debug_enabled: bool,
    pub generate_default: bool,
    pub evolving: Option<bool>,
}

/// Parse fory attributes and return ForyAttrs
fn parse_fory_attrs(attrs: &[Attribute]) -> syn::Result<ForyAttrs> {
    let mut debug_flag: Option<bool> = None;
    let mut generate_default_flag: Option<bool> = None;
    let mut evolving_flag: Option<bool> = None;

    for attr in attrs {
        if attr.path().is_ident("fory") {
            attr.parse_nested_meta(|meta| {
                if meta.path.is_ident("debug") {
                    let value = if meta.input.is_empty() {
                        true
                    } else {
                        let lit: LitBool = meta.value()?.parse()?;
                        lit.value
                    };
                    debug_flag = match debug_flag {
                        Some(existing) if existing != value => {
                            return Err(syn::Error::new(
                                meta.path.span(),
                                "conflicting `debug` attribute values",
                            ));
                        }
                        Some(_) => debug_flag,
                        None => Some(value),
                    };
                } else if meta.path.is_ident("generate_default") {
                    let value = if meta.input.is_empty() {
                        true
                    } else {
                        let lit: LitBool = meta.value()?.parse()?;
                        lit.value
                    };
                    generate_default_flag = match generate_default_flag {
                        Some(existing) if existing != value => {
                            return Err(syn::Error::new(
                                meta.path.span(),
                                "conflicting `generate_default` attribute values",
                            ));
                        }
                        Some(_) => generate_default_flag,
                        None => Some(value),
                    };
                } else if meta.path.is_ident("evolving") {
                    let value = if meta.input.is_empty() {
                        true
                    } else {
                        let lit: LitBool = meta.value()?.parse()?;
                        lit.value
                    };
                    evolving_flag = match evolving_flag {
                        Some(existing) if existing != value => {
                            return Err(syn::Error::new(
                                meta.path.span(),
                                "conflicting `evolving` attribute values",
                            ));
                        }
                        Some(_) => evolving_flag,
                        None => Some(value),
                    };
                }
                Ok(())
            })?;
        }
    }

    Ok(ForyAttrs {
        debug_enabled: debug_flag.unwrap_or(false),
        generate_default: generate_default_flag.unwrap_or(false),
        evolving: evolving_flag,
    })
}
