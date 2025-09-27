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

//! Tests for RefResolver functionality

use fory_core::buffer::Writer;
use fory_core::resolver::ref_resolver::RefResolver;
use std::rc::Rc;
use std::sync::Arc;

#[test]
fn test_rc_ref_tracking() {
    let mut resolver = RefResolver::new();
    let mut writer = Writer::default();

    let rc1 = Rc::new(42i32);
    let rc2 = rc1.clone();

    // First write should register the reference
    assert!(!resolver.try_write_rc_ref(&mut writer, &rc1));

    // Second write should find existing reference
    assert!(resolver.try_write_rc_ref(&mut writer, &rc2));
}

#[test]
fn test_arc_ref_tracking() {
    let mut resolver = RefResolver::new();
    let mut writer = Writer::default();

    let arc1 = Arc::new(42i32);
    let arc2 = arc1.clone();

    // First write should register the reference
    assert!(!resolver.try_write_arc_ref(&mut writer, &arc1));

    // Second write should find existing reference
    assert!(resolver.try_write_arc_ref(&mut writer, &arc2));
}

#[test]
fn test_rc_storage_and_retrieval() {
    let mut resolver = RefResolver::new();
    let rc = Rc::new(String::from("test"));

    let ref_id = resolver.store_rc_ref(rc.clone());

    let retrieved = resolver.get_rc_ref::<String>(ref_id).unwrap();
    assert_eq!(*retrieved, "test");
    assert!(Rc::ptr_eq(&rc, &retrieved));
}

#[test]
fn test_arc_storage_and_retrieval() {
    let mut resolver = RefResolver::new();
    let arc = Arc::new(String::from("test"));

    let ref_id = resolver.store_arc_ref(arc.clone());

    let retrieved = resolver.get_arc_ref::<String>(ref_id).unwrap();
    assert_eq!(*retrieved, "test");
    assert!(Arc::ptr_eq(&arc, &retrieved));
}
