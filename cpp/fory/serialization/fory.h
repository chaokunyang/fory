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
#include <mutex>
#include <string>
#include <utility>
#include <vector>

namespace fory {
namespace serialization {

// Forward declarations
class Fory;
class ThreadSafeFory;

/// Builder class for creating Fory instances with custom configuration.
///
/// Use this class to construct a Fory instance with specific settings.
/// The builder pattern ensures clean, readable configuration code.
///
/// Example:
/// ```cpp
/// // Single-threaded Fory (fastest, not thread-safe)
/// auto fory = Fory::builder()
///     .xlang(true)
///     .build();
///
/// // Thread-safe Fory (uses context pools)
/// auto fory = Fory::builder()
///     .xlang(true)
///     .build_thread_safe();
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

  /// Set maximum allowed nesting depth for dynamically-typed objects.
  ///
  /// This limits the maximum depth for nested polymorphic object serialization
  /// (e.g., shared_ptr<Base>, unique_ptr<Base>). This prevents stack overflow
  /// from deeply nested structures in dynamic serialization scenarios.
  ///
  /// Default value is 5.
  ForyBuilder &max_dyn_depth(uint32_t depth) {
    config_.max_dyn_depth = depth;
    return *this;
  }

  /// Enable/disable reference tracking for shared/circular references.
  ForyBuilder &track_ref(bool enable) {
    config_.track_ref = enable;
    return *this;
  }

  /// Provide a custom type resolver instance.
  ForyBuilder &type_resolver(std::shared_ptr<TypeResolver> resolver) {
    type_resolver_ = std::move(resolver);
    return *this;
  }

  /// Build a single-threaded Fory instance (fastest, not thread-safe).
  Fory build();

  /// Build a thread-safe Fory instance (uses context pools).
  ThreadSafeFory build_thread_safe();

private:
  Config config_;
  std::shared_ptr<TypeResolver> type_resolver_;

  /// Helper to get or create type resolver and finalize it
  std::shared_ptr<TypeResolver> get_finalized_resolver();

  friend class Fory;
  friend class ThreadSafeFory;
};

// ============================================================================
// Common serialization helpers
// ============================================================================

/// Compute the precomputed header value
inline uint32_t compute_header(bool xlang) {
  uint32_t header = 0;
  // Magic number (2 bytes, little endian)
  header |= (MAGIC_NUMBER & 0xFFFF);
  // Flags byte at position 2
  uint8_t flags = 0;
  if (is_little_endian_system()) {
    flags |= (1 << 1); // bit 1: endian flag
  }
  if (xlang) {
    flags |= (1 << 2); // bit 2: xlang flag
  }
  header |= (static_cast<uint32_t>(flags) << 16);
  // Language byte at position 3 (only used if xlang)
  header |= (static_cast<uint32_t>(Language::CPP) << 24);
  return header;
}

/// Core serialization implementation
template <typename T>
Result<size_t, Error> serialize_impl(const T &obj, WriteContext &ctx,
                                     Buffer &buffer, uint32_t precomputed_header,
                                     uint8_t header_length) {
  size_t start_pos = buffer.writer_index();

  // Write precomputed header (4 bytes), then adjust index if not xlang
  buffer.Grow(4);
  buffer.UnsafePut<uint32_t>(buffer.writer_index(), precomputed_header);
  buffer.IncreaseWriterIndex(header_length);

  // Reserve space for meta offset in compatible mode
  size_t meta_start_offset = 0;
  if (ctx.is_compatible()) {
    meta_start_offset = buffer.writer_index();
    buffer.WriteInt32(-1); // Placeholder for meta offset (fixed 4 bytes)
  }

  // Top-level serialization: YES ref flags, yes type info
  FORY_RETURN_NOT_OK(Serializer<T>::write(obj, ctx, true, true));

  // Write collected TypeMetas at the end in compatible mode
  if (ctx.is_compatible() && !ctx.meta_empty()) {
    ctx.write_meta(meta_start_offset);
  }

  return buffer.writer_index() - start_pos;
}

/// Core deserialization implementation
template <typename T>
Result<T, Error> deserialize_impl(ReadContext &ctx, Buffer &buffer) {
  // Load TypeMetas at the beginning in compatible mode
  size_t bytes_to_skip = 0;
  if (ctx.is_compatible()) {
    auto meta_offset_result = buffer.ReadInt32();
    FORY_RETURN_IF_ERROR(meta_offset_result);
    int32_t meta_offset = meta_offset_result.value();
    if (meta_offset != -1) {
      FORY_TRY(meta_size, ctx.load_type_meta(meta_offset));
      bytes_to_skip = meta_size;
    }
  }

  // Top-level deserialization: YES ref flags, yes type info
  auto result = Serializer<T>::read(ctx, true, true);

  if (result.ok()) {
    ctx.ref_reader().resolve_callbacks();
    if (bytes_to_skip > 0) {
      buffer.IncreaseReaderIndex(static_cast<uint32_t>(bytes_to_skip));
    }
  }
  return result;
}

// ============================================================================
// BaseFory - Common base class for Fory implementations
// ============================================================================

/// Base class for Fory serialization implementations.
///
/// This class provides common functionality shared between single-threaded
/// and thread-safe Fory implementations, including configuration access
/// and type registration methods.
///
/// Users should not instantiate this class directly. Use Fory for
/// single-threaded scenarios or ThreadSafeFory for multi-threaded scenarios.
class BaseFory {
public:
  virtual ~BaseFory() = default;

