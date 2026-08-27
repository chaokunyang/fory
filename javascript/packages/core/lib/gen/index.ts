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

import { TypeId, Serializer } from "../type";
import { TypeInfo } from "../typeInfo";
import { CodegenRegistry } from "./router";
import { CodecBuilder, SerializerLookup } from "./builder";
import { Scope } from "./scope";
import { CompatibleScalarConverter } from "../compatible/scalar";
import "./array";
import "./struct";
import "./string";
import "./bool";
import "./datetime";
import "./decimal";
import "./map";
import "./number";
import "./set";
import "./struct";
import "./typedArray";
import "./enum";
import "./any";
import "./union";
import "./ext";
import TypeResolver from "../typeResolver";
import {
  checkedTypeMetaSerializerSymbol,
  checkedTypeMetaWireTypeIdSymbol,
  localTypeMetaSymbol,
  TypeMeta,
} from "../meta/TypeMeta";

CodegenRegistry.registerExternal(CompatibleScalarConverter);

type SerializerFactoryBuilder = () => (
  typeResolver: TypeResolver,
  serializerLookup: SerializerLookup,
  external: unknown,
  typeInfo: TypeInfo,
  options: { [key: string]: unknown },
  localTypeMeta: TypeMeta | undefined,
  localTypeMetaSymbol: symbol,
  checkedTypeMetaSerializerSymbol: symbol,
  checkedTypeMetaWireTypeIdSymbol: symbol,
) => Serializer;

type SerializerCreator = (serializerLookup: SerializerLookup) => Serializer;

interface GeneratedRegistration {
  typeInfo: TypeInfo;
  serializer: Serializer;
  captureOwner: Serializer;
  preparing: boolean;
}

export class Gen {
  static external = CodegenRegistry.getExternal();

  private generatedRegistrations: GeneratedRegistration[] = [];
  private readonly serializerLookup: SerializerLookup;

  constructor(
    private typeResolver: TypeResolver,
    private regOptions: { [key: string]: any } = {},
  ) {
    // Generator-time TypeInfo queries see initialized local serializers for codegen decisions.
    // Factory-init ID/name queries instead return the stable owner captured by runtime closures.
    this.serializerLookup = {
      getSerializerByTypeInfo: (typeInfo) => this.getGeneratedSerializer(typeInfo),
      getSerializerById: (id, userTypeId) => this.getCapturedSerializerById(id, userTypeId),
      getSerializerByName: (name) => this.getCapturedSerializerByName(name),
    };
  }

  private prepare(typeInfo: TypeInfo, serializerLookup: SerializerLookup): SerializerCreator {
    const InnerGeneratorClass = CodegenRegistry.get(typeInfo.typeId);
    if (!InnerGeneratorClass) {
      throw new Error(`${typeInfo.typeId} generator not exists`);
    }
    const scope = new Scope();
    const generator = new InnerGeneratorClass(
      typeInfo,
      new CodecBuilder(scope, this.typeResolver, serializerLookup),
      scope,
    );

    const funcString = generator.toSerializer();
    let factoryBuilder: SerializerFactoryBuilder;
    if (this.typeResolver.config && this.typeResolver.config.hooks) {
      const afterCodeGenerated = this.typeResolver.config.hooks.afterCodeGenerated;
      if (typeof afterCodeGenerated === "function") {
        factoryBuilder = new Function(afterCodeGenerated(funcString)) as SerializerFactoryBuilder;
      } else {
        factoryBuilder = new Function(funcString) as SerializerFactoryBuilder;
      }
    } else {
      factoryBuilder = new Function(funcString) as SerializerFactoryBuilder;
    }
    const factory = factoryBuilder();
    const localTypeMeta = generator.getLocalTypeMeta();
    return (factoryLookup) =>
      factory(
        this.typeResolver,
        factoryLookup,
        Gen.external,
        typeInfo,
        this.regOptions,
        localTypeMeta,
        localTypeMetaSymbol,
        checkedTypeMetaSerializerSymbol,
        checkedTypeMetaWireTypeIdSymbol,
      );
  }

  private isRegistered(typeInfo: TypeInfo) {
    return !!this.typeResolver.getSerializerByTypeInfo(typeInfo);
  }

  private isFullyGenerated(typeInfo: TypeInfo) {
    const ser = this.getGeneratedSerializer(typeInfo);
    return ser && ser._initialized;
  }

  private sameRegistration(left: TypeInfo, right: TypeInfo) {
    const leftTypeId = this.typeResolver.computeTypeId(left);
    const rightTypeId = this.typeResolver.computeTypeId(right);
    if (leftTypeId !== rightTypeId) {
      return false;
    }
    if (TypeId.isNamedType(leftTypeId)) {
      return left.named === right.named;
    }
    if (TypeId.needsUserTypeId(leftTypeId)) {
      return left.userTypeId === right.userTypeId;
    }
    return true;
  }

  private findRegistration(typeInfo: TypeInfo) {
    return this.generatedRegistrations.find((entry) =>
      this.sameRegistration(entry.typeInfo, typeInfo),
    );
  }

