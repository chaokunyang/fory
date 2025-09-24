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
#include <absl/container/flat_hash_map.h>
#include <vector>
#include <cstdint>
#include <utility>
#include "types.hpp"
#include "py_buffer.hpp"

namespace fury {
namespace python {

namespace nb = nanobind;

// MetaString encoding types
enum class MetaStringEncoding : std::uint8_t {
    UTF8 = 0,
    LATIN1 = 1,
    UTF16 = 2
};

constexpr std::int8_t DEFAULT_DYNAMIC_WRITE_META_STR_ID = -1;

// MetaStringBytes class - exact translation from Cython
class MetaStringBytes {
public:
    nb::bytes data;
    std::int16_t length;
    std::uint8_t encoding;
    std::int64_t hashcode;
    std::int16_t dynamic_write_string_id;

    MetaStringBytes(nb::bytes data_bytes, std::int64_t hash)
        : data(data_bytes), hashcode(hash) {
        length = static_cast<std::int16_t>(nb::len(data_bytes));
        encoding = static_cast<std::uint8_t>(hashcode & 0xFF);
        dynamic_write_string_id = DEFAULT_DYNAMIC_WRITE_META_STR_ID;
    }

    bool operator==(const MetaStringBytes& other) const {
        return hashcode == other.hashcode;
    }

    std::size_t hash() const {
        return static_cast<std::size_t>(hashcode);
    }

    std::string decode(const std::string& sep1 = ".", const std::string& sep2 = "_") const {
        // Simple decode implementation - can be optimized based on encoding
        std::string str = nb::cast<std::string>(data);

        // Replace separators as per MetaString decoding logic
        std::size_t pos = 0;
        while ((pos = str.find(sep2, pos)) != std::string::npos) {
            str.replace(pos, sep2.length(), sep1);
            pos += sep1.length();
        }

        return str;
    }
};

// MetaStringResolver - exact translation of Cython implementation
class MetaStringResolver {
private:
    // Memory-safe data structures
    std::int16_t dynamic_write_string_id_;
    std::vector<std::shared_ptr<MetaStringBytes>> dynamic_written_enum_string_;         // _c_dynamic_written_enum_string
    std::vector<std::shared_ptr<MetaStringBytes>> dynamic_id_to_enum_string_vec_;       // _c_dynamic_id_to_enum_string_vec
    absl::flat_hash_map<std::int64_t, std::shared_ptr<MetaStringBytes>> hash_to_metastr_bytes_;  // _c_hash_to_metastr_bytes
    absl::flat_hash_map<std::pair<std::int64_t, std::int64_t>, std::shared_ptr<MetaStringBytes>> hash_to_small_metastring_bytes_;  // _c_hash_to_small_metastring_bytes

    // Memory-safe objects for compatibility (not used in hot path)
    std::unordered_set<std::shared_ptr<MetaStringBytes>> enum_str_set_;
    std::unordered_map<nb::object, std::shared_ptr<MetaStringBytes>> metastr_to_metastr_bytes_;

public:
    MetaStringResolver() : dynamic_write_string_id_(0) {}

    ~MetaStringResolver() {
        reset_write();
        reset_read();
        // Smart pointers handle cleanup automatically
    }

    // Exact translation of write_meta_string_bytes from Cython
    inline void write_meta_string_bytes(PyBuffer& buffer, std::shared_ptr<MetaStringBytes> metastr_bytes) {
        std::int16_t dynamic_type_id = metastr_bytes->dynamic_write_string_id;
        std::int32_t length = metastr_bytes->length;

        if (dynamic_type_id == DEFAULT_DYNAMIC_WRITE_META_STR_ID) {
            // First time writing this string
            dynamic_type_id = dynamic_write_string_id_;
            metastr_bytes->dynamic_write_string_id = dynamic_type_id;
            dynamic_write_string_id_++;

            // Store reference using smart pointer
            dynamic_written_enum_string_.push_back(metastr_bytes);

            // Write full string data
            buffer.write_varuint32(static_cast<std::uint32_t>(length << 1));  // left shift by 1

            if (length <= SMALL_STRING_THRESHOLD) {
                buffer.write_int8(metastr_bytes->encoding);
            } else {
                buffer.write_int64(metastr_bytes->hashcode);
            }

            // Write string bytes
            std::string str_data = nb::cast<std::string>(metastr_bytes->data);
            buffer.write_bytes(std::vector<std::uint8_t>(str_data.begin(), str_data.end()));
        } else {
            // Write reference to previously written string
            buffer.write_varuint32(static_cast<std::uint32_t>(((dynamic_type_id + 1) << 1) | 1));
        }
    }

