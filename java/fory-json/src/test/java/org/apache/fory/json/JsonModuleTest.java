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

package org.apache.fory.json;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.fory.json.codec.AbstractJsonValueCodec;
import org.apache.fory.json.codec.CompositeJsonCodec;
import org.apache.fory.json.codec.JsonValueCodec;
import org.apache.fory.json.reader.JsonReader;
import org.apache.fory.json.reader.Latin1JsonReader;
import org.apache.fory.json.reader.Utf16JsonReader;
import org.apache.fory.json.reader.Utf8JsonReader;
import org.apache.fory.json.resolver.JsonTypeInfo;
import org.apache.fory.json.resolver.JsonTypeResolver;
import org.apache.fory.json.resolver.UnsupportedJsonTypeException;
import org.apache.fory.json.writer.JsonWriter;
import org.apache.fory.json.writer.StringJsonWriter;
import org.apache.fory.json.writer.Utf8JsonWriter;
import org.apache.fory.reflect.TypeRef;
import org.testng.annotations.Test;

public class JsonModuleTest {
  @Test
  public void installsOnceAndFreezesContext() {
    AtomicInteger installs = new AtomicInteger();
    ModuleContext[] retained = new ModuleContext[1];
    ForyJsonModule module =
        context -> {
          installs.incrementAndGet();
          retained[0] = context;
          context.registerCodec(Text.class, new TextCodec("module:"));
        };
    ForyJson json = ForyJson.builder().withModule(module).withModule(module).build();
    assertEquals(installs.get(), 1);
    assertEquals(json.toJson(new Text("value")), "\"module:value\"");
    assertThrows(
        IllegalStateException.class,
        () -> retained[0].registerCodec(Text.class, new TextCodec("late:")));
  }

  @Test
  public void installationIsTransactional() {
    AtomicBoolean fail = new AtomicBoolean(true);
    ForyJsonModule module =
        context -> {
          context.registerCodec(Text.class, new TextCodec("module:"));
          if (fail.getAndSet(false)) {
            throw new IllegalStateException("failed install");
          }
        };
    ForyJsonBuilder builder = ForyJson.builder().withModule(module);
    assertThrows(IllegalStateException.class, builder::build);
    ForyJson json = builder.build();
    assertEquals(json.toJson(new Text("value")), "\"module:value\"");
  }

  @Test
  public void applicationExactCodecWins() {
    ForyJsonModule module = context -> context.registerCodec(Text.class, new TextCodec("module:"));
    ForyJson json =
        ForyJson.builder()
            .withModule(module)
            .registerCodec(Text.class, new TextCodec("application:"))
            .build();
    assertEquals(json.toJson(new Text("value")), "\"application:value\"");
  }

  @Test
  public void duplicateModuleKeyFails() {
    ForyJsonModule first = new KeyedModule("same");
    ForyJsonModule second = new KeyedModule("same");
    ForyJsonBuilder builder = ForyJson.builder().withModule(first).withModule(second);
    assertThrows(IllegalArgumentException.class, builder::build);
  }

  @Test
  public void conflictingFactoriesIgnoreOrder() {
    ForyJsonModule first = new FactoryModule("first", new TextCodec("first:"), false);
    ForyJsonModule second = new FactoryModule("second", null, true);
    ForyJson left = ForyJson.builder().withModule(first).withModule(second).build();
    ForyJson right = ForyJson.builder().withModule(second).withModule(first).build();
    assertThrows(ForyJsonException.class, () -> left.toJson(new Family("value")));
    assertThrows(ForyJsonException.class, () -> right.toJson(new Family("value")));
  }

  @Test
  public void recursiveCompositeRollsBack() {
    AtomicBoolean fail = new AtomicBoolean(true);
    AtomicInteger creations = new AtomicInteger();
    JsonCodecFactory factory =
        (type, resolver) -> {
          if (type.getRawType() != RecursiveValue.class) {
            return null;
          }
          creations.incrementAndGet();
          return new RecursiveCodec(fail);
        };
    ForyJsonModule module = context -> context.registerCodecFactory(factory);
    ForyJson json = ForyJson.builder().withModule(module).withCodegen(false).build();
    assertThrows(
        ForyJsonException.class, () -> json.fromJson("{\"next\":null}", RecursiveValue.class));
    RecursiveValue value = json.fromJson("{\"next\":{\"next\":null}}", RecursiveValue.class);
    assertEquals(json.toJson(value), "{\"next\":{\"next\":null}}");
    assertEquals(creations.get(), 2);
  }

  private static final class KeyedModule implements ForyJsonModule {
    private final String key;

    private KeyedModule(String key) {
      this.key = key;
    }

    @Override
    public String moduleKey() {
      return key;
    }

    @Override
    public void install(ModuleContext context) {}
  }