  private addRegistration(typeInfo: TypeInfo) {
    const owner = this.typeResolver.createSerializerPlaceholder();
    const entry: GeneratedRegistration = {
      typeInfo,
      serializer: owner,
      captureOwner: this.typeResolver.getSerializerByTypeInfo(typeInfo) ?? owner,
      preparing: false,
    };
    this.generatedRegistrations.push(entry);
    return entry;
  }

  private getGeneratedSerializer(typeInfo: TypeInfo) {
    return (
      this.findRegistration(typeInfo)?.serializer ??
      this.typeResolver.getSerializerByTypeInfo(typeInfo)
    );
  }

  private getCapturedSerializerById(id: number, userTypeId?: number) {
    const entry = this.generatedRegistrations.find((candidate) => {
      const typeId = this.typeResolver.computeTypeId(candidate.typeInfo);
      if (typeId !== id || TypeId.isNamedType(typeId)) {
        return false;
      }
      if (TypeId.needsUserTypeId(typeId)) {
        if (userTypeId !== undefined && userTypeId !== -1) {
          return candidate.typeInfo.userTypeId === userTypeId;
        }
        return candidate.typeInfo.userTypeId === -1;
      }
      return true;
    });
    return entry?.captureOwner ?? this.typeResolver.getSerializerById(id, userTypeId);
  }

  private getCapturedSerializerByName(name: number | string) {
    const entry = this.generatedRegistrations.find(
      (candidate) =>
        typeof name === "string" &&
        TypeId.isNamedType(this.typeResolver.computeTypeId(candidate.typeInfo)) &&
        candidate.typeInfo.named === name,
    );
    return entry?.captureOwner ?? this.typeResolver.getSerializerByName(name);
  }

  private prepareRegistration(typeInfo: TypeInfo, children: TypeInfo[]) {
    let entry = this.findRegistration(typeInfo);
    if (entry?.serializer._initialized || entry?.preparing) {
      return;
    }
    if (entry === undefined) {
      entry = this.addRegistration(typeInfo);
    } else {
      entry.typeInfo = typeInfo;
    }
    entry.preparing = true;
    try {
      for (const child of children) {
        this.traversalContainer(child);
      }
      const serializer = this.prepare(typeInfo, this.serializerLookup)(this.serializerLookup);
      Object.assign(entry.serializer, serializer);
    } finally {
      entry.preparing = false;
    }
  }

  private traversalContainer(typeInfo: TypeInfo) {
    if (TypeId.userDefinedType(typeInfo.typeId)) {
      if (this.isFullyGenerated(typeInfo)) {
        return;
      }
      const options = typeInfo.options;
      const unionType =
        typeInfo.typeId === TypeId.UNION ||
        typeInfo.typeId === TypeId.TYPED_UNION ||
        typeInfo.typeId === TypeId.NAMED_UNION;
      if (unionType && options?.cases && Object.keys(options.cases).length > 0) {
        this.prepareRegistration(typeInfo, Object.values(options.cases));
        return;
      } else if (options?.props && Object.keys(options.props).length > 0) {
        this.prepareRegistration(typeInfo, Object.values(options.props));
      } else if (!this.isRegistered(typeInfo) && TypeId.structType(typeInfo.typeId)) {
        // Keep the recursive owner local until every generated factory has completed. If a prior
        // registration published a forward owner, factory captures use that owner without mutating
        // it; commit initializes it in place so earlier serializers keep the same identity.
        if (this.findRegistration(typeInfo) === undefined) {
          this.addRegistration(typeInfo);
        }
      } else if (TypeId.enumType(typeInfo.typeId) && !this.isRegistered(typeInfo)) {
        this.prepareRegistration(typeInfo, []);
      }
    }
    if (typeInfo.typeId === TypeId.LIST) {
      this.traversalContainer(typeInfo.options!.inner!);
    }
    if (typeInfo.typeId === TypeId.SET) {
      this.traversalContainer(typeInfo.options!.key!);
    }
    if (typeInfo.typeId === TypeId.MAP) {
      if (!typeInfo.options?.key || !typeInfo.options?.value) {
        throw new Error("map type must have key and value");
      }
      this.traversalContainer(typeInfo.options!.key!);
      this.traversalContainer(typeInfo.options!.value!);
    }
    if (typeInfo.options?.cases) {
      Object.values(typeInfo.options.cases).forEach((caseTypeInfo) => {
        this.traversalContainer(caseTypeInfo);
      });
    }
  }

  reGenerateSerializer(typeInfo: TypeInfo) {
    return this.prepare(typeInfo, this.typeResolver)(this.typeResolver);
  }

  generateSerializer(typeInfo: TypeInfo) {
    this.traversalContainer(typeInfo);
    const serializer = this.typeResolver.getSerializerByTypeInfo(typeInfo);
    if (!serializer?._initialized) {
      let registration = this.findRegistration(typeInfo);
      if (registration === undefined) {
        registration = this.addRegistration(typeInfo);
      }
      if (!registration.serializer._initialized) {
        this.prepareRegistration(typeInfo, []);
      }
    }

    // Generated factories may execute application-transformed code, so every factory completes
    // against local owners before the resolver performs the only global publication step.
    this.typeResolver.commitGeneratedSerializers(typeInfo, this.generatedRegistrations);
    return this.typeResolver.getSerializerByTypeInfo(typeInfo)!;
  }
}
