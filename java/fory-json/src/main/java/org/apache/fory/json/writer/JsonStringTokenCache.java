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

package org.apache.fory.json.writer;

public final class JsonStringTokenCache {
  private static final int MAX_CHARS = 256;
  private static final int NO_SLOT = -1;

  private String candidate0;
  private String candidate1;
  private String candidate2;
  private String candidate3;
  private int nextCandidate;
  private String value0;
  private String value1;
  private String value2;
  private String value3;
  private int nextValue;
  private byte[] stringValue0;
  private byte[] stringValue1;
  private byte[] stringValue2;
  private byte[] stringValue3;
  private byte[] utf8Value0;
  private byte[] utf8Value1;
  private byte[] utf8Value2;
  private byte[] utf8Value3;
  private byte[] stringName0;
  private byte[] stringName1;
  private byte[] stringName2;
  private byte[] stringName3;
  private byte[] stringComma0;
  private byte[] stringComma1;
  private byte[] stringComma2;
  private byte[] stringComma3;
  private byte[] utf8Name0;
  private byte[] utf8Name1;
  private byte[] utf8Name2;
  private byte[] utf8Name3;
  private byte[] utf8Comma0;
  private byte[] utf8Comma1;
  private byte[] utf8Comma2;
  private byte[] utf8Comma3;

  public boolean writeStringField(
      StringJsonWriter writer, String text, boolean comma, byte[] namePrefix, byte[] commaPrefix) {
    if (!latin1Token(text)) {
      return false;
    }
    int slot = slot(text);
    if (slot == NO_SLOT) {
      return false;
    }
    byte[] token = stringFieldToken(slot, text, comma, namePrefix, commaPrefix);
    writer.writeRawValue(token);
    return true;
  }

  public boolean writeStringCommaField(StringJsonWriter writer, String text, byte[] commaPrefix) {
    if (!latin1Token(text)) {
      return false;
    }
    int slot = slot(text);
    if (slot == NO_SLOT) {
      return false;
    }
    byte[] token = stringComma(slot);
    if (token == null) {
      token = join(commaPrefix, stringValueToken(slot, text));
      setStringComma(slot, token);
    }
    writer.writeRawValue(token);
    return true;
  }

  public boolean writeUtf8Field(
      Utf8JsonWriter writer, String text, boolean comma, byte[] namePrefix, byte[] commaPrefix) {
    int slot = slot(text);
    if (slot == NO_SLOT) {
      return false;
    }
    byte[] token = utf8FieldToken(slot, text, comma, namePrefix, commaPrefix);
    writer.writeRawValue(token);
    return true;
  }

  public boolean writeUtf8CommaField(Utf8JsonWriter writer, String text, byte[] commaPrefix) {
    int slot = slot(text);
    if (slot == NO_SLOT) {
      return false;
    }
    byte[] token = utf8Comma(slot);
    if (token == null) {
      token = join(commaPrefix, utf8ValueToken(slot, text));
      setUtf8Comma(slot, token);
    }
    writer.writeRawValue(token);
    return true;
  }

  public boolean writeStringValue(StringJsonWriter writer, String text) {
    if (!latin1Token(text)) {
      return false;
    }
    int slot = slot(text);
    if (slot == NO_SLOT) {
      return false;
    }
    byte[] token = stringValueToken(slot, text);
    writer.writeRawValue(token);
    return true;
  }

  public boolean writeUtf8Value(Utf8JsonWriter writer, String text) {
    int slot = slot(text);
    if (slot == NO_SLOT) {
      return false;
    }
    byte[] token = utf8ValueToken(slot, text);
    writer.writeRawValue(token);
    return true;
  }

  private byte[] stringFieldToken(
      int slot, String text, boolean comma, byte[] namePrefix, byte[] commaPrefix) {
    if (comma) {
      byte[] token = stringComma(slot);
      if (token == null) {
        token = join(commaPrefix, stringValueToken(slot, text));
        setStringComma(slot, token);
      }
      return token;
    }
    byte[] token = stringName(slot);
    if (token == null) {
      token = join(namePrefix, stringValueToken(slot, text));
      setStringName(slot, token);
    }
    return token;
  }

