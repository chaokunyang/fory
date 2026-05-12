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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fory.annotation.Internal;
import org.apache.fory.context.CopyContext;
import org.apache.fory.context.ReadContext;
import org.apache.fory.context.RefReader;
import org.apache.fory.context.WriteContext;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.meta.FieldInfo;
import org.apache.fory.meta.TypeDef;
import org.apache.fory.resolver.TypeResolver;
import org.apache.fory.serializer.FieldGroups.SerializationFieldInfo;
import org.apache.fory.serializer.converter.FieldConverters;
import org.apache.fory.type.Descriptor;
import org.apache.fory.type.DescriptorGrouper;
import org.apache.fory.util.StringUtils;

/** Base class used by javac-generated {@code @ForyStruct} serializers. */
@Internal
public abstract class StaticGeneratedStructSerializer<T> extends AbstractObjectSerializer<T> {
  protected static final int UNKNOWN_FIELD = -1;

  protected final TypeDef typeDef;
  protected final List<RemoteFieldInfo> remoteFields;
  private final SerializationFieldInfo[] localFieldsById;

  @SuppressWarnings("unchecked")
  public StaticGeneratedStructSerializer(TypeResolver typeResolver, Class<?> type) {
    super(typeResolver, (Class<T>) type);
    this.typeDef = null;
    this.remoteFields = Collections.emptyList();
    this.localFieldsById = new SerializationFieldInfo[0];
  }

  @SuppressWarnings("unchecked")
  protected StaticGeneratedStructSerializer(
      TypeResolver typeResolver, Class<?> type, TypeDef typeDef, List<Descriptor> descriptors) {
    super(typeResolver, (Class<T>) type);
    List<Descriptor> runtimeDescriptors = runtimeDescriptors(descriptors);
    this.typeDef = typeDef;
    this.remoteFields =
        typeDef == null ? Collections.emptyList() : buildRemoteFields(typeDef, runtimeDescriptors);
    this.localFieldsById = buildLocalFieldsById(runtimeDescriptors);
  }

  @Override
  public abstract void write(WriteContext writeContext, T value);

  @Override
  public abstract T read(ReadContext readContext);

  @Override
  public abstract T copy(CopyContext copyContext, T value);

  public abstract List<Descriptor> getDescriptors();

  public abstract T readCompatible(ReadContext readContext);

  protected final FieldGroups buildFieldGroups(List<Descriptor> descriptors) {
    descriptors = runtimeDescriptors(descriptors);
    DescriptorGrouper grouper =
        FieldGroups.buildDescriptorGrouper(
            typeResolver, descriptors, false, descriptor -> descriptor);
    return FieldGroups.buildFieldInfos(typeResolver, grouper);
  }

  protected final List<Descriptor> runtimeDescriptors(List<Descriptor> descriptors) {
    return typeResolver.normalizeFieldDescriptors(type, true, descriptors);
  }

  protected final int[] localFieldIds(
      SerializationFieldInfo[] fieldInfos, List<Descriptor> descriptors) {
    Map<String, Integer> localIds = new HashMap<>();
    for (int i = 0; i < descriptors.size(); i++) {
      Descriptor descriptor = descriptors.get(i);
      localIds.put(fieldKey(descriptor), i);
    }
    int[] ids = new int[fieldInfos.length];
    for (int i = 0; i < fieldInfos.length; i++) {
      Integer id = localIds.get(fieldKey(fieldInfos[i].descriptor));
      if (id == null) {
        throw new IllegalStateException(
            "Generated descriptor is not part of local descriptor list: "
                + fieldInfos[i].descriptor);
      }
      ids[i] = id;
    }
    return ids;
  }

  protected final void writeBuildInFieldValue(
      WriteContext writeContext, SerializationFieldInfo fieldInfo, Object fieldValue) {
    AbstractObjectSerializer.writeBuildInFieldValue(
        writeContext,
        typeResolver,
        writeContext.getRefWriter(),
        fieldInfo,
        writeContext.getBuffer(),
        fieldValue);
  }

