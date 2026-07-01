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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.apache.fory.codegen.Code;
import org.apache.fory.codegen.CodegenContext;
import org.apache.fory.codegen.Expression;
import org.apache.fory.codegen.Expression.Reference;
import org.apache.fory.codegen.ExpressionOptimizer;
import org.apache.fory.json.ForyJsonException;
import org.apache.fory.json.codec.JsonCodec;
import org.apache.fory.json.meta.JsonFieldInfo;
import org.apache.fory.json.meta.JsonFieldKind;
import org.apache.fory.json.resolver.JsonTypeResolver;
import org.apache.fory.json.writer.GeneratedObjectWriter;
import org.apache.fory.json.writer.StringJsonWriter;
import org.apache.fory.json.writer.StringObjectWriter;
import org.apache.fory.json.writer.Utf8JsonWriter;
import org.apache.fory.json.writer.Utf8ObjectWriter;
import org.apache.fory.reflect.TypeRef;

final class JsonWriterCodegen {
  private static final int MIN_SPLIT_MEMBERS = 12;
  // Keep generated member helpers below C2's big-method range without fragmenting them into
  // tiny calls. This mirrors Fory core's object-codec split strategy for large generated codecs.
  private static final int MEMBER_GROUP_SIZE = 10;

  private final JsonCodegen codegen;
  private final boolean writeNullFields;

  JsonWriterCodegen(JsonCodegen codegen) {
    this.codegen = codegen;
    this.writeNullFields = codegen.writeNullFields;
  }

  private Class<?> codecFieldType(JsonCodec codec) {
    return codegen.codecFieldType(codec);
  }

  private static boolean usesWriteCodec(JsonFieldInfo property) {
    return JsonCodegen.usesWriteCodec(property);
  }

  private static Reference typeResolverRef() {
    return new Reference("typeResolver", TypeRef.of(JsonTypeResolver.class));
  }

  private static Reference fieldRef(String name, Class<?> type) {
    return Reference.fieldRef(name, TypeRef.of(type));
  }

  private static Expression eq(Expression left, Expression right) {
    return new Expression.Comparator("==", left, right, true);
  }

  private static Expression ne(Expression left, Expression right) {
    return new Expression.Comparator("!=", left, right, true);
  }

  private static void addGeneratedConstructor(
      CodegenContext ctx, Expression expression, Object... params) {
    ctx.clearExprState();
    Code.ExprCode body = expression.genCode(ctx);
    String code = body.code();
    code = code == null ? "" : ctx.optimizeMethodCode(code);
    ctx.addConstructor(code, params);
  }

