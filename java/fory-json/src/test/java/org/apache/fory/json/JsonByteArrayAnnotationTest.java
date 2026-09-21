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
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.fory.json.annotation.JsonAnyGetter;
import org.apache.fory.json.annotation.JsonAnyProperty;
import org.apache.fory.json.annotation.JsonByteArray;
import org.apache.fory.json.annotation.JsonCodec;
import org.apache.fory.json.annotation.JsonCreator;
import org.apache.fory.json.annotation.JsonIgnore;
import org.apache.fory.json.annotation.JsonMixin;
import org.apache.fory.json.annotation.JsonProperty;
import org.apache.fory.json.annotation.JsonPropertyOrder;
import org.apache.fory.json.annotation.JsonRawValue;
import org.apache.fory.json.codec.Base64ByteArrayCodec;
import org.apache.fory.platform.JdkVersion;
import org.apache.fory.reflect.TypeRef;
import org.testng.SkipException;
import org.testng.annotations.Factory;
import org.testng.annotations.Test;

public class JsonByteArrayAnnotationTest extends ForyJsonTestModels {
  @Factory(dataProvider = "enableCodegen")
  public JsonByteArrayAnnotationTest(boolean codegen) {
    super(codegen);
  }

  @Test
  public void globalFormats() {
    byte[] bytes = {0, 127, -128, -1};
    String[] encodings = {"\"AH+A/w==\"", "\"007f80ff\"", "[0,127,-128,-1]"};
    JsonByteArray.Format[] formats = {
      JsonByteArray.Format.BASE64, JsonByteArray.Format.BASE16, JsonByteArray.Format.ARRAY
    };
    ForyJsonBuilder builder = newJsonBuilder();
    ForyJson defaultJson = builder.build();
    for (int i = 0; i < formats.length; i++) {
      ForyJson json = builder.byteArrayFormat(formats[i]).build();
      String encoded = encodings[i];
      assertEquals(json.toJson(bytes), encoded);
      assertEquals(json.toJson(bytes, TypeRef.of(Object.class)), encoded);
      assertEquals(new String(json.toJsonBytes(bytes), StandardCharsets.UTF_8), encoded);
      assertEquals(json.fromJson(encoded, byte[].class), bytes);
      assertEquals(json.fromJson(encoded.getBytes(StandardCharsets.UTF_8), byte[].class), bytes);
      assertNull(json.fromJson("null", byte[].class));
      assertNull(json.fromJson("null".getBytes(StandardCharsets.UTF_8), byte[].class));

      BinaryValues value = new BinaryValues();
      value.bytes = bytes;
      value.list = Arrays.asList(bytes, null);
      value.map = Collections.singletonMap("data", bytes);
      value.nested = new byte[][] {bytes};
      String expected =
          "{\"label\":\"汉\",\"bytes\":"
              + encoded
              + ",\"list\":["
              + encoded
              + ",null],\"map\":{\"data\":"
              + encoded
              + "},\"nested\":["
              + encoded
              + "]}";
      assertEquals(json.toJson(value), expected);
      assertEquals(new String(json.toJsonBytes(value), StandardCharsets.UTF_8), expected);
      String pretty = json.toPrettyJson(value);
      assertEquals(new String(json.toPrettyJsonBytes(value), StandardCharsets.UTF_8), pretty);
      for (String text : new String[] {expected, pretty}) {
        assertBinaryValues(json.fromJson(text, BinaryValues.class), bytes);
        assertBinaryValues(
            json.fromJson(text.getBytes(StandardCharsets.UTF_8), BinaryValues.class), bytes);
      }
      TypeRef<List<byte[]>> listType = new TypeRef<List<byte[]>>() {};
      assertEquals(json.toJson(value.list, listType), "[" + encoded + ",null]");
      assertEquals(json.fromJson("[" + encoded + ",null]", listType).get(0), bytes);
      TypeRef<Map<String, byte[]>> mapType = new TypeRef<Map<String, byte[]>>() {};
      assertEquals(json.toJson(value.map, mapType), "{\"data\":" + encoded + "}");
      assertEquals(json.fromJson("{\"data\":" + encoded + "}", mapType).get("data"), bytes);
      assertGeneratedWhenSupported(json, BinaryValues.class, codegenEnabled());
    }
    assertEquals(defaultJson.toJson(bytes), encodings[0]);
    assertThrows(NullPointerException.class, () -> builder.byteArrayFormat(null));
  }

