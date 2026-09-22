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
 * Sets the inclusion policy for the annotated class's named serializable properties and unwrapped
 * property groups. Dynamic entries from {@link JsonAnyGetter} are not filtered by this policy.
 *
 * <p>Explicit {@link JsonProperty#include()} policies take precedence. This annotation is not
 * inherited by subclasses; an exact-target {@link JsonMixin} may supply or replace it. {@link
 * JsonProperty.Include#DEFAULT} inherits the runtime setting.
 *
 * <p>{@link JsonProperty.Include#NON_DEFAULT} explicitly authorizes default-value omission for
 * every participating property, including fields added later. The caller accepts the evaluation and
 * construction requirements documented on that policy and guarantees stable defaults with
 * equivalent missing-field recovery. Use {@code ALWAYS} on individual properties to exclude them.
 * Properties without a default remain included, even when null, zero, false, or empty. This
 * includes required constructor properties and Kotlin lateinit properties. Java models without a
 * reader no-argument construction path retain their properties. A declared default that cannot be
 * evaluated through a supported source still causes an error; for example, a Kotlin defaulted
 * property cannot use a reference object if the selected constructor also requires arguments.
 * Property-level {@code NON_DEFAULT} follows the same rules. Scala properties without a supported
 * compiler default method also remain included. {@code NON_EMPTY} does not authorize default
 * evaluation.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JsonInclude {
  /** Returns the class policy; property-level policies override it. */
  JsonProperty.Include value();
}
