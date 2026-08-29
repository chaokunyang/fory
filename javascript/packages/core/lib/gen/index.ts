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

import { CustomSerializer, TypeId, Serializer } from "../type";
import { sealTypeInfo, TypeInfo } from "../typeInfo";
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
  options: object | undefined,
  localTypeMeta: TypeMeta | undefined,
  localTypeMetaSymbol: symbol,
  checkedTypeMetaSerializerSymbol: symbol,
  checkedTypeMetaWireTypeIdSymbol: symbol,
) => Serializer;

type SerializerFactory = ReturnType<SerializerFactoryBuilder>;

interface GeneratedFactory {
  create: SerializerFactory;
  localTypeMeta: TypeMeta | undefined;
  fixedSize: number;
  readDataAlwaysAdvances: boolean;
}

const uninitializedSerializer: Serializer = {
  _initialized: false,
  fixedSize: 0,
  getTypeInfo: () => {
    throw new Error("serializer is not initialized");
  },
  getTypeId: () => {
    throw new Error("serializer is not initialized");
  },
  getUserTypeId: () => {
    throw new Error("serializer is not initialized");
  },
  needToWriteRef: () => {
    throw new Error("serializer is not initialized");
  },
  getHash: () => {
    throw new Error("serializer is not initialized");
  },
  write: (value: any) => {
    void value;
    throw new Error("serializer is not initialized");
  },
  writeRef: (value: any) => {
    void value;
    throw new Error("serializer is not initialized");
  },
  writeNoRef: (value: any) => {
    void value;
    throw new Error("serializer is not initialized");
  },
  writeRefOrNull: (value: any) => {
    void value;
    throw new Error("serializer is not initialized");
  },
  writeTypeInfo: (value: any) => {
    void value;
    throw new Error("serializer is not initialized");
  },
  read: (fromRef: boolean) => {
    void fromRef;
    throw new Error("serializer is not initialized");
  },
  readRef: () => {
    throw new Error("serializer is not initialized");
  },
  readRefWithoutTypeInfo: () => {
    throw new Error("serializer is not initialized");
  },
  readNoRef: (fromRef: boolean) => {
    void fromRef;
    throw new Error("serializer is not initialized");
  },
  readTypeInfo: () => {
    throw new Error("serializer is not initialized");
  },
  readDataAlwaysAdvances: false,
};

interface GeneratedRegistration {
  typeInfo: TypeInfo;
  serializer: Serializer;
  preparing: boolean;
  factory?: GeneratedFactory;
}

export class Gen {
  static external = CodegenRegistry.getExternal();

  constructor(
    private typeResolver: TypeResolver,
    private rootCustomSerializer?: CustomSerializer<any>,
  ) {}

  private generateFactory(
    typeInfo: TypeInfo,
    serializerLookup: SerializerLookup,
  ): GeneratedFactory {
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

    const generated = generator.toSerializer();
    let factoryBuilder: SerializerFactoryBuilder;
    if (this.typeResolver.config && this.typeResolver.config.hooks) {
      const afterCodeGenerated = this.typeResolver.config.hooks.afterCodeGenerated;
      if (typeof afterCodeGenerated === "function") {
        factoryBuilder = new Function(
          afterCodeGenerated(generated.source),
        ) as SerializerFactoryBuilder;
      } else {
        factoryBuilder = new Function(generated.source) as SerializerFactoryBuilder;
      }
    } else {
      factoryBuilder = new Function(generated.source) as SerializerFactoryBuilder;
    }
    return {
      create: factoryBuilder(),
      localTypeMeta: generated.localTypeMeta,
      fixedSize: generated.fixedSize,
      readDataAlwaysAdvances: generated.readDataAlwaysAdvances,
    };
  }

  private createSerializer(
    typeInfo: TypeInfo,
    serializerLookup: SerializerLookup,
    factory: GeneratedFactory,
  ): Serializer {
    const options = TypeId.extType(typeInfo.typeId)
      ? { ...typeInfo.options, customSerializer: this.rootCustomSerializer }
      : typeInfo.options;
    return factory.create(
      this.typeResolver,
      serializerLookup,
      Gen.external,
      typeInfo,
      options,
      factory.localTypeMeta,
      localTypeMetaSymbol,
      checkedTypeMetaSerializerSymbol,
      checkedTypeMetaWireTypeIdSymbol,
    );
  }

