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

package org.apache.fory.scala.internal

import org.apache.fory.annotation.{ForyCase, ForyField, ForyStruct, ForyUnion}
import org.apache.fory.meta.{TypeDef => ForyTypeDef, TypeExtMeta}
import org.apache.fory.resolver.TypeResolver
import org.apache.fory.scala.ForyScalaEnum
import org.apache.fory.scala.ForySerializer
import org.apache.fory.serializer.{
  FieldGroups,
  Serializer,
  StaticGeneratedStructSerializer,
  StaticGeneratedStructSerializerFactory,
  UnionSerializer
}
import org.apache.fory.`type`.{Descriptor, Types}

import java.lang.reflect.Modifier
import scala.quoted.*

object ForySerializerMacros {
  def derive[T: Type](using q: Quotes): Expr[ForySerializer[T]] = {
    import q.reflect.*
    val symbol = TypeRepr.of[T].typeSymbol
    if hasAnnotation[ForyUnion](symbol) then deriveUnion[T](symbol)
    else deriveStruct[T](symbol)
  }

  private def deriveStruct[T: Type](using q: Quotes)(
      owner: q.reflect.Symbol): Expr[ForySerializer[T]] = {
    import q.reflect.*

    final case class FieldMeta(
        symbol: Symbol,
        name: String,
        index: Int,
        fieldId: Int,
        sourceType: TypeRepr,
        wireType: TypeRepr,
        option: Boolean,
        nullable: Boolean,
        trackingRef: Boolean,
        constructorOwned: Boolean)

    if !hasAnnotation[ForyStruct](owner) then {
      report.errorAndAbort(
        s"${owner.fullName} must be annotated with @ForyStruct to derive ForySerializer")
    }
    val ownerClassName = owner.fullName.replace("$.", "$")

    def optionElement(tpe: TypeRepr): Option[TypeRepr] = {
      peelAnnotations(tpe)._1.dealias match {
        case AppliedType(base, List(arg)) if base.typeSymbol.fullName == "scala.Option" =>
          Some(arg)
        case _ => None
      }
    }

    def peelAnnotations(tpe: TypeRepr): (TypeRepr, List[Term]) = {
      tpe match {
        case AnnotatedType(underlying, annotation) =>
          val (base, annotations) = peelAnnotations(underlying)
          (base, annotation :: annotations)
        case other =>
          other.dealias match {
            case AnnotatedType(underlying, annotation) =>
              val (base, annotations) = peelAnnotations(underlying)
              (base, annotation :: annotations)
            case dealiased => (dealiased, Nil)
          }
      }
    }

    def boxedIfPrimitive(tpe: TypeRepr): TypeRepr = {
      val (base, annotations) = peelAnnotations(tpe)
      val boxed =
        if base =:= TypeRepr.of[Boolean] then TypeRepr.of[java.lang.Boolean]
        else if base =:= TypeRepr.of[Byte] then TypeRepr.of[java.lang.Byte]
        else if base =:= TypeRepr.of[Short] then TypeRepr.of[java.lang.Short]
        else if base =:= TypeRepr.of[Int] then TypeRepr.of[java.lang.Integer]
        else if base =:= TypeRepr.of[Long] then TypeRepr.of[java.lang.Long]
        else if base =:= TypeRepr.of[Float] then TypeRepr.of[java.lang.Float]
        else if base =:= TypeRepr.of[Double] then TypeRepr.of[java.lang.Double]
        else base
      annotations.foldRight(boxed)((annotation, current) => AnnotatedType(current, annotation))
    }

    def classFor(tpe: TypeRepr): Expr[Class[?]] = {
      val normalized = peelAnnotations(tpe.widen)._1.dealias
      val fullName = normalized.typeSymbol.fullName
      if normalized =:= TypeRepr.of[Boolean] then '{ java.lang.Boolean.TYPE }
      else if normalized =:= TypeRepr.of[Byte] then '{ java.lang.Byte.TYPE }
      else if normalized =:= TypeRepr.of[Short] then '{ java.lang.Short.TYPE }
      else if normalized =:= TypeRepr.of[Int] then '{ java.lang.Integer.TYPE }
      else if normalized =:= TypeRepr.of[Long] then '{ java.lang.Long.TYPE }
      else if normalized =:= TypeRepr.of[Float] then '{ java.lang.Float.TYPE }
      else if normalized =:= TypeRepr.of[Double] then '{ java.lang.Double.TYPE }
      else if normalized =:= TypeRepr.of[Char] then '{ java.lang.Character.TYPE }
      else if normalized =:= TypeRepr.of[String] ||
          normalized.typeSymbol == TypeRepr.of[String].typeSymbol ||
          fullName == "scala.Predef.String" ||
          fullName == "scala.Predef$.String" ||
          fullName.endsWith("Predef.String") ||
          fullName.endsWith("Predef$.String")
      then '{ classOf[String] }
      else if fullName == "scala.Array" then {
        '{ Class.forName(${ Expr(arrayClassName(normalized)) }) }
      } else '{ Class.forName(${ Expr(fullName.replace("$.", "$")) }) }
    }

    def arrayClassName(tpe: TypeRepr): String = {
      tpe.dealias match {
        case AppliedType(arrayType, List(componentType))
            if arrayType.typeSymbol.fullName == "scala.Array" =>
          "[" + arrayComponentDescriptor(componentType)
        case _ =>
          report.errorAndAbort(s"Expected Scala Array type, got ${tpe.show}")
      }
    }

    def arrayComponentDescriptor(tpe: TypeRepr): String = {
      val normalized = peelAnnotations(tpe.widen)._1.dealias
      if normalized =:= TypeRepr.of[Boolean] then "Z"
      else if normalized =:= TypeRepr.of[Byte] then "B"
      else if normalized =:= TypeRepr.of[Short] then "S"
      else if normalized =:= TypeRepr.of[Int] then "I"
      else if normalized =:= TypeRepr.of[Long] then "J"
      else if normalized =:= TypeRepr.of[Float] then "F"
      else if normalized =:= TypeRepr.of[Double] then "D"
      else if normalized =:= TypeRepr.of[Char] then "C"
      else if normalized.typeSymbol.fullName == "scala.Array" then arrayClassName(normalized)
      else "L" + normalized.typeSymbol.fullName.replace("$.", "$") + ";"
    }

    val constructorFields = {
      val params = owner.primaryConstructor.paramSymss.flatten.filter(_.isValDef)
      if params.nonEmpty then params else owner.caseFields
    }
    val constructorFieldSet = constructorFields.toSet
    val bodyFields = {
      val candidates = owner.fieldMembers.filter { field =>
        !constructorFieldSet.contains(field) &&
        !field.flags.is(Flags.Private) &&
        !field.flags.is(Flags.Synthetic) &&
        !field.name.contains("$")
      }
      val selected =
        if constructorFields.isEmpty then candidates
        else candidates.filter(field => annotationIntArg[ForyField](field, "id").nonEmpty)
      selected.foreach { field =>
        if !field.flags.is(Flags.Mutable) then {
          report.errorAndAbort(
            s"${owner.fullName}.${field.name} is a post-construction field and must be a mutable var")
        }
      }
      selected
    }
    val serializableFields = constructorFields ++ bodyFields

    def declaredType(symbol: Symbol): TypeRepr = {
      symbol.tree match {
        case ValDef(_, tpt, _) => tpt.tpe
        case _ => symbol.termRef.widen
      }
    }

    val fields = serializableFields.zipWithIndex.map { (field, index) =>
      val sourceType = declaredType(field)
      val (wireType, option, nullable) = optionElement(sourceType) match {
        case Some(inner) => (boxedIfPrimitive(inner), true, true)
        case None => (sourceType, false, false)
      }
      FieldMeta(
        field,
        field.name,
        index,
        annotationIntArg[ForyField](field, "id").getOrElse(-1),
        sourceType,
        wireType,
        option,
        nullable,
        hasRef(field) || topLevelTypeHasRef(sourceType),
        constructorFieldSet.contains(field))
    }

    def generatedType(tpe: TypeRepr): Expr[Descriptor.GeneratedType] = {
      val (outer, outerAnnotations) = peelAnnotations(tpe)
      val option = optionElement(outer)
      val fieldSource = option.map(boxedIfPrimitive).getOrElse(outer)
      val (base, baseAnnotations) = peelAnnotations(fieldSource)
      val annotations = outerAnnotations ++ baseAnnotations
      val argumentSource = fieldSource
      def appliedType(tpe: TypeRepr): Option[(TypeRepr, List[TypeRepr])] = {
        val directArgs = tpe.typeArgs
        if directArgs.nonEmpty then {
          tpe match {
            case AppliedType(typeConstructor, _) => Some((typeConstructor, directArgs))
            case other =>
              other.dealias match {
                case AppliedType(typeConstructor, _) => Some((typeConstructor, directArgs))
                case _ => Some((tpe, directArgs))
              }
          }
        } else {
          tpe match {
            case AppliedType(typeConstructor, typeArgs) => Some((typeConstructor, typeArgs))
            case other =>
              other.dealias match {
                case AppliedType(typeConstructor, typeArgs) => Some((typeConstructor, typeArgs))
                case _ => None
              }
          }
        }
      }
      val component = appliedType(argumentSource) match {
        case Some((arrayType, List(componentType)))
            if arrayType.typeSymbol.fullName == "scala.Array" =>
          Some(generatedType(componentType))
        case _ => None
      }
      val args = appliedType(argumentSource) match {
        case Some((arrayType, List(_))) if arrayType.typeSymbol.fullName == "scala.Array" =>
          Nil
        case Some((_, typeArgs)) => typeArgs
        case _ => Nil
      }
      val argExprs = args.map(generatedType)
      val argList: Expr[java.util.List[Descriptor.GeneratedType]] =
        '{
          import scala.jdk.CollectionConverters.*
          java.util.Collections.unmodifiableList(${ Expr.ofList(argExprs) }.asJava)
        }
      val componentExpr: Expr[Descriptor.GeneratedType] =
        component.getOrElse('{ null.asInstanceOf[Descriptor.GeneratedType] })
      val typeId =
        annotations.flatMap(typeIdForAnnotation).headOption
          .orElse {
            if hasAnnotation[ForyUnion](base.typeSymbol) then Some(Types.UNION) else None
          }
          .orElse {
            if isScalaEnumType(base) then Some(Types.ENUM) else None
          }
          .getOrElse(Types.UNKNOWN)
      val rawClass = classFor(base)
      val typeExtMeta = generatedTypeExtMeta(
        typeId,
        nullable = option.nonEmpty,
        trackingRef = annotations.exists(isRefAnnotation),
        rawClass = Some(rawClass))
      '{ Descriptor.generatedType($rawClass, $typeExtMeta, $argList, $componentExpr) }
    }

    def descriptor(field: FieldMeta): Expr[Descriptor] = {
      '{
        new Descriptor(
          ${ generatedType(field.sourceType) },
          ${ Expr(field.sourceType.show) },
          ${ Expr(field.name) },
          ${ Expr(Modifier.PRIVATE | Modifier.FINAL) },
          ${ Expr(ownerClassName) },
          true,
          ${ Expr(field.fieldId) },
          ${ Expr(field.nullable) },
          ${ Expr(field.trackingRef) },
          ForyField.Dynamic.AUTO,
          false
        )
      }
    }

