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

/** Method bytecode writer for the narrow instruction set used by Fory helper generation. */
@Internal
public final class MethodWriter {
  private final ClassWriter.ConstantPool constantPool;
  private final int accessFlags;
  private final int nameIndex;
  private final int descriptorIndex;
  private final int maxStack;
  private final int maxLocals;
  private final ByteVector code = new ByteVector();

  MethodWriter(
      ClassWriter.ConstantPool constantPool,
      int accessFlags,
      String methodName,
      String descriptor,
      int maxStack,
      int maxLocals) {
    this.constantPool = constantPool;
    this.accessFlags = accessFlags;
    this.nameIndex = constantPool.utf8(methodName);
    this.descriptorIndex = constantPool.utf8(descriptor);
    this.maxStack = maxStack;
    this.maxLocals = maxLocals;
  }

  public MethodWriter aload(int slot) {
    load(0x19, 0x2a, slot);
    return this;
  }

  public MethodWriter load(Type type, int slot) {
    switch (type.loadOpcode()) {
      case 0x15:
        load(0x15, 0x1a, slot);
        return this;
      case 0x16:
        load(0x16, 0x1e, slot);
        return this;
      case 0x17:
        load(0x17, 0x22, slot);
        return this;
      case 0x18:
        load(0x18, 0x26, slot);
        return this;
      default:
        load(0x19, 0x2a, slot);
        return this;
    }
  }

  private void load(int opcode, int compactOpcodeBase, int slot) {
    if (slot >= 0 && slot <= 3) {
      code.putByte(compactOpcodeBase + slot);
    } else {
      code.putByte(opcode);
      code.putByte(slot);
    }
  }

  public MethodWriter invokespecial(String owner, String name, String descriptor) {
    code.putByte(0xb7);
    code.putShort(constantPool.methodRef(owner, name, descriptor));
    return this;
  }

  public MethodWriter invokevirtual(String owner, String name, String descriptor) {
    code.putByte(0xb6);
    code.putShort(constantPool.methodRef(owner, name, descriptor));
    return this;
  }

  public MethodWriter getfield(String owner, String name, String descriptor) {
    code.putByte(0xb4);
    code.putShort(constantPool.fieldRef(owner, name, descriptor));
    return this;
  }

  public MethodWriter putfield(String owner, String name, String descriptor) {
    code.putByte(0xb5);
    code.putShort(constantPool.fieldRef(owner, name, descriptor));
    return this;
  }

  public MethodWriter returnValue(Type type) {
    code.putByte(type.returnOpcode());
    return this;
  }

  public MethodWriter pop(Type type) {
    if (!type.isVoid()) {
      code.putByte(type.slots() == 2 ? 0x58 : 0x57);
    }
    return this;
  }

  public MethodWriter returnVoid() {
    code.putByte(0xb1);
    return this;
  }

  void writeTo(ByteVector out, int codeAttributeName) {
    out.putShort(accessFlags);
    out.putShort(nameIndex);
    out.putShort(descriptorIndex);
    out.putShort(1);
    out.putShort(codeAttributeName);
    out.putInt(12 + code.length());
    out.putShort(maxStack);
    out.putShort(maxLocals);
    out.putInt(code.length());
    out.putBytes(code.toByteArray());
    out.putShort(0); // exception table
    out.putShort(0); // code attributes
  }
}
