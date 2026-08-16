# Public API contract

Status: implemented pilot contract for `0.0.x`.

Orion exposes one application workflow in Python, TypeScript, and Kotlin:

1. construct a provider model such as `OpenAI("gpt-5-mini")`;
2. declare typed application functions/tools;
3. construct `Agent` with a required typed output contract;
4. call `agent.run(...)` or `agent.stream(...)`;
5. consume an already decoded typed result.

`ModelRef`, provider adapters, schema codecs, JSON Schema documents,
`ModelRegistry`, `Runner`, protocol DTOs, and native sessions are internal.
They are not alternate public APIs.

| Concept | Python | TypeScript | Kotlin |
|---|---|---|---|
| Model | `OpenAI("id")` | `new OpenAI("id")` | `OpenAI("id")` |
| Tool | typed function | `tool({input, output, execute})` | `tool(name, description, function)` |
| Output | dataclass/Pydantic type | Zod schema | `KSerializer` |
| Run | `await agent.run(input)` | `await agent.run(input)` | `agent.run(input)` |
| Stream | async iterator | async iterator | cold `Flow` |
| Result | `AgentResult[T]` | `AgentResult<T>` | `AgentResult<T>` |

Python derives tool schemas from function annotations and descriptions from
docstrings. TypeScript requires one `tool` declaration because static types are
erased; Zod supplies runtime input/output contracts. Kotlin derives schemas and
codecs from `@Serializable` types and adapts typed function references to its
suspending tool contract. Kotlin tool names are explicit stable identifiers
because the JVM does not reliably retain a source function name after callable
reference adaptation.

All three SDKs convert their host contracts to JSON Schema Draft 2020-12. Rust
validates every schema at run creation, validates model-produced tool arguments
before requesting a callback, and validates structured terminal JSON before
completion. The SDK then decodes the Rust-validated terminal value into the
configured host type.

Runs execute tool calls sequentially in provider order and permit one
outstanding effect. Reaching the configured turn limit fails with
`turn_limit_exceeded`. Cancellation is propagated to the native run and active
host operation.

Public runtime features are complete only when Rust owns their shared semantics
and all supported SDKs expose the same capability through this canonical flow.
