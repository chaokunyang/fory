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
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.fory.builder.CodecBuilder;
import org.apache.fory.codegen.Code;
import org.apache.fory.codegen.CodeGenerator;
import org.apache.fory.codegen.CodegenContext;
import org.apache.fory.codegen.CompileUnit;
import org.apache.fory.codegen.Expression;
import org.apache.fory.codegen.Expression.Reference;
import org.apache.fory.json.ForyJsonException;
import org.apache.fory.json.codec.BaseObjectCodec;
import org.apache.fory.json.codec.GeneratedObjectWriter;
import org.apache.fory.json.codec.JsonCodec;
import org.apache.fory.json.codec.Latin1ObjectReader;
import org.apache.fory.json.codec.ObjectCodecs;
import org.apache.fory.json.codec.ObjectReader;
import org.apache.fory.json.codec.StringObjectWriter;
import org.apache.fory.json.codec.Utf16ObjectReader;
import org.apache.fory.json.codec.Utf8ObjectReader;
import org.apache.fory.json.codec.Utf8ObjectWriter;
import org.apache.fory.json.meta.JsonFieldInfo;
import org.apache.fory.json.meta.JsonFieldKind;
import org.apache.fory.json.reader.JsonReader;
import org.apache.fory.json.reader.Latin1StringJsonReader;
import org.apache.fory.json.reader.Utf16StringJsonReader;
import org.apache.fory.json.reader.Utf8JsonReader;
import org.apache.fory.json.resolver.JsonTypeResolver;
import org.apache.fory.json.writer.StringJsonWriter;
import org.apache.fory.json.writer.Utf8JsonWriter;
import org.apache.fory.platform.JdkVersion;
import org.apache.fory.reflect.TypeRef;
import org.apache.fory.type.Descriptor;
import org.apache.fory.util.record.RecordUtils;

