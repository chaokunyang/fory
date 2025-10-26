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
#include "fory/meta/field_info.h"
#include "fory/meta/preprocessor.h"
#include "fory/meta/type_traits.h"
#include "fory/serialization/serializer.h"
#include "fory/serialization/skip.h"
#include "fory/serialization/serializer_traits.h"
#include "fory/serialization/type_meta.h"
#include <memory>
#include <tuple>
#include <type_traits>
#include <utility>
#include <vector>

namespace fory {
namespace serialization {

/// Serialization metadata for a type.
///
/// This template is specialized by FORY_STRUCT macro to provide
/// compile-time serialization metadata for registered types.
template <typename T> struct SerializationMeta {
  static constexpr bool is_serializable = false;
};

/// Main serialization registration macro.
///
/// This macro must be placed in the same namespace as the type for ADL
/// (Argument-Dependent Lookup).
///
/// It builds upon FORY_FIELD_INFO to add serialization-specific metadata:
/// - Marks the type as serializable
/// - Provides compile-time metadata access
///
/// Example:
/// ```cpp
/// namespace myapp {
///   struct Person {
///     std::string name;
///     int32_t age;
///   };
///   FORY_STRUCT(Person, name, age);
/// }
/// ```
///
/// After expansion, the type can be serialized using Fory:
/// ```cpp
/// fory::serialization::Fory fory;
/// myapp::Person person{"Alice", 30};
/// auto bytes = fory.serialize(person);
/// ```
#define FORY_STRUCT(Type, ...)                                                 \
  FORY_FIELD_INFO(Type, __VA_ARGS__)                                           \
  template <> struct ::fory::serialization::SerializationMeta<Type> {          \
    static constexpr bool is_serializable = true;                              \
    static constexpr size_t field_count = FORY_PP_NARG(__VA_ARGS__);           \
  };

// Forward declaration for future TypeInfo support (xlang spec section 5.4.4)
struct TypeInfo;

namespace detail {

// Forward declare to avoid circular dependency
template <typename T, std::enable_if_t<is_fory_serializable_v<T>, int> = 0>
struct StructSerializerHelper;

// Type trait to get TypeId for a field type
template <typename T>
struct TypeIdGetter {
  static constexpr uint32_t get() {
    // Default: use Serializer::type_id
    return static_cast<uint32_t>(Serializer<T>::type_id);
  }
};

// Specializations for primitive types
template <> struct TypeIdGetter<bool> {
  static constexpr uint32_t get() { return static_cast<uint32_t>(TypeId::BOOL); }
};
template <> struct TypeIdGetter<int8_t> {
  static constexpr uint32_t get() { return static_cast<uint32_t>(TypeId::INT8); }
};
template <> struct TypeIdGetter<int16_t> {
  static constexpr uint32_t get() { return static_cast<uint32_t>(TypeId::INT16); }
};
template <> struct TypeIdGetter<int32_t> {
  static constexpr uint32_t get() { return static_cast<uint32_t>(TypeId::INT32); }
};
template <> struct TypeIdGetter<int64_t> {
  static constexpr uint32_t get() { return static_cast<uint32_t>(TypeId::INT64); }
};
template <> struct TypeIdGetter<float> {
  static constexpr uint32_t get() { return static_cast<uint32_t>(TypeId::FLOAT32); }
};
template <> struct TypeIdGetter<double> {
  static constexpr uint32_t get() { return static_cast<uint32_t>(TypeId::FLOAT64); }
};
template <> struct TypeIdGetter<std::string> {
  static constexpr uint32_t get() { return static_cast<uint32_t>(TypeId::STRING); }
};

/// Helper struct to build FieldInfo for a single field
template <typename T, size_t Index>
struct FieldInfoBuilder {
  static FieldInfo build() {
    const auto field_info = ForyFieldInfo(T{});
    const auto field_names = decltype(field_info)::Names;
    const auto field_ptrs = decltype(field_info)::Ptrs;

    std::string field_name(field_names[Index]);
    const auto field_ptr = std::get<Index>(field_ptrs);
    using RawFieldType = typename meta::RemoveMemberPointerCVRefT<decltype(field_ptr)>;
    // Remove all cv-qualifiers and references to get the bare type
    using ActualFieldType = std::remove_cv_t<std::remove_reference_t<RawFieldType>>;

    // Get type_id using template specialization
    uint32_t type_id = TypeIdGetter<ActualFieldType>::get();

    return FieldInfo(field_name, fory::serialization::FieldType(type_id, false));
  }
};

/// Helper to build all FieldInfos for a struct
template <typename T, size_t... Indices>
std::vector<FieldInfo> build_field_infos_helper(std::index_sequence<Indices...>) {
  std::vector<FieldInfo> fields;
  fields.reserve(sizeof...(Indices));
  (fields.push_back(FieldInfoBuilder<T, Indices>::build()), ...);
  return fields;
}

/// Build TypeMeta for a struct type
template <typename T>
TypeMeta build_type_meta_for_struct() {
  const auto field_info = ForyFieldInfo(T{});
  constexpr size_t field_count = decltype(field_info)::Size;

  // Build field info for each field
  std::vector<FieldInfo> fields = build_field_infos_helper<T>(
      std::make_index_sequence<field_count>{});

  // Sort fields according to xlang spec
  auto sorted_fields = TypeMeta::sort_field_infos(std::move(fields));

  return TypeMeta::from_fields(
      0,  // type_id
      "", // namespace
      typeid(T).name(), // type_name
      false, // register_by_name
      std::move(sorted_fields));
}

/// Build a map from field name to original struct field index
template <typename T, size_t... Indices>
std::map<std::string, size_t> build_field_name_to_index_map(std::index_sequence<Indices...>) {
  const auto field_info = ForyFieldInfo(T{});
  const auto field_names = decltype(field_info)::Names;

  std::map<std::string, size_t> name_to_index;
  ((name_to_index[std::string(field_names[Indices])] = Indices), ...);

  return name_to_index;
}

/// Helper to write a single field
template <typename T, size_t Index, typename FieldPtrs>
Result<void, Error> write_single_field(const T &obj, WriteContext &ctx,
                                       const FieldPtrs &field_ptrs) {
  const auto field_ptr = std::get<Index>(field_ptrs);
  using FieldType = typename meta::RemoveMemberPointerCVRefT<decltype(field_ptr)>;
  const auto &field_value = obj.*field_ptr;

  // Always write field values with write_ref=false, write_type=false
  // (schema evolution is at struct level, not field level)
  constexpr bool field_needs_ref = requires_ref_metadata_v<FieldType>;
  return Serializer<FieldType>::write(field_value, ctx, field_needs_ref, false);
}

/// Write struct fields in sorted order (for compatible mode)
template <typename T>
Result<void, Error> write_struct_fields_sorted(const T &obj, WriteContext &ctx) {
  // Build type meta to get sorted field order
  TypeMeta type_meta = build_type_meta_for_struct<T>();
  const auto &sorted_fields = type_meta.get_field_infos();

  // Build mapping from field name to original index
  const auto field_info = ForyFieldInfo(obj);
  constexpr size_t field_count = decltype(field_info)::Size;
  auto name_to_index = build_field_name_to_index_map<T>(std::make_index_sequence<field_count>{});

  const auto field_ptrs = decltype(field_info)::Ptrs;

  // Write fields in sorted order
  for (const auto &field_meta : sorted_fields) {
    const std::string &field_name = field_meta.field_name;
    auto it = name_to_index.find(field_name);
    if (it == name_to_index.end()) {
      return Unexpected(Error::type_error("Field not found: " + field_name));
    }

    size_t original_index = it->second;

    // Dispatch to write the field at the original index
    bool handled = false;
    Result<void, Error> result;

    [&]<size_t... Indices>(std::index_sequence<Indices...>) {
      ((original_index == Indices
        ? (handled = true,
           result = write_single_field<T, Indices>(obj, ctx, field_ptrs),
           true)
        : false), ...);
    }(std::make_index_sequence<field_count>{});

    if (!handled) {
      return Unexpected(Error::type_error("Failed to write field: " + field_name));
    }
    if (!result.ok()) {
      return result;
    }
  }

  return Result<void, Error>();
}

/// Write struct fields recursively using index sequence (declaration order)
template <typename T, size_t... Indices>
Result<void, Error> write_struct_fields_impl(const T &obj, WriteContext &ctx,
                                             std::index_sequence<Indices...>) {
  // Get field info from FORY_FIELD_INFO via ADL
  const auto field_info = ForyFieldInfo(obj);
  const auto field_ptrs = decltype(field_info)::Ptrs;

  // Write each field with early return on error
  Result<void, Error> result;
  ((result = write_single_field<T, Indices>(obj, ctx, field_ptrs), result.ok()) && ...);
  return result;
}

/// Helper to read a single field by index
template <size_t Index, typename T>
Result<void, Error> read_single_field_by_index(T &obj, ReadContext &ctx) {
  const auto field_info = ForyFieldInfo(obj);
  const auto field_ptrs = decltype(field_info)::Ptrs;
  const auto field_ptr = std::get<Index>(field_ptrs);
  using FieldType = typename meta::RemoveMemberPointerCVRefT<decltype(field_ptr)>;

  // Always read field values with read_ref=false, read_type=false
  // (schema evolution is at struct level, not field level)
  constexpr bool field_needs_ref = requires_ref_metadata_v<FieldType>;
  auto field_result = Serializer<FieldType>::read(ctx, field_needs_ref, false);
  if (!field_result.ok()) {
    return Unexpected(std::move(field_result).error());
  }

  obj.*field_ptr = std::move(field_result).value();
  return Result<void, Error>();
}

/// Read struct fields recursively using index sequence (non-compatible mode)
template <typename T, size_t... Indices>
Result<void, Error> read_struct_fields_impl(T &obj, ReadContext &ctx,
                                            std::index_sequence<Indices...>) {
  // Read each field in order
  Result<void, Error> result;
  ((result = read_single_field_by_index<Indices>(obj, ctx), result.ok()) && ...);
  return result;
}

/// Helper to read a field by name (looks up original index)
template <typename T, size_t... Indices>
Result<void, Error> read_field_by_name(
    T &obj, ReadContext &ctx, const std::string &field_name,
    const std::map<std::string, size_t> &name_to_index,
    std::index_sequence<Indices...>) {

  auto it = name_to_index.find(field_name);
  if (it == name_to_index.end()) {
    return Unexpected(Error::type_error("Field not found: " + field_name));
  }

  size_t original_index = it->second;

  // Use fold expression to dispatch to the correct index
  bool handled = false;
  Result<void, Error> result;

  ((original_index == Indices
    ? (handled = true,
       result = read_single_field_by_index<Indices>(obj, ctx),
       true)
    : false), ...);

  if (!handled) {
    return Unexpected(Error::type_error("Failed to dispatch field: " + field_name));
  }

  return result;
}

/// Read struct fields with schema evolution (compatible mode)
/// This implements the switch/jump table approach from xlang spec
template <typename T, size_t... Indices>
Result<void, Error> read_struct_fields_compatible(
    T &obj, ReadContext &ctx, const std::shared_ptr<TypeMeta> &remote_type_meta,
    std::index_sequence<Indices...>) {

  const auto &remote_fields = remote_type_meta->get_field_infos();

  // Build mapping from field name to original struct index
  auto name_to_index = build_field_name_to_index_map<T>(std::index_sequence<Indices...>{});

  // Iterate over remote fields
  for (const auto &remote_field : remote_fields) {
    int16_t field_id = remote_field.field_id;
    const std::string &field_name = remote_field.field_name;

    // If field_id == -1, the field doesn't exist in local type, skip it
    if (field_id == -1) {
      // Skip with read_ref_and_type=false to match how we write fields
      auto skip_result = skip_field_value(ctx, remote_field.field_type, false);
      if (!skip_result.ok()) {
        return Unexpected(std::move(skip_result).error());
      }
      continue;
    }

    // Use field name to find original index and read the field
    auto read_result = read_field_by_name<T>(obj, ctx, field_name, name_to_index,
                                              std::index_sequence<Indices...>{});
    if (!read_result.ok()) {
      return Unexpected(std::move(read_result).error());
    }
  }

  return Result<void, Error>();
}

} // namespace detail

/// Serializer for types registered with FORY_STRUCT
template <typename T>
struct Serializer<T, std::enable_if_t<is_fory_serializable_v<T>>> {
  static constexpr TypeId type_id = TypeId::STRUCT;

