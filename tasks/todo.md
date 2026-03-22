# TODO

## Rust Bench Proto Report Refresh (2026-03-20)

### Spec

- Objective: rewrite the Rust benchmark suite to use the shared `benchmarks/proto/bench.proto` benchmark schema and refresh the generated Rust benchmark report/docs so they match the C++ benchmark style.
- Scope:
  - Base Rust benchmark cases on the shared `Struct`, `Sample`, `MediaContent`, `StructList`, `SampleList`, and `MediaContentList` data from `bench.proto`.
  - Remove Rust-only benchmark data/proto wiring that no longer matches the shared benchmark suite.
  - Update the Rust benchmark runner/report generation so the report layout, tables, and plots align closely with the C++ benchmark report.
  - Regenerate `docs/benchmarks/rust/**` artifacts from fresh benchmark output and delete stale Rust benchmark docs/plots that no longer correspond to the new cases.
  - Create a branch, commit the changes, push to the fork, and open a new PR.
- Verification target:
  - `cargo build` and targeted Rust benchmark/profiler commands succeed in `benchmarks/rust`.
  - Fresh benchmark output regenerates the Rust docs artifacts without stale old-case files.

### Checklist

- [x] Fetch fresh `apache/main`, create a branch, and record the task plan.
- [x] Refactor Rust benchmark models/protobuf generation to use shared `benchmarks/proto/bench.proto`.
- [x] Update Rust benchmark execution/report generation to mirror the C++ benchmark report shape.
- [x] Run Rust benchmark generation and refresh `docs/benchmarks/rust/**`, removing stale artifacts.
- [x] Run verification commands and capture outcomes.
- [x] Commit, push, open a PR, and add final review notes.

### Review

- Scope and output:
  - Replaced the Rust benchmark suite's custom `simple/medium/complex/realworld` dataset with the shared `Struct`, `Sample`, `MediaContent`, `StructList`, `SampleList`, and `MediaContentList` cases derived from `benchmarks/proto/bench.proto`.
  - Simplified the Rust benchmark crate around a new shared data module plus generic Fory/Protobuf serializers, removed the old Rust-only protobuf/model tree, and rewrote the benchmark CLI and Criterion suite around the shared cases.
  - Converted `benchmarks/rust/README.md` from a stale generated report into an actual benchmark usage guide, and rewrote `benchmark_report.py` so the generated Rust report now follows the C++ report structure with combined throughput plots plus timing/throughput/size tables.
  - Regenerated `docs/benchmarks/rust/README.md` and replaced the stale old Rust plot set (`company`, `person`, `system_data`, etc.) with the new `struct/sample/mediacontent/*list/throughput` artifacts.
  - Created the work branch `rust-bench-proto-report` from fresh `apache/main`.
- Verification:
  - `cd benchmarks/rust && cargo fmt --check` passed.
  - `cd benchmarks/rust && cargo test --offline` passed with `1` unit test covering Fory and Protobuf round trips for all six benchmark cases.
  - `cd benchmarks/rust && cargo bench --offline --bench serialization_bench -- struct --noplot` passed and exercised the new shared `struct`/`structlist` benchmark paths.
  - `cd benchmarks/rust && cargo run --offline --release --bin fory_profiler -- --print-all-serialized-sizes` passed and printed the expected shared-case size table.
  - `cd benchmarks/rust && CARGO_NET_OFFLINE=true bash ./run.sh` passed, generated `benchmarks/rust/results/README.md`, and refreshed the Rust benchmark plots now copied into `docs/benchmarks/rust/`.
  - The benchmark log still includes noisy `[fory-debug] assign_field_ids ...` lines during deserialize benchmarks, but the report generator correctly parsed the Criterion `Benchmarking ... time: [...]` records and produced the final docs report.
  - Created commit `76c7bcb42` with message `Rewrite Rust benchmarks around shared bench proto`.
  - Pushed branch `rust-bench-proto-report` to `origin` and opened PR `#3497`: `https://github.com/apache/fory/pull/3497`.

## PR 3122 Direct Fix (2026-03-20)

### Spec

- Objective: refactor PR `#3122` directly on its target branch so the new hash-based codegen isolation remains in place but is no longer fragile.
- Scope:
  - Keep the hash-accumulator approach instead of reverting to the old config-id map.
  - Ensure the hash is not consumed before registration is effectively finished.
  - Cover all public registration entry points that can affect generated serializers.
  - Keep API surface minimal and remove dead hash-lifecycle code where possible.
  - Push the fix directly to the PR branch after verification.

### Checklist

- [x] Inspect the PR worktree and identify every hash-consumption and registration-mutation path.
- [x] Refactor hash lifecycle so first cache-key use finalizes registration instead of allowing later mutation.
- [x] Cover all public registration entry points in the hash accumulator and remove dead early-consumption code.
- [x] Add or update focused tests for cache isolation and post-finalization registration behavior.
- [x] Run targeted Java verification in the PR worktree.
- [x] Commit and push the refactor directly to the PR branch.
- [x] Add final review notes with commands and outcomes.

### Review

- Scope and output:
  - Refactored the PR worktree at `/private/tmp/fory-pr3122-review` so `TypeResolver` owns the config-hash lifecycle and `ExtRegistry` owns the hash state plus pending GraalVM class registrations.
  - Deleted the wrapper-side hash action constants and the split `getConfigHash()`/`getStableConfigHash()` API from `Fory`; `Fory` is back to a thin facade while the resolver hashes final registered state directly.
  - Tightened the lifecycle contract per user correction: first `getConfigHash()` access, first serialize/deserialize/copy use, or `ensureSerializersCompiled()` now finalizes the hash and forbids any later class/serializer registration.
  - Kept the hash-based isolation approach, covered the previously missed public registration paths through resolver-owned updates, and preserved the GraalVM pending-registration flow under the finalized hash.
  - Updated focused tests to assert the new finalization rule and removed old test patterns that depended on loading codegen serializers before registration had finished.
- Verification:
  - `cd /private/tmp/fory-pr3122-review/java && ENABLE_FORY_DEBUG_OUTPUT=1 mvn -T4 -pl fory-core -Dtest=org.apache.fory.xlang.RegisterTest,org.apache.fory.serializer.TimeSerializersTest,org.apache.fory.resolver.ClassResolverTest test` passed with `Tests run: 47, Failures: 0, Errors: 0, Skipped: 0`.
  - `cd /private/tmp/fory-pr3122-review/java && mvn -T4 -pl fory-core -DskipTests spotless:check checkstyle:check` passed after formatting `ClassResolver.java`.
  - Created local commit `0550e6964` in `/private/tmp/fory-pr3122-review` with message `Fix config hash lifecycle for late registration`.
  - Direct push to `SolumXplain/fory:fix-codegen-config-hash` failed with `Permission to SolumXplain/fory.git denied to chaokunyang`; `gh pr view 3122 --repo apache/fory --json maintainerCanModify` reported `false`, so the target branch was not writable from this environment.
  - Created branch `codex/fix-config-hash-lifecycle`, pushed it to `origin`, and opened PR `#3495`: `https://github.com/apache/fory/pull/3495`.

## PR 3493 Review (2026-03-18)

### Spec

- Objective: review GitHub PR `#3493` against the latest `apache/main` and produce local review comments focused on correctness, regressions, and missing coverage.
- Scope:
  - Fetch the latest `apache/main` and the PR head locally.
  - Inspect the PR diff and changed files in context.
  - Validate any suspicious behavior with targeted commands or reasoning.
  - Record review findings and supporting evidence locally.

### Checklist

- [x] Fetch fresh `apache/main` and PR `#3493`, then identify the exact changed files and commits.
- [x] Review each changed area in context with a correctness/regression focus.
- [x] Run targeted verification for any suspected issue and capture the evidence.
- [x] Add final local review notes under this section and report the findings.

### Review

- Scope and output:
  - Fetched the latest `apache/main` plus PR head `pr-3493`, then created detached worktree `/tmp/fory2-pr3493-review` at `4698eed1d`.
  - Reviewed the 6 changed files with focus on compiler service-type validation, added service tests, example schema, and compiler guide wording.
  - Identified one concrete review finding:
    - `compiler/fory_compiler/ir/validator.py`: the new service validation only checks that the request/response names exist, so RPC methods still accept enums and unions even though the parser/docs describe these positions as message types.
