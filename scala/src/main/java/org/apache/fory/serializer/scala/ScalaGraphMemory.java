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

import org.apache.fory.reflect.ReflectionUtils;
import org.apache.fory.serializer.GraphMemoryEstimates;

/** Portable lower-bound estimates for retained owners materialized by Scala serializers. */
final class ScalaGraphMemory {
  static final int REFERENCE_BYTES = GraphMemoryEstimates.REFERENCE_BYTES;
  static final int LONG_BYTES = Long.BYTES;
  static final int SOME_BYTES = GraphMemoryEstimates.shallowObjectBytes(scala.Some.class);
  static final int LIST_NODE_BYTES =
      GraphMemoryEstimates.shallowObjectBytes(scala.collection.immutable.$colon$colon.class);
  static final int LIST_MAP_NODE_BYTES =
      GraphMemoryEstimates.shallowObjectBytes(
          ReflectionUtils.loadClass("scala.collection.immutable.ListMap$Node"));
  static final int BIT_SET_BYTES =
      GraphMemoryEstimates.shallowObjectBytes(scala.collection.mutable.BitSet.class);
  static final int ARRAY_OWNER_BYTES = GraphMemoryEstimates.objectArrayBytes();

  private ScalaGraphMemory() {}
}
