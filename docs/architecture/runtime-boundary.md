# Rust and host runtime boundary

## Principle

The kernel requests effects; it does not call foreign user code directly.

Conceptually:

```text
step(command) -> events + effect | terminal outcome
resume(effect result) -> events + next effect | terminal outcome
```

## Rust owns today

- Legal transitions and lifecycle invariants
- Run, turn, tool-call, and event sequencing
- Outstanding-effect matching and bounded execution
- Tool-argument and structured-output schema validation
- Protocol validation

## Reserved for future Rust layers

- Durable checkpoint envelopes and migrations
- Retry eligibility, budgets, deadlines, and approval state
- Side-effect identity, receipts, and replay decisions

## Host SDKs own

- One language-native typed `Agent.run`/`Agent.stream` API
- Provider clients and provider authentication
- User-defined tools and callbacks
- Promise, coroutine, and `asyncio` integration
- Future storage transport and framework integrations
- Native exception and cancellation mapping

## Boundary constraints

- Messages are owned values with explicit versions.
- No borrowed Rust data or runtime-specific object crosses FFI.
- Handles are opaque and have explicit release operations.
- Host operations return normalized results and receipts.
- Streaming is represented through ordered events or pull-based batches.
- Backpressure and cancellation must be specified before callbacks are added.
