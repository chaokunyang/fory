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

  static inline void write_type_info(WriteContext &ctx) {
    // Use compile-time type lookup for faster enum type info writing
    ctx.write_enum_typeinfo<E>();
  }

  static inline void read_type_info(ReadContext &ctx) {
    const TypeInfo *type_info = ctx.read_any_typeinfo(ctx.error());
    if (FORY_PREDICT_FALSE(ctx.has_error())) {
      return;
    }
    if (!type_id_matches(type_info->type_id, static_cast<uint32_t>(type_id))) {
      ctx.set_error(Error::type_mismatch(type_info->type_id,
                                         static_cast<uint32_t>(type_id)));
    }
  }

  static inline void write(E value, WriteContext &ctx, bool write_ref,
                           bool write_type, bool has_generics = false) {
    write_not_null_ref_flag(ctx, write_ref);
    if (write_type) {
      write_type_info(ctx);
    }
    write_data_generic(value, ctx, has_generics);
  }

  static inline void write_data(E value, WriteContext &ctx) {
    OrdinalType ordinal{};
    if (!Metadata::to_ordinal(value, &ordinal)) {
      ctx.set_error(Error::unknown_enum("Unknown enum value"));
      return;
    }
    // Enums are encoded as unsigned varints in the xlang spec
    ctx.write_varuint32(static_cast<uint32_t>(ordinal));
  }

  static inline void write_data_generic(E value, WriteContext &ctx,
                                        bool has_generics) {
    write_data(value, ctx);
  }

  static inline E read(ReadContext &ctx, bool read_ref, bool read_type) {
    // Java xlang object serializer treats enum fields as nullable values
    // with an explicit null flag in front of the ordinal
    if (ctx.is_xlang() && !read_ref) {
      int8_t flag = ctx.read_int8(ctx.error());
      if (FORY_PREDICT_FALSE(ctx.has_error())) {
        return E{};
      }
      if (flag == NULL_FLAG) {
        // Represent Java null as the default enum value.
        return E{};
      }
      return read_data(ctx);
    }

    bool has_value = consume_ref_flag(ctx, read_ref);
    if (ctx.has_error() || !has_value) {
      return E{};
    }
    if (read_type) {
      // Use overload without type_index (fast path)
      ctx.read_enum_type_info(static_cast<uint32_t>(type_id));
      if (FORY_PREDICT_FALSE(ctx.has_error())) {
        return E{};
      }
    }
    return read_data(ctx);
  }

  static inline E read_data(ReadContext &ctx) {
    uint32_t raw_ordinal = ctx.read_varuint32(ctx.error());
    if (FORY_PREDICT_FALSE(ctx.has_error())) {
      return E{};
    }
    OrdinalType ordinal = static_cast<OrdinalType>(raw_ordinal);
    E value{};
    if (!Metadata::from_ordinal(ordinal, &value)) {
      ctx.set_error(
          Error::unknown_enum("Invalid ordinal value: " +
                              std::to_string(static_cast<long long>(ordinal))));
      return E{};
    }
    return value;
  }

  static inline E read_with_type_info(ReadContext &ctx, bool read_ref,
                                      const TypeInfo &type_info) {
    return read(ctx, read_ref, false);
  }
};

} // namespace serialization
} // namespace fory
