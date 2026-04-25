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

//! Field-level metadata parsing for `#[fory(...)]` attributes.

use quote::ToTokens;
use std::collections::HashMap;
use syn::parse::Parser;
use syn::spanned::Spanned;
use syn::{Field, GenericArgument, PathArguments, Type};

#[derive(Debug, Clone, Default)]
pub struct ForyFieldMeta {
    pub id: Option<i32>,
    pub nullable: Option<bool>,
    pub r#ref: Option<bool>,
    pub skip: bool,
    pub encoding: Option<EncodingHint>,
    pub list: Option<ListMeta>,
    pub map: Option<MapMeta>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum EncodingHint {
    Varint,
    Fixed,
    Tagged,
}

#[derive(Debug, Clone, Default)]
pub struct ListMeta {
    pub element: ElementMeta,
}

#[derive(Debug, Clone, Default)]
pub struct MapMeta {
    pub key: ElementMeta,
    pub value: ElementMeta,
}

#[derive(Debug, Clone, Default)]
pub struct ElementMeta {
    pub nullable: Option<bool>,
    pub encoding: Option<EncodingHint>,
}

impl EncodingHint {
    pub fn to_type_id_for_type_name(self, type_name: &str) -> Option<u32> {
        match (type_name, self) {
            ("i32", EncodingHint::Varint) => Some(fory_core::type_id::TypeId::VARINT32 as u32),
            ("i32", EncodingHint::Fixed) => Some(fory_core::type_id::TypeId::INT32 as u32),
            ("u32", EncodingHint::Varint) => Some(fory_core::type_id::TypeId::VAR_UINT32 as u32),
            ("u32", EncodingHint::Fixed) => Some(fory_core::type_id::TypeId::UINT32 as u32),
            ("i64", EncodingHint::Varint) => Some(fory_core::type_id::TypeId::VARINT64 as u32),
            ("i64", EncodingHint::Fixed) => Some(fory_core::type_id::TypeId::INT64 as u32),
            ("i64", EncodingHint::Tagged) => Some(fory_core::type_id::TypeId::TAGGED_INT64 as u32),
            ("u64", EncodingHint::Varint) => Some(fory_core::type_id::TypeId::VAR_UINT64 as u32),
            ("u64", EncodingHint::Fixed) => Some(fory_core::type_id::TypeId::UINT64 as u32),
            ("u64", EncodingHint::Tagged) => Some(fory_core::type_id::TypeId::TAGGED_UINT64 as u32),
            _ => None,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum FieldTypeClass {
    Primitive,
    Option,
    Rc,
    Arc,
    RcWeak,
    ArcWeak,
    Other,
}

impl ForyFieldMeta {
    pub fn effective_nullable(&self, type_class: FieldTypeClass) -> bool {
        self.nullable.unwrap_or(matches!(
            type_class,
            FieldTypeClass::Option | FieldTypeClass::RcWeak | FieldTypeClass::ArcWeak
        ))
    }

    pub fn effective_ref(&self, type_class: FieldTypeClass) -> bool {
        self.r#ref.unwrap_or(matches!(
            type_class,
            FieldTypeClass::Rc
                | FieldTypeClass::Arc
                | FieldTypeClass::RcWeak
                | FieldTypeClass::ArcWeak
        ))
    }

    pub fn effective_id(&self) -> i32 {
        self.id.unwrap_or(-1)
    }

    pub fn uses_tag_id(&self) -> bool {
        self.id.is_some_and(|id| id >= 0)
    }
}

pub fn parse_field_meta_or_default(field: &Field) -> ForyFieldMeta {
    parse_field_meta(field).unwrap_or_default()
}

pub fn effective_field_id(meta: &ForyFieldMeta) -> i16 {
    if meta.uses_tag_id() {
        meta.effective_id() as i16
    } else {
        -1
    }
}

pub fn effective_sort_key(field_name: &str, meta: &ForyFieldMeta) -> String {
    if meta.uses_tag_id() {
        meta.effective_id().to_string()
    } else {
        field_name.to_string()
    }
}

pub fn effective_nullable_for_field(ty: &Type, meta: &ForyFieldMeta) -> bool {
    let type_class = classify_field_type(ty);
    meta.effective_nullable(type_class) || is_option_type(ty)
}

pub fn effective_ref_for_field(ty: &Type, meta: &ForyFieldMeta) -> bool {
    meta.effective_ref(classify_field_type(ty))
}

fn parse_bool_or_flag(meta: &syn::meta::ParseNestedMeta) -> syn::Result<bool> {
    if meta.input.is_empty() || meta.input.peek(syn::Token![,]) {
        Ok(true)
    } else {
        let lit: syn::LitBool = meta.value()?.parse()?;
        Ok(lit.value)
    }
}

fn parse_encoding_lit(lit: &syn::LitStr) -> syn::Result<EncodingHint> {
    match lit.value().as_str() {
        "varint" => Ok(EncodingHint::Varint),
        "fixed" => Ok(EncodingHint::Fixed),
        "tagged" => Ok(EncodingHint::Tagged),
        _ => Err(syn::Error::new(
            lit.span(),
            "encoding must be \"varint\", \"fixed\", or \"tagged\"",
        )),
    }
}

fn parse_encoding_or_compress(
    nested: &syn::meta::ParseNestedMeta,
    target: &mut Option<EncodingHint>,
) -> syn::Result<()> {
    if nested.path.is_ident("compress") {
        let value = parse_bool_or_flag(nested)?;
        *target = Some(if value {
            EncodingHint::Varint
        } else {
            EncodingHint::Fixed
        });
        Ok(())
    } else if nested.path.is_ident("encoding") {
        let lit: syn::LitStr = nested.value()?.parse()?;
        *target = Some(parse_encoding_lit(&lit)?);
        Ok(())
    } else {
        Err(syn::Error::new(
            nested.path.span(),
            "unsupported nested attribute",
        ))
    }
}

fn parse_element_meta(tokens: proc_macro2::TokenStream) -> syn::Result<ElementMeta> {
    let mut meta = ElementMeta::default();
    let parser = syn::meta::parser(|nested| {
        if nested.path.is_ident("nullable") {
            meta.nullable = Some(parse_bool_or_flag(&nested)?);
            Ok(())
        } else if nested.path.is_ident("compress") || nested.path.is_ident("encoding") {
            parse_encoding_or_compress(&nested, &mut meta.encoding)
        } else {
            Err(syn::Error::new(
                nested.path.span(),
                "element metadata only supports nullable/compress/encoding",
            ))
        }
    });
    parser.parse2(tokens)?;
    Ok(meta)
}

fn outer_or_option_inner_type_name(ty: &Type) -> String {
    if let Some(inner) = extract_option_inner_type(ty) {
        extract_outer_type_name(&inner)
    } else {
        extract_outer_type_name(ty)
    }
}

pub fn effective_type_id_for_field_type(ty: &Type, meta: &ForyFieldMeta) -> Option<u32> {
    let encode_ty = extract_option_inner_type(ty).unwrap_or_else(|| ty.clone());
    effective_type_id_for_type_name(&type_to_string(&encode_ty), meta)
}

fn validate_nested_metadata_shape(field: &Field, meta: &ForyFieldMeta) -> syn::Result<()> {
    let type_name = outer_or_option_inner_type_name(&field.ty);

    if meta.list.is_some() && meta.map.is_some() {
        return Err(syn::Error::new(
            field.ty.span(),
            "field metadata cannot use both list(...) and map(...)",
        ));
    }

    if meta.list.is_some() && type_name != "Vec" {
        return Err(syn::Error::new(
            field.ty.span(),
            "list(...) requires Vec<T> or Option<Vec<T>>",
        ));
    }

    if meta.map.is_some() && type_name != "HashMap" && type_name != "BTreeMap" {
        return Err(syn::Error::new(
            field.ty.span(),
            "map(...) requires HashMap<K, V>, BTreeMap<K, V>, or their Option wrappers",
        ));
    }

    if meta.encoding.is_some() && effective_type_id_for_field_type(&field.ty, meta).is_none() {
        return Err(syn::Error::new(
            field.ty.span(),
            "encoding/compress is only supported for i32/u32/i64/u64 fields or their Option wrappers",
        ));
    }

    Ok(())
}

pub fn parse_field_meta(field: &Field) -> syn::Result<ForyFieldMeta> {
    let mut meta = ForyFieldMeta::default();

    for attr in &field.attrs {
        if !attr.path().is_ident("fory") {
            continue;
        }

        attr.parse_nested_meta(|nested| {
            if nested.path.is_ident("id") {
                let lit: syn::LitInt = nested.value()?.parse()?;
                let id: i32 = lit.base10_parse()?;
                if id < -1 {
                    return Err(syn::Error::new(lit.span(), "id must be >= -1"));
                }
                meta.id = Some(id);
                Ok(())
            } else if nested.path.is_ident("nullable") {
                meta.nullable = Some(parse_bool_or_flag(&nested)?);
                Ok(())
            } else if nested.path.is_ident("ref") {
                meta.r#ref = Some(parse_bool_or_flag(&nested)?);
                Ok(())
            } else if nested.path.is_ident("skip") {
                meta.skip = true;
                Ok(())
            } else if nested.path.is_ident("compress") || nested.path.is_ident("encoding") {
                parse_encoding_or_compress(&nested, &mut meta.encoding)
            } else if nested.path.is_ident("type_id") {
                Err(syn::Error::new(
                    nested.path.span(),
                    "field-level type_id has been removed; use encoding/list/map annotations instead",
                ))
            } else if nested.path.is_ident("list") {
                let mut list = meta.list.take().unwrap_or_default();
                nested.parse_nested_meta(|inner| {
                    if inner.path.is_ident("element") {
                        let content;
                        syn::parenthesized!(content in inner.input);
                        let tokens: proc_macro2::TokenStream = content.parse()?;
                        list.element = parse_element_meta(tokens)?;
                        Ok(())
                    } else {
                        Err(syn::Error::new(
                            inner.path.span(),
                            "list(...) only supports element(...)",
                        ))
                    }
                })?;
                meta.list = Some(list);
                Ok(())
            } else if nested.path.is_ident("map") {
                let mut map = meta.map.take().unwrap_or_default();
                nested.parse_nested_meta(|inner| {
                    if inner.path.is_ident("key") {
                        let content;
                        syn::parenthesized!(content in inner.input);
                        let tokens: proc_macro2::TokenStream = content.parse()?;
                        map.key = parse_element_meta(tokens)?;
                        Ok(())
                    } else if inner.path.is_ident("value") {
                        let content;
                        syn::parenthesized!(content in inner.input);
                        let tokens: proc_macro2::TokenStream = content.parse()?;
                        map.value = parse_element_meta(tokens)?;
                        Ok(())
                    } else {
                        Err(syn::Error::new(
                            inner.path.span(),
                            "map(...) only supports key(...) and value(...)",
                        ))
                    }
                })?;
                meta.map = Some(map);
                Ok(())
            } else {
                Err(syn::Error::new(
                    nested.path.span(),
                    "unsupported fory field attribute",
                ))
            }
        })?;
    }

    validate_nested_metadata_shape(field, &meta)?;
    Ok(meta)
}

pub fn effective_type_id_for_type_name(type_name: &str, meta: &ForyFieldMeta) -> Option<u32> {
    meta.encoding
        .and_then(|enc| enc.to_type_id_for_type_name(type_name))
}

pub fn validate_field_metas(fields_with_meta: &[(&Field, ForyFieldMeta)]) -> syn::Result<()> {
    let mut id_to_field: HashMap<i32, &syn::Ident> = HashMap::new();

    for (field, meta) in fields_with_meta {
        if meta.skip {
            continue;
        }

        if let Some(id) = meta.id {
            if id >= 0 {
                if let Some(existing) = id_to_field.get(&id) {
                    let field_name = field.ident.as_ref().unwrap();
                    return Err(syn::Error::new(
                        field_name.span(),
                        format!(
                            "duplicate fory field id={} on fields '{}' and '{}'",
                            id, existing, field_name
                        ),
                    ));
                }
                id_to_field.insert(id, field.ident.as_ref().unwrap());
            }
        }
    }

    Ok(())
}

fn extract_outer_type_name(ty: &Type) -> String {
    match ty {
        Type::Path(type_path) => {
            if let Some(seg) = type_path.path.segments.last() {
                seg.ident.to_string()
            } else {
                String::new()
            }
        }
        _ => String::new(),
    }
}

pub fn extract_option_inner_type(ty: &Type) -> Option<Type> {
    if let Type::Path(type_path) = ty {
        if let Some(seg) = type_path.path.segments.last() {
            if seg.ident == "Option" {
                if let PathArguments::AngleBracketed(args) = &seg.arguments {
                    if let Some(GenericArgument::Type(inner_ty)) = args.args.first() {
                        return Some(inner_ty.clone());
                    }
                }
            }
        }
    }
    None
}

pub fn is_option_type(ty: &Type) -> bool {
    extract_outer_type_name(ty) == "Option"
}

pub fn classify_field_type(ty: &Type) -> FieldTypeClass {
    let type_name = extract_outer_type_name(ty);
    match type_name.as_str() {
        "i8" | "i16" | "i32" | "i64" | "i128" | "isize" | "u8" | "u16" | "u32" | "u64" | "u128"
        | "usize" | "f32" | "f64" | "bool" => FieldTypeClass::Primitive,
        "Option" => {
            if let Some(inner) = extract_option_inner_type(ty) {
                let inner_class = classify_field_type(&inner);
                if matches!(
                    inner_class,
                    FieldTypeClass::Rc
                        | FieldTypeClass::Arc
                        | FieldTypeClass::RcWeak
                        | FieldTypeClass::ArcWeak
                ) {
                    return inner_class;
                }
            }
            FieldTypeClass::Option
        }
        "Rc" => FieldTypeClass::Rc,
        "Arc" => FieldTypeClass::Arc,
        "RcWeak" => FieldTypeClass::RcWeak,
        "ArcWeak" => FieldTypeClass::ArcWeak,
        _ => FieldTypeClass::Other,
    }
}

#[allow(dead_code)]
pub fn get_field_flags(field: &Field, meta: &ForyFieldMeta) -> (bool, bool) {
    let type_class = classify_field_type(&field.ty);
    let nullable = meta.effective_nullable(type_class);
    let ref_flag = meta.effective_ref(type_class);
    (nullable, ref_flag)
}

#[allow(dead_code)]
pub fn parse_and_validate_fields<'a>(
    fields: &'a [&'a Field],
) -> syn::Result<Vec<(&'a Field, ForyFieldMeta)>> {
    let fields_with_meta: Vec<_> = fields
        .iter()
        .map(|f| {
            let meta = parse_field_meta(f)?;
            Ok((*f, meta))
        })
        .collect::<syn::Result<_>>()?;

    validate_field_metas(&fields_with_meta)?;

    Ok(fields_with_meta)
}

