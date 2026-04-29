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

// This file is a compact Fory-owned SwissTable-style flat hash map derived
// from Abseil's flat_hash_map/raw_hash_set design. It intentionally exposes
// only the API surface used by Fory's C++ and Cython runtimes.

#pragma once

#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <functional>
#include <initializer_list>
#include <iterator>
#include <limits>
#include <memory>
#include <optional>
#include <stdexcept>
#include <string>
#include <type_traits>
#include <typeindex>
#include <utility>
#include <vector>

namespace fory {
namespace detail {
namespace flat_hash_map_internal {

constexpr uint8_t kEmpty = 0x80;
constexpr uint8_t kDeleted = 0xFE;
constexpr float kMaxLoadFactor = 0.875f;

inline size_t mix_hash(size_t hash) {
  uint64_t x = static_cast<uint64_t>(hash);
  x ^= x >> 33;
  x *= 0xff51afd7ed558ccdULL;
  x ^= x >> 33;
  x *= 0xc4ceb9fe1a85ec53ULL;
  x ^= x >> 33;
  if constexpr (sizeof(size_t) < sizeof(uint64_t)) {
    return static_cast<size_t>(x ^ (x >> 32));
  } else {
    return static_cast<size_t>(x);
  }
}

inline size_t next_capacity(size_t requested) {
  size_t capacity = 8;
  while (capacity < requested) {
    capacity <<= 1;
  }
  return capacity;
}

template <typename T, typename = void> struct default_hash {
  size_t operator()(const T &value) const {
    return mix_hash(std::hash<T>{}(value));
  }
};

template <typename A, typename B> struct default_hash<std::pair<A, B>> {
  size_t operator()(const std::pair<A, B> &value) const {
    size_t first = default_hash<A>{}(value.first);
    size_t second = default_hash<B>{}(value.second);
    return mix_hash(
        first ^ (second + 0x9e3779b97f4a7c15ULL + (first << 6) + (first >> 2)));
  }
};

inline uint8_t h2(size_t hash) {
  uint8_t value = static_cast<uint8_t>((hash >> 7) & 0x7F);
  return value == 0 ? 1 : value;
}

} // namespace flat_hash_map_internal
} // namespace detail

template <typename K, typename V,
          typename Hash = detail::flat_hash_map_internal::default_hash<K>,
          typename Eq = std::equal_to<K>,
          typename Alloc = std::allocator<std::pair<K, V>>>
class flat_hash_map {
public:
  using key_type = K;
  using mapped_type = V;
  using value_type = std::pair<K, V>;
  using size_type = size_t;
  using difference_type = ptrdiff_t;
  using hasher = Hash;
  using key_equal = Eq;
  using allocator_type = Alloc;
  using reference = value_type &;
  using const_reference = const value_type &;
  using pointer = value_type *;
  using const_pointer = const value_type *;

private:
  struct slot_type {
    uint8_t control = detail::flat_hash_map_internal::kEmpty;
    std::optional<value_type> value;

    bool full() const {
      return control != detail::flat_hash_map_internal::kEmpty &&
             control != detail::flat_hash_map_internal::kDeleted;
    }
  };

  template <bool IsConst> class iterator_base {
    using slot_pointer =
        std::conditional_t<IsConst, const slot_type *, slot_type *>;

  public:
    using iterator_category = std::forward_iterator_tag;
    using value_type = flat_hash_map::value_type;
    using difference_type = flat_hash_map::difference_type;
    using reference =
        std::conditional_t<IsConst, const value_type &, value_type &>;
    using pointer =
        std::conditional_t<IsConst, const value_type *, value_type *>;

    iterator_base() : current_(nullptr), end_(nullptr) {}

    iterator_base(slot_pointer current, slot_pointer end)
        : current_(current), end_(end) {
      skip_empty();
    }

    template <bool B = IsConst, typename = std::enable_if_t<B>>
    iterator_base(const iterator_base<false> &other)
        : current_(other.current_), end_(other.end_) {}

    reference operator*() const { return *current_->value; }

    pointer operator->() const { return std::addressof(*current_->value); }

    iterator_base &operator++() {
      ++current_;
      skip_empty();
      return *this;
    }

    iterator_base operator++(int) {
      iterator_base copy = *this;
      ++(*this);
      return copy;
    }

    iterator_base &operator--() = delete;

    bool operator==(const iterator_base &other) const {
      return current_ == other.current_;
    }

    bool operator!=(const iterator_base &other) const {
      return !(*this == other);
    }

  private:
    friend class flat_hash_map;
    template <bool> friend class iterator_base;

    void skip_empty() {
      while (current_ != end_ && !current_->full()) {
        ++current_;
      }
    }

    slot_pointer current_;
    slot_pointer end_;
  };

public:
  using iterator = iterator_base<false>;
  using const_iterator = iterator_base<true>;

