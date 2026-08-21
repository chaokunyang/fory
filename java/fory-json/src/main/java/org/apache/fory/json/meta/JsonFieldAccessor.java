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
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import org.apache.fory.collection.ClassValueCache;
import org.apache.fory.json.ForyJsonException;
import org.apache.fory.platform.AndroidSupport;
import org.apache.fory.platform.GraalvmSupport;
import org.apache.fory.platform.JdkVersion;
import org.apache.fory.platform.internal._JDKAccess;
import org.apache.fory.reflect.FieldAccessor;
import org.apache.fory.util.function.ToByteFunction;
import org.apache.fory.util.function.ToCharFunction;
import org.apache.fory.util.function.ToFloatFunction;
import org.apache.fory.util.function.ToShortFunction;

/**
 * Uniform interpreted object-member access for fields, getters, and setters.
 *
 * <p>Field members use typed access so primitive paths do not box. Ordinary JVM fields delegate to
 * Fory core's {@link FieldAccessor}; JDK 25 Native Image fields use typed reflection. Method
 * members cache a trusted {@code MethodHandle} on the JVM, use reflection on Android, and use
 * lambdas prepared by the Fory JSON Native Image Feature. Generated codecs consume the original
 * field or method metadata and emit direct expressions.
 */
public abstract class JsonFieldAccessor {
  private static final boolean USE_JDK25_NATIVE_ACCESS =
      GraalvmSupport.IN_GRAALVM_NATIVE_IMAGE && JdkVersion.MAJOR_VERSION >= 25;
  // The Feature calls the ordinary factories during analysis, so Native Image retains the same
  // accessor instances later returned while runtime configurations build interpreted codecs.
  private static final ClassValueCache<ConcurrentMap<Member, JsonFieldAccessor>> NATIVE_ACCESSORS =
      ClassValueCache.newClassKeyCache(32);

  public Object getObject(Object target) {
    throw new UnsupportedOperationException();
  }

  public Field field() {
    return null;
  }

  public Method getter() {
    return null;
  }

  public Method setter() {
    return null;
  }

  public FieldAccessor coreAccessor() {
    return null;
  }

  public boolean getBoolean(Object target) {
    return (Boolean) getObject(target);
  }

  public byte getByte(Object target) {
    return (Byte) getObject(target);
  }

  public short getShort(Object target) {
    return (Short) getObject(target);
  }

  public int getInt(Object target) {
    return (Integer) getObject(target);
  }

  public long getLong(Object target) {
    return (Long) getObject(target);
  }

  public float getFloat(Object target) {
    return (Float) getObject(target);
  }

  public double getDouble(Object target) {
    return (Double) getObject(target);
  }

  public char getChar(Object target) {
    return (Character) getObject(target);
  }

  public void putObject(Object target, Object value) {
    throw new UnsupportedOperationException();
  }

  public void putBoolean(Object target, boolean value) {
    putObject(target, value);
  }

  public void putByte(Object target, byte value) {
    putObject(target, value);
  }

  public void putShort(Object target, short value) {
    putObject(target, value);
  }

  public void putInt(Object target, int value) {
    putObject(target, value);
  }

  public void putLong(Object target, long value) {
    putObject(target, value);
  }

  public void putFloat(Object target, float value) {
    putObject(target, value);
  }

  public void putDouble(Object target, double value) {
    putObject(target, value);
  }

  public void putChar(Object target, char value) {
    putObject(target, value);
  }

  public static JsonFieldAccessor forField(Field field) {
    if (GraalvmSupport.IN_GRAALVM_NATIVE_IMAGE) {
      return nativeAccessors(field).computeIfAbsent(field, JsonFieldAccessor::newFieldAccessor);
    }
    return newFieldAccessor(field);
  }

  public static JsonFieldAccessor forGetter(Method getter) {
    if (GraalvmSupport.IN_GRAALVM_NATIVE_IMAGE) {
      return nativeAccessors(getter).computeIfAbsent(getter, JsonFieldAccessor::newGetterAccessor);
    }
    return newGetterAccessor(getter);
  }

  public static JsonFieldAccessor forSetter(Method setter) {
    if (GraalvmSupport.IN_GRAALVM_NATIVE_IMAGE) {
      return nativeAccessors(setter).computeIfAbsent(setter, JsonFieldAccessor::newSetterAccessor);
    }
    return newSetterAccessor(setter);
  }

  private static ConcurrentMap<Member, JsonFieldAccessor> nativeAccessors(Member member) {
    return NATIVE_ACCESSORS.get(member.getDeclaringClass(), ConcurrentHashMap::new);
  }

