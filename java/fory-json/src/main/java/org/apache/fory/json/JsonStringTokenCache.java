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

final class JsonStringTokenCache {
  private static final int MAX_CHARS = 256;

  private String candidate;
  private String value;
  private byte[] stringValue;
  private byte[] utf8Value;
  private byte[] stringName;
  private byte[] stringComma;
  private byte[] utf8Name;
  private byte[] utf8Comma;

  boolean writeStringField(
      StringJsonWriter writer, String text, boolean comma, byte[] namePrefix, byte[] commaPrefix) {
    byte[] token = stringFieldToken(text, comma, namePrefix, commaPrefix);
    if (token == null) {
      return false;
    }
    writer.writeRawValue(token);
    return true;
  }

  boolean writeUtf8Field(
      Utf8JsonWriter writer, String text, boolean comma, byte[] namePrefix, byte[] commaPrefix) {
    byte[] token = utf8FieldToken(text, comma, namePrefix, commaPrefix);
    if (token == null) {
      return false;
    }
    writer.writeRawValue(token);
    return true;
  }

  boolean writeStringValue(StringJsonWriter writer, String text) {
    byte[] token = stringValueToken(text);
    if (token == null) {
      return false;
    }
    writer.writeRawValue(token);
    return true;
  }

  boolean writeUtf8Value(Utf8JsonWriter writer, String text) {
    byte[] token = utf8ValueToken(text);
    if (token == null) {
      return false;
    }
    writer.writeRawValue(token);
    return true;
  }

  private byte[] stringFieldToken(
      String text, boolean comma, byte[] namePrefix, byte[] commaPrefix) {
    if (!cacheable(text)) {
      return null;
    }
    if (!admit(text)) {
      return null;
    }
    if (comma) {
      byte[] token = stringComma;
      if (token == null) {
        token = join(commaPrefix, stringValueToken(text));
        stringComma = token;
      }
      return token;
    }
    byte[] token = stringName;
    if (token == null) {
      token = join(namePrefix, stringValueToken(text));
      stringName = token;
    }
    return token;
  }

  private byte[] utf8FieldToken(String text, boolean comma, byte[] namePrefix, byte[] commaPrefix) {
    if (!cacheable(text)) {
      return null;
    }
    if (!admit(text)) {
      return null;
    }
    if (comma) {
      byte[] token = utf8Comma;
      if (token == null) {
        token = join(commaPrefix, utf8ValueToken(text));
        utf8Comma = token;
      }
      return token;
    }
    byte[] token = utf8Name;
    if (token == null) {
      token = join(namePrefix, utf8ValueToken(text));
      utf8Name = token;
    }
    return token;
  }

  private byte[] stringValueToken(String text) {
    if (!cacheable(text)) {
      return null;
    }
    if (!admit(text)) {
      return null;
    }
    byte[] token = stringValue;
    if (token == null) {
      token = JsonStringEscaper.stringValue(text);
      stringValue = token;
    }
    return token;
  }

  private byte[] utf8ValueToken(String text) {
    if (!cacheable(text)) {
      return null;
    }
    if (!admit(text)) {
      return null;
    }
    byte[] token = utf8Value;
    if (token == null) {
      token = JsonStringEscaper.utf8Value(text);
      utf8Value = token;
    }
    return token;
  }

  private boolean admit(String text) {
    String cached = value;
    if (text == cached || cached != null && text.equals(cached)) {
      return true;
    }
    String pending = candidate;
    if (text == pending || pending != null && text.equals(pending)) {
      value = text;
      candidate = null;
      clearTokens();
      return true;
    }
    candidate = text;
    return false;
  }

  private void clearTokens() {
    stringValue = null;
    utf8Value = null;
    stringName = null;
    stringComma = null;
    utf8Name = null;
    utf8Comma = null;
  }

  private static boolean cacheable(String text) {
    return text.length() <= MAX_CHARS;
  }

  private static byte[] join(byte[] prefix, byte[] token) {
    byte[] joined = new byte[prefix.length + token.length];
    System.arraycopy(prefix, 0, joined, 0, prefix.length);
    System.arraycopy(token, 0, joined, prefix.length, token.length);
    return joined;
  }
}
