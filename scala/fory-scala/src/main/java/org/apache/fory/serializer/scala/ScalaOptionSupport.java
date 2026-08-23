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

package org.apache.fory.serializer.scala;

import org.apache.fory.annotation.Internal;
import org.apache.fory.context.ReadContext;

/** Materializes Scala {@link scala.Option} values with their retained-owner reservation. */
@Internal
public final class ScalaOptionSupport {
  private ScalaOptionSupport() {}

  public static scala.Option<Object> wrap(ReadContext readContext, Object value) {
    if (value == null) {
      return scala.Option.empty();
    }
    readContext.reserveGraphMemory(ScalaGraphMemory.SOME_BYTES);
    return new scala.Some<>(value);
  }
}
