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

import org.apache.fory.json.ForyJsonException
import org.apache.fory.json.codec.JsonValueCodec
import org.apache.fory.json.meta.JsonAsciiToken
import org.apache.fory.json.scala.ScalaJsonCodec
import org.apache.fory.json.writer.Utf8JsonWriter

import scala.quoted.*

private[scala] object ScalaJsonCodecMacros {
  def derive[T: Type](stringEnum: Boolean)(using quotes: Quotes): Expr[ScalaJsonCodec[T]] = {
    import quotes.reflect.*

    val root = TypeRepr.of[T].dealias.typeSymbol
    val enumRoot = root.flags.is(Flags.Enum)
    if (!enumRoot && !root.flags.is(Flags.Sealed))
      report.errorAndAbort(s"${root.fullName} is not a Scala 3 enum or sealed type")
    if (!enumRoot && !root.flags.is(Flags.Abstract) && !root.flags.is(Flags.Trait))
      report.errorAndAbort(s"${root.fullName} must be an abstract sealed class or sealed trait")

    // Compiler symbols are trusted static schema metadata. JSON input never participates in this
    // traversal and later selects only a logical name from the validated generated table.
    val cases =
      if (enumRoot) root.children.filter(_.flags.is(Flags.Case))
      else {
        val result = scala.collection.mutable.ArrayBuffer.empty[Symbol]
        val visited = scala.collection.mutable.HashSet.empty[Symbol]
        def collect(owner: Symbol): Unit =
          owner.children.foreach { child =>
            if (visited.add(child)) {
              val concrete =
                !child.flags.is(Flags.Abstract) && !child.flags.is(Flags.Trait)
              if (concrete) result += child
              if (child.flags.is(Flags.Sealed)) collect(child)
              else if (!concrete)
                report.errorAndAbort(
                  s"Sealed JSON hierarchy has an open abstract branch ${child.fullName}"
                )
            }
          }
        collect(root)
        result.toList
      }
    if (cases.isEmpty)
      report.errorAndAbort(s"${root.fullName} has no concrete closed cases")

    val rootClass =
      Literal(ClassOfConstant(TypeRepr.of[T].dealias)).asExprOf[Class[?]]
    val caseExpressions = cases.map { child =>
      if (child.flags.is(Flags.Module) || child.primaryConstructor == Symbol.noSymbol) {
        val value =
          if (enumRoot) Select.unique(Ref(root.companionModule), child.name).asExpr
          else {
            val module = if (child.isTerm) child else child.companionModule
            if (module == Symbol.noSymbol)
              report.errorAndAbort(s"Cannot resolve Scala singleton ${child.fullName}")
            Ref(module).asExpr
          }
        val singleton = '{ $value.asInstanceOf[AnyRef] }
        ('{ $singleton.getClass }, singleton)
      } else {
        if (stringEnum)
          report.errorAndAbort(s"String enum representation requires singleton cases: ${child.fullName}")
        (Literal(ClassOfConstant(child.typeRef)).asExprOf[Class[?]], '{ null })
      }
    }
    val classExpressions = caseExpressions.map(_._1)
    val singletonExpressions = caseExpressions.map(_._2)
    val nameExpressions = cases.map(child => Expr(child.name.stripSuffix("$")))

    // Bound the generated identity chain; large schemas retain the table-based codec.
    if (stringEnum && cases.size <= 8) {
      // Use the splice's Quotes so method parameters remain in their defining scope on Scala 3.9+.
      def write(writer: Expr[Utf8JsonWriter], value: Expr[Object])(using Quotes): Expr[Unit] = {
        val unknown = '{ throw new ForyJsonException("Unknown Scala enum value") }
        cases.zip(singletonExpressions).foldRight[Expr[Unit]](unknown) {
          case ((child, singleton), next) =>
            val name = child.name.stripSuffix("$")
            val token = "\"" + name + "\""
            val output =
              if (
                JsonAsciiToken.isLongPackable(token) &&
                name.forall(ch => ch >= ' ' && ch < 0x7f && ch != '"' && ch != '\\')
              ) {
                '{ $writer.writeRawValue(
                  ${ Expr(JsonAsciiToken.prefix(token)) },
                  ${ Expr(JsonAsciiToken.suffixLong(token)) },
                  ${ Expr(token.length) }) }
              } else '{ $writer.writeString(${ Expr(name) }) }
            '{ if ($value eq $singleton) $output else $next }
        }
      }
      return '{
        new DerivedScalaJsonCodec[T](
          $rootClass.asInstanceOf[Class[T]],
          Array[Class[_]](${ Varargs(classExpressions) }*),
          Array[String](${ Varargs(nameExpressions) }*),
          Array[AnyRef](${ Varargs(singletonExpressions) }*),
          true
        ) {
          override protected def stringEnumCodec(
              typeClass: Class[_], values: Array[Object], labels: Array[String]
          ): JsonValueCodec[_] = new ScalaEnumCodec(typeClass, values, labels) {
            override def writeUtf8(writer: Utf8JsonWriter, value: Object): Unit = {
              if (value == null) writer.writeNull()
              else ${ write('writer, 'value) }
            }
          }
        }
      }
    }
    '{
      new DerivedScalaJsonCodec[T](
        $rootClass.asInstanceOf[Class[T]],
        Array[Class[_]](${ Varargs(classExpressions) }*),
        Array[String](${ Varargs(nameExpressions) }*),
        Array[AnyRef](${ Varargs(singletonExpressions) }*),
        ${ Expr(stringEnum) }
      )
    }
  }
}
