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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import org.apache.fory.platform.GraalvmSupport;
import org.apache.fory.platform.JdkVersion;
import org.apache.fory.platform.internal._JDKAccess;
import org.apache.fory.type.TypeUtils;
import org.apache.fory.util.Preconditions;

final class FieldAccessorStrategy {
  private FieldAccessorStrategy() {}

  static FieldAccessor createAccessor(Field field) {
    Preconditions.checkArgument(!Modifier.isStatic(field.getModifiers()), field);
    if (GraalvmSupport.isGraalBuildTime() || GraalvmSupport.IN_GRAALVM_NATIVE_IMAGE) {
      return new GeneratedAccessor(field);
    }
    FieldAccessor hiddenAccessor = HiddenFieldAccessorFactory.create(field);
    if (hiddenAccessor != null) {
      return hiddenAccessor;
    }
    return createVarHandleAccessor(field);
  }

  static FieldAccessor createStaticAccessor(Field field) {
    Preconditions.checkArgument(Modifier.isStatic(field.getModifiers()), field);
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

  private static VarHandle fieldHandle(Field field) {
    try {
      if (canUsePublicField(field)) {
        try {
          return findFieldHandle(MethodHandles.publicLookup(), field);
        } catch (IllegalAccessException ignored) {
          // Fall through to the JDK25 trusted lookup. It is enabled by opening
          // java.base/java.lang.invoke to Fory, not by opening every target package.
        }
      }
      return findFieldHandle(privateLookup(field), field);
    } catch (IllegalAccessException e) {
      throw accessFailure(field, e);
    } catch (NoSuchFieldException e) {
      throw new IllegalStateException("Failed to create VarHandle for field " + field, e);
    }
  }

  private static VarHandle findFieldHandle(MethodHandles.Lookup lookup, Field field)
      throws IllegalAccessException, NoSuchFieldException {
    if (Modifier.isStatic(field.getModifiers())) {
      return lookup.findStaticVarHandle(
          field.getDeclaringClass(), field.getName(), field.getType());
    }
    return lookup.findVarHandle(field.getDeclaringClass(), field.getName(), field.getType());
  }

  private static MethodHandles.Lookup privateLookup(Field field) {
    Class<?> declaringClass = field.getDeclaringClass();
    return _JDKAccess.privateLookupIn(declaringClass, MethodHandles.lookup());
  }

  private static boolean canUsePublicField(Field field) {
    return Modifier.isPublic(field.getModifiers())
        && Modifier.isPublic(field.getDeclaringClass().getModifiers());
  }

  private static IllegalStateException accessFailure(Field field, Throwable cause) {
    return new IllegalStateException(
        "Cannot access field "
            + field
            + ". JDK25 zero-Unsafe mode requires java.base/java.lang.invoke to be open "
            + "to org.apache.fory.core",
        cause);
  }

  private static UnsupportedOperationException unsupportedWrite(Field field, Throwable cause) {
    return new UnsupportedOperationException(
        "Field cannot be written through supported JDK access APIs: " + field, cause);
  }

  private static IllegalStateException finalMutationFailure(Field field, Throwable cause) {
    String versionMessage =
        JdkVersion.MAJOR_VERSION >= 26
            ? "On JDK26+, start the JVM with "
                + "--enable-final-field-mutation=org.apache.fory.core. "
            : "";
    return new IllegalStateException(
        "Cannot write final field "
            + field
            + ". "
            + versionMessage
            + "The declaring package must be open to org.apache.fory.core when it is in a "
            + "named module.",
        cause);
  }

  private static RuntimeException accessorFailure(Field field, Throwable cause) {
    return new RuntimeException("Failed to access field: " + field, cause);
  }

  private abstract static class VarHandleAccessor extends FieldAccessor {
    protected final VarHandle handle;
    protected final boolean isStatic;
    protected final boolean isFinal;
    protected volatile Field finalField;

    VarHandleAccessor(Field field) {
      super(field);
      handle = fieldHandle(field);
      isStatic = Modifier.isStatic(field.getModifiers());
      isFinal = Modifier.isFinal(field.getModifiers());
    }

    private static Field createFinalField(Field field) {
      try {
        field.setAccessible(true);
        return field;
      } catch (RuntimeException e) {
        throw finalMutationFailure(field, e);
      }
    }

    private Field finalField(Throwable cause) {
      if (isStatic || !isFinal) {
        throw unsupportedWrite(field, cause);
      }
      Field setter = finalField;
      if (setter == null) {
        setter = createFinalField(field);
        finalField = setter;
      }
      return setter;
    }

    protected void setFinal(Object obj, Object value, Throwable cause) {
      Field setter = finalField(cause);
      checkObj(obj);
      try {
        setter.set(obj, value);
      } catch (IllegalAccessException | IllegalArgumentException e) {
        throw finalMutationFailure(field, e);
      }
    }

    protected void setFinalBoolean(Object obj, boolean value, Throwable cause) {
      Field setter = finalField(cause);
      checkObj(obj);
      try {
        setter.setBoolean(obj, value);
      } catch (IllegalAccessException | IllegalArgumentException e) {
        throw finalMutationFailure(field, e);
      }
    }

    protected void setFinalByte(Object obj, byte value, Throwable cause) {
      Field setter = finalField(cause);
      checkObj(obj);
      try {
        setter.setByte(obj, value);
      } catch (IllegalAccessException | IllegalArgumentException e) {
        throw finalMutationFailure(field, e);
      }
    }

    protected void setFinalChar(Object obj, char value, Throwable cause) {
      Field setter = finalField(cause);
      checkObj(obj);
      try {
        setter.setChar(obj, value);
      } catch (IllegalAccessException | IllegalArgumentException e) {
        throw finalMutationFailure(field, e);
      }
    }

    protected void setFinalShort(Object obj, short value, Throwable cause) {
      Field setter = finalField(cause);
      checkObj(obj);
      try {
        setter.setShort(obj, value);
      } catch (IllegalAccessException | IllegalArgumentException e) {
        throw finalMutationFailure(field, e);
      }
    }

    protected void setFinalInt(Object obj, int value, Throwable cause) {
      Field setter = finalField(cause);
      checkObj(obj);
      try {
        setter.setInt(obj, value);
      } catch (IllegalAccessException | IllegalArgumentException e) {
        throw finalMutationFailure(field, e);
      }
    }

    protected void setFinalLong(Object obj, long value, Throwable cause) {
      Field setter = finalField(cause);
      checkObj(obj);
      try {
        setter.setLong(obj, value);
      } catch (IllegalAccessException | IllegalArgumentException e) {
        throw finalMutationFailure(field, e);
      }
    }

    protected void setFinalFloat(Object obj, float value, Throwable cause) {
      Field setter = finalField(cause);
      checkObj(obj);
      try {
        setter.setFloat(obj, value);
      } catch (IllegalAccessException | IllegalArgumentException e) {
        throw finalMutationFailure(field, e);
      }
    }

    protected void setFinalDouble(Object obj, double value, Throwable cause) {
      Field setter = finalField(cause);
      checkObj(obj);
      try {
        setter.setDouble(obj, value);
      } catch (IllegalAccessException | IllegalArgumentException e) {
        throw finalMutationFailure(field, e);
      }
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
      if (isFinal) {
        setFinalBoolean(obj, value, null);
        return;
      }
      try {
        if (isStatic) {
          handle.set(value);
        } else {
          checkObj(obj);
          handle.set(obj, value);
        }
      } catch (UnsupportedOperationException e) {
        setFinalBoolean(obj, value, e);
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
      if (isFinal) {
        setFinalByte(obj, value, null);
        return;
      }
      try {
        if (isStatic) {
          handle.set(value);
        } else {
          checkObj(obj);
          handle.set(obj, value);
        }
      } catch (UnsupportedOperationException e) {
        setFinalByte(obj, value, e);
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
      if (isFinal) {
        setFinalChar(obj, value, null);
        return;
      }
      try {
        if (isStatic) {
          handle.set(value);
        } else {
          checkObj(obj);
          handle.set(obj, value);
        }
      } catch (UnsupportedOperationException e) {
        setFinalChar(obj, value, e);
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
      if (isFinal) {
        setFinalShort(obj, value, null);
        return;
      }
      try {
        if (isStatic) {
          handle.set(value);
        } else {
          checkObj(obj);
          handle.set(obj, value);
        }
      } catch (UnsupportedOperationException e) {
        setFinalShort(obj, value, e);
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
      if (isFinal) {
        setFinalInt(obj, value, null);
        return;
      }
      try {
        if (isStatic) {
          handle.set(value);
        } else {
          checkObj(obj);
          handle.set(obj, value);
        }
      } catch (UnsupportedOperationException e) {
        setFinalInt(obj, value, e);
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
      if (isFinal) {
        setFinalLong(obj, value, null);
        return;
      }
      try {
        if (isStatic) {
          handle.set(value);
        } else {
          checkObj(obj);
          handle.set(obj, value);
        }
      } catch (UnsupportedOperationException e) {
        setFinalLong(obj, value, e);
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
      if (isFinal) {
        setFinalFloat(obj, value, null);
        return;
      }
      try {
        if (isStatic) {
          handle.set(value);
        } else {
          checkObj(obj);
          handle.set(obj, value);
        }
      } catch (UnsupportedOperationException e) {
        setFinalFloat(obj, value, e);
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
      if (isFinal) {
        setFinalDouble(obj, value, null);
        return;
      }
      try {
        if (isStatic) {
          handle.set(value);
        } else {
          checkObj(obj);
          handle.set(obj, value);
        }
      } catch (UnsupportedOperationException e) {
        setFinalDouble(obj, value, e);
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
      if (isFinal) {
        setFinal(obj, value, null);
        return;
      }
      try {
        if (isStatic) {
          handle.set(value);
        } else {
          checkObj(obj);
          handle.set(obj, value);
        }
      } catch (UnsupportedOperationException e) {
        setFinal(obj, value, e);
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
      if (isFinal) {
        setFinal(obj, value, null);
        return;
      }
      try {
        if (isStatic) {
          handle.set(value);
        } else {
          checkObj(obj);
          handle.set(obj, value);
        }
      } catch (UnsupportedOperationException e) {
        setFinal(obj, value, e);
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
      if (isFinal) {
        setFinalBoolean(obj, value, null);
        return;
      }
      try {
        if (isStatic) {
          handle.set(value);
        } else {
          checkObj(obj);
          handle.set(obj, value);
        }
      } catch (UnsupportedOperationException e) {
        setFinalBoolean(obj, value, e);
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
      if (isFinal) {
        setFinalByte(obj, value, null);
        return;
      }
      try {
        if (isStatic) {
          handle.set(value);
        } else {
          checkObj(obj);
          handle.set(obj, value);
        }
      } catch (UnsupportedOperationException e) {
        setFinalByte(obj, value, e);
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
      if (isFinal) {
        setFinalChar(obj, value, null);
        return;
      }
      try {
        if (isStatic) {
          handle.set(value);
        } else {
          checkObj(obj);
          handle.set(obj, value);
        }
      } catch (UnsupportedOperationException e) {
        setFinalChar(obj, value, e);
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
      if (isFinal) {
        setFinalShort(obj, value, null);
        return;
      }
      try {
        if (isStatic) {
          handle.set(value);
        } else {
          checkObj(obj);
          handle.set(obj, value);
        }
      } catch (UnsupportedOperationException e) {
        setFinalShort(obj, value, e);
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
      if (isFinal) {
        setFinalInt(obj, value, null);
        return;
      }
      try {
        if (isStatic) {
          handle.set(value);
        } else {
          checkObj(obj);
          handle.set(obj, value);
        }
      } catch (UnsupportedOperationException e) {
        setFinalInt(obj, value, e);
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
      if (isFinal) {
        setFinalLong(obj, value, null);
        return;
      }
      try {
        if (isStatic) {
          handle.set(value);
        } else {
          checkObj(obj);
          handle.set(obj, value);
        }
      } catch (UnsupportedOperationException e) {
        setFinalLong(obj, value, e);
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
      if (isFinal) {
        setFinalFloat(obj, value, null);
        return;
      }
      try {
        if (isStatic) {
          handle.set(value);
        } else {
          checkObj(obj);
          handle.set(obj, value);
        }
      } catch (UnsupportedOperationException e) {
        setFinalFloat(obj, value, e);
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
      if (isFinal) {
        setFinalDouble(obj, value, null);
        return;
      }
      try {
        if (isStatic) {
          handle.set(value);
        } else {
          checkObj(obj);
          handle.set(obj, value);
        }
      } catch (UnsupportedOperationException e) {
        setFinalDouble(obj, value, e);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
    }
  }
}
