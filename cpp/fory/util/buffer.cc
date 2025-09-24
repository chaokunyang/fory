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

#include <string>

#include "fory/util/buffer.h"
#include "fory/util/logging.h"

namespace fory {

Buffer::Buffer() {
  data_ = nullptr;
  size_ = -1;
  own_data_ = false;
  writer_index_ = 0;
  reader_index_ = 0;
}

Buffer::Buffer(Buffer &&buffer) noexcept {
  data_ = buffer.data_;
  size_ = buffer.size_;
  own_data_ = buffer.own_data_;
  writer_index_ = buffer.writer_index_;
  reader_index_ = buffer.reader_index_;
  buffer.data_ = nullptr;
  buffer.size_ = -1;
  buffer.own_data_ = false;
}

Buffer &Buffer::operator=(Buffer &&buffer) noexcept {
  if (own_data_) {
    delete data_;
    data_ = nullptr;
  }
  data_ = buffer.data_;
  size_ = buffer.size_;
  own_data_ = buffer.own_data_;
  writer_index_ = buffer.writer_index_;
  reader_index_ = buffer.reader_index_;
  buffer.data_ = nullptr;
  buffer.size_ = -1;
  buffer.own_data_ = false;
  return *this;
}

Buffer::~Buffer() {
  if (own_data_) {
    free(data_);
    data_ = nullptr;
  }
}

bool Buffer::Equals(const Buffer &other, int64_t nbytes) const {
  return this == &other ||
         (size_ >= nbytes && other.size_ >= nbytes &&
          (data_ == other.data_ ||
           !memcmp(data_, other.data_, static_cast<size_t>(nbytes))));
}

bool Buffer::Equals(const Buffer &other) const {
  return this == &other ||
         (size_ == other.size_ &&
          (data_ == other.data_ ||
           !memcmp(data_, other.data_, static_cast<size_t>(size_))));
}

void Buffer::Copy(const uint32_t start, const uint32_t nbytes,
                  std::shared_ptr<Buffer> &out) const {
  std::memcpy(out->data(), data_ + start, static_cast<size_t>(nbytes));
}

void Buffer::Copy(uint32_t start, uint32_t nbytes, Buffer &out) const {
  std::memcpy(out.data(), data_ + start, static_cast<size_t>(nbytes));
}

void Buffer::Copy(uint32_t start, uint32_t nbytes, uint8_t *out) const {
  Copy(start, nbytes, out, 0);
}

void Buffer::Copy(uint32_t start, uint32_t nbytes, uint8_t *out,
                  uint32_t offset) const {
  std::memcpy(out + offset, data_ + start, static_cast<size_t>(nbytes));
}

void Buffer::CopyFrom(uint32_t offset, const uint8_t *src, uint32_t src_offset,
                      uint32_t nbytes) {
  auto new_size = offset + nbytes;
  if (new_size > size_) {
    Reserve(new_size * 2);
  }
  std::memcpy(data_ + offset, src + src_offset, static_cast<size_t>(nbytes));
}

std::string Buffer::ToString() const {
  return std::string(reinterpret_cast<const char *>(data_),
                     static_cast<size_t>(size_));
}

std::string Buffer::Hex() const {
  return util::hex(data(), static_cast<int32_t>(size_));
}

bool AllocateBuffer(uint32_t size, std::shared_ptr<Buffer> *out) {
  auto *data = static_cast<uint8_t *>(malloc(static_cast<size_t>(size)));
  if (data) {
    *out = std::make_shared<Buffer>(data, size);
    return true;
  } else {
    return false;
  }
}

bool AllocateBuffer(uint32_t size, Buffer **out) {
  auto *data = static_cast<uint8_t *>(malloc(static_cast<size_t>(size)));
  if (data) {
    *out = new Buffer(data, size);
    return true;
  } else {
    return false;
  }
}

Buffer *AllocateBuffer(uint32_t size) {
  auto data = static_cast<uint8_t *>(malloc(static_cast<size_t>(size)));
  if (data) {
    return new Buffer(data, size);
  } else {
    return nullptr;
  }
}

// 64-bit varint operations - optimized version from _util.pyx
uint32_t Buffer::PutVarUint64(uint32_t offset, uint64_t value) {
  uint8_t* arr = data_ + offset;

  if (value >> 7 == 0) {
    arr[0] = static_cast<uint8_t>(value);
    return 1;
  }
  arr[0] = static_cast<uint8_t>((value & 0x7F) | 0x80);

  if (value >> 14 == 0) {
    arr[1] = static_cast<uint8_t>(value >> 7);
    return 2;
  }
  arr[1] = static_cast<uint8_t>((value >> 7) | 0x80);

  if (value >> 21 == 0) {
    arr[2] = static_cast<uint8_t>(value >> 14);
    return 3;
  }
  arr[2] = static_cast<uint8_t>((value >> 14) | 0x80);

  if (value >> 28 == 0) {
    arr[3] = static_cast<uint8_t>(value >> 21);
    return 4;
  }
  arr[3] = static_cast<uint8_t>((value >> 21) | 0x80);

  if (value >> 35 == 0) {
    arr[4] = static_cast<uint8_t>(value >> 28);
    return 5;
  }
  arr[4] = static_cast<uint8_t>((value >> 28) | 0x80);

  if (value >> 42 == 0) {
    arr[5] = static_cast<uint8_t>(value >> 35);
    return 6;
  }
  arr[5] = static_cast<uint8_t>((value >> 35) | 0x80);

  if (value >> 49 == 0) {
    arr[6] = static_cast<uint8_t>(value >> 42);
    return 7;
  }
  arr[6] = static_cast<uint8_t>((value >> 42) | 0x80);

  if (value >> 56 == 0) {
    arr[7] = static_cast<uint8_t>(value >> 49);
    return 8;
  }
  arr[7] = static_cast<uint8_t>((value >> 49) | 0x80);
  arr[8] = static_cast<uint8_t>(value >> 56);
  return 9;
}

uint64_t Buffer::GetVarUint64(uint32_t offset, uint32_t *readBytesLength) {
  uint8_t* arr = data_ + offset;
  uint32_t read_length = 1;
  uint64_t result;

  uint8_t b = arr[0];
  result = b & 0x7F;
  if ((b & 0x80) != 0) {
    read_length++;
    b = arr[1];
    result |= static_cast<uint64_t>(b & 0x7F) << 7;
    if ((b & 0x80) != 0) {
      read_length++;
      b = arr[2];
      result |= static_cast<uint64_t>(b & 0x7F) << 14;
      if ((b & 0x80) != 0) {
        read_length++;
        b = arr[3];
        result |= static_cast<uint64_t>(b & 0x7F) << 21;
        if ((b & 0x80) != 0) {
          read_length++;
          b = arr[4];
          result |= static_cast<uint64_t>(b & 0x7F) << 28;
          if ((b & 0x80) != 0) {
            read_length++;
            b = arr[5];
            result |= static_cast<uint64_t>(b & 0x7F) << 35;
            if ((b & 0x80) != 0) {
              read_length++;
              b = arr[6];
              result |= static_cast<uint64_t>(b & 0x7F) << 42;
              if ((b & 0x80) != 0) {
                read_length++;
                b = arr[7];
                result |= static_cast<uint64_t>(b & 0x7F) << 49;
                if ((b & 0x80) != 0) {
                  read_length++;
                  b = arr[8];
                  // Highest bit in last byte is sign bit
                  result |= static_cast<uint64_t>(b) << 56;
                }
              }
            }
          }
        }
      }
    }
  }
  *readBytesLength = read_length;
  return result;
}

} // namespace fory
