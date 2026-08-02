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

package org.apache.fory.graalvm;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.fory.json.ForyJson;
import org.apache.fory.json.annotation.JsonAnyGetter;
import org.apache.fory.json.annotation.JsonAnySetter;
import org.apache.fory.json.annotation.JsonCodec;
import org.apache.fory.json.annotation.JsonCreator;
import org.apache.fory.json.annotation.JsonProperty;
import org.apache.fory.json.annotation.JsonType;
import org.apache.fory.json.annotation.JsonValue;
import org.apache.fory.json.codec.JsonValueCodec;
import org.apache.fory.json.codec.ObjectCodec;
import org.apache.fory.json.reader.Latin1JsonReader;
import org.apache.fory.json.reader.Utf16JsonReader;
import org.apache.fory.json.reader.Utf8JsonReader;
import org.apache.fory.json.writer.StringJsonWriter;
import org.apache.fory.json.writer.Utf8JsonWriter;
import org.apache.fory.platform.GraalvmSupport;
import org.apache.fory.util.Preconditions;

/** Native-image acceptance coverage when no {@code ForyJsonProvider} is reachable. */
public final class ForyJsonNoProviderExample {
  private static final String NATIVE_INTERPRETER_MESSAGE =
      "Fory JSON is using interpreted codecs because the current configuration was not included "
          + "in this native image. Return this configuration from a reachable "
          + "@ForyJsonProvider to enable generated codecs.";

  private ForyJsonNoProviderExample() {}

  public static void main(String[] args) {
    PrintStream originalOut = System.out;
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    try (PrintStream testOut = new PrintStream(captured, true, StandardCharsets.UTF_8)) {
      System.setOut(testOut);
      try {
        exercise(ForyJson.builder().build());
      } finally {
        System.setOut(originalOut);
      }
    }
    String output = new String(captured.toByteArray(), StandardCharsets.UTF_8);
    if (GraalvmSupport.isGraalRuntime()) {
      int occurrences = countOccurrences(output, NATIVE_INTERPRETER_MESSAGE);
      Preconditions.checkArgument(
          occurrences == 1,
          "Expected one Native Image interpreted-codec message, found "
              + occurrences
              + ": "
              + output);
    }
    originalOut.print(output);
    originalOut.println("Fory JSON without provider succeed");
  }

  private static void exercise(ForyJson json) {
    Bean bean = new Bean();
    bean.setName("bean");
    bean.putExtra("dynamic", "extra");
    Model value =
        new Model(
            7, new Probe("value"), bean, new RecordValue(8, "record"), FactoryValue.create(9));
    String encoded = json.toJson(value);
    Preconditions.checkArgument(json.toJsonBytes(value).length != 0);
    Preconditions.checkArgument(json.fromJson(encoded, Model.class).equals(value));
    Preconditions.checkArgument(
        json.fromJson(encoded.replace("value", "\u4f60"), Model.class)
            .probe
            .value
            .equals("\u4f60"));
    Preconditions.checkArgument(
        json.fromJson(encoded.getBytes(StandardCharsets.UTF_8), Model.class).equals(value));
    Preconditions.checkArgument(
        json.toJson(new DirectValueRecord("record-value")).equals("\"record-value\""));
    Preconditions.checkArgument(
        json.fromJson("\"decoded-record\"", DirectValueRecord.class)
            .equals(new DirectValueRecord("decoded-record")));
    Preconditions.checkArgument(json.toJson(DirectValueEnum.READY).equals("\"ready\""));
    Preconditions.checkArgument(
        json.fromJson("\"done\"", DirectValueEnum.class) == DirectValueEnum.DONE);
  }

  private static int countOccurrences(String value, String target) {
    int count = 0;
    int offset = 0;
    while ((offset = value.indexOf(target, offset)) >= 0) {
      count++;
      offset += target.length();
    }
    return count;
  }

  @JsonType
  public static final class Model {
    private final int id;

    @JsonCodec(ProbeCodec.class)
    private final Probe probe;

    private final Bean bean;
    private final RecordValue record;
    private final FactoryValue factory;

    @JsonCreator
    public Model(
        @JsonProperty("id") int id,
        @JsonProperty("probe") Probe probe,
        @JsonProperty("bean") Bean bean,
        @JsonProperty("record") RecordValue record,
        @JsonProperty("factory") FactoryValue factory) {
      this.id = id;
      this.probe = probe;
      this.bean = bean;
      this.record = record;
      this.factory = factory;
    }