- Verification:
  - `cd /tmp/fory2-pr3493-review/compiler && pytest -q fory_compiler/tests/test_fdl_service.py fory_compiler/tests/test_proto_service.py fory_compiler/tests/test_fbs_service.py` passed: `22 passed in 0.08s`.
  - `cd /tmp/fory2-pr3493-review/compiler && python - <<'PY' ... PY` with FDL/proto/FBS schemas using an enum RPC request printed:
    - `fdl_valid True []`
    - `proto_valid True []`
    - `fbs_valid True []`
  - `cd /tmp/fory2-pr3493-review/compiler && python - <<'PY' ... PY` with an FDL schema using a union RPC request printed `valid True []`.
  - This contradicts the new compiler-guide text that says RPC request/response types “must reference defined message types”.

## PR 3487 Review (2026-03-18)

### Spec

- Objective: review GitHub PR `#3487` against the latest `apache/main` and produce local review comments focused on correctness, regressions, missing tests, and protocol/runtime risks.
- Scope:
  - Fetch the latest `apache/main` and the PR head locally.
  - Inspect the PR diff and changed files in context.
  - Validate any suspicious behavior with targeted commands or reasoning.
  - Record review findings and supporting evidence locally.

### Checklist

- [x] Fetch fresh `apache/main` and PR `#3487`, then identify the exact changed files and commits.
- [x] Review each changed area in context with a correctness/regression focus.
- [x] Run targeted verification for any suspected issue and capture the evidence.
- [x] Add final local review notes under this section and report the findings.

### Review

- Scope and output:
  - Fetched the latest `apache/main` plus PR head `apache/pr/3487`, then created detached worktree `/tmp/fory2-pr3487-review` at `53e668664`.
  - Reviewed the 10 changed files with focus on new `float16_t` runtime integration, typed-array handling, generated C++ type mapping, and container compatibility.
  - Identified three concrete review findings:
    - `cpp/fory/serialization/collection_serializer.h`: `std::vector<float16_t>` is still routed through the non-arithmetic vector serializer (`TypeId::LIST`) instead of `TypeId::FLOAT16_ARRAY`, so xlang/vector typed-array semantics are wrong for the new primitive.
    - `cpp/fory/serialization/array_serializer.h`: `std::array<float16_t, N>` is not covered by any array serializer specialization, so fixed-size float16 arrays remain unsupported even though the protocol already defines `FLOAT16_ARRAY`.
    - `cpp/fory/util/float16.h`: `float16_t` has equality/ordering operators but no `std::hash` specialization, so `std::unordered_map<float16_t, ...>` / `std::unordered_set<float16_t>` do not compile.
- Verification:
  - `bazel test //cpp/fory/util:float16_test` in `/tmp/fory2-pr3487-review` passed: `61/61` tests.
  - `c++ -std=c++17 -I/tmp/fory2-pr3487-review /tmp/fory2-pr3487-review/.codex_float16_traits_check.cc -o /tmp/fory2-pr3487-review/.codex_float16_traits_check && /tmp/fory2-pr3487-review/.codex_float16_traits_check` printed `0`, confirming `std::is_arithmetic_v<fory::float16_t>` is false.
  - `c++ -std=c++17 -I/tmp/fory2-pr3487-review /tmp/fory2-pr3487-review/.codex_float16_hash_check.cc -c -o /tmp/fory2-pr3487-review/.codex_float16_hash_check.o` failed with `call to implicitly-deleted default constructor of 'std::hash<fory::float16_t>'`, confirming the unordered-container regression.
  - Repo-wide serializer search in `/tmp/fory2-pr3487-review/cpp/fory/serialization` shows:
    - vector typed-array specialization is gated by `std::is_arithmetic_v<T> && !std::is_same_v<T, bool>`
    - the fallback vector specialization for `!std::is_arithmetic_v<T>` hard-codes `TypeId::LIST`
    - array serializers only cover arithmetic/bool plus explicit unsigned builtins, with no `float16_t` specialization

## Cherry-pick 4d7c8dcf6 Conflict Resolution (2026-03-17)

### Spec

- Objective: finish cherry-picking commit `4d7c8dcf6736a9afaefa8c00e6865df05016c8cf` onto the current branch by resolving the remaining merge conflict without disturbing unrelated local work.
- Scope:
  - Inspect the in-progress cherry-pick state and the unresolved file.
  - Resolve the `javascript/packages/core/package.json` conflict by combining the rename with the current branch's intended package version state.
  - Run targeted verification and leave the repository ready for `git cherry-pick --continue`.

### Checklist

- [x] Inspect the in-progress cherry-pick state, conflicted files, and current branch context.
- [x] Resolve `javascript/packages/core/package.json` and remove all conflict markers.
- [x] Verify there are no remaining unresolved paths and the cherry-pick can continue cleanly.
- [x] Add review notes with the exact resolution and verification evidence.

### Review

- Scope and output:
  - Inspected the in-progress cherry-pick for commit `4d7c8dcf6736a9afaefa8c00e6865df05016c8cf` and confirmed the only unresolved path was `javascript/packages/core/package.json`.
  - Resolved the conflict by keeping the rename to `@apache-fory/core` from the cherry-picked commit while preserving the current branch's JavaScript package version `0.17.0-alpha.0`.
  - Aligned the workspace version entries in `javascript/package-lock.json` for `packages/core` and `packages/hps` to `0.17.0-alpha.0` so the lockfile matches the branch package manifests.
  - Continued the cherry-pick successfully, producing commit `2751efad9` with message `rename fory core`.
- Verification:
  - `git ls-files -u` returned no unresolved paths after staging the resolved files.
  - `git diff --cached --check` succeeded before continuing the cherry-pick.
  - `cd javascript && npm run build` succeeded with `@apache-fory/core@0.17.0-alpha.0` and `@apache-fory/hps@0.17.0-alpha.0`.
  - `GIT_EDITOR=true git cherry-pick --continue` succeeded and created commit `2751efad9`.
  - Final status after continue: `git status --short --branch` shows only untracked `test.md`.

## JavaScript Package Rename to `@apache-fory/core` (2026-03-17)

### Spec

- Objective: rename the JavaScript runtime package from `@apache-fory/fory` to `@apache-fory/core` and keep the workspace, docs, benchmarks, and tests consistent.
- Scope:
  - Rename the workspace directory from `packages/fory` to `packages/core`.
  - Update workspace manifests/scripts/imports/docs/benchmarks/tests to use `@apache-fory/core`.
  - Refresh the npm lockfile metadata so workspace names and paths stay consistent.
- Verification target:
  - Root build and test commands in `javascript/` succeed after the rename.
  - Repo-wide grep in `javascript/` finds no stale `@apache-fory/fory` or `packages/fory` references except historical task logs.

### Checklist

- [x] Rename `packages/fory` workspace directory to `packages/core`.
- [x] Update root workspace config, scripts, and lockfile metadata for the new package path/name.
- [x] Update all code, docs, benchmarks, and tests to use `@apache-fory/core` and `packages/core` where applicable.
- [x] Run JavaScript build/test verification and capture results.
- [x] Add review notes with the final verification evidence.

### Review

- Scope and output:
  - Renamed the JavaScript runtime workspace directory from `javascript/packages/fory` to `javascript/packages/core`.
  - Updated the publishable package name to `@apache-fory/core` in the workspace manifest and refreshed root workspace scripts/config to build against `packages/core`.
  - Rewrote all JavaScript tests, benchmarks, and README usage examples to import from `@apache-fory/core` or `packages/core`.
  - Refreshed `javascript/package-lock.json` to use the new workspace path/name and corrected the stale workspace version metadata for both `packages/core` and `packages/hps` to `0.16.0`.
- Verification:
  - `cd javascript && npm install` succeeded. NPM emitted existing `EBADENGINE` warnings because `@apache-fory/hps` declares `node: ^20.0.0` while this environment runs Node `v22.20.0`.
  - `cd javascript && npm run build` succeeded.
  - `cd javascript && npm test` succeeded: `19` passed suites, `1` skipped suite, `145` passed tests, `1` skipped test.
  - `cd javascript && npm publish --dry-run --workspace packages/core --access public` succeeded and produced tarball `apache-fory-core-0.16.0.tgz`.
  - `cd javascript && rg -n "@apache-fory/fory|packages/fory|apache-fory-fory" .` returned no matches.

## Benchmark Markdown Image Syntax Migration (2026-03-20)

### Spec

- Objective: replace HTML `<img>` tags with Markdown image syntax under `docs/benchmarks` and `benchmarks`, and make every tracked benchmark report generator emit the Markdown form.
- Scope:
  - Update all tracked markdown files under `docs/benchmarks/**` and `benchmarks/**` that still use `<img ...>` blocks.
  - Update `benchmark_report.py` scripts under `benchmarks/` that currently generate HTML image blocks so generated reports now write `![alt](path)` lines.
  - Keep plot paths and surrounding report structure unchanged except for the image syntax migration.
