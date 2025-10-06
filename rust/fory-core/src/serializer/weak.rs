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

use crate::error::Error;
use crate::fory::Fory;
use crate::resolver::context::{ReadContext, WriteContext};
use crate::serializer::{ForyDefault, Serializer};
use crate::types::RefFlag;
use anyhow::anyhow;
use std::cell::UnsafeCell;
use std::rc::Rc;
use std::sync::Arc;

pub struct RcWeak<T: ?Sized> {
    inner: UnsafeCell<std::rc::Weak<T>>,
}

impl<T> RcWeak<T> {
    pub fn new() -> Self {
        RcWeak {
            inner: UnsafeCell::new(std::rc::Weak::new()),
        }
    }
}

impl<T: ?Sized> RcWeak<T> {
    pub fn upgrade(&self) -> Option<Rc<T>> {
        unsafe { (*self.inner.get()).upgrade() }
    }

    pub fn strong_count(&self) -> usize {
        unsafe { (*self.inner.get()).strong_count() }
    }

    pub fn weak_count(&self) -> usize {
        unsafe { (*self.inner.get()).weak_count() }
    }

    pub fn ptr_eq(&self, other: &Self) -> bool {
        unsafe { std::rc::Weak::ptr_eq(&*self.inner.get(), &*other.inner.get()) }
    }

    pub fn update(&self, weak: std::rc::Weak<T>) {
        unsafe {
            *self.inner.get() = weak;
        }
    }

    pub fn from_std(weak: std::rc::Weak<T>) -> Self {
        RcWeak {
            inner: UnsafeCell::new(weak),
        }
    }
}

impl<T> Default for RcWeak<T> {
    fn default() -> Self {
        Self::new()
    }
}

impl<T: ?Sized> Clone for RcWeak<T> {
    fn clone(&self) -> Self {
        unsafe {
            RcWeak {
                inner: UnsafeCell::new((*self.inner.get()).clone()),
            }
        }
    }
}

impl<T: ?Sized> From<&Rc<T>> for RcWeak<T> {
    fn from(rc: &Rc<T>) -> Self {
        RcWeak::from_std(Rc::downgrade(rc))
    }
}

unsafe impl<T: ?Sized> Send for RcWeak<T> where std::rc::Weak<T>: Send {}
unsafe impl<T: ?Sized> Sync for RcWeak<T> where std::rc::Weak<T>: Sync {}

pub struct ArcWeak<T: ?Sized> {
    inner: UnsafeCell<std::sync::Weak<T>>,
}

impl<T> ArcWeak<T> {
    pub fn new() -> Self {
        ArcWeak {
            inner: UnsafeCell::new(std::sync::Weak::new()),
        }
    }
}

impl<T: ?Sized> ArcWeak<T> {
    pub fn upgrade(&self) -> Option<Arc<T>> {
        unsafe { (*self.inner.get()).upgrade() }
    }

    pub fn strong_count(&self) -> usize {
        unsafe { (*self.inner.get()).strong_count() }
    }

    pub fn weak_count(&self) -> usize {
        unsafe { (*self.inner.get()).weak_count() }
    }

    pub fn ptr_eq(&self, other: &Self) -> bool {
        unsafe { std::sync::Weak::ptr_eq(&*self.inner.get(), &*other.inner.get()) }
    }

    pub fn update(&self, weak: std::sync::Weak<T>) {
        unsafe {
            *self.inner.get() = weak;
        }
    }

    pub fn from_std(weak: std::sync::Weak<T>) -> Self {
        ArcWeak {
            inner: UnsafeCell::new(weak),
        }
    }
}

impl<T> Default for ArcWeak<T> {
    fn default() -> Self {
        Self::new()
    }
}

impl<T: ?Sized> Clone for ArcWeak<T> {
    fn clone(&self) -> Self {
        unsafe {
            ArcWeak {
                inner: UnsafeCell::new((*self.inner.get()).clone()),
            }
        }
    }
}

impl<T: ?Sized> From<&Arc<T>> for ArcWeak<T> {
    fn from(arc: &Arc<T>) -> Self {
        ArcWeak::from_std(Arc::downgrade(arc))
    }
}