  private isRegistered(typeInfo: TypeInfo) {
    return !!this.typeResolver.getSerializerByTypeInfo(typeInfo);
  }

  private isFullyGenerated(typeInfo: TypeInfo, registrations: GeneratedRegistration[]) {
    const ser = this.getGeneratedSerializer(typeInfo, registrations);
    return ser && ser._initialized;
  }

  private sameRegistration(left: TypeInfo, right: TypeInfo) {
    const leftTypeId = this.typeResolver.computeTypeId(left);
    const rightTypeId = this.typeResolver.computeTypeId(right);
    if (TypeId.isNamedType(leftTypeId) && TypeId.isNamedType(rightTypeId)) {
      return left.named === right.named;
    }
    if (
      TypeId.needsUserTypeId(leftTypeId) &&
      TypeId.needsUserTypeId(rightTypeId) &&
      left.userTypeId !== -1 &&
      right.userTypeId !== -1
    ) {
      return left.userTypeId === right.userTypeId;
    }
    if (TypeId.userDefinedType(leftTypeId) || TypeId.userDefinedType(rightTypeId)) {
      return left === right;
    }
    return leftTypeId === rightTypeId;
  }

  private sameTypeFamily(left: TypeInfo, right: TypeInfo) {
    const leftTypeId = left.typeId;
    const rightTypeId = right.typeId;
    if (TypeId.structType(leftTypeId) && TypeId.structType(rightTypeId)) {
      return true;
    }
    if (TypeId.enumType(leftTypeId) && TypeId.enumType(rightTypeId)) {
      return true;
    }
    if (TypeId.extType(leftTypeId) && TypeId.extType(rightTypeId)) {
      return true;
    }
    const leftUnion =
      leftTypeId === TypeId.UNION ||
      leftTypeId === TypeId.TYPED_UNION ||
      leftTypeId === TypeId.NAMED_UNION;
    const rightUnion =
      rightTypeId === TypeId.UNION ||
      rightTypeId === TypeId.TYPED_UNION ||
      rightTypeId === TypeId.NAMED_UNION;
    return leftUnion && rightUnion;
  }

  private hasCompleteDefinition(typeInfo: TypeInfo) {
    const options = typeInfo.options;
    if (TypeId.structType(typeInfo.typeId)) {
      return options?.props !== undefined;
    }
    if (TypeId.enumType(typeInfo.typeId)) {
      return options?.enumProps !== undefined;
    }
    if (TypeId.extType(typeInfo.typeId)) {
      return options?.props !== undefined || options?.creator !== undefined;
    }
    return (
      (typeInfo.typeId === TypeId.UNION ||
        typeInfo.typeId === TypeId.TYPED_UNION ||
        typeInfo.typeId === TypeId.NAMED_UNION) &&
      options?.cases !== undefined
    );
  }

  private hasRegistryIdentity(typeInfo: TypeInfo) {
    const typeId = this.typeResolver.computeTypeId(typeInfo);
    if (!TypeId.userDefinedType(typeId) || TypeId.isNamedType(typeId)) {
      return true;
    }
    if (TypeId.needsUserTypeId(typeId) && typeInfo.userTypeId !== -1) {
      return true;
    }
    // A complete anonymous schema belongs to this generation graph. Only the definition-free
    // generic serializer can be the canonical owner of a raw user-defined wire type ID.
    return !this.hasCompleteDefinition(typeInfo);
  }

  private sameDefinition(left: TypeInfo, right: TypeInfo) {
    if (left === right) {
      return true;
    }
    const leftOptions = left.options!;
    const rightOptions = right.options!;
    return (
      this.sameTypeFamily(left, right) &&
      left.named === right.named &&
      left.namespace === right.namespace &&
      left.typeName === right.typeName &&
      left.userTypeId === right.userTypeId &&
      left.evolving === right.evolving &&
      leftOptions.props === rightOptions.props &&
      leftOptions.enumProps === rightOptions.enumProps &&
      leftOptions.cases === rightOptions.cases &&
      leftOptions.fieldEntries === rightOptions.fieldEntries &&
      leftOptions.preserveFieldOrder === rightOptions.preserveFieldOrder &&
      leftOptions.withConstructor === rightOptions.withConstructor &&
      leftOptions.creator === rightOptions.creator
    );
  }

