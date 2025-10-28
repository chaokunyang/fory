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

#include "fory/serialization/serializer.h"
#include <cstdint>
#include <map>
#include <unordered_map>

namespace fory {
namespace serialization {

// ============================================================================
// Map KV Header
// ============================================================================

/// Map key-value header encoding per xlang spec.
///
/// Similar to collection header but encodes metadata for both keys and values.
/// Uses 6 bits total (3 bits per key, 3 bits per value).
///
/// Header bits (1 byte):
/// - Bit 0 (0b000001): key_track_ref
/// - Bit 1 (0b000010): key_has_null
/// - Bit 2 (0b000100): key_is_declared_type
/// - Bit 3 (0b001000): value_track_ref
/// - Bit 4 (0b010000): value_has_null
/// - Bit 5 (0b100000): value_is_declared_type
///
/// Example:
/// - 0b000100: Keys are declared type, values may vary
/// - 0b100100: Both keys and values are declared types (most common)
struct MapKVHeader {
  bool key_track_ref;
  bool key_has_null;
  bool key_is_declared_type;
  bool value_track_ref;
  bool value_has_null;
  bool value_is_declared_type;

  /// Encode header to single byte
  inline uint8_t encode() const {
    uint8_t header = 0;
    if (key_track_ref)
      header |= 0b000001;
    if (key_has_null)
      header |= 0b000010;
    if (key_is_declared_type)
      header |= 0b000100;
    if (value_track_ref)
      header |= 0b001000;
    if (value_has_null)
      header |= 0b010000;
    if (value_is_declared_type)
      header |= 0b100000;
    return header;
  }

  /// Decode header from single byte
  static inline MapKVHeader decode(uint8_t byte) {
    return {
        .key_track_ref = (byte & 0b000001) != 0,
        .key_has_null = (byte & 0b000010) != 0,
        .key_is_declared_type = (byte & 0b000100) != 0,
        .value_track_ref = (byte & 0b001000) != 0,
        .value_has_null = (byte & 0b010000) != 0,
        .value_is_declared_type = (byte & 0b100000) != 0,
    };
  }

  /// Create default header for non-polymorphic maps
  /// (most common: map<string, int>, map<int, string>, etc.)
  static inline MapKVHeader default_header(bool track_ref) {
    return {
        .key_track_ref = track_ref,
        .key_has_null = false,
        .key_is_declared_type = true,
        .value_track_ref = track_ref,
        .value_has_null = false,
        .value_is_declared_type = true,
    };
  }
};

// ============================================================================
// std::map serializer
// ============================================================================

template <typename K, typename V, typename... Args>
struct Serializer<std::map<K, V, Args...>> {
  static constexpr TypeId type_id = TypeId::MAP;

  static inline Result<void, Error> write(const std::map<K, V, Args...> &map,
                                          WriteContext &ctx, bool write_ref,
                                          bool write_type) {
    write_not_null_ref_flag(ctx, write_ref);

    if (write_type) {
      ctx.write_uint8(static_cast<uint8_t>(type_id));
    }

    // Write map size
    ctx.write_varuint32(static_cast<uint32_t>(map.size()));

    // Determine if we can use generics optimization
    bool has_generics = is_generic_type_v<K> || is_generic_type_v<V>;

    // Write key-value pairs
    for (const auto &[key, value] : map) {
      if (has_generics) {
        FORY_RETURN_NOT_OK(
            Serializer<K>::write_data_generic(key, ctx, has_generics));
        FORY_RETURN_NOT_OK(
            Serializer<V>::write_data_generic(value, ctx, has_generics));
      } else {
        FORY_RETURN_NOT_OK(Serializer<K>::write(key, ctx, false, false));
        FORY_RETURN_NOT_OK(Serializer<V>::write(value, ctx, false, false));
      }
    }

    return Result<void, Error>();
  }

  static inline Result<void, Error>
  write_data(const std::map<K, V, Args...> &map, WriteContext &ctx) {
    ctx.write_varuint32(static_cast<uint32_t>(map.size()));
    for (const auto &[key, value] : map) {
      FORY_RETURN_NOT_OK(Serializer<K>::write_data(key, ctx));
      FORY_RETURN_NOT_OK(Serializer<V>::write_data(value, ctx));
    }
    return Result<void, Error>();
  }

