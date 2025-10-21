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
use crate::resolver::context::ReadContext;
use crate::resolver::context::WriteContext;
use crate::resolver::type_resolver::TypeResolver;
use crate::serializer::collection::{
    read_collection_type_info, write_collection_type_info, DECL_ELEMENT_TYPE, HAS_NULL,
    IS_SAME_TYPE, TRACKING_REF,
};

use crate::serializer::{ForyDefault, Serializer};
use crate::types::{need_to_write_type_for_field, RefFlag, TypeId};
use std::collections::BinaryHeap;
use std::mem;

fn write_binaryheap_collection_data<T: Serializer>(
    heap: &BinaryHeap<T>,
    context: &mut WriteContext,
    has_generics: bool,
) -> Result<(), Error> {
    let len = heap.len();
    context.writer.write_varuint32(len as u32);
    if len == 0 {
        return Ok(());
    }
    if T::fory_is_polymorphic() || T::fory_is_shared_ref() {
        return write_binaryheap_collection_data_dyn_ref(heap, context, has_generics);
    }
    let mut header = IS_SAME_TYPE;
    let mut has_null = false;
    let elem_static_type_id = T::fory_static_type_id();
    let is_elem_declared = has_generics && !need_to_write_type_for_field(elem_static_type_id);
    if T::fory_is_option() {
        for item in heap.iter() {
            if item.fory_is_none() {
                has_null = true;
                break;
            }
        }
    }
    if has_null {
        header |= HAS_NULL;
    }
    if is_elem_declared {
        header |= DECL_ELEMENT_TYPE;
        context.writer.write_u8(header);
    } else {
        context.writer.write_u8(header);
        T::fory_write_type_info(context)?;
    }
    if !has_null {
        for item in heap.iter() {
            item.fory_write_data_generic(context, has_generics)?;
        }
    } else {
        for item in heap.iter() {
            if item.fory_is_none() {
                context.writer.write_u8(RefFlag::Null as u8);
                continue;
            }
            context.writer.write_u8(RefFlag::NotNullValue as u8);
            item.fory_write_data_generic(context, has_generics)?;
        }
    }

    Ok(())
}

fn write_binaryheap_collection_data_dyn_ref<T: Serializer>(
    heap: &BinaryHeap<T>,
    context: &mut WriteContext,
    has_generics: bool,
) -> Result<(), Error> {
    let elem_static_type_id = T::fory_static_type_id();
    let is_elem_declared = has_generics && !need_to_write_type_for_field(elem_static_type_id);
    let elem_is_polymorphic = T::fory_is_polymorphic();
    let elem_is_shared_ref = T::fory_is_shared_ref();

    let mut has_null = false;
    let mut is_same_type = true;
    let mut first_type_id: Option<std::any::TypeId> = None;

    for item in heap.iter() {
        if item.fory_is_none() {
            has_null = true;
        } else if elem_is_polymorphic && is_same_type {
            let concrete_id = item.fory_concrete_type_id();
            if let Some(first_id) = first_type_id {
                if first_id != concrete_id {
                    is_same_type = false;
                }
            } else {
                first_type_id = Some(concrete_id);
            }
        }
    }

    if elem_is_polymorphic && is_same_type && first_type_id.is_none() {
        is_same_type = false;
    }

    let mut header = 0u8;
    if has_null {
        header |= HAS_NULL;
    }
    if is_elem_declared {
        header |= DECL_ELEMENT_TYPE;
    }
    if is_same_type {
        header |= IS_SAME_TYPE;
    }
    if elem_is_shared_ref {
        header |= TRACKING_REF;
    }

    context.writer.write_u8(header);

    if is_same_type && !is_elem_declared {
        if elem_is_polymorphic {
            let type_id = first_type_id.ok_or_else(|| {
                Error::type_error(
                    "Unable to determine concrete type for polymorphic collection elements",
                )
            })?;
            context.write_any_typeinfo(T::fory_static_type_id() as u32, type_id)?;
        } else {
            T::fory_write_type_info(context)?;
        }
    }

    if is_same_type {
        if !has_null {
            if elem_is_shared_ref {
                for item in heap.iter() {
                    item.fory_write(context, true, false, has_generics)?;
                }
            } else {
                for item in heap.iter() {
                    item.fory_write_data_generic(context, has_generics)?;
                }
            }
        } else {
            for item in heap.iter() {
                item.fory_write(context, true, false, has_generics)?;
            }
        }
    } else if !has_null {
        if elem_is_shared_ref {
            for item in heap.iter() {
                item.fory_write(context, true, true, has_generics)?;
            }
        } else {
            for item in heap.iter() {
                item.fory_write(context, false, true, has_generics)?;
            }
        }
    } else {
        for item in heap.iter() {
            item.fory_write(context, true, true, has_generics)?;
        }
    }

    Ok(())
}

crate::impl_collection_read_data! {
    {
        name: read_binaryheap_collection_data,
        dyn_name: read_binaryheap_collection_data_dyn_ref,
        collection: BinaryHeap<T>,
        empty: BinaryHeap::new(),
        with_capacity: |len| BinaryHeap::with_capacity(len as usize),
        insert: |result, value| { result.push(value); },
        where T: Ord
    }
}

impl<T: Serializer + ForyDefault + Ord> Serializer for BinaryHeap<T> {
    fn fory_write_data(&self, context: &mut WriteContext) -> Result<(), Error> {
        write_binaryheap_collection_data(self, context, false)
    }

    fn fory_write_type_info(context: &mut WriteContext) -> Result<(), Error> {
        write_collection_type_info(context, TypeId::SET as u32)
    }

    fn fory_read_data(context: &mut ReadContext) -> Result<Self, Error> {
        let len = context.reader.read_varuint32()?;
        read_binaryheap_collection_data(context, len)
    }

    fn fory_read_type_info(context: &mut ReadContext) -> Result<(), Error> {
        read_collection_type_info(context, TypeId::SET as u32)
    }

    fn fory_reserved_space() -> usize {
        mem::size_of::<i32>()
    }

    fn fory_get_type_id(_: &TypeResolver) -> Result<u32, Error> {
        Ok(TypeId::SET as u32)
    }

    fn fory_type_id_dyn(&self, _: &TypeResolver) -> Result<u32, Error> {
        Ok(TypeId::SET as u32)
    }

    fn fory_static_type_id() -> TypeId {
        TypeId::SET
    }

    fn as_any(&self) -> &dyn std::any::Any {
        self
    }
}

impl<T: Ord> ForyDefault for BinaryHeap<T> {
    fn fory_default() -> Self {
        BinaryHeap::new()
    }
}