  private byte[] utf8FieldToken(
      int slot, String text, boolean comma, byte[] namePrefix, byte[] commaPrefix) {
    if (comma) {
      byte[] token = utf8Comma(slot);
      if (token == null) {
        token = join(commaPrefix, utf8ValueToken(slot, text));
        setUtf8Comma(slot, token);
      }
      return token;
    }
    byte[] token = utf8Name(slot);
    if (token == null) {
      token = join(namePrefix, utf8ValueToken(slot, text));
      setUtf8Name(slot, token);
    }
    return token;
  }

  private byte[] stringValueToken(int slot, String text) {
    byte[] token = stringValue(slot);
    if (token == null) {
      token = JsonStringEscaper.stringValue(text);
      setStringValue(slot, token);
    }
    return token;
  }

  private byte[] utf8ValueToken(int slot, String text) {
    byte[] token = utf8Value(slot);
    if (token == null) {
      token = JsonStringEscaper.utf8Value(text);
      setUtf8Value(slot, token);
    }
    return token;
  }

  private int slot(String text) {
    int slot = refSlot(text);
    if (slot != NO_SLOT) {
      return slot;
    }
    return slotSlow(text);
  }

  private int slotSlow(String text) {
    if (!cacheable(text)) {
      return NO_SLOT;
    }
    String value = value0;
    if (value != null && text.equals(value)) {
      return 0;
    }
    value = value1;
    if (value != null && text.equals(value)) {
      return 1;
    }
    value = value2;
    if (value != null && text.equals(value)) {
      return 2;
    }
    value = value3;
    if (value != null && text.equals(value)) {
      return 3;
    }
    return admit(text);
  }

  private int refSlot(String text) {
    String value = value0;
    if (text == value) {
      return 0;
    }
    value = value1;
    if (text == value) {
      return 1;
    }
    value = value2;
    if (text == value) {
      return 2;
    }
    value = value3;
    if (text == value) {
      return 3;
    }
    return NO_SLOT;
  }

  private int admit(String text) {
    String candidate = candidate0;
    if (text == candidate) {
      return promote(text);
    }
    candidate = candidate1;
    if (text == candidate) {
      return promote(text);
    }
    candidate = candidate2;
    if (text == candidate) {
      return promote(text);
    }
    candidate = candidate3;
    if (text == candidate) {
      return promote(text);
    }
    candidate = candidate0;
    if (candidate != null && text.equals(candidate)) {
      return promote(text);
    }
    candidate = candidate1;
    if (candidate != null && text.equals(candidate)) {
      return promote(text);
    }
    candidate = candidate2;
    if (candidate != null && text.equals(candidate)) {
      return promote(text);
    }
    candidate = candidate3;
    if (candidate != null && text.equals(candidate)) {
      return promote(text);
    }
    setCandidate(nextCandidate, text);
    nextCandidate = (nextCandidate + 1) & 3;
    return NO_SLOT;
  }

  private int promote(String text) {
    if (value0 == null) {
      value0 = text;
      clearSlot(0);
      return 0;
    }
    if (value1 == null) {
      value1 = text;
      clearSlot(1);
      return 1;
    }
    if (value2 == null) {
      value2 = text;
      clearSlot(2);
      return 2;
    }
    if (value3 == null) {
      value3 = text;
      clearSlot(3);
      return 3;
    }
    int slot = nextValue;
    setValue(slot, text);
    clearSlot(slot);
    nextValue = (nextValue + 1) & 3;
    return slot;
  }

  private void setCandidate(int slot, String text) {
    switch (slot) {
      case 0:
        candidate0 = text;
        return;
      case 1:
        candidate1 = text;
        return;
      case 2:
        candidate2 = text;
        return;
      default:
        candidate3 = text;
    }
  }

  private void setValue(int slot, String text) {
    switch (slot) {
      case 0:
        value0 = text;
        return;
      case 1:
        value1 = text;
        return;
      case 2:
        value2 = text;
        return;
      default:
        value3 = text;
    }
  }

  private byte[] stringValue(int slot) {
    switch (slot) {
      case 0:
        return stringValue0;
      case 1:
        return stringValue1;
      case 2:
        return stringValue2;
      default:
        return stringValue3;
    }
  }

