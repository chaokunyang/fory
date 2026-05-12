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
import org.apache.fory.exception.DeserializationException;
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
    setSerializerIfAbsent(typeResolver, (Class<T>) type);
    this.typeDef = null;
    this.remoteFields = Collections.emptyList();
    this.localFieldsById = new SerializationFieldInfo[0];
  }

  @SuppressWarnings("unchecked")
  protected StaticGeneratedStructSerializer(
      TypeResolver typeResolver, Class<?> type, TypeDef typeDef, List<Descriptor> descriptors) {
    this(typeResolver, type, typeDef, descriptors, null);
  }

  @SuppressWarnings("unchecked")
  protected StaticGeneratedStructSerializer(
      TypeResolver typeResolver,
      Class<?> type,
      TypeDef typeDef,
      List<Descriptor> descriptors,
      Class<?> remoteDescriptorClass) {
    super(typeResolver, (Class<T>) type);
    setSerializerIfAbsent(typeResolver, (Class<T>) type);
    List<Descriptor> runtimeDescriptors = runtimeDescriptors(descriptors);
    this.typeDef = typeDef;
    this.remoteFields =
        typeDef == null
            ? Collections.emptyList()
            : buildRemoteFields(typeDef, runtimeDescriptors, remoteDescriptorClass);
    this.localFieldsById = buildLocalFieldsById(runtimeDescriptors);
  }

  private void setSerializerIfAbsent(TypeResolver typeResolver, Class<T> type) {
    if (!typeResolver.isCrossLanguage() || typeResolver.getTypeInfo(type, false) != null) {
      // Field-group construction resolves monomorphic field serializers. A generated serializer can
      // therefore encounter its own type before the subclass constructor has finished, just like
      // ObjectSerializer. Install this instance early so recursive fields reuse it instead of
      // constructing another serializer for the same type.
      typeResolver.setSerializerIfAbsent(type, this);
    }
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

  protected final FieldGroups buildLocalFieldGroups(List<Descriptor> descriptors) {
    if (!typeResolver.isShareMeta()) {
      return buildFieldGroups(descriptors);
    }
    // Meta-share writers use the local TypeDef-reified descriptor grouping, matching
    // ObjectSerializer. The constructor TypeDef may be a remote schema for compatible reads, so it
    // must not own local field access ordering.
    DescriptorGrouper grouper =
        typeResolver.createDescriptorGrouper(typeResolver.getTypeDef(type, true), type);
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
    // Some schema-built-in fields still use container-shaped Java accessors, such as
    // @ArrayType List<T>. The override owns the accessor-to-payload conversion.
    if (fieldInfo.containerSerializerOverride != null) {
      writeContainerFieldValue(writeContext, fieldInfo, fieldValue);
      return;
    }
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
    // See writeBuildInFieldValue: built-in schema groups can still need container conversion.
    if (fieldInfo.containerSerializerOverride != null) {
      return readContainerFieldValue(readContext, fieldInfo);
    }
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
    try {
      FieldSkipper.skipField(readContext, typeResolver, refReader, fieldInfo, buffer);
    } catch (RuntimeException e) {
      throw new DeserializationException(
          "Failed to skip remote field " + fieldInfo.descriptor.getName(), e);
    }
  }

  protected final int matchedId(RemoteFieldInfo remoteField) {
    return remoteField.matchedId;
  }

  protected final SerializationFieldInfo localFieldInfo(int matchedId) {
    return localFieldsById[matchedId];
  }

  protected final boolean canReadRemoteField(
      RemoteFieldInfo remoteField, SerializationFieldInfo localFieldInfo) {
    if (remoteField.incompatibleCollectionArrayMatch) {
      throw new DeserializationException(
          "Cannot read remote field "
              + remoteField.descriptor.getName()
              + " as local field "
              + localFieldInfo.descriptor.getName()
              + ": compatible list/array adaptation requires a matching non-null primitive element"
              + " schema and does not apply recursively");
    }
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

  protected final void debugWriteField(
      String stage, SerializationFieldInfo fieldInfo, WriteContext writeContext) {
    if (!org.apache.fory.util.Utils.DEBUG_OUTPUT_ENABLED) {
      return;
    }
    MemoryBuffer buffer = writeContext.getBuffer();
    System.out.println(
        "[ForyStruct] "
            + stage
            + " write "
            + type.getName()
            + "."
            + fieldInfo.descriptor.getName()
            + " localId="
            + localFieldId(fieldInfo.descriptor)
            + " type="
            + fieldInfo.typeRef
            + " writerIndex="
            + buffer.writerIndex());
  }

  protected final void debugReadField(
      String stage, SerializationFieldInfo fieldInfo, ReadContext readContext) {
    if (!org.apache.fory.util.Utils.DEBUG_OUTPUT_ENABLED) {
      return;
    }
    MemoryBuffer buffer = readContext.getBuffer();
    System.out.println(
        "[ForyStruct] "
            + stage
            + " read "
            + type.getName()
            + "."
            + fieldInfo.descriptor.getName()
            + " localId="
            + localFieldId(fieldInfo.descriptor)
            + " type="
            + fieldInfo.typeRef
            + " readerIndex="
            + buffer.readerIndex());
  }

  protected final void debugRemoteReadField(
      String stage, RemoteFieldInfo remoteField, ReadContext readContext) {
    if (!org.apache.fory.util.Utils.DEBUG_OUTPUT_ENABLED) {
      return;
    }
    MemoryBuffer buffer = readContext.getBuffer();
    System.out.println(
        "[ForyStruct] "
            + stage
            + " compatible read "
            + type.getName()
            + " remote="
            + remoteField.fieldInfo.getDefinedClass()
            + "."
            + remoteField.fieldInfo.getFieldName()
            + " remoteFieldId="
            + remoteField.fieldInfo.getFieldId()
            + " matchedId="
            + remoteField.matchedId
            + " descriptor="
            + remoteField.descriptor.getName()
            + " type="
            + remoteField.serializationFieldInfo.typeRef
            + " readerIndex="
            + buffer.readerIndex());
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
      TypeDef remoteTypeDef, List<Descriptor> localDescriptors, Class<?> generatedRemoteClass) {
    Class<?> remoteDescriptorClass = remoteDescriptorClass(remoteTypeDef, generatedRemoteClass);
    List<FieldInfo> remoteFieldInfos = remoteTypeDef.getFieldsInfo();
    List<Descriptor> remoteDescriptors =
        remoteTypeDef.getDescriptors(typeResolver, remoteDescriptorClass);
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
    // Keep compatible-read descriptor ordering owned by TypeResolver, matching the sorted
    // DescriptorGrouper order used by ObjectCodecBuilder and MetaSharedCodecBuilder. FieldGroups
    // may regroup descriptors for helper ownership, so it must not drive remote payload order.
    List<Descriptor> remoteDescriptorsInWireOrder =
        typeResolver
            .createDescriptorGrouper(remoteTypeDef, remoteDescriptorClass)
            .getSortedDescriptors();
    List<RemoteFieldInfo> remoteFields = new ArrayList<>(remoteFieldInfos.size());
    appendRemoteFields(
        remoteFields,
        remoteDescriptorsInWireOrder,
        remoteFieldInfosByKey,
        fieldIds,
        fields,
        localDescriptors);
    return Collections.unmodifiableList(remoteFields);
  }

  private Class<?> remoteDescriptorClass(TypeDef remoteTypeDef, Class<?> generatedRemoteClass) {
    if (generatedRemoteClass != null) {
      // Native TypeDefs for registered classes carry the registered id, so a reader that binds the
      // same id to an evolved class decodes the TypeDef as the local class. Static-compatible
      // codegen may still know the writer-side class; use it to preserve descriptor-only details
      // such as primitive-list carrier raw types while keeping wire order in
      // createDescriptorGrouper.
      return generatedRemoteClass;
    }
    String className = remoteTypeDef.getClassName();
    if (className.equals(type.getName())) {
      return type;
    }
    ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
    try {
      return Class.forName(className, false, contextClassLoader);
    } catch (ClassNotFoundException | LinkageError e) {
      try {
        return Class.forName(className, false, type.getClassLoader());
      } catch (ClassNotFoundException | LinkageError ignored) {
        return type;
      }
    }
  }

  private void appendRemoteFields(
      List<RemoteFieldInfo> remoteFields,
      List<Descriptor> remoteDescriptorsInWireOrder,
      Map<String, FieldInfo> remoteFieldInfosByKey,
      Map<Short, Integer> fieldIds,
      Map<String, Integer> fields,
      List<Descriptor> localDescriptors) {
    for (Descriptor descriptor : remoteDescriptorsInWireOrder) {
      FieldInfo fieldInfo = remoteFieldInfosByKey.get(remoteFieldKey(descriptor));
      if (fieldInfo == null) {
        throw new IllegalStateException("Missing remote field metadata for " + descriptor);
      }
      SerializationFieldInfo serializationFieldInfo =
          FieldGroups.buildFieldInfo(typeResolver, descriptor);
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

  private static int localFieldId(Descriptor descriptor) {
    return descriptor.hasForyFieldId() ? descriptor.getForyFieldId() : -1;
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
    return descriptor.hasForyFieldId() ? "id:" + descriptor.getForyFieldId() : fieldKey(descriptor);
  }

  /** Remote field metadata consumed by generated compatible read methods. */
  @Internal
  protected static final class RemoteFieldInfo {
    private final int matchedId;
    private final FieldInfo fieldInfo;
    private final Descriptor descriptor;
    private final SerializationFieldInfo serializationFieldInfo;
    private final CompatibleCollectionArrayReader.ReadAction compatibleCollectionArrayReadAction;
    private final boolean incompatibleCollectionArrayMatch;

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
      this.incompatibleCollectionArrayMatch =
          CompatibleCollectionArrayReader.incompatibleCollectionArrayMatch(
              typeResolver, fieldInfo, localDescriptor);
    }
  }
}