    public int getId() {
      return id;
    }

    public Probe getProbe() {
      return probe;
    }

    public Bean getBean() {
      return bean;
    }

    public RecordValue getRecord() {
      return record;
    }

    public FactoryValue getFactory() {
      return factory;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof Model)) {
        return false;
      }
      Model that = (Model) other;
      return id == that.id
          && probe.equals(that.probe)
          && bean.equals(that.bean)
          && record.equals(that.record)
          && factory.equals(that.factory);
    }

    @Override
    public int hashCode() {
      int result = 31 * id + probe.hashCode();
      result = 31 * result + bean.hashCode();
      result = 31 * result + record.hashCode();
      return 31 * result + factory.hashCode();
    }
  }

  @JsonType
  public static final class Bean {
    private String name;
    private final transient Map<String, String> extra = new LinkedHashMap<>();

    public Bean() {}

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    @JsonAnyGetter
    public Map<String, String> extra() {
      return extra;
    }

    @JsonAnySetter
    public void putExtra(String key, String value) {
      extra.put(key, value);
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof Bean)) {
        return false;
      }
      Bean that = (Bean) other;
      return name.equals(that.name) && extra.equals(that.extra);
    }

    @Override
    public int hashCode() {
      return 31 * name.hashCode() + extra.hashCode();
    }
  }

  @JsonType
  public record RecordValue(int rank, String text) {}

  @JsonType
  public record DirectValueRecord(@JsonValue String value) {
    @JsonCreator
    public DirectValueRecord {}
  }

  @JsonType
  public enum DirectValueEnum {
    READY("ready"),
    DONE("done");

    private final String value;

    DirectValueEnum(String value) {
      this.value = value;
    }

    @JsonValue
    public String value() {
      return value;
    }

    @JsonCreator
    public static DirectValueEnum fromValue(String value) {
      for (DirectValueEnum candidate : values()) {
        if (candidate.value.equals(value)) {
          return candidate;
        }
      }
      throw new IllegalArgumentException("Unknown direct enum value " + value);
    }
  }

  @JsonType
  public static final class FactoryValue {
    private final int code;

    private FactoryValue(int code) {
      this.code = code;
    }

    @JsonCreator
    public static FactoryValue create(@JsonProperty("code") int code) {
      return new FactoryValue(code);
    }

    public int getCode() {
      return code;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof FactoryValue && code == ((FactoryValue) other).code;
    }

    @Override
    public int hashCode() {
      return code;
    }
  }

  public static final class Probe {
    private final String value;

    private Probe(String value) {
      this.value = value;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Probe && value.equals(((Probe) other).value);
    }

    @Override
    public int hashCode() {
      return value.hashCode();
    }
  }

  public static final class ProbeCodec implements JsonValueCodec<Probe> {
    public ProbeCodec() {}

    @Override
    public void writeString(StringJsonWriter writer, Probe value) {
      checkInterpreted(writer.typeResolver().getTypeInfo(Model.class, Model.class).stringWriter());
      writer.writeString(value == null ? null : value.value);
    }

    @Override
    public void writeUtf8(Utf8JsonWriter writer, Probe value) {
      checkInterpreted(writer.typeResolver().getTypeInfo(Model.class, Model.class).utf8Writer());
      writer.writeString(value == null ? null : value.value);
    }

    @Override
    public Probe readLatin1(Latin1JsonReader reader) {
      checkInterpreted(reader.typeResolver().getTypeInfo(Model.class, Model.class).latin1Reader());
      return reader.tryReadNullToken() ? null : new Probe(reader.readString());
    }

    @Override
    public Probe readUtf16(Utf16JsonReader reader) {
      checkInterpreted(reader.typeResolver().getTypeInfo(Model.class, Model.class).utf16Reader());
      return reader.tryReadNullToken() ? null : new Probe(reader.readString());
    }

    @Override
    public Probe readUtf8(Utf8JsonReader reader) {
      checkInterpreted(reader.typeResolver().getTypeInfo(Model.class, Model.class).utf8Reader());
      return reader.tryReadNullToken() ? null : new Probe(reader.readString());
    }

    private static void checkInterpreted(Object capability) {
      if (GraalvmSupport.isGraalRuntime()) {
        Preconditions.checkArgument(capability instanceof ObjectCodec<?>);
      }
    }
  }
}
