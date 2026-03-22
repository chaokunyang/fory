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
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.SerializedLambda;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.fory.Fory;
import org.apache.fory.annotation.Internal;
import org.apache.fory.builder.JITContext;
import org.apache.fory.exception.ForyException;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.reflect.ReflectionUtils;
import org.apache.fory.resolver.ClassResolver;
import org.apache.fory.resolver.TypeInfo;
import org.apache.fory.type.Types;
import org.apache.fory.util.GraalvmSupport;
import org.apache.fory.util.Preconditions;
import org.apache.fory.util.unsafe._JDKAccess;

/**
 * Serializer for {@link SerializedLambda}. It writes the lambda payload using the normal struct
 * serializer path, but applies {@code readResolve} when reading/copying so direct {@code
 * SerializedLambda} serialization preserves JDK semantics.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class SerializedLambdaSerializer extends Serializer {
  static final Class<SerializedLambda> SERIALIZED_LAMBDA = SerializedLambda.class;
  private static final ConcurrentHashMap<Long, Class<? extends Serializer>>
      GRAALVM_DATA_SERIALIZER_CLASSES = new ConcurrentHashMap<>();
  private static final MethodHandle READ_RESOLVE_HANDLE;
  private static final boolean SERIALIZED_LAMBDA_HAS_JDK_WRITE =
      JavaSerializer.getWriteObjectMethod(SERIALIZED_LAMBDA) != null;
  private static final boolean SERIALIZED_LAMBDA_HAS_JDK_READ =
      JavaSerializer.getReadObjectMethod(SERIALIZED_LAMBDA) != null;

  private Serializer dataSerializer;
  private Class<? extends Serializer> dataSerializerClass;

  static {
    try {
      MethodHandles.Lookup lookup = _JDKAccess._trustedLookup(SERIALIZED_LAMBDA);
      Object readResolveMethod =
          ReflectionUtils.getObjectFieldValue(
              ObjectStreamClass.lookup(SERIALIZED_LAMBDA), "readResolveMethod");
      READ_RESOLVE_HANDLE = lookup.unreflect((java.lang.reflect.Method) readResolveMethod);
    } catch (IllegalAccessException e) {
      throw new ForyException(e);
    }
  }

  public SerializedLambdaSerializer(Fory fory, Class<?> cls) {
    super(fory, cls);
    Preconditions.checkArgument(cls == SERIALIZED_LAMBDA);
  }

  @Override
  public void write(MemoryBuffer buffer, Object value) {
    writeUnresolved(buffer, value);
  }

  @Override
  public Object copy(Object value) {
    return readResolve(copyUnresolved(value));
  }

  @Override
  public Object read(MemoryBuffer buffer) {
    return readResolve(readUnresolved(buffer));
  }

  void writeUnresolved(MemoryBuffer buffer, Object value) {
    Preconditions.checkArgument(SERIALIZED_LAMBDA.isInstance(value));
    getDataSerializer().write(buffer, value);
  }

  Object copyUnresolved(Object value) {
    Preconditions.checkArgument(SERIALIZED_LAMBDA.isInstance(value));
    return getDataSerializer().copy(value);
  }

  Object readUnresolved(MemoryBuffer buffer) {
    return getDataSerializer().read(buffer);
  }

  static Object readResolve(Object replacement) {
    try {
      return READ_RESOLVE_HANDLE.invoke(replacement);
    } catch (Throwable e) {
      throw new RuntimeException("Can't deserialize lambda", e);
    }
  }

  @Internal
  public void ensureDataSerializerRegistered() {
    getDataSerializer();
    if (dataSerializerClass != null) {
      registerSerializedLambdaSerializerClass(dataSerializerClass);
    }
  }

  private Serializer getDataSerializer() {
    Serializer dataSerializer = this.dataSerializer;
    if (dataSerializer == null) {
      ClassResolver classResolver = (ClassResolver) fory.getTypeResolver();
      Class<? extends Serializer> serializerClass;
      if (SERIALIZED_LAMBDA_HAS_JDK_WRITE || SERIALIZED_LAMBDA_HAS_JDK_READ) {
        serializerClass = fory.getDefaultJDKStreamSerializerType();
      } else {
        serializerClass = getRegisteredSerializedLambdaSerializerClass();
        if (serializerClass == null) {
          if (GraalvmSupport.isGraalRuntime()) {
            throw new IllegalStateException(
                String.format("Data serializer for %s is not registered", SERIALIZED_LAMBDA));
          }
          serializerClass =
              classResolver.getObjectSerializerClass(
                  SERIALIZED_LAMBDA,
                  new JITContext.SerializerJITCallback<Class<? extends Serializer>>() {
                    @Override
                    public void onSuccess(Class<? extends Serializer> result) {
                      registerSerializedLambdaSerializerClass(result);
                      dataSerializerClass = result;
                      SerializedLambdaSerializer.this.dataSerializer =
                          createDataSerializer(classResolver, result);
                    }

                    @Override
                    public Object id() {
                      return SERIALIZED_LAMBDA;
                    }
                  });
          registerSerializedLambdaSerializerClass(serializerClass);
        }
      }
      dataSerializerClass = serializerClass;
      this.dataSerializer = dataSerializer = createDataSerializer(classResolver, serializerClass);
    }
    return dataSerializer;
  }

  private Serializer createDataSerializer(
      ClassResolver classResolver, Class<? extends Serializer> serializerClass) {
    Serializer prev = classResolver.getSerializer(SERIALIZED_LAMBDA, false);
    Serializer serializer = Serializers.newSerializer(fory, SERIALIZED_LAMBDA, serializerClass);
    classResolver.resetSerializer(SERIALIZED_LAMBDA, prev);
    return serializer;
  }

  private Class<? extends Serializer> getRegisteredSerializedLambdaSerializerClass() {
    if (!GraalvmSupport.IN_GRAALVM_NATIVE_IMAGE) {
      return null;
    }
    return GRAALVM_DATA_SERIALIZER_CLASSES.get(getDataSerializerHash());
  }

  private void registerSerializedLambdaSerializerClass(
      Class<? extends Serializer> serializerClass) {
    if (serializerClass == null || !GraalvmSupport.isGraalBuildtime()) {
      return;
    }
    GRAALVM_DATA_SERIALIZER_CLASSES.put(getDataSerializerHash(), serializerClass);
  }

  private long getDataSerializerHash() {
    TypeInfo typeInfo =
        ((ClassResolver) fory.getTypeResolver()).getTypeInfo(SERIALIZED_LAMBDA, false);
    Preconditions.checkState(typeInfo != null, "SerializedLambda type info should be registered");
    Preconditions.checkState(
        !Types.isUserDefinedType((byte) typeInfo.getTypeId()),
        "SerializedLambda should use an internal type id");
    return (((long) fory.getConfig().hashCode()) << 32) | (typeInfo.getTypeId() & 0xffffffffL);
  }
}
