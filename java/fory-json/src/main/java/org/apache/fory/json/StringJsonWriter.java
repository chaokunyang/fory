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

import java.util.Arrays;

final class StringJsonWriter extends JsonWriter {
  private static final char[] MIN_INT_CHARS = "-2147483648".toCharArray();
  private static final char[] MIN_LONG_CHARS = "-9223372036854775808".toCharArray();
  private static final char[] DIGIT_HUNDREDS = new char[1000];
  private static final char[] DIGIT_TENS = new char[1000];
  private static final char[] DIGIT_ONES = new char[1000];

  static {
    for (int i = 0; i < 1000; i++) {
      DIGIT_HUNDREDS[i] = (char) ('0' + i / 100);
      DIGIT_TENS[i] = (char) ('0' + (i / 10) % 10);
      DIGIT_ONES[i] = (char) ('0' + i % 10);
    }
  }

  private char[] buffer;
  private int position;

  public StringJsonWriter(boolean writeNullFields) {
    this(writeNullFields, new char[512]);
  }

  public StringJsonWriter(boolean writeNullFields, char[] buffer) {
    super(writeNullFields);
    this.buffer = buffer;
  }

  public void reset(char[] buffer) {
    this.buffer = buffer;
    position = 0;
  }

  public String toJson() {
    return new String(buffer, 0, position);
  }

