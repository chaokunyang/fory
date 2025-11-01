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
use crate::meta::FieldType;
use crate::resolver::context::ReadContext;
use crate::serializer::collection::{DECL_ELEMENT_TYPE, HAS_NULL, IS_SAME_TYPE};
use crate::serializer::util;
use crate::serializer::Serializer;
use crate::types;
use crate::types::{is_user_type, RefFlag, TypeId, BASIC_TYPES, CONTAINER_TYPES};
use chrono::{NaiveDate, NaiveDateTime};
use std::rc::Rc;

macro_rules! basic_type_deserialize {
    ($tid:expr, $context:expr; $(($ty:ty, $id:ident)),+ $(,)?) => {
        $(
            if $tid == TypeId::$id {
                <$ty as Serializer>::fory_read_data($context)?;
                return Ok(());
            }
        )+else {
            unreachable!()
        }
    };
}

#[allow(unreachable_code)]
pub fn skip_field_value(
    context: &mut ReadContext,
    field_type: &FieldType,
    read_ref_flag: bool,
) -> Result<(), Error> {
    skip_value(context, field_type, read_ref_flag, true, &None)
}

const UNKNOWN_FIELD_TYPPE: FieldType = FieldType {
    type_id: types::UNKNOWN,
    nullable: true,
    generics: vec![],
};

pub fn skip_any_value(context: &mut ReadContext, read_ref_flag: bool) -> Result<(), Error> {
    // Handle ref flag first if needed
    if read_ref_flag {
        let ref_flag = context.reader.read_i8()?;
        if ref_flag == (RefFlag::Null as i8) {
            return Ok(());
        }
    }

    // Now read the type ID
    let mut type_id = context.reader.peek_u8()? as u32;
    if !is_user_type(type_id) {
        type_id = context.reader.read_varuint32()?;
    }
    let field_type = match type_id {
        types::LIST | types::SET => FieldType {
            type_id,
            nullable: true,
            generics: vec![UNKNOWN_FIELD_TYPPE.clone()],
        },
        types::MAP => FieldType {
            type_id,
            nullable: true,
            generics: vec![UNKNOWN_FIELD_TYPPE.clone(), UNKNOWN_FIELD_TYPPE.clone()],
        },
        _ => FieldType {
            type_id,
            nullable: true,
            generics: vec![],
        },
    };
    // Don't read ref flag again in skip_value since we already handled it
    skip_value(context, &field_type, false, false, &None)
}

fn skip_collection(
    context: &mut ReadContext,
    field_type: &FieldType,
    type_id: TypeId,
) -> Result<(), Error> {
    let length = context.reader.read_varuint32()? as usize;
    if length == 0 {
        return Ok(());
    }
    let header = context.reader.read_u8()?;
    let has_null = (header & HAS_NULL) != 0;
    let is_same_type = (header & IS_SAME_TYPE) != 0;
    let skip_ref_flag = is_same_type && !has_null;
    let is_declared = (header & DECL_ELEMENT_TYPE) != 0;
    let default_elem_type = field_type.generics.first().unwrap();
    let (type_info, elem_field_type);
    let elem_type = if is_same_type && !is_declared {
        let type_info_rc = context.read_any_typeinfo()?;
        elem_field_type = FieldType {
            type_id: type_info_rc.get_type_id(),
            nullable: has_null,
            generics: vec![],
        };
        type_info = Some(type_info_rc);
        &elem_field_type
    } else {
        type_info = None;
        default_elem_type
    };
    context.inc_depth()?;
    for _ in 0..length {
        skip_value(context, elem_type, !skip_ref_flag, false, &type_info)?;
    }
    context.dec_depth();
    Ok(())
}