  private static void assertBinaryValues(BinaryValues value, byte[] bytes) {
    assertEquals(value.label, "汉");
    assertEquals(value.bytes, bytes);
    assertEquals(value.list.get(0), bytes);
    assertNull(value.list.get(1));
    assertEquals(value.map.get("data"), bytes);
    assertEquals(value.nested[0], bytes);
  }

  @Test
  public void base16Overrides() {
    ForyJson json = newJsonBuilder().byteArrayFormat(JsonByteArray.Format.BASE16).build();
    Base64Field base64 = new Base64Field();
    base64.bytes = new byte[] {1, -2};
    assertEquals(json.toJson(base64), "{\"bytes\":\"Af4=\"}");
    assertEquals(json.fromJson(json.toJsonBytes(base64), Base64Field.class).bytes, base64.bytes);
    ArrayField array = new ArrayField();
    array.bytes = base64.bytes;
    assertEquals(json.toJson(array), "{\"bytes\":[1,-2]}");
    assertEquals(json.fromJson(json.toJsonBytes(array), ArrayField.class).bytes, base64.bytes);
    DirectCodecBase64 custom = new DirectCodecBase64();
    custom.bytes = base64.bytes;
    assertEquals(json.toJson(custom), "{\"bytes\":\"Af4=\"}");

    ForyJson mixinJson =
        newJsonBuilder()
            .byteArrayFormat(JsonByteArray.Format.ARRAY)
            .registerMixin(HexMixin.class)
            .build();
    assertEquals(mixinJson.toJson(base64), "{\"bytes\":\"01fe\"}");
    assertEquals(
        mixinJson.fromJson(mixinJson.toJsonBytes(base64), Base64Field.class).bytes, base64.bytes);
    assertGeneratedWhenSupported(mixinJson, Base64Field.class, codegenEnabled());

    HexField hex = new HexField();
    hex.bytes = base64.bytes;
    assertEquals(newJson().toJson(hex), "{\"bytes\":\"01fe\"}");
    hex.bytes = null;
    assertEquals(newJson().toJson(hex), "{}");
    assertEquals(newJsonBuilder().writeNullFields(true).build().toJson(hex), "{\"bytes\":null}");
  }

  @Test
  public void base16Contents() {
    ForyJson json = newJsonBuilder().byteArrayFormat(JsonByteArray.Format.BASE16).build();
    for (int size : new int[] {0, 1, 2, 255, 256, 257, 1025}) {
      byte[] bytes = new byte[size];
      StringBuilder expected = new StringBuilder("\"");
      for (int i = 0; i < size; i++) {
        bytes[i] = (byte) i;
        expected.append(Character.forDigit((i & 255) >>> 4, 16));
        expected.append(Character.forDigit(i & 15, 16));
      }
      String text = expected.append('"').toString();
      assertEquals(json.toJson(bytes), text);
      assertEquals(new String(json.toJsonBytes(bytes), StandardCharsets.UTF_8), text);
      for (String input : new String[] {text, text.toUpperCase(Locale.ROOT)}) {
        assertEquals(json.fromJson(input, byte[].class), bytes);
        assertEquals(json.fromJson(input.getBytes(StandardCharsets.UTF_8), byte[].class), bytes);
      }
      // The prefix widens String output and forces UTF16 input before the binary value.
      String unicode = "{\"label\":\"汉\",\"bytes\":" + text + "}";
      UnicodeHex value = new UnicodeHex();
      value.bytes = bytes;
      assertEquals(json.toJson(value), unicode);
      assertEquals(json.fromJson(unicode, UnicodeHex.class).bytes, bytes);
    }
    String escaped = "\"0\\u0061Ff\"";
    assertEquals(json.fromJson(escaped, byte[].class), new byte[] {10, -1});
    assertEquals(
        json.fromJson(escaped.getBytes(StandardCharsets.UTF_8), byte[].class), new byte[] {10, -1});
    for (String input :
        new String[] {
          "\"0\"", "\"gg\"", "\"0g\"", "\"汉0\"", "\"0 00\"", "\"00", "[0]", "\"\\u0030\""
        }) {
      assertThrows(ForyJsonException.class, () -> json.fromJson(input, byte[].class));
      assertThrows(
          ForyJsonException.class,
          () -> json.fromJson(input.getBytes(StandardCharsets.UTF_8), byte[].class));
      assertEquals(json.fromJson("\"00\"", byte[].class), new byte[] {0});
    }
    ForyJson leaf =
        newJsonBuilder()
            .byteArrayFormat(JsonByteArray.Format.BASE16)
            .withMaxGraphMemoryBytes(1)
            .build();
    assertEquals(leaf.fromJson(leaf.toJsonBytes(new byte[1024]), byte[].class), new byte[1024]);
  }

