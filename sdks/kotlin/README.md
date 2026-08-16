# Orion Kotlin SDK

[![Maven Central](https://img.shields.io/maven-central/v/io.github.gtechgovind/orion-kotlin-sdk?logo=apachemaven)](https://central.sonatype.com/artifact/io.github.gtechgovind/orion-kotlin-sdk)
[![JVM](https://img.shields.io/badge/JVM-17%2B-7f52ff?logo=kotlin)](https://central.sonatype.com/artifact/io.github.gtechgovind/orion-kotlin-sdk/0.0.1)

Kotlin/JVM SDK backed by an in-process JNI module. The supported API uses
`@Serializable` types, named suspending function references, typed `Agent`
results, and coroutine `Flow` streaming.

## Published release

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.gtechgovind:orion-kotlin-sdk:0.0.1")
}
```

The signed multi-platform JAR embeds JNI libraries for macOS arm64, Linux
x86-64, and Windows x86-64. Central mirrors can take a short time to synchronize
after publication. The
[`v0.0.1` release](https://github.com/GtechGovind/orion/releases/tag/v0.0.1)
also provides the runtime, sources, API documentation, POM, module metadata,
and checksums.

```kotlin
import dev.orion.sdk.Agent
import dev.orion.sdk.OpenAI
import dev.orion.sdk.tool
import kotlinx.serialization.Serializable

@Serializable
data class WeatherArguments(val city: String)

@Serializable
data class WeatherResult(val city: String, val temperatureC: Int)

@Serializable
data class WeatherAnswer(
    val city: String,
    val temperatureC: Int,
    val summary: String,
)

suspend fun getWeather(arguments: WeatherArguments): WeatherResult =
    WeatherResult(arguments.city, 31)

suspend fun main() {

    val agent = Agent(
        model = OpenAI("gpt-5-mini"),
        tools = listOf(
            tool(
                name = "weather",
                description = "Get the current weather for a city.",
                function = ::getWeather,
            ),
        ),
        output = WeatherAnswer.serializer(),
        instructions = "Use the weather tool.",
    )

    val result = agent.run("What is the weather in Delhi?")

    println(result.output.summary)

}
```

Use `agent.stream(...)` for a cold flow of `AgentEvent` values followed by one
typed `AgentResult`. `AgentEventKind` and `AgentErrorCode` make lifecycle payloads
and failures exhaustive. Raw schemas, registries, runners, adapters, model
references, protocol DTOs, and JNI handles are internal implementation details.

See the [complete weather application](../../examples/kotlin/weather-agent/src/main/kotlin/dev/orion/example/weather/Main.kt).

## Error handling

Every SDK failure is an `OrionException` with a stable `AgentErrorCode`, a
`retryable` decision, and an optional `retryAfterMilliseconds` delay. Provider
authentication, rate limits, timeouts, networks, malformed responses, tools,
configuration, unsupported capabilities, and `TURN_LIMIT_EXCEEDED` remain
distinct, so application policy does not parse messages.

```kotlin
try {
    val result = agent.run("What is the weather in Delhi?")
    println(result.output)
} catch (error: OrionException) {
    when (error.code) {
        AgentErrorCode.RATE_LIMITED -> println("Retry after ${error.retryAfterMilliseconds} ms")
        AgentErrorCode.TURN_LIMIT_EXCEEDED -> println("The agent exhausted its turn budget")
        else -> println("Agent failed: ${error.message}")
    }
}
```

`stream(...)` emits `AgentEventKind.RunFailed` before throwing an
`OrionException` carrying the same code and retry metadata. Coroutine
cancellation remains `CancellationException`; it is never converted into a
generic SDK failure.

## Native-library packaging

The SDK JAR contains JNI libraries under the deterministic resource convention
`META-INF/orion/native/<os>/<arch>/<filename>`, for example
`META-INF/orion/native/macos/aarch64/liborion_kotlin.dylib`. At runtime the SDK
selects the current platform, extracts the matching resource to an owner-only
temporary location where POSIX permissions are available, and loads it directly.
Applications do not configure `java.library.path`.

A multi-platform release build can pass
`-Porion.native.prebuiltDir=/absolute/staging/directory`. That directory must use
the `<os>/<arch>/<filename>` tree and can contain every CI-built target library;
the resulting JAR merges the complete tree. Supported directory names are
`macos`, `linux`, and `windows`, with `aarch64` or `x86_64` architectures.

The system library path is only an explicit development fallback. Enable it with
`-Ddev.orion.sdk.native.allowSystemLibraryPath=true` when intentionally testing
an unpackaged local native build. Release applications should never set it.

```bash
cd sdks/kotlin
./gradlew test publishToMavenLocal --no-daemon
./gradlew -p consumer-smoke run --no-daemon
```

## Publishing

`publishToMavenLocal` remains credential-free. It publishes the runtime JAR,
sources JAR, and Dokka-generated API documentation JAR for local development and
consumer smoke tests.

Central Portal publication is deliberately opt-in. Supply Portal user-token
credentials and an ASCII-armored OpenPGP private key through Gradle's standard
environment-to-property bridge, then invoke the remote task explicitly:

```bash
export ORG_GRADLE_PROJECT_orionCentralPublishing=true
export ORG_GRADLE_PROJECT_mavenCentralUsername='<portal-token-username>'
export ORG_GRADLE_PROJECT_mavenCentralPassword='<portal-token-password>'
export ORG_GRADLE_PROJECT_signingInMemoryKey="$(cat /secure/path/private-key.asc)"
export ORG_GRADLE_PROJECT_signingInMemoryKeyPassword='<private-key-password>'
# Optional when the signing key requires an explicit key ID:
export ORG_GRADLE_PROJECT_signingInMemoryKeyId='<long-key-id>'

./gradlew publishToMavenCentral --no-daemon
```

The task uploads a manually releasable Central Portal deployment; it does not
automatically release it. Never commit Portal tokens or signing material.
