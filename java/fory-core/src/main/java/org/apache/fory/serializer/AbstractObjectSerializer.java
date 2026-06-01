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
import org.apache.fory.collection.IntArray;
import org.apache.fory.config.Config;
import org.apache.fory.context.CopyContext;
import org.apache.fory.context.ReadContext;
import org.apache.fory.context.RefReader;
import org.apache.fory.context.RefWriter;
import org.apache.fory.context.WriteContext;
import org.apache.fory.exception.ForyException;
import org.apache.fory.logging.Logger;
import org.apache.fory.logging.LoggerFactory;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.reflect.FieldAccessor;
import org.apache.fory.reflect.ObjectCreator;
import org.apache.fory.reflect.ReflectionUtils;
import org.apache.fory.reflect.TypeRef;
import org.apache.fory.resolver.RefMode;
import org.apache.fory.resolver.TypeInfo;
import org.apache.fory.resolver.TypeResolver;
import org.apache.fory.serializer.FieldGroups.SerializationFieldInfo;
import org.apache.fory.type.BFloat16;
import org.apache.fory.type.Descriptor;
import org.apache.fory.type.DescriptorGrouper;
import org.apache.fory.type.DispatchId;
import org.apache.fory.type.Float16;
import org.apache.fory.type.Generics;
import org.apache.fory.type.TypeUtils;
import org.apache.fory.type.unsigned.UInt16;
import org.apache.fory.type.unsigned.UInt32;
import org.apache.fory.type.unsigned.UInt64;
import org.apache.fory.type.unsigned.UInt8;
import org.apache.fory.util.ExceptionUtils;
import org.apache.fory.util.record.RecordComponent;
import org.apache.fory.util.record.RecordInfo;
import org.apache.fory.util.record.RecordUtils;

public abstract class AbstractObjectSerializer<T> extends Serializer<T> {
  private static final Logger LOG = LoggerFactory.getLogger(AbstractObjectSerializer.class);
  private static final Object SELF_REFERENCE = new Object();
  // Constructor-bound objects reserve a ref id before constructor arguments are read, but the
  // context package must stay a generic ref owner. Track unresolved constructor refs here so
  // final-field construction does not add context APIs or bind the wrong pending ref id.
  private static final Object CONSTRUCTOR_REF_IDS = new Object();
  private static final Object UNRESOLVED_CONSTRUCTOR_REF_IDS = new Object();
  protected final Config config;
  protected final TypeResolver typeResolver;
  protected final boolean isRecord;
  protected final ObjectCreator<T> objectCreator;
  private SerializationFieldInfo[] fieldInfos;
  private RecordInfo copyRecordInfo;

  protected AbstractObjectSerializer() {
    super();
    this.config = null;
    this.typeResolver = null;
    this.isRecord = false;
    this.objectCreator = null;
  }

  public AbstractObjectSerializer(TypeResolver typeResolver, Class<T> type) {
    this(typeResolver, type, typeResolver.getObjectCreator(type));
  }

  public AbstractObjectSerializer(
      TypeResolver typeResolver, Class<T> type, ObjectCreator<T> objectCreator) {
    super(typeResolver.getConfig(), type);
    this.config = typeResolver.getConfig();
    this.typeResolver = typeResolver;
    this.isRecord = RecordUtils.isRecord(type);
    this.objectCreator = objectCreator;
  }

  static void writeField(
      WriteContext writeContext,
      TypeResolver typeResolver,
      RefWriter refWriter,
      SerializationFieldInfo fieldInfo,
      MemoryBuffer buffer,
      Object fieldValue) {
    writeField(
        writeContext, typeResolver, refWriter, fieldInfo, fieldInfo.refMode, buffer, fieldValue);
  }

  static void writeField(
      WriteContext writeContext,
      TypeResolver typeResolver,
      RefWriter refWriter,
      SerializationFieldInfo fieldInfo,
      RefMode refMode,
      MemoryBuffer buffer,
      Object fieldValue) {
    if (fieldInfo.useDeclaredTypeInfo) {
      Serializer<Object> serializer = fieldInfo.typeInfo.getSerializer();
      if (refMode == RefMode.TRACKING) {
        if (!refWriter.writeRefOrNull(buffer, fieldValue)) {
          serializer.write(writeContext, fieldValue);
        }
      } else if (refMode == RefMode.NULL_ONLY) {
        if (fieldValue == null) {
          buffer.writeByte(Fory.NULL_FLAG);
          return;
        }
        buffer.writeByte(Fory.NOT_NULL_VALUE_FLAG);
        serializer.write(writeContext, fieldValue);
      } else {
        serializer.write(writeContext, fieldValue);
      }
      return;
    }
    if (refMode == RefMode.TRACKING) {
      writeContext.writeRef(fieldValue, fieldInfo.classInfoHolder);
    } else if (refMode == RefMode.NULL_ONLY) {
      if (fieldValue == null) {
        buffer.writeByte(Fory.NULL_FLAG);
        return;
      }
      buffer.writeByte(Fory.NOT_NULL_VALUE_FLAG);
      writeContext.writeNonRef(fieldValue, fieldInfo.classInfoHolder);
    } else {
      writeContext.writeNonRef(fieldValue, fieldInfo.classInfoHolder);
    }
  }

  static Object readField(
      ReadContext readContext,
      TypeResolver typeResolver,
      RefReader refReader,
      SerializationFieldInfo fieldInfo,
      MemoryBuffer buffer) {
    return readField(readContext, typeResolver, refReader, fieldInfo, fieldInfo.refMode, buffer);
  }

  static Object readField(
      ReadContext readContext,
      TypeResolver typeResolver,
      RefReader refReader,
      SerializationFieldInfo fieldInfo,
      RefMode refMode,
      MemoryBuffer buffer) {
    if (fieldInfo.useDeclaredTypeInfo) {
      if (refMode == RefMode.TRACKING) {
        trackConstructorRefRead(readContext, buffer);
        return readContext.readRef(fieldInfo.typeInfo);
      }
      if (refMode != RefMode.NULL_ONLY || buffer.readByte() != Fory.NULL_FLAG) {
        refReader.preserveRefId(-1);
        return readContext.readNonRef(fieldInfo.typeInfo);
      }
      return null;
    }
    if (refMode == RefMode.TRACKING) {
      trackConstructorRefRead(readContext, buffer);
      int nextReadRefId = refReader.tryPreserveRefId(buffer);
      if (nextReadRefId >= Fory.NOT_NULL_VALUE_FLAG) {
        Object value =
            typeResolver
                .readTypeInfo(readContext, fieldInfo.type)
                .getSerializer()
                .read(readContext);
        refReader.setReadRef(nextReadRefId, value);
        return value;
      }
      return refReader.getReadRef();
    }
    if (refMode != RefMode.NULL_ONLY || buffer.readByte() != Fory.NULL_FLAG) {
      TypeInfo typeInfo = typeResolver.readTypeInfo(readContext, fieldInfo.type);
      return typeInfo.getSerializer().read(readContext, RefMode.NONE);
    }
    return null;
  }

