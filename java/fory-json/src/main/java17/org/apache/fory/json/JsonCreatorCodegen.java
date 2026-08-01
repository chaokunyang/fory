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

package org.apache.fory.json;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.fory.platform.internal.DefineClass;
import org.apache.fory.platform.internal._JDKAccess;

/** Generates direct constructor invokers while Native Image analysis is still mutable. */
final class JsonCreatorCodegen {
  private static final int ACC_PUBLIC = 0x0001;
  private static final int ACC_STATIC = 0x0008;
  private static final int ACC_FINAL = 0x0010;
  private static final int ACC_SUPER = 0x0020;
  private static final int ACC_SYNTHETIC = 0x1000;

  private JsonCreatorCodegen() {}

  static Invokers create(Constructor<?> constructor) {
    Class<?> ownerType = constructor.getDeclaringClass();
    Class<?> invokerClass =
        DefineClass.defineHiddenNestmate(ownerType, new ClassBytes(constructor).build());
    try {
      MethodHandles.Lookup lookup = _JDKAccess._trustedLookup(invokerClass);
      MethodHandle creator =
          lookup.findStatic(
              invokerClass,
              "invoke",
              MethodType.methodType(Object.class, Object[].class));
      Method creatorMethod = invokerClass.getDeclaredMethod("invoke", Object[].class);
      if (constructor.getParameterCount() != 1
          || constructor.getParameterTypes()[0] != String.class) {
        return new Invokers(invokerClass, creator, creatorMethod, null, null);
      }
      MethodHandle stringCreator =
          lookup.findStatic(
              invokerClass,
              "invoke",
              MethodType.methodType(Object.class, String.class));
      Method stringMethod = invokerClass.getDeclaredMethod("invoke", String.class);
      return new Invokers(invokerClass, creator, creatorMethod, stringCreator, stringMethod);
    } catch (ReflectiveOperationException cause) {
      throw new IllegalStateException(
          "Cannot resolve generated Fory JSON creator for " + constructor, cause);
    }
  }

  static final class Invokers {
    final Class<?> type;
    final MethodHandle creator;
    final Method creatorMethod;
    final MethodHandle stringCreator;
    final Method stringMethod;

    private Invokers(
        Class<?> type,
        MethodHandle creator,
        Method creatorMethod,
        MethodHandle stringCreator,
        Method stringMethod) {
      this.type = type;
      this.creator = creator;
      this.creatorMethod = creatorMethod;
      this.stringCreator = stringCreator;
      this.stringMethod = stringMethod;
    }
  }

  private static final class ClassBytes {
    private static final String OBJECT = "java/lang/Object";
    private static final String OBJECT_ARRAY_INVOKE = "([Ljava/lang/Object;)Ljava/lang/Object;";
    private static final String STRING_INVOKE = "(Ljava/lang/String;)Ljava/lang/Object;";

    private final Constructor<?> constructor;
    private final Class<?>[] parameterTypes;
    private final String owner;
    private final String generatedName;
    private final boolean stringCreator;
    private final ConstantPool constants = new ConstantPool();

    private ClassBytes(Constructor<?> constructor) {
      this.constructor = constructor;
      parameterTypes = constructor.getParameterTypes();
      owner = internalName(constructor.getDeclaringClass());
      generatedName = owner + "$$ForyJsonCreator";
      stringCreator = parameterTypes.length == 1 && parameterTypes[0] == String.class;
    }