  static inline Result<void, Error>
  write_data_generic(const std::map<K, V, Args...> &map, WriteContext &ctx,
                     bool has_generics) {
    ctx.write_varuint32(static_cast<uint32_t>(map.size()));
    for (const auto &[key, value] : map) {
      if (has_generics) {
        FORY_RETURN_NOT_OK(
            Serializer<K>::write_data_generic(key, ctx, has_generics));
        FORY_RETURN_NOT_OK(
            Serializer<V>::write_data_generic(value, ctx, has_generics));
      } else {
        FORY_RETURN_NOT_OK(Serializer<K>::write_data(key, ctx));
        FORY_RETURN_NOT_OK(Serializer<V>::write_data(value, ctx));
      }
    }
    return Result<void, Error>();
  }

  static inline Result<std::map<K, V, Args...>, Error>
  read(ReadContext &ctx, bool read_ref, bool read_type) {
    auto has_value_result = consume_ref_flag(ctx, read_ref);
    if (!has_value_result.ok()) {
      return Unexpected(std::move(has_value_result).error());
    }
    if (!has_value_result.value()) {
      return std::map<K, V, Args...>();
    }

    // Read type info
    if (read_type) {
      auto type_byte_result = ctx.read_uint8();
      if (!type_byte_result.ok()) {
        return Unexpected(std::move(type_byte_result).error());
      }
      uint8_t type_byte = type_byte_result.value();
      if (type_byte != static_cast<uint8_t>(type_id)) {
        return Unexpected(
            Error::type_mismatch(type_byte, static_cast<uint8_t>(type_id)));
      }
    }

    // Read map size
    auto size_result = ctx.read_varuint32();
    if (!size_result.ok()) {
      return Unexpected(std::move(size_result).error());
    }
    uint32_t size = size_result.value();

    // Read key-value pairs
    std::map<K, V, Args...> result;
    for (uint32_t i = 0; i < size; ++i) {
      auto key_result = Serializer<K>::read(ctx, false, false);
      if (!key_result.ok()) {
        return Unexpected(std::move(key_result).error());
      }
      auto value_result = Serializer<V>::read(ctx, false, false);
      if (!value_result.ok()) {
        return Unexpected(std::move(value_result).error());
      }
      result.emplace(std::move(key_result).value(),
                     std::move(value_result).value());
    }

    return result;
  }

  static inline Result<std::map<K, V, Args...>, Error>
  read_data(ReadContext &ctx) {
    auto size_result = ctx.read_varuint32();
    if (!size_result.ok()) {
      return Unexpected(std::move(size_result).error());
    }
    uint32_t size = size_result.value();
    std::map<K, V, Args...> result;
    for (uint32_t i = 0; i < size; ++i) {
      auto key_result = Serializer<K>::read_data(ctx);
      if (!key_result.ok()) {
        return Unexpected(std::move(key_result).error());
      }
      auto value_result = Serializer<V>::read_data(ctx);
      if (!value_result.ok()) {
        return Unexpected(std::move(value_result).error());
      }
      result.emplace(std::move(key_result).value(),
                     std::move(value_result).value());
    }
    return result;
  }
};

// ============================================================================
// std::unordered_map serializer
// ============================================================================

template <typename K, typename V, typename... Args>
struct Serializer<std::unordered_map<K, V, Args...>> {
  static constexpr TypeId type_id = TypeId::MAP;

  static inline Result<void, Error>
  write(const std::unordered_map<K, V, Args...> &map, WriteContext &ctx,
        bool write_ref, bool write_type) {
    write_not_null_ref_flag(ctx, write_ref);

    if (write_type) {
      ctx.write_uint8(static_cast<uint8_t>(type_id));
    }

    // Write map size
    ctx.write_varuint32(static_cast<uint32_t>(map.size()));

    // Determine if we can use generics optimization
    bool has_generics = is_generic_type_v<K> || is_generic_type_v<V>;

    // Write key-value pairs
    for (const auto &[key, value] : map) {
      if (has_generics) {
        FORY_RETURN_NOT_OK(
            Serializer<K>::write_data_generic(key, ctx, has_generics));
        FORY_RETURN_NOT_OK(
            Serializer<V>::write_data_generic(value, ctx, has_generics));
      } else {
        FORY_RETURN_NOT_OK(Serializer<K>::write(key, ctx, false, false));
        FORY_RETURN_NOT_OK(Serializer<V>::write(value, ctx, false, false));
      }
    }

    return Result<void, Error>();
  }

