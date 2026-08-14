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

package org.apache.fory.json.scala.internal

import java.lang.reflect.{Constructor, Method, Type}

import org.apache.fory.json.ForyJsonException
import org.apache.fory.json.codec.{JsonValueCodec, MapKeyCodec}
import org.apache.fory.json.scala.JsonEnumeration
import org.apache.fory.reflect.TypeRef

private[scala] object ScalaEnumerationTypes {
  def propertyTypes(
      typeClass: Class[?],
      constructor: Constructor[?],
      names: Array[String],
      getters: Array[Method],
      setters: Array[Method]
  ): Array[Type] = {
    val constructorParameters = constructor.getParameters
    val constructorTypes = constructor.getGenericParameterTypes
    val types = new Array[Type](names.length)
    var index = 0
    while index < names.length do
      val getter = getters(index)
      val setter = setters(index)
      val parameter = if index < constructorParameters.length then constructorParameters(index) else null
      if annotation(parameter) || annotation(getter) || annotation(setter) then
        throw new ForyJsonException(
          s"@JsonEnumeration supports only Scala 2 Enumeration: ${typeClass.getName}.${names(index)}"
        )
      val fields = typeClass.getDeclaredFields
      var fieldIndex = 0
      while fieldIndex < fields.length do
        if fields(fieldIndex).getName == names(index) && annotation(fields(fieldIndex)) then
          throw new ForyJsonException(
            s"@JsonEnumeration supports only Scala 2 Enumeration: ${typeClass.getName}.${names(index)}"
          )
        fieldIndex += 1
      types(index) =
        if index < constructorTypes.length then constructorTypes(index)
        else if getter != null then getter.getGenericReturnType
        else setter.getGenericParameterTypes.apply(0)
      index += 1
    types
  }

  def createCodec(typeRef: TypeRef[?]): JsonValueCodec[?] = null

  def mapKeyCodec(keyType: Type): MapKeyCodec = null

  private def annotation(element: java.lang.reflect.AnnotatedElement): Boolean =
    element != null && element.getDeclaredAnnotation(classOf[JsonEnumeration]) != null
}
