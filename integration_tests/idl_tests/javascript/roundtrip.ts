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
 * This script is invoked by the existing Java `IdlRoundTripTest` peer flow.
 * It reads Java-written files from the environment, deserializes them with
 * JavaScript, re-serializes them, and writes the roundtripped bytes back.
 */

import * as fs from "fs";
import Fory, { Type } from "@apache-fory/core";

import { registerAddressbookTypes } from "./generated/addressbook";
import { registerAutoIdTypes } from "./generated/auto_id";
import { registerComplexPbTypes } from "./generated/complex_pb";
import { registerCollectionTypes } from "./generated/collection";
import { registerOptionalTypesTypes } from "./generated/optional_types";
import { registerTreeTypes } from "./generated/tree";
import { registerGraphTypes } from "./generated/graph";
import { registerMonsterTypes } from "./generated/monster";
import { registerComplexFbsTypes } from "./generated/complex_fbs";

type RoundTripCase = {
  envVar: string;
  rootTypeId: number;
  registerFn: (fory: any, type: any) => void;
  refTracking?: boolean;
  union?: boolean;
};

const ROUND_TRIP_CASES: ReadonlyArray<RoundTripCase> = [
  {
    envVar: "DATA_FILE",
    rootTypeId: 103,
    registerFn: registerAddressbookTypes,
  },
  {
    envVar: "DATA_FILE_AUTO_ID",
    rootTypeId: 3022445236,
    registerFn: registerAutoIdTypes,
  },
  {
    envVar: "DATA_FILE_PRIMITIVES",
    rootTypeId: 200,
    registerFn: registerComplexPbTypes,
  },
  {
    envVar: "DATA_FILE_COLLECTION",
    rootTypeId: 210,
    registerFn: registerCollectionTypes,
  },
  {
    envVar: "DATA_FILE_COLLECTION_UNION",
    rootTypeId: 211,
    registerFn: registerCollectionTypes,
    union: true,
  },
  {
    envVar: "DATA_FILE_COLLECTION_ARRAY",
    rootTypeId: 212,
    registerFn: registerCollectionTypes,
  },
  {
    envVar: "DATA_FILE_COLLECTION_ARRAY_UNION",
    rootTypeId: 213,
    registerFn: registerCollectionTypes,
    union: true,
  },
  {
    envVar: "DATA_FILE_OPTIONAL_TYPES",
    rootTypeId: 122,
    registerFn: registerOptionalTypesTypes,
  },
  {
    envVar: "DATA_FILE_TREE",
    rootTypeId: 2251833438,
    registerFn: registerTreeTypes,
    refTracking: true,
  },
  {
    envVar: "DATA_FILE_GRAPH",
    rootTypeId: 2373163777,
    registerFn: registerGraphTypes,
    refTracking: true,
  },
  {
    envVar: "DATA_FILE_FLATBUFFERS_MONSTER",
    rootTypeId: 438716985,
    registerFn: registerMonsterTypes,
  },
  {
    envVar: "DATA_FILE_FLATBUFFERS_TEST2",
    rootTypeId: 372413680,
    registerFn: registerComplexFbsTypes,
  },
];

function roundTripFile(spec: RoundTripCase, filePath: string, compatible: boolean) {
  const fory = new Fory({
    compatible,
    ref: spec.refTracking ?? false,
  });

  spec.registerFn(fory, Type);
  const rootTypeInfo = spec.union
    ? Type.union(spec.rootTypeId)
    : Type.struct(spec.rootTypeId);
  const serializer = fory.typeResolver.getSerializerByTypeInfo(rootTypeInfo);

  const bytes = new Uint8Array(fs.readFileSync(filePath));
  const value = fory.deserialize(bytes, serializer);
  const roundTripBytes = fory.serialize(value, serializer);
  fs.writeFileSync(filePath, roundTripBytes);
}

const compatible = process.env["IDL_COMPATIBLE"] === "true";

for (const spec of ROUND_TRIP_CASES) {
  const filePath = process.env[spec.envVar];
  if (!filePath) {
    continue;
  }
  console.log(`Processing ${spec.envVar}: ${filePath}`);
  roundTripFile(spec, filePath, compatible);
  console.log(`  OK: roundtrip complete for ${spec.envVar}`);
}

console.log("JavaScript roundtrip finished.");
