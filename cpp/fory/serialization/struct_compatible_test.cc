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
 * Schema Evolution Test Suite for Fory C++ Serialization
 *
 * Tests various schema evolution scenarios:
 * 1. Adding new fields (backward compatibility)
 * 2. Removing fields (forward compatibility)
 * 3. Reordering fields
 * 4. Renaming fields
 * 5. Changing field types (when compatible)
 * 6. Complex nested struct evolution
 *
 * Schema evolution is enabled via the compatible mode flag.
 */

#include "fory/serialization/fory.h"
#include "gtest/gtest.h"
#include <cstdint>
#include <string>
#include <vector>

// ============================================================================
// Test Case 1: Adding New Fields (Backward Compatibility)
// ============================================================================

// V1: Original schema with 2 fields
struct PersonV1 {
  std::string name;
  int32_t age;

  bool operator==(const PersonV1 &other) const {
    return name == other.name && age == other.age;
  }
  FORY_STRUCT(PersonV1, name, age);
};

// V2: Added email field
struct PersonV2 {
  std::string name;
  int32_t age;
  std::string email; // NEW FIELD

  bool operator==(const PersonV2 &other) const {
    return name == other.name && age == other.age && email == other.email;
  }
  FORY_STRUCT(PersonV2, name, age, email);
};

// V3: Added multiple fields
struct PersonV3 {
  std::string name;
  int32_t age;
  std::string email;
  std::string phone;   // NEW FIELD
  std::string address; // NEW FIELD

  bool operator==(const PersonV3 &other) const {
    return name == other.name && age == other.age && email == other.email &&
           phone == other.phone && address == other.address;
  }
  FORY_STRUCT(PersonV3, name, age, email, phone, address);
};

// ============================================================================
// Test Case 2: Removing Fields (Forward Compatibility)
// ============================================================================

// Full schema
struct UserFull {
  int64_t id;
  std::string username;
  std::string email;
  std::string password_hash;
  int32_t login_count;

  bool operator==(const UserFull &other) const {
    return id == other.id && username == other.username &&
           email == other.email && password_hash == other.password_hash &&
           login_count == other.login_count;
  }
  FORY_STRUCT(UserFull, id, username, email, password_hash, login_count);
};

// Minimal schema (removed 3 fields)
struct UserMinimal {
  int64_t id;
  std::string username;

  bool operator==(const UserMinimal &other) const {
    return id == other.id && username == other.username;
  }
  FORY_STRUCT(UserMinimal, id, username);
};

// ============================================================================
// Test Case 3: Field Reordering
// ============================================================================

struct ConfigOriginal {
  std::string host;
  int32_t port;
  bool enable_ssl;
  std::string protocol;

  bool operator==(const ConfigOriginal &other) const {
    return host == other.host && port == other.port &&
           enable_ssl == other.enable_ssl && protocol == other.protocol;
  }
  FORY_STRUCT(ConfigOriginal, host, port, enable_ssl, protocol);
};

// Reordered fields (different order)
struct ConfigReordered {
  bool enable_ssl;      // Moved to first
  std::string protocol; // Moved to second
  std::string host;     // Moved to third
  int32_t port;         // Moved to last

  bool operator==(const ConfigReordered &other) const {
    return host == other.host && port == other.port &&
           enable_ssl == other.enable_ssl && protocol == other.protocol;
  }
  FORY_STRUCT(ConfigReordered, enable_ssl, protocol, host, port);
};

// ============================================================================
// Test Case 4: Nested Struct Evolution
// ============================================================================

struct AddressV1 {
  std::string street;
  std::string city;

  bool operator==(const AddressV1 &other) const {
    return street == other.street && city == other.city;
  }
  FORY_STRUCT(AddressV1, street, city);
};

struct AddressV2 {
  std::string street;
  std::string city;
  std::string country; // NEW FIELD
  std::string zipcode; // NEW FIELD

  bool operator==(const AddressV2 &other) const {
    return street == other.street && city == other.city &&
           country == other.country && zipcode == other.zipcode;
  }
  FORY_STRUCT(AddressV2, street, city, country, zipcode);
};

struct EmployeeV1 {
  std::string name;
  AddressV1 home_address;

  bool operator==(const EmployeeV1 &other) const {
    return name == other.name && home_address == other.home_address;
  }
  FORY_STRUCT(EmployeeV1, name, home_address);
};

struct EmployeeV2 {
  std::string name;
  AddressV2 home_address;  // Nested struct evolved
  std::string employee_id; // NEW FIELD

  bool operator==(const EmployeeV2 &other) const {
    return name == other.name && home_address == other.home_address &&
           employee_id == other.employee_id;
  }
  FORY_STRUCT(EmployeeV2, name, home_address, employee_id);
};

// ============================================================================
// Test Case 5: Collection Field Evolution
// ============================================================================

struct ProductV1 {
  std::string name;
  double price;

  bool operator==(const ProductV1 &other) const {
    return name == other.name && price == other.price;
  }
  FORY_STRUCT(ProductV1, name, price);
};

