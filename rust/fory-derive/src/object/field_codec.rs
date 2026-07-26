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

use super::field_meta::{
    classify_field_type, extract_option_inner_type, is_option_type, parse_field_meta,
    ForyFieldMeta, IntEncoding,
};
use super::read::create_private_field_name;
use super::util::{trait_object_is_any_send_sync, trait_object_is_any_without_auto_traits};
use crate::util::{is_arc_dyn_trait, is_box_dyn_trait, is_rc_dyn_trait, SourceField};
use proc_macro2::TokenStream;
use quote::{format_ident, quote, ToTokens};
use syn::{GenericArgument, PathArguments, Type};

struct CodecSelection {
    ty: TokenStream,
}

impl CodecSelection {
    fn plain(ty: TokenStream) -> Self {
        Self { ty }
    }
}

pub(crate) struct ResolvedField<'a> {
    pub source: &'a SourceField<'a>,
    pub private_ident: syn::Ident,
    pub codec_ty: TokenStream,
    pub value_ty: &'a Type,
    pub field_id: i16,
    pub has_selected_provider: bool,
}

impl<'a> ResolvedField<'a> {
    #[inline]
    pub fn codec_call(&self) -> TokenStream {
        let codec_ty = &self.codec_ty;
        let value_ty = self.value_ty;
        quote! { <#codec_ty as fory_core::serializer::codec::Codec<#value_ty>> }
    }

    pub fn reserved_space(&self) -> TokenStream {
        let call = self.codec_call();
        quote! { #call::reserved_space() }
    }

    pub fn write_field(&self) -> TokenStream {
        let access = super::util::get_field_accessor(self.source.field, self.source.original_index);
        self.write_value_field(quote! { &#access })
    }

    pub fn write_value_field(&self, value: TokenStream) -> TokenStream {
        let call = self.codec_call();
        quote! {
            #call::write_field(#value, context)?;
        }
    }

    pub fn write_value_with_mode(
        &self,
        value: TokenStream,
        ref_mode: TokenStream,
        write_type_info: TokenStream,
    ) -> TokenStream {
        let call = self.codec_call();
        quote! {
            #call::write_with_mode(
                #value,
                context,
                #ref_mode,
                #write_type_info,
                false,
            )?;
        }
    }

    pub fn read_field(&self) -> TokenStream {
        let var = &self.private_ident;
        let call = self.codec_call();
        quote! {
            let #var = #call::read_field(context)?;
        }
    }

    pub fn read_with_mode_expr(
        &self,
        ref_mode: TokenStream,
        read_type_info: TokenStream,
    ) -> TokenStream {
        let call = self.codec_call();
        quote! { #call::read_with_mode(context, #ref_mode, #read_type_info)? }
    }

    pub fn default_value_expr(&self) -> TokenStream {
        let call = self.codec_call();
        quote! { #call::default_value(context)? }
    }

    pub fn declare_compatible_var(&self) -> TokenStream {
        let var = &self.private_ident;
        let ty = self.value_ty;
        quote! {
            let mut #var: ::std::option::Option<#ty> = ::std::option::Option::None;
        }
    }

    pub fn assign_value(&self) -> TokenStream {
        let var = &self.private_ident;
        let default = self.default_value_expr();
        quote! {
            match #var {
                ::std::option::Option::Some(value) => value,
                ::std::option::Option::None => #default,
            }
        }
    }

    pub fn read_compatible_direct(&self) -> TokenStream {
        let var = &self.private_ident;
        let call = self.codec_call();
        quote! {
            #var = ::std::option::Option::Some(#call::read_field(context)?);
        }
    }

    pub fn read_compatible_conversion(&self) -> TokenStream {
        let var = &self.private_ident;
        // A selected serializer targeting a Rust scalar can still be an EXT-shaped schema leaf. Keep it on the
        // selected codec path instead of bypassing its body through scalar conversion.
        if !self.has_selected_provider {
            if let Some(read_scalar) = compatible_scalar_reader_for(self.value_ty) {
                let call = self.codec_call();
                let local_type = if extract_option_inner_type(self.value_ty).is_some() {
                    quote! { local_field_type.type_id }
                } else {
                    quote! { #call::static_type_id() as u32 }
                };
                return quote! {
                    #var = ::std::option::Option::Some(#read_scalar(
                        context,
                        #local_type,
                        _field,
                    ).map_err(|err| fory_core::Error::invalid_data(
                        format!("compatible field '{}': {}", _field.field_name.as_str(), err)
                    ))?);
                };
            }
        }
        let call = self.codec_call();
        quote! {
            let remote_field_type = &_field.field_type;
            if let Some(value) = #call::read_compatible(
                context,
                local_field_type,
                remote_field_type,
            ).map_err(|err| fory_core::Error::invalid_data(
                format!("compatible field '{}': {}", _field.field_name.as_str(), err)
            ))? {
                #var = ::std::option::Option::Some(value);
            } else {
                return Err(fory_core::Error::invalid_data(format!(
                    "compatible field '{}' cannot convert remote type {} to local type {}",
                    _field.field_name.as_str(),
                    remote_field_type.type_id,
                    local_field_type.type_id,
                )));
            }
        }
    }

    pub fn compatible_needs_local_field_type(&self) -> bool {
        self.has_selected_provider
            || compatible_scalar_reader_for(self.value_ty).is_none()
            || extract_option_inner_type(self.value_ty).is_some()
    }

    pub fn field_info(&self) -> TokenStream {
        let field_id = self.field_id;
        let name = &self.source.field_name;
        let call = self.codec_call();
        quote! {{
            fory_core::meta::FieldInfo::new_with_id(
                #field_id,
                #name,
                #call::field_type(type_resolver)?
            )
        }}
    }
}

pub(crate) struct SkippedField<'a> {
    pub source: &'a SourceField<'a>,
    pub private_ident: syn::Ident,
    pub codec_ty: TokenStream,
}

impl<'a> SkippedField<'a> {
    pub fn read_default(&self) -> TokenStream {
        let var = &self.private_ident;
        let ty = &self.source.field.ty;
        let codec_ty = &self.codec_ty;
        quote! {
            let #var =
                <#codec_ty as fory_core::serializer::codec::Codec<#ty>>::default_value(context)?;
        }
    }

