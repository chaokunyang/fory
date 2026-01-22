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
import org.apache.fory.type.unsigned.Uint16;

/** A fixed-size list backed by a short array for unsigned 16-bit values. */
public final class Uint16List extends AbstractList<Uint16> implements RandomAccess {
  private final short[] array;

  public Uint16List(short[] array) {
    this.array = array;
  }

  public Uint16List(int size) {
    this.array = new short[size];
  }

  @Override
  public Uint16 get(int index) {
    checkIndex(index);
    return new Uint16(array[index]);
  }

  @Override
  public int size() {
    return array.length;
  }

  @Override
  public Uint16 set(int index, Uint16 element) {
    checkIndex(index);
    short prev = array[index];
    array[index] = element.shortValue();
    return new Uint16(prev);
  }

  public int getInt(int index) {
    checkIndex(index);
    return array[index] & 0xFFFF;
  }

  public short getShort(int index) {
    checkIndex(index);
    return array[index];
  }

  public void set(int index, short value) {
    checkIndex(index);
    array[index] = value;
  }

  public void set(int index, int value) {
    checkIndex(index);
    array[index] = (short) value;
  }

  public short[] getArray() {
    return array;
  }

  private void checkIndex(int index) {
    if (index < 0 || index >= array.length) {
      throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + array.length);
    }
  }
}
