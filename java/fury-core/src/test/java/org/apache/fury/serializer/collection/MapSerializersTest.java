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

package org.apache.fury.serializer.collection;

import static com.google.common.collect.ImmutableList.of;
import static org.apache.fury.TestUtils.mapOf;
import static org.apache.fury.collection.Collections.ofArrayList;
import static org.apache.fury.collection.Collections.ofHashMap;
import static org.testng.Assert.assertEquals;

import com.google.common.collect.ImmutableMap;
import java.io.Serializable;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.fury.Fury;
import org.apache.fury.FuryTestBase;
import org.apache.fury.collection.LazyMap;
import org.apache.fury.collection.MapEntry;
import org.apache.fury.config.CompatibleMode;
import org.apache.fury.config.Language;
import org.apache.fury.reflect.TypeRef;
import org.apache.fury.serializer.Serializer;
import org.apache.fury.serializer.collection.CollectionSerializersTest.TestEnum;
import org.apache.fury.test.bean.BeanB;
import org.apache.fury.test.bean.Cyclic;
import org.apache.fury.test.bean.MapFields;
import org.apache.fury.type.GenericType;
import org.testng.Assert;
import org.testng.annotations.Test;

public class MapSerializersTest extends FuryTestBase {

  @Test(dataProvider = "basicMultiConfigFury")
  public void basicTestCaseWithMultiConfig(
      boolean trackingRef,
      boolean codeGen,
      boolean scopedMetaShare,
      CompatibleMode compatibleMode) {
    Fury fury =
        Fury.builder()
            .withLanguage(Language.JAVA)
            .withRefTracking(trackingRef)
            .requireClassRegistration(false)
            .withCodegen(codeGen)
            .withCompatibleMode(compatibleMode)
            .withScopedMetaShare(scopedMetaShare)
            .build();

    // testBasicMap
    Map<String, Integer> data = new HashMap<>(ImmutableMap.of("a", 1, "b", 2));
    serDeCheckSerializer(fury, data, "HashMap");
    serDeCheckSerializer(fury, new LinkedHashMap<>(data), "LinkedHashMap");

    // testBasicMapNested
    Map<String, Integer> data0 = new HashMap<>(ImmutableMap.of("a", 1, "b", 2));
    Map<String, Map<String, Integer>> nestedMap = ofHashMap("k1", data0, "k2", data0);
    serDeCheckSerializer(fury, nestedMap, "HashMap");
    serDeCheckSerializer(fury, new LinkedHashMap<>(nestedMap), "LinkedHashMap");

    // testMapGenerics
    byte[] bytes1 = fury.serialize(data);
    fury.getGenerics().pushGenericType(GenericType.build(new TypeRef<Map<String, Integer>>() {}));
    byte[] bytes2 = fury.serialize(data);
    Assert.assertTrue(bytes1.length > bytes2.length);
    fury.getGenerics().popGenericType();
    Assert.assertThrows(RuntimeException.class, () -> fury.deserialize(bytes2));

    // testSortedMap
    Map<String, Integer> treeMap = new TreeMap<>(ImmutableMap.of("a", 1, "b", 2));
    serDeCheckSerializer(fury, treeMap, "SortedMap");
    byte[] sortMapBytes1 = fury.serialize(treeMap);
    fury.getGenerics().pushGenericType(GenericType.build(new TypeRef<Map<String, Integer>>() {}));
    byte[] sortMapBytes2 = fury.serialize(treeMap);
    Assert.assertTrue(sortMapBytes1.length > sortMapBytes2.length);
    fury.getGenerics().popGenericType();
    Assert.assertThrows(RuntimeException.class, () -> fury.deserialize(sortMapBytes2));

    // testTreeMap
    TreeMap<String, String> map =
        new TreeMap<>(
            (Comparator<? super String> & Serializable)
                (s1, s2) -> {
                  int delta = s1.length() - s2.length();
                  if (delta == 0) {
                    return s1.compareTo(s2);
                  } else {
                    return delta;
                  }
                });
    map.put("str1", "1");
    map.put("str2", "1");
    assertEquals(map, serDe(fury, map));
    BeanForMap beanForMap = new BeanForMap();
    assertEquals(beanForMap, serDe(fury, beanForMap));

    // testEmptyMap
    serDeCheckSerializer(fury, Collections.EMPTY_MAP, "EmptyMapSerializer");
    serDeCheckSerializer(fury, Collections.emptySortedMap(), "EmptySortedMap");

    // testSingletonMap
    serDeCheckSerializer(fury, Collections.singletonMap("k", 1), "SingletonMap");

    // testConcurrentMap
    serDeCheckSerializer(fury, new ConcurrentHashMap<>(data), "ConcurrentHashMap");
    serDeCheckSerializer(fury, new ConcurrentSkipListMap<>(data), "ConcurrentSkipListMap");

    // testEnumMap
    EnumMap<TestEnum, Object> enumMap = new EnumMap<>(TestEnum.class);
    enumMap.put(TestEnum.A, 1);
    enumMap.put(TestEnum.B, "str");
    serDe(fury, enumMap);
    Assert.assertEquals(
        fury.getClassResolver().getSerializerClass(enumMap.getClass()),
        MapSerializers.EnumMapSerializer.class);

    // testNoArgConstructor
    Map<String, Integer> map1 = newInnerMap();
    Assert.assertEquals(jdkDeserialize(jdkSerialize(map1)), map1);
    serDeCheck(fury, map1);

    // testMapFieldSerializers
    MapFields obj = createMapFieldsObject();
    Assert.assertEquals(serDe(fury, obj), obj);

    // testBigMapFieldSerializers
    final MapFields mapFieldsObject = createBigMapFieldsObject();
    serDeCheck(fury, mapFieldsObject);

    // testObjectKeyValueChunk
    final Map<Object, Object> differentKeyAndValueTypeMap = createDifferentKeyAndValueTypeMap();
    final Serializer<? extends Map> serializer =
        fury.getSerializer(differentKeyAndValueTypeMap.getClass());
    MapSerializers.HashMapSerializer mapSerializer = (MapSerializers.HashMapSerializer) serializer;
    serDeCheck(fury, differentKeyAndValueTypeMap);

    // testObjectKeyValueBigChunk
    for (int i = 0; i < 3000; i++) {
      differentKeyAndValueTypeMap.put("k" + i, i);
    }
    serDeCheck(fury, differentKeyAndValueTypeMap);

    // testMapChunkRefTracking
    Map<String, Integer> map2 = new HashMap<>();
    for (int i = 0; i < 1; i++) {
      map2.put("k" + i, i);
    }
    Object v = ofArrayList(map2, ofHashMap("k1", map2, "k2", new HashMap<>(map2), "k3", map2));
    serDeCheck(fury, v);

    // testMapChunkRefTrackingGenerics
    MapFields obj1 = new MapFields();
    Map<String, Integer> map3 = new HashMap<>();
    for (int i = 0; i < 1; i++) {
      map3.put("k" + i, i);
    }
    obj.map = map3;
    obj.mapKeyFinal = ofHashMap("k1", map3);
    serDeCheck(fury, obj1);
  }

