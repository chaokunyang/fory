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

import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

final class VectorArrayCompression implements ArrayCompressionUtils.CompressionSupport {
  private static final VectorSpecies<Integer> INT_SPECIES = IntVector.SPECIES_PREFERRED;
  private static final VectorSpecies<Long> LONG_SPECIES = LongVector.SPECIES_PREFERRED;

  @Override
  public PrimitiveArrayCompressionType determineIntCompressionType(int[] array) {
    boolean canCompressToByte = true;
    boolean canCompressToShort = true;
    int i = 0;
    int upperBound = INT_SPECIES.loopBound(array.length);
    for (; i < upperBound && (canCompressToByte || canCompressToShort); i += INT_SPECIES.length()) {
      IntVector vector = IntVector.fromArray(INT_SPECIES, array, i);
      if (canCompressToByte) {
        if (vector.compare(VectorOperators.GT, Byte.MAX_VALUE).anyTrue()
            || vector.compare(VectorOperators.LT, Byte.MIN_VALUE).anyTrue()) {
          canCompressToByte = false;
        }
      }
      if (canCompressToShort) {
        if (vector.compare(VectorOperators.GT, Short.MAX_VALUE).anyTrue()
            || vector.compare(VectorOperators.LT, Short.MIN_VALUE).anyTrue()) {
          canCompressToShort = false;
        }
      }
    }
    for (; i < array.length && (canCompressToByte || canCompressToShort); i++) {
      int value = array[i];
      if (canCompressToByte && (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE)) {
        canCompressToByte = false;
      }
      if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
        canCompressToShort = false;
      }
    }
    if (canCompressToByte) {
      return PrimitiveArrayCompressionType.INT_TO_BYTE;
    }
    return canCompressToShort
        ? PrimitiveArrayCompressionType.INT_TO_SHORT
        : PrimitiveArrayCompressionType.NONE;
  }

  @Override
  public PrimitiveArrayCompressionType determineLongCompressionType(long[] array) {
    int i = 0;
    int upperBound = LONG_SPECIES.loopBound(array.length);
    for (; i < upperBound; i += LONG_SPECIES.length()) {
      LongVector vector = LongVector.fromArray(LONG_SPECIES, array, i);
      if (vector.compare(VectorOperators.GT, Integer.MAX_VALUE).anyTrue()
          || vector.compare(VectorOperators.LT, Integer.MIN_VALUE).anyTrue()) {
        return PrimitiveArrayCompressionType.NONE;
      }
    }
    for (; i < array.length; i++) {
      long value = array[i];
      if (value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
        return PrimitiveArrayCompressionType.NONE;
      }
    }
    return PrimitiveArrayCompressionType.LONG_TO_INT;
  }
}
