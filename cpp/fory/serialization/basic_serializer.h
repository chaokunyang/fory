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

  /// Write type info only (for collection/map element type headers)
  static inline void write_type_info(WriteContext &ctx, Error *error) {
    (void)error; // Primitives don't produce write errors
    ctx.write_varuint32(static_cast<uint32_t>(type_id));
  }

  /// Read and validate type info
  static inline void read_type_info(ReadContext &ctx, Error *error) {
    uint32_t actual = ctx.read_varuint32(error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return;
    }
    if (actual != static_cast<uint32_t>(type_id)) {
      error->set_error(ErrorCode::TypeMismatch,
                       "Type mismatch: expected " +
                           std::to_string(static_cast<uint32_t>(type_id)) +
                           ", got " + std::to_string(actual));
    }
  }

  /// Write boolean with optional reference and type info
  static inline void write(bool value, WriteContext &ctx, bool write_ref,
                           bool write_type, bool has_generics, Error *error) {
    write_not_null_ref_flag(ctx, write_ref);
    if (write_type) {
      ctx.write_varuint32(static_cast<uint32_t>(type_id));
    }
    write_data_generic(value, ctx, has_generics, error);
  }

  /// Write boolean data only (no type info)
  static inline void write_data(bool value, WriteContext &ctx, Error *error) {
    (void)error;
    ctx.write_uint8(value ? 1 : 0);
  }

  /// Write boolean with generic optimization (unused for primitives)
  static inline void write_data_generic(bool value, WriteContext &ctx,
                                        bool has_generics, Error *error) {
    (void)has_generics;
    write_data(value, ctx, error);
  }

  /// Read boolean with optional reference and type info
  static inline bool read(ReadContext &ctx, bool read_ref, bool read_type,
                          Error *error) {
    bool has_value = consume_ref_flag(ctx, read_ref, error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return false;
    }
    if (!has_value) {
      return false;
    }
    if (read_type) {
      uint32_t type_id_read = ctx.read_varuint32(error);
      if (FORY_PREDICT_FALSE(!error->ok())) {
        return false;
      }
      if (type_id_read != static_cast<uint32_t>(type_id)) {
        error->set_error(ErrorCode::TypeMismatch,
                         "Type mismatch: expected " +
                             std::to_string(static_cast<uint32_t>(type_id)) +
                             ", got " + std::to_string(type_id_read));
        return false;
      }
    }
    uint8_t value = ctx.read_uint8(error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return false;
    }
    return value != 0;
  }

  /// Read boolean data only (no type info)
  static inline bool read_data(ReadContext &ctx, Error *error) {
    uint8_t value = ctx.read_uint8(error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return false;
    }
    return value != 0;
  }

  /// Read boolean with generic optimization (unused for primitives)
  static inline bool read_data_generic(ReadContext &ctx, bool has_generics,
                                       Error *error) {
    (void)has_generics;
    return read_data(ctx, error);
  }

  /// Read boolean with type info (type info already validated)
  static inline bool read_with_type_info(ReadContext &ctx, bool read_ref,
                                         const TypeInfo &type_info,
                                         Error *error) {
    (void)type_info;
    // Type info already validated, skip redundant type read
    return read(ctx, read_ref, false, error);
  }
};

