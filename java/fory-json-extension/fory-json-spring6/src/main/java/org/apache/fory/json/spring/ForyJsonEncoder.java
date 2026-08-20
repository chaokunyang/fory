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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.fory.json.ForyJson;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.AbstractEncoder;
import org.springframework.core.codec.EncodingException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.HttpMessageEncoder;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** Spring WebFlux encoder backed by {@link ForyJson}. */
public final class ForyJsonEncoder extends AbstractEncoder<Object>
    implements HttpMessageEncoder<Object> {
  private static final MimeType APPLICATION_PLUS_JSON = new MimeType("application", "*+json");
  private static final byte[] ARRAY_START = {'['};
  private static final byte[] ARRAY_END = {']'};

  private final ForyJson foryJson;

  /** Creates a WebFlux encoder using the given Fory JSON runtime. */
  public ForyJsonEncoder(ForyJson foryJson) {
    super(MediaType.APPLICATION_JSON, APPLICATION_PLUS_JSON, MediaType.APPLICATION_NDJSON);
    this.foryJson = Objects.requireNonNull(foryJson, "foryJson");
  }

  /** Returns the Fory JSON runtime used by this encoder. */
  public ForyJson getForyJson() {
    return foryJson;
  }

  @Override
  public boolean canEncode(ResolvableType elementType, MimeType mimeType) {
    return ForyJsonCodecSupport.supportsType(elementType)
        && ForyJsonCodecSupport.supportsMimeType(mimeType)
        && super.canEncode(elementType, mimeType);
  }

  @Override
  public Flux<DataBuffer> encode(
      Publisher<?> inputStream,
      DataBufferFactory bufferFactory,
      ResolvableType elementType,
      MimeType mimeType,
      Map<String, Object> hints) {
    if (inputStream instanceof Mono<?>) {
      return Flux.from(inputStream)
          .map(value -> encodeValue(value, bufferFactory, elementType, mimeType, hints))
          .doOnDiscard(DataBuffer.class, DataBufferUtils.releaseConsumer());
    }
    if (isNdjson(mimeType)) {
      return Flux.from(inputStream)
          .map(value -> encodeNdjsonValue(value, bufferFactory, elementType))
          .doOnDiscard(DataBuffer.class, DataBufferUtils.releaseConsumer());
    }
    // Prefix the first value instead of emitting '[' eagerly, so a first-signal error leaves the
    // HTTP response uncommitted and available to the WebFlux error handler.
    return Flux.defer(
            () -> {
              AtomicBoolean first = new AtomicBoolean(true);
              Flux<DataBuffer> values =
                  Flux.from(inputStream)
                      .map(
                          value ->
                              encodeArrayValue(
                                  value, bufferFactory, elementType, first.getAndSet(false)));
              return values
                  .switchIfEmpty(Mono.fromSupplier(() -> bufferFactory.wrap(ARRAY_START)))
                  .concatWith(Mono.fromSupplier(() -> bufferFactory.wrap(ARRAY_END)));
            })
        .doOnDiscard(DataBuffer.class, DataBufferUtils.releaseConsumer());
  }

  @Override
  public DataBuffer encodeValue(
      Object value,
      DataBufferFactory bufferFactory,
      ResolvableType valueType,
      MimeType mimeType,
      Map<String, Object> hints) {
    return bufferFactory.wrap(write(value, valueType));
  }

  @Override
  public List<MediaType> getStreamingMediaTypes() {
    return Collections.singletonList(MediaType.APPLICATION_NDJSON);
  }

  private DataBuffer encodeArrayValue(
      Object value, DataBufferFactory bufferFactory, ResolvableType elementType, boolean first) {
    byte[] json = write(value, elementType);
    byte[] result = new byte[json.length + 1];
    result[0] = first ? (byte) '[' : (byte) ',';
    System.arraycopy(json, 0, result, 1, json.length);
    return bufferFactory.wrap(result);
  }

  private DataBuffer encodeNdjsonValue(
      Object value, DataBufferFactory bufferFactory, ResolvableType elementType) {
    byte[] json = write(value, elementType);
    byte[] result = new byte[json.length + 1];
    System.arraycopy(json, 0, result, 0, json.length);
    result[json.length] = '\n';
    return bufferFactory.wrap(result);
  }

  private byte[] write(Object value, ResolvableType type) {
    try {
      return ForyJsonCodecSupport.write(foryJson, value, type);
    } catch (RuntimeException e) {
      throw new EncodingException("Could not write Fory JSON", e);
    }
  }

  private static boolean isNdjson(MimeType mimeType) {
    return mimeType != null && MediaType.APPLICATION_NDJSON.isCompatibleWith(mimeType);
  }
}