  /**
   * Write field value to buffer by reading from the object via fieldAccessor. Handles primitive
   * types, unsigned/compressed numbers, and common types like String with optimized fast paths.
   */
  static void writeBuildInField(
      WriteContext writeContext,
      TypeResolver typeResolver,
      RefWriter refWriter,
      SerializationFieldInfo fieldInfo,
      MemoryBuffer buffer,
      Object obj) {
    // Handle primitive fields with direct memory access
    if (fieldInfo.isPrimitiveField) {
      writePrimitiveFieldValue(buffer, obj, fieldInfo.fieldAccessor, fieldInfo.dispatchId);
      return;
    }
    // Handle non-primitive fields based on refMode
    Object fieldValue = fieldInfo.fieldAccessor.getObject(obj);
    writeBuildInFieldValue(writeContext, typeResolver, refWriter, fieldInfo, buffer, fieldValue);
  }

  /**
   * Write field value to buffer. Handles primitive types, unsigned/compressed numbers, and common
   * types like String with optimized fast paths.
   *
   * <p>This method is the write counterpart of {@link #readBuildInFieldValue(Fory, TypeResolver,
   * RefReader, SerializationFieldInfo, MemoryBuffer)}.
   */
  static void writeBuildInFieldValue(
      WriteContext writeContext,
      TypeResolver typeResolver,
      RefWriter refWriter,
      SerializationFieldInfo fieldInfo,
      MemoryBuffer buffer,
      Object fieldValue) {
    RefMode refMode = fieldInfo.refMode;
    // Handle non-primitive fields based on refMode
    if (refMode == RefMode.NONE) {
      // No null flag - write value directly
      writeNotPrimitiveFieldValue(
          writeContext, typeResolver, refWriter, buffer, fieldValue, fieldInfo);
    } else if (refMode == RefMode.NULL_ONLY) {
      // Write null flag, then value if not null
      if (fieldValue == null) {
        buffer.writeByte(Fory.NULL_FLAG);
        return;
      }
      buffer.writeByte(Fory.NOT_NULL_VALUE_FLAG);
      writeNotPrimitiveFieldValue(
          writeContext, typeResolver, refWriter, buffer, fieldValue, fieldInfo);
    } else {
      writeField(writeContext, typeResolver, refWriter, fieldInfo, buffer, fieldValue);
    }
  }

  /**
   * Write a primitive field value to buffer using the field accessor.
   *
   * @param buffer the buffer to write to
   * @param targetObject the object containing the field
   * @param fieldAccessor the accessor to get the field value
   * @param dispatchId the class ID of the primitive type
   * @return true if dispatchId is not a primitive type and needs further write handling
   */
  static boolean writePrimitiveFieldValue(
      MemoryBuffer buffer, Object targetObject, FieldAccessor fieldAccessor, int dispatchId) {
    switch (dispatchId) {
      case DispatchId.BOOL:
        buffer.writeBoolean(fieldAccessor.getBoolean(targetObject));
        return false;
      case DispatchId.INT8:
        buffer.writeByte(fieldAccessor.getByte(targetObject));
        return false;
      case DispatchId.UINT8:
        buffer.writeByte(fieldAccessor.getInt(targetObject));
        return false;
      case DispatchId.CHAR:
        buffer.writeChar(fieldAccessor.getChar(targetObject));
        return false;
      case DispatchId.INT16:
        buffer.writeInt16(fieldAccessor.getShort(targetObject));
        return false;
      case DispatchId.UINT16:
        buffer.writeInt16((short) fieldAccessor.getInt(targetObject));
        return false;
      case DispatchId.INT32:
        buffer.writeInt32(fieldAccessor.getInt(targetObject));
        return false;
      case DispatchId.UINT32:
        buffer.writeInt32((int) fieldAccessor.getLong(targetObject));
        return false;
      case DispatchId.VARINT32:
        buffer.writeVarInt32(fieldAccessor.getInt(targetObject));
        return false;
      case DispatchId.VAR_UINT32:
        buffer.writeVarUInt32((int) fieldAccessor.getLong(targetObject));
        return false;
      case DispatchId.FLOAT32:
        buffer.writeFloat32(fieldAccessor.getFloat(targetObject));
        return false;
      case DispatchId.INT64:
      case DispatchId.UINT64:
        buffer.writeInt64(fieldAccessor.getLong(targetObject));
        return false;
      case DispatchId.VARINT64:
        buffer.writeVarInt64(fieldAccessor.getLong(targetObject));
        return false;
      case DispatchId.TAGGED_INT64:
        buffer.writeTaggedInt64(fieldAccessor.getLong(targetObject));
        return false;
      case DispatchId.VAR_UINT64:
        buffer.writeVarUInt64(fieldAccessor.getLong(targetObject));
        return false;
      case DispatchId.TAGGED_UINT64:
        buffer.writeTaggedUInt64(fieldAccessor.getLong(targetObject));
        return false;
      case DispatchId.FLOAT64:
        buffer.writeFloat64(fieldAccessor.getDouble(targetObject));
        return false;
      default:
        return true;
    }
  }

