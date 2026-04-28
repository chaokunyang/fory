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

/**
 * Cross-language roundtrip program for JavaScript IDL tests.
 *
 * This peer is invoked by the existing Java `IdlRoundTripTest` flow. It
 * mirrors the Go roundtrip test shape: build the expected local message,
 * deserialize the Java-written payload, compare it against the local value,
 * then serialize the decoded value back to the temp file.
 */

import * as assert from "assert/strict";
import * as fs from "fs";

import Fory, { BFloat16, Decimal, Type } from "@apache-fory/core";
import { AnyHelper } from "@apache-fory/core/dist/lib/gen/any";
import { ConfigFlags, RefFlags, type Serializer } from "@apache-fory/core/dist/lib/type";

import {
  AddressBook,
  AnimalCase,
  Cat,
  Dog,
  Person,
  registerAddressbookTypes,
} from "./generated/addressbook";
import {
  Envelope,
  Status as AutoIdStatus,
  registerAutoIdTypes,
} from "./generated/auto_id";
import {
  NumericCollections,
  NumericCollectionsArray,
  NumericCollectionArrayUnion,
  NumericCollectionArrayUnionCase,
  NumericCollectionUnion,
  NumericCollectionUnionCase,
  registerCollectionTypes,
} from "./generated/collection";
import {
  Container,
  PayloadCase,
  ScalarPack,
  Status as ComplexFbsStatus,
  registerComplexFbsTypes,
} from "./generated/complex_fbs";
import { PrimitiveTypes } from "./generated/complex_pb";
import {
  Graph,
  registerGraphTypes,
} from "./generated/graph";
import {
  AllOptionalTypes,
  OptionalHolder,
  OptionalUnionCase,
  registerOptionalTypesTypes,
} from "./generated/optional_types";
import {
  Monster,
  Color,
  registerMonsterTypes,
} from "./generated/monster";
import {
  TreeNode,
  registerTreeTypes,
} from "./generated/tree";
import {
  ExampleLeaf,
  ExampleLeafUnion,
  ExampleLeafUnionCase,
  ExampleState,
  registerExampleCommonTypes,
} from "./generated/example_common";
import {
  ExampleMessage,
  ExampleMessageUnion,
  ExampleMessageUnionCase,
  registerExampleTypes,
} from "./generated/example";

type RegisterFn = (fory: Fory, type: typeof Type) => void;
type AssertFn<T> = (expected: T, actual: unknown) => void;
type ExampleSchemaEvolutionVariantSpec = {
  fieldName: keyof ExampleMessage;
  buildTypeInfo: () => any;
};

function resolveCompatibleModes(): boolean[] {
  const value = process.env.IDL_COMPATIBLE;
  if (value == null || value.trim() === "") {
    return [false, true];
  }
  const normalized = value.trim().toLowerCase();
  if (normalized === "1" || normalized === "true" || normalized === "yes") {
    return [true];
  }
  if (normalized === "0" || normalized === "false" || normalized === "no") {
    return [false];
  }
  throw new Error(`Unsupported IDL_COMPATIBLE value: ${value}`);
}

function buildFory(compatible: boolean, ref: boolean, registerFns: ReadonlyArray<RegisterFn>): Fory {
  const fory = new Fory({
    compatible,
    ref,
  });
  for (const registerFn of registerFns) {
    registerFn(fory, Type);
  }
  return fory;
}

function resolveRootSerializer(fory: Fory, bytes: Uint8Array): Serializer {
  fory.readContext.reset(bytes);
  const reader = fory.readContext.reader;
  const bitmap = reader.readUint8();
  if ((bitmap & ConfigFlags.isNullFlag) === ConfigFlags.isNullFlag) {
    throw new Error("IDL roundtrip does not support null root payloads");
  }
  if ((bitmap & ConfigFlags.isCrossLanguageFlag) !== ConfigFlags.isCrossLanguageFlag) {
    throw new Error("support crosslanguage mode only");
  }
  if ((bitmap & ConfigFlags.isOutOfBandFlag) === ConfigFlags.isOutOfBandFlag) {
    throw new Error("outofband mode is not supported now");
  }
  const refFlag = fory.readContext.readRefFlag();
  if (refFlag === RefFlags.NullFlag) {
    throw new Error("IDL roundtrip does not support null root payloads");
  }
  if (refFlag === RefFlags.RefFlag) {
    throw new Error("IDL roundtrip root payload must not be a back reference");
  }
  // Generated JS IDL outputs are plain interfaces, so generic serialize(value)
  // cannot rediscover the root struct serializer. Reuse the runtime's existing
  // serializer detection from the payload, then map back to the local
  // registered serializer when available.
  const detectedSerializer = AnyHelper.detectSerializer(fory.readContext);
  return fory.typeResolver.getSerializerByTypeInfo(detectedSerializer.getTypeInfo()) ?? detectedSerializer;
}

function runFileRoundTrip<T>(
  envVar: string,
  fory: Fory,
  expected: T,
  assertFn: AssertFn<T>,
): void {
  const filePath = process.env[envVar];
  if (!filePath) {
    return;
  }
  console.log(`Processing ${envVar}: ${filePath}`);
  const payload = new Uint8Array(fs.readFileSync(filePath));
  const serializer = resolveRootSerializer(fory, payload);
  const decoded = fory.deserialize(payload);
  assertFn(expected, decoded);
  const roundTripBytes = fory.serialize(decoded, serializer);
  fs.writeFileSync(filePath, roundTripBytes);
  console.log(`  OK: roundtrip complete for ${envVar}`);
}

