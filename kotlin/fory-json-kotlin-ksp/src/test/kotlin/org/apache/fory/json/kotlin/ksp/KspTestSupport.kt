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

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSNode
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.lang.reflect.Proxy

internal class RecordingCodeGenerator : CodeGenerator {
  val outputs = linkedMapOf<String, ByteArrayOutputStream>()
  val dependenciesByPath = linkedMapOf<String, Dependencies>()
  val dependencies: Collection<Dependencies>
    get() = dependenciesByPath.values

  override fun createNewFile(
    dependencies: Dependencies,
    packageName: String,
    fileName: String,
    extensionName: String,
  ): OutputStream {
    val directory = packageName.replace('.', '/')
    val path =
      if (directory.isEmpty()) "$fileName.$extensionName" else "$directory/$fileName.$extensionName"
    return output(path, dependencies)
  }

  override fun createNewFileByPath(
    dependencies: Dependencies,
    path: String,
    extensionName: String,
  ): OutputStream =
    output(path + if (extensionName.isEmpty()) "" else ".$extensionName", dependencies)

  override fun associate(
    sources: List<KSFile>,
    packageName: String,
    fileName: String,
    extensionName: String,
  ) {}

  override fun associateByPath(
    sources: List<KSFile>,
    path: String,
    extensionName: String,
  ) {}

  override fun associateWithClasses(
    classes: List<KSClassDeclaration>,
    packageName: String,
    fileName: String,
    extensionName: String,
  ) {}

  override val generatedFile: Collection<File>
    get() = emptyList()

  fun text(path: String): String = bytes(path).decodeToString()

  fun bytes(path: String): ByteArray = outputs.getValue(path).toByteArray()

  private fun output(path: String, dependencies: Dependencies): ByteArrayOutputStream {
    check(path !in outputs) { "Duplicate generated output $path" }
    dependenciesByPath[path] = dependencies
    return ByteArrayOutputStream().also { outputs[path] = it }
  }
}

internal object SilentLogger : KSPLogger {
  override fun logging(message: String, symbol: KSNode?) {}

  override fun info(message: String, symbol: KSNode?) {}

  override fun warn(message: String, symbol: KSNode?) {}

  override fun error(message: String, symbol: KSNode?) {}

  override fun exception(e: Throwable) {
    throw e
  }
}

internal fun sourceFile(name: String): KSFile =
  Proxy.newProxyInstance(KSFile::class.java.classLoader, arrayOf(KSFile::class.java)) {
    proxy,
    method,
    args ->
    when (method.name) {
      "equals" -> proxy === args?.singleOrNull()
      "hashCode" -> System.identityHashCode(proxy)
      "toString" -> "KSFile($name)"
      "getFileName",
      "getFilePath" -> name
      else -> null
    }
  } as KSFile