  @Test(dataProvider = "referenceTrackingConfig")
  public void testBasicMap(boolean referenceTrackingConfig) {
    Fury fury =
        Fury.builder()
            .withLanguage(Language.JAVA)
            .withRefTracking(referenceTrackingConfig)
            .requireClassRegistration(false)
            .build();
    Map<String, Integer> data = new HashMap<>(ImmutableMap.of("a", 1, "b", 2));
    serDeCheckSerializer(fury, data, "HashMap");
    serDeCheckSerializer(fury, new LinkedHashMap<>(data), "LinkedHashMap");
  }

  @Test(dataProvider = "furyCopyConfig")
  public void testBasicMap(Fury fury) {
    Map<String, Object> data =
        new HashMap<>(ImmutableMap.of("a", 1, "b", 2, "c", Cyclic.create(true)));
    copyCheck(fury, data);
    copyCheck(fury, new LinkedHashMap<>(data));
    copyCheck(fury, new LazyMap<>(new ArrayList<>(data.entrySet())));

    Map<Object, Object> cycMap = new HashMap<>();
    cycMap.put(cycMap, cycMap);
    Map<Object, Object> copy = fury.copy(cycMap);
    copy.forEach(
        (k, v) -> {
          Assert.assertSame(k, copy);
          Assert.assertSame(v, copy);
          Assert.assertSame(k, v);
        });
  }

  @Test(dataProvider = "referenceTrackingConfig")
  public void testBasicMapNested(boolean referenceTrackingConfig) {
    Fury fury =
        Fury.builder()
            .withLanguage(Language.JAVA)
            .withRefTracking(referenceTrackingConfig)
            .requireClassRegistration(false)
            .build();
    Map<String, Integer> data0 = new HashMap<>(ImmutableMap.of("a", 1, "b", 2));
    Map<String, Map<String, Integer>> data = ofHashMap("k1", data0, "k2", data0);
    serDeCheckSerializer(fury, data, "HashMap");
    serDeCheckSerializer(fury, new LinkedHashMap<>(data), "LinkedHashMap");
  }

