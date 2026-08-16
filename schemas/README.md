# Schemas

`protocol-v1.schema.json` describes every DTO that crosses a native binding:
start commands, effects, effect results, errors, events, and steps. It does not
define a JSON string transport or expose mutable `RunState`. Rust Serde types
are authoritative during `0.1`; the schema is a reviewable language-neutral
contract that CI must keep synchronized.

Generated language types must never be edited by hand. Schema generation and
compatibility checks become mandatory before a stable native ABI.
