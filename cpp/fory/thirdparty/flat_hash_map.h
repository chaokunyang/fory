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

// This file is a compact Fory-owned SwissTable flat hash map derived from
// Abseil's flat_hash_map/raw_hash_set design. It keeps the performance-critical
// pieces Fory relies on: separate control bytes, H1/H2 hash splitting,
// triangular group probing, and SIMD group matching on SSE2/AArch64 NEON.

#pragma once

#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <functional>
#include <initializer_list>
#include <iterator>
#include <limits>
#include <memory>
#include <new>
#include <stdexcept>
#include <string>
#include <type_traits>
#include <typeindex>
#include <utility>
#include <vector>

#if defined(__SSE2__) || defined(_M_X64) ||                                    \
    (defined(_M_IX86_FP) && _M_IX86_FP >= 2)
#include <emmintrin.h>
#define FORY_FLAT_HASH_MAP_HAVE_SSE2 1
#endif

#if defined(_MSC_VER)
#include <intrin.h>
#endif

#if defined(__ARM_NEON) || defined(__ARM_NEON__)
#include <arm_neon.h>
#define FORY_FLAT_HASH_MAP_HAVE_NEON 1
#endif

namespace fory {
namespace detail {
namespace flat_hash_map_internal {

using ctrl_t = int8_t;

constexpr ctrl_t kEmpty = static_cast<ctrl_t>(-128);
constexpr ctrl_t kDeleted = static_cast<ctrl_t>(-2);
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

inline uint8_t h2(size_t hash) {
  return static_cast<uint8_t>(hash >> (sizeof(size_t) * 8 - 7));
}

inline bool is_full(ctrl_t ctrl) { return ctrl >= 0; }
inline bool is_empty(ctrl_t ctrl) { return ctrl == kEmpty; }
inline bool is_deleted(ctrl_t ctrl) { return ctrl == kDeleted; }

inline size_t next_capacity(size_t requested) {
  size_t capacity = 16;
  while (capacity < requested) {
    capacity <<= 1;
  }
  return capacity;
}

inline uint32_t trailing_zeros(uint32_t value) {
#if defined(_MSC_VER)
  unsigned long index = 0;
  _BitScanForward(&index, value);
  return static_cast<uint32_t>(index);
#else
  return static_cast<uint32_t>(__builtin_ctz(value));
#endif
}

inline uint32_t lowest_bit(uint32_t mask) { return trailing_zeros(mask); }

inline uint32_t clear_lowest_bit(uint32_t mask) { return mask & (mask - 1); }

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

template <size_t Width> class probe_seq {
public:
  probe_seq(size_t hash, size_t mask) : mask_(mask), offset_(hash & mask) {}

  size_t offset() const { return offset_; }

  void next() {
    index_ += Width;
    offset_ = (offset_ + index_) & mask_;
  }

  size_t index() const { return index_; }

private:
  size_t mask_;
  size_t offset_;
  size_t index_ = 0;
};

#if defined(FORY_FLAT_HASH_MAP_HAVE_SSE2)
class group_sse2 {
public:
  static constexpr size_t kWidth = 16;

  explicit group_sse2(const ctrl_t *control)
      : control_(_mm_loadu_si128(reinterpret_cast<const __m128i *>(control))) {}

  uint32_t match(uint8_t tag) const {
    const __m128i match = _mm_set1_epi8(static_cast<char>(tag));
    return static_cast<uint32_t>(
        _mm_movemask_epi8(_mm_cmpeq_epi8(match, control_)));
  }

  uint32_t mask_empty() const {
    const __m128i match = _mm_set1_epi8(static_cast<char>(kEmpty));
    return static_cast<uint32_t>(
        _mm_movemask_epi8(_mm_cmpeq_epi8(match, control_)));
  }

  uint32_t mask_empty_or_deleted() const {
    const __m128i sentinel = _mm_set1_epi8(static_cast<char>(-1));
    return static_cast<uint32_t>(
        _mm_movemask_epi8(_mm_cmpgt_epi8(sentinel, control_)));
  }

private:
  __m128i control_;
};
#endif

#if defined(FORY_FLAT_HASH_MAP_HAVE_NEON)
inline uint32_t byte_msb_mask_to_bits(uint64_t mask, size_t width) {
  uint32_t bits = 0;
  for (size_t i = 0; i < width; ++i) {
    bits |= static_cast<uint32_t>((mask >> (i * 8 + 7)) & 1) << i;
  }
  return bits;
}

class group_neon {
public:
  static constexpr size_t kWidth = 8;

