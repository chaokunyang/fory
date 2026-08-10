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

use proc_macro2::TokenStream;
use quote::quote;
use std::sync::atomic::{AtomicU32, Ordering};
use syn::Field;

use super::field_codec::{build_bindings, FieldBinding};
use super::field_meta::parse_field_meta;
use super::util::{get_filtered_fields_iter, get_sort_fields_ts, get_sorted_field_names};
use crate::util::SourceField;

// Global type ID counter that auto-grows from 0 at macro processing time
static TYPE_ID_COUNTER: AtomicU32 = AtomicU32::new(0);

/// Allocates a new unique type ID at macro processing time
pub fn allocate_type_id() -> u32 {
    TYPE_ID_COUNTER.fetch_add(1, Ordering::SeqCst)
}

pub fn gen_actual_type_id() -> TokenStream {
    quote! {
        let _ = xlang;
        Ok(fory_core::serializer::struct_::actual_type_id(
            type_id,
            register_by_name,
            compatible,
        ))
    }
}

pub fn gen_actual_type_id_no_evolving() -> TokenStream {
    quote! {
        let _ = (type_id, compatible, xlang);
        Ok(if register_by_name {
            fory_core::type_id::TypeId::NAMED_STRUCT as u32
        } else {
            fory_core::type_id::TypeId::STRUCT as u32
        })
    }
}

pub fn gen_get_sorted_field_names(fields: &[&Field]) -> TokenStream {
    let static_field_names = get_sort_fields_ts(fields);
    quote! {
        #static_field_names
    }
}

pub fn gen_field_fields_info(source_fields: &[SourceField<'_>]) -> TokenStream {
    let bindings = match build_bindings(source_fields) {
        Ok(bindings) => bindings,
        Err(err) => return err.to_compile_error(),
    };
    let field_infos = bindings.iter().filter_map(|binding| match binding {
        FieldBinding::Codec(binding) => Some(binding.field_info()),
        FieldBinding::Skipped(_) => None,
    });

    let fields: Vec<&Field> = source_fields.iter().map(|sf| sf.field).collect();
    let static_field_names = get_sort_fields_ts(&fields);

    quote! {
        let _ = type_resolver;
        let mut field_infos: ::std::vec::Vec<fory_core::meta::FieldInfo> = ::std::vec![#(#field_infos),*];
        let sorted_field_names = #static_field_names;
        fory_core::meta::sort_fields(&mut field_infos, sorted_field_names)?;
        ::std::result::Result::Ok(field_infos)
    }
}

pub fn gen_type_meta_field_ids(source_fields: &[SourceField<'_>]) -> TokenStream {
    let fields: Vec<&Field> = source_fields.iter().map(|source| source.field).collect();
    let filtered_fields: Vec<&Field> = get_filtered_fields_iter(&fields).collect();
    let sorted_field_names = get_sorted_field_names(&filtered_fields);
    // Keep this parallel static slice in the exact protocol order used by fields_info. SourceField
    // is normally pre-sorted, but deriving the order from the same owner prevents a future caller
    // from binding a wide tag to a different FieldInfo after group/type sorting.
    let field_ids = sorted_field_names
        .iter()
        .filter_map(|field_name| {
            let source = source_fields
                .iter()
                .find(|source| &source.field_name == field_name)?;
            let meta = parse_field_meta(source.field).ok()?;
            Some(if meta.uses_tag_id() {
                let field_id = meta.effective_id();
                quote! { ::std::option::Option::Some(#field_id) }
            } else {
                quote! { ::std::option::Option::None }
            })
        })
        .collect::<Vec<_>>();

    quote! { &[#(#field_ids),*] }
}
