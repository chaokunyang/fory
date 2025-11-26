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
#include "fory/util/string_util.h"
#include <algorithm>
#include <array>
#include <memory>
#include <numeric>
#include <string_view>
#include <tuple>
#include <type_traits>
#include <unordered_map>
#include <utility>
#include <vector>

#ifdef FORY_DEBUG
#include <iostream>
#endif

namespace fory {
namespace serialization {

/// Field type markers for collection fields in compatible/evolution mode.
/// These match Java's FieldResolver.FieldTypes values.
constexpr int8_t FIELD_TYPE_OBJECT = 0;
constexpr int8_t FIELD_TYPE_COLLECTION_ELEMENT_FINAL = 1;
constexpr int8_t FIELD_TYPE_MAP_KEY_FINAL = 2;
constexpr int8_t FIELD_TYPE_MAP_VALUE_FINAL = 3;
constexpr int8_t FIELD_TYPE_MAP_KV_FINAL = 4;

/// Check if a field needs reference/null flags in the stream.
///
/// This mirrors Rust's
/// `field_need_write_ref_into(type_id, nullable)` in
/// rust/fory-core/src/serializer/util.rs and determines
/// whether the writer emits a `RefFlag` byte before the
/// field's value payload.
inline bool field_requires_ref_flag(uint32_t type_id, bool nullable) {
  if (nullable) {
    return true;
  }

  uint32_t internal = type_id & 0xffu;
  TypeId tid = static_cast<TypeId>(internal);
  switch (tid) {
  // Primitive numeric / bool types never write ref flags
  case TypeId::BOOL:
  case TypeId::INT8:
  case TypeId::INT16:
  case TypeId::INT32:
  case TypeId::INT64:
  case TypeId::FLOAT32:
  case TypeId::FLOAT64:
    return false;
  // Enums in xlang are written without ref flags (see Rust
  // enum serializer and Java EnumSerializer.xwrite), so we
  // must not try to consume a ref flag for enum fields.
  case TypeId::ENUM:
  case TypeId::NAMED_ENUM:
    return false;
  default:
    return true;
  }
}

inline bool field_requires_ref_flag(uint32_t type_id) {
  return field_requires_ref_flag(type_id, false);
}

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

/// Helper to check if a TypeId represents a primitive type.
/// Per xlang spec, primitive types are: bool, int8-64, var_int32/64,
/// sli_int64, float16/32/64. All other types (string, list, set, map, struct,
/// enum, etc.) are non-primitive and require ref flags.
inline constexpr bool is_primitive_type_id(TypeId type_id) {
  return type_id == TypeId::BOOL || type_id == TypeId::INT8 ||
         type_id == TypeId::INT16 || type_id == TypeId::INT32 ||
         type_id == TypeId::VAR_INT32 || type_id == TypeId::INT64 ||
         type_id == TypeId::VAR_INT64 || type_id == TypeId::SLI_INT64 ||
         type_id == TypeId::FLOAT16 || type_id == TypeId::FLOAT32 ||
         type_id == TypeId::FLOAT64;
}

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

template <typename T> struct CompileTimeFieldHelpers {
  using FieldDescriptor = decltype(ForyFieldInfo(std::declval<const T &>()));
  static constexpr size_t FieldCount = FieldDescriptor::Size;
  static inline constexpr auto Names = FieldDescriptor::Names;
  static inline constexpr auto Ptrs = FieldDescriptor::Ptrs;
  using FieldPtrs = decltype(Ptrs);

  template <size_t Index> static constexpr uint32_t field_type_id() {
    if constexpr (FieldCount == 0) {
      return 0;
    } else {
      using PtrT = std::tuple_element_t<Index, FieldPtrs>;
      using FieldType = meta::RemoveMemberPointerCVRefT<PtrT>;
      return static_cast<uint32_t>(Serializer<FieldType>::type_id);
    }
  }

  template <size_t Index> static constexpr bool field_nullable() {
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

  static inline constexpr std::array<size_t, FieldCount> snake_case_lengths =
      []() constexpr {
        std::array<size_t, FieldCount> lengths{};
        if constexpr (FieldCount > 0) {
          for (size_t i = 0; i < FieldCount; ++i) {
            lengths[i] = ::fory::snake_case_length(Names[i]);
          }
        }
        return lengths;
      }();

  static constexpr size_t compute_max_snake_length() {
    size_t max_length = 0;
    if constexpr (FieldCount > 0) {
      for (size_t length : snake_case_lengths) {
        if (length > max_length) {
          max_length = length;
        }
      }
    }
    return max_length;
  }

  static inline constexpr size_t max_snake_case_length =
      compute_max_snake_length();

  static inline constexpr std::array<
      std::array<char, max_snake_case_length + 1>, FieldCount>
      snake_case_storage = []() constexpr {
        std::array<std::array<char, max_snake_case_length + 1>, FieldCount>
            storage{};
        if constexpr (FieldCount > 0) {
          for (size_t i = 0; i < FieldCount; ++i) {
            const auto [buffer, length] =
                ::fory::to_snake_case<max_snake_case_length>(Names[i]);
            (void)length;
            storage[i] = buffer;
          }
        }
        return storage;
      }();

  static inline constexpr std::array<std::string_view, FieldCount>
      snake_case_names = []() constexpr {
        std::array<std::string_view, FieldCount> names{};
        if constexpr (FieldCount > 0) {
          for (size_t i = 0; i < FieldCount; ++i) {
            names[i] = std::string_view(snake_case_storage[i].data(),
                                        snake_case_lengths[i]);
          }
        }
        return names;
      }();

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

  /// Check if a type ID is an internal (built-in, final) type for group 2.
  /// Internal types are STRING, DURATION, TIMESTAMP, LOCAL_DATE, DECIMAL, BINARY.
  /// Java xlang DescriptorGrouper excludes enums from finals (line 897 in XtypeResolver).
  /// Excludes: ENUM (13-14), STRUCT (15-18), EXT (19-20), LIST (21), SET (22), MAP (23)
  static constexpr bool is_internal_type_id(uint32_t tid) {
    return tid == static_cast<uint32_t>(TypeId::STRING) ||
           (tid >= static_cast<uint32_t>(TypeId::DURATION) &&
            tid <= static_cast<uint32_t>(TypeId::BINARY));
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
      // Check LIST/SET/MAP BEFORE is_internal_type_id since they fall
      // within the internal type range (STRING=12 to DECIMAL=27) but
      // need their own groups for proper field ordering.
      if (tid == static_cast<uint32_t>(TypeId::LIST))
        return 3;
      if (tid == static_cast<uint32_t>(TypeId::SET))
        return 4;
      if (tid == static_cast<uint32_t>(TypeId::MAP))
        return 5;
      if (is_internal_type_id(tid))
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
        return snake_case_names[a] < snake_case_names[b];
      }

      if (ga == 2) {
        if (a_tid != b_tid)
          return a_tid < b_tid;
        return snake_case_names[a] < snake_case_names[b];
      }

      return snake_case_names[a] < snake_case_names[b];
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
          arr[i] = snake_case_names[sorted_indices[i]];
        }
        return arr;
      }();
};

template <typename T, size_t Index, typename FieldPtrs>
Result<void, Error> write_single_field(const T &obj, WriteContext &ctx,
                                       const FieldPtrs &field_ptrs);

template <size_t Index, typename T>
Result<void, Error> read_single_field_by_index(T &obj, ReadContext &ctx);

/// Helper to write a single field
template <typename T, size_t Index, typename FieldPtrs>
Result<void, Error> write_single_field(const T &obj, WriteContext &ctx,
                                       const FieldPtrs &field_ptrs,
                                       bool has_generics) {
  const auto field_ptr = std::get<Index>(field_ptrs);
  using FieldType =
      typename meta::RemoveMemberPointerCVRefT<decltype(field_ptr)>;
  const auto &field_value = obj.*field_ptr;

  constexpr TypeId field_type_id = Serializer<FieldType>::type_id;
  constexpr bool is_primitive_field = is_primitive_type_id(field_type_id);
  constexpr bool field_needs_ref = requires_ref_metadata_v<FieldType>;

  // Per Rust implementation: primitives are written directly without ref/type
  if constexpr (is_primitive_field && !field_needs_ref) {
    return Serializer<FieldType>::write_data(field_value, ctx);
  }

  // Per Rust: collections always use fory_write(value, context, true, false, true)
  // Now C++ write() has matching signature with has_generics parameter
  constexpr bool is_collection_field = field_type_id == TypeId::LIST ||
                                       field_type_id == TypeId::SET ||
                                       field_type_id == TypeId::MAP;
  if constexpr (is_collection_field) {
    // Rust: fory_write(value, context, write_ref=true, write_type=false, has_generics=true)
    return Serializer<FieldType>::write(field_value, ctx, true, false, true);
  }

  // For other types, determine write_ref and write_type per Rust logic
  // write_ref: true for non-primitives (unless field_needs_ref overrides)
  bool write_ref = field_needs_ref || !is_primitive_field;

  // write_type: determined by field_need_write_type_info logic
  // Enums: false (per Rust util.rs:58-59)
  // Structs/EXT: true ONLY in compatible mode (per C++ read logic)
  // Others: false
  constexpr bool is_struct = field_type_id == TypeId::STRUCT ||
                             field_type_id == TypeId::COMPATIBLE_STRUCT ||
                             field_type_id == TypeId::NAMED_STRUCT ||
                             field_type_id == TypeId::NAMED_COMPATIBLE_STRUCT;
  constexpr bool is_ext = field_type_id == TypeId::EXT ||
                          field_type_id == TypeId::NAMED_EXT;
  constexpr bool is_polymorphic = field_type_id == TypeId::UNKNOWN;

  // Per C++ read logic: struct fields need type info only in compatible mode
  // Polymorphic types always need type info
  bool write_type = is_polymorphic || ((is_struct || is_ext) && ctx.is_compatible());

  return Serializer<FieldType>::write(field_value, ctx, write_ref, write_type);
}

/// Write struct fields recursively using index sequence (sorted order)
template <typename T, size_t... Indices>
Result<void, Error> write_struct_fields_impl(const T &obj, WriteContext &ctx,
                                             std::index_sequence<Indices...>,
                                             bool has_generics) {
  // Get field info from FORY_FIELD_INFO via ADL
  const auto field_info = ForyFieldInfo(obj);
  const auto field_ptrs = decltype(field_info)::Ptrs;

  using Helpers = CompileTimeFieldHelpers<T>;

  // Write each field in sorted order with early return on error
  Result<void, Error> result;
  ((result = dispatch_field_index<T>(Helpers::sorted_indices[Indices],
                                     [&](auto index_constant) {
                                       constexpr size_t index =
                                           decltype(index_constant)::value;
                                       return write_single_field<T, index>(
                                           obj, ctx, field_ptrs, has_generics);
                                     }),
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

  // In non-compatible mode, no type info for fields except for polymorphic
  // types (type_id == UNKNOWN), which always need type info. In compatible
  // mode, nested structs carry TypeMeta in the stream so that
  // `Serializer<T>::read` can dispatch to `read_compatible` with the correct
  // remote schema.
  constexpr bool field_needs_ref = requires_ref_metadata_v<FieldType>;
  constexpr bool is_struct_field = is_fory_serializable_v<FieldType>;
  constexpr bool is_polymorphic_field =
      Serializer<FieldType>::type_id == TypeId::UNKNOWN;
  bool read_type = is_polymorphic_field;

  // In compatible mode, nested struct fields always carry type metadata
  // (xtypeId + meta index). We must read this metadata so that
  // `Serializer<T>::read` can dispatch to `read_compatible` with the correct
  // remote TypeMeta instead of treating the bytes as part of the first field
  // value.
  if (!is_polymorphic_field && is_struct_field && ctx.is_compatible()) {
    read_type = true;
  }

  // Per xlang spec, all non-primitive fields have ref flags.
  // Primitive types: bool, int8-64, var_int32/64, sli_int64, float16/32/64
  // Non-primitives include: string, list, set, map, struct, enum, etc.
  constexpr TypeId field_type_id = Serializer<FieldType>::type_id;
  constexpr bool is_primitive_field = is_primitive_type_id(field_type_id);

  // Read ref flag if:
  // 1. Field requires ref metadata (nullable, optional, shared_ptr, etc.)
  // 2. Field is non-primitive
  bool read_ref = field_needs_ref || !is_primitive_field;

#ifdef FORY_DEBUG
  const auto debug_names = decltype(field_info)::Names;
  std::cerr << "[xlang][field] T=" << typeid(T).name() << ", index=" << Index
            << ", name=" << debug_names[Index]
            << ", field_needs_ref=" << field_needs_ref
            << ", read_ref=" << read_ref << ", read_type=" << read_type
            << ", reader_index=" << ctx.buffer().reader_index() << std::endl;
#endif

  FORY_ASSIGN_OR_RETURN(obj.*field_ptr,
                        Serializer<FieldType>::read(ctx, read_ref, read_type));
  return Result<void, Error>();
}

/// Helper to read a single field by index in compatible mode using
/// remote field metadata to decide reference flag presence.
template <size_t Index, typename T>
Result<void, Error>
read_single_field_by_index_compatible(T &obj, ReadContext &ctx,
                                      bool remote_ref_flag) {
  const auto field_info = ForyFieldInfo(obj);
  const auto field_ptrs = decltype(field_info)::Ptrs;
  const auto field_ptr = std::get<Index>(field_ptrs);
  using FieldType =
      typename meta::RemoveMemberPointerCVRefT<decltype(field_ptr)>;

  constexpr bool is_struct_field = is_fory_serializable_v<FieldType>;
  constexpr bool is_polymorphic_field =
      Serializer<FieldType>::type_id == TypeId::UNKNOWN;

  bool read_type = is_polymorphic_field;

  // In compatible mode, nested struct fields always carry type metadata
  // (xtypeId + meta index). We must read this metadata so that
  // `Serializer<T>::read` can dispatch to `read_compatible` with the correct
  // remote TypeMeta instead of treating the bytes as part of the first field
  // value.
  if (!is_polymorphic_field && is_struct_field && ctx.is_compatible()) {
    read_type = true;
  }

  // In compatible mode, trust the remote field metadata to tell us whether
  // a ref/null flag was written before the value payload. The remote_ref_flag
  // is determined by read_struct_fields_compatible() based on:
  // 1. Field nullability
  // 2. Whether field is non-primitive (per xlang spec, all non-primitives have ref flags)
  bool read_ref = remote_ref_flag;

#ifdef FORY_DEBUG
  const auto debug_names = decltype(field_info)::Names;
  std::cerr << "[xlang][field][compat] T=" << typeid(T).name()
            << ", index=" << Index << ", name=" << debug_names[Index]
            << ", remote_ref_flag=" << remote_ref_flag
            << ", read_ref=" << read_ref << ", read_type=" << read_type
            << ", reader_index=" << ctx.buffer().reader_index() << std::endl;
#endif

  FORY_ASSIGN_OR_RETURN(obj.*field_ptr,
                        Serializer<FieldType>::read(ctx, read_ref, read_type));
  return Result<void, Error>();
}

/// Read struct fields recursively using index sequence (sorted order - matches
/// write order)
template <typename T, size_t... Indices>
Result<void, Error> read_struct_fields_impl(T &obj, ReadContext &ctx,
                                            std::index_sequence<Indices...>) {
  using Helpers = CompileTimeFieldHelpers<T>;

  // Read each field in sorted order (same as write) with early return on error
  Result<void, Error> result;
  ((result = dispatch_field_index<T>(Helpers::sorted_indices[Indices],
                                     [&](auto index_constant) {
                                       constexpr size_t index =
                                           decltype(index_constant)::value;
                                       return read_single_field_by_index<index>(
                                           obj, ctx);
                                     }),
    result.ok()) &&
   ...);
  return result;
}

/// Read struct fields with schema evolution (compatible mode)
/// Reads fields in remote schema order, dispatching by field_id to local fields
template <typename T, size_t... Indices>
Result<void, Error>
read_struct_fields_compatible(T &obj, ReadContext &ctx,
                              const std::shared_ptr<TypeMeta> &remote_type_meta,
                              std::index_sequence<Indices...>) {

  using Helpers = CompileTimeFieldHelpers<T>;
  const auto &remote_fields = remote_type_meta->get_field_infos();

  // Iterate through remote fields in their serialization order
  for (size_t remote_idx = 0; remote_idx < remote_fields.size(); ++remote_idx) {
    const auto &remote_field = remote_fields[remote_idx];
    int16_t field_id = remote_field.field_id;

    // In compatible mode, whether a field carries a ref/null flag depends on:
    // 1. If the field is nullable
    // 2. If it's a non-primitive type (string, list, set, map, struct, etc.)
    //    Per xlang spec, only primitive types (bool, int8-64, var_int32/64,
    //    sli_int64, float16/32/64) don't have ref flags
    uint32_t type_id = remote_field.field_type.type_id;
    bool is_primitive = is_primitive_type_id(static_cast<TypeId>(type_id));

    // Read ref flag if: nullable, or non-primitive type
    bool read_ref_flag =
        remote_field.field_type.nullable || !is_primitive;

#ifdef FORY_DEBUG
    std::cerr << "[xlang][compat] remote_idx=" << remote_idx
              << ", field_id=" << field_id
              << ", name=" << remote_field.field_name
              << ", type_id=" << type_id
              << ", is_primitive=" << is_primitive
              << ", nullable=" << remote_field.field_type.nullable
              << ", read_ref_flag=" << read_ref_flag
              << ", reader_index=" << ctx.buffer().reader_index() << std::endl;
#endif

    if (field_id == -1) {
      // Field unknown locally — skip its value
      FORY_RETURN_NOT_OK(
          skip_field_value(ctx, remote_field.field_type, read_ref_flag));
      continue;
    }

    // Dispatch to the correct local field by field_id
    bool handled = false;
    Result<void, Error> result;

    detail::for_each_index(
        std::index_sequence<Indices...>{}, [&](auto index_constant) {
          constexpr size_t index = decltype(index_constant)::value;
          if (!handled && static_cast<int16_t>(index) == field_id) {
            handled = true;
            constexpr size_t original_index = Helpers::sorted_indices[index];
            result = read_single_field_by_index_compatible<original_index>(
                obj, ctx, read_ref_flag);
          }
        });

    if (!handled) {
      // Shouldn't happen if TypeMeta::assign_field_ids worked correctly
      FORY_RETURN_NOT_OK(
          skip_field_value(ctx, remote_field.field_type, read_ref_flag));
      continue;
    }

    FORY_RETURN_NOT_OK(result);
  }

  return Result<void, Error>();
}

} // namespace detail

/// Serializer for types registered with FORY_STRUCT
template <typename T>
struct Serializer<T, std::enable_if_t<is_fory_serializable_v<T>>> {
  static constexpr TypeId type_id = TypeId::STRUCT;

  /// Write type info only (type_id and meta index if applicable).
  /// This is used by collection serializers to write element type info.
  /// Matches Rust's struct_::write_type_info.
  static Result<void, Error> write_type_info(WriteContext &ctx) {
    auto type_info = ctx.type_resolver().template get_struct_type_info<T>();
    FORY_CHECK(type_info)
        << "Type metadata not initialized for requested struct";

    uint32_t type_tag = ctx.type_resolver().struct_type_tag<T>();
    ctx.write_varuint32(type_tag);

    // In compatible mode, always write meta index (matches Rust behavior)
    if (ctx.is_compatible() && type_info->type_meta) {
      FORY_TRY(meta_index, ctx.push_meta(std::type_index(typeid(T))));
      ctx.write_varuint32(static_cast<uint32_t>(meta_index));
    }
    return Result<void, Error>();
  }

  /// Read and validate type info.
  /// This consumes the type_id and meta index from the buffer.
  static Result<void, Error> read_type_info(ReadContext &ctx) {
    FORY_TRY(actual, ctx.read_typeinfo_type_id());
    if (!type_id_matches(actual, static_cast<uint32_t>(type_id))) {
      return Unexpected(
          Error::type_mismatch(actual, static_cast<uint32_t>(type_id)));
    }
    return Result<void, Error>();
  }

  static Result<void, Error> write(const T &obj, WriteContext &ctx,
                                   bool write_ref, bool write_type,
                                   bool has_generics = false) {
    write_not_null_ref_flag(ctx, write_ref);

    auto type_info = ctx.type_resolver().template get_struct_type_info<T>();
    FORY_CHECK(type_info)
        << "Type metadata not initialized for requested struct";

    if (write_type) {
      uint32_t type_tag = ctx.type_resolver().struct_type_tag<T>();
      // Use write_any_typeinfo to handle both type_id AND namespace/type_name
      // for NAMED_STRUCT, and meta_index for compatible mode
      FORY_RETURN_NOT_OK(
          ctx.write_any_typeinfo(type_tag, std::type_index(typeid(T))));
    }
    return write_data_generic(obj, ctx, has_generics);
  }

  static Result<void, Error> write_data(const T &obj, WriteContext &ctx) {
    if (ctx.check_struct_version()) {
      auto type_info = ctx.type_resolver().template get_struct_type_info<T>();
      FORY_CHECK(type_info && type_info->type_meta)
          << "Type metadata not initialized for requested struct";
      int32_t local_version =
          TypeMeta::compute_struct_version(*type_info->type_meta);
      ctx.buffer().WriteInt32(local_version);
    }

    using FieldDescriptor = decltype(ForyFieldInfo(std::declval<const T &>()));
    constexpr size_t field_count = FieldDescriptor::Size;
    return detail::write_struct_fields_impl(
        obj, ctx, std::make_index_sequence<field_count>{}, false);
  }

  static Result<void, Error> write_data_generic(const T &obj, WriteContext &ctx,
                                                bool has_generics) {
    if (ctx.check_struct_version()) {
      auto type_info = ctx.type_resolver().template get_struct_type_info<T>();
      FORY_CHECK(type_info && type_info->type_meta)
          << "Type metadata not initialized for requested struct";
      int32_t local_version =
          TypeMeta::compute_struct_version(*type_info->type_meta);
      ctx.buffer().WriteInt32(local_version);
    }

    using FieldDescriptor = decltype(ForyFieldInfo(std::declval<const T &>()));
    constexpr size_t field_count = FieldDescriptor::Size;
    return detail::write_struct_fields_impl(
        obj, ctx, std::make_index_sequence<field_count>{}, has_generics);
  }

  static Result<T, Error> read(ReadContext &ctx, bool read_ref,
                               bool read_type) {
    // Handle reference metadata
    int8_t ref_flag;
    if (read_ref) {
      FORY_TRY(flag, ctx.read_int8());
      ref_flag = flag;
#ifdef FORY_DEBUG
      std::cerr << "[xlang][struct] T=" << typeid(T).name()
                << ", read_ref_flag=" << static_cast<int>(ref_flag)
                << ", reader_index=" << ctx.buffer().reader_index()
                << std::endl;
#endif
    } else {
      ref_flag = static_cast<int8_t>(RefFlag::NotNullValue);
    }

    int8_t not_null_value_flag = static_cast<int8_t>(RefFlag::NotNullValue);
    int8_t ref_value_flag = static_cast<int8_t>(RefFlag::RefValue);
    int8_t null_flag = static_cast<int8_t>(RefFlag::Null);

    if (ref_flag == not_null_value_flag || ref_flag == ref_value_flag) {
      // In compatible mode: use meta sharing (matches Rust behavior)
      if (ctx.is_compatible()) {
        // In compatible mode: always use remote TypeMeta for schema evolution
        if (read_type) {
          // Read type_tag
          FORY_TRY(type_tag, ctx.read_varuint32());

          // Check LOCAL type to decide if we should read meta_index (matches
          // Rust logic)
          auto local_type_info =
              ctx.type_resolver().template get_struct_type_info<T>();
          uint32_t local_type_tag =
              ctx.type_resolver().struct_type_tag(*local_type_info);
          uint8_t local_type_id_low = local_type_tag & 0xff;

          if (local_type_id_low ==
                  static_cast<uint8_t>(TypeId::COMPATIBLE_STRUCT) ||
              local_type_id_low ==
                  static_cast<uint8_t>(TypeId::NAMED_COMPATIBLE_STRUCT)) {
            // Use meta sharing: read varint index and get TypeInfo from
            // meta_reader
            FORY_TRY(meta_index, ctx.read_varuint32());
            FORY_TRY(remote_type_info_ptr,
                     ctx.get_type_info_by_index(meta_index));
            auto remote_type_info =
                std::static_pointer_cast<TypeInfo>(remote_type_info_ptr);

            return read_compatible(ctx, remote_type_info);
          } else {
            // Local type is not compatible struct - verify type match and read
            // data
            if (type_tag != local_type_tag) {
              return Unexpected(Error::type_mismatch(type_tag, local_type_tag));
            }
            return read_data(ctx);
          }
        } else {
          // read_type=false in compatible mode: same version, use sorted order
          // (fast path)
          return read_data(ctx);
        }
      } else {
        // Non-compatible mode: read type info if requested, then read data.
        //
        // For xlang, we delegate type-info parsing to ReadContext so that
        // named structs/ext/enums consume their namespace/type-name
        // metadata exactly as Java/Rust do. This keeps the reader
        // position aligned with the subsequent class-version hash and
        // payload, and also validates that the concrete type id matches
        // the expected static type.
        if (read_type) {
          auto local_type_info =
              ctx.type_resolver().template get_struct_type_info<T>();
          FORY_CHECK(local_type_info && local_type_info->type_meta)
              << "Type metadata not initialized for requested struct";
          uint32_t expected_tag =
              ctx.type_resolver().struct_type_tag(*local_type_info);

          // xlang: read full type info (id + any named metadata)
          auto type_info_result = ctx.read_any_typeinfo();
          if (!type_info_result.ok()) {
            return Unexpected(type_info_result.error());
          }
          auto remote_info = type_info_result.value();
          uint32_t remote_tag = remote_info ? remote_info->type_id : 0u;
          if (remote_tag != expected_tag) {
            return Unexpected(Error::type_mismatch(remote_tag, expected_tag));
          }
        }
        return read_data(ctx);
      }
    } else if (ref_flag == null_flag) {
      // Null value
      if constexpr (std::is_default_constructible_v<T>) {
        return T();
      } else {
        return Unexpected(Error::invalid_data(
            "Null value encountered for non-default-constructible struct"));
      }
    } else {
      return Unexpected(Error::invalid_ref("Unknown ref flag, value: " +
                                           std::to_string(ref_flag)));
    }
  }

  static Result<T, Error>
  read_compatible(ReadContext &ctx,
                  std::shared_ptr<TypeInfo> remote_type_info) {
    FORY_RETURN_NOT_OK(ctx.increase_depth());
    DepthGuard depth_guard(ctx);

    // Read and verify struct version if enabled (matches write_data behavior)
    if (ctx.check_struct_version()) {
      FORY_TRY(read_version, ctx.buffer().ReadInt32());
      auto local_type_info =
          ctx.type_resolver().template get_struct_type_info<T>();
      FORY_CHECK(local_type_info && local_type_info->type_meta)
          << "Type metadata not initialized for requested struct";
      int32_t local_version =
          TypeMeta::compute_struct_version(*local_type_info->type_meta);
      FORY_RETURN_NOT_OK(TypeMeta::check_struct_version(
          read_version, local_version, local_type_info->type_name));
    }

    T obj{};
    using FieldDescriptor = decltype(ForyFieldInfo(std::declval<const T &>()));
    constexpr size_t field_count = FieldDescriptor::Size;

    // remote_type_info is from the stream, with field_ids already assigned
    if (!remote_type_info || !remote_type_info->type_meta) {
      return Unexpected(
          Error::type_error("Remote type metadata not available"));
    }

    // Use remote TypeMeta for schema evolution - field IDs already assigned
    FORY_RETURN_NOT_OK(detail::read_struct_fields_compatible(
        obj, ctx, remote_type_info->type_meta,
        std::make_index_sequence<field_count>{}));

    return obj;
  }

  static Result<T, Error> read_data(ReadContext &ctx) {
    FORY_RETURN_NOT_OK(ctx.increase_depth());
    DepthGuard depth_guard(ctx);

    if (ctx.check_struct_version()) {
      FORY_TRY(read_version, ctx.buffer().ReadInt32());
      auto local_type_info =
          ctx.type_resolver().template get_struct_type_info<T>();
      FORY_CHECK(local_type_info && local_type_info->type_meta)
          << "Type metadata not initialized for requested struct";
      int32_t local_version =
          TypeMeta::compute_struct_version(*local_type_info->type_meta);
      FORY_RETURN_NOT_OK(TypeMeta::check_struct_version(
          read_version, local_version, local_type_info->type_name));
    }

    T obj{};
    using FieldDescriptor = decltype(ForyFieldInfo(std::declval<const T &>()));
    constexpr size_t field_count = FieldDescriptor::Size;
    FORY_RETURN_NOT_OK(detail::read_struct_fields_impl(
        obj, ctx, std::make_index_sequence<field_count>{}));

    return obj;
  }

  // Optimized read when type info already known (for polymorphic collections)
  // This method is critical for the optimization described in xlang spec
  // section 5.4.4 When deserializing List<Base> where all elements are same
  // concrete type, we read type info once and pass it to all element
  // deserializers
  static Result<T, Error> read_with_type_info(ReadContext &ctx, bool read_ref,
                                              const TypeInfo &type_info) {
    // Note: When called from polymorphic shared_ptr, the shared_ptr has already
    // consumed the ref flag, so we should not read it again here. The read_ref
    // parameter is just for protocol compatibility but should not cause us to
    // read another ref flag.

    // In compatible mode with type info provided, use schema evolution path
    if (ctx.is_compatible() && type_info.type_meta) {
      auto remote_type_info = std::make_shared<TypeInfo>(type_info);
      return read_compatible(ctx, remote_type_info);
    }

    // Otherwise use normal read path
    return read_data(ctx);
  }
};

} // namespace serialization
} // namespace fory
