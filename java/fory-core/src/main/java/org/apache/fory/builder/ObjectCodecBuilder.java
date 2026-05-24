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

import static org.apache.fory.codegen.Code.LiteralValue.FalseLiteral;
import static org.apache.fory.codegen.Expression.Invoke.inlineInvoke;
import static org.apache.fory.codegen.ExpressionUtils.add;
import static org.apache.fory.codegen.ExpressionUtils.cast;
import static org.apache.fory.collection.Collections.ofHashSet;
import static org.apache.fory.type.TypeUtils.OBJECT_ARRAY_TYPE;
import static org.apache.fory.type.TypeUtils.OBJECT_TYPE;
import static org.apache.fory.type.TypeUtils.PRIMITIVE_BOOLEAN_TYPE;
import static org.apache.fory.type.TypeUtils.PRIMITIVE_BYTE_ARRAY_TYPE;
import static org.apache.fory.type.TypeUtils.PRIMITIVE_BYTE_TYPE;
import static org.apache.fory.type.TypeUtils.PRIMITIVE_CHAR_TYPE;
import static org.apache.fory.type.TypeUtils.PRIMITIVE_DOUBLE_TYPE;
import static org.apache.fory.type.TypeUtils.PRIMITIVE_FLOAT_TYPE;
import static org.apache.fory.type.TypeUtils.PRIMITIVE_INT_TYPE;
import static org.apache.fory.type.TypeUtils.PRIMITIVE_LONG_TYPE;
import static org.apache.fory.type.TypeUtils.PRIMITIVE_SHORT_TYPE;
import static org.apache.fory.type.TypeUtils.PRIMITIVE_VOID_TYPE;
import static org.apache.fory.type.TypeUtils.SHORT_TYPE;
import static org.apache.fory.type.TypeUtils.getRawType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import org.apache.fory.Fory;
import org.apache.fory.codegen.Code;
import org.apache.fory.codegen.CodegenContext;
import org.apache.fory.codegen.Expression;
import org.apache.fory.codegen.Expression.Cast;
import org.apache.fory.codegen.Expression.Inlineable;
import org.apache.fory.codegen.Expression.Invoke;
import org.apache.fory.codegen.Expression.ListExpression;
import org.apache.fory.codegen.Expression.Literal;
import org.apache.fory.codegen.Expression.NewInstance;
import org.apache.fory.codegen.Expression.Reference;
import org.apache.fory.codegen.Expression.ReplaceStub;
import org.apache.fory.codegen.Expression.StaticInvoke;
import org.apache.fory.codegen.ExpressionVisitor;
import org.apache.fory.logging.Logger;
import org.apache.fory.logging.LoggerFactory;
import org.apache.fory.meta.TypeDef;
import org.apache.fory.platform.JdkVersion;
import org.apache.fory.reflect.ObjectCreator;
import org.apache.fory.reflect.ObjectCreators;
import org.apache.fory.reflect.TypeRef;
import org.apache.fory.serializer.AbstractObjectSerializer;
import org.apache.fory.serializer.ObjectSerializer;
import org.apache.fory.type.BFloat16;
import org.apache.fory.type.Descriptor;
import org.apache.fory.type.DescriptorGrouper;
import org.apache.fory.type.DispatchId;
import org.apache.fory.type.Float16;
import org.apache.fory.type.TypeUtils;
import org.apache.fory.type.Types;
import org.apache.fory.util.StringUtils;
import org.apache.fory.util.function.SerializableSupplier;
import org.apache.fory.util.record.RecordUtils;

/**
 * Generate sequential read/write code for java serialization to speed up performance. It also
 * reduces space overhead introduced by aligning. Codegen only for time-consuming field, others
 * delegate to fory.
 *
 * <p>In order to improve jit-compile and inline, serialization code should be spilt groups to avoid
 * huge/big methods.
 *
 * <p>With meta context share enabled and compatible mode, this serializer will take all non-inner
 * final types as non-final, so that fory can write class definition when write class info for those
 * types.
 *
 * @see ObjectCodecOptimizer for code stats and split heuristics.
 */
public class ObjectCodecBuilder extends BaseObjectCodecBuilder {
  private static final Logger LOG = LoggerFactory.getLogger(ObjectCodecBuilder.class);

  private final Literal classVersionHash;
  protected ObjectCodecOptimizer objectCodecOptimizer;
  protected Map<String, Integer> recordReversedMapping;
  protected Map<Descriptor, Integer> fieldIndexes;
  protected int[] constructorFieldIndexes;
  protected boolean[] constructorFieldMask;
  protected Class<?>[] constructorFieldTypes;

  public ObjectCodecBuilder(Class<?> beanClass, Fory fory) {
    super(TypeRef.of(beanClass), fory, Generated.GeneratedObjectSerializer.class);
    Collection<Descriptor> descriptors;
    DescriptorGrouper grouper;
    boolean shareMeta = fory.getConfig().isMetaShareEnabled();
    if (shareMeta) {
      TypeDef typeDef = typeResolver(r -> r.getTypeDef(beanClass, true));
      descriptors = typeResolver(r -> typeDef.getDescriptors(r, beanClass));
      grouper = typeResolver(r -> r.createDescriptorGrouper(typeDef, beanClass));
    } else {
      grouper = typeResolver(r -> r.getFieldDescriptorGrouper(beanClass, true, false));
      descriptors = grouper.getSortedDescriptors();
    }
    if (org.apache.fory.util.Utils.DEBUG_OUTPUT_ENABLED) {
      LOG.info(
          "========== {} sorted descriptors for {} ==========",
          descriptors.size(),
          beanClass.getSimpleName());
      List<Descriptor> sortedDescriptors = grouper.getSortedDescriptors();
      for (Descriptor d : sortedDescriptors) {
        LOG.info(
            "  {} -> {}, ref {}, nullable {}",
            StringUtils.toSnakeCase(d.getName()),
            d.getTypeName(),
            d.isTrackingRef(),
            d.isNullable());
      }
    }
    classVersionHash =
        typeResolver.checkClassVersion()
            ? new Literal(
                ObjectSerializer.computeStructHash(typeResolver, grouper), PRIMITIVE_INT_TYPE)
            : null;
    objectCodecOptimizer = new ObjectCodecOptimizer(beanClass, grouper, false, ctx);
    if (isRecord) {
      if (!recordCtrAccessible) {
        buildRecordComponentDefaultValues();
      }
      recordReversedMapping = RecordUtils.buildFieldToComponentMapping(beanClass);
    } else {
      initConstructorFields(grouper.getSortedDescriptors(), true);
    }
  }

  protected ObjectCodecBuilder(TypeRef<?> beanType, Fory fory, Class<?> superSerializerClass) {
    super(beanType, fory, superSerializerClass);
    this.classVersionHash = null;
    if (isRecord) {
      if (!recordCtrAccessible) {
        buildRecordComponentDefaultValues();
      }
      recordReversedMapping = RecordUtils.buildFieldToComponentMapping(beanClass);
    }
  }

  protected final void initConstructorFields(
      List<Descriptor> sortedDescriptors, boolean allowMissingNonFinal) {
    initConstructorFields(sortedDescriptors, allowMissingNonFinal, null);
  }

  protected final void initConstructorFields(
      List<Descriptor> sortedDescriptors, boolean allowMissingNonFinal, String[] defaultFields) {
    initConstructorFields(sortedDescriptors, allowMissingNonFinal, defaultFields, null);
  }

  protected final void initConstructorFields(
      List<Descriptor> sortedDescriptors,
      boolean allowMissingNonFinal,
      String[] defaultFields,
      Class<?>[] defaultDeclaringClasses) {
    ObjectCreator<?> objectCreator = ObjectCreators.getObjectCreator(beanClass);
    if (!objectCreator.hasConstructorFields()) {
      return;
    }
    fieldIndexes = buildFieldIndexes(sortedDescriptors);
    constructorFieldTypes = objectCreator.getConstructorFieldTypes();
    constructorFieldIndexes =
        buildConstructorFieldIndexes(
            sortedDescriptors,
            objectCreator,
            allowMissingNonFinal,
            defaultFields,
            defaultDeclaringClasses);
    constructorFieldMask = buildConstructorFieldMask(sortedDescriptors.size());
  }

  private static Map<Descriptor, Integer> buildFieldIndexes(List<Descriptor> descriptors) {
    Map<Descriptor, Integer> indexes = new IdentityHashMap<>();
    for (int i = 0; i < descriptors.size(); i++) {
      indexes.put(descriptors.get(i), i);
    }
    return indexes;
  }

  private int[] buildConstructorFieldIndexes(
      List<Descriptor> descriptors,
      ObjectCreator<?> objectCreator,
      boolean allowMissingNonFinal,
      String[] defaultFields,
      Class<?>[] defaultDeclaringClasses) {
    String[] names = objectCreator.getConstructorFieldNames();
    Class<?>[] declaringClasses = objectCreator.getConstructorFieldDeclaringClasses();
    boolean[] finalFields = objectCreator.getConstructorFieldFinal();
    int[] indexes = new int[names.length];
    for (int i = 0; i < names.length; i++) {
      Class<?> declaringClass = declaringClasses == null ? null : declaringClasses[i];
      boolean allowMissing =
          (allowMissingNonFinal && !finalFields[i])
              || contains(defaultFields, defaultDeclaringClasses, names[i], declaringClass);
      indexes[i] = constructorFieldIndex(descriptors, declaringClass, names[i], allowMissing);
    }
    return indexes;
  }

  private static boolean contains(
      String[] values, Class<?>[] declaringClasses, String value, Class<?> declaringClass) {
    if (values == null) {
      return false;
    }
    for (int i = 0; i < values.length; i++) {
      if (values[i].equals(value)
          && (declaringClasses == null
              || i >= declaringClasses.length
              || declaringClasses[i] == null
              || declaringClasses[i] == declaringClass)) {
        return true;
      }
    }
    return false;
  }

  private int constructorFieldIndex(
      List<Descriptor> descriptors,
      Class<?> declaringClass,
      String fieldName,
      boolean allowMissing) {
    int index = -1;
    for (int i = 0; i < descriptors.size(); i++) {
      Descriptor descriptor = descriptors.get(i);
      if (!descriptor.getName().equals(fieldName)
          || (declaringClass != null
              && (descriptor.getField() == null
                  || descriptor.getField().getDeclaringClass() != declaringClass))) {
        continue;
      }
      if (index >= 0) {
        throw new IllegalStateException(
            "Constructor field " + fieldName + " is ambiguous for " + beanClass);
      }
      index = i;
    }
    if (index < 0) {
      if (allowMissing) {
        return -1;
      }
      throw new IllegalStateException(
          "Constructor field " + fieldName + " is not serialized for " + beanClass);
    }
    return index;
  }

