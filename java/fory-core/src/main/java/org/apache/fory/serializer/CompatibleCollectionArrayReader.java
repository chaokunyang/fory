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
import java.util.ArrayList;
import java.util.List;
import org.apache.fory.Fory;
import org.apache.fory.collection.BFloat16List;
import org.apache.fory.collection.BoolList;
import org.apache.fory.collection.Float16List;
import org.apache.fory.collection.Float32List;
import org.apache.fory.collection.Float64List;
import org.apache.fory.collection.Int16List;
import org.apache.fory.collection.Int32List;
import org.apache.fory.collection.Int64List;
import org.apache.fory.collection.Int8List;
import org.apache.fory.collection.UInt16List;
import org.apache.fory.collection.UInt32List;
import org.apache.fory.collection.UInt64List;
import org.apache.fory.collection.UInt8List;
import org.apache.fory.config.Config;
import org.apache.fory.context.ReadContext;
import org.apache.fory.context.RefReader;
import org.apache.fory.exception.DeserializationException;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.memory.NativeByteOrder;
import org.apache.fory.meta.FieldTypes;
import org.apache.fory.meta.TypeExtMeta;
import org.apache.fory.platform.UnsafeOps;
import org.apache.fory.reflect.TypeRef;
import org.apache.fory.resolver.RefMode;
import org.apache.fory.resolver.TypeResolver;
import org.apache.fory.serializer.collection.CollectionFlags;
import org.apache.fory.type.BFloat16;
import org.apache.fory.type.BFloat16Array;
import org.apache.fory.type.Descriptor;
import org.apache.fory.type.Float16;
import org.apache.fory.type.Float16Array;
import org.apache.fory.type.TypeUtils;
import org.apache.fory.type.Types;

final class CompatibleCollectionArrayReader {
  static final int READ_LIST_TO_ARRAY = 1;
  static final int READ_ARRAY_TO_LIST = 2;

  static final class ReadAction {
    final int mode;
    final int arrayTypeId;
    final int elementTypeId;
    final Class<?> targetType;

    private ReadAction(int mode, int arrayTypeId, int elementTypeId, Class<?> targetType) {
      this.mode = mode;
      this.arrayTypeId = arrayTypeId;
      this.elementTypeId = elementTypeId;
      this.targetType = targetType;
    }
  }

  private CompatibleCollectionArrayReader() {}

  static ReadAction readAction(TypeResolver resolver, Descriptor descriptor) {
    Field field = descriptor.getField();
    if (field == null || !resolver.isCrossLanguage()) {
      return null;
    }
    FieldTypes.FieldType localFieldType = FieldTypes.buildFieldType(resolver, field);
    int peerListElementTypeId = listElementTypeId(descriptor.getTypeRef());
    if (peerListElementTypeId != Types.UNKNOWN) {
      int localArrayTypeId = arrayTypeId(localFieldType);
      if (localArrayTypeId != Types.UNKNOWN
          && localArrayTypeId == denseArrayTypeId(peerListElementTypeId)) {
        return new ReadAction(
            READ_LIST_TO_ARRAY, localArrayTypeId, peerListElementTypeId, field.getType());
      }
      return null;
    }
    int peerArrayTypeId = arrayTypeId(descriptor.getTypeRef());
    if (peerArrayTypeId != Types.UNKNOWN) {
      int localListElementTypeId = listElementTypeId(localFieldType);
      if (localListElementTypeId != Types.UNKNOWN
          && peerArrayTypeId == denseArrayTypeId(localListElementTypeId)) {
        return new ReadAction(
            READ_ARRAY_TO_LIST, peerArrayTypeId, localListElementTypeId, field.getType());
      }
    }
    return null;
  }

  static Object read(ReadContext readContext, RefMode refMode, ReadAction action) {
    return read(
        readContext,
        refMode,
        action.mode,
        action.arrayTypeId,
        action.elementTypeId,
        action.targetType);
  }

