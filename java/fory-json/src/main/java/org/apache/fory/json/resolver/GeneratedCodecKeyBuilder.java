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

package org.apache.fory.json.resolver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import org.apache.fory.json.codec.CodecUtils;
import org.apache.fory.json.codec.CollectionCodec;
import org.apache.fory.json.codec.DirectUnboxedValueCodec;
import org.apache.fory.json.codec.JsonUnwrappedInfo;
import org.apache.fory.json.codec.ObjectCodec;
import org.apache.fory.json.codec.ObjectCodec.AnyInfo;
import org.apache.fory.json.codec.TransparentUnboxedValueCodec;
import org.apache.fory.json.codec.UnboxedValueCodec;
import org.apache.fory.json.codegen.GeneratedCodecKey;
import org.apache.fory.json.codegen.GeneratedCodecKey.Role;
import org.apache.fory.json.codegen.JsonCodegen;
import org.apache.fory.json.meta.JsonCreatorFieldInfo;
import org.apache.fory.json.meta.JsonFieldInfo;

/** Builds generated-codec keys from configuration and direct codec inputs. */
final class GeneratedCodecKeyBuilder {
  private final JsonTypeResolver resolver;
  private final ObjectCodec<?> owner;
  private final JsonTypeResolver.CapabilityKind kind;
  private final ArrayList<Object> keyParts;
  private int occurrence;

  private GeneratedCodecKeyBuilder(
      JsonTypeResolver resolver,
      ObjectCodec<?> owner,
      JsonTypeResolver.CapabilityKind kind,
      ArrayList<Object> keyParts) {
    this.resolver = resolver;
    this.owner = owner;
    this.kind = kind;
    this.keyParts = keyParts;
  }

  static GeneratedCodecKeyBuilder object(
      JsonTypeResolver resolver,
      JsonTypeInfo typeInfo,
      ObjectCodec<?> owner,
      JsonTypeResolver.CapabilityKind kind) {
    JsonSharedRegistry registry = resolver.sharedRegistry();
    ArrayList<Object> keyParts = new ArrayList<>();
    if (!JsonTypeResolver.readerKind(kind)) {
      keyParts.add(registry.writeNullFields());
    }
    keyParts.add(registry.propertyDiscoveryEnabled());
    keyParts.add(registry.propertyNamingStrategy());
    String factoryKey = typeInfo.objectFactoryKey();
    if (factoryKey != null) {
      keyParts.add(factoryKey);
    }

    JsonUnwrappedInfo unwrapped = owner.unwrappedInfo();
    addMixins(registry, owner, unwrapped, keyParts);
    addUnwrappedFactoryKeys(resolver, unwrapped, keyParts);
    return new GeneratedCodecKeyBuilder(resolver, owner, kind, keyParts);
  }

  GeneratedCodecKey build() {
    return GeneratedCodecKey.object(owner.type(), role(kind), keyParts.toArray());
  }

  static GeneratedCodecKey collection(
      JsonTypeInfo typeInfo, CollectionCodec<?> owner, JsonTypeResolver.CapabilityKind kind) {
    Class<?> rawType = CodecUtils.rawType(typeInfo.type(), Collection.class);
    Class<?> elementType =
        CodecUtils.rawType(CodecUtils.elementType(typeInfo.type()), Object.class);
    return GeneratedCodecKey.collection(
        rawType,
        elementType,
        kind == JsonTypeResolver.CapabilityKind.UTF8_WRITER
            ? Role.UTF8_COLLECTION_WRITER
            : Role.UTF8_COLLECTION_READER,
        owner instanceof CollectionCodec.StringCollectionCodec);
  }

  private static void addMixins(
      JsonSharedRegistry registry,
      ObjectCodec<?> owner,
      JsonUnwrappedInfo unwrapped,
      ArrayList<Object> keyParts) {
    IdentityHashMap<Class<?>, Boolean> seen = new IdentityHashMap<>();
    Class<?> ownerMixin = registry.mixinType(owner.type());
    addMixin(ownerMixin, seen, keyParts);
    if (unwrapped != null) {
      for (JsonUnwrappedInfo.Group group : unwrapped.groups()) {
        Class<?> childType = group.childCodec().type();
        addMixin(registry.mixinType(childType), seen, keyParts);
      }
    }
  }

  private static void addMixin(
      Class<?> mixin,
      IdentityHashMap<Class<?>, Boolean> seen,
      ArrayList<Object> keyParts) {
    if (mixin != null && seen.put(mixin, Boolean.TRUE) == null) {
      keyParts.add(mixin);
    }
  }

  private static void addUnwrappedFactoryKeys(
      JsonTypeResolver resolver,
      JsonUnwrappedInfo unwrapped,
      ArrayList<Object> keyParts) {
    if (unwrapped == null) {
      return;
    }
    JsonUnwrappedInfo.Group[] groups = unwrapped.groups();
    for (int i = 0; i < groups.length; i++) {
      String factoryKey = resolver.objectFactoryKey(groups[i].childCodec());
      if (factoryKey != null) {
        keyParts.add(i);
        keyParts.add(factoryKey);
      }
    }
  }

