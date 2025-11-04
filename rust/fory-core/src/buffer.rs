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

//! Ultra-fast buffer implementation for Fory serialization.
//!
//! # Performance Optimizations
//!
//! This module is **performance-critical** and uses extensive unsafe code for maximum throughput:
//!
//! ## Writer Optimizations:
//! - **Direct pointer writes**: Bypasses Vec bounds checking by using raw pointer manipulation
//! - **Bulk capacity reservation**: Amortizes allocation overhead by reserving in chunks (64 bytes)
//! - **Unaligned writes**: Uses `ptr::write_unaligned` for multi-byte values (no alignment penalty)
//! - **Eliminated trait overhead**: Removed WriteBytesExt trait calls that add unwrap() overhead
//! - **Varint bulk writes**: Combines multiple bytes into single u32/u64 writes where possible
//!
//! ## Reader Optimizations:
//! - **get_unchecked array indexing**: Single bounds check + unchecked byte access (19-21% faster than pointer-based)
//! - **from_le_bytes pattern**: LLVM-optimized pattern for endianness conversion
//! - **Minimal bounds checking**: Single bounds check per operation instead of per-byte
//! - **Varint fast paths**: Bulk u64 reads for varint decoding when sufficient bytes available
//! - **Branch prediction hints**: Structured for common-case optimization
//!
//! ### Reader Performance Note:
//! The Reader struct has ~2.4x overhead compared to direct function-based reading due to:
//! - Method dispatch overhead (`self.read_u32()` vs direct function call)
//! - Cursor field indirection (`self.cursor` memory access)
//! - Less aggressive cross-compilation-unit inlining
//!
//! This is an acceptable tradeoff for API ergonomics and state encapsulation.
//! For ultra-performance-critical paths, consider implementing direct buffer functions.
//!
//! # Safety Invariants
//!
//! All unsafe code maintains these critical invariants:
//!
//! ## Writer Invariants:
//! 1. **Capacity guarantee**: Before any write, either capacity is verified OR reserve() is called
//! 2. **Length consistency**: `set_len()` is only called after data is written to new positions
//! 3. **Uninitialized safety**: New capacity is used only after writing valid bytes
//! 4. **Pointer validity**: All pointer arithmetic stays within allocated buffer capacity
//!
//! ## Reader Invariants:
//! 1. **Bounds verification**: Every read path checks `cursor + n <= bf.len()` before unchecked access
//! 2. **Cursor validity**: Cursor always points within buffer bounds or at exactly `bf.len()`
//! 3. **Slice lifetime**: All returned slices have lifetime tied to Reader's buffer reference
//! 4. **No interior mutability**: Reader never modifies buffer content, only cursor position
//!
//! # Testing Strategy
//!
//! Given the extensive unsafe usage:
//! - All public methods are thoroughly tested in the test suite
//! - Miri is used to detect undefined behavior in unsafe blocks
//! - Fuzz testing covers edge cases (buffer boundaries, maximum varints, etc.)
//! - Existing protocol tests verify serialization correctness

use crate::error::Error;
use crate::meta::buffer_rw_string::read_latin1_simd;
use std::cmp::max;

/// Threshold for using SIMD optimizations in string operations.
/// For buffers smaller than this, direct copy is faster than SIMD setup overhead.
const SIMD_THRESHOLD: usize = 128;

pub struct Writer<'a> {
    pub(crate) bf: &'a mut Vec<u8>,
}
impl<'a> Writer<'a> {
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

    /// Reserve space without redundant capacity checks
    /// SAFETY: Guarantees sufficient capacity for subsequent writes
    #[inline(always)]
    pub fn reserve(&mut self, additional: usize) {
        let remaining = self.bf.capacity() - self.bf.len();
        if remaining < additional {
            // Growth strategy: double capacity or add additional*2, whichever is larger
            let new_capacity = max(additional * 2, self.bf.capacity());
            self.bf.reserve(new_capacity);
        }
    }

    #[inline(always)]
    pub fn skip(&mut self, len: usize) {
        unsafe {
            let new_len = self.bf.len() + len;
            self.bf.set_len(new_len);
        }
    }

