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

#include <Python.h>

#include "absl/container/flat_hash_map.h"
#include "absl/hash/hash.h"
#include <cctype>
#include <cstdint>
#include <cstring>
#include <limits>
#include <new>
#include <optional>
#include <string>
#include <utility>
#include <vector>

#include <nanobind/nanobind.h>
#include <nanobind/stl/optional.h>
#include <nanobind/stl/pair.h>
#include <nanobind/stl/string.h>
#include <nanobind/stl/vector.h>
#include <nanobind/trampoline.h>

#include "fory/python/pyfory.h"
#include "fory/type/type.h"
#include "fory/util/bit_util.h"
#include "fory/util/buffer.h"
#include "fory/util/error.h"
#include "fory/util/string_util.h"

namespace nb = nanobind;
using namespace nb::literals;

constexpr const char *kBufferCapsuleName = "fory.Buffer";

static PyObject *g_raise_fory_error = nullptr;
static PyObject *g_weakref_ref = nullptr;

void raise_fory_error(fory::ErrorCode code, const std::string &message) {
  if (g_raise_fory_error == nullptr) {
    throw nb::builtin_exception(nb::exception_type::runtime_error,
                                message.c_str());
  }
  nb::object raise_obj = nb::borrow(g_raise_fory_error);
  raise_obj(static_cast<int>(code), message);
}

void raise_fory_error(fory::ErrorCode code) {
  raise_fory_error(code, "fory buffer error");
}

struct BufferView {
  explicit BufferView(nb::handle buffer_obj)
      : buffer_obj_(nb::borrow(buffer_obj)) {
    nb::object capsule = buffer_obj_.attr("_c_buffer_capsule")();
    buffer_ = static_cast<fory::Buffer *>(
        PyCapsule_GetPointer(capsule.ptr(), kBufferCapsuleName));
    if (buffer_ == nullptr) {
      throw nb::value_error("Invalid Buffer capsule");
    }
  }

  int32_t get_reader_index() const {
    return static_cast<int32_t>(buffer_->reader_index());
  }

  void set_reader_index(int32_t value) {
    if (value < 0) {
      throw nb::value_error("reader_index must be >= 0");
    }
    buffer_->reader_index(static_cast<uint32_t>(value));
  }

  int32_t get_writer_index() const {
    return static_cast<int32_t>(buffer_->writer_index());
  }

  void set_writer_index(int32_t value) {
    if (value < 0) {
      throw nb::value_error("writer_index must be >= 0");
    }
    buffer_->writer_index(static_cast<uint32_t>(value));
  }

  void grow(int32_t needed_size) {
    buffer_->grow(static_cast<uint32_t>(needed_size));
  }

  void ensure(int32_t length) {
    if (length > static_cast<int32_t>(buffer_->size())) {
      buffer_->reserve(static_cast<uint32_t>(length * 2));
    }
  }

  void check_bound(int32_t offset, int32_t length) {
    int32_t size = static_cast<int32_t>(buffer_->size());
    if ((offset | length | (offset + length) | (size - (offset + length))) <
        0) {
      raise_fory_error(fory::ErrorCode::BufferOutOfBound,
                       "buffer out of bound");
    }
  }

  void write_uint8(uint8_t value) { buffer_->write_uint8(value); }
  void write_int8(int8_t value) { buffer_->write_int8(value); }
  void write_uint16(uint16_t value) { buffer_->write_uint16(value); }
  void write_uint32(uint32_t value) { buffer_->write_uint32(value); }
  void write_uint64(uint64_t value) {
    buffer_->write_int64(static_cast<int64_t>(value));
  }
  void write_int16(int16_t value) { buffer_->write_int16(value); }
  void write_int32(int32_t value) { buffer_->write_int32(value); }
  void write_int64(int64_t value) { buffer_->write_int64(value); }
  void write_float(float value) { buffer_->write_float(value); }
  void write_double(double value) { buffer_->write_double(value); }
  void write_var_uint32(uint32_t value) { buffer_->write_var_uint32(value); }
  void write_var_int32(int32_t value) { buffer_->write_var_int32(value); }
  void write_var_uint64(uint64_t value) { buffer_->write_var_uint64(value); }
  void write_var_int64(int64_t value) { buffer_->write_var_int64(value); }
  void write_tagged_int64(int64_t value) { buffer_->write_tagged_int64(value); }
  void write_tagged_uint64(uint64_t value) {
    buffer_->write_tagged_uint64(value);
  }

  void put_int8(int32_t offset, int8_t value) {
    check_bound(offset, 1);
    buffer_->unsafe_put_byte(static_cast<uint32_t>(offset), value);
  }

  void put_uint8(int32_t offset, uint8_t value) {
    check_bound(offset, 1);
    buffer_->unsafe_put_byte(static_cast<uint32_t>(offset), value);
  }

  void write_bytes(const uint8_t *data, uint32_t length) {
    if (length == 0) {
      return;
    }
    uint32_t offset = buffer_->writer_index();
    buffer_->grow(length);
    check_bound(static_cast<int32_t>(offset), static_cast<int32_t>(length));
    buffer_->copy_from(offset, data, 0, length);
    buffer_->increase_writer_index(length);
  }

  uint8_t read_uint8() {
    uint8_t value = buffer_->read_uint8(error_);
    raise_if_error();
    return value;
  }

  int8_t read_int8() {
    int8_t value = buffer_->read_int8(error_);
    raise_if_error();
    return value;
  }

  uint16_t read_uint16() {
    uint16_t value = buffer_->read_uint16(error_);
    raise_if_error();
    return value;
  }

  uint32_t read_uint32() {
    uint32_t value = buffer_->read_uint32(error_);
    raise_if_error();
    return value;
  }

  uint64_t read_uint64() {
    uint64_t value = buffer_->read_uint64(error_);
    raise_if_error();
    return value;
  }

  int16_t read_int16() {
    int16_t value = buffer_->read_int16(error_);
    raise_if_error();
    return value;
  }

  int32_t read_int32() {
    int32_t value = buffer_->read_int32(error_);
    raise_if_error();
    return value;
  }

  int64_t read_int64() {
    int64_t value = buffer_->read_int64(error_);
    raise_if_error();
    return value;
  }

  float read_float() {
    float value = buffer_->read_float(error_);
    raise_if_error();
    return value;
  }

  double read_double() {
    double value = buffer_->read_double(error_);
    raise_if_error();
    return value;
  }

  int32_t read_var_int32() {
    int32_t value = buffer_->read_var_int32(error_);
    raise_if_error();
    return value;
  }

  uint32_t read_var_uint32() {
    uint32_t value = buffer_->read_var_uint32(error_);
    raise_if_error();
    return value;
  }

  int64_t read_var_int64() {
    int64_t value = buffer_->read_var_int64(error_);
    raise_if_error();
    return value;
  }

  uint64_t read_var_uint64() {
    uint64_t value = buffer_->read_var_uint64(error_);
    raise_if_error();
    return value;
  }

  int64_t read_tagged_int64() {
    int64_t value = buffer_->read_tagged_int64(error_);
    raise_if_error();
    return value;
  }

  uint64_t read_tagged_uint64() {
    uint64_t value = buffer_->read_tagged_uint64(error_);
    raise_if_error();
    return value;
  }

  int64_t read_bytes_as_int64(int32_t length) {
    int64_t value = 0;
    auto result = buffer_->get_bytes_as_int64(
        static_cast<uint32_t>(buffer_->reader_index()),
        static_cast<uint32_t>(length), &value);
    if (!result.ok()) {
      raise_fory_error(result.error().code(), result.error().message());
    }
    buffer_->increase_reader_index(static_cast<uint32_t>(length));
    return value;
  }

  nb::bytes read_bytes(int32_t length) {
    if (length == 0) {
      return nb::bytes("");
    }
    uint32_t offset = buffer_->reader_index();
    check_bound(static_cast<int32_t>(offset), length);
    const char *data = reinterpret_cast<const char *>(buffer_->data() + offset);
    buffer_->increase_reader_index(static_cast<uint32_t>(length));
    return nb::bytes(data, length);
  }

  void write_string(nb::handle value) {
    Py_ssize_t length = PyUnicode_GET_LENGTH(value.ptr());
    int32_t kind = PyUnicode_KIND(value.ptr());
    void *buffer = PyUnicode_DATA(value.ptr());
    uint64_t header = 0;
    int32_t buffer_size = 0;
    if (kind == PyUnicode_1BYTE_KIND) {
      buffer_size = static_cast<int32_t>(length);
      header = (static_cast<uint64_t>(length) << 2) | 0;
    } else if (kind == PyUnicode_2BYTE_KIND) {
      buffer_size = static_cast<int32_t>(length << 1);
      header = (static_cast<uint64_t>(length) << 3) | 1;
    } else {
      buffer =
          const_cast<char *>(PyUnicode_AsUTF8AndSize(value.ptr(), &length));
      buffer_size = static_cast<int32_t>(length);
      header = (static_cast<uint64_t>(buffer_size) << 2) | 2;
    }
    write_var_uint64(header);
    if (buffer_size == 0) {
      return;
    }
    uint32_t offset = buffer_->writer_index();
    buffer_->grow(static_cast<uint32_t>(buffer_size));
    check_bound(static_cast<int32_t>(offset), buffer_size);
    buffer_->copy_from(offset, reinterpret_cast<const uint8_t *>(buffer), 0,
                       static_cast<uint32_t>(buffer_size));
    buffer_->increase_writer_index(static_cast<uint32_t>(buffer_size));
  }

  nb::object read_string() {
    uint64_t header = read_var_uint64();
    uint32_t size = static_cast<uint32_t>(header >> 2);
    uint32_t offset = buffer_->reader_index();
    check_bound(static_cast<int32_t>(offset), static_cast<int32_t>(size));
    const char *buf = reinterpret_cast<const char *>(buffer_->data() + offset);
    buffer_->increase_reader_index(size);
    uint32_t encoding = static_cast<uint32_t>(header & 0b11);
    if (encoding == 0) {
      return nb::steal(
          PyUnicode_DecodeLatin1(buf, static_cast<Py_ssize_t>(size), "strict"));
    }
    if (encoding == 1) {
      if (fory::utf16_has_surrogate_pairs(
              reinterpret_cast<const uint16_t *>(buf), size >> 1)) {
        int utf16_le = -1;
        return nb::steal(PyUnicode_DecodeUTF16(
            buf, static_cast<Py_ssize_t>(size), nullptr, &utf16_le));
      }
      return nb::steal(
          PyUnicode_FromKindAndData(PyUnicode_2BYTE_KIND, buf, size >> 1));
    }
    return nb::steal(
        PyUnicode_DecodeUTF8(buf, static_cast<Py_ssize_t>(size), "strict"));
  }

  nb::bytes to_bytes(int32_t offset, int32_t length) {
    if (length == 0) {
      length = static_cast<int32_t>(buffer_->size());
    }
    uint8_t *data = buffer_->data() + offset;
    return nb::bytes(reinterpret_cast<const char *>(data), length);
  }

  nb::object slice(int32_t offset, int32_t length) {
    return buffer_obj_.attr("slice")(offset, length);
  }

  nb::object object() const { return buffer_obj_; }

  fory::Buffer *raw() const { return buffer_; }

private:
  void raise_if_error() {
    if (!error_.ok()) {
      raise_fory_error(error_.code(), error_.message());
      error_.reset();
    }
  }

  nb::object buffer_obj_;
  fory::Buffer *buffer_{nullptr};
  fory::Error error_{};
};

namespace nanobind::detail {
template <> struct type_caster<BufferView> {
  NB_TYPE_CASTER(BufferView, const_name("Buffer"));

  bool from_python(handle, uint8_t, cleanup_list *) noexcept { return false; }

  static handle from_cpp(const BufferView &view, rv_policy,
                         cleanup_list *) noexcept {
    return view.object().release();
  }
};
} // namespace nanobind::detail

namespace {
constexpr int8_t kNullFlag = -3;
constexpr int8_t kRefFlag = -2;
constexpr int8_t kNotNullValueFlag = -1;
constexpr int8_t kRefValueFlag = 0;

constexpr int8_t kUseTypeName = 0;
constexpr int8_t kUseTypeId = 1;
constexpr int8_t kNoTypeId = 0;
constexpr uint32_t kNoUserTypeId = 0xffffffffu;
constexpr int8_t kDefaultDynamicWriteMetaStrId = -1;
constexpr int32_t kSmallStringThreshold = 16;

PyObject *g_is_primitive_type = nullptr;
PyObject *g_encoding_enum = nullptr;
PyObject *g_datetime_module = nullptr;
PyObject *g_datetime_class = nullptr;
PyObject *g_date_class = nullptr;
PyObject *g_timezone_utc = nullptr;
PyObject *g_time_module = nullptr;
PyObject *g_platform_module = nullptr;
PyObject *g_mmh3_hash_buffer = nullptr;
PyObject *g_meta_string_decoder_cls = nullptr;
PyObject *g_buffer_class = nullptr;
std::vector<PyObject *> g_fory_weakrefs;

int32_t g_not_null_int64_flag = 0;
int32_t g_not_null_float64_flag = 0;
int32_t g_not_null_bool_flag = 0;
int32_t g_not_null_string_flag = 0;
int8_t g_int64_type_id = 0;
int8_t g_float64_type_id = 0;
int8_t g_bool_type_id = 0;
int8_t g_string_type_id = 0;

inline void prune_dead_fory_weakrefs() {
  size_t next = 0;
  for (PyObject *weak : g_fory_weakrefs) {
    if (weak == nullptr) {
      continue;
    }
    PyObject *obj = PyWeakref_GetObject(weak);
    if (obj == nullptr || obj == Py_None) {
      Py_DECREF(weak);
      continue;
    }
    g_fory_weakrefs[next++] = weak;
  }
  g_fory_weakrefs.resize(next);
}

inline void track_fory_instance(nb::handle fory_obj) {
  if (g_weakref_ref == nullptr) {
    return;
  }
  nb::object weak_obj = nb::borrow(g_weakref_ref)(fory_obj);
  g_fory_weakrefs.push_back(weak_obj.release().ptr());
  if ((g_fory_weakrefs.size() & 0x3F) == 0) {
    prune_dead_fory_weakrefs();
  }
}

inline uint64_t mix64(uint64_t x) {
  x ^= x >> 33;
  x *= static_cast<uint64_t>(0xff51afd7ed558ccdULL);
  x ^= x >> 33;
  x *= static_cast<uint64_t>(0xc4ceb9fe1a85ec53ULL);
  x ^= x >> 33;
  return x;
}

inline int64_t hash_small_metastring(int64_t v1, int64_t v2, int32_t length,
                                     uint8_t encoding) {
  uint64_t k = static_cast<uint64_t>(0x9e3779b97f4a7c15ULL);
  uint64_t x = static_cast<uint64_t>(v1) ^ (static_cast<uint64_t>(v2) * k);
  x ^= (static_cast<uint64_t>(length) << 56);
  uint64_t h = mix64(x);
  h = (h & static_cast<uint64_t>(0xffffffffffffff00ULL)) | encoding;
  return static_cast<int64_t>(h);
}

} // namespace
namespace {

class MapRefResolver {
public:
  explicit MapRefResolver(bool track_ref) : track_ref_(track_ref) {}
  ~MapRefResolver() { reset(); }

  bool write_ref_or_null(BufferView &buffer, nb::handle obj) {
    if (!track_ref_) {
      if (obj.is_none()) {
        buffer.write_int8(kNullFlag);
        return true;
      }
      buffer.write_int8(kNotNullValueFlag);
      return false;
    }
    if (obj.is_none()) {
      buffer.write_int8(kNullFlag);
      return true;
    }
    uint64_t object_id = reinterpret_cast<uintptr_t>(obj.ptr());
    auto it = written_objects_id_.find(object_id);
    if (it == written_objects_id_.end()) {
      int32_t next_id = static_cast<int32_t>(written_objects_id_.size());
      written_objects_id_[object_id] = next_id;
      written_objects_.push_back(obj.ptr());
      bool need_decref = Py_REFCNT(obj.ptr()) == 1;
      written_object_need_decref_.push_back(need_decref ? 1 : 0);
      if (need_decref) {
        Py_INCREF(obj.ptr());
      }
      buffer.write_int8(kRefValueFlag);
      return false;
    }
    buffer.write_int8(kRefFlag);
    buffer.write_var_uint32(static_cast<uint32_t>(it->second));
    return true;
  }

  int8_t read_ref_or_null(BufferView &buffer) {
    int8_t head_flag = buffer.read_int8();
    if (!track_ref_) {
      return head_flag;
    }
    if (head_flag == kRefFlag) {
      int32_t ref_id = static_cast<int32_t>(buffer.read_var_uint32());
      if (ref_id < 0 || ref_id >= static_cast<int32_t>(read_objects_.size())) {
        throw nb::value_error("Invalid ref id");
      }
      PyObject *obj = read_objects_[static_cast<size_t>(ref_id)];
      if (obj == nullptr) {
        throw nb::value_error("Invalid ref id");
      }
      read_object_ = nb::borrow(obj);
      return kRefFlag;
    }
    read_object_ = nb::none();
    return head_flag;
  }

  int32_t preserve_ref_id() {
    if (!track_ref_) {
      return -1;
    }
    int32_t next_read_ref_id = static_cast<int32_t>(read_objects_.size());
    read_objects_.push_back(nullptr);
    read_ref_ids_.push_back(next_read_ref_id);
    return next_read_ref_id;
  }

  int32_t try_preserve_ref_id(BufferView &buffer) {
    if (!track_ref_) {
      return buffer.read_int8();
    }
    int8_t head_flag = buffer.read_int8();
    if (head_flag == kRefFlag) {
      int32_t ref_id = static_cast<int32_t>(buffer.read_var_uint32());
      if (ref_id < 0 || ref_id >= static_cast<int32_t>(read_objects_.size())) {
        throw nb::value_error("Invalid ref id");
      }
      PyObject *obj = read_objects_[static_cast<size_t>(ref_id)];
      if (obj == nullptr) {
        throw nb::value_error("Invalid ref id");
      }
      read_object_ = nb::borrow(obj);
      return head_flag;
    }
    read_object_ = nb::none();
    if (head_flag == kRefValueFlag) {
      return preserve_ref_id();
    }
    return head_flag;
  }

  int32_t last_preserved_ref_id() const {
    if (read_ref_ids_.empty()) {
      throw nb::value_error("No preserved ref id");
    }
    return read_ref_ids_.back();
  }

  void reference(nb::handle obj) {
    if (!track_ref_) {
      return;
    }
    if (read_ref_ids_.empty()) {
      return;
    }
    int32_t ref_id = read_ref_ids_.back();
    read_ref_ids_.pop_back();
    if (ref_id < 0) {
      return;
    }
    bool need_inc = read_objects_[static_cast<size_t>(ref_id)] == nullptr;
    if (need_inc) {
      Py_INCREF(obj.ptr());
    }
    read_objects_[static_cast<size_t>(ref_id)] = obj.ptr();
  }

  nb::object get_read_object(std::optional<int32_t> id = std::nullopt) const {
    if (!track_ref_) {
      return nb::none();
    }
    if (!id.has_value()) {
      return read_object_.is_valid() ? read_object_ : nb::none();
    }
    int32_t ref_id = id.value();
    PyObject *obj = read_objects_[static_cast<size_t>(ref_id)];
    if (obj == nullptr) {
      return nb::none();
    }
    return nb::borrow(obj);
  }

  void set_read_object(int32_t ref_id, nb::handle obj) {
    if (!track_ref_) {
      return;
    }
    if (ref_id >= 0) {
      bool need_inc = read_objects_[static_cast<size_t>(ref_id)] == nullptr;
      if (need_inc) {
        Py_INCREF(obj.ptr());
      }
      read_objects_[static_cast<size_t>(ref_id)] = obj.ptr();
    }
  }

  void reset() {
    reset_write();
    reset_read();
  }

  void reset_write() {
    written_objects_id_.clear();
    for (size_t i = 0; i < written_objects_.size(); ++i) {
      if (written_object_need_decref_[i] != 0) {
        Py_XDECREF(written_objects_[i]);
      }
    }
    written_objects_.clear();
    written_object_need_decref_.clear();
  }

  void reset_read() {
    if (!track_ref_) {
      return;
    }
    for (PyObject *item : read_objects_) {
      Py_XDECREF(item);
    }
    read_objects_.clear();
    read_ref_ids_.clear();
    read_object_ = nb::none();
  }

  void push_non_ref_marker() { read_ref_ids_.push_back(-1); }

  bool track_ref() const { return track_ref_; }

private:
  absl::flat_hash_map<uint64_t, int32_t> written_objects_id_;
  std::vector<PyObject *> written_objects_;
  std::vector<uint8_t> written_object_need_decref_;
  std::vector<PyObject *> read_objects_;
  std::vector<int32_t> read_ref_ids_;
  nb::object read_object_{nb::none()};
  bool track_ref_{false};
};

class MetaStringBytes {
public:
  MetaStringBytes(nb::bytes data, int64_t hashcode)
      : data_(std::move(data)),
        length_(static_cast<int16_t>(PyBytes_GET_SIZE(data_.ptr()))),
        encoding_(static_cast<int8_t>(hashcode & 0xff)), hashcode_(hashcode),
        dynamic_write_string_id_(kDefaultDynamicWriteMetaStrId) {}

  nb::bytes data() const { return data_; }
  int16_t length() const { return length_; }
  int8_t encoding() const { return encoding_; }
  int64_t hashcode() const { return hashcode_; }

  int16_t dynamic_write_string_id() const { return dynamic_write_string_id_; }
  void set_dynamic_write_string_id(int16_t value) {
    dynamic_write_string_id_ = value;
  }

  nb::object decode(nb::handle decoder) const {
    if (g_encoding_enum == nullptr) {
      return decoder.attr("decode")(data_, encoding_);
    }
    nb::object enc_enum = nb::borrow(g_encoding_enum);
    nb::object enc = enc_enum(encoding_);
    return decoder.attr("decode")(data_, enc);
  }

  bool operator==(const MetaStringBytes &other) const {
    return hashcode_ == other.hashcode_;
  }

private:
  nb::bytes data_;
  int16_t length_;
  int8_t encoding_;
  int64_t hashcode_;
  int16_t dynamic_write_string_id_;
};

class MetaStringResolver {
public:
  MetaStringResolver()
      : enum_str_set_(nb::set()), metastr_to_metastr_bytes_(nb::dict()) {}

  void write_meta_string_bytes(BufferView &buffer,
                               MetaStringBytes &metastr_bytes) {
    int16_t dynamic_type_id = metastr_bytes.dynamic_write_string_id();
    int32_t length = metastr_bytes.length();
    if (dynamic_type_id == kDefaultDynamicWriteMetaStrId) {
      dynamic_type_id = dynamic_write_string_id_;
      metastr_bytes.set_dynamic_write_string_id(dynamic_type_id);
      dynamic_write_string_id_ += 1;
      dynamic_written_enum_string_.push_back(&metastr_bytes);
      buffer.write_var_uint32(static_cast<uint32_t>(length << 1));
      if (length <= kSmallStringThreshold) {
        if (length != 0) {
          buffer.write_int8(metastr_bytes.encoding());
        }
      } else {
        buffer.write_int64(metastr_bytes.hashcode());
      }
      nb::bytes data = metastr_bytes.data();
      buffer.write_bytes(
          reinterpret_cast<const uint8_t *>(PyBytes_AS_STRING(data.ptr())),
          static_cast<uint32_t>(length));
    } else {
      buffer.write_var_uint32(
          static_cast<uint32_t>(((dynamic_type_id + 1) << 1) | 1));
    }
  }

