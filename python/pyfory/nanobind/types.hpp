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

#include <cstdint>
#include <type_traits>
#include <string>

namespace fury {
namespace python {

// Core type system for optimized dispatch
enum class TypeId : std::uint32_t {
    // Primitive types
    BOOL = 1,
    INT8 = 2,
    INT16 = 3,
    INT32 = 4,
    INT64 = 5,
    VAR_INT32 = 6,
    VAR_INT64 = 7,
    FLOAT32 = 8,
    FLOAT64 = 9,
    STRING = 10,

    // Collection types
    LIST = 20,
    TUPLE = 21,
    SET = 22,
    MAP = 23,

    // Special types
    NULL_TYPE = 0,
    OBJECT = 100,

    // Reference flags
    NULL_FLAG = -3,
    REF_FLAG = -2,
    NOT_NULL_VALUE_FLAG = -1,
    REF_VALUE_FLAG = 0,
};

// Language enum
enum class Language : std::uint8_t {
    PYTHON = 0,
    XLANG = 1,
};

// Constants
constexpr std::int16_t MAGIC_NUMBER = 0x62d4;
constexpr std::int32_t NOT_NULL_INT64_FLAG = 0x00000001;
constexpr std::int32_t NOT_NULL_FLOAT64_FLAG = 0x00000002;
constexpr std::int32_t NOT_NULL_BOOL_FLAG = 0x00000003;
constexpr std::int32_t NOT_NULL_STRING_FLAG = 0x00000004;
constexpr std::int32_t SMALL_STRING_THRESHOLD = 16;

// Type traits for compile-time type detection
template<typename T>
struct TypeTraits {
    static constexpr TypeId type_id = TypeId::OBJECT;
    static constexpr bool is_primitive = false;
    static constexpr bool need_ref_tracking = true;
};

// Specialized type traits for primitives
template<> struct TypeTraits<bool> {
    static constexpr TypeId type_id = TypeId::BOOL;
    static constexpr bool is_primitive = true;
    static constexpr bool need_ref_tracking = false;
};

template<> struct TypeTraits<std::int8_t> {
    static constexpr TypeId type_id = TypeId::INT8;
    static constexpr bool is_primitive = true;
    static constexpr bool need_ref_tracking = false;
};

template<> struct TypeTraits<std::int16_t> {
    static constexpr TypeId type_id = TypeId::INT16;
    static constexpr bool is_primitive = true;
    static constexpr bool need_ref_tracking = false;
};

template<> struct TypeTraits<std::int32_t> {
    static constexpr TypeId type_id = TypeId::INT32;
    static constexpr bool is_primitive = true;
    static constexpr bool need_ref_tracking = false;
};

template<> struct TypeTraits<std::int64_t> {
    static constexpr TypeId type_id = TypeId::INT64;
    static constexpr bool is_primitive = true;
    static constexpr bool need_ref_tracking = false;
};

template<> struct TypeTraits<float> {
    static constexpr TypeId type_id = TypeId::FLOAT32;
    static constexpr bool is_primitive = true;
    static constexpr bool need_ref_tracking = false;
};

template<> struct TypeTraits<double> {
    static constexpr TypeId type_id = TypeId::FLOAT64;
    static constexpr bool is_primitive = true;
    static constexpr bool need_ref_tracking = false;
};

template<> struct TypeTraits<std::string> {
    static constexpr TypeId type_id = TypeId::STRING;
    static constexpr bool is_primitive = false;
    static constexpr bool need_ref_tracking = false;  // Usually not tracked
};

// Helper templates for type checking
template<typename T>
constexpr bool is_primitive_v = TypeTraits<T>::is_primitive;

template<typename T>
constexpr bool need_ref_tracking_v = TypeTraits<T>::need_ref_tracking;

template<typename T>
constexpr TypeId type_id_v = TypeTraits<T>::type_id;

// Runtime type ID detection for Python objects
inline TypeId get_python_type_id(const nanobind::object& obj) {
    if (obj.is_none()) return TypeId::NULL_TYPE;
    if (nanobind::isinstance<nanobind::bool_>(obj)) return TypeId::BOOL;
    if (nanobind::isinstance<nanobind::int_>(obj)) return TypeId::VAR_INT64;
    if (nanobind::isinstance<nanobind::float_>(obj)) return TypeId::FLOAT64;
    if (nanobind::isinstance<nanobind::str>(obj)) return TypeId::STRING;
    if (nanobind::isinstance<nanobind::list>(obj)) return TypeId::LIST;
    if (nanobind::isinstance<nanobind::tuple>(obj)) return TypeId::TUPLE;
    if (nanobind::isinstance<nanobind::set>(obj)) return TypeId::SET;
    if (nanobind::isinstance<nanobind::dict>(obj)) return TypeId::MAP;
    return TypeId::OBJECT;
}

// Type checking utilities
template<TypeId ID>
constexpr bool is_primitive_type() {
    return ID == TypeId::BOOL || ID == TypeId::INT8 || ID == TypeId::INT16 ||
           ID == TypeId::INT32 || ID == TypeId::INT64 || ID == TypeId::VAR_INT32 ||
           ID == TypeId::VAR_INT64 || ID == TypeId::FLOAT32 || ID == TypeId::FLOAT64;
}

template<TypeId ID>
constexpr bool is_collection_type() {
    return ID == TypeId::LIST || ID == TypeId::TUPLE || ID == TypeId::SET || ID == TypeId::MAP;
}

constexpr bool is_namespaced_type(std::uint32_t type_id) {
    return (type_id & 0xFF) >= 32;
}

constexpr bool is_type_share_meta(std::uint32_t type_id) {
    return type_id >= 256;
}

} // namespace python
} // namespace fury