  @Test(dataProvider = "furyCopyConfig")
  public void testBasicMapNested(Fury fury) {
    Map<String, Integer> data0 = new HashMap<>(ImmutableMap.of("a", 1, "b", 2));
    Map<String, Map<String, Integer>> data = ofHashMap("k1", data0, "k2", data0);
    copyCheck(fury, data);
    copyCheck(fury, new LinkedHashMap<>(data));
  }

  @Test(dataProvider = "referenceTrackingConfig")
  public void testMapGenerics(boolean referenceTrackingConfig) {
    Fury fury =
        Fury.builder()
            .withLanguage(Language.JAVA)
            .withRefTracking(referenceTrackingConfig)
            .requireClassRegistration(false)
            .build();
    Map<String, Integer> data = new HashMap<>(ImmutableMap.of("a", 1, "b", 2));
    byte[] bytes1 = fury.serialize(data);
    fury.getGenerics().pushGenericType(GenericType.build(new TypeRef<Map<String, Integer>>() {}));
    byte[] bytes2 = fury.serialize(data);
    Assert.assertTrue(bytes1.length > bytes2.length);
    fury.getGenerics().popGenericType();
    Assert.assertThrows(RuntimeException.class, () -> fury.deserialize(bytes2));
  }

  @Test(dataProvider = "referenceTrackingConfig")
  public void testSortedMap(boolean referenceTrackingConfig) {
    Fury fury =
        Fury.builder()
            .withLanguage(Language.JAVA)
            .withRefTracking(referenceTrackingConfig)
            .requireClassRegistration(false)
            .build();
    Map<String, Integer> data = new TreeMap<>(ImmutableMap.of("a", 1, "b", 2));
    serDeCheckSerializer(fury, data, "SortedMap");
    byte[] bytes1 = fury.serialize(data);
    fury.getGenerics().pushGenericType(GenericType.build(new TypeRef<Map<String, Integer>>() {}));
    byte[] bytes2 = fury.serialize(data);
    Assert.assertTrue(bytes1.length > bytes2.length);
    fury.getGenerics().popGenericType();
    Assert.assertThrows(RuntimeException.class, () -> fury.deserialize(bytes2));
  }

  @Test(dataProvider = "furyCopyConfig")
  public void testSortedMap(Fury fury) {
    Map<String, Integer> data = new TreeMap<>(ImmutableMap.of("a", 1, "b", 2));
    copyCheck(fury, data);
  }

  @Data
  public static class BeanForMap {
    public Map<String, String> map = new TreeMap<>();

    {
      map.put("k1", "v1");
      map.put("k2", "v2");
    }
  }

  @Test
  public void testTreeMap() {
    boolean referenceTracking = true;
    Fury fury =
        Fury.builder()
            .withLanguage(Language.JAVA)
            .withRefTracking(referenceTracking)
            .requireClassRegistration(false)
            .build();
    TreeMap<String, String> map =
        new TreeMap<>(
            (Comparator<? super String> & Serializable)
                (s1, s2) -> {
                  int delta = s1.length() - s2.length();
                  if (delta == 0) {
                    return s1.compareTo(s2);
                  } else {
                    return delta;
                  }
                });
    map.put("str1", "1");
    map.put("str2", "1");
    assertEquals(map, serDe(fury, map));
    BeanForMap beanForMap = new BeanForMap();
    assertEquals(beanForMap, serDe(fury, beanForMap));
  }

  @Test(dataProvider = "furyCopyConfig")
  public void testTreeMap(Fury fury) {
    TreeMap<String, String> map =
        new TreeMap<>(
            (Comparator<? super String> & Serializable)
                (s1, s2) -> {
                  int delta = s1.length() - s2.length();
                  if (delta == 0) {
                    return s1.compareTo(s2);
                  } else {
                    return delta;
                  }
                });
    map.put("str1", "1");
    map.put("str2", "1");
    copyCheck(fury, map);
    BeanForMap beanForMap = new BeanForMap();
    copyCheck(fury, beanForMap);
  }

  @Test
  public void testEmptyMap() {
    serDeCheckSerializer(getJavaFury(), Collections.EMPTY_MAP, "EmptyMapSerializer");
    serDeCheckSerializer(getJavaFury(), Collections.emptySortedMap(), "EmptySortedMap");
  }

  @Test(dataProvider = "furyCopyConfig")
  public void testEmptyMap(Fury fury) {
    copyCheckWithoutSame(fury, Collections.EMPTY_MAP);
    copyCheckWithoutSame(fury, Collections.emptySortedMap());
  }

