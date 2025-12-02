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
#include <cstdint>
#include <map>
#include <memory>
#include <optional>
#include <string>
#include <unordered_map>

namespace fory {
namespace serialization {

// ============================================================================
// Map KV Header Constants
// ============================================================================

/// Maximum number of key-value pairs in a single chunk
constexpr uint8_t MAX_CHUNK_SIZE = 255;

/// Bit flags for map key-value header
constexpr uint8_t TRACKING_KEY_REF = 0b000001;
constexpr uint8_t KEY_NULL = 0b000010;
constexpr uint8_t DECL_KEY_TYPE = 0b000100;
constexpr uint8_t TRACKING_VALUE_REF = 0b001000;
constexpr uint8_t VALUE_NULL = 0b010000;
constexpr uint8_t DECL_VALUE_TYPE = 0b100000;

// ============================================================================
// Type Info Methods
// ============================================================================

/// Write type info for a type to buffer.
template <typename T>
inline Result<void, Error> write_type_info(WriteContext &ctx) {
  Error error;
  Serializer<T>::write_type_info(ctx, &error);
  if (FORY_PREDICT_FALSE(!error.ok())) {
    return Unexpected(std::move(error));
  }
  return Result<void, Error>();
}

/// Read and validate type info for a type from buffer.
template <typename T>
inline Result<void, Error> read_type_info(ReadContext &ctx) {
  Error error;
  Serializer<T>::read_type_info(ctx, &error);
  if (FORY_PREDICT_FALSE(!error.ok())) {
    return Unexpected(std::move(error));
  }
  return Result<void, Error>();
}

inline Result<const TypeInfo *, Error>
read_polymorphic_type_info(ReadContext &ctx) {
  return ctx.read_any_typeinfo();
}

// ============================================================================
// Helper Functions for Map Serialization
// ============================================================================

/// Helper to reserve capacity if the container supports it
template <typename MapType, typename = void> struct MapReserver {
  static void reserve(MapType &map, uint32_t size) {
    // No-op for containers without reserve (like std::map)
  }
};

template <typename MapType>
struct MapReserver<MapType,
                   std::void_t<decltype(std::declval<MapType>().reserve(0))>> {
  static void reserve(MapType &map, uint32_t size) { map.reserve(size); }
};

/// Write chunk size at header offset
inline void write_chunk_size(WriteContext &ctx, size_t header_offset,
                             uint8_t size) {
  // header_offset points to the header byte, size is at offset + 1
  ctx.buffer().UnsafePutByte(header_offset + 1, size);
}

/// Check if we need to write type info for a field type
/// Keep as constexpr for compile time evaluation or constant folding
template <typename T> inline constexpr bool need_to_write_type_for_field() {
  // This matches the Rust implementation's need_to_write_type_for_field
  // Note: Rust includes UNKNOWN, but C++ uses BOUND as a sentinel and doesn't
  // have an UNKNOWN type, so we only check for STRUCT and EXT variants
  constexpr TypeId tid = Serializer<T>::type_id;
  return tid == TypeId::STRUCT || tid == TypeId::COMPATIBLE_STRUCT ||
         tid == TypeId::NAMED_STRUCT ||
         tid == TypeId::NAMED_COMPATIBLE_STRUCT || tid == TypeId::EXT ||
         tid == TypeId::NAMED_EXT;
}

// ============================================================================
// Map Data Writing - Fast Path (Non-Polymorphic)
// ============================================================================

/// Write map data for non-polymorphic, non-shared-ref maps
/// This is the optimized fast path for common cases like map<string, int>
template <typename K, typename V, typename MapType>
inline Result<void, Error>
write_map_data_fast(const MapType &map, WriteContext &ctx, bool has_generics) {
  static_assert(!is_polymorphic_v<K> && !is_polymorphic_v<V>,
                "Fast path is for non-polymorphic types only");
  static_assert(!is_shared_ref_v<K> && !is_shared_ref_v<V>,
                "Fast path is for non-shared-ref types only");

  // Write total length
  ctx.write_varuint32(static_cast<uint32_t>(map.size()));

  if (map.empty()) {
    return Result<void, Error>();
  }

  // Determine if keys/values are declared types (no type info needed)
  const bool is_key_declared =
      has_generics && !need_to_write_type_for_field<K>();
  const bool is_val_declared =
      has_generics && !need_to_write_type_for_field<V>();

  // State for chunked writing
  size_t header_offset = 0;
  uint8_t pair_counter = 0;
  bool need_write_header = true;

  for (const auto &[key, value] : map) {
    // For fast path, we assume no null values (primitives/strings)
    // If nullability is needed, use the slow path

    if (need_write_header) {
      // Reserve space for header (1 byte) + chunk size (1 byte)
      header_offset = ctx.buffer().writer_index();
      ctx.write_uint16(0); // Placeholder for header and chunk size
      uint8_t chunk_header = 0;
      if (is_key_declared) {
        chunk_header |= DECL_KEY_TYPE;
      } else {
        FORY_RETURN_NOT_OK(write_type_info<K>(ctx));
      }
      if (is_val_declared) {
        chunk_header |= DECL_VALUE_TYPE;
      } else {
        FORY_RETURN_NOT_OK(write_type_info<V>(ctx));
      }

      // Write chunk header at reserved position
      ctx.buffer().UnsafePutByte(header_offset, chunk_header);
      need_write_header = false;
    }

    // Write key and value data
    Error error;
    if (has_generics && is_generic_type_v<K>) {
      Serializer<K>::write_data_generic(key, ctx, true, &error);
    } else {
      Serializer<K>::write_data(key, ctx, &error);
    }
    if (FORY_PREDICT_FALSE(!error.ok())) {
      return Unexpected(std::move(error));
    }

    if (has_generics && is_generic_type_v<V>) {
      Serializer<V>::write_data_generic(value, ctx, true, &error);
    } else {
      Serializer<V>::write_data(value, ctx, &error);
    }
    if (FORY_PREDICT_FALSE(!error.ok())) {
      return Unexpected(std::move(error));
    }

    pair_counter++;
    if (pair_counter == MAX_CHUNK_SIZE) {
      write_chunk_size(ctx, header_offset, pair_counter);
      pair_counter = 0;
      need_write_header = true;
    }
  }

  // Write final chunk size
  if (pair_counter > 0) {
    write_chunk_size(ctx, header_offset, pair_counter);
  }

  return Result<void, Error>();
}

// ============================================================================
// Map Data Writing - Slow Path (Polymorphic/Shared-Ref)
// ============================================================================

/// Write map data for polymorphic or shared-ref maps
/// This is the versatile slow path that handles all edge cases
template <typename K, typename V, typename MapType>
inline void write_map_data_slow(const MapType &map, WriteContext &ctx,
                                bool has_generics, Error *error) {
  // Write total length
  ctx.write_varuint32(static_cast<uint32_t>(map.size()));

  if (map.empty()) {
    return;
  }

  // Type characteristics
  constexpr bool key_is_polymorphic = is_polymorphic_v<K>;
  constexpr bool val_is_polymorphic = is_polymorphic_v<V>;
  constexpr bool key_is_shared_ref = is_shared_ref_v<K>;
  constexpr bool val_is_shared_ref = is_shared_ref_v<V>;
  constexpr bool key_needs_ref = requires_ref_metadata_v<K>;
  constexpr bool val_needs_ref = requires_ref_metadata_v<V>;

  const bool is_key_declared =
      has_generics && !need_to_write_type_for_field<K>();
  const bool is_val_declared =
      has_generics && !need_to_write_type_for_field<V>();

  // State for chunked writing
  size_t header_offset = 0;
  uint8_t pair_counter = 0;
  bool need_write_header = true;

  // Track current chunk's types for polymorphic handling
  uint32_t current_key_type_id = 0;
  uint32_t current_val_type_id = 0;

  for (const auto &[key, value] : map) {
    // Check if key or value is null (for nullable types: optional, shared_ptr,
    // unique_ptr, weak_ptr)
    bool key_is_none = false;
    bool value_is_none = false;
    if constexpr (is_nullable_v<K>) {
      key_is_none = is_null_value(key);
    }
    if constexpr (is_nullable_v<V>) {
      value_is_none = is_null_value(value);
    }

    // Handle null entries - write as separate single-entry chunks
    if (key_is_none || value_is_none) {
      // Finish current chunk if any
      if (pair_counter > 0) {
        write_chunk_size(ctx, header_offset, pair_counter);
        pair_counter = 0;
        need_write_header = true;
      }

      if (key_is_none && value_is_none) {
        ctx.write_uint8(KEY_NULL | VALUE_NULL);
        continue;
      } else if (value_is_none) {
        // Non-null key, null value
        // Java writes: chunk_header, then ref_flag, then type_info, then data
        uint8_t chunk_header = VALUE_NULL;
        bool write_ref = key_is_shared_ref || key_needs_ref;
        if (write_ref) {
          chunk_header |= TRACKING_KEY_REF;
        }
        if (is_key_declared && !key_is_polymorphic) {
          chunk_header |= DECL_KEY_TYPE;
        }
        ctx.write_uint8(chunk_header);

        // Write ref flag first if tracking refs
        if (write_ref) {
          write_not_null_ref_flag(ctx, true);
        }

        // Then write type info if not declared
        if (!(chunk_header & DECL_KEY_TYPE)) {
          if constexpr (key_is_polymorphic) {
            auto concrete_type_id = get_concrete_type_id(key);
            if (concrete_type_id ==
                std::type_index(typeid(std::shared_ptr<void>))) {
              error->set_error(
                  ErrorCode::TypeError,
                  "Polymorphic key shared_ptr must not point to void");
              return;
            }
            auto write_result = ctx.write_any_typeinfo(
                static_cast<uint32_t>(TypeId::UNKNOWN), concrete_type_id);
            if (!write_result) {
              *error = std::move(write_result.error());
              return;
            }
          } else {
            Error local_error;
            Serializer<K>::write_type_info(ctx, &local_error);
            if (FORY_PREDICT_FALSE(!local_error.ok())) {
              *error = std::move(local_error);
              return;
            }
          }
        }

        // Write key data (ref flag and type info already written)
        if (has_generics && is_generic_type_v<K>) {
          Serializer<K>::write_data_generic(key, ctx, has_generics, error);
        } else {
          Serializer<K>::write_data(key, ctx, error);
        }
        if (FORY_PREDICT_FALSE(!error->ok())) {
          return;
        }
        continue;
      } else {
        // key_is_none
        // Java writes: chunk_header, then ref_flag, then type_info, then data
        uint8_t chunk_header = KEY_NULL;
        bool write_ref = val_is_shared_ref || val_needs_ref;
        if (write_ref) {
          chunk_header |= TRACKING_VALUE_REF;
        }
        if (is_val_declared && !val_is_polymorphic) {
          chunk_header |= DECL_VALUE_TYPE;
        }
        ctx.write_uint8(chunk_header);

        // Write ref flag first if tracking refs
        if (write_ref) {
          write_not_null_ref_flag(ctx, true);
        }

        // Then write type info if not declared
        if (!(chunk_header & DECL_VALUE_TYPE)) {
          if constexpr (val_is_polymorphic) {
            auto concrete_type_id = get_concrete_type_id(value);
            if (concrete_type_id ==
                std::type_index(typeid(std::shared_ptr<void>))) {
              error->set_error(
                  ErrorCode::TypeError,
                  "Polymorphic value shared_ptr must not point to void");
              return;
            }
            auto write_result = ctx.write_any_typeinfo(
                static_cast<uint32_t>(TypeId::UNKNOWN), concrete_type_id);
            if (!write_result) {
              *error = std::move(write_result.error());
              return;
            }
          } else {
            Error local_error;
            Serializer<V>::write_type_info(ctx, &local_error);
            if (FORY_PREDICT_FALSE(!local_error.ok())) {
              *error = std::move(local_error);
              return;
            }
          }
        }

        // Write value data (ref flag and type info already written)
        if (has_generics && is_generic_type_v<V>) {
          Serializer<V>::write_data_generic(value, ctx, has_generics, error);
        } else {
          Serializer<V>::write_data(value, ctx, error);
        }
        if (FORY_PREDICT_FALSE(!error->ok())) {
          return;
        }
        continue;
      }
    }

    // Get type IDs for polymorphic types
    uint32_t key_type_id = 0;
    uint32_t val_type_id = 0;
    if constexpr (key_is_polymorphic) {
      auto concrete_type_id = get_concrete_type_id(key);
      auto key_type_info_result =
          ctx.type_resolver().get_type_info(concrete_type_id);
      if (!key_type_info_result) {
        *error = std::move(key_type_info_result.error());
        return;
      }
      key_type_id = (*key_type_info_result)->type_id;
    }
    if constexpr (val_is_polymorphic) {
      auto concrete_type_id = get_concrete_type_id(value);
      auto val_type_info_result =
          ctx.type_resolver().get_type_info(concrete_type_id);
      if (!val_type_info_result) {
        *error = std::move(val_type_info_result.error());
        return;
      }
      val_type_id = (*val_type_info_result)->type_id;
    }

    // Check if we need to start a new chunk due to type changes
    bool types_changed = false;
    if constexpr (key_is_polymorphic || val_is_polymorphic) {
      types_changed = (key_type_id != current_key_type_id) ||
                      (val_type_id != current_val_type_id);
    }

    if (need_write_header || types_changed) {
      // Finish previous chunk if types changed
      if (types_changed && pair_counter > 0) {
        write_chunk_size(ctx, header_offset, pair_counter);
        pair_counter = 0;
      }

      // Write new chunk header
      header_offset = ctx.buffer().writer_index();
      ctx.write_uint16(0); // Placeholder for header and chunk size

      uint8_t chunk_header = 0;
      // Set key flags
      if (key_is_shared_ref || key_needs_ref) {
        chunk_header |= TRACKING_KEY_REF;
      }
      if (is_key_declared && !key_is_polymorphic) {
        chunk_header |= DECL_KEY_TYPE;
      }

      // Set value flags
      if (val_is_shared_ref || val_needs_ref) {
        chunk_header |= TRACKING_VALUE_REF;
      }
      if (is_val_declared && !val_is_polymorphic) {
        chunk_header |= DECL_VALUE_TYPE;
      }

      // Write chunk header at reserved position
      ctx.buffer().UnsafePutByte(header_offset, chunk_header);

      // Write type info if needed
      // Matches Rust: write type info here in map, then call serializer with
      // write_type=false
      if (!is_key_declared || key_is_polymorphic) {
        if constexpr (key_is_polymorphic) {
          auto concrete_type_id = get_concrete_type_id(key);
          // Use UNKNOWN for polymorphic shared_ptr
          auto write_result = ctx.write_any_typeinfo(
              static_cast<uint32_t>(TypeId::UNKNOWN), concrete_type_id);
          if (!write_result) {
            *error = std::move(write_result.error());
            return;
          }
        } else {
          Error local_error;
          Serializer<K>::write_type_info(ctx, &local_error);
          if (FORY_PREDICT_FALSE(!local_error.ok())) {
            *error = std::move(local_error);
            return;
          }
        }
      }

      if (!is_val_declared || val_is_polymorphic) {
        if constexpr (val_is_polymorphic) {
          auto concrete_type_id = get_concrete_type_id(value);
          // Use UNKNOWN for polymorphic shared_ptr
          auto write_result = ctx.write_any_typeinfo(
              static_cast<uint32_t>(TypeId::UNKNOWN), concrete_type_id);
          if (!write_result) {
            *error = std::move(write_result.error());
            return;
          }
        } else {
          Error local_error;
          Serializer<V>::write_type_info(ctx, &local_error);
          if (FORY_PREDICT_FALSE(!local_error.ok())) {
            *error = std::move(local_error);
            return;
          }
        }
      }

      need_write_header = false;
      current_key_type_id = key_type_id;
      current_val_type_id = val_type_id;
    }

    // Write key-value pair
    // For polymorphic types, we've already written type info above,
    // so we write ref flag + data directly using the serializer
    if constexpr (key_is_shared_ref) {
      Serializer<K>::write(key, ctx, true, false, has_generics, error);
    } else if constexpr (key_needs_ref) {
      Serializer<K>::write(key, ctx, true, false, false, error);
    } else {
      if (has_generics && is_generic_type_v<K>) {
        Serializer<K>::write_data_generic(key, ctx, has_generics, error);
      } else {
        Serializer<K>::write_data(key, ctx, error);
      }
    }
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return;
    }

    if constexpr (val_is_shared_ref) {
      Serializer<V>::write(value, ctx, true, false, has_generics, error);
    } else if constexpr (val_needs_ref) {
      Serializer<V>::write(value, ctx, true, false, false, error);
    } else {
      if (has_generics && is_generic_type_v<V>) {
        Serializer<V>::write_data_generic(value, ctx, has_generics, error);
      } else {
        Serializer<V>::write_data(value, ctx, error);
      }
    }
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return;
    }

