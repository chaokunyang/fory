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

import java.lang.reflect.Field;
import java.util.Collection;
import org.apache.fory.context.ReadContext;
import org.apache.fory.context.WriteContext;
import org.apache.fory.exception.ForyException;
import org.apache.fory.platform.AndroidSupport;
import org.apache.fory.platform.GraalvmSupport;
import org.apache.fory.platform.UnsafeOps;
import org.apache.fory.resolver.TypeResolver;
import org.apache.fory.serializer.collection.CollectionLikeSerializer;
import org.apache.fory.util.Preconditions;

/**
 * Singleton serializer for scala collection. We need this serializer for fory jit serialization,
 * otherwise the case exception will happen is an empty collection is being serialized as a field of
 * an object.
 */
@SuppressWarnings("rawtypes")
public class SingletonCollectionSerializer extends CollectionLikeSerializer {
  private final Field field;
  private Object base = null;
  private long offset = -1;

  public SingletonCollectionSerializer(TypeResolver typeResolver, Class cls) {
    super(typeResolver, cls, false);
    try {
      field = type.getDeclaredField("MODULE$");
      if (AndroidSupport.IS_ANDROID) {
        field.setAccessible(true);
      }
    } catch (NoSuchFieldException e) {
      throw new RuntimeException(type + " doesn't have `MODULE$` field", e);
    } catch (RuntimeException e) {
      throw new ForyException("Failed to make Scala singleton field accessible: " + type, e);
    }
  }

  @Override
  public Collection onCollectionWrite(WriteContext writeContext, Object value) {
    throw new IllegalStateException("unreachable");
  }

  @Override
  public void write(WriteContext writeContext, Object value) {}

  @Override
  public Object read(ReadContext readContext) {
    if (AndroidSupport.IS_ANDROID) {
      try {
        return field.get(null);
      } catch (IllegalAccessException | RuntimeException e) {
        throw new ForyException("Failed to read Scala singleton field: " + type, e);
      }
    }
    long offset = this.offset;
    if (offset == -1) {
      Preconditions.checkArgument(!GraalvmSupport.isGraalBuildTime());
      offset = this.offset = UnsafeOps.UNSAFE.staticFieldOffset(field);
      base = UnsafeOps.UNSAFE.staticFieldBase(field);
    }
    return UnsafeOps.getObject(base, offset);
  }

  @Override
  public Object onCollectionRead(Collection collection) {
    throw new IllegalStateException("unreachable");
  }
}
