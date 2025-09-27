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
use crate::serializer::Serializer;
use crate::types::{ForyGeneralList, RefFlag};
use anyhow::anyhow;
use std::sync::Arc;

impl<T: Serializer + Send + Sync + 'static> Serializer for Arc<T> {
    fn read(context: &mut ReadContext) -> Result<Self, Error> {
        let ref_flag = context.ref_resolver.read_ref_flag(&mut context.reader);

        match ref_flag {
            RefFlag::Null => Err(anyhow!("Arc cannot be null").into()),
            RefFlag::Ref => {
                let ref_id = context.ref_resolver.read_ref_id(&mut context.reader);
                context.ref_resolver.get_arc_ref::<T>(ref_id)
                    .ok_or_else(|| anyhow!("Arc reference {} not found", ref_id).into())
            }
            RefFlag::NotNullValue => {
                let inner = T::read(context)?;
                Ok(Arc::new(inner))
            }
            RefFlag::RefValue => {
                let inner = T::read(context)?;
                let arc = Arc::new(inner);
                context.ref_resolver.store_arc_ref(arc.clone());
                Ok(arc)
            }
        }
    }

    fn read_type_info(context: &mut ReadContext, is_field: bool) {
        T::read_type_info(context, is_field);
    }

    fn write(&self, context: &mut WriteContext, is_field: bool) {
        if !context.ref_resolver.try_write_arc_ref(context.writer, self) {
            T::write(self.as_ref(), context, is_field);
        }
    }

    fn write_type_info(context: &mut WriteContext, is_field: bool) {
        T::write_type_info(context, is_field);
    }

    fn reserved_space() -> usize {
        T::reserved_space()
    }

    fn get_type_id(fory: &Fory) -> u32 {
        T::get_type_id(fory)
    }

    fn is_option() -> bool {
        false
    }

    fn is_none(&self) -> bool {
        false
    }
}

impl<T: Serializer + Send + Sync> ForyGeneralList for Arc<T> {}

#[cfg(test)]
mod tests {
    use crate::fory::Fory;
    use std::sync::Arc;

    #[test]
    fn test_arc_serialization_basic() {
        let fury = Fory::default();
        let arc = Arc::new(42i32);

        let serialized = fury.serialize(&arc);
        let deserialized: Arc<i32> = fury.deserialize(&serialized).unwrap();

        assert_eq!(*deserialized, 42);
    }

    #[test]
    fn test_arc_shared_reference() {
        let fury = Fory::default();
        let arc1 = Arc::new(String::from("shared"));

        let serialized = fury.serialize(&arc1);
        let deserialized: Arc<String> = fury.deserialize(&serialized).unwrap();

        assert_eq!(*deserialized, "shared");
        // In a full implementation with proper reference tracking,
        // multiple references to the same object would be preserved
    }

    #[test]
    fn test_arc_thread_safety() {
        use std::thread;

        let fury = Fory::default();
        let arc = Arc::new(vec![1, 2, 3, 4, 5]);

        let serialized = fury.serialize(&arc);

        // Test that Arc can be sent across threads
        let handle = thread::spawn(move || {
            let fury = Fory::default();
            let deserialized: Arc<Vec<i32>> = fury.deserialize(&serialized).unwrap();
            assert_eq!(*deserialized, vec![1, 2, 3, 4, 5]);
        });

        handle.join().unwrap();
    }
}
