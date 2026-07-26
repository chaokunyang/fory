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

//! Serialization support for [`RcWeak`] and [`ArcWeak`].

use super::codec::{codec_read_type_info, codec_read_type_info_static, Codec};
use crate::context::{ReadContext, WriteContext};
use crate::error::Error;
use crate::meta::FieldType;
use crate::resolver::{RefFlag, RefMode, TypeInfo, TypeResolver};
use crate::type_id::TypeId;
use crate::types::{ArcWeak, RcWeak};
use std::marker::PhantomData;
use std::rc::Rc;
use std::sync::Arc;

pub struct RcWeakCodec<T, C, const NULLABLE: bool, const TRACK_REF: bool>(PhantomData<(T, C)>);

pub struct ArcWeakCodec<T, C, const NULLABLE: bool, const TRACK_REF: bool>(PhantomData<(T, C)>);

#[inline(always)]
fn reserve_weak_cell<W>(context: &mut ReadContext) -> Result<(), Error> {
    let bytes = std::mem::size_of::<W>();
    if bytes != 0 {
        context.reserve_graph_memory(bytes)?;
    }
    Ok(())
}

#[inline(always)]
fn reserve_strong<T>(context: &mut ReadContext) -> Result<(), Error> {
    let bytes = std::mem::size_of::<T>();
    if bytes != 0 {
        context.reserve_graph_memory(bytes)?;
    }
    Ok(())
}

#[cold]
#[inline(never)]
fn rc_weak_tracking_error() -> Error {
    Error::invalid_ref(
        "RcWeak requires track_ref to be enabled. \
         Use Fory::builder().track_ref(true).build()",
    )
}

#[cold]
#[inline(never)]
fn arc_weak_tracking_error() -> Error {
    Error::invalid_ref(
        "ArcWeak requires track_ref to be enabled. \
         Use Fory::builder().track_ref(true).build()",
    )
}

#[cold]
#[inline(never)]
fn weak_ref_missing_after_insert(owner: &str, ref_id: u32) -> Error {
    Error::invalid_ref(format!(
        "{owner} reference {ref_id} not found after insertion"
    ))
}

#[cold]
#[inline(never)]
fn weak_write_mode_error(owner: &str) -> Error {
    Error::invalid_ref(format!(
        "{owner} requires RefMode::Tracking for serialization"
    ))
}

#[cold]
#[inline(never)]
fn weak_read_mode_error(owner: &str) -> Error {
    Error::invalid_ref(format!(
        "{owner} requires RefMode::Tracking for deserialization"
    ))
}

#[cold]
#[inline(never)]
fn weak_untracked_value(owner: &str) -> Error {
    Error::invalid_ref(format!("{owner} cannot contain an untracked strong value"))
}

#[inline(always)]
fn write_rc_weak<T: 'static, C: Codec<T>>(
    value: &RcWeak<T>,
    context: &mut WriteContext,
    ref_mode: RefMode,
    write_type_info: bool,
    has_generics: bool,
) -> Result<(), Error> {
    if !context.is_track_ref() {
        return Err(rc_weak_tracking_error());
    }
    if ref_mode != RefMode::Tracking {
        return Err(weak_write_mode_error("RcWeak"));
    }
    let Some(value) = value.upgrade() else {
        context.writer.write_i8(RefFlag::Null as i8);
        return Ok(());
    };
    if context
        .ref_writer
        .try_write_rc_ref(&mut context.writer, &value)
    {
        return Ok(());
    }
    C::write_with_mode(
        &value,
        context,
        RefMode::None,
        write_type_info,
        has_generics,
    )
}

#[inline(always)]
fn write_rc_weak_with_type_info<T: 'static, C: Codec<T>>(
    value: &RcWeak<T>,
    context: &mut WriteContext,
    ref_mode: RefMode,
    type_info: &Rc<TypeInfo>,
    has_generics: bool,
) -> Result<(), Error> {
    if !context.is_track_ref() {
        return Err(rc_weak_tracking_error());
    }
    if ref_mode != RefMode::Tracking {
        return Err(weak_write_mode_error("RcWeak"));
    }
    let Some(value) = value.upgrade() else {
        context.writer.write_i8(RefFlag::Null as i8);
        return Ok(());
    };
    if context
        .ref_writer
        .try_write_rc_ref(&mut context.writer, &value)
    {
        return Ok(());
    }
    C::write_with_type_info(&value, context, RefMode::None, type_info, has_generics)
}

