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

use crate::buffer::{Reader, Writer};
use crate::types::RefFlag;
use std::any::Any;
use std::collections::HashMap;
use std::rc::Rc;
use std::sync::Arc;

/// Reference resolver for tracking shared references during serialization/deserialization
#[derive(Default)]
pub struct RefResolver {
    /// Maps pointer addresses to reference IDs for serialization
    write_refs: HashMap<usize, u32>,
    /// Vector to store boxed objects for deserialization
    read_refs: Vec<Box<dyn Any>>,
    /// Next reference ID to assign
    next_ref_id: u32,
}

impl RefResolver {
    pub fn new() -> Self {
        Self::default()
    }

    /// Attempt to write a reference for an Rc<T>. Returns true if reference was written,
    /// false if this is the first occurrence and should be serialized normally.
    pub fn try_write_rc_ref<T>(&mut self, writer: &mut Writer, rc: &Rc<T>) -> bool {
        let ptr_addr = Rc::as_ptr(rc) as usize;

        if let Some(&ref_id) = self.write_refs.get(&ptr_addr) {
            // This object has been seen before, write a reference
            writer.i8(RefFlag::Ref as i8);
            writer.u32(ref_id);
            true
        } else {
            // First time seeing this object, register it and return false
            let ref_id = self.next_ref_id;
            self.next_ref_id += 1;
            self.write_refs.insert(ptr_addr, ref_id);
            writer.i8(RefFlag::RefValue as i8);
            false
        }
    }

    /// Attempt to write a reference for an Arc<T>. Returns true if reference was written,
    /// false if this is the first occurrence and should be serialized normally.
    pub fn try_write_arc_ref<T>(&mut self, writer: &mut Writer, arc: &Arc<T>) -> bool {
        let ptr_addr = Arc::as_ptr(arc) as usize;

        if let Some(&ref_id) = self.write_refs.get(&ptr_addr) {
            // This object has been seen before, write a reference
            writer.i8(RefFlag::Ref as i8);
            writer.u32(ref_id);
            true
        } else {
            // First time seeing this object, register it and return false
            let ref_id = self.next_ref_id;
            self.next_ref_id += 1;
            self.write_refs.insert(ptr_addr, ref_id);
            writer.i8(RefFlag::RefValue as i8);
            false
        }
    }

    /// Store an Rc<T> for later reference resolution during deserialization
    pub fn store_rc_ref<T: 'static>(&mut self, rc: Rc<T>) -> u32 {
        let ref_id = self.read_refs.len() as u32;
        self.read_refs.push(Box::new(rc));
        ref_id
    }

    /// Store an Arc<T> for later reference resolution during deserialization
    pub fn store_arc_ref<T: 'static>(&mut self, arc: Arc<T>) -> u32 {
        let ref_id = self.read_refs.len() as u32;
        self.read_refs.push(Box::new(arc));
        ref_id
    }

    /// Get an Rc<T> by reference ID during deserialization
    pub fn get_rc_ref<T: 'static>(&self, ref_id: u32) -> Option<Rc<T>> {
        let any_box = self.read_refs.get(ref_id as usize)?;
        any_box.downcast_ref::<Rc<T>>().cloned()
    }

    /// Get an Arc<T> by reference ID during deserialization
    pub fn get_arc_ref<T: 'static>(&self, ref_id: u32) -> Option<Arc<T>> {
        let any_box = self.read_refs.get(ref_id as usize)?;
        any_box.downcast_ref::<Arc<T>>().cloned()
    }

    /// Read a reference flag and determine what action to take
    pub fn read_ref_flag(&self, reader: &mut Reader) -> RefFlag {
        let flag_value = reader.i8();
        match flag_value {
            -3 => RefFlag::Null,
            -2 => RefFlag::Ref,
            -1 => RefFlag::NotNullValue,
            0 => RefFlag::RefValue,
            _ => panic!("Invalid reference flag: {}", flag_value),
        }
    }

    /// Read a reference ID
    pub fn read_ref_id(&self, reader: &mut Reader) -> u32 {
        reader.u32()
    }

    /// Clear all stored references (useful for reusing the resolver)
    pub fn clear(&mut self) {
        self.write_refs.clear();
        self.read_refs.clear();
        self.next_ref_id = 0;
    }
}
