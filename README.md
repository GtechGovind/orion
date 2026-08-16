# Orion

Orion is an open-source, cross-language runtime pilot for reliable LLM agents.
One Rust state machine owns execution semantics while Python, Kotlin, and
JavaScript/TypeScript SDKs own provider clients and application tools.

Version `0.0.1` is a working pilot with model/tool loops, ordered lifecycle
events, normalized usage, structured-output declarations, and OpenAI-compatible
model endpoints.

## Architecture

Orion is designed around a Rust execution core with idiomatic SDKs for Python,
Kotlin, and JavaScript/TypeScript.

- Rust currently owns deterministic runtime semantics, transitions, event
  ordering, protocol validation, and schema validation. The architecture
  reserves checkpoint and policy semantics for future Rust layers.
- Host SDKs own language-native APIs, asynchronous integration, model clients,
  tools, storage drivers, and framework integrations.
- PyO3, Node-API, and JNI modules call Rust in-process and retain opaque,
  Rust-owned run sessions.
- Versioned protocol DTOs cross native boundaries as language objects; mutable
  kernel state never leaves Rust.
- Each SDK passes the same end-to-end tool-loop scenario through Rust.

## Public vocabulary

Applications use `Agent`, provider models such as `OpenAI`, language-native
typed tools, `AgentResult`, and lifecycle events. Runners, registries, codecs,
protocol DTOs, and native sessions remain internal.

## One supported workflow

Every SDK follows the same application path:

```text
provider model → typed tool → typed Agent → run/stream → AgentResult<T>
```

Python accepts an annotated function directly. TypeScript uses one Zod-backed
`tool({...})` declaration because static types are erased at runtime. Kotlin
uses one `tool(name, description, function)` declaration with a stable explicit
model-visible name. There are no public runner, registry, raw-schema, adapter,
model-reference, or native-session alternatives.

Complete runnable applications are documented in the
[cross-language examples](examples/README.md). To consume a built SDK from a
different project, follow the [installation guide](docs/guides/installation.md).

## Run the pilot

```bash
cargo build --workspace
cargo test --workspace
```

Then run the SDK tests documented in the [pilot guide](docs/guides/pilot.md).
See also the [public API](docs/contracts/public-api.md) and
[LLM connectivity guide](docs/guides/llm-connectivity.md).

## Repository map

```text
crates/          Rust protocol, kernel, policy, persistence, FFI, and test crates
sdks/            Idiomatic Python, JavaScript/TypeScript, and Kotlin SDKs
bindings/        Native-binding integration boundaries
conformance/     Cross-language behavioral scenarios and expected traces
schemas/         Versioned wire and persistence schemas
docs/            Architecture, ADRs, project policy, and SDK contracts
examples/        Runnable, type-checked cross-language usage examples
.github/         CI, release preparation, issue, and contribution automation
```

See [Repository layout](docs/development/repository-layout.md) and the
[architecture overview](docs/architecture/overview.md).

## Status

The usable single-agent pilot and production-oriented native package pipelines
are complete. Public registry publication remains gated on namespace ownership,
trusted-publisher configuration, and protected release credentials; see the
[roadmap](docs/planning/roadmap.md) and
[publishing guide](docs/release/publishing.md).

## Contributing

The project welcomes design feedback, but implementation work should follow an
accepted issue or ADR so that public contracts do not emerge accidentally. Read
the [contribution guide](.github/CONTRIBUTING.md) and
[governance policy](docs/policy/governance.md).

## Maintainer

Govind Yadav ([@GtechGovind](https://github.com/GtechGovind),
<gtech.govind2000@gmail.com>) is the project maintainer.

## License

Orion is intended to be available under either the Apache License 2.0 or the MIT
License, at your option. See [LICENSE-APACHE](LICENSE-APACHE) and
[LICENSE-MIT](LICENSE-MIT).
