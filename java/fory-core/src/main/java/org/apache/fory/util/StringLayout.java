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

package org.apache.fory.util;

import java.lang.reflect.Field;
import org.apache.fory.annotation.Internal;
import org.apache.fory.memory.NativeByteOrder;
import org.apache.fory.platform.AndroidSupport;
import org.apache.fory.platform.GraalvmSupport;
import org.apache.fory.platform.internal._JDKAccess;
import org.apache.fory.platform.internal._UnsafeUtils;
import sun.misc.Unsafe;

/** JVM string layout view for codecs that need read-only access to compact strings. */
@Internal
public final class StringLayout {
  public static final byte LATIN1 = 0;
  public static final byte UTF16 = 1;

  private static final Unsafe UNSAFE = AndroidSupport.IS_ANDROID ? null : _UnsafeUtils.UNSAFE;
  private static final int BYTE_ARRAY_OFFSET;

  static {
    if (AndroidSupport.IS_ANDROID) {
      BYTE_ARRAY_OFFSET = 0;
    } else {
      BYTE_ARRAY_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);
    }
  }

  private static final StringFields STRING_FIELDS = stringFields();
  private static final boolean STRING_VALUE_FIELD_IS_BYTES =
      STRING_FIELDS.fieldAccess && STRING_FIELDS.valueFieldIsBytes;
  private static final long STRING_VALUE_FIELD_OFFSET = STRING_FIELDS.valueOffset;
  private static final long STRING_CODER_FIELD_OFFSET = STRING_FIELDS.coderOffset;

  private StringLayout() {}

  public static boolean isBytesBacked() {
    return STRING_VALUE_FIELD_IS_BYTES;
  }

  public static byte[] bytes(String value) {
    if (!STRING_VALUE_FIELD_IS_BYTES) {
      throw new IllegalStateException("String byte layout is not available");
    }
    return (byte[]) UNSAFE.getObject(value, STRING_VALUE_FIELD_OFFSET);
  }

  public static byte coder(String value) {
    if (!STRING_VALUE_FIELD_IS_BYTES) {
      throw new IllegalStateException("String byte layout is not available");
    }
    return UNSAFE.getByte(value, STRING_CODER_FIELD_OFFSET);
  }

  public static char utf16Char(byte[] bytes, int byteIndex) {
    if (AndroidSupport.IS_ANDROID) {
      if (NativeByteOrder.IS_LITTLE_ENDIAN) {
        return (char) ((bytes[byteIndex] & 0xff) | ((bytes[byteIndex + 1] & 0xff) << 8));
      } else {
        return (char) (((bytes[byteIndex] & 0xff) << 8) | (bytes[byteIndex + 1] & 0xff));
      }
    }
    return UNSAFE.getChar(bytes, (long) BYTE_ARRAY_OFFSET + byteIndex);
  }

  private static StringFields stringFields() {
    if (AndroidSupport.IS_ANDROID
        || GraalvmSupport.IN_GRAALVM_NATIVE_IMAGE
        || !_JDKAccess.JDK_INTERNAL_FIELD_ACCESS) {
      return StringFields.noAccess();
    }
    try {
      Field valueField = String.class.getDeclaredField("value");
      boolean valueFieldIsBytes = valueField.getType() == byte[].class;
      long valueOffset = UNSAFE.objectFieldOffset(valueField);
      long coderOffset = valueFieldIsBytes ? stringCoderFieldOffset() : -1;
      return new StringFields(true, valueFieldIsBytes, valueOffset, coderOffset);
    } catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
  }

  private static long stringCoderFieldOffset() {
    try {
      return UNSAFE.objectFieldOffset(String.class.getDeclaredField("coder"));
    } catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
  }

  private static final class StringFields {
    private final boolean fieldAccess;
    private final boolean valueFieldIsBytes;
    private final long valueOffset;
    private final long coderOffset;

    private StringFields(
        boolean fieldAccess, boolean valueFieldIsBytes, long valueOffset, long coderOffset) {
      this.fieldAccess = fieldAccess;
      this.valueFieldIsBytes = valueFieldIsBytes;
      this.valueOffset = valueOffset;
      this.coderOffset = coderOffset;
    }

    private static StringFields noAccess() {
      return new StringFields(false, false, -1, -1);
    }
  }
}
