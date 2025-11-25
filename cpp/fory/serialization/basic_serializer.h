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
#include "fory/util/result.h"
#include "fory/util/string_util.h"
#include <cstdint>
#include <cstring>
#include <iomanip>
#include <iostream>
#include <string>
#include <type_traits>

namespace fory {
namespace serialization {

// ============================================================================
// Primitive Type Serializers
// ============================================================================

/// Boolean serializer
template <> struct Serializer<bool> {
  static constexpr TypeId type_id = TypeId::BOOL;

  /// Write boolean with optional reference and type info
  /// Match Rust signature: fory_write(&self, context, write_ref_info, write_type_info, has_generics)
  static inline Result<void, Error> write(bool value, WriteContext &ctx,
                                          bool write_ref, bool write_type,
                                          bool has_generics = false) {
    write_not_null_ref_flag(ctx, write_ref);
    if (write_type) {
      ctx.write_varuint32(static_cast<uint32_t>(type_id));
    }
    return write_data_generic(value, ctx, has_generics);
  }

  /// Write boolean data only (no type info)
  static inline Result<void, Error> write_data(bool value, WriteContext &ctx) {
    ctx.write_uint8(value ? 1 : 0);
    return Result<void, Error>();
  }

  /// Write boolean with generic optimization (unused for primitives)
  static inline Result<void, Error>
  write_data_generic(bool value, WriteContext &ctx, bool has_generics) {
    return write_data(value, ctx);
  }

  /// Read boolean with optional reference and type info
  static inline Result<bool, Error> read(ReadContext &ctx, bool read_ref,
                                         bool read_type) {
    FORY_TRY(has_value, consume_ref_flag(ctx, read_ref));
    if (!has_value) {
      return false;
    }
    if (read_type) {
      FORY_TRY(type_id_read, ctx.read_varuint32());
      if (type_id_read != static_cast<uint32_t>(type_id)) {
        return Unexpected(
            Error::type_mismatch(type_id_read, static_cast<uint32_t>(type_id)));
      }
    }
    FORY_TRY(value, ctx.read_uint8());
    return value != 0;
  }

  /// Read boolean data only (no type info)
  static inline Result<bool, Error> read_data(ReadContext &ctx) {
    FORY_TRY(value, ctx.read_uint8());
    return value != 0;
  }

  /// Read boolean with generic optimization (unused for primitives)
  static inline Result<bool, Error> read_data_generic(ReadContext &ctx,
                                                      bool has_generics) {
    return read_data(ctx);
  }

  /// Read boolean with type info (type info already validated)
  static inline Result<bool, Error>
  read_with_type_info(ReadContext &ctx, bool read_ref,
                      const TypeInfo &type_info) {
    // Type info already validated, skip redundant type read
    return read(ctx, read_ref, false); // read_type=false
  }
};

/// int8_t serializer
template <> struct Serializer<int8_t> {
  static constexpr TypeId type_id = TypeId::INT8;

  static inline Result<void, Error> write(int8_t value, WriteContext &ctx,
                                          bool write_ref, bool write_type,
                                          bool has_generics = false) {
    write_not_null_ref_flag(ctx, write_ref);
    if (write_type) {
      ctx.write_varuint32(static_cast<uint32_t>(type_id));
    }
    return write_data_generic(value, ctx, has_generics);
  }

  static inline Result<void, Error> write_data(int8_t value,
                                               WriteContext &ctx) {
    ctx.write_int8(value);
    return Result<void, Error>();
  }

  static inline Result<void, Error>
  write_data_generic(int8_t value, WriteContext &ctx, bool has_generics) {
    return write_data(value, ctx);
  }

  static inline Result<int8_t, Error> read(ReadContext &ctx, bool read_ref,
                                           bool read_type) {
    FORY_TRY(has_value, consume_ref_flag(ctx, read_ref));
    if (!has_value) {
      return static_cast<int8_t>(0);
    }
    if (read_type) {
      FORY_TRY(type_id_read, ctx.read_varuint32());
      if (type_id_read != static_cast<uint32_t>(type_id)) {
        return Unexpected(
            Error::type_mismatch(type_id_read, static_cast<uint32_t>(type_id)));
      }
    }
    return ctx.read_int8();
  }

  static inline Result<int8_t, Error> read_data(ReadContext &ctx) {
    return ctx.read_int8();
  }

  static inline Result<int8_t, Error> read_data_generic(ReadContext &ctx,
                                                        bool has_generics) {
    return read_data(ctx);
  }

  static inline Result<int8_t, Error>
  read_with_type_info(ReadContext &ctx, bool read_ref,
                      const TypeInfo &type_info) {
    return read(ctx, read_ref, false);
  }
};

/// int16_t serializer
template <> struct Serializer<int16_t> {
  static constexpr TypeId type_id = TypeId::INT16;

  static inline Result<void, Error> write(int16_t value, WriteContext &ctx,
                                          bool write_ref, bool write_type,
                                          bool has_generics = false) {
    write_not_null_ref_flag(ctx, write_ref);
    if (write_type) {
      ctx.write_varuint32(static_cast<uint32_t>(type_id));
    }
    return write_data_generic(value, ctx, has_generics);
  }

  static inline Result<void, Error> write_data(int16_t value,
                                               WriteContext &ctx) {
    ctx.write_bytes(&value, sizeof(int16_t));
    return Result<void, Error>();
  }

  static inline Result<void, Error>
  write_data_generic(int16_t value, WriteContext &ctx, bool has_generics) {
    return write_data(value, ctx);
  }

  static inline Result<int16_t, Error> read(ReadContext &ctx, bool read_ref,
                                            bool read_type) {
    FORY_TRY(has_value, consume_ref_flag(ctx, read_ref));
    if (!has_value) {
      return static_cast<int16_t>(0);
    }
    if (read_type) {
      FORY_TRY(type_id_read, ctx.read_varuint32());
      if (type_id_read != static_cast<uint32_t>(type_id)) {
        return Unexpected(
            Error::type_mismatch(type_id_read, static_cast<uint32_t>(type_id)));
      }
    }
    int16_t value;
    FORY_RETURN_NOT_OK(ctx.read_bytes(&value, sizeof(int16_t)));
    return value;
  }

  static inline Result<int16_t, Error> read_data(ReadContext &ctx) {
    int16_t value;
    FORY_RETURN_NOT_OK(ctx.read_bytes(&value, sizeof(int16_t)));
    return value;
  }

  static inline Result<int16_t, Error> read_data_generic(ReadContext &ctx,
                                                         bool has_generics) {
    return read_data(ctx);
  }

  static inline Result<int16_t, Error>
  read_with_type_info(ReadContext &ctx, bool read_ref,
                      const TypeInfo &type_info) {
    return read(ctx, read_ref, false);
  }
};

/// int32_t serializer
template <> struct Serializer<int32_t> {
  static constexpr TypeId type_id = TypeId::INT32;

  static inline Result<void, Error> write(int32_t value, WriteContext &ctx,
                                          bool write_ref, bool write_type,
                                          bool has_generics = false) {
    write_not_null_ref_flag(ctx, write_ref);
    if (write_type) {
      ctx.write_varuint32(static_cast<uint32_t>(type_id));
    }
    return write_data_generic(value, ctx, has_generics);
  }

  static inline Result<void, Error> write_data(int32_t value,
                                               WriteContext &ctx) {
    ctx.write_varint32(value);
    return Result<void, Error>();
  }

  static inline Result<void, Error>
  write_data_generic(int32_t value, WriteContext &ctx, bool has_generics) {
    return write_data(value, ctx);
  }

  static inline Result<int32_t, Error> read(ReadContext &ctx, bool read_ref,
                                            bool read_type) {
    FORY_TRY(has_value, consume_ref_flag(ctx, read_ref));
    if (!has_value) {
      return static_cast<int32_t>(0);
    }
    if (read_type) {
      FORY_TRY(type_id_read, ctx.read_varuint32());
      if (type_id_read != static_cast<uint32_t>(type_id)) {
        return Unexpected(
            Error::type_mismatch(type_id_read, static_cast<uint32_t>(type_id)));
      }
    }
    return ctx.read_varint32();
  }

  static inline Result<int32_t, Error> read_data(ReadContext &ctx) {
    return ctx.read_varint32();
  }

  static inline Result<int32_t, Error> read_data_generic(ReadContext &ctx,
                                                         bool has_generics) {
    return read_data(ctx);
  }

  static inline Result<int32_t, Error>
  read_with_type_info(ReadContext &ctx, bool read_ref,
                      const TypeInfo &type_info) {
    return read(ctx, read_ref, false);
  }
};

/// int64_t serializer
template <> struct Serializer<int64_t> {
  static constexpr TypeId type_id = TypeId::INT64;

  static inline Result<void, Error> write(int64_t value, WriteContext &ctx,
                                          bool write_ref, bool write_type,
                                          bool has_generics = false) {
    write_not_null_ref_flag(ctx, write_ref);
    if (write_type) {
      ctx.write_varuint32(static_cast<uint32_t>(type_id));
    }
    return write_data_generic(value, ctx, has_generics);
  }

  static inline Result<void, Error> write_data(int64_t value,
                                               WriteContext &ctx) {
    ctx.write_varint64(value);
    return Result<void, Error>();
  }

  static inline Result<void, Error>
  write_data_generic(int64_t value, WriteContext &ctx, bool has_generics) {
    return write_data(value, ctx);
  }

  static inline Result<int64_t, Error> read(ReadContext &ctx, bool read_ref,
                                            bool read_type) {
    FORY_TRY(has_value, consume_ref_flag(ctx, read_ref));
    if (!has_value) {
      return static_cast<int64_t>(0);
    }
    if (read_type) {
      FORY_TRY(type_id_read, ctx.read_varuint32());
      if (type_id_read != static_cast<uint32_t>(type_id)) {
        return Unexpected(
            Error::type_mismatch(type_id_read, static_cast<uint32_t>(type_id)));
      }
    }
    return ctx.read_varint64();
  }

  static inline Result<int64_t, Error> read_data(ReadContext &ctx) {
    return ctx.read_varint64();
  }

  static inline Result<int64_t, Error> read_data_generic(ReadContext &ctx,
                                                         bool has_generics) {
    return read_data(ctx);
  }

  static inline Result<int64_t, Error>
  read_with_type_info(ReadContext &ctx, bool read_ref,
                      const TypeInfo &type_info) {
    return read(ctx, read_ref, false);
  }
};

/// float serializer
template <> struct Serializer<float> {
  static constexpr TypeId type_id = TypeId::FLOAT32;

  static inline Result<void, Error> write(float value, WriteContext &ctx,
                                          bool write_ref, bool write_type,
                                          bool has_generics = false) {
    write_not_null_ref_flag(ctx, write_ref);
    if (write_type) {
      ctx.write_varuint32(static_cast<uint32_t>(type_id));
    }
    return write_data_generic(value, ctx, has_generics);
  }

  static inline Result<void, Error> write_data(float value, WriteContext &ctx) {
    ctx.write_bytes(&value, sizeof(float));
    return Result<void, Error>();
  }

  static inline Result<void, Error>
  write_data_generic(float value, WriteContext &ctx, bool has_generics) {
    return write_data(value, ctx);
  }

  static inline Result<float, Error> read(ReadContext &ctx, bool read_ref,
                                          bool read_type) {
    FORY_TRY(has_value, consume_ref_flag(ctx, read_ref));
    if (!has_value) {
      return 0.0f;
    }
    if (read_type) {
      FORY_TRY(type_id_read, ctx.read_varuint32());
      if (type_id_read != static_cast<uint32_t>(type_id)) {
        return Unexpected(
            Error::type_mismatch(type_id_read, static_cast<uint32_t>(type_id)));
      }
    }
    float value;
    FORY_RETURN_NOT_OK(ctx.read_bytes(&value, sizeof(float)));
    return value;
  }

  static inline Result<float, Error> read_data(ReadContext &ctx) {
    float value;
    FORY_RETURN_NOT_OK(ctx.read_bytes(&value, sizeof(float)));
    return value;
  }

  static inline Result<float, Error> read_data_generic(ReadContext &ctx,
                                                       bool has_generics) {
    return read_data(ctx);
  }

  static inline Result<float, Error>
  read_with_type_info(ReadContext &ctx, bool read_ref,
                      const TypeInfo &type_info) {
    return read(ctx, read_ref, false);
  }
};

/// double serializer
template <> struct Serializer<double> {
  static constexpr TypeId type_id = TypeId::FLOAT64;

  static inline Result<void, Error> write(double value, WriteContext &ctx,
                                          bool write_ref, bool write_type,
                                          bool has_generics = false) {
    write_not_null_ref_flag(ctx, write_ref);
    if (write_type) {
      ctx.write_varuint32(static_cast<uint32_t>(type_id));
    }
    return write_data_generic(value, ctx, has_generics);
  }

  static inline Result<void, Error> write_data(double value,
                                               WriteContext &ctx) {
    ctx.write_bytes(&value, sizeof(double));
    return Result<void, Error>();
  }

  static inline Result<void, Error>
  write_data_generic(double value, WriteContext &ctx, bool has_generics) {
    return write_data(value, ctx);
  }

  static inline Result<double, Error> read(ReadContext &ctx, bool read_ref,
                                           bool read_type) {
    FORY_TRY(has_value, consume_ref_flag(ctx, read_ref));
    if (!has_value) {
      return 0.0;
    }
    if (read_type) {
      FORY_TRY(type_id_read, ctx.read_varuint32());
      if (type_id_read != static_cast<uint32_t>(type_id)) {
        return Unexpected(
            Error::type_mismatch(type_id_read, static_cast<uint32_t>(type_id)));
      }
    }
    double value;
    FORY_RETURN_NOT_OK(ctx.read_bytes(&value, sizeof(double)));
    return value;
  }

  static inline Result<double, Error> read_data(ReadContext &ctx) {
    double value;
    FORY_RETURN_NOT_OK(ctx.read_bytes(&value, sizeof(double)));
    return value;
  }

  static inline Result<double, Error> read_data_generic(ReadContext &ctx,
                                                        bool has_generics) {
    return read_data(ctx);
  }

  static inline Result<double, Error>
  read_with_type_info(ReadContext &ctx, bool read_ref,
                      const TypeInfo &type_info) {
    return read(ctx, read_ref, false);
  }
};

// ============================================================================
// Unsigned Integer Type Serializers
// ============================================================================

/// uint8_t serializer
template <> struct Serializer<uint8_t> {
  static constexpr TypeId type_id = TypeId::INT8; // Same as int8

  static inline Result<void, Error> write(uint8_t value, WriteContext &ctx,
                                          bool write_ref, bool write_type,
                                          bool has_generics = false) {
    write_not_null_ref_flag(ctx, write_ref);
    if (write_type) {
      ctx.write_varuint32(static_cast<uint32_t>(type_id));
    }
    return write_data_generic(value, ctx, has_generics);
  }

  static inline Result<void, Error> write_data(uint8_t value,
                                               WriteContext &ctx) {
    ctx.write_uint8(value);
    return Result<void, Error>();
  }

  static inline Result<void, Error>
  write_data_generic(uint8_t value, WriteContext &ctx, bool has_generics) {
    return write_data(value, ctx);
  }

  static inline Result<uint8_t, Error> read(ReadContext &ctx, bool read_ref,
                                            bool read_type) {
    FORY_TRY(has_value, consume_ref_flag(ctx, read_ref));
    if (!has_value) {
      return static_cast<uint8_t>(0);
    }
    if (read_type) {
      FORY_TRY(type_id_read, ctx.read_varuint32());
      if (type_id_read != static_cast<uint32_t>(type_id)) {
        return Unexpected(
            Error::type_mismatch(type_id_read, static_cast<uint32_t>(type_id)));
      }
    }
    return ctx.read_uint8();
  }

  static inline Result<uint8_t, Error> read_data(ReadContext &ctx) {
    return ctx.read_uint8();
  }

  static inline Result<uint8_t, Error> read_data_generic(ReadContext &ctx,
                                                         bool has_generics) {
    return read_data(ctx);
  }

  static inline Result<uint8_t, Error>
  read_with_type_info(ReadContext &ctx, bool read_ref,
                      const TypeInfo &type_info) {
    return read(ctx, read_ref, false);
  }
};

/// uint16_t serializer
template <> struct Serializer<uint16_t> {
  static constexpr TypeId type_id = TypeId::INT16;

  static inline Result<void, Error> write(uint16_t value, WriteContext &ctx,
                                          bool write_ref, bool write_type,
                                          bool has_generics = false) {
    write_not_null_ref_flag(ctx, write_ref);
    if (write_type) {
      ctx.write_varuint32(static_cast<uint32_t>(type_id));
    }
    return write_data_generic(value, ctx, has_generics);
  }

  static inline Result<void, Error> write_data(uint16_t value,
                                               WriteContext &ctx) {
    ctx.write_bytes(&value, sizeof(uint16_t));
    return Result<void, Error>();
  }

  static inline Result<void, Error>
  write_data_generic(uint16_t value, WriteContext &ctx, bool has_generics) {
    return write_data(value, ctx);
  }

  static inline Result<uint16_t, Error> read(ReadContext &ctx, bool read_ref,
                                             bool read_type) {
    FORY_TRY(has_value, consume_ref_flag(ctx, read_ref));
    if (!has_value) {
      return static_cast<uint16_t>(0);
    }
    if (read_type) {
      FORY_TRY(type_id_read, ctx.read_varuint32());
      if (type_id_read != static_cast<uint32_t>(type_id)) {
        return Unexpected(
            Error::type_mismatch(type_id_read, static_cast<uint32_t>(type_id)));
      }
    }
    uint16_t value;
    FORY_RETURN_NOT_OK(ctx.read_bytes(&value, sizeof(uint16_t)));
    return value;
  }

  static inline Result<uint16_t, Error> read_data(ReadContext &ctx) {
    uint16_t value;
    FORY_RETURN_NOT_OK(ctx.read_bytes(&value, sizeof(uint16_t)));
    return value;
  }

  static inline Result<uint16_t, Error> read_data_generic(ReadContext &ctx,
                                                          bool has_generics) {
    return read_data(ctx);
  }

  static inline Result<uint16_t, Error>
  read_with_type_info(ReadContext &ctx, bool read_ref,
                      const TypeInfo &type_info) {
    return read(ctx, read_ref, false);
  }
};

/// uint32_t serializer
template <> struct Serializer<uint32_t> {
  static constexpr TypeId type_id = TypeId::INT32;

  static inline Result<void, Error> write(uint32_t value, WriteContext &ctx,
                                          bool write_ref, bool write_type,
                                          bool has_generics = false) {
    write_not_null_ref_flag(ctx, write_ref);
    if (write_type) {
      ctx.write_varuint32(static_cast<uint32_t>(type_id));
    }
    return write_data_generic(value, ctx, has_generics);
  }

  static inline Result<void, Error> write_data(uint32_t value,
                                               WriteContext &ctx) {
    ctx.write_bytes(&value, sizeof(uint32_t));
    return Result<void, Error>();
  }

  static inline Result<void, Error>
  write_data_generic(uint32_t value, WriteContext &ctx, bool has_generics) {
    return write_data(value, ctx);
  }

  static inline Result<uint32_t, Error> read(ReadContext &ctx, bool read_ref,
                                             bool read_type) {
    FORY_TRY(has_value, consume_ref_flag(ctx, read_ref));
    if (!has_value) {
      return static_cast<uint32_t>(0);
    }
    if (read_type) {
      FORY_TRY(type_id_read, ctx.read_varuint32());
      if (type_id_read != static_cast<uint32_t>(type_id)) {
        return Unexpected(
            Error::type_mismatch(type_id_read, static_cast<uint32_t>(type_id)));
      }
    }
    uint32_t value;
    FORY_RETURN_NOT_OK(ctx.read_bytes(&value, sizeof(uint32_t)));
    return value;
  }

  static inline Result<uint32_t, Error> read_data(ReadContext &ctx) {
    uint32_t value;
    FORY_RETURN_NOT_OK(ctx.read_bytes(&value, sizeof(uint32_t)));
    return value;
  }

  static inline Result<uint32_t, Error> read_data_generic(ReadContext &ctx,
                                                          bool has_generics) {
    return read_data(ctx);
  }

  static inline Result<uint32_t, Error>
  read_with_type_info(ReadContext &ctx, bool read_ref,
                      const TypeInfo &type_info) {
    return read(ctx, read_ref, false);
  }
};

/// uint64_t serializer
template <> struct Serializer<uint64_t> {
  static constexpr TypeId type_id = TypeId::INT64;

  static inline Result<void, Error> write(uint64_t value, WriteContext &ctx,
                                          bool write_ref, bool write_type,
                                          bool has_generics = false) {
    write_not_null_ref_flag(ctx, write_ref);
    if (write_type) {
      ctx.write_varuint32(static_cast<uint32_t>(type_id));
    }
    return write_data_generic(value, ctx, has_generics);
  }

  static inline Result<void, Error> write_data(uint64_t value,
                                               WriteContext &ctx) {
    ctx.write_bytes(&value, sizeof(uint64_t));
    return Result<void, Error>();
  }

  static inline Result<void, Error>
  write_data_generic(uint64_t value, WriteContext &ctx, bool has_generics) {
    return write_data(value, ctx);
  }

  static inline Result<uint64_t, Error> read(ReadContext &ctx, bool read_ref,
                                             bool read_type) {
    FORY_TRY(has_value, consume_ref_flag(ctx, read_ref));
    if (!has_value) {
      return static_cast<uint64_t>(0);
    }
    if (read_type) {
      FORY_TRY(type_id_read, ctx.read_varuint32());
      if (type_id_read != static_cast<uint32_t>(type_id)) {
        return Unexpected(
            Error::type_mismatch(type_id_read, static_cast<uint32_t>(type_id)));
      }
    }
    uint64_t value;
    FORY_RETURN_NOT_OK(ctx.read_bytes(&value, sizeof(uint64_t)));
    return value;
  }

  static inline Result<uint64_t, Error> read_data(ReadContext &ctx) {
    uint64_t value;
    FORY_RETURN_NOT_OK(ctx.read_bytes(&value, sizeof(uint64_t)));
    return value;
  }

  static inline Result<uint64_t, Error> read_data_generic(ReadContext &ctx,
                                                          bool has_generics) {
    return read_data(ctx);
  }

  static inline Result<uint64_t, Error>
  read_with_type_info(ReadContext &ctx, bool read_ref,
                      const TypeInfo &type_info) {
    return read(ctx, read_ref, false);
  }
};

// ============================================================================
// String Serializer
// ============================================================================

/// std::string serializer using UTF-8 encoding per xlang spec
template <> struct Serializer<std::string> {
  static constexpr TypeId type_id = TypeId::STRING;

  // String encoding types as per xlang spec
  enum class StringEncoding : uint8_t {
    LATIN1 = 0, // Latin1/ISO-8859-1
    UTF16 = 1,  // UTF-16
    UTF8 = 2,   // UTF-8
  };

  static inline Result<void, Error> write(const std::string &value,
                                          WriteContext &ctx, bool write_ref,
                                          bool write_type, bool has_generics = false) {
    write_not_null_ref_flag(ctx, write_ref);
    if (write_type) {
      ctx.write_varuint32(static_cast<uint32_t>(type_id));
    }
    return write_data_generic(value, ctx, has_generics);
  }

  static inline Result<void, Error> write_data(const std::string &value,
                                               WriteContext &ctx) {
    // Always use UTF-8 encoding for cross-language compatibility.
    // Per xlang spec: write size shifted left by 2 bits, with encoding
    // (UTF8) in the lower 2 bits.
    uint32_t length = static_cast<uint32_t>(value.size());
    uint32_t size_with_encoding =
        (length << 2) | static_cast<uint32_t>(StringEncoding::UTF8);
    size_t pos_before = ctx.buffer().writer_index();
    std::cerr << "[DEBUG] string write_data: pos_before=" << pos_before
              << ", length=" << length
              << ", size_with_encoding=" << size_with_encoding
              << ", encoding=" << static_cast<uint32_t>(StringEncoding::UTF8)
              << ", value=\"" << value << "\"" << std::endl;
    ctx.write_varuint32(size_with_encoding);
    size_t pos_after_header = ctx.buffer().writer_index();
    std::cerr << "[DEBUG]   wrote " << (pos_after_header - pos_before)
              << " bytes for header, value=0x" << std::hex << size_with_encoding << std::dec << std::endl;

    // Write string bytes
    if (!value.empty()) {
      ctx.write_bytes(value.data(), value.size());
    }
    size_t pos_after = ctx.buffer().writer_index();
    std::cerr << "[DEBUG]   total bytes written=" << (pos_after - pos_before) << std::endl;
    return Result<void, Error>();
  }

  static inline Result<void, Error> write_data_generic(const std::string &value,
                                                       WriteContext &ctx,
                                                       bool has_generics) {
    return write_data(value, ctx);
  }

  static inline Result<std::string, Error> read(ReadContext &ctx, bool read_ref,
                                                bool read_type) {
    FORY_TRY(has_value, consume_ref_flag(ctx, read_ref));
    if (!has_value) {
      return std::string();
    }
    if (read_type) {
      FORY_TRY(type_id_read, ctx.read_varuint32());
      if (type_id_read != static_cast<uint32_t>(type_id)) {
        return Unexpected(
            Error::type_mismatch(type_id_read, static_cast<uint32_t>(type_id)));
      }
    }
    return read_data(ctx);
  }

  static inline Result<std::string, Error> read_data(ReadContext &ctx) {
    // Read size with encoding
    size_t pos_before = ctx.buffer().reader_index();
    FORY_TRY(size_with_encoding, ctx.read_varuint32());

    // Extract size and encoding from lower 2 bits
    uint32_t length = size_with_encoding >> 2;
    StringEncoding encoding = static_cast<StringEncoding>(size_with_encoding & 0x3);
    std::cerr << "[DEBUG] string read_data: pos=" << pos_before
              << ", size_with_encoding=0x" << std::hex << size_with_encoding << std::dec
              << " (" << size_with_encoding << ")"
              << ", length=" << length
              << ", encoding=" << static_cast<int>(encoding);

    // Print surrounding bytes for debugging
    if (static_cast<int>(encoding) > 2) {
      std::cerr << "\n[ERROR] Invalid encoding detected! Buffer context (pos-5 to pos+10):\n  ";
      for (int i = -5; i <= 10 && static_cast<int>(pos_before) + i < static_cast<int>(ctx.buffer().size()); ++i) {
        int byte_pos = static_cast<int>(pos_before) + i;
        if (byte_pos >= 0) {
          std::cerr << std::hex << std::setw(2) << std::setfill('0')
                    << static_cast<int>(ctx.buffer().data()[byte_pos]) << " ";
        }
      }
      std::cerr << std::dec << std::endl;
    } else {
      std::cerr << std::endl;
    }

    if (length == 0) {
      return std::string();
    }

    // Handle different encodings
    switch (encoding) {
      case StringEncoding::LATIN1: {
        // Latin1: each byte is a character, convert to UTF-8
        std::vector<uint8_t> bytes(length);
        FORY_RETURN_NOT_OK(ctx.read_bytes(bytes.data(), length));
        std::string result;
        result.reserve(length * 2); // Reserve extra space for multi-byte UTF-8
        for (uint8_t byte : bytes) {
          if (byte < 128) {
            result.push_back(static_cast<char>(byte));
          } else {
            // Convert Latin1 to UTF-8 (2 bytes for chars >= 128)
            result.push_back(static_cast<char>(0xC0 | (byte >> 6)));
            result.push_back(static_cast<char>(0x80 | (byte & 0x3F)));
          }
        }
        return result;
      }
      case StringEncoding::UTF16: {
        // UTF-16: read pairs of bytes and convert to UTF-8
        if (length % 2 != 0) {
          return Unexpected(Error::invalid_data("UTF-16 length must be even"));
        }
        std::vector<uint16_t> utf16_chars(length / 2);
        FORY_RETURN_NOT_OK(ctx.read_bytes(reinterpret_cast<uint8_t*>(utf16_chars.data()), length));

        std::string result;
        result.reserve(length * 2); // Approximate - may need more for surrogates

        for (size_t i = 0; i < utf16_chars.size(); ++i) {
          uint16_t ch = utf16_chars[i];

          // Check for surrogate pair
          if (ch >= 0xD800 && ch <= 0xDBFF && i + 1 < utf16_chars.size()) {
            // High surrogate
            uint16_t low = utf16_chars[i + 1];
            if (low >= 0xDC00 && low <= 0xDFFF) {
              // Valid surrogate pair - convert to code point
              uint32_t codepoint = 0x10000 + ((ch & 0x3FF) << 10) + (low & 0x3FF);
              ++i; // Skip the low surrogate

              // Encode as UTF-8 (4 bytes for supplementary planes)
              result.push_back(static_cast<char>(0xF0 | (codepoint >> 18)));
              result.push_back(static_cast<char>(0x80 | ((codepoint >> 12) & 0x3F)));
              result.push_back(static_cast<char>(0x80 | ((codepoint >> 6) & 0x3F)));
              result.push_back(static_cast<char>(0x80 | (codepoint & 0x3F)));
              continue;
            }
          }

          // Not a surrogate pair - encode as regular UTF-8
          if (ch < 0x80) {
            result.push_back(static_cast<char>(ch));
          } else if (ch < 0x800) {
            result.push_back(static_cast<char>(0xC0 | (ch >> 6)));
            result.push_back(static_cast<char>(0x80 | (ch & 0x3F)));
          } else {
            result.push_back(static_cast<char>(0xE0 | (ch >> 12)));
            result.push_back(static_cast<char>(0x80 | ((ch >> 6) & 0x3F)));
            result.push_back(static_cast<char>(0x80 | (ch & 0x3F)));
          }
        }
        return result;
      }
      case StringEncoding::UTF8: {
        // UTF-8: read bytes directly
        std::string result(length, '\0');
        FORY_RETURN_NOT_OK(ctx.read_bytes(&result[0], length));
        return result;
      }
      default:
        std::cerr << "[ERROR] Unknown string encoding=" << static_cast<int>(encoding)
                  << ", size_with_encoding=0x" << std::hex << size_with_encoding << std::dec
                  << " (" << size_with_encoding << ")"
                  << ", length=" << length
                  << ", buffer_pos=" << ctx.buffer().reader_index() - 1 << std::endl;
        return Unexpected(Error::encoding_error("Unknown string encoding"));
    }
  }

  static inline Result<std::string, Error>
  read_data_generic(ReadContext &ctx, bool has_generics) {
    return read_data(ctx);
  }

  static inline Result<std::string, Error>
  read_with_type_info(ReadContext &ctx, bool read_ref,
                      const TypeInfo &type_info) {
    return read(ctx, read_ref, false);
  }
};

} // namespace serialization
} // namespace fory
