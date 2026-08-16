# Repository layout and file guide

This guide explains the purpose of every source-controlled directory and file in
the Orion repository. For the research and design background that informed the
repository scaffold, see the [Agentic Framework Research & Design reference](https://docs.google.com/document/d/1gobjUbbcnHgkUu1dir_0s5q0fBqtME0w0ASAl1mfOro/edit?tab=t.0#heading=h.vzfb1jxxgmqu).

## Current status

Orion is at **M0: architecture scaffold**. The repository defines ownership
boundaries, package metadata, documentation, policies, and validation workflows,
but it does not yet implement an agent runtime. Most Rust modules and public SDK
entry points are intentionally placeholders. Package versions are `0.0.0`, Rust
crates cannot be published, and the JavaScript package is private.

## High-level structure

```text
orion/
├── .github/                 GitHub issue, pull-request, CI, and dependency automation
├── bindings/                Low-level native bridges used internally by host SDKs
├── conformance/             Language-neutral behavioral scenarios and expected traces
├── crates/                  Rust protocol, kernel, policy, persistence, FFI, and tests
├── docs/                    Indexed architecture, contracts, decisions, and project guidance
├── examples/                Future equivalent examples for every supported SDK
├── schemas/                 Future canonical protocol and persistence schemas
├── sdks/                    Public Python, JavaScript/TypeScript, and Kotlin SDKs
└── root project files       Workspace configuration, policies, roadmap, and licenses
```

## How the main parts work together

The intended execution path is:

1. An application uses an idiomatic API from `sdks/python`, `sdks/javascript`,
   or `sdks/kotlin`.
2. The SDK performs host-language work such as asynchronous I/O, model requests,
   tool execution, storage access, authentication, and framework integration.
3. The SDK's internal package in `bindings/<language>` transports owned,
   versioned messages through `orion-ffi`.
4. `orion-protocol` defines commands sent into the runtime, effects requested
   from the host, results returned to the runtime, ordered events, identifiers,
   errors, and version metadata.
5. `orion-kernel` applies deterministic lifecycle transitions. It requests an
   effect instead of directly calling Python, JavaScript, Kotlin, provider, or
   user code.
6. `orion-policy` decides whether actions are allowed and how approval, retry,
   timeout, concurrency, and side-effect rules apply.
7. `orion-checkpoint` records durable run state and completed-action receipts so
   a resumed run does not blindly repeat an external side effect.
8. `orion-testing`, `conformance`, and SDK-specific tests verify that every host
   language observes equivalent behavior.

The Cargo manifests do not declare these inter-crate dependencies yet. The flow
above describes the intended ownership model for later milestones.

## Root files

- `.editorconfig` — Shared editor rules: UTF-8, LF line endings, final newlines,
  trailing-whitespace cleanup, and language-specific indentation.
- `.gitignore` — Excludes Rust build output, dependency folders, Python caches,
  Gradle/IDE state, local environments, secrets, and operating-system files.
- `Cargo.toml` — Root Rust workspace manifest. Registers all six crates and
  centralizes version, edition, minimum Rust version, license, repository
  metadata, and lint policy.
- `rust-toolchain.toml` — Pins Rust `1.85.0` with `rustfmt` and `clippy` so local
  development and CI use the same toolchain.
- `README.md` — Project landing page: purpose, design direction, public
  vocabulary, repository map, current status, contribution entry point, and
  license summary.
- `CHANGELOG.md` — User-facing history of notable changes. It currently records
  the initial scaffold under `Unreleased`.
- `LICENSE-APACHE` — Apache License 2.0 option for dual-licensed use.
- `LICENSE-MIT` — MIT License option for dual-licensed use.
- `NOTICE` — Project copyright and attribution notice.

## `.github/`: repository automation and contribution forms

### Issue and pull-request templates

- `.github/ISSUE_TEMPLATE/bug_report.yml` — Structured bug form requesting a
  description, reproduction, version/commit, and affected subsystem.
- `.github/ISSUE_TEMPLATE/feature_request.yml` — Design/feature proposal form
  centered on the problem, evidence, competing approaches, and validation.
- `.github/ISSUE_TEMPLATE/config.yml` — Disables blank issues and routes security
  reports to private advisories.
- `.github/PULL_REQUEST_TEMPLATE.md` — Requires the problem, approach, runtime
  and compatibility impact, validation, ADR link/rationale, and checklist.

### Community health files

- `.github/CONTRIBUTING.md` — Contribution workflow, development checks, design
  rules, pull-request expectations, and ADR requirements.
- `.github/CODE_OF_CONDUCT.md` — Expected community behavior, unacceptable
  behavior, and maintainer enforcement responsibilities.
- `.github/SECURITY.md` — Supported-version status, private vulnerability
  reporting, and security principles.
- `.github/SUPPORT.md` — Routes architecture questions, bug reports, and
  security reports.

GitHub discovers these files automatically from `.github/`, so they do not need
to occupy the repository root.

### Automation

- `.github/dependabot.yml` — Schedules monthly dependency checks for Cargo, npm,
  Python, Gradle, and GitHub Actions.
- `.github/workflows/ci.yml` — Runs formatting, linting, and tests for the Rust
  workspace; compiles the Python package; dry-runs JavaScript packaging; and
  verifies required repository-policy files.
- `.github/workflows/release-preflight.yml` — Manually verifies that publishing
  remains disabled during M0. It does not publish any artifact.

## `crates/`: Rust semantic core

Every crate has a private `Cargo.toml` that inherits workspace metadata and lint
rules. The source files currently reserve module ownership; they contain no
runtime behavior.

### `crates/orion-protocol/`: language-neutral runtime contract

- `Cargo.toml` — Declares the private `orion-protocol` crate.
- `src/lib.rs` — Crate entry point and module export list.
- `src/commands.rs` — Commands a host SDK will submit, such as create, step,
  resume, cancel, or inspect.
- `src/effects.rs` — Operations the kernel will ask the host to perform, such as
  model calls, tools, approvals, external input, or persistence.
- `src/results.rs` — Normalized effect results and terminal run outcomes.
- `src/events.rs` — Immutable, ordered lifecycle event envelopes used by
  streaming, tracing, debugging, persistence hooks, and evaluation.
- `src/identifiers.rs` — Stable identities for runs, sessions, turns, actions,
  checkpoints, and operations.
- `src/errors.rs` — Versioned error categories that can be mapped consistently
  into every host language.
- `src/versioning.rs` — Protocol/schema version and compatibility metadata.

### `crates/orion-kernel/`: deterministic execution lifecycle

- `Cargo.toml` — Declares the private `orion-kernel` crate.
- `src/lib.rs` — Kernel entry point and module exports.
- `src/machine.rs` — State-machine advancement and enforcement of lifecycle
  invariants.
- `src/state.rs` — Boundary between serializable durable state and temporary
  in-memory run state.
- `src/transitions.rs` — Ownership of continue, complete, handoff, and suspend
  transitions.
- `src/turn.rs` — Lifecycle of one logical model turn.
- `src/budgets.rs` — Run, turn, token, cost, and deadline budget ownership.
- `src/cancellation.rs` — Cancellation propagation, cleanup, and terminal
  semantics distinct from ordinary failure.

### `crates/orion-checkpoint/`: durability and replay safety

- `Cargo.toml` — Declares the private `orion-checkpoint` crate.
- `src/lib.rs` — Checkpoint crate entry point and module exports.
- `src/envelope.rs` — Versioned checkpoint envelope containing the execution
  cursor and durable state.
- `src/ledger.rs` — Completed-action identities and receipts used to prevent
  unsafe duplicate side effects after recovery.
- `src/migration.rs` — Compatibility checks and migrations between durable
  format versions.
- `src/store.rs` — Protocol that host-provided checkpoint stores must implement.

### `crates/orion-policy/`: execution rules

- `Cargo.toml` — Declares the private `orion-policy` crate.
- `src/lib.rs` — Policy crate entry point and module exports.
- `src/approval.rs` — Human and automated approval rules.
- `src/concurrency.rs` — Structured concurrency, action dependencies, and safe
  parallel execution policy.
- `src/retry.rs` — Separate retry scopes for transport, model turn, tool, and
  workflow failures.
- `src/side_effects.rs` — Side-effect classification and idempotency
  requirements.
- `src/timeout.rs` — Run-level, model-call, and tool timeout rules.

### `crates/orion-ffi/`: native ABI boundary

- `Cargo.toml` — Declares the private `orion-ffi` library. It currently builds
  only as an `rlib`; exported dynamic-library symbols will wait for an ADR.
- `src/lib.rs` — FFI entry point and module exports.
- `src/handles.rs` — Opaque handles visible to host languages without exposing
  Rust object layouts.
- `src/memory.rs` — Allocation, ownership, and release conventions across the
  native boundary.
- `src/transport.rs` — Versioned command and response transport used by native
  bindings.

### `crates/orion-testing/`: deterministic test support

- `Cargo.toml` — Declares the private `orion-testing` crate.
- `src/lib.rs` — Testing crate entry point and module exports.
- `src/fake_model.rs` — Scripted, provider-neutral model behavior for tests
  without network access or API keys.
- `src/fake_tool.rs` — Scripted tools, results, side effects, and receipts.
- `src/event_recorder.rs` — Captures and compares ordered event traces.
- `src/scenario.rs` — Loads and executes language-neutral conformance scenarios.

## `sdks/`: public host-language APIs

The SDKs will expose idiomatic APIs while hiding generated/native binding
details. Equivalent runtime behavior does not require identical syntax across
languages.

### `sdks/python/`

- `README.md` — Python SDK status and intended responsibility.
- `pyproject.toml` — Hatch build configuration, package metadata, Python
  `>=3.10`, wheel source path, Ruff settings, and strict Pyright scope.
- `src/orion_sdk/__init__.py` — Future public Python package entry point.
- `src/orion_sdk/py.typed` — PEP 561 marker telling type checkers the package
  ships inline typing information.

Python tests will live in `sdks/python/tests/` when executable behavior exists.
They must consume the shared conformance scenarios.

### `sdks/javascript/`

- `README.md` — JavaScript/TypeScript SDK status and intended responsibility.
- `package.json` — Private ESM package metadata, Node `>=20`, future `dist`
  exports, dry-run packaging check, and test command.
- `src/index.ts` — Future public package entry point; intentionally exports
  nothing during M0.

JavaScript tests will live in `sdks/javascript/tests/` when executable behavior
exists. The plural directory name keeps SDK test layout consistent.

### `sdks/kotlin/`

- `README.md` — Kotlin SDK status and intended responsibility.
- `settings.gradle.kts` — Sets the Gradle root-project name.
- `build.gradle.kts` — Applies Kotlin/JVM `2.1.20`, targets Java 17, uses Maven
  Central, assigns version `0.0.0`, and enables JUnit Platform.
- `gradlew` — Unix/macOS Gradle wrapper launcher.
- `gradlew.bat` — Windows Gradle wrapper launcher.
- `gradle/wrapper/gradle-wrapper.properties` — Pins the Gradle distribution and
  wrapper download/cache behavior.
- `gradle/wrapper/gradle-wrapper.jar` — Binary bootstrap used by both wrapper
  launchers.
- `src/main/kotlin/dev/orion/sdk/Orion.kt` — Future public Kotlin namespace and
  SDK entry point.

Kotlin tests will use the conventional `src/test/kotlin/` tree when executable
behavior exists and must consume the shared conformance scenarios.

The current CI workflow does not yet run a Kotlin job.

## `bindings/`: internal native integration packages

Bindings are transport layers between host SDKs and `orion-ffi`. They are kept
separate so generated types and ABI details do not become the public API.

- `bindings/README.md` — Explains the separation between low-level bindings and
  public SDK ergonomics and records the planned Python, JavaScript, and Kotlin
  binding mechanisms.

Language subdirectories are created only when they contain real binding source
or build metadata. This avoids README-only placeholder trees.

## `conformance/`: cross-language behavioral truth

- `conformance/README.md` — Defines what every language-neutral scenario must
  contain and catalogs the planned completion, tool, retry, handoff, approval,
  cancellation, and crash-recovery scenarios.

Machine-readable scenario fixtures will be added after the protocol encoding is
selected. SDK test suites will consume the same fixtures so implementations
cannot silently drift apart.

## `schemas/`: versioned data contracts

- `schemas/README.md` — Reserves canonical sources for protocol and persistence
  schemas and establishes that generated language types must not be edited by
  hand.

Future schema generation and compatibility checks will feed Rust protocol
types, native transport, host-language bindings, checkpoint migration, and CI.

## `examples/`: equivalent developer examples

- `examples/README.md` — Defines planned cross-language examples for a minimal
  agent, typed tools, structured output, streaming, cancellation, checkpoint and
  resume, approval suspension, delegation, and handoff.

An example should demonstrate the same capability in Python,
JavaScript/TypeScript, and Kotlin wherever the ecosystems support equivalent
behavior.

## `docs/`: design and project documentation

- `docs/README.md` — Single navigation index for all maintained documentation.

### `docs/architecture/`

- `overview.md` — Layered architecture, proposed kernel states, and core runtime
  invariants.
- `runtime-boundary.md` — Exact responsibility split between deterministic Rust
  semantics and host-language I/O/integrations.
- `protocol.md` — Planned command/effect/result/event envelopes, required
  metadata, and unresolved encoding decisions.
- `durability.md` — Checkpoint, action receipt, replay-safety, and initial
  checkpoint-timing model.

### `docs/contracts/`

- `host-sdk.md` — Common semantic concepts and idiomatic expression in
  Python, JavaScript/TypeScript, and Kotlin, plus conformance obligations.

### `docs/decisions/`

- `README.md` — ADR purpose and allowed statuses.
- `0000-template.md` — Required structure for a new architecture decision:
  question, context, alternatives, evidence, experiment, threshold, decision,
  consequences, and revisit trigger.
- `0001-rust-semantic-kernel.md` — Proposed decision to centralize deterministic
  semantics in Rust while host SDKs execute effects natively; includes the
  required prototype and acceptance thresholds.

### `docs/development/`

- `repository-layout.md` — This complete map of repository structure,
  responsibilities, and file use cases.

### `docs/release/`

- `process.md` — Future gated publishing process and the present
  preparation-only state.
- `versioning.md` — Open decisions for SDK, crate, protocol, checkpoint, runtime,
  and platform compatibility.

### `docs/planning/`

- `roadmap.md` — Planned progression from the architecture scaffold through the
  protocol, kernel, SDK, conformance, and production-durability milestones.

### `docs/policy/`

- `governance.md` — Contributor, reviewer, and maintainer roles; decision rules;
  release authority; and the governance-change process.

## Generated and local-only directories

The following paths may appear during development but are intentionally ignored
and are not part of the repository architecture:

- `target/` — Rust build output.
- `node_modules/` and `dist/` — JavaScript dependencies and build output.
- `build/` and `.gradle/` — JVM/Gradle build and cache data.
- `__pycache__/`, `.pytest_cache/`, `.mypy_cache/`, `.ruff_cache/`, and `.venv/`
  — Python bytecode, tool caches, and virtual environments.
- `.idea/` and `*.iml` — Local IDE project state.
- `.env` and `.env.*` — Local environment/secrets files; `.env.example` may be
  committed when a safe template is needed.

## Where new work belongs

- New lifecycle message or shared identity: `crates/orion-protocol` plus schemas
  and conformance fixtures.
- New deterministic transition or invariant: `crates/orion-kernel` plus unit,
  property, and event-trace tests.
- Retry, approval, timeout, concurrency, or idempotency rule:
  `crates/orion-policy`.
- Checkpoint format, migration, store contract, or action receipt:
  `crates/orion-checkpoint`.
- ABI, memory ownership, handle, or native transport change: `crates/orion-ffi`
  and the relevant `bindings/<language>` package.
- Public language-specific API: the appropriate `sdks/<language>` directory;
  low-level generated binding types should remain internal.
- Behavior that must match in every SDK: `conformance/scenarios` and each SDK's
  test suite.
- Durable or wire-format shape: `schemas` with generation and compatibility
  checks.
- Consequential cross-crate, cross-language, FFI, durability, or compatibility
  decision: a new file under `docs/decisions` before implementation becomes a
  public contract.
