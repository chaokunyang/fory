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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.fory.annotation.Ignore;
import org.testng.annotations.Test;

public class ForyJsonTest {
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
    @Ignore public int both = 1;

    @Ignore(ignoreRead = true, ignoreWrite = false)
    public int writeOnly = 2;

    @Ignore(ignoreRead = false, ignoreWrite = true)
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

  public static final class CharValue {
    public char value;
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
  public void escapeStrings() {
    ForyJson json = ForyJson.builder().build();
    PublicFields fields = new PublicFields();
    fields.name = "a\n\"b\"\\\u1234";
    assertEquals(
        json.toJson(fields), "{\"active\":true,\"id\":7,\"name\":\"a\\n\\\"b\\\"\\\\\u1234\"}");
  }

  @Test
  public void writeLatin1NonAsciiBytes() {
    ForyJson json = ForyJson.builder().build();
    PublicFields fields = new PublicFields();
    fields.name = "caf\u00e9";
    assertEquals(
        new String(json.toJsonBytes(fields), StandardCharsets.UTF_8),
        "{\"active\":true,\"id\":7,\"name\":\"caf\u00e9\"}");
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
    String expected = "{\"active\":true,\"id\":7,\"name\":\"a\uD83D\uDE00\"}";
    assertEquals(json.toJson(fields), expected);
    assertEquals(new String(json.toJsonBytes(fields), StandardCharsets.UTF_8), expected);
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
        "{\"name\":\"\u1234\",\"id\":8,\"active\":false}".getBytes(StandardCharsets.UTF_8);
    PublicFields fields = json.fromJson(bytes, PublicFields.class);
    assertEquals(fields.name, "\u1234");
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

  private static Map<String, Object> values() {
    Map<String, Object> values = new LinkedHashMap<>();
    values.put("name", "fory");
    values.put("score", Integer.valueOf(9));
    return values;
  }
}
