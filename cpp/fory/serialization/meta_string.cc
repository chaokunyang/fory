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

#include "fory/serialization/meta_string.h"

#include "fory/util/buffer.h"

#include <cctype>

namespace fory {
namespace serialization {

MetaStringDecoder::MetaStringDecoder(char special_char1, char special_char2)
    : special_char1_(special_char1), special_char2_(special_char2) {}

Result<std::string, Error>
MetaStringDecoder::decode(const uint8_t *data, size_t len,
                          MetaEncoding encoding) const {
  std::string decoded;
  if (len == 0) {
    decoded = "";
  } else {
    switch (encoding) {
    case MetaEncoding::LOWER_SPECIAL: {
      auto res = decode_lower_special(data, len);
      if (!res.ok()) {
        return Unexpected(res.error());
      }
      decoded = std::move(res.value());
      break;
    }
    case MetaEncoding::LOWER_UPPER_DIGIT_SPECIAL: {
      auto res = decode_lower_upper_digit_special(data, len);
      if (!res.ok()) {
        return Unexpected(res.error());
      }
      decoded = std::move(res.value());
      break;
    }
    case MetaEncoding::FIRST_TO_LOWER_SPECIAL: {
      auto res = decode_rep_first_lower_special(data, len);
      if (!res.ok()) {
        return Unexpected(res.error());
      }
      decoded = std::move(res.value());
      break;
    }
    case MetaEncoding::ALL_TO_LOWER_SPECIAL: {
      auto res = decode_rep_all_to_lower_special(data, len);
      if (!res.ok()) {
        return Unexpected(res.error());
      }
      decoded = std::move(res.value());
      break;
    }
    case MetaEncoding::UTF8:
    default:
      decoded.assign(reinterpret_cast<const char *>(data), len);
      break;
    }
  }
  return decoded;
}

Result<std::string, Error>
MetaStringDecoder::decode_lower_special(const uint8_t *data, size_t len) const {
  std::string decoded;
  if (len == 0) {
    return decoded;
  }
  const size_t total_bits = len * 8;
  const bool strip_last_char = (data[0] & 0x80) != 0;
  const size_t bit_mask = 0b11111;
  size_t bit_index = 1;

  while (bit_index + 5 <= total_bits &&
         !(strip_last_char && (bit_index + 2 * 5 > total_bits))) {
    const size_t byte_index = bit_index / 8;
    const size_t intra_byte_index = bit_index % 8;
    size_t char_value;
    if (intra_byte_index > 3) {
      uint16_t two_bytes = static_cast<uint16_t>(data[byte_index]) << 8;
      if (byte_index + 1 < len) {
        two_bytes |= data[byte_index + 1];
      }
      char_value = (static_cast<size_t>(two_bytes) >>
                    (11 - intra_byte_index)) & bit_mask;
    } else {
      char_value = (static_cast<size_t>(data[byte_index]) >>
                    (3 - intra_byte_index)) & bit_mask;
    }
    bit_index += 5;
    FORY_TRY(ch, decode_lower_special_char(static_cast<uint8_t>(char_value)));
    decoded.push_back(ch);
  }
  return decoded;
}

Result<std::string, Error> MetaStringDecoder::decode_lower_upper_digit_special(
    const uint8_t *data, size_t len) const {
  std::string decoded;
  if (len == 0) {
    return decoded;
  }
  const size_t total_bits = len * 8;
  const bool strip_last_char = (data[0] & 0x80) != 0;
  const size_t bit_mask = 0b111111;
  size_t bit_index = 1;

  while (bit_index + 6 <= total_bits &&
         !(strip_last_char && (bit_index + 2 * 6 > total_bits))) {
    const size_t byte_index = bit_index / 8;
    const size_t intra_byte_index = bit_index % 8;
    size_t char_value;
    if (intra_byte_index > 2) {
      uint16_t two_bytes = static_cast<uint16_t>(data[byte_index]) << 8;
      if (byte_index + 1 < len) {
        two_bytes |= data[byte_index + 1];
      }
      char_value = (static_cast<size_t>(two_bytes) >>
                    (10 - intra_byte_index)) & bit_mask;
    } else {
      char_value = (static_cast<size_t>(data[byte_index]) >>
                    (2 - intra_byte_index)) & bit_mask;
    }
    bit_index += 6;
    FORY_TRY(ch,
             decode_lower_upper_digit_special_char(static_cast<uint8_t>(char_value)));
    decoded.push_back(ch);
  }
  return decoded;
}

Result<std::string, Error> MetaStringDecoder::decode_rep_first_lower_special(
    const uint8_t *data, size_t len) const {
  FORY_TRY(base, decode_lower_special(data, len));
  if (base.empty()) {
    return base;
  }
  std::string result;
  result.reserve(base.size());
  auto it = base.begin();
  result.push_back(static_cast<char>(std::toupper(*it)));
  ++it;
  result.append(it, base.end());
  return result;
}

Result<std::string, Error> MetaStringDecoder::decode_rep_all_to_lower_special(
    const uint8_t *data, size_t len) const {
  FORY_TRY(base, decode_lower_special(data, len));
  std::string result;
  result.reserve(base.size());
  bool skip = false;
  for (size_t i = 0; i < base.size(); ++i) {
    char c = base[i];
    if (skip) {
      skip = false;
      continue;
    }
    if (c == '|') {
      if (i + 1 < base.size()) {
        result.push_back(static_cast<char>(std::toupper(base[i + 1])));
      }
      skip = true;
    } else {
      result.push_back(c);
    }
  }
  return result;
}

Result<char, Error>
MetaStringDecoder::decode_lower_special_char(uint8_t value) const {
  switch (value) {
  case 0 ... 25:
    return static_cast<char>('a' + value);
  case 26:
    return '.';
  case 27:
    return '_';
  case 28:
    return '$';
  case 29:
    return '|';
  default:
    return Unexpected(Error::encode_error(
        "Invalid character value for LOWER_SPECIAL decoding: " +
        std::to_string(static_cast<int>(value))));
  }
}

Result<char, Error>
MetaStringDecoder::decode_lower_upper_digit_special_char(uint8_t value) const {
  switch (value) {
  case 0 ... 25:
    return static_cast<char>('a' + value);
  case 26 ... 51:
    return static_cast<char>('A' + (value - 26));
  case 52 ... 61:
    return static_cast<char>('0' + (value - 52));
  case 62:
    return special_char1_;
  case 63:
    return special_char2_;
  default:
    return Unexpected(Error::encode_error(
        "Invalid character value for LOWER_UPPER_DIGIT_SPECIAL decoding: " +
        std::to_string(static_cast<int>(value))));
  }
}

MetaStringTable::MetaStringTable() = default;

Result<std::string, Error>
MetaStringTable::read_string(Buffer &buffer,
                             const MetaStringDecoder &decoder) {
  // Header is encoded with VarUint32Small7 on Java side, but wire
  // format is still standard varuint32.
  FORY_TRY(header, buffer.ReadVarUint32());
  uint32_t len_or_id = header >> 1;
  bool is_ref = (header & 0x1u) != 0;

  if (is_ref) {
    if (len_or_id == 0 || len_or_id > entries_.size()) {
      return Unexpected(Error::invalid_data(
          "Invalid meta string reference id: " + std::to_string(len_or_id)));
    }
    return entries_[len_or_id - 1].decoded;
  }

  constexpr uint32_t kSmallThreshold = 16;
  uint32_t len = len_or_id;

  std::vector<uint8_t> bytes;
  MetaEncoding encoding = MetaEncoding::UTF8;

  if (len > kSmallThreshold) {
    // Big string layout in Java MetaStringResolver:
    //   header (len<<1 | flags) + hashCode(int64) + data[len]
    // The original encoding is not transmitted explicitly. For cross-language
    // purposes we treat the payload bytes as UTF8 and let callers handle any
    // higher-level semantics.
    FORY_TRY(hash_code, buffer.ReadInt64());
    (void)hash_code; // hash_code is only used for Java-side caching.
    bytes.resize(len);
    if (len > 0) {
      FORY_RETURN_NOT_OK(buffer.ReadBytes(bytes.data(), len));
    }
    encoding = MetaEncoding::UTF8;
  } else {
    // Small string layout: encoding(byte) + data[len]
    FORY_TRY(enc_byte_res, buffer.ReadInt8());
    uint8_t enc_byte = static_cast<uint8_t>(enc_byte_res);
    if (len == 0) {
      if (enc_byte != 0) {
        return Unexpected(Error::encoding_error(
            "Empty meta string must use UTF8 encoding"));
      }
      encoding = MetaEncoding::UTF8;
    } else {
      FORY_TRY(enc, ToMetaEncoding(enc_byte));
      encoding = enc;
      bytes.resize(len);
      FORY_RETURN_NOT_OK(buffer.ReadBytes(bytes.data(), len));
    }
  }

  std::string decoded;
  if (len == 0) {
    decoded = "";
  } else {
    FORY_TRY(tmp, decoder.decode(bytes.data(), bytes.size(), encoding));
    decoded = std::move(tmp);
  }

  entries_.push_back(Entry{decoded});
  return decoded;
}

void MetaStringTable::reset() { entries_.clear(); }

Result<MetaEncoding, Error> ToMetaEncoding(uint8_t value) {
  switch (value) {
  case 0x00:
    return MetaEncoding::UTF8;
  case 0x01:
    return MetaEncoding::LOWER_SPECIAL;
  case 0x02:
    return MetaEncoding::LOWER_UPPER_DIGIT_SPECIAL;
  case 0x03:
    return MetaEncoding::FIRST_TO_LOWER_SPECIAL;
  case 0x04:
    return MetaEncoding::ALL_TO_LOWER_SPECIAL;
  default:
    return Unexpected(Error::encoding_error(
        "Unsupported meta string encoding value: " +
        std::to_string(static_cast<int>(value))));
  }
}

} // namespace serialization
} // namespace fory
