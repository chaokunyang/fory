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
use crate::float16::float16;
use crate::meta::buffer_rw_string::read_latin1_simd;
use byteorder::{ByteOrder, LittleEndian};
use std::cmp::max;

/// Threshold for using SIMD optimizations in string operations.
/// For buffers smaller than this, direct copy is faster than SIMD setup overhead.
const SIMD_THRESHOLD: usize = 128;

#[inline(always)]
fn slice_bound_error(offset: usize, len: usize, total_len: usize) -> Error {
    Error::buffer_out_of_bound(offset, len, total_len)
}

#[inline(always)]
fn ensure_slice_bound(slice: &[u8], offset: usize, len: usize) -> Result<(), Error> {
    let end = offset
        .checked_add(len)
        .ok_or_else(|| slice_bound_error(offset, len, slice.len()))?;
    if end > slice.len() {
        Err(slice_bound_error(offset, len, slice.len()))
    } else {
        Ok(())
    }
}

#[doc(hidden)]
#[inline(always)]
pub fn write_bool_to_slice(dst: &mut [u8], offset: usize, value: bool) -> usize {
    write_u8_to_slice(dst, offset, if value { 1 } else { 0 })
}

#[doc(hidden)]
#[inline(always)]
pub fn write_i8_to_slice(dst: &mut [u8], offset: usize, value: i8) -> usize {
    write_u8_to_slice(dst, offset, value as u8)
}

#[doc(hidden)]
#[inline(always)]
pub fn write_u8_to_slice(dst: &mut [u8], offset: usize, value: u8) -> usize {
    debug_assert!(offset < dst.len());
    dst[offset] = value;
    1
}

macro_rules! impl_fixed_width_slice_io {
    ($write_name:ident, $read_name:ident, $ty:ty, $bytes:expr) => {
        #[doc(hidden)]
        #[inline(always)]
        pub fn $write_name(dst: &mut [u8], offset: usize, value: $ty) -> usize {
            let end = offset + $bytes;
            debug_assert!(end <= dst.len());
            dst[offset..end].copy_from_slice(&value.to_le_bytes());
            $bytes
        }

        #[doc(hidden)]
        #[inline(always)]
        pub fn $read_name(src: &[u8], offset: &mut usize) -> Result<$ty, Error> {
            let start = *offset;
            ensure_slice_bound(src, start, $bytes)?;
            let end = start + $bytes;
            *offset = end;
            Ok(<$ty>::from_le_bytes(src[start..end].try_into().unwrap()))
        }
    };
}

impl_fixed_width_slice_io!(write_u16_to_slice, read_u16_from_slice, u16, 2);
impl_fixed_width_slice_io!(write_u32_to_slice, read_u32_from_slice, u32, 4);
impl_fixed_width_slice_io!(write_u64_to_slice, read_u64_from_slice, u64, 8);

#[doc(hidden)]
#[inline(always)]
pub fn write_i16_to_slice(dst: &mut [u8], offset: usize, value: i16) -> usize {
    write_u16_to_slice(dst, offset, value as u16)
}

#[doc(hidden)]
#[inline(always)]
pub fn write_i32_to_slice(dst: &mut [u8], offset: usize, value: i32) -> usize {
    write_u32_to_slice(dst, offset, value as u32)
}

#[doc(hidden)]
#[inline(always)]
pub fn write_f16_to_slice(dst: &mut [u8], offset: usize, value: float16) -> usize {
    write_u16_to_slice(dst, offset, value.to_bits())
}

#[doc(hidden)]
#[inline(always)]
pub fn write_f32_to_slice(dst: &mut [u8], offset: usize, value: f32) -> usize {
    write_u32_to_slice(dst, offset, value.to_bits())
}

#[doc(hidden)]
#[inline(always)]
pub fn write_f64_to_slice(dst: &mut [u8], offset: usize, value: f64) -> usize {
    write_u64_to_slice(dst, offset, value.to_bits())
}

#[doc(hidden)]
#[inline(always)]
pub fn write_varuint32_to_slice(dst: &mut [u8], offset: usize, value: u32) -> usize {
    debug_assert!(offset < dst.len());
    if value < 0x80 {
        dst[offset] = value as u8;
        1
    } else if value < 0x4000 {
        dst[offset] = ((value as u8) & 0x7F) | 0x80;
        dst[offset + 1] = (value >> 7) as u8;
        2
    } else if value < 0x200000 {
        dst[offset] = ((value as u8) & 0x7F) | 0x80;
        dst[offset + 1] = (((value >> 7) as u8) & 0x7F) | 0x80;
        dst[offset + 2] = (value >> 14) as u8;
        3
    } else if value < 0x10000000 {
        dst[offset] = ((value as u8) & 0x7F) | 0x80;
        dst[offset + 1] = (((value >> 7) as u8) & 0x7F) | 0x80;
        dst[offset + 2] = (((value >> 14) as u8) & 0x7F) | 0x80;
        dst[offset + 3] = (value >> 21) as u8;
        4
    } else {
        dst[offset] = ((value as u8) & 0x7F) | 0x80;
        dst[offset + 1] = (((value >> 7) as u8) & 0x7F) | 0x80;
        dst[offset + 2] = (((value >> 14) as u8) & 0x7F) | 0x80;
        dst[offset + 3] = (((value >> 21) as u8) & 0x7F) | 0x80;
        dst[offset + 4] = (value >> 28) as u8;
        5
    }
}

#[doc(hidden)]
#[inline(always)]
pub fn write_varint32_to_slice(dst: &mut [u8], offset: usize, value: i32) -> usize {
    let zigzag = ((value as i64) << 1) ^ ((value as i64) >> 31);
    write_varuint32_to_slice(dst, offset, zigzag as u32)
}

