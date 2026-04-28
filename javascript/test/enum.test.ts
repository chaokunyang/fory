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
import {describe, expect, test} from '@jest/globals';

describe('enum', () => {
    test('should javascript number enum work', () => {
        const Foo = {
            f1: 1,
            f2: 2
        }
        const fory = new Fory({ ref: true });   
        const {serialize, deserialize} = fory.register(Type.enum("example.foo", Foo)) 
        const input = serialize(Foo.f1);
        const result = deserialize(
            input
        );
        expect(result).toEqual(Foo.f1)
      });
    
      test('should javascript string enum work', () => {
        const Foo = {
            f1: "hello",
            f2: "world"
        }
        const fory = new Fory({ ref: true });   
        fory.register(Type.enum("example.foo", Foo)) 
        const input = fory.serialize(Foo.f1);
        const result = fory.deserialize(
            input
        );
        expect(result).toEqual(Foo.f1)
      });
  test('should typescript number enum work', () => {
    enum Foo {
        f1 = 1,
        f2 = 2
    }
    const fory = new Fory({ ref: true });   
    const {serialize, deserialize} = fory.register(Type.enum("example.foo", Foo)) 
    const input = serialize(Foo.f1);
    const result = deserialize(
        input
    );
    expect(result).toEqual(Foo.f1)
  });

  test('should preserve sparse numeric enum values', () => {
    const Foo = {
      unknown: 4096,
      ok: 8192
    };
    const fory = new Fory({ ref: true });
    const { serialize, deserialize } = fory.register(Type.enum("example.foo", Foo));
    const input = serialize(Foo.ok);
    const result = deserialize(input);
    expect(result).toEqual(Foo.ok);
  });

  test('should typescript string enum work', () => {
    enum Foo {
        f1 = "hello",
        f2 = "world"
    }
    const fory = new Fory({ ref: true });   
    fory.register(Type.enum("example.foo", Foo)) 
    const input = fory.serialize(Foo.f1);
    const result = fory.deserialize(
        input
    );
    expect(result).toEqual(Foo.f1)
  });

  test('should auto-register embedded enums for declared enum map fields', () => {
    const Status = {
      UNKNOWN: 0,
      READY: 1,
      FAILED: 2,
    };
    const holderType = Type.struct(1500, {
      enumValue: Type.enum(1504, Status).setId(26),
      stringValuesByEnum: Type.map(Type.enum(1504, Status), Type.string()).setId(221),
    });
    const fory = new Fory({ compatible: true });
    const { serialize, deserialize } = fory.register(holderType);

    expect(fory.typeResolver.getSerializerByTypeInfo(Type.enum(1504, Status))).toBeDefined();

    const input = {
      enumValue: Status.READY,
      stringValuesByEnum: new Map([[Status.READY, 'ready']]),
    };
    const result = deserialize(serialize(input));

    expect(result).toEqual({
      enumValue: Status.READY,
      stringValuesByEnum: new Map([[Status.READY, 'ready']]),
    });
  });
});
