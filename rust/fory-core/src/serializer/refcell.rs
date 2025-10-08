//! Serialization support for `RefCell<T>`.
//!
//! This module implements `Serializer` and `ForyDefault` for `std::cell::RefCell<T>`.
//! It allows mutable reference containers to be part of serialized graphs, which is often
//! necessary when modeling object structures with interior mutability (e.g. parent/child links).
//!
//! Unlike `Rc` and `Arc`, `RefCell` does not do reference counting, so this wrapper relies
//! on the serialization of the contained `T` only.
//!
//! This is commonly used together with `Rc<RefCell<T>>` in graph structures.
//!
//! # Example
//! ```rust
//! use std::cell::RefCell;
//! use fory_core::serializer::recell::*;
//!
//! let cell = RefCell::new(42);
//! // Can be serialized by the Fory framework
//! ```
use crate::error::Error;
use crate::fory::Fory;
use crate::resolver::context::{ReadContext, WriteContext};
use crate::serializer::{ForyDefault, Serializer};
use std::cell::RefCell;

/// `Serializer` impl for `RefCell<T>`
///
/// Simply delegates to the serializer for `T`, allowing interior mutable
/// containers to be included in serialized graphs.
impl<T: Serializer + ForyDefault> Serializer for RefCell<T> {
    fn fory_read(context: &mut ReadContext, is_field: bool) -> Result<Self, Error>
    where
        Self: Sized + ForyDefault,
    {
        Ok(RefCell::new(T::fory_read(context, is_field)?))
    }
    fn fory_read_data(context: &mut ReadContext, is_field: bool) -> Result<Self, Error> {
        Ok(RefCell::new(T::fory_read_data(context, is_field)?))
    }

    fn fory_read_type_info(context: &mut ReadContext, is_field: bool) {
        T::fory_read_type_info(context, is_field);
    }

    fn fory_write(&self, context: &mut WriteContext, is_field: bool) {
        eprintln!("Writing RefCell");
        T::fory_write(&*self.borrow(), context, is_field);
    }

    fn fory_write_data(&self, context: &mut WriteContext, is_field: bool) {
        T::fory_write_data(&*self.borrow(), context, is_field)
    }

    fn fory_write_type_info(context: &mut WriteContext, is_field: bool) {
        T::fory_write_type_info(context, is_field);
    }

    fn fory_reserved_space() -> usize {
        T::fory_reserved_space()
    }

    fn fory_get_type_id(fory: &Fory) -> u32 {
        T::fory_get_type_id(fory)
    }

    fn fory_type_id_dyn(&self, fory: &Fory) -> u32 {
        (*self.borrow()).fory_type_id_dyn(fory)
    }

    fn as_any(&self) -> &dyn std::any::Any {
        self
    }
}

impl<T: ForyDefault> ForyDefault for RefCell<T> {
    fn fory_default() -> Self {
        RefCell::new(T::fory_default())
    }
}
