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
#include <utility>

namespace fory {
namespace serialization {

// ============================================================================
// Helper for polymorphic deserialization
// ============================================================================

/// Reads polymorphic data using the appropriate harness function based on mode.
/// In compatible mode, uses read_compatible_fn if available to handle schema
/// evolution. Otherwise, uses read_data_fn for direct deserialization.
inline void *read_polymorphic_harness_data(ReadContext &ctx,
                                           const TypeInfo *type_info) {
  if (ctx.has_error()) {
    return nullptr;
  }
  if (ctx.is_compatible()) {
    if (!type_info->harness.read_compatible_fn) {
      ctx.set_error(Error::type_error(
          "No harness read_compatible function for polymorphic type "
          "deserialization in compatible mode"));
      return nullptr;
    }
    auto res = type_info->harness.read_compatible_fn(ctx, type_info);
    if (FORY_PREDICT_FALSE(!res.ok())) {
      ctx.set_error(std::move(res).error());
      return nullptr;
    }
    return std::move(res).value();
  }
  if (!type_info->harness.read_data_fn) {
    ctx.set_error(Error::type_error(
        "No harness read function for polymorphic type deserialization"));
    return nullptr;
  }
  auto res = type_info->harness.read_data_fn(ctx);
  if (FORY_PREDICT_FALSE(!res.ok())) {
    ctx.set_error(std::move(res).error());
    return nullptr;
  }
  return std::move(res).value();
}

/// Overload for TypeInfo reference.
inline void *read_polymorphic_harness_data(ReadContext &ctx,
                                           const TypeInfo &type_info) {
  return read_polymorphic_harness_data(ctx, &type_info);
}

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

  static inline void write_type_info(WriteContext &ctx) {
    Serializer<T>::write_type_info(ctx);
  }

  static inline void read_type_info(ReadContext &ctx) {
    Serializer<T>::read_type_info(ctx);
  }

  static void write(const std::optional<T> &opt, WriteContext &ctx,
                    bool write_ref, bool write_type) {
    constexpr bool inner_requires_ref = requires_ref_metadata_v<T>;

    if (ctx.has_error()) {
      return;
    }
    if (!write_ref) {
      if (!opt.has_value()) {
        ctx.set_error(Error::invalid(
          "std::optional requires write_ref=true to encode null state"));
        return;
      }
      Serializer<T>::write(*opt, ctx, false, write_type);
      return;
    }

    if (!opt.has_value()) {
      ctx.write_int8(NULL_FLAG);
      return;
    }

    if constexpr (inner_requires_ref) {
      Serializer<T>::write(*opt, ctx, true, write_type);
    } else {
      ctx.write_int8(NOT_NULL_VALUE_FLAG);
      Serializer<T>::write(*opt, ctx, false, write_type);
    }
  }

  static void write_data(const std::optional<T> &opt, WriteContext &ctx) {
    if (ctx.has_error()) {
      return;
    }
    if (!opt.has_value()) {
        ctx.set_error(
          Error::invalid("std::optional write_data requires value present"));
      return;
    }
    Serializer<T>::write_data(*opt, ctx);
  }

  static void write_data_generic(const std::optional<T> &opt,
                                 WriteContext &ctx, bool has_generics) {
    if (ctx.has_error()) {
      return;
    }
    if (!opt.has_value()) {
        ctx.set_error(
          Error::invalid("std::optional write_data requires value present"));
      return;
    }
    Serializer<T>::write_data_generic(*opt, ctx, has_generics);
  }

  static std::optional<T> read(ReadContext &ctx, bool read_ref,
                               bool read_type) {
    constexpr bool inner_requires_ref = requires_ref_metadata_v<T>;

    if (ctx.has_error()) {
      return std::optional<T>();
    }
    if (!read_ref) {
      auto value = Serializer<T>::read(ctx, false, read_type);
      if (ctx.has_error()) {
        return std::optional<T>();
      }
      return std::optional<T>(std::move(value));
    }

    const uint32_t flag_pos = ctx.buffer().reader_index();
    Error error;
    int8_t flag = ctx.read_int8(&error);
    if (FORY_PREDICT_FALSE(!error.ok())) {
      ctx.set_error(std::move(error));
      return std::optional<T>();
    }

    if (flag == NULL_FLAG) {
      return std::optional<T>(std::nullopt);
    }

    if constexpr (inner_requires_ref) {
      // Rewind so the inner serializer can consume the reference metadata.
      ctx.buffer().ReaderIndex(flag_pos);
      auto value = Serializer<T>::read(ctx, true, read_type);
      if (ctx.has_error()) {
        return std::optional<T>();
      }
      return std::optional<T>(std::move(value));
    }

    if (flag != NOT_NULL_VALUE_FLAG && flag != REF_VALUE_FLAG) {
        ctx.set_error(Error::invalid_ref(
          "Unexpected reference flag for std::optional: " +
          std::to_string(static_cast<int>(flag))));
      return std::optional<T>();
    }

    auto value = Serializer<T>::read(ctx, false, read_type);
    if (ctx.has_error()) {
      return std::optional<T>();
    }
    return std::optional<T>(std::move(value));
  }