  private boolean[] buildConstructorFieldMask(int size) {
    boolean[] mask = new boolean[size];
    for (int index : constructorFieldIndexes) {
      if (index >= 0) {
        mask[index] = true;
      }
    }
    return mask;
  }

  @Override
  protected String codecSuffix() {
    return "";
  }

  @Override
  protected void addCommonImports() {
    super.addCommonImports();
    ctx.addImport(Generated.GeneratedObjectSerializer.class);
  }

  /**
   * Return an expression that serialize java bean of type {@link CodecBuilder#beanClass} to buffer.
   */
  @Override
  public Expression buildEncodeExpression() {
    Reference inputObject = new Reference(ROOT_OBJECT_NAME, OBJECT_TYPE, false);
    Reference buffer = new Reference(BUFFER_NAME, bufferTypeRef, false);

    ListExpression expressions = new ListExpression();
    Expression bean = tryCastIfPublic(inputObject, beanType, ctx.newName(beanClass));
    expressions.add(bean);
    if (typeResolver.checkClassVersion()) {
      expressions.add(new Invoke(buffer, "writeInt32", classVersionHash));
    }
    expressions.addAll(serializePrimitives(bean, buffer, objectCodecOptimizer.primitiveGroups));
    int numGroups = getNumGroups(objectCodecOptimizer);
    addGroupExpressions(
        objectCodecOptimizer.boxedWriteGroups, numGroups, expressions, bean, buffer);
    addGroupExpressions(
        objectCodecOptimizer.nonPrimitiveWriteGroups, numGroups, expressions, bean, buffer);
    return expressions;
  }

  private void addGroupExpressions(
      List<List<Descriptor>> writeGroup,
      int numGroups,
      ListExpression expressions,
      Expression bean,
      Reference buffer) {
    for (List<Descriptor> group : writeGroup) {
      if (group.isEmpty()) {
        continue;
      }
      boolean inline = hasFewFields() || (group.size() == 1 && numGroups < 10);
      expressions.add(serializeGroup(group, bean, buffer, inline));
    }
  }

  protected boolean hasFewFields() {
    return objectCodecOptimizer.descriptorGrouper.getNumDescriptors() < 6;
  }

  protected int getNumGroups(ObjectCodecOptimizer objectCodecOptimizer) {
    return objectCodecOptimizer.boxedWriteGroups.size()
        + objectCodecOptimizer.nonPrimitiveWriteGroups.size();
  }

  private Expression serializeGroup(
      List<Descriptor> group, Expression bean, Expression buffer, boolean inline) {
    SerializableSupplier<Expression> expressionSupplier =
        () -> {
          ListExpression groupExpressions = new ListExpression();
          for (Descriptor d : group) {
            // `bean` will be replaced by `Reference` to cut-off expr dependency.
            Expression fieldValue = getFieldValue(bean, d);
            walkPath.add(d.getDeclaringClass() + d.getName());
            Expression fieldExpr = serializeField(fieldValue, buffer, d);
            walkPath.removeLast();
            groupExpressions.add(fieldExpr);
          }
          return groupExpressions;
        };
    if (inline) {
      return expressionSupplier.get();
    }
    return objectCodecOptimizer.invokeGenerated(
        writeCutPoints(bean, buffer), expressionSupplier.get(), "writeFields");
  }

  /**
   * Return a list of expressions that serialize all primitive fields. This can reduce unnecessary
   * grow call and increment writerIndex in writeXXX.
   */
  private List<Expression> serializePrimitives(
      Expression bean, Expression buffer, List<List<Descriptor>> primitiveGroups) {
    int totalSize = getTotalSizeOfPrimitives(primitiveGroups);
    if (totalSize == 0) {
      return new ArrayList<>();
    }
    if (config.compressInt() || config.compressLong()) {
      return serializePrimitivesCompressed(bean, buffer, primitiveGroups, totalSize);
    } else {
      return serializePrimitivesUnCompressed(bean, buffer, primitiveGroups, totalSize);
    }
  }

  protected int getNumPrimitiveFields(List<List<Descriptor>> primitiveGroups) {
    return primitiveGroups.stream().mapToInt(List::size).sum();
  }

  private List<Expression> serializePrimitivesUnCompressed(
      Expression bean, Expression buffer, List<List<Descriptor>> primitiveGroups, int totalSize) {
    if (JdkVersion.MAJOR_VERSION >= 25) {
      return serializeRawPrimitivesIndexed(bean, buffer, primitiveGroups, totalSize);
    }
    List<Expression> expressions = new ArrayList<>();
    int numPrimitiveFields = getNumPrimitiveFields(primitiveGroups);
    Literal totalSizeLiteral = new Literal(totalSize, PRIMITIVE_INT_TYPE);
    // After this grow, following writes can be unsafe without checks.
    expressions.add(new Invoke(buffer, "grow", totalSizeLiteral));
    // Must grow first, otherwise may get invalid address.
    Expression base = new Invoke(buffer, "getHeapMemory", "base", PRIMITIVE_BYTE_ARRAY_TYPE);
    Expression writerAddr =
        new Invoke(buffer, "_unsafeWriterAddress", "writerAddr", PRIMITIVE_LONG_TYPE);
    expressions.add(base);
    expressions.add(writerAddr);
    int acc = 0;
    for (List<Descriptor> group : primitiveGroups) {
      ListExpression groupExpressions = new ListExpression();
      // use Reference to cut-off expr dependency.
      for (Descriptor descriptor : group) {
        int dispatchId = getNumericDescriptorDispatchId(descriptor);
        // `bean` will be replaced by `Reference` to cut-off expr dependency.
        Expression fieldValue = getFieldValue(bean, descriptor);
        if (fieldValue instanceof Inlineable) {
          ((Inlineable) fieldValue).inline();
        }
        if (dispatchId == DispatchId.BOOL) {
          groupExpressions.add(unsafePutBoolean(base, getWriterPos(writerAddr, acc), fieldValue));
          acc += 1;
        } else if (dispatchId == DispatchId.INT8) {
          groupExpressions.add(unsafePut(base, getWriterPos(writerAddr, acc), fieldValue));
          acc += 1;
        } else if (dispatchId == DispatchId.UINT8) {
          groupExpressions.add(
              unsafePut(
                  base, getWriterPos(writerAddr, acc), primitiveByteValue(fieldValue, descriptor)));
          acc += 1;
        } else if (dispatchId == DispatchId.CHAR) {
          groupExpressions.add(unsafePutChar(base, getWriterPos(writerAddr, acc), fieldValue));
          acc += 2;
        } else if (dispatchId == DispatchId.INT16) {
          groupExpressions.add(unsafePutShort(base, getWriterPos(writerAddr, acc), fieldValue));
          acc += 2;
        } else if (dispatchId == DispatchId.UINT16) {
          groupExpressions.add(
              unsafePutShort(
                  base,
                  getWriterPos(writerAddr, acc),
                  primitiveShortValue(fieldValue, descriptor)));
          acc += 2;
        } else if (dispatchId == DispatchId.FLOAT16 || dispatchId == DispatchId.BFLOAT16) {
          groupExpressions.add(
              unsafePutShort(
                  base,
                  getWriterPos(writerAddr, acc),
                  new Invoke(fieldValue, "toBits", SHORT_TYPE)));
          acc += 2;
        } else if (dispatchId == DispatchId.INT32) {
          groupExpressions.add(unsafePutInt(base, getWriterPos(writerAddr, acc), fieldValue));
          acc += 4;
        } else if (dispatchId == DispatchId.UINT32) {
          groupExpressions.add(
              unsafePutInt(
                  base, getWriterPos(writerAddr, acc), primitiveIntValue(fieldValue, descriptor)));
          acc += 4;
        } else if (dispatchId == DispatchId.INT64 || dispatchId == DispatchId.UINT64) {
          groupExpressions.add(unsafePutLong(base, getWriterPos(writerAddr, acc), fieldValue));
          acc += 8;
        } else if (dispatchId == DispatchId.FLOAT32) {
          groupExpressions.add(unsafePutFloat(base, getWriterPos(writerAddr, acc), fieldValue));
          acc += 4;
        } else if (dispatchId == DispatchId.FLOAT64) {
          groupExpressions.add(unsafePutDouble(base, getWriterPos(writerAddr, acc), fieldValue));
          acc += 8;
        } else {
          throw new IllegalStateException("Unsupported dispatchId: " + dispatchId);
        }
      }
      if (hasFewFields() || numPrimitiveFields < 4) {
        expressions.add(groupExpressions);
      } else {
        expressions.add(
            objectCodecOptimizer.invokeGenerated(
                ofHashSet(bean, base, writerAddr), groupExpressions, "writeFields"));
      }
    }
    Expression increaseWriterIndex =
        new Invoke(
            buffer,
            "_increaseWriterIndexUnsafe",
            new Literal(totalSizeLiteral, PRIMITIVE_INT_TYPE));
    expressions.add(increaseWriterIndex);
    return expressions;
  }

