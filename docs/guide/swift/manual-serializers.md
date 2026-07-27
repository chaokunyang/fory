---
title: Manual Serializers
sidebar_position: 10
id: manual_serializers
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

Use a manual serializer when a target needs a customized wire body or cannot
meet the direct-access requirements of an
[external structural serializer](external-types.md).

A manual serializer is not limited to external types:

- A target that itself conforms to `Serializer` with `Target == Self` selects
  that implementation implicitly everywhere.
- A separate serializer whose `Target` is another type must be selected
  explicitly everywhere it is needed.

This ownership rule is the same for roots, generated fields, optionals, arrays,
sets, and dictionaries. Registration does not change static serializer
selection.

## When to Use a Manual Serializer

- The target has private or immutable state.
- The target must enforce construction invariants.
- The target needs a specialized compact encoding.
- The target needs a tuned allocation or validation path.
- An external enum is not exhaustively switchable.
- An external union cannot represent `UnknownCase`.

## User-Owned Targets

When you own the target, implement `Serializer` on the target itself and set
`Target` to the same type:

```swift
import Fory

struct AccountID: Serializer, Equatable {
    typealias Target = AccountID

    let rawValue: UInt64

    static var staticTypeId: TypeId {
        .ext
    }

    static func writeData(
        _ value: AccountID,
        _ context: WriteContext
    ) throws {
        try UInt64.writeData(value.rawValue, context)
    }

    static func readData(
        _ context: ReadContext
    ) throws -> AccountID {
        AccountID(rawValue: try UInt64.readData(context))
    }
}
```

Register and use the type through the ordinary root APIs:

```swift
let fory = Fory()
try fory.register(AccountID.self, id: 300)

let input = AccountID(rawValue: 42)
let data = try fory.serialize(input)
let output: AccountID = try fory.deserialize(data)

assert(input == output)
```

No `with:` argument is needed because `AccountID.Target == AccountID`.

## One Global Serializer for an External Type

Swift permits an application to make an external type self-provided through a
retroactive conformance:

```swift
import Foundation
import Fory

extension UUID: @retroactive Serializer {
    public typealias Target = UUID

    public static var staticTypeId: TypeId {
        .ext
    }

    public static func defaultValue(
        _ context: ReadContext
    ) throws -> UUID {
        _ = context
        return UUID(
            uuidString: "00000000-0000-0000-0000-000000000000"
        )!
    }

    public static func writeData(
        _ value: UUID,
        _ context: WriteContext
    ) throws {
        try String.writeData(value.uuidString, context)
    }

    public static func readData(
        _ context: ReadContext
    ) throws -> UUID {
        let raw = try String.readData(context)
        guard let uuid = UUID(uuidString: raw) else {
            throw ForyError.invalidData("invalid UUID string: \(raw)")
        }
        return uuid
    }
}
```

Register the external type itself:

```swift
try fory.register(UUID.self, id: 300)

let input = UUID()
let data = try fory.serialize(input)
let output: UUID = try fory.deserialize(data)
```

Because `UUID.Target == UUID`, unannotated generated fields and ordinary
carriers also select this implementation:

```swift
@ForyStruct
struct Request {
    var requestID: UUID
}

let input = [UUID(), UUID()]
let data = try fory.serialize(input)
let output: [UUID] = try fory.deserialize(data)
```

A retroactive conformance is process-global. Only one conformance for one
`(Target, Protocol)` pair can safely exist. `@retroactive` acknowledges
Swift's ownership warning but does not make duplicate conformances safe. Use
this form only when the application intentionally owns the single global
binding. Public libraries should generally provide a separate serializer
instead.

## Separate Serializers

Use a separate serializer when a public library must not claim a process-global
conformance or when an application needs multiple or alternative
implementations. The target may be external or user-owned:

```swift
import Foundation
import Fory

public enum UUIDStringSerializer: Serializer {
    public typealias Target = UUID

    public static var staticTypeId: TypeId {
        .ext
    }

    public static func defaultValue(
        _ context: ReadContext
    ) throws -> UUID {
        _ = context
        return UUID(
            uuidString: "00000000-0000-0000-0000-000000000000"
        )!
    }

    public static func writeData(
        _ value: UUID,
        _ context: WriteContext
    ) throws {
        try String.writeData(value.uuidString, context)
    }

    public static func readData(
        _ context: ReadContext
    ) throws -> UUID {
        let raw = try String.readData(context)
        guard let uuid = UUID(uuidString: raw) else {
            throw ForyError.invalidData("invalid UUID string: \(raw)")
        }
        return uuid
    }
}
```

