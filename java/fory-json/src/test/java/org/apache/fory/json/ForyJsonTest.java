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
import static org.testng.Assert.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Currency;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Queue;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.apache.fory.json.annotation.JsonIgnore;
import org.apache.fory.platform.JdkVersion;
import org.apache.fory.reflect.TypeRef;
import org.apache.fory.serializer.StringSerializer;
import org.testng.SkipException;
import org.testng.annotations.Test;

public class ForyJsonTest {
  private static final String TWO_BYTE_TEXT = "\u0100\u07ff\u03a9";
  private static final String THREE_BYTE_TEXT = "\u0800\u20ac\u4f60\ud7ff\ue000";
  private static final String SUPPLEMENTARY_TEXT = "\uD834\uDD1E\uD83D\uDE00\uD83C\uDF0D";
  private static final String MIXED_SCRIPT_TEXT =
      "\u0100\u03a9\u0416\u05d0\u0627\u0905\u0e01\u4f60";
  private static final String COMBINING_TEXT = "e\u0301\u200d\uD83D\uDCBB";
  private static final String ZH_TEXT = "你好，Fory";
  private static final String EU_TEXT = "café crème Österreich € ČšŽ";

  public enum Kind {
    FAST,
    SMALL
  }

  public static final class PublicFields {
    public boolean active = true;
    public int id = 7;
    public String name = "fory";
    public String missing;
  }

  public static final class FirstIntField {
    public int count = 2;
    public String name = "first";
  }

  public static final class UnicodeFieldNames {
    public String café = EU_TEXT;
    public String 你好 = ZH_TEXT;
  }

  public static final class MethodsIgnored {
    public int setterCalls;
    public String value = "field";
    private String hidden = "hidden";

    public String getHidden() {
      return hidden;
    }

    public String getValue() {
      return "getter";
    }

    public void setValue(String value) {
      setterCalls++;
      this.value = "setter:" + value;
    }
  }

  public static final class PrivateFields {
    private static String staticValue = "static";
    private transient String transientValue = "transient";
    private int id = 11;
    private String name = "private";
    private String nullable;
  }

  public static final class DirectionalIgnore {
    @JsonIgnore public int both = 1;

    @JsonIgnore(ignoreRead = true, ignoreWrite = false)
    public int writeOnly = 2;

    @JsonIgnore(ignoreRead = false, ignoreWrite = true)
    public int readOnly = 3;
  }

  public static final class Nested {
    public Kind kind = Kind.FAST;
    public List<String> names = Arrays.asList("a", "b");
    public Map<String, Integer> scores = scores();
  }

  public static final class BoxedScalars {
    public Boolean bool = true;
    public Byte byteValue = Byte.valueOf((byte) 2);
    public Character charValue = Character.valueOf('x');
    public Double doubleValue = Double.valueOf(2.5);
    public Float floatValue = Float.valueOf(1.5f);
    public Integer intValue = Integer.valueOf(4);
    public Long longValue = Long.valueOf(5);
    public Short shortValue = Short.valueOf((short) 3);
  }

  public static final class NaturalValues {
    public Object bool = Boolean.TRUE;
    public Object list = Arrays.asList("a", Integer.valueOf(1), Boolean.FALSE);
    public Object map = values();
    public Object number = Integer.valueOf(7);
    public Object text = "fory";
  }

  public static final class NaturalObjectValue {
    public Object value = new Object();
  }

  public static class ParentValue {
    public int parent = 1;
  }

  public static final class ChildValue extends ParentValue {
    public int child = 2;
  }

  public static final class DeclaredParentField {
    public ParentValue value = new ChildValue();
  }

  public static final class TokenValues {
    public int count = 1;
    public String name = "alpha";
    public List<String> tags = Arrays.asList("x", "y");
    public long total = 2;
  }

  public static final class TokenGroup {
    public List<TokenValues> values;
  }

  public static final class CharValue {
    public char value;
  }

  public static final class CoreScalarFields {
    public AtomicInteger atomicInt = new AtomicInteger(7);
    public BigDecimal bigDecimal = new BigDecimal("12345.6789");
    public BigInteger bigInteger = new BigInteger("12345678901234567890");
    public StringBuilder builder = new StringBuilder("build");
    public ByteBuffer bytes = ByteBuffer.wrap(new byte[] {1, -2, 3});
    public Calendar calendar = calendar(123456789L);
    public Charset charset = StandardCharsets.UTF_8;
    public Currency currency = Currency.getInstance("EUR");
    public LocalDate date = LocalDate.of(2026, 6, 21);
    public Instant instant = Instant.parse("2026-06-21T01:02:03Z");
    public Locale locale = Locale.forLanguageTag("zh-Hans-CN");
    public Optional<String> maybe = Optional.of("yes");
    public OptionalInt optionalInt = OptionalInt.of(4);
    public TimeZone timeZone = TimeZone.getTimeZone("UTC");
    public Class<?> type = PublicFields.class;
    public URI uri = URI.create("https://fory.apache.org/json");
    public URL url = url("https://fory.apache.org/");
    public UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
  }

  public static final class MapKeyFields {
    public Map<Integer, String> intNames = intNames();
    public EnumMap<Kind, Integer> scores = enumScores();
  }

  public static final class FastContainers {
    public List<Boolean> booleans = Arrays.asList(Boolean.TRUE, Boolean.FALSE);
    public Map<String, Boolean> flags = flags();
    public Map<Integer, String> intNames = intNames();
    public List<Integer> ints = Arrays.asList(1, 2);
    public List<String> names = Arrays.asList("alpha", ZH_TEXT);
    public Map<String, Integer> scores = scores();
  }

  public static final class GeneratedCollectionFields {
    public EnumSet<Kind> kinds = EnumSet.of(Kind.FAST, Kind.SMALL);
    public Set<String> names = new LinkedHashSet<>(Arrays.asList("alpha", ZH_TEXT));
    public Queue<Integer> numbers = new ArrayDeque<>(Arrays.asList(1, 2));
  }

  public static final class UnicodeValues {
    public String first = "\u1234";
    public String second = "music \uD834\uDD1E";
    public List<String> tags = Arrays.asList("latin", "\u1234", "\uD83D\uDE00");
  }

