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

#include "fory/serialization/skip.h"
#include "fory/serialization/ref_resolver.h"
#include "fory/serialization/serializer.h"

namespace fory {
namespace serialization {

namespace {

// Compute RefMode from field type at runtime.
//
// Per xlang protocol and Java's ObjectSerializer.write_other_field_value:
// - In xlang mode with ref=false (default), fields only write
//   ref/null flag if they are nullable
// - Primitives never have ref flags (handled separately)

FieldType make_unknown_field_type() {
  FieldType field_type;
  field_type.type_id = static_cast<uint32_t>(TypeId::UNKNOWN);
  field_type.nullable = false;
  field_type.ref_mode = RefMode::None;
  return field_type;
}

FieldType make_field_type(const TypeInfo &type_info) {
  FieldType field_type;
  field_type.type_id = type_info.type_id;
  field_type.nullable = false;
  field_type.ref_mode = RefMode::None;
  return field_type;
}

bool skip_ref_or_null(ReadContext &ctx, RefMode ref_mode) {
  if (ref_mode == RefMode::None) {
    return false;
  }

  int8_t ref_flag = ctx.read_int8(ctx.error());
  if (FORY_PREDICT_FALSE(ctx.has_error())) {
    return true;
  }

  if (ref_flag == NULL_FLAG) {
    return true;
  }

  if (ref_mode == RefMode::Tracking) {
    if (ref_flag == REF_FLAG) {
      (void)ctx.read_var_uint32(ctx.error());
      return true;
    }
    if (ref_flag != NOT_NULL_VALUE_FLAG && ref_flag != REF_VALUE_FLAG) {
      ctx.set_error(
          Error::invalid_data("Unknown reference flag: " +
                              std::to_string(static_cast<int>(ref_flag))));
      return true;
    }
    return false;
  }

  if (ref_flag != NOT_NULL_VALUE_FLAG) {
    ctx.set_error(
        Error::invalid_data("Unknown nullability flag: " +
                            std::to_string(static_cast<int>(ref_flag))));
    return true;
  }
  return false;
}

void skip_struct_payload(ReadContext &ctx, const TypeInfo &type_info) {
  if (!type_info.type_meta) {
    ctx.set_error(Error::type_error("TypeMeta not found for struct skip"));
    return;
  }

  const auto &field_infos = type_info.type_meta->get_field_infos();
  for (const auto &fi : field_infos) {
    skip_field_value(ctx, fi.field_type, fi.field_type.ref_mode);
    if (FORY_PREDICT_FALSE(ctx.has_error())) {
      return;
    }
  }
}

void skip_ext_payload(ReadContext &ctx, const TypeInfo &type_info) {
  if (!type_info.harness.valid() || !type_info.harness.read_data_fn) {
    ctx.set_error(
        Error::type_error("Ext harness not found or incomplete for type: " +
                          std::to_string(type_info.type_id)));
    return;
  }

  auto depth_res = ctx.increase_dyn_depth();
  if (FORY_PREDICT_FALSE(!depth_res.ok())) {
    ctx.set_error(std::move(depth_res).error());
    return;
  }
  DynDepthGuard dyn_depth_guard(ctx);

  void *ptr = type_info.harness.read_data_fn(ctx);
  if (FORY_PREDICT_FALSE(ctx.has_error())) {
    return;
  }
  if (ptr) {
    ::operator delete(ptr);
  }
}

void skip_payload_with_type_info(ReadContext &ctx, const TypeInfo &type_info) {
  TypeId actual_tid = static_cast<TypeId>(type_info.type_id);
  switch (actual_tid) {
  case TypeId::ENUM:
  case TypeId::NAMED_ENUM:
    (void)ctx.read_var_uint32(ctx.error());
    return;
  case TypeId::STRUCT:
  case TypeId::COMPATIBLE_STRUCT:
  case TypeId::NAMED_STRUCT:
  case TypeId::NAMED_COMPATIBLE_STRUCT:
    skip_struct_payload(ctx, type_info);
    return;
  case TypeId::EXT:
  case TypeId::NAMED_EXT:
    skip_ext_payload(ctx, type_info);
    return;
  default:
    skip_field_value(ctx, make_field_type(type_info), RefMode::None);
    return;
  }
}

void skip_with_type_info(ReadContext &ctx, const TypeInfo &type_info,
                         RefMode ref_mode) {
  if (skip_ref_or_null(ctx, ref_mode)) {
    return;
  }
  skip_payload_with_type_info(ctx, type_info);
}
} // namespace

void skip_varint(ReadContext &ctx) {
  // skip varint by reading it
  ctx.read_var_uint64(ctx.error());
}

void skip_string(ReadContext &ctx) {
  // Strings use the compact var_uint36_small header that packs the encoding
  // into the low 2 bits and the byte length into the remaining bits.
  uint64_t size_encoding = ctx.read_var_uint36_small(ctx.error());
  if (FORY_PREDICT_FALSE(ctx.has_error())) {
    return;
  }
  uint64_t size = size_encoding >> 2;

  // skip string data
  ctx.buffer().increase_reader_index(size, ctx.error());
}

void skip_list(ReadContext &ctx, const FieldType &field_type) {
  // Read list length
  uint32_t length = ctx.read_var_uint32(ctx.error());
  if (FORY_PREDICT_FALSE(ctx.has_error())) {
    return;
  }
  if (FORY_PREDICT_FALSE(length > ctx.config().max_collection_size)) {
    ctx.set_error(
        Error::invalid_data("Collection length exceeds max_collection_size"));
    return;
  }
  if (length == 0) {
    return;
  }

  // Read elements header
  uint8_t header = ctx.read_uint8(ctx.error());
  if (FORY_PREDICT_FALSE(ctx.has_error())) {
    return;
  }

  bool track_ref = (header & 0b1) != 0;
  bool has_null = (header & 0b10) != 0;
  bool is_declared_type = (header & 0b100) != 0;
  bool is_same_type = (header & 0b1000) != 0;

  const TypeInfo *elem_type_info = nullptr;
  if (is_same_type && !is_declared_type) {
    elem_type_info = ctx.read_any_type_info(ctx.error());
    if (FORY_PREDICT_FALSE(ctx.has_error())) {
      return;
    }
  }

  FieldType elem_type = field_type.generics.empty() ? make_unknown_field_type()
                                                    : field_type.generics[0];

  if (is_same_type) {
    RefMode elem_ref_mode = RefMode::None;
    if (track_ref) {
      elem_ref_mode = RefMode::Tracking;
    } else if (has_null) {
      elem_ref_mode = RefMode::NullOnly;
    }
    for (uint32_t i = 0; i < length; ++i) {
      if (elem_type_info) {
        skip_with_type_info(ctx, *elem_type_info, elem_ref_mode);
      } else {
        skip_field_value(ctx, elem_type, elem_ref_mode);
      }
      if (FORY_PREDICT_FALSE(ctx.has_error())) {
        return;
      }
    }
    return;
  }

  for (uint32_t i = 0; i < length; ++i) {
    if (track_ref) {
      int8_t ref_flag = ctx.read_int8(ctx.error());
      if (FORY_PREDICT_FALSE(ctx.has_error())) {
        return;
      }
      if (ref_flag == NULL_FLAG) {
        continue;
      }
      if (ref_flag == REF_FLAG) {
        (void)ctx.read_var_uint32(ctx.error());
        if (FORY_PREDICT_FALSE(ctx.has_error())) {
          return;
        }
        continue;
      }
      if (ref_flag != NOT_NULL_VALUE_FLAG && ref_flag != REF_VALUE_FLAG) {
        ctx.set_error(
            Error::invalid_data("Unknown reference flag: " +
                                std::to_string(static_cast<int>(ref_flag))));
        return;
      }
    } else if (has_null) {
      int8_t null_flag = ctx.read_int8(ctx.error());
      if (FORY_PREDICT_FALSE(ctx.has_error())) {
        return;
      }
      if (null_flag == NULL_FLAG) {
        continue;
      }
      if (null_flag != NOT_NULL_VALUE_FLAG) {
        ctx.set_error(
            Error::invalid_data("Unknown nullability flag: " +
                                std::to_string(static_cast<int>(null_flag))));
        return;
      }
    }

    const TypeInfo *actual_type_info = ctx.read_any_type_info(ctx.error());
    if (FORY_PREDICT_FALSE(ctx.has_error())) {
      return;
    }
    skip_payload_with_type_info(ctx, *actual_type_info);
    if (FORY_PREDICT_FALSE(ctx.has_error())) {
      return;
    }
  }
}

void skip_set(ReadContext &ctx, const FieldType &field_type) {
  // Set has same format as list
  skip_list(ctx, field_type);
}

void skip_map(ReadContext &ctx, const FieldType &field_type) {
  // Read map length
  uint32_t total_length = ctx.read_var_uint32(ctx.error());
  if (FORY_PREDICT_FALSE(ctx.has_error())) {
    return;
  }
  if (FORY_PREDICT_FALSE(total_length > ctx.config().max_collection_size)) {
    ctx.set_error(
        Error::invalid_data("Map entry count exceeds max_collection_size"));
    return;
  }
  if (total_length == 0) {
    return;
  }

  // get key and value types
  FieldType key_type = field_type.generics.size() >= 1
                           ? field_type.generics[0]
                           : make_unknown_field_type();
  FieldType value_type = field_type.generics.size() >= 2
                             ? field_type.generics[1]
                             : make_unknown_field_type();

  uint32_t read_count = 0;
  while (read_count < total_length) {
    uint8_t chunk_header = ctx.read_uint8(ctx.error());
    if (FORY_PREDICT_FALSE(ctx.has_error())) {
      return;
    }

    bool key_track_ref = (chunk_header & 0b1) != 0;
    bool key_has_null = (chunk_header & 0b10) != 0;
    bool key_declared = (chunk_header & 0b100) != 0;
    bool value_track_ref = (chunk_header & 0b1000) != 0;
    bool value_has_null = (chunk_header & 0b10000) != 0;
    bool value_declared = (chunk_header & 0b100000) != 0;

    if (key_has_null && value_has_null) {
      read_count += 1;
      continue;
    }

    if (key_has_null) {
      const TypeInfo *value_type_info = nullptr;
      if (!value_declared) {
        value_type_info = ctx.read_any_type_info(ctx.error());
        if (FORY_PREDICT_FALSE(ctx.has_error())) {
          return;
        }
      }
      if (value_type_info) {
        skip_with_type_info(ctx, *value_type_info,
                            value_track_ref ? RefMode::Tracking
                                            : RefMode::None);
      } else {
        skip_field_value(ctx, value_type,
                         value_track_ref ? RefMode::Tracking : RefMode::None);
      }
      if (FORY_PREDICT_FALSE(ctx.has_error())) {
        return;
      }
      read_count += 1;
      continue;
    }

    if (value_has_null) {
      const TypeInfo *key_type_info = nullptr;
      if (!key_declared) {
        key_type_info = ctx.read_any_type_info(ctx.error());
        if (FORY_PREDICT_FALSE(ctx.has_error())) {
          return;
        }
      }
      if (key_type_info) {
        skip_with_type_info(ctx, *key_type_info,
                            key_track_ref ? RefMode::Tracking : RefMode::None);
      } else {
        skip_field_value(ctx, key_type,
                         key_track_ref ? RefMode::Tracking : RefMode::None);
      }
      if (FORY_PREDICT_FALSE(ctx.has_error())) {
        return;
      }
      read_count += 1;
      continue;
    }

    uint8_t chunk_size = ctx.read_uint8(ctx.error());
    if (FORY_PREDICT_FALSE(ctx.has_error())) {
      return;
    }
    if (chunk_size == 0 || read_count + chunk_size > total_length) {
      ctx.set_error(Error::invalid_data("Chunk size exceeds total map length"));
      return;
    }

    const TypeInfo *key_type_info = nullptr;
    const TypeInfo *value_type_info = nullptr;
    if (!key_declared) {
      key_type_info = ctx.read_any_type_info(ctx.error());
      if (FORY_PREDICT_FALSE(ctx.has_error())) {
        return;
      }
    }
    if (!value_declared) {
      value_type_info = ctx.read_any_type_info(ctx.error());
      if (FORY_PREDICT_FALSE(ctx.has_error())) {
        return;
      }
    }

    for (uint8_t i = 0; i < chunk_size; ++i) {
      if (key_type_info) {
        skip_with_type_info(ctx, *key_type_info,
                            key_track_ref ? RefMode::Tracking : RefMode::None);
      } else {
        skip_field_value(ctx, key_type,
                         key_track_ref ? RefMode::Tracking : RefMode::None);
      }
      if (FORY_PREDICT_FALSE(ctx.has_error())) {
        return;
      }
      if (value_type_info) {
        skip_with_type_info(ctx, *value_type_info,
                            value_track_ref ? RefMode::Tracking
                                            : RefMode::None);
      } else {
        skip_field_value(ctx, value_type,
                         value_track_ref ? RefMode::Tracking : RefMode::None);
      }
      if (FORY_PREDICT_FALSE(ctx.has_error())) {
        return;
      }
    }
    read_count += chunk_size;
  }
}

void skip_struct(ReadContext &ctx, const FieldType &) {
  // Struct fields in compatible mode are serialized with type_id and
  // optionally meta_index, followed by field values. We use the loaded
  // TypeMeta to skip all fields for the remote struct.
  //
  // Type categories:
  // - COMPATIBLE_STRUCT/NAMED_COMPATIBLE_STRUCT: type_id + meta_index + fields
  // - NAMED_STRUCT in compatible mode: type_id + meta_index + fields
  // - STRUCT: type_id + struct_version + fields (no meta_index)

  if (!ctx.is_compatible()) {
    ctx.set_error(Error::unsupported(
        "Struct skipping is only supported in compatible mode"));
    return;
  }

  // Read remote type_id
  uint32_t remote_type_id = ctx.read_uint8(ctx.error());
  if (FORY_PREDICT_FALSE(ctx.has_error())) {
    return;
  }

  uint32_t user_type_id = 0;
  switch (static_cast<TypeId>(remote_type_id)) {
  case TypeId::ENUM:
  case TypeId::STRUCT:
  case TypeId::EXT:
  case TypeId::TYPED_UNION:
    user_type_id = ctx.read_var_uint32(ctx.error());
    if (FORY_PREDICT_FALSE(ctx.has_error())) {
      return;
    }
    break;
  default:
    break;
  }
  TypeId remote_tid = static_cast<TypeId>(remote_type_id);
  bool has_user_type_id =
      remote_tid == TypeId::ENUM || remote_tid == TypeId::STRUCT ||
      remote_tid == TypeId::EXT || remote_tid == TypeId::TYPED_UNION;

  const TypeInfo *type_info = nullptr;

  if (remote_tid == TypeId::COMPATIBLE_STRUCT ||
      remote_tid == TypeId::NAMED_COMPATIBLE_STRUCT ||
      remote_tid == TypeId::NAMED_STRUCT) {
    // These types write TypeMeta inline using streaming protocol
    auto type_info_res = ctx.read_type_meta();
    if (FORY_PREDICT_FALSE(!type_info_res.ok())) {
      ctx.set_error(std::move(type_info_res).error());
      return;
    }
    type_info = type_info_res.value();
  } else {
    // Plain STRUCT: look up by type_id, read struct_version if enabled
    auto type_info_res =
        has_user_type_id
            ? ctx.type_resolver().get_user_type_info_by_id(remote_type_id,
                                                           user_type_id)
            : ctx.type_resolver().get_type_info_by_id(remote_type_id);
    if (FORY_PREDICT_FALSE(!type_info_res.ok())) {
      ctx.set_error(std::move(type_info_res).error());
      return;
    }
    type_info = type_info_res.value();

    // STRUCT writes struct_version if class version checking is enabled
    // For now we skip 4 bytes for the version hash
    // TODO: make this conditional based on config
    (void)ctx.read_int32(ctx.error());
    if (FORY_PREDICT_FALSE(ctx.has_error())) {
      return;
    }
  }

  if (!type_info) {
    ctx.set_error(Error::type_error("TypeInfo not found for struct skip"));
    return;
  }
  skip_struct_payload(ctx, *type_info);
}

void skip_ext(ReadContext &ctx, const FieldType &) {
  // EXT fields in compatible mode are serialized with type_id followed by
  // ext data. For named ext, meta_index is also written after type_id.
  // We read the type_id, look up the registered ext harness, and call its
  // read_data function to consume the data bytes.

  if (!ctx.is_compatible()) {
    ctx.set_error(Error::unsupported(
        "Ext skipping is only supported in compatible mode"));
    return;
  }

  // Read remote type_id from buffer - Java always writes type_id for ext
  uint32_t remote_type_id = ctx.read_uint8(ctx.error());
  if (FORY_PREDICT_FALSE(ctx.has_error())) {
    return;
  }

  uint32_t user_type_id = 0;
  switch (static_cast<TypeId>(remote_type_id)) {
  case TypeId::ENUM:
  case TypeId::STRUCT:
  case TypeId::COMPATIBLE_STRUCT:
  case TypeId::EXT:
  case TypeId::TYPED_UNION:
    user_type_id = ctx.read_var_uint32(ctx.error());
    if (FORY_PREDICT_FALSE(ctx.has_error())) {
      return;
    }
    break;
  default:
    break;
  }
  TypeId remote_tid = static_cast<TypeId>(remote_type_id);

  const TypeInfo *type_info = nullptr;

  if (remote_tid == TypeId::NAMED_EXT) {
    // Named ext in compatible mode: read TypeMeta inline using streaming
    // protocol
    auto type_info_res = ctx.read_type_meta();
    if (FORY_PREDICT_FALSE(!type_info_res.ok())) {
      ctx.set_error(std::move(type_info_res).error());
      return;
    }
    type_info = type_info_res.value();
  } else {
    // ID-based ext: look up by remote type_id we just read
    auto type_info_res = ctx.type_resolver().get_user_type_info_by_id(
        remote_type_id, user_type_id);
    if (FORY_PREDICT_FALSE(!type_info_res.ok())) {
      ctx.set_error(std::move(type_info_res).error());
      return;
    }
    type_info = type_info_res.value();
  }

  if (!type_info) {
    ctx.set_error(Error::type_error("TypeInfo not found for ext type: " +
                                    std::to_string(remote_type_id)));
    return;
  }
  skip_ext_payload(ctx, *type_info);
}

void skip_unknown(ReadContext &ctx) {
  // UNKNOWN type means the actual type info is written inline.
  // We need to read the type info and then skip based on the actual type.
  // This is used for polymorphic fields like List<Animal>.
  const TypeInfo *type_info = ctx.read_any_type_info(ctx.error());
  if (FORY_PREDICT_FALSE(ctx.has_error())) {
    return;
  }
  if (!type_info) {
    ctx.set_error(Error::type_error("TypeInfo not found for UNKNOWN skip"));
    return;
  }

  skip_payload_with_type_info(ctx, *type_info);
}

void skip_union(ReadContext &ctx) {
  // Read the variant index
  (void)ctx.read_var_uint32(ctx.error());
  if (FORY_PREDICT_FALSE(ctx.has_error())) {
    return;
  }
  // Read ref flag for the union value (Any-style)
  int8_t ref_flag = ctx.read_int8(ctx.error());
  if (FORY_PREDICT_FALSE(ctx.has_error())) {
    return;
  }
  if (ref_flag == NULL_FLAG) {
    return;
  }
  if (ref_flag == REF_FLAG) {
    (void)ctx.read_var_uint32(ctx.error());
    return;
  }
  if (ref_flag != NOT_NULL_VALUE_FLAG && ref_flag != REF_VALUE_FLAG) {
    ctx.set_error(
        Error::invalid_data("Unknown reference flag: " +
                            std::to_string(static_cast<int>(ref_flag))));
    return;
  }

  // Read and skip the alternative's type info
  const TypeInfo *type_info = ctx.read_any_type_info(ctx.error());
  if (FORY_PREDICT_FALSE(ctx.has_error())) {
    return;
  }
  if (!type_info) {
    ctx.set_error(
        Error::type_error("TypeInfo not found for UNION alternative skip"));
    return;
  }

  skip_payload_with_type_info(ctx, *type_info);
}

void skip_field_value(ReadContext &ctx, const FieldType &field_type,
                      RefMode ref_mode) {
  // Read ref flag if needed
  if (skip_ref_or_null(ctx, ref_mode)) {
    return;
  }

  // skip based on the logical TypeId (already in 0..255)
  TypeId tid = static_cast<TypeId>(field_type.type_id);

  switch (tid) {
  case TypeId::BOOL:
  case TypeId::INT8:
  case TypeId::UINT8:
  case TypeId::FLOAT8:
    ctx.buffer().increase_reader_index(1, ctx.error());
    return;

  case TypeId::INT16:
  case TypeId::UINT16:
  case TypeId::BFLOAT16:
  case TypeId::FLOAT16:
    ctx.buffer().increase_reader_index(2, ctx.error());
    return;

  case TypeId::UINT32:
    ctx.read_uint32(ctx.error());
    return;

  case TypeId::INT32: {
    ctx.buffer().increase_reader_index(4, ctx.error());
    return;
  }

  case TypeId::FLOAT32:
    ctx.buffer().increase_reader_index(4, ctx.error());
    return;

  case TypeId::UINT64:
    ctx.read_uint64(ctx.error());
    return;

  case TypeId::INT64: {
    ctx.buffer().increase_reader_index(8, ctx.error());
    return;
  }

  case TypeId::FLOAT64:
    ctx.buffer().increase_reader_index(8, ctx.error());
    return;

  case TypeId::VARINT32:
  case TypeId::VARINT64:
  case TypeId::VAR_UINT32:
  case TypeId::VAR_UINT64:
    skip_varint(ctx);
    return;

  case TypeId::TAGGED_INT64:
    ctx.read_tagged_int64(ctx.error());
    return;

  case TypeId::TAGGED_UINT64:
    ctx.read_tagged_uint64(ctx.error());
    return;

  case TypeId::STRING:
    skip_string(ctx);
    return;

  case TypeId::LIST:
    skip_list(ctx, field_type);
    return;

  case TypeId::SET:
    skip_set(ctx, field_type);
    return;

  case TypeId::MAP:
    skip_map(ctx, field_type);
    return;

  case TypeId::DURATION: {
    // Duration is stored as signed varint64 seconds + signed int32
    // nanoseconds.
    skip_varint(ctx);
    if (FORY_PREDICT_FALSE(ctx.has_error())) {
      return;
    }
    constexpr uint32_t k_bytes = static_cast<uint32_t>(sizeof(int32_t));
    ctx.buffer().increase_reader_index(k_bytes, ctx.error());
    return;
  }
  case TypeId::TIMESTAMP: {
    // Timestamp is stored as int64 seconds + uint32 nanoseconds.
    constexpr uint32_t k_bytes =
        static_cast<uint32_t>(sizeof(int64_t) + sizeof(uint32_t));
    ctx.buffer().increase_reader_index(k_bytes, ctx.error());
    return;
  }

  case TypeId::DATE: {
    // Date is stored as a signed varint64 day count.
    ctx.read_var_int64(ctx.error());
    return;
  }

  case TypeId::DECIMAL: {
    // Decimal is stored as signed varint32 scale + varuint64 header and
    // optional little-endian magnitude payload.
    ctx.read_var_int32(ctx.error());
    if (FORY_PREDICT_FALSE(ctx.has_error())) {
      return;
    }
    uint64_t header = ctx.read_var_uint64(ctx.error());
    if (FORY_PREDICT_FALSE(ctx.has_error())) {
      return;
    }
    if ((header & 1U) == 0) {
      return;
    }
    uint64_t length64 = header >> 2;
    if (length64 == 0) {
      ctx.set_error(Error::invalid_data("Invalid decimal magnitude length 0"));
      return;
    }
    if (length64 > ctx.config().max_binary_size) {
      ctx.set_error(Error::invalid_data("Binary size exceeds max_binary_size"));
      return;
    }
    if (length64 > std::numeric_limits<uint32_t>::max()) {
      ctx.set_error(Error::invalid_data("Invalid decimal magnitude length " +
                                        std::to_string(length64)));
      return;
    }
    ctx.buffer().increase_reader_index(static_cast<uint32_t>(length64),
                                       ctx.error());
    return;
  }

  case TypeId::STRUCT:
  case TypeId::COMPATIBLE_STRUCT:
  case TypeId::NAMED_STRUCT:
  case TypeId::NAMED_COMPATIBLE_STRUCT:
    skip_struct(ctx, field_type);
    return;

  // Primitive arrays
  case TypeId::BOOL_ARRAY:
  case TypeId::INT8_ARRAY:
  case TypeId::INT16_ARRAY:
  case TypeId::INT32_ARRAY:
  case TypeId::INT64_ARRAY:
  case TypeId::UINT8_ARRAY:
  case TypeId::UINT16_ARRAY:
  case TypeId::UINT32_ARRAY:
  case TypeId::UINT64_ARRAY:
  case TypeId::FLOAT8_ARRAY:
  case TypeId::FLOAT16_ARRAY:
  case TypeId::BFLOAT16_ARRAY:
  case TypeId::FLOAT32_ARRAY:
  case TypeId::FLOAT64_ARRAY: {
    if (tid == TypeId::FLOAT8_ARRAY) {
      ctx.buffer().increase_reader_index(1, ctx.error());
      if (FORY_PREDICT_FALSE(ctx.has_error())) {
        return;
      }
    }
    // Typed primitive arrays encode payload size in bytes, not element count.
    uint32_t payload_size = ctx.read_var_uint32(ctx.error());
    if (FORY_PREDICT_FALSE(ctx.has_error())) {
      return;
    }

    ctx.buffer().increase_reader_index(payload_size, ctx.error());
    return;
  }

  case TypeId::BINARY: {
    // Read binary length
    uint32_t len = ctx.read_var_uint32(ctx.error());
    if (FORY_PREDICT_FALSE(ctx.has_error())) {
      return;
    }
    ctx.buffer().increase_reader_index(len, ctx.error());
    return;
  }

  case TypeId::ENUM:
  case TypeId::NAMED_ENUM: {
    // Enums are serialized as ordinal varuint32 values.
    ctx.read_var_uint32(ctx.error());
    return;
  }

  case TypeId::EXT:
  case TypeId::NAMED_EXT:
    skip_ext(ctx, field_type);
    return;

  case TypeId::UNKNOWN:
    skip_unknown(ctx);
    return;

  case TypeId::UNION:
    skip_union(ctx);
    return;
  case TypeId::TYPED_UNION:
  case TypeId::NAMED_UNION:
    skip_union(ctx);
    return;

  case TypeId::NONE:
    // NONE (not-applicable/monostate) has no data to skip
    return;

  default:
    ctx.set_error(
        Error::type_error("Unknown field type to skip: " +
                          std::to_string(static_cast<uint32_t>(tid))));
    return;
  }
}

} // namespace serialization
} // namespace fory