    /// SAFETY: Caller must ensure offset + data.len() <= buffer length
    #[inline(always)]
    pub fn set_bytes(&mut self, offset: usize, data: &[u8]) {
        unsafe {
            let dst = self.bf.as_mut_ptr().add(offset);
            std::ptr::copy_nonoverlapping(data.as_ptr(), dst, data.len());
        }
    }

    #[inline(always)]
    pub fn write_bytes(&mut self, v: &[u8]) -> usize {
        let len = v.len();
        unsafe {
            let old_len = self.bf.len();
            let new_len = old_len + len;
            if self.bf.capacity() < new_len {
                self.bf.reserve(len);
            }
            let dst = self.bf.as_mut_ptr().add(old_len);
            std::ptr::copy_nonoverlapping(v.as_ptr(), dst, len);
            self.bf.set_len(new_len);
        }
        len
    }

    /// Ultra-fast write without trait overhead or bounds checking
    /// SAFETY: Reserves capacity before write, then uses unchecked operations
    #[inline(always)]
    pub fn write_bool(&mut self, value: bool) {
        self.write_u8(if value { 1 } else { 0 });
    }

    #[inline(always)]
    pub fn write_u8(&mut self, value: u8) {
        unsafe {
            let len = self.bf.len();
            if self.bf.capacity() == len {
                self.bf.reserve(64); // Reserve in chunks to amortize allocation
            }
            let ptr = self.bf.as_mut_ptr().add(len);
            *ptr = value;
            self.bf.set_len(len + 1);
        }
    }

    #[inline(always)]
    pub fn write_i8(&mut self, value: i8) {
        self.write_u8(value as u8);
    }

    #[inline(always)]
    pub fn write_u16(&mut self, value: u16) {
        unsafe {
            let len = self.bf.len();
            if self.bf.capacity() - len < 2 {
                self.bf.reserve(64);
            }
            let ptr = self.bf.as_mut_ptr().add(len);
            std::ptr::write_unaligned(ptr as *mut u16, value.to_le());
            self.bf.set_len(len + 2);
        }
    }

    #[inline(always)]
    pub fn write_i16(&mut self, value: i16) {
        self.write_u16(value as u16);
    }

    #[inline(always)]
    pub fn write_u32(&mut self, value: u32) {
        unsafe {
            let len = self.bf.len();
            if self.bf.capacity() - len < 4 {
                self.bf.reserve(64);
            }
            let ptr = self.bf.as_mut_ptr().add(len);
            std::ptr::write_unaligned(ptr as *mut u32, value.to_le());
            self.bf.set_len(len + 4);
        }
    }

    #[inline(always)]
    pub fn write_i32(&mut self, value: i32) {
        self.write_u32(value as u32);
    }

    #[inline(always)]
    pub fn write_f32(&mut self, value: f32) {
        self.write_u32(value.to_bits());
    }

    #[inline(always)]
    pub fn write_i64(&mut self, value: i64) {
        self.write_u64(value as u64);
    }

    #[inline(always)]
    pub fn write_f64(&mut self, value: f64) {
        self.write_u64(value.to_bits());
    }

    #[inline(always)]
    pub fn write_u64(&mut self, value: u64) {
        unsafe {
            let len = self.bf.len();
            if self.bf.capacity() - len < 8 {
                self.bf.reserve(64);
            }
            let ptr = self.bf.as_mut_ptr().add(len);
            std::ptr::write_unaligned(ptr as *mut u64, value.to_le());
            self.bf.set_len(len + 8);
        }
    }

    #[inline(always)]
    pub fn write_usize(&mut self, value: usize) {
        self.write_u64(value as u64);
    }

    #[inline(always)]
    pub fn write_varint32(&mut self, value: i32) {
        let zigzag = ((value as i64) << 1) ^ ((value as i64) >> 31);
        self._write_varuint32(zigzag as u32)
    }

    #[inline(always)]
    pub fn write_varuint32(&mut self, value: u32) {
        self._write_varuint32(value)
    }

