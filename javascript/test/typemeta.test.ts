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
import { ReadContext } from "../packages/core/lib/context";
import { TypeMeta } from "../packages/core/lib/meta/TypeMeta";
import { BinaryReader } from "../packages/core/lib/reader";
import { BinaryWriter } from "../packages/core/lib/writer";
import { describe, expect, test } from "@jest/globals";

const COMPRESS_META_FLAG = 1n << 8n;
const RESERVED_META_FLAGS = 0b111n << 9n;
const META_SIZE_MASK = 0xffn;
const HASH_SHIFT_BITS = 12n;

describe("typemeta", () => {
  test("writes TypeMeta header bits in the xlang layout", () => {
    const typeInfo = Type.struct(7001, {
      fullName: Type.string().setId(1),
      age: Type.int32().setId(2),
    });

    const bytes = TypeMeta.fromTypeInfo(typeInfo).toBytes();
    const header = new DataView(
      bytes.buffer,
      bytes.byteOffset,
      bytes.byteLength,
    ).getBigUint64(0, true);

    expect(Number(header & META_SIZE_MASK)).toBe(bytes.length - 8);
    expect((header & COMPRESS_META_FLAG) !== 0n).toBe(false);
    expect((header & RESERVED_META_FLAGS) !== 0n).toBe(false);
    expect(header >> HASH_SHIFT_BITS).toBeGreaterThan(0n);
    expect((bytes[8] & 0x80) !== 0).toBe(true);
  });

  test("keeps tagged direct payload order grouped instead of field-id ordered", () => {
    const typeMeta = TypeMeta.fromTypeInfo(
      Type.struct(7005, {
        stringValue: Type.string().setId(1),
        mapValue: Type.map(Type.string(), Type.int32()).setId(2),
        intValue: Type.int32().setId(10),
      }),
    );

    expect(typeMeta.getFieldInfo().map((field) => field.fieldName)).toEqual([
      "intValue",
      "stringValue",
      "mapValue",
    ]);
  });

  test("writes the zero size extension when the TypeMeta body is exactly 0xFF bytes", () => {
    const typeMeta = TypeMeta.fromTypeInfo(Type.struct(7003, {})) as any;
    const body = new Uint8Array(0xff);
    const bytes = typeMeta.prependHeader(body, false) as Uint8Array;
    const reader = new BinaryReader({});

    expect(bytes).toHaveLength(8 + 1 + body.length);
    expect(bytes[8]).toBe(0);

    reader.reset(bytes);
    const header = TypeMeta.readHeader(reader);
    TypeMeta.skipBody(reader, header);
    expect(reader.readGetCursor()).toBe(bytes.length);
  });

  test("validates TypeMeta body hash before caching parsed metadata", () => {
    const bytes = TypeMeta.fromTypeInfo(
      Type.struct(7006, {
        value: Type.string().setId(1),
      }),
    ).toBytes();
    const malformed = new Uint8Array(bytes);
    malformed[malformed.length - 1] ^= 1;

    const parseReader = new BinaryReader({});
    parseReader.reset(malformed);
    expect(() => TypeMeta.fromBytes(parseReader)).toThrow(
      "TypeMeta metadata hash mismatch",
    );

    const skipReader = new BinaryReader({});
    skipReader.reset(bytes);
    const header = TypeMeta.readHeader(skipReader);
    TypeMeta.skipBody(skipReader, header);
    expect(skipReader.readGetCursor()).toBe(bytes.length);
  });

  test("TypeMeta header cache hit skips the current body size", () => {
    const header = 0xffn;
    const typeMeta = TypeMeta.fromTypeInfo(Type.struct(7010, {}));
    const writer = new BinaryWriter({});
    writer.writeVarUInt32(0);
    writer.writeUint64(header);
    writer.writeVarUInt32(0);
    writer.buffer(new Uint8Array(0xff));
    writer.buffer(new Uint8Array([0x7b]));

    const config = { ref: false, useSliceString: false, hooks: {} } as any;
    const context = new ReadContext(
      {
        config,
        trackingRef: false,
        computeTypeId: (typeInfo: any) => typeInfo.typeId,
        getSerializerById: () => undefined,
        getSerializerByName: () => undefined,
        getSerializerByData: () => undefined,
        isCompatible: () => false,
        regenerateReadSerializer: () => {
          throw new Error("unused");
        },
      } as any,
      config,
    );
    (context as any).typeMetaCache.set(header, typeMeta);
    context.reset(writer.dump());

    expect(context.readTypeMeta()).toBe(typeMeta);
    expect(context.reader.readUint8()).toBe(0x7b);
  });

  test("encodes extended id-registered struct field counts without the name bit", () => {
    const fields: Record<string, any> = {};
    for (let i = 0; i < 32; i++) {
      fields[`field${i}`] = Type.int32().setId(i + 1);
    }
    const bytes = TypeMeta.fromTypeInfo(Type.struct(7201, fields)).toBytes();
    const reader = new BinaryReader({});
    const bodyOffset = typeMetaBodyOffset(bytes);

    expect(bytes[bodyOffset] & 0x1f).toBe(0x1f);
    expect(bytes[bodyOffset] & 0x20).toBe(0);

    reader.reset(bytes);
    const decoded = TypeMeta.fromBytes(reader);
    expect(decoded.getFieldInfo()).toHaveLength(32);
  });

  test("regenerates compatible named serializers when schema changes but field count stays the same", () => {
    const writerFory = new Fory({ compatible: true });
    const readerFory = new Fory({ compatible: true });

    const writerType = Type.struct("example.item", {
      value: Type.string(),
    });
    const readerType = Type.struct("example.item", {
      value: Type.int32(),
    });

    const bytes = writerFory.register(writerType).serialize({ value: "hello" });
    const result = readerFory.register(readerType).deserialize(bytes);

    expect(result).toEqual({ value: "hello" });
  });

  test("remaps compatible tag-id fields onto local property names during regeneration", () => {
    const writerFory = new Fory({ compatible: true });
    const readerFory = new Fory({ compatible: true });

    const writerType = Type.struct(7002, {
      fullName: Type.string().setId(1),
      note: Type.string().setId(2),
    });
    const readerType = Type.struct(7002, {
      name: Type.string().setId(1),
      alias: Type.int32().setId(2),
    });

    const bytes = writerFory.register(writerType).serialize({
      fullName: "Alice",
      note: "ally",
    });
    const result = readerFory.register(readerType).deserialize(bytes);

    expect(result).toEqual({
      name: "Alice",
      alias: "ally",
    });
  });

  test("adapts only immediate compatible list and dense array field pairs", () => {
    const writerFory = new Fory({ compatible: true });
    const readerFory = new Fory({ compatible: true });

    const writerType = Type.struct(7211, {
      values: Type.list(Type.int32({ encoding: "fixed" })).setId(1),
    });
    const readerType = Type.struct(7211, {
      values: Type.int32Array().setId(1),
    });

    const bytes = writerFory.register(writerType).serialize({
      values: [1, 2, 3],
    });
    const result = readerFory.register(readerType).deserialize(bytes);

    expect(result.values).toBeInstanceOf(Int32Array);
    expect(Array.from(result.values)).toEqual([1, 2, 3]);
  });

  test("adapts compatible dense array field to immediate list field", () => {
    const writerFory = new Fory({ compatible: true });
    const readerFory = new Fory({ compatible: true });

    const writerType = Type.struct(7213, {
      values: Type.int32Array().setId(1),
    });
    const readerType = Type.struct(7213, {
      values: Type.list(Type.int32({ encoding: "fixed" })).setId(1),
    });

    const bytes = writerFory.register(writerType).serialize({
      values: new Int32Array([1, 2, 3]),
    });
    const result = readerFory.register(readerType).deserialize(bytes);

    expect(Array.isArray(result.values)).toBe(true);
    expect(result).toEqual({ values: [1, 2, 3] });
  });

  test("rejects compatible list to dense array when payload has nullable elements", () => {
    const writerFory = new Fory({ compatible: true });
    const readerFory = new Fory({ compatible: true });

    const writerType = Type.struct(7212, {
      values: Type.list(
        Type.int32({ encoding: "fixed" }).setNullable(true),
      ).setId(1),
    });
    const readerType = Type.struct(7212, {
      values: Type.int32Array().setId(1),
    });

    const bytes = writerFory.register(writerType).serialize({
      values: [1, null, 3],
    });

    expect(() => readerFory.register(readerType).deserialize(bytes)).toThrow();
  });

  test("keeps compatible named schema evolution working when field count differs", () => {
    const writerFory = new Fory({ compatible: true });
    const readerFory = new Fory({ compatible: true });

    const writerType = Type.struct("example.foo", {
      bar: Type.string(),
      bar2: Type.int32(),
    });
    const readerType = Type.struct("example.foo", {
      bar: Type.string(),
    });

    const bytes = writerFory.register(writerType).serialize({
      bar: "hello",
      bar2: 123,
    });
    const result = readerFory.register(readerType).deserialize(bytes);

    expect(result).toEqual({
      bar: "hello",
      bar2: 123,
    });
  });

  test("remaps regenerated compatible field names onto local snake_case properties", () => {
    const writerFory = new Fory({ compatible: true });
    const readerFory = new Fory({ compatible: true });

    class WriterHolder {
      animalMap = new Map<string, number>();
      marker = 0;
    }
    Type.struct(7004, {
      animalMap: Type.map(Type.string(), Type.any()),
      marker: Type.int32(),
    })(WriterHolder);

    class ReaderHolder {
      animal_map = new Map<string, number>();
    }
    Type.struct(7004, {
      animal_map: Type.map(Type.string(), Type.any()),
    })(ReaderHolder);

    const writerReg = writerFory.register(WriterHolder);
    const readerReg = readerFory.register(ReaderHolder);

    const value = new WriterHolder();
    value.animalMap.set("dog", 7);
    value.marker = 99;

    const result = readerReg.deserialize(writerReg.serialize(value));

    expect(result).toBeInstanceOf(ReaderHolder);
    expect(result.animal_map.get("dog")).toBe(7);
    expect(
      (result as ReaderHolder & { animalMap?: Map<string, number> }).animalMap,
    ).toBeUndefined();
    expect((result as ReaderHolder & { marker?: number }).marker).toBe(99);
  });

  test("skips unknown named custom fields by falling back to any when no local field exists", () => {
    const writerFory = new Fory({ compatible: true });
    const readerFory = new Fory({ compatible: true });

    class MyExt {
      id = 0;
    }
    Type.ext("my_ext")(MyExt);

    const customSerializer = {
      write: (writeContext: any, value: MyExt) => {
        writeContext.writeVarInt32(value.id);
      },
      read: (readContext: any, result: MyExt) => {
        result.id = readContext.readVarInt32();
      },
    };

    writerFory.register(MyExt, customSerializer);
    readerFory.register(MyExt, customSerializer);

    class WriterWrapper {
      note = "";
      myExt = new MyExt();
    }
    Type.struct("example.wrapper", {
      note: Type.string(),
      myExt: Type.ext("my_ext"),
    })(WriterWrapper);

    class EmptyWrapper {}
    Type.struct("example.wrapper", {})(EmptyWrapper);

    const writerReg = writerFory.register(WriterWrapper);
    const readerReg = readerFory.register(EmptyWrapper);

    const value = new WriterWrapper();
    value.note = "hello";
    value.myExt.id = 42;

    const result = readerReg.deserialize(writerReg.serialize(value));

    expect(result).toBeInstanceOf(EmptyWrapper);
  });

  test("skips unknown compatible enum fields when regenerating an empty reader", () => {
    const writerFory = new Fory({ compatible: true });
    const readerFory = new Fory({ compatible: true });

    const TestEnum = {
      VALUE_A: 0,
      VALUE_B: 1,
      VALUE_C: 2,
    };
    writerFory.register(Type.enum(7101, TestEnum));
    readerFory.register(Type.enum(7101, TestEnum));

    class WriterStruct {
      f1 = TestEnum.VALUE_A;
      f2 = TestEnum.VALUE_B;
    }
    Type.struct(7102, {
      f1: Type.enum(7101, TestEnum),
      f2: Type.enum(7101, TestEnum),
    })(WriterStruct);

    class EmptyStruct {}
    Type.struct(7102, {})(EmptyStruct);

    const writerReg = writerFory.register(WriterStruct);
    const readerReg = readerFory.register(EmptyStruct);

    const value = new WriterStruct();
    const result = readerReg.deserialize(writerReg.serialize(value));

    expect(result).toBeInstanceOf(EmptyStruct);
  });

  test("skips unknown enum and named custom fields together during compatible regeneration", () => {
    const writerFory = new Fory({ compatible: true });
    const readerFory = new Fory({ compatible: true });

    const Color = {
      Green: 0,
      Red: 1,
      Blue: 2,
      White: 3,
    };
    writerFory.register(Type.enum("color", Color));
    readerFory.register(Type.enum("color", Color));

    class MyExt {
      id = 0;
    }
    Type.ext("my_ext")(MyExt);

    const customSerializer = {
      write: (writeContext: any, value: MyExt) => {
        writeContext.writeVarInt32(value.id);
      },
      read: (readContext: any, result: MyExt) => {
        result.id = readContext.readVarInt32();
      },
    };

    writerFory.register(MyExt, customSerializer);
    readerFory.register(MyExt, customSerializer);

    class MyStruct {
      id = 0;
    }
    Type.struct("my_struct", {
      id: Type.int32(),
    })(MyStruct);

    writerFory.register(MyStruct);
    readerFory.register(MyStruct);

    class WriterWrapper {
      color = Color.White;
      myStruct = new MyStruct();
      myExt = new MyExt();
    }
    Type.struct("my_wrapper", {
      color: Type.enum("color", Color),
      myStruct: Type.struct("my_struct"),
      myExt: Type.ext("my_ext"),
    })(WriterWrapper);

    class EmptyWrapper {}
    Type.struct("my_wrapper", {})(EmptyWrapper);

    const writerReg = writerFory.register(WriterWrapper);
    const readerReg = readerFory.register(EmptyWrapper);

    const value = new WriterWrapper();
    value.myStruct.id = 42;
    value.myExt.id = 43;

    const result = readerReg.deserialize(writerReg.serialize(value));

    expect(result).toBeInstanceOf(EmptyWrapper);
  });
});

function typeMetaBodyOffset(bytes: Uint8Array) {
  const reader = new BinaryReader({});
  reader.reset(bytes);
  const header = TypeMeta.readHeader(reader);
  if ((header & META_SIZE_MASK) === META_SIZE_MASK) {
    reader.readVarUInt32();
  }
  return reader.readGetCursor();
}