  // ==========================================================================
  // Configuration Access
  // ==========================================================================

  /// Get reference to the serialization configuration.
  ///
  /// The configuration contains settings like xlang mode, compatible mode,
  /// reference tracking, etc.
  ///
  /// @return Const reference to the Config object.
  const Config &config() const { return config_; }

  /// Access the underlying type resolver.
  ///
  /// The type resolver manages type registration and lookup for serialization.
  /// Use this for advanced type manipulation or to check registered types.
  ///
  /// @return Reference to the TypeResolver.
  TypeResolver &type_resolver() { return *type_resolver_; }

  /// Access the underlying type resolver (const version).
  ///
  /// @return Const reference to the TypeResolver.
  const TypeResolver &type_resolver() const { return *type_resolver_; }

  // ==========================================================================
  // Type Registration Methods
  // ==========================================================================

  /// Register a struct type with a numeric type ID.
  ///
  /// Use this method to register types for cross-language serialization
  /// where types are identified by numeric IDs. The type ID must be unique
  /// across all registered types and match the ID used in other languages.
  ///
  /// @tparam T The struct type to register (must be defined with FORY_STRUCT).
  /// @param type_id Unique numeric identifier for this type.
  /// @return Success or error if registration fails.
  ///
  /// Example:
  /// ```cpp
  /// struct MyStruct { int32_t value; };
  /// FORY_STRUCT(MyStruct, value);
  ///
  /// fory.register_struct<MyStruct>(1);
  /// ```
  template <typename T> Result<void, Error> register_struct(uint32_t type_id) {
    return type_resolver_->template register_by_id<T>(type_id);
  }

  /// Register a struct type with namespace and type name.
  ///
  /// Use this method for named type registration, which provides more
  /// flexibility for schema evolution and cross-language compatibility.
  ///
  /// @tparam T The struct type to register (must be defined with FORY_STRUCT).
  /// @param ns Namespace for the type (can be empty string).
  /// @param type_name Name of the type within the namespace.
  /// @return Success or error if registration fails.
  ///
  /// Example:
  /// ```cpp
  /// fory.register_struct<MyStruct>("com.example", "MyStruct");
  /// ```
  template <typename T>
  Result<void, Error> register_struct(const std::string &ns,
                                      const std::string &type_name) {
    return type_resolver_->template register_by_name<T>(ns, type_name);
  }

  /// Register a struct type with type name only (no namespace).
  ///
  /// Convenience method for registering types without a namespace.
  ///
  /// @tparam T The struct type to register (must be defined with FORY_STRUCT).
  /// @param type_name Name of the type.
  /// @return Success or error if registration fails.
  ///
  /// Example:
  /// ```cpp
  /// fory.register_struct<MyStruct>("MyStruct");
  /// ```
  template <typename T>
  Result<void, Error> register_struct(const std::string &type_name) {
    return type_resolver_->template register_by_name<T>("", type_name);
  }

  /// Register an extension type with a numeric type ID.
  ///
  /// Extension types allow custom serialization logic for types that
  /// don't fit the standard struct serialization pattern.
  ///
  /// @tparam T The extension type to register.
  /// @param type_id Unique numeric identifier for this type.
  /// @return Success or error if registration fails.
  template <typename T>
  Result<void, Error> register_extension_type(uint32_t type_id) {
    return type_resolver_->template register_ext_type_by_id<T>(type_id);
  }

