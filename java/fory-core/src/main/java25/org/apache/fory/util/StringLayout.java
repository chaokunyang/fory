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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.nio.ByteOrder;
import org.apache.fory.annotation.Internal;
import org.apache.fory.platform.GraalvmSupport;
import org.apache.fory.platform.internal._JDKAccess;

/** JVM string layout view for codecs that need read-only access to compact strings. */
@Internal
public final class StringLayout {
  public static final byte LATIN1 = 0;
  public static final byte UTF16 = 1;

  private static final StringAccess STRING_ACCESS = stringAccess();
  private static final boolean STRING_VALUE_FIELD_IS_BYTES =
      STRING_ACCESS.fieldAccess && STRING_ACCESS.valueFieldIsBytes;
  private static final VarHandle STRING_VALUE_HANDLE = STRING_ACCESS.value;
  private static final VarHandle STRING_CODER_HANDLE = STRING_ACCESS.coder;
  private static final boolean LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;

  private StringLayout() {}

  public static boolean isBytesBacked() {
    return STRING_VALUE_FIELD_IS_BYTES;
  }

  public static byte[] bytes(String value) {
    if (!STRING_VALUE_FIELD_IS_BYTES) {
      throw new IllegalStateException("String byte layout is not available");
    }
    return (byte[]) STRING_VALUE_HANDLE.get(value);
  }

  public static byte coder(String value) {
    if (!STRING_VALUE_FIELD_IS_BYTES) {
      throw new IllegalStateException("String byte layout is not available");
    }
    return (byte) STRING_CODER_HANDLE.get(value);
  }

  public static char utf16Char(byte[] bytes, int byteIndex) {
    if (LITTLE_ENDIAN) {
      return (char) ((bytes[byteIndex] & 0xff) | ((bytes[byteIndex + 1] & 0xff) << 8));
    }
    return (char) (((bytes[byteIndex] & 0xff) << 8) | (bytes[byteIndex + 1] & 0xff));
  }

  private static StringAccess stringAccess() {
    if (GraalvmSupport.IN_GRAALVM_NATIVE_IMAGE || !_JDKAccess.JDK_INTERNAL_FIELD_ACCESS) {
      return StringAccess.noAccess();
    }
    try {
      Field valueField = String.class.getDeclaredField("value");
      boolean valueFieldIsBytes = valueField.getType() == byte[].class;
      try {
        Lookup stringLookup = _JDKAccess._trustedLookup(String.class);
        return new StringAccess(
            true,
            valueFieldIsBytes,
            stringLookup.findVarHandle(String.class, "value", valueField.getType()),
            valueFieldIsBytes ? stringLookup.findVarHandle(String.class, "coder", byte.class) : null);
      } catch (Throwable e) {
        throw new IllegalStateException(
            "JDK25+ string internals are inaccessible. " + _JDKAccess.jdk25AccessMessage(),
            e);
      }
    } catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
  }

  private static final class StringAccess {
    private final boolean fieldAccess;
    private final boolean valueFieldIsBytes;
    private final VarHandle value;
    private final VarHandle coder;

    private StringAccess(
        boolean fieldAccess, boolean valueFieldIsBytes, VarHandle value, VarHandle coder) {
      this.fieldAccess = fieldAccess;
      this.valueFieldIsBytes = valueFieldIsBytes;
      this.value = value;
      this.coder = coder;
    }

    private static StringAccess noAccess() {
      return new StringAccess(false, false, null, null);
    }
  }
}