  @Test
  public void testSingleMap() {
    serDeCheckSerializer(getJavaFury(), Collections.singletonMap("k", 1), "SingletonMap");
  }

  @Test(dataProvider = "furyCopyConfig")
  public void testSingleMap(Fury fury) {
    copyCheck(fury, Collections.singletonMap("k", 1));
  }

  @Test
  public void testConcurrentMap() {
    Map<String, Integer> data = new TreeMap<>(ImmutableMap.of("a", 1, "b", 2));
    serDeCheckSerializer(getJavaFury(), new ConcurrentHashMap<>(data), "ConcurrentHashMap");
    serDeCheckSerializer(getJavaFury(), new ConcurrentSkipListMap<>(data), "ConcurrentSkipListMap");
  }

  @Test(dataProvider = "furyCopyConfig")
  public void testConcurrentMap(Fury fury) {
    Map<String, Integer> data = new TreeMap<>(ImmutableMap.of("a", 1, "b", 2));
    copyCheck(fury, new ConcurrentHashMap<>(data));
    copyCheck(fury, new ConcurrentSkipListMap<>(data));
  }

  @Test
  public void testEnumMap() {
    EnumMap<TestEnum, Object> enumMap = new EnumMap<>(TestEnum.class);
    enumMap.put(TestEnum.A, 1);
    enumMap.put(TestEnum.B, "str");
    serDe(getJavaFury(), enumMap);
    Assert.assertEquals(
        getJavaFury().getClassResolver().getSerializerClass(enumMap.getClass()),
        MapSerializers.EnumMapSerializer.class);
  }

  @Test(dataProvider = "furyCopyConfig")
  public void testEnumMap(Fury fury) {
    EnumMap<TestEnum, Object> enumMap = new EnumMap<>(TestEnum.class);
    enumMap.put(TestEnum.A, 1);
    enumMap.put(TestEnum.B, "str");
    copyCheck(fury, enumMap);
    Assert.assertEquals(
        getJavaFury().getClassResolver().getSerializerClass(enumMap.getClass()),
        MapSerializers.EnumMapSerializer.class);
  }

  private static Map<String, Integer> newInnerMap() {
    return new HashMap<String, Integer>() {
      {
        put("k1", 1);
        put("k2", 2);
      }
    };
  }

  @Test
  public void testNoArgConstructor() {
    Fury fury = Fury.builder().withLanguage(Language.JAVA).requireClassRegistration(false).build();
    Map<String, Integer> map = newInnerMap();
    Assert.assertEquals(jdkDeserialize(jdkSerialize(map)), map);
    serDeCheck(fury, map);
  }

  @Test(dataProvider = "furyCopyConfig")
  public void testNoArgConstructor(Fury fury) {
    Map<String, Integer> map = newInnerMap();
    copyCheck(fury, map);
  }

  @Test
  public void testMapNoJIT() {
    Fury fury = Fury.builder().withLanguage(Language.JAVA).withCodegen(false).build();
    serDeCheck(fury, new HashMap<>(ImmutableMap.of("a", 1, "b", 2)));
    serDeCheck(fury, new HashMap<>(ImmutableMap.of("a", "v1", "b", "v2")));
    serDeCheck(fury, new HashMap<>(ImmutableMap.of(1, 2, 3, 4)));
  }

  @Test(dataProvider = "javaFury")
  public void testMapFieldSerializers(Fury fury) {
    MapFields obj = createMapFieldsObject();
    Assert.assertEquals(serDe(fury, obj), obj);
  }

  @Test(dataProvider = "furyCopyConfig")
  public void testMapFieldSerializersCopy(Fury fury) {
    MapFields obj = createMapFieldsObject();
    copyCheck(fury, obj);
  }

  @Test(dataProvider = "javaFuryKVCompatible")
  public void testMapFieldsKVCompatible(Fury fury) {
    MapFields obj = createMapFieldsObject();
    Assert.assertEquals(serDe(fury, obj), obj);
  }

  @Test(dataProvider = "furyCopyConfig")
  public void testMapFieldsKVCompatibleCopy(Fury fury) {
    MapFields obj = createMapFieldsObject();
    copyCheck(fury, obj);
  }

  public static MapFields createBigMapFieldsObject() {
    Map<String, Integer> map = new HashMap<>();
    for (int i = 0; i < 1000; i++) {
      map.put("k" + i, i);
    }
    return createMapFieldsObject(map);
  }

  public static MapFields createMapFieldsObject() {
    return createMapFieldsObject(ImmutableMap.of("k1", 1, "k2", 2));
  }

