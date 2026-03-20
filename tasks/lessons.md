# Lessons

## 2026-03-20

- User correction: when creating branches in this repo, do not add the default `codex/` prefix unless the user explicitly wants it.
- Prevention rule:
  - Before creating or renaming a branch, prefer a plain descriptive branch name unless the user explicitly asks for a specific prefix.
  - If the task includes branch/PR creation, restate the chosen branch name in the work log before pushing so any naming preference mismatch is caught early.
- User correction: for the config-hash lifecycle, do not preserve late-registration compatibility on the JVM; first hash access, first serializer use, or `ensureSerializersCompiled()` must finalize the hash and forbid later registration.
- Prevention rule:
  - For registration/codegen cache isolation work, model the freeze point from the actual lifecycle contract first, even if existing tests or behavior currently allow later mutation.
  - If the user says compatibility can be ignored, remove the legacy allowance instead of keeping a split JVM/native behavior.
- User correction: hash lifecycle ownership belongs in resolver state, not in `Fory` wrapper bookkeeping; avoid parallel `getConfigHash`/`getStableConfigHash` APIs and action-marker constants when the final registered state can be hashed directly.
- Prevention rule:
  - Keep registration/hash state in `TypeResolver`/resolver-owned registries and treat `Fory` as a thin facade for public APIs.
  - When a hash represents resolved registration state, hash the resulting resolver state rather than wrapper call-path markers or duplicated API variants.
- User correction: in `copy` tests, do not expect shared-reference preservation just because serialization ref tracking is enabled; `copy` reference semantics are controlled by `withRefCopy(true)`.
- Prevention rule:
  - For `copy` behavior, distinguish serialization ref tracking from copy ref tracking before writing identity assertions.
  - Use `assertSame` only when the test enables ref-copy or when the serializer is immutable and `copy` returns the original instance by contract.
- User correction: tests added for config-hash lifecycle and codegen cache isolation do not belong under `xlang/RegisterTest` when they are not exercising xlang-specific behavior.
- Prevention rule:
  - Place new tests by subsystem ownership, not by whichever helper class is convenient; serializer/codegen registration coverage should live under `org.apache.fory.serializer` unless the scenario depends on xlang semantics.
  - Before finalizing a test addition, do a quick package-scope review: ask whether the assertions would still make sense in another language mode, and if yes, move the test to the more general test suite.
- User correction: in `TimeSerializersTest`, `Instant` and `Duration` copy assertions should use identity checks.
- Prevention rule:
  - When a serializer extends `ImmutableSerializer`, default copy semantics are identity-preserving unless overridden; prefer `assertSame` in copy tests for those types.
  - Separate immutable time types from mutable `Date`/`java.sql.Date`/`java.sql.Time` when writing mixed time-serializer assertions.

## 2026-02-26

- User correction: Panic-safety fixes must not regress throughput or allocation behavior; `ReadContext`/`WriteContext` reuse is mandatory.
- Prevention rule:
  - For panic-safety work in Rust hot paths, preserve TLS context reuse first, then add safety via scoped guards/owned fallbacks rather than per-call context allocation.
  - Before finalizing, run focused perf-sensitive path tests and verify no new per-call allocation is introduced in entrypoint code.
- User correction: pre-call reset alone is insufficient; context must also be reset after successful calls so ref/meta state does not outlive one operation.
- Prevention rule:
  - For context reuse paths, enforce reset at both call entry (panic recovery) and call exit (normal lifecycle cleanup).
- User correction: always fetch `apache` before running A/B baseline tests against `apache/main`.
- Prevention rule:
  - Before any baseline-vs-branch benchmark or micro-comparison, run `git fetch apache --prune` first, then build baseline from fresh `apache/main`.
- User correction: prefer clean C++ architecture and naming over literal Java structure mirroring (e.g., use `serialization/stream.h`, avoid util-level stream header sprawl).
- Prevention rule:
  - For cross-language feature ports, match protocol behavior but redesign interfaces idiomatically for C++ layering boundaries.
  - Keep util abstractions self-contained and expose serialization APIs under `cpp/fory/serialization/*` with clear names.

## 2026-03-04

- User correction: Do not introduce explicit `if (xlang)` / `if (!xlang)` protocol branching in unified read/write paths when protocol is already shared.
- Prevention rule:
  - For xlang/native API unification work, treat wire-format compatibility points as a single protocol first; only branch where format actually differs and document that boundary before coding.
  - During refactor review, reject any newly added xlang boolean branch in hot read/write methods unless backed by a protocol-spec difference.
