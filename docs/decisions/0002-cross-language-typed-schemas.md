# ADR 0002: One typed SDK path with Rust schema validation

- Status: accepted
- Date: 2026-08-16

## Context

Rust cannot inspect Python annotations, TypeScript runtime types, or Kotlin
serialization descriptors through native bindings. Earlier SDK drafts exposed
raw schemas, codecs, registries, runners, and typed wrappers as parallel public
paths. That made ordinary agent construction substantially harder to learn.

## Decision

JSON Schema Draft 2020-12 remains the language-neutral runtime contract, but it
is internal to application-facing SDKs.

- Python accepts ordinary annotated functions and typed output classes.
- TypeScript accepts one Zod-backed `tool` declaration and a Zod output schema.
- Kotlin wraps a typed function reference with the canonical
  `tool(name, description, function)` factory and derives contracts from
  `@Serializable` argument/result types.
- Each SDK internally derives schemas, creates its registry/runner, converts
  tool values, and decodes terminal output.
- Rust validates declarations, model-produced tool arguments, and structured
  terminal output.
- Raw schema, codec, registry, runner, model-reference, adapter, protocol, and
  native-session APIs are internal and are not supported alternatives.

## Consequences

Applications have one obvious workflow and receive typed terminal values
without manual JSON decoding. Dynamic integrations must first bind their schema
to the language-native typed tool/output mechanism rather than bypassing the
canonical API. Internal layers remain separate for testability and dependency
direction.
