# Roadmap

The roadmap records intended milestones, not delivery commitments.

## M0 — Architecture scaffold

Status: complete.

- Repository, governance, security, and contribution policy
- Rust crate and host SDK boundaries
- Proposed effect-driven runtime boundary
- ADR, schema, and conformance-test structure
- CI and release-preparation workflows

## M1 — Protocol specification

Status: pilot complete; expand conformance fixtures before stability.

- Run, session, state, action, event, effect, and checkpoint identities
- Versioned command/effect/result envelopes
- Error taxonomy and compatibility rules
- Language-neutral conformance fixtures

## M2 — Minimal deterministic kernel

Status: pilot core complete for running, completed, failed, and cancelled
states. Durability-specific lifecycle states remain.

- Deterministic model/tool effects with completed, failed, and cancelled
  terminal states
- Deterministic event sequencing
- Bounded turns, schema validation, and deterministic fake model/tool tests

Checkpoint, suspension, handoff, approval, and replay states are deferred to
the durability milestone.

## M3 — First host SDK

Status: superseded by three pilot SDKs.

- One idiomatic SDK selected through an ADR
- Host-driven effect execution
- Streaming, cancellation, and structured output prototype

## M4 — Multi-language conformance

Status: tool-loop conformance and local native package builds are implemented
in all three SDKs; signed multi-platform release pipelines and broader
scenarios remain.

- Python, JavaScript/TypeScript, and Kotlin SDKs
- Equivalent event traces across SDKs
- Signed, multi-platform native release pipelines

## M5 — Durability and production semantics

- Checkpoint migrations
- Action ledger and replay safety
- Suspend/restart/resume validation
- Provider and store contract suites
