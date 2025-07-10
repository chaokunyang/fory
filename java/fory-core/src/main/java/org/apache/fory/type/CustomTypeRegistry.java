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

package org.apache.fory.type;

import org.apache.fory.annotation.Internal;
import org.apache.fory.reflect.TypeRef;

@Internal
public interface CustomTypeRegistry {
  CustomTypeRegistry EMPTY =
      new CustomTypeRegistry() {
        @Override
        public TypeRef<?> replacementTypeFor(final Class<?> beanType, final Class<?> fieldType) {
          return null;
        }

        @Override
        public boolean canConstructCollection(
            final Class<?> collectionType, final Class<?> elementType) {
          return false;
        }

        @Override
        public boolean isExtraSupportedType(final TypeRef<?> type) {
          return false;
        }
      };

  TypeRef<?> replacementTypeFor(Class<?> beanType, Class<?> fieldType);

  boolean canConstructCollection(Class<?> collectionType, Class<?> elementType);

  boolean isExtraSupportedType(TypeRef<?> type);
}
