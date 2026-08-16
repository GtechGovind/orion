# ADR-0001: Rust semantic kernel with host-driven effects

- Status: proposed
- Date: 2026-08-16
- Owners: TBD

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

## Current hypothesis

Approach 2 provides the best initial balance. This is not yet accepted.

## Required prototype

Implement the smallest create-run, model-effect, tool-effect, checkpoint,
resume, and complete path in all three target languages. Validate cancellation,
streaming, error mapping, packaging, and equivalent event traces.

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
