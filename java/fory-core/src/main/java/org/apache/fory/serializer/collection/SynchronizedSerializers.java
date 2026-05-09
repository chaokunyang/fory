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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import org.apache.fory.collection.Tuple2;
import org.apache.fory.context.CopyContext;
import org.apache.fory.context.ReadContext;
import org.apache.fory.context.WriteContext;
import org.apache.fory.logging.Logger;
import org.apache.fory.logging.LoggerFactory;
import org.apache.fory.platform.AndroidSupport;
import org.apache.fory.platform.UnsafeOps;
import org.apache.fory.resolver.TypeInfo;
import org.apache.fory.resolver.TypeResolver;
import org.apache.fory.serializer.Serializer;
import org.apache.fory.util.ExceptionUtils;
import org.apache.fory.util.Preconditions;

/** Serializer for synchronized Collections and Maps created via Collections. */
@SuppressWarnings({"rawtypes", "unchecked"})
public class SynchronizedSerializers {
  private static final Logger LOG = LoggerFactory.getLogger(SynchronizedSerializers.class);

  private static class Offset {
    // Graalvm unsafe offset substitution support: Make the call followed by a field store
    // directly or by a sign extend node followed directly by a field store.
    private static final long SOURCE_COLLECTION_FIELD_OFFSET;
    private static final long SOURCE_MAP_FIELD_OFFSET;

    static {
      String clsName = "java.util.Collections$SynchronizedCollection";
      try {
        SOURCE_COLLECTION_FIELD_OFFSET =
            UnsafeOps.UNSAFE.objectFieldOffset(Class.forName(clsName).getDeclaredField("c"));
      } catch (Exception e) {
        LOG.info("Could not access source collection field in {}", clsName);
        throw new RuntimeException(e);
      }
      clsName = "java.util.Collections$SynchronizedMap";
      try {
        SOURCE_MAP_FIELD_OFFSET =
            UnsafeOps.UNSAFE.objectFieldOffset(Class.forName(clsName).getDeclaredField("m"));
      } catch (Exception e) {
        LOG.info("Could not access source map field in {}", clsName);
        throw new RuntimeException(e);
      }
    }
  }

  public static final class SynchronizedCollectionSerializer
      extends CollectionSerializer<Collection> {

    private final Function factory;
    private final long offset;
    private final TypeInfo sourceTypeInfo;

    public SynchronizedCollectionSerializer(
        TypeResolver typeResolver,
        Class cls,
        Function factory,
        long offset,
        TypeInfo sourceTypeInfo) {
      super(typeResolver, cls, false);
      this.factory = factory;
      this.offset = offset;
      this.sourceTypeInfo = sourceTypeInfo;
    }

    @Override
    public void write(WriteContext writeContext, Collection object) {
      Preconditions.checkArgument(object.getClass() == type);
      if (AndroidSupport.IS_ANDROID) {
        synchronized (object) {
          writeAndroidSourceRef(writeContext, newAndroidWriteSource(object), sourceTypeInfo);
        }
        return;
      }
      // the ordinal could be replaced by s.th. else (e.g. a explicitly managed "id")
      Object unwrapped = UnsafeOps.getObject(object, offset);
      synchronized (object) {
        writeContext.writeRef(unwrapped);
      }
    }

    @Override
    public Collection read(ReadContext readContext) {
      final Object sourceCollection = readContext.readRef();
      return (Collection) factory.apply(sourceCollection);
    }

    @Override
    public Collection copy(CopyContext copyContext, Collection object) {
      if (AndroidSupport.IS_ANDROID) {
        synchronized (object) {
          Collection mutableSource = newAndroidMutableSource(copyContext, object);
          Collection result = (Collection) factory.apply(mutableSource);
          copyContext.reference(object, result);
          copyElements(copyContext, object, mutableSource);
          return result;
        }
      }
      final Object collection = UnsafeOps.getObject(object, offset);
      return (Collection) factory.apply(copyContext.copyObject(collection));
    }

    private Collection newAndroidMutableSource(CopyContext copyContext, Collection value) {
      if (value instanceof SortedSet) {
        Object comparator = copyContext.copyObject(((SortedSet) value).comparator());
        return new TreeSet((java.util.Comparator) comparator);
      } else if (value instanceof Set) {
        return new HashSet(value.size());
      } else {
        return new ArrayList(value.size());
      }
    }

    private Collection newAndroidWriteSource(Collection value) {
      Collection source;
      if (value instanceof SortedSet) {
        source = new TreeSet(((SortedSet) value).comparator());
      } else if (value instanceof Set) {
        source = new HashSet(value.size());
      } else {
        source = new ArrayList(value.size());
      }
      source.addAll(value);
      return source;
    }
  }

