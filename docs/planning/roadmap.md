# Roadmap

The roadmap records intended milestones, not delivery commitments.

## M0 — Architecture scaffold

- Repository, governance, security, and contribution policy
- Rust crate and host SDK boundaries
- Proposed effect-driven runtime boundary
- ADR, schema, and conformance-test structure
- CI and release-preparation workflows

## M1 — Protocol specification

- Run, session, state, action, event, effect, and checkpoint identities
- Versioned command/effect/result envelopes
- Error taxonomy and compatibility rules
- Language-neutral conformance fixtures

## M2 — Minimal deterministic kernel

- Created, preparing, model-call, resolving, action, checkpoint, suspended,
  handoff, and terminal states
- Deterministic event sequencing
- Fake model and fake tool host

## M3 — First host SDK

- One idiomatic SDK selected through an ADR
- Host-driven effect execution
- Streaming, cancellation, and structured output prototype

## M4 — Multi-language conformance

- Python, JavaScript/TypeScript, and Kotlin SDKs
- Equivalent event traces across SDKs
- Native package build and signing pipelines

## M5 — Durability and production semantics

- Checkpoint migrations
- Action ledger and replay safety
- Suspend/restart/resume validation
- Provider and store contract suites
