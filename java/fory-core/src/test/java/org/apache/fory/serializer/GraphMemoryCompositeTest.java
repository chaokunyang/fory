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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.LongFunction;
import org.apache.fory.Fory;
import org.apache.fory.ForyTestBase;
import org.apache.fory.annotation.Int32Type;
import org.apache.fory.collection.Int32List;
import org.apache.fory.config.Int32Encoding;
import org.apache.fory.context.ReadContext;
import org.apache.fory.context.WriteContext;
import org.apache.fory.exception.InsecureException;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.resolver.RefMode;
import org.apache.fory.serializer.collection.GuavaCollectionSerializers;
import org.apache.fory.type.BFloat16Array;
import org.apache.fory.type.Float16Array;
import org.apache.fory.type.Types;
import org.testng.annotations.Test;

public class GraphMemoryCompositeTest extends ForyTestBase {
  private static final long DEFAULT_GRAPH_MEMORY_BYTES = 128L * 1024 * 1024;
  private static final int REFERENCE_BYTES = GraphMemoryEstimates.REFERENCE_BYTES;
  private static final int ARRAY_OWNER_BYTES = GraphMemoryEstimates.objectArrayBytes();

  @Test
  public void testCompatibleEmptyOwners() {
    assertCompatibleBudget(
        ARRAY_OWNER_BYTES,
        CompatibleCollectionArrayReader.READ_LIST_TO_ARRAY,
        Types.INT32_ARRAY,
        Types.INT32,
        int[].class,
        new int[0]);
    assertCompatibleBudget(
        ARRAY_OWNER_BYTES + GraphMemoryEstimates.shallowObjectBytes(Int32List.class),
        CompatibleCollectionArrayReader.READ_LIST_TO_LIST,
        Types.INT32_ARRAY,
        Types.INT32,
        Int32List.class,
        new Int32List(new int[0]));
    assertCompatibleBudget(
        ARRAY_OWNER_BYTES + GraphMemoryEstimates.shallowObjectBytes(Float16Array.class),
        CompatibleCollectionArrayReader.READ_LIST_TO_ARRAY,
        Types.FLOAT16_ARRAY,
        Types.FLOAT16,
        Float16Array.class,
        Float16Array.of());
    assertCompatibleBudget(
        ARRAY_OWNER_BYTES + GraphMemoryEstimates.shallowObjectBytes(BFloat16Array.class),
        CompatibleCollectionArrayReader.READ_LIST_TO_ARRAY,
        Types.BFLOAT16_ARRAY,
        Types.BFLOAT16,
        BFloat16Array.class,
        BFloat16Array.of());
    long boxedListBytes = GraphMemoryEstimates.shallowObjectBytes(ArrayList.class);
    assertCompatibleBudget(
        Math.max(ARRAY_OWNER_BYTES, boxedListBytes),
        CompatibleCollectionArrayReader.READ_LIST_TO_LIST,
        Types.INT32_ARRAY,
        Types.INT32,
        List.class,
        new ArrayList<>());
  }

  @Test
  public void testCompatibleConcreteLists() {
    List<Integer> expected = ImmutableList.of(1, -2, 3);
    long linkedListBytes = collectionBytes(LinkedList.class, expected.size());
    assertThrows(
        InsecureException.class,
        () -> readDenseList(linkedListBytes - 1, LinkedList.class, 1, -2, 3));
    Object linkedList = readDenseList(linkedListBytes, LinkedList.class, 1, -2, 3);
    assertEquals(linkedList.getClass(), LinkedList.class);
    assertEquals(linkedList, expected);

    long arrayListBytes = collectionBytes(ArrayList.class, expected.size());
    assertThrows(
        InsecureException.class,
        () -> readDenseList(arrayListBytes - 1, ArrayList.class, 1, -2, 3));
    Object arrayList = readDenseList(arrayListBytes, ArrayList.class, 1, -2, 3);
    assertEquals(arrayList.getClass(), ArrayList.class);
    assertEquals(arrayList, expected);
    Object list = readDenseList(arrayListBytes, List.class, 1, -2, 3);
    assertEquals(list.getClass(), ArrayList.class);
    assertEquals(list, expected);

    long copyOnWriteBytes =
        collectionBytes(CopyOnWriteArrayList.class, expected.size()) + ARRAY_OWNER_BYTES;
    assertThrows(InsecureException.class, () -> readDenseCowAlias(copyOnWriteBytes - 1, 1, -2, 3));
    readDenseCowAlias(copyOnWriteBytes, 1, -2, 3);

    DenseArrayOwner source = new DenseArrayOwner();
    source.aValues = new int[] {1, -2, 3};
    byte[] bytes = newCompatibleOwnerFory(DenseArrayOwner.class).serialize(source);

    LinkedListOwner linkedOwner =
        (LinkedListOwner) newCompatibleOwnerFory(LinkedListOwner.class).deserialize(bytes);
    assertEquals(linkedOwner.aValues.getClass(), LinkedList.class);
    assertEquals(linkedOwner.aValues, expected);

    ArrayListOwner arrayListOwner =
        (ArrayListOwner) newCompatibleOwnerFory(ArrayListOwner.class).deserialize(bytes);
    assertEquals(arrayListOwner.aValues.getClass(), ArrayList.class);
    assertEquals(arrayListOwner.aValues, expected);

    ListOwner listOwner = (ListOwner) newCompatibleOwnerFory(ListOwner.class).deserialize(bytes);
    assertEquals(listOwner.aValues.getClass(), ArrayList.class);
    assertEquals(listOwner.aValues, expected);

    CowListOwner cowOwner =
        (CowListOwner) newCompatibleOwnerFory(CowListOwner.class).deserialize(bytes);
    assertEquals(cowOwner.aValues.getClass(), CopyOnWriteArrayList.class);
    assertEquals(cowOwner.aValues, expected);
  }

