# Public API contract

Status: implemented pilot contract for `0.1.x`.

| Concept | Responsibility | Excludes |
|---|---|---|
| `Agent` | Immutable behavior, model reference, tools, schema, limits | Keys, clients, mutable run state |
| `Runner` | Models, tools, native kernel session, cancellation, events | Provider-specific loop rules |
| `ModelRef` | Serializable `{provider, model}` selection | Credentials and endpoints |
| `ModelRegistry` | Resolve provider keys to host adapters | Global mutable registration |
| `ModelAdapter` | Provider auth, translation, HTTP lifecycle | Tool and turn decisions |
| `Tool` | JSON Schema plus host handler | Provider request types |
| `RunEvent` | Ordered immutable lifecycle observation | Secrets and live objects |
| `RunResult` | Output, usage, turns, event trace | Vendor response objects |

Python uses awaitables and async iterators, TypeScript uses promises and async
iterables, and Kotlin uses suspending functions and `Flow`. These are idiomatic
bindings of one semantic contract.

## Agent and model

Required agent fields are `id`, `name`, `instructions`, and `model`. The model
accepts `provider:model`; the canonical representation is:

```json
{"provider":"openai","model":"gpt-5-mini"}
```

Tool functions remain in the host. Only name, description, and input JSON
Schema cross the native boundary. Settings include portable temperature and token limits
plus provider-keyed opaque options. Secrets never belong in settings.

## Runner semantics

`run` returns one `RunResult`. `run_stream`/`runStream` emits ordered lifecycle
events followed by that result. A run has at most one outstanding effect. The
host result must match it. Calls are executed sequentially in provider order.

A model response without tool calls completes the run. Calls start tool
execution and their results enter the next model turn. Reaching `max_turns`
while requesting tools fails with `turn_limit_exceeded`.

The protocol is `1.0`. Major versions are breaking. Minor versions add
backwards-compatible fields or variants.
