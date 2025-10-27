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
#include "fory/serialization/serializer_traits.h"
#include "fory/serialization/skip.h"
#include "fory/serialization/type_resolver.h"
#include <algorithm>
#include <array>
#include <memory>
#include <numeric>
#include <tuple>
#include <type_traits>
#include <unordered_map>
#include <utility>
#include <vector>

namespace fory {
namespace serialization {

/// Serialization metadata for a type.
///
/// This template is populated automatically when `FORY_STRUCT` is used to
/// register a type. The registration macro defines an ADL-visible marker
/// function which this trait detects in order to enable serialization. The
/// field count is derived from the generated `ForyFieldInfo` metadata.
template <typename T, typename Enable> struct SerializationMeta {
  static constexpr bool is_serializable = false;
  static constexpr size_t field_count = 0;
};
template <typename T>
struct SerializationMeta<
    T, std::void_t<decltype(ForyStructMarker(std::declval<const T &>()))>> {
  static constexpr bool is_serializable = true;
  static constexpr size_t field_count =
      decltype(ForyFieldInfo(std::declval<const T &>()))::Size;
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
  inline constexpr std::true_type ForyStructMarker(const Type &) noexcept {    \
    return {};                                                                 \
  }

namespace detail {

// Forward declare to avoid circular dependency
template <typename T, std::enable_if_t<is_fory_serializable_v<T>, int> = 0>
struct StructSerializerHelper;

template <size_t... Indices, typename Func>
void for_each_index(std::index_sequence<Indices...>, Func &&func) {
  (func(std::integral_constant<size_t, Indices>{}), ...);
}

template <typename T, typename Func, size_t... Indices>
Result<void, Error> dispatch_field_index_impl(size_t target_index, Func &&func,
                                              std::index_sequence<Indices...>) {
  Result<void, Error> result;
  bool handled =
      ((target_index == Indices
            ? (result = func(std::integral_constant<size_t, Indices>{}), true)
            : false) ||
       ...);
  if (!handled) {
    return Unexpected(Error::type_error("Failed to dispatch field index: " +
                                        std::to_string(target_index)));
  }
  return result;
}

template <typename T, typename Func>
Result<void, Error> dispatch_field_index(size_t target_index, Func &&func) {
  constexpr size_t field_count =
      decltype(ForyFieldInfo(std::declval<const T &>()))::Size;
  return dispatch_field_index_impl<T>(target_index, std::forward<Func>(func),
                                      std::make_index_sequence<field_count>{});
}

// ------------------------------------------------------------------
// Compile-time helpers to compute sorted field indices / names and
// create small jump-table wrappers to unroll read/write per-field calls.
// The goal is to mimic the Rust-derived serializer behaviour where the
// sorted field order is known at compile-time and the read path for
// compatible mode uses a fast switch/jump table.
// ------------------------------------------------------------------

template <typename T>
struct CompileTimeFieldHelpers {
  using FieldDescriptor = decltype(ForyFieldInfo(std::declval<const T &>()));
  static constexpr size_t FieldCount = FieldDescriptor::Size;
  static inline constexpr auto Names = FieldDescriptor::Names;
  static inline constexpr auto Ptrs = FieldDescriptor::Ptrs;
  using FieldPtrs = decltype(Ptrs);

  template <size_t Index>
  static constexpr uint32_t field_type_id() {
    if constexpr (FieldCount == 0) {
      return 0;
    } else {
      using PtrT = std::tuple_element_t<Index, FieldPtrs>;
      using FieldType = meta::RemoveMemberPointerCVRefT<PtrT>;
      return static_cast<uint32_t>(Serializer<FieldType>::type_id);
    }
  }

  template <size_t Index>
  static constexpr bool field_nullable() {
    if constexpr (FieldCount == 0) {
      return false;
    } else {
      using PtrT = std::tuple_element_t<Index, FieldPtrs>;
      using FieldType = meta::RemoveMemberPointerCVRefT<PtrT>;
      return requires_ref_metadata_v<FieldType>;
    }
  }

  template <size_t... Indices>
  static constexpr std::array<uint32_t, FieldCount>
  make_type_ids(std::index_sequence<Indices...>) {
    if constexpr (FieldCount == 0) {
      return {};
    } else {
      return {field_type_id<Indices>()...};
    }
  }

  template <size_t... Indices>
  static constexpr std::array<bool, FieldCount>
  make_nullable_flags(std::index_sequence<Indices...>) {
    if constexpr (FieldCount == 0) {
      return {};
    } else {
      return {field_nullable<Indices>()...};
    }
  }

  static inline constexpr std::array<uint32_t, FieldCount> type_ids =
      make_type_ids(std::make_index_sequence<FieldCount>{});

  static inline constexpr std::array<bool, FieldCount> nullable_flags =
      make_nullable_flags(std::make_index_sequence<FieldCount>{});

  static constexpr bool is_primitive_type_id(uint32_t tid) {
    return tid >= static_cast<uint32_t>(TypeId::BOOL) &&
           tid <= static_cast<uint32_t>(TypeId::FLOAT64);
  }

  static constexpr int32_t primitive_type_size(uint32_t tid) {
    switch (static_cast<TypeId>(tid)) {
    case TypeId::BOOL:
    case TypeId::INT8:
      return 1;
    case TypeId::INT16:
    case TypeId::FLOAT16:
      return 2;
    case TypeId::INT32:
    case TypeId::VAR_INT32:
    case TypeId::FLOAT32:
      return 4;
    case TypeId::INT64:
    case TypeId::VAR_INT64:
    case TypeId::FLOAT64:
      return 8;
    default:
      return 0;
    }
  }

  static constexpr bool is_compress_id(uint32_t tid) {
    return tid == static_cast<uint32_t>(TypeId::INT32) ||
           tid == static_cast<uint32_t>(TypeId::INT64) ||
           tid == static_cast<uint32_t>(TypeId::VAR_INT32) ||
           tid == static_cast<uint32_t>(TypeId::VAR_INT64);
  }

  static constexpr int group_rank(size_t index) {
    if constexpr (FieldCount == 0) {
      return 6;
    } else {
      uint32_t tid = type_ids[index];
      bool nullable = nullable_flags[index];
      if (is_primitive_type_id(tid)) {
        return nullable ? 1 : 0;
      }
      if (tid == static_cast<uint32_t>(TypeId::LIST))
        return 3;
      if (tid == static_cast<uint32_t>(TypeId::SET))
        return 4;
      if (tid == static_cast<uint32_t>(TypeId::MAP))
        return 5;
      if (tid >= static_cast<uint32_t>(TypeId::STRING) &&
          tid <= static_cast<uint32_t>(TypeId::DECIMAL))
        return 2;
      return 6;
    }
  }

  static constexpr bool field_compare(size_t a, size_t b) {
    if constexpr (FieldCount == 0) {
      return false;
    } else {
      int ga = group_rank(a);
      int gb = group_rank(b);
      if (ga != gb)
        return ga < gb;

      uint32_t a_tid = type_ids[a];
      uint32_t b_tid = type_ids[b];
      bool a_null = nullable_flags[a];
      bool b_null = nullable_flags[b];

      if (ga == 0 || ga == 1) {
        bool compress_a = is_compress_id(a_tid);
        bool compress_b = is_compress_id(b_tid);
        int32_t sa = primitive_type_size(a_tid);
        int32_t sb = primitive_type_size(b_tid);
        if (a_null != b_null)
          return !a_null;
        if (compress_a != compress_b)
          return !compress_a;
        if (sa != sb)
          return sa > sb;
        if (a_tid != b_tid)
          return a_tid < b_tid;
        return Names[a] < Names[b];
      }

      if (ga == 2) {
        if (a_tid != b_tid)
          return a_tid < b_tid;
        return Names[a] < Names[b];
      }

      return Names[a] < Names[b];
    }
  }

  static constexpr std::array<size_t, FieldCount> compute_sorted_indices() {
    std::array<size_t, FieldCount> indices{};
    for (size_t i = 0; i < FieldCount; ++i) {
      indices[i] = i;
    }
    for (size_t i = 0; i < FieldCount; ++i) {
      size_t best = i;
      for (size_t j = i + 1; j < FieldCount; ++j) {
        if (field_compare(indices[j], indices[best])) {
          best = j;
        }
      }
      if (best != i) {
        size_t tmp = indices[i];
        indices[i] = indices[best];
        indices[best] = tmp;
      }
    }
    return indices;
  }

  static inline constexpr std::array<size_t, FieldCount> sorted_indices =
      compute_sorted_indices();

  static inline constexpr std::array<std::string_view, FieldCount>
      sorted_field_names = []() constexpr {
        std::array<std::string_view, FieldCount> arr{};
        for (size_t i = 0; i < FieldCount; ++i) {
          arr[i] = Names[sorted_indices[i]];
        }
        return arr;
      }();
};

template <typename T, size_t Index, typename FieldPtrs>
Result<void, Error> write_single_field(const T &obj, WriteContext &ctx,
               const FieldPtrs &field_ptrs);

template <size_t Index, typename T>
Result<void, Error> read_single_field_by_index(T &obj, ReadContext &ctx);

template <typename T, size_t Index>
struct WriteSortedDispatcher {
  using Helpers = CompileTimeFieldHelpers<T>;
  using FieldPtrs = typename Helpers::FieldPtrs;

  static Result<void, Error> apply(const T &obj, WriteContext &ctx,
                                   const FieldPtrs &field_ptrs) {
    if constexpr (Index >= Helpers::FieldCount) {
      (void)obj;
      (void)ctx;
      (void)field_ptrs;
      return Result<void, Error>();
    } else {
      auto result = dispatch_field_index<T>(Helpers::sorted_indices[Index],
                                            [&](auto index_constant) {
                                              constexpr size_t index =
                                                  decltype(index_constant)::value;
                                              return write_single_field<T, index>(
                                                  obj, ctx, field_ptrs);
                                            });
      if (!result.ok()) {
        return result;
      }
      return WriteSortedDispatcher<T, Index + 1>::apply(obj, ctx, field_ptrs);
    }
  }
};

template <typename T, size_t Index>
struct ReadSortedDispatcher {
  using Helpers = CompileTimeFieldHelpers<T>;

  static Result<void, Error> apply(T &obj, ReadContext &ctx) {
    if constexpr (Index >= Helpers::FieldCount) {
      (void)obj;
      (void)ctx;
      return Result<void, Error>();
    } else {
      auto result = dispatch_field_index<T>(Helpers::sorted_indices[Index],
                                            [&](auto index_constant) {
                                              constexpr size_t index =
                                                  decltype(index_constant)::value;
                                              return read_single_field_by_index<index>(
                                                  obj, ctx);
                                            });
      if (!result.ok()) {
        return result;
      }
      return ReadSortedDispatcher<T, Index + 1>::apply(obj, ctx);
    }
  }
};


/// Helper to write a single field
template <typename T, size_t Index, typename FieldPtrs>
Result<void, Error> write_single_field(const T &obj, WriteContext &ctx,
                                       const FieldPtrs &field_ptrs) {
  const auto field_ptr = std::get<Index>(field_ptrs);
  using FieldType =
      typename meta::RemoveMemberPointerCVRefT<decltype(field_ptr)>;
  const auto &field_value = obj.*field_ptr;

  // Always write field values with write_ref=false, write_type=false
  // (schema evolution is at struct level, not field level)
  constexpr bool field_needs_ref = requires_ref_metadata_v<FieldType>;
  return Serializer<FieldType>::write(field_value, ctx, field_needs_ref, false);
}

/// Write struct fields in sorted order (for compatible mode)
template <typename T>
Result<void, Error>
write_struct_fields_sorted(const T &obj, WriteContext &ctx,
                           const TypeResolver::TypeInfo &type_info) {
  (void)type_info;

  using Helpers = CompileTimeFieldHelpers<T>;
  const auto &field_ptrs = Helpers::Ptrs;
  return WriteSortedDispatcher<T, 0>::apply(obj, ctx, field_ptrs);
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
  ((result = write_single_field<T, Indices>(obj, ctx, field_ptrs),
    result.ok()) &&
   ...);
  return result;
}

/// Helper to read a single field by index
template <size_t Index, typename T>
Result<void, Error> read_single_field_by_index(T &obj, ReadContext &ctx) {
  const auto field_info = ForyFieldInfo(obj);
  const auto field_ptrs = decltype(field_info)::Ptrs;
  const auto field_ptr = std::get<Index>(field_ptrs);
  using FieldType =
      typename meta::RemoveMemberPointerCVRefT<decltype(field_ptr)>;

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
  ((result = read_single_field_by_index<Indices>(obj, ctx), result.ok()) &&
   ...);
  return result;
}

/// Helper to read a field by name (looks up original index)
template <typename T, size_t... Indices>
Result<void, Error>
read_field_by_name(T &obj, ReadContext &ctx, const std::string &field_name,
                   const std::unordered_map<std::string, size_t> &name_to_index,
                   std::index_sequence<Indices...>) {

  auto iter_lookup = name_to_index.find(field_name);
  if (iter_lookup == name_to_index.end()) {
    return Unexpected(
        Error::type_error("Failed to dispatch field: " + field_name));
  }
  size_t original_index = iter_lookup->second;

  // Use fold expression to dispatch to the correct index
  bool handled = false;
  Result<void, Error> result;

  detail::for_each_index(
      std::index_sequence<Indices...>{}, [&](auto index_constant) {
        constexpr size_t index = decltype(index_constant)::value;
        if (!handled && original_index == index) {
          handled = true;
          result = read_single_field_by_index<index>(obj, ctx);
        }
      });

  if (!handled) {
    return Unexpected(
        Error::type_error("Failed to dispatch field: " + field_name));
  }

  return result;
}

/// Read struct fields with schema evolution (compatible mode)
/// This implements the switch/jump table approach from xlang spec
template <typename T, size_t... Indices>
Result<void, Error>
read_struct_fields_compatible(T &obj, ReadContext &ctx,
                              const std::shared_ptr<TypeMeta> &remote_type_meta,
                              std::index_sequence<Indices...>) {

  using Helpers = CompileTimeFieldHelpers<T>;
  const auto &remote_fields = remote_type_meta->get_field_infos();
  const auto &remote_fields_ref = remote_fields;

  // Fast dispatch: remote_field.field_id maps to local sorted index (or -1
  // if unknown). We unroll a small if-chain over compile-time indices so the
  // compiler can produce an efficient jump table.
  for (const auto &remote_field : remote_fields_ref) {
    int16_t field_id = remote_field.field_id;

    if (field_id == -1) {
      // Field unknown locally — skip its value
      auto skip_result = skip_field_value(ctx, remote_field.field_type, false);
      if (!skip_result.ok()) {
        return Unexpected(std::move(skip_result).error());
      }
      continue;
    }

    bool handled = false;
    Result<void, Error> result;

    // Unrolled dispatch by comparing field_id against compile-time indices.
    detail::for_each_index(std::index_sequence<Indices...>{}, [&](auto index_constant) {
      constexpr size_t index = decltype(index_constant)::value;
      if (!handled && static_cast<int16_t>(index) == field_id) {
        handled = true;
        constexpr size_t original_index = Helpers::sorted_indices[index];
        result = read_single_field_by_index<original_index>(obj, ctx);
      }
    });

    if (!handled) {
      // Shouldn't happen if TypeMeta::assign_field_ids worked correctly, but
      // as a fallback skip the field value.
      auto skip_result = skip_field_value(ctx, remote_field.field_type, false);
      if (!skip_result.ok()) {
        return Unexpected(std::move(skip_result).error());
      }
      continue;
    }

    if (!result.ok()) {
      return Unexpected(std::move(result).error());
    }
  }

  return Result<void, Error>();
}

/// Read struct fields in sorted order using local metadata
template <typename T>
Result<void, Error>
read_struct_fields_sorted(T &obj, ReadContext &ctx,
                          const TypeResolver::TypeInfo &type_info) {
  (void)type_info;
  return ReadSortedDispatcher<T, 0>::apply(obj, ctx);
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
      uint32_t type_tag = ctx.type_resolver().struct_type_tag<T>();
      ctx.write_varuint32(type_tag);
    }

    auto type_info = ctx.type_resolver().template get_struct_type_info<T>();
    FORY_CHECK(type_info)
        << "Type metadata not initialized for requested struct";
    if (ctx.is_compatible() && type_info && type_info->type_meta) {
      const auto &meta_bytes = type_info->type_def;
      ctx.write_bytes(meta_bytes.data(), meta_bytes.size());
    }

    return detail::write_struct_fields_sorted(obj, ctx, *type_info);
  }

  static Result<void, Error> write_data(const T &obj, WriteContext &ctx) {
    auto type_info = ctx.type_resolver().template get_struct_type_info<T>();
    FORY_CHECK(type_info)
        << "Type metadata not initialized for requested struct";
    return detail::write_struct_fields_sorted(obj, ctx, *type_info);
  }

  static Result<void, Error> write_data_generic(const T &obj, WriteContext &ctx,
                                                bool has_generics) {
    (void)has_generics;
    auto type_info = ctx.type_resolver().template get_struct_type_info<T>();
    FORY_CHECK(type_info)
        << "Type metadata not initialized for requested struct";
    return detail::write_struct_fields_sorted(obj, ctx, *type_info);
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
        return Unexpected(Error::invalid_data(
            "Null value encountered for non-default-constructible struct"));
      }
    }

