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

package org.apache.fory.kotlin.ksp

internal class KotlinProviderSourceWriter(
  private val packageName: String,
  private val providerName: String,
  private val entries: List<ProviderEntry>,
) {
  fun write(): String {
    val builder = StringBuilder(8192)
    if (packageName.isNotEmpty()) {
      builder.append("package ").append(packageName).append("\n\n")
    }
    builder.append("import org.apache.fory.meta.TypeDef\n")
    builder.append("import org.apache.fory.resolver.StaticGeneratedSerializerProvider\n")
    builder.append("import org.apache.fory.resolver.StaticGeneratedSerializerRegistry\n")
    builder.append("import org.apache.fory.resolver.TypeResolver\n")
    builder.append("import org.apache.fory.serializer.StaticGeneratedStructSerializer\n\n")
    builder
      .append("public class ")
      .append(providerName)
      .append(" : StaticGeneratedSerializerProvider.KotlinSymbolProcessor {\n")
    builder.append("  override fun register(registry: StaticGeneratedSerializerRegistry) {\n")
    for (entry in entries) {
      builder.append("    registry.register(\n")
      builder.append("      ").append(entry.targetClass).append("::class.java,\n")
      builder.append("      StaticGeneratedSerializerRegistry.Mode.XLANG,\n")
      builder.append("      ").append(entry.serializerClass).append("::class.java,\n")
      builder.append("      object : StaticGeneratedSerializerRegistry.RuntimeFactory {\n")
      builder.append("        override fun create(\n")
      builder.append("          typeResolver: TypeResolver,\n")
      builder.append("          type: Class<*>,\n")
      builder.append("          typeDef: TypeDef?,\n")
      builder.append("        ): StaticGeneratedStructSerializer<*> {\n")
      builder.append("          return if (typeDef == null) {\n")
      builder.append("            ").append(entry.serializerClass).append("(typeResolver, type)\n")
      builder.append("          } else {\n")
      builder
        .append("            ")
        .append(entry.serializerClass)
        .append("(typeResolver, type, typeDef)\n")
      builder.append("          }\n")
      builder.append("        }\n")
      builder.append("      },\n")
      builder.append("      object : StaticGeneratedSerializerRegistry.DescriptorFactory {\n")
      builder.append("        override fun create(): StaticGeneratedStructSerializer<*> {\n")
      builder.append("          return ").append(entry.serializerClass).append("()\n")
      builder.append("        }\n")
      builder.append("      },\n")
      builder.append("    )\n")
    }
    builder.append("  }\n")
    builder.append("}\n")
    return builder.toString()
  }
}
