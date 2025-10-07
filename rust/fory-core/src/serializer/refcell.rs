use crate::error::Error;
use crate::fory::Fory;
use crate::resolver::context::{ReadContext, WriteContext};
use crate::serializer::{ForyDefault, Serializer};
use std::cell::RefCell;

impl<T: Serializer + ForyDefault> Serializer for RefCell<T> {
    fn fory_read_data(context: &mut ReadContext, is_field: bool) -> Result<Self, Error> {
        Ok(RefCell::new(T::fory_read_data(context, is_field)?))
    }

    fn fory_read_type_info(context: &mut ReadContext, is_field: bool) {
        T::fory_read_type_info(context, is_field);
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
