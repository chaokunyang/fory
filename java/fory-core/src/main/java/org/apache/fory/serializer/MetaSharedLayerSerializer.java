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

import org.apache.fory.Fory;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.meta.TypeDef;
import org.apache.fory.reflect.FieldAccessor;
import org.apache.fory.resolver.TypeResolver;
import org.apache.fory.serializer.FieldGroups.SerializationFieldInfo;
import org.apache.fory.type.DescriptorGrouper;
import org.apache.fory.type.Generics;

/**
 * A meta-shared serializer for a single layer in a class hierarchy. This serializer is used by
 * {@link ObjectStreamSerializer} to serialize fields of a specific class layer without including
 * parent class fields.
 *
 * <p>Unlike {@link MetaSharedSerializer} which handles the full class hierarchy, this serializer
 * only handles fields declared in a specific class layer. It uses a generated marker class as the
 * key in {@code metaContext.classMap} to ensure unique identification of each layer.
 *
 * @see MetaSharedSerializer
 * @see ObjectStreamSerializer
 * @see org.apache.fory.builder.LayerMarkerClassGenerator
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class MetaSharedLayerSerializer<T> extends MetaSharedLayerSerializerBase<T> {
  private final SerializationFieldInfo[] buildInFields;
  private final SerializationFieldInfo[] otherFields;
  private final SerializationFieldInfo[] containerFields;

  /**
   * Creates a new MetaSharedLayerSerializer.
   *
   * @param fory the Fory instance
   * @param type the target class for this layer
   * @param layerTypeDef the TypeDef for this layer only (resolveParent=false)
   * @param layerMarkerClass the generated marker class used as key in metaContext.classMap
   */
  public MetaSharedLayerSerializer(
      Fory fory, Class<T> type, TypeDef layerTypeDef, Class<?> layerMarkerClass) {
    this(fory, type, layerTypeDef, layerMarkerClass, buildFieldGroups(fory, layerTypeDef, type));
  }

  private MetaSharedLayerSerializer(
      Fory fory,
      Class<T> type,
      TypeDef layerTypeDef,
      Class<?> layerMarkerClass,
      FieldGroups fieldGroups) {
    super(
        fory,
        type,
        layerTypeDef,
        layerMarkerClass,
        buildPutFieldNames(fieldGroups.allFields),
        buildPutFieldTypes(fieldGroups.allFields));
    this.buildInFields = fieldGroups.buildInFields;
    this.otherFields = fieldGroups.userTypeFields;
    this.containerFields = fieldGroups.containerFields;
  }

  private static FieldGroups buildFieldGroups(Fory fory, TypeDef layerTypeDef, Class<?> type) {
    TypeResolver typeResolver = fory.getTypeResolver();
    DescriptorGrouper descriptorGrouper = typeResolver.createDescriptorGrouper(layerTypeDef, type);
    return FieldGroups.buildFieldInfos(fory, descriptorGrouper);
  }

  @Override
  public void writeFieldsOnly(MemoryBuffer buffer, T value) {
    // Write fields in order: buildIn, container, other
    writeBuildInFields(buffer, value);
    writeContainerFields(buffer, value);
    writeOtherFields(buffer, value);
  }

  private void writeBuildInFields(MemoryBuffer buffer, T value) {
    for (SerializationFieldInfo fieldInfo : buildInFields) {
      AbstractObjectSerializer.writeBuildInField(
          fory, typeResolver, refResolver, fieldInfo, buffer, value);
    }
  }

  private void writeContainerFields(MemoryBuffer buffer, T value) {
    Generics generics = fory.getGenerics();
    for (SerializationFieldInfo fieldInfo : containerFields) {
      FieldAccessor fieldAccessor = fieldInfo.fieldAccessor;
      Object fieldValue = fieldAccessor.getObject(value);
      AbstractObjectSerializer.writeContainerFieldValue(
          fory, typeResolver, refResolver, generics, fieldInfo, buffer, fieldValue);
    }
  }

  private void writeOtherFields(MemoryBuffer buffer, T value) {
    for (SerializationFieldInfo fieldInfo : otherFields) {
      FieldAccessor fieldAccessor = fieldInfo.fieldAccessor;
      Object fieldValue = fieldAccessor.getObject(value);
      AbstractObjectSerializer.writeField(
          fory, typeResolver, refResolver, fieldInfo, buffer, fieldValue);
    }
  }

  @Override
  protected void writeFieldValue(MemoryBuffer buffer, Object[] vals, int ordinal) {
    SerializationFieldInfo fieldInfo = getFieldInfo(ordinal);
    if (ordinal < buildInFields.length) {
      AbstractObjectSerializer.writeBuildInFieldValue(
          fory, typeResolver, refResolver, fieldInfo, buffer, vals[ordinal]);
    } else if (ordinal < buildInFields.length + containerFields.length) {
      AbstractObjectSerializer.writeContainerFieldValue(
          fory, typeResolver, refResolver, fory.getGenerics(), fieldInfo, buffer, vals[ordinal]);
    } else {
      AbstractObjectSerializer.writeField(
          fory, typeResolver, refResolver, fieldInfo, buffer, vals[ordinal]);
    }
  }

  @Override
  protected Object readFieldValue(MemoryBuffer buffer, int ordinal) {
    SerializationFieldInfo fieldInfo = getFieldInfo(ordinal);
    if (ordinal < buildInFields.length) {
      return AbstractObjectSerializer.readBuildInFieldValue(
          fory, typeResolver, refResolver, fieldInfo, buffer);
    }
    if (ordinal < buildInFields.length + containerFields.length) {
      return AbstractObjectSerializer.readContainerFieldValue(
          fory, typeResolver, refResolver, fory.getGenerics(), fieldInfo, buffer);
    }
    return AbstractObjectSerializer.readField(fory, typeResolver, refResolver, fieldInfo, buffer);
  }

  private SerializationFieldInfo getFieldInfo(int ordinal) {
    if (ordinal < buildInFields.length) {
      return buildInFields[ordinal];
    }
    ordinal -= buildInFields.length;
    if (ordinal < containerFields.length) {
      return containerFields[ordinal];
    }
    return otherFields[ordinal - containerFields.length];
  }

  /**
   * Read fields and set values on the provided object. Note: When meta share is enabled, the caller
   * (typically ObjectStreamSerializer) is responsible for reading the layer class meta first. This
   * method only reads field data.
   *
   * @param buffer the memory buffer to read from
   * @param obj the object to set field values on
   * @return the object with fields set
   */
  @Override
  @SuppressWarnings("unchecked")
  public T readAndSetFields(MemoryBuffer buffer, T obj) {
    // Note: Layer class meta is read by ObjectStreamSerializer before calling this method
    // (when meta share is enabled). This method only reads field values.
    // Read fields in order: final, container, other
    readFinalFields(buffer, obj);
    readContainerFields(buffer, obj);
    readUserTypeFields(buffer, obj);
    return obj;
  }

  private void readFinalFields(MemoryBuffer buffer, T targetObject) {
    for (SerializationFieldInfo fieldInfo : buildInFields) {
      FieldAccessor fieldAccessor = fieldInfo.fieldAccessor;
      if (fieldAccessor != null) {
        AbstractObjectSerializer.readBuildInFieldValue(
            fory, typeResolver, refResolver, fieldInfo, buffer, targetObject);
      } else {
        // Field doesn't exist in current class - skip the value
        FieldSkipper.skipField(fory, typeResolver, refResolver, fieldInfo, buffer);
      }
    }
  }

  private void readContainerFields(MemoryBuffer buffer, T obj) {
    Generics generics = fory.getGenerics();
    for (SerializationFieldInfo fieldInfo : containerFields) {
      Object fieldValue =
          AbstractObjectSerializer.readContainerFieldValue(
              fory, typeResolver, refResolver, generics, fieldInfo, buffer);
      FieldAccessor fieldAccessor = fieldInfo.fieldAccessor;
      if (fieldAccessor != null) {
        fieldAccessor.putObject(obj, fieldValue);
      }
    }
  }

  private void readUserTypeFields(MemoryBuffer buffer, T obj) {
    for (SerializationFieldInfo fieldInfo : otherFields) {
      Object fieldValue =
          AbstractObjectSerializer.readField(fory, typeResolver, refResolver, fieldInfo, buffer);
      FieldAccessor fieldAccessor = fieldInfo.fieldAccessor;
      if (fieldAccessor != null) {
        fieldAccessor.putObject(obj, fieldValue);
      }
    }
  }

  /**
   * Skip field data in the buffer without setting it to any object. This is used for schema
   * evolution when a class layer exists in the sender but not in the receiver. The layer meta
   * should already be consumed before calling this method.
   *
   * @param buffer the memory buffer to read from
   */
  public void skipFields(MemoryBuffer buffer) {
    // Skip all fields in order: buildIn, container, other
    // We read the values but don't set them anywhere (they're discarded)
    skipBuildInFields(buffer);
    skipContainerFields(buffer);
    skipOtherFields(buffer);
  }

  private void skipBuildInFields(MemoryBuffer buffer) {
    for (SerializationFieldInfo fieldInfo : buildInFields) {
      // Read the field value (discarding the result) to advance buffer position
      FieldSkipper.skipField(fory, typeResolver, refResolver, fieldInfo, buffer);
    }
  }

  private void skipContainerFields(MemoryBuffer buffer) {
    Generics generics = fory.getGenerics();
    for (SerializationFieldInfo fieldInfo : containerFields) {
      // Read container field value to advance buffer position
      AbstractObjectSerializer.readContainerFieldValue(
          fory, typeResolver, refResolver, generics, fieldInfo, buffer);
    }
  }

  private void skipOtherFields(MemoryBuffer buffer) {
    for (SerializationFieldInfo fieldInfo : otherFields) {
      // Read field value to advance buffer position
      AbstractObjectSerializer.readField(fory, typeResolver, refResolver, fieldInfo, buffer);
    }
  }
}
