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
import org.apache.fory.serializer.FieldGroups.SerializationFieldInfo;
import org.apache.fory.type.DispatchId;

/**
 * Utility class for skipping field values in the buffer when a field doesn't exist in the current
 * class. This is used for schema compatibility when deserializing data from peers with different
 * class definitions.
 */
public class FieldSkipper {

  /**
   * Skip a field value in the buffer. Handles all dispatch IDs including primitive types,
   * non-null boxed types, nullable boxed types, and compressed number encodings.
   *
   * @param binding the serialization binding for fallback reads
   * @param fieldInfo the field metadata
   * @param buffer the buffer to skip from
   */
  static void skipField(
      SerializationBinding binding, SerializationFieldInfo fieldInfo, MemoryBuffer buffer) {
    if (!skipFieldValue(fieldInfo, buffer)) {
      // Fall back to binding.read for complex types (objects, collections, etc.)
      binding.readField(fieldInfo, buffer);
    }
  }

  /**
   * Skip a field value based on its dispatch ID. Handles primitive types, non-null boxed types,
   * and nullable boxed types with their specific encodings (including compressed numbers).
   *
   * @return true if the field was skipped, false if it needs fallback handling
   */
  private static boolean skipFieldValue(SerializationFieldInfo fieldInfo, MemoryBuffer buffer) {
    switch (fieldInfo.dispatchId) {
      // ============ Primitive types (no null flag) ============
      case DispatchId.PRIMITIVE_BOOL:
        buffer.increaseReaderIndex(1);
        return true;
      case DispatchId.PRIMITIVE_INT8:
      case DispatchId.PRIMITIVE_UINT8:
        buffer.increaseReaderIndex(1);
        return true;
      case DispatchId.PRIMITIVE_CHAR:
        buffer.increaseReaderIndex(2);
        return true;
      case DispatchId.PRIMITIVE_INT16:
      case DispatchId.PRIMITIVE_UINT16:
        buffer.increaseReaderIndex(2);
        return true;
      case DispatchId.PRIMITIVE_INT32:
      case DispatchId.PRIMITIVE_UINT32:
        buffer.increaseReaderIndex(4);
        return true;
      case DispatchId.PRIMITIVE_VARINT32:
        buffer.readVarInt32();
        return true;
      case DispatchId.PRIMITIVE_VAR_UINT32:
        buffer.readVarUint32();
        return true;
      case DispatchId.PRIMITIVE_INT64:
      case DispatchId.PRIMITIVE_UINT64:
        buffer.increaseReaderIndex(8);
        return true;
      case DispatchId.PRIMITIVE_VARINT64:
        buffer.readVarInt64();
        return true;
      case DispatchId.PRIMITIVE_TAGGED_INT64:
        buffer.readTaggedInt64();
        return true;
      case DispatchId.PRIMITIVE_VAR_UINT64:
        buffer.readVarUint64();
        return true;
      case DispatchId.PRIMITIVE_TAGGED_UINT64:
        buffer.readTaggedUint64();
        return true;
      case DispatchId.PRIMITIVE_FLOAT32:
        buffer.increaseReaderIndex(4);
        return true;
      case DispatchId.PRIMITIVE_FLOAT64:
        buffer.increaseReaderIndex(8);
        return true;

      // ============ Non-null boxed types (no null flag) ============
      case DispatchId.NOTNULL_BOXED_BOOL:
        buffer.increaseReaderIndex(1);
        return true;
      case DispatchId.NOTNULL_BOXED_INT8:
      case DispatchId.NOTNULL_BOXED_UINT8:
        buffer.increaseReaderIndex(1);
        return true;
      case DispatchId.NOTNULL_BOXED_CHAR:
        buffer.increaseReaderIndex(2);
        return true;
      case DispatchId.NOTNULL_BOXED_INT16:
      case DispatchId.NOTNULL_BOXED_UINT16:
        buffer.increaseReaderIndex(2);
        return true;
      case DispatchId.NOTNULL_BOXED_INT32:
      case DispatchId.NOTNULL_BOXED_UINT32:
        buffer.increaseReaderIndex(4);
        return true;
      case DispatchId.NOTNULL_BOXED_VARINT32:
        buffer.readVarInt32();
        return true;
      case DispatchId.NOTNULL_BOXED_VAR_UINT32:
        buffer.readVarUint32();
        return true;
      case DispatchId.NOTNULL_BOXED_INT64:
      case DispatchId.NOTNULL_BOXED_UINT64:
        buffer.increaseReaderIndex(8);
        return true;
      case DispatchId.NOTNULL_BOXED_VARINT64:
        buffer.readVarInt64();
        return true;
      case DispatchId.NOTNULL_BOXED_TAGGED_INT64:
        buffer.readTaggedInt64();
        return true;
      case DispatchId.NOTNULL_BOXED_VAR_UINT64:
        buffer.readVarUint64();
        return true;
      case DispatchId.NOTNULL_BOXED_TAGGED_UINT64:
        buffer.readTaggedUint64();
        return true;
      case DispatchId.NOTNULL_BOXED_FLOAT32:
        buffer.increaseReaderIndex(4);
        return true;
      case DispatchId.NOTNULL_BOXED_FLOAT64:
        buffer.increaseReaderIndex(8);
        return true;

      // ============ Nullable boxed types (with null flag) ============
      case DispatchId.BOOL:
        if (buffer.readByte() != Fory.NULL_FLAG) {
          buffer.increaseReaderIndex(1);
        }
        return true;
      case DispatchId.INT8:
      case DispatchId.UINT8:
        if (buffer.readByte() != Fory.NULL_FLAG) {
          buffer.increaseReaderIndex(1);
        }
        return true;
      case DispatchId.CHAR:
        if (buffer.readByte() != Fory.NULL_FLAG) {
          buffer.increaseReaderIndex(2);
        }
        return true;
      case DispatchId.INT16:
      case DispatchId.UINT16:
        if (buffer.readByte() != Fory.NULL_FLAG) {
          buffer.increaseReaderIndex(2);
        }
        return true;
      case DispatchId.INT32:
      case DispatchId.UINT32:
        if (buffer.readByte() != Fory.NULL_FLAG) {
          buffer.increaseReaderIndex(4);
        }
        return true;
      case DispatchId.VARINT32:
        if (buffer.readByte() != Fory.NULL_FLAG) {
          buffer.readVarInt32();
        }
        return true;
      case DispatchId.VAR_UINT32:
        if (buffer.readByte() != Fory.NULL_FLAG) {
          buffer.readVarUint32();
        }
        return true;
      case DispatchId.INT64:
      case DispatchId.UINT64:
        if (buffer.readByte() != Fory.NULL_FLAG) {
          buffer.increaseReaderIndex(8);
        }
        return true;
      case DispatchId.VARINT64:
        if (buffer.readByte() != Fory.NULL_FLAG) {
          buffer.readVarInt64();
        }
        return true;
      case DispatchId.TAGGED_INT64:
        if (buffer.readByte() != Fory.NULL_FLAG) {
          buffer.readTaggedInt64();
        }
        return true;
      case DispatchId.VAR_UINT64:
        if (buffer.readByte() != Fory.NULL_FLAG) {
          buffer.readVarUint64();
        }
        return true;
      case DispatchId.TAGGED_UINT64:
        if (buffer.readByte() != Fory.NULL_FLAG) {
          buffer.readTaggedUint64();
        }
        return true;
      case DispatchId.FLOAT32:
        if (buffer.readByte() != Fory.NULL_FLAG) {
          buffer.increaseReaderIndex(4);
        }
        return true;
      case DispatchId.FLOAT64:
        if (buffer.readByte() != Fory.NULL_FLAG) {
          buffer.increaseReaderIndex(8);
        }
        return true;

      default:
        // Complex types (String, objects, collections, etc.) need fallback handling
        return false;
    }
  }
}
