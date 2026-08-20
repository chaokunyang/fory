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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.fory.json.ForyJson;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.converter.SmartHttpMessageConverter;

/** Spring MVC message converter backed by {@link ForyJson}. */
public final class ForyJsonHttpMessageConverter implements SmartHttpMessageConverter<Object> {
  /** Default maximum request body size: 64 MiB. */
  public static final int DEFAULT_MAX_INPUT_BYTES = 64 * 1024 * 1024;

  private static final MediaType APPLICATION_JSON_UTF8 =
      new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8);
  private static final MediaType APPLICATION_PLUS_JSON = new MediaType("application", "*+json");
  private static final List<MediaType> SUPPORTED_MEDIA_TYPES =
      Collections.unmodifiableList(
          Arrays.asList(MediaType.APPLICATION_JSON, APPLICATION_PLUS_JSON));
  private static final int READ_BUFFER_SIZE = 8192;

  private final ForyJson foryJson;
  private final int maxInputBytes;

  /** Creates a converter with the default 64 MiB request body limit. */
  public ForyJsonHttpMessageConverter(ForyJson foryJson) {
    this(foryJson, DEFAULT_MAX_INPUT_BYTES);
  }

  /** Creates a converter with the given positive request body limit. */
  public ForyJsonHttpMessageConverter(ForyJson foryJson, int maxInputBytes) {
    this.foryJson = Objects.requireNonNull(foryJson, "foryJson");
    if (maxInputBytes <= 0) {
      throw new IllegalArgumentException("maxInputBytes must be positive");
    }
    this.maxInputBytes = maxInputBytes;
  }

  /** Returns the Fory JSON runtime used by this converter. */
  public ForyJson getForyJson() {
    return foryJson;
  }

  /** Returns the maximum request body size in bytes. */
  public int getMaxInputBytes() {
    return maxInputBytes;
  }

  @Override
  public boolean canRead(ResolvableType type, MediaType mediaType) {
    return ForyJsonCodecSupport.supportsType(type) && supportsMediaType(mediaType);
  }

  @Override
  public Object read(ResolvableType type, HttpInputMessage inputMessage, Map<String, Object> hints)
      throws IOException, HttpMessageNotReadableException {
    byte[] bytes = readBody(inputMessage);
    try {
      return ForyJsonCodecSupport.read(foryJson, bytes, type);
    } catch (HttpMessageNotReadableException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new HttpMessageNotReadableException("Could not read Fory JSON", e, inputMessage);
    }
  }

  @Override
  public boolean canWrite(ResolvableType type, Class<?> valueClass, MediaType mediaType) {
    return ForyJsonCodecSupport.supportsClass(valueClass)
        && (type == ResolvableType.NONE || ForyJsonCodecSupport.supportsType(type))
        && supportsMediaType(mediaType);
  }

  @Override
  public void write(
      Object value,
      ResolvableType type,
      MediaType contentType,
      HttpOutputMessage outputMessage,
      Map<String, Object> hints)
      throws IOException, HttpMessageNotWritableException {
    byte[] bytes;
    try {
      bytes = ForyJsonCodecSupport.write(foryJson, value, type);
    } catch (RuntimeException e) {
      throw new HttpMessageNotWritableException("Could not write Fory JSON", e);
    }
    MediaType selected = contentType == null ? APPLICATION_JSON_UTF8 : contentType;
    outputMessage.getHeaders().setContentType(new MediaType(selected, StandardCharsets.UTF_8));
    outputMessage.getHeaders().setContentLength(bytes.length);
    outputMessage.getBody().write(bytes);
  }

  @Override
  public List<MediaType> getSupportedMediaTypes() {
    return SUPPORTED_MEDIA_TYPES;
  }

  private byte[] readBody(HttpInputMessage inputMessage) throws IOException {
    InputStream input = inputMessage.getBody();
    ByteArrayOutputStream output =
        new ByteArrayOutputStream(Math.min(READ_BUFFER_SIZE, maxInputBytes));
    byte[] buffer = new byte[Math.min(READ_BUFFER_SIZE, maxInputBytes)];
    int total = 0;
    while (total < maxInputBytes) {
      int read = input.read(buffer, 0, Math.min(buffer.length, maxInputBytes - total));
      if (read < 0) {
        return output.toByteArray();
      }
      if (read == 0) {
        int next = input.read();
        if (next < 0) {
          return output.toByteArray();
        }
        output.write(next);
        total++;
      } else {
        output.write(buffer, 0, read);
        total += read;
      }
    }
    if (input.read() != -1) {
      throw new HttpMessageNotReadableException(
          "Fory JSON request body exceeds " + maxInputBytes + " bytes", inputMessage);
    }
    return output.toByteArray();
  }

  private static boolean supportsMediaType(MediaType mediaType) {
    if (mediaType == null) {
      return true;
    }
    if (!ForyJsonCodecSupport.supportsMimeType(mediaType)) {
      return false;
    }
    for (MediaType supported : SUPPORTED_MEDIA_TYPES) {
      if (supported.isCompatibleWith(mediaType)) {
        return true;
      }
    }
    return false;
  }
}
