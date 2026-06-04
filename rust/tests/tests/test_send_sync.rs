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

use fory_core::fory::Fory;
use fory_core::Serializer;
use fory_derive::{ForyEnum, ForyStruct, ForyUnion};
use std::{
    any::Any,
    collections::{HashMap, HashSet, LinkedList},
    fmt::Debug,
    rc::Rc,
    sync::Arc,
};

fn assert_arc_any_roundtrip<T>(fory: &Fory, value: T)
where
    T: 'static + Clone + Debug + PartialEq + Send + Sync,
{
    let wrapped: Arc<dyn Any + Send + Sync> = Arc::new(value.clone());
    let bytes = fory.serialize(&wrapped).unwrap();
    let decoded: Arc<dyn Any + Send + Sync> = fory.deserialize(&bytes).unwrap();
    assert_eq!(decoded.downcast_ref::<T>().unwrap(), &value);
}

fn assert_arc_any_unsupported<T>(fory: &Fory, value: T)
where
    T: 'static + Send + Sync,
{
    let wrapped: Arc<dyn Any + Send + Sync> = Arc::new(value);
    let bytes = fory.serialize(&wrapped).unwrap();
    let result: Result<Arc<dyn Any + Send + Sync>, _> = fory.deserialize(&bytes);
    let err = match result {
        Ok(_) => panic!("expected direct generic container payload to be unsupported"),
        Err(err) => err,
    };
    let message = err.to_string();
    assert!(
        message.contains("generic container types")
            || message.contains("cannot be represented as Arc<dyn Any + Send + Sync>"),
        "unexpected error: {err}"
    );
}

#[test]
fn test_builtin_send_sync_arc_any_reads() {
    let fory = Fory::builder().xlang(false).build();

    assert_arc_any_roundtrip(&fory, 42_i32);
    assert_arc_any_roundtrip(&fory, true);
    assert_arc_any_roundtrip(&fory, "thread-safe".to_string());
}

#[test]
fn test_derived_send_sync_arc_any_read() {
    #[derive(ForyStruct, Clone, Debug, PartialEq)]
    struct Value {
        name: String,
        count: i32,
    }

    let mut fory = Fory::builder().xlang(false).build();
    fory.register::<Value>(900).unwrap();

    assert_arc_any_roundtrip(
        &fory,
        Value {
            name: "derived".to_string(),
            count: 7,
        },
    );
}

#[test]
fn test_wrapped_container_send_sync_arc_any_read() {
    #[derive(ForyStruct, Clone, Debug, PartialEq)]
    struct IntList {
        values: Vec<i32>,
    }

    let mut fory = Fory::builder().xlang(false).build();
    fory.register::<IntList>(901).unwrap();

    assert_arc_any_roundtrip(
        &fory,
        IntList {
            values: vec![1, 2, 3],
        },
    );
}

#[test]
fn test_direct_generic_containers_not_send_sync_arc_any() {
    let fory = Fory::builder().xlang(false).build();

    assert_arc_any_unsupported(&fory, vec![1_i32, 2, 3]);
    assert_arc_any_unsupported(&fory, LinkedList::from([1_i32, 2, 3]));
    assert_arc_any_unsupported(&fory, HashSet::from([1_i32, 2, 3]));
    assert_arc_any_unsupported(
        &fory,
        HashMap::from([("one".to_string(), 1_i32), ("two".to_string(), 2)]),
    );
}

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
