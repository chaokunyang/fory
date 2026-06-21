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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.fory.codegen.CodeGenerator;
import org.apache.fory.codegen.CompileUnit;
import org.apache.fory.json.ForyJsonException;
import org.apache.fory.json.codec.BaseObjectCodec;
import org.apache.fory.json.codec.ObjectCodecs;
import org.apache.fory.json.codec.ObjectReader;
import org.apache.fory.json.codec.StringObjectReader;
import org.apache.fory.json.codec.StringObjectWriter;
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
  private static final int STRING_READER = 1;
  private static final int UTF8_READER = 2;
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
    BaseObjectCodec[] writeCodecs = writeCodecs(objectCodec, typeResolver);
    Utf8ObjectWriter utf8Writer =
        (Utf8ObjectWriter)
            compileWriter(className + "_Utf8", type, writeProperties, writeCodecs, true);
    if (utf8Writer == null) {
      return null;
    }
    StringObjectWriter stringWriter =
        (StringObjectWriter)
            compileWriter(className + "_String", type, writeProperties, writeCodecs, false);
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
        stringWriter, utf8Writer, reader, (StringObjectReader) reader, (Utf8ObjectReader) reader);
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

  private BaseObjectCodec[] writeCodecs(
      BaseObjectCodec objectCodec, JsonTypeResolver typeResolver) {
    JsonFieldInfo[] properties = objectCodec.writeFields();
    BaseObjectCodec[] nestedCodecs = new BaseObjectCodec[properties.length];
    Class<?> type = objectCodec.type();
    for (int i = 0; i < properties.length; i++) {
      Class<?> nestedType = writeNestedType(properties[i]);
      if (nestedType != null && nestedType != type) {
        nestedCodecs[i] = typeResolver.getObjectCodec(nestedType);
      }
    }
    return nestedCodecs;
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

  private static Class<?> writeNestedType(JsonFieldInfo property) {
    if (property.writeKind() == JsonFieldKind.OBJECT
        && property.writeRawType() != Object.class
        && property.writeTypeInfo().codec() instanceof BaseObjectCodec) {
      return property.writeRawType();
    }
    if (property.writeKind() == JsonFieldKind.COLLECTION
        && isPojo(property.writeElementRawType())) {
      return property.writeElementRawType();
    }
    return null;
  }

  private static Class<?> readNestedType(JsonFieldInfo property) {
    if (property.readKind() == JsonFieldKind.OBJECT
        && property.readRawType() != Object.class
        && property.readTypeInfo().codec() instanceof BaseObjectCodec) {
      return property.readRawType();
    }
    if (property.readKind() == JsonFieldKind.COLLECTION && isPojo(property.readElementRawType())) {
      return property.readElementRawType();
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
    code.append("import org.apache.fory.json.codec.ObjectReader;\n");
    code.append("import org.apache.fory.json.codec.StringObjectReader;\n");
    code.append("import org.apache.fory.json.codec.Utf8ObjectReader;\n");
    code.append("import org.apache.fory.json.meta.JsonFieldAccessor;\n");
    code.append("import org.apache.fory.json.meta.JsonFieldInfo;\n");
    code.append("import org.apache.fory.json.meta.JsonFieldTable;\n");
    code.append("import org.apache.fory.json.reader.JsonReader;\n");
    code.append("import org.apache.fory.json.reader.StringJsonReader;\n");
    code.append("import org.apache.fory.json.reader.Utf8JsonReader;\n");
    code.append("import org.apache.fory.json.resolver.JsonTypeResolver;\n");
    code.append("final class ")
        .append(className)
        .append(" implements ObjectReader, StringObjectReader, Utf8ObjectReader {\n");
    code.append("  private final String[] fieldNames;\n");
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
    for (int i = 0; i < properties.length; i++) {
      code.append("    this.fieldNames[")
          .append(i)
          .append("] = properties[")
          .append(i)
          .append("].name();\n");
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
    appendReadMethod(code, "read", "JsonReader", properties, GENERIC_READER);
    appendReadMethod(code, "readString", "StringJsonReader", properties, STRING_READER);
    appendReadMethod(code, "readUtf8", "Utf8JsonReader", properties, UTF8_READER);
    code.append("}\n");
    return code.toString();
  }

  private void appendReadMethod(
      StringBuilder code,
      String methodName,
      String readerType,
      JsonFieldInfo[] properties,
      int readerMode) {
    if (readerMode != GENERIC_READER) {
      appendFastRead(code, methodName, readerType, properties, readerMode);
      appendSlowRead(code, methodName + "Slow", readerType, properties, readerMode);
      return;
    }
    code.append("  public Object ")
        .append(methodName)
        .append("(")
        .append(readerType)
        .append(" reader, BaseObjectCodec owner, JsonTypeResolver typeResolver) {\n");
    code.append("    Object object = owner.newInstance();\n");
    appendExpect(code, readerMode, '{', "    ");
    code.append("    if (").append(consumeCall(readerMode, '}')).append(") {\n");
    code.append("      return object;\n");
    code.append("    }\n");
    code.append("    JsonFieldTable fieldTable = owner.readTable();\n");
    code.append("    String[] localFieldNames = fieldNames;\n");
    code.append("    int expectedIndex = 0;\n");
    code.append("    do {\n");
    code.append("      int fieldIndex = expectedIndex < localFieldNames.length\n");
    code.append(
        "          ? reader.readFieldIndex(fieldTable, localFieldNames[expectedIndex], expectedIndex)\n");
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
      JsonFieldInfo[] properties,
      int readerMode) {
    String slowMethod = methodName + "Slow";
    code.append("  public Object ")
        .append(methodName)
        .append("(")
        .append(readerType)
        .append(" reader, BaseObjectCodec owner, JsonTypeResolver typeResolver) {\n");
    code.append("    Object object = owner.newInstance();\n");
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
    code.append("    String[] localFieldNames = fieldNames;\n");
    for (int i = 0; i < properties.length; i++) {
      code.append("    if (!reader.readExpectedField(localFieldNames[").append(i).append("])) {\n");
      code.append("      ").append(slowMethod).append("(reader, owner, typeResolver, object, ");
      code.append(i).append(");\n");
      code.append("      return object;\n");
      code.append("    }\n");
      appendExpect(code, readerMode, ':', "    ");
      readField(code, properties[i], i, "    ", readerMode);
      if (i + 1 < properties.length) {
        code.append("    if (!").append(consumeCall(readerMode, ',')).append(") {\n");
        appendExpect(code, readerMode, '}', "      ");
        code.append("      return object;\n");
        code.append("    }\n");
      } else {
        code.append("    if (").append(consumeCall(readerMode, ',')).append(") {\n");
        code.append("      ").append(slowMethod).append("(reader, owner, typeResolver, object, ");
        code.append(properties.length).append(");\n");
        code.append("    } else {\n");
        appendExpect(code, readerMode, '}', "      ");
        code.append("    }\n");
      }
    }
    code.append("    return object;\n");
    code.append("  }\n");
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
    code.append("    String[] localFieldNames = fieldNames;\n");
    code.append("    do {\n");
    code.append("      int fieldIndex = expectedIndex < localFieldNames.length\n");
    code.append(
        "          ? reader.readFieldIndex(fieldTable, localFieldNames[expectedIndex], expectedIndex)\n");
    code.append("          : reader.readFieldIndex(fieldTable);\n");
    appendExpect(code, readerMode, ':', "      ");
    appendFieldSwitch(code, properties, readerMode, "      ");
    code.append("      if (fieldIndex >= 0) {\n");
    code.append("        expectedIndex = fieldIndex + 1;\n");
    code.append("      }\n");
    code.append("    } while (").append(consumeCall(readerMode, ',')).append(");\n");
    appendExpect(code, readerMode, '}', "    ");
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
    switch (property.readKind()) {
      case OBJECT:
        return property.readRawType() != Object.class
            && property.readTypeInfo().codec() instanceof BaseObjectCodec;
      case COLLECTION:
        return isPojo(property.readElementRawType());
      default:
        return false;
    }
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
        readCollection(code, property, id, indent, readerMode);
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

  private void readCollection(
      StringBuilder code, JsonFieldInfo property, int id, String indent, int readerMode) {
    Class<?> elementType = property.readElementRawType();
    if (elementType == String.class) {
      readStringList(code, id, indent, readerMode);
      return;
    }
    if (elementType != null && elementType.isEnum()) {
      readEnumList(code, elementType, id, indent, readerMode);
      return;
    }
    if (isPojo(elementType)) {
      readObjectList(code, id, indent, readerMode);
      return;
    }
    code.append(indent).append("p").append(id).append(".read(reader, object, typeResolver);\n");
  }

  private static void readStringList(StringBuilder code, int id, String indent, int readerMode) {
    code.append(indent).append("if (").append(tryReadNullCall(readerMode)).append(") {\n");
    code.append(indent).append("  a").append(id).append(".putObject(object, null);\n");
    code.append(indent).append("} else {\n");
    code.append(indent).append("  java.util.ArrayList list = new java.util.ArrayList();\n");
    code.append(indent).append("  ").append(expectCall(readerMode, '[')).append(";\n");
    code.append(indent).append("  if (!").append(consumeCall(readerMode, ']')).append(") {\n");
    code.append(indent).append("    do {\n");
    code.append(indent).append("      if (").append(tryReadNullCall(readerMode)).append(") {\n");
    code.append(indent).append("        list.add(null);\n");
    code.append(indent).append("      } else {\n");
    code.append(indent).append("        list.add(reader.readString());\n");
    code.append(indent).append("      }\n");
    code.append(indent).append("    } while (").append(consumeCall(readerMode, ',')).append(");\n");
    code.append(indent).append("    ").append(expectCall(readerMode, ']')).append(";\n");
    code.append(indent).append("  }\n");
    code.append(indent).append("  a").append(id).append(".putObject(object, list);\n");
    code.append(indent).append("}\n");
  }

  private static void readEnumList(
      StringBuilder code, Class<?> elementType, int id, String indent, int readerMode) {
    code.append(indent).append("if (").append(tryReadNullCall(readerMode)).append(") {\n");
    code.append(indent).append("  a").append(id).append(".putObject(object, null);\n");
    code.append(indent).append("} else {\n");
    code.append(indent).append("  java.util.ArrayList list = new java.util.ArrayList();\n");
    code.append(indent).append("  ").append(expectCall(readerMode, '[')).append(";\n");
    code.append(indent).append("  if (!").append(consumeCall(readerMode, ']')).append(") {\n");
    code.append(indent).append("    do {\n");
    code.append(indent).append("      if (").append(tryReadNullCall(readerMode)).append(") {\n");
    code.append(indent).append("        list.add(null);\n");
    code.append(indent).append("      } else {\n");
    code.append(indent)
        .append("        list.add(")
        .append(sourceName(elementType))
        .append(".valueOf(reader.readString()));\n");
    code.append(indent).append("      }\n");
    code.append(indent).append("    } while (").append(consumeCall(readerMode, ',')).append(");\n");
    code.append(indent).append("    ").append(expectCall(readerMode, ']')).append(";\n");
    code.append(indent).append("  }\n");
    code.append(indent).append("  a").append(id).append(".putObject(object, list);\n");
    code.append(indent).append("}\n");
  }

  private static void readObjectList(StringBuilder code, int id, String indent, int readerMode) {
    code.append(indent).append("if (").append(tryReadNullCall(readerMode)).append(") {\n");
    code.append(indent).append("  a").append(id).append(".putObject(object, null);\n");
    code.append(indent).append("} else {\n");
    code.append(indent).append("  java.util.ArrayList list = new java.util.ArrayList();\n");
    code.append(indent)
        .append("  BaseObjectCodec elementCodec = c")
        .append(id)
        .append(" == null ? owner : c")
        .append(id)
        .append(";\n");
    code.append(indent).append("  ").append(expectCall(readerMode, '[')).append(";\n");
    code.append(indent).append("  if (!").append(consumeCall(readerMode, ']')).append(") {\n");
    code.append(indent).append("    do {\n");
    code.append(indent).append("      if (").append(tryReadNullCall(readerMode)).append(") {\n");
    code.append(indent).append("        list.add(null);\n");
    code.append(indent).append("      } else {\n");
    code.append(indent)
        .append("        list.add(elementCodec.")
        .append(readObjectMethod(readerMode))
        .append("(reader, p")
        .append(id)
        .append(".readElementTypeInfo(), typeResolver));\n");
    code.append(indent).append("      }\n");
    code.append(indent).append("    } while (").append(consumeCall(readerMode, ',')).append(");\n");
    code.append(indent).append("    ").append(expectCall(readerMode, ']')).append(";\n");
    code.append(indent).append("  }\n");
    code.append(indent).append("  a").append(id).append(".putObject(object, list);\n");
    code.append(indent).append("}\n");
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
      case STRING_READER:
        return "readString";
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
    code.append("import org.apache.fory.json.resolver.JsonTypeInfo;\n");
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
    boolean[] useObjectCodec = new boolean[properties.length];
    boolean[] usePrefix = new boolean[properties.length];
    boolean[] useStringToken = new boolean[properties.length];
    boolean[] useElementToken = new boolean[properties.length];
    boolean[] useNumberToken = new boolean[properties.length];
    for (int i = 0; i < properties.length; i++) {
      JsonFieldInfo property = properties[i];
      useInfo[i] = true;
      useObjectCodec[i] = usesObjectCodec(property);
      usePrefix[i] = usesPrefix(property);
      useStringToken[i] = property.writeKind() == JsonFieldKind.STRING;
      useElementToken[i] =
          property.writeKind() == JsonFieldKind.COLLECTION
              && property.writeElementRawType() == String.class;
      useNumberToken[i] = usesNumberToken(property, objectStartFused && i == 0);
      if (useInfo[i]) {
        code.append("  private final JsonFieldInfo p").append(i).append(";\n");
        code.append("  private final InstanceAccessor a").append(i).append(";\n");
      }
      if (useObjectCodec[i]) {
        code.append("  private final BaseObjectCodec c").append(i).append(";\n");
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
      if (useElementToken[i]) {
        code.append("  private final JsonStringTokenCache et").append(i).append(";\n");
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
      if (useObjectCodec[i]) {
        code.append("    this.c").append(i).append(" = objectCodecs[").append(i).append("];\n");
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
      if (useElementToken[i]) {
        code.append("    this.et")
            .append(i)
            .append(" = properties[")
            .append(i)
            .append("].elementStringTokenCache();\n");
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
    writeMethod(code, type, typeName, properties, utf8, objectStartFused);
    code.append("}\n");
    return code.toString();
  }

  private boolean usesPrefix(JsonFieldInfo property) {
    JsonFieldKind kind = property.writeKind();
    return kind != JsonFieldKind.BOOLEAN && kind != JsonFieldKind.ENUM
        || writeNullFields && !property.writeRawType().isPrimitive();
  }

  private static boolean usesObjectCodec(JsonFieldInfo property) {
    switch (property.writeKind()) {
      case COLLECTION:
        return isPojo(property.writeElementRawType());
      case OBJECT:
        return property.writeRawType() != Object.class;
      default:
        return false;
    }
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
      Class<?> ownerType,
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
        writeProp(code, ownerType, properties[i], i, utf8, commaKnown);
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
      StringBuilder code,
      Class<?> ownerType,
      JsonFieldInfo property,
      int id,
      boolean utf8,
      boolean commaKnown) {
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
        writeValue(code, ownerType, property, prop, value, utf8, commaKnown, "      ");
        code.append("    }\n");
      } else {
        writeFieldName(code, id, utf8, commaKnown, "    ");
        code.append("    if (").append(value).append(" == null) {\n");
        code.append("      writer.writeNull();\n");
        code.append("    } else {\n");
        writeValue(code, ownerType, property, prop, value, utf8, commaKnown, "      ");
        code.append("    }\n");
      }
    } else {
      code.append("    if (").append(value).append(" != null) {\n");
      if (isPrefixValue(property.writeKind())) {
        writeValue(code, ownerType, property, prop, value, utf8, commaKnown, "      ");
      } else {
        writeFieldName(code, id, utf8, commaKnown, "      ");
        writeValue(code, ownerType, property, prop, value, utf8, commaKnown, "      ");
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
      Class<?> ownerType,
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
        code.append(indent)
            .append(prop)
            .append(".writeTypeInfo().codec().")
            .append(utf8 ? "writeUtf8" : "writeString")
            .append("(writer, ")
            .append(value)
            .append(", typeResolver);\n");
        return;
      case COLLECTION:
        writeCollection(code, ownerType, property, prop, value, utf8, indent);
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
        writeObject(code, ownerType, property, prop, value, utf8, indent);
    }
  }

  private void writeCollection(
      StringBuilder code,
      Class<?> ownerType,
      JsonFieldInfo property,
      String prop,
      String value,
      boolean utf8,
      String indent) {
    Class<?> elementType = property.writeElementRawType();
    boolean declaredList = List.class.isAssignableFrom(property.writeRawType());
    if (elementType == String.class) {
      code.append(indent)
          .append("writer.writeArrayStart();\n")
          .append(indent)
          .append("int elementIndex = 0;\n")
          .append(indent);
      writeListBranchStart(code, value, declaredList);
      code.append(indent)
          .append("  java.util.List<?> list = (java.util.List<?>) ")
          .append(value)
          .append(";\n")
          .append(indent)
          .append("  int size = list.size();\n")
          .append(indent)
          .append("  for (; elementIndex < size; elementIndex++) {\n")
          .append(indent)
          .append("    writer.writeComma(elementIndex);\n")
          .append(indent)
          .append("    Object element = list.get(elementIndex);\n")
          .append(indent)
          .append("    if (element == null) {\n")
          .append(indent)
          .append("      writer.writeNull();\n")
          .append(indent)
          .append("    } else {\n")
          .append(indent)
          .append("      ")
          .append("if (!et")
          .append(prop.substring(1))
          .append(utf8 ? ".writeUtf8Value" : ".writeStringValue")
          .append("(writer, (String) element)) {\n")
          .append(indent)
          .append("        writer.writeString((String) element);\n")
          .append(indent)
          .append("      }\n")
          .append(indent)
          .append("    }\n")
          .append(indent)
          .append("  }\n")
          .append(indent)
          .append("} else {\n")
          .append(indent)
          .append("for (Object element : (java.util.Collection<?>) ")
          .append(value)
          .append(") {\n")
          .append(indent)
          .append("  writer.writeComma(elementIndex++);\n")
          .append(indent)
          .append("  if (element == null) {\n")
          .append(indent)
          .append("    writer.writeNull();\n")
          .append(indent)
          .append("  } else {\n")
          .append(indent)
          .append("    ")
          .append("if (!et")
          .append(prop.substring(1))
          .append(utf8 ? ".writeUtf8Value" : ".writeStringValue")
          .append("(writer, (String) element)) {\n")
          .append(indent)
          .append("      writer.writeString((String) element);\n")
          .append(indent)
          .append("    }\n")
          .append(indent)
          .append("  }\n")
          .append(indent)
          .append("}\n")
          .append(indent)
          .append("}\n")
          .append(indent)
          .append("writer.writeArrayEnd();\n");
      return;
    }
    if (isPojo(elementType)) {
      boolean hasElementCodec = elementType != ownerType;
      code.append(indent)
          .append("writer.writeArrayStart();\n")
          .append(indent)
          .append("int elementIndex = 0;\n")
          .append(indent)
          .append("BaseObjectCodec elementCodec = c")
          .append(prop.substring(1))
          .append(";\n")
          .append(indent);
      writeListBranchStart(code, value, declaredList);
      code.append(indent)
          .append("  java.util.List<?> list = (java.util.List<?>) ")
          .append(value)
          .append(";\n")
          .append(indent)
          .append("  int size = list.size();\n")
          .append(indent)
          .append("  for (; elementIndex < size; elementIndex++) {\n")
          .append(indent)
          .append("    writer.writeComma(elementIndex);\n")
          .append(indent)
          .append("    Object element = list.get(elementIndex);\n")
          .append(indent)
          .append("    if (element == null) {\n")
          .append(indent)
          .append("      writer.writeNull();\n")
          .append(indent)
          .append("    } else if (element.getClass() == ")
          .append(sourceName(elementType))
          .append(hasElementCodec ? ".class) {\n" : ".class && elementCodec != null) {\n")
          .append(indent)
          .append("      elementCodec.")
          .append(utf8 ? "writeUtf8" : "writeString")
          .append("(writer, element, typeResolver);\n")
          .append(indent)
          .append("    } else {\n");
      writeResolvedValue(
          code,
          "elementTypeInfo" + prop.substring(1),
          "element",
          prop + ".writeElementType()",
          utf8,
          indent + "      ");
      code.append(indent)
          .append("    }\n")
          .append(indent)
          .append("  }\n")
          .append(indent)
          .append("} else {\n")
          .append(indent)
          .append("for (Object element : (java.util.Collection<?>) ")
          .append(value)
          .append(") {\n")
          .append(indent)
          .append("  writer.writeComma(elementIndex++);\n")
          .append(indent)
          .append("  if (element == null) {\n")
          .append(indent)
          .append("    writer.writeNull();\n")
          .append(indent)
          .append("  } else if (element.getClass() == ")
          .append(sourceName(elementType))
          .append(hasElementCodec ? ".class) {\n" : ".class && elementCodec != null) {\n")
          .append(indent)
          .append("    elementCodec.")
          .append(utf8 ? "writeUtf8" : "writeString")
          .append("(writer, element, typeResolver);\n")
          .append(indent)
          .append("  } else {\n");
      writeResolvedValue(
          code,
          "elementTypeInfo" + prop.substring(1),
          "element",
          prop + ".writeElementType()",
          utf8,
          indent + "    ");
      code.append(indent)
          .append("  }\n")
          .append(indent)
          .append("}\n")
          .append(indent)
          .append("}\n")
          .append(indent)
          .append("writer.writeArrayEnd();\n");
      return;
    }
    code.append(indent)
        .append(prop)
        .append(".writeTypeInfo().codec().")
        .append(utf8 ? "writeUtf8" : "writeString")
        .append("(writer, ")
        .append(value)
        .append(", typeResolver);\n");
  }

  private static void writeListBranchStart(StringBuilder code, String value, boolean declaredList) {
    code.append("if (").append(value);
    if (declaredList) {
      code.append(" instanceof java.util.RandomAccess) {\n");
    } else {
      code.append(" instanceof java.util.List && ")
          .append(value)
          .append(" instanceof java.util.RandomAccess) {\n");
    }
  }

  private void writeObject(
      StringBuilder code,
      Class<?> ownerType,
      JsonFieldInfo property,
      String prop,
      String value,
      boolean utf8,
      String indent) {
    Class<?> rawType = property.writeRawType();
    if (rawType == Object.class) {
      writeResolvedValue(code, "typeInfo" + prop.substring(1), value, "Object.class", utf8, indent);
      return;
    }
    if (!(property.writeTypeInfo().codec() instanceof BaseObjectCodec)) {
      code.append(indent)
          .append(prop)
          .append(".writeTypeInfo().codec().")
          .append(utf8 ? "writeUtf8" : "writeString")
          .append("(writer, ")
          .append(value)
          .append(", typeResolver);\n");
      return;
    }
    code.append(indent).append("if (").append(value).append(".getClass() == ");
    code.append(sourceName(rawType)).append(".class) {\n");
    code.append(indent)
        .append("  BaseObjectCodec objectCodec = c")
        .append(prop.substring(1))
        .append(";\n");
    if (rawType == ownerType) {
      code.append(indent).append("  if (objectCodec == null) {\n");
      code.append(indent)
          .append("    objectCodec = typeResolver.getObjectCodec(")
          .append(sourceName(rawType))
          .append(".class);\n");
      code.append(indent).append("  }\n");
    }
    code.append(indent)
        .append("  objectCodec.")
        .append(utf8 ? "writeUtf8" : "writeString")
        .append("(writer, ")
        .append(value)
        .append(", typeResolver);\n");
    code.append(indent).append("} else {\n");
    writeResolvedValue(
        code, "typeInfo" + prop.substring(1), value, prop + ".writeType()", utf8, indent + "  ");
    code.append(indent).append("}\n");
  }

  private static void writeResolvedValue(
      StringBuilder code,
      String typeInfo,
      String value,
      String declaredType,
      boolean utf8,
      String indent) {
    code.append(indent)
        .append("JsonTypeInfo ")
        .append(typeInfo)
        .append(" = typeResolver.getTypeInfo(")
        .append(declaredType)
        .append(", ")
        .append(value)
        .append(".getClass());\n");
    code.append(indent)
        .append(typeInfo)
        .append(".codec().")
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
