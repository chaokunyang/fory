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
      Class<?> fieldType = field.getType();
      if (fieldType == boolean.class) {
        return field.getBoolean(obj);
      } else if (fieldType == byte.class) {
        return field.getByte(obj);
      } else if (fieldType == char.class) {
        return field.getChar(obj);
      } else if (fieldType == short.class) {
        return field.getShort(obj);
      } else if (fieldType == int.class) {
        return field.getInt(obj);
      } else if (fieldType == long.class) {
        return field.getLong(obj);
      } else if (fieldType == float.class) {
        return field.getFloat(obj);
      } else if (fieldType == double.class) {
        return field.getDouble(obj);
      }
      return field.get(obj);
    } catch (IllegalAccessException | IllegalArgumentException e) {
      throw new ForyException("Failed to read field reflectively: " + field, e);
    }
  }

  @Override
  public void set(Object obj, Object value) {
    checkObj(obj);
    try {
      Class<?> fieldType = field.getType();
      if (fieldType == boolean.class) {
        field.setBoolean(obj, (Boolean) value);
      } else if (fieldType == byte.class) {
        field.setByte(obj, ((Number) value).byteValue());
      } else if (fieldType == char.class) {
        field.setChar(obj, (Character) value);
      } else if (fieldType == short.class) {
        field.setShort(obj, ((Number) value).shortValue());
      } else if (fieldType == int.class) {
        field.setInt(obj, ((Number) value).intValue());
      } else if (fieldType == long.class) {
        field.setLong(obj, ((Number) value).longValue());
      } else if (fieldType == float.class) {
        field.setFloat(obj, ((Number) value).floatValue());
      } else if (fieldType == double.class) {
        field.setDouble(obj, ((Number) value).doubleValue());
      } else {
        field.set(obj, value);
      }
    } catch (IllegalAccessException | IllegalArgumentException e) {
      throw new ForyException("Failed to write field reflectively: " + field, e);
    }
  }
}
