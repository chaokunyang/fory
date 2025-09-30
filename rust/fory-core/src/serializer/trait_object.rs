use crate::error::Error;
use crate::resolver::context::{ReadContext, WriteContext};
use crate::serializer::Serializer;
use anyhow::anyhow;
use std::rc::Rc;
use std::sync::Arc;

impl Default for Box<dyn Serializer> {
    fn default() -> Self {
        panic!("Box<dyn Serializer> cannot be default-constructed")
    }
}

impl Serializer for Box<dyn Serializer> {
    fn fory_write_data(&self, context: &mut WriteContext, is_field: bool) {
        let concrete_type_id = (**self).fory_concrete_type_id();
        let fory_type_id = context
            .get_fory()
            .get_type_resolver()
            .get_type_info(concrete_type_id)
            .get_type_id();

        context.writer.write_varuint32(fory_type_id);
        (**self).fory_write_data(context, is_field);
    }

    fn fory_read_data(context: &mut ReadContext, is_field: bool) -> Result<Self, Error>
    where
        Self: Sized,
    {
        let fory_type_id = context.reader.read_varuint32();
        let type_resolver = context.get_fory().get_type_resolver();

        let harness = type_resolver
            .get_harness(fory_type_id)
            .ok_or_else(|| Error::Other(anyhow!("Type ID {} not registered", fory_type_id)))?;

        let deserializer_fn = harness.get_deserializer();
        let boxed_any = deserializer_fn(context, is_field, false)?;

        let boxed_trait_object = boxed_any
            .downcast::<Box<dyn Serializer>>()
            .map(|b| *b)
            .map_err(|_| Error::Other(anyhow!("Failed to downcast to Box<dyn Serializer>")))?;

        Ok(boxed_trait_object)
    }
}

impl Serializer for Arc<dyn Serializer + Send + Sync> {
    fn fory_write_data(&self, context: &mut WriteContext, is_field: bool) {
        let concrete_type_id = (**self).fory_concrete_type_id();
        let fory_type_id = context
            .get_fory()
            .get_type_resolver()
            .get_type_info(concrete_type_id)
            .get_type_id();

        context.writer.write_varuint32(fory_type_id);
        (**self).fory_write_data(context, is_field);
    }

    fn fory_read_data(context: &mut ReadContext, is_field: bool) -> Result<Self, Error>
    where
        Self: Sized,
    {
        let fory_type_id = context.reader.read_varuint32();
        let type_resolver = context.get_fory().get_type_resolver();

        let harness = type_resolver
            .get_harness(fory_type_id)
            .ok_or_else(|| Error::Other(anyhow!("Type ID {} not registered", fory_type_id)))?;

        let deserializer_fn = harness.get_deserializer();
        let boxed_any = deserializer_fn(context, is_field, false)?;

        let arc_trait_object = boxed_any
            .downcast::<Arc<dyn Serializer + Send + Sync>>()
            .map(|b| *b)
            .map_err(|_| Error::Other(anyhow!("Failed to downcast to Arc<dyn Serializer>")))?;

        Ok(arc_trait_object)
    }
}

impl Serializer for Rc<dyn Serializer> {
    fn fory_write_data(&self, context: &mut WriteContext, is_field: bool) {
        let concrete_type_id = (**self).fory_concrete_type_id();
        let fory_type_id = context
            .get_fory()
            .get_type_resolver()
            .get_type_info(concrete_type_id)
            .get_type_id();

        context.writer.write_varuint32(fory_type_id);
        (**self).fory_write_data(context, is_field);
    }

    fn fory_read_data(context: &mut ReadContext, is_field: bool) -> Result<Self, Error>
    where
        Self: Sized,
    {
        let fory_type_id = context.reader.read_varuint32();
        let type_resolver = context.get_fory().get_type_resolver();

        let harness = type_resolver
            .get_harness(fory_type_id)
            .ok_or_else(|| Error::Other(anyhow!("Type ID {} not registered", fory_type_id)))?;

        let deserializer_fn = harness.get_deserializer();
        let boxed_any = deserializer_fn(context, is_field, false)?;

        let rc_trait_object = boxed_any
            .downcast::<Rc<dyn Serializer>>()
            .map(|b| *b)
            .map_err(|_| Error::Other(anyhow!("Failed to downcast to Rc<dyn Serializer>")))?;

        Ok(rc_trait_object)
    }
}