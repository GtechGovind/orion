# Native binding instructions

Apply the root `AGENTS.md` plus these rules to code under `bindings/`.

## Boundary design

- Follow the scoped host-language spacing and documentation rules for exposed
  declarations and the Rust rules for binding implementations. Visually
  separate lifecycle, conversion, failure, and exported-entry-point groups.
- Add concise phase comments where direct conversion or lifecycle code remains
  non-obvious after extraction. Comments must explain ownership, validation, or
  safety intent rather than translate the code into prose.
- Document every exported native entry point, opaque handle lifecycle, ownership
  transfer, accepted DTO shape, translated exception, and safety invariant.
- Bindings are thin conversion and lifecycle adapters around `orion-ffi`.
  Provider clients, orchestration policy, and public SDK ergonomics do not belong
  here.
- Expose opaque native sessions with explicit close/release operations. Reject
  invalid, unknown, closed, or cross-thread handles safely.
- Convert language maps, lists, scalars, and DTOs directly to owned protocol
  values. Do not add a subprocess driver or JSON-string transport for an entire
  kernel transition.
- Keep Python, JavaScript, and Kotlin behavior equivalent, including error codes,
  cancellation, numeric conversions, null handling, and resource release.

## Safety and failures

- Validate type, range, and shape at the boundary before entering the kernel.
- No panic or Rust unwind may cross PyO3, Node-API, or JNI. Catch it at the
  smallest boundary and translate it to the SDK's stable exception shape.
- Keep `unsafe` blocks minimal and add a `SAFETY:` comment stating every required
  lifetime, pointer, ownership, and thread invariant.
- Never log or include credentials, full provider payloads, or arbitrary host
  objects in an exception.

## Verification

- Run the affected binding build plus its host SDK tests.
- Run Rust formatting and Clippy for the affected binding crate.
- Test invalid values, double close, use after close, panic translation, and
  platform-specific library loading when those paths change.
