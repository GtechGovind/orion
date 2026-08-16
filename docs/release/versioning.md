# Versioning and compatibility

No compatibility guarantee exists at version `0.0.0`.

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
