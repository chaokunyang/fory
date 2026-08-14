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
import org.apache.fory.json.annotation.JsonProperty
import org.apache.fory.json.annotation.JsonType

@JsonType
public data class PlatformAnnotated(
  @field:JsonProperty("field_name") public val fieldName: String,
  @get:JsonProperty("getter_name") public val getterName: String,
  @param:JsonProperty("parameter_name") public val parameterName: String,
  @set:JsonProperty("setter_name") public var setterName: String,
  @JsonProperty("bare_name") public val bareName: String,
)

@JsonType
public data class PlatformRoot(
  public val account: PlatformAccount,
  public val ordinary: PlatformOrdinary,
  public val envelope: PlatformEnvelope,
  public val node: PlatformNode<String>,
  public val builtins: PlatformBuiltins,
  public val value: PlatformValueHolder,
  public val unitHolder: PlatformUnitHolder,
  public val propertyShape: PlatformPropertyShape,
  public val wrappedShape: PlatformWrappedShape,
  public val annotated: PlatformAnnotated,
  public val codecSlots: PlatformCodecSlots,
  public val nulls: PlatformNulls,
  public val token: PlatformToken,
)

@JsonType
public data class PlatformCase(
  public val id: String,
  public val type: String,
  public val resource: String,
  public val outcome: String,
  public val platforms: List<String>,
)

@JsonType
public data class PlatformCaseManifest(
  public val schemaVersion: Int,
  public val cases: List<PlatformCase>,
)

internal fun platformRootValue(): PlatformRoot {
  val account = PlatformAccount(25, "platform", null)
  return PlatformRoot(
    account = account,
    ordinary = PlatformOrdinary(11, "ordinary"),
    envelope =
      PlatformEnvelope(
        account,
        listOf("native", "android"),
        listOf(PlatformBox("child")),
        UInt.MAX_VALUE,
      ),
    node = PlatformNode("root", listOf(PlatformNode("leaf"))),
    builtins = platformBuiltinsValue(),
    value =
      PlatformValueHolder(
        id = PlatformPositiveId(19),
        nullableId = null,
        nullableText = PlatformNullableText(null),
        keyed = linkedMapOf(PlatformGenericKey(UInt.MAX_VALUE) to "maximum"),
      ),
    unitHolder = PlatformUnitHolder(Unit, null, null),
    propertyShape = PlatformCircle(3),
    wrappedShape = PlatformWrappedNumber(9),
    annotated = PlatformAnnotated("field", "getter", "parameter", "setter", "bare"),
    codecSlots =
      PlatformCodecSlots(
        scalar = "scalar",
        elements = listOf("first", "second"),
        content = Optional.of("optional"),
        entries = linkedMapOf(7 to "seven"),
      ),
    nulls = PlatformNulls("required", null, 1, null),
    token = PlatformToken("module-token"),
  )
}
