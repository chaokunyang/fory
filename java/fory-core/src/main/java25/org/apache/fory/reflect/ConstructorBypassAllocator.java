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

package org.apache.fory.reflect;

import java.io.ObjectStreamClass;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import org.apache.fory.annotation.Internal;
import org.apache.fory.collection.ClassValueCache;
import org.apache.fory.exception.ForyException;
import org.apache.fory.platform.internal._JDKAccess;

/** JDK25 replacement for the JDK8-24 constructor-bypass allocator. */
@Internal
final class ConstructorBypassAllocator {
  private ConstructorBypassAllocator() {}

  static <T> T allocate(Class<T> type) {
    try {
      return type.cast(ObjectStreamClassAccess.newInstance(type));
    } catch (Throwable e) {
      throw handleAllocationException(type, e);
    }
  }

  private static RuntimeException handleAllocationException(Class<?> type, Throwable cause) {
    if (cause instanceof UnsupportedOperationException || cause instanceof InstantiationException) {
      return unsupported(type, cause);
    }
    return new ForyException("Failed to create an instance for " + type, cause);
  }

  private static ForyException unsupported(Class<?> type, Throwable cause) {
    return new ForyException(
        "Cannot create a constructor-bypassing instance for "
            + type
            + " in JDK25+ zero-Unsafe mode. Provide an accessible no-arg constructor, "
            + "annotate a constructor with @ForyConstructor, register a constructor with "
            + "BaseFory.registerConstructor, use a record canonical constructor, or register a "
            + "custom serializer.",
        cause);
  }

  private static final class ObjectStreamClassAccess {
    private static final ClassValueCache<ObjectStreamClass> CLASSES =
        ClassValueCache.newClassKeyCache(32);
    private static final MethodHandle NEW_INSTANCE;
    private static final Throwable INIT_ERROR;

    static {
      MethodHandle newInstance = null;
      Throwable error = null;
      try {
        newInstance =
            _JDKAccess
                ._trustedLookup(ObjectStreamClass.class)
                .findVirtual(
                    ObjectStreamClass.class, "newInstance", MethodType.methodType(Object.class));
      } catch (ReflectiveOperationException | RuntimeException e) {
        error = e;
      }
      NEW_INSTANCE = newInstance;
      INIT_ERROR = error;
    }

    private static Object newInstance(Class<?> type) throws Throwable {
      MethodHandle handle = NEW_INSTANCE;
      if (handle == null) {
        throw missingLookup();
      }
      return handle.invoke(objectStreamClass(type));
    }

    private static ObjectStreamClass objectStreamClass(Class<?> type) {
      return CLASSES.get(type, () -> ObjectStreamClass.lookupAny(type));
    }

    private static ForyException missingLookup() {
      return new ForyException(
          "JDK25+ Serializable object creation requires java.base/java.lang.invoke to be open "
              + "to org.apache.fory.core",
          INIT_ERROR);
    }
  }
}
