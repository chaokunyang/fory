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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.fory.json.ForyJson;
import org.apache.fory.reflect.TypeRef;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.AbstractDataBufferDecoder;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.codec.StringDecoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Spring WebFlux decoder backed by {@link ForyJson}. */
public final class ForyJsonDecoder extends AbstractDataBufferDecoder<Object> {
  /** Spring's default maximum in-memory codec size: 256 KiB. */
  public static final int DEFAULT_MAX_IN_MEMORY_SIZE = 256 * 1024;

  private static final MimeType APPLICATION_PLUS_JSON = new MimeType("application", "*+json");

  private final ForyJson foryJson;
  private final StringDecoder ndjsonDecoder;

  /** Creates a decoder with Spring's default 256 KiB in-memory limit. */
  public ForyJsonDecoder(ForyJson foryJson) {
    this(foryJson, DEFAULT_MAX_IN_MEMORY_SIZE);
  }

  /** Creates a decoder with the given per-value in-memory limit. */
  public ForyJsonDecoder(ForyJson foryJson, int maxInMemorySize) {
    super(MediaType.APPLICATION_JSON, APPLICATION_PLUS_JSON, MediaType.APPLICATION_NDJSON);
    this.foryJson = Objects.requireNonNull(foryJson, "foryJson");
    this.ndjsonDecoder = StringDecoder.textPlainOnly(Arrays.asList("\r\n", "\n"), true);
    setMaxInMemorySize(maxInMemorySize);
  }

  /** Returns the Fory JSON runtime used by this decoder. */
  public ForyJson getForyJson() {
    return foryJson;
  }

  /** Sets the maximum bytes buffered for one JSON value. A value of {@code -1} is unlimited. */
  @Override
  public void setMaxInMemorySize(int maxInMemorySize) {
    super.setMaxInMemorySize(maxInMemorySize);
    ndjsonDecoder.setMaxInMemorySize(maxInMemorySize);
  }

  @Override
  public boolean canDecode(ResolvableType elementType, MimeType mimeType) {
    return ForyJsonCodecSupport.supportsType(elementType)
        && ForyJsonCodecSupport.supportsMimeType(mimeType)
        && super.canDecode(elementType, mimeType);
  }

  @Override
  public Flux<Object> decode(
      Publisher<DataBuffer> inputStream,
      ResolvableType elementType,
      MimeType mimeType,
      Map<String, Object> hints) {
    if (isNdjson(mimeType)) {
      return ndjsonDecoder
          .decode(inputStream, ResolvableType.forClass(String.class), mimeType, hints)
          .<Object>handle(
              (line, sink) -> {
                byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
                if (getMaxInMemorySize() >= 0 && bytes.length > getMaxInMemorySize()) {
                  sink.error(limitException());
                } else if (!line.trim().isEmpty()) {
                  Object value = read(bytes, elementType);
                  if (value != null) {
                    sink.next(value);
                  }
                }
              });
    }
    return DataBufferUtils.join(inputStream, getMaxInMemorySize())
        .flatMapMany(buffer -> decodeArray(copyAndRelease(buffer), elementType));
  }

  @Override
  public Object decode(
      DataBuffer buffer, ResolvableType targetType, MimeType mimeType, Map<String, Object> hints)
      throws DecodingException {
    return read(copyAndRelease(buffer), targetType);
  }

  @Override
  public Mono<Object> decodeToMono(
      Publisher<DataBuffer> inputStream,
      ResolvableType elementType,
      MimeType mimeType,
      Map<String, Object> hints) {
    return DataBufferUtils.join(inputStream, getMaxInMemorySize())
        .flatMap(buffer -> Mono.justOrEmpty(read(copyAndRelease(buffer), elementType)));
  }

  private Flux<Object> decodeArray(byte[] bytes, ResolvableType elementType) {
    try {
      if (elementType.resolve() == ProblemDetail.class) {
        Object decoded = foryJson.fromJson(bytes, Object.class);
        if (!(decoded instanceof List<?> values)) {
          throw new DecodingException("Expected a JSON array");
        }
        return Flux.fromStream(() -> values.stream().filter(Objects::nonNull))
            .map(ForyJsonCodecSupport::readProblemDetail);
      }
      ResolvableType listType = ResolvableType.forClassWithGenerics(List.class, elementType);
      Object decoded = foryJson.fromJson(bytes, TypeRef.of(listType.getType()));
      if (!(decoded instanceof List<?> values)) {
        throw new DecodingException("Expected a JSON array");
      }
      return Flux.fromStream(() -> values.stream().filter(Objects::nonNull)).cast(Object.class);
    } catch (DecodingException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new DecodingException("Could not read Fory JSON array", e);
    }
  }

  private Object read(byte[] bytes, ResolvableType elementType) {
    try {
      return ForyJsonCodecSupport.read(foryJson, bytes, elementType);
    } catch (RuntimeException e) {
      throw new DecodingException("Could not read Fory JSON", e);
    }
  }

  private byte[] copyAndRelease(DataBuffer buffer) {
    try {
      int readableBytes = buffer.readableByteCount();
      if (getMaxInMemorySize() >= 0 && readableBytes > getMaxInMemorySize()) {
        throw limitException();
      }
      byte[] bytes = new byte[readableBytes];
      buffer.read(bytes);
      return bytes;
    } finally {
      DataBufferUtils.release(buffer);
    }
  }

  private static boolean isNdjson(MimeType mimeType) {
    return mimeType != null && MediaType.APPLICATION_NDJSON.isCompatibleWith(mimeType);
  }

  private DataBufferLimitException limitException() {
    return new DataBufferLimitException(
        "Exceeded limit on max bytes to buffer: " + getMaxInMemorySize());
  }
}