/// int8_t serializer
template <> struct Serializer<int8_t> {
  static constexpr TypeId type_id = TypeId::INT8;

  static inline void write_type_info(WriteContext &ctx, Error *error) {
    (void)error;
    ctx.write_varuint32(static_cast<uint32_t>(type_id));
  }

  static inline void read_type_info(ReadContext &ctx, Error *error) {
    uint32_t actual = ctx.read_varuint32(error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return;
    }
    if (actual != static_cast<uint32_t>(type_id)) {
      error->set_error(ErrorCode::TypeMismatch,
                       "Type mismatch: expected " +
                           std::to_string(static_cast<uint32_t>(type_id)) +
                           ", got " + std::to_string(actual));
    }
  }

  static inline void write(int8_t value, WriteContext &ctx, bool write_ref,
                           bool write_type, bool has_generics, Error *error) {
    write_not_null_ref_flag(ctx, write_ref);
    if (write_type) {
      ctx.write_varuint32(static_cast<uint32_t>(type_id));
    }
    write_data_generic(value, ctx, has_generics, error);
  }

  static inline void write_data(int8_t value, WriteContext &ctx, Error *error) {
    (void)error;
    ctx.write_int8(value);
  }

  static inline void write_data_generic(int8_t value, WriteContext &ctx,
                                        bool has_generics, Error *error) {
    (void)has_generics;
    write_data(value, ctx, error);
  }

  static inline int8_t read(ReadContext &ctx, bool read_ref, bool read_type,
                            Error *error) {
    bool has_value = consume_ref_flag(ctx, read_ref, error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return 0;
    }
    if (!has_value) {
      return 0;
    }
    if (read_type) {
      uint32_t type_id_read = ctx.read_varuint32(error);
      if (FORY_PREDICT_FALSE(!error->ok())) {
        return 0;
      }
      if (type_id_read != static_cast<uint32_t>(type_id)) {
        error->set_error(ErrorCode::TypeMismatch,
                         "Type mismatch: expected " +
                             std::to_string(static_cast<uint32_t>(type_id)) +
                             ", got " + std::to_string(type_id_read));
        return 0;
      }
    }
    return ctx.read_int8(error);
  }

  static inline int8_t read_data(ReadContext &ctx, Error *error) {
    return ctx.read_int8(error);
  }

  static inline int8_t read_data_generic(ReadContext &ctx, bool has_generics,
                                         Error *error) {
    (void)has_generics;
    return read_data(ctx, error);
  }

  static inline int8_t read_with_type_info(ReadContext &ctx, bool read_ref,
                                           const TypeInfo &type_info,
                                           Error *error) {
    (void)type_info;
    return read(ctx, read_ref, false, error);
  }
};

/// int16_t serializer
template <> struct Serializer<int16_t> {
  static constexpr TypeId type_id = TypeId::INT16;

  static inline void write_type_info(WriteContext &ctx, Error *error) {
    (void)error;
    ctx.write_varuint32(static_cast<uint32_t>(type_id));
  }

  static inline void read_type_info(ReadContext &ctx, Error *error) {
    uint32_t actual = ctx.read_varuint32(error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return;
    }
    if (actual != static_cast<uint32_t>(type_id)) {
      error->set_error(ErrorCode::TypeMismatch,
                       "Type mismatch: expected " +
                           std::to_string(static_cast<uint32_t>(type_id)) +
                           ", got " + std::to_string(actual));
    }
  }

  static inline void write(int16_t value, WriteContext &ctx, bool write_ref,
                           bool write_type, bool has_generics, Error *error) {
    write_not_null_ref_flag(ctx, write_ref);
    if (write_type) {
      ctx.write_varuint32(static_cast<uint32_t>(type_id));
    }
    write_data_generic(value, ctx, has_generics, error);
  }

  static inline void write_data(int16_t value, WriteContext &ctx,
                                Error *error) {
    (void)error;
    ctx.write_bytes(&value, sizeof(int16_t));
  }

  static inline void write_data_generic(int16_t value, WriteContext &ctx,
                                        bool has_generics, Error *error) {
    (void)has_generics;
    write_data(value, ctx, error);
  }

  static inline int16_t read(ReadContext &ctx, bool read_ref, bool read_type,
                             Error *error) {
    bool has_value = consume_ref_flag(ctx, read_ref, error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return 0;
    }
    if (!has_value) {
      return 0;
    }
    if (read_type) {
      uint32_t type_id_read = ctx.read_varuint32(error);
      if (FORY_PREDICT_FALSE(!error->ok())) {
        return 0;
      }
      if (type_id_read != static_cast<uint32_t>(type_id)) {
        error->set_error(ErrorCode::TypeMismatch,
                         "Type mismatch: expected " +
                             std::to_string(static_cast<uint32_t>(type_id)) +
                             ", got " + std::to_string(type_id_read));
        return 0;
      }
    }
    return ctx.read_int16(error);
  }

  static inline int16_t read_data(ReadContext &ctx, Error *error) {
    return ctx.read_int16(error);
  }

  static inline int16_t read_data_generic(ReadContext &ctx, bool has_generics,
                                          Error *error) {
    (void)has_generics;
    return read_data(ctx, error);
  }

  static inline int16_t read_with_type_info(ReadContext &ctx, bool read_ref,
                                            const TypeInfo &type_info,
                                            Error *error) {
    (void)type_info;
    return read(ctx, read_ref, false, error);
  }
};

