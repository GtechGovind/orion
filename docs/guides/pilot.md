# Version 0.1 pilot

## Included

- Rust-owned deterministic state machine and owned protocol values
- One outstanding model or tool effect at a time
- Sequential model → tool → model loops
- One simple typed `Agent.run`/`Agent.stream` API in Python, Kotlin, and TypeScript
- Lifecycle event streaming and terminal results
- OpenAI-compatible provider models with internal adapters
- Usage aggregation, turn limits, cancellation commands, and normalized errors
- End-to-end tests in Rust and every SDK

Build all native modules and run the Rust tests:

```bash
cargo build --workspace
cargo test --workspace
```

Run SDK checks:

```bash
python -m compileall sdks/python/src examples/python/weather_agent
uvx ruff check sdks/python/src sdks/python/tests examples/python/weather_agent
uvx ruff format --check sdks/python/src sdks/python/tests examples/python/weather_agent
uvx --with "pydantic>=2.11,<3" pyright -p sdks/python
(cd sdks/python && uvx maturin build --release)
cd sdks/javascript && npm ci && npm run check && npm test && npm pack --dry-run && cd ../..
cd sdks/kotlin && ./gradlew test publishToMavenLocal --no-daemon && cd ../..
```

Python ships an abi3 PyO3 extension, JavaScript ships a Node-API addon, and
Kotlin loads the JNI library produced by Cargo. Each binding owns an opaque
Rust session handle. Protocol DTOs cross as normal dict/object/map values;
mutable run state never crosses the native boundary.

The runnable [weather examples](../../examples/README.md) show the only supported
SDK workflow: provider model, typed tool, typed agent output, streaming, and
usage reporting.

Not included: durable stores, crash recovery, retries, approvals, routing,
fallbacks, parallel tools, multimodal content, token deltas, handoffs, and
multi-agent orchestration. Their boundaries exist but they are not claimed as
implemented features.
