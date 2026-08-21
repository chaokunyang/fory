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

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import org.apache.fory.json.codec.ClosedSubtypeCodec;
import org.apache.fory.json.codec.CodecUtils;
import org.apache.fory.json.codec.CollectionCodec;
import org.apache.fory.json.codec.DirectUnboxedValueCodec;
import org.apache.fory.json.codec.JsonUnwrappedInfo;
import org.apache.fory.json.codec.ObjectCodec;
import org.apache.fory.json.codec.ObjectCodec.AnyInfo;
import org.apache.fory.json.codec.TransparentUnboxedValueCodec;
import org.apache.fory.json.codec.UnboxedValueCodec;
import org.apache.fory.json.codegen.GeneratedCodecKey;
import org.apache.fory.json.codegen.GeneratedCodecKey.MemberDescriptor;
import org.apache.fory.json.codegen.GeneratedCodecKey.Role;
import org.apache.fory.json.meta.JsonCreatorFieldInfo;
import org.apache.fory.json.meta.JsonCreatorInfo;
import org.apache.fory.json.meta.JsonFieldAccessor;
import org.apache.fory.json.meta.JsonFieldInfo;

/** Builds exact generated-codec keys from resolved JSON metadata. */
final class GeneratedCodecKeyBuilder {
  private GeneratedCodecKeyBuilder() {}

  static GeneratedCodecKey object(
      JsonTypeResolver resolver,
      JsonTypeInfo typeInfo,
      ObjectCodec<?> owner,
      JsonTypeResolver.CapabilityKind kind) {
    JsonSharedRegistry registry = resolver.sharedRegistry();
    ArrayList<Object> keyParts = new ArrayList<>();
    ArrayList<Class<?>> referencedClasses = new ArrayList<>();
    keyParts.add(registry.writeNullFields());
    keyParts.add(registry.propertyDiscoveryEnabled());
    keyParts.add(registry.propertyNamingStrategy());
    addMixinKeyParts(registry, owner.type(), keyParts, referencedClasses);
    if (JsonTypeResolver.readerKind(kind)) {
      keyParts.add(owner.graphMemoryBytes());
      keyParts.add(owner.hasValidators());
    }
    JsonUnwrappedInfo unwrapped = owner.unwrappedInfo();
    if (unwrapped != null) {
      for (JsonUnwrappedInfo.Group group : unwrapped.groups()) {
        ObjectCodec<?> child = group.childCodec();
        Class<?> childType = child.type();
        keyParts.add(childType);
        referencedClasses.add(childType);
        addMixinKeyParts(registry, childType, keyParts, referencedClasses);
        keyParts.add(MemberDescriptor.of(accessorMember(group.declaration().writeAccessor())));
        keyParts.add(MemberDescriptor.of(accessorMember(group.declaration().readAccessor())));
        keyParts.add(group.readIndex());
        keyParts.add(group.parent() == null ? -1 : group.parent().readIndex());
        keyParts.add(group.declaration().constructionIndex());
        Class<?> parentType = group.parentCodec().type();
        keyParts.add(parentType);
        referencedClasses.add(parentType);
        keyParts.add(group.writeEnabled());
        keyParts.add(group.readEnabled());
        if (JsonTypeResolver.readerKind(kind)) {
          keyParts.add(child.graphMemoryBytes());
          keyParts.add(child.hasValidators());
          addCreatorKeyParts(resolver, child.creatorInfo(), keyParts, referencedClasses, kind);
        }
      }
    }
    if (JsonTypeResolver.readerKind(kind)) {
      addCreatorKeyParts(resolver, owner.creatorInfo(), keyParts, referencedClasses, kind);
      JsonCreatorInfo creator = owner.creatorInfo();
      if (creator == null) {
        addReadFields(resolver, owner, owner.readFields(), keyParts, referencedClasses, kind);
      } else {
        addCreatorFields(resolver, owner, creator.fields(), keyParts, referencedClasses, kind);
      }
      if (unwrapped != null) {
        for (JsonUnwrappedInfo.ReadRoute route : unwrapped.readRoutes()) {
          keyParts.add("route");
          keyParts.add(route.group().readIndex());
          if (route.field() != null) {
            addReadField(resolver, owner, route.field(), keyParts, referencedClasses, kind);
          } else {
            addCreatorField(
                resolver, owner, route.creatorField(), keyParts, referencedClasses, kind);
          }
        }
      }
    } else {
      if (unwrapped != null) {
        addUnwrappedWriteOrder(unwrapped.writeEntries(), keyParts, referencedClasses);
      }
      JsonFieldInfo[] fields = unwrapped == null ? owner.writeFields() : unwrapped.writeFields();
      for (JsonFieldInfo field : fields) {
        addWriteField(resolver, owner, field, keyParts, referencedClasses, kind);
      }
    }
    addAnyKeyParts(resolver, owner, keyParts, referencedClasses, kind);
    return GeneratedCodecKey.object(
        typeInfo.rawType(),
        role(kind),
        keyParts.toArray(),
        referencedClasses.toArray(new Class<?>[0]));
  }

