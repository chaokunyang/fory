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

package org.apache.fory.serializer;

import static org.apache.fory.serializer.SerializationUtils.getTypeResolver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.fory.Fory;
import org.apache.fory.collection.IdentityObjectIntMap;
import org.apache.fory.collection.LongMap;
import org.apache.fory.collection.MapEntry;
import org.apache.fory.logging.Logger;
import org.apache.fory.logging.LoggerFactory;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.meta.ClassDef;
import org.apache.fory.resolver.MetaContext;
import org.apache.fory.resolver.MetaStringResolver;
import org.apache.fory.resolver.RefResolver;
import org.apache.fory.resolver.TypeResolver;
import org.apache.fory.serializer.FieldGroups.SerializationFieldInfo;
import org.apache.fory.serializer.NonexistentClass.NonexistentEnum;
import org.apache.fory.serializer.Serializers.CrossLanguageCompatibleSerializer;
import org.apache.fory.type.Descriptor;
import org.apache.fory.type.DescriptorGrouper;
import org.apache.fory.type.Generics;
import org.apache.fory.util.Preconditions;
import org.apache.fory.util.Utils;

@SuppressWarnings({"rawtypes", "unchecked"})
public final class NonexistentClassSerializers {
  private static final Logger LOG = LoggerFactory.getLogger(NonexistentClassSerializers.class);

  private static final class ClassFieldsInfo {
    private final SerializationFieldInfo[] buildInFields;
    private final SerializationFieldInfo[] otherFields;
    private final SerializationFieldInfo[] containerFields;
    private final int classVersionHash;

    private ClassFieldsInfo(FieldGroups fieldGroups, int classVersionHash) {
      this.buildInFields = fieldGroups.buildInFields;
      this.otherFields = fieldGroups.userTypeFields;
      this.containerFields = fieldGroups.containerFields;
      this.classVersionHash = classVersionHash;
    }
  }

  public static final class NonexistentClassSerializer extends Serializer {
    private final ClassDef classDef;
    private final LongMap<ClassFieldsInfo> fieldsInfoMap;
    private final SerializationBinding binding;

    public NonexistentClassSerializer(Fory fory, ClassDef classDef) {
      super(fory, NonexistentClass.NonexistentMetaShared.class);
      this.classDef = classDef;
      fieldsInfoMap = new LongMap<>();
      binding = SerializationBinding.createBinding(fory);
      Preconditions.checkArgument(fory.getConfig().isMetaShareEnabled());
      if (Utils.DEBUG_OUTPUT_ENABLED && classDef != null) {
        LOG.info("========== NonexistentClassSerializer ClassDef for {} ==========", type.getName());
        LOG.info("ClassDef fieldsInfo count: {}", classDef.getFieldCount());
        for (int i = 0; i < classDef.getFieldsInfo().size(); i++) {
          LOG.info("  [{}] {}", i, classDef.getFieldsInfo().get(i));
        }
      }
    }

    /**
     * Multiple un existed class will correspond to this `NonexistentMetaSharedClass`. When querying
     * classinfo by `class`, it may dispatch to same `NonexistentClassSerializer`, so we can't use
     * `classDef` in this serializer, but use `classDef` in `NonexistentMetaSharedClass` instead.
     *
     * <p>Note: XtypeResolver.writeClassInfo skips writeSharedClassMeta for NonexistentMetaShared,
     * so this method writes the classDef directly without reverting any buffer bytes.
     */
    private void writeClassDef(MemoryBuffer buffer, NonexistentClass.NonexistentMetaShared value) {
      MetaContext metaContext = fory.getSerializationContext().getMetaContext();
      IdentityObjectIntMap classMap = metaContext.classMap;
      int newId = classMap.size;
      // class not exist, use class def id for identity.
      int id = classMap.putOrGet(value.classDef.getId(), newId);
      if (id >= 0) {
        // Reference to previously written type: (index << 1) | 1, LSB=1
        buffer.writeVarUint32((id << 1) | 1);
      } else {
        // New type: index << 1, LSB=0, followed by ClassDef bytes inline
        buffer.writeVarUint32(newId << 1);
        buffer.writeBytes(value.classDef.getEncoded());
      }
    }

    @Override
    public void write(MemoryBuffer buffer, Object v) {
      NonexistentClass.NonexistentMetaShared value = (NonexistentClass.NonexistentMetaShared) v;
      writeClassDef(buffer, value);
      ClassDef classDef = value.classDef;
      ClassFieldsInfo fieldsInfo = getClassFieldsInfo(classDef);
      Fory fory = this.fory;
      RefResolver refResolver = fory.getRefResolver();
      if (fory.checkClassVersion()) {
        buffer.writeInt32(fieldsInfo.classVersionHash);
      }
      // write order: primitive,boxed,final,other,collection,map
      for (SerializationFieldInfo fieldInfo : fieldsInfo.buildInFields) {
        Object fieldValue = value.get(fieldInfo.qualifiedFieldName);
        AbstractObjectSerializer.writeBuildInFieldValue(binding, fieldInfo, buffer, fieldValue);
      }
      Generics generics = fory.getGenerics();
      for (SerializationFieldInfo fieldInfo : fieldsInfo.containerFields) {
        Object fieldValue = value.get(fieldInfo.qualifiedFieldName);
        AbstractObjectSerializer.writeContainerFieldValue(
            binding, refResolver, generics, fieldInfo, buffer, fieldValue);
      }
      for (SerializationFieldInfo fieldInfo : fieldsInfo.otherFields) {
        Object fieldValue = value.get(fieldInfo.qualifiedFieldName);
        binding.writeField(fieldInfo, buffer, fieldValue);
      }
    }