  static Object read(
      ReadContext readContext,
      RefMode refMode,
      int readMode,
      int arrayTypeId,
      int elementTypeId,
      Class<?> targetType) {
    switch (refMode) {
      case NONE:
        return readNotNull(readContext, readMode, arrayTypeId, elementTypeId, targetType);
      case NULL_ONLY:
        if (readContext.getBuffer().readByte() == Fory.NULL_FLAG) {
          return null;
        }
        return readNotNull(readContext, readMode, arrayTypeId, elementTypeId, targetType);
      case TRACKING:
        return readTracking(readContext, readMode, arrayTypeId, elementTypeId, targetType);
      default:
        throw new IllegalStateException("Unknown refMode: " + refMode);
    }
  }

  private static Object readTracking(
      ReadContext readContext,
      int readMode,
      int arrayTypeId,
      int elementTypeId,
      Class<?> targetType) {
    RefReader refReader = readContext.getRefReader();
    int nextReadRefId = refReader.tryPreserveRefId(readContext.getBuffer());
    if (nextReadRefId >= Fory.NOT_NULL_VALUE_FLAG) {
      Object value = readNotNull(readContext, readMode, arrayTypeId, elementTypeId, targetType);
      refReader.setReadRef(nextReadRefId, value);
      return value;
    }
    return refReader.getReadRef();
  }

  private static Object readNotNull(
      ReadContext readContext,
      int readMode,
      int arrayTypeId,
      int elementTypeId,
      Class<?> targetType) {
    if (readMode == READ_LIST_TO_ARRAY) {
      Object array = readListPayloadAsPrimitiveArray(readContext, arrayTypeId, elementTypeId);
      if (array == null) {
        return null;
      }
      return materializeTarget(array, arrayTypeId, targetType);
    }
    if (readMode == READ_ARRAY_TO_LIST) {
      Object array = readDenseArrayPayload(readContext, arrayTypeId);
      return materializeTarget(array, arrayTypeId, targetType);
    }
    throw new IllegalStateException("Unexpected compatible read mode " + readMode);
  }

  private static int listElementTypeId(FieldTypes.FieldType fieldType) {
    if (!(fieldType instanceof FieldTypes.CollectionFieldType)
        || fieldType.getTypeId() != Types.LIST) {
      return Types.UNKNOWN;
    }
    FieldTypes.FieldType elementType =
        ((FieldTypes.CollectionFieldType) fieldType).getElementType();
    if (elementType instanceof FieldTypes.RegisteredFieldType) {
      return ((FieldTypes.RegisteredFieldType) elementType).getTypeId();
    }
    return Types.UNKNOWN;
  }

  private static int listElementTypeId(TypeRef<?> typeRef) {
    TypeExtMeta extMeta = typeRef.getTypeExtMeta();
    if (extMeta == null || extMeta.typeId() != Types.LIST) {
      return Types.UNKNOWN;
    }
    TypeExtMeta elementExtMeta = TypeUtils.getElementType(typeRef).getTypeExtMeta();
    return elementExtMeta == null ? Types.UNKNOWN : elementExtMeta.typeId();
  }

  private static int arrayTypeId(FieldTypes.FieldType fieldType) {
    if (fieldType instanceof FieldTypes.RegisteredFieldType) {
      int typeId = ((FieldTypes.RegisteredFieldType) fieldType).getTypeId();
      if (Types.isPrimitiveArray(typeId)) {
        return typeId;
      }
    }
    return Types.UNKNOWN;
  }

  private static int arrayTypeId(TypeRef<?> typeRef) {
    TypeExtMeta extMeta = typeRef.getTypeExtMeta();
    if (extMeta != null && Types.isPrimitiveArray(extMeta.typeId())) {
      return extMeta.typeId();
    }
    return Types.UNKNOWN;
  }

