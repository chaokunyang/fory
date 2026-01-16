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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.fory.Fory;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.memory.Platform;
import org.apache.fory.reflect.FieldAccessor;
import org.apache.fory.reflect.ObjectCreator;
import org.apache.fory.reflect.ObjectCreators;
import org.apache.fory.reflect.ReflectionUtils;
import org.apache.fory.reflect.TypeRef;
import org.apache.fory.resolver.ClassResolver;
import org.apache.fory.resolver.RefMode;
import org.apache.fory.resolver.RefResolver;
import org.apache.fory.resolver.TypeResolver;
import org.apache.fory.serializer.FieldGroups.SerializationFieldInfo;
import org.apache.fory.type.Descriptor;
import org.apache.fory.type.DescriptorGrouper;
import org.apache.fory.type.DispatchId;
import org.apache.fory.type.Generics;
import org.apache.fory.logging.Logger;
import org.apache.fory.logging.LoggerFactory;
import org.apache.fory.util.record.RecordComponent;
import org.apache.fory.util.record.RecordInfo;
import org.apache.fory.util.record.RecordUtils;

public abstract class AbstractObjectSerializer<T> extends Serializer<T> {
  private static final Logger LOG = LoggerFactory.getLogger(AbstractObjectSerializer.class);
  protected final RefResolver refResolver;
  protected final ClassResolver classResolver;
  protected final TypeResolver typeResolver;
  protected final boolean isRecord;
  protected final ObjectCreator<T> objectCreator;
  private SerializationFieldInfo[] fieldInfos;
  private RecordInfo copyRecordInfo;

  public AbstractObjectSerializer(Fory fory, Class<T> type) {
    this(fory, type, ObjectCreators.getObjectCreator(type));
  }

  public AbstractObjectSerializer(Fory fory, Class<T> type, ObjectCreator<T> objectCreator) {
    super(fory, type);
    this.refResolver = fory.getRefResolver();
    this.classResolver = fory.getClassResolver();
    this.typeResolver = fory._getTypeResolver();
    this.isRecord = RecordUtils.isRecord(type);
    this.objectCreator = objectCreator;
  }

  /**
   * Write field value to buffer by reading from the object via fieldAccessor. Handles primitive
   * types, unsigned/compressed numbers, and common types like String with optimized fast paths.
   *
   * <p>This method reads the field value from the object using the fieldAccessor in fieldInfo,
   * then writes it to the buffer. It is the write counterpart of {@link
   * #readBuildInFieldValue(SerializationBinding, SerializationFieldInfo, MemoryBuffer, Object)}.
   *
   * @param binding the serialization binding for write operations
   * @param fieldInfo the field metadata including type, nullability info, and field accessor
   * @param buffer the buffer to write to
   * @param obj the object to read the field value from
   */
  static void writeBuildInField(
      SerializationBinding binding,
      SerializationFieldInfo fieldInfo,
      MemoryBuffer buffer,
      Object obj) {
    Fory fory = binding.fory;
    FieldAccessor fieldAccessor = fieldInfo.fieldAccessor;
    int dispatchId = fieldInfo.dispatchId;
    // The dispatch ID already encodes whether the data should have a null flag prefix:
    // - PRIMITIVE_* and NOTNULL_BOXED_* dispatch IDs have no null flag prefix
    // - Nullable dispatch IDs (STRING, BOOL, INT8, etc.) have null flag prefix
    // So we write based on dispatch ID, not the local nullable setting.
    if (writePrimitiveFieldValue(buffer, obj, fieldAccessor, dispatchId)) {
      Object fieldValue = fieldAccessor.getObject(obj);
      boolean needWrite =
          writeBasicObjectFieldValue(fory, buffer, fieldInfo, fieldValue, dispatchId)
              && writeNullableBasicObjectFieldValue(fory, buffer, fieldValue, dispatchId);
      if (needWrite) {
        binding.writeField(fieldInfo, buffer, fieldValue);
      }
    }
  }

  /**
   * Write field value to buffer. Handles primitive types, unsigned/compressed numbers, and common
   * types like String with optimized fast paths.
   *
   * <p>This method is the write counterpart of {@link #readBuildInFieldValue(SerializationBinding,
   * SerializationFieldInfo, MemoryBuffer)}.
   *
   * @param binding the serialization binding for write operations
   * @param fieldInfo the field metadata including type and nullability info
   * @param buffer the buffer to write to
   * @param fieldValue the value to write
   */
  static void writeBuildInFieldValue(
      SerializationBinding binding,
      SerializationFieldInfo fieldInfo,
      MemoryBuffer buffer,
      Object fieldValue) {
    Fory fory = binding.fory;
    boolean nullable = fieldInfo.nullable;
    int dispatchId = fieldInfo.dispatchId;
    // Fast path for primitive types
    if (writePrimitiveValue(buffer, fieldValue, dispatchId)) {
      return;
    }
    boolean needWrite =
        nullable
            ? writeNullableBasicObjectFieldValue(fory, buffer, fieldValue, dispatchId)
            : writeNotNullBasicObjectFieldValue(fory, buffer, fieldValue, dispatchId);
    if (!needWrite) {
      return;
    }
    // Fall back to binding.write for complex types
    binding.writeField(fieldInfo, buffer, fieldValue);
  }

  private static boolean writePrimitiveValue(MemoryBuffer buffer, Object value, int dispatchId) {
    switch (dispatchId) {
      case DispatchId.PRIMITIVE_BOOL:
        buffer.writeBoolean((Boolean) value);
        return true;
      case DispatchId.PRIMITIVE_INT8:
      case DispatchId.PRIMITIVE_UINT8:
        buffer.writeByte((Byte) value);
        return true;
      case DispatchId.PRIMITIVE_CHAR:
        buffer.writeChar((Character) value);
        return true;
      case DispatchId.PRIMITIVE_INT16:
      case DispatchId.PRIMITIVE_UINT16:
        buffer.writeInt16((Short) value);
        return true;
      case DispatchId.PRIMITIVE_INT32:
      case DispatchId.PRIMITIVE_UINT32:
        buffer.writeInt32((Integer) value);
        return true;
      case DispatchId.PRIMITIVE_VARINT32:
        buffer.writeVarInt32((Integer) value);
        return true;
      case DispatchId.PRIMITIVE_VAR_UINT32:
        buffer.writeVarUint32((Integer) value);
        return true;
      case DispatchId.PRIMITIVE_INT64:
      case DispatchId.PRIMITIVE_UINT64:
        buffer.writeInt64((Long) value);
        return true;
      case DispatchId.PRIMITIVE_VARINT64:
        buffer.writeVarInt64((Long) value);
        return true;
      case DispatchId.PRIMITIVE_TAGGED_INT64:
        buffer.writeTaggedInt64((Long) value);
        return true;
      case DispatchId.PRIMITIVE_VAR_UINT64:
        buffer.writeVarUint64((Long) value);
        return true;
      case DispatchId.PRIMITIVE_TAGGED_UINT64:
        buffer.writeTaggedUint64((Long) value);
        return true;
      case DispatchId.PRIMITIVE_FLOAT32:
        buffer.writeFloat32((Float) value);
        return true;
      case DispatchId.PRIMITIVE_FLOAT64:
        buffer.writeFloat64((Double) value);
        return true;
      default:
        return false;
    }
  }

