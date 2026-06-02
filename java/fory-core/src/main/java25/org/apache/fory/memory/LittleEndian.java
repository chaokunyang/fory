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

package org.apache.fory.memory;

public class LittleEndian {
  public static int putVarUint36Small(byte[] arr, int index, long v) {
    if (v >>> 7 == 0) {
      arr[index] = (byte) v;
      return 1;
    }
    if (v >>> 14 == 0) {
      arr[index++] = (byte) ((v & 0x7F) | 0x80);
      arr[index] = (byte) (v >>> 7);
      return 2;
    }
    return bigWriteUint36(arr, index, v);
  }

  private static int bigWriteUint36(byte[] arr, int index, long v) {
    if (v >>> 21 == 0) {
      arr[index++] = (byte) ((v & 0x7F) | 0x80);
      arr[index++] = (byte) (v >>> 7 | 0x80);
      arr[index] = (byte) (v >>> 14);
      return 3;
    }
    if (v >>> 28 == 0) {
      arr[index++] = (byte) ((v & 0x7F) | 0x80);
      arr[index++] = (byte) (v >>> 7 | 0x80);
      arr[index++] = (byte) (v >>> 14 | 0x80);
      arr[index] = (byte) (v >>> 21);
      return 4;
    }
    arr[index++] = (byte) ((v & 0x7F) | 0x80);
    arr[index++] = (byte) (v >>> 7 | 0x80);
    arr[index++] = (byte) (v >>> 14 | 0x80);
    arr[index++] = (byte) (v >>> 21 | 0x80);
    arr[index] = (byte) (v >>> 28);
    return 5;
  }

  public static long getInt64(byte[] o, int index) {
    return ((long) o[index] & 0xff)
        | (((long) o[index + 1] & 0xff) << 8)
        | (((long) o[index + 2] & 0xff) << 16)
        | (((long) o[index + 3] & 0xff) << 24)
        | (((long) o[index + 4] & 0xff) << 32)
        | (((long) o[index + 5] & 0xff) << 40)
        | (((long) o[index + 6] & 0xff) << 48)
        | (((long) o[index + 7] & 0xff) << 56);
  }

  public static void putInt64(byte[] o, int index, long value) {
    o[index] = (byte) value;
    o[index + 1] = (byte) (value >>> 8);
    o[index + 2] = (byte) (value >>> 16);
    o[index + 3] = (byte) (value >>> 24);
    o[index + 4] = (byte) (value >>> 32);
    o[index + 5] = (byte) (value >>> 40);
    o[index + 6] = (byte) (value >>> 48);
    o[index + 7] = (byte) (value >>> 56);
  }
}
