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

package org.apache.fory.serializer.scala;

import org.apache.fory.Fory;
import org.apache.fory.annotation.Internal;
import org.apache.fory.context.ReadContext;
import org.apache.fory.resolver.TypeInfo;
import org.apache.fory.resolver.TypeResolver;
import org.apache.fory.serializer.FieldGroups;
import org.apache.fory.serializer.Serializer;
import org.apache.fory.serializer.Serializers;
import org.apache.fory.serializer.UnionSerializer;
import org.apache.fory.type.GenericType;
import org.apache.fory.type.Types;

/** Reads Scala union containers through the serializer that materializes their final target. */
@Internal
public final class ScalaUnionReadSupport {
  private ScalaUnionReadSupport() {}

  public static Object readCaseValue(
      TypeResolver resolver,
      ReadContext readContext,
      FieldGroups.SerializationFieldInfo fieldInfo) {
    int typeId = Types.getDescriptorTypeId(resolver, fieldInfo.descriptor);
    TypeInfo targetTypeInfo = fieldInfo.containerTypeInfo;
    if (!isScalaContainer(typeId, targetTypeInfo)) {
      return UnionSerializer.readCaseValue(resolver, readContext, fieldInfo);
    }
    int nextReadRefId = readContext.tryPreserveRefId();
    if (nextReadRefId < Fory.NOT_NULL_VALUE_FLAG) {
      // RefFlag owns no new materialization. Reuse the published final target directly; adapting
      // it would allocate an unbudgeted second owner and break reference identity.
      return readContext.getReadRef();
    }
    TypeInfo wireTypeInfo = resolver.readTypeInfo(readContext, targetTypeInfo);
    Serializer<?> serializer =
        wireTypeInfo.getTypeId() == typeId
            ? targetTypeInfo.getSerializer()
            : wireTypeInfo.getSerializer();
    Object value = readValue(readContext, serializer, fieldInfo.genericType);
    readContext.setReadRef(nextReadRefId, value);
    // Immutable Scala containers cannot publish during construction. Consume only this union's
    // preserved id after binding its final target so a nested owner's pending id is never popped.
    if (readContext.hasPreservedRefId() && readContext.lastPreservedRefId() == nextReadRefId) {
      readContext.reference(value);
    }
    return value;
  }

  private static boolean isScalaContainer(int typeId, TypeInfo typeInfo) {
    if (typeInfo == null || (typeId != Types.LIST && typeId != Types.SET && typeId != Types.MAP)) {
      return false;
    }
    return scala.collection.Iterable.class.isAssignableFrom(typeInfo.getType());
  }

  @SuppressWarnings("unchecked")
  private static Object readValue(
      ReadContext readContext, Serializer<?> serializer, GenericType genericType) {
    if (genericType == null) {
      readContext.increaseDepth();
      Object value = Serializers.read(readContext, (Serializer<Object>) serializer);
      readContext.decreaseDepth();
      return value;
    }
    // Root reset owns exceptional depth/generic cleanup, matching UnionSerializer's read path.
    readContext.getGenerics().pushGenericType(genericType, readContext.getDepth());
    readContext.increaseDepth();
    Object value = Serializers.read(readContext, (Serializer<Object>) serializer);
    readContext.decreaseDepth();
    readContext.getGenerics().popGenericType(readContext.getDepth());
    return value;
  }
}