  /**
   * Write a primitive field value to buffer using direct memory offset access.
   *
   * @param buffer       the buffer to write to
   * @param targetObject the object containing the field
   * @param fieldOffset  the memory offset of the field
   * @param dispatchId   the class ID of the primitive type
   * @return true if dispatchId is not a primitive type and needs further write handling
   */
  private static boolean writePrimitiveFieldValue(
      MemoryBuffer buffer, Object targetObject, long fieldOffset, int dispatchId) {
    switch (dispatchId) {
      case DispatchId.PRIMITIVE_BOOL:
        buffer.writeBoolean(Platform.getBoolean(targetObject, fieldOffset));
        return false;
      case DispatchId.PRIMITIVE_INT8:
      case DispatchId.PRIMITIVE_UINT8:
        buffer.writeByte(Platform.getByte(targetObject, fieldOffset));
        return false;
      case DispatchId.PRIMITIVE_CHAR:
        buffer.writeChar(Platform.getChar(targetObject, fieldOffset));
        return false;
      case DispatchId.PRIMITIVE_INT16:
      case DispatchId.PRIMITIVE_UINT16:
        buffer.writeInt16(Platform.getShort(targetObject, fieldOffset));
        return false;
      case DispatchId.PRIMITIVE_INT32:
      case DispatchId.PRIMITIVE_UINT32:
        buffer.writeInt32(Platform.getInt(targetObject, fieldOffset));
        return false;
      case DispatchId.PRIMITIVE_VARINT32:
        buffer.writeVarInt32(Platform.getInt(targetObject, fieldOffset));
        return false;
      case DispatchId.PRIMITIVE_VAR_UINT32:
        buffer.writeVarUint32(Platform.getInt(targetObject, fieldOffset));
        return false;
      case DispatchId.PRIMITIVE_FLOAT32:
        buffer.writeFloat32(Platform.getFloat(targetObject, fieldOffset));
        return false;
      case DispatchId.PRIMITIVE_INT64:
      case DispatchId.PRIMITIVE_UINT64:
        buffer.writeInt64(Platform.getLong(targetObject, fieldOffset));
        return false;
      case DispatchId.PRIMITIVE_VARINT64:
        buffer.writeVarInt64(Platform.getLong(targetObject, fieldOffset));
        return false;
      case DispatchId.PRIMITIVE_TAGGED_INT64:
        buffer.writeTaggedInt64(Platform.getLong(targetObject, fieldOffset));
        return false;
      case DispatchId.PRIMITIVE_VAR_UINT64:
        buffer.writeVarUint64(Platform.getLong(targetObject, fieldOffset));
        return false;
      case DispatchId.PRIMITIVE_TAGGED_UINT64:
        buffer.writeTaggedUint64(Platform.getLong(targetObject, fieldOffset));
        return false;
      case DispatchId.PRIMITIVE_FLOAT64:
        buffer.writeFloat64(Platform.getDouble(targetObject, fieldOffset));
        return false;
      default:
        return true;
    }
  }

  /**
   * Write a primitive field value to buffer using the field accessor.
   *
   * @param buffer        the buffer to write to
   * @param targetObject  the object containing the field
   * @param fieldAccessor the accessor to get the field value
   * @param dispatchId    the class ID of the primitive type
   * @return true if dispatchId is not a primitive type and needs further write handling
   */
  static boolean writePrimitiveFieldValue(
      MemoryBuffer buffer, Object targetObject, FieldAccessor fieldAccessor, int dispatchId) {
    long fieldOffset = fieldAccessor.getFieldOffset();
    if (fieldOffset != -1) {
      return writePrimitiveFieldValue(buffer, targetObject, fieldOffset, dispatchId);
    }
    // graalvm use GeneratedAccessor, which will be this code path.
    switch (dispatchId) {
      case DispatchId.PRIMITIVE_BOOL:
        buffer.writeBoolean((Boolean) fieldAccessor.get(targetObject));
        return false;
      case DispatchId.PRIMITIVE_INT8:
      case DispatchId.PRIMITIVE_UINT8:
        buffer.writeByte((Byte) fieldAccessor.get(targetObject));
        return false;
      case DispatchId.PRIMITIVE_CHAR:
        buffer.writeChar((Character) fieldAccessor.get(targetObject));
        return false;
      case DispatchId.PRIMITIVE_INT16:
      case DispatchId.PRIMITIVE_UINT16:
        buffer.writeInt16((Short) fieldAccessor.get(targetObject));
        return false;
      case DispatchId.PRIMITIVE_INT32:
      case DispatchId.PRIMITIVE_UINT32:
        buffer.writeInt32((Integer) fieldAccessor.get(targetObject));
        return false;
      case DispatchId.PRIMITIVE_VARINT32:
        buffer.writeVarInt32((Integer) fieldAccessor.get(targetObject));
        return false;
      case DispatchId.PRIMITIVE_VAR_UINT32:
        buffer.writeVarUint32((Integer) fieldAccessor.get(targetObject));
        return false;
      case DispatchId.PRIMITIVE_FLOAT32:
        buffer.writeFloat32((Float) fieldAccessor.get(targetObject));
        return false;
      case DispatchId.PRIMITIVE_INT64:
      case DispatchId.PRIMITIVE_UINT64:
        buffer.writeInt64((Long) fieldAccessor.get(targetObject));
        return false;
      case DispatchId.PRIMITIVE_VARINT64:
        buffer.writeVarInt64((Long) fieldAccessor.get(targetObject));
        return false;
      case DispatchId.PRIMITIVE_TAGGED_INT64:
        buffer.writeTaggedInt64((Long) fieldAccessor.get(targetObject));
        return false;
      case DispatchId.PRIMITIVE_VAR_UINT64:
        buffer.writeVarUint64((Long) fieldAccessor.get(targetObject));
        return false;
      case DispatchId.PRIMITIVE_TAGGED_UINT64:
        buffer.writeTaggedUint64((Long) fieldAccessor.get(targetObject));
        return false;
      case DispatchId.PRIMITIVE_FLOAT64:
        buffer.writeFloat64((Double) fieldAccessor.get(targetObject));
        return false;
      default:
        return true;
    }
  }

  /**
   * Write field value to buffer. This method handle the situation which all fields are not null.
   *
   * @return true if field value isn't written by this function.
   */
  static boolean writeNotNullBasicObjectFieldValue(
      Fory fory, MemoryBuffer buffer, Object fieldValue, int dispatchId) {
    if (fieldValue == null) {
      throw new IllegalArgumentException(
          "Non-nullable field has null value. In xlang mode, fields are non-nullable by default. "
              + "Use @ForyField(nullable=true) to allow null values.");
    }
    // add time types serialization here.
    switch (dispatchId) {
      case DispatchId.STRING: // fastpath for string.
        String stringValue = (String) (fieldValue);
        if (fory.getStringSerializer().needToWriteRef()) {
          fory.writeJavaStringRef(buffer, stringValue);
        } else {
          fory.writeString(buffer, stringValue);
        }
        return false;
      case DispatchId.BOOL:
        buffer.writeBoolean((Boolean) fieldValue);
        return false;
      case DispatchId.INT8:
      case DispatchId.UINT8:
        buffer.writeByte((Byte) fieldValue);
        return false;
      case DispatchId.CHAR:
        buffer.writeChar((Character) fieldValue);
        return false;
      case DispatchId.INT16:
      case DispatchId.UINT16:
        buffer.writeInt16((Short) fieldValue);
        return false;
      case DispatchId.INT32:
      case DispatchId.UINT32:
        buffer.writeInt32((Integer) fieldValue);
        return false;
      case DispatchId.VARINT32:
        buffer.writeVarInt32((Integer) fieldValue);
        return false;
      case DispatchId.VAR_UINT32:
        buffer.writeVarUint32((Integer) fieldValue);
        return false;
      case DispatchId.INT64:
      case DispatchId.UINT64:
        buffer.writeInt64((Long) fieldValue);
        return false;
      case DispatchId.VARINT64:
        buffer.writeVarInt64((Long) fieldValue);
        return false;
      case DispatchId.TAGGED_INT64:
        buffer.writeTaggedInt64((Long) fieldValue);
        return false;
      case DispatchId.VAR_UINT64:
        buffer.writeVarUint64((Long) fieldValue);
        return false;
      case DispatchId.TAGGED_UINT64:
        buffer.writeTaggedUint64((Long) fieldValue);
        return false;
      case DispatchId.FLOAT32:
        buffer.writeFloat32((Float) fieldValue);
        return false;
      case DispatchId.FLOAT64:
        buffer.writeFloat64((Double) fieldValue);
        return false;
      default:
        return true;
    }
  }