  public static MapFields createMapFieldsObject(Map<String, Integer> map) {
    MapFields obj = new MapFields();
    obj.map = map;
    obj.map2 = new HashMap<>(map);
    obj.map3 = new HashMap<>(map);
    obj.mapKeyFinal = new HashMap<>(ImmutableMap.of("k1", map, "k2", new HashMap<>(map)));
    obj.mapValueFinal = new HashMap<>(map);
    obj.linkedHashMap = new LinkedHashMap<>(map);
    obj.linkedHashMap2 = new LinkedHashMap<>(map);
    obj.linkedHashMap3 = new LinkedHashMap<>(map);
    obj.sortedMap = new TreeMap<>(map);
    obj.sortedMap2 = new TreeMap<>(map);
    obj.sortedMap3 = new TreeMap<>(map);
    obj.concurrentHashMap = new ConcurrentHashMap<>(map);
    obj.concurrentHashMap2 = new ConcurrentHashMap<>(map);
    obj.concurrentHashMap3 = new ConcurrentHashMap<>(map);
    obj.skipListMap = new ConcurrentSkipListMap<>(map);
    obj.skipListMap2 = new ConcurrentSkipListMap<>(map);
    obj.skipListMap3 = new ConcurrentSkipListMap<>(map);
    EnumMap<TestEnum, Object> enumMap = new EnumMap<>(TestEnum.class);
    enumMap.put(TestEnum.A, 1);
    enumMap.put(TestEnum.B, "str");
    obj.enumMap = enumMap;
    obj.enumMap2 = enumMap;
    obj.emptyMap = Collections.emptyMap();
    obj.sortedEmptyMap = Collections.emptySortedMap();
    obj.singletonMap = Collections.singletonMap("k", "v");
    return obj;
  }

  public static class TestClass1ForDefaultMap extends AbstractMap<String, Object> {
    private final Set<MapEntry> data = new HashSet<>();

    @Override
    public Set<Entry<String, Object>> entrySet() {
      Set data = this.data;
      return data;
    }

    @Override
    public Object put(String key, Object value) {
      return data.add(new MapEntry<>(key, value));
    }
  }

  public static class TestClass2ForDefaultMap extends AbstractMap<String, Object> {
    private final Set<Entry<String, Object>> data = new HashSet<>();

    @Override
    public Set<Entry<String, Object>> entrySet() {
      Set data = this.data;
      return data;
    }

    @Override
    public Object put(String key, Object value) {
      return data.add(new MapEntry<>(key, value));
    }
  }

  @Test(dataProvider = "enableCodegen")
  public void testDefaultMapSerializer(boolean enableCodegen) {
    Fury fury =
        Fury.builder()
            .withLanguage(Language.JAVA)
            .withCodegen(enableCodegen)
            .requireClassRegistration(false)
            .build();
    TestClass1ForDefaultMap map = new TestClass1ForDefaultMap();
    map.put("a", 1);
    map.put("b", 2);
    Assert.assertSame(
        fury.getClassResolver().getSerializerClass(TestClass1ForDefaultMap.class),
        MapSerializers.DefaultJavaMapSerializer.class);
    serDeCheck(fury, map);

    TestClass2ForDefaultMap map2 = new TestClass2ForDefaultMap();
    map.put("a", 1);
    map.put("b", 2);
    Assert.assertSame(
        fury.getClassResolver().getSerializerClass(TestClass2ForDefaultMap.class),
        MapSerializers.DefaultJavaMapSerializer.class);
    serDeCheck(fury, map2);
  }

  @Test(dataProvider = "furyCopyConfig")
  public void testDefaultMapSerializer(Fury fury) {
    TestClass1ForDefaultMap map = new TestClass1ForDefaultMap();
    map.put("a", 1);
    map.put("b", 2);
    Assert.assertSame(
        fury.getClassResolver().getSerializerClass(TestClass1ForDefaultMap.class),
        MapSerializers.DefaultJavaMapSerializer.class);
    copyCheck(fury, map);

    TestClass2ForDefaultMap map2 = new TestClass2ForDefaultMap();
    map.put("a", 1);
    map.put("b", 2);
    Assert.assertSame(
        fury.getClassResolver().getSerializerClass(TestClass2ForDefaultMap.class),
        MapSerializers.DefaultJavaMapSerializer.class);
    copyCheck(fury, map2);
  }

  @Data
  @AllArgsConstructor
  public static class GenericMapBoundTest {
    // test k/v generics
    public Map<Map<Integer, Collection<Integer>>, ? extends Collection<Integer>> map1;
    // test k/v generics bounds
    public Map<? extends Map<Integer, ? extends Collection<Integer>>, ? extends Collection<Integer>>
        map2;
  }

