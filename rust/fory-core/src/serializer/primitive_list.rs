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

use crate::context::{ReadContext, WriteContext};
use crate::error::Error;
use crate::serializer::codec::Codec;
use crate::type_id::TypeId;
use std::mem::MaybeUninit;

#[inline(always)]
pub(super) fn canonical_target<T: 'static>(array_type_id: TypeId) -> bool {
    let target = std::any::TypeId::of::<T>();
    match array_type_id {
        TypeId::BOOL_ARRAY => target == std::any::TypeId::of::<bool>(),
        TypeId::INT8_ARRAY => target == std::any::TypeId::of::<i8>(),
        TypeId::INT16_ARRAY => target == std::any::TypeId::of::<i16>(),
        TypeId::INT32_ARRAY => target == std::any::TypeId::of::<i32>(),
        TypeId::INT64_ARRAY => target == std::any::TypeId::of::<i64>(),
        TypeId::FLOAT16_ARRAY => target == std::any::TypeId::of::<crate::types::float16::float16>(),
        TypeId::BFLOAT16_ARRAY => {
            target == std::any::TypeId::of::<crate::types::bfloat16::bfloat16>()
        }
        TypeId::FLOAT32_ARRAY => target == std::any::TypeId::of::<f32>(),
        TypeId::FLOAT64_ARRAY => target == std::any::TypeId::of::<f64>(),
        TypeId::BINARY | TypeId::UINT8_ARRAY => target == std::any::TypeId::of::<u8>(),
        TypeId::UINT16_ARRAY => target == std::any::TypeId::of::<u16>(),
        TypeId::UINT32_ARRAY => target == std::any::TypeId::of::<u32>(),
        TypeId::UINT64_ARRAY => target == std::any::TypeId::of::<u64>(),
        TypeId::U128_ARRAY => target == std::any::TypeId::of::<u128>(),
        TypeId::INT128_ARRAY => target == std::any::TypeId::of::<i128>(),
        TypeId::USIZE_ARRAY => target == std::any::TypeId::of::<usize>(),
        TypeId::ISIZE_ARRAY => target == std::any::TypeId::of::<isize>(),
        _ => false,
    }
}

#[cold]
#[inline(never)]
fn invalid_primitive_target<T: 'static>(array_type_id: TypeId) -> Error {
    Error::type_error(format!(
        "primitive array kind {:?} does not match Rust target {}",
        array_type_id,
        std::any::type_name::<T>(),
    ))
}

#[cold]
#[inline(never)]
fn unsupported_array_kind(message: &'static str) -> Error {
    Error::not_allowed(message)
}

#[cold]
#[inline(never)]
fn invalid_primitive_len() -> Error {
    Error::invalid_data("Invalid data length")
}

#[cold]
#[inline(never)]
fn invalid_bool_value() -> Error {
    Error::invalid_data("Invalid bool array value")
}

#[cold]
#[inline(never)]
fn primitive_len_overflow() -> Error {
    Error::invalid_data("primitive array byte length overflows")
}

#[cold]
#[inline(never)]
fn primitive_array_len_mismatch(expected: usize, actual: usize) -> Error {
    Error::invalid_data(format!(
        "Array length mismatch: expected {expected} bytes, got {actual}"
    ))
}

#[cold]
#[inline(never)]
fn primitive_list_mismatch() -> Error {
    Error::type_error("a primitive array cannot be read as an object LIST")
}

#[cold]
#[inline(never)]
fn primitive_type_mismatch(expected: u32, actual: u32) -> Error {
    Error::type_mismatch(expected, actual)
}

#[inline(always)]
fn validate_target<T: 'static>(array_type_id: TypeId) -> Result<(), Error> {
    if canonical_target::<T>(array_type_id) {
        Ok(())
    } else {
        Err(invalid_primitive_target::<T>(array_type_id))
    }
}

#[inline(always)]
fn check_xlang_kind(context: &WriteContext, array_type_id: TypeId) -> Result<(), Error> {
    if !context.is_xlang() {
        return Ok(());
    }
    let message = match array_type_id {
        TypeId::U128_ARRAY => Some("u128 is not supported in cross-language mode"),
        TypeId::INT128_ARRAY => Some("i128 is not supported in cross-language mode"),
        TypeId::USIZE_ARRAY => Some("usize is not supported in cross-language mode"),
        TypeId::ISIZE_ARRAY => Some("isize is not supported in cross-language mode"),
        _ => None,
    };
    match message {
        Some(message) => Err(unsupported_array_kind(message)),
        None => Ok(()),
    }
}

