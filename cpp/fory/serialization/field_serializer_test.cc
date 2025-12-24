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

/**
 * Serialization tests for fory::field<> template.
 *
 * Tests struct serialization with fory::field<> members including:
 * - Primitive fields with tag IDs
 * - String fields with tag IDs
 * - Optional fields (inherently nullable)
 * - Smart pointer fields with nullable/ref options
 * - Nested structs with field metadata
 * - Reference tracking with fory::ref
 */

#include "fory/meta/field.h"
#include "fory/serialization/fory.h"
#include "gtest/gtest.h"
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>

// ============================================================================
// Struct definitions with fory::field<> members
// ============================================================================

// Basic struct with primitive fields
struct FieldPerson {
  fory::field<std::string, 0> name;
  fory::field<int32_t, 1> age;
  fory::field<double, 2> score;
  fory::field<bool, 3> active;

  bool operator==(const FieldPerson &other) const {
    return name.value == other.name.value && age.value == other.age.value &&
           score.value == other.score.value &&
           active.value == other.active.value;
  }
};
FORY_STRUCT(FieldPerson, name, age, score, active);

// Struct with optional fields
struct FieldOptionalData {
  fory::field<std::string, 0> required_name;
  fory::field<std::optional<int32_t>, 1> optional_age;
  fory::field<std::optional<std::string>, 2> optional_email;

  bool operator==(const FieldOptionalData &other) const {
    return required_name.value == other.required_name.value &&
           optional_age.value == other.optional_age.value &&
           optional_email.value == other.optional_email.value;
  }
};
FORY_STRUCT(FieldOptionalData, required_name, optional_age, optional_email);

// Struct with shared_ptr fields (non-nullable by default)
struct FieldSharedPtrHolder {
  fory::field<std::shared_ptr<int32_t>, 0> value;
  fory::field<std::shared_ptr<std::string>, 1> text;

  bool operator==(const FieldSharedPtrHolder &other) const {
    if (static_cast<bool>(value.value) != static_cast<bool>(other.value.value))
      return false;
    if (static_cast<bool>(text.value) != static_cast<bool>(other.text.value))
      return false;
    if (value.value && *value.value != *other.value.value)
      return false;
    if (text.value && *text.value != *other.text.value)
      return false;
    return true;
  }
};
FORY_STRUCT(FieldSharedPtrHolder, value, text);

// Struct with nullable shared_ptr fields
struct FieldNullableSharedPtr {
  fory::field<std::shared_ptr<int32_t>, 0, fory::nullable> nullable_value;
  fory::field<std::shared_ptr<std::string>, 1, fory::nullable> nullable_text;

  bool operator==(const FieldNullableSharedPtr &other) const {
    if (static_cast<bool>(nullable_value.value) !=
        static_cast<bool>(other.nullable_value.value))
      return false;
    if (static_cast<bool>(nullable_text.value) !=
        static_cast<bool>(other.nullable_text.value))
      return false;
    if (nullable_value.value &&
        *nullable_value.value != *other.nullable_value.value)
      return false;
    if (nullable_text.value &&
        *nullable_text.value != *other.nullable_text.value)
      return false;
    return true;
  }
};
FORY_STRUCT(FieldNullableSharedPtr, nullable_value, nullable_text);

// Struct with unique_ptr fields
struct FieldUniquePtrHolder {
  fory::field<std::unique_ptr<int32_t>, 0> value;
  fory::field<std::unique_ptr<int32_t>, 1, fory::nullable> nullable_value;
};
FORY_STRUCT(FieldUniquePtrHolder, value, nullable_value);

// Nested struct for reference tracking tests
struct FieldNode {
  fory::field<int32_t, 0> id;
  fory::field<std::string, 1> name;

  bool operator==(const FieldNode &other) const {
    return id.value == other.id.value && name.value == other.name.value;
  }
};
FORY_STRUCT(FieldNode, id, name);

