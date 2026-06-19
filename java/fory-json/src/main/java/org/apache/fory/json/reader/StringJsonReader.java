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

public final class StringJsonReader extends JsonReader {
  private final String input;

  public StringJsonReader(String input) {
    this.input = input;
  }

  @Override
  protected int length() {
    return input.length();
  }

  @Override
  protected char charAt(int index) {
    return input.charAt(index);
  }

  @Override
  public String readString() {
    skipWhitespace();
    if (position >= input.length() || input.charAt(position++) != '"') {
      throw error("Expected string");
    }
    int start = position;
    StringBuilder builder = null;
    while (position < input.length()) {
      char ch = input.charAt(position++);
      if (ch == '"') {
        if (builder == null) {
          return input.substring(start, position - 1);
        }
        builder.append(input, start, position - 1);
        return builder.toString();
      } else if (ch == '\\') {
        if (builder == null) {
          builder = new StringBuilder();
        }
        builder.append(input, start, position - 1);
        appendEscape(builder);
        start = position;
      } else if (ch < 0x20) {
        throw error("Control character in string");
      } else if (Character.isHighSurrogate(ch)) {
        if (position >= input.length() || !Character.isLowSurrogate(input.charAt(position))) {
          throw error("Unpaired high surrogate in string");
        }
        position++;
      } else if (Character.isLowSurrogate(ch)) {
        throw error("Unpaired low surrogate in string");
      }
    }
    throw error("Unterminated string");
  }

  @Override
  protected String slice(int start, int end) {
    return input.substring(start, end);
  }
}
