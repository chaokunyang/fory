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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.apache.fory.json.annotation.JsonProperty;
import org.apache.fory.json.spring.ForyJsonHttpMessageConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    classes = ForyJsonMvcIntegrationTest.TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class ForyJsonMvcIntegrationTest {
  @Autowired private WebApplicationContext applicationContext;

  @Autowired private RequestMappingHandlerAdapter handlerAdapter;

  @Autowired private JsonMapper jsonMapper;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).build();
  }

  @Test
  void usesForyForRequestAndResponse() throws Exception {
    assertThat(jsonMapper).isNotNull();

    mockMvc
        .perform(get("/mvc/payload").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"fory_name\":\"response\"}", true));

    mockMvc
        .perform(
            post("/mvc/payload")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content("{\"fory_name\":\"request\"}"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"fory_name\":\"request\"}", true));
  }

  @Test
  void installsOneForyConverter() {
    assertThat(handlerAdapter.getMessageConverters())
        .filteredOn(ForyJsonHttpMessageConverter.class::isInstance)
        .hasSize(1);
  }

  @Test
  void writesProblemDetail() throws Exception {
    mockMvc
        .perform(get("/mvc/problem").accept(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("bad request")));
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import(MvcController.class)
  static class TestApplication {}

  @RestController
  static class MvcController {
    @GetMapping(value = "/mvc/payload", produces = MediaType.APPLICATION_JSON_VALUE)
    Payload payload() {
      return new Payload("response");
    }

    @PostMapping(
        value = "/mvc/payload",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    Payload echo(@RequestBody Payload payload) {
      return payload;
    }

    @GetMapping(value = "/mvc/problem", produces = MediaType.APPLICATION_PROBLEM_JSON_VALUE)
    ProblemDetail problem() {
      return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "bad request");
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