#[inline(always)]
pub(super) fn write_data<T, C>(
    values: &[T],
    context: &mut WriteContext,
    array_type_id: TypeId,
) -> Result<(), Error>
where
    T: 'static,
    C: Codec<T>,
{
    validate_target::<T>(array_type_id)?;
    check_xlang_kind(context, array_type_id)?;
    write_data_body::<T, C>(values, context)
}

fn write_data_body<T, C>(values: &[T], context: &mut WriteContext) -> Result<(), Error>
where
    T: 'static,
    C: Codec<T>,
{
    #[cfg(target_endian = "little")]
    let _ = std::marker::PhantomData::<C>;
    let len_bytes = std::mem::size_of_val(values);
    context.writer.write_var_u32(len_bytes as u32);
    if values.is_empty() {
        return Ok(());
    }
    #[cfg(target_endian = "little")]
    unsafe {
        // The exact Rust target/kind check above guarantees that these are
        // canonical scalar bytes and contain no references or invalid padding.
        context
            .writer
            .write_bytes_from_ptr(values.as_ptr().cast::<u8>(), len_bytes);
    }
    #[cfg(target_endian = "big")]
    for value in values {
        C::write_data(value, context)?;
    }
    Ok(())
}

#[inline(always)]
pub(super) fn read_vec<T, C>(
    context: &mut ReadContext,
    array_type_id: TypeId,
) -> Result<Vec<T>, Error>
where
    T: 'static,
    C: Codec<T>,
{
    validate_target::<T>(array_type_id)?;
    if array_type_id == TypeId::BOOL_ARRAY {
        return read_bool_vec::<T>(context);
    }
    read_raw_vec::<T, C>(context)
}

#[inline(always)]
fn valid_bool_bytes(bytes: &[u8]) -> bool {
    const MASK64: u64 = 0xfefefefefefefefe;
    const MASK32: u32 = 0xfefefefe;
    const MASK16: u16 = 0xfefe;

    unsafe {
        if bytes.len() == 4 {
            return std::ptr::read_unaligned(bytes.as_ptr().cast::<u32>()) & MASK32 == 0;
        }
        if bytes.len() == 8 {
            return std::ptr::read_unaligned(bytes.as_ptr().cast::<u64>()) & MASK64 == 0;
        }
    }

    let mut offset = 0;
    unsafe {
        while bytes.len() - offset >= 8 {
            if std::ptr::read_unaligned(bytes.as_ptr().add(offset).cast::<u64>()) & MASK64 != 0 {
                return false;
            }
            offset += 8;
        }
        if bytes.len() - offset >= 4 {
            if std::ptr::read_unaligned(bytes.as_ptr().add(offset).cast::<u32>()) & MASK32 != 0 {
                return false;
            }
            offset += 4;
        }
        if bytes.len() - offset >= 2 {
            if std::ptr::read_unaligned(bytes.as_ptr().add(offset).cast::<u16>()) & MASK16 != 0 {
                return false;
            }
            offset += 2;
        }
        offset == bytes.len() || bytes[offset] <= 1
    }
}

fn read_bool_vec<T>(context: &mut ReadContext) -> Result<Vec<T>, Error>
where
    T: 'static,
{
    let size_bytes = context.reader.read_var_u32()? as usize;
    let element_size = std::mem::size_of::<T>();
    if size_bytes % element_size != 0 {
        return Err(invalid_primitive_len());
    }
    context.reader.check_bound(size_bytes)?;
    let len = size_bytes / element_size;
    let mut values: Vec<T> = Vec::with_capacity(len);
    let bytes = context.reader.read_bytes(size_bytes)?;
    if !valid_bool_bytes(bytes) {
        return Err(invalid_bool_value());
    }
    unsafe {
        // Exact target validation proves T is bool. Validation above proves
        // every copied byte is a valid Rust bool representation.
        std::ptr::copy_nonoverlapping(bytes.as_ptr(), values.as_mut_ptr().cast::<u8>(), size_bytes);
        values.set_len(len);
    }
    Ok(values)
}