function normalizeAcyclic(value: unknown): unknown {
  if (value instanceof BFloat16) {
    return { __bfloat16Bits: value.toBits() };
  }
  if (value instanceof Decimal) {
    return { __decimal: value.toString() };
  }
  if (value instanceof Date) {
    return { __dateMs: value.getTime() };
  }
  if (value instanceof Map) {
    const entries = Array.from(value.entries()).map(([key, itemValue]) => {
      const normalizedKey = normalizeAcyclic(key);
      return [normalizedKey, normalizeAcyclic(itemValue)] as const;
    });
    entries.sort((left, right) => normalizedSortKey(left[0]).localeCompare(normalizedSortKey(right[0])));
    return entries;
  }
  if (ArrayBuffer.isView(value)) {
    if (value instanceof DataView) {
      return Array.from(new Uint8Array(value.buffer, value.byteOffset, value.byteLength));
    }
    return Array.from(value as unknown as ArrayLike<unknown>, (item) => normalizeAcyclic(item));
  }
  if (Array.isArray(value)) {
    return value.map((item) => normalizeAcyclic(item));
  }
  if (value != null && typeof value === "object") {
    const entries = Object.entries(value as Record<string, unknown>);
    entries.sort(([left], [right]) => left.localeCompare(right));
    return Object.fromEntries(entries.map(([key, itemValue]) => [key, normalizeAcyclic(itemValue)]));
  }
  return value;
}

function normalizedSortKey(value: unknown): string {
  if (value === null) {
    return "null";
  }
  switch (typeof value) {
    case "bigint":
      return `bigint:${value.toString()}`;
    case "boolean":
      return `boolean:${value ? "1" : "0"}`;
    case "number":
      return `number:${Object.is(value, -0) ? "-0" : String(value)}`;
    case "string":
      return `string:${value}`;
    case "undefined":
      return "undefined";
    default:
      break;
  }
  if (Array.isArray(value)) {
    return `[${value.map((item) => normalizedSortKey(item)).join(",")}]`;
  }
  if (value != null && typeof value === "object") {
    const entries = Object.entries(value as Record<string, unknown>);
    entries.sort(([left], [right]) => left.localeCompare(right));
    return `{${entries.map(([key, itemValue]) => `${key}:${normalizedSortKey(itemValue)}`).join(",")}}`;
  }
  return String(value);
}

function assertAcyclicEqual<T>(label: string, expected: T, actual: unknown): void {
  assert.deepStrictEqual(
    normalizeAcyclic(actual),
    normalizeAcyclic(expected),
    `${label} mismatch`,
  );
}

function buildDog(): Dog {
  return { name: "Rex", barkVolume: 5 };
}

function buildCat(): Cat {
  return { name: "Mimi", lives: 9 };
}

function buildPhoneNumber(number_: string, phoneType: Person.PhoneType): Person.PhoneNumber {
  return { number_, phoneType };
}

function buildAddressBook(): AddressBook {
  const person: Person = {
    name: "Alice",
    id: 123,
    email: "alice@example.com",
    tags: ["friend", "colleague"],
    scores: new Map([
      ["math", 100],
      ["science", 98],
    ]),
    salary: 120000.5,
    phones: [
      buildPhoneNumber("555-0100", Person.PhoneType.MOBILE),
      buildPhoneNumber("555-0111", Person.PhoneType.WORK),
    ],
    pet: {
      case: AnimalCase.CAT,
      value: buildCat(),
    },
  };
  return {
    people: [person],
    peopleByName: new Map([[person.name, person]]),
  };
}

function buildAutoIdEnvelope(): Envelope {
  const payload: Envelope.Payload = { value: 42 };
  return {
    id: "env-1",
    payload,
    detail: { case: Envelope.DetailCase.PAYLOAD, value: payload },
    status: AutoIdStatus.OK,
  };
}

function buildPrimitiveTypes(): PrimitiveTypes {
  return {
    boolValue: true,
    int8Value: 12,
    int16Value: 1234,
    int32Value: -123456,
    varint32Value: -12345,
    int64Value: -123456789n,
    varint64Value: -987654321n,
    taggedInt64Value: 123456789n,
    uint8Value: 200,
    uint16Value: 60000,
    uint32Value: 1234567890,
    varUint32Value: 1234567890,
    uint64Value: 9876543210n,
    varUint64Value: 12345678901n,
    taggedUint64Value: 2222222222n,
    float32Value: 2.5,
    float64Value: 3.5,
    contact: {
      case: PrimitiveTypes.ContactCase.PHONE,
      value: 12345,
    },
  };
}

function buildNumericCollections(): NumericCollections {
  return {
    int8Values: [1, -2, 3],
    int16Values: [100, -200, 300],
    int32Values: [1000, -2000, 3000],
    int64Values: [10000n, -20000n, 30000n],
    uint8Values: [200, 250],
    uint16Values: [50000, 60000],
    uint32Values: [2000000000, 2100000000],
    uint64Values: [9000000000n, 12000000000n],
    float32Values: [1.5, 2.5],
    float64Values: [3.5, 4.5],
  };
}

