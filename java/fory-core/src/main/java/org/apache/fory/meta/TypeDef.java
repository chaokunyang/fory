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

package org.apache.fory.meta;

import static org.apache.fory.meta.NativeTypeDefEncoder.buildDescriptors;

import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.fory.builder.MetaSharedCodecBuilder;
import org.apache.fory.config.ForyBuilder;
import org.apache.fory.exception.DeserializationException;
import org.apache.fory.logging.Logger;
import org.apache.fory.logging.LoggerFactory;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.platform.UnsafeOps;
import org.apache.fory.reflect.ReflectionUtils;
import org.apache.fory.resolver.ClassResolver;
import org.apache.fory.resolver.SharedRegistry;
import org.apache.fory.resolver.TypeResolver;
import org.apache.fory.resolver.XtypeResolver;
import org.apache.fory.serializer.MetaSharedSerializer;
import org.apache.fory.type.Descriptor;
import org.apache.fory.type.Types;
import org.apache.fory.util.Preconditions;
import org.apache.fory.util.StringUtils;

/**
 * Serializable class definition to be sent to other process. So if sender peer and receiver peer
 * has different class definition for same class, such as add/remove fields, we can use this
 * definition to create different serializer to support back/forward compatibility.
 *
 * <p>Note that:
 * <li>If a class is already registered, this definition will contain class id only.
 * <li>Sending class definition is not cheap, should be sent with some kind of meta share mechanism.
 * <li>{@link ObjectStreamClass} doesn't contain any non-primitive field type info, which is not
 *     enough to create serializer in receiver.
 *
 * @see MetaSharedCodecBuilder
 * @see ForyBuilder#withCompatible(boolean)
 * @see MetaSharedSerializer
 * @see ForyBuilder#withMetaShare
 * @see ReflectionUtils#getFieldOffset
 */
public class TypeDef implements Serializable {
  private static final Logger LOG = LoggerFactory.getLogger(TypeDef.class);

  static final int COMPRESS_META_FLAG = 0b1 << 8;
  static final long RESERVED_META_FLAGS = 0b111L << 9;
  // low 8 bits
  static final int META_SIZE_MASKS = 0xff;
  static final int NUM_HASH_BITS = 52;

  // TODO use field offset to sort field, which will hit l1-cache more. Since
  // `objectFieldOffset` is not part of jvm-specification, it may change between different jdk
  // vendor. But the deserialization peer use the class definition to create deserializer, it's OK
  // even field offset or fields order change between jvm process.
  public static final Comparator<Field> FIELD_COMPARATOR =
      (f1, f2) -> {
        long offset1 = UnsafeOps.objectFieldOffset(f1);
        long offset2 = UnsafeOps.objectFieldOffset(f2);
        long diff = offset1 - offset2;
        if (diff != 0) {
          return (int) diff;
        } else {
          if (!f1.equals(f2)) {
            LOG.warn(
                "Field {} has same offset with {}, please an issue with jdk info to fory", f1, f2);
          }
          int compare = f1.getDeclaringClass().getName().compareTo(f2.getName());
          if (compare != 0) {
            return compare;
          }
          return f1.getName().compareTo(f2.getName());
        }
      };

  private final ClassSpec classSpec;
  private final List<FieldInfo> fieldsInfo;
  // Unique id for class def. If class def are same between processes, then the id will
  // be same too.
  private final long id;
  private final byte[] encoded;

  TypeDef(ClassSpec classSpec, List<FieldInfo> fieldsInfo, long id, byte[] encoded) {
    this.classSpec = classSpec;
    this.fieldsInfo = fieldsInfo;
    this.id = id;
    this.encoded = encoded;
  }

  public static void skipTypeDef(MemoryBuffer buffer, long id) {
    // Header-cache hits intentionally treat the current body as opaque bytes and skip by the size
    // in the current header. Parsed TypeDefs are published to the cache only after successful body
    // parse and 52-bit metadata-hash validation; cache hits must not reparse or rehash that body.
    int size = (int) (id & META_SIZE_MASKS);
    if (size == META_SIZE_MASKS) {
      int extendedSize = buffer.readVarUInt32Small14();
      if (extendedSize < 0 || extendedSize > Integer.MAX_VALUE - size) {
        throw new DeserializationException("Invalid TypeDef metadata size " + extendedSize);
      }
      size += extendedSize;
    }
    buffer.checkReadableBytes(size);
    buffer.increaseReaderIndex(size);
  }

