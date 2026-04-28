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

import { TypeInfo } from "../typeInfo";
import { CodecBuilder } from "./builder";
import { BaseSerializerGenerator } from "./serializer";
import { CodegenRegistry } from "./router";
import { TypeId } from "../type";
import { Scope } from "./scope";

class TimestampSerializerGenerator extends BaseSerializerGenerator {
  typeInfo: TypeInfo;

  constructor(typeInfo: TypeInfo, builder: CodecBuilder, scope: Scope) {
    super(typeInfo, builder, scope);
    this.typeInfo = typeInfo;
  }

  write(accessor: string): string {
    const valueVar = this.scope.uniqueName("ts_value");
    const msVar = this.scope.uniqueName("ts_ms");
    const secondsVar = this.scope.uniqueName("ts_sec");
    const nanosVar = this.scope.uniqueName("ts_nanos");
    const exactSecondsVar = this.scope.uniqueName("ts_exact_sec");
    const exactNanosVar = this.scope.uniqueName("ts_exact_nanos");
    const exactMillisVar = this.scope.uniqueName("ts_exact_ms");
    const secondsSymbol = this.scope.declareByName(
      "timestampSecondsSymbol",
      "Symbol.for(\"@apache-fory/timestampSeconds\")",
    );
    const nanosSymbol = this.scope.declareByName(
      "timestampNanosSymbol",
      "Symbol.for(\"@apache-fory/timestampNanos\")",
    );
    const millisSymbol = this.scope.declareByName(
      "timestampMillisSymbol",
      "Symbol.for(\"@apache-fory/timestampMillis\")",
    );
    return `
      const ${valueVar} = ${accessor};
      let ${secondsVar};
      let ${nanosVar};
      if (${valueVar} instanceof Date) {
        const ${exactSecondsVar} = ${valueVar}[${secondsSymbol}];
        const ${exactNanosVar} = ${valueVar}[${nanosSymbol}];
        const ${exactMillisVar} = ${valueVar}[${millisSymbol}];
        if (${exactSecondsVar} !== undefined && ${exactNanosVar} !== undefined
          && ${exactMillisVar} === ${valueVar}.getTime()) {
          ${secondsVar} = ${exactSecondsVar};
          ${nanosVar} = ${exactNanosVar};
        } else {
          const ${msVar} = ${valueVar}.getTime();
          ${secondsVar} = BigInt(Math.floor(${msVar} / 1000));
          ${nanosVar} = Math.round((${msVar} - Number(${secondsVar}) * 1000) * 1000000);
          if (${nanosVar} >= 1000000000) {
            ${secondsVar} += 1n;
            ${nanosVar} -= 1000000000;
          }
        }
      } else {
        const ${msVar} = ${valueVar};
        ${secondsVar} = BigInt(Math.floor(${msVar} / 1000));
        ${nanosVar} = Math.round((${msVar} - Number(${secondsVar}) * 1000) * 1000000);
        if (${nanosVar} >= 1000000000) {
          ${secondsVar} += 1n;
          ${nanosVar} -= 1000000000;
        }
      }
      ${this.builder.writer.writeInt64(`${secondsVar}`)}
      ${this.builder.writer.writeUint32(`${nanosVar}`)}
      `;
  }

  read(accessor: (expr: string) => string): string {
    const seconds = this.scope.uniqueName("ts_sec");
    const nanos = this.scope.uniqueName("ts_nanos");
    const result = this.scope.uniqueName("ts_date");
    const millis = this.scope.uniqueName("ts_ms");
    const secondsSymbol = this.scope.declareByName(
      "timestampSecondsSymbol",
      "Symbol.for(\"@apache-fory/timestampSeconds\")",
    );
    const nanosSymbol = this.scope.declareByName(
      "timestampNanosSymbol",
      "Symbol.for(\"@apache-fory/timestampNanos\")",
    );
    const millisSymbol = this.scope.declareByName(
      "timestampMillisSymbol",
      "Symbol.for(\"@apache-fory/timestampMillis\")",
    );
    return `
      const ${seconds} = ${this.builder.reader.readInt64()};
      const ${nanos} = ${this.builder.reader.readUint32()};
      const ${millis} = Number(${seconds}) * 1000 + Math.floor(${nanos} / 1000000);
      const ${result} = new Date(${millis});
      ${result}[${secondsSymbol}] = ${seconds};
      ${result}[${nanosSymbol}] = ${nanos};
      ${result}[${millisSymbol}] = ${result}.getTime();
      ${accessor(result)}
    `;
  }

  getFixedSize(): number {
    return 12;
  }
}

class DurationSerializerGenerator extends BaseSerializerGenerator {
  typeInfo: TypeInfo;

  constructor(typeInfo: TypeInfo, builder: CodecBuilder, scope: Scope) {
    super(typeInfo, builder, scope);
    this.typeInfo = typeInfo;
  }

  write(accessor: string): string {
    const msVar = this.scope.uniqueName("ts_ms");
    const secondsVar = this.scope.uniqueName("ts_sec");
    const nanosVar = this.scope.uniqueName("ts_nanos");
    return `
      const ${msVar} = ${accessor};
      let ${secondsVar} = BigInt(Math.floor(${msVar} / 1000));
      let ${nanosVar} = Math.round((${msVar} - Number(${secondsVar}) * 1000) * 1000000);
      if (${nanosVar} >= 1000000000) {
        ${secondsVar} += 1n;
        ${nanosVar} -= 1000000000;
      }
      ${this.builder.writer.writeVarInt64(`${secondsVar}`)}
      ${this.builder.writer.writeInt32(`${nanosVar}`)}
      `;
  }

  read(accessor: (expr: string) => string): string {
    const seconds = this.builder.reader.readVarInt64();
    const nanos = this.builder.reader.readInt32();
    return accessor(`Number(${seconds}) * 1000 + ${nanos} / 1000000`);
  }

  getFixedSize(): number {
    return 15;
  }
}

class DateSerializerGenerator extends BaseSerializerGenerator {
  typeInfo: TypeInfo;

  constructor(typeInfo: TypeInfo, builder: CodecBuilder, scope: Scope) {
    super(typeInfo, builder, scope);
    this.typeInfo = typeInfo;
  }

  write(accessor: string): string {
    const epoch = this.scope.declareByName("epoch", `new Date("1970/01/01 00:00").getTime()`);
    return `
      if (${accessor} instanceof Date) {
        ${this.builder.writer.writeVarInt64(`Math.floor((${accessor}.getTime() - ${epoch}) / 1000 / (24 * 60 * 60))`)}
      } else {
        ${this.builder.writer.writeVarInt64(`Math.floor((${accessor} - ${epoch}) / 1000 / (24 * 60 * 60))`)}
      }
    `;
  }

  read(accessor: (expr: string) => string): string {
    const epoch = this.scope.declareByName("epoch", `new Date("1970/01/01 00:00").getTime()`);
    return accessor(`new Date(${epoch} + (Number(${this.builder.reader.readVarInt64()}) * (24 * 60 * 60) * 1000))`);
  }

  getFixedSize(): number {
    return 11;
  }
}

CodegenRegistry.register(TypeId.DURATION, DurationSerializerGenerator);
CodegenRegistry.register(TypeId.TIMESTAMP, TimestampSerializerGenerator);
CodegenRegistry.register(TypeId.DATE, DateSerializerGenerator);
