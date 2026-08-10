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
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.fory.Fory;
import org.apache.fory.ForyTestBase;
import org.apache.fory.exception.InsecureException;
import org.apache.fory.memory.MemoryUtils;
import org.testng.annotations.Test;

public class GraphMemoryOwnersTest extends ForyTestBase {
  private static final int REFERENCE_BYTES = GraphMemoryEstimates.REFERENCE_BYTES;
  private static final int ARRAY_OWNER_BYTES = GraphMemoryEstimates.objectArrayBytes();

  @Test
  public void testBitSetBudget() {
    BitSet value = BitSet.valueOf(new long[] {1, Long.MIN_VALUE});
    long required =
        GraphMemoryEstimates.shallowObjectBytes(BitSet.class) + ARRAY_OWNER_BYTES + 2L * Long.BYTES;
    assertBudget(value, required, false);
  }

  @Test
  public void testOptionalBudget() {
    long required = GraphMemoryEstimates.shallowObjectBytes(Optional.class);
    assertBudget(Optional.of("value"), required, false);

    Fory fory = newFory(1, false);
    Optional<?> decoded = (Optional<?>) fory.deserialize(fory.serialize(Optional.empty()));
    assertFalse(decoded.isPresent());
  }

  @Test
  public void testAtomicReferenceBudget() {
    long required = GraphMemoryEstimates.shallowObjectBytes(AtomicReference.class);
    assertBudget(new AtomicReference<>(), required, false);
  }

  @Test
  public void testProxyBudget() throws ClassNotFoundException {
    Object value =
        Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class[] {TestProxy.class},
            new TestInvocationHandler());
    long proxyBytes =
        GraphMemoryEstimates.objectArrayBytes()
            + REFERENCE_BYTES
            + GraphMemoryEstimates.shallowObjectBytes(TestInvocationHandler.class)
            + GraphMemoryEstimates.shallowObjectBytes(Proxy.class);
    assertBudget(value, proxyBytes, false);
    if (!MemoryUtils.JDK_PROXY_FIELD_ACCESS) {
      Class<?> deferredHandler =
          Class.forName(JdkProxySerializer.class.getName() + "$DeferredInvocationHandler");
      proxyBytes += GraphMemoryEstimates.shallowObjectBytes(deferredHandler);
    }
    assertBudget(value, proxyBytes, true);
  }

  @Test
  public void testExternalizableBudget() {
    long required = GraphMemoryEstimates.shallowObjectBytes(EmptyExternalizable.class);
    assertBudget(new EmptyExternalizable(), required, false);
  }

  @Test
  public void testStackTraceElementBudget() {
    StackTraceElement value = new StackTraceElement("Owner", "read", "Owner.java", 41);
    long required = GraphMemoryEstimates.shallowObjectBytes(StackTraceElement.class);
    assertBudget(value, required, false);
  }

  @Test
  public void testEnumMapBudget() {
    EnumMap<LargeEnum, Object> value = new EnumMap<>(LargeEnum.class);
    long required =
        GraphMemoryEstimates.shallowObjectBytes(EnumMap.class)
            + ARRAY_OWNER_BYTES
            + (long) LargeEnum.values().length * REFERENCE_BYTES;
    assertBudget(value, required, false);
  }

  @Test
  public void testJumboEnumSetBudget() {
    EnumSet<LargeEnum> value = EnumSet.noneOf(LargeEnum.class);
    long required =
        GraphMemoryEstimates.shallowObjectBytes(value.getClass())
            + ARRAY_OWNER_BYTES
            + 2L * Long.BYTES;
    assertBudget(value, required, false);
  }

  @Test
  public void testPriorityQueueBudget() {
    PriorityQueue<Object> value = new PriorityQueue<>();
    long required =
        GraphMemoryEstimates.shallowObjectBytes(PriorityQueue.class)
            + ARRAY_OWNER_BYTES
            + 11L * REFERENCE_BYTES;
    assertBudget(value, required, false);
  }

  private static void assertBudget(Object value, long required, boolean trackingRef) {
    byte[] bytes = newFory(required, trackingRef).serialize(value);
    assertThrows(
        InsecureException.class, () -> newFory(required - 1, trackingRef).deserialize(bytes));
    Object decoded = newFory(required, trackingRef).deserialize(bytes);
    assertEquals(decoded.getClass(), value.getClass());
  }

  private static Fory newFory(long maxGraphMemoryBytes, boolean trackingRef) {
    return Fory.builder()
        .withXlang(false)
        .withRefTracking(trackingRef)
        .withCodegen(false)
        .requireClassRegistration(false)
        .withCompatible(false)
        .withMaxGraphMemoryBytes(maxGraphMemoryBytes)
        .build();
  }

  private interface TestProxy {
    int value();
  }

  private static final class TestInvocationHandler implements InvocationHandler {
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
      return 1;
    }
  }

  public static final class EmptyExternalizable implements Externalizable {
    public EmptyExternalizable() {}

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {}

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {}
  }

  private enum LargeEnum {
    E00,
    E01,
    E02,
    E03,
    E04,
    E05,
    E06,
    E07,
    E08,
    E09,
    E10,
    E11,
    E12,
    E13,
    E14,
    E15,
    E16,
    E17,
    E18,
    E19,
    E20,
    E21,
    E22,
    E23,
    E24,
    E25,
    E26,
    E27,
    E28,
    E29,
    E30,
    E31,
    E32,
    E33,
    E34,
    E35,
    E36,
    E37,
    E38,
    E39,
    E40,
    E41,
    E42,
    E43,
    E44,
    E45,
    E46,
    E47,
    E48,
    E49,
    E50,
    E51,
    E52,
    E53,
    E54,
    E55,
    E56,
    E57,
    E58,
    E59,
    E60,
    E61,
    E62,
    E63,
    E64
  }
}
