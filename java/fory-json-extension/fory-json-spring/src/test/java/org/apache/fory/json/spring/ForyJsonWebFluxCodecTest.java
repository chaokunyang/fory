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

package org.apache.fory.json.spring;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import io.netty.buffer.UnpooledByteBufAllocator;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.apache.fory.json.ForyJson;
import org.apache.fory.json.JsonObject;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.core.io.buffer.PooledDataBuffer;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.testng.annotations.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public class ForyJsonWebFluxCodecTest {
  private static final ForyJson JSON =
      ForyJson.builder().withCodegen(false).withConcurrencyLevel(2).build();
  private static final DataBufferFactory BUFFERS =
      new NettyDataBufferFactory(UnpooledByteBufAllocator.DEFAULT);
  private static final ResolvableType USER_TYPE = ResolvableType.forClass(User.class);

  @Test
  public void capabilitiesAndConfiguration() {
    ForyJsonEncoder encoder = new ForyJsonEncoder(JSON);
    ForyJsonDecoder decoder = new ForyJsonDecoder(JSON);
    MediaType vendorJson = MediaType.parseMediaType("application/vnd.example+json");
    assertTrue(encoder.canEncode(USER_TYPE, vendorJson));
    assertTrue(decoder.canDecode(USER_TYPE, MediaType.APPLICATION_NDJSON));
    assertFalse(encoder.canEncode(ResolvableType.forClass(String.class), vendorJson));
    assertFalse(encoder.canEncode(ResolvableType.forClass(StringBuilder.class), vendorJson));
    assertFalse(decoder.canDecode(ResolvableType.forClass(byte[].class), vendorJson));
    assertFalse(encoder.canEncode(ResolvableType.forClass(ByteArrayResource.class), vendorJson));
    assertFalse(
        decoder.canDecode(USER_TYPE, MediaType.parseMediaType("application/json;charset=UTF-16")));
    assertEquals(decoder.getMaxInMemorySize(), ForyJsonDecoder.DEFAULT_MAX_IN_MEMORY_SIZE);
    decoder.setMaxInMemorySize(1024);
    assertEquals(decoder.getMaxInMemorySize(), 1024);
    assertEquals(encoder.getStreamingMediaTypes(), List.of(MediaType.APPLICATION_NDJSON));
  }

  @Test
  public void encodesMonoAsOneValue() {
    ForyJsonEncoder encoder = new ForyJsonEncoder(JSON);
    Flux<DataBuffer> encoded =
        encoder.encode(
            Mono.just(new User(1, "one")),
            BUFFERS,
            USER_TYPE,
            MediaType.APPLICATION_JSON,
            Map.of());

    assertEquals(readAll(encoded), "{\"id\":1,\"name\":\"one\"}");
  }

  @Test
  public void encodesFluxAsJsonArray() {
    ForyJsonEncoder encoder = new ForyJsonEncoder(JSON);
    Flux<DataBuffer> encoded =
        encoder.encode(
            Flux.just(new User(1, "one"), new User(2, "two")),
            BUFFERS,
            USER_TYPE,
            MediaType.APPLICATION_JSON,
            Map.of());
    assertEquals(readAll(encoded), "[{\"id\":1,\"name\":\"one\"},{\"id\":2,\"name\":\"two\"}]");

    Flux<DataBuffer> empty =
        encoder.encode(Flux.empty(), BUFFERS, USER_TYPE, MediaType.APPLICATION_JSON, Map.of());
    assertEquals(readAll(empty), "[]");

    RuntimeException failure = new RuntimeException("publisher failed");
    StepVerifier.create(
            encoder.encode(
                Flux.error(failure), BUFFERS, USER_TYPE, MediaType.APPLICATION_JSON, Map.of()))
        .expectErrorMatches(error -> error == failure)
        .verify();
  }

  @Test
  public void encodesNdjsonRecords() {
    ForyJsonEncoder encoder = new ForyJsonEncoder(JSON);
    Flux<DataBuffer> encoded =
        encoder.encode(
            Flux.just(new User(1, "one"), new User(2, "two")),
            BUFFERS,
            USER_TYPE,
            MediaType.APPLICATION_NDJSON,
            Map.of());
    assertEquals(readAll(encoded), "{\"id\":1,\"name\":\"one\"}\n{\"id\":2,\"name\":\"two\"}\n");
    assertEquals(
        readAll(
            encoder.encode(
                Mono.just(new User(1, "one")),
                BUFFERS,
                USER_TYPE,
                MediaType.APPLICATION_NDJSON,
                Map.of())),
        "{\"id\":1,\"name\":\"one\"}");
  }

  @Test
  public void decodesMonoAcrossBuffers() {
    ForyJsonDecoder decoder = new ForyJsonDecoder(JSON);
    Flux<DataBuffer> input = buffers("{\"id\":7,", "\"name\":\"Ada\"}");

    StepVerifier.create(
            decoder.decodeToMono(input, USER_TYPE, MediaType.APPLICATION_JSON, Map.of()))
        .assertNext(
            value -> {
              User user = (User) value;
              assertEquals(user.id, 7);
              assertEquals(user.name, "Ada");
            })
        .verifyComplete();

    StepVerifier.create(
            decoder.decodeToMono(buffers("null"), USER_TYPE, MediaType.APPLICATION_JSON, Map.of()))
        .verifyComplete();
  }

  @Test
  public void decodesFluxJsonArrayAcrossBuffers() {
    ForyJsonDecoder decoder = new ForyJsonDecoder(JSON);
    Flux<DataBuffer> input =
        buffers("[{\"id\":1,\"name\":\"one\"},", "{\"id\":2,\"name\":\"two\"}]");

    StepVerifier.create(decoder.decode(input, USER_TYPE, MediaType.APPLICATION_JSON, Map.of()))
        .assertNext(value -> assertEquals(((User) value).id, 1))
        .assertNext(value -> assertEquals(((User) value).id, 2))
        .verifyComplete();

    StepVerifier.create(
            decoder.decode(
                buffers("[null,{\"id\":3,\"name\":\"three\"},null]"),
                USER_TYPE,
                MediaType.APPLICATION_JSON,
                Map.of()))
        .assertNext(value -> assertEquals(((User) value).id, 3))
        .verifyComplete();
  }

  @Test
  public void decodesNdjsonAcrossBuffers() {
    ForyJsonDecoder decoder = new ForyJsonDecoder(JSON);
    Flux<DataBuffer> input =
        buffers("{\"id\":1,\"name\":\"one\"}\r", "\nnull\n{\"id\":2,\"name\":\"two\"}\n");

    StepVerifier.create(decoder.decode(input, USER_TYPE, MediaType.APPLICATION_NDJSON, Map.of()))
        .assertNext(value -> assertEquals(((User) value).id, 1))
        .assertNext(value -> assertEquals(((User) value).id, 2))
        .verifyComplete();
  }

  @Test
  public void releasesSingleBufferOverLimit() {
    ForyJsonDecoder decoder = new ForyJsonDecoder(JSON, 4);
    PooledDataBuffer buffer = pooled("{\"id\":1}");

    StepVerifier.create(
            decoder.decodeToMono(
                Mono.just((DataBuffer) buffer), USER_TYPE, MediaType.APPLICATION_JSON, Map.of()))
        .expectError(DataBufferLimitException.class)
        .verify();
    assertFalse(buffer.isAllocated());

    ForyJsonDecoder ndjsonDecoder = new ForyJsonDecoder(JSON, 16);
    PooledDataBuffer ndjson = pooled("{\"id\":1,\"name\":\"你好\"}\n");
    StepVerifier.create(
            ndjsonDecoder.decode(
                Mono.just((DataBuffer) ndjson), USER_TYPE, MediaType.APPLICATION_NDJSON, Map.of()))
        .expectError(DataBufferLimitException.class)
        .verify();
    assertFalse(ndjson.isAllocated());
  }

  @Test
  public void releasesBuffersOnParseError() {
    ForyJsonDecoder decoder = new ForyJsonDecoder(JSON);
    PooledDataBuffer buffer = pooled("{");

    StepVerifier.create(
            decoder.decodeToMono(
                Mono.just((DataBuffer) buffer), USER_TYPE, MediaType.APPLICATION_JSON, Map.of()))
        .expectError(DecodingException.class)
        .verify();
    assertFalse(buffer.isAllocated());
  }

  @Test
  public void releasesJoinedBuffersOnCancelAndError() {
    ForyJsonDecoder decoder = new ForyJsonDecoder(JSON);
    PooledDataBuffer cancelled = pooled("{\"id\":1");
    Flux<DataBuffer> neverEnding = Flux.concat(Mono.just((DataBuffer) cancelled), Flux.never());
    StepVerifier.create(
            decoder.decodeToMono(neverEnding, USER_TYPE, MediaType.APPLICATION_JSON, Map.of()))
        .thenCancel()
        .verify();
    assertFalse(cancelled.isAllocated());

    PooledDataBuffer failed = pooled("{\"id\":1");
    Flux<DataBuffer> erroring =
        Flux.concat(Mono.just((DataBuffer) failed), Flux.error(new IOExceptionMarker()));
    StepVerifier.create(
            decoder.decodeToMono(erroring, USER_TYPE, MediaType.APPLICATION_JSON, Map.of()))
        .expectError(IOExceptionMarker.class)
        .verify();
    assertFalse(failed.isAllocated());
  }

  @Test
  public void supportsProblemDetailInMonoAndFlux() {
    ForyJsonEncoder encoder = new ForyJsonEncoder(JSON);
    ForyJsonDecoder decoder = new ForyJsonDecoder(JSON);
    ResolvableType type = ResolvableType.forClass(ProblemDetail.class);
    ProblemDetail detail =
        ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(400), "Invalid request");
    detail.setInstance(URI.create("/requests/1"));
    detail.setProperty("field", "name");

    Flux<DataBuffer> monoEncoded =
        encoder.encode(
            Mono.just(detail), BUFFERS, type, MediaType.APPLICATION_PROBLEM_JSON, Map.of());
    String json = readAll(monoEncoded);
    JsonObject values = (JsonObject) JSON.fromJson(json, Object.class);
    assertEquals(((Number) values.get("status")).intValue(), 400);
    assertEquals(values.get("field"), "name");
    assertFalse(values.containsKey("properties"));

    StepVerifier.create(
            decoder.decodeToMono(buffers(json), type, MediaType.APPLICATION_PROBLEM_JSON, Map.of()))
        .assertNext(value -> assertEquals(((ProblemDetail) value).getDetail(), "Invalid request"))
        .verifyComplete();

    String array =
        readAll(
            encoder.encode(
                Flux.just(detail, detail),
                BUFFERS,
                type,
                MediaType.APPLICATION_PROBLEM_JSON,
                Map.of()));
    StepVerifier.create(
            decoder.decode(buffers(array), type, MediaType.APPLICATION_PROBLEM_JSON, Map.of()))
        .assertNext(value -> assertEquals(((ProblemDetail) value).getStatus(), 400))
        .assertNext(value -> assertEquals(((ProblemDetail) value).getStatus(), 400))
        .verifyComplete();
  }

  @Test
  public void decodesProblemDetailWithoutStatus() {
    ForyJsonDecoder decoder = new ForyJsonDecoder(JSON);
    ResolvableType type = ResolvableType.forClass(ProblemDetail.class);
    String json = "{\"detail\":\"failed\"}";

    StepVerifier.create(
            decoder.decodeToMono(buffers(json), type, MediaType.APPLICATION_PROBLEM_JSON, Map.of()))
        .assertNext(value -> assertEquals(((ProblemDetail) value).getStatus(), 0))
        .verifyComplete();
    StepVerifier.create(
            decoder.decode(
                buffers("[" + json + "]"), type, MediaType.APPLICATION_PROBLEM_JSON, Map.of()))
        .assertNext(value -> assertEquals(((ProblemDetail) value).getStatus(), 0))
        .verifyComplete();
    StepVerifier.create(
            decoder.decode(buffers(json + "\n"), type, MediaType.APPLICATION_NDJSON, Map.of()))
        .assertNext(value -> assertEquals(((ProblemDetail) value).getStatus(), 0))
        .verifyComplete();
  }

  @Test
  public void wrapsProblemDetailArrayErrors() {
    ForyJsonDecoder decoder = new ForyJsonDecoder(JSON);
    ResolvableType type = ResolvableType.forClass(ProblemDetail.class);
    StepVerifier.create(
            decoder.decode(
                buffers("[{\"status\":\"bad\"}]"),
                type,
                MediaType.APPLICATION_PROBLEM_JSON,
                Map.of()))
        .expectError(DecodingException.class)
        .verify();
  }

  private static Flux<DataBuffer> buffers(String... chunks) {
    return Flux.fromArray(chunks)
        .map(chunk -> BUFFERS.wrap(chunk.getBytes(StandardCharsets.UTF_8)));
  }

  private static PooledDataBuffer pooled(String value) {
    return (PooledDataBuffer) BUFFERS.wrap(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String readAll(Flux<DataBuffer> buffers) {
    return buffers
        .map(ForyJsonWebFluxCodecTest::readAndRelease)
        .collectList()
        .map(parts -> String.join("", parts))
        .block();
  }

  private static String readAndRelease(DataBuffer buffer) {
    try {
      byte[] bytes = new byte[buffer.readableByteCount()];
      buffer.read(bytes);
      return new String(bytes, StandardCharsets.UTF_8);
    } finally {
      DataBufferUtils.release(buffer);
    }
  }

  public static final class User {
    public int id;
    public String name;

    public User() {}

    User(int id, String name) {
      this.id = id;
      this.name = name;
    }
  }

  private static final class IOExceptionMarker extends RuntimeException {}
}