  /// Register an extension type with namespace and type name.
  ///
  /// @tparam T The extension type to register.
  /// @param ns Namespace for the type (can be empty string).
  /// @param type_name Name of the type within the namespace.
  /// @return Success or error if registration fails.
  template <typename T>
  Result<void, Error> register_extension_type(const std::string &ns,
                                              const std::string &type_name) {
    return type_resolver_->template register_ext_type_by_name<T>(ns, type_name);
  }

  /// Register an extension type with type name only (no namespace).
  ///
  /// @tparam T The extension type to register.
  /// @param type_name Name of the type.
  /// @return Success or error if registration fails.
  template <typename T>
  Result<void, Error> register_extension_type(const std::string &type_name) {
    return type_resolver_->template register_ext_type_by_name<T>("", type_name);
  }

protected:
  /// Protected constructor - only derived classes can instantiate.
  explicit BaseFory(const Config &config,
                    std::shared_ptr<TypeResolver> resolver)
      : config_(config), type_resolver_(std::move(resolver)),
        precomputed_header_(compute_header(config.xlang)),
        header_length_(config.xlang ? 4 : 3) {}

  // Non-copyable
  BaseFory(const BaseFory &) = delete;
  BaseFory &operator=(const BaseFory &) = delete;

  // Non-movable (to ensure stable 'this' pointer for pool lambdas)
  BaseFory(BaseFory &&) = delete;
  BaseFory &operator=(BaseFory &&) = delete;

  Config config_;
  std::shared_ptr<TypeResolver> type_resolver_;
  uint32_t precomputed_header_;
  uint8_t header_length_;
};

// ============================================================================
// Fory - Single-threaded serialization (fastest)
// ============================================================================

/// Single-threaded Fory serialization class.
///
/// This class provides the fastest serialization by holding WriteContext and
/// ReadContext directly without pool overhead. NOT thread-safe - use one
/// instance per thread or use ThreadSafeFory for multi-threaded scenarios.
///
/// Example:
/// ```cpp
/// auto fory = Fory::builder().xlang(true).build();
/// fory.register_struct<MyStruct>(1);
///
/// MyStruct obj{...};
/// auto result = fory.serialize(obj);
/// ```
class Fory : public BaseFory {
public:
  /// Create a builder for configuring Fory instance.
  static ForyBuilder builder() { return ForyBuilder(); }

  // ==========================================================================
  // Serialization Methods
  // ==========================================================================

  /// Serialize an object to a new byte vector.
  ///
  /// Creates a new vector containing the serialized data. This is the
  /// simplest API but allocates memory on each call.
  ///
  /// @tparam T The type of object to serialize.
  /// @param obj The object to serialize.
  /// @return Vector containing serialized bytes, or error.
  ///
  /// Example:
  /// ```cpp
  /// MyStruct obj{42, "hello"};
  /// auto result = fory.serialize(obj);
  /// if (result.ok()) {
  ///   std::vector<uint8_t> bytes = result.value();
  /// }
  /// ```
  template <typename T>
  Result<std::vector<uint8_t>, Error> serialize(const T &obj) {
    ensure_finalized();
    write_ctx_.reset_fast();
    Buffer &buffer = write_ctx_.buffer();

    FORY_RETURN_NOT_OK(serialize_impl(obj, write_ctx_, buffer,
                                      precomputed_header_, header_length_));

    std::vector<uint8_t> result(buffer.writer_index());
    std::memcpy(result.data(), buffer.data(), buffer.writer_index());
    return result;
  }

