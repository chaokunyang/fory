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

package org.apache.fory.serializer;

import javax.annotation.concurrent.NotThreadSafe;
import org.apache.fory.Fory;
import org.apache.fory.config.Config;
import org.apache.fory.context.CopyContext;
import org.apache.fory.context.ReadContext;
import org.apache.fory.context.WriteContext;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.resolver.RefMode;
import org.apache.fory.type.TypeUtils;

/**
 * Serialize/deserializer objects into binary. Note that this class is designed as an abstract class
 * instead of interface to reduce virtual method call cost of {@link #needToWriteRef}.
 *
 * @param <T> type of objects being serializing/deserializing
 */
@NotThreadSafe
@SuppressWarnings("unchecked")
public abstract class Serializer<T> {
  protected final Class<T> type;
  protected final boolean needToWriteRef;

  /**
   * Whether to enable circular reference of copy. Only for mutable objects, immutable objects just
   * return itself.
   */
  protected final boolean needToCopyRef;

  protected final boolean immutable;

  public Serializer(Config config, Class<T> type) {
    this(
        config,
        type,
        config.trackingRef() && !TypeUtils.isBoxed(TypeUtils.wrap(type)),
        TypeUtils.isPrimitive(type) || TypeUtils.isBoxed(type));
  }

  public Serializer(Config config, Class<T> type, boolean immutable) {
    this(
        config,
        type,
        config.trackingRef() && !TypeUtils.isBoxed(TypeUtils.wrap(type)),
        immutable);
  }

  public Serializer(Config config, Class<T> type, boolean needToWriteRef, boolean immutable) {
    this.type = type;
    this.needToWriteRef = needToWriteRef;
    this.needToCopyRef = config.copyRef() && !immutable;
    this.immutable = immutable;
  }

  /**
   * Write value to buffer, this method may write ref/null flags based passed {@code refMode}, and
   * the passed value can be null. Note that this method don't write type info, this method is
   * mostly be used in cases the context has already knows the value type when deserialization.
   */
  public void write(WriteContext writeContext, RefMode refMode, T value) {
    MemoryBuffer buffer = writeContext.getBuffer();
    // noinspection Duplicates
    if (refMode == RefMode.TRACKING) {
      if (writeContext.writeRefOrNull(value)) {
        return;
      }
    } else if (refMode == RefMode.NULL_ONLY) {
      if (value == null) {
        buffer.writeByte(Fory.NULL_FLAG);
        return;
      } else {
        buffer.writeByte(Fory.NOT_NULL_VALUE_FLAG);
      }
    }
    write(writeContext, value);
  }

  /**
   * Write value to buffer, this method do not write ref/null flags and the passed value must not be
   * null.
   */
  public abstract void write(WriteContext writeContext, T value);

  /**
   * Read value from buffer, this method may read ref/null flags based passed {@code refMode}, and
   * the read value can be null. Note that this method don't read type info, this method is mostly
   * be used in cases the context has already knows the value type for deserialization.
   */
  public T read(ReadContext readContext, RefMode refMode) {
    MemoryBuffer buffer = readContext.getBuffer();
    if (refMode == RefMode.TRACKING) {
      T obj;
      int nextReadRefId = readContext.tryPreserveRefId();
      if (nextReadRefId >= Fory.NOT_NULL_VALUE_FLAG) {
        obj = read(readContext);
        readContext.setReadObject(nextReadRefId, obj);
        return obj;
      } else {
        return (T) readContext.getReadObject();
      }
    } else if (refMode != RefMode.NULL_ONLY || buffer.readByte() != Fory.NULL_FLAG) {
      if (needToWriteRef) {
        // in normal case, the read implementation may invoke `readContext.reference` to
        // support circular reference, so we still need this `-1`
        readContext.preserveRefId(-1);
      }
      return read(readContext);
    }
    return null;
  }

  /**
   * Read value from buffer, this method wont read ref/null flags and the read value won't be null.
   */
  public abstract T read(ReadContext readContext);

  public T copy(CopyContext copyContext, T value) {
    if (isImmutable()) {
      return value;
    }
    throw new UnsupportedOperationException(
        String.format("Copy for %s is not supported", value.getClass()));
  }

  public final boolean needToWriteRef() {
    return needToWriteRef;
  }

  public final boolean needToCopyRef() {
    return needToCopyRef;
  }

  public Class<T> getType() {
    return type;
  }

  public boolean isImmutable() {
    return immutable;
  }

  public boolean threadSafe() {
    return false;
  }
}
