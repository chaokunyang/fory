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

package org.apache.fory.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import javax.annotation.concurrent.NotThreadSafe;
import org.apache.fory.exception.DeserializationException;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.platform.AndroidSupport;
import org.apache.fory.util.Preconditions;

@NotThreadSafe
public class ForyReadableChannel implements ForyStreamReader, ReadableByteChannel {
  private final ReadableByteChannel channel;
  private final MemoryBuffer memoryBuffer;
  private ByteBuffer byteBuffer;

  public ForyReadableChannel(ReadableByteChannel channel) {
    this(
        channel,
        AndroidSupport.IS_ANDROID ? ByteBuffer.allocate(4096) : ByteBuffer.allocateDirect(4096));
  }

  public ForyReadableChannel(ReadableByteChannel channel, ByteBuffer buffer) {
    Preconditions.checkArgument(
        !buffer.isReadOnly(), "ForyReadableChannel requires writable ByteBuffer.");
    this.channel = channel;
    if (AndroidSupport.IS_ANDROID && buffer.isDirect()) {
      buffer = ByteBuffer.allocate(buffer.capacity());
    }
    this.byteBuffer = buffer;
    if (buffer.isDirect()) {
      this.memoryBuffer = MemoryBuffer.fromDirectByteBuffer(buffer, 0, this);
    } else if (buffer.hasArray()) {
      this.memoryBuffer =
          MemoryBuffer.fromByteArray(
              buffer.array(), buffer.arrayOffset() + buffer.position(), 0, this);
    } else {
      throw new IllegalArgumentException(
          "ForyReadableChannel requires direct or array-backed ByteBuffer.");
    }
  }

  @Override
  public int fillBuffer(int minFillSize) {
    try {
      ByteBuffer byteBuf = byteBuffer;
      MemoryBuffer memoryBuf = memoryBuffer;
      int position = byteBuf.position();
      int newLimit = position + minFillSize;
      if (newLimit > byteBuf.capacity()) {
        int newSize =
            newLimit < MemoryBuffer.BUFFER_GROW_STEP_THRESHOLD
                ? newLimit << 2
                : (int) Math.min(newLimit * 1.5d, Integer.MAX_VALUE);
        ByteBuffer newByteBuf =
            byteBuf.isDirect() ? ByteBuffer.allocateDirect(newSize) : ByteBuffer.allocate(newSize);
        byteBuf.position(0);
        newByteBuf.put(byteBuf);
        byteBuf = byteBuffer = newByteBuf;
        memoryBuf.initByteBuffer(byteBuf, position);
      }
      byteBuf.limit(newLimit);
      readFully(byteBuf, minFillSize);
      memoryBuf.increaseSize(minFillSize);
      return minFillSize;
    } catch (IOException e) {
      throw new DeserializationException("Failed to read the provided byte channel", e);
    }
  }

  @Override
  public int read(ByteBuffer dst) throws IOException {
    int length = dst.remaining();
    MemoryBuffer buf = memoryBuffer;
    int remaining = buf.remaining();
    if (remaining >= length) {
      buf.read(dst, length);
      return length;
    } else {
      buf.read(dst, remaining);
      return channel.read(dst) + remaining;
    }
  }

  @Override
  public void readTo(byte[] dst, int dstIndex, int length) {
    MemoryBuffer buf = memoryBuffer;
    int remaining = buf.remaining();
    if (remaining >= length) {
      buf.readBytes(dst, dstIndex, length);
    } else {
      buf.readBytes(dst, dstIndex, remaining);
      try {
        ByteBuffer buffer = ByteBuffer.wrap(dst, dstIndex + remaining, length - remaining);
        readFully(buffer, length - remaining);
      } catch (IOException e) {
        throw new DeserializationException("Failed to read the provided byte channel", e);
      }
    }
  }

  @Override
  public void readToByteBuffer(ByteBuffer dst, int length) {
    MemoryBuffer buf = memoryBuffer;
    int remaining = buf.remaining();
    if (remaining >= length) {
      buf.read(dst, length);
    } else {
      buf.read(dst, remaining);
      try {
        int dstLimit = dst.limit();
        int newLimit = dst.position() + length - remaining;
        if (dstLimit > newLimit) {
          dst.limit(newLimit);
          try {
            readFully(dst, length - remaining);
          } finally {
            dst.limit(dstLimit);
          }
        } else {
          readFully(dst, length - remaining);
        }
      } catch (IOException e) {
        throw new DeserializationException("Failed to read the provided byte channel", e);
      }
    }
  }

  @Override
  public int readToByteBuffer(ByteBuffer dst) {
    MemoryBuffer buf = memoryBuffer;
    int remaining = buf.remaining();
    if (remaining > 0) {
      buf.read(dst, remaining);
    }
    try {
      return channel.read(dst) + remaining;
    } catch (IOException e) {
      throw new DeserializationException("Failed to read the provided byte channel", e);
    }
  }

  @Override
  public boolean isOpen() {
    return channel.isOpen();
  }

  @Override
  public void close() throws IOException {
    channel.close();
  }

  @Override
  public MemoryBuffer getBuffer() {
    return memoryBuffer;
  }

  private void readFully(ByteBuffer dst, int length) throws IOException {
    int remaining = length;
    while (remaining > 0) {
      int read = channel.read(dst);
      if (read <= 0) {
        throw new DeserializationException("Unexpected end of byte channel");
      }
      remaining -= read;
    }
  }
}
