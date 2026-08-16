# Orion repository instructions

These instructions apply to every human or AI contributor in this repository.
More specific `AGENTS.md` files may strengthen them for a subtree, but must not
weaken the architecture, safety, compatibility, or quality requirements here.

## Project phase flags

`COMPATIBILITY_MODE: disabled`

- The flag above is the canonical repository compatibility switch. The user's
  explicit instruction for the current task overrides it.
- When `disabled`, Orion is treated as pre-release: prefer the cleanest coherent
  contract and allow breaking renames, moves, removals, schema changes, and API
  redesigns. Update every in-repository caller, binding, SDK, test, example,
  schema, document, and changelog entry in the same change. Do not add aliases,
  deprecated facades, migration wrappers, or legacy paths solely for compatibility.
- When `enabled`, or when the user explicitly requests compatibility, preserve
  released public/durable contracts or introduce an intentional version boundary
  and migration path according to the compatibility rules below.

## Before changing code

- Read the nearest implementation, its tests, and the relevant contract or ADR.
- Check `git status` and preserve unrelated or user-authored changes.
- Follow local conventions. Public names may change when compatibility mode is
  disabled; when it is enabled, do not silently move, rename, or remove them.
- Keep the change focused. Do not combine a feature or fix with broad cleanup.
- For a new cross-language contract, lifecycle rule, durable format, or FFI
  shape, update or add an ADR before treating the design as settled.

## Architecture and dependency direction

Maintain this execution boundary:

```text
application -> host SDK -> native binding -> Rust RunSession -> kernel
```

- `crates/orion-protocol` owns versioned, language-neutral contracts.
- `crates/orion-kernel` owns deterministic transitions and invariants. It must
  not perform network I/O, read provider credentials, or call host callbacks.
- `crates/orion-ffi` owns safe Rust sessions and the binding-facing boundary.
- `bindings/*` are thin PyO3, Node-API, or JNI conversion and lifecycle layers.
  Do not put provider logic or application ergonomics in a binding.
- `sdks/*` own idiomatic public APIs, async effect loops, model/tool adapters,
  provider I/O, and host-language exception mapping.
- Dependencies point inward. Lower layers must not import bindings or SDK code.
- Cross-FFI values are owned, explicit, bounded, and versioned. Never expose
  borrowed Rust data or Rust layout as a public ABI.
- Use direct language DTO conversion at native boundaries. Do not introduce a
  subprocess driver or serialize whole kernel transitions as JSON strings.

## Cohesion, modules, and file size

- A file or module has one cohesive responsibility and one clear reason to
  change. Do not collect unrelated behavior in one file.
- Directories and language packages represent stable capabilities, not file
  types or temporary milestones. Prefer names such as `model`, `runtime`,
  `provider`, `protocol`, and `internal` over vague containers.
- Keep filesystem paths aligned with Rust modules, Python packages, JavaScript
  package exports, and Kotlin package declarations. An IDE project view should
  reveal the architecture without requiring someone to open every file.
- Keep a small intentional package entry point. Do not flatten an entire SDK
  into its root package, and do not create a subpackage for one trivial private
  helper. When compatibility mode is enabled, package moves of public types are
  compatibility changes.
- Split by domain capability or lifecycle responsibility, not merely to satisfy
  a line count. Keep the data, invariants, and tests needed to understand a
  concept near its implementation.
- Do not create generic dumping grounds named `utils`, `common`, `helpers`, or
  `misc`. Give shared code a domain-specific name and owner.
- Prefer private modules and the smallest practical public surface. Re-export
  intentionally from a package or crate entry point.
- A production source file should normally stay below 400 lines, excluding
  generated code and test fixtures. At 500 lines, split it before adding more
  behavior unless the file is inherently declarative and the reason is recorded
  in the change.
- A function should normally stay below 40 logical lines and one abstraction
  level. At 60 lines, first extract cohesive operations or a state object. If an
  algorithm or protocol flow remains large, add short phase comments and state
  why further extraction would make it harder to follow.
