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

package org.apache.fory.serializer;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.fory.Fory;
import org.apache.fory.builder.MetaSharedCodecBuilder;
import org.apache.fory.config.CompatibleMode;
import org.apache.fory.config.ForyBuilder;
import org.apache.fory.logging.Logger;
import org.apache.fory.logging.LoggerFactory;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.memory.Platform;
import org.apache.fory.meta.ClassDef;
import org.apache.fory.reflect.FieldAccessor;
import org.apache.fory.resolver.ClassInfoHolder;
import org.apache.fory.resolver.ClassResolver;
import org.apache.fory.resolver.RefResolver;
import org.apache.fory.resolver.TypeResolver;
import org.apache.fory.serializer.FieldGroups.SerializationFieldInfo;
import org.apache.fory.type.Descriptor;
import org.apache.fory.type.DescriptorGrouper;
import org.apache.fory.type.DispatchId;
import org.apache.fory.type.Generics;
import org.apache.fory.type.Types;
import org.apache.fory.util.DefaultValueUtils;
import org.apache.fory.util.GraalvmSupport;
import org.apache.fory.util.Preconditions;
import org.apache.fory.util.Utils;
import org.apache.fory.util.record.RecordInfo;
import org.apache.fory.util.record.RecordUtils;

/**
 * A meta-shared compatible deserializer builder based on {@link ClassDef}. This serializer will
 * compare fields between {@link ClassDef} and class fields, then create serializer to read and
 * set/skip corresponding fields to support type forward/backward compatibility. Serializer are
 * forward to {@link ObjectSerializer} for now. We can consolidate fields between peers to create
 * better serializers to serialize common fields between peers for efficiency.
 *
 * <p>With meta context share enabled and compatible mode, the {@link ObjectSerializer} will take
 * all non-inner final types as non-final, so that fory can write class definition when write class
 * info for those types.
 *
 * @see CompatibleMode
 * @see ForyBuilder#withMetaShare
 * @see MetaSharedCodecBuilder
 * @see ObjectSerializer
 */
public class MetaSharedSerializer<T> extends AbstractObjectSerializer<T> {
  private static final Logger LOG = LoggerFactory.getLogger(MetaSharedSerializer.class);

  private final SerializationFieldInfo[] buildInFields;
  private final SerializationFieldInfo[] containerFields;
  private final SerializationFieldInfo[] otherFields;
  private final RecordInfo recordInfo;
  private Serializer<T> serializer;
  private final ClassInfoHolder classInfoHolder;
  private final SerializationBinding binding;
  private final boolean hasDefaultValues;
  private final DefaultValueUtils.DefaultValueField[] defaultValueFields;

  public MetaSharedSerializer(Fory fory, Class<T> type, ClassDef classDef) {
    super(fory, type);
    Preconditions.checkArgument(
        !fory.getConfig().checkClassVersion(),
        "Class version check should be disabled when compatible mode is enabled.");
    Preconditions.checkArgument(
        fory.getConfig().isMetaShareEnabled(), "Meta share must be enabled.");
    if (Utils.debugOutputEnabled()) {
      LOG.info("========== MetaSharedSerializer ClassDef for {} ==========", type.getName());
      LOG.info("ClassDef fieldsInfo count: {}", classDef.getFieldsInfo().size());
      for (int i = 0; i < classDef.getFieldsInfo().size(); i++) {
        LOG.info("  [{}] {}", i, classDef.getFieldsInfo().get(i));
      }
    }
    Collection<Descriptor> descriptors = consolidateFields(fory._getTypeResolver(), type, classDef);
    DescriptorGrouper descriptorGrouper =
        fory._getTypeResolver().createDescriptorGrouper(descriptors, false);
    if (Utils.debugOutputEnabled()) {
      LOG.info(
          "========== MetaSharedSerializer sorted descriptors for {} ==========", type.getName());
      for (Descriptor d : descriptorGrouper.getSortedDescriptors()) {
        LOG.info(
            "  {} -> {}, ref {}, nullable {}, type id {}",
            d.getName(),
            d.getTypeName(),
            d.isTrackingRef(),
            d.isNullable(),
            Types.getDescriptorTypeId(fory, d));
      }
    }
    // d.getField() may be null if not exists in this class when meta share enabled.
    FieldGroups fieldGroups = FieldGroups.buildFieldInfos(fory, descriptorGrouper);
    buildInFields = fieldGroups.buildInFields;
    containerFields = fieldGroups.containerFields;
    otherFields = fieldGroups.userTypeFields;
    classInfoHolder = this.classResolver.nilClassInfoHolder();
    if (isRecord) {
      List<String> fieldNames =
          descriptorGrouper.getSortedDescriptors().stream()
              .map(Descriptor::getName)
              .collect(Collectors.toList());
      recordInfo = new RecordInfo(type, fieldNames);
    } else {
      recordInfo = null;
    }
    binding = SerializationBinding.createBinding(fory);
    boolean hasDefaultValues = false;
    DefaultValueUtils.DefaultValueField[] defaultValueFields =
        new DefaultValueUtils.DefaultValueField[0];
    DefaultValueUtils.DefaultValueSupport defaultValueSupport;
    if (fory.getConfig().isScalaOptimizationEnabled()) {
      defaultValueSupport = DefaultValueUtils.getScalaDefaultValueSupport();
      hasDefaultValues = defaultValueSupport.hasDefaultValues(type);
      defaultValueFields =
          defaultValueSupport.buildDefaultValueFields(
              fory, type, descriptorGrouper.getSortedDescriptors());
    }
    if (!hasDefaultValues) {
      DefaultValueUtils.DefaultValueSupport kotlinDefaultValueSupport =
          DefaultValueUtils.getKotlinDefaultValueSupport();
      if (kotlinDefaultValueSupport != null) {
        hasDefaultValues = kotlinDefaultValueSupport.hasDefaultValues(type);
        defaultValueFields =
            kotlinDefaultValueSupport.buildDefaultValueFields(
                fory, type, descriptorGrouper.getSortedDescriptors());
      }
    }
    this.hasDefaultValues = hasDefaultValues;
    this.defaultValueFields = defaultValueFields;
  }

