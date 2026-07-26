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

## When to Use a Manual Serializer

- The target has private or immutable state.
- The target must enforce construction invariants.
- The target needs a specialized compact encoding.
- The target needs a tuned allocation or validation path.
- An external enum is not exhaustively switchable.
- An external union cannot represent `UnknownCase`.

## Implementing `Serializer`

```swift
import Foundation
import Fory

public enum UUIDSerializer: Serializer {
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

## Register and Use

```swift
let fory = Fory()
try fory.register(UUIDSerializer.self, id: 300)

let input = UUID()
let data = try fory.serialize(input, with: UUIDSerializer.self)
let output = try fory.deserialize(data, with: UUIDSerializer.self)

assert(input == output)
```

## Use a Manual Serializer in a Field

```swift
@ForyStruct
struct Request {
    @ForyField(with: UUIDSerializer.self)
    var requestID: UUID
}
```

Recursive carrier selection uses the same field syntax:

```swift
@ListField(element: .with(UUIDSerializer.self))
var requestIDs: [UUID]
```

## Manual Serializer Rules

A manual serializer that replaces a noncanonical body uses `.ext`. Numeric
registration produces EXT and name registration produces NAMED_EXT.

Do not report `.structType`, `.enumType`, or `.typedUnion` from a manual
serializer. Those categories are owned by `@ForyStruct`, `@ForyEnum`, and
`@ForyUnion`.

The serializer declaration is static behavior and is never instantiated.

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
