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
use crate::meta::FieldInfo;
use crate::resolver::context::{ReadContext, WriteContext};
use crate::serializer::util::{
    read_compatible_default, read_ext_type_info, read_ref_info_data, write_ext_type_info,
    write_ref_info_data,
};
use crate::serializer::{bool, struct_};
use crate::types::TypeId;
use crate::TypeResolver;
use std::any::Any;

pub trait ForyDefault: Sized {
    fn fory_default() -> Self;
}

// We can't add blanket impl for all T: Default because it conflicts with other impls.
// For example, upstream crates may add a new impl of trait `std::default::Default` for
// type `std::rc::Rc<(dyn std::any::Any + 'static)>` in future versions.
// impl<T: Default + Sized> ForyDefault for T {
//     fn fory_default() -> Self {
//         Default::default()
//     }
// }

pub trait Serializer: 'static {
    /// Entry point of the serialization.
    fn fory_write(&self, context: &mut WriteContext) -> Result<(), Error>
    where
        Self: Sized,
    {
        write_ref_info_data(self, context, false, false)
    }

    fn fory_read(context: &mut ReadContext) -> Result<Self, Error>
    where
        Self: Sized + ForyDefault,
    {
        read_ref_info_data(context, false, false)
    }

    fn fory_is_option() -> bool
    where
        Self: Sized,
    {
        false
    }

    fn fory_is_none(&self) -> bool {
        false
    }

    fn fory_is_polymorphic() -> bool
    where
        Self: Sized,
    {
        false
    }

    fn fory_is_shared_ref() -> bool
    where
        Self: Sized,
    {
        false
    }

    fn fory_static_type_id() -> TypeId
    where
        Self: Sized,
    {
        // set to ext to simplify the user defined serializer.
        // serializer for other types will override this method.
        TypeId::EXT
    }

    fn fory_get_type_id(type_resolver: &TypeResolver) -> Result<u32, Error>
    where
        Self: Sized,
    {
        Ok(type_resolver
            .get_type_info(std::any::TypeId::of::<Self>())?
            .get_type_id())
    }

    fn fory_type_id_dyn(&self, type_resolver: &TypeResolver) -> Result<u32, Error>;

    /// The possible max memory size of the type.
    /// Used to reserve the buffer space to avoid reallocation, which may hurt performance.
    fn fory_reserved_space() -> usize
    where
        Self: Sized,
    {
        0
    }

    fn fory_write_type_info(context: &mut WriteContext) -> Result<(), Error>
    where
        Self: Sized,
    {
        // default implementation only for ext/named_ext
        // this method will be overridden by serializers for supported types
        write_ext_type_info::<Self>(context)
    }

    fn fory_read_type_info(context: &mut ReadContext) -> Result<(), Error>
    where
        Self: Sized,
    {
        // default implementation only for ext/named_ext
        // this method will be overridden by serializers for supported types
        read_ext_type_info::<Self>(context)
    }

    // only used by struct/enum/ext
    fn fory_read_compatible(context: &mut ReadContext) -> Result<Self, Error>
    where
        Self: Sized,
    {
        // default logic only for ext/named_ext
        // this method will be overridden by fory macro generated serializers
        read_compatible_default::<Self>(context)
    }

    /// Write the data into the buffer. Need to be implemented.
    fn fory_write_data(&self, context: &mut WriteContext) -> Result<(), Error>;

    /// Write the data into the buffer. Need to be implemented for collection/map.
    /// For other types, just forward to `fory_write_data`.
    fn fory_write_data_generic(
        &self,
        context: &mut WriteContext,
        _has_generics: bool,
    ) -> Result<(), Error> {
        self.fory_write_data(context)
    }

    fn fory_read_data(context: &mut ReadContext) -> Result<Self, Error>
    where
        Self: Sized + ForyDefault;

    fn fory_concrete_type_id(&self) -> std::any::TypeId {
        std::any::TypeId::of::<Self>()
    }

    fn as_any(&self) -> &dyn Any;
}

pub trait StructSerializer: Serializer + 'static {
    fn fory_fields_info(_: &TypeResolver) -> Result<Vec<FieldInfo>, Error> {
        Ok(Vec::default())
    }

    fn fory_type_index() -> u32 {
        unimplemented!()
    }

    fn fory_actual_type_id(type_id: u32, register_by_name: bool, compatible: bool) -> u32 {
        struct_::actual_type_id(type_id, register_by_name, compatible)
    }

    fn fory_get_sorted_field_names() -> &'static [&'static str] {
        &[]
    }
}

pub trait CollectionSerializer: Serializer + 'static {
    fn fory_write_collection_field(&self, context: &mut WriteContext) -> Result<(), Error>;
}

pub trait MapSerializer: Serializer + 'static {
    fn fory_write_map_field(&self, context: &mut WriteContext) -> Result<(), Error>;
}