    private byte[] build() {
      try {
        int thisClass = constants.classInfo(generatedName);
        int superClass = constants.classInfo(OBJECT);
        MethodCode arrayInvoker = arrayInvoker();
        MethodCode stringInvoker = stringCreator ? stringInvoker() : null;
        arrayInvoker.register(constants);
        if (stringCreator) {
          stringInvoker.register(constants);
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(0xCAFEBABE);
        output.writeShort(0);
        output.writeShort(52);
        constants.write(output);
        output.writeShort(ACC_PUBLIC | ACC_FINAL | ACC_SUPER | ACC_SYNTHETIC);
        output.writeShort(thisClass);
        output.writeShort(superClass);
        output.writeShort(0);
        output.writeShort(0);
        output.writeShort(stringCreator ? 2 : 1);
        arrayInvoker.write(output, constants);
        if (stringCreator) {
          stringInvoker.write(output, constants);
        }
        output.writeShort(0);
        output.flush();
        return bytes.toByteArray();
      } catch (IOException cause) {
        throw new IllegalStateException("Cannot generate Fory JSON creator for " + constructor, cause);
      }
    }

    private MethodCode arrayInvoker() throws IOException {
      Code code = new Code(constants);
      code.type(0xbb, owner);
      code.op(0x59);
      for (int i = 0; i < parameterTypes.length; i++) {
        code.op(0x2a);
        code.integer(i);
        code.op(0x32);
        code.convert(parameterTypes[i]);
      }
      code.method(0xb7, owner, "<init>", constructorDescriptor(parameterTypes));
      code.op(0xb0);
      return new MethodCode(
          ACC_PUBLIC | ACC_STATIC,
          "invoke",
          OBJECT_ARRAY_INVOKE,
          4 + parameterTypes.length * 2,
          1,
          code.bytes());
    }

    private MethodCode stringInvoker() throws IOException {
      Code code = new Code(constants);
      code.type(0xbb, owner);
      code.op(0x59);
      code.op(0x2a);
      code.method(0xb7, owner, "<init>", constructorDescriptor(parameterTypes));
      code.op(0xb0);
      return new MethodCode(
          ACC_PUBLIC | ACC_STATIC, "invoke", STRING_INVOKE, 3, 1, code.bytes());
    }
  }

  private static final class MethodCode {
    private final int access;
    private final String name;
    private final String descriptor;
    private final int maxStack;
    private final int maxLocals;
    private final byte[] code;

    private MethodCode(
        int access, String name, String descriptor, int maxStack, int maxLocals, byte[] code) {
      this.access = access;
      this.name = name;
      this.descriptor = descriptor;
      this.maxStack = maxStack;
      this.maxLocals = maxLocals;
      this.code = code;
    }

    private void write(DataOutputStream output, ConstantPool constants) throws IOException {
      output.writeShort(access);
      output.writeShort(constants.utf8(name));
      output.writeShort(constants.utf8(descriptor));
      output.writeShort(1);
      output.writeShort(constants.utf8("Code"));
      output.writeInt(12 + code.length);
      output.writeShort(maxStack);
      output.writeShort(maxLocals);
      output.writeInt(code.length);
      output.write(code);
      output.writeShort(0);
      output.writeShort(0);
    }

    private void register(ConstantPool constants) {
      constants.utf8(name);
      constants.utf8(descriptor);
      constants.utf8("Code");
    }
  }

  private static final class Code {
    private static final Map<Class<?>, Class<?>> BOX_TYPES = boxTypes();
    private final ConstantPool constants;
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final DataOutputStream output = new DataOutputStream(bytes);

    private Code(ConstantPool constants) {
      this.constants = constants;
    }

    private void op(int opcode) throws IOException {
      output.writeByte(opcode);
    }

    private void type(int opcode, String type) throws IOException {
      output.writeByte(opcode);
      output.writeShort(constants.classInfo(type));
    }

    private void method(int opcode, String owner, String name, String descriptor)
        throws IOException {
      output.writeByte(opcode);
      output.writeShort(constants.methodRef(owner, name, descriptor));
    }

    private void integer(int value) throws IOException {
      if (value <= 5) {
        output.writeByte(0x03 + value);
      } else if (value <= Byte.MAX_VALUE) {
        output.writeByte(0x10);
        output.writeByte(value);
      } else {
        output.writeByte(0x11);
        output.writeShort(value);
      }
    }

    private void convert(Class<?> type) throws IOException {
      if (!type.isPrimitive()) {
        if (type != Object.class) {
          this.type(0xc0, internalName(type));
        }
        return;
      }
      Class<?> boxType = BOX_TYPES.get(type);
      this.type(0xc0, internalName(boxType));
      method(
          0xb6,
          internalName(boxType),
          type.getName() + "Value",
          "()" + descriptor(type));
    }

