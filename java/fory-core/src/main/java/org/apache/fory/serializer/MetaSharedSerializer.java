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
import org.apache.fory.collection.Tuple2;
import org.apache.fory.collection.Tuple3;
import org.apache.fory.config.CompatibleMode;
import org.apache.fory.config.ForyBuilder;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.memory.Platform;
import org.apache.fory.meta.ClassDef;
import org.apache.fory.reflect.FieldAccessor;
import org.apache.fory.resolver.ClassInfoHolder;
import org.apache.fory.resolver.ClassResolver;
import org.apache.fory.resolver.RefResolver;
import org.apache.fory.resolver.TypeResolver;
import org.apache.fory.type.Descriptor;
import org.apache.fory.type.DescriptorGrouper;
import org.apache.fory.type.Generics;
import org.apache.fory.util.DefaultValueUtils;
import org.apache.fory.util.Preconditions;
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
@SuppressWarnings({"unchecked"})
public class MetaSharedSerializer<T> extends AbstractObjectSerializer<T> {
  private final ObjectSerializer.FinalTypeField[] finalFields;

  /**
   * Whether write class def for non-inner final types.
   *
   * @see ClassResolver#isMonomorphic(Class)
   */
  private final boolean[] isFinal;

  private final ObjectSerializer.GenericTypeField[] otherFields;
  private final ObjectSerializer.GenericTypeField[] containerFields;
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
    boolean xlang = fory.isCrossLanguage();
    Collection<Descriptor> descriptors =
        consolidateFields(
            xlang ? fory.getXtypeResolver() : fory.getClassResolver(), type, classDef);
    DescriptorGrouper descriptorGrouper = classResolver.createDescriptorGrouper(descriptors, false);
    // d.getField() may be null if not exists in this class when meta share enabled.
    Tuple3<
            Tuple2<ObjectSerializer.FinalTypeField[], boolean[]>,
            ObjectSerializer.GenericTypeField[],
            ObjectSerializer.GenericTypeField[]>
        infos = AbstractObjectSerializer.buildFieldInfos(fory, descriptorGrouper);
    finalFields = infos.f0.f0;
    isFinal = infos.f0.f1;
    otherFields = infos.f1;
    containerFields = infos.f2;
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
    DefaultValueUtils.DefaultValueSupport defaultValueSupport = null;
    if (fory.getConfig().isScalaOptimizationEnabled()) {
      defaultValueSupport = DefaultValueUtils.getScalaDefaultValueSupport();
      if (defaultValueSupport != null) {
        hasDefaultValues = defaultValueSupport.hasDefaultValues(type);
        defaultValueFields =
            defaultValueSupport.buildDefaultValueFields(
                fory, type, descriptorGrouper.getSortedDescriptors());
      }
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
    if (isRecord) {
      Object[] fieldValues =
          new Object[finalFields.length + otherFields.length + containerFields.length];
      readFields(buffer, fieldValues);
      fieldValues = RecordUtils.remapping(recordInfo, fieldValues);

      try {
        T t = (T) constructor.invokeWithArguments(fieldValues);
        Arrays.fill(recordInfo.getRecordComponents(), null);
        return t;
      } catch (Throwable e) {
        Platform.throwException(e);
      }
    }
    T obj = newInstance();
    Fory fory = this.fory;
    RefResolver refResolver = this.refResolver;
    ClassResolver classResolver = this.classResolver;
    SerializationBinding binding = this.binding;
    refResolver.reference(obj);
    // read order: primitive,boxed,final,other,collection,map
    ObjectSerializer.FinalTypeField[] finalFields = this.finalFields;
    for (int i = 0; i < finalFields.length; i++) {
      ObjectSerializer.FinalTypeField fieldInfo = finalFields[i];
      boolean isFinal = this.isFinal[i];
      FieldAccessor fieldAccessor = fieldInfo.fieldAccessor;
      boolean nullable = fieldInfo.nullable;
      if (fieldAccessor != null) {
        short classId = fieldInfo.classId;
        if (AbstractObjectSerializer.readPrimitiveFieldValueFailed(
                fory, buffer, obj, fieldAccessor, classId)
            && (nullable
                ? AbstractObjectSerializer.readBasicNullableObjectFieldValueFailed(
                    fory, buffer, obj, fieldAccessor, classId)
                : AbstractObjectSerializer.readBasicObjectFieldValueFailed(
                    fory, buffer, obj, fieldAccessor, classId))) {
          assert fieldInfo.classInfo != null;
          Object fieldValue =
              AbstractObjectSerializer.readFinalObjectFieldValue(
                  binding, refResolver, classResolver, fieldInfo, isFinal, buffer);
          fieldAccessor.putObject(obj, fieldValue);
        }
      } else {
        // Skip the field value from buffer since it doesn't exist in current class
        if (skipPrimitiveFieldValueFailed(fory, fieldInfo.classId, buffer)) {
          if (fieldInfo.classInfo == null) {
            // TODO(chaokunyang) support registered serializer in peer with ref tracking disabled.
            fory.readRef(buffer, classInfoHolder);
          } else {
            AbstractObjectSerializer.readFinalObjectFieldValue(
                binding, refResolver, classResolver, fieldInfo, isFinal, buffer);
          }
        }
      }
    }
    for (ObjectSerializer.GenericTypeField fieldInfo : otherFields) {
      Object fieldValue = AbstractObjectSerializer.readOtherFieldValue(binding, fieldInfo, buffer);
      FieldAccessor fieldAccessor = fieldInfo.fieldAccessor;
      if (fieldAccessor != null) {
        fieldAccessor.putObject(obj, fieldValue);
      }
    }
    Generics generics = fory.getGenerics();
    for (ObjectSerializer.GenericTypeField fieldInfo : containerFields) {
      Object fieldValue =
          AbstractObjectSerializer.readContainerFieldValue(binding, generics, fieldInfo, buffer);
      FieldAccessor fieldAccessor = fieldInfo.fieldAccessor;
      if (fieldAccessor != null) {
        fieldAccessor.putObject(obj, fieldValue);
      }
    }

    // Set default values for missing fields in Scala case classes
    if (hasDefaultValues) {
      DefaultValueUtils.setDefaultValues(obj, defaultValueFields);
    }

    return obj;
  }