  @Test
  public void testCompatibleTrackedArray() {
    Fory fory = builder().withXlang(true).withCodegen(false).withRefTracking(true).build();
    ReadContext readContext = fory.getReadContext();
    readContext.prepare(denseIntBody(new int[] {7, 8}, true), null, false);
    try {
      Object array =
          CompatibleCollectionArrayReader.read(
              readContext,
              RefMode.TRACKING,
              CompatibleCollectionArrayReader.READ_ARRAY_TO_ARRAY,
              Types.INT32_ARRAY,
              Types.UNKNOWN,
              int[].class);
      Object alias =
          CompatibleCollectionArrayReader.read(
              readContext,
              RefMode.TRACKING,
              CompatibleCollectionArrayReader.READ_ARRAY_TO_ARRAY,
              Types.INT32_ARRAY,
              Types.UNKNOWN,
              long[].class);
      assertEquals((int[]) array, new int[] {7, 8});
      assertSame(alias, array);
      assertEquals(readContext.hasPreservedRefId(), false);
    } finally {
      readContext.reset();
    }
  }

  @Test
  public void testCompatibleRefSlotOwner() {
    Fory fory = builder().withXlang(true).withCodegen(false).withRefTracking(true).build();
    MemoryBuffer buffer = MemoryBuffer.newHeapBuffer(48);
    buffer.writeByte(Fory.REF_VALUE_FLAG);
    writeDenseInts(buffer, 7, 8);
    buffer.writeByte(Fory.REF_VALUE_FLAG);
    writeDenseInts(buffer, 1, 2, 3);
    ReadContext readContext = fory.getReadContext();
    readContext.prepare(
        MemoryBuffer.fromByteArray(buffer.getBytes(0, buffer.writerIndex())), null, false);
    try {
      Object trackedArray =
          CompatibleCollectionArrayReader.read(
              readContext,
              RefMode.TRACKING,
              CompatibleCollectionArrayReader.READ_ARRAY_TO_ARRAY,
              Types.INT32_ARRAY,
              Types.UNKNOWN,
              int[].class);
      assertEquals((int[]) trackedArray, new int[] {7, 8});
      int outerRefId = readContext.tryPreserveRefId();
      Object list =
          CompatibleCollectionArrayReader.read(
              readContext,
              RefMode.NONE,
              CompatibleCollectionArrayReader.READ_ARRAY_TO_LIST,
              Types.INT32_ARRAY,
              Types.INT32,
              LinkedList.class);
      assertEquals(list, ImmutableList.of(1, 2, 3));
      assertEquals(readContext.lastPreservedRefId(), outerRefId);
    } finally {
      readContext.reset();
    }
  }