  private static int denseArrayTypeId(int elementTypeId) {
    switch (elementTypeId) {
      case Types.BOOL:
        return Types.BOOL_ARRAY;
      case Types.INT8:
        return Types.INT8_ARRAY;
      case Types.UINT8:
        return Types.UINT8_ARRAY;
      case Types.INT16:
        return Types.INT16_ARRAY;
      case Types.UINT16:
        return Types.UINT16_ARRAY;
      case Types.INT32:
      case Types.VARINT32:
        return Types.INT32_ARRAY;
      case Types.UINT32:
      case Types.VAR_UINT32:
        return Types.UINT32_ARRAY;
      case Types.INT64:
      case Types.VARINT64:
      case Types.TAGGED_INT64:
        return Types.INT64_ARRAY;
      case Types.UINT64:
      case Types.VAR_UINT64:
      case Types.TAGGED_UINT64:
        return Types.UINT64_ARRAY;
      case Types.FLOAT16:
        return Types.FLOAT16_ARRAY;
      case Types.BFLOAT16:
        return Types.BFLOAT16_ARRAY;
      case Types.FLOAT32:
        return Types.FLOAT32_ARRAY;
      case Types.FLOAT64:
        return Types.FLOAT64_ARRAY;
      default:
        return Types.UNKNOWN;
    }
  }

  private static Object readListPayloadAsPrimitiveArray(
      ReadContext readContext, int arrayTypeId, int elementTypeId) {
    MemoryBuffer buffer = readContext.getBuffer();
    int numElements = buffer.readVarUInt32Small7();
    validateElementCount(readContext.getConfig(), numElements);
    validateElementStorageSize(readContext.getConfig(), numElements, elementSize(arrayTypeId));
    if (numElements > 0) {
      int flags = buffer.readByte();
      boolean hasNull = (flags & CollectionFlags.HAS_NULL) == CollectionFlags.HAS_NULL;
      boolean trackingRef = (flags & CollectionFlags.TRACKING_REF) == CollectionFlags.TRACKING_REF;
      boolean sameType = (flags & CollectionFlags.IS_SAME_TYPE) == CollectionFlags.IS_SAME_TYPE;
      boolean declared =
          (flags & CollectionFlags.IS_DECL_ELEMENT_TYPE) == CollectionFlags.IS_DECL_ELEMENT_TYPE;
      if (hasNull || trackingRef) {
        throw new DeserializationException(
            "Cannot read nullable or ref-tracked peer list<T> payload into local array<T> field");
      }
      if (!sameType || !declared) {
        throw new DeserializationException(
            "Cannot read peer list<T> payload into local array<T> field");
      }
    }
    return readListPrimitiveElements(buffer, numElements, arrayTypeId, elementTypeId);
  }

  private static Object readDenseArrayPayload(ReadContext readContext, int arrayTypeId) {
    MemoryBuffer buffer = readContext.getBuffer();
    int byteSize = buffer.readVarUInt32Small7();
    int elemSize = elementSize(arrayTypeId);
    validateBinarySize(readContext.getConfig(), buffer, byteSize, elemSize);
    return readPrimitiveElements(buffer, byteSize / elemSize, arrayTypeId);
  }

