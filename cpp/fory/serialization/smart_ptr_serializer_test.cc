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

#include "fory/serialization/fory.h"
#include "gtest/gtest.h"
#include <cstdint>
#include <memory>
#include <optional>
#include <vector>

namespace fory {
namespace serialization {

struct OptionalIntHolder {
  std::optional<int32_t> value;
};
FORY_STRUCT(OptionalIntHolder, value);

struct OptionalSharedHolder {
  std::optional<std::shared_ptr<int32_t>> value;
};
FORY_STRUCT(OptionalSharedHolder, value);

struct SharedPair {
  std::shared_ptr<int32_t> first;
  std::shared_ptr<int32_t> second;
};
FORY_STRUCT(SharedPair, first, second);

struct UniqueHolder {
  std::unique_ptr<int32_t> value;
};
FORY_STRUCT(UniqueHolder, value);

namespace {

Fory create_serializer(bool track_references) {
  return Fory::builder().track_references(track_references).build();
}

TEST(SmartPtrSerializerTest, OptionalIntRoundTrip) {
  OptionalIntHolder original;
  original.value = 42;

  auto fory = create_serializer(true);
  auto bytes_result = fory.serialize(original);
  ASSERT_TRUE(bytes_result.ok()) << bytes_result.error().to_string();

  auto deserialize_result =
      fory.deserialize<OptionalIntHolder>(bytes_result->data(),
                                          bytes_result->size());
  ASSERT_TRUE(deserialize_result.ok())
      << deserialize_result.error().to_string();

  const auto &deserialized = deserialize_result.value();
  ASSERT_TRUE(deserialized.value.has_value());
  EXPECT_EQ(*deserialized.value, 42);
}

TEST(SmartPtrSerializerTest, OptionalIntNullRoundTrip) {
  OptionalIntHolder original;
  original.value.reset();

  auto fory = create_serializer(true);
  auto bytes_result = fory.serialize(original);
  ASSERT_TRUE(bytes_result.ok()) << bytes_result.error().to_string();

  auto deserialize_result =
      fory.deserialize<OptionalIntHolder>(bytes_result->data(),
                                          bytes_result->size());
  ASSERT_TRUE(deserialize_result.ok())
      << deserialize_result.error().to_string();

  const auto &deserialized = deserialize_result.value();
  EXPECT_FALSE(deserialized.value.has_value());
}

TEST(SmartPtrSerializerTest, OptionalSharedPtrRoundTrip) {
  OptionalSharedHolder original;
  original.value = std::make_shared<int32_t>(42);

  auto fory = create_serializer(true);
  auto bytes_result = fory.serialize(original);
  ASSERT_TRUE(bytes_result.ok()) << bytes_result.error().to_string();

  auto deserialize_result =
      fory.deserialize<OptionalSharedHolder>(bytes_result->data(),
                                             bytes_result->size());
  ASSERT_TRUE(deserialize_result.ok())
      << deserialize_result.error().to_string();

  const auto &deserialized = deserialize_result.value();
  ASSERT_TRUE(deserialized.value.has_value());
  ASSERT_TRUE(deserialized.value.value());
  EXPECT_EQ(*deserialized.value.value(), 42);
}

TEST(SmartPtrSerializerTest, SharedPtrReferenceTracking) {
  auto shared = std::make_shared<int32_t>(1337);
  SharedPair original{shared, shared};

  auto fory = create_serializer(true);
  auto bytes_result = fory.serialize(original);
  ASSERT_TRUE(bytes_result.ok()) << bytes_result.error().to_string();

  auto deserialize_result =
      fory.deserialize<SharedPair>(bytes_result->data(), bytes_result->size());
  ASSERT_TRUE(deserialize_result.ok())
      << deserialize_result.error().to_string();

  auto deserialized = std::move(deserialize_result).value();
  ASSERT_TRUE(deserialized.first);
  ASSERT_TRUE(deserialized.second);
  EXPECT_EQ(*deserialized.first, 1337);
  EXPECT_EQ(*deserialized.second, 1337);
  EXPECT_EQ(deserialized.first, deserialized.second)
      << "Reference tracking should preserve shared_ptr aliasing";
}

TEST(SmartPtrSerializerTest, UniquePtrRoundTrip) {
  UniqueHolder original;
  original.value = std::make_unique<int32_t>(2025);

  auto fory = create_serializer(true);
  auto bytes_result = fory.serialize(original);
  ASSERT_TRUE(bytes_result.ok()) << bytes_result.error().to_string();

  auto deserialize_result =
      fory.deserialize<UniqueHolder>(bytes_result->data(), bytes_result->size());
  ASSERT_TRUE(deserialize_result.ok())
      << deserialize_result.error().to_string();

  auto deserialized = std::move(deserialize_result).value();
  ASSERT_TRUE(deserialized.value);
  EXPECT_EQ(*deserialized.value, 2025);
}

TEST(SmartPtrSerializerTest, UniquePtrNullRoundTrip) {
  UniqueHolder original;
  original.value.reset();

  auto fory = create_serializer(true);
  auto bytes_result = fory.serialize(original);
  ASSERT_TRUE(bytes_result.ok()) << bytes_result.error().to_string();

  auto deserialize_result =
      fory.deserialize<UniqueHolder>(bytes_result->data(), bytes_result->size());
  ASSERT_TRUE(deserialize_result.ok())
      << deserialize_result.error().to_string();

  auto deserialized = std::move(deserialize_result).value();
  EXPECT_EQ(deserialized.value, nullptr);
}

} // namespace
} // namespace serialization
} // namespace fory