  @Test
  public void testCompositeSetOwners() {
    TreeSet<Integer> treeSet = new TreeSet<>(ImmutableList.of(1, 2, 3));
    long treeSetBytes =
        setBytes(treeSet.getClass(), treeSet.size())
            + GraphMemoryEstimates.shallowObjectBytes(TreeMap.class);
    assertGraphBudget(treeSet, treeSetBytes, GraphMemoryCompositeTest::newFory);

    ConcurrentSkipListSet<Integer> skipListSet =
        new ConcurrentSkipListSet<>(ImmutableList.of(1, 2, 3));
    long skipListSetBytes =
        setBytes(skipListSet.getClass(), skipListSet.size())
            + GraphMemoryEstimates.shallowObjectBytes(ConcurrentSkipListMap.class);
    assertGraphBudget(skipListSet, skipListSetBytes, GraphMemoryCompositeTest::newFory);

    CopyOnWriteArraySet<Integer> copyOnWriteSet =
        new CopyOnWriteArraySet<>(ImmutableList.of(1, 2, 3));
    long copyOnWriteSetBytes =
        setBytes(copyOnWriteSet.getClass(), copyOnWriteSet.size())
            + GraphMemoryEstimates.shallowObjectBytes(CopyOnWriteArrayList.class)
            + ARRAY_OWNER_BYTES;
    assertGraphBudget(copyOnWriteSet, copyOnWriteSetBytes, GraphMemoryCompositeTest::newFory);

    ChildTreeSet<Integer> childTreeSet = new ChildTreeSet<>();
    childTreeSet.addAll(ImmutableList.of(1, 2, 3));
    long childTreeSetBytes =
        setBytes(childTreeSet.getClass(), childTreeSet.size())
            + GraphMemoryEstimates.shallowObjectBytes(TreeMap.class);
    assertGraphBudget(childTreeSet, childTreeSetBytes, GraphMemoryCompositeTest::newChildSetFory);

    ChildSkipListSet<Integer> childSkipListSet = new ChildSkipListSet<>();
    childSkipListSet.addAll(ImmutableList.of(1, 2, 3));
    long childSkipListSetBytes =
        setBytes(childSkipListSet.getClass(), childSkipListSet.size())
            + GraphMemoryEstimates.shallowObjectBytes(ConcurrentSkipListMap.class);
    assertGraphBudget(
        childSkipListSet, childSkipListSetBytes, GraphMemoryCompositeTest::newChildSetFory);
  }

  @Test
  public void testCopyOnWriteAliases() {
    long emptyListBytes = collectionBytes(CopyOnWriteArrayList.class, 0) + ARRAY_OWNER_BYTES;
    assertGraphBudget(
        new CopyOnWriteArrayList<>(), emptyListBytes, GraphMemoryCompositeTest::newRefFory);
    long emptySetBytes =
        collectionBytes(CopyOnWriteArraySet.class, 0)
            + GraphMemoryEstimates.shallowObjectBytes(CopyOnWriteArrayList.class)
            + ARRAY_OWNER_BYTES;
    assertGraphBudget(
        new CopyOnWriteArraySet<>(), emptySetBytes, GraphMemoryCompositeTest::newRefFory);

    CopyOnWriteArrayList<Object> list = new CopyOnWriteArrayList<>();
    list.add(list);
    long listBytes = collectionBytes(CopyOnWriteArrayList.class, 1) + ARRAY_OWNER_BYTES;
    byte[] listData = newRefFory(DEFAULT_GRAPH_MEMORY_BYTES).serialize(list);
    assertThrows(InsecureException.class, () -> newRefFory(listBytes - 1).deserialize(listData));
    CopyOnWriteArrayList<?> decodedList =
        (CopyOnWriteArrayList<?>) newRefFory(listBytes).deserialize(listData);
    assertSame(decodedList.get(0), decodedList);

    CopyOnWriteArraySet<Object> set = new CopyOnWriteArraySet<>();
    set.add(set);
    long setBytes =
        collectionBytes(CopyOnWriteArraySet.class, 1)
            + GraphMemoryEstimates.shallowObjectBytes(CopyOnWriteArrayList.class)
            + ARRAY_OWNER_BYTES;
    byte[] setData = newRefFory(DEFAULT_GRAPH_MEMORY_BYTES).serialize(set);
    assertThrows(InsecureException.class, () -> newRefFory(setBytes - 1).deserialize(setData));
    CopyOnWriteArraySet<?> decodedSet =
        (CopyOnWriteArraySet<?>) newRefFory(setBytes).deserialize(setData);
    assertSame(decodedSet.iterator().next(), decodedSet);
  }

