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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import org.apache.fory.collection.ClassValueCache;
import org.apache.fory.collection.Tuple2;
import org.apache.fory.exception.ForyException;
import org.apache.fory.platform.AndroidSupport;
import org.apache.fory.platform.GraalvmSupport;
import org.apache.fory.platform.UnsafeOps;
import org.apache.fory.platform.internal._JDKAccess;
import org.apache.fory.type.TypeUtils;
import org.apache.fory.util.Preconditions;
import org.apache.fory.util.function.Functions;
import org.apache.fory.util.function.ToByteFunction;
import org.apache.fory.util.function.ToCharFunction;
import org.apache.fory.util.function.ToFloatFunction;
import org.apache.fory.util.function.ToShortFunction;
import org.apache.fory.util.record.RecordUtils;

/** Field accessor for primitive types and object types. */
@SuppressWarnings({"unchecked", "rawtypes"})
public abstract class FieldAccessor {
  private static final int REFLECTIVE_ACCESS = 0;
  private static final int BOOLEAN_ACCESS = 1;
  private static final int BYTE_ACCESS = 2;
  private static final int CHAR_ACCESS = 3;
  private static final int SHORT_ACCESS = 4;
  private static final int INT_ACCESS = 5;
  private static final int LONG_ACCESS = 6;
  private static final int FLOAT_ACCESS = 7;
  private static final int DOUBLE_ACCESS = 8;
  private static final int OBJECT_ACCESS = 9;

  protected final Field field;
  protected final long fieldOffset;
  private final int accessKind;

  public FieldAccessor(Field field) {
    this.field = field;
    Preconditions.checkNotNull(field);
    this.fieldOffset = fieldOffset(field);
    this.accessKind = accessKind(field, fieldOffset);
  }

  private static long fieldOffset(Field field) {
    if (AndroidSupport.IS_ANDROID) {
      return -1;
    }
    if (GraalvmSupport.isGraalBuildTime()) {
      // Field offsets are rewritten by GraalVM and are not stable during native-image build time.
      return -1;
    }
    return UnsafeOps.objectFieldOffset(field);
  }

  protected FieldAccessor(Field field, long fieldOffset) {
    this.field = field;
    this.fieldOffset = fieldOffset;
    this.accessKind = accessKind(field, fieldOffset);
  }

