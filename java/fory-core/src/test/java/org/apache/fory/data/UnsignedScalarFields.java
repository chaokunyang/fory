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

package org.apache.fory.data;

import org.apache.fory.annotation.Uint16Type;
import org.apache.fory.annotation.Uint32Type;
import org.apache.fory.annotation.Uint64Type;
import org.apache.fory.annotation.Uint8Type;
import org.apache.fory.config.LongEncoding;

/** Test class with all unsigned integer scalar fields. */
public class UnsignedScalarFields {
  @Uint8Type public byte u8;

  @Uint16Type public short u16;

  @Uint32Type(compress = false)
  public int u32;

  @Uint32Type(compress = true)
  public int u32Var;

  @Uint64Type(encoding = LongEncoding.FIXED)
  public long u64;

  @Uint64Type(encoding = LongEncoding.VARINT)
  public long u64Var;

  @Uint64Type(encoding = LongEncoding.TAGGED)
  public long u64Tagged;
}
