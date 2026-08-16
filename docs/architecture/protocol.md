# Protocol specification outline

The protocol is not implemented. This document defines the work required before
types are added to `orion-protocol`.

## Envelope families

- Commands: create, step, resume, cancel, inspect
- Effects: model call, tool execution, approval, external input, persistence
- Results: normalized provider/tool/storage outcomes
- Events: immutable ordered lifecycle observations
- Terminal outcomes: completed, failed, cancelled

## Required metadata

- `schema_version`
- `framework_version`
- `run_id`
- `session_id` when applicable
- monotonic sequence or cursor
- correlation and causation identities
- compatibility metadata

## Open decisions

- Binary and text encodings
- Batch and streaming transport shapes
- Unknown-field and unknown-variant policy
- FFI error representation
- Payload size and resource limits
- Timestamp and monotonic-clock treatment

These decisions require ADRs and cross-language prototypes.
