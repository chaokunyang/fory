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
#include <memory>
#include "types.hpp"
#include "buffer.hpp"

namespace fury {
namespace python {

namespace nb = nanobind;

// Abstract base class for all serializers - designed for extensibility
class Serializer {
public:
    virtual ~Serializer() = default;

    // Core serialization interface
    virtual void write(Buffer& buffer, nb::handle obj) = 0;
    virtual nb::object read(Buffer& buffer) = 0;

    // Cross-language serialization interface
    virtual void xwrite(Buffer& buffer, nb::handle obj) = 0;
    virtual nb::object xread(Buffer& buffer) = 0;

    // Serializer properties for optimization
    virtual bool need_ref_tracking() const = 0;
    virtual bool support_subclass() const { return false; }
    virtual TypeId type_id() const = 0;

    // Template method for type checking
    template<typename T>
    bool is_type() const {
        return type_id() == TypeTraits<T>::type_id;
    }
};

// Base class for cross-language compatible serializers
class CrossLanguageCompatibleSerializer : public Serializer {
public:
    // Default implementation: xwrite/xread delegate to write/read
    void xwrite(Buffer& buffer, nb::handle obj) override {
        write(buffer, obj);
    }

    nb::object xread(Buffer& buffer) override {
        return read(buffer);
    }
};

// Template base class for primitive serializers - reduces code duplication
template<typename T>
class PrimitiveSerializer : public CrossLanguageCompatibleSerializer {
public:
    bool need_ref_tracking() const override {
        return !TypeTraits<T>::is_primitive;
    }

    TypeId type_id() const override {
        return TypeTraits<T>::type_id;
    }

    // Template methods for derived classes to implement
    virtual void write_value(Buffer& buffer, const T& value) = 0;
    virtual T read_value(Buffer& buffer) = 0;

    // Final implementation using templates
    void write(Buffer& buffer, nb::handle obj) override {
        if constexpr (std::is_same_v<T, bool>) {
            write_value(buffer, nb::cast<bool>(obj));
        } else if constexpr (std::is_integral_v<T>) {
            write_value(buffer, nb::cast<T>(obj));
        } else if constexpr (std::is_floating_point_v<T>) {
            write_value(buffer, nb::cast<T>(obj));
        } else if constexpr (std::is_same_v<T, std::string>) {
            write_value(buffer, nb::cast<std::string>(obj));
        }
    }

    nb::object read(Buffer& buffer) override {
        T value = read_value(buffer);
        if constexpr (std::is_same_v<T, bool>) {
            return nb::cast(value);
        } else if constexpr (std::is_integral_v<T>) {
            return nb::cast(value);
        } else if constexpr (std::is_floating_point_v<T>) {
            return nb::cast(value);
        } else if constexpr (std::is_same_v<T, std::string>) {
            return nb::cast(value);
        }
    }
};

// Concrete primitive serializers using templates for code reuse
class BooleanSerializer : public PrimitiveSerializer<bool> {
public:
    void write_value(Buffer& buffer, const bool& value) override;
    bool read_value(Buffer& buffer) override;
};

class Int8Serializer : public PrimitiveSerializer<std::int8_t> {
public:
    void write_value(Buffer& buffer, const std::int8_t& value) override;
    std::int8_t read_value(Buffer& buffer) override;
};

class Int16Serializer : public PrimitiveSerializer<std::int16_t> {
public:
    void write_value(Buffer& buffer, const std::int16_t& value) override;
    std::int16_t read_value(Buffer& buffer) override;
};

class Int32Serializer : public PrimitiveSerializer<std::int32_t> {
public:
    void write_value(Buffer& buffer, const std::int32_t& value) override;
    std::int32_t read_value(Buffer& buffer) override;
};

class Int64Serializer : public PrimitiveSerializer<std::int64_t> {
public:
    void write_value(Buffer& buffer, const std::int64_t& value) override;
    std::int64_t read_value(Buffer& buffer) override;

