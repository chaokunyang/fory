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

package org.apache.fory.json.meta;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.apache.fory.json.ForyJsonException;

public abstract class JsonMemberAccessor {
  public Object getObject(Object target) {
    throw new UnsupportedOperationException();
  }

  public boolean getBoolean(Object target) {
    return ((Boolean) getObject(target)).booleanValue();
  }

  public byte getByte(Object target) {
    return ((Byte) getObject(target)).byteValue();
  }

  public short getShort(Object target) {
    return ((Short) getObject(target)).shortValue();
  }

  public int getInt(Object target) {
    return ((Integer) getObject(target)).intValue();
  }

  public long getLong(Object target) {
    return ((Long) getObject(target)).longValue();
  }

  public float getFloat(Object target) {
    return ((Float) getObject(target)).floatValue();
  }

  public double getDouble(Object target) {
    return ((Double) getObject(target)).doubleValue();
  }

  public char getChar(Object target) {
    return ((Character) getObject(target)).charValue();
  }

  public void putObject(Object target, Object value) {
    throw new UnsupportedOperationException();
  }

  public void putBoolean(Object target, boolean value) {
    putObject(target, Boolean.valueOf(value));
  }

  public void putByte(Object target, byte value) {
    putObject(target, Byte.valueOf(value));
  }

  public void putShort(Object target, short value) {
    putObject(target, Short.valueOf(value));
  }

  public void putInt(Object target, int value) {
    putObject(target, Integer.valueOf(value));
  }

  public void putLong(Object target, long value) {
    putObject(target, Long.valueOf(value));
  }

  public void putFloat(Object target, float value) {
    putObject(target, Float.valueOf(value));
  }

  public void putDouble(Object target, double value) {
    putObject(target, Double.valueOf(value));
  }

  public void putChar(Object target, char value) {
    putObject(target, Character.valueOf(value));
  }

  static JsonMemberAccessor forField(Field field) {
    if (UnsafeFieldMemberAccessor.isAvailable()) {
      return new UnsafeFieldMemberAccessor(field);
    }
    return new FieldMemberAccessor(field);
  }

  static JsonMemberAccessor forMethod(Method method) {
    try {
      return new MethodMemberAccessor(MethodHandles.lookup().unreflect(method));
    } catch (IllegalAccessException e) {
      throw new ForyJsonException("Cannot access JSON method " + method, e);
    }
  }

  private static final class FieldMemberAccessor extends JsonMemberAccessor {
    private final Field field;

    private FieldMemberAccessor(Field field) {
      this.field = field;
      field.setAccessible(true);
    }

    @Override
    public Object getObject(Object target) {
      try {
        return field.get(target);
      } catch (IllegalAccessException e) {
        throw new ForyJsonException("Failed to read JSON field " + field, e);
      }
    }

    @Override
    public boolean getBoolean(Object target) {
      try {
        return field.getBoolean(target);
      } catch (IllegalAccessException e) {
        throw new ForyJsonException("Failed to read JSON field " + field, e);
      }
    }

    @Override
    public byte getByte(Object target) {
      try {
        return field.getByte(target);
      } catch (IllegalAccessException e) {
        throw new ForyJsonException("Failed to read JSON field " + field, e);
      }
    }

    @Override
    public short getShort(Object target) {
      try {
        return field.getShort(target);
      } catch (IllegalAccessException e) {
        throw new ForyJsonException("Failed to read JSON field " + field, e);
      }
    }

    @Override
    public int getInt(Object target) {
      try {
        return field.getInt(target);
      } catch (IllegalAccessException e) {
        throw new ForyJsonException("Failed to read JSON field " + field, e);
      }
    }

    @Override
    public long getLong(Object target) {
      try {
        return field.getLong(target);
      } catch (IllegalAccessException e) {
        throw new ForyJsonException("Failed to read JSON field " + field, e);
      }
    }

    @Override
    public float getFloat(Object target) {
      try {
        return field.getFloat(target);
      } catch (IllegalAccessException e) {
        throw new ForyJsonException("Failed to read JSON field " + field, e);
      }
    }

