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

package org.apache.fory.json.codec;

import org.apache.fory.json.resolver.JsonTypeResolver;
import org.apache.fory.json.writer.StringJsonWriter;
import org.apache.fory.json.writer.Utf8JsonWriter;

public final class GeneratedObjectCodec extends BaseObjectCodec {
  private final StringObjectWriter stringWriter;
  private final Utf8ObjectWriter utf8Writer;

  GeneratedObjectCodec(ObjectCodec base, ObjectWriters writers) {
    super(base.type, base.writeProperties, base.readProperties, base.instantiator);
    stringWriter = writers.stringWriter();
    utf8Writer = writers.utf8Writer();
  }

  @Override
  void writeStringNonNull(StringJsonWriter writer, Object value, JsonTypeResolver resolver) {
    Class<?> valueClass = value.getClass();
    if (valueClass == type) {
      stringWriter.writeString(writer, value, resolver);
    } else {
      resolver.writeStringValue(writer, value, valueClass);
    }
  }

  @Override
  void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
    Class<?> valueClass = value.getClass();
    if (valueClass == type) {
      utf8Writer.writeUtf8(writer, value, resolver);
    } else {
      resolver.writeUtf8Value(writer, value, valueClass);
    }
  }
}
