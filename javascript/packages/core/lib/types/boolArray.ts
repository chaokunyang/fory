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

export class BoolArray {
  static readonly BYTES_PER_ELEMENT = 1;
  readonly BYTES_PER_ELEMENT = 1;
  private readonly _data: Uint8Array;

  constructor(length: number);
  constructor(source: BoolArray | Uint8Array | ArrayLike<boolean>);
  constructor(lengthOrSource: number | BoolArray | Uint8Array | ArrayLike<boolean>) {
    if (typeof lengthOrSource === "number") {
      this._data = new Uint8Array(lengthOrSource);
    } else if (lengthOrSource instanceof BoolArray) {
      this._data = new Uint8Array(lengthOrSource.raw);
    } else if (lengthOrSource instanceof Uint8Array) {
      this._data = new Uint8Array(lengthOrSource);
    } else {
      this._data = new Uint8Array(lengthOrSource.length);
      for (let i = 0; i < lengthOrSource.length; i++) {
        this._data[i] = lengthOrSource[i] ? 1 : 0;
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

  get raw(): Uint8Array {
    return this._data;
  }

  get(index: number): boolean {
    return this._data[index] !== 0;
  }

  at(index: number): boolean | undefined {
    const normalized = index < 0 ? this.length + index : index;
    if (normalized < 0 || normalized >= this.length) {
      return undefined;
    }
    return this.get(normalized);
  }

  set(values: BoolArray | ArrayLike<boolean>, offset = 0): void {
    if (values instanceof BoolArray) {
      this._data.set(values.raw, offset);
      return;
    }
    for (let i = 0; i < values.length; i++) {
      this._data[offset + i] = values[i] ? 1 : 0;
    }
  }

  setValue(index: number, value: boolean): void {
    this._data[index] = value ? 1 : 0;
  }

  fill(value: boolean, start?: number, end?: number): this {
    this._data.fill(value ? 1 : 0, start, end);
    return this;
  }

  slice(start?: number, end?: number): BoolArray {
    return BoolArray.fromRaw(this._data.slice(start, end));
  }

  subarray(begin?: number, end?: number): BoolArray {
    return BoolArray.fromRaw(this._data.subarray(begin, end));
  }

  toArray(): boolean[] {
    return Array.from(this);
  }

  static fromRaw(data: Uint8Array): BoolArray {
    const array = Object.create(BoolArray.prototype) as BoolArray;
    Object.defineProperty(array, "_data", {
      value: data,
      enumerable: false,
      writable: false,
    });
    return array;
  }

  [Symbol.iterator](): IterableIterator<boolean> {
    let i = 0;
    const data = this._data;
    const len = data.length;
    return {
      next(): IteratorResult<boolean> {
        if (i < len) {
          return { value: data[i++] !== 0, done: false };
        }
        return { value: undefined as unknown as boolean, done: true };
      },
      [Symbol.iterator]() {
        return this;
      },
    };
  }
}