  flat_hash_map() { initialize(0); }

  explicit flat_hash_map(size_t bucket_count) { initialize(bucket_count); }

  flat_hash_map(std::initializer_list<value_type> values) {
    initialize(values.size());
    for (const auto &value : values) {
      emplace(value.first, value.second);
    }
  }

  flat_hash_map(const flat_hash_map &) = default;
  flat_hash_map(flat_hash_map &&) noexcept = default;
  flat_hash_map &operator=(const flat_hash_map &) = default;
  flat_hash_map &operator=(flat_hash_map &&) noexcept = default;
  ~flat_hash_map() = default;

  iterator begin() {
    return iterator(slots_.data(), slots_.data() + capacity_);
  }

  const_iterator begin() const {
    return const_iterator(slots_.data(), slots_.data() + capacity_);
  }

  const_iterator cbegin() const { return begin(); }

  iterator end() {
    return iterator(slots_.data() + capacity_, slots_.data() + capacity_);
  }

  const_iterator end() const {
    return const_iterator(slots_.data() + capacity_, slots_.data() + capacity_);
  }

  const_iterator cend() const { return end(); }

  bool empty() const { return size_ == 0; }

  size_t size() const { return size_; }

  size_t bucket_count() const { return capacity_; }

  size_t capacity() const { return capacity_; }

  float max_load_factor() const {
    return detail::flat_hash_map_internal::kMaxLoadFactor;
  }

  void max_load_factor(float) {}

  void clear() {
    for (auto &slot : slots_) {
      slot.value.reset();
      slot.control = detail::flat_hash_map_internal::kEmpty;
    }
    size_ = 0;
    growth_left_ = max_load_for_capacity(capacity_);
  }

  void reserve(size_t count) {
    size_t min_capacity = capacity_for_size(count);
    if (min_capacity > capacity_) {
      rehash(min_capacity);
    }
  }

  void rehash(size_t count) {
    size_t target = std::max(count, capacity_for_size(size_));
    target = detail::flat_hash_map_internal::next_capacity(target);
    if (target == capacity_) {
      return;
    }
    std::vector<slot_type> old_slots = std::move(slots_);
    initialize(target);
    for (auto &slot : old_slots) {
      if (slot.full()) {
        insert_existing(std::move(*slot.value));
      }
    }
  }

  iterator find(const K &key) {
    size_t index = find_index(key);
    return index == npos()
               ? end()
               : iterator(slots_.data() + index, slots_.data() + capacity_);
  }

  const_iterator find(const K &key) const {
    size_t index = find_index(key);
    return index == npos() ? end()
                           : const_iterator(slots_.data() + index,
                                            slots_.data() + capacity_);
  }

  size_t count(const K &key) const { return find_index(key) == npos() ? 0 : 1; }

  bool contains(const K &key) const { return count(key) != 0; }

  V &at(const K &key) {
    size_t index = find_index(key);
    if (index == npos()) {
      throw std::out_of_range("fory::flat_hash_map::at");
    }
    return slots_[index].value->second;
  }

  const V &at(const K &key) const {
    size_t index = find_index(key);
    if (index == npos()) {
      throw std::out_of_range("fory::flat_hash_map::at");
    }
    return slots_[index].value->second;
  }

  V &operator[](const K &key) {
    auto result = emplace(key, V{});
    return result.first->second;
  }

  V &operator[](K &&key) {
    auto result = emplace(std::move(key), V{});
    return result.first->second;
  }

  std::pair<iterator, bool> insert(const value_type &value) {
    return emplace(value.first, value.second);
  }

  std::pair<iterator, bool> insert(value_type &&value) {
    return emplace(std::move(value.first), std::move(value.second));
  }

  iterator insert(const_iterator, const value_type &value) {
    return insert(value).first;
  }

  iterator insert(const_iterator, value_type &&value) {
    return insert(std::move(value)).first;
  }

  template <typename InputIt> void insert(InputIt first, InputIt last) {
    for (; first != last; ++first) {
      insert(*first);
    }
  }

  template <typename... Args>
  std::pair<iterator, bool> emplace(Args &&...args) {
    return emplace_value(value_type(std::forward<Args>(args)...));
  }

  void swap(flat_hash_map &other) noexcept {
    slots_.swap(other.slots_);
    std::swap(size_, other.size_);
    std::swap(capacity_, other.capacity_);
    std::swap(growth_left_, other.growth_left_);
    std::swap(hash_, other.hash_);
    std::swap(eq_, other.eq_);
  }

  size_t erase(const K &key) {
    size_t index = find_index(key);
    if (index == npos()) {
      return 0;
    }
    erase_at(index);
    return 1;
  }

