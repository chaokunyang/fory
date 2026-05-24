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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.fory.platform.internal.DefineClass;

final class HiddenFieldAccessorFactory {
  private static final int JAVA5_CLASS_VERSION = 49;
  private static final int ACC_PUBLIC = 0x0001;
  private static final int ACC_FINAL = 0x0010;
  private static final int ACC_SUPER = 0x0020;
  private static final String FIELD_ACCESSOR = "org/apache/fory/reflect/FieldAccessor";
  private static final String FIELD_CTR_DESC = "(Ljava/lang/reflect/Field;)V";
  private static final AtomicInteger IDS = new AtomicInteger();

  private HiddenFieldAccessorFactory() {}

  static FieldAccessor create(Field field) {
    if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
      return null;
    }
    try {
      byte[] bytes = new AccessorClass(field).bytes();
      Class<?> accessorClass = DefineClass.defineHiddenNestmate(field.getDeclaringClass(), bytes);
      Constructor<?> constructor = accessorClass.getDeclaredConstructor(Field.class);
      constructor.setAccessible(true);
      return (FieldAccessor) constructor.newInstance(field);
    } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
      return null;
    }
  }

  private static final class AccessorClass {
    private final Field field;
    private final Class<?> fieldType;
    private final String owner;
    private final String thisClass;
    private final String fieldDesc;
    private final Pool pool = new Pool();

    private int codeUtf8;
    private int initName;
    private int initDesc;
    private int getName;
    private int getDesc;
    private int setName;
    private int setDesc;
    private int fieldRef;
    private int ownerClass;
    private int thisClassIndex;
    private int fieldAccessorClass;
    private int superCtr;

    private AccessorClass(Field field) {
      this.field = field;
      this.fieldType = field.getType();
      owner = internalName(field.getDeclaringClass());
      thisClass = owner + "$ForyFieldAccessor$" + IDS.incrementAndGet();
      fieldDesc = descriptor(fieldType);
    }

    private byte[] bytes() {
      try {
        preparePool();
        List<MethodDef> methods = methods();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0xCAFEBABE);
        out.writeShort(0);
        out.writeShort(JAVA5_CLASS_VERSION);
        pool.writeTo(out);
        out.writeShort(ACC_FINAL | ACC_SUPER);
        out.writeShort(thisClassIndex);
        out.writeShort(fieldAccessorClass);
        out.writeShort(0);
        out.writeShort(0);
        out.writeShort(methods.size());
        for (MethodDef method : methods) {
          method.writeTo(out, codeUtf8);
        }
        out.writeShort(0);
        return bytes.toByteArray();
      } catch (IOException e) {
        throw new IllegalStateException("Failed to build field accessor bytecode", e);
      }
    }

    private void preparePool() {
      codeUtf8 = pool.utf8("Code");
      initName = pool.utf8("<init>");
      initDesc = pool.utf8(FIELD_CTR_DESC);
      getName = pool.utf8("get");
      getDesc = pool.utf8("(Ljava/lang/Object;)Ljava/lang/Object;");
      setName = pool.utf8("set");
      setDesc = pool.utf8("(Ljava/lang/Object;Ljava/lang/Object;)V");
      thisClassIndex = pool.classInfo(thisClass);
      ownerClass = pool.classInfo(owner);
      fieldAccessorClass = pool.classInfo(FIELD_ACCESSOR);
      superCtr = pool.methodRef(FIELD_ACCESSOR, "<init>", FIELD_CTR_DESC);
      fieldRef = pool.fieldRef(owner, field.getName(), fieldDesc);
      if (fieldType.isPrimitive()) {
        Primitive primitive = Primitive.of(fieldType);
        pool.classInfo(primitive.wrapper);
        pool.methodRef(primitive.wrapper, "valueOf", primitive.valueOfDesc);
        pool.methodRef(primitive.wrapper, primitive.unboxName, primitive.unboxDesc);
      } else {
        pool.classInfo(castName(fieldType));
      }
    }

    private List<MethodDef> methods() throws IOException {
      List<MethodDef> methods = new ArrayList<>();
      methods.add(method(ACC_PUBLIC, initName, initDesc, 2, 2, constructorCode()));
      methods.add(method(ACC_PUBLIC, getName, getDesc, 4, 2, objectGetterCode()));
      if (fieldType.isPrimitive()) {
        Primitive primitive = Primitive.of(fieldType);
        methods.add(
            method(
                ACC_PUBLIC,
                pool.utf8(primitive.getterName),
                pool.utf8("(Ljava/lang/Object;)" + primitive.desc),
                3,
                2,
                primitiveGetterCode(primitive)));
        if (!Modifier.isFinal(field.getModifiers())) {
          methods.add(method(ACC_PUBLIC, setName, setDesc, 4, 3, objectSetterCode(primitive)));
          methods.add(
              method(
                  ACC_PUBLIC,
                  pool.utf8(primitive.setterName),
                  pool.utf8("(Ljava/lang/Object;" + primitive.desc + ")V"),
                  4,
                  primitive.maxLocals,
                  primitiveSetterCode(primitive)));
        }
      } else {
        if (!Modifier.isFinal(field.getModifiers())) {
          methods.add(method(ACC_PUBLIC, setName, setDesc, 3, 3, referenceSetterCode()));
        }
      }
      return methods;
    }

    private byte[] constructorCode() throws IOException {
      Code code = new Code();
      code.u1(0x2A); // aload_0
      code.u1(0x2B); // aload_1
      code.u1(0xB7).u2(superCtr); // invokespecial
      code.u1(0xB1); // return
      return code.bytes();
    }

    private byte[] objectGetterCode() throws IOException {
      Code code = directReadCode();
      if (fieldType.isPrimitive()) {
        Primitive primitive = Primitive.of(fieldType);
        code.u1(0xB8).u2(pool.methodRef(primitive.wrapper, "valueOf", primitive.valueOfDesc));
      }
      code.u1(0xB0); // areturn
      return code.bytes();
    }

    private byte[] primitiveGetterCode(Primitive primitive) throws IOException {
      Code code = directReadCode();
      code.u1(primitive.returnOpcode);
      return code.bytes();
    }

    private Code directReadCode() throws IOException {
      Code code = new Code();
      code.u1(0x2B); // aload_1
      code.u1(0xC0).u2(ownerClass); // checkcast
      code.u1(0xB4).u2(fieldRef); // getfield
      return code;
    }

    private byte[] objectSetterCode(Primitive primitive) throws IOException {
      Code code = setterPrefix();
      code.u1(0x2C); // aload_2
      code.u1(0xC0).u2(pool.classInfo(primitive.wrapper));
      code.u1(0xB6).u2(pool.methodRef(primitive.wrapper, primitive.unboxName, primitive.unboxDesc));
      code.u1(0xB5).u2(fieldRef); // putfield
      code.u1(0xB1); // return
      return code.bytes();
    }

    private byte[] primitiveSetterCode(Primitive primitive) throws IOException {
      Code code = setterPrefix();
      code.u1(primitive.loadOpcode);
      code.u1(0xB5).u2(fieldRef); // putfield
      code.u1(0xB1); // return
      return code.bytes();
    }

    private byte[] referenceSetterCode() throws IOException {
      Code code = setterPrefix();
      code.u1(0x2C); // aload_2
      code.u1(0xC0).u2(pool.classInfo(castName(fieldType)));
      code.u1(0xB5).u2(fieldRef); // putfield
      code.u1(0xB1); // return
      return code.bytes();
    }

    private Code setterPrefix() throws IOException {
      Code code = new Code();
      code.u1(0x2B); // aload_1
      code.u1(0xC0).u2(ownerClass); // checkcast
      return code;
    }

    private MethodDef method(
        int access,
        int name,
        int desc,
        int maxStack,
        int maxLocals,
        byte[] code)
        throws IOException {
      return new MethodDef(access, name, desc, maxStack, maxLocals, code);
    }
  }

  private static final class MethodDef {
    private final int access;
    private final int name;
    private final int desc;
    private final int maxStack;
    private final int maxLocals;
    private final byte[] code;

    private MethodDef(int access, int name, int desc, int maxStack, int maxLocals, byte[] code) {
      this.access = access;
      this.name = name;
      this.desc = desc;
      this.maxStack = maxStack;
      this.maxLocals = maxLocals;
      this.code = code;
    }

    private void writeTo(DataOutputStream out, int codeUtf8) throws IOException {
      out.writeShort(access);
      out.writeShort(name);
      out.writeShort(desc);
      out.writeShort(1);
      out.writeShort(codeUtf8);
      out.writeInt(12 + code.length);
      out.writeShort(maxStack);
      out.writeShort(maxLocals);
      out.writeInt(code.length);
      out.write(code);
      out.writeShort(0);
      out.writeShort(0);
    }
  }

  private static String descriptor(Class<?> type) {
    if (type == void.class) {
      return "V";
    } else if (type == boolean.class) {
      return "Z";
    } else if (type == byte.class) {
      return "B";
    } else if (type == char.class) {
      return "C";
    } else if (type == short.class) {
      return "S";
    } else if (type == int.class) {
      return "I";
    } else if (type == long.class) {
      return "J";
    } else if (type == float.class) {
      return "F";
    } else if (type == double.class) {
      return "D";
    } else if (type.isArray()) {
      return type.getName().replace('.', '/');
    }
    return "L" + internalName(type) + ";";
  }

  private static String castName(Class<?> type) {
    if (type.isArray()) {
      return descriptor(type);
    }
    return internalName(type);
  }

  private static String internalName(Class<?> type) {
    return type.getName().replace('.', '/');
  }

  private static final class Code {
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final DataOutputStream out = new DataOutputStream(bytes);

    private Code u1(int value) throws IOException {
      out.writeByte(value);
      return this;
    }

    private Code u2(int value) throws IOException {
      out.writeShort(value);
      return this;
    }

    private byte[] bytes() throws IOException {
      out.flush();
      return bytes.toByteArray();
    }
  }

  private static final class Pool {
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final DataOutputStream out = new DataOutputStream(bytes);
    private int count = 1;

    private int utf8(String value) {
      try {
        int index = count++;
        out.writeByte(1);
        out.writeUTF(value);
        return index;
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }

    private int classInfo(String name) {
      try {
        int nameIndex = utf8(name);
        int index = count++;
        out.writeByte(7);
        out.writeShort(nameIndex);
        return index;
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }

    private int nameAndType(String name, String desc) {
      try {
        int nameIndex = utf8(name);
        int descIndex = utf8(desc);
        int index = count++;
        out.writeByte(12);
        out.writeShort(nameIndex);
        out.writeShort(descIndex);
        return index;
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }

    private int fieldRef(String owner, String name, String desc) {
      try {
        int ownerIndex = classInfo(owner);
        int nameAndTypeIndex = nameAndType(name, desc);
        int index = count++;
        out.writeByte(9);
        out.writeShort(ownerIndex);
        out.writeShort(nameAndTypeIndex);
        return index;
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }

    private int methodRef(String owner, String name, String desc) {
      try {
        int ownerIndex = classInfo(owner);
        int nameAndTypeIndex = nameAndType(name, desc);
        int index = count++;
        out.writeByte(10);
        out.writeShort(ownerIndex);
        out.writeShort(nameAndTypeIndex);
        return index;
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }

    private void writeTo(DataOutputStream target) throws IOException {
      out.flush();
      target.writeShort(count);
      bytes.writeTo(target);
    }
  }

  private enum Primitive {
    BOOLEAN(
        boolean.class,
        "Z",
        "java/lang/Boolean",
        "(Z)Ljava/lang/Boolean;",
        "booleanValue",
        "()Z",
        "getBoolean",
        "putBoolean",
        0x1C,
        0xAC,
        3),
    BYTE(
        byte.class,
        "B",
        "java/lang/Byte",
        "(B)Ljava/lang/Byte;",
        "byteValue",
        "()B",
        "getByte",
        "putByte",
        0x1C,
        0xAC,
        3),
    CHAR(
        char.class,
        "C",
        "java/lang/Character",
        "(C)Ljava/lang/Character;",
        "charValue",
        "()C",
        "getChar",
        "putChar",
        0x1C,
        0xAC,
        3),
    SHORT(
        short.class,
        "S",
        "java/lang/Short",
        "(S)Ljava/lang/Short;",
        "shortValue",
        "()S",
        "getShort",
        "putShort",
        0x1C,
        0xAC,
        3),
    INT(
        int.class,
        "I",
        "java/lang/Integer",
        "(I)Ljava/lang/Integer;",
        "intValue",
        "()I",
        "getInt",
        "putInt",
        0x1C,
        0xAC,
        3),
    LONG(
        long.class,
        "J",
        "java/lang/Long",
        "(J)Ljava/lang/Long;",
        "longValue",
        "()J",
        "getLong",
        "putLong",
        0x20,
        0xAD,
        4),
    FLOAT(
        float.class,
        "F",
        "java/lang/Float",
        "(F)Ljava/lang/Float;",
        "floatValue",
        "()F",
        "getFloat",
        "putFloat",
        0x24,
        0xAE,
        3),
    DOUBLE(
        double.class,
        "D",
        "java/lang/Double",
        "(D)Ljava/lang/Double;",
        "doubleValue",
        "()D",
        "getDouble",
        "putDouble",
        0x28,
        0xAF,
        4);

    private final Class<?> type;
    private final String desc;
    private final String wrapper;
    private final String valueOfDesc;
    private final String unboxName;
    private final String unboxDesc;
    private final String getterName;
    private final String setterName;
    private final int loadOpcode;
    private final int returnOpcode;
    private final int maxLocals;

    Primitive(
        Class<?> type,
        String desc,
        String wrapper,
        String valueOfDesc,
        String unboxName,
        String unboxDesc,
        String getterName,
        String setterName,
        int loadOpcode,
        int returnOpcode,
        int maxLocals) {
      this.type = type;
      this.desc = desc;
      this.wrapper = wrapper;
      this.valueOfDesc = valueOfDesc;
      this.unboxName = unboxName;
      this.unboxDesc = unboxDesc;
      this.getterName = getterName;
      this.setterName = setterName;
      this.loadOpcode = loadOpcode;
      this.returnOpcode = returnOpcode;
      this.maxLocals = maxLocals;
    }

    private static Primitive of(Class<?> type) {
      for (Primitive primitive : values()) {
        if (primitive.type == type) {
          return primitive;
        }
      }
      throw new IllegalArgumentException("Not a primitive field type: " + type);
    }
  }
}
