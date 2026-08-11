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
import org.apache.fory.collection.MetadataLongMap;
import org.apache.fory.exception.ForyException;
import org.apache.fory.memory.LittleEndian;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.meta.EncodedMetaString;
import org.apache.fory.meta.MetaString;

/**
 * Read-side state for meta-string references.
 *
 * <p>{@link #dynamicReadStringIds} is the current root's occurrence table. The two hash maps
 * deduplicate validated but not yet accepted wire bodies only within that root; reset clears their
 * entries and bounds retained backing storage. Persistent checked or expected owners belong to
 * resolver TypeInfo, TypeDef, and name caches or to {@code SharedRegistry} registration/writer
 * interning. Callers may supply those owners as expected candidates, but this reader neither
 * publishes nor clears them.
 */
@Internal
public final class MetaStringReader {
  private static final int INITIAL_CAPACITY = 2;
  private static final float LOAD_FACTOR = 0.5f;
  private static final int SMALL_STRING_THRESHOLD = 16;
  private static final int ENCODING_BITS = 4;
  private static final int MAX_ROOT_META_STRINGS = 8192;
  private static final int MAX_ROOT_META_STRING_LENGTH = 2048;
  private static final int MAX_RETAINED_META_STRING_SLOTS = 1024;

  private final MetadataLongMap<EncodedMetaString> hash2MetaStringMap =
      new MetadataLongMap<>(INITIAL_CAPACITY, LOAD_FACTOR);
  private final LongLongByteMap<EncodedMetaString> longLongMetaStringMap =
      new LongLongByteMap<>(INITIAL_CAPACITY, LOAD_FACTOR);
  private EncodedMetaString[] dynamicReadStringIds = new EncodedMetaString[INITIAL_CAPACITY];
  private int dynamicReadStringId;

  /** Creates an empty reader whose root-local state is cleared by {@link #reset()}. */
  public MetaStringReader() {}

  /**
   * Reads a meta string whose header has already been parsed from the stream and includes the
   * protocol flag bit layout.
   */
  public EncodedMetaString readMetaStringWithFlag(MemoryBuffer buffer, int header) {
    int len = header >>> 2;
    if ((header & 0b10) == 0) {
      EncodedMetaString encodedMetaString =
          len <= SMALL_STRING_THRESHOLD
              ? readSmallMetaString(buffer, len)
              : readBigMetaString(buffer, len, buffer.readInt64());
      updateDynamicString(encodedMetaString);
      return encodedMetaString;
    }
    return readDynamicString(len);
  }

  /**
   * Reads a flagged meta string while consulting a caller-supplied cache candidate first.
   *
   * <p>The cache allows call sites with a likely expected value to avoid an additional map lookup
   * for exact matches.
   */
  public EncodedMetaString readMetaStringWithFlag(
      MemoryBuffer buffer, EncodedMetaString cache, int header) {
    int len = header >>> 2;
    if ((header & 0b10) == 0) {
      EncodedMetaString encodedMetaString =
          len <= SMALL_STRING_THRESHOLD
              ? readSmallMetaString(buffer, cache, len)
              : readBigMetaString(buffer, cache, len);
      updateDynamicString(encodedMetaString);
      return encodedMetaString;
    }
    return readDynamicString(len);
  }

  /** Reads a meta string from the current buffer, including any dynamic-id indirection. */
  public EncodedMetaString readMetaString(MemoryBuffer buffer) {
    int header = buffer.readVarUInt32Small7();
    int len = header >>> 1;
    if ((header & 0b1) == 0) {
      EncodedMetaString encodedMetaString =
          len > SMALL_STRING_THRESHOLD
              ? readBigMetaString(buffer, len, buffer.readInt64())
              : readSmallMetaString(buffer, len);
      updateDynamicString(encodedMetaString);
      return encodedMetaString;
    }
    return readDynamicString(len);
  }