  /**
   * Write a nullable boxed primitive or String field value to buffer. Writes null flag before value
   * if the field is null.
   *
   * @param fory       the fory instance for compression and ref tracking settings
   * @param buffer     the buffer to write to
   * @param fieldValue the field value to write (may be null)
   * @param dispatchId the class ID of the boxed type
   * @return true if dispatchId is not a basic type or ref tracking is enabled, needing further
   * write handling
   */
  static boolean writeNullableBasicObjectFieldValue(
      Fory fory, MemoryBuffer buffer, Object fieldValue, int dispatchId) {
    // add time types serialization here.
    switch (dispatchId) {
      case DispatchId.STRING: // fastpath for string.
        fory.writeJavaStringRef(buffer, (String) (fieldValue));
        return false;
      case DispatchId.BOOL:
        if (fieldValue == null) {
          buffer.writeByte(Fory.NULL_FLAG);
        } else {
          buffer.writeByte(Fory.NOT_NULL_VALUE_FLAG);
          buffer.writeBoolean((Boolean) (fieldValue));
        }
        return false;
      case DispatchId.INT8:
      case DispatchId.UINT8:
        if (fieldValue == null) {
          buffer.writeByte(Fory.NULL_FLAG);
        } else {
          buffer.writeByte(Fory.NOT_NULL_VALUE_FLAG);
          buffer.writeByte((Byte) (fieldValue));
        }
        return false;
      case DispatchId.CHAR:
        if (fieldValue == null) {
          buffer.writeByte(Fory.NULL_FLAG);
        } else {
          buffer.writeByte(Fory.NOT_NULL_VALUE_FLAG);
          buffer.writeChar((Character) (fieldValue));
        }
        return false;
      case DispatchId.INT16:
      case DispatchId.UINT16:
        if (fieldValue == null) {
          buffer.writeByte(Fory.NULL_FLAG);
        } else {
          buffer.writeByte(Fory.NOT_NULL_VALUE_FLAG);
          buffer.writeInt16((Short) (fieldValue));
        }
        return false;
      case DispatchId.INT32:
      case DispatchId.UINT32:
        if (fieldValue == null) {
          buffer.writeByte(Fory.NULL_FLAG);
        } else {
          buffer.writeByte(Fory.NOT_NULL_VALUE_FLAG);
          buffer.writeInt32((Integer) (fieldValue));
        }
        return false;
      case DispatchId.VARINT32:
        if (fieldValue == null) {
          buffer.writeByte(Fory.NULL_FLAG);
        } else {
          buffer.writeByte(Fory.NOT_NULL_VALUE_FLAG);
          buffer.writeVarInt32((Integer) (fieldValue));
        }
        return false;
      case DispatchId.VAR_UINT32:
        if (fieldValue == null) {
          buffer.writeByte(Fory.NULL_FLAG);
        } else {
          buffer.writeByte(Fory.NOT_NULL_VALUE_FLAG);
          buffer.writeVarUint32((Integer) (fieldValue));
        }
        return false;
      case DispatchId.FLOAT32:
        if (fieldValue == null) {
          buffer.writeByte(Fory.NULL_FLAG);
        } else {
          buffer.writeByte(Fory.NOT_NULL_VALUE_FLAG);
          buffer.writeFloat32((Float) (fieldValue));
        }
        return false;
      case DispatchId.INT64:
      case DispatchId.UINT64:
        if (fieldValue == null) {
          buffer.writeByte(Fory.NULL_FLAG);
        } else {
          buffer.writeByte(Fory.NOT_NULL_VALUE_FLAG);
          buffer.writeInt64((Long) fieldValue);
        }
        return false;
      case DispatchId.VARINT64:
        if (fieldValue == null) {
          buffer.writeByte(Fory.NULL_FLAG);
        } else {
          buffer.writeByte(Fory.NOT_NULL_VALUE_FLAG);
          buffer.writeVarInt64((Long) fieldValue);
        }
        return false;
      case DispatchId.TAGGED_INT64:
        if (fieldValue == null) {
          buffer.writeByte(Fory.NULL_FLAG);
        } else {
          buffer.writeByte(Fory.NOT_NULL_VALUE_FLAG);
          buffer.writeTaggedInt64((Long) fieldValue);
        }
        return false;
      case DispatchId.VAR_UINT64:
        if (fieldValue == null) {
          buffer.writeByte(Fory.NULL_FLAG);
        } else {
          buffer.writeByte(Fory.NOT_NULL_VALUE_FLAG);
          buffer.writeVarUint64((Long) fieldValue);
        }
        return false;
      case DispatchId.TAGGED_UINT64:
        if (fieldValue == null) {
          buffer.writeByte(Fory.NULL_FLAG);
        } else {
          buffer.writeByte(Fory.NOT_NULL_VALUE_FLAG);
          buffer.writeTaggedUint64((Long) fieldValue);
        }
        return false;
      case DispatchId.FLOAT64:
        if (fieldValue == null) {
          buffer.writeByte(Fory.NULL_FLAG);
        } else {
          buffer.writeByte(Fory.NOT_NULL_VALUE_FLAG);
          buffer.writeFloat64((Double) (fieldValue));
        }
        return false;
      default:
        return true;
    }
  }

  /**
   * Write field value to buffer for PRIMITIVE_* and NOTNULL_BOXED_* dispatch IDs, plus STRING with
   * proper refMode handling. This method handles dispatch IDs that don't have a null flag prefix.
   *
   * <p>Note: This method only handles PRIMITIVE_*, NOTNULL_BOXED_* dispatch IDs, and STRING.
   * Nullable dispatch IDs (BOOL, INT8, etc.) must be handled by {@link
   * #writeNullableBasicObjectFieldValue} since they require a null flag prefix.
   *
   * @return true if field value isn't written by this function and needs further handling.
   */
  private static boolean writeBasicObjectFieldValue(
      Fory fory,
      MemoryBuffer buffer,
      SerializationFieldInfo fieldInfo,
      Object fieldValue,
      int dispatchId) {
    // Only handle PRIMITIVE_*, NOTNULL_BOXED_* dispatch IDs, and STRING here.
    // Nullable dispatch IDs (BOOL, INT8, etc.) require a null flag prefix
    // and must be handled by writeNullableBasicObjectFieldValue.
    switch (dispatchId) {
      case DispatchId.PRIMITIVE_BOOL:
      case DispatchId.NOTNULL_BOXED_BOOL:
        buffer.writeBoolean((Boolean) fieldValue);
        return false;
      case DispatchId.PRIMITIVE_INT8:
      case DispatchId.PRIMITIVE_UINT8:
      case DispatchId.NOTNULL_BOXED_INT8:
      case DispatchId.NOTNULL_BOXED_UINT8:
        buffer.writeByte((Byte) fieldValue);
        return false;
      case DispatchId.PRIMITIVE_CHAR:
      case DispatchId.NOTNULL_BOXED_CHAR:
        buffer.writeChar((Character) fieldValue);
        return false;
      case DispatchId.PRIMITIVE_INT16:
      case DispatchId.PRIMITIVE_UINT16:
      case DispatchId.NOTNULL_BOXED_INT16:
      case DispatchId.NOTNULL_BOXED_UINT16:
        buffer.writeInt16((Short) fieldValue);
        return false;
      case DispatchId.PRIMITIVE_INT32:
      case DispatchId.PRIMITIVE_UINT32:
      case DispatchId.NOTNULL_BOXED_INT32:
      case DispatchId.NOTNULL_BOXED_UINT32:
        buffer.writeInt32((Integer) fieldValue);
        return false;
      case DispatchId.PRIMITIVE_VARINT32:
      case DispatchId.NOTNULL_BOXED_VARINT32:
        buffer.writeVarInt32((Integer) fieldValue);
        return false;
      case DispatchId.PRIMITIVE_VAR_UINT32:
      case DispatchId.NOTNULL_BOXED_VAR_UINT32:
        buffer.writeVarUint32((Integer) fieldValue);
        return false;
      case DispatchId.PRIMITIVE_INT64:
      case DispatchId.PRIMITIVE_UINT64:
      case DispatchId.NOTNULL_BOXED_INT64:
      case DispatchId.NOTNULL_BOXED_UINT64:
        buffer.writeInt64((Long) fieldValue);
        return false;
      case DispatchId.PRIMITIVE_VARINT64:
      case DispatchId.NOTNULL_BOXED_VARINT64:
        buffer.writeVarInt64((Long) fieldValue);
        return false;
      case DispatchId.PRIMITIVE_TAGGED_INT64:
      case DispatchId.NOTNULL_BOXED_TAGGED_INT64:
        buffer.writeTaggedInt64((Long) fieldValue);
        return false;
      case DispatchId.PRIMITIVE_VAR_UINT64:
      case DispatchId.NOTNULL_BOXED_VAR_UINT64:
        buffer.writeVarUint64((Long) fieldValue);
        return false;
      case DispatchId.PRIMITIVE_TAGGED_UINT64:
      case DispatchId.NOTNULL_BOXED_TAGGED_UINT64:
        buffer.writeTaggedUint64((Long) fieldValue);
        return false;
      case DispatchId.PRIMITIVE_FLOAT32:
      case DispatchId.NOTNULL_BOXED_FLOAT32:
        buffer.writeFloat32((Float) fieldValue);
        return false;
      case DispatchId.PRIMITIVE_FLOAT64:
      case DispatchId.NOTNULL_BOXED_FLOAT64:
        buffer.writeFloat64((Double) fieldValue);
        return false;
      case DispatchId.STRING:
        // Handle STRING with proper refMode handling
        if (fieldInfo.refMode == RefMode.TRACKING) {
          fory.writeJavaStringRef(buffer, (String) fieldValue);
        } else {
          if (fieldInfo.refMode == RefMode.NULL_ONLY) {
            if (fieldValue == null) {
              buffer.writeByte(Fory.NULL_FLAG);
            } else {
              buffer.writeByte(Fory.NOT_NULL_VALUE_FLAG);
              fory.writeJavaString(buffer, (String) fieldValue);
            }
          } else {
            fory.writeJavaString(buffer, (String) fieldValue);
          }
        }
        return false;
      default:
        return true;
    }
  }

