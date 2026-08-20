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

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.fory.json.ForyJson;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.AbstractDataBufferDecoder;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.codec.Hints;
import org.springframework.core.codec.StringDecoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.codec.HttpMessageDecoder;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Spring WebFlux JSON decoder backed by a thread-safe {@link ForyJson} runtime. */
public final class ForyJsonDecoder extends AbstractDataBufferDecoder<Object>
    implements HttpMessageDecoder<Object> {
  /** Spring-compatible default maximum buffered JSON value size: 256 KiB. */
  public static final int DEFAULT_MAX_IN_MEMORY_SIZE = 256 * 1024;

  private static final ResolvableType STRING_TYPE = ResolvableType.forClass(String.class);
  private static final MimeType TEXT_PLAIN_UTF8 =
      new MimeType("text", "plain", StandardCharsets.UTF_8);

  private final ForyJson foryJson;
  private final StringDecoder ndjsonDecoder;

  /** Creates a decoder with {@link #DEFAULT_MAX_IN_MEMORY_SIZE}. */
  public ForyJsonDecoder(ForyJson foryJson) {
    this(foryJson, DEFAULT_MAX_IN_MEMORY_SIZE);
  }

  /** Creates a decoder with the given Spring WebFlux in-memory byte limit. */
  public ForyJsonDecoder(ForyJson foryJson, int maxInMemorySize) {
    super(
        MediaType.APPLICATION_JSON,
        SpringJsonSupport.APPLICATION_JSON_SUFFIX,
        MediaType.APPLICATION_NDJSON);
    this.foryJson = Objects.requireNonNull(foryJson, "foryJson");
    ndjsonDecoder = StringDecoder.textPlainOnly();
    setMaxInMemorySize(maxInMemorySize);
  }

  /** Returns the shared Fory JSON runtime. */
  public ForyJson getForyJson() {
    return foryJson;
  }

  @Override
  public void setMaxInMemorySize(int byteCount) {
    super.setMaxInMemorySize(byteCount);
    if (ndjsonDecoder != null) {
      ndjsonDecoder.setMaxInMemorySize(byteCount);
    }
  }

  @Override
  public boolean canDecode(ResolvableType elementType, MimeType mimeType) {
    return SpringJsonSupport.supportsType(elementType.getType())
        && SpringJsonSupport.supportsMimeType(mimeType, true);
  }

  @Override
  public Flux<Object> decode(
      Publisher<DataBuffer> input,
      ResolvableType elementType,
      MimeType mimeType,
      Map<String, Object> hints) {
    if (isNdjson(mimeType)) {
      return ndjsonDecoder
          .decode(input, STRING_TYPE, TEXT_PLAIN_UTF8, hints)
          .filter(line -> !line.trim().isEmpty())
          .<Object>handle(
              (line, sink) -> {
                byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
                int limit = getMaxInMemorySize();
                if (limit >= 0 && bytes.length > limit) {
                  sink.error(
                      new DataBufferLimitException(
                          "Exceeded limit on max bytes to buffer: " + limit));
                  return;
                }
                Object value = read(bytes, elementType.getType());
                if (value != null) {
                  sink.next(value);
                }
              });
    }
    if (elementType.resolve() == ProblemDetail.class) {
      return decodeToMono(input, ResolvableType.forClass(Object.class), mimeType, hints)
          .flatMapMany(
              value -> {
                if (!(value instanceof List<?> values)) {
                  return Flux.error(new DecodingException("Expected a JSON array"));
                }
                return Flux.fromStream(() -> values.stream().filter(Objects::nonNull))
                    .map(this::readProblemDetail);
              });
    }
    ResolvableType listType = ResolvableType.forClassWithGenerics(List.class, elementType);
    return decodeToMono(input, listType, mimeType, hints)
        .flatMapMany(
            value -> Flux.fromStream(() -> ((List<?>) value).stream().filter(Objects::nonNull)));
  }

  @Override
  public Object decode(
      DataBuffer buffer, ResolvableType targetType, MimeType mimeType, Map<String, Object> hints)
      throws DecodingException {
    try {
      int length = buffer.readableByteCount();
      int limit = getMaxInMemorySize();
      if (limit >= 0 && length > limit) {
        throw new DataBufferLimitException("Exceeded limit on max bytes to buffer: " + limit);
      }
      byte[] bytes = new byte[length];
      buffer.read(bytes);
      return read(bytes, targetType.getType());
    } finally {
      DataBufferUtils.release(buffer);
    }
  }

  @Override
  public Mono<Object> decodeToMono(
      Publisher<DataBuffer> input,
      ResolvableType elementType,
      MimeType mimeType,
      Map<String, Object> hints) {
    return DataBufferUtils.join(input, getMaxInMemorySize())
        .flatMap(buffer -> Mono.justOrEmpty(decode(buffer, elementType, mimeType, hints)));
  }

  @Override
  public Map<String, Object> getDecodeHints(
      ResolvableType actualType,
      ResolvableType elementType,
      ServerHttpRequest request,
      ServerHttpResponse response) {
    return Hints.none();
  }

  private Object read(byte[] bytes, Type type) {
    try {
      return SpringJsonSupport.read(foryJson, bytes, type);
    } catch (RuntimeException e) {
      throw new DecodingException("Fory JSON decoding error", e);
    }
  }

  private ProblemDetail readProblemDetail(Object value) {
    try {
      return SpringJsonSupport.readProblemDetail(value);
    } catch (RuntimeException e) {
      throw new DecodingException("Fory JSON ProblemDetail decoding error", e);
    }
  }

  private static boolean isNdjson(MimeType mimeType) {
    return mimeType != null && MediaType.APPLICATION_NDJSON.isCompatibleWith(mimeType);
  }
}