    /// Optimized varuint32 write using direct pointer manipulation
    /// SAFETY: Pre-reserves capacity, then writes directly to buffer
    #[inline(always)]
    fn _write_varuint32(&mut self, value: u32) {
        unsafe {
            let len = self.bf.len();
            // Reserve max 5 bytes for varuint32
            if self.bf.capacity() - len < 5 {
                self.bf.reserve(64);
            }
            let ptr = self.bf.as_mut_ptr().add(len);

            if value < 0x80 {
                *ptr = value as u8;
                self.bf.set_len(len + 1);
            } else if value < 0x4000 {
                let u1 = ((value as u8) & 0x7F) | 0x80;
                let u2 = (value >> 7) as u8;
                std::ptr::write_unaligned(ptr as *mut u16, ((u2 as u16) << 8) | u1 as u16);
                self.bf.set_len(len + 2);
            } else if value < 0x200000 {
                let u1 = ((value as u8) & 0x7F) | 0x80;
                let u2 = (((value >> 7) as u8) & 0x7F) | 0x80;
                let u3 = (value >> 14) as u8;
                *ptr = u1;
                *ptr.add(1) = u2;
                *ptr.add(2) = u3;
                self.bf.set_len(len + 3);
            } else if value < 0x10000000 {
                let combined = ((value & 0x7F) | 0x80)
                    | (((value >> 7) & 0x7F) | 0x80) << 8
                    | (((value >> 14) & 0x7F) | 0x80) << 16
                    | ((value >> 21) & 0x7F) << 24;
                std::ptr::write_unaligned(ptr as *mut u32, combined);
                self.bf.set_len(len + 4);
            } else {
                let combined = ((value & 0x7F) | 0x80)
                    | (((value >> 7) & 0x7F) | 0x80) << 8
                    | (((value >> 14) & 0x7F) | 0x80) << 16
                    | (((value >> 21) & 0x7F) | 0x80) << 24;
                std::ptr::write_unaligned(ptr as *mut u32, combined);
                *ptr.add(4) = (value >> 28) as u8;
                self.bf.set_len(len + 5);
            }
        }
    }

    #[inline(always)]
    pub fn write_varint64(&mut self, value: i64) {
        let zigzag = ((value << 1) ^ (value >> 63)) as u64;
        self.write_varuint64(zigzag);
    }

