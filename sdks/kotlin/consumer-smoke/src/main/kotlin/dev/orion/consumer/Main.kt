package dev.orion.consumer

import dev.orion.sdk.Agent
import dev.orion.sdk.OpenAI
import dev.orion.sdk.OrionException
import java.time.Duration
import kotlinx.serialization.Serializable

@Serializable
private data class SmokeOutput(val loaded: Boolean)

suspend fun main(): Unit {

    val agent = Agent(
        model = OpenAI(
            model = "native-loader-smoke",
            apiKey = null,
            baseUrl = "http://127.0.0.1:1",
            timeout = Duration.ofMillis(250),
        ),
        output = SmokeOutput.serializer(),
        instructions = "This request verifies native-library loading.",
        maxTurns = 1,
    )

    try {
        agent.run("Verify the packaged native library.")
        error("local smoke endpoint unexpectedly returned a model response")
    } catch (error: OrionException) {
        check(error.causeChain().none { it is UnsatisfiedLinkError }) {
            "published SDK could not load its packaged JNI library"
        }
        println("external consumer loaded packaged Orion JNI library")
    }

}

private fun Throwable.causeChain(): Sequence<Throwable> = generateSequence(this) { current ->
    current.cause
}