    pub fn default_value_expr(&self) -> TokenStream {
        let ty = &self.source.field.ty;
        let codec_ty = &self.codec_ty;
        quote! {{
            <#codec_ty as fory_core::serializer::codec::Codec<#ty>>::default_value(context)?
        }}
    }

    pub fn assign_value(&self) -> TokenStream {
        let var = &self.private_ident;
        quote! { #var }
    }
}

pub(crate) enum FieldBinding<'a> {
    Codec(ResolvedField<'a>),
    Skipped(SkippedField<'a>),
}

impl FieldBinding<'_> {
    pub fn default_value_expr(&self) -> TokenStream {
        match self {
            Self::Codec(binding) => binding.default_value_expr(),
            Self::Skipped(binding) => binding.default_value_expr(),
        }
    }
}

pub(crate) fn build_bindings<'a>(
    source_fields: &'a [SourceField<'a>],
) -> syn::Result<Vec<FieldBinding<'a>>> {
    source_fields
        .iter()
        .map(|source| {
            let meta = parse_field_meta(source.field)?;
            let private_ident = create_private_field_name(source.field, source.original_index);
            let type_class = classify_field_type(&source.field.ty);
            let nullable = meta.effective_nullable(type_class) || is_option_type(&source.field.ty);
            let track_ref = meta.effective_ref(type_class);
            let selection = codec_selection_for(&source.field.ty, &meta, nullable, track_ref)?;
            if meta.skip {
                return Ok(FieldBinding::Skipped(SkippedField {
                    source,
                    private_ident,
                    codec_ty: selection.ty,
                }));
            }
            let field_id = if meta.uses_tag_id() {
                meta.effective_id() as i16
            } else {
                -1
            };
            Ok(FieldBinding::Codec(ResolvedField {
                source,
                private_ident,
                codec_ty: selection.ty,
                value_ty: &source.field.ty,
                field_id,
                has_selected_provider: meta.with.is_some(),
            }))
        })
        .collect()
}

#[cfg(test)]
pub(crate) fn codec_type_for(
    ty: &Type,
    meta: &ForyFieldMeta,
    nullable: bool,
    track_ref: bool,
) -> syn::Result<TokenStream> {
    Ok(codec_selection_for(ty, meta, nullable, track_ref)?.ty)
}

