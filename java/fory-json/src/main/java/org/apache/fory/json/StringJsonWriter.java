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

package org.apache.fory.json;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.apache.fory.memory.LittleEndian;
import org.apache.fory.serializer.StringSerializer;
import org.apache.fory.util.StringLayout;

final class StringJsonWriter extends JsonWriter {
  private static final byte[] MIN_INT_BYTES = "-2147483648".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] MIN_LONG_BYTES =
      "-9223372036854775808".getBytes(StandardCharsets.ISO_8859_1);
  private static final long HIGH_BITS = 0x8080808080808080L;
  private static final int INT_HIGH_BITS = 0x80808080;
  private static final long ASCII_CONTROL_OFFSET = 0x6060606060606060L;
  private static final int INT_ASCII_CONTROL_OFFSET = 0x60606060;
  private static final long ONE_BYTES = 0x0101010101010101L;
  private static final int INT_ONE_BYTES = 0x01010101;
  private static final long QUOTE_BYTES_COMPLEMENT = ~0x2222222222222222L;
  private static final int INT_QUOTE_BYTES_COMPLEMENT = ~0x22222222;
  private static final long BACKSLASH_BYTES_COMPLEMENT = ~0x5C5C5C5C5C5C5C5CL;
  private static final int INT_BACKSLASH_BYTES_COMPLEMENT = ~0x5C5C5C5C;
  private static final long UTF16_LATIN1_MASK = 0xFF00FF00FF00FF00L;
  private static final byte[] DIGIT_HUNDREDS = new byte[1000];
  private static final byte[] DIGIT_TENS = new byte[1000];
  private static final byte[] DIGIT_ONES = new byte[1000];
  private static final int[] DIGIT_QUADS = new int[10000];
  private static final boolean STRING_BYTES_BACKED = StringLayout.isBytesBacked();

  static {
    for (int i = 0; i < 1000; i++) {
      DIGIT_HUNDREDS[i] = (byte) ('0' + i / 100);
      DIGIT_TENS[i] = (byte) ('0' + (i / 10) % 10);
      DIGIT_ONES[i] = (byte) ('0' + i % 10);
    }
    for (int i = 0; i < 10000; i++) {
      int high = i / 100;
      int low = i - high * 100;
      int c0 = '0' + high / 10;
      int c1 = '0' + high % 10;
      int c2 = '0' + low / 10;
      int c3 = '0' + low % 10;
      DIGIT_QUADS[i] = c0 | (c1 << 8) | (c2 << 16) | (c3 << 24);
    }
  }

  private byte[] buffer;
  private int position;

  public StringJsonWriter(boolean writeNullFields) {
    this(writeNullFields, new byte[512]);
  }

  public StringJsonWriter(boolean writeNullFields, byte[] buffer) {
    super(writeNullFields);
    this.buffer = buffer;
  }

  public void reset(byte[] buffer) {
    this.buffer = buffer;
    position = 0;
  }

  public String toJson() {
    byte[] bytes = Arrays.copyOf(buffer, position);
    return StringSerializer.newBytesStringZeroCopy(StringLayout.LATIN1, bytes);
  }

  public byte[] buffer() {
    return buffer;
  }

  @Override
  public void writeNull() {
    writeAscii("null");
  }

  @Override
  public void writeBoolean(boolean value) {
    writeAscii(value ? "true" : "false");
  }

  @Override
  public void writeInt(int value) {
    if (value == Integer.MIN_VALUE) {
      writeRaw(MIN_INT_BYTES);
      return;
    }
    if (value < 0) {
      writeByteRaw((byte) '-');
      value = -value;
    }
    writePositiveInt(value);
  }

  @Override
  public void writeLong(long value) {
    if (value == Long.MIN_VALUE) {
      writeRaw(MIN_LONG_BYTES);
      return;
    }
    if (value < 0) {
      writeByteRaw((byte) '-');
      value = -value;
    }
    if (value <= Integer.MAX_VALUE) {
      writePositiveInt((int) value);
      return;
    }
    int start = position;
    do {
      ensure(1);
      buffer[position++] = (byte) ('0' + value % 10);
      value /= 10;
    } while (value != 0);
    reverse(start, position - 1);
  }

  @Override
  public void writeFloat(float value) {
    if (!Float.isFinite(value)) {
      throw new ForyJsonException("JSON does not support non-finite float " + value);
    }
    writeAscii(Float.toString(value));
  }

  @Override
  public void writeDouble(double value) {
    if (!Double.isFinite(value)) {
      throw new ForyJsonException("JSON does not support non-finite double " + value);
    }
    writeAscii(Double.toString(value));
  }

  @Override
  public void writeChar(char value) {
    if (Character.isSurrogate(value)) {
      throw new ForyJsonException("JSON char cannot be a surrogate: " + Integer.toHexString(value));
    }
    writeByteRaw((byte) '"');
    writeEscapedChar(value);
    writeByteRaw((byte) '"');
  }

  @Override
  public void writeString(String value) {
    if (STRING_BYTES_BACKED && writeBytesBackedString(value)) {
      return;
    }
    ensure(value.length() * 6 + 2);
    writeStringNoEnsure(value);
  }

  @Override
  public void writeFieldName(String name) {
    writeString(name);
    writeByteRaw((byte) ':');
  }

  @Override
  public void writeFieldName(JsonPropertyInfo property) {
    writeRaw(property.stringNamePrefix());
  }

  public void writeFieldName(JsonPropertyInfo property, int index) {
    writeRaw(index == 0 ? property.stringNamePrefix() : property.stringCommaNamePrefix());
  }

  public void writeBooleanField(
      byte[] namePrefix, byte[] commaNamePrefix, int index, boolean value) {
    byte[] prefix = index == 0 ? namePrefix : commaNamePrefix;
    ensure(prefix.length + 5);
    writeRawNoEnsure(prefix);
    writeAsciiNoEnsure(value ? "true" : "false");
  }

  public void writeIntField(byte[] namePrefix, byte[] commaNamePrefix, int index, int value) {
    byte[] prefix = index == 0 ? namePrefix : commaNamePrefix;
    ensure(prefix.length + 11);
    writeRawNoEnsure(prefix);
    writeIntNoEnsure(value);
  }

  public void writeObjectIntField(byte[] namePrefix, int value) {
    ensure(namePrefix.length + 12);
    buffer[position++] = (byte) '{';
    writeRawNoEnsure(namePrefix);
    writeIntNoEnsure(value);
  }

  public void writeLongField(byte[] namePrefix, byte[] commaNamePrefix, int index, long value) {
    byte[] prefix = index == 0 ? namePrefix : commaNamePrefix;
    ensure(prefix.length + 20);
    writeRawNoEnsure(prefix);
    writeLongNoEnsure(value);
  }

  public void writeObjectLongField(byte[] namePrefix, long value) {
    ensure(namePrefix.length + 21);
    buffer[position++] = (byte) '{';
    writeRawNoEnsure(namePrefix);
    writeLongNoEnsure(value);
  }

  public void writeStringField(byte[] namePrefix, byte[] commaNamePrefix, int index, String value) {
    byte[] prefix = index == 0 ? namePrefix : commaNamePrefix;
    if (STRING_BYTES_BACKED && writeBytesBackedStringField(prefix, value)) {
      return;
    }
    ensure(prefix.length + value.length() * 6 + 2);
    writeRawNoEnsure(prefix);
    writeStringNoEnsure(value);
  }

  public void writeRawValue(byte[] value) {
    writeRaw(value);
  }

  @Override
  public void writeObjectStart() {
    writeByteRaw((byte) '{');
  }

  @Override
  public void writeObjectEnd() {
    writeByteRaw((byte) '}');
  }

  @Override
  public void writeArrayStart() {
    writeByteRaw((byte) '[');
  }

  @Override
  public void writeArrayEnd() {
    writeByteRaw((byte) ']');
  }

  @Override
  public void writeComma(int index) {
    if (index != 0) {
      writeByteRaw((byte) ',');
    }
  }

  private void writeStringNoEnsure(String value) {
    if (STRING_BYTES_BACKED && writeBytesBackedStringNoEnsure(value)) {
      return;
    }
    int length = value.length();
    byte[] bytes = buffer;
    int pos = position;
    bytes[pos++] = (byte) '"';
    int i = 0;
    while (i + 4 <= length) {
      char c0 = value.charAt(i);
      char c1 = value.charAt(i + 1);
      char c2 = value.charAt(i + 2);
      char c3 = value.charAt(i + 3);
      if (isJsonLatin1(c0) && isJsonLatin1(c1) && isJsonLatin1(c2) && isJsonLatin1(c3)) {
        bytes[pos] = (byte) c0;
        bytes[pos + 1] = (byte) c1;
        bytes[pos + 2] = (byte) c2;
        bytes[pos + 3] = (byte) c3;
        pos += 4;
        i += 4;
      } else {
        break;
      }
    }
    while (i < length) {
      char ch = value.charAt(i);
      if (isJsonLatin1(ch)) {
        bytes[pos++] = (byte) ch;
        i++;
      } else {
        position = pos;
        writeStringSlow(value, i, length);
        return;
      }
    }
    bytes[pos++] = (byte) '"';
    position = pos;
  }

  private boolean writeBytesBackedStringNoEnsure(String value) {
    byte[] bytes = StringLayout.bytes(value);
    byte coder = StringLayout.coder(value);
    if (coder == StringLayout.LATIN1) {
      writeLatin1StringNoEnsure(bytes);
      return true;
    } else if (coder == StringLayout.UTF16) {
      writeUtf16StringNoEnsure(bytes);
      return true;
    }
    return false;
  }

  private boolean writeBytesBackedString(String value) {
    byte[] bytes = StringLayout.bytes(value);
    byte coder = StringLayout.coder(value);
    if (coder == StringLayout.LATIN1) {
      ensure(bytes.length + 2);
      writeLatin1StringNoEnsure(bytes);
      return true;
    } else if (coder == StringLayout.UTF16) {
      ensure((bytes.length >> 1) * 6 + 2);
      writeUtf16StringNoEnsure(bytes);
      return true;
    }
    return false;
  }

  private boolean writeBytesBackedStringField(byte[] prefix, String value) {
    byte[] bytes = StringLayout.bytes(value);
    byte coder = StringLayout.coder(value);
    if (coder == StringLayout.LATIN1) {
      ensure(prefix.length + bytes.length + 2);
      writeRawNoEnsure(prefix);
      writeLatin1StringNoEnsure(bytes);
      return true;
    } else if (coder == StringLayout.UTF16) {
      ensure(prefix.length + (bytes.length >> 1) * 6 + 2);
      writeRawNoEnsure(prefix);
      writeUtf16StringNoEnsure(bytes);
      return true;
    }
    return false;
  }

  private void writeLatin1StringNoEnsure(byte[] value) {
    int length = value.length;
    byte[] bytes = buffer;
    int pos = position;
    bytes[pos++] = (byte) '"';
    int i = 0;
    int upperBound = length & ~7;
    for (; i < upperBound; i += 8) {
      long word = LittleEndian.getInt64(value, i);
      if (!isJsonAsciiWord(word)) {
        break;
      }
      LittleEndian.putInt64(bytes, pos, word);
      pos += 8;
    }
    while (i < length) {
      byte ch = value[i];
      if (isJsonLatin1Byte(ch)) {
        bytes[pos++] = ch;
        i++;
      } else {
        position = pos;
        writeLatin1StringSlow(value, i, length);
        return;
      }
    }
    bytes[pos++] = (byte) '"';
    position = pos;
  }

  private void writeUtf16StringNoEnsure(byte[] value) {
    int length = value.length;
    byte[] bytes = buffer;
    int pos = position;
    bytes[pos++] = (byte) '"';
    int i = 0;
    int upperBound = length & ~7;
    for (; i < upperBound; i += 8) {
      long word = LittleEndian.getInt64(value, i);
      if ((word & UTF16_LATIN1_MASK) != 0) {
        break;
      }
      int packed = packUtf16Latin1(word);
      if (!isJsonAsciiInt(packed)) {
        break;
      }
      LittleEndian.putInt32(bytes, pos, packed);
      pos += 4;
    }
    for (; i < length; i += 2) {
      char ch = StringLayout.utf16Char(value, i);
      if (isJsonLatin1(ch)) {
        bytes[pos++] = (byte) ch;
      } else {
        position = pos;
        writeUtf16StringSlow(value, i >> 1, length >> 1);
        return;
      }
    }
    bytes[pos++] = (byte) '"';
    position = pos;
  }

  private void writeStringSlow(String value, int index, int length) {
    for (int i = index; i < length; i++) {
      char ch = value.charAt(i);
      if (Character.isHighSurrogate(ch)) {
        if (i + 1 >= length) {
          throw new ForyJsonException("Unpaired high surrogate in string");
        }
        char low = value.charAt(++i);
        if (!Character.isLowSurrogate(low)) {
          throw new ForyJsonException("Unpaired high surrogate in string");
        }
        writeUnicodeEscape(ch);
        writeUnicodeEscape(low);
      } else if (Character.isLowSurrogate(ch)) {
        throw new ForyJsonException("Unpaired low surrogate in string");
      } else {
        writeEscapedChar(ch);
      }
    }
    writeByteRaw((byte) '"');
  }

  private void writeLatin1StringSlow(byte[] value, int index, int length) {
    for (int i = index; i < length; i++) {
      writeEscapedChar((char) (value[i] & 0xff));
    }
    writeByteRaw((byte) '"');
  }

  private void writeUtf16StringSlow(byte[] value, int index, int length) {
    for (int i = index; i < length; i++) {
      char ch = StringLayout.utf16Char(value, i << 1);
      if (Character.isHighSurrogate(ch)) {
        if (i + 1 >= length) {
          throw new ForyJsonException("Unpaired high surrogate in string");
        }
        char low = StringLayout.utf16Char(value, (++i) << 1);
        if (!Character.isLowSurrogate(low)) {
          throw new ForyJsonException("Unpaired high surrogate in string");
        }
        writeUnicodeEscape(ch);
        writeUnicodeEscape(low);
      } else if (Character.isLowSurrogate(ch)) {
        throw new ForyJsonException("Unpaired low surrogate in string");
      } else {
        writeEscapedChar(ch);
      }
    }
    writeByteRaw((byte) '"');
  }

  private void writeEscapedChar(char ch) {
    switch (ch) {
      case '"':
        writeAscii("\\\"");
        return;
      case '\\':
        writeAscii("\\\\");
        return;
      case '\b':
        writeAscii("\\b");
        return;
      case '\f':
        writeAscii("\\f");
        return;
      case '\n':
        writeAscii("\\n");
        return;
      case '\r':
        writeAscii("\\r");
        return;
      case '\t':
        writeAscii("\\t");
        return;
      default:
        if (ch < 0x20 || ch > 0xff) {
          writeUnicodeEscape(ch);
        } else {
          writeByteRaw((byte) ch);
        }
    }
  }

  private void writeUnicodeEscape(char ch) {
    ensure(6);
    buffer[position++] = '\\';
    buffer[position++] = 'u';
    buffer[position++] = (byte) hex((ch >>> 12) & 0xF);
    buffer[position++] = (byte) hex((ch >>> 8) & 0xF);
    buffer[position++] = (byte) hex((ch >>> 4) & 0xF);
    buffer[position++] = (byte) hex(ch & 0xF);
  }

  private void writeAscii(String value) {
    int length = value.length();
    ensure(length);
    writeAsciiNoEnsure(value);
  }

  private void writeAsciiNoEnsure(String value) {
    int length = value.length();
    for (int i = 0; i < length; i++) {
      buffer[position++] = (byte) value.charAt(i);
    }
  }

  private void writeRaw(byte[] bytes) {
    ensure(bytes.length);
    writeRawNoEnsure(bytes);
  }

  private void writeRawNoEnsure(byte[] bytes) {
    System.arraycopy(bytes, 0, buffer, position, bytes.length);
    position += bytes.length;
  }

  private void writeByteRaw(byte value) {
    ensure(1);
    buffer[position++] = value;
  }

  private void reverse(int start, int end) {
    while (start < end) {
      byte tmp = buffer[start];
      buffer[start++] = buffer[end];
      buffer[end--] = tmp;
    }
  }

  private void ensure(int additional) {
    int minCapacity = position + additional;
    if (minCapacity > buffer.length) {
      int newCapacity = buffer.length << 1;
      while (newCapacity < minCapacity) {
        newCapacity <<= 1;
      }
      buffer = Arrays.copyOf(buffer, newCapacity);
    }
  }

  private static boolean isJsonLatin1(char ch) {
    return ch > 0x1F && ch <= 0xff && ch != '"' && ch != '\\';
  }

  private static boolean isJsonLatin1Byte(byte value) {
    int ch = value & 0xff;
    return ch > 0x1F && ch != '"' && ch != '\\';
  }

  private static boolean isJsonAsciiWord(long word) {
    return (((word + ASCII_CONTROL_OFFSET) & ~word) & HIGH_BITS) == HIGH_BITS
        && (((word ^ QUOTE_BYTES_COMPLEMENT) + ONE_BYTES) & HIGH_BITS) == HIGH_BITS
        && (((word ^ BACKSLASH_BYTES_COMPLEMENT) + ONE_BYTES) & HIGH_BITS) == HIGH_BITS;
  }

  private static boolean isJsonAsciiInt(int word) {
    return (((word + INT_ASCII_CONTROL_OFFSET) & ~word) & INT_HIGH_BITS) == INT_HIGH_BITS
        && (((word ^ INT_QUOTE_BYTES_COMPLEMENT) + INT_ONE_BYTES) & INT_HIGH_BITS) == INT_HIGH_BITS
        && (((word ^ INT_BACKSLASH_BYTES_COMPLEMENT) + INT_ONE_BYTES) & INT_HIGH_BITS)
            == INT_HIGH_BITS;
  }

  private static int packUtf16Latin1(long word) {
    return (int)
        ((word & 0xFFL)
            | ((word >>> 8) & 0xFF00L)
            | ((word >>> 16) & 0xFF0000L)
            | ((word >>> 24) & 0xFF000000L));
  }

  private void writePositiveInt(int value) {
    ensure(10);
    writePositiveIntNoEnsure(value);
  }

  private void writeIntNoEnsure(int value) {
    if (value == Integer.MIN_VALUE) {
      writeRawNoEnsure(MIN_INT_BYTES);
      return;
    }
    if (value < 0) {
      buffer[position++] = (byte) '-';
      value = -value;
    }
    writePositiveIntNoEnsure(value);
  }

  private void writeLongNoEnsure(long value) {
    if (value == Long.MIN_VALUE) {
      writeRawNoEnsure(MIN_LONG_BYTES);
      return;
    }
    if (value < 0) {
      buffer[position++] = (byte) '-';
      value = -value;
    }
    if (value <= Integer.MAX_VALUE) {
      writePositiveIntNoEnsure((int) value);
      return;
    }
    int start = position;
    do {
      buffer[position++] = (byte) ('0' + value % 10);
      value /= 10;
    } while (value != 0);
    reverse(start, position - 1);
  }

  private void writePositiveIntNoEnsure(int value) {
    byte[] bytes = buffer;
    int pos = position;
    if (value < 10000) {
      position = writeIntUpTo4(bytes, pos, value);
      return;
    }
    int high = divide10000(value);
    int low = value - high * 10000;
    if (high < 10000) {
      pos = writeIntUpTo4(bytes, pos, high);
      position = writePadded4(bytes, pos, low);
      return;
    }
    int top = divide10000(high);
    int middle = high - top * 10000;
    pos = writeIntUpTo4(bytes, pos, top);
    pos = writePadded4(bytes, pos, middle);
    position = writePadded4(bytes, pos, low);
  }

  private static int divide10000(int value) {
    return (int) (((long) value * 1759218605L) >> 44);
  }

  private static int writeIntUpTo4(byte[] bytes, int pos, int value) {
    if (value < 10) {
      bytes[pos++] = DIGIT_ONES[value];
    } else if (value < 100) {
      bytes[pos++] = DIGIT_TENS[value];
      bytes[pos++] = DIGIT_ONES[value];
    } else if (value < 1000) {
      pos = writePadded3(bytes, pos, value);
    } else {
      pos = writePadded4(bytes, pos, value);
    }
    return pos;
  }

  private static int writePadded3(byte[] bytes, int pos, int value) {
    bytes[pos++] = DIGIT_HUNDREDS[value];
    bytes[pos++] = DIGIT_TENS[value];
    bytes[pos++] = DIGIT_ONES[value];
    return pos;
  }

  private static int writePadded4(byte[] bytes, int pos, int value) {
    LittleEndian.putInt32(bytes, pos, DIGIT_QUADS[value]);
    return pos + 4;
  }

  private static char hex(int value) {
    return (char) (value < 10 ? '0' + value : 'a' + value - 10);
  }
}
