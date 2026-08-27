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

import {
  ForyTypeInfoSymbol,
  WithForyClsInfo,
  Serializer,
  TypeId,
  MaxInt32,
  MinInt32,
  Config,
} from "./type";
import { Gen } from "./gen";
import { Dynamic, Type, TypeInfo } from "./typeInfo";
import { ReadContext, WriteContext } from "./context";
import { Decimal } from "./types/decimal";
import { BFloat16Array } from "./types/bfloat16";
import { BoolArray } from "./types/boolArray";
import { isFloat16Array } from "./types/float16";
import { getUnknownTypeMeta, UnknownStructSerializer } from "./unknownStruct";

export default class TypeResolver {
  readonly trackingRef: boolean;
  private internalSerializer: Serializer[] = new Array(300);
  private customSerializer: Map<number | string, Serializer> = new Map();
  private registrationFrozen = false;

  private writeContext!: WriteContext;
  private readContext!: ReadContext;

  private float64Serializer: null | Serializer = null;
  private float32Serializer: null | Serializer = null;
  private varint32Serializer: null | Serializer = null;
  private varInt64Serializer: null | Serializer = null;
  private int64Serializer: null | Serializer = null;
  private boolSerializer: null | Serializer = null;
  private datetimeSerializer: null | Serializer = null;
  private decimalSerializer: null | Serializer = null;
  private stringSerializer: null | Serializer = null;
  private setSerializer: null | Serializer = null;
  private listSerializer: null | Serializer = null;
  private mapSerializer: null | Serializer = null;
  private uint8ArraySerializer: null | Serializer = null;
  private uint16ArraySerializer: null | Serializer = null;
  private uint32ArraySerializer: null | Serializer = null;
  private uint64ArraySerializer: null | Serializer = null;
  private int8ArraySerializer: null | Serializer = null;
  private int16ArraySerializer: null | Serializer = null;
  private int32ArraySerializer: null | Serializer = null;
  private int64ArraySerializer: null | Serializer = null;
  private boolArraySerializer: null | Serializer = null;
  private float16ArraySerializer: null | Serializer = null;
  private bfloat16ArraySerializer: null | Serializer = null;
  private float32ArraySerializer: null | Serializer = null;
  private float64ArraySerializer: null | Serializer = null;
  private unknownStructSerializer!: UnknownStructSerializer;

  constructor(readonly config: Config) {
    this.trackingRef = config.ref;
  }

  bindContexts(writeContext: WriteContext, readContext: ReadContext) {
    this.writeContext = writeContext;
    this.readContext = readContext;
    this.unknownStructSerializer = new UnknownStructSerializer(this, writeContext, readContext);
  }

  getUnknownStructSerializer(typeMeta?: import("./meta/TypeMeta").TypeMeta, wireTypeId?: number) {
    return typeMeta === undefined
      ? this.unknownStructSerializer
      : this.unknownStructSerializer.createReadSerializer(typeMeta, wireTypeId);
  }

  isCompatible() {
    return this.config.compatible === true;
  }

  computeTypeId(typeInfo: TypeInfo) {
    const internalTypeId = typeInfo.typeId;
    if (internalTypeId !== TypeId.STRUCT && internalTypeId !== TypeId.NAMED_STRUCT) {
      return internalTypeId;
    }
    if (internalTypeId === TypeId.NAMED_STRUCT && this.isCompatible() && typeInfo.evolving) {
      return TypeId.NAMED_COMPATIBLE_STRUCT;
    }
    if (internalTypeId === TypeId.STRUCT && this.isCompatible() && typeInfo.evolving) {
      return TypeId.COMPATIBLE_STRUCT;
    }
    return internalTypeId;
  }

  isMonomorphic(typeInfo: TypeInfo, dynamic: Dynamic = Dynamic.AUTO) {
    switch (dynamic) {
      case Dynamic.TRUE:
        return false;
      case Dynamic.FALSE:
        return true;
      default:
        if (TypeId.enumType(typeInfo.typeId)) {
          return true;
        }
        if (
          typeInfo.typeId === TypeId.UNION ||
          typeInfo.typeId === TypeId.TYPED_UNION ||
          typeInfo.typeId === TypeId.NAMED_UNION
        ) {
          return true;
        }
        if (this.isCompatible()) {
          return !TypeId.userDefinedType(typeInfo.typeId) && typeInfo.typeId !== TypeId.UNKNOWN;
        }
        return typeInfo.typeId !== TypeId.UNKNOWN;
    }
  }

  private makeUserTypeKey(userTypeId: number) {
    return `u:${userTypeId}`;
  }

