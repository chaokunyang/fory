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

  static inline void write_type_info(WriteContext &ctx, Error *error) {
    Serializer<T>::write_type_info(ctx, error);
  }

  static inline void read_type_info(ReadContext &ctx, Error *error) {
    Serializer<T>::read_type_info(ctx, error);
  }

  static void write(const std::optional<T> &opt, WriteContext &ctx,
                    bool write_ref, bool write_type, bool has_generics,
                    Error *error) {
    constexpr bool inner_requires_ref = requires_ref_metadata_v<T>;

    if (!write_ref) {
      if (!opt.has_value()) {
        error->set_error(
            ErrorCode::InvalidData,
            "std::optional requires write_ref=true to encode null state");
        return;
      }
      Serializer<T>::write(*opt, ctx, false, write_type, has_generics, error);
      return;
    }

    if (!opt.has_value()) {
      ctx.write_int8(NULL_FLAG);
      return;
    }

    if constexpr (inner_requires_ref) {
      Serializer<T>::write(*opt, ctx, true, write_type, has_generics, error);
    } else {
      ctx.write_int8(NOT_NULL_VALUE_FLAG);
      Serializer<T>::write(*opt, ctx, false, write_type, has_generics, error);
    }
  }

  static void write_data(const std::optional<T> &opt, WriteContext &ctx,
                         Error *error) {
    if (!opt.has_value()) {
      error->set_error(ErrorCode::InvalidData,
                       "std::optional write_data requires value present");
      return;
    }
    Serializer<T>::write_data(*opt, ctx, error);
  }

  static void write_data_generic(const std::optional<T> &opt, WriteContext &ctx,
                                 bool has_generics, Error *error) {
    if (!opt.has_value()) {
      error->set_error(ErrorCode::InvalidData,
                       "std::optional write_data requires value present");
      return;
    }
    Serializer<T>::write_data_generic(*opt, ctx, has_generics, error);
  }

  static std::optional<T> read(ReadContext &ctx, bool read_ref, bool read_type,
                               Error *error) {
    constexpr bool inner_requires_ref = requires_ref_metadata_v<T>;

    if (!read_ref) {
      T value = Serializer<T>::read(ctx, false, read_type, error);
      if (FORY_PREDICT_FALSE(!error->ok())) {
        return std::nullopt;
      }
      return std::optional<T>(std::move(value));
    }

    const uint32_t flag_pos = ctx.buffer().reader_index();
    int8_t flag = ctx.read_int8(error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return std::nullopt;
    }

    if (flag == NULL_FLAG) {
      return std::optional<T>(std::nullopt);
    }

    if constexpr (inner_requires_ref) {
      // Rewind so the inner serializer can consume the reference metadata.
      ctx.buffer().ReaderIndex(flag_pos);
      T value = Serializer<T>::read(ctx, true, read_type, error);
      if (FORY_PREDICT_FALSE(!error->ok())) {
        return std::nullopt;
      }
      return std::optional<T>(std::move(value));
    }

    if (flag != NOT_NULL_VALUE_FLAG && flag != REF_VALUE_FLAG) {
      error->set_error(
          ErrorCode::InvalidRef,
          "Unexpected reference flag for std::optional: " +
              std::to_string(static_cast<int>(flag)));
      return std::nullopt;
    }

    T value = Serializer<T>::read(ctx, false, read_type, error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return std::nullopt;
    }
    return std::optional<T>(std::move(value));
  }

  static std::optional<T> read_with_type_info(ReadContext &ctx, bool read_ref,
                                              const TypeInfo &type_info,
                                              Error *error) {
    constexpr bool inner_requires_ref = requires_ref_metadata_v<T>;

    if (!read_ref) {
      T value =
          Serializer<T>::read_with_type_info(ctx, false, type_info, error);
      if (FORY_PREDICT_FALSE(!error->ok())) {
        return std::nullopt;
      }
      return std::optional<T>(std::move(value));
    }

    const uint32_t flag_pos = ctx.buffer().reader_index();
    int8_t flag = ctx.read_int8(error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return std::nullopt;
    }

    if (flag == NULL_FLAG) {
      return std::optional<T>(std::nullopt);
    }

    if constexpr (inner_requires_ref) {
      // Rewind so the inner serializer can consume the reference metadata.
      ctx.buffer().ReaderIndex(flag_pos);
      T value =
          Serializer<T>::read_with_type_info(ctx, true, type_info, error);
      if (FORY_PREDICT_FALSE(!error->ok())) {
        return std::nullopt;
      }
      return std::optional<T>(std::move(value));
    }

    if (flag != NOT_NULL_VALUE_FLAG && flag != REF_VALUE_FLAG) {
      error->set_error(
          ErrorCode::InvalidRef,
          "Unexpected reference flag for std::optional: " +
              std::to_string(static_cast<int>(flag)));
      return std::nullopt;
    }

    T value = Serializer<T>::read_with_type_info(ctx, false, type_info, error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return std::nullopt;
    }
    return std::optional<T>(std::move(value));
  }

  static std::optional<T> read_data(ReadContext &ctx, Error *error) {
    T value = Serializer<T>::read_data(ctx, error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return std::nullopt;
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

  static inline void write_type_info(WriteContext &ctx, Error *error) {
    if constexpr (std::is_polymorphic_v<T>) {
      // For polymorphic types, type info must be written dynamically
      (void)error;
      ctx.write_varuint32(static_cast<uint32_t>(TypeId::UNKNOWN));
    } else {
      Serializer<T>::write_type_info(ctx, error);
    }
  }

  static inline void read_type_info(ReadContext &ctx, Error *error) {
    if constexpr (std::is_polymorphic_v<T>) {
      // For polymorphic types, type info is read dynamically
      auto type_info_result = ctx.read_any_typeinfo();
      if (!type_info_result) {
        *error = std::move(type_info_result.error());
        return;
      }
      // Type checking is done at value read time
    } else {
      Serializer<T>::read_type_info(ctx, error);
    }
  }

  static void write(const std::shared_ptr<T> &ptr, WriteContext &ctx,
                    bool write_ref, bool write_type, bool has_generics,
                    Error *error) {
    constexpr bool inner_requires_ref = requires_ref_metadata_v<T>;
    constexpr bool is_polymorphic = std::is_polymorphic_v<T>;

    // Handle write_ref=false case (similar to Rust)
    if (!write_ref) {
      if (!ptr) {
        error->set_error(
            ErrorCode::Invalid,
            "std::shared_ptr requires write_ref=true to encode null state");
        return;
      }
      // For polymorphic types, serialize the concrete type dynamically
      if constexpr (is_polymorphic) {
        std::type_index concrete_type_id = std::type_index(typeid(*ptr));
        auto type_info_result =
            ctx.type_resolver().get_type_info(concrete_type_id);
        if (!type_info_result) {
          *error = std::move(type_info_result.error());
          return;
        }
        const TypeInfo *type_info = *type_info_result;
        if (write_type) {
          auto write_result = ctx.write_any_typeinfo(
              static_cast<uint32_t>(TypeId::UNKNOWN), concrete_type_id);
          if (!write_result) {
            *error = std::move(write_result.error());
            return;
          }
        }
        const void *value_ptr = ptr.get();
        auto result =
            type_info->harness.write_data_fn(value_ptr, ctx, has_generics);
        if (!result) {
          *error = std::move(result.error());
        }
        return;
      } else {
        Serializer<T>::write(*ptr, ctx, inner_requires_ref, write_type,
                             has_generics, error);
        return;
      }
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
      auto type_info_result =
          ctx.type_resolver().get_type_info(concrete_type_id);
      if (!type_info_result) {
        *error = std::move(type_info_result.error());
        return;
      }
      const TypeInfo *type_info = *type_info_result;

      // Write type info if requested
      if (write_type) {
        auto write_result = ctx.write_any_typeinfo(
            static_cast<uint32_t>(TypeId::UNKNOWN), concrete_type_id);
        if (!write_result) {
          *error = std::move(write_result.error());
          return;
        }
      }

      // Call the harness with the raw pointer (which points to DerivedType)
      // The harness will static_cast it back to the concrete type
      const void *value_ptr = ptr.get();
      auto result =
          type_info->harness.write_data_fn(value_ptr, ctx, has_generics);
      if (!result) {
        *error = std::move(result.error());
      }
    } else {
      // Non-polymorphic path
      Serializer<T>::write(*ptr, ctx, inner_requires_ref, write_type,
                           has_generics, error);
    }
  }

  static void write_data(const std::shared_ptr<T> &ptr, WriteContext &ctx,
                         Error *error) {
    if (!ptr) {
      error->set_error(ErrorCode::Invalid,
                       "std::shared_ptr write_data requires non-null pointer");
      return;
    }

    // For polymorphic types, use harness to serialize the concrete type
    if constexpr (std::is_polymorphic_v<T>) {
      std::type_index concrete_type_id = std::type_index(typeid(*ptr));
      auto type_info_result =
          ctx.type_resolver().get_type_info(concrete_type_id);
      if (!type_info_result) {
        *error = std::move(type_info_result.error());
        return;
      }
      const TypeInfo *type_info = *type_info_result;
      const void *value_ptr = ptr.get();
      auto result = type_info->harness.write_data_fn(value_ptr, ctx, false);
      if (!result) {
        *error = std::move(result.error());
      }
    } else {
      Serializer<T>::write_data(*ptr, ctx, error);
    }
  }

  static void write_data_generic(const std::shared_ptr<T> &ptr,
                                 WriteContext &ctx, bool has_generics,
                                 Error *error) {
    if (!ptr) {
      error->set_error(ErrorCode::Invalid,
                       "std::shared_ptr write_data requires non-null pointer");
      return;
    }

    // For polymorphic types, use harness to serialize the concrete type
    if constexpr (std::is_polymorphic_v<T>) {
      std::type_index concrete_type_id = std::type_index(typeid(*ptr));
      auto type_info_result =
          ctx.type_resolver().get_type_info(concrete_type_id);
      if (!type_info_result) {
        *error = std::move(type_info_result.error());
        return;
      }
      const TypeInfo *type_info = *type_info_result;
      const void *value_ptr = ptr.get();
      auto result =
          type_info->harness.write_data_fn(value_ptr, ctx, has_generics);
      if (!result) {
        *error = std::move(result.error());
      }
    } else {
      Serializer<T>::write_data_generic(*ptr, ctx, has_generics, error);
    }
  }

  static std::shared_ptr<T> read(ReadContext &ctx, bool read_ref,
                                 bool read_type, Error *error) {
    constexpr bool inner_requires_ref = requires_ref_metadata_v<T>;
    constexpr bool is_polymorphic = std::is_polymorphic_v<T>;

    // Handle read_ref=false case (similar to Rust)
    if (!read_ref) {
      if constexpr (is_polymorphic) {
        // For polymorphic types, we must read type info when read_type=true
        if (!read_type) {
          error->set_error(ErrorCode::TypeError,
                           "Cannot deserialize polymorphic std::shared_ptr<T> "
                           "without type info (read_type=false)");
          return std::shared_ptr<T>(nullptr);
        }
        // Read type info from stream to get the concrete type
        auto type_info_result = ctx.read_any_typeinfo();
        if (!type_info_result) {
          *error = std::move(type_info_result.error());
          return std::shared_ptr<T>(nullptr);
        }
        // Now use read_with_type_info with the concrete type info
        return read_with_type_info(ctx, read_ref, **type_info_result, error);
      } else {
        T value = Serializer<T>::read(ctx, inner_requires_ref, read_type, error);
        if (FORY_PREDICT_FALSE(!error->ok())) {
          return std::shared_ptr<T>(nullptr);
        }
        return std::make_shared<T>(std::move(value));
      }
    }

    // Handle read_ref=true case
    int8_t flag = ctx.read_int8(error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return std::shared_ptr<T>(nullptr);
    }
    if (flag == NULL_FLAG) {
      return std::shared_ptr<T>(nullptr);
    }
    const bool tracking_refs = ctx.track_ref();
    if (flag == REF_FLAG) {
      if (!tracking_refs) {
        error->set_error(
            ErrorCode::InvalidRef,
            "Reference flag encountered when reference tracking disabled");
        return std::shared_ptr<T>(nullptr);
      }
      uint32_t ref_id = ctx.read_varuint32(error);
      if (FORY_PREDICT_FALSE(!error->ok())) {
        return std::shared_ptr<T>(nullptr);
      }
      auto ref_result = ctx.ref_reader().template get_shared_ref<T>(ref_id);
      if (!ref_result) {
        *error = std::move(ref_result.error());
        return std::shared_ptr<T>(nullptr);
      }
      return *ref_result;
    }

    if (flag != NOT_NULL_VALUE_FLAG && flag != REF_VALUE_FLAG) {
      error->set_error(ErrorCode::InvalidRef,
                       "Unexpected reference flag value: " +
                           std::to_string(static_cast<int>(flag)));
      return std::shared_ptr<T>(nullptr);
    }

    uint32_t reserved_ref_id = 0;
    if (flag == REF_VALUE_FLAG) {
      if (!tracking_refs) {
        error->set_error(
            ErrorCode::InvalidRef,
            "REF_VALUE flag encountered when reference tracking disabled");
        return std::shared_ptr<T>(nullptr);
      }
      reserved_ref_id = ctx.ref_reader().reserve_ref_id();
    }

    // For polymorphic types, read type info AFTER handling ref flags
    if constexpr (is_polymorphic) {
      if (!read_type) {
        error->set_error(ErrorCode::TypeError,
                         "Cannot deserialize polymorphic std::shared_ptr<T> "
                         "without type info (read_type=false)");
        return std::shared_ptr<T>(nullptr);
      }

      // Check and increase dynamic depth for polymorphic deserialization
      auto depth_result = ctx.increase_dyn_depth();
      if (!depth_result) {
        *error = std::move(depth_result.error());
        return std::shared_ptr<T>(nullptr);
      }
      DynDepthGuard dyn_depth_guard(ctx);

      // Read type info from stream to get the concrete type
      auto type_info_result = ctx.read_any_typeinfo();
      if (!type_info_result) {
        *error = std::move(type_info_result.error());
        return std::shared_ptr<T>(nullptr);
      }
      const TypeInfo *type_info = *type_info_result;

      // Use the harness to deserialize the concrete type
      if (!type_info->harness.read_data_fn) {
        error->set_error(
            ErrorCode::TypeError,
            "No harness read function for polymorphic type deserialization");
        return std::shared_ptr<T>(nullptr);
      }
      auto raw_ptr_result = type_info->harness.read_data_fn(ctx);
      if (!raw_ptr_result) {
        *error = std::move(raw_ptr_result.error());
        return std::shared_ptr<T>(nullptr);
      }
      T *obj_ptr = static_cast<T *>(*raw_ptr_result);
      auto result = std::shared_ptr<T>(obj_ptr);
      if (flag == REF_VALUE_FLAG) {
        ctx.ref_reader().store_shared_ref_at(reserved_ref_id, result);
      }
      return result;
    } else {
      // Non-polymorphic path
      T value = Serializer<T>::read(ctx, inner_requires_ref, read_type, error);
      if (FORY_PREDICT_FALSE(!error->ok())) {
        return std::shared_ptr<T>(nullptr);
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
                      const TypeInfo &type_info, Error *error) {
    constexpr bool inner_requires_ref = requires_ref_metadata_v<T>;
    constexpr bool is_polymorphic = std::is_polymorphic_v<T>;

    // Handle read_ref=false case (similar to Rust)
    if (!read_ref) {
      // For polymorphic types, use the harness to deserialize the concrete type
      if constexpr (is_polymorphic) {
        if (!type_info.harness.read_data_fn) {
          error->set_error(
              ErrorCode::TypeError,
              "No harness read function for polymorphic type deserialization");
          return std::shared_ptr<T>(nullptr);
        }
        auto raw_ptr_result = type_info.harness.read_data_fn(ctx);
        if (!raw_ptr_result) {
          *error = std::move(raw_ptr_result.error());
          return std::shared_ptr<T>(nullptr);
        }
        T *obj_ptr = static_cast<T *>(*raw_ptr_result);
        return std::shared_ptr<T>(obj_ptr);
      } else {
        // Non-polymorphic path
        T value = Serializer<T>::read_with_type_info(ctx, inner_requires_ref,
                                                     type_info, error);
        if (FORY_PREDICT_FALSE(!error->ok())) {
          return std::shared_ptr<T>(nullptr);
        }
        return std::make_shared<T>(std::move(value));
      }
    }

    // Handle read_ref=true case
    int8_t flag = ctx.read_int8(error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return std::shared_ptr<T>(nullptr);
    }
    if (flag == NULL_FLAG) {
      return std::shared_ptr<T>(nullptr);
    }
    const bool tracking_refs = ctx.track_ref();
    if (flag == REF_FLAG) {
      if (!tracking_refs) {
        error->set_error(
            ErrorCode::InvalidRef,
            "Reference flag encountered when reference tracking disabled");
        return std::shared_ptr<T>(nullptr);
      }
      uint32_t ref_id = ctx.read_varuint32(error);
      if (FORY_PREDICT_FALSE(!error->ok())) {
        return std::shared_ptr<T>(nullptr);
      }
      auto ref_result = ctx.ref_reader().template get_shared_ref<T>(ref_id);
      if (!ref_result) {
        *error = std::move(ref_result.error());
        return std::shared_ptr<T>(nullptr);
      }
      return *ref_result;
    }

    if (flag != NOT_NULL_VALUE_FLAG && flag != REF_VALUE_FLAG) {
      error->set_error(ErrorCode::InvalidRef,
                       "Unexpected reference flag value: " +
                           std::to_string(static_cast<int>(flag)));
      return std::shared_ptr<T>(nullptr);
    }

    uint32_t reserved_ref_id = 0;
    if (flag == REF_VALUE_FLAG) {
      if (!tracking_refs) {
        error->set_error(
            ErrorCode::InvalidRef,
            "REF_VALUE flag encountered when reference tracking disabled");
        return std::shared_ptr<T>(nullptr);
      }
      reserved_ref_id = ctx.ref_reader().reserve_ref_id();
    }

    // For polymorphic types, use the harness to deserialize the concrete type
    if constexpr (is_polymorphic) {
      // Check and increase dynamic depth for polymorphic deserialization
      auto depth_result = ctx.increase_dyn_depth();
      if (!depth_result) {
        *error = std::move(depth_result.error());
        return std::shared_ptr<T>(nullptr);
      }
      DynDepthGuard dyn_depth_guard(ctx);

      // The type_info contains information about the CONCRETE type, not T
      // Use the harness to deserialize it
      if (!type_info.harness.read_data_fn) {
        error->set_error(
            ErrorCode::TypeError,
            "No harness read function for polymorphic type deserialization");
        return std::shared_ptr<T>(nullptr);
      }

      auto raw_ptr_result = type_info.harness.read_data_fn(ctx);
      if (!raw_ptr_result) {
        *error = std::move(raw_ptr_result.error());
        return std::shared_ptr<T>(nullptr);
      }

      // The harness returns void* pointing to the concrete type
      // Cast the void* to T* (this works because the concrete type derives from
      // T)
      T *obj_ptr = static_cast<T *>(*raw_ptr_result);
      auto result = std::shared_ptr<T>(obj_ptr);
      if (flag == REF_VALUE_FLAG) {
        ctx.ref_reader().store_shared_ref_at(reserved_ref_id, result);
      }
      return result;
    } else {
      // Non-polymorphic path
      T value = Serializer<T>::read_with_type_info(ctx, inner_requires_ref,
                                                   type_info, error);
      if (FORY_PREDICT_FALSE(!error->ok())) {
        return std::shared_ptr<T>(nullptr);
      }
      auto result = std::make_shared<T>(std::move(value));
      if (flag == REF_VALUE_FLAG) {
        ctx.ref_reader().store_shared_ref_at(reserved_ref_id, result);
      }

      return result;
    }
  }

  static std::shared_ptr<T> read_data(ReadContext &ctx, Error *error) {
    T value = Serializer<T>::read_data(ctx, error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return std::shared_ptr<T>(nullptr);
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
                    bool write_ref, bool write_type, bool has_generics,
                    Error *error) {
    constexpr bool inner_requires_ref = requires_ref_metadata_v<T>;
    constexpr bool is_polymorphic = std::is_polymorphic_v<T>;

    // Handle write_ref=false case (similar to Rust)
    if (!write_ref) {
      if (!ptr) {
        error->set_error(
            ErrorCode::Invalid,
            "std::unique_ptr requires write_ref=true to encode null state");
        return;
      }
      // For polymorphic types, serialize the concrete type dynamically
      if constexpr (is_polymorphic) {
        std::type_index concrete_type_id = std::type_index(typeid(*ptr));
        auto type_info_result =
            ctx.type_resolver().get_type_info(concrete_type_id);
        if (!type_info_result) {
          *error = std::move(type_info_result.error());
          return;
        }
        const TypeInfo *type_info = *type_info_result;
        if (write_type) {
          auto write_result = ctx.write_any_typeinfo(
              static_cast<uint32_t>(TypeId::UNKNOWN), concrete_type_id);
          if (!write_result) {
            *error = std::move(write_result.error());
            return;
          }
        }
        const void *value_ptr = ptr.get();
        auto result =
            type_info->harness.write_data_fn(value_ptr, ctx, has_generics);
        if (!result) {
          *error = std::move(result.error());
        }
        return;
      } else {
        Serializer<T>::write(*ptr, ctx, inner_requires_ref, write_type,
                             has_generics, error);
        return;
      }
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
      auto type_info_result =
          ctx.type_resolver().get_type_info(concrete_type_id);
      if (!type_info_result) {
        *error = std::move(type_info_result.error());
        return;
      }
      const TypeInfo *type_info = *type_info_result;
      if (write_type) {
        auto write_result = ctx.write_any_typeinfo(
            static_cast<uint32_t>(TypeId::UNKNOWN), concrete_type_id);
        if (!write_result) {
          *error = std::move(write_result.error());
          return;
        }
      }
      const void *value_ptr = ptr.get();
      auto result =
          type_info->harness.write_data_fn(value_ptr, ctx, has_generics);
      if (!result) {
        *error = std::move(result.error());
      }
    } else {
      Serializer<T>::write(*ptr, ctx, inner_requires_ref, write_type,
                           has_generics, error);
    }
  }

  static void write_data(const std::unique_ptr<T> &ptr, WriteContext &ctx,
                         Error *error) {
    if (!ptr) {
      error->set_error(ErrorCode::Invalid,
                       "std::unique_ptr write_data requires non-null pointer");
      return;
    }
    // For polymorphic types, use harness to serialize the concrete type
    if constexpr (std::is_polymorphic_v<T>) {
      std::type_index concrete_type_id = std::type_index(typeid(*ptr));
      auto type_info_result =
          ctx.type_resolver().get_type_info(concrete_type_id);
      if (!type_info_result) {
        *error = std::move(type_info_result.error());
        return;
      }
      const TypeInfo *type_info = *type_info_result;
      const void *value_ptr = ptr.get();
      auto result = type_info->harness.write_data_fn(value_ptr, ctx, false);
      if (!result) {
        *error = std::move(result.error());
      }
    } else {
      Serializer<T>::write_data(*ptr, ctx, error);
    }
  }

  static void write_data_generic(const std::unique_ptr<T> &ptr,
                                 WriteContext &ctx, bool has_generics,
                                 Error *error) {
    if (!ptr) {
      error->set_error(ErrorCode::Invalid,
                       "std::unique_ptr write_data requires non-null pointer");
      return;
    }
    // For polymorphic types, use harness to serialize the concrete type
    if constexpr (std::is_polymorphic_v<T>) {
      std::type_index concrete_type_id = std::type_index(typeid(*ptr));
      auto type_info_result =
          ctx.type_resolver().get_type_info(concrete_type_id);
      if (!type_info_result) {
        *error = std::move(type_info_result.error());
        return;
      }
      const TypeInfo *type_info = *type_info_result;
      const void *value_ptr = ptr.get();
      auto result =
          type_info->harness.write_data_fn(value_ptr, ctx, has_generics);
      if (!result) {
        *error = std::move(result.error());
      }
    } else {
      Serializer<T>::write_data_generic(*ptr, ctx, has_generics, error);
    }
  }

  static std::unique_ptr<T> read(ReadContext &ctx, bool read_ref,
                                 bool read_type, Error *error) {
    constexpr bool inner_requires_ref = requires_ref_metadata_v<T>;
    constexpr bool is_polymorphic = std::is_polymorphic_v<T>;

    // Handle read_ref=false case (similar to Rust)
    if (!read_ref) {
      if constexpr (is_polymorphic) {
        // For polymorphic types, we must read type info when read_type=true
        if (!read_type) {
          error->set_error(ErrorCode::TypeError,
                           "Cannot deserialize polymorphic std::unique_ptr<T> "
                           "without type info (read_type=false)");
          return std::unique_ptr<T>(nullptr);
        }
        // Read type info from stream to get the concrete type
        auto type_info_result = ctx.read_any_typeinfo();
        if (!type_info_result) {
          *error = std::move(type_info_result.error());
          return std::unique_ptr<T>(nullptr);
        }
        const TypeInfo *type_info = *type_info_result;
        // Now use read_with_type_info with the concrete type info
        return read_with_type_info(ctx, read_ref, *type_info, error);
      } else {
        T value = Serializer<T>::read(ctx, inner_requires_ref, read_type, error);
        if (FORY_PREDICT_FALSE(!error->ok())) {
          return std::unique_ptr<T>(nullptr);
        }
        return std::make_unique<T>(std::move(value));
      }
    }

    // Handle read_ref=true case
    int8_t flag = ctx.read_int8(error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return std::unique_ptr<T>(nullptr);
    }
    if (flag == NULL_FLAG) {
      return std::unique_ptr<T>(nullptr);
    }
    if (flag != NOT_NULL_VALUE_FLAG) {
      error->set_error(ErrorCode::InvalidRef,
                       "Unexpected reference flag for unique_ptr: " +
                           std::to_string(static_cast<int>(flag)));
      return std::unique_ptr<T>(nullptr);
    }

    // For polymorphic types, read type info AFTER handling ref flags
    if constexpr (is_polymorphic) {
      if (!read_type) {
        error->set_error(ErrorCode::TypeError,
                         "Cannot deserialize polymorphic std::unique_ptr<T> "
                         "without type info (read_type=false)");
        return std::unique_ptr<T>(nullptr);
      }

      // Check and increase dynamic depth for polymorphic deserialization
      auto depth_result = ctx.increase_dyn_depth();
      if (!depth_result) {
        *error = std::move(depth_result.error());
        return std::unique_ptr<T>(nullptr);
      }
      DynDepthGuard dyn_depth_guard(ctx);

      // Read type info from stream to get the concrete type
      auto type_info_result = ctx.read_any_typeinfo();
      if (!type_info_result) {
        *error = std::move(type_info_result.error());
        return std::unique_ptr<T>(nullptr);
      }
      const TypeInfo *type_info = *type_info_result;

      // Use the harness to deserialize the concrete type
      if (!type_info->harness.read_data_fn) {
        error->set_error(
            ErrorCode::TypeError,
            "No harness read function for polymorphic type deserialization");
        return std::unique_ptr<T>(nullptr);
      }
      auto raw_ptr_result = type_info->harness.read_data_fn(ctx);
      if (!raw_ptr_result) {
        *error = std::move(raw_ptr_result.error());
        return std::unique_ptr<T>(nullptr);
      }
      T *obj_ptr = static_cast<T *>(*raw_ptr_result);
      return std::unique_ptr<T>(obj_ptr);
    } else {
      // Non-polymorphic path
      T value = Serializer<T>::read(ctx, inner_requires_ref, read_type, error);
      if (FORY_PREDICT_FALSE(!error->ok())) {
        return std::unique_ptr<T>(nullptr);
      }
      return std::make_unique<T>(std::move(value));
    }
  }

  static std::unique_ptr<T> read_with_type_info(ReadContext &ctx, bool read_ref,
                                                const TypeInfo &type_info,
                                                Error *error) {
    constexpr bool inner_requires_ref = requires_ref_metadata_v<T>;
    constexpr bool is_polymorphic = std::is_polymorphic_v<T>;

    // Handle read_ref=false case (similar to Rust)
    if (!read_ref) {
      // For polymorphic types, use the harness to deserialize the concrete type
      if constexpr (is_polymorphic) {
        if (!type_info.harness.read_data_fn) {
          error->set_error(
              ErrorCode::TypeError,
              "No harness read function for polymorphic type deserialization");
          return std::unique_ptr<T>(nullptr);
        }
        auto raw_ptr_result = type_info.harness.read_data_fn(ctx);
        if (!raw_ptr_result) {
          *error = std::move(raw_ptr_result.error());
          return std::unique_ptr<T>(nullptr);
        }
        T *obj_ptr = static_cast<T *>(*raw_ptr_result);
        return std::unique_ptr<T>(obj_ptr);
      } else {
        // Non-polymorphic path
        T value = Serializer<T>::read_with_type_info(ctx, inner_requires_ref,
                                                     type_info, error);
        if (FORY_PREDICT_FALSE(!error->ok())) {
          return std::unique_ptr<T>(nullptr);
        }
        return std::make_unique<T>(std::move(value));
      }
    }

    // Handle read_ref=true case
    int8_t flag = ctx.read_int8(error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return std::unique_ptr<T>(nullptr);
    }
    if (flag == NULL_FLAG) {
      return std::unique_ptr<T>(nullptr);
    }
    if (flag != NOT_NULL_VALUE_FLAG) {
      error->set_error(ErrorCode::InvalidRef,
                       "Unexpected reference flag for unique_ptr: " +
                           std::to_string(static_cast<int>(flag)));
      return std::unique_ptr<T>(nullptr);
    }

    // For polymorphic types, use the harness to deserialize the concrete type
    if constexpr (is_polymorphic) {
      // Check and increase dynamic depth for polymorphic deserialization
      auto depth_result = ctx.increase_dyn_depth();
      if (!depth_result) {
        *error = std::move(depth_result.error());
        return std::unique_ptr<T>(nullptr);
      }
      DynDepthGuard dyn_depth_guard(ctx);

      if (!type_info.harness.read_data_fn) {
        error->set_error(
            ErrorCode::TypeError,
            "No harness read function for polymorphic type deserialization");
        return std::unique_ptr<T>(nullptr);
      }
      auto raw_ptr_result = type_info.harness.read_data_fn(ctx);
      if (!raw_ptr_result) {
        *error = std::move(raw_ptr_result.error());
        return std::unique_ptr<T>(nullptr);
      }
      T *obj_ptr = static_cast<T *>(*raw_ptr_result);
      return std::unique_ptr<T>(obj_ptr);
    } else {
      // Non-polymorphic path
      T value = Serializer<T>::read_with_type_info(ctx, inner_requires_ref,
                                                   type_info, error);
      if (FORY_PREDICT_FALSE(!error->ok())) {
        return std::unique_ptr<T>(nullptr);
      }
      return std::make_unique<T>(std::move(value));
    }
  }

  static std::unique_ptr<T> read_data(ReadContext &ctx, Error *error) {
    T value = Serializer<T>::read_data(ctx, error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return std::unique_ptr<T>(nullptr);
    }
    return std::make_unique<T>(std::move(value));
  }
};

} // namespace serialization
} // namespace fory