  void addAny(boolean storesCapability) {
    AnyInfo any = owner.anyInfo();
    if (any == null || !storesCapability) {
      return;
    }
    boolean slot =
        JsonTypeResolver.readerKind(kind)
            ? resolver.usesReaderSlot(owner, any.valueTypeInfo())
            : resolver.usesWriterSlot(owner, any.valueTypeInfo());
    if (slot) {
      addSlot(keyParts, occurrence);
    }
  }

  void addField(JsonFieldInfo field, boolean storesCapability) {
    boolean reader = JsonTypeResolver.readerKind(kind);
    JsonTypeInfo typeInfo = reader ? field.readTypeInfo() : field.writeTypeInfo();
    UnboxedValueCodec unboxed =
        reader ? field.readUnboxedValueCodec() : field.writeUnboxedValueCodec();
    boolean typeDiffers =
        reader ? field.readTypeDiffersFromDeclaration() : field.writeTypeDiffersFromDeclaration();
    Class<?> codecClass =
        unboxed != null
            ? unboxed.getClass()
            : storesCapability && !typeDiffers
                ? keyCodecClass(resolver, owner, typeInfo, kind)
                : null;
    if (codecClass != null) {
      Class<?> logicalClass =
          CodecUtils.rawType(reader ? field.readType() : field.writeType(), Object.class);
      addCodec(keyParts, occurrence, logicalClass, codecClass);
    }
    addDirectTerminal(keyParts, occurrence, unboxed, typeInfo);
    if (storesCapability && usesSlot(resolver, owner, typeInfo, kind)) {
      addSlot(keyParts, occurrence);
    }
    occurrence++;
  }

  void addCreatorField(JsonCreatorFieldInfo field, boolean storesCapability) {
    UnboxedValueCodec unboxed = field.unboxedValueCodec();
    if (unboxed != null) {
      addCodec(keyParts, occurrence, field.typeRef().getRawType(), unboxed.getClass());
    }
    addDirectTerminal(keyParts, occurrence, unboxed, field.typeInfo());
    if (storesCapability && usesSlot(resolver, owner, field.typeInfo(), kind)) {
      addSlot(keyParts, occurrence);
    }
    occurrence++;
  }

  private static void addDirectTerminal(
      ArrayList<Object> keyParts, int occurrence, UnboxedValueCodec outer, JsonTypeInfo typeInfo) {
    if (!(outer instanceof TransparentUnboxedValueCodec)) {
      return;
    }
    UnboxedValueCodec terminal = typeInfo.unboxedValueCodec();
    if (terminal instanceof DirectUnboxedValueCodec) {
      addCodec(keyParts, occurrence, typeInfo.rawType(), terminal.getClass());
    }
  }

  private static void addCodec(
      ArrayList<Object> keyParts, int occurrence, Class<?> logicalClass, Class<?> codecClass) {
    keyParts.add(occurrence);
    keyParts.add(logicalClass);
    keyParts.add(codecClass);
  }

  private static void addSlot(ArrayList<Object> keyParts, int occurrence) {
    keyParts.add(occurrence);
  }

  private static boolean usesSlot(
      JsonTypeResolver resolver,
      ObjectCodec<?> owner,
      JsonTypeInfo typeInfo,
      JsonTypeResolver.CapabilityKind kind) {
    return JsonTypeResolver.readerKind(kind)
        ? resolver.usesReaderSlot(owner, typeInfo)
        : resolver.usesWriterSlot(owner, typeInfo);
  }

  private static Class<?> keyCodecClass(
      JsonTypeResolver resolver,
      ObjectCodec<?> owner,
      JsonTypeInfo typeInfo,
      JsonTypeResolver.CapabilityKind kind) {
    if (resolver.canonicalObjectOwner(typeInfo) != null) {
      return null;
    }
    if ((kind == JsonTypeResolver.CapabilityKind.UTF8_WRITER
            && resolver.exactUtf8WriterCollection(typeInfo) != null)
        || (kind == JsonTypeResolver.CapabilityKind.UTF8_READER
            && resolver.exactUtf8Collection(typeInfo) != null)) {
      return null;
    }
    JsonSharedRegistry registry = resolver.sharedRegistry();
    // Hosted classes always store ordinary registered codecs through the role interface. Native
    // runtime must reconstruct the same key from stable metadata without replaying hosted loader
    // or module visibility.
    if (registry.hostedCodegen() || registry.nativeGeneratedClasses()) {
      return null;
    }
    Class<?> codecClass = typeInfo.registeredCodecClass();
    if (codecClass == null) {
      return null;
    }
    return JsonCodegen.isCodecClassSourceAccessible(codecClass) ? codecClass : null;
  }

  private static Role role(JsonTypeResolver.CapabilityKind kind) {
    switch (kind) {
      case STRING_WRITER:
        return Role.STRING_WRITER;
      case UTF8_WRITER:
        return Role.UTF8_WRITER;
      case LATIN1_READER:
        return Role.LATIN1_READER;
      case UTF16_READER:
        return Role.UTF16_READER;
      case UTF8_READER:
        return Role.UTF8_READER;
      default:
        throw new IllegalStateException("Unknown JSON capability kind " + kind);
    }
  }
}