  public static final class SynchronizedMapSerializer extends MapSerializer<Map> {
    private final Function factory;
    private final long offset;
    private final TypeInfo sourceTypeInfo;

    public SynchronizedMapSerializer(
        TypeResolver typeResolver,
        Class cls,
        Function factory,
        long offset,
        TypeInfo sourceTypeInfo) {
      super(typeResolver, cls, false);
      this.factory = factory;
      this.offset = offset;
      this.sourceTypeInfo = sourceTypeInfo;
    }

    @Override
    public void write(WriteContext writeContext, Map object) {
      Preconditions.checkArgument(object.getClass() == type);
      if (AndroidSupport.IS_ANDROID) {
        synchronized (object) {
          writeAndroidSourceRef(writeContext, newAndroidWriteSource(object), sourceTypeInfo);
        }
        return;
      }
      // the ordinal could be replaced by s.th. else (e.g. a explicitly managed "id")
      Object unwrapped = UnsafeOps.getObject(object, offset);
      synchronized (object) {
        writeContext.writeRef(unwrapped);
      }
    }

    @Override
    public Map copy(CopyContext copyContext, Map originMap) {
      if (AndroidSupport.IS_ANDROID) {
        synchronized (originMap) {
          Map mutableSource = newAndroidMutableSource(copyContext, originMap);
          Map result = (Map) factory.apply(mutableSource);
          copyContext.reference(originMap, result);
          copyEntry(copyContext, originMap, mutableSource);
          return result;
        }
      }
      final Object unwrappedMap = UnsafeOps.getObject(originMap, offset);
      return (Map) factory.apply(copyContext.copyObject(unwrappedMap));
    }

    @Override
    public Map read(ReadContext readContext) {
      final Object sourceCollection = readContext.readRef();
      return (Map) factory.apply(sourceCollection);
    }

    private Map newAndroidMutableSource(CopyContext copyContext, Map value) {
      if (value instanceof SortedMap) {
        Object comparator = copyContext.copyObject(((SortedMap) value).comparator());
        return new TreeMap((java.util.Comparator) comparator);
      } else {
        return new HashMap(value.size());
      }
    }

    private Map newAndroidWriteSource(Map value) {
      Map source;
      if (value instanceof SortedMap) {
        source = new TreeMap(((SortedMap) value).comparator());
      } else {
        source = new HashMap(value.size());
      }
      source.putAll(value);
      return source;
    }
  }

  static Serializer createSerializer(TypeResolver typeResolver, Class<?> cls) {
    for (Tuple2<Class<?>, Function> factory : synchronizedFactories()) {
      if (factory.f0 == cls) {
        return createSerializer(typeResolver, factory);
      }
    }
    throw new IllegalArgumentException("Unsupported type " + cls);
  }

  private static Serializer<?> createSerializer(
      TypeResolver typeResolver, Tuple2<Class<?>, Function> factory) {
    if (Collection.class.isAssignableFrom(factory.f0)) {
      return new SynchronizedCollectionSerializer(
          typeResolver,
          factory.f0,
          factory.f1,
          AndroidSupport.IS_ANDROID ? -1 : Offset.SOURCE_COLLECTION_FIELD_OFFSET,
          sourceCollectionTypeInfo(typeResolver, factory.f0));
    } else {
      return new SynchronizedMapSerializer(
          typeResolver,
          factory.f0,
          factory.f1,
          AndroidSupport.IS_ANDROID ? -1 : Offset.SOURCE_MAP_FIELD_OFFSET,
          sourceMapTypeInfo(typeResolver, factory.f0));
    }
  }

