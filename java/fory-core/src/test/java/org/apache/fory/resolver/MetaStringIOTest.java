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

package org.apache.fory.resolver;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

import java.nio.ByteBuffer;
import org.apache.fory.Fory;
import org.apache.fory.TestUtils;
import org.apache.fory.collection.LongLongByteMap;
import org.apache.fory.collection.MetadataLongMap;
import org.apache.fory.context.MetaStringReader;
import org.apache.fory.context.MetaStringWriter;
import org.apache.fory.exception.ForyException;
import org.apache.fory.exception.InsecureException;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.memory.MemoryUtils;
import org.apache.fory.meta.EncodedMetaString;
import org.apache.fory.meta.Encoders;
import org.apache.fory.util.StringUtils;
import org.testng.annotations.Test;

public class MetaStringIOTest {
  private static SharedRegistry newSharedRegistry() {
    return new SharedRegistry();
  }

  @Test
  public void testWriteMetaString() {
    MetaStringWriter writer = new MetaStringWriter();
    MetaStringReader reader = new MetaStringReader();
    MemoryBuffer buffer = MemoryUtils.buffer(32);
    String str = StringUtils.random(128, 0);
    EncodedMetaString encodedMetaString = newGenericMetaString(str);
    for (int i = 0; i < 128; i++) {
      writer.writeMetaString(buffer, encodedMetaString);
    }
    for (int i = 0; i < 128; i++) {
      String decoded = reader.readMetaString(buffer).decode(Encoders.GENERIC_DECODER);
      assertEquals(decoded.hashCode(), str.hashCode());
      assertEquals(decoded.getBytes(), str.getBytes());
    }
    assertTrue(buffer.writerIndex() < str.getBytes().length + 128 * 4);
  }

  @Test
  public void testWriteSmallMetaString() {
    for (MemoryBuffer buffer :
        new MemoryBuffer[] {
          MemoryUtils.buffer(32), MemoryUtils.wrap(ByteBuffer.allocateDirect(32)),
        }) {
      for (int i = 0; i < 32; i++) {
        String str = StringUtils.random(i, 0);
        MetaStringWriter writer = new MetaStringWriter();
        MetaStringReader reader = new MetaStringReader();
        writer.writeMetaString(buffer, newGenericMetaString(str));
        String metaString = reader.readMetaString(buffer).decode(Encoders.GENERIC_DECODER);
        assertEquals(metaString.hashCode(), str.hashCode());
        assertEquals(metaString.getBytes(), str.getBytes());
        buffer.readerIndex(0);
        buffer.writerIndex(0);
      }
    }
  }

  @Test
  public void testWriterResetClearsIds() {
    MetaStringWriter writer = new MetaStringWriter();
    MetaStringReader reader = new MetaStringReader();
    EncodedMetaString metaString = newGenericMetaString("thread_safe_fory");
    MemoryBuffer buffer = MemoryUtils.buffer(64);

    writer.writeMetaString(buffer, metaString);
    writer.reset();
    buffer.writerIndex(0);
    buffer.readerIndex(0);

    writer.writeMetaString(buffer, metaString);

    assertEquals(
        reader.readMetaString(buffer).decode(Encoders.GENERIC_DECODER), "thread_safe_fory");
  }

  @Test
  public void testReaderKeepsNamesLocal() {
    SharedRegistry sharedRegistry = newSharedRegistry();
    MetaStringWriter writer = new MetaStringWriter();
    MetaStringReader reader = new MetaStringReader();
    EncodedMetaString encodedMetaString = newGenericMetaString("shared_meta_string");
    MemoryBuffer buffer = MemoryUtils.buffer(64);

    writer.writeMetaString(buffer, encodedMetaString);

    EncodedMetaString readMetaString = reader.readMetaString(buffer);
    assertEquals(sharedRegistry.encodedMetaStringMap.size(), 0);
    EncodedMetaString cachedMetaString =
        sharedRegistry.getOrCreateEncodedMetaString(
            encodedMetaString.bytes, encodedMetaString.hash);

    assertNotSame(readMetaString, cachedMetaString);
  }

