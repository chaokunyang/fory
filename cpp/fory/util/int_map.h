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

#include <cstdint>
#include <cstring>
#include <memory>
#include <type_traits>
#include <utility>

#include "fory/util/logging.h"
#include "fory/util/result.h"

namespace fory {
namespace util {

/// A specialized open-addressed hash map optimized for integer keys (uint32_t
/// or uint64_t). Designed for read-heavy workloads: insert can be slow, but
/// lookup must be ultra-fast.
///
/// Features:
/// - Auto-grow when load factor exceeded
/// - Configurable load factor (lower = faster lookup, more memory)
/// - Cache-friendly linear probing
/// - Power-of-2 sizing for fast modulo via bitmasking
///
/// Constraints:
/// - Key 0 is reserved as empty marker
/// - No deletion support
/// - Not thread-safe
template <typename K, typename V> class IntMap {
  static_assert(std::is_same_v<K, uint32_t> || std::is_same_v<K, uint64_t>,
                "IntMap key type must be uint32_t or uint64_t");

public:
  static constexpr K kEmpty = 0;
  static constexpr float kDefaultLoadFactor = 0.5f; // Low for fast lookup

  struct Entry {
    K key;
    V value;
  };

  class Iterator {
  public:
    Iterator(Entry *entries, size_t capacity, size_t index)
        : entries_(entries), capacity_(capacity), index_(index) {
      while (index_ < capacity_ && entries_[index_].key == kEmpty)
        ++index_;
    }
    std::pair<K, V> operator*() const {
      return {entries_[index_].key, entries_[index_].value};
    }
    const Entry *operator->() const { return &entries_[index_]; }
    Iterator &operator++() {
      ++index_;
      while (index_ < capacity_ && entries_[index_].key == kEmpty)
        ++index_;
      return *this;
    }
    bool operator==(const Iterator &other) const {
      return index_ == other.index_;
    }
    bool operator!=(const Iterator &other) const { return !(*this == other); }

  private:
    Entry *entries_;
    size_t capacity_;
    size_t index_;
  };

  explicit IntMap(size_t initial_capacity = 64,
                  float load_factor = kDefaultLoadFactor)
      : load_factor_(load_factor) {
    capacity_ = next_power_of_2(initial_capacity < 8 ? 8 : initial_capacity);
    mask_ = capacity_ - 1;
    grow_threshold_ = static_cast<size_t>(capacity_ * load_factor_);
    entries_ = std::make_unique<Entry[]>(capacity_);
    std::memset(entries_.get(), 0, capacity_ * sizeof(Entry));
    size_ = 0;
  }

  IntMap(const IntMap &other)
      : capacity_(other.capacity_), mask_(other.mask_), size_(other.size_),
        load_factor_(other.load_factor_),
        grow_threshold_(other.grow_threshold_) {
    entries_ = std::make_unique<Entry[]>(capacity_);
    std::memcpy(entries_.get(), other.entries_.get(),
                capacity_ * sizeof(Entry));
  }

  IntMap(IntMap &&other) noexcept
      : entries_(std::move(other.entries_)), capacity_(other.capacity_),
        mask_(other.mask_), size_(other.size_),
        load_factor_(other.load_factor_),
        grow_threshold_(other.grow_threshold_) {
    other.capacity_ = 0;
    other.mask_ = 0;
    other.size_ = 0;
  }

  IntMap &operator=(const IntMap &other) {
    if (this != &other) {
      capacity_ = other.capacity_;
      mask_ = other.mask_;
      size_ = other.size_;
      load_factor_ = other.load_factor_;
      grow_threshold_ = other.grow_threshold_;
      entries_ = std::make_unique<Entry[]>(capacity_);
      std::memcpy(entries_.get(), other.entries_.get(),
                  capacity_ * sizeof(Entry));
    }
    return *this;
  }

  IntMap &operator=(IntMap &&other) noexcept {
    if (this != &other) {
      entries_ = std::move(other.entries_);
      capacity_ = other.capacity_;
      mask_ = other.mask_;
      size_ = other.size_;
      load_factor_ = other.load_factor_;
      grow_threshold_ = other.grow_threshold_;
      other.capacity_ = 0;
      other.mask_ = 0;
      other.size_ = 0;
    }
    return *this;
  }

  /// Insert or update. May trigger grow.
  V &operator[](K key) {
    if (size_ >= grow_threshold_) {
      grow();
    }
    size_t idx = find_slot_for_insert(key);
    if (entries_[idx].key == kEmpty) {
      entries_[idx].key = key;
      ++size_;
    }
    return entries_[idx].value;
  }

  /// Ultra-fast lookup. Returns pointer to Entry or nullptr.
  FORY_ALWAYS_INLINE Entry *find(K key) {
    if (FORY_PREDICT_FALSE(key == kEmpty))
      return nullptr;
    Entry *entries = entries_.get();
    size_t idx = hash(key) & mask_;
    while (true) {
      K k = entries[idx].key;
      if (k == key)
        return &entries[idx];
      if (k == kEmpty)
        return nullptr;
      idx = (idx + 1) & mask_;
    }
  }

  FORY_ALWAYS_INLINE const Entry *find(K key) const {
    return const_cast<IntMap *>(this)->find(key);
  }

  /// Direct value lookup. Returns pointer to value or nullptr.
  FORY_ALWAYS_INLINE V *get(K key) {
    Entry *e = find(key);
    return e ? &e->value : nullptr;
  }

  FORY_ALWAYS_INLINE const V *get(K key) const {
    const Entry *e = find(key);
    return e ? &e->value : nullptr;
  }

  bool contains(K key) const { return find(key) != nullptr; }
  size_t size() const { return size_; }
  size_t capacity() const { return capacity_; }
  bool empty() const { return size_ == 0; }

  void clear() {
    std::memset(entries_.get(), 0, capacity_ * sizeof(Entry));
    size_ = 0;
  }

  Iterator begin() { return Iterator(entries_.get(), capacity_, 0); }
  Iterator end() { return Iterator(entries_.get(), capacity_, capacity_); }
  Iterator begin() const {
    return Iterator(const_cast<Entry *>(entries_.get()), capacity_, 0);
  }
  Iterator end() const {
    return Iterator(const_cast<Entry *>(entries_.get()), capacity_, capacity_);
  }

private:
  void grow() {
    size_t new_capacity = capacity_ * 2;
    size_t new_mask = new_capacity - 1;
    auto new_entries = std::make_unique<Entry[]>(new_capacity);
    std::memset(new_entries.get(), 0, new_capacity * sizeof(Entry));

    // Rehash all existing entries
    for (size_t i = 0; i < capacity_; ++i) {
      if (entries_[i].key != kEmpty) {
        size_t idx = hash(entries_[i].key) & new_mask;
        while (new_entries[idx].key != kEmpty) {
          idx = (idx + 1) & new_mask;
        }
        new_entries[idx] = entries_[i];
      }
    }

    entries_ = std::move(new_entries);
    capacity_ = new_capacity;
    mask_ = new_mask;
    grow_threshold_ = static_cast<size_t>(capacity_ * load_factor_);
  }

  size_t find_slot_for_insert(K key) {
    size_t idx = hash(key) & mask_;
    while (entries_[idx].key != kEmpty && entries_[idx].key != key) {
      idx = (idx + 1) & mask_;
    }
    return idx;
  }

  FORY_ALWAYS_INLINE static size_t hash(K key) {
    uint64_t k = static_cast<uint64_t>(key);
    k ^= k >> 33;
    k *= 0xff51afd7ed558ccdULL;
    k ^= k >> 33;
    k *= 0xc4ceb9fe1a85ec53ULL;
    k ^= k >> 33;
    return static_cast<size_t>(k);
  }

  static size_t next_power_of_2(size_t n) {
    if (n == 0)
      return 1;
    --n;
    n |= n >> 1;
    n |= n >> 2;
    n |= n >> 4;
    n |= n >> 8;
    n |= n >> 16;
    n |= n >> 32;
    return n + 1;
  }

  std::unique_ptr<Entry[]> entries_;
  size_t capacity_;
  size_t mask_;
  size_t size_;
  float load_factor_;
  size_t grow_threshold_;
};

/// Type alias for uint64_t keys (backward compatible)
template <typename V> using U64Map = IntMap<uint64_t, V>;

/// Type alias for uint32_t keys
template <typename V> using U32Map = IntMap<uint32_t, V>;

/// Type alias for the common case of pointer values
using U64PtrMap = U64Map<void *>;
using U32PtrMap = U32Map<void *>;

} // namespace util
} // namespace fory