public final class JsonCodegen {
  private static final String PACKAGE = "org.apache.fory.json.codegen";
  private static final int GENERIC_READER = 0;
  private static final int LATIN1_READER = 1;
  private static final int UTF16_READER = 2;
  private static final int UTF8_READER = 3;
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
    boolean record = objectCodec.isRecord();
    JsonFieldInfo[] writeProperties = objectCodec.writeFields();
    for (int i = 0; i < writeProperties.length; i++) {
      if (!canCompileWrite(writeProperties[i])) {
        return null;
      }
    }
    JsonFieldInfo[] readProperties = objectCodec.readFields();
    for (int i = 0; i < readProperties.length; i++) {
      if (!canCompileRead(readProperties[i], record)) {
        return null;
      }
    }
    String className = className(type);
    JsonCodec[] writeCodecs = writeCodecs(writeProperties);
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
    JsonCodec[] readCodecs = readCodecs(readProperties);
    BaseObjectCodec[] readObjectCodecs = readObjectCodecs(objectCodec, typeResolver);
    ObjectReader reader =
        (ObjectReader)
            compileReader(
                className + "_Reader", type, readProperties, readCodecs, readObjectCodecs, record);
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
      JsonCodec[] nestedCodecs,
      boolean utf8) {
    String code = new JsonCodecBuilder(className, type, properties, utf8, true, false).genCode();
    try {
      CompileUnit unit = new CompileUnit(PACKAGE, className, code, JsonCodegen.class);
      Class<?> writerClass = codeGenerator.compileAndLoad(unit, state -> state.lock.lock());
      Constructor<?> constructor =
          writerClass.getDeclaredConstructor(JsonFieldInfo[].class, JsonCodec[].class);
      constructor.setAccessible(true);
      return constructor.newInstance(properties, nestedCodecs);
    } catch (Throwable e) {
      throw new ForyJsonException("Cannot compile generated JSON writer " + className, e);
    }
  }

  private Object compileReader(
      String className,
      Class<?> type,
      JsonFieldInfo[] properties,
      JsonCodec[] readCodecs,
      BaseObjectCodec[] nestedCodecs,
      boolean record) {
    String code = new JsonCodecBuilder(className, type, properties, false, false, record).genCode();
    try {
      CompileUnit unit = new CompileUnit(PACKAGE, className, code, JsonCodegen.class);
      Class<?> readerClass = codeGenerator.compileAndLoad(unit, state -> state.lock.lock());
      Constructor<?> constructor =
          readerClass.getDeclaredConstructor(
              JsonFieldInfo[].class, JsonCodec[].class, BaseObjectCodec[].class);
      constructor.setAccessible(true);
      return constructor.newInstance(properties, readCodecs, nestedCodecs);
    } catch (Throwable e) {
      throw new ForyJsonException("Cannot compile generated JSON reader " + className, e);
    }
  }

  private static JsonCodec[] writeCodecs(JsonFieldInfo[] properties) {
    JsonCodec[] codecs = new JsonCodec[properties.length];
    for (int i = 0; i < properties.length; i++) {
      if (usesWriteCodec(properties[i])) {
        codecs[i] = properties[i].writeTypeInfo().codec();
      }
    }
    return codecs;
  }

  private static JsonCodec[] readCodecs(JsonFieldInfo[] properties) {
    JsonCodec[] codecs = new JsonCodec[properties.length];
    for (int i = 0; i < properties.length; i++) {
      if (usesReadCodec(properties[i])) {
        codecs[i] = properties[i].readTypeInfo().codec();
      }
    }
    return codecs;
  }

  private BaseObjectCodec[] readObjectCodecs(
      BaseObjectCodec objectCodec, JsonTypeResolver typeResolver) {
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
    if (!isRecordField(property) && property.writeField() == null) {
      return false;
    }
    Class<?> rawType = property.writeRawType();
    if (rawType != null && !rawType.isPrimitive() && !isVisible(rawType)) {
      return false;
    }
    Class<?> elementType = property.writeElementRawType();
    return !isPojo(elementType) || isVisible(elementType);
  }

  private boolean canCompileRead(JsonFieldInfo property, boolean record) {
    if (!record && property.readAccessor() == null) {
      return false;
    }
    if (!record && property.readAccessor().coreAccessor() == null) {
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

  private String codecTypeName(JsonCodec codec) {
    Class<?> type = codec.getClass();
    if (isPublicSourceType(type) && isVisible(type)) {
      return sourceName(type);
    }
    return sourceName(JsonCodec.class);
  }

  private static boolean isPublicSourceType(Class<?> type) {
    if (!CodeGenerator.sourcePublicAccessible(type)) {
      return false;
    }
    for (Class<?> current = type; current != null; current = current.getEnclosingClass()) {
      if (!Modifier.isPublic(current.getModifiers())) {
        return false;
      }
    }
    return true;
  }

  private final class JsonCodecBuilder extends CodecBuilder {
    private final String generatedClassName;
    private final JsonFieldInfo[] properties;
    private final boolean utf8;
    private final boolean writer;
    private final boolean record;

    private JsonCodecBuilder(
        String generatedClassName,
        Class<?> type,
        JsonFieldInfo[] properties,
        boolean utf8,
        boolean writer,
        boolean record) {
      super(new CodegenContext(), TypeRef.of(type));
      this.generatedClassName = generatedClassName;
      this.properties = properties;
      this.utf8 = utf8;
      this.writer = writer;
      this.record = record;
      ctx.setPackage(PACKAGE);
      ctx.setClassName(generatedClassName);
      ctx.setClassModifiers("final");
      ctx.addImports(JsonFieldInfo.class, JsonCodec.class, JsonTypeResolver.class);
      String[] generatedMethodNames = {
        "object", "value", "writer", "reader", "owner", "typeResolver"
      };
      for (String name : generatedMethodNames) {
        if (!ctx.containName(name)) {
          ctx.reserveName(name);
        }
      }
    }

    private CodegenContext context() {
      return ctx;
    }

    @Override
    public String codecClassName(Class<?> cls) {
      return generatedClassName;
    }

    @Override
    public String genCode() {
      return writer
          ? genWriterCode(this, generatedClassName, beanClass, properties, utf8)
          : genReaderCode(this, generatedClassName, beanClass, properties, record);
    }

    @Override
    public Expression buildEncodeExpression() {
      return new Expression.Empty();
    }

    @Override
    public Expression buildDecodeExpression() {
      return new Expression.Empty();
    }

    private Descriptor writeDescriptor(JsonFieldInfo property) {
      Field field = property.writeField();
      return new Descriptor(
          field, TypeRef.of(field.getGenericType()), recordReadMethod(field), null);
    }

    private Descriptor readDescriptor(JsonFieldInfo property) {
      Field field = property.readField();
      return new Descriptor(field, TypeRef.of(field.getGenericType()), null, null);
    }

    private Method recordReadMethod(Field field) {
      if (!RecordUtils.isRecord(field.getDeclaringClass())) {
        return null;
      }
      try {
        return field.getDeclaringClass().getMethod(field.getName());
      } catch (NoSuchMethodException e) {
        throw new ForyJsonException("Cannot resolve record accessor for field " + field, e);
      }
    }

    private ValueCode fieldValue(JsonFieldInfo property, String object) {
      ctx.clearExprState();
      Expression field =
          getFieldValue(
              new Reference(object, TypeRef.of(beanClass), false), writeDescriptor(property));
      Code.ExprCode fieldCode = field.genCode(ctx);
      String code = fieldCode.code();
      String optimized = code == null ? "" : ctx.optimizeMethodCode(code);
      return new ValueCode(optimized, fieldCode.value().toString());
    }

    private String newObjectCode(String objectName) {
      ctx.clearExprState();
      Code.ExprCode newObject = newBean().genCode(ctx);
      StringBuilder code = new StringBuilder();
      appendGeneratedCode(code, newObject.code(), "    ");
      code.append("    ")
          .append(sourceName(beanClass))
          .append(" ")
          .append(objectName)
          .append(" = ")
          .append(newObject.value())
          .append(";\n");
      return code.toString();
    }

    private void appendSetField(
        StringBuilder code,
        JsonFieldInfo property,
        String object,
        String value,
        TypeRef<?> valueType,
        String indent) {
      appendSetField(code, property, object, new Reference(value, valueType, false), indent);
    }

    private void appendSetNull(
        StringBuilder code, JsonFieldInfo property, String object, String indent) {
      appendSetField(
          code,
          property,
          object,
          new Expression.Null(TypeRef.of(property.readRawType()), false),
          indent);
    }

    private void appendSetField(
        StringBuilder code,
        JsonFieldInfo property,
        String object,
        Expression value,
        String indent) {
      ctx.clearExprState();
      Expression set =
          setFieldValue(
              new Reference(object, TypeRef.of(beanClass), false),
              readDescriptor(property),
              tryInlineCast(value, TypeRef.of(property.readField().getGenericType())));
      Code.ExprCode setCode = set.genCode(ctx);
      appendGeneratedCode(code, ctx.optimizeMethodCode(setCode.code()), indent);
    }
  }

  private static final class ValueCode {
    private final String code;
    private final String value;

    private ValueCode(String code, String value) {
      this.code = code;
      this.value = value;
    }
  }

  private static void appendGeneratedCode(StringBuilder code, String generated, String indent) {
    if (generated == null || generated.isEmpty()) {
      return;
    }
    int start = 0;
    int length = generated.length();
    while (start < length) {
      int end = generated.indexOf('\n', start);
      if (end < 0) {
        end = length;
      }
      if (end > start) {
        code.append(indent).append(generated, start, end).append('\n');
      }
      start = end + 1;
    }
  }

  private String genReaderCode(
      JsonCodecBuilder builder,
      String className,
      Class<?> type,
      JsonFieldInfo[] properties,
      boolean record) {
    CodegenContext ctx = builder.context();
    ctx.addImports(
        BaseObjectCodec.class,
        JsonReader.class,
        Latin1StringJsonReader.class,
        Utf16StringJsonReader.class,
        Utf8JsonReader.class);
    ctx.implementsInterfaces(
        ctx.type(ObjectReader.class),
        ctx.type(Latin1ObjectReader.class),
        ctx.type(Utf16ObjectReader.class),
        ctx.type(Utf8ObjectReader.class));
    ctx.addField(long[].class, "fieldHashes");
    StringBuilder constructor = new StringBuilder();
    constructor.append("this.fieldHashes = new long[properties.length];\n");
    for (int i = 0; i < properties.length; i++) {
      ctx.addField(JsonFieldInfo.class, "p" + i);
      if (usesReadCodec(properties[i])) {
        ctx.addField(codecTypeName(properties[i].readTypeInfo().codec()), "r" + i);
      }
      if (usesReadObjectCodec(properties[i])) {
        ctx.addField(BaseObjectCodec.class, "c" + i);
      }
    }
    for (int i = 0; i < properties.length; i++) {
      constructor
          .append("this.fieldHashes[")
          .append(i)
          .append("] = properties[")
          .append(i)
          .append("].nameHash();\n");
      constructor.append("this.p").append(i).append(" = properties[").append(i).append("];\n");
      if (usesReadCodec(properties[i])) {
        constructor
            .append("this.r")
            .append(i)
            .append(" = (")
            .append(codecTypeName(properties[i].readTypeInfo().codec()))
            .append(") codecs[")
            .append(i)
            .append("];\n");
      }
      if (usesReadObjectCodec(properties[i])) {
        constructor.append("this.c").append(i).append(" = objectCodecs[").append(i).append("];\n");
      }
    }
    ctx.addConstructor(
        constructor.toString(),
        JsonFieldInfo[].class,
        "properties",
        JsonCodec[].class,
        "codecs",
        BaseObjectCodec[].class,
        "objectCodecs");
    StringBuilder code = new StringBuilder(4096);
    appendFieldIndexMethod(code, properties);
    appendReadMethod(builder, code, "read", "JsonReader", type, properties, GENERIC_READER, record);
    appendReadMethod(
        builder,
        code,
        "readLatin1",
        "Latin1StringJsonReader",
        type,
        properties,
        LATIN1_READER,
        record);
    appendReadMethod(
        builder,
        code,
        "readUtf16",
        "Utf16StringJsonReader",
        type,
        properties,
        UTF16_READER,
        record);
    appendReadMethod(
        builder, code, "readUtf8", "Utf8JsonReader", type, properties, UTF8_READER, record);
    return appendClassMethods(ctx.genCode(), code.toString());
  }

  private static String appendClassMethods(String classCode, String methods) {
    int end = classCode.lastIndexOf('}');
    if (end < 0) {
      throw new ForyJsonException("Generated class has no closing brace");
    }
    return classCode.substring(0, end) + methods + classCode.substring(end);
  }

  private static void appendFieldIndexMethod(StringBuilder code, JsonFieldInfo[] properties) {
    code.append("  final int fieldIndex(long fieldHash) {\n");
    for (int i = 0; i < properties.length; i++) {
      code.append("    if (fieldHash == ")
          .append(longLiteral(properties[i].nameHash()))
          .append(") {\n");
      code.append("      return ").append(i).append(";\n");
      code.append("    }\n");
    }
    code.append("    return -1;\n");
    code.append("  }\n");
  }

  private static String longLiteral(long value) {
    return "0x" + Long.toHexString(value) + "L";
  }

  private void appendReadMethod(
      JsonCodecBuilder builder,
      StringBuilder code,
      String methodName,
      String readerType,
      Class<?> type,
      JsonFieldInfo[] properties,
      int readerMode,
      boolean record) {
    if (readerMode != GENERIC_READER) {
      appendFastRead(builder, code, methodName, readerType, type, properties, readerMode, record);
      appendSlowRead(
          builder, code, methodName + "Slow", readerType, type, properties, readerMode, record);
      return;
    }
    code.append("  public Object ")
        .append(methodName)
        .append("(")
        .append(readerType)
        .append(" reader, BaseObjectCodec owner, JsonTypeResolver typeResolver) {\n");
    appendNewObject(builder, code, type, record);
    appendExpect(code, readerMode, '{', "    ");
    code.append("    if (").append(consumeCall(readerMode, '}')).append(") {\n");
    appendReturnObject(code, "      ", record);
    code.append("    }\n");
    code.append("    long[] localFieldHashes = fieldHashes;\n");
    code.append("    int expectedIndex = 0;\n");
    code.append("    do {\n");
    code.append("      long fieldHash = reader.readFieldNameHash();\n");
    code.append("      int fieldIndex = expectedIndex < localFieldHashes.length\n");
    code.append("              && fieldHash == localFieldHashes[expectedIndex]\n");
    code.append("          ? expectedIndex\n");
    code.append("          : fieldIndex(fieldHash);\n");
    appendExpect(code, readerMode, ':', "      ");
    appendFieldSwitch(builder, code, properties, readerMode, "      ", record);
    code.append("      if (fieldIndex >= 0) {\n");
    code.append("        expectedIndex = fieldIndex + 1;\n");
    code.append("      }\n");
    code.append("    } while (").append(consumeCall(readerMode, ',')).append(");\n");
    appendExpect(code, readerMode, '}', "    ");
    appendReturnObject(code, "    ", record);
    code.append("  }\n");
  }

  private void appendFastRead(
      JsonCodecBuilder builder,
      StringBuilder code,
      String methodName,
      String readerType,
      Class<?> type,
      JsonFieldInfo[] properties,
      int readerMode,
      boolean record) {
    String slowMethod = methodName + "Slow";
    code.append("  public Object ")
        .append(methodName)
        .append("(")
        .append(readerType)
        .append(" reader, BaseObjectCodec owner, JsonTypeResolver typeResolver) {\n");
    appendNewObject(builder, code, type, record);
    appendExpect(code, readerMode, '{', "    ");
    code.append("    if (").append(consumeCall(readerMode, '}')).append(") {\n");
    appendReturnObject(code, "      ", record);
    code.append("    }\n");
    if (properties.length == 0) {
      code.append("    ").append(slowMethod).append("(reader, owner, typeResolver, object, 0);\n");
      appendReturnObject(code, "    ", record);
      code.append("  }\n");
      return;
    }
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
      appendFastReadField(builder, code, slowMethod, properties, i, indent, readerMode, record);
      if (i > 0) {
        code.append("    }\n");
      }
    }
    appendReturnObject(code, "    ", record);
    code.append("  }\n");
  }

  private void appendFastReadField(
      JsonCodecBuilder builder,
      StringBuilder code,
      String slowMethod,
      JsonFieldInfo[] properties,
      int index,
      String indent,
      int readerMode,
      boolean record) {
    if (isPackedName(properties[index].name())) {
      code.append(indent)
          .append("if (reader.tryReadNextFieldNameColon(")
          .append(longLiteral(properties[index].nameHash()))
          .append(", ")
          .append(longLiteral(packedNameMask(properties[index].name().length())))
          .append(", ")
          .append(properties[index].name().length())
          .append(")) {\n");
      readField(builder, code, properties[index], index, indent + "  ", readerMode, record);
      appendFieldEnd(code, slowMethod, properties.length, index, indent + "  ", readerMode, record);
      code.append(indent).append("} else {\n");
      appendNextDirectFallback(
          builder, code, slowMethod, properties, index, indent + "  ", readerMode, record);
      code.append(indent).append("}\n");
      return;
    }
    appendNextDirectFallback(
        builder, code, slowMethod, properties, index, indent, readerMode, record);
  }

  private void appendNextDirectFallback(
      JsonCodecBuilder builder,
      StringBuilder code,
      String slowMethod,
      JsonFieldInfo[] properties,
      int index,
      String indent,
      int readerMode,
      boolean record) {
    int nextIndex = index + 1;
    if (nextIndex < properties.length && isPackedName(properties[nextIndex].name())) {
      code.append(indent)
          .append("if (reader.tryReadNextFieldNameColon(")
          .append(longLiteral(properties[nextIndex].nameHash()))
          .append(", ")
          .append(longLiteral(packedNameMask(properties[nextIndex].name().length())))
          .append(", ")
          .append(properties[nextIndex].name().length())
          .append(")) {\n");
      readField(builder, code, properties[nextIndex], nextIndex, indent + "  ", readerMode, record);
      appendFieldEnd(
          code, slowMethod, properties.length, nextIndex, indent + "  ", readerMode, record);
      code.append(indent).append("  skip").append(nextIndex).append(" = true;\n");
      code.append(indent).append("} else {\n");
      appendHashFallback(
          builder, code, slowMethod, properties, index, indent + "  ", readerMode, record);
      code.append(indent).append("}\n");
      return;
    }
    appendHashFallback(builder, code, slowMethod, properties, index, indent, readerMode, record);
  }

  private void appendHashFallback(
      JsonCodecBuilder builder,
      StringBuilder code,
      String slowMethod,
      JsonFieldInfo[] properties,
      int index,
      String indent,
      int readerMode,
      boolean record) {
    appendFieldHashRead(code, index, indent);
    appendFastReadFieldFromHash(
        builder, code, slowMethod, properties, index, indent, readerMode, record);
  }

  private void appendFastReadFieldFromHash(
      JsonCodecBuilder builder,
      StringBuilder code,
      String slowMethod,
      JsonFieldInfo[] properties,
      int index,
      String indent,
      int readerMode,
      boolean record) {
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
      readField(
          builder, code, properties[index + 1], index + 1, indent + "    ", readerMode, record);
      appendFieldEnd(
          code, slowMethod, properties.length, index + 1, indent + "    ", readerMode, record);
      code.append(indent).append("    skip").append(index + 1).append(" = true;\n");
      code.append(indent).append("  } else {\n");
      appendSlowConsumedReturn(
          code, slowMethod, index, "fieldIndex(fieldHash" + index + ")", indent + "    ", record);
      code.append(indent).append("  }\n");
    } else {
      appendSlowConsumedReturn(
          code, slowMethod, index, "fieldIndex(fieldHash" + index + ")", indent + "  ", record);
    }
    code.append(indent).append("} else {\n");
    appendExpect(code, readerMode, ':', indent + "  ");
    readField(builder, code, properties[index], index, indent + "  ", readerMode, record);
    appendFieldEnd(code, slowMethod, properties.length, index, indent + "  ", readerMode, record);
    code.append(indent).append("}\n");
  }

  private static void appendFieldHashRead(StringBuilder code, int index, String indent) {
    code.append(indent)
        .append("long fieldHash")
        .append(index)
        .append(" = reader.readFieldNameHash();\n");
  }

  private static boolean isPackedName(String name) {
    int length = name.length();
    if (length == 0 || length > Long.BYTES) {
      return false;
    }
    for (int i = 0; i < length; i++) {
      char ch = name.charAt(i);
      if (ch == 0 || ch > 0xFF) {
        return false;
      }
    }
    return true;
  }

  private static long packedNameMask(int length) {
    return length == Long.BYTES ? -1L : (1L << (length << 3)) - 1L;
  }

  private static void appendNewObject(
      JsonCodecBuilder builder, StringBuilder code, Class<?> type, boolean record) {
    if (record) {
      code.append("    Object[] object = owner.newRecordFieldValues();\n");
    } else {
      code.append(builder.newObjectCode("object"));
    }
  }

  private static void appendReturnObject(StringBuilder code, String indent, boolean record) {
    if (record) {
      code.append(indent).append("return owner.newRecord(object);\n");
    } else {
      code.append(indent).append("return object;\n");
    }
  }

  private static void appendSlowConsumedReturn(
      StringBuilder code,
      String slowMethod,
      int index,
      String firstFieldIndex,
      String indent,
      boolean record) {
    code.append(indent).append(slowMethod).append("(reader, owner, typeResolver, object, ");
    code.append(index).append(", ").append(firstFieldIndex).append(");\n");
    appendReturnObject(code, indent, record);
  }

  private static void appendFieldEnd(
      StringBuilder code,
      String slowMethod,
      int propertyCount,
      int index,
      String indent,
      int readerMode,
      boolean record) {
    if (index + 1 < propertyCount) {
      code.append(indent).append("if (!").append(consumeCall(readerMode, ',')).append(") {\n");
      appendExpect(code, readerMode, '}', indent + "  ");
      appendReturnObject(code, indent + "  ", record);
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
      JsonCodecBuilder builder,
      StringBuilder code,
      String methodName,
      String readerType,
      Class<?> type,
      JsonFieldInfo[] properties,
      int readerMode,
      boolean record) {
    String objectType = record ? "Object[]" : sourceName(type);
    code.append("  final void ")
        .append(methodName)
        .append("(")
        .append(readerType)
        .append(" reader, BaseObjectCodec owner, JsonTypeResolver typeResolver,\n");
    code.append("      ").append(objectType).append(" object, int expectedIndex) {\n");
    code.append("    long[] localFieldHashes = fieldHashes;\n");
    code.append("    do {\n");
    code.append("      long fieldHash = reader.readFieldNameHash();\n");
    code.append("      int fieldIndex = expectedIndex < localFieldHashes.length\n");
    code.append("              && fieldHash == localFieldHashes[expectedIndex]\n");
    code.append("          ? expectedIndex\n");
    code.append("          : fieldIndex(fieldHash);\n");
    appendExpect(code, readerMode, ':', "      ");
    appendFieldSwitch(builder, code, properties, readerMode, "      ", record);
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
    code.append("      ")
        .append(objectType)
        .append(" object, int expectedIndex, int firstFieldIndex) {\n");
    code.append("    long[] localFieldHashes = fieldHashes;\n");
    code.append("    int fieldIndex = firstFieldIndex;\n");
    code.append("    while (true) {\n");
    appendExpect(code, readerMode, ':', "      ");
    appendFieldSwitch(builder, code, properties, readerMode, "      ", record);
    code.append("      if (fieldIndex >= 0) {\n");
    code.append("        expectedIndex = fieldIndex + 1;\n");
    code.append("      }\n");
    code.append("      if (!").append(consumeCall(readerMode, ',')).append(") {\n");
    appendExpect(code, readerMode, '}', "        ");
    code.append("        return;\n");
    code.append("      }\n");
    code.append("      long fieldHash = reader.readFieldNameHash();\n");
    code.append("      fieldIndex = expectedIndex < localFieldHashes.length\n");
    code.append("              && fieldHash == localFieldHashes[expectedIndex]\n");
    code.append("          ? expectedIndex\n");
    code.append("          : fieldIndex(fieldHash);\n");
    code.append("    }\n");
    code.append("  }\n");
  }

  private void appendFieldSwitch(
      JsonCodecBuilder builder,
      StringBuilder code,
      JsonFieldInfo[] properties,
      int readerMode,
      String indent,
      boolean record) {
    code.append(indent).append("switch (fieldIndex) {\n");
    for (int i = 0; i < properties.length; i++) {
      code.append(indent).append("  case ").append(i).append(":\n");
      readField(builder, code, properties[i], i, indent + "    ", readerMode, record);
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
        : "reader.expectNextToken('" + token + "')";
  }

  private static String consumeCall(int readerMode, char token) {
    return readerMode == GENERIC_READER
        ? "reader.consume('" + token + "')"
        : "reader.consumeNextToken('" + token + "')";
  }

  private static String tryReadNullCall(int readerMode) {
    return readerMode == GENERIC_READER ? "reader.tryReadNull()" : "reader.tryReadNextNullToken()";
  }

  private static String readBooleanCall(int readerMode) {
    return readerMode == GENERIC_READER ? "reader.readBoolean()" : "reader.readNextBooleanValue()";
  }

  private static String readIntCall(int readerMode) {
    return readerMode == GENERIC_READER ? "reader.readInt()" : "reader.readNextIntValue()";
  }

  private static String readLongCall(int readerMode) {
    return readerMode == GENERIC_READER ? "reader.readLong()" : "reader.readNextLongValue()";
  }

  private static String readStringCall(int readerMode) {
    return readerMode == GENERIC_READER
        ? "reader.readNullableString()"
        : "reader.readNextNullableString()";
  }

  private static String readEnumCall(int readerMode, int id) {
    switch (readerMode) {
      case LATIN1_READER:
        return "r" + id + ".readNextLatin1Enum(reader)";
      case UTF16_READER:
        return "r" + id + ".readNextUtf16Enum(reader)";
      case UTF8_READER:
        return "r" + id + ".readNextUtf8Enum(reader)";
      default:
        return "r" + id + ".readEnum(reader)";
    }
  }

  private static boolean usesWriteCodec(JsonFieldInfo property) {
    switch (property.writeKind()) {
      case ARRAY:
      case COLLECTION:
      case MAP:
      case OBJECT:
        return true;
      default:
        return false;
    }
  }

  private static boolean usesReadCodec(JsonFieldInfo property) {
    switch (property.readKind()) {
      case ENUM:
      case ARRAY:
      case COLLECTION:
      case MAP:
        return true;
      default:
        return false;
    }
  }

  private static boolean usesReadObjectCodec(JsonFieldInfo property) {
    return property.readKind() == JsonFieldKind.OBJECT
        && property.readRawType() != Object.class
        && property.readTypeInfo().codec() instanceof BaseObjectCodec;
  }

  private void readField(
      JsonCodecBuilder builder,
      StringBuilder code,
      JsonFieldInfo property,
      int id,
      String indent,
      int readerMode,
      boolean record) {
    if (record) {
      readRecordField(code, property, id, indent, readerMode);
      return;
    }
    Class<?> rawType = property.readRawType();
    switch (property.readKind()) {
      case BOOLEAN:
        readBoolean(builder, code, property, rawType, id, indent, readerMode);
        return;
      case INT:
        readInt(builder, code, property, rawType, id, indent, readerMode);
        return;
      case LONG:
        readLong(builder, code, property, rawType, id, indent, readerMode);
        return;
      case STRING:
        readString(builder, code, property, id, indent, readerMode);
        return;
      case ENUM:
        readEnum(builder, code, property, id, indent, readerMode);
        return;
      case ARRAY:
      case COLLECTION:
      case MAP:
        readResolvedField(builder, code, property, id, indent, readerMode);
        return;
      case OBJECT:
        readObject(builder, code, property, id, indent, readerMode);
        return;
      default:
        code.append(indent).append("p").append(id).append(".read(reader, object, typeResolver);\n");
    }
  }

  private void readRecordField(
      StringBuilder code, JsonFieldInfo property, int id, String indent, int readerMode) {
    Class<?> rawType = property.readRawType();
    switch (property.readKind()) {
      case BOOLEAN:
        readRecordBoolean(code, rawType, id, indent, readerMode);
        return;
      case INT:
        readRecordInt(code, rawType, id, indent, readerMode);
        return;
      case LONG:
        readRecordLong(code, rawType, id, indent, readerMode);
        return;
      case STRING:
        readRecordString(code, id, indent, readerMode);
        return;
      case ENUM:
        readRecordEnum(code, id, indent, readerMode);
        return;
      case ARRAY:
      case COLLECTION:
      case MAP:
        readRecordResolved(code, id, indent, readerMode);
        return;
      case OBJECT:
        readRecordObject(code, property, id, indent, readerMode);
        return;
      default:
        code.append(indent)
            .append("object[")
            .append(id)
            .append("] = p")
            .append(id)
            .append(".readValue(reader, typeResolver);\n");
    }
  }

  private static void readRecordBoolean(
      StringBuilder code, Class<?> rawType, int id, String indent, int readerMode) {
    if (rawType.isPrimitive()) {
      code.append(indent)
          .append("object[")
          .append(id)
          .append("] = Boolean.valueOf(")
          .append(readBooleanCall(readerMode))
          .append(");\n");
      return;
    }
    code.append(indent).append("if (").append(tryReadNullCall(readerMode)).append(") {\n");
    code.append(indent).append("  object[").append(id).append("] = null;\n");
    code.append(indent).append("} else {\n");
    code.append(indent)
        .append("  object[")
        .append(id)
        .append("] = Boolean.valueOf(")
        .append(readBooleanCall(readerMode))
        .append(");\n");
    code.append(indent).append("}\n");
  }

  private static void readRecordInt(
      StringBuilder code, Class<?> rawType, int id, String indent, int readerMode) {
    if (rawType.isPrimitive()) {
      code.append(indent)
          .append("object[")
          .append(id)
          .append("] = Integer.valueOf(")
          .append(readIntCall(readerMode))
          .append(");\n");
      return;
    }
    code.append(indent).append("if (").append(tryReadNullCall(readerMode)).append(") {\n");
    code.append(indent).append("  object[").append(id).append("] = null;\n");
    code.append(indent).append("} else {\n");
    code.append(indent)
        .append("  object[")
        .append(id)
        .append("] = Integer.valueOf(")
        .append(readIntCall(readerMode))
        .append(");\n");
    code.append(indent).append("}\n");
  }

  private static void readRecordLong(
      StringBuilder code, Class<?> rawType, int id, String indent, int readerMode) {
    if (rawType.isPrimitive()) {
      code.append(indent)
          .append("object[")
          .append(id)
          .append("] = Long.valueOf(")
          .append(readLongCall(readerMode))
          .append(");\n");
      return;
    }
    code.append(indent).append("if (").append(tryReadNullCall(readerMode)).append(") {\n");
    code.append(indent).append("  object[").append(id).append("] = null;\n");
    code.append(indent).append("} else {\n");
    code.append(indent)
        .append("  object[")
        .append(id)
        .append("] = Long.valueOf(")
        .append(readLongCall(readerMode))
        .append(");\n");
    code.append(indent).append("}\n");
  }

  private static void readRecordString(StringBuilder code, int id, String indent, int readerMode) {
    code.append(indent)
        .append("object[")
        .append(id)
        .append("] = ")
        .append(readStringCall(readerMode))
        .append(";\n");
  }

  private static void readRecordEnum(StringBuilder code, int id, String indent, int readerMode) {
    code.append(indent).append("if (").append(tryReadNullCall(readerMode)).append(") {\n");
    code.append(indent).append("  object[").append(id).append("] = null;\n");
    code.append(indent).append("} else {\n");
    code.append(indent)
        .append("  object[")
        .append(id)
        .append("] = ")
        .append(readEnumCall(readerMode, id))
        .append(";\n");
    code.append(indent).append("}\n");
  }

  private static void readRecordResolved(
      StringBuilder code, int id, String indent, int readerMode) {
    code.append(indent)
        .append("object[")
        .append(id)
        .append("] = r")
        .append(id)
        .append(".")
        .append(readObjectMethod(readerMode))
        .append("(reader, p")
        .append(id)
        .append(".readTypeInfo(), typeResolver);\n");
  }

  private static void readRecordObject(
      StringBuilder code, JsonFieldInfo property, int id, String indent, int readerMode) {
    if (property.readRawType() == Object.class
        || !(property.readTypeInfo().codec() instanceof BaseObjectCodec)) {
      code.append(indent)
          .append("object[")
          .append(id)
          .append("] = p")
          .append(id)
          .append(".readValue(reader, typeResolver);\n");
      return;
    }
    code.append(indent).append("if (").append(tryReadNullCall(readerMode)).append(") {\n");
    code.append(indent).append("  object[").append(id).append("] = null;\n");
    code.append(indent).append("} else {\n");
    code.append(indent)
        .append("  BaseObjectCodec objectCodec = c")
        .append(id)
        .append(" == null ? owner : c")
        .append(id)
        .append(";\n");
    code.append(indent)
        .append("  object[")
        .append(id)
        .append("] = objectCodec.")
        .append(readObjectMethod(readerMode))
        .append("(reader, p")
        .append(id)
        .append(".readTypeInfo(), typeResolver);\n");
    code.append(indent).append("}\n");
  }

  private static void readBoolean(
      JsonCodecBuilder builder,
      StringBuilder code,
      JsonFieldInfo property,
      Class<?> rawType,
      int id,
      String indent,
      int readerMode) {
    if (rawType.isPrimitive()) {
      builder.appendSetField(
          code, property, "object", readBooleanCall(readerMode), TypeRef.of(boolean.class), indent);
      return;
    }
    code.append(indent).append("if (").append(tryReadNullCall(readerMode)).append(") {\n");
    builder.appendSetNull(code, property, "object", indent + "  ");
    code.append(indent).append("} else {\n");
    builder.appendSetField(
        code,
        property,
        "object",
        "Boolean.valueOf(" + readBooleanCall(readerMode) + ")",
        TypeRef.of(Boolean.class),
        indent + "  ");
    code.append(indent).append("}\n");
  }

  private static void readInt(
      JsonCodecBuilder builder,
      StringBuilder code,
      JsonFieldInfo property,
      Class<?> rawType,
      int id,
      String indent,
      int readerMode) {
    if (rawType.isPrimitive()) {
      builder.appendSetField(
          code, property, "object", readIntCall(readerMode), TypeRef.of(int.class), indent);
      return;
    }
    code.append(indent).append("if (").append(tryReadNullCall(readerMode)).append(") {\n");
    builder.appendSetNull(code, property, "object", indent + "  ");
    code.append(indent).append("} else {\n");
    builder.appendSetField(
        code,
        property,
        "object",
        "Integer.valueOf(" + readIntCall(readerMode) + ")",
        TypeRef.of(Integer.class),
        indent + "  ");
    code.append(indent).append("}\n");
  }

  private static void readLong(
      JsonCodecBuilder builder,
      StringBuilder code,
      JsonFieldInfo property,
      Class<?> rawType,
      int id,
      String indent,
      int readerMode) {
    if (rawType.isPrimitive()) {
      builder.appendSetField(
          code, property, "object", readLongCall(readerMode), TypeRef.of(long.class), indent);
      return;
    }
    code.append(indent).append("if (").append(tryReadNullCall(readerMode)).append(") {\n");
    builder.appendSetNull(code, property, "object", indent + "  ");
    code.append(indent).append("} else {\n");
    builder.appendSetField(
        code,
        property,
        "object",
        "Long.valueOf(" + readLongCall(readerMode) + ")",
        TypeRef.of(Long.class),
        indent + "  ");
    code.append(indent).append("}\n");
  }

  private static void readString(
      JsonCodecBuilder builder,
      StringBuilder code,
      JsonFieldInfo property,
      int id,
      String indent,
      int readerMode) {
    builder.appendSetField(
        code, property, "object", readStringCall(readerMode), TypeRef.of(String.class), indent);
  }

  private static void readEnum(
      JsonCodecBuilder builder,
      StringBuilder code,
      JsonFieldInfo property,
      int id,
      String indent,
      int readerMode) {
    code.append(indent).append("if (").append(tryReadNullCall(readerMode)).append(") {\n");
    builder.appendSetNull(code, property, "object", indent + "  ");
    code.append(indent).append("} else {\n");
    builder.appendSetField(
        code,
        property,
        "object",
        "(" + sourceName(property.readRawType()) + ") " + readEnumCall(readerMode, id),
        TypeRef.of(property.readRawType()),
        indent + "  ");
    code.append(indent).append("}\n");
  }

  private static void readResolvedField(
      JsonCodecBuilder builder,
      StringBuilder code,
      JsonFieldInfo property,
      int id,
      String indent,
      int readerMode) {
    builder.appendSetField(
        code,
        property,
        "object",
        "("
            + sourceName(property.readRawType())
            + ") r"
            + id
            + "."
            + readObjectMethod(readerMode)
            + "(reader, p"
            + id
            + ".readTypeInfo(), typeResolver)",
        TypeRef.of(property.readRawType()),
        indent);
  }

  private static void readObject(
      JsonCodecBuilder builder,
      StringBuilder code,
      JsonFieldInfo property,
      int id,
      String indent,
      int readerMode) {
    if (property.readRawType() == Object.class
        || !(property.readTypeInfo().codec() instanceof BaseObjectCodec)) {
      code.append(indent).append("p").append(id).append(".read(reader, object, typeResolver);\n");
      return;
    }
    code.append(indent).append("if (").append(tryReadNullCall(readerMode)).append(") {\n");
    builder.appendSetNull(code, property, "object", indent + "  ");
    code.append(indent).append("} else {\n");
    code.append(indent)
        .append("  BaseObjectCodec objectCodec = c")
        .append(id)
        .append(" == null ? owner : c")
        .append(id)
        .append(";\n");
    builder.appendSetField(
        code,
        property,
        "object",
        "("
            + sourceName(property.readRawType())
            + ") objectCodec."
            + readObjectMethod(readerMode)
            + "(reader, p"
            + id
            + ".readTypeInfo(), typeResolver)",
        TypeRef.of(property.readRawType()),
        indent + "  ");
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

  private String genWriterCode(
      JsonCodecBuilder builder,
      String className,
      Class<?> type,
      JsonFieldInfo[] properties,
      boolean utf8) {
    CodegenContext ctx = builder.context();
    ctx.addImports(StringJsonWriter.class, Utf8JsonWriter.class);
    ctx.extendsClasses(ctx.type(GeneratedObjectWriter.class));
    ctx.implementsInterfaces(ctx.type(utf8 ? Utf8ObjectWriter.class : StringObjectWriter.class));
    String typeName = sourceName(type);
    boolean objectStartFused = canFuseObjectStart(properties);
    boolean[] useInfo = new boolean[properties.length];
    boolean[] usePrefix = new boolean[properties.length];
    StringBuilder constructor = new StringBuilder("super(properties, codecs);\n");
    for (int i = 0; i < properties.length; i++) {
      JsonFieldInfo property = properties[i];
      useInfo[i] = true;
      usePrefix[i] = usesPrefix(property);
      if (useInfo[i]) {
        ctx.addField(JsonFieldInfo.class, "p" + i);
        constructor.append("this.p").append(i).append(" = properties[").append(i).append("];\n");
      }
      if (usesWriteCodec(property)) {
        ctx.addField(codecTypeName(property.writeTypeInfo().codec()), "c" + i);
        constructor
            .append("this.c")
            .append(i)
            .append(" = (")
            .append(codecTypeName(property.writeTypeInfo().codec()))
            .append(") codecs[")
            .append(i)
            .append("];\n");
      }
      if (usePrefix[i]) {
        if (utf8) {
          ctx.addField(byte[].class, "u" + i);
          ctx.addField(byte[].class, "uc" + i);
          constructor
              .append("this.u")
              .append(i)
              .append(" = properties[")
              .append(i)
              .append("].utf8NamePrefix();\n");
          constructor
              .append("this.uc")
              .append(i)
              .append(" = properties[")
              .append(i)
              .append("].utf8CommaNamePrefix();\n");
        } else {
          ctx.addField(byte[].class, "s" + i);
          ctx.addField(byte[].class, "sc" + i);
          constructor
              .append("this.s")
              .append(i)
              .append(" = properties[")
              .append(i)
              .append("].stringNamePrefix();\n");
          constructor
              .append("this.sc")
              .append(i)
              .append(" = properties[")
              .append(i)
              .append("].stringCommaNamePrefix();\n");
        }
      }
    }
    ctx.addConstructor(
        constructor.toString(), JsonFieldInfo[].class, "properties", JsonCodec[].class, "codecs");
    StringBuilder code = new StringBuilder(4096);
    writeMethod(builder, code, typeName, properties, utf8, objectStartFused);
    return appendClassMethods(ctx.genCode(), code.toString());
  }

  private boolean usesPrefix(JsonFieldInfo property) {
    JsonFieldKind kind = property.writeKind();
    return kind != JsonFieldKind.BOOLEAN && kind != JsonFieldKind.ENUM
        || writeNullFields && !property.writeRawType().isPrimitive();
  }

  private void writeMethod(
      JsonCodecBuilder builder,
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
        ValueCode field = builder.fieldValue(properties[i], "object");
        appendGeneratedCode(code, field.code, "    ");
        writeObjectStartPrimitive(code, properties[i], field.value, utf8);
      } else {
        writeProp(builder, code, properties[i], i, utf8, commaKnown);
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
      JsonCodecBuilder builder,
      StringBuilder code,
      JsonFieldInfo property,
      int id,
      boolean utf8,
      boolean commaKnown) {
    String prop = "p" + id;
    Class<?> rawType = property.writeRawType();
    String value = "v" + id;
    ValueCode field = builder.fieldValue(property, "object");
    appendGeneratedCode(code, field.code, "    ");
    if (rawType.isPrimitive()) {
      writePrimitive(code, property, prop, field.value, utf8, commaKnown, "    ");
      return;
    }
    code.append("    ");
    code.append(sourceName(rawType))
        .append(" ")
        .append(value)
        .append(" = ")
        .append("(")
        .append(sourceName(rawType))
        .append(") ")
        .append(field.value)
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
        writeNumberField(code, prop.substring(1), value, false, utf8, commaKnown, indent);
        return;
      case LONG:
        writeNumberField(code, prop.substring(1), value, true, utf8, commaKnown, indent);
        return;
      default:
        writeFieldName(code, Integer.parseInt(prop.substring(1)), utf8, commaKnown, "    ");
        writePrimitiveScalar(code, property.writeKind(), value, "    ");
    }
  }

  private static void writeNumberField(
      StringBuilder code,
      String id,
      String value,
      boolean longValue,
      boolean utf8,
      boolean commaKnown,
      String indent) {
    String writerMethod = longValue ? "writeLongField" : "writeIntField";
    String prefix = utf8 ? "u" : "s";
    code.append(indent)
        .append("writer.")
        .append(writerMethod)
        .append("(")
        .append(prefix)
        .append(id)
        .append(", ")
        .append(prefix)
        .append("c")
        .append(id)
        .append(commaKnown ? ", 1, " : ", index++, ")
        .append(value)
        .append(");\n");
  }

  private static void writeStringField(
      StringBuilder code,
      String id,
      String value,
      boolean utf8,
      boolean commaKnown,
      String indent) {
    String prefix = utf8 ? "u" : "s";
    code.append(indent)
        .append("writer.writeStringField(")
        .append(prefix)
        .append(id)
        .append(", ")
        .append(prefix)
        .append("c")
        .append(id)
        .append(commaKnown ? ", 1, " : ", index++, ")
        .append(value)
        .append(");\n");
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
        writeNumberField(
            code, prop.substring(1), value + ".intValue()", false, utf8, commaKnown, indent);
        return;
      case LONG:
        writeNumberField(
            code, prop.substring(1), value + ".longValue()", true, utf8, commaKnown, indent);
        return;
      case STRING:
        writeStringField(code, prop.substring(1), value, utf8, commaKnown, indent);
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
        writeCodec(code, prop.substring(1), value, utf8, indent);
        return;
      case MAP:
        writeCodec(code, prop.substring(1), value, utf8, indent);
        return;
      default:
        writeCodec(code, prop.substring(1), value, utf8, indent);
    }
  }

  private static void writeCodec(
      StringBuilder code, String id, String value, boolean utf8, String indent) {
    code.append(indent)
        .append("c")
        .append(id)
        .append(".")
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

  private static boolean isRecordField(JsonFieldInfo property) {
    Field field = property.writeField();
    return field != null && RecordUtils.isRecord(field.getDeclaringClass());
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
