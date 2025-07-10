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

import static org.apache.fory.meta.ClassDefEncoder.prependHeader;
import static org.apache.fory.meta.ClassDefEncoder.writePkgName;
import static org.apache.fory.meta.ClassDefEncoder.writeTypeName;
import static org.apache.fory.meta.Encoders.fieldNameEncodingsList;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.fory.Fory;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.meta.ClassDef.FieldInfo;
import org.apache.fory.meta.ClassDef.FieldType;
import org.apache.fory.reflect.ReflectionUtils;
import org.apache.fory.resolver.ClassInfo;
import org.apache.fory.resolver.TypeResolver;
import org.apache.fory.resolver.XtypeResolver;
import org.apache.fory.type.Descriptor;
import org.apache.fory.type.DescriptorGrouper;
import org.apache.fory.type.Types;
import org.apache.fory.util.Preconditions;

/**
 * An encoder which encode {@link ClassDef} into binary. See spec documentation:
 * docs/specification/fory_xlang_serialization_spec.md <a
 * href="https://fory.apache.org/docs/specification/fory_xlang_serialization_spec">...</a>
 */
class TypeDefEncoder {
  /** Build class definition from fields of class. */
  static ClassDef buildTypeDef(Fory fory, Class<?> type) {
    DescriptorGrouper descriptorGrouper =
        fory.getClassResolver()
            .createDescriptorGrouper(
                fory.getClassResolver().getFieldDescriptors(type, true),
                false,
                Function.identity());
    List<Field> fields =
        descriptorGrouper.getSortedDescriptors().stream()
            .map(Descriptor::getField)
            .collect(Collectors.toList());
    return buildClassDefWithFieldInfos(
        fory.getXtypeResolver(), type, buildFieldsInfo(fory.getXtypeResolver(), type, fields));
  }

  static List<FieldInfo> buildFieldsInfo(TypeResolver resolver, Class<?> type, List<Field> fields) {
    return fields.stream()
        .map(
            field ->
                new FieldInfo(
                    type.getName(), field.getName(), ClassDef.buildFieldType(resolver, field)))
        .collect(Collectors.toList());
  }

  static ClassDef buildClassDefWithFieldInfos(
      XtypeResolver resolver, Class<?> type, List<FieldInfo> fieldInfos) {
    fieldInfos = new ArrayList<>(getClassFields(type, fieldInfos).values());
    MemoryBuffer encodeClassDef = encodeClassDef(resolver, type, fieldInfos);
    byte[] classDefBytes = encodeClassDef.getBytes(0, encodeClassDef.writerIndex());
    return new ClassDef(
        Encoders.buildClassSpec(type), fieldInfos, true, encodeClassDef.getInt64(0), classDefBytes);
  }

  static final int SMALL_NUM_FIELDS_THRESHOLD = 0b11111;
  static final int REGISTER_BY_NAME_FLAG = 0b100000;
  static final int FIELD_NAME_SIZE_THRESHOLD = 0b1111;

  // see spec documentation: docs/specification/xlang_serialization_spec.md
  // https://fory.apache.org/docs/specification/fory_xlang_serialization_spec
  static MemoryBuffer encodeClassDef(
      XtypeResolver resolver, Class<?> type, List<FieldInfo> fields) {
    ClassInfo classInfo = resolver.getClassInfo(type);
    Preconditions.checkArgument(
        Types.isStructType(classInfo.getXtypeId()), "%s is not a struct", type);
    MemoryBuffer buffer = MemoryBuffer.newHeapBuffer(128);
    buffer.writeByte(-1); // placeholder for header, update later
    int currentClassHeader = fields.size();
    if (fields.size() >= SMALL_NUM_FIELDS_THRESHOLD) {
      currentClassHeader = SMALL_NUM_FIELDS_THRESHOLD;
      buffer.writeVarUint32(fields.size() - SMALL_NUM_FIELDS_THRESHOLD);
    }
    if (resolver.isRegisteredById(type)) {
      buffer.writeVarUint32(classInfo.getXtypeId());
    } else {
      Preconditions.checkArgument(resolver.isRegisteredByName(type));
      currentClassHeader |= REGISTER_BY_NAME_FLAG;
      String ns = classInfo.decodeNamespace();
      String typename = classInfo.decodeTypeName();
      writePkgName(buffer, ns);
      writeTypeName(buffer, typename);
    }
    buffer.putByte(0, currentClassHeader);
    writeFieldsInfo(resolver, buffer, fields);

    byte[] compressed =
        resolver
            .getFory()
            .getMetaCompressor()
            .compress(buffer.getHeapMemory(), 0, buffer.writerIndex());
    boolean isCompressed = false;
    if (compressed.length < buffer.writerIndex()) {
      isCompressed = true;
      buffer = MemoryBuffer.fromByteArray(compressed);
      buffer.writerIndex(compressed.length);
    }
    return prependHeader(buffer, isCompressed, true);
  }

  static Map<String, FieldInfo> getClassFields(Class<?> type, List<FieldInfo> fieldsInfo) {
    Map<String, FieldInfo> sortedClassFields = new LinkedHashMap<>();
    Map<String, List<FieldInfo>> classFields = ClassDefEncoder.groupClassFields(fieldsInfo);
    for (Class<?> clz : ReflectionUtils.getAllClasses(type, true)) {
      List<FieldInfo> fieldInfos = classFields.get(clz.getName());
      if (fieldInfos != null) {
        for (FieldInfo fieldInfo : fieldInfos) {
          sortedClassFields.put(fieldInfo.getFieldName(), fieldInfo);
        }
      }
    }
    return sortedClassFields;
  }

  /** Write field type and name info. Every field info format: `header + type info + field name` */
  static void writeFieldsInfo(XtypeResolver resolver, MemoryBuffer buffer, List<FieldInfo> fields) {
    for (FieldInfo fieldInfo : fields) {
      FieldType fieldType = fieldInfo.getFieldType();
      // header: 2 bits field name encoding + 4 bits size + nullability flag + ref tracking flag
      int header = ((fieldType.trackingRef() ? 1 : 0));
      header |= fieldType.nullable() ? 0b10 : 0b00;
      int size, encodingFlags;
      byte[] encoded = null;
      if (fieldInfo.hasTag()) {
        size = fieldInfo.getTag();
        encodingFlags = 3;
      } else {
        MetaString metaString = Encoders.encodeFieldName(fieldInfo.getFieldName());
        // Encoding `UTF8/ALL_TO_LOWER_SPECIAL/LOWER_UPPER_DIGIT_SPECIAL/TAG_ID`
        encodingFlags = fieldNameEncodingsList.indexOf(metaString.getEncoding());
        encoded = metaString.getBytes();
        size = (encoded.length - 1);
      }
      header |= (byte) (encodingFlags << 6);
      boolean bigSize = size >= FIELD_NAME_SIZE_THRESHOLD;
      if (bigSize) {
        header |= 0b00111100;
        buffer.writeByte(header);
        buffer.writeVarUint32Small7(size - FIELD_NAME_SIZE_THRESHOLD);
      } else {
        header |= (size << 2);
        buffer.writeByte(header);
      }
      fieldType.xwrite(buffer, false);
      // write field name
      if (!fieldInfo.hasTag()) {
        buffer.writeBytes(encoded);
      }
    }
  }
}
