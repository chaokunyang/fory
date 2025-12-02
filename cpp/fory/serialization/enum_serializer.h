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

#include "fory/meta/enum_info.h"
#include "fory/serialization/context.h"
#include "fory/serialization/serializer.h"
#include "fory/type/type.h"
#include "fory/util/error.h"
#include "fory/util/result.h"
#include <cstdint>
#include <type_traits>

#ifdef FORY_DEBUG
#include <iostream>
#endif

namespace fory {
namespace serialization {

/// Serializer specialization for enum types.
///
/// Writes the enum ordinal (underlying integral value) to match the xlang
/// specification for value-based enums.
template <typename E>
struct Serializer<E, std::enable_if_t<std::is_enum_v<E>>> {
  static constexpr TypeId type_id = TypeId::ENUM;

  using Metadata = meta::EnumMetadata<E>;
  using OrdinalType = typename Metadata::OrdinalType;

  static inline void write_type_info(WriteContext &ctx, Error *error) {
    // Use compile-time type lookup for faster enum type info writing
    auto result = ctx.write_enum_typeinfo<E>();
    if (FORY_PREDICT_FALSE(!result.ok())) {
      *error = std::move(result.error());
    }
  }

  static inline void read_type_info(ReadContext &ctx, Error *error) {
    auto type_info_result = ctx.read_any_typeinfo();
    if (FORY_PREDICT_FALSE(!type_info_result.ok())) {
      *error = std::move(type_info_result.error());
      return;
    }
    const TypeInfo *type_info = type_info_result.value();
    if (!type_id_matches(type_info->type_id, static_cast<uint32_t>(type_id))) {
      error->set_error(ErrorCode::TypeMismatch,
                       "Type mismatch: expected " +
                           std::to_string(static_cast<uint32_t>(type_id)) +
                           ", got " + std::to_string(type_info->type_id));
    }
  }

  static inline void write(E value, WriteContext &ctx, bool write_ref,
                           bool write_type, bool has_generics, Error *error) {
    write_not_null_ref_flag(ctx, write_ref);
    if (write_type) {
      write_type_info(ctx, error);
      if (FORY_PREDICT_FALSE(!error->ok())) {
        return;
      }
    }
    write_data_generic(value, ctx, has_generics, error);
  }

  static inline void write_data(E value, WriteContext &ctx, Error *error) {
    OrdinalType ordinal{};
    if (!Metadata::to_ordinal(value, &ordinal)) {
      error->set_error(ErrorCode::UnknownEnum, "Unknown enum value");
      return;
    }
    // Enums are encoded as unsigned varints in the xlang spec and in
    // the Java implementation (see EnumSerializer.xwrite).  Use
    // varuint32 here instead of the generic int32 zig-zag encoding so
    // that ordinal bytes are identical across languages.
    ctx.write_varuint32(static_cast<uint32_t>(ordinal));
  }

  static inline void write_data_generic(E value, WriteContext &ctx,
                                        bool has_generics, Error *error) {
    (void)has_generics;
    write_data(value, ctx, error);
  }

  static inline E read(ReadContext &ctx, bool read_ref, bool read_type,
                       Error *error) {
    // Java xlang object serializer treats enum fields (in the
    // "other" group) as nullable values with an explicit null flag
    // in front of the ordinal, but does not use the general
    // reference-tracking protocol for them. When reading xlang
    // payloads and the caller did not request reference metadata,
    // mirror that layout: consume a single null/not-null flag and
    // then read the ordinal.
    if (ctx.is_xlang() && !read_ref) {
      int8_t flag = ctx.read_int8(error);
      if (FORY_PREDICT_FALSE(!error->ok())) {
        return E{};
      }
      if (flag == NULL_FLAG) {
        // Represent Java null as the default enum value.
        return E{};
      }
      // For NOT_NULL_VALUE_FLAG or REF_VALUE_FLAG we simply proceed to
      // read the ordinal; schema-consistent named enums are handled at
      // a higher layer via type metadata.
      return read_data(ctx, error);
    }

    bool has_value = consume_ref_flag(ctx, read_ref, error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return E{};
    }
    if (!has_value) {
      return E{};
    }
    if (read_type) {
      // Use overload without type_index (fast path)
      auto result = ctx.read_enum_type_info(static_cast<uint32_t>(type_id));
      if (FORY_PREDICT_FALSE(!result.ok())) {
        *error = std::move(result.error());
        return E{};
      }
    }
    return read_data(ctx, error);
  }

  static inline E read_data(ReadContext &ctx, Error *error) {
    uint32_t raw_ordinal = ctx.read_varuint32(error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return E{};
    }
    OrdinalType ordinal = static_cast<OrdinalType>(raw_ordinal);
    E value{};
    if (!Metadata::from_ordinal(ordinal, &value)) {
      error->set_error(
          ErrorCode::UnknownEnum,
          "Invalid ordinal value: " +
              std::to_string(static_cast<long long>(ordinal)));
      return E{};
    }
    return value;
  }

  static inline E read_data_generic(ReadContext &ctx, bool has_generics,
                                    Error *error) {
    (void)has_generics;
    return read_data(ctx, error);
  }

  static inline E read_with_type_info(ReadContext &ctx, bool read_ref,
                                      const TypeInfo &type_info, Error *error) {
    (void)type_info;
    // Type info already validated, skip redundant type read
    return read(ctx, read_ref, false, error);
  }
};

} // namespace serialization
} // namespace fory
