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

import java.io.File;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.fory.json.codec.JsonCodec;
import org.apache.fory.json.data.BoxedScalars;
import org.apache.fory.json.data.CoreScalarFields;
import org.apache.fory.json.data.NaturalObjectValue;
import org.apache.fory.json.data.NaturalValues;
import org.apache.fory.json.data.NumericBoundaries;
import org.apache.fory.json.data.PublicFields;
import org.apache.fory.json.reader.JsonReader;
import org.apache.fory.json.reader.Latin1JsonReader;
import org.apache.fory.json.reader.Utf16JsonReader;
import org.apache.fory.json.reader.Utf8JsonReader;
import org.apache.fory.json.resolver.JsonTypeInfo;
import org.apache.fory.json.resolver.JsonTypeResolver;
import org.apache.fory.json.writer.JsonWriter;
import org.apache.fory.json.writer.StringJsonWriter;
import org.apache.fory.json.writer.Utf8JsonWriter;
import org.apache.fory.reflect.TypeRef;
import org.testng.annotations.Test;

public class JsonScalarTest extends ForyJsonTestModels {
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
  public void readNumericBoundaries() {
    ForyJson json = ForyJson.builder().build();
    String latin1 =
        "{\"intMax\":2147483647,\"intMin\":-2147483648,"
            + "\"longMax\":9223372036854775807,\"longMin\":-9223372036854775808,"
            + "\"small\":-7,\"text\":\"café\"}";
    String utf16 = latin1.replace("café", ZH_TEXT);
    assertNumericBoundaries(json.fromJson(latin1, NumericBoundaries.class), "café");
    assertNumericBoundaries(json.fromJson(utf16, NumericBoundaries.class), ZH_TEXT);
    assertNumericBoundaries(
        json.fromJson(utf16.getBytes(StandardCharsets.UTF_8), NumericBoundaries.class), ZH_TEXT);

    assertThrows(ForyJsonException.class, () -> json.fromJson("2147483648", int.class));
    assertThrows(ForyJsonException.class, () -> json.fromJson("-2147483649", int.class));
    assertThrows(ForyJsonException.class, () -> json.fromJson("1.0", int.class));
    assertThrows(ForyJsonException.class, () -> json.fromJson("9223372036854775808", long.class));
    assertThrows(
        ForyJsonException.class,
        () -> json.fromJson("-9223372036854775809".getBytes(StandardCharsets.UTF_8), long.class));
    assertThrows(
        ForyJsonException.class,
        () ->
            json.fromJson(
                "{\"intMax\":2147483648,\"text\":\"" + ZH_TEXT + "\"}", NumericBoundaries.class));
  }

  @Test
  public void writeNumericBoundaries() {
    ForyJson json = ForyJson.builder().build();
    NumericBoundaries value = new NumericBoundaries();
    value.intMax = Integer.MAX_VALUE;
    value.intMin = Integer.MIN_VALUE;
    value.longMax = Long.MAX_VALUE;
    value.longMin = Long.MIN_VALUE;
    value.small = -7;
    value.text = "ok";
    String expected =
        "{\"intMax\":2147483647,\"intMin\":-2147483648,"
            + "\"longMax\":9223372036854775807,\"longMin\":-9223372036854775808,"
            + "\"small\":-7,\"text\":\"ok\"}";
    assertEquals(json.toJson(value), expected);
    assertEquals(new String(json.toJsonBytes(value), StandardCharsets.UTF_8), expected);
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
            + "\"maybe\":\"yes\",\"optionalInt\":4,\"timeZone\":\"UTC\","
            + "\"uri\":\"https://fory.apache.org/json\","
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
    assertEquals(read.uri, value.uri);
    assertEquals(read.url, value.url);
    assertEquals(read.uuid, value.uuid);
  }

