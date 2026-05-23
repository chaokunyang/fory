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
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import org.apache.fory.exception.ForyException;
import org.apache.fory.platform.AndroidSupport;
import org.apache.fory.platform.GraalvmSupport;
import org.apache.fory.type.TypeUtils;
import org.apache.fory.util.Preconditions;
import org.apache.fory.util.function.ToByteFunction;
import org.apache.fory.util.function.ToCharFunction;
import org.apache.fory.util.function.ToFloatFunction;
import org.apache.fory.util.function.ToShortFunction;
import org.apache.fory.util.record.RecordUtils;

/** Field accessor for primitive types and object types. */
@SuppressWarnings({"unchecked", "rawtypes"})
public abstract class FieldAccessor {
  protected final Field field;
  protected final long fieldOffset;

  public FieldAccessor(Field field) {
    this(field, -1);
  }

  protected FieldAccessor(Field field, long fieldOffset) {
    this.field = field;
    this.fieldOffset = fieldOffset;
    Preconditions.checkNotNull(field);
  }

  public abstract Object get(Object obj);

  public void set(Object obj, Object value) {
    throw new UnsupportedOperationException("Unsupported for field " + field);
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

  public void putObject(Object targetObject, Object object) {
    set(targetObject, object);
  }

  public Object getObject(Object targetObject) {
    return get(targetObject);
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
      return createRecordAccessor(field);
    }
    if (AndroidSupport.IS_ANDROID) {
      // Android field access must stay reflection-owned: no Unsafe offsets, trusted lookups,
      // generated accessors, or primitive-specific reflection subclasses.
      return new ReflectionFieldAccessor(field);
    }
    if (GraalvmSupport.isGraalBuildTime()) {
      return new GeneratedAccessor(field);
    }
    return createVarHandleAccessor(field);
  }

  public static FieldAccessor createStaticAccessor(Field field) {
    Preconditions.checkArgument(Modifier.isStatic(field.getModifiers()), field);
    if (AndroidSupport.IS_ANDROID) {
      field.setAccessible(true);
      return new ReflectiveStaticFieldAccessor(field);
    }
    return createVarHandleAccessor(field);
  }

