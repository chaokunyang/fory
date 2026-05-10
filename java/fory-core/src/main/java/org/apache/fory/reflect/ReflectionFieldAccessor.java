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

package org.apache.fory.reflect;

import java.lang.reflect.Field;
import org.apache.fory.exception.ForyException;

final class ReflectionFieldAccessor extends FieldAccessor {
  ReflectionFieldAccessor(Field field) {
    super(field, -1);
    try {
      field.setAccessible(true);
    } catch (RuntimeException e) {
      throw new ForyException("Failed to make field accessible: " + field, e);
    }
  }

  @Override
  public Object get(Object obj) {
    checkObj(obj);
    try {
      return field.get(obj);
    } catch (IllegalAccessException | IllegalArgumentException e) {
      throw new ForyException("Failed to read field reflectively: " + field, e);
    }
  }

  @Override
  public void set(Object obj, Object value) {
    checkObj(obj);
    try {
      field.set(obj, value);
    } catch (IllegalAccessException | IllegalArgumentException e) {
      throw new ForyException("Failed to write field reflectively: " + field, e);
    }
  }
}
