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

import java.util.Optional
import org.apache.fory.json.ForyJsonException
import org.apache.fory.json.annotation.JsonCodec
import org.apache.fory.json.annotation.JsonMixin
import org.apache.fory.json.annotation.JsonType
import org.apache.fory.json.codec.AbstractJsonValueCodec
import org.apache.fory.json.codec.MapKeyCodec
import org.apache.fory.json.reader.JsonReader
import org.apache.fory.json.writer.JsonWriter

public class PlatformWholeStringCodec : AbstractJsonValueCodec<String>() {
  override fun write(writer: JsonWriter, value: String?): Unit =
    writeTagged(writer, "whole:", value)

  override fun read(reader: JsonReader): String? = readTagged(reader, "whole:")
}

public class PlatformElementStringCodec : AbstractJsonValueCodec<String>() {
  override fun write(writer: JsonWriter, value: String?): Unit =
    writeTagged(writer, "element:", value)

  override fun read(reader: JsonReader): String? = readTagged(reader, "element:")
}

public class PlatformContentStringCodec : AbstractJsonValueCodec<String>() {
  override fun write(writer: JsonWriter, value: String?): Unit =
    writeTagged(writer, "content:", value)

  override fun read(reader: JsonReader): String? = readTagged(reader, "content:")
}

public class PlatformMapValueStringCodec : AbstractJsonValueCodec<String>() {
  override fun write(writer: JsonWriter, value: String?): Unit =
    writeTagged(writer, "value:", value)

  override fun read(reader: JsonReader): String? = readTagged(reader, "value:")
}

public class PlatformIntKeyCodec : MapKeyCodec {
  override fun toName(key: Any): String = "key:${key as Int}"

  override fun fromName(name: String): Any {
    if (!name.startsWith("key:")) {
      throw ForyJsonException("Expected a tagged platform integer key")
    }
    return name.substring(4).toInt()
  }
}

@JsonType
public data class PlatformCodecSlots(
  @field:JsonCodec(value = PlatformWholeStringCodec::class) public val scalar: String,
  @field:JsonCodec(elementCodec = PlatformElementStringCodec::class)
  public val elements: List<String>,
  @field:JsonCodec(contentCodec = PlatformContentStringCodec::class)
  public val content: Optional<String>,
  @field:JsonCodec(
    keyCodec = PlatformIntKeyCodec::class,
    valueCodec = PlatformMapValueStringCodec::class,
  )
  public val entries: Map<Int, String>,
)

@JsonMixin(target = PlatformCodecSlots::class)
public abstract class PlatformCodecSlotsMixin {
  @get:JsonCodec(value = PlatformWholeStringCodec::class) public abstract val scalar: String

  @get:JsonCodec(elementCodec = PlatformElementStringCodec::class)
  public abstract val elements: List<String>

  @get:JsonCodec(contentCodec = PlatformContentStringCodec::class)
  public abstract val content: Optional<String>

  @get:JsonCodec(
    keyCodec = PlatformIntKeyCodec::class,
    valueCodec = PlatformMapValueStringCodec::class,
  )
  public abstract val entries: Map<Int, String>
}

private fun writeTagged(writer: JsonWriter, prefix: String, value: String?) {
  if (value == null) writer.writeNull() else writer.writeString(prefix + value)
}

private fun readTagged(reader: JsonReader, prefix: String): String? {
  val value = reader.readString() ?: return null
  if (!value.startsWith(prefix)) {
    throw ForyJsonException("Expected a $prefix platform string")
  }
  return value.substring(prefix.length)
}