  @Override
  public void write(MemoryBuffer buffer, T value) {
    if (serializer == null) {
      // xlang mode will register class and create serializer in advance, it won't go to here.
      serializer =
          this.classResolver.createSerializerSafe(type, () -> new ObjectSerializer<>(fory, type));
    }
    serializer.write(buffer, value);
  }

  @Override
  public void xwrite(MemoryBuffer buffer, T value) {
    write(buffer, value);
  }

  @Override
  public T read(MemoryBuffer buffer) {
    if (Utils.debugOutputEnabled()) {
      LOG.info("========== MetaSharedSerializer.read() for {} ==========", type.getName());
      LOG.info("Buffer readerIndex at start: {}", buffer.readerIndex());
      LOG.info("buildInFields count: {}", buildInFields.length);
      for (int i = 0; i < buildInFields.length; i++) {
        SerializationFieldInfo fi = buildInFields[i];
        LOG.info(
            "  buildInField[{}]: name={}, dispatchId={}, nullable={}, isPrimitive={}, hasAccessor={}",
            i,
            fi.qualifiedFieldName,
            fi.dispatchId,
            fi.nullable,
            fi.isPrimitive,
            fi.fieldAccessor != null);
      }
    }
    if (isRecord) {
      Object[] fieldValues =
          new Object[buildInFields.length + otherFields.length + containerFields.length];
      readFields(buffer, fieldValues);
      fieldValues = RecordUtils.remapping(recordInfo, fieldValues);
      T t = objectCreator.newInstanceWithArguments(fieldValues);
      Arrays.fill(recordInfo.getRecordComponents(), null);
      return t;
    }
    T obj = newInstance();
    Fory fory = this.fory;
    RefResolver refResolver = this.refResolver;
    TypeResolver typeResolver = this.typeResolver;
    SerializationBinding binding = this.binding;
    refResolver.reference(obj);
    // read order: primitive,boxed,final,other,collection,map
    for (SerializationFieldInfo fieldInfo : this.buildInFields) {
      FieldAccessor fieldAccessor = fieldInfo.fieldAccessor;
      boolean nullable = fieldInfo.nullable;
      if (Utils.debugOutputEnabled()) {
        LOG.info(
            "[Java] About to read field: name={}, dispatchId={}, nullable={}, isPrimitive={}, bufferPos={}",
            fieldInfo.qualifiedFieldName,
            fieldInfo.dispatchId,
            nullable,
            fieldInfo.isPrimitive,
            buffer.readerIndex());
        // Print next 16 bytes from buffer for debugging
        int pos = buffer.readerIndex();
        int remaining = Math.min(16, buffer.size() - pos);
        if (remaining > 0) {
          byte[] peek = new byte[remaining];
          for (int i = 0; i < remaining; i++) {
            peek[i] = buffer.getByte(pos + i);
          }
          StringBuilder hex = new StringBuilder();
          for (byte b : peek) {
            hex.append(String.format("%02x", b));
          }
          LOG.info("[Java] Next {} bytes at pos {}: {}", remaining, pos, hex.toString());
        }
      }
      if (fieldAccessor != null) {
        int dispatchId = fieldInfo.dispatchId;
        boolean needRead = true;
        if (fieldInfo.isPrimitive) {
          // PRIMITIVE_* dispatch IDs mean non-nullable, so no null flag is read
          needRead =
              AbstractObjectSerializer.readPrimitiveFieldValue(
                  buffer, obj, fieldAccessor, dispatchId);
        }
        // For NOTNULL_BOXED and other boxed types, let readBasicObjectFieldValue handle it
        // which uses putObject for proper boxing
        if (needRead
            && (nullable
                ? AbstractObjectSerializer.readBasicNullableObjectFieldValue(
                    fory, buffer, obj, fieldAccessor, dispatchId)
                : AbstractObjectSerializer.readBasicObjectFieldValue(
                    fory, buffer, obj, fieldAccessor, dispatchId))) {
          assert fieldInfo.classInfo != null;
          Object fieldValue =
              AbstractObjectSerializer.readFinalObjectFieldValue(
                  binding, refResolver, typeResolver, fieldInfo, buffer);
          fieldAccessor.putObject(obj, fieldValue);
        }
      } else {
        if (fieldInfo.fieldConverter == null) {
          // Skip the field value from buffer since it doesn't exist in current class
          if (skipPrimitiveFieldValueFailed(fory, fieldInfo.dispatchId, buffer)) {
            if (fieldInfo.classInfo == null) {
              // TODO(chaokunyang) support registered serializer in peer with ref tracking disabled.
              binding.readRef(buffer, classInfoHolder);
            } else {
              AbstractObjectSerializer.readFinalObjectFieldValue(
                  binding, refResolver, typeResolver, fieldInfo, buffer);
            }
          }
        } else {
          compatibleRead(buffer, fieldInfo, obj);
        }
      }
    }
    Generics generics = fory.getGenerics();
    for (SerializationFieldInfo fieldInfo : containerFields) {
      Object fieldValue =
          AbstractObjectSerializer.readContainerFieldValue(binding, generics, fieldInfo, buffer);
      FieldAccessor fieldAccessor = fieldInfo.fieldAccessor;
      if (fieldAccessor != null) {
        fieldAccessor.putObject(obj, fieldValue);
      }
    }
    for (SerializationFieldInfo fieldInfo : otherFields) {
      Object fieldValue = AbstractObjectSerializer.readOtherFieldValue(binding, fieldInfo, buffer);
      FieldAccessor fieldAccessor = fieldInfo.fieldAccessor;
      if (fieldAccessor != null) {
        fieldAccessor.putObject(obj, fieldValue);
      }
    }
    return obj;
  }

