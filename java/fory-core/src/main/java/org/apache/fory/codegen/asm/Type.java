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

package org.apache.fory.codegen.asm;

import org.apache.fory.annotation.Internal;

/** JVM type descriptor utilities for Fory's small classfile writer. */
@Internal
public final class Type {
  public static final Type VOID = new Type("V", 0, 0, 0xb1);
  public static final Type OBJECT = new Type("Ljava/lang/Object;", 0x19, 1, 0xb0);

  private final String descriptor;
  private final int loadOpcode;
  private final int slots;
  private final int returnOpcode;

  private Type(String descriptor, int loadOpcode, int slots, int returnOpcode) {
    this.descriptor = descriptor;
    this.loadOpcode = loadOpcode;
    this.slots = slots;
    this.returnOpcode = returnOpcode;
  }

  public static Type getType(Class<?> type) {
    if (type == void.class) {
      return VOID;
    } else if (type == boolean.class) {
      return new Type("Z", 0x15, 1, 0xac);
    } else if (type == byte.class) {
      return new Type("B", 0x15, 1, 0xac);
    } else if (type == char.class) {
      return new Type("C", 0x15, 1, 0xac);
    } else if (type == short.class) {
      return new Type("S", 0x15, 1, 0xac);
    } else if (type == int.class) {
      return new Type("I", 0x15, 1, 0xac);
    } else if (type == long.class) {
      return new Type("J", 0x16, 2, 0xad);
    } else if (type == float.class) {
      return new Type("F", 0x17, 1, 0xae);
    } else if (type == double.class) {
      return new Type("D", 0x18, 2, 0xaf);
    } else if (type.isArray()) {
      return new Type(type.getName().replace('.', '/'), 0x19, 1, 0xb0);
    }
    return new Type("L" + internalName(type) + ";", 0x19, 1, 0xb0);
  }

  public static String internalName(Class<?> type) {
    return type.getName().replace('.', '/');
  }

  public static String methodDescriptor(Type returnType, Type... parameterTypes) {
    StringBuilder builder = new StringBuilder();
    builder.append('(');
    for (Type parameterType : parameterTypes) {
      builder.append(parameterType.descriptor);
    }
    builder.append(')');
    builder.append(returnType.descriptor);
    return builder.toString();
  }

  public String descriptor() {
    return descriptor;
  }

  int loadOpcode() {
    return loadOpcode;
  }

  public int slots() {
    return slots;
  }

  public boolean isVoid() {
    return this == VOID;
  }

  int returnOpcode() {
    return returnOpcode;
  }
}