  /**
   * Returns class name.
   *
   * @see Class#getName()
   */
  public String getClassName() {
    return classSpec.entireClassName;
  }

  public ClassSpec getClassSpec() {
    return classSpec;
  }

  /** Contain all fields info including all parent classes. */
  public List<FieldInfo> getFieldsInfo() {
    return fieldsInfo;
  }

  /**
   * Returns an unique id for class def. If class def are same between processes, then the id will
   * be same too.
   */
  public long getId() {
    return id;
  }

  public byte[] getEncoded() {
    return encoded;
  }

  public int getFieldCount() {
    return fieldsInfo.size();
  }

  public boolean isNamed() {
    return classSpec.typeId < 0 || Types.isNamedType(classSpec.typeId);
  }

  public boolean isCompatible() {
    if (classSpec.typeId < 0) {
      return false;
    }
    return classSpec.typeId == Types.COMPATIBLE_STRUCT
        || classSpec.typeId == Types.NAMED_COMPATIBLE_STRUCT;
  }

  public boolean isStructSchemaKind() {
    return Types.isStructType(classSpec.typeId);
  }

  public int getUserTypeId() {
    Preconditions.checkArgument(!isNamed(), "Named types don't have user type id");
    return classSpec.userTypeId;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TypeDef typeDef = (TypeDef) o;
    return id == typeDef.id
        && Objects.equals(classSpec, typeDef.classSpec)
        && Objects.equals(fieldsInfo, typeDef.fieldsInfo);
  }

  @Override
  public int hashCode() {
    return Objects.hash(classSpec.entireClassName, fieldsInfo, id);
  }

  @Override
  public String toString() {
    return "TypeDef{"
        + "className='"
        + classSpec.entireClassName
        + '\''
        + ", fieldsInfo="
        + fieldsInfo
        + ", id="
        + id
        + '}';
  }

  /**
   * Compute diff between this (decoded/remote) TypeDef and a local TypeDef. Returns a string
   * describing the differences, or null if they are identical.
   */
  public String computeDiff(TypeDef localDef) {
    if (localDef == null) {
      return "Local TypeDef is null (type not registered locally)";
    }
    StringBuilder diff = new StringBuilder();

    // Compare class names
    if (!Objects.equals(this.classSpec.entireClassName, localDef.classSpec.entireClassName)) {
      diff.append("  className: remote=")
          .append(this.classSpec.entireClassName)
          .append(", local=")
          .append(localDef.classSpec.entireClassName)
          .append("\n");
    }

    // Build field maps for comparison
    Map<String, FieldInfo> remoteFields = new HashMap<>();
    for (FieldInfo fi : this.fieldsInfo) {
      remoteFields.put(fieldKey(fi), fi);
    }
    Map<String, FieldInfo> localFields = new HashMap<>();
    for (FieldInfo fi : localDef.fieldsInfo) {
      localFields.put(fieldKey(fi), fi);
    }

    // Find fields only in remote
    for (String fieldKey : remoteFields.keySet()) {
      if (!localFields.containsKey(fieldKey)) {
        FieldInfo remoteField = remoteFields.get(fieldKey);
        diff.append("  field '")
            .append(fieldLabel(remoteField))
            .append("': only in remote, type=")
            .append(remoteField.getFieldType())
            .append("\n");
      }
    }

    // Find fields only in local
    for (String fieldKey : localFields.keySet()) {
      if (!remoteFields.containsKey(fieldKey)) {
        FieldInfo localField = localFields.get(fieldKey);
        diff.append("  field '")
            .append(fieldLabel(localField))
            .append("': only in local, type=")
            .append(localField.getFieldType())
            .append("\n");
      }
    }

    // Compare common fields
    for (String fieldKey : remoteFields.keySet()) {
      if (localFields.containsKey(fieldKey)) {
        FieldInfo remoteField = remoteFields.get(fieldKey);
        FieldInfo localField = localFields.get(fieldKey);
        if (!Objects.equals(remoteField.getFieldType(), localField.getFieldType())) {
          diff.append("  field '")
              .append(fieldLabel(remoteField))
              .append("': type mismatch, remote=")
              .append(remoteField.getFieldType())
              .append(", local=")
              .append(localField.getFieldType())
              .append("\n");
        }
      }
    }

    // Compare field order
    if (this.fieldsInfo.size() == localDef.fieldsInfo.size()) {
      boolean orderDifferent = false;
      for (int i = 0; i < this.fieldsInfo.size(); i++) {
        if (!Objects.equals(
            fieldKey(this.fieldsInfo.get(i)), fieldKey(localDef.fieldsInfo.get(i)))) {
          orderDifferent = true;
          break;
        }
      }
      if (orderDifferent) {
        diff.append("  field order differs:\n");
        diff.append("    remote: [");
        for (int i = 0; i < this.fieldsInfo.size(); i++) {
          if (i > 0) {
            diff.append(", ");
          }
          diff.append(fieldLabel(this.fieldsInfo.get(i)));
        }
        diff.append("]\n");
        diff.append("    local:  [");
        for (int i = 0; i < localDef.fieldsInfo.size(); i++) {
          if (i > 0) {
            diff.append(", ");
          }
          diff.append(fieldLabel(localDef.fieldsInfo.get(i)));
        }
        diff.append("]\n");
      }
    }

    return diff.length() > 0 ? diff.toString() : null;
  }

