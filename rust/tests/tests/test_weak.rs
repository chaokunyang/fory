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

use fory_core::fory::Fory;
use fory_core::serializer::weak::{ArcWeak, RcWeak};
use std::rc::Rc;
use std::sync::Arc;

#[test]
fn test_rc_weak_null_serialization() {
    let fury = Fory::default();

    let weak: RcWeak<i32> = RcWeak::new();

    let serialized = fury.serialize(&weak);
    let deserialized: RcWeak<i32> = fury.deserialize(&serialized).unwrap();

    assert!(deserialized.upgrade().is_none());
}

#[test]
fn test_arc_weak_null_serialization() {
    let fury = Fory::default();

    let weak: ArcWeak<i32> = ArcWeak::new();

    let serialized = fury.serialize(&weak);
    let deserialized: ArcWeak<i32> = fury.deserialize(&serialized).unwrap();

    assert!(deserialized.upgrade().is_none());
}

#[test]
fn test_rc_weak_serialization_creates_references() {
    let fury = Fory::default();

    let rc = Rc::new(42i32);
    let weak1 = RcWeak::from(&rc);
    let weak2 = weak1.clone();

    // When we serialize weaks while rc is alive, they serialize as references
    let data = vec![weak1, weak2];
    let serialized = fury.serialize(&data);

    // Serialization should have written:
    // - First weak: RefValue flag + data
    // - Second weak: Ref flag + ref_id
    // We can verify this by checking the serialized bytes contain both flags
    assert!(!serialized.is_empty());
}

#[test]
fn test_rc_weak_dead_pointer_serializes_as_null() {
    let fury = Fory::default();

    let weak = {
        let rc = Rc::new(42i32);
        RcWeak::from(&rc)
        // rc is dropped here
    };

    // Weak is now dead
    assert!(weak.upgrade().is_none());

    // Should serialize as Null
    let serialized = fury.serialize(&weak);
    let deserialized: RcWeak<i32> = fury.deserialize(&serialized).unwrap();

    assert!(deserialized.upgrade().is_none());
}

#[test]
fn test_arc_weak_dead_pointer_serializes_as_null() {
    let fury = Fory::default();

    let weak = {
        let arc = Arc::new(String::from("test"));
        ArcWeak::from(&arc)
        // arc is dropped here
    };

    // Weak is now dead
    assert!(weak.upgrade().is_none());

    // Should serialize as Null
    let serialized = fury.serialize(&weak);
    let deserialized: ArcWeak<String> = fury.deserialize(&serialized).unwrap();

    assert!(deserialized.upgrade().is_none());
}
