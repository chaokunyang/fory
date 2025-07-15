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

package org.apache.fory.serializer.kotlin

import java.lang.reflect.Type
import kotlin.reflect.KParameter
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaType
import org.apache.fory.logging.Logger
import org.apache.fory.logging.LoggerFactory
import org.apache.fory.memory.Platform
import org.apache.fory.util.DefaultValueUtils

/**
 * Kotlin implementation of DefaultValueSupport for extracting default values from Kotlin data
 * classes.
 *
 * This class uses Kotlin native reflection to analyze data classes and extract default values from
 * their primary constructor parameters.
 */
internal class KotlinDefaultValueSupport : DefaultValueUtils.DefaultValueSupport() {
  private val LOG: Logger = LoggerFactory.getLogger(KotlinDefaultValueSupport::class.java)

  override fun hasDefaultValues(cls: Class<*>): Boolean {
    return try {
      if (!isKotlinDataClass(cls)) {
        return false
      }
      getAllDefaultValues(cls).isNotEmpty()
    } catch (e: Exception) {
      LOG.warn("Error checking default values for class ${cls.name}: ${e.message}")
      false
    }
  }

  override fun getAllDefaultValues(cls: Class<*>): Map<String, Any> {
    return try {
      if (!isKotlinDataClass(cls)) {
        return emptyMap()
      }

      val kClass = cls.kotlin
      val primaryConstructor = kClass.primaryConstructor ?: return emptyMap()
      val parameters = primaryConstructor.parameters
      val argsMap = mutableMapOf<KParameter, Any>()
      // Provide default values for all required (non-optional) parameters
      for (parameter in parameters) {
        if (!parameter.isOptional) {
          val defaultValue = getDefaultValueForType(parameter.type.javaType)
          if (defaultValue != null) {
            argsMap[parameter] = defaultValue
          } else {
            // If we can't provide a default for a required parameter, we can't get any defaults
            return emptyMap()
          }
        }
      }
      // Create a single instance
      val instance = primaryConstructor.callBy(argsMap)
      val defaultValues = mutableMapOf<String, Any>()
      // For each optional parameter, extract its value from the instance
      for (parameter in parameters) {
        if (parameter.isOptional && parameter.name != null) {
          val property = kClass.memberProperties.find { it.name == parameter.name }
          property?.let { prop ->
            @Suppress("UNCHECKED_CAST")
            val value = (prop as kotlin.reflect.KProperty1<Any, *>).get(instance as Any)
            if (value != null) {
              defaultValues[parameter.name!!] = value
            }
          }
        }
      }
      defaultValues
    } catch (e: Exception) {
      LOG.info("Error getting default values for class ${cls.name}: ${e.message}")
      emptyMap()
    }
  }

  override fun getDefaultValue(cls: Class<*>, fieldName: String): Any? {
    return try {
      if (!isKotlinDataClass(cls)) {
        return null
      }
      getAllDefaultValues(cls)[fieldName]
    } catch (e: Exception) {
      LOG.info(
        "Error getting default value for field $fieldName in class ${cls.name}: ${e.message}"
      )
      null
    }
  }

  private fun isKotlinDataClass(cls: Class<*>): Boolean {
    return try {
      cls.kotlin.isData
    } catch (e: Exception) {
      LOG.info("Error checking if class ${cls.name} is a Kotlin data class: ${e.message}")
      false
    }
  }

  private fun getDefaultValueForType(type: Type): Any? {
    val clazz =
      when (type) {
        is Class<*> -> type
        is java.lang.reflect.ParameterizedType -> type.rawType as? Class<*>
        else -> null
      } ?: return null

    return when (clazz) {
      Int::class.java,
      Int::class.javaPrimitiveType -> 0
      Long::class.java,
      Long::class.javaPrimitiveType -> 0L
      Double::class.java,
      Double::class.javaPrimitiveType -> 0.0
      Float::class.java,
      Float::class.javaPrimitiveType -> 0.0f
      Boolean::class.java,
      Boolean::class.javaPrimitiveType -> false
      Byte::class.java,
      Byte::class.javaPrimitiveType -> 0.toByte()
      Short::class.java,
      Short::class.javaPrimitiveType -> 0.toShort()
      Char::class.java,
      Char::class.javaPrimitiveType -> '\u0000'
      String::class.java -> ""
      else -> Platform.newInstance(clazz)
    }
  }
}