  private static String fieldKey(FieldInfo fieldInfo) {
    if (fieldInfo.hasFieldId()) {
      return "id:" + fieldInfo.getFieldId();
    }
    return "name:" + fieldInfo.getFieldName();
  }

  private static String fieldLabel(FieldInfo fieldInfo) {
    if (fieldInfo.hasFieldId()) {
      String name = fieldInfo.getFieldName();
      if (name == null || name.startsWith("$tag")) {
        return "id=" + fieldInfo.getFieldId();
      }
      return name + "(id=" + fieldInfo.getFieldId() + ")";
    }
    return fieldInfo.getFieldName();
  }

  /** Write class definition to buffer. */
  public void writeTypeDef(MemoryBuffer buffer) {
    buffer.writeBytes(encoded, 0, encoded.length);
  }

  /** Read class definition from buffer. */
  public static TypeDef readTypeDef(TypeResolver resolver, MemoryBuffer buffer) {
    if (resolver.isCrossLanguage()) {
      return TypeDefDecoder.decodeTypeDef((XtypeResolver) resolver, buffer, buffer.readInt64());
    }
    return NativeTypeDefDecoder.decodeTypeDef((ClassResolver) resolver, buffer, buffer.readInt64());
  }

  /** Read class definition from buffer. */
  public static TypeDef readTypeDef(TypeResolver resolver, MemoryBuffer buffer, long header) {
    if (resolver.isCrossLanguage()) {
      return TypeDefDecoder.decodeTypeDef((XtypeResolver) resolver, buffer, header);
    }
    return NativeTypeDefDecoder.decodeTypeDef((ClassResolver) resolver, buffer, header);
  }

  /**
   * Consolidate fields of <code>typeDef</code> with <code>cls</code>. If some field exists in
   * <code>cls</code> but not in <code>typeDef</code>, it won't be returned in final collection. If
   * some field exists in <code>typeDef</code> but not in <code> cls</code>, it will be added to
   * final collection.
   *
   * @param cls class load in current process.
   */
  public List<Descriptor> getDescriptors(TypeResolver resolver, Class<?> cls) {
    SharedRegistry sharedRegistry = resolver.getSharedRegistry();
    return sharedRegistry.getOrCreateTypeDefDescriptors(
        this, cls, () -> buildDescriptors(resolver, cls));
  }