    auto type_info = ctx.type_resolver().template get_struct_type_info<T>();
    FORY_CHECK(type_info)
        << "Type metadata not initialized for requested struct";

    if (read_type) {
      auto type_id_result = ctx.read_varuint32();
      if (!type_id_result.ok()) {
        return Unexpected(std::move(type_id_result).error());
      }
      uint32_t type_tag = type_id_result.value();
      uint32_t expected_tag = ctx.type_resolver().struct_type_tag(*type_info);
      if (type_tag != expected_tag) {
        return Unexpected(Error::type_mismatch(type_tag, expected_tag));
      }
    }

    FORY_RETURN_NOT_OK(ctx.increase_depth());
    T obj{};
    using FieldDescriptor = decltype(ForyFieldInfo(std::declval<const T &>()));
    constexpr size_t field_count = FieldDescriptor::Size;

    auto finish = [&ctx]() { ctx.decrease_depth(); };

    if (!ctx.is_compatible() || !type_info->type_meta) {
      auto status = detail::read_struct_fields_sorted(obj, ctx, *type_info);
      finish();
      if (!status.ok()) {
        return Unexpected(std::move(status).error());
      }
      return obj;
    }

    auto remote_type_meta_result =
        TypeMeta::from_bytes(ctx.buffer(), type_info->type_meta.get());
    if (!remote_type_meta_result.ok()) {
      finish();
      return Unexpected(std::move(remote_type_meta_result).error());
    }
    auto remote_type_meta = std::move(remote_type_meta_result).value();