    pair_counter++;
    if (pair_counter == MAX_CHUNK_SIZE) {
      write_chunk_size(ctx, header_offset, pair_counter);
      pair_counter = 0;
      need_write_header = true;
      current_key_type_id = 0;
      current_val_type_id = 0;
    }
  }

  // Write final chunk size
  if (pair_counter > 0) {
    write_chunk_size(ctx, header_offset, pair_counter);
  }
}

// ============================================================================
// Map Data Reading - Fast Path (Non-Polymorphic)
// ============================================================================

/// Read map data for non-polymorphic, non-shared-ref maps
template <typename K, typename V, typename MapType>
inline MapType read_map_data_fast(ReadContext &ctx, uint32_t length,
                                  Error *error) {
  static_assert(!is_polymorphic_v<K> && !is_polymorphic_v<V>,
                "Fast path is for non-polymorphic types only");
  static_assert(!is_shared_ref_v<K> && !is_shared_ref_v<V>,
                "Fast path is for non-shared-ref types only");

  MapType result;
  MapReserver<MapType>::reserve(result, length);

  if (length == 0) {
    return result;
  }

  uint32_t len_counter = 0;

  while (len_counter < length) {
    uint8_t header = ctx.read_uint8(error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return MapType();
    }

    // Handle null entries (shouldn't happen in fast path, but be defensive)
    if ((header & KEY_NULL) && (header & VALUE_NULL)) {
      // Both null - skip for now (would need default values)
      len_counter++;
      continue;
    }
    if (header & KEY_NULL) {
      // Null key - read value and skip
      Serializer<V>::read(ctx, false, false, error);
      if (FORY_PREDICT_FALSE(!error->ok())) {
        return MapType();
      }
      len_counter++;
      continue;
    }
    if (header & VALUE_NULL) {
      // Null value - read key and skip
      Serializer<K>::read(ctx, false, false, error);
      if (FORY_PREDICT_FALSE(!error->ok())) {
        return MapType();
      }
      len_counter++;
      continue;
    }

    // Read chunk size
    uint8_t chunk_size = ctx.read_uint8(error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return MapType();
    }

    // Read type info if not declared
    if (!(header & DECL_KEY_TYPE)) {
      auto result = read_type_info<K>(ctx);
      if (!result) {
        *error = std::move(result.error());
        return MapType();
      }
    }
    if (!(header & DECL_VALUE_TYPE)) {
      auto result = read_type_info<V>(ctx);
      if (!result) {
        *error = std::move(result.error());
        return MapType();
      }
    }

    uint32_t cur_len = len_counter + chunk_size;
    if (cur_len > length) {
      error->set_error(ErrorCode::InvalidData,
                       "Chunk size exceeds total map length");
      return MapType();
    }

    // Read chunk_size pairs
    for (uint8_t i = 0; i < chunk_size; ++i) {
      K key = Serializer<K>::read_data(ctx, error);
      if (FORY_PREDICT_FALSE(!error->ok())) {
        return MapType();
      }
      V value = Serializer<V>::read_data(ctx, error);
      if (FORY_PREDICT_FALSE(!error->ok())) {
        return MapType();
      }
      result.emplace(std::move(key), std::move(value));
    }

    len_counter += chunk_size;
  }

  return result;
}

