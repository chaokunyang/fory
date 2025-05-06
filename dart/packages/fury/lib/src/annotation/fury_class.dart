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

import 'package:meta/meta_meta.dart';
import 'package:fury/src/annotation/fury_object.dart';

/// An annotation that provides Fury serialization and deserialization support for classes.
/// 
/// Apply this annotation to classes that need to be serialized/deserialized by Fury.
/// By default, Fury will use available parameterless constructors for object creation
/// during deserialization.
@Target({TargetKind.classType})
class FuryClass extends FuryObject{
  static const String name = 'FuryClass';
  static const List<TargetKind> targets = [TargetKind.classType];

  /// Indicates whether the class promises to be acyclic (contains no circular references).
  ///
  /// When set to true, Fury can optimize serialization by skipping circular reference checks.
  /// Note: Even with this set to true, Fury will still use an available parameterless 
  /// constructor if one exists.
  final bool promiseAcyclic;

  /// Creates a FuryClass annotation.
  /// 
  /// [promiseAcyclic] - Set to true if the class guarantees no circular references.
  const FuryClass({this.promiseAcyclic = false});
}

/// A convenience constant instance of [FuryClass] with default values.
const FuryClass furyClass = FuryClass();
