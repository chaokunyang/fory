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

package org.apache.fory.json.resolver;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.fory.annotation.Internal;
import org.apache.fory.json.ForyJsonException;
import org.apache.fory.json.codec.GeneratedJsonCodec;
import org.apache.fory.json.codec.JsonValueCodec;
import org.apache.fory.json.meta.JsonFieldAccessor;
import org.apache.fory.json.reader.Latin1JsonReader;
import org.apache.fory.json.reader.Utf16JsonReader;
import org.apache.fory.json.reader.Utf8JsonReader;
import org.apache.fory.json.writer.StringJsonWriter;
import org.apache.fory.json.writer.Utf8JsonWriter;
import org.apache.fory.platform.AndroidSupport;
import org.apache.fory.platform.GraalvmSupport;
import org.apache.fory.platform.internal._JDKAccess;
import org.apache.fory.reflect.ReflectionUtils;

/** Complete String representation selected by one effective {@code JsonValue} member. */
@Internal
public final class JsonStringValueCodec implements JsonValueCodec<Object> {
  private final Class<?> ownerType;
  private final JsonFieldAccessor accessor;
  private final ValueCreator creator;
  private final boolean raw;

  JsonStringValueCodec(
      Class<?> ownerType,
      JsonFieldAccessor accessor,
      Executable creator,
      GeneratedJsonCodec<?> generatedCodec,
      boolean raw) {
    this.ownerType = ownerType;
    this.accessor = accessor;
    this.creator =
        creator == null ? null : ValueCreator.forExecutable(ownerType, creator, generatedCodec);
    this.raw = raw;
  }

  @Override
  public void writeString(StringJsonWriter writer, Object value) {
    if (value == null) {
      writer.writeNull();
      return;
    }
    String string = (String) accessor.getObject(value);
    if (string == null) {
      writer.writeNull();
    } else if (raw) {
      writer.writeRawValue(string);
    } else {
      writer.writeString(string);
    }
  }

  @Override
  public void writeUtf8(Utf8JsonWriter writer, Object value) {
    if (value == null) {
      writer.writeNull();
      return;
    }
    String string = (String) accessor.getObject(value);
    if (string == null) {
      writer.writeNull();
    } else if (raw) {
      writer.writeRawValue(string);
    } else {
      writer.writeString(string);
    }
  }

  @Override
  public Object readLatin1(Latin1JsonReader reader) {
    if (reader.peekNull()) {
      reader.readNull();
      return null;
    }
    return read(reader.readString());
  }

  @Override
  public Object readUtf16(Utf16JsonReader reader) {
    if (reader.peekNull()) {
      reader.readNull();
      return null;
    }
    return read(reader.readString());
  }

  @Override
  public Object readUtf8(Utf8JsonReader reader) {
    if (reader.peekNull()) {
      reader.readNull();
      return null;
    }
    return read(reader.readString());
  }

  private Object read(String value) {
    if (raw) {
      throw new ForyJsonException(
          "Combined @JsonValue and @JsonRawValue representation is write-only for "
              + ownerType.getName());
    }
    if (creator == null) {
      throw new ForyJsonException(
          "Reading @JsonValue type "
              + ownerType.getName()
              + " requires a one-String-argument @JsonCreator");
    }
    return creator.create(value);
  }

  private abstract static class ValueCreator {
    private static Map<Executable, MethodHandle> nativeInvokers = new HashMap<>();
    private static boolean nativeInvokersFrozen;

    final Class<?> ownerType;

    private ValueCreator(Class<?> ownerType) {
      this.ownerType = ownerType;
    }

    abstract Object create(String value);

    final Object requireResult(Object result) {
      if (result == null || result.getClass() != ownerType) {
        throw new ForyJsonException(
            "JSON creator must return an exact non-null " + ownerType.getName());
      }
      return result;
    }

    final ForyJsonException invocationFailure(Throwable cause) {
      return new ForyJsonException(
          "Failed to invoke JSON creator for " + ownerType.getName(), cause);
    }

    final ForyJsonException creatorFailure(Throwable cause) {
      if (cause instanceof Error) {
        throw (Error) cause;
      }
      return new ForyJsonException("JSON creator failed for " + ownerType.getName(), cause);
    }

    static ValueCreator forExecutable(
        Class<?> ownerType, Executable executable, GeneratedJsonCodec<?> generatedCodec) {
      if (generatedCodec != null) {
        return new GeneratedCreator(ownerType, generatedCodec);
      }
      if (!AndroidSupport.IS_ANDROID) {
        return new MethodHandleCreator(ownerType, buildInvoker(ownerType, executable));
      }
      executable.setAccessible(true);
      return executable instanceof Constructor
          ? new ConstructorCreator(ownerType, (Constructor<?>) executable)
          : new FactoryCreator(ownerType, (Method) executable);
    }

