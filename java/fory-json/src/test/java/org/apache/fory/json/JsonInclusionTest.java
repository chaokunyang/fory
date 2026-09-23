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

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertThrows;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.apache.fory.json.annotation.JsonAnyGetter;
import org.apache.fory.json.annotation.JsonCodec;
import org.apache.fory.json.annotation.JsonCreator;
import org.apache.fory.json.annotation.JsonIgnore;
import org.apache.fory.json.annotation.JsonInclude;
import org.apache.fory.json.annotation.JsonMixin;
import org.apache.fory.json.annotation.JsonProperty;
import org.apache.fory.json.annotation.JsonProperty.Include;
import org.apache.fory.json.annotation.JsonRawValue;
import org.apache.fory.json.annotation.JsonUnwrapped;
import org.apache.fory.json.codec.AbstractJsonValueCodec;
import org.apache.fory.json.reader.JsonReader;
import org.apache.fory.json.resolver.JsonTypeInfo;
import org.apache.fory.json.resolver.JsonTypeResolver;
import org.apache.fory.json.writer.JsonWriter;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;

public class JsonInclusionTest extends ForyJsonTestModels {
  @Factory(dataProvider = "enableCodegen")
  public JsonInclusionTest(boolean codegen) {
    super(codegen);
  }

  @Test
  public void dynamicEmptyValues() {
    ForyJson json = newJsonBuilder().defaultPropertyInclusion(Include.NON_EMPTY).build();
    Dynamic value = new Dynamic();
    Object[] empty = {
      null,
      "",
      new StringBuilder(),
      emptyList(),
      emptySet(),
      emptyMap(),
      new boolean[0],
      new byte[0],
      new short[0],
      new char[0],
      new int[0],
      new long[0],
      new float[0],
      new double[0],
      new String[0],
      Optional.empty(),
      OptionalInt.empty(),
      OptionalLong.empty(),
      OptionalDouble.empty()
    };
    for (Object item : empty) {
      value.value = item;
      assertJson(json, value, "{}");
    }
    Object[] present = {
      0,
      false,
      " ",
      singletonList(null),
      singletonList(emptyList()),
      Optional.of(emptyList()),
      OptionalInt.of(0),
      OptionalLong.of(0),
      OptionalDouble.of(0)
    };
    String[] encoded = {"0", "false", "\" \"", "[null]", "[[]]", "[]", "0", "0", "0.0"};
    for (int i = 0; i < present.length; i++) {
      value.value = present[i];
      assertJson(json, value, "{\"value\":" + encoded[i] + "}");
    }
    assertGeneratedWhenSupported(json, Dynamic.class, codegenEnabled());
  }

  @Test
  public void defaultInclusion() {
    assertThrows(
        IllegalArgumentException.class,
        () -> newJsonBuilder().defaultPropertyInclusion(Include.NON_DEFAULT));
    ForyJson json = newJson();
    Initialized value = new Initialized();
    int calls = Initialized.calls;
    assertJson(json, value, "{\"retained\":1}");
    assertJson(json, value, "{\"retained\":1}");
    assertEquals(Initialized.calls, calls + 1);
    value.count = 0;
    value.label = "漢";
    assertJson(json, value, "{\"count\":0,\"label\":\"漢\",\"retained\":1}");
    String pretty = "{\n  \"count\" : 0,\n  \"label\" : \"漢\",\n  \"retained\" : 1\n}";
    assertEquals(json.toPrettyJson(value), pretty);
    assertEquals(new String(json.toPrettyJsonBytes(value), StandardCharsets.UTF_8), pretty);
    assertEquals(Initialized.calls, calls + 1);
    Initialized first = json.fromJson("{}", Initialized.class);
    Initialized second = json.fromJson("{}".getBytes(StandardCharsets.UTF_8), Initialized.class);
    assertEquals(first.count, 3);
    assertNotSame(first.values, second.values);
    first.values.add(2);
    assertEquals(second.values, singletonList(1));
    ExplicitDefaults required = new ExplicitDefaults(0, null);
    assertJson(json, required, "{\"count\":0,\"label\":null}");
    String requiredPretty = "{\n  \"count\" : 0,\n  \"label\" : null\n}";
    assertEquals(json.toPrettyJson(required), requiredPretty);
    assertEquals(
        new String(json.toPrettyJsonBytes(required), StandardCharsets.UTF_8), requiredPretty);
    assertEquals(json.fromJson(requiredPretty, ExplicitDefaults.class).count, 0);
    assertEquals(
        json.fromJson(requiredPretty.getBytes(StandardCharsets.UTF_8), ExplicitDefaults.class)
            .label,
        null);
    assertGeneratedWhenSupported(json, ExplicitDefaults.class, codegenEnabled());
    assertJson(json, new RequiredConstructor(0), "{\"count\":0}");
    assertGeneratedWhenSupported(json, RequiredConstructor.class, codegenEnabled());
    assertGeneratedWhenSupported(json, Initialized.class, codegenEnabled());
  }