  private static JsonFieldAccessor newFieldAccessor(Member member) {
    Field field = (Field) member;
    if (USE_JDK25_NATIVE_ACCESS) {
      // LambdaMetafactory accepts invocation handles but rejects REF_getField and REF_putField.
      // Typed reflection is faster than JDK 25's VarHandle-backed FieldAccessor in Native Image.
      return new ReflectiveFieldJsonAccessor(field);
    }
    return new FieldJsonAccessor(FieldAccessor.createAccessor(field));
  }

  private static JsonFieldAccessor newGetterAccessor(Member member) {
    Method getter = (Method) member;
    return USE_JDK25_NATIVE_ACCESS
        ? new LambdaGetterJsonAccessor(getter)
        : new GetterJsonAccessor(getter);
  }

  private static JsonFieldAccessor newSetterAccessor(Member member) {
    Method setter = (Method) member;
    return USE_JDK25_NATIVE_ACCESS && setter.getParameterCount() == 1
        ? new LambdaSetterJsonAccessor(setter)
        : new SetterJsonAccessor(setter);
  }

  private static final class FieldJsonAccessor extends JsonFieldAccessor {
    private final FieldAccessor accessor;

    private FieldJsonAccessor(FieldAccessor accessor) {
      this.accessor = accessor;
    }

    @Override
    public FieldAccessor coreAccessor() {
      return accessor;
    }

    @Override
    public Field field() {
      return accessor.getField();
    }

    @Override
    public Object getObject(Object target) {
      return accessor.getObject(target);
    }

    @Override
    public boolean getBoolean(Object target) {
      return accessor.getBoolean(target);
    }

    @Override
    public byte getByte(Object target) {
      return accessor.getByte(target);
    }

    @Override
    public short getShort(Object target) {
      return accessor.getShort(target);
    }

    @Override
    public int getInt(Object target) {
      return accessor.getInt(target);
    }

    @Override
    public long getLong(Object target) {
      return accessor.getLong(target);
    }

    @Override
    public float getFloat(Object target) {
      return accessor.getFloat(target);
    }

    @Override
    public double getDouble(Object target) {
      return accessor.getDouble(target);
    }

    @Override
    public char getChar(Object target) {
      return accessor.getChar(target);
    }

    @Override
    public void putObject(Object target, Object value) {
      accessor.putObject(target, value);
    }

    @Override
    public void putBoolean(Object target, boolean value) {
      accessor.putBoolean(target, value);
    }

    @Override
    public void putByte(Object target, byte value) {
      accessor.putByte(target, value);
    }

    @Override
    public void putShort(Object target, short value) {
      accessor.putShort(target, value);
    }

    @Override
    public void putInt(Object target, int value) {
      accessor.putInt(target, value);
    }

    @Override
    public void putLong(Object target, long value) {
      accessor.putLong(target, value);
    }

    @Override
    public void putFloat(Object target, float value) {
      accessor.putFloat(target, value);
    }

    @Override
    public void putDouble(Object target, double value) {
      accessor.putDouble(target, value);
    }

    @Override
    public void putChar(Object target, char value) {
      accessor.putChar(target, value);
    }
  }

  private static final class ReflectiveFieldJsonAccessor extends JsonFieldAccessor {
    private final Field field;

    private ReflectiveFieldJsonAccessor(Field field) {
      this.field = field;
      field.setAccessible(true);
    }

    @Override
    public Field field() {
      return field;
    }

    @Override
    public Object getObject(Object target) {
      try {
        return field.get(target);
      } catch (IllegalAccessException e) {
        throw new ForyJsonException("Cannot read JSON field " + field, e);
      }
    }

    @Override
    public boolean getBoolean(Object target) {
      try {
        return field.getBoolean(target);
      } catch (IllegalAccessException e) {
        throw new ForyJsonException("Cannot read JSON field " + field, e);
      }
    }

    @Override
    public byte getByte(Object target) {
      try {
        return field.getByte(target);
      } catch (IllegalAccessException e) {
        throw new ForyJsonException("Cannot read JSON field " + field, e);
      }
    }

    @Override
    public short getShort(Object target) {
      try {
        return field.getShort(target);
      } catch (IllegalAccessException e) {
        throw new ForyJsonException("Cannot read JSON field " + field, e);
      }
    }

    @Override
    public int getInt(Object target) {
      try {
        return field.getInt(target);
      } catch (IllegalAccessException e) {
        throw new ForyJsonException("Cannot read JSON field " + field, e);
      }
    }