Register the separate serializer and select it explicitly at the root:

```swift
let fory = Fory()
try fory.register(UUIDStringSerializer.self, id: 300)

let input = UUID()
let data = try fory.serialize(input, with: UUIDStringSerializer.self)
let output = try fory.deserialize(data, with: UUIDStringSerializer.self)

assert(input == output)
```

Another declaration, such as `UUIDBytesSerializer`, may target the same type
with a different body. Swift cannot reverse-infer one of these serializers from
`S.Target == UUID`. Registration makes the selected serializer available for
type identity and dynamic dispatch; it never makes a separate serializer the
implicit static choice. Register only one implementation for the target on a
given `Fory` instance.

The direct `Any` and `AnyObject` root conveniences remain dynamic operations.
A concrete non-self-provided value can enter registered dynamic lookup through
the `Any` overload, but that does not statically select a separate serializer
and is not a replacement for `with:`.

## Fields and Carriers

A field whose declared type is its own serializer provider needs no selector:

```swift
@ForyStruct
struct Request {
    var accountID: AccountID
}
```

A separate serializer must be selected explicitly:

```swift
@ForyStruct
struct ExternalRequest {
    @ForyField(with: UUIDStringSerializer.self)
    var requestID: UUID
}
```

Ordinary carriers containing self-provided targets also select their children
directly. This includes intentional retroactive conformances:

```swift
let accountIDs = [
    AccountID(rawValue: 1),
    AccountID(rawValue: 2),
]
let data = try fory.serialize(accountIDs)
let output: [AccountID] = try fory.deserialize(data)
```

For a separately provided child, recursive carrier selection names that
serializer:

```swift
@ListField(element: .with(UUIDStringSerializer.self))
var requestIDs: [UUID]
```

At a root, use the matching carrier serializer:

```swift
let data = try fory.serialize(
    requestIDs,
    with: ArraySerializer<UUIDStringSerializer>.self
)
```

## Manual Serializer Rules

A manual serializer uses `.ext` for its noncanonical body whether the target
implements `Serializer` directly or uses a separate serializer declaration.
Numeric registration produces EXT and name registration produces NAMED_EXT.

Do not report `.structType`, `.enumType`, or `.typedUnion` from a manual
serializer. Those categories are owned by `@ForyStruct`, `@ForyEnum`, and
`@ForyUnion`.

Serialization behavior is static. Fory does not instantiate a separate
serializer object.

`writeData` and `readData` process only the target body. Do not call a root
`serialize` or `deserialize` method from either operation.

## Defaults

`defaultValue(_:)` is fallible and receives the active read context. Implement
it only when the target has a valid value for a null or missing field.

If creating a default allocates memory, reserve the target's graph memory
before allocation.

## Allocation Safety

Before allocating from input-controlled data:

1. validate encoded byte counts;
2. validate collection or object limits;
3. validate arithmetic for overflow;
4. reserve graph memory for the final owner;
5. allocate the final target once.

Do not allocate a temporary model and convert it into the target.

## Manual Class Serializers

A manual serializer for a cyclic class must override the complete-value `read`
operation and integrate with Fory's reference APIs so repeated references
resolve to the final target object. Do not deserialize through a temporary
serializer, builder, or conversion wrapper.

## Container Targets

A manual serializer may target an entire optional, array, set, or dictionary
when an application wants one opaque EXT body:

```swift
enum UserArraySerializer: Serializer {
    typealias Target = [ThirdParty.User]
    // One application-defined EXT body.
}
```

This is different from `ArraySerializer<UserSerializer>`, which preserves the
normal LIST schema and recursively selects `UserSerializer`. Static field and
root selection remains explicit, so registering the opaque serializer does not
change ordinary array serialization.

A target that already has a seeded canonical dynamic identity, such as an exact
primitive array, cannot be claimed by another serializer.
