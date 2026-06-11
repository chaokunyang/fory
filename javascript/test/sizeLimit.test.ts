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

import Fory, { Type, BoolArray } from '../packages/core/index';
import { describe, expect, test } from '@jest/globals';

describe('size-limit guardrails', () => {
  describe('configuration', () => {
    test('should have default limits matching Go', () => {
      const fory = new Fory({ compatible: false });
      expect(fory.readContext.maxCollectionSize).toBe(1_000_000);
    });

    test('should accept custom maxCollectionSize', () => {
      const fory = new Fory({ compatible: false, maxCollectionSize: 500 });
      expect(fory.readContext.maxCollectionSize).toBe(500);
    });

    test('should accept zero as a valid limit', () => {
      const fory = new Fory({ compatible: false, maxCollectionSize: 0 });
      expect(fory.readContext.maxCollectionSize).toBe(0);
    });

    test('should reject negative maxCollectionSize', () => {
      expect(() => new Fory({ compatible: false, maxCollectionSize: -10 })).toThrow(
        'maxCollectionSize must be a non-negative integer'
      );
    });

    test('should reject non-integer maxCollectionSize', () => {
      expect(() => new Fory({ compatible: false, maxCollectionSize: 2.7 })).toThrow(
        'maxCollectionSize must be a non-negative integer'
      );
    });

    test('should work with other options combined', () => {
      const fory = new Fory({
        maxDepth: 100,
        maxCollectionSize: 500,
        ref: true,
        compatible: true,
      });
      expect(fory.readContext.maxDepth).toBe(100);
      expect(fory.readContext.maxCollectionSize).toBe(500);
    });
  });

  describe('checkCollectionSize', () => {
    test('should not throw when size is within default limit', () => {
      const fory = new Fory({ compatible: false });
      expect(() => fory.readContext.checkCollectionSize(999999)).not.toThrow();
    });

    test('should not throw when size is within limit', () => {
      const fory = new Fory({ compatible: false, maxCollectionSize: 100 });
      expect(() => fory.readContext.checkCollectionSize(100)).not.toThrow();
      expect(() => fory.readContext.checkCollectionSize(0)).not.toThrow();
    });

    test('should throw when size exceeds limit', () => {
      const fory = new Fory({ compatible: false, maxCollectionSize: 100 });
      expect(() => fory.readContext.checkCollectionSize(101)).toThrow(
        'Collection size 101 exceeds maxCollectionSize 100'
      );
    });

    test('error message should include helpful suggestion', () => {
      const fory = new Fory({ compatible: false, maxCollectionSize: 10 });
      expect(() => fory.readContext.checkCollectionSize(20)).toThrow(
        'increase maxCollectionSize if needed'
      );
    });
  });

  describe('list deserialization with maxCollectionSize', () => {
    test('should deserialize list within limit', () => {
      const fory = new Fory({ compatible: false, maxCollectionSize: 10 });
      const { serialize, deserialize } = fory.register(Type.list(Type.int32()));
      const data = [1, 2, 3];
      const result = deserialize(serialize(data));
      expect(result).toEqual(data);
    });

    test('should throw when list exceeds maxCollectionSize', () => {
      const serializeFory = new Fory({ compatible: false });
      const { serialize } = serializeFory.register(Type.list(Type.int32()));
      const bytes = serialize([1, 2, 3, 4, 5]);

      const deserializeFory = new Fory({ compatible: false, maxCollectionSize: 3 });
      const { deserialize } = deserializeFory.register(Type.list(Type.int32()));
      expect(() => deserialize(bytes)).toThrow('exceeds maxCollectionSize');
    });

    test('should deserialize list at exact limit', () => {
      const fory = new Fory({ compatible: false, maxCollectionSize: 3 });
      const { serialize, deserialize } = fory.register(Type.list(Type.int32()));
      const data = [1, 2, 3];
      const result = deserialize(serialize(data));
      expect(result).toEqual(data);
    });
  });

  describe('set deserialization with maxCollectionSize', () => {
    test('should deserialize set within limit', () => {
      const fory = new Fory({ compatible: false, maxCollectionSize: 10, ref: true });
      const { serialize, deserialize } = fory.register(Type.set(Type.int32()));
      const data = new Set([1, 2, 3]);
      const result = deserialize(serialize(data));
      expect(result).toEqual(data);
    });

    test('should throw when set exceeds maxCollectionSize', () => {
      const serializeFory = new Fory({ compatible: false, ref: true });
      const { serialize } = serializeFory.register(Type.set(Type.int32()));
      const bytes = serialize(new Set([1, 2, 3, 4, 5]));

      const deserializeFory = new Fory({ compatible: false, maxCollectionSize: 3, ref: true });
      const { deserialize } = deserializeFory.register(Type.set(Type.int32()));
      expect(() => deserialize(bytes)).toThrow('exceeds maxCollectionSize');
    });
  });

  describe('map deserialization with maxCollectionSize', () => {
    test('should deserialize map within limit', () => {
      const fory = new Fory({ compatible: false, maxCollectionSize: 10, ref: true });
      const { serialize, deserialize } = fory.register(
        Type.map(Type.string(), Type.int32())
      );
      const data = new Map([['a', 1], ['b', 2]]);
      const result = deserialize(serialize(data));
      expect(result).toEqual(data);
    });

    test('should throw when map exceeds maxCollectionSize', () => {
      const serializeFory = new Fory({ compatible: false, ref: true });
      const { serialize } = serializeFory.register(
        Type.map(Type.string(), Type.int32())
      );
      const bytes = serialize(new Map([['a', 1], ['b', 2], ['c', 3], ['d', 4]]));

      const deserializeFory = new Fory({ compatible: false, maxCollectionSize: 2, ref: true });
      const { deserialize } = deserializeFory.register(
        Type.map(Type.string(), Type.int32())
      );
      expect(() => deserialize(bytes)).toThrow('exceeds maxCollectionSize');
    });
  });

  describe('default limits allow normal payloads', () => {
    test('should allow large collections within default limit', () => {
      const fory = new Fory({ compatible: false, ref: true });
      const { serialize, deserialize } = fory.register(Type.list(Type.int32()));
      const bigArray = Array.from({ length: 1000 }, (_, i) => i);
      const result = deserialize(serialize(bigArray));
      expect(result).toEqual(bigArray);
    });
  });

  describe('polymorphic (any-typed) collection paths', () => {
    test('should enforce maxCollectionSize on untyped list', () => {
      const serializeFory = new Fory({ compatible: false, ref: true });
      const bytes = serializeFory.serialize([1, "two", 3.0]);

      const deserializeFory = new Fory({ compatible: false, maxCollectionSize: 2, ref: true });
      expect(() => deserializeFory.deserialize(bytes)).toThrow('exceeds maxCollectionSize');
    });

    test('should enforce maxCollectionSize on untyped map', () => {
      const serializeFory = new Fory({ compatible: false, ref: true });
      const bytes = serializeFory.serialize(new Map([["a", 1], ["b", 2], ["c", 3]]));

      const deserializeFory = new Fory({ compatible: false, maxCollectionSize: 2, ref: true });
      expect(() => deserializeFory.deserialize(bytes)).toThrow('exceeds maxCollectionSize');
    });
  });

  describe('bool array deserialization with maxCollectionSize', () => {
    // BoolArraySerializerGenerator reads an element count — guarded by checkCollectionSize
    test('should deserialize bool array within limit', () => {
      const fory = new Fory({ compatible: false, maxCollectionSize: 10 });
      const { serialize, deserialize } = fory.register(Type.struct("test.boolArr", {
        flags: Type.boolArray(),
      }));
      const data = { flags: [true, false, true] };
      const result = deserialize(serialize(data));
      expect(result!.flags).toBeInstanceOf(BoolArray);
      expect(Array.from(result!.flags)).toEqual([true, false, true]);
    });

    test('should throw when bool array exceeds maxCollectionSize', () => {
      const serializeFory = new Fory({ compatible: false });
      const { serialize } = serializeFory.register(Type.struct("test.boolArr2", {
        flags: Type.boolArray(),
      }));
      const bytes = serialize({ flags: [true, false, true, true, false] });

      const deserializeFory = new Fory({ compatible: false, maxCollectionSize: 3 });
      const { deserialize } = deserializeFory.register(Type.struct("test.boolArr2", {
        flags: Type.boolArray(),
      }));
      expect(() => deserialize(bytes)).toThrow('exceeds maxCollectionSize');
    });
  });

});
