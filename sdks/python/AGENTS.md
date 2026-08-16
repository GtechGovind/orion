# Python SDK instructions

Apply the root `AGENTS.md` plus these rules to `sdks/python/`.

## Structure and API

- Mirror the shared capability layout with `model/`, `runtime/`, `provider/`,
  and `_internal/`. Keep `orion_sdk/__init__.py` as a documentation-backed public
  re-export facade. Add legacy import facades only when root compatibility mode
  is enabled; otherwise remove obsolete paths.
- In a multi-member class or protocol, use blank lines to separate attributes,
  constructors, and methods, including visual breathing room after the class
  docstring. Do not pad empty classes or one-line protocol members.
- In a non-trivial function or method, leave a blank line after its docstring or
  initial preamble, use blank lines between validation, normalization, execution,
  and return phases, and leave a blank line before the next declaration. Keep
  trivial one-line bodies compact and remain Ruff-compatible.
- Add short `#` phase comments when names and whitespace alone do not explain a
  cohesive flow. State the intent or invariant; do not narrate assignments,
  calls, or returns that are already clear from the code.
- Add docstrings to every public module, class, protocol, function, method, and
  exception. Describe raised exceptions, async cancellation, resource ownership,
  and argument/return contracts; add examples to non-obvious entry points.
- Keep public models and protocols separate from runner orchestration, provider
  adapters, and `_native` internals. Re-export the intentional public surface
  from `orion_sdk/__init__.py`.
- Use idiomatic Python names and preserve PEP 561 typing through `py.typed` and an
  accurate native-module stub.
- Require precise annotations for public and internal production code. Avoid
  `Any`, untyped dictionaries, and unchecked casts when a protocol, dataclass,
  `TypedDict`, enum, or type guard can express the contract.
- Use modern Python features available in the declared Python floor, including
  built-in generics, `X | Y`, dataclasses, `Protocol`, `TypedDict`, `TypeAlias`,
  and exhaustive narrowing. Do not raise the Python floor implicitly.
- Convert dynamic native/provider dictionaries into typed dataclasses or
  `TypedDict` values at the boundary. Internal adapters and runners receive
  typed request/response objects, not `Mapping[str, Any]`, and must not be
  re-exported as an alternate application API.

## Async, errors, and resources

- Never block the event loop with provider, tool, storage, or native work.
  Preserve `asyncio.CancelledError` and cancel the native run before re-raising.
- Close native sessions in `finally` blocks or context managers on every path.
- Translate native and provider failures to documented SDK exceptions without
  losing the original cause or exposing secrets.
- Keep provider-specific request/response mapping inside its adapter.

## Verification

- Run `python -m compileall sdks/python/src`.
- Run `ruff check sdks/python/src sdks/python/tests`.
- Run `pyright -p sdks/python`.
- Build with Maturin and run
  `python -m unittest discover -s sdks/python/tests -v` when behavior changes.
