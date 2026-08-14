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

package org.apache.fory.json.kotlin

import java.io.ByteArrayInputStream
import org.apache.fory.codegen.CompileState
import org.apache.fory.json.ForyJson
import org.apache.fory.json.ForyJsonBuilder
import org.apache.fory.reflect.ReflectionUtils
import org.codehaus.janino.util.ClassFile

internal enum class KotlinJsonTestMode {
  INTERPRETED,
  SYNCHRONOUS,
  ASYNCHRONOUS,
}

internal fun forEachJsonMode(action: (ForyJson) -> Unit) {
  KotlinJsonTestMode.entries.forEach { action(newKotlinJson(it)) }
}

internal fun newKotlinJson(
  mode: KotlinJsonTestMode,
  configure: ForyJsonBuilder.() -> Unit = {},
): ForyJson {
  val builder = ForyJsonKotlin.builder().apply(configure)
  return when (mode) {
    KotlinJsonTestMode.INTERPRETED -> builder.withCodegen(false).build()
    KotlinJsonTestMode.SYNCHRONOUS -> builder.withCodegen(true).withAsyncCompilation(false).build()
    KotlinJsonTestMode.ASYNCHRONOUS -> builder.withCodegen(true).withAsyncCompilation(true).build()
  }
}

@Suppress("UNCHECKED_CAST")
internal fun generatedClassBytes(json: ForyJson, modelName: String): Map<String, ByteArray> {
  val slots = ReflectionUtils.getObjectFieldValue(json, "slots") as Array<Any>
  val state = ReflectionUtils.getObjectFieldValue(slots[0], "state")
  val resolver = ReflectionUtils.getObjectFieldValue(state, "typeResolver")
  val registry = ReflectionUtils.getObjectFieldValue(resolver, "sharedRegistry")
  val codegen = ReflectionUtils.getObjectFieldValue(registry, "codegen")
  val generator = ReflectionUtils.getObjectFieldValue(codegen, "codeGenerator")
  val states =
    ReflectionUtils.getObjectFieldValue(generator, "parallelCompileState")
      as Map<String, CompileState>
  return states.values
    .flatMap { it.result.entries }
    .filter { it.key.contains(modelName) }
    .associate { it.key to it.value }
}

internal fun generatedMethodRefs(bytes: ByteArray): List<GeneratedMethodRef> {
  val classFile = ClassFile(ByteArrayInputStream(bytes))
  return (1 until classFile.constantPoolSize).mapNotNull { index ->
    val info =
      runCatching { classFile.getConstantPoolInfo(index.toShort()) }.getOrNull()
        as? ClassFile.ConstantMethodrefInfo ?: return@mapNotNull null
    val nameAndType = info.getNameAndType(classFile)
    GeneratedMethodRef(
      info.getClassInfo(classFile).getName(classFile),
      nameAndType.getName(classFile),
      nameAndType.getDescriptor(classFile),
    )
  }
}

internal data class GeneratedMethodRef(
  val owner: String,
  val name: String,
  val descriptor: String,
)
