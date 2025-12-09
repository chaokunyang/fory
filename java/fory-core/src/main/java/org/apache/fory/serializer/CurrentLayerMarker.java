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

/**
 * Marker interface for generated layer wrapper classes. These classes serve as unique keys in
 * {@code metaContext.classMap} to distinguish different layers in class hierarchy during
 * serialization.
 *
 * <p>For a class hierarchy {@code C extends B extends A}, marker classes are generated:
 *
 * <ul>
 *   <li>{@code A$ForyLayer$0} - for A's layer fields
 *   <li>{@code B$ForyLayer$1} - for B's layer fields
 *   <li>{@code C$ForyLayer$2} - for C's layer fields
 * </ul>
 *
 * @see MetaSharedLayerSerializer
 */
public interface CurrentLayerMarker {

  /** Returns the target class this marker represents. */
  Class<?> getTargetClass();

  /** Returns the layer index in the class hierarchy (0 = topmost parent). */
  int getLayerIndex();
}
