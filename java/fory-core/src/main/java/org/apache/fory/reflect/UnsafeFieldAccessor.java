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
import org.apache.fory.platform.UnsafeOps;
import org.apache.fory.util.Preconditions;
import sun.misc.Unsafe;

/** An object field accessor based on {@link Unsafe}. */
public class UnsafeFieldAccessor {
  private final Field field;
  private final long fieldOffset;

  /**
   * Search parent class if <code>cls</code> doesn't have a field named <code>fieldName</code>.
   *
   * @param cls class
   * @param fieldName field name
   */
  public UnsafeFieldAccessor(Class<?> cls, String fieldName) {
    this(ReflectionUtils.getField(cls, fieldName));
  }

  public UnsafeFieldAccessor(Field field) {
    Preconditions.checkNotNull(field);
    this.field = field;
    this.fieldOffset = ReflectionUtils.getFieldOffset(field);
    Preconditions.checkArgument(fieldOffset != -1);
  }

  public Field getField() {
    return field;
  }

  public boolean getBoolean(Object obj) {
    return UnsafeOps.UNSAFE.getBoolean(obj, fieldOffset);
  }

  public void putBoolean(Object obj, boolean value) {
    UnsafeOps.UNSAFE.putBoolean(obj, fieldOffset, value);
  }

  public byte getByte(Object obj) {
    return UnsafeOps.UNSAFE.getByte(obj, fieldOffset);
  }

  public void putByte(Object obj, byte value) {
    UnsafeOps.UNSAFE.putByte(obj, fieldOffset, value);
  }

  public char getChar(Object obj) {
    return UnsafeOps.UNSAFE.getChar(obj, fieldOffset);
  }

  public void putChar(Object obj, char value) {
    UnsafeOps.UNSAFE.putChar(obj, fieldOffset, value);
  }

  public short getShort(Object obj) {
    return UnsafeOps.UNSAFE.getShort(obj, fieldOffset);
  }

  public void putShort(Object obj, short value) {
    UnsafeOps.UNSAFE.putShort(obj, fieldOffset, value);
  }

  public int getInt(Object obj) {
    return UnsafeOps.UNSAFE.getInt(obj, fieldOffset);
  }

  public void putInt(Object obj, int value) {
    UnsafeOps.UNSAFE.putInt(obj, fieldOffset, value);
  }

  public long getLong(Object obj) {
    return UnsafeOps.UNSAFE.getLong(obj, fieldOffset);
  }

  public void putLong(Object obj, long value) {
    UnsafeOps.UNSAFE.putLong(obj, fieldOffset, value);
  }

  public float getFloat(Object obj) {
    return UnsafeOps.UNSAFE.getFloat(obj, fieldOffset);
  }

  public void putFloat(Object obj, float value) {
    UnsafeOps.UNSAFE.putFloat(obj, fieldOffset, value);
  }

  public double getDouble(Object obj) {
    return UnsafeOps.UNSAFE.getDouble(obj, fieldOffset);
  }

  public void putDouble(Object obj, double value) {
    UnsafeOps.UNSAFE.putDouble(obj, fieldOffset, value);
  }

  public Object getObject(Object obj) {
    return UnsafeOps.UNSAFE.getObject(obj, fieldOffset);
  }

  public void putObject(Object obj, Object value) {
    UnsafeOps.UNSAFE.putObject(obj, fieldOffset, value);
  }
}
