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

package org.apache.fory.codegen.asm;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

final class ByteVector {
  private byte[] data = new byte[128];
  private int length;

  int length() {
    return length;
  }

  void putByte(int value) {
    ensure(1);
    data[length++] = (byte) value;
  }

  void putShort(int value) {
    ensure(2);
    data[length++] = (byte) (value >>> 8);
    data[length++] = (byte) value;
  }

  void putInt(int value) {
    ensure(4);
    data[length++] = (byte) (value >>> 24);
    data[length++] = (byte) (value >>> 16);
    data[length++] = (byte) (value >>> 8);
    data[length++] = (byte) value;
  }

  void putUTF(String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    if (bytes.length > 65535) {
      throw new IllegalArgumentException("UTF8 constant is too large: " + value);
    }
    putShort(bytes.length);
    putBytes(bytes);
  }

  void putBytes(byte[] bytes) {
    ensure(bytes.length);
    System.arraycopy(bytes, 0, data, length, bytes.length);
    length += bytes.length;
  }

  byte[] toByteArray() {
    return Arrays.copyOf(data, length);
  }

  private void ensure(int additionalBytes) {
    int required = length + additionalBytes;
    if (required > data.length) {
      int newLength = data.length << 1;
      while (newLength < required) {
        newLength <<= 1;
      }
      data = Arrays.copyOf(data, newLength);
    }
  }
}