/// int32_t serializer
template <> struct Serializer<int32_t> {
  static constexpr TypeId type_id = TypeId::INT32;

  static inline void write_type_info(WriteContext &ctx, Error *error) {
    (void)error;
    ctx.write_varuint32(static_cast<uint32_t>(type_id));
  }

  static inline void read_type_info(ReadContext &ctx, Error *error) {
    uint32_t actual = ctx.read_varuint32(error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return;
    }
    if (actual != static_cast<uint32_t>(type_id)) {
      error->set_error(ErrorCode::TypeMismatch,
                       "Type mismatch: expected " +
                           std::to_string(static_cast<uint32_t>(type_id)) +
                           ", got " + std::to_string(actual));
    }
  }

  static inline void write(int32_t value, WriteContext &ctx, bool write_ref,
                           bool write_type, bool has_generics, Error *error) {
    write_not_null_ref_flag(ctx, write_ref);
    if (write_type) {
      ctx.write_varuint32(static_cast<uint32_t>(type_id));
    }
    write_data_generic(value, ctx, has_generics, error);
  }

  static inline void write_data(int32_t value, WriteContext &ctx,
                                Error *error) {
    (void)error;
    ctx.write_varint32(value);
  }

  static inline void write_data_generic(int32_t value, WriteContext &ctx,
                                        bool has_generics, Error *error) {
    (void)has_generics;
    write_data(value, ctx, error);
  }

  static inline int32_t read(ReadContext &ctx, bool read_ref, bool read_type,
                             Error *error) {
    bool has_value = consume_ref_flag(ctx, read_ref, error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return 0;
    }
    if (!has_value) {
      return 0;
    }
    if (read_type) {
      uint32_t type_id_read = ctx.read_varuint32(error);
      if (FORY_PREDICT_FALSE(!error->ok())) {
        return 0;
      }
      if (type_id_read != static_cast<uint32_t>(type_id)) {
        error->set_error(ErrorCode::TypeMismatch,
                         "Type mismatch: expected " +
                             std::to_string(static_cast<uint32_t>(type_id)) +
                             ", got " + std::to_string(type_id_read));
        return 0;
      }
    }
    return ctx.read_varint32(error);
  }

  static inline int32_t read_data(ReadContext &ctx, Error *error) {
    return ctx.read_varint32(error);
  }

  static inline int32_t read_data_generic(ReadContext &ctx, bool has_generics,
                                          Error *error) {
    (void)has_generics;
    return read_data(ctx, error);
  }

  static inline int32_t read_with_type_info(ReadContext &ctx, bool read_ref,
                                            const TypeInfo &type_info,
                                            Error *error) {
    (void)type_info;
    return read(ctx, read_ref, false, error);
  }
};

/// int64_t serializer
template <> struct Serializer<int64_t> {
  static constexpr TypeId type_id = TypeId::INT64;

  static inline void write_type_info(WriteContext &ctx, Error *error) {
    (void)error;
    ctx.write_varuint32(static_cast<uint32_t>(type_id));
  }

  static inline void read_type_info(ReadContext &ctx, Error *error) {
    uint32_t actual = ctx.read_varuint32(error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return;
    }
    if (actual != static_cast<uint32_t>(type_id)) {
      error->set_error(ErrorCode::TypeMismatch,
                       "Type mismatch: expected " +
                           std::to_string(static_cast<uint32_t>(type_id)) +
                           ", got " + std::to_string(actual));
    }
  }

  static inline void write(int64_t value, WriteContext &ctx, bool write_ref,
                           bool write_type, bool has_generics, Error *error) {
    write_not_null_ref_flag(ctx, write_ref);
    if (write_type) {
      ctx.write_varuint32(static_cast<uint32_t>(type_id));
    }
    write_data_generic(value, ctx, has_generics, error);
  }

  static inline void write_data(int64_t value, WriteContext &ctx,
                                Error *error) {
    (void)error;
    ctx.write_varint64(value);
  }

  static inline void write_data_generic(int64_t value, WriteContext &ctx,
                                        bool has_generics, Error *error) {
    (void)has_generics;
    write_data(value, ctx, error);
  }

  static inline int64_t read(ReadContext &ctx, bool read_ref, bool read_type,
                             Error *error) {
    bool has_value = consume_ref_flag(ctx, read_ref, error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return 0;
    }
    if (!has_value) {
      return 0;
    }
    if (read_type) {
      uint32_t type_id_read = ctx.read_varuint32(error);
      if (FORY_PREDICT_FALSE(!error->ok())) {
        return 0;
      }
      if (type_id_read != static_cast<uint32_t>(type_id)) {
        error->set_error(ErrorCode::TypeMismatch,
                         "Type mismatch: expected " +
                             std::to_string(static_cast<uint32_t>(type_id)) +
                             ", got " + std::to_string(type_id_read));
        return 0;
      }
    }
    return ctx.read_varint64(error);
  }

  static inline int64_t read_data(ReadContext &ctx, Error *error) {
    return ctx.read_varint64(error);
  }

  static inline int64_t read_data_generic(ReadContext &ctx, bool has_generics,
                                          Error *error) {
    (void)has_generics;
    return read_data(ctx, error);
  }

  static inline int64_t read_with_type_info(ReadContext &ctx, bool read_ref,
                                            const TypeInfo &type_info,
                                            Error *error) {
    (void)type_info;
    return read(ctx, read_ref, false, error);
  }
};