  private static final class FactoryModule implements ForyJsonModule {
    private final String key;
    private final JsonValueCodec<?> codec;
    private final boolean reject;

    private FactoryModule(String key, JsonValueCodec<?> codec, boolean reject) {
      this.key = key;
      this.codec = codec;
      this.reject = reject;
    }

    @Override
    public String moduleKey() {
      return key;
    }

    @Override
    public void install(ModuleContext context) {
      context.registerCodecFactory(
          new JsonCodecFactory() {
            @Override
            public JsonValueCodec<?> create(TypeRef<?> type, JsonTypeResolver resolver) {
              if (type.getRawType() != Family.class) {
                return null;
              }
              if (reject) {
                throw new UnsupportedJsonTypeException("rejected by " + key);
              }
              return codec;
            }

            @Override
            public String factoryKey() {
              return key;
            }
          });
    }
  }

  private static final class TextCodec extends AbstractJsonValueCodec<Text> {
    private final String prefix;

    private TextCodec(String prefix) {
      this.prefix = prefix;
    }

    @Override
    public void write(JsonWriter writer, Text value) {
      if (value == null) {
        writer.writeNull();
      } else {
        writer.writeString(prefix + value.value);
      }
    }

    @Override
    public Text read(JsonReader reader) {
      return reader.tryReadNullToken() ? null : new Text(reader.readString());
    }
  }

  private static final class RecursiveCodec implements CompositeJsonCodec<RecursiveValue> {
    private final AtomicBoolean fail;
    private JsonTypeInfo self;

    private RecursiveCodec(AtomicBoolean fail) {
      this.fail = fail;
    }

    @Override
    public void resolveTypes(TypeRef<?> type, JsonTypeResolver resolver) {
      self = resolver.getTypeInfo(type.getType(), type.getRawType());
      if (fail.getAndSet(false)) {
        throw new ForyJsonException("forced recursive resolution failure");
      }
    }

    @Override
    public void writeString(StringJsonWriter writer, RecursiveValue value) {
      if (value == null) {
        writer.writeNull();
        return;
      }
      writer.writeObjectStart();
      writer.writeFieldName("next");
      self.stringWriter().writeString(writer, value.next);
      writer.writeObjectEnd();
    }

    @Override
    public void writeUtf8(Utf8JsonWriter writer, RecursiveValue value) {
      if (value == null) {
        writer.writeNull();
        return;
      }
      writer.writeObjectStart();
      writer.writeFieldName("next");
      self.utf8Writer().writeUtf8(writer, value.next);
      writer.writeObjectEnd();
    }

    @Override
    public RecursiveValue readLatin1(Latin1JsonReader reader) {
      if (reader.tryReadNullToken()) {
        return null;
      }
      reader.enterDepth();
      reader.expectNextToken('{');
      expectNext(reader.readFieldName());
      reader.expectNextToken(':');
      RecursiveValue result =
          new RecursiveValue((RecursiveValue) self.latin1Reader().readLatin1(reader));
      expectEnd(reader.consumeNextCommaOrEndObject());
      reader.exitDepth();
      return result;
    }

    @Override
    public RecursiveValue readUtf16(Utf16JsonReader reader) {
      if (reader.tryReadNullToken()) {
        return null;
      }
      reader.enterDepth();
      reader.expectNextToken('{');
      expectNext(reader.readFieldName());
      reader.expectNextToken(':');
      RecursiveValue result =
          new RecursiveValue((RecursiveValue) self.utf16Reader().readUtf16(reader));
      expectEnd(reader.consumeNextCommaOrEndObject());
      reader.exitDepth();
      return result;
    }

    @Override
    public RecursiveValue readUtf8(Utf8JsonReader reader) {
      if (reader.tryReadNullToken()) {
        return null;
      }
      reader.enterDepth();
      reader.expectNextToken('{');
      expectNext(reader.readFieldName());
      reader.expectNextToken(':');
      RecursiveValue result =
          new RecursiveValue((RecursiveValue) self.utf8Reader().readUtf8(reader));
      expectEnd(reader.consumeNextCommaOrEndObject());
      reader.exitDepth();
      return result;
    }

    private static void expectNext(String name) {
      if (!"next".equals(name)) {
        throw new ForyJsonException("Expected recursive next property");
      }
    }

    private static void expectEnd(boolean hasMore) {
      if (hasMore) {
        throw new ForyJsonException("Expected one recursive property");
      }
    }
  }

  private static final class Text {
    private final String value;

    private Text(String value) {
      this.value = value;
    }
  }

  private static final class Family {
    private final String value;

    private Family(String value) {
      this.value = value;
    }
  }

  private static final class RecursiveValue {
    private final RecursiveValue next;

    private RecursiveValue(RecursiveValue next) {
      this.next = next;
    }
  }
}
