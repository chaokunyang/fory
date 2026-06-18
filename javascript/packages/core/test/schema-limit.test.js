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
const runTest =
  typeof globalThis.expect === "function" && typeof globalThis.test === "function"
    ? globalThis.test
    : require("node:test").test;
const { ReadContext } = require("../dist/lib/context");
const { AnyHelper } = require("../dist/lib/gen/any");
const { FieldInfo, TypeMeta } = require("../dist/lib/meta/TypeMeta");
const { TypeId } = require("../dist/lib/type");
const { Type } = require("../dist/lib/typeInfo");

function context(typeResolver = {}, config = {}) {
  return new ReadContext(
    typeResolver,
    {
      compatible: true,
      maxTypeFields: 512,
      maxTypeMetaBytes: 4096,
      maxAverageSchemaVersionsPerType: 3,
      maxSchemaVersionsPerType: 1,
      useSliceString: false,
      ...config,
    },
  );
}

function remoteStruct(
  name,
  fieldName,
  fieldType = Type.int32({ encoding: "fixed" }),
  typeId = TypeId.NAMED_STRUCT,
  userTypeId = -1,
) {
  return new TypeMeta([new FieldInfo(
    fieldName,
    fieldType.typeId,
    fieldType.userTypeId,
    fieldType.trackingRef === true,
    fieldType.nullable === true,
    fieldType.options,
  )], {
    namespace: "example",
    typeId,
    typeName: name,
    userTypeId,
  });
}

function anyStruct(fieldName, fieldType = Type.int32({ encoding: "fixed" })) {
  return remoteStruct("", fieldName, fieldType, TypeId.COMPATIBLE_STRUCT, 901);
}

function readTypeMeta(readContext, typeMeta) {
  const encoded = typeMeta.toBytes();
  const bytes = new Uint8Array(encoded.length + 1);
  bytes[0] = 0;
  bytes.set(encoded, 1);
  readContext.reset(bytes);
  return readContext.readTypeMeta();
}

function readChangedTypeMeta(readContext, expectedHash, original, typeMeta) {
  const encoded = typeMeta.toBytes();
  const bytes = new Uint8Array(encoded.length + 1);
  bytes[0] = 0;
  bytes.set(encoded, 1);
  readContext.reset(bytes);
  return readContext.readTypeMetaIfSchemaChanged(expectedHash, original);
}

function detectAnySerializer(readContext, typeMeta) {
  const encoded = typeMeta.toBytes();
  const bytes = new Uint8Array(encoded.length + 2);
  bytes[0] = TypeId.COMPATIBLE_STRUCT;
  bytes[1] = 0;
  bytes.set(encoded, 2);
  readContext.reset(bytes);
  return AnyHelper.detectSerializer(readContext);
}

runTest("remote schema limit rejects extra versions", () => {
  const readContext = context();
  readTypeMeta(readContext, remoteStruct("Shared", "first"));
  assert.throws(
    () => readTypeMeta(readContext, remoteStruct("Shared", "second")),
    /maxSchemaVersionsPerType/,
  );
});

runTest("TypeMeta field limit rejects large struct metadata", () => {
  const readContext = context({}, { maxTypeFields: 1 });
  const fieldType = Type.int32({ encoding: "fixed" });
  const typeMeta = new TypeMeta([
    new FieldInfo("first", fieldType.typeId, fieldType.userTypeId, false, false, fieldType.options),
    new FieldInfo("second", fieldType.typeId, fieldType.userTypeId, false, false, fieldType.options),
  ], {
    namespace: "example",
    typeId: TypeId.NAMED_STRUCT,
    typeName: "TooManyFields",
    userTypeId: -1,
  });

  assert.throws(() => readTypeMeta(readContext, typeMeta), /maxTypeFields/);
});

runTest("TypeMeta body limit rejects large metadata", () => {
  const readContext = context({}, { maxTypeMetaBytes: 1 });

  assert.throws(
    () => readTypeMeta(readContext, remoteStruct("LargeMeta", "value")),
    /maxTypeMetaBytes/,
  );
});