// Struct with ref tracking for shared_ptr
struct FieldRefTrackingHolder {
  fory::field<std::shared_ptr<FieldNode>, 0, fory::ref> first;
  fory::field<std::shared_ptr<FieldNode>, 1, fory::ref> second;
};
FORY_STRUCT(FieldRefTrackingHolder, first, second);

// Struct with nullable + ref
struct FieldNullableRefHolder {
  fory::field<std::shared_ptr<FieldNode>, 0, fory::nullable, fory::ref> node;
};
FORY_STRUCT(FieldNullableRefHolder, node);

// Struct with not_null + ref
struct FieldNotNullRefHolder {
  fory::field<std::shared_ptr<FieldNode>, 0, fory::not_null, fory::ref> node;
};
FORY_STRUCT(FieldNotNullRefHolder, node);

// Struct with vector of field-wrapped structs
struct FieldVectorHolder {
  fory::field<std::vector<FieldNode>, 0> nodes;

  bool operator==(const FieldVectorHolder &other) const {
    return nodes.value == other.nodes.value;
  }
};
FORY_STRUCT(FieldVectorHolder, nodes);

// Mixed struct: some fields with fory::field, some without
struct MixedFieldStruct {
  fory::field<std::string, 0> field_name;
  int32_t plain_age; // Not wrapped
  fory::field<double, 2> field_score;

  bool operator==(const MixedFieldStruct &other) const {
    return field_name.value == other.field_name.value &&
           plain_age == other.plain_age &&
           field_score.value == other.field_score.value;
  }
};
FORY_STRUCT(MixedFieldStruct, field_name, plain_age, field_score);

// ============================================================================
// Test Implementation
// ============================================================================

