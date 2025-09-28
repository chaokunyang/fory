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
use crate::fory::Fory;
use crate::meta::TypeMeta;
use crate::resolver::ref_resolver::{RefReader, RefWriter};
use std::rc::Rc;

pub struct WriteContext<'se> {
    pub writer: &'se mut Writer,
    fory: &'se Fory,
    pub ref_writer: &'se mut RefWriter,
}

impl<'se> WriteContext<'se> {
    pub fn new(
        fory: &'se Fory,
        writer: &'se mut Writer,
        ref_writer: &'se mut RefWriter,
    ) -> WriteContext<'se> {
        WriteContext {
            writer,
            fory,
            ref_writer,
        }
    }

    pub fn empty(&mut self) -> bool {
        unsafe { self.fory.get_type_resolver_mut().is_meta_empty() }
    }

    pub fn push_meta(&mut self, type_id: std::any::TypeId) -> usize {
        unsafe { self.fory.get_type_resolver_mut().push_meta(type_id) }
    }

    pub fn write_meta(&mut self, offset: usize) {
        self.writer.set_bytes(
            offset,
            &((self.writer.len() - offset - 4) as u32).to_le_bytes(),
        );
        unsafe {
            self.fory
                .get_type_resolver_mut()
                .meta_to_bytes(self.writer)
                .unwrap()
        }
    }

    pub fn get_fory(&self) -> &Fory {
        self.fory
    }
}

pub struct ReadContext<'de, 'bf: 'de> {
    pub reader: Reader<'bf>,
    fory: &'de Fory,
    pub ref_reader: &'de mut RefReader,
}

impl<'de, 'bf: 'de> ReadContext<'de, 'bf> {
    pub fn new(
        fory: &'de Fory,
        reader: Reader<'bf>,
        ref_reader: &'de mut RefReader,
    ) -> ReadContext<'de, 'bf> {
        ReadContext {
            reader,
            fory,
            ref_reader,
        }
    }

    pub fn get_fory(&self) -> &Fory {
        self.fory
    }

    pub fn get_meta(&self, type_index: usize) -> &Rc<TypeMeta> {
        self.fory.get_type_resolver().get_meta(type_index)
    }

    pub fn load_meta(&mut self, offset: usize) -> usize {
        unsafe {
            self.fory
                .get_type_resolver_mut()
                .load_meta(&mut Reader::new(
                    &self.reader.slice_after_cursor()[offset..],
                ))
        }
    }
}
