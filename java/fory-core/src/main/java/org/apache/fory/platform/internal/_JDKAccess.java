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
import java.io.ObjectStreamClass;
import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
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
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.platform.AndroidSupport;
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

/** Unsafe JDK utils. */
// CHECKSTYLE.OFF:TypeName
public class _JDKAccess {
  // CHECKSTYLE.ON:TypeName
  public static final boolean IS_OPEN_J9;
  public static final Unsafe UNSAFE;
  // Root classes use Unsafe for JDK internal fields. A JDK25 multi-release _JDKAccess must keep
  // this API surface and implement supported cases with VarHandle, or set this false so callers
  // choose public fallbacks.
  public static final boolean JDK_INTERNAL_FIELD_ACCESS;
  public static final boolean JDK_LANG_FIELD_ACCESS;
  public static final boolean JDK_STRING_FIELD_ACCESS;
  public static final boolean JDK_BYTE_ARRAY_STREAM_FIELD_ACCESS;
  public static final boolean JDK_OBJECT_STREAM_FIELD_ACCESS;
  public static final boolean JDK_COLLECTION_FIELD_ACCESS;
  public static final boolean JDK_CONCURRENT_FIELD_ACCESS;
  public static final boolean JDK_PROXY_FIELD_ACCESS;
  public static final Class<?> _INNER_UNSAFE_CLASS;
  public static final Object _INNER_UNSAFE;

