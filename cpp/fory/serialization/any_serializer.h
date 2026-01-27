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
#include "fory/serialization/type_info.h"
#include "fory/serialization/type_resolver.h"
#include "fory/type/type.h"
#include "fory/util/error.h"
#include "fory/util/result.h"

#include <any>
#include <string>
#include <typeindex>
#include <unordered_map>

namespace fory {
namespace serialization {

namespace detail {

struct AnyTypeEntry {
  uint32_t type_id;
  std::string namespace_name;
  std::string type_name;
  bool is_namespaced;
  std::type_index type_index;
  void (*write_data)(const std::any &value, WriteContext &ctx);
  std::any (*read_data)(ReadContext &ctx);
};

inline std::string make_any_name_key(const std::string &ns,
                                     const std::string &name) {
  std::string key;
  key.reserve(ns.size() + 1 + name.size());
  key.append(ns);
  key.push_back('\0');
  key.append(name);
  return key;
}

class AnyTypeRegistry {
public:
  static AnyTypeRegistry &instance() {
    static AnyTypeRegistry registry;
    return registry;
  }

  template <typename T> Result<void, Error> register_type(TypeResolver &);

  const AnyTypeEntry *find_by_type_index(const std::type_index &type_index) {
    auto it = by_type_index_.find(type_index);
    if (it == by_type_index_.end()) {
      return nullptr;
    }
    return &it->second;
  }

  const AnyTypeEntry *find_by_type_info(const TypeInfo &type_info) {
    if (is_internal_type(type_info.type_id)) {
      auto it = by_type_id_.find(type_info.type_id);
      if (it == by_type_id_.end()) {
        return nullptr;
      }
      auto ti_it = by_type_index_.find(it->second);
      if (ti_it == by_type_index_.end()) {
        return nullptr;
      }
      return &ti_it->second;
    }

    if (IsNamespacedType(static_cast<int32_t>(type_info.type_id))) {
      auto key =
          make_any_name_key(type_info.namespace_name, type_info.type_name);
      auto it = by_name_.find(key);
      if (it == by_name_.end()) {
        return nullptr;
      }
      auto ti_it = by_type_index_.find(it->second);
      if (ti_it == by_type_index_.end()) {
        return nullptr;
      }
      return &ti_it->second;
    }

    auto it = by_type_id_.find(type_info.type_id);
    if (it == by_type_id_.end()) {
      return nullptr;
    }
    auto ti_it = by_type_index_.find(it->second);
    if (ti_it == by_type_index_.end()) {
      return nullptr;
    }
    return &ti_it->second;
  }

private:
  AnyTypeRegistry() = default;

