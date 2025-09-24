// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

#pragma once

#include "fory/util/buffer.h"
#include "types.hpp"
#include <memory>

namespace fury {
namespace python {

namespace nb = nanobind;

// Python-specific Buffer wrapper that extends the C++ Buffer
class Buffer {
private:
    std::shared_ptr<fory::Buffer> buffer_;

public:
    // Constructors that delegate to the unified C++ Buffer
    Buffer() {
        buffer_ = std::make_shared<fory::Buffer>();
    }

    explicit Buffer(std::size_t initial_size) {
        std::uint8_t* data = new std::uint8_t[initial_size];
        buffer_ = std::make_shared<fory::Buffer>(data, initial_size, true);
    }

    Buffer(const std::uint8_t* data, std::size_t size) {
        std::uint8_t* copied_data = new std::uint8_t[size];
        std::memcpy(copied_data, data, size);
        buffer_ = std::make_shared<fory::Buffer>(copied_data, size, true);
    }

    Buffer(nb::bytes bytes) {
        std::string_view view = nb::cast<std::string_view>(bytes);
        std::uint8_t* data = new std::uint8_t[view.size()];
        std::memcpy(data, view.data(), view.size());
        buffer_ = std::make_shared<fory::Buffer>(data, view.size(), true);
    }

    // Properties - delegate to underlying C++ buffer
    inline std::uint32_t size() const noexcept { return buffer_->size(); }
    inline std::uint32_t reader_index() const noexcept { return buffer_->reader_index(); }
    inline std::uint32_t writer_index() const noexcept { return buffer_->writer_index(); }
    inline std::uint8_t* data() noexcept { return buffer_->data(); }
    inline const std::uint8_t* data() const noexcept { return buffer_->data(); }

    // Index management - delegate to underlying C++ buffer
    inline void set_reader_index(std::uint32_t index) { buffer_->ReaderIndex(index); }
    inline void set_writer_index(std::uint32_t index) { buffer_->WriterIndex(index); }

    // Buffer management - delegate to underlying C++ buffer
    inline void grow(std::uint32_t needed_size) {
        buffer_->Grow(needed_size);
    }

    inline void check_bound(std::uint32_t offset, std::uint32_t length) const {
        buffer_->CheckBound(offset, length);
    }

    inline void ensure(std::uint32_t length) {
        if (length > buffer_->size()) {
            buffer_->Reserve(length * 2);
        }
    }

    // Primitive operations - delegate to underlying C++ buffer

    // Primitive write methods - delegate to unified Buffer
    inline void write_bool(bool value) {
        buffer_->WriteBool(value);
    }

    inline void write_int8(std::int8_t value) {
        buffer_->WriteInt8(value);
    }

    inline void write_int16(std::int16_t value) {
        buffer_->WriteInt16(value);
    }

    inline void write_int32(std::int32_t value) {
        buffer_->WriteInt32(value);
    }

    inline void write_int64(std::int64_t value) {
        buffer_->WriteInt64(value);
    }

    inline void write_float(float value) {
        buffer_->WriteFloat(value);
    }

    inline void write_double(double value) {
        buffer_->WriteDouble(value);
    }

    // Variable-length integer encoding - delegate to unified Buffer
    inline void write_varuint32(std::uint32_t value) {
        buffer_->WriteVarUInt32(value);
    }

    inline void write_varint32(std::int32_t value) {
        buffer_->WriteVarInt32(value);
    }

    inline void write_varint64(std::int64_t value) {
        buffer_->WriteVarInt64(value);
    }

    inline void write_varuint64(std::uint64_t value) {
        buffer_->WriteVarUInt64(value);
    }

    // Primitive read methods - delegate to unified Buffer
    inline bool read_bool() {
        return buffer_->ReadBool();
    }

    inline std::int8_t read_int8() {
        return buffer_->ReadInt8();
    }

    inline std::int16_t read_int16() {
        return buffer_->ReadInt16();
    }

    inline std::int32_t read_int32() {
        return buffer_->ReadInt32();
    }

    inline std::int64_t read_int64() {
        return buffer_->ReadInt64();
    }

    inline float read_float() {
        return buffer_->ReadFloat();
    }

    inline double read_double() {
        return buffer_->ReadDouble();
    }

    // Variable-length integer decoding - delegate to unified Buffer
    inline std::uint32_t read_varuint32() {
        return buffer_->ReadVarUInt32();
    }

    inline std::int32_t read_varint32() {
        return buffer_->ReadVarInt32();
    }