  @Test
  public void testGenericMapBound() {
    Fury fury1 =
        Fury.builder()
            .withLanguage(Language.JAVA)
            .requireClassRegistration(false)
            .withCodegen(false)
            .build();
    Fury fury2 =
        Fury.builder()
            .withLanguage(Language.JAVA)
            .requireClassRegistration(false)
            .withCodegen(false)
            .build();
    ArrayList<Integer> list = new ArrayList<>(of(1, 2));
    roundCheck(
        fury1,
        fury2,
        new GenericMapBoundTest(
            new HashMap<>(mapOf(new HashMap<>(mapOf(1, list)), list)),
            new HashMap<>(mapOf(new HashMap<>(mapOf(1, list)), list))));
  }

  public static class StringKeyMap<T> extends HashMap<String, T> {}

  @Test
  public void testStringKeyMapSerializer() {
    // see https://github.com/apache/fury/issues/1170
    Fury fury = Fury.builder().withRefTracking(true).build();
    fury.registerSerializer(StringKeyMap.class, MapSerializers.StringKeyMapSerializer.class);
    {
      StringKeyMap<List<String>> map = new StringKeyMap<>();
      map.put("k1", ofArrayList("a", "b"));
      serDeCheck(fury, map);
    }
    {
      // test nested map
      StringKeyMap<StringKeyMap<String>> map = new StringKeyMap<>();
      StringKeyMap<String> map2 = new StringKeyMap<>();
      map2.put("k-k1", "v1");
      map2.put("k-k2", "v2");
      map.put("k1", map2);
      serDeCheck(fury, map);
    }
  }

  @Test(dataProvider = "furyCopyConfig")
  public void testStringKeyMapSerializer(Fury fury) {
    fury.registerSerializer(StringKeyMap.class, MapSerializers.StringKeyMapSerializer.class);
    {
      StringKeyMap<List<String>> map = new StringKeyMap<>();
      map.put("k1", ofArrayList("a", "b"));
      copyCheck(fury, map);
    }
    {
      // test nested map
      StringKeyMap<StringKeyMap<String>> map = new StringKeyMap<>();
      StringKeyMap<String> map2 = new StringKeyMap<>();
      map2.put("k-k1", "v1");
      map2.put("k-k2", "v2");
      map.put("k1", map2);
      copyCheck(fury, map);
    }
  }

  // must be final class to test nested map value by private MapSerializer
  private static final class PrivateMap<K, V> implements Map<K, V> {
    private Set<Entry<K, V>> set = new HashSet<>();

    @Override
    public int size() {
      return set.size();
    }

    @Override
    public boolean isEmpty() {
      return set.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
      return set.stream().anyMatch(e -> e.getKey().equals(key));
    }

    @Override
    public boolean containsValue(Object value) {
      return set.stream().anyMatch(e -> e.getValue().equals(value));
    }

    @Override
    public V get(Object key) {
      for (Entry<K, V> kvEntry : set) {
        if (kvEntry.getKey().equals(key)) {
          return kvEntry.getValue();
        }
      }
      return null;
    }

    @Override
    public V put(K key, V value) {
      set.add(new MapEntry<>(key, value));
      return null;
    }

    @Override
    public V remove(Object key) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
      set.clear();
    }