  static GeneratedCodecKey collection(
      JsonTypeInfo typeInfo, CollectionCodec<?> owner, JsonTypeResolver.CapabilityKind kind) {
    Type type = typeInfo.type();
    Class<?> rawType = CodecUtils.rawType(type, Collection.class);
    Class<?> elementType = CodecUtils.rawType(CodecUtils.elementType(type), Object.class);
    return GeneratedCodecKey.collection(
        rawType,
        elementType,
        kind == JsonTypeResolver.CapabilityKind.UTF8_WRITER
            ? Role.UTF8_COLLECTION_WRITER
            : Role.UTF8_COLLECTION_READER,
        owner instanceof CollectionCodec.StringCollectionCodec);
  }

  private static void addUnwrappedWriteOrder(
      JsonUnwrappedInfo.WriteEntry[] entries,
      ArrayList<Object> keyParts,
      ArrayList<Class<?>> referencedClasses) {
    keyParts.add(entries.length);
    for (JsonUnwrappedInfo.WriteEntry entry : entries) {
      keyParts.add(entry.kind());
      if (entry.kind() == JsonUnwrappedInfo.DIRECT) {
        JsonFieldInfo field = entry.field();
        keyParts.add(field.name());
        addMember(field.writeField(), keyParts, referencedClasses);
        addMember(field.writeGetter(), keyParts, referencedClasses);
      } else if (entry.kind() == JsonUnwrappedInfo.GROUP) {
        keyParts.add(entry.group().readIndex());
        addUnwrappedWriteOrder(entry.group().writeEntries(), keyParts, referencedClasses);
      }
    }
  }

  private static void addMixinKeyParts(
      JsonSharedRegistry registry,
      Class<?> target,
      ArrayList<Object> keyParts,
      ArrayList<Class<?>> referencedClasses) {
    Class<?> mixin = registry.mixinType(target);
    keyParts.add(mixin);
    if (mixin != null) {
      referencedClasses.add(mixin);
    }
  }

  private static void addCreatorKeyParts(
      JsonTypeResolver resolver,
      JsonCreatorInfo creator,
      ArrayList<Object> keyParts,
      ArrayList<Class<?>> referencedClasses,
      JsonTypeResolver.CapabilityKind kind) {
    if (creator == null) {
      keyParts.add(null);
      return;
    }
    keyParts.add("creator");
    addMember(creator.executable(), keyParts, referencedClasses);
    addMember(creator.invocationExecutable(), keyParts, referencedClasses);
    addMember(creator.defaultConstructor(), keyParts, referencedClasses);
    keyParts.add(creator.argumentCount());
    keyParts.add(creator.defaultMaskCount());
    keyParts.add(creator.tracksArgumentPresence());
    for (int i = 0; i < creator.argumentCount(); i++) {
      keyParts.add(creator.defaultMaskBit(i));
      keyParts.add(creator.hasDefault(i));
      addMember(creator.defaultMethod(i), keyParts, referencedClasses);
    }
    JsonFieldInfo[] deferred = creator.deferredFields();
    keyParts.add(deferred.length);
    for (int i = 0; i < deferred.length; i++) {
      keyParts.add(creator.deferredRequired(i));
      addReadField(resolver, null, deferred[i], keyParts, referencedClasses, kind);
    }
  }

  private static void addWriteField(
      JsonTypeResolver resolver,
      ObjectCodec<?> owner,
      JsonFieldInfo field,
      ArrayList<Object> keyParts,
      ArrayList<Class<?>> referencedClasses,
      JsonTypeResolver.CapabilityKind kind) {
    JsonTypeInfo child = field.writeTypeInfo();
    keyParts.add("write");
    keyParts.add(field.name());
    keyParts.add(field.writeRawType());
    keyParts.add(child.rawType());
    keyParts.add(field.writeKind());
    keyParts.add(field.writeNull());
    keyParts.add(field.requiresNonNullWrite());
    keyParts.add(field.writesRawString());
    keyParts.add(field.writesUnboxedValue());
    keyParts.add(resolver.usesWriterSlot(owner, child));
    addMember(field.writeField(), keyParts, referencedClasses);
    addMember(field.writeGetter(), keyParts, referencedClasses);
    addCapabilityKeyParts(resolver, child, kind, keyParts, referencedClasses);
    addUnboxedKeyParts(field.writeUnboxedValueCodec(), false, keyParts, referencedClasses);
    addClass(field.writeRawType(), referencedClasses);
    addClass(child.rawType(), referencedClasses);
  }

