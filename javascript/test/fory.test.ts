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

  test.each(["serialize", "deserialize"] as const)("freezes on failed %s", (operation) => {
    const fory = new Fory({ compatible: false });
    fory.register(Type.struct(8101, {}));

    if (operation === "serialize") {
      expect(() => fory.serialize(Symbol("unsupported"))).toThrow();
    } else {
      expect(() => fory.deserialize(new Uint8Array([0]))).toThrow();
    }

    expect(() => fory.register(Type.struct(8102, {}))).toThrow();
  });

  test("keeps rejected schema mutable", () => {
    const fory = new Fory({ compatible: false });
    const typeInfo = Type.struct(8106, {});
    fory.serialize(1);

    expect(() => fory.register(typeInfo)).toThrow();
    typeInfo.setNullable(true);
    expect(typeInfo.nullable).toBe(true);
  });

  test("keeps callback failure local", () => {
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
    expect(() => rootType.setNullable(true)).toThrow();
  });

  test("keeps factory failure local", () => {
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
    expect(() => rootType.setNullable(true)).toThrow();
  });

  test("rejects unresolved schema", () => {
    const fory = new Fory({ compatible: false });
    const typeResolver = fory.typeResolver as any;
    const internalBefore = Array.from(typeResolver.internalSerializer);
    const customBefore = Array.from(typeResolver.customSerializer.entries());
    const forwardType = Type.struct(8113);
    const parentType = Type.struct(8114, { child: forwardType });

    expect(() => fory.register(parentType)).toThrow();

    expect(Array.from(typeResolver.internalSerializer)).toEqual(internalBefore);
    expect(Array.from(typeResolver.customSerializer.entries())).toEqual(customBefore);
    expect(typeResolver.getSerializerById(TypeId.STRUCT, forwardType.userTypeId)).toBeUndefined();
    expect(typeResolver.getSerializerById(TypeId.STRUCT, parentType.userTypeId)).toBeUndefined();

    fory.register(
      Type.struct(8113, {
        value: Type.int32(),
      }),
    );
    const parent = fory.register(
      Type.struct(8114, {
        child: Type.struct(8113),
      }),
    );
    const value = { child: { value: 7 } };
    expect(parent.deserialize(parent.serialize(value))).toEqual(value);
  });

  test("registers empty roots", () => {
    const registered = new Fory({ compatible: false }).register(Type.struct(8122, {}));

    expect(registered.deserialize(registered.serialize({}))).toEqual({});
  });

  test("registers self recursion", () => {
    const nodeType = Type.struct(8123, {
      value: Type.int32(),
      next: Type.struct(8123).setNullable(true).setTrackingRef(true),
    });
    const registered = new Fory({ compatible: false, ref: true }).register(nodeType);
    const value: any = { value: 7 };
    value.next = value;

    const result: any = registered.deserialize(registered.serialize(value));
    expect(result.value).toBe(7);
    expect(result.next).toBe(result);
  });

  test("registers mutual recursion", () => {
    const rightType = Type.struct(8125, {
      value: Type.string(),
      left: Type.struct(8124).setNullable(true),
    });
    const leftType = Type.struct(8124, {
      value: Type.int32(),
      right: rightType,
    });
    const registered = new Fory({ compatible: false }).register(leftType);
    const value = { value: 7, right: { value: "right", left: null } };

    expect(registered.deserialize(registered.serialize(value))).toEqual(value);
  });

  test.each(["userTypeId", "name", "options"] as const)("rejects root %s mutation", (change) => {
    let mutateDescriptor: (() => void) | undefined;
    const fory = new Fory({
      compatible: false,
      hooks: {
        afterCodeGenerated(code) {
          const mutate = mutateDescriptor;
          mutateDescriptor = undefined;
          mutate?.();
          return code;
        },
      },
    });
    const typeInfo =
      change === "name"
        ? Type.struct("stable.Root", { value: Type.int32() })
        : Type.struct(8115, { value: Type.int32() });
    const typeResolver = fory.typeResolver as any;
    const internalBefore = Array.from(typeResolver.internalSerializer);
    const customBefore = Array.from(typeResolver.customSerializer.entries());
    if (change === "userTypeId") {
      mutateDescriptor = () => {
        typeInfo.userTypeId = 9115;
      };
    } else if (change === "name") {
      mutateDescriptor = () => {
        typeInfo.named = "changed$Root";
      };
    } else {
      mutateDescriptor = () => {
        typeInfo.options!.props!.extra = Type.string();
      };
    }

    expect(() => fory.register(typeInfo)).toThrow();

    expect(Array.from(typeResolver.internalSerializer)).toEqual(internalBefore);
    expect(Array.from(typeResolver.customSerializer.entries())).toEqual(customBefore);
    expect(() => typeInfo.setNullable(true)).toThrow();
    if (change === "options") {
      expect(() => {
        typeInfo.options!.props!.afterFailure = Type.bool();
      }).toThrow();
    }
  });

  test("rejects nested mutation", () => {
    let mutateDescriptor: (() => void) | undefined;
    const fory = new Fory({
      compatible: false,
      hooks: {
        afterCodeGenerated(code) {
          const mutate = mutateDescriptor;
          mutateDescriptor = undefined;
          mutate?.();
          return code;
        },
      },
    });
    const childType = Type.struct(8116, { value: Type.int32() });
    const rootType = Type.struct(8117, { child: childType });
    const typeResolver = fory.typeResolver as any;
    const internalBefore = Array.from(typeResolver.internalSerializer);
    const customBefore = Array.from(typeResolver.customSerializer.entries());
    mutateDescriptor = () => {
      childType.options!.props!.extra = Type.string();
    };

    expect(() => fory.register(rootType)).toThrow();

    expect(Array.from(typeResolver.internalSerializer)).toEqual(internalBefore);
    expect(Array.from(typeResolver.customSerializer.entries())).toEqual(customBefore);
    expect(typeResolver.getSerializerById(TypeId.STRUCT, childType.userTypeId)).toBeUndefined();
    expect(typeResolver.getSerializerById(TypeId.STRUCT, rootType.userTypeId)).toBeUndefined();
    expect(() => rootType.setNullable(true)).toThrow();
    expect(() => childType.setNullable(true)).toThrow();
  });

  test("resolves later definitions", () => {
    const itemType = Type.struct(8118, { value: Type.int32() });
    const registered = new Fory({ compatible: false }).register(
      Type.struct(8119, {
        first: Type.struct(8118),
        definition: itemType,
      }),
    );
    const value = {
      first: { value: 1 },
      definition: { value: 2 },
    };

    expect(registered.deserialize(registered.serialize(value as any))).toEqual(value);
  });

  test("rejects conflicting definitions", () => {
    for (const reversed of [false, true]) {
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
      generated = 0;
      const first = Type.struct(8127, { firstValue: Type.int32() });
      const second = Type.struct(8127, { secondValue: Type.string() });
      const props = reversed ? { second, first } : { first, second };
      const root = Type.struct(8128, props);

      expect(() => fory.register(root)).toThrow();
      expect(generated).toBe(0);
      expect(fory.typeResolver.getSerializerById(TypeId.STRUCT, 8127)).toBeUndefined();
      expect(fory.typeResolver.getSerializerById(TypeId.STRUCT, 8128)).toBeUndefined();
    }
  });

  test("seals replaced schema options", () => {
    const original = Type.struct(8126, { oldValue: Type.int32() });
    const replacement: {
      props: Record<string, TypeInfo>;
      withConstructor: boolean;
    } = {
      props: { value: Type.string() },
      withConstructor: false,
    };
    let replaceOptions = true;
    const typeInfo = new Proxy(original, {
      defineProperty(target, property, descriptor) {
        if (property === "options" && replaceOptions) {
          replaceOptions = false;
          target.options = replacement;
        }
        return Reflect.defineProperty(target, property, descriptor);
      },
    });
    const registered = new Fory({ compatible: false }).register(typeInfo);
    const value = { value: "sealed" };

    expect(registered.deserialize(registered.serialize(value as any))).toEqual(value);
    expect(() => {
      replacement.props.extra = Type.bool();
    }).toThrow();
  });

  test("seals recursive schemas", () => {
    const left = Type.struct(8120, {});
    const right = Type.struct(8121, { left });
    left.options!.props!.right = right;

    new Fory({ compatible: false }).register(left);

    expect(() => left.setNullable(true)).toThrow();
    expect(() => right.setTrackingRef(true)).toThrow();
    left.dynamicTypeId = 7;
    expect(left.dynamicTypeId).toBe(7);
  });

  test.each(["serialize", "deserialize"] as const)("freezes after successful %s", (operation) => {
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
  });

  function testTypeInfo(typeinfo: TypeInfo, input: any, expected?: any) {
    const fory = new Fory({ compatible: false });
    const serialize = fory.register(typeinfo);
    const result = serialize.deserialize(serialize.serialize(input));
    expect(result).toEqual(expected ?? input);
  }
});
