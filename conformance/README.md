# Cross-language conformance

Conformance scenarios specify observable runtime behavior independently of an
SDK implementation.

Each scenario will define:

- initial command and durable state
- scripted host effect results
- expected semantic events
- expected terminal or suspended outcome
- permitted language-specific differences

The executable SDK tests currently cover direct completion and a tool call
followed by completion through the Rust native session. `scenarios/tool-loop.json`
records the common expected trace. Future scenarios are:

1. Direct completion
2. Tool call followed by completion
3. Multiple independent tool effects
4. Tool error and scoped retry
5. Handoff
6. Approval suspension and resume
7. Cancellation during model effect
8. Cancellation during tool effects
9. Crash before action receipt
10. Crash after action receipt and before checkpoint advance
