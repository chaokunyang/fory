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

package org.apache.fory.context;

import org.apache.fory.annotation.Internal;
import org.apache.fory.collection.IdentityObjectIntMap;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.meta.EncodedMetaString;
import org.apache.fory.meta.Encoders;
import org.apache.fory.meta.MetaString;
import org.apache.fory.meta.MetaStringEncoder;
import org.apache.fory.resolver.MetaStringRef;
import org.apache.fory.resolver.SharedRegistry;

import java.util.Objects;

/** Write-side state for meta-string references. */
@Internal
public interface MetaStringWriter {
  MetaStringRef getOrCreateGenericMetaStringBytes(String str);

  MetaStringRef getOrCreateGenericMetaStringBytes(String str, MetaString.Encoding encoding);

  MetaStringRef getOrCreatePackageMetaStringBytes(String str);

  MetaStringRef getOrCreateTypeNameMetaStringBytes(String str);

  MetaStringRef getOrCreateMetaStringBytes(
      String str, MetaStringEncoder encoder, MetaString.Encoding encoding, String encoderTypeKey);

  void writeMetaStringBytesWithFlag(MemoryBuffer buffer, MetaStringRef metaStringRef);

  void writeMetaStringBytes(MemoryBuffer buffer, MetaStringRef metaStringRef);

  void reset();

  MetaStringRef getEmptyMetaStringRef();

  abstract class AbstractMetaStringWriter implements MetaStringWriter {
    static final int INITIAL_CAPACITY = 2;
    static final int SMALL_STRING_THRESHOLD = 16;

    final SharedRegistry sharedRegistry;
    private final MetaStringRef emptyMetaStringRef;

    AbstractMetaStringWriter(SharedRegistry sharedRegistry) {
      this.sharedRegistry = Objects.requireNonNull(sharedRegistry);
      emptyMetaStringRef = sharedRegistry.getOrCreateMetaStringRef(EncodedMetaString.EMPTY);
    }

    @Override
    public final MetaStringRef getOrCreateGenericMetaStringBytes(String str) {
      return getOrCreateGenericMetaStringBytes(str, Encoders.computeGenericEncoding(str));
    }

    @Override
    public final MetaStringRef getOrCreateGenericMetaStringBytes(
        String str, MetaString.Encoding encoding) {
      return getOrCreateMetaStringBytes(
          str, Encoders.GENERIC_ENCODER, encoding, Encoders.GENERIC_ENCODER_TYPE_KEY);
    }

    @Override
    public final MetaStringRef getOrCreatePackageMetaStringBytes(String str) {
      return getOrCreateMetaStringBytes(
          str,
          Encoders.PACKAGE_ENCODER,
          Encoders.computePackageEncoding(str),
          Encoders.PACKAGE_ENCODER_TYPE_KEY);
    }

    @Override
    public final MetaStringRef getOrCreateTypeNameMetaStringBytes(String str) {
      return getOrCreateMetaStringBytes(
          str,
          Encoders.TYPE_NAME_ENCODER,
          Encoders.computeTypeNameEncoding(str),
          Encoders.TYPE_NAME_ENCODER_TYPE_KEY);
    }

    @Override
    public final MetaStringRef getOrCreateMetaStringBytes(
        String str, MetaStringEncoder encoder, MetaString.Encoding encoding, String encoderTypeKey) {
      return sharedRegistry.getOrCreateMetaStringRef(str, encoder, encoding, encoderTypeKey);
    }

    @Override
    public final MetaStringRef getEmptyMetaStringRef() {
      return emptyMetaStringRef;
    }

    final void writeNewMetaStringBytesWithFlag(MemoryBuffer buffer, MetaStringRef metaStringRef) {
      EncodedMetaString encodedMetaString = metaStringRef.getEncoded();
      int length = encodedMetaString.bytes.length;
      buffer.writeVarUint32Small7(length << 2 | 0b1);
      if (length > SMALL_STRING_THRESHOLD) {
        buffer.writeInt64(encodedMetaString.hash);
      } else if (length != 0) {
        buffer.writeByte(encodedMetaString.encoding.getValue());
      }
      buffer.writeBytes(encodedMetaString.bytes);
    }

    final void writeNewMetaStringBytes(MemoryBuffer buffer, MetaStringRef metaStringRef) {
      EncodedMetaString encodedMetaString = metaStringRef.getEncoded();
      int length = encodedMetaString.bytes.length;
      buffer.writeVarUint32Small7(length << 1);
      if (length > SMALL_STRING_THRESHOLD) {
        buffer.writeInt64(encodedMetaString.hash);
      } else if (length != 0) {
        buffer.writeByte(encodedMetaString.encoding.getValue());
      }
      buffer.writeBytes(encodedMetaString.bytes);
    }

    final MetaStringRef[] growWrite(MetaStringRef[] current, int id) {
      int newLength = current.length;
      while (newLength <= id) {
        newLength <<= 1;
      }
      MetaStringRef[] expanded = new MetaStringRef[newLength];
      System.arraycopy(current, 0, expanded, 0, current.length);
      return expanded;
    }
  }

  /** Stores per-write meta-string ids on {@link MetaStringRef} itself. */
  @Internal
  final class FieldStateMetaStringWriter extends AbstractMetaStringWriter {
    private MetaStringRef[] dynamicWrittenStrings = new MetaStringRef[INITIAL_CAPACITY];
    private short dynamicWriteStringId;

    public FieldStateMetaStringWriter(SharedRegistry sharedRegistry) {
      super(sharedRegistry);
    }