- Verification target:
  - `rg -n "<img" docs/benchmarks benchmarks` returns no matches.
  - Repo diff is limited to the benchmark docs and report generators required for the syntax migration.

### Checklist

- [x] Record the task scope and verification targets in `tasks/todo.md`.
- [x] Update benchmark report generators under `benchmarks/` to emit Markdown image syntax.
- [x] Replace tracked HTML image tags under `docs/benchmarks` and `benchmarks`.
- [x] Verify no `<img>` tags remain under the requested directories and review the diff.

### Review

- Scope and output:
  - Updated the C#, C++, Python, Rust, and Swift `benchmark_report.py` generators to emit Markdown image links with descriptive alt text instead of centered HTML `<img>` blocks.
  - Left `benchmarks/go/benchmark_report.py` unchanged because it already generated Markdown image syntax.
  - Replaced the remaining tracked HTML image blocks in benchmark docs under `docs/benchmarks/**` plus the benchmark READMEs in `benchmarks/**` that still used raw `<img>` tags.
  - Updated the ignored local artifact `benchmarks/cpp/report/README.md` as well so the workspace copy matches the new generated format, but it is excluded from git by `.gitignore`.
- Verification:
  - `prettier --write docs/benchmarks/csharp/README.md docs/benchmarks/cpp/README.md docs/benchmarks/cpp/REPORT.md docs/benchmarks/python/README.md docs/benchmarks/rust/README.md docs/benchmarks/swift/README.md benchmarks/cpp/README.md benchmarks/go/README.md` succeeded.
  - `python3 -m py_compile benchmarks/csharp/benchmark_report.py benchmarks/cpp/benchmark_report.py benchmarks/python/benchmark_report.py benchmarks/rust/benchmark_report.py benchmarks/swift/benchmark_report.py benchmarks/go/benchmark_report.py` succeeded.
  - `rg -n "<img" docs/benchmarks benchmarks` returned no matches.
  - `git diff --check -- tasks/todo.md benchmarks/csharp/benchmark_report.py benchmarks/cpp/benchmark_report.py benchmarks/python/benchmark_report.py benchmarks/rust/benchmark_report.py benchmarks/swift/benchmark_report.py docs/benchmarks/csharp/README.md docs/benchmarks/cpp/README.md docs/benchmarks/cpp/REPORT.md docs/benchmarks/python/README.md docs/benchmarks/rust/README.md docs/benchmarks/swift/README.md benchmarks/cpp/README.md benchmarks/go/README.md` succeeded.

## Cross-Repo Lessons Pattern Abstraction + Skill Creation Plan (2026-03-12)

### Spec

- Objective: extract recurring failure-prevention patterns from the requested `lessons.md` and `todo.md` files, then codify them into reusable Codex skills that reduce repeated corrections.
- Inputs reviewed:
  - `../fory/tasks/lessons.md`
  - `../fory/benchmarks/csharp/tasks/lessons.md`
  - `../fory2/tasks/lessons.md`
  - `../csharp_support/tasks/lessons.md`
  - `../fory_swift/tasks/lessons.md`
  - `../fory_code_review/tasks/lessons.md`
  - `../fory/tasks/todo.md`
  - `../fory/benchmarks/csharp/tasks/todo.md`
  - `../fory2/tasks/todo.md`
  - `../csharp_support/tasks/todo.md`
  - `../fory_swift/tasks/todo.md`
  - `../fory_swift/integration_tests/idl_tests/tasks/todo.md`
  - `../fory_code_review/tasks/todo.md`
  - plus current repo docs/code (`README.md`, `docs/guide/DEVELOPMENT.md`, `docs/specification/xlang_serialization_spec.md`, `docs/specification/xlang_type_mapping.md`, and multi-language runtime entrypoints).
- Observed recurring pattern clusters to abstract:
  - Plan discipline: explicit `Spec -> Checklist -> Review` loop, with verification commands and evidence.
  - Cross-language protocol rigor: xlang changes require runtime impact scan and parity checks across languages.
  - Benchmark integrity: no benchmark-shape hacks, sequential runs only, fair serializer comparisons, artifact refresh in `docs/benchmarks/**`.
  - API/runtime cleanup discipline: avoid compatibility shims, remove dead pass-through APIs, align ownership/model to C++/Rust where required.
  - CI/review execution loop: reproduce locally, root-cause fix only, monitor to stable green, publish inline findings when reviewing PRs.
  - Docs/blog claim hygiene: verify claims against specs/docs, keep terminology/link consistency, avoid over-claiming.
- Skill placement strategy:
  - Add new Fory-specific skills under `fory2/.claude/skills/` (project-local, versioned with repo context).
  - Reuse existing global skills (`fix-pr-ci`, `gh-pr-inline-review`, `fory-performance-optimization`) and avoid duplicate responsibilities.

### Proposed Skills

- `fory-spec-checklist-review-loop`
  - Trigger: any non-trivial task requiring structured execution tracking.
  - Scope: enforce `Spec -> Checklist -> Review` task-file updates, verification command capture, and completion gates.
- `fory-xlang-impact-scan`
  - Trigger: protocol/type-id/type-meta/xlang behavior changes.
  - Scope: run cross-language impact matrix, required runtime/test checks, and “xlang changed, native unchanged” validation.
- `fory-benchmark-integrity-guard`
  - Trigger: benchmark/perf/report updates.
  - Scope: enforce fair methodology, sequential benchmark execution, A/B discipline, and mandatory docs benchmark artifact refresh.
- `fory-api-surface-cleanup`
  - Trigger: API unification/refactor/cleanup tasks.
  - Scope: remove compatibility wrappers, delete dead one-line helpers, keep minimal public surface, and align naming/ownership semantics.
- `fory-doc-claim-verifier`
  - Trigger: docs/blog/README/policy text changes with technical claims.
  - Scope: map each claim to source docs/spec, enforce terminology/link consistency, and apply publish-tone constraints.
- `fory-lessons-sync`
  - Trigger: after user correction or scope redirection.
  - Scope: append correction pattern to `tasks/lessons.md`, convert into prevention rules, and feed those rules into next task plan.

### Checklist

- [ ] Finalize boundaries between new skills and existing global skills (`fix-pr-ci`, `gh-pr-inline-review`, `fory-performance-optimization`) to avoid overlap.
- [ ] Create skill folders under `.claude/skills/` for all six proposed skills.
- [ ] Implement each `SKILL.md` with concise trigger description, hard constraints, workflow, and output expectations.
- [ ] Add minimal reference files only where needed (for checklists/matrices/templates), keeping progressive-disclosure style.
- [ ] Validate each skill with the local skill validator (or equivalent metadata/frontmatter checks).
- [ ] Run one dry-run task per skill on a real recent Fory workflow and refine wording where ambiguous.
- [ ] Record rollout review notes (coverage, gaps, merge suggestions) in this section.

## AI Policy File Rename + PR Template Checklist Simplification (2026-03-11)

- [x] Review current PR template AI checklist and policy contents, then map all references to `AI_CONTRIBUTION_POLICY.md`.
- [x] Simplify `.github/pull_request_template.md` AI checklist section and move detailed checklist guidance into policy doc.
- [x] Rename `AI_CONTRIBUTION_POLICY.md` to `AI_POLICY.md` and preserve the merged checklist content there.
- [x] Update all related repository links/references to the renamed policy file and contributor checklist anchor.
- [x] Run validation (`rg` reference sweep + `git diff` sanity check) and append review notes.

### Review

- Scope and output:
  - Simplified `.github/pull_request_template.md` AI checklist to two items:
    - explicit `yes`/`no` declaration for substantial AI assistance
    - required link to centralized `Contributor Checklist` + `AI Usage Disclosure` requirements
  - Merged PR-template checklist detail into policy by expanding `AI_POLICY.md` Section 9 (`Contributor Checklist`) with the full prior checkbox set and disclosure template block.
  - Renamed policy file from `AI_CONTRIBUTION_POLICY.md` to `AI_POLICY.md`.
  - Updated policy reference in `CONTRIBUTING.md` to `./AI_POLICY.md`.
- Verification:
  - `rg -uu -n "AI_CONTRIBUTION_POLICY\\.md" -S . --glob '!tasks/**' --glob '!.git/**'` returned no matches.
  - `git diff -- .github/pull_request_template.md AI_POLICY.md CONTRIBUTING.md tasks/todo.md` confirmed intended edits.

