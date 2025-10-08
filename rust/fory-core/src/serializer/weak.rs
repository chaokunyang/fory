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

//! Weak pointer serialization support for `Rc` and `Arc`.
//!
//! This module provides `RcWeak<T>` and `ArcWeak<T>` wrapper types that can be serialized
//! and deserialized by the Fory serialization framework while preserving reference identity
//! and supporting circular references.
//!
//! In Rust, `std::rc::Weak` and `std::sync::Weak` cannot be directly upgraded if the strong
//! reference has been dropped. These wrappers make it possible to store weak references
//! inside serialized graphs:
//!
//! - If a weak pointer can be upgraded during serialization, it will be de/serialized
//!   as a reference to its corresponding strong object
//! - If it cannot be upgraded (target dropped), it will be serialized as `Null`
//!
//! During deserialization, unresolved weak pointers (because their strong reference appears
//! later in the stream) are handled via callbacks in `RefReader` to support forward references.
//!
//! This enables **circular graph structures** to be serialized and restored correctly without
//! duplicating nodes or losing shared reference relationships.
//!
//! # Example usage
//! ```rust
//! use fory_core::serializer::weak::RcWeak;
//! use std::rc::Rc;
//!
//! let rc = Rc::new(42);
//! let weak = RcWeak::from(&rc);
//! assert!(weak.upgrade().is_some());
//! ```
use crate::error::Error;
use crate::fory::Fory;
use crate::resolver::context::{ReadContext, WriteContext};
use crate::serializer::{ForyDefault, Serializer};
use crate::types::RefFlag;
use anyhow::anyhow;
use std::cell::UnsafeCell;
use std::rc::Rc;
use std::sync::Arc;

/// Wrapper for a `std::rc::Weak<T>` that is compatible with the Fory serialization framework.
///
/// This type uses interior mutability via `UnsafeCell` to allow updating the stored weak pointer
/// after creation (necessary during deserialization when resolving forward references).
///
/// It implements `Serializer` so it can be automatically handled in object graphs.
/// See module-level docs for details.
pub struct RcWeak<T: ?Sized> {
    inner: UnsafeCell<std::rc::Weak<T>>,
}

impl<T: ?Sized> std::fmt::Debug for RcWeak<T> {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("RcWeak")
            .field("strong_count", &self.strong_count())
            .field("weak_count", &self.weak_count())
            .finish()
    }
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

/// Wrapper for a `std::sync::Weak<T>` that is compatible with the Fory serialization framework.
///
/// Works like [`RcWeak`] but for thread-safe reference-counted graphs.
pub struct ArcWeak<T: ?Sized> {
    inner: UnsafeCell<std::sync::Weak<T>>,
}

impl<T: ?Sized> std::fmt::Debug for ArcWeak<T> {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("ArcWeak")
            .field("strong_count", &self.strong_count())
            .field("weak_count", &self.weak_count())
            .finish()
    }
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
    fn fory_is_shared_ref() -> bool {
        true
    }

    fn fory_write(&self, context: &mut WriteContext, _is_field: bool) {
        if let Some(rc) = self.upgrade() {
            // IMPORTANT: If the target Rc was serialized already, just write a ref
            if context.ref_writer.try_write_rc_ref(context.writer, &rc) {
                // Already seen, wrote Ref flag + id, we're done
                return;
            }
            // First time seeing this object, write RefValue and then its data
            T::fory_write_data(&*rc, context, _is_field);
        } else {
            context.writer.write_i8(RefFlag::Null as i8);
        }
    }

    fn fory_write_data(&self, context: &mut WriteContext, is_field: bool) {
        self.fory_write(context, is_field);
    }

    fn fory_write_type_info(context: &mut WriteContext, is_field: bool) {
        T::fory_write_type_info(context, is_field);
    }

    fn fory_read(context: &mut ReadContext, _is_field: bool) -> Result<Self, Error> {
        let ref_flag = context.ref_reader.read_ref_flag(&mut context.reader);

        match ref_flag {
            RefFlag::Null => Ok(RcWeak::new()),
            RefFlag::RefValue => {
                let data = T::fory_read_data(context, _is_field)?;
                let rc = Rc::new(data);
                let ref_id = context.ref_reader.store_rc_ref(rc);
                let rc = context.ref_reader.get_rc_ref::<T>(ref_id).unwrap();
                let weak = RcWeak::from(&rc);
                Ok(weak)
            }
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
            _ => Err(anyhow!("Weak can only be Null, RefValue or Ref, got {:?}", ref_flag).into()),
        }
    }

    fn fory_read_data(context: &mut ReadContext, is_field: bool) -> Result<Self, Error> {
        Self::fory_read(context, is_field)
    }

    fn fory_read_type_info(context: &mut ReadContext, is_field: bool) {
        T::fory_read_type_info(context, is_field);
    }

    fn fory_reserved_space() -> usize {
        // RcWeak is a shared ref, return 0 to avoid infinite recursion
        0
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
    fn fory_is_shared_ref() -> bool {
        true
    }

    fn fory_write(&self, context: &mut WriteContext, _is_field: bool) {
        if let Some(arc) = self.upgrade() {
            // IMPORTANT: If the target Arc was serialized already, just write a ref
            if context.ref_writer.try_write_arc_ref(context.writer, &arc) {
                // Already seen, wrote Ref flag + id, we're done
                return;
            }
            // First time seeing this object, write RefValue and then its data
            T::fory_write_data(&*arc, context, _is_field);
        } else {
            context.writer.write_i8(RefFlag::Null as i8);
        }
    }

    fn fory_write_data(&self, context: &mut WriteContext, is_field: bool) {
        self.fory_write(context, is_field);
    }

    fn fory_write_type_info(context: &mut WriteContext, is_field: bool) {
        T::fory_write_type_info(context, is_field);
    }

    fn fory_read(context: &mut ReadContext, _is_field: bool) -> Result<Self, Error> {
        let ref_flag = context.ref_reader.read_ref_flag(&mut context.reader);

        match ref_flag {
            RefFlag::Null => Ok(ArcWeak::new()),
            RefFlag::RefValue => {
                let data = T::fory_read_data(context, _is_field)?;
                let arc = Arc::new(data);
                let ref_id = context.ref_reader.store_arc_ref(arc);
                let arc = context.ref_reader.get_arc_ref::<T>(ref_id).unwrap();
                let weak = ArcWeak::from(&arc);
                Ok(weak)
            }
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
            _ => Err(anyhow!("Weak can only be Null, RefValue or Ref, got {:?}", ref_flag).into()),
        }
    }

    fn fory_read_data(context: &mut ReadContext, is_field: bool) -> Result<Self, Error> {
        Self::fory_read(context, is_field)
    }

    fn fory_read_type_info(context: &mut ReadContext, is_field: bool) {
        T::fory_read_type_info(context, is_field);
    }

    fn fory_reserved_space() -> usize {
        // ArcWeak is a shared ref, return 0 to avoid infinite recursion
        0
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
