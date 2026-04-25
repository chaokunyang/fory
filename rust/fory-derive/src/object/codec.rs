use proc_macro2::TokenStream;
use quote::quote;
use syn::{spanned::Spanned, GenericArgument, PathArguments, Type};

use super::field_meta::{parse_field_meta, ElementMeta, EncodingHint, ForyFieldMeta};

fn option_inner_type(ty: &Type) -> Option<Type> {
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

fn type_generics(ty: &Type) -> Vec<Type> {
    if let Type::Path(type_path) = ty {
        if let Some(seg) = type_path.path.segments.last() {
            if let PathArguments::AngleBracketed(args) = &seg.arguments {
                return args
                    .args
                    .iter()
                    .filter_map(|arg| {
                        if let GenericArgument::Type(ty) = arg {
                            Some(ty.clone())
                        } else {
                            None
                        }
                    })
                    .collect();
            }
        }
    }
    Vec::new()
}

fn scalar_codec_from_hint(
    ty: &Type,
    hint: Option<EncodingHint>,
) -> syn::Result<Option<TokenStream>> {
    let Some(hint) = hint else {
        return Ok(None);
    };
    let ty_str = quote::ToTokens::to_token_stream(ty)
        .to_string()
        .replace(' ', "");
    let ts = match (ty_str.as_str(), hint) {
        ("i32", EncodingHint::Fixed) => quote! { fory_core::FixedI32Codec },
        ("i32", EncodingHint::Varint) => quote! { fory_core::VarI32Codec },
        ("u32", EncodingHint::Fixed) => quote! { fory_core::FixedU32Codec },
        ("u32", EncodingHint::Varint) => quote! { fory_core::VarU32Codec },
        ("i64", EncodingHint::Fixed) => quote! { fory_core::FixedI64Codec },
        ("i64", EncodingHint::Varint) => quote! { fory_core::VarI64Codec },
        ("i64", EncodingHint::Tagged) => quote! { fory_core::TaggedI64Codec },
        ("u64", EncodingHint::Fixed) => quote! { fory_core::FixedU64Codec },
        ("u64", EncodingHint::Varint) => quote! { fory_core::VarU64Codec },
        ("u64", EncodingHint::Tagged) => quote! { fory_core::TaggedU64Codec },
        _ => {
            return Err(syn::Error::new(
                ty.span(),
                format!("unsupported encoding hint for type {}", ty_str),
            ));
        }
    };
    Ok(Some(ts))
}

fn wrap_nullable_if_needed(
    base: TokenStream,
    elem_meta: Option<&ElementMeta>,
    ty: &Type,
) -> TokenStream {
    if elem_meta.and_then(|m| m.nullable) == Some(true) && option_inner_type(ty).is_none() {
        quote! { fory_core::OptionCodec<#base> }
    } else {
        base
    }
}

fn codec_tokens_for_type_with_elem_meta(
    ty: &Type,
    elem_meta: Option<&ElementMeta>,
) -> syn::Result<TokenStream> {
    if let Some(inner) = option_inner_type(ty) {
        let inner_meta = elem_meta.map(|meta| ElementMeta {
            nullable: None,
            encoding: meta.encoding,
        });
        let inner_codec = codec_tokens_for_type_with_elem_meta(&inner, inner_meta.as_ref())?;
        return Ok(quote! { fory_core::OptionCodec<#inner_codec> });
    }

    if let Some(ts) = scalar_codec_from_hint(ty, elem_meta.and_then(|m| m.encoding))? {
        return Ok(wrap_nullable_if_needed(ts, elem_meta, ty));
    }

    let ty_name = if let Type::Path(type_path) = ty {
        type_path
            .path
            .segments
            .last()
            .map(|s| s.ident.to_string())
            .unwrap_or_default()
    } else {
        String::new()
    };

    if ty_name == "Vec" {
        let args = type_generics(ty);
        let elem_ty = args
            .first()
            .ok_or_else(|| syn::Error::new(ty.span(), "Vec requires an element type"))?;
        let elem_codec = codec_tokens_for_type_with_elem_meta(elem_ty, None)?;
        let base = quote! { fory_core::VecCodec<#elem_codec> };
        return Ok(wrap_nullable_if_needed(base, elem_meta, ty));
    }

    if ty_name == "HashMap" {
        let args = type_generics(ty);
        if args.len() != 2 {
            return Err(syn::Error::new(
                ty.span(),
                "HashMap requires key and value types",
            ));
        }
        let key_codec = codec_tokens_for_type_with_elem_meta(&args[0], None)?;
        let value_codec = codec_tokens_for_type_with_elem_meta(&args[1], None)?;
        let base = quote! { fory_core::HashMapCodec<#key_codec, #value_codec> };
        return Ok(wrap_nullable_if_needed(base, elem_meta, ty));
    }

    if ty_name == "BTreeMap" {
        let args = type_generics(ty);
        if args.len() != 2 {
            return Err(syn::Error::new(
                ty.span(),
                "BTreeMap requires key and value types",
            ));
        }
        let key_codec = codec_tokens_for_type_with_elem_meta(&args[0], None)?;
        let value_codec = codec_tokens_for_type_with_elem_meta(&args[1], None)?;
        let base = quote! { fory_core::BTreeMapCodec<#key_codec, #value_codec> };
        return Ok(wrap_nullable_if_needed(base, elem_meta, ty));
    }

    let base = quote! { fory_core::SerializerCodec<#ty> };
    Ok(wrap_nullable_if_needed(base, elem_meta, ty))
}

fn codec_tokens_for_meta_type(ty: &Type, meta: &ForyFieldMeta) -> syn::Result<TokenStream> {
    if let Some(inner) = option_inner_type(ty) {
        let inner_codec = if meta.encoding.is_some() || meta.list.is_some() || meta.map.is_some() {
            codec_tokens_for_meta_type(&inner, meta)?
        } else {
            codec_tokens_for_type_with_elem_meta(&inner, None)?
        };
        return Ok(quote! { fory_core::OptionCodec<#inner_codec> });
    }

    let ty_name = if let Type::Path(type_path) = ty {
        type_path
            .path
            .segments
            .last()
            .map(|s| s.ident.to_string())
            .unwrap_or_default()
    } else {
        String::new()
    };

    if ty_name == "Vec" {
        let args = type_generics(ty);
        let elem_ty = args
            .first()
            .ok_or_else(|| syn::Error::new(ty.span(), "Vec requires an element type"))?;
        let elem_codec =
            codec_tokens_for_type_with_elem_meta(elem_ty, meta.list.as_ref().map(|m| &m.element))?;
        return Ok(quote! { fory_core::VecCodec<#elem_codec> });
    }

    if ty_name == "HashMap" {
        let args = type_generics(ty);
        if args.len() != 2 {
            return Err(syn::Error::new(
                ty.span(),
                "HashMap requires key and value types",
            ));
        }
        let map_meta = meta.map.as_ref();
        let key_codec = codec_tokens_for_type_with_elem_meta(&args[0], map_meta.map(|m| &m.key))?;
        let value_codec =
            codec_tokens_for_type_with_elem_meta(&args[1], map_meta.map(|m| &m.value))?;
        return Ok(quote! { fory_core::HashMapCodec<#key_codec, #value_codec> });
    }

    if ty_name == "BTreeMap" {
        let args = type_generics(ty);
        if args.len() != 2 {
            return Err(syn::Error::new(
                ty.span(),
                "BTreeMap requires key and value types",
            ));
        }
        let map_meta = meta.map.as_ref();
        let key_codec = codec_tokens_for_type_with_elem_meta(&args[0], map_meta.map(|m| &m.key))?;
        let value_codec =
            codec_tokens_for_type_with_elem_meta(&args[1], map_meta.map(|m| &m.value))?;
        return Ok(quote! { fory_core::BTreeMapCodec<#key_codec, #value_codec> });
    }

    if let Some(ts) = scalar_codec_from_hint(ty, meta.encoding)? {
        return Ok(ts);
    }

    Ok(quote! { fory_core::SerializerCodec<#ty> })
}

pub fn codec_tokens_for_field(field: &syn::Field) -> syn::Result<TokenStream> {
    let meta = parse_field_meta(field)?;
    codec_tokens_for_meta_type(&field.ty, &meta)
}

#[cfg(test)]
mod tests {
    use super::codec_tokens_for_field;
    use syn::{parse_quote, Field};

    fn normalize(tokens: proc_macro2::TokenStream) -> String {
        tokens.to_string().replace(' ', "")
    }

    #[test]
    fn list_element_encoding_flows_through_option_inner_type() {
        let field: Field = parse_quote! {
            #[fory(list(element(encoding = "fixed")))]
            value: Vec<Option<i32>>
        };

        let codec = codec_tokens_for_field(&field).unwrap();
        assert_eq!(
            normalize(codec),
            "fory_core::VecCodec<fory_core::OptionCodec<fory_core::FixedI32Codec>>"
        );
    }

    #[test]
    fn map_element_encoding_flows_through_option_inner_type() {
        let field: Field = parse_quote! {
            #[fory(map(key(encoding = "fixed"), value(encoding = "tagged")))]
            value: std::collections::HashMap<Option<i32>, Option<u64>>
        };

        let codec = codec_tokens_for_field(&field).unwrap();
        assert_eq!(
            normalize(codec),
            "fory_core::HashMapCodec<fory_core::OptionCodec<fory_core::FixedI32Codec>,fory_core::OptionCodec<fory_core::TaggedU64Codec>>"
        );
    }

    #[test]
    fn option_wrapped_collection_uses_top_level_nested_metadata() {
        let field: Field = parse_quote! {
            #[fory(list(element(encoding = "fixed")))]
            value: Option<Vec<i32>>
        };

        let codec = codec_tokens_for_field(&field).unwrap();
        assert_eq!(
            normalize(codec),
            "fory_core::OptionCodec<fory_core::VecCodec<fory_core::FixedI32Codec>>"
        );
    }
}
