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
import java.util.Arrays;
import java.util.Objects;
import java.util.RandomAccess;
import org.apache.fory.type.BFloat16;

/**
 * Dense {@link java.util.List} carrier for xlang {@code bfloat16_array} payloads.
 *
 * <p>The list stores packed 16-bit values in a primitive {@code short[]} so the runtime can
 * serialize and deserialize {@code bfloat16_array} without per-element boxing overhead.
 */
public final class BFloat16List extends AbstractList<BFloat16> implements RandomAccess {
  private static final int DEFAULT_CAPACITY = 10;

  private short[] array;
  private int size;

  public BFloat16List() {
    this(DEFAULT_CAPACITY);
  }

  public BFloat16List(int initialCapacity) {
    if (initialCapacity < 0) {
      throw new IllegalArgumentException("Illegal capacity: " + initialCapacity);
    }
    this.array = new short[initialCapacity];
    this.size = 0;
  }

  public BFloat16List(short[] array) {
    this.array = array;
    this.size = array.length;
  }

  @Override
  public BFloat16 get(int index) {
    checkIndex(index);
    return BFloat16.fromBits(array[index]);
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public BFloat16 set(int index, BFloat16 element) {
    checkIndex(index);
    Objects.requireNonNull(element, "element");
    short prev = array[index];
    array[index] = element.toBits();
    return BFloat16.fromBits(prev);
  }

  public void set(int index, short bits) {
    checkIndex(index);
    array[index] = bits;
  }

  public void set(int index, float value) {
    checkIndex(index);
    array[index] = BFloat16.toBits(value);
  }

  @Override
  public void add(int index, BFloat16 element) {
    checkPositionIndex(index);
    ensureCapacity(size + 1);
    System.arraycopy(array, index, array, index + 1, size - index);
    array[index] = element.toBits();
    size++;
    modCount++;
  }

  @Override
  public boolean add(BFloat16 element) {
    Objects.requireNonNull(element, "element");
    ensureCapacity(size + 1);
    array[size++] = element.toBits();
    modCount++;
    return true;
  }

  public boolean add(short bits) {
    ensureCapacity(size + 1);
    array[size++] = bits;
    modCount++;
    return true;
  }

  public boolean add(float value) {
    ensureCapacity(size + 1);
    array[size++] = BFloat16.toBits(value);
    modCount++;
    return true;
  }

  public float getFloat(int index) {
    checkIndex(index);
    return BFloat16.toFloat(array[index]);
  }

  public short getShort(int index) {
    checkIndex(index);
    return array[index];
  }

  public boolean hasArray() {
    return array != null;
  }

  public short[] getArray() {
    return array;
  }

  public short[] copyArray() {
    return Arrays.copyOf(array, size);
  }

  private void ensureCapacity(int minCapacity) {
    if (array.length >= minCapacity) {
      return;
    }
    int newCapacity = array.length + (array.length >> 1) + 1;
    if (newCapacity < minCapacity) {
      newCapacity = minCapacity;
    }
    array = Arrays.copyOf(array, newCapacity);
  }

  private void checkIndex(int index) {
    if (index < 0 || index >= size) {
      throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
    }
  }

  private void checkPositionIndex(int index) {
    if (index < 0 || index > size) {
      throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
    }
  }
}
