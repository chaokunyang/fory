use fory_derive::Fory;
use std::rc::Rc;

#[derive(Fory)]
struct SimpleTest {
    field: Rc<dyn std::fmt::Display>,
}