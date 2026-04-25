use proc_macro2::TokenStream;
use quote::quote;
use syn::{spanned::Spanned, GenericArgument, PathArguments, Type};

use super::field_meta::parse_field_meta;

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

fn scalar_codec_tokens(
    ty: &Type,
    meta: &super::field_meta::ForyFieldMeta,
) -> syn::Result<Option<TokenStream>> {
    let ty_str = quote::ToTokens::to_token_stream(ty)
        .to_string()
        .replace(' ', "");
    let type_id = match meta.type_id {
        Some(v) => v as u32,
        None => return Ok(None),
    };
    let ts = match (ty_str.as_str(), type_id) {
        ("i32", x) if x == fory_core::TypeId::INT32 as u32 => quote! { fory_core::FixedI32Codec },
        ("i32", x) if x == fory_core::TypeId::VARINT32 as u32 => quote! { fory_core::VarI32Codec },
        ("u32", x)
            if x == fory_core::TypeId::INT32 as u32 || x == fory_core::TypeId::UINT32 as u32 =>
        {
            quote! { fory_core::FixedU32Codec }
        }
        ("u32", x)
            if x == fory_core::TypeId::VARINT32 as u32
                || x == fory_core::TypeId::VAR_UINT32 as u32 =>
        {
            quote! { fory_core::VarU32Codec }
        }
        ("i64", x)
            if x == fory_core::TypeId::INT64 as u32 || x == fory_core::TypeId::INT32 as u32 =>
        {
            quote! { fory_core::FixedI64Codec }
        }
        ("i64", x)
            if x == fory_core::TypeId::VARINT64 as u32
                || x == fory_core::TypeId::VARINT32 as u32 =>
        {
            quote! { fory_core::VarI64Codec }
        }
        ("i64", x)
            if x == fory_core::TypeId::TAGGED_INT64 as u32
                || x == fory_core::TypeId::TAGGED_UINT64 as u32 =>
        {
            quote! { fory_core::TaggedI64Codec }
        }
        ("u64", x)
            if x == fory_core::TypeId::INT32 as u32 || x == fory_core::TypeId::UINT64 as u32 =>
        {
            quote! { fory_core::FixedU64Codec }
        }
        ("u64", x)
            if x == fory_core::TypeId::VARINT32 as u32
                || x == fory_core::TypeId::VAR_UINT64 as u32 =>
        {
            quote! { fory_core::VarU64Codec }
        }
        ("u64", x) if x == fory_core::TypeId::TAGGED_UINT64 as u32 => {
            quote! { fory_core::TaggedU64Codec }
        }
        _ => return Ok(None),
    };
    Ok(Some(ts))
}

pub fn codec_tokens_for_field_type(ty: &Type) -> syn::Result<TokenStream> {
    let scalar_default = quote! { fory_core::SerializerCodec<#ty> };
    let field: syn::Field = syn::parse_quote! { __tmp: #ty };
    codec_tokens_for_field(&field).or(Ok(scalar_default))
}

pub fn codec_tokens_for_field(field: &syn::Field) -> syn::Result<TokenStream> {
    let ty = &field.ty;
    let meta = parse_field_meta(field)?;

    if let Some(inner) = option_inner_type(ty) {
        let inner_field: syn::Field = syn::parse_quote! { __inner: #inner };
        let inner_codec = codec_tokens_for_field(&inner_field)?;
        return Ok(quote! { fory_core::OptionCodec<#inner_codec> });
    }

    if let Some(ts) = scalar_codec_tokens(ty, &meta)? {
        return Ok(ts);
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
        let elem_field: syn::Field = syn::parse_quote! { __elem: #elem_ty };
        let elem_codec = codec_tokens_for_field(&elem_field)?;
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
        let key_ty = args[0].clone();
        let val_ty = args[1].clone();
        let key_field: syn::Field = syn::parse_quote! { __key: #key_ty };
        let val_field: syn::Field = syn::parse_quote! { __value: #val_ty };
        let key_codec = codec_tokens_for_field(&key_field)?;
        let val_codec = codec_tokens_for_field(&val_field)?;
        return Ok(quote! { fory_core::HashMapCodec<#key_codec, #val_codec> });
    }

    if ty_name == "BTreeMap" {
        let args = type_generics(ty);
        if args.len() != 2 {
            return Err(syn::Error::new(
                ty.span(),
                "BTreeMap requires key and value types",
            ));
        }
        let key_ty = args[0].clone();
        let val_ty = args[1].clone();
        let key_field: syn::Field = syn::parse_quote! { __key: #key_ty };
        let val_field: syn::Field = syn::parse_quote! { __value: #val_ty };
        let key_codec = codec_tokens_for_field(&key_field)?;
        let val_codec = codec_tokens_for_field(&val_field)?;
        return Ok(quote! { fory_core::BTreeMapCodec<#key_codec, #val_codec> });
    }

    Ok(quote! { fory_core::SerializerCodec<#ty> })
}
