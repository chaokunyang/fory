<!--
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Apache Fory — Threat Model (v0 draft)

## §1 Header

- **Project:** Apache Fory (`apache/fory`), `main`, against which this draft was written. Fory is a multi-language serialization framework (Java, C++, Python, Go, Rust, JavaScript, Kotlin, Scala, Swift, Dart, C#).
- **Date:** 2026-06-02. **Status:** draft — for Apache Fory PMC review. **Author:** ASF Security team (drafted via the Scovetta threat-model rubric), for PMC ratification.
- **Version binding:** versioned with the project; a report against Fory version *N* is triaged against the model as it stood at *N*, not at HEAD.
- **Reporting cross-reference:** findings that violate a §8 property should be reported privately per the ASF process (`security@apache.org` → `private@fory.apache.org`); findings under §3 or §9 are closed citing this document.
- **Provenance legend:** *(documented)* = stated in Fory's own docs/repo; *(maintainer)* = confirmed by a Fory PMC member through this process; *(inferred)* = reasoned from architecture/domain knowledge, not yet confirmed — every *(inferred)* claim has a matching §14 open question.
- **Draft confidence:** ~20 documented / 0 maintainer / ~26 inferred.
- **What Fory is:** Apache Fory is a high-performance, multi-language object/data serialization framework. An application uses it in-process to serialize its objects to bytes and deserialize bytes back into objects, either within one language ("native" mode) or across languages ("xlang" mode), with optional zero-copy and a row format. *(documented — README, docs/guide)*

## §2 Scope and intended use

- **Primary use:** an **in-process library** linked into a host application that calls `serialize()` / `deserialize()` on its own data types. *(documented — guides)*
- **It is not a network service or daemon.** It has no listening surface, no auth, no users — the embedding application owns where the bytes come from and go. *(inferred)*
- **Caller / trust level:** a single caller — the embedding application — which is **trusted** (it links the library and registers its types). The security-relevant question is not "who calls Fory" but **"where do the bytes handed to `deserialize()` come from"** — trusted producer, or attacker-controlled. *(inferred; the registration guidance is documented)*

**Component-family table** *(in/out of this model):*

| Family | Entry point | Notes | In model? |
| --- | --- | --- | --- |
| Object-graph serialization (native, per language) | `fory.serialize` / `deserialize` | the core; instantiates registered types from bytes | **In** *(documented)* |
| Cross-language (xlang) serialization | xlang `serialize`/`deserialize` | type mapping across languages | **In** *(documented)* |
| Row format / zero-copy | row encoders | reads fields in place from a buffer | **In** *(documented)* |
| Class/type registration + "secure mode" | `requireClassRegistration`, `register(...)` | the primary defense | **In** *(documented)* |
| Per-language implementations | `java/`, `cpp/`, `python/`, `go/`, `rust/`, `javascript/`, `kotlin/`, `scala/`, `swift/`, `dart/`, `csharp/` | each is a separate impl of the same model | **In** — but memory-safety profile differs by language (see §5/§8) *(documented: dirs exist)* |
| `examples/`, `benchmarks/`, `integration_tests/` | demo/bench/test | not production surface | **Out** *(see §3)* |

## §3 Out of scope (explicit non-goals)

- **The integrity / authenticity / confidentiality of the serialized bytes.** Fory deserializes what it is given; it does not authenticate, MAC, or encrypt payloads. If bytes can be tampered with in transit/at rest, that is the application's problem to solve (sign/encrypt before handing to Fory). *(inferred)*
- **Anything when the caller disables class registration on an untrusted payload source.** `requireClassRegistration(false)` is a documented, deliberately-available footgun; using it against attacker-controlled bytes is out of the model's protection (see §5a/§9). *(documented — config: "Disabling may allow unknown classes to be deserialized, potentially causing security risks")*
- **The behaviour of the application's own registered classes.** Fory instantiates and populates registered types; if a registered class has dangerous side effects in its constructors/setters/finalizers, that is the application's design, not Fory's. *(inferred)*
- **`examples/`, `benchmarks/`, `integration_tests/`** — shipped but not a production trust surface. *(inferred)*

## §4 Trust boundaries and data flow

- **The trust boundary is the byte buffer passed to `deserialize()`** (and the row-format buffer). Everything Fory does on the serialize side operates on the application's own in-memory objects (trusted); the deserialize side is where attacker-controlled bytes, if any, enter. *(inferred)*
- **Data flow:** untrusted bytes → format/header parse → (class id / type resolution → **registration check**) → field decode → object graph construction → returned to caller. The registration check is the gate that decides whether an arbitrary type may be instantiated. *(inferred; registration mechanism documented)*
- **Reachability precondition:** a deserialize-side finding is **in-model** only if it is reachable from the byte buffer under the **default secure configuration** (`requireClassRegistration(true)`). A finding that requires `requireClassRegistration(false)`, or that requires the *serialize* side to be fed attacker-controlled live objects, is out-of-model (§5a / trusted-input). *(inferred)*

## §5 Assumptions about the environment

- **In-process, no ambient I/O.** Fory does not (by design) open sockets, spawn processes, or read the network; it operates on in-memory buffers handed to it. *(inferred — high-priority confirmation; negative claim)*
- **Per-language memory model differs.** In managed runtimes (Java, Python, Go, JS, …) memory safety is the runtime's; in the **C++** (and unsafe-Rust FFI) paths, malformed input reaching the decoder is a memory-safety surface in a way it is not on the JVM. The model's "memory safety on malformed input" property is therefore language-conditional (see §8). *(inferred)*
- **Codegen / JIT:** on ordinary JVMs Fory generates serializer code at runtime (`codeGenEnabled` default true); disabled on Android / GraalVM native image. This is a performance mechanism over the application's own registered types, not a path for executing attacker bytes. *(documented — config table)*

## §5a Build-time and configuration variants

The security envelope is set by runtime configuration, not build flags. The load-bearing knobs *(documented — docs/guide/java/configuration.md)*:

| Knob | Default | Effect on the model |
| --- | --- | --- |
| `requireClassRegistration` | **`true`** (secure) | When true, only registered types are deserialized — the primary defense against deserializing arbitrary/gadget classes. Disabling "may allow unknown classes to be deserialized, potentially causing security risks." |
| `maxDepth` | **`50`** | Bounds deserialization recursion depth; "can be used to refuse deserialization DDOS attack." |
| `deserializeUnknownClass` | `true` in compatible mode, else `false` | Whether data for unknown/non-existent classes is skipped/deserialized. |
| `compatible` | xlang: `true`; native: `false` | Schema forward/backward compatibility. |
| `suppressClassRegistrationWarnings` | `true` | Registration warnings are useful for security audit but suppressed by default. |

**The default is the *secure* posture here** (registration required) — the inverse of the usual insecure-default case. The model's §8 properties hold *under the defaults*; a report that only manifests under `requireClassRegistration(false)` is `OUT-OF-MODEL: non-default-build`. Confirm this framing with the PMC (§14).

## §6 Assumptions about inputs

Per-entry-point trust table *(registration mechanism + defaults documented; trust framing inferred):*

| Entry point | Input | Attacker-controllable? | Caller must enforce |
| --- | --- | --- | --- |
| `deserialize(bytes)` / `deserialize(bytes, Class)` | serialized byte buffer | **yes, if the application sources bytes from an untrusted producer** | keep `requireClassRegistration(true)`; register only safe types; integrity-check bytes upstream |
| row-format readers | buffer | **yes** (same as above) | same |
| `serialize(obj)` | a live application object | no — the app's own trusted object | n/a |
| `register(Class, …)` | type registered at setup | no — controlled by the app developer | register only types safe to instantiate from untrusted data |

- **Size/shape/rate:** `maxDepth` (default 50) bounds nesting; whether total allocation / output size is otherwise bounded against a hostile payload is open (see §8 resource line). *(maxDepth documented; broader bound inferred)*

## §7 Adversary model

- **Primary adversary:** a party who controls the **serialized bytes** an application later passes to `deserialize()` (e.g. data arriving over a network the app feeds to Fory, or persisted data an attacker can tamper with). Goal: instantiate dangerous types (gadget-chain RCE), corrupt memory in the native paths, or exhaust CPU/memory. *(inferred — the canonical serialization-framework adversary)*
- **Capabilities:** can craft arbitrary/malformed byte buffers; cannot change the application's Fory configuration or its registered-type set (those are set by the trusted app at startup). *(inferred)*
- **Out of scope:** an attacker who controls the embedding application, its configuration, or the objects passed to `serialize()` — already trusted; an attacker who has set `requireClassRegistration(false)` themselves. *(inferred)*

## §8 Security properties the project provides

*(Registration + depth defenses documented; the guarantees framed below are for PMC confirmation.)*

- **Registered-type-only instantiation (default).** With `requireClassRegistration(true)` (the default), deserialization instantiates only types the application registered, so attacker bytes cannot drive Fory to construct an arbitrary class. *Violation symptom:* an unregistered/unexpected type is instantiated from input under the default config. *Severity:* security-critical (this is the deserialization-RCE defense). *(documented that registration is required by default + that disabling causes risk; the unbypassability guarantee is the claim to confirm)*
- **Bounded recursion depth.** Deserialization beyond `maxDepth` (default 50) throws rather than recursing unbounded. *Violation symptom:* stack overflow / unbounded recursion from crafted nesting under the default. *Severity:* security-critical (DoS). *(documented — config table)*
- **Memory safety on malformed input — language-conditional.** In managed-runtime implementations, malformed bytes yield an exception, not memory corruption. For the **C++** implementation this is the load-bearing property to confirm (malformed-input fuzzing of the C++ decoder). *Violation symptom:* OOB read/write, crash. *Severity:* security-critical. *(inferred — confirm per language)*
- **Resource bounds beyond depth — UNSPECIFIED.** Whether a crafted payload can force large allocation / CPU blowup within the depth limit (e.g. huge declared collection sizes) is a bug or expected is open; the model needs a line (§14). *(inferred; maxDepth documented)*

## §9 Security properties the project does *not* provide

*(Highest-value section for integrators.)*

- **No protection when class registration is disabled.** `requireClassRegistration(false)` deliberately allows deserializing unknown classes — using it on untrusted input re-opens the classic deserialization-gadget RCE surface. This is the caller's choice, documented as risky. *(documented — config)*
- **No payload authentication or confidentiality.** Fory does not verify that bytes came from a trusted producer or that they are unmodified; it is not a MAC, signature, or cipher. *(inferred)* **False friend:** a successful round-trip / schema-compatibility check is *not* an integrity guarantee against a malicious producer.
- **Not a sandbox for registered types.** Registering a class authorizes Fory to instantiate it from bytes; if that class's construction has side effects, Fory does not contain them. *(inferred)*
- **Cross-language type-confusion is the integrator's concern** in xlang mode — relying on the peer to send a compatible schema is a trust assumption between the two ends, not something Fory enforces against a hostile peer. *(inferred)*
- **Well-known classes left to the caller:** deserialization-gadget attacks (defended by registration, *if left on*), decompression/allocation bombs (partially bounded by `maxDepth`), and integrity attacks on the byte stream. *(inferred)*

## §10 Downstream responsibilities (the embedding application)

- **Keep `requireClassRegistration(true)`** whenever any deserialized bytes could be attacker-influenced (the documented production guidance). *(documented)*
- **Register only types that are safe to instantiate from untrusted data**; do not register types with dangerous construction side effects. *(inferred)*
- **Authenticate / integrity-check / decrypt** untrusted bytes *before* handing them to `deserialize()` — Fory will not. *(inferred)*
- **Tune `maxDepth`** to the application's real object depth rather than disabling it. *(inferred)*
- **In xlang mode, treat the peer's schema as a trust relationship** you control, not something Fory polices. *(inferred)*

## §11 Known misuse patterns

*(Draft one-liners — expand before publishing.)*

- Setting `requireClassRegistration(false)` for convenience, then deserializing network/user data. *(documented as risky)*
- Treating Fory deserialization of untrusted bytes as safe without integrity-checking the bytes first. *(inferred)*
- Registering broad/dangerous types (or whole packages) to "make it work", widening the gadget surface. *(inferred)*
- Assuming the C++ decoder is as forgiving of malformed input as the JVM one. *(inferred)*

## §11a Known non-findings (recurring false positives)

*(Seed list — confirmations here are the highest-leverage scan-suppression input.)*

- "Fory can deserialize arbitrary classes → RCE" — **only** with `requireClassRegistration(false)`; under the default (`true`) it cannot. A report that assumes registration is off is `OUT-OF-MODEL: non-default-build` unless the PMC says otherwise. *(documented)*
- "No signature/MAC/encryption on the serialized format" — by-design; integrity/confidentiality is the caller's (§9/§10). *(inferred)*
- "Unbounded recursion on nested input" — bounded by `maxDepth` (default 50). *(documented)*
- "Registered class X does something dangerous when constructed" — the application's registration choice (§3/§10), not a Fory bug. *(inferred)*
- "Reflection / dynamic codegen used at runtime" — `codeGenEnabled` operates over the app's own registered types, not attacker bytes (§5). *(documented config; framing inferred)*

## §12 Conditions that would change this model

- A change to the **default** of `requireClassRegistration` or `maxDepth`. *(documented knobs)*
- A new deserialization entry point or a new language implementation with a different memory-safety profile. *(inferred)*
- Fory gaining any I/O / network surface (it would stop being a pure in-process library). *(inferred)*
- A report that cannot be routed to a single §13 disposition → revise the model.

## §13 Triage dispositions

| Disposition | Meaning | Licensed by |
| --- | --- | --- |
| `VALID` | Violates a §8 property under the **default** config via attacker-controlled bytes (e.g. unregistered-type instantiation with registration on; unbounded recursion within maxDepth; C++ memory corruption on malformed input). | §8, §6, §7 |
| `VALID-HARDENING` | No §8 property broken, but a §11 misuse is easy enough to harden (e.g. a safer default, a louder warning). | §11 |
| `OUT-OF-MODEL: trusted-input` | Requires attacker control of the serialize-side objects, the registered-type set, or the Fory config. | §6, §7 |
| `OUT-OF-MODEL: non-default-build` | Only manifests with `requireClassRegistration(false)` or another discouraged §5a setting. | §5a |
| `OUT-OF-MODEL: unsupported-component` | Lands in `examples/`, `benchmarks/`, `integration_tests/`. | §3 |
| `BY-DESIGN: property-disclaimed` | Concerns a §9-disclaimed property (no payload auth/encryption, not a sandbox for registered types, xlang peer trust). | §9 |
| `KNOWN-NON-FINDING` | Matches a §11a entry. | §11a |
| `MODEL-GAP` | Cannot be cleanly routed — triggers a §12 revision. | §12 |

## §14 Open questions for the maintainers

**Wave 1 — scope & the registration framing:**

1. Confirm Fory is modeled as an **in-process library** with no ambient I/O (no sockets/processes/network) — the negative-side-effects inventory in §5. Proposed: yes. → §2/§5.
2. **The core ruling:** with `requireClassRegistration(true)` (default), is "only registered types are instantiated from untrusted bytes" a property Fory **commits to** (so a bypass is `VALID`/security-critical)? And is a finding that requires `requireClassRegistration(false)` correctly `OUT-OF-MODEL: non-default-build`? Proposed: yes to both. → §8/§5a/§13.
3. Confirm `examples/`/`benchmarks/`/`integration_tests/` are out of scope. → §3.

**Wave 2 — language profiles & inputs:**

4. **Per-language memory safety:** for which implementations does Fory claim "malformed input → clean error, not memory corruption"? Is the **C++** decoder the primary memory-safety surface to fuzz, and does it carry the same guarantee? → §5/§8.
5. Beyond `maxDepth`, are there bounds on total allocation / declared collection sizes / output size against a hostile payload, or is that explicitly the caller's concern? Where is the resource/DoS line? → §8/§11a.
6. In **xlang** mode, what does Fory assume about the peer — is a hostile/malformed peer schema in scope, or is the peer a trusted endpoint? Proposed: peer trusted; type-confusion is the integrator's concern. → §7/§9.

**Wave 3 — disclaimers & non-findings:**

7. Confirm Fory disclaims payload integrity/authenticity/confidentiality (no MAC/sig/encryption) and is not a sandbox for registered types' own logic. → §9.
8. Any other recurring scanner/fuzzer false positives the PMC already knows about, to seed §11a (e.g. reflection/Unsafe usage, codegen)? → §11a.
9. **Meta:** Fory has no in-repo `SECURITY.md` and an `AGENTS.md` that is a developer/agent guide. This engagement adds `SECURITY.md` + `THREAT_MODEL.md` and wires `AGENTS.md → SECURITY.md → THREAT_MODEL.md`. Confirm the model should live in-repo (as proposed) vs. on the website, and who owns revisions. The existing config-guide "Security" section becomes a pointer to this model. → §1.
