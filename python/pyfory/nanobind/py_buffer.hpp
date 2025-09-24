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

#include <nanobind/nanobind.h>
#include <nanobind/stl.h>
#include "fory/util/buffer.h"
#include "exceptions.hpp"
#include <memory>
#include <cstring>

namespace fury {
namespace python {

namespace nb = nanobind;

// Ultra-thin nanobind wrapper around fury::Buffer
// Only handles Python-specific operations - everything else delegates to C++ buffer
class PyBuffer {
private:
    std::shared_ptr<fury::Buffer> buffer_;

public:
    // Constructors
    PyBuffer() {
        fury::Buffer* buf = fury::AllocateBuffer(1024);  // Default 1KB
        if (!buf) {
            throw BufferException("Failed to allocate default buffer (1024 bytes)");
        }
        buffer_ = std::shared_ptr<fury::Buffer>(buf);
    }

    explicit PyBuffer(std::size_t initial_size) {
        if (initial_size == 0) {
            throw ValidationException("Buffer size must be greater than 0");
        }
        fury::Buffer* buf = fury::AllocateBuffer(initial_size);
        if (!buf) {
            throw BufferException("Failed to allocate buffer of size " + std::to_string(initial_size));
        }
        buffer_ = std::shared_ptr<fury::Buffer>(buf);
    }

    PyBuffer(nb::bytes bytes) {
        std::string_view view = nb::cast<std::string_view>(bytes);
        uint8_t* data = new uint8_t[view.size()];
        std::memcpy(data, view.data(), view.size());
        buffer_ = std::make_shared<fury::Buffer>(data, view.size(), true);
        buffer_->WriterIndex(view.size());  // Set writer index to data size
    }

    // Properties - direct delegation to C++ buffer
    inline std::uint8_t* data() const { return buffer_->data(); }
    inline std::uint32_t size() const { return buffer_->size(); }
    inline std::uint32_t reader_index() const { return buffer_->reader_index(); }
    inline std::uint32_t writer_index() const { return buffer_->writer_index(); }
    inline void set_reader_index(std::uint32_t index) { buffer_->ReaderIndex(index); }
    inline void set_writer_index(std::uint32_t index) { buffer_->WriterIndex(index); }

    // Buffer management - direct delegation
    inline void grow(std::uint32_t needed_size) { buffer_->Grow(needed_size); }
    inline void ensure(std::uint32_t length) { buffer_->EnsureCapacity(length); }
    inline void skip(std::uint32_t length) { buffer_->Skip(length); }

    // All primitive operations - direct delegation to C++ buffer (maximum performance)
    inline void write_bool(bool value) { buffer_->WriteBool(value); }
    inline void write_int8(std::int8_t value) { buffer_->WriteInt8(value); }
    inline void write_int16(std::int16_t value) { buffer_->WriteInt16(value); }
    inline void write_int32(std::int32_t value) { buffer_->WriteInt32(value); }
    inline void write_int64(std::int64_t value) { buffer_->WriteInt64(value); }
    inline void write_float(float value) { buffer_->WriteFloat(value); }
    inline void write_double(double value) { buffer_->WriteDouble(value); }

    inline bool read_bool() { return buffer_->ReadBool(); }
    inline std::int8_t read_int8() { return buffer_->ReadInt8(); }
    inline std::int16_t read_int16() { return buffer_->ReadInt16(); }
    inline std::int32_t read_int32() { return buffer_->ReadInt32(); }
    inline std::int64_t read_int64() { return buffer_->ReadInt64(); }
    inline float read_float() { return buffer_->ReadFloat(); }
    inline double read_double() { return buffer_->ReadDouble(); }

    // Variable-length integer operations - direct delegation
    inline void write_varint32(std::int32_t value) { buffer_->WriteVarInt32(value); }
    inline void write_varuint32(std::uint32_t value) { buffer_->WriteVarUInt32(value); }
    inline void write_varint64(std::int64_t value) { buffer_->WriteVarInt64(value); }
    inline void write_varuint64(std::uint64_t value) { buffer_->WriteVarUInt64(value); }

    inline std::int32_t read_varint32() { return buffer_->ReadVarInt32(); }
    inline std::uint32_t read_varuint32() { return buffer_->ReadVarUInt32(); }
    inline std::int64_t read_varint64() { return buffer_->ReadVarInt64(); }
    inline std::uint64_t read_varuint64() { return buffer_->ReadVarUInt64(); }

