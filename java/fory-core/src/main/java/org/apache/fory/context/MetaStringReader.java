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

import java.util.Arrays;
import org.apache.fory.annotation.Internal;
import org.apache.fory.collection.LongLongByteMap;
import org.apache.fory.collection.LongMap;
import org.apache.fory.collection.ObjectMap;
import org.apache.fory.memory.LittleEndian;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.meta.EncodedMetaString;
import org.apache.fory.meta.Encoders;
import org.apache.fory.util.MurmurHash3;

/** Read-side state for meta-string references. */
@Internal
public final class MetaStringReader {
  private static final int INITIAL_CAPACITY = 2;
  private static final float LOAD_FACTOR = 0.5f;
  private static final int SMALL_STRING_THRESHOLD = 16;

  private final ObjectMap<EncodedMetaString, String> metaString2StringMap =
      new ObjectMap<>(INITIAL_CAPACITY, LOAD_FACTOR);
  private final LongMap<EncodedMetaString> hash2MetaStringMap =
      new LongMap<>(INITIAL_CAPACITY, LOAD_FACTOR);
  private final LongLongByteMap<EncodedMetaString> longLongMetaStringMap =
      new LongLongByteMap<>(INITIAL_CAPACITY, LOAD_FACTOR);
  private EncodedMetaString[] dynamicReadStringIds = new EncodedMetaString[INITIAL_CAPACITY];
  private short dynamicReadStringId;

  public MetaStringReader() {
    metaString2StringMap.put(EncodedMetaString.EMPTY, "");
  }

  public String readMetaString(MemoryBuffer buffer) {
    EncodedMetaString encodedMetaString = readMetaStringBytes(buffer);
    String str = metaString2StringMap.get(encodedMetaString);
    if (str == null) {
      str = encodedMetaString.decode(Encoders.GENERIC_DECODER);
      metaString2StringMap.put(encodedMetaString, str);
    }
    return str;
  }

  public EncodedMetaString readMetaStringBytesWithFlag(MemoryBuffer buffer, int header) {
    int len = header >>> 2;
    if ((header & 0b10) == 0) {
      EncodedMetaString encodedMetaString =
          len <= SMALL_STRING_THRESHOLD
              ? readSmallMetaStringBytes(buffer, len)
              : readBigMetaStringBytes(buffer, len, buffer.readInt64());
      updateDynamicString(encodedMetaString);
      return encodedMetaString;
    }
    return dynamicReadStringIds[len - 1];
  }

  public EncodedMetaString readMetaStringBytesWithFlag(
      MemoryBuffer buffer, EncodedMetaString cache, int header) {
    int len = header >>> 2;
    if ((header & 0b10) == 0) {
      EncodedMetaString encodedMetaString =
          len <= SMALL_STRING_THRESHOLD
              ? readSmallMetaStringBytes(buffer, cache, len)
              : readBigMetaStringBytes(buffer, cache, len);
      updateDynamicString(encodedMetaString);
      return encodedMetaString;
    }
    return dynamicReadStringIds[len - 1];
  }

  public EncodedMetaString readMetaStringBytes(MemoryBuffer buffer) {
    int header = buffer.readVarUint32Small7();
    int len = header >>> 1;
    if ((header & 0b1) == 0) {
      EncodedMetaString encodedMetaString =
          len > SMALL_STRING_THRESHOLD
              ? readBigMetaStringBytes(buffer, len, buffer.readInt64())
              : readSmallMetaStringBytes(buffer, len);
      updateDynamicString(encodedMetaString);
      return encodedMetaString;
    }
    return dynamicReadStringIds[len - 1];
  }

  public EncodedMetaString readMetaStringBytes(MemoryBuffer buffer, EncodedMetaString cache) {
    int header = buffer.readVarUint32Small7();
    int len = header >>> 1;
    if ((header & 0b1) == 0) {
      EncodedMetaString encodedMetaString =
          len <= SMALL_STRING_THRESHOLD
              ? readSmallMetaStringBytes(buffer, cache, len)
              : readBigMetaStringBytes(buffer, cache, len);
      updateDynamicString(encodedMetaString);
      return encodedMetaString;
    }
    return dynamicReadStringIds[len - 1];
  }

