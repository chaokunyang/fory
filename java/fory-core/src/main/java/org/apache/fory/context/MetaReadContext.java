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

package org.apache.fory.context;

import org.apache.fory.collection.ObjectArray;
import org.apache.fory.resolver.TypeInfo;

/**
 * Read-side state for meta-share deserialization.
 *
 * <p>When scoped meta share is disabled, the same instance can be reused across multiple reads so
 * type definitions announced by the peer remain available for later payloads.
 */
public class MetaReadContext {
  private static final int MAX_RETAINED_TYPE_INFOS = 8192;
  private static final int RESET_TYPE_INFO_CAPACITY = 8;

  /**
   * Type infos announced by the peer, indexed by the protocol id assigned during the current or
   * shared meta-share session.
   */
  public final ObjectArray<TypeInfo> readTypeInfos = new ObjectArray<>();

  void reset() {
    ObjectArray<TypeInfo> typeInfos = readTypeInfos;
    int size = typeInfos.size;
    // The current size is the protocol visibility boundary, so stale slots cannot be referenced
    // by a later root. Keep bounded tables intact to make normal root cleanup allocation-free, and
    // discard only an oversized backing array retained by an unusually metadata-heavy root.
    typeInfos.size = 0;
    if (size > MAX_RETAINED_TYPE_INFOS) {
      typeInfos.objects = new Object[RESET_TYPE_INFO_CAPACITY];
    }
  }
}
