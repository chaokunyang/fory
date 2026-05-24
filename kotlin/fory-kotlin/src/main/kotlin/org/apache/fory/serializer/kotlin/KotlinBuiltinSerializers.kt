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

package org.apache.fory.serializer.kotlin

import org.apache.fory.config.Config
import org.apache.fory.context.ReadContext
import org.apache.fory.context.WriteContext
import org.apache.fory.serializer.ImmutableSerializer
import org.apache.fory.serializer.Shareable

public class PairSerializer(config: Config) :
  ImmutableSerializer<Pair<*, *>>(config, Pair::class.java), Shareable {
  override fun write(writeContext: WriteContext, value: Pair<*, *>) {
    writeContext.writeRef(value.first)
    writeContext.writeRef(value.second)
  }

  override fun read(readContext: ReadContext): Pair<*, *> {
    return Pair(readContext.readRef(), readContext.readRef())
  }
}

public class TripleSerializer(config: Config) :
  ImmutableSerializer<Triple<*, *, *>>(config, Triple::class.java), Shareable {
  override fun write(writeContext: WriteContext, value: Triple<*, *, *>) {
    writeContext.writeRef(value.first)
    writeContext.writeRef(value.second)
    writeContext.writeRef(value.third)
  }

  override fun read(readContext: ReadContext): Triple<*, *, *> {
    return Triple(readContext.readRef(), readContext.readRef(), readContext.readRef())
  }
}

public class ResultSerializer(config: Config) :
  ImmutableSerializer<Result<*>>(config, Result::class.java), Shareable {
  override fun write(writeContext: WriteContext, value: Result<*>) {
    val failure = value.exceptionOrNull()
    val buffer = writeContext.buffer
    buffer.writeBoolean(failure == null)
    if (failure == null) {
      writeContext.writeRef(value.getOrNull())
    } else {
      writeContext.writeRef(failure)
    }
  }

  override fun read(readContext: ReadContext): Result<*> {
    return if (readContext.buffer.readBoolean()) {
      Result.success<Any?>(readContext.readRef())
    } else {
      Result.failure<Any?>(readContext.readRef() as Throwable)
    }
  }
}

public class CharRangeSerializer(config: Config) :
  ImmutableSerializer<CharRange>(config, CharRange::class.java), Shareable {
  override fun write(writeContext: WriteContext, value: CharRange) {
    val buffer = writeContext.buffer
    buffer.writeInt16(value.first.code.toShort())
    buffer.writeInt16(value.last.code.toShort())
  }

  override fun read(readContext: ReadContext): CharRange {
    val buffer = readContext.buffer
    return CharRange(buffer.readInt16().toInt().toChar(), buffer.readInt16().toInt().toChar())
  }
}

public class CharProgressionSerializer(config: Config) :
  ImmutableSerializer<CharProgression>(config, CharProgression::class.java), Shareable {
  override fun write(writeContext: WriteContext, value: CharProgression) {
    val buffer = writeContext.buffer
    buffer.writeInt16(value.first.code.toShort())
    buffer.writeInt16(value.last.code.toShort())
    buffer.writeInt32(value.step)
  }

  override fun read(readContext: ReadContext): CharProgression {
    val buffer = readContext.buffer
    return CharProgression.fromClosedRange(
      buffer.readInt16().toInt().toChar(),
      buffer.readInt16().toInt().toChar(),
      buffer.readInt32()
    )
  }
}

public class IntRangeSerializer(config: Config) :
  ImmutableSerializer<IntRange>(config, IntRange::class.java), Shareable {
  override fun write(writeContext: WriteContext, value: IntRange) {
    val buffer = writeContext.buffer
    buffer.writeInt32(value.first)
    buffer.writeInt32(value.last)
  }

  override fun read(readContext: ReadContext): IntRange {
    val buffer = readContext.buffer
    return IntRange(buffer.readInt32(), buffer.readInt32())
  }
}