  /// Serialize an object to an existing Buffer.
  ///
  /// Writes serialized data directly to the provided buffer. This is the
  /// fastest serialization path as it avoids memory allocation when the
  /// buffer is pre-reserved.
  ///
  /// @tparam T The type of object to serialize.
  /// @param obj The object to serialize.
  /// @param buffer The buffer to write to (will be appended to).
  /// @return Number of bytes written, or error.
  ///
  /// Example:
  /// ```cpp
  /// fory::Buffer buffer;
  /// buffer.Reserve(1024);  // Pre-allocate for best performance
  ///
  /// for (auto& obj : objects) {
  ///   buffer.WriterIndex(0);  // Reset for reuse
  ///   auto result = fory.serialize_to(obj, buffer);
  ///   // Use buffer.data() and buffer.writer_index()
  /// }
  /// ```
  template <typename T>
  FORY_ALWAYS_INLINE Result<size_t, Error> serialize_to(const T &obj,
                                                        Buffer &buffer) {
    if (FORY_PREDICT_FALSE(!finalized_)) {
      ensure_finalized();
    }
    return serialize_impl(obj, write_ctx_, buffer, precomputed_header_,
                          header_length_);
  }

  /// Serialize an object to an existing byte vector.
  ///
  /// Resizes the output vector to fit the serialized data. The vector
  /// is reused across calls for better performance.
  ///
  /// @tparam T The type of object to serialize.
  /// @param obj The object to serialize.
  /// @param output The vector to write to (will be resized).
  /// @return Number of bytes written, or error.
  ///
  /// Example:
  /// ```cpp
  /// std::vector<uint8_t> output;
  /// output.reserve(1024);  // Pre-allocate for best performance
  ///
  /// auto result = fory.serialize_to(obj, output);
  /// ```
  template <typename T>
  Result<size_t, Error> serialize_to(const T &obj,
                                     std::vector<uint8_t> &output) {
    ensure_finalized();
    write_ctx_.reset_fast();
    Buffer &buffer = write_ctx_.buffer();

    FORY_TRY(bytes_written, serialize_impl(obj, write_ctx_, buffer,
                                           precomputed_header_, header_length_));

    output.resize(buffer.writer_index());
    std::memcpy(output.data(), buffer.data(), buffer.writer_index());
    return bytes_written;
  }

  // ==========================================================================
  // Deserialization Methods
  // ==========================================================================

  /// Deserialize an object from a byte array.
  ///
  /// Reads serialized data from a raw byte pointer and reconstructs
  /// the original object.
  ///
  /// @tparam T The type of object to deserialize.
  /// @param data Pointer to serialized data.
  /// @param size Size of serialized data in bytes.
  /// @return Deserialized object, or error.
  ///
  /// Example:
  /// ```cpp
  /// auto result = fory.deserialize<MyStruct>(bytes.data(), bytes.size());
  /// if (result.ok()) {
  ///   MyStruct obj = result.value();
  /// }
  /// ```
  template <typename T>
  Result<T, Error> deserialize(const uint8_t *data, size_t size) {
    ensure_finalized();
    if (data == nullptr) {
      return Unexpected(Error::invalid("Data pointer is null"));
    }
    if (size == 0) {
      return Unexpected(Error::invalid("Data size is zero"));
    }

    Buffer buffer(const_cast<uint8_t *>(data), static_cast<uint32_t>(size),
                  false);

    FORY_TRY(header, read_header(buffer));
    if (header.is_null) {
      return Unexpected(Error::invalid_data("Cannot deserialize null object"));
    }
    if (header.is_little_endian != is_little_endian_system()) {
      return Unexpected(
          Error::unsupported("Cross-endian deserialization not yet supported"));
    }

    read_ctx_.reset();
    read_ctx_.attach(buffer);
    auto result = deserialize_impl<T>(read_ctx_, buffer);
    read_ctx_.detach();
    return result;
  }

  /// Deserialize an object from a byte vector.
  ///
  /// Convenience method that takes a vector instead of raw pointer.
  ///
  /// @tparam T The type of object to deserialize.
  /// @param data Vector containing serialized data.
  /// @return Deserialized object, or error.
  ///
  /// Example:
  /// ```cpp
  /// std::vector<uint8_t> bytes = ...;
  /// auto result = fory.deserialize<MyStruct>(bytes);
  /// ```
  template <typename T>
  Result<T, Error> deserialize(const std::vector<uint8_t> &data) {
    return deserialize<T>(data.data(), data.size());
  }

  // ==========================================================================
  // Advanced Access
  // ==========================================================================

  /// Access the internal WriteContext (for advanced use).
  ///
  /// Use this for direct manipulation of the serialization context.
  /// Most users should use the serialize() methods instead.
  WriteContext &write_context() { return write_ctx_; }