fn skip_map(context: &mut ReadContext, field_type: &FieldType) -> Result<(), Error> {
    let length = context.reader.read_varuint32()?;
    if length == 0 {
        return Ok(());
    }
    let mut len_counter = 0;
    let default_key_type = field_type.generics.first().unwrap();
    let default_value_type = field_type.generics.get(1).unwrap();
    loop {
        if len_counter == length {
            break;
        }
        let header = context.reader.read_u8()?;
        if header & crate::serializer::map::KEY_NULL != 0
            && header & crate::serializer::map::VALUE_NULL != 0
        {
            len_counter += 1;
            continue;
        }
        if header & crate::serializer::map::KEY_NULL != 0 {
            // Read value type info if not declared
            let value_declared = (header & crate::serializer::map::DECL_VALUE_TYPE) != 0;
            let (value_type_info, value_field_type);
            let value_type = if !value_declared {
                let type_info = context.read_any_typeinfo()?;
                value_field_type = FieldType {
                    type_id: type_info.get_type_id(),
                    nullable: true,
                    generics: vec![],
                };
                value_type_info = Some(type_info);
                &value_field_type
            } else {
                value_type_info = None;
                default_value_type
            };
            context.inc_depth()?;
            skip_value(context, value_type, false, false, &value_type_info)?;
            context.dec_depth();
            len_counter += 1;
            continue;
        }
        if header & crate::serializer::map::VALUE_NULL != 0 {
            // Read key type info if not declared
            let key_declared = (header & crate::serializer::map::DECL_KEY_TYPE) != 0;
            let (key_type_info, key_field_type);
            let key_type = if !key_declared {
                let type_info = context.read_any_typeinfo()?;
                key_field_type = FieldType {
                    type_id: type_info.get_type_id(),
                    nullable: true,
                    generics: vec![],
                };
                key_type_info = Some(type_info);
                &key_field_type
            } else {
                key_type_info = None;
                default_key_type
            };
            context.inc_depth()?;
            skip_value(context, key_type, false, false, &key_type_info)?;
            context.dec_depth();
            len_counter += 1;
            continue;
        }
        // Both key and value are non-null
        let chunk_size = context.reader.read_u8()?;
        let key_declared = (header & crate::serializer::map::DECL_KEY_TYPE) != 0;
        let value_declared = (header & crate::serializer::map::DECL_VALUE_TYPE) != 0;

        // Read key type info if not declared
        let (key_type_info, key_field_type);
        let key_type = if !key_declared {
            let type_info = context.read_any_typeinfo()?;
            key_field_type = FieldType {
                type_id: type_info.get_type_id(),
                nullable: true,
                generics: vec![],
            };
            key_type_info = Some(type_info);
            &key_field_type
        } else {
            key_type_info = None;
            default_key_type
        };

        // Read value type info if not declared
        let (value_type_info, value_field_type);
        let value_type = if !value_declared {
            let type_info = context.read_any_typeinfo()?;
            value_field_type = FieldType {
                type_id: type_info.get_type_id(),
                nullable: true,
                generics: vec![],
            };
            value_type_info = Some(type_info);
            &value_field_type
        } else {
            value_type_info = None;
            default_value_type
        };

        context.inc_depth()?;
        for _ in 0..chunk_size {
            skip_value(context, key_type, false, false, &key_type_info)?;
            skip_value(context, value_type, false, false, &value_type_info)?;
        }
        context.dec_depth();
        len_counter += chunk_size as u32;
    }
    Ok(())
}

fn skip_struct(
    context: &mut ReadContext,
    type_id_num: u32,
    type_info: &Option<Rc<crate::TypeInfo>>,
) -> Result<(), Error> {
    let type_info_value = if type_info.is_none() {
        let remote_type_id = context.reader.read_varuint32()?;
        ensure!(
            type_id_num == remote_type_id,
            Error::type_mismatch(type_id_num, remote_type_id)
        );
        let meta_index = context.reader.read_varuint32()?;
        context.get_type_info_by_index(meta_index as usize)?
    } else {
        type_info.as_ref().unwrap()
    };
    let field_infos = type_info_value.get_type_meta().get_field_infos().to_vec();
    context.inc_depth()?;
    for field_info in field_infos.iter() {
        let read_ref_flag = util::field_requires_ref_flag(
            field_info.field_type.type_id,
            field_info.field_type.nullable,
        );
        skip_value(context, &field_info.field_type, read_ref_flag, true, &None)?;
    }
    context.dec_depth();
    Ok(())
}

fn skip_ext(
    context: &mut ReadContext,
    type_id_num: u32,
    type_info: &Option<Rc<crate::TypeInfo>>,
) -> Result<(), Error> {
    let type_info_value = if type_info.is_none() {
        let remote_type_id = context.reader.read_varuint32()?;
        ensure!(
            type_id_num == remote_type_id,
            Error::type_mismatch(type_id_num, remote_type_id)
        );
        let meta_index = context.reader.read_varuint32()?;
        context.get_type_info_by_index(meta_index as usize)?
    } else {
        type_info.as_ref().unwrap()
    };
    let type_resolver = context.get_type_resolver();
    let type_meta = type_info_value.get_type_meta();
    type_resolver
        .get_ext_name_harness(type_meta.get_namespace(), type_meta.get_type_name())?
        .get_read_data_fn()(context)?;
    Ok(())
}