  private List<Expression> serializePrimitivesCompressed(
      Expression bean, Expression buffer, List<List<Descriptor>> primitiveGroups, int totalSize) {
    if (JdkVersion.MAJOR_VERSION >= 25) {
      return serializeCompressedIndexed(bean, buffer, primitiveGroups, totalSize);
    }
    List<Expression> expressions = new ArrayList<>();
    // int/long may need extra one-byte for writing.
    int extraSize = 0;
    for (List<Descriptor> group : primitiveGroups) {
      for (Descriptor d : group) {
        int id = getNumericDescriptorDispatchId(d);
        if (id == DispatchId.INT32
            || id == DispatchId.VARINT32
            || id == DispatchId.VAR_UINT32
            || id == DispatchId.UINT32) {
          // varint may be written as 5bytes, use 8bytes for written as long to reduce cost.
          extraSize += 4;
        } else if (id == DispatchId.INT64
            || id == DispatchId.VARINT64
            || id == DispatchId.TAGGED_INT64
            || id == DispatchId.VAR_UINT64
            || id == DispatchId.TAGGED_UINT64
            || id == DispatchId.UINT64) {
          extraSize += 1; // long use 1~9 bytes.
        }
      }
    }
    int growSize = totalSize + extraSize;
    // After this grow, following writes can be unsafe without checks.
    expressions.add(new Invoke(buffer, "grow", Literal.ofInt(growSize)));
    // Must grow first, otherwise may get invalid address.
    Expression base = new Invoke(buffer, "getHeapMemory", "base", PRIMITIVE_BYTE_ARRAY_TYPE);
    expressions.add(base);
    int numPrimitiveFields = getNumPrimitiveFields(primitiveGroups);
    for (List<Descriptor> group : primitiveGroups) {
      ListExpression groupExpressions = new ListExpression();
      Expression writerAddr =
          new Invoke(buffer, "_unsafeWriterAddress", "writerAddr", PRIMITIVE_LONG_TYPE);
      // use Reference to cut-off expr dependency.
      int acc = 0;
      boolean compressStarted = false;
      for (Descriptor descriptor : group) {
        int dispatchId = getNumericDescriptorDispatchId(descriptor);
        // `bean` will be replaced by `Reference` to cut-off expr dependency.
        Expression fieldValue = getFieldValue(bean, descriptor);
        if (fieldValue instanceof Inlineable) {
          ((Inlineable) fieldValue).inline();
        }
        if (dispatchId == DispatchId.BOOL) {
          groupExpressions.add(unsafePutBoolean(base, getWriterPos(writerAddr, acc), fieldValue));
          acc += 1;
        } else if (dispatchId == DispatchId.INT8) {
          groupExpressions.add(unsafePut(base, getWriterPos(writerAddr, acc), fieldValue));
          acc += 1;
        } else if (dispatchId == DispatchId.UINT8) {
          groupExpressions.add(
              unsafePut(
                  base, getWriterPos(writerAddr, acc), primitiveByteValue(fieldValue, descriptor)));
          acc += 1;
        } else if (dispatchId == DispatchId.CHAR) {
          groupExpressions.add(unsafePutChar(base, getWriterPos(writerAddr, acc), fieldValue));
          acc += 2;
        } else if (dispatchId == DispatchId.INT16) {
          groupExpressions.add(unsafePutShort(base, getWriterPos(writerAddr, acc), fieldValue));
          acc += 2;
        } else if (dispatchId == DispatchId.UINT16) {
          groupExpressions.add(
              unsafePutShort(
                  base,
                  getWriterPos(writerAddr, acc),
                  primitiveShortValue(fieldValue, descriptor)));
          acc += 2;
        } else if (dispatchId == DispatchId.FLOAT16 || dispatchId == DispatchId.BFLOAT16) {
          groupExpressions.add(
              unsafePutShort(
                  base,
                  getWriterPos(writerAddr, acc),
                  new Invoke(fieldValue, "toBits", SHORT_TYPE)));
          acc += 2;
        } else if (dispatchId == DispatchId.FLOAT32) {
          groupExpressions.add(unsafePutFloat(base, getWriterPos(writerAddr, acc), fieldValue));
          acc += 4;
        } else if (dispatchId == DispatchId.FLOAT64) {
          groupExpressions.add(unsafePutDouble(base, getWriterPos(writerAddr, acc), fieldValue));
          acc += 8;
        } else if (dispatchId == DispatchId.INT32) {
          groupExpressions.add(unsafePutInt(base, getWriterPos(writerAddr, acc), fieldValue));
          acc += 4;
        } else if (dispatchId == DispatchId.UINT32) {
          groupExpressions.add(
              unsafePutInt(
                  base, getWriterPos(writerAddr, acc), primitiveIntValue(fieldValue, descriptor)));
          acc += 4;
        } else if (dispatchId == DispatchId.INT64 || dispatchId == DispatchId.UINT64) {
          groupExpressions.add(unsafePutLong(base, getWriterPos(writerAddr, acc), fieldValue));
          acc += 8;
        } else if (dispatchId == DispatchId.VARINT32) {
          if (!compressStarted) {
            addIncWriterIndexExpr(groupExpressions, buffer, acc);
            compressStarted = true;
          }
          groupExpressions.add(new Invoke(buffer, "_unsafeWriteVarInt32", fieldValue));
        } else if (dispatchId == DispatchId.VAR_UINT32) {
          if (!compressStarted) {
            addIncWriterIndexExpr(groupExpressions, buffer, acc);
            compressStarted = true;
          }
          groupExpressions.add(
              new Invoke(
                  buffer, "_unsafeWriteVarUInt32", primitiveIntValue(fieldValue, descriptor)));
        } else if (dispatchId == DispatchId.VARINT64) {
          if (!compressStarted) {
            addIncWriterIndexExpr(groupExpressions, buffer, acc);
            compressStarted = true;
          }
          groupExpressions.add(new Invoke(buffer, "writeVarInt64", fieldValue));
        } else if (dispatchId == DispatchId.TAGGED_INT64) {
          if (!compressStarted) {
            addIncWriterIndexExpr(groupExpressions, buffer, acc);
            compressStarted = true;
          }
          groupExpressions.add(new Invoke(buffer, "writeTaggedInt64", fieldValue));
        } else if (dispatchId == DispatchId.VAR_UINT64) {
          if (!compressStarted) {
            addIncWriterIndexExpr(groupExpressions, buffer, acc);
            compressStarted = true;
          }
          groupExpressions.add(new Invoke(buffer, "writeVarUInt64", fieldValue));
        } else if (dispatchId == DispatchId.TAGGED_UINT64) {
          if (!compressStarted) {
            addIncWriterIndexExpr(groupExpressions, buffer, acc);
            compressStarted = true;
          }
          groupExpressions.add(new Invoke(buffer, "writeTaggedUInt64", fieldValue));
        } else {
          throw new IllegalStateException("Unsupported dispatchId: " + dispatchId);
        }
      }
      if (!compressStarted) {
        // int/long are sorted in the last.
        addIncWriterIndexExpr(groupExpressions, buffer, acc);
      }
      if (hasFewFields() || numPrimitiveFields < 4) {
        expressions.add(groupExpressions);
      } else {
        expressions.add(
            objectCodecOptimizer.invokeGenerated(
                ofHashSet(bean, buffer, base), groupExpressions, "writeFields"));
      }
    }
    return expressions;
  }

  private List<Expression> serializeRawPrimitivesIndexed(
      Expression bean, Expression buffer, List<List<Descriptor>> primitiveGroups, int totalSize) {
    List<Expression> expressions = new ArrayList<>();
    int numPrimitiveFields = getNumPrimitiveFields(primitiveGroups);
    Literal totalSizeLiteral = new Literal(totalSize, PRIMITIVE_INT_TYPE);
    expressions.add(new Invoke(buffer, "grow", totalSizeLiteral));
    Expression writerIndex = new Invoke(buffer, "writerIndex", "writerIndex", PRIMITIVE_INT_TYPE);
    expressions.add(writerIndex);
    int acc = 0;
    for (List<Descriptor> group : primitiveGroups) {
      ListExpression groupExpressions = new ListExpression();
      for (Descriptor descriptor : group) {
        int dispatchId = getNumericDescriptorDispatchId(descriptor);
        Expression fieldValue = getFieldValue(bean, descriptor);
        if (fieldValue instanceof Inlineable) {
          ((Inlineable) fieldValue).inline();
        }
        if (dispatchId == DispatchId.BOOL) {
          groupExpressions.add(
              bufferPutBoolean(buffer, getBufferIndex(writerIndex, acc), fieldValue));
          acc += 1;
        } else if (dispatchId == DispatchId.INT8) {
          groupExpressions.add(bufferPutByte(buffer, getBufferIndex(writerIndex, acc), fieldValue));
          acc += 1;
        } else if (dispatchId == DispatchId.UINT8) {
          groupExpressions.add(
              bufferPutByte(
                  buffer,
                  getBufferIndex(writerIndex, acc),
                  primitiveByteValue(fieldValue, descriptor)));
          acc += 1;
        } else if (dispatchId == DispatchId.CHAR) {
          groupExpressions.add(bufferPutChar(buffer, getBufferIndex(writerIndex, acc), fieldValue));
          acc += 2;
        } else if (dispatchId == DispatchId.INT16) {
          groupExpressions.add(
              bufferPutInt16(buffer, getBufferIndex(writerIndex, acc), fieldValue));
          acc += 2;
        } else if (dispatchId == DispatchId.UINT16) {
          groupExpressions.add(
              bufferPutInt16(
                  buffer,
                  getBufferIndex(writerIndex, acc),
                  primitiveShortValue(fieldValue, descriptor)));
          acc += 2;
        } else if (dispatchId == DispatchId.FLOAT16 || dispatchId == DispatchId.BFLOAT16) {
          groupExpressions.add(
              bufferPutInt16(
                  buffer,
                  getBufferIndex(writerIndex, acc),
                  new Invoke(fieldValue, "toBits", SHORT_TYPE)));
          acc += 2;
        } else if (dispatchId == DispatchId.INT32) {
          groupExpressions.add(
              bufferPutInt32(buffer, getBufferIndex(writerIndex, acc), fieldValue));
          acc += 4;
        } else if (dispatchId == DispatchId.UINT32) {
          groupExpressions.add(
              bufferPutInt32(
                  buffer,
                  getBufferIndex(writerIndex, acc),
                  primitiveIntValue(fieldValue, descriptor)));
          acc += 4;
        } else if (dispatchId == DispatchId.INT64 || dispatchId == DispatchId.UINT64) {
          groupExpressions.add(
              bufferPutInt64(buffer, getBufferIndex(writerIndex, acc), fieldValue));
          acc += 8;
        } else if (dispatchId == DispatchId.FLOAT32) {
          groupExpressions.add(
              bufferPutFloat32(buffer, getBufferIndex(writerIndex, acc), fieldValue));
          acc += 4;
        } else if (dispatchId == DispatchId.FLOAT64) {
          groupExpressions.add(
              bufferPutFloat64(buffer, getBufferIndex(writerIndex, acc), fieldValue));
          acc += 8;
        } else {
          throw new IllegalStateException("Unsupported dispatchId: " + dispatchId);
        }
      }
      if (hasFewFields() || numPrimitiveFields < 4) {
        expressions.add(groupExpressions);
      } else {
        expressions.add(
            objectCodecOptimizer.invokeGenerated(
                ofHashSet(bean, buffer, writerIndex), groupExpressions, "writeFields"));
      }
    }
    Expression increaseWriterIndex =
        new Invoke(
            buffer,
            "_increaseWriterIndexUnsafe",
            new Literal(totalSizeLiteral, PRIMITIVE_INT_TYPE));
    expressions.add(increaseWriterIndex);
    return expressions;
  }

