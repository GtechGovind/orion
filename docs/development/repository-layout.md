# Repository layout and file guide

This guide describes the source-controlled Orion repository as it exists in
`0.1.0`. The external [Agentic Framework Research & Design document](https://docs.google.com/document/d/1gobjUbbcnHgkUu1dir_0s5q0fBqtME0w0ASAl1mfOro/edit?tab=t.0#heading=h.vzfb1jxxgmqu)
is background only; it is not a specification Orion is compared against.

## Execution path

```text
Application
  → public SDK (Python, JavaScript/TypeScript, or Kotlin)
  → in-process native binding (PyO3, Node-API, or JNI)
  → Rust-owned RunSession
  → deterministic kernel
  → effect returned to the SDK
  → SDK executes model/tool I/O and resumes the same native session
```

Mutable run state stays inside Rust. Native boundaries receive ordinary
language DTOs. Dynamic schemas, tool values, provider options, and provider
state remain JSON-compatible because that is part of their public contract;
whole kernel transitions are not transported as JSON strings.

## Root files

- `README.md` — Project entry point, architecture summary, build commands,
  maintainer, contribution links, and licensing.
- `Cargo.toml` — Rust workspace members, shared package metadata, Rust `1.88`
  floor, dependency versions, and lint policy.
- `Cargo.lock` — Reproducible Rust dependency resolution for the workspace and
  native modules.
- `rust-toolchain.toml` — Pins Rust, rustfmt, and Clippy.
- `.editorconfig` — Common encoding, newline, whitespace, and indentation
  rules.
- `.gitignore` — Excludes build output, dependency trees, caches, IDE state,
  secrets, and local environments.
- `CHANGELOG.md` — User-visible release history.
- `LICENSE-APACHE`, `LICENSE-MIT`, and `NOTICE` — Dual-license terms and
  attribution.

## `.github/`

- `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`, and `SUPPORT.md` —
  Community workflow, conduct, private vulnerability reporting, and support
  routing.
- `PULL_REQUEST_TEMPLATE.md` — Required change rationale and verification.
- `ISSUE_TEMPLATE/` — Structured bug and feature forms plus issue routing.
- `workflows/ci.yml` — Blocking Rust and SDK build/test/package checks.
- `workflows/release-preflight.yml` — Manual check that public publishing stays
  disabled until a release decision.
- `dependabot.yml` — Dependency update schedule for every ecosystem.

## `crates/`: reusable Rust runtime

### `orion-protocol`

- `commands.rs` — `StartRun`, agent, model, tool, and settings definitions.
- `effects.rs` — Messages, model requests, tool calls, capabilities, and host
  effects.
- `results.rs` — Model/tool effect results, usage, finish reasons, and terminal
  results.
- `events.rs` — Ordered lifecycle event variants and envelopes.
- `errors.rs` — Stable cross-language error codes and retry metadata.
- `identifiers.rs` — Run and action identifiers.
- `versioning.rs` — Protocol version and compatibility check.
- `lib.rs` — Public module/re-export boundary.

### `orion-kernel`

- `machine.rs` — Deterministic transition engine, command/checkpoint validation,
  effect matching, tool validation, finish-reason handling, limits, events, and
  unit tests.
- `state.rs` — Serializable internal run state and lifecycle status.
- `lib.rs` — Public kernel and state exports.

### `orion-ffi`

- `session.rs` — Safe Rust `RunSession` wrapper. It owns a kernel, exposes
  start/resume/cancel/fail, and retains each unread step for thin native
  bindings.
- `lib.rs` — Native-session module export.

### Reserved crates

- `orion-checkpoint` — Future durable checkpoint stores and migration logic.
- `orion-policy` — Future retry, approval, timeout, and side-effect policy.
- `orion-testing` — Future reusable fake models/tools and scenario runner.

These crates contain explicit Rust package/module boundaries, not empty
directories. They must not be described as implemented features.

## `bindings/`: private native modules

- `python/Cargo.toml` and `python/src/lib.rs` — PyO3 abi3 module exposing an
  opaque `NativeRun`. `pythonize` maps Python dict/list/scalars directly to
  Serde protocol values without JSON strings.
- `javascript/Cargo.toml`, `javascript/build.rs`, and
  `javascript/src/lib.rs` — N-API addon exposing an opaque `NativeRun` and
  direct JavaScript object conversion.
- `kotlin/Cargo.toml` and `kotlin/src/lib.rs` — JNI shared library. A guarded
  handle registry owns `RunSession` values; recursive Map/List/scalar conversion
  maps JVM DTOs directly to Rust values. JNI exports catch panics and translate
  failures to `OrionException`.

Bindings are internal implementation details. Public application ergonomics
belong only in `sdks/`.

## `sdks/`: public host APIs

### Python

- `pyproject.toml` — Maturin mixed-project build, package metadata, abi3 native
  module path, Ruff, and strict Pyright configuration.
- `src/orion_sdk/models.py` — Public models, tools, adapter protocol, registry,
  and OpenAI-compatible adapter.
- `src/orion_sdk/runner.py` — Async effect loop over a PyO3 `NativeRun`.
- `src/orion_sdk/_native.pyi` and `py.typed` — Native-module type surface and
  PEP 561 marker.
- `src/orion_sdk/__init__.py` — Stable package exports.
- `tests/test_runner.py` — Deterministic native tool-loop smoke test.

### JavaScript/TypeScript

- `package.json` and `package-lock.json` — Private release-gated npm package,
  N-API targets, exact dependency resolution, build/test/package scripts, and
  developer metadata.
- `tsconfig.json` — Strict ESM compilation and declaration output.
- `src/index.ts` — Public types, models, runner, adapters, and native addon
  loading.
- `test/runner.test.ts` — Deterministic native tool-loop smoke test.

### Kotlin/JVM

- `settings.gradle.kts`, `build.gradle.kts`, and `gradle.lockfile` — JVM 17
  build, locked dependencies, Cargo JNI build, tests, and local Maven
  publication metadata.
- `gradlew`, `gradlew.bat`, and `gradle/wrapper/` — Reproducible Gradle wrapper.
- `src/main/kotlin/dev/orion/sdk/Orion.kt` — Public data classes, Flow runner,
  adapters, direct JNI DTO conversion, and native handle lifecycle.
- `src/test/kotlin/dev/orion/sdk/RunnerTest.kt` — Deterministic JNI tool-loop
  smoke test.

## Shared behavior and documentation

- `conformance/scenarios/tool-loop.json` — Common event-order expectation for
  a model → tool → model run.
- `conformance/README.md` — Scenario contract and future coverage list.
- `schemas/protocol-v1.schema.json` — Reviewable language-neutral protocol
  schema; Rust Serde types remain authoritative during `0.1`.
- `schemas/README.md` — Schema ownership and generation policy.
- `examples/weather.py`, `weather.ts`, and `Weather.kt` — Equivalent public API
  examples using an OpenAI-compatible model and weather tool.
- `examples/README.md` — Example prerequisites and purpose.
- `docs/README.md` — Documentation index.
- `docs/architecture/` — Runtime layers, native boundary, protocol, and future
  durability model.
- `docs/contracts/` — Shared public and host-SDK semantics.
- `docs/decisions/` — ADR process and accepted Rust-kernel decision.
- `docs/guides/` — Pilot build/use and LLM connectivity.
- `docs/planning/roadmap.md` — Milestones and deferred features.
- `docs/policy/governance.md` — Project roles and decision authority.
- `docs/release/` — Versioning and release gates.

## Generated paths

`target/`, `node_modules/`, `dist/`, `.gradle/`, `build/`, `.kotlin/`, Python
bytecode/tool caches, IDE metadata, and local environment files are generated or
machine-specific. They are ignored and must not be committed. The `examples/`
directory is intentionally retained even when an example language temporarily
lacks generated output.