  public List<Descriptor> getDescriptors(
      TypeResolver resolver, Class<?> cls, Collection<Descriptor> localDescriptors) {
    return buildDescriptors(resolver, cls, localDescriptors);
  }

  private List<Descriptor> buildDescriptors(TypeResolver resolver, Class<?> cls) {
    Collection<Descriptor> fieldDescriptors = resolver.getFieldDescriptors(cls, true);
    return buildDescriptors(resolver, cls, fieldDescriptors);
  }

  private List<Descriptor> buildDescriptors(
      TypeResolver resolver, Class<?> cls, Collection<Descriptor> fieldDescriptors) {
    Map<String, Descriptor> descriptorsMap = new HashMap<>();
    Map<Short, Descriptor> fieldIdToDescriptorMap = new HashMap<>();

    for (Descriptor descriptor : fieldDescriptors) {
      String fullName = descriptor.getDeclaringClass() + "." + descriptor.getName();
      if (descriptorsMap.put(fullName, descriptor) != null) {
        throw new IllegalStateException("Duplicate key");
      }
      if (descriptor.hasForyFieldId()) {
        int fieldId = descriptor.getForyFieldId();
        if (fieldIdToDescriptorMap.containsKey((short) fieldId)) {
          throw new IllegalArgumentException(
              "Duplicate field id "
                  + fieldId
                  + " for field "
                  + descriptor.getName()
                  + " in class "
                  + cls.getName());
        }
        fieldIdToDescriptorMap.put((short) fieldId, descriptor);
      }
    }
    List<Descriptor> descriptors = new ArrayList<>(fieldsInfo.size());
    boolean isXlang = resolver.isCrossLanguage();
    for (FieldInfo fieldInfo : fieldsInfo) {
      Descriptor descriptor;
      if (fieldInfo.hasFieldId()) {
        descriptor = fieldIdToDescriptorMap.get(fieldInfo.getFieldId());
      } else {
        String fieldName = fieldInfo.getFieldName();
        String definedClass = fieldInfo.getDefinedClass();
        descriptor = descriptorsMap.get(definedClass + "." + fieldName);
        if (descriptor == null && isXlang) {
          descriptor =
              descriptorsMap.get(
                  definedClass + "." + StringUtils.lowerCamelToLowerUnderscore(fieldName));
        }
      }
      descriptors.add(fieldInfo.toDescriptor(resolver, descriptor));
    }
    return descriptors;
  }

  public static TypeDef buildTypeDef(TypeResolver resolver, Class<?> cls) {
    return buildTypeDef(resolver, cls, true);
  }

  public static TypeDef buildTypeDef(TypeResolver resolver, Class<?> cls, boolean resolveParent) {
    if (resolver.isCrossLanguage()) {
      return TypeDefEncoder.buildTypeDef((XtypeResolver) resolver, cls);
    }
    return NativeTypeDefEncoder.buildTypeDefFromDescriptors(
        (ClassResolver) resolver,
        cls,
        NativeTypeDefEncoder.buildDescriptors(resolver, cls, resolveParent));
  }

  /** Build class definition from fields of class. */
  static TypeDef buildTypeDef(ClassResolver classResolver, Class<?> type, List<Field> fields) {
    return NativeTypeDefEncoder.buildTypeDef(classResolver, type, fields);
  }

  public TypeDef replaceRootClassTo(TypeResolver resolver, Class<?> targetCls) {
    String name = targetCls.getName();
    List<FieldInfo> fieldInfos =
        fieldsInfo.stream()
            .map(
                fieldInfo -> {
                  if (fieldInfo.definedClass.equals(classSpec.entireClassName)) {
                    return new FieldInfo(
                        name, fieldInfo.fieldName, fieldInfo.fieldType, fieldInfo.fieldId);
                  } else {
                    return fieldInfo;
                  }
                })
            .collect(Collectors.toList());
    // Keep the remote class spec/id/encoded metadata. Compatible readers still need the remote
    // schema identity to rebuild descriptor order; only root field ownership is rewritten so local
    // field matching by declaring class keeps the existing target-class semantics.
    return new TypeDef(classSpec, fieldInfos, id, encoded);
  }
}
