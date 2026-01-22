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

package org.apache.fory.collection;

import java.util.AbstractList;
import java.util.RandomAccess;
import org.apache.fory.type.unsigned.Uint32;

/** A fixed-size list backed by an int array for unsigned 32-bit values. */
public final class Uint32List extends AbstractList<Uint32> implements RandomAccess {
  private final int[] array;

  public Uint32List(int[] array) {
    this.array = array;
  }

  public Uint32List(int size) {
    this.array = new int[size];
  }

  @Override
  public Uint32 get(int index) {
    checkIndex(index);
    return new Uint32(array[index]);
  }

  @Override
  public int size() {
    return array.length;
  }

  @Override
  public Uint32 set(int index, Uint32 element) {
    checkIndex(index);
    int prev = array[index];
    array[index] = element.intValue();
    return new Uint32(prev);
  }

  public long getLong(int index) {
    checkIndex(index);
    return Integer.toUnsignedLong(array[index]);
  }

  public int getInt(int index) {
    checkIndex(index);
    return array[index];
  }

  public void set(int index, int value) {
    checkIndex(index);
    array[index] = value;
  }

  public void set(int index, long value) {
    checkIndex(index);
    array[index] = (int) value;
  }

  public int[] getArray() {
    return array;
  }

  private void checkIndex(int index) {
    if (index < 0 || index >= array.length) {
      throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + array.length);
    }
  }
}