  static void writeContainerFieldValue(
      SerializationBinding binding,
      RefResolver refResolver,
      Generics generics,
      SerializationFieldInfo fieldInfo,
      MemoryBuffer buffer,
      Object fieldValue) {
    if (fieldInfo.refMode == RefMode.TRACKING) {
      if (refResolver.writeRefOrNull(buffer, fieldValue)) {
        return;
      }
    } else if (fieldInfo.refMode == RefMode.NULL_ONLY) {
      if (fieldValue == null) {
        buffer.writeByte(Fory.NULL_FLAG);
        return;
      }
      buffer.writeByte(Fory.NOT_NULL_VALUE_FLAG);
    }
    generics.pushGenericType(fieldInfo.genericType);
    binding.writeContainerFieldValue(fieldInfo, buffer, fieldValue);
    generics.popGenericType();
  }

  /**
   * Read a container field value (Collection or Map). Handles reference tracking, nullable fields,
   * and pushes/pops generic type information for proper deserialization of parameterized types.
   *
   * @param binding   the serialization binding for read operations
   * @param generics  the generics context for tracking parameterized types
   * @param fieldInfo the field metadata including generic type info and nullability
   * @param buffer    the buffer to read from
   * @return the deserialized container field value, or null if the field is nullable and was null
   */
  static Object readContainerFieldValue(
      SerializationBinding binding,
      Generics generics,
      SerializationFieldInfo fieldInfo,
      MemoryBuffer buffer) {
    Object fieldValue;
    switch (fieldInfo.refMode) {
      case NONE:
        binding.preserveRefId(-1);
        generics.pushGenericType(fieldInfo.genericType);
        fieldValue = binding.readContainerFieldValue(buffer, fieldInfo);
        generics.popGenericType();
        break;
      case NULL_ONLY: {
        binding.preserveRefId(-1);
        byte headFlag = buffer.readByte();
        if (headFlag == Fory.NULL_FLAG) {
          return null;
        }
        generics.pushGenericType(fieldInfo.genericType);
        fieldValue = binding.readContainerFieldValue(buffer, fieldInfo);
        generics.popGenericType();
      }
      break;
      case TRACKING:
        generics.pushGenericType(fieldInfo.genericType);
        fieldValue = binding.readContainerFieldValueRef(buffer, fieldInfo);
        generics.popGenericType();
        break;
      default:
        throw new IllegalStateException("Unknown refMode: " + fieldInfo.refMode);
    }
    return fieldValue;
  }

  /**
   * Sentinel object to indicate the dispatch ID was not handled by optimized path.
   */
  private static final Object UNHANDLED_SENTINEL = new Object();

  /**
   * Read field value from buffer and return it. Handles primitive types, unsigned/compressed
   * numbers, and common types like String with optimized fast paths.
   *
   * <p>This method is similar to {@link #readBuildInFieldValue(SerializationBinding,
   * SerializationFieldInfo, MemoryBuffer, Object)}, but returns the field value instead of setting
   * it into the target object. Useful for record types where field values need to be collected into
   * an array before constructing the object.
   *
   * <p>Note: The dispatch ID from fieldInfo determines the actual data format in the buffer
   * (whether there's a null flag prefix or not), regardless of the local field's nullable setting.
   * This is important for schema compatibility when peer's field definition differs from local.
   *
   * @param binding   the serialization binding for read operations
   * @param fieldInfo the field metadata including type and nullability info
   * @param buffer    the buffer to read from
   * @return the deserialized field value, or null if the field is nullable and was null
   * @see #readBuildInFieldValue(SerializationBinding, SerializationFieldInfo, MemoryBuffer, Object)
   */
  static Object readBuildInFieldValue(
      SerializationBinding binding, SerializationFieldInfo fieldInfo, MemoryBuffer buffer) {
    Fory fory = binding.fory;
    int dispatchId = fieldInfo.dispatchId;
    // Try optimized path for basic types (primitives, boxed, string)
    // The dispatch ID already encodes whether the data has a null flag prefix:
    // - PRIMITIVE_* and NOTNULL_BOXED_* dispatch IDs have no null flag prefix
    // - Nullable dispatch IDs (BOOL, INT8, etc.) have null flag prefix
    // So we try both paths based on dispatch ID, not the local nullable setting.
    // Try primitive and non-null boxed types first (PRIMITIVE_*, NOTNULL_BOXED_*, STRING)
    Object value = readBasicObjectValue(fory, buffer, fieldInfo, dispatchId);
    if (value != UNHANDLED_SENTINEL) {
      return value;
    }
    // Try nullable types (BOOL, INT8, etc. with null flag prefix)
    value = readBasicNullableObjectValue(buffer, dispatchId);
    if (value != UNHANDLED_SENTINEL) {
      return value;
    }
    // Fall back to binding.read for complex types
    return binding.readField(fieldInfo, buffer);
  }

  /**
   * Read a non-nullable basic object value from buffer and return it. Handles PRIMITIVE_*,
   * NOTNULL_BOXED_*, and STRING dispatch IDs with optimized fast paths.
   *
   * <p>Note: Nullable dispatch IDs (BOOL, INT8, etc.) must be handled by
   * {@link #readBasicNullableObjectValue} since they have a null flag prefix in the serialized
   * data.
   *
   * @return the value if handled, or {@link #UNHANDLED_SENTINEL} if not a basic type
   */
  private static Object readBasicObjectValue(
      Fory fory, MemoryBuffer buffer, SerializationFieldInfo fieldInfo, int dispatchId) {
    switch (dispatchId) {
      case DispatchId.PRIMITIVE_BOOL:
      case DispatchId.NOTNULL_BOXED_BOOL:
        return buffer.readBoolean();
      case DispatchId.PRIMITIVE_INT8:
      case DispatchId.PRIMITIVE_UINT8:
      case DispatchId.NOTNULL_BOXED_INT8:
      case DispatchId.NOTNULL_BOXED_UINT8:
        return buffer.readByte();
      case DispatchId.PRIMITIVE_CHAR:
      case DispatchId.NOTNULL_BOXED_CHAR:
        return buffer.readChar();
      case DispatchId.PRIMITIVE_INT16:
      case DispatchId.PRIMITIVE_UINT16:
      case DispatchId.NOTNULL_BOXED_INT16:
      case DispatchId.NOTNULL_BOXED_UINT16:
        return buffer.readInt16();
      case DispatchId.PRIMITIVE_INT32:
      case DispatchId.PRIMITIVE_UINT32:
      case DispatchId.NOTNULL_BOXED_INT32:
      case DispatchId.NOTNULL_BOXED_UINT32:
        return buffer.readInt32();
      case DispatchId.PRIMITIVE_VARINT32:
      case DispatchId.NOTNULL_BOXED_VARINT32:
        return buffer.readVarInt32();
      case DispatchId.PRIMITIVE_VAR_UINT32:
      case DispatchId.NOTNULL_BOXED_VAR_UINT32:
        return buffer.readVarUint32();
      case DispatchId.PRIMITIVE_INT64:
      case DispatchId.PRIMITIVE_UINT64:
      case DispatchId.NOTNULL_BOXED_INT64:
      case DispatchId.NOTNULL_BOXED_UINT64:
        return buffer.readInt64();
      case DispatchId.PRIMITIVE_VARINT64:
      case DispatchId.NOTNULL_BOXED_VARINT64:
        return buffer.readVarInt64();
      case DispatchId.PRIMITIVE_TAGGED_INT64:
      case DispatchId.NOTNULL_BOXED_TAGGED_INT64:
        return buffer.readTaggedInt64();
      case DispatchId.PRIMITIVE_VAR_UINT64:
      case DispatchId.NOTNULL_BOXED_VAR_UINT64:
        return buffer.readVarUint64();
      case DispatchId.PRIMITIVE_TAGGED_UINT64:
      case DispatchId.NOTNULL_BOXED_TAGGED_UINT64:
        return buffer.readTaggedUint64();
      case DispatchId.PRIMITIVE_FLOAT32:
      case DispatchId.NOTNULL_BOXED_FLOAT32:
        return buffer.readFloat32();
      case DispatchId.PRIMITIVE_FLOAT64:
      case DispatchId.NOTNULL_BOXED_FLOAT64:
        return buffer.readFloat64();
      case DispatchId.STRING:
        if (fieldInfo.refMode == RefMode.TRACKING) {
          return fory.readJavaStringRef(buffer);
        } else if (fieldInfo.refMode == RefMode.NULL_ONLY) {
          if (buffer.readByte() == Fory.NULL_FLAG) {
            return null;
          }
          return fory.readJavaString(buffer);
        } else {
          // RefMode.NONE - read string directly without null flag
          return fory.readJavaString(buffer);
        }
      default:
        return UNHANDLED_SENTINEL;
    }
  }