public class IntProgressionSerializer(config: Config) :
  ImmutableSerializer<IntProgression>(config, IntProgression::class.java), Shareable {
  override fun write(writeContext: WriteContext, value: IntProgression) {
    val buffer = writeContext.buffer
    buffer.writeInt32(value.first)
    buffer.writeInt32(value.last)
    buffer.writeInt32(value.step)
  }

  override fun read(readContext: ReadContext): IntProgression {
    val buffer = readContext.buffer
    return IntProgression.fromClosedRange(
      buffer.readInt32(),
      buffer.readInt32(),
      buffer.readInt32()
    )
  }
}

public class LongRangeSerializer(config: Config) :
  ImmutableSerializer<LongRange>(config, LongRange::class.java), Shareable {
  override fun write(writeContext: WriteContext, value: LongRange) {
    val buffer = writeContext.buffer
    buffer.writeInt64(value.first)
    buffer.writeInt64(value.last)
  }

  override fun read(readContext: ReadContext): LongRange {
    val buffer = readContext.buffer
    return LongRange(buffer.readInt64(), buffer.readInt64())
  }
}

public class LongProgressionSerializer(config: Config) :
  ImmutableSerializer<LongProgression>(config, LongProgression::class.java), Shareable {
  override fun write(writeContext: WriteContext, value: LongProgression) {
    val buffer = writeContext.buffer
    buffer.writeInt64(value.first)
    buffer.writeInt64(value.last)
    buffer.writeInt64(value.step)
  }

  override fun read(readContext: ReadContext): LongProgression {
    val buffer = readContext.buffer
    val first = buffer.readInt64()
    val last = buffer.readInt64()
    val step = buffer.readInt64()
    return LongProgression.fromClosedRange(first, last, step)
  }
}

public class UIntRangeSerializer(config: Config) :
  ImmutableSerializer<UIntRange>(config, UIntRange::class.java), Shareable {
  override fun write(writeContext: WriteContext, value: UIntRange) {
    val buffer = writeContext.buffer
    buffer.writeInt32(value.first.toInt())
    buffer.writeInt32(value.last.toInt())
  }

  override fun read(readContext: ReadContext): UIntRange {
    val buffer = readContext.buffer
    return UIntRange(buffer.readInt32().toUInt(), buffer.readInt32().toUInt())
  }
}

public class UIntProgressionSerializer(config: Config) :
  ImmutableSerializer<UIntProgression>(config, UIntProgression::class.java), Shareable {
  override fun write(writeContext: WriteContext, value: UIntProgression) {
    val buffer = writeContext.buffer
    buffer.writeInt32(value.first.toInt())
    buffer.writeInt32(value.last.toInt())
    buffer.writeInt32(value.step)
  }

  override fun read(readContext: ReadContext): UIntProgression {
    val buffer = readContext.buffer
    return UIntProgression.fromClosedRange(
      buffer.readInt32().toUInt(),
      buffer.readInt32().toUInt(),
      buffer.readInt32()
    )
  }
}

public class ULongRangeSerializer(config: Config) :
  ImmutableSerializer<ULongRange>(config, ULongRange::class.java), Shareable {
  override fun write(writeContext: WriteContext, value: ULongRange) {
    val buffer = writeContext.buffer
    buffer.writeInt64(value.first.toLong())
    buffer.writeInt64(value.last.toLong())
  }

  override fun read(readContext: ReadContext): ULongRange {
    val buffer = readContext.buffer
    return ULongRange(buffer.readInt64().toULong(), buffer.readInt64().toULong())
  }
}

public class ULongProgressionSerializer(config: Config) :
  ImmutableSerializer<ULongProgression>(config, ULongProgression::class.java), Shareable {
  override fun write(writeContext: WriteContext, value: ULongProgression) {
    val buffer = writeContext.buffer
    buffer.writeInt64(value.first.toLong())
    buffer.writeInt64(value.last.toLong())
    buffer.writeInt64(value.step)
  }

  override fun read(readContext: ReadContext): ULongProgression {
    val buffer = readContext.buffer
    val first = buffer.readInt64().toULong()
    val last = buffer.readInt64().toULong()
    val step = buffer.readInt64()
    return ULongProgression.fromClosedRange(first, last, step)
  }
}
