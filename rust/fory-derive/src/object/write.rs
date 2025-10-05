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

use crate::util::{
    detect_collection_with_trait_object, is_arc_dyn_trait, is_box_dyn_trait, is_rc_dyn_trait,
    CollectionTraitInfo,
};
use proc_macro2::TokenStream;
use quote::quote;
use syn::{Field, GenericArgument, PathArguments, Type};

/// Check if a type contains trait objects anywhere in its structure
fn contains_trait_object(ty: &Type) -> bool {
    match ty {
        Type::TraitObject(_) => true,
        Type::Path(type_path) => {
            // Check for Box<dyn Trait>, Rc<dyn Trait>, Arc<dyn Trait>
            if is_box_dyn_trait(ty).is_some()
                || is_rc_dyn_trait(ty).is_some()
                || is_arc_dyn_trait(ty).is_some()
            {
                return true;
            }

            // Check generics recursively
            if let Some(seg) = type_path.path.segments.last() {
                if let PathArguments::AngleBracketed(args) = &seg.arguments {
                    return args.args.iter().any(|arg| {
                        if let GenericArgument::Type(inner_ty) = arg {
                            contains_trait_object(inner_ty)
                        } else {
                            false
                        }
                    });
                }
            }
            false
        }
        _ => false,
    }
}