  private static void addReadFields(
      JsonTypeResolver resolver,
      ObjectCodec<?> owner,
      JsonFieldInfo[] fields,
      ArrayList<Object> keyParts,
      ArrayList<Class<?>> referencedClasses,
      JsonTypeResolver.CapabilityKind kind) {
    for (JsonFieldInfo field : fields) {
      addReadField(resolver, owner, field, keyParts, referencedClasses, kind);
    }
  }

  private static void addReadField(
      JsonTypeResolver resolver,
      ObjectCodec<?> owner,
      JsonFieldInfo field,
      ArrayList<Object> keyParts,
      ArrayList<Class<?>> referencedClasses,
      JsonTypeResolver.CapabilityKind kind) {
    JsonTypeInfo child = field.readTypeInfo();
    keyParts.add("read");
    keyParts.add(field.name());
    keyParts.add(field.readRawType());
    keyParts.add(child.rawType());
    keyParts.add(field.readKind());
    keyParts.add(field.readIndex());
    keyParts.add(field.hasOccurrenceNullability());
    keyParts.add(field.occurrenceNullable());
    keyParts.add(field.occurrenceWrapsNull());
    keyParts.add(field.readsUnboxedValue());
    keyParts.add(owner != null && resolver.usesReaderSlot(owner, child));
    addMember(field.readField(), keyParts, referencedClasses);
    addMember(field.readSetter(), keyParts, referencedClasses);
    addCapabilityKeyParts(resolver, child, kind, keyParts, referencedClasses);
    addUnboxedKeyParts(field.readUnboxedValueCodec(), true, keyParts, referencedClasses);
    addClass(field.readRawType(), referencedClasses);
    addClass(child.rawType(), referencedClasses);
  }

  private static void addCreatorFields(
      JsonTypeResolver resolver,
      ObjectCodec<?> owner,
      JsonCreatorFieldInfo[] fields,
      ArrayList<Object> keyParts,
      ArrayList<Class<?>> referencedClasses,
      JsonTypeResolver.CapabilityKind kind) {
    for (JsonCreatorFieldInfo field : fields) {
      addCreatorField(resolver, owner, field, keyParts, referencedClasses, kind);
    }
  }

  private static void addCreatorField(
      JsonTypeResolver resolver,
      ObjectCodec<?> owner,
      JsonCreatorFieldInfo field,
      ArrayList<Object> keyParts,
      ArrayList<Class<?>> referencedClasses,
      JsonTypeResolver.CapabilityKind kind) {
    JsonTypeInfo child = field.typeInfo();
    keyParts.add("argument");
    keyParts.add(field.name());
    keyParts.add(field.argumentIndex());
    keyParts.add(field.rawType());
    keyParts.add(child.rawType());
    keyParts.add(child.kind());
    keyParts.add(child.nullable());
    keyParts.add(child.rejectsNull());
    keyParts.add(field.materializesNullCarrier());
    keyParts.add(owner != null && resolver.usesReaderSlot(owner, child));
    addCapabilityKeyParts(resolver, child, kind, keyParts, referencedClasses);
    addUnboxedKeyParts(field.unboxedValueCodec(), true, keyParts, referencedClasses);
    addClass(field.rawType(), referencedClasses);
    addClass(child.rawType(), referencedClasses);
  }

  private static void addAnyKeyParts(
      JsonTypeResolver resolver,
      ObjectCodec<?> owner,
      ArrayList<Object> keyParts,
      ArrayList<Class<?>> referencedClasses,
      JsonTypeResolver.CapabilityKind kind) {
    AnyInfo any = owner.anyInfo();
    boolean active =
        any != null
            && (JsonTypeResolver.readerKind(kind)
                ? any.readField() != null || any.readSetter() != null
                : any.writeField() != null || any.writeGetter() != null);
    if (!active) {
      keyParts.add(null);
      return;
    }
    keyParts.add("any");
    keyParts.add(any.valueRawType());
    keyParts.add(any.writeIndex());
    keyParts.add(any.constructionIndex());
    boolean storesCodec = resolver.storesAnyCodec(owner, any);
    keyParts.add(storesCodec);
    keyParts.add(
        storesCodec
            && (JsonTypeResolver.readerKind(kind)
                ? resolver.usesReaderSlot(owner, any.valueTypeInfo())
                : resolver.usesWriterSlot(owner, any.valueTypeInfo())));
    if (JsonTypeResolver.readerKind(kind)) {
      addMember(any.readField(), keyParts, referencedClasses);
      addMember(any.readSetter(), keyParts, referencedClasses);
    } else {
      addMember(any.writeField(), keyParts, referencedClasses);
      addMember(any.writeGetter(), keyParts, referencedClasses);
    }
    addCapabilityKeyParts(resolver, any.valueTypeInfo(), kind, keyParts, referencedClasses);
    addClass(any.valueRawType(), referencedClasses);
  }