  @Test
  public void referenceConstruction() {
    ForyJson json = newJson();
    FailingDefault value = new FailingDefault();
    FailingDefault.fail = true;
    try {
      assertThrows(ForyJsonException.class, () -> json.toJson(value));
      assertThrows(ForyJsonException.class, () -> json.toJsonBytes(value));
    } finally {
      FailingDefault.fail = false;
    }
    PlainDefault plain = new PlainDefault();
    int calls = PlainDefault.calls;
    assertJson(newJson(), plain, "{\"count\":3}");
    assertEquals(PlainDefault.calls, calls);
    ForyJson mixed = newJsonBuilder().registerMixin(DefaultMixin.class).build();
    assertJson(mixed, plain, "{}");
    assertEquals(PlainDefault.calls, calls + 1);
    ForyJson overridden = newJsonBuilder().registerMixin(DefaultOverrideMixin.class).build();
    assertJson(overridden, new Initialized(), "{\"count\":3,\"retained\":1}");
  }

  @Test
  public void defaultNullCodec() {
    ForyJson json = newJson();
    NullDefault value = new NullDefault();
    assertJson(json, value, "{}");
    value.value = null;
    assertJson(json, value, "{\"value\":\"absent\"}");
    String pretty = "{\n  \"value\" : \"absent\"\n}";
    assertEquals(json.toPrettyJson(value), pretty);
    assertEquals(new String(json.toPrettyJsonBytes(value), StandardCharsets.UTF_8), pretty);
    assertGeneratedWhenSupported(json, NullDefault.class, codegenEnabled());
  }

  @Test
  public void writeOnlyDefaultGroup() {
    ForyJson json = newJson();
    WriteOnlyDefault value = new WriteOnlyDefault();
    assertJson(json, value, "{}");
    value.value = new Positions();
    value.value.a = "漢";
    assertJson(json, value, "{\"a\":\"漢\"}");
    String pretty = "{\n  \"a\" : \"漢\"\n}";
    assertEquals(json.toPrettyJson(value), pretty);
    assertEquals(new String(json.toPrettyJsonBytes(value), StandardCharsets.UTF_8), pretty);
    assertGeneratedWhenSupported(json, WriteOnlyDefault.class, codegenEnabled());
  }

  @Test
  public void dynamicCodecCache() throws Exception {
    ForyJson json =
        newJsonBuilder()
            .defaultPropertyInclusion(Include.NON_EMPTY)
            .registerCodec(EmptyObject.class, new EmptyObjectCodec())
            .build();
    JsonTypeResolver resolver = JsonTestSupport.currentTypeResolver(json);
    Field runtimeTypes = JsonTypeResolver.class.getDeclaredField("runtimeTypeInfos");
    runtimeTypes.setAccessible(true);
    RuntimeLookups lookups = new RuntimeLookups();
    runtimeTypes.set(resolver, lookups);
    Dynamic value = new Dynamic();
    value.value = new EmptyObject();
    assertJson(json, value, "{\"value\":\"\"}");
    String pretty = "{\n  \"value\" : \"\"\n}";
    assertEquals(json.toPrettyJson(value), pretty);
    assertEquals(new String(json.toPrettyJsonBytes(value), StandardCharsets.UTF_8), pretty);
    assertEquals(lookups.calls, 1);
    JsonTypeInfo natural = resolver.getTypeInfo(Object.class, Object.class);
    JsonTypeResolver other = new JsonTypeResolver(resolver.sharedRegistry());
    assertNotSame(natural.valueCodec(), other.getTypeInfo(Object.class, Object.class).valueCodec());

    value.value = "different";
    assertJson(json, value, "{\"value\":\"different\"}");
    value.value = new EmptyObject();
    assertJson(json, value, "{\"value\":\"\"}");
    assertEquals(lookups.calls, 2);
    FailingDefault.fail = true;
    try {
      assertThrows(ForyJsonException.class, () -> resolver.getObjectCodec(FailingDefault.class));
    } finally {
      FailingDefault.fail = false;
    }
    assertJson(json, value, "{\"value\":\"\"}");
    assertEquals(lookups.calls, 3);
    assertGeneratedWhenSupported(json, Dynamic.class, codegenEnabled());
  }

  private static final class RuntimeLookups extends IdentityHashMap<Class<?>, JsonTypeInfo> {
    private int calls;

