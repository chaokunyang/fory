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

package org.apache.fory.json.codegen;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.fory.codegen.CodeGenerator;
import org.apache.fory.codegen.CompileUnit;
import org.apache.fory.json.ForyJsonException;
import org.apache.fory.json.codec.BaseObjectCodec;
import org.apache.fory.json.codec.Latin1ObjectReader;
import org.apache.fory.json.codec.ObjectCodecs;
import org.apache.fory.json.codec.ObjectReader;
import org.apache.fory.json.codec.StringObjectWriter;
import org.apache.fory.json.codec.Utf16ObjectReader;
import org.apache.fory.json.codec.Utf8ObjectReader;
import org.apache.fory.json.codec.Utf8ObjectWriter;
import org.apache.fory.json.meta.JsonFieldInfo;
import org.apache.fory.json.meta.JsonFieldKind;
import org.apache.fory.json.resolver.JsonTypeResolver;
import org.apache.fory.platform.JdkVersion;
import org.apache.fory.reflect.FieldAccessor;
import org.apache.fory.reflect.InstanceFieldAccessors;

public final class JsonCodegen {
  private static final String PACKAGE = "org.apache.fory.json.codegen";
  private static final int GENERIC_READER = 0;
  private static final int LATIN1_READER = 1;
  private static final int UTF16_READER = 2;
  private static final int UTF8_READER = 3;
  private static final BaseObjectCodec[] EMPTY_CODECS = new BaseObjectCodec[0];
  private static final AtomicInteger ID = new AtomicInteger();

  private final boolean writeNullFields;
  private final CodeGenerator codeGenerator;
  private final ClassLoader jsonLoader;

  public JsonCodegen(boolean writeNullFields) {
    this.writeNullFields = writeNullFields;
    jsonLoader = JsonCodegen.class.getClassLoader();
    codeGenerator = new CodeGenerator(jsonLoader);
  }

  public ObjectCodecs compile(BaseObjectCodec objectCodec, JsonTypeResolver typeResolver) {
    Class<?> type = objectCodec.type();
    if (!canCompile(type)) {
      return null;
    }
    JsonFieldInfo[] writeProperties = objectCodec.writeFields();
    for (int i = 0; i < writeProperties.length; i++) {
      if (!canCompileWrite(writeProperties[i])) {
        return null;
      }
    }
    JsonFieldInfo[] readProperties = objectCodec.readFields();
    for (int i = 0; i < readProperties.length; i++) {
      if (!canCompileRead(readProperties[i])) {
        return null;
      }
    }
    String className = className(type);
    Utf8ObjectWriter utf8Writer =
        (Utf8ObjectWriter)
            compileWriter(className + "_Utf8", type, writeProperties, EMPTY_CODECS, true);
    if (utf8Writer == null) {
      return null;
    }
    StringObjectWriter stringWriter =
        (StringObjectWriter)
            compileWriter(className + "_String", type, writeProperties, EMPTY_CODECS, false);
    if (stringWriter == null) {
      return null;
    }
    BaseObjectCodec[] readCodecs = readCodecs(objectCodec, typeResolver);
    ObjectReader reader =
        (ObjectReader) compileReader(className + "_Reader", type, readProperties, readCodecs);
    if (reader == null) {
      return null;
    }
    return new ObjectCodecs(
        stringWriter,
        utf8Writer,
        reader,
        (Latin1ObjectReader) reader,
        (Utf16ObjectReader) reader,
        (Utf8ObjectReader) reader);
  }

  private Object compileWriter(
      String className,
      Class<?> type,
      JsonFieldInfo[] properties,
      BaseObjectCodec[] nestedCodecs,
      boolean utf8) {
    String code = genCode(className, type, properties, utf8);
    try {
      CompileUnit unit = new CompileUnit(PACKAGE, className, code, JsonCodegen.class);
      Class<?> writerClass = codeGenerator.compileAndLoad(unit, state -> state.lock.lock());
      Constructor<?> constructor =
          writerClass.getDeclaredConstructor(JsonFieldInfo[].class, BaseObjectCodec[].class);
      constructor.setAccessible(true);
      return constructor.newInstance(properties, nestedCodecs);
    } catch (Throwable e) {
      throw new ForyJsonException("Cannot compile generated JSON writer " + className, e);
    }
  }

  private Object compileReader(
      String className, Class<?> type, JsonFieldInfo[] properties, BaseObjectCodec[] nestedCodecs) {
    String code = genReaderCode(className, type, properties);
    try {
      CompileUnit unit = new CompileUnit(PACKAGE, className, code, JsonCodegen.class);
      Class<?> readerClass = codeGenerator.compileAndLoad(unit, state -> state.lock.lock());
      Constructor<?> constructor =
          readerClass.getDeclaredConstructor(JsonFieldInfo[].class, BaseObjectCodec[].class);
      constructor.setAccessible(true);
      return constructor.newInstance(properties, nestedCodecs);
    } catch (Throwable e) {
      throw new ForyJsonException("Cannot compile generated JSON reader " + className, e);
    }
  }

  private BaseObjectCodec[] readCodecs(BaseObjectCodec objectCodec, JsonTypeResolver typeResolver) {
    JsonFieldInfo[] properties = objectCodec.readFields();
    BaseObjectCodec[] nestedCodecs = new BaseObjectCodec[properties.length];
    Class<?> type = objectCodec.type();
    for (int i = 0; i < properties.length; i++) {
      Class<?> nestedType = readNestedType(properties[i]);
      if (nestedType != null && nestedType != type) {
        nestedCodecs[i] = typeResolver.getObjectCodec(nestedType);
      }
    }
    return nestedCodecs;
  }

  private static Class<?> readNestedType(JsonFieldInfo property) {
    if (property.readKind() == JsonFieldKind.OBJECT
        && property.readRawType() != Object.class
        && property.readTypeInfo().codec() instanceof BaseObjectCodec) {
      return property.readRawType();
    }
    return null;
  }