  private static Object readPrimitiveElements(
      MemoryBuffer buffer, int numElements, int arrayTypeId) {
    switch (arrayTypeId) {
      case Types.BOOL_ARRAY:
        {
          boolean[] values = new boolean[numElements];
          buffer.readToUnsafe(values, UnsafeOps.BOOLEAN_ARRAY_OFFSET, numElements);
          return values;
        }
      case Types.INT8_ARRAY:
      case Types.UINT8_ARRAY:
        {
          byte[] values = new byte[numElements];
          buffer.readToUnsafe(values, UnsafeOps.BYTE_ARRAY_OFFSET, numElements);
          return values;
        }
      case Types.INT16_ARRAY:
      case Types.UINT16_ARRAY:
      case Types.FLOAT16_ARRAY:
      case Types.BFLOAT16_ARRAY:
        {
          short[] values = new short[numElements];
          if (NativeByteOrder.IS_LITTLE_ENDIAN) {
            buffer.readToUnsafe(values, UnsafeOps.SHORT_ARRAY_OFFSET, numElements * 2);
          } else {
            for (int i = 0; i < numElements; i++) {
              values[i] = buffer.readInt16();
            }
          }
          return values;
        }
      case Types.INT32_ARRAY:
      case Types.UINT32_ARRAY:
        {
          int[] values = new int[numElements];
          if (NativeByteOrder.IS_LITTLE_ENDIAN) {
            buffer.readToUnsafe(values, UnsafeOps.INT_ARRAY_OFFSET, numElements * 4);
          } else {
            for (int i = 0; i < numElements; i++) {
              values[i] = buffer.readInt32();
            }
          }
          return values;
        }
      case Types.INT64_ARRAY:
      case Types.UINT64_ARRAY:
        {
          long[] values = new long[numElements];
          if (NativeByteOrder.IS_LITTLE_ENDIAN) {
            buffer.readToUnsafe(values, UnsafeOps.LONG_ARRAY_OFFSET, numElements * 8);
          } else {
            for (int i = 0; i < numElements; i++) {
              values[i] = buffer.readInt64();
            }
          }
          return values;
        }
      case Types.FLOAT32_ARRAY:
        {
          float[] values = new float[numElements];
          if (NativeByteOrder.IS_LITTLE_ENDIAN) {
            buffer.readToUnsafe(values, UnsafeOps.FLOAT_ARRAY_OFFSET, numElements * 4);
          } else {
            for (int i = 0; i < numElements; i++) {
              values[i] = buffer.readFloat32();
            }
          }
          return values;
        }
      case Types.FLOAT64_ARRAY:
        {
          double[] values = new double[numElements];
          if (NativeByteOrder.IS_LITTLE_ENDIAN) {
            buffer.readToUnsafe(values, UnsafeOps.DOUBLE_ARRAY_OFFSET, numElements * 8);
          } else {
            for (int i = 0; i < numElements; i++) {
              values[i] = buffer.readFloat64();
            }
          }
          return values;
        }
      default:
        throw new IllegalArgumentException("Unsupported dense array type id " + arrayTypeId);
    }
  }

  private static Object readListPrimitiveElements(
      MemoryBuffer buffer, int numElements, int arrayTypeId, int elementTypeId) {
    switch (elementTypeId) {
      case Types.BOOL:
        {
          boolean[] values = new boolean[numElements];
          for (int i = 0; i < numElements; i++) {
            values[i] = buffer.readBoolean();
          }
          return values;
        }
      case Types.INT8:
      case Types.UINT8:
        {
          byte[] values = new byte[numElements];
          buffer.readBytes(values);
          return values;
        }
      case Types.INT16:
      case Types.UINT16:
      case Types.FLOAT16:
      case Types.BFLOAT16:
        {
          short[] values = new short[numElements];
          for (int i = 0; i < numElements; i++) {
            values[i] = buffer.readInt16();
          }
          return values;
        }
      case Types.INT32:
      case Types.UINT32:
        {
          int[] values = new int[numElements];
          for (int i = 0; i < numElements; i++) {
            values[i] = buffer.readInt32();
          }
          return values;
        }
      case Types.VARINT32:
        {
          int[] values = new int[numElements];
          for (int i = 0; i < numElements; i++) {
            values[i] = buffer.readVarInt32();
          }
          return values;
        }
      case Types.VAR_UINT32:
        {
          int[] values = new int[numElements];
          for (int i = 0; i < numElements; i++) {
            values[i] = buffer.readVarUInt32();
          }
          return values;
        }
      case Types.INT64:
      case Types.UINT64:
        {
          long[] values = new long[numElements];
          for (int i = 0; i < numElements; i++) {
            values[i] = buffer.readInt64();
          }
          return values;
        }
      case Types.VARINT64:
        {
          long[] values = new long[numElements];
          for (int i = 0; i < numElements; i++) {
            values[i] = buffer.readVarInt64();
          }
          return values;
        }
      case Types.TAGGED_INT64:
        {
          long[] values = new long[numElements];
          for (int i = 0; i < numElements; i++) {
            values[i] = buffer.readTaggedInt64();
          }
          return values;
        }
      case Types.VAR_UINT64:
        {
          long[] values = new long[numElements];
          for (int i = 0; i < numElements; i++) {
            values[i] = buffer.readVarUInt64();
          }
          return values;
        }
      case Types.TAGGED_UINT64:
        {
          long[] values = new long[numElements];
          for (int i = 0; i < numElements; i++) {
            values[i] = buffer.readTaggedUInt64();
          }
          return values;
        }
      case Types.FLOAT32:
        {
          float[] values = new float[numElements];
          for (int i = 0; i < numElements; i++) {
            values[i] = buffer.readFloat32();
          }
          return values;
        }
      case Types.FLOAT64:
        {
          double[] values = new double[numElements];
          for (int i = 0; i < numElements; i++) {
            values[i] = buffer.readFloat64();
          }
          return values;
        }
      default:
        throw new DeserializationException(
            "Unsupported peer list<T> element type id "
                + elementTypeId
                + " for local array<T> type id "
                + arrayTypeId);
    }
  }