    private ClassFieldsInfo getClassFieldsInfo(ClassDef classDef) {
      ClassFieldsInfo fieldsInfo = fieldsInfoMap.get(classDef.getId());
      TypeResolver resolver = getTypeResolver(fory);
      if (fieldsInfo == null) {
        // Use `NonexistentSkipClass` since it doesn't have any field.
        Collection<Descriptor> descriptors =
            MetaSharedSerializer.consolidateFields(
                resolver, NonexistentClass.NonexistentSkip.class, classDef);
        DescriptorGrouper grouper =
            fory.getClassResolver().createDescriptorGrouper(descriptors, false);
        FieldGroups fieldGroups = FieldGroups.buildFieldInfos(fory, grouper);
        int classVersionHash = 0;
        if (fory.checkClassVersion()) {
          classVersionHash = ObjectSerializer.computeStructHash(fory, grouper);
        }
        fieldsInfo = new ClassFieldsInfo(fieldGroups, classVersionHash);
        fieldsInfoMap.put(classDef.getId(), fieldsInfo);
      }
      return fieldsInfo;
    }

    @Override
    public Object read(MemoryBuffer buffer) {
      NonexistentClass.NonexistentMetaShared obj =
          new NonexistentClass.NonexistentMetaShared(classDef);
      Fory fory = this.fory;
      RefResolver refResolver = fory.getRefResolver();
      refResolver.reference(obj);
      List<MapEntry> entries = new ArrayList<>();
      // read order: primitive,boxed,final,other,collection,map
      ClassFieldsInfo allFieldsInfo = getClassFieldsInfo(classDef);
      for (SerializationFieldInfo fieldInfo : allFieldsInfo.buildInFields) {
        Object fieldValue =
            AbstractObjectSerializer.readBuildInFieldValue(binding, fieldInfo, buffer);
        entries.add(new MapEntry(fieldInfo.qualifiedFieldName, fieldValue));
      }
      Generics generics = fory.getGenerics();
      for (SerializationFieldInfo fieldInfo : allFieldsInfo.containerFields) {
        Object fieldValue =
            AbstractObjectSerializer.readContainerFieldValue(binding, generics, fieldInfo, buffer);
        entries.add(new MapEntry(fieldInfo.qualifiedFieldName, fieldValue));
      }
      for (SerializationFieldInfo fieldInfo : allFieldsInfo.otherFields) {
        Object fieldValue = binding.readField(fieldInfo, buffer);
        entries.add(new MapEntry(fieldInfo.qualifiedFieldName, fieldValue));
      }
      obj.setEntries(entries);
      return obj;
    }

    @Override
    public void xwrite(MemoryBuffer buffer, Object value) {
      write(buffer, value);
    }

    @Override
    public Object xread(MemoryBuffer buffer) {
      return read(buffer);
    }
  }

  public static final class NonexistentEnumClassSerializer
      extends CrossLanguageCompatibleSerializer {
    private final NonexistentEnum[] enumConstants;
    private final MetaStringResolver metaStringResolver;

    public NonexistentEnumClassSerializer(Fory fory) {
      super(fory, NonexistentEnum.class);
      metaStringResolver = fory.getMetaStringResolver();
      enumConstants = NonexistentEnum.class.getEnumConstants();
    }

    @Override
    public void write(MemoryBuffer buffer, Object value) {
      NonexistentEnum enumValue = (NonexistentEnum) value;
      buffer.writeVarUint32Small7(enumValue.ordinal());
    }

    @Override
    public Object read(MemoryBuffer buffer) {
      if (fory.getConfig().serializeEnumByName()) {
        metaStringResolver.readMetaStringBytes(buffer);
        return NonexistentEnum.UNKNOWN;
      }

      int ordinal = buffer.readVarUint32Small7();
      if (ordinal >= enumConstants.length) {
        return NonexistentEnum.UNKNOWN;
      }
      return enumConstants[ordinal];
    }
  }

  public static Serializer getSerializer(Fory fory, String className, Class<?> cls) {
    if (cls.isArray()) {
      return new ArraySerializers.NonexistentArrayClassSerializer(fory, className, cls);
    } else {
      if (cls.isEnum()) {
        return new NonexistentEnumClassSerializer(fory);
      } else {
        if (fory.getConfig().isMetaShareEnabled()) {
          throw new IllegalStateException(
              String.format(
                  "Serializer of class %s should be set in ClassResolver#getMetaSharedClassInfo",
                  className));
        } else {
          return new ObjectSerializer(fory, cls);
        }
      }
    }
  }
}