  /**
   * Read a nullable basic object value from buffer and return it. Reads null flag before value for
   * nullable boxed types (BOOL, INT8, etc.).
   *
   * <p>Note: STRING is handled by {@link #readBasicObjectValue} with proper refMode check.
   *
   * @return the value (possibly null) if handled, or {@link #UNHANDLED_SENTINEL} if not a basic
   *     type
   */
  private static Object readBasicNullableObjectValue(MemoryBuffer buffer, int dispatchId) {
    switch (dispatchId) {
      case DispatchId.BOOL:
        if (buffer.readByte() == Fory.NULL_FLAG) {
          return null;
        }
        return buffer.readBoolean();
      case DispatchId.INT8:
      case DispatchId.UINT8:
        if (buffer.readByte() == Fory.NULL_FLAG) {
          return null;
        }
        return buffer.readByte();
      case DispatchId.CHAR:
        if (buffer.readByte() == Fory.NULL_FLAG) {
          return null;
        }
        return buffer.readChar();
      case DispatchId.INT16:
      case DispatchId.UINT16:
        if (buffer.readByte() == Fory.NULL_FLAG) {
          return null;
        }
        return buffer.readInt16();
      case DispatchId.INT32:
      case DispatchId.UINT32:
        if (buffer.readByte() == Fory.NULL_FLAG) {
          return null;
        }
        return buffer.readInt32();
      case DispatchId.VARINT32:
        if (buffer.readByte() == Fory.NULL_FLAG) {
          return null;
        }
        return buffer.readVarInt32();
      case DispatchId.VAR_UINT32:
        if (buffer.readByte() == Fory.NULL_FLAG) {
          return null;
        }
        return buffer.readVarUint32();
      case DispatchId.INT64:
      case DispatchId.UINT64:
        if (buffer.readByte() == Fory.NULL_FLAG) {
          return null;
        }
        return buffer.readInt64();
      case DispatchId.VARINT64:
        if (buffer.readByte() == Fory.NULL_FLAG) {
          return null;
        }
        return buffer.readVarInt64();
      case DispatchId.TAGGED_INT64:
        if (buffer.readByte() == Fory.NULL_FLAG) {
          return null;
        }
        return buffer.readTaggedInt64();
      case DispatchId.VAR_UINT64:
        if (buffer.readByte() == Fory.NULL_FLAG) {
          return null;
        }
        return buffer.readVarUint64();
      case DispatchId.TAGGED_UINT64:
        if (buffer.readByte() == Fory.NULL_FLAG) {
          return null;
        }
        return buffer.readTaggedUint64();
      case DispatchId.FLOAT32:
        if (buffer.readByte() == Fory.NULL_FLAG) {
          return null;
        }
        return buffer.readFloat32();
      case DispatchId.FLOAT64:
        if (buffer.readByte() == Fory.NULL_FLAG) {
          return null;
        }
        return buffer.readFloat64();
      default:
        return UNHANDLED_SENTINEL;
    }
  }

  /**
   * Handle all numeric fields read include unsigned and compressed numbers.
   * It also include fastpath for common type such as String.
   *
   * <p>Note: The dispatch ID from fieldInfo determines the actual data format in the buffer
   * (whether there's a null flag prefix or not), regardless of the local field's nullable setting.
   * This is important for schema compatibility when peer's field definition differs from local.
   */
  static void readBuildInFieldValue(
      SerializationBinding binding, SerializationFieldInfo fieldInfo, MemoryBuffer buffer, Object targetObject) {
    Fory fory = binding.fory;
    FieldAccessor fieldAccessor = fieldInfo.fieldAccessor;
    int dispatchId = fieldInfo.dispatchId;
    // The dispatch ID already encodes whether the data has a null flag prefix:
    // - PRIMITIVE_* and NOTNULL_BOXED_* dispatch IDs have no null flag prefix
    // - Nullable dispatch IDs (BOOL, INT8, etc.) have null flag prefix
    // So we try both paths based on dispatch ID, not the local nullable setting.
    boolean needRead = readPrimitiveFieldValue(buffer, targetObject, fieldAccessor, dispatchId)
        && readBasicObjectFieldValue(fory, buffer, targetObject, fieldInfo, dispatchId)
        && readBasicNullableObjectFieldValue(fory, buffer, targetObject, fieldAccessor, dispatchId);
    if (needRead) {
      Object fieldValue = binding.readField(fieldInfo, buffer);
      fieldAccessor.putObject(targetObject, fieldValue);
    }
  }

  /**
   * Read a primitive value from buffer and set it to field referenced by <code>fieldAccessor</code>
   * of <code>targetObject</code>.
   *
   * @return true if <code>classId</code> is not a primitive type id.
   */
  private static boolean readPrimitiveFieldValue(
      MemoryBuffer buffer, Object targetObject, FieldAccessor fieldAccessor, int dispatchId) {
    long fieldOffset = fieldAccessor.getFieldOffset();
    if (fieldOffset != -1) {
      return readPrimitiveFieldValue(buffer, targetObject, fieldOffset, dispatchId);
    }
    // graalvm use GeneratedAccessor, which will be this code path.
    // we still need `PRIMITIVE` cases since peer may send
    switch (dispatchId) {
      case DispatchId.PRIMITIVE_BOOL:
        fieldAccessor.set(targetObject, buffer.readBoolean());
        return false;
      case DispatchId.PRIMITIVE_INT8:
      case DispatchId.PRIMITIVE_UINT8:
        fieldAccessor.set(targetObject, buffer.readByte());
        return false;
      case DispatchId.PRIMITIVE_CHAR:
        fieldAccessor.set(targetObject, buffer.readChar());
        return false;
      case DispatchId.PRIMITIVE_INT16:
      case DispatchId.PRIMITIVE_UINT16:
        fieldAccessor.set(targetObject, buffer.readInt16());
        return false;
      case DispatchId.PRIMITIVE_INT32:
      case DispatchId.PRIMITIVE_UINT32:
        fieldAccessor.set(targetObject, buffer.readInt32());
        return false;
      case DispatchId.PRIMITIVE_VARINT32:
        fieldAccessor.set(targetObject, buffer.readVarInt32());
        return false;
      case DispatchId.PRIMITIVE_VAR_UINT32:
        fieldAccessor.set(targetObject, buffer.readVarUint32());
        return false;
      case DispatchId.PRIMITIVE_FLOAT32:
        fieldAccessor.set(targetObject, buffer.readFloat32());
        return false;
      case DispatchId.PRIMITIVE_INT64:
      case DispatchId.PRIMITIVE_UINT64:
        fieldAccessor.set(targetObject, buffer.readInt64());
        return false;
      case DispatchId.PRIMITIVE_VARINT64:
        fieldAccessor.set(targetObject, buffer.readVarInt64());
        return false;
      case DispatchId.PRIMITIVE_TAGGED_INT64:
        fieldAccessor.set(targetObject, buffer.readTaggedInt64());
        return false;
      case DispatchId.PRIMITIVE_VAR_UINT64:
        fieldAccessor.set(targetObject, buffer.readVarUint64());
        return false;
      case DispatchId.PRIMITIVE_TAGGED_UINT64:
        fieldAccessor.set(targetObject, buffer.readTaggedUint64());
        return false;
      case DispatchId.PRIMITIVE_FLOAT64:
        fieldAccessor.set(targetObject, buffer.readFloat64());
        return false;
      default:
        return true;
    }
  }