function buildNumericCollectionUnion(): NumericCollectionUnion {
  return {
    case: NumericCollectionUnionCase.INT32_VALUES,
    value: [7, 8, 9],
  };
}

function buildNumericCollectionsArray(): NumericCollectionsArray {
  return {
    int8Values: [1, -2, 3],
    int16Values: [100, -200, 300],
    int32Values: [1000, -2000, 3000],
    int64Values: [10000n, -20000n, 30000n],
    uint8Values: [200, 250],
    uint16Values: [50000, 60000],
    uint32Values: [2000000000, 2100000000],
    uint64Values: [9000000000n, 12000000000n],
    float32Values: [1.5, 2.5],
    float64Values: [3.5, 4.5],
  };
}

function buildNumericCollectionArrayUnion(): NumericCollectionArrayUnion {
  return {
    case: NumericCollectionArrayUnionCase.UINT16_VALUES,
    value: [1000, 2000, 3000],
  };
}

function buildMonster(): Monster {
  return {
    pos: {
      x: 1.0,
      y: 2.0,
      z: 3.0,
    },
    mana: 200,
    hp: 80,
    name: "Orc",
    friendly: true,
    inventory: [1, 2, 3],
    color: Color.Blue,
  };
}

function buildContainer(): Container {
  const scalars: ScalarPack = {
    b: -8,
    ub: 200,
    s: -1234,
    us: 40000,
    i: -123456,
    ui: 123456,
    l: -123456789n,
    ul: 987654321n,
    f: 1.5,
    d: 2.5,
    ok: true,
  };
  return {
    id: 9876543210n,
    status: ComplexFbsStatus.STARTED,
    bytes: [1, 2, 3],
    numbers: [10, 20, 30],
    scalars,
    names: ["alpha", "beta"],
    flags: [true, false],
    payload: {
      case: PayloadCase.METRIC,
      value: { value: 42.0 },
    },
  };
}

function buildLocalDate(year: number, month: number, day: number): Date {
  return new Date(year, month - 1, day, 0, 0, 0, 0);
}

function buildOptionalHolder(): OptionalHolder {
  const allTypes: AllOptionalTypes = {
    boolValue: true,
    int8Value: 12,
    int16Value: 1234,
    int32Value: -123456,
    fixedInt32Value: -123456,
    varint32Value: -12345,
    int64Value: -123456789n,
    fixedInt64Value: -123456789n,
    varint64Value: -987654321n,
    taggedInt64Value: 123456789n,
    uint8Value: 200,
    uint16Value: 60000,
    uint32Value: 1234567890,
    fixedUint32Value: 1234567890,
    varUint32Value: 1234567890,
    uint64Value: 9876543210n,
    fixedUint64Value: 9876543210n,
    varUint64Value: 12345678901n,
    taggedUint64Value: 2222222222n,
    float32Value: 2.5,
    float64Value: 3.5,
    stringValue: "optional",
    bytesValue: new Uint8Array([1, 2, 3]),
    dateValue: buildLocalDate(2024, 1, 2),
    timestampValue: new Date("2024-01-02T03:04:05Z"),
    int32List: [1, 2, 3],
    stringList: ["alpha", "beta"],
    int64Map: new Map([
      ["alpha", 10n],
      ["beta", 20n],
    ]),
  };
  return {
    allTypes,
    choice: {
      case: OptionalUnionCase.NOTE,
      value: "optional",
    },
  };
}

function buildExampleLeafA(): ExampleLeaf {
  return {
    label: "leaf-a",
    count: 7,
  };
}

function buildExampleLeafB(): ExampleLeaf {
  return {
    label: "leaf-b",
    count: -3,
  };
}

function buildExampleLeafUnionLeaf(value: ExampleLeaf): ExampleLeafUnion {
  return {
    case: ExampleLeafUnionCase.LEAF,
    value,
  };
}

function buildExampleTimestamp(): Date {
  return new Date("2024-02-29T12:34:56.789Z");
}

function buildExampleTimestamp2(): Date {
  return new Date("2024-03-01T00:00:00.123Z");
}

function buildExampleDurationMillis(): number {
  return 3_723_456.789;
}

function buildExampleDurationMillis2(): number {
  return 1_234.567;
}

function buildExampleDecimal(): Decimal {
  return Decimal.from("1234567890123456789", 4);
}

function buildExampleDecimal2(): Decimal {
  return Decimal.from(-5, 1);
}

function buildBFloat16(value: number): BFloat16 {
  return BFloat16.fromFloat32(value);
}

