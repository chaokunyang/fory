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

import static org.apache.fory.builder.Generated.GeneratedMetaSharedSerializer.SERIALIZER_FIELD_NAME;
import static org.apache.fory.type.TypeUtils.PRIMITIVE_VOID_TYPE;

import java.util.Collection;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.fory.Fory;
import org.apache.fory.builder.Generated.GeneratedMetaSharedSerializer;
import org.apache.fory.codegen.CodeGenerator;
import org.apache.fory.codegen.Expression;
import org.apache.fory.codegen.Expression.Literal;
import org.apache.fory.codegen.Expression.StaticInvoke;
import org.apache.fory.config.CompatibleMode;
import org.apache.fory.config.ForyBuilder;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.memory.Platform;
import org.apache.fory.meta.ClassDef;
import org.apache.fory.reflect.TypeRef;
import org.apache.fory.resolver.ClassResolver;
import org.apache.fory.serializer.CodegenSerializer;
import org.apache.fory.serializer.MetaSharedSerializer;
import org.apache.fory.serializer.ObjectSerializer;
import org.apache.fory.serializer.Serializer;
import org.apache.fory.serializer.Serializers;
import org.apache.fory.type.Descriptor;
import org.apache.fory.type.DescriptorGrouper;
import org.apache.fory.util.ExceptionUtils;
import org.apache.fory.util.GraalvmSupport;
import org.apache.fory.util.Preconditions;
import org.apache.fory.util.ScalaCaseClassUtils;
import org.apache.fory.util.StringUtils;
import org.apache.fory.util.record.RecordComponent;
import org.apache.fory.util.record.RecordUtils;

/**
 * A meta-shared compatible deserializer builder based on {@link ClassDef}. This builder will
 * compare fields between {@link ClassDef} and class fields, then create serializer to read and
 * set/skip corresponding fields to support type forward/backward compatibility. Serializer are
 * forward to {@link ObjectCodecBuilder} for now. We can consolidate fields between peers to create
 * better serializers to serialize common fields between peers for efficiency.
 *
 * <p>With meta context share enabled and compatible mode, the {@link ObjectCodecBuilder} will take
 * all non-inner final types as non-final, so that fory can write class definition when write class
 * info for those types.
 *
 * @see CompatibleMode
 * @see ForyBuilder#withMetaShare
 * @see GeneratedMetaSharedSerializer
 * @see MetaSharedSerializer
 */
public class MetaSharedCodecBuilder extends ObjectCodecBuilder {
  private final ClassDef classDef;
  private final boolean isScalaCaseClass;
  private final ScalaCaseClassUtils.ScalaDefaultValueField[] scalaDefaultValueFields;

  public MetaSharedCodecBuilder(TypeRef<?> beanType, Fory fory, ClassDef classDef) {
    super(beanType, fory, GeneratedMetaSharedSerializer.class);
    Preconditions.checkArgument(
        !fory.getConfig().checkClassVersion(),
        "Class version check should be disabled when compatible mode is enabled.");
    this.classDef = classDef;
    Collection<Descriptor> descriptors =
        fory(
            f ->
                MetaSharedSerializer.consolidateFields(
                    f.isCrossLanguage() ? f.getXtypeResolver() : f.getClassResolver(),
                    beanClass,
                    classDef));
    DescriptorGrouper grouper = fory.getClassResolver().createDescriptorGrouper(descriptors, false);
    objectCodecOptimizer =
        new ObjectCodecOptimizer(beanClass, grouper, !fory.isBasicTypesRefIgnored(), ctx);
    
    // Check if this is a Scala case class and build default value fields
    this.isScalaCaseClass =
        fory.getConfig().isScalaOptimizationEnabled() && ScalaCaseClassUtils.isScalaCaseClass(beanClass);
    this.scalaDefaultValueFields =
        ScalaCaseClassUtils.buildScalaDefaultValueFields(
            fory, beanClass, grouper.getSortedDescriptors());
  }

  // Must be static to be shared across the whole process life.
  private static final Map<Long, Integer> idGenerator = new ConcurrentHashMap<>();