  @Test
  public void testGuavaChildOwners() {
    ImmutableList<Integer> list = ImmutableList.of(1, 2, 3);
    assertGraphBudget(
        list,
        collectionBytes(list.getClass(), list.size()) + ARRAY_OWNER_BYTES,
        GraphMemoryCompositeTest::newGuavaFory);

    ImmutableSet<Integer> set = ImmutableSet.of(1, 2, 3);
    assertGraphBudget(
        set,
        collectionBytes(set.getClass(), set.size())
            + 2L * ARRAY_OWNER_BYTES
            + (long) set.size() * REFERENCE_BYTES,
        GraphMemoryCompositeTest::newGuavaFory);

    ImmutableSortedSet<Integer> sortedSet = ImmutableSortedSet.of(1, 2, 3);
    long listChildBytes =
        GraphMemoryEstimates.shallowObjectBytes(list.getClass()) + ARRAY_OWNER_BYTES;
    long comparatorBytes =
        GraphMemoryEstimates.shallowObjectBytes(sortedSet.comparator().getClass());
    assertGraphBudget(
        sortedSet,
        collectionBytes(sortedSet.getClass(), sortedSet.size()) + listChildBytes + comparatorBytes,
        GraphMemoryCompositeTest::newGuavaFory);

    ImmutableMap<Integer, Integer> map = ImmutableMap.of(1, 2, 3, 4);
    assertGraphBudget(
        map,
        mapBytes(map.getClass(), map.size()) + 2L * ARRAY_OWNER_BYTES,
        GraphMemoryCompositeTest::newGuavaFory);

    ImmutableBiMap<Integer, Integer> biMap = ImmutableBiMap.of(1, 2, 3, 4);
    assertGraphBudget(
        biMap,
        mapBytes(biMap.getClass(), biMap.size())
            + 3L * ARRAY_OWNER_BYTES
            + (long) biMap.size() * REFERENCE_BYTES,
        GraphMemoryCompositeTest::newGuavaFory);
    assertMapFormBudget(
        serializedForm(map),
        map,
        mapBytes(ImmutableMap.class, map.size()) + 2L * ARRAY_OWNER_BYTES,
        false);
    assertMapFormBudget(
        serializedForm(biMap),
        biMap,
        mapBytes(ImmutableBiMap.class, biMap.size())
            + 3L * ARRAY_OWNER_BYTES
            + (long) biMap.size() * REFERENCE_BYTES,
        true);

    ImmutableSortedMap<Integer, Integer> sortedMap = ImmutableSortedMap.of(1, 2, 3, 4);
    long sortedSetChildBytes =
        GraphMemoryEstimates.shallowObjectBytes(sortedSet.getClass()) + 2L * listChildBytes;
    assertGraphBudget(
        sortedMap,
        mapBytes(sortedMap.getClass(), sortedMap.size()) + sortedSetChildBytes + comparatorBytes,
        GraphMemoryCompositeTest::newGuavaFory);

    HashBasedTable<Integer, Integer, Integer> table = HashBasedTable.create();
    table.put(1, 1, 1);
    table.put(1, 2, 2);
    table.put(2, 1, 3);
    long tableBytes =
        GraphMemoryEstimates.shallowObjectBytes(HashBasedTable.class)
            + classBytes("com.google.common.collect.HashBasedTable$Factory")
            + 3L * table.size() * REFERENCE_BYTES
            + 3L * GraphMemoryEstimates.shallowObjectBytes(LinkedHashMap.class);
    assertGraphBudget(table, tableBytes, GraphMemoryCompositeTest::newTableFory);
  }

  private static Fory newFory(long maxGraphMemoryBytes) {
    return builder().withMaxGraphMemoryBytes(maxGraphMemoryBytes).build();
  }

  private static Fory newGuavaFory(long maxGraphMemoryBytes) {
    return builder()
        .withXlang(false)
        .registerGuavaTypes(true)
        .requireClassRegistration(false)
        .withMaxGraphMemoryBytes(maxGraphMemoryBytes)
        .build();
  }

  private static Fory newChildSetFory(long maxGraphMemoryBytes) {
    Fory fory = newFory(maxGraphMemoryBytes);
    fory.register(ChildTreeSet.class);
    fory.register(ChildSkipListSet.class);
    return fory;
  }

  private static Fory newRefFory(long maxGraphMemoryBytes) {
    return builder().withRefTracking(true).withMaxGraphMemoryBytes(maxGraphMemoryBytes).build();
  }

  private static Fory newXlangFory(long maxGraphMemoryBytes) {
    return builder()
        .withXlang(true)
        .withCodegen(false)
        .withMaxGraphMemoryBytes(maxGraphMemoryBytes)
        .build();
  }

