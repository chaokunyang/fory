# Go

Load this file when changing `go/fory/` or Go xlang behavior.

## Rules

- Run Go commands from within `go/fory/`.
- Changes under `go/` must pass formatting and tests.
- The Go implementation focuses on reflection-based and codegen-based serialization.
- Root deserialization container memory budgets are owned by `ReadContext`.
  `WithMaxContainerMemoryBytes` defaults to `-1 / auto`; byte-slice roots use
  `inputBytes * 8 + 64 KiB`, and `DeserializeFromReader`/`DeserializeFromStream`
  use fixed `128 MiB`. `ReadContext` may expose only raw byte reservation and
  generic counted-byte arithmetic; slice/map formulas belong in handwritten or
  generated serializer owners. Charge Go slices as `len * elemBytes`, maps as
  `len * (keyBytes + valueBytes)`, map-backed sets, LIST-encoded inline/value
  slices, and generated container reads before allocation. Empty containers with
  no backing storage normally charge zero. Fixed arrays are caller-owned and
  normally not charged; `arrayDynSerializer` charges its temporary slice. Skip
  only dedicated string, binary, BufferObject, primitive ARRAY slice, and
  primitive array owners with byte checks.
- Set `FORY_PANIC_ON_ERROR=1` when debugging a failing Go test so you get the full call stack.
- Do not set `FORY_PANIC_ON_ERROR=1` when running the full Go test suite, because some tests assert on error contents.

## Key Paths

- `fory.go`
- `resolver.go`
- `type.go`
- `slice.go`
- `map.go`
- `set.go`
- `struct.go`
- `string.go`
- `buffer.go`
- `codegen/`
- `meta/`

## Commands

```bash
# Format code
go fmt ./...

# Run tests
go test -v ./...

# Run tests with race detection
go test -race -v ./...

# Build
go build

# Generate code
go generate ./...
```

## Java-Driven Xlang Test

```bash
cd java
mvn -T16 install -DskipTests
cd fory-core
FORY_GO_JAVA_CI=1 ENABLE_FORY_DEBUG_OUTPUT=1 mvn test -Dtest=org.apache.fory.xlang.GoXlangTest
```
