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

#include "fory/serialization/context.h"
#include "fory/serialization/meta_string.h"
#include "fory/serialization/type_resolver.h"
#include "fory/thirdparty/MurmurHash3.h"
#include "fory/type/type.h"

namespace fory {
namespace serialization {

// ============================================================================
// WriteContext Implementation
// ============================================================================

WriteContext::WriteContext(const Config &config,
                           std::shared_ptr<TypeResolver> type_resolver)
    : buffer_(), config_(&config), type_resolver_(std::move(type_resolver)),
      current_depth_(0) {}

WriteContext::~WriteContext() = default;

Result<void, Error>
WriteContext::write_enum_type_info(const std::type_index &type,
                                   uint32_t base_type_id) {
  if (!type_resolver_) {
    buffer_.WriteVarUint32(base_type_id);
    return Result<void, Error>();
  }
  auto type_info_result = type_resolver_->get_type_info(type);
  if (type_info_result.ok()) {
    const TypeInfo *info = type_info_result.value();
    if (info != nullptr) {
      bool needs_extended =
          info->register_by_name || !is_internal_type(info->type_id);
      if (needs_extended) {
        auto result = write_any_typeinfo(info->type_id, type);
        if (!result.ok()) {
          return Unexpected(result.error());
        }
        return Result<void, Error>();
      }
    }
  }
  buffer_.WriteVarUint32(base_type_id);
  return Result<void, Error>();
}

Result<size_t, Error> WriteContext::push_meta(const std::type_index &type_id) {
  auto it = write_type_id_index_map_.find(type_id);
  if (it != write_type_id_index_map_.end()) {
    return it->second;
  }

  size_t index = write_type_defs_.size();
  FORY_TRY(type_info, type_resolver_->get_type_info(type_id));
  write_type_defs_.push_back(type_info->type_def);
  write_type_id_index_map_[type_id] = index;
  return index;
}

void WriteContext::write_meta(size_t offset) {
  size_t current_pos = buffer_.writer_index();
  // Update the meta offset field (written as -1 initially)
  int32_t meta_size = static_cast<int32_t>(current_pos - offset - 4);
  buffer_.UnsafePut<int32_t>(offset, meta_size);
  // Write all collected TypeMetas
  buffer_.WriteVarUint32(static_cast<uint32_t>(write_type_defs_.size()));
  for (size_t i = 0; i < write_type_defs_.size(); ++i) {
    const auto &type_def = write_type_defs_[i];
    buffer_.WriteBytes(type_def.data(), type_def.size());
  }
}

bool WriteContext::meta_empty() const { return write_type_defs_.empty(); }

Result<const TypeInfo *, Error>
WriteContext::write_any_typeinfo(uint32_t fory_type_id,
                                 const std::type_index &concrete_type_id) {
  // Check if it's an internal type
  if (is_internal_type(fory_type_id)) {
    buffer_.WriteVarUint32(fory_type_id);
    auto type_info = type_resolver_->get_type_info_by_id(fory_type_id);
    if (!type_info) {
      return Unexpected(
          Error::type_error("Type info for internal type not found"));
    }
    return type_info.get();
  }

  // Get type info for the concrete type
  FORY_TRY(type_info, type_resolver_->get_type_info(concrete_type_id));
  uint32_t type_id = type_info->type_id;
  const std::string &namespace_name = type_info->namespace_name;
  const std::string &type_name = type_info->type_name;

  // Write type_id
  buffer_.WriteVarUint32(type_id);

  // Handle different type categories based on low byte
  uint32_t type_id_low = type_id & 0xff;
  switch (type_id_low) {
  case static_cast<uint32_t>(TypeId::NAMED_COMPATIBLE_STRUCT):
  case static_cast<uint32_t>(TypeId::COMPATIBLE_STRUCT): {
    // Write meta_index
    FORY_TRY(meta_index, push_meta(concrete_type_id));
    buffer_.WriteVarUint32(static_cast<uint32_t>(meta_index));
    break;
  }
  case static_cast<uint32_t>(TypeId::NAMED_ENUM):
  case static_cast<uint32_t>(TypeId::NAMED_EXT):
  case static_cast<uint32_t>(TypeId::NAMED_STRUCT): {
    if (config_->compatible) {
      // Write meta_index (share_meta is effectively compatible in C++)
      FORY_TRY(meta_index, push_meta(concrete_type_id));
      buffer_.WriteVarUint32(static_cast<uint32_t>(meta_index));
    } else {
      // Write namespace and type_name using the same format as Java's
      // MetaStringResolver#writeMetaStringBytes (without dynamic reuse).
      constexpr uint32_t kSmallStringThreshold = 16;

      // Create encoders matching Java's PACKAGE_ENCODER ('.', '_') for namespace
      // and TYPE_NAME_ENCODER ('$', '_') for type names.
      static const MetaStringEncoder kNamespaceEncoder('.', '_');
      static const MetaStringEncoder kTypeNameEncoder('$', '_');

      // Java encoding restrictions (from Encoders.java):
      // pkgEncodings = {UTF_8, ALL_TO_LOWER_SPECIAL, LOWER_UPPER_DIGIT_SPECIAL}
      // typeNameEncodings = {UTF_8, ALL_TO_LOWER_SPECIAL, LOWER_UPPER_DIGIT_SPECIAL, FIRST_TO_LOWER_SPECIAL}
      // Note: LOWER_SPECIAL is NOT allowed for either!
      static const std::vector<MetaEncoding> kPkgEncodings = {
          MetaEncoding::UTF8, MetaEncoding::ALL_TO_LOWER_SPECIAL,
          MetaEncoding::LOWER_UPPER_DIGIT_SPECIAL};
      static const std::vector<MetaEncoding> kTypeNameEncodings = {
          MetaEncoding::UTF8, MetaEncoding::ALL_TO_LOWER_SPECIAL,
          MetaEncoding::LOWER_UPPER_DIGIT_SPECIAL,
          MetaEncoding::FIRST_TO_LOWER_SPECIAL};

      auto write_meta_string =
          [this](const std::string &value, const MetaStringEncoder &encoder,
                 const std::vector<MetaEncoding> &encodings) -> Result<void, Error> {
        // Encode the string first using allowed encodings
        FORY_TRY(encoded, encoder.encode(value, encodings));
        const uint32_t encoded_len = static_cast<uint32_t>(encoded.bytes.size());
        uint32_t header = encoded_len << 1; // last bit 0 => new string
        buffer_.WriteVarUint32(header);

        if (encoded_len > kSmallStringThreshold) {
          // For strings > 16 bytes, write hashCode (int64) instead of encoding
          int64_t hash_out[2] = {0, 0};
          MurmurHash3_x64_128(encoded.bytes.data(),
                              static_cast<int>(encoded.bytes.size()), 47,
                              hash_out);
          buffer_.WriteInt64(hash_out[0]);
        } else {
          // For strings <= 16 bytes, write encoding byte
          buffer_.WriteInt8(static_cast<int8_t>(encoded.encoding));
        }

        if (encoded_len > 0) {
          buffer_.WriteBytes(encoded.bytes.data(), encoded_len);
        }
        return Result<void, Error>();
      };

      FORY_RETURN_NOT_OK(
          write_meta_string(namespace_name, kNamespaceEncoder, kPkgEncodings));
      FORY_RETURN_NOT_OK(
          write_meta_string(type_name, kTypeNameEncoder, kTypeNameEncodings));
    }
    break;
  }
  default:
    // For other types, just writing type_id is sufficient
    break;
  }

  return type_info;
}

void WriteContext::reset() {
  ref_writer_.reset();
  write_type_defs_.clear();
  write_type_id_index_map_.clear();
  current_depth_ = 0;
  // Reset buffer for reuse
  buffer_.WriterIndex(0);
  buffer_.ReaderIndex(0);
}

// ============================================================================
// ReadContext Implementation
// ============================================================================

ReadContext::ReadContext(const Config &config,
                         std::shared_ptr<TypeResolver> type_resolver)
    : buffer_(nullptr), config_(&config),
      type_resolver_(std::move(type_resolver)), current_depth_(0) {}

ReadContext::~ReadContext() = default;

// Static decoders for NAMED_ENUM namespace/type_name - shared across calls
static const MetaStringDecoder kNamespaceDecoder('.', '_');
static const MetaStringDecoder kTypeNameDecoder('$', '_');

Result<void, Error>
ReadContext::read_enum_type_info(const std::type_index &type,
                                 uint32_t base_type_id) {
  // Helper to consume namespace/type_name for NAMED_ENUM
  auto consume_named_enum_metadata = [this]() -> Result<void, Error> {
    if (config_->compatible) {
      // In compatible mode, read meta_index
      FORY_TRY(meta_index, buffer_->ReadVarUint32());
      (void)meta_index;
    } else {
      // Read namespace and type_name using MetaStringResolver-compatible
      // encoding. Java uses PACKAGE_DECODER ('.', '_') for namespace and
      // TYPE_NAME_DECODER ('$', '_') for type names.
      FORY_TRY(namespace_str,
               meta_string_table_.read_string(*buffer_, kNamespaceDecoder));
      FORY_TRY(type_name,
               meta_string_table_.read_string(*buffer_, kTypeNameDecoder));
      (void)namespace_str;
      (void)type_name;
    }
    return Result<void, Error>();
  };

  if (!type_resolver_) {
    FORY_TRY(type_id, read_varuint32());
    // For enums, accept both ENUM and NAMED_ENUM as compatible types
    if (base_type_id == static_cast<uint32_t>(TypeId::ENUM) ||
        base_type_id == static_cast<uint32_t>(TypeId::NAMED_ENUM)) {
      if (type_id != static_cast<uint32_t>(TypeId::ENUM) &&
          type_id != static_cast<uint32_t>(TypeId::NAMED_ENUM)) {
        return Unexpected(Error::type_mismatch(type_id, base_type_id));
      }
      // If NAMED_ENUM, consume namespace/type_name
      if (type_id == static_cast<uint32_t>(TypeId::NAMED_ENUM)) {
        return consume_named_enum_metadata();
      }
      return Result<void, Error>();
    }
    if (type_id != base_type_id) {
      return Unexpected(Error::type_mismatch(type_id, base_type_id));
    }
    return Result<void, Error>();
  }

  uint32_t start = buffer_->reader_index();
  FORY_TRY(type_id, buffer_->ReadVarUint32());
  // If type_id < 256, it's a base type (high byte is 0, meaning no registration
  // ID) Internal types and unregistered user types both fall in this range
  if (type_id < 256 || is_internal_type(type_id)) {
    // For enums, accept both ENUM and NAMED_ENUM as compatible types
    // since Java may write NAMED_ENUM when the enum is registered with a name
    if (base_type_id == static_cast<uint32_t>(TypeId::ENUM) ||
        base_type_id == static_cast<uint32_t>(TypeId::NAMED_ENUM)) {
      if (type_id != static_cast<uint32_t>(TypeId::ENUM) &&
          type_id != static_cast<uint32_t>(TypeId::NAMED_ENUM)) {
        return Unexpected(Error::type_mismatch(type_id, base_type_id));
      }
      // If NAMED_ENUM, consume namespace/type_name
      if (type_id == static_cast<uint32_t>(TypeId::NAMED_ENUM)) {
        return consume_named_enum_metadata();
      }
      return Result<void, Error>();
    }
    if (type_id != base_type_id) {
      return Unexpected(Error::type_mismatch(type_id, base_type_id));
    }
    return Result<void, Error>();
  }

  buffer_->ReaderIndex(start);
  auto type_info_result = read_any_typeinfo();
  if (!type_info_result.ok()) {
    return Unexpected(type_info_result.error());
  }

  const auto &type_info = type_info_result.value();
  if (!type_info) {
    return Result<void, Error>();
  }
  uint32_t low = type_info->type_id & 0xff;
  if (low == static_cast<uint32_t>(TypeId::ENUM) ||
      low == static_cast<uint32_t>(TypeId::NAMED_ENUM)) {
    return Result<void, Error>();
  }
  return Unexpected(Error::type_mismatch(low, base_type_id));
}

Result<size_t, Error> ReadContext::load_type_meta(int32_t meta_offset) {
  size_t current_pos = buffer_->reader_index();
  size_t meta_start = current_pos + meta_offset;
  buffer_->ReaderIndex(static_cast<uint32_t>(meta_start));

  // Load all TypeMetas
  FORY_TRY(meta_size, buffer_->ReadVarUint32());
  reading_type_infos_.reserve(meta_size);

  for (uint32_t i = 0; i < meta_size; i++) {
    FORY_TRY(parsed_meta, TypeMeta::from_bytes(*buffer_, nullptr));

    // Find local TypeInfo to get field_id mapping
    std::shared_ptr<TypeInfo> local_type_info = nullptr;
    if (parsed_meta->register_by_name) {
      local_type_info = type_resolver_->get_type_info_by_name(
          parsed_meta->namespace_str, parsed_meta->type_name);
    } else {
      local_type_info =
          type_resolver_->get_type_info_by_id(parsed_meta->type_id);
    }

    // Create TypeInfo with field_ids assigned
    std::shared_ptr<TypeInfo> type_info;
    if (local_type_info) {
      // Have local type - assign field_ids by comparing schemas
      TypeMeta::assign_field_ids(local_type_info->type_meta.get(),
                                 parsed_meta->field_infos);
      type_info = std::make_shared<TypeInfo>();
      type_info->type_id = local_type_info->type_id;
      type_info->type_meta = parsed_meta;
      type_info->type_def = local_type_info->type_def;
      // CRITICAL: Copy the harness from the registered type_info
      type_info->harness = local_type_info->harness;
      type_info->name_to_index = local_type_info->name_to_index;
      type_info->namespace_name = local_type_info->namespace_name;
      type_info->type_name = local_type_info->type_name;
      type_info->register_by_name = local_type_info->register_by_name;
    } else {
      // No local type - create stub TypeInfo with parsed meta
      type_info = std::make_shared<TypeInfo>();
      type_info->type_id = parsed_meta->type_id;
      type_info->type_meta = parsed_meta;
    }

    // Cast to void* to store in reading_type_infos_
    reading_type_infos_.push_back(std::static_pointer_cast<void>(type_info));
  }

  // Calculate size of meta section
  size_t meta_end = buffer_->reader_index();
  size_t meta_section_size = meta_end - meta_start;

  // Restore buffer position
  buffer_->ReaderIndex(static_cast<uint32_t>(current_pos));
  return meta_section_size;
}

Result<std::shared_ptr<void>, Error>
ReadContext::get_type_info_by_index(size_t index) const {
  if (index >= reading_type_infos_.size()) {
    return Unexpected(Error::invalid(
        "Meta index out of bounds: " + std::to_string(index) +
        ", size: " + std::to_string(reading_type_infos_.size())));
  }
  return reading_type_infos_[index];
}

Result<std::shared_ptr<TypeInfo>, Error> ReadContext::read_any_typeinfo() {
  FORY_TRY(fory_type_id, buffer_->ReadVarUint32());
  uint32_t type_id_low = fory_type_id & 0xff;

  // Handle different type categories based on low byte
  switch (type_id_low) {
  case static_cast<uint32_t>(TypeId::NAMED_COMPATIBLE_STRUCT):
  case static_cast<uint32_t>(TypeId::COMPATIBLE_STRUCT): {
    // Read meta_index - may or may not have loaded metas depending on context
    FORY_TRY(meta_index, buffer_->ReadVarUint32());
    // Try to get TypeInfo from loaded metas
    auto type_info_result = get_type_info_by_index(meta_index);
    if (type_info_result.has_value()) {
      auto type_info =
          std::static_pointer_cast<TypeInfo>(type_info_result.value());
      return type_info;
    }
    // If no loaded metas (e.g., reading list element type info at top level),
    // return a stub TypeInfo with the type_id
    auto stub = std::make_shared<TypeInfo>();
    stub->type_id = fory_type_id;
    stub->register_by_name = false;
    return stub;
  }
  case static_cast<uint32_t>(TypeId::NAMED_ENUM):
  case static_cast<uint32_t>(TypeId::NAMED_EXT):
  case static_cast<uint32_t>(TypeId::NAMED_STRUCT): {
    if (config_->compatible) {
      // Read meta_index (share_meta is effectively compatible in C++)
      FORY_TRY(meta_index, buffer_->ReadVarUint32());
      // Try to get TypeInfo from loaded metas
      auto type_info_result = get_type_info_by_index(meta_index);
      if (type_info_result.has_value()) {
        auto type_info =
            std::static_pointer_cast<TypeInfo>(type_info_result.value());
        return type_info;
      }
      // If no loaded metas, return a stub TypeInfo
      auto stub = std::make_shared<TypeInfo>();
      stub->type_id = fory_type_id;
      stub->register_by_name = true;
      return stub;
    } else {
      // Read namespace and type_name using MetaStringResolver-compatible
      // encoding. Java uses PACKAGE_DECODER ('.', '_') for namespace and
      // TYPE_NAME_DECODER ('$', '_') for type names.
      static const MetaStringDecoder kNamespaceDecoder('.', '_');
      static const MetaStringDecoder kTypeNameDecoder('$', '_');

      FORY_TRY(namespace_str,
               meta_string_table_.read_string(*buffer_, kNamespaceDecoder));
      FORY_TRY(type_name,
               meta_string_table_.read_string(*buffer_, kTypeNameDecoder));

      auto type_info =
          type_resolver_->get_type_info_by_name(namespace_str, type_name);
      if (!type_info) {
        auto stub = std::make_shared<TypeInfo>();
        stub->type_id = fory_type_id;
        stub->namespace_name = std::move(namespace_str);
        stub->type_name = std::move(type_name);
        stub->register_by_name = true;
        return stub;
      }
      return type_info;
    }
  }
  case static_cast<uint32_t>(TypeId::STRUCT):
  case static_cast<uint32_t>(TypeId::ENUM):
  case static_cast<uint32_t>(TypeId::EXT): {
    // Plain STRUCT/ENUM/EXT types without namespace+typename: just return a
    // stub TypeInfo. The caller will use the local type's serializer directly.
    auto stub = std::make_shared<TypeInfo>();
    stub->type_id = fory_type_id;
    stub->register_by_name = false;
    return stub;
  }
  default: {
    // For internal types (STRING, TIMESTAMP, etc.), create a stub TypeInfo
    // since they don't require registration. For user types, look up by ID.
    if (is_internal_type(fory_type_id)) {
      auto stub = std::make_shared<TypeInfo>();
      stub->type_id = fory_type_id;
      stub->register_by_name = false;
      return stub;
    }
    // Look up by type_id for user types
    auto type_info = type_resolver_->get_type_info_by_id(fory_type_id);
    if (!type_info) {
      return Unexpected(Error::type_error("ID harness not found"));
    }
    return type_info;
  }
  }
}

void ReadContext::reset() {
  ref_reader_.reset();
  reading_type_infos_.clear();
  parsed_type_infos_.clear();
  current_depth_ = 0;
  meta_string_table_.reset();
}

} // namespace serialization
} // namespace fory
