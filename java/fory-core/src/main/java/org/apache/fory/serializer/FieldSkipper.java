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
import org.apache.fory.resolver.RefMode;
import org.apache.fory.serializer.FieldGroups.SerializationFieldInfo;
import org.apache.fory.type.DispatchId;

/**
 * Utility class for skipping field values in the buffer when a field doesn't exist in the current
 * class. This is used for schema compatibility when deserializing data from peers with different
 * class definitions.
 */
public class FieldSkipper {

  /**
   * Skip a field value in the buffer. Handles all dispatch IDs including basic types and complex
   * types. Whether to read a null flag is determined by fieldInfo.refMode.
   *
   * @param binding the serialization binding for fallback reads
   * @param fieldInfo the field metadata
   * @param buffer the buffer to skip from
   */
  static void skipField(
      SerializationBinding binding, SerializationFieldInfo fieldInfo, MemoryBuffer buffer) {
    if (!skipFieldValue(binding, fieldInfo, buffer)) {
      // Fall back to binding.read for complex types (objects, collections, etc.)
      binding.readField(fieldInfo, buffer);
    }
  }

  /**
   * Skip a field value based on its dispatch ID and refMode. The refMode determines whether a null
   * flag exists in the stream.
   *
   * @return true if the field was skipped, false if it needs fallback handling
   */
  private static boolean skipFieldValue(
      SerializationBinding binding, SerializationFieldInfo fieldInfo, MemoryBuffer buffer) {
    int dispatchId = fieldInfo.dispatchId;
    RefMode refMode = fieldInfo.refMode;

    // For non-basic types, let the binding handle it
    if (!DispatchId.isBasicType(dispatchId)) {
      return false;
    }

    // If refMode is not NONE, we need to check for null flag first
    if (refMode != RefMode.NONE) {
      if (buffer.readByte() == Fory.NULL_FLAG) {
        return true; // Field is null, nothing more to skip
      }
    }

    // Now skip the actual value bytes based on dispatch ID
    switch (dispatchId) {
      case DispatchId.BOOL:
        buffer.increaseReaderIndex(1);
        return true;
      case DispatchId.INT8:
      case DispatchId.UINT8:
        buffer.increaseReaderIndex(1);
        return true;
      case DispatchId.CHAR:
        buffer.increaseReaderIndex(2);
        return true;
      case DispatchId.INT16:
      case DispatchId.UINT16:
        buffer.increaseReaderIndex(2);
        return true;
      case DispatchId.INT32:
      case DispatchId.UINT32:
        buffer.increaseReaderIndex(4);
        return true;
      case DispatchId.VARINT32:
        buffer.readVarInt32();
        return true;
      case DispatchId.VAR_UINT32:
        buffer.readVarUint32();
        return true;
      case DispatchId.INT64:
      case DispatchId.UINT64:
        buffer.increaseReaderIndex(8);
        return true;
      case DispatchId.VARINT64:
        buffer.readVarInt64();
        return true;
      case DispatchId.TAGGED_INT64:
        buffer.readTaggedInt64();
        return true;
      case DispatchId.VAR_UINT64:
        buffer.readVarUint64();
        return true;
      case DispatchId.TAGGED_UINT64:
        buffer.readTaggedUint64();
        return true;
      case DispatchId.FLOAT32:
        buffer.increaseReaderIndex(4);
        return true;
      case DispatchId.FLOAT64:
        buffer.increaseReaderIndex(8);
        return true;
      case DispatchId.STRING:
        // Read and discard the string - no class info in stream for STRING
        binding.fory.readJavaString(buffer);
        return true;
      default:
        // Other types need fallback handling
        return false;
    }
  }
}