  private void compatibleRead(MemoryBuffer buffer, SerializationFieldInfo fieldInfo, Object obj) {
    Object fieldValue;
    int dispatchId = fieldInfo.dispatchId;
    if (DispatchId.isPrimitive(dispatchId)) {
      fieldValue = Serializers.readPrimitiveValue(buffer, dispatchId);
    } else {
      fieldValue =
          AbstractObjectSerializer.readFinalObjectFieldValue(
              binding, refResolver, classResolver, fieldInfo, buffer);
    }
    fieldInfo.fieldConverter.set(obj, fieldValue);
  }

  private T newInstance() {
    if (!hasDefaultValues) {
      return newBean();
    }
    T obj = GraalvmSupport.IN_GRAALVM_NATIVE_IMAGE ? newBean() : Platform.newInstance(type);
    // Set default values for missing fields in Scala case classes
    DefaultValueUtils.setDefaultValues(obj, defaultValueFields);
    return obj;
  }

  @Override
  public T xread(MemoryBuffer buffer) {
    return read(buffer);
  }

  private void readFields(MemoryBuffer buffer, Object[] fields) {
    int counter = 0;
    Fory fory = this.fory;
    RefResolver refResolver = this.refResolver;
    ClassResolver classResolver = this.classResolver;
    SerializationBinding binding = this.binding;
    // read order: primitive,boxed,final,other,collection,map
    for (SerializationFieldInfo fieldInfo : this.buildInFields) {
      if (fieldInfo.fieldAccessor != null) {
        assert fieldInfo.classInfo != null;
        int dispatchId = fieldInfo.dispatchId;
        // primitive field won't write null flag.
        if (DispatchId.isPrimitive(dispatchId)) {
          fields[counter++] = Serializers.readPrimitiveValue(buffer, dispatchId);
        } else {
          Object fieldValue =
              AbstractObjectSerializer.readFinalObjectFieldValue(
                  binding, refResolver, classResolver, fieldInfo, buffer);
          fields[counter++] = fieldValue;
        }
      } else {
        // Skip the field value from buffer since it doesn't exist in current class
        if (skipPrimitiveFieldValueFailed(fory, fieldInfo.dispatchId, buffer)) {
          if (fieldInfo.classInfo == null) {
            // TODO(chaokunyang) support registered serializer in peer with ref tracking disabled.
            fory.readRef(buffer, classInfoHolder);
          } else {
            AbstractObjectSerializer.readFinalObjectFieldValue(
                binding, refResolver, classResolver, fieldInfo, buffer);
          }
        }
        // remapping will handle those extra fields from peers.
        fields[counter++] = null;
      }
    }
    for (SerializationFieldInfo fieldInfo : otherFields) {
      Object fieldValue = AbstractObjectSerializer.readOtherFieldValue(binding, fieldInfo, buffer);
      fields[counter++] = fieldValue;
    }
    Generics generics = fory.getGenerics();
    for (SerializationFieldInfo fieldInfo : containerFields) {
      Object fieldValue =
          AbstractObjectSerializer.readContainerFieldValue(binding, generics, fieldInfo, buffer);
      fields[counter++] = fieldValue;
    }
  }

