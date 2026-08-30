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
  test("restores generated root state after failure", () => {
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
    const input = bytes.subarray(0, bytes.length - 1);

    expect(() => invoke(readerFory, reader, input)).toThrow();
    expectRootStateCleared(readerFory.readContext);
    expect(invoke(readerFory, reader, bytes)).toEqual({ value: 7 });
  });

  test("restores logical tables after failure", () => {
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
      throw new Error("root read failed");
    };

    const read = () => invoke(fory, registered, new Uint8Array([1]));
    expect(read).toThrow();
    expectRootStateCleared(readContext);
    registered.serializer.readRef = () => {
      expectRootStateCleared(readContext);
      return 7;
    };
    expect(read()).toBe(7);

    expect(readContext.typeMetaCache.get(headerHash)).toBe(typeMeta);
  });
});

test("restores root write state after failure", () => {
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
    throw new Error("root write failed");
  };

  expect(() => registered.serialize(value)).toThrow();
  expect(writeContext.refWriter.writeObjects.size).toBe(0);
  expect(name.dynamicWriteStringId).toBe(-1);
  expect(typeMeta.dynamicTypeId).toBe(-1);
  expect(fory.serialize(7)).toBeDefined();
});

test("clears write state before serializer lookup", () => {
  const fory = new Fory({ compatible: true, ref: true });
  const registered = fory.register(Type.struct(7613, {}));
  const writeContext = (fory as any).writeContext;
  const typeMeta = TypeMeta.fromTypeInfo(Type.struct(7614, {}));
  const name = writeContext.metaStringWriter.encodeTypeName("PreviousRoot");
  const value = {};

  registered.serializer.writeRef = () => {
    writeContext.refWriter.writeRef(value);
    writeContext.metaStringWriter.writeBytes(writeContext.writer, name);
    writeContext.writeTypeMeta(typeMeta, typeMeta.toBytes());
  };
  expect(registered.serialize(value)).toBeDefined();

  expect(() => fory.serialize(1, null as any)).toThrow();
  expect(writeContext.refWriter.writeObjects.size).toBe(0);
  expect(name.dynamicWriteStringId).toBe(-1);
  expect(typeMeta.dynamicTypeId).toBe(-1);
});

test("reuses root write metastring owners", () => {
  const fory = new Fory({ compatible: true });
  const registered = fory.register(Type.struct(7609, {}));
  const writeContext = (fory as any).writeContext;
  const name = writeContext.metaStringWriter.encodeTypeName("RootName");

  registered.serializer.writeRef = () => {
    writeContext.metaStringWriter.writeBytes(writeContext.writer, name);
  };

  expect(registered.serialize({})).toBeDefined();
  const owners = writeContext.metaStringWriter.metaStringOwners;
  expect(owners).toHaveLength(1);
  expect(name.dynamicWriteStringId).toBe(0);

  expect(registered.serialize({})).toBeDefined();
  expect(writeContext.metaStringWriter.metaStringOwners).toBe(owners);
  expect(owners).toHaveLength(1);
  expect(name.dynamicWriteStringId).toBe(0);
});

test("reuses write metadata owners", () => {
  const fory = new Fory({ compatible: true });
  const registered = fory.register(Type.struct(7610, {}));
  const writeContext = (fory as any).writeContext;
  const typeMeta = TypeMeta.fromTypeInfo(Type.struct(7611, {}));

  registered.serializer.writeRef = () => {
    writeContext.writeTypeMeta(typeMeta, typeMeta.toBytes());
  };

  expect(registered.serialize({})).toBeDefined();
  const owners = writeContext.typeMetaOwners;
  expect(owners).toHaveLength(1);
  expect(typeMeta.dynamicTypeId).toBe(0);

  expect(registered.serialize({})).toBeDefined();
  expect(writeContext.typeMetaOwners).toBe(owners);
  expect(owners).toHaveLength(1);
  expect(typeMeta.dynamicTypeId).toBe(0);
});

test.each([8192, 8193])("bounds %s metastring owners", (ownerCount) => {
  const fory = new Fory({ compatible: true });
  const writeContext = (fory as any).writeContext;
  const metaStringWriter = writeContext.metaStringWriter;

  for (let i = 0; i < ownerCount; i++) {
    const owner = metaStringWriter.encodeTypeName(`name-${i}`);
    metaStringWriter.writeBytes(writeContext.writer, owner);
  }
  const owners = metaStringWriter.metaStringOwners;

  writeContext.reset();
  if (ownerCount === 8192) {
    expect(metaStringWriter.metaStringOwners).toBe(owners);
  } else {
    expect(metaStringWriter.metaStringOwners).not.toBe(owners);
    expect(metaStringWriter.metaStringOwners).toHaveLength(0);
  }
  const nextOwner = metaStringWriter.encodeTypeName("next-root");
  metaStringWriter.writeBytes(writeContext.writer, nextOwner);
  expect(nextOwner.dynamicWriteStringId).toBe(0);
});

test.each([8192, 8193])("bounds %s type metadata owners", (ownerCount) => {
  const fory = new Fory({ compatible: true });
  const writeContext = (fory as any).writeContext;
  const typeMetaOwners = Array.from({ length: ownerCount }, () => ({ dynamicTypeId: -1 }));
  const bytes = new Uint8Array();

  for (const owner of typeMetaOwners) {
    writeContext.writeTypeMeta(owner, bytes);
  }
  const owners = writeContext.typeMetaOwners;

  writeContext.reset();
  expect(typeMetaOwners.every((owner) => owner.dynamicTypeId === -1)).toBe(true);
  if (ownerCount === 8192) {
    expect(writeContext.typeMetaOwners).toBe(owners);
  } else {
    expect(writeContext.typeMetaOwners).not.toBe(owners);
    expect(writeContext.typeMetaOwners).toHaveLength(0);
  }
  const nextOwner = { dynamicTypeId: -1 };
  writeContext.writeTypeMeta(nextOwner, bytes);
  expect(nextOwner.dynamicTypeId).toBe(0);
});

test("releases failed write buffer", () => {
  const fory = new Fory({ compatible: true });
  const registered = fory.register(Type.struct(7608, {}));
  const writer = (fory as any).writeContext.writer;

  registered.serializer.writeRef = () => {
    writer.buffer(new Uint8Array(4 * 1024 * 1024));
    throw new Error("root write failed");
  };

  expect(() => registered.serialize({})).toThrow();
  expect(writer.getPlatformBuffer().byteLength).toBeLessThan(4 * 1024 * 1024);
  expect(fory.serialize(7)).toBeDefined();
});

test("clears failed read refs", () => {
  const fory = new Fory({ compatible: false, ref: true });
  const registered = fory.register(Type.struct(7612, {}));
  const refReader = (fory as any).readContext.refReader;

  registered.serializer.readRef = () => {
    refReader.reference({});
    throw new Error("root read failed");
  };
  expect(() => registered.deserialize(new Uint8Array([1]))).toThrow();
  expect(refReader.readObjects).toHaveLength(0);

  registered.serializer.readRef = () => {
    expect(refReader.readObjects).toHaveLength(0);
    return {};
  };
  expect(registered.deserialize(new Uint8Array([1]))).toEqual({});
});
