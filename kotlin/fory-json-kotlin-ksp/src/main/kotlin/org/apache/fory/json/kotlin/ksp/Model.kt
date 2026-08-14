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

package org.apache.fory.json.kotlin.ksp

import com.google.devtools.ksp.symbol.KSFile

internal enum class MemberKind {
  FIELD,
  GETTER,
  SETTER,
}

internal data class JvmMember(
  val kind: MemberKind,
  val ownerBinaryName: String,
  val ownerInterface: Boolean,
  val name: String,
  val descriptor: String,
  val writable: Boolean = false,
)

internal data class JsonCreator(
  val parameterNames: List<String>,
  val parameterTypes: List<JvmType>,
  val optional: BooleanArray,
  val invocationOwner: String,
  val invocationName: String,
  val invocationDescriptor: String,
  val defaultDescriptor: String?,
) {
  init {
    require(parameterNames.size == parameterTypes.size)
    require(optional.size == parameterTypes.size)
  }

  val maskCount: Int
    get() = if (defaultDescriptor == null) 0 else (parameterTypes.size + 31) ushr 5

  val factory: Boolean
    get() = invocationName != "<init>"
}

internal data class JvmAnySetter(
  val ownerBinaryName: String,
  val ownerInterface: Boolean,
  val name: String,
  val descriptor: String,
)

internal data class JvmValidator(
  val ownerBinaryName: String,
  val ownerInterface: Boolean,
  val name: String,
)

internal data class JsonModel(
  val packageName: String,
  val targetBinaryName: String,
  val targetSourceName: String,
  val companionSimpleName: String,
  val operationSimpleName: String,
  val generateCompanion: Boolean,
  val members: List<JvmMember>,
  val anySetter: JvmAnySetter?,
  val validators: List<JvmValidator>,
  val creator: JsonCreator?,
  val singleton: Boolean,
  val valueClass: ValueClassOperations?,
  val mixinBinaryName: String?,
  val originatingFiles: List<KSFile>,
  val retainedAnnotations: Set<String>,
  val retainedTypes: Set<String>,
  val mixinMembers: List<String>,
) {
  val companionBinaryName: String = qualify(packageName, companionSimpleName)
  val operationBinaryName: String = qualify(packageName, operationSimpleName)
}

internal data class ValueClassOperations(
  val layers: List<ValueClassLayer>,
  val terminalType: JvmType,
  val terminalTypeExpression: String = "org.apache.fory.reflect.TypeRef.of(java.lang.Object.class)",
)

internal data class ValueClassLayer(
  val ownerBinaryName: String,
  val carrierType: JvmType,
  val constructorName: String = "constructor-impl",
  val boxName: String = "box-impl",
  val unboxName: String = "unbox-impl",
  val occurrenceTypeExpression: String = "type",
  val underlyingTypeExpression: String =
    "org.apache.fory.reflect.TypeRef.of(java.lang.Object.class)",
)

internal fun qualify(packageName: String, simpleName: String): String =
  if (packageName.isEmpty()) simpleName else "$packageName.$simpleName"