#[inline(always)]
fn read_rc_inner<T: 'static, C: Codec<T>>(
    context: &mut ReadContext,
    read_type_info: bool,
    type_info: Option<&Rc<TypeInfo>>,
    remote_field_type: Option<&FieldType>,
) -> Result<T, Error> {
    reserve_strong::<T>(context)?;
    if let Some(type_info) = type_info {
        return C::read_with_type_info(context, RefMode::None, type_info);
    }
    if let Some(remote_field_type) = remote_field_type {
        // The weak envelope owns only reference framing. A compatible
        // metadata-bearing child still owns its inline TypeInfo before its
        // body, while declared carrier children consume the remote schema
        // directly.
        if codec_read_type_info::<T, C>(context, remote_field_type) {
            return C::read_with_mode(context, RefMode::None, true);
        }
        return C::read_data_with_type(context, remote_field_type);
    }
    // The weak envelope has consumed its ref flag; the child still owns any
    // inline dynamic metadata before its body.
    C::read_with_mode(context, RefMode::None, read_type_info)
}

#[inline(always)]
fn read_rc_weak<T: 'static, C: Codec<T>>(
    context: &mut ReadContext,
    ref_mode: RefMode,
    read_type_info: bool,
    type_info: Option<&Rc<TypeInfo>>,
    remote_field_type: Option<&FieldType>,
) -> Result<RcWeak<T>, Error> {
    if ref_mode != RefMode::Tracking {
        return Err(weak_read_mode_error("RcWeak"));
    }
    match context.ref_reader.read_ref_flag(&mut context.reader)? {
        RefFlag::Null => {
            reserve_weak_cell::<std::rc::Weak<T>>(context)?;
            Ok(RcWeak::new())
        }
        RefFlag::RefValue => {
            context.inc_depth()?;
            let result =
                read_rc_inner::<T, C>(context, read_type_info, type_info, remote_field_type);
            context.dec_depth();
            let value = result?;
            let strong = Rc::new(value);
            let ref_id = context.ref_reader.store_rc_ref(strong);
            let strong = context
                .ref_reader
                .get_rc_ref::<T>(ref_id)
                .ok_or_else(|| weak_ref_missing_after_insert("Rc", ref_id))?;
            reserve_weak_cell::<std::rc::Weak<T>>(context)?;
            Ok(RcWeak::from(&strong))
        }
        RefFlag::Ref => {
            let ref_id = context.ref_reader.read_ref_id(&mut context.reader)?;
            reserve_weak_cell::<std::rc::Weak<T>>(context)?;
            if let Some(strong) = context.ref_reader.get_rc_ref::<T>(ref_id) {
                return Ok(RcWeak::from(&strong));
            }
            let weak = RcWeak::new();
            let callback_weak = weak.clone();
            context.ref_reader.add_callback(Box::new(move |reader| {
                if let Some(strong) = reader.get_rc_ref::<T>(ref_id) {
                    callback_weak.update(Rc::downgrade(&strong));
                }
            }));
            Ok(weak)
        }
        RefFlag::NotNullValue => Err(weak_untracked_value("RcWeak")),
    }
}

impl<T, C, const NULLABLE: bool, const TRACK_REF: bool> Codec<RcWeak<T>>
    for RcWeakCodec<T, C, NULLABLE, TRACK_REF>