  MetaStringBytes *read_meta_string_bytes(BufferView &buffer) {
    int32_t header = static_cast<int32_t>(buffer.read_var_uint32());
    int32_t length = header >> 1;
    if ((header & 0b1) != 0) {
      return dynamic_id_to_enum_string_vec_[length - 1];
    }
    if (length <= kSmallStringThreshold) {
      if (length == 0) {
        auto *empty = get_empty_meta_string();
        dynamic_id_to_enum_string_vec_.push_back(empty);
        return empty;
      }
      int8_t encoding = buffer.read_int8();
      nb::bytes str_bytes = buffer.read_bytes(length);
      const uint8_t *data =
          reinterpret_cast<const uint8_t *>(PyBytes_AS_STRING(str_bytes.ptr()));
      int64_t v1 = 0;
      int64_t v2 = 0;
      if (length <= 8) {
        for (int i = 0; i < length; ++i) {
          v1 |= static_cast<int64_t>(data[i]) << (i * 8);
        }
      } else {
        for (int i = 0; i < 8; ++i) {
          v1 |= static_cast<int64_t>(data[i]) << (i * 8);
        }
        for (int i = 0; i < length - 8; ++i) {
          v2 |= static_cast<int64_t>(data[8 + i]) << (i * 8);
        }
      }
      int64_t hashcode =
          hash_small_metastring(v1, v2, length, static_cast<uint8_t>(encoding));
      auto it = hash_to_small_metastring_bytes_.find(hashcode);
      if (it != hash_to_small_metastring_bytes_.end()) {
        auto *cached = nb::cast<MetaStringBytes *>(it->second);
        dynamic_id_to_enum_string_vec_.push_back(cached);
        return cached;
      }
      nb::object enum_str = nb::cast(MetaStringBytes(str_bytes, hashcode));
      enum_str_set_.add(enum_str);
      hash_to_small_metastring_bytes_[hashcode] = enum_str;
      auto *meta_ptr = nb::cast<MetaStringBytes *>(enum_str);
      dynamic_id_to_enum_string_vec_.push_back(meta_ptr);
      return meta_ptr;
    }

    int64_t hashcode = buffer.read_int64();
    nb::bytes str_bytes = buffer.read_bytes(length);
    auto it = hash_to_metastring_bytes_.find(hashcode);
    if (it != hash_to_metastring_bytes_.end()) {
      auto *cached = nb::cast<MetaStringBytes *>(it->second);
      dynamic_id_to_enum_string_vec_.push_back(cached);
      return cached;
    }
    nb::object enum_str = nb::cast(MetaStringBytes(str_bytes, hashcode));
    enum_str_set_.add(enum_str);
    hash_to_metastring_bytes_[hashcode] = enum_str;
    auto *meta_ptr = nb::cast<MetaStringBytes *>(enum_str);
    dynamic_id_to_enum_string_vec_.push_back(meta_ptr);
    return meta_ptr;
  }

  MetaStringBytes *get_metastr_bytes(nb::handle metastr) {
    nb::object cached = metastr_to_metastr_bytes_.attr("get")(metastr);
    if (!cached.is_none()) {
      return nb::cast<MetaStringBytes *>(cached);
    }
    nb::bytes encoded = metastr.attr("encoded_data");
    int32_t length = static_cast<int32_t>(PyBytes_GET_SIZE(encoded.ptr()));
    if (length == 0) {
      MetaStringBytes *empty = get_empty_meta_string();
      metastr_to_metastr_bytes_[metastr] = nb::cast(empty);
      return empty;
    }
    int64_t hashcode = 0;
    if (length <= kSmallStringThreshold) {
      const uint8_t *data =
          reinterpret_cast<const uint8_t *>(PyBytes_AS_STRING(encoded.ptr()));
      int64_t v1 = 0;
      int64_t v2 = 0;
      if (length <= 8) {
        for (int i = 0; i < length; ++i) {
          v1 |= static_cast<int64_t>(data[i]) << (i * 8);
        }
      } else {
        for (int i = 0; i < 8; ++i) {
          v1 |= static_cast<int64_t>(data[i]) << (i * 8);
        }
        for (int i = 0; i < length - 8; ++i) {
          v2 |= static_cast<int64_t>(data[8 + i]) << (i * 8);
        }
      }
      uint8_t enc = static_cast<uint8_t>(
          nb::cast<int>(metastr.attr("encoding").attr("value")));
      hashcode = hash_small_metastring(v1, v2, length, enc);
    } else {
      nb::object hash_buffer = nb::borrow(g_mmh3_hash_buffer);
      nb::object result_obj = hash_buffer(encoded, 47);
      nb::tuple result = nb::cast<nb::tuple>(result_obj);
      hashcode = nb::cast<int64_t>(result[0]);
      int8_t enc = static_cast<int8_t>(
          nb::cast<int>(metastr.attr("encoding").attr("value")) & 0xFF);
      hashcode = (hashcode >> 8 << 8) | enc;
    }
    nb::object metastr_bytes = nb::cast(MetaStringBytes(encoded, hashcode));
    metastr_to_metastr_bytes_[metastr] = metastr_bytes;
    return nb::cast<MetaStringBytes *>(metastr_bytes);
  }

  void reset_read() { dynamic_id_to_enum_string_vec_.clear(); }

  void reset_write() {
    if (dynamic_write_string_id_ != 0) {
      dynamic_write_string_id_ = 0;
      for (MetaStringBytes *meta_ptr : dynamic_written_enum_string_) {
        meta_ptr->set_dynamic_write_string_id(kDefaultDynamicWriteMetaStrId);
      }
      dynamic_written_enum_string_.clear();
    }
  }

  void clear() {
    reset_write();
    reset_read();
    hash_to_metastring_bytes_.clear();
    hash_to_small_metastring_bytes_.clear();
    enum_str_set_.clear();
    metastr_to_metastr_bytes_.clear();
    empty_meta_string_ = nb::none();
  }

private:
  MetaStringBytes *get_empty_meta_string() {
    if (empty_meta_string_.is_none()) {
      empty_meta_string_ = nb::cast(MetaStringBytes(nb::bytes(""), 0));
      enum_str_set_.add(empty_meta_string_);
    }
    return nb::cast<MetaStringBytes *>(empty_meta_string_);
  }

  int16_t dynamic_write_string_id_{0};
  std::vector<MetaStringBytes *> dynamic_written_enum_string_;
  std::vector<MetaStringBytes *> dynamic_id_to_enum_string_vec_;
  absl::flat_hash_map<int64_t, nb::object> hash_to_metastring_bytes_;
  absl::flat_hash_map<int64_t, nb::object> hash_to_small_metastring_bytes_;
  nb::set enum_str_set_;
  nb::dict metastr_to_metastr_bytes_;
  nb::object empty_meta_string_{nb::none()};
};

class Serializer;

class TypeInfo {
public:
  TypeInfo() = default;

  TypeInfo(nb::object cls, int type_id, uint32_t user_type_id,
           nb::object serializer, nb::object namespace_bytes,
           nb::object typename_bytes, bool dynamic_type, nb::object type_def)
      : cls_(std::move(cls)),
        type_id_(type_id < 0 ? kNoTypeId : static_cast<uint8_t>(type_id)),
        user_type_id_(user_type_id), serializer_(std::move(serializer)),
        namespace_bytes_(std::move(namespace_bytes)),
        typename_bytes_(std::move(typename_bytes)), dynamic_type_(dynamic_type),
        type_def_(std::move(type_def)) {}

  nb::object cls() const { return cls_; }
  void set_cls(nb::object cls) { cls_ = std::move(cls); }

  uint8_t type_id() const { return type_id_; }
  void set_type_id(uint8_t type_id) { type_id_ = type_id; }

  uint32_t user_type_id() const { return user_type_id_; }
  void set_user_type_id(uint32_t user_type_id) { user_type_id_ = user_type_id; }

  nb::object serializer() const { return serializer_; }
  void set_serializer(nb::object serializer) {
    serializer_ = std::move(serializer);
  }

  nb::object namespace_bytes() const { return namespace_bytes_; }
  void set_namespace_bytes(nb::object namespace_bytes) {
    namespace_bytes_ = std::move(namespace_bytes);
  }

  nb::object typename_bytes() const { return typename_bytes_; }
  void set_typename_bytes(nb::object typename_bytes) {
    typename_bytes_ = std::move(typename_bytes);
  }

  bool dynamic_type() const { return dynamic_type_; }
  void set_dynamic_type(bool dynamic_type) { dynamic_type_ = dynamic_type; }

  nb::object type_def() const { return type_def_; }
  void set_type_def(nb::object type_def) { type_def_ = std::move(type_def); }

  nb::str decode_namespace() const {
    if (namespace_bytes_.is_none()) {
      return nb::str("");
    }
    nb::object decoder_cls = nb::borrow(g_meta_string_decoder_cls);
    nb::object decoder = decoder_cls(".", "_");
    return nb::str(namespace_bytes_.attr("decode")(decoder));
  }

  nb::str decode_typename() const {
    if (typename_bytes_.is_none()) {
      return nb::str("");
    }
    nb::object decoder_cls = nb::borrow(g_meta_string_decoder_cls);
    nb::object decoder = decoder_cls("$", "_");
    return nb::str(typename_bytes_.attr("decode")(decoder));
  }

private:
  nb::object cls_{nb::none()};
  uint8_t type_id_{kNoTypeId};
  uint32_t user_type_id_{kNoUserTypeId};
  nb::object serializer_{nb::none()};
  nb::object namespace_bytes_{nb::none()};
  nb::object typename_bytes_{nb::none()};
  bool dynamic_type_{false};
  nb::object type_def_{nb::none()};
};

class SerializationContext;

class TypeResolver {
public:
  TypeResolver(nb::object fory, bool meta_share, nb::object meta_compressor);

  void initialize() {
    resolver_.attr("initialize")();
    nb::dict types_info = resolver_.attr("_types_info");
    for (auto item : types_info) {
      auto *typeinfo = nb::cast<TypeInfo *>(item.second);
      populate_type_info(typeinfo);
    }
  }

  nb::object fory() const { return resolver_.attr("fory"); }

  void set_serialization_context(SerializationContext *context) {
    serialization_context_ = context;
  }

  void register_type(nb::object cls, int type_id, nb::object ns,
                     nb::object typename_, nb::object serializer) {
    nb::object type_id_obj = type_id < 0 ? nb::none() : nb::int_(type_id);
    nb::object typeinfo = resolver_.attr("register_type")(
        cls, "type_id"_a = type_id_obj, "namespace"_a = ns,
        "typename"_a = typename_, "serializer"_a = serializer);
    populate_type_info(nb::cast<TypeInfo *>(typeinfo));
  }

  void register_union(nb::object cls, int type_id, nb::object ns,
                      nb::object typename_, nb::object serializer) {
    nb::object type_id_obj = type_id < 0 ? nb::none() : nb::int_(type_id);
    nb::object typeinfo = resolver_.attr("register_union")(
        cls, "type_id"_a = type_id_obj, "namespace"_a = ns,
        "typename"_a = typename_, "serializer"_a = serializer);
    populate_type_info(nb::cast<TypeInfo *>(typeinfo));
  }

  void register_serializer(nb::object cls, nb::object serializer) {
    nb::object typeinfo1 = resolver_.attr("get_type_info")(cls);
    resolver_.attr("register_serializer")(cls, serializer);
    nb::object typeinfo2 = resolver_.attr("get_type_info")(cls);
    auto *info1 = nb::cast<TypeInfo *>(typeinfo1);
    auto *info2 = nb::cast<TypeInfo *>(typeinfo2);
    if (info1->type_id() != info2->type_id() ||
        info1->user_type_id() != info2->user_type_id()) {
      uint8_t type_id = info1->type_id();
      if (type_id == static_cast<uint8_t>(fory::TypeId::ENUM) ||
          type_id == static_cast<uint8_t>(fory::TypeId::STRUCT) ||
          type_id == static_cast<uint8_t>(fory::TypeId::COMPATIBLE_STRUCT) ||
          type_id == static_cast<uint8_t>(fory::TypeId::EXT) ||
          type_id == static_cast<uint8_t>(fory::TypeId::TYPED_UNION)) {
        if (info1->user_type_id() != kNoUserTypeId) {
          user_type_id_to_type_info_[info1->user_type_id()] = nullptr;
        }
      } else {
        if (type_id < registered_id_to_type_info_.size()) {
          registered_id_to_type_info_[type_id] = nullptr;
        }
      }
      populate_type_info(info2);
    }
  }

  nb::object get_serializer(nb::object cls) {
    return get_type_info(cls, true)->serializer();
  }

  TypeInfo *get_type_info(nb::object cls, bool create) {
    uint64_t key = reinterpret_cast<uintptr_t>(cls.ptr());
    auto it = types_info_.find(key);
    if (it != types_info_.end() && it->second != nullptr) {
      auto *info = reinterpret_cast<TypeInfo *>(it->second);
      if (!info->serializer().is_none()) {
        return info;
      }
      info->set_serializer(
          resolver_.attr("get_type_info")(cls).attr("serializer"));
      return info;
    }
    if (!create) {
      return nullptr;
    }
    nb::object typeinfo =
        resolver_.attr("get_type_info")(cls, "create"_a = create);
    auto *info = nb::cast<TypeInfo *>(typeinfo);
    types_info_[key] = reinterpret_cast<PyObject *>(info);
    populate_type_info(info);
    return info;
  }

  bool is_registered_by_name(nb::object cls) {
    return nb::cast<bool>(resolver_.attr("is_registered_by_name")(cls));
  }

  bool is_registered_by_id(nb::object cls) {
    return nb::cast<bool>(resolver_.attr("is_registered_by_id")(cls));
  }

  nb::object get_registered_name(nb::object cls) {
    return resolver_.attr("get_registered_name")(cls);
  }

  nb::object get_registered_id(nb::object cls) {
    return resolver_.attr("get_registered_id")(cls);
  }

  nb::object get_registered_user_type_id(nb::object cls) {
    return resolver_.attr("get_registered_user_type_id")(cls);
  }

  nb::object get_registered_type_ids(nb::object cls) {
    return resolver_.attr("get_registered_type_ids")(cls);
  }

  TypeInfo *read_type_info(BufferView &buffer);
  void write_type_info(BufferView &buffer, TypeInfo *typeinfo);

  TypeInfo *get_type_info_by_id(uint8_t type_id) {
    if (type_id >= registered_id_to_type_info_.size() ||
        fory::is_namespaced_type(static_cast<fory::TypeId>(type_id))) {
      throw nb::value_error("Unexpected type_id");
    }
    PyObject *typeinfo_ptr = registered_id_to_type_info_[type_id];
    if (typeinfo_ptr == nullptr) {
      throw nb::value_error("Unexpected type_id");
    }
    return reinterpret_cast<TypeInfo *>(typeinfo_ptr);
  }

  TypeInfo *get_user_type_info_by_id(uint32_t user_type_id) {
    auto it = user_type_id_to_type_info_.find(user_type_id);
    if (it == user_type_id_to_type_info_.end() || it->second == nullptr) {
      throw nb::value_error("Unexpected user_type_id");
    }
    return reinterpret_cast<TypeInfo *>(it->second);
  }

  nb::object get_type_info_by_name(nb::object ns, nb::object typename_) {
    return resolver_.attr("get_type_info_by_name")("namespace"_a = ns,
                                                   "typename"_a = typename_);
  }

  void set_type_info(nb::object typeinfo) {
    resolver_.attr("_set_type_info")(typeinfo);
  }

  nb::object get_meta_compressor() {
    return resolver_.attr("get_meta_compressor")();
  }

  void write_shared_type_meta(BufferView &buffer, TypeInfo *typeinfo);
  nb::object read_shared_type_meta(BufferView &buffer);

  nb::object read_and_build_type_info(BufferView &buffer) {
    return resolver_.attr("_read_and_build_type_info")(buffer.object());
  }

  void reset_write() {}
  void reset_read() {}

  void clear() {
    registered_id_to_type_info_.clear();
    user_type_id_to_type_info_.clear();
    types_info_.clear();
    meta_hash_to_type_info_.clear();
    serialization_context_ = nullptr;
    metastring_resolver_ = nullptr;
    resolver_ = nb::none();
  }

private:
  void populate_type_info(TypeInfo *typeinfo) {
    uint8_t type_id = typeinfo->type_id();
    if (type_id == static_cast<uint8_t>(fory::TypeId::ENUM) ||
        type_id == static_cast<uint8_t>(fory::TypeId::STRUCT) ||
        type_id == static_cast<uint8_t>(fory::TypeId::COMPATIBLE_STRUCT) ||
        type_id == static_cast<uint8_t>(fory::TypeId::EXT) ||
        type_id == static_cast<uint8_t>(fory::TypeId::TYPED_UNION)) {
      if (typeinfo->user_type_id() != kNoUserTypeId) {
        user_type_id_to_type_info_[typeinfo->user_type_id()] =
            reinterpret_cast<PyObject *>(typeinfo);
      }
    } else {
      if (type_id >= registered_id_to_type_info_.size()) {
        registered_id_to_type_info_.resize(type_id * 2, nullptr);
      }
      if (type_id > 0 && (!xlang_ || !fory::is_namespaced_type(
                                         static_cast<fory::TypeId>(type_id)))) {
        registered_id_to_type_info_[type_id] =
            reinterpret_cast<PyObject *>(typeinfo);
      }
    }
    uint64_t key = reinterpret_cast<uintptr_t>(typeinfo->cls().ptr());
    types_info_[key] = reinterpret_cast<PyObject *>(typeinfo);
    if (types_info_.size() * 10 >= types_info_.bucket_count() * 5) {
      types_info_.rehash(types_info_.size() * 2);
    }
    if (!typeinfo->typename_bytes().is_none()) {
      auto *ns = nb::cast<MetaStringBytes *>(typeinfo->namespace_bytes());
      auto *tn = nb::cast<MetaStringBytes *>(typeinfo->typename_bytes());
      load_bytes_to_type_info(type_id, ns, tn);
    }
  }

  TypeInfo *load_bytes_to_type_info(uint8_t type_id, MetaStringBytes *ns,
                                    MetaStringBytes *tn) {
    auto key = std::make_pair(ns->hashcode(), tn->hashcode());
    auto it = meta_hash_to_type_info_.find(key);
    if (it != meta_hash_to_type_info_.end()) {
      return reinterpret_cast<TypeInfo *>(it->second);
    }
    nb::object typeinfo = resolver_.attr("_load_metabytes_to_type_info")(
        nb::cast(ns), nb::cast(tn));
    auto *info = nb::cast<TypeInfo *>(typeinfo);
    meta_hash_to_type_info_[key] = reinterpret_cast<PyObject *>(info);
    return info;
  }

  MetaStringResolver *metastring_resolver_{nullptr};
  nb::object resolver_;
  std::vector<PyObject *> registered_id_to_type_info_;
  absl::flat_hash_map<uint32_t, PyObject *> user_type_id_to_type_info_;
  absl::flat_hash_map<uint64_t, PyObject *> types_info_;
  absl::flat_hash_map<std::pair<int64_t, int64_t>, PyObject *>
      meta_hash_to_type_info_;
  SerializationContext *serialization_context_{nullptr};
  bool meta_share_{false};
  bool xlang_{false};
};

class MetaContext {
public:
  explicit MetaContext(TypeResolver *type_resolver)
      : type_resolver_(type_resolver) {}
  explicit MetaContext(nb::object fory);

  void write_shared_type_info(BufferView &buffer, nb::object typeinfo) {
    uint8_t type_id = nb::cast<uint8_t>(typeinfo.attr("type_id"));
    if (!fory::is_type_share_meta(static_cast<fory::TypeId>(type_id))) {
      return;
    }
    nb::object type_cls = typeinfo.attr("cls");
    uint64_t type_addr = reinterpret_cast<uintptr_t>(type_cls.ptr());
    auto it = type_map_.find(type_addr);
    if (it != type_map_.end()) {
      buffer.write_var_uint32(static_cast<uint32_t>((it->second << 1) | 1));
      return;
    }
    int32_t index = static_cast<int32_t>(type_map_.size());
    buffer.write_var_uint32(static_cast<uint32_t>(index << 1));
    type_map_[type_addr] = index;
    nb::object type_def = typeinfo.attr("type_def");
    if (type_def.is_none()) {
      if (type_resolver_ != nullptr) {
        type_resolver_->set_type_info(typeinfo);
      } else {
        type_resolver_obj().attr("_set_type_info")(typeinfo);
      }
      type_def = typeinfo.attr("type_def");
    }
    nb::bytes encoded = type_def.attr("encoded");
    buffer.write_bytes(
        reinterpret_cast<const uint8_t *>(PyBytes_AS_STRING(encoded.ptr())),
        static_cast<uint32_t>(PyBytes_GET_SIZE(encoded.ptr())));
  }

  nb::object read_shared_type_info(BufferView &buffer) {
    uint8_t type_id = buffer.read_uint8();
    return read_shared_type_info_with_type_id(buffer, type_id);
  }

  nb::object read_shared_type_info_with_type_id(BufferView &buffer,
                                                uint8_t type_id) {
    uint32_t user_type_id = kNoUserTypeId;
    fory::TypeRegistrationKind reg_kind =
        fory::get_type_registration_kind(static_cast<fory::TypeId>(type_id));
    bool share_meta =
        fory::is_type_share_meta(static_cast<fory::TypeId>(type_id));
    if (reg_kind == fory::TypeRegistrationKind::BY_ID && !share_meta) {
      user_type_id = buffer.read_var_uint32();
    }
    if (!share_meta) {
      if (reg_kind == fory::TypeRegistrationKind::BY_ID) {
        if (type_resolver_ != nullptr) {
          return nb::cast(
              type_resolver_->get_user_type_info_by_id(user_type_id));
        }
        return type_resolver_obj().attr("get_user_type_info_by_id")(
            user_type_id);
      }
      if (type_resolver_ != nullptr) {
        return nb::cast(type_resolver_->get_type_info_by_id(type_id));
      }
      return type_resolver_obj().attr("get_type_info_by_id")(type_id);
    }
    int32_t index_marker = static_cast<int32_t>(buffer.read_var_uint32());
    bool is_ref = (index_marker & 1) == 1;
    int32_t index = index_marker >> 1;
    if (is_ref) {
      return read_type_infos_[static_cast<size_t>(index)];
    }
    nb::object type_info =
        type_resolver_ != nullptr
            ? type_resolver_->read_and_build_type_info(buffer)
            : type_resolver_obj().attr("_read_and_build_type_info")(
                  buffer.object());
    read_type_infos_.push_back(type_info);
    return type_info;
  }

  void reset_write() { type_map_.clear(); }
  void reset_read() { read_type_infos_.clear(); }
  void clear() {
    reset_write();
    reset_read();
    type_resolver_ = nullptr;
    type_resolver_ref_ = nb::none();
  }

  nb::str repr() const {
    nb::list infos;
    for (auto &item : read_type_infos_) {
      infos.append(item);
    }
    return nb::str("MetaContext(read_infos={})").format(infos);
  }

private:
  nb::object type_resolver_obj() const {
    if (type_resolver_ref_.is_none()) {
      nb::object raise_fory_error = nb::borrow(g_raise_fory_error);
      raise_fory_error(5, "TypeResolver has been destroyed");
      return nb::none();
    }
    nb::object resolver = type_resolver_ref_();
    if (resolver.is_none()) {
      nb::object raise_fory_error = nb::borrow(g_raise_fory_error);
      raise_fory_error(5, "TypeResolver has been destroyed");
      return nb::none();
    }
    return resolver;
  }

  absl::flat_hash_map<uint64_t, int32_t> type_map_;
  std::vector<nb::object> read_type_infos_;
  TypeResolver *type_resolver_{nullptr};
  nb::object type_resolver_ref_{nb::none()};
};

class SerializationContext {
public:
  SerializationContext(nb::object fory, bool scoped_meta_share_enabled);

  void add(nb::handle key, nb::handle obj) {
    objects_[reinterpret_cast<uintptr_t>(key.ptr())] = obj;
  }

