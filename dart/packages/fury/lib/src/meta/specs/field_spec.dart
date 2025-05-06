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

import 'package:meta/meta.dart';
import 'package:fury/src/meta/specs/type_spec.dart';

typedef Getter = Object? Function(Object inst);
typedef Setter = void Function(Object inst, dynamic value);

@immutable
class FieldSpec{
  final String name;
  final TypeSpec typeSpec;
  final Getter? getter;
  final Setter? setter;

  final bool includeFromFury;
  final bool includeToFury;
  
  const FieldSpec(
    this.name,
    this.typeSpec,
    this.includeFromFury,
    this.includeToFury,
    this.getter,
    this.setter,
  );

  /// Regarding the == comparison of Function, besides static methods which can be directly compared using ==,
  /// it is difficult to compare whether the functions are the same. So for testing purposes, we use a simplified
  /// comparison by checking for null.
  @override
  bool operator ==(Object other) {
    return identical(this, other) ||
      (other is FieldSpec &&
        runtimeType == other.runtimeType &&
        name == other.name &&
        typeSpec == other.typeSpec &&
        includeFromFury == other.includeFromFury &&
        includeToFury == other.includeToFury &&
        (identical(getter, other.getter) || (getter == null) == (other.getter == null)) &&
        (identical(setter, other.setter) || (setter == null) == (other.setter == null))
      );
  }
}
