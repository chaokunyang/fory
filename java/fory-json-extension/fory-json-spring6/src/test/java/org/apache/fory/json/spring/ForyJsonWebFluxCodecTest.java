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
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.fory.json.ForyJson;
import org.apache.fory.json.JsonObject;
import org.apache.fory.reflect.TypeRef;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.testng.annotations.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public class ForyJsonWebFluxCodecTest {
  private static final DataBufferFactory BUFFER_FACTORY = DefaultDataBufferFactory.sharedInstance;

  private final ForyJson foryJson = ForyJson.builder().build();
  private final ResolvableType userType = ResolvableType.forClass(User.class);

  @Test
  public void testMonoAndFluxJsonEncoding() {
    ForyJsonEncoder encoder = new ForyJsonEncoder(foryJson);
    User first = new User("Alice", 31);
    User second = new User("Bob", 27);

    String monoJson = encode(encoder, Mono.just(first), userType, MediaType.APPLICATION_JSON);
    assertEquals(foryJson.fromJson(monoJson, User.class), first);
    assertEquals(encode(encoder, Flux.empty(), userType, MediaType.APPLICATION_JSON), "[]");
    assertEquals(
        foryJson.fromJson(
            encode(encoder, Flux.just(first), userType, MediaType.APPLICATION_JSON),
            new TypeRef<List<User>>() {}),
        Collections.singletonList(first));
    assertEquals(
        foryJson.fromJson(
            encode(encoder, Flux.just(first, second), userType, MediaType.APPLICATION_JSON),
            new TypeRef<List<User>>() {}),
        Arrays.asList(first, second));
  }

  @Test
  public void testNdjsonAcrossBuffers() {
    ForyJsonEncoder encoder = new ForyJsonEncoder(foryJson);
    ForyJsonDecoder decoder = new ForyJsonDecoder(foryJson);
    User first = new User("Alice", 31);
    User second = new User("Bob", 27);

    String ndjson =
        encode(encoder, Flux.just(first, second), userType, MediaType.APPLICATION_NDJSON);
    assertTrue(ndjson.endsWith("\n"));
    assertFalse(
        encode(encoder, Mono.just(first), userType, MediaType.APPLICATION_NDJSON).endsWith("\n"));
    String[] lines = ndjson.split("\n");
    assertEquals(foryJson.fromJson(lines[0], User.class), first);
    assertEquals(foryJson.fromJson(lines[1], User.class), second);

    String body = "  \r\n" + lines[0] + "\r\nnull\n" + lines[1] + "\n";
    int split = body.indexOf(lines[0]) + 5;
    Flux<DataBuffer> buffers =
        Flux.just(
            buffer(body.substring(0, split)),
            buffer(body.substring(split, body.length() - 2)),
            buffer(body.substring(body.length() - 2)));
    StepVerifier.create(
            decoder.decode(buffers, userType, MediaType.APPLICATION_NDJSON, Collections.emptyMap()))
        .expectNext(first, second)
        .verifyComplete();
  }

  @Test
  public void testArrayAndMonoDecodingWithGenerics() {
    ForyJsonDecoder decoder = new ForyJsonDecoder(foryJson);
    User first = new User("Alice", 31);
    User second = new User("Bob", 27);
    String array = foryJson.toJson(Arrays.asList(first, second), new TypeRef<List<User>>() {});
    int split = array.length() / 2;

    StepVerifier.create(
            decoder.decode(
                Flux.just(buffer(array.substring(0, split)), buffer(array.substring(split))),
                userType,
                MediaType.APPLICATION_JSON,
                Collections.emptyMap()))
        .expectNext(first, second)
        .verifyComplete();

    StepVerifier.create(
            decoder.decode(
                Mono.just(buffer("[null,{\"name\":\"Bob\",\"age\":27},null]")),
                userType,
                MediaType.APPLICATION_JSON,
                Collections.emptyMap()))
        .expectNext(second)
        .verifyComplete();

    ResolvableType listType = ResolvableType.forClassWithGenerics(List.class, User.class);
    StepVerifier.create(
            decoder.decodeToMono(
                Mono.just(buffer(array)),
                listType,
                MediaType.APPLICATION_JSON,
                Collections.emptyMap()))
        .expectNext(Arrays.asList(first, second))
        .verifyComplete();

    StepVerifier.create(
            decoder.decodeToMono(
                Mono.just(buffer("null")),
                userType,
                MediaType.APPLICATION_JSON,
                Collections.emptyMap()))
        .verifyComplete();
  }

  @Test
  public void testProblemDetailEncoding() {
    ForyJsonEncoder encoder = new ForyJsonEncoder(foryJson);
    ProblemDetail detail = ProblemDetail.forStatus(400);
    detail.setDetail("Invalid request");
    detail.setProperty("field", "name");

    String json =
        encode(
            encoder,
            Mono.just(detail),
            ResolvableType.forClass(ProblemDetail.class),
            MediaType.APPLICATION_PROBLEM_JSON);

    JsonObject values = (JsonObject) foryJson.fromJson(json, Object.class);
    assertEquals(((Number) values.get("status")).intValue(), 400);
    assertEquals(values.get("detail"), "Invalid request");
    assertEquals(values.get("field"), "name");
    assertFalse(values.containsKey("properties"));
  }

  @Test
  public void testDecodeLimits() {
    ForyJsonDecoder decoder = new ForyJsonDecoder(foryJson, 4);
    StepVerifier.create(
            decoder.decode(
                Mono.just(buffer("[0,1]")),
                ResolvableType.forClass(Integer.class),
                MediaType.APPLICATION_JSON,
                Collections.emptyMap()))
        .expectError(DataBufferLimitException.class)
        .verify();

    StepVerifier.create(
            decoder.decode(
                Flux.just(buffer("123"), buffer("45\n")),
                ResolvableType.forClass(Integer.class),
                MediaType.APPLICATION_NDJSON,
                Collections.emptyMap()))
        .expectError(DataBufferLimitException.class)
        .verify();
  }

  @Test
  public void testErrorAndCancelPaths() {
    ForyJsonEncoder encoder = new ForyJsonEncoder(foryJson);
    ForyJsonDecoder decoder = new ForyJsonDecoder(foryJson);
    User user = new User("Alice", 31);
    RuntimeException failure = new RuntimeException("publisher failed");
    StepVerifier.create(
            encoder.encode(
                Flux.error(failure),
                BUFFER_FACTORY,
                userType,
                MediaType.APPLICATION_JSON,
                Collections.emptyMap()))
        .expectErrorMatches(error -> error == failure)
        .verify();

    StepVerifier.create(
            encoder.encode(
                Flux.concat(Flux.just(user), Flux.error(failure)),
                BUFFER_FACTORY,
                userType,
                MediaType.APPLICATION_JSON,
                Collections.emptyMap()))
        .assertNext(ForyJsonWebFluxCodecTest::release)
        .expectErrorMatches(error -> error == failure)
        .verify();

    StepVerifier.create(
            encoder.encode(
                Flux.never(),
                BUFFER_FACTORY,
                userType,
                MediaType.APPLICATION_JSON,
                Collections.emptyMap()))
        .thenCancel()
        .verify();

    String line = foryJson.toJson(user) + "\n";
    StepVerifier.create(
            decoder.decode(
                Flux.concat(Flux.just(buffer(line)), Flux.never()),
                userType,
                MediaType.APPLICATION_NDJSON,
                Collections.emptyMap()))
        .expectNext(user)
        .thenCancel()
        .verify();

    StepVerifier.create(
            decoder.decode(
                Flux.concat(Flux.just(buffer("[")), Flux.error(failure)),
                userType,
                MediaType.APPLICATION_JSON,
                Collections.emptyMap()))
        .expectErrorMatches(error -> error == failure)
        .verify();
  }

  @Test
  public void testPublicConfiguration() {
    ForyJsonEncoder encoder = new ForyJsonEncoder(foryJson);
    ForyJsonDecoder decoder = new ForyJsonDecoder(foryJson);
    assertSame(encoder.getForyJson(), foryJson);
    assertSame(decoder.getForyJson(), foryJson);
    assertEquals(decoder.getMaxInMemorySize(), 256 * 1024);
    assertFalse(
        encoder.canEncode(
            ResolvableType.forClass(StringBuilder.class), MediaType.APPLICATION_JSON));
    assertFalse(
        decoder.canDecode(userType, MediaType.parseMediaType("application/json;charset=UTF-16")));
    decoder.setMaxInMemorySize(1024);
    assertEquals(decoder.getMaxInMemorySize(), 1024);
  }

  private static String encode(
      ForyJsonEncoder encoder,
      org.reactivestreams.Publisher<?> publisher,
      ResolvableType type,
      MediaType mediaType) {
    List<DataBuffer> buffers =
        encoder
            .encode(publisher, BUFFER_FACTORY, type, mediaType, Collections.emptyMap())
            .collectList()
            .block();
    StringBuilder output = new StringBuilder();
    for (DataBuffer buffer : buffers) {
      try {
        output.append(buffer.toString(StandardCharsets.UTF_8));
      } finally {
        DataBufferUtils.release(buffer);
      }
    }
    return output.toString();
  }

  private static DataBuffer buffer(String value) {
    return BUFFER_FACTORY.wrap(value.getBytes(StandardCharsets.UTF_8));
  }

  private static void release(DataBuffer buffer) {
    DataBufferUtils.release(buffer);
  }

  public record User(String name, int age) {}
}
