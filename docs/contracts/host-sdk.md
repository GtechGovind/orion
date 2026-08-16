# Host SDK contract

Every SDK exposes the same six public concepts: `Agent`, provider models,
language-native typed tools, `AgentResult`, lifecycle events, and SDK errors.

The supported flow is always model → tools → agent → `run`/`stream`. Internal
registries, runners, codecs, schemas, adapters, protocol DTOs, and native handles
must not be re-exported or documented as application choices.

Language expression remains idiomatic:

- Python uses annotations, dataclasses/Pydantic models, awaitables, and async
  iterators.
- TypeScript uses Zod runtime schemas, promises, async iterators, and
  `AbortSignal`.
- Kotlin uses `@Serializable` data classes,
  `tool(name, description, function)`, coroutines, sealed stream items, and
  `Flow`. The explicit tool name is a stable provider/protocol identifier;
  Kotlin/JVM reflection is not used to infer it.

Conformance requires equivalent terminal output, event order, error categories,
cancellation, tool validation, structured-output validation, and direct native
DTO conversion in every SDK.
