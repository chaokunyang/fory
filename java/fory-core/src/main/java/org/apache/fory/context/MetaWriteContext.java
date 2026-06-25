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

/**
 * Write-side state for meta-share serialization.
 *
 * <p>When scoped meta share is disabled, the same instance can be reused across multiple writes so
 * already announced classes are not sent repeatedly.
 */
public class MetaWriteContext {
  private static final int INITIAL_CAPACITY = 8;
  private static final int MISSING_ID = -1;

  private Object[] keys = new Object[INITIAL_CAPACITY];
  private int[] idsBySlot = new int[INITIAL_CAPACITY];
  private int[] usedSlots = new int[INITIAL_CAPACITY];
  private int mask = INITIAL_CAPACITY - 1;
  private int threshold = INITIAL_CAPACITY >> 1;
  private int size;

  /** Returns the next protocol id that will be assigned to a new metadata key. */
  public int size() {
    return size;
  }

  /**
   * Returns the existing protocol id for {@code key}, or records it and returns {@code -1}.
   *
   * <p>Meta-share keys use identity semantics. Class objects and layer marker classes are already
   * identity keys, and unknown-struct TypeDef ids historically used the same identity map path.
   */
  public int putOrGetMetaId(Object key) {
    if (key == null) {
      throw new NullPointerException("Meta key must not be null");
    }
    int slot = locate(key, keys, mask);
    if (slot >= 0) {
      return idsBySlot[slot];
    }
    if (size >= threshold) {
      resize(keys.length << 1);
      slot = locate(key, keys, mask);
    }
    slot = -(slot + 1);
    int id = size;
    keys[slot] = key;
    idsBySlot[slot] = id;
    usedSlots[id] = slot;
    size = id + 1;
    return MISSING_ID;
  }

  /** Clears operation-local metadata ids while retaining reusable table storage. */
  public void reset() {
    int size = this.size;
    if (size == 0) {
      return;
    }
    Object[] keys = this.keys;
    int[] usedSlots = this.usedSlots;
    for (int i = 0; i < size; i++) {
      keys[usedSlots[i]] = null;
    }
    this.size = 0;
  }

  private static int locate(Object key, Object[] keys, int mask) {
    for (int slot = System.identityHashCode(key) & mask; ; slot = (slot + 1) & mask) {
      Object other = keys[slot];
      if (other == null) {
        return -(slot + 1);
      }
      if (other == key) {
        return slot;
      }
    }
  }

  private void resize(int newCapacity) {
    Object[] oldKeys = keys;
    int[] oldUsedSlots = usedSlots;
    int oldSize = size;
    Object[] newKeys = new Object[newCapacity];
    int[] newIdsBySlot = new int[newCapacity];
    int[] newUsedSlots = new int[newCapacity];
    int newMask = newCapacity - 1;
    for (int id = 0; id < oldSize; id++) {
      Object key = oldKeys[oldUsedSlots[id]];
      int slot = -(locate(key, newKeys, newMask) + 1);
      newKeys[slot] = key;
      newIdsBySlot[slot] = id;
      newUsedSlots[id] = slot;
    }
    keys = newKeys;
    idsBySlot = newIdsBySlot;
    usedSlots = newUsedSlots;
    mask = newMask;
    threshold = newCapacity >> 1;
  }
}
