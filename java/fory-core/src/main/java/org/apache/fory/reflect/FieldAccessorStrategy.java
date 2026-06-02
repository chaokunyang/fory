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

package org.apache.fory.reflect;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.apache.fory.collection.ClassValueCache;
import org.apache.fory.collection.Tuple2;
import org.apache.fory.platform.AndroidSupport;
import org.apache.fory.platform.GraalvmSupport;
import org.apache.fory.platform.internal._JDKAccess;
import org.apache.fory.platform.internal._UnsafeUtils;
import org.apache.fory.type.TypeUtils;
import org.apache.fory.util.Preconditions;
import sun.misc.Unsafe;

final class FieldAccessorStrategy {
  private static final Unsafe UNSAFE = AndroidSupport.IS_ANDROID ? null : _UnsafeUtils.UNSAFE;

  private static final int BOOLEAN_ACCESS = 1;
  private static final int BYTE_ACCESS = 2;
  private static final int CHAR_ACCESS = 3;
  private static final int SHORT_ACCESS = 4;
  private static final int INT_ACCESS = 5;
  private static final int LONG_ACCESS = 6;
  private static final int FLOAT_ACCESS = 7;
  private static final int DOUBLE_ACCESS = 8;
  private static final int OBJECT_ACCESS = 9;

  private FieldAccessorStrategy() {}

  private static long fieldOffset(Field field) {
    if (AndroidSupport.IS_ANDROID) {
      return -1;
    }
    if (GraalvmSupport.isGraalBuildTime()) {
      // Field offsets are rewritten by GraalVM and are not stable during native-image build time.
      return -1;
    }
    return UNSAFE.objectFieldOffset(field);
  }

  private static int accessKind(Field field) {
    Class<?> fieldType = field.getType();
    if (fieldType == boolean.class) {
      return BOOLEAN_ACCESS;
    } else if (fieldType == byte.class) {
      return BYTE_ACCESS;
    } else if (fieldType == char.class) {
      return CHAR_ACCESS;
    } else if (fieldType == short.class) {
      return SHORT_ACCESS;
    } else if (fieldType == int.class) {
      return INT_ACCESS;
    } else if (fieldType == long.class) {
      return LONG_ACCESS;
    } else if (fieldType == float.class) {
      return FLOAT_ACCESS;
    } else if (fieldType == double.class) {
      return DOUBLE_ACCESS;
    }
    return OBJECT_ACCESS;
  }

  private static void copyField(
      long fieldOffset,
      int accessKind,
      Object sourceObject,
      Object targetObject,
      FieldAccessor accessor) {
    switch (accessKind) {
      case BOOLEAN_ACCESS:
        UNSAFE.putBoolean(targetObject, fieldOffset, UNSAFE.getBoolean(sourceObject, fieldOffset));
        return;
      case BYTE_ACCESS:
        UNSAFE.putByte(targetObject, fieldOffset, UNSAFE.getByte(sourceObject, fieldOffset));
        return;
      case CHAR_ACCESS:
        UNSAFE.putChar(targetObject, fieldOffset, UNSAFE.getChar(sourceObject, fieldOffset));
        return;
      case SHORT_ACCESS:
        UNSAFE.putShort(targetObject, fieldOffset, UNSAFE.getShort(sourceObject, fieldOffset));
        return;
      case INT_ACCESS:
        UNSAFE.putInt(targetObject, fieldOffset, UNSAFE.getInt(sourceObject, fieldOffset));
        return;
      case LONG_ACCESS:
        UNSAFE.putLong(targetObject, fieldOffset, UNSAFE.getLong(sourceObject, fieldOffset));
        return;
      case FLOAT_ACCESS:
        UNSAFE.putFloat(targetObject, fieldOffset, UNSAFE.getFloat(sourceObject, fieldOffset));
        return;
      case DOUBLE_ACCESS:
        UNSAFE.putDouble(targetObject, fieldOffset, UNSAFE.getDouble(sourceObject, fieldOffset));
        return;
      case OBJECT_ACCESS:
        UNSAFE.putObject(targetObject, fieldOffset, UNSAFE.getObject(sourceObject, fieldOffset));
        return;
      default:
        accessor.putObject(targetObject, accessor.getObject(sourceObject));
    }
  }

  private static void copyObjectField(
      long fieldOffset,
      int accessKind,
      Object sourceObject,
      Object targetObject,
      FieldAccessor accessor) {
    if (accessKind == OBJECT_ACCESS) {
      UNSAFE.putObject(targetObject, fieldOffset, UNSAFE.getObject(sourceObject, fieldOffset));
    } else {
      accessor.putObject(targetObject, accessor.getObject(sourceObject));
    }
  }

  private abstract static class InstanceAccessor extends FieldAccessor {
    protected final long fieldOffset;
    private final int accessKind;

    InstanceAccessor(Field field) {
      super(field);
      fieldOffset = fieldOffset(field);
      accessKind = accessKind(field);
    }

    @Override
    public void copy(Object sourceObject, Object targetObject) {
      copyField(fieldOffset, accessKind, sourceObject, targetObject, this);
    }