  public static final class UnicodeMatrix {
    public Character boxedChar = Character.valueOf('\u20ac');
    public char charThreeByte = '\u4f60';
    public char charTwoByte = '\u0100';
    public char[] chars = {'\u0100', '\u07ff', '\u0800', '\u20ac', '\u4f60'};
    public String combining = COMBINING_TEXT;
    public String eu = EU_TEXT;
    public String mixedScripts = MIXED_SCRIPT_TEXT;
    public String supplementary = SUPPLEMENTARY_TEXT;
    public String threeByte = THREE_BYTE_TEXT;
    public String twoByte = TWO_BYTE_TEXT;
    public Map<String, String> valueMap = unicodeMap();
    public List<String> values =
        Arrays.asList(
            TWO_BYTE_TEXT,
            THREE_BYTE_TEXT,
            SUPPLEMENTARY_TEXT,
            MIXED_SCRIPT_TEXT,
            COMBINING_TEXT,
            ZH_TEXT,
            EU_TEXT);
    public String zh = ZH_TEXT;
  }

  public static final class RecursiveParent {
    public RecursiveChild child = new RecursiveChild();
    public String name = "parent";
  }

  public static final class RecursiveChild {
    public String name = "child";
    public RecursiveParent parent;
  }

  private static TokenValues tokenValue(int count, String name, List<String> tags, long total) {
    TokenValues value = new TokenValues();
    value.count = count;
    value.name = name;
    value.tags = tags;
    value.total = total;
    return value;
  }

  @Test
  public void writePublicFields() {
    ForyJson json = ForyJson.builder().build();
    assertEquals(json.toJson(new PublicFields()), "{\"active\":true,\"id\":7,\"name\":\"fory\"}");
    assertEquals(
        new String(json.toJsonBytes(new PublicFields()), StandardCharsets.UTF_8),
        "{\"active\":true,\"id\":7,\"name\":\"fory\"}");
  }

  @Test
  public void writeFirstIntGenerated() {
    ForyJson json = ForyJson.builder().build();
    String expected = "{\"count\":2,\"name\":\"first\"}";
    assertEquals(json.toJson(new FirstIntField()), expected);
    assertEquals(
        new String(json.toJsonBytes(new FirstIntField()), StandardCharsets.UTF_8), expected);
    assertEquals(json.hasGeneratedWriter(FirstIntField.class), true);
  }

