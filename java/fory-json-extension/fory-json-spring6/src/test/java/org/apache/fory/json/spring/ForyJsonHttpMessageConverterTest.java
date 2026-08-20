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
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.fory.json.ForyJson;
import org.apache.fory.json.JsonObject;
import org.apache.fory.reflect.TypeRef;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.http.MockHttpOutputMessage;
import org.testng.annotations.Test;

public class ForyJsonHttpMessageConverterTest {
  private final ForyJson foryJson = ForyJson.builder().build();

  @Test
  public void testMvcRoundTripAndGenericRoot() throws Exception {
    ForyJsonHttpMessageConverter converter = new ForyJsonHttpMessageConverter(foryJson);
    ResolvableType userType = ResolvableType.forClass(User.class);
    User user = new User("Alice", 31);
    MockHttpOutputMessage output = new MockHttpOutputMessage();

    converter.write(user, userType, MediaType.APPLICATION_JSON, output, Collections.emptyMap());

    assertEquals(output.getHeaders().getContentType().getCharset(), StandardCharsets.UTF_8);
    assertEquals(output.getHeaders().getContentLength(), output.getBodyAsBytes().length);
    assertEquals(
        converter.read(
            userType, new MockHttpInputMessage(output.getBodyAsBytes()), Collections.emptyMap()),
        user);

    ResolvableType listType = ResolvableType.forClassWithGenerics(List.class, User.class);
    List<User> users = Arrays.asList(user, new User("Bob", 27));
    byte[] bytes = foryJson.toJsonBytes(users, new TypeRef<List<User>>() {});
    assertEquals(
        converter.read(listType, new MockHttpInputMessage(bytes), Collections.emptyMap()), users);
  }

  @Test
  public void testMediaTypesAndSpringOwnedValues() {
    ForyJsonHttpMessageConverter converter = new ForyJsonHttpMessageConverter(foryJson);
    MediaType vendorJson = MediaType.valueOf("application/vnd.example+json");
    assertTrue(converter.canRead(ResolvableType.forClass(User.class), vendorJson));
    assertTrue(
        converter.canWrite(
            ResolvableType.forClass(User.class), User.class, MediaType.valueOf("application/*")));
    assertTrue(converter.canWrite(ResolvableType.forClass(User.class), User.class, vendorJson));
    assertFalse(converter.canRead(ResolvableType.forClass(User.class), MediaType.TEXT_PLAIN));
    assertFalse(
        converter.canRead(ResolvableType.forClass(String.class), MediaType.APPLICATION_JSON));
    assertFalse(
        converter.canRead(
            ResolvableType.forClass(StringBuilder.class), MediaType.APPLICATION_JSON));
    assertFalse(
        converter.canRead(ResolvableType.forClass(byte[].class), MediaType.APPLICATION_JSON));
    assertFalse(
        converter.canRead(
            ResolvableType.forClass(ByteArrayResource.class), MediaType.APPLICATION_JSON));
    assertFalse(
        converter.canRead(
            ResolvableType.forClass(ResourceRegion.class), MediaType.APPLICATION_JSON));
    assertFalse(
        converter.canRead(
            ResolvableType.forClass(User.class),
            MediaType.parseMediaType("application/json;charset=UTF-16")));
  }

  @Test
  public void testInputLimitAndMalformedJson() throws Exception {
    ForyJsonHttpMessageConverter converter = new ForyJsonHttpMessageConverter(foryJson, 4);
    assertNull(
        converter.read(
            ResolvableType.forClass(Object.class),
            new MockHttpInputMessage("null".getBytes(StandardCharsets.UTF_8)),
            Collections.emptyMap()));
    assertThrows(
        HttpMessageNotReadableException.class,
        () ->
            converter.read(
                ResolvableType.forClass(Object.class),
                new MockHttpInputMessage("null ".getBytes(StandardCharsets.UTF_8)),
                Collections.emptyMap()));
    assertThrows(
        HttpMessageNotReadableException.class,
        () ->
            new ForyJsonHttpMessageConverter(foryJson)
                .read(
                    ResolvableType.forClass(User.class),
                    new MockHttpInputMessage("[".getBytes(StandardCharsets.UTF_8)),
                    Collections.emptyMap()));
    assertThrows(
        IllegalArgumentException.class, () -> new ForyJsonHttpMessageConverter(foryJson, 0));
  }

  @Test
  public void testProblemDetailContract() throws Exception {
    ForyJsonHttpMessageConverter converter = new ForyJsonHttpMessageConverter(foryJson);
    ProblemDetail detail =
        ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(422), "Invalid value");
    detail.setInstance(URI.create("/orders/7"));
    detail.setProperty("field", "quantity");
    MockHttpOutputMessage output = new MockHttpOutputMessage();

    converter.write(
        detail,
        ResolvableType.forClass(ProblemDetail.class),
        MediaType.APPLICATION_PROBLEM_JSON,
        output,
        Collections.emptyMap());

    assertEquals(
        output.getHeaders().getContentType(),
        new MediaType(MediaType.APPLICATION_PROBLEM_JSON, StandardCharsets.UTF_8));
    JsonObject values = (JsonObject) foryJson.fromJson(output.getBodyAsBytes(), Object.class);
    assertEquals(((Number) values.get("status")).intValue(), 422);
    assertEquals(values.get("detail"), "Invalid value");
    assertEquals(values.get("instance"), "/orders/7");
    assertEquals(values.get("field"), "quantity");
    assertFalse(values.containsKey("properties"));

    ProblemDetail decoded =
        (ProblemDetail)
            converter.read(
                ResolvableType.forClass(ProblemDetail.class),
                new MockHttpInputMessage(output.getBodyAsBytes()),
                Collections.emptyMap());
    assertEquals(decoded.getStatus(), detail.getStatus());
    assertEquals(decoded.getDetail(), detail.getDetail());
    assertEquals(decoded.getInstance(), detail.getInstance());
    assertEquals(decoded.getProperties(), detail.getProperties());
  }

  @Test
  public void testPublicConfiguration() {
    ForyJsonHttpMessageConverter converter = new ForyJsonHttpMessageConverter(foryJson, 1024);
    assertSame(converter.getForyJson(), foryJson);
    assertEquals(converter.getMaxInputBytes(), 1024);
    assertEquals(ForyJsonHttpMessageConverter.DEFAULT_MAX_INPUT_BYTES, 64 * 1024 * 1024);
  }

  public record User(String name, int age) {}
}