  private static void writeAndroidSourceRef(
      WriteContext writeContext, Object value, TypeInfo sourceTypeInfo) {
    writeContext.writeRef(value, sourceTypeInfo);
  }

  private static TypeInfo sourceCollectionTypeInfo(TypeResolver typeResolver, Class<?> cls) {
    if (SortedSet.class.isAssignableFrom(cls)) {
      return typeResolver.getTypeInfo(TreeSet.class);
    } else if (Set.class.isAssignableFrom(cls)) {
      return typeResolver.getTypeInfo(HashSet.class);
    } else {
      return typeResolver.getTypeInfo(ArrayList.class);
    }
  }

  private static TypeInfo sourceMapTypeInfo(TypeResolver typeResolver, Class<?> cls) {
    if (SortedMap.class.isAssignableFrom(cls)) {
      return typeResolver.getTypeInfo(TreeMap.class);
    } else {
      return typeResolver.getTypeInfo(HashMap.class);
    }
  }

  static Tuple2<Class<?>, Function>[] synchronizedFactories() {
    Tuple2<Class<?>, Function> collectionFactory =
        Tuple2.of(
            Collections.synchronizedCollection(Arrays.asList("")).getClass(),
            o -> Collections.synchronizedCollection((Collection) o));
    Tuple2<Class<?>, Function> randomListFactory =
        Tuple2.of(
            Collections.synchronizedList(new ArrayList<Void>()).getClass(),
            o -> Collections.synchronizedList((List<?>) o));
    Tuple2<Class<?>, Function> listFactory =
        Tuple2.of(
            Collections.synchronizedList(new LinkedList<Void>()).getClass(),
            o -> Collections.synchronizedList((List<?>) o));
    Tuple2<Class<?>, Function> setFactory =
        Tuple2.of(
            Collections.synchronizedSet(new HashSet<Void>()).getClass(),
            o -> Collections.synchronizedSet((Set<?>) o));
    Tuple2<Class<?>, Function> sortedsetFactory =
        Tuple2.of(
            Collections.synchronizedSortedSet(new TreeSet<>()).getClass(),
            o -> Collections.synchronizedSortedSet((TreeSet<?>) o));
    Tuple2<Class<?>, Function> mapFactory =
        Tuple2.of(
            Collections.synchronizedMap(new HashMap<Void, Void>()).getClass(),
            o -> Collections.synchronizedMap((Map) o));
    Tuple2<Class<?>, Function> sortedmapFactory =
        Tuple2.of(
            Collections.synchronizedSortedMap(new TreeMap<>()).getClass(),
            o -> Collections.synchronizedSortedMap((SortedMap) o));
    return new Tuple2[] {
      collectionFactory,
      randomListFactory,
      listFactory,
      setFactory,
      sortedsetFactory,
      mapFactory,
      sortedmapFactory
    };
  }

  /**
   * Registering serializers for synchronized Collections and Maps created via {@link Collections}.
   *
   * @see Collections#synchronizedCollection(Collection)
   * @see Collections#synchronizedList(List)
   * @see Collections#synchronizedSet(Set)
   * @see Collections#synchronizedSortedSet(SortedSet)
   * @see Collections#synchronizedMap(Map)
   * @see Collections#synchronizedSortedMap(SortedMap)
   */
  public static void registerSerializers(TypeResolver resolver) {
    try {
      for (Tuple2<Class<?>, Function> factory : synchronizedFactories()) {
        resolver.registerInternalSerializer(factory.f0, createSerializer(resolver, factory));
      }
    } catch (Throwable e) {
      ExceptionUtils.ignore(e);
    }
  }
}