    @Override
    public double getDouble(Object target) {
      try {
        return field.getDouble(target);
      } catch (IllegalAccessException e) {
        throw new ForyJsonException("Failed to read JSON field " + field, e);
      }
    }

    @Override
    public char getChar(Object target) {
      try {
        return field.getChar(target);
      } catch (IllegalAccessException e) {
        throw new ForyJsonException("Failed to read JSON field " + field, e);
      }
    }

    @Override
    public void putObject(Object target, Object value) {
      try {
        field.set(target, value);
      } catch (IllegalAccessException e) {
        throw new ForyJsonException("Failed to write JSON field " + field, e);
      }
    }

    @Override
    public void putBoolean(Object target, boolean value) {
      try {
        field.setBoolean(target, value);
      } catch (IllegalAccessException e) {
        throw new ForyJsonException("Failed to write JSON field " + field, e);
      }
    }

    @Override
    public void putByte(Object target, byte value) {
      try {
        field.setByte(target, value);
      } catch (IllegalAccessException e) {
        throw new ForyJsonException("Failed to write JSON field " + field, e);
      }
    }

    @Override
    public void putShort(Object target, short value) {
      try {
        field.setShort(target, value);
      } catch (IllegalAccessException e) {
        throw new ForyJsonException("Failed to write JSON field " + field, e);
      }
    }

    @Override
    public void putInt(Object target, int value) {
      try {
        field.setInt(target, value);
      } catch (IllegalAccessException e) {
        throw new ForyJsonException("Failed to write JSON field " + field, e);
      }
    }

    @Override
    public void putLong(Object target, long value) {
      try {
        field.setLong(target, value);
      } catch (IllegalAccessException e) {
        throw new ForyJsonException("Failed to write JSON field " + field, e);
      }
    }

    @Override
    public void putFloat(Object target, float value) {
      try {
        field.setFloat(target, value);
      } catch (IllegalAccessException e) {
        throw new ForyJsonException("Failed to write JSON field " + field, e);
      }
    }

    @Override
    public void putDouble(Object target, double value) {
      try {
        field.setDouble(target, value);
      } catch (IllegalAccessException e) {
        throw new ForyJsonException("Failed to write JSON field " + field, e);
      }
    }

    @Override
    public void putChar(Object target, char value) {
      try {
        field.setChar(target, value);
      } catch (IllegalAccessException e) {
        throw new ForyJsonException("Failed to write JSON field " + field, e);
      }
    }
  }

  private static final class UnsafeFieldMemberAccessor extends JsonMemberAccessor {
    private final long offset;

    private UnsafeFieldMemberAccessor(Field field) {
      try {
        offset = (long) UnsafeAccess.OBJECT_FIELD_OFFSET.invokeExact(UnsafeAccess.UNSAFE, field);
      } catch (Throwable e) {
        throw new ForyJsonException("Failed to access JSON field offset " + field, e);
      }
    }

    private static boolean isAvailable() {
      return UnsafeAccess.UNSAFE != null && UnsafeAccess.OBJECT_FIELD_OFFSET != null;
    }

    @Override
    public Object getObject(Object target) {
      try {
        return UnsafeAccess.GET_OBJECT.invokeExact(UnsafeAccess.UNSAFE, target, offset);
      } catch (Throwable e) {
        throw new ForyJsonException("Failed to read JSON field", e);
      }
    }

    @Override
    public boolean getBoolean(Object target) {
      try {
        return (boolean) UnsafeAccess.GET_BOOLEAN.invokeExact(UnsafeAccess.UNSAFE, target, offset);
      } catch (Throwable e) {
        throw new ForyJsonException("Failed to read JSON field", e);
      }
    }

    @Override
    public byte getByte(Object target) {
      try {
        return (byte) UnsafeAccess.GET_BYTE.invokeExact(UnsafeAccess.UNSAFE, target, offset);
      } catch (Throwable e) {
        throw new ForyJsonException("Failed to read JSON field", e);
      }
    }