#[doc(hidden)]
#[inline(always)]
pub fn write_varuint64_to_slice(dst: &mut [u8], offset: usize, value: u64) -> usize {
    debug_assert!(offset < dst.len());
    if value < 0x80 {
        dst[offset] = value as u8;
        1
    } else if value < 0x4000 {
        dst[offset] = ((value as u8) & 0x7F) | 0x80;
        dst[offset + 1] = (value >> 7) as u8;
        2
    } else if value < 0x200000 {
        dst[offset] = ((value as u8) & 0x7F) | 0x80;
        dst[offset + 1] = (((value >> 7) as u8) & 0x7F) | 0x80;
        dst[offset + 2] = (value >> 14) as u8;
        3
    } else if value < 0x10000000 {
        dst[offset] = ((value as u8) & 0x7F) | 0x80;
        dst[offset + 1] = (((value >> 7) as u8) & 0x7F) | 0x80;
        dst[offset + 2] = (((value >> 14) as u8) & 0x7F) | 0x80;
        dst[offset + 3] = (value >> 21) as u8;
        4
    } else if value < 0x800000000 {
        dst[offset] = ((value as u8) & 0x7F) | 0x80;
        dst[offset + 1] = (((value >> 7) as u8) & 0x7F) | 0x80;
        dst[offset + 2] = (((value >> 14) as u8) & 0x7F) | 0x80;
        dst[offset + 3] = (((value >> 21) as u8) & 0x7F) | 0x80;
        dst[offset + 4] = (value >> 28) as u8;
        5
    } else if value < 0x40000000000 {
        dst[offset] = ((value as u8) & 0x7F) | 0x80;
        dst[offset + 1] = (((value >> 7) as u8) & 0x7F) | 0x80;
        dst[offset + 2] = (((value >> 14) as u8) & 0x7F) | 0x80;
        dst[offset + 3] = (((value >> 21) as u8) & 0x7F) | 0x80;
        dst[offset + 4] = (((value >> 28) as u8) & 0x7F) | 0x80;
        dst[offset + 5] = (value >> 35) as u8;
        6
    } else if value < 0x2000000000000 {
        dst[offset] = ((value as u8) & 0x7F) | 0x80;
        dst[offset + 1] = (((value >> 7) as u8) & 0x7F) | 0x80;
        dst[offset + 2] = (((value >> 14) as u8) & 0x7F) | 0x80;
        dst[offset + 3] = (((value >> 21) as u8) & 0x7F) | 0x80;
        dst[offset + 4] = (((value >> 28) as u8) & 0x7F) | 0x80;
        dst[offset + 5] = (((value >> 35) as u8) & 0x7F) | 0x80;
        dst[offset + 6] = (value >> 42) as u8;
        7
    } else if value < 0x100000000000000 {
        dst[offset] = ((value as u8) & 0x7F) | 0x80;
        dst[offset + 1] = (((value >> 7) as u8) & 0x7F) | 0x80;
        dst[offset + 2] = (((value >> 14) as u8) & 0x7F) | 0x80;
        dst[offset + 3] = (((value >> 21) as u8) & 0x7F) | 0x80;
        dst[offset + 4] = (((value >> 28) as u8) & 0x7F) | 0x80;
        dst[offset + 5] = (((value >> 35) as u8) & 0x7F) | 0x80;
        dst[offset + 6] = (((value >> 42) as u8) & 0x7F) | 0x80;
        dst[offset + 7] = (value >> 49) as u8;
        8
    } else {
        dst[offset] = ((value as u8) & 0x7F) | 0x80;
        dst[offset + 1] = (((value >> 7) as u8) & 0x7F) | 0x80;
        dst[offset + 2] = (((value >> 14) as u8) & 0x7F) | 0x80;
        dst[offset + 3] = (((value >> 21) as u8) & 0x7F) | 0x80;
        dst[offset + 4] = (((value >> 28) as u8) & 0x7F) | 0x80;
        dst[offset + 5] = (((value >> 35) as u8) & 0x7F) | 0x80;
        dst[offset + 6] = (((value >> 42) as u8) & 0x7F) | 0x80;
        dst[offset + 7] = (((value >> 49) as u8) & 0x7F) | 0x80;
        dst[offset + 8] = (value >> 56) as u8;
        9
    }
}

#[doc(hidden)]
#[inline(always)]
pub fn write_varint64_to_slice(dst: &mut [u8], offset: usize, value: i64) -> usize {
    let zigzag = ((value << 1) ^ (value >> 63)) as u64;
    write_varuint64_to_slice(dst, offset, zigzag)
}

#[doc(hidden)]
#[inline(always)]
pub fn write_tagged_u64_to_slice(dst: &mut [u8], offset: usize, value: u64) -> usize {
    if value <= i32::MAX as u64 {
        write_u32_to_slice(dst, offset, (value as u32) << 1)
    } else {
        dst[offset] = 0b1;
        1 + write_u64_to_slice(dst, offset + 1, value)
    }
}

#[doc(hidden)]
#[inline(always)]
pub fn read_bool_from_slice(src: &[u8], offset: &mut usize) -> Result<bool, Error> {
    Ok(read_u8_from_slice(src, offset)? != 0)
}

#[doc(hidden)]
#[inline(always)]
pub fn read_i8_from_slice(src: &[u8], offset: &mut usize) -> Result<i8, Error> {
    Ok(read_u8_from_slice(src, offset)? as i8)
}

#[doc(hidden)]
#[inline(always)]
pub fn read_u8_from_slice(src: &[u8], offset: &mut usize) -> Result<u8, Error> {
    let start = *offset;
    if start >= src.len() {
        return Err(slice_bound_error(start, 1, src.len()));
    }
    let value = unsafe { *src.get_unchecked(start) };
    *offset = start + 1;
    Ok(value)
}

#[doc(hidden)]
#[inline(always)]
pub fn read_i16_from_slice(src: &[u8], offset: &mut usize) -> Result<i16, Error> {
    Ok(read_u16_from_slice(src, offset)? as i16)
}

#[doc(hidden)]
#[inline(always)]
pub fn read_i32_from_slice(src: &[u8], offset: &mut usize) -> Result<i32, Error> {
    Ok(read_u32_from_slice(src, offset)? as i32)
}

#[doc(hidden)]
#[inline(always)]
pub fn read_varuint32_from_slice(src: &[u8], offset: &mut usize) -> Result<u32, Error> {
    let start = *offset;
    if start >= src.len() {
        return Err(slice_bound_error(start, 1, src.len()));
    }
    let remaining = src.len() - start;
    let ptr = unsafe { src.as_ptr().add(start) };
    let b0 = unsafe { *ptr } as u32;
    if b0 < 0x80 {
        *offset = start + 1;
        return Ok(b0);
    }

    if remaining < 2 {
        return Err(slice_bound_error(start, 2, src.len()));
    }
    let b1 = unsafe { *ptr.add(1) } as u32;
    let mut encoded = (b0 & 0x7F) | ((b1 & 0x7F) << 7);
    if b1 < 0x80 {
        *offset = start + 2;
        return Ok(encoded);
    }

    if remaining < 3 {
        return Err(slice_bound_error(start, 3, src.len()));
    }
    let b2 = unsafe { *ptr.add(2) } as u32;
    encoded |= (b2 & 0x7F) << 14;
    if b2 < 0x80 {
        *offset = start + 3;
        return Ok(encoded);
    }

    if remaining < 4 {
        return Err(slice_bound_error(start, 4, src.len()));
    }
    let b3 = unsafe { *ptr.add(3) } as u32;
    encoded |= (b3 & 0x7F) << 21;
    if b3 < 0x80 {
        *offset = start + 4;
        return Ok(encoded);
    }

    if remaining < 5 {
        return Err(slice_bound_error(start, 5, src.len()));
    }
    let b4 = unsafe { *ptr.add(4) } as u32;
    encoded |= b4 << 28;
    *offset = start + 5;
    Ok(encoded)
}

#[doc(hidden)]
#[inline(always)]
pub fn read_varint32_from_slice(src: &[u8], offset: &mut usize) -> Result<i32, Error> {
    let encoded = read_varuint32_from_slice(src, offset)?;
    Ok(((encoded >> 1) as i32) ^ -((encoded & 1) as i32))
}

#[doc(hidden)]
#[inline(always)]
pub fn read_i64_from_slice(src: &[u8], offset: &mut usize) -> Result<i64, Error> {
    Ok(read_u64_from_slice(src, offset)? as i64)
}