// ============================================================================
// Map Data Reading - Slow Path (Polymorphic/Shared-Ref)
// ============================================================================

/// Read map data for polymorphic or shared-ref maps
template <typename K, typename V, typename MapType>
inline MapType read_map_data_slow(ReadContext &ctx, uint32_t length,
                                  Error *error) {
  MapType result;
  MapReserver<MapType>::reserve(result, length);

  if (length == 0) {
    return result;
  }

  constexpr bool key_is_polymorphic = is_polymorphic_v<K>;
  constexpr bool val_is_polymorphic = is_polymorphic_v<V>;
  constexpr bool key_is_shared_ref = is_shared_ref_v<K>;
  constexpr bool val_is_shared_ref = is_shared_ref_v<V>;

  uint32_t len_counter = 0;

  while (len_counter < length) {
    uint8_t header = ctx.read_uint8(error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return MapType();
    }

    // Handle null entries
    if ((header & KEY_NULL) && (header & VALUE_NULL)) {
      // Both key and value are null - insert with default-constructed values
      result.emplace(K{}, V{});
      len_counter++;
      continue;
    }

    if (header & KEY_NULL) {
      // Null key, non-null value
      // Java writes: chunk_header, then ref_flag, then type_info, then data
      bool track_value_ref = (header & TRACKING_VALUE_REF) != 0;
      bool value_declared = (header & DECL_VALUE_TYPE) != 0;

      // Consume ref flag first if tracking refs
      bool has_value = true;
      if (track_value_ref || val_is_shared_ref) {
        has_value = consume_ref_flag(ctx, true, error);
        if (FORY_PREDICT_FALSE(!error->ok())) {
          return MapType();
        }
      }

      if (!has_value) {
        // Value is null reference
        result.emplace(K{}, V{});
        len_counter++;
        continue;
      }

      // Now read type info if needed
      const TypeInfo *value_type_info = nullptr;
      if (!value_declared || val_is_polymorphic) {
        if constexpr (val_is_polymorphic) {
          auto type_info_result = read_polymorphic_type_info(ctx);
          if (!type_info_result) {
            *error = std::move(type_info_result.error());
            return MapType();
          }
          value_type_info = *type_info_result;
        } else {
          auto result = read_type_info<V>(ctx);
          if (!result) {
            *error = std::move(result.error());
            return MapType();
          }
        }
      }

      // Read value data (ref flag already consumed above)
      V value;
      if constexpr (val_is_polymorphic) {
        // For polymorphic types, use read_with_type_info
        value = Serializer<V>::read_with_type_info(ctx, false, *value_type_info,
                                                   error);
        if (FORY_PREDICT_FALSE(!error->ok())) {
          return MapType();
        }
      } else {
        // Read data directly - ref flag already consumed
        value = Serializer<V>::read_data(ctx, error);
        if (FORY_PREDICT_FALSE(!error->ok())) {
          return MapType();
        }
      }
      // Insert with default-constructed key and the read value
      result.emplace(K{}, std::move(value));
      len_counter++;
      continue;
    }

    if (header & VALUE_NULL) {
      // Non-null key, null value
      // Java writes: chunk_header, then ref_flag, then type_info, then data
      bool track_key_ref = (header & TRACKING_KEY_REF) != 0;
      bool key_declared = (header & DECL_KEY_TYPE) != 0;

      // Consume ref flag first if tracking refs
      bool has_key = true;
      if (track_key_ref || key_is_shared_ref) {
        has_key = consume_ref_flag(ctx, true, error);
        if (FORY_PREDICT_FALSE(!error->ok())) {
          return MapType();
        }
      }

      if (!has_key) {
        // Key is null reference
        result.emplace(K{}, V{});
        len_counter++;
        continue;
      }

      // Now read type info if needed
      const TypeInfo *key_type_info = nullptr;
      if (!key_declared || key_is_polymorphic) {
        if constexpr (key_is_polymorphic) {
          auto type_info_result = read_polymorphic_type_info(ctx);
          if (!type_info_result) {
            *error = std::move(type_info_result.error());
            return MapType();
          }
          key_type_info = *type_info_result;
        } else {
          auto result = read_type_info<K>(ctx);
          if (!result) {
            *error = std::move(result.error());
            return MapType();
          }
        }
      }

      // Read key data (ref flag already consumed above)
      K key;
      if constexpr (key_is_polymorphic) {
        key =
            Serializer<K>::read_with_type_info(ctx, false, *key_type_info, error);
        if (FORY_PREDICT_FALSE(!error->ok())) {
          return MapType();
        }
      } else {
        // Read data directly - ref flag already consumed
        key = Serializer<K>::read_data(ctx, error);
        if (FORY_PREDICT_FALSE(!error->ok())) {
          return MapType();
        }
      }
      // Insert with the read key and default-constructed value
      result.emplace(std::move(key), V{});
      len_counter++;
      continue;
    }

    // Non-null key and value chunk
    uint8_t chunk_size = ctx.read_uint8(error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return MapType();
    }
    bool key_declared = (header & DECL_KEY_TYPE) != 0;
    bool value_declared = (header & DECL_VALUE_TYPE) != 0;
    bool track_key_ref = (header & TRACKING_KEY_REF) != 0;
    bool track_value_ref = (header & TRACKING_VALUE_REF) != 0;

    // Read type info if not declared
    const TypeInfo *key_type_info = nullptr;
    const TypeInfo *value_type_info = nullptr;

    if (!key_declared || key_is_polymorphic) {
      if constexpr (key_is_polymorphic) {
        auto type_info_result = read_polymorphic_type_info(ctx);
        if (!type_info_result) {
          *error = std::move(type_info_result.error());
          return MapType();
        }
        key_type_info = *type_info_result;
      } else {
        auto result = read_type_info<K>(ctx);
        if (!result) {
          *error = std::move(result.error());
          return MapType();
        }
      }
    }
    if (!value_declared || val_is_polymorphic) {
      if constexpr (val_is_polymorphic) {
        auto type_info_result = read_polymorphic_type_info(ctx);
        if (!type_info_result) {
          *error = std::move(type_info_result.error());
          return MapType();
        }
        value_type_info = *type_info_result;
      } else {
        auto result = read_type_info<V>(ctx);
        if (!result) {
          *error = std::move(result.error());
          return MapType();
        }
      }
    }

    uint32_t cur_len = len_counter + chunk_size;
    if (cur_len > length) {
      error->set_error(ErrorCode::InvalidData,
                       "Chunk size exceeds total map length");
      return MapType();
    }

    // Read chunk_size pairs
    // NOTE: Only shared_ref and track_*_ref determine if ref flag was written
    // The requires_ref_metadata trait is for top-level serialization, NOT for
    // values inside map chunks. Map entries use write_data (no ref flag) unless
    // the type is shared_ref or track_*_ref was set.
    bool key_read_ref = key_is_shared_ref || track_key_ref;
    bool val_read_ref = val_is_shared_ref || track_value_ref;

    for (uint8_t i = 0; i < chunk_size; ++i) {
      // Read key - use type info if available (polymorphic case)
      K key;
      if constexpr (key_is_polymorphic) {
        key = Serializer<K>::read_with_type_info(ctx, key_read_ref,
                                                 *key_type_info, error);
        if (FORY_PREDICT_FALSE(!error->ok())) {
          return MapType();
        }
      } else if (key_read_ref) {
        key = Serializer<K>::read(ctx, key_read_ref, false, error);
        if (FORY_PREDICT_FALSE(!error->ok())) {
          return MapType();
        }
      } else {
        // No ref flag - read data directly
        key = Serializer<K>::read_data(ctx, error);
        if (FORY_PREDICT_FALSE(!error->ok())) {
          return MapType();
        }
      }

      // Read value - use type info if available (polymorphic case)
      V value;
      if constexpr (val_is_polymorphic) {
        value = Serializer<V>::read_with_type_info(ctx, val_read_ref,
                                                   *value_type_info, error);
        if (FORY_PREDICT_FALSE(!error->ok())) {
          return MapType();
        }
      } else if (val_read_ref) {
        value = Serializer<V>::read(ctx, val_read_ref, false, error);
        if (FORY_PREDICT_FALSE(!error->ok())) {
          return MapType();
        }
      } else {
        // No ref flag - read data directly
        value = Serializer<V>::read_data(ctx, error);
        if (FORY_PREDICT_FALSE(!error->ok())) {
          return MapType();
        }
      }

      result.emplace(std::move(key), std::move(value));
    }

    len_counter += chunk_size;
  }

  return result;
}