- User correction: protocol-level TypeMeta changes must be applied consistently across all xlang runtimes (not just Java), while keeping native-mode behavior unchanged.
- Prevention rule:
  - For any xlang wire-format toggle (compression flags, field/type encoding, headers), run a cross-runtime impact scan first (`java/python/go/rust/cpp/csharp/swift/javascript`) and update all relevant encoders/decoders or document why each runtime is already aligned.
  - Before finalizing, explicitly verify "xlang changed, native unchanged" in both code paths and tests.
- User correction: renaming xlang-specific helper methods is not API unification if method signatures still hard-code `XtypeResolver`.
- Prevention rule:
  - For API unification refactors, validate helper method signatures and parameter types are mode-agnostic (`TypeResolver`/shared abstractions), not just method names.
  - Add a final review check: if any newly introduced private/public method still encodes mode in its type signature (`Xtype*`, `*CrossLanguage*`), the unification is incomplete.
- User correction: do not thread/pass `TypeResolver` instances through hot read/write helpers when `Fory` already holds resolver fields.
- Prevention rule:
  - For hot-path helper refactors, use `Fory`'s resolver fields/mode-local dispatch (`classResolver`/`xtypeResolver`) instead of passing resolver objects as method parameters.
  - Add a final perf-oriented review pass to remove avoidable polymorphic parameter dispatch in tight serialization/deserialization paths.
- User correction: prefer Java overload-style helper names over suffix-based variants (e.g., remove `BySerializer` suffix).
- Prevention rule:
  - When adding private helper variants in Java, prefer overloads on parameter types and keep a single method family name.
  - Before finalizing, run a signature-overlap scan to ensure overloads are unambiguous and no accidental erasure conflicts are introduced.
- User correction: do not expose mode flags as helper parameters in unified paths (e.g., `..., boolean isTargetXLang`).
- Prevention rule:
  - For unified hot-path helpers, avoid boolean mode parameters in method signatures; select mode once into internal state and keep helper signatures mode-agnostic.
  - Final review check: reject any new private helper signature that adds mode booleans for xlang/native dispatch.
- User correction: remove `setClassChecker`; use `TypeChecker` only.
- Prevention rule:
  - Do not add or keep parallel checker APIs (`ClassChecker` + `TypeChecker`) during resolver unification; keep a single checker contract (`TypeChecker`) across `Fory`, `ThreadSafeFory`, docs, and examples.
  - Before commit, run a repo-wide grep for removed API names (`setClassChecker`, `ClassChecker`) and clean all code/docs/native-image references.

## 2026-03-07

- User correction: Do not frame Fory's language coverage as a fixed endpoint like "seven languages" in external content; emphasize current support without implying the set is closed.
- Prevention rule:
  - For blog posts, docs, and release content about multi-language support, avoid count-based headlines unless the count itself is the point and is unlikely to age poorly.
  - Prefer wording such as "across languages" or "current IDL targets include ..." and do a final pass to remove fixed-count framing from titles, slugs, and summaries.

## 2026-03-08

- User correction: In Schema IDL compatible mode, schema evolution matching is by field id, not by field name.
- Prevention rule:
  - For external docs and blog posts about schema evolution, verify matching semantics directly against current compiler/runtime docs before describing compatibility behavior.
  - Do a final terminology pass for serialization articles to catch stale wording like "matched by name" versus the actual protocol rule.
- User correction: Remove unused registration-helper imports from examples; only import symbols that are actually referenced in the snippet.
- Prevention rule:
  - For article/example code, run a final import-usage scan and remove helper imports that are not referenced in the shown snippet.
  - Prefer minimal imports in docs examples so the snippet reads as executable and not template-generated.
- User correction: Avoid awkward compiler jargon like `Fory targets` in prose; prefer clearer phrases such as `Fory-supported languages`.
- Prevention rule:
  - For blog/docs prose, expand ambiguous jargon like `targets` into reader-facing language (`supported languages`, `target languages`, `codegen backends`) based on the actual meaning.
  - Do a final readability pass on headings, TL;DR text, and CLI captions to replace internal shorthand with natural English.