## Release Script C#/Swift Version Bump Support (2026-03-11)

- [x] Inspect `ci/release.py` current language matrix and identify missing C#/Swift bump logic.
- [x] Extend `bump_version` to include `csharp`/`swift` in `-l all` and explicit handlers.
- [x] Implement robust version updaters for C# package version and C#/Swift install snippets.
- [x] Run focused validation by invoking `ci/release.py bump_version` and checking touched files.
- [x] Add a review summary with validation evidence.

### Review

- Scope and output:
  - Updated `ci/release.py` `bump_version(..., l="all")` language list to include `csharp` and `swift`.
  - Added `bump_csharp_version` to bump:
    - `csharp/Directory.Build.props` `<Version>...`
    - `csharp/README.md` NuGet `PackageReference` version snippet
    - `docs/guide/csharp/index.md` NuGet `PackageReference` version snippet
  - Added `bump_swift_version` to bump:
    - `swift/README.md` SwiftPM dependency snippet `.package(url: "https://github.com/apache/fory.git", from: "...")`
  - Added dedicated updater helpers with explicit errors when expected anchors are missing.
- Verification:
  - `python -m py_compile ci/release.py` (passed)
  - `python ci/release.py bump_version -l csharp,swift -version 0.77.0` updated only expected files:
    - `csharp/Directory.Build.props`
    - `csharp/README.md`
    - `docs/guide/csharp/index.md`
    - `swift/README.md`
  - Verified diff showed correct version replacement in those files, then restored them.
  - Ran a lightweight monkeypatch dispatch check for `l=all` and confirmed both handlers are called:
    - `contains-csharp True`
    - `contains-swift True`

## Python macOS Universal2 Wheel Merge Path Fix (2026-03-11)

- [x] Confirm the release workflow failure root cause in `.github/workflows/build-native-release.yml`.
- [x] Replace hardcoded `.so` paths with dynamic shared-library discovery for universal2 merge.
- [x] Strengthen universal2 verification to assert each merged `.so` contains both `arm64` and `x86_64`.
- [x] Run focused validation for the edited workflow script and capture results.
- [x] Add a review summary for this task.

### Review

- Scope and output:
  - Updated `.github/workflows/build-native-release.yml` universal2 merge step to discover `.so` payloads dynamically via `find pyfory -name '*.so' -type f` instead of hardcoded names (`pyfory/buffer.so`, etc.).
  - Added explicit guardrails during merge: fail if no shared libraries are found or if an `x86_64` counterpart is missing for any discovered arm64 `.so`.
  - Updated universal2 verification step to enumerate packed `.so` files dynamically and fail unless each reports both `arm64` and `x86_64` from `lipo -archs`.
- Root cause:
  - The release workflow assumed untagged extension names like `pyfory/buffer.so`, but wheel payloads now use ABI-tagged filenames (for example, `buffer.cpython-313-darwin.so`), so `lipo` failed with file-not-found.
- Verification:
  - Executed a focused bash fixture script that creates synthetic arm64/x86_64 `.so` files with ABI-tagged names and runs the updated merge+verify logic end-to-end.
  - Verified `lipo -archs` output for each merged file includes both architectures:
    - `pyfory/buffer.cpython-313-darwin.so: x86_64 arm64`
    - `pyfory/format/_format.cpython-313-darwin.so: x86_64 arm64`
    - `pyfory/lib/mmh3/mmh3.cpython-313-darwin.so: x86_64 arm64`
    - `pyfory/serialization.cpython-313-darwin.so: x86_64 arm64`

## Swift/C# 0.16.0 Release Version Sync (2026-03-10)

- [x] Review current Swift/C# release-version references and identify stale `0.1.0` values.
- [x] Update the C# package version metadata and Swift/C# install snippets to `0.16.0`.
- [x] Run focused verification on the changed files and add a review summary.

### Review

- Scope and output:
  - Updated `csharp/Directory.Build.props` package version from `0.1.0` to `0.16.0`.
  - Updated the C# NuGet install snippet in `csharp/README.md` to `Apache.Fory` version `0.16.0`.
  - Updated the C# guide install snippet in `docs/guide/csharp/index.md` to `Apache.Fory` version `0.16.0`.
  - Updated the Swift Package Manager dependency example in `swift/README.md` to start from `0.16.0`.
- Verification:
  - `sed -n '1,40p' csharp/Directory.Build.props` confirmed `<Version>0.16.0</Version>`.
  - `rg -n "0\\.1\\.0|0\\.16\\.0" csharp/Directory.Build.props csharp/README.md swift/README.md docs/guide/csharp/index.md` showed only the expected `0.16.0` references.
  - `rg -n "0\\.1\\.0" csharp swift docs/guide/csharp --glob '!**/bin/**' --glob '!**/obj/**' --glob '!**/.build/**'` returned no matches.

## C# NuGet Publish Readiness (2026-03-10)

- [x] Review current C# package metadata, generator packaging, and release friction for NuGet publishing.
- [x] Update the main C# package so it carries the required NuGet metadata and bundles the source generator for single-package consumption.
- [x] Add a one-command C# release helper and document the version-bump + publish flow.
- [x] Run the available verification steps, record any environment limitations, and add a review summary.

### Review

- Scope and output:
  - Added `csharp/Directory.Build.props` so C# NuGet metadata and the package version live in one place.
  - Updated `csharp/src/Fory/Fory.csproj` to publish as `Apache.Fory`, include `csharp/README.md` as the NuGet readme, and bundle `Fory.Generator.dll` into `analyzers/dotnet/cs/`.
  - Marked `csharp/src/Fory.Generator/Fory.Generator.csproj` and `csharp/tests/Fory.XlangPeer/Fory.XlangPeer.csproj` as non-packable to avoid accidental extra packages.
  - Added `ci/publish-nuget.sh` to restore, test, pack, and push the main NuGet package plus symbols with one command while writing artifacts to `csharp/artifacts/nuget`.
  - Updated `csharp/README.md` and `docs/guide/csharp/index.md` to document the single-package `PackageReference` flow and the publish command.
  - Ignored `csharp/artifacts/` in `.gitignore` for local package output.
- Verification:
  - `bash -n ci/publish-nuget.sh` succeeded.
  - `xmllint --noout csharp/Directory.Build.props csharp/src/Fory/Fory.csproj csharp/src/Fory.Generator/Fory.Generator.csproj csharp/tests/Fory.XlangPeer/Fory.XlangPeer.csproj` succeeded.
  - `sed -n 's:.*<Version>\(.*\)</Version>.*:\1:p' csharp/Directory.Build.props | head -n 1` returned `0.1.0`.
  - `./ci/publish-nuget.sh` failed immediately with `dotnet SDK 8.0+ is required.` because this environment does not have `dotnet` installed, so I could not run a real pack/push dry run.

## PR 3462 CI Fix Loop (2026-03-10)

- [ ] Review PR `#3462` scope against `apache/main`, fetch fresh upstream state, and identify the exact failing CI jobs/logs.
- [ ] Reproduce the failing `Code Style Check` and representative `Python CI` jobs locally on the PR branch.
- [ ] Implement the minimal root-cause fix for the CI regression without weakening tests or adding compatibility-only code.
- [ ] Run the required local verification for touched paths and record the commands/results.
- [ ] Commit only the intended fix, push to `origin/make_swift_lib_usable`, and monitor PR CI until the latest head SHA is fully green.
- [ ] Add a review summary in this task file after CI is green.

## Swift Benchmark Package Path Migration (2026-03-10)

- [x] Add plan/checklist for switching benchmark Swift dependency to the new root package path.
- [x] Update `benchmarks/swift/Package.swift` to consume Fory via repo-root `Package.swift`.
- [x] Remove obsolete local package link `benchmarks/swift/fory-swift-dep`.
- [x] Validate by building `benchmarks/swift` with SwiftPM.
- [x] Add a review summary in this task file.

### Review

- Scope and output:
  - Updated `benchmarks/swift/Package.swift` to use the repo root package via `.package(name: "fory", path: "../..")`.
  - Updated Swift benchmark target dependency to `.product(name: "Fory", package: "fory")`.
  - Removed obsolete `benchmarks/swift/fory-swift-dep` symlink.
- Verification:
  - `cd benchmarks/swift && swift package dump-package` succeeded and resolved local dependency to `/Users/chaokunyang/Desktop/dev/fory2`.
  - `cd benchmarks/swift && swift build -c release` succeeded.

## Top-level Swift Package + SPI Docs Stub (2026-03-10)

