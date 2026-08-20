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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.apache.fory.json.annotation.JsonProperty;
import org.apache.fory.json.spring.ForyJsonHttpMessageConverter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

@SpringBootTest(
    classes = ForyJsonMvcIntegrationTest.Application.class,
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = "spring.main.web-application-type=servlet")
@AutoConfigureMockMvc
class ForyJsonMvcIntegrationTest {
  @Autowired private MockMvc mockMvc;

  @Autowired private RequestMappingHandlerAdapter handlerAdapter;

  @Autowired private ObjectMapper objectMapper;

  @Test
  void usesForyWithJacksonPresent() throws Exception {
    assertThat(objectMapper).isNotNull();
    List<HttpMessageConverter<?>> converters = handlerAdapter.getMessageConverters();
    int foryIndex = indexOf(converters, ForyJsonHttpMessageConverter.class);
    int jacksonIndex = indexOf(converters, MappingJackson2HttpMessageConverter.class);
    assertThat(foryIndex).isGreaterThanOrEqualTo(0).isLessThan(jacksonIndex);

    mockMvc
        .perform(
            post("/echo")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .content("{\"fory_name\":\"boot3-mvc\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fory_name").value("boot3-mvc"))
        .andExpect(jsonPath("$.jackson_name").doesNotExist());
  }

  private static int indexOf(List<HttpMessageConverter<?>> converters, Class<?> converterType) {
    for (int i = 0; i < converters.size(); i++) {
      if (converterType.isInstance(converters.get(i))) {
        return i;
      }
    }
    return -1;
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
  }

  public static class EchoValue {
    @JsonProperty("fory_name")
    @com.fasterxml.jackson.annotation.JsonProperty("jackson_name")
    public String value;

    public EchoValue() {}
  }
}