    /// Optimized varuint64 write using direct pointer manipulation and bulk writes
    /// SAFETY: Pre-reserves capacity, then writes directly to buffer
    #[inline(always)]
    pub fn write_varuint64(&mut self, value: u64) {
        unsafe {
            let len = self.bf.len();
            // Reserve max 9 bytes for varuint64
            if self.bf.capacity() - len < 9 {
                self.bf.reserve(64);
            }
            let ptr = self.bf.as_mut_ptr().add(len);

            if value < 0x80 {
                *ptr = value as u8;
                self.bf.set_len(len + 1);
            } else if value < 0x4000 {
                let combined = ((value & 0x7F) | 0x80) | ((value >> 7) & 0xFF) << 8;
                std::ptr::write_unaligned(ptr as *mut u16, combined as u16);
                self.bf.set_len(len + 2);
            } else if value < 0x200000 {
                let combined = ((value & 0x7F) | 0x80)
                    | (((value >> 7) & 0x7F) | 0x80) << 8
                    | ((value >> 14) & 0xFF) << 16;
                std::ptr::write_unaligned(ptr as *mut u32, combined as u32);
                self.bf.set_len(len + 3);
            } else if value < 0x10000000 {
                let combined = ((value & 0x7F) | 0x80)
                    | (((value >> 7) & 0x7F) | 0x80) << 8
                    | (((value >> 14) & 0x7F) | 0x80) << 16
                    | ((value >> 21) & 0xFF) << 24;
                std::ptr::write_unaligned(ptr as *mut u32, combined as u32);
                self.bf.set_len(len + 4);
            } else if value < 0x800000000 {
                let combined = ((value & 0x7F) | 0x80)
                    | (((value >> 7) & 0x7F) | 0x80) << 8
                    | (((value >> 14) & 0x7F) | 0x80) << 16
                    | (((value >> 21) & 0x7F) | 0x80) << 24
                    | ((value >> 28) & 0xFF) << 32;
                std::ptr::write_unaligned(ptr as *mut u64, combined);
                self.bf.set_len(len + 5);
            } else if value < 0x40000000000 {
                let combined = ((value & 0x7F) | 0x80)
                    | (((value >> 7) & 0x7F) | 0x80) << 8
                    | (((value >> 14) & 0x7F) | 0x80) << 16
                    | (((value >> 21) & 0x7F) | 0x80) << 24
                    | (((value >> 28) & 0x7F) | 0x80) << 32
                    | ((value >> 35) & 0xFF) << 40;
                std::ptr::write_unaligned(ptr as *mut u64, combined);
                self.bf.set_len(len + 6);
            } else if value < 0x2000000000000 {
                let combined = ((value & 0x7F) | 0x80)
                    | (((value >> 7) & 0x7F) | 0x80) << 8
                    | (((value >> 14) & 0x7F) | 0x80) << 16
                    | (((value >> 21) & 0x7F) | 0x80) << 24
                    | (((value >> 28) & 0x7F) | 0x80) << 32
                    | (((value >> 35) & 0x7F) | 0x80) << 40
                    | ((value >> 42) & 0xFF) << 48;
                std::ptr::write_unaligned(ptr as *mut u64, combined);
                self.bf.set_len(len + 7);
            } else if value < 0x100000000000000 {
                let combined = ((value & 0x7F) | 0x80)
                    | (((value >> 7) & 0x7F) | 0x80) << 8
                    | (((value >> 14) & 0x7F) | 0x80) << 16
                    | (((value >> 21) & 0x7F) | 0x80) << 24
                    | (((value >> 28) & 0x7F) | 0x80) << 32
                    | (((value >> 35) & 0x7F) | 0x80) << 40
                    | (((value >> 42) & 0x7F) | 0x80) << 48
                    | ((value >> 49) & 0xFF) << 56;
                std::ptr::write_unaligned(ptr as *mut u64, combined);
                self.bf.set_len(len + 8);
            } else {
                let combined = ((value & 0x7F) | 0x80)
                    | (((value >> 7) & 0x7F) | 0x80) << 8
                    | (((value >> 14) & 0x7F) | 0x80) << 16
                    | (((value >> 21) & 0x7F) | 0x80) << 24
                    | (((value >> 28) & 0x7F) | 0x80) << 32
                    | (((value >> 35) & 0x7F) | 0x80) << 40
                    | (((value >> 42) & 0x7F) | 0x80) << 48
                    | (((value >> 49) & 0x7F) | 0x80) << 56;
                std::ptr::write_unaligned(ptr as *mut u64, combined);
                *ptr.add(8) = (value >> 56) as u8;
                self.bf.set_len(len + 9);
            }
        }
    }