  @Test
  public void writeReadAtomicScalars() {
    ForyJson json = ForyJson.builder().build();
    assertEquals(json.toJson(new AtomicBoolean(true)), "true");
    assertEquals(json.fromJson("false", AtomicBoolean.class).get(), false);
    assertEquals(json.toJson(new AtomicInteger(12)), "12");
    assertEquals(json.fromJson("13", AtomicInteger.class).get(), 13);
    assertEquals(json.toJson(new AtomicLong(14L)), "14");
    assertEquals(json.fromJson("15", AtomicLong.class).get(), 15L);
    assertEquals(json.toJson(new AtomicReference<>("value")), "\"value\"");

    AtomicReference<String> value =
        json.fromJson("\"typed\"", new TypeRef<AtomicReference<String>>() {});
    assertEquals(value.get(), "typed");
    AtomicReference<String> nullValue =
        json.fromJson("null", new TypeRef<AtomicReference<String>>() {});
    assertEquals(nullValue.get(), null);
  }

  @Test
  public void writeUtf8ScalarFormats() {
    ForyJson json = ForyJson.builder().build();
    UUID uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    assertEquals(
        new String(json.toJsonBytes(uuid), StandardCharsets.UTF_8),
        "\"123e4567-e89b-12d3-a456-426614174000\"");
    assertEquals(
        new String(json.toJsonBytes(LocalDate.of(2024, 2, 3)), StandardCharsets.UTF_8),
        "\"2024-02-03\"");

    OffsetDateTimeFields fields = new OffsetDateTimeFields();
    fields.value = OffsetDateTime.of(2024, 2, 3, 4, 5, 0, 0, ZoneOffset.UTC);
    assertEquals(
        new String(json.toJsonBytes(fields), StandardCharsets.UTF_8),
        "{\"value\":\"2024-02-03T04:05Z\"}");
    fields.value = OffsetDateTime.of(2024, 2, 3, 4, 5, 0, 1_000_000, ZoneOffset.UTC);
    assertEquals(
        new String(json.toJsonBytes(fields), StandardCharsets.UTF_8),
        "{\"value\":\"2024-02-03T04:05:00.001Z\"}");
    fields.value = OffsetDateTime.of(2024, 2, 3, 4, 5, 6, 120_000_000, ZoneOffset.UTC);
    assertEquals(
        new String(json.toJsonBytes(fields), StandardCharsets.UTF_8),
        "{\"value\":\"2024-02-03T04:05:06.120Z\"}");
    fields.value = OffsetDateTime.of(2024, 2, 3, 4, 5, 6, 123_400_000, ZoneOffset.UTC);
    assertEquals(
        new String(json.toJsonBytes(fields), StandardCharsets.UTF_8),
        "{\"value\":\"2024-02-03T04:05:06.123400Z\"}");
    fields.value = OffsetDateTime.of(2024, 2, 3, 4, 5, 6, 1_000, ZoneOffset.UTC);
    assertEquals(
        new String(json.toJsonBytes(fields), StandardCharsets.UTF_8),
        "{\"value\":\"2024-02-03T04:05:06.000001Z\"}");
    fields.value = OffsetDateTime.of(2024, 2, 3, 4, 5, 6, 123456789, ZoneOffset.UTC);
    assertEquals(
        new String(json.toJsonBytes(fields), StandardCharsets.UTF_8),
        "{\"value\":\"2024-02-03T04:05:06.123456789Z\"}");

    OffsetDateTime offset =
        OffsetDateTime.of(2024, 2, 3, 4, 5, 6, 123456789, ZoneOffset.ofHoursMinutes(8, 30));
    fields.value = offset;
    assertEquals(
        new String(json.toJsonBytes(fields), StandardCharsets.UTF_8),
        "{\"value\":\"" + offset + "\"}");
  }

  @Test
  public void writeGeneratedUtf8Scalars() {
    ForyJson json = ForyJson.builder().build();
    Utf8ScalarFields fields = new Utf8ScalarFields();
    fields.uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    fields.decimal = new BigDecimal("12345.6789");
    fields.date = LocalDate.of(2024, 2, 3);
    fields.timestamp = OffsetDateTime.of(2024, 2, 3, 4, 5, 6, 123456789, ZoneOffset.UTC);
    String expected =
        "{\"uuid\":\"123e4567-e89b-12d3-a456-426614174000\","
            + "\"decimal\":12345.6789,"
            + "\"date\":\"2024-02-03\","
            + "\"timestamp\":\"2024-02-03T04:05:06.123456789Z\"}";
    assertEquals(new String(json.toJsonBytes(fields), StandardCharsets.UTF_8), expected);
    assertEquals(json.toJson(fields), expected);
    assertGeneratedWhenSupported(json, Utf8ScalarFields.class);
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
    assertEquals(
        json.fromJson("0.100".getBytes(StandardCharsets.UTF_8), BigDecimal.class),
        new BigDecimal("0.100"));
    assertEquals(
        json.fromJson(
            "12345678901234567890.123".getBytes(StandardCharsets.UTF_8), BigDecimal.class),
        new BigDecimal("12345678901234567890.123"));
  }

