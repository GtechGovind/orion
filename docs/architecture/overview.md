# Architecture overview

## Objective

Orion aims to provide one reliable execution model across multiple host
languages without making those languages share one unnatural public API.

## Layers

```text
Application
  -> Idiomatic host SDK
      -> Model, tool, storage, and framework adapters
          -> Versioned command/effect bridge
              -> Rust semantic kernel
```

The Rust kernel is a deterministic state machine. It consumes commands and
effect results, updates run state, emits ordered events, and returns either the
next effect or a terminal outcome.

The host SDK owns asynchronous I/O. This prevents Rust from directly invoking
arbitrary Python callbacks, JavaScript functions, or Kotlin lambdas across
foreign runtime boundaries.

## Proposed kernel states

`CREATED`, `PREPARING`, `MODEL_CALL`, `RESOLVING`, `EXECUTING_ACTIONS`,
`CHECKPOINTING`, `NEXT_TURN`, `SUSPENDED`, `HANDOFF`, and terminal states.

The state names and transitions are proposals until the protocol ADR and
state-machine specification are accepted.

## Invariants

- Exactly one terminal outcome per run.
- Every state-changing transition has an ordered event sequence number.
- A resumed run does not blindly repeat a durable completed external action.
- Agent definitions do not contain mutable run state.
- Provider-native values do not enter kernel state.
- Suspension is a successful lifecycle state rather than a failure.
- Cancellation is distinct from failure.
