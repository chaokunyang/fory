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

#include "fory/serialization/config.h"
#include "fory/serialization/ref_resolver.h"
#include "fory/util/buffer.h"
#include "fory/util/error.h"
#include "fory/util/result.h"

namespace fory {
namespace serialization {

/// Write context for serialization operations.
///
/// This class maintains the state during serialization, including:
/// - Output buffer for writing data
/// - Reference tracking for shared/circular references
/// - Configuration flags
/// - Depth tracking for preventing stack overflow
///
/// Example:
/// ```cpp
/// Buffer buffer;
/// WriteContext ctx(buffer, config);
/// ctx.write_uint8(42);
/// ```
class WriteContext {
public:
  /// Construct write context with buffer and configuration.
  ///
  /// @param buffer Reference to output buffer. Must remain valid during
  /// context lifetime.
  /// @param config Configuration options.
  explicit WriteContext(Buffer &buffer, const Config &config)
      : buffer_(buffer), config_(config), current_depth_(0) {}

  /// Get reference to output buffer.
  inline Buffer &buffer() { return buffer_; }

  /// Get const reference to output buffer.
  inline const Buffer &buffer() const { return buffer_; }

  /// Get reference writer for tracking shared references.
  inline RefWriter &ref_writer() { return ref_writer_; }

  /// Check if compatible mode is enabled.
  inline bool is_compatible() const { return config_.compatible; }

  /// Check if xlang mode is enabled.
  inline bool is_xlang() const { return config_.xlang; }

  /// Check if struct version checking is enabled.
  inline bool check_struct_version() const { return config_.check_struct_version; }

  /// Check if reference tracking is enabled.
  inline bool track_references() const { return config_.track_references; }

  /// Get maximum allowed nesting depth.
  inline uint32_t max_depth() const { return config_.max_depth; }

  /// Get current nesting depth.
  inline uint32_t current_depth() const { return current_depth_; }

  /// Increase nesting depth by 1.
  ///
  /// @return Error if max depth exceeded, success otherwise.
  inline Result<void, Error> increase_depth() {
    if (current_depth_ >= config_.max_depth) {
      return Unexpected(
          Error::depth_exceed("Max serialization depth exceeded: " +
                              std::to_string(config_.max_depth)));
    }
    current_depth_++;
    return Result<void, Error>();
  }

  /// Decrease nesting depth by 1.
  inline void decrease_depth() {
    if (current_depth_ > 0) {
      current_depth_--;
    }
  }

  /// Write uint8_t value to buffer.
  inline void write_uint8(uint8_t value) {
    buffer_.WriteUint8(value);
  }

  /// Write int8_t value to buffer.
  inline void write_int8(int8_t value) {
    buffer_.WriteInt8(value);
  }

  /// Write uint32_t value as varint to buffer.
  inline void write_varuint32(uint32_t value) {
    buffer_.WriteVarUint32(value);
  }

  /// Write uint64_t value as varint to buffer.
  inline void write_varuint64(uint64_t value) {
    buffer_.WriteVarUint64(value);
  }

  /// Write raw bytes to buffer.
  inline void write_bytes(const void *data, uint32_t length) {
    buffer_.WriteBytes(data, length);
  }

  /// Reset context for reuse.
  inline void reset() {
  ref_writer_.reset();
    current_depth_ = 0;
  }

private:
  Buffer &buffer_;
  const Config &config_;
  RefWriter ref_writer_;
  uint32_t current_depth_;
};

/// Read context for deserialization operations.
///
/// This class maintains the state during deserialization, including:
/// - Input buffer for reading data
/// - Reference tracking for reconstructing shared/circular references
/// - Configuration flags
/// - Depth tracking for preventing stack overflow
///
/// Example:
/// ```cpp
/// Buffer buffer(data, size);
/// ReadContext ctx(buffer, config);
/// auto result = ctx.read_uint8();
/// if (result.ok()) {
///   uint8_t value = result.value();
/// }
/// ```
class ReadContext {
public:
  /// Construct read context with buffer and configuration.
  ///
  /// @param buffer Reference to input buffer. Must remain valid during context
  /// lifetime.
  /// @param config Configuration options.
  explicit ReadContext(Buffer &buffer, const Config &config)
      : buffer_(buffer), config_(config), current_depth_(0) {}

  /// Get reference to input buffer.
  inline Buffer &buffer() { return buffer_; }

  /// Get const reference to input buffer.
  inline const Buffer &buffer() const { return buffer_; }

  /// Get reference reader for reconstructing shared references.
  inline RefReader &ref_reader() { return ref_reader_; }

  /// Check if compatible mode is enabled.
  inline bool is_compatible() const { return config_.compatible; }

  /// Check if xlang mode is enabled.
  inline bool is_xlang() const { return config_.xlang; }

  /// Check if struct version checking is enabled.
  inline bool check_struct_version() const { return config_.check_struct_version; }

  /// Check if reference tracking is enabled.
  inline bool track_references() const { return config_.track_references; }

  /// Get maximum allowed nesting depth.
  inline uint32_t max_depth() const { return config_.max_depth; }

  /// Get current nesting depth.
  inline uint32_t current_depth() const { return current_depth_; }

  /// Increase nesting depth by 1.
  ///
  /// @return Error if max depth exceeded, success otherwise.
  inline Result<void, Error> increase_depth() {
    if (current_depth_ >= config_.max_depth) {
      return Unexpected(
          Error::depth_exceed("Max deserialization depth exceeded: " +
                              std::to_string(config_.max_depth)));
    }
    current_depth_++;
    return Result<void, Error>();
  }

  /// Decrease nesting depth by 1.
  inline void decrease_depth() {
    if (current_depth_ > 0) {
      current_depth_--;
    }
  }

  /// Read uint8_t value from buffer.
  inline Result<uint8_t, Error> read_uint8() { return buffer_.ReadUint8(); }

  /// Read int8_t value from buffer.
  inline Result<int8_t, Error> read_int8() { return buffer_.ReadInt8(); }

  /// Read uint32_t value as varint from buffer.
  inline Result<uint32_t, Error> read_varuint32() { return buffer_.ReadVarUint32(); }

  /// Read uint64_t value as varint from buffer.
  inline Result<uint64_t, Error> read_varuint64() { return buffer_.ReadVarUint64(); }

  /// Read raw bytes from buffer.
  inline Result<void, Error> read_bytes(void *data, uint32_t length) {
    return buffer_.ReadBytes(data, length);
  }

  /// Reset context for reuse.
  inline void reset() {
  ref_reader_.reset();
    current_depth_ = 0;
  }

private:
  Buffer &buffer_;
  const Config &config_;
  RefReader ref_reader_;
  uint32_t current_depth_;
};

} // namespace serialization
} // namespace fory
