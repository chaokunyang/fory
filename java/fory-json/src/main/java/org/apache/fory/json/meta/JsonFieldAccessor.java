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
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.fory.annotation.Internal;
import org.apache.fory.json.ForyJsonException;
import org.apache.fory.platform.AndroidSupport;
import org.apache.fory.platform.GraalvmSupport;
import org.apache.fory.platform.internal._JDKAccess;
import org.apache.fory.reflect.FieldAccessor;

/**
 * Uniform interpreted object-member access for fields, getters, and setters.
 *
 * <p>Field members delegate to Fory core's typed {@link FieldAccessor}. Method members cache a
 * trusted {@code MethodHandle} on the JVM and retain reflective invocation only for Android. Typed
 * primitive methods let interpreted object codecs avoid boxing for field-backed access, while
 * generated codecs consume the original field or method metadata and emit direct expressions.
 */
public abstract class JsonFieldAccessor {
  private static Map<Field, JsonFieldAccessor> nativeFields = new HashMap<>();
  private static Map<Method, JsonFieldAccessor> nativeGetters = new HashMap<>();
  private static Map<Method, JsonFieldAccessor> nativeSetters = new HashMap<>();
  private static boolean nativeCachesFrozen;

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
    if (GraalvmSupport.isGraalRuntime()) {
      return requireNativeAccessor(nativeFields.get(field), field);
    }
    return new FieldJsonAccessor(FieldAccessor.createAccessor(field));
  }

  public static JsonFieldAccessor forGetter(Method getter) {
    if (GraalvmSupport.isGraalRuntime()) {
      return requireNativeAccessor(nativeGetters.get(getter), getter);
    }
    return new GetterJsonAccessor(getter);
  }

  public static JsonFieldAccessor forSetter(Method setter) {
    if (GraalvmSupport.isGraalRuntime()) {
      return requireNativeAccessor(nativeSetters.get(setter), setter);
    }
    return new SetterJsonAccessor(setter);
  }

  /** Prepares one field accessor for Native Image runtime metadata construction. */
  @Internal
  public static synchronized void prepareField(Field field) {
    requireNativeBuildTime();
    // Core field access intentionally falls back to Method.invoke for record backing fields in a
    // native image. JSON retains the semantic field identity but must cache the component accessor
    // MethodHandle here so interpreted codecs never perform runtime reflection.
    JsonFieldAccessor accessor =
        field.getDeclaringClass().isRecord()
            ? new RecordFieldJsonAccessor(field)
            : new FieldJsonAccessor(FieldAccessor.createAccessor(field));
    putPrepared(nativeFields, field, accessor);
  }

  /** Prepares one getter accessor for Native Image runtime metadata construction. */
  @Internal
  public static synchronized void prepareGetter(Method getter) {
    requireNativeBuildTime();
    putPrepared(nativeGetters, getter, new GetterJsonAccessor(getter));
  }

  /** Prepares one setter accessor for Native Image runtime metadata construction. */
  @Internal
  public static synchronized void prepareSetter(Method setter) {
    requireNativeBuildTime();
    putPrepared(nativeSetters, setter, new SetterJsonAccessor(setter));
  }

  /** Freezes all Native Image accessors after hosted analysis. */
  @Internal
  public static synchronized void freezeNativeAccessors() {
    if (nativeCachesFrozen) {
      return;
    }
    nativeFields = immutable(nativeFields);
    nativeGetters = immutable(nativeGetters);
    nativeSetters = immutable(nativeSetters);
    nativeCachesFrozen = true;
  }

  private static void requireNativeBuildTime() {
    if (!GraalvmSupport.isGraalBuildTime() || nativeCachesFrozen) {
      throw new IllegalStateException("Fory JSON native accessor cache is not writable");
    }
  }

  private static <M> void putPrepared(
      Map<M, JsonFieldAccessor> accessors, M member, JsonFieldAccessor accessor) {
    JsonFieldAccessor previous = accessors.putIfAbsent(member, accessor);
    if (previous != null && previous.getClass() != accessor.getClass()) {
      throw new IllegalStateException("Conflicting Fory JSON accessor for " + member);
    }
  }

  private static <M> Map<M, JsonFieldAccessor> immutable(Map<M, JsonFieldAccessor> accessors) {
    return accessors.isEmpty()
        ? Collections.emptyMap()
        : Collections.unmodifiableMap(new HashMap<>(accessors));
  }

  private static JsonFieldAccessor requireNativeAccessor(
      JsonFieldAccessor accessor, Object member) {
    if (accessor == null) {
      throw new ForyJsonException("Missing Native Image Fory JSON accessor metadata for " + member);
    }
    return accessor;
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

  private static final class RecordFieldJsonAccessor extends JsonFieldAccessor {
    private final Field field;
    private final MethodHandle getterHandle;

    private RecordFieldJsonAccessor(Field field) {
      this.field = field;
      try {
        getterHandle = methodHandle(field.getDeclaringClass().getDeclaredMethod(field.getName()));
      } catch (NoSuchMethodException e) {
        throw new ForyJsonException("Cannot find JSON record accessor for " + field, e);
      }
    }

    @Override
    public Field field() {
      return field;
    }

    @Override
    public Object getObject(Object target) {
      try {
        return getterHandle.invoke(target);
      } catch (Throwable e) {
        throw new ForyJsonException("Cannot access JSON record field " + field, e);
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