  @Test
  public void testExpectedNamesAcrossRoots() {
    SharedRegistry sharedRegistry = newSharedRegistry();
    EncodedMetaString smallName = sharedRegistry.getPackageEncodedMetaString("pkg");
    EncodedMetaString largeName =
        sharedRegistry.getTypeNameEncodedMetaString("PersistentExpectedCandidateLongName");
    assertTrue(smallName.bytes.length <= 16);
    assertTrue(largeName.bytes.length > 16);
    MetaStringReader reader = new MetaStringReader();

    for (int root = 0; root < 2; root++) {
      MemoryBuffer buffer =
          MemoryUtils.buffer(smallName.bytes.length + largeName.bytes.length + 32);
      MetaStringWriter writer = new MetaStringWriter();
      writer.writeMetaString(buffer, smallName);
      writer.writeMetaString(buffer, largeName);

      assertSame(reader.readMetaString(buffer, smallName), smallName);
      assertSame(reader.readMetaString(buffer, largeName), largeName);
      assertReadCachesEmpty(reader);
      reader.reset();

      assertReadCachesEmpty(reader);
      assertSame(sharedRegistry.getPackageEncodedMetaString("pkg"), smallName);
      assertSame(
          sharedRegistry.getTypeNameEncodedMetaString("PersistentExpectedCandidateLongName"),
          largeName);
    }
  }

  @Test
  public void testRejectedNameStaysLocal() {
    byte[] bytes = newNamedTypeBytes();
    SharedRegistry sharedRegistry = newSharedRegistry();
    Fory reader =
        Fory.builder()
            .withXlang(false)
            .requireClassRegistration(true)
            .withCompatible(false)
            .withSharedRegistry(sharedRegistry)
            .build();
    int sharedNames = sharedRegistry.encodedMetaStringMap.size();

    expectThrows(InsecureException.class, () -> reader.deserialize(bytes));

    assertEquals(sharedRegistry.encodedMetaStringMap.size(), sharedNames);
    assertReadCachesEmpty(reader.getReadContext().getMetaStringReader());
  }

  @Test
  public void testAcceptedNameStaysLocal() {
    byte[] bytes = newNamedTypeBytes();
    SharedRegistry sharedRegistry = newSharedRegistry();
    Fory reader =
        Fory.builder()
            .withXlang(false)
            .requireClassRegistration(true)
            .withCompatible(false)
            .withSharedRegistry(sharedRegistry)
            .build();
    int initialSharedNames = sharedRegistry.encodedMetaStringMap.size();
    reader.register(LongNamedTypeForMetaStringPublication.class);
    int registeredSharedNames = sharedRegistry.encodedMetaStringMap.size();
    assertTrue(registeredSharedNames > initialSharedNames);

    LongNamedTypeForMetaStringPublication value =
        reader.deserialize(bytes, LongNamedTypeForMetaStringPublication.class);

    assertEquals(value.number, 17);
    assertEquals(sharedRegistry.encodedMetaStringMap.size(), registeredSharedNames);
    assertReadCachesEmpty(reader.getReadContext().getMetaStringReader());
  }

  @Test
  public void testSharedRegistrySkipsLongEncodedMetaStrings() {
    SharedRegistry sharedRegistry = newSharedRegistry();
    String str = StringUtils.random(2050, 0);

    EncodedMetaString first = newGenericMetaString(sharedRegistry, str);
    EncodedMetaString second = newGenericMetaString(sharedRegistry, str);

    assertNotSame(first, second);
  }

  @Test
  public void testSharedRegistryCapsEncodedMetaStringCount() {
    SharedRegistry sharedRegistry = newSharedRegistry();
    EncodedMetaString first = null;
    for (int i = 0; i < 32768; i++) {
      EncodedMetaString encodedMetaString = newGenericMetaString(sharedRegistry, "meta_" + i);
      if (i == 0) {
        first = encodedMetaString;
      }
    }

    EncodedMetaString overflow1 = newGenericMetaString(sharedRegistry, "meta_overflow");
    EncodedMetaString overflow2 = newGenericMetaString(sharedRegistry, "meta_overflow");

    assertSame(first, newGenericMetaString(sharedRegistry, "meta_0"));
    assertNotSame(overflow1, overflow2);
  }