  bool contains(nb::handle key) const {
    return objects_.contains(reinterpret_cast<uintptr_t>(key.ptr()));
  }

  nb::object get_item(nb::handle key) const {
    return objects_[reinterpret_cast<uintptr_t>(key.ptr())];
  }

  nb::object get(nb::handle key) const {
    return objects_.attr("get")(reinterpret_cast<uintptr_t>(key.ptr()));
  }

  void reset() {
    if (objects_.size() > 0) {
      objects_.clear();
    }
  }

  void reset_write() {
    if (objects_.size() > 0) {
      objects_.clear();
    }
    if (scoped_meta_share_enabled_ && meta_context_ptr_ != nullptr) {
      meta_context_ptr_->reset_write();
    }
  }

  void reset_read() {
    if (objects_.size() > 0) {
      objects_.clear();
    }
    if (scoped_meta_share_enabled_ && meta_context_ptr_ != nullptr) {
      meta_context_ptr_->reset_read();
    }
  }

  void clear() {
    reset();
    if (meta_context_ptr_ != nullptr) {
      meta_context_ptr_->clear();
    }
    meta_context_ptr_ = nullptr;
    meta_context_ref_ = nb::none();
  }

  nb::object meta_context() const { return meta_context_ref_; }
  MetaContext *meta_context_ptr() const { return meta_context_ptr_; }
  bool scoped_meta_share_enabled() const { return scoped_meta_share_enabled_; }
  void set_meta_context(nb::object meta_context) {
    if (meta_context.is_none()) {
      meta_context_ref_ = nb::none();
      meta_context_ptr_ = nullptr;
      return;
    }
    meta_context_ptr_ = nb::cast<MetaContext *>(meta_context);
    meta_context_ref_ = std::move(meta_context);
  }

private:
  nb::dict objects_;
  bool scoped_meta_share_enabled_{false};
  MetaContext *meta_context_ptr_{nullptr};
  nb::object meta_context_ref_{nb::none()};
};

TypeInfo *TypeResolver::read_type_info(BufferView &buffer) {
  uint8_t type_id = buffer.read_uint8();
  fory::TypeRegistrationKind reg_kind =
      fory::get_type_registration_kind(static_cast<fory::TypeId>(type_id));
  uint32_t user_type_id = kNoUserTypeId;
  MetaContext *meta_context = serialization_context_->meta_context_ptr();
  if (type_id == static_cast<uint8_t>(fory::TypeId::COMPATIBLE_STRUCT) ||
      type_id == static_cast<uint8_t>(fory::TypeId::NAMED_COMPATIBLE_STRUCT)) {
    return nb::cast<TypeInfo *>(
        meta_context->read_shared_type_info_with_type_id(buffer, type_id));
  }
  if (reg_kind == fory::TypeRegistrationKind::BY_NAME) {
    if (meta_share_) {
      return nb::cast<TypeInfo *>(
          meta_context->read_shared_type_info_with_type_id(buffer, type_id));
    }
    auto *namespace_bytes =
        metastring_resolver_->read_meta_string_bytes(buffer);
    auto *typename_bytes = metastring_resolver_->read_meta_string_bytes(buffer);
    return load_bytes_to_type_info(type_id, namespace_bytes, typename_bytes);
  }
  if (reg_kind == fory::TypeRegistrationKind::BY_ID) {
    user_type_id = buffer.read_var_uint32();
    return get_user_type_info_by_id(user_type_id);
  }
  if (type_id >= registered_id_to_type_info_.size()) {
    throw nb::value_error("Unexpected type_id");
  }
  PyObject *typeinfo_ptr = registered_id_to_type_info_[type_id];
  if (typeinfo_ptr == nullptr) {
    throw nb::value_error("Unexpected type_id");
  }
  return reinterpret_cast<TypeInfo *>(typeinfo_ptr);
}

void TypeResolver::write_type_info(BufferView &buffer, TypeInfo *typeinfo) {
  if (typeinfo->dynamic_type()) {
    return;
  }
  uint8_t type_id = typeinfo->type_id();
  buffer.write_uint8(type_id);
  MetaContext *meta_context = serialization_context_->meta_context_ptr();
  if (type_id == static_cast<uint8_t>(fory::TypeId::COMPATIBLE_STRUCT) ||
      type_id == static_cast<uint8_t>(fory::TypeId::NAMED_COMPATIBLE_STRUCT)) {
    meta_context->write_shared_type_info(buffer, nb::cast(typeinfo));
    return;
  }
  fory::TypeRegistrationKind reg_kind =
      fory::get_type_registration_kind(static_cast<fory::TypeId>(type_id));
  if (reg_kind == fory::TypeRegistrationKind::BY_ID) {
    if (typeinfo->user_type_id() == kNoUserTypeId) {
      throw nb::value_error("user_type_id required");
    }
    buffer.write_var_uint32(typeinfo->user_type_id());
    return;
  }
  if (reg_kind == fory::TypeRegistrationKind::BY_NAME) {
    if (meta_share_) {
      meta_context->write_shared_type_info(buffer, nb::cast(typeinfo));
    } else {
      auto *namespace_bytes =
          nb::cast<MetaStringBytes *>(typeinfo->namespace_bytes());
      auto *typename_bytes =
          nb::cast<MetaStringBytes *>(typeinfo->typename_bytes());
      metastring_resolver_->write_meta_string_bytes(buffer, *namespace_bytes);
      metastring_resolver_->write_meta_string_bytes(buffer, *typename_bytes);
    }
  }
}

void TypeResolver::write_shared_type_meta(BufferView &buffer,
                                          TypeInfo *typeinfo) {
  serialization_context_->meta_context_ptr()->write_shared_type_info(
      buffer, nb::cast(typeinfo));
}

nb::object TypeResolver::read_shared_type_meta(BufferView &buffer) {
  return serialization_context_->meta_context_ptr()->read_shared_type_info(
      buffer);
}

class Fory;

class Serializer {
public:
  Serializer(nb::object fory, nb::object type);

  virtual ~Serializer() = default;

  virtual void write(BufferView &, nb::handle) {
    throw nb::type_error("write method not implemented");
  }

  virtual nb::object read(BufferView &) {
    throw nb::type_error("read method not implemented");
  }

  virtual void xwrite(BufferView &, nb::handle) {
    throw nb::type_error("xwrite method not implemented");
  }

  virtual nb::object xread(BufferView &) {
    throw nb::type_error("xread method not implemented");
  }

  virtual bool support_subclass() const { return false; }

  nb::object fory() const {
    if (fory_ref_.is_none()) {
      return nb::none();
    }
    nb::object obj = fory_ref_();
    if (obj.is_none()) {
      return nb::none();
    }
    return obj;
  }
  nb::object type() const { return type_; }

  bool need_to_write_ref() const { return need_to_write_ref_; }
  void set_need_to_write_ref(bool value) { need_to_write_ref_ = value; }

protected:
  Fory *fory_ptr_{nullptr};
  nb::object fory_ref_{nb::none()};
  nb::object type_;
  bool need_to_write_ref_{false};
};

class XlangCompatibleSerializer : public Serializer {
public:
  using Serializer::Serializer;

  void xwrite(BufferView &buffer, nb::handle value) override {
    write(buffer, value);
  }

  nb::object xread(BufferView &buffer) override { return read(buffer); }
};

class BooleanSerializer : public XlangCompatibleSerializer {
public:
  using XlangCompatibleSerializer::XlangCompatibleSerializer;

  void write(BufferView &buffer, nb::handle value) override {
    buffer.write_int8(PyObject_IsTrue(value.ptr()) ? 1 : 0);
  }

  nb::object read(BufferView &buffer) override {
    return nb::bool_(buffer.read_int8() != 0);
  }
};

class ByteSerializer : public XlangCompatibleSerializer {
public:
  using XlangCompatibleSerializer::XlangCompatibleSerializer;

  void write(BufferView &buffer, nb::handle value) override {
    buffer.write_int8(static_cast<int8_t>(nb::cast<int64_t>(value)));
  }

  nb::object read(BufferView &buffer) override {
    return nb::int_(buffer.read_int8());
  }
};

class Int16Serializer : public XlangCompatibleSerializer {
public:
  using XlangCompatibleSerializer::XlangCompatibleSerializer;

  void write(BufferView &buffer, nb::handle value) override {
    buffer.write_int16(static_cast<int16_t>(nb::cast<int64_t>(value)));
  }

  nb::object read(BufferView &buffer) override {
    return nb::int_(buffer.read_int16());
  }
};

class Int32Serializer : public XlangCompatibleSerializer {
public:
  using XlangCompatibleSerializer::XlangCompatibleSerializer;

  void write(BufferView &buffer, nb::handle value) override {
    buffer.write_var_int32(static_cast<int32_t>(nb::cast<int64_t>(value)));
  }

  nb::object read(BufferView &buffer) override {
    return nb::int_(buffer.read_var_int32());
  }
};

class Int64Serializer : public XlangCompatibleSerializer {
public:
  using XlangCompatibleSerializer::XlangCompatibleSerializer;

  void write(BufferView &buffer, nb::handle value) override {
    buffer.write_var_int64(static_cast<int64_t>(nb::cast<int64_t>(value)));
  }

  nb::object read(BufferView &buffer) override {
    return nb::int_(buffer.read_var_int64());
  }

  void xwrite(BufferView &buffer, nb::handle value) override {
    buffer.write_var_int64(static_cast<int64_t>(nb::cast<int64_t>(value)));
  }

  nb::object xread(BufferView &buffer) override {
    return nb::int_(buffer.read_var_int64());
  }
};

class FixedInt32Serializer : public XlangCompatibleSerializer {
public:
  using XlangCompatibleSerializer::XlangCompatibleSerializer;

  void write(BufferView &buffer, nb::handle value) override {
    buffer.write_int32(static_cast<int32_t>(nb::cast<int64_t>(value)));
  }

  nb::object read(BufferView &buffer) override {
    return nb::int_(buffer.read_int32());
  }
};

class FixedInt64Serializer : public XlangCompatibleSerializer {
public:
  using XlangCompatibleSerializer::XlangCompatibleSerializer;

  void write(BufferView &buffer, nb::handle value) override {
    buffer.write_int64(static_cast<int64_t>(nb::cast<int64_t>(value)));
  }

  nb::object read(BufferView &buffer) override {
    return nb::int_(buffer.read_int64());
  }
};

class Varint32Serializer : public XlangCompatibleSerializer {
public:
  using XlangCompatibleSerializer::XlangCompatibleSerializer;

  void write(BufferView &buffer, nb::handle value) override {
    buffer.write_var_int32(static_cast<int32_t>(nb::cast<int64_t>(value)));
  }

  nb::object read(BufferView &buffer) override {
    return nb::int_(buffer.read_var_int32());
  }
};

class Varint64Serializer : public XlangCompatibleSerializer {
public:
  using XlangCompatibleSerializer::XlangCompatibleSerializer;

  void write(BufferView &buffer, nb::handle value) override {
    buffer.write_var_int64(static_cast<int64_t>(nb::cast<int64_t>(value)));
  }

  nb::object read(BufferView &buffer) override {
    return nb::int_(buffer.read_var_int64());
  }
};

class TaggedInt64Serializer : public XlangCompatibleSerializer {
public:
  using XlangCompatibleSerializer::XlangCompatibleSerializer;

  void write(BufferView &buffer, nb::handle value) override {
    buffer.write_tagged_int64(static_cast<int64_t>(nb::cast<int64_t>(value)));
  }

  nb::object read(BufferView &buffer) override {
    return nb::int_(buffer.read_tagged_int64());
  }
};

class Uint8Serializer : public XlangCompatibleSerializer {
public:
  using XlangCompatibleSerializer::XlangCompatibleSerializer;

  void write(BufferView &buffer, nb::handle value) override {
    buffer.write_uint8(static_cast<uint8_t>(nb::cast<uint64_t>(value)));
  }

  nb::object read(BufferView &buffer) override {
    return nb::int_(buffer.read_uint8());
  }
};

class Uint16Serializer : public XlangCompatibleSerializer {
public:
  using XlangCompatibleSerializer::XlangCompatibleSerializer;

  void write(BufferView &buffer, nb::handle value) override {
    buffer.write_uint16(static_cast<uint16_t>(nb::cast<uint64_t>(value)));
  }

  nb::object read(BufferView &buffer) override {
    return nb::int_(buffer.read_uint16());
  }
};

class Uint32Serializer : public XlangCompatibleSerializer {
public:
  using XlangCompatibleSerializer::XlangCompatibleSerializer;

  void write(BufferView &buffer, nb::handle value) override {
    buffer.write_uint32(static_cast<uint32_t>(nb::cast<uint64_t>(value)));
  }

  nb::object read(BufferView &buffer) override {
    return nb::int_(buffer.read_uint32());
  }
};

class VarUint32Serializer : public XlangCompatibleSerializer {
public:
  using XlangCompatibleSerializer::XlangCompatibleSerializer;

  void write(BufferView &buffer, nb::handle value) override {
    buffer.write_var_uint32(static_cast<uint32_t>(nb::cast<uint64_t>(value)));
  }

  nb::object read(BufferView &buffer) override {
    return nb::int_(buffer.read_var_uint32());
  }
};

class Uint64Serializer : public XlangCompatibleSerializer {
public:
  using XlangCompatibleSerializer::XlangCompatibleSerializer;

  void write(BufferView &buffer, nb::handle value) override {
    buffer.write_uint64(static_cast<uint64_t>(nb::cast<uint64_t>(value)));
  }

  nb::object read(BufferView &buffer) override {
    return nb::int_(buffer.read_uint64());
  }
};

class VarUint64Serializer : public XlangCompatibleSerializer {
public:
  using XlangCompatibleSerializer::XlangCompatibleSerializer;

  void write(BufferView &buffer, nb::handle value) override {
    buffer.write_var_uint64(static_cast<uint64_t>(nb::cast<uint64_t>(value)));
  }

  nb::object read(BufferView &buffer) override {
    return nb::int_(buffer.read_var_uint64());
  }
};

class TaggedUint64Serializer : public XlangCompatibleSerializer {
public:
  using XlangCompatibleSerializer::XlangCompatibleSerializer;

  void write(BufferView &buffer, nb::handle value) override {
    buffer.write_tagged_uint64(
        static_cast<uint64_t>(nb::cast<uint64_t>(value)));
  }

  nb::object read(BufferView &buffer) override {
    return nb::int_(buffer.read_tagged_uint64());
  }
};

class Float32Serializer : public XlangCompatibleSerializer {
public:
  using XlangCompatibleSerializer::XlangCompatibleSerializer;

  void write(BufferView &buffer, nb::handle value) override {
    buffer.write_float(static_cast<float>(nb::cast<double>(value)));
  }

  nb::object read(BufferView &buffer) override {
    return nb::float_(buffer.read_float());
  }
};

class Float64Serializer : public XlangCompatibleSerializer {
public:
  using XlangCompatibleSerializer::XlangCompatibleSerializer;

  void write(BufferView &buffer, nb::handle value) override {
    buffer.write_double(static_cast<double>(nb::cast<double>(value)));
  }

  nb::object read(BufferView &buffer) override {
    return nb::float_(buffer.read_double());
  }
};

class StringSerializer : public XlangCompatibleSerializer {
public:
  StringSerializer(nb::object fory, nb::object type, bool track_ref)
      : XlangCompatibleSerializer(std::move(fory), std::move(type)) {
    set_need_to_write_ref(track_ref);
  }

  void write(BufferView &buffer, nb::handle value) override {
    buffer.write_string(value);
  }

  nb::object read(BufferView &buffer) override { return buffer.read_string(); }
};

class DateSerializer : public XlangCompatibleSerializer {
public:
  using XlangCompatibleSerializer::XlangCompatibleSerializer;

  void write(BufferView &buffer, nb::handle value) override {
    if (Py_TYPE(value.ptr()) !=
        reinterpret_cast<PyTypeObject *>(g_date_class)) {
      throw nb::type_error("value should be datetime.date");
    }
    nb::object date_class = nb::borrow(g_date_class);
    nb::object base_date = date_class(1970, 1, 1);
    nb::object delta = nb::borrow(value) - base_date;
    int32_t days = nb::cast<int32_t>(delta.attr("days"));
    buffer.write_int32(days);
  }

  nb::object read(BufferView &buffer) override {
    int32_t days = buffer.read_int32();
    nb::object date_class = nb::borrow(g_date_class);
    nb::object base_date = date_class(1970, 1, 1);
    int base_ordinal = nb::cast<int>(base_date.attr("toordinal")());
    return date_class.attr("fromordinal")(base_ordinal + days);
  }
};

class TimestampSerializer : public XlangCompatibleSerializer {
public:
  explicit TimestampSerializer(nb::object fory, nb::object type)
      : XlangCompatibleSerializer(std::move(fory), std::move(type)) {
    nb::object platform_module = nb::borrow(g_platform_module);
    win_platform_ =
        nb::cast<std::string>(platform_module.attr("system")()) == "Windows";
  }

  void write(BufferView &buffer, nb::handle value) override {
    if (Py_TYPE(value.ptr()) !=
        reinterpret_cast<PyTypeObject *>(g_datetime_class)) {
      throw nb::type_error("value should be datetime.datetime");
    }
    auto [seconds, nanos] = get_timestamp(nb::borrow(value));
    buffer.write_int64(seconds);
    buffer.write_uint32(nanos);
  }

  nb::object read(BufferView &buffer) override {
    int64_t seconds = buffer.read_int64();
    uint32_t nanos = buffer.read_uint32();
    double ts = static_cast<double>(seconds) +
                static_cast<double>(nanos) / 1000000000.0;
    return nb::borrow(g_datetime_class).attr("fromtimestamp")(ts);
  }

private:
  std::pair<int64_t, uint32_t> get_timestamp(nb::object value) {
    double seconds_offset = 0.0;
    if (win_platform_ && value.attr("tzinfo").is_none()) {
      nb::object time_module = nb::borrow(g_time_module);
      bool is_dst =
          nb::cast<bool>(time_module.attr("daylight")) &&
          nb::cast<int>(time_module.attr("localtime")().attr("tm_isdst")) > 0;
      seconds_offset =
          nb::cast<double>(time_module.attr(is_dst ? "altzone" : "timezone"));
      value = value.attr("replace")("tzinfo"_a = nb::borrow(g_timezone_utc));
    }
    double micros =
        (nb::cast<double>(value.attr("timestamp")()) + seconds_offset) *
        1000000.0;
    int64_t micros_int = static_cast<int64_t>(micros);
    int64_t seconds;
    int64_t micros_rem;
    if (micros_int >= 0) {
      seconds = micros_int / 1000000;
      micros_rem = micros_int % 1000000;
    } else {
      seconds = -((-micros_int) / 1000000);
      micros_rem = micros_int - seconds * 1000000;
    }
    if (micros_rem < 0) {
      seconds -= 1;
      micros_rem += 1000000;
    }
    return {seconds, static_cast<uint32_t>(micros_rem * 1000)};
  }

  bool win_platform_{false};
};

class EnumSerializer : public Serializer {
public:
  using Serializer::Serializer;

  bool support_subclass() const override { return true; }

  void write(BufferView &buffer, nb::handle value) override {
    nb::object name = nb::borrow(value).attr("name");
    buffer.write_string(name);
  }

  nb::object read(BufferView &buffer) override {
    nb::str name = nb::cast<nb::str>(buffer.read_string());
    return type_.attr(name.c_str());
  }

  void xwrite(BufferView &buffer, nb::handle value) override {
    buffer.write_var_uint32(
        nb::cast<uint32_t>(nb::borrow(value).attr("value")));
  }

  nb::object xread(BufferView &buffer) override {
    uint32_t ordinal = buffer.read_var_uint32();
    return type_(ordinal);
  }
};

class SliceSerializer : public Serializer {
public:
  using Serializer::Serializer;

  void write(BufferView &buffer, nb::handle value) override {
    nb::object slice_obj = nb::borrow(value);
    nb::object start = slice_obj.attr("start");
    nb::object stop = slice_obj.attr("stop");
    nb::object step = slice_obj.attr("step");
    write_slice_part(buffer, start);
    write_slice_part(buffer, stop);
    write_slice_part(buffer, step);
  }

  nb::object read(BufferView &buffer) override {
    nb::object start = read_slice_part(buffer);
    nb::object stop = read_slice_part(buffer);
    nb::object step = read_slice_part(buffer);
    return nb::slice(start, stop, step);
  }

  void xwrite(BufferView &, nb::handle) override {
    throw nb::type_error("xwrite not supported");
  }

  nb::object xread(BufferView &) override {
    throw nb::type_error("xread not supported");
  }

private:
  void write_slice_part(BufferView &buffer, nb::handle part);
  nb::object read_slice_part(BufferView &buffer);
};

} // namespace
namespace {

constexpr int8_t kCollDefaultFlag = 0b0;
constexpr int8_t kCollTrackingRef = 0b1;
constexpr int8_t kCollHasNull = 0b10;
constexpr int8_t kCollIsDeclElementType = 0b100;
constexpr int8_t kCollIsSameType = 0b1000;

constexpr int8_t kCollDeclSameTypeTrackingRef =
    kCollIsDeclElementType | kCollIsSameType | kCollTrackingRef;
constexpr int8_t kCollDeclSameTypeNotTrackingRef =
    kCollIsDeclElementType | kCollIsSameType;
constexpr int8_t kCollDeclSameTypeHasNull =
    kCollIsDeclElementType | kCollIsSameType | kCollHasNull;
constexpr int8_t kCollDeclSameTypeNotHasNull =
    kCollIsDeclElementType | kCollIsSameType;

inline bool is_python_serializer(const nb::object &serializer_obj) {
  if (serializer_obj.is_none()) {
    return false;
  }
  return nb::detail::nb_inst_python_derived(serializer_obj.ptr());
}

inline void serializer_write(Serializer *serializer,
                             const nb::object &serializer_obj,
                             BufferView &buffer, nb::handle value,
                             bool is_python) {
  if (is_python) {
    serializer_obj.attr("write")(buffer.object(), nb::borrow(value));
  } else {
    serializer->write(buffer, value);
  }
}

inline void serializer_write(Serializer *serializer,
                             const nb::object &serializer_obj,
                             BufferView &buffer, nb::handle value) {
  serializer_write(serializer, serializer_obj, buffer, value,
                   is_python_serializer(serializer_obj));
}

inline void serializer_xwrite(Serializer *serializer,
                              const nb::object &serializer_obj,
                              BufferView &buffer, nb::handle value,
                              bool is_python) {
  if (is_python) {
    serializer_obj.attr("xwrite")(buffer.object(), nb::borrow(value));
  } else {
    serializer->xwrite(buffer, value);
  }
}

inline void serializer_xwrite(Serializer *serializer,
                              const nb::object &serializer_obj,
                              BufferView &buffer, nb::handle value) {
  serializer_xwrite(serializer, serializer_obj, buffer, value,
                    is_python_serializer(serializer_obj));
}

inline nb::object serializer_read(Serializer *serializer,
                                  const nb::object &serializer_obj,
                                  BufferView &buffer, bool is_python) {
  if (is_python) {
    return serializer_obj.attr("read")(buffer.object());
  }
  return serializer->read(buffer);
}

inline nb::object serializer_read(Serializer *serializer,
                                  const nb::object &serializer_obj,
                                  BufferView &buffer) {
  return serializer_read(serializer, serializer_obj, buffer,
                         is_python_serializer(serializer_obj));
}

inline nb::object serializer_xread(Serializer *serializer,
                                   const nb::object &serializer_obj,
                                   BufferView &buffer, bool is_python) {
  if (is_python) {
    return serializer_obj.attr("xread")(buffer.object());
  }
  return serializer->xread(buffer);
}

inline nb::object serializer_xread(Serializer *serializer,
                                   const nb::object &serializer_obj,
                                   BufferView &buffer) {
  return serializer_xread(serializer, serializer_obj, buffer,
                          is_python_serializer(serializer_obj));
}

class Fory {
public:
  Fory() = default;
  ~Fory() { close(); }

