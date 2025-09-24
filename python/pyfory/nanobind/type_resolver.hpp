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
#include <memory>
#include <cstdint>
#include <utility>
#include "types.hpp"
#include "py_buffer.hpp"
#include "metastring_resolver.hpp"
#include "serializer.hpp"
#include "exceptions.hpp"

namespace fury {
namespace python {

namespace nb = nanobind;

// Forward declarations
class Fury;
class SerializationContext;

constexpr std::int8_t USE_TYPE_NAME = 0;
constexpr std::int8_t USE_TYPE_ID = 1;
constexpr std::int8_t NO_TYPE_ID = 0;

// TypeInfo class - exact translation from Cython
class TypeInfo {
public:
    nb::object cls;                                    // Python class object
    std::int16_t type_id;                             // Type ID
    std::shared_ptr<Serializer> serializer;          // Serializer instance
    std::shared_ptr<MetaStringBytes> namespace_bytes;  // Namespace meta string
    std::shared_ptr<MetaStringBytes> typename_bytes;   // Type name meta string
    bool dynamic_type;                                 // Is dynamic type
    nb::object type_def;                              // Type definition

    TypeInfo(nb::object cls_obj = nb::none(),
             std::int16_t id = NO_TYPE_ID,
             std::shared_ptr<Serializer> ser = nullptr,
             std::shared_ptr<MetaStringBytes> ns_bytes = nullptr,
             std::shared_ptr<MetaStringBytes> tn_bytes = nullptr,
             bool dyn_type = false,
             nb::object def_obj = nb::none())
        : cls(cls_obj), type_id(id), serializer(ser),
          namespace_bytes(ns_bytes), typename_bytes(tn_bytes),
          dynamic_type(dyn_type), type_def(def_obj) {}

    std::string decode_namespace() const {
        if (!namespace_bytes) {
            return "";
        }
        return namespace_decoder.decode(namespace_bytes->data, static_cast<MetaStringEncoding>(namespace_bytes->encoding));
    }

    std::string decode_typename() const {
        if (!typename_bytes) {
            return "";
        }
        return typename_decoder.decode(typename_bytes->data, static_cast<MetaStringEncoding>(typename_bytes->encoding));
    }

    std::string repr() const {
        return "TypeInfo(cls=" + nb::cast<std::string>(nb::repr(cls)) +
               ", type_id=" + std::to_string(type_id) +
               ", serializer=" + (serializer ? "present" : "null") + ")";
    }
};

// TypeResolver - exact translation of Cython implementation with performance optimizations
class TypeResolver {
private:
    // Memory-safe data structures using smart pointers
    std::vector<std::shared_ptr<TypeInfo>> registered_id_to_type_info_;                           // _c_registered_id_to_type_info
    absl::flat_hash_map<std::uint64_t, std::shared_ptr<TypeInfo>> types_info_;                   // _c_types_info: cls -> TypeInfo
    absl::flat_hash_map<std::pair<std::int64_t, std::int64_t>, std::shared_ptr<TypeInfo>> meta_hash_to_typeinfo_;  // _c_meta_hash_to_typeinfo

    // References to other components
    Fury* fury_;
    MetaStringResolver* metastring_resolver_;
    bool meta_share_;
    SerializationContext* serialization_context_;

    // Python resolver integration for non-performance-critical operations
    nb::object python_resolver_;  // Python _registry.TypeResolver instance
    bool python_resolver_enabled_;

public:
    explicit TypeResolver(Fury* fury, bool meta_share = false)
        : fury_(fury), meta_share_(meta_share), serialization_context_(nullptr), python_resolver_enabled_(false) {
        // Python resolver will be injected later via set_python_resolver
        python_resolver_ = nb::none();
    }

    ~TypeResolver() {
        // Smart pointers handle cleanup automatically
        reset_write();
        reset_read();
    }

    void initialize(MetaStringResolver* resolver, SerializationContext* context) {
        metastring_resolver_ = resolver;
        serialization_context_ = context;

        // Initialize with built-in types
        register_builtin_types();
    }

    // Integration with Python registry
    void set_python_resolver(nb::object resolver) {
        python_resolver_ = resolver;
        python_resolver_enabled_ = !resolver.is_none();
    }

    bool has_python_resolver() const {
        return python_resolver_enabled_ && !python_resolver_.is_none();
    }