  private List<Expression> serializeCompressedIndexed(
      Expression bean, Expression buffer, List<List<Descriptor>> primitiveGroups, int totalSize) {
    List<Expression> expressions = new ArrayList<>();
    int extraSize = 0;
    for (List<Descriptor> group : primitiveGroups) {
      for (Descriptor d : group) {
        int id = getNumericDescriptorDispatchId(d);
        if (id == DispatchId.INT32
            || id == DispatchId.VARINT32
            || id == DispatchId.VAR_UINT32
            || id == DispatchId.UINT32) {
          extraSize += 4;
        } else if (id == DispatchId.INT64
            || id == DispatchId.VARINT64
            || id == DispatchId.TAGGED_INT64
            || id == DispatchId.VAR_UINT64
            || id == DispatchId.TAGGED_UINT64
            || id == DispatchId.UINT64) {
          extraSize += 1;
        }
      }
    }
    int growSize = totalSize + extraSize;
    expressions.add(new Invoke(buffer, "grow", Literal.ofInt(growSize)));
    int numPrimitiveFields = getNumPrimitiveFields(primitiveGroups);
    for (List<Descriptor> group : primitiveGroups) {
      ListExpression groupExpressions = new ListExpression();
      Expression writerIndex = new Invoke(buffer, "writerIndex", "writerIndex", PRIMITIVE_INT_TYPE);
      int acc = 0;
      boolean compressStarted = false;
      for (Descriptor descriptor : group) {
        int dispatchId = getNumericDescriptorDispatchId(descriptor);
        Expression fieldValue = getFieldValue(bean, descriptor);
        if (fieldValue instanceof Inlineable) {
          ((Inlineable) fieldValue).inline();
        }
        if (dispatchId == DispatchId.BOOL) {
          groupExpressions.add(
              bufferPutBoolean(buffer, getBufferIndex(writerIndex, acc), fieldValue));
          acc += 1;
        } else if (dispatchId == DispatchId.INT8) {
          groupExpressions.add(bufferPutByte(buffer, getBufferIndex(writerIndex, acc), fieldValue));
          acc += 1;
        } else if (dispatchId == DispatchId.UINT8) {
          groupExpressions.add(
              bufferPutByte(
                  buffer,
                  getBufferIndex(writerIndex, acc),
                  primitiveByteValue(fieldValue, descriptor)));
          acc += 1;
        } else if (dispatchId == DispatchId.CHAR) {
          groupExpressions.add(bufferPutChar(buffer, getBufferIndex(writerIndex, acc), fieldValue));
          acc += 2;
        } else if (dispatchId == DispatchId.INT16) {
          groupExpressions.add(
              bufferPutInt16(buffer, getBufferIndex(writerIndex, acc), fieldValue));
          acc += 2;
        } else if (dispatchId == DispatchId.UINT16) {
          groupExpressions.add(
              bufferPutInt16(
                  buffer,
                  getBufferIndex(writerIndex, acc),
                  primitiveShortValue(fieldValue, descriptor)));
          acc += 2;
        } else if (dispatchId == DispatchId.FLOAT16 || dispatchId == DispatchId.BFLOAT16) {
          groupExpressions.add(
              bufferPutInt16(
                  buffer,
                  getBufferIndex(writerIndex, acc),
                  new Invoke(fieldValue, "toBits", SHORT_TYPE)));
          acc += 2;
        } else if (dispatchId == DispatchId.FLOAT32) {
          groupExpressions.add(
              bufferPutFloat32(buffer, getBufferIndex(writerIndex, acc), fieldValue));
          acc += 4;
        } else if (dispatchId == DispatchId.FLOAT64) {
          groupExpressions.add(
              bufferPutFloat64(buffer, getBufferIndex(writerIndex, acc), fieldValue));
          acc += 8;
        } else if (dispatchId == DispatchId.INT32) {
          groupExpressions.add(
              bufferPutInt32(buffer, getBufferIndex(writerIndex, acc), fieldValue));
          acc += 4;
        } else if (dispatchId == DispatchId.UINT32) {
          groupExpressions.add(
              bufferPutInt32(
                  buffer,
                  getBufferIndex(writerIndex, acc),
                  primitiveIntValue(fieldValue, descriptor)));
          acc += 4;
        } else if (dispatchId == DispatchId.INT64 || dispatchId == DispatchId.UINT64) {
          groupExpressions.add(
              bufferPutInt64(buffer, getBufferIndex(writerIndex, acc), fieldValue));
          acc += 8;
        } else if (dispatchId == DispatchId.VARINT32) {
          if (!compressStarted) {
            addIncWriterIndexExpr(groupExpressions, buffer, acc);
            compressStarted = true;
          }
          groupExpressions.add(new Invoke(buffer, "_unsafeWriteVarInt32", fieldValue));
        } else if (dispatchId == DispatchId.VAR_UINT32) {
          if (!compressStarted) {
            addIncWriterIndexExpr(groupExpressions, buffer, acc);
            compressStarted = true;
          }
          groupExpressions.add(
              new Invoke(
                  buffer, "_unsafeWriteVarUInt32", primitiveIntValue(fieldValue, descriptor)));
        } else if (dispatchId == DispatchId.VARINT64) {
          if (!compressStarted) {
            addIncWriterIndexExpr(groupExpressions, buffer, acc);
            compressStarted = true;
          }
          groupExpressions.add(new Invoke(buffer, "writeVarInt64", fieldValue));
        } else if (dispatchId == DispatchId.TAGGED_INT64) {
          if (!compressStarted) {
            addIncWriterIndexExpr(groupExpressions, buffer, acc);
            compressStarted = true;
          }
          groupExpressions.add(new Invoke(buffer, "writeTaggedInt64", fieldValue));
        } else if (dispatchId == DispatchId.VAR_UINT64) {
          if (!compressStarted) {
            addIncWriterIndexExpr(groupExpressions, buffer, acc);
            compressStarted = true;
          }
          groupExpressions.add(new Invoke(buffer, "writeVarUInt64", fieldValue));
        } else if (dispatchId == DispatchId.TAGGED_UINT64) {
          if (!compressStarted) {
            addIncWriterIndexExpr(groupExpressions, buffer, acc);
            compressStarted = true;
          }
          groupExpressions.add(new Invoke(buffer, "writeTaggedUInt64", fieldValue));
        } else {
          throw new IllegalStateException("Unsupported dispatchId: " + dispatchId);
        }
      }
      if (!compressStarted) {
        addIncWriterIndexExpr(groupExpressions, buffer, acc);
      }
      if (hasFewFields() || numPrimitiveFields < 4) {
        expressions.add(groupExpressions);
      } else {
        expressions.add(
            objectCodecOptimizer.invokeGenerated(
                ofHashSet(bean, buffer, writerIndex), groupExpressions, "writeFields"));
      }
    }
    return expressions;
  }

  private Expression bufferPutByte(Expression buffer, Expression index, Expression value) {
    return new Invoke(buffer, "_unsafePutByte", index, value);
  }

  private Expression bufferPutBoolean(Expression buffer, Expression index, Expression value) {
    return new Invoke(buffer, "_unsafePutBoolean", index, value);
  }

  private Expression bufferPutChar(Expression buffer, Expression index, Expression value) {
    return new Invoke(buffer, "_unsafePutChar", index, value);
  }

  private Expression bufferPutInt16(Expression buffer, Expression index, Expression value) {
    return new Invoke(buffer, "_unsafePutInt16", index, value);
  }

  private Expression bufferPutInt32(Expression buffer, Expression index, Expression value) {
    return new Invoke(buffer, "_unsafePutInt32", index, value);
  }

  private Expression bufferPutInt64(Expression buffer, Expression index, Expression value) {
    return new Invoke(buffer, "_unsafePutInt64", index, value);
  }

  private Expression bufferPutFloat32(Expression buffer, Expression index, Expression value) {
    return bufferPutInt32(
        buffer,
        index,
        new StaticInvoke(Float.class, "floatToRawIntBits", PRIMITIVE_INT_TYPE, value));
  }

  private Expression bufferPutFloat64(Expression buffer, Expression index, Expression value) {
    return bufferPutInt64(
        buffer,
        index,
        new StaticInvoke(Double.class, "doubleToRawLongBits", PRIMITIVE_LONG_TYPE, value));
  }

  private Expression bufferGetByte(Expression buffer, Expression index) {
    return new Invoke(buffer, "_unsafeGetByte", PRIMITIVE_BYTE_TYPE, index);
  }

  private Expression bufferGetBoolean(Expression buffer, Expression index) {
    return new Invoke(buffer, "_unsafeGetBoolean", PRIMITIVE_BOOLEAN_TYPE, index);
  }

  private Expression bufferGetChar(Expression buffer, Expression index) {
    return new Invoke(buffer, "_unsafeGetChar", PRIMITIVE_CHAR_TYPE, index);
  }

  private Expression bufferGetInt16(Expression buffer, Expression index) {
    return new Invoke(buffer, "_unsafeGetInt16", PRIMITIVE_SHORT_TYPE, index);
  }

  private Expression bufferGetInt32(Expression buffer, Expression index) {
    return new Invoke(buffer, "_unsafeGetInt32", PRIMITIVE_INT_TYPE, index);
  }

  private Expression bufferGetInt64(Expression buffer, Expression index) {
    return new Invoke(buffer, "_unsafeGetInt64", PRIMITIVE_LONG_TYPE, index);
  }

  private Expression bufferGetFloat32(Expression buffer, Expression index) {
    return new StaticInvoke(
        Float.class, "intBitsToFloat", PRIMITIVE_FLOAT_TYPE, bufferGetInt32(buffer, index));
  }

  private Expression bufferGetFloat64(Expression buffer, Expression index) {
    return new StaticInvoke(
        Double.class, "longBitsToDouble", PRIMITIVE_DOUBLE_TYPE, bufferGetInt64(buffer, index));
  }

  private Expression primitiveByteValue(Expression fieldValue, Descriptor descriptor) {
    return fieldValue.type().isPrimitive()
        ? cast(fieldValue, PRIMITIVE_BYTE_TYPE)
        : new Invoke(boxedNumericValue(fieldValue, descriptor), "byteValue", PRIMITIVE_BYTE_TYPE);
  }

  private Expression primitiveShortValue(Expression fieldValue, Descriptor descriptor) {
    return fieldValue.type().isPrimitive()
        ? cast(fieldValue, PRIMITIVE_SHORT_TYPE)
        : new Invoke(boxedNumericValue(fieldValue, descriptor), "shortValue", PRIMITIVE_SHORT_TYPE);
  }