  protected final void writeContainerFieldValue(
      WriteContext writeContext, SerializationFieldInfo fieldInfo, Object fieldValue) {
    AbstractObjectSerializer.writeContainerFieldValue(
        writeContext,
        typeResolver,
        writeContext.getRefWriter(),
        writeContext.getGenerics(),
        fieldInfo,
        writeContext.getBuffer(),
        fieldValue);
  }

  protected final void writeOtherFieldValue(
      WriteContext writeContext, SerializationFieldInfo fieldInfo, Object fieldValue) {
    AbstractObjectSerializer.writeField(
        writeContext,
        typeResolver,
        writeContext.getRefWriter(),
        fieldInfo,
        writeContext.getBuffer(),
        fieldValue);
  }

  protected final Object readBuildInFieldValue(
      ReadContext readContext, SerializationFieldInfo fieldInfo) {
    return AbstractObjectSerializer.readBuildInFieldValue(
        readContext, typeResolver, readContext.getRefReader(), fieldInfo, readContext.getBuffer());
  }

  protected final Object readContainerFieldValue(
      ReadContext readContext, SerializationFieldInfo fieldInfo) {
    return AbstractObjectSerializer.readContainerFieldValue(
        readContext,
        typeResolver,
        readContext.getRefReader(),
        readContext.getGenerics(),
        fieldInfo,
        readContext.getBuffer());
  }

  protected final Object readOtherFieldValue(
      ReadContext readContext, SerializationFieldInfo fieldInfo) {
    return AbstractObjectSerializer.readField(
        readContext, typeResolver, readContext.getRefReader(), fieldInfo, readContext.getBuffer());
  }

  protected final Object readRemoteField(ReadContext readContext, RemoteFieldInfo remoteField) {
    if (remoteField.compatibleCollectionArrayReadAction != null) {
      return CompatibleCollectionArrayReader.read(
          readContext,
          remoteField.serializationFieldInfo.refMode,
          remoteField.compatibleCollectionArrayReadAction);
    }
    return readField(readContext, remoteField.serializationFieldInfo);
  }

  protected final void skipField(ReadContext readContext, RemoteFieldInfo remoteField) {
    skipField(
        readContext,
        readContext.getRefReader(),
        remoteField.serializationFieldInfo,
        readContext.getBuffer());
  }

  protected final void skipField(
      ReadContext readContext,
      RefReader refReader,
      SerializationFieldInfo fieldInfo,
      MemoryBuffer buffer) {
    FieldSkipper.skipField(readContext, typeResolver, refReader, fieldInfo, buffer);
  }

  protected final int matchedId(RemoteFieldInfo remoteField) {
    return remoteField.matchedId;
  }

  protected final SerializationFieldInfo localFieldInfo(int matchedId) {
    return localFieldsById[matchedId];
  }

  protected final boolean canReadRemoteField(
      RemoteFieldInfo remoteField, SerializationFieldInfo localFieldInfo) {
    if (remoteField.compatibleCollectionArrayReadAction != null) {
      return true;
    }
    Class<?> remoteType = remoteField.serializationFieldInfo.typeRef.getRawType();
    Class<?> localType = localFieldInfo.typeRef.getRawType();
    return FieldConverters.canConvert(remoteType, localType);
  }

  protected final Object readCompatibleFieldValue(
      ReadContext readContext, RemoteFieldInfo remoteField, SerializationFieldInfo localFieldInfo) {
    Object fieldValue = readRemoteField(readContext, remoteField);
    if (remoteField.compatibleCollectionArrayReadAction != null) {
      return fieldValue;
    }
    Class<?> remoteType = remoteField.serializationFieldInfo.typeRef.getRawType();
    Class<?> localType = localFieldInfo.typeRef.getRawType();
    return FieldConverters.convertValue(remoteType, localType, fieldValue);
  }

  protected final boolean hasFieldConverter(RemoteFieldInfo remoteField) {
    return remoteField.serializationFieldInfo.fieldConverter != null;
  }

  protected final void setConvertedField(
      Object targetObject, Object fieldValue, RemoteFieldInfo remoteField) {
    remoteField.serializationFieldInfo.fieldConverter.set(targetObject, fieldValue);
  }