    @Override
    public JsonTypeInfo get(Object type) {
      if (type == EmptyObject.class) {
        calls++;
      }
      return super.get(type);
    }
  }

  public static final class NullDefault {
    @JsonProperty(include = Include.NON_DEFAULT)
    @JsonCodec(NullTextCodec.class)
    public String value = "initial";
  }

  public static final class NullTextCodec extends AbstractJsonValueCodec<String> {
    @Override
    public void write(JsonWriter writer, String value) {
      writer.writeString(value == null ? "absent" : value);
    }

    @Override
    public String read(JsonReader reader) {
      return reader.readString();
    }
  }

  public static final class WriteOnlyDefault {
    @JsonUnwrapped
    @JsonIgnore(ignoreRead = true, ignoreWrite = false)
    @JsonProperty(include = Include.NON_DEFAULT)
    public Positions value;
  }

  @JsonInclude(Include.NON_DEFAULT)
  public static class Initialized {
    public static int calls;
    public int count = 3;
    public String label;
    public List<Integer> values = new java.util.ArrayList<>(singletonList(1));

    @JsonProperty(include = Include.ALWAYS)
    public int retained = 1;

    public Initialized() {
      calls++;
    }
  }

  @JsonInclude(Include.NON_DEFAULT)
  public static class ExplicitDefaults {
    public final int count;
    public final String label;

    public ExplicitDefaults() {
      this(3, "default");
    }

    @JsonCreator
    public ExplicitDefaults(@JsonProperty("count") int count, @JsonProperty("label") String label) {
      this.count = count;
      this.label = label;
    }
  }

  public static class RequiredConstructor {
    @JsonProperty(include = Include.NON_DEFAULT)
    public int count;

    public RequiredConstructor(int count) {
      this.count = count;
    }
  }

  @Test
  public void requiredMixin() {
    ForyJson json = newJsonBuilder().registerMixin(RequiredMixin.class).build();
    ConstructorOnly value = new ConstructorOnly(0);
    int calls = ConstructorOnly.calls;
    assertJson(json, value, "{\"count\":0}");
    assertEquals(json.toPrettyJson(value), "{\n  \"count\" : 0\n}");
    assertEquals(
        new String(json.toPrettyJsonBytes(value), StandardCharsets.UTF_8),
        json.toPrettyJson(value));
    assertEquals(ConstructorOnly.calls, calls);
    assertGeneratedWhenSupported(json, ConstructorOnly.class, codegenEnabled());
  }

  public static class ConstructorOnly {
    public static int calls;
    public int count;

    public ConstructorOnly(int count) {
      calls++;
      this.count = count;
    }
  }

  @JsonMixin(target = ConstructorOnly.class)
  @JsonInclude(Include.NON_DEFAULT)
  public abstract static class RequiredMixin {}

  public static class FailingDefault {
    public static boolean fail;

    @JsonProperty(include = Include.NON_DEFAULT)
    public int value = 1;

    public FailingDefault() {
      if (fail) throw new IllegalStateException("constructor failed");
    }
  }

  public static class PlainDefault {
    public static int calls;
    public int count = 3;

    public PlainDefault() {
      calls++;
    }
  }

  @JsonMixin(target = PlainDefault.class)
  @JsonInclude(Include.NON_DEFAULT)
  public abstract static class DefaultMixin {}

  @JsonMixin(target = Initialized.class)
  public abstract static class DefaultOverrideMixin {
    @JsonProperty(include = Include.ALWAYS)
    public int count;
  }

  @Test
  public void propertyOverrides() {
    Overrides value = new Overrides();
    assertJson(newJson(), value, "{\"always\":[],\"defaults\":[],\"nonNull\":[]}");
    assertJson(
        newJsonBuilder().defaultPropertyInclusion(Include.NON_EMPTY).build(),
        value,
        "{\"always\":[],\"nonNull\":[]}");
    value.always = null;
    value.nonNull = null;
    assertJson(
        newJsonBuilder().defaultPropertyInclusion(Include.NON_EMPTY).build(),
        value,
        "{\"always\":null}");
    assertJson(
        newJsonBuilder().defaultPropertyInclusion(Include.NON_EMPTY).writeNullFields(true).build(),
        value,
        "{\"always\":null,\"defaults\":[]}");
    assertJson(
        newJsonBuilder().writeNullFields(true).defaultPropertyInclusion(Include.NON_EMPTY).build(),
        value,
        "{\"always\":null}");
    assertJson(
        newJsonBuilder().defaultPropertyInclusion(Include.NON_EMPTY).writeNullFields(false).build(),
        value,
        "{\"always\":null,\"defaults\":[]}");
    assertThrows(
        IllegalArgumentException.class,
        () -> newJsonBuilder().defaultPropertyInclusion(Include.DEFAULT));
    assertThrows(NullPointerException.class, () -> newJsonBuilder().defaultPropertyInclusion(null));
  }