- Do not replace one large function with many meaningless one-line helpers.
  Names and boundaries must reveal domain intent and reduce cognitive load.
- In Kotlin, normally keep one public top-level type per same-named file. In
  Rust, Python, and TypeScript, group tightly related types when that improves
  discoverability; split orchestration, models, adapters, and transport logic.

## Language-specific instructions

Before editing a language area, read and follow its nearest instruction file:

- `crates/AGENTS.md` for reusable Rust crates and kernel semantics.
- `bindings/AGENTS.md` for PyO3, Node-API, and JNI native boundaries.
- `sdks/python/AGENTS.md` for the Python SDK.
- `sdks/javascript/AGENTS.md` for the JavaScript/TypeScript SDK.
- `sdks/kotlin/AGENTS.md` for the Kotlin/JVM SDK.

These scoped files add language rules; they do not replace this root policy.

## Cross-language SDK layout

Keep equivalent capabilities easy to find in every host SDK:

```text
model/      model references, profiles, responses, usage, adapter contracts
runtime/    agents, tools, runner, run events, and terminal results
provider/   concrete external model-provider adapters
internal/   native loading, FFI conversion, and protocol parsing
```

- Python, TypeScript, and Kotlin use these capability names in their package or
  module trees. A small root entry point re-exports only the supported API.
- Use Python `_internal` when underscore naming is needed to signal a private
  package. Never export `internal` or `_internal` implementation types.
- Rust expresses the same ownership through workspace crates and focused modules:
  `orion-protocol` for contracts, `orion-kernel`/`orion-ffi` for runtime, and
  `bindings/*` for host-internal conversion. Provider transport stays in SDKs.
- Equivalent concepts should use equivalent names across languages unless an
  established language convention makes a different name materially clearer.

## Cross-language feature parity

- A public runtime capability is incomplete until it is available through the
  Rust runtime and every supported SDK: Python, JavaScript/TypeScript, and
  Kotlin. Implement and document the complete vertical slice in one change.
- Put language-neutral behavior, validation, lifecycle rules, limits, and error
  classification in the lowest appropriate Rust layer. SDKs adapt that contract
  to idiomatic host types; they must not independently reimplement runtime
  semantics that Rust can own.
- SDK-only code is limited to unavoidable language ergonomics, provider I/O,
  callback execution, type/schema derivation, and native value conversion.
  These conveniences must still expose equivalent capabilities in every SDK.
- Before adding a feature, define its shared protocol shape and conformance
  behavior. Add or update an ADR when it introduces a cross-language contract,
  then test the same success and failure cases at each SDK boundary.
- Do not merge a feature for only one supported SDK unless the user explicitly
  narrows the scope and the limitation is recorded in the relevant contract.
- Expose exactly one canonical application workflow in every SDK. Applications
  construct a provider model, declare language-native typed tools, construct a
  typed `Agent`, and call `run` or `stream`. Do not publicly expose alternate
  raw-schema, codec, registry, runner, model-reference, or native-session paths.
- Give each public capability one canonical constructor or factory signature.
  Do not add convenience overloads, builders, aliases, or reflection-based
  variants that express the same operation in a second way.
- Require explicit stable identifiers for names that cross provider, protocol,
  checkpoint, or FFI boundaries. Do not derive them from compiler-generated
  class names or reflection metadata unless the language contract guarantees
  that metadata across supported toolchains.
- JSON Schema conversion, model registries, effect runners, protocol DTOs, and
  native handles are implementation details. Keep them internal even when they
  remain separate modules for maintainability and testing.

## Readability and documentation

- Optimize for the next maintainer: use precise domain names, explicit types at
  boundaries, guard clauses, shallow nesting, and a linear happy path.
