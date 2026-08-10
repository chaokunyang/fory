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

package org.apache.fory.collection;

import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.fory.util.MurmurHash3;

/**
 * Construction-only seed source for maps containing input-derived metadata.
 *
 * <p>The sequence starts from process entropy and advances through the full {@code long} space
 * before mixing. Map seeds remain private, so input-selected keys cannot predict bucket placement.
 * This keeps seed creation explicit and independent of per-thread runtime state.
 */
final class MetadataHashSeed {
  private static final long SEED_INCREMENT = 0x9E3779B97F4A7C15L;
  private static final AtomicLong SEED_SEQUENCE = new AtomicLong(new SecureRandom().nextLong());

  private MetadataHashSeed() {}

  static long next() {
    return MurmurHash3.fmix64(SEED_SEQUENCE.getAndAdd(SEED_INCREMENT));
  }
}