// ============================================================================
// std::map serializer
// ============================================================================

template <typename K, typename V, typename... Args>
struct Serializer<std::map<K, V, Args...>> {
  static constexpr TypeId type_id = TypeId::MAP;

  static inline void write_type_info(WriteContext &ctx, Error *error) {
    (void)error;
    ctx.write_varuint32(static_cast<uint32_t>(type_id));
  }

  static inline void read_type_info(ReadContext &ctx, Error *error) {
    auto type_info_result = ctx.read_any_typeinfo();
    if (!type_info_result) {
      *error = std::move(type_info_result.error());
      return;
    }
    const TypeInfo *type_info = *type_info_result;
    if (!type_id_matches(type_info->type_id, static_cast<uint32_t>(type_id))) {
      error->set_error(
          ErrorCode::TypeError,
          Error::type_mismatch(type_info->type_id, static_cast<uint32_t>(type_id))
              .message());
    }
  }

  // Match Rust signature: fory_write(&self, context, write_ref_info,
  // write_type_info, has_generics)
  static inline void write(const std::map<K, V, Args...> &map,
                           WriteContext &ctx, bool write_ref, bool write_type,
                           bool has_generics, Error *error) {
    write_not_null_ref_flag(ctx, write_ref);

    if (write_type) {
      ctx.write_varuint32(static_cast<uint32_t>(type_id));
    }

    write_data_generic(map, ctx, has_generics, error);
  }

