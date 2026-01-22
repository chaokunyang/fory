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

/** A fixed-size list backed by a byte array. */
public final class Int8List extends AbstractList<Byte> implements RandomAccess {
  private final byte[] array;

  public Int8List(byte[] array) {
    this.array = array;
  }

  public Int8List(int size) {
    this.array = new byte[size];
  }

  @Override
  public Byte get(int index) {
    checkIndex(index);
    return array[index];
  }

  @Override
  public int size() {
    return array.length;
  }

  @Override
  public Byte set(int index, Byte element) {
    checkIndex(index);
    byte prev = array[index];
    array[index] = element;
    return prev;
  }

  public byte getByte(int index) {
    checkIndex(index);
    return array[index];
  }

  public int getInt(int index) {
    checkIndex(index);
    return array[index];
  }

  public void set(int index, byte value) {
    checkIndex(index);
    array[index] = value;
  }

  public byte[] getArray() {
    return array;
  }

  private void checkIndex(int index) {
    if (index < 0 || index >= array.length) {
      throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + array.length);
    }
  }
}
