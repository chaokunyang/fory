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

use chrono::NaiveDate;
use fory_core::{Fory, StructSerializer, TypeId, TypeResolver};
use fory_derive::ForyObject;
use std::any::Any;

#[derive(ForyObject, Debug, PartialEq)]
struct XlangDurationRecord {
    count: i32,
    local_date: NaiveDate,
    signed_duration: chrono::Duration,
}

#[test]
fn chrono_duration_is_registered_for_xlang_polymorphic_read() {
    let fory = Fory::builder().xlang(true).build();
    let duration = chrono::Duration::nanoseconds(-1);
    let value: Box<dyn Any> = Box::new(duration);

    let bytes = fory.serialize(&value).unwrap();
    let decoded: Box<dyn Any> = fory.deserialize(&bytes).unwrap();

    assert_eq!(
        decoded.downcast_ref::<chrono::Duration>().unwrap(),
        &duration
    );
}

#[test]
fn derive_treats_chrono_duration_path_as_internal_duration() {
    let resolver = TypeResolver::default();
    let fields = <XlangDurationRecord as StructSerializer>::fory_fields_info(&resolver).unwrap();
    let field_names = fields
        .iter()
        .map(|field| field.field_name.as_str())
        .collect::<Vec<_>>();

    assert_eq!(field_names, vec!["count", "signed_duration", "local_date"]);
    assert_eq!(fields[1].field_type.type_id, TypeId::DURATION as u32);

    let mut fory = Fory::builder().compatible(true).xlang(true).build();
    fory.register::<XlangDurationRecord>(700).unwrap();
    let value = XlangDurationRecord {
        count: 7,
        local_date: NaiveDate::from_ymd_opt(2026, 4, 23).unwrap(),
        signed_duration: chrono::Duration::nanoseconds(-1),
    };

    let bytes = fory.serialize(&value).unwrap();
    let decoded: XlangDurationRecord = fory.deserialize(&bytes).unwrap();

    assert_eq!(decoded, value);
}