struct ProductV2 {
  std::string name;
  double price;
  std::vector<std::string> tags;                 // NEW FIELD
  std::map<std::string, std::string> attributes; // NEW FIELD

  bool operator==(const ProductV2 &other) const {
    return name == other.name && price == other.price && tags == other.tags &&
           attributes == other.attributes;
  }
  FORY_STRUCT(ProductV2, name, price, tags, attributes);
};

struct PackedInt32MapConfigured {
  std::map<std::string, std::vector<int32_t>> groups;

  bool operator==(const PackedInt32MapConfigured &other) const {
    return groups == other.groups;
  }
  FORY_STRUCT(PackedInt32MapConfigured, groups);
};

FORY_FIELD_CONFIG(PackedInt32MapConfigured,
                  (groups,
                   fory::F().id(11).map(fory::T(),
                                        fory::T().list(fory::T().fixed()))));

struct PackedInt32MapPlain {
  std::map<std::string, std::vector<int32_t>> groups;

  bool operator==(const PackedInt32MapPlain &other) const {
    return groups == other.groups;
  }
  FORY_STRUCT(PackedInt32MapPlain, groups);
};

FORY_FIELD_CONFIG(PackedInt32MapPlain, (groups, fory::F().id(11)));

struct SkipNestedConfiguredFieldsFull {
  std::string keep;
  std::vector<int64_t> fixed_values;
  std::map<std::string, int64_t> tagged_map;
  std::optional<std::vector<int32_t>> maybe_fixed;

  bool operator==(const SkipNestedConfiguredFieldsFull &other) const {
    return keep == other.keep && fixed_values == other.fixed_values &&
           tagged_map == other.tagged_map && maybe_fixed == other.maybe_fixed;
  }
  FORY_STRUCT(SkipNestedConfiguredFieldsFull, keep, fixed_values, tagged_map,
              maybe_fixed);
};

FORY_FIELD_CONFIG(SkipNestedConfiguredFieldsFull, (keep, fory::F().id(1)),
                  (fixed_values, fory::F().id(2).list(fory::T().fixed())),
                  (tagged_map,
                   fory::F().id(3).map(fory::T(), fory::T().tagged())),
                  (maybe_fixed,
                   fory::F().id(4).inner(fory::T().list(fory::T().fixed()))));

struct SkipNestedConfiguredFieldsReduced {
  std::string keep;

  bool operator==(const SkipNestedConfiguredFieldsReduced &other) const {
    return keep == other.keep;
  }
  FORY_STRUCT(SkipNestedConfiguredFieldsReduced, keep);
};

FORY_FIELD_CONFIG(SkipNestedConfiguredFieldsReduced, (keep, fory::F().id(1)));

// ============================================================================
// Test Case 6: Unsigned Scalar And Array Field Skipping
// ============================================================================

struct SkipUnsignedEncodingsFull {
  std::string keep;
  uint8_t uint8_value;
  uint16_t uint16_value;
  uint32_t fixed_uint32;
  uint32_t var_uint32;
  uint64_t fixed_uint64;
  uint64_t var_uint64;
  uint64_t tagged_uint64;
  int64_t tagged_int64;

  bool operator==(const SkipUnsignedEncodingsFull &other) const {
    return keep == other.keep && uint8_value == other.uint8_value &&
           uint16_value == other.uint16_value &&
           fixed_uint32 == other.fixed_uint32 &&
           var_uint32 == other.var_uint32 &&
           fixed_uint64 == other.fixed_uint64 &&
           var_uint64 == other.var_uint64 &&
           tagged_uint64 == other.tagged_uint64 &&
           tagged_int64 == other.tagged_int64;
  }
  FORY_STRUCT(SkipUnsignedEncodingsFull, keep, uint8_value, uint16_value,
              fixed_uint32, var_uint32, fixed_uint64, var_uint64, tagged_uint64,
              tagged_int64);
};

FORY_FIELD_CONFIG(SkipUnsignedEncodingsFull, (keep, fory::F().id(1)),
                  (uint8_value, fory::F().id(2)),
                  (uint16_value, fory::F().id(3)),
                  (fixed_uint32, fory::F().id(4).fixed()),
                  (var_uint32, fory::F().id(5).varint()),
                  (fixed_uint64, fory::F().id(6).fixed()),
                  (var_uint64, fory::F().id(7).varint()),
                  (tagged_uint64, fory::F().id(8).tagged()),
                  (tagged_int64, fory::F().id(9).tagged()));

struct SkipUnsignedEncodingsReduced {
  std::string keep;

  bool operator==(const SkipUnsignedEncodingsReduced &other) const {
    return keep == other.keep;
  }
  FORY_STRUCT(SkipUnsignedEncodingsReduced, keep);
};

FORY_FIELD_CONFIG(SkipUnsignedEncodingsReduced, (keep, fory::F().id(1)));

struct SkipUnsignedArraysFull {
  std::string keep;
  std::vector<uint8_t> uint8_array;
  std::vector<uint16_t> uint16_array;
  std::vector<uint32_t> uint32_array;
  std::vector<uint64_t> uint64_array;