  private checkTypeFamily(owner: TypeInfo, typeInfo: TypeInfo) {
    if (
      TypeId.userDefinedType(typeInfo.typeId) &&
      (!TypeId.userDefinedType(owner.typeId) || !this.sameTypeFamily(owner, typeInfo))
    ) {
      throw new Error("conflicting type families for the same registry identity");
    }
  }

  private checkDefinitionOwner(owner: TypeInfo, typeInfo: TypeInfo) {
    if (!TypeId.userDefinedType(typeInfo.typeId)) {
      return;
    }
    this.checkTypeFamily(owner, typeInfo);
    if (
      this.hasCompleteDefinition(owner) &&
      this.hasCompleteDefinition(typeInfo) &&
      !this.sameDefinition(owner, typeInfo)
    ) {
      throw new Error("conflicting complete definitions for the same registry identity");
    }
  }

  private findRegistration(typeInfo: TypeInfo, registrations: GeneratedRegistration[]) {
    const registration = registrations.find((entry) =>
      this.sameRegistration(entry.typeInfo, typeInfo),
    );
    if (registration !== undefined) {
      this.checkDefinitionOwner(registration.typeInfo, typeInfo);
    }
    return registration;
  }

  private addRegistration(typeInfo: TypeInfo, registrations: GeneratedRegistration[]) {
    const owner = { ...uninitializedSerializer };
    const entry: GeneratedRegistration = {
      typeInfo,
      serializer: owner,
      preparing: false,
    };
    owner.getTypeInfo = () => entry.typeInfo;
    registrations.push(entry);
    return entry;
  }

  private getGeneratedSerializer(typeInfo: TypeInfo, registrations: GeneratedRegistration[]) {
    const published = this.hasRegistryIdentity(typeInfo)
      ? this.typeResolver.getSerializerByTypeInfo(typeInfo)
      : undefined;
    if (published !== undefined) {
      this.checkDefinitionOwner(published.getTypeInfo(), typeInfo);
      return published;
    }
    return this.findRegistration(typeInfo, registrations)?.serializer;
  }

  private getCapturedSerializerById(
    registrations: GeneratedRegistration[],
    id: number,
    userTypeId?: number,
  ) {
    const published = this.typeResolver.getSerializerById(id, userTypeId);
    if (published !== undefined) {
      return published;
    }
    if (id === TypeId.TYPED_UNION && (userTypeId === undefined || userTypeId === -1)) {
      throw new Error("anonymous union serializer requires its TypeInfo owner");
    }
    const entry = registrations.find((candidate) => {
      const typeId = this.typeResolver.computeTypeId(candidate.typeInfo);
      if (
        TypeId.needsUserTypeId(id) &&
        TypeId.needsUserTypeId(typeId) &&
        userTypeId !== undefined &&
        userTypeId !== -1
      ) {
        return candidate.typeInfo.userTypeId === userTypeId;
      }
      return typeId === id;
    });
    return entry?.serializer as Serializer;
  }

  private getCapturedSerializerByName(
    registrations: GeneratedRegistration[],
    name: number | string,
  ) {
    const published = this.typeResolver.getSerializerByName(name);
    if (published !== undefined) {
      return published;
    }
    const entry = registrations.find(
      (candidate) =>
        typeof name === "string" &&
        TypeId.isNamedType(this.typeResolver.computeTypeId(candidate.typeInfo)) &&
        candidate.typeInfo.named === name,
    );
    return entry?.serializer;
  }

