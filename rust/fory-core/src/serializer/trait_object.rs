use crate::error::Error;
use crate::resolver::context::{ReadContext, WriteContext};
use crate::serializer::Serializer;

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

        let (deserializer_fn, to_serializer_fn) = {
            let type_resolver = context.get_fory().get_type_resolver();
            let harness = type_resolver
                .get_harness(fory_type_id)
                .ok_or_else(|| Error::Other(anyhow::anyhow!("Type ID {} not registered", fory_type_id)))?;
            (harness.get_deserializer(), harness.get_to_serializer())
        };

        let boxed_any = deserializer_fn(context, is_field, false)?;
        let trait_object = to_serializer_fn(boxed_any)?;

        Ok(trait_object)
    }
}