    // Register type - delegates to Python resolver for type resolution, caches for performance
    void register_type(nb::handle cls,
                      std::int32_t type_id = -1,
                      const std::string& name_space = "",
                      const std::string& typename_str = "",
                      std::shared_ptr<Serializer> serializer = nullptr) {

        // Create TypeInfo using smart pointer
        auto typeinfo = std::make_shared<TypeInfo>();
        typeinfo->cls = nb::cast<nb::object>(cls);
        typeinfo->type_id = type_id;
        typeinfo->serializer = serializer;
        typeinfo->dynamic_type = false;

        // Handle namespace and typename
        if (!name_space.empty() || !typename_str.empty()) {
            if (metastring_resolver_ != nullptr) {
                if (!name_space.empty()) {
                    typeinfo->namespace_bytes = metastring_resolver_->get_metastr_bytes(nb::cast(name_space));
                }
                if (!typename_str.empty()) {
                    typeinfo->typename_bytes = metastring_resolver_->get_metastr_bytes(nb::cast(typename_str));
                }
            }
        }

        populate_typeinfo(typeinfo);
    }

    void register_serializer(nb::handle cls, std::shared_ptr<Serializer> serializer) {
        auto typeinfo1 = get_typeinfo(cls, false);
        std::int16_t old_type_id = typeinfo1 ? typeinfo1->type_id : -1;

        // Update serializer
        if (typeinfo1) {
            typeinfo1->serializer = serializer;
        }

        auto typeinfo2 = get_typeinfo(cls);
        if (typeinfo1 && typeinfo2 && typeinfo1->type_id != typeinfo2->type_id) {
            // Type ID changed, update registration
            if (old_type_id > 0 && old_type_id < registered_id_to_type_info_.size()) {
                registered_id_to_type_info_[old_type_id] = nullptr;
            }
            populate_typeinfo(typeinfo2);
        }
    }

    // Fast type info lookup using same caching pattern as Cython
    inline std::shared_ptr<Serializer> get_serializer(nb::handle cls) {
        auto typeinfo = get_typeinfo(cls);
        return typeinfo ? typeinfo->serializer : nullptr;
    }

    // Exact translation of get_typeinfo from Cython with performance optimizations
    inline std::shared_ptr<TypeInfo> get_typeinfo(nb::handle cls, bool create = true) {
        // Use same caching pattern as Cython: type address as hash key
        std::uint64_t type_addr = reinterpret_cast<std::uintptr_t>(cls.ptr());

        auto it = types_info_.find(type_addr);
        if (it != types_info_.end()) {
            auto typeinfo = it->second;
            if (typeinfo->serializer != nullptr) {
                return typeinfo;
            } else {
                // Lazy serializer creation - delegate to Python resolver
                if (has_python_resolver()) {
                    exceptions::safe_python_call([&]() {
                        auto py_typeinfo = python_resolver_.attr("get_typeinfo")(cls, create);
                        if (!py_typeinfo.is_none()) {
                            // Extract serializer from Python TypeInfo
                            auto py_serializer = py_typeinfo.attr("serializer");
                            if (!py_serializer.is_none()) {
                                typeinfo->serializer = create_python_serializer_wrapper(py_serializer);
                            }
                        }
                        return nb::none();  // Return value not used
                    }, "lazy serializer creation");
                }
                return typeinfo;
            }
        } else if (!create) {
            return nullptr;
        } else {
            // Create new TypeInfo - try built-ins first, then delegate to Python resolver
            auto typeinfo = create_builtin_typeinfo(cls);
            if (typeinfo) {
                types_info_[type_addr] = typeinfo;
                populate_typeinfo(typeinfo);
                return typeinfo;
            }

            // Fall back to Python resolver for complex types
            if (has_python_resolver()) {
                exceptions::safe_python_call([&]() {
                    auto py_typeinfo = python_resolver_.attr("get_typeinfo")(cls, create);
                    if (!py_typeinfo.is_none()) {
                        typeinfo = create_typeinfo_from_python(py_typeinfo, cls);
                        if (typeinfo) {
                            types_info_[type_addr] = typeinfo;
                            populate_typeinfo(typeinfo);
                        }
                    }
                    return nb::none();  // Return value not used
                }, "type creation via Python resolver");

                if (typeinfo) {
                    return typeinfo;
                }
            }

            return nullptr;
        }
    }

    // Type registration queries - delegate to Python resolver
    bool is_registered_by_name(nb::handle cls) const {
        if (has_python_resolver()) {
            try {
                return exceptions::safe_python_call([&]() {
                    auto py_typeinfo = python_resolver_.attr("get_typeinfo")(cls, false);
                    return !py_typeinfo.is_none();
                }, "type registration check");
            } catch (const PythonIntegrationException&) {
                // Python call failed, type is not registered
                return false;
            }
        }
        return false;
    }

    bool is_registered_by_id(nb::handle cls) const {
        auto typeinfo = const_cast<TypeResolver*>(this)->get_typeinfo(cls, false);
        return typeinfo && typeinfo->type_id > 0;
    }