pub fn gen_reserved_space(fields: &[&Field]) -> TokenStream {
    let reserved_size_expr: Vec<_> = fields.iter().map(|field| {
        let ty = &field.ty;
        if is_box_dyn_trait(ty).is_some() {
            quote! {
                fory_core::types::SIZE_OF_REF_AND_TYPE
            }
        } else if let Some((_, trait_name)) = is_rc_dyn_trait(ty) {
            let wrapper_ty = quote::format_ident!("{}Rc", trait_name);
            quote! {
                <#wrapper_ty as fory_core::serializer::Serializer>::fory_reserved_space() + fory_core::types::SIZE_OF_REF_AND_TYPE
            }
        } else if let Some((_, trait_name)) = is_arc_dyn_trait(ty) {
            let wrapper_ty = quote::format_ident!("{}Arc", trait_name);
            quote! {
                <#wrapper_ty as fory_core::serializer::Serializer>::fory_reserved_space() + fory_core::types::SIZE_OF_REF_AND_TYPE
            }
        } else if let Some(collection_info) = detect_collection_with_trait_object(ty) {
            match collection_info {
                CollectionTraitInfo::VecRc(trait_name) => {
                    let wrapper_ty = quote::format_ident!("{}Rc", trait_name);
                    quote! {
                        <Vec<#wrapper_ty> as fory_core::serializer::Serializer>::fory_reserved_space() + fory_core::types::SIZE_OF_REF_AND_TYPE
                    }
                }
                CollectionTraitInfo::VecArc(trait_name) => {
                    let wrapper_ty = quote::format_ident!("{}Arc", trait_name);
                    quote! {
                        <Vec<#wrapper_ty> as fory_core::serializer::Serializer>::fory_reserved_space() + fory_core::types::SIZE_OF_REF_AND_TYPE
                    }
                }
                CollectionTraitInfo::HashMapRc(key_ty, trait_name) => {
                    let wrapper_ty = quote::format_ident!("{}Rc", trait_name);
                    quote! {
                        <std::collections::HashMap<#key_ty, #wrapper_ty> as fory_core::serializer::Serializer>::fory_reserved_space() + fory_core::types::SIZE_OF_REF_AND_TYPE
                    }
                }
                CollectionTraitInfo::HashMapArc(key_ty, trait_name) => {
                    let wrapper_ty = quote::format_ident!("{}Arc", trait_name);
                    quote! {
                        <std::collections::HashMap<#key_ty, #wrapper_ty> as fory_core::serializer::Serializer>::fory_reserved_space() + fory_core::types::SIZE_OF_REF_AND_TYPE
                    }
                }
            }
        } else if contains_trait_object(ty) {
            // For complex types containing trait objects (like HashMap<String, Box<dyn Animal>>),
            // just use the standard serialization path without trying to parse the type structure
            quote! {
                <#ty as fory_core::serializer::Serializer>::fory_reserved_space() + fory_core::types::SIZE_OF_REF_AND_TYPE
            }
        } else {
            quote! {
                <#ty as fory_core::serializer::Serializer>::fory_reserved_space() + fory_core::types::SIZE_OF_REF_AND_TYPE
            }
        }
    }).collect();
    if reserved_size_expr.is_empty() {
        quote! { 0 }
    } else {
        quote! { #(#reserved_size_expr)+* }
    }
}

pub fn gen_write_type_info() -> TokenStream {
    quote! {
        fory_core::serializer::struct_::write_type_info::<Self>(context, is_field)
    }
}

pub fn gen_write_data(fields: &[&Field]) -> TokenStream {
    let sorted_serialize = if fields.is_empty() {
        quote! {}
    } else {
        let match_ts = fields.iter().map(|field| {
            let ty = &field.ty;
            let ident = &field.ident;
            let name_str = ident.as_ref().unwrap().to_string();

            if is_box_dyn_trait(ty).is_some() {
                quote! {
                    #name_str => {
                        let any_ref = self.#ident.as_any();
                        let concrete_type_id = any_ref.type_id();
                        let fory_type_id = context.get_fory()
                            .get_type_resolver()
                            .get_fory_type_id(concrete_type_id)
                            .expect("Type not registered for trait object field");

                        context.writer.write_i8(fory_core::types::RefFlag::NotNullValue as i8);
                        context.writer.write_varuint32(fory_type_id);

                        let harness = context.get_fory()
                            .get_type_resolver()
                            .get_harness(fory_type_id)
                            .expect("Harness not found for trait object field");

                        let serializer_fn = harness.get_serializer();
                        serializer_fn(any_ref, context, true);
                    }
                }
            } else if let Some((_, trait_name)) = is_rc_dyn_trait(ty) {
                let wrapper_ty = quote::format_ident!("{}Rc", trait_name);
                let trait_ident = quote::format_ident!("{}", trait_name);
                quote! {
                    #name_str => {
                        let wrapper = #wrapper_ty::from(self.#ident.clone() as std::rc::Rc<dyn #trait_ident>);
                        fory_core::serializer::Serializer::fory_write(&wrapper, context, true);
                    }
                }
            } else if let Some((_, trait_name)) = is_arc_dyn_trait(ty) {
                let wrapper_ty = quote::format_ident!("{}Arc", trait_name);
                let trait_ident = quote::format_ident!("{}", trait_name);
                quote! {
                    #name_str => {
                        let wrapper = #wrapper_ty::from(self.#ident.clone() as std::sync::Arc<dyn #trait_ident>);
                        fory_core::serializer::Serializer::fory_write(&wrapper, context, true);
                    }
                }
            } else if let Some(collection_info) = detect_collection_with_trait_object(ty) {
                match collection_info {
                    CollectionTraitInfo::VecRc(trait_name) => {
                        let wrapper_ty = quote::format_ident!("{}Rc", trait_name);
                        let trait_ident = quote::format_ident!("{}", trait_name);
                        quote! {
                            #name_str => {
                                let wrapper_vec: Vec<#wrapper_ty> = self.#ident.iter()
                                    .map(|item| #wrapper_ty::from(item.clone() as std::rc::Rc<dyn #trait_ident>))
                                    .collect();
                                fory_core::serializer::Serializer::fory_write(&wrapper_vec, context, true);
                            }
                        }
                    }
                    CollectionTraitInfo::VecArc(trait_name) => {
                        let wrapper_ty = quote::format_ident!("{}Arc", trait_name);
                        let trait_ident = quote::format_ident!("{}", trait_name);
                        quote! {
                            #name_str => {
                                let wrapper_vec: Vec<#wrapper_ty> = self.#ident.iter()
                                    .map(|item| #wrapper_ty::from(item.clone() as std::sync::Arc<dyn #trait_ident>))
                                    .collect();
                                fory_core::serializer::Serializer::fory_write(&wrapper_vec, context, true);
                            }
                        }
                    }
                    CollectionTraitInfo::HashMapRc(key_ty, trait_name) => {
                        let wrapper_ty = quote::format_ident!("{}Rc", trait_name);
                        let trait_ident = quote::format_ident!("{}", trait_name);
                        quote! {
                            #name_str => {
                                let wrapper_map: std::collections::HashMap<#key_ty, #wrapper_ty> = self.#ident.iter()
                                    .map(|(k, v)| (k.clone(), #wrapper_ty::from(v.clone() as std::rc::Rc<dyn #trait_ident>)))
                                    .collect();
                                fory_core::serializer::Serializer::fory_write(&wrapper_map, context, true);
                            }
                        }
                    }
                    CollectionTraitInfo::HashMapArc(key_ty, trait_name) => {
                        let wrapper_ty = quote::format_ident!("{}Arc", trait_name);
                        let trait_ident = quote::format_ident!("{}", trait_name);
                        quote! {
                            #name_str => {
                                let wrapper_map: std::collections::HashMap<#key_ty, #wrapper_ty> = self.#ident.iter()
                                    .map(|(k, v)| (k.clone(), #wrapper_ty::from(v.clone() as std::sync::Arc<dyn #trait_ident>)))
                                    .collect();
                                fory_core::serializer::Serializer::fory_write(&wrapper_map, context, true);
                            }
                        }
                    }
                }
            } else {
                quote! {
                    #name_str => {
                        let skip_ref_flag = fory_core::serializer::get_skip_ref_flag::<#ty>(context.get_fory());
                        fory_core::serializer::write_ref_info_data::<#ty>(&self.#ident, context, true, skip_ref_flag, false);
                    }
                }
            }
        });
        quote! {
            let sorted_field_names = <Self as fory_core::serializer::StructSerializer>::fory_get_sorted_field_names(context.get_fory());
            for field_name in sorted_field_names {
                match field_name.as_str() {
                    #(#match_ts),*
                    , _ => {unreachable!()}
                }
            }
        }
    };
    quote! {
        #sorted_serialize
    }
}

pub fn gen_write() -> TokenStream {
    quote! {
        fory_core::serializer::struct_::write::<Self>(self, context, is_field)
    }
}