  private static int accessKind(Field field, long fieldOffset) {
    if (fieldOffset == -1) {
      return REFLECTIVE_ACCESS;
    }
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

  public abstract Object get(Object obj);

  public void set(Object obj, Object value) {
    throw new UnsupportedOperationException("Unsupported for field " + field);
  }

  public final void copy(Object sourceObject, Object targetObject) {
    switch (accessKind) {
      case BOOLEAN_ACCESS:
        UnsafeOps.putBoolean(
            targetObject, fieldOffset, UnsafeOps.getBoolean(sourceObject, fieldOffset));
        return;
      case BYTE_ACCESS:
        UnsafeOps.putByte(targetObject, fieldOffset, UnsafeOps.getByte(sourceObject, fieldOffset));
        return;
      case CHAR_ACCESS:
        UnsafeOps.putChar(targetObject, fieldOffset, UnsafeOps.getChar(sourceObject, fieldOffset));
        return;
      case SHORT_ACCESS:
        UnsafeOps.putShort(
            targetObject, fieldOffset, UnsafeOps.getShort(sourceObject, fieldOffset));
        return;
      case INT_ACCESS:
        UnsafeOps.putInt(targetObject, fieldOffset, UnsafeOps.getInt(sourceObject, fieldOffset));
        return;
      case LONG_ACCESS:
        UnsafeOps.putLong(targetObject, fieldOffset, UnsafeOps.getLong(sourceObject, fieldOffset));
        return;
      case FLOAT_ACCESS:
        UnsafeOps.putFloat(
            targetObject, fieldOffset, UnsafeOps.getFloat(sourceObject, fieldOffset));
        return;
      case DOUBLE_ACCESS:
        UnsafeOps.putDouble(
            targetObject, fieldOffset, UnsafeOps.getDouble(sourceObject, fieldOffset));
        return;
      case OBJECT_ACCESS:
        UnsafeOps.putObject(
            targetObject, fieldOffset, UnsafeOps.getObject(sourceObject, fieldOffset));
        return;
      default:
        putObject(targetObject, getObject(sourceObject));
    }
  }

  public final void copyObject(Object sourceObject, Object targetObject) {
    if (accessKind == OBJECT_ACCESS) {
      UnsafeOps.putObject(
          targetObject, fieldOffset, UnsafeOps.getObject(sourceObject, fieldOffset));
    } else {
      putObject(targetObject, getObject(sourceObject));
    }
  }

  public Field getField() {
    return field;
  }

  public boolean getBoolean(Object targetObject) {
    return (Boolean) get(targetObject);
  }

  public void putBoolean(Object targetObject, boolean value) {
    set(targetObject, value);
  }

  public byte getByte(Object targetObject) {
    return (Byte) get(targetObject);
  }

  public void putByte(Object targetObject, byte value) {
    set(targetObject, value);
  }

  public char getChar(Object targetObject) {
    return (Character) get(targetObject);
  }

  public void putChar(Object targetObject, char value) {
    set(targetObject, value);
  }

  public short getShort(Object targetObject) {
    return (Short) get(targetObject);
  }

  public void putShort(Object targetObject, short value) {
    set(targetObject, value);
  }

  public int getInt(Object targetObject) {
    return (Integer) get(targetObject);
  }

  public void putInt(Object targetObject, int value) {
    set(targetObject, value);
  }

  public long getLong(Object targetObject) {
    return (Long) get(targetObject);
  }

  public void putLong(Object targetObject, long value) {
    set(targetObject, value);
  }

  public float getFloat(Object targetObject) {
    return (Float) get(targetObject);
  }

  public void putFloat(Object targetObject, float value) {
    set(targetObject, value);
  }

  public double getDouble(Object targetObject) {
    return (Double) get(targetObject);
  }

  public void putDouble(Object targetObject, double value) {
    set(targetObject, value);
  }

  public final void putObject(Object targetObject, Object object) {
    // For primitive fields, we must use set() which calls the correct UnsafeOps.putXxx method.
    // UnsafeOps.putObject writes object references, not primitive values.
    if (fieldOffset != -1 && !field.getType().isPrimitive()) {
      UnsafeOps.putObject(targetObject, fieldOffset, object);
    } else {
      set(targetObject, object);
    }
  }

  public final Object getObject(Object targetObject) {
    // For primitive fields, we must use get() which calls the correct UnsafeOps.getXxx method
    // and returns the boxed value. UnsafeOps.getObject interprets primitive bytes as object
    // refs.
    if (fieldOffset != -1 && !field.getType().isPrimitive()) {
      return UnsafeOps.getObject(targetObject, fieldOffset);
    } else {
      return get(targetObject);
    }
  }

  void checkObj(Object obj) {
    if (!this.field.getDeclaringClass().isAssignableFrom(obj.getClass())) {
      throw new IllegalArgumentException("Illegal class " + obj.getClass());
    }
  }

  @Override
  public String toString() {
    return field.toString();
  }

  public abstract static class FieldGetter extends FieldAccessor {

    private final Object getter;

    protected FieldGetter(Field field, Object getter) {
      super(field, -1);
      this.getter = getter;
    }

    public Object getGetter() {
      return getter;
    }
  }

  public static FieldAccessor createAccessor(Field field) {
    Preconditions.checkArgument(!Modifier.isStatic(field.getModifiers()), field);
    if (RecordUtils.isRecord(field.getDeclaringClass())) {
      if (AndroidSupport.IS_ANDROID || GraalvmSupport.IN_GRAALVM_NATIVE_IMAGE) {
        return new ReflectiveRecordFieldAccessor(field);
      }
      Object getter;
      try {
        Method getterMethod = field.getDeclaringClass().getDeclaredMethod(field.getName());
        getter = Functions.makeGetterFunction(getterMethod);
      } catch (NoSuchMethodException ex) {
        throw new RuntimeException(ex);
      }
      if (getter instanceof Predicate) {
        return new BooleanGetter(field, (Predicate) getter);
      } else if (getter instanceof ToByteFunction) {
        return new ByteGetter(field, (ToByteFunction) getter);
      } else if (getter instanceof ToCharFunction) {
        return new CharGetter(field, (ToCharFunction) getter);
      } else if (getter instanceof ToShortFunction) {
        return new ShortGetter(field, (ToShortFunction) getter);
      } else if (getter instanceof ToIntFunction) {
        return new IntGetter(field, (ToIntFunction) getter);
      } else if (getter instanceof ToLongFunction) {
        return new LongGetter(field, (ToLongFunction) getter);
      } else if (getter instanceof ToFloatFunction) {
        return new FloatGetter(field, (ToFloatFunction) getter);
      } else if (getter instanceof ToDoubleFunction) {
        return new DoubleGetter(field, (ToDoubleFunction) getter);
      } else {
        return new ObjectGetter(field, (Function) getter);
      }
    }
    if (AndroidSupport.IS_ANDROID) {
      // Android field access must stay reflection-owned: no Unsafe offsets, trusted lookups,
      // generated accessors, or primitive-specific reflection subclasses.
      return new ReflectionFieldAccessor(field);
    }
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

  public static FieldAccessor createStaticAccessor(Field field) {
    Preconditions.checkArgument(Modifier.isStatic(field.getModifiers()), field);
    if (AndroidSupport.IS_ANDROID) {
      field.setAccessible(true);
      return new ReflectiveStaticFieldAccessor(field);
    }
    return new StaticObjectAccessor(field);
  }

  static final class ReflectiveRecordFieldAccessor extends FieldGetter {
    private final Method accessor;

    ReflectiveRecordFieldAccessor(Field field) {
      super(field, null);
      try {
        accessor = field.getDeclaringClass().getDeclaredMethod(field.getName());
        accessor.setAccessible(true);
      } catch (NoSuchMethodException | RuntimeException e) {
        throw new ForyException("Failed to create record field accessor for " + field, e);
      }
    }

    @Override
    public Object get(Object obj) {
      checkObj(obj);
      try {
        return accessor.invoke(obj);
      } catch (IllegalAccessException | IllegalArgumentException e) {
        throw new ForyException("Failed to read record field reflectively: " + field, e);
      } catch (InvocationTargetException e) {
        throw new ForyException(
            "Record accessor threw while reading field: " + field, e.getCause());
      }
    }

    @Override
    public void set(Object obj, Object value) {
      throw new UnsupportedOperationException("Record field is read-only: " + field);
    }
  }

  /** Primitive boolean accessor. */
  public static class BooleanAccessor extends FieldAccessor {
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
      return UnsafeOps.getBoolean(obj, fieldOffset);
    }

    @Override
    public void set(Object obj, Object value) {
      putBoolean(obj, (Boolean) value);
    }

    @Override
    public void putBoolean(Object obj, boolean value) {
      checkObj(obj);
      UnsafeOps.putBoolean(obj, fieldOffset, value);
    }
  }

  public static class BooleanGetter extends FieldGetter {
    private final Predicate getter;

    public BooleanGetter(Field field, Predicate getter) {
      super(field, getter);
      this.getter = getter;
      Preconditions.checkArgument(field.getType() == boolean.class);
    }

    @Override
    public Boolean get(Object obj) {
      return getBoolean(obj);
    }

    @Override
    public boolean getBoolean(Object obj) {
      checkObj(obj);
      return getter.test(obj);
    }
  }

  /** Primitive byte accessor. */
  public static class ByteAccessor extends FieldAccessor {
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
      return UnsafeOps.getByte(obj, fieldOffset);
    }

    @Override
    public void set(Object obj, Object value) {
      putByte(obj, (Byte) value);
    }

    @Override
    public void putByte(Object obj, byte value) {
      checkObj(obj);
      UnsafeOps.putByte(obj, fieldOffset, value);
    }
  }

