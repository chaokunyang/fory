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

#include <stdexcept>
#include <string>
#include <nanobind/nanobind.h>

namespace fury {
namespace python {

namespace nb = nanobind;

// Base exception for all Fury-specific errors
class FuryException : public std::runtime_error {
public:
    explicit FuryException(const std::string& message)
        : std::runtime_error("Fury Error: " + message) {}
};

// Serialization-specific exceptions
class SerializationException : public FuryException {
public:
    explicit SerializationException(const std::string& message)
        : FuryException("Serialization failed: " + message) {}
};

class DeserializationException : public FuryException {
public:
    explicit DeserializationException(const std::string& message)
        : FuryException("Deserialization failed: " + message) {}
};

// Type resolution exceptions
class TypeNotFoundException : public FuryException {
public:
    explicit TypeNotFoundException(const std::string& message)
        : FuryException("Type not found: " + message) {}
};

class TypeRegistrationException : public FuryException {
public:
    explicit TypeRegistrationException(const std::string& message)
        : FuryException("Type registration failed: " + message) {}
};

// Buffer operation exceptions
class BufferException : public FuryException {
public:
    explicit BufferException(const std::string& message)
        : FuryException("Buffer operation failed: " + message) {}
};

class BufferUnderflowException : public BufferException {
public:
    explicit BufferUnderflowException(const std::string& message)
        : BufferException("Buffer underflow: " + message) {}
};

class BufferOverflowException : public BufferException {
public:
    explicit BufferOverflowException(const std::string& message)
        : BufferException("Buffer overflow: " + message) {}
};

// Reference tracking exceptions
class ReferenceException : public FuryException {
public:
    explicit ReferenceException(const std::string& message)
        : FuryException("Reference tracking error: " + message) {}
};

// MetaString exceptions
class MetaStringException : public FuryException {
public:
    explicit MetaStringException(const std::string& message)
        : FuryException("MetaString operation failed: " + message) {}
};

// Python integration exceptions
class PythonIntegrationException : public FuryException {
public:
    explicit PythonIntegrationException(const std::string& message)
        : FuryException("Python integration failed: " + message) {}
};

// Validation exceptions
class ValidationException : public FuryException {
public:
    explicit ValidationException(const std::string& message)
        : FuryException("Validation failed: " + message) {}
};

// Exception handling utilities for nanobind integration
namespace exceptions {

// Convert C++ exceptions to appropriate Python exceptions
inline void register_exception_translators(nb::module_& m) {
    // Register custom exception translators for nanobind
    nb::register_exception_translator([](const std::exception_ptr& p, void*) {
        try {
            std::rethrow_exception(p);
        } catch (const BufferUnderflowException& e) {
            PyErr_SetString(PyExc_IndexError, e.what());
        } catch (const BufferOverflowException& e) {
            PyErr_SetString(PyExc_MemoryError, e.what());
        } catch (const BufferException& e) {
            PyErr_SetString(PyExc_RuntimeError, e.what());
        } catch (const TypeNotFoundException& e) {
            PyErr_SetString(PyExc_KeyError, e.what());
        } catch (const TypeRegistrationException& e) {
            PyErr_SetString(PyExc_ValueError, e.what());
        } catch (const SerializationException& e) {
            PyErr_SetString(PyExc_RuntimeError, e.what());
        } catch (const DeserializationException& e) {
            PyErr_SetString(PyExc_RuntimeError, e.what());
        } catch (const ValidationException& e) {
            PyErr_SetString(PyExc_ValueError, e.what());
        } catch (const PythonIntegrationException& e) {
            PyErr_SetString(PyExc_RuntimeError, e.what());
        } catch (const FuryException& e) {
            PyErr_SetString(PyExc_RuntimeError, e.what());
        }
    });
}

// Macro for safe nanobind function wrapping
#define NANOBIND_SAFE_CALL(func_body) \
    try { \
        func_body \
    } catch (const FuryException& e) { \
        throw; /* Let nanobind handle the translation */ \
    } catch (const std::exception& e) { \
        throw FuryException("Unexpected error: " + std::string(e.what())); \
    } catch (...) { \
        throw FuryException("Unknown error occurred"); \
    }

// Helper for validating parameters
inline void validate_not_null(const void* ptr, const std::string& param_name) {
    if (ptr == nullptr) {
        throw ValidationException(param_name + " cannot be null");
    }
}

// Helper for validating Python objects
inline void validate_python_object(nb::handle obj, const std::string& expected_type = "") {
    if (obj.is_none()) {
        throw ValidationException("Python object cannot be None" +
            (expected_type.empty() ? "" : " (expected " + expected_type + ")"));
    }
}

// Helper for validating buffer operations
inline void validate_buffer_bounds(std::uint32_t offset, std::uint32_t length, std::uint32_t buffer_size) {
    if (offset > buffer_size) {
        throw BufferOverflowException("Offset " + std::to_string(offset) +
                                     " exceeds buffer size " + std::to_string(buffer_size));
    }
    if (offset + length > buffer_size) {
        throw BufferOverflowException("Operation would exceed buffer bounds: offset=" +
                                     std::to_string(offset) + ", length=" + std::to_string(length) +
                                     ", buffer_size=" + std::to_string(buffer_size));
    }
}

// Helper for validating type IDs
inline void validate_type_id(std::int32_t type_id) {
    if (type_id < 0) {
        throw ValidationException("Invalid type ID: " + std::to_string(type_id));
    }
}

// Helper for Python call error handling
template<typename F>
auto safe_python_call(F&& func, const std::string& operation) -> decltype(func()) {
    try {
        return func();
    } catch (const nb::python_error& e) {
        throw PythonIntegrationException("Python call failed in " + operation + ": " + std::string(e.what()));
    } catch (const std::exception& e) {
        throw PythonIntegrationException("Error in " + operation + ": " + std::string(e.what()));
    }
}

} // namespace exceptions

} // namespace python
} // namespace fury