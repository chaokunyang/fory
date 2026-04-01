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
import org.apache.fory.collection.IdentityObjectIntMap;
import org.apache.fory.collection.ObjectIntMap;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.meta.TypeDef;
import org.apache.fory.resolver.MetaContext;
import org.apache.fory.serializer.FieldGroups.SerializationFieldInfo;
import org.apache.fory.util.Preconditions;

/**
 * Base class for meta-shared layer serializers. It owns layer metadata shared by both interpreter
 * mode ({@link MetaSharedLayerSerializer}) and generated serializers.
 *
 * @see MetaSharedLayerSerializer
 * @see org.apache.fory.builder.MetaSharedLayerCodecBuilder
 */
public abstract class MetaSharedLayerSerializerBase<T> extends AbstractObjectSerializer<T> {
  private final long layerTypeDefId;
  private final TypeDef layerTypeDef;
  private final Class<?> layerMarkerClass;
  private final String[] fieldNames;
  private final Class<?>[] fieldTypes;
  private transient MetaSharedLayerSerializer<T> fallbackSerializer;

  protected MetaSharedLayerSerializerBase(
      Fory fory,
      Class<T> type,
      TypeDef layerTypeDef,
      Class<?> layerMarkerClass,
      String[] fieldNames,
      Class<?>[] fieldTypes) {
    super(fory, type);
    this.layerTypeDef = Preconditions.checkNotNull(fory.getTypeResolver().cacheTypeDef(layerTypeDef));
    this.layerTypeDefId = this.layerTypeDef.getId();
    this.layerMarkerClass = Preconditions.checkNotNull(layerMarkerClass);
    this.fieldNames = Preconditions.checkNotNull(fieldNames);
    this.fieldTypes = Preconditions.checkNotNull(fieldTypes);
    Preconditions.checkArgument(
        fieldNames.length == fieldTypes.length,
        "Field names and field types must have the same length");
  }

  protected MetaSharedLayerSerializerBase(
      Fory fory,
      Class<T> type,
      long layerTypeDefId,
      Class<?> layerMarkerClass,
      String[] fieldNames,
      Class<?>[] fieldTypes) {
    this(
        fory,
        type,
        Preconditions.checkNotNull(
            fory.getTypeResolver().getTypeDefById(layerTypeDefId),
            "Missing cached layer TypeDef for id " + layerTypeDefId + " and class " + type),
        layerMarkerClass,
        fieldNames,
        fieldTypes);
  }

  protected static String[] buildPutFieldNames(SerializationFieldInfo[] fieldInfos) {
    String[] fieldNames = new String[fieldInfos.length];
    for (int i = 0; i < fieldInfos.length; i++) {
      SerializationFieldInfo fieldInfo = fieldInfos[i];
      fieldNames[i] =
          fieldInfo.fieldAccessor != null
              ? fieldInfo.fieldAccessor.getField().getName()
              : fieldInfo.descriptor.getName();
    }
    return fieldNames;
  }

  protected static Class<?>[] buildPutFieldTypes(SerializationFieldInfo[] fieldInfos) {
    Class<?>[] fieldTypes = new Class<?>[fieldInfos.length];
    for (int i = 0; i < fieldInfos.length; i++) {
      SerializationFieldInfo fieldInfo = fieldInfos[i];
      fieldTypes[i] =
          fieldInfo.fieldAccessor != null
              ? fieldInfo.fieldAccessor.getField().getType()
              : fieldInfo.descriptor.getRawType();
    }
    return fieldTypes;
  }

  public final long getLayerTypeDefId() {
    return layerTypeDefId;
  }

  public final TypeDef getLayerTypeDef() {
    return layerTypeDef;
  }

  @Override
  public final void write(MemoryBuffer buffer, T value) {
    if (fory.getConfig().isMetaShareEnabled()) {
      writeLayerClassMeta(buffer);
    }
    writeFieldsOnly(buffer, value);
  }

  @Override
  public final T read(MemoryBuffer buffer) {
    T obj = newBean();
    refResolver.reference(obj);
    return readAndSetFields(buffer, obj);
  }

  /**
   * Read fields and set values on the provided object. Note: When meta share is enabled, the caller
   * (typically ObjectStreamSerializer) is responsible for reading the layer class meta first. This
   * method only reads field data, not the layer class meta.
   *
   * @param buffer the memory buffer to read from
   * @param obj the object to set field values on
   * @return the object with fields set
   */
  public abstract T readAndSetFields(MemoryBuffer buffer, T obj);

