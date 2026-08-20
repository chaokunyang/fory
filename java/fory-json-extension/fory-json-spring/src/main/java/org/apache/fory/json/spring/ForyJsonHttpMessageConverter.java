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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.apache.fory.json.ForyJson;
import org.springframework.core.GenericTypeResolver;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractGenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;

/** Spring MVC HTTP message converter backed by a thread-safe {@link ForyJson} runtime. */
public final class ForyJsonHttpMessageConverter
    extends AbstractGenericHttpMessageConverter<Object> {
  /** Default maximum number of bytes read from one HTTP request body: 64 MiB. */
  public static final int DEFAULT_MAX_INPUT_BYTES = 64 * 1024 * 1024;

  private static final int READ_BUFFER_SIZE = 8192;

  private final ForyJson foryJson;
  private final int maxInputBytes;

  /** Creates a converter with {@link #DEFAULT_MAX_INPUT_BYTES}. */
  public ForyJsonHttpMessageConverter(ForyJson foryJson) {
    this(foryJson, DEFAULT_MAX_INPUT_BYTES);
  }

  /** Creates a converter with the given positive request-body byte limit. */
  public ForyJsonHttpMessageConverter(ForyJson foryJson, int maxInputBytes) {
    super(
        StandardCharsets.UTF_8,
        MediaType.APPLICATION_JSON,
        SpringJsonSupport.APPLICATION_JSON_SUFFIX);
    this.foryJson = Objects.requireNonNull(foryJson, "foryJson");
    if (maxInputBytes <= 0) {
      throw new IllegalArgumentException("maxInputBytes must be positive");
    }
    this.maxInputBytes = maxInputBytes;
  }

  /** Returns the shared Fory JSON runtime. */
  public ForyJson getForyJson() {
    return foryJson;
  }

  /** Returns the maximum number of bytes read from one request body. */
  public int getMaxInputBytes() {
    return maxInputBytes;
  }

  @Override
  protected boolean supports(Class<?> type) {
    return SpringJsonSupport.supportsClass(type);
  }

  @Override
  public boolean canRead(Class<?> type, MediaType mediaType) {
    return supports(type) && SpringJsonSupport.supportsMimeType(mediaType, false);
  }

  @Override
  public boolean canRead(Type type, Class<?> contextClass, MediaType mediaType) {
    Type resolvedType = GenericTypeResolver.resolveType(type, contextClass);
    return SpringJsonSupport.supportsType(resolvedType)
        && SpringJsonSupport.supportsMimeType(mediaType, false);
  }

  @Override
  public boolean canWrite(Class<?> type, MediaType mediaType) {
    return supports(type) && SpringJsonSupport.supportsMimeType(mediaType, false);
  }

  @Override
  public boolean canWrite(Type type, Class<?> valueClass, MediaType mediaType) {
    return SpringJsonSupport.supportsClass(valueClass)
        && (type == null || SpringJsonSupport.supportsType(type))
        && SpringJsonSupport.supportsMimeType(mediaType, false);
  }

  @Override
  protected Object readInternal(Class<?> type, HttpInputMessage inputMessage) throws IOException {
    byte[] bytes = readBody(inputMessage);
    try {
      return SpringJsonSupport.read(foryJson, bytes, type);
    } catch (RuntimeException e) {
      throw notReadable(e, inputMessage);
    }
  }

  @Override
  public Object read(Type type, Class<?> contextClass, HttpInputMessage inputMessage)
      throws IOException {
    Type resolvedType = GenericTypeResolver.resolveType(type, contextClass);
    byte[] bytes = readBody(inputMessage);
    try {
      return SpringJsonSupport.read(foryJson, bytes, resolvedType);
    } catch (RuntimeException e) {
      throw notReadable(e, inputMessage);
    }
  }

  @Override
  protected void writeInternal(Object value, Type type, HttpOutputMessage outputMessage)
      throws IOException {
    byte[] bytes;
    try {
      bytes = SpringJsonSupport.write(foryJson, value, type);
    } catch (RuntimeException e) {
      throw new HttpMessageNotWritableException("Could not write Fory JSON", e);
    }
    outputMessage.getHeaders().setContentLength(bytes.length);
    outputMessage.getBody().write(bytes);
  }

  private byte[] readBody(HttpInputMessage inputMessage) throws IOException {
    InputStream input = inputMessage.getBody();
    ByteArrayOutputStream output =
        new ByteArrayOutputStream(Math.min(READ_BUFFER_SIZE, maxInputBytes));
    byte[] buffer = new byte[READ_BUFFER_SIZE];
    int total = 0;
    while (true) {
      int count = input.read(buffer);
      if (count < 0) {
        break;
      }
      if (count == 0) {
        int value = input.read();
        if (value < 0) {
          break;
        }
        checkInputSize(total, 1, inputMessage);
        output.write(value);
        total++;
        continue;
      }
      checkInputSize(total, count, inputMessage);
      output.write(buffer, 0, count);
      total += count;
    }
    return output.toByteArray();
  }

  private void checkInputSize(int total, int additional, HttpInputMessage inputMessage) {
    if (additional > maxInputBytes - total) {
      throw new HttpMessageNotReadableException(
          "Fory JSON input exceeds maxInputBytes of " + maxInputBytes, inputMessage);
    }
  }

  private static HttpMessageNotReadableException notReadable(
      RuntimeException cause, HttpInputMessage inputMessage) {
    return new HttpMessageNotReadableException("Could not read Fory JSON", cause, inputMessage);
  }
}
