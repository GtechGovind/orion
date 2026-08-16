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

- `AGENTS.md` — Canonical architecture, readability, modularity, reliability,
  compatibility, and verification instructions for human and AI contributors.
- `CLAUDE.md` and `GEMINI.md` — Thin compatibility files that import the
  canonical `AGENTS.md` policy for tools using vendor-specific context names.
- `README.md` — Project entry point, architecture summary, build commands,
  maintainer, contribution links, and licensing.
- `Cargo.toml` — Rust workspace members, shared package metadata, Rust `1.88`
  floor, dependency versions, and lint policy.
- `Cargo.lock` — Reproducible Rust dependency resolution for the workspace and
  native modules.
- `rust-toolchain.toml` — Pins Rust, rustfmt, and Clippy.
- `rustfmt.toml` — Enforceable Rust whitespace, width, edition, and newline
  settings. Formatter-inexpressible phase and comment rules live in `AGENTS.md`.
- `.editorconfig` — Common encoding, newline, whitespace, and indentation
  rules.
- `.gitignore` — Excludes build output, dependency trees, caches, IDE state,
  secrets, and local environments.
- `CHANGELOG.md` — User-visible release history.
- `LICENSE-APACHE`, `LICENSE-MIT`, and `NOTICE` — Dual-license terms and
  attribution.

## `.github/`

- `copilot-instructions.md` — GitHub Copilot pointer to the canonical root
  `AGENTS.md` policy.
- `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`, and `SUPPORT.md` —
  Community workflow, conduct, private vulnerability reporting, and support
  routing.
- `PULL_REQUEST_TEMPLATE.md` — Required change rationale and verification.
- `ISSUE_TEMPLATE/` — Structured bug and feature forms plus issue routing.
- `workflows/ci.yml` — Blocking Rust and SDK build/test/package checks.
- `workflows/release-preflight.yml` — Credential-free coordinated version and
  package-readiness audit.
- `workflows/release.yml` — Tag-only native build, clean-consumer verification,
  protected registry publication, checksums, and GitHub release automation.
- `dependabot.yml` — Dependency update schedule for every ecosystem.

## `.cursor/`

- `rules/orion-engineering.mdc` — Always-applied Cursor rule that points to the
  canonical root `AGENTS.md` policy.

## `crates/`: reusable Rust runtime

- `AGENTS.md` — Rust-specific module, API, determinism, error, safety, test, and
  verification instructions.

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

- `machine.rs` — Public deterministic kernel operations, effect matching,
  terminal transitions, and event sequencing.
- `machine/transitions.rs` — Model/tool result acceptance and next-effect
  construction.
- `machine/validation.rs` — Command, checkpoint, bounded-capacity, Draft 2020-12
  schema, tool-argument, and structured-output validation.
- `machine/tests.rs` — Deterministic transition and invariant tests.
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

- `AGENTS.md` — Cross-binding conversion, lifecycle, safety, panic, and
  verification instructions.

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

- `AGENTS.md` — Python typing, async, packaging, error, and validation rules.
- `pyproject.toml` — Maturin mixed-project build, package metadata, abi3 native
  module path, complete sdist, Ruff, and strict Pyright configuration.
- `LICENSE-APACHE`, `LICENSE-MIT`, and `NOTICE` — Legal files embedded in Python
  wheels and source distributions through PEP 639 metadata.
- `src/orion_sdk/__init__.py` — The only supported application surface:
  `Agent`, `OpenAI`, typed results/events, and SDK errors.
- `src/orion_sdk/model/`, `runtime/`, and `provider/` — Internal model DTOs,
  schema derivation, registry/runner machinery, and provider transport used to
  implement the simple root API.
- `src/orion_sdk/_internal/` — Private native loading plus typed
  protocol encoding, decoding, and boundary validation.
- `src/orion_sdk/_native.pyi` and `py.typed` — Native-module type surface and
  PEP 561 marker.
- `tests/test_runner.py` — Deterministic simple-API tool-loop and typed-output tests.

### JavaScript/TypeScript

- `AGENTS.md` — Strict TypeScript, ESM, async, exports, native-loading, and
  validation rules.
- `package.json` and `package-lock.json` — Public npm package metadata, verified
  N-API targets, exact dependency resolution, build/test/release scripts, and
  developer metadata.
- `tsconfig.json`, `tsconfig.test.json`, and `tsconfig.examples.json` — Strict
  ESM compilation, declaration output, and independent test/example checking.
