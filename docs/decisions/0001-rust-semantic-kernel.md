# ADR-0001: Rust semantic kernel with host-driven effects

- Status: accepted for the v0.1 pilot
- Date: 2026-08-16
- Owner: Govind Yadav

## Question

Should Orion centralize execution semantics in Rust while allowing each host SDK
to execute model, tool, storage, and integration effects natively?

## Context

Orion targets Python, Kotlin, and JavaScript/TypeScript. A common kernel can
reduce semantic drift, but direct cross-runtime callbacks introduce event-loop,
threading, ownership, packaging, and debugging complexity.

## Candidate approaches

1. Rust owns execution and directly invokes foreign callbacks.
2. Rust is a deterministic semantic kernel; host SDKs execute requested effects.
3. Each SDK implements its own runtime from a shared written specification.
4. Orion runs as an out-of-process service accessed through RPC.

## Decision

Use approach 2: Rust is a deterministic semantic kernel and host SDKs execute
requested effects. Each SDK calls an in-process native module and holds an
opaque Rust-owned session; mutable kernel state is never serialized between
turns.

## Prototype evidence

Python/PyO3, Kotlin/JNI, and TypeScript/Node-API execute the same model → tool →
model scenario through Rust-owned sessions. Rust tests effect matching, state
validation, finish reasons, and usage aggregation. SDKs use native async APIs
and provider clients without callbacks from Rust threads. Lifecycle streaming,
cancellation, normalized errors, and local package builds are covered; durable
checkpoint storage and the full cross-platform release matrix remain later
acceptance gates.

## Acceptance threshold

- No foreign callback is required from a Rust worker thread.
- Every SDK produces equivalent lifecycle events for the same fixture.
- Native cancellation reaches pending host effects without leaking resources.
- SDK wrappers remain idiomatic and hide low-level binding details.
- Packaging works on the supported operating-system and architecture matrix.

## Revisit trigger

Reject or revise this approach if bridge complexity dominates SDK code, if
performance requires unsafe shared objects, or if native packaging creates an
unacceptable maintenance burden.
