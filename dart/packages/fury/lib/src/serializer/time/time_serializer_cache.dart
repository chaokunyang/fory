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

import 'package:fury/src/config/fury_config.dart';
import 'package:fury/src/serializer/serializer.dart';
import 'package:fury/src/serializer/serializer_cache.dart';

abstract base class TimeSerializerCache extends SerializerCache{

  const TimeSerializerCache();

  @override
  Serializer getSerializer(FuryConfig conf, [Type? type]){
    // Currently, there are only two types of Serialization for primitive types:
    // with ref and without ref. So only these two are cached here.
    bool writeRef = conf.refTracking && !conf.timeRefIgnored;
    return getSerWithRef(writeRef);
  }

  Serializer getSerWithRef(bool writeRef);
}