  static std::optional<T> read_with_type_info(ReadContext &ctx, bool read_ref,
                                             const TypeInfo &type_info) {
    constexpr bool inner_requires_ref = requires_ref_metadata_v<T>;

    if (ctx.has_error()) {
      return std::optional<T>();
    }
    if (!read_ref) {
      auto value = Serializer<T>::read_with_type_info(ctx, false, type_info);
      if (ctx.has_error()) {
        return std::optional<T>();
      }
      return std::optional<T>(std::move(value));
    }

    const uint32_t flag_pos = ctx.buffer().reader_index();
    Error error;
    int8_t flag = ctx.read_int8(&error);
    if (FORY_PREDICT_FALSE(!error.ok())) {
      ctx.set_error(std::move(error));
      return std::optional<T>();
    }

    if (flag == NULL_FLAG) {
      return std::optional<T>(std::nullopt);
    }

    if constexpr (inner_requires_ref) {
      // Rewind so the inner serializer can consume the reference metadata.
      ctx.buffer().ReaderIndex(flag_pos);
      auto value = Serializer<T>::read_with_type_info(ctx, true, type_info);
      if (ctx.has_error()) {
        return std::optional<T>();
      }
      return std::optional<T>(std::move(value));
    }

    if (flag != NOT_NULL_VALUE_FLAG && flag != REF_VALUE_FLAG) {
        ctx.set_error(Error::invalid_ref(
          "Unexpected reference flag for std::optional: " +
          std::to_string(static_cast<int>(flag))));
      return std::optional<T>();
    }

    auto value = Serializer<T>::read_with_type_info(ctx, false, type_info);
    if (ctx.has_error()) {
      return std::optional<T>();
    }
    return std::optional<T>(std::move(value));
  }

