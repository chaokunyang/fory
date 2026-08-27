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

import Fory, { TypeInfo, Type } from "../packages/core/index";
import { describe, expect, test } from "@jest/globals";
import { fromUint8Array } from "../packages/core/lib/platformBuffer";
import { TypeId } from "../packages/core/lib/type";

describe("fory", () => {
  test("defaults to compatible mode unless explicitly set", () => {
    expect(new Fory().config.compatible).toBe(true);
    expect(new Fory({ compatible: false }).config.compatible).toBe(false);
  });

  test("should deserialize null work", () => {
    const fory = new Fory({ compatible: false });

    expect(fory.deserialize(new Uint8Array([1, 253]))).toBe(null);
  });

  test("should deserialize xlang disable work", () => {
    const fory = new Fory({ compatible: false });
    try {
      // bit 0 = xlang flag, bit 1 = oob flag
      // value 0 means xlang is disabled
      fory.deserialize(new Uint8Array([0]));
      throw new Error("unreachable code");
    } catch (error) {
      expect(error.message).toBe("support crosslanguage mode only");
    }
  });

  test("should deserialize oob mode work", () => {
    const fory = new Fory({ compatible: false });
    try {
      // bit 0 = xlang flag, bit 1 = oob flag
      // value 3 = xlang (1) + oob (2)
      fory.deserialize(new Uint8Array([3]));
      throw new Error("unreachable code");
    } catch (error) {
      expect(error.message).toBe("outofband mode is not supported now");
    }
  });

  test("can serialize and deserialize primitive types", () => {
    const typeinfo = Type.int8();
    testTypeInfo(typeinfo, 123);

    const typeinfo2 = Type.int16();
    testTypeInfo(typeinfo2, 123);

    const typeinfo3 = Type.int32();
    testTypeInfo(typeinfo3, 123);

    const typeinfo4 = Type.bool();
    testTypeInfo(typeinfo4, true);

    // has precision problem
    // const typeinfo5 = Type.float()
    // testTypeInfo(typeinfo5, 123.456)

    const typeinfo6 = Type.float64();
    testTypeInfo(typeinfo6, 123.456789);

    const typeinfo7 = Type.binary();
    testTypeInfo(typeinfo7, new Uint8Array([1, 2, 3]), new Uint8Array([1, 2, 3]));

    const typeinfo8 = Type.string();
    testTypeInfo(typeinfo8, "123");
  });

  test.each(["serialize", "deserialize"] as const)(
    "freezes registration when %s starts and fails",
    (operation) => {
      const fory = new Fory({ compatible: false });
      fory.register(Type.struct(8101, {}));

      if (operation === "serialize") {
        expect(() => fory.serialize(Symbol("unsupported"))).toThrow();
      } else {
        expect(() => fory.deserialize(new Uint8Array([0]))).toThrow();
      }

      expect(() => fory.register(Type.struct(8102, {}))).toThrow();
    },
  );

  test("freezes direct resolver registration", () => {
    const fory = new Fory({ compatible: false });
    fory.serialize(1);

    expect(() => fory.typeResolver.registerSerializer(Type.struct(8105, {}))).toThrow();
    expect(fory.typeResolver.getSerializerById(TypeId.STRUCT, 8105)).toBeUndefined();
  });

  test("keeps rejected descriptor mutable", () => {
    const fory = new Fory({ compatible: false });
    const typeInfo = Type.struct(8106, {});
    fory.serialize(1);

    expect(() => fory.register(typeInfo)).toThrow();
    typeInfo.setNullable(true);
    expect(typeInfo.nullable).toBe(true);
  });

  test("rejects regeneration before codegen", () => {
    let generated = 0;
    const fory = new Fory({
      compatible: false,
      hooks: {
        afterCodeGenerated(code) {
          generated++;
          return code;
        },
      },
    });
    fory.serialize(1);
    const generatedBefore = generated;

    expect(() => fory.typeResolver.regenerateReadSerializer(Type.struct(8107, {}))).toThrow();
    expect(generated).toBe(generatedBefore);
  });

  test("keeps codegen callbacks from publishing registration", () => {
    let reenterRoot = false;
    let fory: Fory;
    fory = new Fory({
      compatible: false,
      hooks: {
        afterCodeGenerated(code) {
          if (reenterRoot) {
            reenterRoot = false;
            fory.serialize(1);
          }
          return code;
        },
      },
    });
    const typeResolver = fory.typeResolver as any;
    const internalBefore = Array.from(typeResolver.internalSerializer);
    const customBefore = Array.from(typeResolver.customSerializer.entries());
    const childType = Type.struct(8109, {
      value: Type.int32(),
    });
    const rootType = Type.struct(8110, {
      child: childType,
    });

    reenterRoot = true;
    expect(() => fory.register(rootType)).toThrow();

    expect(Array.from(typeResolver.internalSerializer)).toEqual(internalBefore);
    expect(Array.from(typeResolver.customSerializer.entries())).toEqual(customBefore);
    expect(typeResolver.getSerializerById(TypeId.STRUCT, childType.userTypeId)).toBeUndefined();
    expect(typeResolver.getSerializerById(TypeId.STRUCT, rootType.userTypeId)).toBeUndefined();
    rootType.setNullable(true);
    expect(rootType.nullable).toBe(true);
  });

  test("keeps failed generated factories local", () => {
    let failFactory = false;
    const fory = new Fory({
      compatible: false,
      hooks: {
        afterCodeGenerated(code) {
          if (!failFactory) {
            return code;
          }
          return code.replace(
            /return function \(typeResolver, serializerLookup, external, typeInfo, options([^)]*)\) \{/,
            (signature) => `${signature}\nthrow new Error("factory failure");`,
          );
        },
      },
    });
    const typeResolver = fory.typeResolver as any;
    const internalBefore = Array.from(typeResolver.internalSerializer);
    const customBefore = Array.from(typeResolver.customSerializer.entries());
    const childType = Type.struct(8111, {
      value: Type.int32(),
    });
    const rootType = Type.struct(8112, {
      child: childType,
    });

    failFactory = true;
    expect(() => fory.register(rootType)).toThrow();

    expect(Array.from(typeResolver.internalSerializer)).toEqual(internalBefore);
    expect(Array.from(typeResolver.customSerializer.entries())).toEqual(customBefore);
    expect(typeResolver.getSerializerById(TypeId.STRUCT, childType.userTypeId)).toBeUndefined();
    expect(typeResolver.getSerializerById(TypeId.STRUCT, rootType.userTypeId)).toBeUndefined();
    rootType.setNullable(true);
    expect(rootType.nullable).toBe(true);
  });

  test("initializes a published forward owner in place", () => {
    const fory = new Fory({ compatible: false });
    const forwardType = Type.struct(8113);
    const parent = fory.register(
      Type.struct(8114, {
        child: forwardType,
      }),
    );
    const forwardOwner = fory.typeResolver.getSerializerById(TypeId.STRUCT, forwardType.userTypeId);

    fory.register(
      Type.struct(8113, {
        value: Type.int32(),
      }),
    );

    expect(fory.typeResolver.getSerializerById(TypeId.STRUCT, forwardType.userTypeId)).toBe(
      forwardOwner,
    );
    const value = { child: { value: 7 } };
    expect(parent.deserialize(parent.serialize(value))).toEqual(value);
  });

  test.each(["serialize", "deserialize"] as const)(
    "freezes registration after registered %s succeeds",
    (operation) => {
      const typeInfo = Type.struct(8103, {});
      const source = new Fory({ compatible: false }).register(typeInfo.clone());
      const fory = new Fory({ compatible: false });
      const registered = fory.register(typeInfo);

      if (operation === "serialize") {
        registered.serialize({});
      } else {
        registered.deserialize(source.serialize({}));
      }

      expect(() => fory.register(Type.struct(8104, {}))).toThrow();
    },
  );

  function testTypeInfo(typeinfo: TypeInfo, input: any, expected?: any) {
    const fory = new Fory({ compatible: false });
    const serialize = fory.register(typeinfo);
    const result = serialize.deserialize(serialize.serialize(input));
    expect(result).toEqual(expected ?? input);
  }
});