  /**
   * Write field value to buffer. This method handle the situation which all fields are not null.
   */
  static void writeNotPrimitiveFieldValue(
      WriteContext writeContext,
      TypeResolver typeResolver,
      RefWriter refWriter,
      MemoryBuffer buffer,
      Object fieldValue,
      SerializationFieldInfo fieldInfo) {
    if (fieldValue == null) {
      throw new IllegalArgumentException(
          "Non-nullable field has null value. In xlang mode, fields are non-nullable by default. "
              + "Use @Nullable on the field type to allow null values.");
    }
    // add time types serialization here.
    switch (fieldInfo.dispatchId) {
      case DispatchId.STRING: // fastpath for string.
        writeContext.writeString((String) fieldValue);
        return;
      case DispatchId.BOOL:
        buffer.writeBoolean((Boolean) fieldValue);
        return;
      case DispatchId.INT8:
        buffer.writeByte((Byte) fieldValue);
        return;
      case DispatchId.UINT8:
        buffer.writeByte((Integer) fieldValue);
        return;
      case DispatchId.EXT_UINT8:
        buffer.writeByte(((UInt8) fieldValue).byteValue());
        return;
      case DispatchId.CHAR:
        buffer.writeChar((Character) fieldValue);
        return;
      case DispatchId.INT16:
        buffer.writeInt16((Short) fieldValue);
        return;
      case DispatchId.UINT16:
        buffer.writeInt16(((Integer) fieldValue).shortValue());
        return;
      case DispatchId.EXT_UINT16:
        buffer.writeInt16(((UInt16) fieldValue).shortValue());
        return;
      case DispatchId.INT32:
        buffer.writeInt32((Integer) fieldValue);
        return;
      case DispatchId.UINT32:
        buffer.writeInt32(((Long) fieldValue).intValue());
        return;
      case DispatchId.EXT_UINT32:
        buffer.writeInt32(((UInt32) fieldValue).intValue());
        return;
      case DispatchId.VARINT32:
        buffer.writeVarInt32((Integer) fieldValue);
        return;
      case DispatchId.VAR_UINT32:
        buffer.writeVarUInt32(((Long) fieldValue).intValue());
        return;
      case DispatchId.EXT_VAR_UINT32:
        buffer.writeVarUInt32(((UInt32) fieldValue).intValue());
        return;
      case DispatchId.INT64:
        buffer.writeInt64((Long) fieldValue);
        return;
      case DispatchId.UINT64:
        buffer.writeInt64((Long) fieldValue);
        return;
      case DispatchId.EXT_UINT64:
        buffer.writeInt64(((UInt64) fieldValue).longValue());
        return;
      case DispatchId.VARINT64:
        buffer.writeVarInt64((Long) fieldValue);
        return;
      case DispatchId.TAGGED_INT64:
        buffer.writeTaggedInt64((Long) fieldValue);
        return;
      case DispatchId.VAR_UINT64:
        buffer.writeVarUInt64((Long) fieldValue);
        return;
      case DispatchId.EXT_VAR_UINT64:
        buffer.writeVarUInt64(((UInt64) fieldValue).longValue());
        return;
      case DispatchId.TAGGED_UINT64:
        buffer.writeTaggedUInt64((Long) fieldValue);
        return;
      case DispatchId.FLOAT32:
        buffer.writeFloat32((Float) fieldValue);
        return;
      case DispatchId.FLOAT64:
        buffer.writeFloat64((Double) fieldValue);
        return;
      case DispatchId.FLOAT16:
        buffer.writeInt16(((Float16) fieldValue).toBits());
        return;
      case DispatchId.BFLOAT16:
        buffer.writeInt16(((BFloat16) fieldValue).toBits());
        return;
      default:
        writeField(
            writeContext, typeResolver, refWriter, fieldInfo, RefMode.NONE, buffer, fieldValue);
    }
  }

  static void writeContainerFieldValue(
      WriteContext writeContext,
      TypeResolver typeResolver,
      RefWriter refWriter,
      Generics generics,
      SerializationFieldInfo fieldInfo,
      MemoryBuffer buffer,
      Object fieldValue) {
    if (fieldInfo.refMode == RefMode.TRACKING) {
      if (refWriter.writeRefOrNull(buffer, fieldValue)) {
        return;
      }
    } else if (fieldInfo.refMode == RefMode.NULL_ONLY) {
      if (fieldValue == null) {
        buffer.writeByte(Fory.NULL_FLAG);
        return;
      }
      buffer.writeByte(Fory.NOT_NULL_VALUE_FLAG);
    }
    generics.pushGenericType(fieldInfo.genericType, writeContext.getDepth());
    if (fieldInfo.containerSerializerOverride != null) {
      writeContext.increaseDepth();
      ((Serializer) fieldInfo.containerSerializerOverride).write(writeContext, fieldValue);
      writeContext.decreaseDepth();
    } else if (writeContext.isCrossLanguage() && fieldInfo.useDeclaredTypeInfo) {
      TypeInfo typeInfo =
          typeResolver.getTypeInfo(fieldValue.getClass(), fieldInfo.classInfoHolder);
      writeContext.writeData(typeInfo, fieldValue);
    } else if (fieldInfo.useDeclaredTypeInfo) {
      TypeInfo typeInfo =
          typeResolver.getTypeInfo(fieldValue.getClass(), fieldInfo.classInfoHolder);
      writeContext.writeNonRef(fieldValue, typeInfo);
    } else {
      writeContext.writeNonRef(fieldValue, fieldInfo.classInfoHolder);
    }
    generics.popGenericType(writeContext.getDepth());
  }

  /**
   * Read a container field value (Collection or Map). Handles reference tracking, nullable fields,
   * and pushes/pops generic type information for proper deserialization of parameterized types.
   *
   * @param typeResolver resolver used for type metadata read
   * @param refReader resolver used for reference tracking
   * @param generics the generics context for tracking parameterized types
   * @param fieldInfo the field metadata including generic type info and nullability
   * @param buffer the buffer to read from
   * @return the deserialized container field value, or null if the field is nullable and was null
   */
  static Object readContainerFieldValue(
      ReadContext readContext,
      TypeResolver typeResolver,
      RefReader refReader,
      Generics generics,
      SerializationFieldInfo fieldInfo,
      MemoryBuffer buffer) {
    Object fieldValue;
    switch (fieldInfo.refMode) {
      case NONE:
        refReader.preserveRefId(-1);
        generics.pushGenericType(fieldInfo.genericType, readContext.getDepth());
        fieldValue = readContainerFieldValueNoRef(readContext, fieldInfo);
        generics.popGenericType(readContext.getDepth());
        break;
      case NULL_ONLY:
        {
          refReader.preserveRefId(-1);
          byte headFlag = buffer.readByte();
          if (headFlag == Fory.NULL_FLAG) {
            return null;
          }
          generics.pushGenericType(fieldInfo.genericType, readContext.getDepth());
          fieldValue = readContainerFieldValueNoRef(readContext, fieldInfo);
          generics.popGenericType(readContext.getDepth());
        }
        break;
      case TRACKING:
        generics.pushGenericType(fieldInfo.genericType, readContext.getDepth());
        fieldValue =
            readContainerFieldValueRef(readContext, typeResolver, refReader, fieldInfo, buffer);
        generics.popGenericType(readContext.getDepth());
        break;
      default:
        throw new IllegalStateException("Unknown refMode: " + fieldInfo.refMode);
    }
    return fieldValue;
  }

  private static Object readContainerFieldValueNoRef(
      ReadContext readContext, SerializationFieldInfo fieldInfo) {
    if (fieldInfo.containerSerializerOverride != null) {
      return readContext.readNonRef(fieldInfo.containerSerializerOverride);
    }
    if (readContext.getConfig().isXlang()) {
      return readContext.readNonRef(fieldInfo.containerTypeInfo);
    }
    return readContext.readNonRef(fieldInfo.classInfoHolder);
  }

  private static Object readContainerFieldValueRef(
      ReadContext readContext,
      TypeResolver typeResolver,
      RefReader refReader,
      SerializationFieldInfo fieldInfo,
      MemoryBuffer buffer) {
    int nextReadRefId = refReader.tryPreserveRefId(buffer);
    if (nextReadRefId >= Fory.NOT_NULL_VALUE_FLAG) {
      Object value;
      if (fieldInfo.containerSerializerOverride != null) {
        value = readContext.readNonRef(fieldInfo.containerSerializerOverride);
      } else if (readContext.getConfig().isXlang()) {
        value = readContext.readNonRef(fieldInfo.containerTypeInfo);
      } else {
        value = readContext.readData(typeResolver.readTypeInfo(readContext));
      }
      refReader.setReadRef(nextReadRefId, value);
      return value;
    }
    return refReader.getReadRef();
  }

