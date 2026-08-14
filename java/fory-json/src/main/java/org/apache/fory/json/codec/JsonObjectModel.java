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

package org.apache.fory.json.codec;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Objects;
import org.apache.fory.annotation.Internal;

/** Immutable constructor and accessor metadata supplied by a language JSON module. */
@Internal
public final class JsonObjectModel {
  private final Constructor<?> constructor;
  private final String[] parameterNames;
  private final Method[] accessors;
  private final Method[] defaultMethods;
  private final String[] propertyNames;
  private final Method[] propertyGetters;
  private final Method[] propertySetters;
  private final Type[] propertyTypes;

  public JsonObjectModel(
      Constructor<?> constructor,
      String[] parameterNames,
      Method[] accessors,
      Method[] defaultMethods) {
    this(
        constructor,
        parameterNames,
        accessors,
        defaultMethods,
        parameterNames,
        accessors,
        null,
        constructor.getGenericParameterTypes());
  }

  public JsonObjectModel(
      Constructor<?> constructor,
      String[] parameterNames,
      Method[] accessors,
      Method[] defaultMethods,
      String[] propertyNames,
      Method[] propertyGetters,
      Method[] propertySetters) {
    this(
        constructor,
        parameterNames,
        accessors,
        defaultMethods,
        propertyNames,
        propertyGetters,
        propertySetters,
        propertyTypes(
            constructor, parameterNames, propertyNames, propertyGetters, propertySetters));
  }

  public JsonObjectModel(
      Constructor<?> constructor,
      String[] parameterNames,
      Method[] accessors,
      Method[] defaultMethods,
      String[] propertyNames,
      Method[] propertyGetters,
      Method[] propertySetters,
      Type[] propertyTypes) {
    this.constructor = Objects.requireNonNull(constructor, "constructor");
    this.parameterNames = parameterNames.clone();
    this.accessors = accessors.clone();
    this.defaultMethods = defaultMethods.clone();
    this.propertyNames = propertyNames.clone();
    this.propertyGetters = propertyGetters.clone();
    this.propertySetters =
        propertySetters == null ? new Method[propertyNames.length] : propertySetters.clone();
    this.propertyTypes = propertyTypes.clone();
    int count = constructor.getParameterCount();
    if (this.parameterNames.length != count
        || this.accessors.length != count
        || this.defaultMethods.length != count) {
      throw new IllegalArgumentException("JSON object model arrays must match constructor arity");
    }
    HashSet<String> names = new HashSet<>();
    for (String name : this.parameterNames) {
      if (name == null || name.isEmpty() || !names.add(name)) {
        throw new IllegalArgumentException("Invalid JSON object model parameter name " + name);
      }
    }
    if (this.propertyGetters.length != this.propertyNames.length
        || this.propertySetters.length != this.propertyNames.length
        || this.propertyTypes.length != this.propertyNames.length) {
      throw new IllegalArgumentException(
          "JSON object model property arrays must have equal length");
    }
    names.clear();
    for (String name : this.propertyNames) {
      if (name == null || name.isEmpty() || !names.add(name)) {
        throw new IllegalArgumentException("Invalid JSON object model property name " + name);
      }
    }
    for (Type propertyType : this.propertyTypes) {
      Objects.requireNonNull(propertyType, "propertyType");
    }
  }

  public Constructor<?> constructor() {
    return constructor;
  }

  public String[] parameterNames() {
    return parameterNames.clone();
  }

  public Method[] accessors() {
    return accessors.clone();
  }

  public Method[] defaultMethods() {
    return defaultMethods.clone();
  }

  public String[] propertyNames() {
    return propertyNames.clone();
  }

  public Method[] propertyGetters() {
    return propertyGetters.clone();
  }

  public Method[] propertySetters() {
    return propertySetters.clone();
  }

  public Type[] propertyTypes() {
    return propertyTypes.clone();
  }

  private static Type[] propertyTypes(
      Constructor<?> constructor,
      String[] parameterNames,
      String[] propertyNames,
      Method[] propertyGetters,
      Method[] propertySetters) {
    Method[] setters = propertySetters == null ? new Method[propertyNames.length] : propertySetters;
    if (propertyGetters.length != propertyNames.length || setters.length != propertyNames.length) {
      throw new IllegalArgumentException(
          "JSON object model property arrays must have equal length");
    }
    Type[] parameterTypes = constructor.getGenericParameterTypes();
    Type[] types = new Type[propertyNames.length];
    for (int i = 0; i < types.length; i++) {
      for (int parameterIndex = 0; parameterIndex < parameterNames.length; parameterIndex++) {
        if (propertyNames[i].equals(parameterNames[parameterIndex])) {
          types[i] = parameterTypes[parameterIndex];
          break;
        }
      }
      if (types[i] != null) {
        continue;
      }
      Method getter = propertyGetters[i];
      Method setter = setters[i];
      if (getter != null) {
        types[i] = getter.getGenericReturnType();
      } else if (setter != null && setter.getParameterCount() == 1) {
        types[i] = setter.getGenericParameterTypes()[0];
      } else {
        throw new IllegalArgumentException(
            "JSON object model property has no type source " + propertyNames[i]);
      }
    }
    return types;
  }
}