    inline std::int64_t read_varint64() {
        return buffer_->ReadVarInt64();
    }

    inline std::uint64_t read_varuint64() {
        return buffer_->ReadVarUInt64();
    }

    // String operations - delegate to unified Buffer
    inline void write_string(const std::string& str) {
        buffer_->WriteString(str);
    }

    inline std::string read_string() {
        return buffer_->ReadString();
    }

    // Bytes operations
    inline void write_bytes(const std::vector<std::uint8_t>& bytes) {
        if (!bytes.empty()) {
            buffer_->WriteBytes(bytes.data(), bytes.size());
        }
    }

    inline void write_bytes_and_size(const std::vector<std::uint8_t>& bytes) {
        buffer_->WriteBytesAndSize(bytes.data(), bytes.size());
    }

    inline std::vector<std::uint8_t> read_bytes(std::uint32_t length) {
        if (length == 0) return {};

        std::vector<std::uint8_t> result(length);
        buffer_->ReadBytes(length, result.data());
        return result;
    }

    inline std::vector<std::uint8_t> read_bytes_and_size() {
        std::uint32_t length = buffer_->ReadVarUInt32();
        return read_bytes(length);
    }

    inline nb::bytes get_bytes(std::uint32_t offset, std::uint32_t length) const {
        buffer_->CheckBound(offset, length);
        return nb::bytes(reinterpret_cast<const char*>(buffer_->data() + offset), length);
    }

    // Bulk operations for better performance
    template<typename T>
    inline void write_array(const std::vector<T>& array) {
        write_array_python(array);
    }

    template<typename T>
    inline std::vector<T> read_array(std::uint32_t count) {
        return read_array_python<T>(count);
    }

    // Bit manipulation utilities - delegate to unified Buffer
    inline bool get_bit(std::uint32_t byte_offset, int bit_index) const {
        return buffer_->GetBit(byte_offset, bit_index);
    }

    inline void set_bit(std::uint32_t byte_offset, int bit_index) {
        buffer_->SetBit(byte_offset, bit_index);
    }

    inline void clear_bit(std::uint32_t byte_offset, int bit_index) {
        buffer_->ClearBit(byte_offset, bit_index);
    }

    // Skip reading
    inline void skip(std::uint32_t length) {
        buffer_->Skip(length);
    }

    // Export to Python bytes
    inline nb::bytes to_bytes() const {
        return nb::bytes(reinterpret_cast<const char*>(buffer_->data()), buffer_->writer_index());
    }

    inline nb::bytes to_bytes(std::uint32_t offset, std::uint32_t length) const {
        buffer_->CheckBound(offset, length);
        return nb::bytes(reinterpret_cast<const char*>(buffer_->data() + offset), length);
    }

    // Access to underlying C++ buffer for advanced operations
    inline fory::Buffer& get_c_buffer() { return *buffer_; }
    inline const fory::Buffer& get_c_buffer() const { return *buffer_; }

    // Python-specific bulk array operations
    template<typename T>
    inline void write_array_python(const std::vector<T>& array) {
        static_assert(std::is_arithmetic_v<T>, "T must be arithmetic type");
        if (array.empty()) return;

        std::uint32_t byte_size = array.size() * sizeof(T);
        buffer_->Grow(byte_size);
        buffer_->UnsafePut(buffer_->writer_index(), array.data(), byte_size);
        buffer_->IncreaseWriterIndex(byte_size);
    }

    template<typename T>
    inline std::vector<T> read_array_python(std::uint32_t count) {
        static_assert(std::is_arithmetic_v<T>, "T must be arithmetic type");
        if (count == 0) return {};

        std::uint32_t byte_size = count * sizeof(T);
        buffer_->CheckBound(buffer_->reader_index(), byte_size);

        std::vector<T> result(count);
        std::memcpy(result.data(), buffer_->data() + buffer_->reader_index(), byte_size);
        buffer_->IncreaseReaderIndex(byte_size);
        return result;
    }
};

// Utility functions for bit manipulation
inline bool get_bit(const Buffer& buffer, std::uint32_t byte_offset, int bit_index) {
    return buffer.get_bit(byte_offset, bit_index);
}

inline void set_bit(Buffer& buffer, std::uint32_t byte_offset, int bit_index) {
    buffer.set_bit(byte_offset, bit_index);
}

inline void clear_bit(Buffer& buffer, std::uint32_t byte_offset, int bit_index) {
    buffer.clear_bit(byte_offset, bit_index);
}

} // namespace python
} // namespace fury