namespace fory {
namespace serialization {
namespace test {

inline void register_field_test_types(Fory &fory) {
  uint32_t type_id = 500; // Start from 500 to avoid conflicts

  fory.register_struct<FieldPerson>(type_id++);
  fory.register_struct<FieldOptionalData>(type_id++);
  fory.register_struct<FieldSharedPtrHolder>(type_id++);
  fory.register_struct<FieldNullableSharedPtr>(type_id++);
  fory.register_struct<FieldUniquePtrHolder>(type_id++);
  fory.register_struct<FieldNode>(type_id++);
  fory.register_struct<FieldRefTrackingHolder>(type_id++);
  fory.register_struct<FieldNullableRefHolder>(type_id++);
  fory.register_struct<FieldNotNullRefHolder>(type_id++);
  fory.register_struct<FieldVectorHolder>(type_id++);
  fory.register_struct<MixedFieldStruct>(type_id++);
}

Fory create_fory(bool track_ref = true) {
  return Fory::builder().xlang(true).track_ref(track_ref).build();
}

// ============================================================================
// Primitive Field Tests
// ============================================================================

TEST(FieldSerializerTest, PrimitiveFieldsRoundTrip) {
  auto fory = create_fory();
  register_field_test_types(fory);

  FieldPerson original;
  original.name = "Alice";
  original.age = 30;
  original.score = 95.5;
  original.active = true;

  auto bytes_result = fory.serialize(original);
  ASSERT_TRUE(bytes_result.ok()) << bytes_result.error().to_string();

  auto deser_result =
      fory.deserialize<FieldPerson>(bytes_result->data(), bytes_result->size());
  ASSERT_TRUE(deser_result.ok()) << deser_result.error().to_string();

  EXPECT_EQ(original, deser_result.value());
}

TEST(FieldSerializerTest, PrimitiveFieldsEdgeCases) {
  auto fory = create_fory();
  register_field_test_types(fory);

  FieldPerson original;
  original.name = "";
  original.age = -1;
  original.score = 0.0;
  original.active = false;

  auto bytes_result = fory.serialize(original);
  ASSERT_TRUE(bytes_result.ok()) << bytes_result.error().to_string();

  auto deser_result =
      fory.deserialize<FieldPerson>(bytes_result->data(), bytes_result->size());
  ASSERT_TRUE(deser_result.ok()) << deser_result.error().to_string();

  EXPECT_EQ(original, deser_result.value());
}

// ============================================================================
// Optional Field Tests
// ============================================================================

TEST(FieldSerializerTest, OptionalFieldsAllSet) {
  auto fory = create_fory();
  register_field_test_types(fory);

  FieldOptionalData original;
  original.required_name = "Bob";
  original.optional_age = 25;
  original.optional_email = "bob@example.com";

  auto bytes_result = fory.serialize(original);
  ASSERT_TRUE(bytes_result.ok()) << bytes_result.error().to_string();

  auto deser_result = fory.deserialize<FieldOptionalData>(bytes_result->data(),
                                                          bytes_result->size());
  ASSERT_TRUE(deser_result.ok()) << deser_result.error().to_string();

  EXPECT_EQ(original, deser_result.value());
}

TEST(FieldSerializerTest, OptionalFieldsAllEmpty) {
  auto fory = create_fory();
  register_field_test_types(fory);

  FieldOptionalData original;
  original.required_name = "Charlie";
  original.optional_age = std::nullopt;
  original.optional_email = std::nullopt;

  auto bytes_result = fory.serialize(original);
  ASSERT_TRUE(bytes_result.ok()) << bytes_result.error().to_string();

  auto deser_result = fory.deserialize<FieldOptionalData>(bytes_result->data(),
                                                          bytes_result->size());
  ASSERT_TRUE(deser_result.ok()) << deser_result.error().to_string();

  EXPECT_EQ(original, deser_result.value());
}

TEST(FieldSerializerTest, OptionalFieldsMixed) {
  auto fory = create_fory();
  register_field_test_types(fory);

  FieldOptionalData original;
  original.required_name = "Diana";
  original.optional_age = 35;
  original.optional_email = std::nullopt;

  auto bytes_result = fory.serialize(original);
  ASSERT_TRUE(bytes_result.ok()) << bytes_result.error().to_string();

  auto deser_result = fory.deserialize<FieldOptionalData>(bytes_result->data(),
                                                          bytes_result->size());
  ASSERT_TRUE(deser_result.ok()) << deser_result.error().to_string();

  EXPECT_EQ(original, deser_result.value());
}

// ============================================================================
// Shared Pointer Field Tests
// ============================================================================

TEST(FieldSerializerTest, SharedPtrFieldsNonNullable) {
  auto fory = create_fory();
  register_field_test_types(fory);

  FieldSharedPtrHolder original;
  original.value = std::make_shared<int32_t>(42);
  original.text = std::make_shared<std::string>("hello");

  auto bytes_result = fory.serialize(original);
  ASSERT_TRUE(bytes_result.ok()) << bytes_result.error().to_string();

  auto deser_result = fory.deserialize<FieldSharedPtrHolder>(
      bytes_result->data(), bytes_result->size());
  ASSERT_TRUE(deser_result.ok()) << deser_result.error().to_string();

  EXPECT_EQ(original, deser_result.value());
}

TEST(FieldSerializerTest, NullableSharedPtrWithValues) {
  auto fory = create_fory();
  register_field_test_types(fory);

  FieldNullableSharedPtr original;
  original.nullable_value = std::make_shared<int32_t>(99);
  original.nullable_text = std::make_shared<std::string>("world");

  auto bytes_result = fory.serialize(original);
  ASSERT_TRUE(bytes_result.ok()) << bytes_result.error().to_string();

  auto deser_result = fory.deserialize<FieldNullableSharedPtr>(
      bytes_result->data(), bytes_result->size());
  ASSERT_TRUE(deser_result.ok()) << deser_result.error().to_string();

  EXPECT_EQ(original, deser_result.value());
}

TEST(FieldSerializerTest, NullableSharedPtrWithNulls) {
  auto fory = create_fory();
  register_field_test_types(fory);

  FieldNullableSharedPtr original;
  original.nullable_value = nullptr;
  original.nullable_text = nullptr;

  auto bytes_result = fory.serialize(original);
  ASSERT_TRUE(bytes_result.ok()) << bytes_result.error().to_string();

  auto deser_result = fory.deserialize<FieldNullableSharedPtr>(
      bytes_result->data(), bytes_result->size());
  ASSERT_TRUE(deser_result.ok()) << deser_result.error().to_string();

  EXPECT_EQ(original, deser_result.value());
}

TEST(FieldSerializerTest, NullableSharedPtrMixed) {
  auto fory = create_fory();
  register_field_test_types(fory);

  FieldNullableSharedPtr original;
  original.nullable_value = std::make_shared<int32_t>(123);
  original.nullable_text = nullptr;

  auto bytes_result = fory.serialize(original);
  ASSERT_TRUE(bytes_result.ok()) << bytes_result.error().to_string();

  auto deser_result = fory.deserialize<FieldNullableSharedPtr>(
      bytes_result->data(), bytes_result->size());
  ASSERT_TRUE(deser_result.ok()) << deser_result.error().to_string();

  EXPECT_EQ(original, deser_result.value());
}

// ============================================================================
// Unique Pointer Field Tests
// ============================================================================

TEST(FieldSerializerTest, UniquePtrFieldWithValue) {
  auto fory = create_fory();
  register_field_test_types(fory);

  FieldUniquePtrHolder original;
  original.value = std::make_unique<int32_t>(2025);
  original.nullable_value = std::make_unique<int32_t>(1234);

  auto bytes_result = fory.serialize(original);
  ASSERT_TRUE(bytes_result.ok()) << bytes_result.error().to_string();

  auto deser_result = fory.deserialize<FieldUniquePtrHolder>(
      bytes_result->data(), bytes_result->size());
  ASSERT_TRUE(deser_result.ok()) << deser_result.error().to_string();

  ASSERT_TRUE(deser_result.value().value.value);
  EXPECT_EQ(*deser_result.value().value.value, 2025);
  ASSERT_TRUE(deser_result.value().nullable_value.value);
  EXPECT_EQ(*deser_result.value().nullable_value.value, 1234);
}

TEST(FieldSerializerTest, UniquePtrNullableFieldNull) {
  auto fory = create_fory();
  register_field_test_types(fory);

  FieldUniquePtrHolder original;
  original.value = std::make_unique<int32_t>(999);
  original.nullable_value = nullptr;

  auto bytes_result = fory.serialize(original);
  ASSERT_TRUE(bytes_result.ok()) << bytes_result.error().to_string();

  auto deser_result = fory.deserialize<FieldUniquePtrHolder>(
      bytes_result->data(), bytes_result->size());
  ASSERT_TRUE(deser_result.ok()) << deser_result.error().to_string();

  ASSERT_TRUE(deser_result.value().value.value);
  EXPECT_EQ(*deser_result.value().value.value, 999);
  EXPECT_EQ(deser_result.value().nullable_value.value, nullptr);
}

// ============================================================================
// Reference Tracking Tests
// ============================================================================

TEST(FieldSerializerTest, RefTrackingSameObject) {
  auto fory = create_fory(true);
  register_field_test_types(fory);

  auto shared_node = std::make_shared<FieldNode>();
  shared_node->id = 42;
  shared_node->name = "shared";

  FieldRefTrackingHolder original;
  original.first = shared_node;
  original.second = shared_node; // Same object

  auto bytes_result = fory.serialize(original);
  ASSERT_TRUE(bytes_result.ok()) << bytes_result.error().to_string();

  auto deser_result = fory.deserialize<FieldRefTrackingHolder>(
      bytes_result->data(), bytes_result->size());
  ASSERT_TRUE(deser_result.ok()) << deser_result.error().to_string();

  auto &result = deser_result.value();
  ASSERT_TRUE(result.first.value);
  ASSERT_TRUE(result.second.value);
  EXPECT_EQ(result.first.value->id.value, 42);
  EXPECT_EQ(result.first.value->name.value, "shared");
  EXPECT_EQ(result.second.value->id.value, 42);
  EXPECT_EQ(result.second.value->name.value, "shared");

  // Reference tracking should preserve shared_ptr aliasing
  EXPECT_EQ(result.first.value, result.second.value)
      << "Reference tracking should preserve shared_ptr aliasing";
}

TEST(FieldSerializerTest, RefTrackingDifferentObjects) {
  auto fory = create_fory(true);
  register_field_test_types(fory);

  FieldRefTrackingHolder original;
  original.first = std::make_shared<FieldNode>();
  original.first.value->id = 1;
  original.first.value->name = "first";
  original.second = std::make_shared<FieldNode>();
  original.second.value->id = 2;
  original.second.value->name = "second";

  auto bytes_result = fory.serialize(original);
  ASSERT_TRUE(bytes_result.ok()) << bytes_result.error().to_string();

  auto deser_result = fory.deserialize<FieldRefTrackingHolder>(
      bytes_result->data(), bytes_result->size());
  ASSERT_TRUE(deser_result.ok()) << deser_result.error().to_string();

  auto &result = deser_result.value();
  ASSERT_TRUE(result.first.value);
  ASSERT_TRUE(result.second.value);
  EXPECT_EQ(result.first.value->id.value, 1);
  EXPECT_EQ(result.first.value->name.value, "first");
  EXPECT_EQ(result.second.value->id.value, 2);
  EXPECT_EQ(result.second.value->name.value, "second");

  // Different objects should not share
  EXPECT_NE(result.first.value, result.second.value);
}

TEST(FieldSerializerTest, NullableRefWithValue) {
  auto fory = create_fory(true);
  register_field_test_types(fory);

  FieldNullableRefHolder original;
  original.node = std::make_shared<FieldNode>();
  original.node.value->id = 100;
  original.node.value->name = "nullable_ref";

  auto bytes_result = fory.serialize(original);
  ASSERT_TRUE(bytes_result.ok()) << bytes_result.error().to_string();

  auto deser_result = fory.deserialize<FieldNullableRefHolder>(
      bytes_result->data(), bytes_result->size());
  ASSERT_TRUE(deser_result.ok()) << deser_result.error().to_string();

  auto &result = deser_result.value();
  ASSERT_TRUE(result.node.value);
  EXPECT_EQ(result.node.value->id.value, 100);
  EXPECT_EQ(result.node.value->name.value, "nullable_ref");
}

TEST(FieldSerializerTest, NullableRefWithNull) {
  auto fory = create_fory(true);
  register_field_test_types(fory);

  FieldNullableRefHolder original;
  original.node = nullptr;

  auto bytes_result = fory.serialize(original);
  ASSERT_TRUE(bytes_result.ok()) << bytes_result.error().to_string();

  auto deser_result = fory.deserialize<FieldNullableRefHolder>(
      bytes_result->data(), bytes_result->size());
  ASSERT_TRUE(deser_result.ok()) << deser_result.error().to_string();

  EXPECT_EQ(deser_result.value().node.value, nullptr);
}

TEST(FieldSerializerTest, NotNullRefWithValue) {
  auto fory = create_fory(true);
  register_field_test_types(fory);

  FieldNotNullRefHolder original;
  original.node = std::make_shared<FieldNode>();
  original.node.value->id = 200;
  original.node.value->name = "not_null_ref";

  auto bytes_result = fory.serialize(original);
  ASSERT_TRUE(bytes_result.ok()) << bytes_result.error().to_string();

  auto deser_result = fory.deserialize<FieldNotNullRefHolder>(
      bytes_result->data(), bytes_result->size());
  ASSERT_TRUE(deser_result.ok()) << deser_result.error().to_string();

  auto &result = deser_result.value();
  ASSERT_TRUE(result.node.value);
  EXPECT_EQ(result.node.value->id.value, 200);
  EXPECT_EQ(result.node.value->name.value, "not_null_ref");
}

// ============================================================================
// Container Field Tests
// ============================================================================

TEST(FieldSerializerTest, VectorOfFieldStructs) {
  auto fory = create_fory();
  register_field_test_types(fory);

  FieldVectorHolder original;
  for (int i = 0; i < 5; ++i) {
    FieldNode node;
    node.id = i;
    node.name = "node_" + std::to_string(i);
    original.nodes.value.push_back(node);
  }

  auto bytes_result = fory.serialize(original);
  ASSERT_TRUE(bytes_result.ok()) << bytes_result.error().to_string();

  auto deser_result = fory.deserialize<FieldVectorHolder>(bytes_result->data(),
                                                          bytes_result->size());
  ASSERT_TRUE(deser_result.ok()) << deser_result.error().to_string();

  EXPECT_EQ(original, deser_result.value());
}

TEST(FieldSerializerTest, EmptyVectorField) {
  auto fory = create_fory();
  register_field_test_types(fory);

  FieldVectorHolder original;
  // Empty vector

  auto bytes_result = fory.serialize(original);
  ASSERT_TRUE(bytes_result.ok()) << bytes_result.error().to_string();

  auto deser_result = fory.deserialize<FieldVectorHolder>(bytes_result->data(),
                                                          bytes_result->size());
  ASSERT_TRUE(deser_result.ok()) << deser_result.error().to_string();

  EXPECT_EQ(original, deser_result.value());
}

// ============================================================================
// Mixed Field Tests
// ============================================================================

TEST(FieldSerializerTest, MixedFieldStruct) {
  auto fory = create_fory();
  register_field_test_types(fory);

  MixedFieldStruct original;
  original.field_name = "mixed";
  original.plain_age = 42;
  original.field_score = 88.5;

  auto bytes_result = fory.serialize(original);
  ASSERT_TRUE(bytes_result.ok()) << bytes_result.error().to_string();

  auto deser_result = fory.deserialize<MixedFieldStruct>(bytes_result->data(),
                                                         bytes_result->size());
  ASSERT_TRUE(deser_result.ok()) << deser_result.error().to_string();

  EXPECT_EQ(original, deser_result.value());
}

// ============================================================================
// Field Metadata Compile-time Tests
// ============================================================================

TEST(FieldSerializerTest, FieldMetadataCompileTime) {
  // Verify compile-time field metadata extraction
  using PersonType = FieldPerson;

  // Check that field types are correctly detected
  static_assert(is_fory_field_v<decltype(PersonType::name)>);
  static_assert(is_fory_field_v<decltype(PersonType::age)>);
  static_assert(is_fory_field_v<decltype(PersonType::score)>);
  static_assert(is_fory_field_v<decltype(PersonType::active)>);

  // Check tag IDs
  static_assert(decltype(PersonType::name)::tag_id == 0);
  static_assert(decltype(PersonType::age)::tag_id == 1);
  static_assert(decltype(PersonType::score)::tag_id == 2);
  static_assert(decltype(PersonType::active)::tag_id == 3);

  // Check nullability
  static_assert(!decltype(PersonType::name)::is_nullable);
  static_assert(!decltype(PersonType::age)::is_nullable);

  // Optional fields are inherently nullable
  static_assert(decltype(FieldOptionalData::optional_age)::is_nullable);
  static_assert(decltype(FieldOptionalData::optional_email)::is_nullable);

  // Nullable shared_ptr
  static_assert(decltype(FieldNullableSharedPtr::nullable_value)::is_nullable);
  static_assert(!decltype(FieldSharedPtrHolder::value)::is_nullable);

  // Ref tracking
  static_assert(decltype(FieldRefTrackingHolder::first)::track_ref);
  static_assert(!decltype(FieldSharedPtrHolder::value)::track_ref);

  // not_null doesn't change is_nullable for already non-nullable
  static_assert(!decltype(FieldNotNullRefHolder::node)::is_nullable);
  static_assert(decltype(FieldNotNullRefHolder::node)::track_ref);
}

} // namespace test
} // namespace serialization
} // namespace fory

int main(int argc, char **argv) {
  ::testing::InitGoogleTest(&argc, argv);
  return RUN_ALL_TESTS();
}