/// float serializer
template <> struct Serializer<float> {
  static constexpr TypeId type_id = TypeId::FLOAT32;

  static inline void write_type_info(WriteContext &ctx, Error *error) {
    (void)error;
    ctx.write_varuint32(static_cast<uint32_t>(type_id));
  }

  static inline void read_type_info(ReadContext &ctx, Error *error) {
    uint32_t actual = ctx.read_varuint32(error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return;
    }
    if (actual != static_cast<uint32_t>(type_id)) {
      error->set_error(ErrorCode::TypeMismatch,
                       "Type mismatch: expected " +
                           std::to_string(static_cast<uint32_t>(type_id)) +
                           ", got " + std::to_string(actual));
    }
  }

  static inline void write(float value, WriteContext &ctx, bool write_ref,
                           bool write_type, bool has_generics, Error *error) {
    write_not_null_ref_flag(ctx, write_ref);
    if (write_type) {
      ctx.write_varuint32(static_cast<uint32_t>(type_id));
    }
    write_data_generic(value, ctx, has_generics, error);
  }

  static inline void write_data(float value, WriteContext &ctx, Error *error) {
    (void)error;
    ctx.write_bytes(&value, sizeof(float));
  }

  static inline void write_data_generic(float value, WriteContext &ctx,
                                        bool has_generics, Error *error) {
    (void)has_generics;
    write_data(value, ctx, error);
  }

  static inline float read(ReadContext &ctx, bool read_ref, bool read_type,
                           Error *error) {
    bool has_value = consume_ref_flag(ctx, read_ref, error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return 0.0f;
    }
    if (!has_value) {
      return 0.0f;
    }
    if (read_type) {
      uint32_t type_id_read = ctx.read_varuint32(error);
      if (FORY_PREDICT_FALSE(!error->ok())) {
        return 0.0f;
      }
      if (type_id_read != static_cast<uint32_t>(type_id)) {
        error->set_error(ErrorCode::TypeMismatch,
                         "Type mismatch: expected " +
                             std::to_string(static_cast<uint32_t>(type_id)) +
                             ", got " + std::to_string(type_id_read));
        return 0.0f;
      }
    }
    return ctx.read_float(error);
  }

  static inline float read_data(ReadContext &ctx, Error *error) {
    return ctx.read_float(error);
  }

  static inline float read_data_generic(ReadContext &ctx, bool has_generics,
                                        Error *error) {
    (void)has_generics;
    return read_data(ctx, error);
  }

  static inline float read_with_type_info(ReadContext &ctx, bool read_ref,
                                          const TypeInfo &type_info,
                                          Error *error) {
    (void)type_info;
    return read(ctx, read_ref, false, error);
  }
};

