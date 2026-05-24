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

import java.nio.charset.StandardCharsets;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.platform.AndroidSupport;
import org.apache.fory.platform.GraalvmSupport;
import org.apache.fory.platform.internal._JDKAccess;
import sun.misc.Unsafe;

/** Platform-owned string internals used by {@link StringSerializer}. */
final class PlatformStringUtils {
  private static final Unsafe UNSAFE = AndroidSupport.IS_ANDROID ? null : _JDKAccess.UNSAFE;
  private static final int BYTE_ARRAY_OFFSET =
      AndroidSupport.IS_ANDROID ? 0 : UNSAFE.arrayBaseOffset(byte[].class);
  private static final int CHAR_ARRAY_OFFSET =
      AndroidSupport.IS_ANDROID ? 0 : UNSAFE.arrayBaseOffset(char[].class);

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

  private static final long STRING_VALUE_FIELD_OFFSET =
      JDK_STRING_FIELD_ACCESS ? _JDKAccess.STRING_VALUE_FIELD_OFFSET : -1;
  private static final long STRING_CODER_FIELD_OFFSET =
      JDK_STRING_FIELD_ACCESS ? _JDKAccess.STRING_CODER_FIELD_OFFSET : -1;
  private static final long STRING_COUNT_FIELD_OFFSET =
      JDK_STRING_FIELD_ACCESS ? _JDKAccess.STRING_COUNT_FIELD_OFFSET : -1;
  private static final long STRING_OFFSET_FIELD_OFFSET =
      JDK_STRING_FIELD_ACCESS ? _JDKAccess.STRING_OFFSET_FIELD_OFFSET : -1;

  private static final byte LATIN1 = 0;
  private static final byte UTF16 = 1;

  private PlatformStringUtils() {}

  static Object getStringValue(String value) {
    return UNSAFE.getObject(value, STRING_VALUE_FIELD_OFFSET);
  }

  static byte getStringCoder(String value) {
    return UNSAFE.getByte(value, STRING_CODER_FIELD_OFFSET);
  }

  static int getStringOffset(String value) {
    return UNSAFE.getInt(value, STRING_OFFSET_FIELD_OFFSET);
  }

  static int getStringCount(String value) {
    return UNSAFE.getInt(value, STRING_COUNT_FIELD_OFFSET);
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
    return UNSAFE.getLong(chars, CHAR_ARRAY_OFFSET + ((long) charIndex << 1));
  }

  static long getBytesLong(byte[] bytes, int byteIndex) {
    return UNSAFE.getLong(bytes, BYTE_ARRAY_OFFSET + byteIndex);
  }

  static char getBytesChar(byte[] bytes, int byteIndex) {
    return UNSAFE.getChar(bytes, BYTE_ARRAY_OFFSET + byteIndex);
  }

  static void copyCharsToBytes(
      char[] chars, int charOffset, byte[] target, int byteOffset, int numBytes) {
    UNSAFE.copyMemory(
        chars,
        CHAR_ARRAY_OFFSET + ((long) charOffset << 1),
        target,
        BYTE_ARRAY_OFFSET + byteOffset,
        numBytes);
  }

  static void putBytes(MemoryBuffer buffer, int writerIndex, byte[] bytes, int numBytes) {
    long address = buffer._unsafeWriterAddress() + writerIndex - buffer.writerIndex();
    UNSAFE.copyMemory(bytes, BYTE_ARRAY_OFFSET, null, address, numBytes);
  }
}
