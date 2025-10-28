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

#include "fory/serialization/array_serializer.h"
#include "fory/serialization/collection_serializer.h"
#include "fory/serialization/config.h"
#include "fory/serialization/context.h"
#include "fory/serialization/map_serializer.h"
#include "fory/serialization/serializer.h"
#include "fory/serialization/smart_ptr_serializers.h"
#include "fory/serialization/struct_serializer.h"
#include "fory/serialization/temporal_serializers.h"
#include "fory/serialization/type_resolver.h"
#include "fory/util/buffer.h"
#include "fory/util/error.h"
#include "fory/util/pool.h"
#include "fory/util/result.h"
#include <cstring>
#include <memory>
#include <string>
#include <utility>
#include <vector>

namespace fory {
namespace serialization {

// Forward declaration
class Fory;

/// Builder class for creating Fory instances with custom configuration.
///
/// Use this class to construct a Fory instance with specific settings.
/// The builder pattern ensures clean, readable configuration code.
///
/// Example:
/// ```cpp
/// auto fory = Fory::builder()
///     .compatible(true)
///     .xlang(true)
///     .check_struct_version(false)
///     .max_depth(128)
///     .build();
/// ```
class ForyBuilder {
public:
  /// Default constructor with sensible defaults
  ForyBuilder() = default;

  /// Enable/disable compatible mode for schema evolution.
  ForyBuilder &compatible(bool enable) {
    config_.compatible = enable;
    return *this;
  }

  /// Enable/disable cross-language (xlang) serialization mode.
  ForyBuilder &xlang(bool enable) {
    config_.xlang = enable;
    return *this;
  }

  /// Enable/disable struct version checking.
  ForyBuilder &check_struct_version(bool enable) {
    config_.check_struct_version = enable;
    return *this;
  }

  /// Set maximum allowed nesting depth.
  ForyBuilder &max_depth(uint32_t depth) {
    config_.max_depth = depth;
    return *this;
  }

  /// Enable/disable reference tracking for shared/circular references.
  ForyBuilder &track_ref(bool enable) {
    config_.track_ref = enable;
    return *this;
  }

  /// Enable/disable meta string compression.
  ForyBuilder &compress_meta_strings(bool enable) {
    config_.compress_meta_strings = enable;
    return *this;
  }

  /// Provide a custom type resolver instance.
  ForyBuilder &type_resolver(std::shared_ptr<TypeResolver> resolver) {
    type_resolver_ = std::move(resolver);
    return *this;
  }

  /// Build the final Fory instance.
  Fory build();

private:
  Config config_;
  std::shared_ptr<TypeResolver> type_resolver_;

  friend class Fory;
};

/// Main Fory serialization class.
///
/// This class provides serialization and deserialization functionality
/// for C++ objects. Create instances using the builder pattern via
/// Fory::builder().
///
/// Example:
/// ```cpp
/// // Create Fory instance
/// auto fory = Fory::builder().xlang(true).build();
///
/// // Serialize
/// MyStruct obj{...};
/// auto bytes_result = fory.serialize(obj);
/// if (bytes_result.ok()) {
///   std::vector<uint8_t> bytes = bytes_result.value();
/// }
///
/// // Deserialize
/// auto obj_result = fory.deserialize<MyStruct>(bytes.data(), bytes.size());
/// if (obj_result.ok()) {
///   MyStruct obj = obj_result.value();
/// }
/// ```
class Fory {
public:
  /// Create a builder for configuring Fory instance.
  static ForyBuilder builder() { return ForyBuilder(); }

  // ============================================================================
  // Serialization Methods
  // ============================================================================

  /// Serialize an object to a byte vector.
  ///
  /// @param obj Object to serialize (const reference).
  /// @return Vector of bytes on success, error on failure.
  template <typename T>
  Result<std::vector<uint8_t>, Error> serialize(const T &obj) {
    // Allocate buffer (estimate size)
    Buffer buffer;

    // Write Fory header
    FORY_RETURN_NOT_OK(write_header(buffer, false, config_.xlang,
                                    is_little_endian_system(), false,
                                    Language::CPP));

    auto ctx_handle = write_ctx_pool_.acquire();
    WriteContext &ctx = *ctx_handle;
    ctx.attach(buffer);
    ctx.reset();
    struct WriteContextCleanup {
      WriteContext &ctx;
      ~WriteContextCleanup() {
        ctx.reset();
        ctx.detach();
      }
    } cleanup{ctx};

    // Serialize object
    FORY_RETURN_NOT_OK(Serializer<T>::write(obj, ctx, true, true));

    // Copy to vector
    std::vector<uint8_t> result(buffer.writer_index());
    std::memcpy(result.data(), buffer.data(), buffer.writer_index());
    return result;
  }

  /// Serialize an object to an existing buffer.
  ///
  /// @param obj Object to serialize (const reference).
  /// @param buffer Output buffer to write to.
  /// @return Number of bytes written on success, error on failure.
  template <typename T>
  Result<size_t, Error> serialize_to(const T &obj, Buffer &buffer) {
    size_t start_pos = buffer.writer_index();

    // Write Fory header
    FORY_RETURN_NOT_OK(write_header(buffer, false, config_.xlang,
                                    is_little_endian_system(), false,
                                    Language::CPP));

    auto ctx_handle = write_ctx_pool_.acquire();
    WriteContext &ctx = *ctx_handle;
    ctx.attach(buffer);
    ctx.reset();
    struct WriteContextCleanup {
      WriteContext &ctx;
      ~WriteContextCleanup() {
        ctx.reset();
        ctx.detach();
      }
    } cleanup{ctx};

    FORY_RETURN_NOT_OK(Serializer<T>::write(obj, ctx, true, true));

    return buffer.writer_index() - start_pos;
  }

