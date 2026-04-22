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

import Fory, { Type } from '../packages/core/index';
import { TypeMeta } from '../packages/core/lib/meta/TypeMeta';
import { describe, expect, test } from '@jest/globals';

const HAS_FIELDS_META_FLAG = 1n << 8n;
const COMPRESS_META_FLAG = 1n << 9n;
const META_SIZE_MASK = 0xFFn;
const HASH_SHIFT_BITS = 14n;

describe('typemeta', () => {
  test('writes TypeMeta header bits in the xlang layout', () => {
    const typeInfo = Type.struct(7001, {
      fullName: Type.string().setId(1),
      age: Type.int32().setId(2),
    });

    const bytes = TypeMeta.fromTypeInfo(typeInfo).toBytes();
    const header = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength).getBigUint64(0, true);

    expect(Number(header & META_SIZE_MASK)).toBe(bytes.length - 8);
    expect((header & HAS_FIELDS_META_FLAG) !== 0n).toBe(true);
    expect((header & COMPRESS_META_FLAG) !== 0n).toBe(false);
    expect(header >> HASH_SHIFT_BITS).toBeGreaterThan(0n);
  });

  test('regenerates compatible named serializers when schema changes but field count stays the same', () => {
    const writerFory = new Fory({ compatible: true });
    const readerFory = new Fory({ compatible: true });

    const writerType = Type.struct('example.item', {
      value: Type.string(),
    });
    const readerType = Type.struct('example.item', {
      value: Type.int32(),
    });

    const bytes = writerFory.register(writerType).serialize({ value: 'hello' });
    const result = readerFory.register(readerType).deserialize(bytes);

    expect(result).toEqual({ value: 'hello' });
  });

  test('remaps compatible tag-id fields onto local property names during regeneration', () => {
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
      fullName: 'Alice',
      note: 'ally',
    });
    const result = readerFory.register(readerType).deserialize(bytes);

    expect(result).toEqual({
      name: 'Alice',
      alias: 'ally',
    });
  });

  test('keeps compatible named schema evolution working when field count differs', () => {
    const writerFory = new Fory({ compatible: true });
    const readerFory = new Fory({ compatible: true });

    const writerType = Type.struct('example.foo', {
      bar: Type.string(),
      bar2: Type.int32(),
    });
    const readerType = Type.struct('example.foo', {
      bar: Type.string(),
    });

    const bytes = writerFory.register(writerType).serialize({
      bar: 'hello',
      bar2: 123,
    });
    const result = readerFory.register(readerType).deserialize(bytes);

    expect(result).toEqual({
      bar: 'hello',
      bar2: 123,
    });
  });
});
