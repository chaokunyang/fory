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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.fory.codegen.CodeGenerator;
import org.apache.fory.codegen.CompileUnit;
import org.apache.fory.platform.JdkVersion;

final class JsonCodegen {
  private static final String PACKAGE = "org.apache.fory.json";
  private static final AtomicInteger ID = new AtomicInteger();

  private final boolean writeNullFields;
  private final CodeGenerator codeGenerator;
  private final ClassLoader jsonLoader;

  JsonCodegen(boolean writeNullFields) {
    this.writeNullFields = writeNullFields;
    jsonLoader = JsonCodegen.class.getClassLoader();
    codeGenerator = new CodeGenerator(jsonLoader);
  }

  JsonObjectWriter compile(JsonClassInfo classInfo) {
    Class<?> type = classInfo.type();
    if (!canCompile(type)) {
      return null;
    }
    JsonPropertyInfo[] properties = classInfo.writeProperties();
    for (int i = 0; i < properties.length; i++) {
      if (!canCompile(properties[i])) {
        return null;
      }
    }
    String className = className(type);
    String code = genCode(className, type, properties);
    try {
      CompileUnit unit = new CompileUnit(PACKAGE, className, code, JsonCodegen.class);
      Class<?> writerClass = codeGenerator.compileAndLoad(unit, state -> state.lock.lock());
      Constructor<?> constructor = writerClass.getDeclaredConstructor(JsonPropertyInfo[].class);
      constructor.setAccessible(true);
      return (JsonObjectWriter) constructor.newInstance(new Object[] {properties});
    } catch (Throwable ignored) {
      return null;
    }
  }

  private boolean canCompile(JsonPropertyInfo property) {
    Member member = property.writeMember();
    if (member == null || !isVisible(member.getDeclaringClass())) {
      return false;
    }
    Class<?> rawType = property.writeRawType();
    return rawType == null || rawType.isPrimitive() || isVisible(rawType);
  }

