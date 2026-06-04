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

#![allow(dead_code)]

use fory_core::Serializer;
use fory_derive::{ForyEnum, ForyStruct, ForyUnion};
use std::{any::Any, rc::Rc, sync::Arc};

#[test]
fn test_auto_send_sync_struct() {
    #[derive(ForyStruct)]
    struct Value {
        name: String,
    }

    assert!(Value::fory_is_send_sync_type());
}

#[test]
fn test_any_carrier_flags() {
    assert!(!<Rc<dyn Any> as Serializer>::fory_is_send_sync_type());
    assert!(<Arc<dyn Any + Send + Sync> as Serializer>::fory_is_send_sync_type());
}

#[test]
fn test_nested_custom_default() {
    #[derive(ForyStruct)]
    struct Leaf {
        name: String,
    }

    #[derive(ForyStruct)]
    struct Value {
        leaf: Leaf,
    }

    assert!(Value::fory_is_send_sync_type());
}

#[test]
fn test_send_sync_opt_out_struct() {
    #[derive(ForyStruct)]
    #[fory(send_sync = false)]
    struct Value {
        name: String,
    }

    assert!(!Value::fory_is_send_sync_type());
}

#[test]
fn test_nested_custom_opt_out() {
    #[derive(ForyStruct)]
    struct Leaf {
        name: Rc<String>,
    }

    #[derive(ForyStruct)]
    #[fory(send_sync = false)]
    struct Value {
        leaf: Leaf,
    }

    assert!(!Value::fory_is_send_sync_type());
}

#[test]
fn test_send_sync_force_struct() {
    #[derive(ForyStruct)]
    #[fory(send_sync)]
    struct Value {
        name: String,
    }

    assert!(Value::fory_is_send_sync_type());
}

#[test]
fn test_known_non_send_sync_struct() {
    #[derive(ForyStruct)]
    struct Value {
        name: Rc<String>,
    }

    assert!(!Value::fory_is_send_sync_type());
}

#[test]
fn test_send_sync_opt_out_union() {
    #[derive(ForyUnion)]
    #[fory(send_sync = false)]
    enum Event {
        #[fory(unknown)]
        Unknown(fory_core::UnknownCase),
        #[fory(id = 0, default)]
        Value(String),
    }

    assert!(!Event::fory_is_send_sync_type());
}

#[test]
fn test_send_sync_union() {
    #[derive(ForyUnion)]
    enum Event {
        #[fory(unknown)]
        Unknown(fory_core::UnknownCase),
        #[fory(id = 0, default)]
        Value(String),
    }

    assert!(Event::fory_is_send_sync_type());
}

#[test]
fn test_send_sync_enum() {
    #[derive(ForyEnum, Default)]
    #[fory(send_sync = true)]
    enum Status {
        #[default]
        Active,
        Inactive,
    }

    assert!(Status::fory_is_send_sync_type());
}