    Result<void, Error> status;
    if (remote_type_meta->get_hash() == type_info->type_meta->get_hash()) {
      status = detail::read_struct_fields_sorted(obj, ctx, *type_info);
    } else {
      status = detail::read_struct_fields_compatible(
          obj, ctx, remote_type_meta, std::make_index_sequence<field_count>{});
    }

    finish();
    if (!status.ok()) {
      return Unexpected(std::move(status).error());
    }
    return obj;
  }

  static Result<T, Error> read_data(ReadContext &ctx) {
    // Increase depth tracking
    FORY_RETURN_NOT_OK(ctx.increase_depth());

    // Read all fields in sorted order
    T obj{};
    auto type_info = ctx.type_resolver().template get_struct_type_info<T>();
    FORY_CHECK(type_info)
        << "Type metadata not initialized for requested struct";
    auto status = detail::read_struct_fields_sorted(obj, ctx, *type_info);

    // Decrease depth tracking
    ctx.decrease_depth();

    if (!status.ok()) {
      return Unexpected(std::move(status).error());
    }
    return obj;
  }

  // Optimized read when type info already known (for polymorphic collections)
  // This method is critical for the optimization described in xlang spec
  // section 5.4.4 When deserializing List<Base> where all elements are same
  // concrete type, we read type info once and pass it to all element
  // deserializers
  static Result<T, Error>
  read_with_type_info(ReadContext &ctx, bool read_ref,
                      const TypeResolver::TypeInfo &type_info) {
    // Type info already validated, skip redundant type read
    return read(ctx, read_ref, false); // read_type=false
  }
};

} // namespace serialization
} // namespace fory
