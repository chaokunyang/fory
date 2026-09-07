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

#include "v8-fast-api-calls.h"
#include <limits>
#include <nan.h>
using v8::Local;
using v8::String;
using v8::Value;

template <typename T> constexpr T RoundUp(T a, T b) {
  return a % b != 0 ? a + b - (a % b) : a;
}

template <typename T, typename U> constexpr T *AlignUp(T *ptr, U alignment) {
  return reinterpret_cast<T *>(
      RoundUp(reinterpret_cast<uintptr_t>(ptr), alignment));
}

uint32_t varUint32Size(uint32_t value) {
  if (value >> 7 == 0) {
    return 1;
  }
  if (value >> 14 == 0) {
    return 2;
  }
  if (value >> 21 == 0) {
    return 3;
  }
  if (value >> 28 == 0) {
    return 4;
  }
  return 5;
}

uint32_t writeVarUint32(uint8_t *dst, uint32_t offset, uint32_t value) {
  if (value >> 7 == 0) {
    dst[offset] = (uint8_t)value;
    return 1;
  }
  if (value >> 14 == 0) {
    dst[offset++] = (uint8_t)((value & 0x7F) | 0x80);
    dst[offset++] = (uint8_t)(value >> 7);
    return 2;
  }
  if (value >> 21 == 0) {
    dst[offset++] = (uint8_t)((value & 0x7F) | 0x80);
    dst[offset++] = (uint8_t)(value >> 7 | 0x80);
    dst[offset++] = (uint8_t)(value >> 14);
    return 3;
  }
  if (value >> 28 == 0) {
    dst[offset++] = (uint8_t)((value & 0x7F) | 0x80);
    dst[offset++] = (uint8_t)(value >> 7 | 0x80);
    dst[offset++] = (uint8_t)(value >> 14 | 0x80);
    dst[offset++] = (uint8_t)(value >> 21);
    return 4;
  }
  dst[offset++] = (uint8_t)((value & 0x7F) | 0x80);
  dst[offset++] = (uint8_t)(value >> 7 | 0x80);
  dst[offset++] = (uint8_t)(value >> 14 | 0x80);
  dst[offset++] = (uint8_t)(value >> 21 | 0x80);
  dst[offset++] = (uint8_t)(value >> 28);
  return 5;
}

enum Encoding { LATIN1, UTF16, UTF8 };

struct StringLayout {
  uint32_t header;
  size_t body_size;
  size_t total_size;
};

bool getStringLayout(size_t length, bool is_one_byte, StringLayout *layout) {
  const size_t char_width = is_one_byte ? 1 : 2;
  const size_t max_body_size = std::numeric_limits<uint32_t>::max() >> 2;
  if (length > max_body_size / char_width) {
    return false;
  }
  const size_t body_size = length * char_width;
  const auto encoding = is_one_byte ? Encoding::LATIN1 : Encoding::UTF16;
  const uint32_t header = (static_cast<uint32_t>(body_size) << 2) | encoding;
  layout->header = header;
  layout->body_size = body_size;
  layout->total_size = varUint32Size(header) + body_size;
  return true;
}

bool hasStringCapacity(size_t capacity, uint32_t offset,
                       const StringLayout &layout) {
  return offset <= capacity && layout.total_size <= capacity - offset &&
         layout.total_size <= std::numeric_limits<uint32_t>::max() - offset;
}

uint32_t writeUCS2(v8::Isolate *isolate, uint8_t *buf, Local<String> str,
                   int flags) {
  uint16_t *const dst = reinterpret_cast<uint16_t *>(buf);

  uint16_t *const aligned_dst = AlignUp(dst, sizeof(*dst));
  size_t nchars;
  if (aligned_dst == dst) {
    nchars = str->Write(isolate, dst, 0, -1, flags);
    return nchars * sizeof(*dst);
  }

  nchars = str->Write(isolate, aligned_dst, 0, str->Length() - 1, flags);

  memmove(dst, aligned_dst, nchars * sizeof(*dst));

  uint16_t last;
  str->Write(isolate, &last, nchars, 1, flags);
  memcpy(buf + nchars * sizeof(*dst), &last, sizeof(last));
  nchars++;

  return nchars * sizeof(*dst);
}

