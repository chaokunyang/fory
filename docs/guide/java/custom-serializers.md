---
title: Custom Serializers
sidebar_position: 4
id: custom_serializers
license: |
  Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements.  See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License.  You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
---

This page covers the current Java custom serializer API.

## Constructor Inputs

Custom serializers should not retain `Fory`.

- Use `Config` when the serializer only depends on immutable configuration and can be shared.
- Use `TypeResolver` when the serializer needs type metadata, generics, or nested dynamic dispatch.
- If a serializer retains `TypeResolver`, it is not thread-safe and should keep the default `threadSafe() == false`.

## Basic Serializer

Use `WriteContext` and `ReadContext` for runtime state. Get the buffer into a local variable first
when you perform multiple reads or writes.

```java
import org.apache.fory.config.Config;
import org.apache.fory.context.ReadContext;
import org.apache.fory.context.WriteContext;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.serializer.Serializer;

public final class FooSerializer extends Serializer<Foo> {
  public FooSerializer(Config config) {
    super(config, Foo.class);
  }

  @Override
  public void write(WriteContext writeContext, Foo value) {
    MemoryBuffer buffer = writeContext.getBuffer();
    buffer.writeInt64(value.f1);
    writeContext.writeString(value.f2);
  }

  @Override
  public Foo read(ReadContext readContext) {
    MemoryBuffer buffer = readContext.getBuffer();
    Foo foo = new Foo();
    foo.f1 = buffer.readInt64();
    foo.f2 = readContext.readString(buffer);
    return foo;
  }

  @Override
  public boolean threadSafe() {
    return true;
  }
}
```

Register it with a `Config`-based constructor when the serializer is shareable:

```java
Fory fory = Fory.builder().build();
fory.registerSerializer(Foo.class, new FooSerializer(fory.getConfig()));
```

## Nested Objects

If your serializer needs to write or read nested objects, use the context helpers instead of
retaining `Fory`:

```java
import org.apache.fory.context.ReadContext;
import org.apache.fory.context.WriteContext;
import org.apache.fory.resolver.TypeResolver;
import org.apache.fory.serializer.Serializer;

public final class EnvelopeSerializer extends Serializer<Envelope> {
  public EnvelopeSerializer(TypeResolver typeResolver) {
    super(typeResolver, Envelope.class);
  }

  @Override
  public void write(WriteContext writeContext, Envelope value) {
    writeContext.writeRef(value.header);
    writeContext.writeRef(value.payload);
  }

  @Override
  public Envelope read(ReadContext readContext) {
    Envelope envelope = new Envelope();
    envelope.header = (Header) readContext.readRef();
    envelope.payload = readContext.readRef();
    return envelope;
  }
}
```

This serializer is not thread-safe because it retains `TypeResolver`.

## Collection Serializers

For Java collections, extend `CollectionSerializer` or `CollectionLikeSerializer`.

- Use `CollectionSerializer` for real `Collection` implementations.
- Use `CollectionLikeSerializer` for collection-shaped types that do not implement `Collection`.
- Keep `supportCodegenHook == true` when the collection can use the standard element codegen path.
- Set `supportCodegenHook == false` only when you need to fully control element IO.

Example:

```java
import java.util.ArrayList;
import java.util.Collection;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.resolver.TypeResolver;
import org.apache.fory.serializer.collection.CollectionSerializer;

public final class CustomCollectionSerializer<T extends Collection<?>>
    extends CollectionSerializer<T> {
  public CustomCollectionSerializer(TypeResolver typeResolver, Class<T> type) {
    super(typeResolver, type, true);
  }

  @Override
  public Collection onCollectionWrite(MemoryBuffer buffer, T value) {
    buffer.writeVarUint32Small7(value.size());
    return value;
  }

  @Override
  public T onCollectionRead(Collection collection) {
    return (T) collection;
  }

  @Override
  public Collection newCollection(MemoryBuffer buffer) {
    int numElements = buffer.readVarUint32Small7();
    setNumElements(numElements);
    return new ArrayList(numElements);
  }
}
```

## Map Serializers

For Java maps, extend `MapSerializer` or `MapLikeSerializer`.

```java
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.fory.memory.MemoryBuffer;
import org.apache.fory.resolver.TypeResolver;
import org.apache.fory.serializer.collection.MapSerializer;

public final class CustomMapSerializer<T extends Map<?, ?>> extends MapSerializer<T> {
  public CustomMapSerializer(TypeResolver typeResolver, Class<T> type) {
    super(typeResolver, type, true);
  }

  @Override
  public Map onMapWrite(MemoryBuffer buffer, T value) {
    buffer.writeVarUint32Small7(value.size());
    return value;
  }

  @Override
  public T onMapRead(Map map) {
    return (T) map;
  }

  @Override
  public Map newMap(MemoryBuffer buffer) {
    int numElements = buffer.readVarUint32Small7();
    setNumElements(numElements);
    return new LinkedHashMap(numElements);
  }
}
```

## Registration

```java
Fory fory = Fory.builder().build();

fory.registerSerializer(Foo.class, new FooSerializer(fory.getConfig()));
fory.registerSerializer(
    CustomMap.class, new CustomMapSerializer<>(fory.getTypeResolver(), CustomMap.class));
fory.registerSerializer(
    CustomCollection.class,
    new CustomCollectionSerializer<>(fory.getTypeResolver(), CustomCollection.class));
```

If you want Fory to construct the serializer lazily, register a factory:

```java
fory.registerSerializer(
    CustomMap.class, resolver -> new CustomMapSerializer<>(resolver, CustomMap.class));
```

## Thread Safety

Override `threadSafe()` only when all retained fields are immutable and the serializer does not
retain `TypeResolver`, `RefResolver`, `Fory`, or other runtime state.

In practice:

- `Config`-only serializers are often thread-safe.
- `TypeResolver`-based serializers are not thread-safe.
- Operation state belongs in `WriteContext`, `ReadContext`, and `CopyContext`, not in serializer
  fields.