  void init(nb::object self_obj, bool xlang, bool ref, bool strict,
            nb::object policy, bool compatible, int32_t max_depth,
            bool field_nullable, nb::object meta_compressor) {
    if (!closed_) {
      close();
    }
    closed_ = false;
    nb::object self_ref = self_obj;
    xlang_ = xlang;
    bool force_strict =
        nb::cast<bool>(nb::module_::import_("pyfory._fory")
                           .attr("_ENABLE_TYPE_REGISTRATION_FORCIBLY"));
    strict_ = force_strict || strict;
    policy_ = policy.is_none()
                  ? nb::module_::import_("pyfory.policy").attr("DEFAULT_POLICY")
                  : policy;
    compatible_ = compatible;
    track_ref_ = ref;
    ref_resolver_ = MapRefResolver(ref);
    field_nullable_ = field_nullable && !xlang_;
    metastring_resolver_ = MetaStringResolver();
    type_resolver_ =
        std::make_unique<TypeResolver>(self_ref, compatible, meta_compressor);
    serialization_context_ =
        std::make_unique<SerializationContext>(self_ref, compatible);
    type_resolver_->set_serialization_context(serialization_context_.get());
    type_resolver_->initialize();
    nb::object buffer_cls =
        g_buffer_class == nullptr
            ? nb::module_::import_("pyfory.buffer").attr("Buffer")
            : nb::borrow(g_buffer_class);
    buffer_ = buffer_cls.attr("allocate")(32);
    buffer_callback_ = nb::none();
    unsupported_callback_ = nb::none();
    buffers_iter_ = nb::none();
    unsupported_objects_iter_ = nb::none();
    is_peer_out_of_band_enabled_ = false;
    depth_ = 0;
    max_depth_ = max_depth;
    track_fory_instance(self_obj);
  }

  bool xlang() const { return xlang_; }
  bool track_ref() const { return track_ref_; }
  bool strict() const { return strict_; }
  bool compatible() const { return compatible_; }
  bool field_nullable() const { return field_nullable_; }
  nb::object policy() const { return policy_; }

  MapRefResolver *ref_resolver() { return &ref_resolver_; }
  TypeResolver *type_resolver() { return type_resolver_.get(); }
  MetaStringResolver *metastring_resolver() { return &metastring_resolver_; }
  SerializationContext *serialization_context() {
    return serialization_context_.get();
  }

  void register_serializer(nb::object cls, nb::object serializer) {
    type_resolver_->register_serializer(cls, serializer);
  }

  void register_type(nb::object cls, int type_id, nb::object ns,
                     nb::object typename_, nb::object serializer) {
    type_resolver_->register_type(cls, type_id, ns, typename_, serializer);
  }

  void register_union(nb::object cls, int type_id, nb::object ns,
                      nb::object typename_, nb::object serializer) {
    type_resolver_->register_union(cls, type_id, ns, typename_, serializer);
  }

  nb::object dumps(nb::handle obj, nb::object buffer,
                   nb::object buffer_callback,
                   nb::object unsupported_callback) {
    return serialize(obj, buffer, buffer_callback, unsupported_callback);
  }

  nb::object loads(nb::handle buffer, nb::object buffers,
                   nb::object unsupported_objects) {
    return deserialize(buffer, buffers, unsupported_objects);
  }

  nb::object serialize(nb::handle obj, nb::object buffer,
                       nb::object buffer_callback,
                       nb::object unsupported_callback) {
    try {
      nb::object result = serialize_internal(obj, buffer, buffer_callback,
                                             unsupported_callback);
      reset_write();
      return result;
    } catch (...) {
      reset_write();
      throw;
    }
  }

  nb::object deserialize(nb::handle buffer, nb::object buffers,
                         nb::object unsupported_objects) {
    nb::object buffer_obj = nb::borrow(buffer.ptr());
    try {
      if (PyBytes_Check(buffer.ptr())) {
        nb::object buffer_cls =
            g_buffer_class == nullptr
                ? nb::module_::import_("pyfory.buffer").attr("Buffer")
                : nb::borrow(g_buffer_class);
        buffer_obj = buffer_cls(buffer);
      }
      nb::object result =
          deserialize_internal(buffer_obj, buffers, unsupported_objects);
      reset_read();
      return result;
    } catch (...) {
      reset_read();
      throw;
    }
  }

  void write_ref(BufferView &buffer, nb::handle obj) {
    if (PyUnicode_CheckExact(obj.ptr())) {
      buffer.write_int16(static_cast<int16_t>(g_not_null_string_flag));
      buffer.write_string(obj);
      return;
    }
    if (PyLong_CheckExact(obj.ptr())) {
      buffer.write_int16(static_cast<int16_t>(g_not_null_int64_flag));
      buffer.write_var_int64(nb::cast<int64_t>(obj));
      return;
    }
    if (PyBool_Check(obj.ptr())) {
      buffer.write_int16(static_cast<int16_t>(g_not_null_bool_flag));
      buffer.write_int8(PyObject_IsTrue(obj.ptr()) ? 1 : 0);
      return;
    }
    if (PyFloat_CheckExact(obj.ptr())) {
      buffer.write_int16(static_cast<int16_t>(g_not_null_float64_flag));
      buffer.write_double(nb::cast<double>(obj));
      return;
    }
    if (ref_resolver_.write_ref_or_null(buffer, obj)) {
      return;
    }
    TypeInfo *typeinfo =
        type_resolver_->get_type_info(nb::borrow(Py_TYPE(obj.ptr())), true);
    type_resolver_->write_type_info(buffer, typeinfo);
    nb::object serializer_obj = typeinfo->serializer();
    Serializer *serializer = nb::cast<Serializer *>(serializer_obj);
    serializer_write(serializer, serializer_obj, buffer, obj);
  }

  void write_no_ref(BufferView &buffer, nb::handle obj) {
    if (PyUnicode_CheckExact(obj.ptr())) {
      buffer.write_var_uint32(static_cast<uint32_t>(g_string_type_id));
      buffer.write_string(obj);
      return;
    }
    if (PyLong_CheckExact(obj.ptr())) {
      buffer.write_var_uint32(static_cast<uint32_t>(g_int64_type_id));
      buffer.write_var_int64(nb::cast<int64_t>(obj));
      return;
    }
    if (PyBool_Check(obj.ptr())) {
      buffer.write_var_uint32(static_cast<uint32_t>(g_bool_type_id));
      buffer.write_int8(PyObject_IsTrue(obj.ptr()) ? 1 : 0);
      return;
    }
    if (PyFloat_CheckExact(obj.ptr())) {
      buffer.write_var_uint32(static_cast<uint32_t>(g_float64_type_id));
      buffer.write_double(nb::cast<double>(obj));
      return;
    }
    TypeInfo *typeinfo =
        type_resolver_->get_type_info(nb::borrow(Py_TYPE(obj.ptr())), true);
    type_resolver_->write_type_info(buffer, typeinfo);
    nb::object serializer_obj = typeinfo->serializer();
    Serializer *serializer = nb::cast<Serializer *>(serializer_obj);
    serializer_write(serializer, serializer_obj, buffer, obj);
  }

  void xwrite_ref(BufferView &buffer, nb::handle obj, Serializer *serializer,
                  nb::object serializer_obj) {
    if (serializer == nullptr || serializer->need_to_write_ref()) {
      if (!ref_resolver_.write_ref_or_null(buffer, obj)) {
        xwrite_no_ref(buffer, obj, serializer, serializer_obj);
      }
      return;
    }
    if (obj.is_none()) {
      buffer.write_int8(kNullFlag);
    } else {
      buffer.write_int8(kNotNullValueFlag);
      xwrite_no_ref(buffer, obj, serializer, serializer_obj);
    }
  }

  void xwrite_no_ref(BufferView &buffer, nb::handle obj, Serializer *serializer,
                     nb::object serializer_obj) {
    if (serializer == nullptr) {
      TypeInfo *typeinfo =
          type_resolver_->get_type_info(nb::borrow(Py_TYPE(obj.ptr())), true);
      type_resolver_->write_type_info(buffer, typeinfo);
      serializer_obj = typeinfo->serializer();
      serializer = nb::cast<Serializer *>(serializer_obj);
    }
    serializer_xwrite(serializer, serializer_obj, buffer, obj);
  }

  nb::object read_ref(BufferView &buffer) {
    int32_t ref_id = ref_resolver_.try_preserve_ref_id(buffer);
    if (ref_id < kNotNullValueFlag) {
      return ref_resolver_.get_read_object();
    }
    TypeInfo *typeinfo = type_resolver_->read_type_info(buffer);
    nb::object cls = typeinfo->cls();
    if (cls.ptr() == reinterpret_cast<PyObject *>(&PyUnicode_Type)) {
      return buffer.read_string();
    }
    if (cls.ptr() == reinterpret_cast<PyObject *>(&PyLong_Type)) {
      return nb::int_(buffer.read_var_int64());
    }
    if (cls.ptr() == reinterpret_cast<PyObject *>(&PyBool_Type)) {
      return nb::bool_(buffer.read_int8() != 0);
    }
    if (cls.ptr() == reinterpret_cast<PyObject *>(&PyFloat_Type)) {
      return nb::float_(buffer.read_double());
    }
    inc_depth();
    nb::object serializer_obj = typeinfo->serializer();
    Serializer *serializer = nb::cast<Serializer *>(serializer_obj);
    nb::object obj = serializer_read(serializer, serializer_obj, buffer);
    dec_depth();
    ref_resolver_.set_read_object(ref_id, obj);
    return obj;
  }

  nb::object read_no_ref(BufferView &buffer) {
    TypeInfo *typeinfo = type_resolver_->read_type_info(buffer);
    nb::object cls = typeinfo->cls();
    if (cls.ptr() == reinterpret_cast<PyObject *>(&PyUnicode_Type)) {
      return buffer.read_string();
    }
    if (cls.ptr() == reinterpret_cast<PyObject *>(&PyLong_Type)) {
      return nb::int_(buffer.read_var_int64());
    }
    if (cls.ptr() == reinterpret_cast<PyObject *>(&PyBool_Type)) {
      return nb::bool_(buffer.read_int8() != 0);
    }
    if (cls.ptr() == reinterpret_cast<PyObject *>(&PyFloat_Type)) {
      return nb::float_(buffer.read_double());
    }
    inc_depth();
    nb::object serializer_obj = typeinfo->serializer();
    Serializer *serializer = nb::cast<Serializer *>(serializer_obj);
    nb::object obj = serializer_read(serializer, serializer_obj, buffer);
    dec_depth();
    return obj;
  }

  nb::object xread_ref(BufferView &buffer, Serializer *serializer,
                       nb::object serializer_obj) {
    if (serializer == nullptr || serializer->need_to_write_ref()) {
      int32_t ref_id = ref_resolver_.try_preserve_ref_id(buffer);
      if (ref_id >= kNotNullValueFlag) {
        nb::object obj =
            xread_no_ref_internal(buffer, serializer, serializer_obj);
        ref_resolver_.set_read_object(ref_id, obj);
        return obj;
      }
      return ref_resolver_.get_read_object();
    }
    int8_t head_flag = buffer.read_int8();
    if (head_flag == kNullFlag) {
      return nb::none();
    }
    return xread_no_ref(buffer, serializer, serializer_obj);
  }

  nb::object xread_no_ref(BufferView &buffer, Serializer *serializer,
                          nb::object serializer_obj) {
    if (serializer == nullptr) {
      serializer_obj = type_resolver_->read_type_info(buffer)->serializer();
      serializer = nb::cast<Serializer *>(serializer_obj);
    }
    if (ref_resolver_.track_ref()) {
      ref_resolver_.push_non_ref_marker();
    }
    return xread_no_ref_internal(buffer, serializer, serializer_obj);
  }

  nb::object xread_no_ref_internal(BufferView &buffer, Serializer *serializer,
                                   nb::object serializer_obj) {
    if (serializer == nullptr) {
      serializer_obj = type_resolver_->read_type_info(buffer)->serializer();
      serializer = nb::cast<Serializer *>(serializer_obj);
    }
    inc_depth();
    nb::object obj = serializer_xread(serializer, serializer_obj, buffer);
    dec_depth();
    return obj;
  }

  void inc_depth() {
    depth_ += 1;
    if (depth_ > max_depth_) {
      throw_depth_limit_exceeded_exception();
    }
  }

  void dec_depth() { depth_ -= 1; }

  void throw_depth_limit_exceeded_exception() {
    throw nb::builtin_exception(
        nb::exception_type::runtime_error,
        ("Read depth exceed max depth: " + std::to_string(depth_) +
         ", the deserialization data may be malicious. If it's not malicious, "
         "please increase max read depth by Fory(..., max_depth=...)")
            .c_str());
  }

  void write_buffer_object(BufferView &buffer, nb::handle buffer_object) {
    auto read_total_bytes = [&buffer_object]() -> int32_t {
      nb::object size_obj = buffer_object.attr("total_bytes")();
      nb::object long_obj = nb::steal(PyNumber_Long(size_obj.ptr()));
      if (!long_obj) {
        throw nb::python_error();
      }
      long long size_value = PyLong_AsLongLong(long_obj.ptr());
      if (PyErr_Occurred()) {
        throw nb::python_error();
      }
      if (size_value < 0 ||
          size_value >
              static_cast<long long>(std::numeric_limits<int32_t>::max())) {
        throw nb::value_error("buffer object size out of range");
      }
      return static_cast<int32_t>(size_value);
    };
    auto write_in_band = [&buffer, &buffer_object, &read_total_bytes]() {
      int32_t size = read_total_bytes();
      buffer.write_var_uint32(static_cast<uint32_t>(size));
      int32_t writer_index = buffer.get_writer_index();
      buffer.ensure(writer_index + size);
      nb::object buf = buffer.slice(writer_index, size);
      buffer_object.attr("write_to")(buf);
      buffer.set_writer_index(writer_index + size);
    };
    if (buffer_callback_.is_none()) {
      write_in_band();
      return;
    }
    nb::object callback_result = buffer_callback_(buffer_object);
    int truth = PyObject_IsTrue(callback_result.ptr());
    if (truth < 0) {
      throw nb::python_error();
    }
    if (truth != 0) {
      buffer.write_int8(1);
      write_in_band();
    } else {
      buffer.write_int8(0);
    }
  }

  nb::object read_buffer_object(BufferView &buffer) {
    if (!is_peer_out_of_band_enabled_) {
      int32_t size = static_cast<int32_t>(buffer.read_var_uint32());
      int32_t reader_index = buffer.get_reader_index();
      nb::object buf = buffer.slice(reader_index, size);
      buffer.set_reader_index(reader_index + size);
      return buf;
    }
    bool in_band = buffer.read_int8() != 0;
    if (!in_band) {
      if (buffers_iter_.is_none()) {
        throw nb::builtin_exception(nb::exception_type::runtime_error,
                                    "buffers iterator not set");
      }
      return nb::module_::import_("builtins").attr("next")(buffers_iter_);
    }
    int32_t size = static_cast<int32_t>(buffer.read_var_uint32());
    int32_t reader_index = buffer.get_reader_index();
    nb::object buf = buffer.slice(reader_index, size);
    buffer.set_reader_index(reader_index + size);
    return buf;
  }

  void handle_unsupported_write(BufferView &buffer, nb::handle obj) {
    if (unsupported_callback_.is_none() ||
        nb::cast<bool>(unsupported_callback_(obj))) {
      throw nb::type_error("Unsupported type");
    }
  }

  nb::object handle_unsupported_read(BufferView &) {
    if (unsupported_objects_iter_.is_none()) {
      throw nb::builtin_exception(nb::exception_type::runtime_error,
                                  "unsupported objects iterator not set");
    }
    return nb::module_::import_("builtins")
        .attr("next")(unsupported_objects_iter_);
  }

  void write_ref_pyobject(BufferView &buffer, nb::handle value,
                          TypeInfo *typeinfo) {
    if (ref_resolver_.write_ref_or_null(buffer, value)) {
      return;
    }
    if (typeinfo == nullptr) {
      typeinfo =
          type_resolver_->get_type_info(nb::borrow(Py_TYPE(value.ptr())), true);
    }
    type_resolver_->write_type_info(buffer, typeinfo);
    nb::object serializer_obj = typeinfo->serializer();
    Serializer *serializer = nb::cast<Serializer *>(serializer_obj);
    serializer_write(serializer, serializer_obj, buffer, value);
  }

  nb::object read_ref_pyobject(BufferView &buffer) {
    int32_t ref_id = ref_resolver_.try_preserve_ref_id(buffer);
    if (ref_id < kNotNullValueFlag) {
      return ref_resolver_.get_read_object();
    }
    TypeInfo *typeinfo = type_resolver_->read_type_info(buffer);
    inc_depth();
    nb::object serializer_obj = typeinfo->serializer();
    Serializer *serializer = nb::cast<Serializer *>(serializer_obj);
    nb::object obj = serializer_read(serializer, serializer_obj, buffer);
    dec_depth();
    ref_resolver_.set_read_object(ref_id, obj);
    return obj;
  }

  void reset_write() {
    depth_ = 0;
    ref_resolver_.reset_write();
    if (type_resolver_ != nullptr) {
      type_resolver_->reset_write();
    }
    metastring_resolver_.reset_write();
    if (serialization_context_ != nullptr) {
      serialization_context_->reset_write();
    }
    unsupported_callback_ = nb::none();
  }

  void reset_read() {
    depth_ = 0;
    ref_resolver_.reset_read();
    if (type_resolver_ != nullptr) {
      type_resolver_->reset_read();
    }
    metastring_resolver_.reset_read();
    if (serialization_context_ != nullptr) {
      serialization_context_->reset_read();
    }
    buffers_iter_ = nb::none();
    unsupported_objects_iter_ = nb::none();
    is_peer_out_of_band_enabled_ = false;
  }

  void reset() {
    reset_write();
    reset_read();
  }

  void close() {
    if (closed_) {
      return;
    }
    closed_ = true;
    reset();
    if (serialization_context_ != nullptr) {
      serialization_context_->clear();
      serialization_context_.reset();
    }
    if (type_resolver_ != nullptr) {
      type_resolver_->clear();
      type_resolver_.reset();
    }
    metastring_resolver_.clear();
    policy_ = nb::none();
    buffer_ = nb::none();
    buffer_callback_ = nb::none();
    buffers_iter_ = nb::none();
    unsupported_callback_ = nb::none();
    unsupported_objects_iter_ = nb::none();
    is_peer_out_of_band_enabled_ = false;
  }

  nb::object buffer() const { return buffer_; }
  nb::object buffer_callback() const { return buffer_callback_; }
  bool is_peer_out_of_band_enabled() const {
    return is_peer_out_of_band_enabled_;
  }

private:
  nb::object serialize_internal(nb::handle obj, nb::object buffer,
                                nb::object buffer_callback,
                                nb::object unsupported_callback) {
    if (depth_ != 0) {
      throw nb::builtin_exception(
          nb::exception_type::runtime_error,
          "Nested serialization should use "
          "write_ref/write_no_ref/xwrite_ref/xwrite_no_ref.");
    }
    depth_ += 1;
    buffer_callback_ = buffer_callback;
    unsupported_callback_ = unsupported_callback;
    bool using_internal = false;
    if (buffer.is_none()) {
      buffer = buffer_;
      using_internal = true;
    }
    BufferView buf(buffer);
    if (using_internal) {
      buf.set_writer_index(0);
    }
    int32_t mask_index = buf.get_writer_index();
    buf.grow(1);
    buf.set_writer_index(mask_index + 1);
    if (obj.is_none()) {
      fory::util::set_bit(buf.raw()->data() + mask_index, 0);
    } else {
      fory::util::clear_bit(buf.raw()->data() + mask_index, 0);
    }
    if (xlang_) {
      fory::util::set_bit(buf.raw()->data() + mask_index, 1);
    } else {
      fory::util::clear_bit(buf.raw()->data() + mask_index, 1);
    }
    if (!buffer_callback_.is_none()) {
      fory::util::set_bit(buf.raw()->data() + mask_index, 2);
    } else {
      fory::util::clear_bit(buf.raw()->data() + mask_index, 2);
    }
    if (!xlang_) {
      write_ref(buf, obj);
    } else {
      xwrite_ref(buf, obj, nullptr, nb::none());
    }
    if (!buffer.is(buffer_)) {
      return buffer;
    }
    return buf.to_bytes(0, buf.get_writer_index());
  }

  nb::object deserialize_internal(nb::handle buffer, nb::object buffers,
                                  nb::object unsupported_objects) {
    if (depth_ != 0) {
      throw nb::builtin_exception(
          nb::exception_type::runtime_error,
          "Nested deserialization should use "
          "read_ref/read_no_ref/xread_ref/xread_no_ref.");
    }
    depth_ += 1;
    if (!unsupported_objects.is_none()) {
      unsupported_objects_iter_ = nb::iter(unsupported_objects);
    }
    BufferView buf(buffer);
    int32_t reader_index = buf.get_reader_index();
    buf.set_reader_index(reader_index + 1);
    if (fory::util::get_bit(buf.raw()->data() + reader_index, 0)) {
      return nb::none();
    }
    bool is_target_xlang =
        fory::util::get_bit(buf.raw()->data() + reader_index, 1);
    is_peer_out_of_band_enabled_ =
        fory::util::get_bit(buf.raw()->data() + reader_index, 2);
    if (is_peer_out_of_band_enabled_) {
      if (buffers.is_none()) {
        throw nb::builtin_exception(
            nb::exception_type::runtime_error,
            "buffers shouldn't be null when stream uses buffer_callback");
      }
      buffers_iter_ = nb::iter(buffers);
    } else if (!buffers.is_none()) {
      throw nb::builtin_exception(
          nb::exception_type::runtime_error,
          "buffers should be null when stream is in-band");
    }
    if (!is_target_xlang) {
      return read_ref(buf);
    }
    return xread_ref(buf, nullptr, nb::none());
  }

