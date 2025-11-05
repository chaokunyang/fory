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
#include "fory/serialization/type_resolver.h"

namespace fory {
namespace serialization {

// ============================================================================
// WriteContext Implementation
// ============================================================================

WriteContext::WriteContext(const Config &config, TypeResolver &type_resolver)
    : buffer_(), config_(&config), type_resolver_(&type_resolver),
      current_depth_(0) {}

WriteContext::~WriteContext() = default;

Result<size_t, Error> WriteContext::push_meta(const std::type_index &type_id) {
  auto it = write_type_id_index_map_.find(type_id);
  if (it != write_type_id_index_map_.end()) {
    return it->second;
  }

  size_t index = write_type_defs_.size();
  const auto &type_info_cache = type_resolver_->get_type_info_cache();
  auto cache_it = type_info_cache.find(type_id);
  if (cache_it == type_info_cache.end()) {
    return Unexpected(Error::type_error("Type not registered"));
  }
  auto type_info = cache_it->second;
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
  for (const auto &type_def : write_type_defs_) {
    buffer_.WriteBytes(type_def.data(), type_def.size());
  }
}

bool WriteContext::meta_empty() const { return write_type_defs_.empty(); }

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

ReadContext::ReadContext(const Config &config, TypeResolver &type_resolver)
    : buffer_(nullptr), config_(&config), type_resolver_(&type_resolver),
      current_depth_(0) {}

ReadContext::~ReadContext() = default;

Result<void, Error> ReadContext::load_type_meta(int32_t meta_offset) {
  size_t current_pos = buffer_->reader_index();
  size_t meta_start = current_pos + meta_offset;
  buffer_->ReaderIndex(static_cast<uint32_t>(meta_start));

  // Load all TypeMetas
  auto meta_size_result = buffer_->ReadVarUint32();
  if (!meta_size_result.ok()) {
    return Unexpected(std::move(meta_size_result).error());
  }
  uint32_t meta_size = std::move(meta_size_result).value();
  reading_type_infos_.reserve(meta_size);

  for (uint32_t i = 0; i < meta_size; i++) {
    auto parsed_meta_result = TypeMeta::from_bytes(*buffer_, nullptr);
    if (!parsed_meta_result.ok()) {
      return Unexpected(std::move(parsed_meta_result).error());
    }
    auto parsed_meta = std::move(parsed_meta_result).value();

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
      type_info->type_meta = parsed_meta;
      type_info->type_def = local_type_info->type_def;
    } else {
      // No local type - create stub TypeInfo with parsed meta
      type_info = std::make_shared<TypeInfo>();
      type_info->type_meta = parsed_meta;
    }

    // Cast to void* to store in reading_type_infos_
    reading_type_infos_.push_back(std::static_pointer_cast<void>(type_info));
  }

  buffer_->ReaderIndex(static_cast<uint32_t>(current_pos));
  return {};
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

void ReadContext::reset() {
  ref_reader_.reset();
  reading_type_infos_.clear();
  parsed_type_infos_.clear();
  current_depth_ = 0;
}

} // namespace serialization
} // namespace fory
