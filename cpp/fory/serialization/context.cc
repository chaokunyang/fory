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
    : buffer_(nullptr), config_(&config), type_resolver_(&type_resolver),
      current_depth_(0) {
}

WriteContext::~WriteContext() = default;

Result<size_t, Error> WriteContext::push_meta(const std::type_index &type_id) {
  return type_resolver_->meta_push(type_id);
}

void WriteContext::write_meta(size_t offset) {
  size_t current_pos = buffer_->writer_index();
  // Update the meta offset field (written as -1 initially)
  int32_t meta_size = static_cast<int32_t>(current_pos - offset - 4);
  buffer_->UnsafePut<int32_t>(offset, meta_size);
  // Write all collected TypeMetas
  type_resolver_->meta_write_to_buffer(*buffer_);
}

bool WriteContext::meta_empty() const {
  return type_resolver_->meta_empty();
}

void WriteContext::reset() {
  ref_writer_.reset();
  type_resolver_->meta_reset_writer();
  current_depth_ = 0;
}

// ============================================================================
// ReadContext Implementation
// ============================================================================

ReadContext::ReadContext(const Config &config, TypeResolver &type_resolver)
    : buffer_(nullptr), config_(&config), type_resolver_(&type_resolver),
      current_depth_(0) {
}

ReadContext::~ReadContext() = default;

Result<size_t, Error> ReadContext::load_type_meta(int32_t meta_offset) {
  size_t current_pos = buffer_->reader_index();
  size_t meta_start = current_pos + meta_offset;
  buffer_->ReaderIndex(static_cast<uint32_t>(meta_start));
  auto result = type_resolver_->meta_load(*buffer_);
  if (!result.ok()) {
    return Unexpected(std::move(result).error());
  }
  buffer_->ReaderIndex(static_cast<uint32_t>(current_pos));
  // Return bytes to skip after reading data (to skip over meta section)
  return meta_offset;
}

Result<std::shared_ptr<void>, Error>
ReadContext::get_type_info_by_index(size_t index) const {
  auto result = type_resolver_->meta_get_by_index(index);
  if (!result.ok()) {
    return Unexpected(std::move(result).error());
  }
  // Cast TypeInfo to void*
  return std::static_pointer_cast<void>(std::move(result).value());
}

void ReadContext::reset() {
  ref_reader_.reset();
  type_resolver_->meta_reset_reader();
  current_depth_ = 0;
}

} // namespace serialization
} // namespace fory