where
    T: 'static,
    C: Codec<T>,
{
    #[inline(always)]
    fn field_type(type_resolver: &TypeResolver) -> Result<FieldType, Error> {
        let mut field_type = C::field_type(type_resolver)?;
        field_type.nullable = NULLABLE;
        field_type.track_ref = true;
        Ok(field_type)
    }

    #[inline(always)]
    fn reserved_space() -> usize {
        4
    }

    #[inline(always)]
    fn write_field(value: &RcWeak<T>, context: &mut WriteContext) -> Result<(), Error> {
        write_rc_weak::<T, C>(
            value,
            context,
            RefMode::Tracking,
            super::codec::codec_write_type_info::<T, C>(context),
            true,
        )
    }

    #[inline(always)]
    fn read_field(context: &mut ReadContext) -> Result<RcWeak<T>, Error> {
        read_rc_weak::<T, C>(
            context,
            RefMode::Tracking,
            codec_read_type_info_static::<T, C>(context),
            None,
            None,
        )
    }

    #[cold]
    #[inline(never)]
    fn write_data(_: &RcWeak<T>, _: &mut WriteContext) -> Result<(), Error> {
        Err(Error::not_allowed(
            "RcWeak must be written through its reference-tracking envelope",
        ))
    }

    #[cold]
    #[inline(never)]
    fn read_data(_: &mut ReadContext) -> Result<RcWeak<T>, Error> {
        Err(Error::not_allowed(
            "RcWeak must be read through its reference-tracking envelope",
        ))
    }

    #[inline(always)]
    fn read_field_with_type(
        context: &mut ReadContext,
        remote_field_type: &FieldType,
    ) -> Result<RcWeak<T>, Error> {
        read_rc_weak::<T, C>(
            context,
            RefMode::Tracking,
            false,
            None,
            Some(remote_field_type),
        )
    }

    #[inline(always)]
    fn write_with_mode(
        value: &RcWeak<T>,
        context: &mut WriteContext,
        ref_mode: RefMode,
        write_type_info: bool,
        has_generics: bool,
    ) -> Result<(), Error> {
        write_rc_weak::<T, C>(value, context, ref_mode, write_type_info, has_generics)
    }

    #[inline(always)]
    fn write_type_info_value(
        context: &mut WriteContext,
        target_type_id: std::any::TypeId,
    ) -> Result<Rc<TypeInfo>, Error> {
        C::write_type_info_value(context, target_type_id)
    }

    #[inline(always)]
    fn write_with_type_info(
        value: &RcWeak<T>,
        context: &mut WriteContext,
        ref_mode: RefMode,
        type_info: &Rc<TypeInfo>,
        has_generics: bool,
    ) -> Result<(), Error> {
        write_rc_weak_with_type_info::<T, C>(value, context, ref_mode, type_info, has_generics)
    }

    #[inline(always)]
    fn read_with_mode(
        context: &mut ReadContext,
        ref_mode: RefMode,
        read_type_info: bool,
    ) -> Result<RcWeak<T>, Error> {
        read_rc_weak::<T, C>(context, ref_mode, read_type_info, None, None)
    }

    #[inline(always)]
    fn read_with_type_info(
        context: &mut ReadContext,
        ref_mode: RefMode,
        type_info: &Rc<TypeInfo>,
    ) -> Result<RcWeak<T>, Error> {
        read_rc_weak::<T, C>(context, ref_mode, false, Some(type_info), None)
    }

    #[inline(always)]
    fn default_value(context: &mut ReadContext) -> Result<RcWeak<T>, Error> {
        reserve_weak_cell::<std::rc::Weak<T>>(context)?;
        Ok(RcWeak::new())
    }

    #[inline(always)]
    fn write_type_info(context: &mut WriteContext) -> Result<(), Error> {
        C::write_type_info(context)
    }

    #[inline(always)]
    fn read_type_info(context: &mut ReadContext) -> Result<(), Error> {
        C::read_type_info(context)
    }

    #[inline(always)]
    fn read_type_info_value(
        context: &mut ReadContext,
    ) -> Result<super::codec::CodecReadType, Error> {
        C::read_type_info_value(context)
    }

    #[inline(always)]
    fn static_type_id() -> TypeId {
        C::static_type_id()
    }

    #[inline(always)]
    fn is_polymorphic() -> bool {
        C::is_polymorphic()
    }

    #[inline(always)]
    fn is_shared_ref() -> bool {
        true
    }

    #[inline(always)]
    fn is_wrapper_type() -> bool {
        true
    }

    #[inline(always)]
    fn dynamic_type_id(value: &RcWeak<T>) -> Result<Option<std::any::TypeId>, Error> {
        match value.upgrade() {
            Some(value) => C::dynamic_type_id(&value),
            None => Ok(None),
        }
    }

    #[inline(always)]
    fn dynamic_type_is_direct() -> bool {
        false
    }
}

#[inline(always)]
fn write_arc_weak<T: Send + Sync + 'static, C: Codec<T>>(
    value: &ArcWeak<T>,
    context: &mut WriteContext,
    ref_mode: RefMode,
    write_type_info: bool,
    has_generics: bool,
) -> Result<(), Error> {
    if !context.is_track_ref() {
        return Err(arc_weak_tracking_error());
    }
    if ref_mode != RefMode::Tracking {
        return Err(weak_write_mode_error("ArcWeak"));
    }
    let Some(value) = value.upgrade() else {
        context.writer.write_i8(RefFlag::Null as i8);
        return Ok(());
    };
    if context
        .ref_writer
        .try_write_arc_ref(&mut context.writer, &value)
    {
        return Ok(());
    }
    C::write_with_mode(
        &value,
        context,
        RefMode::None,
        write_type_info,
        has_generics,
    )
}

