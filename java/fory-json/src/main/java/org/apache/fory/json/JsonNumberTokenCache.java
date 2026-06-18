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

final class JsonNumberTokenCache {
  private boolean hasCandidate;
  private long candidate;
  private boolean hasValue;
  private long value;
  private byte[] stringName;
  private byte[] stringComma;
  private byte[] utf8Name;
  private byte[] utf8Comma;

  boolean writeStringField(
      StringJsonWriter writer, long number, boolean comma, byte[] namePrefix, byte[] commaPrefix) {
    byte[] token = stringToken(number, comma, namePrefix, commaPrefix);
    if (token == null) {
      return false;
    }
    writer.writeRawValue(token);
    return true;
  }

  boolean writeUtf8Field(
      Utf8JsonWriter writer, long number, boolean comma, byte[] namePrefix, byte[] commaPrefix) {
    byte[] token = utf8Token(number, comma, namePrefix, commaPrefix);
    if (token == null) {
      return false;
    }
    writer.writeRawValue(token);
    return true;
  }

  private byte[] stringToken(long number, boolean comma, byte[] namePrefix, byte[] commaPrefix) {
    if (!admit(number)) {
      return null;
    }
    if (comma) {
      byte[] token = stringComma;
      if (token == null) {
        token = fieldToken(commaPrefix, number);
        stringComma = token;
      }
      return token;
    }
    byte[] token = stringName;
    if (token == null) {
      token = fieldToken(namePrefix, number);
      stringName = token;
    }
    return token;
  }

  private byte[] utf8Token(long number, boolean comma, byte[] namePrefix, byte[] commaPrefix) {
    if (!admit(number)) {
      return null;
    }
    if (comma) {
      byte[] token = utf8Comma;
      if (token == null) {
        token = fieldToken(commaPrefix, number);
        utf8Comma = token;
      }
      return token;
    }
    byte[] token = utf8Name;
    if (token == null) {
      token = fieldToken(namePrefix, number);
      utf8Name = token;
    }
    return token;
  }

  private boolean admit(long number) {
    if (hasValue && value == number) {
      return true;
    }
    if (hasCandidate && candidate == number) {
      value = number;
      hasValue = true;
      hasCandidate = false;
      clearTokens();
      return true;
    }
    candidate = number;
    hasCandidate = true;
    return false;
  }

  private void clearTokens() {
    stringName = null;
    stringComma = null;
    utf8Name = null;
    utf8Comma = null;
  }

  private static byte[] fieldToken(byte[] prefix, long number) {
    byte[] value = Long.toString(number).getBytes(StandardCharsets.ISO_8859_1);
    byte[] token = new byte[prefix.length + value.length];
    System.arraycopy(prefix, 0, token, 0, prefix.length);
    System.arraycopy(value, 0, token, prefix.length, value.length);
    return token;
  }
}