    @Override
    public short getShort(Object target) {
      try {
        return (short) UnsafeAccess.GET_SHORT.invokeExact(UnsafeAccess.UNSAFE, target, offset);
      } catch (Throwable e) {
        throw new ForyJsonException("Failed to read JSON field", e);
      }
    }

    @Override
    public int getInt(Object target) {
      try {
        return (int) UnsafeAccess.GET_INT.invokeExact(UnsafeAccess.UNSAFE, target, offset);
      } catch (Throwable e) {
        throw new ForyJsonException("Failed to read JSON field", e);
      }
    }

    @Override
    public long getLong(Object target) {
      try {
        return (long) UnsafeAccess.GET_LONG.invokeExact(UnsafeAccess.UNSAFE, target, offset);
      } catch (Throwable e) {
        throw new ForyJsonException("Failed to read JSON field", e);
      }
    }

    @Override
    public float getFloat(Object target) {
      try {
        return (float) UnsafeAccess.GET_FLOAT.invokeExact(UnsafeAccess.UNSAFE, target, offset);
      } catch (Throwable e) {
        throw new ForyJsonException("Failed to read JSON field", e);
      }
    }

    @Override
    public double getDouble(Object target) {
      try {
        return (double) UnsafeAccess.GET_DOUBLE.invokeExact(UnsafeAccess.UNSAFE, target, offset);
      } catch (Throwable e) {
        throw new ForyJsonException("Failed to read JSON field", e);
      }
    }

    @Override
    public char getChar(Object target) {
      try {
        return (char) UnsafeAccess.GET_CHAR.invokeExact(UnsafeAccess.UNSAFE, target, offset);
      } catch (Throwable e) {
        throw new ForyJsonException("Failed to read JSON field", e);
      }
    }

    @Override
    public void putObject(Object target, Object value) {
      try {
        UnsafeAccess.PUT_OBJECT.invokeExact(UnsafeAccess.UNSAFE, target, offset, value);
      } catch (Throwable e) {
        throw new ForyJsonException("Failed to write JSON field", e);
      }
    }

    @Override
    public void putBoolean(Object target, boolean value) {
      try {
        UnsafeAccess.PUT_BOOLEAN.invokeExact(UnsafeAccess.UNSAFE, target, offset, value);
      } catch (Throwable e) {
        throw new ForyJsonException("Failed to write JSON field", e);
      }
    }

    @Override
    public void putByte(Object target, byte value) {
      try {
        UnsafeAccess.PUT_BYTE.invokeExact(UnsafeAccess.UNSAFE, target, offset, value);
      } catch (Throwable e) {
        throw new ForyJsonException("Failed to write JSON field", e);
      }
    }

    @Override
    public void putShort(Object target, short value) {
      try {
        UnsafeAccess.PUT_SHORT.invokeExact(UnsafeAccess.UNSAFE, target, offset, value);
      } catch (Throwable e) {
        throw new ForyJsonException("Failed to write JSON field", e);
      }
    }

    @Override
    public void putInt(Object target, int value) {
      try {
        UnsafeAccess.PUT_INT.invokeExact(UnsafeAccess.UNSAFE, target, offset, value);
      } catch (Throwable e) {
        throw new ForyJsonException("Failed to write JSON field", e);
      }
    }

    @Override
    public void putLong(Object target, long value) {
      try {
        UnsafeAccess.PUT_LONG.invokeExact(UnsafeAccess.UNSAFE, target, offset, value);
      } catch (Throwable e) {
        throw new ForyJsonException("Failed to write JSON field", e);
      }
    }

    @Override
    public void putFloat(Object target, float value) {
      try {
        UnsafeAccess.PUT_FLOAT.invokeExact(UnsafeAccess.UNSAFE, target, offset, value);
      } catch (Throwable e) {
        throw new ForyJsonException("Failed to write JSON field", e);
      }
    }

    @Override
    public void putDouble(Object target, double value) {
      try {
        UnsafeAccess.PUT_DOUBLE.invokeExact(UnsafeAccess.UNSAFE, target, offset, value);
      } catch (Throwable e) {
        throw new ForyJsonException("Failed to write JSON field", e);
      }
    }

