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

import org.apache.fory.json.annotation.JsonIgnore
import org.apache.fory.json.annotation.JsonType

@JsonType
public data class PlatformAccount(
  public val id: Int,
  public val name: String,
  public val label: String? = "corpus-default",
)

@JsonType public data class PlatformBox<T>(public val value: T)

@JsonType public class PlatformOrdinary(public val id: Int, public val name: String)

@JsonType
public data class PlatformEnvelope(
  public val account: PlatformAccount,
  public val names: List<String>,
  public val boxedNames: List<PlatformBox<String>>,
  public val unsigned: UInt,
)

@JsonType
public data class PlatformNode<T>(
  public val value: T,
  public val children: List<PlatformNode<T>> = emptyList(),
)

@JsonType
public data class PlatformNulls(
  public val required: String,
  public val nullable: String?,
  public val count: Int,
  public val nullableCount: Int?,
  public val defaultNullable: String? = "nullable-default",
  public val defaultNonNull: String = "non-null-default",
)

@JsonType
public data class PlatformUnitHolder(
  public val required: Unit,
  public val nullable: Unit?,
  public val nothing: Nothing?,
)

@JsonType
public object PlatformMarker {
  @get:JsonIgnore @set:JsonIgnore public var ignoredState: Int = 1
}

@JsonType public data class PlatformKotlinProfile(public val label: String)

public object PlatformStatefulMarker {
  public var state: Int = 1
}

public class PlatformComputed(public val id: Int) {
  public val computed: Int
    get() = id * 2
}

public class PlatformDelegated(public val id: Int) {
  public val delegated: String by lazy { id.toString() }
}

public class PlatformInnerOwner {
  public inner class InnerModel(public val id: Int)
}