fn skip_user_struct(context: &mut ReadContext, type_id_num: u32) -> Result<(), Error> {
    let remote_type_id = context.reader.read_varuint32()?;
    let meta_index = context.reader.read_varuint32()?;
    let type_info = context.get_type_info_by_index(meta_index as usize)?;
    let type_meta = type_info.get_type_meta();
    ensure!(
        type_meta.get_type_id() == remote_type_id,
        Error::type_mismatch(type_meta.get_type_id(), remote_type_id)
    );
    let field_infos = type_meta.get_field_infos().to_vec();
    context.inc_depth()?;
    for field_info in field_infos.iter() {
        let read_ref_flag = util::field_requires_ref_flag(
            field_info.field_type.type_id,
            field_info.field_type.nullable,
        );
        skip_value(context, &field_info.field_type, read_ref_flag, true, &None)?;
    }
    context.dec_depth();
    Ok(())
}

fn skip_user_ext(context: &mut ReadContext, type_id_num: u32) -> Result<(), Error> {
    let remote_type_id = context.reader.read_varuint32()?;
    ensure!(
        type_id_num == remote_type_id,
        Error::type_mismatch(type_id_num, remote_type_id)
    );
    context.inc_depth()?;
    let type_resolver = context.get_type_resolver();
    type_resolver
        .get_ext_harness(type_id_num)?
        .get_read_data_fn()(context)?;
    context.dec_depth();
    Ok(())
}

// call when is_field && is_compatible_mode
#[allow(unreachable_code)]
fn skip_value(
    context: &mut ReadContext,
    field_type: &FieldType,
    read_ref_flag: bool,
    _is_field: bool,
    type_info: &Option<Rc<crate::TypeInfo>>,
) -> Result<(), Error> {
    if read_ref_flag {
        let ref_flag = context.reader.read_i8()?;
        if ref_flag == (RefFlag::Null as i8) {
            return Ok(());
        }
    }
    let type_id_num = field_type.type_id;
    match TypeId::try_from(type_id_num as i16) {
        Ok(type_id) => {
            if BASIC_TYPES.contains(&type_id) {
                basic_type_deserialize!(type_id, context;
                    (bool, BOOL),
                    (i8, INT8),
                    (i16, INT16),
                    (i32, INT32),
                    (i64, INT64),
                    (f32, FLOAT32),
                    (f64, FLOAT64),
                    (String, STRING),
                    (NaiveDate, LOCAL_DATE),
                    (NaiveDateTime, TIMESTAMP),
                    (Vec<u8>, BINARY),
                    (Vec<bool> , BOOL_ARRAY),
                    (Vec<i8> , INT8_ARRAY),
                    (Vec<i16> , INT16_ARRAY),
                    (Vec<i32> , INT32_ARRAY),
                    (Vec<i64> , INT64_ARRAY),
                    (Vec<f32>, FLOAT32_ARRAY),
                    (Vec<f64> , FLOAT64_ARRAY),
                    (u8, U8),
                    (u16, U16),
                    (u32, U32),
                    (u64, U64),
                    (Vec<u16> , U16_ARRAY),
                    (Vec<u32> , U32_ARRAY),
                    (Vec<u64> , U64_ARRAY),
                );
            } else if CONTAINER_TYPES.contains(&type_id) {
                if type_id == TypeId::LIST || type_id == TypeId::SET {
                    skip_collection(context, field_type, type_id)
                } else if type_id == TypeId::MAP {
                    skip_map(context, field_type)
                } else {
                    Ok(())
                }
            } else if type_id == TypeId::NAMED_ENUM {
                let _ordinal = context.reader.read_varuint32()?;
                Ok(())
            } else if type_id == TypeId::NAMED_COMPATIBLE_STRUCT {
                skip_struct(context, type_id_num, type_info)
            } else if type_id == TypeId::NAMED_EXT {
                skip_ext(context, type_id_num, type_info)
            } else if type_id == TypeId::UNKNOWN {
                skip_any_value(context, false)
            } else {
                unreachable!("unimplemented type: {:?}", type_id);
            }
        }
        Err(_) => {
            let internal_id = type_id_num & 0xff;
            const COMPATIBLE_STRUCT_ID: u32 = TypeId::COMPATIBLE_STRUCT as u32;
            const EXT_ID: u32 = TypeId::EXT as u32;
            const ENUM_ID: u32 = TypeId::ENUM as u32;
            if internal_id == COMPATIBLE_STRUCT_ID {
                skip_user_struct(context, type_id_num)
            } else if internal_id == ENUM_ID {
                let _ordinal = context.reader.read_varuint32()?;
                let _ordinalx = _ordinal;
                println!("skip enum ordinal: {}", _ordinalx);
                Ok(())
            } else if internal_id == EXT_ID {
                skip_user_ext(context, type_id_num)
            } else {
                Err(Error::type_error(format!(
                    "Unknown type id: {}",
                    type_id_num
                )))
            }
        }
    }
}