  public static class ByteGetter extends FieldGetter {

    private final ToByteFunction getter;

    public ByteGetter(Field field, ToByteFunction getter) {
      super(field, getter);
      this.getter = getter;
      Preconditions.checkArgument(field.getType() == byte.class);
    }

    @Override
    public Byte get(Object obj) {
      return getByte(obj);
    }

    @Override
    public byte getByte(Object obj) {
      return getter.applyAsByte(obj);
    }
  }

  /** Primitive char accessor. */
  public static class CharAccessor extends FieldAccessor {
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
      return UnsafeOps.getChar(obj, fieldOffset);
    }

    @Override
    public void set(Object obj, Object value) {
      putChar(obj, (Character) value);
    }

    @Override
    public void putChar(Object obj, char value) {
      checkObj(obj);
      UnsafeOps.putChar(obj, fieldOffset, value);
    }
  }

  public static class CharGetter extends FieldGetter {
    private final ToCharFunction getter;

    public CharGetter(Field field, ToCharFunction getter) {
      super(field, getter);
      this.getter = getter;
      Preconditions.checkArgument(field.getType() == char.class);
    }

    @Override
    public Character get(Object obj) {
      return getChar(obj);
    }

    @Override
    public char getChar(Object obj) {
      return getter.applyAsChar(obj);
    }
  }

  /** Primitive short accessor. */
  public static class ShortAccessor extends FieldAccessor {
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
      return UnsafeOps.getShort(obj, fieldOffset);
    }

    @Override
    public void set(Object obj, Object value) {
      putShort(obj, (Short) value);
    }

    @Override
    public void putShort(Object obj, short value) {
      checkObj(obj);
      UnsafeOps.putShort(obj, fieldOffset, value);
    }
  }

  public static class ShortGetter extends FieldGetter {
    private final ToShortFunction getter;

    public ShortGetter(Field field, ToShortFunction getter) {
      super(field, getter);
      this.getter = getter;
      Preconditions.checkArgument(field.getType() == short.class);
    }

    @Override
    public Short get(Object obj) {
      return getShort(obj);
    }

    @Override
    public short getShort(Object obj) {
      return getter.applyAsShort(obj);
    }
  }

  /** Primitive int accessor. */
  public static class IntAccessor extends FieldAccessor {
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
      return UnsafeOps.getInt(obj, fieldOffset);
    }

    @Override
    public void set(Object obj, Object value) {
      putInt(obj, (Integer) value);
    }

    @Override
    public void putInt(Object obj, int value) {
      checkObj(obj);
      UnsafeOps.putInt(obj, fieldOffset, value);
    }
  }

  public static class IntGetter extends FieldGetter {
    private final ToIntFunction getter;

    public IntGetter(Field field, ToIntFunction getter) {
      super(field, getter);
      this.getter = getter;
      Preconditions.checkArgument(field.getType() == int.class);
    }

    @Override
    public Integer get(Object obj) {
      return getInt(obj);
    }

    @Override
    public int getInt(Object obj) {
      return getter.applyAsInt(obj);
    }
  }

  /** Primitive long accessor. */
  public static class LongAccessor extends FieldAccessor {
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
      return UnsafeOps.getLong(obj, fieldOffset);
    }

    @Override
    public void set(Object obj, Object value) {
      putLong(obj, (Long) value);
    }

    @Override
    public void putLong(Object obj, long value) {
      checkObj(obj);
      UnsafeOps.putLong(obj, fieldOffset, value);
    }
  }

  public static class LongGetter extends FieldGetter {
    private final ToLongFunction getter;

    public LongGetter(Field field, ToLongFunction getter) {
      super(field, getter);
      this.getter = getter;
      Preconditions.checkArgument(field.getType() == long.class);
    }

    @Override
    public Long get(Object obj) {
      return getLong(obj);
    }

    @Override
    public long getLong(Object obj) {
      return getter.applyAsLong(obj);
    }
  }

  /** Primitive float accessor. */
  public static class FloatAccessor extends FieldAccessor {
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
      return UnsafeOps.getFloat(obj, fieldOffset);
    }

    @Override
    public void set(Object obj, Object value) {
      putFloat(obj, (Float) value);
    }

    @Override
    public void putFloat(Object obj, float value) {
      checkObj(obj);
      UnsafeOps.putFloat(obj, fieldOffset, value);
    }
  }

  public static class FloatGetter extends FieldGetter {
    private final ToFloatFunction getter;

    public FloatGetter(Field field, ToFloatFunction getter) {
      super(field, getter);
      this.getter = getter;
      Preconditions.checkArgument(field.getType() == float.class);
    }

    @Override
    public Float get(Object obj) {
      return getFloat(obj);
    }

    @Override
    public float getFloat(Object obj) {
      return getter.applyAsFloat(obj);
    }
  }

  /** Primitive double accessor. */
  public static class DoubleAccessor extends FieldAccessor {
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
      return UnsafeOps.getDouble(obj, fieldOffset);
    }

    @Override
    public void set(Object obj, Object value) {
      putDouble(obj, (Double) value);
    }

    @Override
    public void putDouble(Object obj, double value) {
      checkObj(obj);
      UnsafeOps.putDouble(obj, fieldOffset, value);
    }
  }

  public static class DoubleGetter extends FieldGetter {
    private final ToDoubleFunction getter;

    public DoubleGetter(Field field, ToDoubleFunction getter) {
      super(field, getter);
      this.getter = getter;
      Preconditions.checkArgument(field.getType() == double.class);
    }

    @Override
    public Double get(Object obj) {
      return getDouble(obj);
    }

    @Override
    public double getDouble(Object obj) {
      return getter.applyAsDouble(obj);
    }
  }

  /** Object accessor. */
  public static class ObjectAccessor extends FieldAccessor {
    public ObjectAccessor(Field field) {
      super(field);
      Preconditions.checkArgument(!TypeUtils.isPrimitive(field.getType()));
    }

    @Override
    public Object get(Object obj) {
      checkObj(obj);
      return UnsafeOps.getObject(obj, fieldOffset);
    }

    @Override
    public void set(Object obj, Object value) {
      checkObj(obj);
      UnsafeOps.putObject(obj, fieldOffset, value);
    }
  }

  public static class ObjectGetter extends FieldGetter {
    private final Function getter;

    public ObjectGetter(Field field, Function getter) {
      super(field, getter);
      this.getter = getter;
      Preconditions.checkArgument(!field.getType().isPrimitive(), field);
    }

    @Override
    public Object get(Object obj) {
      return getter.apply(obj);
    }
  }

  static final class ReflectiveStaticFieldAccessor extends FieldAccessor {
    ReflectiveStaticFieldAccessor(Field field) {
      super(field, -1);
    }

    @Override
    public Object get(Object obj) {
      try {
        return field.get(null);
      } catch (IllegalAccessException | IllegalArgumentException e) {
        throw new ForyException("Failed to read static field reflectively: " + field, e);
      }
    }

    @Override
    public void set(Object obj, Object value) {
      try {
        field.set(null, value);
      } catch (IllegalAccessException | IllegalArgumentException e) {
        throw new ForyException("Failed to write static field reflectively: " + field, e);
      }
    }
  }

  static final class StaticObjectAccessor extends FieldAccessor {
    private final Object base;
    private final long offset;

    StaticObjectAccessor(Field field) {
      super(field, -1);
      Preconditions.checkArgument(!TypeUtils.isPrimitive(field.getType()));
      base = UnsafeOps.UNSAFE.staticFieldBase(field);
      offset = UnsafeOps.UNSAFE.staticFieldOffset(field);
    }

    @Override
    public Object get(Object obj) {
      return UnsafeOps.getObject(base, offset);
    }

    @Override
    public void set(Object obj, Object value) {
      UnsafeOps.putObject(base, offset, value);
    }
  }

  static final class GeneratedAccessor extends FieldAccessor {
    private static final ClassValueCache<ConcurrentMap<String, Tuple2<MethodHandle, MethodHandle>>>
        cache = ClassValueCache.newClassKeyCache(8);

    private final MethodHandle getter;
    private final MethodHandle setter;

    GeneratedAccessor(Field field) {
      super(field, -1);
      ConcurrentMap<String, Tuple2<MethodHandle, MethodHandle>> map =
          cache.get(field.getDeclaringClass(), ConcurrentHashMap::new);
      ;
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
