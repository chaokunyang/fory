use crate::error::Error;
use crate::fory::Fory;
use crate::resolver::context::{ReadContext, WriteContext};
use crate::serializer::Serializer;

impl Default for Box<dyn Serializer> {
    fn default() -> Self {
        panic!("Box<dyn Serializer> cannot be default-constructed")
    }
}

impl Serializer for Box<dyn Serializer> {
    fn fory_write(&self, context: &mut WriteContext, is_field: bool)
    where
        Self: Sized,
    {
        use crate::types::{Mode, RefFlag};

        context.writer.write_i8(RefFlag::NotNullValue as i8);

        if *context.get_fory().get_mode() == Mode::Compatible && !is_field {
            let fory_type_id = (**self).fory_type_id_dyn(context.get_fory());
            context.writer.write_varuint32(fory_type_id);
        }
        (**self).fory_write_data(context, is_field);
    }

    fn fory_write_data(&self, _context: &mut WriteContext, _is_field: bool) {
        panic!("fory_write_data should not be called directly on Box<dyn Serializer>");
    }

    fn fory_type_id_dyn(&self, fory: &Fory) -> u32 {
        (**self).fory_type_id_dyn(fory)
    }

    fn fory_read(context: &mut ReadContext, is_field: bool) -> Result<Self, Error>
    where
        Self: Sized + Default,
    {
        use crate::types::{Mode, RefFlag};

        let ref_flag = context.reader.read_i8();
        if ref_flag != RefFlag::NotNullValue as i8 {
            return Err(Error::Other(anyhow::anyhow!("Expected NotNullValue ref flag, got {}", ref_flag)));
        }

        if *context.get_fory().get_mode() != Mode::Compatible {
            return Err(Error::Other(anyhow::anyhow!(
                "Box<dyn Serializer> deserialization is only supported in Compatible mode"
            )));
        }

        let fory_type_id = if !is_field {
            context.reader.read_varuint32()
        } else {
            return Err(Error::Other(anyhow::anyhow!("Box<dyn Serializer> cannot be used as a field")));
        };

        let (deserializer_fn, to_serializer_fn) = {
            let type_resolver = context.get_fory().get_type_resolver();
            let harness = type_resolver
                .get_harness(fory_type_id)
                .ok_or_else(|| Error::Other(anyhow::anyhow!("Type ID {} not registered", fory_type_id)))?;
            (harness.get_deserializer(), harness.get_to_serializer())
        };

        let boxed_any = deserializer_fn(context, is_field, true)?;
        let trait_object = to_serializer_fn(boxed_any)?;

        Ok(trait_object)
    }

    fn fory_read_data(_context: &mut ReadContext, _is_field: bool) -> Result<Self, Error>
    where
        Self: Sized + Default,
    {
        panic!("fory_read_data should not be called directly on Box<dyn Serializer>");
    }
}