  @Override
  protected String codecSuffix() {
    // For every class def sent from different peer, if the class def are different, then
    // a new serializer needs being generated.
    Integer id = idGenerator.get(classDef.getId());
    if (id == null) {
      synchronized (idGenerator) {
        id = idGenerator.computeIfAbsent(classDef.getId(), k -> idGenerator.size());
      }
    }
    return "MetaShared" + id;
  }

  @Override
  public String genCode() {
    ctx.setPackage(CodeGenerator.getPackage(beanClass));
    String className = codecClassName(beanClass);
    ctx.setClassName(className);
    // don't addImport(beanClass), because user class may name collide.
    ctx.extendsClasses(ctx.type(parentSerializerClass));
    ctx.reserveName(POJO_CLASS_TYPE_NAME);
    ctx.reserveName(SERIALIZER_FIELD_NAME);
    ctx.addField(ctx.type(Fory.class), FORY_NAME);
    String constructorCode =
        StringUtils.format(
            ""
                + "super(${fory}, ${cls});\n"
                + "this.${fory} = ${fory};\n"
                + "${serializer} = ${builderClass}.setCodegenSerializer(${fory}, ${cls}, this);\n",
            "fory",
            FORY_NAME,
            "cls",
            POJO_CLASS_TYPE_NAME,
            "builderClass",
            MetaSharedCodecBuilder.class.getName(),
            "serializer",
            SERIALIZER_FIELD_NAME);
    ctx.clearExprState();
    Expression decodeExpr = buildDecodeExpression();
    String decodeCode = decodeExpr.genCode(ctx).code();
    decodeCode = ctx.optimizeMethodCode(decodeCode);
    ctx.overrideMethod("read", decodeCode, Object.class, MemoryBuffer.class, BUFFER_NAME);
    registerJITNotifyCallback();
    ctx.addConstructor(constructorCode, Fory.class, "fory", Class.class, POJO_CLASS_TYPE_NAME);
    return ctx.genCode();
  }

  @Override
  protected void addCommonImports() {
    super.addCommonImports();
    ctx.addImport(GeneratedMetaSharedSerializer.class);
    ctx.addImport(ScalaCaseClassUtils.class);
  }

  // Invoked by JIT.
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static Serializer setCodegenSerializer(
      Fory fory, Class<?> cls, GeneratedMetaSharedSerializer s) {
    if (GraalvmSupport.isGraalRuntime()) {
      return fory.getJITContext()
          .asyncVisitFory(f -> f.getClassResolver().getSerializer(s.getType()));
    }
    // This method hold jit lock, so create jit serializer async to avoid block serialization.
    Class serializerClass =
        fory.getJITContext()
            .registerSerializerJITCallback(
                () -> ObjectSerializer.class,
                () -> CodegenSerializer.loadCodegenSerializer(fory, s.getType()),
                c -> s.serializer = Serializers.newSerializer(fory, s.getType(), c));
    return Serializers.newSerializer(fory, cls, serializerClass);
  }

  @Override
  public Expression buildEncodeExpression() {
    throw new IllegalStateException("unreachable");
  }

  @Override
  protected Expression buildComponentsArray() {
    return buildDefaultComponentsArray();
  }

  protected Expression createRecord(SortedMap<Integer, Expression> recordComponents) {
    RecordComponent[] components = RecordUtils.getRecordComponents(beanClass);
    Object[] defaultValues = RecordUtils.buildRecordComponentDefaultValues(beanClass);
    for (int i = 0; i < defaultValues.length; i++) {
      if (!recordComponents.containsKey(i)) {
        Object defaultValue = defaultValues[i];
        assert components != null;
        RecordComponent component = components[i];
        recordComponents.put(i, new Literal(defaultValue, TypeRef.of(component.getType())));
      }
    }
    Expression[] params = recordComponents.values().toArray(new Expression[0]);
    return new Expression.NewInstance(beanType, params);
  }