    // Override for varint encoding
    void xwrite(Buffer& buffer, nb::handle obj) override;
    nb::object xread(Buffer& buffer) override;
};

class Float32Serializer : public PrimitiveSerializer<float> {
public:
    void write_value(Buffer& buffer, const float& value) override;
    float read_value(Buffer& buffer) override;
};

class Float64Serializer : public PrimitiveSerializer<double> {
public:
    void write_value(Buffer& buffer, const double& value) override;
    double read_value(Buffer& buffer) override;
};

class StringSerializer : public PrimitiveSerializer<std::string> {
private:
    bool track_ref_;

public:
    explicit StringSerializer(bool track_ref = false) : track_ref_(track_ref) {}

    bool need_ref_tracking() const override { return track_ref_; }

    void write_value(Buffer& buffer, const std::string& value) override;
    std::string read_value(Buffer& buffer) override;

    // Override for optimized Python string handling
    void write(Buffer& buffer, nb::handle obj) override;
    nb::object read(Buffer& buffer) override;
};

// Collection serializer base class
class CollectionSerializer : public Serializer {
protected:
    std::shared_ptr<Serializer> element_serializer_;
    TypeId element_type_id_;

public:
    explicit CollectionSerializer(std::shared_ptr<Serializer> elem_serializer = nullptr)
        : element_serializer_(elem_serializer) {
        if (elem_serializer) {
            element_type_id_ = elem_serializer->type_id();
        } else {
            element_type_id_ = TypeId::OBJECT;
        }
    }

    bool need_ref_tracking() const override { return true; }

protected:
    // Helper methods for collection serialization
    virtual void write_collection_header(Buffer& buffer, nb::handle collection) = 0;
    virtual nb::object create_collection(std::size_t size) = 0;
    virtual void add_element(nb::object collection, std::size_t index, nb::object element) = 0;
};

class ListSerializer : public CollectionSerializer {
public:
    using CollectionSerializer::CollectionSerializer;

    TypeId type_id() const override { return TypeId::LIST; }

    void write(Buffer& buffer, nb::handle obj) override;
    nb::object read(Buffer& buffer) override;
    void xwrite(Buffer& buffer, nb::handle obj) override;
    nb::object xread(Buffer& buffer) override;

protected:
    void write_collection_header(Buffer& buffer, nb::handle collection) override;
    nb::object create_collection(std::size_t size) override;
    void add_element(nb::object collection, std::size_t index, nb::object element) override;
};

class TupleSerializer : public CollectionSerializer {
public:
    using CollectionSerializer::CollectionSerializer;

    TypeId type_id() const override { return TypeId::TUPLE; }

    void write(Buffer& buffer, nb::handle obj) override;
    nb::object read(Buffer& buffer) override;
    void xwrite(Buffer& buffer, nb::handle obj) override;
    nb::object xread(Buffer& buffer) override;

protected:
    void write_collection_header(Buffer& buffer, nb::handle collection) override;
    nb::object create_collection(std::size_t size) override;
    void add_element(nb::object collection, std::size_t index, nb::object element) override;
};

class SetSerializer : public CollectionSerializer {
public:
    using CollectionSerializer::CollectionSerializer;

    TypeId type_id() const override { return TypeId::SET; }

    void write(Buffer& buffer, nb::handle obj) override;
    nb::object read(Buffer& buffer) override;
    void xwrite(Buffer& buffer, nb::handle obj) override;
    nb::object xread(Buffer& buffer) override;

protected:
    void write_collection_header(Buffer& buffer, nb::handle collection) override;
    nb::object create_collection(std::size_t size) override;
    void add_element(nb::object collection, std::size_t index, nb::object element) override;
};

// Map/Dict serializer
class MapSerializer : public Serializer {
protected:
    std::shared_ptr<Serializer> key_serializer_;
    std::shared_ptr<Serializer> value_serializer_;

public:
    MapSerializer(std::shared_ptr<Serializer> key_serializer = nullptr,
                  std::shared_ptr<Serializer> value_serializer = nullptr)
        : key_serializer_(key_serializer), value_serializer_(value_serializer) {}

    TypeId type_id() const override { return TypeId::MAP; }
    bool need_ref_tracking() const override { return true; }

