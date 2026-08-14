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

import java.nio.charset.StandardCharsets
import org.apache.fory.json.ForyJson
import org.apache.fory.json.kotlin.jsonTypeRef
import org.apache.fory.reflect.TypeRef

/** Java-friendly exact structural type tokens shared by every platform fixture. */
public object KotlinJsonCorpus {
  private const val RESOURCE_ROOT: String = "/org/apache/fory/integration/kotlin/json/corpus/"

  @JvmStatic public fun accountType(): TypeRef<PlatformAccount> = jsonTypeRef()

  @JvmStatic public fun envelopeType(): TypeRef<PlatformEnvelope> = jsonTypeRef()

  @JvmStatic public fun envelopeValue(): PlatformEnvelope = platformRootValue().envelope

  @JvmStatic public fun boxType(): TypeRef<PlatformBox<String>> = jsonTypeRef()

  @JvmStatic public fun unreachedBoxType(): TypeRef<PlatformBox<Int>> = jsonTypeRef()

  @JvmStatic public fun nodeType(): TypeRef<PlatformNode<String>> = jsonTypeRef()

  @JvmStatic public fun rootType(): TypeRef<PlatformRoot> = jsonTypeRef()

  @JvmStatic public fun rootValue(): PlatformRoot = platformRootValue()

  @JvmStatic public fun tokenType(): TypeRef<PlatformToken> = jsonTypeRef()

  @JvmStatic public fun builtinsType(): TypeRef<PlatformBuiltins> = jsonTypeRef()

  @JvmStatic public fun valueHolderType(): TypeRef<PlatformValueHolder> = jsonTypeRef()

  @JvmStatic public fun propertyShapeType(): TypeRef<PlatformPropertyShape> = jsonTypeRef()

  @JvmStatic public fun wrappedShapeType(): TypeRef<PlatformWrappedShape> = jsonTypeRef()

  @JvmStatic
  public fun invalidPropertyShapeType(): TypeRef<PlatformInvalidPropertyShape> = jsonTypeRef()

  @JvmStatic public fun manifestType(): TypeRef<PlatformCaseManifest> = jsonTypeRef()

  @JvmStatic public fun nullableUnitType(): TypeRef<Unit?> = jsonTypeRef()

  @JvmStatic public fun nullableNothingType(): TypeRef<Nothing?> = jsonTypeRef()

  @JvmStatic public fun caseJson(id: String): String = resourceText("cases/$id.json")

  @JvmStatic
  public fun manifest(json: ForyJson): PlatformCaseManifest =
    json.fromJson(resourceText("cases.json"), manifestType())

  private fun resourceText(path: String): String {
    val stream =
      KotlinJsonCorpus::class.java.getResourceAsStream(RESOURCE_ROOT + path)
        ?: error("Missing Kotlin JSON corpus resource: $path")
    return stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
  }
}
