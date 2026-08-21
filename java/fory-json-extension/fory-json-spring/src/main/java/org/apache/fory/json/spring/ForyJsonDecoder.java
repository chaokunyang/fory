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
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;
import org.apache.fory.json.ForyJson;
import org.apache.fory.json.ForyJsonException;
import org.apache.fory.json.JsonStreamDecoder;
import org.apache.fory.json.JsonStreamValueLimitException;
import org.apache.fory.reflect.TypeRef;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.AbstractDataBufferDecoder;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.codec.Hints;
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

  private static final int MAX_VALUE_BYTES = Integer.MAX_VALUE - 8;
  private static final Object END_OF_BUFFER = new Object();

  private final ForyJson foryJson;

  /** Creates a decoder with {@link #DEFAULT_MAX_IN_MEMORY_SIZE}. */
  public ForyJsonDecoder(ForyJson foryJson) {
    this(foryJson, DEFAULT_MAX_IN_MEMORY_SIZE);
  }

  /** Creates a decoder with the given per-value byte limit, or {@code -1} for no added limit. */
  public ForyJsonDecoder(ForyJson foryJson, int maxInMemorySize) {
    super(
        MediaType.APPLICATION_JSON,
        SpringJsonSupport.APPLICATION_JSON_SUFFIX,
        MediaType.APPLICATION_NDJSON);
    this.foryJson = Objects.requireNonNull(foryJson, "foryJson");
    setMaxInMemorySize(maxInMemorySize);
  }

  /** Returns the shared Fory JSON runtime. */
  public ForyJson getForyJson() {
    return foryJson;
  }

  @Override
  public void setMaxInMemorySize(int byteCount) {
    if (byteCount == 0 || byteCount < -1 || byteCount > MAX_VALUE_BYTES) {
      throw new IllegalArgumentException(
          "maxInMemorySize must be -1 or between 1 and " + MAX_VALUE_BYTES + ": " + byteCount);
    }
    super.setMaxInMemorySize(byteCount);
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
    return Flux.defer(() -> decodeStream(input, elementType.getType(), isNdjson(mimeType)));
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

  private Flux<Object> decodeStream(Publisher<DataBuffer> input, Type type, boolean ndjson) {
    boolean problemDetail = ProblemDetail.class.equals(TypeRef.of(type).getRawType());
    TypeRef<Object> streamType = TypeRef.of(problemDetail ? Object.class : type);
    int maxValueBytes = getMaxInMemorySize();
    if (maxValueBytes < 0) {
      maxValueBytes = MAX_VALUE_BYTES;
    }
    JsonStreamDecoder<Object> decoder =
        ndjson
            ? foryJson.newNdjsonStreamDecoder(streamType, maxValueBytes)
            : foryJson.newArrayStreamDecoder(streamType, maxValueBytes);
    return Flux.from(input)
        .concatMap(buffer -> decodeBuffer(buffer, decoder, problemDetail), 1)
        .concatWith(Mono.defer(() -> finishStream(decoder, problemDetail)))
        .doOnDiscard(DataBuffer.class, DataBufferUtils::release)
        .onErrorMap(ForyJsonException.class, this::streamError);
  }

  private Flux<Object> decodeBuffer(
      DataBuffer buffer, JsonStreamDecoder<Object> decoder, boolean problemDetail) {
    return Flux.defer(
        () -> {
          DataBufferCursor cursor = new DataBufferCursor(buffer);
          try {
            Object value = readNext(cursor, decoder, problemDetail);
            if (value == END_OF_BUFFER) {
              cursor.release();
              return Flux.empty();
            }
            cursor.pendingValue = value;
          } catch (RuntimeException e) {
            cursor.release();
            return Flux.error(e);
          }
          return Flux.<Object>generate(
                  sink -> {
                    Object value = cursor.pendingValue;
                    sink.next(value);
                    try {
                      Object next = readNext(cursor, decoder, problemDetail);
                      if (next == END_OF_BUFFER) {
                        sink.complete();
                      } else {
                        cursor.pendingValue = next;
                      }
                    } catch (RuntimeException e) {
                      sink.error(e);
                    }
                  })
              .doFinally(signal -> cursor.release());
        });
  }

  private Object readNext(
      DataBufferCursor cursor, JsonStreamDecoder<Object> decoder, boolean problemDetail) {
    while (true) {
      ByteBuffer bytes = cursor.next();
      if (bytes == null) {
        return END_OF_BUFFER;
      }
      if (decoder.decodeNext(bytes)) {
        Object value = decoder.value();
        if (value != null) {
          return problemDetail ? readProblemDetail(value) : value;
        }
      }
    }
  }

  private Mono<Object> finishStream(JsonStreamDecoder<Object> decoder, boolean problemDetail) {
    try {
      if (!decoder.finish()) {
        return Mono.empty();
      }
      Object value = decoder.value();
      return value == null
          ? Mono.empty()
          : Mono.just(problemDetail ? readProblemDetail(value) : value);
    } catch (RuntimeException e) {
      return Mono.error(e);
    }
  }

  private RuntimeException streamError(ForyJsonException error) {
    if (error instanceof JsonStreamValueLimitException limitError) {
      return new DataBufferLimitException(
          "Exceeded limit on max bytes to buffer: " + limitError.getMaxValueBytes(), error);
    }
    return new DecodingException("Fory JSON stream decoding error", error);
  }

  private static boolean isNdjson(MimeType mimeType) {
    return mimeType != null && MediaType.APPLICATION_NDJSON.isCompatibleWith(mimeType);
  }

  private static final class DataBufferCursor {
    private final DataBuffer buffer;
    private final DataBuffer.ByteBufferIterator buffers;
    private ByteBuffer current;
    private Object pendingValue;

    private DataBufferCursor(DataBuffer buffer) {
      this.buffer = buffer;
      try {
        buffers = buffer.readableByteBuffers();
      } catch (RuntimeException | Error e) {
        DataBufferUtils.release(buffer);
        throw e;
      }
    }

    private ByteBuffer next() {
      while (current == null || !current.hasRemaining()) {
        if (!buffers.hasNext()) {
          return null;
        }
        current = buffers.next();
      }
      return current;
    }

    private void release() {
      try {
        buffers.close();
      } finally {
        DataBufferUtils.release(buffer);
      }
    }
  }
}