#[doc(hidden)]
#[inline(always)]
pub fn read_varuint64_from_slice(src: &[u8], offset: &mut usize) -> Result<u64, Error> {
    let start = *offset;
    if start >= src.len() {
        return Err(slice_bound_error(start, 1, src.len()));
    }
    let remaining = src.len() - start;
    let ptr = unsafe { src.as_ptr().add(start) };
    let b0 = unsafe { *ptr } as u64;
    if b0 < 0x80 {
        *offset = start + 1;
        return Ok(b0);
    }

    if remaining < 2 {
        return Err(slice_bound_error(start, 2, src.len()));
    }
    let b1 = unsafe { *ptr.add(1) } as u64;
    let mut encoded = (b0 & 0x7F) | ((b1 & 0x7F) << 7);
    if b1 < 0x80 {
        *offset = start + 2;
        return Ok(encoded);
    }

    if remaining < 3 {
        return Err(slice_bound_error(start, 3, src.len()));
    }
    let b2 = unsafe { *ptr.add(2) } as u64;
    encoded |= (b2 & 0x7F) << 14;
    if b2 < 0x80 {
        *offset = start + 3;
        return Ok(encoded);
    }

    if remaining < 4 {
        return Err(slice_bound_error(start, 4, src.len()));
    }
    let b3 = unsafe { *ptr.add(3) } as u64;
    encoded |= (b3 & 0x7F) << 21;
    if b3 < 0x80 {
        *offset = start + 4;
        return Ok(encoded);
    }

    if remaining < 5 {
        return Err(slice_bound_error(start, 5, src.len()));
    }
    let b4 = unsafe { *ptr.add(4) } as u64;
    encoded |= (b4 & 0x7F) << 28;
    if b4 < 0x80 {
        *offset = start + 5;
        return Ok(encoded);
    }

    if remaining < 6 {
        return Err(slice_bound_error(start, 6, src.len()));
    }
    let b5 = unsafe { *ptr.add(5) } as u64;
    encoded |= (b5 & 0x7F) << 35;
    if b5 < 0x80 {
        *offset = start + 6;
        return Ok(encoded);
    }

    if remaining < 7 {
        return Err(slice_bound_error(start, 7, src.len()));
    }
    let b6 = unsafe { *ptr.add(6) } as u64;
    encoded |= (b6 & 0x7F) << 42;
    if b6 < 0x80 {
        *offset = start + 7;
        return Ok(encoded);
    }

    if remaining < 8 {
        return Err(slice_bound_error(start, 8, src.len()));
    }
    let b7 = unsafe { *ptr.add(7) } as u64;
    encoded |= (b7 & 0x7F) << 49;
    if b7 < 0x80 {
        *offset = start + 8;
        return Ok(encoded);
    }

    if remaining < 9 {
        return Err(slice_bound_error(start, 9, src.len()));
    }
    let b8 = unsafe { *ptr.add(8) } as u64;
    encoded |= b8 << 56;
    *offset = start + 9;
    Ok(encoded)
}

#[doc(hidden)]
#[inline(always)]
pub fn read_varint64_from_slice(src: &[u8], offset: &mut usize) -> Result<i64, Error> {
    let encoded = read_varuint64_from_slice(src, offset)?;
    Ok(((encoded >> 1) as i64) ^ -((encoded & 1) as i64))
}

#[doc(hidden)]
#[inline(always)]
pub fn read_tagged_u64_from_slice(src: &[u8], offset: &mut usize) -> Result<u64, Error> {
    let start = *offset;
    ensure_slice_bound(src, start, 4)?;
    let head = LittleEndian::read_u32(&src[start..start + 4]);
    if (head & 0b1) != 0b1 {
        *offset = start + 4;
        Ok((head >> 1) as u64)
    } else {
        ensure_slice_bound(src, start, 9)?;
        let value = LittleEndian::read_u64(&src[start + 1..start + 9]);
        *offset = start + 9;
        Ok(value)
    }
}

#[doc(hidden)]
#[inline(always)]
pub fn read_f16_from_slice(src: &[u8], offset: &mut usize) -> Result<float16, Error> {
    Ok(float16::from_bits(read_u16_from_slice(src, offset)?))
}

#[doc(hidden)]
#[inline(always)]
pub fn read_f32_from_slice(src: &[u8], offset: &mut usize) -> Result<f32, Error> {
    Ok(f32::from_bits(read_u32_from_slice(src, offset)?))
}

#[doc(hidden)]
#[inline(always)]
pub fn read_f64_from_slice(src: &[u8], offset: &mut usize) -> Result<f64, Error> {
    Ok(f64::from_bits(read_u64_from_slice(src, offset)?))
}

pub struct Writer<'a> {
    pub(crate) bf: &'a mut Vec<u8>,
}
impl<'a> Writer<'a> {
    // ============ Utility methods ============

