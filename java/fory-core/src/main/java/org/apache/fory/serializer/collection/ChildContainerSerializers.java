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

package org.apache.fory.serializer.collection;

import static org.apache.fory.collection.Collections.ofHashSet;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.fory.Fory;
import org.apache.fory.config.CompatibleMode;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.reflect.ReflectionUtils;
import org.apache.fory.resolver.ClassResolver;
import org.apache.fory.resolver.FieldResolver;
import org.apache.fory.serializer.AbstractObjectSerializer;
import org.apache.fory.serializer.AbstractObjectSerializer.InternalFieldInfo;
import org.apache.fory.serializer.CompatibleSerializer;
import org.apache.fory.serializer.JavaSerializer;
import org.apache.fory.serializer.ObjectSerializer;
import org.apache.fory.serializer.Serializer;
import org.apache.fory.util.Preconditions;

/**
 * Serializers for subclasses of common JDK container types. Subclasses of {@link ArrayList}/{@link
 * HashMap}/{@link LinkedHashMap}/{@link java.util.TreeMap}/etc have `writeObject`/`readObject`
 * defined, which will use JDK compatible serializers, thus inefficient. Serializers will optimize
 * the serialization for those cases by serializing super classes part separately using existing
 * JIT/interpreter-mode serializers.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class ChildContainerSerializers {

  public static Class<? extends Serializer> getCollectionSerializerClass(Class<?> cls) {
    if (ChildCollectionSerializer.superClasses.contains(cls)) {
      return null;
    }
    if (ClassResolver.useReplaceResolveSerializer(cls)) {
      return null;
    }
    // Collection/Map must have default constructor to be invoked by fory, otherwise created object
    // can't be used to adding elements.
    // For example: `new ArrayList<Integer> { add(1);}`, without default constructor, created
    // list will have elementData as null, adding elements will raise NPE.
    if (!ReflectionUtils.hasNoArgConstructor(cls)) {
      return null;
    }
    while (cls != Object.class) {
      if (ChildCollectionSerializer.superClasses.contains(cls)) {
        if (cls == ArrayList.class) {
          return ChildArrayListSerializer.class;
        } else {
          return ChildCollectionSerializer.class;
        }
      } else {
        if (JavaSerializer.getReadObjectMethod(cls, false) != null
            || JavaSerializer.getWriteObjectMethod(cls, false) != null) {
          return null;
        }
      }
      cls = cls.getSuperclass();
    }
    return null;
  }

  public static Class<? extends Serializer> getMapSerializerClass(Class<?> cls) {
    if (ChildMapSerializer.superClasses.contains(cls)) {
      return null;
    }
    if (ClassResolver.useReplaceResolveSerializer(cls)) {
      return null;
    }
    // Collection/Map must have default constructor to be invoked by fory, otherwise created object
    // can't be used to adding elements.
    // For example: `new ArrayList<Integer> { add(1);}`, without default constructor, created
    // list will have elementData as null, adding elements will raise NPE.
    if (!ReflectionUtils.hasNoArgConstructor(cls)) {
      return null;
    }
    while (cls != Object.class) {
      if (ChildMapSerializer.superClasses.contains(cls)) {
        return ChildMapSerializer.class;
      } else {
        if (JavaSerializer.getReadObjectMethod(cls, false) != null
            || JavaSerializer.getWriteObjectMethod(cls, false) != null) {
          return null;
        }
      }
      cls = cls.getSuperclass();
    }
    return null;
  }

  /**
   * Serializer for subclasses of {@link ChildCollectionSerializer#superClasses} if no jdk custom
   * serialization in those classes.
   */
  public static class ChildCollectionSerializer<T extends Collection>
      extends CollectionSerializer<T> {
    public static Set<Class<?>> superClasses =
        ofHashSet(
            ArrayList.class, LinkedList.class, ArrayDeque.class, Vector.class, HashSet.class
            // PriorityQueue/TreeSet/ConcurrentSkipListSet need comparator as constructor argument
            );
    protected InternalFieldInfo[] fieldInfos;
    protected final Serializer[] slotsSerializers;

    public ChildCollectionSerializer(Fory fory, Class<T> cls) {
      super(fory, cls);
      slotsSerializers = buildSlotsSerializers(fory, superClasses, cls);
    }

    @Override
    public Collection onCollectionWrite(MemoryBuffer buffer, T value) {
      buffer.writeVarUint32Small7(value.size());
      for (Serializer slotsSerializer : slotsSerializers) {
        slotsSerializer.write(buffer, value);
      }
      return value;
    }

    public Collection newCollection(MemoryBuffer buffer) {
      Collection collection = super.newCollection(buffer);
      readAndSetFields(buffer, collection, slotsSerializers);
      return collection;
    }

    @Override
    public Collection newCollection(Collection originCollection) {
      Collection newCollection = super.newCollection(originCollection);
      if (fieldInfos == null) {
        List<Field> fields = ReflectionUtils.getFieldsWithoutSuperClasses(type, superClasses);
        fieldInfos = AbstractObjectSerializer.buildFieldsInfo(fory, fields);
      }
      AbstractObjectSerializer.copyFields(fory, fieldInfos, originCollection, newCollection);
      return newCollection;
    }
  }

  public static final class ChildArrayListSerializer<T extends ArrayList>
      extends ChildCollectionSerializer<T> {
    public ChildArrayListSerializer(Fory fory, Class<T> cls) {
      super(fory, cls);
    }

    @Override
    public T newCollection(MemoryBuffer buffer) {
      T collection = (T) super.newCollection(buffer);
      int numElements = getAndClearNumElements();
      setNumElements(numElements);
      collection.ensureCapacity(numElements);
      return collection;
    }
  }

  /**
   * Serializer for subclasses of {@link ChildMapSerializer#superClasses} if no jdk custom
   * serialization in those classes.
   */
  public static class ChildMapSerializer<T extends Map> extends MapSerializer<T> {
    public static Set<Class<?>> superClasses =
        ofHashSet(
            HashMap.class, LinkedHashMap.class, ConcurrentHashMap.class
            // TreeMap/ConcurrentSkipListMap need comparator as constructor argument
            );
    private final Serializer[] slotsSerializers;
    private InternalFieldInfo[] fieldInfos;

    public ChildMapSerializer(Fory fory, Class<T> cls) {
      super(fory, cls);
      slotsSerializers = buildSlotsSerializers(fory, superClasses, cls);
    }

    @Override
    public Map onMapWrite(MemoryBuffer buffer, T value) {
      buffer.writeVarUint32Small7(value.size());
      for (Serializer slotsSerializer : slotsSerializers) {
        slotsSerializer.write(buffer, value);
      }
      return value;
    }

    @Override
    public Map newMap(MemoryBuffer buffer) {
      Map map = super.newMap(buffer);
      readAndSetFields(buffer, map, slotsSerializers);
      return map;
    }

    @Override
    public Map newMap(Map originMap) {
      Map newMap = super.newMap(originMap);
      if (fieldInfos == null || fieldInfos.length == 0) {
        List<Field> fields = ReflectionUtils.getFieldsWithoutSuperClasses(type, superClasses);
        fieldInfos = AbstractObjectSerializer.buildFieldsInfo(fory, fields);
      }
      AbstractObjectSerializer.copyFields(fory, fieldInfos, originMap, newMap);
      return newMap;
    }
  }

  private static <T> Serializer[] buildSlotsSerializers(
      Fory fory, Set<Class<?>> superClasses, Class<T> cls) {
    Preconditions.checkArgument(!superClasses.contains(cls));
    List<Serializer> serializers = new ArrayList<>();
    while (!superClasses.contains(cls)) {
      Serializer slotsSerializer;
      if (fory.getConfig().getCompatibleMode() == CompatibleMode.COMPATIBLE) {
        slotsSerializer =
            new CompatibleSerializer(fory, cls, FieldResolver.of(fory, cls, false, false));
      } else {
        slotsSerializer = new ObjectSerializer<>(fory, cls, false);
      }
      serializers.add(slotsSerializer);
      cls = (Class<T>) cls.getSuperclass();
    }
    Collections.reverse(serializers);
    return serializers.toArray(new Serializer[0]);
  }

  private static void readAndSetFields(
      MemoryBuffer buffer, Object collection, Serializer[] slotsSerializers) {
    for (Serializer slotsSerializer : slotsSerializers) {
      if (slotsSerializer.getClass() == CompatibleSerializer.class) {
        ((CompatibleSerializer) slotsSerializer).readAndSetFields(buffer, collection);
      } else {
        ((ObjectSerializer) slotsSerializer).readAndSetFields(buffer, collection);
      }
    }
  }
}