  private Expression primitiveIntValue(Expression fieldValue, Descriptor descriptor) {
    return fieldValue.type().isPrimitive()
        ? cast(fieldValue, PRIMITIVE_INT_TYPE)
        : new Invoke(boxedNumericValue(fieldValue, descriptor), "intValue", PRIMITIVE_INT_TYPE);
  }

  private Expression boxedNumericValue(Expression fieldValue, Descriptor descriptor) {
    return Number.class.isAssignableFrom(getRawType(fieldValue.type()))
        ? fieldValue
        : cast(fieldValue, descriptor.getTypeRef());
  }

  private void addIncWriterIndexExpr(ListExpression expressions, Expression buffer, int diff) {
    if (diff != 0) {
      expressions.add(new Invoke(buffer, "_increaseWriterIndexUnsafe", Literal.ofInt(diff)));
    }
  }

  private int getTotalSizeOfPrimitives(List<List<Descriptor>> primitiveGroups) {
    return primitiveGroups.stream()
        .flatMap(Collection::stream)
        .mapToInt(
            d -> {
              Class<?> rawType = d.getRawType();
              if (TypeUtils.isPrimitive(rawType) || TypeUtils.isBoxed(rawType)) {
                return TypeUtils.getSizeOfPrimitiveType(TypeUtils.unwrap(rawType));
              }
              return Types.getPrimitiveTypeSize(Types.getDescriptorTypeId(typeResolver, d));
            })
        .sum();
  }

  private Expression getWriterPos(Expression writerPos, long acc) {
    if (acc == 0) {
      return writerPos;
    }
    return add(writerPos, Literal.ofLong(acc));
  }

  public Expression buildDecodeExpression() {
    Reference buffer = new Reference(BUFFER_NAME, bufferTypeRef, false);
    ListExpression expressions = new ListExpression();
    if (typeResolver.checkClassVersion()) {
      expressions.add(checkClassVersion(buffer));
    }
    if (!isRecord && constructorFieldIndexes != null) {
      return buildConstructorDecodeExpression(buffer, expressions);
    }
    Expression bean;
    if (!isRecord) {
      if (constructorFieldIndexes == null) {
        bean = newBean();
        Expression referenceObject = invokeReadContext("reference", bean);
        expressions.add(bean);
        expressions.add(referenceObject);
      } else {
        bean = new FieldsArray(fieldIndexes.size());
        expressions.add(bean);
      }
    } else {
      if (recordCtrAccessible) {
        bean = new FieldsCollector();
      } else {
        bean = buildComponentsArray();
      }
    }
    expressions.addAll(deserializePrimitives(bean, buffer, objectCodecOptimizer.primitiveGroups));
    int numGroups = getNumGroups(objectCodecOptimizer);
    deserializeReadGroup(
        objectCodecOptimizer.boxedReadGroups, numGroups, expressions, bean, buffer);
    deserializeReadGroup(
        objectCodecOptimizer.nonPrimitiveReadGroups, numGroups, expressions, bean, buffer);
    if (isRecord) {
      if (recordCtrAccessible) {
        assert bean instanceof FieldsCollector;
        FieldsCollector collector = (FieldsCollector) bean;
        bean = createRecord(collector.recordValuesMap);
      } else {
        ObjectCreators.getObjectCreator(beanClass); // trigger cache and make error raised early
        bean =
            new Invoke(getObjectCreator(beanClass), "newInstanceWithArguments", OBJECT_TYPE, bean);
      }
    }
    expressions.add(new Expression.Return(bean));
    return expressions;
  }

  private Expression buildConstructorDecodeExpression(
      Reference buffer, ListExpression expressions) {
    FieldsArray fieldsArray = new FieldsArray(fieldIndexes.size());
    expressions.add(fieldsArray);
    expressions.add(
        new StaticInvoke(
            AbstractObjectSerializer.class,
            "beginConstructorRef",
            PRIMITIVE_VOID_TYPE,
            readContextRef()));
    List<Descriptor> bufferedNonConstructorFields = new ArrayList<>();
    int remainingConstructorFields = countConstructorFields();
    Expression bean = null;
    if (remainingConstructorFields == 0) {
      bean = createCtorBean(expressions, fieldsArray);
    }
    for (Descriptor descriptor : protocolDescriptors()) {
      int index = fieldIndexes.get(descriptor);
      walkPath.add(descriptor.getDeclaringClass() + descriptor.getName());
      if (constructorFieldMask[index]) {
        expressions.add(deserializeToFieldsArray(fieldsArray, buffer, descriptor, true));
        remainingConstructorFields--;
        if (remainingConstructorFields == 0) {
          bean = createCtorBean(expressions, fieldsArray);
          addBufferedFieldSetters(expressions, bean, fieldsArray, bufferedNonConstructorFields);
        }
      } else if (bean == null) {
        expressions.add(deserializeToFieldsArray(fieldsArray, buffer, descriptor, false));
        bufferedNonConstructorFields.add(descriptor);
      } else {
        expressions.add(deserializeToBean(bean, buffer, descriptor));
      }
      walkPath.removeLast();
    }
    expressions.add(
        new StaticInvoke(
            AbstractObjectSerializer.class,
            "endConstructorRef",
            PRIMITIVE_VOID_TYPE,
            readContextRef()));
    expressions.add(new Expression.Return(bean));
    return expressions;
  }

  private int countConstructorFields() {
    int count = 0;
    for (boolean constructorField : constructorFieldMask) {
      if (constructorField) {
        count++;
      }
    }
    return count;
  }

  private List<Descriptor> protocolDescriptors() {
    List<Descriptor> descriptors = new ArrayList<>();
    addDescriptors(descriptors, objectCodecOptimizer.primitiveGroups);
    addDescriptors(descriptors, objectCodecOptimizer.boxedReadGroups);
    addDescriptors(descriptors, objectCodecOptimizer.nonPrimitiveReadGroups);
    return descriptors;
  }

  private void addDescriptors(List<Descriptor> descriptors, List<List<Descriptor>> groups) {
    for (List<Descriptor> group : groups) {
      descriptors.addAll(group);
    }
  }

  private Expression createCtorBean(ListExpression expressions, FieldsArray fieldsArray) {
    Expression bean = createConstructorObject(fieldsArray);
    expressions.add(
        new StaticInvoke(
            AbstractObjectSerializer.class,
            "checkNoUnresolvedReadRef",
            PRIMITIVE_VOID_TYPE,
            readContextRef(),
            staticBeanClassExpr()));
    expressions.add(bean);
    expressions.add(
        new StaticInvoke(
            AbstractObjectSerializer.class,
            "referenceConstructorRef",
            PRIMITIVE_VOID_TYPE,
            readContextRef(),
            bean));
    postCreateConstructorObject(expressions, bean);
    return bean;
  }

  private Expression deserializeToFieldsArray(
      FieldsArray fieldsArray, Reference buffer, Descriptor descriptor, boolean constructorField) {
    TypeRef<?> castTypeRef =
        hasCompatibleCollectionArrayRead(descriptor)
            ? compatibleReadTargetTypeRef(descriptor)
            : descriptor.getTypeRef();
    return deserializeField(
        buffer,
        descriptor,
        expr -> {
          Expression value =
              constructorField ? tryInlineCast(expr, castTypeRef) : new Cast(expr, OBJECT_TYPE);
          value =
              new StaticInvoke(
                  AbstractObjectSerializer.class,
                  constructorField ? "ctorFieldValue" : "bufferFieldValue",
                  OBJECT_TYPE,
                  readContextRef(),
                  value,
                  staticBeanClassExpr());
          return setFieldValue(fieldsArray, descriptor, value);
        });
  }

  private Expression deserializeToBean(Expression bean, Reference buffer, Descriptor descriptor) {
    TypeRef<?> castTypeRef =
        hasCompatibleCollectionArrayRead(descriptor)
            ? compatibleReadTargetTypeRef(descriptor)
            : descriptor.getTypeRef();
    return deserializeField(
        buffer,
        descriptor,
        expr -> setFieldValue(bean, descriptor, tryInlineCast(expr, castTypeRef)));
  }

  protected void postCreateConstructorObject(ListExpression expressions, Expression bean) {}

  protected void deserializeReadGroup(
      List<List<Descriptor>> readGroups,
      int numGroups,
      ListExpression expressions,
      Expression bean,
      Reference buffer) {
    for (List<Descriptor> group : readGroups) {
      if (group.isEmpty()) {
        continue;
      }
      boolean inline = hasFewFields() || (group.size() == 1 && numGroups < 10);
      expressions.add(deserializeGroup(group, bean, buffer, inline));
    }
  }

  protected Expression buildComponentsArray() {
    return new Cast(
        new Invoke(recordComponentDefaultValues, "clone", OBJECT_TYPE), OBJECT_ARRAY_TYPE);
  }

  protected Expression createRecord(SortedMap<Integer, Expression> recordComponents) {
    Expression[] params = recordComponents.values().toArray(new Expression[0]);
    return new NewInstance(beanType, params);
  }

  protected Expression createConstructorObject(FieldsArray fieldValues) {
    Expression[] params = new Expression[constructorFieldIndexes.length];
    Expression[] directParams = new Expression[constructorFieldIndexes.length];
    for (int i = 0; i < constructorFieldIndexes.length; i++) {
      int index = constructorFieldIndexes[i];
      if (index < 0) {
        params[i] = defaultConstructorValue(i);
      } else {
        params[i] = fieldValue(fieldValues, index);
      }
      directParams[i] = tryInlineCast(params[i], TypeRef.of(constructorFieldTypes[i]));
    }
    ObjectCreator<?> objectCreator = ObjectCreators.getObjectCreator(beanClass);
    if (JdkVersion.MAJOR_VERSION >= 25
        && objectCreator.isOnlyPublicConstructor()
        && sourcePublicAccessible(beanClass)
        && constructorParamsAccessible()) {
      return new NewInstance(beanType, directParams);
    }
    Expression args = new Expression.NewArray(OBJECT_ARRAY_TYPE, params);
    Expression newInstance =
        new Invoke(getObjectCreator(beanClass), "newInstanceWithArguments", OBJECT_TYPE, args);
    return sourcePublicAccessible(beanClass) ? new Cast(newInstance, beanType) : newInstance;
  }

  protected Expression defaultConstructorValue(int constructorParameterIndex) {
    return new StaticInvoke(
        AbstractObjectSerializer.class,
        "defaultConstructorValue",
        OBJECT_TYPE,
        staticClassFieldExpr(
            constructorFieldTypes[constructorParameterIndex],
            "constructorFieldClass" + constructorParameterIndex + "_"));
  }

