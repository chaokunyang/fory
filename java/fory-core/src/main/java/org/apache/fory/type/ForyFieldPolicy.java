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

package org.apache.fory.type;

import java.util.Objects;
import org.apache.fory.annotation.ForyField;
import org.apache.fory.annotation.Internal;

/** Descriptor-owned representation of {@link ForyField} values for generated descriptors. */
@Internal
public final class ForyFieldPolicy {
  private final int id;
  private final boolean nullable;
  private final boolean trackingRef;
  private final ForyField.Dynamic dynamic;

  public static ForyFieldPolicy of(
      int id, boolean nullable, boolean trackingRef, ForyField.Dynamic dynamic) {
    return new ForyFieldPolicy(id, nullable, trackingRef, dynamic);
  }

  public static ForyFieldPolicy from(ForyField field) {
    if (field == null) {
      return null;
    }
    return of(field.id(), field.nullable(), field.ref(), field.dynamic());
  }

  private ForyFieldPolicy(
      int id, boolean nullable, boolean trackingRef, ForyField.Dynamic dynamic) {
    this.id = id;
    this.nullable = nullable;
    this.trackingRef = trackingRef;
    this.dynamic = Objects.requireNonNull(dynamic);
  }

  public int id() {
    return id;
  }

  public boolean nullable() {
    return nullable;
  }

  public boolean trackingRef() {
    return trackingRef;
  }

  public ForyField.Dynamic dynamic() {
    return dynamic;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ForyFieldPolicy)) {
      return false;
    }
    ForyFieldPolicy that = (ForyFieldPolicy) o;
    return id == that.id
        && nullable == that.nullable
        && trackingRef == that.trackingRef
        && dynamic == that.dynamic;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, nullable, trackingRef, dynamic);
  }
}
