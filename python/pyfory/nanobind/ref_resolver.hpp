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
#include "types.hpp"
#include "py_buffer.hpp"

namespace fury {
namespace python {

namespace nb = nanobind;

// Reference flags - exact same values as Cython implementation
constexpr std::int8_t NULL_FLAG = -3;
constexpr std::int8_t REF_FLAG = -2;
constexpr std::int8_t NOT_NULL_VALUE_FLAG = -1;
constexpr std::int8_t REF_VALUE_FLAG = 0;

// MapRefResolver - exact translation of Cython implementation
// This class copies the exact algorithm and data structures from _serialization.pyx
// for maximum compatibility and performance
class MapRefResolver {
private:
    // Exact same data structures as Cython
    absl::flat_hash_map<std::uint64_t, std::int32_t> written_objects_id_;  // id(obj) -> ref_id
    std::vector<PyObject*> written_objects_;     // Hold object references to prevent GC
    std::vector<PyObject*> read_objects_;        // Read objects storage
    std::vector<std::int32_t> read_ref_ids_;    // Reference IDs for reading
    PyObject* read_object_;                      // Current read object
    bool ref_tracking_;                          // Reference tracking enabled

public:
    explicit MapRefResolver(bool ref_tracking)
        : ref_tracking_(ref_tracking), read_object_(nullptr) {}

    ~MapRefResolver() {
        reset();
    }

    // Exact translation of write_ref_or_null from Cython
    inline bool write_ref_or_null(PyBuffer& buffer, nb::handle obj) {
        if (!ref_tracking_) {
            if (obj.is_none()) {
                buffer.write_int8(NULL_FLAG);
                return true;
            } else {
                buffer.write_int8(NOT_NULL_VALUE_FLAG);
                return false;
            }
        }

        if (obj.is_none()) {
            buffer.write_int8(NULL_FLAG);
            return true;
        }

        // Use object identity (memory address) as key - same as Cython id(obj)
        std::uint64_t object_id = reinterpret_cast<std::uintptr_t>(obj.ptr());

        auto it = written_objects_id_.find(object_id);
        if (it == written_objects_id_.end()) {
            // First time seeing this object
            std::int32_t next_id = static_cast<std::int32_t>(written_objects_id_.size());
            written_objects_id_[object_id] = next_id;

            // Store reference to prevent garbage collection
            written_objects_.push_back(obj.ptr());
            Py_INCREF(obj.ptr());

            buffer.write_int8(REF_VALUE_FLAG);
            return false;
        } else {
            // Object has been written previously - write reference
            buffer.write_int8(REF_FLAG);
            buffer.write_varuint32(static_cast<std::uint32_t>(it->second));
            return true;
        }
    }

    // Exact translation of read_ref_or_null from Cython
    inline std::int8_t read_ref_or_null(PyBuffer& buffer) {
        std::int8_t head_flag = buffer.read_int8();
        if (!ref_tracking_) {
            return head_flag;
        }

        if (head_flag == REF_FLAG) {
            // Read reference ID and get object from reference resolver
            std::int32_t ref_id = buffer.read_varint32();
            read_object_ = read_objects_[ref_id];
            return REF_FLAG;
        } else {
            read_object_ = nullptr;
            return head_flag;
        }
    }

    // Exact translation of preserve_ref_id from Cython
    inline std::int32_t preserve_ref_id() {
        if (!ref_tracking_) {
            return -1;
        }
        std::int32_t next_read_ref_id = static_cast<std::int32_t>(read_objects_.size());
        read_objects_.push_back(nullptr);
        read_ref_ids_.push_back(next_read_ref_id);
        return next_read_ref_id;
    }

    // Exact translation of try_preserve_ref_id from Cython
    inline std::int32_t try_preserve_ref_id(PyBuffer& buffer) {
        if (!ref_tracking_) {
            // NOT_NULL_VALUE_FLAG can be used as stub reference id
            return buffer.read_int8();
        }

        std::int8_t head_flag = buffer.read_int8();
        if (head_flag == REF_FLAG) {
            // Read reference ID and get object from reference resolver
            std::int32_t ref_id = buffer.read_varint32();
            read_object_ = read_objects_[ref_id];
            return head_flag;
        } else {
            read_object_ = nullptr;
            if (head_flag == REF_VALUE_FLAG) {
                return preserve_ref_id();
            }
            return head_flag;
        }
    }