function buildExampleMessage(): ExampleMessage {
  const bytesValue = Uint8Array.from([1, 2, 3, 4]);
  const dateValue = buildLocalDate(2024, 2, 29);
  const timestampValue = buildExampleTimestamp();
  const durationValue = buildExampleDurationMillis();
  const decimalValue = buildExampleDecimal();
  const messageValue = buildExampleLeafA();
  const unionValue = buildExampleLeafUnionLeaf(buildExampleLeafB());
  return {
    boolValue: true,
    int8Value: -12,
    int16Value: 1234,
    fixedInt32Value: 123456789,
    varint32Value: -1234567,
    fixedInt64Value: 1234567890123456789n,
    varint64Value: -1234567890123456789n,
    taggedInt64Value: 1073741824n,
    uint8Value: 200,
    uint16Value: 60000,
    fixedUint32Value: 2000000000,
    varUint32Value: 2100000000,
    fixedUint64Value: 9000000000n,
    varUint64Value: 12000000000n,
    taggedUint64Value: 2222222222n,
    float16Value: 1.5,
    bfloat16Value: buildBFloat16(-2.75),
    float32Value: 3.25,
    float64Value: -4.5,
    stringValue: "example-string",
    bytesValue,
    dateValue,
    timestampValue,
    durationValue,
    decimalValue,
    enumValue: ExampleState.READY,
    messageValue,
    unionValue,
    boolList: [true, false],
    int8List: [-12, 7],
    int16List: [1234, -2345],
    fixedInt32List: [123456789, -123456789],
    varint32List: [-1234567, 7654321],
    fixedInt64List: [1234567890123456789n, -123456789012345678n],
    varint64List: [-1234567890123456789n, 123456789012345678n],
    taggedInt64List: [1073741824n, -1073741824n],
    uint8List: [200, 42],
    uint16List: [60000, 12345],
    fixedUint32List: [2000000000, 1234567890],
    varUint32List: [2100000000, 1234567890],
    fixedUint64List: [9000000000n, 4000000000n],
    varUint64List: [12000000000n, 5000000000n],
    taggedUint64List: [2222222222n, 3333333333n],
    float16List: [1.5, -0.5],
    bfloat16List: [buildBFloat16(-2.75), buildBFloat16(2.25)],
    maybeFloat16List: [1.5, null, -0.5],
    maybeBfloat16List: [null, buildBFloat16(2.25), buildBFloat16(-1.0)],
    float32List: [3.25, -0.5],
    float64List: [-4.5, 6.75],
    stringList: ["example-string", "secondary"],
    bytesList: [Uint8Array.from([1, 2, 3, 4]), Uint8Array.from([5, 6])],
    dateList: [buildLocalDate(2024, 2, 29), buildLocalDate(2024, 3, 1)],
    timestampList: [buildExampleTimestamp(), buildExampleTimestamp2()],
    durationList: [buildExampleDurationMillis(), buildExampleDurationMillis2()],
    decimalList: [buildExampleDecimal(), buildExampleDecimal2()],
    enumList: [ExampleState.READY, ExampleState.FAILED],
    messageList: [buildExampleLeafA(), buildExampleLeafB()],
    unionList: [buildExampleLeafUnionLeaf(buildExampleLeafA()), buildExampleLeafUnionLeaf(buildExampleLeafB())],
    stringValuesByBool: new Map([[true, "true-value"], [false, "false-value"]]),
    stringValuesByInt8: new Map([[-12, "minus-twelve"]]),
    stringValuesByInt16: new Map([[1234, "twelve-thirty-four"]]),
    stringValuesByFixedInt32: new Map([[123456789, "fixed-int32"]]),
    stringValuesByVarint32: new Map([[-1234567, "varint32"]]),
    stringValuesByFixedInt64: new Map([[1234567890123456789n, "fixed-int64"]]),
    stringValuesByVarint64: new Map([[-1234567890123456789n, "varint64"]]),
    stringValuesByTaggedInt64: new Map([[1073741824n, "tagged-int64"]]),
    stringValuesByUint8: new Map([[200, "uint8"]]),
    stringValuesByUint16: new Map([[60000, "uint16"]]),
    stringValuesByFixedUint32: new Map([[2000000000, "fixed-uint32"]]),
    stringValuesByVarUint32: new Map([[2100000000, "var-uint32"]]),
    stringValuesByFixedUint64: new Map([[9000000000n, "fixed-uint64"]]),
    stringValuesByVarUint64: new Map([[12000000000n, "var-uint64"]]),
    stringValuesByTaggedUint64: new Map([[2222222222n, "tagged-uint64"]]),
    stringValuesByString: new Map([["example-string", "string"]]),
    stringValuesByTimestamp: new Map([[buildExampleTimestamp(), "timestamp"]]),
    stringValuesByDuration: new Map([[buildExampleDurationMillis(), "duration"]]),
    stringValuesByEnum: new Map([[ExampleState.READY, "ready"]]),
    float16ValuesByName: new Map([["primary", 1.5]]),
    maybeFloat16ValuesByName: new Map([["primary", 1.5], ["missing", null]]),
    bfloat16ValuesByName: new Map([["primary", buildBFloat16(-2.75)]]),
    maybeBfloat16ValuesByName: new Map([["missing", null], ["secondary", buildBFloat16(2.25)]]),
    bytesValuesByName: new Map([["payload", Uint8Array.from([1, 2, 3, 4])]]),
    dateValuesByName: new Map([["leap-day", buildLocalDate(2024, 2, 29)]]),
    decimalValuesByName: new Map([["amount", buildExampleDecimal()]]),
    messageValuesByName: new Map([["leaf-a", buildExampleLeafA()], ["leaf-b", buildExampleLeafB()]]),
    unionValuesByName: new Map([["leaf-b", buildExampleLeafUnionLeaf(buildExampleLeafB())]]),
  };
}

function buildExampleMessageUnion(): ExampleMessageUnion {
  return {
    case: ExampleMessageUnionCase.UNION_VALUE,
    value: buildExampleLeafUnionLeaf(buildExampleLeafB()),
  };
}