  private static void addGeneratedMethod(
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

  String genWriterCode(
      JsonGeneratedCodecBuilder builder,
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
      JsonGeneratedCodecBuilder builder,
      Class<?> type,
      JsonFieldInfo[] properties,
      boolean utf8,
      boolean objectStartFused) {
    Expression object =
        new Expression.Variable(
            "object",
            new Expression.Cast(
                new Reference("value", TypeRef.of(Object.class)), TypeRef.of(type)));
    Reference writer = writerRef(utf8);
    Reference typeResolver = typeResolverRef();
    Expression.ListExpression expressions = new Expression.ListExpression();
    expressions.add(object);
    Expression index = null;
    if (!objectStartFused) {
      expressions.add(new Expression.Invoke(writer, "writeObjectStart"));
      index = new Expression.Variable("index", Expression.Literal.ofInt(0));
      expressions.add(index);
    }
    boolean commaKnown = objectStartFused;
    boolean splitMembers = properties.length >= MIN_SPLIT_MEMBERS;
    List<Expression> memberGroup = splitMembers ? new ArrayList<>(MEMBER_GROUP_SIZE) : null;
    for (int i = 0; i < properties.length; i++) {
      Expression member;
      if (objectStartFused && i == 0) {
        member =
            writeObjectStartPrimitive(
                properties[i], builder.fieldValue(properties[i], object), utf8, writer);
      } else {
        member =
            writeProp(
                builder, properties[i], i, utf8, commaKnown, index, object, writer, typeResolver);
      }
      if (splitMembers && commaKnown) {
        memberGroup.add(member);
        if (memberGroup.size() == MEMBER_GROUP_SIZE) {
          addMemberGroup(builder, expressions, memberGroup, object, writer, typeResolver);
        }
      } else {
        if (splitMembers) {
          addMemberGroup(builder, expressions, memberGroup, object, writer, typeResolver);
        }
        expressions.add(member);
      }
      if (writeNullFields || properties[i].writeRawType().isPrimitive()) {
        commaKnown = true;
      }
    }
    if (splitMembers) {
      addMemberGroup(builder, expressions, memberGroup, object, writer, typeResolver);
    }
    expressions.add(new Expression.Invoke(writer, "writeObjectEnd"));
    return expressions;
  }

  private static void addMemberGroup(
      JsonGeneratedCodecBuilder builder,
      Expression.ListExpression expressions,
      List<Expression> memberGroup,
      Expression object,
      Reference writer,
      Reference typeResolver) {
    if (memberGroup.isEmpty()) {
      return;
    }
    if (memberGroup.size() == 1) {
      expressions.add(memberGroup.get(0));
      memberGroup.clear();
      return;
    }
    LinkedHashSet<Expression> cutPoints = new LinkedHashSet<>();
    cutPoints.add(object);
    cutPoints.add(writer);
    cutPoints.add(typeResolver);
    expressions.add(
        ExpressionOptimizer.invokeGenerated(
            builder.context(),
            cutPoints,
            new Expression.ListExpression(new ArrayList<>(memberGroup)),
            "writeMembers",
            false));
    memberGroup.clear();
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
      JsonFieldInfo property, Expression value, boolean utf8, Expression writer) {
    switch (property.writeKind()) {
      case BYTE:
      case SHORT:
      case INT:
        return new Expression.Invoke(
            writer, "writeObjectIntField", prefixRef(utf8, false, 0), value);
      case LONG:
        return new Expression.Invoke(
            writer, "writeObjectLongField", prefixRef(utf8, false, 0), value);
      default:
        throw new ForyJsonException(
            "Unsupported generated object-start kind " + property.writeKind());
    }
  }

  private Expression writeProp(
      JsonGeneratedCodecBuilder builder,
      JsonFieldInfo property,
      int id,
      boolean utf8,
      boolean commaKnown,
      Expression index,
      Expression object,
      Expression writer,
      Expression typeResolver) {
    Class<?> rawType = property.writeRawType();
    if (rawType.isPrimitive()) {
      return writePrimitive(
          property, id, builder.fieldValue(property, object), utf8, commaKnown, index, writer);
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
                    writeFieldName(id, utf8, commaKnown, index, writer),
                    new Expression.Invoke(writer, "writeNull")),
                writeValue(property, id, value, utf8, commaKnown, index, writer, typeResolver)));
      }
      return new Expression.ListExpression(
          value,
          writeFieldName(id, utf8, commaKnown, index, writer),
          new Expression.If(
              eq(value, nullValue),
              new Expression.Invoke(writer, "writeNull"),
              writeValue(property, id, value, utf8, true, index, writer, typeResolver)));
    }
    Expression write =
        isPrefixValue(property.writeKind())
            ? writeValue(property, id, value, utf8, commaKnown, index, writer, typeResolver)
            : new Expression.ListExpression(
                writeFieldName(id, utf8, commaKnown, index, writer),
                writeValue(property, id, value, utf8, true, index, writer, typeResolver));
    return new Expression.ListExpression(value, new Expression.If(ne(value, nullValue), write));
  }

  private Expression writePrimitive(
      JsonFieldInfo property,
      int id,
      Expression value,
      boolean utf8,
      boolean commaKnown,
      Expression index,
      Expression writer) {
    switch (property.writeKind()) {
      case BOOLEAN:
        return writeRawFieldValue(
            commaKnown, index, booleanFieldValue(id, value, utf8, commaKnown, index), writer);
      case BYTE:
      case SHORT:
      case INT:
        return writeNumberField(id, value, false, utf8, commaKnown, index, writer);
      case LONG:
        return writeNumberField(id, value, true, utf8, commaKnown, index, writer);
      default:
        return new Expression.ListExpression(
            writeFieldName(id, utf8, commaKnown, index, writer),
            writePrimitiveScalar(property.writeKind(), value, writer));
    }
  }

  private static Expression writeNumberField(
      int id,
      Expression value,
      boolean longValue,
      boolean utf8,
      boolean commaKnown,
      Expression index,
      Expression writer) {
    String writerMethod = longValue ? "writeLongField" : "writeIntField";
    if (commaKnown) {
      return new Expression.Invoke(writer, writerMethod, prefixRef(utf8, true, id), value);
    }
    Expression.ListExpression expressions =
        new Expression.ListExpression(
            new Expression.Invoke(
                writer,
                writerMethod,
                prefixRef(utf8, false, id),
                prefixRef(utf8, true, id),
                index,
                value));
    expressions.add(increment(index));
    return expressions;
  }

  private static Expression writeStringField(
      int id,
      Expression value,
      boolean utf8,
      boolean commaKnown,
      Expression index,
      Expression writer) {
    if (commaKnown) {
      return new Expression.Invoke(writer, "writeStringField", prefixRef(utf8, true, id), value);
    }
    Expression.ListExpression expressions =
        new Expression.ListExpression(
            new Expression.Invoke(
                writer,
                "writeStringField",
                prefixRef(utf8, false, id),
                prefixRef(utf8, true, id),
                index,
                value));
    expressions.add(increment(index));
    return expressions;
  }

  private static Expression writeFieldName(
      int id, boolean utf8, boolean commaKnown, Expression index, Expression writer) {
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
        new Expression.ListExpression(new Expression.Invoke(writer, "writeRawValue", prefix));
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
      Expression index,
      Expression writer,
      Expression typeResolver) {
    JsonFieldKind kind = property.writeKind();
    switch (kind) {
      case BOOLEAN:
        return writeRawFieldValue(
            commaKnown,
            index,
            booleanFieldValue(
                id,
                new Expression.Invoke(value, "booleanValue", TypeRef.of(boolean.class)).inline(),
                utf8,
                commaKnown,
                index),
            writer);
      case BYTE:
      case SHORT:
      case INT:
        return writeNumberField(
            id,
            new Expression.Invoke(value, "intValue", TypeRef.of(int.class)).inline(),
            false,
            utf8,
            commaKnown,
            index,
            writer);
      case LONG:
        return writeNumberField(
            id,
            new Expression.Invoke(value, "longValue", TypeRef.of(long.class)).inline(),
            true,
            utf8,
            commaKnown,
            index,
            writer);
      case STRING:
        return writeStringField(id, value, utf8, commaKnown, index, writer);
      case ENUM:
        return writeRawFieldValue(
            commaKnown, index, enumFieldValue(id, value, utf8, commaKnown, index), writer);
      case FLOAT:
      case DOUBLE:
      case CHAR:
        return writeScalar(kind, value, writer);
      case ARRAY:
      case MAP:
        return writeCodec(id, value, utf8, writer, typeResolver);
      case COLLECTION:
        if (property.writeElementRawType() == String.class) {
          return writeStringCollection(value, utf8, writer);
        }
        return writeCodec(id, value, utf8, writer, typeResolver);
      case OBJECT:
        Expression scalar = writeExactUtf8Scalar(property.writeRawType(), value, utf8, writer);
        return scalar == null ? writeCodec(id, value, utf8, writer, typeResolver) : scalar;
      default:
        return writeCodec(id, value, utf8, writer, typeResolver);
    }
  }

  private static Expression writeRawFieldValue(
      boolean commaKnown, Expression index, Expression value, Expression writer) {
    Expression.ListExpression expressions =
        new Expression.ListExpression(new Expression.Invoke(writer, "writeRawValue", value));
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

  private static Expression writeCodec(
      int id, Expression value, boolean utf8, Expression writer, Expression typeResolver) {
    return new Expression.Invoke(
        fieldRef("c" + id, JsonCodec.class),
        utf8 ? "writeUtf8" : "writeString",
        writer,
        value,
        typeResolver);
  }

  private static Expression writeExactUtf8Scalar(
      Class<?> rawType, Expression value, boolean utf8, Expression writer) {
    if (!utf8) {
      return null;
    }
    String writerMethod;
    if (rawType == UUID.class) {
      writerMethod = "writeUuid";
    } else if (rawType == LocalDate.class) {
      writerMethod = "writeLocalDate";
    } else if (rawType == OffsetDateTime.class) {
      writerMethod = "writeOffsetDateTime";
    } else if (rawType == BigDecimal.class) {
      return new Expression.Invoke(
          writer,
          "writeNumber",
          new Expression.Invoke(value, "toString", TypeRef.of(String.class)).inline());
    } else {
      return null;
    }
    return new Expression.Invoke(writer, writerMethod, value);
  }

  private static Expression writeStringCollection(
      Expression value, boolean utf8, Expression writer) {
    if (utf8) {
      return new Expression.Invoke(writer, "writeStringCollection", value);
    }
    return new Expression.ListExpression(
        new Expression.Invoke(writer, "writeArrayStart"),
        new Expression.ForEach(
            value,
            TypeRef.of(String.class),
            true,
            (index, element) ->
                new Expression.Invoke(writer, "writeStringElement", index, element)),
        new Expression.Invoke(writer, "writeArrayEnd"));
  }

  private Expression writeScalar(JsonFieldKind kind, Expression value, Expression writer) {
    switch (kind) {
      case FLOAT:
        return new Expression.Invoke(
            writer,
            "writeFloat",
            new Expression.Invoke(value, "floatValue", TypeRef.of(float.class)).inline());
      case DOUBLE:
        return new Expression.Invoke(
            writer,
            "writeDouble",
            new Expression.Invoke(value, "doubleValue", TypeRef.of(double.class)).inline());
      case CHAR:
        return new Expression.Invoke(
            writer,
            "writeChar",
            new Expression.Invoke(value, "charValue", TypeRef.of(char.class)).inline());
      default:
        throw new ForyJsonException("Unsupported generated scalar kind " + kind);
    }
  }

  private Expression writePrimitiveScalar(JsonFieldKind kind, Expression value, Expression writer) {
    switch (kind) {
      case FLOAT:
        return new Expression.Invoke(writer, "writeFloat", value);
      case DOUBLE:
        return new Expression.Invoke(writer, "writeDouble", value);
      case CHAR:
        return new Expression.Invoke(writer, "writeChar", value);
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
}
