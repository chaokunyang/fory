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
import org.apache.fory.json.codec.ObjectWriters;
import org.apache.fory.json.codec.StringObjectWriter;
import org.apache.fory.json.codec.Utf8ObjectWriter;
import org.apache.fory.json.meta.JsonFieldInfo;
import org.apache.fory.json.meta.JsonFieldKind;
import org.apache.fory.json.resolver.JsonTypeResolver;
import org.apache.fory.platform.JdkVersion;

public final class JsonCodegen {
  private static final String PACKAGE = "org.apache.fory.json.codegen";
  private static final AtomicInteger ID = new AtomicInteger();

  private final boolean writeNullFields;
  private final CodeGenerator codeGenerator;
  private final ClassLoader jsonLoader;

  public JsonCodegen(boolean writeNullFields) {
    this.writeNullFields = writeNullFields;
    jsonLoader = JsonCodegen.class.getClassLoader();
    codeGenerator = new CodeGenerator(jsonLoader);
  }

  public ObjectWriters compile(BaseObjectCodec objectCodec, JsonTypeResolver typeResolver) {
    Class<?> type = objectCodec.type();
    if (!canCompile(type)) {
      return null;
    }
    JsonFieldInfo[] properties = objectCodec.writeFields();
    for (int i = 0; i < properties.length; i++) {
      if (!canCompile(properties[i])) {
        return null;
      }
    }
    String className = className(type);
    BaseObjectCodec[] nestedCodecs = nestedCodecs(objectCodec, typeResolver);
    Utf8ObjectWriter utf8Writer =
        (Utf8ObjectWriter) compileWriter(className + "_Utf8", type, properties, nestedCodecs, true);
    if (utf8Writer == null) {
      return null;
    }
    StringObjectWriter stringWriter =
        (StringObjectWriter)
            compileWriter(className + "_String", type, properties, nestedCodecs, false);
    if (stringWriter == null) {
      return null;
    }
    return new ObjectWriters(stringWriter, utf8Writer);
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

  private BaseObjectCodec[] nestedCodecs(
      BaseObjectCodec objectCodec, JsonTypeResolver typeResolver) {
    JsonFieldInfo[] properties = objectCodec.writeFields();
    BaseObjectCodec[] nestedCodecs = new BaseObjectCodec[properties.length];
    Class<?> type = objectCodec.type();
    for (int i = 0; i < properties.length; i++) {
      Class<?> nestedType = nestedType(properties[i]);
      if (nestedType != null && nestedType != type) {
        nestedCodecs[i] = typeResolver.getObjectCodec(nestedType);
      }
    }
    return nestedCodecs;
  }

  private static Class<?> nestedType(JsonFieldInfo property) {
    if (property.writeKind() == JsonFieldKind.OBJECT && property.writeRawType() != Object.class) {
      return property.writeRawType();
    }
    if (property.writeKind() == JsonFieldKind.COLLECTION
        && isPojo(property.writeElementRawType())) {
      return property.writeElementRawType();
    }
    return null;
  }

  private boolean canCompile(JsonFieldInfo property) {
    Field field = property.writeField();
    if (field == null || !isVisible(field.getDeclaringClass())) {
      return false;
    }
    Class<?> rawType = property.writeRawType();
    if (rawType != null && !rawType.isPrimitive() && !isVisible(rawType)) {
      return false;
    }
    Class<?> elementType = property.writeElementRawType();
    return !isPojo(elementType) || isVisible(elementType);
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
      useInfo[i] = usesFieldInfo(property);
      useObjectCodec[i] = usesObjectCodec(property);
      usePrefix[i] = usesPrefix(property);
      useStringToken[i] = property.writeKind() == JsonFieldKind.STRING;
      useElementToken[i] =
          property.writeKind() == JsonFieldKind.COLLECTION
              && property.writeElementRawType() == String.class;
      useNumberToken[i] = usesNumberToken(property, objectStartFused && i == 0);
      if (useInfo[i]) {
        code.append("  private final JsonFieldInfo p").append(i).append(";\n");
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

  private static boolean usesFieldInfo(JsonFieldInfo property) {
    switch (property.writeKind()) {
      case BYTE:
      case SHORT:
      case INT:
      case LONG:
      case STRING:
      case BOOLEAN:
      case ENUM:
      case ARRAY:
      case MAP:
        return true;
      case COLLECTION:
        return property.writeElementRawType() != String.class;
      case OBJECT:
        return property.writeRawType() != Object.class;
      default:
        return false;
    }
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
            code, properties[i], fieldExpr("object", properties[i].writeField()), utf8);
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
          code,
          property,
          prop,
          fieldExpr("object", property.writeField()),
          utf8,
          commaKnown,
          "    ");
      return;
    }
    code.append("    ");
    code.append(sourceName(rawType))
        .append(" ")
        .append(value)
        .append(" = ")
        .append(fieldExpr("object", property.writeField()))
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

  private static String fieldExpr(String object, Field field) {
    return object + "." + field.getName();
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