  /**
   * Reads a meta string from the current buffer while consulting a caller-supplied cache candidate
   * first.
   */
  public EncodedMetaString readMetaString(MemoryBuffer buffer, EncodedMetaString cache) {
    int header = buffer.readVarUInt32Small7();
    int len = header >>> 1;
    if ((header & 0b1) == 0) {
      EncodedMetaString encodedMetaString =
          len <= SMALL_STRING_THRESHOLD
              ? readSmallMetaString(buffer, cache, len)
              : readBigMetaString(buffer, cache, len);
      updateDynamicString(encodedMetaString);
      return encodedMetaString;
    }
    return readDynamicString(len);
  }

  private EncodedMetaString readBigMetaString(
      MemoryBuffer buffer, EncodedMetaString cache, int len) {
    long hashCode = buffer.readInt64();
    buffer.checkReadableBytes(len);
    if (cache.hash == hashCode
        && cache.bytes.length == len
        && buffer.equalTo(cache.bytes, 0, buffer.readerIndex(), len)) {
      buffer._increaseReaderIndexUnsafe(len);
      return cache;
    }
    return readBigMetaString(buffer, len, hashCode);
  }

  private EncodedMetaString readBigMetaString(MemoryBuffer buffer, int len, long hashCode) {
    buffer.checkReadableBytes(len);
    EncodedMetaString encodedMetaString = hash2MetaStringMap.get(hashCode);
    if (encodedMetaString != null
        && encodedMetaString.bytes.length == len
        && buffer.equalTo(encodedMetaString.bytes, 0, buffer.readerIndex(), len)) {
      // The wire hash narrows the root-local lookup, but exact bytes own a named type's identity.
      // Skipping on hash and length alone can substitute a different cached name before the
      // resolver performs its exact TypeNameBytes comparison.
      buffer._increaseReaderIndexUnsafe(len);
      return encodedMetaString;
    }
    byte[] bytes = readAndValidateBigMetaString(buffer, len, hashCode);
    // Wire names stay root-local until an owning resolver validates and publishes their TypeInfo.
    // Publishing here would let a later-rejected name persist in the shared registry.
    EncodedMetaString canonicalMetaString = new EncodedMetaString(bytes, hashCode);
    if (encodedMetaString == null
        && len <= MAX_ROOT_META_STRING_LENGTH
        && hash2MetaStringMap.size < MAX_ROOT_META_STRINGS) {
      hash2MetaStringMap.put(hashCode, canonicalMetaString);
    }
    return canonicalMetaString;
  }

  private byte[] readAndValidateBigMetaString(MemoryBuffer buffer, int len, long hashCode) {
    byte[] bytes = buffer.readBytes(len);
    MetaString.Encoding encoding = MetaString.Encoding.fromInt((int) (hashCode & 0xff));
    long canonicalHash = EncodedMetaString.computeHash(bytes, encoding);
    if (canonicalHash != hashCode) {
      throw new ForyException("Malformed meta string hash");
    }
    return bytes;
  }

  private boolean shouldCacheSmallMetaString() {
    return longLongMetaStringMap.size < MAX_ROOT_META_STRINGS;
  }

  private EncodedMetaString cacheSmallMetaString(
      long v1, long v2, byte key, EncodedMetaString encodedMetaString) {
    if (shouldCacheSmallMetaString()) {
      longLongMetaStringMap.put(v1, v2, key, encodedMetaString);
    }
    return encodedMetaString;
  }

  private EncodedMetaString createSmallMetaString(
      int len, MetaString.Encoding encoding, byte key, long v1, long v2) {
    byte[] data = new byte[16];
    LittleEndian.putInt64(data, 0, v1);
    LittleEndian.putInt64(data, 8, v2);
    byte[] bytes = Arrays.copyOf(data, len);
    long hashCode = EncodedMetaString.computeHash(bytes, encoding);
    return cacheSmallMetaString(v1, v2, key, new EncodedMetaString(bytes, hashCode));
  }