/// double serializer
template <> struct Serializer<double> {
  static constexpr TypeId type_id = TypeId::FLOAT64;

  static inline void write_type_info(WriteContext &ctx, Error *error) {
    (void)error;
    ctx.write_varuint32(static_cast<uint32_t>(type_id));
  }

  static inline void read_type_info(ReadContext &ctx, Error *error) {
    uint32_t actual = ctx.read_varuint32(error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return;
    }
    if (actual != static_cast<uint32_t>(type_id)) {
      error->set_error(ErrorCode::TypeMismatch,
                       "Type mismatch: expected " +
                           std::to_string(static_cast<uint32_t>(type_id)) +
                           ", got " + std::to_string(actual));
    }
  }

  static inline void write(double value, WriteContext &ctx, bool write_ref,
                           bool write_type, bool has_generics, Error *error) {
    write_not_null_ref_flag(ctx, write_ref);
    if (write_type) {
      ctx.write_varuint32(static_cast<uint32_t>(type_id));
    }
    write_data_generic(value, ctx, has_generics, error);
  }

  static inline void write_data(double value, WriteContext &ctx,
                                Error *error) {
    (void)error;
    ctx.write_bytes(&value, sizeof(double));
  }

  static inline void write_data_generic(double value, WriteContext &ctx,
                                        bool has_generics, Error *error) {
    (void)has_generics;
    write_data(value, ctx, error);
  }

  static inline double read(ReadContext &ctx, bool read_ref, bool read_type,
                            Error *error) {
    bool has_value = consume_ref_flag(ctx, read_ref, error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return 0.0;
    }
    if (!has_value) {
      return 0.0;
    }
    if (read_type) {
      uint32_t type_id_read = ctx.read_varuint32(error);
      if (FORY_PREDICT_FALSE(!error->ok())) {
        return 0.0;
      }
      if (type_id_read != static_cast<uint32_t>(type_id)) {
        error->set_error(ErrorCode::TypeMismatch,
                         "Type mismatch: expected " +
                             std::to_string(static_cast<uint32_t>(type_id)) +
                             ", got " + std::to_string(type_id_read));
        return 0.0;
      }
    }
    return ctx.read_double(error);
  }

  static inline double read_data(ReadContext &ctx, Error *error) {
    return ctx.read_double(error);
  }

  static inline double read_data_generic(ReadContext &ctx, bool has_generics,
                                         Error *error) {
    (void)has_generics;
    return read_data(ctx, error);
  }

  static inline double read_with_type_info(ReadContext &ctx, bool read_ref,
                                           const TypeInfo &type_info,
                                           Error *error) {
    (void)type_info;
    return read(ctx, read_ref, false, error);
  }
};

// ============================================================================
// Unsigned Integer Type Serializers
// ============================================================================

/// uint8_t serializer
template <> struct Serializer<uint8_t> {
  static constexpr TypeId type_id = TypeId::INT8; // Same as int8

  static inline void write_type_info(WriteContext &ctx, Error *error) {
    (void)error;
    ctx.write_varuint32(static_cast<uint32_t>(type_id));
  }

  static inline void read_type_info(ReadContext &ctx, Error *error) {
    uint32_t actual = ctx.read_varuint32(error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return;
    }
    if (actual != static_cast<uint32_t>(type_id)) {
      error->set_error(ErrorCode::TypeMismatch,
                       "Type mismatch: expected " +
                           std::to_string(static_cast<uint32_t>(type_id)) +
                           ", got " + std::to_string(actual));
    }
  }

  static inline void write(uint8_t value, WriteContext &ctx, bool write_ref,
                           bool write_type, bool has_generics, Error *error) {
    write_not_null_ref_flag(ctx, write_ref);
    if (write_type) {
      ctx.write_varuint32(static_cast<uint32_t>(type_id));
    }
    write_data_generic(value, ctx, has_generics, error);
  }

  static inline void write_data(uint8_t value, WriteContext &ctx,
                                Error *error) {
    (void)error;
    ctx.write_uint8(value);
  }

  static inline void write_data_generic(uint8_t value, WriteContext &ctx,
                                        bool has_generics, Error *error) {
    (void)has_generics;
    write_data(value, ctx, error);
  }

  static inline uint8_t read(ReadContext &ctx, bool read_ref, bool read_type,
                             Error *error) {
    bool has_value = consume_ref_flag(ctx, read_ref, error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return 0;
    }
    if (!has_value) {
      return 0;
    }
    if (read_type) {
      uint32_t type_id_read = ctx.read_varuint32(error);
      if (FORY_PREDICT_FALSE(!error->ok())) {
        return 0;
      }
      if (type_id_read != static_cast<uint32_t>(type_id)) {
        error->set_error(ErrorCode::TypeMismatch,
                         "Type mismatch: expected " +
                             std::to_string(static_cast<uint32_t>(type_id)) +
                             ", got " + std::to_string(type_id_read));
        return 0;
      }
    }
    return ctx.read_uint8(error);
  }

  static inline uint8_t read_data(ReadContext &ctx, Error *error) {
    return ctx.read_uint8(error);
  }

  static inline uint8_t read_data_generic(ReadContext &ctx, bool has_generics,
                                          Error *error) {
    (void)has_generics;
    return read_data(ctx, error);
  }

  static inline uint8_t read_with_type_info(ReadContext &ctx, bool read_ref,
                                            const TypeInfo &type_info,
                                            Error *error) {
    (void)type_info;
    return read(ctx, read_ref, false, error);
  }
};