  @Test
  public void testRejectsNonCanonicalHash() {
    MetaStringReader reader = new MetaStringReader();
    EncodedMetaString encodedMetaString = newGenericMetaString(StringUtils.random(32, 0));
    MemoryBuffer buffer = MemoryUtils.buffer(64);

    buffer.writeVarUInt32Small7(encodedMetaString.bytes.length << 1);
    buffer.writeInt64(encodedMetaString.hash + 0x100);
    buffer.writeBytes(encodedMetaString.bytes);

    expectThrows(ForyException.class, () -> reader.readMetaString(buffer));
  }

  @Test
  public void testBigMetaStringCacheHit() {
    MetaStringReader reader = new MetaStringReader();
    EncodedMetaString encodedMetaString = newGenericMetaString(StringUtils.random(32, 0));
    MemoryBuffer buffer = MemoryUtils.buffer(128);

    writeBigMetaString(buffer, encodedMetaString);
    writeBigMetaString(buffer, encodedMetaString);
    EncodedMetaString first = reader.readMetaString(buffer);
    EncodedMetaString second = reader.readMetaString(buffer);

    assertSame(first, second);
    assertEquals(buffer.readerIndex(), buffer.writerIndex());
  }

  @Test
  public void testExpectedBigNameIdentity() {
    MetaStringReader reader = new MetaStringReader();
    EncodedMetaString encodedMetaString = newGenericMetaString(StringUtils.random(32, 0));
    byte[] differentBytes = encodedMetaString.bytes.clone();
    differentBytes[0] ^= 1;
    EncodedMetaString wrongCache = new EncodedMetaString(differentBytes, encodedMetaString.hash);
    MemoryBuffer buffer = newBigMetaStringBuffer(encodedMetaString);

    EncodedMetaString read = reader.readMetaString(buffer, wrongCache);

    assertNotSame(read, wrongCache);
    assertEquals(read.bytes, encodedMetaString.bytes);
    assertEquals(buffer.readerIndex(), buffer.writerIndex());
  }

  @Test
  public void testCachedBigNameIdentity() {
    MetaStringReader reader = new MetaStringReader();
    EncodedMetaString encodedMetaString = newGenericMetaString(StringUtils.random(32, 0));
    byte[] differentBytes = encodedMetaString.bytes.clone();
    differentBytes[0] ^= 1;
    EncodedMetaString wrongCache = new EncodedMetaString(differentBytes, encodedMetaString.hash);
    MetadataLongMap<EncodedMetaString> readCache =
        TestUtils.getFieldValue(reader, "hash2MetaStringMap");
    readCache.put(encodedMetaString.hash, wrongCache);
    MemoryBuffer buffer = newBigMetaStringBuffer(encodedMetaString);

    EncodedMetaString read = reader.readMetaString(buffer);

    assertNotSame(read, wrongCache);
    assertEquals(read.bytes, encodedMetaString.bytes);
    assertEquals(buffer.readerIndex(), buffer.writerIndex());
  }

  @Test
  public void testSmallMetaStringKey() {
    MetaStringReader reader = new MetaStringReader();
    MemoryBuffer buffer = MemoryUtils.buffer(32);

    buffer.writeVarUInt32Small7(1 << 1);
    buffer.writeByte(0);
    buffer.writeByte('a');
    buffer.writeVarUInt32Small7(2 << 1);
    buffer.writeByte(0);
    buffer.writeByte('a');
    buffer.writeByte(0);

    EncodedMetaString oneByte = reader.readMetaString(buffer);
    EncodedMetaString twoBytes = reader.readMetaString(buffer);

    assertEquals(oneByte.bytes.length, 1);
    assertEquals(twoBytes.bytes.length, 2);
    assertNotEquals(oneByte.hash, twoBytes.hash);
  }

