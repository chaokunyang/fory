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

package org.apache.fory.serializer;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.memory.NativeByteOrder;
import org.apache.fory.platform.AndroidSupport;
import org.apache.fory.platform.GraalvmSupport;
import org.apache.fory.platform.internal._JDKAccess;

/** JDK25 string internals used by {@link StringSerializer}. */
final class PlatformStringUtils {
  static final boolean JDK_STRING_FIELD_ACCESS =
      !AndroidSupport.IS_ANDROID
          && !GraalvmSupport.IN_GRAALVM_NATIVE_IMAGE
          && _JDKAccess.JDK_STRING_FIELD_ACCESS;
  static final boolean STRING_VALUE_FIELD_IS_CHARS =
      JDK_STRING_FIELD_ACCESS && _JDKAccess.STRING_VALUE_FIELD_IS_CHARS;
  static final boolean STRING_VALUE_FIELD_IS_BYTES =
      JDK_STRING_FIELD_ACCESS && _JDKAccess.STRING_VALUE_FIELD_IS_BYTES;
  static final boolean STRING_HAS_COUNT_OFFSET =
      JDK_STRING_FIELD_ACCESS && _JDKAccess.STRING_HAS_COUNT_OFFSET;

  private static final byte LATIN1 = 0;
  private static final byte UTF16 = 1;
  private static final VarHandle BYTE_ARRAY_LONG =
      MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.nativeOrder());
  private static final VarHandle BYTE_ARRAY_CHAR =
      MethodHandles.byteArrayViewVarHandle(char[].class, ByteOrder.nativeOrder());

  private PlatformStringUtils() {}

  static Object getStringValue(String value) {
    return _JDKAccess.getStringValue(value);
  }

  static byte getStringCoder(String value) {
    return _JDKAccess.getStringCoder(value);
  }

  static int getStringOffset(String value) {
    return _JDKAccess.getStringOffset(value);
  }

  static int getStringCount(String value) {
    return _JDKAccess.getStringCount(value);
  }

  static String newCharsStringZeroCopy(char[] data) {
    if (!JDK_STRING_FIELD_ACCESS) {
      return new String(data);
    }
    return _JDKAccess.newCharsStringZeroCopy(data);
  }

  static String newBytesStringZeroCopy(byte coder, byte[] data) {
    if (!JDK_STRING_FIELD_ACCESS) {
      return newBytesStringSlow(coder, data);
    }
    return _JDKAccess.newBytesStringZeroCopy(coder, data);
  }

  private static String newBytesStringSlow(byte coder, byte[] data) {
    if (coder == LATIN1) {
      return new String(data, StandardCharsets.ISO_8859_1);
    } else if (coder == UTF16) {
      char[] chars = new char[data.length >> 1];
      for (int i = 0, j = 0; i < data.length; i += 2) {
        chars[j++] = (char) ((data[i] & 0xff) | ((data[i + 1] & 0xff) << 8));
      }
      return new String(chars);
    } else {
      return new String(data, StandardCharsets.UTF_8);
    }
  }

  static long getCharsLong(char[] chars, int charIndex) {
    long c0 = chars[charIndex];
    long c1 = chars[charIndex + 1];
    long c2 = chars[charIndex + 2];
    long c3 = chars[charIndex + 3];
    if (NativeByteOrder.IS_LITTLE_ENDIAN) {
      return c0 | (c1 << 16) | (c2 << 32) | (c3 << 48);
    } else {
      return (c0 << 48) | (c1 << 32) | (c2 << 16) | c3;
    }
  }

  static long getBytesLong(byte[] bytes, int byteIndex) {
    return (long) BYTE_ARRAY_LONG.get(bytes, byteIndex);
  }

  static char getBytesChar(byte[] bytes, int byteIndex) {
    return (char) BYTE_ARRAY_CHAR.get(bytes, byteIndex);
  }

  static void copyCharsToBytes(
      char[] chars, int charOffset, byte[] target, int byteOffset, int numBytes) {
    int charIndex = charOffset;
    if (NativeByteOrder.IS_LITTLE_ENDIAN) {
      for (int i = byteOffset, end = byteOffset + numBytes; i < end; i += 2) {
        char c = chars[charIndex++];
        target[i] = (byte) c;
        target[i + 1] = (byte) (c >>> 8);
      }
    } else {
      for (int i = byteOffset, end = byteOffset + numBytes; i < end; i += 2) {
        char c = chars[charIndex++];
        target[i] = (byte) (c >>> 8);
        target[i + 1] = (byte) c;
      }
    }
  }

  static void putBytes(MemoryBuffer buffer, int writerIndex, byte[] bytes, int numBytes) {
    buffer.put(writerIndex, bytes, 0, numBytes);
  }
}