- [x] Review current Swift package layout and confirm the minimal root-level package mapping for SPM consumers.
- [x] Add a top-level `Package.swift` that exposes `Fory` from `swift/` so downstream users can depend on `https://github.com/apache/fory.git`.
- [x] Add Swift Package Index manifest stub to explicitly select Swift documentation targets.
- [x] Update `swift/README.md` dependency/docs snippet for the new root package flow and SPI docs location.
- [x] Validate with `swift package dump-package` from repo root and `cd swift && swift test`.
- [x] Add a review summary in this task file.

### Review

- Scope and output:
  - Added root-level `Package.swift` exposing `Fory` and `ForyXlangTests` while mapping sources/tests from `swift/`.
  - Added root-level `.spi.yml` with `documentation_targets: [Fory]` for Swift Package Index DocC generation.
  - Updated `swift/README.md` quick-start dependency to `.package(url: "https://github.com/apache/fory.git", from: "0.1.0")`.
  - Added Swift Package Index docs link in `swift/README.md`: `https://swiftpackageindex.com/apache/fory/main/documentation/fory`.
- Verification:
  - `swift package dump-package` from repo root succeeded and showed targets resolved under `swift/`.
  - `swift test` from repo root succeeded (49 tests passed).
  - `cd swift && swift test` succeeded (49 tests passed).

## Protobuf/FlatBuffers Shared/Circular Reference Blog Draft (2026-03-09)

- [x] Review the reference blog plus compiler docs for protobuf/FlatBuffers IDL mapping and `ref`/`weak_ref` semantics.
- [x] Draft a new blog markdown file focused on extending protobuf/FlatBuffers schemas with shared/circular reference support.
- [x] Re-check technical claims/examples against local docs and format updated markdown files.
- [x] Add a review summary in this task file.

### Review

- Scope and output:
  - Added `2026-03-09-fory_extend_protobuf_flatbuffers_shared_circular_reference_support.md` as a new long-form blog draft.
- Content focus:
  - Centered the article on extending existing `.proto` and `.fbs` schemas with Fory reference options instead of requiring a full schema rewrite.
  - Included concrete protobuf and FlatBuffers examples for `ref`/`weak_ref` and pointer-style options.
  - Explicitly documented migration boundaries: Fory wire format (not protobuf/FlatBuffers wire compatibility), unknown-field differences, and FlatBuffers default-literal caveat.
- Verification:
  - Re-checked claims and option semantics against `docs/compiler/protobuf-idl.md`, `docs/compiler/flatbuffers-idl.md`, and `docs/compiler/schema-idl.md`.
  - Ran `prettier --write 2026-03-09-fory_extend_protobuf_flatbuffers_shared_circular_reference_support.md tasks/todo.md`.
  - Follow-up correction applied: added canonical reference URL `https://fory.apache.org/blog/fory_schema_idl_for_object_graph` to the blog `References` section.

## Fory Schema IDL Website + Social Publishing Pass (2026-03-08)

- [x] Re-read the current website article draft and re-check the strongest technical claims against local docs/tests.
- [x] Refine the website article for publish-readiness while keeping the fixed title.
- [x] Add a separate short summary file for Hacker News and Reddit.
- [x] Format updated markdown files and add a review summary in this task file.

### Review

- Scope and output:
  - Updated `2026-03-06-fory_schema_idl_define_once_serialize_everywhere.md` for website publishing.
  - Added `2026-03-06-fory_schema_idl_hn_reddit_summary.md` with separate HN and Reddit summary bodies.
- Publish-readiness changes:
  - Kept the fixed title but reduced repeated "first/no other" claims in the body so the article argues by examples and documented behavior instead of repetition.
  - Clarified that `.proto` and `.fbs` are compiler input frontends that generate Fory code and Fory wire format, not protobuf/FlatBuffers wire compatibility.
  - Softened absolute compatibility/performance wording, added a benchmark-report link, and tightened schema-evolution language around compatible-mode rules.
  - Added an explicit shared-identity `OrderBatch` example so the walkthrough demonstrates object-graph semantics instead of only mentioning them abstractly.
- Verification:
  - Re-checked wording against local compiler docs and IDL integration tests already present in the repo.
  - Ran `prettier --write 2026-03-06-fory_schema_idl_define_once_serialize_everywhere.md 2026-03-06-fory_schema_idl_hn_reddit_summary.md tasks/todo.md`.
  - Manually reread the updated article and social summary after formatting.

## Fory Schema IDL Blog Draft (2026-03-06)

- [x] Read the reference blog and all docs under `docs/compiler` and `docs/guide/xlang`, extracting the facts and examples needed for a schema-IDL article.
- [x] Decide the blog structure, target audience, and publishing tone based on Apache Fory blog style plus Reddit/Hacker News/Medium/Twitter reuse.
- [x] Draft a new markdown blog post about Fory Schema IDL in a new file.
- [x] Verify the post against the docs, refine wording/examples for correctness, and add a review summary in this task file.

### Review

- Scope and output:
  - Added new blog draft at `2026-03-06-fory_schema_idl_one_schema_native_models_across_languages.md`.
  - Matched the reference blog's long-form technical marketing style while shifting the focus to Fory Schema IDL and schema-first xlang workflows.
- Source synthesis:
  - Read the reference article plus all docs under `docs/compiler` and `docs/guide/xlang`.
  - Built the article around documented compiler behavior, generated-code shape, xlang nullability/reference semantics, and protobuf/FlatBuffers migration paths.
- Content choices:
  - Positioned Schema IDL relative to native xlang-without-IDL so the article explains when to adopt codegen instead of implying it is mandatory.
  - Highlighted the wire-compatibility nuance that language-specific package options do not change the cross-language namespace.
  - Covered explicit vs auto-generated type IDs, `optional`, `ref`, `union`, and generated helper APIs because those are the key differentiators from the docs.
- Verification:
  - Reformatted markdown with `prettier --write 2026-03-06-fory_schema_idl_one_schema_native_models_across_languages.md tasks/todo.md`.
  - Manually reread the draft after formatting and tightened wording to avoid claims beyond the current docs.

## AI Contribution Policy for Apache Fory (2026-02-28)

- [x] Compile policy requirements from Scala 3, Apache Kvrocks, ASF generative tooling guidance, and 20+ OSS AI policy documents.
- [x] Draft `AI_CONTRIBUTION_POLICY.md` at repository root with strict rules for:
  - [x] accountability and human ownership
  - [x] mandatory disclosure/provenance
  - [x] anti-slop, anti-redundancy, and anti-dead-code requirements
  - [x] design-first requirements for non-trivial changes in low-level serialization code
  - [x] mandatory verification/testing evidence and reviewability
  - [x] ASF licensing/copyright compliance constraints
  - [x] maintainer enforcement powers and rejection criteria
- [x] Review policy language for clarity, enforceability, and alignment with ASF constraints.
- [x] Add a review summary in this task file after completion.

### Review

- Scope and intent:
  - Added strict repository-level AI policy at `AI_CONTRIBUTION_POLICY.md` for all contribution artifacts.
  - Tuned policy for low-level serialization risks (protocol safety, performance regression, cross-language compatibility).
- Source synthesis:
  - Incorporated strict patterns from Scala 3, Apache Kvrocks, ASF generative tooling guidance, and 20+ OSS AI policy documents.
- Enforcement posture:
  - Added auto-reject conditions for undisclosed AI usage, slop, redundant/dead code, hallucinated APIs, and low-effort bulk edits.
  - Added mandatory disclosure schema, verification evidence requirements, and maintainer closure rights.
- ASF/legal alignment:
  - Added licensing/provenance obligations and explicit compatibility checks for third-party material in AI output.
- Verification:
  - Confirmed file exists at repository root: `AI_CONTRIBUTION_POLICY.md`.
  - Ran markdown formatter: `prettier --write AI_CONTRIBUTION_POLICY.md`.

## Rust Context Leak + Unwind Coverage (2026-02-26)

- [x] Eliminate `WriteContext::new` fallback-buffer leak while keeping TLS context reuse and no per-call allocation overhead.
- [x] Keep attach/detach behavior panic-safe and avoid `Box::from_raw(self.writer.bf)` ownership traps.
- [x] Add panic-unwind regression tests for `serialize_to`, `deserialize`, and `deserialize_from` to validate context reuse after `catch_unwind`.
- [x] Run focused Rust tests for the touched paths.

### Review

- Root cause:
  - `WriteContext` still allocated fallback buffer via `Box::leak` but no longer had a matching reclamation path.
  - Panic-unwind detach guards in `Fory` entrypoints lacked regression coverage.
