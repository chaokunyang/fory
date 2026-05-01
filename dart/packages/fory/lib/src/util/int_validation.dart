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

@pragma('vm:prefer-inline')
int checkedInt8(int value) {
  if (value < -128 || value > 127) {
    throw RangeError.range(value, -128, 127, 'value');
  }
  return value;
}

@pragma('vm:prefer-inline')
int checkedInt16(int value) {
  if (value < -32768 || value > 32767) {
    throw RangeError.range(value, -32768, 32767, 'value');
  }
  return value;
}

@pragma('vm:prefer-inline')
int checkedInt32(int value) {
  if (value < -2147483648 || value > 2147483647) {
    throw RangeError.range(value, -2147483648, 2147483647, 'value');
  }
  return value;
}

@pragma('vm:prefer-inline')
int checkedUint8(int value) {
  if (value < 0 || value > 255) {
    throw RangeError.range(value, 0, 255, 'value');
  }
  return value;
}

@pragma('vm:prefer-inline')
int checkedUint16(int value) {
  if (value < 0 || value > 65535) {
    throw RangeError.range(value, 0, 65535, 'value');
  }
  return value;
}

@pragma('vm:prefer-inline')
int checkedUint32(int value) {
  if (value < 0 || value > 4294967295) {
    throw RangeError.range(value, 0, 4294967295, 'value');
  }
  return value;
}