  @JsonPropertyOrder({"label", "bytes", "list", "map", "nested"})
  public static final class BinaryValues {
    public String label = "汉";
    public byte[] bytes;
    public List<byte[]> list;
    public Map<String, byte[]> map;
    public byte[][] nested;
  }

  public static final class HexField {
    @JsonByteArray(JsonByteArray.Format.BASE16)
    public byte[] bytes;
  }

  @JsonPropertyOrder({"label", "bytes"})
  public static final class UnicodeHex {
    public String label = "汉";
    public byte[] bytes;
  }

  @JsonMixin(target = Base64Field.class)
  public abstract static class HexMixin {
    @JsonByteArray(JsonByteArray.Format.BASE16)
    public byte[] bytes;
  }

  @Test
  public void arrayRoundTrip() {
    ForyJson json = newJson();
    ArrayField value = new ArrayField();
    byte[][] values = {new byte[0], {-128}, {1, -2, 127}};
    String[] encoded = {"[]", "[-128]", "[1,-2,127]"};
    for (int i = 0; i < values.length; i++) {
      value.bytes = values[i];
      String text = "{\"bytes\":" + encoded[i] + "}";
      assertEquals(json.toJson(value), text);
      assertEquals(new String(json.toJsonBytes(value), StandardCharsets.UTF_8), text);
      assertEquals(json.fromJson(text, ArrayField.class).bytes, values[i]);
      assertEquals(
          json.fromJson(text.getBytes(StandardCharsets.UTF_8), ArrayField.class).bytes, values[i]);
      assertEquals(
          json.fromJson("{\"ignored\":\"汉\",\"bytes\":" + encoded[i] + "}", ArrayField.class).bytes,
          values[i]);
    }
    assertNull(json.fromJson("{\"bytes\":null}", ArrayField.class).bytes);
    for (String encodedValue : new String[] {"[128]", "[-129]", "[null]", "\"AQ==\""}) {
      assertThrows(
          ForyJsonException.class,
          () -> json.fromJson("{\"bytes\":" + encodedValue + "}", ArrayField.class));
    }
    assertGeneratedWhenSupported(json, ArrayField.class, codegenEnabled());
  }

  @Test
  public void arrayGetter() {
    ForyJson json = newJson();
    ArrayGetter value = new ArrayGetter();
    value.bytes = new byte[] {1, -2};
    assertEquals(json.toJson(value), "{\"bytes\":[1,-2]}");
    assertEquals(json.fromJson("{\"bytes\":[1,-2]}", ArrayGetter.class).bytes, value.bytes);
  }

  @Test
  public void conflictingFormats() {
    assertThrows(ForyJsonException.class, () -> newJson().toJson(new ConflictingFormat()));
  }