  private boolean constructorParamsAccessible() {
    for (Class<?> constructorFieldType : constructorFieldTypes) {
      if (!sourcePublicAccessible(constructorFieldType)) {
        return false;
      }
    }
    return true;
  }

  private void addNonConstructorFieldSetters(
      ListExpression expressions, Expression bean, FieldsArray fieldValues) {
    for (Descriptor descriptor : objectCodecOptimizer.descriptorGrouper.getSortedDescriptors()) {
      int index = fieldIndexes.get(descriptor);
      if (constructorFieldMask[index]) {
        continue;
      }
      TypeRef<?> castTypeRef =
          hasCompatibleCollectionArrayRead(descriptor)
              ? compatibleReadTargetTypeRef(descriptor)
              : descriptor.getTypeRef();
      Expression value =
          new StaticInvoke(
              AbstractObjectSerializer.class,
              "resolveBufferedValue",
              OBJECT_TYPE,
              fieldValue(fieldValues, index),
              bean);
      value = tryInlineCast(value, castTypeRef);
      expressions.add(setFieldValue(bean, descriptor, value));
    }
  }

  private void addBufferedFieldSetters(
      ListExpression expressions,
      Expression bean,
      FieldsArray fieldValues,
      List<Descriptor> descriptors) {
    for (Descriptor descriptor : descriptors) {
      int index = fieldIndexes.get(descriptor);
      TypeRef<?> castTypeRef =
          hasCompatibleCollectionArrayRead(descriptor)
              ? compatibleReadTargetTypeRef(descriptor)
              : descriptor.getTypeRef();
      Expression value =
          new StaticInvoke(
              AbstractObjectSerializer.class,
              "resolveBufferedValue",
              OBJECT_TYPE,
              fieldValue(fieldValues, index),
              bean);
      value = tryInlineCast(value, castTypeRef);
      expressions.add(setFieldValue(bean, descriptor, value));
    }
  }

  private Expression fieldValue(Expression fieldValues, int index) {
    return new StaticInvoke(
        AbstractObjectSerializer.class,
        "fieldValue",
        OBJECT_TYPE,
        fieldValues,
        Literal.ofInt(index));
  }

  private class FieldsCollector extends Expression.AbstractExpression {
    private final TreeMap<Integer, Expression> recordValuesMap = new TreeMap<>();

    protected FieldsCollector() {
      super(new Expression[0]);
    }

    @Override
    public TypeRef<?> type() {
      return beanType;
    }

    @Override
    public Code.ExprCode doGenCode(CodegenContext ctx) {
      return new Code.ExprCode(FalseLiteral, Code.variable(getRawType(beanType), "null"));
    }
  }

  protected class FieldsArray extends Expression.AbstractExpression {
    private final int size;
    private final String name;

    protected FieldsArray(int size) {
      super(new Expression[0]);
      this.size = size;
      name = ctx.newName("fieldValues");
    }

    @Override
    public TypeRef<?> type() {
      return OBJECT_ARRAY_TYPE;
    }

    @Override
    public Code.ExprCode doGenCode(CodegenContext ctx) {
      String code = ctx.type(Object[].class) + " " + name + " = new Object[" + size + "];";
      return new Code.ExprCode(code, FalseLiteral, Code.variable(Object[].class, name));
    }

    int fieldIndex(Descriptor descriptor) {
      return fieldIndexes.get(descriptor);
    }
  }

  @Override
  protected Expression setFieldValue(Expression bean, Descriptor d, Expression value) {
    if (bean instanceof FieldsArray) {
      return new Expression.AssignArrayElem(
          bean, value, Literal.ofInt(((FieldsArray) bean).fieldIndex(d)));
    }
    if (isRecord) {
      if (recordCtrAccessible) {
        if (value instanceof Inlineable) {
          ((Inlineable) value).inline(false);
        }
        int index = recordReversedMapping.get(d.getName());
        FieldsCollector collector = (FieldsCollector) bean;
        collector.recordValuesMap.put(index, value);
        return value;
      } else {
        int index = recordReversedMapping.get(d.getName());
        return new Expression.AssignArrayElem(bean, value, Literal.ofInt(index));
      }
    }
    return super.setFieldValue(bean, d, value);
  }

  protected Expression deserializeGroup(
      List<Descriptor> group, Expression bean, Expression buffer, boolean inline) {
    if (isRecord) {
      return deserializeGroupForRecord(group, bean, buffer);
    }
    SerializableSupplier<Expression> exprSupplier =
        () -> {
          ListExpression groupExpressions = new ListExpression();
          // use Reference to cut-off expr dependency.
          for (Descriptor d : group) {
            ExpressionVisitor.ExprHolder exprHolder = ExpressionVisitor.ExprHolder.of("bean", bean);
            walkPath.add(d.getDeclaringClass() + d.getName());
            TypeRef<?> castTypeRef =
                hasCompatibleCollectionArrayRead(d)
                    ? compatibleReadTargetTypeRef(d)
                    : d.getTypeRef();
            Expression action =
                deserializeField(
                    buffer,
                    d,
                    // `bean` will be replaced by `Reference` to cut-off expr
                    // dependency.
                    expr ->
                        setFieldValue(exprHolder.get("bean"), d, tryInlineCast(expr, castTypeRef)));
            walkPath.removeLast();
            if (needsGeneratedReadFieldMethod(d)) {
              action =
                  objectCodecOptimizer.invokeGenerated(
                      readCutPoints(bean, buffer), action, "readField");
            }
            groupExpressions.add(action);
          }
          return groupExpressions;
        };
    if (inline) {
      return exprSupplier.get();
    } else {
      return objectCodecOptimizer.invokeGenerated(
          readCutPoints(bean, buffer), exprSupplier.get(), "readFields");
    }
  }

  private boolean needsGeneratedReadFieldMethod(Descriptor descriptor) {
    return !hasFewFields()
        && !isMonomorphic(descriptor)
        && !useCollectionSerialization(descriptor)
        && !useMapSerialization(descriptor.getTypeRef());
  }

  protected Expression deserializeGroupForRecord(
      List<Descriptor> group, Expression bean, Expression buffer) {
    ListExpression groupExpressions = new ListExpression();
    // use Reference to cut-off expr dependency.
    for (Descriptor d : group) {
      TypeRef<?> castTypeRef =
          hasCompatibleCollectionArrayRead(d) ? compatibleReadTargetTypeRef(d) : d.getTypeRef();
      Expression value = deserializeField(buffer, d, expr -> expr);
      Expression action = setFieldValue(bean, d, tryInlineCast(value, castTypeRef));
      groupExpressions.add(action);
    }
    return groupExpressions;
  }

  private Expression checkClassVersion(Expression buffer) {
    return new StaticInvoke(
        ObjectSerializer.class,
        "checkClassVersion",
        PRIMITIVE_VOID_TYPE,
        false,
        beanClassExpr(),
        inlineInvoke(buffer, readIntFunc(), PRIMITIVE_INT_TYPE),
        Objects.requireNonNull(classVersionHash));
  }

  /**
   * Return a list of expressions that deserialize all primitive fields. This can reduce unnecessary
   * check call and increment readerIndex in writeXXX.
   */
  protected List<Expression> deserializePrimitives(
      Expression bean, Expression buffer, List<List<Descriptor>> primitiveGroups) {
    int totalSize = getTotalSizeOfPrimitives(primitiveGroups);
    if (totalSize == 0) {
      return new ArrayList<>();
    }
    if (config.compressInt() || config.compressLong()) {
      return deserializeCompressedPrimitives(bean, buffer, primitiveGroups);
    } else {
      return deserializeUnCompressedPrimitives(bean, buffer, primitiveGroups, totalSize);
    }
  }

  private List<Expression> deserializeUnCompressedPrimitives(
      Expression bean, Expression buffer, List<List<Descriptor>> primitiveGroups, int totalSize) {
    if (JdkVersion.MAJOR_VERSION >= 25) {
      return deserializeRawIndexed(bean, buffer, primitiveGroups, totalSize);
    }
    List<Expression> expressions = new ArrayList<>();
    int numPrimitiveFields = getNumPrimitiveFields(primitiveGroups);
    Literal totalSizeLiteral = Literal.ofInt(totalSize);
    // After this check, following read can be totally unsafe without checks
    expressions.add(new Invoke(buffer, "checkReadableBytes", totalSizeLiteral));
    Expression heapBuffer =
        new Invoke(buffer, "getHeapMemory", "heapBuffer", PRIMITIVE_BYTE_ARRAY_TYPE);
    Expression readerAddr =
        new Invoke(buffer, "getUnsafeReaderAddress", "readerAddr", PRIMITIVE_LONG_TYPE);
    expressions.add(heapBuffer);
    expressions.add(readerAddr);
    int acc = 0;
    for (List<Descriptor> group : primitiveGroups) {
      ListExpression groupExpressions = new ListExpression();
      for (Descriptor descriptor : group) {
        int dispatchId = getNumericDescriptorDispatchId(descriptor);
        Expression fieldValue;
        if (dispatchId == DispatchId.BOOL) {
          fieldValue = unsafeGetBoolean(heapBuffer, getReaderAddress(readerAddr, acc));
          acc += 1;
        } else if (dispatchId == DispatchId.INT8) {
          fieldValue = unsafeGet(heapBuffer, getReaderAddress(readerAddr, acc));
          acc += 1;
        } else if (dispatchId == DispatchId.UINT8) {
          fieldValue =
              new StaticInvoke(
                  Byte.class,
                  "toUnsignedInt",
                  descriptor.getTypeRef(),
                  unsafeGet(heapBuffer, getReaderAddress(readerAddr, acc)));
          acc += 1;
        } else if (dispatchId == DispatchId.CHAR) {
          fieldValue = unsafeGetChar(heapBuffer, getReaderAddress(readerAddr, acc));
          acc += 2;
        } else if (dispatchId == DispatchId.INT16) {
          fieldValue = unsafeGetShort(heapBuffer, getReaderAddress(readerAddr, acc));
          acc += 2;
        } else if (dispatchId == DispatchId.UINT16) {
          fieldValue =
              new StaticInvoke(
                  Short.class,
                  "toUnsignedInt",
                  descriptor.getTypeRef(),
                  unsafeGetShort(heapBuffer, getReaderAddress(readerAddr, acc)));
          acc += 2;
        } else if (dispatchId == DispatchId.FLOAT16) {
          fieldValue =
              new StaticInvoke(
                  Float16.class,
                  "fromBits",
                  TypeRef.of(Float16.class),
                  unsafeGetShort(heapBuffer, getReaderAddress(readerAddr, acc)));
          acc += 2;
        } else if (dispatchId == DispatchId.BFLOAT16) {
          fieldValue =
              new StaticInvoke(
                  BFloat16.class,
                  "fromBits",
                  TypeRef.of(BFloat16.class),
                  unsafeGetShort(heapBuffer, getReaderAddress(readerAddr, acc)));
          acc += 2;
        } else if (dispatchId == DispatchId.INT32) {
          fieldValue = unsafeGetInt(heapBuffer, getReaderAddress(readerAddr, acc));
          acc += 4;
        } else if (dispatchId == DispatchId.UINT32) {
          fieldValue =
              new StaticInvoke(
                  Integer.class,
                  "toUnsignedLong",
                  descriptor.getTypeRef(),
                  unsafeGetInt(heapBuffer, getReaderAddress(readerAddr, acc)));
          acc += 4;
        } else if (dispatchId == DispatchId.INT64 || dispatchId == DispatchId.UINT64) {
          fieldValue = unsafeGetLong(heapBuffer, getReaderAddress(readerAddr, acc));
          acc += 8;
        } else if (dispatchId == DispatchId.FLOAT32) {
          fieldValue = unsafeGetFloat(heapBuffer, getReaderAddress(readerAddr, acc));
          acc += 4;
        } else if (dispatchId == DispatchId.FLOAT64) {
          fieldValue = unsafeGetDouble(heapBuffer, getReaderAddress(readerAddr, acc));
          acc += 8;
        } else {
          throw new IllegalStateException("Unsupported dispatchId: " + dispatchId);
        }
        // `bean` will be replaced by `Reference` to cut-off expr dependency.
        groupExpressions.add(setFieldValue(bean, descriptor, fieldValue));
      }
      if (hasFewFields() || numPrimitiveFields < 4 || isRecord) {
        expressions.add(groupExpressions);
      } else {
        expressions.add(
            objectCodecOptimizer.invokeGenerated(
                ofHashSet(bean, heapBuffer, readerAddr), groupExpressions, "readFields"));
      }
    }
    Expression increaseReaderIndex =
        new Invoke(
            buffer, "increaseReaderIndex", new Literal(totalSizeLiteral, PRIMITIVE_INT_TYPE));
    expressions.add(increaseReaderIndex);
    return expressions;
  }

