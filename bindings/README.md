# Native bindings

Bindings are intentionally separated from public SDKs. Generated or low-level
binding code is an internal transport layer; it must not define the public
developer experience.

- `python/` will contain the PyO3/maturin integration.
- `javascript/` will contain the Node-API integration.
- `kotlin/` will contain the accepted JVM native-binding integration.

No binding implementation exists yet, so the language directories are not kept
as empty placeholder trees. Each directory should be created with its real build
metadata and source files when its binding implementation begins.
