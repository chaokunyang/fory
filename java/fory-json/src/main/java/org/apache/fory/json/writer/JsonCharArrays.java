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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.apache.fory.memory.NativeByteOrder;
import org.apache.fory.platform.AndroidSupport;

public final class JsonCharArrays {
  private static final MethodHandle PUT_LONG;
  private static final int CHAR_ARRAY_OFFSET;

  static {
    if (AndroidSupport.IS_ANDROID) {
      CHAR_ARRAY_OFFSET = 0;
      PUT_LONG = null;
    } else {
      Object unsafe = unsafe();
      CHAR_ARRAY_OFFSET = charArrayOffset(unsafe);
      PUT_LONG = putLong(unsafe);
    }
  }

  private JsonCharArrays() {}

  static void putInt64(char[] chars, int index, long value) {
    if (AndroidSupport.IS_ANDROID) {
      chars[index] = (char) value;
      chars[index + 1] = (char) (value >>> 16);
      chars[index + 2] = (char) (value >>> 32);
      chars[index + 3] = (char) (value >>> 48);
      return;
    }
    if (!NativeByteOrder.IS_LITTLE_ENDIAN) {
      value = Long.reverseBytes(value);
    }
    try {
      PUT_LONG.invokeExact((Object) chars, (long) CHAR_ARRAY_OFFSET + ((long) index << 1), value);
    } catch (Throwable e) {
      throw rethrow(e);
    }
  }

  private static Object unsafe() {
    try {
      // Keep Unsafe reflective so fory-json does not expose sun.misc in the JDK25 class graph.
      Class<?> type = Class.forName("sun.misc.Unsafe");
      Field field = type.getDeclaredField("theUnsafe");
      field.setAccessible(true);
      return field.get(null);
    } catch (ReflectiveOperationException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private static int charArrayOffset(Object unsafe) {
    try {
      Method method = unsafe.getClass().getMethod("arrayBaseOffset", Class.class);
      return ((Integer) method.invoke(unsafe, char[].class)).intValue();
    } catch (ReflectiveOperationException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private static MethodHandle putLong(Object unsafe) {
    try {
      Method method = unsafe.getClass().getMethod("putLong", Object.class, long.class, long.class);
      return MethodHandles.lookup().unreflect(method).bindTo(unsafe);
    } catch (ReflectiveOperationException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private static RuntimeException rethrow(Throwable e) {
    if (e instanceof RuntimeException) {
      return (RuntimeException) e;
    }
    if (e instanceof Error) {
      throw (Error) e;
    }
    return new IllegalStateException(e);
  }
}