  @Test
  public void sharedFacadeThreads() throws Exception {
    ForyJson json = ForyJson.builder().build();
    String expected = "{\"active\":true,\"id\":7,\"name\":\"fory\"}";
    int threads = 8;
    int iterations = 200;
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<?>> futures = new ArrayList<>();
    try {
      for (int t = 0; t < threads; t++) {
        futures.add(
            executor.submit(
                () -> {
                  start.await();
                  for (int i = 0; i < iterations; i++) {
                    assertEquals(json.toJson(new PublicFields()), expected);
                    assertEquals(
                        new String(json.toJsonBytes(new PublicFields()), StandardCharsets.UTF_8),
                        expected);
                    PublicFields value = json.fromJson(expected, PublicFields.class);
                    assertEquals(value.name, "fory");
                    assertEquals(value.id, 7);
                    assertEquals(value.active, true);
                  }
                  return null;
                }));
      }
      start.countDown();
      for (Future<?> future : futures) {
        future.get();
      }
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  public void useGeneratedWriter() {
    ForyJson json = ForyJson.builder().build();
    assertEquals(json.toJson(new PublicFields()), "{\"active\":true,\"id\":7,\"name\":\"fory\"}");
    assertEquals(json.hasGeneratedWriter(PublicFields.class), true);
  }

  @Test
  public void disableGeneratedWriter() {
    ForyJson json = ForyJson.builder().withCodegen(false).build();
    assertEquals(json.toJson(new PublicFields()), "{\"active\":true,\"id\":7,\"name\":\"fory\"}");
    PublicFields fields =
        json.fromJson("{\"active\":false,\"id\":8,\"name\":\"json\"}", PublicFields.class);
    assertEquals(fields.active, false);
    assertEquals(fields.id, 8);
    assertEquals(fields.name, "json");
    BoxedScalars scalars =
        json.fromJson(
            "{\"bool\":true,\"byteValue\":2,\"charValue\":\"x\",\"doubleValue\":2.5,"
                + "\"floatValue\":1.5,\"intValue\":4,\"longValue\":5,\"shortValue\":3}",
            BoxedScalars.class);
    assertEquals(scalars.byteValue, Byte.valueOf((byte) 2));
    assertEquals(scalars.shortValue, Short.valueOf((short) 3));
    assertEquals(json.hasGeneratedWriter(PublicFields.class), false);
  }

  @Test
  public void writeNullFields() {
    ForyJson json = ForyJson.builder().writeNullFields(true).build();
    assertEquals(
        json.toJson(new PublicFields()),
        "{\"active\":true,\"id\":7,\"missing\":null,\"name\":\"fory\"}");
  }

  @Test
  public void ignoreMethods() {
    ForyJson json = ForyJson.builder().build();
    assertEquals(
        json.toJson(new MethodsIgnored()),
        "{\"hidden\":\"hidden\",\"setterCalls\":0,\"value\":\"field\"}");
    MethodsIgnored value =
        json.fromJson("{\"hidden\":\"json\",\"value\":\"json\"}", MethodsIgnored.class);
    assertEquals(value.hidden, "json");
    assertEquals(value.setterCalls, 0);
    assertEquals(value.value, "json");
  }

  @Test
  public void writeDeclaredFields() {
    ForyJson json = ForyJson.builder().build();
    String expected = "{\"id\":11,\"name\":\"private\"}";
    assertEquals(json.toJson(new PrivateFields()), expected);
    assertEquals(
        new String(json.toJsonBytes(new PrivateFields()), StandardCharsets.UTF_8), expected);
    assertEquals(json.hasGeneratedWriter(PrivateFields.class), true);
    PrivateFields value =
        json.fromJson("{\"id\":12,\"name\":\"json\",\"nullable\":\"value\"}", PrivateFields.class);
    assertEquals(value.id, 12);
    assertEquals(value.name, "json");
    assertEquals(value.nullable, "value");
    assertEquals(value.transientValue, "transient");
    assertEquals(PrivateFields.staticValue, "static");
  }

  @Test
  public void writeDirectionalIgnore() {
    ForyJson json = ForyJson.builder().build();
    assertEquals(json.toJson(new DirectionalIgnore()), "{\"writeOnly\":2}");
  }

  @Test
  public void writeNestedCollections() {
    ForyJson json = ForyJson.builder().build();
    assertEquals(
        json.toJson(new Nested()),
        "{\"kind\":\"FAST\",\"names\":[\"a\",\"b\"],\"scores\":{\"one\":1,\"two\":2}}");
  }

  @Test
  public void writeRecursiveGeneratedTypes() {
    ForyJson json = ForyJson.builder().build();
    RecursiveParent value = new RecursiveParent();
    assertEquals(json.toJson(value), "{\"child\":{\"name\":\"child\"},\"name\":\"parent\"}");
    assertEquals(
        new String(json.toJsonBytes(value), StandardCharsets.UTF_8),
        "{\"child\":{\"name\":\"child\"},\"name\":\"parent\"}");
    assertEquals(json.hasGeneratedWriter(RecursiveParent.class), true);
    assertEquals(json.hasGeneratedWriter(RecursiveChild.class), true);
  }

  @Test
  public void writeBoxedScalars() {
    ForyJson json = ForyJson.builder().build();
    String expected =
        "{\"bool\":true,\"byteValue\":2,\"charValue\":\"x\",\"doubleValue\":2.5,"
            + "\"floatValue\":1.5,\"intValue\":4,\"longValue\":5,\"shortValue\":3}";
    assertEquals(json.toJson(new BoxedScalars()), expected);
    assertEquals(
        new String(json.toJsonBytes(new BoxedScalars()), StandardCharsets.UTF_8), expected);
  }

  @Test
  public void writeNaturalObjectValues() {
    ForyJson json = ForyJson.builder().build();
    String expected =
        "{\"bool\":true,\"list\":[\"a\",1,false],\"map\":{\"name\":\"fory\",\"score\":9},"
            + "\"number\":7,\"text\":\"fory\"}";
    assertEquals(json.toJson(new NaturalValues()), expected);
    assertEquals(
        new String(json.toJsonBytes(new NaturalValues()), StandardCharsets.UTF_8), expected);
  }

  @Test
  public void writeNaturalEmptyObject() {
    ForyJson json = ForyJson.builder().build();
    String expected = "{\"value\":{}}";
    assertEquals(json.toJson(new NaturalObjectValue()), expected);
    assertEquals(
        new String(json.toJsonBytes(new NaturalObjectValue()), StandardCharsets.UTF_8), expected);
  }

  @Test
  public void writeDeclaredObjectFieldType() {
    ForyJson json = ForyJson.builder().build();
    String expected = "{\"value\":{\"parent\":1}}";
    assertEquals(json.toJson(new DeclaredParentField()), expected);
    assertEquals(
        new String(json.toJsonBytes(new DeclaredParentField()), StandardCharsets.UTF_8), expected);
    DeclaredParentField read =
        json.fromJson("{\"value\":{\"child\":9,\"parent\":3}}", DeclaredParentField.class);
    assertEquals(read.value.getClass(), ParentValue.class);
    assertEquals(read.value.parent, 3);
  }

  @Test
  public void writeGeneratedTokenChanges() {
    ForyJson json = ForyJson.builder().build();
    TokenValues value = new TokenValues();
    String first = "{\"count\":1,\"name\":\"alpha\",\"tags\":[\"x\",\"y\"],\"total\":2}";
    assertEquals(json.toJson(value), first);
    assertEquals(new String(json.toJsonBytes(value), StandardCharsets.UTF_8), first);
    assertEquals(json.toJson(value), first);
    assertEquals(new String(json.toJsonBytes(value), StandardCharsets.UTF_8), first);
    value.count = 7;
    value.name = "beta";
    value.tags = Arrays.asList("z", "x");
    value.total = 9;
    String second = "{\"count\":7,\"name\":\"beta\",\"tags\":[\"z\",\"x\"],\"total\":9}";
    assertEquals(json.toJson(value), second);
    assertEquals(new String(json.toJsonBytes(value), StandardCharsets.UTF_8), second);
    assertEquals(json.hasGeneratedWriter(TokenValues.class), true);
  }

  @Test
  public void writeGeneratedTokenLanes() {
    ForyJson json = ForyJson.builder().build();
    TokenGroup group = new TokenGroup();
    group.values =
        Arrays.asList(
            tokenValue(1, "alpha", Arrays.asList("x", "y"), 2),
            tokenValue(3, "beta", Arrays.asList("z", "x"), 4),
            tokenValue(5, "gamma", Arrays.asList("y", "z"), 6));
    String first =
        "{\"values\":[{\"count\":1,\"name\":\"alpha\",\"tags\":[\"x\",\"y\"],\"total\":2},"
            + "{\"count\":3,\"name\":\"beta\",\"tags\":[\"z\",\"x\"],\"total\":4},"
            + "{\"count\":5,\"name\":\"gamma\",\"tags\":[\"y\",\"z\"],\"total\":6}]}";
    assertEquals(json.toJson(group), first);
    assertEquals(new String(json.toJsonBytes(group), StandardCharsets.UTF_8), first);
    assertEquals(json.toJson(group), first);
    assertEquals(new String(json.toJsonBytes(group), StandardCharsets.UTF_8), first);
    TokenValues middle = group.values.get(1);
    middle.count = 7;
    middle.name = "delta";
    middle.tags = Arrays.asList("q", "x");
    middle.total = 8;
    String second =
        "{\"values\":[{\"count\":1,\"name\":\"alpha\",\"tags\":[\"x\",\"y\"],\"total\":2},"
            + "{\"count\":7,\"name\":\"delta\",\"tags\":[\"q\",\"x\"],\"total\":8},"
            + "{\"count\":5,\"name\":\"gamma\",\"tags\":[\"y\",\"z\"],\"total\":6}]}";
    assertEquals(json.toJson(group), second);
    assertEquals(new String(json.toJsonBytes(group), StandardCharsets.UTF_8), second);
    assertEquals(json.hasGeneratedWriter(TokenGroup.class), true);
    assertEquals(json.hasGeneratedWriter(TokenValues.class), true);
  }

  @Test
  public void readGeneratedObjectCollection() {
    ForyJson json = ForyJson.builder().build();
    String input = "{\"values\":[{\"count\":1,\"name\":\"alpha\",\"tags\":[\"x\"],\"total\":2}]}";
    TokenGroup stringValue = json.fromJson(input, TokenGroup.class);
    TokenGroup utf8Value = json.fromJson(input.getBytes(StandardCharsets.UTF_8), TokenGroup.class);
    assertEquals(stringValue.values.size(), 1);
    assertEquals(stringValue.values.get(0).name, "alpha");
    assertEquals(stringValue.values.get(0).tags, Arrays.asList("x"));
    assertEquals(utf8Value.values.size(), 1);
    assertEquals(utf8Value.values.get(0).total, 2);
    assertEquals(json.hasGeneratedWriter(TokenGroup.class), true);
    assertEquals(json.hasGeneratedWriter(TokenValues.class), true);
  }

  @Test
  public void escapeStrings() {
    ForyJson json = ForyJson.builder().build();
    PublicFields fields = new PublicFields();
    fields.name = "a\n\"b\"\\\u1234";
    String stringExpected = "{\"active\":true,\"id\":7,\"name\":\"a\\n\\\"b\\\"\\\\\u1234\"}";
    assertEquals(json.toJson(fields), stringExpected);
    assertEquals(json.fromJson(stringExpected, PublicFields.class).name, fields.name);
  }

  @Test
  public void writeUtf16StringText() {
    ForyJson json = ForyJson.builder().build();
    UnicodeValues values = new UnicodeValues();
    String expected =
        "{\"first\":\"\u1234\",\"second\":\"music \uD834\uDD1E\","
            + "\"tags\":[\"latin\",\"\u1234\",\"\uD83D\uDE00\"]}";
    assertEquals(json.toJson(values), expected);
    assertEquals(json.toJson(values), expected);
    assertEquals(new String(json.toJsonBytes(values), StandardCharsets.UTF_8), expected);
    assertEquals(json.fromJson(expected, UnicodeValues.class).second, values.second);
    assertEquals(json.fromJson(json.toJsonBytes(values), UnicodeValues.class).tags, values.tags);
  }

  @Test
  public void writeUtf16Char() {
    ForyJson json = ForyJson.builder().build();
    CharValue value = new CharValue();
    value.value = '\u1234';
    assertEquals(json.toJson(value), "{\"value\":\"\u1234\"}");
    assertEquals(
        new String(json.toJsonBytes(value), StandardCharsets.UTF_8), "{\"value\":\"\u1234\"}");
  }

  @Test
  public void writeNonLatin1Matrix() {
    ForyJson json = ForyJson.builder().build();
    UnicodeMatrix value = new UnicodeMatrix();
    String expected = unicodeMatrixJson();
    assertEquals(json.toJson(value), expected);
    assertEquals(json.toJson(value), expected);
    assertEquals(new String(json.toJsonBytes(value), StandardCharsets.UTF_8), expected);
    assertUnicodeMatrix(json.fromJson(expected, UnicodeMatrix.class));
    assertUnicodeMatrix(
        json.fromJson(expected.getBytes(StandardCharsets.UTF_8), UnicodeMatrix.class));
    assertEquals(json.fromJson("\"" + MIXED_SCRIPT_TEXT + "\"", String.class), MIXED_SCRIPT_TEXT);
    assertEquals(
        json.fromJson(
            ("\"" + SUPPLEMENTARY_TEXT + "\"").getBytes(StandardCharsets.UTF_8), String.class),
        SUPPLEMENTARY_TEXT);
  }

  @Test
  public void writeReadZhEuStrings() {
    ForyJson json = ForyJson.builder().build();
    assertTextRoundTrip(json, ZH_TEXT);
    assertTextRoundTrip(json, EU_TEXT);
  }

  @Test
  public void readStringInputLayouts() {
    ForyJson json = ForyJson.builder().build();
    String latin1Json = "{\"active\":true,\"id\":7,\"name\":\"café\"}";
    String utf16Json = "{\"active\":true,\"id\":7,\"name\":\"你好，Fory\"}";
    if (StringSerializer.isBytesBackedString()) {
      assertTrue(StringSerializer.isLatin1Coder(StringSerializer.getStringCoder(latin1Json)));
      assertTrue(StringSerializer.isUtf16Coder(StringSerializer.getStringCoder(utf16Json)));
    }
    assertEquals(json.fromJson(latin1Json, PublicFields.class).name, "café");
    assertEquals(json.fromJson(utf16Json, PublicFields.class).name, ZH_TEXT);
    assertEquals(json.fromJson("\"café\"", String.class), "café");
    assertEquals(json.fromJson("\"你好，Fory\"", String.class), ZH_TEXT);
  }

  @Test
  public void readUnicodeFieldNames() {
    ForyJson json = ForyJson.builder().build();
    String direct = "{\"café\":\"" + EU_TEXT + "\",\"你好\":\"" + ZH_TEXT + "\"}";
    String escaped = "{\"caf\\u00e9\":\"" + EU_TEXT + "\",\"\\u4f60\\u597d\":\"" + ZH_TEXT + "\"}";
    UnicodeFieldNames directValue = json.fromJson(direct, UnicodeFieldNames.class);
    UnicodeFieldNames escapedValue = json.fromJson(escaped, UnicodeFieldNames.class);
    UnicodeFieldNames bytesValue =
        json.fromJson(direct.getBytes(StandardCharsets.UTF_8), UnicodeFieldNames.class);
    assertEquals(directValue.café, EU_TEXT);
    assertEquals(directValue.你好, ZH_TEXT);
    assertEquals(escapedValue.café, EU_TEXT);
    assertEquals(escapedValue.你好, ZH_TEXT);
    assertEquals(bytesValue.café, EU_TEXT);
    assertEquals(bytesValue.你好, ZH_TEXT);
  }

  @Test
  public void writeLatin1NonAsciiBytes() {
    ForyJson json = ForyJson.builder().build();
    PublicFields fields = new PublicFields();
    fields.name = "caf\u00e9";
    assertEquals(json.toJson(fields), "{\"active\":true,\"id\":7,\"name\":\"caf\u00e9\"}");
    assertEquals(
        new String(json.toJsonBytes(fields), StandardCharsets.UTF_8),
        "{\"active\":true,\"id\":7,\"name\":\"caf\u00e9\"}");
    fields.name = "\u0080";
    String expected = "{\"active\":true,\"id\":7,\"name\":\"\u0080\"}";
    assertEquals(json.toJson(fields), expected);
    assertEquals(new String(json.toJsonBytes(fields), StandardCharsets.UTF_8), expected);
    assertEquals(json.fromJson(json.toJsonBytes(fields), PublicFields.class).name, fields.name);
  }

  @Test
  public void rejectSurrogateChar() {
    ForyJson json = ForyJson.builder().build();
    CharValue value = new CharValue();
    value.value = '\uD800';
    assertThrows(ForyJsonException.class, () -> json.toJson(value));
    assertThrows(ForyJsonException.class, () -> json.toJsonBytes(value));
  }

  @Test
  public void rejectSurrogateString() {
    ForyJson json = ForyJson.builder().build();
    PublicFields fields = new PublicFields();
    fields.name = "\uD800";
    assertThrows(ForyJsonException.class, () -> json.toJson(fields));
    assertThrows(ForyJsonException.class, () -> json.toJsonBytes(fields));
  }

  @Test
  public void writeSurrogatePair() {
    ForyJson json = ForyJson.builder().build();
    PublicFields fields = new PublicFields();
    fields.name = "a\uD83D\uDE00";
    String stringExpected = "{\"active\":true,\"id\":7,\"name\":\"a\uD83D\uDE00\"}";
    String utf8Expected = "{\"active\":true,\"id\":7,\"name\":\"a\uD83D\uDE00\"}";
    assertEquals(json.toJson(fields), stringExpected);
    assertEquals(new String(json.toJsonBytes(fields), StandardCharsets.UTF_8), utf8Expected);
    assertEquals(json.fromJson(stringExpected, PublicFields.class).name, fields.name);
  }

  @Test
  public void readPublicFields() {
    ForyJson json = ForyJson.builder().build();
    PublicFields fields =
        json.fromJson(
            "{\"unknown\":[1,true,{\"x\":\"y\"}],\"name\":\"fory\",\"id\":7,\"active\":true}",
            PublicFields.class);
    assertEquals(fields.name, "fory");
    assertEquals(fields.id, 7);
    assertEquals(fields.active, true);
  }

  @Test
  public void readUtf8Bytes() {
    ForyJson json = ForyJson.builder().build();
    byte[] bytes =
        "{\"name\":\"\uD83D\uDE00\u1234\",\"id\":8,\"active\":false}"
            .getBytes(StandardCharsets.UTF_8);
    PublicFields fields = json.fromJson(bytes, PublicFields.class);
    assertEquals(fields.name, "\uD83D\uDE00\u1234");
    assertEquals(fields.id, 8);
    assertEquals(fields.active, false);
  }

  @Test
  public void readDirectionalIgnore() {
    ForyJson json = ForyJson.builder().build();
    DirectionalIgnore value =
        json.fromJson("{\"both\":7,\"writeOnly\":8,\"readOnly\":9}", DirectionalIgnore.class);
    assertEquals(value.both, 1);
    assertEquals(value.writeOnly, 2);
    assertEquals(value.readOnly, 9);
  }

  @Test
  public void readBoxedScalars() {
    ForyJson json = ForyJson.builder().build();
    BoxedScalars value =
        json.fromJson(
            "{\"bool\":false,\"byteValue\":6,\"charValue\":\"z\",\"doubleValue\":3.5,"
                + "\"floatValue\":2.5,\"intValue\":8,\"longValue\":9,\"shortValue\":7}",
            BoxedScalars.class);
    assertEquals(value.bool, Boolean.FALSE);
    assertEquals(value.byteValue, Byte.valueOf((byte) 6));
    assertEquals(value.charValue, Character.valueOf('z'));
    assertEquals(value.doubleValue, Double.valueOf(3.5));
    assertEquals(value.floatValue, Float.valueOf(2.5f));
    assertEquals(value.intValue, Integer.valueOf(8));
    assertEquals(value.longValue, Long.valueOf(9));
    assertEquals(value.shortValue, Short.valueOf((short) 7));
  }

  @Test
  public void readTypeRefList() {
    ForyJson json = ForyJson.builder().build();
    List<TokenValues> values =
        json.fromJson(
            "[{\"count\":1,\"name\":\"alpha\",\"tags\":[\"x\",\"y\"],\"total\":2},"
                + "{\"count\":3,\"name\":\"beta\",\"tags\":[\"z\"],\"total\":4}]",
            new TypeRef<List<TokenValues>>() {});
    assertEquals(values.size(), 2);
    assertEquals(values.get(0).name, "alpha");
    assertEquals(values.get(0).tags, Arrays.asList("x", "y"));
    assertEquals(values.get(1).count, 3);
    assertEquals(values.get(1).total, 4);
  }

  @Test
  public void readTypeRefMapBytes() {
    ForyJson json = ForyJson.builder().build();
    byte[] bytes =
        "{\"first\":{\"count\":5,\"name\":\"gamma\",\"tags\":[\"u\"],\"total\":6}}"
            .getBytes(StandardCharsets.UTF_8);
    Map<String, TokenValues> values =
        json.fromJson(bytes, new TypeRef<Map<String, TokenValues>>() {});
    assertEquals(values.size(), 1);
    assertEquals(values.get("first").name, "gamma");
    assertEquals(values.get("first").tags, Arrays.asList("u"));
    assertEquals(values.get("first").total, 6);
  }

  @Test
  public void readJsonContainers() {
    ForyJson json = ForyJson.builder().build();
    JSONObject object =
        json.fromJson("{\"name\":\"fory\",\"items\":[1,\"你好，Fory\"]}", JSONObject.class);
    assertEquals(object.get("name"), "fory");
    assertTrue(object.get("items") instanceof JSONArray);
    JSONArray items = (JSONArray) object.get("items");
    assertEquals(items.get(0), Long.valueOf(1));
    assertEquals(items.get(1), ZH_TEXT);

    Object natural = json.fromJson("{\"items\":[true]}", Object.class);
    assertTrue(natural instanceof JSONObject);
    assertTrue(((JSONObject) natural).get("items") instanceof JSONArray);
  }

  @Test
  public void writeJsonContainers() {
    ForyJson json = ForyJson.builder().build();
    JSONObject object = new JSONObject();
    JSONArray values = new JSONArray();
    values.add(Integer.valueOf(1));
    values.add(ZH_TEXT);
    object.put("values", values);
    object.put("name", "fory");
    String expected = "{\"values\":[1,\"你好，Fory\"],\"name\":\"fory\"}";
    assertEquals(json.toJson(object), expected);
    assertEquals(new String(json.toJsonBytes(object), StandardCharsets.UTF_8), expected);
  }

  @Test
  public void writeReadCoreScalarFields() {
    ForyJson json = ForyJson.builder().build();
    CoreScalarFields value = new CoreScalarFields();
    String expected =
        "{\"atomicInt\":7,\"bigDecimal\":12345.6789,\"bigInteger\":12345678901234567890,"
            + "\"builder\":\"build\",\"bytes\":[1,-2,3],\"calendar\":123456789,"
            + "\"charset\":\"UTF-8\",\"currency\":\"EUR\",\"date\":\"2026-06-21\","
            + "\"instant\":\"2026-06-21T01:02:03Z\",\"locale\":\"zh-Hans-CN\","
            + "\"maybe\":\"yes\",\"optionalInt\":4,\"timeZone\":\"UTC\",\"type\":\""
            + PublicFields.class.getName()
            + "\",\"uri\":\"https://fory.apache.org/json\","
            + "\"url\":\"https://fory.apache.org/\","
            + "\"uuid\":\"123e4567-e89b-12d3-a456-426614174000\"}";
    assertEquals(json.toJson(value), expected);
    assertEquals(new String(json.toJsonBytes(value), StandardCharsets.UTF_8), expected);
    CoreScalarFields read = json.fromJson(expected, CoreScalarFields.class);
    assertEquals(read.atomicInt.get(), 7);
    assertEquals(read.bigDecimal, value.bigDecimal);
    assertEquals(read.bigInteger, value.bigInteger);
    assertEquals(read.builder.toString(), "build");
    assertEquals(byteBufferBytes(read.bytes), new byte[] {1, -2, 3});
    assertEquals(read.calendar.getTimeInMillis(), 123456789L);
    assertEquals(read.charset, StandardCharsets.UTF_8);
    assertEquals(read.currency, value.currency);
    assertEquals(read.date, value.date);
    assertEquals(read.instant, value.instant);
    assertEquals(read.locale, value.locale);
    assertEquals(read.maybe, Optional.of("yes"));
    assertEquals(read.optionalInt.getAsInt(), 4);
    assertEquals(read.timeZone.getID(), "UTC");
    assertEquals(read.type, PublicFields.class);
    assertEquals(read.uri, value.uri);
    assertEquals(read.url, value.url);
    assertEquals(read.uuid, value.uuid);
  }

  @Test
  public void writeReadMapKeyFields() {
    ForyJson json = ForyJson.builder().build();
    String expected =
        "{\"intNames\":{\"1\":\"one\",\"2\":\"two\"},\"scores\":{\"FAST\":1,\"SMALL\":2}}";
    assertEquals(json.toJson(new MapKeyFields()), expected);
    MapKeyFields read = json.fromJson(expected, MapKeyFields.class);
    assertEquals(read.intNames, intNames());
    assertEquals(read.scores, enumScores());
  }

  @Test
  public void readTypeRefOptional() {
    ForyJson json = ForyJson.builder().build();
    Optional<TokenValues> value =
        json.fromJson(
            "{\"count\":9,\"name\":\"optional\",\"tags\":[\"a\"],\"total\":10}",
            new TypeRef<Optional<TokenValues>>() {});
    assertTrue(value.isPresent());
    assertEquals(value.get().name, "optional");
    assertEquals(value.get().tags, Arrays.asList("a"));
    assertEquals(json.fromJson("null", new TypeRef<Optional<TokenValues>>() {}), Optional.empty());
  }

  @Test
  public void readTypeRefMapKeys() {
    ForyJson json = ForyJson.builder().build();
    Map<Integer, String> value =
        json.fromJson("{\"1\":\"one\",\"2\":\"two\"}", new TypeRef<Map<Integer, String>>() {});
    assertEquals(value, intNames());

    Map<Integer, String> escaped =
        json.fromJson("{\"\\u0031\":\"one\"}", new TypeRef<Map<Integer, String>>() {});
    assertEquals(escaped.get(1), "one");

    Map<Long, String> longs =
        json.fromJson(
            "{\"-1\":\"negative\",\"9223372036854775807\":\"max\"}",
            new TypeRef<Map<Long, String>>() {});
    Map<Long, String> expected = new LinkedHashMap<>();
    expected.put(-1L, "negative");
    expected.put(Long.MAX_VALUE, "max");
    assertEquals(longs, expected);
    assertEquals(
        json.fromJson(
            "{\"-1\":\"negative\",\"9223372036854775807\":\"max\"}"
                .getBytes(StandardCharsets.UTF_8),
            new TypeRef<Map<Long, String>>() {}),
        expected);
  }

  @Test
  public void readFastContainerTypeRefs() {
    ForyJson json = ForyJson.builder().build();
    assertEquals(
        json.fromJson("[\"alpha\",\"你好，Fory\"]", new TypeRef<List<String>>() {}),
        Arrays.asList("alpha", ZH_TEXT));
    assertEquals(json.fromJson("[1,2]", new TypeRef<List<Integer>>() {}), Arrays.asList(1, 2));
    assertEquals(
        json.fromJson("[true,false]", new TypeRef<List<Boolean>>() {}),
        Arrays.asList(Boolean.TRUE, Boolean.FALSE));
    assertEquals(
        json.fromJson(
            "{\"one\":1,\"two\":2}".getBytes(StandardCharsets.UTF_8),
            new TypeRef<Map<String, Integer>>() {}),
        scores());
    assertEquals(
        json.fromJson(
            "{\"enabled\":true,\"disabled\":false}", new TypeRef<Map<String, Boolean>>() {}),
        flags());
    Map<String, String> aliases = new LinkedHashMap<>();
    aliases.put("zh", ZH_TEXT);
    aliases.put("eu", EU_TEXT);
    assertEquals(
        json.fromJson(
            "{\"zh\":\"你好，Fory\",\"eu\":\"café crème Österreich € ČšŽ\"}",
            new TypeRef<Map<String, String>>() {}),
        aliases);
    assertEquals(
        json.fromJson("{\"1\":\"one\",\"2\":\"two\"}", new TypeRef<Map<Integer, String>>() {}),
        intNames());
  }

  @Test
  public void writeReadFastContainerFields() {
    ForyJson json = ForyJson.builder().build();
    String input =
        "{\"booleans\":[true,false],\"flags\":{\"enabled\":true,\"disabled\":false},"
            + "\"intNames\":{\"1\":\"one\",\"2\":\"two\"},\"ints\":[1,2],"
            + "\"names\":[\"alpha\",\"你好，Fory\"],\"scores\":{\"one\":1,\"two\":2}}";
    FastContainers read = json.fromJson(input, FastContainers.class);
    assertFastContainers(read);
    assertFastContainers(
        json.fromJson(json.toJsonBytes(new FastContainers()), FastContainers.class));
  }

  @Test
  public void readGeneratedCollectionFields() {
    ForyJson json = ForyJson.builder().build();
    String input =
        "{\"kinds\":[\"FAST\",\"SMALL\"],\"names\":[\"alpha\",\"你好，Fory\"]," + "\"numbers\":[1,2]}";
    assertGeneratedCollections(json.fromJson(input, GeneratedCollectionFields.class));
    assertGeneratedCollections(
        json.fromJson(input.getBytes(StandardCharsets.UTF_8), GeneratedCollectionFields.class));
    assertEquals(json.hasGeneratedWriter(GeneratedCollectionFields.class), true);
  }

  @Test
  public void writeReadRecordClass() throws Exception {
    if (JdkVersion.MAJOR_VERSION < 17) {
      throw new SkipException("Java record test requires JDK 17+");
    }
    Class<?> type =
        compileRecordClass(
            "JsonRecordValue",
            "package org.apache.fory.json.records;\n"
                + "import java.util.List;\n"
                + "public record JsonRecordValue(int id, String name, List<String> tags, "
                + "Child child) {\n"
                + "  public record Child(String label) {}\n"
                + "}\n");
    Class<?> childType = Class.forName(type.getName() + "$Child", true, type.getClassLoader());
    Object child = childType.getConstructor(String.class).newInstance("kid");
    Object value =
        type.getConstructor(int.class, String.class, List.class, childType)
            .newInstance(7, ZH_TEXT, Arrays.asList("a", "b"), child);
    ForyJson json = ForyJson.builder().build();
    String expected =
        "{\"child\":{\"label\":\"kid\"},\"id\":7,\"name\":\"你好，Fory\"," + "\"tags\":[\"a\",\"b\"]}";
    assertEquals(json.toJson(value), expected);
    assertEquals(new String(json.toJsonBytes(value), StandardCharsets.UTF_8), expected);
    assertEquals(json.hasGeneratedWriter(type), true);
    assertRecordValue(json.fromJson(expected, type), childType);
    assertRecordValue(json.fromJson(expected.getBytes(StandardCharsets.UTF_8), type), childType);

    Object missing = json.fromJson("{\"name\":\"missing\"}", type);
    assertEquals(type.getMethod("id").invoke(missing), Integer.valueOf(0));
    assertEquals(type.getMethod("name").invoke(missing), "missing");
    assertEquals(type.getMethod("tags").invoke(missing), null);
    assertEquals(type.getMethod("child").invoke(missing), null);
  }

  @Test
  public void readScalarRoots() {
    ForyJson json = ForyJson.builder().build();
    assertEquals(json.fromJson("7", int.class), Integer.valueOf(7));
    assertEquals(json.fromJson("true", boolean.class), Boolean.TRUE);
    assertEquals(json.fromJson("\"fory\"".getBytes(StandardCharsets.UTF_8), String.class), "fory");
    assertEquals(
        json.fromJson("\"\uD83D\uDE00\u1234\"".getBytes(StandardCharsets.UTF_8), String.class),
        "\uD83D\uDE00\u1234");
  }

  @Test
  public void readPrimitiveArrayRoots() {
    ForyJson json = ForyJson.builder().build();
    assertEquals(json.toJson(new int[] {1, 2}), "[1,2]");
    assertEquals(json.fromJson("[1,2]", int[].class), new int[] {1, 2});
    assertEquals(
        json.fromJson("[1,2]".getBytes(StandardCharsets.UTF_8), int[].class), new int[] {1, 2});
    assertEquals(
        json.fromJson("[true,false]".getBytes(StandardCharsets.UTF_8), boolean[].class),
        new boolean[] {true, false});
    assertEquals(json.fromJson("[1,-2,3]", byte[].class), new byte[] {1, -2, 3});
    assertEquals(json.fromJson("[\"a\",\"你\"]", char[].class), new char[] {'a', '你'});
    assertThrows(ForyJsonException.class, () -> json.fromJson("[1,null]", int[].class));
  }

  @Test
  public void rejectLeadingZero() {
    ForyJson json = ForyJson.builder().build();
    assertThrows(ForyJsonException.class, () -> json.fromJson("01", int.class));
    assertThrows(
        ForyJsonException.class,
        () -> json.fromJson("{\"id\":01}".getBytes(StandardCharsets.UTF_8), PublicFields.class));
  }

  @Test
  public void rejectInvalidSurrogates() {
    ForyJson json = ForyJson.builder().build();
    assertEquals(json.fromJson("\"\\uD834\\uDD1E\"", String.class), "\uD834\uDD1E");
    assertThrows(ForyJsonException.class, () -> json.fromJson("\"\\uD800\"", String.class));
    assertThrows(ForyJsonException.class, () -> json.fromJson("\"\\uDC00\"", String.class));
    assertThrows(
        ForyJsonException.class,
        () -> json.fromJson("\"" + Character.toString('\uD800') + "\"", String.class));
  }

  private static Map<String, Integer> scores() {
    Map<String, Integer> scores = new LinkedHashMap<>();
    scores.put("one", 1);
    scores.put("two", 2);
    return scores;
  }

  private static Map<String, Boolean> flags() {
    Map<String, Boolean> flags = new LinkedHashMap<>();
    flags.put("enabled", Boolean.TRUE);
    flags.put("disabled", Boolean.FALSE);
    return flags;
  }

  private static Map<Integer, String> intNames() {
    Map<Integer, String> values = new LinkedHashMap<>();
    values.put(1, "one");
    values.put(2, "two");
    return values;
  }

  private static EnumMap<Kind, Integer> enumScores() {
    EnumMap<Kind, Integer> values = new EnumMap<>(Kind.class);
    values.put(Kind.FAST, 1);
    values.put(Kind.SMALL, 2);
    return values;
  }

  private static Calendar calendar(long millis) {
    Calendar calendar = new GregorianCalendar();
    calendar.setTimeZone(TimeZone.getTimeZone("UTC"));
    calendar.setTimeInMillis(millis);
    return calendar;
  }

  private static URL url(String value) {
    try {
      return new URL(value);
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException(e);
    }
  }

  private static byte[] byteBufferBytes(ByteBuffer buffer) {
    ByteBuffer copy = buffer.duplicate();
    byte[] bytes = new byte[copy.remaining()];
    copy.get(bytes);
    return bytes;
  }

  private static Map<String, String> unicodeMap() {
    Map<String, String> values = new LinkedHashMap<>();
    values.put(TWO_BYTE_TEXT, THREE_BYTE_TEXT);
    values.put(ZH_TEXT, EU_TEXT);
    values.put("\u043a\u043b\u044e\u0447", "\uD83D\uDE00");
    values.put("\u0645\u0631\u062d\u0628\u0627", "\u0928\u092e\u0938\u094d\u0924\u0947");
    return values;
  }

  private static String unicodeMatrixJson() {
    return "{\"boxedChar\":\"\u20ac\",\"charThreeByte\":\"\u4f60\","
        + "\"charTwoByte\":\"\u0100\",\"chars\":[\"\u0100\",\"\u07ff\",\"\u0800\","
        + "\"\u20ac\",\"\u4f60\"],\"combining\":\""
        + COMBINING_TEXT
        + "\",\"eu\":\""
        + EU_TEXT
        + "\",\"mixedScripts\":\""
        + MIXED_SCRIPT_TEXT
        + "\",\"supplementary\":\""
        + SUPPLEMENTARY_TEXT
        + "\",\"threeByte\":\""
        + THREE_BYTE_TEXT
        + "\",\"twoByte\":\""
        + TWO_BYTE_TEXT
        + "\",\"valueMap\":{\""
        + TWO_BYTE_TEXT
        + "\":\""
        + THREE_BYTE_TEXT
        + "\",\""
        + ZH_TEXT
        + "\":\""
        + EU_TEXT
        + "\",\"\u043a\u043b\u044e\u0447\":\"\uD83D\uDE00\","
        + "\"\u0645\u0631\u062d\u0628\u0627\":\"\u0928\u092e\u0938\u094d\u0924\u0947\"},"
        + "\"values\":[\""
        + TWO_BYTE_TEXT
        + "\",\""
        + THREE_BYTE_TEXT
        + "\",\""
        + SUPPLEMENTARY_TEXT
        + "\",\""
        + MIXED_SCRIPT_TEXT
        + "\",\""
        + COMBINING_TEXT
        + "\",\""
        + ZH_TEXT
        + "\",\""
        + EU_TEXT
        + "\"],\"zh\":\""
        + ZH_TEXT
        + "\"}";
  }

  private static void assertUnicodeMatrix(UnicodeMatrix value) {
    assertEquals(value.boxedChar, Character.valueOf('\u20ac'));
    assertEquals(value.charThreeByte, '\u4f60');
    assertEquals(value.charTwoByte, '\u0100');
    assertEquals(value.chars, new char[] {'\u0100', '\u07ff', '\u0800', '\u20ac', '\u4f60'});
    assertEquals(value.combining, COMBINING_TEXT);
    assertEquals(value.eu, EU_TEXT);
    assertEquals(value.mixedScripts, MIXED_SCRIPT_TEXT);
    assertEquals(value.supplementary, SUPPLEMENTARY_TEXT);
    assertEquals(value.threeByte, THREE_BYTE_TEXT);
    assertEquals(value.twoByte, TWO_BYTE_TEXT);
    assertEquals(value.valueMap, unicodeMap());
    assertEquals(
        value.values,
        Arrays.asList(
            TWO_BYTE_TEXT,
            THREE_BYTE_TEXT,
            SUPPLEMENTARY_TEXT,
            MIXED_SCRIPT_TEXT,
            COMBINING_TEXT,
            ZH_TEXT,
            EU_TEXT));
    assertEquals(value.zh, ZH_TEXT);
  }

  private static void assertFastContainers(FastContainers value) {
    assertEquals(value.booleans, Arrays.asList(Boolean.TRUE, Boolean.FALSE));
    assertEquals(value.flags, flags());
    assertEquals(value.intNames, intNames());
    assertEquals(value.ints, Arrays.asList(1, 2));
    assertEquals(value.names, Arrays.asList("alpha", ZH_TEXT));
    assertEquals(value.scores, scores());
  }

  private static void assertGeneratedCollections(GeneratedCollectionFields value) {
    assertTrue(value.kinds instanceof EnumSet);
    assertEquals(value.kinds, EnumSet.of(Kind.FAST, Kind.SMALL));
    assertTrue(value.names instanceof LinkedHashSet);
    assertEquals(value.names, new LinkedHashSet<>(Arrays.asList("alpha", ZH_TEXT)));
    assertTrue(value.numbers instanceof ArrayDeque);
    assertEquals(new ArrayList<>(value.numbers), Arrays.asList(1, 2));
  }

  private static Class<?> compileRecordClass(String simpleName, String source) throws Exception {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertTrue(compiler != null);
    Path output =
        Paths.get(ForyJsonTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    Path sourceDir = Files.createTempDirectory("fory-json-record");
    Files.createDirectories(sourceDir);
    Path sourceFile = sourceDir.resolve(simpleName + ".java");
    Files.write(sourceFile, source.getBytes(StandardCharsets.UTF_8));
    int exit =
        compiler.run(
            null, null, null, "--release", "17", "-d", output.toString(), sourceFile.toString());
    assertEquals(exit, 0);
    return Class.forName(
        "org.apache.fory.json.records." + simpleName, true, ForyJsonTest.class.getClassLoader());
  }

  private static void assertRecordValue(Object value, Class<?> childType) throws Exception {
    Class<?> type = value.getClass();
    assertEquals(type.getMethod("id").invoke(value), Integer.valueOf(7));
    assertEquals(type.getMethod("name").invoke(value), ZH_TEXT);
    assertEquals(type.getMethod("tags").invoke(value), Arrays.asList("a", "b"));
    Object child = type.getMethod("child").invoke(value);
    assertEquals(childType.getMethod("label").invoke(child), "kid");
  }

  private static void assertTextRoundTrip(ForyJson json, String text) {
    String rootJson = "\"" + text + "\"";
    assertEquals(json.toJson(text), rootJson);
    assertEquals(new String(json.toJsonBytes(text), StandardCharsets.UTF_8), rootJson);
    assertEquals(json.fromJson(rootJson, String.class), text);
    assertEquals(json.fromJson(rootJson.getBytes(StandardCharsets.UTF_8), String.class), text);

    PublicFields fields = new PublicFields();
    fields.name = text;
    String objectJson = "{\"active\":true,\"id\":7,\"name\":\"" + text + "\"}";
    assertEquals(json.toJson(fields), objectJson);
    assertEquals(new String(json.toJsonBytes(fields), StandardCharsets.UTF_8), objectJson);
    assertEquals(json.fromJson(objectJson, PublicFields.class).name, text);
    assertEquals(
        json.fromJson(objectJson.getBytes(StandardCharsets.UTF_8), PublicFields.class).name, text);
  }

  private static Map<String, Object> values() {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("name", "fory");
    values.put("score", Integer.valueOf(9));
    return values;
  }
}