- Give multi-member type declarations visual breathing room: leave one blank
  line after the opening delimiter, one blank line between distinct members,
  and one blank line before the closing delimiter when the language formatter
  permits it. Do not add this padding to empty or deliberately one-line types.
- Give every non-trivial function or method body the same breathing room: leave
  one blank line after its opening delimiter, blank lines between logical phases
  such as validation, normalization, construction, execution, and return, and
  one blank line before its closing delimiter when syntax permits it.
- Apply this standard to Rust as well as Python, TypeScript, Kotlin, and binding
  code. In Rust, `rustfmt` removes padding immediately inside braces; accept that
  formatter behavior and preserve readability with blank lines between logical
  phases plus concise intent/invariant comments where needed.
- Keep truly trivial one-line functions compact. Do not add blank padding inside
  control-flow blocks, collection literals, or builder closures unless the blank
  line separates meaningful phases rather than individual statements.
- Keep functions deterministic and side-effect-free where practical. Make I/O,
  mutation, time, randomness, and global state explicit dependencies.
- Comments explain intent, tradeoffs, invariants, safety, units, ownership, or
  a non-obvious algorithm. Do not narrate syntax or preserve dead history.
- For a long but cohesive function, use phase comments such as validation,
  transition, effect creation, and commit. Keep comments synchronized with code.
- Every public/exported API must have language-native documentation: Rust doc
  comments, Python docstrings, TSDoc/JSDoc, or KDoc. Document its purpose,
  parameters, return value, errors, cancellation, ownership, ordering,
  concurrency, and compatibility behavior wherever relevant.
- Documentation must add contract knowledge rather than restate the declaration.
  Include a short example for non-obvious entry points and keep documentation,
  schemas, and examples updated in the same change as behavior.
- Replace unexplained literals with named constants or domain types. Include
  units in names or types for durations, sizes, limits, and identifiers.
- Remove dead code and stale comments. Do not leave empty placeholder folders,
  speculative extension points, or TODOs without an owner or tracked decision.

## Design and implementation

- Use modern, stable language features supported by the repository's declared
  minimum toolchain. Prefer features that improve type safety, ownership,
  exhaustiveness, cancellation, or readability; do not use novelty that reduces
  portability or requires an unapproved version-floor increase.
- Fully type domain data and public APIs. Prefer structs/data classes/dataclasses,
  enums or sealed/discriminated unions, protocols/interfaces/traits, generics,
  and typed errors over raw maps, tuples, strings, booleans, or positional lists.
- Do not expose `Any`, TypeScript `any`, broad `unknown`, raw `JsonObject`/
  `serde_json::Value`, `Map<String, Any?>`, or equivalent loose containers when
  the fields are known. Dynamic values are allowed only at genuinely dynamic
  provider/FFI boundaries and must be validated and converted immediately into
  typed internal DTOs.
- Give every function and callable member explicit parameter and return types
  unless the language convention and compiler infer a strictly equivalent local
  type without weakening the API contract.
- Start with the simplest design that satisfies known requirements. Patterns are
  tools, not goals; do not add factories, layers, traits, or interfaces for a
  hypothetical future use.
- Prefer composition and explicit dependency injection over inheritance,
  singletons, hidden service locators, or mutable global state.
- Use established patterns where they match the problem: state machine for run
  transitions, Adapter/Strategy for providers and tools, and ports plus concrete
  adapters for external storage. Keep abstractions owned by their consumers.
- Make illegal states difficult to represent with enums, sealed types,
  newtypes/value classes, and exhaustive matching instead of boolean switches or
  loosely related optional fields.
- Search for an existing abstraction before creating another. Reuse only when
  semantics match; do not force unrelated concepts through a generic helper.
- Keep algorithms correct and clear first. State important time/space complexity
  when it is not obvious, data can grow, or a choice affects production cost.
- Bound queues, histories, payloads, retries, recursion, and concurrency. Use
  checked arithmetic and deterministic ordering where results cross languages.