    private static MethodHandle buildInvoker(Class<?> ownerType, Executable executable) {
      if (GraalvmSupport.isGraalRuntime()) {
        MethodHandle invoker = nativeInvokers.get(executable);
        if (invoker == null) {
          throw new ForyJsonException(
              "Missing Native Image Fory JSON String creator metadata for " + executable);
        }
        return invoker;
      }
      try {
        MethodHandle target =
            executable instanceof Constructor
                ? ReflectionUtils.getCtrHandle(ownerType, executable.getParameterTypes())
                : _JDKAccess._trustedLookup(ownerType).unreflect((Method) executable);
        return target.asType(MethodType.methodType(Object.class, String.class));
      } catch (IllegalAccessException e) {
        throw new ForyJsonException("Cannot access JSON creator for " + ownerType.getName(), e);
      }
    }
  }

  /** Prepares one complete-String creator handle for Native Image runtime metadata construction. */
  @Internal
  public static synchronized void prepareNativeCreator(Class<?> ownerType, Executable executable) {
    if (!GraalvmSupport.isGraalBuildTime() || ValueCreator.nativeInvokersFrozen) {
      throw new IllegalStateException("Fory JSON native String creator cache is not writable");
    }
    ValueCreator.nativeInvokers.putIfAbsent(
        executable, ValueCreator.buildInvoker(ownerType, executable));
  }

  /** Caches one generated one-String constructor invoker for Native Image runtime use. */
  @Internal
  public static synchronized void prepareNativeConstructor(
      Constructor<?> constructor, MethodHandle invoker) {
    if (!GraalvmSupport.isGraalBuildTime() || ValueCreator.nativeInvokersFrozen) {
      throw new IllegalStateException("Fory JSON native String creator cache is not writable");
    }
    ValueCreator.nativeInvokers.putIfAbsent(constructor, invoker);
  }

  /** Freezes all Native Image complete-String creator handles after hosted analysis. */
  @Internal
  public static synchronized void freezeNativeCreators() {
    if (ValueCreator.nativeInvokersFrozen) {
      return;
    }
    ValueCreator.nativeInvokers =
        ValueCreator.nativeInvokers.isEmpty()
            ? Collections.emptyMap()
            : Collections.unmodifiableMap(new HashMap<>(ValueCreator.nativeInvokers));
    ValueCreator.nativeInvokersFrozen = true;
  }

  private static final class GeneratedCreator extends ValueCreator {
    private final GeneratedJsonCodec<?> generatedCodec;

    private GeneratedCreator(Class<?> ownerType, GeneratedJsonCodec<?> generatedCodec) {
      super(ownerType);
      this.generatedCodec = generatedCodec;
    }

    @Override
    Object create(String value) {
      try {
        return requireResult(generatedCodec.newInstance(new Object[] {value}));
      } catch (Throwable cause) {
        throw creatorFailure(cause);
      }
    }
  }

  private static final class MethodHandleCreator extends ValueCreator {
    private final MethodHandle invoker;

    private MethodHandleCreator(Class<?> ownerType, MethodHandle invoker) {
      super(ownerType);
      this.invoker = invoker;
    }

    @Override
    Object create(String value) {
      try {
        Object result = (Object) invoker.invokeExact(value);
        return requireResult(result);
      } catch (Throwable cause) {
        throw creatorFailure(cause);
      }
    }
  }

  private static final class ConstructorCreator extends ValueCreator {
    private final Constructor<?> constructor;

    private ConstructorCreator(Class<?> ownerType, Constructor<?> constructor) {
      super(ownerType);
      this.constructor = constructor;
    }

    @Override
    Object create(String value) {
      try {
        return requireResult(constructor.newInstance(value));
      } catch (InstantiationException | IllegalAccessException e) {
        throw invocationFailure(e);
      } catch (InvocationTargetException e) {
        throw creatorFailure(e.getCause());
      }
    }
  }

  private static final class FactoryCreator extends ValueCreator {
    private final Method factory;

    private FactoryCreator(Class<?> ownerType, Method factory) {
      super(ownerType);
      this.factory = factory;
    }

    @Override
    Object create(String value) {
      try {
        return requireResult(factory.invoke(null, value));
      } catch (IllegalAccessException e) {
        throw invocationFailure(e);
      } catch (InvocationTargetException e) {
        throw creatorFailure(e.getCause());
      }
    }
  }
}