/// uint16_t serializer
template <> struct Serializer<uint16_t> {
  static constexpr TypeId type_id = TypeId::INT16;

  static inline void write_type_info(WriteContext &ctx, Error *error) {
    (void)error;
    ctx.write_varuint32(static_cast<uint32_t>(type_id));
  }

  static inline void read_type_info(ReadContext &ctx, Error *error) {
    uint32_t actual = ctx.read_varuint32(error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return;
    }
    if (actual != static_cast<uint32_t>(type_id)) {
      error->set_error(ErrorCode::TypeMismatch,
                       "Type mismatch: expected " +
                           std::to_string(static_cast<uint32_t>(type_id)) +
                           ", got " + std::to_string(actual));
    }
  }

  static inline void write(uint16_t value, WriteContext &ctx, bool write_ref,
                           bool write_type, bool has_generics, Error *error) {
    write_not_null_ref_flag(ctx, write_ref);
    if (write_type) {
      ctx.write_varuint32(static_cast<uint32_t>(type_id));
    }
    write_data_generic(value, ctx, has_generics, error);
  }

  static inline void write_data(uint16_t value, WriteContext &ctx,
                                Error *error) {
    (void)error;
    ctx.write_bytes(&value, sizeof(uint16_t));
  }

  static inline void write_data_generic(uint16_t value, WriteContext &ctx,
                                        bool has_generics, Error *error) {
    (void)has_generics;
    write_data(value, ctx, error);
  }

  static inline uint16_t read(ReadContext &ctx, bool read_ref, bool read_type,
                              Error *error) {
    bool has_value = consume_ref_flag(ctx, read_ref, error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return 0;
    }
    if (!has_value) {
      return 0;
    }
    if (read_type) {
      uint32_t type_id_read = ctx.read_varuint32(error);
      if (FORY_PREDICT_FALSE(!error->ok())) {
        return 0;
      }
      if (type_id_read != static_cast<uint32_t>(type_id)) {
        error->set_error(ErrorCode::TypeMismatch,
                         "Type mismatch: expected " +
                             std::to_string(static_cast<uint32_t>(type_id)) +
                             ", got " + std::to_string(type_id_read));
        return 0;
      }
    }
    return ctx.read_uint16(error);
  }

  static inline uint16_t read_data(ReadContext &ctx, Error *error) {
    return ctx.read_uint16(error);
  }

  static inline uint16_t read_data_generic(ReadContext &ctx, bool has_generics,
                                           Error *error) {
    (void)has_generics;
    return read_data(ctx, error);
  }

  static inline uint16_t read_with_type_info(ReadContext &ctx, bool read_ref,
                                             const TypeInfo &type_info,
                                             Error *error) {
    (void)type_info;
    return read(ctx, read_ref, false, error);
  }
};

/// uint32_t serializer
template <> struct Serializer<uint32_t> {
  static constexpr TypeId type_id = TypeId::INT32;

  static inline void write_type_info(WriteContext &ctx, Error *error) {
    (void)error;
    ctx.write_varuint32(static_cast<uint32_t>(type_id));
  }

  static inline void read_type_info(ReadContext &ctx, Error *error) {
    uint32_t actual = ctx.read_varuint32(error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return;
    }
    if (actual != static_cast<uint32_t>(type_id)) {
      error->set_error(ErrorCode::TypeMismatch,
                       "Type mismatch: expected " +
                           std::to_string(static_cast<uint32_t>(type_id)) +
                           ", got " + std::to_string(actual));
    }
  }

  static inline void write(uint32_t value, WriteContext &ctx, bool write_ref,
                           bool write_type, bool has_generics, Error *error) {
    write_not_null_ref_flag(ctx, write_ref);
    if (write_type) {
      ctx.write_varuint32(static_cast<uint32_t>(type_id));
    }
    write_data_generic(value, ctx, has_generics, error);
  }

  static inline void write_data(uint32_t value, WriteContext &ctx,
                                Error *error) {
    (void)error;
    ctx.write_bytes(&value, sizeof(uint32_t));
  }

  static inline void write_data_generic(uint32_t value, WriteContext &ctx,
                                        bool has_generics, Error *error) {
    (void)has_generics;
    write_data(value, ctx, error);
  }

  static inline uint32_t read(ReadContext &ctx, bool read_ref, bool read_type,
                              Error *error) {
    bool has_value = consume_ref_flag(ctx, read_ref, error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return 0;
    }
    if (!has_value) {
      return 0;
    }
    if (read_type) {
      uint32_t type_id_read = ctx.read_varuint32(error);
      if (FORY_PREDICT_FALSE(!error->ok())) {
        return 0;
      }
      if (type_id_read != static_cast<uint32_t>(type_id)) {
        error->set_error(ErrorCode::TypeMismatch,
                         "Type mismatch: expected " +
                             std::to_string(static_cast<uint32_t>(type_id)) +
                             ", got " + std::to_string(type_id_read));
        return 0;
      }
    }
    return ctx.read_uint32(error);
  }

  static inline uint32_t read_data(ReadContext &ctx, Error *error) {
    return ctx.read_uint32(error);
  }

  static inline uint32_t read_data_generic(ReadContext &ctx, bool has_generics,
                                           Error *error) {
    (void)has_generics;
    return read_data(ctx, error);
  }

  static inline uint32_t read_with_type_info(ReadContext &ctx, bool read_ref,
                                             const TypeInfo &type_info,
                                             Error *error) {
    (void)type_info;
    return read(ctx, read_ref, false, error);
  }
};