static void serializeString(const v8::FunctionCallbackInfo<v8::Value> &args) {
  auto isolate = args.GetIsolate();
  auto context = isolate->GetCurrentContext();
  v8::Local<v8::Uint8Array> dst = args[0].As<v8::Uint8Array>();
  v8::Local<v8::String> str = args[1].As<v8::String>();
  uint32_t offset = args[2].As<v8::Number>()->Uint32Value(context).ToChecked();

  bool is_one_byte = str->IsOneByte();
  StringLayout layout;
  if (!getStringLayout(str->Length(), is_one_byte, &layout) ||
      !hasStringCapacity(dst->ByteLength(), offset, layout)) {
    Nan::ThrowRangeError(
        "serializeString: string does not fit into the destination buffer");
    return;
  }
  // The local ArrayBuffer handle keeps its data alive through the synchronous
  // string writes.
  const auto buffer = dst->Buffer();
  uint8_t *dst_data =
      reinterpret_cast<uint8_t *>(buffer->Data()) + dst->ByteOffset();

  if (is_one_byte && str->IsExternalOneByte()) {
    offset += writeVarUint32(dst_data, offset, layout.header);
    const auto src = str->GetExternalOneByteStringResource()->data();
    memcpy(dst_data + offset, src, str->Length());
    offset += str->Length();
  } else {
    v8::HandleScope scope(isolate);
    int flags = String::HINT_MANY_WRITES_EXPECTED |
                String::NO_NULL_TERMINATION | String::REPLACE_INVALID_UTF8;
    if (is_one_byte) {
      offset += writeVarUint32(dst_data, offset, layout.header);
      offset += str->WriteOneByte(isolate, dst_data + offset, 0, str->Length(),
                                  flags);
    } else {
      offset += writeVarUint32(dst_data, offset, layout.header);
      offset += writeUCS2(isolate, dst_data + offset, str, flags);
    }
  }

  args.GetReturnValue().Set(offset);
}

static uint32_t serializeStringFast(Local<Value> receiver,
                                    const v8::FastApiTypedArray<uint8_t> &dst,
                                    const v8::FastOneByteString &src,
                                    uint32_t offset, uint32_t /* max_length */,
                                    v8::FastApiCallbackOptions &options) {
  StringLayout layout;
  uint8_t *dst_data;
  if (!getStringLayout(src.length, true, &layout) ||
      !hasStringCapacity(dst.length(), offset, layout) ||
      !dst.getStorageIfAligned(&dst_data)) {
    options.fallback = true;
    return 0;
  }
  offset += writeVarUint32(dst_data, offset, layout.header);
  memcpy(dst_data + offset, src.data, src.length);
  return offset + layout.body_size;
}

v8::CFunction fast_serialize_string(v8::CFunction::Make(serializeStringFast));

v8::Local<v8::FunctionTemplate> FastFunction(v8::Isolate *isolate,
                                             v8::FunctionCallback callback,
                                             const v8::CFunction *c_function) {
  return v8::FunctionTemplate::New(
      isolate, callback, v8::Local<v8::Value>(), v8::Local<v8::Signature>(), 0,
      v8::ConstructorBehavior::kThrow, v8::SideEffectType::kHasSideEffect,
      c_function);
}

void Init(v8::Local<v8::Object> exports) {
  v8::Local<v8::Context> context = exports->GetCreationContextChecked();
  v8::Isolate *isolate = context->GetIsolate();
  exports
      ->Set(context, Nan::New("serializeString").ToLocalChecked(),
            FastFunction(isolate, serializeString, &fast_serialize_string)
                ->GetFunction(context)
                .ToLocalChecked())
      .Check();
}

NODE_MODULE(fory_util, Init)