  bool xlang_{false};
  bool track_ref_{false};
  bool strict_{true};
  bool compatible_{false};
  bool field_nullable_{false};
  nb::object policy_{nb::none()};
  MapRefResolver ref_resolver_{false};
  std::unique_ptr<TypeResolver> type_resolver_;
  MetaStringResolver metastring_resolver_{};
  std::unique_ptr<SerializationContext> serialization_context_;
  nb::object buffer_{nb::none()};
  nb::object buffer_callback_{nb::none()};
  nb::object buffers_iter_{nb::none()};
  nb::object unsupported_callback_{nb::none()};
  nb::object unsupported_objects_iter_{nb::none()};
  bool is_peer_out_of_band_enabled_{false};
  int32_t max_depth_{50};
  int32_t depth_{0};
  bool closed_{true};
};

TypeResolver::TypeResolver(nb::object fory, bool meta_share,
                           nb::object meta_compressor)
    : meta_share_(meta_share) {
  Fory *fory_ptr = nb::cast<Fory *>(fory);
  metastring_resolver_ = fory_ptr->metastring_resolver();
  xlang_ = fory_ptr->xlang();
  nb::object registry_module = nb::module_::import_("pyfory.registry");
  nb::object resolver_cls = registry_module.attr("TypeResolver");
  if (meta_compressor.is_none()) {
    resolver_ = resolver_cls(fory, meta_share);
  } else {
    resolver_ = resolver_cls(fory, meta_share, meta_compressor);
  }
  nb::object context_obj = fory.attr("serialization_context");
  if (!context_obj.is_none()) {
    serialization_context_ = nb::cast<SerializationContext *>(context_obj);
  }
}

MetaContext::MetaContext(nb::object fory) {
  auto *fory_type = reinterpret_cast<PyTypeObject *>(nb::type<Fory>().ptr());
  if (PyObject_TypeCheck(fory.ptr(), fory_type)) {
    type_resolver_ = nb::cast<Fory *>(fory)->type_resolver();
  } else {
    type_resolver_ref_ = nb::borrow(g_weakref_ref)(fory.attr("type_resolver"));
  }
}

SerializationContext::SerializationContext(nb::object fory,
                                           bool scoped_meta_share_enabled)
    : objects_(nb::dict()),
      scoped_meta_share_enabled_(scoped_meta_share_enabled) {
  if (scoped_meta_share_enabled_) {
    auto *fory_type = reinterpret_cast<PyTypeObject *>(nb::type<Fory>().ptr());
    if (PyObject_TypeCheck(fory.ptr(), fory_type)) {
      Fory *fory_ptr = nb::cast<Fory *>(fory);
      meta_context_ref_ = nb::cast(MetaContext(fory_ptr->type_resolver()));
    } else {
      meta_context_ref_ = nb::cast(MetaContext(fory));
    }
    meta_context_ptr_ = nb::cast<MetaContext *>(meta_context_ref_);
  }
}

Serializer::Serializer(nb::object fory, nb::object type)
    : type_(std::move(type)) {
  if (fory.is_none()) {
    fory_ref_ = nb::none();
    need_to_write_ref_ = false;
    return;
  }
  if (g_weakref_ref == nullptr) {
    nb::object weakref_ref = nb::module_::import_("weakref").attr("ref");
    g_weakref_ref = weakref_ref.ptr();
  }
  fory_ref_ = nb::borrow(g_weakref_ref)(fory);
  fory_ptr_ = nb::cast<Fory *>(fory);
  bool track_ref = fory_ptr_->track_ref();
  nb::object is_primitive_type = nb::borrow(g_is_primitive_type);
  bool is_primitive = nb::cast<bool>(is_primitive_type(type_));
  need_to_write_ref_ = track_ref && !is_primitive;
}

void SliceSerializer::write_slice_part(BufferView &buffer, nb::handle part) {
  if (PyLong_CheckExact(part.ptr())) {
    buffer.write_int16(static_cast<int16_t>(g_not_null_int64_flag));
    buffer.write_var_int64(nb::cast<int64_t>(part));
    return;
  }
  if (part.is_none()) {
    buffer.write_int8(kNullFlag);
    return;
  }
  buffer.write_int8(kNotNullValueFlag);
  fory_ptr_->write_no_ref(buffer, part);
}

nb::object SliceSerializer::read_slice_part(BufferView &buffer) {
  if (buffer.read_int8() == kNullFlag) {
    return nb::none();
  }
  return fory_ptr_->read_no_ref(buffer);
}

template <typename AddFn, typename ReadFn>
inline void read_sequence(int64_t len, AddFn add, ReadFn read) {
  for (int64_t i = 0; i < len; ++i) {
    add(i, read());
  }
}

class CollectionSerializer : public Serializer {
public:
  CollectionSerializer(nb::object fory, nb::object type,
                       nb::object elem_serializer,
                       std::optional<bool> elem_tracking_ref)
      : Serializer(fory, type), type_resolver_(fory_ptr_->type_resolver()),
        ref_resolver_(fory_ptr_->ref_resolver()) {
    if (!elem_serializer.is_none()) {
      elem_serializer_obj_ = std::move(elem_serializer);
      elem_serializer_ = nb::cast<Serializer *>(elem_serializer_obj_);
      elem_type_ = elem_serializer_->type();
      elem_type_info_ = type_resolver_->get_type_info(elem_type_, true);
      elem_tracking_ref_ = elem_serializer_->need_to_write_ref();
      if (elem_tracking_ref.has_value()) {
        elem_tracking_ref_ = elem_tracking_ref.value();
      }
    } else {
      elem_type_ = nb::none();
      elem_type_info_ = type_resolver_->get_type_info(nb::none(), true);
      elem_tracking_ref_ = -1;
    }
  }

  std::pair<int8_t, TypeInfo *> write_header(BufferView &buffer,
                                             nb::handle value) {
    int8_t collect_flag = kCollDefaultFlag;
    nb::object elem_type = elem_type_;
    TypeInfo *elem_type_info = elem_type_info_;
    bool has_null = false;
    bool has_same_type = true;
    PyObject *iterable = value.ptr();
    if (elem_type.is_none()) {
      nb::object iter = nb::iter(value);
      for (nb::handle item : iter) {
        if (!has_null && item.is_none()) {
          has_null = true;
          continue;
        }
        if (elem_type.is_none()) {
          elem_type = nb::borrow(Py_TYPE(item.ptr()));
        } else if (has_same_type &&
                   Py_TYPE(item.ptr()) !=
                       reinterpret_cast<PyTypeObject *>(elem_type.ptr())) {
          has_same_type = false;
        }
      }
      if (has_same_type) {
        collect_flag |= kCollIsSameType;
        elem_type_info = type_resolver_->get_type_info(elem_type, true);
      }
    } else {
      collect_flag |= kCollIsDeclElementType | kCollIsSameType;
      nb::object iter = nb::iter(value);
      for (nb::handle item : iter) {
        if (item.is_none()) {
          has_null = true;
          break;
        }
      }
    }
    if (has_null) {
      collect_flag |= kCollHasNull;
    }
    if (fory_ptr_->track_ref()) {
      if (elem_tracking_ref_ == 1) {
        collect_flag |= kCollTrackingRef;
      } else if (elem_tracking_ref_ == -1) {
        bool need_ref = false;
        if (has_same_type) {
          nb::object elem_serializer_obj = elem_type_info->serializer();
          Serializer *elem_serializer =
              nb::cast<Serializer *>(elem_serializer_obj);
          need_ref = elem_serializer->need_to_write_ref();
        }
        if (!has_same_type || need_ref) {
          collect_flag |= kCollTrackingRef;
        }
      }
    }
    int64_t size = PyObject_Length(iterable);
    buffer.write_var_uint32(static_cast<uint32_t>(size));
    buffer.write_int8(collect_flag);
    if (has_same_type && (collect_flag & kCollIsDeclElementType) == 0) {
      type_resolver_->write_type_info(buffer, elem_type_info);
    }
    return {collect_flag, elem_type_info};
  }

  void write(BufferView &buffer, nb::handle value) override {
    int64_t size = PyObject_Length(value.ptr());
    if (size == 0) {
      buffer.write_var_uint32(0);
      return;
    }
    auto header_pair = write_header(buffer, value);
    int8_t collect_flag = header_pair.first;
    TypeInfo *elem_type_info = header_pair.second;
    nb::object elem_type = elem_type_info->cls();
    bool is_py = !fory_ptr_->xlang();
    nb::object serializer_obj = elem_type_info->serializer();
    auto *serializer = nb::cast<Serializer *>(serializer_obj);
    bool serializer_is_python = is_python_serializer(serializer_obj);
    if ((collect_flag & kCollIsSameType) != 0) {
      if ((collect_flag & kCollHasNull) == 0) {
        uint8_t type_id = elem_type_info->type_id();
        switch (static_cast<fory::TypeId>(type_id)) {
        case fory::TypeId::STRING:
          write_string(buffer, value);
          return;
        case fory::TypeId::VARINT64:
          write_int(buffer, value);
          return;
        case fory::TypeId::BOOL:
          write_bool(buffer, value);
          return;
        case fory::TypeId::FLOAT64:
          write_float(buffer, value);
          return;
        default:
          break;
        }
        if ((collect_flag & kCollTrackingRef) == 0) {
          write_same_type_no_ref(buffer, value, serializer, serializer_obj,
                                 serializer_is_python, is_py);
        } else {
          write_same_type_ref(buffer, value, serializer, serializer_obj,
                              serializer_is_python, is_py);
        }
      } else if ((collect_flag & kCollTrackingRef) != 0) {
        write_same_type_ref(buffer, value, serializer, serializer_obj,
                            serializer_is_python, is_py);
      } else {
        write_same_type_has_null(buffer, value, serializer, serializer_obj,
                                 serializer_is_python, is_py);
      }
      return;
    }
    bool tracking_ref = (collect_flag & kCollTrackingRef) != 0;
    bool has_null = (collect_flag & kCollHasNull) != 0;
    if (tracking_ref) {
      for (nb::handle item : nb::iter(value)) {
        nb::object cls = nb::borrow(Py_TYPE(item.ptr()));
        if (PyUnicode_CheckExact(item.ptr())) {
          buffer.write_int16(static_cast<int16_t>(g_not_null_string_flag));
          buffer.write_string(item);
          continue;
        }
        if (PyLong_CheckExact(item.ptr())) {
          buffer.write_int16(static_cast<int16_t>(g_not_null_int64_flag));
          buffer.write_var_int64(nb::cast<int64_t>(item));
          continue;
        }
        if (PyBool_Check(item.ptr())) {
          buffer.write_int16(static_cast<int16_t>(g_not_null_bool_flag));
          buffer.write_int8(PyObject_IsTrue(item.ptr()) ? 1 : 0);
          continue;
        }
        if (PyFloat_CheckExact(item.ptr())) {
          buffer.write_int16(static_cast<int16_t>(g_not_null_float64_flag));
          buffer.write_double(nb::cast<double>(item));
          continue;
        }
        if (!ref_resolver_->write_ref_or_null(buffer, item)) {
          TypeInfo *typeinfo = type_resolver_->get_type_info(cls, true);
          type_resolver_->write_type_info(buffer, typeinfo);
          nb::object ser_obj = typeinfo->serializer();
          auto *ser = nb::cast<Serializer *>(ser_obj);
          if (is_py) {
            serializer_write(ser, ser_obj, buffer, item);
          } else {
            serializer_xwrite(ser, ser_obj, buffer, item);
          }
        }
      }
    } else if (!has_null) {
      for (nb::handle item : nb::iter(value)) {
        nb::object cls = nb::borrow(Py_TYPE(item.ptr()));
        TypeInfo *typeinfo = type_resolver_->get_type_info(cls, true);
        type_resolver_->write_type_info(buffer, typeinfo);
        nb::object ser_obj = typeinfo->serializer();
        auto *ser = nb::cast<Serializer *>(ser_obj);
        if (is_py) {
          serializer_write(ser, ser_obj, buffer, item);
        } else {
          serializer_xwrite(ser, ser_obj, buffer, item);
        }
      }
    } else {
      for (nb::handle item : nb::iter(value)) {
        if (item.is_none()) {
          buffer.write_int8(kNullFlag);
        } else {
          buffer.write_int8(kNotNullValueFlag);
          nb::object cls = nb::borrow(Py_TYPE(item.ptr()));
          TypeInfo *typeinfo = type_resolver_->get_type_info(cls, true);
          type_resolver_->write_type_info(buffer, typeinfo);
          nb::object ser_obj = typeinfo->serializer();
          auto *ser = nb::cast<Serializer *>(ser_obj);
          if (is_py) {
            serializer_write(ser, ser_obj, buffer, item);
          } else {
            serializer_xwrite(ser, ser_obj, buffer, item);
          }
        }
      }
    }
  }

  void xwrite(BufferView &buffer, nb::handle value) override {
    write(buffer, value);
  }

  nb::object xread(BufferView &buffer) override { return read(buffer); }

protected:
  void write_string(BufferView &buffer, nb::handle value) {
    PyObject *collection = value.ptr();
    if (PyList_CheckExact(collection)) {
      Py_ssize_t size = Py_SIZE(collection);
      for (Py_ssize_t i = 0; i < size; ++i) {
        buffer.write_string(nb::handle(PyList_GET_ITEM(collection, i)));
      }
      return;
    }
    if (PyTuple_CheckExact(collection)) {
      Py_ssize_t size = Py_SIZE(collection);
      for (Py_ssize_t i = 0; i < size; ++i) {
        buffer.write_string(nb::handle(PyTuple_GET_ITEM(collection, i)));
      }
      return;
    }
    for (nb::handle item : nb::iter(nb::borrow(collection))) {
      buffer.write_string(item);
    }
  }

  void write_int(BufferView &buffer, nb::handle value) {
    PyObject *collection = value.ptr();
    if (PyList_CheckExact(collection)) {
      Py_ssize_t size = Py_SIZE(collection);
      for (Py_ssize_t i = 0; i < size; ++i) {
        PyObject *item = PyList_GET_ITEM(collection, i);
        long v = PyLong_AsLong(item);
        if (v == -1 && PyErr_Occurred()) {
          PyErr_Clear();
          long long vv = PyLong_AsLongLong(item);
          if (vv == -1 && PyErr_Occurred()) {
            throw nb::python_error();
          }
          buffer.write_var_int64(static_cast<int64_t>(vv));
        } else {
          buffer.write_var_int64(static_cast<int64_t>(v));
        }
      }
      return;
    }
    if (PyTuple_CheckExact(collection)) {
      Py_ssize_t size = Py_SIZE(collection);
      for (Py_ssize_t i = 0; i < size; ++i) {
        PyObject *item = PyTuple_GET_ITEM(collection, i);
        long v = PyLong_AsLong(item);
        if (v == -1 && PyErr_Occurred()) {
          PyErr_Clear();
          long long vv = PyLong_AsLongLong(item);
          if (vv == -1 && PyErr_Occurred()) {
            throw nb::python_error();
          }
          buffer.write_var_int64(static_cast<int64_t>(vv));
        } else {
          buffer.write_var_int64(static_cast<int64_t>(v));
        }
      }
      return;
    }
    for (nb::handle item : nb::iter(nb::borrow(collection))) {
      buffer.write_var_int64(nb::cast<int64_t>(item));
    }
  }

  void write_bool(BufferView &buffer, nb::handle value) {
    PyObject *collection = value.ptr();
    if (PyList_CheckExact(collection) || PyTuple_CheckExact(collection)) {
      Py_ssize_t size = Py_SIZE(collection);
      int32_t bytes = static_cast<int32_t>(sizeof(bool) * size);
      buffer.grow(bytes);
      int32_t writer_index = buffer.get_writer_index();
      fory::Fory_PyBooleanSequenceWriteToBuffer(collection, buffer.raw(),
                                                writer_index);
      buffer.set_writer_index(writer_index + bytes);
      return;
    }
    for (nb::handle item : nb::iter(value)) {
      buffer.write_int8(PyObject_IsTrue(item.ptr()) ? 1 : 0);
    }
  }

  void write_float(BufferView &buffer, nb::handle value) {
    PyObject *collection = value.ptr();
    if (PyList_CheckExact(collection) || PyTuple_CheckExact(collection)) {
      Py_ssize_t size = Py_SIZE(collection);
      int32_t bytes = static_cast<int32_t>(sizeof(double) * size);
      buffer.grow(bytes);
      int32_t writer_index = buffer.get_writer_index();
      fory::Fory_PyFloatSequenceWriteToBuffer(collection, buffer.raw(),
                                              writer_index);
      buffer.set_writer_index(writer_index + bytes);
      return;
    }
    for (nb::handle item : nb::iter(value)) {
      buffer.write_double(nb::cast<double>(item));
    }
  }

  void write_same_type_no_ref(BufferView &buffer, nb::handle value,
                              Serializer *serializer,
                              const nb::object &serializer_obj,
                              bool serializer_is_python, bool is_py) {
    for (nb::handle item : nb::iter(value)) {
      if (is_py) {
        serializer_write(serializer, serializer_obj, buffer, item,
                         serializer_is_python);
      } else {
        serializer_xwrite(serializer, serializer_obj, buffer, item,
                          serializer_is_python);
      }
    }
  }

  void write_same_type_has_null(BufferView &buffer, nb::handle value,
                                Serializer *serializer,
                                const nb::object &serializer_obj,
                                bool serializer_is_python, bool is_py) {
    for (nb::handle item : nb::iter(value)) {
      if (item.is_none()) {
        buffer.write_int8(kNullFlag);
      } else {
        buffer.write_int8(kNotNullValueFlag);
        if (is_py) {
          serializer_write(serializer, serializer_obj, buffer, item,
                           serializer_is_python);
        } else {
          serializer_xwrite(serializer, serializer_obj, buffer, item,
                            serializer_is_python);
        }
      }
    }
  }

  void write_same_type_ref(BufferView &buffer, nb::handle value,
                           Serializer *serializer,
                           const nb::object &serializer_obj,
                           bool serializer_is_python, bool is_py) {
    for (nb::handle item : nb::iter(value)) {
      if (!ref_resolver_->write_ref_or_null(buffer, item)) {
        if (is_py) {
          serializer_write(serializer, serializer_obj, buffer, item,
                           serializer_is_python);
        } else {
          serializer_xwrite(serializer, serializer_obj, buffer, item,
                            serializer_is_python);
        }
      }
    }
  }

  TypeResolver *type_resolver_;
  MapRefResolver *ref_resolver_;
  nb::object elem_serializer_obj_{nb::none()};
  Serializer *elem_serializer_{nullptr};
  nb::object elem_type_{nb::none()};
  TypeInfo *elem_type_info_{nullptr};
  int8_t elem_tracking_ref_{-1};
};

} // namespace
namespace {

template <typename AddFn>
inline void read_string_sequence(BufferView &buffer, int64_t len, AddFn add) {
  read_sequence(len, add, [&buffer]() { return buffer.read_string(); });
}

template <typename AddFn>
inline void read_int_sequence(BufferView &buffer, int64_t len, AddFn add) {
  read_sequence(len, add,
                [&buffer]() { return nb::int_(buffer.read_var_int64()); });
}

template <typename AddFn>
inline void read_bool_sequence(BufferView &buffer, int64_t len, AddFn add) {
  read_sequence(len, add,
                [&buffer]() { return nb::bool_(buffer.read_int8() != 0); });
}

template <typename AddFn>
inline void read_float_sequence(BufferView &buffer, int64_t len, AddFn add) {
  read_sequence(len, add,
                [&buffer]() { return nb::float_(buffer.read_double()); });
}

template <typename AddFn>
inline bool read_primitive_sequence(BufferView &buffer, int64_t len,
                                    uint8_t type_id, AddFn add) {
  switch (static_cast<fory::TypeId>(type_id)) {
  case fory::TypeId::STRING:
    read_string_sequence(buffer, len, add);
    return true;
  case fory::TypeId::VARINT64:
    read_int_sequence(buffer, len, add);
    return true;
  case fory::TypeId::BOOL:
    read_bool_sequence(buffer, len, add);
    return true;
  case fory::TypeId::FLOAT64:
    read_float_sequence(buffer, len, add);
    return true;
  default:
    return false;
  }
}

inline bool read_primitive_list_sequence(BufferView &buffer, PyObject *list_obj,
                                         int64_t len, uint8_t type_id) {
  switch (static_cast<fory::TypeId>(type_id)) {
  case fory::TypeId::STRING:
    for (int64_t i = 0; i < len; ++i) {
      nb::object value = buffer.read_string();
      PyList_SET_ITEM(list_obj, static_cast<Py_ssize_t>(i),
                      value.release().ptr());
    }
    return true;
  case fory::TypeId::VARINT64:
    for (int64_t i = 0; i < len; ++i) {
      int64_t data = buffer.read_var_int64();
      PyObject *value = (data >= std::numeric_limits<long>::min() &&
                         data <= std::numeric_limits<long>::max())
                            ? PyLong_FromLong(static_cast<long>(data))
                            : PyLong_FromLongLong(data);
      if (value == nullptr) {
        throw nb::python_error();
      }
      PyList_SET_ITEM(list_obj, static_cast<Py_ssize_t>(i), value);
    }
    return true;
  case fory::TypeId::BOOL:
    for (int64_t i = 0; i < len; ++i) {
      PyObject *value = buffer.read_int8() != 0 ? Py_True : Py_False;
      Py_INCREF(value);
      PyList_SET_ITEM(list_obj, static_cast<Py_ssize_t>(i), value);
    }
    return true;
  case fory::TypeId::FLOAT64:
    for (int64_t i = 0; i < len; ++i) {
      PyObject *value = PyFloat_FromDouble(buffer.read_double());
      if (value == nullptr) {
        throw nb::python_error();
      }
      PyList_SET_ITEM(list_obj, static_cast<Py_ssize_t>(i), value);
    }
    return true;
  default:
    return false;
  }
}

inline bool read_primitive_tuple_sequence(BufferView &buffer,
                                          PyObject *tuple_obj, int64_t len,
                                          uint8_t type_id) {
  switch (static_cast<fory::TypeId>(type_id)) {
  case fory::TypeId::STRING:
    for (int64_t i = 0; i < len; ++i) {
      nb::object value = buffer.read_string();
      PyTuple_SET_ITEM(tuple_obj, static_cast<Py_ssize_t>(i),
                       value.release().ptr());
    }
    return true;
  case fory::TypeId::VARINT64:
    for (int64_t i = 0; i < len; ++i) {
      int64_t data = buffer.read_var_int64();
      PyObject *value = (data >= std::numeric_limits<long>::min() &&
                         data <= std::numeric_limits<long>::max())
                            ? PyLong_FromLong(static_cast<long>(data))
                            : PyLong_FromLongLong(data);
      if (value == nullptr) {
        throw nb::python_error();
      }
      PyTuple_SET_ITEM(tuple_obj, static_cast<Py_ssize_t>(i), value);
    }
    return true;
  case fory::TypeId::BOOL:
    for (int64_t i = 0; i < len; ++i) {
      PyObject *value = buffer.read_int8() != 0 ? Py_True : Py_False;
      Py_INCREF(value);
      PyTuple_SET_ITEM(tuple_obj, static_cast<Py_ssize_t>(i), value);
    }
    return true;
  case fory::TypeId::FLOAT64:
    for (int64_t i = 0; i < len; ++i) {
      PyObject *value = PyFloat_FromDouble(buffer.read_double());
      if (value == nullptr) {
        throw nb::python_error();
      }
      PyTuple_SET_ITEM(tuple_obj, static_cast<Py_ssize_t>(i), value);
    }
    return true;
  default:
    return false;
  }
}

inline nb::object get_next_element(BufferView &buffer,
                                   MapRefResolver *ref_resolver,
                                   TypeResolver *type_resolver, bool is_py,
                                   Fory *fory);

class ListSerializer : public CollectionSerializer {
public:
  using CollectionSerializer::CollectionSerializer;

  nb::object read(BufferView &buffer) override {
    int32_t len = static_cast<int32_t>(buffer.read_var_uint32());
    nb::object list_obj = nb::steal(PyList_New(len));
    if (len == 0) {
      return list_obj;
    }
    int8_t collect_flag = buffer.read_int8();
    ref_resolver_->reference(list_obj);
    bool is_py = !fory_ptr_->xlang();
    if ((collect_flag & kCollIsSameType) != 0) {
      TypeInfo *typeinfo = nullptr;
      if ((collect_flag & kCollIsDeclElementType) == 0) {
        typeinfo = type_resolver_->read_type_info(buffer);
      } else {
        typeinfo = elem_type_info_;
      }
      if ((collect_flag & kCollHasNull) == 0) {
        uint8_t type_id = typeinfo->type_id();
        if (read_primitive_list_sequence(buffer, list_obj.ptr(), len,
                                         type_id)) {
          return list_obj;
        }
        if ((collect_flag & kCollTrackingRef) == 0) {
          read_same_type_no_ref(buffer, len, list_obj, typeinfo, is_py);
        } else {
          read_same_type_ref(buffer, len, list_obj, typeinfo, is_py);
        }
      } else if ((collect_flag & kCollTrackingRef) != 0) {
        read_same_type_ref(buffer, len, list_obj, typeinfo, is_py);
      } else {
        read_same_type_has_null(buffer, len, list_obj, typeinfo, is_py);
      }
      return list_obj;
    }

    bool tracking_ref = (collect_flag & kCollTrackingRef) != 0;
    bool has_null = (collect_flag & kCollHasNull) != 0;
    fory_ptr_->inc_depth();
    if (tracking_ref) {
      for (int32_t i = 0; i < len; ++i) {
        nb::object elem = get_next_element(buffer, ref_resolver_,
                                           type_resolver_, is_py, fory_ptr_);
        Py_INCREF(elem.ptr());
        PyList_SET_ITEM(list_obj.ptr(), i, elem.ptr());
      }
    } else if (!has_null) {
      for (int32_t i = 0; i < len; ++i) {
        TypeInfo *typeinfo = type_resolver_->read_type_info(buffer);
        nb::object ser_obj = typeinfo->serializer();
        Serializer *ser = nb::cast<Serializer *>(ser_obj);
        nb::object elem;
        if (is_py) {
          elem = serializer_read(ser, ser_obj, buffer);
        } else {
          elem = fory_ptr_->xread_no_ref(buffer, ser, ser_obj);
        }
        Py_INCREF(elem.ptr());
        PyList_SET_ITEM(list_obj.ptr(), i, elem.ptr());
      }
    } else {
      for (int32_t i = 0; i < len; ++i) {
        int8_t head_flag = buffer.read_int8();
        nb::object elem = nb::none();
        if (head_flag != kNullFlag) {
          TypeInfo *typeinfo = type_resolver_->read_type_info(buffer);
          nb::object ser_obj = typeinfo->serializer();
          Serializer *ser = nb::cast<Serializer *>(ser_obj);
          if (is_py) {
            elem = serializer_read(ser, ser_obj, buffer);
          } else {
            elem = fory_ptr_->xread_no_ref(buffer, ser, ser_obj);
          }
        }
        Py_INCREF(elem.ptr());
        PyList_SET_ITEM(list_obj.ptr(), i, elem.ptr());
      }
    }
    fory_ptr_->dec_depth();
    return list_obj;
  }

protected:
  void add_element(nb::handle list_obj, int64_t index, nb::handle element) {
    Py_INCREF(element.ptr());
    PyList_SET_ITEM(list_obj.ptr(), static_cast<Py_ssize_t>(index),
                    element.ptr());
  }

