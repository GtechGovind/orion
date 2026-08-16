# Rust crate instructions

Apply the root `AGENTS.md` plus these rules to code under `crates/`.

## Modules and APIs

- Preserve the SDK capability model conceptually without collapsing Rust crate
  boundaries: protocol contracts live in `orion-protocol`, execution in
  `orion-kernel`/`orion-ffi`, native conversion in `bindings/*`, and provider
  transport only in host SDKs.
- In multi-member `struct`, `enum`, `trait`, and `impl` bodies, use blank lines
  between distinct logical member groups. Accept `rustfmt` as authoritative when
  it removes padding immediately inside braces; never add comments whose only
  purpose is preserving whitespace.
- In non-trivial functions, separate validation, state mutation, effect creation,
  and return phases with blank lines. Accept `rustfmt` when it removes padding
  immediately inside function braces.
- Add short `//` phase comments when those boundaries and domain-oriented names
  still do not make the flow clear. Explain the invariant or reason for a phase;
  never narrate the Rust statements below the comment.
- Rust uses the same readability standard as every SDK, but `rustfmt` is the
  final authority on physical whitespace. Do not try to force a blank line
  immediately after `{` or immediately before `}` because `rustfmt` removes it.
  Preserve the intent with blank lines between logical phases, cohesive helper
  extraction, and phase comments where a transition remains non-obvious.
- Treat the repository-root `rustfmt.toml` as the enforceable Rust formatting
  policy. Do not bypass it with `#[rustfmt::skip]` or tool-specific overrides to
  preserve a visual preference; encode rules rustfmt cannot express here in
  `AGENTS.md` and apply them during review.
- A crate owns one architectural capability. Add code to the narrowest existing
  crate; introduce a crate only when it creates a real dependency boundary.
- Keep modules private by default and re-export a deliberately small API from
  `lib.rs`. Do not leave empty future modules or placeholder source files.
- Put cohesive implementation submodules beside their owner. Keep validation,
  state transitions, persistence, and transport concerns separate.
- Model domain states with enums and newtypes. Prefer exhaustive `match` arms;
  avoid boolean mode parameters and unrelated `Option` fields.
- Document every public item with `///` or `//!`, including errors, panics,
  safety, cancellation, ownership, and examples where relevant. Also document
  any invariant that justifies `expect`, checked indexing, or unusual ownership.
  Production `unwrap` is not allowed.

## Correctness and performance

- Use the stable feature set supported by the pinned Rust toolchain. Prefer
  newtypes, exhaustive enums, typed builders, `Result`, iterator APIs, and
  ownership-aware borrowing over string modes, raw tuples, or JSON values.
- Keep `serde_json::Value` at explicitly dynamic schema/provider fields. Convert
  known command, effect, state, and error shapes into typed structs immediately.
- Preserve deterministic event ordering and use checked arithmetic for counters,
  limits, sizes, and durable sequence numbers.
- Bound collections and work derived from host or model input. State complexity
  when an algorithm operates on run history, tools, events, or checkpoints.
- Avoid cloning protocol histories or payloads without considering their growth.
  Optimize only with a benchmark or profile when the clearer design is not enough.
- Do not perform network, filesystem, clock, randomness, or provider I/O inside
  the semantic kernel.

## Errors, unsafe code, and tests

- Use typed errors with actionable messages. Do not panic on recoverable host,
  protocol, checkpoint, or model input.
- `unsafe` remains forbidden in reusable crates. Native binding exceptions are
  governed by `bindings/AGENTS.md` and require documented safety invariants.
- Add tests for transitions, invariant rejection, limits, overflow boundaries,
  mismatched effects, cancellation, and restore behavior as applicable.
- Put a newly extracted test module in a descriptive sibling file and connect it
  with an explicit `#[path = "..."]` attribute.

## Verification

- Run `cargo fmt --all --check`.
- Run targeted crate tests while iterating.
- For shared changes run
  `cargo clippy --workspace --all-targets --all-features -- -D warnings` and
  `cargo test --workspace --all-features`.