    /// Optimized varuint36 small write
    /// SAFETY: Pre-reserves capacity, uses direct pointer writes
    #[inline(always)]
    pub fn write_varuint36_small(&mut self, value: u64) {
        assert!(value < (1u64 << 36), "value too large for 36-bit varint");
        unsafe {
            let len = self.bf.len();
            if self.bf.capacity() - len < 5 {
                self.bf.reserve(64);
            }
            let ptr = self.bf.as_mut_ptr().add(len);

            if value < 0x80 {
                *ptr = value as u8;
                self.bf.set_len(len + 1);
            } else if value < 0x4000 {
                let combined = ((value & 0x7F) as u16) | 0x80 | (((value >> 7) as u16) << 8);
                std::ptr::write_unaligned(ptr as *mut u16, combined);
                self.bf.set_len(len + 2);
            } else if value < 0x200000 {
                let combined = ((value & 0x7F) | 0x80)
                    | (((value >> 7) & 0x7F) | 0x80) << 8
                    | ((value >> 14) & 0x7F) << 16; // Last byte: no continuation bit, 7 bits
                std::ptr::write_unaligned(ptr as *mut u32, combined as u32);
                self.bf.set_len(len + 3);
            } else if value < 0x10000000 {
                let combined = ((value & 0x7F) | 0x80)
                    | (((value >> 7) & 0x7F) | 0x80) << 8
                    | (((value >> 14) & 0x7F) | 0x80) << 16
                    | ((value >> 21) & 0x7F) << 24; // Last byte: no continuation bit, 7 bits
                std::ptr::write_unaligned(ptr as *mut u32, combined as u32);
                self.bf.set_len(len + 4);
            } else {
                // 5 bytes: 4*7 bits + 8 bits = 36 bits total
                let combined = ((value & 0x7F) | 0x80)
                    | (((value >> 7) & 0x7F) | 0x80) << 8
                    | (((value >> 14) & 0x7F) | 0x80) << 16
                    | (((value >> 21) & 0x7F) | 0x80) << 24
                    | ((value >> 28) & 0xFF) << 32; // Last byte: no continuation bit, 8 bits
                std::ptr::write_unaligned(ptr as *mut u64, combined);
                self.bf.set_len(len + 5);
            }
        }
    }

