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

package org.apache.fory.benchmark.json;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.apache.fory.json.ForyJson;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
@Threads(1)
public class Fastjson2EishayWriteSuite {
  @State(Scope.Thread)
  public static class EishayState {
    ForyJson foryJson;
    ObjectMapper mapper;
    Gson gson;
    MediaContent mediaContent;

    @Setup
    public void setup() {
      foryJson = ForyJson.builder().build();
      mapper = new ObjectMapper();
      gson = new Gson();
      String json = readResource();
      mediaContent = JSONReader.of(json).read(MediaContent.class);
      assertSameJson(foryJson.toJson(mediaContent), JSON.toJSONString(mediaContent));
      assertSameJson(foryJson.toJsonBytes(mediaContent), JSON.toJSONBytes(mediaContent));
    }
  }

  @Benchmark
  public void foryToJsonString(EishayState state, Blackhole bh) {
    bh.consume(state.foryJson.toJson(state.mediaContent));
  }

  @Benchmark
  public void fastjson2ToJsonString(EishayState state, Blackhole bh) {
    bh.consume(JSON.toJSONString(state.mediaContent));
  }

  @Benchmark
  public void jacksonToJsonString(EishayState state, Blackhole bh) throws IOException {
    bh.consume(state.mapper.writeValueAsString(state.mediaContent));
  }

  @Benchmark
  public void gsonToJsonString(EishayState state, Blackhole bh) {
    bh.consume(state.gson.toJson(state.mediaContent));
  }

  @Benchmark
  public void foryToJsonBytes(EishayState state, Blackhole bh) {
    bh.consume(state.foryJson.toJsonBytes(state.mediaContent));
  }

  @Benchmark
  public void fastjson2ToJsonBytes(EishayState state, Blackhole bh) {
    bh.consume(JSON.toJSONBytes(state.mediaContent));
  }

  @Benchmark
  public void jacksonToJsonBytes(EishayState state, Blackhole bh) throws IOException {
    bh.consume(state.mapper.writeValueAsBytes(state.mediaContent));
  }

  @Benchmark
  public void gsonToJsonBytes(EishayState state, Blackhole bh) {
    bh.consume(state.gson.toJson(state.mediaContent).getBytes(StandardCharsets.UTF_8));
  }

  private static String readResource() {
    InputStream input =
        Fastjson2EishayWriteSuite.class.getClassLoader().getResourceAsStream("data/eishay.json");
    if (input == null) {
      throw new IllegalStateException("Missing data/eishay.json");
    }
    try (InputStream closeable = input;
        InputStreamReader reader = new InputStreamReader(closeable, StandardCharsets.UTF_8)) {
      char[] buffer = new char[1024];
      StringBuilder builder = new StringBuilder();
      int read;
      while ((read = reader.read(buffer)) != -1) {
        builder.append(buffer, 0, read);
      }
      return builder.toString();
    } catch (IOException e) {
      throw new IllegalStateException("Unable to read data/eishay.json", e);
    }
  }

  private static void assertSameJson(String left, String right) {
    if (!JSON.parseObject(left).equals(JSON.parseObject(right))) {
      throw new IllegalStateException("JSON String outputs are not equivalent");
    }
  }

  private static void assertSameJson(byte[] left, byte[] right) {
    if (!JSON.parseObject(left).equals(JSON.parseObject(right))) {
      throw new IllegalStateException("JSON byte outputs are not equivalent");
    }
  }

  public static final class MediaContent implements java.io.Serializable {
    private Media media;
    private List<Image> images;

    public MediaContent() {}

    public MediaContent(Media media, List<Image> images) {
      this.media = media;
      this.images = images;
    }

    public Media getMedia() {
      return media;
    }

    public void setMedia(Media media) {
      this.media = media;
    }

    public List<Image> getImages() {
      return images;
    }

    public void setImages(List<Image> images) {
      this.images = images;
    }

    @Override
    public boolean equals(Object object) {
      if (this == object) {
        return true;
      }
      if (!(object instanceof MediaContent)) {
        return false;
      }
      MediaContent that = (MediaContent) object;
      return Objects.equals(media, that.media) && Objects.equals(images, that.images);
    }

    @Override
    public int hashCode() {
      return Objects.hash(media, images);
    }
  }

  public static final class Media implements java.io.Serializable {
    private int bitrate;
    private long duration;
    private String format;
    private int height;
    private List<String> persons;
    private Player player;
    private long size;
    private String title;
    private String uri;
    private int width;
    private String copyright;

    public Media() {}

    public String getUri() {
      return uri;
    }

    public void setUri(String uri) {
      this.uri = uri;
    }

    public String getTitle() {
      return title;
    }

    public void setTitle(String title) {
      this.title = title;
    }

    public int getWidth() {
      return width;
    }

    public void setWidth(int width) {
      this.width = width;
    }

    public int getHeight() {
      return height;
    }

    public void setHeight(int height) {
      this.height = height;
    }

    public String getFormat() {
      return format;
    }

    public void setFormat(String format) {
      this.format = format;
    }

    public long getDuration() {
      return duration;
    }

    public void setDuration(long duration) {
      this.duration = duration;
    }

    public long getSize() {
      return size;
    }

    public void setSize(long size) {
      this.size = size;
    }

    public int getBitrate() {
      return bitrate;
    }

    public void setBitrate(int bitrate) {
      this.bitrate = bitrate;
    }

    public List<String> getPersons() {
      return persons;
    }

    public void setPersons(List<String> persons) {
      this.persons = persons;
    }

    public Player getPlayer() {
      return player;
    }

    public void setPlayer(Player player) {
      this.player = player;
    }

    public String getCopyright() {
      return copyright;
    }

    public void setCopyright(String copyright) {
      this.copyright = copyright;
    }

    @Override
    public boolean equals(Object object) {
      if (this == object) {
        return true;
      }
      if (!(object instanceof Media)) {
        return false;
      }
      Media media = (Media) object;
      return bitrate == media.bitrate
          && duration == media.duration
          && height == media.height
          && size == media.size
          && width == media.width
          && Objects.equals(format, media.format)
          && Objects.equals(persons, media.persons)
          && player == media.player
          && Objects.equals(title, media.title)
          && Objects.equals(uri, media.uri)
          && Objects.equals(copyright, media.copyright);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          bitrate, duration, format, height, persons, player, size, title, uri, width, copyright);
    }
  }

  public static final class Image implements java.io.Serializable {
    private int height;
    private Size size;
    private String title;
    private String uri;
    private int width;

    public Image() {}

    public String getUri() {
      return uri;
    }

    public void setUri(String uri) {
      this.uri = uri;
    }

    public String getTitle() {
      return title;
    }

    public void setTitle(String title) {
      this.title = title;
    }

    public int getWidth() {
      return width;
    }

    public void setWidth(int width) {
      this.width = width;
    }

    public int getHeight() {
      return height;
    }

    public void setHeight(int height) {
      this.height = height;
    }

    public Size getSize() {
      return size;
    }

    public void setSize(Size size) {
      this.size = size;
    }

    @Override
    public boolean equals(Object object) {
      if (this == object) {
        return true;
      }
      if (!(object instanceof Image)) {
        return false;
      }
      Image image = (Image) object;
      return height == image.height
          && width == image.width
          && size == image.size
          && Objects.equals(title, image.title)
          && Objects.equals(uri, image.uri);
    }

    @Override
    public int hashCode() {
      return Objects.hash(height, size, title, uri, width);
    }
  }

  public enum Player {
    JAVA,
    FLASH
  }

  public enum Size {
    SMALL,
    LARGE
  }
}
