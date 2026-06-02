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

import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import org.apache.fory.platform.GraalvmSupport;
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
      return _JDKAccess
          ._trustedLookup(field.getDeclaringClass())
          .findVarHandle(field.getDeclaringClass(), field.getName(), field.getType());
    } catch (IllegalAccessException e) {
      throw accessFailure(field, e);
    } catch (NoSuchFieldException e) {
      throw new IllegalStateException("Failed to create VarHandle for field " + field, e);
    }
  }

  private static IllegalStateException accessFailure(Field field, Throwable cause) {
    return new IllegalStateException(
        "Cannot access field "
            + field
            + ". JDK25 zero-Unsafe mode requires java.base/java.lang.invoke to be open "
            + "to org.apache.fory.core",
        cause);
  }

  private static RuntimeException accessorFailure(Field field, Throwable cause) {
    return new RuntimeException("Failed to access field: " + field, cause);
  }

  private abstract static class InstanceAccessor extends FieldAccessor {
    protected final VarHandle handle;

    InstanceAccessor(Field field) {
      super(field);
      handle = fieldHandle(field);
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
      return (boolean) handle.get(obj);
    }

    @Override
    public void set(Object obj, Object value) {
      putBoolean(obj, (Boolean) value);
    }

    @Override
    public void putBoolean(Object obj, boolean value) {
      try {
        handle.set(obj, value);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
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
      return (byte) handle.get(obj);
    }

    @Override
    public void set(Object obj, Object value) {
      putByte(obj, (Byte) value);
    }

    @Override
    public void putByte(Object obj, byte value) {
      try {
        handle.set(obj, value);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
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
      return (char) handle.get(obj);
    }

    @Override
    public void set(Object obj, Object value) {
      putChar(obj, (Character) value);
    }

    @Override
    public void putChar(Object obj, char value) {
      try {
        handle.set(obj, value);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
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
      return (short) handle.get(obj);
    }

    @Override
    public void set(Object obj, Object value) {
      putShort(obj, (Short) value);
    }

    @Override
    public void putShort(Object obj, short value) {
      try {
        handle.set(obj, value);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
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
      return (int) handle.get(obj);
    }

    @Override
    public void set(Object obj, Object value) {
      putInt(obj, (Integer) value);
    }

    @Override
    public void putInt(Object obj, int value) {
      try {
        handle.set(obj, value);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
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
      return (long) handle.get(obj);
    }

    @Override
    public void set(Object obj, Object value) {
      putLong(obj, (Long) value);
    }

    @Override
    public void putLong(Object obj, long value) {
      try {
        handle.set(obj, value);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
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
      return (float) handle.get(obj);
    }

    @Override
    public void set(Object obj, Object value) {
      putFloat(obj, (Float) value);
    }

    @Override
    public void putFloat(Object obj, float value) {
      try {
        handle.set(obj, value);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
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
      return (double) handle.get(obj);
    }

    @Override
    public void set(Object obj, Object value) {
      putDouble(obj, (Double) value);
    }

    @Override
    public void putDouble(Object obj, double value) {
      try {
        handle.set(obj, value);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
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
      return handle.get(obj);
    }

    @Override
    public void set(Object obj, Object value) {
      try {
        handle.set(obj, value);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
    }
  }

  static final class GeneratedAccessor extends InstanceAccessor {
    GeneratedAccessor(Field field) {
      super(field);
    }

    @Override
    public Object get(Object obj) {
      try {
        return handle.get(obj);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
    }

    @Override
    public void set(Object obj, Object value) {
      try {
        handle.set(obj, value);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
    }

    @Override
    public boolean getBoolean(Object obj) {
      try {
        return (boolean) handle.get(obj);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
    }

    @Override
    public void putBoolean(Object obj, boolean value) {
      try {
        handle.set(obj, value);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
    }

    @Override
    public byte getByte(Object obj) {
      try {
        return (byte) handle.get(obj);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
    }

    @Override
    public void putByte(Object obj, byte value) {
      try {
        handle.set(obj, value);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
    }

    @Override
    public char getChar(Object obj) {
      try {
        return (char) handle.get(obj);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
    }

    @Override
    public void putChar(Object obj, char value) {
      try {
        handle.set(obj, value);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
    }

    @Override
    public short getShort(Object obj) {
      try {
        return (short) handle.get(obj);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
    }

    @Override
    public void putShort(Object obj, short value) {
      try {
        handle.set(obj, value);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
    }

    @Override
    public int getInt(Object obj) {
      try {
        return (int) handle.get(obj);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
    }

    @Override
    public void putInt(Object obj, int value) {
      try {
        handle.set(obj, value);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
    }

    @Override
    public long getLong(Object obj) {
      try {
        return (long) handle.get(obj);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
    }

    @Override
    public void putLong(Object obj, long value) {
      try {
        handle.set(obj, value);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
    }

    @Override
    public float getFloat(Object obj) {
      try {
        return (float) handle.get(obj);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
    }

    @Override
    public void putFloat(Object obj, float value) {
      try {
        handle.set(obj, value);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
    }

    @Override
    public double getDouble(Object obj) {
      try {
        return (double) handle.get(obj);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
    }

    @Override
    public void putDouble(Object obj, double value) {
      try {
        handle.set(obj, value);
      } catch (RuntimeException e) {
        throw accessorFailure(field, e);
      }
    }
  }
}
