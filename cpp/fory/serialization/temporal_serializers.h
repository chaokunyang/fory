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
#include <chrono>

namespace fory {
namespace serialization {

// ============================================================================
// Temporal Type Aliases
// ============================================================================

/// Duration: absolute length of time as nanoseconds
using Duration = std::chrono::nanoseconds;

/// Timestamp: point in time as nanoseconds since Unix epoch (Jan 1, 1970 UTC)
using Timestamp = std::chrono::time_point<std::chrono::system_clock,
                                          std::chrono::nanoseconds>;

/// LocalDate: naive date without timezone as days since Unix epoch
struct LocalDate {
  int32_t days_since_epoch; // Days since Jan 1, 1970 UTC

  LocalDate() : days_since_epoch(0) {}
  explicit LocalDate(int32_t days) : days_since_epoch(days) {}

  bool operator==(const LocalDate &other) const {
    return days_since_epoch == other.days_since_epoch;
  }

  bool operator!=(const LocalDate &other) const { return !(*this == other); }
};

// ============================================================================
// Duration Serializer
// ============================================================================

/// Serializer for Duration (std::chrono::nanoseconds)
/// Per xlang spec: serialized as int64 nanosecond count
template <> struct Serializer<Duration> {
  static constexpr TypeId type_id = TypeId::DURATION;

  static void write(const Duration &duration, WriteContext &ctx,
                    bool write_ref, bool write_type, bool has_generics,
                    Error *error) {
    (void)has_generics;
    (void)error;
    write_not_null_ref_flag(ctx, write_ref);
    if (write_type) {
      ctx.write_varuint32(static_cast<uint32_t>(type_id));
    }
    write_data(duration, ctx, error);
  }

  static void write_data(const Duration &duration, WriteContext &ctx,
                         Error *error) {
    (void)error;
    int64_t nanos = duration.count();
    ctx.write_bytes(&nanos, sizeof(int64_t));
  }

  static void write_data_generic(const Duration &duration, WriteContext &ctx,
                                 bool has_generics, Error *error) {
    (void)has_generics;
    write_data(duration, ctx, error);
  }

  static Duration read(ReadContext &ctx, bool read_ref, bool read_type,
                       Error *error) {
    bool has_value = consume_ref_flag(ctx, read_ref, error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return Duration(0);
    }
    if (!has_value) {
      return Duration(0);
    }
    if (read_type) {
      uint32_t type_id_read = ctx.read_varuint32(error);
      if (FORY_PREDICT_FALSE(!error->ok())) {
        return Duration(0);
      }
      if (type_id_read != static_cast<uint32_t>(type_id)) {
        error->set_error(ErrorCode::TypeError,
                         Error::type_mismatch(type_id_read,
                                              static_cast<uint32_t>(type_id))
                             .message());
        return Duration(0);
      }
    }
    return read_data(ctx, error);
  }

  static Duration read_data(ReadContext &ctx, Error *error) {
    int64_t nanos;
    ctx.read_bytes(&nanos, sizeof(int64_t), error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return Duration(0);
    }
    return Duration(nanos);
  }
};

// ============================================================================
// Timestamp Serializer
// ============================================================================

/// Serializer for Timestamp
/// Per xlang spec: serialized as int64 nanosecond count since Unix epoch
template <> struct Serializer<Timestamp> {
  static constexpr TypeId type_id = TypeId::TIMESTAMP;

  static void write(const Timestamp &timestamp, WriteContext &ctx,
                    bool write_ref, bool write_type, bool has_generics,
                    Error *error) {
    (void)has_generics;
    (void)error;
    write_not_null_ref_flag(ctx, write_ref);
    if (write_type) {
      ctx.write_varuint32(static_cast<uint32_t>(type_id));
    }
    write_data(timestamp, ctx, error);
  }

  static void write_data(const Timestamp &timestamp, WriteContext &ctx,
                         Error *error) {
    (void)error;
    int64_t nanos = timestamp.time_since_epoch().count();
    ctx.write_bytes(&nanos, sizeof(int64_t));
  }

  static void write_data_generic(const Timestamp &timestamp, WriteContext &ctx,
                                 bool has_generics, Error *error) {
    (void)has_generics;
    write_data(timestamp, ctx, error);
  }

  static Timestamp read(ReadContext &ctx, bool read_ref, bool read_type,
                        Error *error) {
    bool has_value = consume_ref_flag(ctx, read_ref, error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return Timestamp(Duration(0));
    }
    if (!has_value) {
      return Timestamp(Duration(0));
    }
    if (read_type) {
      uint32_t type_id_read = ctx.read_varuint32(error);
      if (FORY_PREDICT_FALSE(!error->ok())) {
        return Timestamp(Duration(0));
      }
      if (type_id_read != static_cast<uint32_t>(type_id)) {
        error->set_error(ErrorCode::TypeError,
                         Error::type_mismatch(type_id_read,
                                              static_cast<uint32_t>(type_id))
                             .message());
        return Timestamp(Duration(0));
      }
    }
    return read_data(ctx, error);
  }

  static Timestamp read_data(ReadContext &ctx, Error *error) {
    int64_t nanos;
    ctx.read_bytes(&nanos, sizeof(int64_t), error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return Timestamp(Duration(0));
    }
    return Timestamp(Duration(nanos));
  }
};

// ============================================================================
// LocalDate Serializer
// ============================================================================

/// Serializer for LocalDate
/// Per xlang spec: serialized as int32 day count since Unix epoch
template <> struct Serializer<LocalDate> {
  static constexpr TypeId type_id = TypeId::LOCAL_DATE;

  static void write(const LocalDate &date, WriteContext &ctx, bool write_ref,
                    bool write_type, bool has_generics, Error *error) {
    (void)has_generics;
    (void)error;
    write_not_null_ref_flag(ctx, write_ref);
    if (write_type) {
      ctx.write_varuint32(static_cast<uint32_t>(type_id));
    }
    write_data(date, ctx, error);
  }

  static void write_data(const LocalDate &date, WriteContext &ctx,
                         Error *error) {
    (void)error;
    ctx.write_bytes(&date.days_since_epoch, sizeof(int32_t));
  }

  static void write_data_generic(const LocalDate &date, WriteContext &ctx,
                                 bool has_generics, Error *error) {
    (void)has_generics;
    write_data(date, ctx, error);
  }

  static LocalDate read(ReadContext &ctx, bool read_ref, bool read_type,
                        Error *error) {
    bool has_value = consume_ref_flag(ctx, read_ref, error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return LocalDate();
    }
    if (!has_value) {
      return LocalDate();
    }
    if (read_type) {
      uint32_t type_id_read = ctx.read_varuint32(error);
      if (FORY_PREDICT_FALSE(!error->ok())) {
        return LocalDate();
      }
      if (type_id_read != static_cast<uint32_t>(type_id)) {
        error->set_error(ErrorCode::TypeError,
                         Error::type_mismatch(type_id_read,
                                              static_cast<uint32_t>(type_id))
                             .message());
        return LocalDate();
      }
    }
    return read_data(ctx, error);
  }

  static LocalDate read_data(ReadContext &ctx, Error *error) {
    LocalDate date;
    ctx.read_bytes(&date.days_since_epoch, sizeof(int32_t), error);
    if (FORY_PREDICT_FALSE(!error->ok())) {
      return LocalDate();
    }
    return date;
  }
};

} // namespace serialization
} // namespace fory