  /**
   * Read a primitive field value from buffer and set it using direct memory offset access.
   *
   * @param buffer       the buffer to read from
   * @param targetObject the object to set the field value on
   * @param fieldOffset  the memory offset of the field
   * @param dispatchId   the dispatch ID of the primitive type
   * @return true if classId is not a primitive type and needs further read handling
   */
  private static boolean readPrimitiveFieldValue(
      MemoryBuffer buffer, Object targetObject, long fieldOffset, int dispatchId) {
    switch (dispatchId) {
      case DispatchId.PRIMITIVE_BOOL:
        Platform.putBoolean(targetObject, fieldOffset, buffer.readBoolean());
        return false;
      case DispatchId.PRIMITIVE_INT8:
      case DispatchId.PRIMITIVE_UINT8:
        Platform.putByte(targetObject, fieldOffset, buffer.readByte());
        return false;
      case DispatchId.PRIMITIVE_CHAR:
        Platform.putChar(targetObject, fieldOffset, buffer.readChar());
        return false;
      case DispatchId.PRIMITIVE_INT16:
      case DispatchId.PRIMITIVE_UINT16:
        Platform.putShort(targetObject, fieldOffset, buffer.readInt16());
        return false;
      case DispatchId.PRIMITIVE_INT32:
      case DispatchId.PRIMITIVE_UINT32:
        Platform.putInt(targetObject, fieldOffset, buffer.readInt32());
        return false;
      case DispatchId.PRIMITIVE_VARINT32:
        Platform.putInt(targetObject, fieldOffset, buffer.readVarInt32());
        return false;
      case DispatchId.PRIMITIVE_VAR_UINT32:
        Platform.putInt(targetObject, fieldOffset, buffer.readVarUint32());
        return false;
      case DispatchId.PRIMITIVE_FLOAT32:
        Platform.putFloat(targetObject, fieldOffset, buffer.readFloat32());
        return false;
      case DispatchId.PRIMITIVE_INT64:
      case DispatchId.PRIMITIVE_UINT64:
        Platform.putLong(targetObject, fieldOffset, buffer.readInt64());
        return false;
      case DispatchId.PRIMITIVE_VARINT64:
        Platform.putLong(targetObject, fieldOffset, buffer.readVarInt64());
        return false;
      case DispatchId.PRIMITIVE_TAGGED_INT64:
        Platform.putLong(targetObject, fieldOffset, buffer.readTaggedInt64());
        return false;
      case DispatchId.PRIMITIVE_VAR_UINT64:
        Platform.putLong(targetObject, fieldOffset, buffer.readVarUint64());
        return false;
      case DispatchId.PRIMITIVE_TAGGED_UINT64:
        Platform.putLong(targetObject, fieldOffset, buffer.readTaggedUint64());
        return false;
      case DispatchId.PRIMITIVE_FLOAT64:
        Platform.putDouble(targetObject, fieldOffset, buffer.readFloat64());
        return false;
      default:
        return true;
    }
  }

  /**
   * Read field value from buffer and set it on the target object. This method handles PRIMITIVE_*
   * and NOTNULL_BOXED_* dispatch IDs where null values are not allowed.
   *
   * <p>Note: This method only handles PRIMITIVE_* and NOTNULL_BOXED_* dispatch IDs. Nullable
   * dispatch IDs (BOOL, INT8, etc.) must be handled by {@link #readBasicNullableObjectFieldValue}
   * since they have a null flag prefix in the serialized data, regardless of the local field's
   * nullable setting.
   *
   * @return true if field value isn't read by this function.
   */
  static boolean readBasicObjectFieldValue(
      Fory fory,
      MemoryBuffer buffer,
      Object targetObject,
      SerializationFieldInfo fieldInfo,
      int dispatchId) {
    FieldAccessor fieldAccessor = fieldInfo.fieldAccessor;
    // Only handle PRIMITIVE_* and NOTNULL_BOXED_* dispatch IDs here.
    // Nullable dispatch IDs (STRING, BOOL, INT8, etc.) have a null flag prefix in the serialized data
    // and must be handled by readBasicNullableObjectFieldValue.
    switch (dispatchId) {
      case DispatchId.PRIMITIVE_BOOL:
      case DispatchId.NOTNULL_BOXED_BOOL:
        fieldAccessor.putObject(targetObject, buffer.readBoolean());
        return false;
      case DispatchId.PRIMITIVE_INT8:
      case DispatchId.PRIMITIVE_UINT8:
      case DispatchId.NOTNULL_BOXED_INT8:
      case DispatchId.NOTNULL_BOXED_UINT8:
        fieldAccessor.putObject(targetObject, buffer.readByte());
        return false;
      case DispatchId.PRIMITIVE_CHAR:
      case DispatchId.NOTNULL_BOXED_CHAR:
        fieldAccessor.putObject(targetObject, buffer.readChar());
        return false;
      case DispatchId.PRIMITIVE_INT16:
      case DispatchId.PRIMITIVE_UINT16:
      case DispatchId.NOTNULL_BOXED_INT16:
      case DispatchId.NOTNULL_BOXED_UINT16:
        fieldAccessor.putObject(targetObject, buffer.readInt16());
        return false;
      case DispatchId.PRIMITIVE_INT32:
      case DispatchId.PRIMITIVE_UINT32:
      case DispatchId.NOTNULL_BOXED_INT32:
      case DispatchId.NOTNULL_BOXED_UINT32:
        fieldAccessor.putObject(targetObject, buffer.readInt32());
        return false;
      case DispatchId.PRIMITIVE_VARINT32:
      case DispatchId.NOTNULL_BOXED_VARINT32:
        fieldAccessor.putObject(targetObject, buffer.readVarInt32());
        return false;
      case DispatchId.PRIMITIVE_VAR_UINT32:
      case DispatchId.NOTNULL_BOXED_VAR_UINT32:
        fieldAccessor.putObject(targetObject, buffer.readVarUint32());
        return false;
      case DispatchId.PRIMITIVE_INT64:
      case DispatchId.PRIMITIVE_UINT64:
      case DispatchId.NOTNULL_BOXED_INT64:
      case DispatchId.NOTNULL_BOXED_UINT64:
        fieldAccessor.putObject(targetObject, buffer.readInt64());
        return false;
      case DispatchId.PRIMITIVE_VARINT64:
      case DispatchId.NOTNULL_BOXED_VARINT64:
        fieldAccessor.putObject(targetObject, buffer.readVarInt64());
        return false;
      case DispatchId.PRIMITIVE_TAGGED_INT64:
      case DispatchId.NOTNULL_BOXED_TAGGED_INT64:
        fieldAccessor.putObject(targetObject, buffer.readTaggedInt64());
        return false;
      case DispatchId.PRIMITIVE_VAR_UINT64:
      case DispatchId.NOTNULL_BOXED_VAR_UINT64:
        fieldAccessor.putObject(targetObject, buffer.readVarUint64());
        return false;
      case DispatchId.PRIMITIVE_TAGGED_UINT64:
      case DispatchId.NOTNULL_BOXED_TAGGED_UINT64:
        fieldAccessor.putObject(targetObject, buffer.readTaggedUint64());
        return false;
      case DispatchId.PRIMITIVE_FLOAT32:
      case DispatchId.NOTNULL_BOXED_FLOAT32:
        fieldAccessor.putObject(targetObject, buffer.readFloat32());
        return false;
      case DispatchId.PRIMITIVE_FLOAT64:
      case DispatchId.NOTNULL_BOXED_FLOAT64:
        fieldAccessor.putObject(targetObject, buffer.readFloat64());
        return false;
      case DispatchId.STRING:
        if (fieldInfo.refMode == RefMode.TRACKING) {
          fieldAccessor.putObject(targetObject, fory.readJavaStringRef(buffer));
        } else {
          if (fieldInfo.refMode != RefMode.NULL_ONLY || buffer.readByte() != Fory.NULL_FLAG) {
            fieldAccessor.putObject(targetObject, fory.readJavaString(buffer));
          }
        }
        return false;
      default:
        return true;
    }
  }

