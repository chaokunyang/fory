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

"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const { ReadContext } = require("../dist/lib/context");
const { FieldInfo, TypeMeta } = require("../dist/lib/meta/TypeMeta");
const { TypeId } = require("../dist/lib/type");

function context() {
  return new ReadContext(
    {},
    {
      compatible: true,
      maxAverageSchemaVersionsPerType: 3,
      maxSchemaVersionsPerType: 1,
      useSliceString: false,
    },
  );
}

function remoteStruct(name, fieldName) {
  return new TypeMeta([new FieldInfo(fieldName, TypeId.INT32)], {
    namespace: "example",
    typeId: TypeId.NAMED_STRUCT,
    typeName: name,
    userTypeId: -1,
  });
}

function readTypeMeta(readContext, typeMeta) {
  const encoded = typeMeta.toBytes();
  const bytes = new Uint8Array(encoded.length + 1);
  bytes[0] = 0;
  bytes.set(encoded, 1);
  readContext.reset(bytes);
  return readContext.readTypeMeta();
}

test("remote schema limit rejects extra versions", () => {
  const readContext = context();
  readTypeMeta(readContext, remoteStruct("Shared", "first"));
  assert.throws(
    () => readTypeMeta(readContext, remoteStruct("Shared", "second")),
    /maxSchemaVersionsPerType/,
  );
});

test("remote schema limit keeps unknown structs separate", () => {
  const readContext = context();
  assert.equal(
    readTypeMeta(readContext, remoteStruct("UnknownA", "value")).getTypeName(),
    "UnknownA",
  );
  assert.equal(
    readTypeMeta(readContext, remoteStruct("UnknownB", "value")).getTypeName(),
    "UnknownB",
  );
});
