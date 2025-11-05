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

#include <algorithm>
#include <any>
#include <cstdint>
#include <functional>
#include <memory>
#include <mutex>
#include <numeric>
#include <optional>
#include <string>
#include <string_view>
#include <tuple>
#include <type_traits>
#include <typeindex>
#include <typeinfo>
#include <unordered_map>
#include <utility>
#include <vector>

#include "fory/meta/field_info.h"
#include "fory/meta/type_traits.h"
#include "fory/serialization/config.h"
#include "fory/serialization/serializer.h"
#include "fory/serialization/serializer_traits.h"
#include "fory/type/type.h"
#include "fory/util/buffer.h"
#include "fory/util/error.h"
#include "fory/util/logging.h"
#include "fory/util/result.h"

namespace fory {
namespace serialization {

// Forward declarations
class TypeResolver;
class WriteContext;
class ReadContext;

// ============================================================================
// FieldType - Represents type information for a field
// ============================================================================

/// Represents type information including type ID, nullability, and generics
class FieldType {
public:
  uint32_t type_id;
  bool nullable;
  std::vector<FieldType> generics;

  FieldType() : type_id(0), nullable(false) {}

  FieldType(uint32_t tid, bool null, std::vector<FieldType> gens = {})
      : type_id(tid), nullable(null), generics(std::move(gens)) {}

  /// Write field type to buffer
  /// @param buffer Target buffer
  /// @param write_flag Whether to write nullability flag (for nested types)
  /// @param nullable_val Nullability to write if write_flag is true
  Result<void, Error> write_to(Buffer &buffer, bool write_flag,
                               bool nullable_val) const;

  /// Read field type from buffer
  /// @param buffer Source buffer
  /// @param read_flag Whether to read nullability flag (for nested types)
  /// @param nullable_val Nullability if read_flag is false
  static Result<FieldType, Error> read_from(Buffer &buffer, bool read_flag,
                                            bool nullable_val);

  bool operator==(const FieldType &other) const {
    return type_id == other.type_id && nullable == other.nullable &&
           generics == other.generics;
  }

  bool operator!=(const FieldType &other) const { return !(*this == other); }
};

// ============================================================================
// FieldInfo - Field metadata (name, type, id)
// ============================================================================

/// Field information including name, type, and assigned field ID
class FieldInfo {
public:
  int16_t field_id;       // Assigned during deserialization (-1 = skip)
  std::string field_name; // Field name
  FieldType field_type;   // Field type information

  FieldInfo() : field_id(-1) {}

  FieldInfo(std::string name, FieldType type)
      : field_id(-1), field_name(std::move(name)), field_type(std::move(type)) {
  }

  /// Write field info to buffer (for serialization)
  Result<std::vector<uint8_t>, Error> to_bytes() const;

  /// Read field info from buffer (for deserialization)
  static Result<FieldInfo, Error> from_bytes(Buffer &buffer);

  bool operator==(const FieldInfo &other) const {
    return field_name == other.field_name && field_type == other.field_type;
  }
};

// ============================================================================
// TypeMeta - Complete type metadata (for schema evolution)
// ============================================================================

constexpr size_t MAX_PARSED_NUM_TYPE_DEFS = 8192;

/// Type metadata containing all field information
/// Used for schema evolution to compare remote and local type schemas
class TypeMeta {
public:
  int64_t hash;                       // Type hash for fast comparison
  uint32_t type_id;                   // Type ID (for non-named registration)
  std::string namespace_str;          // Namespace (for named registration)
  std::string type_name;              // Type name (for named registration)
  bool register_by_name;              // Whether registered by name
  std::vector<FieldInfo> field_infos; // Field information

  TypeMeta() : hash(0), type_id(0), register_by_name(false) {}

  /// Create TypeMeta from field information
  static TypeMeta from_fields(uint32_t tid, const std::string &ns,
                              const std::string &name, bool by_name,
                              std::vector<FieldInfo> fields);

  /// Write type meta to buffer (for serialization)
  Result<std::vector<uint8_t>, Error> to_bytes() const;

  /// Read type meta from buffer (for deserialization)
  /// @param buffer Source buffer
  /// @param local_type_info Local type information (for field ID assignment)
  static Result<std::shared_ptr<TypeMeta>, Error>
  from_bytes(Buffer &buffer, const TypeMeta *local_type_info);

  /// Skip type meta in buffer without parsing
  static Result<void, Error> skip_bytes(Buffer &buffer, int64_t header);

  /// Check struct version consistency
  static Result<void, Error> check_struct_version(int32_t read_version,
                                                  int32_t local_version,
                                                  const std::string &type_name);

