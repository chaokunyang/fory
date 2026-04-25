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

import 'type_spec.dart';

/// Field-level code generation options for [ForyStruct].
final class ForyField {
  /// Skips this field entirely in generated serializers.
  final bool skip;

  /// Stable field identifier used by compatible structs.
  ///
  /// When omitted, the generator derives an identifier from the field name.
  final int? id;

  /// Canonical recursive field-type override.
  final TypeSpec? type;

  /// Root-only sugar for overriding inferred nullability.
  final bool? nullable;

  /// Root-only sugar for overriding reference tracking.
  final bool? ref;

  /// Root-only sugar for overriding runtime type metadata behavior.
  final bool? dynamic;

  /// Root-only sugar for numeric encodings when the declared field type is
  /// already unambiguous.
  final Encoding? encoding;

  const ForyField({
    this.skip = false,
    this.id,
    this.type,
    this.nullable,
    this.ref,
    this.dynamic,
    this.encoding,
  });
}

final class ListField {
  final TypeSpec element;
  final bool? nullable;
  final bool? ref;
  final bool? dynamic;

  const ListField({
    this.element = const DeclaredType(),
    this.nullable,
    this.ref,
    this.dynamic,
  });
}

final class SetField {
  final TypeSpec element;
  final bool? nullable;
  final bool? ref;
  final bool? dynamic;

  const SetField({
    this.element = const DeclaredType(),
    this.nullable,
    this.ref,
    this.dynamic,
  });
}

final class MapField {
  final TypeSpec key;
  final TypeSpec value;
  final bool? nullable;
  final bool? ref;
  final bool? dynamic;

  const MapField({
    this.key = const DeclaredType(),
    this.value = const DeclaredType(),
    this.nullable,
    this.ref,
    this.dynamic,
  });
}