- Optimize measured bottlenecks. Profile or benchmark before and after a
  non-trivial optimization, and record the tradeoff. Do not trade clarity for an
  assumed micro-optimization.

## Reliability, errors, and security

- Validate untrusted data at the boundary and preserve validated invariants
  internally. Reject unknown versions and impossible transitions explicitly.
- Return typed, actionable errors with stable codes where they cross an SDK
  boundary. Preserve causes without exposing secrets, tokens, or full sensitive
  payloads.
- Do not use panic, `unwrap`, `expect`, unchecked casts, non-null assertions, or
  catch-all exception handling in production paths unless an invariant makes the
  case unreachable and a nearby comment explains it.
- Treat cancellation, timeout, retry, failure, suspension, and completion as
  distinct states. Release native handles and other resources on every path.
- Avoid blocking an async runtime. Make concurrency ownership explicit, prevent
  races and duplicate side effects, and apply backpressure to producer/consumer
  boundaries.
- Keep `unsafe` code confined to the smallest binding boundary, document every
  safety invariant, and provide a safe wrapper. Workspace Rust otherwise
  forbids unsafe code.
- Minimize dependencies. Before adding one, check maintenance, license, security,
  binary-size, and cross-platform impact; use locked reproducible versions.

## Public contracts and compatibility

- Apply the preservation and migration rules in this section only when
  `COMPATIBILITY_MODE` is `enabled` or the user explicitly requests compatibility.
  When disabled, contract changes still require complete repository-wide updates
  and documentation, but no legacy surface must be retained.
- Treat exported Rust APIs, Python imports, npm exports and types, Kotlin package
  names and signatures, native symbols, event order, error codes, schemas, and
  durable data as compatibility surfaces.
- When a breaking change is intentional, update all bindings, SDKs, examples,
  tests, schemas, docs, and release notes together, and provide a migration path
  or version boundary.
- Rust Serde protocol types are authoritative during `0.1`; keep the reviewable
  JSON schema and cross-language DTOs synchronized with them.
- Keep all SDKs behaviorally consistent while preserving language-native naming,
  async, error, and resource-management conventions.

## Tests and verification

- Test behavior, observable contracts, and failure modes rather than private
  implementation details. Prefer deterministic tests with explicit fixtures.
- Cover the happy path plus relevant invalid input, limits, cancellation,
  resource cleanup, and cross-language conversion boundaries.
- A bug fix includes a regression test when practical. A changed public contract
  includes conformance coverage or equivalent tests in every affected SDK.
- Use the narrowest useful checks while iterating, then run every check affected
  by the final diff. Never claim a check passed if it was skipped or failed.
- Rust: `cargo fmt --all --check`, then targeted tests; for shared changes run
  `cargo clippy --workspace --all-targets --all-features -- -D warnings` and
  `cargo test --workspace --all-features`.
- Python: `python -m compileall sdks/python/src`, Ruff, Pyright, Maturin build,
  and `python -m unittest discover -s sdks/python/tests -v` as applicable.
- JavaScript/TypeScript: from `sdks/javascript`, run `npm ci`, `npm run check`,
  `npm test`, and `npm pack --dry-run` as applicable.
- Kotlin: from `sdks/kotlin`, run
  `./gradlew test publishToMavenLocal --no-daemon`.
- Qodana: keep `qodana.yaml`, `.qodana/*.yaml`, and the Qodana workflow aligned
  with every maintained language. Use the recommended inspection profiles,
  exclude only generated or machine-local paths, and do not weaken the zero
  high/critical quality gate to make a change pass.
- Before finishing, inspect the complete diff for accidental API changes,
  generated artifacts, secrets, debug code, duplicate logic, and stale docs.

## Definition of done

A change is complete only when the implementation is cohesive and readable,
relevant tests and static checks pass, public and durable contracts remain
compatible or are deliberately versioned, documentation matches behavior, and
the final report names both validations run and any remaining risk.