- `src/index.ts` — The only package export: `Agent`, `OpenAI`, `tool`, Zod,
  typed results/events, run options, and SDK errors.
- `src/model/`, `runtime/`, and `provider/` — Internal DTOs, schema codecs,
  registry/runner machinery, and provider transport.
- `src/internal/` — Private N-API loading, native DTO guards, and protocol
  conversion.
- `scripts/` — Root-package inspection, current-platform external-consumer
  smoke testing, and release-only napi-rs platform-package verification.
- `test/runner.test.ts` — Deterministic simple-API tool-loop and typed-output tests.

### Kotlin/JVM

- `AGENTS.md` — Kotlin package, explicit API, coroutine, JNI, error, and
  validation rules.
- `settings.gradle.kts`, `build.gradle.kts`, and `gradle.lockfile` — JVM 17
  build, locked dependencies, Cargo JNI resource staging, Dokka, signing,
  tests, Maven Local, and opt-in Central Portal publication metadata.
- `gradlew`, `gradlew.bat`, and `gradle/wrapper/` — Reproducible Gradle wrapper.
- `src/main/kotlin/dev/orion/sdk/` — Supported `Agent`, `OpenAI`, `tool`, typed
  result/event, usage, and exception surface.
- `model/`, `runtime/`, and `provider/` — Internal DTOs, registry/runner
  machinery, schema-backed host tools, and provider transport.
- `src/main/kotlin/dev/orion/sdk/internal/` — JNI session ownership, focused
  agent/model/run protocol conversion, and Kotlin-serialization schema
  derivation, including secure packaged-native extraction; not a public
  application API.
- `src/main/kotlin/dev/orion/sdk/OrionException.kt` — Stable public SDK failure.
- `src/test/kotlin/dev/orion/sdk/AgentTest.kt` — Deterministic simple-API JNI
  tool-loop and typed-output tests.
- `consumer-smoke/` — Independent Gradle application that resolves only the
  Maven artifact and proves its embedded JNI library loads without
  `java.library.path`.

## Shared behavior and documentation

- `conformance/scenarios/tool-loop.json` — Common event-order expectation for
  a model → tool → model run.
- `conformance/README.md` — Scenario contract and future coverage list.
- `schemas/protocol-v1.schema.json` — Reviewable language-neutral protocol
  schema; Rust Serde types remain authoritative during `0.1`.
- `schemas/README.md` — Schema ownership and generation policy.
- `examples/python/weather_agent/`, `examples/javascript/weather-agent/`, and
  `examples/kotlin/weather-agent/` — Equivalent, compiler-checked applications
  split into model, tool, agent, and main modules. They use only the canonical
  provider-model → typed-tool → agent → stream workflow.
- `examples/javascript/weather-agent/package.json` — Defines a private,
  kebab-case npm example package and marks its `src/` tree as ESM.
- `examples/README.md` — Language-specific prerequisites, build/run commands,
  credential handling, canonical API usage, and offline verification.

Within each weather application:

- `model/weather.*` owns typed tool arguments, tool results, and the terminal answer.
- `tool/weather.*`/`WeatherTool.kt` owns application validation and business
  logic. Provider and runner concerns do not enter it.
- `agent.*`/`WeatherAgent.kt` wraps functions with the language-native tool
  declaration and composes the provider model, tool set, instructions, limits,
  and structured-output contract into an immutable agent.
- `main.*`/`Main.kt` owns streaming, cancellation where applicable, typed output,
  and usage reporting; SDK internals own provider and native plumbing.
- `docs/README.md` — Documentation index.
- `docs/architecture/` — Runtime layers, native boundary, protocol, and future
  durability model.
- `docs/contracts/` — Shared public and host-SDK semantics.
- `docs/decisions/` — ADR process, Rust-kernel ownership, and cross-language
  typed-schema decisions.
- `docs/guides/` — Pilot build/use, external-project installation, and LLM
  connectivity.
- `docs/planning/roadmap.md` — Milestones and deferred features.
- `docs/policy/governance.md` — Project roles and decision authority.
- `docs/release/` — Versioning, registry ownership, publishing, recovery, and
  release gates.

## Generated paths

`target/`, `node_modules/`, `dist/`, `.gradle/`, `build/`, `.kotlin/`, Python
bytecode/tool caches, IDE metadata, and local environment files are generated or
machine-specific. They are ignored and must not be committed. The `examples/`
directory is intentionally retained even when an example language temporarily
lacks generated output.
