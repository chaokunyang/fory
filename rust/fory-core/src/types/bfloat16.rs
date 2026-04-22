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

//! IEEE 754 bfloat16 floating-point type.
//!
//! This module provides a `bfloat16` type that stores an IEEE 754 bfloat16
//! payload exactly. The type is a transparent wrapper around `u16` and
//! provides round-to-nearest-even conversion from `f32`, exact expansion back
//! to `f32`, classification helpers, and arithmetic through `f32`.
//!
//! The type is re-exported from `fory_core` as `BFloat16`, and `Vec<bfloat16>`
//! / `Vec<BFloat16>` is the canonical dense carrier for xlang `bfloat16_array`
//! payloads.

use std::cmp::Ordering;
use std::fmt;
use std::hash::{Hash, Hasher};
use std::ops::{Add, Div, Mul, Neg, Sub};

#[repr(transparent)]
#[derive(Copy, Clone, Default)]
#[allow(non_camel_case_types)]
pub struct bfloat16(u16);

const SIGN_MASK: u16 = 0x8000;
const EXP_MASK: u16 = 0x7F80;
const MANTISSA_MASK: u16 = 0x007F;
const INFINITY_BITS: u16 = 0x7F80;
const NEG_INFINITY_BITS: u16 = 0xFF80;
const QUIET_NAN_BITS: u16 = 0x7FC0;

impl bfloat16 {
    #[inline(always)]
    pub const fn from_bits(bits: u16) -> Self {
        Self(bits)
    }

    #[inline(always)]
    pub const fn to_bits(self) -> u16 {
        self.0
    }

    pub const ZERO: Self = Self(0x0000);
    pub const NEG_ZERO: Self = Self(0x8000);
    pub const INFINITY: Self = Self(INFINITY_BITS);
    pub const NEG_INFINITY: Self = Self(NEG_INFINITY_BITS);
    pub const NAN: Self = Self(QUIET_NAN_BITS);
    pub const MAX: Self = Self(0x7F7F);
    pub const MIN_POSITIVE: Self = Self(0x0080);
    pub const MIN_POSITIVE_SUBNORMAL: Self = Self(0x0001);

    #[inline(always)]
    pub fn from_f32(value: f32) -> Self {
        let bits = value.to_bits();
        if (bits & 0x7F80_0000) == 0x7F80_0000 && (bits & 0x007F_FFFF) != 0 {
            return Self(QUIET_NAN_BITS);
        }
        let lsb = (bits >> 16) & 1;
        let rounding_bias = 0x7FFF + lsb;
        Self(((bits + rounding_bias) >> 16) as u16)
    }

    #[inline(always)]
    pub fn to_f32(self) -> f32 {
        f32::from_bits((self.0 as u32) << 16)
    }

    #[inline(always)]
    pub fn is_nan(self) -> bool {
        (self.0 & EXP_MASK) == EXP_MASK && (self.0 & MANTISSA_MASK) != 0
    }

    #[inline(always)]
    pub fn is_infinite(self) -> bool {
        (self.0 & EXP_MASK) == EXP_MASK && (self.0 & MANTISSA_MASK) == 0
    }

    #[inline(always)]
    pub fn is_finite(self) -> bool {
        (self.0 & EXP_MASK) != EXP_MASK
    }

    #[inline(always)]
    pub fn is_zero(self) -> bool {
        (self.0 & !SIGN_MASK) == 0
    }

    #[inline(always)]
    pub fn is_normal(self) -> bool {
        let exp = self.0 & EXP_MASK;
        exp != 0 && exp != EXP_MASK
    }

    #[inline(always)]
    pub fn is_subnormal(self) -> bool {
        (self.0 & EXP_MASK) == 0 && (self.0 & MANTISSA_MASK) != 0
    }

    #[inline(always)]
    pub fn is_sign_negative(self) -> bool {
        (self.0 & SIGN_MASK) != 0
    }

    #[inline(always)]
    pub fn eq_value(self, other: Self) -> bool {
        if self.is_nan() || other.is_nan() {
            return false;
        }
        if self.is_zero() && other.is_zero() {
            return true;
        }
        self.0 == other.0
    }