  @Override
  protected Expression setFieldValue(Expression bean, Descriptor descriptor, Expression value) {
    if (descriptor.getField() == null) {
      // Field doesn't exist in current class, skip set this field value.
      // Note that the field value shouldn't be an inlined value, otherwise field value read may
      // be ignored.
      // Add an ignored call here to make expression type to void.
      return new Expression.StaticInvoke(ExceptionUtils.class, "ignore", value);
    }
    return super.setFieldValue(bean, descriptor, value);
  }

  @Override
  protected Expression newBean() {
    Expression bean = super.newBean();
    if (scalaDefaultValueFields.length == 0) {
      return bean;
    }
  
    Expression.ListExpression setDefaultsExpr = new Expression.ListExpression();
    setDefaultsExpr.add(bean);
    for (ScalaCaseClassUtils.ScalaDefaultValueField defaultField : scalaDefaultValueFields) {
      Object defaultValue = defaultField.getDefaultValue();
      short classId = defaultField.getClassId();
      
      Expression setDefaultExpr = createSetDefaultValueExpression(
          bean, defaultField.getFieldName(), defaultValue, classId);
      setDefaultsExpr.add(setDefaultExpr);
    }
      
    return setDefaultsExpr;
  }

  private Expression createSetDefaultValueExpression(
      Expression bean, String fieldName, Object defaultValue, short classId) {
    PlatformMethodInfo methodInfo = getPlatformMethodInfo(classId);
    return new StaticInvoke(
        Platform.class,
        methodInfo.methodName,
        PRIMITIVE_VOID_TYPE,
        bean,
        Literal.ofLong(getFieldOffset(fieldName)),  
        new Literal(defaultValue, methodInfo.type));
  }

  private static class PlatformMethodInfo {
    final String methodName;
    final TypeRef<?> type;
    
    PlatformMethodInfo(String methodName, TypeRef<?> type) {
      this.methodName = methodName;
      this.type = type;
    }
  }

  private PlatformMethodInfo getPlatformMethodInfo(short classId) {
    switch (classId) {
      case ClassResolver.PRIMITIVE_BOOLEAN_CLASS_ID:
      case ClassResolver.BOOLEAN_CLASS_ID:
        return new PlatformMethodInfo("putBoolean", TypeRef.of(boolean.class));
      case ClassResolver.PRIMITIVE_BYTE_CLASS_ID:
      case ClassResolver.BYTE_CLASS_ID:
        return new PlatformMethodInfo("putByte", TypeRef.of(byte.class));
      case ClassResolver.PRIMITIVE_CHAR_CLASS_ID:
      case ClassResolver.CHAR_CLASS_ID:
        return new PlatformMethodInfo("putChar", TypeRef.of(char.class));
      case ClassResolver.PRIMITIVE_SHORT_CLASS_ID:
      case ClassResolver.SHORT_CLASS_ID:
        return new PlatformMethodInfo("putShort", TypeRef.of(short.class));
      case ClassResolver.PRIMITIVE_INT_CLASS_ID:
      case ClassResolver.INTEGER_CLASS_ID:
        return new PlatformMethodInfo("putInt", TypeRef.of(int.class));
      case ClassResolver.PRIMITIVE_LONG_CLASS_ID:
      case ClassResolver.LONG_CLASS_ID:
        return new PlatformMethodInfo("putLong", TypeRef.of(long.class));
      case ClassResolver.PRIMITIVE_FLOAT_CLASS_ID:
      case ClassResolver.FLOAT_CLASS_ID:
        return new PlatformMethodInfo("putFloat", TypeRef.of(float.class));
      case ClassResolver.PRIMITIVE_DOUBLE_CLASS_ID:
      case ClassResolver.DOUBLE_CLASS_ID:
        return new PlatformMethodInfo("putDouble", TypeRef.of(double.class));
      default:
        return new PlatformMethodInfo("putObject", TypeRef.of(Object.class));
    }
  }

  private long getFieldOffset(String fieldName) {
    try {
      java.lang.reflect.Field field = beanClass.getDeclaredField(fieldName);
      return Platform.objectFieldOffset(field);
    } catch (NoSuchFieldException e) {
      // Field not found, return 0 (this should not happen in practice)
      return 0;
    }
  }
}