  public static final class ArrayField {
    @JsonByteArray(JsonByteArray.Format.ARRAY)
    public byte[] bytes;
  }

  public static final class ArrayGetter {
    private byte[] bytes;

    @JsonByteArray(JsonByteArray.Format.ARRAY)
    public byte[] getBytes() {
      return bytes;
    }

    public void setBytes(byte[] bytes) {
      this.bytes = bytes;
    }
  }

  public static final class ConflictingFormat {
    @JsonByteArray(JsonByteArray.Format.ARRAY)
    public byte[] bytes = {1};

    @JsonByteArray(JsonByteArray.Format.BASE64)
    public byte[] getBytes() {
      return bytes;
    }
  }

  @Test
  public void fieldRoundTrip() {
    ForyJson json = newJson();
    Base64Field value = new Base64Field();
    value.bytes = new byte[] {0, 1, 2, -1};
    assertEquals(json.toJson(value), "{\"bytes\":\"AAEC/w==\"}");
    assertEquals(
        new String(json.toJsonBytes(value), StandardCharsets.UTF_8), "{\"bytes\":\"AAEC/w==\"}");
    assertEquals(
        json.fromJson("{\"bytes\":\"AQID\"}", Base64Field.class).bytes, new byte[] {1, 2, 3});
    assertEquals(
        json.fromJson("{\"bytes\":\"AQI=\"}".getBytes(StandardCharsets.UTF_8), Base64Field.class)
            .bytes,
        new byte[] {1, 2});
    assertThrows(
        ForyJsonException.class,
        () -> json.fromJson("{\"bytes\":\"not base64\"}", Base64Field.class));
    assertGeneratedWhenSupported(json, Base64Field.class, codegenEnabled());
  }

  @Test
  public void paddingAndEscapes() {
    ForyJson json = newJson();
    Base64Field value = new Base64Field();
    byte[][] values = {new byte[0], {1}, {1, 2}, {1, 2, 3}};
    String[] encoded = {"", "AQ==", "AQI=", "AQID"};
    for (int i = 0; i < values.length; i++) {
      value.bytes = values[i];
      assertEquals(json.toJson(value), "{\"bytes\":\"" + encoded[i] + "\"}");
      assertEquals(
          new String(json.toJsonBytes(value), StandardCharsets.UTF_8),
          "{\"bytes\":\"" + encoded[i] + "\"}");
      assertEquals(
          json.fromJson("{\"bytes\":\"" + encoded[i] + "\"}", Base64Field.class).bytes, values[i]);
    }
    assertEquals(
        json.fromJson("{\"bytes\":\"A\\u0051I=\"}", Base64Field.class).bytes, new byte[] {1, 2});
    for (String invalid : new String[] {"A===", "AA=A", "A", "AQ", "AQI"}) {
      String input = "{\"bytes\":\"" + invalid + "\"}";
      assertThrows(ForyJsonException.class, () -> json.fromJson(input, Base64Field.class));
      assertThrows(
          ForyJsonException.class,
          () -> json.fromJson(input.getBytes(StandardCharsets.UTF_8), Base64Field.class));
    }
  }

  @Test
  public void afterUnicode() {
    ForyJson json = newJson();
    UnicodeBase64 value = new UnicodeBase64();
    value.text = "你好";
    value.bytes = new byte[] {1};
    String expected = "{\"text\":\"你好\",\"bytes\":\"AQ==\"}";
    assertEquals(json.toJson(value), expected);
    assertEquals(new String(json.toJsonBytes(value), StandardCharsets.UTF_8), expected);
    UnicodeBase64 decoded = json.fromJson(expected, UnicodeBase64.class);
    assertEquals(decoded.text, "你好");
    assertEquals(decoded.bytes, new byte[] {1});
  }

  @Test
  public void getterRoundTrip() {
    Base64Getter value = new Base64Getter(new byte[] {1, 2, 3});
    ForyJson json = newJson();
    assertEquals(json.toJson(value), "{\"bytes\":\"AQID\"}");
    assertEquals(
        json.fromJson("{\"bytes\":\"AQI=\"}", Base64Getter.class).getBytes(), new byte[] {1, 2});
  }