  /**
   * Read field value from buffer and return it. Handles primitive types, unsigned/compressed
   * numbers, and common types like String with optimized fast paths.
   *
   * <p>This method is similar to {@link #readBuildInFieldValue(ReadContext, TypeResolver,
   * RefReader, SerializationFieldInfo, MemoryBuffer)}, but returns the field value instead of
   * setting it into the target object.
   *
   * <p>The refMode determines how to read the value from buffer: - RefMode.NONE: read directly
   * without null flag - RefMode.NULL_ONLY: read null flag first, then value if not null -
   * RefMode.TRACKING: use reference tracking
   *
   * @param typeResolver resolver used for type metadata read
   * @param refReader resolver used for reference tracking
   * @param fieldInfo the field metadata including type and nullability info
   * @param buffer the buffer to read from
   * @return the deserialized field value, or null if the field is nullable and was null
   * @see #readBuildInFieldValue(ReadContext, TypeResolver, RefReader, SerializationFieldInfo,
   *     MemoryBuffer)
   */
  static Object readBuildInFieldValue(
      ReadContext readContext,
      TypeResolver typeResolver,
      RefReader refReader,
      SerializationFieldInfo fieldInfo,
      MemoryBuffer buffer) {
    int dispatchId = fieldInfo.dispatchId;
    RefMode refMode = fieldInfo.refMode;
    // Use refMode to determine if there's a null flag prefix in the stream
    if (refMode == RefMode.NONE) {
      // No null flag in stream - read directly
      return readNotNullBuildInFieldValue(
          readContext, typeResolver, refReader, buffer, fieldInfo, dispatchId);
    } else if (refMode == RefMode.NULL_ONLY) {
      // Read null flag from buffer
      if (buffer.readByte() == Fory.NULL_FLAG) {
        return null;
      }
      return readNotNullBuildInFieldValue(
          readContext, typeResolver, refReader, buffer, fieldInfo, dispatchId);
    }
    return readField(readContext, typeResolver, refReader, fieldInfo, buffer);
  }

  /**
   * Handle all numeric fields read include unsigned and compressed numbers. It also include
   * fastpath for common type such as String.
   */
  static void readBuildInFieldValue(
      ReadContext readContext,
      TypeResolver typeResolver,
      RefReader refReader,
      SerializationFieldInfo fieldInfo,
      MemoryBuffer buffer,
      Object targetObject) {
    FieldAccessor fieldAccessor = fieldInfo.fieldAccessor;
    int dispatchId = fieldInfo.dispatchId;
    if (fieldInfo.refMode == RefMode.NONE) {
      if (fieldInfo.isPrimitiveField) {
        readPrimitiveFieldValue(buffer, targetObject, fieldAccessor, dispatchId);
      } else {
        readNotPrimitiveFieldValue(
            readContext, typeResolver, refReader, buffer, targetObject, fieldInfo, dispatchId);
      }
    } else if (fieldInfo.refMode == RefMode.NULL_ONLY) {
      if (buffer.readByte() == Fory.NULL_FLAG) {
        return;
      }
      if (fieldInfo.isPrimitiveField) {
        readPrimitiveFieldValue(buffer, targetObject, fieldAccessor, dispatchId);
      } else {
        readNotPrimitiveFieldValue(
            readContext, typeResolver, refReader, buffer, targetObject, fieldInfo, dispatchId);
      }
    } else {
      Object fieldValue = readField(readContext, typeResolver, refReader, fieldInfo, buffer);
      fieldAccessor.putObject(targetObject, fieldValue);
    }
  }

  /**
   * Read a non-nullable basic object value from buffer and return it. Handles PRIMITIVE_*, and
   * STRING dispatch IDs with optimized fast paths.
   */
  private static Object readNotNullBuildInFieldValue(
      ReadContext readContext,
      TypeResolver typeResolver,
      RefReader refReader,
      MemoryBuffer buffer,
      SerializationFieldInfo fieldInfo,
      int dispatchId) {
    switch (dispatchId) {
      case DispatchId.BOOL:
        return buffer.readBoolean();
      case DispatchId.INT8:
        return buffer.readByte();
      case DispatchId.UINT8:
        return buffer.readByte() & 0xFF;
      case DispatchId.EXT_UINT8:
        return UInt8.valueOf(buffer.readByte());
      case DispatchId.CHAR:
        return buffer.readChar();
      case DispatchId.INT16:
        return buffer.readInt16();
      case DispatchId.UINT16:
        return buffer.readInt16() & 0xFFFF;
      case DispatchId.EXT_UINT16:
        return UInt16.valueOf(buffer.readInt16());
      case DispatchId.INT32:
        return buffer.readInt32();
      case DispatchId.UINT32:
        return Integer.toUnsignedLong(buffer.readInt32());
      case DispatchId.EXT_UINT32:
        return UInt32.valueOf(buffer.readInt32());
      case DispatchId.VARINT32:
        return buffer.readVarInt32();
      case DispatchId.VAR_UINT32:
        return Integer.toUnsignedLong(buffer.readVarUInt32());
      case DispatchId.EXT_VAR_UINT32:
        return UInt32.valueOf(buffer.readVarUInt32());
      case DispatchId.INT64:
        return buffer.readInt64();
      case DispatchId.UINT64:
        return buffer.readInt64();
      case DispatchId.EXT_UINT64:
        return UInt64.valueOf(buffer.readInt64());
      case DispatchId.VARINT64:
        return buffer.readVarInt64();
      case DispatchId.TAGGED_INT64:
        return buffer.readTaggedInt64();
      case DispatchId.VAR_UINT64:
        return buffer.readVarUInt64();
      case DispatchId.EXT_VAR_UINT64:
        return UInt64.valueOf(buffer.readVarUInt64());
      case DispatchId.TAGGED_UINT64:
        return buffer.readTaggedUInt64();
      case DispatchId.FLOAT32:
        return buffer.readFloat32();
      case DispatchId.FLOAT64:
        return buffer.readFloat64();
      case DispatchId.FLOAT16:
        return Float16.fromBits(buffer.readInt16());
      case DispatchId.BFLOAT16:
        return BFloat16.fromBits(buffer.readInt16());
      case DispatchId.STRING:
        return readContext.readString();
      default:
        return readField(readContext, typeResolver, refReader, fieldInfo, RefMode.NONE, buffer);
    }
  }

