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

package org.apache.fory.reflect;

import java.lang.reflect.Constructor;
import org.apache.fory.annotation.Internal;
import org.apache.fory.collection.ClassValueCache;

/** Runtime-scoped object creator cache and explicit constructor registrations. */
@Internal
public final class ObjectCreatorRegistry {
  private final ClassValueCache<ObjectCreator<?>> objectCreatorCache =
      ClassValueCache.newClassKeySoftCache(8);
  private final ClassValueCache<ObjectCreators.ConstructorMatch<?>> constructorMatches =
      ClassValueCache.newClassKeyCache(8);

  public <T> ObjectCreator<T> getObjectCreator(Class<T> type) {
    return (ObjectCreator<T>)
        objectCreatorCache.get(
            type,
            () ->
                ObjectCreators.createObjectCreator(
                    type,
                    (ObjectCreators.ConstructorMatch<T>) constructorMatches.getIfPresent(type)));
  }

  public <T> void registerConstructor(
      Class<T> type, Constructor<T> constructor, String... fieldNames) {
    ObjectCreators.ConstructorMatch<T> match =
        ObjectCreators.explicitConstructor(type, constructor, fieldNames.clone(), "registered");
    ObjectCreator<T> objectCreator = ObjectCreators.createObjectCreator(type, match);
    constructorMatches.put(type, match);
    objectCreatorCache.put(type, objectCreator);
  }
}
