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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;

// CHECKSTYLE.OFF:TypeName
class _Lookup {
  // CHECKSTYLE.ON:TypeName
  static final Lookup IMPL_LOOKUP = MethodHandles.lookup();

  // CHECKSTYLE.OFF:MethodName
  public static Lookup _trustedLookup(Class<?> objectClass) {
    // CHECKSTYLE.ON:MethodName
    return privateLookupIn(objectClass, MethodHandles.lookup());
  }

  public static Lookup privateLookupIn(Class<?> targetClass, Lookup caller) {
    try {
      Module foryModule = _Lookup.class.getModule();
      Module targetModule = targetClass.getModule();
      if (foryModule != targetModule) {
        foryModule.addReads(targetModule);
      }
      return MethodHandles.privateLookupIn(targetClass, caller);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException(privateAccessMessage(targetClass), e);
    }
  }

  /**
   * Creates and links a class or interface from {@code bytes} with the same class loader and in the
   * same runtime package and protection domain as this lookup's lookup class. Classes in bytecode
   * must be in the same package as the lookup class.
   */
  public static Class<?> defineClass(Lookup lookup, byte[] bytes) {
    try {
      return lookup.defineClass(bytes);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException(privateAccessMessage(lookup.lookupClass()), e);
    }
  }

  private static String privateAccessMessage(Class<?> targetClass) {
    Module module = targetClass.getModule();
    Package pkg = targetClass.getPackage();
    String packageName = pkg == null ? "" : pkg.getName();
    return "Private lookup for "
        + targetClass.getName()
        + " requires package "
        + packageName
        + " in module "
        + module.getName()
        + " to be open to org.apache.fory.core,org.apache.fory.format";
  }
}