  /**
   * Read a primitive value from buffer and set it to field referenced by <code>fieldAccessor</code>
   * of <code>targetObject</code>.
   */
  private static void readPrimitiveFieldValue(
      MemoryBuffer buffer, Object targetObject, FieldAccessor fieldAccessor, int dispatchId) {
    // we still need `PRIMITIVE` cases since peer may send
    switch (dispatchId) {
      case DispatchId.BOOL:
        fieldAccessor.putBoolean(targetObject, buffer.readBoolean());
        return;
      case DispatchId.INT8:
        fieldAccessor.putByte(targetObject, buffer.readByte());
        return;
      case DispatchId.UINT8:
        fieldAccessor.putInt(targetObject, buffer.readByte() & 0xFF);
        return;
      case DispatchId.CHAR:
        fieldAccessor.putChar(targetObject, buffer.readChar());
        return;
      case DispatchId.INT16:
        fieldAccessor.putShort(targetObject, buffer.readInt16());
        return;
      case DispatchId.UINT16:
        fieldAccessor.putInt(targetObject, buffer.readInt16() & 0xFFFF);
        return;
      case DispatchId.INT32:
        fieldAccessor.putInt(targetObject, buffer.readInt32());
        return;
      case DispatchId.UINT32:
        fieldAccessor.putLong(targetObject, Integer.toUnsignedLong(buffer.readInt32()));
        return;
      case DispatchId.VARINT32:
        fieldAccessor.putInt(targetObject, buffer.readVarInt32());
        return;
      case DispatchId.VAR_UINT32:
        fieldAccessor.putLong(targetObject, Integer.toUnsignedLong(buffer.readVarUInt32()));
        return;
      case DispatchId.FLOAT32:
        fieldAccessor.putFloat(targetObject, buffer.readFloat32());
        return;
      case DispatchId.INT64:
      case DispatchId.UINT64:
        fieldAccessor.putLong(targetObject, buffer.readInt64());
        return;
      case DispatchId.VARINT64:
        fieldAccessor.putLong(targetObject, buffer.readVarInt64());
        return;
      case DispatchId.TAGGED_INT64:
        fieldAccessor.putLong(targetObject, buffer.readTaggedInt64());
        return;
      case DispatchId.VAR_UINT64:
        fieldAccessor.putLong(targetObject, buffer.readVarUInt64());
        return;
      case DispatchId.TAGGED_UINT64:
        fieldAccessor.putLong(targetObject, buffer.readTaggedUInt64());
        return;
      case DispatchId.FLOAT64:
        fieldAccessor.putDouble(targetObject, buffer.readFloat64());
        return;
      default:
        throw new IllegalArgumentException("Unsupported dispatch id " + dispatchId);
    }
  }

  /**
   * Read field value from buffer and set it on the target object. This method handles PRIMITIVE_*
   * and NOTNULL_BOXED_* dispatch IDs where null values are not allowed.
   */
  private static void readNotPrimitiveFieldValue(
      ReadContext readContext,
      TypeResolver typeResolver,
      RefReader refReader,
      MemoryBuffer buffer,
      Object targetObject,
      SerializationFieldInfo fieldInfo,
      int dispatchId) {
    FieldAccessor fieldAccessor = fieldInfo.fieldAccessor;
    switch (dispatchId) {
      case DispatchId.BOOL:
        fieldAccessor.putObject(targetObject, buffer.readBoolean());
        return;
      case DispatchId.INT8:
        fieldAccessor.putObject(targetObject, buffer.readByte());
        return;
      case DispatchId.UINT8:
        fieldAccessor.putObject(targetObject, buffer.readByte() & 0xFF);
        return;
      case DispatchId.EXT_UINT8:
        fieldAccessor.putObject(targetObject, UInt8.valueOf(buffer.readByte()));
        return;
      case DispatchId.CHAR:
        fieldAccessor.putObject(targetObject, buffer.readChar());
        return;
      case DispatchId.INT16:
        fieldAccessor.putObject(targetObject, buffer.readInt16());
        return;
      case DispatchId.UINT16:
        fieldAccessor.putObject(targetObject, buffer.readInt16() & 0xFFFF);
        return;
      case DispatchId.EXT_UINT16:
        fieldAccessor.putObject(targetObject, UInt16.valueOf(buffer.readInt16()));
        return;
      case DispatchId.INT32:
        fieldAccessor.putObject(targetObject, buffer.readInt32());
        return;
      case DispatchId.UINT32:
        fieldAccessor.putObject(targetObject, Integer.toUnsignedLong(buffer.readInt32()));
        return;
      case DispatchId.EXT_UINT32:
        fieldAccessor.putObject(targetObject, UInt32.valueOf(buffer.readInt32()));
        return;
      case DispatchId.VARINT32:
        fieldAccessor.putObject(targetObject, buffer.readVarInt32());
        return;
      case DispatchId.VAR_UINT32:
        fieldAccessor.putObject(targetObject, Integer.toUnsignedLong(buffer.readVarUInt32()));
        return;
      case DispatchId.EXT_VAR_UINT32:
        fieldAccessor.putObject(targetObject, UInt32.valueOf(buffer.readVarUInt32()));
        return;
      case DispatchId.INT64:
        fieldAccessor.putObject(targetObject, buffer.readInt64());
        return;
      case DispatchId.UINT64:
        fieldAccessor.putObject(targetObject, buffer.readInt64());
        return;
      case DispatchId.EXT_UINT64:
        fieldAccessor.putObject(targetObject, UInt64.valueOf(buffer.readInt64()));
        return;
      case DispatchId.VARINT64:
        fieldAccessor.putObject(targetObject, buffer.readVarInt64());
        return;
      case DispatchId.TAGGED_INT64:
        fieldAccessor.putObject(targetObject, buffer.readTaggedInt64());
        return;
      case DispatchId.VAR_UINT64:
        fieldAccessor.putObject(targetObject, buffer.readVarUInt64());
        return;
      case DispatchId.EXT_VAR_UINT64:
        fieldAccessor.putObject(targetObject, UInt64.valueOf(buffer.readVarUInt64()));
        return;
      case DispatchId.TAGGED_UINT64:
        fieldAccessor.putObject(targetObject, buffer.readTaggedUInt64());
        return;
      case DispatchId.FLOAT32:
        fieldAccessor.putObject(targetObject, buffer.readFloat32());
        return;
      case DispatchId.FLOAT64:
        fieldAccessor.putObject(targetObject, buffer.readFloat64());
        return;
      case DispatchId.FLOAT16:
        fieldAccessor.putObject(targetObject, Float16.fromBits(buffer.readInt16()));
        return;
      case DispatchId.BFLOAT16:
        fieldAccessor.putObject(targetObject, BFloat16.fromBits(buffer.readInt16()));
        return;
      case DispatchId.STRING:
        fieldAccessor.putObject(targetObject, readContext.readString());
        return;
      default:
        // Use RefMode.NONE because null flag was already handled by caller
        Object fieldValue =
            readField(readContext, typeResolver, refReader, fieldInfo, RefMode.NONE, buffer);
        fieldAccessor.putObject(targetObject, fieldValue);
    }
  }

  @Override
  public T copy(CopyContext copyContext, T originObj) {
    if (immutable) {
      return originObj;
    }
    if (isRecord) {
      return copyRecord(copyContext, originObj);
    }
    if (objectCreator.hasConstructorFields()) {
      return copyConstructorObject(copyContext, originObj);
    }
    T newObj = newBean();
    copyContext.reference(originObj, newObj);
    copyFields(copyContext, originObj, newObj);
    return newObj;
  }