  /**
   * Read a nullable boxed primitive or String field value from buffer and set it on the target
   * object. Reads the null flag before value for nullable types.
   * Note that this method must handle all unsigned/compressed int encodings, since the fallback code path
   * are type based, and won't handle such cases.
   *
   * @param fory          the fory instance for compression and ref tracking settings
   * @param buffer        the buffer to read from
   * @param targetObject  the object to set the field value on
   * @param fieldAccessor the accessor to set the field value
   * @param dispatchId    the class ID of the boxed type
   * @return true if dispatchId is not a basic type or ref tracking is enabled, needing further read
   * handling
   */
  private static boolean readBasicNullableObjectFieldValue(
      Fory fory,
      MemoryBuffer buffer,
      Object targetObject,
      FieldAccessor fieldAccessor,
      int dispatchId) {
    // add time types serialization here.
    switch (dispatchId) {
      case DispatchId.BOOL:
        if (buffer.readByte() == Fory.NULL_FLAG) {
          fieldAccessor.putObject(targetObject, null);
        } else {
          fieldAccessor.putObject(targetObject, buffer.readBoolean());
        }
        return false;
      case DispatchId.INT8:
      case DispatchId.UINT8:
        if (buffer.readByte() == Fory.NULL_FLAG) {
          fieldAccessor.putObject(targetObject, null);
        } else {
          fieldAccessor.putObject(targetObject, buffer.readByte());
        }
        return false;
      case DispatchId.CHAR:
        if (buffer.readByte() == Fory.NULL_FLAG) {
          fieldAccessor.putObject(targetObject, null);
        } else {
          fieldAccessor.putObject(targetObject, buffer.readChar());
        }
        return false;
      case DispatchId.INT16:
      case DispatchId.UINT16:
        if (buffer.readByte() == Fory.NULL_FLAG) {
          fieldAccessor.putObject(targetObject, null);
        } else {
          fieldAccessor.putObject(targetObject, buffer.readInt16());
        }
        return false;
      case DispatchId.INT32:
      case DispatchId.UINT32:
        if (buffer.readByte() == Fory.NULL_FLAG) {
          fieldAccessor.putObject(targetObject, null);
        } else {
          fieldAccessor.putObject(targetObject, buffer.readInt32());
        }
        return false;
      case DispatchId.VARINT32:
        if (buffer.readByte() == Fory.NULL_FLAG) {
          fieldAccessor.putObject(targetObject, null);
        } else {
          fieldAccessor.putObject(targetObject, buffer.readVarInt32());
        }
        return false;
      case DispatchId.VAR_UINT32:
        if (buffer.readByte() == Fory.NULL_FLAG) {
          fieldAccessor.putObject(targetObject, null);
        } else {
          fieldAccessor.putObject(targetObject, buffer.readVarUint32());
        }
        return false;
      case DispatchId.INT64:
      case DispatchId.UINT64:
        if (buffer.readByte() == Fory.NULL_FLAG) {
          fieldAccessor.putObject(targetObject, null);
        } else {
          fieldAccessor.putObject(targetObject, buffer.readInt64());
        }
        return false;
      case DispatchId.VARINT64:
        if (buffer.readByte() == Fory.NULL_FLAG) {
          fieldAccessor.putObject(targetObject, null);
        } else {
          fieldAccessor.putObject(targetObject, buffer.readVarInt64());
        }
        return false;
      case DispatchId.TAGGED_INT64:
        if (buffer.readByte() == Fory.NULL_FLAG) {
          fieldAccessor.putObject(targetObject, null);
        } else {
          fieldAccessor.putObject(targetObject, buffer.readTaggedInt64());
        }
        return false;
      case DispatchId.VAR_UINT64:
        if (buffer.readByte() == Fory.NULL_FLAG) {
          fieldAccessor.putObject(targetObject, null);
        } else {
          fieldAccessor.putObject(targetObject, buffer.readVarUint64());
        }
        return false;
      case DispatchId.TAGGED_UINT64:
        if (buffer.readByte() == Fory.NULL_FLAG) {
          fieldAccessor.putObject(targetObject, null);
        } else {
          fieldAccessor.putObject(targetObject, buffer.readTaggedUint64());
        }
        return false;
      case DispatchId.FLOAT32:
        if (buffer.readByte() == Fory.NULL_FLAG) {
          fieldAccessor.putObject(targetObject, null);
        } else {
          fieldAccessor.putObject(targetObject, buffer.readFloat32());
        }
        return false;
      case DispatchId.FLOAT64:
        if (buffer.readByte() == Fory.NULL_FLAG) {
          fieldAccessor.putObject(targetObject, null);
        } else {
          fieldAccessor.putObject(targetObject, buffer.readFloat64());
        }
        return false;
      // string is handled in readBasicObjectFieldValue
      default:
        return true;
    }
  }

  static Object readPrimitiveValue(MemoryBuffer buffer, int dispatchId) {
    switch (dispatchId) {
      case DispatchId.PRIMITIVE_BOOL:
        return buffer.readBoolean();
      case DispatchId.PRIMITIVE_INT8:
      case DispatchId.PRIMITIVE_UINT8:
        return buffer.readByte();
      case DispatchId.PRIMITIVE_CHAR:
        return buffer.readChar();
      case DispatchId.PRIMITIVE_INT16:
      case DispatchId.PRIMITIVE_UINT16:
        return buffer.readInt16();
      case DispatchId.PRIMITIVE_INT32:
        return buffer.readInt32();
      case DispatchId.PRIMITIVE_VARINT32:
        return buffer.readVarInt32();
      case DispatchId.PRIMITIVE_UINT32:
        return buffer.readInt32();
      case DispatchId.PRIMITIVE_VAR_UINT32:
        return buffer.readVarUint32();
      case DispatchId.PRIMITIVE_INT64:
        return buffer.readInt64();
      case DispatchId.PRIMITIVE_VARINT64:
        return buffer.readVarInt64();
      case DispatchId.PRIMITIVE_TAGGED_INT64:
        return buffer.readTaggedInt64();
      case DispatchId.PRIMITIVE_UINT64:
        return buffer.readInt64();
      case DispatchId.PRIMITIVE_VAR_UINT64:
        return buffer.readVarUint64();
      case DispatchId.PRIMITIVE_TAGGED_UINT64:
        return buffer.readTaggedUint64();
      case DispatchId.PRIMITIVE_FLOAT32:
        return buffer.readFloat32();
      case DispatchId.PRIMITIVE_FLOAT64:
        return buffer.readFloat64();
      default:
        return UNHANDLED_SENTINEL;
    }
  }

  @Override
  public T copy(T originObj) {
    if (immutable) {
      return originObj;
    }
    if (isRecord) {
      return copyRecord(originObj);
    }
    T newObj = newBean();
    if (needToCopyRef) {
      fory.reference(originObj, newObj);
    }
    copyFields(originObj, newObj);
    return newObj;
  }

  private T copyRecord(T originObj) {
    Object[] fieldValues = copyFields(originObj);
    try {
      T t = (T) objectCreator.newInstanceWithArguments(fieldValues);
      Arrays.fill(copyRecordInfo.getRecordComponents(), null);
      fory.reference(originObj, t);
      return t;
    } catch (Throwable e) {
      Platform.throwException(e);
    }
    return originObj;
  }