    // Int24 support - direct delegation
    inline void write_int24(std::int32_t value) { buffer_->WriteInt24(value); }
    inline std::int32_t read_int24() { return buffer_->ReadInt24(); }

    // Standard string operations - direct delegation to C++ buffer
    inline void write_string(const std::string& str) { buffer_->WriteString(str); }
    inline std::string read_string() { return buffer_->ReadString(); }

    // PYTHON-SPECIFIC OPERATIONS ONLY (the only place where we handle PyObject*)
    void write_python_string(nb::handle str_obj) {
        // Optimized Python Unicode string encoding using direct Python APIs
        exceptions::validate_python_object(str_obj, "str");
        if (!nb::isinstance<nb::str>(str_obj)) {
            throw SerializationException("Expected string object, got " + std::string(nb::cast<std::string>(nb::repr(nb::type_name(str_obj)))));
        }

        nb::str str = nb::cast<nb::str>(str_obj);
        PyObject* py_str = str.ptr();

        // Get string properties using Python C API
        Py_ssize_t length = PyUnicode_GET_LENGTH(py_str);
        int kind = PyUnicode_KIND(py_str);
        void* data = PyUnicode_DATA(py_str);

        std::uint64_t header = 0;
        std::uint32_t buffer_size = 0;

        if (kind == PyUnicode_1BYTE_KIND) {
            // 1-byte per character (Latin-1/ASCII)
            buffer_size = length;
            header = (length << 2) | 0;  // encoding = 0
        } else if (kind == PyUnicode_2BYTE_KIND) {
            // 2-byte per character (UCS2)
            buffer_size = length << 1;
            header = (length << 3) | 1;  // encoding = 1
        } else {
            // 4-byte per character - convert to UTF-8
            const char* utf8_data = PyUnicode_AsUTF8AndSize(py_str, &length);
            data = const_cast<char*>(utf8_data);
            buffer_size = length;
            header = (buffer_size << 2) | 2;  // encoding = 2
        }

        // Write header and string data
        write_varuint64(header);
        if (buffer_size > 0) {
            buffer_->Grow(buffer_size);
            buffer_->CopyFrom(buffer_->writer_index(),
                            static_cast<const uint8_t*>(data), 0, buffer_size);
            buffer_->IncreaseWriterIndex(buffer_size);
        }
    }

    nb::object read_python_string() {
        // Optimized Python string decoding using direct Python APIs
        std::uint64_t header = read_varuint64();
        std::uint32_t size = header >> 2;
        std::uint32_t encoding = header & 0b11;

        if (size == 0) {
            return nb::str("");
        }

        if (buffer_->reader_index() + size > buffer_->size()) {
            throw BufferUnderflowException("Cannot read " + std::to_string(size) + " bytes at position " +
                                          std::to_string(buffer_->reader_index()) + " (buffer size: " +
                                          std::to_string(buffer_->size()) + ")");
        }

        const char* buf = reinterpret_cast<const char*>(buffer_->data() + buffer_->reader_index());
        buffer_->IncreaseReaderIndex(size);

        PyObject* result = nullptr;
        if (encoding == 0) {
            // Latin-1 encoding
            result = PyUnicode_DecodeLatin1(buf, size, "strict");
        } else if (encoding == 1) {
            // UTF-16 (need to check for surrogate pairs)
            const uint16_t* utf16_buf = reinterpret_cast<const uint16_t*>(buf);
            bool has_surrogates = false;

            // Quick check for surrogate pairs
            for (size_t i = 0; i < size / 2; i++) {
                if ((utf16_buf[i] & 0xF800) == 0xD800) {
                    has_surrogates = true;
                    break;
                }
            }

            if (has_surrogates) {
                int byteorder = -1;  // Little-endian
                result = PyUnicode_DecodeUTF16(buf, size, nullptr, &byteorder);
            } else {
                result = PyUnicode_FromKindAndData(PyUnicode_2BYTE_KIND, buf, size / 2);
            }
        } else {
            // UTF-8 encoding
            result = PyUnicode_DecodeUTF8(buf, size, "strict");
        }

        if (!result) {
            throw DeserializationException("Failed to decode Python string with encoding " + std::to_string(encoding));
        }

        return nb::steal(result);
    }

