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
  private static final int NO_SLOT = -1;

  private boolean hasCandidate0;
  private boolean hasCandidate1;
  private boolean hasCandidate2;
  private boolean hasCandidate3;
  private long candidate0;
  private long candidate1;
  private long candidate2;
  private long candidate3;
  private int nextCandidate;
  private boolean hasValue0;
  private boolean hasValue1;
  private boolean hasValue2;
  private boolean hasValue3;
  private long value0;
  private long value1;
  private long value2;
  private long value3;
  private int nextValue;
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

  boolean writeStringField(
      StringJsonWriter writer, long number, boolean comma, byte[] namePrefix, byte[] commaPrefix) {
    int slot = slot(number);
    if (slot == NO_SLOT) {
      return false;
    }
    byte[] token = stringToken(slot, number, comma, namePrefix, commaPrefix);
    writer.writeRawValue(token);
    return true;
  }

  boolean writeUtf8Field(
      Utf8JsonWriter writer, long number, boolean comma, byte[] namePrefix, byte[] commaPrefix) {
    int slot = slot(number);
    if (slot == NO_SLOT) {
      return false;
    }
    byte[] token = utf8Token(slot, number, comma, namePrefix, commaPrefix);
    writer.writeRawValue(token);
    return true;
  }

  private byte[] stringToken(
      int slot, long number, boolean comma, byte[] namePrefix, byte[] commaPrefix) {
    if (comma) {
      byte[] token = stringComma(slot);
      if (token == null) {
        token = fieldToken(commaPrefix, number);
        setStringComma(slot, token);
      }
      return token;
    }
    byte[] token = stringName(slot);
    if (token == null) {
      token = fieldToken(namePrefix, number);
      setStringName(slot, token);
    }
    return token;
  }

  private byte[] utf8Token(
      int slot, long number, boolean comma, byte[] namePrefix, byte[] commaPrefix) {
    if (comma) {
      byte[] token = utf8Comma(slot);
      if (token == null) {
        token = fieldToken(commaPrefix, number);
        setUtf8Comma(slot, token);
      }
      return token;
    }
    byte[] token = utf8Name(slot);
    if (token == null) {
      token = fieldToken(namePrefix, number);
      setUtf8Name(slot, token);
    }
    return token;
  }

  private int slot(long number) {
    if (hasValue0 && value0 == number) {
      return 0;
    }
    if (hasValue1 && value1 == number) {
      return 1;
    }
    if (hasValue2 && value2 == number) {
      return 2;
    }
    if (hasValue3 && value3 == number) {
      return 3;
    }
    return admit(number);
  }

  private int admit(long number) {
    if (hasCandidate0 && candidate0 == number) {
      return promote(number);
    }
    if (hasCandidate1 && candidate1 == number) {
      return promote(number);
    }
    if (hasCandidate2 && candidate2 == number) {
      return promote(number);
    }
    if (hasCandidate3 && candidate3 == number) {
      return promote(number);
    }
    setCandidate(nextCandidate, number);
    nextCandidate = (nextCandidate + 1) & 3;
    return NO_SLOT;
  }

  private int promote(long number) {
    if (!hasValue0) {
      hasValue0 = true;
      value0 = number;
      clearSlot(0);
      return 0;
    }
    if (!hasValue1) {
      hasValue1 = true;
      value1 = number;
      clearSlot(1);
      return 1;
    }
    if (!hasValue2) {
      hasValue2 = true;
      value2 = number;
      clearSlot(2);
      return 2;
    }
    if (!hasValue3) {
      hasValue3 = true;
      value3 = number;
      clearSlot(3);
      return 3;
    }
    int slot = nextValue;
    setValue(slot, number);
    clearSlot(slot);
    nextValue = (nextValue + 1) & 3;
    return slot;
  }

  private void setCandidate(int slot, long number) {
    switch (slot) {
      case 0:
        candidate0 = number;
        hasCandidate0 = true;
        return;
      case 1:
        candidate1 = number;
        hasCandidate1 = true;
        return;
      case 2:
        candidate2 = number;
        hasCandidate2 = true;
        return;
      default:
        candidate3 = number;
        hasCandidate3 = true;
    }
  }

  private void setValue(int slot, long number) {
    switch (slot) {
      case 0:
        value0 = number;
        hasValue0 = true;
        return;
      case 1:
        value1 = number;
        hasValue1 = true;
        return;
      case 2:
        value2 = number;
        hasValue2 = true;
        return;
      default:
        value3 = number;
        hasValue3 = true;
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
        stringName0 = null;
        stringComma0 = null;
        utf8Name0 = null;
        utf8Comma0 = null;
        return;
      case 1:
        stringName1 = null;
        stringComma1 = null;
        utf8Name1 = null;
        utf8Comma1 = null;
        return;
      case 2:
        stringName2 = null;
        stringComma2 = null;
        utf8Name2 = null;
        utf8Comma2 = null;
        return;
      default:
        stringName3 = null;
        stringComma3 = null;
        utf8Name3 = null;
        utf8Comma3 = null;
    }
  }

  private static byte[] fieldToken(byte[] prefix, long number) {
    byte[] value = Long.toString(number).getBytes(StandardCharsets.ISO_8859_1);
    byte[] token = new byte[prefix.length + value.length];
    System.arraycopy(prefix, 0, token, 0, prefix.length);
    System.arraycopy(value, 0, token, prefix.length, value.length);
    return token;
  }
}
