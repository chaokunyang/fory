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

/**
 * Resizable list backed by a float array for 32-bit floating values.
 *
 * <p>Supports auto-growing on insertions, primitive overloads to avoid boxing, and direct access
 * to the backing array for zero-copy interop. Prefer primitive get/set/add to avoid boxing cost;
 * elements are always non-null. The {@link #size()} tracks the logical element count while the
 * backing array capacity may be larger.
 */
public final class Float32List extends AbstractList<Float> implements RandomAccess {
  private static final int DEFAULT_CAPACITY = 10;

  private float[] array;
  private int size;

  /** Creates an empty list with default capacity. */
  public Float32List() {
    this(DEFAULT_CAPACITY);
  }

  /**
   * Creates an empty list with a given initial capacity.
   *
   * @param initialCapacity starting backing array length; must be non-negative
   * @throws IllegalArgumentException if {@code initialCapacity} is negative
   */
  public Float32List(int initialCapacity) {
    if (initialCapacity < 0) {
      throw new IllegalArgumentException("Illegal capacity: " + initialCapacity);
    }
    this.array = new float[initialCapacity];
    this.size = 0;
  }

  /**
   * Wraps an existing array as the backing storage.
   *
   * @param array source array; its current length becomes {@link #size()}
   */
  public Float32List(float[] array) {
    this.array = array;
    this.size = array.length;
  }

  @Override
  public Float get(int index) {
    checkIndex(index);
    return array[index];
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public Float set(int index, Float element) {
    checkIndex(index);
    Objects.requireNonNull(element, "element");
    float prev = array[index];
    array[index] = element;
    return prev;
  }

  /** Sets a value without boxing. */
  public void set(int index, float value) {
    checkIndex(index);
    array[index] = value;
  }

  /** Sets a value without boxing; truncates to 32-bit float. */
  public void set(int index, double value) {
    checkIndex(index);
    array[index] = (float) value;
  }

  public float getFloat(int index) {
    checkIndex(index);
    return array[index];
  }

  @Override
  public void add(int index, Float element) {
    checkPositionIndex(index);
    Objects.requireNonNull(element, "element");
    ensureCapacity(size + 1);
    System.arraycopy(array, index, array, index + 1, size - index);
    array[index] = element;
    size++;
    modCount++;
  }

  @Override
  public boolean add(Float element) {
    Objects.requireNonNull(element, "element");
    ensureCapacity(size + 1);
    array[size++] = element;
    modCount++;
    return true;
  }

  /** Appends a value without boxing. */
  public boolean add(float value) {
    ensureCapacity(size + 1);
    array[size++] = value;
    modCount++;
    return true;
  }

  /** Appends a value without boxing; truncates to 32-bit float. */
  public boolean add(double value) {
    ensureCapacity(size + 1);
    array[size++] = (float) value;
    modCount++;
    return true;
  }

  /** Returns the live backing array; elements beyond {@code size()} are undefined. */
  public float[] getArray() {
    return array;
  }

  /** Returns a trimmed copy containing exactly {@code size()} elements. */
  public float[] copyArray() {
    return Arrays.copyOf(array, size);
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
}