  private prepareRegistration(
    typeInfo: TypeInfo,
    children: TypeInfo[],
    registrations: GeneratedRegistration[],
    factories: GeneratedRegistration[],
    serializerLookup: SerializerLookup,
  ) {
    let entry = this.findRegistration(typeInfo, registrations);
    if (entry?.serializer._initialized || entry?.preparing) {
      return;
    }
    if (entry === undefined) {
      entry = this.addRegistration(typeInfo, registrations);
    } else {
      entry.typeInfo = typeInfo;
    }
    entry.preparing = true;
    try {
      for (const child of children) {
        this.traversalContainer(child, registrations, factories, serializerLookup);
      }
      entry.factory = this.generateFactory(typeInfo, serializerLookup);
      // This local owner is still unreachable by the resolver. Expose only the completed static
      // facts to later code generation; the final pass installs its runtime methods after hooks.
      entry.serializer.fixedSize = entry.factory.fixedSize;
      entry.serializer.readDataAlwaysAdvances = entry.factory.readDataAlwaysAdvances;
      entry.serializer._initialized = true;
      factories.push(entry);
    } finally {
      entry.preparing = false;
    }
  }

  private seedDefinitions(root: TypeInfo, registrations: GeneratedRegistration[]) {
    const pending = [root];
    const seen = new Set<TypeInfo>();
    while (pending.length > 0) {
      const typeInfo = pending.pop()!;
      if (seen.has(typeInfo)) {
        continue;
      }
      seen.add(typeInfo);
      const options = typeInfo.options;
      if (
        !TypeId.extType(typeInfo.typeId) &&
        this.hasCompleteDefinition(typeInfo) &&
        !this.getGeneratedSerializer(typeInfo, registrations)?._initialized
      ) {
        const registration = this.findRegistration(typeInfo, registrations);
        if (registration === undefined) {
          this.addRegistration(typeInfo, registrations);
        }
      }
      if (options === undefined) {
        continue;
      }
      if (options.props !== undefined) {
        pending.push(...Object.values(options.props));
      }
      if (options.cases !== undefined) {
        pending.push(...Object.values(options.cases));
      }
      if (options.fieldEntries !== undefined) {
        for (const entry of options.fieldEntries) {
          pending.push(entry.typeInfo);
        }
      }
      if (options.inner !== undefined) {
        pending.push(options.inner);
      }
      if (options.key !== undefined) {
        pending.push(options.key);
      }
      if (options.value !== undefined) {
        pending.push(options.value);
      }
    }
    for (const typeInfo of seen) {
      if (TypeId.userDefinedType(typeInfo.typeId)) {
        this.findRegistration(typeInfo, registrations);
      }
    }
  }

  private traversalContainer(
    typeInfo: TypeInfo,
    registrations: GeneratedRegistration[],
    factories: GeneratedRegistration[],
    serializerLookup: SerializerLookup,
  ) {
    if (TypeId.userDefinedType(typeInfo.typeId)) {
      if (this.isFullyGenerated(typeInfo, registrations)) {
        return;
      }
      const options = typeInfo.options;
      const unionType =
        typeInfo.typeId === TypeId.UNION ||
        typeInfo.typeId === TypeId.TYPED_UNION ||
        typeInfo.typeId === TypeId.NAMED_UNION;
      // Extension generation belongs only to an explicit root registration. Check it before the
      // generic props path so a decorated nested extension cannot create a second local owner.
      if (TypeId.extType(typeInfo.typeId)) {
        if (this.findRegistration(typeInfo, registrations) === undefined) {
          throw new Error("nested extension serializer must be registered before use");
        }
        this.prepareRegistration(
          typeInfo,
          Object.values(options?.props ?? {}),
          registrations,
          factories,
          serializerLookup,
        );
        return;
      } else if (unionType && options?.cases && Object.keys(options.cases).length > 0) {
        this.prepareRegistration(
          typeInfo,
          Object.values(options.cases),
          registrations,
          factories,
          serializerLookup,
        );
        return;
      } else if (options?.props !== undefined) {
        this.prepareRegistration(
          typeInfo,
          Object.values(options.props),
          registrations,
          factories,
          serializerLookup,
        );
      } else if (!this.isRegistered(typeInfo) && TypeId.structType(typeInfo.typeId)) {
        if (this.findRegistration(typeInfo, registrations) === undefined) {
          throw new Error("nested struct schema must be registered or defined before use");
        }
      } else if (TypeId.enumType(typeInfo.typeId) && !this.isRegistered(typeInfo)) {
        this.prepareRegistration(typeInfo, [], registrations, factories, serializerLookup);
      }
    }
    if (typeInfo.typeId === TypeId.LIST) {
      this.traversalContainer(typeInfo.options!.inner!, registrations, factories, serializerLookup);
    }
    if (typeInfo.typeId === TypeId.SET) {
      this.traversalContainer(typeInfo.options!.key!, registrations, factories, serializerLookup);
    }
    if (typeInfo.typeId === TypeId.MAP) {
      if (!typeInfo.options?.key || !typeInfo.options?.value) {
        throw new Error("map type must have key and value");
      }
      this.traversalContainer(typeInfo.options!.key!, registrations, factories, serializerLookup);
      this.traversalContainer(typeInfo.options!.value!, registrations, factories, serializerLookup);
    }
    if (typeInfo.options?.cases) {
      Object.values(typeInfo.options.cases).forEach((caseTypeInfo) => {
        this.traversalContainer(caseTypeInfo, registrations, factories, serializerLookup);
      });
    }
  }

