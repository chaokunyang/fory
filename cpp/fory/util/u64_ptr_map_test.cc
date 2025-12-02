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

#include "fory/util/u64_ptr_map.h"
#include <gtest/gtest.h>
#include <random>
#include <unordered_set>

namespace fory {
namespace util {

class U64PtrMapTest : public ::testing::Test {
protected:
  int dummy_values_[100];
};

TEST_F(U64PtrMapTest, BasicInsertAndFind) {
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

TEST_F(U64PtrMapTest, FindNonExistent) {
  U64Map<int *> map(16);

  map[1] = &dummy_values_[0];

  EXPECT_EQ(map.find(2), nullptr);
  EXPECT_EQ(map.find(100), nullptr);
  EXPECT_EQ(map.find(0), nullptr); // Key 0 is reserved
}

TEST_F(U64PtrMapTest, UpdateExistingKey) {
  U64Map<int *> map(16);

  map[1] = &dummy_values_[0];
  EXPECT_EQ(map.size(), 1);

  map[1] = &dummy_values_[1];
  EXPECT_EQ(map.size(), 1); // Size should not increase

  auto *entry = map.find(1);
  ASSERT_NE(entry, nullptr);
  EXPECT_EQ(entry->value, &dummy_values_[1]);
}

TEST_F(U64PtrMapTest, Contains) {
  U64Map<int *> map(16);

  map[42] = &dummy_values_[0];

  EXPECT_TRUE(map.contains(42));
  EXPECT_FALSE(map.contains(43));
  EXPECT_FALSE(map.contains(0));
}

TEST_F(U64PtrMapTest, EmptyMap) {
  U64Map<int *> map(16);

  EXPECT_TRUE(map.empty());
  EXPECT_EQ(map.size(), 0);
  EXPECT_EQ(map.find(1), nullptr);

  map[1] = &dummy_values_[0];
  EXPECT_FALSE(map.empty());
}

TEST_F(U64PtrMapTest, PowerOf2Capacity) {
  U64Map<int *> map1(10);
  EXPECT_EQ(map1.capacity(), 16); // Rounded up to 16

  U64Map<int *> map2(17);
  EXPECT_EQ(map2.capacity(), 32); // Rounded up to 32

  U64Map<int *> map3(64);
  EXPECT_EQ(map3.capacity(), 64); // Already power of 2
}

TEST_F(U64PtrMapTest, ManyInsertions) {
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

TEST_F(U64PtrMapTest, CollisionHandling) {
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

TEST_F(U64PtrMapTest, LargeKeys) {
  U64Map<int *> map(16);

  uint64_t key1 = 0xFFFFFFFFFFFFFFFFULL;
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

TEST_F(U64PtrMapTest, CopyConstructor) {
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

TEST_F(U64PtrMapTest, MoveConstructor) {
  U64Map<int *> map1(16);
  map1[1] = &dummy_values_[0];
  map1[2] = &dummy_values_[1];

  U64Map<int *> map2(std::move(map1));

  EXPECT_EQ(map2.size(), 2);
  EXPECT_NE(map2.find(1), nullptr);
  EXPECT_NE(map2.find(2), nullptr);
}

TEST_F(U64PtrMapTest, Iterator) {
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

TEST_F(U64PtrMapTest, RangeBasedFor) {
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

TEST_F(U64PtrMapTest, ConstFind) {
  U64Map<int *> map(16);
  map[1] = &dummy_values_[0];

  const U64Map<int *> &const_map = map;

  const auto *entry = const_map.find(1);
  ASSERT_NE(entry, nullptr);
  EXPECT_EQ(entry->key, 1);
}

// Performance-oriented test: verify lookup works well with type-index-like keys
TEST_F(U64PtrMapTest, TypeIndexLikeKeys) {
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
    EXPECT_EQ(entry->value, reinterpret_cast<void *>(static_cast<uintptr_t>(i + 1)));
  }
}

} // namespace util
} // namespace fory

int main(int argc, char **argv) {
  ::testing::InitGoogleTest(&argc, argv);
  return RUN_ALL_TESTS();
}
