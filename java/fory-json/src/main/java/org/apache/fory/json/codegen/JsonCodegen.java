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
import org.apache.fory.json.resolver.JsonTypeInfo;
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

  private Class<?> codecFieldType(JsonCodec codec) {
    Class<?> type = codec.getClass();
    if (isPublicSourceType(type) && isVisible(type)) {
      return type;
    }
    return JsonCodec.class;
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

    private Expression fieldValue(JsonFieldInfo property, Expression object) {
      return getFieldValue(object, writeDescriptor(property));
    }

    private Expression newObject() {
      return newBean();
    }

    private Expression setField(JsonFieldInfo property, Expression object, Expression value) {
      return setFieldValue(
          object,
          readDescriptor(property),
          tryInlineCast(value, TypeRef.of(property.readField().getGenericType())));
    }

    private Expression setNull(JsonFieldInfo property, Expression object) {
      return setField(
          property, object, new Expression.Null(TypeRef.of(property.readRawType()), false));
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
    for (int i = 0; i < properties.length; i++) {
      ctx.addField(JsonFieldInfo.class, "p" + i);
      if (usesReadCodec(properties[i])) {
        ctx.addField(codecFieldType(properties[i].readTypeInfo().codec()), "r" + i);
      }
      if (usesReadTypeField(properties[i])) {
        ctx.addField(JsonTypeInfo.class, "t" + i);
      }
      if (storesReadObjectCodec(type, properties[i])) {
        ctx.addField(BaseObjectCodec.class, "c" + i);
      }
    }
    addGeneratedConstructor(
        ctx,
        readerConstructorExpression(type, properties),
        JsonFieldInfo[].class,
        "properties",
        JsonCodec[].class,
        "codecs",
        BaseObjectCodec[].class,
        "objectCodecs");
    addGeneratedMethod(
        ctx,
        "final",
        "fieldIndex",
        fieldIndexExpression(properties),
        int.class,
        long.class,
        "fieldHash");
    addGeneratedMethod(
        ctx,
        "public",
        "read",
        readExpression(builder, type, properties, GENERIC_READER, record),
        Object.class,
        JsonReader.class,
        "reader",
        BaseObjectCodec.class,
        "owner",
        JsonTypeResolver.class,
        "typeResolver");
    addGeneratedMethod(
        ctx,
        "public",
        "readLatin1",
        fastReadExpression(builder, "readLatin1Slow", type, properties, LATIN1_READER, record),
        Object.class,
        Latin1StringJsonReader.class,
        "reader",
        BaseObjectCodec.class,
        "owner",
        JsonTypeResolver.class,
        "typeResolver");
    addSlowReadMethods(
        ctx,
        builder,
        "readLatin1Slow",
        Latin1StringJsonReader.class,
        type,
        properties,
        LATIN1_READER,
        record);
    addGeneratedMethod(
        ctx,
        "public",
        "readUtf16",
        fastReadExpression(builder, "readUtf16Slow", type, properties, UTF16_READER, record),
        Object.class,
        Utf16StringJsonReader.class,
        "reader",
        BaseObjectCodec.class,
        "owner",
        JsonTypeResolver.class,
        "typeResolver");
    addSlowReadMethods(
        ctx,
        builder,
        "readUtf16Slow",
        Utf16StringJsonReader.class,
        type,
        properties,
        UTF16_READER,
        record);
    addGeneratedMethod(
        ctx,
        "public",
        "readUtf8",
        fastReadExpression(builder, "readUtf8Slow", type, properties, UTF8_READER, record),
        Object.class,
        Utf8JsonReader.class,
        "reader",
        BaseObjectCodec.class,
        "owner",
        JsonTypeResolver.class,
        "typeResolver");
    addSlowReadMethods(
        ctx, builder, "readUtf8Slow", Utf8JsonReader.class, type, properties, UTF8_READER, record);
    return ctx.genCode();
  }

  private void addSlowReadMethods(
      CodegenContext ctx,
      JsonCodecBuilder builder,
      String methodName,
      Class<?> readerType,
      Class<?> type,
      JsonFieldInfo[] properties,
      int readerMode,
      boolean record) {
    Class<?> objectType = record ? Object[].class : type;
    addGeneratedMethod(
        ctx,
        "final",
        methodName,
        slowReadExpression(builder, type, properties, readerMode, record),
        void.class,
        readerType,
        "reader",
        BaseObjectCodec.class,
        "owner",
        JsonTypeResolver.class,
        "typeResolver",
        objectType,
        "object",
        int.class,
        "expectedIndex");
    addGeneratedMethod(
        ctx,
        "final",
        methodName,
        slowReadFromFirstExpression(builder, type, properties, readerMode, record),
        void.class,
        readerType,
        "reader",
        BaseObjectCodec.class,
        "owner",
        JsonTypeResolver.class,
        "typeResolver",
        objectType,
        "object",
        int.class,
        "expectedIndex",
        int.class,
        "firstFieldIndex");
  }

  private void addGeneratedConstructor(
      CodegenContext ctx, Expression expression, Object... params) {
    ctx.clearExprState();
    Code.ExprCode body = expression.genCode(ctx);
    String code = body.code();
    code = code == null ? "" : ctx.optimizeMethodCode(code);
    ctx.addConstructor(code, params);
  }

  private void addGeneratedMethod(
      CodegenContext ctx,
      String modifier,
      String name,
      Expression expression,
      Class<?> returnType,
      Object... params) {
    ctx.clearExprState();
    Code.ExprCode body = expression.genCode(ctx);
    String code = body.code();
    code = code == null ? "" : ctx.optimizeMethodCode(code);
    ctx.addMethod(modifier, name, code, returnType, params);
  }

  private Expression readerConstructorExpression(Class<?> type, JsonFieldInfo[] properties) {
    Expression.ListExpression expressions = new Expression.ListExpression();
    Reference propertiesRef = new Reference("properties", TypeRef.of(JsonFieldInfo[].class));
    Reference codecsRef = new Reference("codecs", TypeRef.of(JsonCodec[].class));
    Reference objectCodecsRef = new Reference("objectCodecs", TypeRef.of(BaseObjectCodec[].class));
    Reference hashes = new Reference("this.fieldHashes", TypeRef.of(long[].class));
    expressions.add(
        new Expression.Assign(
            hashes,
            new Expression.NewArray(
                long.class,
                new Expression.FieldValue(
                    propertiesRef, "length", TypeRef.of(int.class), false, true))));
    for (int i = 0; i < properties.length; i++) {
      Expression id = Expression.Literal.ofInt(i);
      Expression property = new Expression.ArrayValue(propertiesRef, id);
      expressions.add(
          new Expression.Assign(
              new Reference("this.p" + i, TypeRef.of(JsonFieldInfo.class)), property));
      expressions.add(
          new Expression.AssignArrayElem(
              hashes, new Expression.Invoke(property, "nameHash", TypeRef.of(long.class)), id));
      if (usesReadCodec(properties[i])) {
        Class<?> codecType = codecFieldType(properties[i].readTypeInfo().codec());
        expressions.add(
            new Expression.Assign(
                new Reference("this.r" + i, TypeRef.of(codecType)),
                new Expression.Cast(
                    new Expression.ArrayValue(codecsRef, id), TypeRef.of(codecType))));
      }
      if (usesReadTypeField(properties[i])) {
        expressions.add(
            new Expression.Assign(
                new Reference("this.t" + i, TypeRef.of(JsonTypeInfo.class)),
                new Expression.Invoke(property, "readTypeInfo", TypeRef.of(JsonTypeInfo.class))));
      }
      if (storesReadObjectCodec(type, properties[i])) {
        expressions.add(
            new Expression.Assign(
                new Reference("this.c" + i, TypeRef.of(BaseObjectCodec.class)),
                new Expression.ArrayValue(objectCodecsRef, id)));
      }
    }
    return expressions;
  }

  private Expression fieldIndexExpression(JsonFieldInfo[] properties) {
    Expression.ListExpression expressions = new Expression.ListExpression();
    Reference fieldHash = new Reference("fieldHash", TypeRef.of(long.class));
    for (int i = 0; i < properties.length; i++) {
      expressions.add(
          new Expression.If(
              eq(fieldHash, Expression.Literal.ofLong(properties[i].nameHash())),
              new Expression.Return(Expression.Literal.ofInt(i))));
    }
    expressions.add(new Expression.Return(Expression.Literal.ofInt(-1)));
    return expressions;
  }

  private Expression readExpression(
      JsonCodecBuilder builder,
      Class<?> type,
      JsonFieldInfo[] properties,
      int readerMode,
      boolean record) {
    Expression object = objectExpression(builder, record);
    Expression hashes =
        new Expression.Variable("localFieldHashes", fieldRef("fieldHashes", long[].class));
    Expression expectedIndex =
        new Expression.Variable("expectedIndex", Expression.Literal.ofInt(0));
    Expression.ListExpression expressions = new Expression.ListExpression();
    expressions.add(object);
    expressions.add(expectExpr(readerMode, '{'));
    expressions.add(new Expression.If(consumeExpr(readerMode, '}'), returnObject(object, record)));
    expressions.add(hashes);
    expressions.add(expectedIndex);
    Expression.ListExpression loop = new Expression.ListExpression();
    loop.add(
        readNextHashedField(
            builder, type, properties, readerMode, object, hashes, expectedIndex, record));
    loop.add(
        new Expression.If(not(consumeCommaOrEndObjectExpr(readerMode)), new Expression.Break()));
    expressions.add(new Expression.While(Expression.Literal.True, loop));
    expressions.add(returnObject(object, record));
    return expressions;
  }

  private Expression fastReadExpression(
      JsonCodecBuilder builder,
      String slowMethod,
      Class<?> type,
      JsonFieldInfo[] properties,
      int readerMode,
      boolean record) {
    Expression object = objectExpression(builder, record);
    Expression.ListExpression expressions = new Expression.ListExpression();
    expressions.add(object);
    expressions.add(expectExpr(readerMode, '{'));
    expressions.add(new Expression.If(consumeExpr(readerMode, '}'), returnObject(object, record)));
    if (properties.length == 0) {
      expressions.add(slowCall(slowMethod, object, Expression.Literal.ofInt(0)));
      expressions.add(returnObject(object, record));
      return expressions;
    }
    Expression hashes =
        new Expression.Variable("localFieldHashes", fieldRef("fieldHashes", long[].class));
    expressions.add(hashes);
    Expression[] skips = new Expression[properties.length];
    for (int i = 1; i < properties.length; i++) {
      skips[i] = new Expression.Variable("skip" + i, Expression.Literal.False);
      expressions.add(skips[i]);
    }
    for (int i = 0; i < properties.length; i++) {
      Expression read =
          fastReadField(
              builder, slowMethod, type, properties, i, readerMode, object, hashes, skips, record);
      expressions.add(i == 0 ? read : new Expression.If(not(skips[i]), read));
    }
    expressions.add(returnObject(object, record));
    return expressions;
  }

  private Expression fastReadField(
      JsonCodecBuilder builder,
      String slowMethod,
      Class<?> type,
      JsonFieldInfo[] properties,
      int index,
      int readerMode,
      Expression object,
      Expression hashes,
      Expression[] skips,
      boolean record) {
    if (isPackedName(properties[index].name())) {
      return new Expression.If(
          tryReadNextFieldNameColon(readerMode, properties[index]),
          new Expression.ListExpression(
              readField(builder, type, properties[index], index, readerMode, object, record),
              fieldEnd(slowMethod, properties.length, index, readerMode, object, record)),
          nextDirectFallback(
              builder,
              slowMethod,
              type,
              properties,
              index,
              readerMode,
              object,
              hashes,
              skips,
              record));
    }
    return nextDirectFallback(
        builder, slowMethod, type, properties, index, readerMode, object, hashes, skips, record);
  }

  private Expression nextDirectFallback(
      JsonCodecBuilder builder,
      String slowMethod,
      Class<?> type,
      JsonFieldInfo[] properties,
      int index,
      int readerMode,
      Expression object,
      Expression hashes,
      Expression[] skips,
      boolean record) {
    int nextIndex = index + 1;
    if (nextIndex < properties.length && isPackedName(properties[nextIndex].name())) {
      return new Expression.If(
          tryReadNextFieldNameColon(readerMode, properties[nextIndex]),
          new Expression.ListExpression(
              readField(
                  builder, type, properties[nextIndex], nextIndex, readerMode, object, record),
              fieldEnd(slowMethod, properties.length, nextIndex, readerMode, object, record),
              new Expression.Assign(skips[nextIndex], Expression.Literal.True)),
          hashFallback(
              builder,
              slowMethod,
              type,
              properties,
              index,
              readerMode,
              object,
              hashes,
              skips,
              record));
    }
    return hashFallback(
        builder, slowMethod, type, properties, index, readerMode, object, hashes, skips, record);
  }

  private Expression hashFallback(
      JsonCodecBuilder builder,
      String slowMethod,
      Class<?> type,
      JsonFieldInfo[] properties,
      int index,
      int readerMode,
      Expression object,
      Expression hashes,
      Expression[] skips,
      boolean record) {
    Expression fieldHash = readFieldNameHash(readerMode, "fieldHash" + index);
    return new Expression.ListExpression(
        fieldHash,
        fastReadFieldFromHash(
            builder,
            slowMethod,
            type,
            properties,
            index,
            readerMode,
            object,
            hashes,
            skips,
            fieldHash,
            record));
  }

  private Expression fastReadFieldFromHash(
      JsonCodecBuilder builder,
      String slowMethod,
      Class<?> type,
      JsonFieldInfo[] properties,
      int index,
      int readerMode,
      Expression object,
      Expression hashes,
      Expression[] skips,
      Expression fieldHash,
      boolean record) {
    Expression fallback;
    if (index + 1 < properties.length) {
      fallback =
          new Expression.If(
              eq(fieldHash, arrayValue(hashes, index + 1)),
              new Expression.ListExpression(
                  expectExpr(readerMode, ':'),
                  readField(
                      builder, type, properties[index + 1], index + 1, readerMode, object, record),
                  fieldEnd(slowMethod, properties.length, index + 1, readerMode, object, record),
                  new Expression.Assign(skips[index + 1], Expression.Literal.True)),
              slowConsumedReturn(slowMethod, index, fieldIndexInvoke(fieldHash), object, record));
    } else {
      fallback = slowConsumedReturn(slowMethod, index, fieldIndexInvoke(fieldHash), object, record);
    }
    return new Expression.If(
        ne(fieldHash, arrayValue(hashes, index)),
        fallback,
        new Expression.ListExpression(
            expectExpr(readerMode, ':'),
            readField(builder, type, properties[index], index, readerMode, object, record),
            fieldEnd(slowMethod, properties.length, index, readerMode, object, record)));
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

  private Expression objectExpression(JsonCodecBuilder builder, boolean record) {
    if (record) {
      return new Expression.Variable(
          "object",
          new Expression.Invoke(
              ownerRef(), "newRecordFieldValues", TypeRef.of(Object[].class), false));
    }
    return new Expression.Variable("object", builder.newObject());
  }

  private Expression returnObject(Expression object, boolean record) {
    if (record) {
      return new Expression.Return(
          new Expression.Invoke(ownerRef(), "newRecord", TypeRef.of(Object.class), object));
    }
    return new Expression.Return(object);
  }

  private Expression slowConsumedReturn(
      String slowMethod, int index, Expression firstFieldIndex, Expression object, boolean record) {
    return new Expression.ListExpression(
        slowCall(slowMethod, object, Expression.Literal.ofInt(index), firstFieldIndex),
        returnObject(object, record));
  }

  private Expression fieldEnd(
      String slowMethod,
      int propertyCount,
      int index,
      int readerMode,
      Expression object,
      boolean record) {
    if (index + 1 < propertyCount) {
      return new Expression.If(
          not(consumeCommaOrEndObjectExpr(readerMode)), returnObject(object, record));
    }
    return new Expression.If(
        consumeCommaOrEndObjectExpr(readerMode),
        slowCall(slowMethod, object, Expression.Literal.ofInt(propertyCount)));
  }

  private Expression slowReadExpression(
      JsonCodecBuilder builder,
      Class<?> type,
      JsonFieldInfo[] properties,
      int readerMode,
      boolean record) {
    Expression object = objectParam(type, record);
    Expression hashes =
        new Expression.Variable("localFieldHashes", fieldRef("fieldHashes", long[].class));
    Reference expectedIndex = new Reference("expectedIndex", TypeRef.of(int.class));
    Expression.ListExpression expressions = new Expression.ListExpression();
    expressions.add(hashes);
    Expression.ListExpression loop = new Expression.ListExpression();
    loop.add(
        readNextHashedField(
            builder, type, properties, readerMode, object, hashes, expectedIndex, record));
    loop.add(
        new Expression.If(not(consumeCommaOrEndObjectExpr(readerMode)), new Expression.Break()));
    expressions.add(new Expression.While(Expression.Literal.True, loop));
    return expressions;
  }

  private Expression slowReadFromFirstExpression(
      JsonCodecBuilder builder,
      Class<?> type,
      JsonFieldInfo[] properties,
      int readerMode,
      boolean record) {
    Expression object = objectParam(type, record);
    Expression hashes =
        new Expression.Variable("localFieldHashes", fieldRef("fieldHashes", long[].class));
    Reference expectedIndex = new Reference("expectedIndex", TypeRef.of(int.class));
    Expression fieldIndex =
        new Expression.Variable(
            "fieldIndex", new Reference("firstFieldIndex", TypeRef.of(int.class)));
    Expression.ListExpression expressions = new Expression.ListExpression();
    expressions.add(hashes);
    expressions.add(fieldIndex);
    Expression.ListExpression loop = new Expression.ListExpression();
    loop.add(expectExpr(readerMode, ':'));
    loop.add(fieldSwitch(builder, type, properties, readerMode, object, fieldIndex, record));
    loop.add(updateExpectedIndex(expectedIndex, fieldIndex));
    loop.add(
        new Expression.If(not(consumeCommaOrEndObjectExpr(readerMode)), new Expression.Return()));
    Expression fieldHash = readFieldNameHash(readerMode, "fieldHash");
    loop.add(fieldHash);
    loop.add(new Expression.Assign(fieldIndex, fieldIndexValue(expectedIndex, hashes, fieldHash)));
    expressions.add(new Expression.While(Expression.Literal.True, loop));
    return expressions;
  }

  private Expression readNextHashedField(
      JsonCodecBuilder builder,
      Class<?> type,
      JsonFieldInfo[] properties,
      int readerMode,
      Expression object,
      Expression hashes,
      Expression expectedIndex,
      boolean record) {
    Expression fieldHash = readFieldNameHash(readerMode, "fieldHash");
    Expression fieldIndex =
        new Expression.Variable("fieldIndex", fieldIndexValue(expectedIndex, hashes, fieldHash));
    return new Expression.ListExpression(
        fieldHash,
        fieldIndex,
        expectExpr(readerMode, ':'),
        fieldSwitch(builder, type, properties, readerMode, object, fieldIndex, record),
        updateExpectedIndex(expectedIndex, fieldIndex));
  }

  private Expression fieldSwitch(
      JsonCodecBuilder builder,
      Class<?> type,
      JsonFieldInfo[] properties,
      int readerMode,
      Expression object,
      Expression fieldIndex,
      boolean record) {
    Expression.Switch.Case[] cases = new Expression.Switch.Case[properties.length];
    for (int i = 0; i < properties.length; i++) {
      cases[i] =
          new Expression.Switch.Case(
              i,
              new Expression.ListExpression(
                  readField(builder, type, properties[i], i, readerMode, object, record),
                  new Expression.Break()));
    }
    return new Expression.Switch(
        fieldIndex, cases, new Expression.Invoke(readerRef(readerMode), "skipValue"));
  }

  private Expression updateExpectedIndex(Expression expectedIndex, Expression fieldIndex) {
    return new Expression.If(
        ge(fieldIndex, Expression.Literal.ofInt(0)),
        new Expression.Assign(
            expectedIndex, new Expression.Add(fieldIndex, Expression.Literal.ofInt(1))));
  }

  private Expression fieldIndexValue(
      Expression expectedIndex, Expression hashes, Expression fieldHash) {
    return new Expression.Ternary(
        and(
            lt(
                expectedIndex,
                new Expression.FieldValue(hashes, "length", TypeRef.of(int.class), false, true)),
            eq(fieldHash, new Expression.ArrayValue(hashes, expectedIndex))),
        expectedIndex,
        fieldIndexInvoke(fieldHash),
        true,
        TypeRef.of(int.class));
  }

  private static Expression objectParam(Class<?> type, boolean record) {
    return record
        ? new Reference("object", TypeRef.of(Object[].class))
        : new Reference("object", TypeRef.of(type));
  }

  private static Reference ownerRef() {
    return new Reference("owner", TypeRef.of(BaseObjectCodec.class));
  }

  private static Reference typeResolverRef() {
    return new Reference("typeResolver", TypeRef.of(JsonTypeResolver.class));
  }

  private static Reference fieldRef(String name, Class<?> type) {
    return Reference.fieldRef(name, TypeRef.of(type));
  }

  private static Reference readerRef(int readerMode) {
    return new Reference("reader", TypeRef.of(readerClass(readerMode)));
  }

  private static Class<?> readerClass(int readerMode) {
    switch (readerMode) {
      case LATIN1_READER:
        return Latin1StringJsonReader.class;
      case UTF16_READER:
        return Utf16StringJsonReader.class;
      case UTF8_READER:
        return Utf8JsonReader.class;
      default:
        return JsonReader.class;
    }
  }

  private static Expression expectExpr(int readerMode, char token) {
    return new Expression.Invoke(
        readerRef(readerMode),
        readerMode == GENERIC_READER ? "expect" : "expectNextToken",
        Expression.Literal.ofChar(token));
  }

  private static Expression consumeExpr(int readerMode, char token) {
    return new Expression.Invoke(
            readerRef(readerMode),
            readerMode == GENERIC_READER ? "consume" : "consumeNextToken",
            TypeRef.of(boolean.class),
            Expression.Literal.ofChar(token))
        .inline();
  }

  private static Expression consumeCommaOrEndObjectExpr(int readerMode) {
    return new Expression.Invoke(
            readerRef(readerMode),
            readerMode == GENERIC_READER
                ? "consumeCommaOrEndObject"
                : "consumeNextCommaOrEndObject",
            TypeRef.of(boolean.class))
        .inline();
  }

  private static Expression tryReadNullExpr(int readerMode) {
    return new Expression.Invoke(
            readerRef(readerMode),
            readerMode == GENERIC_READER ? "tryReadNull" : "tryReadNextNullToken",
            TypeRef.of(boolean.class))
        .inline();
  }

  private static Expression readBooleanExpr(int readerMode) {
    return new Expression.Invoke(
            readerRef(readerMode),
            readerMode == GENERIC_READER ? "readBoolean" : "readNextBooleanValue",
            TypeRef.of(boolean.class))
        .inline();
  }

  private static Expression readIntExpr(int readerMode) {
    return new Expression.Invoke(
            readerRef(readerMode),
            readerMode == GENERIC_READER ? "readInt" : "readNextIntValue",
            TypeRef.of(int.class))
        .inline();
  }

  private static Expression readLongExpr(int readerMode) {
    return new Expression.Invoke(
            readerRef(readerMode),
            readerMode == GENERIC_READER ? "readLong" : "readNextLongValue",
            TypeRef.of(long.class))
        .inline();
  }

  private static Expression readStringExpr(int readerMode) {
    return new Expression.Invoke(
            readerRef(readerMode),
            readerMode == GENERIC_READER ? "readNullableString" : "readNextNullableString",
            TypeRef.of(String.class),
            true)
        .inline();
  }

  private static Expression readFieldNameHash(int readerMode, String namePrefix) {
    return new Expression.Invoke(
        readerRef(readerMode), "readFieldNameHash", namePrefix, TypeRef.of(long.class), false);
  }

  private static Expression fieldIndexInvoke(Expression fieldHash) {
    return new Expression.Invoke(
            new Reference("this", TypeRef.of(Object.class)),
            "fieldIndex",
            "",
            TypeRef.of(int.class),
            false,
            false,
            fieldHash)
        .inline();
  }

  private static Expression tryReadNextFieldNameColon(int readerMode, JsonFieldInfo property) {
    if (readerMode == LATIN1_READER || readerMode == UTF8_READER) {
      String name = property.name();
      int tokenLength = fieldNameTokenLength(name);
      // This is a compact-JSON fast path. Whitespace, escapes, and UTF8 spellings that do not
      // match the raw token fall through to the generated field-hash reader without consuming.
      return new Expression.Invoke(
              readerRef(readerMode),
              "tryReadNextFieldNameToken",
              TypeRef.of(boolean.class),
              Expression.Literal.ofLong(fieldNameTokenPrefix(name)),
              Expression.Literal.ofLong(fieldNameTokenPrefixMask(tokenLength)),
              Expression.Literal.ofInt(fieldNameTokenSuffix(name)),
              Expression.Literal.ofInt(fieldNameTokenSuffixLength(tokenLength)),
              Expression.Literal.ofInt(tokenLength))
          .inline();
    }
    return new Expression.Invoke(
            readerRef(readerMode),
            "tryReadNextFieldNameColon",
            TypeRef.of(boolean.class),
            Expression.Literal.ofLong(property.nameHash()),
            Expression.Literal.ofLong(packedNameMask(property.name().length())),
            Expression.Literal.ofInt(property.name().length()))
        .inline();
  }

  private static int fieldNameTokenLength(String name) {
    return name.length() + 3;
  }

  private static long fieldNameTokenPrefix(String name) {
    int tokenLength = fieldNameTokenLength(name);
    int prefixLength = Math.min(tokenLength, Long.BYTES);
    long value = 0;
    for (int i = 0; i < prefixLength; i++) {
      value |= (long) fieldNameTokenByte(name, i) << (i << 3);
    }
    return value;
  }

  private static long fieldNameTokenPrefixMask(int tokenLength) {
    int prefixLength = Math.min(tokenLength, Long.BYTES);
    return prefixLength == Long.BYTES ? -1L : (1L << (prefixLength << 3)) - 1;
  }

  private static int fieldNameTokenSuffix(String name) {
    int tokenLength = fieldNameTokenLength(name);
    int suffixLength = fieldNameTokenSuffixLength(tokenLength);
    int value = 0;
    for (int i = 0; i < suffixLength; i++) {
      value |= fieldNameTokenByte(name, i + Long.BYTES) << (i << 3);
    }
    return value;
  }

  private static int fieldNameTokenSuffixLength(int tokenLength) {
    return Math.max(0, tokenLength - Long.BYTES);
  }

  private static int fieldNameTokenByte(String name, int index) {
    if (index == 0) {
      return '"';
    }
    int nameLength = name.length();
    if (index <= nameLength) {
      return name.charAt(index - 1) & 0xFF;
    }
    return index == nameLength + 1 ? '"' : ':';
  }

  private static Expression slowCall(
      String slowMethod, Expression object, Expression expectedIndex) {
    return slowCall(slowMethod, object, expectedIndex, null);
  }

  private static Expression slowCall(
      String slowMethod, Expression object, Expression expectedIndex, Expression firstFieldIndex) {
    if (firstFieldIndex == null) {
      return new Expression.Invoke(
          new Reference("this", TypeRef.of(Object.class)),
          slowMethod,
          "",
          TypeRef.of(void.class),
          false,
          false,
          readerRefForCall(),
          ownerRef(),
          typeResolverRef(),
          object,
          expectedIndex);
    }
    return new Expression.Invoke(
        new Reference("this", TypeRef.of(Object.class)),
        slowMethod,
        "",
        TypeRef.of(void.class),
        false,
        false,
        readerRefForCall(),
        ownerRef(),
        typeResolverRef(),
        object,
        expectedIndex,
        firstFieldIndex);
  }

  private static Reference readerRefForCall() {
    return new Reference("reader");
  }

  private static Expression arrayValue(Expression array, int index) {
    return new Expression.ArrayValue(array, Expression.Literal.ofInt(index));
  }

  private static Expression eq(Expression left, Expression right) {
    return new Expression.Comparator("==", left, right, true);
  }

  private static Expression ne(Expression left, Expression right) {
    return new Expression.Comparator("!=", left, right, true);
  }

  private static Expression lt(Expression left, Expression right) {
    return new Expression.Comparator("<", left, right, true);
  }

  private static Expression ge(Expression left, Expression right) {
    return new Expression.Comparator(">=", left, right, true);
  }

  private static Expression and(Expression left, Expression right) {
    return new Expression.LogicalAnd(left, right);
  }

  private static Expression not(Expression expression) {
    return new Expression.Not(expression);
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

  private static boolean usesReadTypeField(JsonFieldInfo property) {
    switch (property.readKind()) {
      case ARRAY:
      case COLLECTION:
      case MAP:
        return true;
      case OBJECT:
        return usesReadObjectCodec(property);
      default:
        return false;
    }
  }

  private static boolean usesReadObjectCodec(JsonFieldInfo property) {
    return property.readKind() == JsonFieldKind.OBJECT
        && property.readRawType() != Object.class
        && property.readTypeInfo().codec() instanceof BaseObjectCodec;
  }

  private static boolean storesReadObjectCodec(Class<?> type, JsonFieldInfo property) {
    Class<?> nestedType = readNestedType(property);
    return nestedType != null && nestedType != type;
  }

  private Expression readField(
      JsonCodecBuilder builder,
      Class<?> type,
      JsonFieldInfo property,
      int id,
      int readerMode,
      Expression object,
      boolean record) {
    if (record) {
      return readRecordField(type, property, id, readerMode, object);
    }
    Class<?> rawType = property.readRawType();
    switch (property.readKind()) {
      case BOOLEAN:
        return readBoolean(builder, property, rawType, readerMode, object);
      case INT:
        return readInt(builder, property, rawType, readerMode, object);
      case LONG:
        return readLong(builder, property, rawType, readerMode, object);
      case STRING:
        return builder.setField(property, object, readStringExpr(readerMode));
      case ENUM:
        return readEnum(builder, property, id, readerMode, object);
      case ARRAY:
      case COLLECTION:
      case MAP:
        return readResolvedField(builder, property, id, readerMode, object);
      case OBJECT:
        return readObject(builder, type, property, id, readerMode, object);
      default:
        return new Expression.Invoke(
            fieldRef("p" + id, JsonFieldInfo.class),
            "read",
            readerRef(readerMode),
            object,
            typeResolverRef());
    }
  }

  private Expression readRecordField(
      Class<?> type, JsonFieldInfo property, int id, int readerMode, Expression object) {
    Class<?> rawType = property.readRawType();
    switch (property.readKind()) {
      case BOOLEAN:
        return readRecordBoolean(rawType, id, readerMode, object);
      case INT:
        return readRecordInt(rawType, id, readerMode, object);
      case LONG:
        return readRecordLong(rawType, id, readerMode, object);
      case STRING:
        return assignRecord(object, id, readStringExpr(readerMode));
      case ENUM:
        return readRecordEnum(id, readerMode, object);
      case ARRAY:
      case COLLECTION:
      case MAP:
        return assignRecord(object, id, readResolvedValue(property, id, readerMode));
      case OBJECT:
        return readRecordObject(type, property, id, readerMode, object);
      default:
        return assignRecord(
            object,
            id,
            new Expression.Invoke(
                fieldRef("p" + id, JsonFieldInfo.class),
                "readValue",
                TypeRef.of(Object.class),
                true,
                readerRef(readerMode),
                typeResolverRef()));
    }
  }

  private static Expression readRecordBoolean(
      Class<?> rawType, int id, int readerMode, Expression object) {
    Expression value = box(Boolean.class, readBooleanExpr(readerMode));
    if (rawType.isPrimitive()) {
      return assignRecord(object, id, value);
    }
    return new Expression.If(
        tryReadNullExpr(readerMode),
        assignRecord(object, id, new Expression.Null(TypeRef.of(Boolean.class), false)),
        assignRecord(object, id, value));
  }

  private static Expression readRecordInt(
      Class<?> rawType, int id, int readerMode, Expression object) {
    Expression value = box(Integer.class, readIntExpr(readerMode));
    if (rawType.isPrimitive()) {
      return assignRecord(object, id, value);
    }
    return new Expression.If(
        tryReadNullExpr(readerMode),
        assignRecord(object, id, new Expression.Null(TypeRef.of(Integer.class), false)),
        assignRecord(object, id, value));
  }

  private static Expression readRecordLong(
      Class<?> rawType, int id, int readerMode, Expression object) {
    Expression value = box(Long.class, readLongExpr(readerMode));
    if (rawType.isPrimitive()) {
      return assignRecord(object, id, value);
    }
    return new Expression.If(
        tryReadNullExpr(readerMode),
        assignRecord(object, id, new Expression.Null(TypeRef.of(Long.class), false)),
        assignRecord(object, id, value));
  }

  private static Expression readRecordEnum(int id, int readerMode, Expression object) {
    return new Expression.If(
        tryReadNullExpr(readerMode),
        assignRecord(object, id, new Expression.Null(TypeRef.of(Object.class), false)),
        assignRecord(object, id, readEnumValue(Object.class, id, readerMode)));
  }

  private static Expression readRecordObject(
      Class<?> type, JsonFieldInfo property, int id, int readerMode, Expression object) {
    if (property.readRawType() == Object.class
        || !(property.readTypeInfo().codec() instanceof BaseObjectCodec)) {
      return assignRecord(
          object,
          id,
          new Expression.Invoke(
              fieldRef("p" + id, JsonFieldInfo.class),
              "readValue",
              TypeRef.of(Object.class),
              true,
              readerRef(readerMode),
              typeResolverRef()));
    }
    return new Expression.If(
        tryReadNullExpr(readerMode),
        assignRecord(object, id, new Expression.Null(TypeRef.of(property.readRawType()), false)),
        assignRecord(object, id, readObjectValue(type, property, id, readerMode)));
  }

  private static Expression readBoolean(
      JsonCodecBuilder builder,
      JsonFieldInfo property,
      Class<?> rawType,
      int readerMode,
      Expression object) {
    if (rawType.isPrimitive()) {
      return builder.setField(property, object, readBooleanExpr(readerMode));
    }
    return new Expression.If(
        tryReadNullExpr(readerMode),
        builder.setNull(property, object),
        builder.setField(property, object, box(Boolean.class, readBooleanExpr(readerMode))));
  }

  private static Expression readInt(
      JsonCodecBuilder builder,
      JsonFieldInfo property,
      Class<?> rawType,
      int readerMode,
      Expression object) {
    if (rawType.isPrimitive()) {
      return builder.setField(property, object, readIntExpr(readerMode));
    }
    return new Expression.If(
        tryReadNullExpr(readerMode),
        builder.setNull(property, object),
        builder.setField(property, object, box(Integer.class, readIntExpr(readerMode))));
  }

  private static Expression readLong(
      JsonCodecBuilder builder,
      JsonFieldInfo property,
      Class<?> rawType,
      int readerMode,
      Expression object) {
    if (rawType.isPrimitive()) {
      return builder.setField(property, object, readLongExpr(readerMode));
    }
    return new Expression.If(
        tryReadNullExpr(readerMode),
        builder.setNull(property, object),
        builder.setField(property, object, box(Long.class, readLongExpr(readerMode))));
  }

  private static Expression readEnum(
      JsonCodecBuilder builder, JsonFieldInfo property, int id, int readerMode, Expression object) {
    return new Expression.If(
        tryReadNullExpr(readerMode),
        builder.setNull(property, object),
        builder.setField(property, object, readEnumValue(property.readRawType(), id, readerMode)));
  }

  private static Expression readResolvedField(
      JsonCodecBuilder builder, JsonFieldInfo property, int id, int readerMode, Expression object) {
    return builder.setField(property, object, readResolvedValue(property, id, readerMode));
  }

  private static Expression readObject(
      JsonCodecBuilder builder,
      Class<?> type,
      JsonFieldInfo property,
      int id,
      int readerMode,
      Expression object) {
    if (property.readRawType() == Object.class
        || !(property.readTypeInfo().codec() instanceof BaseObjectCodec)) {
      return new Expression.Invoke(
          fieldRef("p" + id, JsonFieldInfo.class),
          "read",
          readerRef(readerMode),
          object,
          typeResolverRef());
    }
    return new Expression.If(
        tryReadNullExpr(readerMode),
        builder.setNull(property, object),
        builder.setField(property, object, readObjectValue(type, property, id, readerMode)));
  }

  private static Expression assignRecord(Expression object, int id, Expression value) {
    return new Expression.AssignArrayElem(object, value, Expression.Literal.ofInt(id));
  }

  private static Expression box(Class<?> boxedType, Expression value) {
    return new Expression.StaticInvoke(
        boxedType, "valueOf", "", TypeRef.of(boxedType), false, true, false, value);
  }

  private static Expression readEnumValue(Class<?> enumType, int id, int readerMode) {
    return new Expression.Cast(
        new Expression.Invoke(
            fieldRef("r" + id, JsonCodec.class),
            readEnumMethod(readerMode),
            "",
            TypeRef.of(Object.class),
            true,
            false,
            readerRef(readerMode)),
        TypeRef.of(enumType));
  }

  private static Expression readResolvedValue(JsonFieldInfo property, int id, int readerMode) {
    return new Expression.Cast(
        new Expression.Invoke(
            fieldRef("r" + id, JsonCodec.class),
            readObjectMethod(readerMode),
            TypeRef.of(Object.class),
            true,
            readerRef(readerMode),
            fieldRef("t" + id, JsonTypeInfo.class),
            typeResolverRef()),
        TypeRef.of(property.readRawType()));
  }

  private static Expression readObjectValue(
      Class<?> type, JsonFieldInfo property, int id, int readerMode) {
    Expression codec =
        property.readRawType() == type ? ownerRef() : fieldRef("c" + id, BaseObjectCodec.class);
    return new Expression.Cast(
        new Expression.Invoke(
            codec,
            readObjectNonNullMethod(readerMode),
            TypeRef.of(Object.class),
            true,
            readerRef(readerMode),
            fieldRef("t" + id, JsonTypeInfo.class),
            typeResolverRef()),
        TypeRef.of(property.readRawType()));
  }

  private static String readEnumMethod(int readerMode) {
    switch (readerMode) {
      case LATIN1_READER:
        return "readNextLatin1Enum";
      case UTF16_READER:
        return "readNextUtf16Enum";
      case UTF8_READER:
        return "readNextUtf8Enum";
      default:
        return "readEnum";
    }
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

  private static String readObjectNonNullMethod(int readerMode) {
    switch (readerMode) {
      case LATIN1_READER:
        return "readLatin1NonNull";
      case UTF16_READER:
        return "readUtf16NonNull";
      case UTF8_READER:
        return "readUtf8NonNull";
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
    boolean objectStartFused = canFuseObjectStart(properties);
    boolean[] useInfo = new boolean[properties.length];
    boolean[] usePrefix = new boolean[properties.length];
    for (int i = 0; i < properties.length; i++) {
      JsonFieldInfo property = properties[i];
      useInfo[i] = true;
      usePrefix[i] = usesPrefix(property);
      if (useInfo[i]) {
        ctx.addField(JsonFieldInfo.class, "p" + i);
      }
      if (usesWriteCodec(property)) {
        ctx.addField(codecFieldType(property.writeTypeInfo().codec()), "c" + i);
      }
      if (usePrefix[i]) {
        if (utf8) {
          ctx.addField(byte[].class, "u" + i);
          ctx.addField(byte[].class, "uc" + i);
        } else {
          ctx.addField(byte[].class, "s" + i);
          ctx.addField(byte[].class, "sc" + i);
        }
      }
    }
    addGeneratedConstructor(
        ctx,
        writerConstructorExpression(properties, utf8),
        JsonFieldInfo[].class,
        "properties",
        JsonCodec[].class,
        "codecs");
    addGeneratedMethod(
        ctx,
        "public",
        utf8 ? "writeUtf8" : "writeString",
        writeExpression(builder, type, properties, utf8, objectStartFused),
        void.class,
        utf8 ? Utf8JsonWriter.class : StringJsonWriter.class,
        "writer",
        Object.class,
        "value",
        JsonTypeResolver.class,
        "typeResolver");
    return ctx.genCode();
  }

  private boolean usesPrefix(JsonFieldInfo property) {
    JsonFieldKind kind = property.writeKind();
    return kind != JsonFieldKind.BOOLEAN && kind != JsonFieldKind.ENUM
        || writeNullFields && !property.writeRawType().isPrimitive();
  }

  private Expression writerConstructorExpression(JsonFieldInfo[] properties, boolean utf8) {
    Expression.ListExpression expressions = new Expression.ListExpression();
    Reference propertiesRef = new Reference("properties", TypeRef.of(JsonFieldInfo[].class));
    Reference codecsRef = new Reference("codecs", TypeRef.of(JsonCodec[].class));
    expressions.add(new Expression.SuperCall(propertiesRef, codecsRef));
    for (int i = 0; i < properties.length; i++) {
      Expression id = Expression.Literal.ofInt(i);
      Expression property = new Expression.ArrayValue(propertiesRef, id);
      expressions.add(
          new Expression.Assign(
              new Reference("this.p" + i, TypeRef.of(JsonFieldInfo.class)), property));
      if (usesWriteCodec(properties[i])) {
        Class<?> codecType = codecFieldType(properties[i].writeTypeInfo().codec());
        expressions.add(
            new Expression.Assign(
                new Reference("this.c" + i, TypeRef.of(codecType)),
                new Expression.Cast(
                    new Expression.ArrayValue(codecsRef, id), TypeRef.of(codecType))));
      }
      if (usesPrefix(properties[i])) {
        if (utf8) {
          expressions.add(
              new Expression.Assign(
                  new Reference("this.u" + i, TypeRef.of(byte[].class)),
                  new Expression.Invoke(property, "utf8NamePrefix", TypeRef.of(byte[].class))));
          expressions.add(
              new Expression.Assign(
                  new Reference("this.uc" + i, TypeRef.of(byte[].class)),
                  new Expression.Invoke(
                      property, "utf8CommaNamePrefix", TypeRef.of(byte[].class))));
        } else {
          expressions.add(
              new Expression.Assign(
                  new Reference("this.s" + i, TypeRef.of(byte[].class)),
                  new Expression.Invoke(property, "stringNamePrefix", TypeRef.of(byte[].class))));
          expressions.add(
              new Expression.Assign(
                  new Reference("this.sc" + i, TypeRef.of(byte[].class)),
                  new Expression.Invoke(
                      property, "stringCommaNamePrefix", TypeRef.of(byte[].class))));
        }
      }
    }
    return expressions;
  }

  private Expression writeExpression(
      JsonCodecBuilder builder,
      Class<?> type,
      JsonFieldInfo[] properties,
      boolean utf8,
      boolean objectStartFused) {
    Expression object =
        new Expression.Variable(
            "object",
            new Expression.Cast(
                new Reference("value", TypeRef.of(Object.class)), TypeRef.of(type)));
    Expression.ListExpression expressions = new Expression.ListExpression();
    expressions.add(object);
    Expression index = null;
    if (!objectStartFused) {
      expressions.add(new Expression.Invoke(writerRef(utf8), "writeObjectStart"));
      index = new Expression.Variable("index", Expression.Literal.ofInt(0));
      expressions.add(index);
    }
    boolean commaKnown = objectStartFused;
    for (int i = 0; i < properties.length; i++) {
      if (objectStartFused && i == 0) {
        expressions.add(
            writeObjectStartPrimitive(
                properties[i], builder.fieldValue(properties[i], object), utf8));
      } else {
        expressions.add(writeProp(builder, properties[i], i, utf8, commaKnown, index, object));
      }
      if (writeNullFields || properties[i].writeRawType().isPrimitive()) {
        commaKnown = true;
      }
    }
    expressions.add(new Expression.Invoke(writerRef(utf8), "writeObjectEnd"));
    return expressions;
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

  private static Expression writeObjectStartPrimitive(
      JsonFieldInfo property, Expression value, boolean utf8) {
    switch (property.writeKind()) {
      case BYTE:
      case SHORT:
      case INT:
        return new Expression.Invoke(
            writerRef(utf8), "writeObjectIntField", prefixRef(utf8, false, 0), value);
      case LONG:
        return new Expression.Invoke(
            writerRef(utf8), "writeObjectLongField", prefixRef(utf8, false, 0), value);
      default:
        throw new ForyJsonException(
            "Unsupported generated object-start kind " + property.writeKind());
    }
  }

  private Expression writeProp(
      JsonCodecBuilder builder,
      JsonFieldInfo property,
      int id,
      boolean utf8,
      boolean commaKnown,
      Expression index,
      Expression object) {
    Class<?> rawType = property.writeRawType();
    if (rawType.isPrimitive()) {
      return writePrimitive(
          property, id, builder.fieldValue(property, object), utf8, commaKnown, index);
    }
    Expression value =
        new Expression.Variable(
            "v" + id,
            new Expression.Cast(builder.fieldValue(property, object), TypeRef.of(rawType)));
    Expression nullValue = new Expression.Null(TypeRef.of(rawType), false);
    if (writeNullFields) {
      if (isPrefixValue(property.writeKind())) {
        return new Expression.ListExpression(
            value,
            new Expression.If(
                eq(value, nullValue),
                new Expression.ListExpression(
                    writeFieldName(id, utf8, commaKnown, index),
                    new Expression.Invoke(writerRef(utf8), "writeNull")),
                writeValue(property, id, value, utf8, commaKnown, index)));
      }
      return new Expression.ListExpression(
          value,
          writeFieldName(id, utf8, commaKnown, index),
          new Expression.If(
              eq(value, nullValue),
              new Expression.Invoke(writerRef(utf8), "writeNull"),
              writeValue(property, id, value, utf8, true, index)));
    }
    Expression write =
        isPrefixValue(property.writeKind())
            ? writeValue(property, id, value, utf8, commaKnown, index)
            : new Expression.ListExpression(
                writeFieldName(id, utf8, commaKnown, index),
                writeValue(property, id, value, utf8, true, index));
    return new Expression.ListExpression(value, new Expression.If(ne(value, nullValue), write));
  }

  private Expression writePrimitive(
      JsonFieldInfo property,
      int id,
      Expression value,
      boolean utf8,
      boolean commaKnown,
      Expression index) {
    switch (property.writeKind()) {
      case BOOLEAN:
        return writeRawFieldValue(
            utf8, commaKnown, index, booleanFieldValue(id, value, utf8, commaKnown, index));
      case BYTE:
      case SHORT:
      case INT:
        return writeNumberField(id, value, false, utf8, commaKnown, index);
      case LONG:
        return writeNumberField(id, value, true, utf8, commaKnown, index);
      default:
        return new Expression.ListExpression(
            writeFieldName(id, utf8, commaKnown, index),
            writePrimitiveScalar(property.writeKind(), value, utf8));
    }
  }

  private static Expression writeNumberField(
      int id,
      Expression value,
      boolean longValue,
      boolean utf8,
      boolean commaKnown,
      Expression index) {
    String writerMethod = longValue ? "writeLongField" : "writeIntField";
    Expression.ListExpression expressions =
        new Expression.ListExpression(
            new Expression.Invoke(
                writerRef(utf8),
                writerMethod,
                prefixRef(utf8, false, id),
                prefixRef(utf8, true, id),
                commaIndex(commaKnown, index),
                value));
    if (!commaKnown) {
      expressions.add(increment(index));
    }
    return expressions;
  }

  private static Expression writeStringField(
      int id, Expression value, boolean utf8, boolean commaKnown, Expression index) {
    Expression.ListExpression expressions =
        new Expression.ListExpression(
            new Expression.Invoke(
                writerRef(utf8),
                "writeStringField",
                prefixRef(utf8, false, id),
                prefixRef(utf8, true, id),
                commaIndex(commaKnown, index),
                value));
    if (!commaKnown) {
      expressions.add(increment(index));
    }
    return expressions;
  }

  private static Expression writeFieldName(
      int id, boolean utf8, boolean commaKnown, Expression index) {
    Expression prefix =
        commaKnown
            ? prefixRef(utf8, true, id)
            : new Expression.Ternary(
                eq(index, Expression.Literal.ofInt(0)),
                prefixRef(utf8, false, id),
                prefixRef(utf8, true, id),
                true,
                TypeRef.of(byte[].class));
    Expression.ListExpression expressions =
        new Expression.ListExpression(
            new Expression.Invoke(writerRef(utf8), "writeRawValue", prefix));
    if (!commaKnown) {
      expressions.add(increment(index));
    }
    return expressions;
  }

  private Expression writeValue(
      JsonFieldInfo property,
      int id,
      Expression value,
      boolean utf8,
      boolean commaKnown,
      Expression index) {
    JsonFieldKind kind = property.writeKind();
    switch (kind) {
      case BOOLEAN:
        return writeRawFieldValue(
            utf8,
            commaKnown,
            index,
            booleanFieldValue(
                id,
                new Expression.Invoke(value, "booleanValue", TypeRef.of(boolean.class)).inline(),
                utf8,
                commaKnown,
                index));
      case BYTE:
      case SHORT:
      case INT:
        return writeNumberField(
            id,
            new Expression.Invoke(value, "intValue", TypeRef.of(int.class)).inline(),
            false,
            utf8,
            commaKnown,
            index);
      case LONG:
        return writeNumberField(
            id,
            new Expression.Invoke(value, "longValue", TypeRef.of(long.class)).inline(),
            true,
            utf8,
            commaKnown,
            index);
      case STRING:
        return writeStringField(id, value, utf8, commaKnown, index);
      case ENUM:
        return writeRawFieldValue(
            utf8, commaKnown, index, enumFieldValue(id, value, utf8, commaKnown, index));
      case FLOAT:
      case DOUBLE:
      case CHAR:
        return writeScalar(kind, value, utf8);
      case ARRAY:
      case COLLECTION:
      case MAP:
        return writeCodec(id, value, utf8);
      default:
        return writeCodec(id, value, utf8);
    }
  }

  private static Expression writeRawFieldValue(
      boolean utf8, boolean commaKnown, Expression index, Expression value) {
    Expression.ListExpression expressions =
        new Expression.ListExpression(
            new Expression.Invoke(writerRef(utf8), "writeRawValue", value));
    if (!commaKnown) {
      expressions.add(increment(index));
    }
    return expressions;
  }

  private static Expression booleanFieldValue(
      int id, Expression value, boolean utf8, boolean commaKnown, Expression index) {
    return new Expression.Invoke(
            fieldRef("p" + id, JsonFieldInfo.class),
            utf8 ? "utf8BooleanFieldValue" : "stringBooleanFieldValue",
            TypeRef.of(byte[].class),
            value,
            commaFlag(commaKnown, index))
        .inline();
  }

  private static Expression enumFieldValue(
      int id, Expression value, boolean utf8, boolean commaKnown, Expression index) {
    return new Expression.Invoke(
            fieldRef("p" + id, JsonFieldInfo.class),
            utf8 ? "utf8EnumFieldValue" : "stringEnumFieldValue",
            TypeRef.of(byte[].class),
            value,
            commaFlag(commaKnown, index))
        .inline();
  }

  private static Expression writeCodec(int id, Expression value, boolean utf8) {
    return new Expression.Invoke(
        fieldRef("c" + id, JsonCodec.class),
        utf8 ? "writeUtf8" : "writeString",
        writerRef(utf8),
        value,
        typeResolverRef());
  }

  private Expression writeScalar(JsonFieldKind kind, Expression value, boolean utf8) {
    switch (kind) {
      case FLOAT:
        return new Expression.Invoke(
            writerRef(utf8),
            "writeFloat",
            new Expression.Invoke(value, "floatValue", TypeRef.of(float.class)).inline());
      case DOUBLE:
        return new Expression.Invoke(
            writerRef(utf8),
            "writeDouble",
            new Expression.Invoke(value, "doubleValue", TypeRef.of(double.class)).inline());
      case CHAR:
        return new Expression.Invoke(
            writerRef(utf8),
            "writeChar",
            new Expression.Invoke(value, "charValue", TypeRef.of(char.class)).inline());
      default:
        throw new ForyJsonException("Unsupported generated scalar kind " + kind);
    }
  }

  private Expression writePrimitiveScalar(JsonFieldKind kind, Expression value, boolean utf8) {
    switch (kind) {
      case FLOAT:
        return new Expression.Invoke(writerRef(utf8), "writeFloat", value);
      case DOUBLE:
        return new Expression.Invoke(writerRef(utf8), "writeDouble", value);
      case CHAR:
        return new Expression.Invoke(writerRef(utf8), "writeChar", value);
      default:
        throw new ForyJsonException("Unsupported generated primitive kind " + kind);
    }
  }

  private static Reference writerRef(boolean utf8) {
    return new Reference(
        "writer", TypeRef.of(utf8 ? Utf8JsonWriter.class : StringJsonWriter.class));
  }

  private static Reference prefixRef(boolean utf8, boolean comma, int id) {
    String prefix = utf8 ? (comma ? "uc" : "u") : (comma ? "sc" : "s");
    return fieldRef(prefix + id, byte[].class);
  }

  private static Expression commaIndex(boolean commaKnown, Expression index) {
    return commaKnown ? Expression.Literal.ofInt(1) : index;
  }

  private static Expression commaFlag(boolean commaKnown, Expression index) {
    return commaKnown ? Expression.Literal.True : ne(index, Expression.Literal.ofInt(0));
  }

  private static Expression increment(Expression value) {
    return new Expression.Assign(value, new Expression.Add(value, Expression.Literal.ofInt(1)));
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
}
