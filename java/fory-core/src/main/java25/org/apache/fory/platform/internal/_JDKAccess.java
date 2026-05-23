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

package org.apache.fory.platform.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import org.apache.fory.collection.ClassValueCache;
import org.apache.fory.collection.Tuple2;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.platform.GraalvmSupport;
import org.apache.fory.platform.JdkVersion;
import org.apache.fory.type.TypeUtils;
import org.apache.fory.util.ExceptionUtils;
import org.apache.fory.util.Preconditions;
import org.apache.fory.util.function.ToByteFunction;
import org.apache.fory.util.function.ToCharFunction;
import org.apache.fory.util.function.ToFloatFunction;
import org.apache.fory.util.function.ToShortFunction;
import sun.misc.Unsafe;

/** JDK internals access for the JDK25 multi-release runtime. */
// CHECKSTYLE.OFF:TypeName
public class _JDKAccess {
  // CHECKSTYLE.ON:TypeName
  public static final boolean IS_OPEN_J9;
  public static final Unsafe UNSAFE = null;
  public static final boolean JDK_INTERNAL_FIELD_ACCESS;
  public static final Class<?> _INNER_UNSAFE_CLASS = null;
  public static final Object _INNER_UNSAFE = null;

  private static final ClassValueCache<Lookup> lookupCache = ClassValueCache.newClassKeyCache(32);

  public static final boolean STRING_VALUE_FIELD_IS_CHARS;
  public static final boolean STRING_VALUE_FIELD_IS_BYTES;
  public static final boolean STRING_HAS_COUNT_OFFSET;
  private static final long STRING_VALUE_FIELD_OFFSET = -1;
  private static final long STRING_COUNT_FIELD_OFFSET = -1;
  private static final long STRING_OFFSET_FIELD_OFFSET = -1;
  private static final VarHandle STRING_VALUE_HANDLE;
  private static final VarHandle STRING_CODER_HANDLE;
  private static final VarHandle STRING_COUNT_HANDLE;
  private static final VarHandle STRING_OFFSET_HANDLE;
  private static final VarHandle BAS_BUF_HANDLE;
  private static final VarHandle BAS_COUNT_HANDLE;
  private static final VarHandle BIS_BUF_HANDLE;
  private static final VarHandle BIS_POS_HANDLE;
  private static final VarHandle BIS_COUNT_HANDLE;

  static {
    String jmvName = System.getProperty("java.vm.name", "");
    IS_OPEN_J9 = jmvName.contains("OpenJ9");
  }

  static {
    try {
      Field valueField = String.class.getDeclaredField("value");
      STRING_VALUE_FIELD_IS_CHARS = valueField.getType() == char[].class;
      STRING_VALUE_FIELD_IS_BYTES = valueField.getType() == byte[].class;
      Field countField = getStringFieldNullable("count");
      Field offsetField = getStringFieldNullable("offset");
      if (countField != null || offsetField != null) {
        Preconditions.checkArgument(
            countField != null && offsetField != null, "Current jdk not supported");
        Preconditions.checkArgument(
            countField.getType() == int.class && offsetField.getType() == int.class,
            "Current jdk not supported");
        STRING_HAS_COUNT_OFFSET = true;
      } else {
        STRING_HAS_COUNT_OFFSET = false;
      }
      FieldHandles handles = initFieldHandles(valueField.getType(), countField, offsetField);
      JDK_INTERNAL_FIELD_ACCESS = handles != null;
      STRING_VALUE_HANDLE = handles == null ? null : handles.stringValue;
      STRING_CODER_HANDLE = handles == null ? null : handles.stringCoder;
      STRING_COUNT_HANDLE = handles == null ? null : handles.stringCount;
      STRING_OFFSET_HANDLE = handles == null ? null : handles.stringOffset;
      BAS_BUF_HANDLE = handles == null ? null : handles.basBuf;
      BAS_COUNT_HANDLE = handles == null ? null : handles.basCount;
      BIS_BUF_HANDLE = handles == null ? null : handles.bisBuf;
      BIS_POS_HANDLE = handles == null ? null : handles.bisPos;
      BIS_COUNT_HANDLE = handles == null ? null : handles.bisCount;
    } catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
  }

  private static Field getStringFieldNullable(String fieldName) {
    try {
      return String.class.getDeclaredField(fieldName);
    } catch (NoSuchFieldException e) {
      return null;
    }
  }