function buildTree(): TreeNode {
  const childA: TreeNode = {
    id: "child-a",
    name: "child-a",
    children: [],
  };
  const childB: TreeNode = {
    id: "child-b",
    name: "child-b",
    children: [],
  };
  childA.parent = childB;
  childB.parent = childA;
  return {
    id: "root",
    name: "root",
    children: [childA, childA, childB],
  };
}

function buildGraph(): Graph {
  const nodeA = {
    id: "node-a",
    outEdges: [],
    inEdges: [],
  } as unknown as Graph["nodes"][number];
  const nodeB = {
    id: "node-b",
    outEdges: [],
    inEdges: [],
  } as unknown as Graph["nodes"][number];
  const edge = {
    id: "edge-1",
    weight: 1.5,
    from_: nodeA,
    to: nodeB,
  } as Graph["edges"][number];
  nodeA.outEdges = [edge];
  nodeA.inEdges = [edge];
  nodeB.inEdges = [edge];
  nodeB.outEdges = [];
  return {
    nodes: [nodeA, nodeB],
    edges: [edge],
  };
}

function assertAddressBookEqual(expected: AddressBook, actual: unknown): void {
  assertAcyclicEqual("addressbook", expected, actual);
}

function assertAutoIdEnvelopeEqual(expected: Envelope, actual: unknown): void {
  assertAcyclicEqual("auto_id envelope", expected, actual);
}

function assertPrimitiveTypesEqual(expected: PrimitiveTypes, actual: unknown): void {
  assertAcyclicEqual("primitive types", expected, actual);
}

function assertNumericCollectionsEqual(expected: NumericCollections, actual: unknown): void {
  assertAcyclicEqual("numeric collections", expected, actual);
}

function assertNumericCollectionUnionEqual(expected: NumericCollectionUnion, actual: unknown): void {
  assertAcyclicEqual("numeric collection union", expected, actual);
}

function assertNumericCollectionsArrayEqual(expected: NumericCollectionsArray, actual: unknown): void {
  assertAcyclicEqual("numeric collections array", expected, actual);
}

function assertNumericCollectionArrayUnionEqual(expected: NumericCollectionArrayUnion, actual: unknown): void {
  assertAcyclicEqual("numeric collection array union", expected, actual);
}

function assertMonsterEqual(expected: Monster, actual: unknown): void {
  assertAcyclicEqual("monster", expected, actual);
}

function assertContainerEqual(expected: Container, actual: unknown): void {
  assertAcyclicEqual("complex_fbs container", expected, actual);
}

function assertOptionalHolderEqual(expected: OptionalHolder, actual: unknown): void {
  assertAcyclicEqual("optional holder", expected, actual);
}

function assertExampleMessageEqual(expected: ExampleMessage, actual: unknown): void {
  assertAcyclicEqual("example message", expected, actual);
}

function assertExampleMessageUnionEqual(expected: ExampleMessageUnion, actual: unknown): void {
  assertAcyclicEqual("example message union", expected, actual);
}

