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

import java.nio.ByteBuffer;
import org.apache.fory.io.ForyByteArrayInputStream;
import org.apache.fory.io.ForyByteArrayOutputStream;
import org.apache.fory.platform.AndroidSupport;
import org.apache.fory.util.Preconditions;

/** Memory utils for fory. */
public class MemoryUtils {

  public static MemoryBuffer buffer(int size) {
    return wrap(new byte[size]);
  }

  /**
   * Creates a new memory segment that targets to the given heap memory region.
   *
   * <p>This method should be used to turn short lived byte arrays into memory segments.
   *
   * @param buffer The heap memory region.
   * @return A new memory segment that targets the given heap memory region.
   */
  public static MemoryBuffer wrap(byte[] buffer, int offset, int length) {
    return MemoryBuffer.fromByteArray(buffer, offset, length);
  }

  public static MemoryBuffer wrap(byte[] buffer) {
    return MemoryBuffer.fromByteArray(buffer);
  }

  /**
   * Creates a new memory segment that represents the memory backing the given byte buffer section
   * of [buffer.position(), buffer,limit()).
   *
   * @param buffer a direct buffer or heap buffer
   */
  public static MemoryBuffer wrap(ByteBuffer buffer) {
    if (AndroidSupport.IS_ANDROID) {
      // Android ByteBuffer roots copy into a Fory-owned heap buffer; never read direct-buffer
      // native
      // addresses or depend on read-only buffers exposing arrays.
      return copyToHeapBuffer(buffer);
    } else if (buffer.isDirect()) {
      return MemoryBuffer.fromByteBuffer(buffer);
    } else if (buffer.hasArray()) {
      int offset = buffer.arrayOffset() + buffer.position();
      return MemoryBuffer.fromByteArray(buffer.array(), offset, buffer.remaining());
    } else {
      return copyToHeapBuffer(buffer);
    }
  }

  /**
   * Wrap a {@link ForyByteArrayOutputStream} into a {@link MemoryBuffer}. The writerIndex of buffer
   * will be the count of the stream.
   */
  public static void wrap(ForyByteArrayOutputStream stream, MemoryBuffer buffer) {
    Preconditions.checkNotNull(stream);
    byte[] buf = stream.getBuffer();
    int count = stream.getCount();
    buffer.pointTo(buf, 0, buf.length);
    buffer.writerIndex(count);
  }

  /**
   * Wrap a {@link MemoryBuffer} into a {@link ForyByteArrayOutputStream}. The count of the stream
   * will be the writerIndex of buffer.
   */
  public static void wrap(MemoryBuffer buffer, ForyByteArrayOutputStream stream) {
    Preconditions.checkNotNull(stream);
    byte[] bytes = buffer.getHeapMemory();
    Preconditions.checkNotNull(bytes);
    stream.setBuffer(bytes);
    stream.setCount(buffer.writerIndex());
  }

  /**
   * Wrap a {@link ForyByteArrayInputStream} into a {@link MemoryBuffer}. The readerIndex of buffer
   * will be the position of the stream.
   */
  public static void wrap(ForyByteArrayInputStream stream, MemoryBuffer buffer) {
    Preconditions.checkNotNull(stream);
    byte[] buf = stream.getBuffer();
    int count = stream.getCount();
    int pos = stream.getPosition();
    buffer.pointTo(buf, 0, count);
    buffer.readerIndex(pos);
  }

  private static MemoryBuffer copyToHeapBuffer(ByteBuffer buffer) {
    ByteBuffer duplicate = buffer.duplicate();
    byte[] bytes = new byte[duplicate.remaining()];
    duplicate.get(bytes);
    return MemoryBuffer.fromByteArray(bytes);
  }
}
