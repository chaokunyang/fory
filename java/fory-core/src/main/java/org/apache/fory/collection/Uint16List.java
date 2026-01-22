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
import org.apache.fory.type.unsigned.Uint16;

/**
 * Resizable list backed by a short array for unsigned 16-bit values.
 *
 * <p>Supports auto-growing on insertions, primitive overloads to avoid boxing, and direct access to
 * the backing array for zero-copy interop. Prefer primitive get/set/add to avoid boxing cost;
 * elements are always non-null. The {@link #size()} tracks the logical element count while the
 * backing array capacity may be larger.
 */
public final class Uint16List extends AbstractList<Uint16> implements RandomAccess {
  private static final int DEFAULT_CAPACITY = 10;

  private short[] array;
  private int size;

  /** Creates an empty list with default capacity. */
  public Uint16List() {
    this(DEFAULT_CAPACITY);
  }

  /**
   * Creates an empty list with a given initial capacity.
   *
   * @param initialCapacity starting backing array length; must be non-negative
   * @throws IllegalArgumentException if {@code initialCapacity} is negative
   */
  public Uint16List(int initialCapacity) {
    if (initialCapacity < 0) {
      throw new IllegalArgumentException("Illegal capacity: " + initialCapacity);
    }
    this.array = new short[initialCapacity];
    this.size = 0;
  }

  /**
   * Wraps an existing array as the backing storage.
   *
   * @param array source array; its current length becomes {@link #size()}
   */
  public Uint16List(short[] array) {
    this.array = array;
    this.size = array.length;
  }

  @Override
  public Uint16 get(int index) {
    checkIndex(index);
    return new Uint16(array[index]);
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public Uint16 set(int index, Uint16 element) {
    checkIndex(index);
    Objects.requireNonNull(element, "element");
    short prev = array[index];
    array[index] = element.shortValue();
    return new Uint16(prev);
  }

  /** Sets a value without boxing. */
  public void set(int index, short value) {
    checkIndex(index);
    array[index] = value;
  }

  /** Sets a value without boxing; truncates to 16 bits. */
  public void set(int index, int value) {
    checkIndex(index);
    array[index] = (short) value;
  }

  @Override
  public void add(int index, Uint16 element) {
    checkPositionIndex(index);
    ensureCapacity(size + 1);
    System.arraycopy(array, index, array, index + 1, size - index);
    array[index] = element.shortValue();
    size++;
    modCount++;
  }

  @Override
  public boolean add(Uint16 element) {
    Objects.requireNonNull(element, "element");
    ensureCapacity(size + 1);
    array[size++] = element.shortValue();
    modCount++;
    return true;
  }

  /** Appends a value without boxing. */
  public boolean add(short value) {
    ensureCapacity(size + 1);
    array[size++] = value;
    modCount++;
    return true;
  }

  /** Appends a value without boxing; truncates to 16 bits. */
  public boolean add(int value) {
    ensureCapacity(size + 1);
    array[size++] = (short) value;
    modCount++;
    return true;
  }

  public int getInt(int index) {
    checkIndex(index);
    return array[index] & 0xFFFF;
  }

  public short getShort(int index) {
    checkIndex(index);
    return array[index];
  }

  /** Returns the live backing array; elements beyond {@code size()} are undefined. */
  public short[] getArray() {
    return array;
  }

  /** Returns a trimmed copy containing exactly {@code size()} elements. */
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
