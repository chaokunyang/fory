# Apache Fory™ C++

Apache Fory™ is a blazingly-fast multi-language serialization framework powered by just-in-time compilation and zero-copy.

## Environment

- Bazel version: 8.2.1

## Build Apache Fory™ C++

```bash
# Build all projects
bazel build //:all
# Run all tests
bazel test //:all
# Run serialization tests
bazel test //cpp/fory/serialization:all
```

## Format Code

```bash
bash ci/format.sh --cpp
```

## Architecture

The C++ implementation consists of these main components:

```
cpp/fory/
├── serialization/           # Object graph serialization
│   ├── fory.h              # Main entry point (Fory, ThreadSafeFory)
│   ├── config.h            # Configuration options
│   ├── serializer.h        # Core serializer API
│   ├── basic_serializer.h  # Primitive type serializers
│   ├── struct_serializer.h # Struct serialization with FORY_STRUCT
│   ├── collection_serializer.h  # vector, set serializers
│   ├── map_serializer.h    # map serializers
│   ├── smart_ptr_serializers.h  # optional, shared_ptr, unique_ptr
│   ├── temporal_serializers.h   # Duration, Timestamp, LocalDate
│   ├── variant_serializer.h     # std::variant support
│   ├── type_resolver.h     # Type resolution and registration
│   └── context.h           # Read/Write context
├── encoder/                 # Row format encoding
│   ├── row_encoder.h       # Row format encoder
│   └── row_encode_trait.h  # Encoding traits
├── row/                     # Row format data structures
│   ├── row.h               # Row, ArrayData, MapData
│   ├── writer.h            # RowWriter, ArrayWriter
│   ├── schema.h            # Schema definitions
│   └── type.h              # Type definitions
├── meta/                    # Compile-time reflection
│   ├── field_info.h        # Field metadata extraction
│   └── type_traits.h       # Type traits utilities
└── util/                    # Common utilities
    ├── buffer.h            # Binary buffer management
    ├── error.h             # Error handling
    └── status.h            # Status codes
```
