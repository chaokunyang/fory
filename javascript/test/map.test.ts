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

describe('map', () => {
  test('should map work', () => {
    
    const fory = new Fory({ ref: true });    
    const input = fory.serialize(new Map([["foo", "bar"], ["foo2", "bar2"]]));
    const result = fory.deserialize(
        input
    );
    expect(result).toEqual(new Map([["foo", "bar"],["foo2", "bar2"]]))
  });
  
  test('should map specific type work', () => {
    
    const fory = new Fory({ ref: true });  
    const { serialize, deserialize } = fory.register(Type.struct("class.foo", {
      f1: Type.map(Type.string(), Type.varInt32())
    }))  
    const bin = serialize({
      f1: new Map([["hello", 123], ["world", 456]]),
    })
    const result = deserialize(bin);
    expect(result).toEqual({ f1: new Map([["hello", 123],["world", 456]])})
  });

  test('should read specific map chunks when one side omits declared type info', () => {
    const fory = new Fory({ compatible: true });
    const holderType = Type.struct(1500, {
      float16ValuesByName: Type.map(Type.string(), Type.float16()).setId(222),
    });
    const { serialize, deserialize } = fory.register(holderType);
    const bytes = serialize({
      float16ValuesByName: new Map([["primary", 1.5]]),
    });

    fory.readContext.reset(bytes);
    const reader = fory.readContext.reader;
    reader.readUint8();
    reader.readInt8();
    reader.readUint8();
    fory.readContext.readTypeMeta();
    const bodyStart = reader.readGetCursor();

    const patched = new Uint8Array(bytes.length + 1);
    patched.set(bytes.subarray(0, bodyStart), 0);
    patched[bodyStart] = bytes[bodyStart];
    patched[bodyStart + 1] = 0x04;
    patched[bodyStart + 2] = bytes[bodyStart + 2];
    patched[bodyStart + 3] = Type.float16().typeId;
    patched.set(bytes.subarray(bodyStart + 3), bodyStart + 4);

    expect(deserialize(patched)).toEqual({
      float16ValuesByName: new Map([["primary", 1.5]]),
    });
  });

  test('should not declare compatible struct map values in chunk headers', () => {
    const fory = new Fory({ compatible: true });
    const leafType = Type.struct(1502, {
      label: Type.string().setId(1),
      count: Type.varInt32().setId(2),
    });
    const holderType = Type.struct(1500, {
      messageValuesByName: Type.map(Type.string(), Type.struct({ typeId: 1502, evolving: false })).setId(229),
    });
    fory.register(leafType);
    const { serialize, deserialize } = fory.register(holderType);
    const bytes = serialize({
      messageValuesByName: new Map([["leaf-a", { label: "alpha", count: 7 }]]),
    });

    fory.readContext.reset(bytes);
    const reader = fory.readContext.reader;
    reader.readUint8();
    reader.readInt8();
    reader.readUint8();
    fory.readContext.readTypeMeta();
    const bodyStart = reader.readGetCursor();

    expect(bytes[bodyStart]).toBe(1);
    expect(bytes[bodyStart + 1]).toBe(0x04);
    expect(deserialize(bytes)).toEqual({
      messageValuesByName: new Map([["leaf-a", { label: "alpha", count: 7 }]]),
    });
  });
});
