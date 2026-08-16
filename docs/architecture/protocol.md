# Protocol specification

Version `1.0` is implemented by `orion-protocol` and represented for host
languages by `schemas/protocol-v1.schema.json`.

## Envelope families

- Commands: start, resume, cancel, and fail through native session methods
- Effects: model call and tool execution
- Results: normalized model and tool outcomes
- Events: immutable ordered lifecycle observations
- Terminal outcomes: completed, failed, cancelled

## Required metadata

- `run_id`
- monotonic event sequence
- stable action and tool-call identities
- protocol major and minor version constants

## Boundary behavior

Bindings convert native dict/object/map DTOs directly to owned Rust protocol
types. No JSON string transport or serialized run state is used during normal
execution. Rust Serde types are authoritative. Provider state and schema fields
remain JSON-compatible values because their public meaning is dynamic. At most
one effect is outstanding.

## Open decisions after the pilot

- Durable checkpoint export/import encoding
- Batch and streaming transport shapes
- Unknown-field and unknown-variant policy
- Stable native error subclasses/codes
- Payload size and resource limits
- Timestamp and monotonic-clock treatment

These decisions require new ADRs and cross-language conformance evidence.
