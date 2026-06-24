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
import static org.testng.Assert.assertTrue;

import java.lang.reflect.Field;
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
import java.util.HashMap;
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
import java.util.concurrent.atomic.AtomicInteger;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.apache.fory.json.annotation.JsonIgnore;
import org.apache.fory.platform.JdkVersion;
import org.apache.fory.reflect.FieldAccessor;
import org.testng.SkipException;

public abstract class ForyJsonTestModels {
  protected static final String TWO_BYTE_TEXT = "\u0100\u07ff\u03a9";
  protected static final String THREE_BYTE_TEXT = "\u0800\u20ac\u4f60\ud7ff\ue000";
  protected static final String SUPPLEMENTARY_TEXT = "\uD834\uDD1E\uD83D\uDE00\uD83C\uDF0D";
  protected static final String MIXED_SCRIPT_TEXT =
      "\u0100\u03a9\u0416\u05d0\u0627\u0905\u0e01\u4f60";
  protected static final String COMBINING_TEXT = "e\u0301\u200d\uD83D\uDCBB";
  protected static final String ZH_TEXT = "你好，Fory";
  protected static final String EU_TEXT = "café crème Österreich € ČšŽ";

  public enum Kind {
    FAST,
    SMALL
  }

  public enum UnicodeKind {
    你好
  }

  public static final class UnicodeEnumValue {
    public UnicodeKind kind = UnicodeKind.你好;
  }

  public static final class PublicFields {
    public boolean active = true;
    public int id = 7;
    public String name = "fory";
    public String missing;
  }

  public static final class DepthNode {
    public int value;
    public DepthNode child;
    public List<DepthNode> children;
    public Map<String, DepthNode> nodes;
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

  public static final class NumericBoundaries {
    public int intMax;
    public int intMin;
    public long longMax;
    public long longMin;
    public int small;
    public String text;
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

  protected static TokenValues tokenValue(int count, String name, List<String> tags, long total) {
    TokenValues value = new TokenValues();
    value.count = count;
    value.name = name;
    value.tags = tags;
    value.total = total;
    return value;
  }

  protected static String hiddenValue(MethodsIgnored value) {
    return value.hidden;
  }

  protected static int privateId(PrivateFields value) {
    return value.id;
  }

  protected static String privateName(PrivateFields value) {
    return value.name;
  }

  protected static String privateNullable(PrivateFields value) {
    return value.nullable;
  }

  protected static String privateTransientValue(PrivateFields value) {
    return value.transientValue;
  }

  protected static String privateStaticValue() {
    return PrivateFields.staticValue;
  }

  protected static Map<String, Integer> scores() {
    Map<String, Integer> scores = new LinkedHashMap<>();
    scores.put("one", 1);
    scores.put("two", 2);
    return scores;
  }

  protected static Map<String, Boolean> flags() {
    Map<String, Boolean> flags = new LinkedHashMap<>();
    flags.put("enabled", Boolean.TRUE);
    flags.put("disabled", Boolean.FALSE);
    return flags;
  }

  protected static Map<Integer, String> intNames() {
    Map<Integer, String> values = new LinkedHashMap<>();
    values.put(1, "one");
    values.put(2, "two");
    return values;
  }

  protected static EnumMap<Kind, Integer> enumScores() {
    EnumMap<Kind, Integer> values = new EnumMap<>(Kind.class);
    values.put(Kind.FAST, 1);
    values.put(Kind.SMALL, 2);
    return values;
  }

  protected static Calendar calendar(long millis) {
    Calendar calendar = new GregorianCalendar();
    calendar.setTimeZone(TimeZone.getTimeZone("UTC"));
    calendar.setTimeInMillis(millis);
    return calendar;
  }

  protected static URL url(String value) {
    try {
      return new URL(value);
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException(e);
    }
  }

  protected static byte[] byteBufferBytes(ByteBuffer buffer) {
    ByteBuffer copy = buffer.duplicate();
    byte[] bytes = new byte[copy.remaining()];
    copy.get(bytes);
    return bytes;
  }

  protected static Map<String, String> unicodeMap() {
    Map<String, String> values = new LinkedHashMap<>();
    values.put(TWO_BYTE_TEXT, THREE_BYTE_TEXT);
    values.put(ZH_TEXT, EU_TEXT);
    values.put("\u043a\u043b\u044e\u0447", "\uD83D\uDE00");
    values.put("\u0645\u0631\u062d\u0628\u0627", "\u0928\u092e\u0938\u094d\u0924\u0947");
    return values;
  }

