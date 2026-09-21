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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertThrows;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.fory.json.annotation.JsonAnyProperty;
import org.apache.fory.json.annotation.JsonFormat;
import org.apache.fory.json.annotation.JsonProperty;
import org.apache.fory.json.annotation.JsonRawValue;
import org.apache.fory.json.annotation.JsonUnwrapped;
import org.apache.fory.json.resolver.JsonTypeInfo;
import org.apache.fory.json.resolver.JsonTypeResolver;
import org.apache.fory.json.writer.StringJsonWriter;
import org.apache.fory.json.writer.Utf8JsonWriter;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class JsonPrettyPrintTest {
  public static class Values {
    public String name;
    public long[] numbers = {1, 2, 3};
    public boolean[] flags = {true, false};
  }

  public static class Raw {
    @JsonRawValue public String value;
  }

  public enum Color {
    RED,
    BLUE
  }

  public static class Scalars {
    public int id = 1;
    public long count = Long.MAX_VALUE;
    public Boolean enabled = true;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    public boolean quoted = false;

    public Color color = Color.RED;
    public String absent;
  }

  public static class Unwrapped {
    @JsonUnwrapped public Scalars scalars = new Scalars();
    @JsonAnyProperty public Map<String, Object> extra = Collections.singletonMap("extra", 2);
  }

  public static class Dynamic {
    public int id = 1;

    @JsonAnyProperty
    public Map<String, Object> extra = Collections.singletonMap("extra", new int[] {2, 3});
  }

  public static class Names {
    public int abc = 1;
    public int abcd = 2;
    public int abcdefghijk = 3;
    public int abcdefghijkl = 4;

    @JsonProperty("\"\\")
    public int escaped = 5;
  }

  @DataProvider
  public Object[][] modes() {
    return new Object[][] {{false}, {true}};
  }

  @Test(dataProvider = "modes")
  public void objects(boolean codegen) {
    ForyJson json = pretty(codegen);
    Values value = new Values();
    for (String name : new String[] {"ascii", "中文 😀", "latin-é", "ascii"}) {
      value.name = name;
      String expected =
          lines(
              "{",
              "  \"name\" : \"" + name + "\",",
              "  \"numbers\" : [",
              "    1,",
              "    2,",
              "    3",
              "  ],",
              "  \"flags\" : [",
              "    true,",
              "    false",
              "  ]",
              "}");
      assertOutput(json, value, expected);
      assertEquals(json.fromJson(expected, Values.class).name, name);
      assertEquals(json.fromJson(expected.getBytes(UTF_8), Values.class).numbers, value.numbers);
    }
  }

  @Test(dataProvider = "modes")
  public void nestedContainers(boolean codegen) {
    ForyJson json = pretty(codegen);
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("items", Arrays.asList(Collections.singletonMap("x", 1), Collections.emptyMap()));
    map.put("arrays", new int[][] {{}, {1, 2}});
    map.put("object", Collections.singletonMap("null", null));
    assertOutput(
        json,
        map,
        lines(
            "{",
            "  \"items\" : [",
            "    {",
            "      \"x\" : 1",
            "    },",
            "    { }",
            "  ],",
            "  \"arrays\" : [",
            "    [ ],",
            "    [",
            "      1,",
            "      2",
            "    ]",
            "  ],",
            "  \"object\" : {",
            "    \"null\" : null",
            "  }",
            "}"));
    assertOutput(
        json,
        new Object[] {map},
        lines("[", "  " + json.toPrettyJson(map).replace("\n", "\n  "), "]"));
    assertOutput(json, Collections.emptyMap(), "{ }");
    assertOutput(json, new int[0], "[ ]");
    assertOutput(json, null, "null");
    assertOutput(json, true, "true");
    assertOutput(json, -12.5, "-12.5");
    assertOutput(
        json, new char[] {'a', '中', '"'}, lines("[", "  \"a\",", "  \"中\",", "  \"\\\"\"", "]"));
    assertOutput(
        json,
        new Color[] {Color.RED, null, Color.BLUE},
        lines("[", "  \"RED\",", "  null,", "  \"BLUE\"", "]"));
    assertOutput(
        json, new String[] {"a", null, "中"}, lines("[", "  \"a\",", "  null,", "  \"中\"", "]"));
    assertOutput(json, Collections.singletonMap(1, 2), lines("{", "  \"1\" : 2", "}"));
    assertOutput(
        json,
        Collections.singletonMap(Long.MAX_VALUE, 2),
        lines("{", "  \"9223372036854775807\" : 2", "}"));
  }

  @Test(dataProvider = "modes")
  public void fieldShapes(boolean codegen) {
    ForyJson json = pretty(codegen);
    String fields =
        "  \"id\" : 1,\n  \"count\" : 9223372036854775807,\n"
            + "  \"enabled\" : true,\n  \"quoted\" : \"false\",\n  \"color\" : \"RED\"";
    Scalars scalars = new Scalars();
    assertOutput(json, scalars, "{\n" + fields + "\n}");
    assertEquals(
        json.toJson(scalars),
        "{\"id\":1,\"count\":9223372036854775807,\"enabled\":true,\"quoted\":\"false\",\"color\":\"RED\"}");
    assertOutput(json, new Unwrapped(), "{\n" + fields + ",\n  \"extra\" : 2\n}");
    assertOutput(
        json,
        new Dynamic(),
        lines("{", "  \"id\" : 1,", "  \"extra\" : [", "    2,", "    3", "  ]", "}"));
  }

  @Test(dataProvider = "modes")
  public void stringsAndRawValues(boolean codegen) {
    ForyJson json = pretty(codegen);
    ForyJson compact = ForyJson.builder().withCodegen(codegen).build();
    for (String value : new String[] {"", "\\\"{},:[]\n\t", "\"\\\\\"", "é中文😀\""}) {
      assertOutput(json, value, compact.toJson(value));
      Map<String, String> map = Collections.singletonMap(value, value);
      assertOutput(
          json, map, lines("{", "  " + compact.toJson(value) + " : " + compact.toJson(value), "}"));
    }
    Raw raw = new Raw();
    raw.value = " { \"a\": [1, 2], \"b\": \"[ } : \\\"\" } ";
    assertOutput(json, raw, lines("{", "  \"value\" : " + raw.value, "}"));
  }

  @Test(dataProvider = "modes")
  public void fieldNames(boolean codegen) {
    ForyJson json = pretty(codegen);
    assertOutput(
        json,
        new Names(),
        lines(
            "{",
            "  \"abc\" : 1,",
            "  \"abcd\" : 2,",
            "  \"abcdefghijk\" : 3,",
            "  \"abcdefghijkl\" : 4,",
            "  \"\\\"\\\\\" : 5",
            "}"));
  }

  @Test
  public void wideObject() {
    ForyJson json = pretty(true);
    JsonGeneratedCodecTest.WideWriterFields value = new JsonGeneratedCodecTest.WideWriterFields();
    StringBuilder expected = new StringBuilder("{\n  \"field00\" : 1");
    for (int i = 1; i < 24; i++) {
      String suffix = i < 10 ? "0" + i : Integer.toString(i);
      expected.append(",\n  \"field").append(suffix).append("\" : \"v").append(suffix).append('"');
    }
    expected.append("\n}");
    assertOutput(json, value, expected.toString());
    JsonTypeResolver resolver = JsonTestSupport.currentTypeResolver(json);
    JsonTypeInfo info = resolver.getTypeInfo(value.getClass(), value.getClass());
    Object owner = resolver.getObjectCodec(value.getClass());
    assertNotSame(info.stringWriter(), owner);
    assertNotSame(info.utf8Writer(), owner);
  }

  @Test
  public void bufferGrowthAndReuse() {
    ForyJson json = ForyJson.builder().maxDepth(128).withBufferSizeLimitBytes(32).build();
    Object value = Collections.emptyMap();
    for (int i = 0; i < 50; i++) {
      value = Collections.singletonMap("x", new Object[] {value});
    }
    String text = json.toPrettyJson(value);
    assertOutput(json, value, text);
    assertOutput(json, Collections.singletonMap("x", "中文"), lines("{", "  \"x\" : \"中文\"", "}"));
    assertOutput(json, new int[0], "[ ]");
    Map<String, Object> cycle = new LinkedHashMap<>();
    cycle.put("cycle", cycle);
    assertThrows(ForyJsonException.class, () -> json.toPrettyJson(cycle));
    assertEquals(json.toJson(Collections.singletonMap("x", 1)), "{\"x\":1}");
    assertThrows(ForyJsonException.class, () -> json.toPrettyJsonBytes(cycle));
    assertEquals(json.toJsonBytes(Collections.singletonMap("x", 1)), "{\"x\":1}".getBytes(UTF_8));
    assertOutput(json, Collections.singletonMap("x", 1), lines("{", "  \"x\" : 1", "}"));
  }

  @Test
  public void compactDefault() {
    ForyJson json = ForyJson.builder().build();
    Map<String, Object> value = Collections.singletonMap("x", new int[] {1, 2});
    for (int i = 0; i < 3; i++) {
      assertOutput(json, value, lines("{", "  \"x\" : [", "    1,", "    2", "  ]", "}"));
      assertEquals(json.toJson(value), "{\"x\":[1,2]}");
      assertEquals(json.toJsonBytes(value), "{\"x\":[1,2]}".getBytes(UTF_8));
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      json.writeJsonTo(value, output);
      assertEquals(output.toByteArray(), "{\"x\":[1,2]}".getBytes(UTF_8));
    }
  }

  @Test
  public void indentationDuringWrite() {
    Utf8JsonWriter utf8 = JsonTestSupport.newUtf8Writer();
    utf8.setPrettyPrint(true);
    utf8.writeArrayStart();
    utf8.writeInt(1);
    utf8.writeComma(1);
    utf8.writeInt(2);
    utf8.writeArrayEnd();
    assertEquals(
        Arrays.copyOf(utf8.getBuffer(), utf8.getPosition()), "[\n  1,\n  2\n]".getBytes(UTF_8));
    StringJsonWriter string = JsonTestSupport.newStringWriter();
    string.setPrettyPrint(true);
    string.writeObjectStart();
    string.writeFieldName("x");
    string.writeString("中文");
    string.writeObjectEnd();
    assertEquals(string.toJson(), "{\n  \"x\" : \"中文\"\n}");
  }

  private static ForyJson pretty(boolean codegen) {
    return ForyJson.builder().withCodegen(codegen).withAsyncCompilation(false).build();
  }

  private static String lines(String... lines) {
    return String.join("\n", lines);
  }

  private static void assertOutput(ForyJson json, Object value, String expected) {
    assertEquals(json.toPrettyJson(value), expected);
    assertEquals(json.toPrettyJsonBytes(value), expected.getBytes(UTF_8));
  }
}
