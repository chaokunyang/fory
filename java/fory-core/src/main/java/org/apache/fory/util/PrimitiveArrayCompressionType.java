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

package org.apache.fory.util;

/**
 * Compression types for primitive arrays.
 *
 * <p>These types are used by the optional compressed array serializers when array values can be
 * stored with a narrower primitive type.
 */
public enum PrimitiveArrayCompressionType {
  NONE(0),
  INT_TO_BYTE(1),
  INT_TO_SHORT(2),
  LONG_TO_INT(3);

  private final int value;

  PrimitiveArrayCompressionType(int value) {
    this.value = value;
  }

  public int getValue() {
    return value;
  }

  public static PrimitiveArrayCompressionType fromValue(int value) {
    switch (value) {
      case 0:
        return NONE;
      case 1:
        return INT_TO_BYTE;
      case 2:
        return INT_TO_SHORT;
      case 3:
        return LONG_TO_INT;
      default:
        throw new IllegalArgumentException("Unknown compression type value: " + value);
    }
  }

  public static final class IntArrayCompression {
    private IntArrayCompression() {}

    public static PrimitiveArrayCompressionType determine(int[] array) {
      return ArrayCompressionUtils.determineIntCompressionType(array);
    }

    public static boolean isSupported(PrimitiveArrayCompressionType type) {
      return type == NONE || type == INT_TO_BYTE || type == INT_TO_SHORT;
    }
  }

  public static final class LongArrayCompression {
    private LongArrayCompression() {}

    public static PrimitiveArrayCompressionType determine(long[] array) {
      return ArrayCompressionUtils.determineLongCompressionType(array);
    }

    public static boolean isSupported(PrimitiveArrayCompressionType type) {
      return type == NONE || type == LONG_TO_INT;
    }
  }
}