  private List<Expression> deserializeCompressedPrimitives(
      Expression bean, Expression buffer, List<List<Descriptor>> primitiveGroups) {
    if (JdkVersion.MAJOR_VERSION >= 25) {
      return deserializeCompressedIndexed(bean, buffer, primitiveGroups);
    }
    List<Expression> expressions = new ArrayList<>();
    int numPrimitiveFields = getNumPrimitiveFields(primitiveGroups);
    for (List<Descriptor> group : primitiveGroups) {
      // After this check, following read can be totally unsafe without checks.
      // checkReadableBytes first, `fillBuffer` may create a new heap buffer.
      ReplaceStub checkReadableBytesStub = new ReplaceStub();
      expressions.add(checkReadableBytesStub);
      Expression heapBuffer =
          new Invoke(buffer, "getHeapMemory", "heapBuffer", PRIMITIVE_BYTE_ARRAY_TYPE);
      expressions.add(heapBuffer);
      ListExpression groupExpressions = new ListExpression();
      Expression readerAddr =
          new Invoke(buffer, "getUnsafeReaderAddress", "readerAddr", PRIMITIVE_LONG_TYPE);
      int acc = 0;
      boolean compressStarted = false;
      for (Descriptor descriptor : group) {
        int dispatchId = getNumericDescriptorDispatchId(descriptor);
        Expression fieldValue;
        if (dispatchId == DispatchId.BOOL) {
          fieldValue = unsafeGetBoolean(heapBuffer, getReaderAddress(readerAddr, acc));
          acc += 1;
        } else if (dispatchId == DispatchId.INT8) {
          fieldValue = unsafeGet(heapBuffer, getReaderAddress(readerAddr, acc));
          acc += 1;
        } else if (dispatchId == DispatchId.UINT8) {
          fieldValue =
              new StaticInvoke(
                  Byte.class,
                  "toUnsignedInt",
                  descriptor.getTypeRef(),
                  unsafeGet(heapBuffer, getReaderAddress(readerAddr, acc)));
          acc += 1;
        } else if (dispatchId == DispatchId.CHAR) {
          fieldValue = unsafeGetChar(heapBuffer, getReaderAddress(readerAddr, acc));
          acc += 2;
        } else if (dispatchId == DispatchId.INT16) {
          fieldValue = unsafeGetShort(heapBuffer, getReaderAddress(readerAddr, acc));
          acc += 2;
        } else if (dispatchId == DispatchId.UINT16) {
          fieldValue =
              new StaticInvoke(
                  Short.class,
                  "toUnsignedInt",
                  descriptor.getTypeRef(),
                  unsafeGetShort(heapBuffer, getReaderAddress(readerAddr, acc)));
          acc += 2;
        } else if (dispatchId == DispatchId.FLOAT16) {
          fieldValue =
              new StaticInvoke(
                  Float16.class,
                  "fromBits",
                  TypeRef.of(Float16.class),
                  unsafeGetShort(heapBuffer, getReaderAddress(readerAddr, acc)));
          acc += 2;
        } else if (dispatchId == DispatchId.BFLOAT16) {
          fieldValue =
              new StaticInvoke(
                  BFloat16.class,
                  "fromBits",
                  TypeRef.of(BFloat16.class),
                  unsafeGetShort(heapBuffer, getReaderAddress(readerAddr, acc)));
          acc += 2;
        } else if (dispatchId == DispatchId.FLOAT32) {
          fieldValue = unsafeGetFloat(heapBuffer, getReaderAddress(readerAddr, acc));
          acc += 4;
        } else if (dispatchId == DispatchId.FLOAT64) {
          fieldValue = unsafeGetDouble(heapBuffer, getReaderAddress(readerAddr, acc));
          acc += 8;
        } else if (dispatchId == DispatchId.INT32) {
          fieldValue = unsafeGetInt(heapBuffer, getReaderAddress(readerAddr, acc));
          acc += 4;
        } else if (dispatchId == DispatchId.UINT32) {
          fieldValue =
              new StaticInvoke(
                  Integer.class,
                  "toUnsignedLong",
                  descriptor.getTypeRef(),
                  unsafeGetInt(heapBuffer, getReaderAddress(readerAddr, acc)));
          acc += 4;
        } else if (dispatchId == DispatchId.INT64 || dispatchId == DispatchId.UINT64) {
          fieldValue = unsafeGetLong(heapBuffer, getReaderAddress(readerAddr, acc));
          acc += 8;
        } else if (dispatchId == DispatchId.VARINT32) {
          if (!compressStarted) {
            compressStarted = true;
            addIncReaderIndexExpr(groupExpressions, buffer, acc);
          }
          fieldValue = readVarInt32(buffer);
        } else if (dispatchId == DispatchId.VAR_UINT32) {
          if (!compressStarted) {
            compressStarted = true;
            addIncReaderIndexExpr(groupExpressions, buffer, acc);
          }
          fieldValue =
              new StaticInvoke(
                  Integer.class,
                  "toUnsignedLong",
                  descriptor.getTypeRef(),
                  new Invoke(buffer, "readVarUInt32", PRIMITIVE_INT_TYPE));
        } else if (dispatchId == DispatchId.VARINT64) {
          if (!compressStarted) {
            compressStarted = true;
            addIncReaderIndexExpr(groupExpressions, buffer, acc);
          }
          fieldValue = new Invoke(buffer, "readVarInt64", PRIMITIVE_LONG_TYPE);
        } else if (dispatchId == DispatchId.TAGGED_INT64) {
          if (!compressStarted) {
            compressStarted = true;
            addIncReaderIndexExpr(groupExpressions, buffer, acc);
          }
          fieldValue = new Invoke(buffer, "readTaggedInt64", PRIMITIVE_LONG_TYPE);
        } else if (dispatchId == DispatchId.VAR_UINT64) {
          if (!compressStarted) {
            compressStarted = true;
            addIncReaderIndexExpr(groupExpressions, buffer, acc);
          }
          fieldValue = new Invoke(buffer, "readVarUInt64", PRIMITIVE_LONG_TYPE);
        } else if (dispatchId == DispatchId.TAGGED_UINT64) {
          if (!compressStarted) {
            compressStarted = true;
            addIncReaderIndexExpr(groupExpressions, buffer, acc);
          }
          fieldValue = new Invoke(buffer, "readTaggedUInt64", PRIMITIVE_LONG_TYPE);
        } else {
          throw new IllegalStateException("Unsupported dispatchId: " + dispatchId);
        }
        // `bean` will be replaced by `Reference` to cut-off expr dependency.
        groupExpressions.add(setFieldValue(bean, descriptor, fieldValue));
      }
      if (acc != 0) {
        checkReadableBytesStub.setTargetObject(
            new Invoke(buffer, "checkReadableBytes", Literal.ofInt(acc)));
      }
      if (!compressStarted) {
        addIncReaderIndexExpr(groupExpressions, buffer, acc);
      }
      if (hasFewFields() || numPrimitiveFields < 4 || isRecord) {
        expressions.add(groupExpressions);
      } else {
        expressions.add(
            objectCodecOptimizer.invokeGenerated(
                ofHashSet(bean, buffer, heapBuffer), groupExpressions, "readFields"));
      }
    }
    return expressions;
  }

