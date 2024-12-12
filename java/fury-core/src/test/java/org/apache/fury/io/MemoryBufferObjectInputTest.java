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

package org.apache.fury.io;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.io.IOException;
import org.apache.fury.Fury;
import org.apache.fury.FuryTestBase;
import org.apache.fury.memory.MemoryBuffer;
import org.apache.fury.memory.MemoryUtils;
import org.testng.annotations.Test;

public class MemoryBufferObjectInputTest extends FuryTestBase {

  @Test(dataProvider = "compressNumber")
  public void testFuryObjectInput(boolean compressNumber) throws IOException {
    Fury fury = Fury.builder().withNumberCompressed(compressNumber).build();
    MemoryBuffer buffer = MemoryUtils.buffer(32);
    buffer.writeByte(1);
    if (compressNumber) {
      buffer.writeVarInt32(2);
    } else {
      buffer.writeInt32(2);
    }
    fury.writeInt64(buffer, 3);
    buffer.writeBoolean(true);
    buffer.writeFloat32(4.1f);
    buffer.writeFloat64(4.2);
    fury.writeJavaString(buffer, "abc");
    try (MemoryBufferObjectInput input = new MemoryBufferObjectInput(fury, buffer)) {
      assertEquals(input.readByte(), 1);
      assertEquals(input.readInt(), 2);
      assertEquals(input.readLong(), 3);
      assertTrue(input.readBoolean());
      assertEquals(input.readFloat(), 4.1f);
      assertEquals(input.readDouble(), 4.2);
      assertEquals(input.readUTF(), "abc");
    }
  }
}
