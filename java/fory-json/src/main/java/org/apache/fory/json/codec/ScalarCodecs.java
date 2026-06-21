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

package org.apache.fory.json.codec;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Currency;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.apache.fory.json.ForyJsonException;
import org.apache.fory.json.meta.JsonFieldAccessor;
import org.apache.fory.json.reader.JsonReader;
import org.apache.fory.json.resolver.JsonTypeInfo;
import org.apache.fory.json.resolver.JsonTypeResolver;
import org.apache.fory.json.writer.JsonWriter;
import org.apache.fory.json.writer.StringJsonWriter;
import org.apache.fory.json.writer.Utf8JsonWriter;
import org.apache.fory.reflect.ReflectionUtils;
import org.apache.fory.type.BFloat16;
import org.apache.fory.type.Float16;

final class ScalarCodecs {
  private ScalarCodecs() {}

  static final class NaturalCodec extends AbstractJsonCodec {
    static final NaturalCodec INSTANCE = new NaturalCodec();

    private NaturalCodec() {}

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      JsonTypeInfo typeInfo = resolver.getRuntimeTypeInfo(value.getClass());
      typeInfo.codec().write(writer, value, resolver);
    }

    @Override
    void writeStringNonNull(StringJsonWriter writer, Object value, JsonTypeResolver resolver) {
      JsonTypeInfo typeInfo = resolver.getRuntimeTypeInfo(value.getClass());
      typeInfo.codec().writeString(writer, value, resolver);
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      JsonTypeInfo typeInfo = resolver.getRuntimeTypeInfo(value.getClass());
      typeInfo.codec().writeUtf8(writer, value, resolver);
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      char token = reader.peekToken();
      if (token == '"') {
        return reader.readString();
      } else if (token == '{') {
        return MapCodec.readUntyped(reader, resolver);
      } else if (token == '[') {
        return CollectionCodec.readUntyped(reader, resolver);
      } else if (token == 't' || token == 'f') {
        return reader.readBoolean();
      } else if (token == 'n') {
        reader.readNull();
        return null;
      }
      String number = reader.readNumber();
      if (number.indexOf('.') >= 0 || number.indexOf('e') >= 0 || number.indexOf('E') >= 0) {
        return Double.parseDouble(number);
      }
      return Long.parseLong(number);
    }
  }

  static final class StringCodec extends AbstractJsonCodec {
    static final StringCodec INSTANCE = new StringCodec();

    private StringCodec() {}

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeString((String) value);
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeString((String) value);
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      return reader.readString();
    }
  }

  static final class VoidCodec extends AbstractJsonCodec {
    static final VoidCodec INSTANCE = new VoidCodec();

    @Override
    public Object read(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      reader.readNull();
      return null;
    }

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeNull();
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeNull();
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      reader.readNull();
      return null;
    }
  }

  static final class BooleanCodec extends AbstractJsonCodec {
    static final BooleanCodec INSTANCE = new BooleanCodec();

    private BooleanCodec() {}

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeBoolean((Boolean) value);
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeBoolean((Boolean) value);
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      return reader.readBoolean();
    }

    @Override
    public void readField(
        JsonReader reader,
        Object object,
        JsonFieldAccessor accessor,
        JsonTypeInfo typeInfo,
        JsonTypeResolver resolver) {
      if (reader.peekNull()) {
        readFieldDefault(reader, object, accessor, typeInfo, resolver);
      } else if (typeInfo.primitive()) {
        accessor.putBoolean(object, reader.readBoolean());
      } else {
        accessor.putObject(object, reader.readBoolean());
      }
    }
  }

  static final class IntCodec extends AbstractJsonCodec {
    static final IntCodec INSTANCE = new IntCodec();

    private IntCodec() {}

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeInt((Integer) value);
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeInt((Integer) value);
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      return reader.readInt();
    }

    @Override
    public void readField(
        JsonReader reader,
        Object object,
        JsonFieldAccessor accessor,
        JsonTypeInfo typeInfo,
        JsonTypeResolver resolver) {
      if (reader.peekNull()) {
        readFieldDefault(reader, object, accessor, typeInfo, resolver);
      } else if (typeInfo.primitive()) {
        accessor.putInt(object, reader.readInt());
      } else {
        accessor.putObject(object, reader.readInt());
      }
    }
  }

  static final class LongCodec extends AbstractJsonCodec {
    static final LongCodec INSTANCE = new LongCodec();

    private LongCodec() {}

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeLong((Long) value);
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeLong((Long) value);
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      return reader.readLong();
    }

    @Override
    public void readField(
        JsonReader reader,
        Object object,
        JsonFieldAccessor accessor,
        JsonTypeInfo typeInfo,
        JsonTypeResolver resolver) {
      if (reader.peekNull()) {
        readFieldDefault(reader, object, accessor, typeInfo, resolver);
      } else if (typeInfo.primitive()) {
        accessor.putLong(object, reader.readLong());
      } else {
        accessor.putObject(object, reader.readLong());
      }
    }
  }

  static final class ShortCodec extends AbstractJsonCodec {
    static final ShortCodec INSTANCE = new ShortCodec();

    private ShortCodec() {}

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeInt(((Short) value).intValue());
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeInt(((Short) value).intValue());
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      int value = reader.readInt();
      if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
        throw new ForyJsonException("Short overflow");
      }
      return (short) value;
    }

    @Override
    public void readField(
        JsonReader reader,
        Object object,
        JsonFieldAccessor accessor,
        JsonTypeInfo typeInfo,
        JsonTypeResolver resolver) {
      if (reader.peekNull()) {
        readFieldDefault(reader, object, accessor, typeInfo, resolver);
      } else if (typeInfo.primitive()) {
        int value = reader.readInt();
        if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
          throw new ForyJsonException("Short overflow");
        }
        accessor.putShort(object, (short) value);
      } else {
        int value = reader.readInt();
        if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
          throw new ForyJsonException("Short overflow");
        }
        accessor.putObject(object, (short) value);
      }
    }
  }

  static final class ByteCodec extends AbstractJsonCodec {
    static final ByteCodec INSTANCE = new ByteCodec();

    private ByteCodec() {}

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeInt(((Byte) value).intValue());
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeInt(((Byte) value).intValue());
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      int value = reader.readInt();
      if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
        throw new ForyJsonException("Byte overflow");
      }
      return (byte) value;
    }

    @Override
    public void readField(
        JsonReader reader,
        Object object,
        JsonFieldAccessor accessor,
        JsonTypeInfo typeInfo,
        JsonTypeResolver resolver) {
      if (reader.peekNull()) {
        readFieldDefault(reader, object, accessor, typeInfo, resolver);
      } else if (typeInfo.primitive()) {
        int value = reader.readInt();
        if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
          throw new ForyJsonException("Byte overflow");
        }
        accessor.putByte(object, (byte) value);
      } else {
        int value = reader.readInt();
        if (value < Byte.MIN_VALUE || value > Byte.MAX_VALUE) {
          throw new ForyJsonException("Byte overflow");
        }
        accessor.putObject(object, (byte) value);
      }
    }
  }

  static final class FloatCodec extends AbstractJsonCodec {
    static final FloatCodec INSTANCE = new FloatCodec();

    private FloatCodec() {}

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeFloat((Float) value);
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeFloat((Float) value);
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      return Float.parseFloat(reader.readNumber());
    }

    @Override
    public void readField(
        JsonReader reader,
        Object object,
        JsonFieldAccessor accessor,
        JsonTypeInfo typeInfo,
        JsonTypeResolver resolver) {
      if (reader.peekNull()) {
        readFieldDefault(reader, object, accessor, typeInfo, resolver);
      } else if (typeInfo.primitive()) {
        accessor.putFloat(object, Float.parseFloat(reader.readNumber()));
      } else {
        accessor.putObject(object, Float.parseFloat(reader.readNumber()));
      }
    }
  }

  static final class DoubleCodec extends AbstractJsonCodec {
    static final DoubleCodec INSTANCE = new DoubleCodec();

    private DoubleCodec() {}

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeDouble((Double) value);
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeDouble((Double) value);
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      return Double.parseDouble(reader.readNumber());
    }

    @Override
    public void readField(
        JsonReader reader,
        Object object,
        JsonFieldAccessor accessor,
        JsonTypeInfo typeInfo,
        JsonTypeResolver resolver) {
      if (reader.peekNull()) {
        readFieldDefault(reader, object, accessor, typeInfo, resolver);
      } else if (typeInfo.primitive()) {
        accessor.putDouble(object, Double.parseDouble(reader.readNumber()));
      } else {
        accessor.putObject(object, Double.parseDouble(reader.readNumber()));
      }
    }
  }

  static final class CharCodec extends AbstractJsonCodec {
    static final CharCodec INSTANCE = new CharCodec();

    private CharCodec() {}

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeChar((Character) value);
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeChar((Character) value);
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      String value = reader.readString();
      if (value.length() != 1) {
        throw new ForyJsonException("Expected one-character JSON string for char");
      }
      return value.charAt(0);
    }

    @Override
    public void readField(
        JsonReader reader,
        Object object,
        JsonFieldAccessor accessor,
        JsonTypeInfo typeInfo,
        JsonTypeResolver resolver) {
      if (reader.peekNull()) {
        readFieldDefault(reader, object, accessor, typeInfo, resolver);
      } else {
        char value = (Character) readNonNull(reader, typeInfo, resolver);
        if (typeInfo.primitive()) {
          accessor.putChar(object, value);
        } else {
          accessor.putObject(object, value);
        }
      }
    }
  }

  abstract static class StringValueCodec extends AbstractJsonCodec {
    @Override
    final void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeString(toJsonString(value));
    }

    @Override
    final void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeString(toJsonString(value));
    }

    @Override
    final Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      return fromJsonString(reader.readString());
    }

    abstract String toJsonString(Object value);

    abstract Object fromJsonString(String value);
  }

  abstract static class NumberValueCodec extends AbstractJsonCodec {
    @Override
    final void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeNumber(toJsonNumber(value));
    }

    @Override
    final void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeNumber(toJsonNumber(value));
    }

    @Override
    final Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      return fromJsonNumber(reader.readNumber());
    }

    abstract String toJsonNumber(Object value);

    abstract Object fromJsonNumber(String value);
  }

  static final class BigIntegerCodec extends NumberValueCodec {
    static final BigIntegerCodec INSTANCE = new BigIntegerCodec();

    @Override
    String toJsonNumber(Object value) {
      return value.toString();
    }

    @Override
    Object fromJsonNumber(String value) {
      return new BigInteger(value);
    }
  }

  static final class BigDecimalCodec extends NumberValueCodec {
    static final BigDecimalCodec INSTANCE = new BigDecimalCodec();

    @Override
    String toJsonNumber(Object value) {
      return value.toString();
    }

    @Override
    Object fromJsonNumber(String value) {
      return new BigDecimal(value);
    }
  }

  static final class Float16Codec extends AbstractJsonCodec {
    static final Float16Codec INSTANCE = new Float16Codec();

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeFloat(((Float16) value).floatValue());
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeFloat(((Float16) value).floatValue());
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      return Float16.valueOf(Float.parseFloat(reader.readNumber()));
    }
  }

  static final class BFloat16Codec extends AbstractJsonCodec {
    static final BFloat16Codec INSTANCE = new BFloat16Codec();

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeFloat(((BFloat16) value).floatValue());
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeFloat(((BFloat16) value).floatValue());
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      return BFloat16.valueOf(Float.parseFloat(reader.readNumber()));
    }
  }

  static final class StringBuilderCodec extends StringValueCodec {
    static final StringBuilderCodec INSTANCE = new StringBuilderCodec();

    @Override
    String toJsonString(Object value) {
      return value.toString();
    }

    @Override
    Object fromJsonString(String value) {
      return new StringBuilder(value);
    }
  }

  static final class StringBufferCodec extends StringValueCodec {
    static final StringBufferCodec INSTANCE = new StringBufferCodec();

    @Override
    String toJsonString(Object value) {
      return value.toString();
    }

    @Override
    Object fromJsonString(String value) {
      return new StringBuffer(value);
    }
  }

  static final class ClassCodec extends StringValueCodec {
    static final ClassCodec INSTANCE = new ClassCodec();

    @Override
    String toJsonString(Object value) {
      return ((Class<?>) value).getName();
    }

    @Override
    Object fromJsonString(String value) {
      try {
        return ReflectionUtils.loadClass(value);
      } catch (RuntimeException e) {
        throw new ForyJsonException("Cannot load class " + value, e);
      }
    }
  }

  static final class CurrencyCodec extends StringValueCodec {
    static final CurrencyCodec INSTANCE = new CurrencyCodec();

    @Override
    String toJsonString(Object value) {
      return ((Currency) value).getCurrencyCode();
    }

    @Override
    Object fromJsonString(String value) {
      return Currency.getInstance(value);
    }
  }

  static final class UriCodec extends StringValueCodec {
    static final UriCodec INSTANCE = new UriCodec();

    @Override
    String toJsonString(Object value) {
      return value.toString();
    }

    @Override
    Object fromJsonString(String value) {
      return URI.create(value);
    }
  }

  static final class UrlCodec extends StringValueCodec {
    static final UrlCodec INSTANCE = new UrlCodec();

    @Override
    String toJsonString(Object value) {
      return value.toString();
    }

    @Override
    Object fromJsonString(String value) {
      try {
        return new URL(value);
      } catch (MalformedURLException e) {
        throw new ForyJsonException("Invalid URL " + value, e);
      }
    }
  }

  static final class PatternCodec extends StringValueCodec {
    static final PatternCodec INSTANCE = new PatternCodec();

    @Override
    String toJsonString(Object value) {
      return ((Pattern) value).pattern();
    }

    @Override
    Object fromJsonString(String value) {
      return Pattern.compile(value);
    }
  }

  static final class UuidCodec extends StringValueCodec {
    static final UuidCodec INSTANCE = new UuidCodec();

    @Override
    String toJsonString(Object value) {
      return value.toString();
    }

    @Override
    Object fromJsonString(String value) {
      return UUID.fromString(value);
    }
  }

  static final class LocaleCodec extends StringValueCodec {
    static final LocaleCodec INSTANCE = new LocaleCodec();

    @Override
    String toJsonString(Object value) {
      return ((Locale) value).toLanguageTag();
    }

    @Override
    Object fromJsonString(String value) {
      return Locale.forLanguageTag(value);
    }
  }

  static final class CharsetCodec extends StringValueCodec {
    static final CharsetCodec INSTANCE = new CharsetCodec();

    @Override
    String toJsonString(Object value) {
      return ((Charset) value).name();
    }

    @Override
    Object fromJsonString(String value) {
      return Charset.forName(value);
    }
  }

  static final class DateCodec extends AbstractJsonCodec {
    static final DateCodec INSTANCE = new DateCodec();

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeLong(((Date) value).getTime());
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeLong(((Date) value).getTime());
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      return new Date(reader.readLong());
    }
  }

  static final class SqlDateCodec extends AbstractJsonCodec {
    static final SqlDateCodec INSTANCE = new SqlDateCodec();

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeLong(((java.sql.Date) value).getTime());
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeLong(((java.sql.Date) value).getTime());
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      return new java.sql.Date(reader.readLong());
    }
  }

  static final class SqlTimeCodec extends AbstractJsonCodec {
    static final SqlTimeCodec INSTANCE = new SqlTimeCodec();

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeLong(((java.sql.Time) value).getTime());
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeLong(((java.sql.Time) value).getTime());
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      return new java.sql.Time(reader.readLong());
    }
  }

  static final class TimestampCodec extends AbstractJsonCodec {
    static final TimestampCodec INSTANCE = new TimestampCodec();

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeLong(((java.sql.Timestamp) value).getTime());
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeLong(((java.sql.Timestamp) value).getTime());
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      return new java.sql.Timestamp(reader.readLong());
    }
  }

  static final class CalendarCodec extends AbstractJsonCodec {
    static final CalendarCodec INSTANCE = new CalendarCodec();

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeLong(((Calendar) value).getTimeInMillis());
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeLong(((Calendar) value).getTimeInMillis());
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      Calendar calendar = new GregorianCalendar();
      calendar.setTimeInMillis(reader.readLong());
      return calendar;
    }
  }

  static final class TimeZoneCodec extends StringValueCodec {
    static final TimeZoneCodec INSTANCE = new TimeZoneCodec();

    @Override
    String toJsonString(Object value) {
      return ((TimeZone) value).getID();
    }

    @Override
    Object fromJsonString(String value) {
      return TimeZone.getTimeZone(value);
    }
  }

  static final class LocalDateCodec extends StringValueCodec {
    static final LocalDateCodec INSTANCE = new LocalDateCodec();

    @Override
    String toJsonString(Object value) {
      return value.toString();
    }

    @Override
    Object fromJsonString(String value) {
      return LocalDate.parse(value);
    }
  }

  static final class LocalTimeCodec extends StringValueCodec {
    static final LocalTimeCodec INSTANCE = new LocalTimeCodec();

    @Override
    String toJsonString(Object value) {
      return value.toString();
    }

    @Override
    Object fromJsonString(String value) {
      return LocalTime.parse(value);
    }
  }

  static final class LocalDateTimeCodec extends StringValueCodec {
    static final LocalDateTimeCodec INSTANCE = new LocalDateTimeCodec();

    @Override
    String toJsonString(Object value) {
      return value.toString();
    }

    @Override
    Object fromJsonString(String value) {
      return LocalDateTime.parse(value);
    }
  }

  static final class InstantCodec extends StringValueCodec {
    static final InstantCodec INSTANCE = new InstantCodec();

    @Override
    String toJsonString(Object value) {
      return value.toString();
    }

    @Override
    Object fromJsonString(String value) {
      return Instant.parse(value);
    }
  }

  static final class DurationCodec extends StringValueCodec {
    static final DurationCodec INSTANCE = new DurationCodec();

    @Override
    String toJsonString(Object value) {
      return value.toString();
    }

    @Override
    Object fromJsonString(String value) {
      return Duration.parse(value);
    }
  }

  static final class ZoneOffsetCodec extends StringValueCodec {
    static final ZoneOffsetCodec INSTANCE = new ZoneOffsetCodec();

    @Override
    String toJsonString(Object value) {
      return value.toString();
    }

    @Override
    Object fromJsonString(String value) {
      return ZoneOffset.of(value);
    }
  }

  static final class ZoneIdCodec extends StringValueCodec {
    static final ZoneIdCodec INSTANCE = new ZoneIdCodec();

    @Override
    String toJsonString(Object value) {
      return ((ZoneId) value).getId();
    }

    @Override
    Object fromJsonString(String value) {
      return ZoneId.of(value);
    }
  }

  static final class ZonedDateTimeCodec extends StringValueCodec {
    static final ZonedDateTimeCodec INSTANCE = new ZonedDateTimeCodec();

    @Override
    String toJsonString(Object value) {
      return value.toString();
    }

    @Override
    Object fromJsonString(String value) {
      return ZonedDateTime.parse(value);
    }
  }

  static final class YearCodec extends StringValueCodec {
    static final YearCodec INSTANCE = new YearCodec();

    @Override
    String toJsonString(Object value) {
      return value.toString();
    }

    @Override
    Object fromJsonString(String value) {
      return Year.parse(value);
    }
  }

  static final class YearMonthCodec extends StringValueCodec {
    static final YearMonthCodec INSTANCE = new YearMonthCodec();

    @Override
    String toJsonString(Object value) {
      return value.toString();
    }

    @Override
    Object fromJsonString(String value) {
      return YearMonth.parse(value);
    }
  }

  static final class MonthDayCodec extends StringValueCodec {
    static final MonthDayCodec INSTANCE = new MonthDayCodec();

    @Override
    String toJsonString(Object value) {
      return value.toString();
    }

    @Override
    Object fromJsonString(String value) {
      return MonthDay.parse(value);
    }
  }

  static final class PeriodCodec extends StringValueCodec {
    static final PeriodCodec INSTANCE = new PeriodCodec();

    @Override
    String toJsonString(Object value) {
      return value.toString();
    }

    @Override
    Object fromJsonString(String value) {
      return Period.parse(value);
    }
  }

  static final class OffsetTimeCodec extends StringValueCodec {
    static final OffsetTimeCodec INSTANCE = new OffsetTimeCodec();

    @Override
    String toJsonString(Object value) {
      return value.toString();
    }

    @Override
    Object fromJsonString(String value) {
      return OffsetTime.parse(value);
    }
  }

  static final class OffsetDateTimeCodec extends StringValueCodec {
    static final OffsetDateTimeCodec INSTANCE = new OffsetDateTimeCodec();

    @Override
    String toJsonString(Object value) {
      return value.toString();
    }

    @Override
    Object fromJsonString(String value) {
      return OffsetDateTime.parse(value);
    }
  }

  static final class AtomicBooleanCodec extends AbstractJsonCodec {
    static final AtomicBooleanCodec INSTANCE = new AtomicBooleanCodec();

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeBoolean(((AtomicBoolean) value).get());
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeBoolean(((AtomicBoolean) value).get());
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      return new AtomicBoolean(reader.readBoolean());
    }
  }

  static final class AtomicIntegerCodec extends AbstractJsonCodec {
    static final AtomicIntegerCodec INSTANCE = new AtomicIntegerCodec();

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeInt(((AtomicInteger) value).get());
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeInt(((AtomicInteger) value).get());
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      return new AtomicInteger(reader.readInt());
    }
  }

  static final class AtomicLongCodec extends AbstractJsonCodec {
    static final AtomicLongCodec INSTANCE = new AtomicLongCodec();

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeLong(((AtomicLong) value).get());
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeLong(((AtomicLong) value).get());
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      return new AtomicLong(reader.readLong());
    }
  }

  static final class AtomicReferenceCodec extends AbstractJsonCodec {
    private final JsonTypeInfo valueTypeInfo;
    private final JsonCodec valueCodec;

    AtomicReferenceCodec(java.lang.reflect.Type valueType, JsonTypeResolver resolver) {
      Class<?> valueRawType = CodecUtils.rawType(valueType, Object.class);
      valueTypeInfo = resolver.getTypeInfo(valueType, valueRawType);
      valueCodec = valueTypeInfo.codec();
    }

    @Override
    public Object read(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      Object value =
          reader.peekNull()
              ? readNullValue(reader)
              : valueCodec.read(reader, valueTypeInfo, resolver);
      return new AtomicReference<>(value);
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      return new AtomicReference<>(valueCodec.read(reader, valueTypeInfo, resolver));
    }

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      valueCodec.write(writer, ((AtomicReference<?>) value).get(), resolver);
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      valueCodec.writeUtf8(writer, ((AtomicReference<?>) value).get(), resolver);
    }
  }

  static final class OptionalCodec extends AbstractJsonCodec {
    private final JsonTypeInfo valueTypeInfo;
    private final JsonCodec valueCodec;

    OptionalCodec(java.lang.reflect.Type valueType, JsonTypeResolver resolver) {
      Class<?> valueRawType = CodecUtils.rawType(valueType, Object.class);
      valueTypeInfo = resolver.getTypeInfo(valueType, valueRawType);
      valueCodec = valueTypeInfo.codec();
    }

    @Override
    public Object read(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      if (reader.peekNull()) {
        reader.readNull();
        return Optional.empty();
      }
      return Optional.ofNullable(valueCodec.read(reader, valueTypeInfo, resolver));
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      return Optional.ofNullable(valueCodec.read(reader, valueTypeInfo, resolver));
    }

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      Optional<?> optional = (Optional<?>) value;
      if (optional.isPresent()) {
        valueCodec.write(writer, optional.get(), resolver);
      } else {
        writer.writeNull();
      }
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      Optional<?> optional = (Optional<?>) value;
      if (optional.isPresent()) {
        valueCodec.writeUtf8(writer, optional.get(), resolver);
      } else {
        writer.writeNull();
      }
    }
  }

  static final class OptionalIntCodec extends AbstractJsonCodec {
    static final OptionalIntCodec INSTANCE = new OptionalIntCodec();

    @Override
    public Object read(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      if (reader.peekNull()) {
        reader.readNull();
        return OptionalInt.empty();
      }
      return OptionalInt.of(reader.readInt());
    }

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      OptionalInt optional = (OptionalInt) value;
      if (optional.isPresent()) {
        writer.writeInt(optional.getAsInt());
      } else {
        writer.writeNull();
      }
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writeNonNull(writer, value, resolver);
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      return OptionalInt.of(reader.readInt());
    }
  }

  static final class OptionalLongCodec extends AbstractJsonCodec {
    static final OptionalLongCodec INSTANCE = new OptionalLongCodec();

    @Override
    public Object read(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      if (reader.peekNull()) {
        reader.readNull();
        return OptionalLong.empty();
      }
      return OptionalLong.of(reader.readLong());
    }

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      OptionalLong optional = (OptionalLong) value;
      if (optional.isPresent()) {
        writer.writeLong(optional.getAsLong());
      } else {
        writer.writeNull();
      }
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writeNonNull(writer, value, resolver);
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      return OptionalLong.of(reader.readLong());
    }
  }

  static final class OptionalDoubleCodec extends AbstractJsonCodec {
    static final OptionalDoubleCodec INSTANCE = new OptionalDoubleCodec();

    @Override
    public Object read(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      if (reader.peekNull()) {
        reader.readNull();
        return OptionalDouble.empty();
      }
      return OptionalDouble.of(Double.parseDouble(reader.readNumber()));
    }

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      OptionalDouble optional = (OptionalDouble) value;
      if (optional.isPresent()) {
        writer.writeDouble(optional.getAsDouble());
      } else {
        writer.writeNull();
      }
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writeNonNull(writer, value, resolver);
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      return OptionalDouble.of(Double.parseDouble(reader.readNumber()));
    }
  }

  static final class ByteBufferCodec extends AbstractJsonCodec {
    static final ByteBufferCodec INSTANCE = new ByteBufferCodec();

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writeBuffer(writer, (ByteBuffer) value);
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writeBuffer(writer, (ByteBuffer) value);
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      byte[] bytes = new byte[16];
      int size = 0;
      reader.expect('[');
      if (!reader.consume(']')) {
        do {
          if (size == bytes.length) {
            bytes = Arrays.copyOf(bytes, size << 1);
          }
          int value = reader.readInt();
          if (value < Byte.MIN_VALUE || value > 255) {
            throw new ForyJsonException("ByteBuffer element out of byte range: " + value);
          }
          bytes[size++] = (byte) value;
        } while (reader.consume(','));
        reader.expect(']');
      }
      return ByteBuffer.wrap(Arrays.copyOf(bytes, size));
    }

    private void writeBuffer(JsonWriter writer, ByteBuffer value) {
      ByteBuffer buffer = value.duplicate();
      writer.writeArrayStart();
      int index = 0;
      while (buffer.hasRemaining()) {
        writer.writeComma(index++);
        writer.writeInt(buffer.get());
      }
      writer.writeArrayEnd();
    }
  }

  static final class EnumCodec extends AbstractJsonCodec {
    private final Class<?> type;
    private final Map<String, Enum<?>> values;

    EnumCodec(Class<?> type) {
      this.type = type;
      Enum<?>[] constants = (Enum<?>[]) type.getEnumConstants();
      values = new HashMap<>(constants.length * 2);
      for (Enum<?> constant : constants) {
        values.put(constant.name(), constant);
      }
    }

    @Override
    void writeNonNull(JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeString(((Enum<?>) value).name());
    }

    @Override
    void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
      writer.writeString(((Enum<?>) value).name());
    }

    @Override
    Object readNonNull(JsonReader reader, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
      String name = reader.readString();
      Enum<?> value = values.get(name);
      if (value == null) {
        throw new ForyJsonException("Unknown enum value " + name + " for " + type);
      }
      return value;
    }
  }

  private static Object readNullValue(JsonReader reader) {
    reader.readNull();
    return null;
  }
}