fn codec_selection_for(
    ty: &Type,
    meta: &ForyFieldMeta,
    nullable: bool,
    track_ref: bool,
) -> syn::Result<CodecSelection> {
    if is_dynamic_trait_carrier(ty) {
        if let Some(provider) = &meta.with {
            return Err(syn::Error::new_spanned(
                provider,
                "with cannot select a serializer for a dynamic Any or application-trait node",
            ));
        }
        validate_no_shape(
            ty,
            meta,
            "dynamic Any and application-trait nodes do not accept structural field config",
        )?;
        return dynamic_codec_for(ty, nullable, track_ref);
    }

    if let Some(provider) = &meta.with {
        return Ok(CodecSelection::plain(quote! {
            fory_core::serializer::codec::SerializerCodec<
                #provider,
                #nullable,
                #track_ref
            >
        }));
    }

    if let Some(inner) = extract_option_inner_type(ty) {
        let child_meta = transparent_child_meta(meta);
        let child_ty = codec_for_child(&inner, &child_meta)?.ty;
        return Ok(CodecSelection::plain(quote! {
            fory_core::serializer::codec::OptionCodec<
                #inner,
                #child_ty,
                #track_ref
            >
        }));
    }

    if let Some((name, Some(args))) = type_name_and_args(ty) {
        if is_transparent_carrier(&name) {
            let inner = single_type_arg(args, ty, &name)?;
            let child_meta = transparent_child_meta(meta);
            let child_ty = codec_for_child(inner, &child_meta)?.ty;
            let codec_ident = format_ident!("{name}Codec");
            return Ok(CodecSelection::plain(quote! {
                fory_core::serializer::codec::#codec_ident<
                    #inner,
                    #child_ty,
                    #nullable,
                    #track_ref
                >
            }));
        }
    }

    if let Type::Array(array) = ty {
        validate_array_meta(ty, meta)?;
        let elem_ty = array.elem.as_ref();
        let elem_meta = meta.element_meta();
        let child = codec_for_child(elem_ty, &elem_meta)?;
        let child_ty = child.ty;
        let len = &array.len;
        return Ok(CodecSelection {
            ty: quote! {
                fory_core::serializer::codec::ArrayCodec<
                    #elem_ty,
                    #child_ty,
                    #len,
                    #nullable,
                    #track_ref
                >
            },
        });
    }

    if let Type::Tuple(tuple) = ty {
        return tuple_codec_for(tuple, ty, meta, nullable, track_ref);
    }

    if let Some((name, Some(args))) = type_name_and_args(ty) {
        if is_one_child_collection(&name) {
            validate_collection_meta(ty, meta, &name)?;
            let elem_ty = single_type_arg(args, ty, &name)?;
            if name == "Vec" {
                validate_vec_schema(elem_ty, meta)?;
            }
            let elem_meta = meta.element_meta();
            let child = codec_for_child(elem_ty, &elem_meta)?;
            let child_ty = child.ty;
            let codec_ident = format_ident!("{name}Codec");
            let structural_list = !meta.array && !meta.bytes;
            let dense_array = meta.array;
            let vec_schema_args = if name == "Vec" {
                quote! { #structural_list, #dense_array, }
            } else {
                quote! {}
            };
            return Ok(CodecSelection {
                ty: quote! {
                    fory_core::serializer::codec::#codec_ident<
                        #elem_ty,
                        #child_ty,
                        #vec_schema_args
                        #nullable,
                        #track_ref
                    >
                },
            });
        }
        if is_map(&name) {
            validate_map_meta(ty, meta, &name)?;
            let (key_ty, value_ty) = two_type_args(args, ty, &name)?;
            let key = codec_for_child(key_ty, &meta.map_key_meta())?;
            let value = codec_for_child(value_ty, &meta.map_value_meta())?;
            let key_codec = key.ty;
            let value_codec = value.ty;
            let codec_ident = format_ident!("{name}Codec");
            return Ok(CodecSelection {
                ty: quote! {
                    fory_core::serializer::codec::#codec_ident<
                        #key_ty,
                        #value_ty,
                        #key_codec,
                        #value_codec,
                        #nullable,
                        #track_ref
                    >
                },
            });
        }
    }

    validate_leaf_meta(ty, meta)?;
    if let Some(codec) = integer_codec_type(ty, meta, nullable, track_ref) {
        return Ok(CodecSelection::plain(codec));
    }
    Ok(CodecSelection::plain(quote! {
        fory_core::serializer::codec::SerializerCodec<#ty, #nullable, #track_ref>
    }))
}

fn codec_for_child(ty: &Type, meta: &ForyFieldMeta) -> syn::Result<CodecSelection> {
    let class = classify_field_type(ty);
    codec_selection_for(
        ty,
        meta,
        meta.effective_nullable(class) || is_option_type(ty),
        meta.effective_ref(class),
    )
}

fn transparent_child_meta(meta: &ForyFieldMeta) -> ForyFieldMeta {
    let mut child = meta.clone();
    child.id = None;
    child.nullable = None;
    child.r#ref = None;
    child.skip = false;
    child
}

fn is_dynamic_trait_carrier(ty: &Type) -> bool {
    any_trait_object_for(ty, "Box").is_some()
        || any_trait_object_for(ty, "Rc").is_some()
        || any_trait_object_for(ty, "Arc").is_some()
        || is_box_dyn_trait(ty).is_some()
        || is_rc_dyn_trait(ty).is_some()
        || is_arc_dyn_trait(ty).is_some()
}

fn dynamic_codec_for(ty: &Type, nullable: bool, track_ref: bool) -> syn::Result<CodecSelection> {
    if let Some(trait_obj) = any_trait_object_for(ty, "Box") {
        if trait_object_is_any_without_auto_traits(trait_obj) {
            return Ok(CodecSelection::plain(quote! {
                fory_core::serializer::codec::AnyBoxCodec<#nullable, #track_ref>
            }));
        }
        return Err(syn::Error::new_spanned(
            ty,
            "Box<dyn Any> is the supported owned Any carrier",
        ));
    }
    if let Some(trait_obj) = any_trait_object_for(ty, "Rc") {
        if trait_object_is_any_without_auto_traits(trait_obj) {
            return Ok(CodecSelection::plain(quote! {
                fory_core::serializer::codec::AnyRcCodec<#nullable, #track_ref>
            }));
        }
        return Err(syn::Error::new_spanned(
            ty,
            "Rc<dyn Any> is the supported single-thread Any carrier",
        ));
    }
    if let Some(trait_obj) = any_trait_object_for(ty, "Arc") {
        if trait_object_is_any_send_sync(trait_obj) {
            return Ok(CodecSelection::plain(quote! {
                fory_core::serializer::codec::AnyArcCodec<#nullable, #track_ref>
            }));
        }
        return Err(syn::Error::new_spanned(
            ty,
            "Arc<dyn Any> must include Send + Sync",
        ));
    }
    if let Some((_, trait_name)) = is_box_dyn_trait(ty) {
        let codec_ident = format_ident!("{trait_name}BoxCodec");
        return Ok(CodecSelection::plain(
            quote! { #codec_ident<#nullable, #track_ref> },
        ));
    }
    if let Some((_, trait_name)) = is_rc_dyn_trait(ty) {
        let codec_ident = format_ident!("{trait_name}RcCodec");
        return Ok(CodecSelection::plain(
            quote! { #codec_ident<#nullable, #track_ref> },
        ));
    }
    if let Some((_, trait_name)) = is_arc_dyn_trait(ty) {
        let codec_ident = format_ident!("{trait_name}ArcCodec");
        return Ok(CodecSelection::plain(
            quote! { #codec_ident<#nullable, #track_ref> },
        ));
    }
    Err(syn::Error::new_spanned(
        ty,
        "unsupported dynamic trait carrier",
    ))
}

fn tuple_codec_for(
    tuple: &syn::TypeTuple,
    ty: &Type,
    meta: &ForyFieldMeta,
    nullable: bool,
    track_ref: bool,
) -> syn::Result<CodecSelection> {
    validate_tuple_meta(ty, meta, tuple.elems.len())?;
    if tuple.elems.is_empty() {
        return Ok(CodecSelection::plain(quote! {
            fory_core::serializer::codec::SerializerCodec<#ty, #nullable, #track_ref>
        }));
    }
    let codec_ident = format_ident!("Tuple{}Codec", tuple.elems.len());
    let mut args = Vec::with_capacity(tuple.elems.len() * 2);
    for (index, elem_ty) in tuple.elems.iter().enumerate() {
        let child = codec_for_child(elem_ty, &meta.tuple_element_meta(index))?;
        let child_ty = child.ty;
        args.push(quote! { #elem_ty });
        args.push(child_ty);
    }
    Ok(CodecSelection {
        ty: quote! {
            fory_core::serializer::codec::#codec_ident<
                #(#args,)*
                #nullable,
                #track_ref
            >
        },
    })
}

fn validate_no_shape(ty: &Type, meta: &ForyFieldMeta, message: &str) -> syn::Result<()> {
    if has_shape(meta) || meta.encoding.is_some() {
        return Err(syn::Error::new_spanned(ty, message));
    }
    Ok(())
}

fn validate_collection_meta(ty: &Type, meta: &ForyFieldMeta, name: &str) -> syn::Result<()> {
    if meta.map.is_some() || meta.tuple.is_some() || meta.encoding.is_some() {
        return Err(syn::Error::new_spanned(
            ty,
            format!("{name} accepts only list(element(...)) collection config"),
        ));
    }
    if (meta.array || meta.bytes) && name != "Vec" {
        return Err(syn::Error::new_spanned(
            ty,
            "array and bytes are valid only for Vec fields",
        ));
    }
    Ok(())
}

fn validate_vec_schema(elem_ty: &Type, meta: &ForyFieldMeta) -> syn::Result<()> {
    let elem_name = type_name_and_args(elem_ty)
        .map(|(name, _)| name)
        .unwrap_or_else(|| elem_ty.to_token_stream().to_string().replace(' ', ""));
    if meta.bytes && elem_name != "u8" {
        return Err(syn::Error::new_spanned(
            elem_ty,
            "bytes schema requires Vec<u8>",
        ));
    }
    if meta.array
        && !matches!(
            elem_name.as_str(),
            "bool"
                | "i8"
                | "i16"
                | "i32"
                | "i64"
                | "i128"
                | "isize"
                | "u8"
                | "u16"
                | "u32"
                | "u64"
                | "u128"
                | "usize"
                | "float16"
                | "Float16"
                | "bfloat16"
                | "BFloat16"
                | "f32"
                | "f64"
        )
    {
        return Err(syn::Error::new_spanned(
            elem_ty,
            "array schema requires a canonical number or bool Vec element type",
        ));
    }
    Ok(())
}

fn validate_map_meta(ty: &Type, meta: &ForyFieldMeta, name: &str) -> syn::Result<()> {
    if meta.list.is_some()
        || meta.tuple.is_some()
        || meta.encoding.is_some()
        || meta.array
        || meta.bytes
    {
        return Err(syn::Error::new_spanned(
            ty,
            format!("{name} accepts only map(key(...), value(...)) config"),
        ));
    }
    Ok(())
}

fn validate_array_meta(ty: &Type, meta: &ForyFieldMeta) -> syn::Result<()> {
    if meta.map.is_some()
        || meta.tuple.is_some()
        || meta.encoding.is_some()
        || meta.array
        || meta.bytes
    {
        return Err(syn::Error::new_spanned(
            ty,
            "fixed arrays accept only list(element(...)) config",
        ));
    }
    Ok(())
}

fn validate_tuple_meta(ty: &Type, meta: &ForyFieldMeta, arity: usize) -> syn::Result<()> {
    if meta.list.is_some()
        || meta.map.is_some()
        || meta.encoding.is_some()
        || meta.array
        || meta.bytes
    {
        return Err(syn::Error::new_spanned(
            ty,
            "tuple fields accept only tuple(element(index = N, ...)) config",
        ));
    }
    if arity > 22 {
        return Err(syn::Error::new_spanned(
            ty,
            "tuple serialization supports arities 1 through 22",
        ));
    }
    if let Some(tuple) = &meta.tuple {
        if arity == 0 {
            return Err(syn::Error::new_spanned(
                ty,
                "tuple(...) is not valid for the unit tuple",
            ));
        }
        if let Some(element) = tuple.elements.iter().find(|element| element.index >= arity) {
            return Err(syn::Error::new_spanned(
                ty,
                format!(
                    "tuple element index {} is outside tuple arity {}",
                    element.index, arity
                ),
            ));
        }
    }
    Ok(())
}

fn validate_leaf_meta(ty: &Type, meta: &ForyFieldMeta) -> syn::Result<()> {
    if meta.list.is_some() {
        return Err(syn::Error::new_spanned(
            ty,
            "list(...) requires a one-child collection",
        ));
    }
    if meta.map.is_some() {
        return Err(syn::Error::new_spanned(
            ty,
            "map(...) requires HashMap or BTreeMap",
        ));
    }
    if meta.tuple.is_some() {
        return Err(syn::Error::new_spanned(
            ty,
            "tuple(...) requires a Rust tuple",
        ));
    }
    if meta.array || meta.bytes {
        return Err(syn::Error::new_spanned(ty, "array and bytes require Vec"));
    }
    Ok(())
}

fn has_shape(meta: &ForyFieldMeta) -> bool {
    meta.list.is_some() || meta.map.is_some() || meta.tuple.is_some() || meta.array || meta.bytes
}

fn is_transparent_carrier(name: &str) -> bool {
    matches!(
        name,
        "Box" | "Rc" | "Arc" | "RcWeak" | "ArcWeak" | "RefCell" | "Mutex"
    )
}

fn is_one_child_collection(name: &str) -> bool {
    matches!(
        name,
        "Vec" | "VecDeque" | "LinkedList" | "HashSet" | "BTreeSet" | "BinaryHeap"
    )
}

fn is_map(name: &str) -> bool {
    matches!(name, "HashMap" | "BTreeMap")
}

fn integer_codec_type(
    ty: &Type,
    meta: &ForyFieldMeta,
    nullable: bool,
    track_ref: bool,
) -> Option<TokenStream> {
    let type_name = type_name_and_args(ty)
        .map(|(name, _)| name)
        .unwrap_or_else(|| ty.to_token_stream().to_string().replace(' ', ""));
    let encoding = meta.encoding.unwrap_or(IntEncoding::Varint);
    match type_name.as_str() {
        "i32" => {
            let wire = match encoding {
                IntEncoding::Fixed => quote! { { fory_core::type_id::TypeId::INT32 as u8 } },
                IntEncoding::Varint => {
                    quote! { { fory_core::type_id::TypeId::VARINT32 as u8 } }
                }
                IntEncoding::Tagged => {
                    return Some(quote! {
                        compile_error!("encoding = tagged is only valid for 64-bit integer fields")
                    });
                }
            };
            Some(quote! { fory_core::serializer::codec::I32Codec<#wire, #nullable, #track_ref> })
        }
        "i64" => {
            let wire = match encoding {
                IntEncoding::Fixed => quote! { { fory_core::type_id::TypeId::INT64 as u8 } },
                IntEncoding::Varint => {
                    quote! { { fory_core::type_id::TypeId::VARINT64 as u8 } }
                }
                IntEncoding::Tagged => {
                    quote! { { fory_core::type_id::TypeId::TAGGED_INT64 as u8 } }
                }
            };
            Some(quote! { fory_core::serializer::codec::I64Codec<#wire, #nullable, #track_ref> })
        }
        "u32" => {
            let wire = match encoding {
                IntEncoding::Fixed => quote! { { fory_core::type_id::TypeId::UINT32 as u8 } },
                IntEncoding::Varint => {
                    quote! { { fory_core::type_id::TypeId::VAR_UINT32 as u8 } }
                }
                IntEncoding::Tagged => {
                    return Some(quote! {
                        compile_error!("encoding = tagged is only valid for 64-bit integer fields")
                    });
                }
            };
            Some(quote! { fory_core::serializer::codec::U32Codec<#wire, #nullable, #track_ref> })
        }
        "u64" => {
            let wire = match encoding {
                IntEncoding::Fixed => quote! { { fory_core::type_id::TypeId::UINT64 as u8 } },
                IntEncoding::Varint => {
                    quote! { { fory_core::type_id::TypeId::VAR_UINT64 as u8 } }
                }
                IntEncoding::Tagged => {
                    quote! { { fory_core::type_id::TypeId::TAGGED_UINT64 as u8 } }
                }
            };
            Some(quote! { fory_core::serializer::codec::U64Codec<#wire, #nullable, #track_ref> })
        }
        _ if meta.encoding.is_some() => Some(quote! {
            compile_error!("encoding is only valid for i32, i64, u32, and u64 fields")
        }),
        _ => None,
    }
}

fn compatible_scalar_reader_for(ty: &Type) -> Option<TokenStream> {
    if let Some(inner) = extract_option_inner_type(ty) {
        return compatible_scalar_reader_name(&inner, true);
    }
    compatible_scalar_reader_name(ty, false)
}

fn compatible_scalar_reader_name(ty: &Type, option: bool) -> Option<TokenStream> {
    let name = type_name_and_args(ty)
        .map(|(name, _)| name)
        .unwrap_or_else(|| ty.to_token_stream().to_string().replace(' ', ""));
    let reader = match (name.as_str(), option) {
        ("bool", false) => quote! { fory_core::serializer::codec::read_bool_compatible_scalar },
        ("bool", true) => {
            quote! { fory_core::serializer::codec::read_bool_option_compatible_scalar }
        }
        ("String", false) => {
            quote! { fory_core::serializer::codec::read_string_compatible_scalar }
        }
        ("String", true) => {
            quote! { fory_core::serializer::codec::read_string_option_compatible_scalar }
        }
        ("i8", false) => quote! { fory_core::serializer::codec::read_i8_compatible_scalar },
        ("i8", true) => quote! { fory_core::serializer::codec::read_i8_option_compatible_scalar },
        ("i16", false) => quote! { fory_core::serializer::codec::read_i16_compatible_scalar },
        ("i16", true) => {
            quote! { fory_core::serializer::codec::read_i16_option_compatible_scalar }
        }
        ("i32", false) => quote! { fory_core::serializer::codec::read_i32_compatible_scalar },
        ("i32", true) => {
            quote! { fory_core::serializer::codec::read_i32_option_compatible_scalar }
        }
        ("i64", false) => quote! { fory_core::serializer::codec::read_i64_compatible_scalar },
        ("i64", true) => {
            quote! { fory_core::serializer::codec::read_i64_option_compatible_scalar }
        }
        ("u8", false) => quote! { fory_core::serializer::codec::read_u8_compatible_scalar },
        ("u8", true) => quote! { fory_core::serializer::codec::read_u8_option_compatible_scalar },
        ("u16", false) => quote! { fory_core::serializer::codec::read_u16_compatible_scalar },
        ("u16", true) => {
            quote! { fory_core::serializer::codec::read_u16_option_compatible_scalar }
        }
        ("u32", false) => quote! { fory_core::serializer::codec::read_u32_compatible_scalar },
        ("u32", true) => {
            quote! { fory_core::serializer::codec::read_u32_option_compatible_scalar }
        }
        ("u64", false) => quote! { fory_core::serializer::codec::read_u64_compatible_scalar },
        ("u64", true) => {
            quote! { fory_core::serializer::codec::read_u64_option_compatible_scalar }
        }
        ("f32", false) => quote! { fory_core::serializer::codec::read_f32_compatible_scalar },
        ("f32", true) => {
            quote! { fory_core::serializer::codec::read_f32_option_compatible_scalar }
        }
        ("f64", false) => quote! { fory_core::serializer::codec::read_f64_compatible_scalar },
        ("f64", true) => {
            quote! { fory_core::serializer::codec::read_f64_option_compatible_scalar }
        }
        ("float16" | "Float16", false) => {
            quote! { fory_core::serializer::codec::read_float16_compatible_scalar }
        }
        ("float16" | "Float16", true) => {
            quote! { fory_core::serializer::codec::read_float16_option_compatible_scalar }
        }
        ("bfloat16" | "BFloat16", false) => {
            quote! { fory_core::serializer::codec::read_bfloat16_compatible_scalar }
        }
        ("bfloat16" | "BFloat16", true) => {
            quote! { fory_core::serializer::codec::read_bfloat16_option_compatible_scalar }
        }
        ("Decimal", false) => {
            quote! { fory_core::serializer::codec::read_decimal_compatible_scalar }
        }
        ("Decimal", true) => {
            quote! { fory_core::serializer::codec::read_decimal_option_compatible_scalar }
        }
        _ => return None,
    };
    Some(reader)
}

fn type_name_and_args(
    ty: &Type,
) -> Option<(
    String,
    Option<&syn::punctuated::Punctuated<GenericArgument, syn::token::Comma>>,
)> {
    let Type::Path(type_path) = ty else {
        return None;
    };
    let seg = type_path.path.segments.last()?;
    let PathArguments::AngleBracketed(args) = &seg.arguments else {
        return Some((seg.ident.to_string(), None));
    };
    Some((seg.ident.to_string(), Some(&args.args)))
}

fn single_type_arg<'a>(
    args: &'a syn::punctuated::Punctuated<GenericArgument, syn::token::Comma>,
    ty: &Type,
    owner: &str,
) -> syn::Result<&'a Type> {
    args.iter()
        .find_map(|arg| match arg {
            GenericArgument::Type(ty) => Some(ty),
            _ => None,
        })
        .ok_or_else(|| syn::Error::new_spanned(ty, format!("{owner} requires one type argument")))
}

fn two_type_args<'a>(
    args: &'a syn::punctuated::Punctuated<GenericArgument, syn::token::Comma>,
    ty: &Type,
    owner: &str,
) -> syn::Result<(&'a Type, &'a Type)> {
    let mut iter = args.iter().filter_map(|arg| match arg {
        GenericArgument::Type(ty) => Some(ty),
        _ => None,
    });
    let first = iter
        .next()
        .ok_or_else(|| syn::Error::new_spanned(ty, format!("{owner} requires key type")))?;
    let second = iter
        .next()
        .ok_or_else(|| syn::Error::new_spanned(ty, format!("{owner} requires value type")))?;
    Ok((first, second))
}

fn any_trait_object_for<'a>(ty: &'a Type, owner: &str) -> Option<&'a syn::TypeTraitObject> {
    let Type::Path(type_path) = ty else {
        return None;
    };
    let segment = type_path.path.segments.last()?;
    if segment.ident != owner {
        return None;
    }
    let PathArguments::AngleBracketed(args) = &segment.arguments else {
        return None;
    };
    let inner = args.args.iter().find_map(|arg| match arg {
        GenericArgument::Type(ty) => Some(ty),
        _ => None,
    })?;
    let Type::TraitObject(trait_obj) = inner else {
        return None;
    };
    if trait_obj.bounds.iter().any(|bound| {
        matches!(
            bound,
            syn::TypeParamBound::Trait(trait_bound)
                if trait_bound.path.segments.last().is_some_and(|segment| segment.ident == "Any")
        )
    }) {
        Some(trait_obj)
    } else {
        None
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use syn::parse_quote;

    #[test]
    fn lowers_nested_external_list() {
        let ty: Type = parse_quote!(Vec<Vec<External>>);
        let field: syn::Field = parse_quote!(
            #[fory(list(element(list(element(with = ExternalSerializer)))))]
            value: Vec<Vec<External>>
        );
        let meta = parse_field_meta(&field).unwrap();
        let codec = codec_type_for(&ty, &meta, false, false)
            .unwrap()
            .to_string();
        assert!(codec.contains("VecCodec"));
        assert!(codec.contains("ExternalSerializer"));
    }

    #[test]
    fn rejects_tuple_index_outside_arity() {
        let ty: Type = parse_quote!((String, i32));
        let field: syn::Field = parse_quote!(
            #[fory(tuple(element(index = 2, with = ExternalSerializer)))]
            value: (String, i32)
        );
        let meta = parse_field_meta(&field).unwrap();
        let error = codec_type_for(&ty, &meta, false, false).unwrap_err();
        assert!(error.to_string().contains("outside tuple arity 2"));
    }

    #[test]
    fn lowers_all_transparent_carriers() {
        for ty in [
            parse_quote!(Option<External>),
            parse_quote!(Box<External>),
            parse_quote!(Rc<External>),
            parse_quote!(Arc<External>),
            parse_quote!(RcWeak<External>),
            parse_quote!(ArcWeak<External>),
            parse_quote!(RefCell<External>),
            parse_quote!(Mutex<External>),
        ] {
            let meta = ForyFieldMeta {
                with: Some(parse_quote!(ExternalSerializer)),
                ..Default::default()
            };
            let codec = codec_type_for(&ty, &meta, false, false)
                .unwrap()
                .to_string();
            assert!(codec.contains("ExternalSerializer"));
        }
    }
}