  @Test
  public void directionalIgnore() {
    ForyJson json = newJson();
    Base64ReadOnly readOnly = new Base64ReadOnly();
    readOnly.bytes = new byte[] {1};
    assertEquals(json.toJson(readOnly), "{}");
    assertEquals(
        json.fromJson("{\"bytes\":\"AQI=\"}", Base64ReadOnly.class).bytes, new byte[] {1, 2});

    Base64WriteOnly writeOnly = new Base64WriteOnly();
    writeOnly.bytes = new byte[] {1, 2};
    assertEquals(json.toJson(writeOnly), "{\"bytes\":\"AQI=\"}");
    assertNull(json.fromJson("{\"bytes\":\"AQID\"}", Base64WriteOnly.class).bytes);
  }

  @Test
  public void creatorRoundTrip() {
    ForyJson json = newJson();
    byte[] bytes = {1, 2, 3};
    PropertyListBase64 propertyList = new PropertyListBase64(bytes);
    assertEquals(json.toJson(propertyList), "{\"bytes\":\"AQID\"}");
    assertEquals(
        json.fromJson("{\"bytes\":\"AQI=\"}", PropertyListBase64.class).bytes, new byte[] {1, 2});

    ParameterLocalBase64 parameterLocal = new ParameterLocalBase64(bytes);
    assertEquals(json.toJson(parameterLocal), "{\"bytes\":\"AQID\"}");
    assertEquals(
        json.fromJson("{\"bytes\":\"AQ==\"}", ParameterLocalBase64.class).bytes, new byte[] {1});
  }

  @Test
  public void recordRoundTrip() throws Exception {
    if (JdkVersion.MAJOR_VERSION < 17) {
      throw new SkipException("Java record test requires JDK 17+");
    }
    Class<?> type =
        compileRecordClass(
            "JsonByteArrayRecord",
            "package org.apache.fory.json.records;\n"
                + "import org.apache.fory.json.annotation.JsonByteArray;\n"
                + "public record JsonByteArrayRecord(@JsonByteArray(JsonByteArray.Format.BASE64) byte[] bytes) {}\n");
    Object value = type.getConstructor(byte[].class).newInstance((Object) new byte[] {1, 2, 3});
    for (ForyJson json : new ForyJson[] {newJson(), newJsonBuilder().withFieldMode(true).build()}) {
      assertEquals(json.toJson(value), "{\"bytes\":\"AQID\"}");
      Object decoded = json.fromJson("{\"bytes\":\"AQI=\"}", type);
      assertEquals(type.getMethod("bytes").invoke(decoded), new byte[] {1, 2});
    }
  }

  @Test
  public void nullInclusion() {
    ForyJson json = newJson();
    Base64Always value = new Base64Always();
    assertEquals(json.toJson(value), "{\"bytes\":null}");
    assertEquals(new String(json.toJsonBytes(value), StandardCharsets.UTF_8), "{\"bytes\":null}");
    value.bytes = new byte[] {1, 2, 3};
    assertEquals(json.toJson(value), "{\"bytes\":\"AQID\"}");
    assertEquals(
        new String(json.toJsonBytes(value), StandardCharsets.UTF_8), "{\"bytes\":\"AQID\"}");
    assertGeneratedWhenSupported(json, Base64Always.class, codegenEnabled());
  }

  @Test
  public void directCodec() {
    ForyJson json = newJson();
    DirectCodecBase64 value = new DirectCodecBase64();
    value.bytes = new byte[] {1, 2, 3};
    assertEquals(json.toJson(value), "{\"bytes\":\"AQID\"}");
    assertEquals(
        json.fromJson("{\"bytes\":\"AQI=\"}", DirectCodecBase64.class).bytes, new byte[] {1, 2});
  }