  @Test
  public void conditionalFieldPositions() {
    ForyJson json = newJsonBuilder().defaultPropertyInclusion(Include.NON_EMPTY).build();
    Positions value = new Positions();
    String[] expected = {
      "{}",
      "{\"a\":\"a\"}",
      "{\"b\":[\"b\"]}",
      "{\"a\":\"a\",\"b\":[\"b\"]}",
      "{\"c\":{\"x\":\"c\"}}",
      "{\"a\":\"a\",\"c\":{\"x\":\"c\"}}",
      "{\"b\":[\"b\"],\"c\":{\"x\":\"c\"}}",
      "{\"a\":\"a\",\"b\":[\"b\"],\"c\":{\"x\":\"c\"}}"
    };
    for (int i = 0; i < expected.length; i++) {
      value.a = (i & 1) == 0 ? "" : "a";
      value.b = (i & 2) == 0 ? emptyList() : singletonList("b");
      value.c = (i & 4) == 0 ? emptyMap() : singletonMap("x", "c");
      assertJson(json, value, expected[i]);
    }
    assertGeneratedWhenSupported(json, Positions.class, codegenEnabled());
  }

  @Test
  public void declaredEmptyValues() {
    ForyJson json = newJsonBuilder().defaultPropertyInclusion(Include.NON_EMPTY).build();
    Typed value = new Typed();
    assertJson(json, value, "{\"no\":false,\"zero\":0}");
    value.bytes = new byte[] {1};
    value.optional = Optional.of(emptyList());
    value.raw = "[]";
    assertJson(
        json, value, "{\"bytes\":\"AQ==\",\"no\":false,\"optional\":[],\"raw\":[],\"zero\":0}");
    assertGeneratedWhenSupported(json, Typed.class, codegenEnabled());
  }

  @Test
  public void getterRunsOnce() {
    ForyJson json = newJson();
    Getter value = new Getter();
    assertEquals(json.toJson(value), "{}");
    assertEquals(value.calls, 1);
    value.calls = 0;
    assertEquals(new String(json.toJsonBytes(value), StandardCharsets.UTF_8), "{}");
    assertEquals(value.calls, 1);
  }

  @Test
  public void enumCharSequence() {
    ForyJson json = newJsonBuilder().defaultPropertyInclusion(Include.NON_EMPTY).build();
    EnumValue value = new EnumValue();
    assertJson(json, value, "{}");
    value.value = TextEnum.PRESENT;
    assertJson(json, value, "{\"value\":\"PRESENT\"}");
    assertGeneratedWhenSupported(json, EnumValue.class, codegenEnabled());
  }

  @Test
  public void customRepresentation() {
    ForyJson json = newJsonBuilder().defaultPropertyInclusion(Include.NON_EMPTY).build();
    Custom value = new Custom();
    assertJson(json, value, "{\"object\":\"\"}");
    value.list = singletonList("x");
    assertJson(json, value, "{\"list\":\"custom\",\"object\":\"\"}");
    value.object.empty = true;
    assertJson(json, value, "{\"list\":\"custom\"}");
    assertEquals(json.toPrettyJson(value), "{\n  \"list\" : \"custom\"\n}");
    assertEquals(
        new String(json.toPrettyJsonBytes(value), StandardCharsets.UTF_8),
        json.toPrettyJson(value));
    assertGeneratedWhenSupported(json, Custom.class, codegenEnabled());
    CustomDynamic dynamic = new CustomDynamic();
    assertJson(json, dynamic, "{\"value\":\"custom\"}");
    String pretty = "{\n  \"value\" : \"custom\"\n}";
    assertEquals(json.toPrettyJson(dynamic), pretty);
    assertEquals(new String(json.toPrettyJsonBytes(dynamic), StandardCharsets.UTF_8), pretty);
    assertGeneratedWhenSupported(json, CustomDynamic.class, codegenEnabled());
  }

  @Test
  public void rootAndContents() {
    ForyJson json = newJsonBuilder().defaultPropertyInclusion(Include.NON_EMPTY).build();
    assertJson(json, emptyList(), "[]");
    assertJson(json, Arrays.asList(emptyList(), null), "[[],null]");
    assertJson(json, singletonMap("items", emptyList()), "{\"items\":[]}");
  }

