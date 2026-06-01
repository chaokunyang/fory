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

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaConversionException;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.SerializedLambda;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import org.apache.fory.collection.ClassValueCache;
import org.apache.fory.collection.Tuple2;
import org.apache.fory.platform.GraalvmSupport;
import org.apache.fory.platform.JdkVersion;
import org.apache.fory.type.TypeUtils;
import org.apache.fory.util.ExceptionUtils;
import org.apache.fory.util.Preconditions;
import org.apache.fory.util.function.ToByteFunction;
import org.apache.fory.util.function.ToCharFunction;
import org.apache.fory.util.function.ToFloatFunction;
import org.apache.fory.util.function.ToShortFunction;

/** JDK internals access for the JDK25 multi-release runtime. */
// CHECKSTYLE.OFF:TypeName
public class _JDKAccess {
  // CHECKSTYLE.ON:TypeName
  public static final boolean IS_OPEN_J9;
  public static final boolean JDK_INTERNAL_FIELD_ACCESS;
  public static final boolean JDK_LANG_FIELD_ACCESS;
  public static final boolean JDK_STRING_FIELD_ACCESS;
  public static final boolean JDK_COLLECTION_FIELD_ACCESS;
  public static final boolean JDK_CONCURRENT_FIELD_ACCESS;
  public static final boolean JDK_PROXY_FIELD_ACCESS;

  private static final ClassValueCache<Lookup> lookupCache = ClassValueCache.newClassKeyCache(32);

  public static final boolean STRING_VALUE_FIELD_IS_CHARS;
  public static final boolean STRING_VALUE_FIELD_IS_BYTES;
  public static final boolean STRING_HAS_COUNT_OFFSET;
  public static final long STRING_VALUE_FIELD_OFFSET = -1;
  public static final long STRING_COUNT_FIELD_OFFSET = -1;
  public static final long STRING_OFFSET_FIELD_OFFSET = -1;
  public static final long STRING_CODER_FIELD_OFFSET = -1;
  private static final VarHandle STRING_VALUE_HANDLE;
  private static final VarHandle STRING_CODER_HANDLE;
  private static final VarHandle STRING_COUNT_HANDLE;
  private static final VarHandle STRING_OFFSET_HANDLE;

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

      StringHandles stringHandles = initStringHandles(valueField.getType(), countField, offsetField);

      JDK_LANG_FIELD_ACCESS = canAccess(String.class);
      JDK_STRING_FIELD_ACCESS = stringHandles != null;
      JDK_COLLECTION_FIELD_ACCESS = canAccess("java.util.Collections$SynchronizedCollection");
      JDK_CONCURRENT_FIELD_ACCESS =
          canAccess(ArrayBlockingQueue.class) && canAccess(LinkedBlockingQueue.class);
      JDK_PROXY_FIELD_ACCESS = canAccess(Proxy.class);
      JDK_INTERNAL_FIELD_ACCESS = JDK_STRING_FIELD_ACCESS;

      STRING_VALUE_HANDLE = stringHandles == null ? null : stringHandles.value;
      STRING_CODER_HANDLE = stringHandles == null ? null : stringHandles.coder;
      STRING_COUNT_HANDLE = stringHandles == null ? null : stringHandles.count;
      STRING_OFFSET_HANDLE = stringHandles == null ? null : stringHandles.offset;
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