#[inline(always)]
fn write_arc_weak_with_type_info<T: Send + Sync + 'static, C: Codec<T>>(
    value: &ArcWeak<T>,
    context: &mut WriteContext,
    ref_mode: RefMode,
    type_info: &Rc<TypeInfo>,
    has_generics: bool,
) -> Result<(), Error> {
    if !context.is_track_ref() {
        return Err(arc_weak_tracking_error());
    }
    if ref_mode != RefMode::Tracking {
        return Err(weak_write_mode_error("ArcWeak"));
    }
    let Some(value) = value.upgrade() else {
        context.writer.write_i8(RefFlag::Null as i8);
        return Ok(());
    };
    if context
        .ref_writer
        .try_write_arc_ref(&mut context.writer, &value)
    {
        return Ok(());
    }
    C::write_with_type_info(&value, context, RefMode::None, type_info, has_generics)
}

#[inline(always)]
fn read_arc_inner<T: Send + Sync + 'static, C: Codec<T>>(
    context: &mut ReadContext,
    read_type_info: bool,
    type_info: Option<&Rc<TypeInfo>>,
    remote_field_type: Option<&FieldType>,
) -> Result<T, Error> {
    reserve_strong::<T>(context)?;
    if let Some(type_info) = type_info {
        return C::read_with_type_info(context, RefMode::None, type_info);
    }
    if let Some(remote_field_type) = remote_field_type {
        // The weak envelope owns only reference framing. A compatible
        // metadata-bearing child still owns its inline TypeInfo before its
        // body, while declared carrier children consume the remote schema
        // directly.
        if codec_read_type_info::<T, C>(context, remote_field_type) {
            return C::read_with_mode(context, RefMode::None, true);
        }
        return C::read_data_with_type(context, remote_field_type);
    }
    // The weak envelope has consumed its ref flag; the child still owns any
    // inline dynamic metadata before its body.
    C::read_with_mode(context, RefMode::None, read_type_info)
}

#[inline(always)]
fn read_arc_weak<T: Send + Sync + 'static, C: Codec<T>>(
    context: &mut ReadContext,
    ref_mode: RefMode,
    read_type_info: bool,
    type_info: Option<&Rc<TypeInfo>>,
    remote_field_type: Option<&FieldType>,
) -> Result<ArcWeak<T>, Error> {
    if ref_mode != RefMode::Tracking {
        return Err(weak_read_mode_error("ArcWeak"));
    }
    match context.ref_reader.read_ref_flag(&mut context.reader)? {
        RefFlag::Null => {
            reserve_weak_cell::<std::sync::Weak<T>>(context)?;
            Ok(ArcWeak::new())
        }
        RefFlag::RefValue => {
            context.inc_depth()?;
            let result =
                read_arc_inner::<T, C>(context, read_type_info, type_info, remote_field_type);
            context.dec_depth();
            let value = result?;
            let strong = Arc::new(value);
            let ref_id = context.ref_reader.store_arc_ref(strong);
            let strong = context
                .ref_reader
                .get_arc_ref::<T>(ref_id)
                .ok_or_else(|| weak_ref_missing_after_insert("Arc", ref_id))?;
            reserve_weak_cell::<std::sync::Weak<T>>(context)?;
            Ok(ArcWeak::from(&strong))
        }
        RefFlag::Ref => {
            let ref_id = context.ref_reader.read_ref_id(&mut context.reader)?;
            reserve_weak_cell::<std::sync::Weak<T>>(context)?;
            let weak = ArcWeak::new();
            if let Some(strong) = context.ref_reader.get_arc_ref::<T>(ref_id) {
                weak.update(Arc::downgrade(&strong));
            } else {
                let callback_weak = weak.clone();
                context.ref_reader.add_callback(Box::new(move |reader| {
                    if let Some(strong) = reader.get_arc_ref::<T>(ref_id) {
                        callback_weak.update(Arc::downgrade(&strong));
                    }
                }));
            }
            Ok(weak)
        }
        RefFlag::NotNullValue => Err(weak_untracked_value("ArcWeak")),
    }
}

impl<T, C, const NULLABLE: bool, const TRACK_REF: bool> Codec<ArcWeak<T>>
    for ArcWeakCodec<T, C, NULLABLE, TRACK_REF>