    @Override
    public void writeMetaStringBytesWithFlag(MemoryBuffer buffer, MetaStringRef metaStringRef) {
      Objects.requireNonNull(metaStringRef);
      short id = metaStringRef.dynamicWriteStringId;
      if (id == MetaStringRef.DEFAULT_DYNAMIC_WRITE_STRING_ID) {
        id = dynamicWriteStringId;
        dynamicWriteStringId = (short) (id + 1);
        metaStringRef.dynamicWriteStringId = id;
        MetaStringRef[] writtenStrings = dynamicWrittenStrings;
        if (writtenStrings.length <= id) {
          writtenStrings = dynamicWrittenStrings = growWrite(writtenStrings, id);
        }
        writtenStrings[id] = metaStringRef;
        writeNewMetaStringBytesWithFlag(buffer, metaStringRef);
      } else {
        buffer.writeVarUint32Small7(((id + 1) << 2) | 0b11);
      }
    }

    @Override
    public void writeMetaStringBytes(MemoryBuffer buffer, MetaStringRef metaStringRef) {
      Objects.requireNonNull(metaStringRef);
      short id = metaStringRef.dynamicWriteStringId;
      if (id == MetaStringRef.DEFAULT_DYNAMIC_WRITE_STRING_ID) {
        id = dynamicWriteStringId;
        dynamicWriteStringId = (short) (id + 1);
        metaStringRef.dynamicWriteStringId = id;
        MetaStringRef[] writtenStrings = dynamicWrittenStrings;
        if (writtenStrings.length <= id) {
          writtenStrings = dynamicWrittenStrings = growWrite(writtenStrings, id);
        }
        writtenStrings[id] = metaStringRef;
        writeNewMetaStringBytes(buffer, metaStringRef);
      } else {
        buffer.writeVarUint32Small7(((id + 1) << 1) | 1);
      }
    }

    @Override
    public void reset() {
      int dynamicId = dynamicWriteStringId;
      if (dynamicId != 0) {
        for (int i = 0; i < dynamicId; i++) {
          MetaStringRef metaStringRef = dynamicWrittenStrings[i];
          if (metaStringRef != null) {
            metaStringRef.dynamicWriteStringId = MetaStringRef.DEFAULT_DYNAMIC_WRITE_STRING_ID;
            dynamicWrittenStrings[i] = null;
          }
        }
        dynamicWriteStringId = 0;
      }
    }
  }

  /** Tracks per-write meta-string ids in a writer-owned map keyed by {@link MetaStringRef}. */
  @Internal
  final class MapStateMetaStringWriter extends AbstractMetaStringWriter {
    private static final float LOAD_FACTOR = 0.5f;

    private final IdentityObjectIntMap<MetaStringRef> dynamicWriteStringIds =
        new IdentityObjectIntMap<>(INITIAL_CAPACITY, LOAD_FACTOR);
    private MetaStringRef[] dynamicWrittenStrings = new MetaStringRef[INITIAL_CAPACITY];
    private short dynamicWriteStringId;

    public MapStateMetaStringWriter(SharedRegistry sharedRegistry) {
      super(sharedRegistry);
    }

    @Override
    public void writeMetaStringBytesWithFlag(MemoryBuffer buffer, MetaStringRef metaStringRef) {
      Objects.requireNonNull(metaStringRef);
      int id =
          dynamicWriteStringIds.get(metaStringRef, MetaStringRef.DEFAULT_DYNAMIC_WRITE_STRING_ID);
      if (id == MetaStringRef.DEFAULT_DYNAMIC_WRITE_STRING_ID) {
        id = dynamicWriteStringId++;
        dynamicWriteStringIds.put(metaStringRef, id);
        MetaStringRef[] writtenStrings = dynamicWrittenStrings;
        if (writtenStrings.length <= id) {
          writtenStrings = dynamicWrittenStrings = growWrite(writtenStrings, id);
        }
        writtenStrings[id] = metaStringRef;
        writeNewMetaStringBytesWithFlag(buffer, metaStringRef);
      } else {
        buffer.writeVarUint32Small7(((id + 1) << 2) | 0b11);
      }
    }

    @Override
    public void writeMetaStringBytes(MemoryBuffer buffer, MetaStringRef metaStringRef) {
      Objects.requireNonNull(metaStringRef);
      int id =
          dynamicWriteStringIds.get(metaStringRef, MetaStringRef.DEFAULT_DYNAMIC_WRITE_STRING_ID);
      if (id == MetaStringRef.DEFAULT_DYNAMIC_WRITE_STRING_ID) {
        id = dynamicWriteStringId++;
        dynamicWriteStringIds.put(metaStringRef, id);
        MetaStringRef[] writtenStrings = dynamicWrittenStrings;
        if (writtenStrings.length <= id) {
          writtenStrings = dynamicWrittenStrings = growWrite(writtenStrings, id);
        }
        writtenStrings[id] = metaStringRef;
        writeNewMetaStringBytes(buffer, metaStringRef);
      } else {
        buffer.writeVarUint32Small7(((id + 1) << 1) | 1);
      }
    }

    @Override
    public void reset() {
      int dynamicId = dynamicWriteStringId;
      if (dynamicId != 0) {
        for (int i = 0; i < dynamicId; i++) {
          dynamicWrittenStrings[i] = null;
        }
        dynamicWriteStringIds.clearApproximate(Math.max(INITIAL_CAPACITY, dynamicId));
        dynamicWriteStringId = 0;
      }
    }
  }
}