  @Test
  public void mixinAndUnwrapped() {
    ForyJson json = newJsonBuilder().registerMixin(PositionsMixin.class).build();
    Positions value = new Positions();
    value.a = "";
    value.b = emptyList();
    value.c = emptyMap();
    assertJson(json, value, "{\"a\":\"\",\"c\":{}}");
    Flattened flattened = new Flattened();
    flattened.value = value;
    assertJson(json, flattened, "{\"a\":\"\",\"c\":{}}");
    ForyJson empty = newJsonBuilder().defaultPropertyInclusion(Include.NON_EMPTY).build();
    assertJson(empty, flattened, "{}");
    assertJson(empty, new AnyValues(), "{\"items\":[]}");
  }

  private static void assertJson(ForyJson json, Object value, String expected) {
    assertEquals(json.toJson(value), expected);
    assertEquals(new String(json.toJsonBytes(value), StandardCharsets.UTF_8), expected);
  }

  public static final class Dynamic {
    public Object value;
  }

  public static final class EnumValue {
    public TextEnum value = TextEnum.EMPTY;
  }

  public enum TextEnum implements CharSequence {
    EMPTY(""),
    PRESENT("text");
    private final String text;

    TextEnum(String text) {
      this.text = text;
    }

    @Override
    public int length() {
      return text.length();
    }

    @Override
    public char charAt(int index) {
      return text.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
      return text.subSequence(start, end);
    }
  }

  public static final class Overrides {
    @JsonProperty(include = Include.ALWAYS)
    public List<String> always = emptyList();

    public List<String> defaults = emptyList();

    @JsonProperty(include = Include.NON_EMPTY)
    public List<String> nonEmpty = emptyList();

    @JsonProperty(include = Include.NON_NULL)
    public List<String> nonNull = emptyList();
  }

  public static final class Positions {
    public String a;
    public List<String> b;
    public Map<String, String> c;
  }

  @JsonMixin(target = Positions.class)
  public abstract static class PositionsMixin {
    @JsonProperty(include = Include.NON_EMPTY)
    public List<String> b;
  }

  public static final class Flattened {
    @JsonUnwrapped public Positions value;
  }

  public static final class AnyValues {
    public String a = "";

    @JsonAnyGetter
    public Map<String, List<String>> values() {
      return singletonMap("items", emptyList());
    }

    public String z = "";
  }

  public static final class Typed {
    public byte[] bytes = new byte[0];
    public CharSequence chars = "";
    public int[] ints = new int[0];
    public boolean no;
    public Optional<List<String>> optional = Optional.empty();
    public OptionalDouble optionalDouble = OptionalDouble.empty();
    public OptionalInt optionalInt = OptionalInt.empty();
    public OptionalLong optionalLong = OptionalLong.empty();
    @JsonRawValue public String raw = "";
    public String[] strings = new String[0];
    public int zero;
  }

  public static final class Getter {
    @JsonIgnore public int calls;

    @JsonProperty(include = Include.NON_EMPTY)
    public List<String> getItems() {
      return calls++ == 0 ? emptyList() : singletonList("changed");
    }
  }

  public static final class Custom {
    @JsonCodec(ListCodec.class)
    public List<String> list = emptyList();

    @JsonCodec(EmptyObjectCodec.class)
    public EmptyObject object = new EmptyObject();
  }

  public static final class EmptyObject {
    public boolean empty;
  }

  public static final class CustomDynamic {
    @JsonCodec(RepresentationCodec.class)
    public Object value = new RequiredConstructor(1);
  }

  public static final class RepresentationCodec extends AbstractJsonValueCodec<Object> {
    @Override
    public void write(JsonWriter writer, Object value) {
      writer.writeString("custom");
    }

    @Override
    public Object read(JsonReader reader) {
      return reader.readString();
    }
  }

  public static final class ListCodec extends AbstractJsonValueCodec<List<String>> {
    @Override
    public boolean isEmpty(JsonWriter writer, List<String> value) {
      throw new AssertionError("Collection emptiness must use the built-in fast path");
    }

    @Override
    public void write(JsonWriter writer, List<String> value) {
      writer.writeString("custom");
    }

    @Override
    public List<String> read(JsonReader reader) {
      return singletonList(reader.readString());
    }
  }

  public static final class EmptyObjectCodec extends AbstractJsonValueCodec<EmptyObject> {
    @Override
    public boolean isEmpty(JsonWriter writer, EmptyObject value) {
      return value.empty;
    }

    @Override
    public void write(JsonWriter writer, EmptyObject value) {
      writer.writeString("");
    }

    @Override
    public EmptyObject read(JsonReader reader) {
      reader.readString();
      return new EmptyObject();
    }
  }
}
