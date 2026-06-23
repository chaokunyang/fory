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

package org.apache.fory.json.reader;

final class ByteReaderUtils {
  private ByteReaderUtils() {}

  static boolean fieldTokenSuffixMatches(byte[] bytes, int offset, int suffix, int suffixLength) {
    switch (suffixLength) {
      case 0:
        return true;
      case 1:
        return offset < bytes.length && (bytes[offset] & 0xFF) == suffix;
      case 2:
        return offset + 1 < bytes.length
            && ((bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8)) == suffix;
      case 3:
        return offset + 2 < bytes.length
            && ((bytes[offset] & 0xFF)
                    | ((bytes[offset + 1] & 0xFF) << 8)
                    | ((bytes[offset + 2] & 0xFF) << 16))
                == suffix;
      default:
        throw new IllegalArgumentException("Unsupported field token suffix length " + suffixLength);
    }
  }
}