  private initInternalSerializer() {
    const generateInternalSerializer = (typeInfo: TypeInfo) => {
      return new Gen(this).generateSerializer(typeInfo);
    };
    generateInternalSerializer(Type.string());
    generateInternalSerializer(new TypeInfo(TypeId.ENUM));
    generateInternalSerializer(new TypeInfo(TypeId.NAMED_ENUM));
    generateInternalSerializer(Type.any());
    generateInternalSerializer(Type.list(Type.any()));
    generateInternalSerializer(Type.map(Type.any(), Type.any()));
    generateInternalSerializer(Type.bool());
    generateInternalSerializer(Type.int8());
    generateInternalSerializer(Type.int16());
    generateInternalSerializer(Type.int32({ encoding: "fixed" }));
    generateInternalSerializer(Type.int32());
    generateInternalSerializer(Type.uint32({ encoding: "fixed" }));
    generateInternalSerializer(Type.uint64({ encoding: "fixed" }));
    generateInternalSerializer(Type.int64({ encoding: "fixed" }));
    generateInternalSerializer(Type.int64());
    generateInternalSerializer(Type.uint8());
    generateInternalSerializer(Type.uint16());
    generateInternalSerializer(Type.uint32());
    generateInternalSerializer(Type.uint64());
    generateInternalSerializer(Type.uint64({ encoding: "tagged" }));
    generateInternalSerializer(Type.int64({ encoding: "tagged" }));
    generateInternalSerializer(Type.float16());
    generateInternalSerializer(Type.bfloat16());
    generateInternalSerializer(Type.float32());
    generateInternalSerializer(Type.float64());
    generateInternalSerializer(Type.timestamp());
    generateInternalSerializer(Type.duration());
    generateInternalSerializer(Type.date());
    generateInternalSerializer(Type.decimal());
    generateInternalSerializer(Type.set(Type.any()));
    generateInternalSerializer(Type.binary());
    generateInternalSerializer(Type.boolArray());
    generateInternalSerializer(Type.uint8Array());
    generateInternalSerializer(Type.int8Array());
    generateInternalSerializer(Type.uint16Array());
    generateInternalSerializer(Type.int16Array());
    generateInternalSerializer(Type.uint32Array());
    generateInternalSerializer(Type.int32Array());
    generateInternalSerializer(Type.uint64Array());
    generateInternalSerializer(Type.int64Array());
    generateInternalSerializer(Type.float16Array());
    generateInternalSerializer(Type.bfloat16Array());
    generateInternalSerializer(Type.float32Array());
    generateInternalSerializer(Type.float64Array());

    this.float64Serializer = this.getSerializerById(TypeId.FLOAT64);
    this.float32Serializer = this.getSerializerById(TypeId.FLOAT32);
    this.varint32Serializer = this.getSerializerById(TypeId.VARINT32);
    this.varInt64Serializer = this.getSerializerById(TypeId.VARINT64);
    this.int64Serializer = this.getSerializerById(TypeId.INT64);
    this.boolSerializer = this.getSerializerById(TypeId.BOOL);
    this.datetimeSerializer = this.getSerializerById(TypeId.TIMESTAMP);
    this.decimalSerializer = this.getSerializerById(TypeId.DECIMAL);
    this.stringSerializer = this.getSerializerById(TypeId.STRING);
    this.setSerializer = this.getSerializerById(TypeId.SET);
    this.listSerializer = this.getSerializerById(TypeId.LIST);
    this.mapSerializer = this.getSerializerById(TypeId.MAP);
    this.uint8ArraySerializer = this.getSerializerById(TypeId.UINT8_ARRAY);
    this.uint16ArraySerializer = this.getSerializerById(TypeId.UINT16_ARRAY);
    this.uint32ArraySerializer = this.getSerializerById(TypeId.UINT32_ARRAY);
    this.uint64ArraySerializer = this.getSerializerById(TypeId.UINT64_ARRAY);
    this.int8ArraySerializer = this.getSerializerById(TypeId.INT8_ARRAY);
    this.int16ArraySerializer = this.getSerializerById(TypeId.INT16_ARRAY);
    this.int32ArraySerializer = this.getSerializerById(TypeId.INT32_ARRAY);
    this.int64ArraySerializer = this.getSerializerById(TypeId.INT64_ARRAY);
    this.boolArraySerializer = this.getSerializerById(TypeId.BOOL_ARRAY);
    this.float16ArraySerializer = this.getSerializerById(TypeId.FLOAT16_ARRAY);
    this.bfloat16ArraySerializer = this.getSerializerById(TypeId.BFLOAT16_ARRAY);
    this.float32ArraySerializer = this.getSerializerById(TypeId.FLOAT32_ARRAY);
    this.float64ArraySerializer = this.getSerializerById(TypeId.FLOAT64_ARRAY);
  }

  init() {
    this.initInternalSerializer();
  }

  freezeRegistration() {
    if (!this.registrationFrozen) {
      this.registrationFrozen = true;
    }
  }

  ensureRegistrationOpen() {
    if (this.registrationFrozen) {
      throw new Error("types and serializers must be registered before the first root operation");
    }
  }

