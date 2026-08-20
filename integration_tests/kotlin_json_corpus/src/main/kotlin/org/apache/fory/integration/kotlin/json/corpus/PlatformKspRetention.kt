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

import java.util.ArrayList
import java.util.LinkedHashMap
import org.apache.fory.json.annotation.JsonBase64
import org.apache.fory.json.annotation.JsonCodec
import org.apache.fory.json.annotation.JsonCreator
import org.apache.fory.json.annotation.JsonMixin
import org.apache.fory.json.annotation.JsonMixinRemove
import org.apache.fory.json.annotation.JsonProperty
import org.apache.fory.json.annotation.JsonSubTypes
import org.apache.fory.json.annotation.JsonType
import org.apache.fory.json.annotation.JsonValue
import org.apache.fory.json.codec.AbstractJsonValueCodec
import org.apache.fory.json.reader.JsonReader
import org.apache.fory.json.writer.JsonWriter

public class PlatformDirectEndpointCodec : AbstractJsonValueCodec<PlatformDirectEndpoint>() {
  override fun write(writer: JsonWriter, value: PlatformDirectEndpoint?): Unit =
    writer.writeString(value?.value)

  override fun read(reader: JsonReader): PlatformDirectEndpoint? =
    reader.readString()?.let(::PlatformDirectEndpoint)
}

public class PlatformInheritedEndpointCodec : AbstractJsonValueCodec<PlatformInheritedEndpoint>() {
  override fun write(writer: JsonWriter, value: PlatformInheritedEndpoint?): Unit =
    writer.writeString(value?.value)

  override fun read(reader: JsonReader): PlatformInheritedEndpoint? =
    reader.readString()?.let(::PlatformInheritedEndpoint)
}

@JsonCodec(PlatformDirectEndpointCodec::class)
public data class PlatformDirectEndpoint(public val value: String)

@JsonCodec(PlatformInheritedEndpointCodec::class) public interface PlatformEndpointContract

public data class PlatformInheritedEndpoint(public val value: String) : PlatformEndpointContract

public class PlatformEndpointList : ArrayList<PlatformDirectEndpoint>()

public class PlatformEndpointMap : LinkedHashMap<String, PlatformInheritedEndpoint>()

@JsonType
public data class PlatformEndpointOwner(
  public val direct: PlatformDirectEndpoint,
  public val inherited: PlatformInheritedEndpoint,
  public val list: PlatformEndpointList,
  public val map: PlatformEndpointMap,
)

@JsonType
public class PlatformMethodEndpointOwner public constructor() {
  @JsonValue public fun endpoint(): PlatformDirectEndpoint = PlatformDirectEndpoint("method")
}

@JsonType public data class PlatformBase64Owner(@field:JsonBase64 public val bytes: ByteArray)

@JsonType
@JsonSubTypes(
  value =
    [
      JsonSubTypes.Type(
        className = "org.apache.fory.integration.kotlin.json.corpus.PlatformNamedSubtype",
        name = "named",
      )
    ],
  property = "kind",
)
public abstract class PlatformNamedSubtypeBase

public class PlatformNamedSubtype : PlatformNamedSubtypeBase()

public class PlatformOldTypeCodec : AbstractJsonValueCodec<PlatformMixinRetentionTarget>() {
  override fun write(writer: JsonWriter, value: PlatformMixinRetentionTarget?): Unit =
    writer.writeNull()

  override fun read(reader: JsonReader): PlatformMixinRetentionTarget? = null
}

public class PlatformReplacementTypeCodec : AbstractJsonValueCodec<PlatformMixinRetentionTarget>() {
  override fun write(writer: JsonWriter, value: PlatformMixinRetentionTarget?): Unit =
    writer.writeNull()

  override fun read(reader: JsonReader): PlatformMixinRetentionTarget? = null
}

public class PlatformUnrelatedEndpointCodec : AbstractJsonValueCodec<PlatformUnrelatedEndpoint>() {
  override fun write(writer: JsonWriter, value: PlatformUnrelatedEndpoint?): Unit =
    writer.writeString(value?.value)

  override fun read(reader: JsonReader): PlatformUnrelatedEndpoint? =
    reader.readString()?.let(::PlatformUnrelatedEndpoint)
}

@JsonCodec(PlatformUnrelatedEndpointCodec::class)
public data class PlatformUnrelatedEndpoint(public val value: String)

@JsonCodec(PlatformOldTypeCodec::class)
@JsonSubTypes(
  value = [JsonSubTypes.Type(value = PlatformRemovedSubtype::class, name = "removed")],
  property = "kind",
)
public abstract class PlatformMixinRetentionTarget {
  public abstract fun unrelated(): PlatformUnrelatedEndpoint
}

public class PlatformRemovedSubtype : PlatformMixinRetentionTarget() {
  override fun unrelated(): PlatformUnrelatedEndpoint = PlatformUnrelatedEndpoint("removed")
}

@JsonMixin(target = PlatformMixinRetentionTarget::class)
@JsonMixinRemove(JsonSubTypes::class)
@JsonCodec(PlatformReplacementTypeCodec::class)
public abstract class PlatformMixinRetention {
  @Deprecated("Not a JSON mapping annotation")
  public abstract fun unrelated(): PlatformUnrelatedEndpoint
}

@JsonType
public class PlatformFactoryModel private constructor(public val value: String) {
  public companion object {
    @JvmStatic
    @JsonCreator
    public fun create(@JsonProperty("value") value: PlatformDirectEndpoint): PlatformFactoryModel =
      PlatformFactoryModel(value.value)
  }
}
