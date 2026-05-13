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

import org.apache.fory.meta.TypeDef;
import org.apache.fory.resolver.TypeResolver;

/**
 * Interpreter implementation for a single compatible class layer. Generated layer serializers
 * extend {@link CompatibleLayerSerializerBase} directly and override only the hot field read/write
 * paths.
 */
public class CompatibleLayerSerializer<T> extends CompatibleLayerSerializerBase<T> {

  public CompatibleLayerSerializer(
      TypeResolver typeResolver, Class<T> type, TypeDef layerTypeDef, Class<?> layerMarkerClass) {
    super(typeResolver, type);
    setLayerSerializerMeta(layerTypeDef, layerMarkerClass);
  }
}