  explicit group_neon(const ctrl_t *control)
      : control_(vld1_u8(reinterpret_cast<const uint8_t *>(control))) {}

  uint32_t match(uint8_t tag) const {
    const uint8x8_t result = vceq_u8(control_, vdup_n_u8(tag));
    uint64_t raw = vget_lane_u64(vreinterpret_u64_u8(result), 0);
    return byte_msb_mask_to_bits(raw, kWidth);
  }

  uint32_t mask_empty() const {
    const uint8x8_t result =
        vceq_s8(vreinterpret_s8_u8(control_), vdup_n_s8(kEmpty));
    uint64_t raw = vget_lane_u64(vreinterpret_u64_u8(result), 0);
    return byte_msb_mask_to_bits(raw, kWidth);
  }

  uint32_t mask_empty_or_deleted() const {
    const uint8x8_t result = vcgt_s8(vdup_n_s8(static_cast<int8_t>(-1)),
                                     vreinterpret_s8_u8(control_));
    uint64_t raw = vget_lane_u64(vreinterpret_u64_u8(result), 0);
    return byte_msb_mask_to_bits(raw, kWidth);
  }

private:
  uint8x8_t control_;
};
#endif

class group_portable {
public:
  static constexpr size_t kWidth = 8;

  explicit group_portable(const ctrl_t *control) : control_(control) {}

  uint32_t match(uint8_t tag) const {
    uint32_t mask = 0;
    for (size_t i = 0; i < kWidth; ++i) {
      mask |= static_cast<uint32_t>(control_[i] == static_cast<ctrl_t>(tag))
              << i;
    }
    return mask;
  }

  uint32_t mask_empty() const {
    uint32_t mask = 0;
    for (size_t i = 0; i < kWidth; ++i) {
      mask |= static_cast<uint32_t>(is_empty(control_[i])) << i;
    }
    return mask;
  }

  uint32_t mask_empty_or_deleted() const {
    uint32_t mask = 0;
    for (size_t i = 0; i < kWidth; ++i) {
      mask |= static_cast<uint32_t>(control_[i] < static_cast<ctrl_t>(-1)) << i;
    }
    return mask;
  }

private:
  const ctrl_t *control_;
};

#if defined(FORY_FLAT_HASH_MAP_HAVE_SSE2)
using group = group_sse2;
#elif defined(FORY_FLAT_HASH_MAP_HAVE_NEON)
using group = group_neon;
#else
using group = group_portable;
#endif

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
  using ctrl_t = detail::flat_hash_map_internal::ctrl_t;
  using group = detail::flat_hash_map_internal::group;

  struct slot_type {
    alignas(value_type) unsigned char storage[sizeof(value_type)];

    value_type *value() { return reinterpret_cast<value_type *>(storage); }

    const value_type *value() const {
      return reinterpret_cast<const value_type *>(storage);
    }
  };

public:
  template <bool IsConst> class iterator_base {
    using map_pointer =
        std::conditional_t<IsConst, const flat_hash_map *, flat_hash_map *>;

  public:
    using iterator_category = std::forward_iterator_tag;
    using value_type = flat_hash_map::value_type;
    using difference_type = flat_hash_map::difference_type;
    using reference =
        std::conditional_t<IsConst, const value_type &, value_type &>;
    using pointer =
        std::conditional_t<IsConst, const value_type *, value_type *>;

    iterator_base() : map_(nullptr), index_(0) {}

    iterator_base(map_pointer map, size_t index) : map_(map), index_(index) {
      skip_empty();
    }

    template <bool B = IsConst, typename = std::enable_if_t<B>>
    iterator_base(const iterator_base<false> &other)
        : map_(other.map_), index_(other.index_) {}

    reference operator*() const { return *map_->slots_[index_].value(); }

    pointer operator->() const { return map_->slots_[index_].value(); }

    iterator_base &operator++() {
      ++index_;
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
      return map_ == other.map_ && index_ == other.index_;
    }

    bool operator!=(const iterator_base &other) const {
      return !(*this == other);
    }

  private:
    friend class flat_hash_map;
    template <bool> friend class iterator_base;

    void skip_empty() {
      while (map_ != nullptr && index_ < map_->capacity_ &&
             !detail::flat_hash_map_internal::is_full(map_->ctrl_[index_])) {
        ++index_;
      }
    }

    map_pointer map_;
    size_t index_;
  };

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

