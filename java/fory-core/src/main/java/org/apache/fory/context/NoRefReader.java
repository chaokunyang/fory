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

import org.apache.fory.memory.MemoryBuffer;

public final class NoRefReader implements RefReader {
  @Override
  public byte readRefOrNull(MemoryBuffer buffer) {
    return buffer.readByte();
  }

  @Override
  public int preserveRefId() {
    return -1;
  }

  @Override
  public int preserveRefId(int refId) {
    return -1;
  }

  @Override
  public int tryPreserveRefId(MemoryBuffer buffer) {
    return buffer.readByte();
  }

  @Override
  public int lastPreservedRefId() {
    return -1;
  }

  @Override
  public boolean hasPreservedRefId() {
    return false;
  }

  @Override
  public void reference(Object object) {}

  @Override
  public Object getReadObject(int id) {
    return null;
  }

  @Override
  public Object getReadObject() {
    return null;
  }

  @Override
  public void setReadObject(int id, Object object) {}

  @Override
  public void reset() {}
}