  protected final Object copyFieldValue(
      CopyContext copyContext, Object fieldValue, SerializationFieldInfo fieldInfo) {
    if (fieldInfo.containerSerializerOverride != null) {
      @SuppressWarnings("unchecked")
      Serializer<Object> serializer = (Serializer<Object>) fieldInfo.containerSerializerOverride;
      return copyContext.copyObject(fieldValue, serializer);
    }
    return copyContext.copyObject(fieldValue, fieldInfo.dispatchId);
  }

  protected final int computeClassVersionHash(List<Descriptor> descriptors) {
    descriptors = runtimeDescriptors(descriptors);
    return ObjectSerializer.computeStructHash(
        typeResolver,
        FieldGroups.buildDescriptorGrouper(
            typeResolver, descriptors, false, descriptor -> descriptor));
  }

  protected final void checkClassVersion(int readHash, int classVersionHash) {
    ObjectSerializer.checkClassVersion(type, readHash, classVersionHash);
  }

  private Object readField(ReadContext readContext, SerializationFieldInfo fieldInfo) {
    if (typeResolver.isCollectionDescriptor(fieldInfo.descriptor)
        || typeResolver.isMap(fieldInfo.type)) {
      return readContainerFieldValue(readContext, fieldInfo);
    }
    if (typeResolver.isBuildIn(fieldInfo.descriptor)
        || typeResolver.usesPrimitiveFieldOrdering(fieldInfo.descriptor)) {
      return readBuildInFieldValue(readContext, fieldInfo);
    }
    return readOtherFieldValue(readContext, fieldInfo);
  }

  private List<RemoteFieldInfo> buildRemoteFields(
      TypeDef remoteTypeDef, List<Descriptor> localDescriptors) {
    List<FieldInfo> remoteFieldInfos = remoteTypeDef.getFieldsInfo();
    List<Descriptor> remoteDescriptors =
        remoteTypeDef.getDescriptors(typeResolver, type, localDescriptors);
    Map<String, FieldInfo> remoteFieldInfosByKey = new HashMap<>();
    for (int i = 0; i < remoteFieldInfos.size(); i++) {
      FieldInfo fieldInfo = remoteFieldInfos.get(i);
      Descriptor descriptor = remoteDescriptors.get(i);
      putRemoteFieldInfo(remoteFieldInfosByKey, fieldInfo, descriptor);
    }
    Map<Short, Integer> fieldIds = new HashMap<>();
    Map<String, Integer> fields = new HashMap<>();
    for (int i = 0; i < localDescriptors.size(); i++) {
      Descriptor descriptor = localDescriptors.get(i);
      if (descriptor.hasForyFieldId()) {
        fieldIds.put((short) descriptor.getForyFieldId(), i);
      }
      fields.put(fieldKey(descriptor), i);
    }
    FieldGroups remoteFieldGroups =
        FieldGroups.buildFieldInfos(
            typeResolver,
            FieldGroups.buildDescriptorGrouper(
                typeResolver, remoteDescriptors, false, descriptor -> descriptor));
    List<RemoteFieldInfo> remoteFields = new ArrayList<>(remoteFieldInfos.size());
    appendRemoteFields(
        remoteFields, remoteFieldGroups.buildInFields, remoteFieldInfosByKey, fieldIds, fields,
        localDescriptors);
    appendRemoteFields(
        remoteFields, remoteFieldGroups.containerFields, remoteFieldInfosByKey, fieldIds, fields,
        localDescriptors);
    appendRemoteFields(
        remoteFields, remoteFieldGroups.userTypeFields, remoteFieldInfosByKey, fieldIds, fields,
        localDescriptors);
    return Collections.unmodifiableList(remoteFields);
  }

