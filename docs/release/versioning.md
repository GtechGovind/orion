# Versioning and compatibility

Version `0.0.x` is an alpha contract and may change incompatibly before `1.0`.
The canonical development switch is `COMPATIBILITY_MODE` in the root
`AGENTS.md`; it is currently `disabled`. While disabled, contributors prefer a
clean contract and update all repository consumers together instead of retaining
aliases or migration facades. Enabling that flag, or an explicit user request,
activates compatibility-preservation and migration requirements.

Before the first published package, Orion must define:

- coordinated versus independent crate and SDK versions
- protocol-schema versioning
- checkpoint migration and rejection behavior
- minimum supported Rust, Python, Node.js, JVM, and Kotlin versions
- deprecation windows
- native binary support matrix
- compatibility test policy

Public SDK compatibility and durable-format compatibility are separate promises
and must be versioned separately where appropriate.
