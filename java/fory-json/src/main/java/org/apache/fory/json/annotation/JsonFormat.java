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

package org.apache.fory.json.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Selects a custom textual format for one directly declared date/time field or direct wrapper
 * child.
 *
 * <p>The pattern uses {@link java.time.format.DateTimeFormatter} syntax with {@link
 * java.util.Locale#ROOT}. Supported values are {@link java.time.LocalDate}, {@link
 * java.time.LocalTime}, {@link java.time.LocalDateTime}, {@link java.time.Instant}, {@link
 * java.time.ZonedDateTime}, {@link java.time.Year}, {@link java.time.YearMonth}, {@link
 * java.time.MonthDay}, {@link java.time.OffsetTime}, {@link java.time.OffsetDateTime}, {@link
 * java.time.chrono.HijrahDate}, {@link java.time.chrono.JapaneseDate}, {@link
 * java.time.chrono.MinguoDate}, and {@link java.time.chrono.ThaiBuddhistDate}. Instant values use
 * UTC; zoned and offset values use the zone or offset carried by the value. The pattern must retain
 * enough information to reconstruct the declared type.
 *
 * <p>Formatting is applied in both JSON directions. Arrays and collections apply it to their direct
 * element, maps to their direct value, and optional and atomic-reference wrappers to their direct
 * content. Nested wrappers, map keys, JSON Any values, and unwrapped properties are not supported.
 * A wrapper with a complete custom or {@link JsonValue} representation is also rejected.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface JsonFormat {
  /** Returns the required date/time pattern. */
  String pattern();
}
