# Orion

Orion is a planned open-source, cross-language runtime for reliable LLM-based
agentic systems.

The project is currently a **design and repository scaffold**. It contains no
agent runtime implementation and is not ready for application use.

## Direction

Orion is designed around a Rust execution core with idiomatic SDKs for Python,
Kotlin, and JavaScript/TypeScript.

- Rust owns deterministic runtime semantics, transitions, event ordering,
  checkpoint formats, and policy evaluation.
- Host SDKs own language-native APIs, asynchronous integration, model clients,
  tools, storage drivers, and framework integrations.
- A narrow effect protocol connects the kernel to host-provided operations.
- Every SDK must pass the same language-neutral conformance scenarios.

## Proposed public vocabulary

`Agent`, `Runner`, `Tool`, `RunContext`, `RunResult`, and `RunEvent` form the
intended common vocabulary. These names are provisional until accepted through
the architecture-decision process.

## Repository map

```text
crates/          Rust protocol, kernel, policy, persistence, FFI, and test crates
sdks/            Idiomatic Python, JavaScript/TypeScript, and Kotlin SDK shells
bindings/        Native-binding integration boundaries
conformance/     Cross-language behavioral scenarios and expected traces
schemas/         Versioned wire and persistence schemas
docs/            Architecture, ADRs, project policy, and SDK contracts
examples/        Future cross-language examples
.github/         CI, release preparation, issue, and contribution automation
```

See [Repository layout](docs/development/repository-layout.md) and the
[architecture overview](docs/architecture/overview.md).

## Status

The current milestone is **M0: architecture scaffold**. See the
[roadmap](docs/planning/roadmap.md).

## Contributing

The project welcomes design feedback, but implementation work should follow an
accepted issue or ADR so that public contracts do not emerge accidentally. Read
the [contribution guide](.github/CONTRIBUTING.md) and
[governance policy](docs/policy/governance.md).

## License

Orion is intended to be available under either the Apache License 2.0 or the MIT
License, at your option. See [LICENSE-APACHE](LICENSE-APACHE) and
[LICENSE-MIT](LICENSE-MIT).