  static inline Result<void, Error>
  write_data(const std::unordered_map<K, V, Args...> &map, WriteContext &ctx) {
    ctx.write_varuint32(static_cast<uint32_t>(map.size()));
    for (const auto &[key, value] : map) {
      FORY_RETURN_NOT_OK(Serializer<K>::write_data(key, ctx));
      FORY_RETURN_NOT_OK(Serializer<V>::write_data(value, ctx));
    }
    return Result<void, Error>();
  }

  static inline Result<void, Error>
  write_data_generic(const std::unordered_map<K, V, Args...> &map,
                     WriteContext &ctx, bool has_generics) {
    ctx.write_varuint32(static_cast<uint32_t>(map.size()));
    for (const auto &[key, value] : map) {
      if (has_generics) {
        FORY_RETURN_NOT_OK(
            Serializer<K>::write_data_generic(key, ctx, has_generics));
        FORY_RETURN_NOT_OK(
            Serializer<V>::write_data_generic(value, ctx, has_generics));
      } else {
        FORY_RETURN_NOT_OK(Serializer<K>::write_data(key, ctx));
        FORY_RETURN_NOT_OK(Serializer<V>::write_data(value, ctx));
      }
    }
    return Result<void, Error>();
  }

  static inline Result<std::unordered_map<K, V, Args...>, Error>
  read(ReadContext &ctx, bool read_ref, bool read_type) {
    auto has_value_result = consume_ref_flag(ctx, read_ref);
    if (!has_value_result.ok()) {
      return Unexpected(std::move(has_value_result).error());
    }
    if (!has_value_result.value()) {
      return std::unordered_map<K, V, Args...>();
    }

    // Read type info
    if (read_type) {
      auto type_byte_result = ctx.read_uint8();
      if (!type_byte_result.ok()) {
        return Unexpected(std::move(type_byte_result).error());
      }
      uint8_t type_byte = type_byte_result.value();
      if (type_byte != static_cast<uint8_t>(type_id)) {
        return Unexpected(
            Error::type_mismatch(type_byte, static_cast<uint8_t>(type_id)));
      }
    }

    // Read map size
    auto size_result = ctx.read_varuint32();
    if (!size_result.ok()) {
      return Unexpected(std::move(size_result).error());
    }
    uint32_t size = size_result.value();

    // Read key-value pairs
    std::unordered_map<K, V, Args...> result;
    result.reserve(size);
    for (uint32_t i = 0; i < size; ++i) {
      auto key_result = Serializer<K>::read(ctx, false, false);
      if (!key_result.ok()) {
        return Unexpected(std::move(key_result).error());
      }
      auto value_result = Serializer<V>::read(ctx, false, false);
      if (!value_result.ok()) {
        return Unexpected(std::move(value_result).error());
      }
      result.emplace(std::move(key_result).value(),
                     std::move(value_result).value());
    }

    return result;
  }

  static inline Result<std::unordered_map<K, V, Args...>, Error>
  read_data(ReadContext &ctx) {
    auto size_result = ctx.read_varuint32();
    if (!size_result.ok()) {
      return Unexpected(std::move(size_result).error());
    }
    uint32_t size = size_result.value();
    std::unordered_map<K, V, Args...> result;
    result.reserve(size);
    for (uint32_t i = 0; i < size; ++i) {
      auto key_result = Serializer<K>::read_data(ctx);
      if (!key_result.ok()) {
        return Unexpected(std::move(key_result).error());
      }
      auto value_result = Serializer<V>::read_data(ctx);
      if (!value_result.ok()) {
        return Unexpected(std::move(value_result).error());
      }
      result.emplace(std::move(key_result).value(),
                     std::move(value_result).value());
    }
    return result;
  }
};

} // namespace serialization
} // namespace fory