    /// Add two `bfloat16` values (via `f32`).
    #[inline(always)]
    #[allow(clippy::should_implement_trait)]
    pub fn add(self, rhs: Self) -> Self {
        Self::from_f32(self.to_f32() + rhs.to_f32())
    }

    /// Subtract two `bfloat16` values (via `f32`).
    #[inline(always)]
    #[allow(clippy::should_implement_trait)]
    pub fn sub(self, rhs: Self) -> Self {
        Self::from_f32(self.to_f32() - rhs.to_f32())
    }

    /// Multiply two `bfloat16` values (via `f32`).
    #[inline(always)]
    #[allow(clippy::should_implement_trait)]
    pub fn mul(self, rhs: Self) -> Self {
        Self::from_f32(self.to_f32() * rhs.to_f32())
    }

    /// Divide two `bfloat16` values (via `f32`).
    #[inline(always)]
    #[allow(clippy::should_implement_trait)]
    pub fn div(self, rhs: Self) -> Self {
        Self::from_f32(self.to_f32() / rhs.to_f32())
    }

    /// Negate this `bfloat16` value.
    #[inline(always)]
    #[allow(clippy::should_implement_trait)]
    pub fn neg(self) -> Self {
        Self(self.0 ^ SIGN_MASK)
    }

    /// Absolute value.
    #[inline(always)]
    pub fn abs(self) -> Self {
        Self(self.0 & !SIGN_MASK)
    }
}

impl PartialEq for bfloat16 {
    #[inline]
    fn eq(&self, other: &Self) -> bool {
        self.0 == other.0
    }
}

impl Eq for bfloat16 {}

impl Hash for bfloat16 {
    #[inline]
    fn hash<H: Hasher>(&self, state: &mut H) {
        self.0.hash(state);
    }
}

impl PartialOrd for bfloat16 {
    #[inline]
    fn partial_cmp(&self, other: &Self) -> Option<Ordering> {
        self.to_f32().partial_cmp(&other.to_f32())
    }
}

impl Add for bfloat16 {
    type Output = Self;
    #[inline]
    fn add(self, rhs: Self) -> Self {
        Self::add(self, rhs)
    }
}

impl Sub for bfloat16 {
    type Output = Self;
    #[inline]
    fn sub(self, rhs: Self) -> Self {
        Self::sub(self, rhs)
    }
}

impl Mul for bfloat16 {
    type Output = Self;
    #[inline]
    fn mul(self, rhs: Self) -> Self {
        Self::mul(self, rhs)
    }
}

impl Div for bfloat16 {
    type Output = Self;
    #[inline]
    fn div(self, rhs: Self) -> Self {
        Self::div(self, rhs)
    }
}

impl Neg for bfloat16 {
    type Output = Self;
    #[inline]
    fn neg(self) -> Self {
        Self::neg(self)
    }
}

impl fmt::Display for bfloat16 {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}", self.to_f32())
    }
}

impl fmt::Debug for bfloat16 {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "bfloat16({})", self.to_f32())
    }
}

#[cfg(test)]
mod tests {
    use super::bfloat16;

    #[test]
    fn test_basic_conversion() {
        assert_eq!(bfloat16::from_f32(1.0).to_bits(), 0x3F80);
        assert_eq!(bfloat16::from_f32(-1.0).to_bits(), 0xBF80);
        assert_eq!(bfloat16::from_f32(0.0), bfloat16::ZERO);
        assert_eq!(bfloat16::from_f32(-0.0), bfloat16::NEG_ZERO);
    }

    #[test]
    fn test_special_values() {
        assert_eq!(bfloat16::INFINITY.to_bits(), 0x7F80);
        assert_eq!(bfloat16::NEG_INFINITY.to_bits(), 0xFF80);
        assert_eq!(bfloat16::NAN.to_bits(), 0x7FC0);
        assert!(bfloat16::from_f32(f32::NAN).is_nan());
    }

    #[test]
    fn test_round_to_nearest_even() {
        assert_eq!(bfloat16::from_f32(1.0 + 1.0 / 256.0).to_bits(), 0x3F80);
        assert_eq!(bfloat16::from_f32(1.0 + 3.0 / 256.0).to_bits(), 0x3F82);
    }
}