  static inline void write_data(const std::map<K, V, Args...> &map,
                                WriteContext &ctx, Error *error) {
    constexpr bool is_fast_path =
        !is_polymorphic_v<K> && !is_polymorphic_v<V> && !is_shared_ref_v<K> &&
        !is_shared_ref_v<V> && !requires_ref_metadata_v<K> &&
        !requires_ref_metadata_v<V>;

    if constexpr (is_fast_path) {
      auto result = write_map_data_fast<K, V>(map, ctx, false);
      if (!result) {
        *error = std::move(result.error());
      }
    } else {
      write_map_data_slow<K, V>(map, ctx, true, error);
    }
  }

  static inline void write_data_generic(const std::map<K, V, Args...> &map,
                                        WriteContext &ctx, bool has_generics,
                                        Error *error) {
    constexpr bool is_fast_path =
        !is_polymorphic_v<K> && !is_polymorphic_v<V> && !is_shared_ref_v<K> &&
        !is_shared_ref_v<V> && !requires_ref_metadata_v<K> &&
        !requires_ref_metadata_v<V>;

    if constexpr (is_fast_path) {
      auto result = write_map_data_fast<K, V>(map, ctx, has_generics);
      if (!result) {
        *error = std::move(result.error());
      }
    } else {
      write_map_data_slow<K, V>(map, ctx, has_generics, error);
    }
  }