  private static Object materializeTarget(Object array, int arrayTypeId, Class<?> targetType) {
    if (targetType.isArray()) {
      return array;
    }
    if (targetType == Float16Array.class) {
      return Float16Array.wrapBits((short[]) array);
    }
    if (targetType == BFloat16Array.class) {
      return BFloat16Array.wrapBits((short[]) array);
    }
    Object primitiveList = materializePrimitiveList(array, arrayTypeId, targetType);
    if (primitiveList != null) {
      return primitiveList;
    }
    if (List.class.isAssignableFrom(targetType)) {
      return materializeBoxedList(array, arrayTypeId);
    }
    throw new DeserializationException("Unsupported compatible list/array target " + targetType);
  }

  private static Object materializePrimitiveList(
      Object array, int arrayTypeId, Class<?> targetType) {
    switch (arrayTypeId) {
      case Types.BOOL_ARRAY:
        return targetType == BoolList.class ? new BoolList((boolean[]) array) : null;
      case Types.INT8_ARRAY:
        return targetType == Int8List.class ? new Int8List((byte[]) array) : null;
      case Types.UINT8_ARRAY:
        return targetType == UInt8List.class ? new UInt8List((byte[]) array) : null;
      case Types.INT16_ARRAY:
        return targetType == Int16List.class ? new Int16List((short[]) array) : null;
      case Types.UINT16_ARRAY:
        return targetType == UInt16List.class ? new UInt16List((short[]) array) : null;
      case Types.INT32_ARRAY:
        return targetType == Int32List.class ? new Int32List((int[]) array) : null;
      case Types.UINT32_ARRAY:
        return targetType == UInt32List.class ? new UInt32List((int[]) array) : null;
      case Types.INT64_ARRAY:
        return targetType == Int64List.class ? new Int64List((long[]) array) : null;
      case Types.UINT64_ARRAY:
        return targetType == UInt64List.class ? new UInt64List((long[]) array) : null;
      case Types.FLOAT16_ARRAY:
        return targetType == Float16List.class ? new Float16List((short[]) array) : null;
      case Types.BFLOAT16_ARRAY:
        return targetType == BFloat16List.class ? new BFloat16List((short[]) array) : null;
      case Types.FLOAT32_ARRAY:
        return targetType == Float32List.class ? new Float32List((float[]) array) : null;
      case Types.FLOAT64_ARRAY:
        return targetType == Float64List.class ? new Float64List((double[]) array) : null;
      default:
        throw new IllegalArgumentException("Unsupported dense array type id " + arrayTypeId);
    }
  }

