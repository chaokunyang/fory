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

use super::util::{
    classify_trait_object_field, create_wrapper_types_arc, create_wrapper_types_rc,
    get_type_id_by_type_ast, is_option, should_skip_type_info_for_field, skip_ref_flag,
    StructField,
};
use fory_core::types::TypeId;
use proc_macro2::TokenStream;
use quote::quote;
use syn::Field;

pub fn gen_reserved_space(fields: &[&Field]) -> TokenStream {
    let reserved_size_expr: Vec<_> = fields.iter().map(|field| {
        let ty = &field.ty;
        match classify_trait_object_field(ty) {
            StructField::BoxDyn => {
                quote! {
                    fory_core::types::SIZE_OF_REF_AND_TYPE
                }
            }
            StructField::RcDyn(trait_name) => {
                let types = create_wrapper_types_rc(&trait_name);
                let wrapper_ty = types.wrapper_ty;
                quote! {
                    <#wrapper_ty as fory_core::Serializer>::fory_reserved_space() + fory_core::types::SIZE_OF_REF_AND_TYPE
                }
            }
            StructField::ArcDyn(trait_name) => {
                let types = create_wrapper_types_arc(&trait_name);
                let wrapper_ty = types.wrapper_ty;
                quote! {
                    <#wrapper_ty as fory_core::Serializer>::fory_reserved_space() + fory_core::types::SIZE_OF_REF_AND_TYPE
                }
            }
            StructField::VecRc(trait_name) => {
                let types = create_wrapper_types_rc(&trait_name);
                let wrapper_ty = types.wrapper_ty;
                quote! {
                    <Vec<#wrapper_ty> as fory_core::Serializer>::fory_reserved_space() + fory_core::types::SIZE_OF_REF_AND_TYPE
                }
            }
            StructField::VecArc(trait_name) => {
                let types = create_wrapper_types_arc(&trait_name);
                let wrapper_ty = types.wrapper_ty;
                quote! {
                    <Vec<#wrapper_ty> as fory_core::Serializer>::fory_reserved_space() + fory_core::types::SIZE_OF_REF_AND_TYPE
                }
            }
            StructField::HashMapRc(key_ty, trait_name) => {
                let types = create_wrapper_types_rc(&trait_name);
                let wrapper_ty = types.wrapper_ty;
                quote! {
                    <std::collections::HashMap<#key_ty, #wrapper_ty> as fory_core::Serializer>::fory_reserved_space() + fory_core::types::SIZE_OF_REF_AND_TYPE
                }
            }
            StructField::HashMapArc(key_ty, trait_name) => {
                let types = create_wrapper_types_arc(&trait_name);
                let wrapper_ty = types.wrapper_ty;
                quote! {
                    <std::collections::HashMap<#key_ty, #wrapper_ty> as fory_core::Serializer>::fory_reserved_space() + fory_core::types::SIZE_OF_REF_AND_TYPE
                }
            }
            StructField::Forward => {
                quote! {
                    <#ty as fory_core::Serializer>::fory_reserved_space() + fory_core::types::SIZE_OF_REF_AND_TYPE
                }
            }
            _ => {
                quote! {
                    <#ty as fory_core::Serializer>::fory_reserved_space() + fory_core::types::SIZE_OF_REF_AND_TYPE
                }
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
        fory_core::serializer::struct_::write_type_info::<Self>(context)
    }
}

fn gen_write_field(field: &Field) -> TokenStream {
    let ty = &field.ty;
    let ident = &field.ident;
    match classify_trait_object_field(ty) {
        StructField::BoxDyn => {
            quote! {
                fory_core::Serializer::fory_write(&self.#ident, context, true, false)?;
            }
        }
        StructField::RcDyn(trait_name) => {
            let types = create_wrapper_types_rc(&trait_name);
            let wrapper_ty = types.wrapper_ty;
            let trait_ident = types.trait_ident;
            quote! {
                let wrapper = #wrapper_ty::from(self.#ident.clone() as std::rc::Rc<dyn #trait_ident>);
                fory_core::Serializer::fory_write(&wrapper, context, true, false)?;
            }
        }
        StructField::ArcDyn(trait_name) => {
            let types = create_wrapper_types_arc(&trait_name);
            let wrapper_ty = types.wrapper_ty;
            let trait_ident = types.trait_ident;
            quote! {
                let wrapper = #wrapper_ty::from(self.#ident.clone() as std::sync::Arc<dyn #trait_ident>);
                fory_core::Serializer::fory_write(&wrapper, context, true, false)?;
            }
        }
        StructField::VecRc(trait_name) => {
            let types = create_wrapper_types_rc(&trait_name);
            let wrapper_ty = types.wrapper_ty;
            let trait_ident = types.trait_ident;
            quote! {
                let wrapper_vec: Vec<#wrapper_ty> = self.#ident.iter()
                    .map(|item| #wrapper_ty::from(item.clone() as std::rc::Rc<dyn #trait_ident>))
                    .collect();
                fory_core::Serializer::fory_write(&wrapper_vec, context, false, true)?;
            }
        }
        StructField::VecArc(trait_name) => {
            let types = create_wrapper_types_arc(&trait_name);
            let wrapper_ty = types.wrapper_ty;
            let trait_ident = types.trait_ident;
            quote! {
                let wrapper_vec: Vec<#wrapper_ty> = self.#ident.iter()
                    .map(|item| #wrapper_ty::from(item.clone() as std::sync::Arc<dyn #trait_ident>))
                    .collect();
                fory_core::Serializer::fory_write(&wrapper_vec, context, false, true)?;
            }
        }
        StructField::HashMapRc(key_ty, trait_name) => {
            let types = create_wrapper_types_rc(&trait_name);
            let wrapper_ty = types.wrapper_ty;
            let trait_ident = types.trait_ident;
            quote! {
                let wrapper_map: std::collections::HashMap<#key_ty, #wrapper_ty> = self.#ident.iter()
                    .map(|(k, v)| (k.clone(), #wrapper_ty::from(v.clone() as std::rc::Rc<dyn #trait_ident>)))
                    .collect();
                fory_core::Serializer::fory_write(&wrapper_map, context, false, true)?;
            }
        }
        StructField::HashMapArc(key_ty, trait_name) => {
            let types = create_wrapper_types_arc(&trait_name);
            let wrapper_ty = types.wrapper_ty;
            let trait_ident = types.trait_ident;
            quote! {
                let wrapper_map: std::collections::HashMap<#key_ty, #wrapper_ty> = self.#ident.iter()
                    .map(|(k, v)| (k.clone(), #wrapper_ty::from(v.clone() as std::sync::Arc<dyn #trait_ident>)))
                    .collect();
                fory_core::Serializer::fory_write(&wrapper_map, context, false, true)?;
            }
        }
        StructField::Forward => {
            quote! {
                fory_core::Serializer::fory_write(&self.#ident, context, true, false)?;
            }
        }
        _ => {
            let skip_ref_flag = skip_ref_flag(ty);
            let skip_type_info = should_skip_type_info_for_field(ty);
            let type_id = get_type_id_by_type_ast(ty);
            let is_option = is_option(&field.ty);
            if type_id == TypeId::LIST as u32 || type_id == TypeId::SET as u32 {
                if is_option {
                    quote! {
                        if let Some(v) = &self.#ident {
                            context.writer.write_i8(fory_core::RefFlag::NotNullValue as i8);
                            fory_core::serializer::CollectionSerializer::fory_write_collection_field(v, context)?;
                        } else {
                            context.writer.write_i8(fory_core::RefFlag::Null as i8);
                        }
                    }
                } else {
                    quote! {
                        context.writer.write_i8(fory_core::RefFlag::NotNullValue as i8);
                        fory_core::serializer::CollectionSerializer::fory_write_collection_field(&self.#ident, context)?;
                    }
                }
            } else if type_id == TypeId::MAP as u32 {
                if is_option {
                    quote! {
                        if let Some(v) = &self.#ident {
                            context.writer.write_i8(fory_core::RefFlag::NotNullValue as i8);
                            fory_core::serializer::MapSerializer::fory_write_map_field(v, context)?;
                        } else {
                            context.writer.write_i8(fory_core::RefFlag::Null as i8);
                        }
                    }
                } else {
                    quote! {
                        context.writer.write_i8(fory_core::RefFlag::NotNullValue as i8);
                        fory_core::serializer::MapSerializer::fory_write_map_field(&self.#ident, context)?;
                    }
                }
            } else {
                // Known types (primitives, strings, collections) - skip type info at compile time
                // For custom types that we can't determine at compile time (like enums),
                // we need to check at runtime whether to skip type info
                if skip_type_info {
                    if skip_ref_flag {
                        quote! {
                            fory_core::Serializer::fory_write_data(&self.#ident, context)?;
                        }
                    } else {
                        quote! {
                            fory_core::Serializer::fory_write(&self.#ident, context, false, true)?;
                        }
                    }
                } else {
                    if skip_ref_flag {
                        quote! {
                            let is_enum = <#ty as fory_core::Serializer>::fory_static_type_id() == fory_core::types::TypeId::ENUM;
                            fory_core::Serializer::fory_write(&self.#ident, context, true, is_enum)?;
                        }
                    } else {
                        quote! {
                            let is_enum = <#ty as fory_core::Serializer>::fory_static_type_id() == fory_core::types::TypeId::ENUM;
                            fory_core::Serializer::fory_write(&self.#ident, context, false, is_enum)?;
                        }
                    }
                }
            }
        }
    }
}

pub fn gen_write_data(fields: &[&Field]) -> TokenStream {
    let write_fields_ts: Vec<_> = fields.iter().map(|field| gen_write_field(field)).collect();
    quote! {
        #(#write_fields_ts)*
        Ok(())
    }
}

pub fn gen_write() -> TokenStream {
    quote! {
        fory_core::serializer::struct_::write::<Self>(self, context, write_ref_info, write_type_info)
    }
}
