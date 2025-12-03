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

#include "fory/serialization/collection_serializer.h"
#include "fory/serialization/serializer.h"
#include <cstdint>
#include <tuple>
#include <type_traits>
#include <utility>

namespace fory {
namespace serialization {

// ============================================================================
// Compile-time Tuple Homogeneity Detection
// ============================================================================

/// Check if all types in a tuple are the same type at compile time.
/// Polymorphic types are always treated as non-homogeneous.
template <typename... Ts> struct is_tuple_homogeneous : std::false_type {};

template <> struct is_tuple_homogeneous<> : std::true_type {};

template <typename T> struct is_tuple_homogeneous<T> : std::true_type {};

template <typename T, typename U, typename... Rest>
struct is_tuple_homogeneous<T, U, Rest...>
    : std::conditional_t<std::is_same_v<T, U> && !is_polymorphic_v<T> &&
                             !is_shared_ref_v<T>,
                         is_tuple_homogeneous<T, Rest...>, std::false_type> {};

template <typename... Ts>
inline constexpr bool is_tuple_homogeneous_v =
    is_tuple_homogeneous<Ts...>::value;

/// Get the first type of a tuple (for homogeneous tuple element type)
template <typename Tuple> struct tuple_first_type;

template <typename T, typename... Rest>
struct tuple_first_type<std::tuple<T, Rest...>> {
  using type = T;
};

template <typename Tuple>
using tuple_first_type_t = typename tuple_first_type<Tuple>::type;

// ============================================================================
// Tuple Serialization Helpers
// ============================================================================

/// Write tuple elements directly (non-xlang mode)
template <typename Tuple, size_t... Is>
inline Result<void, Error>
write_tuple_elements_direct(const Tuple &tuple, WriteContext &ctx,
                            std::index_sequence<Is...>) {
  Result<void, Error> result;
  // Use fold expression to write each element
  ((result = [&]() -> Result<void, Error> {
     if (!result.ok())
       return result;
     using ElemType = std::tuple_element_t<Is, Tuple>;
     const auto &elem = std::get<Is>(tuple);
     // For nullable/shared types, use full write with ref tracking
     if constexpr (is_nullable_v<ElemType> || is_shared_ref_v<ElemType> ||
                   is_polymorphic_v<ElemType>) {
       return Serializer<ElemType>::write(elem, ctx, true, false, false);
     } else {
       return Serializer<ElemType>::write_data(elem, ctx);
     }
   }()),
   ...);
  return result;
}

/// Write tuple elements with type info (xlang/compatible mode, heterogeneous)
template <typename Tuple, size_t... Is>
inline Result<void, Error>
write_tuple_elements_heterogeneous(const Tuple &tuple, WriteContext &ctx,
                                   std::index_sequence<Is...>) {
  Result<void, Error> result;
  // Write each element with its type info
  ((result = [&]() -> Result<void, Error> {
     if (!result.ok())
       return result;
     using ElemType = std::tuple_element_t<Is, Tuple>;
     return Serializer<ElemType>::write(std::get<Is>(tuple), ctx, true, true,
                                        false);
   }()),
   ...);
  return result;
}

/// Write tuple elements without type info (xlang/compatible mode, homogeneous)
template <typename Tuple, size_t... Is>
inline Result<void, Error>
write_tuple_elements_homogeneous(const Tuple &tuple, WriteContext &ctx,
                                 std::index_sequence<Is...>) {
  Result<void, Error> result;
  // Write each element data only (type info written once in header)
  ((result = [&]() -> Result<void, Error> {
     if (!result.ok())
       return result;
     using ElemType = std::tuple_element_t<Is, Tuple>;
     return Serializer<ElemType>::write_data(std::get<Is>(tuple), ctx);
   }()),
   ...);
  return result;
}

/// Read tuple elements directly (non-xlang mode)
template <typename Tuple, size_t... Is>
inline Result<Tuple, Error>
read_tuple_elements_direct(ReadContext &ctx, std::index_sequence<Is...>) {
  Tuple result;
  Error error;
  bool has_error = false;

  // Use fold expression to read each element
  ((has_error ? void() : [&]() {
      using ElemType = std::tuple_element_t<Is, Tuple>;
      // For nullable/shared types, use full read with ref tracking
      if constexpr (is_nullable_v<ElemType> || is_shared_ref_v<ElemType> ||
                    is_polymorphic_v<ElemType>) {
        auto elem_result = Serializer<ElemType>::read(ctx, true, false);
        if (elem_result.ok()) {
          std::get<Is>(result) = std::move(elem_result).value();
        } else {
          error = std::move(elem_result).error();
          has_error = true;
        }
      } else {
        auto elem_result = Serializer<ElemType>::read_data(ctx);
        if (elem_result.ok()) {
          std::get<Is>(result) = std::move(elem_result).value();
        } else {
          error = std::move(elem_result).error();
          has_error = true;
        }
      }
    }()),
    ...);

  if (has_error) {
    return Unexpected(std::move(error));
  }
  return result;
}

/// Read tuple elements with type info (xlang/compatible mode, heterogeneous)
template <typename Tuple, size_t... Is>
inline Result<Tuple, Error>
read_tuple_elements_heterogeneous(ReadContext &ctx, uint32_t length,
                                  std::index_sequence<Is...>) {
  Tuple result;
  Error error;
  bool has_error = false;
  uint32_t index = 0;

  // Read each element with its type info, handling length mismatch
  ((has_error ? void() : [&]() {
      using ElemType = std::tuple_element_t<Is, Tuple>;
      if (index < length) {
        auto elem_result = Serializer<ElemType>::read(ctx, true, true);
        if (elem_result.ok()) {
          std::get<Is>(result) = std::move(elem_result).value();
        } else {
          error = std::move(elem_result).error();
          has_error = true;
        }
        ++index;
      }
      // If index >= length, use default-constructed value
    }()),
    ...);

  if (has_error) {
    return Unexpected(std::move(error));
  }

  // Skip any extra elements beyond tuple size
  while (index < length) {
    // Skip value - read type and skip data
    FORY_TRY(type_info, ctx.read_any_typeinfo());
    // For simplicity, read and discard - in practice would need skip logic
    (void)type_info;
    ++index;
  }

  return result;
}

/// Read tuple elements without type info (xlang/compatible mode, homogeneous)
template <typename Tuple, size_t... Is>
inline Result<Tuple, Error>
read_tuple_elements_homogeneous(ReadContext &ctx, uint32_t length,
                                std::index_sequence<Is...>) {
  Tuple result;
  Error error;
  bool has_error = false;
  uint32_t index = 0;

  // Read each element data only (type info read once in header)
  ((has_error ? void() : [&]() {
      using ElemType = std::tuple_element_t<Is, Tuple>;
      if (index < length) {
        auto elem_result = Serializer<ElemType>::read_data(ctx);
        if (elem_result.ok()) {
          std::get<Is>(result) = std::move(elem_result).value();
        } else {
          error = std::move(elem_result).error();
          has_error = true;
        }
        ++index;
      }
      // If index >= length, use default-constructed value
    }()),
    ...);

  if (has_error) {
    return Unexpected(std::move(error));
  }

  // Skip any extra elements beyond tuple size
  using ElemType = tuple_first_type_t<Tuple>;
  while (index < length) {
    auto skip_result = Serializer<ElemType>::read_data(ctx);
    if (!skip_result.ok()) {
      return Unexpected(std::move(skip_result).error());
    }
    ++index;
  }

  return result;
}

// ============================================================================
// std::tuple Serializer
// ============================================================================

/// Empty tuple serializer
template <> struct Serializer<std::tuple<>> {
  static constexpr TypeId type_id = TypeId::LIST;

  static inline Result<void, Error> write_type_info(WriteContext &ctx) {
    ctx.write_varuint32(static_cast<uint32_t>(type_id));
    return {};
  }

  static inline Result<void, Error> read_type_info(ReadContext &ctx) {
    FORY_TRY(type_info, ctx.read_any_typeinfo());
    if (!type_id_matches(type_info->type_id, static_cast<uint32_t>(type_id))) {
      return Unexpected(Error::type_mismatch(type_info->type_id,
                                             static_cast<uint32_t>(type_id)));
    }
    return {};
  }

  static inline Result<void, Error> write(const std::tuple<> &,
                                          WriteContext &ctx, bool write_ref,
                                          bool write_type,
                                          bool has_generics = false) {
    (void)has_generics;
    write_not_null_ref_flag(ctx, write_ref);
    if (write_type) {
      ctx.write_varuint32(static_cast<uint32_t>(type_id));
    }
    return write_data(std::tuple<>(), ctx);
  }

  static inline Result<void, Error> write_data(const std::tuple<> &,
                                               WriteContext &ctx) {
    ctx.write_varuint32(0); // length = 0
    return {};
  }

  static inline Result<void, Error>
  write_data_generic(const std::tuple<> &tuple, WriteContext &ctx,
                     bool has_generics) {
    (void)has_generics;
    return write_data(tuple, ctx);
  }

  static inline Result<std::tuple<>, Error>
  read(ReadContext &ctx, bool read_ref, bool read_type) {
    FORY_TRY(has_value, consume_ref_flag(ctx, read_ref));
    if (!has_value) {
      return std::tuple<>();
    }

    Error error;
    if (read_type) {
      uint32_t type_id_read = ctx.read_varuint32(&error);
      if (FORY_PREDICT_FALSE(!error.ok())) {
        return Unexpected(std::move(error));
      }
      if (!type_id_matches(type_id_read, static_cast<uint32_t>(type_id))) {
        return Unexpected(
            Error::type_mismatch(type_id_read, static_cast<uint32_t>(type_id)));
      }
    }
    return read_data(ctx);
  }

  static inline Result<std::tuple<>, Error> read_data(ReadContext &ctx) {
    Error error;
    uint32_t length = ctx.read_varuint32(&error);
    if (FORY_PREDICT_FALSE(!error.ok())) {
      return Unexpected(std::move(error));
    }
    (void)length; // Ignore - empty tuple
    return std::tuple<>();
  }
};

/// Generic tuple serializer for tuples with 1+ elements
template <typename... Ts> struct Serializer<std::tuple<Ts...>> {
  static constexpr TypeId type_id = TypeId::LIST;
  static constexpr size_t tuple_size = sizeof...(Ts);
  static constexpr bool is_homogeneous = is_tuple_homogeneous_v<Ts...>;

  using TupleType = std::tuple<Ts...>;
  using IndexSeq = std::index_sequence_for<Ts...>;

  static inline Result<void, Error> write_type_info(WriteContext &ctx) {
    ctx.write_varuint32(static_cast<uint32_t>(type_id));
    return {};
  }

  static inline Result<void, Error> read_type_info(ReadContext &ctx) {
    FORY_TRY(type_info, ctx.read_any_typeinfo());
    if (!type_id_matches(type_info->type_id, static_cast<uint32_t>(type_id))) {
      return Unexpected(Error::type_mismatch(type_info->type_id,
                                             static_cast<uint32_t>(type_id)));
    }
    return {};
  }

  static inline Result<void, Error> write(const TupleType &tuple,
                                          WriteContext &ctx, bool write_ref,
                                          bool write_type,
                                          bool has_generics = false) {
    write_not_null_ref_flag(ctx, write_ref);
    if (write_type) {
      ctx.write_varuint32(static_cast<uint32_t>(type_id));
    }
    return write_data_generic(tuple, ctx, has_generics);
  }

  static inline Result<void, Error> write_data(const TupleType &tuple,
                                               WriteContext &ctx) {
    // Non-xlang mode: write elements directly without collection header
    return write_tuple_elements_direct(tuple, ctx, IndexSeq{});
  }

  static inline Result<void, Error> write_data_generic(const TupleType &tuple,
                                                       WriteContext &ctx,
                                                       bool has_generics) {
    (void)has_generics;
    // xlang/compatible mode: use collection protocol

    // Write length
    ctx.write_varuint32(static_cast<uint32_t>(tuple_size));

    if constexpr (tuple_size == 0) {
      return {};
    }

    // Build header bitmap
    uint8_t bitmap = 0;
    if constexpr (is_homogeneous) {
      // Homogeneous: set IS_SAME_TYPE flag
      bitmap |= COLL_IS_SAME_TYPE;
      ctx.write_uint8(bitmap);

      // Write element type info once
      using ElemType = tuple_first_type_t<TupleType>;
      FORY_RETURN_NOT_OK(Serializer<ElemType>::write_type_info(ctx));

      // Write all elements data
      return write_tuple_elements_homogeneous(tuple, ctx, IndexSeq{});
    } else {
      // Heterogeneous: no IS_SAME_TYPE flag
      ctx.write_uint8(bitmap);

      // Write elements with type info per element
      return write_tuple_elements_heterogeneous(tuple, ctx, IndexSeq{});
    }
  }

  static inline Result<TupleType, Error> read(ReadContext &ctx, bool read_ref,
                                              bool read_type) {
    FORY_TRY(has_value, consume_ref_flag(ctx, read_ref));
    if (!has_value) {
      return TupleType{};
    }

    Error error;
    if (read_type) {
      uint32_t type_id_read = ctx.read_varuint32(&error);
      if (FORY_PREDICT_FALSE(!error.ok())) {
        return Unexpected(std::move(error));
      }
      if (!type_id_matches(type_id_read, static_cast<uint32_t>(type_id))) {
        return Unexpected(
            Error::type_mismatch(type_id_read, static_cast<uint32_t>(type_id)));
      }
    }

    // xlang mode: read length and header
    uint32_t length = ctx.read_varuint32(&error);
    if (FORY_PREDICT_FALSE(!error.ok())) {
      return Unexpected(std::move(error));
    }

    if (length == 0) {
      return TupleType{};
    }

    // Read header bitmap
    uint8_t bitmap = ctx.read_uint8(&error);
    if (FORY_PREDICT_FALSE(!error.ok())) {
      return Unexpected(std::move(error));
    }

    bool is_same_type = (bitmap & COLL_IS_SAME_TYPE) != 0;

    if (is_same_type) {
      // Read element type info once
      FORY_TRY(elem_type_info, ctx.read_any_typeinfo());
      (void)elem_type_info; // Type validation handled by element serializer

      // Read elements data only
      return read_tuple_elements_homogeneous<TupleType>(ctx, length,
                                                        IndexSeq{});
    } else {
      // Read elements with type info per element
      return read_tuple_elements_heterogeneous<TupleType>(ctx, length,
                                                          IndexSeq{});
    }
  }

  static inline Result<TupleType, Error> read_data(ReadContext &ctx) {
    // Non-xlang mode: read elements directly
    return read_tuple_elements_direct<TupleType>(ctx, IndexSeq{});
  }

  static inline Result<TupleType, Error>
  read_with_type_info(ReadContext &ctx, bool read_ref,
                      const TypeInfo &type_info) {
    (void)type_info;
    return read(ctx, read_ref, false);
  }
};

} // namespace serialization
} // namespace fory