  static inline std::map<K, V, Args...> read(ReadContext &ctx, bool read_ref,
                                             bool read_type, Error *error) {
    bool has_value = consume_ref_flag(ctx, read_ref, error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return std::map<K, V, Args...>();
    }
    if (!has_value) {
      return std::map<K, V, Args...>();
    }
    if (read_type) {
      uint32_t type_id_read = ctx.read_varuint32(error);
      if (FORY_PREDICT_FALSE(!error->ok())) {
        return std::map<K, V, Args...>();
      }
      if (type_id_read != static_cast<uint32_t>(type_id)) {
        error->set_error(
            ErrorCode::TypeError,
            "Type mismatch: expected " +
                std::to_string(static_cast<uint32_t>(type_id)) + " but got " +
                std::to_string(type_id_read));
        return std::map<K, V, Args...>();
      }
    }

    uint32_t length = ctx.read_varuint32(error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return std::map<K, V, Args...>();
    }

    constexpr bool is_fast_path =
        !is_polymorphic_v<K> && !is_polymorphic_v<V> && !is_shared_ref_v<K> &&
        !is_shared_ref_v<V> && !requires_ref_metadata_v<K> &&
        !requires_ref_metadata_v<V>;

    if constexpr (is_fast_path) {
      return read_map_data_fast<K, V, std::map<K, V, Args...>>(ctx, length,
                                                               error);
    } else {
      return read_map_data_slow<K, V, std::map<K, V, Args...>>(ctx, length,
                                                               error);
    }
  }

