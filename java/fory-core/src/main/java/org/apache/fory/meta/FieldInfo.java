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

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Objects;
import org.apache.fory.reflect.TypeRef;
import org.apache.fory.resolver.TypeResolver;
import org.apache.fory.serializer.converter.FieldConverter;
import org.apache.fory.serializer.converter.FieldConverters;
import org.apache.fory.type.Descriptor;
import org.apache.fory.type.DescriptorBuilder;
import org.apache.fory.type.Types;
import org.apache.fory.util.StringUtils;

/**
 * FieldInfo contains all necessary info of a field to execute serialization/deserialization logic.
 */
public final class FieldInfo implements Serializable {
  /** where are current field defined. */
  final String definedClass;

  /** Name of a field. */
  final String fieldName;

  final FieldTypes.FieldType fieldType;

  /** Field ID for schema evolution, -1 means no field ID (use field name). */
  final short fieldId;

  FieldInfo(String definedClass, String fieldName, FieldTypes.FieldType fieldType) {
    this(definedClass, fieldName, fieldType, (short) -1);
  }

  FieldInfo(String definedClass, String fieldName, FieldTypes.FieldType fieldType, short fieldId) {
    this.definedClass = definedClass;
    this.fieldName = fieldName;
    this.fieldType = fieldType;
    this.fieldId = fieldId;
  }

  /** Returns classname of current field defined. */
  public String getDefinedClass() {
    return definedClass;
  }

  /** Returns name of current field. */
  public String getFieldName() {
    return fieldName;
  }

  /** Returns whether field is annotated by an unsigned int id. */
  public boolean hasFieldId() {
    return fieldId >= 0;
  }

  /** Returns annotated field-id for the field. */
  public short getFieldId() {
    return fieldId;
  }

  /** Returns type of current field. */
  public FieldTypes.FieldType getFieldType() {
    return fieldType;
  }

  /**
   * Convert this field into a {@link Descriptor}, the corresponding {@link Field} field will be
   * null. Don't invoke this method if class does have <code>fieldName</code> field. In such case,
   * reflection should be used to get the descriptor.
   */
  Descriptor toDescriptor(TypeResolver resolver, Descriptor descriptor) {
    TypeRef<?> declared = descriptor != null ? descriptor.getTypeRef() : null;
    TypeRef<?> typeRef = fieldType.toTypeToken(resolver, declared);
    String typeName = fieldType.getTypeName(resolver, typeRef);
    if (fieldType instanceof FieldTypes.RegisteredFieldType) {
      if (!Types.isPrimitiveType(fieldType.xtypeId)) {
        typeName = String.valueOf(((FieldTypes.RegisteredFieldType) fieldType).getClassId());
      }
    }
    // Get nullable and trackingRef from remote FieldType - these are what the remote peer
    // used when serializing, so we must respect them when deserializing
    boolean remoteNullable = fieldType.nullable();
    boolean remoteTrackingRef = fieldType.trackingRef();

    if (descriptor != null) {
      if (remoteNullable == descriptor.isNullable()
          && remoteTrackingRef == descriptor.isTrackingRef()
          && typeRef.equals(descriptor.getTypeRef())) {
        if (typeName.equals(descriptor.getTypeName())) {
          return descriptor;
        }
      }
      Class<?> rawType = typeRef.getRawType();
      if (FieldTypes.useFieldType(rawType, descriptor)) {
        return new DescriptorBuilder(descriptor)
            .typeName(typeName)
            .trackingRef(remoteTrackingRef)
            .nullable(remoteNullable)
            .typeRef(typeRef)
            .build();
      }
      DescriptorBuilder builder =
          new DescriptorBuilder(descriptor)
              .typeName(typeName)
              .trackingRef(remoteTrackingRef)
              .nullable(remoteNullable)
              .typeRef(typeRef)
              .type(rawType);
      // Local field exists - check if we need to update nullable/trackingRef
      boolean typeMismatch = !typeRef.equals(declared);
      if (typeMismatch) {
        if (!declared.getRawType().isAssignableFrom(rawType)) {
          // Check if both are primitives (or boxed primitives) of the same underlying type
          // For example: Integer <-> int is OK, but Long -> int is NOT OK
          Class<?> declaredPrimitive = declared.unwrap().getRawType();
          Class<?> remotePrimitive = typeRef.unwrap().getRawType();
          boolean bothPrimitives = declaredPrimitive.isPrimitive() && remotePrimitive.isPrimitive();
          boolean samePrimitiveType = bothPrimitives && declaredPrimitive.equals(remotePrimitive);
          // Set field to null if types are incompatible (not the same primitive type)
          if (!samePrimitiveType) {
            builder.field(null);
          }
        }
        FieldConverter<?> converter = FieldConverters.getConverter(rawType, descriptor.getField());
        if (converter != null) {
          builder.fieldConverter(converter);
        }
      }
      return builder.build();
    }
    // This field doesn't exist in peer class, so any legal modifier will be OK.
    // Use constant instead of reflection to avoid GraalVM native image issues.
    int stubModifiers = Modifier.PRIVATE | Modifier.FINAL;
    return new Descriptor(
        typeRef,
        typeName,
        fieldName,
        stubModifiers,
        definedClass,
        remoteTrackingRef,
        remoteNullable);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    FieldInfo fieldInfo = (FieldInfo) o;
    return fieldId == fieldInfo.fieldId
        && Objects.equals(definedClass, fieldInfo.definedClass)
        && Objects.equals(fieldName, fieldInfo.fieldName)
        && Objects.equals(fieldType, fieldInfo.fieldType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(definedClass, fieldName, fieldType, fieldId);
  }

  @Override
  public String toString() {
    return "FieldInfo{"
        + "fieldName='"
        + StringUtils.lowerCamelToLowerUnderscore(fieldName)
        + '\''
        + ", definedClass='"
        + definedClass
        + '\''
        + (fieldId >= 0 ? ", fieldID=" + fieldId : "")
        + ", fieldType="
        + fieldType
        + '}';
  }
}