  private T newInstance() {
    if (!hasDefaultValues) {
      return newBean();
    }
    return Platform.newInstance(type);
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
    ObjectSerializer.FinalTypeField[] finalFields = this.finalFields;
    for (int i = 0; i < finalFields.length; i++) {
      ObjectSerializer.FinalTypeField fieldInfo = finalFields[i];
      boolean isFinal = this.isFinal[i];
      if (fieldInfo.fieldAccessor != null) {
        assert fieldInfo.classInfo != null;
        short classId = fieldInfo.classId;
        // primitive field won't write null flag.
        if (classId >= ClassResolver.PRIMITIVE_BOOLEAN_CLASS_ID
            && classId <= ClassResolver.PRIMITIVE_DOUBLE_CLASS_ID) {
          fields[counter++] = Serializers.readPrimitiveValue(fory, buffer, classId);
        } else {
          Object fieldValue =
              AbstractObjectSerializer.readFinalObjectFieldValue(
                  binding, refResolver, classResolver, fieldInfo, isFinal, buffer);
          fields[counter++] = fieldValue;
        }
      } else {
        // Skip the field value from buffer since it doesn't exist in current class
        if (skipPrimitiveFieldValueFailed(fory, fieldInfo.classId, buffer)) {
          if (fieldInfo.classInfo == null) {
            // TODO(chaokunyang) support registered serializer in peer with ref tracking disabled.
            fory.readRef(buffer, classInfoHolder);
          } else {
            AbstractObjectSerializer.readFinalObjectFieldValue(
                binding, refResolver, classResolver, fieldInfo, isFinal, buffer);
          }
        }
        fields[counter++] = null;
      }
    }
    for (ObjectSerializer.GenericTypeField fieldInfo : otherFields) {
      Object fieldValue = AbstractObjectSerializer.readOtherFieldValue(binding, fieldInfo, buffer);
      fields[counter++] = fieldValue;
    }
    Generics generics = fory.getGenerics();
    for (ObjectSerializer.GenericTypeField fieldInfo : containerFields) {
      Object fieldValue =
          AbstractObjectSerializer.readContainerFieldValue(binding, generics, fieldInfo, buffer);
      fields[counter++] = fieldValue;
    }
  }

  /** Skip primitive primitive field value since it doesn't write null flag. */
  static boolean skipPrimitiveFieldValueFailed(Fory fory, short classId, MemoryBuffer buffer) {
    switch (classId) {
      case ClassResolver.PRIMITIVE_BOOLEAN_CLASS_ID:
        buffer.increaseReaderIndex(1);
        return false;
      case ClassResolver.PRIMITIVE_BYTE_CLASS_ID:
        buffer.increaseReaderIndex(1);
        return false;
      case ClassResolver.PRIMITIVE_CHAR_CLASS_ID:
        buffer.increaseReaderIndex(2);
        return false;
      case ClassResolver.PRIMITIVE_SHORT_CLASS_ID:
        buffer.increaseReaderIndex(2);
        return false;
      case ClassResolver.PRIMITIVE_INT_CLASS_ID:
        if (fory.compressInt()) {
          buffer.readVarInt32();
        } else {
          buffer.increaseReaderIndex(4);
        }
        return false;
      case ClassResolver.PRIMITIVE_FLOAT_CLASS_ID:
        buffer.increaseReaderIndex(4);
        return false;
      case ClassResolver.PRIMITIVE_LONG_CLASS_ID:
        fory.readInt64(buffer);
        return false;
      case ClassResolver.PRIMITIVE_DOUBLE_CLASS_ID:
        buffer.increaseReaderIndex(8);
        return false;
      default:
        {
          return true;
        }
    }
  }

  public static Collection<Descriptor> consolidateFields(
      TypeResolver resolver, Class<?> cls, ClassDef classDef) {
    return classDef.getDescriptors(resolver, cls);
  }
}
