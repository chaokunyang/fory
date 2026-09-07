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

import Fory, { BinaryReader } from "../packages/core/index";
import hps from "../packages/hps/index";
import { describe, expect, test } from "@jest/globals";

const skipableDescribe = hps ? describe : describe.skip;

skipableDescribe("hps", () => {
  test("should isLatin1 work", () => {
    const { serializeString } = hps!;
    for (let index = 0; index < 10000; index++) {
      const bf = Buffer.alloc(100);
      serializeString("hello", bf, 0);
      var reader = new BinaryReader({});
      reader.reset(bf);
      expect(reader.stringWithHeader()).toBe("hello");

      serializeString("😁", bf, 0);
      var reader = new BinaryReader({});
      reader.reset(bf);
      expect(reader.stringWithHeader()).toBe("😁");
    }
  });

  test("should enforce the exact destination capacity", () => {
    const { serializeString } = hps!;
    const backing = Buffer.alloc(8, 0xa5);
    const exact = backing.subarray(1, 7);
    expect(serializeString("hello", exact, 0)).toBe(6);

    const reader = new BinaryReader({});
    reader.reset(exact);
    expect(reader.stringWithHeader()).toBe("hello");
    expect(backing[0]).toBe(0xa5);
    expect(backing[7]).toBe(0xa5);

    const utf16Backing = Buffer.alloc(7, 0xa5);
    const utf16Exact = utf16Backing.subarray(1, 6);
    expect(serializeString("😁", utf16Exact, 0)).toBe(5);
    reader.reset(utf16Exact);
    expect(reader.stringWithHeader()).toBe("😁");
    expect(utf16Backing[0]).toBe(0xa5);
    expect(utf16Backing[6]).toBe(0xa5);

    const tooSmall = Buffer.alloc(5, 0xa5);
    expect(() => serializeString("hello", tooSmall, 0)).toThrow();
    expect(Array.from(tooSmall)).toEqual(new Array(5).fill(0xa5));
  });

  test("should grow before an oversized native string write", () => {
    const fory = new Fory({ hps: hps! });
    const value = "a".repeat(200 * 1024);
    expect(fory.deserialize(fory.serialize(value))).toBe(value);
  });
});