    // Exact translation of reference method from Cython
    inline void reference(nb::handle obj) {
        if (!ref_tracking_) {
            return;
        }
        std::int32_t ref_id = read_ref_ids_.back();
        read_ref_ids_.pop_back();

        bool need_inc = (read_objects_[ref_id] == nullptr);
        if (need_inc) {
            Py_INCREF(obj.ptr());
        }
        read_objects_[ref_id] = obj.ptr();
    }

    // Exact translation of get_read_object from Cython
    inline nb::object get_read_object(std::int32_t ref_id = -1) const {
        if (!ref_tracking_) {
            return nb::none();
        }

        if (ref_id == -1) {
            return read_object_ ? nb::borrow(read_object_) : nb::none();
        } else {
            return read_objects_[ref_id] ? nb::borrow(read_objects_[ref_id]) : nb::none();
        }
    }

    // Exact translation of set_read_object from Cython
    inline void set_read_object(std::int32_t ref_id, nb::handle obj) {
        if (!ref_tracking_) {
            return;
        }

        if (ref_id >= 0) {
            bool need_inc = (read_objects_[ref_id] == nullptr);
            if (need_inc) {
                Py_INCREF(obj.ptr());
            }
            read_objects_[ref_id] = obj.ptr();
        }
    }

    // Exact translation of reset methods from Cython
    inline void reset() {
        reset_write();
        reset_read();
    }

    inline void reset_write() {
        written_objects_id_.clear();
        for (PyObject* item : written_objects_) {
            Py_XDECREF(item);
        }
        written_objects_.clear();
    }

    inline void reset_read() {
        if (!ref_tracking_) {
            return;
        }
        for (PyObject* item : read_objects_) {
            Py_XDECREF(item);
        }
        read_objects_.clear();
        read_ref_ids_.clear();
        read_object_ = nullptr;
    }

    // Accessors
    inline bool is_ref_tracking_enabled() const { return ref_tracking_; }
};

// Utility functions for nullable primitive writing - exact translation from Cython
inline void write_nullable_bool(BufferWrapper& buffer, nb::handle value) {
    if (value.is_none()) {
        buffer.write_int8(NULL_FLAG);
    } else {
        buffer.write_int8(NOT_NULL_VALUE_FLAG);
        buffer.write_bool(nb::cast<bool>(value));
    }
}

inline void write_nullable_int64(BufferWrapper& buffer, nb::handle value) {
    if (value.is_none()) {
        buffer.write_int8(NULL_FLAG);
    } else {
        buffer.write_int8(NOT_NULL_VALUE_FLAG);
        buffer.write_varint64(nb::cast<std::int64_t>(value));
    }
}

inline void write_nullable_float64(BufferWrapper& buffer, nb::handle value) {
    if (value.is_none()) {
        buffer.write_int8(NULL_FLAG);
    } else {
        buffer.write_int8(NOT_NULL_VALUE_FLAG);
        buffer.write_double(nb::cast<double>(value));
    }
}

inline void write_nullable_string(BufferWrapper& buffer, nb::handle value) {
    if (value.is_none()) {
        buffer.write_int8(NULL_FLAG);
    } else {
        buffer.write_int8(NOT_NULL_VALUE_FLAG);
        buffer.write_string(nb::cast<std::string>(value));
    }
}

// Utility functions for nullable primitive reading - exact translation from Cython
inline nb::object read_nullable_bool(BufferWrapper& buffer) {
    if (buffer.read_int8() == NOT_NULL_VALUE_FLAG) {
        return nb::cast(buffer.read_bool());
    } else {
        return nb::none();
    }
}

inline nb::object read_nullable_int64(BufferWrapper& buffer) {
    if (buffer.read_int8() == NOT_NULL_VALUE_FLAG) {
        return nb::cast(buffer.read_varint64());
    } else {
        return nb::none();
    }
}

inline nb::object read_nullable_float64(BufferWrapper& buffer) {
    if (buffer.read_int8() == NOT_NULL_VALUE_FLAG) {
        return nb::cast(buffer.read_double());
    } else {
        return nb::none();
    }
}

inline nb::object read_nullable_string(BufferWrapper& buffer) {
    if (buffer.read_int8() == NOT_NULL_VALUE_FLAG) {
        return nb::cast(buffer.read_string());
    } else {
        return nb::none();
    }
}

} // namespace python
} // namespace fury