  public char[] buffer() {
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
      writeAscii("-2147483648");
      return;
    }
    if (value < 0) {
      writeCharRaw('-');
      value = -value;
    }
    writePositiveInt(value);
  }

  @Override
  public void writeLong(long value) {
    if (value == Long.MIN_VALUE) {
      writeAscii("-9223372036854775808");
      return;
    }
    if (value < 0) {
      writeCharRaw('-');
      value = -value;
    }
    if (value <= Integer.MAX_VALUE) {
      writePositiveInt((int) value);
      return;
    }
    int start = position;
    do {
      ensure(1);
      buffer[position++] = (char) ('0' + value % 10);
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
    writeCharRaw('"');
    writeEscapedChar(value);
    writeCharRaw('"');
  }

  @Override
  public void writeString(String value) {
    int length = value.length();
    ensure(length + 2);
    writeStringNoEnsure(value);
  }

  @Override
  public void writeFieldName(String name) {
    writeString(name);
    writeCharRaw(':');
  }

  @Override
  public void writeFieldName(JsonPropertyInfo property) {
    writeRaw(property.stringNamePrefix());
  }

  public void writeFieldName(JsonPropertyInfo property, int index) {
    writeRaw(index == 0 ? property.stringNamePrefix() : property.stringCommaNamePrefix());
  }

  public void writeBooleanField(
      char[] namePrefix, char[] commaNamePrefix, int index, boolean value) {
    char[] prefix = index == 0 ? namePrefix : commaNamePrefix;
    ensure(prefix.length + 5);
    writeRawNoEnsure(prefix);
    writeAsciiNoEnsure(value ? "true" : "false");
  }

  public void writeIntField(char[] namePrefix, char[] commaNamePrefix, int index, int value) {
    char[] prefix = index == 0 ? namePrefix : commaNamePrefix;
    ensure(prefix.length + 11);
    writeRawNoEnsure(prefix);
    writeIntNoEnsure(value);
  }

  public void writeLongField(char[] namePrefix, char[] commaNamePrefix, int index, long value) {
    char[] prefix = index == 0 ? namePrefix : commaNamePrefix;
    ensure(prefix.length + 20);
    writeRawNoEnsure(prefix);
    writeLongNoEnsure(value);
  }

  public void writeStringField(char[] namePrefix, char[] commaNamePrefix, int index, String value) {
    char[] prefix = index == 0 ? namePrefix : commaNamePrefix;
    ensure(prefix.length + value.length() + 2);
    writeRawNoEnsure(prefix);
    writeStringNoEnsure(value);
  }

  public void writeRawValue(char[] value) {
    writeRaw(value);
  }

  @Override
  public void writeObjectStart() {
    writeCharRaw('{');
  }

  @Override
  public void writeObjectEnd() {
    writeCharRaw('}');
  }

  @Override
  public void writeArrayStart() {
    writeCharRaw('[');
  }

  @Override
  public void writeArrayEnd() {
    writeCharRaw(']');
  }

  @Override
  public void writeComma(int index) {
    if (index != 0) {
      writeCharRaw(',');
    }
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
        if (ch < 0x20) {
          writeUnicodeEscape(ch);
        } else {
          writeCharRaw(ch);
        }
    }
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
        writeCharRaw(ch);
        writeCharRaw(low);
      } else if (Character.isLowSurrogate(ch)) {
        throw new ForyJsonException("Unpaired low surrogate in string");
      } else {
        writeEscapedChar(ch);
      }
    }
    writeCharRaw('"');
  }

  private void writeStringNoEnsure(String value) {
    int length = value.length();
    char[] chars = buffer;
    int pos = position;
    chars[pos++] = '"';
    int i = 0;
    while (i + 8 <= length) {
      char c0 = value.charAt(i);
      char c1 = value.charAt(i + 1);
      char c2 = value.charAt(i + 2);
      char c3 = value.charAt(i + 3);
      char c4 = value.charAt(i + 4);
      char c5 = value.charAt(i + 5);
      char c6 = value.charAt(i + 6);
      char c7 = value.charAt(i + 7);
      if (isJsonChar(c0)
          && isJsonChar(c1)
          && isJsonChar(c2)
          && isJsonChar(c3)
          && isJsonChar(c4)
          && isJsonChar(c5)
          && isJsonChar(c6)
          && isJsonChar(c7)) {
        chars[pos] = c0;
        chars[pos + 1] = c1;
        chars[pos + 2] = c2;
        chars[pos + 3] = c3;
        chars[pos + 4] = c4;
        chars[pos + 5] = c5;
        chars[pos + 6] = c6;
        chars[pos + 7] = c7;
        pos += 8;
        i += 8;
      } else {
        break;
      }
    }
    while (i < length) {
      char ch = value.charAt(i);
      if (isJsonChar(ch)) {
        chars[pos++] = ch;
        i++;
      } else {
        position = pos;
        writeStringSlow(value, i, length);
        return;
      }
    }
    chars[pos++] = '"';
    position = pos;
  }

  private void writeUnicodeEscape(char ch) {
    ensure(6);
    buffer[position++] = '\\';
    buffer[position++] = 'u';
    buffer[position++] = '0';
    buffer[position++] = '0';
    buffer[position++] = hex((ch >>> 4) & 0xF);
    buffer[position++] = hex(ch & 0xF);
  }

  private void writeAscii(String value) {
    int length = value.length();
    ensure(length);
    writeAsciiNoEnsure(value);
  }

  private void writeAsciiNoEnsure(String value) {
    int length = value.length();
    value.getChars(0, length, buffer, position);
    position += length;
  }

  private void writeRaw(char[] chars) {
    ensure(chars.length);
    writeRawNoEnsure(chars);
  }

  private void writeRawNoEnsure(char[] chars) {
    System.arraycopy(chars, 0, buffer, position, chars.length);
    position += chars.length;
  }

  private void writeCharRaw(char ch) {
    ensure(1);
    buffer[position++] = ch;
  }

  private void reverse(int start, int end) {
    while (start < end) {
      char tmp = buffer[start];
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

  private static char hex(int value) {
    return (char) (value < 10 ? '0' + value : 'a' + value - 10);
  }

  private static boolean isJsonChar(char ch) {
    return ch > 0x1F && ch != '"' && ch != '\\' && (ch & 0xF800) != 0xD800;
  }

  private void writePositiveInt(int value) {
    ensure(10);
    writePositiveIntNoEnsure(value);
  }

  private void writeIntNoEnsure(int value) {
    if (value == Integer.MIN_VALUE) {
      writeRawNoEnsure(MIN_INT_CHARS);
      return;
    }
    if (value < 0) {
      buffer[position++] = '-';
      value = -value;
    }
    writePositiveIntNoEnsure(value);
  }

  private void writeLongNoEnsure(long value) {
    if (value == Long.MIN_VALUE) {
      writeRawNoEnsure(MIN_LONG_CHARS);
      return;
    }
    if (value < 0) {
      buffer[position++] = '-';
      value = -value;
    }
    if (value <= Integer.MAX_VALUE) {
      writePositiveIntNoEnsure((int) value);
      return;
    }
    int start = position;
    do {
      buffer[position++] = (char) ('0' + value % 10);
      value /= 10;
    } while (value != 0);
    reverse(start, position - 1);
  }

  private void writePositiveIntNoEnsure(int value) {
    char[] chars = buffer;
    int pos = position;
    if (value < 1000) {
      position = writeIntUpTo3(chars, pos, value);
      return;
    }
    int high = divide1000(value);
    int low = value - high * 1000;
    if (high < 1000) {
      pos = writeIntUpTo3(chars, pos, high);
      position = writePadded3(chars, pos, low);
      return;
    }
    int top = divide1000(high);
    int middle = high - top * 1000;
    if (top < 1000) {
      pos = writeIntUpTo3(chars, pos, top);
      pos = writePadded3(chars, pos, middle);
      position = writePadded3(chars, pos, low);
      return;
    }
    int first = divide1000(top);
    int middleHigh = top - first * 1000;
    chars[pos++] = (char) ('0' + first);
    pos = writePadded3(chars, pos, middleHigh);
    pos = writePadded3(chars, pos, middle);
    position = writePadded3(chars, pos, low);
  }

  private static int divide1000(int value) {
    return (int) (((long) value * 274877907L) >> 38);
  }

  private static int writeIntUpTo3(char[] chars, int pos, int value) {
    if (value < 10) {
      chars[pos++] = DIGIT_ONES[value];
    } else if (value < 100) {
      chars[pos++] = DIGIT_TENS[value];
      chars[pos++] = DIGIT_ONES[value];
    } else {
      pos = writePadded3(chars, pos, value);
    }
    return pos;
  }

  private static int writePadded3(char[] chars, int pos, int value) {
    chars[pos++] = DIGIT_HUNDREDS[value];
    chars[pos++] = DIGIT_TENS[value];
    chars[pos++] = DIGIT_ONES[value];
    return pos;
  }
}
