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

import { TypeId } from "../type";
import { TypeInfo } from "../typeInfo";
import { SerializerGenerator } from "./serializer";
import { CodecBuilder } from "./builder";
import { Scope } from "./scope";

type SerializerGeneratorConstructor = new (
  typeInfo: TypeInfo,
  builder: CodecBuilder,
  scope: Scope,
) => SerializerGenerator;

export class CodegenRegistry {
  static map = new Map<number, SerializerGeneratorConstructor>();
  static external = new Map<string, any>();

  private static checkExists(name: string) {
    if (this.external.has(name)) {
      throw new Error(`${name} has been registered.`);
    }
  }

  static register(typeId: number, generator: SerializerGeneratorConstructor) {
    this.map.set(typeId, generator);
  }

  static registerExternal(object: { name: string }) {
    CodegenRegistry.checkExists(object.name);
    this.external.set(object.name, object);
  }

  static newGeneratorByTypeInfo(typeInfo: TypeInfo, builder: CodecBuilder, scope: Scope) {
    let generatorTypeInfo = typeInfo;
    if (TypeId.userDefinedType(typeInfo.typeId)) {
      const ownerTypeInfo = builder.serializerLookup
        .getSerializerByTypeInfo(typeInfo)
        ?.getTypeInfo();
      if (ownerTypeInfo !== undefined && ownerTypeInfo !== typeInfo) {
        // Schema comes from the authoritative serializer owner. Field occurrence modifiers remain
        // local to the containing schema and must not be replaced with the owner's modifiers.
        generatorTypeInfo = ownerTypeInfo.clone();
        generatorTypeInfo.nullable = typeInfo.nullable;
        generatorTypeInfo.trackingRef = typeInfo.trackingRef;
        generatorTypeInfo.id = typeInfo.id;
        generatorTypeInfo.dynamic = typeInfo.dynamic;
      }
    }
    const constructor = CodegenRegistry.get(generatorTypeInfo.typeId);
    if (!constructor) {
      throw new Error("type not registered");
    }
    return new constructor(generatorTypeInfo, builder, scope);
  }

  static get(typeId: number) {
    return this.map.get(typeId);
  }

  static getExternal() {
    return Object.fromEntries(
      Array.from(CodegenRegistry.external.entries()).map(([key, value]) => [key, value]),
    );
  }
}