- User correction: When comparing Fory IDL with protobuf/FlatBuffers in external content, break the argument into concrete dimensions like shared refs, polymorphism, and API idiomaticness, and mention meaningful exceptions such as `prost` in Rust.
- Prevention rule:
  - For comparison sections in blogs/docs, avoid broad one-bucket critiques; split the comparison into distinct technical limitations so each claim is specific and defensible.
  - When a competing ecosystem has a real exception or stronger implementation in one language, call it out explicitly instead of overstating the generalization.
- User correction: For the idiomatic-API comparison, make the practical cost explicit: users often need extra application-layer conversion between generated transport types and real domain objects.
- Prevention rule:
  - In external comparisons about generated APIs, do not stop at "not idiomatic"; state the concrete developer cost such as added conversion layers or wrapper-model leakage into application code.
  - Prefer phrasing that ties API shape to workflow impact so the comparison explains why the difference matters in practice.
- User correction: When wording is refined in one section, propagate the same nuance through repeated terminology and summary tables instead of leaving older shorthand behind.
- Prevention rule:
  - After revising comparison prose, run a consistency sweep across tables, captions, and repeated phrases (`targets`, `wrappers`, `generated registration`) so summaries do not contradict or oversimplify the body.
  - Treat tables as claims, not decoration; if the body adds an exception or nuance, update the table row to match before finalizing.
- User correction: If a title makes a strong claim, strengthen the body with an explicit definition and concrete support rather than hedging with phrases like `to the best of our knowledge`.
- Prevention rule:
  - For strong external titles, add an early paragraph that defines exactly what the claim means in technical terms, then support it with a short list of concrete evidence.
  - Avoid fallback hedges the user has explicitly rejected; strengthen the argument itself instead of weakening the wording.
- User correction: Prefer narrower, more precise title language like `serialization IDL` and `API ergonomics` over broader phrases like `IDL` or `cross-language ergonomics` when that better matches the article's actual argument.
- Prevention rule:
  - For external titles and subtitles, choose the narrowest phrasing that the body can clearly support.
  - When a title term is tightened, update the body definition paragraph so it uses the same wording and scope.
- User correction: When listing supported languages in external content, an explicit list can still be followed by `and more` so it reads as illustrative rather than closed.
- Prevention rule:
  - For language lists in blog posts and summaries, prefer open-ended wording such as `Java, Python, ... and more` when the intent is illustrative coverage rather than an exhaustive set.
  - If a sentence already uses a language list, make sure the surrounding wording still reads naturally after adding the open-ended qualifier.
- User correction: Avoid over-familiar or emotionally loaded opener phrasing like `you know the pain` / `is brutal` in Apache Fory articles; use neutral, professional framing.
- Prevention rule:
  - For intros in project docs/blog posts, prefer calm technical framing over emotional or overly conversational language.
  - Do a tone pass on opening paragraphs to remove phrases that could feel presumptive, uncomfortable, or marketing-heavy.
- User correction: Keep comparison bullets concise; do not let one item accumulate every caveat in a single long paragraph.
- Prevention rule:
  - For list items in external articles, compress each point to one core claim plus at most one clarifying caveat.
  - If a comparison needs more nuance, move the details to a later section instead of overloading the opener.
- User correction: In the "Object Graph Gap" framing, keep the argument tied to schema-level limitations and use safer scope words like `most existing serialization IDLs` instead of absolute claims.
- Prevention rule:
  - When describing competitor limitations in external content, prefer precise schema-level language (`shared identity`, `schema-level representation`, `transport-first types`) over loose shorthand like `serialize it twice`.
  - Keep section headings and bullets aligned: if the section is about object-graph gaps, make each point clearly connect back to graph semantics or the resulting application-model mismatch.
- User correction: Be precise about Protobuf `Any`: it is message-based and uses a type URL, but `type.googleapis.com/...` is the default prefix rather than a required exact URL form.
- Prevention rule:
  - When mentioning Protobuf `Any` in docs or comparisons, say `type URL` unless the exact default prefix matters to the point being made.
  - Avoid turning common defaults into hard requirements in external technical writing.

## 2026-03-09

- User correction: When a draft is based on a specific published Fory article, include that canonical article URL in the final reference list.
- Prevention rule:
  - For blog drafts that cite a prior Fory post as reference context, add the exact published URL under `References`.
  - Run a final references checklist before delivery: official docs links, repository link (if used), and referenced canonical blog URLs.