  static {
    String jmvName = System.getProperty("java.vm.name", "");
    IS_OPEN_J9 = jmvName.contains("OpenJ9");
    Unsafe unsafe;
    try {
      Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
      unsafeField.setAccessible(true);
      unsafe = (Unsafe) unsafeField.get(null);
    } catch (Throwable cause) {
      throw new UnsupportedOperationException("Unsafe is not supported in this platform.");
    }
    UNSAFE = unsafe;
    if (AndroidSupport.IS_ANDROID) {
      JDK_INTERNAL_FIELD_ACCESS = false;
      JDK_LANG_FIELD_ACCESS = false;
      JDK_STRING_FIELD_ACCESS = false;
      JDK_BYTE_ARRAY_STREAM_FIELD_ACCESS = false;
      JDK_OBJECT_STREAM_FIELD_ACCESS = false;
      JDK_COLLECTION_FIELD_ACCESS = false;
      JDK_CONCURRENT_FIELD_ACCESS = false;
      JDK_PROXY_FIELD_ACCESS = false;
    } else {
      JDK_INTERNAL_FIELD_ACCESS = true;
      JDK_LANG_FIELD_ACCESS = true;
      JDK_STRING_FIELD_ACCESS = true;
      JDK_BYTE_ARRAY_STREAM_FIELD_ACCESS = true;
      JDK_OBJECT_STREAM_FIELD_ACCESS = true;
      JDK_COLLECTION_FIELD_ACCESS = true;
      JDK_CONCURRENT_FIELD_ACCESS = true;
      JDK_PROXY_FIELD_ACCESS = true;
    }
    Object innerUnsafe = null;
    Class<?> innerUnsafeClass = null;
    if (!AndroidSupport.IS_ANDROID && JdkVersion.MAJOR_VERSION >= 11) {
      try {
        Field theInternalUnsafeField = Unsafe.class.getDeclaredField("theInternalUnsafe");
        theInternalUnsafeField.setAccessible(true);
        innerUnsafe = theInternalUnsafeField.get(null);
        innerUnsafeClass = innerUnsafe.getClass();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    _INNER_UNSAFE = innerUnsafe;
    _INNER_UNSAFE_CLASS = innerUnsafeClass;
  }

  public static Unsafe unsafe() {
    return UNSAFE;
  }

  private static final ClassValueCache<Lookup> lookupCache = ClassValueCache.newClassKeyCache(32);

  public static final boolean STRING_VALUE_FIELD_IS_CHARS;
  public static final boolean STRING_VALUE_FIELD_IS_BYTES;
  public static final boolean STRING_HAS_COUNT_OFFSET;
  public static final long STRING_VALUE_FIELD_OFFSET;
  public static final long STRING_COUNT_FIELD_OFFSET;
  public static final long STRING_OFFSET_FIELD_OFFSET;

  static {
    if (AndroidSupport.IS_ANDROID) {
      STRING_VALUE_FIELD_IS_CHARS = false;
      STRING_VALUE_FIELD_IS_BYTES = false;
      STRING_VALUE_FIELD_OFFSET = -1;
      STRING_HAS_COUNT_OFFSET = false;
      STRING_COUNT_FIELD_OFFSET = -1;
      STRING_OFFSET_FIELD_OFFSET = -1;
    } else {
      try {
        Field valueField = String.class.getDeclaredField("value");
        STRING_VALUE_FIELD_IS_CHARS = valueField.getType() == char[].class;
        STRING_VALUE_FIELD_IS_BYTES = valueField.getType() == byte[].class;
        STRING_VALUE_FIELD_OFFSET = UNSAFE.objectFieldOffset(valueField);
        Field countField = getStringFieldNullable("count");
        Field offsetField = getStringFieldNullable("offset");
        if (countField != null || offsetField != null) {
          Preconditions.checkArgument(
              countField != null && offsetField != null, "Current jdk not supported");
          Preconditions.checkArgument(
              countField.getType() == int.class && offsetField.getType() == int.class,
              "Current jdk not supported");
          STRING_HAS_COUNT_OFFSET = true;
          STRING_COUNT_FIELD_OFFSET = UNSAFE.objectFieldOffset(countField);
          STRING_OFFSET_FIELD_OFFSET = UNSAFE.objectFieldOffset(offsetField);
        } else {
          STRING_HAS_COUNT_OFFSET = false;
          STRING_COUNT_FIELD_OFFSET = -1;
          STRING_OFFSET_FIELD_OFFSET = -1;
        }
      } catch (NoSuchFieldException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static Field getStringFieldNullable(String fieldName) {
    try {
      return String.class.getDeclaredField(fieldName);
    } catch (NoSuchFieldException e) {
      return null;
    }
  }

  private static class StringCoderField {
    private static final long OFFSET;

    static {
      try {
        OFFSET = UNSAFE.objectFieldOffset(String.class.getDeclaredField("coder"));
      } catch (NoSuchFieldException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public static final long STRING_CODER_FIELD_OFFSET =
      STRING_VALUE_FIELD_IS_BYTES ? StringCoderField.OFFSET : -1;

  public static Object getStringValue(String value) {
    return UNSAFE.getObject(value, STRING_VALUE_FIELD_OFFSET);
  }

  public static byte getStringCoder(String value) {
    return UNSAFE.getByte(value, STRING_CODER_FIELD_OFFSET);
  }

  public static int getStringOffset(String value) {
    return UNSAFE.getInt(value, STRING_OFFSET_FIELD_OFFSET);
  }

  public static int getStringCount(String value) {
    return UNSAFE.getInt(value, STRING_COUNT_FIELD_OFFSET);
  }

  // CHECKSTYLE.OFF:MethodName

  public static Lookup _trustedLookup(Class<?> objectClass) {
    // CHECKSTYLE.ON:MethodName
    if (GraalvmSupport.isGraalBuildTime()) {
      // Lookup will init `java.io.FilePermission`,which is not allowed at graalvm build time
      // as a reachable object.
      return _Lookup._trustedLookup(objectClass);
    }
    return lookupCache.get(objectClass, () -> _Lookup._trustedLookup(objectClass));
  }

  public static MethodHandle readResolveHandle(Class<?> cls, Method method)
      throws IllegalAccessException {
    return _trustedLookup(cls).unreflect(method);
  }

  private static final byte LATIN1 = 0;
  private static final Byte LATIN1_BOXED = LATIN1;
  private static final byte UTF16 = 1;
  private static final Byte UTF16_BOXED = UTF16;
  private static final MethodHandles.Lookup STRING_LOOK_UP =
      JDK_INTERNAL_FIELD_ACCESS ? _trustedLookup(String.class) : null;
  private static final BiFunction<char[], Boolean, String> CHARS_STRING_ZERO_COPY_CTR =
      JDK_INTERNAL_FIELD_ACCESS ? getCharsStringZeroCopyCtr() : null;
  private static final BiFunction<byte[], Byte, String> BYTES_STRING_ZERO_COPY_CTR =
      JDK_INTERNAL_FIELD_ACCESS ? getBytesStringZeroCopyCtr() : null;
  private static final Function<byte[], String> LATIN_BYTES_STRING_ZERO_COPY_CTR =
      JDK_INTERNAL_FIELD_ACCESS ? getLatinBytesStringZeroCopyCtr() : null;

  public static String newCharsStringZeroCopy(char[] data) {
    if (!JDK_INTERNAL_FIELD_ACCESS) {
      return new String(data);
    }
    if (!STRING_VALUE_FIELD_IS_CHARS) {
      throw new IllegalStateException("String value isn't char[], current java isn't supported");
    }
    return CHARS_STRING_ZERO_COPY_CTR.apply(data, Boolean.TRUE);
  }

  public static String newBytesStringZeroCopy(byte coder, byte[] data) {
    if (!JDK_INTERNAL_FIELD_ACCESS) {
      return newBytesStringSlow(coder, data);
    }
    if (coder == LATIN1) {
      if (LATIN_BYTES_STRING_ZERO_COPY_CTR != null) {
        return LATIN_BYTES_STRING_ZERO_COPY_CTR.apply(data);
      }
      return BYTES_STRING_ZERO_COPY_CTR.apply(data, LATIN1_BOXED);
    } else if (coder == UTF16) {
      return BYTES_STRING_ZERO_COPY_CTR.apply(data, UTF16_BOXED);
    } else {
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

  private static BiFunction<char[], Boolean, String> getCharsStringZeroCopyCtr() {
    if (!STRING_VALUE_FIELD_IS_CHARS) {
      return null;
    }
    MethodHandle handle = getJavaStringZeroCopyCtrHandle();
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
    MethodHandle handle = getJavaStringZeroCopyCtrHandle();
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

  // Lazy load offsets and keep the access shape in one class so the JDK25 multi-release
  // replacement can change these methods without touching MemoryUtils callers.
  private static class ByteArrayStreamFields {
    private static final long BAS_BUF_BUF;
    private static final long BAS_BUF_COUNT;
    private static final long BIS_BUF_BUF;
    private static final long BIS_BUF_POS;
    private static final long BIS_BUF_COUNT;

    static {
      try {
        BAS_BUF_BUF = UNSAFE.objectFieldOffset(ByteArrayOutputStream.class.getDeclaredField("buf"));
        BAS_BUF_COUNT =
            UNSAFE.objectFieldOffset(ByteArrayOutputStream.class.getDeclaredField("count"));
        BIS_BUF_BUF = UNSAFE.objectFieldOffset(ByteArrayInputStream.class.getDeclaredField("buf"));
        BIS_BUF_POS = UNSAFE.objectFieldOffset(ByteArrayInputStream.class.getDeclaredField("pos"));
        BIS_BUF_COUNT =
            UNSAFE.objectFieldOffset(ByteArrayInputStream.class.getDeclaredField("count"));
      } catch (NoSuchFieldException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public static void wrap(ByteArrayOutputStream stream, MemoryBuffer buffer) {
    Preconditions.checkNotNull(stream);
    byte[] buf = (byte[]) UNSAFE.getObject(stream, ByteArrayStreamFields.BAS_BUF_BUF);
    int count = UNSAFE.getInt(stream, ByteArrayStreamFields.BAS_BUF_COUNT);
    buffer.pointTo(buf, 0, buf.length);
    buffer.writerIndex(count);
  }

  public static void wrap(MemoryBuffer buffer, ByteArrayOutputStream stream) {
    Preconditions.checkNotNull(stream);
    byte[] bytes = buffer.getHeapMemory();
    Preconditions.checkNotNull(bytes);
    UNSAFE.putObject(stream, ByteArrayStreamFields.BAS_BUF_BUF, bytes);
    UNSAFE.putInt(stream, ByteArrayStreamFields.BAS_BUF_COUNT, buffer.writerIndex());
  }

  public static void wrap(ByteArrayInputStream stream, MemoryBuffer buffer) {
    Preconditions.checkNotNull(stream);
    byte[] buf = (byte[]) UNSAFE.getObject(stream, ByteArrayStreamFields.BIS_BUF_BUF);
    int count = UNSAFE.getInt(stream, ByteArrayStreamFields.BIS_BUF_COUNT);
    int pos = UNSAFE.getInt(stream, ByteArrayStreamFields.BIS_BUF_POS);
    buffer.pointTo(buf, 0, count);
    buffer.readerIndex(pos);
  }

  private static class ObjectStreamClassFields {
    private static final long WRITE_OBJECT_METHOD;
    private static final long READ_OBJECT_METHOD;
    private static final long READ_OBJECT_NO_DATA_METHOD;
    private static final long WRITE_REPLACE_METHOD;
    private static final long READ_RESOLVE_METHOD;

    static {
      try {
        WRITE_OBJECT_METHOD =
            UNSAFE.objectFieldOffset(ObjectStreamClass.class.getDeclaredField("writeObjectMethod"));
        READ_OBJECT_METHOD =
            UNSAFE.objectFieldOffset(ObjectStreamClass.class.getDeclaredField("readObjectMethod"));
        READ_OBJECT_NO_DATA_METHOD =
            UNSAFE.objectFieldOffset(
                ObjectStreamClass.class.getDeclaredField("readObjectNoDataMethod"));
        WRITE_REPLACE_METHOD =
            UNSAFE.objectFieldOffset(
                ObjectStreamClass.class.getDeclaredField("writeReplaceMethod"));
        READ_RESOLVE_METHOD =
            UNSAFE.objectFieldOffset(ObjectStreamClass.class.getDeclaredField("readResolveMethod"));
      } catch (NoSuchFieldException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public static Method getObjectStreamClassWriteObjectMethod(ObjectStreamClass objectStreamClass) {
    return (Method)
        UNSAFE.getObject(objectStreamClass, ObjectStreamClassFields.WRITE_OBJECT_METHOD);
  }

  public static Method getObjectStreamClassReadObjectMethod(ObjectStreamClass objectStreamClass) {
    return (Method) UNSAFE.getObject(objectStreamClass, ObjectStreamClassFields.READ_OBJECT_METHOD);
  }

  public static Method getObjectStreamClassReadObjectNoDataMethod(
      ObjectStreamClass objectStreamClass) {
    return (Method)
        UNSAFE.getObject(objectStreamClass, ObjectStreamClassFields.READ_OBJECT_NO_DATA_METHOD);
  }

  public static Method getObjectStreamClassWriteReplaceMethod(ObjectStreamClass objectStreamClass) {
    return (Method)
        UNSAFE.getObject(objectStreamClass, ObjectStreamClassFields.WRITE_REPLACE_METHOD);
  }

  public static Method getObjectStreamClassReadResolveMethod(ObjectStreamClass objectStreamClass) {
    return (Method)
        UNSAFE.getObject(objectStreamClass, ObjectStreamClassFields.READ_RESOLVE_METHOD);
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
      // Faster than handle.invokeExact.
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
      // Faster than handle.invokeExact.
      CallSite callSite =
          LambdaMetafactory.metafactory(
              lookup,
              invokedName,
              MethodType.methodType(functionInterface),
              interfaceType,
              handle,
              interfaceType);
      // FIXME(chaokunyang) why use invokeExact will fail.
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
      // Can't use invokeExact, since we can't specify exact target type for return variable.
      return callSite.getTarget().invoke();
    } catch (ClassNotFoundException | NoClassDefFoundError e) {
      // ToByteFunction/ToBoolFunction/.. are not defined in jdk, if the classloader of
      // fory functions `ToByteFunction/..` isn't parent classloader of classloader for getter
      // represented by handle, then exception will be thrown.
      return makeGetterFunction(lookup, handle, Object.class);
    } catch (Throwable e) {
      throw ExceptionUtils.throwException(e);
    }
  }

  private static volatile Method getModuleMethod;

  public static Object getModule(Class<?> cls) {
    Preconditions.checkArgument(JdkVersion.MAJOR_VERSION >= 9);
    if (getModuleMethod == null) {
      try {
        getModuleMethod = Class.class.getDeclaredMethod("getModule");
      } catch (NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
    }
    try {
      return getModuleMethod.invoke(cls);
    } catch (IllegalAccessException | InvocationTargetException e) {
      throw ExceptionUtils.throwException(e);
    }
  }

  // caller sensitive, must use MethodHandle to walk around the check.
  private static volatile MethodHandle addReadsHandle;

  public static Object addReads(Object thisModule, Object otherModule) {
    Preconditions.checkArgument(JdkVersion.MAJOR_VERSION >= 9);
    try {
      if (addReadsHandle == null) {
        Class<?> cls = Class.forName("java.lang.Module");
        MethodHandles.Lookup lookup = _JDKAccess._trustedLookup(cls);
        addReadsHandle = lookup.findVirtual(cls, "addReads", MethodType.methodType(cls, cls));
      }
      return addReadsHandle.invoke(thisModule, otherModule);
    } catch (Throwable e) {
      throw ExceptionUtils.throwException(e);
    }
  }

  public static Lookup privateLookupIn(Class<?> targetClass, Lookup caller) {
    return _Lookup.privateLookupIn(targetClass, caller);
  }
}
