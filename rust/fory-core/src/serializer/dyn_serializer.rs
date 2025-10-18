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

use crate::ensure;
use crate::error::Error;
use crate::resolver::context::{ReadContext, WriteContext};
use crate::resolver::type_resolver::{TypeInfo, TypeResolver};
use crate::serializer::{ForyDefault, Serializer};
use crate::RefFlag;
use std::sync::Arc;

impl Default for Box<dyn Serializer> {
    fn default() -> Self {
        Box::new(0)
    }
}

impl ForyDefault for Box<dyn Serializer> {
    fn fory_default() -> Self {
        Box::new(0)
    }
}

impl Serializer for Box<dyn Serializer> {
    fn fory_write(
        &self,
        context: &mut WriteContext,
        write_ref_info: bool,
        write_type_info: bool,
        has_generics: bool,
    ) -> Result<(), Error> {
        if write_ref_info {
            context.writer.write_i8(RefFlag::NotNullValue as i8);
        }
        let concrete_type_id = (**self).fory_concrete_type_id();
        if write_type_info {
            context.write_any_typeinfo(concrete_type_id)?;
        };
        self.fory_write_data_generic(context, has_generics)
    }

    fn fory_write_data(&self, context: &mut WriteContext) -> Result<(), Error> {
        self.fory_write_data_generic(context, false)
    }

    fn fory_write_data_generic(
        &self,
        context: &mut WriteContext,
        has_generics: bool,
    ) -> Result<(), Error> {
        let concrete_type_id = (**self).fory_concrete_type_id();
        context
            .get_type_info(&concrete_type_id)?
            .get_harness()
            .get_write_data_fn()(self.as_any(), context, has_generics)
    }

    fn fory_type_id_dyn(&self, type_resolver: &TypeResolver) -> Result<u32, Error> {
        (**self).fory_type_id_dyn(type_resolver)
    }

    fn as_any(&self) -> &dyn std::any::Any {
        (**self).as_any()
    }

    fn fory_is_polymorphic() -> bool {
        true
    }

    fn fory_write_type_info(_context: &mut WriteContext) -> Result<(), Error> {
        panic!("Box<dyn Serializer> is polymorphic - can's write type info statically");
    }

    fn fory_read_type_info(context: &mut ReadContext) -> Result<(), Error> {
        context.read_any_typeinfo()?;
        Ok(())
    }

    fn fory_read(
        context: &mut ReadContext,
        read_ref_info: bool,
        read_type_info: bool,
    ) -> Result<Self, Error> {
        read_box_seralizer(context, read_ref_info, read_type_info, None)
    }

    fn fory_read_with_typeinfo(
        context: &mut ReadContext,
        read_ref_info: bool,
        type_info: Arc<TypeInfo>,
    ) -> Result<Self, Error>
    where
        Self: Sized + ForyDefault,
    {
        read_box_seralizer(context, read_ref_info, false, Some(type_info))
    }

    fn fory_read_data(_context: &mut ReadContext) -> Result<Self, Error> {
        panic!("fory_read_data should not be called directly on Box<dyn Serializer>");
    }
}

fn read_box_seralizer(
    context: &mut ReadContext,
    read_ref_info: bool,
    read_type_info: bool,
    type_info: Option<Arc<TypeInfo>>,
) -> Result<Box<dyn Serializer>, Error> {
    context.inc_depth()?;
    let ref_flag = if read_ref_info {
        context.reader.read_i8()?
    } else {
        RefFlag::NotNullValue as i8
    };
    if ref_flag != RefFlag::NotNullValue as i8 {
        return Err(Error::InvalidData(
            "Expected NotNullValue for Box<dyn Serializer>".into(),
        ));
    }
    let typeinfo = if let Some(type_info) = type_info {
        type_info
    } else {
        ensure!(
            read_type_info,
            Error::InvalidData("Type info must be read for Box<dyn Serializer>".into())
        );
        context.read_any_typeinfo()?
    };
    let harness = typeinfo.get_harness();
    let boxed_any = harness.get_read_data_fn()(context)?;
    let trait_object = harness.get_to_serializer()(boxed_any)?;
    context.dec_depth();
    Ok(trait_object)
}
