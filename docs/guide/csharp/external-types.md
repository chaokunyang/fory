---
title: External Types
sidebar_position: 6
id: external_types
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

External-type serialization generates a serializer for a class, struct, or
enum that cannot carry its own Fory annotation. The target can come from a
referenced package or from generated or otherwise unmodifiable source. A local
declaration supplies the schema while Fory reads and writes the target value
directly.

## Class and Struct Targets

Suppose another package defines this class:

```csharp
namespace ThirdParty;

public sealed class User
{
    public string Name { get; set; } = string.Empty;

    public int Age { get; set; }
}
```

Declare its external structural serializer in your project:

```csharp
using Apache.Fory;
using S = Apache.Fory.Schema.Types;

[ForyStruct(Target = typeof(ThirdParty.User))]
internal abstract class UserSerializer
{
    [ForyField(1)]
    public abstract string Name { get; }

    [ForyField(2, Type = typeof(S.Int32))]
    public abstract int Age { get; }

    [ForyField(
        Ignore = true,
        TargetDeclaringType = typeof(ThirdParty.User),
        TargetMemberName = "<Name>k__BackingField",
        TargetMemberKind = ForyTargetMemberKind.Field)]
    public abstract string NameStorage { get; }

    [ForyField(
        Ignore = true,
        TargetDeclaringType = typeof(ThirdParty.User),
        TargetMemberName = "<Age>k__BackingField",
        TargetMemberKind = ForyTargetMemberKind.Field)]
    public abstract int AgeStorage { get; }
}
```

`Name` and `Age` define wire fields. Their properties do not themselves occupy
object storage, so the two ignored mappings identify the exact backing fields
used for graph-memory accounting. A public target field is discovered
automatically and does not need a separate storage mapping.

The declaration is generator input only. Do not instantiate or register
`UserSerializer`.

The same form supports an external struct:

```csharp
[ForyStruct(Target = typeof(ThirdParty.Point))]
internal abstract class PointSerializer
{
    [ForyField(1)]
    public abstract int X { get; }

    [ForyField(2)]
    public abstract int Y { get; }
}
```

## Exact Member Mappings

By default, a declaration property binds a visible target field or property
with the same case-sensitive name. Use the `TargetDeclaringType`,
`TargetMemberName`, and `TargetMemberKind` options to bind a renamed or
inaccessible member:

```csharp
[ForyStruct(Target = typeof(ThirdParty.Account))]
internal abstract class AccountSerializer
{
    [ForyField(
        1,
        TargetDeclaringType = typeof(ThirdParty.Account),
        TargetMemberName = "_identifier",
        TargetMemberKind = ForyTargetMemberKind.Field)]
    public abstract long Id { get; }

    [ForyField(
        2,
        TargetDeclaringType = typeof(ThirdParty.Account),
        TargetMemberName = "Secret",
        TargetMemberKind = ForyTargetMemberKind.Property)]
    public abstract string Credential { get; }

    [ForyField(
        Ignore = true,
        TargetDeclaringType = typeof(ThirdParty.Account),
        TargetMemberName = "<Secret>k__BackingField",
        TargetMemberKind = ForyTargetMemberKind.Field)]
    public abstract string CredentialStorage { get; }
}
```

The declaration property name is the logical schema name. The target options
identify the exact package member:

- A field mapping is both a wire member and a physical storage field.
- A property mapping is a wire member only. Map its backing field separately
  when it has storage.
- An ignored mapping must identify an exact field. It contributes storage but
  never enters the wire schema or generated reads and writes.
- Every non-public target field that should be included in the shallow
  graph-memory estimate must be listed exactly once. Static fields are not
  instance storage.

Fory does not inspect private members in the referenced package. An exact
private mapping is an application-owned package ABI declaration. Pin and test
the package version together with the declaration. If a mapped private field
or property changes, the generated exact accessor fails with the CLR's
missing-field or missing-method error; Fory does not fall back to reflection or
another member. An ignored private field has no runtime accessor, so validate
that storage declaration against the pinned package build.

## Third-Party Base Classes

An ordinary `[ForyStruct]` class can inherit an unmodifiable third-party class.
Declare one external hierarchy provider for its immediate third-party base and
set `BaseOnly = true`.

For example, assume a package defines:

```csharp
namespace ThirdParty;

public class VendorBase
{
    private long _identifier;
    private string Secret { get; set; } = string.Empty;
    private readonly int _cache;
}

public class VendorRecord : VendorBase
{
    public int Revision;
}
```

A shared schema assembly can declare the complete third-party prefix:

```csharp
using Apache.Fory;

[ForyStruct(Target = typeof(ThirdParty.VendorRecord), BaseOnly = true)]
public abstract class VendorRecordHierarchy
{
    [ForyField(
        1,
        TargetDeclaringType = typeof(ThirdParty.VendorBase),
        TargetMemberName = "_identifier",
        TargetMemberKind = ForyTargetMemberKind.Field)]
    public abstract long Identifier { get; }

    [ForyField(
        2,
        TargetDeclaringType = typeof(ThirdParty.VendorBase),
        TargetMemberName = "Secret",
        TargetMemberKind = ForyTargetMemberKind.Property)]
    public abstract string Secret { get; }

    [ForyField(
        Ignore = true,
        TargetDeclaringType = typeof(ThirdParty.VendorBase),
        TargetMemberName = "<Secret>k__BackingField",
        TargetMemberKind = ForyTargetMemberKind.Field)]
    public abstract string SecretStorage { get; }

    [ForyField(
        Ignore = true,
        TargetDeclaringType = typeof(ThirdParty.VendorBase),
        TargetMemberName = "_cache",
        TargetMemberKind = ForyTargetMemberKind.Field)]
    public abstract int CacheStorage { get; }

    [ForyField(3)]
    public abstract int Revision { get; }
}
```