  private static FieldAccessor createVarHandleAccessor(Field field) {
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

  private static FieldAccessor createRecordAccessor(Field field) {
    MethodHandle getter = recordGetter(field);
    if (field.getType() == boolean.class) {
      return new BooleanGetter(field, getter);
    } else if (field.getType() == byte.class) {
      return new ByteGetter(field, getter);
    } else if (field.getType() == char.class) {
      return new CharGetter(field, getter);
    } else if (field.getType() == short.class) {
      return new ShortGetter(field, getter);
    } else if (field.getType() == int.class) {
      return new IntGetter(field, getter);
    } else if (field.getType() == long.class) {
      return new LongGetter(field, getter);
    } else if (field.getType() == float.class) {
      return new FloatGetter(field, getter);
    } else if (field.getType() == double.class) {
      return new DoubleGetter(field, getter);
    } else {
      return new ObjectGetter(field, getter);
    }
  }

  private static VarHandle fieldHandle(Field field) {
    MethodHandles.Lookup lookup = privateLookup(field);
    try {
      if (Modifier.isStatic(field.getModifiers())) {
        return lookup.findStaticVarHandle(
            field.getDeclaringClass(), field.getName(), field.getType());
      }
      return lookup.findVarHandle(field.getDeclaringClass(), field.getName(), field.getType());
    } catch (IllegalAccessException e) {
      throw accessFailure(field, e);
    } catch (NoSuchFieldException e) {
      throw new IllegalStateException("Failed to create VarHandle for field " + field, e);
    }
  }

  private static MethodHandle recordGetter(Field field) {
    MethodHandles.Lookup lookup = privateLookup(field);
    try {
      return lookup.findVirtual(
          field.getDeclaringClass(), field.getName(), MethodType.methodType(field.getType()));
    } catch (IllegalAccessException e) {
      throw accessFailure(field, e);
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException("Failed to find record accessor for field " + field, e);
    }
  }

  private static MethodHandles.Lookup privateLookup(Field field) {
    Class<?> declaringClass = field.getDeclaringClass();
    try {
      return MethodHandles.privateLookupIn(declaringClass, MethodHandles.lookup());
    } catch (IllegalAccessException e) {
      throw accessFailure(field, e);
    }
  }

  private static IllegalStateException accessFailure(Field field, Throwable cause) {
    Class<?> declaringClass = field.getDeclaringClass();
    Module targetModule = declaringClass.getModule();
    Package targetPackage = declaringClass.getPackage();
    String packageName = targetPackage == null ? "" : targetPackage.getName();
    return new IllegalStateException(
        "Cannot access field "
            + field
            + " because package "
            + packageName
            + " in module "
            + moduleName(targetModule)
            + " is not open to "
            + moduleName(FieldAccessor.class.getModule()),
        cause);
  }

  private static String moduleName(Module module) {
    String name = module.getName();
    return name == null ? "<unnamed>" : name;
  }

  private static UnsupportedOperationException unsupportedWrite(Field field, Throwable cause) {
    return new UnsupportedOperationException(
        "Field cannot be written through supported JDK access APIs: " + field, cause);
  }

  private static RuntimeException getterFailure(Field field, Throwable cause) {
    return new RuntimeException("Failed to read record field: " + field, cause);
  }

  private static RuntimeException accessorFailure(Field field, Throwable cause) {
    return new RuntimeException("Failed to access field: " + field, cause);
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

  private abstract static class VarHandleAccessor extends FieldAccessor {
    protected final VarHandle handle;
    protected final boolean isStatic;

    VarHandleAccessor(Field field) {
      super(field, -1);
      handle = fieldHandle(field);
      isStatic = Modifier.isStatic(field.getModifiers());
    }
  }

  /** Primitive boolean accessor. */
  public static class BooleanAccessor extends VarHandleAccessor {
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
      if (isStatic) {
        return (boolean) handle.get();
      }
      checkObj(obj);
      return (boolean) handle.get(obj);
    }

    @Override
    public void set(Object obj, Object value) {
      putBoolean(obj, (Boolean) value);
    }

    @Override
    public void putBoolean(Object obj, boolean value) {
      try {
        if (isStatic) {
          handle.set(value);
        } else {
          checkObj(obj);
          handle.set(obj, value);
        }
      } catch (UnsupportedOperationException e) {
        throw unsupportedWrite(field, e);
      }
    }
  }

  public static class BooleanGetter extends FieldGetter {
    private final Predicate getter;
    private final MethodHandle getterHandle;

    public BooleanGetter(Field field, Predicate getter) {
      super(field, getter);
      this.getter = getter;
      getterHandle = null;
      Preconditions.checkArgument(field.getType() == boolean.class);
    }

    private BooleanGetter(Field field, MethodHandle getter) {
      super(field, getter);
      this.getter = null;
      getterHandle = getter;
      Preconditions.checkArgument(field.getType() == boolean.class);
    }

    @Override
    public Boolean get(Object obj) {
      return getBoolean(obj);
    }

    @Override
    public boolean getBoolean(Object obj) {
      checkObj(obj);
      if (getterHandle == null) {
        return getter.test(obj);
      }
      try {
        return (boolean) getterHandle.invoke(obj);
      } catch (Throwable e) {
        throw getterFailure(field, e);
      }
    }
  }

  /** Primitive byte accessor. */
  public static class ByteAccessor extends VarHandleAccessor {
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
      if (isStatic) {
        return (byte) handle.get();
      }
      checkObj(obj);
      return (byte) handle.get(obj);
    }

    @Override
    public void set(Object obj, Object value) {
      putByte(obj, (Byte) value);
    }

    @Override
    public void putByte(Object obj, byte value) {
      try {
        if (isStatic) {
          handle.set(value);
        } else {
          checkObj(obj);
          handle.set(obj, value);
        }
      } catch (UnsupportedOperationException e) {
        throw unsupportedWrite(field, e);
      }
    }
  }

  public static class ByteGetter extends FieldGetter {