  @Test
  public void readGeneratedUtf8BigDecimal() {
    ForyJson json = ForyJson.builder().build();
    byte[] input =
        ("{\"uuid\":\"123e4567-e89b-12d3-a456-426614174000\","
                + "\"decimal\":0.12345678901234567,"
                + "\"date\":\"2024-02-03\","
                + "\"timestamp\":\"2024-02-03T04:05:06Z\"}")
            .getBytes(StandardCharsets.UTF_8);
    Utf8ScalarFields fields = json.fromJson(input, Utf8ScalarFields.class);
    assertEquals(fields.decimal, new BigDecimal("0.12345678901234567"));
  }

  @Test
  public void readUntypedLargeInteger() {
    ForyJson json = ForyJson.builder().build();
    BigInteger unsigned = new BigInteger("18446744073709550616");
    assertEquals(json.fromJson(unsigned.toString(), Object.class), unsigned);
    JSONObject object = json.fromJson("{\"count\":18446744073709550616}", JSONObject.class);
    assertEquals(object.get("count"), unsigned);
  }

  @Test
  public void rejectClassTypeByDefault() {
    ForyJson json = ForyJson.builder().build();
    assertThrows(ForyJsonException.class, () -> json.toJson(String.class));
    assertThrows(ForyJsonException.class, () -> json.toJson(int.class));
    assertThrows(ForyJsonException.class, () -> json.fromJson("\"java.lang.String\"", Class.class));
    assertThrows(ForyJsonException.class, () -> json.fromJson("\"int\"", Class.class));
  }

  @Test
  public void rejectClassFields() {
    ForyJson json = ForyJson.builder().build();
    assertThrows(ForyJsonException.class, () -> json.toJson(new ClassFieldHolder()));
    assertThrows(
        ForyJsonException.class,
        () -> json.fromJson("{\"type\":\"java.lang.String\"}", ClassFieldHolder.class));
  }

  @Test
  public void rejectClassArrays() {
    ForyJson json = ForyJson.builder().build();
    assertThrows(ForyJsonException.class, () -> json.toJson(new Class<?>[] {String.class}));
    assertThrows(
        ForyJsonException.class, () -> json.fromJson("[\"java.lang.String\"]", Class[].class));
    assertThrows(ForyJsonException.class, () -> json.toJson(new ClassArrayFields()));
    assertThrows(
        ForyJsonException.class,
        () -> json.fromJson("{\"types\":[\"java.lang.String\"]}", ClassArrayFields.class));
  }

  @Test
  public void writeReadFileAndPath() {
    ForyJson json = ForyJson.builder().build();
    File file = new File("fory-json-file.txt");
    Path path = Paths.get("fory-json-path.txt");
    assertEquals(json.toJson(file), "\"fory-json-file.txt\"");
    assertEquals(json.fromJson("\"fory-json-file.txt\"", File.class), file);
    assertEquals(json.toJson(path), "\"fory-json-path.txt\"");
    assertEquals(json.fromJson("\"fory-json-path.txt\"", Path.class), path);

    FilePathFields fields =
        json.fromJson(
            "{\"file\":\"fory-json-file.txt\",\"path\":\"fory-json-path.txt\"}",
            FilePathFields.class);
    assertEquals(fields.file, file);
    assertEquals(fields.path, path);
    assertEquals(
        json.toJson(fields), "{\"file\":\"fory-json-file.txt\",\"path\":\"fory-json-path.txt\"}");
  }

  @Test
  public void readLocalDateFromDateTime() {
    ForyJson json = ForyJson.builder().build();
    LocalDate expected = LocalDate.of(2023, 7, 2);
    assertEquals(json.fromJson("\"2023-07-02T16:00:00.000Z\"", LocalDate.class), expected);
    assertEquals(
        json.fromJson(
            "\"2023-07-02T16:00:00.000Z\"".getBytes(StandardCharsets.UTF_8), LocalDate.class),
        expected);
    LocalDateFields fields =
        json.fromJson("{\"value\":\"2023-07-02T16:00:00.000Z\"}", LocalDateFields.class);
    assertEquals(fields.value, expected);
  }