    private byte[] bytes() throws IOException {
      output.flush();
      return bytes.toByteArray();
    }

    private static Map<Class<?>, Class<?>> boxTypes() {
      Map<Class<?>, Class<?>> types = new LinkedHashMap<>();
      types.put(boolean.class, Boolean.class);
      types.put(byte.class, Byte.class);
      types.put(short.class, Short.class);
      types.put(int.class, Integer.class);
      types.put(long.class, Long.class);
      types.put(float.class, Float.class);
      types.put(double.class, Double.class);
      types.put(char.class, Character.class);
      return types;
    }
  }

  private static final class ConstantPool {
    private final List<Constant> entries = new ArrayList<>();
    private final Map<String, Integer> indices = new LinkedHashMap<>();

    private int utf8(String value) {
      return add("U" + value, new Utf8Constant(value));
    }

    private int classInfo(String internalName) {
      int name = utf8(internalName);
      return add("C" + internalName, new IndexConstant(7, name));
    }

    private int nameAndType(String name, String descriptor) {
      int nameIndex = utf8(name);
      int descriptorIndex = utf8(descriptor);
      return add(
          "N" + name + descriptor, new PairConstant(12, nameIndex, descriptorIndex));
    }

    private int methodRef(String owner, String name, String descriptor) {
      int ownerIndex = classInfo(owner);
      int nameAndType = nameAndType(name, descriptor);
      return add(
          "M" + owner + '.' + name + descriptor,
          new PairConstant(10, ownerIndex, nameAndType));
    }

    private int add(String key, Constant constant) {
      Integer index = indices.get(key);
      if (index != null) {
        return index;
      }
      int newIndex = entries.size() + 1;
      entries.add(constant);
      indices.put(key, newIndex);
      return newIndex;
    }

    private void write(DataOutputStream output) throws IOException {
      output.writeShort(entries.size() + 1);
      for (Constant entry : entries) {
        entry.write(output);
      }
    }
  }

  private interface Constant {
    void write(DataOutputStream output) throws IOException;
  }

  private static final class Utf8Constant implements Constant {
    private final String value;

    private Utf8Constant(String value) {
      this.value = value;
    }

    @Override
    public void write(DataOutputStream output) throws IOException {
      output.writeByte(1);
      output.writeUTF(value);
    }
  }

  private static final class IndexConstant implements Constant {
    private final int tag;
    private final int index;

    private IndexConstant(int tag, int index) {
      this.tag = tag;
      this.index = index;
    }

    @Override
    public void write(DataOutputStream output) throws IOException {
      output.writeByte(tag);
      output.writeShort(index);
    }
  }

  private static final class PairConstant implements Constant {
    private final int tag;
    private final int first;
    private final int second;

    private PairConstant(int tag, int first, int second) {
      this.tag = tag;
      this.first = first;
      this.second = second;
    }

    @Override
    public void write(DataOutputStream output) throws IOException {
      output.writeByte(tag);
      output.writeShort(first);
      output.writeShort(second);
    }
  }

  private static String constructorDescriptor(Class<?>[] parameterTypes) {
    StringBuilder descriptor = new StringBuilder("(");
    for (Class<?> parameterType : parameterTypes) {
      descriptor.append(descriptor(parameterType));
    }
    return descriptor.append(")V").toString();
  }

  private static String descriptor(Class<?> type) {
    if (type == void.class) {
      return "V";
    }
    if (type == boolean.class) {
      return "Z";
    }
    if (type == byte.class) {
      return "B";
    }
    if (type == char.class) {
      return "C";
    }
    if (type == short.class) {
      return "S";
    }
    if (type == int.class) {
      return "I";
    }
    if (type == long.class) {
      return "J";
    }
    if (type == float.class) {
      return "F";
    }
    if (type == double.class) {
      return "D";
    }
    if (type.isArray()) {
      return type.getName().replace('.', '/');
    }
    return 'L' + internalName(type) + ';';
  }

  private static String internalName(Class<?> type) {
    return type.getName().replace('.', '/');
  }
}