  flat_hash_map(const flat_hash_map &other)
      : hash_(other.hash_), eq_(other.eq_) {
    initialize(other.size_);
    for (const auto &value : other) {
      emplace(value.first, value.second);
    }
  }

  flat_hash_map(flat_hash_map &&other) noexcept
      : ctrl_(std::move(other.ctrl_)), slots_(std::move(other.slots_)),
        size_(other.size_), capacity_(other.capacity_),
        growth_left_(other.growth_left_), hash_(std::move(other.hash_)),
        eq_(std::move(other.eq_)) {
    other.size_ = 0;
    other.capacity_ = 0;
    other.growth_left_ = 0;
  }

  flat_hash_map &operator=(const flat_hash_map &other) {
    if (this == &other) {
      return *this;
    }
    flat_hash_map copy(other);
    swap(copy);
    return *this;
  }

  flat_hash_map &operator=(flat_hash_map &&other) noexcept {
    if (this == &other) {
      return *this;
    }
    destroy_values();
    ctrl_ = std::move(other.ctrl_);
    slots_ = std::move(other.slots_);
    size_ = other.size_;
    capacity_ = other.capacity_;
    growth_left_ = other.growth_left_;
    hash_ = std::move(other.hash_);
    eq_ = std::move(other.eq_);
    other.size_ = 0;
    other.capacity_ = 0;
    other.growth_left_ = 0;
    return *this;
  }

  ~flat_hash_map() { destroy_values(); }

  iterator begin() { return iterator(this, 0); }

  const_iterator begin() const { return const_iterator(this, 0); }

  const_iterator cbegin() const { return begin(); }

  iterator end() { return iterator(this, capacity_); }

  const_iterator end() const { return const_iterator(this, capacity_); }

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
    destroy_values();
    std::fill(ctrl_.begin(), ctrl_.end(),
              detail::flat_hash_map_internal::kEmpty);
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

    flat_hash_map replacement(target);
    replacement.hash_ = hash_;
    replacement.eq_ = eq_;
    for (size_t i = 0; i < capacity_; ++i) {
      if (detail::flat_hash_map_internal::is_full(ctrl_[i])) {
        replacement.insert_existing(std::move(*slots_[i].value()));
        slots_[i].value()->~value_type();
        set_ctrl(i, detail::flat_hash_map_internal::kEmpty);
      }
    }
    size_ = 0;
    swap(replacement);
  }

  iterator find(const K &key) {
    size_t index = find_index(key);
    return index == npos() ? end() : iterator(this, index);
  }

  const_iterator find(const K &key) const {
    size_t index = find_index(key);
    return index == npos() ? end() : const_iterator(this, index);
  }

  size_t count(const K &key) const { return find_index(key) == npos() ? 0 : 1; }

  bool contains(const K &key) const { return count(key) != 0; }

  V &at(const K &key) {
    size_t index = find_index(key);
    if (index == npos()) {
      throw std::out_of_range("fory::flat_hash_map::at");
    }
    return slots_[index].value()->second;
  }

  const V &at(const K &key) const {
    size_t index = find_index(key);
    if (index == npos()) {
      throw std::out_of_range("fory::flat_hash_map::at");
    }
    return slots_[index].value()->second;
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
    ctrl_.swap(other.ctrl_);
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
    if (it.map_ == this && it.index_ < capacity_) {
      erase_at(it.index_);
    }
  }

private:
  static constexpr size_t npos() { return std::numeric_limits<size_t>::max(); }

  void initialize(size_t requested_capacity) {
    capacity_ = detail::flat_hash_map_internal::next_capacity(
        std::max<size_t>(requested_capacity, group::kWidth));
    ctrl_.assign(capacity_ + group::kWidth,
                 detail::flat_hash_map_internal::kEmpty);
    slots_.clear();
    slots_.resize(capacity_);
    size_ = 0;
    growth_left_ = max_load_for_capacity(capacity_);
  }