    std::string get_registered_name(nb::handle cls) const {
        if (has_python_resolver()) {
            try {
                return exceptions::safe_python_call([&]() {
                    auto py_typeinfo = python_resolver_.attr("get_typeinfo")(cls, false);
                    if (!py_typeinfo.is_none()) {
                        auto decode_typename = py_typeinfo.attr("decode_typename");
                        return nb::cast<std::string>(decode_typename());
                    }
                    return std::string("");
                }, "type name retrieval");
            } catch (const PythonIntegrationException&) {
                // Python call failed, return empty string
                return "";
            }
        }
        return "";
    }

    std::int32_t get_registered_id(nb::handle cls) const {
        auto typeinfo = const_cast<TypeResolver*>(this)->get_typeinfo(cls, false);
        return typeinfo ? typeinfo->type_id : -1;
    }

    // Type info serialization
    void write_typeinfo(PyBuffer& buffer, std::shared_ptr<TypeInfo> typeinfo) {
        if (typeinfo->dynamic_type) {
            return;
        }

        std::int32_t type_id = typeinfo->type_id;
        std::int32_t internal_type_id = type_id & 0xFF;

        if (meta_share_) {
            write_shared_type_meta(buffer, typeinfo);
            return;
        }

        buffer.write_varuint32(static_cast<std::uint32_t>(type_id));
        if (is_namespaced_type(internal_type_id)) {
            if (metastring_resolver_ && typeinfo->namespace_bytes) {
                metastring_resolver_->write_meta_string_bytes(buffer, typeinfo->namespace_bytes);
            }
            if (metastring_resolver_ && typeinfo->typename_bytes) {
                metastring_resolver_->write_meta_string_bytes(buffer, typeinfo->typename_bytes);
            }
        }
    }

    inline std::shared_ptr<TypeInfo> read_typeinfo(PyBuffer& buffer) {
        if (meta_share_) {
            return read_shared_type_meta(buffer);
        }

        std::int32_t type_id = static_cast<std::int32_t>(buffer.read_varuint32());
        if (type_id < 0) {
            type_id = -type_id;
        }

        if (type_id >= registered_id_to_type_info_.size()) {
            throw TypeNotFoundException("Type ID " + std::to_string(type_id) + " not registered (max: " +
                                      std::to_string(registered_id_to_type_info_.size() - 1) + ")");
        }

        std::int32_t internal_type_id = type_id & 0xFF;

        if (is_namespaced_type(internal_type_id)) {
            auto namespace_bytes = metastring_resolver_->read_meta_string_bytes(buffer);
            auto typename_bytes = metastring_resolver_->read_meta_string_bytes(buffer);
            return load_bytes_to_typeinfo(type_id, namespace_bytes, typename_bytes);
        }

        if (type_id < registered_id_to_type_info_.size()) {
            auto typeinfo = registered_id_to_type_info_[type_id];
            if (typeinfo != nullptr) {
                return typeinfo;
            }
        }

        throw TypeNotFoundException("Type ID " + std::to_string(type_id) + " could not be resolved");
    }

    void reset_write() {
        // Reset any write-specific state
    }

    void reset_read() {
        // Reset any read-specific state
    }

private:
    // Exact translation of _populate_typeinfo from Cython
    void populate_typeinfo(std::shared_ptr<TypeInfo> typeinfo) {
        std::int32_t type_id = typeinfo->type_id;
        if (type_id >= 0) {
            // Resize vector if necessary
            if (type_id >= registered_id_to_type_info_.size()) {
                registered_id_to_type_info_.resize(type_id * 2, nullptr);
            }

            // Register by type ID if valid
            if (type_id > 0) {
                registered_id_to_type_info_[type_id] = typeinfo;
            }
        }

        // Register by type class address (same pattern as Cython)
        std::uint64_t type_addr = reinterpret_cast<std::uintptr_t>(typeinfo->cls.ptr());
        types_info_[type_addr] = typeinfo;

        // Load type metadata if available
        if (typeinfo->typename_bytes != nullptr) {
            load_bytes_to_typeinfo(type_id, typeinfo->namespace_bytes, typeinfo->typename_bytes);
        }
    }

    // Exact translation of _load_bytes_to_typeinfo from Cython
    std::shared_ptr<TypeInfo> load_bytes_to_typeinfo(std::int32_t type_id,
                                                     std::shared_ptr<MetaStringBytes> ns_metabytes,
                                                     std::shared_ptr<MetaStringBytes> type_metabytes) {
        auto key = std::make_pair(ns_metabytes ? ns_metabytes->hashcode : 0,
                                 type_metabytes ? type_metabytes->hashcode : 0);
        auto it = meta_hash_to_typeinfo_.find(key);
        if (it != meta_hash_to_typeinfo_.end()) {
            return it->second;
        }

        // Create new TypeInfo from metadata (would delegate to Python resolver in real implementation)
        auto typeinfo = std::make_shared<TypeInfo>();
        typeinfo->type_id = type_id;
        typeinfo->namespace_bytes = ns_metabytes;
        typeinfo->typename_bytes = type_metabytes;

        meta_hash_to_typeinfo_[key] = typeinfo;
        return typeinfo;
    }

