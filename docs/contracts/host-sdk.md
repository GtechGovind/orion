# Host SDK contract

Each public SDK should express the same runtime semantics through idiomatic
language constructs.

## Common concepts

- Agent definition
- Runner facade
- Tool definition
- Run context
- Run result or suspended run
- Typed lifecycle events

## Language-specific expression

- Python may use decorators, protocols, generics, `asyncio`, and exceptions.
- JavaScript/TypeScript may use structural types, Promises, async iterators, and
  `AbortSignal`.
- Kotlin may use data classes, sealed hierarchies, coroutines, `Flow`, and
  structured resource ownership.

Equivalent semantics do not require identical method names or builder styles.

## Conformance

Every SDK must execute shared scenarios and prove:

- equivalent terminal outcomes
- equivalent semantic event order
- consistent error categories
- correct cancellation and suspension mapping
- checkpoint round-trip compatibility