const exampleSchemaEvolutionVariantSpecs: ReadonlyArray<ExampleSchemaEvolutionVariantSpec> = [
  { fieldName: "boolValue", buildTypeInfo: () => Type.bool().setId(1) },
  { fieldName: "int8Value", buildTypeInfo: () => Type.int8().setId(2) },
  { fieldName: "int16Value", buildTypeInfo: () => Type.int16().setId(3) },
  { fieldName: "fixedInt32Value", buildTypeInfo: () => Type.int32().setId(4) },
  { fieldName: "varint32Value", buildTypeInfo: () => Type.varInt32().setId(5) },
  { fieldName: "fixedInt64Value", buildTypeInfo: () => Type.int64().setId(6) },
  { fieldName: "varint64Value", buildTypeInfo: () => Type.varInt64().setId(7) },
  { fieldName: "taggedInt64Value", buildTypeInfo: () => Type.sliInt64().setId(8) },
  { fieldName: "uint8Value", buildTypeInfo: () => Type.uint8().setId(9) },
  { fieldName: "uint16Value", buildTypeInfo: () => Type.uint16().setId(10) },
  { fieldName: "fixedUint32Value", buildTypeInfo: () => Type.uint32().setId(11) },
  { fieldName: "varUint32Value", buildTypeInfo: () => Type.varUInt32().setId(12) },
  { fieldName: "fixedUint64Value", buildTypeInfo: () => Type.uint64().setId(13) },
  { fieldName: "varUint64Value", buildTypeInfo: () => Type.varUInt64().setId(14) },
  { fieldName: "taggedUint64Value", buildTypeInfo: () => Type.taggedUInt64().setId(15) },
  { fieldName: "float16Value", buildTypeInfo: () => Type.float16().setId(16) },
  { fieldName: "bfloat16Value", buildTypeInfo: () => Type.bfloat16().setId(17) },
  { fieldName: "float32Value", buildTypeInfo: () => Type.float32().setId(18) },
  { fieldName: "float64Value", buildTypeInfo: () => Type.float64().setId(19) },
  { fieldName: "stringValue", buildTypeInfo: () => Type.string().setId(20) },
  { fieldName: "bytesValue", buildTypeInfo: () => Type.binary().setId(21) },
  { fieldName: "dateValue", buildTypeInfo: () => Type.date().setId(22) },
  { fieldName: "timestampValue", buildTypeInfo: () => Type.timestamp().setId(23) },
  { fieldName: "durationValue", buildTypeInfo: () => Type.duration().setId(24) },
  { fieldName: "decimalValue", buildTypeInfo: () => Type.decimal().setId(25) },
  { fieldName: "enumValue", buildTypeInfo: () => Type.enum(1504, { UNKNOWN: 0, READY: 1, FAILED: 2 }).setId(26) },
  { fieldName: "messageValue", buildTypeInfo: () => Type.struct({ typeId: 1502, evolving: false }).setId(27).setNullable(true) },
  { fieldName: "unionValue", buildTypeInfo: () => Type.union(1503, { 1: Type.string(), 2: Type.varInt32(), 3: Type.struct({ typeId: 1502, evolving: false }) }).setId(28) },
  { fieldName: "boolList", buildTypeInfo: () => Type.boolArray().setId(101) },
  { fieldName: "int8List", buildTypeInfo: () => Type.int8Array().setId(102) },
  { fieldName: "int16List", buildTypeInfo: () => Type.int16Array().setId(103) },
  { fieldName: "fixedInt32List", buildTypeInfo: () => Type.int32Array().setId(104) },
  { fieldName: "varint32List", buildTypeInfo: () => Type.int32Array().setId(105) },
  { fieldName: "fixedInt64List", buildTypeInfo: () => Type.int64Array().setId(106) },
  { fieldName: "varint64List", buildTypeInfo: () => Type.int64Array().setId(107) },
  { fieldName: "taggedInt64List", buildTypeInfo: () => Type.int64Array().setId(108) },
  { fieldName: "uint8List", buildTypeInfo: () => Type.uint8Array().setId(109) },
  { fieldName: "uint16List", buildTypeInfo: () => Type.uint16Array().setId(110) },
  { fieldName: "fixedUint32List", buildTypeInfo: () => Type.uint32Array().setId(111) },
  { fieldName: "varUint32List", buildTypeInfo: () => Type.uint32Array().setId(112) },
  { fieldName: "fixedUint64List", buildTypeInfo: () => Type.uint64Array().setId(113) },
  { fieldName: "varUint64List", buildTypeInfo: () => Type.uint64Array().setId(114) },
  { fieldName: "taggedUint64List", buildTypeInfo: () => Type.uint64Array().setId(115) },
  { fieldName: "float16List", buildTypeInfo: () => Type.float16Array().setId(116) },
  { fieldName: "bfloat16List", buildTypeInfo: () => Type.bfloat16Array().setId(117) },
  { fieldName: "maybeFloat16List", buildTypeInfo: () => Type.array(Type.float16().setNullable(true)).setId(118) },
  { fieldName: "maybeBfloat16List", buildTypeInfo: () => Type.array(Type.bfloat16().setNullable(true)).setId(119) },
  { fieldName: "float32List", buildTypeInfo: () => Type.float32Array().setId(120) },
  { fieldName: "float64List", buildTypeInfo: () => Type.float64Array().setId(121) },
  { fieldName: "stringList", buildTypeInfo: () => Type.array(Type.string()).setId(122) },
  { fieldName: "bytesList", buildTypeInfo: () => Type.array(Type.binary()).setId(123) },
  { fieldName: "dateList", buildTypeInfo: () => Type.array(Type.date()).setId(124) },
  { fieldName: "timestampList", buildTypeInfo: () => Type.array(Type.timestamp()).setId(125) },
  { fieldName: "durationList", buildTypeInfo: () => Type.array(Type.duration()).setId(126) },
  { fieldName: "decimalList", buildTypeInfo: () => Type.array(Type.decimal()).setId(127) },
  { fieldName: "enumList", buildTypeInfo: () => Type.array(Type.enum(1504, { UNKNOWN: 0, READY: 1, FAILED: 2 })).setId(128) },
  { fieldName: "messageList", buildTypeInfo: () => Type.array(Type.struct({ typeId: 1502, evolving: false })).setId(129) },
  { fieldName: "unionList", buildTypeInfo: () => Type.array(Type.union(1503, { 1: Type.string(), 2: Type.varInt32(), 3: Type.struct({ typeId: 1502, evolving: false }) })).setId(130) },
  { fieldName: "stringValuesByBool", buildTypeInfo: () => Type.map(Type.bool(), Type.string()).setId(201) },
  { fieldName: "stringValuesByInt8", buildTypeInfo: () => Type.map(Type.int8(), Type.string()).setId(202) },
  { fieldName: "stringValuesByInt16", buildTypeInfo: () => Type.map(Type.int16(), Type.string()).setId(203) },
  { fieldName: "stringValuesByFixedInt32", buildTypeInfo: () => Type.map(Type.int32(), Type.string()).setId(204) },
  { fieldName: "stringValuesByVarint32", buildTypeInfo: () => Type.map(Type.varInt32(), Type.string()).setId(205) },
  { fieldName: "stringValuesByFixedInt64", buildTypeInfo: () => Type.map(Type.int64(), Type.string()).setId(206) },
  { fieldName: "stringValuesByVarint64", buildTypeInfo: () => Type.map(Type.varInt64(), Type.string()).setId(207) },
  { fieldName: "stringValuesByTaggedInt64", buildTypeInfo: () => Type.map(Type.sliInt64(), Type.string()).setId(208) },
  { fieldName: "stringValuesByUint8", buildTypeInfo: () => Type.map(Type.uint8(), Type.string()).setId(209) },
  { fieldName: "stringValuesByUint16", buildTypeInfo: () => Type.map(Type.uint16(), Type.string()).setId(210) },
  { fieldName: "stringValuesByFixedUint32", buildTypeInfo: () => Type.map(Type.uint32(), Type.string()).setId(211) },
  { fieldName: "stringValuesByVarUint32", buildTypeInfo: () => Type.map(Type.varUInt32(), Type.string()).setId(212) },
  { fieldName: "stringValuesByFixedUint64", buildTypeInfo: () => Type.map(Type.uint64(), Type.string()).setId(213) },
  { fieldName: "stringValuesByVarUint64", buildTypeInfo: () => Type.map(Type.varUInt64(), Type.string()).setId(214) },
  { fieldName: "stringValuesByTaggedUint64", buildTypeInfo: () => Type.map(Type.taggedUInt64(), Type.string()).setId(215) },
  { fieldName: "stringValuesByString", buildTypeInfo: () => Type.map(Type.string(), Type.string()).setId(218) },
  { fieldName: "stringValuesByTimestamp", buildTypeInfo: () => Type.map(Type.timestamp(), Type.string()).setId(219) },
  { fieldName: "stringValuesByDuration", buildTypeInfo: () => Type.map(Type.duration(), Type.string()).setId(220) },
  { fieldName: "stringValuesByEnum", buildTypeInfo: () => Type.map(Type.enum(1504, { UNKNOWN: 0, READY: 1, FAILED: 2 }), Type.string()).setId(221) },
  { fieldName: "float16ValuesByName", buildTypeInfo: () => Type.map(Type.string(), Type.float16()).setId(222) },
  { fieldName: "maybeFloat16ValuesByName", buildTypeInfo: () => Type.map(Type.string(), Type.float16().setNullable(true)).setId(223) },
  { fieldName: "bfloat16ValuesByName", buildTypeInfo: () => Type.map(Type.string(), Type.bfloat16()).setId(224) },
  { fieldName: "maybeBfloat16ValuesByName", buildTypeInfo: () => Type.map(Type.string(), Type.bfloat16().setNullable(true)).setId(225) },
  { fieldName: "bytesValuesByName", buildTypeInfo: () => Type.map(Type.string(), Type.binary()).setId(226) },
  { fieldName: "dateValuesByName", buildTypeInfo: () => Type.map(Type.string(), Type.date()).setId(227) },
  { fieldName: "decimalValuesByName", buildTypeInfo: () => Type.map(Type.string(), Type.decimal()).setId(228) },
  { fieldName: "messageValuesByName", buildTypeInfo: () => Type.map(Type.string(), Type.struct({ typeId: 1502, evolving: false })).setId(229) },
  { fieldName: "unionValuesByName", buildTypeInfo: () => Type.map(Type.string(), Type.union(1503, { 1: Type.string(), 2: Type.varInt32(), 3: Type.struct({ typeId: 1502, evolving: false }) })).setId(230) },
];