    void register_builtin_types() {
        // Register built-in primitive types
        register_builtin_type<bool>(TypeId::BOOL);
        register_builtin_type<std::int8_t>(TypeId::INT8);
        register_builtin_type<std::int16_t>(TypeId::INT16);
        register_builtin_type<std::int32_t>(TypeId::INT32);
        register_builtin_type<std::int64_t>(TypeId::INT64);
        register_builtin_type<float>(TypeId::FLOAT32);
        register_builtin_type<double>(TypeId::FLOAT64);
        // String is handled specially
    }

    template<typename T>
    void register_builtin_type(TypeId type_id) {
        nb::object cls = nb::type<T>();
        auto serializer = create_primitive_serializer<T>();
        register_type(cls, static_cast<std::int32_t>(type_id), "", "", serializer);
    }

    std::shared_ptr<TypeInfo> create_builtin_typeinfo(nb::handle cls) {
        // Fast path for built-in types
        if (nb::isinstance<nb::bool_>(cls)) {
            return create_typeinfo_for_type<bool>(TypeId::BOOL);
        } else if (nb::isinstance<nb::int_>(cls)) {
            return create_typeinfo_for_type<std::int64_t>(TypeId::VAR_INT64);
        } else if (nb::isinstance<nb::float_>(cls)) {
            return create_typeinfo_for_type<double>(TypeId::FLOAT64);
        } else if (nb::isinstance<nb::str>(cls)) {
            return create_typeinfo_for_type<std::string>(TypeId::STRING);
        }
        return nullptr;
    }

    template<typename T>
    std::shared_ptr<TypeInfo> create_typeinfo_for_type(TypeId type_id) {
        auto typeinfo = std::make_shared<TypeInfo>();
        typeinfo->cls = nb::type<T>();
        typeinfo->type_id = static_cast<std::int16_t>(type_id);
        typeinfo->serializer = create_primitive_serializer<T>();
        typeinfo->dynamic_type = false;
        return typeinfo;
    }

    // Placeholder methods for shared metadata (would be implemented for full compatibility)
    void write_shared_type_meta(PyBuffer& buffer, std::shared_ptr<TypeInfo> typeinfo) {
        // Placeholder - would implement shared metadata protocol
        write_typeinfo(buffer, typeinfo);  // Fallback to regular protocol
    }

    std::shared_ptr<TypeInfo> read_shared_type_meta(PyBuffer& buffer) {
        // Placeholder - would implement shared metadata protocol
        return read_typeinfo(buffer);  // Fallback to regular protocol
    }

    // Python integration helper methods
private:
    // Create TypeInfo from Python TypeInfo object
    std::shared_ptr<TypeInfo> create_typeinfo_from_python(nb::object py_typeinfo, nb::handle cls) {
        auto typeinfo = std::make_shared<TypeInfo>();
        typeinfo->cls = nb::cast<nb::object>(cls);

        // Extract type_id
        auto py_type_id = py_typeinfo.attr("type_id");
        if (!py_type_id.is_none()) {
            typeinfo->type_id = nb::cast<std::int16_t>(py_type_id);
        }

        // Extract serializer
        auto py_serializer = py_typeinfo.attr("serializer");
        if (!py_serializer.is_none()) {
            typeinfo->serializer = create_python_serializer_wrapper(py_serializer);
        }

        // Extract namespace and typename bytes if available
        auto py_namespace_bytes = py_typeinfo.attr("namespace_bytes");
        if (!py_namespace_bytes.is_none()) {
            // TODO: Convert Python MetaStringBytes to C++ MetaStringBytes
        }

        auto py_typename_bytes = py_typeinfo.attr("typename_bytes");
        if (!py_typename_bytes.is_none()) {
            // TODO: Convert Python MetaStringBytes to C++ MetaStringBytes
        }

        return typeinfo;
    }

    // Create a C++ serializer wrapper for Python serializer
    std::shared_ptr<Serializer> create_python_serializer_wrapper(nb::object py_serializer) {
        // For now, create a placeholder serializer that delegates to Python
        // In full implementation, this would create a C++ wrapper that calls Python serializer
        return std::make_shared<PythonSerializerWrapper>(py_serializer);
    }
};

// Global MetaString decoders (same as Cython)
inline MetaStringDecoder namespace_decoder(".", "_");
inline MetaStringDecoder typename_decoder("$", "_");

} // namespace python
} // namespace fury