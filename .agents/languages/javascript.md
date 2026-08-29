# JavaScript And TypeScript

Load this file when changing `javascript/`.

## Rules

- Run JavaScript and TypeScript commands from within `javascript/`.
- This implementation uses npm or yarn for package management.
- IDL Jest tests should stay local to generated-code construction and local `Fory` serialize/deserialize assertions. Java-driven peer orchestration belongs in the existing integration harness.
- Language peer entrypoints under `integration_tests/idl_tests` should mirror existing peers and validate semantic equality, not just deserialize and reserialize bytes.
- Preserve generated serializer hot paths that bind writer, reader, ref, resolver, and metadata locals in outer closures; do not replace them with per-call context lookups without a measured reason.
- Do not add parallel header-low/header-high slot caches in TypeMeta hot paths to chase benchmark gaps. Header-cache hits must use the concrete checked cache owner directly; if a small hit hint is needed, cache TypeMeta objects themselves and compare `TypeMeta.headerHash`, not separate low/high header fields or benchmark-pattern state.
- JavaScript TypeMeta header cache hits should compare the 52-bit TypeMeta header hash directly. The hash is precise in JS `Number` and already includes the low header bits as hash input; do not add extra low-bit fields, sentinel state, nullable accepted headers, or parallel slot arrays around it.
- Root entry releases reference and metadata state left by the previous operation, including a
  failed operation, before the context is reused. Do not put full cleanup on the root exit path or
  copy Java backing-array retention policies onto native JavaScript arrays. Read-side metadata
  occurrence arrays use native replacement reset. The MetaString and TypeMeta writer owner tables each have
  their own logical size: reset active owner IDs and that table's logical size without clearing
  bounded backing, and replace either backing only after its root has more than 8192 owners.
- Generated registration must build the complete recursive serializer source graph against
  generation-local owners before one `TypeResolver` batch publication. The package-internal schema
  seal must lock each `TypeInfo` schema pointer before reading or traversing it; schema fields and
  occurrence modifiers are immutable afterward, while `dynamicTypeId` remains operation-local
  writer state. Seed every complete Struct, enum, and union definition in the sealed graph before
  resolving identity-only occurrences, so definition order cannot affect recursive resolution.
  One numeric ID or name cannot identify different user-defined type families. Each resolver
  identity has one complete schema owner in that graph. Repeated references and clones may share
  the same immutable definition containers and settings; reject a second conflicting definition
  before code generation without deep schema comparison. Complete anonymous definitions without a
  name or user ID do not share registry identity merely because their raw type IDs match. The
  definition-free generic enum or union serializer remains the canonical raw-type owner. Each
  transaction entry stores the schema/progress facts needed by later code generation. Within one
  registration transaction, run all of its application code hooks before instantiating any of its
  runtime serializer factories. Then reconcile each same-definition identity with any nested
  published winner and instantiate each remaining factory once in dependency completion order, with
  fixed direct captures of the final published-or-local owners. Batch-publish only the remaining
  local owners. Do not reject a valid late winner, rerun code generation or a hook, rebuild a factory
  after publication, or retain a transaction lookup, cell, callback, or wrapper in runtime
  serializers. Field occurrence modifiers remain field-owned, and conflicting families or complete
  definitions still fail before outer publication. Reject an unresolved nested Struct identity before
  resolver publication. An enum without a mapping and a union without cases keep their existing
  generic definitions; an extension reference resolves through its registered custom serializer
  owner. Never publish generated serializers, descriptors, or cache state before every generated
  factory and application code hook succeeds.
- Runtime value carriers such as decimal or reduced-precision numeric types belong under the core `types/` ownership boundary, with imports, exports, and codegen externals updated together.
- Keep `TypeInfo` as schema metadata. Compatibility-sensitive decisions belong on `TypeResolver` or explicit operations, not as retained resolver state on metadata objects.
- Normalize optional boolean config values at config construction; do not carry `null` through runtime paths when it means `false`.
- JavaScript root deserialization graph memory budgeting belongs to `ReadContext`.
  `maxGraphMemoryBytes` uses a fixed `128 MiB` default, positive explicit limits override it, and
  explicit non-positive values are invalid at config creation. Do not derive the budget from the
  `Uint8Array` root length. `ReadContext` may expose only raw
  byte reservation; generated and dynamic
  list/set/map/array/struct/object readers must reserve before allocation while preserving existing
  byte checks. Lists/sets/object arrays reserve nonzero owner self cost plus 4-byte reference slots,
  maps reserve nonzero owner self cost plus key/value reference storage, object/struct readers
  reserve nonzero shallow self memory plus shallow field storage, compatible array-to-list reads
  reserve target list materialization, and compatible list-to-typed-array reads skip the dense
  primitive-array leaf owner while preserving byte checks. Keep dedicated string, binary, primitive
  scalar, and dense typed-array leaf owners out of this budget.
  Treat the option as an approximate collection/map/array/struct/object gate, not an exact heap
  cap. Leaf values skipped by graph budgeting remain gated by unread input bytes.
- Regenerated compatible read serializers are remote-schema-specific. After classification marks a field as direct, compatible scalar, or skip, generated JavaScript should emit straight-line remote-field-order code. Do not add an outer matched-id switch unless the current regenerated shape cannot preserve those semantics.
- Compatible scalar codegen must decide the exact remote/local scalar pair before emitting source. Generate the concrete `reader.readXxx()` call plus inline trivial conversions such as boolean-to-string or numeric widening, and keep helpers only for semantic validation such as range checks, exactness checks, decimal parsing/formatting, and string-to-bool. Do not call a generic hot-path converter that redispatches on `remoteTypeId`, `localTypeId`, field descriptors, or field names.
- Compatible scalar conversion is immediate-field-only. Recursive schema comparison for collection elements, array elements, map keys, and map values must reject scalar mismatches instead of applying the top-level scalar conversion matrix.

## Commands

```bash
# Install dependencies
npm install

# Run tests
node ./node_modules/.bin/jest --ci --reporters=default --reporters=jest-junit

# Check TypeScript formatting and lint rules
npm run format-check

# Format TypeScript, then apply ESLint fixes
npm run format
```
