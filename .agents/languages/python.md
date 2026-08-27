# Python

Load this file when changing `python/`, Cython serialization, or Python xlang behavior.

## Rules

- Run Python commands from within `python/`.
- Changes under `python/` must pass formatting and tests.
- Fory Python requires CPython `3.8+`.
- Use `ENABLE_FORY_CYTHON_SERIALIZATION=0` first when debugging protocol behavior.
- Python mode is the pure-Python xlang implementation and is mainly for debugging and testing.
- Cython mode is the default high-performance implementation.
- Cython mode owns the hot runtime path. Do not duplicate core runtime types between Python and Cython, tunnel Python facade methods into hidden Cython internals, or keep dead shims unless the user explicitly needs a compatibility module path.
- Python `TypeResolver` owns registry freeze and finalization state. Its Cython companion may cache
  completion of the one Python-owner dispatch needed to populate native resolver tables, but the
  `Fory` facade must not mirror that state. Cython roots call the resolver owner directly.
  Serializer construction may reenter registration or a root, so the resolver rechecks both
  registration conflicts and its frozen state after construction and before publishing type,
  serializer, name, or ID state. Allocate automatic type IDs only after those checks at the common
  publication point; do not reserve IDs before callbacks or maintain rollback state.
  `ThreadSafeFory` validates registrations before retaining their replay callbacks, and it must not
  execute application factories or callbacks while holding its pool lock. Its registration
  linearization is reentrant so nested facade registrations share the same publication order. A
  root started during registration must not reuse the staging instance, and root reentry from a
  running user `fory_factory` fails without recursively invoking that factory.
- In non-strict native mode, public unqualified `register_type` for a built-in native carrier uses
  the same reserved type identity as pre-root discovery. Ordinary application classes and
  dataclasses retain their struct registration identity. Configure both through public registration;
  do not prewarm private resolver state or enumerate version-specific transitive object shapes.
- Function serialization writes captured globals as a data-only exact `dict`. Keep the reader's
  exact-type check before sizing or merging the namespace; a dict subclass or other mapping must not
  introduce runtime behavior into function reconstruction.
- Pandas `RangeIndex` owns its dtype wire slot. Encode `dtype.str` and reconstruct it with
  `numpy.dtype`; do not serialize the dtype object as a reference because concrete NumPy dtype
  classes vary across versions and would make the wire depend on version-specific registration.
- Use explicit Cython fields and methods for fixed hot-path shapes. Avoid `__getattr__`, generic `object` fields, public bridge internals, or `Fory` backreferences where ownership can stay explicit.
- Keep Python and Cython context/ref-tracking branch conditions and stack mutations semantically aligned unless a documented intentional difference exists.
- Root deserialization graph memory budget state belongs to pure-Python and Cython `ReadContext`.
  Keep `max_graph_memory_bytes` public on `pyfory.Fory`/`Config`; the default effective limit is
  fixed `128 MiB`, positive explicit values override it, and explicit non-positive values are
  invalid at config creation. Byte and stream roots use the same
  configured/default budget behavior. Do not mirror the configured max into a
  second active-limit field; keep one configured max plus mutable remaining
  budget. `ReadContext` may expose only raw
  byte reservation; collection, dict, array, struct, and object
  formulas belong in the pure-Python or Cython serializer owner. Lists, tuples, sets, and
  object-dtype ndarray item storage reserve nonzero owner self cost plus `count * PyObject*`; dicts
  reserve nonzero owner self cost plus `entryCount * 2 * PyObject*`. Python object owners reserve a
  nonzero shallow self cost plus shallow field/reference storage. Keep string, bytes, primitive
  scalar, `array.array`, primitive dense array, and primitive ndarray owners skipped, and preserve
  byte-availability checks after budget reservation.
  Treat the option as an approximate collection/dict/array/struct/object gate, not an exact heap
  cap. Leaf values skipped by graph budgeting remain gated by unread input bytes.
- Public value constructors should accept normal Python values. Raw-bit, raw-buffer, and memoryview entry points should be explicit low-level APIs, and packed carriers should expose the buffer protocol from the actual storage owner when appropriate.
- When debugging runtime or benchmark behavior, install the local package into the exact interpreter under test instead of relying on mixed `PYTHONPATH` state.
- For wheel or extension pipeline changes, derive extension-module paths from current build targets, packaging config, or wheel payload discovery rather than historical module names.
- Keep new Python test names compact and behavior-focused; avoid sentence-length names that restate setup details already obvious from the test body.
- `ENABLE_FORY_DEBUG_OUTPUT=1` enables detailed struct serialization and deserialization logs.
- Compatible scalar, list-array, and binary/uint8-array adaptations are immediate-field-only. Recursive matched-field comparison for collection elements, array elements, map keys, and map values must require exact nullability, ref tracking, generic arity, and type shape except documented user-type family normalization.

## Key Paths

- `pyfory/serialization.pyx`
- `pyfory/_fory.py`
- `pyfory/registry.py`
- `pyfory/serializer.py`
- `pyfory/includes`
- `pyfory/resolver.py`
- `pyfory/format`
- `pyfory/buffer.pyx`

## Commands

```bash
# Clean build outputs
rm -rf build dist .pytest_cache
bazel clean --expunge

# Format and lint
ruff format .
ruff check --fix .

# Install
pip install -v -e .

# Build the native extension on x86_64
bazel build //:cp_fory_so --@rules_python//python/config_settings:python_version=X.Y --config=x86_64

# Build the native extension on arm64 / aarch64
bazel build //:cp_fory_so --@rules_python//python/config_settings:python_version=X.Y --copt=-fsigned-char

# Run tests without Cython
ENABLE_FORY_CYTHON_SERIALIZATION=0 pytest -v -s .

# Run tests with Cython
ENABLE_FORY_CYTHON_SERIALIZATION=1 pytest -v -s .
```

## Java-Driven Xlang Test

```bash
cd java
mvn -T16 install -DskipTests
cd fory-core
FORY_PYTHON_JAVA_CI=1 ENABLE_FORY_CYTHON_SERIALIZATION=0 ENABLE_FORY_DEBUG_OUTPUT=1 mvn -T16 test -Dtest=org.apache.fory.xlang.PythonXlangTest
FORY_PYTHON_JAVA_CI=1 ENABLE_FORY_CYTHON_SERIALIZATION=1 ENABLE_FORY_DEBUG_OUTPUT=1 mvn -T16 test -Dtest=org.apache.fory.xlang.PythonXlangTest
```

## Debugging

- Generate annotated Cython output with `cython --cplus -a pyfory/serialization.pyx`.
- Build a debug extension with `FORY_DEBUG=true python setup.py build_ext --inplace`.