  private EncodedMetaString readSmallMetaString(MemoryBuffer buffer, int len) {
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
    int encodingValue = encoding & 0xff;
    byte key = smallMetaStringKey(len, encodingValue);
    EncodedMetaString encodedMetaString = longLongMetaStringMap.get(v1, v2, key);
    if (encodedMetaString == null) {
      return createSmallMetaString(len, MetaString.Encoding.fromInt(encodingValue), key, v1, v2);
    }
    return encodedMetaString;
  }

  private EncodedMetaString readSmallMetaString(
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
    int encodingValue = encoding & 0xff;
    if (cache.bytes.length == len
        && cache.encodingValue == encodingValue
        && cache.first8Bytes == v1
        && cache.second8Bytes == v2) {
      return cache;
    }
    byte key = smallMetaStringKey(len, encodingValue);
    EncodedMetaString encodedMetaString = longLongMetaStringMap.get(v1, v2, key);
    if (encodedMetaString == null) {
      return createSmallMetaString(len, MetaString.Encoding.fromInt(encodingValue), key, v1, v2);
    }
    return encodedMetaString;
  }

  private static byte smallMetaStringKey(int len, int encodingValue) {
    return (byte) (((len - 1) << ENCODING_BITS) | encodingValue);
  }

  private EncodedMetaString readDynamicString(int dynamicId) {
    if (dynamicId <= 0 || dynamicId > dynamicReadStringId) {
      throw new ForyException("Invalid meta string reference id " + dynamicId);
    }
    return dynamicReadStringIds[dynamicId - 1];
  }

  private void updateDynamicString(EncodedMetaString encodedMetaString) {
    int currentDynamicReadId = dynamicReadStringId;
    if (currentDynamicReadId >= MAX_ROOT_META_STRINGS) {
      throw new ForyException("Too many meta string references in payload");
    }
    EncodedMetaString[] readStringIds = dynamicReadStringIds;
    if (readStringIds.length <= currentDynamicReadId) {
      readStringIds = dynamicReadStringIds = growRead(readStringIds, currentDynamicReadId);
    }
    readStringIds[currentDynamicReadId] = encodedMetaString;
    dynamicReadStringId = currentDynamicReadId + 1;
  }

  private static EncodedMetaString[] growRead(EncodedMetaString[] current, int id) {
    int newLength = current.length;
    while (newLength <= id) {
      newLength <<= 1;
    }
    EncodedMetaString[] expanded = new EncodedMetaString[newLength];
    System.arraycopy(current, 0, expanded, 0, current.length);
    return expanded;
  }

  /** Clears all dynamic ids so this reader can be reused for a new deserialization stream. */
  public void reset() {
    int dynamicReadId = dynamicReadStringId;
    dynamicReadStringId = 0;
    // Every root-local cache insertion is followed by occurrence publication in the same read, so
    // no occurrence means both maps are already empty.
    if (dynamicReadId == 0) {
      return;
    }
    // These caches contain untrusted wire bodies before named-type acceptance. Keep them local to
    // one root; accepted names persist only through the resolver's checked TypeInfo caches.
    // A nonempty reset already bounds the backing arrays. Avoid recomputing table capacity on
    // every later root while the root-local maps remain empty.
    if (hash2MetaStringMap.size != 0) {
      hash2MetaStringMap.clear(MAX_RETAINED_META_STRING_SLOTS);
    }
    if (longLongMetaStringMap.size != 0) {
      longLongMetaStringMap.clear(MAX_RETAINED_META_STRING_SLOTS);
    }
    // Clear only committed slots. Failed reads must never make root cleanup index beyond the fixed
    // reference array and leave this reader poisoned for later operations.
    Arrays.fill(
        dynamicReadStringIds, 0, Math.min(dynamicReadId, dynamicReadStringIds.length), null);
  }
}