The application class then carries its own direct annotation:

```csharp
[ForyStruct]
public sealed class LocalRecord : ThirdParty.VendorRecord
{
    [ForyField(4)]
    public string Label { get; set; } = string.Empty;
}
```

Register `LocalRecord`. A `BaseOnly` declaration publishes only the generated
hierarchy API used by derived serializers; it does not generate a standalone
serializer factory or registration for `ThirdParty.VendorRecord`.

The provider declaration may list exact fields and properties from every
third-party ancestor of its target. Fory does not walk private package metadata
or reconstruct a parent budget in the child compilation. Put a public
`BaseOnly` declaration in a shared referenced assembly when several consumer
assemblies derive from the same third-party base.

A `BaseOnly` target may be abstract or lack a parameterless constructor as
long as each concrete annotated child has a legal parameterless construction
path.

First-party bases use direct `[ForyStruct]` annotations instead. See
[Class Inheritance](basic-serialization.md#class-inheritance).

## Declaration and Target Requirements

An external structural declaration must be a non-generic abstract class
containing only abstract get-only declaration properties. Each wire property:

- binds a visible member by the same name or supplies an exact target mapping;
- has the target member's CLR type and generic shape;
- has matching explicit nullability when the target metadata provides it; and
- may use the standard `ForyField` ID and schema descriptor options.

When target metadata is nullable-oblivious, the declaration selects schema
nullability. Members not declared as wire fields keep their constructor or
derived behavior after deserialization.

A standalone external target must be an accessible concrete class or struct
with a legal parameterless construction path and writable declared wire state.
Constructor-bound, factory-only, readonly, init-only, converted, or
custom-wire targets require a [manual serializer](manual-serializers.md).

Closed generic targets such as `ThirdParty.Box<string>` are supported. On
.NET 8, a private wire member whose declaring type or signature is generic
requires a manual serializer. Visible generic members and exact ignored field
mappings remain supported. Open generic targets are not supported.

## Enum Targets

Use an empty static serializer declaration for an enum:

```csharp
[ForyEnum(Target = typeof(ThirdParty.Status))]
internal static class StatusSerializer
{
}
```

The target enum's numeric values are authoritative. Do not copy its constants
into the declaration. Every serialized numeric value must fit in the unsigned
32-bit Fory enum tag range. Cross-language peers must use matching explicit
enum tags, such as Java's `@ForyEnumId`.

## Registration and Root Values

Register the target through the normal APIs:

```csharp
Fory fory = Fory.Builder().Build();
fory.Register<ThirdParty.User>(100);
fory.Register<ThirdParty.Status>("example.Status");

ThirdParty.User user = new() { Name = "Alice", Age = 30 };
byte[] bytes = fory.Serialize(user);
ThirdParty.User decoded = fory.Deserialize<ThirdParty.User>(bytes);
```

The split namespace/name overloads and `ThreadSafeFory` registration work the
same way. There is no separate external-type registration or root API.

## Fields and Carriers

Use the target type directly in generated models:

```csharp
[ForyStruct]
public sealed class Group
{
    public ThirdParty.User Owner { get; set; } = new();

    public List<ThirdParty.User> Users { get; set; } = [];

    public Dictionary<string, ThirdParty.User> UsersByName { get; set; } = [];
}
```

Root carrier composition also uses the ordinary typed API:

```csharp
ThirdParty.User user = new() { Name = "Alice", Age = 30 };
byte[] bytes = fory.Serialize(new List<ThirdParty.User> { user });

List<ThirdParty.User> decoded =
    fory.Deserialize<List<ThirdParty.User>>(bytes);
```

External children are supported through the concrete carrier types already
supported by the C# runtime:

- `Nullable<T>` for external structs and one-dimensional `T[]`;
- `List<T>`, `LinkedList<T>`, `Queue<T>`, and `Stack<T>`;
- `HashSet<T>`, `SortedSet<T>`, and `ImmutableHashSet<T>`;
- `Dictionary<TKey, TValue>`, `SortedDictionary<TKey, TValue>`,
  `SortedList<TKey, TValue>`, `ConcurrentDictionary<TKey, TValue>`, and
  `NullableKeyDictionary<TKey, TValue>`.

Targets can appear as map keys or values and inside recursively nested
carriers. Collection interface types are not supported as generated fields or
typed roots.

## Schema Evolution

Set `Evolving` on the serializer declaration:

```csharp
[ForyStruct(
    Target = typeof(ThirdParty.User),
    Evolving = false)]
internal abstract class UserSerializer
{
    public abstract string Name { get; }
}
```

Field IDs, field names, schema descriptors, and `Evolving` come from the
declaration. Compatible and schema-consistent modes otherwise behave exactly
as they do for an ordinary generated C# type.

## Dynamic Values and References

After registering the target, it can appear in `object`-based dynamic roots,
fields, collections, and maps. Register every concrete target that can appear
dynamically.

Mutable external classes retain shared-reference and cycle support when
reference tracking is enabled. External structs remain inline values.

This feature does not add arbitrary interface or base-class polymorphism.

## Related Topics

- [Schema Metadata](schema-metadata.md)
- [Type Registration](type-registration.md)
- [Manual Serializers](manual-serializers.md)
- [References](references.md)
- [Schema Evolution](schema-evolution.md)