  reGenerateSerializer(typeInfo: TypeInfo) {
    const factory = this.generateFactory(typeInfo, this.typeResolver);
    return this.createSerializer(typeInfo, this.typeResolver, factory);
  }

  generateSerializer(typeInfo: TypeInfo) {
    this.typeResolver.ensureRegistrationOpen();
    sealTypeInfo(typeInfo);
    // TypeInfo freezing may invoke application-owned proxy traps. A root entered there closes the
    // resolver before code generation or publication can continue.
    this.typeResolver.ensureRegistrationOpen();
    const registrations: GeneratedRegistration[] = [];
    const factories: GeneratedRegistration[] = [];
    // Generator-time TypeInfo queries see initialized local serializers for codegen decisions.
    // Factory-init ID/name queries instead return the stable owner captured by runtime closures.
    const serializerLookup: SerializerLookup = {
      getSerializerByTypeInfo: (fieldType) => this.getGeneratedSerializer(fieldType, registrations),
      getSerializerById: (id, userTypeId) =>
        this.getCapturedSerializerById(registrations, id, userTypeId),
      getSerializerByName: (name) => this.getCapturedSerializerByName(registrations, name),
    };
    this.seedDefinitions(typeInfo, registrations);
    if (
      !TypeId.structType(typeInfo.typeId) &&
      !this.typeResolver.getSerializerByTypeInfo(typeInfo)?._initialized &&
      this.findRegistration(typeInfo, registrations) === undefined
    ) {
      this.addRegistration(typeInfo, registrations);
    }
    this.traversalContainer(typeInfo, registrations, factories, serializerLookup);
    const publishedRoot = this.typeResolver.getSerializerByTypeInfo(typeInfo);
    if (!publishedRoot?._initialized) {
      let registration = this.findRegistration(typeInfo, registrations);
      if (registration === undefined) {
        registration = this.addRegistration(typeInfo, registrations);
      }
      if (!registration.serializer._initialized) {
        this.prepareRegistration(typeInfo, [], registrations, factories, serializerLookup);
      }
    }

    // Hooks may publish an owner after earlier code generation used an equivalent local schema.
    // Reconcile every identity before invoking any factory so fixed captures use the final owner.
    for (const registration of registrations) {
      if (!this.hasRegistryIdentity(registration.typeInfo)) {
        continue;
      }
      const published = this.typeResolver.getSerializerByTypeInfo(registration.typeInfo);
      if (published !== undefined) {
        this.checkDefinitionOwner(published.getTypeInfo(), registration.typeInfo);
      }
    }
    for (const registration of factories) {
      const published = this.hasRegistryIdentity(registration.typeInfo)
        ? this.typeResolver.getSerializerByTypeInfo(registration.typeInfo)
        : undefined;
      if (published !== undefined) {
        continue;
      }
      Object.assign(
        registration.serializer,
        this.createSerializer(registration.typeInfo, serializerLookup, registration.factory!),
      );
    }
    const serializer = this.getGeneratedSerializer(typeInfo, registrations)!;
    this.typeResolver.commitGeneratedSerializers(
      registrations.filter(
        (registration) =>
          this.hasRegistryIdentity(registration.typeInfo) &&
          this.typeResolver.getSerializerByTypeInfo(registration.typeInfo) === undefined,
      ),
    );
    return serializer;
  }
}