- User correction: When asked to make a summary line “more fancy,” upgrade the prose style without losing technical precision.
- Prevention rule:
  - For headline/TL;DR rewrites, preserve core technical claims first, then improve rhythm and wording (for example, “first-class semantics,” “foundation,” “stitching”) instead of adding vague hype.
  - Run a quick meaning check after stylistic edits: same promises, same scope, clearer impact.
- User correction: Prefer TL;DR phrasing that starts with a direct reader scenario pattern: “If you already have X and want Y, with Fory compiler you can Z.”
- Prevention rule:
  - For Fory migration TL;DR lines, use reader-first flow (`already have` -> `want` -> `with Fory compiler you can`) before supporting detail.
  - Fix user-provided style text for spelling/grammar while preserving the requested sentence structure.
- User correction: Keep TL;DR wording concise by avoiding duplicate compiler/action phrases (for example “with compiler ... then compile will ...”).
- Prevention rule:
  - In TL;DR rewrites, use one clear action verb chain (`lets you keep/add/generate`) instead of repeating tool references.
  - Normalize branding/case in prose (`Fory`, not `fory`) unless a command/token intentionally uses lowercase.

## 2026-03-10

- User correction: NuGet-rendered package docs should link to the published Fory docs site instead of GitHub markdown pages.
- Prevention rule:
  - When a repository README is used as a NuGet package readme, prefer `https://fory.apache.org/docs/...` links for user documentation rather than `github.com/.../*.md`.
  - Before finalizing doc-site links, verify the exact public route; if the stable path is not live yet, use the live published path and call out the difference.
- User correction: for the C# package readme, prefer the stable docs URL `https://fory.apache.org/docs/guide/csharp/` even if it is not live yet, because that is the intended long-term link.
- Prevention rule:
  - When the user specifies a canonical future docs URL for package-facing documentation, use that exact URL instead of falling back to `/docs/next/...`.
  - Treat package readme links as canonical destinations, not just currently live routes, when the user explicitly confirms the stable path.
- User correction: repository release helpers should live under `ci/`, not inside language subdirectories.
- Prevention rule:
  - Put reusable publish/release automation in `ci/` unless the user explicitly wants a language-local script.
  - When moving a release script into `ci/`, update its internal path resolution so it still targets the correct language workspace and artifact directory.

## 2026-03-11

- User correction: `buffer.pyx`/`pyfory/buffer*.so` is already removed, so fixes must not imply that module still exists.
- Prevention rule:
  - For Python wheel pipeline changes, derive extension-module paths from current build targets (`BUILD`, setup/packaging config) or wheel payload discovery, not historical module names.
  - In verification notes, avoid synthetic examples that look like canonical current module names unless they match the repository's actual extension targets.
- User correction: In PR templates, when the checklist is required, do not only link to it; explicitly ask contributors to paste the completed checklist in the PR body.
- Prevention rule:
  - For required checklist flows, wording must require in-PR inclusion (`paste/include here`) rather than implicit external completion.
  - Keep the policy link for canonical content, but pair it with an explicit action so reviewers can verify compliance from the PR description alone.
- User correction: The heading qualifier `(required when AI assistance = yes)` is redundant/ambiguous; align wording to the policy term `substantial AI assistance`.
- Prevention rule:
  - Avoid redundant requirement qualifiers in section titles when checklist items already scope behavior.
  - Reuse exact policy terminology in headings (`substantial AI assistance`) to avoid interpretation drift.
- User correction: Even if wording is more precise, avoid making titles longer when the same scope is already clear from checklist lines.
- Prevention rule:
  - Prefer the shortest clear section heading and keep detailed conditions in nearby checklist items or helper text.
  - For template copy edits, do a quick length/readability pass before finalizing wording changes.
- User correction: In checklist affirmations, prefer `included` over `pasted` for more natural and less tool-specific wording.
- Prevention rule:
  - For PR template attestations, use neutral action verbs (`included`, `provided`) instead of implementation-specific wording (`pasted`) unless explicitly required.
- User correction: Use `AI Contribution Checklist` label instead of `Contributor Checklist` for clearer AI-specific context in PR/disclosure text.
- Prevention rule:
  - When linking AI-policy checklist content from PR/contribution docs, prefer explicit AI-scoped link labels (`AI Contribution Checklist`) even if anchor targets remain unchanged.
