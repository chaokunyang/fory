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

package org.apache.fory.builder;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.fory.codegen.CodeGenerator;
import org.apache.fory.collection.ClassValueCache;
import org.apache.fory.exception.ForyException;
import org.apache.fory.logging.Logger;
import org.apache.fory.logging.LoggerFactory;
import org.apache.fory.platform.AndroidSupport;
import org.apache.fory.platform.JdkVersion;
import org.apache.fory.platform.internal.DefineClass;
import org.apache.fory.platform.internal._JDKAccess;
import org.apache.fory.reflect.ReflectionUtils;
import org.apache.fory.util.ClassLoaderUtils;
import org.apache.fory.util.Preconditions;
import org.apache.fory.util.StringUtils;

/**
 * Defines generated field accessor helpers in a target class's loader.
 *
 * <p>Source-visible fields use a normal same-package helper class so generated serializers can call
 * direct static methods. Private fields use a JDK15+ hidden nestmate helper: a trusted lookup can
 * define classes, but only hidden classes with the NESTMATE option become nestmates of an existing
 * host. The hidden helper's own bytecode performs the private {@code getfield}/{@code putfield};
 * generated source reaches it through cached {@link MethodHandle}s.
 */
public class AccessorHelper {
  private static final Logger LOG = LoggerFactory.getLogger(AccessorHelper.class);
  private static final ClassValueCache<AccessorState> accessorStates =
      ClassValueCache.newClassKeyCache(32);

  // Must be static to be shared across the whole process life.
  private static final Map<String, Integer> idGenerator = new ConcurrentHashMap<>();

  public static String accessorClassName(Class<?> beanClass) {
    String key = CodeGenerator.getClassUniqueId(beanClass);
    Integer id = idGenerator.get(key);
    if (id == null) {
      synchronized (idGenerator) {
        id = idGenerator.computeIfAbsent(key, k -> idGenerator.size());
      }
    }
    String name = ReflectionUtils.getClassNameWithoutPackage(beanClass) + "ForyAccessor_" + id;
    return name.replace("$", "_");
  }

  public static String qualifiedAccessorClassName(Class<?> beanClass) {
    String pkgName = CodeGenerator.getPackage(beanClass);
    if (StringUtils.isNotBlank(pkgName)) {
      return pkgName + "." + accessorClassName(beanClass);
    } else {
      return accessorClassName(beanClass);
    }
  }

  public static byte[] genBytecode(Class<?> beanClass) {
    return genBytecode(beanClass, false);
  }

  public static boolean defineAccessorClass(Class<?> beanClass) {
    return defineNormalAccessorClass(beanClass);
  }

  public static Class<?> getAccessorClass(Class<?> beanClass) {
    Preconditions.checkArgument(defineAccessorClass(beanClass));
    return state(beanClass).normalClass;
  }

  /** Should be invoked only when {@link #defineAccessor} returns true. */
  public static Class<?> getAccessorClass(Field field) {
    if (useHiddenAccessor(field)) {
      Preconditions.checkArgument(defineHiddenAccessorClass(field.getDeclaringClass()));
      return state(field.getDeclaringClass()).hiddenClass;
    }
    return getAccessorClass(field.getDeclaringClass());
  }

  /** Should be invoked only when {@link #defineAccessor} returns true. */
  public static Class<?> getAccessorClass(Method method) {
    return getAccessorClass(method.getDeclaringClass());
  }

  public static boolean isHiddenAccessor(Field field) {
    return useHiddenAccessor(field);
  }

  public static boolean defineAccessor(Field field) {
    return useHiddenAccessor(field)
        ? defineHiddenAccessorClass(field.getDeclaringClass())
        : defineAccessorClass(field.getDeclaringClass());
  }

  public static boolean defineAccessor(Method method) {
    if (Modifier.isPrivate(method.getModifiers())) {
      return false;
    }
    return defineAccessorClass(method.getDeclaringClass());
  }

  public static boolean defineSetter(Field field) {
    if (Modifier.isFinal(field.getModifiers())) {
      return false;
    }
    if (useHiddenAccessor(field)) {
      return defineHiddenAccessorClass(field.getDeclaringClass());
    }
    if (!AccessorBytecodeGenerator.sourceSetterAccessible(field)) {
      return false;
    }
    return defineAccessorClass(field.getDeclaringClass());
  }

  public static boolean defineSetter(Method method) {
    if (!AccessorBytecodeGenerator.sourceSetterAccessible(method)) {
      return false;
    }
    return defineAccessorClass(method.getDeclaringClass());
  }

