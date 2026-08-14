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

package org.apache.fory.integration.kotlin.json.corpus

import org.apache.fory.json.ForyJsonModule
import org.apache.fory.json.ModuleContext
import org.apache.fory.json.codec.AbstractJsonValueCodec
import org.apache.fory.json.reader.JsonReader
import org.apache.fory.json.writer.JsonWriter

/** Application value whose JSON authority belongs exclusively to [PlatformJsonModule]. */
public data class PlatformToken(public val value: String)

/** Application module shared by the JVM, Native Image, and Android corpus consumers. */
public object PlatformJsonModule : ForyJsonModule {
  override fun install(context: ModuleContext) {
    context.registerCodec(PlatformToken::class.java, PlatformTokenCodec)
  }
}

private object PlatformTokenCodec : AbstractJsonValueCodec<PlatformToken>() {
  override fun write(writer: JsonWriter, value: PlatformToken?) {
    if (value == null) writer.writeNull() else writer.writeString(value.value)
  }

  override fun read(reader: JsonReader): PlatformToken? {
    val value = reader.readString() ?: return null
    return PlatformToken(value)
  }
}