    @Override
    public void copyObject(Object sourceObject, Object targetObject) {
      copyObjectField(fieldOffset, accessKind, sourceObject, targetObject, this);
    }
  }

  static FieldAccessor createAccessor(Field field) {
    Preconditions.checkArgument(!Modifier.isStatic(field.getModifiers()), field);
    if (GraalvmSupport.isGraalBuildTime()) {
      return new GeneratedAccessor(field);
    }
    if (field.getType() == boolean.class) {
      return new BooleanAccessor(field);
    } else if (field.getType() == byte.class) {
      return new ByteAccessor(field);
    } else if (field.getType() == char.class) {
      return new CharAccessor(field);
    } else if (field.getType() == short.class) {
      return new ShortAccessor(field);
    } else if (field.getType() == int.class) {
      return new IntAccessor(field);
    } else if (field.getType() == long.class) {
      return new LongAccessor(field);
    } else if (field.getType() == float.class) {
      return new FloatAccessor(field);
    } else if (field.getType() == double.class) {
      return new DoubleAccessor(field);
    } else {
      return new ObjectAccessor(field);
    }
  }

  /** Primitive boolean accessor. */
  public static class BooleanAccessor extends InstanceAccessor {
    public BooleanAccessor(Field field) {
      super(field);
      Preconditions.checkArgument(field.getType() == boolean.class);
    }

    @Override
    public Object get(Object obj) {
      return getBoolean(obj);
    }

    @Override
    public boolean getBoolean(Object obj) {
      checkObj(obj);
      return UNSAFE.getBoolean(obj, fieldOffset);
    }

    @Override
    public void set(Object obj, Object value) {
      putBoolean(obj, (Boolean) value);
    }

    @Override
    public void putBoolean(Object obj, boolean value) {
      checkObj(obj);
      UNSAFE.putBoolean(obj, fieldOffset, value);
    }
  }

  /** Primitive byte accessor. */
  public static class ByteAccessor extends InstanceAccessor {
    public ByteAccessor(Field field) {
      super(field);
      Preconditions.checkArgument(field.getType() == byte.class);
    }

    @Override
    public Byte get(Object obj) {
      return getByte(obj);
    }

    @Override
    public byte getByte(Object obj) {
      checkObj(obj);
      return UNSAFE.getByte(obj, fieldOffset);
    }

    @Override
    public void set(Object obj, Object value) {
      putByte(obj, (Byte) value);
    }

    @Override
    public void putByte(Object obj, byte value) {
      checkObj(obj);
      UNSAFE.putByte(obj, fieldOffset, value);
    }
  }

  /** Primitive char accessor. */
  public static class CharAccessor extends InstanceAccessor {
    public CharAccessor(Field field) {
      super(field);
      Preconditions.checkArgument(field.getType() == char.class);
    }

    @Override
    public Character get(Object obj) {
      return getChar(obj);
    }

    @Override
    public char getChar(Object obj) {
      checkObj(obj);
      return UNSAFE.getChar(obj, fieldOffset);
    }

    @Override
    public void set(Object obj, Object value) {
      putChar(obj, (Character) value);
    }

    @Override
    public void putChar(Object obj, char value) {
      checkObj(obj);
      UNSAFE.putChar(obj, fieldOffset, value);
    }
  }

  /** Primitive short accessor. */
  public static class ShortAccessor extends InstanceAccessor {
    public ShortAccessor(Field field) {
      super(field);
      Preconditions.checkArgument(field.getType() == short.class);
    }

    @Override
    public Short get(Object obj) {
      return getShort(obj);
    }

    @Override
    public short getShort(Object obj) {
      checkObj(obj);
      return UNSAFE.getShort(obj, fieldOffset);
    }

    @Override
    public void set(Object obj, Object value) {
      putShort(obj, (Short) value);
    }

    @Override
    public void putShort(Object obj, short value) {
      checkObj(obj);
      UNSAFE.putShort(obj, fieldOffset, value);
    }
  }

  /** Primitive int accessor. */
  public static class IntAccessor extends InstanceAccessor {
    public IntAccessor(Field field) {
      super(field);
      Preconditions.checkArgument(field.getType() == int.class);
    }

    @Override
    public Integer get(Object obj) {
      return getInt(obj);
    }

    @Override
    public int getInt(Object obj) {
      checkObj(obj);
      return UNSAFE.getInt(obj, fieldOffset);
    }

    @Override
    public void set(Object obj, Object value) {
      putInt(obj, (Integer) value);
    }

    @Override
    public void putInt(Object obj, int value) {
      checkObj(obj);
      UNSAFE.putInt(obj, fieldOffset, value);
    }
  }

  /** Primitive long accessor. */
  public static class LongAccessor extends InstanceAccessor {
    public LongAccessor(Field field) {
      super(field);
      Preconditions.checkArgument(field.getType() == long.class);
    }

    @Override
    public Long get(Object obj) {
      return getLong(obj);
    }

    @Override
    public long getLong(Object obj) {
      checkObj(obj);
      return UNSAFE.getLong(obj, fieldOffset);
    }