  @Test
  public void testResetClearsReadCaches() {
    MetaStringReader reader = new MetaStringReader();
    MemoryBuffer buffer = MemoryUtils.buffer(32);

    buffer.writeVarUInt32Small7(1 << 1);
    buffer.writeByte(0);
    buffer.writeByte('a');
    reader.readMetaString(buffer);

    LongLongByteMap<EncodedMetaString> smallCache =
        TestUtils.getFieldValue(reader, "longLongMetaStringMap");
    MetadataLongMap<EncodedMetaString> bigCache =
        TestUtils.getFieldValue(reader, "hash2MetaStringMap");
    EncodedMetaString value = newGenericMetaString("value");
    for (int i = 1; i <= 2048; i++) {
      smallCache.put(i, 0, (byte) 0, value);
      bigCache.put(i, value);
    }
    assertTrue(((Object[]) TestUtils.getFieldValue(smallCache, "keyTable")).length > 2048);
    assertTrue(((long[]) TestUtils.getFieldValue(bigCache, "keyTable")).length > 2048);

    reader.reset();

    assertEquals(smallCache.size, 0);
    assertEquals(bigCache.size, 0);
    assertTrue(((Object[]) TestUtils.getFieldValue(smallCache, "keyTable")).length <= 2048);
    assertTrue(((long[]) TestUtils.getFieldValue(bigCache, "keyTable")).length <= 2048);

    MemoryBuffer refBuffer = MemoryUtils.buffer(8);
    refBuffer.writeVarUInt32Small7((1 << 1) | 1);
    expectThrows(ForyException.class, () -> reader.readMetaString(refBuffer));
  }

  @Test
  public void testMetaStringLimitCleanup() {
    MetaStringReader reader = new MetaStringReader();
    MemoryBuffer buffer = MemoryUtils.buffer(1 << 16);
    for (int i = 0; i <= 8192; i++) {
      buffer.writeVarUInt32Small7(1 << 1);
      buffer.writeByte(0);
      buffer.writeByte('a');
    }

    for (int i = 0; i < 8192; i++) {
      reader.readMetaString(buffer);
    }
    expectThrows(ForyException.class, () -> reader.readMetaString(buffer));
    reader.reset();

    MemoryBuffer nextRoot = MemoryUtils.buffer(8);
    nextRoot.writeVarUInt32Small7(1 << 1);
    nextRoot.writeByte(0);
    nextRoot.writeByte('b');
    assertEquals(reader.readMetaString(nextRoot).bytes, new byte[] {'b'});
  }

  @Test
  public void testTypeNameBytesUsesBytesWhenHashesMatch() {
    EncodedMetaString namespace1 = new EncodedMetaString(new byte[] {'a'}, 0x100);
    EncodedMetaString namespace2 = new EncodedMetaString(new byte[] {'b'}, 0x100);
    EncodedMetaString typeName = new EncodedMetaString(new byte[] {'C'}, 0x200);

    assertNotEquals(
        new TypeNameBytes(namespace1, typeName), new TypeNameBytes(namespace2, typeName));
  }

  private static EncodedMetaString newGenericMetaString(String str) {
    return Encoders.GENERIC_ENCODER.encodeBinary(str, Encoders.computeGenericEncoding(str));
  }

  private static EncodedMetaString newGenericMetaString(SharedRegistry sharedRegistry, String str) {
    EncodedMetaString encodedMetaString = newGenericMetaString(str);
    return sharedRegistry.getOrCreateEncodedMetaString(
        encodedMetaString.bytes, encodedMetaString.hash);
  }

  private static MemoryBuffer newBigMetaStringBuffer(EncodedMetaString encodedMetaString) {
    MemoryBuffer buffer = MemoryUtils.buffer(encodedMetaString.bytes.length + 16);
    writeBigMetaString(buffer, encodedMetaString);
    return buffer;
  }

  private static void writeBigMetaString(MemoryBuffer buffer, EncodedMetaString encodedMetaString) {
    buffer.writeVarUInt32Small7(encodedMetaString.bytes.length << 1);
    buffer.writeInt64(encodedMetaString.hash);
    buffer.writeBytes(encodedMetaString.bytes);
  }

  private static byte[] newNamedTypeBytes() {
    Fory writer =
        Fory.builder()
            .withXlang(false)
            .requireClassRegistration(false)
            .withCompatible(false)
            .build();
    LongNamedTypeForMetaStringPublication value = new LongNamedTypeForMetaStringPublication();
    value.number = 17;
    return writer.serialize(value);
  }

  private static void assertReadCachesEmpty(MetaStringReader reader) {
    LongLongByteMap<?> smallCache = TestUtils.getFieldValue(reader, "longLongMetaStringMap");
    MetadataLongMap<?> bigCache = TestUtils.getFieldValue(reader, "hash2MetaStringMap");
    assertEquals(smallCache.size, 0);
    assertEquals(bigCache.size, 0);
  }

  public static final class LongNamedTypeForMetaStringPublication {
    public int number;
  }
}