where
    T: Send + Sync + 'static,
    C: Codec<T>,
{
    #[inline(always)]
    fn field_type(type_resolver: &TypeResolver) -> Result<FieldType, Error> {
        let mut field_type = C::field_type(type_resolver)?;
        field_type.nullable = NULLABLE;
        field_type.track_ref = true;
        Ok(field_type)
    }

    #[inline(always)]
    fn reserved_space() -> usize {
        4
    }

    #[inline(always)]
    fn write_field(value: &ArcWeak<T>, context: &mut WriteContext) -> Result<(), Error> {
        write_arc_weak::<T, C>(
            value,
            context,
            RefMode::Tracking,
            super::codec::codec_write_type_info::<T, C>(context),
            true,
        )
    }

    #[inline(always)]
    fn read_field(context: &mut ReadContext) -> Result<ArcWeak<T>, Error> {
        read_arc_weak::<T, C>(
            context,
            RefMode::Tracking,
            codec_read_type_info_static::<T, C>(context),
            None,
            None,
        )
    }

    #[cold]
    #[inline(never)]
    fn write_data(_: &ArcWeak<T>, _: &mut WriteContext) -> Result<(), Error> {
        Err(Error::not_allowed(
            "ArcWeak must be written through its reference-tracking envelope",
        ))
    }

    #[cold]
    #[inline(never)]
    fn read_data(_: &mut ReadContext) -> Result<ArcWeak<T>, Error> {
        Err(Error::not_allowed(
            "ArcWeak must be read through its reference-tracking envelope",
        ))
    }

    #[inline(always)]
    fn read_field_with_type(
        context: &mut ReadContext,
        remote_field_type: &FieldType,
    ) -> Result<ArcWeak<T>, Error> {
        read_arc_weak::<T, C>(
            context,
            RefMode::Tracking,
            false,
            None,
            Some(remote_field_type),
        )
    }

    #[inline(always)]
    fn write_with_mode(
        value: &ArcWeak<T>,
        context: &mut WriteContext,
        ref_mode: RefMode,
        write_type_info: bool,
        has_generics: bool,
    ) -> Result<(), Error> {
        write_arc_weak::<T, C>(value, context, ref_mode, write_type_info, has_generics)
    }

    #[inline(always)]
    fn write_type_info_value(
        context: &mut WriteContext,
        target_type_id: std::any::TypeId,
    ) -> Result<Rc<TypeInfo>, Error> {
        C::write_type_info_value(context, target_type_id)
    }

    #[inline(always)]
    fn write_with_type_info(
        value: &ArcWeak<T>,
        context: &mut WriteContext,
        ref_mode: RefMode,
        type_info: &Rc<TypeInfo>,
        has_generics: bool,
    ) -> Result<(), Error> {
        write_arc_weak_with_type_info::<T, C>(value, context, ref_mode, type_info, has_generics)
    }

    #[inline(always)]
    fn read_with_mode(
        context: &mut ReadContext,
        ref_mode: RefMode,
        read_type_info: bool,
    ) -> Result<ArcWeak<T>, Error> {
        read_arc_weak::<T, C>(context, ref_mode, read_type_info, None, None)
    }

    #[inline(always)]
    fn read_with_type_info(
        context: &mut ReadContext,
        ref_mode: RefMode,
        type_info: &Rc<TypeInfo>,
    ) -> Result<ArcWeak<T>, Error> {
        read_arc_weak::<T, C>(context, ref_mode, false, Some(type_info), None)
    }

    #[inline(always)]
    fn default_value(context: &mut ReadContext) -> Result<ArcWeak<T>, Error> {
        reserve_weak_cell::<std::sync::Weak<T>>(context)?;
        Ok(ArcWeak::new())
    }

    #[inline(always)]
    fn write_type_info(context: &mut WriteContext) -> Result<(), Error> {
        C::write_type_info(context)
    }

    #[inline(always)]
    fn read_type_info(context: &mut ReadContext) -> Result<(), Error> {
        C::read_type_info(context)
    }

    #[inline(always)]
    fn read_type_info_value(
        context: &mut ReadContext,
    ) -> Result<super::codec::CodecReadType, Error> {
        C::read_type_info_value(context)
    }

    #[inline(always)]
    fn static_type_id() -> TypeId {
        C::static_type_id()
    }

    #[inline(always)]
    fn is_polymorphic() -> bool {
        C::is_polymorphic()
    }

    #[inline(always)]
    fn is_shared_ref() -> bool {
        true
    }

    #[inline(always)]
    fn is_wrapper_type() -> bool {
        true
    }

    #[inline(always)]
    fn dynamic_type_id(value: &ArcWeak<T>) -> Result<Option<std::any::TypeId>, Error> {
        match value.upgrade() {
            Some(value) => C::dynamic_type_id(&value),
            None => Ok(None),
        }
    }

    #[inline(always)]
    fn dynamic_type_is_direct() -> bool {
        false
    }
}

impl_single_carrier_serializer!(RcWeakSerializer, RcWeak, RcWeakCodec, wrapper = true);

impl_single_carrier_serializer!(
    ArcWeakSerializer,
    ArcWeak,
    ArcWeakCodec,
    wrapper = true,
    bounds = [Send + Sync]
);
