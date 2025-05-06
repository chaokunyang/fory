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

import { StructTypeInfo, Type } from "./typeInfo";
import fury from "./fury";
import { TypeMeta } from "./meta/TypeMeta";
import { BinaryReader } from "./reader";
import { Serializer } from "./type";
import { BinaryWriter } from "./writer";

export class TypeMetaResolver {
  private disposeTypeInfo: StructTypeInfo[] = [];
  private dynamicTypeId = 0;
  private typeMeta: TypeMeta[] = [];

  constructor(private fury: fury) {

  }

  private typeMetaToTypeInfo(typeMeta: TypeMeta, ns: string, typeName: string) {
    const typeId = typeMeta.getTypeId();
    return Type.struct({
      typeId: typeId < 0xFF ? undefined : typeId,
      namespace: ns,
      typeName,
    }, {
      ...Object.fromEntries(typeMeta.getFieldInfo().map((x) => {
        const typeId = x.getFieldId();
        const fieldName = x.getFieldName();
        const typeInfo = this.fury.classResolver.getTypeInfo(typeId);
        if (!typeInfo) {
          throw new Error(`${typeId} not registered`); // todo
        }
        return [fieldName, typeInfo];
      })),
    });
  }

  genSerializerByTypeMetaRuntime(typeMeta: TypeMeta, ns: string, typeName: string): Serializer {
    const typeInfo = this.typeMetaToTypeInfo(typeMeta, ns, typeName);
    return this.fury.registerSerializer(typeInfo, true).serializer;
  }

  readTypeMeta(reader: BinaryReader): TypeMeta {
    const idOrLen = reader.varUInt32();
    if (idOrLen & 1) {
      return this.typeMeta[idOrLen >> 1];
    } else {
      idOrLen >> 1; // not used
      const typeMeta = TypeMeta.fromBytes(reader);
      this.typeMeta.push(typeMeta);
      return typeMeta;
    }
  }

  writeTypeMeta(typeInfo: StructTypeInfo, writer: BinaryWriter, bytes: Uint8Array) {
    if (typeInfo.dynamicTypeId !== -1) {
      writer.varUInt32(((this.dynamicTypeId + 1) << 1) | 1);
    } else {
      typeInfo.dynamicTypeId = this.dynamicTypeId;
      this.dynamicTypeId += 1;
      this.disposeTypeInfo.push(typeInfo);
      writer.varUInt32(bytes.byteLength << 1);
      writer.buffer(bytes);
    }
  }

  reset() {
    this.disposeTypeInfo.forEach((x) => {
      x.dynamicTypeId = -1;
    });
    this.disposeTypeInfo = [];
    this.dynamicTypeId = 0;
  }
}
