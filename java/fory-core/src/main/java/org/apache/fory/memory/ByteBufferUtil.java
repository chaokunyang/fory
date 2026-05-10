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

package org.apache.fory.memory;

import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import org.apache.fory.platform.UnsafeOps;
import org.apache.fory.util.Preconditions;

public class ByteBufferUtil {
  public static final Class<?> HEAP_BYTE_BUFFER_CLASS = ByteBuffer.allocate(0).getClass();
  public static final Class<?> DIRECT_BYTE_BUFFER_CLASS = ByteBuffer.allocateDirect(0).getClass();

  private static final class DirectBufferAccess {
    private static final long BUFFER_ADDRESS_FIELD_OFFSET;

    static {
      try {
        Field addressField = Buffer.class.getDeclaredField("address");
        BUFFER_ADDRESS_FIELD_OFFSET = UnsafeOps.objectFieldOffset(addressField);
        Preconditions.checkArgument(BUFFER_ADDRESS_FIELD_OFFSET != 0);
      } catch (NoSuchFieldException e) {
        throw new IllegalStateException(e);
      }
    }
  }

  static long getAddress(ByteBuffer buffer) {
    Preconditions.checkNotNull(buffer, "buffer is null");
    Preconditions.checkArgument(buffer.isDirect(), "Can't get address of a non-direct ByteBuffer.");
    try {
      return UnsafeOps.getLong(buffer, DirectBufferAccess.BUFFER_ADDRESS_FIELD_OFFSET);
    } catch (Throwable t) {
      throw new Error("Could not access direct byte buffer address field.", t);
    }
  }

  public static void clearBuffer(Buffer buffer) {
    buffer.clear();
  }

  public static void flipBuffer(Buffer buffer) {
    buffer.flip();
  }

  public static void rewind(Buffer buffer) {
    buffer.rewind();
  }

  public static void position(Buffer buffer, int pos) {
    buffer.position(pos);
  }
}
