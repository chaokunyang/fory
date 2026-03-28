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

import org.apache.fory.Fory;
import org.apache.fory.collection.IntArray;
import org.apache.fory.collection.ObjectArray;
import org.apache.fory.memory.MemoryBuffer;

public final class MapRefReader implements RefReader {
  private static final int DEFAULT_ARRAY_CAPACITY = 3;

  private long readCounter;
  private long readTotalObjectSize = 0;
  private final ObjectArray readObjects = new ObjectArray(DEFAULT_ARRAY_CAPACITY);
  private final IntArray readRefIds = new IntArray(DEFAULT_ARRAY_CAPACITY);
  private Object readObject;

  @Override
  public byte readRefOrNull(MemoryBuffer buffer) {
    byte headFlag = buffer.readByte();
    if (headFlag == Fory.REF_FLAG) {
      readObject = getReadObject(buffer.readVarUint32Small14());
    } else {
      readObject = null;
    }
    return headFlag;
  }

  @Override
  public int preserveRefId() {
    int nextReadRefId = readObjects.size();
    readObjects.add(null);
    readRefIds.add(nextReadRefId);
    return nextReadRefId;
  }

  @Override
  public int preserveRefId(int refId) {
    readRefIds.add(refId);
    return refId;
  }

  @Override
  public int tryPreserveRefId(MemoryBuffer buffer) {
    byte headFlag = buffer.readByte();
    if (headFlag == Fory.REF_FLAG) {
      readObject = getReadObject(buffer.readVarUint32Small14());
    } else {
      readObject = null;
      if (headFlag == Fory.REF_VALUE_FLAG) {
        return preserveRefId();
      }
    }
    return headFlag;
  }

  @Override
  public int lastPreservedRefId() {
    return readRefIds.get(readRefIds.size - 1);
  }

  @Override
  public boolean hasPreservedRefId() {
    return readRefIds.size > 0;
  }

  @Override
  public void reference(Object object) {
    int refId = readRefIds.pop();
    setReadObject(refId, object);
  }

  @Override
  public Object getReadObject(int id) {
    return readObjects.get(id);
  }

  @Override
  public Object getReadObject() {
    return readObject;
  }

  @Override
  public void setReadObject(int id, Object object) {
    if (id >= 0) {
      readObjects.set(id, object);
    }
  }

  public ObjectArray getReadObjects() {
    return readObjects;
  }

  @Override
  public void reset() {
    long totalObjectSize = this.readTotalObjectSize + readObjects.size();
    long counter = this.readCounter + 1;
    if (counter < 0 || totalObjectSize < 0) {
      counter = 1;
      totalObjectSize = readObjects.size();
    }
    this.readCounter = counter;
    this.readTotalObjectSize = totalObjectSize;
    int avg = (int) (totalObjectSize / counter);
    if (avg <= DEFAULT_ARRAY_CAPACITY) {
      avg = DEFAULT_ARRAY_CAPACITY;
    }
    readObjects.clearApproximate(avg);
    readRefIds.clear();
    readObject = null;
  }
}