  /**
   * Write layer class meta to buffer. Called by ObjectStreamSerializer before writing fields. Only
   * writes meta if meta share is enabled.
   *
   * @param buffer the memory buffer to write to
   */
  public final void writeLayerClassMeta(MemoryBuffer buffer) {
    MetaContext metaContext = fory.getSerializationContext().getMetaContext();
    if (metaContext == null) {
      return;
    }
    IdentityObjectIntMap<Class<?>> classMap = metaContext.classMap;
    int newId = classMap.size;
    int id = classMap.putOrGet(layerMarkerClass, newId);
    if (id >= 0) {
      buffer.writeVarUint32((id << 1) | 1);
    } else {
      buffer.writeVarUint32(newId << 1);
      buffer.writeBytes(layerTypeDef.getEncoded());
    }
  }

  /**
   * Write fields only, without layer class meta. The layer class meta should be written by the
   * caller (ObjectStreamSerializer) before calling this method.
   *
   * @param buffer the memory buffer to write to
   * @param value the object to write fields from
   */
  public abstract void writeFieldsOnly(MemoryBuffer buffer, T value);

  protected final void writeFieldsOnlyWithFallback(MemoryBuffer buffer, Object value) {
    getOrCreateFallbackSerializer().writeFieldsOnly(buffer, (T) value);
  }

  protected final Object readAndSetFieldsWithFallback(MemoryBuffer buffer, Object obj) {
    return getOrCreateFallbackSerializer().readAndSetFields(buffer, (T) obj);
  }

  /**
   * Write field values from array. Used by writeFields() when values come from PutField. The values
   * array should be in the serializer's field order (buildIn, container, other).
   *
   * @param buffer the memory buffer to write to
   * @param vals the values array in field order
   */
  public final void writeFieldValues(MemoryBuffer buffer, Object[] vals) {
    for (int ordinal = 0; ordinal < fieldNames.length; ordinal++) {
      writeFieldValue(buffer, vals, ordinal);
    }
  }

  /**
   * Read field values into array. Used by readFields() to populate GetField. Returns values in the
   * serializer's field order (buildIn, container, other).
   *
   * @param buffer the memory buffer to read from
   * @return array of field values in field order
   */
  public final Object[] readFieldValues(MemoryBuffer buffer) {
    Object[] vals = new Object[fieldNames.length];
    for (int ordinal = 0; ordinal < fieldNames.length; ordinal++) {
      vals[ordinal] = readFieldValue(buffer, ordinal);
    }
    return vals;
  }

  /**
   * Get the total number of fields in this layer.
   *
   * @return number of fields
   */
  public final int getNumFields() {
    return fieldNames.length;
  }

  /**
   * Populate field index map and field types array in the serializer's field order. This is used by
   * ObjectStreamSerializer to build fieldIndexMap and putFieldTypes in the correct order.
   *
   * @param fieldIndexMap map to populate with field name → index
   * @param fieldTypes array to populate with field types (must be pre-allocated with getNumFields()
   *     size)
   */
  @SuppressWarnings("rawtypes")
  public final void populateFieldInfo(ObjectIntMap fieldIndexMap, Class<?>[] fieldTypes) {
    Preconditions.checkArgument(
        fieldTypes.length >= this.fieldTypes.length,
        "fieldTypes length %s is smaller than serializer field count %s",
        fieldTypes.length,
        this.fieldTypes.length);
    for (int i = 0; i < fieldNames.length; i++) {
      fieldIndexMap.put(fieldNames[i], i);
      fieldTypes[i] = this.fieldTypes[i];
    }
  }

  protected void writeFieldValue(MemoryBuffer buffer, Object[] vals, int ordinal) {
    getOrCreateFallbackSerializer().writeFieldValue(buffer, vals, ordinal);
  }

  protected Object readFieldValue(MemoryBuffer buffer, int ordinal) {
    return getOrCreateFallbackSerializer().readFieldValue(buffer, ordinal);
  }

  private MetaSharedLayerSerializer<T> getOrCreateFallbackSerializer() {
    MetaSharedLayerSerializer<T> serializer = fallbackSerializer;
    if (serializer == null) {
      serializer = new MetaSharedLayerSerializer<>(fory, type, layerTypeDef, layerMarkerClass);
      fallbackSerializer = serializer;
    }
    return serializer;
  }
}