    @Override
    public Set<K> keySet() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Collection<V> values() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
      return set;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      PrivateMap<?, ?> that = (PrivateMap<?, ?>) o;
      return Objects.equals(set, that.set);
    }

    @Override
    public int hashCode() {
      return Objects.hash(set);
    }
  }

  @Data
  @AllArgsConstructor
  public static class LazyMapCollectionFieldStruct {
    List<PrivateMap<String, Integer>> mapList;
    PrivateMap<String, Integer> map;
  }

  @Test
  public void testNestedValueByPrivateMapSerializer() {
    Fury fury = builder().withRefTracking(false).build();
    // test private map serializer
    fury.registerSerializer(PrivateMap.class, new MapSerializer(fury, PrivateMap.class) {});
    PrivateMap<String, Integer> map = new PrivateMap<>();
    map.put("k", 1);
    serDeCheck(fury, new LazyMapCollectionFieldStruct(ofArrayList(map), map));
  }

  @Test(dataProvider = "referenceTrackingConfig")
  public void testObjectKeyValueChunk(boolean referenceTrackingConfig) {
    Fury fury = Fury.builder().withRefTracking(referenceTrackingConfig).build();
    final Map<Object, Object> differentKeyAndValueTypeMap = createDifferentKeyAndValueTypeMap();
    final Serializer<? extends Map> serializer =
        fury.getSerializer(differentKeyAndValueTypeMap.getClass());
    MapSerializers.HashMapSerializer mapSerializer = (MapSerializers.HashMapSerializer) serializer;
    serDeCheck(fury, differentKeyAndValueTypeMap);
  }

  @Test(dataProvider = "referenceTrackingConfig")
  public void testObjectKeyValueBigChunk(boolean referenceTrackingConfig) {
    Fury fury = Fury.builder().withRefTracking(referenceTrackingConfig).build();
    final Map<Object, Object> differentKeyAndValueTypeMap = createDifferentKeyAndValueTypeMap();
    for (int i = 0; i < 3000; i++) {
      differentKeyAndValueTypeMap.put("k" + i, i);
    }
    final Serializer<? extends Map> serializer =
        fury.getSerializer(differentKeyAndValueTypeMap.getClass());
    MapSerializers.HashMapSerializer mapSerializer = (MapSerializers.HashMapSerializer) serializer;
    serDeCheck(fury, differentKeyAndValueTypeMap);
  }

  @Test
  public void testMapChunkRefTracking() {
    Fury fury =
        Fury.builder()
            .withRefTracking(true)
            .withCodegen(false)
            .requireClassRegistration(false)
            .build();
    Map<String, Integer> map = new HashMap<>();
    for (int i = 0; i < 1; i++) {
      map.put("k" + i, i);
    }
    Object v = ofArrayList(map, ofHashMap("k1", map, "k2", new HashMap<>(map), "k3", map));
    serDeCheck(fury, v);
  }

  @Test
  public void testMapChunkRefTrackingGenerics() {
    Fury fury =
        Fury.builder()
            .withRefTracking(true)
            .withCodegen(false)
            .requireClassRegistration(false)
            .build();

    MapFields obj = new MapFields();
    Map<String, Integer> map = new HashMap<>();
    for (int i = 0; i < 1; i++) {
      map.put("k" + i, i);
    }
    obj.map = map;
    obj.mapKeyFinal = ofHashMap("k1", map);
    serDeCheck(fury, obj);
  }

  @Test(dataProvider = "referenceTrackingConfig")
  public void testMapFieldsChunkSerializer(boolean referenceTrackingConfig) {
    Fury fury =
        Fury.builder()
            .withRefTracking(referenceTrackingConfig)
            .withCodegen(false)
            .requireClassRegistration(false)
            .build();
    final MapFields mapFieldsObject = createBigMapFieldsObject();
    serDeCheck(fury, mapFieldsObject);
  }

  private static Map<Object, Object> createDifferentKeyAndValueTypeMap() {
    Map<Object, Object> map = new HashMap<>();
    map.put(null, "1");
    map.put(2, "1");
    map.put(4, "1");
    map.put(6, "1");
    map.put(7, "1");
    map.put(10, "1");
    map.put(12, "null");
    map.put(19, "null");
    map.put(11, null);
    map.put(20, null);
    map.put(21, 9);
    map.put(22, 99);
    map.put(291, 900);
    map.put("292", 900);
    map.put("293", 900);
    map.put("23", 900);
    return map;
  }

  @Data
  public static class MapFieldStruct1 {
    public Map<Integer, Boolean> map1;
    public Map<String, String> map2;
  }

  @Test(dataProvider = "referenceTrackingConfig")
  public void testMapFieldStructCodegen1(boolean referenceTrackingConfig) {
    Fury fury =
        Fury.builder()
            .withRefTracking(referenceTrackingConfig)
            .withCodegen(true)
            .requireClassRegistration(false)
            .build();
    MapFieldStruct1 struct1 = new MapFieldStruct1();
    struct1.map1 = ofHashMap(1, false, 2, true);
    struct1.map2 = ofHashMap("k1", "v1", "k2", "v2");
    serDeCheck(fury, struct1);
  }

  @Data
  public static class MapFieldStruct2 {
    public Map<Object, Object> map1;
    public Map<String, Object> map2;
    public Map<Object, String> map3;
  }

  @Test(dataProvider = "referenceTrackingConfig")
  public void testMapFieldStructCodegen2(boolean referenceTrackingConfig) {
    Fury fury =
        Fury.builder()
            .withRefTracking(referenceTrackingConfig)
            .withCodegen(true)
            .requireClassRegistration(false)
            .build();
    MapFieldStruct2 struct1 = new MapFieldStruct2();
    struct1.map1 = ofHashMap(1, false, 2, true);
    struct1.map2 = ofHashMap("k1", "v1", "k2", "v2");
    struct1.map3 = ofHashMap(1, "v1", 2, "v2");
    serDeCheck(fury, struct1);
  }

  @Data
  public static class MapFieldStruct3 {
    public Map<Object, Object> map1;
    public Map<BeanB, Object> map2;
    public Map<Object, BeanB> map3;
  }

  @Test(dataProvider = "referenceTrackingConfig")
  public void testMapFieldStructCodegen3(boolean referenceTrackingConfig) {
    Fury fury =
        Fury.builder()
            .withRefTracking(referenceTrackingConfig)
            .withCodegen(true)
            .requireClassRegistration(false)
            .build();
    MapFieldStruct3 struct1 = new MapFieldStruct3();
    BeanB beanB = BeanB.createBeanB(2);
    struct1.map1 = ofHashMap(BeanB.createBeanB(2), BeanB.createBeanB(2));
    struct1.map2 = ofHashMap(BeanB.createBeanB(2), 1, beanB, beanB);
    struct1.map3 = ofHashMap(1, beanB, 2, beanB, 3, BeanB.createBeanB(2));
    serDeCheck(fury, struct1);
  }

  @Data
  public static class NestedMapFieldStruct1 {
    public Map<Object, Map<String, String>> map1;
    public Map<String, Map<String, Integer>> map2;
    public Map<Integer, Map<String, BeanB>> map3;
    public Map<Object, Map<Object, Map<String, BeanB>>> map4;
  }

  @Test(dataProvider = "referenceTrackingConfig")
  public void testNestedMapFieldStructCodegen(boolean referenceTrackingConfig) {
    Fury fury =
        Fury.builder()
            .withRefTracking(referenceTrackingConfig)
            .withCodegen(true)
            .requireClassRegistration(false)
            .build();
    NestedMapFieldStruct1 struct1 = new NestedMapFieldStruct1();
    BeanB beanB = BeanB.createBeanB(2);
    struct1.map1 = ofHashMap(1, ofHashMap("k1", "v1", "k2", "v2"));
    struct1.map2 = ofHashMap("k1", ofHashMap("k1", 1, "k2", 2));
    struct1.map3 = ofHashMap(1, ofHashMap("k1", beanB, "k2", beanB, "k3", BeanB.createBeanB(1)));
    struct1.map4 =
        ofHashMap(
            2, ofHashMap(true, ofHashMap("k1", beanB, "k2", beanB, "k3", BeanB.createBeanB(1))));
    serDeCheck(fury, struct1);
  }

  @Data
  static class Wildcard<T> {
    public int c = 9;
    public T t;
  }

  @Data
  private static class Wildcard1<T> {
    public int c = 10;
    public T t;
  }

  @Data
  public static class MapWildcardFieldStruct1 {
    protected Map<String, ?> f0;
    protected Map<String, Wildcard<String>> f1;
    protected Map<String, Wildcard<?>> f2;
    protected Map<?, Wildcard<?>> f3;
    protected Map<?, Wildcard1<?>> f4;
    protected Map<Wildcard1<String>, Wildcard<?>> f5;
    protected Map<String, Wildcard1<?>> f6;
  }

  @Test(dataProvider = "referenceTrackingConfig")
  public void testWildcard(boolean referenceTrackingConfig) {
    Fury fury =
        Fury.builder()
            .withRefTracking(referenceTrackingConfig)
            .requireClassRegistration(false)
            .build();
    MapWildcardFieldStruct1 struct = new MapWildcardFieldStruct1();
    struct.f0 = ofHashMap("k", 1);
    struct.f1 = ofHashMap("k1", new Wildcard<>());
    struct.f2 = ofHashMap("k2", new Wildcard<>());
    struct.f3 = ofHashMap(new Wildcard<>(), new Wildcard<>());
    struct.f4 = ofHashMap(new Wildcard1<>(), new Wildcard1<>());
    struct.f5 = ofHashMap(new Wildcard1<>(), new Wildcard<>());
    struct.f5 = ofHashMap("k5", new Wildcard1<>());
    serDeCheck(fury, struct);
  }

  @Data
  public static class NestedListMap {
    public List<Map<String, String>> map1;
    public List<HashMap<String, String>> map2;
  }

  @Test(dataProvider = "referenceTrackingConfig")
  public void testNestedListMap(boolean referenceTrackingConfig) {
    Fury fury =
        Fury.builder()
            .withRefTracking(referenceTrackingConfig)
            .requireClassRegistration(false)
            .build();
    NestedListMap o = new NestedListMap();
    o.map1 = ofArrayList(ofHashMap("k1", "v"));
    o.map2 = ofArrayList(ofHashMap("k2", "2"));
    serDeCheck(fury, o);
  }
}
