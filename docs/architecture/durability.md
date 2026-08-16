# Durability model

Durability is more than serializing run state.

The future checkpoint model must connect:

- execution cursor
- ordered state
- pending actions
- completed action identities and receipts
- suspension requirements
- schema and framework compatibility

For externally side-effecting operations, the host SDK executes the operation
and returns an identity or receipt. The kernel records that durable evidence
before advancing beyond the relevant checkpoint boundary.

Initial durability should target checkpoint-after-turn and
checkpoint-on-suspend. More sophisticated replay models require evidence from
real use cases and a dedicated ADR.