  protected static String unicodeMatrixJson() {
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

  protected static void assertUnicodeMatrix(UnicodeMatrix value) {
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

  protected static void assertFastContainers(FastContainers value) {
    assertEquals(value.booleans, Arrays.asList(Boolean.TRUE, Boolean.FALSE));
    assertEquals(value.flags, flags());
    assertEquals(value.intNames, intNames());
    assertEquals(value.ints, Arrays.asList(1, 2));
    assertEquals(value.names, Arrays.asList("alpha", ZH_TEXT));
    assertEquals(value.scores, scores());
  }

  protected static void assertGeneratedCollections(GeneratedCollectionFields value) {
    assertTrue(value.kinds instanceof EnumSet);
    assertEquals(value.kinds, EnumSet.of(Kind.FAST, Kind.SMALL));
    assertTrue(value.names instanceof LinkedHashSet);
    assertEquals(value.names, new LinkedHashSet<>(Arrays.asList("alpha", ZH_TEXT)));
    assertTrue(value.numbers instanceof ArrayDeque);
    assertEquals(new ArrayList<>(value.numbers), Arrays.asList(1, 2));
  }

  protected static void assertNumericBoundaries(NumericBoundaries value, String text) {
    assertEquals(value.intMax, Integer.MAX_VALUE);
    assertEquals(value.intMin, Integer.MIN_VALUE);
    assertEquals(value.longMax, Long.MAX_VALUE);
    assertEquals(value.longMin, Long.MIN_VALUE);
    assertEquals(value.small, -7);
    assertEquals(value.text, text);
  }

  protected static Class<?> compileRecordClass(String simpleName, String source) throws Exception {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertTrue(compiler != null);
    Path output =
        Paths.get(
            ForyJsonTestModels.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    Path sourceDir = Files.createTempDirectory("fory-json-record");
    Files.createDirectories(sourceDir);
    Path sourceFile = sourceDir.resolve(simpleName + ".java");
    Files.write(sourceFile, source.getBytes(StandardCharsets.UTF_8));
    int exit =
        compiler.run(
            null, null, null, "--release", "17", "-d", output.toString(), sourceFile.toString());
    assertEquals(exit, 0);
    return Class.forName(
        "org.apache.fory.json.records." + simpleName,
        true,
        ForyJsonTestModels.class.getClassLoader());
  }

  protected static void assertRecordValue(Object value, Class<?> childType) throws Exception {
    Class<?> type = value.getClass();
    assertEquals(type.getMethod("id").invoke(value), Integer.valueOf(7));
    assertEquals(type.getMethod("name").invoke(value), ZH_TEXT);
    assertEquals(type.getMethod("tags").invoke(value), Arrays.asList("a", "b"));
    Object child = type.getMethod("child").invoke(value);
    assertEquals(childType.getMethod("label").invoke(child), "kid");
  }

  protected static void assertTextRoundTrip(ForyJson json, String text) {
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

  protected static void assertGeneratedWhenSupported(ForyJson json, Class<?> type) {
    assertEquals(json.hasGeneratedWriter(type), JdkVersion.MAJOR_VERSION >= 15);
  }

  protected static String repeat(char ch, int length) {
    char[] chars = new char[length];
    Arrays.fill(chars, ch);
    return new String(chars);
  }

  protected static String nestedArray(int depth) {
    StringBuilder builder = new StringBuilder(depth * 2 + 1);
    for (int i = 0; i < depth; i++) {
      builder.append('[');
    }
    builder.append('0');
    for (int i = 0; i < depth; i++) {
      builder.append(']');
    }
    return builder.toString();
  }

  protected static int arrayCapacity(ArrayList<?> list) {
    try {
      Field field = ArrayList.class.getDeclaredField("elementData");
      Object[] array = (Object[]) FieldAccessor.createAccessor(field).getObject(list);
      return array.length;
    } catch (Throwable cause) {
      throw new SkipException("Cannot inspect ArrayList capacity on this runtime", cause);
    }
  }

  protected static int mapCapacity(HashMap<?, ?> map) {
    try {
      Field field = HashMap.class.getDeclaredField("table");
      Object[] table = (Object[]) FieldAccessor.createAccessor(field).getObject(map);
      return table == null ? 0 : table.length;
    } catch (Throwable cause) {
      throw new SkipException("Cannot inspect HashMap capacity on this runtime", cause);
    }
  }

  protected static Map<String, Object> values() {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("name", "fory");
    values.put("score", 9);
    return values;
  }
}