  public static MethodHandle getGetter(Field field) {
    Preconditions.checkArgument(defineAccessor(field), field);
    boolean hidden = useHiddenAccessor(field);
    MethodHandle handle =
        findStaticHandle(
            getAccessorClass(field),
            field.getName(),
            MethodType.methodType(
                AccessorBytecodeGenerator.getterReturnType(field, hidden),
                field.getDeclaringClass()));
    return adaptGetter(field, handle);
  }

  public static MethodHandle getSetter(Field field) {
    Preconditions.checkArgument(defineSetter(field), field);
    MethodHandle handle =
        findStaticHandle(
            getAccessorClass(field),
            field.getName(),
            MethodType.methodType(void.class, field.getDeclaringClass(), field.getType()));
    return adaptSetter(field, handle);
  }

  public static Object getObject(MethodHandle getter, Object obj) {
    try {
      return getter.invokeExact(obj);
    } catch (Throwable e) {
      throw accessFailure(e);
    }
  }

  public static void putObject(MethodHandle setter, Object obj, Object value) {
    try {
      setter.invokeExact(obj, value);
    } catch (Throwable e) {
      throw accessFailure(e);
    }
  }

  public static boolean getBoolean(MethodHandle getter, Object obj) {
    try {
      return (boolean) getter.invokeExact(obj);
    } catch (Throwable e) {
      throw accessFailure(e);
    }
  }

  public static void putBoolean(MethodHandle setter, Object obj, boolean value) {
    try {
      setter.invokeExact(obj, value);
    } catch (Throwable e) {
      throw accessFailure(e);
    }
  }

  public static byte getByte(MethodHandle getter, Object obj) {
    try {
      return (byte) getter.invokeExact(obj);
    } catch (Throwable e) {
      throw accessFailure(e);
    }
  }

  public static void putByte(MethodHandle setter, Object obj, byte value) {
    try {
      setter.invokeExact(obj, value);
    } catch (Throwable e) {
      throw accessFailure(e);
    }
  }

  public static char getChar(MethodHandle getter, Object obj) {
    try {
      return (char) getter.invokeExact(obj);
    } catch (Throwable e) {
      throw accessFailure(e);
    }
  }

  public static void putChar(MethodHandle setter, Object obj, char value) {
    try {
      setter.invokeExact(obj, value);
    } catch (Throwable e) {
      throw accessFailure(e);
    }
  }

  public static short getShort(MethodHandle getter, Object obj) {
    try {
      return (short) getter.invokeExact(obj);
    } catch (Throwable e) {
      throw accessFailure(e);
    }
  }

  public static void putShort(MethodHandle setter, Object obj, short value) {
    try {
      setter.invokeExact(obj, value);
    } catch (Throwable e) {
      throw accessFailure(e);
    }
  }

  public static int getInt(MethodHandle getter, Object obj) {
    try {
      return (int) getter.invokeExact(obj);
    } catch (Throwable e) {
      throw accessFailure(e);
    }
  }

  public static void putInt(MethodHandle setter, Object obj, int value) {
    try {
      setter.invokeExact(obj, value);
    } catch (Throwable e) {
      throw accessFailure(e);
    }
  }

  public static long getLong(MethodHandle getter, Object obj) {
    try {
      return (long) getter.invokeExact(obj);
    } catch (Throwable e) {
      throw accessFailure(e);
    }
  }

  public static void putLong(MethodHandle setter, Object obj, long value) {
    try {
      setter.invokeExact(obj, value);
    } catch (Throwable e) {
      throw accessFailure(e);
    }
  }

  public static float getFloat(MethodHandle getter, Object obj) {
    try {
      return (float) getter.invokeExact(obj);
    } catch (Throwable e) {
      throw accessFailure(e);
    }
  }

  public static void putFloat(MethodHandle setter, Object obj, float value) {
    try {
      setter.invokeExact(obj, value);
    } catch (Throwable e) {
      throw accessFailure(e);
    }
  }

  public static double getDouble(MethodHandle getter, Object obj) {
    try {
      return (double) getter.invokeExact(obj);
    } catch (Throwable e) {
      throw accessFailure(e);
    }
  }

  public static void putDouble(MethodHandle setter, Object obj, double value) {
    try {
      setter.invokeExact(obj, value);
    } catch (Throwable e) {
      throw accessFailure(e);
    }
  }