  private static Fory newTableFory(long maxGraphMemoryBytes) {
    Fory fory = newGuavaFory(maxGraphMemoryBytes);
    fory.register(HashBasedTable.class);
    return fory;
  }

  private static Fory newCompatibleOwnerFory(Class<?> type) {
    Fory fory =
        builder()
            .withXlang(true)
            .withCompatible(true)
            .withCodegen(false)
            .withRefTracking(true)
            .build();
    fory.register(type, "graph.compatible-list-owner");
    return fory;
  }

  private static void assertCompatibleBudget(
      long required,
      int readMode,
      int arrayTypeId,
      int elementTypeId,
      Class<?> targetType,
      Object expected) {
    assertThrows(
        InsecureException.class,
        () -> readEmptyCompatible(required - 1, readMode, arrayTypeId, elementTypeId, targetType));
    assertEquals(
        readEmptyCompatible(required, readMode, arrayTypeId, elementTypeId, targetType), expected);
  }

  private static Object readEmptyCompatible(
      long maxGraphMemoryBytes,
      int readMode,
      int arrayTypeId,
      int elementTypeId,
      Class<?> targetType) {
    Fory fory = newXlangFory(maxGraphMemoryBytes);
    MemoryBuffer buffer = MemoryBuffer.newHeapBuffer(1);
    buffer.writeVarUInt32Small7(0);
    ReadContext readContext = fory.getReadContext();
    readContext.prepare(MemoryBuffer.fromByteArray(buffer.getBytes(0, 1)), null, false);
    try {
      return CompatibleCollectionArrayReader.read(
          readContext, RefMode.NONE, readMode, arrayTypeId, elementTypeId, targetType);
    } finally {
      readContext.reset();
    }
  }

  private static Object readDenseList(
      long maxGraphMemoryBytes, Class<?> targetType, int... values) {
    Fory fory = newXlangFory(maxGraphMemoryBytes);
    MemoryBuffer buffer = denseIntBody(values, false);
    ReadContext readContext = fory.getReadContext();
    readContext.prepare(buffer, null, false);
    try {
      return CompatibleCollectionArrayReader.read(
          readContext,
          RefMode.NONE,
          CompatibleCollectionArrayReader.READ_ARRAY_TO_LIST,
          Types.INT32_ARRAY,
          Types.INT32,
          targetType);
    } finally {
      readContext.reset();
    }
  }

  private static void readDenseCowAlias(long maxGraphMemoryBytes, int... values) {
    Fory fory =
        builder()
            .withXlang(true)
            .withCodegen(false)
            .withRefTracking(true)
            .withMaxGraphMemoryBytes(maxGraphMemoryBytes)
            .build();
    MemoryBuffer buffer = denseIntBody(values, true);
    ReadContext readContext = fory.getReadContext();
    readContext.prepare(buffer, null, false);
    try {
      Object list =
          CompatibleCollectionArrayReader.read(
              readContext,
              RefMode.TRACKING,
              CompatibleCollectionArrayReader.READ_ARRAY_TO_LIST,
              Types.INT32_ARRAY,
              Types.INT32,
              CopyOnWriteArrayList.class);
      Object alias =
          CompatibleCollectionArrayReader.read(
              readContext,
              RefMode.TRACKING,
              CompatibleCollectionArrayReader.READ_ARRAY_TO_LIST,
              Types.INT32_ARRAY,
              Types.INT32,
              CopyOnWriteArrayList.class);
      List<Integer> expected = new ArrayList<>(values.length);
      for (int value : values) {
        expected.add(value);
      }
      assertEquals(list.getClass(), CopyOnWriteArrayList.class);
      assertEquals(list, expected);
      assertSame(alias, list);
    } finally {
      readContext.reset();
    }
  }

  private static MemoryBuffer denseIntBody(int[] values, boolean trackedAlias) {
    MemoryBuffer buffer = MemoryBuffer.newHeapBuffer(32);
    if (trackedAlias) {
      buffer.writeByte(Fory.REF_VALUE_FLAG);
    }
    writeDenseInts(buffer, values);
    if (trackedAlias) {
      buffer.writeByte(Fory.REF_FLAG);
      buffer.writeByte(0);
    }
    return MemoryBuffer.fromByteArray(buffer.getBytes(0, buffer.writerIndex()));
  }