  private static StringHandles initStringHandles(
      Class<?> stringValueType, Field countField, Field offsetField) {
    try {
      Lookup stringLookup = _Lookup._trustedLookup(String.class);
      return new StringHandles(
          stringLookup.findVarHandle(String.class, "value", stringValueType),
          STRING_VALUE_FIELD_IS_BYTES
              ? stringLookup.findVarHandle(String.class, "coder", byte.class)
              : null,
          countField == null ? null : stringLookup.findVarHandle(String.class, "count", int.class),
          offsetField == null
              ? null
              : stringLookup.findVarHandle(String.class, "offset", int.class));
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static boolean canAccess(String className) {
    try {
      return canAccess(Class.forName(className));
    } catch (Throwable ignored) {
      return false;
    }
  }

  private static boolean canAccess(Class<?> type) {
    try {
      _Lookup._trustedLookup(type);
      return true;
    } catch (Throwable ignored) {
      return false;
    }
  }

  private static class StringHandles {
    private final VarHandle value;
    private final VarHandle coder;
    private final VarHandle count;
    private final VarHandle offset;

    private StringHandles(VarHandle value, VarHandle coder, VarHandle count, VarHandle offset) {
      this.value = value;
      this.coder = coder;
      this.count = count;
      this.offset = offset;
    }
  }

  // The root native-image configuration names this root lazy helper. Keep a same-named JDK25
  // shadow so multi-release class lookup does not fall back to the root Unsafe offset helper.
  private static class StringCoderField {}

  public static Object getStringValue(String value) {
    checkStringAccess("String.value");
    return STRING_VALUE_HANDLE.get(value);
  }

  public static byte getStringCoder(String value) {
    checkStringAccess("String.coder");
    if (STRING_CODER_HANDLE == null) {
      throw new UnsupportedOperationException("String.coder is not available on this JDK");
    }
    return (byte) STRING_CODER_HANDLE.get(value);
  }

  public static int getStringOffset(String value) {
    checkStringAccess("String.offset");
    if (STRING_OFFSET_HANDLE == null) {
      throw new UnsupportedOperationException("String.offset is not available on this JDK");
    }
    return (int) STRING_OFFSET_HANDLE.get(value);
  }

  public static int getStringCount(String value) {
    checkStringAccess("String.count");
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

  public static MethodHandle readResolveHandle(Class<?> cls, Method method)
      throws IllegalAccessException {
    try {
      return _trustedLookup(cls).unreflect(method);
    } catch (IllegalArgumentException e) {
      if (cls != SerializedLambda.class) {
        throw e;
      }
      // JDK25 rejects SerializedLambda itself as a privateLookupIn target. Reflective access still
      // honors the same java.base/java.lang.invoke open requirement and avoids serializer-level
      // versioning.
      try {
        method.setAccessible(true);
      } catch (RuntimeException inaccessible) {
        throw new IllegalStateException(
            "SerializedLambda readResolve requires java.base/java.lang.invoke to be open to "
                + "org.apache.fory.core",
            inaccessible);
      }
      return MethodHandles.lookup().unreflect(method);
    }
  }

  private static final byte LATIN1 = 0;
  private static final Byte LATIN1_BOXED = LATIN1;
  private static final byte UTF16 = 1;
  private static final Byte UTF16_BOXED = UTF16;
  private static final Lookup STRING_LOOK_UP =
      JDK_STRING_FIELD_ACCESS ? _trustedLookup(String.class) : null;
  private static final MethodHandle STRING_ZERO_COPY_CTR_HANDLE =
      JDK_STRING_FIELD_ACCESS ? getJavaStringZeroCopyCtrHandle() : null;
  private static final BiFunction<char[], Boolean, String> CHARS_STRING_ZERO_COPY_CTR =
      JDK_STRING_FIELD_ACCESS ? getCharsStringZeroCopyCtr() : null;
  private static final BiFunction<byte[], Byte, String> BYTES_STRING_ZERO_COPY_CTR =
      JDK_STRING_FIELD_ACCESS ? getBytesStringZeroCopyCtr() : null;
  private static final Function<byte[], String> LATIN_BYTES_STRING_ZERO_COPY_CTR =
      JDK_STRING_FIELD_ACCESS ? getLatinBytesStringZeroCopyCtr() : null;

  public static String newCharsStringZeroCopy(char[] data) {
    if (!JDK_STRING_FIELD_ACCESS) {
      return new String(data);
    }
    if (!STRING_VALUE_FIELD_IS_CHARS) {
      throw new IllegalStateException("String value isn't char[], current java isn't supported");
    }
    if (CHARS_STRING_ZERO_COPY_CTR == null) {
      return newCharsStringByHandle(data);
    }
    return CHARS_STRING_ZERO_COPY_CTR.apply(data, Boolean.TRUE);
  }

  private static String newCharsStringByHandle(char[] data) {
    MethodHandle handle = STRING_ZERO_COPY_CTR_HANDLE;
    if (handle == null) {
      return new String(data);
    }
    try {
      return (String) handle.invokeExact(data, true);
    } catch (Throwable ignored) {
      return new String(data);
    }
  }

  public static String newBytesStringZeroCopy(byte coder, byte[] data) {
    if (!JDK_STRING_FIELD_ACCESS) {
      return newBytesStringSlow(coder, data);
    }
    if (coder == LATIN1) {
      if (LATIN_BYTES_STRING_ZERO_COPY_CTR != null) {
        return LATIN_BYTES_STRING_ZERO_COPY_CTR.apply(data);
      } else if (BYTES_STRING_ZERO_COPY_CTR == null) {
        return newBytesStringByHandle(coder, data);
      }
      return BYTES_STRING_ZERO_COPY_CTR.apply(data, LATIN1_BOXED);
    } else if (coder == UTF16) {
      if (BYTES_STRING_ZERO_COPY_CTR == null) {
        return newBytesStringByHandle(coder, data);
      }
      return BYTES_STRING_ZERO_COPY_CTR.apply(data, UTF16_BOXED);
    } else {
      if (BYTES_STRING_ZERO_COPY_CTR == null) {
        return newBytesStringSlow(coder, data);
      }
      return BYTES_STRING_ZERO_COPY_CTR.apply(data, coder);
    }
  }

  private static String newBytesStringSlow(byte coder, byte[] data) {
    if (coder == LATIN1) {
      return new String(data, StandardCharsets.ISO_8859_1);
    } else if (coder == UTF16) {
      char[] chars = new char[data.length >> 1];
      for (int i = 0, j = 0; i < data.length; i += 2) {
        chars[j++] = (char) ((data[i] & 0xff) | ((data[i + 1] & 0xff) << 8));
      }
      return new String(chars);
    } else {
      return new String(data, StandardCharsets.UTF_8);
    }
  }

  private static String newBytesStringByHandle(byte coder, byte[] data) {
    MethodHandle handle = STRING_ZERO_COPY_CTR_HANDLE;
    if (handle == null) {
      return newBytesStringSlow(coder, data);
    }
    try {
      return (String) handle.invokeExact(data, coder);
    } catch (Throwable ignored) {
      return newBytesStringSlow(coder, data);
    }
  }

  private static BiFunction<char[], Boolean, String> getCharsStringZeroCopyCtr() {
    if (!STRING_VALUE_FIELD_IS_CHARS) {
      return null;
    }
    MethodHandle handle = STRING_ZERO_COPY_CTR_HANDLE;
    if (handle == null) {
      return null;
    }
    try {
      CallSite callSite =
          LambdaMetafactory.metafactory(
              STRING_LOOK_UP,
              "apply",
              MethodType.methodType(BiFunction.class),
              handle.type().generic(),
              handle,
              handle.type());
      return (BiFunction) callSite.getTarget().invokeExact();
    } catch (Throwable e) {
      return null;
    }
  }

  private static BiFunction<byte[], Byte, String> getBytesStringZeroCopyCtr() {
    if (!STRING_VALUE_FIELD_IS_BYTES) {
      return null;
    }
    MethodHandle handle = STRING_ZERO_COPY_CTR_HANDLE;
    if (handle == null) {
      return null;
    }
    try {
      MethodType instantiatedMethodType =
          MethodType.methodType(handle.type().returnType(), new Class[] {byte[].class, Byte.class});
      CallSite callSite =
          LambdaMetafactory.metafactory(
              STRING_LOOK_UP,
              "apply",
              MethodType.methodType(BiFunction.class),
              handle.type().generic(),
              handle,
              instantiatedMethodType);
      return (BiFunction) callSite.getTarget().invokeExact();
    } catch (Throwable e) {
      return null;
    }
  }

  private static Function<byte[], String> getLatinBytesStringZeroCopyCtr() {
    if (!STRING_VALUE_FIELD_IS_BYTES || STRING_LOOK_UP == null) {
      return null;
    }
    try {
      Class<?> clazz = Class.forName("java.lang.StringCoding");
      Lookup caller = STRING_LOOK_UP.in(clazz);
      MethodHandle handle =
          caller.findStatic(
              clazz, "newStringLatin1", MethodType.methodType(String.class, byte[].class));
      return makeFunction(caller, handle, Function.class);
    } catch (Throwable e) {
      return null;
    }
  }

  private static MethodHandle getJavaStringZeroCopyCtrHandle() {
    Preconditions.checkArgument(JdkVersion.MAJOR_VERSION >= 8);
    if (STRING_LOOK_UP == null) {
      return null;
    }
    try {
      if (STRING_VALUE_FIELD_IS_CHARS) {
        return STRING_LOOK_UP.findConstructor(
            String.class, MethodType.methodType(void.class, char[].class, boolean.class));
      } else {
        return STRING_LOOK_UP.findConstructor(
            String.class, MethodType.methodType(void.class, byte[].class, byte.class));
      }
    } catch (Exception e) {
      return null;
    }
  }

  private static void checkStringAccess(String target) {
    if (!JDK_STRING_FIELD_ACCESS) {
      throw new UnsupportedOperationException(
          target
              + " private access is unavailable; open java.base/java.lang.invoke to "
              + "org.apache.fory.core");
    }
  }

  private static final ClassValueCache<SerializationMethods> serializationMethodsCache =
      ClassValueCache.newClassKeyCache(32);

  private static final class SerializationMethods {
    private final Method writeObject;
    private final Method readObject;
    private final Method readObjectNoData;
    private final Method writeReplace;
    private final Method readResolve;

    private SerializationMethods(Class<?> type) {
      writeObject =
          getPrivateMethod(type, "writeObject", void.class, ObjectOutputStream.class);
      readObject = getPrivateMethod(type, "readObject", void.class, ObjectInputStream.class);
      readObjectNoData = getPrivateMethod(type, "readObjectNoData", void.class);
      writeReplace = getInheritableObjectMethod(type, "writeReplace");
      readResolve = getInheritableObjectMethod(type, "readResolve");
    }
  }

  private static SerializationMethods serializationMethods(Class<?> type) {
    return serializationMethodsCache.get(type, () -> new SerializationMethods(type));
  }

  private static Method getPrivateMethod(
      Class<?> type, String methodName, Class<?> returnType, Class<?>... parameterTypes) {
    try {
      Method method = type.getDeclaredMethod(methodName, parameterTypes);
      int modifiers = method.getModifiers();
      if (Modifier.isPrivate(modifiers)
          && !Modifier.isStatic(modifiers)
          && method.getReturnType() == returnType) {
        return method;
      }
    } catch (NoSuchMethodException | SecurityException e) {
      ExceptionUtils.ignore(e);
    }
    return null;
  }

  private static Method getInheritableObjectMethod(Class<?> type, String methodName) {
    Class<?> cls = type;
    while (cls != null) {
      try {
        Method method = cls.getDeclaredMethod(methodName);
        int modifiers = method.getModifiers();
        if (!Modifier.isStatic(modifiers) && method.getReturnType() == Object.class) {
          return method;
        }
        return null;
      } catch (NoSuchMethodException e) {
        ExceptionUtils.ignore(e);
      } catch (SecurityException e) {
        return null;
      }
      cls = cls.getSuperclass();
    }
    return null;
  }

  public static Method getSerializationWriteObjectMethod(Class<?> type) {
    return serializationMethods(type).writeObject;
  }

  public static Method getSerializationReadObjectMethod(Class<?> type) {
    return serializationMethods(type).readObject;
  }

  public static Method getSerializationReadObjectNoDataMethod(Class<?> type) {
    return serializationMethods(type).readObjectNoData;
  }

  public static MethodHandle getSerializationDefaultReadObjectHandle(Class<?> type) {
    return null;
  }

  public static Method getSerializationWriteReplaceMethod(Class<?> type) {
    return serializationMethods(type).writeReplace;
  }

  public static Method getSerializationReadResolveMethod(Class<?> type) {
    return serializationMethods(type).readResolve;
  }

  public static boolean isSerializationHookLookupAvailable() {
    return true;
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
    } catch (LambdaConversionException e) {
      return makeGetterFallback(handle, returnType);
    } catch (ClassNotFoundException | NoClassDefFoundError e) {
      return makeGetterFunction(lookup, handle, Object.class);
    } catch (Throwable e) {
      throw ExceptionUtils.throwException(e);
    }
  }

  private static Object makeGetterFallback(MethodHandle handle, Class<?> returnType) {
    if (returnType == boolean.class) {
      return (Predicate<Object>)
          value -> {
            try {
              return (boolean) handle.invoke(value);
            } catch (Throwable e) {
              throw ExceptionUtils.throwException(e);
            }
          };
    } else if (returnType == byte.class) {
      return (ToByteFunction<Object>)
          value -> {
            try {
              return (byte) handle.invoke(value);
            } catch (Throwable e) {
              throw ExceptionUtils.throwException(e);
            }
          };
    } else if (returnType == char.class) {
      return (ToCharFunction<Object>)
          value -> {
            try {
              return (char) handle.invoke(value);
            } catch (Throwable e) {
              throw ExceptionUtils.throwException(e);
            }
          };
    } else if (returnType == short.class) {
      return (ToShortFunction<Object>)
          value -> {
            try {
              return (short) handle.invoke(value);
            } catch (Throwable e) {
              throw ExceptionUtils.throwException(e);
            }
          };
    } else if (returnType == int.class) {
      return (ToIntFunction<Object>)
          value -> {
            try {
              return (int) handle.invoke(value);
            } catch (Throwable e) {
              throw ExceptionUtils.throwException(e);
            }
          };
    } else if (returnType == long.class) {
      return (ToLongFunction<Object>)
          value -> {
            try {
              return (long) handle.invoke(value);
            } catch (Throwable e) {
              throw ExceptionUtils.throwException(e);
            }
          };
    } else if (returnType == float.class) {
      return (ToFloatFunction<Object>)
          value -> {
            try {
              return (float) handle.invoke(value);
            } catch (Throwable e) {
              throw ExceptionUtils.throwException(e);
            }
          };
    } else if (returnType == double.class) {
      return (ToDoubleFunction<Object>)
          value -> {
            try {
              return (double) handle.invoke(value);
            } catch (Throwable e) {
              throw ExceptionUtils.throwException(e);
            }
          };
    }
    return (Function<Object, Object>)
        value -> {
          try {
            return handle.invoke(value);
          } catch (Throwable e) {
            throw ExceptionUtils.throwException(e);
          }
        };
  }

  public static Object getModule(Class<?> cls) {
    Preconditions.checkArgument(JdkVersion.MAJOR_VERSION >= 9);
    return cls.getModule();
  }

  public static Object addReads(Object thisModule, Object otherModule) {
    Preconditions.checkArgument(JdkVersion.MAJOR_VERSION >= 9);
    return ((Module) thisModule).addReads((Module) otherModule);
  }

  public static Lookup privateLookupIn(Class<?> targetClass, Lookup caller) {
    return _Lookup.privateLookupIn(targetClass, caller);
  }
}
