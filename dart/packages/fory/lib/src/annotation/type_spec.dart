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

/// Recursive field-type specifications for generated serializers.
library;

enum Encoding {
  fixed,
  varint,
  tagged,
}

abstract class TypeSpec {
  final bool? nullable;
  final bool? ref;
  final bool? dynamic;

  const TypeSpec({
    this.nullable,
    this.ref,
    this.dynamic,
  });
}

/// Uses the declared Dart type at this node and only overrides field flags.
final class DeclaredType extends TypeSpec {
  const DeclaredType({
    super.nullable,
    super.ref,
    super.dynamic,
  });
}

final class BoolType extends TypeSpec {
  const BoolType({
    super.nullable,
    super.ref,
    super.dynamic,
  });
}

final class StringType extends TypeSpec {
  const StringType({
    super.nullable,
    super.ref,
    super.dynamic,
  });
}

final class BinaryType extends TypeSpec {
  const BinaryType({
    super.nullable,
    super.ref,
    super.dynamic,
  });
}

final class DecimalType extends TypeSpec {
  const DecimalType({
    super.nullable,
    super.ref,
    super.dynamic,
  });
}

final class DateType extends TypeSpec {
  const DateType({
    super.nullable,
    super.ref,
    super.dynamic,
  });
}

final class DurationType extends TypeSpec {
  const DurationType({
    super.nullable,
    super.ref,
    super.dynamic,
  });
}

final class TimestampType extends TypeSpec {
  const TimestampType({
    super.nullable,
    super.ref,
    super.dynamic,
  });
}

/// Distinguishes `uint8[]` typed arrays from binary `Uint8List` payloads.
final class Uint8ArrayType extends TypeSpec {
  const Uint8ArrayType({
    super.nullable,
    super.ref,
    super.dynamic,
  });
}

final class ListType extends TypeSpec {
  final TypeSpec element;

  const ListType({
    this.element = const DeclaredType(),
    super.nullable,
    super.ref,
    super.dynamic,
  });
}

final class SetType extends TypeSpec {
  final TypeSpec element;

  const SetType({
    this.element = const DeclaredType(),
    super.nullable,
    super.ref,
    super.dynamic,
  });
}

final class MapType extends TypeSpec {
  final TypeSpec key;
  final TypeSpec value;

  const MapType({
    this.key = const DeclaredType(),
    this.value = const DeclaredType(),
    super.nullable,
    super.ref,
    super.dynamic,
  });
}