  commitGeneratedSerializers(entries: readonly { typeInfo: TypeInfo; serializer: Serializer }[]) {
    this.ensureRegistrationOpen();
    const publications = entries.map((entry) => {
      if (!entry.serializer._initialized) {
        throw new Error("generated serializer graph is incomplete");
      }
      const typeId = this.computeTypeId(entry.typeInfo);
      let internalTypeId: number | undefined;
      let customTypeKey: number | string | undefined;
      if (TypeId.isNamedType(typeId)) {
        customTypeKey = entry.typeInfo.named!;
      } else if (TypeId.needsUserTypeId(typeId) && entry.typeInfo.userTypeId !== -1) {
        customTypeKey = this.makeUserTypeKey(entry.typeInfo.userTypeId);
      } else if (typeId <= 0xff) {
        internalTypeId = typeId;
      } else {
        customTypeKey = typeId;
      }
      const existingSerializer =
        internalTypeId === undefined
          ? this.customSerializer.get(customTypeKey!)
          : this.internalSerializer[internalTypeId];
      return {
        entry,
        internalTypeId,
        customTypeKey,
        existingSerializer,
      };
    });
    for (const publication of publications) {
      if (publication.existingSerializer !== undefined) {
        continue;
      }
      if (publication.internalTypeId !== undefined) {
        this.internalSerializer[publication.internalTypeId] = publication.entry.serializer;
      } else {
        this.customSerializer.set(publication.customTypeKey!, publication.entry.serializer);
      }
    }
  }

  generateReadSerializer(typeInfo: TypeInfo) {
    return new Gen(this, { creator: typeInfo.options?.creator }).reGenerateSerializer(typeInfo);
  }

  getSerializerByTypeInfo(typeInfo: TypeInfo) {
    const typeId = this.computeTypeId(typeInfo);
    if (TypeId.isNamedType(typeId)) {
      return this.customSerializer.get(typeInfo.named!);
    }
    return this.getSerializerById(typeId, typeInfo.userTypeId);
  }

  getSerializerById(id: number, userTypeId?: number) {
    if (TypeId.needsUserTypeId(id) && userTypeId !== undefined && userTypeId !== -1) {
      return this.customSerializer.get(this.makeUserTypeKey(userTypeId))!;
    }
    if (id <= 0xff) {
      return this.internalSerializer[id]!;
    }
    return this.customSerializer.get(id)!;
  }

  getSerializerByName(typeIdOrName: number | string) {
    return this.customSerializer.get(typeIdOrName);
  }

  getSerializerByData(v: any) {
    if (v === null || v === undefined) {
      return null;
    }
    if (typeof v === "number") {
      if (Number.isInteger(v)) {
        if (v > MaxInt32 || v < MinInt32) {
          return this.varInt64Serializer;
        }
        return this.varint32Serializer;
      }
      if (v > MaxInt32 || v < MinInt32) {
        return this.float64Serializer;
      }
      return this.float32Serializer;
    }

    if (typeof v === "bigint") {
      return this.varInt64Serializer;
    }

    if (typeof v === "string") {
      return this.stringSerializer;
    }

    if (v instanceof Decimal) {
      return this.decimalSerializer;
    }

    if (v instanceof BoolArray) {
      return this.boolArraySerializer;
    }

    if (isFloat16Array(v)) {
      return this.float16ArraySerializer;
    }

    if (v instanceof BFloat16Array) {
      return this.bfloat16ArraySerializer;
    }

    if (v instanceof Uint8Array) {
      return this.uint8ArraySerializer;
    }

    if (v instanceof Uint16Array) {
      return this.uint16ArraySerializer;
    }

    if (v instanceof Uint32Array) {
      return this.uint32ArraySerializer;
    }

    if (v instanceof BigUint64Array) {
      return this.uint64ArraySerializer;
    }

    if (v instanceof Int8Array) {
      return this.int8ArraySerializer;
    }

    if (v instanceof Int16Array) {
      return this.int16ArraySerializer;
    }

    if (v instanceof Int32Array) {
      return this.int32ArraySerializer;
    }

    if (v instanceof BigInt64Array) {
      return this.int64ArraySerializer;
    }

    if (v instanceof Float32Array) {
      return this.float32ArraySerializer;
    }

    if (v instanceof Float64Array) {
      return this.float64ArraySerializer;
    }

    if (Array.isArray(v)) {
      return this.listSerializer;
    }

    if (typeof v === "boolean") {
      return this.boolSerializer;
    }

    if (typeof v === "bigint") {
      return this.int64Serializer;
    }

    if (v instanceof Date) {
      return this.datetimeSerializer;
    }

    if (v instanceof Map) {
      return this.mapSerializer;
    }

    if (v instanceof Set) {
      return this.setSerializer;
    }

    if (getUnknownTypeMeta(v) !== undefined) {
      return this.unknownStructSerializer;
    }

    if (typeof v === "object" && v !== null && ForyTypeInfoSymbol in v) {
      const typeInfo = (v[ForyTypeInfoSymbol] as WithForyClsInfo).structTypeInfo;
      return this.getSerializerByTypeInfo(typeInfo);
    }

    throw new Error(`Failed to detect the Fory type from JavaScript type: ${typeof v}`);
  }
}