  static inline std::map<K, V, Args...>
  read_with_type_info(ReadContext &ctx, bool read_ref, const TypeInfo &type_info,
                      Error *error) {
    // Type info already validated, skip redundant type read
    return read(ctx, read_ref, false, error); // read_type=false
  }

  static inline std::map<K, V, Args...> read_data(ReadContext &ctx,
                                                  Error *error) {
    uint32_t length = ctx.read_varuint32(error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return std::map<K, V, Args...>();
    }

    constexpr bool is_fast_path =
        !is_polymorphic_v<K> && !is_polymorphic_v<V> && !is_shared_ref_v<K> &&
        !is_shared_ref_v<V> && !requires_ref_metadata_v<K> &&
        !requires_ref_metadata_v<V>;

    if constexpr (is_fast_path) {
      return read_map_data_fast<K, V, std::map<K, V, Args...>>(ctx, length,
                                                               error);
    } else {
      return read_map_data_slow<K, V, std::map<K, V, Args...>>(ctx, length,
                                                               error);
    }
  }
};

// ============================================================================
// std::unordered_map serializer
// ============================================================================

template <typename K, typename V, typename... Args>
struct Serializer<std::unordered_map<K, V, Args...>> {
  static constexpr TypeId type_id = TypeId::MAP;

  static inline void write(const std::unordered_map<K, V, Args...> &map,
                           WriteContext &ctx, bool write_ref, bool write_type,
                           bool has_generics, Error *error) {
    write_not_null_ref_flag(ctx, write_ref);

    if (write_type) {
      ctx.write_varuint32(static_cast<uint32_t>(type_id));
    }

    write_data_generic(map, ctx, has_generics, error);
  }

