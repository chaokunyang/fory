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

use fory_core::meta::{FieldInfo, FieldType, MetaString, TypeMeta};
use fory_core::type_id::TypeId;
use fory_core::{compute_field_hash, compute_struct_hash};

#[test]
fn field_info_i16_source_api() {
    let field_id: i16 = 43;
    let field_type = FieldType::new(TypeId::BOOL as u32, true, vec![]);
    let constructed = FieldInfo::new_with_id(field_id, "value", field_type.clone());
    let _: i16 = constructed.field_id;

    let literal = FieldInfo {
        field_id,
        field_name: "value".to_string(),
        field_type,
    };
    let _: i16 = literal.field_id;
    let _ = compute_field_hash(17, field_id);
    let _ = compute_struct_hash([field_id]);
}

#[test]
fn test_meta_hash() {
    let meta = TypeMeta::new(
        TypeId::STRUCT as u32,
        1,
        MetaString::get_empty().clone(),
        MetaString::get_empty().clone(),
        false,
        vec![FieldInfo::new_with_id(
            43,
            "f1",
            FieldType::new(TypeId::BOOL as u32, true, vec![]),
        )],
    )
    .unwrap();
    assert_ne!(meta.get_hash(), 0);
}