  void erase(iterator it) {
    if (it.current_ != slots_.data() + capacity_) {
      erase_at(static_cast<size_t>(it.current_ - slots_.data()));
    }
  }

private:
  static constexpr size_t npos() { return std::numeric_limits<size_t>::max(); }

  void initialize(size_t requested_capacity) {
    capacity_ = detail::flat_hash_map_internal::next_capacity(
        std::max<size_t>(requested_capacity, 8));
    slots_.clear();
    slots_.resize(capacity_);
    size_ = 0;
    growth_left_ = max_load_for_capacity(capacity_);
  }

  static size_t max_load_for_capacity(size_t capacity) {
    return std::max<size_t>(
        1, static_cast<size_t>(capacity *
                               detail::flat_hash_map_internal::kMaxLoadFactor));
  }

  static size_t capacity_for_size(size_t size) {
    size_t needed =
        static_cast<size_t>(static_cast<double>(size) /
                                detail::flat_hash_map_internal::kMaxLoadFactor +
                            1);
    return detail::flat_hash_map_internal::next_capacity(needed);
  }

  size_t hash_key(const K &key) const {
    return detail::flat_hash_map_internal::mix_hash(hash_(key));
  }

  size_t find_index(const K &key) const {
    if (capacity_ == 0) {
      return npos();
    }
    size_t hash = hash_key(key);
    uint8_t tag = detail::flat_hash_map_internal::h2(hash);
    size_t mask = capacity_ - 1;
    size_t index = hash & mask;
    size_t probes = 0;
    while (probes < capacity_) {
      const slot_type &slot = slots_[index];
      if (slot.control == detail::flat_hash_map_internal::kEmpty) {
        return npos();
      }
      if (slot.control == tag && slot.value && eq_(slot.value->first, key)) {
        return index;
      }
      index = (index + 1) & mask;
      ++probes;
    }
    return npos();
  }

  size_t find_insert_index(const K &key, size_t hash, bool &found) const {
    uint8_t tag = detail::flat_hash_map_internal::h2(hash);
    size_t mask = capacity_ - 1;
    size_t index = hash & mask;
    size_t first_deleted = npos();
    size_t probes = 0;
    while (probes < capacity_) {
      const slot_type &slot = slots_[index];
      if (slot.control == detail::flat_hash_map_internal::kEmpty) {
        found = false;
        return first_deleted == npos() ? index : first_deleted;
      }
      if (slot.control == detail::flat_hash_map_internal::kDeleted) {
        if (first_deleted == npos()) {
          first_deleted = index;
        }
      } else if (slot.control == tag && slot.value &&
                 eq_(slot.value->first, key)) {
        found = true;
        return index;
      }
      index = (index + 1) & mask;
      ++probes;
    }
    found = false;
    return first_deleted;
  }

  std::pair<iterator, bool> emplace_value(value_type value) {
    if (growth_left_ == 0) {
      rehash(capacity_ * 2);
    }
    size_t hash = hash_key(value.first);
    bool found = false;
    size_t index = find_insert_index(value.first, hash, found);
    if (found) {
      return {iterator(slots_.data() + index, slots_.data() + capacity_),
              false};
    }
    if (index == npos()) {
      rehash(capacity_ * 2);
      hash = hash_key(value.first);
      index = find_insert_index(value.first, hash, found);
    }
    slot_type &slot = slots_[index];
    bool reused_deleted =
        slot.control == detail::flat_hash_map_internal::kDeleted;
    slot.control = detail::flat_hash_map_internal::h2(hash);
    slot.value.emplace(std::move(value));
    ++size_;
    if (!reused_deleted) {
      --growth_left_;
    }
    return {iterator(slots_.data() + index, slots_.data() + capacity_), true};
  }

  void insert_existing(value_type value) {
    size_t hash = hash_key(value.first);
    bool found = false;
    size_t index = find_insert_index(value.first, hash, found);
    slot_type &slot = slots_[index];
    slot.control = detail::flat_hash_map_internal::h2(hash);
    slot.value.emplace(std::move(value));
    ++size_;
    --growth_left_;
  }

  void erase_at(size_t index) {
    slots_[index].value.reset();
    slots_[index].control = detail::flat_hash_map_internal::kDeleted;
    --size_;
  }

  std::vector<slot_type> slots_;
  size_t size_ = 0;
  size_t capacity_ = 0;
  size_t growth_left_ = 0;
  Hash hash_;
  Eq eq_;
};

template <typename K, typename V, typename Hash, typename Eq, typename Alloc>
void swap(flat_hash_map<K, V, Hash, Eq, Alloc> &left,
          flat_hash_map<K, V, Hash, Eq, Alloc> &right) noexcept {
  left.swap(right);
}

} // namespace fory
