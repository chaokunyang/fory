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

package org.apache.fory.json.spring.boot3;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.apache.fory.json.annotation.JsonProperty;
import org.apache.fory.json.spring.ForyJsonDecoder;
import org.apache.fory.json.spring.ForyJsonEncoder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SpringBootTest(
    classes = ForyJsonWebFluxIntegrationTest.Application.class,
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.main.web-application-type=reactive",
      "spring.codec.max-in-memory-size=4KB"
    })
@AutoConfigureWebTestClient
class ForyJsonWebFluxIntegrationTest {
  @Autowired private WebTestClient webTestClient;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private ForyJsonEncoder encoder;

  @Autowired private ForyJsonDecoder decoder;

  @Test
  void usesForyWithJacksonPresent() {
    assertThat(objectMapper).isNotNull();
    assertThat(encoder).isNotNull();
    assertThat(decoder).isNotNull();
    assertThat(decoder.getMaxInMemorySize()).isEqualTo(4096);

    webTestClient
        .post()
        .uri("/echo")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .bodyValue("{\"fory_name\":\"boot3-webflux\"}".getBytes(StandardCharsets.UTF_8))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.fory_name")
        .isEqualTo("boot3-webflux")
        .jsonPath("$.jackson_name")
        .doesNotExist();
  }

  @Test
  void writesJsonArrayAndNdjson() {
    webTestClient
        .get()
        .uri("/array")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo("[{\"fory_name\":\"one\"},{\"fory_name\":\"two\"}]");

    webTestClient
        .get()
        .uri("/ndjson")
        .accept(MediaType.APPLICATION_NDJSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo("{\"fory_name\":\"one\"}\n{\"fory_name\":\"two\"}\n");
  }

  @Test
  void readsJsonArrayAndNdjson() {
    webTestClient
        .post()
        .uri("/array")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .bodyValue(
            "[{\"fory_name\":\"one\"},{\"fory_name\":\"two\"}]".getBytes(StandardCharsets.UTF_8))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.fory_name")
        .isEqualTo("two")
        .jsonPath("$.jackson_name")
        .doesNotExist();

    webTestClient
        .post()
        .uri("/ndjson")
        .contentType(MediaType.APPLICATION_NDJSON)
        .accept(MediaType.APPLICATION_JSON)
        .bodyValue(
            "{\"fory_name\":\"one\"}\n{\"fory_name\":\"two\"}".getBytes(StandardCharsets.UTF_8))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.fory_name")
        .isEqualTo("two")
        .jsonPath("$.jackson_name")
        .doesNotExist();
  }

  @Test
  void rejectsMalformedProblemDetailArray() {
    webTestClient
        .post()
        .uri("/problems")
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyValue("[{\"status\":\"bad\"}]".getBytes(StandardCharsets.UTF_8))
        .exchange()
        .expectStatus()
        .isBadRequest();
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import(EchoController.class)
  static class Application {}

  @RestController
  @RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  static class EchoController {
    @PostMapping(path = "/echo", consumes = MediaType.APPLICATION_JSON_VALUE)
    EchoValue echo(@RequestBody EchoValue value) {
      return value;
    }

    @GetMapping(path = "/array", produces = MediaType.APPLICATION_JSON_VALUE)
    Flux<EchoValue> array() {
      return Flux.just(new EchoValue("one"), new EchoValue("two"));
    }

    @GetMapping(path = "/ndjson", produces = MediaType.APPLICATION_NDJSON_VALUE)
    Flux<EchoValue> ndjson() {
      return Flux.just(new EchoValue("one"), new EchoValue("two"));
    }

    @PostMapping(
        path = "/array",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    Mono<EchoValue> readArray(@RequestBody Flux<EchoValue> values) {
      return values.last();
    }

    @PostMapping(
        path = "/ndjson",
        consumes = MediaType.APPLICATION_NDJSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    Mono<EchoValue> readNdjson(@RequestBody Flux<EchoValue> values) {
      return values.last();
    }

    @PostMapping(
        path = "/problems",
        consumes = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    Mono<Long> problems(@RequestBody Flux<ProblemDetail> problems) {
      return problems.count();
    }
  }

  public static class EchoValue {
    @JsonProperty("fory_name")
    @com.fasterxml.jackson.annotation.JsonProperty("jackson_name")
    public String value;

    public EchoValue() {}

    EchoValue(String value) {
      this.value = value;
    }
  }
}
