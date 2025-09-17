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

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.apache.fory.annotation.Internal;

/**
 * A specialized map implementation that creates a snapshot of entries for single iteration. This
 * class is designed to efficiently handle concurrent map serialization by creating a lightweight
 * snapshot that can be iterated without holding references to the original map entries.
 *
 * <p>The implementation uses an array-based storage for entries and provides optimized iteration
 * through a custom iterator. It includes memory management features such as automatic array
 * reallocation when clearing large collections to prevent memory leaks.
 *
 * <p><strong>Important:</strong> The returned iterator from {@link #entrySet()}{@code .iterator()}
 * must be consumed completely before calling {@code iterator()} again. The iterator maintains its
 * position through a shared index, and calling {@code iterator()} again before the previous
 * iterator is fully consumed will result in incorrect iteration behavior.
 *
 * <p>This class is marked as {@code @Internal} and should not be used directly by application code.
 * It's specifically designed for internal serialization purposes.
 *
 * @param <K> the type of keys maintained by this map
 * @param <V> the type of mapped values
 * @since 1.0
 */
@Internal
public class MapSnapshot<K, V> extends AbstractMap<K, V> {
  /** Threshold for array reallocation during clear operation to prevent memory leaks. */
  private static final int CLEAR_ARRAY_SIZE_THRESHOLD = 2048;

  /** Array storing the map entries for iteration. */
  ObjectArray<Entry<K, V>> array;

  /** Reference to the original map (used for snapshot creation). */
  Map<K, V> map;

  /** Current number of entries in the snapshot. */
  int size;

  /** Current iteration index for the iterator. */
  int iterIndex;

  /** Cached entry set for this map. */
  private final EntrySet entrySet;

  /** Cached iterator for this map. */
  private final EntryIterator iterator;

  /**
   * Constructs a new empty MapSnapshot. Initializes the internal array with a default capacity of
   * 16 entries and creates the entry set and iterator instances.
   */
  public MapSnapshot() {
    array = new ObjectArray<>(16);
    entrySet = new EntrySet();
    iterator = new EntryIterator();
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public Set<Entry<K, V>> entrySet() {
    return entrySet;
  }

  /**
   * Entry set implementation for the MapSnapshot. Provides a view of the map entries and supports
   * iteration through the custom EntryIterator.
   */
  class EntrySet extends AbstractSet<Entry<K, V>> {

    @Override
    public Iterator<Entry<K, V>> iterator() {
      iterIndex = 0;
      return iterator;
    }

    @Override
    public int size() {
      return size;
    }
  }

  /**
   * Iterator implementation for the MapSnapshot. This iterator is designed for single-pass
   * iteration and maintains its position through the iterIndex field. It provides efficient access
   * to map entries stored in the underlying array.
   *
   * <p><strong>Important:</strong> This iterator must be consumed completely before calling {@code
   * iterator()} again on the entry set. The iterator shares its position with other potential
   * iterators through the {@code iterIndex} field, and calling {@code iterator()} again before the
   * current iterator is fully consumed will result in incorrect iteration behavior.
   */
  class EntryIterator implements Iterator<Entry<K, V>> {

    @Override
    public boolean hasNext() {
      return iterIndex < size;
    }

    @Override
    public Entry<K, V> next() {
      return array.get(iterIndex++);
    }
  }

  /**
   * Creates a snapshot of the specified map by copying all its entries into the internal array.
   * This method is used to create a stable view of a concurrent map for serialization purposes.
   *
   * <p>The method iterates through all entries in the provided map and adds them to the internal
   * array. The iteration index is reset to 0 to prepare for subsequent iteration.
   *
   * @param map the map to create a snapshot of
   */
  public void setMap(Map<K, V> map) {
    ObjectArray<Entry<K, V>> array = this.array;
    int size = 0;
    for (Entry<K, V> kvEntry : map.entrySet()) {
      array.add(kvEntry);
      size++;
    }
    this.size = size;
  }

  @Override
  public void clear() {
    if (size > CLEAR_ARRAY_SIZE_THRESHOLD) {
      array = new ObjectArray<>(16);
    } else {
      array.clear();
    }
    iterIndex = 0;
    size = 0;
  }
}