    // Python buffer protocol support
    void write_from_python_buffer(nb::handle buffer_obj) {
        // Extract data from Python buffer protocol object
        exceptions::validate_python_object(buffer_obj, "buffer protocol object");
        if (!nb::hasattr(buffer_obj, "__buffer__")) {
            throw SerializationException("Object doesn't support buffer protocol");
        }

        nb::bytes bytes = nb::cast<nb::bytes>(buffer_obj);
        std::string_view view = nb::cast<std::string_view>(bytes);

        if (!view.empty()) {
            buffer_->WriteBytes(reinterpret_cast<const uint8_t*>(view.data()), view.size());
        }
    }

    // Bit manipulation - delegate to existing bit utilities if available
    inline bool get_bit(std::uint32_t byte_offset, int bit_index) const {
        exceptions::validate_buffer_bounds(byte_offset, 1, buffer_->size());
        if (bit_index < 0 || bit_index > 7) {
            throw ValidationException("Bit index must be between 0 and 7, got " + std::to_string(bit_index));
        }
        return (buffer_->data()[byte_offset] & (1 << bit_index)) != 0;
    }

    inline void set_bit(std::uint32_t byte_offset, int bit_index) {
        exceptions::validate_buffer_bounds(byte_offset, 1, buffer_->size());
        if (bit_index < 0 || bit_index > 7) {
            throw ValidationException("Bit index must be between 0 and 7, got " + std::to_string(bit_index));
        }
        buffer_->data()[byte_offset] |= (1 << bit_index);
    }

    inline void clear_bit(std::uint32_t byte_offset, int bit_index) {
        exceptions::validate_buffer_bounds(byte_offset, 1, buffer_->size());
        if (bit_index < 0 || bit_index > 7) {
            throw ValidationException("Bit index must be between 0 and 7, got " + std::to_string(bit_index));
        }
        buffer_->data()[byte_offset] &= ~(1 << bit_index);
    }

    // Bytes operations - delegate to C++ buffer
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

        exceptions::validate_buffer_bounds(buffer_->reader_index(), length, buffer_->size());

        std::vector<std::uint8_t> result(length);
        buffer_->ReadBytes(length, result.data());
        return result;
    }

    // Export to Python - PYTHON-SPECIFIC
    inline nb::bytes to_bytes() const {
        return nb::bytes(reinterpret_cast<const char*>(buffer_->data()), buffer_->writer_index());
    }

    inline nb::bytes to_bytes(std::uint32_t offset, std::uint32_t length) const {
        exceptions::validate_buffer_bounds(offset, length, buffer_->size());
        return nb::bytes(reinterpret_cast<const char*>(buffer_->data() + offset), length);
    }

    inline nb::bytes get_bytes(std::uint32_t offset, std::uint32_t length) const {
        return to_bytes(offset, length);
    }

    // Access underlying C++ buffer for advanced operations
    inline fury::Buffer& get_c_buffer() { return *buffer_; }
    inline const fury::Buffer& get_c_buffer() const { return *buffer_; }
    inline std::shared_ptr<fury::Buffer> get_shared_buffer() { return buffer_; }

    // Buffer slicing
    PyBuffer slice(std::uint32_t offset, std::uint32_t length = 0) const {
        if (length == 0) {
            if (offset > buffer_->size()) {
                throw ValidationException("Slice offset " + std::to_string(offset) + " exceeds buffer size " + std::to_string(buffer_->size()));
            }
            length = buffer_->size() - offset;
        }
        exceptions::validate_buffer_bounds(offset, length, buffer_->size());

        // Create new buffer with sliced data
        PyBuffer sliced(length);
        std::memcpy(sliced.data(), buffer_->data() + offset, length);
        sliced.set_writer_index(length);
        return sliced;
    }

    // Python-compatible methods
    std::size_t len() const { return buffer_->size(); }

    std::string hex() const {
        return buffer_->Hex();
    }

    std::string repr() const {
        return "BufferWrapper(reader_index=" + std::to_string(reader_index()) +
               ", writer_index=" + std::to_string(writer_index()) +
               ", size=" + std::to_string(size()) + ")";
    }
};

// Utility functions for bit manipulation (same as _util.pyx API)
inline bool get_bit(const PyBuffer& buffer, std::uint32_t base_offset, std::uint32_t index) {
    return buffer.get_bit(base_offset, index);
}

inline void set_bit(PyBuffer& buffer, std::uint32_t base_offset, std::uint32_t index) {
    buffer.set_bit(base_offset, index);
}

inline void clear_bit(PyBuffer& buffer, std::uint32_t base_offset, std::uint32_t index) {
    buffer.clear_bit(base_offset, index);
}

} // namespace python
} // namespace fury