  private static void addCapabilityKeyParts(
      JsonTypeResolver resolver,
      JsonTypeInfo typeInfo,
      JsonTypeResolver.CapabilityKind kind,
      ArrayList<Object> keyParts,
      ArrayList<Class<?>> referencedClasses) {
    Object capability = JsonTypeResolver.currentCapability(typeInfo, kind);
    Class<?> capabilityClass =
        resolver.sharedRegistry().canonicalProtectedBuiltin(typeInfo, capability)
            ? null
            : logicalCapabilityClass(resolver, typeInfo, capability);
    keyParts.add(capabilityClass);
    keyParts.add(typeInfo.kind());
    keyParts.add(typeInfo.nullable());
    keyParts.add(typeInfo.rejectsNull());
    keyParts.add(typeInfo.transparentNull());
    addClass(capabilityClass, referencedClasses);
  }

  private static Class<?> logicalCapabilityClass(
      JsonTypeResolver resolver, JsonTypeInfo typeInfo, Object capability) {
    if (resolver.canonicalObjectOwner(typeInfo) != null) {
      return ObjectCodec.class;
    }
    CollectionCodec<?> collection = resolver.collectionCodecOwner(typeInfo);
    if (collection != null) {
      return collection.getClass();
    }
    return capability instanceof ClosedSubtypeCodec
        ? ClosedSubtypeCodec.class
        : capability.getClass();
  }

  private static void addUnboxedKeyParts(
      UnboxedValueCodec codec,
      boolean reader,
      ArrayList<Object> keyParts,
      ArrayList<Class<?>> referencedClasses) {
    if (codec == null) {
      keyParts.add(null);
      return;
    }
    keyParts.add(codec.getClass());
    addClass(codec.getClass(), referencedClasses);
    if (codec instanceof DirectUnboxedValueCodec) {
      DirectUnboxedValueCodec direct = (DirectUnboxedValueCodec) codec;
      addMember(
          reader ? direct.readCarrierMethod() : direct.writeCarrierMethod(),
          keyParts,
          referencedClasses);
      return;
    }
    TransparentUnboxedValueCodec transparent = (TransparentUnboxedValueCodec) codec;
    JsonTypeInfo terminal = transparent.valueTypeInfo();
    keyParts.add(terminal.rawType());
    keyParts.add(terminal.kind());
    addClass(terminal.rawType(), referencedClasses);
    UnboxedValueCodec terminalCodec = terminal.unboxedValueCodec();
    if (terminalCodec instanceof DirectUnboxedValueCodec) {
      DirectUnboxedValueCodec direct = (DirectUnboxedValueCodec) terminalCodec;
      addMember(
          reader ? direct.readCarrierMethod() : direct.writeCarrierMethod(),
          keyParts,
          referencedClasses);
    } else {
      keyParts.add(null);
    }
    Method[] methods = reader ? transparent.constructMethods() : transparent.extractMethods();
    keyParts.add(methods.length);
    for (Method method : methods) {
      addMember(method, keyParts, referencedClasses);
    }
    if (reader) {
      int[] boxes = transparent.constructBoxBytes();
      keyParts.add(boxes.length);
      for (int box : boxes) {
        keyParts.add(box);
      }
    }
  }

  private static Member accessorMember(JsonFieldAccessor accessor) {
    if (accessor == null) {
      return null;
    }
    return accessor.getter() != null ? accessor.getter() : accessor.field();
  }

  private static void addMember(
      Member member, ArrayList<Object> keyParts, ArrayList<Class<?>> referencedClasses) {
    MemberDescriptor descriptor = MemberDescriptor.of(member);
    keyParts.add(descriptor);
    if (member != null) {
      addClass(member.getDeclaringClass(), referencedClasses);
    }
  }

  private static void addClass(Class<?> type, ArrayList<Class<?>> referencedClasses) {
    if (type != null) {
      referencedClasses.add(type);
    }
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
