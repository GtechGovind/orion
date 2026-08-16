# Kotlin SDK instructions

Apply the root `AGENTS.md` plus these rules to `sdks/kotlin/`.

## Packages and API

- In every multi-member `class`, `interface`, `object`, and `companion object`,
  leave one blank line immediately after `{`, one blank line between properties,
  constructors, and functions, and one blank line immediately before `}`. Keep
  empty and intentionally one-line declarations compact.
- Apply the same grouping to data-class bodies containing constructors,
  companion objects, validation, or functions. Do not pad primary-constructor
  parameter lists, control flow, or builder lambdas.
- In every non-trivial function or method, leave one blank line immediately
  after `{`, blank lines between validation, normalization, construction,
  execution, and return phases, and one blank line immediately before `}`.
  Keep truly trivial one-line functions compact.
- Add short `//` phase comments when names and whitespace alone do not explain a
  cohesive flow. State intent, coroutine/lifecycle ordering, or an invariant;
  do not narrate Kotlin statements that are already self-explanatory.
- Add KDoc to every public class, interface, object, constructor, property,
  function, and exception. Document `@param`, `@return`, `@throws`, coroutine
  cancellation, thread/resource ownership, and examples wherever relevant.
- Keep the supported application surface in `dev.orion.sdk`: `Agent`, provider
  models such as `OpenAI`, `Tool`/`tool`, typed results/events, public error
  codes, and `OrionException`. Keep model contracts, execution machinery, and
  provider adapters in their `model`, `runtime`, and `provider` subpackages with
  `internal` visibility. Keep JNI/protocol conversion in `dev.orion.sdk.internal`.
- The canonical Kotlin tool declaration is
  `tool(name, description, function)`. Do not introduce shorthand such as
  `tool(::function)` because the explicit name is the stable model-visible ID
  and the description is required model guidance.
- Keep one public top-level type per same-named file. Do not turn a single
  `Orion.kt` file into a general container for unrelated SDK behavior.
- Keep `explicitApi()` satisfied with explicit public visibility and types.
  Treat package names, constructors, properties, suspend signatures, and Java
  visibility as compatibility surfaces only when root compatibility mode is
  enabled; otherwise update all repository consumers together.
- Use modern Kotlin supported by the pinned compiler: data/value classes, sealed
  interfaces, exhaustive `when`, extension functions, reified generics where
  justified, structured coroutines, and immutable collections at contracts.
- Public APIs must not accept raw `JsonObject`, `Map<String, Any?>`, or native
  handles when the shape is known. Convert boundary values into typed DTOs before
  calling adapters, runners, or application code.
- Prefer sealed types, enums, and value classes over string modes and boolean
  switches when evolving a versioned contract permits it.
- Keep `tool(name, description, function)` as the sole public tool factory.
  Require the explicit stable model-visible name and accept a typed suspending
  function reference; do not infer the name with Kotlin/JVM reflection or add
  competing lambda, raw-schema, serializer, builder, or overload-based paths.
- Compile and test imported top-level suspend function references because the
  Kotlin compiler may adapt them differently from bound member references.

## Coroutines, JNI, and errors

- Expose cold `Flow` streams and structured suspend APIs. Preserve
  `CancellationException`, cancel the native run in `NonCancellable`, and then
  rethrow it.
- Close every native handle with `use` or `finally`. JNI objects stay internal;
  public callers must not manage raw `Long` handles or native maps.
- Do not use `!!` in production paths. Validate native/provider JSON with named
  accessors that return actionable `OrionException` messages.
- Keep blocking Java HTTP, provider mapping, JNI conversion, and runner state
  advancement in separate packages. Do not block coroutine dispatcher threads.

## Verification

- From `sdks/kotlin`, run `./gradlew compileKotlin --no-daemon` while iterating.
- Before completion run
  `./gradlew test publishToMavenLocal --no-daemon`.
- When JNI declarations move, verify matching exported Rust symbol names and run
  a native lifecycle smoke test on every supported operating system in CI.