  private List<Expression> deserializeRawIndexed(
      Expression bean, Expression buffer, List<List<Descriptor>> primitiveGroups, int totalSize) {
    List<Expression> expressions = new ArrayList<>();
    int numPrimitiveFields = getNumPrimitiveFields(primitiveGroups);
    Literal totalSizeLiteral = Literal.ofInt(totalSize);
    expressions.add(new Invoke(buffer, "checkReadableBytes", totalSizeLiteral));
    Expression readerIndex = new Invoke(buffer, "readerIndex", "readerIndex", PRIMITIVE_INT_TYPE);
    expressions.add(readerIndex);
    int acc = 0;
    for (List<Descriptor> group : primitiveGroups) {
      ListExpression groupExpressions = new ListExpression();
      for (Descriptor descriptor : group) {
        int dispatchId = getNumericDescriptorDispatchId(descriptor);
        Expression fieldValue;
        if (dispatchId == DispatchId.BOOL) {
          fieldValue = bufferGetBoolean(buffer, getBufferIndex(readerIndex, acc));
          acc += 1;
        } else if (dispatchId == DispatchId.INT8) {
          fieldValue = bufferGetByte(buffer, getBufferIndex(readerIndex, acc));
          acc += 1;
        } else if (dispatchId == DispatchId.UINT8) {
          fieldValue =
              new StaticInvoke(
                  Byte.class,
                  "toUnsignedInt",
                  descriptor.getTypeRef(),
                  bufferGetByte(buffer, getBufferIndex(readerIndex, acc)));
          acc += 1;
        } else if (dispatchId == DispatchId.CHAR) {
          fieldValue = bufferGetChar(buffer, getBufferIndex(readerIndex, acc));
          acc += 2;
        } else if (dispatchId == DispatchId.INT16) {
          fieldValue = bufferGetInt16(buffer, getBufferIndex(readerIndex, acc));
          acc += 2;
        } else if (dispatchId == DispatchId.UINT16) {
          fieldValue =
              new StaticInvoke(
                  Short.class,
                  "toUnsignedInt",
                  descriptor.getTypeRef(),
                  bufferGetInt16(buffer, getBufferIndex(readerIndex, acc)));
          acc += 2;
        } else if (dispatchId == DispatchId.FLOAT16) {
          fieldValue =
              new StaticInvoke(
                  Float16.class,
                  "fromBits",
                  TypeRef.of(Float16.class),
                  bufferGetInt16(buffer, getBufferIndex(readerIndex, acc)));
          acc += 2;
        } else if (dispatchId == DispatchId.BFLOAT16) {
          fieldValue =
              new StaticInvoke(
                  BFloat16.class,
                  "fromBits",
                  TypeRef.of(BFloat16.class),
                  bufferGetInt16(buffer, getBufferIndex(readerIndex, acc)));
          acc += 2;
        } else if (dispatchId == DispatchId.INT32) {
          fieldValue = bufferGetInt32(buffer, getBufferIndex(readerIndex, acc));
          acc += 4;
        } else if (dispatchId == DispatchId.UINT32) {
          fieldValue =
              new StaticInvoke(
                  Integer.class,
                  "toUnsignedLong",
                  descriptor.getTypeRef(),
                  bufferGetInt32(buffer, getBufferIndex(readerIndex, acc)));
          acc += 4;
        } else if (dispatchId == DispatchId.INT64 || dispatchId == DispatchId.UINT64) {
          fieldValue = bufferGetInt64(buffer, getBufferIndex(readerIndex, acc));
          acc += 8;
        } else if (dispatchId == DispatchId.FLOAT32) {
          fieldValue = bufferGetFloat32(buffer, getBufferIndex(readerIndex, acc));
          acc += 4;
        } else if (dispatchId == DispatchId.FLOAT64) {
          fieldValue = bufferGetFloat64(buffer, getBufferIndex(readerIndex, acc));
          acc += 8;
        } else {
          throw new IllegalStateException("Unsupported dispatchId: " + dispatchId);
        }
        groupExpressions.add(setFieldValue(bean, descriptor, fieldValue));
      }
      if (hasFewFields() || numPrimitiveFields < 4 || isRecord) {
        expressions.add(groupExpressions);
      } else {
        expressions.add(
            objectCodecOptimizer.invokeGenerated(
                ofHashSet(bean, buffer, readerIndex), groupExpressions, "readFields"));
      }
    }
    Expression increaseReaderIndex =
        new Invoke(
            buffer, "increaseReaderIndex", new Literal(totalSizeLiteral, PRIMITIVE_INT_TYPE));
    expressions.add(increaseReaderIndex);
    return expressions;
  }

  private List<Expression> deserializeCompressedIndexed(
      Expression bean, Expression buffer, List<List<Descriptor>> primitiveGroups) {
    List<Expression> expressions = new ArrayList<>();
    int numPrimitiveFields = getNumPrimitiveFields(primitiveGroups);
    for (List<Descriptor> group : primitiveGroups) {
      ReplaceStub checkReadableBytesStub = new ReplaceStub();
      expressions.add(checkReadableBytesStub);
      Expression readerIndex = new Invoke(buffer, "readerIndex", "readerIndex", PRIMITIVE_INT_TYPE);
      expressions.add(readerIndex);
      ListExpression groupExpressions = new ListExpression();
      int acc = 0;
      boolean compressStarted = false;
      for (Descriptor descriptor : group) {
        int dispatchId = getNumericDescriptorDispatchId(descriptor);
        Expression fieldValue;
        if (dispatchId == DispatchId.BOOL) {
          fieldValue = bufferGetBoolean(buffer, getBufferIndex(readerIndex, acc));
          acc += 1;
        } else if (dispatchId == DispatchId.INT8) {
          fieldValue = bufferGetByte(buffer, getBufferIndex(readerIndex, acc));
          acc += 1;
        } else if (dispatchId == DispatchId.UINT8) {
          fieldValue =
              new StaticInvoke(
                  Byte.class,
                  "toUnsignedInt",
                  descriptor.getTypeRef(),
                  bufferGetByte(buffer, getBufferIndex(readerIndex, acc)));
          acc += 1;
        } else if (dispatchId == DispatchId.CHAR) {
          fieldValue = bufferGetChar(buffer, getBufferIndex(readerIndex, acc));
          acc += 2;
        } else if (dispatchId == DispatchId.INT16) {
          fieldValue = bufferGetInt16(buffer, getBufferIndex(readerIndex, acc));
          acc += 2;
        } else if (dispatchId == DispatchId.UINT16) {
          fieldValue =
              new StaticInvoke(
                  Short.class,
                  "toUnsignedInt",
                  descriptor.getTypeRef(),
                  bufferGetInt16(buffer, getBufferIndex(readerIndex, acc)));
          acc += 2;
        } else if (dispatchId == DispatchId.FLOAT16) {
          fieldValue =
              new StaticInvoke(
                  Float16.class,
                  "fromBits",
                  TypeRef.of(Float16.class),
                  bufferGetInt16(buffer, getBufferIndex(readerIndex, acc)));
          acc += 2;
        } else if (dispatchId == DispatchId.BFLOAT16) {
          fieldValue =
              new StaticInvoke(
                  BFloat16.class,
                  "fromBits",
                  TypeRef.of(BFloat16.class),
                  bufferGetInt16(buffer, getBufferIndex(readerIndex, acc)));
          acc += 2;
        } else if (dispatchId == DispatchId.FLOAT32) {
          fieldValue = bufferGetFloat32(buffer, getBufferIndex(readerIndex, acc));
          acc += 4;
        } else if (dispatchId == DispatchId.FLOAT64) {
          fieldValue = bufferGetFloat64(buffer, getBufferIndex(readerIndex, acc));
          acc += 8;
        } else if (dispatchId == DispatchId.INT32) {
          fieldValue = bufferGetInt32(buffer, getBufferIndex(readerIndex, acc));
          acc += 4;
        } else if (dispatchId == DispatchId.UINT32) {
          fieldValue =
              new StaticInvoke(
                  Integer.class,
                  "toUnsignedLong",
                  descriptor.getTypeRef(),
                  bufferGetInt32(buffer, getBufferIndex(readerIndex, acc)));
          acc += 4;
        } else if (dispatchId == DispatchId.INT64 || dispatchId == DispatchId.UINT64) {
          fieldValue = bufferGetInt64(buffer, getBufferIndex(readerIndex, acc));
          acc += 8;
        } else if (dispatchId == DispatchId.VARINT32) {
          if (!compressStarted) {
            compressStarted = true;
            addIncReaderIndexExpr(groupExpressions, buffer, acc);
          }
          fieldValue = readVarInt32(buffer);
        } else if (dispatchId == DispatchId.VAR_UINT32) {
          if (!compressStarted) {
            compressStarted = true;
            addIncReaderIndexExpr(groupExpressions, buffer, acc);
          }
          fieldValue =
              new StaticInvoke(
                  Integer.class,
                  "toUnsignedLong",
                  descriptor.getTypeRef(),
                  new Invoke(buffer, "readVarUInt32", PRIMITIVE_INT_TYPE));
        } else if (dispatchId == DispatchId.VARINT64) {
          if (!compressStarted) {
            compressStarted = true;
            addIncReaderIndexExpr(groupExpressions, buffer, acc);
          }
          fieldValue = new Invoke(buffer, "readVarInt64", PRIMITIVE_LONG_TYPE);
        } else if (dispatchId == DispatchId.TAGGED_INT64) {
          if (!compressStarted) {
            compressStarted = true;
            addIncReaderIndexExpr(groupExpressions, buffer, acc);
          }
          fieldValue = new Invoke(buffer, "readTaggedInt64", PRIMITIVE_LONG_TYPE);
        } else if (dispatchId == DispatchId.VAR_UINT64) {
          if (!compressStarted) {
            compressStarted = true;
            addIncReaderIndexExpr(groupExpressions, buffer, acc);
          }
          fieldValue = new Invoke(buffer, "readVarUInt64", PRIMITIVE_LONG_TYPE);
        } else if (dispatchId == DispatchId.TAGGED_UINT64) {
          if (!compressStarted) {
            compressStarted = true;
            addIncReaderIndexExpr(groupExpressions, buffer, acc);
          }
          fieldValue = new Invoke(buffer, "readTaggedUInt64", PRIMITIVE_LONG_TYPE);
        } else {
          throw new IllegalStateException("Unsupported dispatchId: " + dispatchId);
        }
        groupExpressions.add(setFieldValue(bean, descriptor, fieldValue));
      }
      if (acc != 0) {
        checkReadableBytesStub.setTargetObject(
            new Invoke(buffer, "checkReadableBytes", Literal.ofInt(acc)));
      }
      if (!compressStarted) {
        addIncReaderIndexExpr(groupExpressions, buffer, acc);
      }
      if (hasFewFields() || numPrimitiveFields < 4 || isRecord) {
        expressions.add(groupExpressions);
      } else {
        expressions.add(
            objectCodecOptimizer.invokeGenerated(
                ofHashSet(bean, buffer, readerIndex), groupExpressions, "readFields"));
      }
    }
    return expressions;
  }

  private void addIncReaderIndexExpr(ListExpression expressions, Expression buffer, int diff) {
    if (diff != 0) {
      expressions.add(new Invoke(buffer, "increaseReaderIndex", Literal.ofInt(diff)));
    }
  }

  private Expression getReaderAddress(Expression readerPos, long acc) {
    if (acc == 0) {
      return readerPos;
    }
    return add(readerPos, new Literal(acc, PRIMITIVE_LONG_TYPE));
  }

  private Expression getBufferIndex(Expression index, int acc) {
    if (acc == 0) {
      return index;
    }
    return add(index, Literal.ofInt(acc));
  }
}