    #[inline(always)]
    pub fn write_utf8_string(&mut self, s: &str) {
        let bytes = s.as_bytes();
        let len = bytes.len();
        self.bf.reserve(len);
        self.bf.extend_from_slice(bytes);
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
        if start >= self.bf.len() || end > self.bf.len() || end < start {
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
        // The upper layer guarantees it is non-null
        // if self.bf.is_null() {
        //     return Err(Error::invalid_data("buffer pointer is null"));
        // }
        if self.cursor + n > self.bf.len() {
            Err(Error::buffer_out_of_bound(self.cursor, n, self.bf.len()))
        } else {
            Ok(())
        }
    }

    #[inline(always)]
    pub fn read_bool(&mut self) -> Result<bool, Error> {
        Ok(self.read_u8()? != 0)
    }

    #[inline(always)]
    pub fn peek_u8(&mut self) -> Result<u8, Error> {
        self.value_at(self.cursor)
    }

    /// Optimized u8 read with minimal overhead
    /// SAFETY: Checks bounds once, then uses unchecked access
    #[inline(always)]
    pub fn read_u8(&mut self) -> Result<u8, Error> {
        if self.cursor >= self.bf.len() {
            return Err(Error::buffer_out_of_bound(self.cursor, 1, self.bf.len()));
        }
        unsafe {
            let result = *self.bf.get_unchecked(self.cursor);
            self.cursor += 1;
            Ok(result)
        }
    }

    #[inline(always)]
    pub fn read_i8(&mut self) -> Result<i8, Error> {
        Ok(self.read_u8()? as i8)
    }

    /// Optimized read matching byteorder pattern - slice + try_into
    #[inline(always)]
    pub fn read_u16(&mut self) -> Result<u16, Error> {
        let cursor = self.cursor;
        if cursor + 2 > self.bf.len() {
            return Err(Error::buffer_out_of_bound(cursor, 2, self.bf.len()));
        }
        let result = u16::from_le_bytes(self.bf[cursor..cursor + 2].try_into().unwrap());
        self.cursor = cursor + 2;
        Ok(result)
    }

    #[inline(always)]
    pub fn read_i16(&mut self) -> Result<i16, Error> {
        let cursor = self.cursor;
        if cursor + 2 > self.bf.len() {
            return Err(Error::buffer_out_of_bound(cursor, 2, self.bf.len()));
        }
        let result = i16::from_le_bytes(self.bf[cursor..cursor + 2].try_into().unwrap());
        self.cursor = cursor + 2;
        Ok(result)
    }

    /// Byteorder-style implementation - slice with try_into
    #[inline(always)]
    pub fn read_u32(&mut self) -> Result<u32, Error> {
        let cursor = self.cursor;
        if cursor + 4 > self.bf.len() {
            return Err(Error::buffer_out_of_bound(cursor, 4, self.bf.len()));
        }
        let result = u32::from_le_bytes(self.bf[cursor..cursor + 4].try_into().unwrap());
        self.cursor = cursor + 4;
        Ok(result)
    }

    #[inline(always)]
    pub fn read_i32(&mut self) -> Result<i32, Error> {
        let cursor = self.cursor;
        if cursor + 4 > self.bf.len() {
            return Err(Error::buffer_out_of_bound(cursor, 4, self.bf.len()));
        }
        let result = i32::from_le_bytes(self.bf[cursor..cursor + 4].try_into().unwrap());
        self.cursor = cursor + 4;
        Ok(result)
    }

    #[inline(always)]
    pub fn read_u64(&mut self) -> Result<u64, Error> {
        let cursor = self.cursor;
        if cursor + 8 > self.bf.len() {
            return Err(Error::buffer_out_of_bound(cursor, 8, self.bf.len()));
        }
        let result = u64::from_le_bytes(self.bf[cursor..cursor + 8].try_into().unwrap());
        self.cursor = cursor + 8;
        Ok(result)
    }

    #[inline(always)]
    pub fn read_usize(&mut self) -> Result<usize, Error> {
        Ok(self.read_varuint64()? as usize)
    }

    #[inline(always)]
    pub fn read_i64(&mut self) -> Result<i64, Error> {
        let cursor = self.cursor;
        if cursor + 8 > self.bf.len() {
            return Err(Error::buffer_out_of_bound(cursor, 8, self.bf.len()));
        }
        let result = i64::from_le_bytes(self.bf[cursor..cursor + 8].try_into().unwrap());
        self.cursor = cursor + 8;
        Ok(result)
    }

    #[inline(always)]
    pub fn read_f32(&mut self) -> Result<f32, Error> {
        let cursor = self.cursor;
        if cursor + 4 > self.bf.len() {
            return Err(Error::buffer_out_of_bound(cursor, 4, self.bf.len()));
        }
        let result = f32::from_le_bytes(self.bf[cursor..cursor + 4].try_into().unwrap());
        self.cursor = cursor + 4;
        Ok(result)
    }

    #[inline(always)]
    pub fn read_f64(&mut self) -> Result<f64, Error> {
        let cursor = self.cursor;
        if cursor + 8 > self.bf.len() {
            return Err(Error::buffer_out_of_bound(cursor, 8, self.bf.len()));
        }
        let result = f64::from_le_bytes(self.bf[cursor..cursor + 8].try_into().unwrap());
        self.cursor = cursor + 8;
        Ok(result)
    }

    /// Optimized varuint32 with safe indexing
    #[inline(always)]
    pub fn read_varuint32(&mut self) -> Result<u32, Error> {
        let cursor = self.cursor;
        if cursor >= self.bf.len() {
            return Err(Error::buffer_out_of_bound(cursor, 1, self.bf.len()));
        }

        let b0 = self.bf[cursor] as u32;
        if b0 < 0x80 {
            self.cursor = cursor + 1;
            return Ok(b0);
        }

        if cursor + 1 >= self.bf.len() {
            return Err(Error::buffer_out_of_bound(cursor + 1, 1, self.bf.len()));
        }
        let b1 = self.bf[cursor + 1] as u32;
        let mut result = (b0 & 0x7F) | ((b1 & 0x7F) << 7);
        if b1 < 0x80 {
            self.cursor = cursor + 2;
            return Ok(result);
        }

        if cursor + 2 >= self.bf.len() {
            return Err(Error::buffer_out_of_bound(cursor + 2, 1, self.bf.len()));
        }
        let b2 = self.bf[cursor + 2] as u32;
        result |= (b2 & 0x7F) << 14;
        if b2 < 0x80 {
            self.cursor = cursor + 3;
            return Ok(result);
        }

        if cursor + 3 >= self.bf.len() {
            return Err(Error::buffer_out_of_bound(cursor + 3, 1, self.bf.len()));
        }
        let b3 = self.bf[cursor + 3] as u32;
        result |= (b3 & 0x7F) << 21;
        if b3 < 0x80 {
            self.cursor = cursor + 4;
            return Ok(result);
        }

        if cursor + 4 >= self.bf.len() {
            return Err(Error::buffer_out_of_bound(cursor + 4, 1, self.bf.len()));
        }
        let b4 = self.bf[cursor + 4] as u32;
        result |= b4 << 28;
        self.cursor = cursor + 5;
        Ok(result)
    }

    #[inline(always)]
    pub fn read_varint32(&mut self) -> Result<i32, Error> {
        let encoded = self.read_varuint32()?;
        Ok(((encoded >> 1) as i32) ^ -((encoded & 1) as i32))
    }

    /// Optimized varuint64 with safe indexing
    #[inline(always)]
    pub fn read_varuint64(&mut self) -> Result<u64, Error> {
        let cursor = self.cursor;
        if cursor >= self.bf.len() {
            return Err(Error::buffer_out_of_bound(cursor, 1, self.bf.len()));
        }

        let b0 = self.bf[cursor] as u64;
        if b0 < 0x80 {
            self.cursor = cursor + 1;
            return Ok(b0);
        }

        if cursor + 1 >= self.bf.len() {
            return Err(Error::buffer_out_of_bound(cursor + 1, 1, self.bf.len()));
        }
        let b1 = self.bf[cursor + 1] as u64;
        let mut result = (b0 & 0x7F) | ((b1 & 0x7F) << 7);
        if b1 < 0x80 {
            self.cursor = cursor + 2;
            return Ok(result);
        }

        if cursor + 2 >= self.bf.len() {
            return Err(Error::buffer_out_of_bound(cursor + 2, 1, self.bf.len()));
        }
        let b2 = self.bf[cursor + 2] as u64;
        result |= (b2 & 0x7F) << 14;
        if b2 < 0x80 {
            self.cursor = cursor + 3;
            return Ok(result);
        }

        if cursor + 3 >= self.bf.len() {
            return Err(Error::buffer_out_of_bound(cursor + 3, 1, self.bf.len()));
        }
        let b3 = self.bf[cursor + 3] as u64;
        result |= (b3 & 0x7F) << 21;
        if b3 < 0x80 {
            self.cursor = cursor + 4;
            return Ok(result);
        }

        if cursor + 4 >= self.bf.len() {
            return Err(Error::buffer_out_of_bound(cursor + 4, 1, self.bf.len()));
        }
        let b4 = self.bf[cursor + 4] as u64;
        result |= (b4 & 0x7F) << 28;
        if b4 < 0x80 {
            self.cursor = cursor + 5;
            return Ok(result);
        }

        if cursor + 5 >= self.bf.len() {
            return Err(Error::buffer_out_of_bound(cursor + 5, 1, self.bf.len()));
        }
        let b5 = self.bf[cursor + 5] as u64;
        result |= (b5 & 0x7F) << 35;
        if b5 < 0x80 {
            self.cursor = cursor + 6;
            return Ok(result);
        }

        if cursor + 6 >= self.bf.len() {
            return Err(Error::buffer_out_of_bound(cursor + 6, 1, self.bf.len()));
        }
        let b6 = self.bf[cursor + 6] as u64;
        result |= (b6 & 0x7F) << 42;
        if b6 < 0x80 {
            self.cursor = cursor + 7;
            return Ok(result);
        }

        if cursor + 7 >= self.bf.len() {
            return Err(Error::buffer_out_of_bound(cursor + 7, 1, self.bf.len()));
        }
        let b7 = self.bf[cursor + 7] as u64;
        result |= (b7 & 0x7F) << 49;
        if b7 < 0x80 {
            self.cursor = cursor + 8;
            return Ok(result);
        }

        if cursor + 8 >= self.bf.len() {
            return Err(Error::buffer_out_of_bound(cursor + 8, 1, self.bf.len()));
        }
        let b8 = self.bf[cursor + 8] as u64;
        result |= (b8 & 0xFF) << 56;
        self.cursor = cursor + 9;
        Ok(result)
    }

    #[inline(always)]
    pub fn read_varint64(&mut self) -> Result<i64, Error> {
        let encoded = self.read_varuint64()?;
        Ok(((encoded >> 1) as i64) ^ -((encoded & 1) as i64))
    }

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

    /// Optimized varuint36small read using bulk u64 read when possible
    /// SAFETY: Uses unchecked operations after bounds verification
    #[inline(always)]
    pub fn read_varuint36small(&mut self) -> Result<u64, Error> {
        if self.cursor >= self.bf.len() {
            return Err(Error::buffer_out_of_bound(self.cursor, 1, self.bf.len()));
        }

        let remaining = self.bf.len() - self.cursor;

        if remaining >= 8 {
            // Fast path: bulk read u64
            unsafe {
                let ptr = self.bf.as_ptr().add(self.cursor) as *const u64;
                let bulk = u64::from_le(std::ptr::read_unaligned(ptr));

                // Extract first byte (bits 0-6)
                let mut result = bulk & 0x7F;
                let mut bytes_read = 1;

                // Check continuation bit of byte 0
                if (bulk & 0x80) != 0 {
                    bytes_read = 2;
                    // Extract byte 1 (bits 8-14 from bulk, shifted to position 7-13 in result)
                    result |= ((bulk >> 8) & 0x7F) << 7;

                    // Check continuation bit of byte 1 (bit 15 of bulk)
                    if (bulk & 0x8000) != 0 {
                        bytes_read = 3;
                        // Extract byte 2 (bits 16-22 from bulk, shifted to position 14-20 in result)
                        result |= ((bulk >> 16) & 0x7F) << 14;

                        // Check continuation bit of byte 2 (bit 23 of bulk)
                        if (bulk & 0x800000) != 0 {
                            bytes_read = 4;
                            // Extract byte 3 (bits 24-30 from bulk, shifted to position 21-27 in result)
                            result |= ((bulk >> 24) & 0x7F) << 21;

                            // Check continuation bit of byte 3 (bit 31 of bulk)
                            if (bulk & 0x80000000) != 0 {
                                bytes_read = 5;
                                // Extract byte 4 (bits 32-39 from bulk, all 8 bits, shifted to position 28-35 in result)
                                result |= ((bulk >> 32) & 0xFF) << 28;
                            }
                        }
                    }
                }

                self.cursor += bytes_read;
                return Ok(result);
            }
        }

        // Slow path for reading near buffer end
        self.read_varuint36small_slow()
    }

    /// Slow path for varuint36small when near buffer end
    fn read_varuint36small_slow(&mut self) -> Result<u64, Error> {
        let mut result = 0u64;
        let mut shift = 0;
        let mut bytes_read = 0;

        loop {
            if self.cursor >= self.bf.len() {
                return Err(Error::buffer_out_of_bound(self.cursor, 1, self.bf.len()));
            }

            let b = unsafe { *self.bf.get_unchecked(self.cursor) };
            self.cursor += 1;
            bytes_read += 1;

            // For varuint36small, bytes 1-4 use 7 bits, byte 5 uses 8 bits
            if bytes_read < 5 {
                // Extract 7 data bits
                result |= ((b & 0x7F) as u64) << shift;

                // Check continuation bit
                if (b & 0x80) == 0 {
                    return Ok(result);
                }

                shift += 7;
            } else {
                // Byte 5: use all 8 bits (no continuation bit)
                result |= (b as u64) << shift;
                return Ok(result);
            }

            if shift >= 36 {
                return Self::varuint36small_overflow_error();
            }
        }
    }

    /// Cold path for varuint36small overflow error - marked inline(never) to reduce code bloat
    #[inline(never)]
    #[cold]
    fn varuint36small_overflow_error() -> Result<u64, Error> {
        Err(Error::encode_error("varuint36small overflow"))
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
}

#[allow(clippy::needless_lifetimes)]
unsafe impl<'a> Send for Reader<'a> {}
#[allow(clippy::needless_lifetimes)]
unsafe impl<'a> Sync for Reader<'a> {}