    void write(Buffer& buffer, nb::handle obj) override;
    nb::object read(Buffer& buffer) override;
    void xwrite(Buffer& buffer, nb::handle obj) override;
    nb::object xread(Buffer& buffer) override;
};

// Factory functions for creating serializers
std::shared_ptr<Serializer> create_serializer(TypeId type_id);

template<typename T>
std::shared_ptr<Serializer> create_primitive_serializer() {
    if constexpr (std::is_same_v<T, bool>) {
        return std::make_shared<BooleanSerializer>();
    } else if constexpr (std::is_same_v<T, std::int8_t>) {
        return std::make_shared<Int8Serializer>();
    } else if constexpr (std::is_same_v<T, std::int16_t>) {
        return std::make_shared<Int16Serializer>();
    } else if constexpr (std::is_same_v<T, std::int32_t>) {
        return std::make_shared<Int32Serializer>();
    } else if constexpr (std::is_same_v<T, std::int64_t>) {
        return std::make_shared<Int64Serializer>();
    } else if constexpr (std::is_same_v<T, float>) {
        return std::make_shared<Float32Serializer>();
    } else if constexpr (std::is_same_v<T, double>) {
        return std::make_shared<Float64Serializer>();
    } else if constexpr (std::is_same_v<T, std::string>) {
        return std::make_shared<StringSerializer>();
    } else {
        static_assert(always_false_v<T>, "Unsupported type for primitive serializer");
    }
}

// Helper for static_assert
template<typename T>
constexpr bool always_false_v = false;

// Python serializer wrapper for C++/Python integration
class PythonSerializerWrapper : public Serializer {
private:
    nb::object python_serializer_;
    TypeId cached_type_id_;

public:
    explicit PythonSerializerWrapper(nb::object py_serializer)
        : python_serializer_(py_serializer), cached_type_id_(TypeId::OBJECT) {
        // Cache type_id if available
        try {
            if (nb::hasattr(python_serializer_, "type_id")) {
                auto py_type_id = python_serializer_.attr("type_id");
                if (!py_type_id.is_none()) {
                    cached_type_id_ = static_cast<TypeId>(nb::cast<std::int16_t>(py_type_id));
                }
            }
        } catch (...) {
            // Use default OBJECT type id
        }
    }

    TypeId type_id() const override { return cached_type_id_; }

    bool need_ref_tracking() const override {
        try {
            if (nb::hasattr(python_serializer_, "need_ref_tracking")) {
                return nb::cast<bool>(python_serializer_.attr("need_ref_tracking")());
            }
        } catch (...) {
            // Default to true for safety
        }
        return true;
    }

    bool support_subclass() const override {
        try {
            if (nb::hasattr(python_serializer_, "support_subclass")) {
                return nb::cast<bool>(python_serializer_.attr("support_subclass")());
            }
        } catch (...) {
            // Default to false
        }
        return false;
    }

    void write(Buffer& buffer, nb::handle obj) override {
        try {
            python_serializer_.attr("write")(buffer, obj);
        } catch (const std::exception& e) {
            throw std::runtime_error("Python serializer write failed: " + std::string(e.what()));
        }
    }

    nb::object read(Buffer& buffer) override {
        try {
            return python_serializer_.attr("read")(buffer);
        } catch (const std::exception& e) {
            throw std::runtime_error("Python serializer read failed: " + std::string(e.what()));
        }
    }

    void xwrite(Buffer& buffer, nb::handle obj) override {
        try {
            if (nb::hasattr(python_serializer_, "xwrite")) {
                python_serializer_.attr("xwrite")(buffer, obj);
            } else {
                // Fall back to regular write
                write(buffer, obj);
            }
        } catch (const std::exception& e) {
            throw std::runtime_error("Python serializer xwrite failed: " + std::string(e.what()));
        }
    }

    nb::object xread(Buffer& buffer) override {
        try {
            if (nb::hasattr(python_serializer_, "xread")) {
                return python_serializer_.attr("xread")(buffer);
            } else {
                // Fall back to regular read
                return read(buffer);
            }
        } catch (const std::exception& e) {
            throw std::runtime_error("Python serializer xread failed: " + std::string(e.what()));
        }
    }
};

} // namespace python
} // namespace fury