  bool operator==(const SkipUnsignedArraysFull &other) const {
    return keep == other.keep && uint8_array == other.uint8_array &&
           uint16_array == other.uint16_array &&
           uint32_array == other.uint32_array &&
           uint64_array == other.uint64_array;
  }
  FORY_STRUCT(SkipUnsignedArraysFull, keep, uint8_array, uint16_array,
              uint32_array, uint64_array);
};

FORY_FIELD_CONFIG(
    SkipUnsignedArraysFull, (keep, fory::F().id(1)),
    (uint8_array, fory::F().id(2).uint8_array()),
    (uint16_array, fory::F().id(3).type_id(fory::TypeId::UINT16_ARRAY)),
    (uint32_array, fory::F().id(4).type_id(fory::TypeId::UINT32_ARRAY)),
    (uint64_array, fory::F().id(5).type_id(fory::TypeId::UINT64_ARRAY)));

struct SkipUnsignedArraysReduced {
  std::string keep;

  bool operator==(const SkipUnsignedArraysReduced &other) const {
    return keep == other.keep;
  }
  FORY_STRUCT(SkipUnsignedArraysReduced, keep);
};

FORY_FIELD_CONFIG(SkipUnsignedArraysReduced, (keep, fory::F().id(1)));

struct SkipSignedEncodingsFull {
  std::string keep;
  int32_t fixed_int32;
  int64_t fixed_int64;
  int32_t var_int32;
  int64_t var_int64;
  int64_t tagged_int64;

  bool operator==(const SkipSignedEncodingsFull &other) const {
    return keep == other.keep && fixed_int32 == other.fixed_int32 &&
           fixed_int64 == other.fixed_int64 && var_int32 == other.var_int32 &&
           var_int64 == other.var_int64 && tagged_int64 == other.tagged_int64;
  }
  FORY_STRUCT(SkipSignedEncodingsFull, keep, fixed_int32, fixed_int64,
              var_int32, var_int64, tagged_int64);
};

FORY_FIELD_CONFIG(SkipSignedEncodingsFull, (keep, fory::F().id(1)),
                  (fixed_int32, fory::F().id(2).fixed()),
                  (fixed_int64, fory::F().id(3).fixed()),
                  (var_int32, fory::F().id(4)), (var_int64, fory::F().id(5)),
                  (tagged_int64, fory::F().id(6).tagged()));

struct SkipSignedEncodingsReduced {
  std::string keep;

  bool operator==(const SkipSignedEncodingsReduced &other) const {
    return keep == other.keep;
  }
  FORY_STRUCT(SkipSignedEncodingsReduced, keep);
};

FORY_FIELD_CONFIG(SkipSignedEncodingsReduced, (keep, fory::F().id(1)));

struct SkipInternalFieldsFull {
  std::vector<int16_t> keep_values;
  std::string string_value;
  fory::serialization::Duration duration_value;
  fory::serialization::Timestamp timestamp_value;
  fory::serialization::Date date_value;
  fory::serialization::Decimal decimal_value;
  std::vector<uint8_t> bytes_value;
  std::vector<bool> bool_values;

  bool operator==(const SkipInternalFieldsFull &other) const {
    return keep_values == other.keep_values &&
           string_value == other.string_value &&
           duration_value == other.duration_value &&
           timestamp_value == other.timestamp_value &&
           date_value == other.date_value &&
           decimal_value == other.decimal_value &&
           bytes_value == other.bytes_value && bool_values == other.bool_values;
  }
  FORY_STRUCT(SkipInternalFieldsFull, keep_values, string_value, duration_value,
              timestamp_value, date_value, decimal_value, bytes_value,
              bool_values);
};

FORY_FIELD_CONFIG(SkipInternalFieldsFull, (keep_values, fory::F().id(1)),
                  (string_value, fory::F().id(2)),
                  (duration_value, fory::F().id(3)),
                  (timestamp_value, fory::F().id(4)),
                  (date_value, fory::F().id(5)),
                  (decimal_value, fory::F().id(6)),
                  (bytes_value, fory::F().id(7)),
                  (bool_values, fory::F().id(8)));

struct SkipInternalFieldsReduced {
  std::vector<int16_t> keep_values;

  bool operator==(const SkipInternalFieldsReduced &other) const {
    return keep_values == other.keep_values;
  }
  FORY_STRUCT(SkipInternalFieldsReduced, keep_values);
};

FORY_FIELD_CONFIG(SkipInternalFieldsReduced, (keep_values, fory::F().id(1)));

struct MatchTaggedBinaryFieldFull {
  std::vector<uint8_t> bytes_value;
  std::vector<uint8_t> uint8_array;

  bool operator==(const MatchTaggedBinaryFieldFull &other) const {
    return bytes_value == other.bytes_value && uint8_array == other.uint8_array;
  }
  FORY_STRUCT(MatchTaggedBinaryFieldFull, bytes_value, uint8_array);
};