  /// Get sorted field infos (sorted according to xlang spec)
  static std::vector<FieldInfo> sort_field_infos(std::vector<FieldInfo> fields);

  /// Assign field IDs by comparing with local type
  /// This is the key function for schema evolution!
  static void assign_field_ids(const TypeMeta *local_type,
                               std::vector<FieldInfo> &remote_fields);

  const std::vector<FieldInfo> &get_field_infos() const { return field_infos; }
  int64_t get_hash() const { return hash; }
  uint32_t get_type_id() const { return type_id; }
  const std::string &get_type_name() const { return type_name; }
  const std::string &get_namespace() const { return namespace_str; }

private:
  /// Compute hash from type meta bytes
  static int64_t compute_hash(const std::vector<uint8_t> &meta_bytes);
};

// ============================================================================
// Helper utilities for building field metadata
// ============================================================================

namespace detail {

inline uint32_t to_type_id(TypeId id) { return static_cast<uint32_t>(id); }

template <typename T> struct is_shared_ptr : std::false_type {};
template <typename T>
struct is_shared_ptr<std::shared_ptr<T>> : std::true_type {};
template <typename T>
inline constexpr bool is_shared_ptr_v = is_shared_ptr<T>::value;

template <typename T> struct is_unique_ptr : std::false_type {};
template <typename T, typename D>
struct is_unique_ptr<std::unique_ptr<T, D>> : std::true_type {};
template <typename T>
inline constexpr bool is_unique_ptr_v = is_unique_ptr<T>::value;

template <typename T> struct FieldTypeBuilder;

template <typename T> FieldType build_field_type(bool nullable = false) {
  return FieldTypeBuilder<T>::build(nullable);
}

template <typename T> struct FieldTypeBuilder {
  static FieldType build(bool nullable) {
    if constexpr (std::is_same_v<T, bool>) {
      return FieldType(to_type_id(TypeId::BOOL), nullable);
    } else if constexpr (std::is_same_v<T, int8_t>) {
      return FieldType(to_type_id(TypeId::INT8), nullable);
    } else if constexpr (std::is_same_v<T, int16_t>) {
      return FieldType(to_type_id(TypeId::INT16), nullable);
    } else if constexpr (std::is_same_v<T, int32_t>) {
      return FieldType(to_type_id(TypeId::INT32), nullable);
    } else if constexpr (std::is_same_v<T, int64_t>) {
      return FieldType(to_type_id(TypeId::INT64), nullable);
    } else if constexpr (std::is_same_v<T, float>) {
      return FieldType(to_type_id(TypeId::FLOAT32), nullable);
    } else if constexpr (std::is_same_v<T, double>) {
      return FieldType(to_type_id(TypeId::FLOAT64), nullable);
    } else if constexpr (std::is_same_v<T, std::string>) {
      return FieldType(to_type_id(TypeId::STRING), nullable);
    } else if constexpr (std::is_same_v<T, std::string_view>) {
      return FieldType(to_type_id(TypeId::STRING), nullable);
    } else if constexpr (is_optional_v<T>) {
      using Inner = typename T::value_type;
      FieldType inner = FieldTypeBuilder<Inner>::build(true);
      inner.nullable = true;
      return inner;
    } else if constexpr (is_shared_ptr_v<T>) {
      using Inner = typename T::element_type;
      FieldType inner = FieldTypeBuilder<Inner>::build(true);
      inner.nullable = true;
      return inner;
    } else if constexpr (is_unique_ptr_v<T>) {
      using Inner = typename T::element_type;
      FieldType inner = FieldTypeBuilder<Inner>::build(true);
      inner.nullable = true;
      return inner;
    } else if constexpr (is_vector_v<T>) {
      FieldType elem = FieldTypeBuilder<element_type_t<T>>::build(false);
      FieldType ft(to_type_id(TypeId::LIST), nullable);
      ft.generics.push_back(std::move(elem));
      return ft;
    } else if constexpr (is_set_like_v<T>) {
      FieldType elem = FieldTypeBuilder<element_type_t<T>>::build(false);
      FieldType ft(to_type_id(TypeId::SET), nullable);
      ft.generics.push_back(std::move(elem));
      return ft;
    } else if constexpr (is_map_like_v<T>) {
      FieldType key = FieldTypeBuilder<key_type_t<T>>::build(false);
      FieldType value = FieldTypeBuilder<mapped_type_t<T>>::build(false);
      FieldType ft(to_type_id(TypeId::MAP), nullable);
      ft.generics.push_back(std::move(key));
      ft.generics.push_back(std::move(value));
      return ft;
    } else {
      return FieldType(to_type_id(TypeId::STRUCT), nullable);
    }
  }
};

template <typename T, size_t Index> struct FieldInfoBuilder {
  static FieldInfo build() {
    const auto meta = ForyFieldInfo(T{});
    const auto field_names = decltype(meta)::Names;
    const auto field_ptrs = decltype(meta)::Ptrs;

    std::string field_name(field_names[Index]);
    const auto field_ptr = std::get<Index>(field_ptrs);
    using RawFieldType =
        typename meta::RemoveMemberPointerCVRefT<decltype(field_ptr)>;
    using ActualFieldType =
        std::remove_cv_t<std::remove_reference_t<RawFieldType>>;

    FieldType field_type = FieldTypeBuilder<ActualFieldType>::build(false);
    return FieldInfo(std::move(field_name), std::move(field_type));
  }
};

template <typename T, size_t... Indices>
std::vector<FieldInfo> build_field_infos(std::index_sequence<Indices...>) {
  std::vector<FieldInfo> fields;
  fields.reserve(sizeof...(Indices));
  (fields.push_back(FieldInfoBuilder<T, Indices>::build()), ...);
  return fields;
}

} // namespace detail

// ============================================================================
// Forward declarations for TypeResolver
// ============================================================================

class TypeResolver;

// ============================================================================
// Harness - Function pointers for serialization/deserialization
// ============================================================================

struct Harness {
  using WriteFn = Result<void, Error> (*)(const std::any &value,
                                          WriteContext &ctx,
                                          bool write_ref_info,
                                          bool write_type_info,
                                          bool has_generics);
  using ReadFn = Result<std::any, Error> (*)(ReadContext &ctx,
                                             bool read_ref_info,
                                             bool read_type_info);
  using WriteDataFn = Result<void, Error> (*)(const std::any &value,
                                              WriteContext &ctx,
                                              bool has_generics);
  using ReadDataFn = Result<std::any, Error> (*)(ReadContext &ctx);
  using SortedFieldInfosFn =
      Result<std::vector<FieldInfo>, Error> (*)(TypeResolver &);

