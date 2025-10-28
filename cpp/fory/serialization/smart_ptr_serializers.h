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
#include <memory>
#include <optional>
#include <string>

namespace fory {
namespace serialization {

// ============================================================================
// std::optional serializer
// ============================================================================

/// Serializer for std::optional<T>
///
/// Serializes optional values with null handling.
/// Uses NOT_NULL_VALUE_FLAG for values, NULL_FLAG for nullopt.
template <typename T> struct Serializer<std::optional<T>> {
  // Use the inner type's type_id
  static constexpr TypeId type_id = Serializer<T>::type_id;

  static Result<void, Error> write(const std::optional<T> &opt,
                                   WriteContext &ctx, bool write_ref,
                                   bool write_type) {
    constexpr bool inner_requires_ref = requires_ref_metadata_v<T>;

    if (!write_ref) {
      if (!opt.has_value()) {
        return Unexpected(Error::invalid(
            "std::optional requires write_ref=true to encode null state"));
      }
      return Serializer<T>::write(*opt, ctx, inner_requires_ref, write_type);
    }

    if (!opt.has_value()) {
      ctx.write_int8(NULL_FLAG);
      return Result<void, Error>();
    }

    if constexpr (inner_requires_ref) {
      return Serializer<T>::write(*opt, ctx, true, write_type);
    } else {
      ctx.write_int8(NOT_NULL_VALUE_FLAG);
      return Serializer<T>::write(*opt, ctx, false, write_type);
    }
  }

  static Result<void, Error> write_data(const std::optional<T> &opt,
                                        WriteContext &ctx) {
    if (!opt.has_value()) {
      return Unexpected(Error::invalid(
          "std::optional write_data requires value present"));
    }
    return Serializer<T>::write_data(*opt, ctx);
  }

  static Result<void, Error> write_data_generic(const std::optional<T> &opt,
                                                WriteContext &ctx,
                                                bool has_generics) {
    if (!opt.has_value()) {
      return Unexpected(Error::invalid(
          "std::optional write_data requires value present"));
    }
    return Serializer<T>::write_data_generic(*opt, ctx, has_generics);
  }

  static Result<std::optional<T>, Error> read(ReadContext &ctx, bool read_ref,
                                              bool read_type) {
    constexpr bool inner_requires_ref = requires_ref_metadata_v<T>;

    if (!read_ref) {
      return Unexpected(Error::invalid(
          "std::optional requires read_ref=true to decode null state"));
    }

    const uint32_t flag_pos = ctx.buffer().reader_index();
    auto flag_result = ctx.read_int8();
    if (!flag_result.ok()) {
      return Unexpected(std::move(flag_result).error());
    }
    int8_t flag = flag_result.value();

    if (flag == NULL_FLAG) {
      return std::optional<T>(std::nullopt);
    }

    if constexpr (inner_requires_ref) {
      // Rewind so the inner serializer can consume the reference metadata.
      ctx.buffer().ReaderIndex(flag_pos);
      auto value_result = Serializer<T>::read(ctx, true, read_type);
      if (!value_result.ok()) {
        return Unexpected(std::move(value_result).error());
      }
      return std::optional<T>(std::move(value_result).value());
    }

    if (flag != NOT_NULL_VALUE_FLAG && flag != REF_VALUE_FLAG) {
      return Unexpected(Error::invalid_ref(
          "Unexpected reference flag for std::optional: " +
          std::to_string(static_cast<int>(flag))));
    }

    auto value_result = Serializer<T>::read(ctx, false, read_type);
    if (!value_result.ok()) {
      return Unexpected(std::move(value_result).error());
    }
    return std::optional<T>(std::move(value_result).value());
  }

  static Result<std::optional<T>, Error> read_data(ReadContext &ctx) {
    auto value_result = Serializer<T>::read_data(ctx);
    if (!value_result.ok()) {
      return Unexpected(std::move(value_result).error());
    }
    return std::optional<T>(std::move(value_result).value());
  }
};

// ============================================================================
// std::shared_ptr serializer
// ============================================================================

/// Serializer for std::shared_ptr<T>
///
/// Supports reference tracking for shared and circular references.
/// When reference tracking is enabled, identical shared_ptr instances
/// will serialize only once and use reference IDs for subsequent occurrences.
template <typename T> struct Serializer<std::shared_ptr<T>> {
  static constexpr TypeId type_id = Serializer<T>::type_id;

  static Result<void, Error> write(const std::shared_ptr<T> &ptr,
                                   WriteContext &ctx, bool write_ref,
                                   bool write_type) {
    constexpr bool inner_requires_ref = requires_ref_metadata_v<T>;

    if (!write_ref) {
      return Unexpected(Error::invalid(
          "std::shared_ptr requires write_ref=true to encode null/reference state"));
    }

    if (!ptr) {
      ctx.write_int8(NULL_FLAG);
      return Result<void, Error>();
    }

    if (ctx.track_ref()) {
      if (ctx.ref_writer().try_write_shared_ref(ctx, ptr)) {
        return Result<void, Error>();
      }
    } else {
      ctx.write_int8(NOT_NULL_VALUE_FLAG);
    }

    return Serializer<T>::write(*ptr, ctx, inner_requires_ref, write_type);
  }

  static Result<void, Error> write_data(const std::shared_ptr<T> &ptr,
                                        WriteContext &ctx) {
    if (!ptr) {
      return Unexpected(Error::invalid(
          "std::shared_ptr write_data requires non-null pointer"));
    }
    return Serializer<T>::write_data(*ptr, ctx);
  }

  static Result<void, Error> write_data_generic(const std::shared_ptr<T> &ptr,
                                                WriteContext &ctx,
                                                bool has_generics) {
    if (!ptr) {
      return Unexpected(Error::invalid(
          "std::shared_ptr write_data requires non-null pointer"));
    }
    return Serializer<T>::write_data_generic(*ptr, ctx, has_generics);
  }

  static Result<std::shared_ptr<T>, Error> read(ReadContext &ctx, bool read_ref,
                                                bool read_type) {
    constexpr bool inner_requires_ref = requires_ref_metadata_v<T>;

    if (!read_ref) {
      return Unexpected(Error::invalid(
          "std::shared_ptr requires read_ref=true to decode null/reference state"));
    }

    auto flag_result = ctx.read_int8();
    if (!flag_result.ok()) {
      return Unexpected(std::move(flag_result).error());
    }
    int8_t flag = flag_result.value();

    if (flag == NULL_FLAG) {
      return std::shared_ptr<T>(nullptr);
    }

    const bool tracking_refs = ctx.track_ref();

    if (flag == REF_FLAG) {
      if (!tracking_refs) {
        return Unexpected(Error::invalid_ref(
            "Reference flag encountered when reference tracking disabled"));
      }
      auto ref_id_result = ctx.read_varuint32();
      if (!ref_id_result.ok()) {
        return Unexpected(std::move(ref_id_result).error());
      }
      auto shared_result =
          ctx.ref_reader().template get_shared_ref<T>(ref_id_result.value());
      if (!shared_result.ok()) {
        return Unexpected(std::move(shared_result).error());
      }
      return shared_result.value();
    }

    if (flag != NOT_NULL_VALUE_FLAG && flag != REF_VALUE_FLAG) {
      return Unexpected(Error::invalid_ref(
          "Unexpected reference flag value: " +
          std::to_string(static_cast<int>(flag))));
    }

    uint32_t reserved_ref_id = 0;
    if (flag == REF_VALUE_FLAG) {
      if (!tracking_refs) {
        return Unexpected(Error::invalid_ref(
            "REF_VALUE flag encountered when reference tracking disabled"));
      }
      reserved_ref_id = ctx.ref_reader().reserve_ref_id();
    }

    auto value_result = Serializer<T>::read(ctx, inner_requires_ref, read_type);
    if (!value_result.ok()) {
      return Unexpected(std::move(value_result).error());
    }

    auto result = std::make_shared<T>(std::move(value_result).value());

    if (flag == REF_VALUE_FLAG) {
      ctx.ref_reader().store_shared_ref_at(reserved_ref_id, result);
    }

    return result;
  }

  static Result<std::shared_ptr<T>, Error> read_data(ReadContext &ctx) {
    auto value_result = Serializer<T>::read_data(ctx);
    if (!value_result.ok()) {
      return Unexpected(std::move(value_result).error());
    }
    return std::make_shared<T>(std::move(value_result).value());
  }
};

// ============================================================================
// std::unique_ptr serializer
// ============================================================================

/// Serializer for std::unique_ptr<T>
///
/// Note: unique_ptr does not support reference tracking since
/// it represents exclusive ownership. Each unique_ptr is serialized
/// independently.
template <typename T> struct Serializer<std::unique_ptr<T>> {
  static constexpr TypeId type_id = Serializer<T>::type_id;

  static Result<void, Error> write(const std::unique_ptr<T> &ptr,
                                   WriteContext &ctx, bool write_ref,
                                   bool write_type) {
    constexpr bool inner_requires_ref = requires_ref_metadata_v<T>;

    if (!write_ref) {
      if (!ptr) {
        return Unexpected(Error::invalid(
            "std::unique_ptr requires write_ref=true to encode null state"));
      }
      return Serializer<T>::write(*ptr, ctx, inner_requires_ref, write_type);
    }

    if (!ptr) {
      ctx.write_int8(NULL_FLAG);
      return Result<void, Error>();
    }

    ctx.write_int8(NOT_NULL_VALUE_FLAG);
    return Serializer<T>::write(*ptr, ctx, inner_requires_ref, write_type);
  }

  static Result<void, Error> write_data(const std::unique_ptr<T> &ptr,
                                        WriteContext &ctx) {
    if (!ptr) {
      return Unexpected(Error::invalid(
          "std::unique_ptr write_data requires non-null pointer"));
    }
    return Serializer<T>::write_data(*ptr, ctx);
  }

  static Result<void, Error> write_data_generic(const std::unique_ptr<T> &ptr,
                                                WriteContext &ctx,
                                                bool has_generics) {
    if (!ptr) {
      return Unexpected(Error::invalid(
          "std::unique_ptr write_data requires non-null pointer"));
    }
    return Serializer<T>::write_data_generic(*ptr, ctx, has_generics);
  }

  static Result<std::unique_ptr<T>, Error> read(ReadContext &ctx, bool read_ref,
                                                bool read_type) {
    constexpr bool inner_requires_ref = requires_ref_metadata_v<T>;

    auto flag_result = ctx.read_int8();
    if (!flag_result.ok()) {
      return Unexpected(std::move(flag_result).error());
    }
    int8_t flag = flag_result.value();

    if (flag == NULL_FLAG) {
      return std::unique_ptr<T>(nullptr);
    }

    auto value_result =
        Serializer<T>::read(ctx, inner_requires_ref && read_ref, read_type);
    if (!value_result.ok()) {
      return Unexpected(std::move(value_result).error());
    }

    return std::make_unique<T>(std::move(value_result).value());
  }

  static Result<std::unique_ptr<T>, Error> read_data(ReadContext &ctx) {
    auto value_result = Serializer<T>::read_data(ctx);
    if (!value_result.ok()) {
      return Unexpected(std::move(value_result).error());
    }
    return std::make_unique<T>(std::move(value_result).value());
  }
};

} // namespace serialization
} // namespace fory
