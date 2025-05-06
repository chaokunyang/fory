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

import 'dart:typed_data';
import 'package:collection/collection.dart';
import 'package:fury/src/const/obj_type.dart';
import 'package:fury/src/serializer/array_serializer.dart';
import 'package:fury/src/deserializer_pack.dart';
import 'package:fury/src/memory/byte_reader.dart';
import 'package:fury/src/memory/byte_writer.dart';
import 'package:fury/src/serializer/serializer_cache.dart';
import 'package:fury/src/serializer_pack.dart';

final class _BoolListSerializerCache extends ArraySerializerCache {
  static BoolListSerializer? _noRefSer;
  static BoolListSerializer? _writeRefSer;

  const _BoolListSerializerCache();

  @override
  BoolListSerializer getSerWithRef(bool writeRef) {
    if (writeRef) {
      _writeRefSer ??= BoolListSerializer(true);
      return _writeRefSer!;
    } else {
      _noRefSer ??= BoolListSerializer(false);
      return _noRefSer!;
    }
  }
}

final class BoolListSerializer extends ArraySerializer<bool>{
  static const SerializerCache cache = _BoolListSerializerCache();
  const BoolListSerializer(bool writeRef) : super(ObjType.BOOL_ARRAY, writeRef);

  @override
  BoolList read(ByteReader br, int refId, DeserializerPack pack){
    int num = br.readVarUint32Small7();
    BoolList list = BoolList(num);
    Uint8List bytes = br.readBytesView(num);
    for (int i = 0; i < num; ++i) {
      list[i] = bytes[i] != 0;
    }
    return list;
  }

  @override
  void write(ByteWriter bw, covariant BoolList v, SerializerPack pack) {
    bw.writeVarUint32(v.length);
    Uint8List bytes = Uint8List(v.length);
    for (int i = 0; i < v.length; ++i) {
      bytes[i] = v[i] ? 1 : 0;
    }
    bw.writeBytes(bytes);
  }
}