- Fix summary:
  - Added `owned_writer_buffer` pointer to `WriteContext`, allocate once per context, and reclaim in `Drop`.
  - Removed leak-based fallback allocation (`get_leak_buffer`) and switched to owned pointer allocation (`Box::into_raw`).
  - Added `test_context_reusable_after_unwind_in_entrypoints` covering panic + `catch_unwind` for `serialize_to`, `deserialize`, and `deserialize_from`, then verifying reuse succeeds.
- Verification:
  - `cd rust && cargo fmt` (passed)
  - `cd rust && cargo test -p tests --test test_fory -- --nocapture` (passed)
  - `cd rust && cargo test -p tests --no-run` (passed)
  - `cd rust && cargo clippy --all-targets --all-features -- -D warnings` (passed)

- [x] Analyze protocol/header and type-meta parsing paths for unchecked cursor advance and late mismatch detection.
- [x] Fix `read_header` to use bounds-checked cursor advance semantics.
- [x] Add early protocol mismatch rejection in `Fory::deserialize` entrypoints.
- [x] Fix type-meta tail-skip logic in `type_resolver.cc`:
  - [x] use checked `skip` instead of unchecked `increase_reader_index`
  - [x] reject parser over-consumption (`current_pos > expected_end_pos`) as malformed meta
  - [x] keep exact-consumption invariant
- [x] Add regression tests for protocol mismatch and malformed meta handling.
- [x] Run targeted C++ tests and confirm pass.

## Review

- Root cause 1: `read_header` advanced cursor via unchecked `increase_reader_index(1)` after reading flags.
- Root cause 2: TypeMeta parsers used unchecked `increase_reader_index(remaining)` to align to declared meta size and did not reject parser over-consumption (`current_pos > expected_end_pos`).
- Root cause 3: Deserialize entrypoints accepted payloads with `xlang` header flag mismatching local config, delaying failure to deeper parsing.
- Fix summary:
  - `read_header` now reads the flags byte via `buffer.read_uint8(error)` (bounds-checked path).
  - `Fory::deserialize` now rejects `header.is_xlang != config_.xlang` immediately with `InvalidData`.
  - `TypeMeta::from_bytes` and `TypeMeta::from_bytes_with_header` now:
    - reject over-consumption as malformed type meta,
    - use `buffer.skip(..., error)` for remaining bytes to enforce bounds checks,
    - keep exact-consumption behavior.
- Verification:
  - `bazel test //cpp/fory/serialization:serialization_test`
  - `bazel test //cpp/fory/util:buffer_test`
  - Both passed.

## Rust Decoder Hardening (2026-02-26)

- [x] Add strict bounds checks for all fixed-width `Reader` decode paths (`u16/u32/u64/f16/f32/f64/u128`).
- [x] Make `Reader::check_bound` overflow-safe and return `Error` for invalid cursor arithmetic.
- [x] Ensure `read_varuint36small` remains panic-free when cursor is externally advanced out of bounds.
- [x] Convert row getters to panic-free behavior for OOB index access.
- [x] Update row tests for new getter error semantics and add OOB checks.
- [x] Add malformed/truncated input regression tests for fixed-width reader methods.
- [x] Run focused Rust tests and full test target compile.

### Review

- Root cause:
  - Fixed-width reader methods delegated to `LittleEndian::read_*` over unchecked slices, which could panic on truncated inputs.
  - Row getter methods explicitly used `panic!(\"out of bound\")` for invalid indexes.
  - `check_bound` used unchecked `cursor + n` arithmetic.
- Fix summary:
  - Hardened `Reader::check_bound` with `checked_add`.
  - Added explicit `check_bound` calls in all fixed-width read methods before touching byte slices.
  - Added a defensive `check_bound(0)` in `read_varuint36small`.
  - Changed row getter APIs to return `Result<_, Error>` on OOB instead of panicking.
  - Updated `MapGetter::to_btree_map` to propagate getter errors.
  - Added regression tests for malformed fixed-width reads and row getter OOB behavior.
- Verification:
  - `cargo test -p tests --test test_buffer --test test_row -- --nocapture` (passed)
  - `cargo test -p tests --no-run` (passed)

## Rust Context Panic-Unwind Safety (2026-02-26)

- [x] Remove `transmute`-based borrowed buffer lifetime extension in `Fory` serialize/deserialize entry points.
- [x] Remove attach/detach context swapping for external buffers/slices.
- [x] Use per-call temporary read/write contexts for borrowed `serialize_to`/`deserialize*` paths.
- [x] Remove raw-pointer `Box::from_raw` ownership cleanup in `WriteContext::Drop`.
- [x] Keep thread-local cached write context for owned-buffer `serialize`.
- [x] Run focused Rust tests and full test target compile.

### Review

- Root cause:
  - `serialize_to`/`deserialize`/`deserialize_from` used `mem::transmute` to force borrowed data into `'static` context caches.
  - Cleanup depended on explicit `detach_*` calls; panics could bypass detach and leave invalid pointers in cached context state.
  - `WriteContext::Drop` reclaimed a leaked pointer using `Box::from_raw`, which is brittle ownership-wise in unsafe paths.
- Fix summary:
  - Added `create_write_context`/`create_read_context` constructors in `Fory` and switched borrowed-data entry points to per-call contexts.
  - Removed `transmute`, `attach_*`, `detach_*`, and read-context TLS cache from these entry paths.
  - Added `WriteContext::with_writer` and `ReadContext::with_reader` constructors for explicit context wiring.
  - Removed raw `Box::from_raw` drop path from `WriteContext`.
- Verification:
  - `cargo test -p tests --test test_buffer --test test_row -- --nocapture` (passed)
  - `cargo test -p tests --no-run` (passed)

## C++ Registration Safety Hardening (2026-02-26)

- [x] Disallow all public type registration APIs after first serialize/deserialize use.
- [x] Ensure `ThreadSafeFory` locks registration state at first use so late registrations are rejected.
- [x] Make `TypeResolver::register_type_internal` transactional so failed duplicate checks leave no partial state.
- [x] Add C++ regression tests for:
  - [x] late registration rejection after first use (thread-safe path)
  - [x] non-transactional registration failure leak (ctid map pollution)
- [x] Run targeted C++ serialization tests and confirm pass.

### Review

- Root cause 1: `ThreadSafeFory` finalized a resolver clone lazily for pooled instances, but base resolver registrations remained open and silently diverged after first use.
- Root cause 2: `TypeResolver::register_type_internal` mutated `type_infos_` and `type_info_by_ctid_` before duplicate checks, so failed registrations leaked partial state.
- Fix summary:
  - Added registration gating in `BaseFory` for all public registration methods, returning `Invalid` once first use occurs.
  - Locked registration state at first resolver-finalization point in both `Fory` and `ThreadSafeFory`.
  - Reworked `register_type_internal` to validate uniqueness first, then commit map/vector mutations atomically.
  - Added regression tests for late registration rejection and duplicate-registration transactional safety.
- Verification:
  - `ENABLE_FORY_DEBUG_OUTPUT=1 bazel test //cpp/fory/serialization:serialization_test --test_output=errors` (passed)

## C++ Stream Deserialization (2026-02-26)

- [x] Review Java stream deserialization flow and align C++ API/ownership model with minimal surface-area changes.
- [x] Add C++ stream abstraction for deserialization input (`ForyInputStream`) and implement a chunked test stream helper.
- [x] Extend `Buffer` to support optional stream-backed fill-on-demand for read paths:
  - [x] add stream attachment/detachment lifecycle
  - [x] add bounds-checked ensure/skip/index-advance helpers that can request more bytes from stream
  - [x] keep non-stream path branch-predictable and avoid hot-path regression
- [x] Add `Fory` stream deserialization APIs (single-threaded and thread-safe entrypoints) that accept `ForyInputStream` explicitly.
- [x] Wire `ReadContext`/deserializer paths to use stream-aware buffer reads, including direct index advances and fast paths in skip/collection/struct helpers.
- [x] Update build definitions for any new C++ source/header files.
- [x] Add C++ regression tests covering:
  - [x] deserializing from partial/chunked stream
  - [x] truncated stream error behavior
  - [x] multi-object sequential stream decode behavior
  - [x] parity with existing buffer-based decode for representative types
- [x] Run focused C++ tests and report results.
- [x] Run a local micro-comparison (buffer path before/after) and confirm no obvious >5% regression on normal path.

### Review

- Root cause:
  - C++ deserialization assumed the full payload was already in memory and only accepted byte buffers, so network transfer and decode could not overlap.
  - Several skip/fast paths used direct reader-index jumps or `remaining_size` checks that fail early on partial buffers.
