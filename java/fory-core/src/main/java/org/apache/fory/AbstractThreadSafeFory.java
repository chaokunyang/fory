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

package org.apache.fory;

import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.fory.resolver.TypeChecker;
import org.apache.fory.resolver.TypeResolver;
import org.apache.fory.serializer.Serializer;
import org.apache.fory.serializer.SerializerFactory;

public abstract class AbstractThreadSafeFory implements ThreadSafeFory {
  private void applyRegistration(Consumer<Fory> registration) {
    registerCallback(
        (fory, checkBeforePublication) -> {
          checkBeforePublication.run();
          registration.accept(fory);
        });
  }

  @Override
  public void register(Class<?> clz) {
    applyRegistration(fory -> fory.register(clz));
  }

  @Override
  public void register(Class<?> cls, int id) {
    applyRegistration(fory -> fory.register(cls, id));
  }

  @Override
  public void register(Class<?> cls, String name) {
    applyRegistration(fory -> fory.register(cls, name));
  }

  @Override
  public void register(Class<?> cls, String namespace, String typeName) {
    applyRegistration(fory -> fory.register(cls, namespace, typeName));
  }

  @Override
  public void register(String className) {
    applyRegistration(fory -> fory.register(className));
  }

  @Override
  public void register(String className, int id) {
    applyRegistration(fory -> fory.register(className, id));
  }

  @Override
  public void register(String className, String name) {
    applyRegistration(fory -> fory.register(className, name));
  }

  @Override
  public void register(String className, String namespace, String typeName) {
    applyRegistration(fory -> fory.register(className, namespace, typeName));
  }

  @Override
  public void register(ForyModule module) {
    applyRegistration(fory -> fory.register(module));
  }

  public void registerUnion(
      Class<?> cls, int id, org.apache.fory.serializer.Serializer<?> serializer) {
    applyRegistration(fory -> fory.registerUnion(cls, id, serializer));
  }

  @Override
  public void registerUnion(
      Class<?> cls, String name, org.apache.fory.serializer.Serializer<?> serializer) {
    applyRegistration(fory -> fory.registerUnion(cls, name, serializer));
  }

  public void registerUnion(
      Class<?> cls,
      String namespace,
      String typeName,
      org.apache.fory.serializer.Serializer<?> serializer) {
    applyRegistration(fory -> fory.registerUnion(cls, namespace, typeName, serializer));
  }

  @Override
  public <T> void registerSerializer(Class<T> type, Class<? extends Serializer> serializerClass) {
    registerCallback(
        (fory, checkBeforePublication) ->
            fory.registerSerializer(type, serializerClass, checkBeforePublication));
  }

  @Override
  public void registerSerializer(Class<?> type, Serializer<?> serializer) {
    applyRegistration(fory -> fory.registerSerializer(type, serializer));
  }

  @Override
  public void registerSerializer(
      Class<?> type, Function<TypeResolver, Serializer<?>> serializerCreator) {
    registerCallback(
        (fory, checkBeforePublication) ->
            fory.registerSerializer(type, serializerCreator, checkBeforePublication));
  }

  @Override
  public <T> void registerSerializerAndType(
      Class<T> type, Class<? extends Serializer> serializerClass) {
    registerCallback(
        (fory, checkBeforePublication) ->
            fory.registerSerializerAndType(type, serializerClass, checkBeforePublication));
  }

  @Override
  public void registerSerializerAndType(Class<?> type, Serializer<?> serializer) {
    applyRegistration(fory -> fory.registerSerializerAndType(type, serializer));
  }

  @Override
  public void registerSerializerAndType(
      Class<?> type, Function<TypeResolver, Serializer<?>> serializerCreator) {
    registerCallback(
        (fory, checkBeforePublication) ->
            fory.registerSerializerAndType(type, serializerCreator, checkBeforePublication));
  }

  @Override
  public void registerSerializerFactory(SerializerFactory serializerFactory) {
    applyRegistration(fory -> fory.registerSerializerFactory(serializerFactory));
  }

  @Override
  public void setTypeChecker(TypeChecker typeChecker) {
    applyRegistration(fory -> fory.getTypeResolver().setTypeChecker(typeChecker));
  }

  @Override
  public void ensureSerializersCompiled() {
    execute(
        fory -> {
          fory.ensureSerializersCompiled();
          return null;
        });
  }
}