  private void appendRemoteFields(
      List<RemoteFieldInfo> remoteFields,
      SerializationFieldInfo[] serializationFieldInfos,
      Map<String, FieldInfo> remoteFieldInfosByKey,
      Map<Short, Integer> fieldIds,
      Map<String, Integer> fields,
      List<Descriptor> localDescriptors) {
    for (SerializationFieldInfo serializationFieldInfo : serializationFieldInfos) {
      Descriptor descriptor = serializationFieldInfo.descriptor;
      FieldInfo fieldInfo = remoteFieldInfosByKey.get(remoteFieldKey(descriptor));
      if (fieldInfo == null) {
        throw new IllegalStateException("Missing remote field metadata for " + descriptor);
      }
      int matchedId = matchField(fieldInfo, fieldIds, fields);
      Descriptor localDescriptor =
          matchedId == UNKNOWN_FIELD ? null : localDescriptors.get(matchedId);
      remoteFields.add(
          new RemoteFieldInfo(
              typeResolver,
              matchedId,
              fieldInfo,
              descriptor,
              serializationFieldInfo,
              localDescriptor));
    }
  }

  private SerializationFieldInfo[] buildLocalFieldsById(List<Descriptor> descriptors) {
    FieldGroups fieldGroups = buildFieldGroups(descriptors);
    SerializationFieldInfo[] allFields = fieldGroups.allFields;
    int[] ids = localFieldIds(allFields, descriptors);
    SerializationFieldInfo[] fieldsById = new SerializationFieldInfo[descriptors.size()];
    for (int i = 0; i < allFields.length; i++) {
      fieldsById[ids[i]] = allFields[i];
    }
    return fieldsById;
  }

  private int matchField(
      FieldInfo fieldInfo, Map<Short, Integer> fieldIds, Map<String, Integer> fields) {
    Integer localId;
    if (fieldInfo.hasFieldId()) {
      localId = fieldIds.get(fieldInfo.getFieldId());
    } else {
      String key = fieldInfo.getDefinedClass() + "." + fieldInfo.getFieldName();
      localId = fields.get(key);
      if (localId == null && typeResolver.isCrossLanguage()) {
        localId =
            fields.get(
                fieldInfo.getDefinedClass()
                    + "."
                    + StringUtils.lowerCamelToLowerUnderscore(fieldInfo.getFieldName()));
      }
    }
    return localId == null ? UNKNOWN_FIELD : localId;
  }

  private static String fieldKey(Descriptor descriptor) {
    return descriptor.getDeclaringClass() + "." + descriptor.getName();
  }

  private static void putRemoteFieldInfo(
      Map<String, FieldInfo> remoteFieldInfosByKey, FieldInfo fieldInfo, Descriptor descriptor) {
    remoteFieldInfosByKey.put(remoteFieldKey(descriptor), fieldInfo);
    if (fieldInfo.hasFieldId()) {
      remoteFieldInfosByKey.put(remoteFieldKey(fieldInfo), fieldInfo);
    }
  }

  private static String remoteFieldKey(FieldInfo fieldInfo) {
    return fieldInfo.hasFieldId()
        ? "id:" + fieldInfo.getFieldId()
        : fieldInfo.getDefinedClass() + "." + fieldInfo.getFieldName();
  }

  private static String remoteFieldKey(Descriptor descriptor) {
    return descriptor.hasForyFieldId()
        ? "id:" + descriptor.getForyFieldId()
        : fieldKey(descriptor);
  }

  /** Remote field metadata consumed by generated compatible read methods. */
  @Internal
  protected static final class RemoteFieldInfo {
    private final int matchedId;
    private final FieldInfo fieldInfo;
    private final Descriptor descriptor;
    private final SerializationFieldInfo serializationFieldInfo;
    private final CompatibleCollectionArrayReader.ReadAction compatibleCollectionArrayReadAction;

    private RemoteFieldInfo(
        TypeResolver typeResolver,
        int matchedId,
        FieldInfo fieldInfo,
        Descriptor descriptor,
        SerializationFieldInfo serializationFieldInfo,
        Descriptor localDescriptor) {
      this.matchedId = matchedId;
      this.fieldInfo = fieldInfo;
      this.descriptor = descriptor;
      this.serializationFieldInfo = serializationFieldInfo;
      this.compatibleCollectionArrayReadAction =
          CompatibleCollectionArrayReader.readAction(typeResolver, fieldInfo, localDescriptor);
    }
  }
}