  void read_same_type_no_ref(BufferView &buffer, int64_t len,
                             nb::object list_obj, TypeInfo *typeinfo,
                             bool is_py) {
    nb::object serializer_obj = typeinfo->serializer();
    Serializer *serializer = nb::cast<Serializer *>(serializer_obj);
    fory_ptr_->inc_depth();
    if (is_py) {
      for (int64_t i = 0; i < len; ++i) {
        nb::object obj = serializer_read(serializer, serializer_obj, buffer);
        add_element(list_obj, i, obj);
      }
    } else {
      for (int64_t i = 0; i < len; ++i) {
        nb::object obj =
            fory_ptr_->xread_no_ref(buffer, serializer, serializer_obj);
        add_element(list_obj, i, obj);
      }
    }
    fory_ptr_->dec_depth();
  }

  void read_same_type_has_null(BufferView &buffer, int64_t len,
                               nb::object list_obj, TypeInfo *typeinfo,
                               bool is_py) {
    nb::object serializer_obj = typeinfo->serializer();
    Serializer *serializer = nb::cast<Serializer *>(serializer_obj);
    fory_ptr_->inc_depth();
    for (int64_t i = 0; i < len; ++i) {
      int8_t flag = buffer.read_int8();
      if (flag == kNullFlag) {
        add_element(list_obj, i, nb::none());
      } else {
        nb::object obj =
            is_py ? serializer_read(serializer, serializer_obj, buffer)
                  : fory_ptr_->xread_no_ref(buffer, serializer, serializer_obj);
        add_element(list_obj, i, obj);
      }
    }
    fory_ptr_->dec_depth();
  }

  void read_same_type_ref(BufferView &buffer, int64_t len, nb::object list_obj,
                          TypeInfo *typeinfo, bool is_py) {
    nb::object serializer_obj = typeinfo->serializer();
    Serializer *serializer = nb::cast<Serializer *>(serializer_obj);
    fory_ptr_->inc_depth();
    for (int64_t i = 0; i < len; ++i) {
      int32_t ref_id = ref_resolver_->try_preserve_ref_id(buffer);
      nb::object obj;
      if (ref_id < kNotNullValueFlag) {
        obj = ref_resolver_->get_read_object();
      } else {
        obj = is_py ? serializer_read(serializer, serializer_obj, buffer)
                    : serializer_xread(serializer, serializer_obj, buffer);
        ref_resolver_->set_read_object(ref_id, obj);
      }
      add_element(list_obj, i, obj);
    }
    fory_ptr_->dec_depth();
  }
};

class TupleSerializer : public ListSerializer {
public:
  using ListSerializer::ListSerializer;

  nb::object read(BufferView &buffer) override {
    int32_t len = static_cast<int32_t>(buffer.read_var_uint32());
    nb::object tuple_obj = nb::steal(PyTuple_New(len));
    if (len == 0) {
      return tuple_obj;
    }
    int8_t collect_flag = buffer.read_int8();
    bool is_py = !fory_ptr_->xlang();
    if ((collect_flag & kCollIsSameType) != 0) {
      TypeInfo *typeinfo = nullptr;
      if ((collect_flag & kCollIsDeclElementType) == 0) {
        typeinfo = type_resolver_->read_type_info(buffer);
      } else {
        typeinfo = elem_type_info_;
      }
      if ((collect_flag & kCollHasNull) == 0) {
        uint8_t type_id = typeinfo->type_id();
        if (read_primitive_tuple_sequence(buffer, tuple_obj.ptr(), len,
                                          type_id)) {
          return tuple_obj;
        }
        if ((collect_flag & kCollTrackingRef) == 0) {
          read_same_type_no_ref_tuple(buffer, len, tuple_obj, typeinfo, is_py);
        } else {
          read_same_type_ref_tuple(buffer, len, tuple_obj, typeinfo, is_py);
        }
      } else if ((collect_flag & kCollTrackingRef) != 0) {
        read_same_type_ref_tuple(buffer, len, tuple_obj, typeinfo, is_py);
      } else {
        read_same_type_has_null_tuple(buffer, len, tuple_obj, typeinfo, is_py);
      }
      return tuple_obj;
    }

    bool tracking_ref = (collect_flag & kCollTrackingRef) != 0;
    bool has_null = (collect_flag & kCollHasNull) != 0;
    fory_ptr_->inc_depth();
    if (tracking_ref) {
      for (int32_t i = 0; i < len; ++i) {
        nb::object elem = get_next_element(buffer, ref_resolver_,
                                           type_resolver_, is_py, fory_ptr_);
        Py_INCREF(elem.ptr());
        PyTuple_SET_ITEM(tuple_obj.ptr(), i, elem.ptr());
      }
    } else if (!has_null) {
      for (int32_t i = 0; i < len; ++i) {
        TypeInfo *typeinfo = type_resolver_->read_type_info(buffer);
        nb::object ser_obj = typeinfo->serializer();
        Serializer *ser = nb::cast<Serializer *>(ser_obj);
        nb::object elem = is_py ? serializer_read(ser, ser_obj, buffer)
                                : fory_ptr_->xread_no_ref(buffer, ser, ser_obj);
        Py_INCREF(elem.ptr());
        PyTuple_SET_ITEM(tuple_obj.ptr(), i, elem.ptr());
      }
    } else {
      for (int32_t i = 0; i < len; ++i) {
        int8_t head_flag = buffer.read_int8();
        nb::object elem = nb::none();
        if (head_flag != kNullFlag) {
          TypeInfo *typeinfo = type_resolver_->read_type_info(buffer);
          nb::object ser_obj = typeinfo->serializer();
          Serializer *ser = nb::cast<Serializer *>(ser_obj);
          elem = is_py ? serializer_read(ser, ser_obj, buffer)
                       : fory_ptr_->xread_no_ref(buffer, ser, ser_obj);
        }
        Py_INCREF(elem.ptr());
        PyTuple_SET_ITEM(tuple_obj.ptr(), i, elem.ptr());
      }
    }
    fory_ptr_->dec_depth();
    return tuple_obj;
  }

private:
  void read_same_type_no_ref_tuple(BufferView &buffer, int64_t len,
                                   nb::object tuple_obj, TypeInfo *typeinfo,
                                   bool is_py) {
    nb::object serializer_obj = typeinfo->serializer();
    Serializer *serializer = nb::cast<Serializer *>(serializer_obj);
    fory_ptr_->inc_depth();
    for (int64_t i = 0; i < len; ++i) {
      nb::object obj =
          is_py ? serializer_read(serializer, serializer_obj, buffer)
                : fory_ptr_->xread_no_ref(buffer, serializer, serializer_obj);
      Py_INCREF(obj.ptr());
      PyTuple_SET_ITEM(tuple_obj.ptr(), static_cast<Py_ssize_t>(i), obj.ptr());
    }
    fory_ptr_->dec_depth();
  }

  void read_same_type_has_null_tuple(BufferView &buffer, int64_t len,
                                     nb::object tuple_obj, TypeInfo *typeinfo,
                                     bool is_py) {
    nb::object serializer_obj = typeinfo->serializer();
    Serializer *serializer = nb::cast<Serializer *>(serializer_obj);
    fory_ptr_->inc_depth();
    for (int64_t i = 0; i < len; ++i) {
      int8_t flag = buffer.read_int8();
      if (flag == kNullFlag) {
        Py_INCREF(Py_None);
        PyTuple_SET_ITEM(tuple_obj.ptr(), static_cast<Py_ssize_t>(i), Py_None);
      } else {
        nb::object obj =
            is_py ? serializer_read(serializer, serializer_obj, buffer)
                  : fory_ptr_->xread_no_ref(buffer, serializer, serializer_obj);
        Py_INCREF(obj.ptr());
        PyTuple_SET_ITEM(tuple_obj.ptr(), static_cast<Py_ssize_t>(i),
                         obj.ptr());
      }
    }
    fory_ptr_->dec_depth();
  }

  void read_same_type_ref_tuple(BufferView &buffer, int64_t len,
                                nb::object tuple_obj, TypeInfo *typeinfo,
                                bool is_py) {
    nb::object serializer_obj = typeinfo->serializer();
    Serializer *serializer = nb::cast<Serializer *>(serializer_obj);
    fory_ptr_->inc_depth();
    for (int64_t i = 0; i < len; ++i) {
      int32_t ref_id = ref_resolver_->try_preserve_ref_id(buffer);
      nb::object obj;
      if (ref_id < kNotNullValueFlag) {
        obj = ref_resolver_->get_read_object();
      } else {
        obj = is_py ? serializer_read(serializer, serializer_obj, buffer)
                    : serializer_xread(serializer, serializer_obj, buffer);
        ref_resolver_->set_read_object(ref_id, obj);
      }
      Py_INCREF(obj.ptr());
      PyTuple_SET_ITEM(tuple_obj.ptr(), static_cast<Py_ssize_t>(i), obj.ptr());
    }
    fory_ptr_->dec_depth();
  }
};

class StringArraySerializer : public ListSerializer {
public:
  StringArraySerializer(nb::object fory, nb::object type)
      : ListSerializer(fory, type,
                       nb::cast(StringSerializer(
                           fory,
                           nb::borrow(nb::handle(
                               reinterpret_cast<PyObject *>(&PyUnicode_Type))),
                           false)),
                       std::nullopt) {}
};

class SetSerializer : public CollectionSerializer {
public:
  using CollectionSerializer::CollectionSerializer;

  nb::object read(BufferView &buffer) override {
    nb::object set_obj = nb::set();
    ref_resolver_->reference(set_obj);
    int32_t len = static_cast<int32_t>(buffer.read_var_uint32());
    if (len == 0) {
      return set_obj;
    }
    int8_t collect_flag = buffer.read_int8();
    bool is_py = !fory_ptr_->xlang();
    if ((collect_flag & kCollIsSameType) != 0) {
      TypeInfo *typeinfo = nullptr;
      if ((collect_flag & kCollIsDeclElementType) == 0) {
        typeinfo = type_resolver_->read_type_info(buffer);
      } else {
        typeinfo = elem_type_info_;
      }
      if ((collect_flag & kCollHasNull) == 0) {
        uint8_t type_id = typeinfo->type_id();
        if (read_primitive_sequence(buffer, len, type_id,
                                    [&](int64_t, nb::object value) {
                                      set_obj.attr("add")(value);
                                    })) {
          return set_obj;
        }
        if ((collect_flag & kCollTrackingRef) == 0) {
          read_same_type_no_ref(buffer, len, set_obj, typeinfo, is_py);
        } else {
          read_same_type_ref(buffer, len, set_obj, typeinfo, is_py);
        }
      } else if ((collect_flag & kCollTrackingRef) != 0) {
        read_same_type_ref(buffer, len, set_obj, typeinfo, is_py);
      } else {
        read_same_type_has_null(buffer, len, set_obj, typeinfo, is_py);
      }
      return set_obj;
    }

    bool tracking_ref = (collect_flag & kCollTrackingRef) != 0;
    bool has_null = (collect_flag & kCollHasNull) != 0;
    fory_ptr_->inc_depth();
    if (tracking_ref) {
      for (int32_t i = 0; i < len; ++i) {
        int32_t ref_id = ref_resolver_->try_preserve_ref_id(buffer);
        if (ref_id < kNotNullValueFlag) {
          set_obj.attr("add")(ref_resolver_->get_read_object());
          continue;
        }
        TypeInfo *typeinfo = type_resolver_->read_type_info(buffer);
        uint8_t type_id = typeinfo->type_id();
        nb::object elem;
        switch (static_cast<fory::TypeId>(type_id)) {
        case fory::TypeId::STRING:
          elem = buffer.read_string();
          break;
        case fory::TypeId::VARINT64:
          elem = nb::int_(buffer.read_var_int64());
          break;
        case fory::TypeId::BOOL:
          elem = nb::bool_(buffer.read_int8() != 0);
          break;
        case fory::TypeId::FLOAT64:
          elem = nb::float_(buffer.read_double());
          break;
        default: {
          nb::object ser_obj = typeinfo->serializer();
          Serializer *ser = nb::cast<Serializer *>(ser_obj);
          elem = is_py ? serializer_read(ser, ser_obj, buffer)
                       : serializer_xread(ser, ser_obj, buffer);
          ref_resolver_->set_read_object(ref_id, elem);
          break;
        }
        }
        set_obj.attr("add")(elem);
      }
    } else if (!has_null) {
      for (int32_t i = 0; i < len; ++i) {
        TypeInfo *typeinfo = type_resolver_->read_type_info(buffer);
        uint8_t type_id = typeinfo->type_id();
        nb::object elem;
        switch (static_cast<fory::TypeId>(type_id)) {
        case fory::TypeId::STRING:
          elem = buffer.read_string();
          break;
        case fory::TypeId::VARINT64:
          elem = nb::int_(buffer.read_var_int64());
          break;
        case fory::TypeId::BOOL:
          elem = nb::bool_(buffer.read_int8() != 0);
          break;
        case fory::TypeId::FLOAT64:
          elem = nb::float_(buffer.read_double());
          break;
        default: {
          nb::object ser_obj = typeinfo->serializer();
          Serializer *ser = nb::cast<Serializer *>(ser_obj);
          elem = is_py ? serializer_read(ser, ser_obj, buffer)
                       : fory_ptr_->xread_no_ref(buffer, ser, ser_obj);
          break;
        }
        }
        set_obj.attr("add")(elem);
      }
    } else {
      for (int32_t i = 0; i < len; ++i) {
        int8_t head_flag = buffer.read_int8();
        if (head_flag == kNullFlag) {
          set_obj.attr("add")(nb::none());
          continue;
        }
        TypeInfo *typeinfo = type_resolver_->read_type_info(buffer);
        nb::object ser_obj = typeinfo->serializer();
        Serializer *ser = nb::cast<Serializer *>(ser_obj);
        nb::object elem = is_py ? serializer_read(ser, ser_obj, buffer)
                                : fory_ptr_->xread_no_ref(buffer, ser, ser_obj);
        set_obj.attr("add")(elem);
      }
    }
    fory_ptr_->dec_depth();
    return set_obj;
  }

private:
  void read_same_type_no_ref(BufferView &buffer, int64_t len,
                             nb::object set_obj, TypeInfo *typeinfo,
                             bool is_py) {
    nb::object serializer_obj = typeinfo->serializer();
    Serializer *serializer = nb::cast<Serializer *>(serializer_obj);
    fory_ptr_->inc_depth();
    for (int64_t i = 0; i < len; ++i) {
      nb::object obj =
          is_py ? serializer_read(serializer, serializer_obj, buffer)
                : fory_ptr_->xread_no_ref(buffer, serializer, serializer_obj);
      set_obj.attr("add")(obj);
    }
    fory_ptr_->dec_depth();
  }

  void read_same_type_has_null(BufferView &buffer, int64_t len,
                               nb::object set_obj, TypeInfo *typeinfo,
                               bool is_py) {
    nb::object serializer_obj = typeinfo->serializer();
    Serializer *serializer = nb::cast<Serializer *>(serializer_obj);
    fory_ptr_->inc_depth();
    for (int64_t i = 0; i < len; ++i) {
      int8_t flag = buffer.read_int8();
      if (flag == kNullFlag) {
        set_obj.attr("add")(nb::none());
      } else {
        nb::object obj =
            is_py ? serializer_read(serializer, serializer_obj, buffer)
                  : fory_ptr_->xread_no_ref(buffer, serializer, serializer_obj);
        set_obj.attr("add")(obj);
      }
    }
    fory_ptr_->dec_depth();
  }

  void read_same_type_ref(BufferView &buffer, int64_t len, nb::object set_obj,
                          TypeInfo *typeinfo, bool is_py) {
    nb::object serializer_obj = typeinfo->serializer();
    Serializer *serializer = nb::cast<Serializer *>(serializer_obj);
    fory_ptr_->inc_depth();
    for (int64_t i = 0; i < len; ++i) {
      int32_t ref_id = ref_resolver_->try_preserve_ref_id(buffer);
      nb::object obj;
      if (ref_id < kNotNullValueFlag) {
        obj = ref_resolver_->get_read_object();
      } else {
        obj = is_py ? serializer_read(serializer, serializer_obj, buffer)
                    : serializer_xread(serializer, serializer_obj, buffer);
        ref_resolver_->set_read_object(ref_id, obj);
      }
      set_obj.attr("add")(obj);
    }
    fory_ptr_->dec_depth();
  }
};

inline nb::object get_next_element(BufferView &buffer,
                                   MapRefResolver *ref_resolver,
                                   TypeResolver *type_resolver, bool is_py,
                                   Fory *fory) {
  int32_t ref_id = ref_resolver->try_preserve_ref_id(buffer);
  if (ref_id < kNotNullValueFlag) {
    return ref_resolver->get_read_object();
  }
  TypeInfo *typeinfo = type_resolver->read_type_info(buffer);
  uint8_t type_id = typeinfo->type_id();
  switch (static_cast<fory::TypeId>(type_id)) {
  case fory::TypeId::STRING:
    return buffer.read_string();
  case fory::TypeId::VARINT32:
  case fory::TypeId::VARINT64:
    return nb::int_(buffer.read_var_int64());
  case fory::TypeId::BOOL:
    return nb::bool_(buffer.read_int8() != 0);
  case fory::TypeId::FLOAT64:
    return nb::float_(buffer.read_double());
  default: {
    nb::object ser_obj = typeinfo->serializer();
    Serializer *ser = nb::cast<Serializer *>(ser_obj);
    nb::object obj = is_py ? serializer_read(ser, ser_obj, buffer)
                           : serializer_xread(ser, ser_obj, buffer);
    ref_resolver->set_read_object(ref_id, obj);
    return obj;
  }
  }
}

} // namespace
namespace {

constexpr int32_t kMaxChunkSize = 255;
constexpr int32_t kTrackingKeyRef = 0b1;
constexpr int32_t kKeyHasNull = 0b10;
constexpr int32_t kKeyDeclType = 0b100;
constexpr int32_t kTrackingValueRef = 0b1000;
constexpr int32_t kValueHasNull = 0b10000;
constexpr int32_t kValueDeclType = 0b100000;
constexpr int32_t kKvNull = kKeyHasNull | kValueHasNull;
constexpr int32_t kNullKeyValueDeclType = kKeyHasNull | kValueDeclType;
constexpr int32_t kNullKeyValueDeclTypeTrackingRef =
    kKeyHasNull | kValueDeclType | kTrackingValueRef;
constexpr int32_t kNullValueKeyDeclType = kValueHasNull | kKeyDeclType;
constexpr int32_t kNullValueKeyDeclTypeTrackingRef =
    kValueHasNull | kKeyDeclType | kTrackingKeyRef;

template <typename ReadFn, typename ReadNoRefFn>
inline nb::object
read_with_optional_ref(BufferView &buffer, MapRefResolver *ref_resolver,
                       bool tracking_ref, ReadFn read_with_ref,
                       ReadNoRefFn read_no_ref) {
  if (!tracking_ref) {
    return read_no_ref();
  }
  int32_t ref_id = ref_resolver->try_preserve_ref_id(buffer);
  if (ref_id < kNotNullValueFlag) {
    return ref_resolver->get_read_object();
  }
  nb::object obj = read_with_ref();
  ref_resolver->set_read_object(ref_id, obj);
  return obj;
}

class MapSerializer : public Serializer {
public:
  MapSerializer(nb::object fory, nb::object type, nb::object key_serializer,
                nb::object value_serializer,
                std::optional<bool> key_tracking_ref,
                std::optional<bool> value_tracking_ref)
      : Serializer(fory, type), type_resolver_(fory_ptr_->type_resolver()),
        ref_resolver_(fory_ptr_->ref_resolver()) {
    if (!key_serializer.is_none()) {
      key_serializer_obj_ = std::move(key_serializer);
      key_serializer_ = nb::cast<Serializer *>(key_serializer_obj_);
      key_is_python_ = is_python_serializer(key_serializer_obj_);
      key_tracking_ref_ = key_serializer_->need_to_write_ref();
      if (key_tracking_ref.has_value()) {
        key_tracking_ref_ = key_tracking_ref.value() && fory_ptr_->track_ref();
      }
    }
    if (!value_serializer.is_none()) {
      value_serializer_obj_ = std::move(value_serializer);
      value_serializer_ = nb::cast<Serializer *>(value_serializer_obj_);
      value_is_python_ = is_python_serializer(value_serializer_obj_);
      value_tracking_ref_ = value_serializer_->need_to_write_ref();
      if (value_tracking_ref.has_value()) {
        value_tracking_ref_ =
            value_tracking_ref.value() && fory_ptr_->track_ref();
      }
    }
  }