  private EncodedMetaString readBigMetaStringBytes(
      MemoryBuffer buffer, EncodedMetaString cache, int len) {
    long hashCode = buffer.readInt64();
    if (cache.hash == hashCode) {
      buffer.increaseReaderIndex(len);
      return cache;
    }
    return readBigMetaStringBytes(buffer, len, hashCode);
  }

  private EncodedMetaString readBigMetaStringBytes(
      MemoryBuffer buffer, int len, long hashCode) {
    EncodedMetaString encodedMetaString = hash2MetaStringMap.get(hashCode);
    if (encodedMetaString == null) {
      EncodedMetaString newMetaString = new EncodedMetaString(buffer.readBytes(len), hashCode);
      hash2MetaStringMap.put(hashCode, newMetaString);
      return newMetaString;
    }
    buffer.increaseReaderIndex(len);
    return encodedMetaString;
  }

  private EncodedMetaString readSmallMetaStringBytes(MemoryBuffer buffer, int len) {
    if (len == 0) {
      return EncodedMetaString.EMPTY;
    }
    byte encoding = buffer.readByte();
    long v1;
    long v2 = 0;
    if (len <= 8) {
      v1 = buffer.readBytesAsInt64(len);
    } else {
      v1 = buffer.readInt64();
      v2 = buffer.readBytesAsInt64(len - 8);
    }
    EncodedMetaString encodedMetaString = longLongMetaStringMap.get(v1, v2, encoding);
    if (encodedMetaString == null) {
      return createSmallMetaStringBytes(len, encoding, v1, v2);
    }
    return encodedMetaString;
  }

  private EncodedMetaString readSmallMetaStringBytes(
      MemoryBuffer buffer, EncodedMetaString cache, int len) {
    if (len == 0) {
      return EncodedMetaString.EMPTY;
    }
    byte encoding = buffer.readByte();
    long v1;
    long v2 = 0;
    if (len <= 8) {
      v1 = buffer.readBytesAsInt64(len);
    } else {
      v1 = buffer.readInt64();
      v2 = buffer.readBytesAsInt64(len - 8);
    }
    if (cache.first8Bytes == v1 && cache.second8Bytes == v2) {
      return cache;
    }
    EncodedMetaString encodedMetaString = longLongMetaStringMap.get(v1, v2, encoding);
    if (encodedMetaString == null) {
      return createSmallMetaStringBytes(len, encoding, v1, v2);
    }
    return encodedMetaString;
  }

  private EncodedMetaString createSmallMetaStringBytes(int len, byte encoding, long v1, long v2) {
    byte[] data = new byte[16];
    LittleEndian.putInt64(data, 0, v1);
    LittleEndian.putInt64(data, 8, v2);
    long hashCode = MurmurHash3.murmurhash3_x64_128(data, 0, len, 47)[0];
    hashCode = Math.abs(hashCode);
    hashCode = (hashCode & 0xffffffffffffff00L) | encoding;
    EncodedMetaString encodedMetaString = new EncodedMetaString(Arrays.copyOf(data, len), hashCode);
    longLongMetaStringMap.put(v1, v2, encoding, encodedMetaString);
    return encodedMetaString;
  }

  private void updateDynamicString(EncodedMetaString encodedMetaString) {
    short currentDynamicReadId = dynamicReadStringId++;
    EncodedMetaString[] readStringIds = dynamicReadStringIds;
    if (readStringIds.length <= currentDynamicReadId) {
      readStringIds = dynamicReadStringIds = growRead(readStringIds, currentDynamicReadId);
    }
    readStringIds[currentDynamicReadId] = encodedMetaString;
  }

  private EncodedMetaString[] growRead(EncodedMetaString[] current, int id) {
    int newLength = current.length;
    while (newLength <= id) {
      newLength <<= 1;
    }
    EncodedMetaString[] expanded = new EncodedMetaString[newLength];
    System.arraycopy(current, 0, expanded, 0, current.length);
    return expanded;
  }

  public void reset() {
    int dynamicReadId = dynamicReadStringId;
    if (dynamicReadId != 0) {
      for (int i = 0; i < dynamicReadId; i++) {
        dynamicReadStringIds[i] = null;
      }
      dynamicReadStringId = 0;
    }
  }
}
