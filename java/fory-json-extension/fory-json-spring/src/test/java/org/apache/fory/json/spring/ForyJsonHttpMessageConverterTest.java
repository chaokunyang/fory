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
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.fory.json.ForyJson;
import org.apache.fory.json.ForyJsonException;
import org.apache.fory.json.JsonObject;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.testng.annotations.Test;

public class ForyJsonHttpMessageConverterTest {
  private static final ForyJson JSON =
      ForyJson.builder().withCodegen(false).withConcurrencyLevel(2).build();

  @Test
  public void constructorsAndMediaTypes() {
    ForyJsonHttpMessageConverter converter = new ForyJsonHttpMessageConverter(JSON);
    assertSame(converter.getForyJson(), JSON);
    assertEquals(
        converter.getMaxInputBytes(), ForyJsonHttpMessageConverter.DEFAULT_MAX_INPUT_BYTES);
    assertThrows(IllegalArgumentException.class, () -> new ForyJsonHttpMessageConverter(JSON, 0));
    assertThrows(NullPointerException.class, () -> new ForyJsonHttpMessageConverter(null));

    MediaType vendorJson = MediaType.parseMediaType("application/vnd.example+json");
    MediaType utf16 = MediaType.parseMediaType("application/json;charset=UTF-16");
    assertTrue(converter.canRead(User.class, MediaType.APPLICATION_JSON));
    assertTrue(converter.canWrite(User.class, vendorJson));
    assertFalse(converter.canRead(User.class, MediaType.APPLICATION_NDJSON));
    assertFalse(converter.canWrite(User.class, utf16));
    assertFalse(converter.canRead(User.class, MediaType.TEXT_PLAIN));
  }

  @Test
  public void excludesFrameworkOwnedBodies() {
    ForyJsonHttpMessageConverter converter = new ForyJsonHttpMessageConverter(JSON);
    assertFalse(converter.canWrite(String.class, MediaType.APPLICATION_JSON));
    assertFalse(converter.canWrite(StringBuilder.class, MediaType.APPLICATION_JSON));
    assertFalse(converter.canRead(byte[].class, MediaType.APPLICATION_JSON));
    assertFalse(converter.canWrite(ByteArrayResource.class, MediaType.APPLICATION_JSON));
    assertFalse(converter.canRead(ResourceRegion.class, MediaType.APPLICATION_JSON));
  }

  @Test
  public void preservesGenericType() throws Exception {
    ForyJsonHttpMessageConverter converter = new ForyJsonHttpMessageConverter(JSON);
    Type usersType = Types.class.getDeclaredField("users").getGenericType();
    TestInputMessage input = new TestInputMessage("[{\"id\":7,\"name\":\"Ada\"}]");

    Object decoded = converter.read(usersType, null, input);
    assertTrue(decoded instanceof List<?>);
    User user = (User) ((List<?>) decoded).get(0);
    assertEquals(user.id, 7);
    assertEquals(user.name, "Ada");

    TestOutputMessage output = new TestOutputMessage();
    converter.write(decoded, usersType, MediaType.APPLICATION_JSON, output);
    assertEquals(output.bodyText(), "[{\"id\":7,\"name\":\"Ada\"}]");
    assertEquals(output.getHeaders().getContentType().getCharset(), StandardCharsets.UTF_8);
  }

  @Test
  public void readsActualBytesWithinLimit() throws Exception {
    byte[] body = "{\"id\":1}".getBytes(StandardCharsets.UTF_8);
    ForyJsonHttpMessageConverter converter = new ForyJsonHttpMessageConverter(JSON, body.length);
    TestInputMessage input = new TestInputMessage(body);
    input.getHeaders().setContentLength(body.length + 1000L);

    User user = (User) converter.read(User.class, input);
    assertEquals(user.id, 1);
  }

  @Test
  public void rejectsActualBytesBeyondLimit() {
    byte[] body = "{\"id\":12}".getBytes(StandardCharsets.UTF_8);
    ForyJsonHttpMessageConverter converter =
        new ForyJsonHttpMessageConverter(JSON, body.length - 1);
    TestInputMessage input = new TestInputMessage(body);
    input.getHeaders().setContentLength(1);

    HttpMessageNotReadableException error =
        expectThrows(
            HttpMessageNotReadableException.class, () -> converter.read(User.class, input));
    assertSame(error.getHttpInputMessage(), input);
    assertTrue(error.getMessage().contains("maxInputBytes"));
  }