  void write(BufferView &buffer, nb::handle obj) override {
    PyObject *dict_obj = obj.ptr();
    int32_t length = static_cast<int32_t>(PyDict_Size(dict_obj));
    buffer.write_var_uint32(static_cast<uint32_t>(length));
    if (length == 0) {
      return;
    }
    Py_ssize_t pos = 0;
    PyObject *key = nullptr;
    PyObject *value = nullptr;
    int has_next = PyDict_Next(dict_obj, &pos, &key, &value);
    if (!has_next) {
      return;
    }
    bool is_py = !fory_ptr_->xlang();
    nb::object key_serializer_obj = key_serializer_obj_;
    nb::object value_serializer_obj = value_serializer_obj_;
    bool key_is_python = key_is_python_;
    bool value_is_python = value_is_python_;
    while (has_next != 0) {
      while (has_next != 0) {
        if (key != Py_None) {
          if (value != Py_None) {
            break;
          }
          if (key_serializer_ != nullptr) {
            bool key_write_ref = key_tracking_ref_;
            if (key_write_ref) {
              buffer.write_int8(
                  static_cast<int8_t>(kNullValueKeyDeclTypeTrackingRef));
              if (!ref_resolver_->write_ref_or_null(buffer, nb::borrow(key))) {
                write_obj(key_serializer_, key_serializer_obj, key_is_python,
                          buffer, nb::borrow(key), is_py);
              }
            } else {
              buffer.write_int8(static_cast<int8_t>(kNullValueKeyDeclType));
              write_obj(key_serializer_, key_serializer_obj, key_is_python,
                        buffer, nb::borrow(key), is_py);
            }
          } else {
            buffer.write_int8(
                static_cast<int8_t>(kValueHasNull | kTrackingKeyRef));
            write_ref(buffer, nb::borrow(key), is_py);
          }
        } else {
          if (value != Py_None) {
            if (value_serializer_ != nullptr) {
              bool value_write_ref = value_tracking_ref_;
              if (value_write_ref) {
                buffer.write_int8(
                    static_cast<int8_t>(kNullKeyValueDeclTypeTrackingRef));
                if (!ref_resolver_->write_ref_or_null(buffer,
                                                      nb::borrow(value))) {
                  write_obj(value_serializer_, value_serializer_obj,
                            value_is_python, buffer, nb::borrow(value), is_py);
                }
              } else {
                buffer.write_int8(static_cast<int8_t>(kNullKeyValueDeclType));
                write_obj(value_serializer_, value_serializer_obj,
                          value_is_python, buffer, nb::borrow(value), is_py);
              }
            } else {
              buffer.write_int8(
                  static_cast<int8_t>(kKeyHasNull | kTrackingValueRef));
              write_ref(buffer, nb::borrow(value), is_py);
            }
          } else {
            buffer.write_int8(static_cast<int8_t>(kKvNull));
          }
        }
        has_next = PyDict_Next(dict_obj, &pos, &key, &value);
      }
      if (has_next == 0) {
        break;
      }
      nb::object key_cls = nb::borrow(Py_TYPE(key));
      nb::object value_cls = nb::borrow(Py_TYPE(value));
      buffer.write_int16(-1);
      int32_t chunk_size_offset = buffer.get_writer_index() - 1;
      int32_t chunk_header = 0;
      Serializer *key_serializer = key_serializer_;
      Serializer *value_serializer = value_serializer_;
      nb::object key_serializer_obj_local = key_serializer_obj;
      nb::object value_serializer_obj_local = value_serializer_obj;
      bool key_is_python_local = key_is_python;
      bool value_is_python_local = value_is_python;
      if (key_serializer != nullptr) {
        chunk_header |= kKeyDeclType;
      } else {
        TypeInfo *key_type_info = type_resolver_->get_type_info(key_cls, true);
        type_resolver_->write_type_info(buffer, key_type_info);
        key_serializer_obj_local = key_type_info->serializer();
        key_serializer = nb::cast<Serializer *>(key_serializer_obj_local);
        key_is_python_local = is_python_serializer(key_serializer_obj_local);
      }
      if (value_serializer != nullptr) {
        chunk_header |= kValueDeclType;
      } else {
        TypeInfo *value_type_info =
            type_resolver_->get_type_info(value_cls, true);
        type_resolver_->write_type_info(buffer, value_type_info);
        value_serializer_obj_local = value_type_info->serializer();
        value_serializer = nb::cast<Serializer *>(value_serializer_obj_local);
        value_is_python_local =
            is_python_serializer(value_serializer_obj_local);
      }
      bool key_write_ref = key_serializer_ != nullptr
                               ? key_tracking_ref_
                               : key_serializer->need_to_write_ref();
      bool value_write_ref = value_serializer_ != nullptr
                                 ? value_tracking_ref_
                                 : value_serializer->need_to_write_ref();
      if (key_write_ref) {
        chunk_header |= kTrackingKeyRef;
      }
      if (value_write_ref) {
        chunk_header |= kTrackingValueRef;
      }
      buffer.put_int8(chunk_size_offset - 1, static_cast<int8_t>(chunk_header));
      int32_t chunk_size = 0;
      while (true) {
        if (key == Py_None || value == Py_None ||
            Py_TYPE(key) != reinterpret_cast<PyTypeObject *>(key_cls.ptr()) ||
            Py_TYPE(value) !=
                reinterpret_cast<PyTypeObject *>(value_cls.ptr())) {
          break;
        }
        if (!key_write_ref ||
            !ref_resolver_->write_ref_or_null(buffer, nb::borrow(key))) {
          write_obj(key_serializer, key_serializer_obj_local,
                    key_is_python_local, buffer, nb::borrow(key), is_py);
        }
        if (!value_write_ref ||
            !ref_resolver_->write_ref_or_null(buffer, nb::borrow(value))) {
          write_obj(value_serializer, value_serializer_obj_local,
                    value_is_python_local, buffer, nb::borrow(value), is_py);
        }
        chunk_size += 1;
        has_next = PyDict_Next(dict_obj, &pos, &key, &value);
        if (has_next == 0 || chunk_size == kMaxChunkSize) {
          break;
        }
      }
      buffer.put_int8(chunk_size_offset, static_cast<int8_t>(chunk_size));
    }
  }

  void xwrite(BufferView &buffer, nb::handle obj) override {
    write(buffer, obj);
  }

  nb::object read(BufferView &buffer) override {
    int32_t size = static_cast<int32_t>(buffer.read_var_uint32());
    nb::dict map_obj = nb::steal<nb::dict>(_PyDict_NewPresized(size));
    ref_resolver_->reference(map_obj);
    int32_t chunk_header = 0;
    if (size != 0) {
      chunk_header = buffer.read_uint8();
    }
    Serializer *key_serializer = key_serializer_;
    Serializer *value_serializer = value_serializer_;
    bool is_py = !fory_ptr_->xlang();
    nb::object key_serializer_obj = key_serializer_obj_;
    nb::object value_serializer_obj = value_serializer_obj_;
    bool key_is_python = key_is_python_;
    bool value_is_python = value_is_python_;
    fory_ptr_->inc_depth();
    while (size > 0) {
      while (true) {
        bool key_has_null = (chunk_header & kKeyHasNull) != 0;
        bool value_has_null = (chunk_header & kValueHasNull) != 0;
        if (!key_has_null) {
          if (!value_has_null) {
            break;
          }
          bool track_key_ref = (chunk_header & kTrackingKeyRef) != 0;
          nb::object key;
          if (chunk_header & kKeyDeclType) {
            key = read_with_optional_ref(
                buffer, ref_resolver_, track_key_ref,
                [&]() {
                  return read_obj(key_serializer, key_serializer_obj,
                                  key_is_python, buffer, is_py);
                },
                [&]() {
                  return read_obj_no_ref(key_serializer, key_serializer_obj,
                                         key_is_python, buffer, is_py);
                });
          } else {
            key = read_ref(buffer, is_py);
          }
          PyDict_SetItem(map_obj.ptr(), key.ptr(), Py_None);
        } else {
          if (!value_has_null) {
            bool track_value_ref = (chunk_header & kTrackingValueRef) != 0;
            nb::object value;
            if (chunk_header & kValueDeclType) {
              value = read_with_optional_ref(
                  buffer, ref_resolver_, track_value_ref,
                  [&]() {
                    return read_obj(value_serializer, value_serializer_obj,
                                    value_is_python, buffer, is_py);
                  },
                  [&]() {
                    return read_obj_no_ref(value_serializer,
                                           value_serializer_obj,
                                           value_is_python, buffer, is_py);
                  });
            } else {
              value = read_ref(buffer, is_py);
            }
            PyDict_SetItem(map_obj.ptr(), Py_None, value.ptr());
          } else {
            PyDict_SetItem(map_obj.ptr(), Py_None, Py_None);
          }
        }
        size -= 1;
        if (size == 0) {
          fory_ptr_->dec_depth();
          return map_obj;
        }
        chunk_header = buffer.read_uint8();
      }

      bool track_key_ref = (chunk_header & kTrackingKeyRef) != 0;
      bool track_value_ref = (chunk_header & kTrackingValueRef) != 0;
      bool key_is_declared_type = (chunk_header & kKeyDeclType) != 0;
      bool value_is_declared_type = (chunk_header & kValueDeclType) != 0;
      int32_t chunk_size = buffer.read_uint8();
      if (!key_is_declared_type) {
        TypeInfo *key_type_info = type_resolver_->read_type_info(buffer);
        key_serializer_obj = key_type_info->serializer();
        key_serializer = nb::cast<Serializer *>(key_serializer_obj);
        key_is_python = is_python_serializer(key_serializer_obj);
      }
      if (!value_is_declared_type) {
        TypeInfo *value_type_info = type_resolver_->read_type_info(buffer);
        value_serializer_obj = value_type_info->serializer();
        value_serializer = nb::cast<Serializer *>(value_serializer_obj);
        value_is_python = is_python_serializer(value_serializer_obj);
      }
      for (int32_t i = 0; i < chunk_size; ++i) {
        nb::object key = read_with_optional_ref(
            buffer, ref_resolver_, track_key_ref,
            [&]() {
              return read_obj(key_serializer, key_serializer_obj, key_is_python,
                              buffer, is_py);
            },
            [&]() {
              return read_obj_no_ref(key_serializer, key_serializer_obj,
                                     key_is_python, buffer, is_py);
            });
        nb::object value = read_with_optional_ref(
            buffer, ref_resolver_, track_value_ref,
            [&]() {
              return read_obj(value_serializer, value_serializer_obj,
                              value_is_python, buffer, is_py);
            },
            [&]() {
              return read_obj_no_ref(value_serializer, value_serializer_obj,
                                     value_is_python, buffer, is_py);
            });
        PyDict_SetItem(map_obj.ptr(), key.ptr(), value.ptr());
        size -= 1;
      }
      if (size != 0) {
        chunk_header = buffer.read_uint8();
      }
    }
    fory_ptr_->dec_depth();
    return map_obj;
  }

  nb::object xread(BufferView &buffer) override { return read(buffer); }

private:
  void write_obj(Serializer *serializer, const nb::object &serializer_obj,
                 bool serializer_is_python, BufferView &buffer, nb::handle obj,
                 bool is_py) {
    if (is_py) {
      serializer_write(serializer, serializer_obj, buffer, obj,
                       serializer_is_python);
    } else {
      serializer_xwrite(serializer, serializer_obj, buffer, obj,
                        serializer_is_python);
    }
  }

  nb::object read_obj(Serializer *serializer, const nb::object &serializer_obj,
                      bool serializer_is_python, BufferView &buffer,
                      bool is_py) {
    return is_py ? serializer_read(serializer, serializer_obj, buffer,
                                   serializer_is_python)
                 : serializer_xread(serializer, serializer_obj, buffer,
                                    serializer_is_python);
  }

  nb::object read_obj_no_ref(Serializer *serializer,
                             const nb::object &serializer_obj,
                             bool serializer_is_python, BufferView &buffer,
                             bool is_py) {
    return is_py ? serializer_read(serializer, serializer_obj, buffer,
                                   serializer_is_python)
                 : fory_ptr_->xread_no_ref(buffer, serializer, serializer_obj);
  }

  void write_ref(BufferView &buffer, nb::handle obj, bool is_py) {
    if (is_py) {
      fory_ptr_->write_ref(buffer, obj);
    } else {
      fory_ptr_->xwrite_ref(buffer, obj, nullptr, nb::none());
    }
  }

  nb::object read_ref(BufferView &buffer, bool is_py) {
    return is_py ? fory_ptr_->read_ref(buffer)
                 : fory_ptr_->xread_ref(buffer, nullptr, nb::none());
  }

  TypeResolver *type_resolver_;
  MapRefResolver *ref_resolver_;
  nb::object key_serializer_obj_{nb::none()};
  nb::object value_serializer_obj_{nb::none()};
  Serializer *key_serializer_{nullptr};
  Serializer *value_serializer_{nullptr};
  bool key_tracking_ref_{false};
  bool value_tracking_ref_{false};
  bool key_is_python_{false};
  bool value_is_python_{false};
};

} // namespace
namespace {
void cleanup_tracked_fory_instances() {
  try {
    for (PyObject *weak : g_fory_weakrefs) {
      if (weak == nullptr) {
        continue;
      }
      nb::object weak_obj = nb::steal(weak);
      PyObject *obj = PyWeakref_GetObject(weak_obj.ptr());
      if (obj == nullptr || obj == Py_None) {
        continue;
      }
      if (PyObject_TypeCheck(
              obj, reinterpret_cast<PyTypeObject *>(nb::type<Fory>().ptr()))) {
        nb::cast<Fory *>(nb::borrow(obj))->close();
      }
    }
  } catch (...) {
    PyErr_Clear();
  }
  g_fory_weakrefs.clear();
}
} // namespace

