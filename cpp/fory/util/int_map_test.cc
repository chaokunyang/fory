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

#include "fory/util/int_map.h"
#include <gtest/gtest.h>
#include <random>
#include <unordered_set>

namespace fory {
namespace util {

class IntMapTest : public ::testing::Test {
protected:
  int dummy_values_[100];
};

// ============================================================================
// Tests for U64Map (uint64_t keys) - backward compatible
// ============================================================================

TEST_F(IntMapTest, U64Map_BasicInsertAndFind) {
  U64Map<int *> map(16);

  map[1] = &dummy_values_[0];
  map[2] = &dummy_values_[1];
  map[3] = &dummy_values_[2];

  EXPECT_EQ(map.size(), 3);

  auto *entry1 = map.find(1);
  ASSERT_NE(entry1, nullptr);
  EXPECT_EQ(entry1->key, 1);
  EXPECT_EQ(entry1->value, &dummy_values_[0]);

  auto *entry2 = map.find(2);
  ASSERT_NE(entry2, nullptr);
  EXPECT_EQ(entry2->value, &dummy_values_[1]);

  auto *entry3 = map.find(3);
  ASSERT_NE(entry3, nullptr);
  EXPECT_EQ(entry3->value, &dummy_values_[2]);
}

TEST_F(IntMapTest, U64Map_FindNonExistent) {
  U64Map<int *> map(16);

  map[1] = &dummy_values_[0];

  EXPECT_EQ(map.find(2), nullptr);
  EXPECT_EQ(map.find(100), nullptr);
  EXPECT_EQ(map.find(UINT64_MAX), nullptr); // Max value is reserved as empty
}

TEST_F(IntMapTest, U64Map_ZeroKey) {
  U64Map<int *> map(16);

  map[0] = &dummy_values_[0]; // 0 is now a valid key
  EXPECT_EQ(map.size(), 1);

  auto *entry = map.find(0);
  ASSERT_NE(entry, nullptr);
  EXPECT_EQ(entry->value, &dummy_values_[0]);
}

TEST_F(IntMapTest, U64Map_UpdateExistingKey) {
  U64Map<int *> map(16);

  map[1] = &dummy_values_[0];
  EXPECT_EQ(map.size(), 1);

  map[1] = &dummy_values_[1];
  EXPECT_EQ(map.size(), 1); // Size should not increase

  auto *entry = map.find(1);
  ASSERT_NE(entry, nullptr);
  EXPECT_EQ(entry->value, &dummy_values_[1]);
}

TEST_F(IntMapTest, U64Map_Contains) {
  U64Map<int *> map(16);

  map[42] = &dummy_values_[0];

  EXPECT_TRUE(map.contains(42));
  EXPECT_FALSE(map.contains(43));
  EXPECT_FALSE(map.contains(UINT64_MAX)); // Max is reserved
}

TEST_F(IntMapTest, U64Map_EmptyMap) {
  U64Map<int *> map(16);

  EXPECT_TRUE(map.empty());
  EXPECT_EQ(map.size(), 0);
  EXPECT_EQ(map.find(1), nullptr);

  map[1] = &dummy_values_[0];
  EXPECT_FALSE(map.empty());
}

TEST_F(IntMapTest, U64Map_PowerOf2Capacity) {
  U64Map<int *> map1(10);
  EXPECT_EQ(map1.capacity(), 16); // Rounded up to 16

  U64Map<int *> map2(17);
  EXPECT_EQ(map2.capacity(), 32); // Rounded up to 32

  U64Map<int *> map3(64);
  EXPECT_EQ(map3.capacity(), 64); // Already power of 2
}

TEST_F(IntMapTest, U64Map_ManyInsertions) {
  U64Map<int *> map(256);

  // Insert many entries
  for (int i = 1; i <= 100; ++i) {
    map[static_cast<uint64_t>(i)] = &dummy_values_[i % 100];
  }

  EXPECT_EQ(map.size(), 100);

  // Verify all entries
  for (int i = 1; i <= 100; ++i) {
    auto *entry = map.find(static_cast<uint64_t>(i));
    ASSERT_NE(entry, nullptr) << "Key " << i << " not found";
    EXPECT_EQ(entry->value, &dummy_values_[i % 100]);
  }
}

TEST_F(IntMapTest, U64Map_CollisionHandling) {
  // Use a small capacity to force collisions
  U64Map<int *> map(8);

  // Insert several entries that might collide
  for (int i = 1; i <= 5; ++i) {
    map[static_cast<uint64_t>(i)] = &dummy_values_[i];
  }

  EXPECT_EQ(map.size(), 5);

  // All entries should still be findable
  for (int i = 1; i <= 5; ++i) {
    auto *entry = map.find(static_cast<uint64_t>(i));
    ASSERT_NE(entry, nullptr);
    EXPECT_EQ(entry->value, &dummy_values_[i]);
  }
}

TEST_F(IntMapTest, U64Map_LargeKeys) {
  U64Map<int *> map(16);

  uint64_t key1 = 0xFFFFFFFFFFFFFFFEULL; // Max-1 (max is reserved)
  uint64_t key2 = 0x123456789ABCDEF0ULL;
  uint64_t key3 = 0x1ULL;

  map[key1] = &dummy_values_[0];
  map[key2] = &dummy_values_[1];
  map[key3] = &dummy_values_[2];

  EXPECT_EQ(map.size(), 3);

  EXPECT_NE(map.find(key1), nullptr);
  EXPECT_NE(map.find(key2), nullptr);
  EXPECT_NE(map.find(key3), nullptr);
}

TEST_F(IntMapTest, U64Map_CopyConstructor) {
  U64Map<int *> map1(16);
  map1[1] = &dummy_values_[0];
  map1[2] = &dummy_values_[1];

  U64Map<int *> map2(map1);

  EXPECT_EQ(map2.size(), 2);
  EXPECT_NE(map2.find(1), nullptr);
  EXPECT_NE(map2.find(2), nullptr);

  // Modify original - copy should be independent
  map1[3] = &dummy_values_[2];
  EXPECT_EQ(map1.size(), 3);
  EXPECT_EQ(map2.size(), 2);
  EXPECT_EQ(map2.find(3), nullptr);
}

TEST_F(IntMapTest, U64Map_MoveConstructor) {
  U64Map<int *> map1(16);
  map1[1] = &dummy_values_[0];
  map1[2] = &dummy_values_[1];

  U64Map<int *> map2(std::move(map1));

  EXPECT_EQ(map2.size(), 2);
  EXPECT_NE(map2.find(1), nullptr);
  EXPECT_NE(map2.find(2), nullptr);
}

TEST_F(IntMapTest, U64Map_Iterator) {
  U64Map<int *> map(16);
  map[1] = &dummy_values_[0];
  map[2] = &dummy_values_[1];
  map[3] = &dummy_values_[2];

  std::unordered_set<uint64_t> found_keys;
  for (auto it = map.begin(); it != map.end(); ++it) {
    found_keys.insert(it->key);
  }

  EXPECT_EQ(found_keys.size(), 3);
  EXPECT_TRUE(found_keys.count(1));
  EXPECT_TRUE(found_keys.count(2));
  EXPECT_TRUE(found_keys.count(3));
}

TEST_F(IntMapTest, U64Map_RangeBasedFor) {
  U64Map<int *> map(16);
  map[10] = &dummy_values_[0];
  map[20] = &dummy_values_[1];

  int count = 0;
  for (const auto &entry : map) {
    EXPECT_TRUE(entry.first == 10 || entry.first == 20);
    ++count;
  }
  EXPECT_EQ(count, 2);
}

TEST_F(IntMapTest, U64Map_ConstFind) {
  U64Map<int *> map(16);
  map[1] = &dummy_values_[0];

  const U64Map<int *> &const_map = map;

  const auto *entry = const_map.find(1);
  ASSERT_NE(entry, nullptr);
  EXPECT_EQ(entry->key, 1);
}

// Performance-oriented test: verify lookup works well with type-index-like keys
TEST_F(IntMapTest, U64Map_TypeIndexLikeKeys) {
  U64Map<void *> map(128);

  // Simulate type index values (typically hash-based, well-distributed)
  std::vector<uint64_t> keys;
  for (int i = 0; i < 50; ++i) {
    // Simulate fnv1a-like hashing
    uint64_t key = 0xcbf29ce484222325ULL;
    key ^= static_cast<uint64_t>(i);
    key *= 0x100000001b3ULL;
    keys.push_back(key);
    map[key] = reinterpret_cast<void *>(static_cast<uintptr_t>(i + 1));
  }

  EXPECT_EQ(map.size(), 50);

  // Verify all lookups work
  for (size_t i = 0; i < keys.size(); ++i) {
    auto *entry = map.find(keys[i]);
    ASSERT_NE(entry, nullptr) << "Key at index " << i << " not found";
    EXPECT_EQ(entry->value,
              reinterpret_cast<void *>(static_cast<uintptr_t>(i + 1)));
  }
}

// ============================================================================
// Tests for U32Map (uint32_t keys)
// ============================================================================

TEST_F(IntMapTest, U32Map_BasicInsertAndFind) {
  U32Map<int *> map(16);

  map[1] = &dummy_values_[0];
  map[2] = &dummy_values_[1];
  map[3] = &dummy_values_[2];

  EXPECT_EQ(map.size(), 3);

  auto *entry1 = map.find(1);
  ASSERT_NE(entry1, nullptr);
  EXPECT_EQ(entry1->key, 1u);
  EXPECT_EQ(entry1->value, &dummy_values_[0]);

  auto *entry2 = map.find(2);
  ASSERT_NE(entry2, nullptr);
  EXPECT_EQ(entry2->value, &dummy_values_[1]);

  auto *entry3 = map.find(3);
  ASSERT_NE(entry3, nullptr);
  EXPECT_EQ(entry3->value, &dummy_values_[2]);
}

TEST_F(IntMapTest, U32Map_FindNonExistent) {
  U32Map<int *> map(16);

  map[1] = &dummy_values_[0];

  EXPECT_EQ(map.find(2), nullptr);
  EXPECT_EQ(map.find(100), nullptr);
  EXPECT_EQ(map.find(UINT32_MAX), nullptr); // Max value is reserved as empty
}

TEST_F(IntMapTest, U32Map_ZeroKey) {
  U32Map<int *> map(16);

  map[0] = &dummy_values_[0]; // 0 is now a valid key
  EXPECT_EQ(map.size(), 1);

  auto *entry = map.find(0);
  ASSERT_NE(entry, nullptr);
  EXPECT_EQ(entry->value, &dummy_values_[0]);
}

TEST_F(IntMapTest, U32Map_UpdateExistingKey) {
  U32Map<int *> map(16);

  map[1] = &dummy_values_[0];
  EXPECT_EQ(map.size(), 1);

  map[1] = &dummy_values_[1];
  EXPECT_EQ(map.size(), 1); // Size should not increase

  auto *entry = map.find(1);
  ASSERT_NE(entry, nullptr);
  EXPECT_EQ(entry->value, &dummy_values_[1]);
}

TEST_F(IntMapTest, U32Map_Contains) {
  U32Map<int *> map(16);

  map[42] = &dummy_values_[0];

  EXPECT_TRUE(map.contains(42));
  EXPECT_FALSE(map.contains(43));
  EXPECT_FALSE(map.contains(UINT32_MAX)); // Max is reserved
}

TEST_F(IntMapTest, U32Map_EmptyMap) {
  U32Map<int *> map(16);

  EXPECT_TRUE(map.empty());
  EXPECT_EQ(map.size(), 0);
  EXPECT_EQ(map.find(1), nullptr);

  map[1] = &dummy_values_[0];
  EXPECT_FALSE(map.empty());
}

TEST_F(IntMapTest, U32Map_ManyInsertions) {
  U32Map<int *> map(256);

  // Insert many entries
  for (uint32_t i = 1; i <= 100; ++i) {
    map[i] = &dummy_values_[i % 100];
  }

  EXPECT_EQ(map.size(), 100);

  // Verify all entries
  for (uint32_t i = 1; i <= 100; ++i) {
    auto *entry = map.find(i);
    ASSERT_NE(entry, nullptr) << "Key " << i << " not found";
    EXPECT_EQ(entry->value, &dummy_values_[i % 100]);
  }
}

TEST_F(IntMapTest, U32Map_CollisionHandling) {
  // Use a small capacity to force collisions
  U32Map<int *> map(8);

  // Insert several entries that might collide
  for (uint32_t i = 1; i <= 5; ++i) {
    map[i] = &dummy_values_[i];
  }

  EXPECT_EQ(map.size(), 5);

  // All entries should still be findable
  for (uint32_t i = 1; i <= 5; ++i) {
    auto *entry = map.find(i);
    ASSERT_NE(entry, nullptr);
    EXPECT_EQ(entry->value, &dummy_values_[i]);
  }
}

TEST_F(IntMapTest, U32Map_LargeKeys) {
  U32Map<int *> map(16);

  uint32_t key1 = 0xFFFFFFFEu; // Max-1 (max is reserved)
  uint32_t key2 = 0x12345678u;
  uint32_t key3 = 0x1u;

  map[key1] = &dummy_values_[0];
  map[key2] = &dummy_values_[1];
  map[key3] = &dummy_values_[2];

  EXPECT_EQ(map.size(), 3);

  EXPECT_NE(map.find(key1), nullptr);
  EXPECT_NE(map.find(key2), nullptr);
  EXPECT_NE(map.find(key3), nullptr);
}

TEST_F(IntMapTest, U32Map_CopyConstructor) {
  U32Map<int *> map1(16);
  map1[1] = &dummy_values_[0];
  map1[2] = &dummy_values_[1];

  U32Map<int *> map2(map1);

  EXPECT_EQ(map2.size(), 2);
  EXPECT_NE(map2.find(1), nullptr);
  EXPECT_NE(map2.find(2), nullptr);

  // Modify original - copy should be independent
  map1[3] = &dummy_values_[2];
  EXPECT_EQ(map1.size(), 3);
  EXPECT_EQ(map2.size(), 2);
  EXPECT_EQ(map2.find(3), nullptr);
}

TEST_F(IntMapTest, U32Map_MoveConstructor) {
  U32Map<int *> map1(16);
  map1[1] = &dummy_values_[0];
  map1[2] = &dummy_values_[1];

  U32Map<int *> map2(std::move(map1));

  EXPECT_EQ(map2.size(), 2);
  EXPECT_NE(map2.find(1), nullptr);
  EXPECT_NE(map2.find(2), nullptr);
}

TEST_F(IntMapTest, U32Map_Iterator) {
  U32Map<int *> map(16);
  map[1] = &dummy_values_[0];
  map[2] = &dummy_values_[1];
  map[3] = &dummy_values_[2];

  std::unordered_set<uint32_t> found_keys;
  for (auto it = map.begin(); it != map.end(); ++it) {
    found_keys.insert(it->key);
  }

  EXPECT_EQ(found_keys.size(), 3);
  EXPECT_TRUE(found_keys.count(1));
  EXPECT_TRUE(found_keys.count(2));
  EXPECT_TRUE(found_keys.count(3));
}

TEST_F(IntMapTest, U32Map_RangeBasedFor) {
  U32Map<int *> map(16);
  map[10] = &dummy_values_[0];
  map[20] = &dummy_values_[1];

  int count = 0;
  for (const auto &entry : map) {
    EXPECT_TRUE(entry.first == 10 || entry.first == 20);
    ++count;
  }
  EXPECT_EQ(count, 2);
}

TEST_F(IntMapTest, U32Map_ConstFind) {
  U32Map<int *> map(16);
  map[1] = &dummy_values_[0];

  const U32Map<int *> &const_map = map;

  const auto *entry = const_map.find(1);
  ASSERT_NE(entry, nullptr);
  EXPECT_EQ(entry->key, 1u);
}

// Test for type_id lookups (simulates type_info_by_id_ usage)
TEST_F(IntMapTest, U32Map_TypeIdLookups) {
  U32Map<void *> map(256);

  // Simulate type ID values used in TypeResolver
  std::vector<uint32_t> type_ids;
  for (uint32_t i = 1; i <= 50; ++i) {
    // Simulate encoded type_id: (user_id << 8) + TypeId
    uint32_t type_id = (i << 8) + 100; // 100 = some TypeId enum value
    type_ids.push_back(type_id);
    map[type_id] = reinterpret_cast<void *>(static_cast<uintptr_t>(i));
  }

  EXPECT_EQ(map.size(), 50);

  // Verify all lookups work
  for (size_t i = 0; i < type_ids.size(); ++i) {
    auto *entry = map.find(type_ids[i]);
    ASSERT_NE(entry, nullptr) << "Type ID at index " << i << " not found";
    EXPECT_EQ(entry->value,
              reinterpret_cast<void *>(static_cast<uintptr_t>(i + 1)));
  }
}

// ============================================================================
// Tests for auto-grow functionality
// ============================================================================

TEST_F(IntMapTest, AutoGrow_U64Map) {
  // Start with small capacity, default load factor 0.5
  U64Map<int *> map(8);
  EXPECT_EQ(map.capacity(), 8);

  // Insert more than capacity * load_factor (8 * 0.5 = 4)
  for (int i = 1; i <= 10; ++i) {
    map[static_cast<uint64_t>(i)] = &dummy_values_[i % 100];
  }

  EXPECT_EQ(map.size(), 10);
  EXPECT_GT(map.capacity(), 8); // Should have grown

  // Verify all entries still accessible after grow
  for (int i = 1; i <= 10; ++i) {
    auto *entry = map.find(static_cast<uint64_t>(i));
    ASSERT_NE(entry, nullptr) << "Key " << i << " not found after grow";
    EXPECT_EQ(entry->value, &dummy_values_[i % 100]);
  }
}

TEST_F(IntMapTest, AutoGrow_U32Map) {
  U32Map<int *> map(8);
  EXPECT_EQ(map.capacity(), 8);

  for (uint32_t i = 1; i <= 10; ++i) {
    map[i] = &dummy_values_[i % 100];
  }

  EXPECT_EQ(map.size(), 10);
  EXPECT_GT(map.capacity(), 8);

  for (uint32_t i = 1; i <= 10; ++i) {
    auto *entry = map.find(i);
    ASSERT_NE(entry, nullptr);
    EXPECT_EQ(entry->value, &dummy_values_[i % 100]);
  }
}

TEST_F(IntMapTest, CustomLoadFactor) {
  // Use higher load factor (0.75) - more memory efficient but slower lookup
  U64Map<int *> map(16, 0.75f);
  EXPECT_EQ(map.capacity(), 16);

  // Can insert up to 16 * 0.75 = 12 before grow
  for (int i = 1; i <= 12; ++i) {
    map[static_cast<uint64_t>(i)] = &dummy_values_[i % 100];
  }
  EXPECT_EQ(map.capacity(), 16); // Should not have grown yet

  // One more should trigger grow
  map[13] = &dummy_values_[13];
  EXPECT_GT(map.capacity(), 16);
}

TEST_F(IntMapTest, GetMethod) {
  U64Map<int *> map(16);
  map[1] = &dummy_values_[0];
  map[2] = &dummy_values_[1];

  // Test get() returns pointer to value
  int **val1 = map.get(1);
  ASSERT_NE(val1, nullptr);
  EXPECT_EQ(*val1, &dummy_values_[0]);

  int **val2 = map.get(2);
  ASSERT_NE(val2, nullptr);
  EXPECT_EQ(*val2, &dummy_values_[1]);

  // Non-existent key returns nullptr
  EXPECT_EQ(map.get(999), nullptr);
}

TEST_F(IntMapTest, ManyGrows) {
  U64Map<int> map(8);

  // Insert many entries to trigger multiple grows
  for (int i = 1; i <= 1000; ++i) {
    map[static_cast<uint64_t>(i)] = i * 10;
  }

  EXPECT_EQ(map.size(), 1000);

  // Verify all entries
  for (int i = 1; i <= 1000; ++i) {
    auto *v = map.get(static_cast<uint64_t>(i));
    ASSERT_NE(v, nullptr) << "Key " << i << " not found";
    EXPECT_EQ(*v, i * 10);
  }
}

} // namespace util
} // namespace fory

int main(int argc, char **argv) {
  ::testing::InitGoogleTest(&argc, argv);
  return RUN_ALL_TESTS();
}