    private final ToByteFunction getter;
    private final MethodHandle getterHandle;

    public ByteGetter(Field field, ToByteFunction getter) {
      super(field, getter);
      this.getter = getter;
      getterHandle = null;
      Preconditions.checkArgument(field.getType() == byte.class);
    }

    private ByteGetter(Field field, MethodHandle getter) {
      super(field, getter);
      this.getter = null;
      getterHandle = getter;
      Preconditions.checkArgument(field.getType() == byte.class);
    }

    @Override
    public Byte get(Object obj) {
      return getByte(obj);
    }

    @Override
    public byte getByte(Object obj) {
      checkObj(obj);
      if (getterHandle == null) {
        return getter.applyAsByte(obj);
      }
      try {
        return (byte) getterHandle.invoke(obj);
      } catch (Throwable e) {
        throw getterFailure(field, e);
      }
    }
  }

  /** Primitive char accessor. */
  public static class CharAccessor extends VarHandleAccessor {
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
      if (isStatic) {
        return (char) handle.get();
      }
      checkObj(obj);
      return (char) handle.get(obj);
    }

    @Override
    public void set(Object obj, Object value) {
      putChar(obj, (Character) value);
    }

    @Override
    public void putChar(Object obj, char value) {
      try {
        if (isStatic) {
          handle.set(value);
        } else {
          checkObj(obj);
          handle.set(obj, value);
        }
      } catch (UnsupportedOperationException e) {
        throw unsupportedWrite(field, e);
      }
    }
  }

  public static class CharGetter extends FieldGetter {
    private final ToCharFunction getter;
    private final MethodHandle getterHandle;

    public CharGetter(Field field, ToCharFunction getter) {
      super(field, getter);
      this.getter = getter;
      getterHandle = null;
      Preconditions.checkArgument(field.getType() == char.class);
    }

    private CharGetter(Field field, MethodHandle getter) {
      super(field, getter);
      this.getter = null;
      getterHandle = getter;
      Preconditions.checkArgument(field.getType() == char.class);
    }

    @Override
    public Character get(Object obj) {
      return getChar(obj);
    }

    @Override
    public char getChar(Object obj) {
      checkObj(obj);
      if (getterHandle == null) {
        return getter.applyAsChar(obj);
      }
      try {
        return (char) getterHandle.invoke(obj);
      } catch (Throwable e) {
        throw getterFailure(field, e);
      }
    }
  }

  /** Primitive short accessor. */
  public static class ShortAccessor extends VarHandleAccessor {
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
      if (isStatic) {
        return (short) handle.get();
      }
      checkObj(obj);
      return (short) handle.get(obj);
    }

    @Override
    public void set(Object obj, Object value) {
      putShort(obj, (Short) value);
    }

    @Override
    public void putShort(Object obj, short value) {
      try {
        if (isStatic) {
          handle.set(value);
        } else {
          checkObj(obj);
          handle.set(obj, value);
        }
      } catch (UnsupportedOperationException e) {
        throw unsupportedWrite(field, e);
      }
    }
  }

  public static class ShortGetter extends FieldGetter {
    private final ToShortFunction getter;
    private final MethodHandle getterHandle;

    public ShortGetter(Field field, ToShortFunction getter) {
      super(field, getter);
      this.getter = getter;
      getterHandle = null;
      Preconditions.checkArgument(field.getType() == short.class);
    }

    private ShortGetter(Field field, MethodHandle getter) {
      super(field, getter);
      this.getter = null;
      getterHandle = getter;
      Preconditions.checkArgument(field.getType() == short.class);
    }

    @Override
    public Short get(Object obj) {
      return getShort(obj);
    }

    @Override
    public short getShort(Object obj) {
      checkObj(obj);
      if (getterHandle == null) {
        return getter.applyAsShort(obj);
      }
      try {
        return (short) getterHandle.invoke(obj);
      } catch (Throwable e) {
        throw getterFailure(field, e);
      }
    }
  }

  /** Primitive int accessor. */
  public static class IntAccessor extends VarHandleAccessor {
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
      if (isStatic) {
        return (int) handle.get();
      }
      checkObj(obj);
      return (int) handle.get(obj);
    }

    @Override
    public void set(Object obj, Object value) {
      putInt(obj, (Integer) value);
    }

    @Override
    public void putInt(Object obj, int value) {
      try {
        if (isStatic) {
          handle.set(value);
        } else {
          checkObj(obj);
          handle.set(obj, value);
        }
      } catch (UnsupportedOperationException e) {
        throw unsupportedWrite(field, e);
      }
    }
  }

  public static class IntGetter extends FieldGetter {
    private final ToIntFunction getter;
    private final MethodHandle getterHandle;

    public IntGetter(Field field, ToIntFunction getter) {
      super(field, getter);
      this.getter = getter;
      getterHandle = null;
      Preconditions.checkArgument(field.getType() == int.class);
    }

    private IntGetter(Field field, MethodHandle getter) {
      super(field, getter);
      this.getter = null;
      getterHandle = getter;
      Preconditions.checkArgument(field.getType() == int.class);
    }

    @Override
    public Integer get(Object obj) {
      return getInt(obj);
    }

    @Override
    public int getInt(Object obj) {
      checkObj(obj);
      if (getterHandle == null) {
        return getter.applyAsInt(obj);
      }
      try {
        return (int) getterHandle.invoke(obj);
      } catch (Throwable e) {
        throw getterFailure(field, e);
      }
    }
  }

  /** Primitive long accessor. */
  public static class LongAccessor extends VarHandleAccessor {
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
      if (isStatic) {
        return (long) handle.get();
      }
      checkObj(obj);
      return (long) handle.get(obj);
    }

    @Override
    public void set(Object obj, Object value) {
      putLong(obj, (Long) value);
    }

    @Override
    public void putLong(Object obj, long value) {
      try {
        if (isStatic) {
          handle.set(value);
        } else {
          checkObj(obj);
          handle.set(obj, value);
        }
      } catch (UnsupportedOperationException e) {
        throw unsupportedWrite(field, e);
      }
    }
  }

  public static class LongGetter extends FieldGetter {
    private final ToLongFunction getter;
    private final MethodHandle getterHandle;

    public LongGetter(Field field, ToLongFunction getter) {
      super(field, getter);
      this.getter = getter;
      getterHandle = null;
      Preconditions.checkArgument(field.getType() == long.class);
    }

    private LongGetter(Field field, MethodHandle getter) {
      super(field, getter);
      this.getter = null;
      getterHandle = getter;
      Preconditions.checkArgument(field.getType() == long.class);
    }

    @Override
    public Long get(Object obj) {
      return getLong(obj);
    }

    @Override
    public long getLong(Object obj) {
      checkObj(obj);
      if (getterHandle == null) {
        return getter.applyAsLong(obj);
      }
      try {
        return (long) getterHandle.invoke(obj);
      } catch (Throwable e) {
        throw getterFailure(field, e);
      }
    }
  }

  /** Primitive float accessor. */
  public static class FloatAccessor extends VarHandleAccessor {
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
      if (isStatic) {
        return (float) handle.get();
      }
      checkObj(obj);
      return (float) handle.get(obj);
    }

    @Override
    public void set(Object obj, Object value) {
      putFloat(obj, (Float) value);
    }

    @Override
    public void putFloat(Object obj, float value) {
      try {
        if (isStatic) {
          handle.set(value);
        } else {
          checkObj(obj);
          handle.set(obj, value);
        }
      } catch (UnsupportedOperationException e) {
        throw unsupportedWrite(field, e);
      }
    }
  }

  public static class FloatGetter extends FieldGetter {
    private final ToFloatFunction getter;
    private final MethodHandle getterHandle;

    public FloatGetter(Field field, ToFloatFunction getter) {
      super(field, getter);
      this.getter = getter;
      getterHandle = null;
      Preconditions.checkArgument(field.getType() == float.class);
    }

    private FloatGetter(Field field, MethodHandle getter) {
      super(field, getter);
      this.getter = null;
      getterHandle = getter;
      Preconditions.checkArgument(field.getType() == float.class);
    }

    @Override
    public Float get(Object obj) {
      return getFloat(obj);
    }

    @Override
    public float getFloat(Object obj) {
      checkObj(obj);
      if (getterHandle == null) {
        return getter.applyAsFloat(obj);
      }
      try {
        return (float) getterHandle.invoke(obj);
      } catch (Throwable e) {
        throw getterFailure(field, e);
      }
    }
  }

  /** Primitive double accessor. */
  public static class DoubleAccessor extends VarHandleAccessor {
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
      if (isStatic) {
        return (double) handle.get();
      }
      checkObj(obj);
      return (double) handle.get(obj);
    }

    @Override
    public void set(Object obj, Object value) {
      putDouble(obj, (Double) value);
    }

    @Override
    public void putDouble(Object obj, double value) {
      try {
        if (isStatic) {
          handle.set(value);
        } else {
          checkObj(obj);
          handle.set(obj, value);
        }
      } catch (UnsupportedOperationException e) {
        throw unsupportedWrite(field, e);
      }
    }
  }

  public static class DoubleGetter extends FieldGetter {
    private final ToDoubleFunction getter;
    private final MethodHandle getterHandle;

    public DoubleGetter(Field field, ToDoubleFunction getter) {
      super(field, getter);
      this.getter = getter;
      getterHandle = null;
      Preconditions.checkArgument(field.getType() == double.class);
    }

    private DoubleGetter(Field field, MethodHandle getter) {
      super(field, getter);
      this.getter = null;
      getterHandle = getter;
      Preconditions.checkArgument(field.getType() == double.class);
    }

    @Override
    public Double get(Object obj) {
      return getDouble(obj);
    }

    @Override
    public double getDouble(Object obj) {
      checkObj(obj);
      if (getterHandle == null) {
        return getter.applyAsDouble(obj);
      }
      try {
        return (double) getterHandle.invoke(obj);
      } catch (Throwable e) {
        throw getterFailure(field, e);
      }
    }
  }

  /** Object accessor. */
  public static class ObjectAccessor extends VarHandleAccessor {
    public ObjectAccessor(Field field) {
      super(field);
      Preconditions.checkArgument(!TypeUtils.isPrimitive(field.getType()));
    }

    @Override
    public Object get(Object obj) {
      if (isStatic) {
        return handle.get();
      }
      checkObj(obj);
      return handle.get(obj);
    }

    @Override
    public void set(Object obj, Object value) {
      try {
        if (isStatic) {
          handle.set(value);
        } else {
          checkObj(obj);
          handle.set(obj, value);
        }
      } catch (UnsupportedOperationException e) {
        throw unsupportedWrite(field, e);
      }
    }
  }

  public static class ObjectGetter extends FieldGetter {
    private final Function getter;
    private final MethodHandle getterHandle;

    public ObjectGetter(Field field, Function getter) {
      super(field, getter);
      this.getter = getter;
      getterHandle = null;
      Preconditions.checkArgument(!field.getType().isPrimitive(), field);
    }

    private ObjectGetter(Field field, MethodHandle getter) {
      super(field, getter);
      this.getter = null;
      getterHandle = getter;
      Preconditions.checkArgument(!field.getType().isPrimitive(), field);
    }

    @Override
    public Object get(Object obj) {
      checkObj(obj);
      if (getterHandle == null) {
        return getter.apply(obj);
      }
      try {
        return getterHandle.invoke(obj);
      } catch (Throwable e) {
        throw getterFailure(field, e);
      }
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

  static final class StaticObjectAccessor extends ObjectAccessor {
    StaticObjectAccessor(Field field) {
      super(field);
      Preconditions.checkArgument(Modifier.isStatic(field.getModifiers()), field);
    }
  }

  static final class GeneratedAccessor extends VarHandleAccessor {
    GeneratedAccessor(Field field) {
      super(field);
    }

    @Override
    public Object get(Object obj) {
      try {
        if (isStatic) {
          return handle.get();
        }
        checkObj(obj);
        return handle.get(obj);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
    }

    @Override
    public void set(Object obj, Object value) {
      try {
        if (isStatic) {
          handle.set(value);
        } else {
          checkObj(obj);
          handle.set(obj, value);
        }
      } catch (UnsupportedOperationException e) {
        throw unsupportedWrite(field, e);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
    }

    @Override
    public boolean getBoolean(Object obj) {
      try {
        if (isStatic) {
          return (boolean) handle.get();
        }
        checkObj(obj);
        return (boolean) handle.get(obj);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
    }

    @Override
    public void putBoolean(Object obj, boolean value) {
      try {
        if (isStatic) {
          handle.set(value);
        } else {
          checkObj(obj);
          handle.set(obj, value);
        }
      } catch (UnsupportedOperationException e) {
        throw unsupportedWrite(field, e);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
    }

    @Override
    public byte getByte(Object obj) {
      try {
        if (isStatic) {
          return (byte) handle.get();
        }
        checkObj(obj);
        return (byte) handle.get(obj);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
    }

    @Override
    public void putByte(Object obj, byte value) {
      try {
        if (isStatic) {
          handle.set(value);
        } else {
          checkObj(obj);
          handle.set(obj, value);
        }
      } catch (UnsupportedOperationException e) {
        throw unsupportedWrite(field, e);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
    }

    @Override
    public char getChar(Object obj) {
      try {
        if (isStatic) {
          return (char) handle.get();
        }
        checkObj(obj);
        return (char) handle.get(obj);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
    }

    @Override
    public void putChar(Object obj, char value) {
      try {
        if (isStatic) {
          handle.set(value);
        } else {
          checkObj(obj);
          handle.set(obj, value);
        }
      } catch (UnsupportedOperationException e) {
        throw unsupportedWrite(field, e);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
    }

    @Override
    public short getShort(Object obj) {
      try {
        if (isStatic) {
          return (short) handle.get();
        }
        checkObj(obj);
        return (short) handle.get(obj);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
    }

    @Override
    public void putShort(Object obj, short value) {
      try {
        if (isStatic) {
          handle.set(value);
        } else {
          checkObj(obj);
          handle.set(obj, value);
        }
      } catch (UnsupportedOperationException e) {
        throw unsupportedWrite(field, e);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
    }

    @Override
    public int getInt(Object obj) {
      try {
        if (isStatic) {
          return (int) handle.get();
        }
        checkObj(obj);
        return (int) handle.get(obj);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
    }

    @Override
    public void putInt(Object obj, int value) {
      try {
        if (isStatic) {
          handle.set(value);
        } else {
          checkObj(obj);
          handle.set(obj, value);
        }
      } catch (UnsupportedOperationException e) {
        throw unsupportedWrite(field, e);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
    }

    @Override
    public long getLong(Object obj) {
      try {
        if (isStatic) {
          return (long) handle.get();
        }
        checkObj(obj);
        return (long) handle.get(obj);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
    }

    @Override
    public void putLong(Object obj, long value) {
      try {
        if (isStatic) {
          handle.set(value);
        } else {
          checkObj(obj);
          handle.set(obj, value);
        }
      } catch (UnsupportedOperationException e) {
        throw unsupportedWrite(field, e);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
    }

    @Override
    public float getFloat(Object obj) {
      try {
        if (isStatic) {
          return (float) handle.get();
        }
        checkObj(obj);
        return (float) handle.get(obj);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
    }

    @Override
    public void putFloat(Object obj, float value) {
      try {
        if (isStatic) {
          handle.set(value);
        } else {
          checkObj(obj);
          handle.set(obj, value);
        }
      } catch (UnsupportedOperationException e) {
        throw unsupportedWrite(field, e);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
    }

    @Override
    public double getDouble(Object obj) {
      try {
        if (isStatic) {
          return (double) handle.get();
        }
        checkObj(obj);
        return (double) handle.get(obj);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
    }

    @Override
    public void putDouble(Object obj, double value) {
      try {
        if (isStatic) {
          handle.set(value);
        } else {
          checkObj(obj);
          handle.set(obj, value);
        }
      } catch (UnsupportedOperationException e) {
        throw unsupportedWrite(field, e);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
    }
  }
}