  /** Skip primitive/notnull-boxed field value since they don't write null flag. */
  static boolean skipPrimitiveFieldValueFailed(Fory fory, int dispatchId, MemoryBuffer buffer) {
    switch (dispatchId) {
      case DispatchId.PRIMITIVE_BOOL:
      case DispatchId.NOTNULL_BOXED_BOOL:
        buffer.increaseReaderIndex(1);
        return false;
      case DispatchId.PRIMITIVE_INT8:
      case DispatchId.PRIMITIVE_UINT8:
      case DispatchId.NOTNULL_BOXED_INT8:
      case DispatchId.NOTNULL_BOXED_UINT8:
        buffer.increaseReaderIndex(1);
        return false;
      case DispatchId.PRIMITIVE_CHAR:
      case DispatchId.NOTNULL_BOXED_CHAR:
        buffer.increaseReaderIndex(2);
        return false;
      case DispatchId.PRIMITIVE_INT16:
      case DispatchId.PRIMITIVE_UINT16:
      case DispatchId.NOTNULL_BOXED_INT16:
      case DispatchId.NOTNULL_BOXED_UINT16:
        buffer.increaseReaderIndex(2);
        return false;
      case DispatchId.PRIMITIVE_INT32:
      case DispatchId.NOTNULL_BOXED_INT32:
        buffer.increaseReaderIndex(4);
        return false;
      case DispatchId.PRIMITIVE_VARINT32:
      case DispatchId.NOTNULL_BOXED_VARINT32:
        buffer.readVarInt32();
        return false;
      case DispatchId.PRIMITIVE_UINT32:
      case DispatchId.NOTNULL_BOXED_UINT32:
        buffer.increaseReaderIndex(4);
        return false;
      case DispatchId.PRIMITIVE_VAR_UINT32:
      case DispatchId.NOTNULL_BOXED_VAR_UINT32:
        buffer.readVarUint32();
        return false;
      case DispatchId.PRIMITIVE_INT64:
      case DispatchId.NOTNULL_BOXED_INT64:
        buffer.increaseReaderIndex(8);
        return false;
      case DispatchId.PRIMITIVE_VARINT64:
      case DispatchId.NOTNULL_BOXED_VARINT64:
        buffer.readVarInt64();
        return false;
      case DispatchId.PRIMITIVE_TAGGED_INT64:
      case DispatchId.NOTNULL_BOXED_TAGGED_INT64:
        buffer.readTaggedInt64();
        return false;
      case DispatchId.PRIMITIVE_UINT64:
      case DispatchId.NOTNULL_BOXED_UINT64:
        buffer.increaseReaderIndex(8);
        return false;
      case DispatchId.PRIMITIVE_VAR_UINT64:
      case DispatchId.NOTNULL_BOXED_VAR_UINT64:
        buffer.readVarUint64();
        return false;
      case DispatchId.PRIMITIVE_TAGGED_UINT64:
      case DispatchId.NOTNULL_BOXED_TAGGED_UINT64:
        buffer.readTaggedUint64();
        return false;
      case DispatchId.PRIMITIVE_FLOAT32:
      case DispatchId.NOTNULL_BOXED_FLOAT32:
        buffer.increaseReaderIndex(4);
        return false;
      case DispatchId.PRIMITIVE_FLOAT64:
      case DispatchId.NOTNULL_BOXED_FLOAT64:
        buffer.increaseReaderIndex(8);
        return false;
      default:
        return true;
    }
  }

  public static Collection<Descriptor> consolidateFields(
      TypeResolver resolver, Class<?> cls, ClassDef classDef) {
    return classDef.getDescriptors(resolver, cls);
  }
}
