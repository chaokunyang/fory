---
title: Static Struct Serializers
sidebar_position: 15
id: static_struct_serializers
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

Build-time static serializers are generated for Java classes annotated with `@ForyStruct`. They
provide a non-JIT serializer path for ordinary JVM runtimes using `ForyBuilder#withCodegen(false)`
and for Android, where runtime bytecode generation is disabled.

## Enabling The Processor

Add the annotation processor to the Java compile configuration. The generated code depends only on
`fory-core` at runtime. `fory-core` itself does not depend on the processor; applications opt in by
placing `fory-annotation-processor` on their build's annotation-processor path.

```xml
<annotationProcessorPaths>
  <path>
    <groupId>org.apache.fory</groupId>
    <artifactId>fory-annotation-processor</artifactId>
    <version>${fory.version}</version>
  </path>
</annotationProcessorPaths>
```

Annotate serializable structs with `@ForyStruct`:

```java
import org.apache.fory.annotation.ForyStruct;

@ForyStruct
public class Order {
  public long id;
  public String note;

  public Order() {}
}
```

The processor emits a public top-level serializer in the same package. For `Order`, the generated
class is `Order__ForyStaticSerializer__`. For a static member type `Outer.Inner`, the generated
top-level class is `Outer$Inner__ForyStaticSerializer__`; the processor does not modify the
enclosing class and does not generate inner serializer classes.

## Runtime Selection

Static serializers are used when available on:

- ordinary JVMs with `ForyBuilder#withCodegen(false)`.
- Android runtimes, because runtime code generation is disabled there.
- compatible-mode meta-share reads when a generated static serializer exists for the target struct.

Ordinary JVM `codegen=true` keeps the runtime-generated serializer precedence. Static serializer
lookup is deterministic by generated class name and does not scan the classpath.

GraalVM native image does not use annotation-processor-generated static serializer classes. Native
image builds use the GraalVM registry path: matching local and remote `TypeDef` hashes use the
existing meta-shared generated serializer, and mismatched hashes use a build-time generated
read-only compatible serializer cached by local Java class. At runtime, the compatible serializer
constructor receives the current remote `TypeDef` and derives the remote-field layout from that
metadata.

## Field Access Rules

The processor never falls back to reflection for private serialized fields.

- Public, protected, and package-private fields can be accessed directly when Java package access
  allows the generated same-package serializer to use them.
- Private serialized fields must have accessible non-private getter and setter methods, or be
  excluded with `transient` or Fory `@Ignore`.
- Public, protected, and package-private getter/setter methods are accepted when they are accessible
  from the generated serializer package.
- Final fields are rejected for normal classes because generated read and copy methods must assign
  them. Use records for constructor-based immutable structs.

For records, generated serializers use public record accessors and construct values through the
canonical record constructor. Ignored record components are skipped by serialization and copy, and
their constructor arguments use Java default values during generated read/copy.

## Generated Metadata

Generated serializers expose descriptor metadata through
`StaticGeneratedStructSerializer#getDescriptors()`. The descriptor list is a static immutable list
owned by the generated serializer. Each descriptor carries:

- field name and declaring-class identity.
- `@ForyField` id, nullable, reference-tracking, and dynamic-field semantics.
- a `TypeRef` tree with nested type arguments, array component type, and `TypeExtMeta` for nested
  `TYPE_USE` metadata such as `@Ref`, `@UInt8Type`, and `@UInt16Type`.

The runtime uses these descriptors to build schema metadata instead of reading nested
`Field#getAnnotatedType()` information at runtime. This keeps Android and JVM wire protocol unified
while avoiding Android reflection gaps.

## Compatible Reads

Generated static serializers include normal read/write/copy methods and a compatible read method.
The compatible path consumes remote schema metadata, matches remote fields to local fields, skips
unknown fields, and preserves Java defaults for missing fields.

Field matching assigns dense generated matched ids for the generated branch table. Those ids are
local dispatch ids only; they are not `@ForyField.id` values and are not wire ids. Remote field order
still controls payload consumption.

The same-schema fast path is used only when the remote schema hash equals the local schema hash and
the struct has no nested struct fields that require compatible layouts.