    #[inline(always)]
    pub fn from_buffer(bf: &'a mut Vec<u8>) -> Writer<'a> {
        Writer { bf }
    }

    #[inline(always)]
    pub fn dump(&self) -> Vec<u8> {
        self.bf.clone()
    }

    #[inline(always)]
    pub fn reset(&mut self) {
        self.bf.clear();
    }

    #[inline(always)]
    pub fn len(&self) -> usize {
        self.bf.len()
    }

    #[inline(always)]
    pub fn is_empty(&self) -> bool {
        self.bf.is_empty()
    }

    #[inline(always)]
    pub fn reserve(&mut self, additional: usize) {
        if self.bf.capacity() - self.len() < additional {
            self.bf.reserve(max(additional * 2, self.bf.capacity()));
        }
    }

    #[inline(always)]
    pub fn skip(&mut self, len: usize) {
        self.bf.resize(self.bf.len() + len, 0);
    }

    #[inline(always)]
    pub fn set_bytes(&mut self, offset: usize, data: &[u8]) {
        self.bf
            .get_mut(offset..offset + data.len())
            .unwrap()
            .copy_from_slice(data);
    }

    #[inline(always)]
    pub fn write_bytes(&mut self, v: &[u8]) -> usize {
        self.bf.extend_from_slice(v);
        v.len()
    }

    #[doc(hidden)]
    #[inline(always)]
    pub fn write_reserved<F>(&mut self, max_additional: usize, fill: F)
    where
        F: FnOnce(&mut [u8]) -> usize,
    {
        self.reserve(max_additional);
        let base = self.bf.len();
        let spare = self.bf.spare_capacity_mut();
        assert!(
            spare.len() >= max_additional,
            "insufficient spare capacity for reserved write"
        );
        let dst = unsafe {
            std::slice::from_raw_parts_mut(spare.as_mut_ptr().cast::<u8>(), max_additional)
        };
        let written = fill(dst);
        assert!(
            written <= max_additional,
            "reserved write exceeded requested capacity"
        );
        unsafe {
            self.bf.set_len(base + written);
        }
    }

    // ============ BOOL (TypeId = 1) ============

    #[inline(always)]
    pub fn write_bool(&mut self, value: bool) {
        self.bf.push(if value { 1 } else { 0 });
    }

    // ============ INT8 (TypeId = 2) ============

    #[inline(always)]
    pub fn write_i8(&mut self, value: i8) {
        self.bf.push(value as u8);
    }

    // ============ INT16 (TypeId = 3) ============

    #[inline(always)]
    pub fn write_i16(&mut self, value: i16) {
        self.write_u16(value as u16);
    }

    // ============ INT32 (TypeId = 4) ============

    #[inline(always)]
    pub fn write_i32(&mut self, value: i32) {
        self.write_u32(value as u32);
    }

    // ============ VARINT32 (TypeId = 5) ============

    #[inline(always)]
    pub fn write_varint32(&mut self, value: i32) {
        let zigzag = ((value as i64) << 1) ^ ((value as i64) >> 31);
        self._write_var_uint32(zigzag as u32)
    }

    // ============ INT64 (TypeId = 6) ============

    #[inline(always)]
    pub fn write_i64(&mut self, value: i64) {
        self.write_u64(value as u64);
    }

    // ============ VARINT64 (TypeId = 7) ============

    #[inline(always)]
    pub fn write_varint64(&mut self, value: i64) {
        let zigzag = ((value << 1) ^ (value >> 63)) as u64;
        self._write_var_uint64(zigzag);
    }

    // ============ TAGGED_INT64 (TypeId = 8) ============

    /// Write signed long using fory Tagged(Small long as int) encoding.
    /// If value is in [0xc0000000, 0x3fffffff] (i.e., [-1073741824, 1073741823]),
    /// encode as 4 bytes: `((value as i32) << 1)`.
    /// Otherwise write as 9 bytes: `0b1 | little-endian 8 bytes i64`.
    #[inline(always)]
    pub fn write_tagged_i64(&mut self, value: i64) {
        const HALF_MIN_INT_VALUE: i64 = i32::MIN as i64 / 2; // -1073741824
        const HALF_MAX_INT_VALUE: i64 = i32::MAX as i64 / 2; // 1073741823
        if (HALF_MIN_INT_VALUE..=HALF_MAX_INT_VALUE).contains(&value) {
            // Fits in 31 bits (with sign), encode as 4 bytes with bit 0 = 0
            let v = (value as i32) << 1;
            self.write_i32(v);
        } else {
            // Write flag byte (0b1) followed by 8-byte i64
            self.bf.push(0b1);
            self.write_i64(value);
        }
    }

    // ============ UINT8 (TypeId = 9) ============

    #[inline(always)]
    pub fn write_u8(&mut self, value: u8) {
        self.bf.push(value);
    }

    // ============ UINT16 (TypeId = 10) ============

    #[inline(always)]
    pub fn write_u16(&mut self, value: u16) {
        #[cfg(target_endian = "little")]
        {
            let bytes = unsafe { &*(&value as *const u16 as *const [u8; 2]) };
            self.bf.extend_from_slice(bytes);
        }
        #[cfg(target_endian = "big")]
        {
            self.bf.extend_from_slice(&value.to_le_bytes());
        }
    }

    // ============ UINT32 (TypeId = 11) ============

    #[inline(always)]
    pub fn write_u32(&mut self, value: u32) {
        #[cfg(target_endian = "little")]
        {
            let bytes = unsafe { &*(&value as *const u32 as *const [u8; 4]) };
            self.bf.extend_from_slice(bytes);
        }
        #[cfg(target_endian = "big")]
        {
            self.bf.extend_from_slice(&value.to_le_bytes());
        }
    }

    // ============ VAR_UINT32 (TypeId = 12) ============

    #[inline(always)]
    pub fn write_var_uint32(&mut self, value: u32) {
        self._write_var_uint32(value)
    }

    #[inline(always)]
    fn _write_var_uint32(&mut self, value: u32) {
        if value < 0x80 {
            self.bf.push(value as u8);
        } else if value < 0x4000 {
            // 2 bytes
            let u1 = ((value as u8) & 0x7F) | 0x80;
            let u2 = (value >> 7) as u8;
            self.write_u16(((u2 as u16) << 8) | u1 as u16);
        } else if value < 0x200000 {
            // 3 bytes
            let u1 = ((value as u8) & 0x7F) | 0x80;
            let u2 = (((value >> 7) as u8) & 0x7F) | 0x80;
            let u3 = (value >> 14) as u8;
            self.write_u16(((u2 as u16) << 8) | u1 as u16);
            self.bf.push(u3);
        } else if value < 0x10000000 {
            // 4 bytes
            let u1 = ((value as u8) & 0x7F) | 0x80;
            let u2 = (((value >> 7) as u8) & 0x7F) | 0x80;
            let u3 = (((value >> 14) as u8) & 0x7F) | 0x80;
            let u4 = (value >> 21) as u8;
            self.write_u32(
                ((u4 as u32) << 24) | ((u3 as u32) << 16) | ((u2 as u32) << 8) | u1 as u32,
            );
        } else {
            // 5 bytes
            let u1 = ((value as u8) & 0x7F) | 0x80;
            let u2 = (((value >> 7) as u8) & 0x7F) | 0x80;
            let u3 = (((value >> 14) as u8) & 0x7F) | 0x80;
            let u4 = (((value >> 21) as u8) & 0x7F) | 0x80;
            let u5 = (value >> 28) as u8;
            self.write_u32(
                ((u4 as u32) << 24) | ((u3 as u32) << 16) | ((u2 as u32) << 8) | u1 as u32,
            );
            self.bf.push(u5);
        }
    }

    // ============ UINT64 (TypeId = 13) ============

    #[inline(always)]
    pub fn write_u64(&mut self, value: u64) {
        #[cfg(target_endian = "little")]
        {
            let bytes = unsafe { &*(&value as *const u64 as *const [u8; 8]) };
            self.bf.extend_from_slice(bytes);
        }
        #[cfg(target_endian = "big")]
        {
            self.bf.extend_from_slice(&value.to_le_bytes());
        }
    }

    // ============ VAR_UINT64 (TypeId = 14) ============

    #[inline(always)]
    pub fn write_var_uint64(&mut self, value: u64) {
        self._write_var_uint64(value);
    }

    #[inline(always)]
    fn _write_var_uint64(&mut self, value: u64) {
        if value < 0x80 {
            self.bf.push(value as u8);
        } else if value < 0x4000 {
            let u1 = ((value as u8) & 0x7F) | 0x80;
            let u2 = (value >> 7) as u8;
            self.write_u16(((u2 as u16) << 8) | u1 as u16);
        } else if value < 0x200000 {
            let u1 = ((value as u8) & 0x7F) | 0x80;
            let u2 = (((value >> 7) as u8) & 0x7F) | 0x80;
            let u3 = (value >> 14) as u8;
            self.write_u16(((u2 as u16) << 8) | u1 as u16);
            self.bf.push(u3);
        } else if value < 0x10000000 {
            let u1 = ((value as u8) & 0x7F) | 0x80;
            let u2 = (((value >> 7) as u8) & 0x7F) | 0x80;
            let u3 = (((value >> 14) as u8) & 0x7F) | 0x80;
            let u4 = (value >> 21) as u8;
            self.write_u32(
                ((u4 as u32) << 24) | ((u3 as u32) << 16) | ((u2 as u32) << 8) | u1 as u32,
            );
        } else if value < 0x800000000 {
            let u1 = ((value as u8) & 0x7F) | 0x80;
            let u2 = (((value >> 7) as u8) & 0x7F) | 0x80;
            let u3 = (((value >> 14) as u8) & 0x7F) | 0x80;
            let u4 = (((value >> 21) as u8) & 0x7F) | 0x80;
            let u5 = (value >> 28) as u8;
            self.write_u32(
                ((u4 as u32) << 24) | ((u3 as u32) << 16) | ((u2 as u32) << 8) | u1 as u32,
            );
            self.bf.push(u5);
        } else if value < 0x40000000000 {
            let u1 = ((value as u8) & 0x7F) | 0x80;
            let u2 = (((value >> 7) as u8) & 0x7F) | 0x80;
            let u3 = (((value >> 14) as u8) & 0x7F) | 0x80;
            let u4 = (((value >> 21) as u8) & 0x7F) | 0x80;
            let u5 = (((value >> 28) as u8) & 0x7F) | 0x80;
            let u6 = (value >> 35) as u8;
            self.write_u32(
                ((u4 as u32) << 24) | ((u3 as u32) << 16) | ((u2 as u32) << 8) | u1 as u32,
            );
            self.write_u16(((u6 as u16) << 8) | u5 as u16);
        } else if value < 0x2000000000000 {
            let u1 = ((value as u8) & 0x7F) | 0x80;
            let u2 = (((value >> 7) as u8) & 0x7F) | 0x80;
            let u3 = (((value >> 14) as u8) & 0x7F) | 0x80;
            let u4 = (((value >> 21) as u8) & 0x7F) | 0x80;
            let u5 = (((value >> 28) as u8) & 0x7F) | 0x80;
            let u6 = (((value >> 35) as u8) & 0x7F) | 0x80;
            let u7 = (value >> 42) as u8;
            self.write_u32(
                ((u4 as u32) << 24) | ((u3 as u32) << 16) | ((u2 as u32) << 8) | u1 as u32,
            );
            self.write_u16(((u6 as u16) << 8) | u5 as u16);
            self.bf.push(u7);
        } else if value < 0x100000000000000 {
            let u1 = ((value as u8) & 0x7F) | 0x80;
            let u2 = (((value >> 7) as u8) & 0x7F) | 0x80;
            let u3 = (((value >> 14) as u8) & 0x7F) | 0x80;
            let u4 = (((value >> 21) as u8) & 0x7F) | 0x80;
            let u5 = (((value >> 28) as u8) & 0x7F) | 0x80;
            let u6 = (((value >> 35) as u8) & 0x7F) | 0x80;
            let u7 = (((value >> 42) as u8) & 0x7F) | 0x80;
            let u8 = (value >> 49) as u8;
            self.write_u64(
                (u8 as u64) << 56
                    | (u7 as u64) << 48
                    | (u6 as u64) << 40
                    | (u5 as u64) << 32
                    | (u4 as u64) << 24
                    | (u3 as u64) << 16
                    | (u2 as u64) << 8
                    | (u1 as u64),
            );
        } else {
            let u1 = ((value as u8) & 0x7F) | 0x80;
            let u2 = (((value >> 7) as u8) & 0x7F) | 0x80;
            let u3 = (((value >> 14) as u8) & 0x7F) | 0x80;
            let u4 = (((value >> 21) as u8) & 0x7F) | 0x80;
            let u5 = (((value >> 28) as u8) & 0x7F) | 0x80;
            let u6 = (((value >> 35) as u8) & 0x7F) | 0x80;
            let u7 = (((value >> 42) as u8) & 0x7F) | 0x80;
            let u8 = (((value >> 49) as u8) & 0x7F) | 0x80;
            let u9 = (value >> 56) as u8;
            self.write_u64(
                (u8 as u64) << 56
                    | (u7 as u64) << 48
                    | (u6 as u64) << 40
                    | (u5 as u64) << 32
                    | (u4 as u64) << 24
                    | (u3 as u64) << 16
                    | (u2 as u64) << 8
                    | (u1 as u64),
            );
            self.bf.push(u9);
        }
    }

    // ============ TAGGED_UINT64 (TypeId = 15) ============

    /// Write unsigned long using fory Tagged(Small long as int) encoding.
    /// If value is in [0, 0x7fffffff], encode as 4 bytes: `((value as u32) << 1)`.
    /// Otherwise write as 9 bytes: `0b1 | little-endian 8 bytes u64`.
    #[inline(always)]
    pub fn write_tagged_u64(&mut self, value: u64) {
        if value <= i32::MAX as u64 {
            // Fits in 31 bits, encode as 4 bytes with bit 0 = 0
            let v = (value as u32) << 1;
            self.write_u32(v);
        } else {
            // Write flag byte (0b1) followed by 8-byte u64
            self.bf.push(0b1);
            self.write_u64(value);
        }
    }

    // ============ FLOAT32 (TypeId = 17) ============

    #[inline(always)]
    pub fn write_f32(&mut self, value: f32) {
        #[cfg(target_endian = "little")]
        {
            let bytes = unsafe { &*(&value as *const f32 as *const [u8; 4]) };
            self.bf.extend_from_slice(bytes);
        }
        #[cfg(target_endian = "big")]
        {
            self.bf.extend_from_slice(&value.to_bits().to_le_bytes());
        }
    }

    // ============ FLOAT16 (TypeId = 16) ============
    #[inline(always)]
    pub fn write_f16(&mut self, value: float16) {
        self.write_u16(value.to_bits());
    }

    // ============ FLOAT64 (TypeId = 18) ============

    #[inline(always)]
    pub fn write_f64(&mut self, value: f64) {
        #[cfg(target_endian = "little")]
        {
            let bytes = unsafe { &*(&value as *const f64 as *const [u8; 8]) };
            self.bf.extend_from_slice(bytes);
        }
        #[cfg(target_endian = "big")]
        {
            self.bf.extend_from_slice(&value.to_bits().to_le_bytes());
        }
    }

    // ============ STRING (TypeId = 19) ============

    #[inline(always)]
    pub fn write_utf8_string(&mut self, s: &str) {
        let bytes = s.as_bytes();
        let len = bytes.len();
        self.bf.reserve(len);
        self.bf.extend_from_slice(bytes);
    }

    // ============ Rust-specific types (i128, u128, isize, usize) ============

    #[inline(always)]
    pub fn write_i128(&mut self, value: i128) {
        self.write_u128(value as u128);
    }

    #[inline(always)]
    pub fn write_u128(&mut self, value: u128) {
        #[cfg(target_endian = "little")]
        {
            let bytes = unsafe { &*(&value as *const u128 as *const [u8; 16]) };
            self.bf.extend_from_slice(bytes);
        }
        #[cfg(target_endian = "big")]
        {
            self.bf.extend_from_slice(&value.to_le_bytes());
        }
    }

    #[inline(always)]
    pub fn write_isize(&mut self, value: isize) {
        const SIZE: usize = std::mem::size_of::<isize>();
        match SIZE {
            2 => self.write_i16(value as i16),
            4 => self.write_varint32(value as i32),
            8 => self.write_varint64(value as i64),
            _ => unreachable!("unsupported isize size"),
        }
    }

    #[inline(always)]
    pub fn write_usize(&mut self, value: usize) {
        const SIZE: usize = std::mem::size_of::<usize>();
        match SIZE {
            2 => self.write_u16(value as u16),
            4 => self.write_var_uint32(value as u32),
            8 => self.write_var_uint64(value as u64),
            _ => unreachable!("unsupported usize size"),
        }
    }

    // ============ Other helper methods ============

    #[inline(always)]
    pub fn write_var_uint36_small(&mut self, value: u64) {
        assert!(value < (1u64 << 36), "value too large for 36-bit varint");
        if value < 0x80 {
            self.bf.push(value as u8);
        } else if value < 0x4000 {
            let b0 = ((value & 0x7F) as u8) | 0x80;
            let b1 = (value >> 7) as u8;
            let combined = ((b1 as u16) << 8) | (b0 as u16);
            self.write_u16(combined);
        } else if value < 0x200000 {
            let b0 = (value & 0x7F) | 0x80;
            let b1 = ((value >> 7) & 0x7F) | 0x80;
            let b2 = value >> 14;
            let combined = b0 | (b1 << 8) | (b2 << 16);
            self.write_u32(combined as u32);
        } else if value < 0x10000000 {
            let b0 = (value & 0x7F) | 0x80;
            let b1 = ((value >> 7) & 0x7F) | 0x80;
            let b2 = ((value >> 14) & 0x7F) | 0x80;
            let b3 = value >> 21;
            let combined = b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
            self.write_u32(combined as u32);
        } else {
            let b0 = (value & 0x7F) | 0x80;
            let b1 = ((value >> 7) & 0x7F) | 0x80;
            let b2 = ((value >> 14) & 0x7F) | 0x80;
            let b3 = ((value >> 21) & 0x7F) | 0x80;
            let b4 = value >> 28;
            let combined = b0 | (b1 << 8) | (b2 << 16) | (b3 << 24) | (b4 << 32);
            self.write_u64(combined);
        }
    }
}

#[derive(Default)]
#[allow(clippy::needless_lifetimes)]
pub struct Reader<'a> {
    pub(crate) bf: &'a [u8],
    pub(crate) cursor: usize,
}