    @Override
    public long getLong(Object target) {
      try {
        return field.getLong(target);
      } catch (IllegalAccessException e) {
        throw new ForyJsonException("Cannot read JSON field " + field, e);
      }
    }

    @Override
    public float getFloat(Object target) {
      try {
        return field.getFloat(target);
      } catch (IllegalAccessException e) {
        throw new ForyJsonException("Cannot read JSON field " + field, e);
      }
    }

    @Override
    public double getDouble(Object target) {
      try {
        return field.getDouble(target);
      } catch (IllegalAccessException e) {
        throw new ForyJsonException("Cannot read JSON field " + field, e);
      }
    }

    @Override
    public char getChar(Object target) {
      try {
        return field.getChar(target);
      } catch (IllegalAccessException e) {
        throw new ForyJsonException("Cannot read JSON field " + field, e);
      }
    }

    @Override
    public void putObject(Object target, Object value) {
      try {
        field.set(target, value);
      } catch (IllegalAccessException e) {
        throw new ForyJsonException("Cannot write JSON field " + field, e);
      }
    }

    @Override
    public void putBoolean(Object target, boolean value) {
      try {
        field.setBoolean(target, value);
      } catch (IllegalAccessException e) {
        throw new ForyJsonException("Cannot write JSON field " + field, e);
      }
    }

    @Override
    public void putByte(Object target, byte value) {
      try {
        field.setByte(target, value);
      } catch (IllegalAccessException e) {
        throw new ForyJsonException("Cannot write JSON field " + field, e);
      }
    }

    @Override
    public void putShort(Object target, short value) {
      try {
        field.setShort(target, value);
      } catch (IllegalAccessException e) {
        throw new ForyJsonException("Cannot write JSON field " + field, e);
      }
    }

    @Override
    public void putInt(Object target, int value) {
      try {
        field.setInt(target, value);
      } catch (IllegalAccessException e) {
        throw new ForyJsonException("Cannot write JSON field " + field, e);
      }
    }

    @Override
    public void putLong(Object target, long value) {
      try {
        field.setLong(target, value);
      } catch (IllegalAccessException e) {
        throw new ForyJsonException("Cannot write JSON field " + field, e);
      }
    }

    @Override
    public void putFloat(Object target, float value) {
      try {
        field.setFloat(target, value);
      } catch (IllegalAccessException e) {
        throw new ForyJsonException("Cannot write JSON field " + field, e);
      }
    }

    @Override
    public void putDouble(Object target, double value) {
      try {
        field.setDouble(target, value);
      } catch (IllegalAccessException e) {
        throw new ForyJsonException("Cannot write JSON field " + field, e);
      }
    }

    @Override
    public void putChar(Object target, char value) {
      try {
        field.setChar(target, value);
      } catch (IllegalAccessException e) {
        throw new ForyJsonException("Cannot write JSON field " + field, e);
      }
    }
  }

  private static final class GetterJsonAccessor extends JsonFieldAccessor {
    private final Method getter;
    private final MethodHandle getterHandle;

    private GetterJsonAccessor(Method getter) {
      this.getter = getter;
      if (AndroidSupport.IS_ANDROID) {
        getter.setAccessible(true);
        getterHandle = null;
      } else {
        getterHandle = methodHandle(getter);
      }
    }

    @Override
    public Method getter() {
      return getter;
    }

    @Override
    public Object getObject(Object target) {
      try {
        if (AndroidSupport.IS_ANDROID) {
          return getter.invoke(target);
        }
        return getterHandle.invoke(target);
      } catch (Throwable e) {
        throw accessException(getter, e);
      }
    }
  }

  private static final class LambdaGetterJsonAccessor extends JsonFieldAccessor {
    private final Method getter;
    private final Object function;

    private LambdaGetterJsonAccessor(Method getter) {
      this.getter = getter;
      MethodHandles.Lookup lookup = _JDKAccess._trustedLookup(getter.getDeclaringClass());
      try {
        function =
            _JDKAccess.makeGetterFunction(lookup, lookup.unreflect(getter), getter.getReturnType());
      } catch (IllegalAccessException e) {
        throw accessException(getter, e);
      }
    }

