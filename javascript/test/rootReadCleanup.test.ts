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
  readContext.metaStringReader.names.push("stale");
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
      expect(read).toThrow();
    } else {
      expect(read()).toBe(7);
    }

    expectRootStateCleared(readContext);
    expect(readContext.typeMetaCache.get(headerHash)).toBe(typeMeta);
  });
});

test("retains bounded read metadata", () => {
  const fory = new Fory({ compatible: true });
  const readContext = (fory as any).readContext;
  const typeMeta = TypeMeta.fromTypeInfo(Type.struct(7604, {}));

  readContext.typeMeta.push(...new Array(8192).fill(typeMeta));
  const bounded = readContext.typeMeta;
  readContext.metaStringReader.names.push(...new Array(8192).fill("stale"));
  const boundedNames = readContext.metaStringReader.names;
  readContext.resetRootState();
  expect(readContext.typeMeta).toBe(bounded);
  expect(readContext.metaStringReader.names).toBe(boundedNames);
  expect(readContext.typeMeta).toHaveLength(0);
  expect(readContext.metaStringReader.names).toHaveLength(0);

  readContext.typeMeta.push(...new Array(8193).fill(typeMeta));
  const oversized = readContext.typeMeta;
  readContext.metaStringReader.names.push(...new Array(8193).fill("stale"));
  const oversizedNames = readContext.metaStringReader.names;
  readContext.resetRootState();
  expect(readContext.typeMeta).not.toBe(oversized);
  expect(readContext.metaStringReader.names).not.toBe(oversizedNames);
  expect(readContext.typeMeta).toHaveLength(0);
  expect(readContext.metaStringReader.names).toHaveLength(0);
});

test("retains bounded write metadata", () => {
  const fory = new Fory({ compatible: true });
  const writeContext = (fory as any).writeContext;
  const typeMetaOwners = Array.from({ length: 8192 }, (_, dynamicTypeId) => ({
    dynamicTypeId,
  }));
  const metaStringOwners = Array.from({ length: 8192 }, (_, dynamicWriteStringId) => ({
    dynamicWriteStringId,
  }));

  writeContext.disposeTypeMetaOwners.push(...typeMetaOwners);
  const bounded = writeContext.disposeTypeMetaOwners;
  writeContext.metaStringWriter.disposeMetaStringBytes.push(...metaStringOwners);
  const boundedNames = writeContext.metaStringWriter.disposeMetaStringBytes;
  writeContext.reset();
  expect(writeContext.disposeTypeMetaOwners).toBe(bounded);
  expect(writeContext.metaStringWriter.disposeMetaStringBytes).toBe(boundedNames);

  writeContext.disposeTypeMetaOwners.push(...typeMetaOwners, { dynamicTypeId: 8192 });
  const oversized = writeContext.disposeTypeMetaOwners;
  writeContext.metaStringWriter.disposeMetaStringBytes.push(...metaStringOwners, {
    dynamicWriteStringId: 8192,
  });
  const oversizedNames = writeContext.metaStringWriter.disposeMetaStringBytes;
  writeContext.reset();
  expect(writeContext.disposeTypeMetaOwners).not.toBe(oversized);
  expect(writeContext.metaStringWriter.disposeMetaStringBytes).not.toBe(oversizedNames);
});

test.each(["success", "failure"] as const)("clears root write state after %s", (outcome) => {
  const fory = new Fory({ compatible: true, ref: true });
  const registered = fory.register(Type.struct(7606, {}));
  const writeContext = (fory as any).writeContext;
  const typeMeta = TypeMeta.fromTypeInfo(Type.struct(7607, {}));
  const name = writeContext.metaStringWriter.encodeTypeName("FailedRoot");
  const value = {};

  registered.serializer.writeRef = () => {
    writeContext.refWriter.writeRef(value);
    writeContext.metaStringWriter.writeBytes(writeContext.writer, name);
    writeContext.writeTypeMeta(typeMeta, typeMeta.toBytes());
    if (outcome === "failure") {
      throw new Error("root write failed");
    }
  };

  if (outcome === "failure") {
    expect(() => registered.serialize(value)).toThrow("root write failed");
  } else {
    expect(registered.serialize(value)).toBeDefined();
  }
  expect(writeContext.refWriter.writeObjects.size).toBe(0);
  expect(writeContext.metaStringWriter.disposeMetaStringBytes).toHaveLength(0);
  expect(writeContext.disposeTypeMetaOwners).toHaveLength(0);
  expect(name.dynamicWriteStringId).toBe(-1);
  expect(typeMeta.dynamicTypeId).toBe(-1);
  expect(writeContext.writer.writeGetCursor()).toBe(0);
  expect(fory.serialize(7)).toBeDefined();
});

test("releases a failed root write buffer", () => {
  const fory = new Fory({ compatible: true });
  const registered = fory.register(Type.struct(7608, {}));
  const writer = (fory as any).writeContext.writer;

  registered.serializer.writeRef = () => {
    writer.buffer(new Uint8Array(4 * 1024 * 1024));
    throw new Error("root write failed");
  };

  expect(() => registered.serialize({})).toThrow("root write failed");
  expect(writer.getPlatformBuffer().byteLength).toBeLessThan(4 * 1024 * 1024);
  expect(writer.writeGetCursor()).toBe(0);
});
