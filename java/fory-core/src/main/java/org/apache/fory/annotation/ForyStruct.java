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

package org.apache.fory.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marker annotation for Fory-serializable types with optional serialization behavior settings. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ForyStruct {
  enum Evolution {
    /** Follow Fory global compatible/meta-share options. */
    INHERIT,

    /** Require schema evolution metadata for this struct. */
    ENABLED,

    /** Require fixed-schema struct encoding for this struct. */
    DISABLED
  }

  /**
   * Legacy per-struct schema evolution switch.
   *
   * <p>Set this to {@code false} to force fixed-schema struct encoding. New code that needs to
   * require schema evolution metadata should use {@link #evolution()}.
   */
  boolean evolving() default true;

  /**
   * Per-struct schema evolution policy.
   *
   * <p>{@link Evolution#INHERIT} follows {@link #evolving()} and then the Fory instance's
   * compatible/meta-share configuration. {@link Evolution#ENABLED} requires that configuration to
   * emit schema evolution metadata for this struct. {@link Evolution#DISABLED} uses fixed-schema
   * struct encoding even when compatible metadata is otherwise enabled.
   */
  Evolution evolution() default Evolution.INHERIT;
}