  private T copyRecord(CopyContext copyContext, T originObj) {
    Object[] fieldValues = copyFieldValues(copyContext, originObj);
    fieldValues = RecordUtils.remapping(copyRecordInfo, fieldValues);
    try {
      T t = objectCreator.newInstanceWithArguments(fieldValues);
      Arrays.fill(copyRecordInfo.getRecordComponents(), null);
      copyContext.reference(originObj, t);
      return t;
    } catch (Throwable e) {
      ExceptionUtils.throwException(e);
    }
    return originObj;
  }

  private T copyConstructorObject(CopyContext copyContext, T originObj) {
    SerializationFieldInfo[] fieldInfos = this.fieldInfos;
    if (fieldInfos == null) {
      fieldInfos = buildFieldsInfo();
    }
    int[] constructorFieldIndexes = buildConstructorFieldIndexes(fieldInfos);
    boolean[] constructorFieldMask =
        buildConstructorFieldMask(fieldInfos.length, constructorFieldIndexes);
    checkNoSelfConstructorField(originObj, fieldInfos, constructorFieldMask);
    Object[] fieldValues =
        copyFieldValues(copyContext, originObj, fieldInfos, constructorFieldMask, true);
    T newObj =
        objectCreator.newInstanceWithArguments(
            constructorArgs(
                fieldValues, constructorFieldIndexes, objectCreator.getConstructorFieldTypes()));
    copyContext.reference(originObj, newObj);
    copyFields(copyContext, fieldInfos, originObj, newObj, constructorFieldMask, false);
    return newObj;
  }

  private void checkNoSelfConstructorField(
      T originObj, SerializationFieldInfo[] fieldInfos, boolean[] constructorFieldMask) {
    for (int i = 0; i < fieldInfos.length; i++) {
      if (constructorFieldMask[i] && !fieldInfos[i].isPrimitiveField) {
        Object fieldValue = fieldInfos[i].fieldAccessor.getObject(originObj);
        if (fieldValue == originObj) {
          throwConstructorCycle(type);
        }
      }
    }
  }

  private Object[] copyFieldValues(CopyContext copyContext, T originObj) {
    SerializationFieldInfo[] fieldInfos = this.fieldInfos;
    if (fieldInfos == null) {
      fieldInfos = buildFieldsInfo();
    }
    Object[] fieldValues = new Object[fieldInfos.length];
    for (int i = 0; i < fieldInfos.length; i++) {
      SerializationFieldInfo fieldInfo = fieldInfos[i];
      FieldAccessor fieldAccessor = fieldInfo.fieldAccessor;
      if (fieldInfo.isPrimitiveField) {
        fieldValues[i] = copyPrimitiveField(originObj, fieldAccessor, fieldInfo.dispatchId);
      } else {
        fieldValues[i] =
            copyNotPrimitiveField(copyContext, originObj, fieldAccessor, fieldInfo.dispatchId);
      }
    }
    return fieldValues;
  }

  private Object[] copyFieldValues(
      CopyContext copyContext,
      T originObj,
      SerializationFieldInfo[] fieldInfos,
      boolean[] constructorFieldMask,
      boolean constructorFields) {
    Object[] fieldValues = new Object[fieldInfos.length];
    for (int i = 0; i < fieldInfos.length; i++) {
      if (constructorFieldMask[i] != constructorFields) {
        continue;
      }
      SerializationFieldInfo fieldInfo = fieldInfos[i];
      FieldAccessor fieldAccessor = fieldInfo.fieldAccessor;
      if (fieldInfo.isPrimitiveField) {
        fieldValues[i] = copyPrimitiveField(originObj, fieldAccessor, fieldInfo.dispatchId);
      } else {
        fieldValues[i] =
            copyNotPrimitiveField(copyContext, originObj, fieldAccessor, fieldInfo.dispatchId);
      }
    }
    return fieldValues;
  }

  private void copyFields(CopyContext copyContext, T originObj, T newObj) {
    SerializationFieldInfo[] fieldInfos = this.fieldInfos;
    if (fieldInfos == null) {
      fieldInfos = buildFieldsInfo();
    }
    copyFields(copyContext, fieldInfos, originObj, newObj);
  }

  public static void copyFields(
      CopyContext copyContext,
      SerializationFieldInfo[] fieldInfos,
      Object originObj,
      Object newObj) {
    for (SerializationFieldInfo fieldInfo : fieldInfos) {
      FieldAccessor fieldAccessor = fieldInfo.fieldAccessor;
      if (fieldInfo.isPrimitiveField) {
        copySetPrimitiveField(originObj, newObj, fieldAccessor, fieldInfo.dispatchId);
      } else {
        copySetNotPrimitiveField(
            copyContext, originObj, newObj, fieldAccessor, fieldInfo.dispatchId);
      }
    }
  }

  private static void copyFields(
      CopyContext copyContext,
      SerializationFieldInfo[] fieldInfos,
      Object originObj,
      Object newObj,
      boolean[] constructorFieldMask,
      boolean constructorFields) {
    for (int i = 0; i < fieldInfos.length; i++) {
      if (constructorFieldMask[i] != constructorFields) {
        continue;
      }
      SerializationFieldInfo fieldInfo = fieldInfos[i];
      FieldAccessor fieldAccessor = fieldInfo.fieldAccessor;
      if (fieldInfo.isPrimitiveField) {
        copySetPrimitiveField(originObj, newObj, fieldAccessor, fieldInfo.dispatchId);
      } else {
        copySetNotPrimitiveField(
            copyContext, originObj, newObj, fieldAccessor, fieldInfo.dispatchId);
      }
    }
  }

  private static Object copyFieldValue(CopyContext copyContext, Object fieldValue, int dispatchId) {
    if (fieldValue == null) {
      return null;
    }
    return isCopyByReference(dispatchId) ? fieldValue : copyContext.copyObject(fieldValue);
  }

  private static boolean isCopyByReference(int dispatchId) {
    switch (dispatchId) {
      case DispatchId.BOOL:
      case DispatchId.INT8:
      case DispatchId.UINT8:
      case DispatchId.EXT_UINT8:
      case DispatchId.CHAR:
      case DispatchId.INT16:
      case DispatchId.UINT16:
      case DispatchId.EXT_UINT16:
      case DispatchId.INT32:
      case DispatchId.VARINT32:
      case DispatchId.UINT32:
      case DispatchId.EXT_UINT32:
      case DispatchId.VAR_UINT32:
      case DispatchId.EXT_VAR_UINT32:
      case DispatchId.INT64:
      case DispatchId.VARINT64:
      case DispatchId.TAGGED_INT64:
      case DispatchId.UINT64:
      case DispatchId.EXT_UINT64:
      case DispatchId.VAR_UINT64:
      case DispatchId.EXT_VAR_UINT64:
      case DispatchId.TAGGED_UINT64:
      case DispatchId.FLOAT32:
      case DispatchId.FLOAT64:
      case DispatchId.FLOAT16:
      case DispatchId.BFLOAT16:
      case DispatchId.STRING:
        return true;
      default:
        return false;
    }
  }

