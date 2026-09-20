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

import org.apache.fory.json.scala.ScalaJsonCodec

import scala.reflect.macros.blackbox

private[scala] object ScalaJsonCodecMacros {
  def derived[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[ScalaJsonCodec[T]] =
    derive[T](c)(false)

  def stringEnum[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[ScalaJsonCodec[T]] =
    derive[T](c)(true)

  private def derive[T: c.WeakTypeTag](c: blackbox.Context)(
      stringEnum: Boolean
  ): c.Expr[ScalaJsonCodec[T]] = {
    import c.universe._
    val rootType = weakTypeOf[T].dealias
    val root = rootType.typeSymbol
    if (!root.isClass || !root.asClass.isSealed || !root.asClass.isAbstract)
      c.abort(c.enclosingPosition, s"${root.fullName} must be an abstract sealed class or sealed trait")

    val cases = scala.collection.mutable.ArrayBuffer.empty[ClassSymbol]
    val visited = scala.collection.mutable.HashSet.empty[Symbol]
    def collect(owner: ClassSymbol): Unit =
      owner.knownDirectSubclasses.toList.sortBy(_.fullName).foreach { child =>
        val cls = child.asClass
        if (visited.add(cls)) {
          if (!cls.isAbstract) cases += cls
          if (cls.isSealed) collect(cls)
          else if (cls.isAbstract)
            c.abort(c.enclosingPosition, s"Sealed JSON hierarchy has an open abstract branch ${cls.fullName}")
        }
      }
    collect(root.asClass)
    if (cases.isEmpty)
      c.abort(c.enclosingPosition, s"${root.fullName} has no concrete closed cases")
    val entries = cases.map { child =>
      if (child.isModuleClass) {
        val value = c.internal.gen.mkAttributedRef(child.module)
        (q"$value.getClass", q"$value.asInstanceOf[AnyRef]")
      } else {
        if (stringEnum)
          c.abort(c.enclosingPosition, s"String enum representation requires singleton cases: ${child.fullName}")
        (q"classOf[${child.toType}]", q"null")
      }
    }
    val classes = entries.map(_._1).toList
    val singletons = entries.map(_._2).toList
    val names = cases.map(child => Literal(Constant(child.name.decodedName.toString.stripSuffix("$")))).toList
    c.Expr[ScalaJsonCodec[T]](
      q"""new _root_.org.apache.fory.json.scala.internal.DerivedScalaJsonCodec[$rootType](
        classOf[$rootType], _root_.scala.Array[_root_.java.lang.Class[_]](..$classes),
        _root_.scala.Array[_root_.java.lang.String](..$names),
        _root_.scala.Array[_root_.scala.AnyRef](..$singletons), $stringEnum)"""
    )
  }
}
