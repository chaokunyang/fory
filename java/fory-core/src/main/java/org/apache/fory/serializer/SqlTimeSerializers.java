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

import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import org.apache.fory.config.Config;
import org.apache.fory.context.CopyContext;
import org.apache.fory.context.ReadContext;
import org.apache.fory.context.WriteContext;
import org.apache.fory.memory.MemoryBuffer;

/** Optional SQL time serializers loaded only when {@code java.sql} types are actually used. */
public final class SqlTimeSerializers {
  private SqlTimeSerializers() {}

  public static final class SqlDateSerializer
      extends TimeSerializers.BaseDateSerializer<java.sql.Date> {
    public SqlDateSerializer(Config config) {
      super(config, java.sql.Date.class);
    }

    public SqlDateSerializer(Config config, boolean needToWriteRef) {
      super(config, java.sql.Date.class, needToWriteRef);
    }

    @Override
    protected java.sql.Date newInstance(long time) {
      return new java.sql.Date(time);
    }

    @Override
    public java.sql.Date copy(CopyContext copyContext, java.sql.Date value) {
      return newInstance(value.getTime());
    }
  }

  public static final class SqlTimeSerializer extends TimeSerializers.BaseDateSerializer<Time> {
    public SqlTimeSerializer(Config config) {
      super(config, Time.class);
    }

    public SqlTimeSerializer(Config config, boolean needToWriteRef) {
      super(config, Time.class, needToWriteRef);
    }

    @Override
    protected Time newInstance(long time) {
      return new Time(time);
    }

    @Override
    public Time copy(CopyContext copyContext, Time value) {
      return newInstance(value.getTime());
    }
  }

  public static final class TimestampSerializer extends TimeSerializers.TimeSerializer<Timestamp> {
    public TimestampSerializer(Config config) {
      super(config, Timestamp.class);
    }

    public TimestampSerializer(Config config, boolean needToWriteRef) {
      super(config, Timestamp.class, needToWriteRef);
    }

    @Override
    public void write(WriteContext writeContext, Timestamp value) {
      MemoryBuffer buffer = writeContext.getBuffer();
      if (config.isXlang()) {
        Instant instant = value.toInstant();
        buffer.writeInt64(instant.getEpochSecond());
        buffer.writeInt32(instant.getNano());
      } else {
        long time = value.getTime() - (value.getNanos() / 1_000_000);
        buffer.writeInt64(time);
        buffer.writeInt32(value.getNanos());
      }
    }

    @Override
    public Timestamp read(ReadContext readContext) {
      MemoryBuffer buffer = readContext.getBuffer();
      if (config.isXlang()) {
        long seconds = buffer.readInt64();
        int nanos = buffer.readInt32();
        return Timestamp.from(Instant.ofEpochSecond(seconds, nanos));
      }
      Timestamp t = new Timestamp(buffer.readInt64());
      t.setNanos(buffer.readInt32());
      return t;
    }

    @Override
    public Timestamp copy(CopyContext copyContext, Timestamp value) {
      return new Timestamp(value.getTime());
    }
  }
}
