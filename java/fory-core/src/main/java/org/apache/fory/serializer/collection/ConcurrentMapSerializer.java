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

package org.apache.fory.serializer.collection;

import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import org.apache.fory.Fory;
import org.apache.fory.collection.IterableOnceMapSnapshot;
import org.apache.fory.collection.ObjectArray;
import org.apache.fory.memory.MemoryBuffer;

/**
 * Serializer for concurrent map implementations that require thread-safe serialization.
 *
 * <p>This serializer extends {@link MapSerializer} to provide specialized handling for concurrent
 * maps such as {@link java.util.concurrent.ConcurrentHashMap}. The key feature is the use of {@link
 * IterableOnceMapSnapshot} to create stable snapshots of concurrent maps during serialization,
 * avoiding potential {@link java.util.ConcurrentModificationException} and ensuring thread safety.
 *
 * <p>The serializer maintains a pool of reusable {@link IterableOnceMapSnapshot} instances to
 * minimize object allocation overhead during serialization.
 *
 * <p>This implementation is particularly important for concurrent maps because:
 *
 * <ul>
 *   <li>Concurrent maps can be modified during iteration, causing exceptions
 *   <li>Creating snapshots ensures consistent serialization state
 *   <li>Object pooling reduces garbage collection pressure
 * </ul>
 *
 * @param <T> the type of concurrent map being serialized
 * @since 1.0
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class ConcurrentMapSerializer<T extends ConcurrentMap> extends MapSerializer<T> {
  /** Pool of reusable IterableOnceMapSnapshot instances for efficient serialization. */
  protected final ObjectArray<IterableOnceMapSnapshot> snapshots = new ObjectArray<>(1);

  /**
   * Constructs a new ConcurrentMapSerializer for the specified concurrent map type.
   *
   * @param fory the Fory instance for serialization context
   * @param type the class type of the concurrent map to serialize
   * @param supportCodegen whether code generation is supported for this serializer
   */
  public ConcurrentMapSerializer(Fory fory, Class<T> type, boolean supportCodegen) {
    super(fory, type, supportCodegen);
  }

  /**
   * Creates a snapshot of the concurrent map for safe serialization.
   *
   * <p>This method retrieves a reusable {@link IterableOnceMapSnapshot} from the pool, or creates a
   * new one if none are available. It then creates a snapshot of the concurrent map to avoid
   * concurrent modification issues during serialization. The map size is written to the buffer
   * before returning the snapshot.
   *
   * @param buffer the memory buffer to write serialization data to
   * @param value the concurrent map to serialize
   * @return a snapshot of the map for safe iteration during serialization
   */
  @Override
  public IterableOnceMapSnapshot onMapWrite(MemoryBuffer buffer, T value) {
    IterableOnceMapSnapshot snapshot = snapshots.popOrNull();
    if (snapshot == null) {
      snapshot = new IterableOnceMapSnapshot();
    }
    snapshot.setMap(value);
    buffer.writeVarUint32Small7(snapshot.size());
    return snapshot;
  }

  /**
   * Cleans up the snapshot after serialization and returns it to the pool for reuse.
   *
   * <p>This method is called after the map serialization is complete. It clears the snapshot to
   * remove all references to the serialized data and returns the snapshot instance to the pool for
   * future reuse, improving memory efficiency.
   *
   * @param map the snapshot that was used for serialization
   */
  @Override
  public void onMapWriteFinish(Map map) {
    IterableOnceMapSnapshot snapshot = (IterableOnceMapSnapshot) map;
    snapshot.clear();
    snapshots.add(snapshot);
  }
}