  @Test
  public void mapsForyFailuresAndPreservesIoFailures() {
    ForyJsonHttpMessageConverter converter = new ForyJsonHttpMessageConverter(JSON);
    TestInputMessage malformed = new TestInputMessage("{");
    HttpMessageNotReadableException readError =
        expectThrows(
            HttpMessageNotReadableException.class, () -> converter.read(User.class, malformed));
    assertTrue(readError.getCause() instanceof ForyJsonException);

    TestOutputMessage output = new TestOutputMessage();
    HttpMessageNotWritableException writeError =
        expectThrows(
            HttpMessageNotWritableException.class,
            () -> converter.write(String.class, MediaType.APPLICATION_JSON, output));
    assertTrue(writeError.getCause() instanceof ForyJsonException);

    HttpInputMessage failingInput =
        new TestInputMessage(new FailingInputStream(), new HttpHeaders());
    assertThrows(IOException.class, () -> converter.read(User.class, failingInput));

    HttpOutputMessage failingOutput = new TestOutputMessage(new FailingOutputStream());
    assertThrows(
        IOException.class,
        () -> converter.write(new User(1, "x"), MediaType.APPLICATION_JSON, failingOutput));
  }

  @Test
  public void supportsProblemDetailContract() throws Exception {
    ForyJsonHttpMessageConverter converter = new ForyJsonHttpMessageConverter(JSON);
    ProblemDetail detail =
        ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(422), "Invalid value");
    detail.setInstance(URI.create("/orders/7"));
    detail.setProperty("field", "quantity");
    TestOutputMessage output = new TestOutputMessage();

    converter.write(detail, MediaType.APPLICATION_PROBLEM_JSON, output);

    assertEquals(
        output.getHeaders().getContentLength(),
        output.bodyText().getBytes(StandardCharsets.UTF_8).length);
    JsonObject values = (JsonObject) JSON.fromJson(output.bodyText(), Object.class);
    assertEquals(((Number) values.get("status")).intValue(), 422);
    assertEquals(values.get("detail"), "Invalid value");
    assertEquals(values.get("instance"), "/orders/7");
    assertEquals(values.get("field"), "quantity");
    assertFalse(values.containsKey("properties"));

    ProblemDetail decoded =
        (ProblemDetail)
            converter.read(ProblemDetail.class, new TestInputMessage(output.bodyText()));
    assertEquals(decoded.getStatus(), 422);
    assertEquals(decoded.getDetail(), "Invalid value");
    assertEquals(decoded.getInstance(), URI.create("/orders/7"));
    assertEquals(decoded.getProperties().get("field"), "quantity");

    ProblemDetail withoutStatus =
        (ProblemDetail)
            converter.read(ProblemDetail.class, new TestInputMessage("{\"detail\":\"failed\"}"));
    assertEquals(withoutStatus.getStatus(), 0);
    assertEquals(withoutStatus.getDetail(), "failed");
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

  private static final class Types {
    private List<User> users;
  }

  private static class TestInputMessage implements HttpInputMessage {
    private final InputStream body;
    private final HttpHeaders headers;

    private TestInputMessage(String body) {
      this(body.getBytes(StandardCharsets.UTF_8));
    }

    private TestInputMessage(byte[] body) {
      this(new ByteArrayInputStream(body), new HttpHeaders());
    }

    private TestInputMessage(InputStream body, HttpHeaders headers) {
      this.body = body;
      this.headers = headers;
    }

    @Override
    public InputStream getBody() {
      return body;
    }

    @Override
    public HttpHeaders getHeaders() {
      return headers;
    }
  }

  private static class TestOutputMessage implements HttpOutputMessage {
    private final HttpHeaders headers = new HttpHeaders();
    private final OutputStream body;

    private TestOutputMessage() {
      this(new ByteArrayOutputStream());
    }

    private TestOutputMessage(OutputStream body) {
      this.body = body;
    }

    @Override
    public OutputStream getBody() {
      return body;
    }

    @Override
    public HttpHeaders getHeaders() {
      return headers;
    }

    private String bodyText() {
      return ((ByteArrayOutputStream) body).toString(StandardCharsets.UTF_8);
    }
  }

  private static final class FailingInputStream extends InputStream {
    @Override
    public int read() throws IOException {
      throw new IOException("input failure");
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
      throw new IOException("input failure");
    }
  }

  private static final class FailingOutputStream extends OutputStream {
    @Override
    public void write(int value) throws IOException {
      throw new IOException("output failure");
    }

    @Override
    public void write(byte[] bytes, int offset, int length) throws IOException {
      throw new IOException("output failure");
    }
  }
}
