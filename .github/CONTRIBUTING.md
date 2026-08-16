# Contributing to Orion

Orion is in its architecture phase. Contributions should preserve the boundary
between the Rust semantic kernel and language-native SDK behavior.

## Before opening a pull request

1. Search existing issues and architecture decision records.
2. Open a design discussion for new public types, lifecycle behavior, storage
   formats, FFI contracts, or SDK-wide conventions.
3. Add or update an ADR for decisions with cross-crate or cross-language impact.
4. Keep changes focused and include tests appropriate to the current milestone.

## Development checks

```sh
cargo fmt --all --check
cargo clippy --workspace --all-targets -- -D warnings
cargo test --workspace
python -m compileall sdks/python/src
npm pack --dry-run --prefix sdks/javascript
```

Kotlin validation will be enabled when the first executable SDK behavior and
tests are added; the Gradle wrapper is already present.

## Design rules

- Rust defines deterministic semantics, not host-language ergonomics.
- SDK APIs should feel native to their language.
- Foreign runtimes execute model, tool, storage, and integration effects.
- FFI contracts use owned, versioned data rather than Rust-specific layouts.
- Streaming and non-streaming observe the same underlying run lifecycle.
- No public contract is considered stable before the first compatibility ADR.
- No crate or SDK package may be published until naming and namespace ownership
  are confirmed.

## Pull requests

Pull requests should explain the problem, affected invariants, compatibility
impact, validation performed, and any follow-up work. Draft pull requests are
welcome for early design feedback.

All contributions are licensed under MIT OR Apache-2.0.
