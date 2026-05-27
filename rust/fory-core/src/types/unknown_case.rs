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

use std::any::Any;
use std::fmt;
use std::hash::{Hash, Hasher};
use std::sync::Arc;

#[derive(Clone)]
pub struct UnknownCase {
    case_id: u32,
    type_id: u32,
    // Keep resolver TypeInfo/Rc out of the carrier. Generated unions can outlive or move
    // independently from the resolver context, so the carrier stores only stable metadata
    // plus the dynamic payload owned by Rust's existing polymorphic Arc path.
    value: Arc<dyn Any>,
}

impl UnknownCase {
    pub fn new<T>(case_id: u32, type_id: u32, value: T) -> Self
    where
        T: Any,
    {
        Self {
            case_id,
            type_id,
            value: Arc::new(value),
        }
    }

    pub fn case_id(&self) -> u32 {
        self.case_id
    }

    pub(crate) fn type_id(&self) -> u32 {
        self.type_id
    }

    pub fn value(&self) -> &dyn Any {
        self.value.as_ref()
    }

    pub fn downcast_ref<T: Any>(&self) -> Option<&T> {
        self.value.downcast_ref::<T>()
    }

    pub(crate) fn value_arc(&self) -> &Arc<dyn Any> {
        &self.value
    }

    pub(crate) fn from_runtime(case_id: u32, type_id: u32, value: Arc<dyn Any>) -> Self {
        Self {
            case_id,
            type_id,
            value,
        }
    }
}

impl PartialEq for UnknownCase {
    fn eq(&self, other: &Self) -> bool {
        Arc::ptr_eq(&self.value, &other.value)
    }
}

impl Eq for UnknownCase {}

impl Hash for UnknownCase {
    fn hash<H: Hasher>(&self, state: &mut H) {
        let ptr = Arc::as_ptr(&self.value) as *const () as usize;
        ptr.hash(state);
    }
}

impl fmt::Debug for UnknownCase {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("UnknownCase")
            .field("case_id", &self.case_id)
            .field("type_id", &self.type_id())
            .finish_non_exhaustive()
    }
}