  static inline void write_data(const std::unordered_map<K, V, Args...> &map,
                                WriteContext &ctx, Error *error) {
    constexpr bool is_fast_path =
        !is_polymorphic_v<K> && !is_polymorphic_v<V> && !is_shared_ref_v<K> &&
        !is_shared_ref_v<V> && !requires_ref_metadata_v<K> &&
        !requires_ref_metadata_v<V>;

    if constexpr (is_fast_path) {
      auto result = write_map_data_fast<K, V>(map, ctx, false);
      if (!result) {
        *error = std::move(result.error());
      }
    } else {
      write_map_data_slow<K, V>(map, ctx, true, error);
    }
  }

  static inline void
  write_data_generic(const std::unordered_map<K, V, Args...> &map,
                     WriteContext &ctx, bool has_generics, Error *error) {
    constexpr bool is_fast_path =
        !is_polymorphic_v<K> && !is_polymorphic_v<V> && !is_shared_ref_v<K> &&
        !is_shared_ref_v<V> && !requires_ref_metadata_v<K> &&
        !requires_ref_metadata_v<V>;

    if constexpr (is_fast_path) {
      auto result = write_map_data_fast<K, V>(map, ctx, has_generics);
      if (!result) {
        *error = std::move(result.error());
      }
    } else {
      write_map_data_slow<K, V>(map, ctx, has_generics, error);
    }
  }

  static inline std::unordered_map<K, V, Args...>
  read(ReadContext &ctx, bool read_ref, bool read_type, Error *error) {
    bool has_value = consume_ref_flag(ctx, read_ref, error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return std::unordered_map<K, V, Args...>();
    }
    if (!has_value) {
      return std::unordered_map<K, V, Args...>();
    }
    if (read_type) {
      uint32_t type_id_read = ctx.read_varuint32(error);
      if (FORY_PREDICT_FALSE(!error->ok())) {
        return std::unordered_map<K, V, Args...>();
      }
      if (type_id_read != static_cast<uint32_t>(type_id)) {
        error->set_error(
            ErrorCode::TypeError,
            "Type mismatch: expected " +
                std::to_string(static_cast<uint32_t>(type_id)) + " but got " +
                std::to_string(type_id_read));
        return std::unordered_map<K, V, Args...>();
      }
    }

    uint32_t length = ctx.read_varuint32(error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return std::unordered_map<K, V, Args...>();
    }

    constexpr bool is_fast_path =
        !is_polymorphic_v<K> && !is_polymorphic_v<V> && !is_shared_ref_v<K> &&
        !is_shared_ref_v<V> && !requires_ref_metadata_v<K> &&
        !requires_ref_metadata_v<V>;

    if constexpr (is_fast_path) {
      return read_map_data_fast<K, V, std::unordered_map<K, V, Args...>>(
          ctx, length, error);
    } else {
      return read_map_data_slow<K, V, std::unordered_map<K, V, Args...>>(
          ctx, length, error);
    }
  }

  static inline std::unordered_map<K, V, Args...>
  read_with_type_info(ReadContext &ctx, bool read_ref, const TypeInfo &type_info,
                      Error *error) {
    // Type info already validated, skip redundant type read
    return read(ctx, read_ref, false, error); // read_type=false
  }

  static inline std::unordered_map<K, V, Args...> read_data(ReadContext &ctx,
                                                            Error *error) {
    uint32_t length = ctx.read_varuint32(error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return std::unordered_map<K, V, Args...>();
    }

    constexpr bool is_fast_path =
        !is_polymorphic_v<K> && !is_polymorphic_v<V> && !is_shared_ref_v<K> &&
        !is_shared_ref_v<V> && !requires_ref_metadata_v<K> &&
        !requires_ref_metadata_v<V>;

    if constexpr (is_fast_path) {
      return read_map_data_fast<K, V, std::unordered_map<K, V, Args...>>(
          ctx, length, error);
    } else {
      return read_map_data_slow<K, V, std::unordered_map<K, V, Args...>>(
          ctx, length, error);
    }
  }
};

} // namespace serialization
} // namespace fory