/// uint64_t serializer
template <> struct Serializer<uint64_t> {
  static constexpr TypeId type_id = TypeId::INT64;

  static inline void write_type_info(WriteContext &ctx, Error *error) {
    (void)error;
    ctx.write_varuint32(static_cast<uint32_t>(type_id));
  }

  static inline void read_type_info(ReadContext &ctx, Error *error) {
    uint32_t actual = ctx.read_varuint32(error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return;
    }
    if (actual != static_cast<uint32_t>(type_id)) {
      error->set_error(ErrorCode::TypeMismatch,
                       "Type mismatch: expected " +
                           std::to_string(static_cast<uint32_t>(type_id)) +
                           ", got " + std::to_string(actual));
    }
  }

  static inline void write(uint64_t value, WriteContext &ctx, bool write_ref,
                           bool write_type, bool has_generics, Error *error) {
    write_not_null_ref_flag(ctx, write_ref);
    if (write_type) {
      ctx.write_varuint32(static_cast<uint32_t>(type_id));
    }
    write_data_generic(value, ctx, has_generics, error);
  }

  static inline void write_data(uint64_t value, WriteContext &ctx,
                                Error *error) {
    (void)error;
    ctx.write_bytes(&value, sizeof(uint64_t));
  }

  static inline void write_data_generic(uint64_t value, WriteContext &ctx,
                                        bool has_generics, Error *error) {
    (void)has_generics;
    write_data(value, ctx, error);
  }

  static inline uint64_t read(ReadContext &ctx, bool read_ref, bool read_type,
                              Error *error) {
    bool has_value = consume_ref_flag(ctx, read_ref, error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return 0;
    }
    if (!has_value) {
      return 0;
    }
    if (read_type) {
      uint32_t type_id_read = ctx.read_varuint32(error);
      if (FORY_PREDICT_FALSE(!error->ok())) {
        return 0;
      }
      if (type_id_read != static_cast<uint32_t>(type_id)) {
        error->set_error(ErrorCode::TypeMismatch,
                         "Type mismatch: expected " +
                             std::to_string(static_cast<uint32_t>(type_id)) +
                             ", got " + std::to_string(type_id_read));
        return 0;
      }
    }
    return ctx.read_uint64(error);
  }

  static inline uint64_t read_data(ReadContext &ctx, Error *error) {
    return ctx.read_uint64(error);
  }

  static inline uint64_t read_data_generic(ReadContext &ctx, bool has_generics,
                                           Error *error) {
    (void)has_generics;
    return read_data(ctx, error);
  }

  static inline uint64_t read_with_type_info(ReadContext &ctx, bool read_ref,
                                             const TypeInfo &type_info,
                                             Error *error) {
    (void)type_info;
    return read(ctx, read_ref, false, error);
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

  static inline void write_type_info(WriteContext &ctx, Error *error) {
    (void)error;
    ctx.write_varuint32(static_cast<uint32_t>(type_id));
  }

  static inline void read_type_info(ReadContext &ctx, Error *error) {
    uint32_t actual = ctx.read_varuint32(error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return;
    }
    if (actual != static_cast<uint32_t>(type_id)) {
      error->set_error(ErrorCode::TypeMismatch,
                       "Type mismatch: expected " +
                           std::to_string(static_cast<uint32_t>(type_id)) +
                           ", got " + std::to_string(actual));
    }
  }

  static inline void write(const std::string &value, WriteContext &ctx,
                           bool write_ref, bool write_type, bool has_generics,
                           Error *error) {
    write_not_null_ref_flag(ctx, write_ref);
    if (write_type) {
      ctx.write_varuint32(static_cast<uint32_t>(type_id));
    }
    write_data_generic(value, ctx, has_generics, error);
  }

  static inline void write_data(const std::string &value, WriteContext &ctx,
                                Error *error) {
    (void)error;
    // Always use UTF-8 encoding for cross-language compatibility.
    // Per xlang spec: write size shifted left by 2 bits, with encoding
    // (UTF8) in the lower 2 bits. Use varuint36small encoding.
    uint64_t length = static_cast<uint64_t>(value.size());
    uint64_t size_with_encoding =
        (length << 2) | static_cast<uint64_t>(StringEncoding::UTF8);
    ctx.write_varuint36small(size_with_encoding);

    // Write string bytes
    if (!value.empty()) {
      ctx.write_bytes(value.data(), value.size());
    }
  }

  static inline void write_data_generic(const std::string &value,
                                        WriteContext &ctx, bool has_generics,
                                        Error *error) {
    (void)has_generics;
    write_data(value, ctx, error);
  }

  static inline std::string read(ReadContext &ctx, bool read_ref,
                                 bool read_type, Error *error) {
    bool has_value = consume_ref_flag(ctx, read_ref, error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return std::string();
    }
    if (!has_value) {
      return std::string();
    }
    if (read_type) {
      uint32_t type_id_read = ctx.read_varuint32(error);
      if (FORY_PREDICT_FALSE(!error->ok())) {
        return std::string();
      }
      if (type_id_read != static_cast<uint32_t>(type_id)) {
        error->set_error(ErrorCode::TypeMismatch,
                         "Type mismatch: expected " +
                             std::to_string(static_cast<uint32_t>(type_id)) +
                             ", got " + std::to_string(type_id_read));
        return std::string();
      }
    }
    return read_data(ctx, error);
  }

  static inline std::string read_data(ReadContext &ctx, Error *error) {
    // Read size with encoding using varuint36small
    uint64_t size_with_encoding = ctx.read_varuint36small(error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return std::string();
    }

    // Extract size and encoding from lower 2 bits
    uint64_t length = size_with_encoding >> 2;
    StringEncoding encoding =
        static_cast<StringEncoding>(size_with_encoding & 0x3);

    if (length == 0) {
      return std::string();
    }

    // Handle different encodings
    switch (encoding) {
    case StringEncoding::LATIN1: {
      std::vector<uint8_t> bytes(length);
      ctx.read_bytes(bytes.data(), length, error);
      if (FORY_PREDICT_FALSE(!error->ok())) {
        return std::string();
      }
      return latin1ToUtf8(bytes.data(), length);
    }
    case StringEncoding::UTF16: {
      if (length % 2 != 0) {
        error->set_error(ErrorCode::InvalidData, "UTF-16 length must be even");
        return std::string();
      }
      std::vector<uint16_t> utf16_chars(length / 2);
      ctx.read_bytes(reinterpret_cast<uint8_t *>(utf16_chars.data()), length,
                     error);
      if (FORY_PREDICT_FALSE(!error->ok())) {
        return std::string();
      }
      return utf16ToUtf8(utf16_chars.data(), utf16_chars.size());
    }
    case StringEncoding::UTF8: {
      // UTF-8: read bytes directly
      std::string result(length, '\0');
      ctx.read_bytes(&result[0], length, error);
      if (FORY_PREDICT_FALSE(!error->ok())) {
        return std::string();
      }
      return result;
    }
    default:
      error->set_error(ErrorCode::EncodingError,
                       "Unknown string encoding: " +
                           std::to_string(static_cast<int>(encoding)));
      return std::string();
    }
  }

  static inline std::string read_data_generic(ReadContext &ctx,
                                              bool has_generics, Error *error) {
    (void)has_generics;
    return read_data(ctx, error);
  }

  static inline std::string read_with_type_info(ReadContext &ctx, bool read_ref,
                                                const TypeInfo &type_info,
                                                Error *error) {
    (void)type_info;
    return read(ctx, read_ref, false, error);
  }
};

} // namespace serialization
} // namespace fory
