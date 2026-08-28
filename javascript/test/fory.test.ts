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

  test("rejects unresolved extension", () => {
    const fory = new Fory({ compatible: false });
    const root = Type.struct(8151, { value: Type.ext(8152) });

    expect(() => fory.register(root)).toThrow();
    expect(fory.typeResolver.getSerializerById(TypeId.STRUCT, 8151)).toBeUndefined();
    expect(fory.typeResolver.getSerializerById(TypeId.EXT, 8152)).toBeUndefined();
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
    const identities: (number | { namespace: string; typeName: string })[] = [
      8127,
      { namespace: "test", typeName: "Conflict" },
    ];
    for (const identity of identities) {
      const definitionPairs = [
        [
          Type.struct(identity, { firstValue: Type.int32() }),
          Type.struct(identity, { secondValue: Type.string() }),
        ],
        [Type.enum(identity, { FIRST: 1 }), Type.enum(identity, { FIRST: 1 })],
        [Type.union(identity, { 1: Type.int32() }), Type.union(identity, { 1: Type.int32() })],
      ];
      for (const [first, second] of definitionPairs) {
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
          const props = reversed ? { second, first } : { first, second };

          expect(() => fory.register(Type.struct(8128, props))).toThrow();
          expect(generated).toBe(0);
          expect(fory.typeResolver.getSerializerById(TypeId.STRUCT, 8128)).toBeUndefined();
        }
      }
    }
  });

  test("rejects mixed type families", () => {
    const identities: (number | { namespace: string; typeName: string })[] = [
      8129,
      { namespace: "test", typeName: "Mixed" },
    ];
    for (const identity of identities) {
      const types = [
        Type.struct(identity, { value: Type.int32() }),
        Type.enum(identity, { VALUE: 1 }),
        Type.ext(identity),
        Type.union(identity, { 1: Type.string() }),
      ];
      for (let left = 0; left < types.length; left++) {
        for (let right = left + 1; right < types.length; right++) {
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
            const first = types[reversed ? right : left];
            const second = types[reversed ? left : right];

            expect(() => fory.register(Type.struct(8130, { first, second }))).toThrow();
            expect(generated).toBe(0);
            expect(fory.typeResolver.getSerializerById(TypeId.STRUCT, 8130)).toBeUndefined();
          }
        }
      }
    }
  });

  test("reuses shared definitions", () => {
    const struct = Type.struct(8131, { value: Type.int32() });
    const enumType = Type.enum(8132, { VALUE: 1 });
    const union = Type.union(8133, { 1: Type.string() });
    const registered = new Fory({ compatible: false }).register(
      Type.struct(8134, {
        firstStruct: struct,
        secondStruct: struct.clone(),
        firstEnum: enumType,
        secondEnum: enumType.clone(),
        firstUnion: union,
        secondUnion: union.clone(),
      }),
    );
    const value = {
      firstStruct: { value: 1 },
      secondStruct: { value: 2 },
      firstEnum: 1,
      secondEnum: 1,
      firstUnion: { case: 1, value: "first" },
      secondUnion: { case: 1, value: "second" },
    };

    expect(registered.deserialize(registered.serialize(value as any))).toEqual(value);
  });

  test("rejects reentrant family conflict", () => {
    let publishConflict = false;
    let fory: Fory;
    fory = new Fory({
      compatible: false,
      hooks: {
        afterCodeGenerated(code) {
          if (publishConflict) {
            publishConflict = false;
            fory.register(Type.enum(8135, { VALUE: 1 }));
          }
          return code;
        },
      },
    });
    publishConflict = true;

    expect(() =>
      fory.register(
        Type.struct(8136, {
          value: Type.struct(8135, { value: Type.int32() }),
        }),
      ),
    ).toThrow("conflicting type families");
    expect(fory.typeResolver.getSerializerById(TypeId.ENUM, 8135)).toBeDefined();
    expect(fory.typeResolver.getSerializerById(TypeId.STRUCT, 8136)).toBeUndefined();
  });

  test("rejects published schema conflicts", () => {
    const sharedProps = { value: Type.int32() };
    const definitionPairs: [TypeInfo, TypeInfo][] = [
      [Type.struct(8137, { first: Type.int32() }), Type.struct(8137, { second: Type.string() })],
      [
        Type.struct({ typeId: 8138, evolving: false }, sharedProps),
        Type.struct({ typeId: 8138, evolving: true }, sharedProps),
      ],
      [Type.enum(8139, { FIRST: 1 }), Type.enum(8139, { SECOND: 2 })],
      [Type.union(8140, { 1: Type.int32() }), Type.union(8140, { 2: Type.string() })],
    ];

    for (const [first, second] of definitionPairs) {
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
      const registered = fory.register(first);
      generated = 0;

      expect(() => fory.register(second)).toThrow("conflicting complete definitions");
      expect(generated).toBe(0);
      expect(fory.typeResolver.getSerializerByTypeInfo(first)).toBe(registered.serializer);
    }
  });

  test("keeps extension owner", () => {
    class FirstExtension {
      value = 0;
    }
    class SecondExtension {
      value = 0;
    }
    Type.ext(8144)(FirstExtension);
    Type.ext(8144)(SecondExtension);
    const customSerializer = {
      write(writeContext: any, value: FirstExtension | SecondExtension) {
        writeContext.writeVarInt32(value.value);
      },
      read(readContext: any, value: FirstExtension | SecondExtension) {
        value.value = readContext.readVarInt32();
      },
    };
    const fory = new Fory({ compatible: false });
    const extension = fory.register(FirstExtension, customSerializer);
    const wrapper = fory.register(Type.struct(8145, { value: Type.ext(8144) }));

    expect(() => fory.register(SecondExtension, customSerializer)).toThrow(
      "conflicting complete definitions",
    );
    expect(fory.typeResolver.getSerializerById(TypeId.EXT, 8144)).toBe(extension.serializer);
    const value = new FirstExtension();
    value.value = 7;
    expect(wrapper.serializer).toBeDefined();
    expect(extension.deserialize(extension.serialize(value))).toEqual(value);
  });

  test("uses published schema owners", () => {
    const fory = new Fory({ compatible: false });
    fory.register(Type.enum(8146, { VALUE: 7 }));
    fory.register(Type.union(8147, { 1: Type.string() }));
    const registered = fory.register(
      Type.struct(8148, {
        enumValue: Type.enum(8146),
        unionValue: Type.union(8147),
      }),
    );
    const value = { enumValue: 7, unionValue: { case: 1, value: "value" } };

    expect(registered.deserialize(registered.serialize(value))).toEqual(value);
  });

  test("keeps open enum and union", () => {
    const fory = new Fory({ compatible: false });
    const enumType = fory.register(Type.enum(8149));
    const unionType = fory.register(Type.union(8150));
    const unionValue = { case: 1, value: "value" };

    expect(enumType.deserialize(enumType.serialize(7))).toBe(7);
    expect(unionType.deserialize(unionType.serialize(unionValue))).toEqual(unionValue);
  });

  test("rejects reentrant schema conflict", () => {
    let publishConflict = false;
    let reentrant: ReturnType<Fory["register"]>;
    let fory: Fory;
    fory = new Fory({
      compatible: false,
      hooks: {
        afterCodeGenerated(code) {
          if (publishConflict) {
            publishConflict = false;
            reentrant = fory.register(Type.struct(8141, { inner: Type.string() }));
          }
          return code;
        },
      },
    });
    publishConflict = true;

    expect(() =>
      fory.register(
        Type.struct(8142, {
          trigger: Type.struct(8143, { value: Type.int32() }),
          value: Type.struct(8141, { outer: Type.int32() }),
        }),
      ),
    ).toThrow("conflicting complete definitions");
    expect(fory.typeResolver.getSerializerById(TypeId.STRUCT, 8141)).toBe(reentrant!.serializer);
    expect(fory.typeResolver.getSerializerById(TypeId.STRUCT, 8142)).toBeUndefined();
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
