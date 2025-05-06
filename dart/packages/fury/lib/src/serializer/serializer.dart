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

import 'package:fury/src/const/obj_type.dart';
import 'package:fury/src/deserializer_pack.dart';
import 'package:fury/src/memory/byte_reader.dart';
import 'package:fury/src/memory/byte_writer.dart';
import 'package:fury/src/serializer_pack.dart';

/// Planned to only handle non-null type serializers. Null values should be handled externally.
abstract base class Serializer<T> {

  final ObjType objType;
  // final bool forceNoRefWrite; // Not controlled by Fury's Config; indicates no reference writing for this type
  final bool writeRef; // Indicates whether to write references

  const Serializer(
    this.objType,
    this.writeRef,
    // [this.forceNoRefWrite = false]
  );
  T read(ByteReader br, int refId, DeserializerPack pack);

  void write(ByteWriter bw, T v, SerializerPack pack);

  String get tag => throw UnimplementedError('tag is not implemented');
}
