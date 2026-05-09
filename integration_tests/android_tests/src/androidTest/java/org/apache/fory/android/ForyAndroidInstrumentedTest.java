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

package org.apache.fory.android;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.fory.Fory;
import org.apache.fory.ThreadSafeFory;
import org.apache.fory.config.ForyBuilder;
import org.apache.fory.context.ReadContext;
import org.apache.fory.context.WriteContext;
import org.apache.fory.io.ForyInputStream;
import org.apache.fory.io.ForyReadableChannel;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.memory.MemoryUtils;
import org.apache.fory.platform.AndroidSupport;
import org.apache.fory.resolver.TypeResolver;
import org.apache.fory.serializer.Serializer;
import org.apache.fory.type.union.Union2;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ForyAndroidInstrumentedTest {
  @Test
  public void androidRuntimeDisablesCodegenAndUnsafeCopies() {
    assertTrue(AndroidSupport.IS_ANDROID);

    Fory fory = nativeBuilder().withCodegen(true).withAsyncCompilation(true).build();
    assertFalse(fory.getConfig().isCodeGenEnabled());
    assertFalse(fory.getConfig().isAsyncCompilationEnabled());

    MemoryBuffer buffer = MemoryUtils.buffer(16);
    try {
      buffer.copyToUnsafe(0, new byte[16], 0, 1);
      fail("copyToUnsafe should fail on Android");
    } catch (UnsupportedOperationException expected) {
      assertTrue(expected.getMessage().contains("Android"));
    }
  }

  @Test
  public void structEnumCollectionAndMapRoundTrip() {
    Fory fory = nativeBuilder().build();
    AndroidStruct value = AndroidStruct.create();
    AndroidStruct copy = roundTrip(fory, value);

    assertEquals(value, copy);
    assertEquals(AndroidEnum.GREEN, roundTrip(fory, AndroidEnum.GREEN));
  }

  @Test
  public void customSerializerExtRoundTrip() {
    Fory fory = nativeBuilder().build();
    fory.registerSerializerAndType(AndroidExt.class, AndroidExtSerializer.class);

    AndroidExt value = new AndroidExt("android", 26);
    assertEquals(value, roundTrip(fory, value));
  }

  @Test
  public void xlangUnionRoundTrip() {
    Fory fory = Fory.builder().withXlang(true).registerGuavaTypes(false).build();

    Union2<String, Integer> value = Union2.ofT2(26);
    Object copy = roundTrip(fory, value);

    assertEquals(value, copy);
  }

  @Test
  public void jdkProxyCycleRoundTripAndCopy() {
    Fory fory = nativeBuilder().withRefCopy(true).build();
    AndroidProxyHandler handler = new AndroidProxyHandler();
    AndroidProxyService proxy =
        (AndroidProxyService)
            Proxy.newProxyInstance(
                AndroidProxyService.class.getClassLoader(),
                new Class[] {AndroidProxyService.class},
                handler);
    handler.proxy = proxy;

    AndroidProxyService copy = roundTrip(fory, proxy);
    assertEquals(42, copy.value());
    assertSame(copy, copy.self());

    AndroidProxyService copiedAgain = fory.copy(proxy);
    assertEquals(42, copiedAgain.value());
    assertSame(copiedAgain, copiedAgain.self());
  }

  @Test
  public void internalCollectionMapAndSetWrappersRoundTrip() {
    Fory fory = nativeBuilder().build();

    List<String> list = Arrays.asList("a", "b", "c");
    assertEquals(list, roundTrip(fory, list));
    assertEquals(list.subList(1, 3), roundTrip(fory, list.subList(1, 3)));

    Set<String> set = new HashSet<>(list);
    Map<String, Integer> map = new HashMap<>();
    map.put("a", 1);
    map.put("b", 2);

    assertEquals(
        Collections.unmodifiableList(list), roundTrip(fory, Collections.unmodifiableList(list)));
    assertEquals(
        Collections.unmodifiableSet(set), roundTrip(fory, Collections.unmodifiableSet(set)));
    assertEquals(
        Collections.unmodifiableMap(map), roundTrip(fory, Collections.unmodifiableMap(map)));
    assertEquals(
        Collections.synchronizedList(new ArrayList<>(list)),
        roundTrip(fory, Collections.synchronizedList(new ArrayList<>(list))));
    assertEquals(
        Collections.synchronizedSet(set), roundTrip(fory, Collections.synchronizedSet(set)));
    assertEquals(
        Collections.synchronizedMap(map), roundTrip(fory, Collections.synchronizedMap(map)));

    Set<String> setFromMap = Collections.newSetFromMap(new HashMap<String, Boolean>());
    setFromMap.addAll(list);
    assertEquals(setFromMap, roundTrip(fory, setFromMap));

    EnumMap<AndroidEnum, String> enumMap = new EnumMap<>(AndroidEnum.class);
    enumMap.put(AndroidEnum.BLUE, "blue");
    assertEquals(enumMap, roundTrip(fory, enumMap));
  }

  @Test
  public void byteBufferStreamChannelAndOutOfBandRoundTrip() throws Exception {
    ThreadSafeFory threadSafeFory = nativeBuilder().buildThreadSafeFory();
    Fory fory = nativeBuilder().build();
    AndroidStruct value = AndroidStruct.create();

    byte[] bytes = fory.serialize(value);
    ByteBuffer direct = ByteBuffer.allocateDirect(bytes.length + 2);
    direct.position(1);
    direct.put(bytes);
    direct.limit(direct.position());
    direct.position(1);
    ByteBuffer readonly = direct.asReadOnlyBuffer();
    int position = readonly.position();
    int limit = readonly.limit();
    assertEquals(value, threadSafeFory.deserialize(readonly));
    assertEquals(position, readonly.position());
    assertEquals(limit, readonly.limit());

    ByteArrayOutputStream streamBytes = new ByteArrayOutputStream();
    fory.serialize(streamBytes, value);
    ForyInputStream inputStream =
        new ForyInputStream(new ByteArrayInputStream(streamBytes.toByteArray()));
    assertEquals(value, fory.deserialize(inputStream));

    ForyReadableChannel channel =
        new ForyReadableChannel(
            Channels.newChannel(new ByteArrayInputStream(streamBytes.toByteArray())));
    assertEquals(value, fory.deserialize(channel));

    final List<MemoryBuffer> buffers = new ArrayList<>();
    int[] numbers = new int[] {1, 2, 3, 4, 5, 6};
    byte[] payload =
        fory.serialize(
            numbers,
            bufferObject -> {
              buffers.add(bufferObject.toBuffer());
              return false;
            });
    assertArrayEquals(numbers, (int[]) fory.deserialize(payload, buffers));
  }

  private static ForyBuilder nativeBuilder() {
    return Fory.builder()
        .withXlang(false)
        .withRefTracking(true)
        .requireClassRegistration(false)
        .registerGuavaTypes(false);
  }

  @SuppressWarnings("unchecked")
  private static <T> T roundTrip(Fory fory, T value) {
    return (T) fory.deserialize(fory.serialize(value));
  }

  public enum AndroidEnum {
    RED,
    GREEN,
    BLUE
  }

  public static class AndroidStruct {
    public int id;
    public String name;
    public AndroidEnum color;
    public ArrayList<String> tags;
    public HashMap<String, Integer> scores;
    public AndroidNested nested;

    public AndroidStruct() {}

    public static AndroidStruct create() {
      AndroidStruct struct = new AndroidStruct();
      struct.id = 26;
      struct.name = "android";
      struct.color = AndroidEnum.GREEN;
      struct.tags = new ArrayList<>(Arrays.asList("core", "runtime"));
      struct.scores = new HashMap<>();
      struct.scores.put("api", 26);
      struct.scores.put("status", 1);
      struct.nested = new AndroidNested("child", 7);
      return struct;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof AndroidStruct)) {
        return false;
      }
      AndroidStruct that = (AndroidStruct) o;
      return id == that.id
          && equalsNullable(name, that.name)
          && color == that.color
          && equalsNullable(tags, that.tags)
          && equalsNullable(scores, that.scores)
          && equalsNullable(nested, that.nested);
    }

    @Override
    public int hashCode() {
      return id;
    }
  }

  public static class AndroidNested {
    public String label;
    public int count;

    public AndroidNested() {}

    public AndroidNested(String label, int count) {
      this.label = label;
      this.count = count;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof AndroidNested)) {
        return false;
      }
      AndroidNested that = (AndroidNested) o;
      return count == that.count && equalsNullable(label, that.label);
    }

    @Override
    public int hashCode() {
      return count;
    }
  }

  public static class AndroidExt {
    public String name;
    public int api;

    public AndroidExt() {}

    public AndroidExt(String name, int api) {
      this.name = name;
      this.api = api;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof AndroidExt)) {
        return false;
      }
      AndroidExt that = (AndroidExt) o;
      return api == that.api && equalsNullable(name, that.name);
    }

    @Override
    public int hashCode() {
      return api;
    }
  }

  public static final class AndroidExtSerializer extends Serializer<AndroidExt> {
    public AndroidExtSerializer(TypeResolver typeResolver, Class<AndroidExt> cls) {
      super(typeResolver.getConfig(), cls);
    }

    @Override
    public void write(WriteContext writeContext, AndroidExt value) {
      writeContext.writeString(value.name);
      writeContext.getBuffer().writeVarInt32(value.api);
    }

    @Override
    public AndroidExt read(ReadContext readContext) {
      AndroidExt value = new AndroidExt();
      value.name = readContext.readString();
      value.api = readContext.getBuffer().readVarInt32();
      return value;
    }
  }

  public interface AndroidProxyService {
    int value();

    AndroidProxyService self();
  }

  public static final class AndroidProxyHandler implements InvocationHandler, Serializable {
    public AndroidProxyService proxy;

    public AndroidProxyHandler() {}

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
      switch (method.getName()) {
        case "value":
          return 42;
        case "self":
          return this.proxy;
        case "toString":
          return "AndroidProxyService";
        case "hashCode":
          return System.identityHashCode(proxy);
        case "equals":
          return proxy == args[0];
        default:
          throw new UnsupportedOperationException(method.toString());
      }
    }
  }

  private static boolean equalsNullable(Object left, Object right) {
    return left == right || (left != null && left.equals(right));
  }
}