  static Result<void, Error> write(const T &obj, WriteContext &ctx,
                                   bool write_ref, bool write_type) {
    write_not_null_ref_flag(ctx, write_ref);

    if (write_type) {
      ctx.write_uint8(static_cast<uint8_t>(type_id));
    }

    // Increase depth tracking
    FORY_RETURN_NOT_OK(ctx.increase_depth());

    // Choose serialization approach based on compatible flag
    if (ctx.is_compatible()) {
      // Compatible mode: write type_meta first, then fields in sorted order
      TypeMeta type_meta = detail::build_type_meta_for_struct<T>();
      auto meta_bytes_result = type_meta.to_bytes();
      if (!meta_bytes_result.ok()) {
        ctx.decrease_depth();
        return Unexpected(std::move(meta_bytes_result).error());
      }
      auto &meta_bytes = meta_bytes_result.value();
      ctx.write_bytes(meta_bytes.data(), meta_bytes.size());

      // Write field values in SORTED order (must match type_meta field order!)
      FORY_RETURN_NOT_OK(detail::write_struct_fields_sorted(obj, ctx));
    } else {
      // Non-compatible mode: write fields in declaration order
      const auto field_info = ForyFieldInfo(obj);
      const size_t field_count = decltype(field_info)::Size;
      FORY_RETURN_NOT_OK(detail::write_struct_fields_impl(
          obj, ctx, std::make_index_sequence<field_count>{}));
    }

    // Decrease depth tracking
    ctx.decrease_depth();

    return Result<void, Error>();
  }

