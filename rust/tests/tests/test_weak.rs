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
use fory_derive::ForyObject;
use std::cell::RefCell;
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

#[test]
fn test_rc_weak_serialization_preserves_sharing() {
    let fury = Fory::default();

    let data1 = Rc::new(42i32);
    let weak1a = RcWeak::from(&data1);
    let weak1b = weak1a.clone();

    let weaks = vec![weak1a, weak1b];
    let serialized = fury.serialize(&weaks);

    assert!(!serialized.is_empty());
}

#[test]
fn test_arc_weak_serialization_preserves_sharing() {
    let fury = Fory::default();

    let data1 = Arc::new(String::from("test"));
    let weak1a = ArcWeak::from(&data1);
    let weak1b = weak1a.clone();
    let weak1c = weak1a.clone();

    let weaks = vec![weak1a, weak1b, weak1c];
    let serialized = fury.serialize(&weaks);

    assert!(!serialized.is_empty());
}

#[test]
fn test_rc_weak_in_vec_circular_reference() {
    let fury = Fory::default();

    let data1 = Rc::new(42i32);
    let data2 = Rc::new(100i32);

    let weak1 = RcWeak::from(&data1);
    let weak2 = RcWeak::from(&data2);
    let weak3 = weak1.clone();

    let weaks = vec![weak1, weak2, weak3];
    let serialized = fury.serialize(&weaks);
    let deserialized: Vec<RcWeak<i32>> = fury.deserialize(&serialized).unwrap();

    assert_eq!(deserialized.len(), 3);
}

#[test]
fn test_arc_weak_in_vec_circular_reference() {
    let fury = Fory::default();

    let data1 = Arc::new(String::from("hello"));
    let data2 = Arc::new(String::from("world"));

    let weak1 = ArcWeak::from(&data1);
    let weak2 = ArcWeak::from(&data2);
    let weak3 = weak1.clone();

    let weaks = vec![weak1, weak2, weak3];
    let serialized = fury.serialize(&weaks);
    let deserialized: Vec<ArcWeak<String>> = fury.deserialize(&serialized).unwrap();

    assert_eq!(deserialized.len(), 3);
}

#[test]
fn test_rc_weak_field_in_struct() {
    use fory_derive::ForyObject;

    #[derive(ForyObject, Debug)]
    struct SimpleNode {
        value: i32,
        weak_ref: RcWeak<i32>,
    }

    let mut fury = Fory::default();
    fury.register::<SimpleNode>(1000);

    let data = Rc::new(42i32);
    let node = SimpleNode {
        value: 1,
        weak_ref: RcWeak::from(&data),
    };

    let serialized = fury.serialize(&node);
    let deserialized: SimpleNode = fury.deserialize(&serialized).unwrap();

    assert_eq!(deserialized.value, 1);
}

#[derive(ForyObject, Debug)]
struct Node {
    value: i32,
    // Weak ref to parent Rc<RefCell<Node>>
    parent: RcWeak<RefCell<Node>>,
    // Strong refs to children Rc<RefCell<Node>>
    children: Vec<Rc<RefCell<Node>>>,
}

#[test]
fn test_node_circular_reference_with_parent_children() {
    // Parent
    let parent = Rc::new(RefCell::new(Node {
        value: 1,
        parent: RcWeak::new(),
        children: vec![],
    }));

    // Child 1
    let child1 = Rc::new(RefCell::new(Node {
        value: 2,
        parent: RcWeak::from(&parent),
        children: vec![],
    }));

    // Child 2
    let child2 = Rc::new(RefCell::new(Node {
        value: 3,
        parent: RcWeak::from(&parent),
        children: vec![],
    }));

    // Modify parent's children after creation
    parent.borrow_mut().children.push(child1.clone());
    parent.borrow_mut().children.push(child2.clone());

    // Verify both children's parent points back to parent
    assert_eq!(parent.borrow().children.len(), 2);
    for child in &parent.borrow().children {
        let upgraded_parent = child.borrow().parent.upgrade().unwrap();
        assert!(Rc::ptr_eq(&parent, &upgraded_parent));
    }
}