  private boolean canCompile(Class<?> type) {
    return JdkVersion.MAJOR_VERSION >= 15
        && CodeGenerator.sourcePublicAccessible(type)
        && isVisible(type);
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

  private static String className(Class<?> type) {
    String name = type.getName().replace('.', '_').replace('$', '_');
    String uniqueId = CodeGenerator.getClassUniqueId(type);
    if (uniqueId.isEmpty()) {
      uniqueId = String.valueOf(ID.incrementAndGet());
    }
    return "JsonWriter_" + name + "_" + uniqueId;
  }

  private String genCode(String className, Class<?> type, JsonPropertyInfo[] properties) {
    String typeName = sourceName(type);
    StringBuilder code = new StringBuilder(4096);
    code.append("package ").append(PACKAGE).append(";\n");
    code.append("final class ").append(className).append(" implements JsonObjectWriter {\n");
    for (int i = 0; i < properties.length; i++) {
      code.append("  private final JsonPropertyInfo p").append(i).append(";\n");
    }
    code.append("  ").append(className).append("(JsonPropertyInfo[] properties) {\n");
    for (int i = 0; i < properties.length; i++) {
      code.append("    this.p").append(i).append(" = properties[").append(i).append("];\n");
    }
    code.append("  }\n");
    writeMethod(code, typeName, properties, true);
    writeMethod(code, typeName, properties, false);
    code.append("}\n");
    return code.toString();
  }

  private void writeMethod(
      StringBuilder code, String typeName, JsonPropertyInfo[] properties, boolean utf8) {
    String writerType = utf8 ? "Utf8JsonWriter" : "StringJsonWriter";
    String method = utf8 ? "writeUtf8" : "writeString";
    code.append("  public void ")
        .append(method)
        .append("(")
        .append(writerType)
        .append(" writer, Object value, JsonClassCache classCache) {\n");
    code.append("    ")
        .append(typeName)
        .append(" object = (")
        .append(typeName)
        .append(") value;\n");
    code.append("    writer.writeObjectStart();\n");
    code.append("    int index = 0;\n");
    for (int i = 0; i < properties.length; i++) {
      writeProp(code, properties[i], i, utf8);
    }
    code.append("    writer.writeObjectEnd();\n");
    code.append("  }\n");
  }

  private void writeProp(StringBuilder code, JsonPropertyInfo property, int id, boolean utf8) {
    String prop = "p" + id;
    Class<?> rawType = property.writeRawType();
    String value = "v" + id;
    code.append("    ");
    if (rawType.isPrimitive()) {
      writePrimitive(code, property, prop, memberExpr("object", property.writeMember()), utf8);
      return;
    }
    code.append(sourceName(rawType))
        .append(" ")
        .append(value)
        .append(" = ")
        .append(memberExpr("object", property.writeMember()))
        .append(";\n");
    if (writeNullFields) {
      if (isPrefixValue(property.writeKind())) {
        code.append("    if (").append(value).append(" == null) {\n");
        code.append("      writer.writeFieldName(").append(prop).append(", index++);\n");
        code.append("      writer.writeNull();\n");
        code.append("    } else {\n");
        writeValue(code, property, prop, value, utf8, "      ");
        code.append("    }\n");
      } else {
        code.append("    writer.writeFieldName(").append(prop).append(", index++);\n");
        code.append("    if (").append(value).append(" == null) {\n");
        code.append("      writer.writeNull();\n");
        code.append("    } else {\n");
        writeValue(code, property, prop, value, utf8, "      ");
        code.append("    }\n");
      }
    } else {
      code.append("    if (").append(value).append(" != null) {\n");
      if (isPrefixValue(property.writeKind())) {
        writeValue(code, property, prop, value, utf8, "      ");
      } else {
        code.append("      writer.writeFieldName(").append(prop).append(", index++);\n");
        writeValue(code, property, prop, value, utf8, "      ");
      }
      code.append("    }\n");
    }
  }

  private void writePrimitive(
      StringBuilder code, JsonPropertyInfo property, String prop, String value, boolean utf8) {
    switch (property.writeKind()) {
      case BOOLEAN:
        code.append("writer.writeRawValue(")
            .append(prop)
            .append(utf8 ? ".utf8BooleanFieldValue(" : ".stringBooleanFieldValue(")
            .append(value)
            .append(", index != 0));\n");
        code.append("    index++;\n");
        return;
      case BYTE:
      case SHORT:
      case INT:
        code.append("writer.writeIntField(")
            .append(
                utf8
                    ? prop + ".utf8NamePrefix(), " + prop + ".utf8CommaNamePrefix(), "
                    : prop + ".stringNamePrefix(), " + prop + ".stringCommaNamePrefix(), ")
            .append("index++, ")
            .append(value)
            .append(");\n");
        return;
      case LONG:
        code.append("writer.writeLongField(")
            .append(
                utf8
                    ? prop + ".utf8NamePrefix(), " + prop + ".utf8CommaNamePrefix(), "
                    : prop + ".stringNamePrefix(), " + prop + ".stringCommaNamePrefix(), ")
            .append("index++, ")
            .append(value)
            .append(");\n");
        return;
      default:
        code.append("writer.writeFieldName(").append(prop).append(", index++);\n");
        writePrimitiveScalar(code, property.writeKind(), value, "    ");
    }
  }

  private void writeValue(
      StringBuilder code,
      JsonPropertyInfo property,
      String prop,
      String value,
      boolean utf8,
      String indent) {
    JsonPropertyKind kind = property.writeKind();
    switch (kind) {
      case BOOLEAN:
        code.append(indent)
            .append("writer.writeRawValue(")
            .append(prop)
            .append(utf8 ? ".utf8BooleanFieldValue(" : ".stringBooleanFieldValue(")
            .append(value)
            .append(".booleanValue(), index != 0));\n");
        code.append(indent).append("index++;\n");
        return;
      case BYTE:
      case SHORT:
      case INT:
        code.append(indent)
            .append("writer.writeIntField(")
            .append(
                utf8
                    ? prop + ".utf8NamePrefix(), " + prop + ".utf8CommaNamePrefix(), "
                    : prop + ".stringNamePrefix(), " + prop + ".stringCommaNamePrefix(), ")
            .append("index++, ")
            .append(value)
            .append(".intValue());\n");
        return;
      case LONG:
        code.append(indent)
            .append("writer.writeLongField(")
            .append(
                utf8
                    ? prop + ".utf8NamePrefix(), " + prop + ".utf8CommaNamePrefix(), "
                    : prop + ".stringNamePrefix(), " + prop + ".stringCommaNamePrefix(), ")
            .append("index++, ")
            .append(value)
            .append(".longValue());\n");
        return;
      case STRING:
        code.append(indent)
            .append("writer.writeStringField(")
            .append(
                utf8
                    ? prop + ".utf8NamePrefix(), " + prop + ".utf8CommaNamePrefix(), "
                    : prop + ".stringNamePrefix(), " + prop + ".stringCommaNamePrefix(), ")
            .append("index++, ")
            .append(value)
            .append(");\n");
        return;
      case ENUM:
        code.append(indent)
            .append("writer.writeRawValue(")
            .append(prop)
            .append(utf8 ? ".utf8EnumFieldValue(" : ".stringEnumFieldValue(")
            .append(value)
            .append(", index != 0));\n");
        code.append(indent).append("index++;\n");
        return;
      case FLOAT:
      case DOUBLE:
      case CHAR:
        writeScalar(code, kind, value, indent);
        return;
      case ARRAY:
        code.append(indent)
            .append(utf8 ? "JsonSerializers.writeUtf8Array" : "JsonSerializers.writeArray")
            .append("(writer, ")
            .append(value)
            .append(", ")
            .append(prop)
            .append(".writeArrayComponentType(), classCache);\n");
        return;
      case COLLECTION:
        code.append(indent)
            .append(
                utf8 ? "JsonSerializers.writeUtf8Collection" : "JsonSerializers.writeCollection")
            .append("(writer, (java.util.Collection<?>) ")
            .append(value)
            .append(", ")
            .append(prop)
            .append(".writeElementType(), classCache);\n");
        return;
      case MAP:
        code.append(indent)
            .append(utf8 ? "JsonSerializers.writeUtf8Map" : "JsonSerializers.writeMap")
            .append("(writer, (java.util.Map<?, ?>) ")
            .append(value)
            .append(", ")
            .append(prop)
            .append(".writeMapValueType(), classCache);\n");
        return;
      default:
        writeObject(code, property, prop, value, utf8, indent);
    }
  }

  private void writeObject(
      StringBuilder code,
      JsonPropertyInfo property,
      String prop,
      String value,
      boolean utf8,
      String indent) {
    Class<?> rawType = property.writeRawType();
    if (rawType == Object.class) {
      code.append(indent)
          .append(utf8 ? "JsonSerializers.writeUtf8Value" : "JsonSerializers.writeValue")
          .append("(writer, ")
          .append(value)
          .append(", Object.class, classCache);\n");
      return;
    }
    code.append(indent).append("if (").append(value).append(".getClass() == ");
    code.append(sourceName(rawType)).append(".class) {\n");
    code.append(indent)
        .append("  classCache.get(")
        .append(sourceName(rawType))
        .append(".class).")
        .append(utf8 ? "writeUtf8" : "write")
        .append("(writer, ")
        .append(value)
        .append(", classCache);\n");
    code.append(indent).append("} else {\n");
    code.append(indent)
        .append("  ")
        .append(utf8 ? "JsonSerializers.writeUtf8Value" : "JsonSerializers.writeValue")
        .append("(writer, ")
        .append(value)
        .append(", ")
        .append(prop)
        .append(".writeType(), classCache);\n");
    code.append(indent).append("}\n");
  }

  private void writeScalar(StringBuilder code, JsonPropertyKind kind, String value, String indent) {
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
      StringBuilder code, JsonPropertyKind kind, String value, String indent) {
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

  private static boolean isPrefixValue(JsonPropertyKind kind) {
    return kind == JsonPropertyKind.BOOLEAN
        || kind == JsonPropertyKind.BYTE
        || kind == JsonPropertyKind.SHORT
        || kind == JsonPropertyKind.INT
        || kind == JsonPropertyKind.LONG
        || kind == JsonPropertyKind.STRING
        || kind == JsonPropertyKind.ENUM;
  }

  private static String memberExpr(String object, Member member) {
    if (member instanceof Field) {
      return object + "." + member.getName();
    }
    Method method = (Method) member;
    return object + "." + method.getName() + "()";
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