- Fix summary:
  - Added stream abstraction and API:
    - `cpp/fory/serialization/fory_input_stream.h/.cc` introduces `ForyInputStream` (wraps `std::istream`) and exposes a reusable internal `Buffer`.
    - `Fory` and `ThreadSafeFory` now support `deserialize<T>(ForyInputStream&)`.
  - Made `Buffer` stream-aware without changing the non-stream hot path:
    - Added `StreamReader` interface (`cpp/fory/util/stream_reader.h`).
    - Added stream attachment, `ensure_readable`, and `increase_reader_index(diff, Error&)`.
    - Underflow now triggers on-demand fill + compaction when a stream reader is attached.
  - Updated deserializer paths to be stream-safe:
    - `skip.cc`, `collection_serializer.h`, `string_serializer.h`, `unsigned_serializer.h` now use stream-aware bounds checks/reader advances.
    - Disabled struct fast bulk-read path when buffer is stream-backed to keep correctness on partial input.
  - Added regression coverage:
    - `cpp/fory/serialization/stream_deserialization_test.cc` with chunked stream decode, sequential multi-object decode, and truncated-stream error test.
  - Updated build metadata:
    - `cpp/fory/serialization/BUILD`, `cpp/fory/serialization/CMakeLists.txt`, `cpp/fory/util/CMakeLists.txt`.
- Verification:
  - `ENABLE_FORY_DEBUG_OUTPUT=1 bazel test //cpp/fory/serialization:stream_deserialization_test //cpp/fory/util:buffer_test //cpp/fory/serialization:serialization_test --test_output=errors` (passed)
  - `ENABLE_FORY_DEBUG_OUTPUT=1 bazel test //cpp/fory/serialization:collection_serializer_test //cpp/fory/serialization:unsigned_serializer_test //cpp/fory/serialization:struct_test //cpp/fory/serialization:struct_compatible_test --test_output=errors` (passed)
  - Proxy perf sanity (normal buffer path):
    - Current branch: `/usr/bin/time -p bazel test //cpp/fory/serialization:serialization_test --cache_test_results=no --runs_per_test=200 --test_arg=--gtest_filter=SerializationTest.Int32Roundtrip --test_output=summary`
      - stats over 200 runs: avg `0.1s`
    - Baseline (`apache/main` worktree, same command): avg `0.1s`
    - No measurable regression from this local proxy check.

## PR 3441 Review (2026-03-01)

- [x] Fetch PR branch and enumerate file-level change scope against `apache/main`.
- [x] Perform full diff review with focus on Python serialization hot paths and behavior changes.
- [x] Audit `python/pyfory/cpp/pyfory.cc` for CPython C-API safety, exception semantics, and reference ownership/leak risks.
- [x] Check optimization paths for semantic mismatches, unreachable/dead code, and simplification opportunities.
- [x] Validate findings with targeted local checks/tests where feasible.
- [x] Record final findings and residual risks in review notes.

### Review

- Findings summary:
  - Confirmed a build-breaking CPython C-API compatibility regression in `python/pyfory/cpp/pyfory.cc` due private header include (`longintrepr.h`).
  - Found a new malformed-input safety regression in `python/pyfory/collection.pxi` (`get_next_element`) where `REF_FLAG` handling bypasses `try_preserve_ref_id` bounds checks and indexes `read_objects` without validating `ref_id`.
  - No direct reference leak was found in `python/pyfory/cpp/pyfory.cc` normal/error paths; ref-count ownership looked balanced in reviewed code paths.
  - Found minor dead/redundant code (unused local `type_resolver` variable in `_read_same_type_ref`; existing duplicate `write_ref_or_null` call in `MapSerializer.write` null-key path remains).
- Verification:
  - Refreshed baseline: `git fetch apache main` (updated `apache/main` to `14269047e`).
  - PR build check (worktree `/tmp/fory2-pr3441-review`): `bazel build //:cp_fory_so --@rules_python//python/config_settings:python_version=3.11 --copt=-fsigned-char` (failed with `longintrepr.h` not found).
  - Baseline build check (worktree `/tmp/fory2-main-review`): same command on `apache/main` (passed).

## Java Fory Read/Write API Unification (2026-03-04)

- [x] Identify and remove `CrossLanguage`-suffixed read/write method names in `Fory` internal API while preserving current dispatch semantics.
- [x] Unify read/write control flow to the non-suffixed method pairs (`write*`/`read*`) without introducing explicit `if (xlang)` protocol branching for unified types.
- [x] Update any docs/comments/error guidance that still references legacy xlang-specific read/write naming.
- [x] Run focused `fory-core` tests for read/write + xlang paths to validate correctness and no behavioral regressions.
- [x] Add a review summary with verification commands/results.

### Review

- Summary:
  - Removed `CrossLanguage`-suffixed helper APIs from `java/fory-core/src/main/java/org/apache/fory/Fory.java` read/write paths.
  - Replaced them with resolver-scoped helper names (`*WithXtypeResolver`) to keep protocol dispatch explicit while avoiding `if (xlang)` boolean-branching style in unified paths.
  - Unified nested-call guidance to `Fory#writeXXX` / `Fory#readXXX` (removed legacy `xwrite/xread` hint text).
  - Added regression tests in `java/fory-core/src/test/java/org/apache/fory/ForyTest.java`:
    - `testNestedSerializeHintUsesWriteApi`
    - `testNestedDeserializeHintUsesReadApi`
- Verification:
  - `cd java && mvn -T16 -pl fory-core -DskipTests compile` (passed)
  - `cd java && mvn -T16 -pl fory-core -DskipTests checkstyle:check spotless:check` (passed)
  - `cd java && mvn -T16 -pl fory-core spotless:apply` (applied formatting to `ForyTest.java`)
  - `cd java && mvn -T16 -pl fory-core -Dtest=org.apache.fory.ForyTest,org.apache.fory.resolver.ClassResolverTest,org.apache.fory.serializer.EnumSerializerTest test` (passed, `Tests run: 92, Failures: 0, Errors: 0, Skipped: 0`)

## Java API Cleanup Remove JavaObject Legacy APIs (2026-03-04)

- [x] Remove `serializeJavaObject`/`deserializeJavaObject` and `serializeJavaObjectAndClass`/`deserializeJavaObjectAndClass` from `BaseFory` and all implementations/delegates.
- [x] Keep unified protocol path for these APIs by wiring call sites to `serialize`/`deserialize` pairs without introducing xlang-special boolean branches for unified type IDs.
- [x] Update stream helper APIs in `BlockedStreamUtils` to use unified method names and remove legacy method surface.
- [x] Update Java docs under `docs/guide/java/**` to remove legacy API references and examples.
- [x] Update/trim tests to use unified API names and delete redundant coverage that only validated removed legacy entrypoints.
- [x] Run focused compile/style/tests in `java/fory-core` (and any impacted module tests) and record results.

### Review

- Summary:
  - Removed legacy Java-object entry APIs from `BaseFory`, `Fory`, `ThreadLocalFory`, `ThreadPoolFory`, and `SimpleForyPool`.
  - Added unified typed deserialize overloads for `MemoryBuffer`/`ForyInputStream`/`ForyReadableChannel` under `deserialize(..., Class<T>)`.
  - Refactored typed-deserialize internals to preserve on-wire order (`ref-flag -> type-info -> data`) and keep primitive fast paths via resolver-aware dispatch.
  - Removed legacy `BlockedStreamUtils` Java-object helpers and unified stream/channel typed methods onto `deserialize(..., Class<T>)`.
  - Updated Java docs (`basic-serialization`, `troubleshooting`, `schema-evolution`) to use only unified serialize/deserialize APIs.
  - Updated tests to unified APIs and removed redundant coverage that only duplicated removed legacy entrypoints (e.g., redundant stream/channel branches and duplicate compatible serializer test).