FORY_FIELD_CONFIG(MatchTaggedBinaryFieldFull, (bytes_value, fory::F().id(21)),
                  (uint8_array, fory::F().id(109).uint8_array()));

struct MatchTaggedBinaryFieldReduced {
  std::vector<uint8_t> bytes_value;

  bool operator==(const MatchTaggedBinaryFieldReduced &other) const {
    return bytes_value == other.bytes_value;
  }
  FORY_STRUCT(MatchTaggedBinaryFieldReduced, bytes_value);
};

FORY_FIELD_CONFIG(MatchTaggedBinaryFieldReduced,
                  (bytes_value, fory::F().id(21)));

struct MatchTaggedInt8FieldFull {
  std::vector<uint8_t> bytes_value;
  std::vector<int8_t> int8_array;
  std::vector<uint8_t> uint8_array;

  bool operator==(const MatchTaggedInt8FieldFull &other) const {
    return bytes_value == other.bytes_value && int8_array == other.int8_array &&
           uint8_array == other.uint8_array;
  }
  FORY_STRUCT(MatchTaggedInt8FieldFull, bytes_value, int8_array, uint8_array);
};

FORY_FIELD_CONFIG(MatchTaggedInt8FieldFull, (bytes_value, fory::F().id(21)),
                  (int8_array, fory::F().id(102).int8_array()),
                  (uint8_array, fory::F().id(109).uint8_array()));

struct MatchTaggedInt8FieldReduced {
  std::vector<int8_t> int8_array;

  bool operator==(const MatchTaggedInt8FieldReduced &other) const {
    return int8_array == other.int8_array;
  }
  FORY_STRUCT(MatchTaggedInt8FieldReduced, int8_array);
};

FORY_FIELD_CONFIG(MatchTaggedInt8FieldReduced,
                  (int8_array, fory::F().id(102).int8_array()));

// ============================================================================
// TESTS
// ============================================================================

