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

package org.apache.fory.json.reader;

import java.nio.charset.StandardCharsets;

public final class Utf8JsonReader extends JsonReader {
  private final byte[] input;

  public Utf8JsonReader(byte[] input) {
    this.input = input;
  }

  @Override
  protected int length() {
    return input.length;
  }

  @Override
  protected char charAt(int index) {
    return (char) (input[index] & 0xFF);
  }

  @Override
  public String readString() {
    skipWhitespace();
    if (position >= input.length || input[position++] != '"') {
      throw error("Expected string");
    }
    int start = position;
    while (position < input.length) {
      int b = input[position++] & 0xFF;
      if (b == '"') {
        return new String(input, start, position - 1 - start, StandardCharsets.ISO_8859_1);
      } else if (b == '\\') {
        StringBuilder builder = new StringBuilder(input.length - start);
        appendAscii(builder, start, position - 1);
        appendEscape(builder);
        return readStringTail(builder);
      } else if (b < 0x20) {
        throw error("Control character in string");
      } else if (b < 0x80) {
        continue;
      } else {
        StringBuilder builder = new StringBuilder(input.length - start);
        appendAscii(builder, start, position - 1);
        appendUtf8(builder, b);
        return readStringTail(builder);
      }
    }
    throw error("Unterminated string");
  }

  @Override
  protected String slice(int start, int end) {
    char[] chars = new char[end - start];
    for (int i = start; i < end; i++) {
      chars[i - start] = (char) (input[i] & 0xFF);
    }
    return new String(chars);
  }

  private String readStringTail(StringBuilder builder) {
    while (position < input.length) {
      int b = input[position++] & 0xFF;
      if (b == '"') {
        return builder.toString();
      } else if (b == '\\') {
        appendEscape(builder);
      } else if (b < 0x20) {
        throw error("Control character in string");
      } else if (b < 0x80) {
        builder.append((char) b);
      } else {
        appendUtf8(builder, b);
      }
    }
    throw error("Unterminated string");
  }

  private void appendAscii(StringBuilder builder, int start, int end) {
    for (int i = start; i < end; i++) {
      builder.append((char) (input[i] & 0xFF));
    }
  }

  private void appendUtf8(StringBuilder builder, int first) {
    if ((first & 0xE0) == 0xC0) {
      int second = continuation();
      int codePoint = ((first & 0x1F) << 6) | second;
      if (codePoint < 0x80) {
        throw error("Overlong UTF-8 sequence");
      }
      builder.append((char) codePoint);
    } else if ((first & 0xF0) == 0xE0) {
      int second = continuation();
      int third = continuation();
      int codePoint = ((first & 0x0F) << 12) | (second << 6) | third;
      if (codePoint < 0x800 || (codePoint >= 0xD800 && codePoint <= 0xDFFF)) {
        throw error("Invalid UTF-8 sequence");
      }
      builder.append((char) codePoint);
    } else if ((first & 0xF8) == 0xF0) {
      int second = continuation();
      int third = continuation();
      int fourth = continuation();
      int codePoint = ((first & 0x07) << 18) | (second << 12) | (third << 6) | fourth;
      if (codePoint < 0x10000 || codePoint > 0x10FFFF) {
        throw error("Invalid UTF-8 sequence");
      }
      builder.append(Character.highSurrogate(codePoint));
      builder.append(Character.lowSurrogate(codePoint));
    } else {
      throw error("Invalid UTF-8 sequence");
    }
  }

  private int continuation() {
    if (position >= input.length) {
      throw error("Short UTF-8 sequence");
    }
    int value = input[position++] & 0xFF;
    if ((value & 0xC0) != 0x80) {
      throw error("Invalid UTF-8 continuation");
    }
    return value & 0x3F;
  }
}