pub fn is_skip_field(field: &Field) -> bool {
    parse_field_meta(field).is_ok_and(|meta| meta.skip)
}

#[allow(dead_code)]
pub fn type_to_string(ty: &Type) -> String {
    ty.to_token_stream()
        .to_string()
        .chars()
        .filter(|c| !c.is_whitespace())
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;
    use syn::parse_quote;

    #[test]
    fn test_parse_id_only() {
        let field: Field = parse_quote! {
            #[fory(id = 0)]
            name: String
        };
        let meta = parse_field_meta(&field).unwrap();
        assert_eq!(meta.id, Some(0));
        assert_eq!(meta.nullable, None);
        assert_eq!(meta.r#ref, None);
        assert!(!meta.skip);
    }

    #[test]
    fn test_parse_full_attributes() {
        let field: Field = parse_quote! {
            #[fory(id = 1, nullable = true, ref = false)]
            data: Vec<u8>
        };
        let meta = parse_field_meta(&field).unwrap();
        assert_eq!(meta.id, Some(1));
        assert_eq!(meta.nullable, Some(true));
        assert_eq!(meta.r#ref, Some(false));
    }

    #[test]
    fn test_parse_standalone_flags() {
        let field: Field = parse_quote! {
            #[fory(id = 2, nullable, ref)]
            data: String
        };
        let meta = parse_field_meta(&field).unwrap();
        assert_eq!(meta.id, Some(2));
        assert_eq!(meta.nullable, Some(true));
        assert_eq!(meta.r#ref, Some(true));
    }

    #[test]
    fn test_parse_skip() {
        let field: Field = parse_quote! {
            #[fory(skip)]
            secret: String
        };
        let meta = parse_field_meta(&field).unwrap();
        assert!(meta.skip);
    }

    #[test]
    fn test_validate_duplicate_ids() {
        let field1: Field = parse_quote! {
            #[fory(id = 0)]
            name: String
        };
        let field2: Field = parse_quote! {
            #[fory(id = 0)]
            other: String
        };
        let meta1 = parse_field_meta(&field1).unwrap();
        let meta2 = parse_field_meta(&field2).unwrap();
        let result = validate_field_metas(&[(&field1, meta1), (&field2, meta2)]);
        assert!(result.is_err());
    }

    #[test]
    fn test_classify_primitive_types() {
        let field: Field = parse_quote! { x: i32 };
        assert_eq!(classify_field_type(&field.ty), FieldTypeClass::Primitive);
        let field: Field = parse_quote! { x: f64 };
        assert_eq!(classify_field_type(&field.ty), FieldTypeClass::Primitive);
        let field: Field = parse_quote! { x: bool };
        assert_eq!(classify_field_type(&field.ty), FieldTypeClass::Primitive);
    }

    #[test]
    fn test_classify_option_types() {
        let field: Field = parse_quote! { x: Option<String> };
        assert_eq!(classify_field_type(&field.ty), FieldTypeClass::Option);
        let field: Field = parse_quote! { x: Option<i32> };
        assert_eq!(classify_field_type(&field.ty), FieldTypeClass::Option);
    }

    #[test]
    fn test_classify_shared_ownership_types() {
        let field: Field = parse_quote! { x: Rc<String> };
        assert_eq!(classify_field_type(&field.ty), FieldTypeClass::Rc);
        let field: Field = parse_quote! { x: Arc<Vec<u8>> };
        assert_eq!(classify_field_type(&field.ty), FieldTypeClass::Arc);
        let field: Field = parse_quote! { x: RcWeak<String> };
        assert_eq!(classify_field_type(&field.ty), FieldTypeClass::RcWeak);
        let field: Field = parse_quote! { x: ArcWeak<i32> };
        assert_eq!(classify_field_type(&field.ty), FieldTypeClass::ArcWeak);
    }

    #[test]
    fn test_classify_other_types() {
        let field: Field = parse_quote! { x: String };
        assert_eq!(classify_field_type(&field.ty), FieldTypeClass::Other);
        let field: Field = parse_quote! { x: Vec<i32> };
        assert_eq!(classify_field_type(&field.ty), FieldTypeClass::Other);
        let field: Field = parse_quote! { x: HashMap<String, i32> };
        assert_eq!(classify_field_type(&field.ty), FieldTypeClass::Other);
    }

    #[test]
    fn test_classify_option_with_shared_types() {
        let field: Field = parse_quote! { x: Option<Rc<String>> };
        assert_eq!(classify_field_type(&field.ty), FieldTypeClass::Rc);
        let field: Field = parse_quote! { x: Option<Arc<Vec<u8>>> };
        assert_eq!(classify_field_type(&field.ty), FieldTypeClass::Arc);
    }

    #[test]
    fn test_effective_nullable_defaults() {
        let meta = ForyFieldMeta::default();
        assert!(!meta.effective_nullable(FieldTypeClass::Primitive));
        assert!(meta.effective_nullable(FieldTypeClass::Option));
        assert!(!meta.effective_nullable(FieldTypeClass::Rc));
        assert!(meta.effective_nullable(FieldTypeClass::RcWeak));
        assert!(meta.effective_nullable(FieldTypeClass::ArcWeak));
        assert!(!meta.effective_nullable(FieldTypeClass::Other));
    }

    #[test]
    fn test_effective_ref_defaults() {
        let meta = ForyFieldMeta::default();
        assert!(!meta.effective_ref(FieldTypeClass::Primitive));
        assert!(!meta.effective_ref(FieldTypeClass::Option));
        assert!(meta.effective_ref(FieldTypeClass::Rc));
        assert!(meta.effective_ref(FieldTypeClass::Arc));
        assert!(meta.effective_ref(FieldTypeClass::RcWeak));
        assert!(meta.effective_ref(FieldTypeClass::ArcWeak));
        assert!(!meta.effective_ref(FieldTypeClass::Other));
    }

    #[test]
    fn test_explicit_attribute_overrides_default() {
        let meta = ForyFieldMeta {
            id: None,
            nullable: Some(false),
            r#ref: Some(false),
            skip: false,
            encoding: None,
            list: None,
            map: None,
        };
        assert!(!meta.effective_nullable(FieldTypeClass::Option));
        assert!(!meta.effective_ref(FieldTypeClass::Rc));

        let meta = ForyFieldMeta {
            id: None,
            nullable: Some(true),
            r#ref: Some(true),
            skip: false,
            encoding: None,
            list: None,
            map: None,
        };
        assert!(meta.effective_nullable(FieldTypeClass::Primitive));
        assert!(meta.effective_ref(FieldTypeClass::Primitive));
    }

    #[test]
    fn test_parse_compress_attribute() {
        let field: Field = parse_quote! {
            #[fory(compress = false)]
            value: u32
        };
        let meta = parse_field_meta(&field).unwrap();
        assert_eq!(meta.encoding, Some(EncodingHint::Fixed));

        let field: Field = parse_quote! {
            #[fory(compress = true)]
            value: u32
        };
        let meta = parse_field_meta(&field).unwrap();
        assert_eq!(meta.encoding, Some(EncodingHint::Varint));
    }

    #[test]
    fn test_parse_encoding_attribute() {
        let field: Field = parse_quote! {
            #[fory(encoding = "varint")]
            value: u64
        };
        let meta = parse_field_meta(&field).unwrap();
        assert_eq!(meta.encoding, Some(EncodingHint::Varint));

        let field: Field = parse_quote! {
            #[fory(encoding = "fixed")]
            value: u64
        };
        let meta = parse_field_meta(&field).unwrap();
        assert_eq!(meta.encoding, Some(EncodingHint::Fixed));

        let field: Field = parse_quote! {
            #[fory(encoding = "tagged")]
            value: u64
        };
        let meta = parse_field_meta(&field).unwrap();
        assert_eq!(meta.encoding, Some(EncodingHint::Tagged));
    }

    #[test]
    fn test_parse_nested_list_map_annotations() {
        let field: Field = parse_quote! {
            #[fory(list(element(encoding = "fixed")))]
            value: Vec<i32>
        };
        let meta = parse_field_meta(&field).unwrap();
        assert_eq!(
            meta.list.as_ref().unwrap().element.encoding,
            Some(EncodingHint::Fixed)
        );

        let field: Field = parse_quote! {
            #[fory(map(key(encoding = "fixed"), value(nullable = true, encoding = "fixed")))]
            value: std::collections::HashMap<Option<i32>, Option<i32>>
        };
        let meta = parse_field_meta(&field).unwrap();
        let map = meta.map.unwrap();
        assert_eq!(map.key.encoding, Some(EncodingHint::Fixed));
        assert_eq!(map.value.encoding, Some(EncodingHint::Fixed));
        assert_eq!(map.value.nullable, Some(true));

        let field: Field = parse_quote! {
            #[fory(list(element(encoding = "fixed")))]
            value: Option<Vec<i32>>
        };
        let meta = parse_field_meta(&field).unwrap();
        assert_eq!(
            meta.list.as_ref().unwrap().element.encoding,
            Some(EncodingHint::Fixed)
        );
    }

    #[test]
    fn test_type_id_removed() {
        let field: Field = parse_quote! {
            #[fory(type_id = "union")]
            value: u32
        };
        assert!(parse_field_meta(&field).is_err());
    }

    #[test]
    fn test_malformed_list_annotation_rejected() {
        let field: Field = parse_quote! {
            #[fory(list(key(encoding = "fixed")))]
            value: Vec<i32>
        };
        assert!(parse_field_meta(&field).is_err());
    }

    #[test]
    fn test_malformed_map_annotation_rejected() {
        let field: Field = parse_quote! {
            #[fory(map(element(encoding = "fixed")))]
            value: std::collections::HashMap<i32, i32>
        };
        assert!(parse_field_meta(&field).is_err());
    }

    #[test]
    fn test_malformed_element_annotation_rejected() {
        let field: Field = parse_quote! {
            #[fory(list(element(ref = true)))]
            value: Vec<i32>
        };
        assert!(parse_field_meta(&field).is_err());
    }

    #[test]
    fn test_list_annotation_requires_list_field() {
        let field: Field = parse_quote! {
            #[fory(list(element(encoding = "fixed")))]
            value: i32
        };
        assert!(parse_field_meta(&field).is_err());
    }

    #[test]
    fn test_map_annotation_requires_map_field() {
        let field: Field = parse_quote! {
            #[fory(map(key(encoding = "fixed"), value(encoding = "fixed")))]
            value: Vec<i32>
        };
        assert!(parse_field_meta(&field).is_err());
    }

    #[test]
    fn test_scalar_encoding_rejected_for_container_field() {
        let field: Field = parse_quote! {
            #[fory(encoding = "fixed")]
            value: Vec<i32>
        };
        assert!(parse_field_meta(&field).is_err());
    }

    #[test]
    fn test_list_and_map_annotations_cannot_coexist() {
        let field: Field = parse_quote! {
            #[fory(list(element(encoding = "fixed")), map(key(encoding = "fixed"), value(encoding = "fixed")))]
            value: HashMap<i32, i32>
        };
        assert!(parse_field_meta(&field).is_err());
    }
}