unsafe impl<T: ?Sized + Send + Sync> Send for ArcWeak<T> {}
unsafe impl<T: ?Sized + Send + Sync> Sync for ArcWeak<T> {}

impl<T: Serializer + ForyDefault + 'static> Serializer for RcWeak<T> {
    fn fory_read_data(context: &mut ReadContext, _is_field: bool) -> Result<Self, Error> {
        let ref_flag = context.ref_reader.read_ref_flag(&mut context.reader);

        match ref_flag {
            RefFlag::Null => Ok(RcWeak::new()),
            RefFlag::Ref => {
                let ref_id = context.ref_reader.read_ref_id(&mut context.reader);
                let weak = RcWeak::new();

                if let Some(rc) = context.ref_reader.get_rc_ref::<T>(ref_id) {
                    weak.update(Rc::downgrade(&rc));
                } else {
                    let weak_clone = weak.clone();
                    context.ref_reader.add_callback(Box::new(move |ref_reader| {
                        if let Some(rc) = ref_reader.get_rc_ref::<T>(ref_id) {
                            weak_clone.update(Rc::downgrade(&rc));
                        }
                    }));
                }

                Ok(weak)
            }
            _ => Err(anyhow!("Weak can only be Null or Ref, got {:?}", ref_flag).into()),
        }
    }

    fn fory_read_type_info(context: &mut ReadContext, is_field: bool) {
        T::fory_read_type_info(context, is_field);
    }

    fn fory_write_data(&self, context: &mut WriteContext, _is_field: bool) {
        if let Some(rc) = self.upgrade() {
            if context.ref_writer.try_write_rc_ref(context.writer, &rc) {
                return;
            }
        }
        context.writer.write_i8(RefFlag::Null as i8);
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
        if let Some(rc) = self.upgrade() {
            (*rc).fory_type_id_dyn(fory)
        } else {
            T::fory_get_type_id(fory)
        }
    }

    fn as_any(&self) -> &dyn std::any::Any {
        self
    }
}

impl<T: ForyDefault> ForyDefault for RcWeak<T> {
    fn fory_default() -> Self {
        RcWeak::new()
    }
}

impl<T: Serializer + ForyDefault + Send + Sync + 'static> Serializer for ArcWeak<T> {
    fn fory_read_data(context: &mut ReadContext, _is_field: bool) -> Result<Self, Error> {
        let ref_flag = context.ref_reader.read_ref_flag(&mut context.reader);

        match ref_flag {
            RefFlag::Null => Ok(ArcWeak::new()),
            RefFlag::Ref => {
                let ref_id = context.ref_reader.read_ref_id(&mut context.reader);
                let weak = ArcWeak::new();

                if let Some(arc) = context.ref_reader.get_arc_ref::<T>(ref_id) {
                    weak.update(Arc::downgrade(&arc));
                } else {
                    let weak_clone = weak.clone();
                    context.ref_reader.add_callback(Box::new(move |ref_reader| {
                        if let Some(arc) = ref_reader.get_arc_ref::<T>(ref_id) {
                            weak_clone.update(Arc::downgrade(&arc));
                        }
                    }));
                }

                Ok(weak)
            }
            _ => Err(anyhow!("Weak can only be Null or Ref, got {:?}", ref_flag).into()),
        }
    }

    fn fory_read_type_info(context: &mut ReadContext, is_field: bool) {
        T::fory_read_type_info(context, is_field);
    }

    fn fory_write_data(&self, context: &mut WriteContext, _is_field: bool) {
        if let Some(arc) = self.upgrade() {
            if context.ref_writer.try_write_arc_ref(context.writer, &arc) {
                return;
            }
        }
        context.writer.write_i8(RefFlag::Null as i8);
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
        if let Some(arc) = self.upgrade() {
            (*arc).fory_type_id_dyn(fory)
        } else {
            T::fory_get_type_id(fory)
        }
    }

    fn as_any(&self) -> &dyn std::any::Any {
        self
    }
}

impl<T: ForyDefault> ForyDefault for ArcWeak<T> {
    fn fory_default() -> Self {
        ArcWeak::new()
    }
}