  static Result<void, Error> write_data(const T &obj, WriteContext &ctx) {
    // Increase depth tracking
    FORY_RETURN_NOT_OK(ctx.increase_depth());

    // Write all fields
    const auto field_info = ForyFieldInfo(obj);
    const size_t field_count = decltype(field_info)::Size;
    FORY_RETURN_NOT_OK(detail::write_struct_fields_impl(
        obj, ctx, std::make_index_sequence<field_count>{}));

    // Decrease depth tracking
    ctx.decrease_depth();

    return Result<void, Error>();
  }

  static Result<void, Error> write_data_generic(const T &obj, WriteContext &ctx,
                                                bool has_generics) {
    // For structs, has_generics doesn't change behavior
    return write_data(obj, ctx);
  }

  static Result<T, Error> read(ReadContext &ctx, bool read_ref,
                               bool read_type) {
  // Handle reference metadata
      auto has_value_result = consume_ref_flag(ctx, read_ref);
      if (!has_value_result.ok()) {
        return Unexpected(std::move(has_value_result).error());
      }
      if (!has_value_result.value()) {
        if constexpr (std::is_default_constructible_v<T>) {
          return T();
        } else {
          return Unexpected(
              Error::invalid_data("Null value encountered for non-default-constructible struct"));
        }
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

    // Increase depth tracking
    FORY_RETURN_NOT_OK(ctx.increase_depth());

    // Initialize object with default values
    T obj{};

    const auto field_info = ForyFieldInfo(obj);
    constexpr size_t field_count = decltype(field_info)::Size;

    // Choose deserialization mode based on compatible flag
    if (ctx.is_compatible()) {
      // Schema evolution mode: read type_meta first, then read fields with schema evolution

      // Step 1: Build local type meta
      TypeMeta local_type_meta = detail::build_type_meta_for_struct<T>();

      // Step 2: Read remote type meta and assign field IDs
      auto remote_type_meta_result = TypeMeta::from_bytes(ctx.buffer(), &local_type_meta);
      if (!remote_type_meta_result.ok()) {
        ctx.decrease_depth();
        return Unexpected(std::move(remote_type_meta_result).error());
      }
      auto remote_type_meta = std::move(remote_type_meta_result).value();

      // Step 3: Check if hashes match (fast path)
      if (remote_type_meta->get_hash() == local_type_meta.get_hash()) {
        // Hashes match - types are identical, read fields directly
        FORY_RETURN_NOT_OK(detail::read_struct_fields_impl(
            obj, ctx, std::make_index_sequence<field_count>{}));
      } else {
        // Hashes differ - use schema evolution path with switch/jump table
        FORY_RETURN_NOT_OK(detail::read_struct_fields_compatible(
            obj, ctx, remote_type_meta, std::make_index_sequence<field_count>{}));
      }
    } else {
      // Non-compatible mode: read fields in order
      FORY_RETURN_NOT_OK(detail::read_struct_fields_impl(
          obj, ctx, std::make_index_sequence<field_count>{}));
    }

    // Decrease depth tracking
    ctx.decrease_depth();

    return obj;
  }

  static Result<T, Error> read_data(ReadContext &ctx) {
    // Increase depth tracking
    FORY_RETURN_NOT_OK(ctx.increase_depth());

    // Read all fields
    T obj{};
    const auto field_info = ForyFieldInfo(obj);
    const size_t field_count = decltype(field_info)::Size;
    FORY_RETURN_NOT_OK(detail::read_struct_fields_impl(
        obj, ctx, std::make_index_sequence<field_count>{}));

    // Decrease depth tracking
    ctx.decrease_depth();

    return obj;
  }

  // Optimized read when type info already known (for polymorphic collections)
  // This method is critical for the optimization described in xlang spec section 5.4.4
  // When deserializing List<Base> where all elements are same concrete type,
  // we read type info once and pass it to all element deserializers
  static Result<T, Error> read_with_type_info(ReadContext &ctx, bool read_ref,
                                               const TypeInfo &type_info) {
    // Type info already validated, skip redundant type read
    return read(ctx, read_ref, false);  // read_type=false
  }
};

} // namespace serialization
} // namespace fory
