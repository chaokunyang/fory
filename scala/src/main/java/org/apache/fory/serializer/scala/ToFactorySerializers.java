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

import org.apache.fory.Fory;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.memory.Platform;
import org.apache.fory.reflect.ReflectionUtils;
import org.apache.fory.serializer.Serializer;

import java.lang.reflect.Field;

public class ToFactorySerializers  {
  static final Class<?> IterableToFactoryClass = ReflectionUtils.loadClass(
    "scala.collection.IterableFactory$ToFactory");
  static final Class<?> MapToFactoryClass = ReflectionUtils.loadClass(
    "scala.collection.MapFactory$ToFactory");

  public static class IterableToFactorySerializer extends Serializer {
    private static final long fieldOffset;

    static {
      try {
        // for graalvm field offset auto rewrite
        Field field = Class.forName("scala.collection.IterableFactory$ToFactory").getDeclaredField("factory");
        fieldOffset = Platform.objectFieldOffset(field);
      } catch (final Exception e) {
        throw new RuntimeException(e);
      }
    }

    public IterableToFactorySerializer(Fory fory) {
      super(fory, IterableToFactoryClass);
    }

    @Override
    public void write(MemoryBuffer buffer, Object value) {
      fory.writeRef(buffer, Platform.getObject(value, fieldOffset));
    }

    @Override
    public Object read(MemoryBuffer buffer) {
      Object o = Platform.newInstance(type);
      Platform.putObject(o, fieldOffset, fory.readRef(buffer));
      return o;
    }
  }

  public static class MapToFactorySerializer extends Serializer {
    private static final long fieldOffset;

    static {
      try {
        // for graalvm field offset auto rewrite
        Field field = Class.forName("scala.collection.MapFactory$ToFactory").getDeclaredField("factory");
        fieldOffset = Platform.objectFieldOffset(field);
      } catch (final Exception e) {
        throw new RuntimeException(e);
      }
    }

    public MapToFactorySerializer(Fory fory) {
      super(fory, MapToFactoryClass);
    }

    @Override
    public void write(MemoryBuffer buffer, Object value) {
      fory.writeRef(buffer, Platform.getObject(value, fieldOffset));
    }

    @Override
    public Object read(MemoryBuffer buffer) {
      Object o = Platform.newInstance(type);
      Platform.putObject(o, fieldOffset, fory.readRef(buffer));
      return o;
    }
  }
}