function buildExampleSchemaEvolutionRegistration(typeInfoFactory: () => any) {
  const fory = new Fory({ compatible: true, ref: false });
  registerExampleCommonTypes(fory, Type);
  return fory.register(typeInfoFactory());
}

function runExampleMessageRoundTrip(fory: Fory, compatible: boolean): void {
  const filePath = process.env.DATA_FILE_EXAMPLE_MESSAGE;
  if (!filePath) {
    return;
  }
  const expected = buildExampleMessage();
  const payload = new Uint8Array(fs.readFileSync(filePath));
  const serializer = resolveRootSerializer(fory, payload);
  const decoded = fory.deserialize(payload);
  assertExampleMessageEqual(expected, decoded);
  if (compatible) {
    const emptyDecoded = buildExampleSchemaEvolutionRegistration(() => Type.struct(1500, {})).deserialize(payload);
    assert.ok(emptyDecoded != null && typeof emptyDecoded === "object", "example empty schema decode failed");
    for (const spec of exampleSchemaEvolutionVariantSpecs) {
      const variantDecoded = buildExampleSchemaEvolutionRegistration(
        () => Type.struct(1500, { [spec.fieldName]: spec.buildTypeInfo() }),
      ).deserialize(payload) as Record<string, unknown>;
      assertAcyclicEqual(
        `example schema evolution ${String(spec.fieldName)}`,
        expected[spec.fieldName],
        variantDecoded[spec.fieldName],
      );
    }
  }
  fs.writeFileSync(filePath, fory.serialize(decoded, serializer));
}

