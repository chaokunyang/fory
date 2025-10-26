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

#include "type_meta.h"
#include "fory/type/type.h"
#include <algorithm>
#include <cstring>
#include <map>
#include <unordered_map>

namespace fory {
namespace serialization {

// Constants from xlang spec
constexpr size_t SMALL_NUM_FIELDS_THRESHOLD = 0b11111;
constexpr uint8_t REGISTER_BY_NAME_FLAG = 0b100000;
constexpr size_t FIELD_NAME_SIZE_THRESHOLD = 0b1111;
constexpr int64_t META_SIZE_MASK = 0xfff;
constexpr int64_t COMPRESS_META_FLAG = 0b1 << 13;
constexpr int64_t HAS_FIELDS_META_FLAG = 0b1 << 12;
constexpr int8_t NUM_HASH_BITS = 50;

// ============================================================================
// FieldType Implementation
// ============================================================================

Result<void, Error> FieldType::write_to(Buffer &buffer, bool write_flag,
                                        bool nullable_val) const {
  uint32_t header = type_id;
  if (write_flag) {
    header <<= 2;
    if (nullable_val) {
      header |= 2;
    }
  }
  buffer.WriteVarUint32(header);

  // Write generics for list/set/map
  if (type_id == static_cast<uint32_t>(TypeId::LIST) ||
      type_id == static_cast<uint32_t>(TypeId::SET)) {
    if (generics.empty()) {
      return Unexpected(Error::invalid("List/Set must have element type"));
    }
    FORY_RETURN_IF_ERROR(
        generics[0].write_to(buffer, true, generics[0].nullable));
  } else if (type_id == static_cast<uint32_t>(TypeId::MAP)) {
    if (generics.size() < 2) {
      return Unexpected(Error::invalid("Map must have key and value types"));
    }
    FORY_RETURN_IF_ERROR(
        generics[0].write_to(buffer, true, generics[0].nullable));
    FORY_RETURN_IF_ERROR(
        generics[1].write_to(buffer, true, generics[1].nullable));
  }

  return {};
}

Result<FieldType, Error> FieldType::read_from(Buffer &buffer, bool read_flag,
                                              bool nullable_val) {
  auto header_result = buffer.ReadVarUint32();
  if (!header_result.ok()) {
    return Unexpected(std::move(header_result).error());
  }
  uint32_t header = header_result.value();

  uint32_t tid;
  bool null;
  if (read_flag) {
    tid = header >> 2;
    null = (header & 2) != 0;
  } else {
    tid = header;
    null = nullable_val;
  }

  FieldType ft(tid, null);

  // Read generics for list/set/map
  if (tid == static_cast<uint32_t>(TypeId::LIST) ||
      tid == static_cast<uint32_t>(TypeId::SET)) {
    auto generic_result = FieldType::read_from(buffer, true, false);
    if (!generic_result.ok()) {
      return Unexpected(std::move(generic_result).error());
    }
    ft.generics.push_back(std::move(generic_result).value());
  } else if (tid == static_cast<uint32_t>(TypeId::MAP)) {
    auto key_result = FieldType::read_from(buffer, true, false);
    if (!key_result.ok()) {
      return Unexpected(std::move(key_result).error());
    }
    auto val_result = FieldType::read_from(buffer, true, false);
    if (!val_result.ok()) {
      return Unexpected(std::move(val_result).error());
    }
    ft.generics.push_back(std::move(key_result).value());
    ft.generics.push_back(std::move(val_result).value());
  }

  return ft;
}

// ============================================================================
// FieldInfo Implementation
// ============================================================================

Result<std::vector<uint8_t>, Error> FieldInfo::to_bytes() const {
  Buffer buffer;

  // Write field header (simplified encoding for now - always use UTF8)
  // header: | field_name_encoding:2bits | size:4bits | nullability:1bit |
  // ref_tracking:1bit |
  uint8_t encoding_idx = 0; // UTF8
  size_t name_size = field_name.size();
  uint8_t header =
      (std::min(FIELD_NAME_SIZE_THRESHOLD, name_size - 1) << 2) & 0x3C;

  if (field_type.nullable) {
    header |= 2;
  }
  header |= (encoding_idx << 6);

  buffer.WriteUint8(header);

  if (name_size - 1 >= FIELD_NAME_SIZE_THRESHOLD) {
    buffer.WriteVarUint32(name_size - 1 - FIELD_NAME_SIZE_THRESHOLD);
  }

  // Write field type
  auto type_result = field_type.write_to(buffer, false, field_type.nullable);
  if (!type_result.ok()) {
    return Unexpected(std::move(type_result).error());
  }

  // Write field name
  buffer.WriteBytes(reinterpret_cast<const uint8_t *>(field_name.data()),
                    field_name.size());

  // CRITICAL FIX: Use writer_index() not size() to get actual bytes written!
  return std::vector<uint8_t>(buffer.data(), buffer.data() + buffer.writer_index());
}

Result<FieldInfo, Error> FieldInfo::from_bytes(Buffer &buffer) {
  // Read field header
  auto header_result = buffer.ReadUint8();
  if (!header_result.ok()) {
    return Unexpected(std::move(header_result).error());
  }
  uint8_t header = header_result.value();

  bool nullable = (header & 2) != 0;
  size_t name_size = ((header >> 2) & FIELD_NAME_SIZE_THRESHOLD);
  if (name_size == FIELD_NAME_SIZE_THRESHOLD) {
    auto extra_result = buffer.ReadVarUint32();
    if (!extra_result.ok()) {
      return Unexpected(std::move(extra_result).error());
    }
    name_size += extra_result.value();
  }
  name_size += 1;

  // Read field type
  auto type_result = FieldType::read_from(buffer, false, nullable);
  if (!type_result.ok()) {
    return Unexpected(std::move(type_result).error());
  }

  // Read field name
  std::string field_name(name_size, '\0');
  auto name_read_result = buffer.ReadBytes(field_name.data(), name_size);
  if (!name_read_result.ok()) {
    return Unexpected(std::move(name_read_result).error());
  }

  return FieldInfo(field_name, std::move(type_result).value());
}

// ============================================================================
// TypeMeta Implementation
// ============================================================================

TypeMeta TypeMeta::from_fields(uint32_t tid, const std::string &ns,
                               const std::string &name, bool by_name,
                               std::vector<FieldInfo> fields) {
  TypeMeta meta;
  meta.type_id = tid;
  meta.namespace_str = ns;
  meta.type_name = name;
  meta.register_by_name = by_name;
  meta.field_infos = std::move(fields);
  meta.hash = 0; // Will be computed during serialization
  return meta;
}

Result<std::vector<uint8_t>, Error> TypeMeta::to_bytes() const {
  Buffer layer_buffer;

  // Write meta header
  size_t num_fields = field_infos.size();
  uint8_t meta_header =
      static_cast<uint8_t>(std::min(num_fields, SMALL_NUM_FIELDS_THRESHOLD));
  if (register_by_name) {
    meta_header |= REGISTER_BY_NAME_FLAG;
  }
  layer_buffer.WriteUint8(meta_header);

  if (num_fields >= SMALL_NUM_FIELDS_THRESHOLD) {
    layer_buffer.WriteVarUint32(num_fields - SMALL_NUM_FIELDS_THRESHOLD);
  }

  // Write namespace and type name (if registered by name)
  // For now, use simplified UTF8 encoding
  if (register_by_name) {
    // Write namespace
    layer_buffer.WriteVarUint32(namespace_str.size());
    layer_buffer.WriteBytes(
        reinterpret_cast<const uint8_t *>(namespace_str.data()),
        namespace_str.size());
    // Write type name
    layer_buffer.WriteVarUint32(type_name.size());
    layer_buffer.WriteBytes(reinterpret_cast<const uint8_t *>(type_name.data()),
                            type_name.size());
  } else {
    layer_buffer.WriteVarUint32(type_id);
  }

  // Write field infos
  for (const auto &field : field_infos) {
    auto field_bytes_result = field.to_bytes();
    if (!field_bytes_result.ok()) {
      return Unexpected(std::move(field_bytes_result).error());
    }
    const auto &field_bytes = field_bytes_result.value();
    layer_buffer.WriteBytes(field_bytes.data(), field_bytes.size());
  }

  // Now write global binary header
  Buffer result_buffer;
  int64_t meta_size = layer_buffer.size();
  int64_t header = std::min(META_SIZE_MASK, meta_size);

  bool write_meta_fields_flag = !field_infos.empty();
  if (write_meta_fields_flag) {
    header |= HAS_FIELDS_META_FLAG;
  }

  // Compute hash
  std::vector<uint8_t> layer_data(layer_buffer.data(),
                                  layer_buffer.data() + layer_buffer.size());
  int64_t meta_hash = compute_hash(layer_data);
  header |= (meta_hash << (64 - NUM_HASH_BITS));

  result_buffer.WriteBytes(reinterpret_cast<const uint8_t *>(&header),
                           sizeof(header));

  if (meta_size >= META_SIZE_MASK) {
    result_buffer.WriteVarUint32(meta_size - META_SIZE_MASK);
  }

  result_buffer.WriteBytes(layer_data.data(), layer_data.size());

  // CRITICAL FIX: Use writer_index() not size() to get actual bytes written!
  return std::vector<uint8_t>(result_buffer.data(),
                              result_buffer.data() + result_buffer.writer_index());
}

Result<std::shared_ptr<TypeMeta>, Error>
TypeMeta::from_bytes(Buffer &buffer, const TypeMeta *local_type_info) {
  size_t start_pos = buffer.reader_index();

  // Read global binary header
  int64_t header;
  auto header_result = buffer.ReadBytes(&header, sizeof(header));
  if (!header_result.ok()) {
    return Unexpected(std::move(header_result).error());
  }

  size_t header_size = sizeof(header);
  int64_t meta_size = header & META_SIZE_MASK;
  if (meta_size == META_SIZE_MASK) {
    auto extra_result = buffer.ReadVarUint32();
    if (!extra_result.ok()) {
      return Unexpected(std::move(extra_result).error());
    }
    meta_size += extra_result.value();
    header_size += 4; // approximate varuint32 size
  }

  int64_t meta_hash = header >> (64 - NUM_HASH_BITS);

  // Read meta header
  auto meta_header_result = buffer.ReadUint8();
  if (!meta_header_result.ok()) {
    return Unexpected(std::move(meta_header_result).error());
  }
  uint8_t meta_header = meta_header_result.value();

  bool register_by_name = (meta_header & REGISTER_BY_NAME_FLAG) != 0;
  size_t num_fields = meta_header & SMALL_NUM_FIELDS_THRESHOLD;
  if (num_fields == SMALL_NUM_FIELDS_THRESHOLD) {
    auto extra_result = buffer.ReadVarUint32();
    if (!extra_result.ok()) {
      return Unexpected(std::move(extra_result).error());
    }
    num_fields += extra_result.value();
  }

  // Read type ID or namespace/type name
  uint32_t type_id = 0;
  std::string namespace_str;
  std::string type_name;

  if (register_by_name) {
    // Read namespace
    auto ns_len_result = buffer.ReadVarUint32();
    if (!ns_len_result.ok()) {
      return Unexpected(std::move(ns_len_result).error());
    }
    namespace_str.resize(ns_len_result.value());
    auto ns_read_result =
        buffer.ReadBytes(namespace_str.data(), namespace_str.size());
    if (!ns_read_result.ok()) {
      return Unexpected(std::move(ns_read_result).error());
    }

    // Read type name
    auto tn_len_result = buffer.ReadVarUint32();
    if (!tn_len_result.ok()) {
      return Unexpected(std::move(tn_len_result).error());
    }
    type_name.resize(tn_len_result.value());
    auto tn_read_result = buffer.ReadBytes(type_name.data(), type_name.size());
    if (!tn_read_result.ok()) {
      return Unexpected(std::move(tn_read_result).error());
    }
  } else {
    auto tid_result = buffer.ReadVarUint32();
    if (!tid_result.ok()) {
      return Unexpected(std::move(tid_result).error());
    }
    type_id = tid_result.value();
  }

  // Read field infos
  std::vector<FieldInfo> field_infos;
  field_infos.reserve(num_fields);
  for (size_t i = 0; i < num_fields; ++i) {
    auto field_result = FieldInfo::from_bytes(buffer);
    if (!field_result.ok()) {
      return Unexpected(std::move(field_result).error());
    }
    field_infos.push_back(std::move(field_result).value());
  }

  // Sort fields according to xlang spec
  field_infos = sort_field_infos(std::move(field_infos));

  // Assign field IDs by comparing with local type
  if (local_type_info != nullptr) {
    assign_field_ids(local_type_info, field_infos);
  }

  // CRITICAL FIX: Ensure we consume exactly meta_size bytes
  size_t current_pos = buffer.reader_index();
  size_t expected_end_pos = start_pos + header_size + meta_size;
  if (current_pos < expected_end_pos) {
    size_t remaining = expected_end_pos - current_pos;
    buffer.IncreaseReaderIndex(remaining);
  }

  auto meta = std::make_shared<TypeMeta>();
  meta->hash = meta_hash;
  meta->type_id = type_id;
  meta->namespace_str = std::move(namespace_str);
  meta->type_name = std::move(type_name);
  meta->register_by_name = register_by_name;
  meta->field_infos = std::move(field_infos);

  return meta;
}

Result<void, Error> TypeMeta::skip_bytes(Buffer &buffer, int64_t header) {
  int64_t meta_size = header & META_SIZE_MASK;
  if (meta_size == META_SIZE_MASK) {
    auto extra_result = buffer.ReadVarUint32();
    if (!extra_result.ok()) {
      return Unexpected(std::move(extra_result).error());
    }
    meta_size += extra_result.value();
  }
  return buffer.Skip(meta_size);
}

Result<void, Error>
TypeMeta::check_struct_version(int32_t read_version, int32_t local_version,
                               const std::string &type_name) {
  if (read_version != local_version) {
    return Unexpected(Error::type_error(
        "Read class " + type_name + " version " + std::to_string(read_version) +
        " is not consistent with " + std::to_string(local_version) +
        ", please align struct field types and names, or use compatible mode "
        "of Fory by Fory#compatible(true)"));
  }
  return {};
}

// ============================================================================
// Field Sorting (following xlang spec and Rust implementation)
// ============================================================================

namespace {

bool is_primitive_type(uint32_t type_id) {
  return type_id >= static_cast<uint32_t>(TypeId::BOOL) &&
         type_id <= static_cast<uint32_t>(TypeId::FLOAT64);
}

int32_t get_primitive_type_size(uint32_t type_id) {
  switch (static_cast<TypeId>(type_id)) {
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

bool is_compress(uint32_t type_id) {
  return type_id == static_cast<uint32_t>(TypeId::INT32) ||
         type_id == static_cast<uint32_t>(TypeId::INT64) ||
         type_id == static_cast<uint32_t>(TypeId::VAR_INT32) ||
         type_id == static_cast<uint32_t>(TypeId::VAR_INT64);
}

// Numeric field sorter (for primitive fields)
bool numeric_sorter(const FieldInfo &a, const FieldInfo &b) {
  uint32_t a_id = a.field_type.type_id;
  uint32_t b_id = b.field_type.type_id;
  bool a_nullable = a.field_type.nullable;
  bool b_nullable = b.field_type.nullable;
  bool compress_a = is_compress(a_id);
  bool compress_b = is_compress(b_id);
  int32_t size_a = get_primitive_type_size(a_id);
  int32_t size_b = get_primitive_type_size(b_id);

  // Sort by: nullable (false first), compress (false first), size (larger
  // first), type_id, field_name
  if (a_nullable != b_nullable)
    return !a_nullable; // non-nullable first
  if (compress_a != compress_b)
    return !compress_a; // fixed-size first
  if (size_a != size_b)
    return size_a > size_b; // larger size first
  if (a_id != b_id)
    return a_id < b_id; // smaller type id first
  return a.field_name < b.field_name;
}

// Type then name sorter (for internal type fields)
bool type_then_name_sorter(const FieldInfo &a, const FieldInfo &b) {
  if (a.field_type.type_id != b.field_type.type_id) {
    return a.field_type.type_id < b.field_type.type_id;
  }
  return a.field_name < b.field_name;
}

// Name sorter (for list/set/map/other fields)
bool name_sorter(const FieldInfo &a, const FieldInfo &b) {
  return a.field_name < b.field_name;
}

bool is_internal_type(uint32_t type_id) {
  return type_id >= static_cast<uint32_t>(TypeId::STRING) &&
         type_id <= static_cast<uint32_t>(TypeId::DECIMAL);
}

} // anonymous namespace

std::vector<FieldInfo>
TypeMeta::sort_field_infos(std::vector<FieldInfo> fields) {
  // Group fields according to xlang spec
  std::vector<FieldInfo> primitive_fields;
  std::vector<FieldInfo> nullable_primitive_fields;
  std::vector<FieldInfo> internal_type_fields;
  std::vector<FieldInfo> list_fields;
  std::vector<FieldInfo> set_fields;
  std::vector<FieldInfo> map_fields;
  std::vector<FieldInfo> other_fields;

  for (auto &field : fields) {
    uint32_t type_id = field.field_type.type_id;
    bool nullable = field.field_type.nullable;

    if (is_primitive_type(type_id)) {
      if (nullable) {
        nullable_primitive_fields.push_back(std::move(field));
      } else {
        primitive_fields.push_back(std::move(field));
      }
    } else if (type_id == static_cast<uint32_t>(TypeId::LIST)) {
      list_fields.push_back(std::move(field));
    } else if (type_id == static_cast<uint32_t>(TypeId::SET)) {
      set_fields.push_back(std::move(field));
    } else if (type_id == static_cast<uint32_t>(TypeId::MAP)) {
      map_fields.push_back(std::move(field));
    } else if (is_internal_type(type_id)) {
      internal_type_fields.push_back(std::move(field));
    } else {
      other_fields.push_back(std::move(field));
    }
  }

  // Sort each group
  std::sort(primitive_fields.begin(), primitive_fields.end(), numeric_sorter);
  std::sort(nullable_primitive_fields.begin(), nullable_primitive_fields.end(),
            numeric_sorter);
  std::sort(internal_type_fields.begin(), internal_type_fields.end(),
            type_then_name_sorter);
  std::sort(list_fields.begin(), list_fields.end(), name_sorter);
  std::sort(set_fields.begin(), set_fields.end(), name_sorter);
  std::sort(map_fields.begin(), map_fields.end(), name_sorter);
  std::sort(other_fields.begin(), other_fields.end(), name_sorter);

  // Combine sorted groups
  std::vector<FieldInfo> sorted;
  sorted.reserve(fields.size());
  sorted.insert(sorted.end(), std::make_move_iterator(primitive_fields.begin()),
                std::make_move_iterator(primitive_fields.end()));
  sorted.insert(sorted.end(),
                std::make_move_iterator(nullable_primitive_fields.begin()),
                std::make_move_iterator(nullable_primitive_fields.end()));
  sorted.insert(sorted.end(),
                std::make_move_iterator(internal_type_fields.begin()),
                std::make_move_iterator(internal_type_fields.end()));
  sorted.insert(sorted.end(), std::make_move_iterator(list_fields.begin()),
                std::make_move_iterator(list_fields.end()));
  sorted.insert(sorted.end(), std::make_move_iterator(set_fields.begin()),
                std::make_move_iterator(set_fields.end()));
  sorted.insert(sorted.end(), std::make_move_iterator(map_fields.begin()),
                std::make_move_iterator(map_fields.end()));
  sorted.insert(sorted.end(), std::make_move_iterator(other_fields.begin()),
                std::make_move_iterator(other_fields.end()));

  // Assign sequential field IDs (0, 1, 2, ...)
  for (size_t i = 0; i < sorted.size(); ++i) {
    sorted[i].field_id = static_cast<int16_t>(i);
  }

  return sorted;
}

// ============================================================================
// Field ID Assignment (KEY FUNCTION for schema evolution!)
// ============================================================================

void TypeMeta::assign_field_ids(const TypeMeta *local_type,
                                std::vector<FieldInfo> &remote_fields) {
  // Build a map: field_name -> local_field_info
  std::unordered_map<std::string, const FieldInfo *> local_field_map;
  for (const auto &field : local_type->field_infos) {
    local_field_map[field.field_name] = &field;
  }

  // For each remote field, assign field ID
  for (auto &remote_field : remote_fields) {
    auto it = local_field_map.find(remote_field.field_name);
    if (it == local_field_map.end()) {
      // Field not found in local type -> assign -1 (skip)
      remote_field.field_id = -1;
    } else {
      const FieldInfo *local_field = it->second;
      // Check if field type matches (type_id and generics)
      if (remote_field.field_type != local_field->field_type) {
        // Type mismatch -> assign -1 (skip)
        remote_field.field_id = -1;
      } else {
        // Match! -> assign local field ID
        remote_field.field_id = local_field->field_id;
      }
    }
  }
}

int64_t TypeMeta::compute_hash(const std::vector<uint8_t> &meta_bytes) {
  // Use murmurhash3 to compute hash (simplified for now)
  // In production, use proper murmurhash3_x64_128 implementation
  uint64_t hash = 0;
  for (uint8_t byte : meta_bytes) {
    hash = hash * 31 + byte;
  }
  return static_cast<int64_t>(hash & ((1ULL << NUM_HASH_BITS) - 1));
}

} // namespace serialization
} // namespace fory
