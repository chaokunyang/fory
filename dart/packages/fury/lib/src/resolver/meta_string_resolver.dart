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

import 'package:fury/src/memory/byte_reader.dart';
import 'package:fury/src/meta/meta_string.dart';
import 'package:fury/src/meta/meta_string_byte.dart';
import 'package:fury/src/resolver/impl/meta_string_resolver_impl.dart';
import 'package:fury/src/resolver/meta_string_handler.dart';

abstract class MetaStringResolver extends MataStringHandler{
  const MetaStringResolver();
  static MetaStringResolver get newInst => MetaStringResolverImpl();

  MetaStringBytes readMetaStringBytes(ByteReader reader);
  MetaStringBytes getOrCreateMetaStringBytes(MetaString mstr);
  
  String decodeNamespace(MetaStringBytes msb);
  String decodeTypename(MetaStringBytes msb);
}