  /// Access the internal ReadContext (for advanced use).
  ///
  /// Use this for direct manipulation of the deserialization context.
  /// Most users should use the deserialize() methods instead.
  ReadContext &read_context() { return read_ctx_; }

private:
  explicit Fory(const Config &config, std::shared_ptr<TypeResolver> resolver)
      : BaseFory(config, std::move(resolver)), finalized_(false),
        write_ctx_(config_, type_resolver_),
        read_ctx_(config_, type_resolver_) {}

  /// Finalize the type resolver on first use
  void ensure_finalized() {
    if (!finalized_) {
      auto final_result = type_resolver_->build_final_type_resolver();
      FORY_CHECK(final_result.ok())
          << "Failed to build finalized TypeResolver: "
          << final_result.error().to_string();
      type_resolver_ = std::move(final_result).value();
      finalized_ = true;
    }
  }

  bool finalized_;
  WriteContext write_ctx_;
  ReadContext read_ctx_;

  friend class ForyBuilder;
};

// ============================================================================
// ThreadSafeFory - Thread-safe serialization with context pools
// ============================================================================

/// Thread-safe Fory serialization class.
///
/// This class uses context pools to provide thread-safe serialization.
/// Slightly slower than single-threaded Fory due to pool overhead, but
/// safe to use from multiple threads concurrently.
///
/// Example:
/// ```cpp
/// auto fory = Fory::builder().xlang(true).build_thread_safe();
/// fory.register_struct<MyStruct>(1);
///
/// // Can be used from multiple threads safely
/// std::thread t1([&]() {
///   auto result = fory.serialize(obj1);
/// });
/// std::thread t2([&]() {
///   auto result = fory.serialize(obj2);
/// });
/// ```
class ThreadSafeFory : public BaseFory {
public:
  // ==========================================================================
  // Serialization Methods
  // ==========================================================================

  /// Serialize an object to a new byte vector.
  ///
  /// Thread-safe version that acquires a context from the pool.
  ///
  /// @tparam T The type of object to serialize.
  /// @param obj The object to serialize.
  /// @return Vector containing serialized bytes, or error.
  template <typename T>
  Result<std::vector<uint8_t>, Error> serialize(const T &obj) {
    auto ctx_handle = write_ctx_pool_.acquire();
    WriteContext &ctx = *ctx_handle;
    struct ContextGuard {
      WriteContext &ctx;
      ~ContextGuard() { ctx.reset(); }
    } guard{ctx};

    FORY_RETURN_NOT_OK(serialize_impl(obj, ctx, ctx.buffer(),
                                      precomputed_header_, header_length_));

    std::vector<uint8_t> result(ctx.buffer().writer_index());
    std::memcpy(result.data(), ctx.buffer().data(), ctx.buffer().writer_index());
    return result;
  }

  /// Serialize an object to an existing Buffer.
  ///
  /// Thread-safe version that writes directly to the provided buffer.
  ///
  /// @tparam T The type of object to serialize.
  /// @param obj The object to serialize.
  /// @param buffer The buffer to write to.
  /// @return Number of bytes written, or error.
  template <typename T>
  Result<size_t, Error> serialize_to(const T &obj, Buffer &buffer) {
    auto ctx_handle = write_ctx_pool_.acquire();
    WriteContext &ctx = *ctx_handle;
    struct ContextGuard {
      WriteContext &ctx;
      ~ContextGuard() { ctx.reset(); }
    } guard{ctx};

    return serialize_impl(obj, ctx, buffer, precomputed_header_, header_length_);
  }

  /// Serialize an object to an existing byte vector.
  ///
  /// Thread-safe version that resizes and fills the output vector.
  ///
  /// @tparam T The type of object to serialize.
  /// @param obj The object to serialize.
  /// @param output The vector to write to.
  /// @return Number of bytes written, or error.
  template <typename T>
  Result<size_t, Error> serialize_to(const T &obj,
                                     std::vector<uint8_t> &output) {
    auto ctx_handle = write_ctx_pool_.acquire();
    WriteContext &ctx = *ctx_handle;
    struct ContextGuard {
      WriteContext &ctx;
      ~ContextGuard() { ctx.reset(); }
    } guard{ctx};

    FORY_TRY(bytes_written, serialize_impl(obj, ctx, ctx.buffer(),
                                           precomputed_header_, header_length_));

    output.resize(ctx.buffer().writer_index());
    std::memcpy(output.data(), ctx.buffer().data(), ctx.buffer().writer_index());
    return bytes_written;
  }

