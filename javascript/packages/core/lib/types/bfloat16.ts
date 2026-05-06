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

const float32View = new Float32Array(1);
const uint32View = new Uint32Array(float32View.buffer);

export class BFloat16 {
  private readonly _bits: number;

  constructor(bits: number) {
    this._bits = bits & 0xffff;
  }

  toBits(): number {
    return this._bits;
  }

  static fromBits(bits: number): BFloat16 {
    return new BFloat16(bits & 0xffff);
  }

  static fromFloat32(f32: number): BFloat16 {
    float32View[0] = f32;
    const bits = uint32View[0];
    const exponent = (bits >> 23) & 0xff;
    if (exponent === 255) {
      return BFloat16.fromBits((bits >> 16) & 0xffff);
    }
    const remainder = bits & 0x1ffff;
    let u = (bits + 0x8000) >> 16;
    if (remainder === 0x8000 && (u & 1) !== 0) {
      u--;
    }
    return BFloat16.fromBits(u & 0xffff);
  }

  toFloat32(): number {
    float32View[0] = 0;
    uint32View[0] = this._bits << 16;
    return float32View[0];
  }
}

export function toBFloat16Bits(value: BFloat16 | number): number {
  return value instanceof BFloat16 ? value.toBits() : BFloat16.fromFloat32(value).toBits();
}

export function fromBFloat16Bits(bits: number): number {
  float32View[0] = 0;
  uint32View[0] = (bits & 0xffff) << 16;
  return float32View[0];
}

export class BFloat16Array {
  static readonly BYTES_PER_ELEMENT = 2;
  readonly BYTES_PER_ELEMENT = 2;
  private _data: Uint16Array;

  constructor(length: number);
  constructor(source: BFloat16Array | Uint16Array | ArrayLike<BFloat16 | number>);
  constructor(lengthOrSource: number | BFloat16Array | Uint16Array | ArrayLike<BFloat16 | number>) {
    if (typeof lengthOrSource === "number") {
      this._data = new Uint16Array(lengthOrSource);
    } else if (lengthOrSource instanceof BFloat16Array) {
      this._data = new Uint16Array(lengthOrSource.raw);
    } else if (lengthOrSource instanceof Uint16Array) {
      this._data = new Uint16Array(lengthOrSource.length);
      this._data.set(lengthOrSource);
    } else {
      this._data = new Uint16Array(lengthOrSource.length);
      for (let i = 0; i < lengthOrSource.length; i++) {
        this._data[i] = toBFloat16Bits(lengthOrSource[i]);
      }
    }
  }

  get length(): number {
    return this._data.length;
  }

  get byteLength(): number {
    return this._data.byteLength;
  }

  get byteOffset(): number {
    return this._data.byteOffset;
  }

  get buffer(): ArrayBufferLike {
    return this._data.buffer;
  }

  get(index: number): number {
    return fromBFloat16Bits(this._data[index]);
  }

  at(index: number): number | undefined {
    const normalized = index < 0 ? this.length + index : index;
    if (normalized < 0 || normalized >= this.length) {
      return undefined;
    }
    return this.get(normalized);
  }

  set(values: BFloat16Array | ArrayLike<BFloat16 | number>, offset = 0): void {
    if (values instanceof BFloat16Array) {
      this._data.set(values.raw, offset);
      return;
    }
    for (let i = 0; i < values.length; i++) {
      this._data[offset + i] = toBFloat16Bits(values[i]);
    }
  }

  setValue(index: number, value: BFloat16 | number): void {
    this._data[index] = toBFloat16Bits(value);
  }

  fill(value: BFloat16 | number, start?: number, end?: number): this {
    this._data.fill(toBFloat16Bits(value), start, end);
    return this;
  }

  get raw(): Uint16Array {
    return this._data;
  }

  static fromRaw(data: Uint16Array): BFloat16Array {
    const array = Object.create(BFloat16Array.prototype) as BFloat16Array;
    array._data = data;
    return array;
  }

  slice(start?: number, end?: number): BFloat16Array {
    return BFloat16Array.fromRaw(this._data.slice(start, end));
  }

  subarray(begin?: number, end?: number): BFloat16Array {
    return BFloat16Array.fromRaw(this._data.subarray(begin, end));
  }

  toArray(): number[] {
    return Array.from(this);
  }

  [Symbol.iterator](): IterableIterator<number> {
    let i = 0;
    const data = this._data;
    const len = data.length;
    return {
      next(): IteratorResult<number> {
        if (i < len) {
          return { value: fromBFloat16Bits(data[i++]), done: false };
        }
        return { value: undefined as unknown as number, done: true };
      },
      [Symbol.iterator]() {
        return this;
      },
    };
  }
}
