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

import java.io.ByteArrayInputStream;
import org.apache.fory.util.Preconditions;

/** A {@link ByteArrayInputStream} with public accessors for its protected state. */
public class ForyByteArrayInputStream extends ByteArrayInputStream {
  public ForyByteArrayInputStream(byte[] buffer) {
    super(buffer);
  }

  public ForyByteArrayInputStream(byte[] buffer, int offset, int length) {
    super(buffer, offset, length);
  }

  public byte[] getBuffer() {
    return buf;
  }

  public void setBuffer(byte[] buffer) {
    buf = Preconditions.checkNotNull(buffer);
    pos = 0;
    mark = 0;
    count = buffer.length;
  }

  public void setBuffer(byte[] buffer, int offset, int length) {
    Preconditions.checkNotNull(buffer);
    Preconditions.checkArgument(offset >= 0 && length >= 0 && length <= buffer.length - offset);
    buf = buffer;
    pos = offset;
    mark = offset;
    count = offset + length;
  }

  public int getPosition() {
    return pos;
  }

  public void setPosition(int position) {
    Preconditions.checkArgument(position >= 0 && position <= count);
    pos = position;
  }

  public int getMark() {
    return mark;
  }

  public void setMark(int mark) {
    Preconditions.checkArgument(mark >= 0 && mark <= count);
    this.mark = mark;
  }

  public int getCount() {
    return count;
  }

  public void setCount(int count) {
    Preconditions.checkArgument(count >= 0 && count <= buf.length);
    Preconditions.checkArgument(pos <= count && mark <= count);
    this.count = count;
  }
}