runTest("failed compatible TypeMeta does not consume schema limit", () => {
  const localTypeInfo = Type.struct(
    { namespace: "example", typeName: "Shared" },
    { value: Type.int32({ encoding: "fixed" }) },
  );
  const original = {
    getTypeInfo() {
      return localTypeInfo;
    },
  };
  const readContext = context({
    computeTypeId(typeInfo) {
      return typeInfo.typeId;
    },
    getSerializerById() {
      return undefined;
    },
    generateReadSerializer(typeInfo) {
      return {
        getTypeInfo() {
          return typeInfo;
        },
      };
    },
  });
  const localHash = TypeMeta.fromTypeInfo(localTypeInfo).getHash();

  assert.throws(
    () => readChangedTypeMeta(
      readContext,
      localHash,
      original,
      remoteStruct("Shared", "value", Type.map(Type.string(), Type.int32({ encoding: "fixed" }))),
    ),
    /field schema mismatch/,
  );
  assert.doesNotThrow(() => readChangedTypeMeta(
    readContext,
    localHash,
    original,
    remoteStruct("Shared", "extra"),
  ));
});

runTest("exact local TypeMeta bypasses schema limit", () => {
  const localTypeInfo = Type.struct(
    { namespace: "example", typeName: "Shared" },
    { value: Type.int32({ encoding: "fixed" }) },
  );
  const original = {
    getTypeInfo() {
      return localTypeInfo;
    },
  };
  const localHash = TypeMeta.fromTypeInfo(localTypeInfo).getHash();
  const readContext = context({
    computeTypeId(typeInfo) {
      return typeInfo.typeId;
    },
    getSerializerById() {
      return undefined;
    },
    getSerializerByHash(hash) {
      return hash === localHash ? original : undefined;
    },
    generateReadSerializer(typeInfo) {
      return {
        getTypeInfo() {
          return typeInfo;
        },
      };
    },
  });

  readChangedTypeMeta(readContext, localHash, original, remoteStruct("Shared", "extra"));
  assert.doesNotThrow(() => readChangedTypeMeta(
    readContext,
    localHash,
    undefined,
    TypeMeta.fromTypeInfo(localTypeInfo),
  ));
  assert.doesNotThrow(() => readTypeMeta(
    readContext,
    TypeMeta.fromTypeInfo(localTypeInfo),
  ));
});

runTest("failed Any TypeMeta does not consume schema limit", () => {
  const localTypeInfo = Type.struct(
    901,
    { value: Type.int32({ encoding: "fixed" }) },
  );
  const localHash = TypeMeta.fromTypeInfo(localTypeInfo).getHash();
  const original = {
    getHash() {
      return localHash;
    },
    getTypeInfo() {
      return localTypeInfo;
    },
  };
  const readContext = context({
    computeTypeId(typeInfo) {
      return typeInfo.typeId;
    },
    getSerializerById(typeId, userTypeId) {
      return userTypeId === 901 ? original : undefined;
    },
    getSerializerByName() {
      return undefined;
    },
    generateReadSerializer(typeInfo) {
      return {
        getHash() {
          return TypeMeta.fromTypeInfo(typeInfo).getHash();
        },
        getTypeInfo() {
          return typeInfo;
        },
      };
    },
  });

  assert.throws(
    () => detectAnySerializer(
      readContext,
      anyStruct("value", Type.map(Type.string(), Type.int32({ encoding: "fixed" }))),
    ),
    /field schema mismatch/,
  );
  assert.doesNotThrow(() => detectAnySerializer(readContext, anyStruct("extra")));
});

runTest("exact Any TypeMeta bypasses schema limit", () => {
  const localTypeInfo = Type.struct(
    901,
    { value: Type.int32({ encoding: "fixed" }) },
  );
  const localHash = TypeMeta.fromTypeInfo(localTypeInfo).getHash();
  const original = {
    getHash() {
      return localHash;
    },
    getTypeInfo() {
      return localTypeInfo;
    },
  };
  const readContext = context({
    computeTypeId(typeInfo) {
      return typeInfo.typeId;
    },
    getSerializerById(typeId, userTypeId) {
      return userTypeId === 901 ? original : undefined;
    },
    getSerializerByName() {
      return undefined;
    },
    generateReadSerializer(typeInfo) {
      return {
        getHash() {
          return TypeMeta.fromTypeInfo(typeInfo).getHash();
        },
        getTypeInfo() {
          return typeInfo;
        },
      };
    },
  });

  detectAnySerializer(readContext, anyStruct("extra"));
  assert.doesNotThrow(() => detectAnySerializer(
    readContext,
    TypeMeta.fromTypeInfo(localTypeInfo),
  ));
  assert.doesNotThrow(() => readTypeMeta(
    readContext,
    TypeMeta.fromTypeInfo(localTypeInfo),
  ));
});

runTest("remote schema limit keeps unknown structs separate", () => {
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