  private static void copySetPrimitiveField(
      Object originObj, Object newObj, FieldAccessor fieldAccessor, int typeId) {
    fieldAccessor.copy(originObj, newObj);
  }

  private static void copySetNotPrimitiveField(
      CopyContext copyContext,
      Object originObj,
      Object newObj,
      FieldAccessor fieldAccessor,
      int typeId) {
    if (isCopyByReference(typeId)) {
      fieldAccessor.copyObject(originObj, newObj);
      return;
    }
    Object fieldValue = fieldAccessor.getObject(originObj);
    fieldAccessor.putObject(newObj, fieldValue == null ? null : copyContext.copyObject(fieldValue));
  }

  private Object copyPrimitiveField(Object targetObject, FieldAccessor fieldAccessor, int typeId) {
    switch (typeId) {
      case DispatchId.BOOL:
        return fieldAccessor.getBoolean(targetObject);
      case DispatchId.INT8:
        return fieldAccessor.getByte(targetObject);
      case DispatchId.UINT8:
        return fieldAccessor.getInt(targetObject);
      case DispatchId.CHAR:
        return fieldAccessor.getChar(targetObject);
      case DispatchId.INT16:
        return fieldAccessor.getShort(targetObject);
      case DispatchId.UINT16:
        return fieldAccessor.getInt(targetObject);
      case DispatchId.INT32:
      case DispatchId.VARINT32:
        return fieldAccessor.getInt(targetObject);
      case DispatchId.UINT32:
      case DispatchId.VAR_UINT32:
        return fieldAccessor.getLong(targetObject);
      case DispatchId.FLOAT32:
        return fieldAccessor.getFloat(targetObject);
      case DispatchId.INT64:
      case DispatchId.VARINT64:
      case DispatchId.TAGGED_INT64:
      case DispatchId.UINT64:
      case DispatchId.VAR_UINT64:
      case DispatchId.TAGGED_UINT64:
        return fieldAccessor.getLong(targetObject);
      case DispatchId.FLOAT64:
        return fieldAccessor.getDouble(targetObject);
      default:
        throw new RuntimeException("Unknown primitive type: " + typeId);
    }
  }

  private Object copyNotPrimitiveField(
      CopyContext copyContext, Object targetObject, FieldAccessor fieldAccessor, int typeId) {
    return copyFieldValue(copyContext, fieldAccessor.getObject(targetObject), typeId);
  }