    @Override
    public void set(Object obj, Object value) {
      putLong(obj, (Long) value);
    }

    @Override
    public void putLong(Object obj, long value) {
      checkObj(obj);
      UNSAFE.putLong(obj, fieldOffset, value);
    }
  }

  /** Primitive float accessor. */
  public static class FloatAccessor extends InstanceAccessor {
    public FloatAccessor(Field field) {
      super(field);
      Preconditions.checkArgument(field.getType() == float.class);
    }

    @Override
    public Object get(Object obj) {
      return getFloat(obj);
    }

    @Override
    public float getFloat(Object obj) {
      checkObj(obj);
      return UNSAFE.getFloat(obj, fieldOffset);
    }

    @Override
    public void set(Object obj, Object value) {
      putFloat(obj, (Float) value);
    }

    @Override
    public void putFloat(Object obj, float value) {
      checkObj(obj);
      UNSAFE.putFloat(obj, fieldOffset, value);
    }
  }

  /** Primitive double accessor. */
  public static class DoubleAccessor extends InstanceAccessor {
    public DoubleAccessor(Field field) {
      super(field);
      Preconditions.checkArgument(field.getType() == double.class);
    }

    @Override
    public Object get(Object obj) {
      return getDouble(obj);
    }

    @Override
    public double getDouble(Object obj) {
      checkObj(obj);
      return UNSAFE.getDouble(obj, fieldOffset);
    }

    @Override
    public void set(Object obj, Object value) {
      putDouble(obj, (Double) value);
    }

    @Override
    public void putDouble(Object obj, double value) {
      checkObj(obj);
      UNSAFE.putDouble(obj, fieldOffset, value);
    }
  }

  /** Object accessor. */
  public static class ObjectAccessor extends InstanceAccessor {
    public ObjectAccessor(Field field) {
      super(field);
      Preconditions.checkArgument(!TypeUtils.isPrimitive(field.getType()));
    }

    @Override
    public Object get(Object obj) {
      checkObj(obj);
      return UNSAFE.getObject(obj, fieldOffset);
    }

    @Override
    public void set(Object obj, Object value) {
      checkObj(obj);
      UNSAFE.putObject(obj, fieldOffset, value);
    }
  }

  static final class GeneratedAccessor extends FieldAccessor {
    private static final ClassValueCache<ConcurrentMap<String, Tuple2<MethodHandle, MethodHandle>>>
        cache = ClassValueCache.newClassKeyCache(8);

    private final MethodHandle getter;
    private final MethodHandle setter;

    GeneratedAccessor(Field field) {
      super(field);
      ConcurrentMap<String, Tuple2<MethodHandle, MethodHandle>> map =
          cache.get(field.getDeclaringClass(), ConcurrentHashMap::new);
      MethodHandles.Lookup lookup = _JDKAccess._trustedLookup(field.getDeclaringClass());
      Tuple2<MethodHandle, MethodHandle> tuple2 =
          map.computeIfAbsent(
              field.getName(),
              k -> {
                try {
                  MethodHandle getter =
                      lookup.findGetter(
                          field.getDeclaringClass(), field.getName(), field.getType());
                  MethodHandle setter =
                      lookup.findSetter(
                          field.getDeclaringClass(), field.getName(), field.getType());
                  return Tuple2.of(getter, setter);
                } catch (IllegalAccessException | NoSuchFieldException ex) {
                  throw new RuntimeException(ex);
                }
              });
      getter = tuple2.f0;
      setter = tuple2.f1;
    }

    @Override
    public Object get(Object obj) {
      try {
        return getter.invoke(obj);
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void set(Object obj, Object value) {
      try {
        setter.invoke(obj, value);
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public boolean getBoolean(Object obj) {
      try {
        return (boolean) getter.invoke(obj);
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void putBoolean(Object obj, boolean value) {
      try {
        setter.invoke(obj, value);
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public byte getByte(Object obj) {
      try {
        return (byte) getter.invoke(obj);
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void putByte(Object obj, byte value) {
      try {
        setter.invoke(obj, value);
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public char getChar(Object obj) {
      try {
        return (char) getter.invoke(obj);
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void putChar(Object obj, char value) {
      try {
        setter.invoke(obj, value);
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public short getShort(Object obj) {
      try {
        return (short) getter.invoke(obj);
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void putShort(Object obj, short value) {
      try {
        setter.invoke(obj, value);
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public int getInt(Object obj) {
      try {
        return (int) getter.invoke(obj);
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void putInt(Object obj, int value) {
      try {
        setter.invoke(obj, value);
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public long getLong(Object obj) {
      try {
        return (long) getter.invoke(obj);
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void putLong(Object obj, long value) {
      try {
        setter.invoke(obj, value);
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public float getFloat(Object obj) {
      try {
        return (float) getter.invoke(obj);
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void putFloat(Object obj, float value) {
      try {
        setter.invoke(obj, value);
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public double getDouble(Object obj) {
      try {
        return (double) getter.invoke(obj);
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void putDouble(Object obj, double value) {
      try {
        setter.invoke(obj, value);
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
    }
  }
}
