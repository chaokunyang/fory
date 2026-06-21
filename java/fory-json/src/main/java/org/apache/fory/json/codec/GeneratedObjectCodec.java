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

import org.apache.fory.json.reader.JsonReader;
import org.apache.fory.json.resolver.JsonTypeInfo;
import org.apache.fory.json.resolver.JsonTypeResolver;
import org.apache.fory.json.writer.StringJsonWriter;
import org.apache.fory.json.writer.Utf8JsonWriter;

public final class GeneratedObjectCodec extends BaseObjectCodec {
  private final StringObjectWriter stringWriter;
  private final Utf8ObjectWriter utf8Writer;
  private final ObjectReader reader;

  GeneratedObjectCodec(ObjectCodec base, ObjectCodecs codecs) {
    super(base.type, base.writeFields, base.readFields, base.instantiator);
    stringWriter = codecs.stringWriter();
    utf8Writer = codecs.utf8Writer();
    reader = codecs.reader();
  }

  @Override
  void writeStringNonNull(StringJsonWriter writer, Object value, JsonTypeResolver resolver) {
    Class<?> valueClass = value.getClass();
    if (valueClass == type) {
      stringWriter.writeString(writer, value, resolver);
    } else {
      JsonTypeInfo typeInfo = resolver.getTypeInfo(valueClass, valueClass);
      typeInfo.codec().writeString(writer, value, resolver);
    }
  }

  @Override
  void writeUtf8NonNull(Utf8JsonWriter writer, Object value, JsonTypeResolver resolver) {
    Class<?> valueClass = value.getClass();
    if (valueClass == type) {
      utf8Writer.writeUtf8(writer, value, resolver);
    } else {
      JsonTypeInfo typeInfo = resolver.getTypeInfo(valueClass, valueClass);
      typeInfo.codec().writeUtf8(writer, value, resolver);
    }
  }

  @Override
  Object readNonNull(JsonReader input, JsonTypeInfo typeInfo, JsonTypeResolver resolver) {
    return reader.read(input, this, resolver);
  }
}