NB_MODULE(serialization, m) {
  const char *env = std::getenv("ENABLE_FORY_CYTHON_SERIALIZATION");
  bool enable = true;
  if (env != nullptr) {
    std::string val(env);
    for (auto &c : val) {
      c = static_cast<char>(std::tolower(static_cast<unsigned char>(c)));
    }
    enable = (val == "1" || val == "true");
  }
  m.attr("ENABLE_FORY_CYTHON_SERIALIZATION") = enable;

  nb::object raise_fory_error =
      nb::module_::import_("pyfory.error").attr("raise_fory_error");
  g_raise_fory_error = raise_fory_error.ptr();
  nb::object weakref_ref = nb::module_::import_("weakref").attr("ref");
  g_weakref_ref = weakref_ref.ptr();
  nb::object is_primitive_type =
      nb::module_::import_("pyfory.types").attr("is_primitive_type");
  g_is_primitive_type = is_primitive_type.ptr();
  nb::object encoding_enum =
      nb::module_::import_("pyfory.meta.metastring").attr("Encoding");
  g_encoding_enum = encoding_enum.ptr();
  nb::object meta_string_decoder_cls =
      nb::module_::import_("pyfory.meta.metastring").attr("MetaStringDecoder");
  g_meta_string_decoder_cls = meta_string_decoder_cls.ptr();
  nb::object datetime_module = nb::module_::import_("datetime");
  nb::object datetime_class = datetime_module.attr("datetime");
  nb::object date_class = datetime_module.attr("date");
  nb::object timezone_utc = datetime_module.attr("timezone").attr("utc");
  g_datetime_module = datetime_module.ptr();
  g_datetime_class = datetime_class.ptr();
  g_date_class = date_class.ptr();
  g_timezone_utc = timezone_utc.ptr();
  g_time_module = nb::module_::import_("time").ptr();
  g_platform_module = nb::module_::import_("platform").ptr();
  g_buffer_class = nb::module_::import_("pyfory.buffer").attr("Buffer").ptr();
  nb::object mmh3_hash_buffer =
      nb::module_::import_("pyfory.lib.mmh3").attr("hash_buffer");
  g_mmh3_hash_buffer = mmh3_hash_buffer.ptr();

  nb::object fmod = nb::module_::import_("pyfory._fory");
  g_not_null_int64_flag = nb::cast<int32_t>(fmod.attr("NOT_NULL_INT64_FLAG"));
  g_not_null_float64_flag =
      nb::cast<int32_t>(fmod.attr("NOT_NULL_FLOAT64_FLAG"));
  g_not_null_bool_flag = nb::cast<int32_t>(fmod.attr("NOT_NULL_BOOL_FLAG"));
  g_not_null_string_flag = nb::cast<int32_t>(fmod.attr("NOT_NULL_STRING_FLAG"));
  g_int64_type_id = nb::cast<int8_t>(fmod.attr("INT64_TYPE_ID"));
  g_float64_type_id = nb::cast<int8_t>(fmod.attr("FLOAT64_TYPE_ID"));
  g_bool_type_id = nb::cast<int8_t>(fmod.attr("BOOL_TYPE_ID"));
  g_string_type_id = nb::cast<int8_t>(fmod.attr("STRING_TYPE_ID"));

  nb::module_::import_("atexit").attr("register")(
      nb::cpp_function([]() { cleanup_tracked_fory_instances(); }));

  m.def(
      "write_nullable_pybool",
      [](nb::object buffer, nb::object value) {
        BufferView view(buffer);
        if (value.is_none()) {
          view.write_int8(kNullFlag);
        } else {
          view.write_int8(kNotNullValueFlag);
          view.write_int8(PyObject_IsTrue(value.ptr()) ? 1 : 0);
        }
      },
      "buffer"_a, "value"_a.none());
  m.def(
      "write_nullable_int8",
      [](nb::object buffer, nb::object value) {
        BufferView view(buffer);
        if (value.is_none()) {
          view.write_int8(kNullFlag);
        } else {
          view.write_int8(kNotNullValueFlag);
          view.write_int8(static_cast<int8_t>(nb::cast<int64_t>(value)));
        }
      },
      "buffer"_a, "value"_a.none());
  m.def(
      "write_nullable_int16",
      [](nb::object buffer, nb::object value) {
        BufferView view(buffer);
        if (value.is_none()) {
          view.write_int8(kNullFlag);
        } else {
          view.write_int8(kNotNullValueFlag);
          view.write_int16(static_cast<int16_t>(nb::cast<int64_t>(value)));
        }
      },
      "buffer"_a, "value"_a.none());
  m.def(
      "write_nullable_int32",
      [](nb::object buffer, nb::object value) {
        BufferView view(buffer);
        if (value.is_none()) {
          view.write_int8(kNullFlag);
        } else {
          view.write_int8(kNotNullValueFlag);
          view.write_var_int32(static_cast<int32_t>(nb::cast<int64_t>(value)));
        }
      },
      "buffer"_a, "value"_a.none());
  m.def(
      "write_nullable_pyint64",
      [](nb::object buffer, nb::object value) {
        BufferView view(buffer);
        if (value.is_none()) {
          view.write_int8(kNullFlag);
        } else {
          view.write_int8(kNotNullValueFlag);
          view.write_var_int64(static_cast<int64_t>(nb::cast<int64_t>(value)));
        }
      },
      "buffer"_a, "value"_a.none());
  m.def(
      "write_nullable_float32",
      [](nb::object buffer, nb::object value) {
        BufferView view(buffer);
        if (value.is_none()) {
          view.write_int8(kNullFlag);
        } else {
          view.write_int8(kNotNullValueFlag);
          view.write_float(static_cast<float>(nb::cast<double>(value)));
        }
      },
      "buffer"_a, "value"_a.none());
  m.def(
      "write_nullable_pyfloat64",
      [](nb::object buffer, nb::object value) {
        BufferView view(buffer);
        if (value.is_none()) {
          view.write_int8(kNullFlag);
        } else {
          view.write_int8(kNotNullValueFlag);
          view.write_double(static_cast<double>(nb::cast<double>(value)));
        }
      },
      "buffer"_a, "value"_a.none());
  m.def(
      "write_nullable_pystr",
      [](nb::object buffer, nb::object value) {
        BufferView view(buffer);
        if (value.is_none()) {
          view.write_int8(kNullFlag);
        } else {
          view.write_int8(kNotNullValueFlag);
          view.write_string(value);
        }
      },
      "buffer"_a, "value"_a.none());

  m.def("read_nullable_pybool", [](nb::object buffer) -> nb::object {
    BufferView view(buffer);
    if (view.read_int8() == kNotNullValueFlag) {
      return nb::bool_(view.read_int8() != 0);
    }
    return nb::none();
  });
  m.def("read_nullable_int8", [](nb::object buffer) -> nb::object {
    BufferView view(buffer);
    if (view.read_int8() == kNotNullValueFlag) {
      return nb::int_(view.read_int8());
    }
    return nb::none();
  });
  m.def("read_nullable_int16", [](nb::object buffer) -> nb::object {
    BufferView view(buffer);
    if (view.read_int8() == kNotNullValueFlag) {
      return nb::int_(view.read_int16());
    }
    return nb::none();
  });
  m.def("read_nullable_int32", [](nb::object buffer) -> nb::object {
    BufferView view(buffer);
    if (view.read_int8() == kNotNullValueFlag) {
      return nb::int_(view.read_var_int32());
    }
    return nb::none();
  });
  m.def("read_nullable_pyint64", [](nb::object buffer) -> nb::object {
    BufferView view(buffer);
    if (view.read_int8() == kNotNullValueFlag) {
      return nb::int_(view.read_var_int64());
    }
    return nb::none();
  });
  m.def("read_nullable_float32", [](nb::object buffer) -> nb::object {
    BufferView view(buffer);
    if (view.read_int8() == kNotNullValueFlag) {
      return nb::float_(view.read_float());
    }
    return nb::none();
  });
  m.def("read_nullable_pyfloat64", [](nb::object buffer) -> nb::object {
    BufferView view(buffer);
    if (view.read_int8() == kNotNullValueFlag) {
      return nb::float_(view.read_double());
    }
    return nb::none();
  });
  m.def("read_nullable_pystr", [](nb::object buffer) -> nb::object {
    BufferView view(buffer);
    if (view.read_int8() == kNotNullValueFlag) {
      return view.read_string();
    }
    return nb::none();
  });

  nb::class_<MetaStringBytes>(m, "MetaStringBytes")
      .def(nb::init<nb::bytes, int64_t>(), "data"_a, "hashcode"_a)
      .def_prop_ro("data", &MetaStringBytes::data)
      .def_prop_ro("length", &MetaStringBytes::length)
      .def_prop_ro("encoding", &MetaStringBytes::encoding)
      .def_prop_ro("hashcode", &MetaStringBytes::hashcode)
      .def_prop_rw("dynamic_write_string_id",
                   &MetaStringBytes::dynamic_write_string_id,
                   &MetaStringBytes::set_dynamic_write_string_id)
      .def("decode", &MetaStringBytes::decode)
      .def("__repr__",
           [](const MetaStringBytes &self) {
             return nb::str("MetaStringBytes(data={}, hashcode={})")
                 .format(self.data(), self.hashcode());
           })
      .def("__hash__",
           [](const MetaStringBytes &self) { return self.hashcode(); })
      .def("__eq__",
           [](const MetaStringBytes &self, const MetaStringBytes &other) {
             return self == other;
           });

  nb::class_<MetaStringResolver>(m, "MetaStringResolver")
      .def(nb::init<>())
      .def("write_meta_string_bytes",
           [](MetaStringResolver &self, nb::object buffer,
              MetaStringBytes &bytes) {
             BufferView view(buffer);
             self.write_meta_string_bytes(view, bytes);
           })
      .def(
          "read_meta_string_bytes",
          [](MetaStringResolver &self, nb::object buffer) {
            BufferView view(buffer);
            return self.read_meta_string_bytes(view);
          },
          nb::rv_policy::reference)
      .def("get_metastr_bytes", &MetaStringResolver::get_metastr_bytes,
           nb::rv_policy::reference)
      .def("reset_read", &MetaStringResolver::reset_read)
      .def("reset_write", &MetaStringResolver::reset_write);

  nb::class_<TypeInfo>(m, "TypeInfo")
      .def(nb::init<>())
      .def(nb::init<nb::object, int, uint32_t, nb::object, nb::object,
                    nb::object, bool, nb::object>(),
           "cls"_a.none() = nb::none(), "type_id"_a = kNoTypeId,
           "user_type_id"_a = kNoUserTypeId, "serializer"_a.none() = nb::none(),
           "namespace_bytes"_a.none() = nb::none(),
           "typename_bytes"_a.none() = nb::none(), "dynamic_type"_a = false,
           "type_def"_a.none() = nb::none())
      .def_prop_rw("cls", &TypeInfo::cls, &TypeInfo::set_cls,
                   nb::for_setter("value"_a.none()))
      .def_prop_rw("type_id", &TypeInfo::type_id, &TypeInfo::set_type_id)
      .def_prop_rw("user_type_id", &TypeInfo::user_type_id,
                   &TypeInfo::set_user_type_id)
      .def_prop_rw("serializer", &TypeInfo::serializer,
                   &TypeInfo::set_serializer, nb::for_setter("value"_a.none()))
      .def_prop_rw("namespace_bytes", &TypeInfo::namespace_bytes,
                   &TypeInfo::set_namespace_bytes,
                   nb::for_setter("value"_a.none()))
      .def_prop_rw("typename_bytes", &TypeInfo::typename_bytes,
                   &TypeInfo::set_typename_bytes,
                   nb::for_setter("value"_a.none()))
      .def_prop_rw("dynamic_type", &TypeInfo::dynamic_type,
                   &TypeInfo::set_dynamic_type)
      .def_prop_rw("type_def", &TypeInfo::type_def, &TypeInfo::set_type_def,
                   nb::for_setter("value"_a.none()))
      .def("decode_namespace", &TypeInfo::decode_namespace)
      .def("decode_typename", &TypeInfo::decode_typename)
      .def("__repr__", [](const TypeInfo &self) {
        return nb::
            str("TypeInfo(cls={}, type_id={}, user_type_id={}, serializer={})")
                .format(self.cls(), self.type_id(), self.user_type_id(),
                        self.serializer());
      });

  nb::class_<MapRefResolver>(m, "MapRefResolver")
      .def(nb::init<bool>())
      .def(
          "write_ref_or_null",
          [](MapRefResolver &self, nb::object buffer, nb::object obj) {
            BufferView view(buffer);
            return self.write_ref_or_null(view, obj);
          },
          "buffer"_a, "obj"_a.none())
      .def("read_ref_or_null",
           [](MapRefResolver &self, nb::object buffer) {
             BufferView view(buffer);
             return self.read_ref_or_null(view);
           })
      .def("preserve_ref_id", &MapRefResolver::preserve_ref_id)
      .def("try_preserve_ref_id",
           [](MapRefResolver &self, nb::object buffer) {
             BufferView view(buffer);
             return self.try_preserve_ref_id(view);
           })
      .def("last_preserved_ref_id", &MapRefResolver::last_preserved_ref_id)
      .def("reference", &MapRefResolver::reference)
      .def("get_read_object", &MapRefResolver::get_read_object,
           "id_"_a.none() = nb::none())
      .def("set_read_object", &MapRefResolver::set_read_object)
      .def("reset", &MapRefResolver::reset)
      .def("reset_write", &MapRefResolver::reset_write)
      .def("reset_read", &MapRefResolver::reset_read);

  nb::class_<TypeResolver>(m, "TypeResolver")
      .def(nb::init<nb::object, bool, nb::object>(), "fory"_a,
           "meta_share"_a = false, "meta_compressor"_a.none() = nb::none())
      .def("initialize", &TypeResolver::initialize)
      .def_prop_ro("fory", &TypeResolver::fory)
      .def("register_type", &TypeResolver::register_type, "cls"_a,
           "type_id"_a = -1, "namespace"_a.none() = nb::none(),
           "typename"_a.none() = nb::none(), "serializer"_a.none() = nb::none())
      .def("register_union", &TypeResolver::register_union, "cls"_a,
           "type_id"_a = -1, "namespace"_a.none() = nb::none(),
           "typename"_a.none() = nb::none(), "serializer"_a.none() = nb::none())
      .def("register_serializer", &TypeResolver::register_serializer)
      .def("get_serializer", &TypeResolver::get_serializer)
      .def("get_type_info", &TypeResolver::get_type_info, "cls"_a,
           "create"_a = true, nb::rv_policy::reference)
      .def("is_registered_by_name", &TypeResolver::is_registered_by_name)
      .def("is_registered_by_id", &TypeResolver::is_registered_by_id)
      .def("get_registered_name", &TypeResolver::get_registered_name)
      .def("get_registered_id", &TypeResolver::get_registered_id)
      .def("get_registered_user_type_id",
           &TypeResolver::get_registered_user_type_id)
      .def("get_registered_type_ids", &TypeResolver::get_registered_type_ids)
      .def("write_type_info",
           [](TypeResolver &self, nb::object buffer, TypeInfo *typeinfo) {
             BufferView view(buffer);
             self.write_type_info(view, typeinfo);
           })
      .def(
          "read_type_info",
          [](TypeResolver &self, nb::object buffer) {
            BufferView view(buffer);
            return self.read_type_info(view);
          },
          nb::rv_policy::reference)
      .def("get_type_info_by_id", &TypeResolver::get_type_info_by_id,
           nb::rv_policy::reference)
      .def("get_user_type_info_by_id", &TypeResolver::get_user_type_info_by_id,
           nb::rv_policy::reference)
      .def("get_type_info_by_name", &TypeResolver::get_type_info_by_name)
      .def("_set_type_info", &TypeResolver::set_type_info)
      .def("get_meta_compressor", &TypeResolver::get_meta_compressor)
      .def("write_shared_type_meta",
           [](TypeResolver &self, nb::object buffer, TypeInfo *typeinfo) {
             BufferView view(buffer);
             self.write_shared_type_meta(view, typeinfo);
           })
      .def("read_shared_type_meta",
           [](TypeResolver &self, nb::object buffer) {
             BufferView view(buffer);
             return self.read_shared_type_meta(view);
           })
      .def("_read_and_build_type_info",
           [](TypeResolver &self, nb::object buffer) {
             BufferView view(buffer);
             return self.read_and_build_type_info(view);
           });

  nb::class_<MetaContext>(m, "MetaContext")
      .def(nb::init<nb::object>())
      .def("write_shared_type_info",
           [](MetaContext &self, nb::object buffer, nb::object typeinfo) {
             BufferView view(buffer);
             self.write_shared_type_info(view, typeinfo);
           })
      .def("read_shared_type_info",
           [](MetaContext &self, nb::object buffer) {
             BufferView view(buffer);
             return self.read_shared_type_info(view);
           })
      .def("read_shared_type_info_with_type_id",
           [](MetaContext &self, nb::object buffer, uint8_t type_id) {
             BufferView view(buffer);
             return self.read_shared_type_info_with_type_id(view, type_id);
           })
      .def("reset_write", &MetaContext::reset_write)
      .def("reset_read", &MetaContext::reset_read)
      .def("__repr__", &MetaContext::repr);

  nb::class_<SerializationContext>(m, "SerializationContext")
      .def(nb::init<nb::object, bool>(), "fory"_a,
           "scoped_meta_share_enabled"_a = false)
      .def("add", &SerializationContext::add)
      .def("__contains__", &SerializationContext::contains)
      .def("__getitem__", &SerializationContext::get_item)
      .def("get", &SerializationContext::get)
      .def("reset", &SerializationContext::reset)
      .def("reset_write", &SerializationContext::reset_write)
      .def("reset_read", &SerializationContext::reset_read)
      .def_prop_ro("scoped_meta_share_enabled",
                   &SerializationContext::scoped_meta_share_enabled)
      .def_prop_rw("meta_context", &SerializationContext::meta_context,
                   &SerializationContext::set_meta_context,
                   nb::for_setter("value"_a.none()));

  struct PySerializer : Serializer {
    NB_TRAMPOLINE(Serializer, 5);
    PySerializer(nb::object fory, nb::object type)
        : Serializer(std::move(fory), std::move(type)) {}
    void write(BufferView &buffer, nb::handle value) override {
      NB_OVERRIDE(write, buffer, value);
    }
    nb::object read(BufferView &buffer) override { NB_OVERRIDE(read, buffer); }
    void xwrite(BufferView &buffer, nb::handle value) override {
      NB_OVERRIDE(xwrite, buffer, value);
    }
    nb::object xread(BufferView &buffer) override {
      NB_OVERRIDE(xread, buffer);
    }
    bool support_subclass() const override { NB_OVERRIDE(support_subclass); }
  };

  struct PyXlangCompatibleSerializer : XlangCompatibleSerializer {
    NB_TRAMPOLINE(XlangCompatibleSerializer, 5);
    PyXlangCompatibleSerializer(nb::object fory, nb::object type)
        : XlangCompatibleSerializer(std::move(fory), std::move(type)) {}
    void write(BufferView &buffer, nb::handle value) override {
      NB_OVERRIDE(write, buffer, value);
    }
    nb::object read(BufferView &buffer) override { NB_OVERRIDE(read, buffer); }
    void xwrite(BufferView &buffer, nb::handle value) override {
      NB_OVERRIDE(xwrite, buffer, value);
    }
    nb::object xread(BufferView &buffer) override {
      NB_OVERRIDE(xread, buffer);
    }
    bool support_subclass() const override { NB_OVERRIDE(support_subclass); }
  };

  nb::class_<Serializer, PySerializer>(m, "Serializer")
      .def(nb::init<nb::object, nb::object>(), "fory"_a, "type"_a.none())
      .def(
          "write",
          [](Serializer &self, nb::object buffer, nb::object value) {
            BufferView view(buffer);
            self.write(view, value);
          },
          "buffer"_a, "value"_a.none())
      .def("read",
           [](Serializer &self, nb::object buffer) {
             BufferView view(buffer);
             return self.read(view);
           })
      .def(
          "xwrite",
          [](Serializer &self, nb::object buffer, nb::object value) {
            BufferView view(buffer);
            self.xwrite(view, value);
          },
          "buffer"_a, "value"_a.none())
      .def("xread",
           [](Serializer &self, nb::object buffer) {
             BufferView view(buffer);
             return self.xread(view);
           })
      .def_prop_ro("fory", &Serializer::fory)
      .def_prop_ro("type_", &Serializer::type)
      .def_prop_rw("need_to_write_ref", &Serializer::need_to_write_ref,
                   &Serializer::set_need_to_write_ref)
      .def_static("support_subclass", []() { return false; });

  nb::class_<XlangCompatibleSerializer, Serializer,
             PyXlangCompatibleSerializer>(m, "XlangCompatibleSerializer")
      .def(nb::init<nb::object, nb::object>());

  nb::class_<BooleanSerializer, XlangCompatibleSerializer>(m,
                                                           "BooleanSerializer")
      .def(nb::init<nb::object, nb::object>());
  nb::class_<ByteSerializer, XlangCompatibleSerializer>(m, "ByteSerializer")
      .def(nb::init<nb::object, nb::object>());
  nb::class_<Int16Serializer, XlangCompatibleSerializer>(m, "Int16Serializer")
      .def(nb::init<nb::object, nb::object>());
  nb::class_<Int32Serializer, XlangCompatibleSerializer>(m, "Int32Serializer")
      .def(nb::init<nb::object, nb::object>());
  nb::class_<Int64Serializer, XlangCompatibleSerializer>(m, "Int64Serializer")
      .def(nb::init<nb::object, nb::object>());
  nb::class_<FixedInt32Serializer, XlangCompatibleSerializer>(
      m, "FixedInt32Serializer")
      .def(nb::init<nb::object, nb::object>());
  nb::class_<FixedInt64Serializer, XlangCompatibleSerializer>(
      m, "FixedInt64Serializer")
      .def(nb::init<nb::object, nb::object>());
  nb::class_<Varint32Serializer, XlangCompatibleSerializer>(
      m, "Varint32Serializer")
      .def(nb::init<nb::object, nb::object>());
  nb::class_<Varint64Serializer, XlangCompatibleSerializer>(
      m, "Varint64Serializer")
      .def(nb::init<nb::object, nb::object>());
  nb::class_<TaggedInt64Serializer, XlangCompatibleSerializer>(
      m, "TaggedInt64Serializer")
      .def(nb::init<nb::object, nb::object>());
  nb::class_<Uint8Serializer, XlangCompatibleSerializer>(m, "Uint8Serializer")
      .def(nb::init<nb::object, nb::object>());
  nb::class_<Uint16Serializer, XlangCompatibleSerializer>(m, "Uint16Serializer")
      .def(nb::init<nb::object, nb::object>());
  nb::class_<Uint32Serializer, XlangCompatibleSerializer>(m, "Uint32Serializer")
      .def(nb::init<nb::object, nb::object>());
  nb::class_<VarUint32Serializer, XlangCompatibleSerializer>(
      m, "VarUint32Serializer")
      .def(nb::init<nb::object, nb::object>());
  nb::class_<Uint64Serializer, XlangCompatibleSerializer>(m, "Uint64Serializer")
      .def(nb::init<nb::object, nb::object>());
  nb::class_<VarUint64Serializer, XlangCompatibleSerializer>(
      m, "VarUint64Serializer")
      .def(nb::init<nb::object, nb::object>());
  nb::class_<TaggedUint64Serializer, XlangCompatibleSerializer>(
      m, "TaggedUint64Serializer")
      .def(nb::init<nb::object, nb::object>());
  nb::class_<Float32Serializer, XlangCompatibleSerializer>(m,
                                                           "Float32Serializer")
      .def(nb::init<nb::object, nb::object>());
  nb::class_<Float64Serializer, XlangCompatibleSerializer>(m,
                                                           "Float64Serializer")
      .def(nb::init<nb::object, nb::object>());
  nb::class_<StringSerializer, XlangCompatibleSerializer>(m, "StringSerializer")
      .def(nb::init<nb::object, nb::object, bool>(), "fory"_a, "type_"_a,
           "track_ref"_a = false);
  nb::class_<DateSerializer, XlangCompatibleSerializer>(m, "DateSerializer")
      .def(nb::init<nb::object, nb::object>());
  nb::class_<TimestampSerializer, XlangCompatibleSerializer>(
      m, "TimestampSerializer")
      .def(nb::init<nb::object, nb::object>());

  nb::class_<CollectionSerializer, Serializer>(m, "CollectionSerializer")
      .def(nb::init<nb::object, nb::object, nb::object, std::optional<bool>>(),
           "fory"_a, "type_"_a, "elem_serializer"_a.none() = nb::none(),
           "elem_tracking_ref"_a.none() = nb::none());

  nb::class_<ListSerializer, CollectionSerializer>(m, "ListSerializer")
      .def(nb::init<nb::object, nb::object, nb::object, std::optional<bool>>(),
           "fory"_a, "type_"_a, "elem_serializer"_a.none() = nb::none(),
           "elem_tracking_ref"_a.none() = nb::none());

  nb::class_<TupleSerializer, ListSerializer>(m, "TupleSerializer")
      .def(nb::init<nb::object, nb::object, nb::object, std::optional<bool>>(),
           "fory"_a, "type_"_a, "elem_serializer"_a.none() = nb::none(),
           "elem_tracking_ref"_a.none() = nb::none());

  nb::class_<StringArraySerializer, ListSerializer>(m, "StringArraySerializer")
      .def(nb::init<nb::object, nb::object>());

  nb::class_<SetSerializer, CollectionSerializer>(m, "SetSerializer")
      .def(nb::init<nb::object, nb::object, nb::object, std::optional<bool>>(),
           "fory"_a, "type_"_a, "elem_serializer"_a.none() = nb::none(),
           "elem_tracking_ref"_a.none() = nb::none());

  nb::class_<MapSerializer, Serializer>(m, "MapSerializer")
      .def(nb::init<nb::object, nb::object, nb::object, nb::object,
                    std::optional<bool>, std::optional<bool>>(),
           "fory"_a, "type_"_a, "key_serializer"_a.none() = nb::none(),
           "value_serializer"_a.none() = nb::none(),
           "key_tracking_ref"_a.none() = nb::none(),
           "value_tracking_ref"_a.none() = nb::none());

  nb::class_<EnumSerializer, Serializer>(m, "EnumSerializer")
      .def(nb::init<nb::object, nb::object>())
      .def_static("support_subclass", []() { return true; });

  nb::class_<SliceSerializer, Serializer>(m, "SliceSerializer")
      .def(nb::init<nb::object, nb::object>());

  nb::class_<Fory>(m, "Fory", nb::is_weak_referenceable())
      .def(
          "__init__",
          [](nb::pointer_and_handle<Fory> self, nb::object xlang,
             nb::object ref, nb::object strict, nb::object policy,
             nb::object compatible, nb::object max_depth,
             nb::object field_nullable, nb::object meta_compressor) {
            new (self.p) Fory();
            nb::inst_mark_ready(self.h);
            bool xlang_b = xlang.is_none() ? false : nb::cast<bool>(xlang);
            bool ref_b = ref.is_none() ? false : nb::cast<bool>(ref);
            bool strict_b = strict.is_none() ? true : nb::cast<bool>(strict);
            bool compatible_b =
                compatible.is_none() ? false : nb::cast<bool>(compatible);
            int32_t max_depth_v =
                max_depth.is_none() ? 50 : nb::cast<int32_t>(max_depth);
            bool field_nullable_b = field_nullable.is_none()
                                        ? false
                                        : nb::cast<bool>(field_nullable);
            nb::object self_obj = nb::borrow(self.h);
            self.p->init(self_obj, xlang_b, ref_b, strict_b, policy,
                         compatible_b, max_depth_v, field_nullable_b,
                         meta_compressor);
          },
          "xlang"_a.none() = false, "ref"_a.none() = false,
          "strict"_a.none() = true, "policy"_a.none() = nb::none(),
          "compatible"_a.none() = false, "max_depth"_a.none() = 50,
          "field_nullable"_a.none() = false,
          "meta_compressor"_a.none() = nb::none())
      .def("register_serializer", &Fory::register_serializer)
      .def(
          "register",
          [](Fory &self, nb::object cls, nb::object type_id, nb::object ns,
             nb::object typename_, nb::object serializer) {
            int type_id_val = type_id.is_none() ? -1 : nb::cast<int>(type_id);
            self.register_type(std::move(cls), type_id_val, std::move(ns),
                               std::move(typename_), std::move(serializer));
          },
          "cls"_a, "type_id"_a.none() = nb::none(),
          "namespace"_a.none() = nb::none(), "typename"_a.none() = nb::none(),
          "serializer"_a.none() = nb::none())
      .def(
          "register_type",
          [](Fory &self, nb::object cls, nb::object type_id, nb::object ns,
             nb::object typename_, nb::object serializer) {
            int type_id_val = type_id.is_none() ? -1 : nb::cast<int>(type_id);
            self.register_type(std::move(cls), type_id_val, std::move(ns),
                               std::move(typename_), std::move(serializer));
          },
          "cls"_a, "type_id"_a.none() = nb::none(),
          "namespace"_a.none() = nb::none(), "typename"_a.none() = nb::none(),
          "serializer"_a.none() = nb::none())
      .def(
          "register_union",
          [](Fory &self, nb::object cls, nb::object type_id, nb::object ns,
             nb::object typename_, nb::object serializer) {
            int type_id_val = type_id.is_none() ? -1 : nb::cast<int>(type_id);
            self.register_union(std::move(cls), type_id_val, std::move(ns),
                                std::move(typename_), std::move(serializer));
          },
          "cls"_a, "type_id"_a.none() = nb::none(),
          "namespace"_a.none() = nb::none(), "typename"_a.none() = nb::none(),
          "serializer"_a.none() = nb::none())
      .def("dumps", &Fory::dumps, "obj"_a.none(),
           "buffer"_a.none() = nb::none(),
           "buffer_callback"_a.none() = nb::none(),
           "unsupported_callback"_a.none() = nb::none())
      .def("loads", &Fory::loads, "buffer"_a, "buffers"_a.none() = nb::none(),
           "unsupported_objects"_a.none() = nb::none())
      .def("serialize", &Fory::serialize, "obj"_a.none(),
           "buffer"_a.none() = nb::none(),
           "buffer_callback"_a.none() = nb::none(),
           "unsupported_callback"_a.none() = nb::none())
      .def("deserialize", &Fory::deserialize, "buffer"_a,
           "buffers"_a.none() = nb::none(),
           "unsupported_objects"_a.none() = nb::none())
      .def(
          "write_ref",
          [](Fory &self, nb::object buffer, nb::object obj) {
            BufferView view(buffer);
            self.write_ref(view, obj);
          },
          "buffer"_a, "obj"_a.none())
      .def(
          "write_no_ref",
          [](Fory &self, nb::object buffer, nb::object obj) {
            BufferView view(buffer);
            self.write_no_ref(view, obj);
          },
          "buffer"_a, "obj"_a.none())
      .def(
          "xwrite_ref",
          [](Fory &self, nb::object buffer, nb::object obj,
             nb::object serializer) {
            BufferView view(buffer);
            Serializer *ser = serializer.is_none()
                                  ? nullptr
                                  : nb::cast<Serializer *>(serializer);
            self.xwrite_ref(view, obj, ser, serializer);
          },
          "buffer"_a, "obj"_a.none(), "serializer"_a.none() = nb::none())
      .def(
          "xwrite_no_ref",
          [](Fory &self, nb::object buffer, nb::object obj,
             nb::object serializer) {
            BufferView view(buffer);
            Serializer *ser = serializer.is_none()
                                  ? nullptr
                                  : nb::cast<Serializer *>(serializer);
            self.xwrite_no_ref(view, obj, ser, serializer);
          },
          "buffer"_a, "obj"_a.none(), "serializer"_a.none() = nb::none())
      .def("read_ref",
           [](Fory &self, nb::object buffer) {
             BufferView view(buffer);
             return self.read_ref(view);
           })
      .def("read_no_ref",
           [](Fory &self, nb::object buffer) {
             BufferView view(buffer);
             return self.read_no_ref(view);
           })
      .def(
          "xread_ref",
          [](Fory &self, nb::object buffer, nb::object serializer) {
            BufferView view(buffer);
            Serializer *ser = serializer.is_none()
                                  ? nullptr
                                  : nb::cast<Serializer *>(serializer);
            return self.xread_ref(view, ser, serializer);
          },
          "buffer"_a, "serializer"_a.none() = nb::none())
      .def(
          "xread_no_ref",
          [](Fory &self, nb::object buffer, nb::object serializer) {
            BufferView view(buffer);
            Serializer *ser = serializer.is_none()
                                  ? nullptr
                                  : nb::cast<Serializer *>(serializer);
            return self.xread_no_ref(view, ser, serializer);
          },
          "buffer"_a, "serializer"_a.none() = nb::none())
      .def("write_buffer_object",
           [](Fory &self, nb::object buffer, nb::object buffer_object) {
             BufferView view(buffer);
             self.write_buffer_object(view, buffer_object);
           })
      .def("read_buffer_object",
           [](Fory &self, nb::object buffer) {
             BufferView view(buffer);
             return self.read_buffer_object(view);
           })
      .def(
          "handle_unsupported_write",
          [](Fory &self, nb::object buffer, nb::object obj) {
            BufferView view(buffer);
            self.handle_unsupported_write(view, obj);
          },
          "buffer"_a, "obj"_a.none())
      .def("handle_unsupported_read",
           [](Fory &self, nb::object buffer) {
             BufferView view(buffer);
             return self.handle_unsupported_read(view);
           })
      .def(
          "write_ref_pyobject",
          [](Fory &self, nb::object buffer, nb::object obj,
             nb::object typeinfo) {
            BufferView view(buffer);
            TypeInfo *info =
                typeinfo.is_none() ? nullptr : nb::cast<TypeInfo *>(typeinfo);
            self.write_ref_pyobject(view, obj, info);
          },
          "buffer"_a, "obj"_a.none(), "typeinfo"_a.none() = nb::none())
      .def("read_ref_pyobject",
           [](Fory &self, nb::object buffer) {
             BufferView view(buffer);
             return self.read_ref_pyobject(view);
           })
      .def("reset_write", &Fory::reset_write)
      .def("reset_read", &Fory::reset_read)
      .def("reset", &Fory::reset)
      .def("close", &Fory::close)
      .def_prop_ro("xlang", &Fory::xlang)
      .def_prop_ro("track_ref", &Fory::track_ref)
      .def_prop_ro("strict", &Fory::strict)
      .def_prop_ro("compatible", &Fory::compatible)
      .def_prop_ro("field_nullable", &Fory::field_nullable)
      .def_prop_ro("policy", &Fory::policy)
      .def_prop_ro("buffer_callback", &Fory::buffer_callback)
      .def_prop_ro("is_peer_out_of_band_enabled",
                   &Fory::is_peer_out_of_band_enabled)
      .def_prop_ro("ref_resolver", &Fory::ref_resolver,
                   nb::rv_policy::reference_internal)
      .def_prop_ro("type_resolver", &Fory::type_resolver,
                   nb::rv_policy::reference_internal)
      .def_prop_ro("metastring_resolver", &Fory::metastring_resolver,
                   nb::rv_policy::reference_internal)
      .def_prop_ro("serialization_context", &Fory::serialization_context,
                   nb::rv_policy::reference_internal)
      .def_prop_ro("buffer", &Fory::buffer);
}