  @Test
  public void readOffsetDateTime() {
    ForyJson json = ForyJson.builder().build();
    OffsetDateTime utc = OffsetDateTime.of(2024, 2, 3, 4, 5, 6, 0, ZoneOffset.UTC);
    assertEquals(json.fromJson("\"2024-02-03T04:05:06Z\"", OffsetDateTime.class), utc);
    assertEquals(
        json.fromJson(
            "\"2024-02-03T04:05:06\\u005A\"".getBytes(StandardCharsets.UTF_8),
            OffsetDateTime.class),
        utc);

    OffsetDateTime nanos =
        OffsetDateTime.of(2024, 2, 3, 4, 5, 6, 123456789, ZoneOffset.ofHoursMinutes(8, 30));
    assertEquals(
        json.fromJson(
            "\"2024-02-03T04:05:06.123456789+08:30\"".getBytes(StandardCharsets.UTF_8),
            OffsetDateTime.class),
        nanos);

    OffsetDateTime minutePrecision =
        OffsetDateTime.of(2024, 2, 3, 4, 5, 0, 0, ZoneOffset.ofHoursMinutes(-5, -30));
    OffsetDateTimeFields fields =
        json.fromJson("{\"value\":\"2024-02-03T04:05-05:30\"}", OffsetDateTimeFields.class);
    assertEquals(fields.value, minutePrecision);
  }

  @Test
  public void objectFieldUsesUtf8Codec() {
    ForyJson json =
        ForyJson.builder().registerCodec(ModeAwareValue.class, new ModeAwareCodec()).build();
    ModeAwareHolder holder =
        json.fromJson("{\"value\":{}}".getBytes(StandardCharsets.UTF_8), ModeAwareHolder.class);
    assertEquals(holder.value.mode, "utf8");
  }

  @Test
  public void wrapStringScalarParseErrors() {
    ForyJson json = ForyJson.builder().build();
    assertThrows(
        ForyJsonException.class,
        () -> json.fromJson("\"2024-02-03 04:05:06\"", LocalDateTime.class));
  }

  @Test
  public void rejectLeadingZero() {
    ForyJson json = ForyJson.builder().build();
    assertThrows(ForyJsonException.class, () -> json.fromJson("01", int.class));
    assertThrows(
        ForyJsonException.class,
        () -> json.fromJson("{\"id\":01}".getBytes(StandardCharsets.UTF_8), PublicFields.class));
  }

  public static final class ClassFieldHolder {
    public Class<?> type = String.class;
  }

  public static final class ClassArrayFields {
    public Class<?>[] types = new Class<?>[] {String.class};
  }

  public static final class FilePathFields {
    public File file;
    public Path path;
  }

  public static final class LocalDateFields {
    public LocalDate value;
  }

  public static final class OffsetDateTimeFields {
    public OffsetDateTime value;
  }

  public static final class Utf8ScalarFields {
    public UUID uuid;
    public BigDecimal decimal;
    public LocalDate date;
    public OffsetDateTime timestamp;
  }

  public static final class ModeAwareHolder {
    public ModeAwareValue value;
  }

  public static final class ModeAwareValue {
    public final String mode;

    ModeAwareValue(String mode) {
      this.mode = mode;
    }
  }

  private static final class ModeAwareCodec implements JsonCodec {
    @Override
    public void write(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeNull();
    }

    @Override
    public void writeString(StringJsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeNull();
    }

    @Override
    public void writeUtf8(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeNull();
    }

    @Override
    public Object read(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      reader.skipValue();
      return new ModeAwareValue("generic");
    }

    @Override
    public Object readLatin1(
        Latin1JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      reader.skipValue();
      return new ModeAwareValue("latin1");
    }

    @Override
    public Object readUtf16(
        Utf16JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      reader.skipValue();
      return new ModeAwareValue("utf16");
    }

    @Override
    public Object readUtf8(
        Utf8JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      reader.skipValue();
      return new ModeAwareValue("utf8");
    }
  }
}