  static std::optional<T> read_data(ReadContext &ctx) {
    if (ctx.has_error()) {
      return std::optional<T>();
    }
    auto value = Serializer<T>::read_data(ctx);
    if (ctx.has_error()) {
      return std::optional<T>();
    }
    return std::optional<T>(std::move(value));
  }
};

// ============================================================================
// std::shared_ptr serializer
// ============================================================================

// Helper to get type_id for shared_ptr without instantiating Serializer for
// polymorphic types
template <typename T, bool IsPolymorphic> struct SharedPtrTypeIdHelper {
  static constexpr TypeId value = Serializer<T>::type_id;
};

template <typename T> struct SharedPtrTypeIdHelper<T, true> {
  static constexpr TypeId value = TypeId::UNKNOWN;
};

/// Serializer for std::shared_ptr<T>
///
/// Supports reference tracking for shared and circular references.
/// When reference tracking is enabled, identical shared_ptr instances
/// will serialize only once and use reference IDs for subsequent occurrences.
template <typename T> struct Serializer<std::shared_ptr<T>> {
  static constexpr TypeId type_id =
      SharedPtrTypeIdHelper<T, std::is_polymorphic_v<T>>::value;

  static inline void write_type_info(WriteContext &ctx) {
    if (ctx.has_error()) {
      return;
    }
    if constexpr (std::is_polymorphic_v<T>) {
      // For polymorphic types, type info must be written dynamically
      ctx.write_varuint32(static_cast<uint32_t>(TypeId::UNKNOWN));
    } else {
      Serializer<T>::write_type_info(ctx);
    }
  }

  static inline void read_type_info(ReadContext &ctx) {
    if (ctx.has_error()) {
      return;
    }
    if constexpr (std::is_polymorphic_v<T>) {
      // For polymorphic types, type info is read dynamically
      auto type_info = ctx.read_any_typeinfo();
      if (FORY_PREDICT_FALSE(!type_info.ok())) {
        ctx.set_error(std::move(type_info).error());
        return;
      }
      (void)type_info;
    } else {
      Serializer<T>::read_type_info(ctx);
    }
  }

  static void write(const std::shared_ptr<T> &ptr, WriteContext &ctx,
                    bool write_ref, bool write_type,
                    bool has_generics = false) {
    constexpr bool inner_requires_ref = requires_ref_metadata_v<T>;
    constexpr bool is_polymorphic = std::is_polymorphic_v<T>;

    if (ctx.has_error()) {
      return;
    }
    // Handle write_ref=false case (similar to Rust)
    if (!write_ref) {
      if (!ptr) {
        ctx.set_error(Error::invalid(
            "std::shared_ptr requires write_ref=true to encode null state"));
        return;
      }
      // For polymorphic types, serialize the concrete type dynamically
      if constexpr (is_polymorphic) {
        std::type_index concrete_type_id = std::type_index(typeid(*ptr));
        auto type_info_res =
            ctx.type_resolver().get_type_info(concrete_type_id);
        if (FORY_PREDICT_FALSE(!type_info_res.ok())) {
          ctx.set_error(std::move(type_info_res).error());
          return;
        }
        const TypeInfo *type_info = type_info_res.value();
        if (write_type) {
          auto res = ctx.write_any_typeinfo(
              static_cast<uint32_t>(TypeId::UNKNOWN), concrete_type_id);
          if (FORY_PREDICT_FALSE(!res.ok())) {
            ctx.set_error(std::move(res).error());
            return;
          }
        }
        const void *value_ptr = ptr.get();
        auto res = type_info->harness.write_data_fn(value_ptr, ctx,
                      has_generics);
        if (FORY_PREDICT_FALSE(!res.ok())) {
          ctx.set_error(std::move(res).error());
        }
      } else {
        Serializer<T>::write(*ptr, ctx, inner_requires_ref, write_type);
      }
      return;
    }

    // Handle write_ref=true case
    if (!ptr) {
      ctx.write_int8(NULL_FLAG);
      return;
    }

    if (ctx.track_ref()) {
      if (ctx.ref_writer().try_write_shared_ref(ctx, ptr)) {
        return;
      }
    } else {
      ctx.write_int8(NOT_NULL_VALUE_FLAG);
    }

    // For polymorphic types, serialize the concrete type dynamically
    if constexpr (is_polymorphic) {
      // Get the concrete type_index from the actual object
      std::type_index concrete_type_id = std::type_index(typeid(*ptr));

      // Look up the TypeInfo for the concrete type
      auto type_info_res = ctx.type_resolver().get_type_info(concrete_type_id);
      if (FORY_PREDICT_FALSE(!type_info_res.ok())) {
        ctx.set_error(std::move(type_info_res).error());
        return;
      }
      const TypeInfo *type_info = type_info_res.value();

      // Write type info if requested
      if (write_type) {
        auto res = ctx.write_any_typeinfo(
            static_cast<uint32_t>(TypeId::UNKNOWN), concrete_type_id);
        if (FORY_PREDICT_FALSE(!res.ok())) {
          ctx.set_error(std::move(res).error());
          return;
        }
      }

      // Call the harness with the raw pointer (which points to DerivedType)
      // The harness will static_cast it back to the concrete type
      const void *value_ptr = ptr.get();
      auto res = type_info->harness.write_data_fn(value_ptr, ctx,
                      has_generics);
      if (FORY_PREDICT_FALSE(!res.ok())) {
        ctx.set_error(std::move(res).error());
      }
    } else {
      // Non-polymorphic path
      Serializer<T>::write(*ptr, ctx, inner_requires_ref, write_type);
    }
  }

  static void write_data(const std::shared_ptr<T> &ptr, WriteContext &ctx) {
    if (ctx.has_error()) {
      return;
    }
    if (!ptr) {
      ctx.set_error(
          Error::invalid("std::shared_ptr write_data requires non-null pointer"));
      return;
    }

    // For polymorphic types, use harness to serialize the concrete type
    if constexpr (std::is_polymorphic_v<T>) {
      std::type_index concrete_type_id = std::type_index(typeid(*ptr));
      auto type_info_res = ctx.type_resolver().get_type_info(concrete_type_id);
      if (FORY_PREDICT_FALSE(!type_info_res.ok())) {
        ctx.set_error(std::move(type_info_res).error());
        return;
      }
      const TypeInfo *type_info = type_info_res.value();
      const void *value_ptr = ptr.get();
      auto res = type_info->harness.write_data_fn(value_ptr, ctx, false);
      if (FORY_PREDICT_FALSE(!res.ok())) {
        ctx.set_error(std::move(res).error());
      }
    } else {
      Serializer<T>::write_data(*ptr, ctx);
    }
  }

  static void write_data_generic(const std::shared_ptr<T> &ptr,
                                 WriteContext &ctx, bool has_generics) {
    if (ctx.has_error()) {
      return;
    }
    if (!ptr) {
      ctx.set_error(
          Error::invalid("std::shared_ptr write_data requires non-null pointer"));
      return;
    }

    // For polymorphic types, use harness to serialize the concrete type
    if constexpr (std::is_polymorphic_v<T>) {
      std::type_index concrete_type_id = std::type_index(typeid(*ptr));
      auto type_info_res = ctx.type_resolver().get_type_info(concrete_type_id);
      if (FORY_PREDICT_FALSE(!type_info_res.ok())) {
        ctx.set_error(std::move(type_info_res).error());
        return;
      }
      const TypeInfo *type_info = type_info_res.value();
      const void *value_ptr = ptr.get();
      auto res = type_info->harness.write_data_fn(value_ptr, ctx,
                                                  has_generics);
      if (FORY_PREDICT_FALSE(!res.ok())) {
        ctx.set_error(std::move(res).error());
      }
    } else {
      Serializer<T>::write_data_generic(*ptr, ctx, has_generics);
    }
  }

  static std::shared_ptr<T> read(ReadContext &ctx, bool read_ref,
                                 bool read_type) {
    constexpr bool inner_requires_ref = requires_ref_metadata_v<T>;
    constexpr bool is_polymorphic = std::is_polymorphic_v<T>;

    if (ctx.has_error()) {
      return std::shared_ptr<T>();
    }

    // Handle read_ref=false case (similar to Rust)
    if (!read_ref) {
      if constexpr (is_polymorphic) {
        // For polymorphic types, we must read type info when read_type=true
        if (!read_type) {
          ctx.set_error(Error::type_error(
              "Cannot deserialize polymorphic std::shared_ptr<T> "
              "without type info (read_type=false)"));
          return std::shared_ptr<T>();
        }
        auto type_info_res = ctx.read_any_typeinfo();
        if (FORY_PREDICT_FALSE(!type_info_res.ok())) {
          ctx.set_error(std::move(type_info_res).error());
          return std::shared_ptr<T>();
        }
        return read_with_type_info(ctx, read_ref, *type_info_res.value());
      } else {
        auto value = Serializer<T>::read(ctx, inner_requires_ref, read_type);
        if (ctx.has_error()) {
          return std::shared_ptr<T>();
        }
        return std::make_shared<T>(std::move(value));
      }
    }

    // Handle read_ref=true case
    Error error;
    int8_t flag = ctx.read_int8(&error);
    if (FORY_PREDICT_FALSE(!error.ok())) {
      ctx.set_error(std::move(error));
      return std::shared_ptr<T>();
    }
    if (flag == NULL_FLAG) {
      return std::shared_ptr<T>(nullptr);
    }
    const bool tracking_refs = ctx.track_ref();
    if (flag == REF_FLAG) {
      if (!tracking_refs) {
        ctx.set_error(Error::invalid_ref(
            "Reference flag encountered when reference tracking disabled"));
        return std::shared_ptr<T>();
      }
      uint32_t ref_id = ctx.read_varuint32(&error);
      if (FORY_PREDICT_FALSE(!error.ok())) {
        ctx.set_error(std::move(error));
        return std::shared_ptr<T>();
      }
      auto ref_res = ctx.ref_reader().template get_shared_ref<T>(ref_id);
      if (FORY_PREDICT_FALSE(!ref_res.ok())) {
        ctx.set_error(std::move(ref_res).error());
        return std::shared_ptr<T>();
      }
      return std::move(ref_res).value();
    }

    if (flag != NOT_NULL_VALUE_FLAG && flag != REF_VALUE_FLAG) {
      ctx.set_error(Error::invalid_ref(
          "Unexpected reference flag value: " +
          std::to_string(static_cast<int>(flag))));
      return std::shared_ptr<T>();
    }

    uint32_t reserved_ref_id = 0;
    if (flag == REF_VALUE_FLAG) {
      if (!tracking_refs) {
        ctx.set_error(Error::invalid_ref(
            "REF_VALUE flag encountered when reference tracking disabled"));
        return std::shared_ptr<T>();
      }
      reserved_ref_id = ctx.ref_reader().reserve_ref_id();
    }

    // For polymorphic types, read type info AFTER handling ref flags
    if constexpr (is_polymorphic) {
      if (!read_type) {
        ctx.set_error(Error::type_error(
            "Cannot deserialize polymorphic std::shared_ptr<T> "
            "without type info (read_type=false)"));
        return std::shared_ptr<T>();
      }

      // Check and increase dynamic depth for polymorphic deserialization
      auto depth_res = ctx.increase_dyn_depth();
      if (FORY_PREDICT_FALSE(!depth_res.ok())) {
        ctx.set_error(std::move(depth_res).error());
        return std::shared_ptr<T>();
      }
      DynDepthGuard dyn_depth_guard(ctx);

      // Read type info from stream to get the concrete type
      auto type_info_res = ctx.read_any_typeinfo();
      if (FORY_PREDICT_FALSE(!type_info_res.ok())) {
        ctx.set_error(std::move(type_info_res).error());
        return std::shared_ptr<T>();
      }

      // Use the harness to deserialize the concrete type
      void *raw_ptr = read_polymorphic_harness_data(ctx, type_info_res.value());
      if (ctx.has_error()) {
        return std::shared_ptr<T>();
      }
      T *obj_ptr = static_cast<T *>(raw_ptr);
      auto result = std::shared_ptr<T>(obj_ptr);
      if (flag == REF_VALUE_FLAG) {
        ctx.ref_reader().store_shared_ref_at(reserved_ref_id, result);
      }
      return result;
    } else {
      // Non-polymorphic path
      auto value = Serializer<T>::read(ctx, inner_requires_ref, read_type);
      if (ctx.has_error()) {
        return std::shared_ptr<T>();
      }
      auto result = std::make_shared<T>(std::move(value));
      if (flag == REF_VALUE_FLAG) {
        ctx.ref_reader().store_shared_ref_at(reserved_ref_id, result);
      }
      return result;
    }
  }

  static std::shared_ptr<T>
  read_with_type_info(ReadContext &ctx, bool read_ref,
                      const TypeInfo &type_info) {
    constexpr bool inner_requires_ref = requires_ref_metadata_v<T>;
    constexpr bool is_polymorphic = std::is_polymorphic_v<T>;

    if (ctx.has_error()) {
      return std::shared_ptr<T>();
    }

    // Handle read_ref=false case (similar to Rust)
    if (!read_ref) {
      // For polymorphic types, use the harness to deserialize the concrete type
      if constexpr (is_polymorphic) {
        void *raw_ptr = read_polymorphic_harness_data(ctx, type_info);
        if (ctx.has_error()) {
          return std::shared_ptr<T>();
        }
        T *obj_ptr = static_cast<T *>(raw_ptr);
        return std::shared_ptr<T>(obj_ptr);
      } else {
        // Non-polymorphic path
        auto value = Serializer<T>::read_with_type_info(
            ctx, inner_requires_ref, type_info);
        if (ctx.has_error()) {
          return std::shared_ptr<T>();
        }
        return std::make_shared<T>(std::move(value));
      }
    }

    // Handle read_ref=true case
    Error error;
    int8_t flag = ctx.read_int8(&error);
    if (FORY_PREDICT_FALSE(!error.ok())) {
      ctx.set_error(std::move(error));
      return std::shared_ptr<T>();
    }

    if (flag == NULL_FLAG) {
      return std::shared_ptr<T>(nullptr);
    }
    const bool tracking_refs = ctx.track_ref();
    if (flag == REF_FLAG) {
      if (!tracking_refs) {
        ctx.set_error(Error::invalid_ref(
            "Reference flag encountered when reference tracking disabled"));
        return std::shared_ptr<T>();
      }
      uint32_t ref_id = ctx.read_varuint32(&error);
      if (FORY_PREDICT_FALSE(!error.ok())) {
        ctx.set_error(std::move(error));
        return std::shared_ptr<T>();
      }
      auto ref_res = ctx.ref_reader().template get_shared_ref<T>(ref_id);
      if (FORY_PREDICT_FALSE(!ref_res.ok())) {
        ctx.set_error(std::move(ref_res).error());
        return std::shared_ptr<T>();
      }
      return std::move(ref_res).value();
    }

    if (flag != NOT_NULL_VALUE_FLAG && flag != REF_VALUE_FLAG) {
      ctx.set_error(Error::invalid_ref(
          "Unexpected reference flag value: " +
          std::to_string(static_cast<int>(flag))));
      return std::shared_ptr<T>();
    }

    uint32_t reserved_ref_id = 0;
    if (flag == REF_VALUE_FLAG) {
      if (!tracking_refs) {
        ctx.set_error(Error::invalid_ref(
            "REF_VALUE flag encountered when reference tracking disabled"));
        return std::shared_ptr<T>();
      }
      reserved_ref_id = ctx.ref_reader().reserve_ref_id();
    }

    // For polymorphic types, use the harness to deserialize the concrete type
    if constexpr (is_polymorphic) {
      // Check and increase dynamic depth for polymorphic deserialization
      auto depth_res = ctx.increase_dyn_depth();
      if (FORY_PREDICT_FALSE(!depth_res.ok())) {
        ctx.set_error(std::move(depth_res).error());
        return std::shared_ptr<T>();
      }
      DynDepthGuard dyn_depth_guard(ctx);

      // Use the harness to deserialize the concrete type
      void *raw_ptr = read_polymorphic_harness_data(ctx, type_info);
      if (ctx.has_error()) {
        return std::shared_ptr<T>();
      }
      T *obj_ptr = static_cast<T *>(raw_ptr);
      auto result = std::shared_ptr<T>(obj_ptr);
      if (flag == REF_VALUE_FLAG) {
        ctx.ref_reader().store_shared_ref_at(reserved_ref_id, result);
      }
      return result;
    } else {
      // Non-polymorphic path
      auto value = Serializer<T>::read_with_type_info(ctx, inner_requires_ref,
                                                      type_info);
      if (ctx.has_error()) {
        return std::shared_ptr<T>();
      }
      auto result = std::make_shared<T>(std::move(value));
      if (flag == REF_VALUE_FLAG) {
        ctx.ref_reader().store_shared_ref_at(reserved_ref_id, result);
      }

      return result;
    }
  }

  static std::shared_ptr<T> read_data(ReadContext &ctx) {
    if (ctx.has_error()) {
      return std::shared_ptr<T>();
    }
    auto value = Serializer<T>::read_data(ctx);
    if (ctx.has_error()) {
      return std::shared_ptr<T>();
    }
    return std::make_shared<T>(std::move(value));
  }
};

// ============================================================================
// std::unique_ptr serializer
// ============================================================================

// Helper to get type_id for unique_ptr without instantiating Serializer for
// polymorphic types
template <typename T, bool IsPolymorphic> struct UniquePtrTypeIdHelper {
  static constexpr TypeId value = Serializer<T>::type_id;
};

template <typename T> struct UniquePtrTypeIdHelper<T, true> {
  static constexpr TypeId value = TypeId::UNKNOWN;
};

/// Serializer for std::unique_ptr<T>
///
/// Note: unique_ptr does not support reference tracking since
/// it represents exclusive ownership. Each unique_ptr is serialized
/// independently.
template <typename T> struct Serializer<std::unique_ptr<T>> {
  static constexpr TypeId type_id =
      UniquePtrTypeIdHelper<T, std::is_polymorphic_v<T>>::value;

  static void write(const std::unique_ptr<T> &ptr, WriteContext &ctx,
                    bool write_ref, bool write_type) {
    constexpr bool inner_requires_ref = requires_ref_metadata_v<T>;
    constexpr bool is_polymorphic = std::is_polymorphic_v<T>;

    if (ctx.has_error()) {
      return;
    }
    // Handle write_ref=false case (similar to Rust)
    if (!write_ref) {
      if (!ptr) {
        ctx.set_error(Error::invalid(
            "std::unique_ptr requires write_ref=true to encode null state"));
        return;
      }
      // For polymorphic types, serialize the concrete type dynamically
      if constexpr (is_polymorphic) {
        std::type_index concrete_type_id = std::type_index(typeid(*ptr));
        auto type_info_res =
            ctx.type_resolver().get_type_info(concrete_type_id);
        if (FORY_PREDICT_FALSE(!type_info_res.ok())) {
          ctx.set_error(std::move(type_info_res).error());
          return;
        }
        const TypeInfo *type_info = type_info_res.value();
        if (write_type) {
          auto res = ctx.write_any_typeinfo(
              static_cast<uint32_t>(TypeId::UNKNOWN), concrete_type_id);
          if (FORY_PREDICT_FALSE(!res.ok())) {
            ctx.set_error(std::move(res).error());
            return;
          }
        }
        const void *value_ptr = ptr.get();
        auto res = type_info->harness.write_data_fn(value_ptr, ctx, false);
        if (FORY_PREDICT_FALSE(!res.ok())) {
          ctx.set_error(std::move(res).error());
        }
      } else {
        Serializer<T>::write(*ptr, ctx, inner_requires_ref, write_type);
      }
      return;
    }

    // Handle write_ref=true case
    if (!ptr) {
      ctx.write_int8(NULL_FLAG);
      return;
    }

    ctx.write_int8(NOT_NULL_VALUE_FLAG);

    // For polymorphic types, serialize the concrete type dynamically
    if constexpr (is_polymorphic) {
      std::type_index concrete_type_id = std::type_index(typeid(*ptr));
      auto type_info_res = ctx.type_resolver().get_type_info(concrete_type_id);
      if (FORY_PREDICT_FALSE(!type_info_res.ok())) {
        ctx.set_error(std::move(type_info_res).error());
        return;
      }
      const TypeInfo *type_info = type_info_res.value();
      if (write_type) {
        auto res = ctx.write_any_typeinfo(
            static_cast<uint32_t>(TypeId::UNKNOWN), concrete_type_id);
        if (FORY_PREDICT_FALSE(!res.ok())) {
          ctx.set_error(std::move(res).error());
          return;
        }
      }
      const void *value_ptr = ptr.get();
      auto res = type_info->harness.write_data_fn(value_ptr, ctx, false);
      if (FORY_PREDICT_FALSE(!res.ok())) {
        ctx.set_error(std::move(res).error());
      }
    } else {
      Serializer<T>::write(*ptr, ctx, inner_requires_ref, write_type);
    }
  }

  static void write_data(const std::unique_ptr<T> &ptr, WriteContext &ctx) {
    if (ctx.has_error()) {
      return;
    }
    if (!ptr) {
      ctx.set_error(
          Error::invalid("std::unique_ptr write_data requires non-null pointer"));
      return;
    }
    // For polymorphic types, use harness to serialize the concrete type
    if constexpr (std::is_polymorphic_v<T>) {
      std::type_index concrete_type_id = std::type_index(typeid(*ptr));
      auto type_info_res = ctx.type_resolver().get_type_info(concrete_type_id);
      if (FORY_PREDICT_FALSE(!type_info_res.ok())) {
        ctx.set_error(std::move(type_info_res).error());
        return;
      }
      const TypeInfo *type_info = type_info_res.value();
      const void *value_ptr = ptr.get();
      auto res = type_info->harness.write_data_fn(value_ptr, ctx, false);
      if (FORY_PREDICT_FALSE(!res.ok())) {
        ctx.set_error(std::move(res).error());
      }
    } else {
      Serializer<T>::write_data(*ptr, ctx);
    }
  }

  static void write_data_generic(const std::unique_ptr<T> &ptr,
                                 WriteContext &ctx, bool has_generics) {
    if (ctx.has_error()) {
      return;
    }
    if (!ptr) {
      ctx.set_error(
          Error::invalid("std::unique_ptr write_data requires non-null pointer"));
      return;
    }
    // For polymorphic types, use harness to serialize the concrete type
    if constexpr (std::is_polymorphic_v<T>) {
      std::type_index concrete_type_id = std::type_index(typeid(*ptr));
      auto type_info_res = ctx.type_resolver().get_type_info(concrete_type_id);
      if (FORY_PREDICT_FALSE(!type_info_res.ok())) {
        ctx.set_error(std::move(type_info_res).error());
        return;
      }
      const TypeInfo *type_info = type_info_res.value();
      const void *value_ptr = ptr.get();
      auto res = type_info->harness.write_data_fn(value_ptr, ctx, has_generics);
      if (FORY_PREDICT_FALSE(!res.ok())) {
        ctx.set_error(std::move(res).error());
      }
    } else {
      Serializer<T>::write_data_generic(*ptr, ctx, has_generics);
    }
  }

  static std::unique_ptr<T> read(ReadContext &ctx, bool read_ref,
                                 bool read_type) {
    constexpr bool inner_requires_ref = requires_ref_metadata_v<T>;
    constexpr bool is_polymorphic = std::is_polymorphic_v<T>;

    if (ctx.has_error()) {
      return std::unique_ptr<T>();
    }
    // Handle read_ref=false case (similar to Rust)
    if (!read_ref) {
      if constexpr (is_polymorphic) {
        // For polymorphic types, we must read type info when read_type=true
        if (!read_type) {
          ctx.set_error(Error::type_error(
              "Cannot deserialize polymorphic std::unique_ptr<T> "
              "without type info (read_type=false)"));
          return std::unique_ptr<T>();
        }
        // Read type info from stream to get the concrete type
        auto type_info_res = ctx.read_any_typeinfo();
        if (FORY_PREDICT_FALSE(!type_info_res.ok())) {
          ctx.set_error(std::move(type_info_res).error());
          return std::unique_ptr<T>();
        }
        // Now use read_with_type_info with the concrete type info
        return read_with_type_info(ctx, read_ref, *type_info_res.value());
      } else {
        auto value = Serializer<T>::read(ctx, inner_requires_ref, read_type);
        if (ctx.has_error()) {
          return std::unique_ptr<T>();
        }
        return std::make_unique<T>(std::move(value));
      }
    }

    // Handle read_ref=true case
    Error error;
    int8_t flag = ctx.read_int8(&error);
    if (FORY_PREDICT_FALSE(!error.ok())) {
      ctx.set_error(std::move(error));
      return std::unique_ptr<T>();
    }
    if (flag == NULL_FLAG) {
      return std::unique_ptr<T>(nullptr);
    }
    if (flag != NOT_NULL_VALUE_FLAG) {
      ctx.set_error(Error::invalid_ref(
          "Unexpected reference flag for unique_ptr: " +
          std::to_string(static_cast<int>(flag))));
      return std::unique_ptr<T>();
    }

    // For polymorphic types, read type info AFTER handling ref flags
    if constexpr (is_polymorphic) {
      if (!read_type) {
        ctx.set_error(Error::type_error(
            "Cannot deserialize polymorphic std::unique_ptr<T> "
            "without type info (read_type=false)"));
        return std::unique_ptr<T>();
      }

      // Check and increase dynamic depth for polymorphic deserialization
      auto depth_res = ctx.increase_dyn_depth();
      if (FORY_PREDICT_FALSE(!depth_res.ok())) {
        ctx.set_error(std::move(depth_res).error());
        return std::unique_ptr<T>();
      }
      DynDepthGuard dyn_depth_guard(ctx);

      // Read type info from stream to get the concrete type
      auto type_info_res = ctx.read_any_typeinfo();
      if (FORY_PREDICT_FALSE(!type_info_res.ok())) {
        ctx.set_error(std::move(type_info_res).error());
        return std::unique_ptr<T>();
      }

      // Use the harness to deserialize the concrete type
      void *raw_ptr = read_polymorphic_harness_data(ctx, type_info_res.value());
      if (ctx.has_error()) {
        return std::unique_ptr<T>();
      }
      T *obj_ptr = static_cast<T *>(raw_ptr);
      return std::unique_ptr<T>(obj_ptr);
    } else {
      // Non-polymorphic path
      auto value = Serializer<T>::read(ctx, inner_requires_ref, read_type);
      if (ctx.has_error()) {
        return std::unique_ptr<T>();
      }
      return std::make_unique<T>(std::move(value));
    }
  }

  static std::unique_ptr<T>
  read_with_type_info(ReadContext &ctx, bool read_ref,
                      const TypeInfo &type_info) {
    constexpr bool inner_requires_ref = requires_ref_metadata_v<T>;
    constexpr bool is_polymorphic = std::is_polymorphic_v<T>;

    if (ctx.has_error()) {
      return std::unique_ptr<T>();
    }
    // Handle read_ref=false case (similar to Rust)
    if (!read_ref) {
      // For polymorphic types, use the harness to deserialize the concrete type
      if constexpr (is_polymorphic) {
        void *raw_ptr = read_polymorphic_harness_data(ctx, type_info);
        if (ctx.has_error()) {
          return std::unique_ptr<T>();
        }
        T *obj_ptr = static_cast<T *>(raw_ptr);
        return std::unique_ptr<T>(obj_ptr);
      } else {
        // Non-polymorphic path
        auto value = Serializer<T>::read_with_type_info(
            ctx, inner_requires_ref, type_info);
        if (ctx.has_error()) {
          return std::unique_ptr<T>();
        }
        return std::make_unique<T>(std::move(value));
      }
    }

    // Handle read_ref=true case
    Error error;
    int8_t flag = ctx.read_int8(&error);
    if (FORY_PREDICT_FALSE(!error.ok())) {
      ctx.set_error(std::move(error));
      return std::unique_ptr<T>();
    }
    if (flag == NULL_FLAG) {
      return std::unique_ptr<T>(nullptr);
    }
    if (flag != NOT_NULL_VALUE_FLAG) {
      ctx.set_error(Error::invalid_ref(
          "Unexpected reference flag for unique_ptr: " +
          std::to_string(static_cast<int>(flag))));
      return std::unique_ptr<T>();
    }

    // For polymorphic types, use the harness to deserialize the concrete type
    if constexpr (is_polymorphic) {
      // Check and increase dynamic depth for polymorphic deserialization
      auto depth_res = ctx.increase_dyn_depth();
      if (FORY_PREDICT_FALSE(!depth_res.ok())) {
        ctx.set_error(std::move(depth_res).error());
        return std::unique_ptr<T>();
      }
      DynDepthGuard dyn_depth_guard(ctx);

      // Use the harness to deserialize the concrete type
      void *raw_ptr = read_polymorphic_harness_data(ctx, type_info);
      if (ctx.has_error()) {
        return std::unique_ptr<T>();
      }
      T *obj_ptr = static_cast<T *>(raw_ptr);
      return std::unique_ptr<T>(obj_ptr);
    } else {
      // Non-polymorphic path
      auto value = Serializer<T>::read_with_type_info(ctx, inner_requires_ref,
                                                      type_info);
      if (ctx.has_error()) {
        return std::unique_ptr<T>();
      }
      return std::make_unique<T>(std::move(value));
    }
  }

  static std::unique_ptr<T> read_data(ReadContext &ctx) {
    if (ctx.has_error()) {
      return std::unique_ptr<T>();
    }
    auto value = Serializer<T>::read_data(ctx);
    if (ctx.has_error()) {
      return std::unique_ptr<T>();
    }
    return std::make_unique<T>(std::move(value));
  }
};

} // namespace serialization
} // namespace fory
