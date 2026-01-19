---
title: Java Serialization Format
sidebar_position: 1
id: java_serialization_spec
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

## Spec overview

Apache Fory Java serialization is a dynamic binary format for Java object graphs. It supports shared references, circular
references, and polymorphism. The format is stream friendly: type metadata is written inline when needed, and there is no
end-of-stream metadata section.

Overall layout:

```
| fory header | object ref meta | object type meta | object value data |
```

All data is encoded in little endian byte order.

## Fory header

Java native serialization uses a one-byte bitmap header.

```
|     4 bits    | 1 bit | 1 bit | 1 bit  | 1 bit |
+---------------+-------+-------+--------+-------+
| reserved bits |  oob  | xlang | endian | null  |
```

- null flag: 1 when object is null, 0 otherwise. If an object is null, other bits are not set.
- endian flag: 1 when data is encoded by little endian, 0 for big endian.
- xlang flag: 1 when serialization uses xlang format, 0 when serialization uses Java native format.
- oob flag: 1 when passed `BufferCallback` is not null, 0 otherwise.

If xlang flag is set, a one-byte language ID follows the bitmap. In Java native mode (xlang flag unset), there is no
language byte and no meta start offset.

## Reference meta

Reference tracking uses the same flags as the xlang specification:

| Flag                | Byte Value | Description                                                                                              |
| ------------------- | ---------- | -------------------------------------------------------------------------------------------------------- |
| NULL FLAG           | `-3`       | Object is null. No further bytes are written for this object.                                            |
| REF FLAG            | `-2`       | Object was already serialized. Followed by unsigned varint32 reference ID.                               |
| NOT_NULL VALUE FLAG | `-1`       | Object is non-null but reference tracking is disabled for this type. Object data follows immediately.    |
| REF VALUE FLAG      | `0`        | Object is referencable and this is its first occurrence. Object data follows. Assigns next reference ID. |

When reference tracking is disabled globally or for a specific field/type, only `NULL FLAG` and `NOT_NULL VALUE FLAG`
are used.

## Type system and type IDs

Java native serialization uses the unified type ID layout:

```
full_type_id = (user_type_id << 8) | internal_type_id
```

- `internal_type_id` is a byte-sized ID describing the kind (enum/struct/ext, named variants, built-ins).
- `user_type_id` is the numeric registration ID (0-based) for user-defined enum/struct/ext types.

### Shared internal type IDs

Java shares the xlang internal IDs for the common primitive and core types. See the xlang spec for the full table:
`docs/specification/xlang_serialization_spec.md#internal-type-id-table`.

### Java native internal type IDs

Java native format adds Java-specific built-in type IDs starting at `Types.NAMED_EXT + 1`:

| Type ID | Name                       | Description                    |
| ------- | -------------------------- | ------------------------------ |
| 31      | VOID_ID                    | java.lang.Void                 |
| 32      | CHAR_ID                    | java.lang.Character            |
| 33      | PRIMITIVE_VOID_ID          | void                           |
| 34      | PRIMITIVE_BOOL_ID          | boolean                        |
| 35      | PRIMITIVE_INT8_ID          | byte                           |
| 36      | PRIMITIVE_CHAR_ID          | char                           |
| 37      | PRIMITIVE_INT16_ID         | short                          |
| 38      | PRIMITIVE_INT32_ID         | int                            |
| 39      | PRIMITIVE_FLOAT32_ID       | float                          |
| 40      | PRIMITIVE_INT64_ID         | long                           |
| 41      | PRIMITIVE_FLOAT64_ID       | double                         |
| 42      | PRIMITIVE_BOOLEAN_ARRAY_ID | boolean[]                      |
| 43      | PRIMITIVE_BYTE_ARRAY_ID    | byte[]                         |
| 44      | PRIMITIVE_CHAR_ARRAY_ID    | char[]                         |
| 45      | PRIMITIVE_SHORT_ARRAY_ID   | short[]                        |
| 46      | PRIMITIVE_INT_ARRAY_ID     | int[]                          |
| 47      | PRIMITIVE_FLOAT_ARRAY_ID   | float[]                        |
| 48      | PRIMITIVE_LONG_ARRAY_ID    | long[]                         |
| 49      | PRIMITIVE_DOUBLE_ARRAY_ID  | double[]                       |
| 50      | STRING_ARRAY_ID            | String[]                       |
| 51      | OBJECT_ARRAY_ID            | Object[]                       |
| 52      | ARRAYLIST_ID               | java.util.ArrayList            |
| 53      | HASHMAP_ID                 | java.util.HashMap              |
| 54      | HASHSET_ID                 | java.util.HashSet              |
| 55      | CLASS_ID                   | java.lang.Class                |
| 56      | EMPTY_OBJECT_ID            | empty object stub              |
| 57      | LAMBDA_STUB_ID             | lambda stub                    |
| 58      | JDK_PROXY_STUB_ID          | JDK proxy stub                 |
| 59      | REPLACE_STUB_ID            | writeReplace/readResolve stub  |
| 60      | NONEXISTENT_META_SHARED_ID | meta-shared unknown class stub |