fn read_raw_vec<T, C>(context: &mut ReadContext) -> Result<Vec<T>, Error>
where
    T: 'static,
    C: Codec<T>,
{
    let size_bytes = context.reader.read_var_u32()? as usize;
    let element_size = std::mem::size_of::<T>();
    if size_bytes % element_size != 0 {
        return Err(invalid_primitive_len());
    }
    context.reader.check_bound(size_bytes)?;
    let len = size_bytes / element_size;
    let mut values: Vec<T> = Vec::with_capacity(len);
    #[cfg(target_endian = "little")]
    unsafe {
        // Readable bytes were proven before allocation and the exact canonical
        // non-bool scalar check above makes every copied bit pattern valid for T.
        let bytes = context.reader.read_bytes(size_bytes)?;
        std::ptr::copy_nonoverlapping(bytes.as_ptr(), values.as_mut_ptr().cast::<u8>(), size_bytes);
        values.set_len(len);
    }
    #[cfg(target_endian = "big")]
    for _ in 0..len {
        values.push(C::read_data(context)?);
    }
    Ok(values)
}

#[inline(always)]
pub(super) fn read_array<T, C, const N: usize>(
    context: &mut ReadContext,
    array_type_id: TypeId,
) -> Result<[T; N], Error>
where
    T: 'static,
    C: Codec<T>,
{
    validate_target::<T>(array_type_id)?;
    if array_type_id == TypeId::BOOL_ARRAY {
        return read_bool_array::<T, N>(context);
    }
    read_raw_array::<T, C, N>(context)
}

fn read_bool_array<T, const N: usize>(context: &mut ReadContext) -> Result<[T; N], Error>
where
    T: 'static,
{
    let size_bytes = context.reader.read_var_u32()? as usize;
    let element_size = std::mem::size_of::<T>();
    let expected_bytes = N
        .checked_mul(element_size)
        .ok_or_else(primitive_len_overflow)?;
    if size_bytes != expected_bytes {
        return Err(primitive_array_len_mismatch(expected_bytes, size_bytes));
    }
    context.reader.check_bound(size_bytes)?;
    let bytes = context.reader.read_bytes(size_bytes)?;
    if !valid_bool_bytes(bytes) {
        return Err(invalid_bool_value());
    }
    unsafe {
        // Exact target validation proves T is bool. Validation above proves
        // every copied byte is a valid Rust bool representation.
        let mut values = MaybeUninit::<[T; N]>::uninit();
        std::ptr::copy_nonoverlapping(bytes.as_ptr(), values.as_mut_ptr().cast::<u8>(), size_bytes);
        Ok(values.assume_init())
    }
}

fn read_raw_array<T, C, const N: usize>(context: &mut ReadContext) -> Result<[T; N], Error>
where
    T: 'static,
    C: Codec<T>,
{
    let size_bytes = context.reader.read_var_u32()? as usize;
    let element_size = std::mem::size_of::<T>();
    let expected_bytes = N
        .checked_mul(element_size)
        .ok_or_else(primitive_len_overflow)?;
    if size_bytes != expected_bytes {
        return Err(primitive_array_len_mismatch(expected_bytes, size_bytes));
    }
    context.reader.check_bound(size_bytes)?;
    #[cfg(target_endian = "little")]
    unsafe {
        let mut values = MaybeUninit::<[T; N]>::uninit();
        let bytes = context.reader.read_bytes(size_bytes)?;
        // The exact canonical non-bool scalar check makes every copied bit
        // pattern valid for T, and readable bytes were proven above.
        std::ptr::copy_nonoverlapping(bytes.as_ptr(), values.as_mut_ptr().cast::<u8>(), size_bytes);
        Ok(values.assume_init())
    }
    #[cfg(target_endian = "big")]
    {
        super::array::try_init_array(|| C::read_data(context))
    }
}

#[inline(always)]
pub(super) fn write_type_info(
    context: &mut WriteContext,
    array_type_id: TypeId,
) -> Result<(), Error> {
    context.writer.write_u8(array_type_id as u8);
    Ok(())
}

#[inline(always)]
pub(super) fn read_type_info(
    context: &mut ReadContext,
    array_type_id: TypeId,
) -> Result<(), Error> {
    let remote_type_id = context.reader.read_u8()? as u32;
    if remote_type_id == TypeId::LIST as u32 {
        return Err(primitive_list_mismatch());
    }
    if array_type_id as u32 != remote_type_id {
        return Err(primitive_type_mismatch(
            array_type_id as u32,
            remote_type_id,
        ));
    }
    Ok(())
}

#[inline(always)]
pub(super) fn reserved_space<T>() -> usize {
    std::mem::size_of::<T>()
}
