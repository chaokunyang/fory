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

import kotlin.jvm.JvmInline
import org.apache.fory.json.annotation.JsonSubTypes
import org.apache.fory.json.annotation.JsonType

@JsonType
@JvmInline
public value class PlatformPositiveId(public val value: Long) {
  init {
    require(value >= 0) { "id must be non-negative" }
  }
}

@JsonType @JvmInline public value class PlatformNullableText(public val value: String?)

@JsonType @JvmInline public value class PlatformGenericKey<T>(public val value: T)

@JsonType
public data class PlatformValueHolder(
  public val id: PlatformPositiveId,
  public val nullableId: PlatformPositiveId?,
  public val nullableText: PlatformNullableText,
  public val keyed: Map<PlatformGenericKey<UInt>, String>,
  public val defaultId: PlatformPositiveId = PlatformPositiveId(7),
)

@JsonType
@JsonSubTypes(
  value =
    [
      JsonSubTypes.Type(value = PlatformCircle::class, name = "circle"),
      JsonSubTypes.Type(value = PlatformShapeMarker::class, name = "marker"),
    ],
  property = "kind",
)
public sealed interface PlatformPropertyShape

@JsonType public data class PlatformCircle(public val radius: Int) : PlatformPropertyShape

@JsonType public data object PlatformShapeMarker : PlatformPropertyShape

@JsonType public data class PlatformUnlistedShape(public val value: Int) : PlatformPropertyShape

@JsonType
@JsonSubTypes(
  value =
    [
      JsonSubTypes.Type(value = PlatformWrappedData::class, name = "data"),
      JsonSubTypes.Type(value = PlatformWrappedNumber::class, name = "number"),
      JsonSubTypes.Type(value = PlatformWrappedMarker::class, name = "marker"),
    ],
  inclusion = JsonSubTypes.Inclusion.WRAPPER_OBJECT,
)
public sealed interface PlatformWrappedShape

@JsonType public data class PlatformWrappedData(public val value: String) : PlatformWrappedShape

@JsonType
@JvmInline
public value class PlatformWrappedNumber(public val value: Int) : PlatformWrappedShape

@JsonType public data object PlatformWrappedMarker : PlatformWrappedShape

@JsonType
@JsonSubTypes(
  value = [JsonSubTypes.Type(value = PlatformPropertyNumber::class, name = "number")],
  property = "kind",
)
public sealed interface PlatformInvalidPropertyShape

@JsonType
@JvmInline
public value class PlatformPropertyNumber(public val value: Int) : PlatformInvalidPropertyShape
