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

package org.apache.fory.builder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.fory.Fory;
import org.apache.fory.builder.Generated.GeneratedMetaSharedLayerSerializer;
import org.apache.fory.codegen.CodeGenerator;
import org.apache.fory.codegen.Expression;
import org.apache.fory.codegen.Expression.Literal;
import org.apache.fory.codegen.Expression.NewArray;
import org.apache.fory.codegen.Expression.StaticInvoke;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.meta.TypeDef;
import org.apache.fory.reflect.TypeRef;
import org.apache.fory.resolver.TypeInfo;
import org.apache.fory.serializer.FieldGroups;
import org.apache.fory.serializer.FieldGroups.SerializationFieldInfo;
import org.apache.fory.serializer.MetaSharedLayerSerializer;
import org.apache.fory.serializer.Serializer;
import org.apache.fory.serializer.collection.CollectionLikeSerializer;
import org.apache.fory.serializer.collection.MapLikeSerializer;
import org.apache.fory.serializer.converter.FieldConverter;
import org.apache.fory.type.Descriptor;
import org.apache.fory.type.DescriptorGrouper;
import org.apache.fory.util.ExceptionUtils;
import org.apache.fory.util.Preconditions;
import org.apache.fory.util.StringUtils;

/**
 * A JIT codec builder for single-layer meta-shared serialization. This builder generates
 * serializers that only handle fields from a specific class layer, without including parent class
 * fields.
 *
 * <p>The generated serializer is the real layer serializer. It owns its own layer metadata, handles
 * the layer hot path directly, and never boots through an interpreter delegate.
 *
 * @see MetaSharedLayerSerializer
 * @see MetaSharedCodecBuilder
 * @see GeneratedMetaSharedLayerSerializer
 */
public class MetaSharedLayerCodecBuilder extends ObjectCodecBuilder {
  private static final String PUT_FIELD_NAMES_NAME = "PUT_FIELD_NAMES";
  private static final String PUT_FIELD_TYPES_NAME = "PUT_FIELD_TYPES";

  private final TypeDef layerTypeDef;
  private final SerializationFieldInfo[] allFieldInfos;
  private final boolean fallbackHotPath;

  public MetaSharedLayerCodecBuilder(TypeRef<?> beanType, Fory fory, TypeDef layerTypeDef) {
    super(beanType, fory, GeneratedMetaSharedLayerSerializer.class);
    Preconditions.checkArgument(
        !fory.getConfig().checkClassVersion(),
        "Class version check should be disabled when compatible mode is enabled.");
    this.layerTypeDef = fory.getTypeResolver().cacheTypeDef(layerTypeDef);
    DescriptorGrouper grouper =
        typeResolver(r -> r.createDescriptorGrouper(this.layerTypeDef, beanClass));
    objectCodecOptimizer = new ObjectCodecOptimizer(beanClass, grouper, false, ctx);
    allFieldInfos = FieldGroups.buildFieldInfos(fory, grouper).allFields;
    fallbackHotPath = requiresFallbackHotPath(allFieldInfos);
  }

  private static final Map<Long, Integer> idGenerator = new ConcurrentHashMap<>();

  @Override
  protected String codecSuffix() {
    Integer id = idGenerator.get(layerTypeDef.getId());
    if (id == null) {
      synchronized (idGenerator) {
        id = idGenerator.computeIfAbsent(layerTypeDef.getId(), k -> idGenerator.size());
      }
    }
    return "MetaSharedLayer" + id;
  }

  @Override
  public String genCode() {
    ctx.setPackage(CodeGenerator.getPackage(beanClass));
    String className = codecClassName(beanClass);
    ctx.setClassName(className);
    ctx.extendsClasses(ctx.type(parentSerializerClass));
    ctx.reserveName(POJO_CLASS_TYPE_NAME);
    ctx.addField(ctx.type(Fory.class), FORY_NAME);
    ctx.addField(
        true,
        true,
        ctx.type(String[].class),
        PUT_FIELD_NAMES_NAME,
        buildPutFieldNamesExpr());
    ctx.addField(
        true,
        true,
        ctx.type(Class[].class),
        PUT_FIELD_TYPES_NAME,
        buildPutFieldTypesExpr());

    String constructorCode =
        StringUtils.format(
            ""
                + "super(${fory}, ${cls}, ${layerTypeDefId}, ${markerClass}, ${fieldNames}, ${fieldTypes});\n"
                + "this.${fory} = ${fory};\n",
            "fory",
            FORY_NAME,
            "cls",
            POJO_CLASS_TYPE_NAME,
            "layerTypeDefId",
            layerTypeDef.getId() + "L",
            "markerClass",
            LayerMarkerClassGenerator.class.getName()
                + ".getOrCreate("
                + FORY_NAME
                + ", "
                + POJO_CLASS_TYPE_NAME
                + ", 0)",
            "fieldNames",
            PUT_FIELD_NAMES_NAME,
            "fieldTypes",
            PUT_FIELD_TYPES_NAME);

    String writeFieldsOnlyCode;
    String readAndSetFieldsCode;
    if (fallbackHotPath) {
      writeFieldsOnlyCode = "writeFieldsOnlyWithFallback(" + BUFFER_NAME + ", " + ROOT_OBJECT_NAME + ");";
      readAndSetFieldsCode =
          "return readAndSetFieldsWithFallback(" + BUFFER_NAME + ", " + ROOT_OBJECT_NAME + ");";
    } else {
      ctx.clearExprState();
      writeFieldsOnlyCode = genVoidExpressionCode(buildEncodeExpression());
      ctx.clearExprState();
      readAndSetFieldsCode = genDecodeMethodCode(buildDecodeIntoBeanExpression());
    }

    ctx.overrideMethod(
        "writeFieldsOnly",
        ctx.optimizeMethodCode(writeFieldsOnlyCode),
        void.class,
        MemoryBuffer.class,
        BUFFER_NAME,
        Object.class,
        ROOT_OBJECT_NAME);
    ctx.overrideMethod(
        "readAndSetFields",
        ctx.optimizeMethodCode(readAndSetFieldsCode),
        Object.class,
        MemoryBuffer.class,
        BUFFER_NAME,
        Object.class,
        ROOT_OBJECT_NAME);

    registerJITNotifyCallback();
    ctx.addConstructor(constructorCode, Fory.class, FORY_NAME, Class.class, POJO_CLASS_TYPE_NAME);
    return ctx.genCode();
  }