  private Object[] copyFields(T originObj) {
    SerializationFieldInfo[] fieldInfos = this.fieldInfos;
    if (fieldInfos == null) {
      fieldInfos = buildFieldsInfo();
    }
    Object[] fieldValues = new Object[fieldInfos.length];
    for (int i = 0; i < fieldInfos.length; i++) {
      SerializationFieldInfo fieldInfo = fieldInfos[i];
      FieldAccessor fieldAccessor = fieldInfo.fieldAccessor;
      long fieldOffset = fieldAccessor.getFieldOffset();
      if (fieldOffset != -1) {
        fieldValues[i] = copyField(originObj, fieldOffset, fieldInfo.dispatchId);
      } else {
        // field in record class has offset -1
        Object fieldValue = fieldAccessor.get(originObj);
        fieldValues[i] = fory.copyObject(fieldValue, fieldInfo.dispatchId);
      }
    }
    return RecordUtils.remapping(copyRecordInfo, fieldValues);
  }

  private void copyFields(T originObj, T newObj) {
    SerializationFieldInfo[] fieldInfos = this.fieldInfos;
    if (fieldInfos == null) {
      fieldInfos = buildFieldsInfo();
    }
    copyFields(fory, fieldInfos, originObj, newObj);
  }

  public static void copyFields(
      Fory fory, SerializationFieldInfo[] fieldInfos, Object originObj, Object newObj) {
    for (SerializationFieldInfo fieldInfo : fieldInfos) {
      FieldAccessor fieldAccessor = fieldInfo.fieldAccessor;
      long fieldOffset = fieldAccessor.getFieldOffset();
      // record class won't go to this path;
      assert fieldOffset != -1;
      switch (fieldInfo.dispatchId) {
        case DispatchId.PRIMITIVE_BOOL:
          Platform.putBoolean(newObj, fieldOffset, Platform.getBoolean(originObj, fieldOffset));
          break;
        case DispatchId.PRIMITIVE_INT8:
        case DispatchId.PRIMITIVE_UINT8:
          Platform.putByte(newObj, fieldOffset, Platform.getByte(originObj, fieldOffset));
          break;
        case DispatchId.PRIMITIVE_CHAR:
          Platform.putChar(newObj, fieldOffset, Platform.getChar(originObj, fieldOffset));
          break;
        case DispatchId.PRIMITIVE_INT16:
        case DispatchId.PRIMITIVE_UINT16:
          Platform.putShort(newObj, fieldOffset, Platform.getShort(originObj, fieldOffset));
          break;
        case DispatchId.PRIMITIVE_INT32:
        case DispatchId.PRIMITIVE_VARINT32:
        case DispatchId.PRIMITIVE_UINT32:
        case DispatchId.PRIMITIVE_VAR_UINT32:
          Platform.putInt(newObj, fieldOffset, Platform.getInt(originObj, fieldOffset));
          break;
        case DispatchId.PRIMITIVE_INT64:
        case DispatchId.PRIMITIVE_VARINT64:
        case DispatchId.PRIMITIVE_TAGGED_INT64:
        case DispatchId.PRIMITIVE_UINT64:
        case DispatchId.PRIMITIVE_VAR_UINT64:
        case DispatchId.PRIMITIVE_TAGGED_UINT64:
          Platform.putLong(newObj, fieldOffset, Platform.getLong(originObj, fieldOffset));
          break;
        case DispatchId.PRIMITIVE_FLOAT32:
          Platform.putFloat(newObj, fieldOffset, Platform.getFloat(originObj, fieldOffset));
          break;
        case DispatchId.PRIMITIVE_FLOAT64:
          Platform.putDouble(newObj, fieldOffset, Platform.getDouble(originObj, fieldOffset));
          break;
        case DispatchId.BOOL:
        case DispatchId.INT8:
        case DispatchId.UINT8:
        case DispatchId.CHAR:
        case DispatchId.INT16:
        case DispatchId.UINT16:
        case DispatchId.INT32:
        case DispatchId.VARINT32:
        case DispatchId.UINT32:
        case DispatchId.VAR_UINT32:
        case DispatchId.INT64:
        case DispatchId.VARINT64:
        case DispatchId.TAGGED_INT64:
        case DispatchId.UINT64:
        case DispatchId.VAR_UINT64:
        case DispatchId.TAGGED_UINT64:
        case DispatchId.FLOAT32:
        case DispatchId.FLOAT64:
        case DispatchId.STRING:
          Platform.putObject(newObj, fieldOffset, Platform.getObject(originObj, fieldOffset));
          break;
        default:
          Platform.putObject(
              newObj, fieldOffset, fory.copyObject(Platform.getObject(originObj, fieldOffset)));
      }
    }
  }

  private Object copyField(Object targetObject, long fieldOffset, int typeId) {
    switch (typeId) {
      case DispatchId.PRIMITIVE_BOOL:
        return Platform.getBoolean(targetObject, fieldOffset);
      case DispatchId.PRIMITIVE_INT8:
      case DispatchId.PRIMITIVE_UINT8:
        return Platform.getByte(targetObject, fieldOffset);
      case DispatchId.PRIMITIVE_CHAR:
        return Platform.getChar(targetObject, fieldOffset);
      case DispatchId.PRIMITIVE_INT16:
      case DispatchId.PRIMITIVE_UINT16:
        return Platform.getShort(targetObject, fieldOffset);
      case DispatchId.PRIMITIVE_INT32:
      case DispatchId.PRIMITIVE_VARINT32:
      case DispatchId.PRIMITIVE_UINT32:
      case DispatchId.PRIMITIVE_VAR_UINT32:
        return Platform.getInt(targetObject, fieldOffset);
      case DispatchId.PRIMITIVE_FLOAT32:
        return Platform.getFloat(targetObject, fieldOffset);
      case DispatchId.PRIMITIVE_INT64:
      case DispatchId.PRIMITIVE_VARINT64:
      case DispatchId.PRIMITIVE_TAGGED_INT64:
      case DispatchId.PRIMITIVE_UINT64:
      case DispatchId.PRIMITIVE_VAR_UINT64:
      case DispatchId.PRIMITIVE_TAGGED_UINT64:
        return Platform.getLong(targetObject, fieldOffset);
      case DispatchId.PRIMITIVE_FLOAT64:
        return Platform.getDouble(targetObject, fieldOffset);
      case DispatchId.BOOL:
      case DispatchId.INT8:
      case DispatchId.UINT8:
      case DispatchId.CHAR:
      case DispatchId.INT16:
      case DispatchId.UINT16:
      case DispatchId.INT32:
      case DispatchId.VARINT32:
      case DispatchId.UINT32:
      case DispatchId.VAR_UINT32:
      case DispatchId.FLOAT32:
      case DispatchId.INT64:
      case DispatchId.VARINT64:
      case DispatchId.TAGGED_INT64:
      case DispatchId.UINT64:
      case DispatchId.VAR_UINT64:
      case DispatchId.TAGGED_UINT64:
      case DispatchId.FLOAT64:
      case DispatchId.STRING:
        return Platform.getObject(targetObject, fieldOffset);
      default:
        return fory.copyObject(Platform.getObject(targetObject, fieldOffset));
    }
  }

  private SerializationFieldInfo[] buildFieldsInfo() {
    List<Descriptor> descriptors = new ArrayList<>();
    if (RecordUtils.isRecord(type)) {
      RecordComponent[] components = RecordUtils.getRecordComponents(type);
      assert components != null;
      try {
        for (RecordComponent component : components) {
          Field field = type.getDeclaredField(component.getName());
          descriptors.add(
              new Descriptor(
                  field, TypeRef.of(field.getGenericType()), component.getAccessor(), null));
        }
      } catch (NoSuchFieldException e) {
        // impossible
        Platform.throwException(e);
      }
    } else {
      for (Field field : ReflectionUtils.getFields(type, true)) {
        if (!Modifier.isStatic(field.getModifiers())) {
          descriptors.add(new Descriptor(field, TypeRef.of(field.getGenericType()), null, null));
        }
      }
    }
    DescriptorGrouper descriptorGrouper =
        fory.getClassResolver().createDescriptorGrouper(descriptors, false);
    FieldGroups fieldGroups = FieldGroups.buildFieldInfos(fory, descriptorGrouper);
    fieldInfos = fieldGroups.allFields;
    if (isRecord) {
      List<String> fieldNames =
          Arrays.stream(fieldInfos)
              .map(f -> f.fieldAccessor.getField().getName())
              .collect(Collectors.toList());
      copyRecordInfo = new RecordInfo(type, fieldNames);
    }
    return fieldInfos;
  }

  protected T newBean() {
    return objectCreator.newInstance();
  }
}
