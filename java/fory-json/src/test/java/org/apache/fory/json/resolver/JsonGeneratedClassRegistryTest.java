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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.apache.fory.json.codec.GeneratedJsonCodec;
import org.apache.fory.json.codec.GeneratedJsonCodec.GeneratedMapKey;
import org.apache.fory.json.codec.MapKeyCodec;
import org.apache.fory.json.meta.JsonFieldAccessor;
import org.apache.fory.reflect.TypeRef;
import org.testng.annotations.Test;

public class JsonGeneratedClassRegistryTest {
  @Test
  public void rejectSignatureCollision() {
    Map<String, String> signatures = new HashMap<>();
    JsonGeneratedClassRegistry.mergeSignatures(
        signatures, Collections.singletonMap("example.Generated", "complete-signature-a"));
    JsonGeneratedClassRegistry.mergeSignatures(
        signatures, Collections.singletonMap("example.Generated", "complete-signature-a"));
    assertEquals(signatures.get("example.Generated"), "complete-signature-a");

    expectThrows(
        IllegalStateException.class,
        () ->
            JsonGeneratedClassRegistry.mergeSignatures(
                signatures, Collections.singletonMap("example.Generated", "complete-signature-b")));
  }

  @Test
  public void mergeSourceCapabilities() {
    TypeRef<?> type = TypeRef.of(String.class);
    Map<TypeRef<?>, GeneratedJsonCodec<?>> codecs = new HashMap<>();
    Set<Class<?>> added = new LinkedHashSet<>();
    JsonGeneratedClassRegistry.mergeSourceCodecs(
        Collections.singletonMap(type, new SourceCodec()), codecs, added);
    JsonGeneratedClassRegistry.mergeSourceCodecs(
        Collections.singletonMap(type, new SourceCodec()), codecs, added);
    assertEquals(codecs.get(type).getClass(), SourceCodec.class);
    assertEquals(added, Collections.singleton(SourceCodec.class));
    expectThrows(
        IllegalStateException.class,
        () ->
            JsonGeneratedClassRegistry.mergeSourceCodecs(
                Collections.singletonMap(type, new OtherSourceCodec()), codecs, added));

    Map<TypeRef<?>, GeneratedMapKey> mapKeys = new HashMap<>();
    JsonGeneratedClassRegistry.mergeSourceMapKeys(
        Collections.singletonMap(
            type, new GeneratedMapKey(new StringKeyCodec(), new Class<?>[] {String.class})),
        mapKeys);
    JsonGeneratedClassRegistry.mergeSourceMapKeys(
        Collections.singletonMap(
            type, new GeneratedMapKey(new StringKeyCodec(), new Class<?>[] {String.class})),
        mapKeys);
    expectThrows(
        IllegalStateException.class,
        () ->
            JsonGeneratedClassRegistry.mergeSourceMapKeys(
                Collections.singletonMap(
                    type,
                    new GeneratedMapKey(
                        new StringKeyCodec(), new Class<?>[] {String.class, Object.class})),
                mapKeys));
    expectThrows(
        IllegalStateException.class,
        () ->
            JsonGeneratedClassRegistry.mergeSourceMapKeys(
                Collections.singletonMap(
                    type, new GeneratedMapKey(new OtherKeyCodec(), new Class<?>[] {String.class})),
                mapKeys));
  }

  private static class SourceCodec extends GeneratedJsonCodec<String> {
    @Override
    public Class<String> type() {
      return String.class;
    }

    @Override
    public JsonFieldAccessor[] fieldAccessors() {
      return new JsonFieldAccessor[0];
    }
  }

  private static final class OtherSourceCodec extends SourceCodec {}

  private static class StringKeyCodec implements MapKeyCodec {
    @Override
    public String toName(Object key) {
      return (String) key;
    }

    @Override
    public Object fromName(String name) {
      return name;
    }
  }

  private static final class OtherKeyCodec extends StringKeyCodec {}
}