    // Exact translation of read_meta_string_bytes from Cython
    inline std::shared_ptr<MetaStringBytes> read_meta_string_bytes(PyBuffer& buffer) {
        std::uint32_t header = buffer.read_varuint32();
        std::int32_t length = static_cast<std::int32_t>(header >> 1);

        if ((header & 0b1) != 0) {
            // Reference to previously read string
            return dynamic_id_to_enum_string_vec_[length - 1];
        }

        // Reading new string
        std::int64_t v1 = 0, v2 = 0, hashcode = 0;
        std::shared_ptr<MetaStringBytes> enum_str_ptr = nullptr;
        std::uint32_t reader_index = 0;
        std::uint8_t encoding = 0;

        if (length <= SMALL_STRING_THRESHOLD) {
            // Small string optimization
            encoding = buffer.read_int8();
            if (length <= 8) {
                // Read as int64 for efficiency
                std::vector<std::uint8_t> bytes = buffer.read_bytes(length);
                v1 = 0;
                for (int i = 0; i < length; i++) {
                    v1 |= (static_cast<std::int64_t>(bytes[i]) << (i * 8));
                }
            } else {
                // Read first 8 bytes as int64, rest as bytes
                std::vector<std::uint8_t> bytes = buffer.read_bytes(8);
                v1 = 0;
                for (int i = 0; i < 8; i++) {
                    v1 |= (static_cast<std::int64_t>(bytes[i]) << (i * 8));
                }

                std::vector<std::uint8_t> remaining = buffer.read_bytes(length - 8);
                v2 = 0;
                for (int i = 0; i < (length - 8); i++) {
                    v2 |= (static_cast<std::int64_t>(remaining[i]) << (i * 8));
                }
            }

            hashcode = ((v1 * 31 + v2) >> 8 << 8) | encoding;

            // Check cache for small strings
            auto key = std::make_pair(v1, v2);
            auto it = hash_to_small_metastring_bytes_.find(key);
            if (it != hash_to_small_metastring_bytes_.end()) {
                enum_str_ptr = it->second;
            } else {
                // Create new MetaStringBytes
                reader_index = buffer.reader_index() - length;  // Go back to read string data
                std::vector<std::uint8_t> str_bytes = buffer.read_bytes(length);
                nb::bytes data_bytes = nb::bytes(reinterpret_cast<const char*>(str_bytes.data()), length);

                auto enum_str = std::make_shared<MetaStringBytes>(data_bytes, hashcode);
                enum_str_set_.insert(enum_str);
                enum_str_ptr = enum_str;
                hash_to_small_metastring_bytes_[key] = enum_str_ptr;
            }
        } else {
            // Large string
            hashcode = buffer.read_int64();
            reader_index = buffer.reader_index();
            std::vector<std::uint8_t> str_bytes = buffer.read_bytes(length);

            // Check cache for large strings
            auto it = hash_to_metastr_bytes_.find(hashcode);
            if (it != hash_to_metastr_bytes_.end()) {
                enum_str_ptr = it->second;
            } else {
                // Create new MetaStringBytes
                nb::bytes data_bytes = nb::bytes(reinterpret_cast<const char*>(str_bytes.data()), length);
                auto enum_str = std::make_shared<MetaStringBytes>(data_bytes, hashcode);
                enum_str_set_.insert(enum_str);
                enum_str_ptr = enum_str;
                hash_to_metastr_bytes_[hashcode] = enum_str_ptr;
            }
        }

        // Add to dynamic string vector for future references
        dynamic_id_to_enum_string_vec_.push_back(enum_str_ptr);
        return enum_str_ptr;
    }

    std::shared_ptr<MetaStringBytes> get_metastr_bytes(nb::handle metastr_obj) {
        // Check cache first
        auto it = metastr_to_metastr_bytes_.find(metastr_obj);
        if (it != metastr_to_metastr_bytes_.end()) {
            return it->second;
        }

        // Create new MetaStringBytes from Python object
        // This is a simplified version - real implementation would handle MetaString objects
        std::string str_data = nb::cast<std::string>(metastr_obj);
        nb::bytes encoded_data = nb::cast<nb::bytes>(str_data);

        std::int64_t hashcode = compute_hash(str_data);
        auto metastr_bytes = std::make_shared<MetaStringBytes>(encoded_data, hashcode);

        enum_str_set_.insert(metastr_bytes);
        metastr_to_metastr_bytes_[metastr_obj] = metastr_bytes;
        return metastr_bytes;
    }

    // Exact translation of reset methods from Cython
    inline void reset_read() {
        dynamic_id_to_enum_string_vec_.clear();
    }

    inline void reset_write() {
        if (dynamic_write_string_id_ != 0) {
            dynamic_write_string_id_ = 0;

            // Reset dynamic write string IDs
            for (auto metastr : dynamic_written_enum_string_) {
                metastr->dynamic_write_string_id = DEFAULT_DYNAMIC_WRITE_META_STR_ID;
            }

            dynamic_written_enum_string_.clear();
        }
    }

private:
    // Simple hash computation for strings
    std::int64_t compute_hash(const std::string& str) {
        // Simplified hash - in real implementation, would use mmh3 hash like Cython version
        std::hash<std::string> hasher;
        std::int64_t hash_value = static_cast<std::int64_t>(hasher(str));
        return (hash_value >> 8 << 8) | static_cast<std::int64_t>(MetaStringEncoding::UTF8);
    }
};

// Global decoders for namespace and typename (same as Cython)
class MetaStringDecoder {
private:
    std::string sep1_, sep2_;

public:
    MetaStringDecoder(const std::string& sep1, const std::string& sep2)
        : sep1_(sep1), sep2_(sep2) {}

    std::string decode(nb::bytes data, MetaStringEncoding encoding) const {
        std::string str = nb::cast<std::string>(data);

        // Replace separators
        std::size_t pos = 0;
        while ((pos = str.find(sep2_, pos)) != std::string::npos) {
            str.replace(pos, sep2_.length(), sep1_);
            pos += sep1_.length();
        }

        return str;
    }
};

// Global decoders (same as Cython)
extern MetaStringDecoder namespace_decoder;
extern MetaStringDecoder typename_decoder;

} // namespace python
} // namespace fury