  @Test
  public void rejectInvalidDeclarations() {
    ForyJson json = newJson();
    assertThrows(ForyJsonException.class, () -> json.toJson(new NonBinaryBase64()));
    assertThrows(ForyJsonException.class, () -> json.toJson(new StaticBase64()));
    assertThrows(ForyJsonException.class, () -> json.toJson(new CodecBase64()));
    assertThrows(ForyJsonException.class, () -> json.toJson(new RawBase64()));
    assertThrows(ForyJsonException.class, () -> json.toJson(new IgnoredBase64()));
    assertThrows(ForyJsonException.class, () -> json.toJson(new AnyFieldBase64()));
    assertThrows(ForyJsonException.class, () -> json.toJson(new AnyGetterBase64()));
  }

  public static final class Base64Field {
    @JsonByteArray(JsonByteArray.Format.BASE64)
    public byte[] bytes;
  }

  @JsonPropertyOrder({"text", "bytes"})
  public static final class UnicodeBase64 {
    public String text;

    @JsonByteArray(JsonByteArray.Format.BASE64)
    public byte[] bytes;
  }

  public static final class Base64Getter {
    private byte[] bytes;

    public Base64Getter() {}

    public Base64Getter(byte[] bytes) {
      this.bytes = bytes;
    }

    @JsonByteArray(JsonByteArray.Format.BASE64)
    public byte[] getBytes() {
      return bytes;
    }

    public void setBytes(byte[] bytes) {
      this.bytes = bytes;
    }
  }

  public static final class Base64ReadOnly {
    @JsonByteArray(JsonByteArray.Format.BASE64)
    @JsonIgnore(ignoreRead = false, ignoreWrite = true)
    public byte[] bytes;
  }

  public static final class Base64WriteOnly {
    @JsonByteArray(JsonByteArray.Format.BASE64)
    @JsonIgnore(ignoreRead = true, ignoreWrite = false)
    public byte[] bytes;
  }

  public static final class PropertyListBase64 {
    @JsonByteArray(JsonByteArray.Format.BASE64)
    public final byte[] bytes;

    @JsonCreator({"bytes"})
    public PropertyListBase64(byte[] bytes) {
      this.bytes = bytes;
    }
  }

  public static final class ParameterLocalBase64 {
    @JsonByteArray(JsonByteArray.Format.BASE64)
    public final byte[] bytes;

    @JsonCreator
    public ParameterLocalBase64(@JsonProperty("bytes") byte[] bytes) {
      this.bytes = bytes;
    }
  }

  public static final class Base64Always {
    @JsonByteArray(JsonByteArray.Format.BASE64)
    @JsonProperty(include = JsonProperty.Include.ALWAYS)
    public byte[] bytes;
  }

  public static final class DirectCodecBase64 {
    @JsonCodec(Base64ByteArrayCodec.class)
    public byte[] bytes;
  }

  public static final class NonBinaryBase64 {
    @JsonByteArray(JsonByteArray.Format.BASE64)
    public String value = "x";
  }

  public static final class StaticBase64 {
    @JsonByteArray(JsonByteArray.Format.BASE64)
    public static byte[] value = {1};
  }

  public static final class CodecBase64 {
    @JsonByteArray(JsonByteArray.Format.BASE64)
    @JsonCodec(Base64ByteArrayCodec.class)
    public byte[] value = {1};
  }

  public static final class RawBase64 {
    @JsonByteArray(JsonByteArray.Format.BASE64)
    @JsonRawValue
    public byte[] value = {1};
  }

  public static final class IgnoredBase64 {
    @JsonByteArray(JsonByteArray.Format.BASE64)
    @JsonIgnore
    public byte[] value = {1};
  }

  public static final class AnyFieldBase64 {
    @JsonByteArray(JsonByteArray.Format.BASE64)
    @JsonAnyProperty
    public Map<String, byte[]> values;
  }

  public static final class AnyGetterBase64 {
    @JsonByteArray(JsonByteArray.Format.BASE64)
    @JsonAnyGetter
    public Map<String, byte[]> getValues() {
      return null;
    }
  }
}
