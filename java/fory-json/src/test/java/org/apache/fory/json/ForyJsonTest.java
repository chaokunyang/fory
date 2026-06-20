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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.fory.json.annotation.JsonIgnore;
import org.testng.annotations.Test;

public class ForyJsonTest {
  private static final String TWO_BYTE_TEXT = "\u0100\u07ff\u03a9";
  private static final String THREE_BYTE_TEXT = "\u0800\u20ac\u4f60\ud7ff\ue000";
  private static final String SUPPLEMENTARY_TEXT = "\uD834\uDD1E\uD83D\uDE00\uD83C\uDF0D";
  private static final String MIXED_SCRIPT_TEXT =
      "\u0100\u03a9\u0416\u05d0\u0627\u0905\u0e01\u4f60";
  private static final String COMBINING_TEXT = "e\u0301\u200d\uD83D\uDCBB";

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

  public static final class Accessors {
    public String value = "field";
    private boolean ready = true;
    private String writeOnly;

    public boolean getReady() {
      return ready;
    }

    public boolean isReady() {
      return ready;
    }

    public String getValue() {
      return "getter";
    }

    public void setReady(boolean ready) {
      this.ready = ready;
    }

    public void setWriteOnly(String writeOnly) {
      this.writeOnly = writeOnly;
    }
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
    public String mixedScripts = MIXED_SCRIPT_TEXT;
    public String supplementary = SUPPLEMENTARY_TEXT;
    public String threeByte = THREE_BYTE_TEXT;
    public String twoByte = TWO_BYTE_TEXT;
    public Map<String, String> valueMap = unicodeMap();
    public List<String> values =
        Arrays.asList(
            TWO_BYTE_TEXT, THREE_BYTE_TEXT, SUPPLEMENTARY_TEXT, MIXED_SCRIPT_TEXT, COMBINING_TEXT);
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
  public void writeAccessorPrecedence() {
    ForyJson json = ForyJson.builder().build();
    assertEquals(json.toJson(new Accessors()), "{\"ready\":true,\"value\":\"getter\"}");
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
  public void readSetterProperties() {
    ForyJson json = ForyJson.builder().build();
    Accessors accessors =
        json.fromJson("{\"ready\":false,\"writeOnly\":\"value\"}", Accessors.class);
    assertEquals(accessors.ready, false);
    assertEquals(accessors.writeOnly, "value");
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

  private static Map<String, String> unicodeMap() {
    Map<String, String> values = new LinkedHashMap<>();
    values.put(TWO_BYTE_TEXT, THREE_BYTE_TEXT);
    values.put("\u043a\u043b\u044e\u0447", "\uD83D\uDE00");
    values.put("\u0645\u0631\u062d\u0628\u0627", "\u0928\u092e\u0938\u094d\u0924\u0947");
    return values;
  }

  private static String unicodeMatrixJson() {
    return "{\"boxedChar\":\"\u20ac\",\"charThreeByte\":\"\u4f60\","
        + "\"charTwoByte\":\"\u0100\",\"chars\":[\"\u0100\",\"\u07ff\",\"\u0800\","
        + "\"\u20ac\",\"\u4f60\"],\"combining\":\""
        + COMBINING_TEXT
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
        + "\"]}";
  }

  private static void assertUnicodeMatrix(UnicodeMatrix value) {
    assertEquals(value.boxedChar, Character.valueOf('\u20ac'));
    assertEquals(value.charThreeByte, '\u4f60');
    assertEquals(value.charTwoByte, '\u0100');
    assertEquals(value.chars, new char[] {'\u0100', '\u07ff', '\u0800', '\u20ac', '\u4f60'});
    assertEquals(value.combining, COMBINING_TEXT);
    assertEquals(value.mixedScripts, MIXED_SCRIPT_TEXT);
    assertEquals(value.supplementary, SUPPLEMENTARY_TEXT);
    assertEquals(value.threeByte, THREE_BYTE_TEXT);
    assertEquals(value.twoByte, TWO_BYTE_TEXT);
    assertEquals(value.valueMap, unicodeMap());
    assertEquals(
        value.values,
        Arrays.asList(
            TWO_BYTE_TEXT, THREE_BYTE_TEXT, SUPPLEMENTARY_TEXT, MIXED_SCRIPT_TEXT, COMBINING_TEXT));
  }

  private static Map<String, Object> values() {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("name", "fory");
    values.put("score", Integer.valueOf(9));
    return values;
  }
}
