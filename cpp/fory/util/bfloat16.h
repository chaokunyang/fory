/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

#pragma once

#include <cmath>
#include <cstdint>
#include <cstring>
#include <string>
#include <type_traits>

namespace fory {

/// Public carrier for xlang bfloat16 values.
///
/// Use `from_bits()` and `to_bits()` for exact wire control, or `from_float()`
/// and `to_float()` for numeric conversion. The canonical dense array carrier
/// is `std::vector<bfloat16_t>`.
struct bfloat16_t {
  uint16_t bits;

  [[nodiscard]] uint16_t to_bits() const noexcept { return bits; }
  [[nodiscard]] static bfloat16_t from_bits(uint16_t bits) noexcept {
    return bfloat16_t{bits};
  }

  [[nodiscard]] float to_float() const noexcept {
    const uint32_t raw = static_cast<uint32_t>(bits) << 16;
    float value = 0.0f;
    std::memcpy(&value, &raw, sizeof(value));
    return value;
  }

  [[nodiscard]] static bfloat16_t from_float(float value) noexcept {
    uint32_t raw = 0;
    std::memcpy(&raw, &value, sizeof(raw));
    if ((raw & 0x7F800000u) == 0x7F800000u && (raw & 0x007FFFFFu) != 0u) {
      return from_bits(0x7FC0u);
    }
    const uint32_t lsb = (raw >> 16) & 1u;
    const uint32_t rounding_bias = 0x7FFFu + lsb;
    return from_bits(static_cast<uint16_t>((raw + rounding_bias) >> 16));
  }

  [[nodiscard]] static bool is_nan(bfloat16_t v) noexcept {
    return (v.bits & 0x7F80u) == 0x7F80u && (v.bits & 0x007Fu) != 0u;
  }
  [[nodiscard]] static bool is_inf(bfloat16_t v) noexcept {
    return (v.bits & 0x7FFFu) == 0x7F80u;
  }
  [[nodiscard]] static bool is_inf(bfloat16_t v, int sign) noexcept {
    if (sign == 0) {
      return is_inf(v);
    }
    return sign > 0 ? v.bits == 0x7F80u : v.bits == 0xFF80u;
  }
  [[nodiscard]] static bool is_zero(bfloat16_t v) noexcept {
    return (v.bits & 0x7FFFu) == 0u;
  }
  [[nodiscard]] static bool signbit(bfloat16_t v) noexcept {
    return (v.bits & 0x8000u) != 0u;
  }
  [[nodiscard]] static bool is_subnormal(bfloat16_t v) noexcept {
    return (v.bits & 0x7F80u) == 0u && (v.bits & 0x007Fu) != 0u;
  }
  [[nodiscard]] static bool is_normal(bfloat16_t v) noexcept {
    const uint16_t exp = v.bits & 0x7F80u;
    return exp != 0u && exp != 0x7F80u;
  }
  [[nodiscard]] static bool is_finite(bfloat16_t v) noexcept {
    return (v.bits & 0x7F80u) != 0x7F80u;
  }
  [[nodiscard]] static bool equal(bfloat16_t a, bfloat16_t b) noexcept {
    if (is_nan(a) || is_nan(b)) {
      return false;
    }
    if (is_zero(a) && is_zero(b)) {
      return true;
    }
    return a.bits == b.bits;
  }
  [[nodiscard]] static bool less(bfloat16_t a, bfloat16_t b) noexcept {
    if (is_nan(a) || is_nan(b)) {
      return false;
    }
    if (is_zero(a) && is_zero(b)) {
      return false;
    }
    const bool neg_a = signbit(a);
    const bool neg_b = signbit(b);
    if (neg_a != neg_b) {
      return neg_a;
    }
    return neg_a ? a.bits > b.bits : a.bits < b.bits;
  }
  [[nodiscard]] static bool less_eq(bfloat16_t a, bfloat16_t b) noexcept {
    return equal(a, b) || less(a, b);
  }
  [[nodiscard]] static bool greater(bfloat16_t a, bfloat16_t b) noexcept {
    return less(b, a);
  }
  [[nodiscard]] static bool greater_eq(bfloat16_t a, bfloat16_t b) noexcept {
    return equal(a, b) || greater(a, b);
  }
  [[nodiscard]] static int compare(bfloat16_t a, bfloat16_t b) noexcept {
    if (is_nan(a) || is_nan(b)) {
      return 0;
    }
    if (equal(a, b)) {
      return 0;
    }
    return less(a, b) ? -1 : 1;
  }
  [[nodiscard]] static std::string to_string(bfloat16_t v) {
    return std::to_string(v.to_float());
  }
  [[nodiscard]] static bfloat16_t add(bfloat16_t a, bfloat16_t b) noexcept {
    return from_float(a.to_float() + b.to_float());
  }
  [[nodiscard]] static bfloat16_t sub(bfloat16_t a, bfloat16_t b) noexcept {
    return from_float(a.to_float() - b.to_float());
  }
  [[nodiscard]] static bfloat16_t mul(bfloat16_t a, bfloat16_t b) noexcept {
    return from_float(a.to_float() * b.to_float());
  }
  [[nodiscard]] static bfloat16_t div(bfloat16_t a, bfloat16_t b) noexcept {
    return from_float(a.to_float() / b.to_float());
  }
  [[nodiscard]] static bfloat16_t neg(bfloat16_t a) noexcept {
    return from_bits(static_cast<uint16_t>(a.bits ^ 0x8000u));
  }
  [[nodiscard]] static bfloat16_t abs(bfloat16_t a) noexcept {
    return from_bits(static_cast<uint16_t>(a.bits & 0x7FFFu));
  }