    @Override
    public Method getter() {
      return getter;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object getObject(Object target) {
      Class<?> type = getter.getReturnType();
      if (!type.isPrimitive()) {
        try {
          return ((Function<Object, Object>) function).apply(target);
        } catch (Throwable e) {
          throw accessException(getter, e);
        }
      }
      if (type == boolean.class) {
        return getBoolean(target);
      }
      if (type == byte.class) {
        return getByte(target);
      }
      if (type == short.class) {
        return getShort(target);
      }
      if (type == int.class) {
        return getInt(target);
      }
      if (type == long.class) {
        return getLong(target);
      }
      if (type == float.class) {
        return getFloat(target);
      }
      if (type == double.class) {
        return getDouble(target);
      }
      return getChar(target);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean getBoolean(Object target) {
      try {
        return ((Predicate<Object>) function).test(target);
      } catch (Throwable e) {
        throw accessException(getter, e);
      }
    }

    @Override
    @SuppressWarnings("unchecked")
    public byte getByte(Object target) {
      try {
        return ((ToByteFunction<Object>) function).applyAsByte(target);
      } catch (Throwable e) {
        throw accessException(getter, e);
      }
    }

    @Override
    @SuppressWarnings("unchecked")
    public short getShort(Object target) {
      try {
        return ((ToShortFunction<Object>) function).applyAsShort(target);
      } catch (Throwable e) {
        throw accessException(getter, e);
      }
    }

    @Override
    @SuppressWarnings("unchecked")
    public int getInt(Object target) {
      try {
        return ((ToIntFunction<Object>) function).applyAsInt(target);
      } catch (Throwable e) {
        throw accessException(getter, e);
      }
    }

    @Override
    @SuppressWarnings("unchecked")
    public long getLong(Object target) {
      try {
        return ((ToLongFunction<Object>) function).applyAsLong(target);
      } catch (Throwable e) {
        throw accessException(getter, e);
      }
    }

    @Override
    @SuppressWarnings("unchecked")
    public float getFloat(Object target) {
      try {
        return ((ToFloatFunction<Object>) function).applyAsFloat(target);
      } catch (Throwable e) {
        throw accessException(getter, e);
      }
    }

    @Override
    @SuppressWarnings("unchecked")
    public double getDouble(Object target) {
      try {
        return ((ToDoubleFunction<Object>) function).applyAsDouble(target);
      } catch (Throwable e) {
        throw accessException(getter, e);
      }
    }

    @Override
    @SuppressWarnings("unchecked")
    public char getChar(Object target) {
      try {
        return ((ToCharFunction<Object>) function).applyAsChar(target);
      } catch (Throwable e) {
        throw accessException(getter, e);
      }
    }
  }

  private static final class SetterJsonAccessor extends JsonFieldAccessor {
    private final Method setter;
    private final MethodHandle setterHandle;

    private SetterJsonAccessor(Method setter) {
      this.setter = setter;
      if (AndroidSupport.IS_ANDROID) {
        setter.setAccessible(true);
        setterHandle = null;
      } else {
        setterHandle = methodHandle(setter);
      }
    }

    @Override
    public Method setter() {
      return setter;
    }

    @Override
    public void putObject(Object target, Object value) {
      try {
        if (AndroidSupport.IS_ANDROID) {
          setter.invoke(target, value);
        } else {
          setterHandle.invoke(target, value);
        }
      } catch (Throwable e) {
        throw accessException(setter, e);
      }
    }
  }

  private static final class LambdaSetterJsonAccessor extends JsonFieldAccessor {
    private final Method setter;
    private final BiConsumer<Object, Object> function;

    @SuppressWarnings("unchecked")
    private LambdaSetterJsonAccessor(Method setter) {
      this.setter = setter;
      MethodHandles.Lookup lookup = _JDKAccess._trustedLookup(setter.getDeclaringClass());
      try {
        function =
            (BiConsumer<Object, Object>)
                (BiConsumer<?, ?>) _JDKAccess.makeJDKBiConsumer(lookup, lookup.unreflect(setter));
      } catch (IllegalAccessException e) {
        throw accessException(setter, e);
      }
    }

    @Override
    public Method setter() {
      return setter;
    }

    @Override
    public void putObject(Object target, Object value) {
      try {
        function.accept(target, value);
      } catch (Throwable e) {
        throw accessException(setter, e);
      }
    }
  }

  private static MethodHandle methodHandle(Method method) {
    try {
      return _JDKAccess._trustedLookup(method.getDeclaringClass()).unreflect(method);
    } catch (IllegalAccessException e) {
      throw accessException(method, e);
    }
  }

  /** Preserves ordinary property-method failure semantics for generated direct calls. */
  protected static ForyJsonException accessException(Method method, Throwable e) {
    Throwable cause =
        e instanceof InvocationTargetException ? ((InvocationTargetException) e).getCause() : e;
    return new ForyJsonException("Cannot access JSON property method " + method, cause);
  }
}