  private static boolean defineNormalAccessorClass(Class<?> beanClass) {
    if (AndroidSupport.IS_ANDROID || beanClass.getClassLoader() == null) {
      return false;
    }
    AccessorState state = state(beanClass);
    if (state.normalDefined) {
      return true;
    }
    synchronized (state.lock) {
      if (state.normalDefined) {
        return true;
      }
      if (state.normalAttempted) {
        return false;
      }
      state.normalAttempted = true;
      String qualifiedClassName = qualifiedAccessorClassName(beanClass);
      try {
        state.normalClass = beanClass.getClassLoader().loadClass(qualifiedClassName);
        state.normalDefined = true;
        return true;
      } catch (ClassNotFoundException ignored) {
        long startTime = System.nanoTime();
        byte[] bytecode = genBytecode(beanClass, false);
        long durationMs = (System.nanoTime() - startTime) / 1000_000;
        LOG.info("Generate accessor bytecode {} take {} ms", qualifiedClassName, durationMs);
        state.normalClass =
            ClassLoaderUtils.tryDefineClassesInClassLoader(
                qualifiedClassName, beanClass, beanClass.getClassLoader(), bytecode);
        state.normalDefined = state.normalClass != null;
        if (!state.normalDefined) {
          LOG.info(
              "Define accessor {} in classloader {} failed.",
              qualifiedClassName,
              beanClass.getClassLoader());
        }
        return state.normalDefined;
      }
    }
  }

  private static boolean defineHiddenAccessorClass(Class<?> beanClass) {
    if (AndroidSupport.IS_ANDROID
        || beanClass.getClassLoader() == null
        || JdkVersion.MAJOR_VERSION < 15) {
      return false;
    }
    AccessorState state = state(beanClass);
    if (state.hiddenDefined) {
      return true;
    }
    synchronized (state.lock) {
      if (state.hiddenDefined) {
        return true;
      }
      if (state.hiddenAttempted) {
        return false;
      }
      state.hiddenAttempted = true;
      long startTime = System.nanoTime();
      byte[] bytecode = genBytecode(beanClass, true);
      long durationMs = (System.nanoTime() - startTime) / 1000_000;
      LOG.info(
          "Generate hidden accessor bytecode {} take {} ms",
          hiddenAccessorClassName(beanClass),
          durationMs);
      state.hiddenClass = DefineClass.defineHiddenNestmate(beanClass, bytecode);
      state.hiddenDefined = true;
      return true;
    }
  }

  private static byte[] genBytecode(Class<?> beanClass, boolean includePrivate) {
    return AccessorBytecodeGenerator.generate(
        beanClass, internalAccessorName(beanClass, includePrivate), includePrivate);
  }

  private static MethodHandle findStaticHandle(
      Class<?> accessorClass, String methodName, MethodType methodType) {
    try {
      return _JDKAccess._trustedLookup(accessorClass)
          .findStatic(accessorClass, methodName, methodType);
    } catch (IllegalAccessException | NoSuchMethodException e) {
      throw new ForyException("Failed to find generated accessor " + methodName, e);
    }
  }

  private static MethodHandle adaptGetter(Field field, MethodHandle handle) {
    Class<?> fieldType = field.getType();
    Class<?> returnType = fieldType.isPrimitive() ? fieldType : Object.class;
    return handle.asType(MethodType.methodType(returnType, Object.class));
  }

  private static MethodHandle adaptSetter(Field field, MethodHandle handle) {
    Class<?> fieldType = field.getType();
    Class<?> valueType = fieldType.isPrimitive() ? fieldType : Object.class;
    return handle.asType(MethodType.methodType(void.class, Object.class, valueType));
  }

  private static boolean useHiddenAccessor(Field field) {
    return Modifier.isPrivate(field.getModifiers());
  }

  private static String internalAccessorName(Class<?> beanClass, boolean hidden) {
    return packagePrefix(beanClass, hidden)
        + (hidden ? hiddenAccessorClassName(beanClass) : accessorClassName(beanClass));
  }

  private static String packagePrefix(Class<?> beanClass, boolean hidden) {
    String pkgName =
        hidden ? ReflectionUtils.getPackage(beanClass) : CodeGenerator.getPackage(beanClass);
    if (StringUtils.isNotBlank(pkgName)) {
      return pkgName.replace('.', '/') + "/";
    }
    return "";
  }

  private static String hiddenAccessorClassName(Class<?> beanClass) {
    return accessorClassName(beanClass) + "Hidden";
  }

  private static AccessorState state(Class<?> beanClass) {
    return accessorStates.get(beanClass, AccessorState::new);
  }

  private static RuntimeException accessFailure(Throwable e) {
    if (e instanceof RuntimeException) {
      return (RuntimeException) e;
    }
    if (e instanceof Error) {
      throw (Error) e;
    }
    return new ForyException("Generated accessor invocation failed.", e);
  }

  private static final class AccessorState {
    private final Object lock = new Object();
    private volatile boolean normalAttempted;
    private volatile boolean normalDefined;
    private volatile Class<?> normalClass;
    private volatile boolean hiddenAttempted;
    private volatile boolean hiddenDefined;
    private volatile Class<?> hiddenClass;

    private AccessorState() {}
  }
}
