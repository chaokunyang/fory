---
title: FDL Syntax Reference
sidebar_position: 2
id: fdl_syntax
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

# FDL Syntax Reference

This document provides a complete reference for the Fory Definition Language (FDL) syntax.

## File Structure

An FDL file consists of:

1. Optional package declaration
2. Type definitions (enums and messages)

```fdl
// Optional package declaration
package com.example.models;

// Type definitions
enum Color @100 { ... }
message User @101 { ... }
message Order @102 { ... }
```

## Comments

FDL supports both single-line and block comments:

```fdl
// This is a single-line comment

/*
 * This is a block comment
 * that spans multiple lines
 */

message Example {
    string name = 1;  // Inline comment
}
```

## Package Declaration

The package declaration defines the namespace for all types in the file.

```fdl
package com.example.models;
```

**Rules:**

- Optional but recommended
- Must appear before any type definitions
- Only one package declaration per file
- Used for namespace-based type registration

**Language Mapping:**

| Language | Package Usage                     |
| -------- | --------------------------------- |
| Java     | Java package                      |
| Python   | Module name (dots to underscores) |
| Go       | Package name (last component)     |
| Rust     | Module name (dots to underscores) |
| C++      | Namespace (dots to `::`)          |

## Enum Definition

Enums define a set of named integer constants.

### Basic Syntax

```fdl
enum Status {
    PENDING = 0;
    ACTIVE = 1;
    COMPLETED = 2;
}
```

### With Type ID

```fdl
enum Status @100 {
    PENDING = 0;
    ACTIVE = 1;
    COMPLETED = 2;
}
```

**Grammar:**

```
enum_def     := 'enum' IDENTIFIER [type_id] '{' enum_values '}'
type_id      := '@' INTEGER
enum_values  := enum_value*
enum_value   := IDENTIFIER '=' INTEGER ';'
```

**Rules:**

- Enum names must be unique within the file
- Enum values must have explicit integer assignments
- Value integers must be unique within the enum
- Type ID (`@100`) is optional but recommended for cross-language use

**Example with All Features:**

```fdl
// HTTP status code categories
enum HttpCategory @200 {
    INFORMATIONAL = 1;
    SUCCESS = 2;
    REDIRECTION = 3;
    CLIENT_ERROR = 4;
    SERVER_ERROR = 5;
}
```

## Message Definition

Messages define structured data types with typed fields.

### Basic Syntax

```fdl
message Person {
    string name = 1;
    int32 age = 2;
}
```

### With Type ID

```fdl
message Person @101 {
    string name = 1;
    int32 age = 2;
}
```

**Grammar:**

```
message_def  := 'message' IDENTIFIER [type_id] '{' field_defs '}'
type_id      := '@' INTEGER
field_defs   := field_def*
```

## Field Definition

Fields define the properties of a message.

### Basic Syntax

```fdl
field_type field_name = field_number;
```

### With Modifiers

```fdl
optional ref repeated field_type field_name = field_number;
```

**Grammar:**

```
field_def    := [modifiers] field_type IDENTIFIER '=' INTEGER ';'
modifiers    := ['optional'] ['ref'] ['repeated']
field_type   := primitive_type | named_type | map_type
```

### Field Modifiers

#### `optional`

Marks the field as nullable:

```fdl
message User {
    string name = 1;           // Required, non-null
    optional string email = 2; // Nullable
}
```

**Generated Code:**

| Language | Non-optional       | Optional                                        |
| -------- | ------------------ | ----------------------------------------------- |
| Java     | `String name`      | `String email` with `@ForyField(nullable=true)` |
| Python   | `name: str`        | `name: Optional[str]`                           |
| Go       | `Name string`      | `Name *string`                                  |
| Rust     | `name: String`     | `name: Option<String>`                          |
| C++      | `std::string name` | `std::optional<std::string> name`               |

#### `ref`

Enables reference tracking for shared/circular references:

```fdl
message Node {
    string value = 1;
    ref Node parent = 2;     // Can point to shared object
    repeated ref Node children = 3;
}
```

**Use Cases:**

- Shared objects (same object referenced multiple times)
- Circular references (object graphs with cycles)
- Tree structures with parent pointers

**Generated Code:**

| Language | Without `ref`  | With `ref`                                        |
| -------- | -------------- | ------------------------------------------------- |
| Java     | `Node parent`  | `Node parent` with `@ForyField(trackingRef=true)` |
| Python   | `parent: Node` | `parent: Node` (runtime tracking)                 |
| Go       | `Parent Node`  | `Parent *Node` with `fory:"trackRef"`             |
| Rust     | `parent: Node` | `parent: Rc<Node>`                                |
| C++      | `Node parent`  | `std::shared_ptr<Node> parent`                    |

#### `repeated`

Marks the field as a list/array:

```fdl
message Document {
    repeated string tags = 1;
    repeated User authors = 2;
}
```

**Generated Code:**

| Language | Type                       |
| -------- | -------------------------- |
| Java     | `List<String>`             |
| Python   | `List[str]`                |
| Go       | `[]string`                 |
| Rust     | `Vec<String>`              |
| C++      | `std::vector<std::string>` |

### Combining Modifiers

Modifiers can be combined:

```fdl
message Example {
    optional repeated string tags = 1;  // Nullable list
    repeated ref Node nodes = 2;        // List of tracked references
    optional ref User owner = 3;        // Nullable tracked reference
}
```

**Order:** `optional` must come before `ref`, which must come before `repeated`.

## Type System