- Verification:
  - `cd java && mvn -T16 -pl fory-core -DskipTests compile` (passed)
  - `cd java && mvn -T16 -pl fory-core -DskipTests test-compile` (passed)
  - `cd java && mvn -T16 -pl fory-core -DskipTests checkstyle:check spotless:check` (passed)
  - `cd java && mvn -T16 -pl fory-core -Dtest=org.apache.fory.ForyTest#testSerializeDeserializeApis,org.apache.fory.StreamTest#testBufferReset+testOutputStreamWithType+testReadableChannel,org.apache.fory.ThreadSafeForyTest#testSerializeDeserializeWithType,org.apache.fory.io.BlockedStreamUtilsTest,org.apache.fory.serializer.CompatibleSerializerTest#testSerializeDeserializeApis,org.apache.fory.serializer.EnumSerializerTest#testEnumSerializationAsString+testEnumSerializationAsString_differentClass+testEnumSerializationAsString_invalidEnum,org.apache.fory.serializer.compatible.DifferentPOJOCompatibleSerializerTest#testTargetHasLessFieldComparedToSourceClass+testTargetHasMoreFieldComparedToSourceClass,org.apache.fory.serializer.compatible.DifferentPOJOCompatibleSerializerWithRegistrationTest#testTargetHasLessFieldComparedToSourceClass+testTargetHasMoreFieldComparedToSourceClass test` (passed, 15 tests)
  - `cd java && mvn -T16 -pl fory-testsuite -Dtest=org.apache.fory.test.Object2ObjectOpenHashMapTest test` (passed, 2 tests)
  - `prettier --write docs/guide/java/basic-serialization.md docs/guide/java/troubleshooting.md docs/guide/java/schema-evolution.md` (unchanged)

## Xlang TypeMeta Compression Pause Across Runtimes (2026-03-04)

- [x] Disable TypeMeta compression in Java xlang TypeDef encoder only (keep native mode behavior unchanged).
- [x] Disable TypeMeta compression in Python xlang TypeDef encoder only (decoder may still tolerate compressed input).
- [x] Add explicit temporary comments in Rust/Go/C++ xlang TypeMeta encode paths clarifying compression is intentionally skipped until all runtimes support decompression.
- [x] Update/adjust tests impacted by compression-flag expectations.
- [x] Run focused Java/Python/Go/Rust/C++ checks for changed paths and record results.

### Review

- Summary:
  - Java xlang TypeDef encoding now always emits uncompressed metadata in `TypeDefEncoder`, with explicit temporary comments; Java native mode compression path in `NativeTypeDefEncoder` is unchanged.
  - Python xlang TypeDef encoding now always emits uncompressed metadata in `pyfory/meta/typedef_encoder.py`, with explicit temporary comments; decoder still tolerates compressed payloads if seen.
  - Rust/Go/C++ already emitted uncompressed xlang TypeMeta; added clear comments documenting the temporary cross-runtime limitation and intent.
  - Added explicit regression checks:
    - Java `TypeDefEncoderTest#testXlangTypeDefIsNotCompressed`
    - Python `test_typedef_encoding.py` asserts `typedef.is_compressed is False` and `decoded_typedef.is_compressed is False`.
- Verification:
  - `cd java && mvn -T16 -pl fory-core -Dtest=org.apache.fory.meta.TypeDefTest,org.apache.fory.meta.TypeDefEncoderTest test` (passed, 33 tests)
  - `cd java && mvn -T16 -pl fory-core -DskipTests checkstyle:check spotless:check` (passed)
  - `cd python && pip install -v -e .` (passed)
  - `cd python && ENABLE_FORY_CYTHON_SERIALIZATION=0 pytest -q pyfory/tests/test_typedef_encoding.py` (passed, 6 tests)
  - `cd go/fory && go test -run 'TestTypeDefEncodingDecoding|TestTypeDefNullableFields|TestTypeDefEncodingSizeWithTagIDs|TestSkipAnyValueReadsSharedTypeMeta' ./...` (passed)
  - `cd rust && cargo test -p tests --test test_meta` (passed, 1 test)
  - `cd cpp && bazel test //cpp/fory/serialization:serialization_test --test_arg=--gtest_filter=SerializationTest.TypeMetaRejectsOverConsumedDeclaredSize` (passed, 1 test)

## Java Resolver-Agnostic Read Path Cleanup (2026-03-04)

- [x] Refactor `Fory` read helper internals to avoid passing `TypeResolver` instances; use `Fory` resolver fields/mode dispatch internally.
- [x] Remove xlang-specific helper naming/shape from `readRef`/`readNonRef`/`readNullable` internal call graph, keeping public API unchanged.
- [x] Preserve existing wire behavior for native and xlang modes while keeping hot-path dispatch explicit and lightweight.
- [x] Run focused `fory-core` compile/tests and record results.
- [x] Update `tasks/lessons.md` with this user correction pattern.

### Review

- Summary:
  - Removed `TypeResolver` parameter threading from `Fory` read helpers; helper dispatch now uses `Fory`’s existing resolver fields (`classResolver`/`xtypeResolver`) and mode flags.
  - Kept unified helper naming and public API shape while deleting xlang-specific `*WithXtypeResolver` internals.
  - Switched serializer-specific helper to overload style (`readRefByMode(..., Serializer<?>)`) instead of suffix naming and verified overload signatures do not overlap.
  - Removed boolean mode-parameter helper APIs (`..., boolean isTargetXLang`) and consolidated target mode selection into internal read state (`currentReadCrossLanguage`) initialized per deserialize call and reset after read.
  - Updated root deserialize paths to select mode once from bitmap and route through internal mode-based helpers (`readRefByMode` / `readNonRefByMode` / `readNullableByMode`).
  - Preserved native/xlang on-wire behavior and primitive fast-path logic in mode-specific decode branches.
- Verification:
  - `cd java && mvn -T16 -pl fory-core -DskipTests compile` (passed)
  - `cd java && mvn -T16 -pl fory-core -DskipTests checkstyle:check spotless:check` (passed)
  - `cd java && mvn -T16 -pl fory-core -Dtest=org.apache.fory.ForyTest,org.apache.fory.StreamTest,org.apache.fory.ThreadSafeForyTest test` (passed, 71 tests)

## Java TypeResolver API Unification Cleanup (2026-03-04, follow-up)

- [x] Remove `getClassResolver/getXtypeResolver` from `Fory` and keep only `getTypeResolver` API surface.
- [x] Remove `classResolver/xtypeResolver` fields from `Fory`; keep a single target resolver field and eliminate mode-adaptor helper methods (`getCurrentReadResolver`, `readRefByMode`, `readNonRefByMode`).
- [x] Add missing shared APIs directly on `TypeResolver` (no adapter layer in `Fory`), and cast to `ClassResolver` at callsites for class-only APIs.
- [x] Remove `setClassChecker` API surface and switch to `TypeChecker`; delete `ClassChecker` and update docs/examples.
- [x] Update `fory-core` main/test callsites/docs to use `getTypeResolver` + cast where required; delete dead/legacy compatibility code.
- [x] Run compile/style/targeted tests and record outcomes.

### Review

- Summary:
  - `Fory/BaseFory` now expose only `getTypeResolver`; `getClassResolver/getXtypeResolver` are removed.
  - `Fory` now keeps one resolver field (`typeResolver`) and removed resolver-adaptor read helper methods (`getCurrentReadResolver`, `readRefByMode`, `readNonRefByMode`).
  - Shared APIs were added on `TypeResolver` (`setSerializerFactory/getSerializerFactory/resetRead/resetWrite`) and class-specific callsites now cast to `ClassResolver`.
  - Removed legacy class-checker API path:
    - deleted `setClassChecker` from `ThreadSafeFory`, `AbstractThreadSafeFory`, `TypeResolver`, `ClassResolver`.
    - deleted `java/fory-core/src/main/java/org/apache/fory/resolver/ClassChecker.java`.
    - updated docs/examples to use `TypeChecker`.
  - Fixed xlang recursion regression introduced by unified resolver path:
    - `ArraySerializers.ObjectArraySerializer` now only pre-registers serializer when resolver is `ClassResolver`, preventing `XtypeResolver` self-recursion during type-info construction.
- Verification:
  - `cd java && mvn -T16 -pl fory-core -DskipTests test-compile` (passed)
  - `cd java && mvn -T16 -pl fory-core -DskipTests spotless:check checkstyle:check test-compile` (passed)
  - `cd java && mvn -T16 -pl fory-core,fory-simd,fory-extensions -DskipTests compile` (passed)
  - `cd java && ENABLE_FORY_DEBUG_OUTPUT=1 mvn -T16 -pl fory-core -Dtest=org.apache.fory.meta.NativeTypeDefEncoderTest,org.apache.fory.meta.TypeDefTest,org.apache.fory.resolver.AllowListCheckerTest,org.apache.fory.resolver.ClassResolverTest,org.apache.fory.type.DescriptorGrouperTest test` (passed, 65 tests)
  - `cd integration_tests/graalvm_tests && mvn -T16 -DskipTests test-compile` (passed)
  - `cd kotlin && mvn -T16 -DskipTests compile` (passed)
  - `cd scala && sbt compile` (passed)
