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
import static org.testng.Assert.assertTrue;

import java.nio.ByteBuffer;
import org.apache.fory.context.MetaStringReader;
import org.apache.fory.context.MetaStringWriter;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.memory.MemoryUtils;
import org.apache.fory.meta.EncodedMetaString;
import org.apache.fory.meta.Encoders;
import org.apache.fory.util.StringUtils;
import org.testng.annotations.Test;

public class MetaStringIOTest {
  @Test
  public void testWriteMetaString() {
    MetaStringWriter writer = new MetaStringWriter();
    MetaStringReader reader = new MetaStringReader();
    MemoryBuffer buffer = MemoryUtils.buffer(32);
    String str = StringUtils.random(128, 0);
    EncodedMetaString encodedMetaString = newGenericMetaString(str);
    for (int i = 0; i < 128; i++) {
      writer.writeMetaStringBytes(buffer, encodedMetaString);
    }
    for (int i = 0; i < 128; i++) {
      String decoded = reader.readMetaString(buffer);
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
        writer.writeMetaStringBytes(buffer, newGenericMetaString(str));
        String metaString = reader.readMetaString(buffer);
        assertEquals(metaString.hashCode(), str.hashCode());
        assertEquals(metaString.getBytes(), str.getBytes());
        buffer.readerIndex(0);
        buffer.writerIndex(0);
      }
    }
  }

  @Test
  public void testMetaStringWriterResetClearsDynamicWriteState() {
    MetaStringWriter writer = new MetaStringWriter();
    MetaStringReader reader = new MetaStringReader();
    EncodedMetaString metaString = newGenericMetaString("thread_safe_fory");
    MemoryBuffer buffer = MemoryUtils.buffer(64);

    writer.writeMetaStringBytes(buffer, metaString);
    writer.reset();
    buffer.writerIndex(0);
    buffer.readerIndex(0);

    writer.writeMetaStringBytes(buffer, metaString);

    assertEquals(reader.readMetaString(buffer), "thread_safe_fory");
  }

  private static EncodedMetaString newGenericMetaString(String str) {
    return Encoders.GENERIC_ENCODER.encodeBinary(str, Encoders.computeGenericEncoding(str));
  }
}