#[allow(clippy::needless_lifetimes)]
impl<'a> Reader<'a> {
    // ============ Utility methods ============

    #[inline(always)]
    pub fn new(bf: &[u8]) -> Reader<'_> {
        Reader { bf, cursor: 0 }
    }

    #[inline(always)]
    pub(crate) fn move_next(&mut self, additional: usize) {
        self.cursor += additional;
    }

    #[inline(always)]
    pub(crate) fn move_back(&mut self, additional: usize) {
        self.cursor -= additional;
    }

    #[inline(always)]
    pub fn sub_slice(&self, start: usize, end: usize) -> Result<&[u8], Error> {
        // Allow start == bf.len() when end == bf.len() to support empty slices at buffer end
        if start > self.bf.len() || end > self.bf.len() || end < start {
            Err(Error::buffer_out_of_bound(
                start,
                self.bf.len(),
                self.bf.len(),
            ))
        } else {
            Ok(&self.bf[start..end])
        }
    }

    #[inline(always)]
    pub fn slice_after_cursor(&self) -> &[u8] {
        &self.bf[self.cursor..]
    }

    #[inline(always)]
    pub fn get_cursor(&self) -> usize {
        self.cursor
    }

    #[inline(always)]
    fn value_at(&self, index: usize) -> Result<u8, Error> {
        match self.bf.get(index) {
            None => Err(Error::buffer_out_of_bound(
                index,
                self.bf.len(),
                self.bf.len(),
            )),
            Some(v) => Ok(*v),
        }
    }

    #[inline(always)]
    fn check_bound(&self, n: usize) -> Result<(), Error> {
        let end = self
            .cursor
            .checked_add(n)
            .ok_or_else(|| Error::buffer_out_of_bound(self.cursor, n, self.bf.len()))?;
        if end > self.bf.len() {
            Err(Error::buffer_out_of_bound(self.cursor, n, self.bf.len()))
        } else {
            Ok(())
        }
    }

    #[inline(always)]
    fn read_u8_uncheck(&mut self) -> u8 {
        let result = unsafe { self.bf.get_unchecked(self.cursor) };
        self.move_next(1);
        *result
    }

    #[inline(always)]
    pub fn skip(&mut self, len: usize) -> Result<(), Error> {
        self.check_bound(len)?;
        self.move_next(len);
        Ok(())
    }

    #[inline(always)]
    pub fn read_bytes(&mut self, len: usize) -> Result<&[u8], Error> {
        self.check_bound(len)?;
        let result = &self.bf[self.cursor..self.cursor + len];
        self.move_next(len);
        Ok(result)
    }

    #[inline(always)]
    pub fn reset_cursor_to_here(&self) -> impl FnOnce(&mut Self) {
        let raw_cursor = self.cursor;
        move |this: &mut Self| {
            this.cursor = raw_cursor;
        }
    }

    pub fn set_cursor(&mut self, cursor: usize) {
        self.cursor = cursor;
    }

    #[doc(hidden)]
    #[inline(always)]
    pub fn read_with_cursor<T, F>(&mut self, read: F) -> Result<T, Error>
    where
        F: FnOnce(&[u8]) -> Result<(usize, T), Error>,
    {
        let start = self.cursor;
        let (consumed, value) = read(&self.bf[start..])?;
        let end = start
            .checked_add(consumed)
            .ok_or_else(|| Error::buffer_out_of_bound(start, consumed, self.bf.len()))?;
        if end > self.bf.len() {
            Err(Error::buffer_out_of_bound(start, consumed, self.bf.len()))
        } else {
            self.cursor = end;
            Ok(value)
        }
    }

    // ============ BOOL (TypeId = 1) ============

    #[inline(always)]
    pub fn read_bool(&mut self) -> Result<bool, Error> {
        Ok(self.read_u8()? != 0)
    }

    // ============ INT8 (TypeId = 2) ============

    #[inline(always)]
    pub fn read_i8(&mut self) -> Result<i8, Error> {
        Ok(self.read_u8()? as i8)
    }

    // ============ INT16 (TypeId = 3) ============

    #[inline(always)]
    pub fn read_i16(&mut self) -> Result<i16, Error> {
        Ok(self.read_u16()? as i16)
    }

    // ============ INT32 (TypeId = 4) ============

    #[inline(always)]
    pub fn read_i32(&mut self) -> Result<i32, Error> {
        Ok(self.read_u32()? as i32)
    }

    // ============ VARINT32 (TypeId = 5) ============

    #[inline(always)]
    pub fn read_varint32(&mut self) -> Result<i32, Error> {
        let encoded = self.read_varuint32()?;
        Ok(((encoded >> 1) as i32) ^ -((encoded & 1) as i32))
    }

    // ============ INT64 (TypeId = 6) ============

    #[inline(always)]
    pub fn read_i64(&mut self) -> Result<i64, Error> {
        Ok(self.read_u64()? as i64)
    }

    // ============ VARINT64 (TypeId = 7) ============

    #[inline(always)]
    pub fn read_varint64(&mut self) -> Result<i64, Error> {
        let encoded = self.read_varuint64()?;
        Ok(((encoded >> 1) as i64) ^ -((encoded & 1) as i64))
    }

    // ============ TAGGED_INT64 (TypeId = 8) ============

    /// Read signed fory Tagged(Small long as int) encoded i64.
    /// If bit 0 of the first 4 bytes is 0, return the value >> 1 (arithmetic shift).
    /// Otherwise, skip the flag byte and read 8 bytes as i64.
    #[inline(always)]
    pub fn read_tagged_i64(&mut self) -> Result<i64, Error> {
        self.check_bound(4)?;
        let i = LittleEndian::read_i32(&self.bf[self.cursor..]);
        if (i & 0b1) != 0b1 {
            // Bit 0 is 0, small value encoded in 4 bytes
            self.cursor += 4;
            Ok((i >> 1) as i64) // arithmetic right shift preserves sign
        } else {
            // Bit 0 is 1, big value: skip flag byte and read 8 bytes
            self.check_bound(9)?;
            self.cursor += 1;
            let value = LittleEndian::read_i64(&self.bf[self.cursor..]);
            self.cursor += 8;
            Ok(value)
        }
    }

    // ============ UINT8 (TypeId = 9) ============

    #[inline(always)]
    pub fn peek_u8(&mut self) -> Result<u8, Error> {
        let result = self.value_at(self.cursor)?;
        Ok(result)
    }

    #[inline(always)]
    pub fn read_u8(&mut self) -> Result<u8, Error> {
        let result = self.value_at(self.cursor)?;
        self.move_next(1);
        Ok(result)
    }

    // ============ UINT16 (TypeId = 10) ============

    #[inline(always)]
    pub fn read_u16(&mut self) -> Result<u16, Error> {
        self.check_bound(2)?;
        let result = LittleEndian::read_u16(&self.bf[self.cursor..self.cursor + 2]);
        self.cursor += 2;
        Ok(result)
    }

    // ============ UINT32 (TypeId = 11) ============

    #[inline(always)]
    pub fn read_u32(&mut self) -> Result<u32, Error> {
        self.check_bound(4)?;
        let result = LittleEndian::read_u32(&self.bf[self.cursor..self.cursor + 4]);
        self.cursor += 4;
        Ok(result)
    }

    // ============ VAR_UINT32 (TypeId = 12) ============

    #[inline(always)]
    pub fn read_varuint32(&mut self) -> Result<u32, Error> {
        let b0 = self.value_at(self.cursor)? as u32;
        if b0 < 0x80 {
            self.move_next(1);
            return Ok(b0);
        }

        let b1 = self.value_at(self.cursor + 1)? as u32;
        let mut encoded = (b0 & 0x7F) | ((b1 & 0x7F) << 7);
        if b1 < 0x80 {
            self.move_next(2);
            return Ok(encoded);
        }

        let b2 = self.value_at(self.cursor + 2)? as u32;
        encoded |= (b2 & 0x7F) << 14;
        if b2 < 0x80 {
            self.move_next(3);
            return Ok(encoded);
        }

        let b3 = self.value_at(self.cursor + 3)? as u32;
        encoded |= (b3 & 0x7F) << 21;
        if b3 < 0x80 {
            self.move_next(4);
            return Ok(encoded);
        }

        let b4 = self.value_at(self.cursor + 4)? as u32;
        encoded |= b4 << 28;
        self.move_next(5);
        Ok(encoded)
    }

    // ============ UINT64 (TypeId = 13) ============

    #[inline(always)]
    pub fn read_u64(&mut self) -> Result<u64, Error> {
        self.check_bound(8)?;
        let result = LittleEndian::read_u64(&self.bf[self.cursor..self.cursor + 8]);
        self.cursor += 8;
        Ok(result)
    }

    // ============ VAR_UINT64 (TypeId = 14) ============

    #[inline(always)]
    pub fn read_varuint64(&mut self) -> Result<u64, Error> {
        let b0 = self.value_at(self.cursor)? as u64;
        if b0 < 0x80 {
            self.move_next(1);
            return Ok(b0);
        }

        let b1 = self.value_at(self.cursor + 1)? as u64;
        let mut result = (b0 & 0x7F) | ((b1 & 0x7F) << 7);
        if b1 < 0x80 {
            self.move_next(2);
            return Ok(result);
        }

        let b2 = self.value_at(self.cursor + 2)? as u64;
        result |= (b2 & 0x7F) << 14;
        if b2 < 0x80 {
            self.move_next(3);
            return Ok(result);
        }

        let b3 = self.value_at(self.cursor + 3)? as u64;
        result |= (b3 & 0x7F) << 21;
        if b3 < 0x80 {
            self.move_next(4);
            return Ok(result);
        }

        let b4 = self.value_at(self.cursor + 4)? as u64;
        result |= (b4 & 0x7F) << 28;
        if b4 < 0x80 {
            self.move_next(5);
            return Ok(result);
        }

        let b5 = self.value_at(self.cursor + 5)? as u64;
        result |= (b5 & 0x7F) << 35;
        if b5 < 0x80 {
            self.move_next(6);
            return Ok(result);
        }

        let b6 = self.value_at(self.cursor + 6)? as u64;
        result |= (b6 & 0x7F) << 42;
        if b6 < 0x80 {
            self.move_next(7);
            return Ok(result);
        }

        let b7 = self.value_at(self.cursor + 7)? as u64;
        result |= (b7 & 0x7F) << 49;
        if b7 < 0x80 {
            self.move_next(8);
            return Ok(result);
        }

        let b8 = self.value_at(self.cursor + 8)? as u64;
        result |= (b8 & 0xFF) << 56;
        self.move_next(9);
        Ok(result)
    }

    // ============ TAGGED_UINT64 (TypeId = 15) ============

    /// Read unsigned fory Tagged(Small long as int) encoded u64.
    /// If bit 0 of the first 4 bytes is 0, return the value >> 1.
    /// Otherwise, skip the flag byte and read 8 bytes as u64.
    #[inline(always)]
    pub fn read_tagged_u64(&mut self) -> Result<u64, Error> {
        self.check_bound(4)?;
        let i = LittleEndian::read_u32(&self.bf[self.cursor..]);
        if (i & 0b1) != 0b1 {
            // Bit 0 is 0, small value encoded in 4 bytes
            self.cursor += 4;
            Ok((i >> 1) as u64)
        } else {
            // Bit 0 is 1, big value: skip flag byte and read 8 bytes
            self.check_bound(9)?;
            self.cursor += 1;
            let value = LittleEndian::read_u64(&self.bf[self.cursor..]);
            self.cursor += 8;
            Ok(value)
        }
    }

    // ============ FLOAT32 (TypeId = 17) ============

    #[inline(always)]
    pub fn read_f32(&mut self) -> Result<f32, Error> {
        self.check_bound(4)?;
        let result = LittleEndian::read_f32(&self.bf[self.cursor..self.cursor + 4]);
        self.cursor += 4;
        Ok(result)
    }

    // ============ FLOAT64 (TypeId = 18) ============
    #[inline(always)]
    pub fn read_f16(&mut self) -> Result<float16, Error> {
        self.check_bound(2)?;
        let bits = LittleEndian::read_u16(&self.bf[self.cursor..self.cursor + 2]);
        self.cursor += 2;
        Ok(float16::from_bits(bits))
    }

    pub fn read_f64(&mut self) -> Result<f64, Error> {
        self.check_bound(8)?;
        let result = LittleEndian::read_f64(&self.bf[self.cursor..self.cursor + 8]);
        self.cursor += 8;
        Ok(result)
    }

    // ============ STRING (TypeId = 19) ============

    #[inline(always)]
    pub fn read_latin1_string(&mut self, len: usize) -> Result<String, Error> {
        self.check_bound(len)?;
        if len < SIMD_THRESHOLD {
            // Fast path for small buffers
            unsafe {
                let src = self.sub_slice(self.cursor, self.cursor + len)?;

                // Check if all bytes are ASCII (< 0x80)
                let is_ascii = src.iter().all(|&b| b < 0x80);

                if is_ascii {
                    // ASCII fast path: Latin1 == UTF-8, direct copy
                    let mut vec = Vec::with_capacity(len);
                    let dst = vec.as_mut_ptr();
                    std::ptr::copy_nonoverlapping(src.as_ptr(), dst, len);
                    vec.set_len(len);
                    self.move_next(len);
                    Ok(String::from_utf8_unchecked(vec))
                } else {
                    // Contains Latin1 bytes (0x80-0xFF): must convert to UTF-8
                    let mut out: Vec<u8> = Vec::with_capacity(len * 2);
                    let out_ptr = out.as_mut_ptr();
                    let mut out_len = 0;

                    for &b in src {
                        if b < 0x80 {
                            *out_ptr.add(out_len) = b;
                            out_len += 1;
                        } else {
                            // Latin1 -> UTF-8 encoding
                            *out_ptr.add(out_len) = 0xC0 | (b >> 6);
                            *out_ptr.add(out_len + 1) = 0x80 | (b & 0x3F);
                            out_len += 2;
                        }
                    }

                    out.set_len(out_len);
                    self.move_next(len);
                    Ok(String::from_utf8_unchecked(out))
                }
            }
        } else {
            // Use SIMD for larger strings where the overhead is amortized
            read_latin1_simd(self, len)
        }
    }

    #[inline(always)]
    pub fn read_utf8_string(&mut self, len: usize) -> Result<String, Error> {
        self.check_bound(len)?;
        // don't use simd for memory copy, copy_non_overlapping is faster
        unsafe {
            let mut vec = Vec::with_capacity(len);
            let src = self.bf.as_ptr().add(self.cursor);
            let dst = vec.as_mut_ptr();
            // Use fastest possible copy - copy_nonoverlapping compiles to memcpy
            std::ptr::copy_nonoverlapping(src, dst, len);
            vec.set_len(len);
            self.move_next(len);
            // SAFETY: Assuming valid UTF-8 bytes (responsibility of serialization protocol)
            Ok(String::from_utf8_unchecked(vec))
        }
    }

    #[inline(always)]
    pub fn read_utf16_string(&mut self, len: usize) -> Result<String, Error> {
        self.check_bound(len)?;
        let slice = self.sub_slice(self.cursor, self.cursor + len)?;
        let units: Vec<u16> = slice
            .chunks_exact(2)
            .map(|c| u16::from_le_bytes([c[0], c[1]]))
            .collect();
        self.move_next(len);
        Ok(String::from_utf16_lossy(&units))
    }

    // ============ Rust-specific types (i128, u128, isize, usize) ============

    #[inline(always)]
    pub fn read_i128(&mut self) -> Result<i128, Error> {
        Ok(self.read_u128()? as i128)
    }

    #[inline(always)]
    pub fn read_u128(&mut self) -> Result<u128, Error> {
        self.check_bound(16)?;
        let result = LittleEndian::read_u128(&self.bf[self.cursor..self.cursor + 16]);
        self.cursor += 16;
        Ok(result)
    }

    #[inline(always)]
    pub fn read_isize(&mut self) -> Result<isize, Error> {
        const SIZE: usize = std::mem::size_of::<isize>();
        match SIZE {
            2 => Ok(self.read_i16()? as isize),
            4 => Ok(self.read_varint32()? as isize),
            8 => Ok(self.read_varint64()? as isize),
            _ => unreachable!("unsupported isize size"),
        }
    }

    #[inline(always)]
    pub fn read_usize(&mut self) -> Result<usize, Error> {
        const SIZE: usize = std::mem::size_of::<usize>();
        match SIZE {
            2 => Ok(self.read_u16()? as usize),
            4 => Ok(self.read_varuint32()? as usize),
            8 => Ok(self.read_varuint64()? as usize),
            _ => unreachable!("unsupported usize size"),
        }
    }

    // ============ Other helper methods ============

    #[inline(always)]
    pub fn read_varuint36small(&mut self) -> Result<u64, Error> {
        // Keep this API panic-free even if cursor is externally set past buffer end.
        self.check_bound(0)?;
        let start = self.cursor;
        let slice = self.slice_after_cursor();

        if slice.len() >= 8 {
            // here already check bound
            let bulk = self.read_u64()?;
            let mut result = bulk & 0x7F;
            let mut read_idx = start;

            if (bulk & 0x80) != 0 {
                read_idx += 1;
                result |= (bulk >> 1) & 0x3F80;
                if (bulk & 0x8000) != 0 {
                    read_idx += 1;
                    result |= (bulk >> 2) & 0x1FC000;
                    if (bulk & 0x800000) != 0 {
                        read_idx += 1;
                        result |= (bulk >> 3) & 0xFE00000;
                        if (bulk & 0x80000000) != 0 {
                            read_idx += 1;
                            result |= (bulk >> 4) & 0xFF0000000;
                        }
                    }
                }
            }
            self.cursor = read_idx + 1;
            return Ok(result);
        }

        let mut result = 0u64;
        let mut shift = 0;
        while self.cursor < self.bf.len() {
            let b = self.read_u8_uncheck();
            result |= ((b & 0x7F) as u64) << shift;
            if (b & 0x80) == 0 {
                break;
            }
            shift += 7;
            if shift >= 36 {
                return Err(Error::encode_error("varuint36small overflow"));
            }
        }
        Ok(result)
    }
}

#[allow(clippy::needless_lifetimes)]
unsafe impl<'a> Send for Reader<'a> {}
#[allow(clippy::needless_lifetimes)]
unsafe impl<'a> Sync for Reader<'a> {}