  bfloat16_t &operator+=(bfloat16_t rhs) noexcept {
    *this = add(*this, rhs);
    return *this;
  }
  bfloat16_t &operator-=(bfloat16_t rhs) noexcept {
    *this = sub(*this, rhs);
    return *this;
  }
  bfloat16_t &operator*=(bfloat16_t rhs) noexcept {
    *this = mul(*this, rhs);
    return *this;
  }
  bfloat16_t &operator/=(bfloat16_t rhs) noexcept {
    *this = div(*this, rhs);
    return *this;
  }
};

static_assert(sizeof(bfloat16_t) == 2);
static_assert(std::is_trivial_v<bfloat16_t>);
static_assert(std::is_standard_layout_v<bfloat16_t>);

[[nodiscard]] inline bfloat16_t operator+(bfloat16_t a, bfloat16_t b) noexcept {
  return bfloat16_t::add(a, b);
}
[[nodiscard]] inline bfloat16_t operator-(bfloat16_t a, bfloat16_t b) noexcept {
  return bfloat16_t::sub(a, b);
}
[[nodiscard]] inline bfloat16_t operator*(bfloat16_t a, bfloat16_t b) noexcept {
  return bfloat16_t::mul(a, b);
}
[[nodiscard]] inline bfloat16_t operator/(bfloat16_t a, bfloat16_t b) noexcept {
  return bfloat16_t::div(a, b);
}
[[nodiscard]] inline bfloat16_t operator-(bfloat16_t a) noexcept {
  return bfloat16_t::neg(a);
}
[[nodiscard]] inline bfloat16_t operator+(bfloat16_t a) noexcept { return a; }
[[nodiscard]] inline bool operator==(bfloat16_t a, bfloat16_t b) noexcept {
  return bfloat16_t::equal(a, b);
}
[[nodiscard]] inline bool operator!=(bfloat16_t a, bfloat16_t b) noexcept {
  return !bfloat16_t::equal(a, b);
}
[[nodiscard]] inline bool operator<(bfloat16_t a, bfloat16_t b) noexcept {
  return bfloat16_t::less(a, b);
}
[[nodiscard]] inline bool operator<=(bfloat16_t a, bfloat16_t b) noexcept {
  return bfloat16_t::less_eq(a, b);
}
[[nodiscard]] inline bool operator>(bfloat16_t a, bfloat16_t b) noexcept {
  return bfloat16_t::greater(a, b);
}
[[nodiscard]] inline bool operator>=(bfloat16_t a, bfloat16_t b) noexcept {
  return bfloat16_t::greater_eq(a, b);
}

} // namespace fory

namespace std {
template <> struct hash<fory::bfloat16_t> {
  size_t operator()(fory::bfloat16_t v) const noexcept {
    uint16_t bits = fory::bfloat16_t::is_zero(v) ? 0u : v.to_bits();
    return std::hash<uint16_t>{}(bits);
  }
};
} // namespace std