    val descriptorsExpr: Expr[java.util.List[Descriptor]] = {
      val exprs = fields.map(descriptor)
      '{
        import scala.jdk.CollectionConverters.*
        java.util.Collections.unmodifiableList(${ Expr.ofList(exprs) }.asJava)
      }
    }

    def selectValue(valueExpr: Expr[T], field: FieldMeta): Expr[Any] = {
      Select.unique(valueExpr.asTerm, field.name).asExpr
    }

    def writeDispatch(
        valueExpr: Expr[T],
        fieldIdExpr: Expr[Int],
        fieldInfoExpr: Expr[FieldGroups.SerializationFieldInfo],
        writeContextExpr: Expr[org.apache.fory.context.WriteContext],
        resolverExpr: Expr[TypeResolver]): Expr[Unit] = {
      fields.foldRight(
        '{
          throw new IllegalStateException("Unknown generated Scala field id " + $fieldIdExpr)
        }: Expr[Unit]) { (field, next) =>
        val fieldValue = selectValue(valueExpr, field)
        val wireValue =
          if field.option then '{ $fieldValue.asInstanceOf[Option[Any]].orNull }
          else fieldValue
        '{
          if $fieldIdExpr == ${ Expr(field.index) } then {
            StaticGeneratedStructSerializer.writeFieldValue(
              $resolverExpr,
              $writeContextExpr,
              $fieldInfoExpr,
              $wireValue)
          } else {
            $next
          }
        }
      }
    }

    def decodeValue(raw: Expr[Any], field: FieldMeta): Expr[Any] = {
      if field.option then {
        field.sourceType.asType match {
          case '[a] => '{ Option($raw).asInstanceOf[a] }
        }
      } else {
        field.sourceType.asType match {
          case '[a] => '{ $raw.asInstanceOf[a] }
        }
      }
    }

    def valueArg(valuesExpr: Expr[Array[Any]], field: FieldMeta): Expr[Any] =
      decodeValue('{ $valuesExpr(${ Expr(field.index) }) }, field)

    def assignRawValue(objExpr: Expr[T], field: FieldMeta, raw: Expr[Any]): Expr[Unit] =
      Assign(Select.unique(objExpr.asTerm, field.name), decodeValue(raw, field).asTerm)
        .asExprOf[Unit]

    def assignValueById(objExpr: Expr[T], fieldIdExpr: Expr[Int], raw: Expr[Any]): Expr[Unit] = {
      fields.foldRight(
        '{
          throw new IllegalStateException("Unknown generated Scala field id " + $fieldIdExpr)
        }: Expr[Unit]) { (field, next) =>
        '{
          if $fieldIdExpr == ${ Expr(field.index) } then {
            ${ assignRawValue(objExpr, field, raw) }
          } else {
            $next
          }
        }
      }
    }

    def readAndAssignDispatch(
        objExpr: Expr[T],
        fieldIdExpr: Expr[Int],
        fieldInfoExpr: Expr[FieldGroups.SerializationFieldInfo],
        readContextExpr: Expr[org.apache.fory.context.ReadContext],
        resolverExpr: Expr[TypeResolver]): Expr[Unit] = {
      '{
        val fieldValue =
          StaticGeneratedStructSerializer.readFieldValue($resolverExpr, $readContextExpr, $fieldInfoExpr)
        ${ assignValueById(objExpr, fieldIdExpr, 'fieldValue) }
      }
    }

    def constructFromValues(valuesExpr: Expr[Array[Any]]): Expr[T] = {
      val constructorOwned = fields.filter(_.constructorOwned)
      val postConstruction = fields.filterNot(_.constructorOwned)
      if postConstruction.isEmpty then {
        val args = constructorOwned.map { field =>
          valueArg(valuesExpr, field).asTerm
        }
        Apply(Select(New(TypeTree.of[T]), owner.primaryConstructor), args).asExprOf[T]
      } else if constructorOwned.isEmpty then {
        val obj = Symbol.newVal(Symbol.spliceOwner, "obj", TypeRepr.of[T], Flags.EmptyFlags, Symbol.noSymbol)
        val construct = Apply(Select(New(TypeTree.of[T]), owner.primaryConstructor), Nil)
        val assignments = postConstruction.map { field =>
          Assign(Select.unique(Ref(obj), field.name), valueArg(valuesExpr, field).asTerm)
        }
        Block(ValDef(obj, Some(construct)) :: assignments, Ref(obj)).asExprOf[T]
      } else {
        val obj = Symbol.newVal(Symbol.spliceOwner, "obj", TypeRepr.of[T], Flags.EmptyFlags, Symbol.noSymbol)
        val args = constructorOwned.map { field =>
          valueArg(valuesExpr, field).asTerm
        }
        val construct = Apply(Select(New(TypeTree.of[T]), owner.primaryConstructor), args)
        val assignments = postConstruction.map { field =>
          Assign(Select.unique(Ref(obj), field.name), valueArg(valuesExpr, field).asTerm)
        }
        Block(ValDef(obj, Some(construct)) :: assignments, Ref(obj)).asExprOf[T]
      }
    }

    def copyValue(valueExpr: Expr[T]): Expr[T] = {
      val constructorOwned = fields.filter(_.constructorOwned)
      val postConstruction = fields.filterNot(_.constructorOwned)
      if postConstruction.isEmpty then {
        val args = constructorOwned.map { field =>
          val selected = selectValue(valueExpr, field)
          field.sourceType.asType match {
            case '[a] => '{ $selected.asInstanceOf[a] }.asTerm
          }
        }
        Apply(Select(New(TypeTree.of[T]), owner.primaryConstructor), args).asExprOf[T]
      } else if constructorOwned.isEmpty then {
        val obj = Symbol.newVal(Symbol.spliceOwner, "obj", TypeRepr.of[T], Flags.EmptyFlags, Symbol.noSymbol)
        val construct = Apply(Select(New(TypeTree.of[T]), owner.primaryConstructor), Nil)
        val assignments = postConstruction.map { field =>
          val selected = selectValue(valueExpr, field)
          val copied = field.sourceType.asType match {
            case '[a] => '{ $selected.asInstanceOf[a] }
          }
          Assign(Select.unique(Ref(obj), field.name), copied.asTerm)
        }
        Block(ValDef(obj, Some(construct)) :: assignments, Ref(obj)).asExprOf[T]
      } else {
        val obj = Symbol.newVal(Symbol.spliceOwner, "obj", TypeRepr.of[T], Flags.EmptyFlags, Symbol.noSymbol)
        val args = constructorOwned.map { field =>
          val selected = selectValue(valueExpr, field)
          field.sourceType.asType match {
            case '[a] => '{ $selected.asInstanceOf[a] }.asTerm
          }
        }
        val construct = Apply(Select(New(TypeTree.of[T]), owner.primaryConstructor), args)
        val assignments = postConstruction.map { field =>
          val selected = selectValue(valueExpr, field)
          val copied = field.sourceType.asType match {
            case '[a] => '{ $selected.asInstanceOf[a] }
          }
          Assign(Select.unique(Ref(obj), field.name), copied.asTerm)
        }
        Block(ValDef(obj, Some(construct)) :: assignments, Ref(obj)).asExprOf[T]
      }
    }

    def constructRead(valuesExpr: Expr[Array[Any]], readContextExpr: Expr[org.apache.fory.context.ReadContext]): Expr[T] = {
      val constructorOwned = fields.filter(_.constructorOwned)
      if constructorOwned.isEmpty then {
        report.errorAndAbort(
          s"${owner.fullName} cycle-owned generated classes must use generated mutable read paths")
      } else constructFromValues(valuesExpr)
    }

    def readSchemaConsistentBody(
        serializerExpr: Expr[StaticGeneratedStructSerializer[T]],
        resolverExpr: Expr[TypeResolver],
        descriptorsExpr: Expr[java.util.List[Descriptor]],
        classVersionHashExpr: Expr[Int],
        allFieldsExpr: Expr[Array[FieldGroups.SerializationFieldInfo]],
        allFieldIdsExpr: Expr[Array[Int]],
        readContextExpr: Expr[org.apache.fory.context.ReadContext]): Expr[T] = {
      val constructorOwned = fields.filter(_.constructorOwned)
      if constructorOwned.isEmpty then {
        '{
          val buffer = $readContextExpr.getBuffer
          if $resolverExpr.checkClassVersion() then {
            $serializerExpr.checkClassVersion(buffer.readInt32(), $classVersionHashExpr)
          }
          val obj = ${ Apply(Select(New(TypeTree.of[T]), owner.primaryConstructor), Nil).asExprOf[T] }
          $readContextExpr.reference(obj)
          var i = 0
          while i < $allFieldsExpr.length do {
            val fieldInfo = $allFieldsExpr(i)
            val fieldId = $allFieldIdsExpr(i)
            ${ readAndAssignDispatch('obj, 'fieldId, 'fieldInfo, readContextExpr, resolverExpr) }
            i += 1
          }
          obj
        }
      } else {
        '{
          val buffer = $readContextExpr.getBuffer
          if $resolverExpr.checkClassVersion() then {
            $serializerExpr.checkClassVersion(buffer.readInt32(), $classVersionHashExpr)
          }
          val values = new Array[Any]($descriptorsExpr.size())
          var i = 0
          while i < $allFieldsExpr.length do {
            val fieldInfo = $allFieldsExpr(i)
            values($allFieldIdsExpr(i)) =
              StaticGeneratedStructSerializer.readFieldValue($resolverExpr, $readContextExpr, fieldInfo)
            i += 1
          }
          ${ constructRead('values, readContextExpr) }
        }
      }
    }

    def readCompatibleBody(
        serializerExpr: Expr[StaticGeneratedStructSerializer[T]],
        descriptorsExpr: Expr[java.util.List[Descriptor]],
        fieldsByIdExpr: Expr[Array[FieldGroups.SerializationFieldInfo]],
        sameSchemaCompatibleExpr: Expr[Boolean],
        readContextExpr: Expr[org.apache.fory.context.ReadContext]): Expr[T] = {
      val constructorOwned = fields.filter(_.constructorOwned)
      if constructorOwned.isEmpty then {
        '{
          if $sameSchemaCompatibleExpr then {
            $serializerExpr.read($readContextExpr)
          } else {
            val obj = ${ Apply(Select(New(TypeTree.of[T]), owner.primaryConstructor), Nil).asExprOf[T] }
            $readContextExpr.reference(obj)
            val remoteFields = $serializerExpr.getRemoteFields()
            var i = 0
            while i < remoteFields.size() do {
              val remoteField = remoteFields.get(i)
              val matchedId = remoteField.matchedId
              if matchedId >= 0 then {
                val localField = $fieldsByIdExpr(matchedId)
                if $serializerExpr.canReadRemoteField(remoteField, localField) then {
                  val fieldValue =
                    $serializerExpr.readCompatibleFieldValue($readContextExpr, remoteField, localField)
                  ${ assignValueById('obj, 'matchedId, 'fieldValue) }
                } else {
                  $serializerExpr.skipField($readContextExpr, remoteField)
                }
              } else {
                $serializerExpr.skipField($readContextExpr, remoteField)
              }
              i += 1
            }
            obj
          }
        }
      } else {
        '{
          if $sameSchemaCompatibleExpr then {
            $serializerExpr.read($readContextExpr)
          } else {
            val values = new Array[Any]($descriptorsExpr.size())
            val remoteFields = $serializerExpr.getRemoteFields()
            var i = 0
            while i < remoteFields.size() do {
              val remoteField = remoteFields.get(i)
              val matchedId = remoteField.matchedId
              if matchedId >= 0 then {
                val localField = $fieldsByIdExpr(matchedId)
                if $serializerExpr.canReadRemoteField(remoteField, localField) then {
                  values(matchedId) =
                    $serializerExpr.readCompatibleFieldValue($readContextExpr, remoteField, localField)
                } else {
                  $serializerExpr.skipField($readContextExpr, remoteField)
                }
              } else {
                $serializerExpr.skipField($readContextExpr, remoteField)
              }
              i += 1
            }
            ${ constructRead('values, readContextExpr) }
          }
        }
      }
    }

    val classExpr: Expr[Class[T]] =
      '{ Class.forName(${ Expr(ownerClassName) }).asInstanceOf[Class[T]] }

    '{
      new ForySerializer[T] with StaticGeneratedStructSerializerFactory[T] {
        private val descriptors: java.util.List[Descriptor] = $descriptorsExpr

        override def getGeneratedDescriptors(): java.util.List[Descriptor] = descriptors

        override def createSerializer(
            resolver: TypeResolver,
            remoteTypeDef: ForyTypeDef): Serializer[T] = {
          newSerializer(resolver, $classExpr, remoteTypeDef)
        }

        override def newSerializer(
            resolver: TypeResolver,
            cls: Class[?],
            remoteTypeDef: ForyTypeDef): StaticGeneratedStructSerializer[T] = {
          new StaticGeneratedStructSerializer[T](resolver, cls, remoteTypeDef, descriptors) {
            private val generatedSerializer: StaticGeneratedStructSerializer[T] = this
            private val fieldGroups: FieldGroups =
              buildLocalFieldGroups(descriptors)
            private val allFields: Array[FieldGroups.SerializationFieldInfo] =
              fieldGroups.allFields
            private val allFieldIds: Array[Int] = localFieldIds(allFields, descriptors)
            private val fieldsById: Array[FieldGroups.SerializationFieldInfo] = {
              val result = new Array[FieldGroups.SerializationFieldInfo](descriptors.size())
              var i = 0
              while i < allFields.length do {
                result(allFieldIds(i)) = allFields(i)
                i += 1
              }
              result
            }
            private val classVersionHash: Int =
              if resolver.checkClassVersion() then computeClassVersionHash(descriptors) else 0
            private val sameSchemaCompatible: Boolean =
              remoteTypeDef != null &&
                remoteTypeDef.getId == ForyTypeDef.buildTypeDef(resolver, cls).getId

            override def getGeneratedDescriptors(): java.util.List[Descriptor] = descriptors

            override def write(
                writeContext: org.apache.fory.context.WriteContext,
                value: T): Unit = {
              val buffer = writeContext.getBuffer
              if resolver.checkClassVersion() then {
                buffer.writeInt32(classVersionHash)
              }
              var i = 0
              while i < allFields.length do {
                val fieldInfo = allFields(i)
                val fieldId = allFieldIds(i)
                ${ writeDispatch('value, 'fieldId, 'fieldInfo, 'writeContext, 'resolver) }
                i += 1
              }
            }

            override def read(readContext: org.apache.fory.context.ReadContext): T = {
              if remoteTypeDef != null && !sameSchemaCompatible then readCompatible(readContext)
              else readSchemaConsistent(readContext)
            }

            private def readSchemaConsistent(
                readContext: org.apache.fory.context.ReadContext): T =
              ${
                readSchemaConsistentBody(
                  'generatedSerializer,
                  'resolver,
                  'descriptors,
                  'classVersionHash,
                  'allFields,
                  'allFieldIds,
                  'readContext)
              }

            override def readCompatible(readContext: org.apache.fory.context.ReadContext): T = {
              ${
                readCompatibleBody(
                  'generatedSerializer,
                  'descriptors,
                  'fieldsById,
                  'sameSchemaCompatible,
                  'readContext)
              }
            }

            override def copy(
                copyContext: org.apache.fory.context.CopyContext,
                value: T): T = ${ copyValue('value) }
          }
        }
      }
    }
  }

  private def deriveUnion[T: Type](using q: Quotes)(
      owner: q.reflect.Symbol): Expr[ForySerializer[T]] = {
    import q.reflect.*

    final case class CaseMeta(
        symbol: Symbol,
        id: Int,
        payloadType: TypeRepr,
        payloadName: String,
        unknownIdName: String,
        unknown: Boolean,
        fieldIndex: Int)

    def payloadMeta(child: Symbol, id: Int): (TypeRepr, String, String) = {
      val params = child.primaryConstructor.paramSymss.flatten
      if id == 0 then {
        if params.size != 2 then {
          report.errorAndAbort(
            s"${child.fullName} is the unknown union case and must have (caseId: Int, value: Any)")
        }
        val caseIdType = params.head.tree match {
          case ValDef(_, tpt, _) => tpt.tpe
          case _ => params.head.termRef.widen
        }
        val tpe = params(1).tree match {
          case ValDef(_, tpt, _) => tpt.tpe
          case _ => TypeRepr.of[Any]
        }
        if !(caseIdType =:= TypeRepr.of[Int]) || !(tpe =:= TypeRepr.of[Any]) then {
          report.errorAndAbort(
            s"${child.fullName} is the unknown union case and must have (caseId: Int, value: Any)")
        }
        (tpe, params(1).name, params.head.name)
      } else {
        if params.size != 1 then {
          report.errorAndAbort(s"${child.fullName} must have exactly one payload parameter")
        }
        val tpe = params.head.tree match {
          case ValDef(_, tpt, _) => tpt.tpe
          case _ => params.head.termRef.widen
        }
        (tpe, params.head.name, "")
      }
    }

    val rawCases = owner.children.flatMap { child =>
      annotationIntArg[ForyCase](child, "id").map { id =>
        if id < 0 then report.errorAndAbort(s"${child.fullName} @ForyCase id must be >= 0")
        val (tpe, payloadName, unknownIdName) = payloadMeta(child, id)
        CaseMeta(child, id, tpe, payloadName, unknownIdName, id == 0, -1)
      }
    }
    var nextFieldIndex = 0
    val cases = rawCases.map { unionCase =>
      if unionCase.unknown then unionCase
      else {
        val indexed = unionCase.copy(fieldIndex = nextFieldIndex)
        nextFieldIndex += 1
        indexed
      }
    }
    val knownCases = cases.filterNot(_.unknown)
    if cases.count(_.unknown) > 1 then {
      report.errorAndAbort(s"${owner.fullName} must define exactly one @ForyCase(id = 0) unknown case")
    }
    if cases.filterNot(_.unknown).groupBy(_.id).exists(_._2.size > 1) then {
      report.errorAndAbort(s"${owner.fullName} has duplicate @ForyCase ids")
    }
    val unknown =
      cases.find(_.unknown).getOrElse(
        report.errorAndAbort(s"${owner.fullName} must define @ForyCase(id = 0) unknown case"))

    def peelAnnotations(tpe: TypeRepr): (TypeRepr, List[Term]) = {
      tpe match {
        case AnnotatedType(underlying, annotation) =>
          val (base, annotations) = peelAnnotations(underlying)
          (base, annotation :: annotations)
        case other =>
          other.dealias match {
            case AnnotatedType(underlying, annotation) =>
              val (base, annotations) = peelAnnotations(underlying)
              (base, annotation :: annotations)
            case dealiased => (dealiased, Nil)
          }
      }
    }

    def optionElement(tpe: TypeRepr): Option[TypeRepr] = {
      peelAnnotations(tpe)._1.dealias match {
        case AppliedType(base, List(arg)) if base.typeSymbol.fullName == "scala.Option" =>
          Some(arg)
        case _ => None
      }
    }

    def boxedIfPrimitive(tpe: TypeRepr): TypeRepr = {
      val (base, annotations) = peelAnnotations(tpe)
      val boxed =
        if base =:= TypeRepr.of[Boolean] then TypeRepr.of[java.lang.Boolean]
        else if base =:= TypeRepr.of[Byte] then TypeRepr.of[java.lang.Byte]
        else if base =:= TypeRepr.of[Short] then TypeRepr.of[java.lang.Short]
        else if base =:= TypeRepr.of[Int] then TypeRepr.of[java.lang.Integer]
        else if base =:= TypeRepr.of[Long] then TypeRepr.of[java.lang.Long]
        else if base =:= TypeRepr.of[Float] then TypeRepr.of[java.lang.Float]
        else if base =:= TypeRepr.of[Double] then TypeRepr.of[java.lang.Double]
        else base
      annotations.foldRight(boxed)((annotation, current) => AnnotatedType(current, annotation))
    }

    def classFor(tpe: TypeRepr): Expr[Class[?]] = {
      val normalized = peelAnnotations(tpe.widen)._1.dealias
      val fullName = normalized.typeSymbol.fullName
      if normalized =:= TypeRepr.of[Boolean] then '{ java.lang.Boolean.TYPE }
      else if normalized =:= TypeRepr.of[Byte] then '{ java.lang.Byte.TYPE }
      else if normalized =:= TypeRepr.of[Short] then '{ java.lang.Short.TYPE }
      else if normalized =:= TypeRepr.of[Int] then '{ java.lang.Integer.TYPE }
      else if normalized =:= TypeRepr.of[Long] then '{ java.lang.Long.TYPE }
      else if normalized =:= TypeRepr.of[Float] then '{ java.lang.Float.TYPE }
      else if normalized =:= TypeRepr.of[Double] then '{ java.lang.Double.TYPE }
      else if normalized =:= TypeRepr.of[Char] then '{ java.lang.Character.TYPE }
      else if normalized =:= TypeRepr.of[String] ||
          normalized.typeSymbol == TypeRepr.of[String].typeSymbol ||
          fullName == "scala.Predef.String" ||
          fullName == "scala.Predef$.String" ||
          fullName.endsWith("Predef.String") ||
          fullName.endsWith("Predef$.String")
      then '{ classOf[String] }
      else if fullName == "scala.Array" then {
        '{ Class.forName(${ Expr(arrayClassName(normalized)) }) }
      } else '{ Class.forName(${ Expr(fullName.replace("$.", "$")) }) }
    }

    def arrayClassName(tpe: TypeRepr): String = {
      tpe.dealias match {
        case AppliedType(arrayType, List(componentType))
            if arrayType.typeSymbol.fullName == "scala.Array" =>
          "[" + arrayComponentDescriptor(componentType)
        case _ =>
          report.errorAndAbort(s"Expected Scala Array type, got ${tpe.show}")
      }
    }

    def arrayComponentDescriptor(tpe: TypeRepr): String = {
      val normalized = peelAnnotations(tpe.widen)._1.dealias
      if normalized =:= TypeRepr.of[Boolean] then "Z"
      else if normalized =:= TypeRepr.of[Byte] then "B"
      else if normalized =:= TypeRepr.of[Short] then "S"
      else if normalized =:= TypeRepr.of[Int] then "I"
      else if normalized =:= TypeRepr.of[Long] then "J"
      else if normalized =:= TypeRepr.of[Float] then "F"
      else if normalized =:= TypeRepr.of[Double] then "D"
      else if normalized =:= TypeRepr.of[Char] then "C"
      else if normalized.typeSymbol.fullName == "scala.Array" then arrayClassName(normalized)
      else "L" + normalized.typeSymbol.fullName.replace("$.", "$") + ";"
    }

    def generatedType(tpe: TypeRepr): Expr[Descriptor.GeneratedType] = {
      val (outer, outerAnnotations) = peelAnnotations(tpe)
      val option = optionElement(outer)
      val fieldSource = option.map(boxedIfPrimitive).getOrElse(outer)
      val (base, baseAnnotations) = peelAnnotations(fieldSource)
      val annotations = outerAnnotations ++ baseAnnotations
      val argumentSource = fieldSource
      def appliedType(tpe: TypeRepr): Option[(TypeRepr, List[TypeRepr])] = {
        val directArgs = tpe.typeArgs
        if directArgs.nonEmpty then {
          tpe match {
            case AppliedType(typeConstructor, _) => Some((typeConstructor, directArgs))
            case other =>
              other.dealias match {
                case AppliedType(typeConstructor, _) => Some((typeConstructor, directArgs))
                case _ => Some((tpe, directArgs))
              }
          }
        } else {
          tpe match {
            case AppliedType(typeConstructor, typeArgs) => Some((typeConstructor, typeArgs))
            case other =>
              other.dealias match {
                case AppliedType(typeConstructor, typeArgs) => Some((typeConstructor, typeArgs))
                case _ => None
              }
          }
        }
      }
      val component = appliedType(argumentSource) match {
        case Some((arrayType, List(componentType)))
            if arrayType.typeSymbol.fullName == "scala.Array" =>
          Some(generatedType(componentType))
        case _ => None
      }
      val args = appliedType(argumentSource) match {
        case Some((arrayType, List(_))) if arrayType.typeSymbol.fullName == "scala.Array" =>
          Nil
        case Some((_, typeArgs)) => typeArgs
        case _ => Nil
      }
      val argExprs = args.map(generatedType)
      val argList: Expr[java.util.List[Descriptor.GeneratedType]] =
        '{
          import scala.jdk.CollectionConverters.*
          java.util.Collections.unmodifiableList(${ Expr.ofList(argExprs) }.asJava)
        }
      val componentExpr: Expr[Descriptor.GeneratedType] =
        component.getOrElse('{ null.asInstanceOf[Descriptor.GeneratedType] })
      val typeId =
        annotations.flatMap(typeIdForAnnotation).headOption
          .orElse {
            if hasAnnotation[ForyUnion](base.typeSymbol) then Some(Types.UNION) else None
          }
          .orElse {
            if isScalaEnumType(base) then Some(Types.ENUM) else None
          }
          .getOrElse(Types.UNKNOWN)
      val rawClass = classFor(base)
      val typeExtMeta = generatedTypeExtMeta(
        typeId,
        nullable = option.nonEmpty,
        trackingRef = annotations.exists(isRefAnnotation),
        rawClass = Some(rawClass))
      '{ Descriptor.generatedType($rawClass, $typeExtMeta, $argList, $componentExpr) }
    }

    def caseDescriptor(unionCase: CaseMeta): Expr[Descriptor] = {
      '{
        new Descriptor(
          ${ generatedType(unionCase.payloadType) },
          ${ Expr(unionCase.payloadType.show) },
          ${ Expr(unionCase.symbol.name + ".value") },
          ${ Expr(Modifier.PRIVATE | Modifier.FINAL) },
          ${ Expr(owner.fullName.replace("$.", "$")) },
          true,
          ${ Expr(unionCase.id) },
          false,
          false,
          ForyField.Dynamic.AUTO,
          false
        )
      }
    }

    val caseDescriptorsExpr: Expr[java.util.List[Descriptor]] =
      '{
        import scala.jdk.CollectionConverters.*
        java.util.Collections.unmodifiableList(${ Expr.ofList(knownCases.map(caseDescriptor)) }.asJava)
      }

    def writeDispatch(
        valueExpr: Expr[T],
        writeContextExpr: Expr[org.apache.fory.context.WriteContext],
        resolverExpr: Expr[TypeResolver],
        caseFieldInfosExpr: Expr[Array[FieldGroups.SerializationFieldInfo]]): Expr[Unit] = {
      cases.foldRight(
        '{
          throw new IllegalStateException("Unknown Scala union case " + $valueExpr)
        }: Expr[Unit]) { (unionCase, next) =>
        unionCase.symbol.typeRef.asType match {
          case '[c] =>
            if unionCase.unknown then {
              val originalId =
                Select.unique(
                  '{ $valueExpr.asInstanceOf[c] }.asTerm,
                  unionCase.unknownIdName).asExprOf[Int]
              val payload =
                Select.unique(
                  '{ $valueExpr.asInstanceOf[c] }.asTerm,
                  unionCase.payloadName).asExpr
              '{
                if $valueExpr.isInstanceOf[c] then {
                  UnionSerializer.writeUnknownCaseValue($writeContextExpr, $payload, $originalId)
                } else {
                  $next
                }
              }
            } else {
              val payload =
                Select.unique(
                  '{ $valueExpr.asInstanceOf[c] }.asTerm,
                  unionCase.payloadName).asExpr
              '{
                if $valueExpr.isInstanceOf[c] then {
                  UnionSerializer.writeCaseValue(
                    $resolverExpr,
                    $writeContextExpr,
                    $caseFieldInfosExpr(${ Expr(unionCase.fieldIndex) }),
                    $payload,
                    ${ Expr(unionCase.id) })
                } else {
                  $next
                }
              }
            }
        }
      }
    }

    def construct(unionCase: CaseMeta, args: List[Term]): Expr[T] = {
      Apply(Select(New(TypeTree.ref(unionCase.symbol)), unionCase.symbol.primaryConstructor), args)
        .asExprOf[T]
    }

    def readDispatch(
        caseIdExpr: Expr[Int],
        readContextExpr: Expr[org.apache.fory.context.ReadContext],
        resolverExpr: Expr[TypeResolver],
        caseFieldInfosExpr: Expr[Array[FieldGroups.SerializationFieldInfo]]): Expr[T] = {
      val unknownPayload = '{ $readContextExpr.readRef() }
      val unknownExpr = construct(unknown, List(caseIdExpr.asTerm, unknownPayload.asTerm))
      knownCases.foldRight(unknownExpr) { (unionCase, next) =>
        unionCase.payloadType.asType match {
          case '[p] =>
            val rawPayload =
              '{
                UnionSerializer.readCaseValue(
                  $resolverExpr,
                  $readContextExpr,
                  $caseFieldInfosExpr(${ Expr(unionCase.fieldIndex) }))
              }
            val payload = coercePayload[p](rawPayload, unionCase.payloadType)
            val current = construct(unionCase, List(payload.asTerm))
            '{
              if $caseIdExpr == ${ Expr(unionCase.id) } then $current else $next
            }
        }
      }
    }

    def coercePayload[P: Type](
        payloadExpr: Expr[Any],
        payloadType: TypeRepr): Expr[P] = {
      val rawTypeName = payloadType.dealias match {
        case AppliedType(base, _) => base.typeSymbol.fullName
        case other => other.typeSymbol.fullName
      }
      val renderedType = payloadType.show
      if rawTypeName == "scala.collection.immutable.List" ||
          rawTypeName == "scala.collection.Seq" ||
          rawTypeName == "scala.collection.immutable.Seq" ||
          renderedType.startsWith("scala.List[") ||
          renderedType.startsWith("List[")
      then {
          '{
            $payloadExpr match {
              case value: scala.collection.immutable.List[?] => value.asInstanceOf[P]
              case value: java.util.List[?] =>
                import scala.jdk.CollectionConverters.*
                value.asScala.toList.asInstanceOf[P]
              case value => value.asInstanceOf[P]
            }
          }
      } else if rawTypeName == "scala.collection.immutable.Set" ||
          rawTypeName == "scala.collection.Set" ||
          renderedType.startsWith("scala.Set[") ||
          renderedType.startsWith("Set[")
      then {
          '{
            $payloadExpr match {
              case value: scala.collection.immutable.Set[?] => value.asInstanceOf[P]
              case value: java.util.Set[?] =>
                import scala.jdk.CollectionConverters.*
                value.asScala.toSet.asInstanceOf[P]
              case value => value.asInstanceOf[P]
            }
          }
      } else if rawTypeName == "scala.collection.immutable.Map" ||
          rawTypeName == "scala.collection.Map" ||
          renderedType.startsWith("scala.Map[") ||
          renderedType.startsWith("Map[")
      then {
          '{
            $payloadExpr match {
              case value: scala.collection.immutable.Map[?, ?] => value.asInstanceOf[P]
              case value: java.util.Map[?, ?] =>
                import scala.jdk.CollectionConverters.*
                value.asScala.toMap.asInstanceOf[P]
              case value => value.asInstanceOf[P]
            }
          }
      } else '{ $payloadExpr.asInstanceOf[P] }
    }

    val ownerClassName = owner.fullName.replace("$.", "$")
    val classExpr: Expr[Class[T]] =
      '{ Class.forName(${ Expr(ownerClassName) }).asInstanceOf[Class[T]] }
    val caseClassesExpr: Expr[List[Class[_]]] =
      Expr.ofList(cases.map(unionCase =>
        '{ Class.forName(${ Expr(ownerClassName + "$" + unionCase.symbol.name) }) }))

    '{
      new ForySerializer[T] {
        override def isUnion: Boolean = true

        override def registrationClasses(cls: Class[T]): Array[Class[_]] =
          (cls :: $caseClassesExpr).toArray

        override def createSerializer(
            resolver: TypeResolver,
            remoteTypeDef: ForyTypeDef): Serializer[T] = {
          new Serializer[T](resolver.getConfig, $classExpr) {
            private val caseFieldInfos: Array[FieldGroups.SerializationFieldInfo] = {
              val descriptors = $caseDescriptorsExpr
              val result = new Array[FieldGroups.SerializationFieldInfo](descriptors.size())
              var i = 0
              while i < descriptors.size() do {
                result(i) = new FieldGroups.SerializationFieldInfo(resolver, descriptors.get(i))
                i += 1
              }
              result
            }

            override def write(
                writeContext: org.apache.fory.context.WriteContext,
                value: T): Unit = {
              ${ writeDispatch('value, 'writeContext, 'resolver, 'caseFieldInfos) }
            }

            override def read(readContext: org.apache.fory.context.ReadContext): T = {
              val buffer = readContext.getBuffer
              val caseId = buffer.readVarUInt32()
              ${ readDispatch('caseId, 'readContext, 'resolver, 'caseFieldInfos) }
            }

            override def copy(copyContext: org.apache.fory.context.CopyContext, value: T): T =
              value
          }
        }
      }
    }
  }

  private def annotationIntArg[A: Type](using q: Quotes)(
      symbol: q.reflect.Symbol,
      name: String): Option[Int] = {
    import q.reflect.*
    symbol.annotations
      .find(_.tpe <:< TypeRepr.of[A])
      .flatMap {
        case Apply(_, args) =>
          args.collectFirst {
            case NamedArg(`name`, Literal(IntConstant(value))) => value
            case Literal(IntConstant(value)) => value
          }
        case _ => None
      }
  }

  private def hasAnnotation[A: Type](using q: Quotes)(symbol: q.reflect.Symbol): Boolean = {
    import q.reflect.*
    symbol.annotations.exists(_.tpe <:< TypeRepr.of[A])
  }

  private def hasRef(using q: Quotes)(symbol: q.reflect.Symbol): Boolean = {
    import q.reflect.*
    symbol.annotations.exists(_.tpe.typeSymbol.fullName == "org.apache.fory.annotation.Ref")
  }

  private def topLevelTypeHasRef(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect.*

    def peelAnnotations(tpe: TypeRepr): (TypeRepr, List[Term]) = {
      tpe match {
        case AnnotatedType(underlying, annotation) =>
          val (base, annotations) = peelAnnotations(underlying)
          (base, annotation :: annotations)
        case other =>
          other.dealias match {
            case AnnotatedType(underlying, annotation) =>
              val (base, annotations) = peelAnnotations(underlying)
              (base, annotation :: annotations)
            case dealiased => (dealiased, Nil)
          }
      }
    }

    def isRef(annotation: Term): Boolean =
      annotation.tpe.typeSymbol.fullName == "org.apache.fory.annotation.Ref"

    val (base, annotations) = peelAnnotations(tpe)
    base.dealias match {
      case AppliedType(optionType, List(inner))
          if optionType.typeSymbol.fullName == "scala.Option" =>
        peelAnnotations(inner)._2.exists(isRef)
      case _ => annotations.exists(isRef)
    }
  }

  private def generatedTypeExtMeta(using q: Quotes)(
      typeId: Int,
      nullable: Boolean,
      trackingRef: Boolean,
      rawClass: Option[Expr[Class[?]]] = None): Expr[TypeExtMeta] = {
    if typeId == Types.UNKNOWN && rawClass.nonEmpty then {
      val raw = rawClass.get
      '{
        val resolvedTypeId =
          if classOf[ForyScalaEnum].isAssignableFrom($raw) then Types.ENUM else Types.UNKNOWN
        if resolvedTypeId == Types.UNKNOWN && !${ Expr(nullable) } && !${ Expr(trackingRef) } then {
          null.asInstanceOf[TypeExtMeta]
        } else {
          TypeExtMeta.of(resolvedTypeId, ${ Expr(nullable) }, ${ Expr(trackingRef) })
        }
      }
    } else if typeId == Types.UNKNOWN && !nullable && !trackingRef then {
      '{ null.asInstanceOf[TypeExtMeta] }
    } else {
      '{ TypeExtMeta.of(${ Expr(typeId) }, ${ Expr(nullable) }, ${ Expr(trackingRef) }) }
    }
  }

  private def isScalaEnumType(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect.*
    tpe.typeSymbol.flags.is(Flags.Enum) ||
    tpe <:< TypeRepr.of[ForyScalaEnum] ||
    tpe.baseClasses.exists(_.fullName == "org.apache.fory.scala.ForyScalaEnum")
  }

  private def typeIdForAnnotation(using q: Quotes)(annotation: q.reflect.Term): Option[Int] = {
    import q.reflect.*
    val annotationName = annotation.tpe.typeSymbol.fullName
    annotationName match {
      case "org.apache.fory.annotation.Int8Type" => Some(Types.INT8)
      case "org.apache.fory.annotation.UInt8Type" => Some(Types.UINT8)
      case "org.apache.fory.annotation.UInt16Type" => Some(Types.UINT16)
      case "org.apache.fory.annotation.Float16Type" => Some(Types.FLOAT16)
      case "org.apache.fory.annotation.BFloat16Type" => Some(Types.BFLOAT16)
      case "org.apache.fory.annotation.Int32Type" =>
        Some(if annotationEncoding(annotation).contains("FIXED") then Types.INT32 else Types.VARINT32)
      case "org.apache.fory.annotation.UInt32Type" =>
        Some(if annotationEncoding(annotation).contains("FIXED") then Types.UINT32 else Types.VAR_UINT32)
      case "org.apache.fory.annotation.Int64Type" =>
        annotationEncoding(annotation) match {
          case Some("FIXED") => Some(Types.INT64)
          case Some("TAGGED") => Some(Types.TAGGED_INT64)
          case _ => Some(Types.VARINT64)
        }
      case "org.apache.fory.annotation.UInt64Type" =>
        annotationEncoding(annotation) match {
          case Some("FIXED") => Some(Types.UINT64)
          case Some("TAGGED") => Some(Types.TAGGED_UINT64)
          case _ => Some(Types.VAR_UINT64)
        }
      case _ => None
    }
  }

  private def annotationEncoding(using q: Quotes)(annotation: q.reflect.Term): Option[String] = {
    import q.reflect.*
    annotation match {
      case Apply(_, args) => args.collectFirst {
          case NamedArg("encoding", Select(_, name)) => name
          case NamedArg("encoding", Ident(name)) => name
        }
      case _ => None
    }
  }

  private def isRefAnnotation(using q: Quotes)(annotation: q.reflect.Term): Boolean = {
    annotation.tpe.typeSymbol.fullName == "org.apache.fory.annotation.Ref"
  }
}
