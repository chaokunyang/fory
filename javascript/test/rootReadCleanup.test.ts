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

import Fory, { Type } from "../packages/core/index";
import { TypeMeta } from "../packages/core/lib/meta/TypeMeta";
import { describe, expect, test } from "@jest/globals";

function expectRootStateCleared(readContext: any) {
  expect(readContext.refReader.readObjects).toHaveLength(0);
  expect(readContext.metaStringReader.names).toHaveLength(0);
  expect(readContext.typeMeta).toHaveLength(0);
}

function populateLogicalTables(readContext: any, typeMeta: TypeMeta) {
  readContext.metaStringReader.names.push({ bytes: new Uint8Array([1]), encoding: 0 });
  readContext.typeMeta.push(typeMeta);
}

describe.each([
  {
    name: "Fory.deserialize",
    invoke: (fory: Fory, registered: ReturnType<Fory["register"]>, bytes: Uint8Array) =>
      fory.deserialize(bytes, registered.serializer),
  },
  {
    name: "registered deserialize",
    invoke: (_fory: Fory, registered: ReturnType<Fory["register"]>, bytes: Uint8Array) =>
      registered.deserialize(bytes),
  },
])("$name root cleanup", ({ invoke }) => {
  test.each(["success", "failure"] as const)("clears generated root state after %s", (outcome) => {
    const writerFory = new Fory({ compatible: true, ref: true });
    const readerFory = new Fory({ compatible: true, ref: true });
    const writer = writerFory.register(
      Type.struct(7601, {
        value: Type.int32().setId(1),
      }),
    );
    const reader = readerFory.register(
      Type.struct(7601, {
        value: Type.int32().setId(1),
      }),
    );
    const bytes = writer.serialize({ value: 7 });
    const input = outcome === "failure" ? bytes.subarray(0, bytes.length - 1) : bytes;
    const read = () => invoke(readerFory, reader, input);

    if (outcome === "failure") {
      expect(read).toThrow();
    } else {
      expect(read()).toEqual({ value: 7 });
    }

    const readContext = (readerFory as any).readContext;
    expectRootStateCleared(readContext);
    expect(readContext.typeMetaCache.size).toBeGreaterThan(0);
  });

  test.each(["success", "failure"] as const)("clears logical tables after %s", (outcome) => {
    const fory = new Fory({ compatible: true, ref: true });
    const registered = fory.register(Type.struct(7602, {}));
    const readContext = (fory as any).readContext;
    const typeMeta = TypeMeta.fromTypeInfo(Type.struct(7603, {}));
    const headerHash = typeMeta.getHash();
    readContext.typeMetaCache.set(headerHash, typeMeta);
    populateLogicalTables(readContext, typeMeta);

    registered.serializer.readRef = () => {
      expectRootStateCleared(readContext);
      populateLogicalTables(readContext, typeMeta);
      if (outcome === "failure") {
        throw new Error("root read failed");
      }
      return 7;
    };

    const read = () => invoke(fory, registered, new Uint8Array([1]));
    if (outcome === "failure") {
      expect(read).toThrow("root read failed");
    } else {
      expect(read()).toBe(7);
    }

    expectRootStateCleared(readContext);
    expect(readContext.typeMetaCache.get(headerHash)).toBe(typeMeta);
  });
});
