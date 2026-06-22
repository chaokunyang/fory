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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.fory.annotation.Internal;

/** Minimal classfile writer for small Fory-generated helper classes. */
@Internal
public final class ClassWriter {
  public static final int ACC_PUBLIC = 0x0001;
  public static final int ACC_PRIVATE = 0x0002;
  public static final int ACC_STATIC = 0x0008;
  public static final int ACC_FINAL = 0x0010;
  public static final int ACC_SUPER = 0x0020;

  private static final int JAVA8_MAJOR_VERSION = 52;

  private final ConstantPool constantPool = new ConstantPool();
  private final int accessFlags;
  private final String className;
  private final String superName;
  private final List<MethodWriter> methods = new ArrayList<>();

  public ClassWriter(int accessFlags, String className, String superName) {
    this.accessFlags = accessFlags;
    this.className = className;
    this.superName = superName;
  }

  public MethodWriter visitMethod(
      int accessFlags, String methodName, String descriptor, int maxStack, int maxLocals) {
    MethodWriter method =
        new MethodWriter(constantPool, accessFlags, methodName, descriptor, maxStack, maxLocals);
    methods.add(method);
    return method;
  }

  public byte[] toByteArray() {
    ByteVector out = new ByteVector();
    out.putInt(0xCAFEBABE);
    out.putShort(0);
    out.putShort(JAVA8_MAJOR_VERSION);
    int thisClass = constantPool.classInfo(className);
    int superClass = constantPool.classInfo(superName);
    int codeAttributeName = constantPool.utf8("Code");
    byte[] constantPoolBytes = constantPool.toByteArray();
    out.putShort(constantPool.size());
    out.putBytes(constantPoolBytes);
    out.putShort(accessFlags);
    out.putShort(thisClass);
    out.putShort(superClass);
    out.putShort(0); // interfaces
    out.putShort(0); // fields
    out.putShort(methods.size());
    for (MethodWriter method : methods) {
      method.writeTo(out, codeAttributeName);
    }
    out.putShort(0); // class attributes
    return out.toByteArray();
  }

  static final class ConstantPool {
    private final ByteVector entries = new ByteVector();
    private final Map<String, Integer> indexes = new LinkedHashMap<>();
    private int size = 1;

    int size() {
      return size;
    }

    int utf8(String value) {
      String key = "U:" + value;
      Integer index = indexes.get(key);
      if (index != null) {
        return index;
      }
      int next = size++;
      indexes.put(key, next);
      entries.putByte(1);
      entries.putUTF(value);
      return next;
    }

    int classInfo(String name) {
      String key = "C:" + name;
      Integer index = indexes.get(key);
      if (index != null) {
        return index;
      }
      int nameIndex = utf8(name);
      int next = size++;
      indexes.put(key, next);
      entries.putByte(7);
      entries.putShort(nameIndex);
      return next;
    }

    int nameAndType(String name, String descriptor) {
      String key = "NT:" + name + ':' + descriptor;
      Integer index = indexes.get(key);
      if (index != null) {
        return index;
      }
      int nameIndex = utf8(name);
      int descriptorIndex = utf8(descriptor);
      int next = size++;
      indexes.put(key, next);
      entries.putByte(12);
      entries.putShort(nameIndex);
      entries.putShort(descriptorIndex);
      return next;
    }

    int fieldRef(String owner, String name, String descriptor) {
      String key = "F:" + owner + ':' + name + ':' + descriptor;
      Integer index = indexes.get(key);
      if (index != null) {
        return index;
      }
      int ownerIndex = classInfo(owner);
      int nameAndTypeIndex = nameAndType(name, descriptor);
      int next = size++;
      indexes.put(key, next);
      entries.putByte(9);
      entries.putShort(ownerIndex);
      entries.putShort(nameAndTypeIndex);
      return next;
    }

    int methodRef(String owner, String name, String descriptor) {
      String key = "M:" + owner + ':' + name + ':' + descriptor;
      Integer index = indexes.get(key);
      if (index != null) {
        return index;
      }
      int ownerIndex = classInfo(owner);
      int nameAndTypeIndex = nameAndType(name, descriptor);
      int next = size++;
      indexes.put(key, next);
      entries.putByte(10);
      entries.putShort(ownerIndex);
      entries.putShort(nameAndTypeIndex);
      return next;
    }

    byte[] toByteArray() {
      return entries.toByteArray();
    }
  }
}
