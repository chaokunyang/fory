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

    if (operation === "serialize") {
      expect(() => fory.serialize(Symbol("unsupported"))).toThrow();
    } else {
      expect(() => fory.deserialize(new Uint8Array([0]))).toThrow();
    }

    expect(() => fory.register(Type.struct(8102, {}))).toThrow();
  });

  test("freezes before serializer lookup", () => {
    const fory = new Fory({ compatible: false });

    expect(() => fory.serialize(1, null as any)).toThrow();
    expect(() => fory.register(Type.struct(8104, {}))).toThrow();
  });

  test("freezes during serializer generation", () => {
    let armed = false;
    let fory: Fory;
    fory = new Fory({
      compatible: false,
      hooks: {
        afterCodeGenerated(code) {
          if (armed) {
            fory.serialize(null);
          }
          return code;
        },
      },
    });
    armed = true;

    expect(() => fory.register(Type.struct(8105, {}))).toThrow();
  });

  test.each(["serialize", "deserialize"] as const)("freezes after %s", (operation) => {
    const fory = new Fory({ compatible: false });

    if (operation === "serialize") {
      fory.serialize(1);
    } else {
      const bytes = new Fory({ compatible: false }).serialize(1);
      fory.deserialize(bytes);
    }

    expect(() => fory.register(Type.struct(8103, {}))).toThrow();
  });

  function testTypeInfo(typeinfo: TypeInfo, input: any, expected?: any) {
    const fory = new Fory({ compatible: false });
    const serialize = fory.register(typeinfo);
    const result = serialize.deserialize(serialize.serialize(input));
    expect(result).toEqual(expected ?? input);
  }
});