  Harness() = default;
  Harness(WriteFn write, ReadFn read, WriteDataFn write_data,
          ReadDataFn read_data, SortedFieldInfosFn sorted_fields)
      : write_fn(write), read_fn(read), write_data_fn(write_data),
        read_data_fn(read_data), sorted_field_infos_fn(sorted_fields) {}

  bool valid() const {
    return write_fn != nullptr && read_fn != nullptr &&
           write_data_fn != nullptr && read_data_fn != nullptr &&
           sorted_field_infos_fn != nullptr;
  }

  WriteFn write_fn = nullptr;
  ReadFn read_fn = nullptr;
  WriteDataFn write_data_fn = nullptr;
  ReadDataFn read_data_fn = nullptr;
  SortedFieldInfosFn sorted_field_infos_fn = nullptr;
};

// ============================================================================
// TypeInfo - Type metadata and serialization information
// ============================================================================

struct TypeInfo {
  uint32_t type_id = 0;
  std::string namespace_name;
  std::string type_name;
  bool register_by_name = false;
  bool is_external = false;
  std::shared_ptr<TypeMeta> type_meta;
  std::vector<size_t> sorted_indices;
  std::unordered_map<std::string, size_t> name_to_index;
  std::vector<uint8_t> type_def;
  Harness harness;
};

// ============================================================================
// TypeResolver - central registry for type metadata and configuration
// ============================================================================

class TypeResolver {
public:

  TypeResolver();
  TypeResolver(const TypeResolver &) = delete;
  TypeResolver &operator=(const TypeResolver &) = delete;

  void apply_config(const Config &config);

  bool compatible() const { return compatible_; }

  template <typename T> const TypeMeta &struct_meta();
  template <typename T> TypeMeta clone_struct_meta();
  template <typename T> const std::vector<size_t> &sorted_indices();
  template <typename T>
  const std::unordered_map<std::string, size_t> &field_name_to_index();

  template <typename T> std::shared_ptr<TypeInfo> get_struct_type_info();

  uint32_t struct_type_tag(const TypeInfo &info) const;

  template <typename T> uint32_t struct_type_tag();

  template <typename T> Result<void, Error> register_by_id(uint32_t type_id);

  template <typename T>
  Result<void, Error> register_by_name(const std::string &ns,
                                       const std::string &type_name);

  template <typename T>
  Result<void, Error> register_ext_type_by_id(uint32_t type_id);

  template <typename T>
  Result<void, Error> register_ext_type_by_name(const std::string &ns,
                                                const std::string &type_name);