  void destroy_values() {
    for (size_t i = 0; i < capacity_; ++i) {
      if (detail::flat_hash_map_internal::is_full(ctrl_[i])) {
        slots_[i].value()->~value_type();
      }
    }
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

  void set_ctrl(size_t index, ctrl_t value) {
    ctrl_[index] = value;
    if (index < group::kWidth) {
      ctrl_[capacity_ + index] = value;
    }
  }

  size_t slot_index(size_t group_offset, size_t group_index) const {
    return (group_offset + group_index) & (capacity_ - 1);
  }

  size_t find_index(const K &key) const {
    size_t hash = hash_key(key);
    uint8_t tag = detail::flat_hash_map_internal::h2(hash);
    detail::flat_hash_map_internal::probe_seq<group::kWidth> seq(hash,
                                                                 capacity_ - 1);

    while (seq.index() < capacity_) {
      group current(ctrl_.data() + seq.offset());
      uint32_t candidates = current.match(tag);
      while (candidates != 0) {
        uint32_t group_index =
            detail::flat_hash_map_internal::lowest_bit(candidates);
        size_t index = slot_index(seq.offset(), group_index);
        if (ctrl_[index] == static_cast<ctrl_t>(tag) &&
            eq_(slots_[index].value()->first, key)) {
          return index;
        }
        candidates =
            detail::flat_hash_map_internal::clear_lowest_bit(candidates);
      }
      if (current.mask_empty() != 0) {
        return npos();
      }
      seq.next();
    }
    return npos();
  }

  size_t find_insert_index(const K &key, size_t hash, bool &found) const {
    uint8_t tag = detail::flat_hash_map_internal::h2(hash);
    detail::flat_hash_map_internal::probe_seq<group::kWidth> seq(hash,
                                                                 capacity_ - 1);
    size_t first_deleted = npos();

    while (seq.index() < capacity_) {
      group current(ctrl_.data() + seq.offset());
      uint32_t candidates = current.match(tag);
      while (candidates != 0) {
        uint32_t group_index =
            detail::flat_hash_map_internal::lowest_bit(candidates);
        size_t index = slot_index(seq.offset(), group_index);
        if (ctrl_[index] == static_cast<ctrl_t>(tag) &&
            eq_(slots_[index].value()->first, key)) {
          found = true;
          return index;
        }
        candidates =
            detail::flat_hash_map_internal::clear_lowest_bit(candidates);
      }

      uint32_t non_full = current.mask_empty_or_deleted();
      while (non_full != 0) {
        uint32_t group_index =
            detail::flat_hash_map_internal::lowest_bit(non_full);
        size_t index = slot_index(seq.offset(), group_index);
        if (detail::flat_hash_map_internal::is_deleted(ctrl_[index])) {
          if (first_deleted == npos()) {
            first_deleted = index;
          }
        } else if (detail::flat_hash_map_internal::is_empty(ctrl_[index])) {
          found = false;
          return first_deleted == npos() ? index : first_deleted;
        }
        non_full = detail::flat_hash_map_internal::clear_lowest_bit(non_full);
      }
      seq.next();
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
      return {iterator(this, index), false};
    }
    if (index == npos()) {
      rehash(capacity_ * 2);
      hash = hash_key(value.first);
      index = find_insert_index(value.first, hash, found);
    }
    ctrl_t previous = ctrl_[index];
    new (slots_[index].value()) value_type(std::move(value));
    set_ctrl(index,
             static_cast<ctrl_t>(detail::flat_hash_map_internal::h2(hash)));
    ++size_;
    if (!detail::flat_hash_map_internal::is_deleted(previous)) {
      --growth_left_;
    }
    return {iterator(this, index), true};
  }

  void insert_existing(value_type value) {
    size_t hash = hash_key(value.first);
    bool found = false;
    size_t index = find_insert_index(value.first, hash, found);
    new (slots_[index].value()) value_type(std::move(value));
    set_ctrl(index,
             static_cast<ctrl_t>(detail::flat_hash_map_internal::h2(hash)));
    ++size_;
    --growth_left_;
  }

  void erase_at(size_t index) {
    slots_[index].value()->~value_type();
    set_ctrl(index, detail::flat_hash_map_internal::kDeleted);
    --size_;
  }

  std::vector<ctrl_t> ctrl_;
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

#undef FORY_FLAT_HASH_MAP_HAVE_SSE2
#undef FORY_FLAT_HASH_MAP_HAVE_NEON
