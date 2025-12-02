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
#include <utility>

namespace fory {
namespace util {

/// A specialized open-addressed hash map optimized for uint64_t keys and
/// pointer values.
///
/// Design goals:
/// - Minimal overhead for small maps (typical use: 10-100 entries)
/// - Cache-friendly: key and value stored together in contiguous array
/// - Fast lookup using linear probing
/// - No dynamic resizing (fixed capacity, set at construction)
///
/// Constraints:
/// - Key 0 is reserved as empty marker (cannot store key=0)
/// - No deletion support (not needed for type registry use case)
/// - Not thread-safe for concurrent modification
///
/// Performance characteristics:
/// - O(1) average lookup with good hash distribution
/// - Linear probing provides excellent cache locality
/// - Power-of-2 sizing enables fast modulo via bitmasking
template <typename V> class U64Map {
public:
  static constexpr uint64_t kEmpty = 0;

  struct Entry {
    uint64_t key;
    V value;
  };

  /// Iterator for range-based for loops and STL compatibility
  class Iterator {
  public:
    using iterator_category = std::forward_iterator_tag;
    using value_type = std::pair<const uint64_t, V>;
    using difference_type = std::ptrdiff_t;
    using pointer = value_type *;
    using reference = value_type &;

    Iterator(Entry *entries, size_t capacity, size_t index)
        : entries_(entries), capacity_(capacity), index_(index) {
      advance_to_valid();
    }

    std::pair<uint64_t, V> operator*() const {
      return {entries_[index_].key, entries_[index_].value};
    }

    const Entry *operator->() const { return &entries_[index_]; }

    Iterator &operator++() {
      ++index_;
      advance_to_valid();
      return *this;
    }

    bool operator==(const Iterator &other) const {
      return index_ == other.index_;
    }
    bool operator!=(const Iterator &other) const { return !(*this == other); }

  private:
    void advance_to_valid() {
      while (index_ < capacity_ && entries_[index_].key == kEmpty) {
        ++index_;
      }
    }

    Entry *entries_;
    size_t capacity_;
    size_t index_;
  };

  /// Construct map with given capacity (will be rounded up to power of 2)
  /// @param initial_capacity Minimum number of entries to support
  explicit U64Map(size_t initial_capacity = 64) {
    capacity_ = next_power_of_2(initial_capacity < 8 ? 8 : initial_capacity);
    mask_ = capacity_ - 1;
    entries_ = std::make_unique<Entry[]>(capacity_);
    std::memset(entries_.get(), 0, capacity_ * sizeof(Entry));
    size_ = 0;
  }

  /// Copy constructor
  U64Map(const U64Map &other)
      : capacity_(other.capacity_), mask_(other.mask_), size_(other.size_) {
    entries_ = std::make_unique<Entry[]>(capacity_);
    std::memcpy(entries_.get(), other.entries_.get(),
                capacity_ * sizeof(Entry));
  }

  /// Move constructor
  U64Map(U64Map &&other) noexcept
      : entries_(std::move(other.entries_)), capacity_(other.capacity_),
        mask_(other.mask_), size_(other.size_) {
    other.capacity_ = 0;
    other.mask_ = 0;
    other.size_ = 0;
  }

  /// Copy assignment
  U64Map &operator=(const U64Map &other) {
    if (this != &other) {
      capacity_ = other.capacity_;
      mask_ = other.mask_;
      size_ = other.size_;
      entries_ = std::make_unique<Entry[]>(capacity_);
      std::memcpy(entries_.get(), other.entries_.get(),
                  capacity_ * sizeof(Entry));
    }
    return *this;
  }

  /// Move assignment
  U64Map &operator=(U64Map &&other) noexcept {
    if (this != &other) {
      entries_ = std::move(other.entries_);
      capacity_ = other.capacity_;
      mask_ = other.mask_;
      size_ = other.size_;
      other.capacity_ = 0;
      other.mask_ = 0;
      other.size_ = 0;
    }
    return *this;
  }

  /// Insert or update a key-value pair.
  /// @param key The key (must not be 0)
  /// @param value The value to associate with the key
  /// @return Reference to the stored value
  V &operator[](uint64_t key) {
    size_t idx = find_slot(key);
    if (entries_[idx].key == kEmpty) {
      entries_[idx].key = key;
      ++size_;
    }
    return entries_[idx].value;
  }

  /// Find an entry by key.
  /// @param key The key to search for
  /// @return Pointer to the Entry if found, nullptr otherwise
  Entry *find(uint64_t key) {
    if (key == kEmpty)
      return nullptr;

    size_t idx = hash(key) & mask_;
    size_t start = idx;

    do {
      if (entries_[idx].key == key) {
        return &entries_[idx];
      }
      if (entries_[idx].key == kEmpty) {
        return nullptr;
      }
      idx = (idx + 1) & mask_;
    } while (idx != start);

    return nullptr;
  }

  /// Find an entry by key (const version).
  const Entry *find(uint64_t key) const {
    return const_cast<U64Map *>(this)->find(key);
  }

  /// Check if a key exists in the map.
  bool contains(uint64_t key) const { return find(key) != nullptr; }

  /// Get the number of entries in the map.
  size_t size() const { return size_; }

  /// Get the capacity of the map.
  size_t capacity() const { return capacity_; }

  /// Check if the map is empty.
  bool empty() const { return size_ == 0; }

  /// Clear all entries from the map.
  void clear() {
    std::memset(entries_.get(), 0, capacity_ * sizeof(Entry));
    size_ = 0;
  }

  /// Iterator support
  Iterator begin() { return Iterator(entries_.get(), capacity_, 0); }
  Iterator end() { return Iterator(entries_.get(), capacity_, capacity_); }

  Iterator begin() const {
    return Iterator(const_cast<Entry *>(entries_.get()), capacity_, 0);
  }
  Iterator end() const {
    return Iterator(const_cast<Entry *>(entries_.get()), capacity_, capacity_);
  }

private:
  /// Find the slot for a key (for insertion).
  /// Returns the index where the key is found or should be inserted.
  size_t find_slot(uint64_t key) {
    size_t idx = hash(key) & mask_;

    while (entries_[idx].key != kEmpty && entries_[idx].key != key) {
      idx = (idx + 1) & mask_;
    }

    return idx;
  }

  /// Hash function optimized for uint64_t keys.
  /// Uses splitmix64 mixing - excellent distribution for sequential or
  /// similar keys.
  static size_t hash(uint64_t key) {
    key ^= key >> 33;
    key *= 0xff51afd7ed558ccdULL;
    key ^= key >> 33;
    key *= 0xc4ceb9fe1a85ec53ULL;
    key ^= key >> 33;
    return static_cast<size_t>(key);
  }

  /// Round up to the next power of 2.
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
};

/// Type alias for the common case of pointer values
using U64PtrMap = U64Map<void *>;

} // namespace util
} // namespace fory