  /// Create a WriteContext for direct serialization.
  ///
  /// Creates a new WriteContext that can be used for advanced serialization
  /// scenarios. The returned context is independent and thread-safe.
  std::unique_ptr<WriteContext> create_write_context() {
    return std::make_unique<WriteContext>(config_, get_finalized_resolver());
  }

  // ==========================================================================
  // Deserialization Methods
  // ==========================================================================

  /// Deserialize an object from a byte array.
  ///
  /// Thread-safe version that acquires a context from the pool.
  ///
  /// @tparam T The type of object to deserialize.
  /// @param data Pointer to serialized data.
  /// @param size Size of serialized data in bytes.
  /// @return Deserialized object, or error.
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

    FORY_TRY(header, read_header(buffer));
    if (header.is_null) {
      return Unexpected(Error::invalid_data("Cannot deserialize null object"));
    }
    if (header.is_little_endian != is_little_endian_system()) {
      return Unexpected(
          Error::unsupported("Cross-endian deserialization not yet supported"));
    }

    auto ctx_handle = read_ctx_pool_.acquire();
    ReadContext &ctx = *ctx_handle;
    ctx.attach(buffer);
    struct ReadContextCleanup {
      ReadContext &ctx;
      ~ReadContextCleanup() {
        ctx.reset();
        ctx.detach();
      }
    } cleanup{ctx};

    return deserialize_impl<T>(ctx, buffer);
  }

  /// Deserialize an object from a byte vector.
  ///
  /// Thread-safe convenience method that takes a vector.
  ///
  /// @tparam T The type of object to deserialize.
  /// @param data Vector containing serialized data.
  /// @return Deserialized object, or error.
  template <typename T>
  Result<T, Error> deserialize(const std::vector<uint8_t> &data) {
    return deserialize<T>(data.data(), data.size());
  }

private:
  explicit ThreadSafeFory(const Config &config,
                          std::shared_ptr<TypeResolver> resolver)
      : BaseFory(config, std::move(resolver)), finalized_resolver_(),
        finalized_once_flag_(),
        write_ctx_pool_([this]() {
          return std::make_unique<WriteContext>(config_,
                                                get_finalized_resolver());
        }),
        read_ctx_pool_([this]() {
          return std::make_unique<ReadContext>(config_,
                                               get_finalized_resolver());
        }) {}

  std::shared_ptr<TypeResolver> get_finalized_resolver() const {
    std::call_once(finalized_once_flag_, [this]() {
      auto final_result = type_resolver_->build_final_type_resolver();
      FORY_CHECK(final_result.ok())
          << "Failed to build finalized TypeResolver: "
          << final_result.error().to_string();
      finalized_resolver_ = std::move(final_result).value();
    });
    return finalized_resolver_->clone();
  }

  mutable std::shared_ptr<TypeResolver> finalized_resolver_;
  mutable std::once_flag finalized_once_flag_;
  util::Pool<WriteContext> write_ctx_pool_;
  util::Pool<ReadContext> read_ctx_pool_;

  friend class ForyBuilder;
};

// ============================================================================
// ForyBuilder Implementation
// ============================================================================

inline std::shared_ptr<TypeResolver> ForyBuilder::get_finalized_resolver() {
  if (!type_resolver_) {
    type_resolver_ = std::make_shared<TypeResolver>();
  }
  type_resolver_->apply_config(config_);
  auto final_result = type_resolver_->build_final_type_resolver();
  FORY_CHECK(final_result.ok())
      << "Failed to build finalized TypeResolver: "
      << final_result.error().to_string();
  return std::move(final_result).value();
}

inline Fory ForyBuilder::build() {
  if (!type_resolver_) {
    type_resolver_ = std::make_shared<TypeResolver>();
  }
  type_resolver_->apply_config(config_);
  // Don't finalize yet - allow type registration, finalize on first use
  return Fory(config_, type_resolver_);
}

inline ThreadSafeFory ForyBuilder::build_thread_safe() {
  if (!type_resolver_) {
    type_resolver_ = std::make_shared<TypeResolver>();
  }
  type_resolver_->apply_config(config_);
  // ThreadSafeFory builds finalized resolver lazily
  return ThreadSafeFory(config_, type_resolver_);
}

} // namespace serialization
} // namespace fory
