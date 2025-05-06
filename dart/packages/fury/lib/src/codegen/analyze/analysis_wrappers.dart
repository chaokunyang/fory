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
import 'package:analyzer/dart/element/type.dart';

typedef TypeDecision = ({InterfaceType type, bool forceNullable});

class ObjTypeWrapper{
  static const namedEnum = ObjTypeWrapper(ObjType.NAMED_ENUM, true,);
  static const namedStruct = ObjTypeWrapper(ObjType.NAMED_STRUCT, false);
  static const unknownStruct = ObjTypeWrapper(ObjType.UNKNOWN_YET, false);

  final ObjType objType; // null means unsupported
  final bool certainForSer;

  const ObjTypeWrapper(this.objType, this.certainForSer);
}