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

import 'dart:collection';

import 'package:fory/src/types/int64.dart';

const int _typeMetaHashLow32Mask = 0xfffff000;

LinkedHashMap<Int64, V> createParsedTypeMetaEntries<V>() =>
    LinkedHashMap<Int64, V>(
      equals: _typeMetaHeadersEqual,
      hashCode: _typeMetaHeaderHashCode,
    );

bool _typeMetaHeadersEqual(Int64 left, Int64 right) =>
    left.high32Unsigned == right.high32Unsigned &&
    (left.low32 & _typeMetaHashLow32Mask) ==
        (right.low32 & _typeMetaHashLow32Mask);

int _typeMetaHeaderHashCode(Int64 header) =>
    header.high32Unsigned ^ (header.low32 & _typeMetaHashLow32Mask);