  /// Get type info by type ID (for non-namespaced types)
  std::shared_ptr<TypeInfo> get_type_info_by_id(uint32_t type_id) const;

  /// Get type info by namespace and type name (for namespaced types)
  std::shared_ptr<TypeInfo> get_type_info_by_name(const std::string &ns,
                                                   const std::string &type_name) const;

  /// Read type information dynamically from ReadContext based on type ID.
  ///
  /// This method handles reading type info for various type categories:
  /// - COMPATIBLE_STRUCT/NAMED_COMPATIBLE_STRUCT: reads meta index
  /// - NAMED_ENUM/NAMED_STRUCT/NAMED_EXT: reads namespace and type name (if not sharing meta)
  /// - Other types: looks up by type ID
  ///
  /// @return TypeInfo pointer if found, error otherwise
  Result<std::shared_ptr<TypeInfo>, Error> read_any_typeinfo(ReadContext &ctx);
  
  /// Read type info from stream with explicit local TypeMeta for field_id assignment
  Result<std::shared_ptr<TypeInfo>, Error> read_any_typeinfo(ReadContext &ctx, const TypeMeta *local_type_meta);

  // ============================================================================
  // Meta Sharing API - For compatible mode serialization
  // ============================================================================

  /// Push a TypeId's TypeMeta into the write collection.
  /// Returns the index for writing as varint.
  /// (MetaWriterResolver::push)
  Result<size_t, Error> meta_push(const std::type_index &type_id);

  /// Write all collected TypeMetas to buffer.
  /// Format: varuint32 count + raw TypeMeta bytes
  /// (MetaWriterResolver::to_bytes)
  void meta_write_to_buffer(Buffer &buffer) const;

  /// Check if any TypeMetas were collected.
  /// (MetaWriterResolver::empty)
  bool meta_empty() const;

  /// Reset write meta state for reuse.
  /// (MetaWriterResolver::reset)
  void meta_reset_writer();

  /// Get TypeInfo by meta index.
  /// (MetaReaderResolver::get)
  Result<std::shared_ptr<TypeInfo>, Error> meta_get_by_index(size_t index) const;

  /// Load all TypeMetas from buffer at start of deserialization.
  /// (MetaReaderResolver::load)
  Result<size_t, Error> meta_load(Buffer &buffer);

  /// Reset read meta state for reuse.
  /// (MetaReaderResolver::reset)
  void meta_reset_reader();

private:
  template <typename T> std::shared_ptr<TypeInfo> ensure_type_info();

  template <typename T>
  static Result<std::shared_ptr<TypeInfo>, Error>
  build_struct_type_info(uint32_t type_id, std::string ns,
                         std::string type_name, bool register_by_name);

  template <typename T>
  static Result<std::shared_ptr<TypeInfo>, Error>
  build_ext_type_info(uint32_t type_id, std::string ns, std::string type_name,
                      bool register_by_name);

  template <typename T> static Harness make_struct_harness();

  template <typename T> static Harness make_serializer_harness();

  template <typename T>
  static Result<void, Error>
  harness_write_adapter(const std::any &value, WriteContext &ctx,
                        bool write_ref_info, bool write_type_info,
                        bool has_generics);

  template <typename T>
  static Result<std::any, Error> harness_read_adapter(ReadContext &ctx,
                                                      bool read_ref_info,
                                                      bool read_type_info);

  template <typename T>
  static Result<void, Error> harness_write_data_adapter(const std::any &value,
                                                        WriteContext &ctx,
                                                        bool has_generics);

  template <typename T>
  static Result<std::any, Error> harness_read_data_adapter(ReadContext &ctx);

  template <typename T>
  static Result<std::vector<FieldInfo>, Error>
  harness_struct_sorted_fields(TypeResolver &resolver);

  template <typename T>
  static Result<std::vector<FieldInfo>, Error>
  harness_empty_sorted_fields(TypeResolver &resolver);

  template <typename T> static const T *any_to_pointer(const std::any &value);

  template <typename T> static std::any make_any_value(T &&value);

  static std::string make_name_key(const std::string &ns,
                                   const std::string &name);

  Result<void, Error> register_type_internal(const std::type_index &type_index,
                                             std::shared_ptr<TypeInfo> info);

  bool compatible_;
  bool xlang_;
  bool check_struct_version_;
  bool track_ref_;

  mutable std::mutex struct_mutex_;
  mutable std::unordered_map<std::type_index, std::shared_ptr<TypeInfo>>
      type_info_cache_;
  mutable std::unordered_map<uint32_t, std::shared_ptr<TypeInfo>>
      type_info_by_id_;
  mutable std::unordered_map<std::string, std::shared_ptr<TypeInfo>>
      type_info_by_name_;