  private static void writeDenseInts(MemoryBuffer buffer, int... values) {
    buffer.writeVarUInt32Small7(values.length * Integer.BYTES);
    for (int value : values) {
      buffer.writeInt32(value);
    }
  }

  private static Object serializedForm(Object value) {
    Class<?> current = value.getClass();
    while (current != null) {
      try {
        Method writeReplace = current.getDeclaredMethod("writeReplace");
        writeReplace.setAccessible(true);
        return writeReplace.invoke(value);
      } catch (NoSuchMethodException e) {
        current = current.getSuperclass();
      } catch (ReflectiveOperationException e) {
        throw new RuntimeException(e);
      }
    }
    throw new IllegalArgumentException("No writeReplace method for " + value.getClass());
  }

  private static void assertMapFormBudget(
      Object form, Object expected, long required, boolean biMap) {
    byte[] bytes = writeMapForm(form, biMap);
    assertThrows(
        InsecureException.class, () -> readMapForm(required - 1, bytes, form.getClass(), biMap));
    assertEquals(readMapForm(required, bytes, form.getClass(), biMap), expected);
  }

  private static byte[] writeMapForm(Object form, boolean biMap) {
    Fory fory = newGuavaFory(DEFAULT_GRAPH_MEMORY_BYTES);
    MemoryBuffer buffer = MemoryBuffer.newHeapBuffer(32);
    WriteContext writeContext = fory.getWriteContext();
    writeContext.prepare(buffer, null);
    try {
      mapFormSerializer(fory, form.getClass(), biMap).write(writeContext, form);
      return buffer.getBytes(0, buffer.writerIndex());
    } finally {
      writeContext.reset();
    }
  }

  private static Object readMapForm(
      long maxGraphMemoryBytes, byte[] bytes, Class<?> formClass, boolean biMap) {
    Fory fory = newGuavaFory(maxGraphMemoryBytes);
    ReadContext readContext = fory.getReadContext();
    readContext.prepare(MemoryBuffer.fromByteArray(bytes), null, false);
    try {
      return mapFormSerializer(fory, formClass, biMap).read(readContext);
    } finally {
      readContext.reset();
    }
  }

  @SuppressWarnings("unchecked")
  private static Serializer<Object> mapFormSerializer(
      Fory fory, Class<?> formClass, boolean biMap) {
    return (Serializer<Object>)
        (biMap
            ? new GuavaCollectionSerializers.ImmutableBiMapFormSerializer(
                fory.getTypeResolver(), formClass)
            : new GuavaCollectionSerializers.ImmutableMapFormSerializer(
                fory.getTypeResolver(), formClass));
  }

  private static void assertGraphBudget(
      Object value, long required, LongFunction<Fory> foryFactory) {
    byte[] bytes = foryFactory.apply(DEFAULT_GRAPH_MEMORY_BYTES).serialize(value);
    assertThrows(InsecureException.class, () -> foryFactory.apply(required - 1).deserialize(bytes));
    assertEquals(foryFactory.apply(required).deserialize(bytes), value);
  }

  private static long collectionBytes(Class<?> type, int size) {
    return GraphMemoryEstimates.shallowObjectBytes(type) + (long) size * REFERENCE_BYTES;
  }

  private static long setBytes(Class<?> type, int size) {
    return collectionBytes(type, size);
  }

  private static long mapBytes(Class<?> type, int size) {
    return GraphMemoryEstimates.shallowObjectBytes(type) + (long) size * 2 * REFERENCE_BYTES;
  }

  private static long classBytes(String className) {
    try {
      return GraphMemoryEstimates.shallowObjectBytes(Class.forName(className));
    } catch (ClassNotFoundException e) {
      throw new AssertionError(e);
    }
  }

  public static final class ChildTreeSet<E> extends TreeSet<E> {}

  public static final class ChildSkipListSet<E> extends ConcurrentSkipListSet<E> {}

  public static final class DenseArrayOwner {
    public int[] aValues;
  }

  public static final class LinkedListOwner {
    public LinkedList<@Int32Type(encoding = Int32Encoding.FIXED) Integer> aValues;
  }

  public static final class ArrayListOwner {
    public ArrayList<@Int32Type(encoding = Int32Encoding.FIXED) Integer> aValues;
  }

  public static final class ListOwner {
    public List<@Int32Type(encoding = Int32Encoding.FIXED) Integer> aValues;
  }

  public static final class CowListOwner {
    public CopyOnWriteArrayList<@Int32Type(encoding = Int32Encoding.FIXED) Integer> aValues;
  }
}