  private static List<Object> materializeBoxedList(Object array, int arrayTypeId) {
    int size = java.lang.reflect.Array.getLength(array);
    ArrayList<Object> list = new ArrayList<>(size);
    switch (arrayTypeId) {
      case Types.BOOL_ARRAY:
        for (boolean value : (boolean[]) array) {
          list.add(value);
        }
        break;
      case Types.INT8_ARRAY:
        for (byte value : (byte[]) array) {
          list.add(value);
        }
        break;
      case Types.UINT8_ARRAY:
        for (byte value : (byte[]) array) {
          list.add(value & 0xFF);
        }
        break;
      case Types.INT16_ARRAY:
        for (short value : (short[]) array) {
          list.add(value);
        }
        break;
      case Types.UINT16_ARRAY:
        for (short value : (short[]) array) {
          list.add(value & 0xFFFF);
        }
        break;
      case Types.INT32_ARRAY:
        for (int value : (int[]) array) {
          list.add(value);
        }
        break;
      case Types.UINT32_ARRAY:
        for (int value : (int[]) array) {
          list.add(Integer.toUnsignedLong(value));
        }
        break;
      case Types.INT64_ARRAY:
      case Types.UINT64_ARRAY:
        for (long value : (long[]) array) {
          list.add(value);
        }
        break;
      case Types.FLOAT16_ARRAY:
        for (short value : (short[]) array) {
          list.add(Float16.fromBits(value));
        }
        break;
      case Types.BFLOAT16_ARRAY:
        for (short value : (short[]) array) {
          list.add(BFloat16.fromBits(value));
        }
        break;
      case Types.FLOAT32_ARRAY:
        for (float value : (float[]) array) {
          list.add(value);
        }
        break;
      case Types.FLOAT64_ARRAY:
        for (double value : (double[]) array) {
          list.add(value);
        }
        break;
      default:
        throw new IllegalArgumentException("Unsupported dense array type id " + arrayTypeId);
    }
    return list;
  }

  private static int elementSize(int arrayTypeId) {
    switch (arrayTypeId) {
      case Types.BOOL_ARRAY:
      case Types.INT8_ARRAY:
      case Types.UINT8_ARRAY:
        return 1;
      case Types.INT16_ARRAY:
      case Types.UINT16_ARRAY:
      case Types.FLOAT16_ARRAY:
      case Types.BFLOAT16_ARRAY:
        return 2;
      case Types.INT32_ARRAY:
      case Types.UINT32_ARRAY:
      case Types.FLOAT32_ARRAY:
        return 4;
      case Types.INT64_ARRAY:
      case Types.UINT64_ARRAY:
      case Types.FLOAT64_ARRAY:
        return 8;
      default:
        throw new IllegalArgumentException("Unsupported dense array type id " + arrayTypeId);
    }
  }

  private static void validateElementCount(Config config, int numElements) {
    if (numElements < 0) {
      throw new DeserializationException("Collection size must be non-negative: " + numElements);
    }
    if (numElements > config.maxCollectionSize()) {
      throw new DeserializationException(
          "Collection size "
              + numElements
              + " exceeds max collection size "
              + config.maxCollectionSize());
    }
  }

  private static void validateElementStorageSize(Config config, int numElements, int elemSize) {
    if (numElements > config.maxBinarySize() / elemSize) {
      throw new DeserializationException(
          "Binary payload size "
              + ((long) numElements * elemSize)
              + " exceeds max binary size "
              + config.maxBinarySize());
    }
  }

  private static void validateBinarySize(
      Config config, MemoryBuffer buffer, int byteSize, int elemSize) {
    if (byteSize < 0) {
      throw new DeserializationException("Binary payload size must be non-negative: " + byteSize);
    }
    if (byteSize > config.maxBinarySize()) {
      throw new DeserializationException(
          "Binary payload size " + byteSize + " exceeds max binary size " + config.maxBinarySize());
    }
    if (byteSize % elemSize != 0) {
      throw new DeserializationException(
          "Binary payload size " + byteSize + " is not aligned to element size " + elemSize);
    }
    if (byteSize > buffer.remaining()) {
      buffer.checkReadableBytes(byteSize);
    }
  }
}