  // ============================================================================
  // Deserialization Methods
  // ============================================================================

  /// Deserialize an object from a byte array.
  ///
  /// @param data Pointer to serialized data. Must not be nullptr.
  /// @param size Size of data in bytes.
  /// @return Deserialized object on success, error on failure.
  template <typename T>
  Result<T, Error> deserialize(const uint8_t *data, size_t size) {
    if (data == nullptr) {
      return Unexpected(Error::invalid("Data pointer is null"));
    }
    if (size == 0) {
      return Unexpected(Error::invalid("Data size is zero"));
    }

    Buffer buffer(const_cast<uint8_t *>(data), static_cast<uint32_t>(size),
                  false);

    // Read and validate header
    auto header_result = read_header(buffer);
    if (!header_result.ok()) {
      return Unexpected(std::move(header_result).error());
    }
    HeaderInfo header = header_result.value();

    // Check for null object
    if (header.is_null) {
      return Unexpected(Error::invalid_data("Cannot deserialize null object"));
    }

    // Check endianness compatibility
    if (header.is_little_endian != is_little_endian_system()) {
      return Unexpected(
          Error::unsupported("Cross-endian deserialization not yet supported"));
    }

    return deserialize_from<T>(buffer);
  }

  /// Deserialize an object from an existing buffer.
  ///
  /// @param buffer Input buffer to read from.
  /// @return Deserialized object on success, error on failure.
  template <typename T> Result<T, Error> deserialize_from(Buffer &buffer) {
    auto ctx_handle = read_ctx_pool_.acquire();
    ReadContext &ctx = *ctx_handle;
    ctx.attach(buffer);
    ctx.reset();
    struct ReadContextCleanup {
      ReadContext &ctx;
      ~ReadContextCleanup() {
        ctx.reset();
        ctx.detach();
      }
    } cleanup{ctx};

    auto result = Serializer<T>::read(ctx, true, true);
    if (result.ok()) {
      ctx.ref_reader().resolve_callbacks();
    }
    return result;
  }

  /// Get reference to configuration.
  const Config &config() const { return config_; }

  /// Access the underlying type resolver.
  TypeResolver &type_resolver() { return *type_resolver_; }
  const TypeResolver &type_resolver() const { return *type_resolver_; }

  // ==========================================================================
  // Type Registration Helpers
  // ==========================================================================

  /// Register a struct type with a numeric identifier.
  template <typename T> Result<void, Error> register_struct(uint32_t type_id) {
    return type_resolver_->template register_by_id<T>(type_id);
  }

  /// Register a struct type with an explicit namespace and name.
  template <typename T>
  Result<void, Error> register_struct(const std::string &ns,
                                      const std::string &type_name) {
    return type_resolver_->template register_by_name<T>(ns, type_name);
  }

  /// Register a struct type using only a type name (default namespace).
  template <typename T>
  Result<void, Error> register_struct(const std::string &type_name) {
    return type_resolver_->template register_by_name<T>("", type_name);
  }

  /// Register an external serializer type with a numeric identifier.
  template <typename T>
  Result<void, Error> register_extension_type(uint32_t type_id) {
    return type_resolver_->template register_ext_type_by_id<T>(type_id);
  }

  /// Register an external serializer with namespace and name.
  template <typename T>
  Result<void, Error> register_extension_type(const std::string &ns,
                                              const std::string &type_name) {
    return type_resolver_->template register_ext_type_by_name<T>(ns, type_name);
  }

  /// Register an external serializer using a type name (default namespace).
  template <typename T>
  Result<void, Error> register_extension_type(const std::string &type_name) {
    return type_resolver_->template register_ext_type_by_name<T>("", type_name);
  }

private:
  /// Private constructor - use builder() instead!
  explicit Fory(const Config &config, std::shared_ptr<TypeResolver> resolver)
      : config_(config), type_resolver_(std::move(resolver)),
        write_ctx_pool_([this]() {
          return std::make_unique<WriteContext>(config_, *type_resolver_);
        }),
        read_ctx_pool_([this]() {
          return std::make_unique<ReadContext>(config_, *type_resolver_);
        }) {
    if (!type_resolver_) {
      type_resolver_ = std::make_shared<TypeResolver>();
    }
    type_resolver_->apply_config(config_);
  }

  Config config_;
  std::shared_ptr<TypeResolver> type_resolver_;
  util::Pool<WriteContext> write_ctx_pool_;
  util::Pool<ReadContext> read_ctx_pool_;

  friend class ForyBuilder;
};

// ============================================================================
// ForyBuilder Implementation
// ============================================================================

inline Fory ForyBuilder::build() {
  if (!type_resolver_) {
    type_resolver_ = std::make_shared<TypeResolver>();
  }
  return Fory(config_, type_resolver_);
}

} // namespace serialization
} // namespace fory