### Named and unregistered types

If a type is not registered by numeric ID, Java native serialization encodes it as a named type:

- enum: `NAMED_ENUM`
- struct-like serializer: `NAMED_STRUCT` (or `NAMED_COMPATIBLE_STRUCT` when compatible mode is enabled)
- other serializers: `NAMED_EXT`

The namespace is the package name and the type name is the simple class name.

## Type meta encoding

Java native serialization writes a type ID for every value, then optionally writes additional metadata depending on the
internal type ID:

1. Write type ID using varuint32 small7 encoding.
2. For `NAMED_ENUM`, `NAMED_STRUCT`, `NAMED_EXT`, `NAMED_COMPATIBLE_STRUCT`:
   - If meta share is enabled: write shared class meta (streaming format).
   - Otherwise: write namespace and type name as meta strings.
3. For `COMPATIBLE_STRUCT`:
   - If meta share is enabled: write shared class meta (streaming format).
   - Otherwise: no extra meta (type ID is sufficient).
4. All other types: no extra meta.

### Shared class meta (streaming)

When meta share is enabled, Java uses a streaming shared meta protocol. It does not rely on a meta start offset in the
header.

```
| unsigned varint: index_marker | [class def bytes if new] |
```

- `index_marker` encodes both a reference flag and index: `index_marker = (index << 1) | flag`.
- If `flag == 1`, this is a reference to a previously written type, and no additional bytes follow.
- If `flag == 0`, this is a new type definition and the `class def bytes` are written inline.

The index is assigned sequentially in the order types are first encountered.

## ClassDef format (compatible mode)

ClassDef encodes schema evolution metadata for compatible structs. The layout is:

```
| 8 bytes header | optional varuint32 extra size | class meta bytes |
```

### Header

`50 bits hash + 1 bit compress flag + 1 bit has-fields-meta flag + 12 bits meta size` (lower bits on the right).

- meta size: lower 12 bits, with an extra varuint32 written when the size exceeds the 12-bit limit.
- compress flag: set when the payload is compressed.
- has-fields-meta: set when fields metadata is included.
- hash: 50-bit hash of the payload and flags.

### Class meta bytes

Class meta bytes encode a linearized inheritance chain and field metadata:

```
| num classes | class layer 0 | class layer 1 | ... |

class layer:
| num fields + registered flag | [type id if registered] | namespace | type name | field infos |
```

- num classes: number of class layers with serializable fields.
- registered flag: set when the class is registered by numeric ID.
- namespace and type name use the same meta string encoding as xlang (see below).
- field infos: for each field, the header encodes name encoding, name length/tag id, nullable flag, and ref tracking flag,
  followed by the field type ID and field name (or tag id).

### Meta string encoding

Meta string encoding (namespace/type name/field name) follows the same encoding algorithms as the xlang specification:
`docs/specification/xlang_serialization_spec.md#meta-string`.
