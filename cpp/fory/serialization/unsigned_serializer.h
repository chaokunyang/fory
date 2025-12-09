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

#include "fory/serialization/context.h"
#include "fory/serialization/serializer_traits.h"
#include "fory/type/type.h"
#include "fory/util/error.h"
#include <array>
#include <cstdint>
#include <vector>

namespace fory {
namespace serialization {

// ============================================================================
// Unsigned Integer Type Serializers (xlang=false mode only)
// These serializers use distinct TypeIds for unsigned types.
// In xlang mode, unsigned types are not supported per the xlang spec.
// ============================================================================

/// uint8_t serializer
/// Note: uint8_t uses INT8 type id for xlang mode and U8 for native mode.
/// The type_id constant is provided for compatibility with FieldTypeBuilder.
template <> struct Serializer<uint8_t> {
  static constexpr TypeId type_id = TypeId::INT8;
  static constexpr TypeId xlang_type_id = TypeId::INT8;
  static constexpr TypeId native_type_id = TypeId::U8;

  static inline TypeId get_type_id(bool is_xlang) {
    return is_xlang ? xlang_type_id : native_type_id;
  }

  static inline void write_type_info(WriteContext &ctx) {
    TypeId tid = get_type_id(ctx.is_xlang());
    ctx.write_varuint32(static_cast<uint32_t>(tid));
  }

  static inline void read_type_info(ReadContext &ctx) {
    uint32_t actual = ctx.read_varuint32(ctx.error());
    if (FORY_PREDICT_FALSE(ctx.has_error())) {
      return;
    }
    TypeId expected = get_type_id(ctx.is_xlang());
    if (actual != static_cast<uint32_t>(expected)) {
      ctx.set_error(
          Error::type_mismatch(actual, static_cast<uint32_t>(expected)));
    }
  }

  static inline void write(uint8_t value, WriteContext &ctx, bool write_ref,
                           bool write_type, bool has_generics = false) {
    write_not_null_ref_flag(ctx, write_ref);
    if (write_type) {
      write_type_info(ctx);
    }
    write_data(value, ctx);
  }

  static inline void write_data(uint8_t value, WriteContext &ctx) {
    ctx.write_uint8(value);
  }

  static inline void write_data_generic(uint8_t value, WriteContext &ctx,
                                        bool has_generics) {
    write_data(value, ctx);
  }

  static inline uint8_t read(ReadContext &ctx, bool read_ref, bool read_type) {
    bool has_value = consume_ref_flag(ctx, read_ref);
    if (ctx.has_error() || !has_value) {
      return 0;
    }
    if (read_type) {
      uint32_t type_id_read = ctx.read_varuint32(ctx.error());
      if (FORY_PREDICT_FALSE(ctx.has_error())) {
        return 0;
      }
      TypeId expected = get_type_id(ctx.is_xlang());
      if (type_id_read != static_cast<uint32_t>(expected)) {
        ctx.set_error(Error::type_mismatch(type_id_read,
                                           static_cast<uint32_t>(expected)));
        return 0;
      }
    }
    return ctx.read_uint8(ctx.error());
  }

  static inline uint8_t read_data(ReadContext &ctx) {
    return ctx.read_uint8(ctx.error());
  }

  static inline uint8_t read_data_generic(ReadContext &ctx, bool has_generics) {
    return read_data(ctx);
  }

  static inline uint8_t read_with_type_info(ReadContext &ctx, bool read_ref,
                                            const TypeInfo &type_info) {
    return read(ctx, read_ref, false);
  }
};

/// uint16_t serializer
template <> struct Serializer<uint16_t> {
  static constexpr TypeId type_id = TypeId::INT16;
  static constexpr TypeId xlang_type_id = TypeId::INT16;
  static constexpr TypeId native_type_id = TypeId::U16;

  static inline TypeId get_type_id(bool is_xlang) {
    return is_xlang ? xlang_type_id : native_type_id;
  }

  static inline void write_type_info(WriteContext &ctx) {
    TypeId tid = get_type_id(ctx.is_xlang());
    ctx.write_varuint32(static_cast<uint32_t>(tid));
  }

  static inline void read_type_info(ReadContext &ctx) {
    uint32_t actual = ctx.read_varuint32(ctx.error());
    if (FORY_PREDICT_FALSE(ctx.has_error())) {
      return;
    }
    TypeId expected = get_type_id(ctx.is_xlang());
    if (actual != static_cast<uint32_t>(expected)) {
      ctx.set_error(
          Error::type_mismatch(actual, static_cast<uint32_t>(expected)));
    }
  }

  static inline void write(uint16_t value, WriteContext &ctx, bool write_ref,
                           bool write_type, bool has_generics = false) {
    write_not_null_ref_flag(ctx, write_ref);
    if (write_type) {
      write_type_info(ctx);
    }
    write_data(value, ctx);
  }

  static inline void write_data(uint16_t value, WriteContext &ctx) {
    ctx.write_bytes(&value, sizeof(uint16_t));
  }

  static inline void write_data_generic(uint16_t value, WriteContext &ctx,
                                        bool has_generics) {
    write_data(value, ctx);
  }

  static inline uint16_t read(ReadContext &ctx, bool read_ref, bool read_type) {
    bool has_value = consume_ref_flag(ctx, read_ref);
    if (ctx.has_error() || !has_value) {
      return 0;
    }
    if (read_type) {
      uint32_t type_id_read = ctx.read_varuint32(ctx.error());
      if (FORY_PREDICT_FALSE(ctx.has_error())) {
        return 0;
      }
      TypeId expected = get_type_id(ctx.is_xlang());
      if (type_id_read != static_cast<uint32_t>(expected)) {
        ctx.set_error(Error::type_mismatch(type_id_read,
                                           static_cast<uint32_t>(expected)));
        return 0;
      }
    }
    return ctx.read_uint16(ctx.error());
  }

  static inline uint16_t read_data(ReadContext &ctx) {
    return ctx.read_uint16(ctx.error());
  }

  static inline uint16_t read_data_generic(ReadContext &ctx,
                                           bool has_generics) {
    return read_data(ctx);
  }

  static inline uint16_t read_with_type_info(ReadContext &ctx, bool read_ref,
                                             const TypeInfo &type_info) {
    return read(ctx, read_ref, false);
  }
};

/// uint32_t serializer
template <> struct Serializer<uint32_t> {
  static constexpr TypeId type_id = TypeId::INT32;
  static constexpr TypeId xlang_type_id = TypeId::INT32;
  static constexpr TypeId native_type_id = TypeId::U32;

  static inline TypeId get_type_id(bool is_xlang) {
    return is_xlang ? xlang_type_id : native_type_id;
  }

  static inline void write_type_info(WriteContext &ctx) {
    TypeId tid = get_type_id(ctx.is_xlang());
    ctx.write_varuint32(static_cast<uint32_t>(tid));
  }

  static inline void read_type_info(ReadContext &ctx) {
    uint32_t actual = ctx.read_varuint32(ctx.error());
    if (FORY_PREDICT_FALSE(ctx.has_error())) {
      return;
    }
    TypeId expected = get_type_id(ctx.is_xlang());
    if (actual != static_cast<uint32_t>(expected)) {
      ctx.set_error(
          Error::type_mismatch(actual, static_cast<uint32_t>(expected)));
    }
  }

  static inline void write(uint32_t value, WriteContext &ctx, bool write_ref,
                           bool write_type, bool has_generics = false) {
    write_not_null_ref_flag(ctx, write_ref);
    if (write_type) {
      write_type_info(ctx);
    }
    write_data(value, ctx);
  }

  static inline void write_data(uint32_t value, WriteContext &ctx) {
    ctx.write_bytes(&value, sizeof(uint32_t));
  }

  static inline void write_data_generic(uint32_t value, WriteContext &ctx,
                                        bool has_generics) {
    write_data(value, ctx);
  }

  static inline uint32_t read(ReadContext &ctx, bool read_ref, bool read_type) {
    bool has_value = consume_ref_flag(ctx, read_ref);
    if (ctx.has_error() || !has_value) {
      return 0;
    }
    if (read_type) {
      uint32_t type_id_read = ctx.read_varuint32(ctx.error());
      if (FORY_PREDICT_FALSE(ctx.has_error())) {
        return 0;
      }
      TypeId expected = get_type_id(ctx.is_xlang());
      if (type_id_read != static_cast<uint32_t>(expected)) {
        ctx.set_error(Error::type_mismatch(type_id_read,
                                           static_cast<uint32_t>(expected)));
        return 0;
      }
    }
    return ctx.read_uint32(ctx.error());
  }

  static inline uint32_t read_data(ReadContext &ctx) {
    return ctx.read_uint32(ctx.error());
  }

  static inline uint32_t read_data_generic(ReadContext &ctx,
                                           bool has_generics) {
    return read_data(ctx);
  }

  static inline uint32_t read_with_type_info(ReadContext &ctx, bool read_ref,
                                             const TypeInfo &type_info) {
    return read(ctx, read_ref, false);
  }
};

/// uint64_t serializer
template <> struct Serializer<uint64_t> {
  static constexpr TypeId type_id = TypeId::INT64;
  static constexpr TypeId xlang_type_id = TypeId::INT64;
  static constexpr TypeId native_type_id = TypeId::U64;

  static inline TypeId get_type_id(bool is_xlang) {
    return is_xlang ? xlang_type_id : native_type_id;
  }

  static inline void write_type_info(WriteContext &ctx) {
    TypeId tid = get_type_id(ctx.is_xlang());
    ctx.write_varuint32(static_cast<uint32_t>(tid));
  }

  static inline void read_type_info(ReadContext &ctx) {
    uint32_t actual = ctx.read_varuint32(ctx.error());
    if (FORY_PREDICT_FALSE(ctx.has_error())) {
      return;
    }
    TypeId expected = get_type_id(ctx.is_xlang());
    if (actual != static_cast<uint32_t>(expected)) {
      ctx.set_error(
          Error::type_mismatch(actual, static_cast<uint32_t>(expected)));
    }
  }

  static inline void write(uint64_t value, WriteContext &ctx, bool write_ref,
                           bool write_type, bool has_generics = false) {
    write_not_null_ref_flag(ctx, write_ref);
    if (write_type) {
      write_type_info(ctx);
    }
    write_data(value, ctx);
  }

  static inline void write_data(uint64_t value, WriteContext &ctx) {
    ctx.write_bytes(&value, sizeof(uint64_t));
  }

  static inline void write_data_generic(uint64_t value, WriteContext &ctx,
                                        bool has_generics) {
    write_data(value, ctx);
  }

  static inline uint64_t read(ReadContext &ctx, bool read_ref, bool read_type) {
    bool has_value = consume_ref_flag(ctx, read_ref);
    if (ctx.has_error() || !has_value) {
      return 0;
    }
    if (read_type) {
      uint32_t type_id_read = ctx.read_varuint32(ctx.error());
      if (FORY_PREDICT_FALSE(ctx.has_error())) {
        return 0;
      }
      TypeId expected = get_type_id(ctx.is_xlang());
      if (type_id_read != static_cast<uint32_t>(expected)) {
        ctx.set_error(Error::type_mismatch(type_id_read,
                                           static_cast<uint32_t>(expected)));
        return 0;
      }
    }
    return ctx.read_uint64(ctx.error());
  }

  static inline uint64_t read_data(ReadContext &ctx) {
    return ctx.read_uint64(ctx.error());
  }

  static inline uint64_t read_data_generic(ReadContext &ctx,
                                           bool has_generics) {
    return read_data(ctx);
  }

  static inline uint64_t read_with_type_info(ReadContext &ctx, bool read_ref,
                                             const TypeInfo &type_info) {
    return read(ctx, read_ref, false);
  }
};

// ============================================================================
// Unsigned Integer Array Serializers (std::array only)
// For xlang=false mode, use distinct unsigned array TypeIds.
// For xlang=true mode, use signed array TypeIds for compatibility.
// ============================================================================

/// Helper to get array type id based on element type and xlang mode
template <typename T> struct UnsignedArrayTypeId;

template <> struct UnsignedArrayTypeId<uint8_t> {
  static constexpr TypeId xlang_type_id = TypeId::INT8_ARRAY;
  static constexpr TypeId native_type_id =
      TypeId::INT8_ARRAY; // u8 uses INT8_ARRAY (BINARY)
};

template <> struct UnsignedArrayTypeId<uint16_t> {
  static constexpr TypeId xlang_type_id = TypeId::INT16_ARRAY;
  static constexpr TypeId native_type_id = TypeId::U16_ARRAY;
};

template <> struct UnsignedArrayTypeId<uint32_t> {
  static constexpr TypeId xlang_type_id = TypeId::INT32_ARRAY;
  static constexpr TypeId native_type_id = TypeId::U32_ARRAY;
};

template <> struct UnsignedArrayTypeId<uint64_t> {
  static constexpr TypeId xlang_type_id = TypeId::INT64_ARRAY;
  static constexpr TypeId native_type_id = TypeId::U64_ARRAY;
};

/// Serializer for std::array<T, N> of unsigned integers
template <size_t N> struct Serializer<std::array<uint8_t, N>> {
  static inline TypeId get_type_id(bool is_xlang) {
    return is_xlang ? UnsignedArrayTypeId<uint8_t>::xlang_type_id
                    : UnsignedArrayTypeId<uint8_t>::native_type_id;
  }

  static inline void write_type_info(WriteContext &ctx) {
    TypeId tid = get_type_id(ctx.is_xlang());
    ctx.write_varuint32(static_cast<uint32_t>(tid));
  }

  static inline void read_type_info(ReadContext &ctx) {
    uint32_t actual = ctx.read_varuint32(ctx.error());
    if (FORY_PREDICT_FALSE(ctx.has_error())) {
      return;
    }
    TypeId expected = get_type_id(ctx.is_xlang());
    if (!type_id_matches(actual, static_cast<uint32_t>(expected))) {
      ctx.set_error(
          Error::type_mismatch(actual, static_cast<uint32_t>(expected)));
    }
  }

  static inline void write(const std::array<uint8_t, N> &arr, WriteContext &ctx,
                           bool write_ref, bool write_type,
                           bool has_generics = false) {
    write_not_null_ref_flag(ctx, write_ref);
    if (write_type) {
      write_type_info(ctx);
    }
    write_data_generic(arr, ctx, has_generics);
  }

  static inline void write_data(const std::array<uint8_t, N> &arr,
                                WriteContext &ctx) {
    Buffer &buffer = ctx.buffer();
    constexpr size_t max_size = 8 + N * sizeof(uint8_t);
    buffer.Grow(static_cast<uint32_t>(max_size));
    uint32_t writer_index = buffer.writer_index();
    writer_index += buffer.PutVarUint32(writer_index, static_cast<uint32_t>(N));
    if constexpr (N > 0) {
      buffer.UnsafePut(writer_index, arr.data(), N * sizeof(uint8_t));
    }
    buffer.WriterIndex(writer_index + N * sizeof(uint8_t));
  }

  static inline void write_data_generic(const std::array<uint8_t, N> &arr,
                                        WriteContext &ctx, bool has_generics) {
    write_data(arr, ctx);
  }

  static inline std::array<uint8_t, N> read(ReadContext &ctx, bool read_ref,
                                            bool read_type) {
    bool has_value = consume_ref_flag(ctx, read_ref);
    if (ctx.has_error() || !has_value) {
      return std::array<uint8_t, N>();
    }
    if (read_type) {
      uint32_t type_id_read = ctx.read_varuint32(ctx.error());
      if (FORY_PREDICT_FALSE(ctx.has_error())) {
        return std::array<uint8_t, N>();
      }
      TypeId expected = get_type_id(ctx.is_xlang());
      if (type_id_read != static_cast<uint32_t>(expected)) {
        ctx.set_error(Error::type_mismatch(type_id_read,
                                           static_cast<uint32_t>(expected)));
        return std::array<uint8_t, N>();
      }
    }
    return read_data(ctx);
  }

  static inline std::array<uint8_t, N> read_data(ReadContext &ctx) {
    uint32_t length = ctx.read_varuint32(ctx.error());
    if (FORY_PREDICT_FALSE(ctx.has_error())) {
      return std::array<uint8_t, N>();
    }
    if (length != N) {
      ctx.set_error(Error::invalid_data("Array size mismatch: expected " +
                                        std::to_string(N) + " but got " +
                                        std::to_string(length)));
      return std::array<uint8_t, N>();
    }
    std::array<uint8_t, N> arr;
    if constexpr (N > 0) {
      ctx.read_bytes(arr.data(), N * sizeof(uint8_t), ctx.error());
    }
    return arr;
  }

  static inline std::array<uint8_t, N>
  read_with_type_info(ReadContext &ctx, bool read_ref,
                      const TypeInfo &type_info) {
    return read(ctx, read_ref, false);
  }
};

template <size_t N> struct Serializer<std::array<uint16_t, N>> {
  static inline TypeId get_type_id(bool is_xlang) {
    return is_xlang ? UnsignedArrayTypeId<uint16_t>::xlang_type_id
                    : UnsignedArrayTypeId<uint16_t>::native_type_id;
  }

  static inline void write_type_info(WriteContext &ctx) {
    TypeId tid = get_type_id(ctx.is_xlang());
    ctx.write_varuint32(static_cast<uint32_t>(tid));
  }

  static inline void read_type_info(ReadContext &ctx) {
    uint32_t actual = ctx.read_varuint32(ctx.error());
    if (FORY_PREDICT_FALSE(ctx.has_error())) {
      return;
    }
    TypeId expected = get_type_id(ctx.is_xlang());
    if (!type_id_matches(actual, static_cast<uint32_t>(expected))) {
      ctx.set_error(
          Error::type_mismatch(actual, static_cast<uint32_t>(expected)));
    }
  }

  static inline void write(const std::array<uint16_t, N> &arr,
                           WriteContext &ctx, bool write_ref, bool write_type,
                           bool has_generics = false) {
    write_not_null_ref_flag(ctx, write_ref);
    if (write_type) {
      write_type_info(ctx);
    }
    write_data_generic(arr, ctx, has_generics);
  }

  static inline void write_data(const std::array<uint16_t, N> &arr,
                                WriteContext &ctx) {
    Buffer &buffer = ctx.buffer();
    constexpr size_t max_size = 8 + N * sizeof(uint16_t);
    buffer.Grow(static_cast<uint32_t>(max_size));
    uint32_t writer_index = buffer.writer_index();
    writer_index += buffer.PutVarUint32(writer_index, static_cast<uint32_t>(N));
    if constexpr (N > 0) {
      buffer.UnsafePut(writer_index, arr.data(), N * sizeof(uint16_t));
    }
    buffer.WriterIndex(writer_index + N * sizeof(uint16_t));
  }

  static inline void write_data_generic(const std::array<uint16_t, N> &arr,
                                        WriteContext &ctx, bool has_generics) {
    write_data(arr, ctx);
  }

  static inline std::array<uint16_t, N> read(ReadContext &ctx, bool read_ref,
                                             bool read_type) {
    bool has_value = consume_ref_flag(ctx, read_ref);
    if (ctx.has_error() || !has_value) {
      return std::array<uint16_t, N>();
    }
    if (read_type) {
      uint32_t type_id_read = ctx.read_varuint32(ctx.error());
      if (FORY_PREDICT_FALSE(ctx.has_error())) {
        return std::array<uint16_t, N>();
      }
      TypeId expected = get_type_id(ctx.is_xlang());
      if (type_id_read != static_cast<uint32_t>(expected)) {
        ctx.set_error(Error::type_mismatch(type_id_read,
                                           static_cast<uint32_t>(expected)));
        return std::array<uint16_t, N>();
      }
    }
    return read_data(ctx);
  }

  static inline std::array<uint16_t, N> read_data(ReadContext &ctx) {
    uint32_t length = ctx.read_varuint32(ctx.error());
    if (FORY_PREDICT_FALSE(ctx.has_error())) {
      return std::array<uint16_t, N>();
    }
    if (length != N) {
      ctx.set_error(Error::invalid_data("Array size mismatch: expected " +
                                        std::to_string(N) + " but got " +
                                        std::to_string(length)));
      return std::array<uint16_t, N>();
    }
    std::array<uint16_t, N> arr;
    if constexpr (N > 0) {
      ctx.read_bytes(arr.data(), N * sizeof(uint16_t), ctx.error());
    }
    return arr;
  }

  static inline std::array<uint16_t, N>
  read_with_type_info(ReadContext &ctx, bool read_ref,
                      const TypeInfo &type_info) {
    return read(ctx, read_ref, false);
  }
};

template <size_t N> struct Serializer<std::array<uint32_t, N>> {
  static inline TypeId get_type_id(bool is_xlang) {
    return is_xlang ? UnsignedArrayTypeId<uint32_t>::xlang_type_id
                    : UnsignedArrayTypeId<uint32_t>::native_type_id;
  }

  static inline void write_type_info(WriteContext &ctx) {
    TypeId tid = get_type_id(ctx.is_xlang());
    ctx.write_varuint32(static_cast<uint32_t>(tid));
  }

  static inline void read_type_info(ReadContext &ctx) {
    uint32_t actual = ctx.read_varuint32(ctx.error());
    if (FORY_PREDICT_FALSE(ctx.has_error())) {
      return;
    }
    TypeId expected = get_type_id(ctx.is_xlang());
    if (!type_id_matches(actual, static_cast<uint32_t>(expected))) {
      ctx.set_error(
          Error::type_mismatch(actual, static_cast<uint32_t>(expected)));
    }
  }

  static inline void write(const std::array<uint32_t, N> &arr,
                           WriteContext &ctx, bool write_ref, bool write_type,
                           bool has_generics = false) {
    write_not_null_ref_flag(ctx, write_ref);
    if (write_type) {
      write_type_info(ctx);
    }
    write_data_generic(arr, ctx, has_generics);
  }

  static inline void write_data(const std::array<uint32_t, N> &arr,
                                WriteContext &ctx) {
    Buffer &buffer = ctx.buffer();
    constexpr size_t max_size = 8 + N * sizeof(uint32_t);
    buffer.Grow(static_cast<uint32_t>(max_size));
    uint32_t writer_index = buffer.writer_index();
    writer_index += buffer.PutVarUint32(writer_index, static_cast<uint32_t>(N));
    if constexpr (N > 0) {
      buffer.UnsafePut(writer_index, arr.data(), N * sizeof(uint32_t));
    }
    buffer.WriterIndex(writer_index + N * sizeof(uint32_t));
  }

  static inline void write_data_generic(const std::array<uint32_t, N> &arr,
                                        WriteContext &ctx, bool has_generics) {
    write_data(arr, ctx);
  }

  static inline std::array<uint32_t, N> read(ReadContext &ctx, bool read_ref,
                                             bool read_type) {
    bool has_value = consume_ref_flag(ctx, read_ref);
    if (ctx.has_error() || !has_value) {
      return std::array<uint32_t, N>();
    }
    if (read_type) {
      uint32_t type_id_read = ctx.read_varuint32(ctx.error());
      if (FORY_PREDICT_FALSE(ctx.has_error())) {
        return std::array<uint32_t, N>();
      }
      TypeId expected = get_type_id(ctx.is_xlang());
      if (type_id_read != static_cast<uint32_t>(expected)) {
        ctx.set_error(Error::type_mismatch(type_id_read,
                                           static_cast<uint32_t>(expected)));
        return std::array<uint32_t, N>();
      }
    }
    return read_data(ctx);
  }

  static inline std::array<uint32_t, N> read_data(ReadContext &ctx) {
    uint32_t length = ctx.read_varuint32(ctx.error());
    if (FORY_PREDICT_FALSE(ctx.has_error())) {
      return std::array<uint32_t, N>();
    }
    if (length != N) {
      ctx.set_error(Error::invalid_data("Array size mismatch: expected " +
                                        std::to_string(N) + " but got " +
                                        std::to_string(length)));
      return std::array<uint32_t, N>();
    }
    std::array<uint32_t, N> arr;
    if constexpr (N > 0) {
      ctx.read_bytes(arr.data(), N * sizeof(uint32_t), ctx.error());
    }
    return arr;
  }

  static inline std::array<uint32_t, N>
  read_with_type_info(ReadContext &ctx, bool read_ref,
                      const TypeInfo &type_info) {
    return read(ctx, read_ref, false);
  }
};

template <size_t N> struct Serializer<std::array<uint64_t, N>> {
  static inline TypeId get_type_id(bool is_xlang) {
    return is_xlang ? UnsignedArrayTypeId<uint64_t>::xlang_type_id
                    : UnsignedArrayTypeId<uint64_t>::native_type_id;
  }

  static inline void write_type_info(WriteContext &ctx) {
    TypeId tid = get_type_id(ctx.is_xlang());
    ctx.write_varuint32(static_cast<uint32_t>(tid));
  }

  static inline void read_type_info(ReadContext &ctx) {
    uint32_t actual = ctx.read_varuint32(ctx.error());
    if (FORY_PREDICT_FALSE(ctx.has_error())) {
      return;
    }
    TypeId expected = get_type_id(ctx.is_xlang());
    if (!type_id_matches(actual, static_cast<uint32_t>(expected))) {
      ctx.set_error(
          Error::type_mismatch(actual, static_cast<uint32_t>(expected)));
    }
  }

  static inline void write(const std::array<uint64_t, N> &arr,
                           WriteContext &ctx, bool write_ref, bool write_type,
                           bool has_generics = false) {
    write_not_null_ref_flag(ctx, write_ref);
    if (write_type) {
      write_type_info(ctx);
    }
    write_data_generic(arr, ctx, has_generics);
  }

  static inline void write_data(const std::array<uint64_t, N> &arr,
                                WriteContext &ctx) {
    Buffer &buffer = ctx.buffer();
    constexpr size_t max_size = 8 + N * sizeof(uint64_t);
    buffer.Grow(static_cast<uint32_t>(max_size));
    uint32_t writer_index = buffer.writer_index();
    writer_index += buffer.PutVarUint32(writer_index, static_cast<uint32_t>(N));
    if constexpr (N > 0) {
      buffer.UnsafePut(writer_index, arr.data(), N * sizeof(uint64_t));
    }
    buffer.WriterIndex(writer_index + N * sizeof(uint64_t));
  }

  static inline void write_data_generic(const std::array<uint64_t, N> &arr,
                                        WriteContext &ctx, bool has_generics) {
    write_data(arr, ctx);
  }

  static inline std::array<uint64_t, N> read(ReadContext &ctx, bool read_ref,
                                             bool read_type) {
    bool has_value = consume_ref_flag(ctx, read_ref);
    if (ctx.has_error() || !has_value) {
      return std::array<uint64_t, N>();
    }
    if (read_type) {
      uint32_t type_id_read = ctx.read_varuint32(ctx.error());
      if (FORY_PREDICT_FALSE(ctx.has_error())) {
        return std::array<uint64_t, N>();
      }
      TypeId expected = get_type_id(ctx.is_xlang());
      if (type_id_read != static_cast<uint32_t>(expected)) {
        ctx.set_error(Error::type_mismatch(type_id_read,
                                           static_cast<uint32_t>(expected)));
        return std::array<uint64_t, N>();
      }
    }
    return read_data(ctx);
  }

  static inline std::array<uint64_t, N> read_data(ReadContext &ctx) {
    uint32_t length = ctx.read_varuint32(ctx.error());
    if (FORY_PREDICT_FALSE(ctx.has_error())) {
      return std::array<uint64_t, N>();
    }
    if (length != N) {
      ctx.set_error(Error::invalid_data("Array size mismatch: expected " +
                                        std::to_string(N) + " but got " +
                                        std::to_string(length)));
      return std::array<uint64_t, N>();
    }
    std::array<uint64_t, N> arr;
    if constexpr (N > 0) {
      ctx.read_bytes(arr.data(), N * sizeof(uint64_t), ctx.error());
    }
    return arr;
  }

  static inline std::array<uint64_t, N>
  read_with_type_info(ReadContext &ctx, bool read_ref,
                      const TypeInfo &type_info) {
    return read(ctx, read_ref, false);
  }
};

// ============================================================================
// std::vector serializers for unsigned types
// ============================================================================

/// Serializer for std::vector<uint8_t> (binary data)
/// Note: uint8_t vectors use BINARY type which works in both xlang and native
/// modes
template <> struct Serializer<std::vector<uint8_t>> {
  static constexpr TypeId type_id = TypeId::BINARY;

  static inline void write_type_info(WriteContext &ctx) {
    ctx.write_varuint32(static_cast<uint32_t>(type_id));
  }

  static inline void read_type_info(ReadContext &ctx) {
    uint32_t actual = ctx.read_varuint32(ctx.error());
    if (FORY_PREDICT_FALSE(ctx.has_error())) {
      return;
    }
    if (!type_id_matches(actual, static_cast<uint32_t>(type_id))) {
      ctx.set_error(
          Error::type_mismatch(actual, static_cast<uint32_t>(type_id)));
    }
  }

  static inline void write(const std::vector<uint8_t> &vec, WriteContext &ctx,
                           bool write_ref, bool write_type,
                           bool has_generics = false) {
    write_not_null_ref_flag(ctx, write_ref);
    if (write_type) {
      ctx.write_varuint32(static_cast<uint32_t>(type_id));
    }
    write_data_generic(vec, ctx, has_generics);
  }

  static inline void write_data(const std::vector<uint8_t> &vec,
                                WriteContext &ctx) {
    Buffer &buffer = ctx.buffer();
    size_t max_size = 8 + vec.size();
    buffer.Grow(static_cast<uint32_t>(max_size));
    uint32_t writer_index = buffer.writer_index();
    writer_index +=
        buffer.PutVarUint32(writer_index, static_cast<uint32_t>(vec.size()));
    if (!vec.empty()) {
      buffer.UnsafePut(writer_index, vec.data(), vec.size());
    }
    buffer.WriterIndex(writer_index + static_cast<uint32_t>(vec.size()));
  }

  static inline void write_data_generic(const std::vector<uint8_t> &vec,
                                        WriteContext &ctx, bool has_generics) {
    write_data(vec, ctx);
  }

  static inline std::vector<uint8_t> read(ReadContext &ctx, bool read_ref,
                                          bool read_type) {
    bool has_value = consume_ref_flag(ctx, read_ref);
    if (ctx.has_error() || !has_value) {
      return std::vector<uint8_t>();
    }
    if (read_type) {
      uint32_t type_id_read = ctx.read_varuint32(ctx.error());
      if (FORY_PREDICT_FALSE(ctx.has_error())) {
        return std::vector<uint8_t>();
      }
      if (type_id_read != static_cast<uint32_t>(type_id)) {
        ctx.set_error(
            Error::type_mismatch(type_id_read, static_cast<uint32_t>(type_id)));
        return std::vector<uint8_t>();
      }
    }
    return read_data(ctx);
  }

  static inline std::vector<uint8_t> read_data(ReadContext &ctx) {
    uint32_t length = ctx.read_varuint32(ctx.error());
    if (FORY_PREDICT_FALSE(ctx.has_error())) {
      return std::vector<uint8_t>();
    }
    std::vector<uint8_t> vec(length);
    if (length > 0) {
      ctx.read_bytes(vec.data(), length, ctx.error());
    }
    return vec;
  }

  static inline std::vector<uint8_t> read_data_generic(ReadContext &ctx,
                                                       bool has_generics) {
    return read_data(ctx);
  }

  static inline std::vector<uint8_t>
  read_with_type_info(ReadContext &ctx, bool read_ref,
                      const TypeInfo &type_info) {
    return read(ctx, read_ref, false);
  }
};

/// Serializer for std::vector<uint16_t>
template <> struct Serializer<std::vector<uint16_t>> {
  static constexpr TypeId type_id = TypeId::INT16_ARRAY;
  static constexpr TypeId xlang_type_id = TypeId::INT16_ARRAY;
  static constexpr TypeId native_type_id = TypeId::U16_ARRAY;

  static inline TypeId get_type_id(bool is_xlang) {
    return is_xlang ? xlang_type_id : native_type_id;
  }

  static inline void write_type_info(WriteContext &ctx) {
    TypeId tid = get_type_id(ctx.is_xlang());
    ctx.write_varuint32(static_cast<uint32_t>(tid));
  }

  static inline void read_type_info(ReadContext &ctx) {
    uint32_t actual = ctx.read_varuint32(ctx.error());
    if (FORY_PREDICT_FALSE(ctx.has_error())) {
      return;
    }
    TypeId expected = get_type_id(ctx.is_xlang());
    if (!type_id_matches(actual, static_cast<uint32_t>(expected))) {
      ctx.set_error(
          Error::type_mismatch(actual, static_cast<uint32_t>(expected)));
    }
  }

  static inline void write(const std::vector<uint16_t> &vec, WriteContext &ctx,
                           bool write_ref, bool write_type,
                           bool has_generics = false) {
    write_not_null_ref_flag(ctx, write_ref);
    if (write_type) {
      write_type_info(ctx);
    }
    write_data_generic(vec, ctx, has_generics);
  }

  static inline void write_data(const std::vector<uint16_t> &vec,
                                WriteContext &ctx) {
    Buffer &buffer = ctx.buffer();
    size_t data_size = vec.size() * sizeof(uint16_t);
    size_t max_size = 8 + data_size;
    buffer.Grow(static_cast<uint32_t>(max_size));
    uint32_t writer_index = buffer.writer_index();
    writer_index +=
        buffer.PutVarUint32(writer_index, static_cast<uint32_t>(vec.size()));
    if (!vec.empty()) {
      buffer.UnsafePut(writer_index, vec.data(), data_size);
    }
    buffer.WriterIndex(writer_index + static_cast<uint32_t>(data_size));
  }

  static inline void write_data_generic(const std::vector<uint16_t> &vec,
                                        WriteContext &ctx, bool has_generics) {
    write_data(vec, ctx);
  }

  static inline std::vector<uint16_t> read(ReadContext &ctx, bool read_ref,
                                           bool read_type) {
    bool has_value = consume_ref_flag(ctx, read_ref);
    if (ctx.has_error() || !has_value) {
      return std::vector<uint16_t>();
    }
    if (read_type) {
      uint32_t type_id_read = ctx.read_varuint32(ctx.error());
      if (FORY_PREDICT_FALSE(ctx.has_error())) {
        return std::vector<uint16_t>();
      }
      TypeId expected = get_type_id(ctx.is_xlang());
      if (type_id_read != static_cast<uint32_t>(expected)) {
        ctx.set_error(Error::type_mismatch(type_id_read,
                                           static_cast<uint32_t>(expected)));
        return std::vector<uint16_t>();
      }
    }
    return read_data(ctx);
  }

  static inline std::vector<uint16_t> read_data(ReadContext &ctx) {
    uint32_t length = ctx.read_varuint32(ctx.error());
    if (FORY_PREDICT_FALSE(ctx.has_error())) {
      return std::vector<uint16_t>();
    }
    std::vector<uint16_t> vec(length);
    if (length > 0) {
      ctx.read_bytes(vec.data(), length * sizeof(uint16_t), ctx.error());
    }
    return vec;
  }

  static inline std::vector<uint16_t> read_data_generic(ReadContext &ctx,
                                                        bool has_generics) {
    return read_data(ctx);
  }

  static inline std::vector<uint16_t>
  read_with_type_info(ReadContext &ctx, bool read_ref,
                      const TypeInfo &type_info) {
    return read(ctx, read_ref, false);
  }
};

/// Serializer for std::vector<uint32_t>
template <> struct Serializer<std::vector<uint32_t>> {
  static constexpr TypeId type_id = TypeId::INT32_ARRAY;
  static constexpr TypeId xlang_type_id = TypeId::INT32_ARRAY;
  static constexpr TypeId native_type_id = TypeId::U32_ARRAY;

  static inline TypeId get_type_id(bool is_xlang) {
    return is_xlang ? xlang_type_id : native_type_id;
  }

  static inline void write_type_info(WriteContext &ctx) {
    TypeId tid = get_type_id(ctx.is_xlang());
    ctx.write_varuint32(static_cast<uint32_t>(tid));
  }

  static inline void read_type_info(ReadContext &ctx) {
    uint32_t actual = ctx.read_varuint32(ctx.error());
    if (FORY_PREDICT_FALSE(ctx.has_error())) {
      return;
    }
    TypeId expected = get_type_id(ctx.is_xlang());
    if (!type_id_matches(actual, static_cast<uint32_t>(expected))) {
      ctx.set_error(
          Error::type_mismatch(actual, static_cast<uint32_t>(expected)));
    }
  }

  static inline void write(const std::vector<uint32_t> &vec, WriteContext &ctx,
                           bool write_ref, bool write_type,
                           bool has_generics = false) {
    write_not_null_ref_flag(ctx, write_ref);
    if (write_type) {
      write_type_info(ctx);
    }
    write_data_generic(vec, ctx, has_generics);
  }

  static inline void write_data(const std::vector<uint32_t> &vec,
                                WriteContext &ctx) {
    Buffer &buffer = ctx.buffer();
    size_t data_size = vec.size() * sizeof(uint32_t);
    size_t max_size = 8 + data_size;
    buffer.Grow(static_cast<uint32_t>(max_size));
    uint32_t writer_index = buffer.writer_index();
    writer_index +=
        buffer.PutVarUint32(writer_index, static_cast<uint32_t>(vec.size()));
    if (!vec.empty()) {
      buffer.UnsafePut(writer_index, vec.data(), data_size);
    }
    buffer.WriterIndex(writer_index + static_cast<uint32_t>(data_size));
  }

  static inline void write_data_generic(const std::vector<uint32_t> &vec,
                                        WriteContext &ctx, bool has_generics) {
    write_data(vec, ctx);
  }

  static inline std::vector<uint32_t> read(ReadContext &ctx, bool read_ref,
                                           bool read_type) {
    bool has_value = consume_ref_flag(ctx, read_ref);
    if (ctx.has_error() || !has_value) {
      return std::vector<uint32_t>();
    }
    if (read_type) {
      uint32_t type_id_read = ctx.read_varuint32(ctx.error());
      if (FORY_PREDICT_FALSE(ctx.has_error())) {
        return std::vector<uint32_t>();
      }
      TypeId expected = get_type_id(ctx.is_xlang());
      if (type_id_read != static_cast<uint32_t>(expected)) {
        ctx.set_error(Error::type_mismatch(type_id_read,
                                           static_cast<uint32_t>(expected)));
        return std::vector<uint32_t>();
      }
    }
    return read_data(ctx);
  }

  static inline std::vector<uint32_t> read_data(ReadContext &ctx) {
    uint32_t length = ctx.read_varuint32(ctx.error());
    if (FORY_PREDICT_FALSE(ctx.has_error())) {
      return std::vector<uint32_t>();
    }
    std::vector<uint32_t> vec(length);
    if (length > 0) {
      ctx.read_bytes(vec.data(), length * sizeof(uint32_t), ctx.error());
    }
    return vec;
  }

  static inline std::vector<uint32_t> read_data_generic(ReadContext &ctx,
                                                        bool has_generics) {
    return read_data(ctx);
  }

  static inline std::vector<uint32_t>
  read_with_type_info(ReadContext &ctx, bool read_ref,
                      const TypeInfo &type_info) {
    return read(ctx, read_ref, false);
  }
};

/// Serializer for std::vector<uint64_t>
template <> struct Serializer<std::vector<uint64_t>> {
  static constexpr TypeId type_id = TypeId::INT64_ARRAY;
  static constexpr TypeId xlang_type_id = TypeId::INT64_ARRAY;
  static constexpr TypeId native_type_id = TypeId::U64_ARRAY;

  static inline TypeId get_type_id(bool is_xlang) {
    return is_xlang ? xlang_type_id : native_type_id;
  }

  static inline void write_type_info(WriteContext &ctx) {
    TypeId tid = get_type_id(ctx.is_xlang());
    ctx.write_varuint32(static_cast<uint32_t>(tid));
  }

  static inline void read_type_info(ReadContext &ctx) {
    uint32_t actual = ctx.read_varuint32(ctx.error());
    if (FORY_PREDICT_FALSE(ctx.has_error())) {
      return;
    }
    TypeId expected = get_type_id(ctx.is_xlang());
    if (!type_id_matches(actual, static_cast<uint32_t>(expected))) {
      ctx.set_error(
          Error::type_mismatch(actual, static_cast<uint32_t>(expected)));
    }
  }

  static inline void write(const std::vector<uint64_t> &vec, WriteContext &ctx,
                           bool write_ref, bool write_type,
                           bool has_generics = false) {
    write_not_null_ref_flag(ctx, write_ref);
    if (write_type) {
      write_type_info(ctx);
    }
    write_data_generic(vec, ctx, has_generics);
  }

  static inline void write_data(const std::vector<uint64_t> &vec,
                                WriteContext &ctx) {
    Buffer &buffer = ctx.buffer();
    size_t data_size = vec.size() * sizeof(uint64_t);
    size_t max_size = 8 + data_size;
    buffer.Grow(static_cast<uint32_t>(max_size));
    uint32_t writer_index = buffer.writer_index();
    writer_index +=
        buffer.PutVarUint32(writer_index, static_cast<uint32_t>(vec.size()));
    if (!vec.empty()) {
      buffer.UnsafePut(writer_index, vec.data(), data_size);
    }
    buffer.WriterIndex(writer_index + static_cast<uint32_t>(data_size));
  }

  static inline void write_data_generic(const std::vector<uint64_t> &vec,
                                        WriteContext &ctx, bool has_generics) {
    write_data(vec, ctx);
  }

  static inline std::vector<uint64_t> read(ReadContext &ctx, bool read_ref,
                                           bool read_type) {
    bool has_value = consume_ref_flag(ctx, read_ref);
    if (ctx.has_error() || !has_value) {
      return std::vector<uint64_t>();
    }
    if (read_type) {
      uint32_t type_id_read = ctx.read_varuint32(ctx.error());
      if (FORY_PREDICT_FALSE(ctx.has_error())) {
        return std::vector<uint64_t>();
      }
      TypeId expected = get_type_id(ctx.is_xlang());
      if (type_id_read != static_cast<uint32_t>(expected)) {
        ctx.set_error(Error::type_mismatch(type_id_read,
                                           static_cast<uint32_t>(expected)));
        return std::vector<uint64_t>();
      }
    }
    return read_data(ctx);
  }

  static inline std::vector<uint64_t> read_data(ReadContext &ctx) {
    uint32_t length = ctx.read_varuint32(ctx.error());
    if (FORY_PREDICT_FALSE(ctx.has_error())) {
      return std::vector<uint64_t>();
    }
    std::vector<uint64_t> vec(length);
    if (length > 0) {
      ctx.read_bytes(vec.data(), length * sizeof(uint64_t), ctx.error());
    }
    return vec;
  }

  static inline std::vector<uint64_t> read_data_generic(ReadContext &ctx,
                                                        bool has_generics) {
    return read_data(ctx);
  }

  static inline std::vector<uint64_t>
  read_with_type_info(ReadContext &ctx, bool read_ref,
                      const TypeInfo &type_info) {
    return read(ctx, read_ref, false);
  }
};

} // namespace serialization
} // namespace fory
