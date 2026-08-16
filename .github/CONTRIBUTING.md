# Contributing to Orion

Orion is an executable `0.1` pilot. Contributions must preserve the boundary
between Rust-owned execution semantics and the single application-facing
workflow exposed idiomatically by every supported SDK.

## Before opening a pull request

1. Search existing issues and architecture decision records.
2. Open a design discussion for new public types, lifecycle behavior, storage
   formats, FFI contracts, or SDK-wide conventions.
3. Add or update an ADR for decisions with cross-crate or cross-language impact.
4. Keep changes focused and include tests appropriate to the current milestone.

## Development checks

```sh
cargo fmt --all --check
cargo clippy --workspace --all-targets --all-features -- -D warnings
cargo test --workspace --all-features
python -m compileall sdks/python/src examples/python/weather_agent
uvx ruff check sdks/python/src sdks/python/tests examples/python/weather_agent
uvx ruff format --check sdks/python/src sdks/python/tests examples/python/weather_agent
uvx --with "pydantic>=2.11,<3" pyright -p sdks/python
(cd sdks/javascript && npm ci && npm run check && npm test && npm pack --dry-run)
(cd sdks/kotlin && ./gradlew test publishToMavenLocal --no-daemon)
```

Native SDK tests build the corresponding PyO3, Node-API, or JNI module and run
the deterministic model → tool → model scenario without provider credentials.
The CI workflow keeps formatting, linting, strict type checking, native builds,
tests, and package validation blocking for every maintained language.

## Design rules

- Rust defines deterministic semantics, not host-language ergonomics.
- SDK APIs should feel native to their language.
- Every SDK exposes only provider model → typed tool → typed `Agent` →
  `run`/`stream` → `AgentResult<T>`; low-level runners, registries, schemas,
  adapters, protocol DTOs, and native handles stay internal.
- One public capability has one canonical construction path. Do not add
  compatibility aliases or parallel convenience APIs while compatibility mode
  is disabled in `AGENTS.md`.
- Foreign runtimes execute model, tool, storage, and integration effects.
- FFI contracts use owned, versioned data rather than Rust-specific layouts.
- Streaming and non-streaming observe the same underlying run lifecycle.
- No public contract is considered stable before the first compatibility ADR.
- Package publishing remains release-gated until naming, namespace ownership,
  artifact signing, and the native support matrix are confirmed.

## Pull requests

Pull requests should explain the problem, affected invariants, compatibility
impact, validation performed, and any follow-up work. Draft pull requests are
welcome for early design feedback.

All contributions are licensed under MIT OR Apache-2.0.