  std::unordered_map<std::type_index, AnyTypeEntry> by_type_index_;
  std::unordered_map<uint32_t, std::type_index> by_type_id_;
  std::unordered_map<std::string, std::type_index> by_name_;
};

template <typename T>
void write_any_data_impl(const std::any &value, WriteContext &ctx) {
  const T *ptr = std::any_cast<T>(&value);
  if (FORY_PREDICT_FALSE(ptr == nullptr)) {
    ctx.set_error(Error::type_error("std::any stored value type mismatch"));
    return;
  }
  Serializer<T>::write_data(*ptr, ctx);
}

template <typename T> std::any read_any_data_impl(ReadContext &ctx) {
  T value = Serializer<T>::read_data(ctx);
  if (FORY_PREDICT_FALSE(ctx.has_error())) {
    return std::any();
  }
  return std::any(std::move(value));
}

} // namespace detail

// ============================================================================
// std::any Serializer
// ============================================================================

/// Serializer for std::any.
///
/// std::any serialization requires explicit registration of allowed value
/// types via register_any_type<T>(). Only registered types can be serialized
/// or deserialized.
template <> struct Serializer<std::any> {
  static constexpr TypeId type_id = TypeId::UNKNOWN;

  static inline void write_type_info(WriteContext &ctx) {
    ctx.set_error(Error::invalid("std::any requires runtime type info"));
  }

  static inline void read_type_info(ReadContext &ctx) {
    ctx.set_error(Error::invalid("std::any requires runtime type info"));
  }

  static inline void write(const std::any &value, WriteContext &ctx,
                           RefMode ref_mode, bool write_type,
                           bool has_generics = false) {
    (void)has_generics;
    if (ref_mode != RefMode::None) {
      if (!value.has_value()) {
        ctx.write_int8(NULL_FLAG);
        return;
      }
      write_not_null_ref_flag(ctx, ref_mode);
    } else if (FORY_PREDICT_FALSE(!value.has_value())) {
      ctx.set_error(Error::invalid("std::any requires non-empty value"));
      return;
    }

    const std::type_index concrete_type_id(value.type());
    const auto *entry = detail::AnyTypeRegistry::instance().find_by_type_index(
        concrete_type_id);
    if (FORY_PREDICT_FALSE(entry == nullptr)) {
      ctx.set_error(Error::type_error("std::any type is not registered"));
      return;
    }

    if (write_type) {
      uint32_t fory_type_id = entry->type_id;
      uint32_t type_id_arg = is_internal_type(fory_type_id)
                                 ? fory_type_id
                                 : static_cast<uint32_t>(TypeId::UNKNOWN);
      auto write_res = ctx.write_any_typeinfo(type_id_arg, entry->type_index);
      if (FORY_PREDICT_FALSE(!write_res.ok())) {
        ctx.set_error(std::move(write_res).error());
        return;
      }
    }

    entry->write_data(value, ctx);
  }

  static inline void write_data(const std::any &value, WriteContext &ctx) {
    const std::type_index concrete_type_id(value.type());
    const auto *entry = detail::AnyTypeRegistry::instance().find_by_type_index(
        concrete_type_id);
    if (FORY_PREDICT_FALSE(entry == nullptr)) {
      ctx.set_error(Error::type_error("std::any type is not registered"));
      return;
    }
    entry->write_data(value, ctx);
  }

  static inline void write_data_generic(const std::any &value,
                                        WriteContext &ctx, bool has_generics) {
    (void)has_generics;
    write_data(value, ctx);
  }

  static inline std::any read(ReadContext &ctx, RefMode ref_mode,
                              bool read_type) {
    bool has_value = read_null_only_flag(ctx, ref_mode);
    if (ctx.has_error() || !has_value) {
      return std::any();
    }
    if (FORY_PREDICT_FALSE(!read_type)) {
      ctx.set_error(Error::invalid("std::any requires read_type=true"));
      return std::any();
    }

    const TypeInfo *type_info = ctx.read_any_typeinfo(ctx.error());
    if (FORY_PREDICT_FALSE(ctx.has_error())) {
      return std::any();
    }

    const auto *entry =
        detail::AnyTypeRegistry::instance().find_by_type_info(*type_info);
    if (FORY_PREDICT_FALSE(entry == nullptr)) {
      ctx.set_error(Error::type_error("std::any type is not registered"));
      return std::any();
    }

    return entry->read_data(ctx);
  }

  static inline std::any read_data(ReadContext &ctx) {
    ctx.set_error(Error::invalid("std::any requires type info for reading"));
    return std::any();
  }

  static inline std::any read_with_type_info(ReadContext &ctx, RefMode ref_mode,
                                             const TypeInfo &type_info) {
    bool has_value = read_null_only_flag(ctx, ref_mode);
    if (ctx.has_error() || !has_value) {
      return std::any();
    }

    const auto *entry =
        detail::AnyTypeRegistry::instance().find_by_type_info(type_info);
    if (FORY_PREDICT_FALSE(entry == nullptr)) {
      ctx.set_error(Error::type_error("std::any type is not registered"));
      return std::any();
    }

    return entry->read_data(ctx);
  }
};

/// Register a type so it can be serialized inside std::any.
///
/// For internal types (primitives, string, temporal), registration does not
/// require prior type registration. For user-defined types, the type must be
/// registered with TypeResolver first (e.g., via Fory::register_struct).
template <typename T>
Result<void, Error> register_any_type(TypeResolver &resolver) {
  return detail::AnyTypeRegistry::instance().template register_type<T>(
      resolver);
}

// ============================================================================
// AnyTypeRegistry registration implementation
// ============================================================================

template <typename T>
Result<void, Error>
detail::AnyTypeRegistry::register_type(TypeResolver &resolver) {
  const std::type_index type_index(typeid(T));
  auto existing = by_type_index_.find(type_index);
  if (existing != by_type_index_.end()) {
    return Result<void, Error>();
  }

  detail::AnyTypeEntry entry{
      0,
      std::string(),
      std::string(),
      false,
      type_index,
      &detail::write_any_data_impl<T>,
      &detail::read_any_data_impl<T>,
  };

  constexpr uint32_t static_type_id =
      static_cast<uint32_t>(Serializer<T>::type_id);

  if (is_internal_type(static_type_id)) {
    entry.type_id = static_type_id;
    by_type_index_.emplace(type_index, entry);
    by_type_id_.emplace(entry.type_id, type_index);
    return Result<void, Error>();
  }

  auto type_info_res = resolver.template get_type_info<T>();
  if (FORY_PREDICT_FALSE(!type_info_res.ok())) {
    return Unexpected(std::move(type_info_res).error());
  }
  const TypeInfo *type_info = type_info_res.value();
  entry.type_id = type_info->type_id;
  entry.namespace_name = type_info->namespace_name;
  entry.type_name = type_info->type_name;
  entry.is_namespaced =
      IsNamespacedType(static_cast<int32_t>(type_info->type_id));

  by_type_index_.emplace(type_index, entry);
  if (entry.is_namespaced) {
    auto key = make_any_name_key(entry.namespace_name, entry.type_name);
    by_name_.emplace(std::move(key), type_index);
  } else {
    by_type_id_.emplace(entry.type_id, type_index);
  }

  return Result<void, Error>();
}

} // namespace serialization
} // namespace fory
