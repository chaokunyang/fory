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
import 'package:fury/src/const/obj_type.dart';
import 'package:fury/src/dev_annotation/optimize.dart';
import 'package:fury/src/serializer/array_serializer.dart';
import 'package:fury/src/serializer/serializer_cache.dart';

final class _Uint8ListSerializerCache extends ArraySerializerCache {

  static Uint8ListSerializer? _noRefSer;
  static Uint8ListSerializer? _writeRefSer;

  const _Uint8ListSerializerCache();

  @override
  Uint8ListSerializer getSerWithRef(bool writeRef) {
    if (writeRef) {
      _writeRefSer ??= Uint8ListSerializer(true);
      return _writeRefSer!;
    } else {
      _noRefSer ??= Uint8ListSerializer(false);
      return _noRefSer!;
    }
  }
}

final class Uint8ListSerializer extends NumericArraySerializer<int> {
  static const SerializerCache cache = _Uint8ListSerializerCache();

  const Uint8ListSerializer(bool writeRef) : super(ObjType.BINARY, writeRef);

  @inline
  @override
  TypedDataList<int> readToList(Uint8List copiedMem) {
    // need copy
    return copiedMem;
  }

  @override
  int get bytesPerNum => 1;
}

final class _Int8ListSerializerCache extends ArraySerializerCache{
  static Int8ListSerializer? _noRefSer;
  static Int8ListSerializer? _writeRefSer;

  const _Int8ListSerializerCache();

  @override
  Int8ListSerializer getSerWithRef(bool writeRef) {
    if (writeRef) {
      _writeRefSer ??= Int8ListSerializer(true);
      return _writeRefSer!;
    } else {
      _noRefSer ??= Int8ListSerializer(false);
      return _noRefSer!;
    }
  }
}

final class Int8ListSerializer extends NumericArraySerializer<int> {
  static const SerializerCache cache = _Int8ListSerializerCache();
  const Int8ListSerializer(bool writeRef) : super(ObjType.INT8_ARRAY, writeRef);

  @override
  TypedDataList<int> readToList(Uint8List copiedMem) {
    // need copy
    Int8List list = copiedMem.buffer.asInt8List();
    return list;
  }

  @override
  int get bytesPerNum => 1;
}

final class _Int16ListSerializerCache extends ArraySerializerCache{
  static Int16ListSerializer? _noRefSer;
  static Int16ListSerializer? _writeRefSer;

  const _Int16ListSerializerCache();

  @override
  Int16ListSerializer getSerWithRef(bool writeRef) {
    if (writeRef) {
      _writeRefSer ??= Int16ListSerializer(true);
      return _writeRefSer!;
    } else {
      _noRefSer ??= Int16ListSerializer(false);
      return _noRefSer!;
    }
  }
}

final class Int16ListSerializer extends NumericArraySerializer<int> {
  static const SerializerCache cache = _Int16ListSerializerCache();
  const Int16ListSerializer(bool writeRef) : super(ObjType.INT16_ARRAY, writeRef);

  @override
  TypedDataList<int> readToList(Uint8List copiedMem) {
    // need copy
    Int16List list = copiedMem.buffer.asInt16List();
    return list;
  }

  @override
  int get bytesPerNum => 2;
}

final class _Int32ListSerializerCache extends ArraySerializerCache{
  static Int32ListSerializer? _noRefSer;
  static Int32ListSerializer? _writeRefSer;

  const _Int32ListSerializerCache();

  @override
  Int32ListSerializer getSerWithRef(bool writeRef) {
    if (writeRef) {
      _writeRefSer ??= Int32ListSerializer(true);
      return _writeRefSer!;
    } else {
      _noRefSer ??= Int32ListSerializer(false);
      return _noRefSer!;
    }
  }
}

final class Int32ListSerializer extends NumericArraySerializer<int> {
  static const SerializerCache cache = _Int32ListSerializerCache();
  const Int32ListSerializer(bool writeRef) : super(ObjType.INT32_ARRAY, writeRef);

  @override
  TypedDataList<int> readToList(Uint8List copiedMem) {
    // need copy
    Int32List list = copiedMem.buffer.asInt32List();
    return list;
  }

  @override
  int get bytesPerNum => 4;
}

final class _Float32ListSerializerCache extends ArraySerializerCache{
  static Float32ListSerializer? _noRefSer;
  static Float32ListSerializer? _writeRefSer;

  const _Float32ListSerializerCache();

  @override
  Float32ListSerializer getSerWithRef(bool writeRef) {
    if (writeRef) {
      _writeRefSer ??= Float32ListSerializer(true);
      return _writeRefSer!;
    } else {
      _noRefSer ??= Float32ListSerializer(false);
      return _noRefSer!;
    }
  }
}

final class _Int64ListSerializerCache extends ArraySerializerCache{
  static Int64ListSerializer? _noRefSer;
  static Int64ListSerializer? _writeRefSer;

  const _Int64ListSerializerCache();

  @override
  Int64ListSerializer getSerWithRef(bool writeRef) {
    if (writeRef) {
      _writeRefSer ??= Int64ListSerializer(true);
      return _writeRefSer!;
    } else {
      _noRefSer ??= Int64ListSerializer(false);
      return _noRefSer!;
    }
  }
}

final class Int64ListSerializer extends NumericArraySerializer<int> {
  static const SerializerCache cache = _Int64ListSerializerCache();
  const Int64ListSerializer(bool writeRef) : super(ObjType.INT64_ARRAY, writeRef);

  @override
  TypedDataList<int> readToList(Uint8List copiedMem) {
    // need copy
    Int64List list = copiedMem.buffer.asInt64List();
    return list;
  }

  @override
  int get bytesPerNum => 8;
}

final class Float32ListSerializer extends NumericArraySerializer<double> {
  static const SerializerCache cache = _Float32ListSerializerCache();

  const Float32ListSerializer(bool writeRef) : super(ObjType.FLOAT32_ARRAY, writeRef);

  @override
  TypedDataList<double> readToList(Uint8List copiedMem) {
    // need copy
    Float32List list = copiedMem.buffer.asFloat32List();
    return list;
  }
  @override
  int get bytesPerNum => 4;
}

final class _Float64ListSerializerCache extends ArraySerializerCache{
  static Float64ListSerializer? _noRefSer;
  static Float64ListSerializer? _writeRefSer;

  const _Float64ListSerializerCache();

  @override
  Float64ListSerializer getSerWithRef(bool writeRef) {
    if (writeRef) {
      _writeRefSer ??= Float64ListSerializer(true);
      return _writeRefSer!;
    } else {
      _noRefSer ??= Float64ListSerializer(false);
      return _noRefSer!;
    }
  }
}

final class Float64ListSerializer extends NumericArraySerializer<double> {
  static const SerializerCache cache = _Float64ListSerializerCache();
  const Float64ListSerializer(bool writeRef) : super(ObjType.FLOAT64_ARRAY, writeRef);

  @override
  TypedDataList<double> readToList(Uint8List copiedMem) {
    // need copy
    Float64List list = copiedMem.buffer.asFloat64List();
    return list;
  }

  @override
  int get bytesPerNum => 8;
}