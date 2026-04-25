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

final class Int8Type extends TypeSpec {
  const Int8Type({
    super.nullable,
    super.ref,
    super.dynamic,
  });
}

final class Int16Type extends TypeSpec {
  const Int16Type({
    super.nullable,
    super.ref,
    super.dynamic,
  });
}

final class Int32Type extends TypeSpec {
  final Encoding encoding;

  const Int32Type({
    this.encoding = Encoding.varint,
    super.nullable,
    super.ref,
    super.dynamic,
  });
}

final class Int64Type extends TypeSpec {
  final Encoding encoding;

  const Int64Type({
    this.encoding = Encoding.varint,
    super.nullable,
    super.ref,
    super.dynamic,
  });
}

final class Uint8Type extends TypeSpec {
  const Uint8Type({
    super.nullable,
    super.ref,
    super.dynamic,
  });
}

final class Uint16Type extends TypeSpec {
  const Uint16Type({
    super.nullable,
    super.ref,
    super.dynamic,
  });
}

final class Uint32Type extends TypeSpec {
  final Encoding encoding;

  const Uint32Type({
    this.encoding = Encoding.varint,
    super.nullable,
    super.ref,
    super.dynamic,
  });
}

final class Uint64Type extends TypeSpec {
  final Encoding encoding;

  const Uint64Type({
    this.encoding = Encoding.varint,
    super.nullable,
    super.ref,
    super.dynamic,
  });
}
