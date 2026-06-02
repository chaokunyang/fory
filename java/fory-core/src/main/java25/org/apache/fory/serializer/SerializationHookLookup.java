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

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.apache.fory.collection.ClassValueCache;
import org.apache.fory.platform.internal._JDKAccess;
import org.apache.fory.util.ExceptionUtils;

final class SerializationHookLookup {
  private SerializationHookLookup() {}

  static MethodHandle readResolveHandle(Class<?> type, Method method)
      throws IllegalAccessException {
    try {
      return _JDKAccess._trustedLookup(type).unreflect(method);
    } catch (IllegalArgumentException e) {
      if (type != SerializedLambda.class) {
        throw e;
      }
      // JDK25 rejects SerializedLambda itself as a privateLookupIn target. Reflective access still
      // honors the same java.base/java.lang.invoke open requirement and preserves the serializer
      // path used by JDK8-24.
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

  private static final ClassValueCache<Methods> methodsCache = ClassValueCache.newClassKeyCache(32);

  private static final class Methods {
    private final Method writeObject;
    private final Method readObject;
    private final Method readObjectNoData;
    private final Method writeReplace;
    private final Method readResolve;

    private Methods(Class<?> type) {
      writeObject = getPrivateMethod(type, "writeObject", void.class, ObjectOutputStream.class);
      readObject = getPrivateMethod(type, "readObject", void.class, ObjectInputStream.class);
      readObjectNoData = getPrivateMethod(type, "readObjectNoData", void.class);
      writeReplace = getInheritableObjectMethod(type, "writeReplace");
      readResolve = getInheritableObjectMethod(type, "readResolve");
    }
  }

  private static Methods methods(Class<?> type) {
    return methodsCache.get(type, () -> new Methods(type));
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

  static Method getWriteObjectMethod(Class<?> type) {
    return methods(type).writeObject;
  }

  static Method getReadObjectMethod(Class<?> type) {
    return methods(type).readObject;
  }

  static Method getReadObjectNoDataMethod(Class<?> type) {
    return methods(type).readObjectNoData;
  }

  static MethodHandle getDefaultReadObjectHandle(Class<?> type) {
    return null;
  }

  static Method getWriteReplaceMethod(Class<?> type) {
    return methods(type).writeReplace;
  }

  static Method getReadResolveMethod(Class<?> type) {
    return methods(type).readResolve;
  }

  static boolean isAvailable() {
    return true;
  }
}