function assertTreeEqual(expected: TreeNode, actualValue: unknown): void {
  assert.ok(actualValue != null && typeof actualValue === "object", "tree payload must decode to an object");
  const actual = actualValue as TreeNode;
  assert.equal(actual.id, expected.id, "tree root id mismatch");
  assert.equal(actual.name, expected.name, "tree root name mismatch");
  assert.equal(actual.children.length, expected.children.length, "tree children size mismatch");
  assert.equal(expected.children.length, 3, "expected tree fixture drift");
  assert.strictEqual(expected.children[0], expected.children[1], "expected tree shared child drift");
  assert.notStrictEqual(expected.children[0], expected.children[2], "expected tree distinct child drift");
  assert.equal(actual.children[0].id, expected.children[0].id, "tree first child id mismatch");
  assert.equal(actual.children[0].name, expected.children[0].name, "tree first child name mismatch");
  assert.equal(actual.children[2].id, expected.children[2].id, "tree third child id mismatch");
  assert.equal(actual.children[2].name, expected.children[2].name, "tree third child name mismatch");
  assert.strictEqual(actual.children[0], actual.children[1], "tree shared child mismatch");
  assert.notStrictEqual(actual.children[0], actual.children[2], "tree distinct child mismatch");
  assert.strictEqual(actual.children[0].parent, actual.children[2], "tree parent back-pointer mismatch");
  assert.strictEqual(actual.children[2].parent, actual.children[0], "tree parent reverse mismatch");
}

function assertGraphEqual(expected: Graph, actualValue: unknown): void {
  assert.ok(actualValue != null && typeof actualValue === "object", "graph payload must decode to an object");
  const actual = actualValue as Graph;
  assert.equal(actual.nodes.length, expected.nodes.length, "graph node size mismatch");
  assert.equal(actual.edges.length, expected.edges.length, "graph edge size mismatch");
  assert.equal(expected.nodes.length, 2, "expected graph fixture drift");
  assert.equal(expected.edges.length, 1, "expected graph edge fixture drift");
  const actualNodeA = actual.nodes[0];
  const actualNodeB = actual.nodes[1];
  const actualEdge = actual.edges[0];
  assert.equal(actualNodeA.id, expected.nodes[0].id, "graph node-a id mismatch");
  assert.equal(actualNodeB.id, expected.nodes[1].id, "graph node-b id mismatch");
  assert.equal(actualEdge.id, expected.edges[0].id, "graph edge id mismatch");
  assert.equal(actualEdge.weight, expected.edges[0].weight, "graph edge weight mismatch");
  assert.strictEqual(actualNodeA.outEdges[0], actualNodeA.inEdges[0], "graph shared edge mismatch");
  assert.strictEqual(actualEdge, actualNodeA.outEdges[0], "graph edge link mismatch");
  assert.strictEqual(actualEdge.from_, actualNodeA, "graph edge from mismatch");
  assert.strictEqual(actualEdge.to, actualNodeB, "graph edge to mismatch");
}

function runStandardRoundTrip(compatible: boolean): void {
  // AddressBook registration already includes the shared complex_pb types.
  const fory = buildFory(compatible, false, [
    registerAddressbookTypes,
    registerAutoIdTypes,
    registerMonsterTypes,
    registerComplexFbsTypes,
    registerCollectionTypes,
    registerOptionalTypesTypes,
    registerExampleTypes,
  ]);

  runFileRoundTrip("DATA_FILE", fory, buildAddressBook(), assertAddressBookEqual);
  runFileRoundTrip("DATA_FILE_AUTO_ID", fory, buildAutoIdEnvelope(), assertAutoIdEnvelopeEqual);
  runFileRoundTrip("DATA_FILE_PRIMITIVES", fory, buildPrimitiveTypes(), assertPrimitiveTypesEqual);
  runFileRoundTrip("DATA_FILE_COLLECTION", fory, buildNumericCollections(), assertNumericCollectionsEqual);
  runFileRoundTrip(
    "DATA_FILE_COLLECTION_UNION",
    fory,
    buildNumericCollectionUnion(),
    assertNumericCollectionUnionEqual,
  );
  runFileRoundTrip(
    "DATA_FILE_COLLECTION_ARRAY",
    fory,
    buildNumericCollectionsArray(),
    assertNumericCollectionsArrayEqual,
  );
  runFileRoundTrip(
    "DATA_FILE_COLLECTION_ARRAY_UNION",
    fory,
    buildNumericCollectionArrayUnion(),
    assertNumericCollectionArrayUnionEqual,
  );
  runFileRoundTrip("DATA_FILE_OPTIONAL_TYPES", fory, buildOptionalHolder(), assertOptionalHolderEqual);
  runExampleMessageRoundTrip(fory, compatible);
  runFileRoundTrip("DATA_FILE_EXAMPLE_UNION", fory, buildExampleMessageUnion(), assertExampleMessageUnionEqual);
  runFileRoundTrip("DATA_FILE_FLATBUFFERS_MONSTER", fory, buildMonster(), assertMonsterEqual);
  runFileRoundTrip("DATA_FILE_FLATBUFFERS_TEST2", fory, buildContainer(), assertContainerEqual);
}

function runRefRoundTrip(compatible: boolean): void {
  const refFory = buildFory(compatible, true, [
    registerTreeTypes,
    registerGraphTypes,
  ]);

  runFileRoundTrip("DATA_FILE_TREE", refFory, buildTree(), assertTreeEqual);
  runFileRoundTrip("DATA_FILE_GRAPH", refFory, buildGraph(), assertGraphEqual);
}

for (const compatible of resolveCompatibleModes()) {
  runStandardRoundTrip(compatible);
  runRefRoundTrip(compatible);
}

console.log("JavaScript roundtrip finished.");
