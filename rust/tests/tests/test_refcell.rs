use fory_core::fory::Fory;
use fory_core::serializer::weak::{ArcWeak, RcWeak};
use std::cell::RefCell;
use std::rc::Rc;
use std::sync::Arc;

#[test]
fn test_refcell_basic_serialization() {
    let fury = Fory::default();

    let cell = RefCell::new(42i32);

    let serialized = fury.serialize(&cell);
    let deserialized: RefCell<i32> = fury.deserialize(&serialized).unwrap();

    assert_eq!(*cell.borrow(), *deserialized.borrow());
}

#[test]
fn test_refcell_string_serialization() {
    let fury = Fory::default();

    let cell = RefCell::new(String::from("test"));

    let serialized = fury.serialize(&cell);
    let deserialized: RefCell<String> = fury.deserialize(&serialized).unwrap();

    assert_eq!(*cell.borrow(), *deserialized.borrow());
}

#[test]
fn test_refcell_vec_serialization() {
    let fury = Fory::default();

    let cell = RefCell::new(vec![1, 2, 3, 4, 5]);

    let serialized = fury.serialize(&cell);
    let deserialized: RefCell<Vec<i32>> = fury.deserialize(&serialized).unwrap();

    assert_eq!(*cell.borrow(), *deserialized.borrow());
}

#[test]
fn test_refcell_with_rc_weak() {
    let fury = Fory::default();

    let rc1 = Rc::new(42i32);
    let rc2 = rc1.clone();
    let weak = RcWeak::from(&rc1);
    let cell = RefCell::new(weak);

    let data = vec![rc1, rc2];
    let serialized_data = fury.serialize(&data);
    let serialized_cell = fury.serialize(&cell);

    let deserialized_data: Vec<Rc<i32>> = fury.deserialize(&serialized_data).unwrap();
    let _deserialized_cell: RefCell<RcWeak<i32>> = fury.deserialize(&serialized_cell).unwrap();

    assert_eq!(deserialized_data.len(), 2);
    assert_eq!(*deserialized_data[0], 42);
}

#[test]
fn test_refcell_with_arc_weak() {
    let fury = Fory::default();

    let arc1 = Arc::new(String::from("test"));
    let arc2 = arc1.clone();
    let weak = ArcWeak::from(&arc1);
    let cell = RefCell::new(weak);

    let data = vec![arc1, arc2];
    let serialized_data = fury.serialize(&data);
    let serialized_cell = fury.serialize(&cell);

    let deserialized_data: Vec<Arc<String>> = fury.deserialize(&serialized_data).unwrap();
    let _deserialized_cell: RefCell<ArcWeak<String>> = fury.deserialize(&serialized_cell).unwrap();

    assert_eq!(deserialized_data.len(), 2);
    assert_eq!(*deserialized_data[0], "test");
}

#[test]
fn test_refcell_with_dead_rc_weak() {
    let fury = Fory::default();

    let weak = {
        let rc = Rc::new(42i32);
        RcWeak::from(&rc)
    };

    let cell = RefCell::new(weak);

    let serialized = fury.serialize(&cell);
    let deserialized: RefCell<RcWeak<i32>> = fury.deserialize(&serialized).unwrap();

    assert!(deserialized.borrow().upgrade().is_none());
}

#[test]
fn test_vec_of_refcell() {
    let fury = Fory::default();

    let cells = vec![RefCell::new(10), RefCell::new(20), RefCell::new(30)];

    let serialized = fury.serialize(&cells);
    let deserialized: Vec<RefCell<i32>> = fury.deserialize(&serialized).unwrap();

    assert_eq!(deserialized.len(), 3);
    assert_eq!(*deserialized[0].borrow(), 10);
    assert_eq!(*deserialized[1].borrow(), 20);
    assert_eq!(*deserialized[2].borrow(), 30);
}

#[test]
fn test_refcell_mutability() {
    let fury = Fory::default();

    let cell = RefCell::new(100);
    *cell.borrow_mut() = 200;

    let serialized = fury.serialize(&cell);
    let deserialized: RefCell<i32> = fury.deserialize(&serialized).unwrap();

    assert_eq!(*deserialized.borrow(), 200);

    *deserialized.borrow_mut() = 300;
    assert_eq!(*deserialized.borrow(), 300);
}
