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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import org.apache.fory.platform.internal._JDKAccess;
import org.apache.fory.util.ExceptionUtils;

final class SerializationHookLookup {
  private SerializationHookLookup() {}

  static MethodHandle readResolveHandle(Class<?> type, Method method)
      throws IllegalAccessException {
    return _JDKAccess._trustedLookup(type).unreflect(method);
  }

  private static final class Methods {
    private static final Object REFLECTION_FACTORY;
    private static final Method WRITE_OBJECT;
    private static final Method READ_OBJECT;
    private static final Method READ_OBJECT_NO_DATA;
    private static final Method DEFAULT_READ_OBJECT;
    private static final Method WRITE_REPLACE;
    private static final Method READ_RESOLVE;

    static {
      Object reflectionFactory = null;
      Method writeObject = null;
      Method readObject = null;
      Method readObjectNoData = null;
      Method defaultReadObject = null;
      Method writeReplace = null;
      Method readResolve = null;
      try {
        Class<?> factoryClass = Class.forName("sun.reflect.ReflectionFactory");
        Method getReflectionFactory = factoryClass.getDeclaredMethod("getReflectionFactory");
        reflectionFactory = getReflectionFactory.invoke(null);
        writeObject = factoryClass.getDeclaredMethod("writeObjectForSerialization", Class.class);
        readObject = factoryClass.getDeclaredMethod("readObjectForSerialization", Class.class);
        readObjectNoData =
            factoryClass.getDeclaredMethod("readObjectNoDataForSerialization", Class.class);
        try {
          defaultReadObject =
              factoryClass.getDeclaredMethod("defaultReadObjectForSerialization", Class.class);
        } catch (NoSuchMethodException e) {
          ExceptionUtils.ignore(e);
        }
        writeReplace = factoryClass.getDeclaredMethod("writeReplaceForSerialization", Class.class);
        readResolve = factoryClass.getDeclaredMethod("readResolveForSerialization", Class.class);
      } catch (Throwable e) {
        ExceptionUtils.ignore(e);
      }
      REFLECTION_FACTORY = reflectionFactory;
      WRITE_OBJECT = writeObject;
      READ_OBJECT = readObject;
      READ_OBJECT_NO_DATA = readObjectNoData;
      DEFAULT_READ_OBJECT = defaultReadObject;
      WRITE_REPLACE = writeReplace;
      READ_RESOLVE = readResolve;
    }
  }

  private static MethodHandle getHandle(Class<?> type, Method factoryMethod) {
    if (Methods.REFLECTION_FACTORY == null || factoryMethod == null) {
      return null;
    }
    try {
      return (MethodHandle) factoryMethod.invoke(Methods.REFLECTION_FACTORY, type);
    } catch (Throwable e) {
      ExceptionUtils.ignore(e);
      return null;
    }
  }

  private static Method getMethod(Class<?> type, Method factoryMethod) {
    MethodHandle handle = getHandle(type, factoryMethod);
    return handle == null ? null : MethodHandles.reflectAs(Method.class, handle);
  }

  static Method getWriteObjectMethod(Class<?> type) {
    return getMethod(type, Methods.WRITE_OBJECT);
  }

  static Method getReadObjectMethod(Class<?> type) {
    return getMethod(type, Methods.READ_OBJECT);
  }

  static Method getReadObjectNoDataMethod(Class<?> type) {
    return getMethod(type, Methods.READ_OBJECT_NO_DATA);
  }

  static MethodHandle getDefaultReadObjectHandle(Class<?> type) {
    return getHandle(type, Methods.DEFAULT_READ_OBJECT);
  }

  static Method getWriteReplaceMethod(Class<?> type) {
    return getMethod(type, Methods.WRITE_REPLACE);
  }

  static Method getReadResolveMethod(Class<?> type) {
    return getMethod(type, Methods.READ_RESOLVE);
  }

  static boolean isAvailable() {
    return Methods.REFLECTION_FACTORY != null
        && Methods.WRITE_OBJECT != null
        && Methods.READ_OBJECT != null
        && Methods.READ_OBJECT_NO_DATA != null
        && Methods.WRITE_REPLACE != null
        && Methods.READ_RESOLVE != null;
  }
}
