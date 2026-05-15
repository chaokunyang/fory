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

package org.apache.fory.kotlin

import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import org.apache.fory.context.ReadContext
import org.apache.fory.context.WriteContext
import org.apache.fory.exception.ForyException

/** Xlang duration payload adapter for Kotlin's source-level Duration carrier. */
public object DurationEncoding {
  @JvmStatic
  public fun write(writeContext: WriteContext, value: Duration) {
    if (value.isInfinite()) {
      throw ForyException("Kotlin xlang duration does not support infinite values")
    }
    val buffer = writeContext.buffer
    value.toComponents { seconds, nanoseconds ->
      var normalizedSeconds = seconds
      var normalizedNanoseconds = nanoseconds
      if (normalizedNanoseconds < 0) {
        if (normalizedSeconds == Long.MIN_VALUE) {
          throw ForyException("Kotlin xlang duration seconds underflow")
        }
        normalizedSeconds -= 1
        normalizedNanoseconds += 1_000_000_000
      }
      buffer.writeVarInt64(normalizedSeconds)
      buffer.writeInt32(normalizedNanoseconds)
    }
  }

  @JvmStatic
  public fun read(readContext: ReadContext): Duration {
    val buffer = readContext.buffer
    val seconds = buffer.readVarInt64()
    val nanoseconds = buffer.readInt32()
    if (nanoseconds < 0 || nanoseconds >= 1_000_000_000) {
      throw ForyException("Invalid xlang duration nanoseconds: $nanoseconds")
    }
    return requireFinite(seconds.seconds + nanoseconds.nanoseconds)
  }

  @JvmStatic
  public fun fromJava(value: java.time.Duration): Duration {
    return requireFinite(value.seconds.seconds + value.nano.nanoseconds)
  }

  private fun requireFinite(value: Duration): Duration {
    if (value.isInfinite()) {
      throw ForyException("Kotlin xlang duration does not support infinite values")
    }
    return value
  }
}