  @Override
  protected void addCommonImports() {
    super.addCommonImports();
    ctx.addImport(GeneratedMetaSharedLayerSerializer.class);
  }

  @Override
  protected Expression setFieldValue(
      Expression bean, Descriptor descriptor, Expression value) {
    if (descriptor.getField() == null) {
      FieldConverter<?> converter = descriptor.getFieldConverter();
      if (converter != null) {
        Descriptor convertedDescriptor =
            new org.apache.fory.type.DescriptorBuilder(descriptor)
                .field(converter.getField())
                .type(converter.getField().getType())
                .typeRef(TypeRef.of(converter.getField().getType()))
                .build();
        StaticInvoke convertedValue =
            new StaticInvoke(
                converter.getClass(),
                "convertFrom",
                TypeRef.of(converter.getField().getType()),
                value);
        return super.setFieldValue(bean, convertedDescriptor, convertedValue);
      }
      return new StaticInvoke(ExceptionUtils.class, "ignore", value);
    }
    return super.setFieldValue(bean, descriptor, value);
  }

  @Override
  protected Expression buildComponentsArray() {
    return buildDefaultComponentsArray();
  }

  @Override
  protected boolean useCollectionSerialization(Class<?> type) {
    if (!super.useCollectionSerialization(type)) {
      return false;
    }
    TypeInfo typeInfo = typeResolver(r -> r.getTypeInfo(type, false));
    if (typeInfo == null || typeInfo.getSerializer() == null) {
      return false;
    }
    return CollectionLikeSerializer.class.isAssignableFrom(typeInfo.getSerializer().getClass());
  }

  @Override
  protected boolean useMapSerialization(Class<?> type) {
    if (!super.useMapSerialization(type)) {
      return false;
    }
    TypeInfo typeInfo = typeResolver(r -> r.getTypeInfo(type, false));
    if (typeInfo == null || typeInfo.getSerializer() == null) {
      return false;
    }
    return MapLikeSerializer.class.isAssignableFrom(typeInfo.getSerializer().getClass());
  }

  @Override
  protected TypeRef<?> getSerializerType(Class<?> objType) {
    if (useCollectionSerialization(objType)) {
      return TypeRef.of(CollectionLikeSerializer.class);
    } else if (useMapSerialization(objType)) {
      return TypeRef.of(MapLikeSerializer.class);
    }
    return TypeRef.of(Serializer.class);
  }

  private Expression buildPutFieldNamesExpr() {
    Expression[] elements = new Expression[allFieldInfos.length];
    for (int i = 0; i < allFieldInfos.length; i++) {
      elements[i] = Literal.ofString(getPutFieldName(allFieldInfos[i]));
    }
    return new NewArray(TypeRef.of(String[].class), elements);
  }

  private Expression buildPutFieldTypesExpr() {
    Expression[] elements = new Expression[allFieldInfos.length];
    for (int i = 0; i < allFieldInfos.length; i++) {
      elements[i] = Literal.ofClass(getPutFieldType(allFieldInfos[i]));
    }
    return new NewArray(TypeRef.of(Class[].class), elements);
  }

  private String genVoidExpressionCode(Expression expression) {
    org.apache.fory.codegen.Code.ExprCode exprCode = expression.genCode(ctx);
    return StringUtils.isBlank(exprCode.code()) ? "" : exprCode.code();
  }

  private String genDecodeMethodCode(Expression expression) {
    org.apache.fory.codegen.Code.ExprCode exprCode = expression.genCode(ctx);
    return exprCode.code();
  }

  private static String getPutFieldName(SerializationFieldInfo fieldInfo) {
    return fieldInfo.fieldAccessor != null
        ? fieldInfo.fieldAccessor.getField().getName()
        : fieldInfo.descriptor.getName();
  }

  private Class<?> getPutFieldType(SerializationFieldInfo fieldInfo) {
    Class<?> fieldType =
        fieldInfo.fieldAccessor != null
            ? fieldInfo.fieldAccessor.getField().getType()
            : fieldInfo.descriptor.getRawType();
    if (!fieldType.isPrimitive() && !sourcePublicAccessible(fieldType)) {
      return Object.class;
    }
    return fieldType;
  }

  private static boolean requiresFallbackHotPath(SerializationFieldInfo[] fieldInfos) {
    for (SerializationFieldInfo fieldInfo : fieldInfos) {
      Descriptor descriptor = fieldInfo.descriptor;
      if (descriptor.getField() == null || descriptor.getFieldConverter() != null) {
        return true;
      }
    }
    return false;
  }
}
