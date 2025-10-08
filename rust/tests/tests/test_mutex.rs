use fory_core::fory::Fory;
use std::sync::{Arc, Mutex};

#[test]
fn test_mutex_basic_serialization() {
    let fury = Fory::default();
    let m = Mutex::new(42i32);
    let serialized = fury.serialize(&m);
    let deserialized: Mutex<i32> = fury.deserialize(&serialized).unwrap();
    assert_eq!(deserialized.lock().unwrap().clone(), 42);
}

#[test]
fn test_arc_mutex_serialization() {
    let fury = Fory::default();
    let arc_mutex = Arc::new(Mutex::new(String::from("hello")));
    let serialized = fury.serialize(&arc_mutex);
    let deserialized: Arc<Mutex<String>> = fury.deserialize(&serialized).unwrap();
    assert_eq!(deserialized.lock().unwrap().as_str(), "hello");
}

#[test]
fn test_arc_mutex_sharing_preserved() {
    let fury = Fory::default();

    let data = Arc::new(Mutex::new(123i32));
    let list = vec![data.clone(), data.clone()];

    let serialized = fury.serialize(&list);
    let deserialized: Vec<Arc<Mutex<i32>>> = fury.deserialize(&serialized).unwrap();

    assert_eq!(deserialized.len(), 2);
    assert!(Arc::ptr_eq(&deserialized[0], &deserialized[1]));
}
