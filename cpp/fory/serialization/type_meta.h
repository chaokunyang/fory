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

#include "fory/type/type.h"
#include "fory/util/buffer.h"
#include "fory/util/error.h"
#include "fory/util/result.h"
#include <cstdint>
#include <memory>
#include <string>
#include <vector>

namespace fory {
namespace serialization {

// Forward declarations
class MetaString;
class FieldType;
class FieldInfo;
class TypeMeta;

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
  /// @param nullable Nullability to write if write_flag is true
  Result<void, Error> write_to(Buffer &buffer, bool write_flag,
                                 bool nullable) const;

  /// Read field type from buffer
  /// @param buffer Source buffer
  /// @param read_flag Whether to read nullability flag (for nested types)
  /// @param nullable Nullability if read_flag is false
  static Result<FieldType, Error> read_from(Buffer &buffer, bool read_flag,
                                              bool nullable);

  bool operator==(const FieldType &other) const {
    return type_id == other.type_id && generics == other.generics;
  }

  bool operator!=(const FieldType &other) const { return !(*this == other); }
};

// ============================================================================
// FieldInfo - Field metadata (name, type, id)
// ============================================================================

/// Field information including name, type, and assigned field ID
class FieldInfo {
public:
  int16_t field_id;        // Assigned during deserialization (-1 = skip)
  std::string field_name;  // Field name
  FieldType field_type;    // Field type information

  FieldInfo() : field_id(-1) {}

  FieldInfo(const std::string &name, FieldType type)
      : field_id(-1), field_name(name), field_type(std::move(type)) {}

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

/// Type metadata containing all field information
/// Used for schema evolution to compare remote and local type schemas
class TypeMeta {
public:
  int64_t hash;                     // Type hash for fast comparison
  uint32_t type_id;                 // Type ID (for non-named registration)
  std::string namespace_str;        // Namespace (for named registration)
  std::string type_name;            // Type name (for named registration)
  bool register_by_name;            // Whether registered by name
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

} // namespace serialization
} // namespace fory
