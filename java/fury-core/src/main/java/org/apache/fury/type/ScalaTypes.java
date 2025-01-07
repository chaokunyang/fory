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

package org.apache.fury.type;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import org.apache.fury.collection.Tuple2;
import org.apache.fury.reflect.ReflectionUtils;
import org.apache.fury.reflect.TypeRef;

/** Scala types utils using reflection without dependency on scala library. */
@SuppressWarnings({"unchecked", "rawtypes"})
public class ScalaTypes {
  private static volatile Class<?> SCALA_MAP_TYPE;
  private static volatile Class<?> SCALA_SEQ_TYPE;
  private static volatile Class<?> SCALA_SET_TYPE;
  private static volatile Class<?> SCALA_ITERABLE_TYPE;
  private static volatile java.lang.reflect.Type SCALA_ITERATOR_RETURN_TYPE;
  private static volatile java.lang.reflect.Type SCALA_NEXT_RETURN_TYPE;

  public static Class<?> getScalaMapType() {
    if (SCALA_MAP_TYPE == null) {
      // load scala classes dynamically to make graalvm native build work
      // see https://github.com/quarkiverse/quarkus-fury/issues/7
      SCALA_MAP_TYPE = ReflectionUtils.loadClass("scala.collection.Map");
    }
    return SCALA_MAP_TYPE;
  }

  public static Class<?> getScalaSeqType() {
    if (SCALA_SEQ_TYPE == null) {
      SCALA_SEQ_TYPE = ReflectionUtils.loadClass("scala.collection.Seq");
    }
    return SCALA_SEQ_TYPE;
  }

  public static Class<?> getScalaSetType() {
    if (SCALA_SET_TYPE == null) {
      SCALA_SET_TYPE = ReflectionUtils.loadClass("scala.collection.Set");
    }
    return SCALA_SET_TYPE;
  }

  public static Class<?> getScalaIterableType() {
    if (SCALA_ITERABLE_TYPE == null) {
      SCALA_ITERABLE_TYPE = ReflectionUtils.loadClass("scala.collection.Iterable");
    }
    return SCALA_ITERABLE_TYPE;
  }

  public static TypeRef<?> getElementType(TypeRef typeRef) {
    TypeRef<?> supertype = typeRef.getSupertype(getScalaIterableType());
    return supertype
        .resolveType(getScalaIteratorReturnType())
        .resolveType(getScalaNextReturnType());
  }

  private static Type getScalaIteratorReturnType() {
    if (SCALA_ITERATOR_RETURN_TYPE == null) {
      try {
        SCALA_ITERATOR_RETURN_TYPE =
            getScalaIterableType().getMethod("iterator").getGenericReturnType();
      } catch (NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
    }
    return SCALA_ITERATOR_RETURN_TYPE;
  }

  private static Type getScalaNextReturnType() {
    if (SCALA_NEXT_RETURN_TYPE == null) {
      Class<?> scalaIteratorType = ReflectionUtils.loadClass("scala.collection.Iterator");
      try {
        SCALA_NEXT_RETURN_TYPE = scalaIteratorType.getMethod("next").getGenericReturnType();
      } catch (NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
    }
    return SCALA_NEXT_RETURN_TYPE;
  }

  /** Returns key/value type of scala map. */
  public static Tuple2<TypeRef<?>, TypeRef<?>> getMapKeyValueType(TypeRef typeRef) {
    TypeRef<?> kvTupleType = getElementType(typeRef);
    ParameterizedType type = (ParameterizedType) kvTupleType.getType();
    Type[] types = type.getActualTypeArguments();
    return Tuple2.of(TypeRef.of(types[0]), TypeRef.of(types[1]));
  }
}
