# Changelog

All notable changes will be documented here.

The format follows Keep a Changelog, and releases will follow Semantic
Versioning once compatibility policy is accepted.

## [Unreleased]

### Added

- Versioned provider-neutral protocol and deterministic Rust kernel.
- In-process PyO3, Node-API, and JNI bindings with Rust-owned run sessions.
- Working Python, Kotlin, and TypeScript runners and model/tool loops.
- OpenAI-compatible adapters, tests, examples, and public documentation.
- Kernel state validation, bounded turns/tool calls, finish-reason checks, and
  protected provider request fields.
- Repository-wide and language-scoped AI contribution instructions with
  architecture, readability, documentation, modern-language, full-typing,
  package, safety, and verification requirements.
- An explicit pre-release compatibility-mode flag, disabled by default so early
  contracts can be redesigned without retaining legacy facades.
- A checked-in stable `rustfmt.toml` plus formatter-aware Rust phase-spacing and
  intent-comment rules shared with every SDK.
- Capability-based Kotlin packages for model contracts, runtime orchestration,
  provider adapters, and internal JNI conversion.
- Runnable, compiler-checked Python, TypeScript, and Kotlin example applications
  split into model, tool, agent, and lifecycle modules, covering typed tools,
  structured output, streaming, cancellation, usage, and adapter cleanup.
- One low-ceremony typed API in every SDK: Python annotated functions,
  TypeScript Zod tools, and Kotlin suspending function references.
- Common Rust-owned JSON Schema Draft 2020-12 validation for declarations,
  model-produced tool arguments, and structured terminal output.
- Stable public SDK error codes, retryability, and retry-delay metadata with
  equivalent Rust terminal, provider, tool, capability, configuration, and
  cancellation handling in Python, TypeScript, and Kotlin.
- Publish-ready Python abi3 wheels and complete source distributions, npm root
  and platform packages, and a Kotlin Maven artifact with embedded native
  libraries, sources, Dokka API documentation, signing, and Central metadata.
- Coordinated tag-only release automation that builds the supported native
  matrix, verifies clean external consumers, publishes through protected
  registry environments, records checksums, and creates a GitHub release.
- Multi-linter Qodana analysis for Rust, Python, JavaScript/TypeScript, and
  Kotlin with recommended inspections and zero tolerance for high or critical
  findings.

### Changed

- Replaced public model-reference, adapter, registry, runner, codec, raw-schema,
  and manual JSON-decoding paths with direct provider models, `Agent.run`/
  `Agent.stream`, and decoded `AgentResult<T>` output.
- Python async streams now cancel their Rust-owned run when consumers stop
  iteration before a terminal result.
- Cross-language feature work now requires one Rust-owned semantic contract and
  equivalent Python, TypeScript, and Kotlin SDK exposure.
- Kotlin tools now require one explicit stable model-visible name through
  `tool(name, description, function)`, avoiding unreliable suspend-function
  name discovery after JVM callable-reference adaptation.