  private boolean canCompileWrite(JsonFieldInfo property) {
    Field field = property.writeField();
    if (field == null) {
      return false;
    }
    if (!isInstanceAccessor(property.writeFieldAccessor())) {
      return false;
    }
    Class<?> rawType = property.writeRawType();
    if (rawType != null && !rawType.isPrimitive() && !isVisible(rawType)) {
      return false;
    }
    Class<?> elementType = property.writeElementRawType();
    return !isPojo(elementType) || isVisible(elementType);
  }

  private boolean canCompileRead(JsonFieldInfo property) {
    if (property.readAccessor() == null) {
      return false;
    }
    Class<?> rawType = property.readRawType();
    if (rawType != null && !rawType.isPrimitive() && !isVisible(rawType)) {
      return false;
    }
    Class<?> elementType = property.readElementRawType();
    return !isPojo(elementType) || isVisible(elementType);
  }

  private boolean canCompile(Class<?> type) {
    return JdkVersion.MAJOR_VERSION >= 15
        && CodeGenerator.sourcePublicAccessible(type)
        && isVisible(type);
  }

  private static boolean isInstanceAccessor(FieldAccessor accessor) {
    return accessor instanceof InstanceFieldAccessors.InstanceAccessor;
  }

  private boolean isVisible(Class<?> type) {
    if (type.isPrimitive()) {
      return true;
    }
    while (type.isArray()) {
      type = type.getComponentType();
    }
    if (type.isPrimitive()) {
      return true;
    }
    String name = type.getName();
    try {
      return Class.forName(name, false, jsonLoader) == type;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  private String genReaderCode(String className, Class<?> type, JsonFieldInfo[] properties) {
    StringBuilder code = new StringBuilder(4096);
    code.append("package ").append(PACKAGE).append(";\n");
    code.append("import org.apache.fory.json.codec.BaseObjectCodec;\n");
    code.append("import org.apache.fory.json.codec.Latin1ObjectReader;\n");
    code.append("import org.apache.fory.json.codec.ObjectReader;\n");
    code.append("import org.apache.fory.json.codec.Utf16ObjectReader;\n");
    code.append("import org.apache.fory.json.codec.Utf8ObjectReader;\n");
    code.append("import org.apache.fory.json.meta.JsonFieldAccessor;\n");
    code.append("import org.apache.fory.json.meta.JsonFieldInfo;\n");
    code.append("import org.apache.fory.json.meta.JsonFieldTable;\n");
    code.append("import org.apache.fory.json.reader.JsonReader;\n");
    code.append("import org.apache.fory.json.reader.Latin1StringJsonReader;\n");
    code.append("import org.apache.fory.json.reader.Utf16StringJsonReader;\n");
    code.append("import org.apache.fory.json.reader.Utf8JsonReader;\n");
    code.append("import org.apache.fory.json.resolver.JsonTypeResolver;\n");
    code.append("final class ")
        .append(className)
        .append(
            " implements ObjectReader, Latin1ObjectReader, Utf16ObjectReader, Utf8ObjectReader {\n");
    code.append("  private final String[] fieldNames;\n");
    code.append("  private final long[] fieldHashes;\n");
    for (int i = 0; i < properties.length; i++) {
      code.append("  private final JsonFieldInfo p").append(i).append(";\n");
      code.append("  private final JsonFieldAccessor a").append(i).append(";\n");
      if (usesReadObjectCodec(properties[i])) {
        code.append("  private final BaseObjectCodec c").append(i).append(";\n");
      }
    }
    code.append("  ")
        .append(className)
        .append("(JsonFieldInfo[] properties, BaseObjectCodec[] objectCodecs) {\n");
    code.append("    this.fieldNames = new String[properties.length];\n");
    code.append("    this.fieldHashes = new long[properties.length];\n");
    for (int i = 0; i < properties.length; i++) {
      code.append("    this.fieldNames[")
          .append(i)
          .append("] = properties[")
          .append(i)
          .append("].name();\n");
      code.append("    this.fieldHashes[")
          .append(i)
          .append("] = properties[")
          .append(i)
          .append("].nameHash();\n");
      code.append("    this.p").append(i).append(" = properties[").append(i).append("];\n");
      code.append("    this.a")
          .append(i)
          .append(" = properties[")
          .append(i)
          .append("].readAccessor();\n");
      if (usesReadObjectCodec(properties[i])) {
        code.append("    this.c").append(i).append(" = objectCodecs[").append(i).append("];\n");
      }
    }
    code.append("  }\n");
    appendReadMethod(code, "read", "JsonReader", type, properties, GENERIC_READER);
    appendReadMethod(code, "readLatin1", "Latin1StringJsonReader", type, properties, LATIN1_READER);
    appendReadMethod(code, "readUtf16", "Utf16StringJsonReader", type, properties, UTF16_READER);
    appendReadMethod(code, "readUtf8", "Utf8JsonReader", type, properties, UTF8_READER);
    code.append("}\n");
    return code.toString();
  }

  private void appendReadMethod(
      StringBuilder code,
      String methodName,
      String readerType,
      Class<?> type,
      JsonFieldInfo[] properties,
      int readerMode) {
    if (readerMode != GENERIC_READER) {
      appendFastRead(code, methodName, readerType, type, properties, readerMode);
      appendSlowRead(code, methodName + "Slow", readerType, properties, readerMode);
      return;
    }
    code.append("  public Object ")
        .append(methodName)
        .append("(")
        .append(readerType)
        .append(" reader, BaseObjectCodec owner, JsonTypeResolver typeResolver) {\n");
    appendNewObject(code, type);
    appendExpect(code, readerMode, '{', "    ");
    code.append("    if (").append(consumeCall(readerMode, '}')).append(") {\n");
    code.append("      return object;\n");
    code.append("    }\n");
    code.append("    JsonFieldTable fieldTable = owner.readTable();\n");
    code.append("    long[] localFieldHashes = fieldHashes;\n");
    code.append("    int expectedIndex = 0;\n");
    code.append("    do {\n");
    code.append("      int fieldIndex = expectedIndex < localFieldHashes.length\n");
    code.append(
        "          ? reader.readFieldIndex(fieldTable, localFieldHashes[expectedIndex], expectedIndex)\n");
    code.append("          : reader.readFieldIndex(fieldTable);\n");
    appendExpect(code, readerMode, ':', "      ");
    appendFieldSwitch(code, properties, readerMode, "      ");
    code.append("      if (fieldIndex >= 0) {\n");
    code.append("        expectedIndex = fieldIndex + 1;\n");
    code.append("      }\n");
    code.append("    } while (").append(consumeCall(readerMode, ',')).append(");\n");
    appendExpect(code, readerMode, '}', "    ");
    code.append("    return object;\n");
    code.append("  }\n");
  }

  private void appendFastRead(
      StringBuilder code,
      String methodName,
      String readerType,
      Class<?> type,
      JsonFieldInfo[] properties,
      int readerMode) {
    String slowMethod = methodName + "Slow";
    code.append("  public Object ")
        .append(methodName)
        .append("(")
        .append(readerType)
        .append(" reader, BaseObjectCodec owner, JsonTypeResolver typeResolver) {\n");
    appendNewObject(code, type);
    appendExpect(code, readerMode, '{', "    ");
    code.append("    if (").append(consumeCall(readerMode, '}')).append(") {\n");
    code.append("      return object;\n");
    code.append("    }\n");
    if (properties.length == 0) {
      code.append("    ").append(slowMethod).append("(reader, owner, typeResolver, object, 0);\n");
      code.append("    return object;\n");
      code.append("  }\n");
      return;
    }
    code.append("    JsonFieldTable fieldTable = owner.readTable();\n");
    code.append("    long[] localFieldHashes = fieldHashes;\n");
    for (int i = 1; i < properties.length; i++) {
      code.append("    boolean skip").append(i).append(" = false;\n");
    }
    for (int i = 0; i < properties.length; i++) {
      String indent = "    ";
      if (i > 0) {
        code.append("    if (!skip").append(i).append(") {\n");
        indent = "      ";
      }
      appendFastReadField(code, slowMethod, properties, i, indent, readerMode);
      if (i > 0) {
        code.append("    }\n");
      }
    }
    code.append("    return object;\n");
    code.append("  }\n");
  }

  private void appendFastReadField(
      StringBuilder code,
      String slowMethod,
      JsonFieldInfo[] properties,
      int index,
      String indent,
      int readerMode) {
    code.append(indent)
        .append("long fieldHash")
        .append(index)
        .append(" = reader.readFieldNameHash();\n");
    code.append(indent)
        .append("if (fieldHash")
        .append(index)
        .append(" != localFieldHashes[")
        .append(index)
        .append("]) {\n");
    if (index + 1 < properties.length) {
      code.append(indent)
          .append("  if (fieldHash")
          .append(index)
          .append(" == localFieldHashes[")
          .append(index + 1)
          .append("]) {\n");
      appendExpect(code, readerMode, ':', indent + "    ");
      readField(code, properties[index + 1], index + 1, indent + "    ", readerMode);
      appendFieldEnd(code, slowMethod, properties.length, index + 1, indent + "    ", readerMode);
      code.append(indent).append("    skip").append(index + 1).append(" = true;\n");
      code.append(indent).append("  } else {\n");
      appendSlowConsumedReturn(
          code, slowMethod, index, "fieldTable.index(fieldHash" + index + ")", indent + "    ");
      code.append(indent).append("  }\n");
    } else {
      appendSlowConsumedReturn(
          code, slowMethod, index, "fieldTable.index(fieldHash" + index + ")", indent + "  ");
    }
    code.append(indent).append("} else {\n");
    appendExpect(code, readerMode, ':', indent + "  ");
    readField(code, properties[index], index, indent + "  ", readerMode);
    appendFieldEnd(code, slowMethod, properties.length, index, indent + "  ", readerMode);
    code.append(indent).append("}\n");
  }

  private static void appendNewObject(StringBuilder code, Class<?> type) {
    if (canUseDirectNew(type)) {
      String typeName = sourceName(type);
      code.append("    ")
          .append(typeName)
          .append(" object = new ")
          .append(typeName)
          .append("();\n");
    } else {
      code.append("    Object object = owner.newInstance();\n");
    }
  }

  private static boolean canUseDirectNew(Class<?> type) {
    try {
      Constructor<?> constructor = type.getConstructor();
      return constructor.getExceptionTypes().length == 0;
    } catch (NoSuchMethodException e) {
      return false;
    }
  }

  private static void appendSlowReturn(
      StringBuilder code, String slowMethod, int index, String indent) {
    code.append(indent).append(slowMethod).append("(reader, owner, typeResolver, object, ");
    code.append(index).append(");\n");
    code.append(indent).append("return object;\n");
  }

  private static void appendSlowConsumedReturn(
      StringBuilder code, String slowMethod, int index, String firstFieldIndex, String indent) {
    code.append(indent).append(slowMethod).append("(reader, owner, typeResolver, object, ");
    code.append(index).append(", ").append(firstFieldIndex).append(");\n");
    code.append(indent).append("return object;\n");
  }

  private static void appendFieldEnd(
      StringBuilder code,
      String slowMethod,
      int propertyCount,
      int index,
      String indent,
      int readerMode) {
    if (index + 1 < propertyCount) {
      code.append(indent).append("if (!").append(consumeCall(readerMode, ',')).append(") {\n");
      appendExpect(code, readerMode, '}', indent + "  ");
      code.append(indent).append("  return object;\n");
      code.append(indent).append("}\n");
    } else {
      code.append(indent).append("if (").append(consumeCall(readerMode, ',')).append(") {\n");
      code.append(indent).append("  ").append(slowMethod);
      code.append("(reader, owner, typeResolver, object, ").append(propertyCount).append(");\n");
      code.append(indent).append("} else {\n");
      appendExpect(code, readerMode, '}', indent + "  ");
      code.append(indent).append("}\n");
    }
  }

  private void appendSlowRead(
      StringBuilder code,
      String methodName,
      String readerType,
      JsonFieldInfo[] properties,
      int readerMode) {
    code.append("  final void ")
        .append(methodName)
        .append("(")
        .append(readerType)
        .append(" reader, BaseObjectCodec owner, JsonTypeResolver typeResolver,\n");
    code.append("      Object object, int expectedIndex) {\n");
    code.append("    JsonFieldTable fieldTable = owner.readTable();\n");
    code.append("    long[] localFieldHashes = fieldHashes;\n");
    code.append("    do {\n");
    code.append("      int fieldIndex = expectedIndex < localFieldHashes.length\n");
    code.append(
        "          ? reader.readFieldIndex(fieldTable, localFieldHashes[expectedIndex], expectedIndex)\n");
    code.append("          : reader.readFieldIndex(fieldTable);\n");
    appendExpect(code, readerMode, ':', "      ");
    appendFieldSwitch(code, properties, readerMode, "      ");
    code.append("      if (fieldIndex >= 0) {\n");
    code.append("        expectedIndex = fieldIndex + 1;\n");
    code.append("      }\n");
    code.append("    } while (").append(consumeCall(readerMode, ',')).append(");\n");
    appendExpect(code, readerMode, '}', "    ");
    code.append("  }\n");
    code.append("  final void ")
        .append(methodName)
        .append("(")
        .append(readerType)
        .append(" reader, BaseObjectCodec owner, JsonTypeResolver typeResolver,\n");
    code.append("      Object object, int expectedIndex, int firstFieldIndex) {\n");
    code.append("    JsonFieldTable fieldTable = owner.readTable();\n");
    code.append("    long[] localFieldHashes = fieldHashes;\n");
    code.append("    int fieldIndex = firstFieldIndex;\n");
    code.append("    while (true) {\n");
    appendExpect(code, readerMode, ':', "      ");
    appendFieldSwitch(code, properties, readerMode, "      ");
    code.append("      if (fieldIndex >= 0) {\n");
    code.append("        expectedIndex = fieldIndex + 1;\n");
    code.append("      }\n");
    code.append("      if (!").append(consumeCall(readerMode, ',')).append(") {\n");
    appendExpect(code, readerMode, '}', "        ");
    code.append("        return;\n");
    code.append("      }\n");
    code.append("      fieldIndex = expectedIndex < localFieldHashes.length\n");
    code.append(
        "          ? reader.readFieldIndex(fieldTable, localFieldHashes[expectedIndex], expectedIndex)\n");
    code.append("          : reader.readFieldIndex(fieldTable);\n");
    code.append("    }\n");
    code.append("  }\n");
  }

  private void appendFieldSwitch(
      StringBuilder code, JsonFieldInfo[] properties, int readerMode, String indent) {
    code.append(indent).append("switch (fieldIndex) {\n");
    for (int i = 0; i < properties.length; i++) {
      code.append(indent).append("  case ").append(i).append(":\n");
      readField(code, properties[i], i, indent + "    ", readerMode);
      code.append(indent).append("    break;\n");
    }
    code.append(indent).append("  default:\n");
    code.append(indent).append("    reader.skipValue();\n");
    code.append(indent).append("}\n");
  }

  private static void appendExpect(StringBuilder code, int readerMode, char token, String indent) {
    code.append(indent).append(expectCall(readerMode, token)).append(";\n");
  }

  private static String expectCall(int readerMode, char token) {
    return readerMode == GENERIC_READER
        ? "reader.expect('" + token + "')"
        : "reader.expectToken('" + token + "')";
  }

  private static String consumeCall(int readerMode, char token) {
    return readerMode == GENERIC_READER
        ? "reader.consume('" + token + "')"
        : "reader.consumeToken('" + token + "')";
  }

  private static String tryReadNullCall(int readerMode) {
    return readerMode == GENERIC_READER ? "reader.tryReadNull()" : "reader.tryReadNullToken()";
  }

  private static String readBooleanCall(int readerMode) {
    return readerMode == GENERIC_READER ? "reader.readBoolean()" : "reader.readBooleanValue()";
  }

  private static String readIntCall(int readerMode) {
    return readerMode == GENERIC_READER ? "reader.readInt()" : "reader.readIntValue()";
  }

  private static String readLongCall(int readerMode) {
    return readerMode == GENERIC_READER ? "reader.readLong()" : "reader.readLongValue()";
  }

  private static boolean usesReadObjectCodec(JsonFieldInfo property) {
    return property.readKind() == JsonFieldKind.OBJECT
        && property.readRawType() != Object.class
        && property.readTypeInfo().codec() instanceof BaseObjectCodec;
  }

  private void readField(
      StringBuilder code, JsonFieldInfo property, int id, String indent, int readerMode) {
    Class<?> rawType = property.readRawType();
    switch (property.readKind()) {
      case BOOLEAN:
        readBoolean(code, rawType, id, indent, readerMode);
        return;
      case INT:
        readInt(code, rawType, id, indent, readerMode);
        return;
      case LONG:
        readLong(code, rawType, id, indent, readerMode);
        return;
      case STRING:
        readString(code, id, indent, readerMode);
        return;
      case ENUM:
        readEnum(code, rawType, id, indent, readerMode);
        return;
      case COLLECTION:
        readResolvedField(code, id, indent, readerMode);
        return;
      case OBJECT:
        readObject(code, property, id, indent, readerMode);
        return;
      default:
        code.append(indent).append("p").append(id).append(".read(reader, object, typeResolver);\n");
    }
  }

  private static void readBoolean(
      StringBuilder code, Class<?> rawType, int id, String indent, int readerMode) {
    if (rawType.isPrimitive()) {
      code.append(indent)
          .append("a")
          .append(id)
          .append(".putBoolean(object, ")
          .append(readBooleanCall(readerMode))
          .append(");\n");
      return;
    }
    code.append(indent).append("if (").append(tryReadNullCall(readerMode)).append(") {\n");
    code.append(indent).append("  a").append(id).append(".putObject(object, null);\n");
    code.append(indent).append("} else {\n");
    code.append(indent)
        .append("  a")
        .append(id)
        .append(".putObject(object, Boolean.valueOf(")
        .append(readBooleanCall(readerMode))
        .append("));\n");
    code.append(indent).append("}\n");
  }

  private static void readInt(
      StringBuilder code, Class<?> rawType, int id, String indent, int readerMode) {
    if (rawType.isPrimitive()) {
      code.append(indent)
          .append("a")
          .append(id)
          .append(".putInt(object, ")
          .append(readIntCall(readerMode))
          .append(");\n");
      return;
    }
    code.append(indent).append("if (").append(tryReadNullCall(readerMode)).append(") {\n");
    code.append(indent).append("  a").append(id).append(".putObject(object, null);\n");
    code.append(indent).append("} else {\n");
    code.append(indent)
        .append("  a")
        .append(id)
        .append(".putObject(object, Integer.valueOf(")
        .append(readIntCall(readerMode))
        .append("));\n");
    code.append(indent).append("}\n");
  }

  private static void readLong(
      StringBuilder code, Class<?> rawType, int id, String indent, int readerMode) {
    if (rawType.isPrimitive()) {
      code.append(indent)
          .append("a")
          .append(id)
          .append(".putLong(object, ")
          .append(readLongCall(readerMode))
          .append(");\n");
      return;
    }
    code.append(indent).append("if (").append(tryReadNullCall(readerMode)).append(") {\n");
    code.append(indent).append("  a").append(id).append(".putObject(object, null);\n");
    code.append(indent).append("} else {\n");
    code.append(indent)
        .append("  a")
        .append(id)
        .append(".putObject(object, Long.valueOf(")
        .append(readLongCall(readerMode))
        .append("));\n");
    code.append(indent).append("}\n");
  }

  private static void readString(StringBuilder code, int id, String indent, int readerMode) {
    code.append(indent).append("if (").append(tryReadNullCall(readerMode)).append(") {\n");
    code.append(indent).append("  a").append(id).append(".putObject(object, null);\n");
    code.append(indent).append("} else {\n");
    code.append(indent)
        .append("  a")
        .append(id)
        .append(".putObject(object, reader.readString());\n");
    code.append(indent).append("}\n");
  }

  private static void readEnum(
      StringBuilder code, Class<?> rawType, int id, String indent, int readerMode) {
    code.append(indent).append("if (").append(tryReadNullCall(readerMode)).append(") {\n");
    code.append(indent).append("  a").append(id).append(".putObject(object, null);\n");
    code.append(indent).append("} else {\n");
    code.append(indent)
        .append("  a")
        .append(id)
        .append(".putObject(object, ")
        .append(sourceName(rawType))
        .append(".valueOf(reader.readString()));\n");
    code.append(indent).append("}\n");
  }

  private static void readResolvedField(StringBuilder code, int id, String indent, int readerMode) {
    code.append(indent)
        .append("a")
        .append(id)
        .append(".putObject(object, p")
        .append(id)
        .append(".readTypeInfo().codec().")
        .append(readObjectMethod(readerMode))
        .append("(reader, p")
        .append(id)
        .append(".readTypeInfo(), typeResolver));\n");
  }

  private static void readObject(
      StringBuilder code, JsonFieldInfo property, int id, String indent, int readerMode) {
    if (property.readRawType() == Object.class
        || !(property.readTypeInfo().codec() instanceof BaseObjectCodec)) {
      code.append(indent).append("p").append(id).append(".read(reader, object, typeResolver);\n");
      return;
    }
    code.append(indent).append("if (").append(tryReadNullCall(readerMode)).append(") {\n");
    code.append(indent).append("  a").append(id).append(".putObject(object, null);\n");
    code.append(indent).append("} else {\n");
    code.append(indent)
        .append("  BaseObjectCodec objectCodec = c")
        .append(id)
        .append(" == null ? owner : c")
        .append(id)
        .append(";\n");
    code.append(indent)
        .append("  a")
        .append(id)
        .append(".putObject(object, objectCodec.")
        .append(readObjectMethod(readerMode))
        .append("(reader, p")
        .append(id)
        .append(".readTypeInfo(), typeResolver));\n");
    code.append(indent).append("}\n");
  }

  private static String readObjectMethod(int readerMode) {
    switch (readerMode) {
      case LATIN1_READER:
        return "readLatin1";
      case UTF16_READER:
        return "readUtf16";
      case UTF8_READER:
        return "readUtf8";
      default:
        return "read";
    }
  }

  private static String className(Class<?> type) {
    String name = type.getName().replace('.', '_').replace('$', '_');
    String uniqueId = CodeGenerator.getClassUniqueId(type);
    if (uniqueId.isEmpty()) {
      uniqueId = String.valueOf(ID.incrementAndGet());
    }
    return "JsonWriter_" + name + "_" + uniqueId;
  }

  private String genCode(
      String className, Class<?> type, JsonFieldInfo[] properties, boolean utf8) {
    String typeName = sourceName(type);
    StringBuilder code = new StringBuilder(4096);
    code.append("package ").append(PACKAGE).append(";\n");
    code.append("import org.apache.fory.json.codec.BaseObjectCodec;\n");
    code.append("import org.apache.fory.json.meta.JsonFieldInfo;\n");
    code.append("import org.apache.fory.json.resolver.JsonTypeResolver;\n");
    code.append("import org.apache.fory.json.codec.GeneratedObjectWriter;\n");
    code.append("import org.apache.fory.json.codec.StringObjectWriter;\n");
    code.append("import org.apache.fory.json.codec.Utf8ObjectWriter;\n");
    code.append("import org.apache.fory.json.writer.JsonNumberTokenCache;\n");
    code.append("import org.apache.fory.json.writer.JsonStringTokenCache;\n");
    code.append("import org.apache.fory.json.writer.StringJsonWriter;\n");
    code.append("import org.apache.fory.json.writer.Utf8JsonWriter;\n");
    code.append("import org.apache.fory.reflect.InstanceFieldAccessors.InstanceAccessor;\n");
    code.append("final class ")
        .append(className)
        .append(" extends GeneratedObjectWriter implements ")
        .append(utf8 ? "Utf8ObjectWriter" : "StringObjectWriter")
        .append(" {\n");
    boolean objectStartFused = canFuseObjectStart(properties);
    boolean[] useInfo = new boolean[properties.length];
    boolean[] usePrefix = new boolean[properties.length];
    boolean[] useStringToken = new boolean[properties.length];
    boolean[] useNumberToken = new boolean[properties.length];
    for (int i = 0; i < properties.length; i++) {
      JsonFieldInfo property = properties[i];
      useInfo[i] = true;
      usePrefix[i] = usesPrefix(property);
      useStringToken[i] = property.writeKind() == JsonFieldKind.STRING;
      useNumberToken[i] = usesNumberToken(property, objectStartFused && i == 0);
      if (useInfo[i]) {
        code.append("  private final JsonFieldInfo p").append(i).append(";\n");
        code.append("  private final InstanceAccessor a").append(i).append(";\n");
      }
      if (usePrefix[i]) {
        if (utf8) {
          code.append("  private final byte[] u").append(i).append(";\n");
          code.append("  private final byte[] uc").append(i).append(";\n");
        } else {
          code.append("  private final byte[] s").append(i).append(";\n");
          code.append("  private final byte[] sc").append(i).append(";\n");
        }
      }
      if (useStringToken[i]) {
        code.append("  private final JsonStringTokenCache st").append(i).append(";\n");
      }
      if (useNumberToken[i]) {
        code.append("  private final JsonNumberTokenCache nt").append(i).append(";\n");
      }
    }
    code.append("  ")
        .append(className)
        .append("(JsonFieldInfo[] properties, BaseObjectCodec[] objectCodecs) {\n");
    code.append("    super(properties, objectCodecs);\n");
    for (int i = 0; i < properties.length; i++) {
      if (useInfo[i]) {
        code.append("    this.p").append(i).append(" = properties[").append(i).append("];\n");
        code.append("    this.a")
            .append(i)
            .append(" = (InstanceAccessor) properties[")
            .append(i)
            .append("].writeFieldAccessor();\n");
      }
      if (usePrefix[i]) {
        if (utf8) {
          code.append("    this.u")
              .append(i)
              .append(" = properties[")
              .append(i)
              .append("].utf8NamePrefix();\n");
          code.append("    this.uc")
              .append(i)
              .append(" = properties[")
              .append(i)
              .append("].utf8CommaNamePrefix();\n");
        } else {
          code.append("    this.s")
              .append(i)
              .append(" = properties[")
              .append(i)
              .append("].stringNamePrefix();\n");
          code.append("    this.sc")
              .append(i)
              .append(" = properties[")
              .append(i)
              .append("].stringCommaNamePrefix();\n");
        }
      }
      if (useStringToken[i]) {
        code.append("    this.st")
            .append(i)
            .append(" = properties[")
            .append(i)
            .append("].stringTokenCache();\n");
      }
      if (useNumberToken[i]) {
        code.append("    this.nt")
            .append(i)
            .append(" = properties[")
            .append(i)
            .append("].numberTokenCache();\n");
      }
    }
    code.append("  }\n");
    writeMethod(code, typeName, properties, utf8, objectStartFused);
    code.append("}\n");
    return code.toString();
  }

  private boolean usesPrefix(JsonFieldInfo property) {
    JsonFieldKind kind = property.writeKind();
    return kind != JsonFieldKind.BOOLEAN && kind != JsonFieldKind.ENUM
        || writeNullFields && !property.writeRawType().isPrimitive();
  }

  private static boolean usesNumberToken(JsonFieldInfo property, boolean objectStartFused) {
    if (objectStartFused) {
      return false;
    }
    switch (property.writeKind()) {
      case BYTE:
      case SHORT:
      case INT:
      case LONG:
        return true;
      default:
        return false;
    }
  }

  private void writeMethod(
      StringBuilder code,
      String typeName,
      JsonFieldInfo[] properties,
      boolean utf8,
      boolean objectStartFused) {
    String writerType = utf8 ? "Utf8JsonWriter" : "StringJsonWriter";
    String method = utf8 ? "writeUtf8" : "writeString";
    code.append("  public void ")
        .append(method)
        .append("(")
        .append(writerType)
        .append(" writer, Object value, JsonTypeResolver typeResolver) {\n");
    code.append("    ")
        .append(typeName)
        .append(" object = (")
        .append(typeName)
        .append(") value;\n");
    if (!objectStartFused) {
      code.append("    writer.writeObjectStart();\n");
      code.append("    int index = 0;\n");
    }
    boolean commaKnown = objectStartFused;
    for (int i = 0; i < properties.length; i++) {
      if (objectStartFused && i == 0) {
        writeObjectStartPrimitive(
            code, properties[i], fieldValue(properties[i], i, "object"), utf8);
      } else {
        writeProp(code, properties[i], i, utf8, commaKnown);
      }
      if (writeNullFields || properties[i].writeRawType().isPrimitive()) {
        commaKnown = true;
      }
    }
    code.append("    writer.writeObjectEnd();\n");
    code.append("  }\n");
  }

  private static boolean canFuseObjectStart(JsonFieldInfo[] properties) {
    if (properties.length == 0 || !properties[0].writeRawType().isPrimitive()) {
      return false;
    }
    switch (properties[0].writeKind()) {
      case BYTE:
      case SHORT:
      case INT:
      case LONG:
        return true;
      default:
        return false;
    }
  }

  private static void writeObjectStartPrimitive(
      StringBuilder code, JsonFieldInfo property, String value, boolean utf8) {
    switch (property.writeKind()) {
      case BYTE:
      case SHORT:
      case INT:
        code.append("    writer.writeObjectIntField(")
            .append(utf8 ? "u0, " : "s0, ")
            .append(value)
            .append(");\n");
        return;
      case LONG:
        code.append("    writer.writeObjectLongField(")
            .append(utf8 ? "u0, " : "s0, ")
            .append(value)
            .append(");\n");
        return;
      default:
        throw new ForyJsonException(
            "Unsupported generated object-start kind " + property.writeKind());
    }
  }

  private void writeProp(
      StringBuilder code, JsonFieldInfo property, int id, boolean utf8, boolean commaKnown) {
    String prop = "p" + id;
    Class<?> rawType = property.writeRawType();
    String value = "v" + id;
    if (rawType.isPrimitive()) {
      writePrimitive(
          code, property, prop, fieldValue(property, id, "object"), utf8, commaKnown, "    ");
      return;
    }
    code.append("    ");
    code.append(sourceName(rawType))
        .append(" ")
        .append(value)
        .append(" = ")
        .append(fieldValue(property, id, "object"))
        .append(";\n");
    if (writeNullFields) {
      if (isPrefixValue(property.writeKind())) {
        code.append("    if (").append(value).append(" == null) {\n");
        writeFieldName(code, id, utf8, commaKnown, "      ");
        code.append("      writer.writeNull();\n");
        code.append("    } else {\n");
        writeValue(code, property, prop, value, utf8, commaKnown, "      ");
        code.append("    }\n");
      } else {
        writeFieldName(code, id, utf8, commaKnown, "    ");
        code.append("    if (").append(value).append(" == null) {\n");
        code.append("      writer.writeNull();\n");
        code.append("    } else {\n");
        writeValue(code, property, prop, value, utf8, commaKnown, "      ");
        code.append("    }\n");
      }
    } else {
      code.append("    if (").append(value).append(" != null) {\n");
      if (isPrefixValue(property.writeKind())) {
        writeValue(code, property, prop, value, utf8, commaKnown, "      ");
      } else {
        writeFieldName(code, id, utf8, commaKnown, "      ");
        writeValue(code, property, prop, value, utf8, commaKnown, "      ");
      }
      code.append("    }\n");
    }
  }

  private void writePrimitive(
      StringBuilder code,
      JsonFieldInfo property,
      String prop,
      String value,
      boolean utf8,
      boolean commaKnown,
      String indent) {
    switch (property.writeKind()) {
      case BOOLEAN:
        code.append(indent)
            .append("writer.writeRawValue(")
            .append(prop)
            .append(utf8 ? ".utf8BooleanFieldValue(" : ".stringBooleanFieldValue(")
            .append(value)
            .append(commaKnown ? ", true));\n" : ", index != 0));\n");
        if (!commaKnown) {
          code.append(indent).append("index++;\n");
        }
        return;
      case BYTE:
      case SHORT:
      case INT:
        writeNumberToken(code, prop.substring(1), value, false, utf8, commaKnown, indent);
        return;
      case LONG:
        writeNumberToken(code, prop.substring(1), value, true, utf8, commaKnown, indent);
        return;
      default:
        writeFieldName(code, Integer.parseInt(prop.substring(1)), utf8, commaKnown, "    ");
        writePrimitiveScalar(code, property.writeKind(), value, "    ");
    }
  }

  private static void writeNumberToken(
      StringBuilder code,
      String id,
      String value,
      boolean longValue,
      boolean utf8,
      boolean commaKnown,
      String indent) {
    String cacheMethod = utf8 ? "writeUtf8Field" : "writeStringField";
    String writerMethod = longValue ? "writeLongField" : "writeIntField";
    String prefix = utf8 ? "u" : "s";
    if (commaKnown) {
      String commaCacheMethod = utf8 ? "writeUtf8CommaField" : "writeStringCommaField";
      code.append(indent)
          .append("if (!nt")
          .append(id)
          .append(".")
          .append(commaCacheMethod)
          .append("(writer, ")
          .append(value)
          .append(", ")
          .append(prefix)
          .append("c")
          .append(id)
          .append(")) {\n");
      code.append(indent)
          .append("  writer.")
          .append(writerMethod)
          .append("(")
          .append(prefix)
          .append(id)
          .append(", ")
          .append(prefix)
          .append("c")
          .append(id)
          .append(", 1, ")
          .append(value)
          .append(");\n");
      code.append(indent).append("}\n");
      return;
    }
    code.append(indent).append("int fieldIndex").append(id).append(" = index++;\n");
    code.append(indent)
        .append("if (!nt")
        .append(id)
        .append(".")
        .append(cacheMethod)
        .append("(writer, ")
        .append(value)
        .append(", fieldIndex")
        .append(id)
        .append(" != 0, ")
        .append(prefix)
        .append(id)
        .append(", ")
        .append(prefix)
        .append("c")
        .append(id)
        .append(")) {\n");
    code.append(indent)
        .append("  writer.")
        .append(writerMethod)
        .append("(")
        .append(prefix)
        .append(id)
        .append(", ")
        .append(prefix)
        .append("c")
        .append(id)
        .append(", fieldIndex")
        .append(id)
        .append(", ")
        .append(value)
        .append(");\n");
    code.append(indent).append("}\n");
  }

  private static void writeStringToken(
      StringBuilder code,
      String id,
      String value,
      boolean utf8,
      boolean commaKnown,
      String indent) {
    String cacheMethod = utf8 ? "writeUtf8Field" : "writeStringField";
    String prefix = utf8 ? "u" : "s";
    if (commaKnown) {
      String commaCacheMethod = utf8 ? "writeUtf8CommaField" : "writeStringCommaField";
      code.append(indent)
          .append("if (!st")
          .append(id)
          .append(".")
          .append(commaCacheMethod)
          .append("(writer, ")
          .append(value)
          .append(", ")
          .append(prefix)
          .append("c")
          .append(id)
          .append(")) {\n");
      code.append(indent)
          .append("  writer.writeStringField(")
          .append(prefix)
          .append(id)
          .append(", ")
          .append(prefix)
          .append("c")
          .append(id)
          .append(", 1, ")
          .append(value)
          .append(");\n");
      code.append(indent).append("}\n");
      return;
    }
    code.append(indent).append("int fieldIndex").append(id).append(" = index++;\n");
    code.append(indent)
        .append("if (!st")
        .append(id)
        .append(".")
        .append(cacheMethod)
        .append("(writer, ")
        .append(value)
        .append(", fieldIndex")
        .append(id)
        .append(" != 0, ")
        .append(prefix)
        .append(id)
        .append(", ")
        .append(prefix)
        .append("c")
        .append(id)
        .append(")) {\n");
    code.append(indent)
        .append("  writer.writeStringField(")
        .append(prefix)
        .append(id)
        .append(", ")
        .append(prefix)
        .append("c")
        .append(id)
        .append(", fieldIndex")
        .append(id)
        .append(", ")
        .append(value)
        .append(");\n");
    code.append(indent).append("}\n");
  }

  private static void writeFieldName(
      StringBuilder code, int id, boolean utf8, boolean commaKnown, String indent) {
    code.append(indent)
        .append("writer.writeRawValue(")
        .append(commaKnown ? (utf8 ? "uc" : "sc") : "index == 0 ? " + (utf8 ? "u" : "s"))
        .append(id)
        .append(commaKnown ? "" : " : " + (utf8 ? "uc" : "sc") + id)
        .append(");\n");
    if (!commaKnown) {
      code.append(indent).append("index++;\n");
    }
  }

  private void writeValue(
      StringBuilder code,
      JsonFieldInfo property,
      String prop,
      String value,
      boolean utf8,
      boolean commaKnown,
      String indent) {
    JsonFieldKind kind = property.writeKind();
    switch (kind) {
      case BOOLEAN:
        code.append(indent)
            .append("writer.writeRawValue(")
            .append(prop)
            .append(utf8 ? ".utf8BooleanFieldValue(" : ".stringBooleanFieldValue(")
            .append(value)
            .append(commaKnown ? ".booleanValue(), true));\n" : ".booleanValue(), index != 0));\n");
        if (!commaKnown) {
          code.append(indent).append("index++;\n");
        }
        return;
      case BYTE:
      case SHORT:
      case INT:
        writeNumberToken(
            code, prop.substring(1), value + ".intValue()", false, utf8, commaKnown, indent);
        return;
      case LONG:
        writeNumberToken(
            code, prop.substring(1), value + ".longValue()", true, utf8, commaKnown, indent);
        return;
      case STRING:
        writeStringToken(code, prop.substring(1), value, utf8, commaKnown, indent);
        return;
      case ENUM:
        code.append(indent)
            .append("writer.writeRawValue(")
            .append(prop)
            .append(utf8 ? ".utf8EnumFieldValue(" : ".stringEnumFieldValue(")
            .append(value)
            .append(commaKnown ? ", true));\n" : ", index != 0));\n");
        if (!commaKnown) {
          code.append(indent).append("index++;\n");
        }
        return;
      case FLOAT:
      case DOUBLE:
      case CHAR:
        writeScalar(code, kind, value, indent);
        return;
      case ARRAY:
      case COLLECTION:
        code.append(indent)
            .append(prop)
            .append(".writeTypeInfo().codec().")
            .append(utf8 ? "writeUtf8" : "writeString")
            .append("(writer, ")
            .append(value)
            .append(", typeResolver);\n");
        return;
      case MAP:
        code.append(indent)
            .append(prop)
            .append(".writeTypeInfo().codec().")
            .append(utf8 ? "writeUtf8" : "writeString")
            .append("(writer, ")
            .append(value)
            .append(", typeResolver);\n");
        return;
      default:
        writeObject(code, prop, value, utf8, indent);
    }
  }

  private void writeObject(
      StringBuilder code, String prop, String value, boolean utf8, String indent) {
    code.append(indent)
        .append(prop)
        .append(".writeTypeInfo().codec().")
        .append(utf8 ? "writeUtf8" : "writeString")
        .append("(writer, ")
        .append(value)
        .append(", typeResolver);\n");
  }

  private void writeScalar(StringBuilder code, JsonFieldKind kind, String value, String indent) {
    switch (kind) {
      case FLOAT:
        code.append(indent).append("writer.writeFloat(").append(value).append(".floatValue());\n");
        return;
      case DOUBLE:
        code.append(indent)
            .append("writer.writeDouble(")
            .append(value)
            .append(".doubleValue());\n");
        return;
      case CHAR:
        code.append(indent).append("writer.writeChar(").append(value).append(".charValue());\n");
        return;
      default:
        throw new ForyJsonException("Unsupported generated scalar kind " + kind);
    }
  }

  private void writePrimitiveScalar(
      StringBuilder code, JsonFieldKind kind, String value, String indent) {
    switch (kind) {
      case FLOAT:
        code.append(indent).append("writer.writeFloat(").append(value).append(");\n");
        return;
      case DOUBLE:
        code.append(indent).append("writer.writeDouble(").append(value).append(");\n");
        return;
      case CHAR:
        code.append(indent).append("writer.writeChar(").append(value).append(");\n");
        return;
      default:
        throw new ForyJsonException("Unsupported generated primitive kind " + kind);
    }
  }

  private static boolean isPrefixValue(JsonFieldKind kind) {
    return kind == JsonFieldKind.BOOLEAN
        || kind == JsonFieldKind.BYTE
        || kind == JsonFieldKind.SHORT
        || kind == JsonFieldKind.INT
        || kind == JsonFieldKind.LONG
        || kind == JsonFieldKind.STRING
        || kind == JsonFieldKind.ENUM;
  }

  private static boolean isPojo(Class<?> type) {
    return type != null
        && type != Object.class
        && type != String.class
        && type != Boolean.class
        && type != Byte.class
        && type != Short.class
        && type != Integer.class
        && type != Long.class
        && type != Float.class
        && type != Double.class
        && type != Character.class
        && !type.isPrimitive()
        && !type.isEnum()
        && !type.isArray()
        && !Collection.class.isAssignableFrom(type)
        && !Map.class.isAssignableFrom(type);
  }

  private static String fieldValue(JsonFieldInfo property, int id, String object) {
    String accessor = "a" + id;
    if (!property.writeRawType().isPrimitive()) {
      return "("
          + sourceName(property.writeRawType())
          + ") "
          + accessor
          + ".getObject("
          + object
          + ")";
    }
    switch (property.writeKind()) {
      case BOOLEAN:
        return accessor + ".getBoolean(" + object + ")";
      case BYTE:
        return accessor + ".getByte(" + object + ")";
      case SHORT:
        return accessor + ".getShort(" + object + ")";
      case INT:
        return accessor + ".getInt(" + object + ")";
      case LONG:
        return accessor + ".getLong(" + object + ")";
      case FLOAT:
        return accessor + ".getFloat(" + object + ")";
      case DOUBLE:
        return accessor + ".getDouble(" + object + ")";
      case CHAR:
        return accessor + ".getChar(" + object + ")";
      default:
        throw new ForyJsonException("Unsupported generated primitive kind " + property.writeKind());
    }
  }

  private static String sourceName(Class<?> type) {
    if (type.isArray()) {
      return sourceName(type.getComponentType()) + "[]";
    }
    String name = type.getCanonicalName();
    if (name == null) {
      throw new ForyJsonException("Class is not source accessible " + type);
    }
    return name;
  }
}