    @Override
    public void putChar(Object target, char value) {
      try {
        UnsafeAccess.PUT_CHAR.invokeExact(UnsafeAccess.UNSAFE, target, offset, value);
      } catch (Throwable e) {
        throw new ForyJsonException("Failed to write JSON field", e);
      }
    }
  }

  private static final class UnsafeAccess {
    private static final Object UNSAFE = unsafe();
    private static final MethodHandle OBJECT_FIELD_OFFSET =
        method("objectFieldOffset", long.class, Field.class);
    private static final MethodHandle GET_OBJECT =
        method("getObject", Object.class, Object.class, long.class);
    private static final MethodHandle GET_BOOLEAN =
        method("getBoolean", boolean.class, Object.class, long.class);
    private static final MethodHandle GET_BYTE =
        method("getByte", byte.class, Object.class, long.class);
    private static final MethodHandle GET_SHORT =
        method("getShort", short.class, Object.class, long.class);
    private static final MethodHandle GET_INT =
        method("getInt", int.class, Object.class, long.class);
    private static final MethodHandle GET_LONG =
        method("getLong", long.class, Object.class, long.class);
    private static final MethodHandle GET_FLOAT =
        method("getFloat", float.class, Object.class, long.class);
    private static final MethodHandle GET_DOUBLE =
        method("getDouble", double.class, Object.class, long.class);
    private static final MethodHandle GET_CHAR =
        method("getChar", char.class, Object.class, long.class);
    private static final MethodHandle PUT_OBJECT =
        method("putObject", void.class, Object.class, long.class, Object.class);
    private static final MethodHandle PUT_BOOLEAN =
        method("putBoolean", void.class, Object.class, long.class, boolean.class);
    private static final MethodHandle PUT_BYTE =
        method("putByte", void.class, Object.class, long.class, byte.class);
    private static final MethodHandle PUT_SHORT =
        method("putShort", void.class, Object.class, long.class, short.class);
    private static final MethodHandle PUT_INT =
        method("putInt", void.class, Object.class, long.class, int.class);
    private static final MethodHandle PUT_LONG =
        method("putLong", void.class, Object.class, long.class, long.class);
    private static final MethodHandle PUT_FLOAT =
        method("putFloat", void.class, Object.class, long.class, float.class);
    private static final MethodHandle PUT_DOUBLE =
        method("putDouble", void.class, Object.class, long.class, double.class);
    private static final MethodHandle PUT_CHAR =
        method("putChar", void.class, Object.class, long.class, char.class);

    private UnsafeAccess() {}

    private static Object unsafe() {
      try {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field field = unsafeClass.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return field.get(null);
      } catch (ReflectiveOperationException | RuntimeException e) {
        return null;
      }
    }

    private static MethodHandle method(String name, Class<?> returnType, Class<?>... params) {
      if (UNSAFE == null) {
        return null;
      }
      try {
        Class<?> unsafeClass = UNSAFE.getClass();
        Class<?>[] lookupParams = params;
        MethodHandle handle =
            MethodHandles.publicLookup().unreflect(unsafeClass.getMethod(name, lookupParams));
        Class<?>[] erasedParams = new Class<?>[params.length + 1];
        erasedParams[0] = Object.class;
        System.arraycopy(params, 0, erasedParams, 1, params.length);
        return handle.asType(MethodType.methodType(returnType, erasedParams));
      } catch (ReflectiveOperationException | RuntimeException e) {
        return null;
      }
    }
  }

  private static final class MethodMemberAccessor extends JsonMemberAccessor {
    private final MethodHandle handle;

    private MethodMemberAccessor(MethodHandle handle) {
      this.handle = handle;
    }

    @Override
    public Object getObject(Object target) {
      try {
        return handle.invoke(target);
      } catch (Throwable e) {
        throw new ForyJsonException("Failed to invoke JSON getter", e);
      }
    }

    @Override
    public void putObject(Object target, Object value) {
      try {
        handle.invoke(target, value);
      } catch (Throwable e) {
        throw new ForyJsonException("Failed to invoke JSON setter", e);
      }
    }
  }
}
