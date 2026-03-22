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

package org.apache.fory.serializer;

import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Objects;
import org.apache.fory.Fory;
import org.apache.fory.collection.ClassValueCache;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.reflect.ReflectionUtils;
import org.apache.fory.resolver.ClassResolver;
import org.apache.fory.util.Preconditions;
import org.apache.fory.util.function.SerializableFunction;
import org.apache.fory.util.unsafe._JDKAccess;

/**
 * Serializer for java serializable lambda. Use fory to serialize java lambda instead of JDK
 * serialization to avoid serialization captured values in closure using JDK, which is slow and not
 * secure(will work around type white-list).
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class LambdaSerializer extends Serializer {
  public static Class<?> STUB_LAMBDA_CLASS =
      ((SerializableFunction<Integer, Integer>) (x -> x * 2)).getClass();
  private static final ClassValueCache<MethodHandle> writeReplaceMethodCache =
      ClassValueCache.newClassKeySoftCache(32);

  private final MethodHandle writeReplaceHandle;
  private SerializedLambdaSerializer serializedLambdaSerializer;

  public LambdaSerializer(Fory fory, Class<?> cls) {
    super(fory, cls);
    if (cls != ReplaceStub.class) {
      if (!Serializable.class.isAssignableFrom(cls)) {
        String msg =
            String.format(
                "Lambda %s needs to implement %s for serialization",
                cls, Serializable.class.getName());
        throw new UnsupportedOperationException(msg);
      }
      MethodHandle methodHandle = writeReplaceMethodCache.getIfPresent(cls);
      if (methodHandle == null) {
        try {
          MethodHandles.Lookup lookup = _JDKAccess._trustedLookup(cls);
          Object writeReplaceMethod =
              ReflectionUtils.getObjectFieldValue(
                  ObjectStreamClass.lookup(cls), "writeReplaceMethod");
          methodHandle =
              lookup.unreflect(
                  (java.lang.reflect.Method) Objects.requireNonNull(writeReplaceMethod));
          writeReplaceMethodCache.put(cls, methodHandle);
        } catch (Throwable e) {
          throw new RuntimeException(
              String.format("Failed to create writeReplace MethodHandle for %s", cls), e);
        }
      }
      writeReplaceHandle = methodHandle;
    } else {
      writeReplaceHandle = null;
    }
  }

  @Override
  public void write(MemoryBuffer buffer, Object value) {
    assert value.getClass() != ReplaceStub.class;
    try {
      Object replacement = writeReplaceHandle.invoke(value);
      Preconditions.checkArgument(
          SerializedLambdaSerializer.SERIALIZED_LAMBDA.isInstance(replacement));
      getSerializedLambdaSerializer().writeUnresolved(buffer, replacement);
    } catch (Throwable e) {
      throw new RuntimeException("Can't serialize lambda " + value, e);
    }
  }

  @Override
  public Object copy(Object value) {
    try {
      Object replacement = writeReplaceHandle.invoke(value);
      Object newReplacement = getSerializedLambdaSerializer().copyUnresolved(replacement);
      return SerializedLambdaSerializer.readResolve(newReplacement);
    } catch (Throwable e) {
      throw new RuntimeException("Can't copy lambda " + value, e);
    }
  }

  @Override
  public Object read(MemoryBuffer buffer) {
    try {
      Object replacement = getSerializedLambdaSerializer().readUnresolved(buffer);
      return SerializedLambdaSerializer.readResolve(replacement);
    } catch (Throwable e) {
      throw new RuntimeException("Can't deserialize lambda", e);
    }
  }

  private SerializedLambdaSerializer getSerializedLambdaSerializer() {
    SerializedLambdaSerializer serializer = serializedLambdaSerializer;
    if (serializer == null) {
      serializer =
          (SerializedLambdaSerializer)
              ((ClassResolver) fory.getTypeResolver())
                  .getSerializer(SerializedLambdaSerializer.SERIALIZED_LAMBDA);
      serializedLambdaSerializer = serializer;
    }
    return serializer;
  }

  /**
   * Class name of dynamic generated class is not fixed, so we use a stub class to mock dynamic
   * class.
   */
  public static class ReplaceStub {}
}