  private static FieldHandles initFieldHandles(
      Class<?> stringValueType, Field countField, Field offsetField) {
    try {
      Lookup stringLookup = MethodHandles.privateLookupIn(String.class, MethodHandles.lookup());
      VarHandle stringValue = stringLookup.findVarHandle(String.class, "value", stringValueType);
      VarHandle stringCoder =
          STRING_VALUE_FIELD_IS_BYTES
              ? stringLookup.findVarHandle(String.class, "coder", byte.class)
              : null;
      VarHandle stringCount =
          countField == null ? null : stringLookup.findVarHandle(String.class, "count", int.class);
      VarHandle stringOffset =
          offsetField == null
              ? null
              : stringLookup.findVarHandle(String.class, "offset", int.class);
      Lookup basLookup =
          MethodHandles.privateLookupIn(ByteArrayOutputStream.class, MethodHandles.lookup());
      Lookup bisLookup =
          MethodHandles.privateLookupIn(ByteArrayInputStream.class, MethodHandles.lookup());
      return new FieldHandles(
          stringValue,
          stringCoder,
          stringCount,
          stringOffset,
          basLookup.findVarHandle(ByteArrayOutputStream.class, "buf", byte[].class),
          basLookup.findVarHandle(ByteArrayOutputStream.class, "count", int.class),
          bisLookup.findVarHandle(ByteArrayInputStream.class, "buf", byte[].class),
          bisLookup.findVarHandle(ByteArrayInputStream.class, "pos", int.class),
          bisLookup.findVarHandle(ByteArrayInputStream.class, "count", int.class));
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static class FieldHandles {
    private final VarHandle stringValue;
    private final VarHandle stringCoder;
    private final VarHandle stringCount;
    private final VarHandle stringOffset;
    private final VarHandle basBuf;
    private final VarHandle basCount;
    private final VarHandle bisBuf;
    private final VarHandle bisPos;
    private final VarHandle bisCount;

    private FieldHandles(
        VarHandle stringValue,
        VarHandle stringCoder,
        VarHandle stringCount,
        VarHandle stringOffset,
        VarHandle basBuf,
        VarHandle basCount,
        VarHandle bisBuf,
        VarHandle bisPos,
        VarHandle bisCount) {
      this.stringValue = stringValue;
      this.stringCoder = stringCoder;
      this.stringCount = stringCount;
      this.stringOffset = stringOffset;
      this.basBuf = basBuf;
      this.basCount = basCount;
      this.bisBuf = bisBuf;
      this.bisPos = bisPos;
      this.bisCount = bisCount;
    }
  }

  public static Object getStringValue(String value) {
    checkInternalFieldAccess("String.value");
    return STRING_VALUE_HANDLE.get(value);
  }

  public static byte getStringCoder(String value) {
    checkInternalFieldAccess("String.coder");
    if (STRING_CODER_HANDLE == null) {
      throw new UnsupportedOperationException("String.coder is not available on this JDK");
    }
    return (byte) STRING_CODER_HANDLE.get(value);
  }

  public static int getStringOffset(String value) {
    checkInternalFieldAccess("String.offset");
    if (STRING_OFFSET_HANDLE == null) {
      throw new UnsupportedOperationException("String.offset is not available on this JDK");
    }
    return (int) STRING_OFFSET_HANDLE.get(value);
  }

  public static int getStringCount(String value) {
    checkInternalFieldAccess("String.count");
    if (STRING_COUNT_HANDLE == null) {
      throw new UnsupportedOperationException("String.count is not available on this JDK");
    }
    return (int) STRING_COUNT_HANDLE.get(value);
  }

  // CHECKSTYLE.OFF:MethodName

  public static Lookup _trustedLookup(Class<?> objectClass) {
    // CHECKSTYLE.ON:MethodName
    if (GraalvmSupport.isGraalBuildTime()) {
      return _Lookup._trustedLookup(objectClass);
    }
    return lookupCache.get(objectClass, () -> _Lookup._trustedLookup(objectClass));
  }

  public static void wrap(ByteArrayOutputStream stream, MemoryBuffer buffer) {
    Preconditions.checkNotNull(stream);
    checkInternalFieldAccess("ByteArrayOutputStream");
    byte[] buf = (byte[]) BAS_BUF_HANDLE.get(stream);
    int count = (int) BAS_COUNT_HANDLE.get(stream);
    buffer.pointTo(buf, 0, buf.length);
    buffer.writerIndex(count);
  }

  public static void wrap(MemoryBuffer buffer, ByteArrayOutputStream stream) {
    Preconditions.checkNotNull(stream);
    checkInternalFieldAccess("ByteArrayOutputStream");
    byte[] bytes = buffer.getHeapMemory();
    Preconditions.checkNotNull(bytes);
    BAS_BUF_HANDLE.set(stream, bytes);
    BAS_COUNT_HANDLE.set(stream, buffer.writerIndex());
  }

  public static void wrap(ByteArrayInputStream stream, MemoryBuffer buffer) {
    Preconditions.checkNotNull(stream);
    checkInternalFieldAccess("ByteArrayInputStream");
    byte[] buf = (byte[]) BIS_BUF_HANDLE.get(stream);
    int count = (int) BIS_COUNT_HANDLE.get(stream);
    int pos = (int) BIS_POS_HANDLE.get(stream);
    buffer.pointTo(buf, 0, count);
    buffer.readerIndex(pos);
  }

  private static void checkInternalFieldAccess(String target) {
    if (!JDK_INTERNAL_FIELD_ACCESS) {
      throw new UnsupportedOperationException(
          target
              + " private access is unavailable; open java.base/java.lang and java.base/java.io "
              + "to org.apache.fory.core");
    }
  }

  public static <T> T tryMakeFunction(
      Lookup lookup, MethodHandle handle, Class<T> functionInterface) {
    try {
      return makeFunction(lookup, handle, functionInterface);
    } catch (Throwable e) {
      ExceptionUtils.ignore(e);
      throw new IllegalStateException();
    }
  }

  private static final MethodType jdkFunctionMethodType =
      MethodType.methodType(Object.class, Object.class);

  @SuppressWarnings("unchecked")
  public static <T, R> Function<T, R> makeJDKFunction(Lookup lookup, MethodHandle handle) {
    return makeJDKFunction(lookup, handle, jdkFunctionMethodType);
  }

  @SuppressWarnings("unchecked")
  public static <T, R> Function<T, R> makeJDKFunction(
      Lookup lookup, MethodHandle handle, MethodType methodType) {
    try {
      CallSite callSite =
          LambdaMetafactory.metafactory(
              lookup,
              "apply",
              MethodType.methodType(Function.class),
              methodType,
              handle,
              boxedMethodType(handle.type()));
      return (Function<T, R>) callSite.getTarget().invokeExact();
    } catch (Throwable e) {
      throw ExceptionUtils.throwException(e);
    }
  }

  private static final MethodType jdkConsumerMethodType =
      MethodType.methodType(void.class, Object.class);

  @SuppressWarnings("unchecked")
  public static <T> Consumer<T> makeJDKConsumer(Lookup lookup, MethodHandle handle) {
    try {
      CallSite callSite =
          LambdaMetafactory.metafactory(
              lookup,
              "accept",
              MethodType.methodType(Consumer.class),
              jdkConsumerMethodType,
              handle,
              boxedMethodType(handle.type()));
      return (Consumer<T>) callSite.getTarget().invokeExact();
    } catch (Throwable e) {
      throw ExceptionUtils.throwException(e);
    }
  }

  private static final MethodType jdkBiConsumerMethodType =
      MethodType.methodType(void.class, Object.class, Object.class);

  @SuppressWarnings("unchecked")
  public static <T, U> BiConsumer<T, U> makeJDKBiConsumer(Lookup lookup, MethodHandle handle) {
    try {
      CallSite callSite =
          LambdaMetafactory.metafactory(
              lookup,
              "accept",
              MethodType.methodType(BiConsumer.class),
              jdkBiConsumerMethodType,
              handle,
              boxedMethodType(handle.type()));
      return (BiConsumer<T, U>) callSite.getTarget().invokeExact();
    } catch (Throwable e) {
      throw ExceptionUtils.throwException(e);
    }
  }

  private static MethodType boxedMethodType(MethodType methodType) {
    Class<?>[] paramTypes = new Class[methodType.parameterCount()];
    for (int i = 0; i < paramTypes.length; i++) {
      Class<?> t = methodType.parameterType(i);
      if (t.isPrimitive()) {
        t = TypeUtils.wrap(t);
      }
      paramTypes[i] = t;
    }
    return MethodType.methodType(methodType.returnType(), paramTypes);
  }

  @SuppressWarnings("unchecked")
  public static <T> T makeFunction(Lookup lookup, MethodHandle handle, Method methodToImpl) {
    MethodType instantiatedMethodType = boxedMethodType(handle.type());
    MethodType methodToImplType =
        MethodType.methodType(methodToImpl.getReturnType(), methodToImpl.getParameterTypes());
    try {
      CallSite callSite =
          LambdaMetafactory.metafactory(
              lookup,
              methodToImpl.getName(),
              MethodType.methodType(methodToImpl.getDeclaringClass()),
              methodToImplType,
              handle,
              instantiatedMethodType);
      return (T) callSite.getTarget().invokeExact();
    } catch (Throwable e) {
      throw ExceptionUtils.throwException(e);
    }
  }

  public static <T> T makeFunction(Lookup lookup, MethodHandle handle, Class<T> functionInterface) {
    String invokedName = "apply";
    try {
      Method method = null;
      Method[] methods = functionInterface.getMethods();
      for (Method interfaceMethod : methods) {
        if (interfaceMethod.getName().equals(invokedName)) {
          method = interfaceMethod;
          break;
        }
      }
      if (method == null) {
        Preconditions.checkArgument(methods.length == 1);
        method = methods[0];
        invokedName = method.getName();
      }
      MethodType interfaceType =
          MethodType.methodType(method.getReturnType(), method.getParameterTypes());
      CallSite callSite =
          LambdaMetafactory.metafactory(
              lookup,
              invokedName,
              MethodType.methodType(functionInterface),
              interfaceType,
              handle,
              interfaceType);
      return (T) callSite.getTarget().invoke();
    } catch (Throwable e) {
      throw ExceptionUtils.throwException(e);
    }
  }

  private static final Map<Class<?>, Tuple2<Class<?>, String>> methodMap = new HashMap<>();

  static {
    methodMap.put(boolean.class, Tuple2.of(Predicate.class, "test"));
    methodMap.put(byte.class, Tuple2.of(ToByteFunction.class, "applyAsByte"));
    methodMap.put(char.class, Tuple2.of(ToCharFunction.class, "applyAsChar"));
    methodMap.put(short.class, Tuple2.of(ToShortFunction.class, "applyAsShort"));
    methodMap.put(int.class, Tuple2.of(ToIntFunction.class, "applyAsInt"));
    methodMap.put(long.class, Tuple2.of(ToLongFunction.class, "applyAsLong"));
    methodMap.put(float.class, Tuple2.of(ToFloatFunction.class, "applyAsFloat"));
    methodMap.put(double.class, Tuple2.of(ToDoubleFunction.class, "applyAsDouble"));
  }

  public static Tuple2<Class<?>, String> getterMethodInfo(Class<?> type) {
    Tuple2<Class<?>, String> info = methodMap.get(type);
    if (info == null) {
      return Tuple2.of(Function.class, "apply");
    }
    return info;
  }

  public static Object makeGetterFunction(
      MethodHandles.Lookup lookup, MethodHandle handle, Class<?> returnType) {
    Tuple2<Class<?>, String> methodInfo = methodMap.get(returnType);
    MethodType factoryType;
    if (methodInfo == null) {
      methodInfo = Tuple2.of(Function.class, "apply");
      factoryType = jdkFunctionMethodType;
    } else {
      factoryType = MethodType.methodType(returnType, Object.class);
    }
    try {
      CallSite callSite =
          LambdaMetafactory.metafactory(
              lookup,
              methodInfo.f1,
              MethodType.methodType(methodInfo.f0),
              factoryType,
              handle,
              handle.type());
      return callSite.getTarget().invoke();
    } catch (ClassNotFoundException | NoClassDefFoundError e) {
      return makeGetterFunction(lookup, handle, Object.class);
    } catch (Throwable e) {
      throw ExceptionUtils.throwException(e);
    }
  }

  public static Object getModule(Class<?> cls) {
    Preconditions.checkArgument(JdkVersion.MAJOR_VERSION >= 9);
    return cls.getModule();
  }

  public static Object addReads(Object thisModule, Object otherModule) {
    Preconditions.checkArgument(JdkVersion.MAJOR_VERSION >= 9);
    return ((Module) thisModule).addReads((Module) otherModule);
  }
}
