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

import static org.apache.fory.codegen.CodeGenerator.sourcePkgLevelAccessible;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;
import org.apache.fory.codegen.asm.ClassWriter;
import org.apache.fory.codegen.asm.MethodWriter;
import org.apache.fory.codegen.asm.Type;
import org.apache.fory.reflect.ReflectionUtils;
import org.apache.fory.type.Descriptor;

final class AccessorBytecodeGenerator {

  static byte[] generate(Class<?> beanClass, String internalName, boolean includePrivate) {
    ClassWriter writer =
        new ClassWriter(
            ClassWriter.ACC_PUBLIC | ClassWriter.ACC_FINAL | ClassWriter.ACC_SUPER,
            internalName,
            "java/lang/Object");
    MethodWriter constructor =
        writer.visitMethod(
            ClassWriter.ACC_PUBLIC, "<init>", Type.methodDescriptor(Type.VOID), 1, 1);
    constructor.aload(0).invokespecial("java/lang/Object", "<init>", "()V").returnVoid();
    Set<String> methodKeys = new HashSet<>();
    for (Descriptor descriptor : Descriptor.getAllDescriptorsMap(beanClass, false).values()) {
      Field field = descriptor.getField();
      if (field == null || Modifier.isStatic(field.getModifiers())) {
        continue;
      }
      if (includePrivate || !Modifier.isPrivate(field.getModifiers())) {
        genFieldGetter(writer, beanClass, field, includePrivate, methodKeys);
      }
      if ((includePrivate || sourceSetterAccessible(field))
          && !Modifier.isFinal(field.getModifiers())) {
        genFieldSetter(writer, beanClass, field, methodKeys);
      }
      if (!includePrivate) {
        Method readMethod = descriptor.getReadMethod();
        if (readMethod != null && !Modifier.isPrivate(readMethod.getModifiers())) {
          genReadMethod(writer, beanClass, readMethod, methodKeys);
        }
        Method writeMethod = descriptor.getWriteMethod();
        if (writeMethod != null && sourceSetterAccessible(writeMethod)) {
          genWriteMethod(writer, beanClass, writeMethod, methodKeys);
        }
      }
    }
    return writer.toByteArray();
  }

  static Class<?> getterReturnType(Field field, boolean hidden) {
    return getterReturnType(field.getType(), hidden);
  }

  private static Class<?> getterReturnType(Class<?> valueType, boolean hidden) {
    if (hidden || sourcePkgLevelAccessible(valueType)) {
      return valueType;
    }
    return Object.class;
  }

  static boolean sourceSetterAccessible(Field field) {
    Class<?> fieldType = field.getType();
    return !Modifier.isPrivate(field.getModifiers())
        && !ReflectionUtils.isPrivate(fieldType)
        && sourcePkgLevelAccessible(fieldType);
  }

  static boolean sourceSetterAccessible(Method method) {
    if (Modifier.isPrivate(method.getModifiers()) || method.getParameterCount() != 1) {
      return false;
    }
    Class<?> parameterType = method.getParameterTypes()[0];
    return !ReflectionUtils.isPrivate(parameterType) && sourcePkgLevelAccessible(parameterType);
  }

  private static void genFieldGetter(
      ClassWriter writer,
      Class<?> beanClass,
      Field field,
      boolean includePrivate,
      Set<String> methodKeys) {
    Type beanType = Type.getType(beanClass);
    Type fieldType = Type.getType(field.getType());
    Type returnType = Type.getType(getterReturnType(field, includePrivate));
    String descriptor = Type.methodDescriptor(returnType, beanType);
    if (!methodKeys.add(field.getName() + descriptor)) {
      return;
    }
    MethodWriter method =
        writer.visitMethod(
            ClassWriter.ACC_PUBLIC | ClassWriter.ACC_STATIC,
            field.getName(),
            descriptor,
            Math.max(1, returnType.slots()),
            1);
    method
        .aload(0)
        .getfield(
            Type.internalName(field.getDeclaringClass()), field.getName(), fieldType.descriptor())
        .returnValue(returnType);
  }

  private static void genFieldSetter(
      ClassWriter writer, Class<?> beanClass, Field field, Set<String> methodKeys) {
    Type beanType = Type.getType(beanClass);
    Type fieldType = Type.getType(field.getType());
    String descriptor = Type.methodDescriptor(Type.VOID, beanType, fieldType);
    if (!methodKeys.add(field.getName() + descriptor)) {
      return;
    }
    MethodWriter method =
        writer.visitMethod(
            ClassWriter.ACC_PUBLIC | ClassWriter.ACC_STATIC,
            field.getName(),
            descriptor,
            1 + fieldType.slots(),
            1 + fieldType.slots());
    method
        .aload(0)
        .load(fieldType, 1)
        .putfield(
            Type.internalName(field.getDeclaringClass()), field.getName(), fieldType.descriptor())
        .returnVoid();
  }

  private static void genReadMethod(
      ClassWriter writer, Class<?> beanClass, Method readMethod, Set<String> methodKeys) {
    Type beanType = Type.getType(beanClass);
    Type methodReturnType = Type.getType(readMethod.getReturnType());
    Type returnType = Type.getType(getterReturnType(readMethod.getReturnType(), false));
    String descriptor = Type.methodDescriptor(returnType, beanType);
    if (!methodKeys.add(readMethod.getName() + descriptor)) {
      return;
    }
    MethodWriter method =
        writer.visitMethod(
            ClassWriter.ACC_PUBLIC | ClassWriter.ACC_STATIC,
            readMethod.getName(),
            descriptor,
            Math.max(1, returnType.slots()),
            1);
    method
        .aload(0)
        .invokevirtual(
            Type.internalName(readMethod.getDeclaringClass()),
            readMethod.getName(),
            Type.methodDescriptor(methodReturnType))
        .returnValue(returnType);
  }

  private static void genWriteMethod(
      ClassWriter writer, Class<?> beanClass, Method writeMethod, Set<String> methodKeys) {
    Type beanType = Type.getType(beanClass);
    Type valueType = Type.getType(writeMethod.getParameterTypes()[0]);
    Type returnType = Type.getType(writeMethod.getReturnType());
    String descriptor = Type.methodDescriptor(Type.VOID, beanType, valueType);
    if (!methodKeys.add(writeMethod.getName() + descriptor)) {
      return;
    }
    MethodWriter method =
        writer.visitMethod(
            ClassWriter.ACC_PUBLIC | ClassWriter.ACC_STATIC,
            writeMethod.getName(),
            descriptor,
            Math.max(1 + valueType.slots(), returnType.slots()),
            1 + valueType.slots());
    method
        .aload(0)
        .load(valueType, 1)
        .invokevirtual(
            Type.internalName(writeMethod.getDeclaringClass()),
            writeMethod.getName(),
            Type.methodDescriptor(returnType, valueType))
        .pop(returnType)
        .returnVoid();
  }

  private AccessorBytecodeGenerator() {}
}