  private SerializationFieldInfo[] buildFieldsInfo() {
    List<Descriptor> descriptors = new ArrayList<>();
    if (RecordUtils.isRecord(type)) {
      RecordComponent[] components = RecordUtils.getRecordComponents(type);
      assert components != null;
      try {
        for (RecordComponent component : components) {
          Field field = type.getDeclaredField(component.getName());
          TypeRef<?> typeRef = TypeUtils.getRecordComponentTypeRef(component);
          descriptors.add(
              new Descriptor(
                  field,
                  typeRef,
                  component.getAccessor(),
                  null,
                  TypeUtils.isNullable(typeRef, false)));
        }
      } catch (NoSuchFieldException e) {
        // impossible
        ExceptionUtils.throwException(e);
      }
    } else {
      for (Field field : ReflectionUtils.getFields(type, true)) {
        if (!Modifier.isStatic(field.getModifiers())) {
          TypeRef<?> typeRef = TypeUtils.getFieldTypeRef(field);
          descriptors.add(new Descriptor(field, typeRef, null, null));
        }
      }
    }
    DescriptorGrouper descriptorGrouper =
        FieldGroups.buildDescriptorGrouper(typeResolver, descriptors, false, null);
    FieldGroups fieldGroups = FieldGroups.buildFieldInfos(typeResolver, descriptorGrouper);
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

  protected final int[] buildConstructorFieldIndexes(SerializationFieldInfo[] fieldInfos) {
    return buildConstructorFieldIndexes(fieldInfos, true);
  }

  protected final int[] buildConstructorFieldIndexes(
      SerializationFieldInfo[] fieldInfos, boolean allowMissingNonFinal) {
    return buildConstructorFieldIndexes(fieldInfos, allowMissingNonFinal, null);
  }

  protected final int[] buildConstructorFieldIndexes(
      SerializationFieldInfo[] fieldInfos, boolean allowMissingNonFinal, String[] defaultFields) {
    return buildConstructorFieldIndexes(fieldInfos, allowMissingNonFinal, defaultFields, null);
  }

  protected final int[] buildConstructorFieldIndexes(
      SerializationFieldInfo[] fieldInfos,
      boolean allowMissingNonFinal,
      String[] defaultFields,
      Class<?>[] defaultDeclaringClasses) {
    String[] fieldNames = objectCreator.getConstructorFieldNames();
    if (fieldNames.length == 0) {
      return null;
    }
    Class<?>[] declaringClasses = objectCreator.getConstructorFieldDeclaringClasses();
    boolean[] finalFields = objectCreator.getConstructorFieldFinal();
    int[] indexes = new int[fieldNames.length];
    for (int i = 0; i < fieldNames.length; i++) {
      Class<?> declaringClass = declaringClasses == null ? null : declaringClasses[i];
      boolean allowMissing =
          (allowMissingNonFinal && !finalFields[i])
              || contains(defaultFields, defaultDeclaringClasses, fieldNames[i], declaringClass);
      indexes[i] = constructorFieldIndex(fieldInfos, declaringClass, fieldNames[i], allowMissing);
    }
    return indexes;
  }

  private static boolean contains(
      String[] values, Class<?>[] declaringClasses, String value, Class<?> declaringClass) {
    if (values == null) {
      return false;
    }
    for (int i = 0; i < values.length; i++) {
      if (values[i].equals(value)
          && (declaringClasses == null
              || i >= declaringClasses.length
              || declaringClasses[i] == null
              || declaringClasses[i] == declaringClass)) {
        return true;
      }
    }
    return false;
  }

  protected final boolean[] buildConstructorFieldMask(int size, int[] indexes) {
    if (indexes == null) {
      return null;
    }
    boolean[] mask = new boolean[size];
    for (int index : indexes) {
      if (index >= 0) {
        mask[index] = true;
      }
    }
    return mask;
  }

  protected final Object[] constructorArgs(Object[] fieldValues, int[] indexes) {
    Object[] args = new Object[indexes.length];
    for (int i = 0; i < indexes.length; i++) {
      args[i] = fieldValues[indexes[i]];
    }
    return args;
  }

  protected final Object[] constructorArgs(
      Object[] fieldValues, int[] indexes, Class<?>[] fieldTypes) {
    Object[] args = new Object[indexes.length];
    for (int i = 0; i < indexes.length; i++) {
      int index = indexes[i];
      args[i] = index < 0 ? defaultConstructorValue(fieldTypes[i]) : fieldValues[index];
    }
    return args;
  }

  protected final void checkNoUnresolvedReadRef(ReadContext readContext) {
    checkNoUnresolvedReadRef(readContext, type);
  }

  public static void checkNoUnresolvedReadRef(ReadContext readContext, Class<?> type) {
    if (consumeSelfRef(readContext)) {
      throwConstructorCycle(type);
    }
  }

  public static void beginConstructorRef(ReadContext readContext) {
    if (readContext.hasPreservedRefId()) {
      constructorRefIds(readContext).add(readContext.lastPreservedRefId());
    }
  }

  public static void endConstructorRef(ReadContext readContext) {
    IntArray refIds = (IntArray) readContext.getContextObject(CONSTRUCTOR_REF_IDS);
    if (refIds != null && refIds.size > 0) {
      refIds.pop();
    }
  }

  public static void referenceConstructorRef(ReadContext readContext, Object object) {
    int constructorRefId = currentConstructorRefId(readContext);
    if (constructorRefId >= 0) {
      readContext.setReadRef(constructorRefId, object);
      return;
    }
    if (readContext.hasPreservedRefId()) {
      readContext.reference(object);
    }
  }

  public static Object ctorFieldValue(ReadContext readContext, Object value, Class<?> type) {
    if (consumeSelfRef(readContext)) {
      throwConstructorCycle(type);
    }
    return value;
  }

  public static Object bufferFieldValue(ReadContext readContext, Object value, Class<?> type) {
    if (!consumeSelfRef(readContext)) {
      return value;
    }
    if (value == null) {
      return SELF_REFERENCE;
    }
    throwConstructorCycle(type);
    return null;
  }

  public static Object resolveBufferedValue(Object value, Object targetObject) {
    return value == SELF_REFERENCE ? targetObject : value;
  }

  private static boolean consumeSelfRef(ReadContext readContext) {
    int constructorRefId = currentConstructorRefId(readContext);
    return constructorRefId >= 0 && consumeUnresolvedConstructorRef(readContext, constructorRefId);
  }

  public static void trackConstructorRefRead(ReadContext readContext, MemoryBuffer buffer) {
    IntArray constructorRefIds = (IntArray) readContext.getContextObject(CONSTRUCTOR_REF_IDS);
    if (constructorRefIds == null || constructorRefIds.size == 0) {
      return;
    }
    int readerIndex = buffer.readerIndex();
    if (buffer.getByte(readerIndex) != Fory.REF_FLAG) {
      return;
    }
    int refId;
    try {
      buffer.readerIndex(readerIndex + 1);
      refId = buffer.readVarUInt32Small14();
    } finally {
      buffer.readerIndex(readerIndex);
    }
    if (readContext.getReadRef(refId) == null && containsRefId(constructorRefIds, refId)) {
      unresolvedConstructorRefIds(readContext).add(refId);
    }
  }

  private static IntArray constructorRefIds(ReadContext readContext) {
    IntArray refIds = (IntArray) readContext.getContextObject(CONSTRUCTOR_REF_IDS);
    if (refIds == null) {
      refIds = new IntArray(4);
      readContext.putContextObject(CONSTRUCTOR_REF_IDS, refIds);
    }
    return refIds;
  }

  private static IntArray unresolvedConstructorRefIds(ReadContext readContext) {
    IntArray refIds = (IntArray) readContext.getContextObject(UNRESOLVED_CONSTRUCTOR_REF_IDS);
    if (refIds == null) {
      refIds = new IntArray(4);
      readContext.putContextObject(UNRESOLVED_CONSTRUCTOR_REF_IDS, refIds);
    }
    return refIds;
  }

  private static int currentConstructorRefId(ReadContext readContext) {
    IntArray refIds = (IntArray) readContext.getContextObject(CONSTRUCTOR_REF_IDS);
    if (refIds == null || refIds.size == 0) {
      return -1;
    }
    return refIds.get(refIds.size - 1);
  }

  private static boolean containsRefId(IntArray refIds, int refId) {
    for (int i = refIds.size - 1; i >= 0; i--) {
      if (refIds.get(i) == refId) {
        return true;
      }
    }
    return false;
  }

  private static boolean consumeUnresolvedConstructorRef(ReadContext readContext, int refId) {
    IntArray unresolvedRefIds =
        (IntArray) readContext.getContextObject(UNRESOLVED_CONSTRUCTOR_REF_IDS);
    if (unresolvedRefIds == null || unresolvedRefIds.size == 0) {
      return false;
    }
    boolean found = false;
    int newSize = 0;
    for (int i = 0; i < unresolvedRefIds.size; i++) {
      int unresolvedRefId = unresolvedRefIds.get(i);
      if (unresolvedRefId == refId) {
        found = true;
      } else {
        unresolvedRefIds.elementData[newSize++] = unresolvedRefId;
      }
    }
    unresolvedRefIds.size = newSize;
    return found;
  }

  protected static void throwConstructorCycle(Class<?> type) {
    throw new ForyException(
        "Cyclic references to constructor-bound type "
            + type.getName()
            + " cannot be restored before the object is constructed. Use a no-arg constructor "
            + "or keep the cycle as a direct non-constructor field.");
  }

  public static Object defaultConstructorValue(Class<?> type) {
    if (type == boolean.class) {
      return false;
    } else if (type == byte.class) {
      return (byte) 0;
    } else if (type == short.class) {
      return (short) 0;
    } else if (type == char.class) {
      return (char) 0;
    } else if (type == int.class) {
      return 0;
    } else if (type == long.class) {
      return 0L;
    } else if (type == float.class) {
      return 0.0f;
    } else if (type == double.class) {
      return 0.0d;
    }
    return null;
  }

  protected final void setNonConstructorFields(
      Object targetObject,
      Object[] fieldValues,
      SerializationFieldInfo[] fieldInfos,
      boolean[] constructorMask) {
    for (int i = 0; i < fieldInfos.length; i++) {
      if (!constructorMask[i] && fieldInfos[i].fieldAccessor != null) {
        fieldInfos[i].fieldAccessor.putObject(targetObject, fieldValues[i]);
      }
    }
  }

  public static Object fieldValue(Object[] fieldValues, int index) {
    return fieldValues[index];
  }

  private static int constructorFieldIndex(
      SerializationFieldInfo[] fieldInfos,
      Class<?> declaringClass,
      String fieldName,
      boolean allowMissing) {
    int index = -1;
    for (int i = 0; i < fieldInfos.length; i++) {
      FieldAccessor fieldAccessor = fieldInfos[i].fieldAccessor;
      if (fieldAccessor == null) {
        continue;
      }
      Field field = fieldAccessor.getField();
      if (!field.getName().equals(fieldName)
          || (declaringClass != null && field.getDeclaringClass() != declaringClass)) {
        continue;
      }
      if (index >= 0) {
        throw new ForyException(
            "Constructor field " + fieldName + " is ambiguous because multiple fields match");
      }
      index = i;
    }
    if (index < 0) {
      if (allowMissing) {
        return -1;
      }
      throw new ForyException("Constructor field " + fieldName + " is not serialized");
    }
    return index;
  }
}