  private void setStringValue(int slot, byte[] token) {
    switch (slot) {
      case 0:
        stringValue0 = token;
        return;
      case 1:
        stringValue1 = token;
        return;
      case 2:
        stringValue2 = token;
        return;
      default:
        stringValue3 = token;
    }
  }

  private byte[] utf8Value(int slot) {
    switch (slot) {
      case 0:
        return utf8Value0;
      case 1:
        return utf8Value1;
      case 2:
        return utf8Value2;
      default:
        return utf8Value3;
    }
  }

  private void setUtf8Value(int slot, byte[] token) {
    switch (slot) {
      case 0:
        utf8Value0 = token;
        return;
      case 1:
        utf8Value1 = token;
        return;
      case 2:
        utf8Value2 = token;
        return;
      default:
        utf8Value3 = token;
    }
  }

  private byte[] stringName(int slot) {
    switch (slot) {
      case 0:
        return stringName0;
      case 1:
        return stringName1;
      case 2:
        return stringName2;
      default:
        return stringName3;
    }
  }

  private void setStringName(int slot, byte[] token) {
    switch (slot) {
      case 0:
        stringName0 = token;
        return;
      case 1:
        stringName1 = token;
        return;
      case 2:
        stringName2 = token;
        return;
      default:
        stringName3 = token;
    }
  }

  private byte[] stringComma(int slot) {
    switch (slot) {
      case 0:
        return stringComma0;
      case 1:
        return stringComma1;
      case 2:
        return stringComma2;
      default:
        return stringComma3;
    }
  }

  private void setStringComma(int slot, byte[] token) {
    switch (slot) {
      case 0:
        stringComma0 = token;
        return;
      case 1:
        stringComma1 = token;
        return;
      case 2:
        stringComma2 = token;
        return;
      default:
        stringComma3 = token;
    }
  }

  private byte[] utf8Name(int slot) {
    switch (slot) {
      case 0:
        return utf8Name0;
      case 1:
        return utf8Name1;
      case 2:
        return utf8Name2;
      default:
        return utf8Name3;
    }
  }

  private void setUtf8Name(int slot, byte[] token) {
    switch (slot) {
      case 0:
        utf8Name0 = token;
        return;
      case 1:
        utf8Name1 = token;
        return;
      case 2:
        utf8Name2 = token;
        return;
      default:
        utf8Name3 = token;
    }
  }

  private byte[] utf8Comma(int slot) {
    switch (slot) {
      case 0:
        return utf8Comma0;
      case 1:
        return utf8Comma1;
      case 2:
        return utf8Comma2;
      default:
        return utf8Comma3;
    }
  }

  private void setUtf8Comma(int slot, byte[] token) {
    switch (slot) {
      case 0:
        utf8Comma0 = token;
        return;
      case 1:
        utf8Comma1 = token;
        return;
      case 2:
        utf8Comma2 = token;
        return;
      default:
        utf8Comma3 = token;
    }
  }

  private void clearSlot(int slot) {
    switch (slot) {
      case 0:
        stringValue0 = null;
        utf8Value0 = null;
        stringName0 = null;
        stringComma0 = null;
        utf8Name0 = null;
        utf8Comma0 = null;
        return;
      case 1:
        stringValue1 = null;
        utf8Value1 = null;
        stringName1 = null;
        stringComma1 = null;
        utf8Name1 = null;
        utf8Comma1 = null;
        return;
      case 2:
        stringValue2 = null;
        utf8Value2 = null;
        stringName2 = null;
        stringComma2 = null;
        utf8Name2 = null;
        utf8Comma2 = null;
        return;
      default:
        stringValue3 = null;
        utf8Value3 = null;
        stringName3 = null;
        stringComma3 = null;
        utf8Name3 = null;
        utf8Comma3 = null;
    }
  }

  private static boolean cacheable(String text) {
    return text.length() <= MAX_CHARS;
  }

  private static boolean latin1Token(String text) {
    int length = text.length();
    for (int i = 0; i < length; i++) {
      if (text.charAt(i) > 0xff) {
        return false;
      }
    }
    return true;
  }

  private static byte[] join(byte[] prefix, byte[] token) {
    byte[] joined = new byte[prefix.length + token.length];
    System.arraycopy(prefix, 0, joined, 0, prefix.length);
    System.arraycopy(token, 0, joined, prefix.length, token.length);
    return joined;
  }
}
