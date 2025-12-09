# Fory C++ Hello Row (Row Format)

This example demonstrates how to use Fory's row format for cache-friendly binary random access.

## When to Use Row Format

Row format is ideal for scenarios where you need:

- **Partial serialization/deserialization**: Read specific fields without deserializing entire objects
- **Random field access**: Access fields by index without full deserialization
- **Interoperability with columnar formats**: Convert between row and column formats
- **Data processing pipelines**: Efficient data transformation and filtering

## Prerequisites

- CMake 3.16 or higher (for CMake build)
- Bazel 8+ (for Bazel build)
- C++17 compatible compiler (GCC 7+, Clang 5+, MSVC 2017+)

## Building with CMake

### Option 1: Using FetchContent (Recommended)

```cmake
include(FetchContent)
FetchContent_Declare(
    fory
    GIT_REPOSITORY https://github.com/apache/fory.git
    GIT_TAG        main
    SOURCE_SUBDIR  cpp
)
FetchContent_MakeAvailable(fory)

target_link_libraries(your_app PRIVATE fory::row_format)
```

### Build and Run

```bash
cd examples/cpp/hello_row
mkdir build && cd build
cmake .. -DCMAKE_BUILD_TYPE=Release
cmake --build . --parallel
./hello_row
```

Or use the provided script:

```bash
./run.sh
```

## Building with Bazel

From the repository root:

```bash
bazel build //examples/cpp/hello_row:hello_row
bazel run //examples/cpp/hello_row:hello_row
```

Or use the provided script:

```bash
./run_bazel.sh
```

## Example Overview

This example demonstrates:

- **Manual row writing**: Creating rows with schema definition
- **Automatic encoding**: Using `RowEncoder` for struct encoding
- **Nested structs**: Encoding structs containing other structs
- **Array encoding**: Encoding vectors of structs
- **Direct array creation**: Creating arrays from vectors

## Key Concepts

### Registering Field Info

Use the `FORY_FIELD_INFO` macro to enable automatic encoding:

```cpp
struct Employee {
  std::string name;
  int32_t id;
  float salary;
};

FORY_FIELD_INFO(Employee, name, id, salary);
```

### Manual Row Writing

```cpp
using namespace fory::row;

// Define schema
auto name_field = field("name", utf8());
auto age_field = field("age", int32());
std::vector<FieldPtr> fields = {name_field, age_field};
auto row_schema = schema(fields);

// Create and write row
RowWriter writer(row_schema);
writer.Reset();
writer.WriteString(0, "Alice");
writer.Write(1, static_cast<int32_t>(25));

// Read back
auto row = writer.ToRow();
std::cout << row->GetString(0) << std::endl;  // "Alice"
std::cout << row->GetInt32(1) << std::endl;   // 25
```

### Automatic Encoding with RowEncoder

```cpp
using namespace fory::row;

Employee emp{"Bob", 1001, 75000.0f};
encoder::RowEncoder<Employee> enc;
enc.Encode(emp);

auto row = enc.GetWriter().ToRow();
std::cout << row->GetString(0) << std::endl;  // "Bob"
std::cout << row->GetInt32(1) << std::endl;   // 1001
std::cout << row->GetFloat(2) << std::endl;   // 75000.0
```

### Supported Types

Row format supports these field types:

| Function     | Type          |
| ------------ | ------------- |
| `int8()`     | 8-bit integer |
| `int16()`    | 16-bit int    |
| `int32()`    | 32-bit int    |
| `int64()`    | 64-bit int    |
| `float32()`  | 32-bit float  |
| `float64()`  | 64-bit float  |
| `utf8()`     | UTF-8 string  |
| `binary()`   | Binary data   |
| `list(T)`    | List of T     |
| `struct(Fs)` | Struct fields |

## Using Fory in Your Project

### CMake Integration

```cmake
# Link to row format library (includes serialization)
target_link_libraries(your_app PRIVATE fory::row_format)
```

### Including Headers

```cpp
#include "fory/row/row.h"
#include "fory/row/schema.h"
#include "fory/row/writer.h"
#include "fory/encoder/row_encoder.h"
#include "fory/encoder/row_encode_trait.h"
```