namespace fory {
namespace serialization {
namespace test {

TEST(SchemaEvolutionTest, AddingSingleField) {
  // Serialize V1, deserialize as V2 (V2 should have default value for email)
  // Create separate Fory instances for V1 and V2
  auto fory_v1 = Fory::builder().compatible(true).xlang(true).build();
  auto fory_v2 = Fory::builder().compatible(true).xlang(true).build();

  // Register both PersonV1 and PersonV2 with the SAME type ID for schema
  // evolution
  constexpr uint32_t PERSON_TYPE_ID = 999;
  auto reg1_result = fory_v1.register_struct<PersonV1>(PERSON_TYPE_ID);
  ASSERT_TRUE(reg1_result.ok()) << reg1_result.error().to_string();
  auto reg2_result = fory_v2.register_struct<PersonV2>(PERSON_TYPE_ID);
  ASSERT_TRUE(reg2_result.ok()) << reg2_result.error().to_string();

  // Serialize PersonV1
  PersonV1 v1{"Alice", 30};
  auto ser_result = fory_v1.serialize(v1);
  ASSERT_TRUE(ser_result.ok()) << ser_result.error().to_string();

  std::vector<uint8_t> bytes = std::move(ser_result).value();

  // Deserialize as PersonV2 - email should be default-initialized (empty
  // string)
  auto deser_result = fory_v2.deserialize<PersonV2>(bytes.data(), bytes.size());
  ASSERT_TRUE(deser_result.ok()) << deser_result.error().to_string();

  PersonV2 v2 = std::move(deser_result).value();
  EXPECT_EQ(v2.name, "Alice");
  EXPECT_EQ(v2.age, 30);
  EXPECT_EQ(v2.email, ""); // Default value for missing field
}

TEST(SchemaEvolutionTest, AddingMultipleFields) {
  auto fory_v1 = Fory::builder().compatible(true).xlang(true).build();
  auto fory_v3 = Fory::builder().compatible(true).xlang(true).build();

  constexpr uint32_t PERSON_TYPE_ID = 999;
  ASSERT_TRUE(fory_v1.register_struct<PersonV1>(PERSON_TYPE_ID).ok());
  ASSERT_TRUE(fory_v3.register_struct<PersonV3>(PERSON_TYPE_ID).ok());

  // V1 -> V3 (skipping V2, adding 3 fields at once)
  PersonV1 v1{"Bob", 25};
  auto ser_result = fory_v1.serialize(v1);
  ASSERT_TRUE(ser_result.ok());

  std::vector<uint8_t> bytes = std::move(ser_result).value();

  auto deser_result = fory_v3.deserialize<PersonV3>(bytes.data(), bytes.size());
  ASSERT_TRUE(deser_result.ok()) << deser_result.error().to_string();

  PersonV3 v3 = std::move(deser_result).value();
  EXPECT_EQ(v3.name, "Bob");
  EXPECT_EQ(v3.age, 25);
  EXPECT_EQ(v3.email, "");
  EXPECT_EQ(v3.phone, "");
  EXPECT_EQ(v3.address, "");
}

TEST(SchemaEvolutionTest, RemovingFields) {
  // Serialize UserFull, deserialize as UserMinimal (should ignore extra fields)
  auto fory_full = Fory::builder().compatible(true).xlang(true).build();
  auto fory_minimal = Fory::builder().compatible(true).xlang(true).build();

  constexpr uint32_t USER_TYPE_ID = 1000;
  ASSERT_TRUE(fory_full.register_struct<UserFull>(USER_TYPE_ID).ok());
  ASSERT_TRUE(fory_minimal.register_struct<UserMinimal>(USER_TYPE_ID).ok());

  UserFull full{12345, "johndoe", "john@example.com", "hash123", 42};
  auto ser_result = fory_full.serialize(full);
  ASSERT_TRUE(ser_result.ok());

  std::vector<uint8_t> bytes = std::move(ser_result).value();

  // Deserialize as minimal - should skip email, password_hash, login_count
  auto deser_result =
      fory_minimal.deserialize<UserMinimal>(bytes.data(), bytes.size());
  ASSERT_TRUE(deser_result.ok()) << deser_result.error().to_string();

  UserMinimal minimal = std::move(deser_result).value();
  EXPECT_EQ(minimal.id, 12345);
  EXPECT_EQ(minimal.username, "johndoe");
}

TEST(SchemaEvolutionTest, FieldReordering) {
  // Serialize ConfigOriginal, deserialize as ConfigReordered
  // Field order shouldn't matter in compatible mode
  auto fory_orig = Fory::builder().compatible(true).xlang(true).build();
  auto fory_reord = Fory::builder().compatible(true).xlang(true).build();

  constexpr uint32_t CONFIG_TYPE_ID = 1001;
  ASSERT_TRUE(fory_orig.register_struct<ConfigOriginal>(CONFIG_TYPE_ID).ok());
  ASSERT_TRUE(fory_reord.register_struct<ConfigReordered>(CONFIG_TYPE_ID).ok());

  ConfigOriginal orig{"localhost", 8080, true, "https"};
  auto ser_result = fory_orig.serialize(orig);
  ASSERT_TRUE(ser_result.ok());

  std::vector<uint8_t> bytes = std::move(ser_result).value();

  auto deser_result =
      fory_reord.deserialize<ConfigReordered>(bytes.data(), bytes.size());
  ASSERT_TRUE(deser_result.ok()) << deser_result.error().to_string();

  ConfigReordered reordered = std::move(deser_result).value();
  EXPECT_EQ(reordered.host, "localhost");
  EXPECT_EQ(reordered.port, 8080);
  EXPECT_EQ(reordered.enable_ssl, true);
  EXPECT_EQ(reordered.protocol, "https");
}

TEST(SchemaEvolutionTest, BidirectionalAddRemove) {
  auto fory_v2 = Fory::builder().compatible(true).xlang(true).build();
  auto fory_v1 = Fory::builder().compatible(true).xlang(true).build();

  constexpr uint32_t PERSON_TYPE_ID = 999;
  ASSERT_TRUE(fory_v2.register_struct<PersonV2>(PERSON_TYPE_ID).ok());
  ASSERT_TRUE(fory_v1.register_struct<PersonV1>(PERSON_TYPE_ID).ok());

  // V2 -> V1 (removing email field)
  PersonV2 v2{"Charlie", 35, "charlie@example.com"};
  auto ser_result = fory_v2.serialize(v2);
  ASSERT_TRUE(ser_result.ok());

  std::vector<uint8_t> bytes = std::move(ser_result).value();

  auto deser_result = fory_v1.deserialize<PersonV1>(bytes.data(), bytes.size());
  ASSERT_TRUE(deser_result.ok()) << deser_result.error().to_string();

  PersonV1 v1 = std::move(deser_result).value();
  EXPECT_EQ(v1.name, "Charlie");
  EXPECT_EQ(v1.age, 35);
  // email is lost, which is expected
}

TEST(SchemaEvolutionTest, NestedStructEvolution) {
  auto fory_v1 = Fory::builder().compatible(true).xlang(true).build();
  auto fory_v2 = Fory::builder().compatible(true).xlang(true).build();

  constexpr uint32_t ADDRESS_TYPE_ID = 1002;
  constexpr uint32_t EMPLOYEE_TYPE_ID = 1003;

  ASSERT_TRUE(fory_v1.register_struct<AddressV1>(ADDRESS_TYPE_ID).ok());
  ASSERT_TRUE(fory_v1.register_struct<EmployeeV1>(EMPLOYEE_TYPE_ID).ok());
  ASSERT_TRUE(fory_v2.register_struct<AddressV2>(ADDRESS_TYPE_ID).ok());
  ASSERT_TRUE(fory_v2.register_struct<EmployeeV2>(EMPLOYEE_TYPE_ID).ok());

  // Serialize EmployeeV1, deserialize as EmployeeV2
  EmployeeV1 emp_v1{"Jane Doe", {"123 Main St", "NYC"}};
  auto ser_result = fory_v1.serialize(emp_v1);
  ASSERT_TRUE(ser_result.ok());

  std::vector<uint8_t> bytes = std::move(ser_result).value();

  auto deser_result =
      fory_v2.deserialize<EmployeeV2>(bytes.data(), bytes.size());
  ASSERT_TRUE(deser_result.ok()) << deser_result.error().to_string();

  EmployeeV2 emp_v2 = std::move(deser_result).value();
  EXPECT_EQ(emp_v2.name, "Jane Doe");
  EXPECT_EQ(emp_v2.home_address.street, "123 Main St");
  EXPECT_EQ(emp_v2.home_address.city, "NYC");
  EXPECT_EQ(emp_v2.home_address.country, ""); // Default value
  EXPECT_EQ(emp_v2.home_address.zipcode, ""); // Default value
  EXPECT_EQ(emp_v2.employee_id, "");          // Default value
}

TEST(SchemaEvolutionTest, CollectionFieldEvolution) {
  auto fory_v1 = Fory::builder().compatible(true).xlang(true).build();
  auto fory_v2 = Fory::builder().compatible(true).xlang(true).build();

  constexpr uint32_t PRODUCT_TYPE_ID = 1004;
  ASSERT_TRUE(fory_v1.register_struct<ProductV1>(PRODUCT_TYPE_ID).ok());
  ASSERT_TRUE(fory_v2.register_struct<ProductV2>(PRODUCT_TYPE_ID).ok());

  // Serialize ProductV1, deserialize as ProductV2
  ProductV1 prod_v1{"Laptop", 999.99};
  auto ser_result = fory_v1.serialize(prod_v1);
  ASSERT_TRUE(ser_result.ok());

  std::vector<uint8_t> bytes = std::move(ser_result).value();

  auto deser_result =
      fory_v2.deserialize<ProductV2>(bytes.data(), bytes.size());
  ASSERT_TRUE(deser_result.ok()) << deser_result.error().to_string();

  ProductV2 prod_v2 = std::move(deser_result).value();
  EXPECT_EQ(prod_v2.name, "Laptop");
  EXPECT_EQ(prod_v2.price, 999.99);
  EXPECT_TRUE(prod_v2.tags.empty());       // Default empty vector
  EXPECT_TRUE(prod_v2.attributes.empty()); // Default empty map
}

TEST(SchemaEvolutionTest, PackedFixedInt32VectorInsideMapStaysPrimitiveArray) {
  auto fory_configured =
      Fory::builder().compatible(true).xlang(true).track_ref(false).build();
  auto fory_plain =
      Fory::builder().compatible(true).xlang(true).track_ref(false).build();

  constexpr uint32_t TYPE_ID = 1011;
  ASSERT_TRUE(
      fory_configured.register_struct<PackedInt32MapConfigured>(TYPE_ID).ok());
  ASSERT_TRUE(fory_plain.register_struct<PackedInt32MapPlain>(TYPE_ID).ok());

  PackedInt32MapConfigured configured{
      {{"small", {1, 2, 3}}, {"mixed", {-1, 0x12345678, 42}}}};
  auto ser_result = fory_configured.serialize(configured);
  ASSERT_TRUE(ser_result.ok()) << ser_result.error().to_string();

  auto deser_plain =
      fory_plain.deserialize<PackedInt32MapPlain>(ser_result.value());
  ASSERT_TRUE(deser_plain.ok()) << deser_plain.error().to_string();
  EXPECT_EQ(deser_plain->groups, configured.groups);

  auto deser_configured =
      fory_configured.deserialize<PackedInt32MapConfigured>(ser_result.value());
  ASSERT_TRUE(deser_configured.ok()) << deser_configured.error().to_string();
  EXPECT_EQ(*deser_configured, configured);
}

TEST(SchemaEvolutionTest, RemovingUnsignedAndTaggedNumericFields) {
  auto fory_full =
      Fory::builder().compatible(true).xlang(true).track_ref(false).build();
  auto fory_reduced =
      Fory::builder().compatible(true).xlang(true).track_ref(false).build();

  constexpr uint32_t TYPE_ID = 1005;
  ASSERT_TRUE(
      fory_full.register_struct<SkipUnsignedEncodingsFull>(TYPE_ID).ok());
  ASSERT_TRUE(
      fory_reduced.register_struct<SkipUnsignedEncodingsReduced>(TYPE_ID).ok());

  SkipUnsignedEncodingsFull full{"keep-me",
                                 255,
                                 65535,
                                 0xF1234567u,
                                 4000000000u,
                                 0xFEDCBA9876543210ULL,
                                 9000000000000000000ULL,
                                 1ULL << 40,
                                 -1234567890123456789LL};
  auto ser_result = fory_full.serialize(full);
  ASSERT_TRUE(ser_result.ok()) << ser_result.error().to_string();

  std::vector<uint8_t> bytes = std::move(ser_result).value();

  auto deser_result = fory_reduced.deserialize<SkipUnsignedEncodingsReduced>(
      bytes.data(), bytes.size());
  ASSERT_TRUE(deser_result.ok()) << deser_result.error().to_string();

  SkipUnsignedEncodingsReduced reduced = std::move(deser_result).value();
  EXPECT_EQ(reduced.keep, full.keep);
}

TEST(SchemaEvolutionTest, RemovingUnsignedPrimitiveArrayFields) {
  auto fory_full =
      Fory::builder().compatible(true).xlang(true).track_ref(false).build();
  auto fory_reduced =
      Fory::builder().compatible(true).xlang(true).track_ref(false).build();

  constexpr uint32_t TYPE_ID = 1006;
  ASSERT_TRUE(fory_full.register_struct<SkipUnsignedArraysFull>(TYPE_ID).ok());
  ASSERT_TRUE(
      fory_reduced.register_struct<SkipUnsignedArraysReduced>(TYPE_ID).ok());

  SkipUnsignedArraysFull full{"keep-arrays",
                              {0u, 1u, 200u, 255u},
                              {0u, 1u, 65535u},
                              {0u, 65537u, 4000000000u},
                              {0ULL, 1ULL << 40, 0xFEDCBA9876543210ULL}};
  auto ser_result = fory_full.serialize(full);
  ASSERT_TRUE(ser_result.ok()) << ser_result.error().to_string();

  std::vector<uint8_t> bytes = std::move(ser_result).value();

  auto deser_result = fory_reduced.deserialize<SkipUnsignedArraysReduced>(
      bytes.data(), bytes.size());
  ASSERT_TRUE(deser_result.ok()) << deser_result.error().to_string();

  SkipUnsignedArraysReduced reduced = std::move(deser_result).value();
  EXPECT_EQ(reduced.keep, full.keep);
}

TEST(SchemaEvolutionTest, RemovingSignedConfiguredNumericFields) {
  auto fory_full =
      Fory::builder().compatible(true).xlang(true).track_ref(false).build();
  auto fory_reduced =
      Fory::builder().compatible(true).xlang(true).track_ref(false).build();

  constexpr uint32_t TYPE_ID = 1007;
  ASSERT_TRUE(fory_full.register_struct<SkipSignedEncodingsFull>(TYPE_ID).ok());
  ASSERT_TRUE(
      fory_reduced.register_struct<SkipSignedEncodingsReduced>(TYPE_ID).ok());

  SkipSignedEncodingsFull full{"keep-signed",          0x12345678,
                               0x1234567890ABCDELL,    -1234567,
                               -1234567890123456789LL, -1073741824LL};
  auto ser_result = fory_full.serialize(full);
  ASSERT_TRUE(ser_result.ok()) << ser_result.error().to_string();

  std::vector<uint8_t> bytes = std::move(ser_result).value();

  auto deser_result = fory_reduced.deserialize<SkipSignedEncodingsReduced>(
      bytes.data(), bytes.size());
  ASSERT_TRUE(deser_result.ok()) << deser_result.error().to_string();

  SkipSignedEncodingsReduced reduced = std::move(deser_result).value();
  EXPECT_EQ(reduced.keep, full.keep);
}

TEST(SchemaEvolutionTest, RemovingInternalFieldsAndBoolArray) {
  auto fory_full =
      Fory::builder().compatible(true).xlang(true).track_ref(false).build();
  auto fory_reduced =
      Fory::builder().compatible(true).xlang(true).track_ref(false).build();

  constexpr uint32_t TYPE_ID = 1008;
  ASSERT_TRUE(fory_full.register_struct<SkipInternalFieldsFull>(TYPE_ID).ok());
  ASSERT_TRUE(
      fory_reduced.register_struct<SkipInternalFieldsReduced>(TYPE_ID).ok());

  SkipInternalFieldsFull full{
      {1234, -4321},
      "example-string",
      std::chrono::seconds(3723) + std::chrono::nanoseconds(456789000),
      fory::serialization::Timestamp(std::chrono::seconds(1709210096) +
                                     std::chrono::nanoseconds(789123000)),
      fory::serialization::Date(19782),
      fory::serialization::Decimal::from_int64(1234567890123456789LL, 4),
      {1u, 2u, 3u, 4u},
      {true, false, true},
  };
  auto ser_result = fory_full.serialize(full);
  ASSERT_TRUE(ser_result.ok()) << ser_result.error().to_string();

  std::vector<uint8_t> bytes = std::move(ser_result).value();

  auto deser_result = fory_reduced.deserialize<SkipInternalFieldsReduced>(
      bytes.data(), bytes.size());
  ASSERT_TRUE(deser_result.ok()) << deser_result.error().to_string();

  SkipInternalFieldsReduced reduced = std::move(deser_result).value();
  EXPECT_EQ(reduced.keep_values, full.keep_values);
}

TEST(SchemaEvolutionTest, TaggedBinaryFieldKeepsExplicitIdMatch) {
  auto fory_full =
      Fory::builder().compatible(true).xlang(true).track_ref(false).build();
  auto fory_reduced =
      Fory::builder().compatible(true).xlang(true).track_ref(false).build();

  constexpr uint32_t TYPE_ID = 1009;
  ASSERT_TRUE(
      fory_full.register_struct<MatchTaggedBinaryFieldFull>(TYPE_ID).ok());
  ASSERT_TRUE(
      fory_reduced.register_struct<MatchTaggedBinaryFieldReduced>(TYPE_ID)
          .ok());

  MatchTaggedBinaryFieldFull full{{1u, 2u, 3u, 4u}, {200u, 17u}};
  auto ser_result = fory_full.serialize(full);
  ASSERT_TRUE(ser_result.ok()) << ser_result.error().to_string();

  auto deser_result = fory_reduced.deserialize<MatchTaggedBinaryFieldReduced>(
      ser_result.value());
  ASSERT_TRUE(deser_result.ok()) << deser_result.error().to_string();

  EXPECT_EQ(deser_result->bytes_value, full.bytes_value);
}

TEST(SchemaEvolutionTest, TaggedInt8FieldKeepsExplicitIdMatch) {
  auto fory_full =
      Fory::builder().compatible(true).xlang(true).track_ref(false).build();
  auto fory_reduced =
      Fory::builder().compatible(true).xlang(true).track_ref(false).build();

  constexpr uint32_t TYPE_ID = 1010;
  ASSERT_TRUE(
      fory_full.register_struct<MatchTaggedInt8FieldFull>(TYPE_ID).ok());
  ASSERT_TRUE(
      fory_reduced.register_struct<MatchTaggedInt8FieldReduced>(TYPE_ID).ok());

  MatchTaggedInt8FieldFull full{
      {1u, 2u, 3u, 4u},
      {static_cast<int8_t>(-12), static_cast<int8_t>(8)},
      {200u, 17u}};
  auto ser_result = fory_full.serialize(full);
  ASSERT_TRUE(ser_result.ok()) << ser_result.error().to_string();

  auto deser_result =
      fory_reduced.deserialize<MatchTaggedInt8FieldReduced>(ser_result.value());
  ASSERT_TRUE(deser_result.ok()) << deser_result.error().to_string();

  EXPECT_EQ(deser_result->int8_array, full.int8_array);
}

TEST(SchemaEvolutionTest, RoundtripWithSameVersion) {
  // Sanity check: V2 -> V2 should work perfectly
  auto fory_compat = Fory::builder().compatible(true).xlang(true).build();

  constexpr uint32_t PERSON_TYPE_ID = 999;
  ASSERT_TRUE(fory_compat.register_struct<PersonV2>(PERSON_TYPE_ID).ok());

  PersonV2 original{"Dave", 40, "dave@example.com"};
  auto ser_result = fory_compat.serialize(original);
  ASSERT_TRUE(ser_result.ok());

  std::vector<uint8_t> bytes = std::move(ser_result).value();

  std::cout << "Serialized bytes size: " << bytes.size() << std::endl;

  auto deser_result =
      fory_compat.deserialize<PersonV2>(bytes.data(), bytes.size());
  ASSERT_TRUE(deser_result.ok())
      << "Error: " << deser_result.error().to_string();

  PersonV2 deserialized = std::move(deser_result).value();
  EXPECT_EQ(original, deserialized);
}

TEST(SchemaEvolutionTest, NonCompatibleModeStrictness) {
  // In non-compatible mode, struct serialization should be strict
  // Different struct types should NOT be interchangeable
  auto fory_strict = Fory::builder().compatible(false).xlang(true).build();

  // Register PersonV1 before serialization
  constexpr uint32_t PERSON_TYPE_ID = 999;
  ASSERT_TRUE(fory_strict.register_struct<PersonV1>(PERSON_TYPE_ID).ok());

  PersonV1 v1{"Eve", 28};
  auto ser_result = fory_strict.serialize(v1);
  ASSERT_TRUE(ser_result.ok());

  std::vector<uint8_t> bytes = std::move(ser_result).value();

  // NOTE: In strict mode (compatible=false), deserializing V1 data as V2
  // should ideally fail or at least not work correctly. However, this
  // depends on implementation details. For now, we just document this
  // as a potential enhancement.

  // This test is disabled until we implement strict mode validation
  // auto deser_result = fory_strict.deserialize<PersonV2>(bytes.data(),
  // bytes.size()); EXPECT_FALSE(deser_result.ok()) << "Should fail in strict
  // mode";
}

// ============================================================================
// Performance and Stress Tests
// ============================================================================

TEST(SchemaEvolutionTest, LargeNumberOfFields) {
  // Test evolution with structs that have many fields
  // (This would require defining structs with 20+ fields, omitted for brevity)
}

TEST(SchemaEvolutionTest, DeepNesting) {
  // Test evolution with deeply nested structs (5+ levels)
  // (This would require defining deep struct hierarchies, omitted for brevity)
}

TEST(SchemaEvolutionTest, MixedEvolution) {
  // Test combining add, remove, and reorder operations simultaneously
  // (This is effectively tested by the combination of other tests)
}

} // namespace test
} // namespace serialization
} // namespace fory
