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

package org.apache.fory.serializer;

import java.lang.reflect.Field;
import org.apache.fory.Fory;
import org.apache.fory.meta.TypeExtMeta;
import org.apache.fory.reflect.FieldAccessor;
import org.apache.fory.reflect.TypeRef;
import org.apache.fory.resolver.RefMode;
import org.apache.fory.resolver.TypeResolver;
import org.apache.fory.serializer.converter.FieldConverter;
import org.apache.fory.type.Descriptor;
import org.apache.fory.type.DispatchId;

/** Immutable field metadata that can be shared across equivalent {@link Fory} instances. */
public final class ResolvedFieldInfo {
  public final Descriptor descriptor;
  public final Class<?> type;
  public final TypeRef<?> typeRef;
  public final int dispatchId;
  public final String qualifiedFieldName;
  public final FieldAccessor fieldAccessor;
  public final FieldConverter<?> fieldConverter;
  public final RefMode refMode;
  public final boolean nullable;
  public final boolean trackingRef;
  public final boolean isPrimitiveField;
  public final boolean isArray;
  public final boolean useDeclaredTypeInfo;

  ResolvedFieldInfo(Fory fory, Descriptor descriptor) {
    this.descriptor = descriptor;
    type = descriptor.getRawType();
    typeRef = descriptor.getTypeRef();
    dispatchId = DispatchId.getDispatchId(fory, descriptor);
    qualifiedFieldName = descriptor.getDeclaringClass() + "." + descriptor.getName();
    Field field = descriptor.getField();
    fieldAccessor = field == null ? null : FieldAccessor.createAccessor(field);
    fieldConverter = descriptor.getFieldConverter();
    TypeExtMeta extMeta = typeRef.getTypeExtMeta();
    if (extMeta != null) {
      nullable = extMeta.nullable();
      trackingRef = extMeta.trackingRef();
    } else {
      nullable = descriptor.isNullable();
      trackingRef = descriptor.isTrackingRef();
    }
    refMode = RefMode.of(trackingRef, nullable);
    isPrimitiveField = type.isPrimitive();
    isArray = type.isArray();
    TypeResolver resolver = fory.getTypeResolver();
    useDeclaredTypeInfo = resolver.isMonomorphic(descriptor);
  }
}
