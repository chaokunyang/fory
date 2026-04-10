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

package org.apache.fory.util;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.time.Instant;
import java.util.Date;
import org.apache.fory.annotation.Internal;

/** Utilities for optional SQL time types without eagerly loading {@code java.sql}. */
@Internal
public final class OptionalSqlTimeSupport {
  public static final String SQL_DATE_CLASS_NAME = "java.sql.Date";
  public static final String SQL_TIME_CLASS_NAME = "java.sql.Time";
  public static final String SQL_TIMESTAMP_CLASS_NAME = "java.sql.Timestamp";

  private OptionalSqlTimeSupport() {}

  @SuppressWarnings("unchecked")
  public static <T extends Date> Class<T> getSqlDateClass() {
    return (Class<T>) Holder.SQL_DATE_CLASS;
  }

  @SuppressWarnings("unchecked")
  public static <T extends Date> Class<T> getSqlTimeClass() {
    return (Class<T>) Holder.SQL_TIME_CLASS;
  }

  @SuppressWarnings("unchecked")
  public static <T extends Date> Class<T> getSqlTimestampClass() {
    return (Class<T>) Holder.SQL_TIMESTAMP_CLASS;
  }

  public static Date newSqlDate(long time) {
    return invokeDate(Holder.SQL_DATE_CONSTRUCTOR, time);
  }

  public static Date newSqlTime(long time) {
    return invokeDate(Holder.SQL_TIME_CONSTRUCTOR, time);
  }

  public static Date newSqlTimestamp(long time) {
    return invokeDate(Holder.SQL_TIMESTAMP_CONSTRUCTOR, time);
  }

  public static Date sqlTimestampFrom(Instant instant) {
    return invokeDate(Holder.SQL_TIMESTAMP_FROM, instant);
  }

  public static Instant sqlTimestampToInstant(Date timestamp) {
    try {
      return (Instant) Holder.SQL_TIMESTAMP_TO_INSTANT.invoke(timestamp);
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  public static int getSqlTimestampNanos(Date timestamp) {
    try {
      return (int) Holder.SQL_TIMESTAMP_GET_NANOS.invoke(timestamp);
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  public static void setSqlTimestampNanos(Date timestamp, int nanos) {
    try {
      Holder.SQL_TIMESTAMP_SET_NANOS.invoke(timestamp, nanos);
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  private static Date invokeDate(MethodHandle handle, Object value) {
    try {
      return (Date) handle.invoke(value);
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable t) {
      throw new IllegalStateException(t);
    }
  }

  @SuppressWarnings("unchecked")
  private static Class<? extends Date> loadDateClass(String className) {
    ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
    if (contextClassLoader != null) {
      try {
        return (Class<? extends Date>) Class.forName(className, false, contextClassLoader);
      } catch (ClassNotFoundException ignored) {
        // Fall through to the defining class loader.
      }
    }
    try {
      return (Class<? extends Date>)
          Class.forName(className, false, OptionalSqlTimeSupport.class.getClassLoader());
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("Optional SQL type " + className + " is not available.", e);
    }
  }

  private static MethodHandle findConstructor(Class<?> type) {
    try {
      return MethodHandles.publicLookup()
          .findConstructor(type, MethodType.methodType(void.class, long.class));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new IllegalStateException("Unable to access constructor for " + type.getName(), e);
    }
  }

  private static MethodHandle findVirtual(
      Class<?> type, String methodName, Class<?> returnType, Class<?>... parameterTypes) {
    try {
      return MethodHandles.publicLookup()
          .findVirtual(type, methodName, MethodType.methodType(returnType, parameterTypes));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new IllegalStateException(
          "Unable to access method " + type.getName() + "#" + methodName, e);
    }
  }

  private static MethodHandle findStatic(
      Class<?> type, String methodName, Class<?> returnType, Class<?>... parameterTypes) {
    try {
      return MethodHandles.publicLookup()
          .findStatic(type, methodName, MethodType.methodType(returnType, parameterTypes));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new IllegalStateException(
          "Unable to access method " + type.getName() + "#" + methodName, e);
    }
  }

  private static final class Holder {
    private static final Class<? extends Date> SQL_DATE_CLASS = loadDateClass(SQL_DATE_CLASS_NAME);
    private static final Class<? extends Date> SQL_TIME_CLASS = loadDateClass(SQL_TIME_CLASS_NAME);
    private static final Class<? extends Date> SQL_TIMESTAMP_CLASS =
        loadDateClass(SQL_TIMESTAMP_CLASS_NAME);
    private static final MethodHandle SQL_DATE_CONSTRUCTOR = findConstructor(SQL_DATE_CLASS);
    private static final MethodHandle SQL_TIME_CONSTRUCTOR = findConstructor(SQL_TIME_CLASS);
    private static final MethodHandle SQL_TIMESTAMP_CONSTRUCTOR =
        findConstructor(SQL_TIMESTAMP_CLASS);
    private static final MethodHandle SQL_TIMESTAMP_FROM =
        findStatic(SQL_TIMESTAMP_CLASS, "from", SQL_TIMESTAMP_CLASS, Instant.class);
    private static final MethodHandle SQL_TIMESTAMP_TO_INSTANT =
        findVirtual(SQL_TIMESTAMP_CLASS, "toInstant", Instant.class);
    private static final MethodHandle SQL_TIMESTAMP_GET_NANOS =
        findVirtual(SQL_TIMESTAMP_CLASS, "getNanos", int.class);
    private static final MethodHandle SQL_TIMESTAMP_SET_NANOS =
        findVirtual(SQL_TIMESTAMP_CLASS, "setNanos", void.class, int.class);
  }
}
