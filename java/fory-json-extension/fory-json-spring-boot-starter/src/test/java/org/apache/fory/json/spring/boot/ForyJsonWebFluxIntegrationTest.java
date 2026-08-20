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

package org.apache.fory.json.spring.boot;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.apache.fory.json.annotation.JsonProperty;
import org.apache.fory.json.spring.ForyJsonDecoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    classes = ForyJsonWebFluxIntegrationTest.TestApplication.class,
    properties = {
      "spring.main.web-application-type=reactive",
      "spring.http.codecs.max-in-memory-size=4KB"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ForyJsonWebFluxIntegrationTest {
  @LocalServerPort private int port;

  @Autowired private JsonMapper jsonMapper;

  @Autowired private ForyJsonDecoder decoder;

  private WebTestClient webTestClient;

  @BeforeEach
  void setUp() {
    webTestClient = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
  }

  @Test
  void usesForyForMonoRequestAndResponse() {
    assertThat(jsonMapper).isNotNull();
    assertThat(decoder.getMaxInMemorySize()).isEqualTo(4096);

    webTestClient
        .post()
        .uri("/webflux/mono")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .bodyValue("{\"fory_name\":\"request\"}".getBytes(StandardCharsets.UTF_8))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo("{\"fory_name\":\"request\"}");
  }

  @Test
  void writesJsonArrayAndNdjson() {
    webTestClient
        .get()
        .uri("/webflux/flux")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo("[{\"fory_name\":\"one\"},{\"fory_name\":\"two\"}]");

    webTestClient
        .get()
        .uri("/webflux/ndjson")
        .accept(MediaType.APPLICATION_NDJSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo("{\"fory_name\":\"one\"}\n{\"fory_name\":\"two\"}\n");
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import(WebFluxController.class)
  static class TestApplication {}

  @RestController
  static class WebFluxController {
    @PostMapping(
        value = "/webflux/mono",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    Mono<Payload> echo(@RequestBody Mono<Payload> payload) {
      return payload;
    }

    @GetMapping(value = "/webflux/flux", produces = MediaType.APPLICATION_JSON_VALUE)
    Flux<Payload> flux() {
      return Flux.just(new Payload("one"), new Payload("two"));
    }

    @GetMapping(value = "/webflux/ndjson", produces = MediaType.APPLICATION_NDJSON_VALUE)
    Flux<Payload> ndjson() {
      return Flux.just(new Payload("one"), new Payload("two"));
    }
  }

  public static final class Payload {
    @JsonProperty("fory_name")
    @com.fasterxml.jackson.annotation.JsonProperty("jackson_name")
    public String value;

    public Payload() {}

    public Payload(String value) {
      this.value = value;
    }
  }
}