  // Meta sharing state (was MetaWriterResolver)
  std::vector<std::vector<uint8_t>> write_type_defs_;
  std::unordered_map<std::type_index, size_t> write_type_id_index_map_;

  // Meta sharing state (was MetaReaderResolver)
  std::vector<std::shared_ptr<TypeInfo>> reading_type_infos_;
  std::unordered_map<int64_t, std::shared_ptr<TypeInfo>> parsed_type_infos_;
};

// Alias for backward compatibility (already defined above as top-level)
// using TypeInfo = TypeInfo;

// ============================================================================
// Inline implementations
// ============================================================================

inline TypeResolver::TypeResolver()
    : compatible_(false), xlang_(false), check_struct_version_(true),
      track_ref_(true) {}

inline void TypeResolver::apply_config(const Config &config) {
  compatible_ = config.compatible;
  xlang_ = config.xlang;
  check_struct_version_ = config.check_struct_version;
  track_ref_ = config.track_ref;
}

template <typename T> const TypeMeta &TypeResolver::struct_meta() {
  auto info = ensure_type_info<T>();
  FORY_CHECK(info && info->type_meta)
      << "Type metadata not initialized for requested struct";
  return *info->type_meta;
}

template <typename T> TypeMeta TypeResolver::clone_struct_meta() {
  auto info = ensure_type_info<T>();
  FORY_CHECK(info && info->type_meta)
      << "Type metadata not initialized for requested struct";
  return *info->type_meta;
}

template <typename T>
const std::vector<size_t> &TypeResolver::sorted_indices() {
  return ensure_type_info<T>()->sorted_indices;
}

template <typename T>
const std::unordered_map<std::string, size_t> &
TypeResolver::field_name_to_index() {
  return ensure_type_info<T>()->name_to_index;
}

template <typename T>
std::shared_ptr<TypeInfo>
TypeResolver::get_struct_type_info() {
  static_assert(is_fory_serializable_v<T>,
                "get_struct_type_info requires FORY_STRUCT types");
  return ensure_type_info<T>();
}

inline uint32_t TypeResolver::struct_type_tag(const TypeInfo &info) const {
  if (info.register_by_name) {
    return compatible_ ? static_cast<uint32_t>(TypeId::NAMED_COMPATIBLE_STRUCT)
                       : static_cast<uint32_t>(TypeId::NAMED_STRUCT);
  }
  if (compatible_) {
    return (info.type_id << 8) |
           static_cast<uint32_t>(TypeId::COMPATIBLE_STRUCT);
  }
  return (info.type_id << 8) | static_cast<uint32_t>(TypeId::STRUCT);
}

template <typename T> uint32_t TypeResolver::struct_type_tag() {
  auto info = get_struct_type_info<T>();
  FORY_CHECK(info && info->type_meta)
      << "Type metadata not initialized for requested struct";
  return struct_type_tag(*info);
}

template <typename T>
std::shared_ptr<TypeInfo>
TypeResolver::ensure_type_info() {
  const std::type_index key(typeid(T));
  {
    std::lock_guard<std::mutex> lock(struct_mutex_);
    auto it = type_info_cache_.find(key);
    if (it != type_info_cache_.end()) {
      return it->second;
    }
  }

  uint32_t default_type_id =
      compatible_ ? static_cast<uint32_t>(TypeId::COMPATIBLE_STRUCT)
                  : static_cast<uint32_t>(TypeId::STRUCT);
  auto info_result = build_struct_type_info<T>(default_type_id, "", "", true);
  if (!info_result.ok()) {
    FORY_CHECK(info_result.ok()) << info_result.error().message();
  }
  auto entry = std::move(info_result).value();

  std::lock_guard<std::mutex> lock(struct_mutex_);
  auto [it, inserted] = type_info_cache_.emplace(key, entry);
  if (!inserted) {
    return it->second;
  }
  return entry;
}

template <typename T>
Result<void, Error> TypeResolver::register_by_id(uint32_t type_id) {
  static_assert(is_fory_serializable_v<T>,
                "register_by_id requires a type declared with FORY_STRUCT");
  if (type_id == 0) {
    return Unexpected(
        Error::invalid("type_id must be non-zero for register_by_id"));
  }

  auto info_result = build_struct_type_info<T>(type_id, "", "", false);
  if (!info_result.ok()) {
    return Unexpected(std::move(info_result).error());
  }
  auto info = std::move(info_result).value();
  if (!info->harness.valid()) {
    return Unexpected(
        Error::invalid("Harness for registered type is incomplete"));
  }
  return register_type_internal(std::type_index(typeid(T)), std::move(info));
}

template <typename T>
Result<void, Error>
TypeResolver::register_by_name(const std::string &ns,
                               const std::string &type_name) {
  static_assert(is_fory_serializable_v<T>,
                "register_by_name requires a type declared with FORY_STRUCT");
  if (type_name.empty()) {
    return Unexpected(
        Error::invalid("type_name must be non-empty for register_by_name"));
  }
  uint32_t actual_type_id =
      compatible_ ? static_cast<uint32_t>(TypeId::NAMED_COMPATIBLE_STRUCT)
                  : static_cast<uint32_t>(TypeId::NAMED_STRUCT);

  auto info_result =
      build_struct_type_info<T>(actual_type_id, ns, type_name, true);
  if (!info_result.ok()) {
    return Unexpected(std::move(info_result).error());
  }
  auto info = std::move(info_result).value();
  if (!info->harness.valid()) {
    return Unexpected(
        Error::invalid("Harness for registered type is incomplete"));
  }
  return register_type_internal(std::type_index(typeid(T)), std::move(info));
}

template <typename T>
Result<void, Error> TypeResolver::register_ext_type_by_id(uint32_t type_id) {
  if (type_id == 0) {
    return Unexpected(
        Error::invalid("type_id must be non-zero for register_ext_type_by_id"));
  }
  auto info_result = build_ext_type_info<T>(type_id, "", "", false);
  if (!info_result.ok()) {
    return Unexpected(std::move(info_result).error());
  }
  auto info = std::move(info_result).value();
  return register_type_internal(std::type_index(typeid(T)), std::move(info));
}

template <typename T>
Result<void, Error>
TypeResolver::register_ext_type_by_name(const std::string &ns,
                                        const std::string &type_name) {
  if (type_name.empty()) {
    return Unexpected(Error::invalid(
        "type_name must be non-empty for register_ext_type_by_name"));
  }
  uint32_t actual_type_id = static_cast<uint32_t>(TypeId::NAMED_EXT);
  auto info_result =
      build_ext_type_info<T>(actual_type_id, ns, type_name, true);
  if (!info_result.ok()) {
    return Unexpected(std::move(info_result).error());
  }
  auto info = std::move(info_result).value();
  return register_type_internal(std::type_index(typeid(T)), std::move(info));
}

template <typename T>
Result<std::shared_ptr<TypeInfo>, Error>
TypeResolver::build_struct_type_info(uint32_t type_id, std::string ns,
                                     std::string type_name,
                                     bool register_by_name) {
  static_assert(is_fory_serializable_v<T>,
                "build_struct_type_info requires FORY_STRUCT types");

  if (type_id == 0) {
    type_id = static_cast<uint32_t>(TypeId::STRUCT);
  }

  auto entry = std::make_shared<TypeInfo>();
  entry->type_id = type_id;
  entry->namespace_name = std::move(ns);
  entry->register_by_name = register_by_name;
  entry->is_external = false;

  const auto meta_desc = ForyFieldInfo(T{});
  constexpr size_t field_count = decltype(meta_desc)::Size;
  const auto field_names = decltype(meta_desc)::Names;

  std::string resolved_name = type_name;
  if (resolved_name.empty()) {
    resolved_name = std::string(decltype(meta_desc)::Name);
  }
  if (register_by_name && resolved_name.empty()) {
    return Unexpected(Error::invalid(
        "Resolved type name must be non-empty when register_by_name is true"));
  }
  entry->type_name = std::move(resolved_name);

  entry->name_to_index.reserve(field_count);
  for (size_t i = 0; i < field_count; ++i) {
    entry->name_to_index.emplace(std::string(field_names[i]), i);
  }

  auto field_infos =
      detail::build_field_infos<T>(std::make_index_sequence<field_count>{});
  auto sorted_fields = TypeMeta::sort_field_infos(std::move(field_infos));

  entry->sorted_indices.clear();
  entry->sorted_indices.reserve(field_count);
  for (const auto &sorted_field : sorted_fields) {
    auto it = entry->name_to_index.find(sorted_field.field_name);
    FORY_CHECK(it != entry->name_to_index.end())
        << "Sorted field name '" << sorted_field.field_name
        << "' not found in original struct definition";
    entry->sorted_indices.push_back(it->second);
  }

  TypeMeta meta =
      TypeMeta::from_fields(type_id, entry->namespace_name, entry->type_name,
                            register_by_name, std::move(sorted_fields));

  auto type_def_result = meta.to_bytes();
  if (!type_def_result.ok()) {
    return Unexpected(std::move(type_def_result).error());
  }
  entry->type_def = std::move(type_def_result).value();

  Buffer buffer(entry->type_def.data(),
                static_cast<uint32_t>(entry->type_def.size()), false);
  buffer.WriterIndex(static_cast<uint32_t>(entry->type_def.size()));
  auto parsed_meta_result = TypeMeta::from_bytes(buffer, nullptr);
  if (!parsed_meta_result.ok()) {
    return Unexpected(std::move(parsed_meta_result).error());
  }
  entry->type_meta = std::move(parsed_meta_result).value();
  entry->harness = make_struct_harness<T>();

  return entry;
}

template <typename T>
Result<std::shared_ptr<TypeInfo>, Error>
TypeResolver::build_ext_type_info(uint32_t type_id, std::string ns,
                                  std::string type_name,
                                  bool register_by_name) {
  auto entry = std::make_shared<TypeInfo>();
  entry->type_id = type_id;
  entry->namespace_name = std::move(ns);
  entry->type_name = std::move(type_name);
  entry->register_by_name = register_by_name;
  entry->is_external = true;
  entry->harness = make_serializer_harness<T>();

  if (!entry->harness.valid()) {
    return Unexpected(
        Error::invalid("Harness for external type is incomplete"));
  }
  return entry;
}

template <typename T>
Harness TypeResolver::make_struct_harness() {
  return Harness(&TypeResolver::harness_write_adapter<T>,
                 &TypeResolver::harness_read_adapter<T>,
                 &TypeResolver::harness_write_data_adapter<T>,
                 &TypeResolver::harness_read_data_adapter<T>,
                 &TypeResolver::harness_struct_sorted_fields<T>);
}

template <typename T>
Harness TypeResolver::make_serializer_harness() {
  return Harness(&TypeResolver::harness_write_adapter<T>,
                 &TypeResolver::harness_read_adapter<T>,
                 &TypeResolver::harness_write_data_adapter<T>,
                 &TypeResolver::harness_read_data_adapter<T>,
                 &TypeResolver::harness_empty_sorted_fields<T>);
}

template <typename T>
Result<void, Error>
TypeResolver::harness_write_adapter(const std::any &value, WriteContext &ctx,
                                    bool write_ref_info, bool write_type_info,
                                    bool has_generics) {
  (void)has_generics;
  const T *ptr = any_to_pointer<T>(value);
  if (ptr == nullptr) {
    return Unexpected(Error::type_error(
        "Failed to extract value for serializer harness write"));
  }
  return Serializer<T>::write(*ptr, ctx, write_ref_info, write_type_info);
}

template <typename T>
Result<std::any, Error>
TypeResolver::harness_read_adapter(ReadContext &ctx, bool read_ref_info,
                                   bool read_type_info) {
  auto value_result = Serializer<T>::read(ctx, read_ref_info, read_type_info);
  if (!value_result.ok()) {
    return Unexpected(std::move(value_result).error());
  }
  return make_any_value<T>(std::move(value_result).value());
}

template <typename T>
Result<void, Error>
TypeResolver::harness_write_data_adapter(const std::any &value,
                                         WriteContext &ctx, bool has_generics) {
  const T *ptr = any_to_pointer<T>(value);
  if (ptr == nullptr) {
    return Unexpected(Error::type_error(
        "Failed to extract value for serializer harness write_data"));
  }
  return Serializer<T>::write_data_generic(*ptr, ctx, has_generics);
}

template <typename T>
Result<std::any, Error>
TypeResolver::harness_read_data_adapter(ReadContext &ctx) {
  auto value_result = Serializer<T>::read_data(ctx);
  if (!value_result.ok()) {
    return Unexpected(std::move(value_result).error());
  }
  return make_any_value<T>(std::move(value_result).value());
}

template <typename T>
Result<std::vector<FieldInfo>, Error>
TypeResolver::harness_struct_sorted_fields(TypeResolver &) {
  static_assert(is_fory_serializable_v<T>,
                "harness_struct_sorted_fields requires FORY_STRUCT types");
  const auto meta_desc = ForyFieldInfo(T{});
  constexpr size_t field_count = decltype(meta_desc)::Size;
  auto fields =
      detail::build_field_infos<T>(std::make_index_sequence<field_count>{});
  auto sorted = TypeMeta::sort_field_infos(std::move(fields));
  return sorted;
}

template <typename T>
Result<std::vector<FieldInfo>, Error>
TypeResolver::harness_empty_sorted_fields(TypeResolver &) {
  return std::vector<FieldInfo>{};
}

template <typename T>
const T *TypeResolver::any_to_pointer(const std::any &value) {
  if (const auto *ptr = std::any_cast<const T>(&value)) {
    return ptr;
  }
  if (const auto *ptr = std::any_cast<T>(&value)) {
    return ptr;
  }
  if (const auto *ptr = std::any_cast<const T *>(&value)) {
    return (ptr && *ptr) ? *ptr : nullptr;
  }
  if (const auto *ptr = std::any_cast<T *>(&value)) {
    return (ptr && *ptr) ? *ptr : nullptr;
  }
  if (const auto *ref =
          std::any_cast<std::reference_wrapper<const T>>(&value)) {
    return &ref->get();
  }
  if (const auto *ref = std::any_cast<std::reference_wrapper<T>>(&value)) {
    return &ref->get();
  }
  if (const auto *shared = std::any_cast<std::shared_ptr<T>>(&value)) {
    return shared->get();
  }
  if (const auto *shared_const =
          std::any_cast<std::shared_ptr<const T>>(&value)) {
    return shared_const->get();
  }
  return nullptr;
}

template <typename T> std::any TypeResolver::make_any_value(T &&value) {
  using ValueType = std::remove_reference_t<T>;
  if constexpr (std::is_copy_constructible_v<ValueType>) {
    return std::any(std::forward<T>(value));
  } else {
    return std::any(std::make_shared<ValueType>(std::forward<T>(value)));
  }
}

inline std::string TypeResolver::make_name_key(const std::string &ns,
                                               const std::string &name) {
  std::string key;
  key.reserve(ns.size() + 1 + name.size());
  key.append(ns);
  key.push_back('\0');
  key.append(name);
  return key;
}

inline Result<void, Error>
TypeResolver::register_type_internal(const std::type_index &type_index,
                                     std::shared_ptr<TypeInfo> info) {
  if (!info || !info->harness.valid()) {
    return Unexpected(
        Error::invalid("TypeInfo or harness is invalid during registration"));
  }

  std::lock_guard<std::mutex> lock(struct_mutex_);

  type_info_cache_[type_index] = info;

  if (info->type_id != 0) {
    auto it = type_info_by_id_.find(info->type_id);
    if (it != type_info_by_id_.end() && it->second.get() != info.get()) {
      return Unexpected(Error::invalid("Type id already registered: " +
                                       std::to_string(info->type_id)));
    }
    type_info_by_id_[info->type_id] = info;
  }

  if (info->register_by_name) {
    auto key = make_name_key(info->namespace_name, info->type_name);
    auto it = type_info_by_name_.find(key);
    if (it != type_info_by_name_.end() && it->second.get() != info.get()) {
      return Unexpected(Error::invalid(
          "Type already registered for namespace '" + info->namespace_name +
          "' and name '" + info->type_name + "'"));
    }
    type_info_by_name_[key] = info;
  }

  return Result<void, Error>();
}

inline std::shared_ptr<TypeInfo>
TypeResolver::get_type_info_by_id(uint32_t type_id) const {
  std::lock_guard<std::mutex> lock(struct_mutex_);
  auto it = type_info_by_id_.find(type_id);
  if (it != type_info_by_id_.end()) {
    return it->second;
  }
  return nullptr;
}

inline std::shared_ptr<TypeInfo>
TypeResolver::get_type_info_by_name(const std::string &ns,
                                     const std::string &type_name) const {
  std::lock_guard<std::mutex> lock(struct_mutex_);
  auto key = make_name_key(ns, type_name);
  auto it = type_info_by_name_.find(key);
  if (it != type_info_by_name_.end()) {
    return it->second;
  }
  return nullptr;
}

// ============================================================================
// Meta Sharing implementations (after TypeInfo is defined)
// ============================================================================

inline void TypeResolver::meta_write_to_buffer(Buffer &buffer) const {
  buffer.WriteVarUint32(static_cast<uint32_t>(write_type_defs_.size()));
  for (const auto &type_def : write_type_defs_) {
    buffer.WriteBytes(type_def.data(), type_def.size());
  }
}

inline bool TypeResolver::meta_empty() const {
  return write_type_defs_.empty();
}

inline void TypeResolver::meta_reset_writer() {
  write_type_defs_.clear();
  write_type_id_index_map_.clear();
}

inline Result<std::shared_ptr<TypeInfo>, Error>
TypeResolver::meta_get_by_index(size_t index) const {
  if (index >= reading_type_infos_.size()) {
    return Unexpected(Error::invalid(
        "Meta index out of bounds: " + std::to_string(index) +
        ", size: " + std::to_string(reading_type_infos_.size())));
  }
  return reading_type_infos_[index];
}

inline void TypeResolver::meta_reset_reader() {
  reading_type_infos_.clear();
  parsed_type_infos_.clear();
}

} // namespace serialization
} // namespace fory