### Primitive Types

| Type        | Description                 | Size     |
| ----------- | --------------------------- | -------- |
| `bool`      | Boolean value               | 1 byte   |
| `int8`      | Signed 8-bit integer        | 1 byte   |
| `int16`     | Signed 16-bit integer       | 2 bytes  |
| `int32`     | Signed 32-bit integer       | 4 bytes  |
| `int64`     | Signed 64-bit integer       | 8 bytes  |
| `float32`   | 32-bit floating point       | 4 bytes  |
| `float64`   | 64-bit floating point       | 8 bytes  |
| `string`    | UTF-8 string                | Variable |
| `bytes`     | Binary data                 | Variable |
| `date`      | Calendar date               | Variable |
| `timestamp` | Date and time with timezone | Variable |

See [Type System](type-system.md) for complete type mappings.

### Named Types

Reference other messages or enums by name:

```fdl
enum Status { ... }
message User { ... }

message Order {
    User customer = 1;    // Reference to User message
    Status status = 2;    // Reference to Status enum
}
```

### Map Types

Maps with typed keys and values:

```fdl
message Config {
    map<string, string> properties = 1;
    map<string, int32> counts = 2;
    map<int32, User> users = 3;
}
```

**Syntax:** `map<KeyType, ValueType>`

**Restrictions:**

- Key type should be a primitive type (typically `string` or integer types)
- Value type can be any type including messages

## Field Numbers

Each field must have a unique positive integer identifier:

```fdl
message Example {
    string first = 1;
    string second = 2;
    string third = 3;
}
```

**Rules:**

- Must be unique within a message
- Must be positive integers
- Used for field ordering and identification
- Gaps in numbering are allowed (useful for deprecating fields)

**Best Practices:**

- Use sequential numbers starting from 1
- Reserve number ranges for different categories
- Never reuse numbers for different fields (even after deletion)

## Type IDs

Type IDs enable efficient cross-language serialization:

```fdl
enum Color @100 { ... }
message User @101 { ... }
message Order @102 { ... }
```

### With Type ID (Recommended)

```fdl
message User @101 { ... }
```

- Serialized as compact integer
- Fast lookup during deserialization
- Must be globally unique across all types
- Recommended for production use

### Without Type ID

```fdl
message Config { ... }
```

- Registered using namespace + name
- More flexible for development
- Slightly larger serialized size
- Uses package as namespace: `"package.Config"`

### ID Assignment Strategy

```fdl
// Enums: 100-199
enum Status @100 { ... }
enum Priority @101 { ... }

// User domain: 200-299
message User @200 { ... }
message UserProfile @201 { ... }

// Order domain: 300-399
message Order @300 { ... }
message OrderItem @301 { ... }
```

## Complete Example

```fdl
// E-commerce domain model
package com.shop.models;

// Enums with type IDs
enum OrderStatus @100 {
    PENDING = 0;
    CONFIRMED = 1;
    SHIPPED = 2;
    DELIVERED = 3;
    CANCELLED = 4;
}

enum PaymentMethod @101 {
    CREDIT_CARD = 0;
    DEBIT_CARD = 1;
    PAYPAL = 2;
    BANK_TRANSFER = 3;
}

// Messages with type IDs
message Address @200 {
    string street = 1;
    string city = 2;
    string state = 3;
    string country = 4;
    string postal_code = 5;
}

message Customer @201 {
    string id = 1;
    string name = 2;
    optional string email = 3;
    optional string phone = 4;
    optional Address billing_address = 5;
    optional Address shipping_address = 6;
}

message Product @202 {
    string sku = 1;
    string name = 2;
    string description = 3;
    float64 price = 4;
    int32 stock = 5;
    repeated string categories = 6;
    map<string, string> attributes = 7;
}

message OrderItem @203 {
    ref Product product = 1;  // Track reference to avoid duplication
    int32 quantity = 2;
    float64 unit_price = 3;
}

message Order @204 {
    string id = 1;
    ref Customer customer = 2;
    repeated OrderItem items = 3;
    OrderStatus status = 4;
    PaymentMethod payment_method = 5;
    float64 total = 6;
    optional string notes = 7;
    timestamp created_at = 8;
    optional timestamp shipped_at = 9;
}

// Config without type ID (uses namespace registration)
message ShopConfig {
    string store_name = 1;
    string currency = 2;
    float64 tax_rate = 3;
    repeated string supported_countries = 4;
}
```

## Grammar Summary

```
file         := [package_decl] type_def*

package_decl := 'package' package_name ';'
package_name := IDENTIFIER ('.' IDENTIFIER)*

type_def     := enum_def | message_def

enum_def     := 'enum' IDENTIFIER [type_id] '{' enum_value* '}'
enum_value   := IDENTIFIER '=' INTEGER ';'

message_def  := 'message' IDENTIFIER [type_id] '{' field_def* '}'
field_def    := [modifiers] field_type IDENTIFIER '=' INTEGER ';'

modifiers    := ['optional'] ['ref'] ['repeated']

field_type   := primitive_type | named_type | map_type
primitive_type := 'bool' | 'int8' | 'int16' | 'int32' | 'int64'
               | 'float32' | 'float64' | 'string' | 'bytes'
               | 'date' | 'timestamp'
named_type   := IDENTIFIER
map_type     := 'map' '<' field_type ',' field_type '>'

type_id      := '@' INTEGER

IDENTIFIER   := [a-zA-Z_][a-zA-Z0-9_]*
INTEGER      := '-'? [0-9]+
```
