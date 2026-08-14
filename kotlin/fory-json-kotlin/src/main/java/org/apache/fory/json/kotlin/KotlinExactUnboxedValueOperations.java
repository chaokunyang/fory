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

package org.apache.fory.json.kotlin;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import org.apache.fory.json.ForyJsonException;
import org.apache.fory.json.reader.JsonReader;

/** Exact prebound interpreted invocation for one unboxed parent occurrence. */
final class KotlinExactUnboxedValueOperations implements KotlinUnboxedValueClassOperations {
  private final Class<?> owner;
  private final Class<?> carrier;
  private final Class<?> valueCarrier;
  private final MethodHandle construct;
  private final MethodHandle extract;
  private final MethodHandle box;
  private final MethodHandle unbox;
  private final Method[] constructMethods;
  private final int[] constructBoxBytes;
  private final Method[] extractMethods;
  private final Method boxMethod;
  private final Method unboxMethod;
  private final int boxBytes;

  private KotlinExactUnboxedValueOperations(
      Class<?> owner,
      Class<?> carrier,
      Class<?> valueCarrier,
      MethodHandle construct,
      MethodHandle extract,
      MethodHandle box,
      MethodHandle unbox,
      Method[] constructMethods,
      int[] constructBoxBytes,
      Method[] extractMethods,
      Method boxMethod,
      Method unboxMethod,
      int boxBytes) {
    if (constructMethods.length != constructBoxBytes.length) {
      throw new IllegalArgumentException("Unboxed construct operations and charges must align");
    }
    this.owner = owner;
    this.carrier = carrier;
    this.valueCarrier = valueCarrier;
    this.construct = construct;
    this.extract = extract;
    this.box = box;
    this.unbox = unbox;
    this.constructMethods = constructMethods.clone();
    this.constructBoxBytes = constructBoxBytes.clone();
    this.extractMethods = extractMethods.clone();
    this.boxMethod = boxMethod;
    this.unboxMethod = unboxMethod;
    this.boxBytes = boxBytes;
  }

  static KotlinUnboxedValueClassOperations create(
      Class<?> owner,
      Class<?> carrier,
      Class<?> valueCarrier,
      MethodHandle construct,
      MethodHandle extract,
      MethodHandle box,
      MethodHandle unbox,
      Method[] constructMethods,
      int[] constructBoxBytes,
      Method[] extractMethods,
      Method boxMethod,
      Method unboxMethod,
      int boxBytes) {
    return new KotlinExactUnboxedValueOperations(
        owner,
        carrier,
        valueCarrier,
        construct,
        extract,
        box,
        unbox,
        constructMethods,
        constructBoxBytes,
        extractMethods,
        boxMethod,
        unboxMethod,
        boxBytes);
  }

  @Override
  public Object constructCarrier(JsonReader reader, Object value) {
    try {
      return (Object) construct.invokeExact(reader, value);
    } catch (Throwable cause) {
      throw failure("construct", cause);
    }
  }

  @Override
  public Object extractValue(Object carrierValue) {
    requireCarrier(carrierValue);
    try {
      return (Object) extract.invokeExact(carrierValue);
    } catch (Throwable cause) {
      throw failure("extract", cause);
    }
  }

  @Override
  public Object boxCarrier(Object carrierValue) {
    requireCarrier(carrierValue);
    try {
      return (Object) box.invokeExact(carrierValue);
    } catch (Throwable cause) {
      throw failure("box", cause);
    }
  }

  @Override
  public Object unboxValue(Object value) {
    try {
      return (Object) unbox.invokeExact(value);
    } catch (Throwable cause) {
      throw failure("unbox", cause);
    }
  }

  @Override
  public Method[] constructMethods() {
    return constructMethods.clone();
  }

  @Override
  public int[] constructBoxBytes() {
    return constructBoxBytes.clone();
  }

  @Override
  public Method[] extractMethods() {
    return extractMethods.clone();
  }

  @Override
  public Method boxMethod() {
    return boxMethod;
  }

  @Override
  public Method unboxMethod() {
    return unboxMethod;
  }

  @Override
  public int boxBytes() {
    return boxBytes;
  }

  private void requireCarrier(Object value) {
    if (value == null && carrier.isPrimitive()) {
      throw new ForyJsonException("Null unboxed carrier for " + owner.getName());
    }
  }

  private ForyJsonException failure(String operation, Throwable cause) {
    if (cause instanceof Error) {
      throw (Error) cause;
    }
    if (cause instanceof ForyJsonException) {
      return (ForyJsonException) cause;
    }
    return new ForyJsonException(
        "Kotlin value-class "
            + operation
            + " failed for "
            + owner.getName()
            + " using "
            + valueCarrier.getName()
            + " -> "
            + carrier.getName(),
        cause);
  }
}
