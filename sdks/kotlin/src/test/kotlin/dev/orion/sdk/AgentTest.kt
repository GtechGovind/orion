package dev.orion.sdk

import dev.orion.sdk.model.CapabilitySupport
import dev.orion.sdk.model.ModelAdapter
import dev.orion.sdk.model.ModelProfile
import dev.orion.sdk.model.ModelRef
import dev.orion.sdk.model.ModelRequest
import dev.orion.sdk.model.ModelResponse
import dev.orion.sdk.model.ToolCall
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
private data class TopLevelToolArguments(val value: String)

@Serializable
private data class TopLevelToolResult(val value: String)

private suspend fun topLevelTool(arguments: TopLevelToolArguments): TopLevelToolResult =
    TopLevelToolResult(arguments.value)

class AgentTest {

    @Serializable
    private data class WeatherArguments(val city: String)

    @Serializable
    private data class WeatherResult(val city: String, val temperatureC: Int)

    @Serializable
    private data class WeatherAnswer(
        val city: String,
        val temperatureC: Int,
        val summary: String,
    )

    private suspend fun weather(arguments: WeatherArguments): WeatherResult =
        WeatherResult(arguments.city, 31)

    private suspend fun failingWeather(@Suppress("UNUSED_PARAMETER") arguments: WeatherArguments): WeatherResult =
        throw IllegalStateException("weather database is unavailable")

    @Test
    fun topLevelFunctionReferenceUsesStableToolName(): Unit {

        val definition = tool(
            name = "echo_value",
            description = "Echo one value.",
            function = ::topLevelTool,
        ).definition

        assertEquals("echo_value", definition.name)

    }

    @Test
    fun functionToolAndStructuredOutputAreAutomatic(): Unit = runBlocking {

        val adapter = FakeModel()
        val agent = Agent(
            model = configuredModel(ModelRef("fake", "test"), adapter),
            tools = listOf(tool(
                name = "weather",
                description = "Get the current weather for a city.",
                function = ::weather,
            )),
            output = WeatherAnswer.serializer(),
            instructions = "Use the weather tool.",
        )

        val result = agent.run("Weather?")

        assertEquals(31, result.output.temperatureC)
        assertEquals("Delhi is 31 C.", result.output.summary)
        assertEquals(2, result.turns)
        assertEquals(2, adapter.calls)

    }

    @Test
    fun streamFinishesWithTheSameTypedResult(): Unit = runBlocking {

        val agent = Agent(
            model = configuredModel(ModelRef("fake", "test"), FakeModel()),
            tools = listOf(tool(
                name = "weather",
                description = "Get the current weather for a city.",
                function = ::weather,
            )),
            output = WeatherAnswer.serializer(),
        )

        val items = agent.stream("Weather?").toList()
        val started = assertIs<AgentEvent>(items.first())
        val terminal = items.last()

        assertIs<AgentEventKind.RunStarted>(started.kind)
        assertIs<AgentResult<WeatherAnswer>>(terminal)
        assertEquals("Delhi", terminal.output.city)

    }

    @Test
    fun providerFailurePreservesStableRetryMetadata(): Unit = runBlocking {

        val agent = Agent(
            model = configuredModel(ModelRef("fake", "limited"), RateLimitedModel()),
            output = WeatherAnswer.serializer(),
        )
        val observed = mutableListOf<AgentStreamItem<WeatherAnswer>>()

        val error = assertFailsWith<OrionException> {
            agent.stream("Weather?").collect { observed += it }
        }
        val terminal = observed.filterIsInstance<AgentEvent>().last().kind
        val failure = assertIs<AgentEventKind.RunFailed>(terminal)

        assertEquals(AgentErrorCode.RATE_LIMITED, error.code)
        assertEquals(true, error.retryable)
        assertEquals(1_250, error.retryAfterMilliseconds)
        assertEquals(error.code, failure.code)
        assertEquals(error.retryable, failure.retryable)
        assertEquals(error.retryAfterMilliseconds, failure.retryAfterMilliseconds)

    }

    @Test
    fun applicationToolFailureUsesToolCodeAndRetainsCause(): Unit = runBlocking {

        val agent = Agent(
            model = configuredModel(ModelRef("fake", "tool"), AlwaysCallsToolModel("weather")),
            tools = listOf(tool(
                name = "weather",
                description = "Get the current weather for a city.",
                function = ::failingWeather,
            )),
            output = WeatherAnswer.serializer(),
        )

        val error = assertFailsWith<OrionException> { agent.run("Weather?") }

        assertEquals(AgentErrorCode.TOOL, error.code)
        assertEquals(false, error.retryable)
        assertNull(error.retryAfterMilliseconds)
        assertIs<IllegalStateException>(error.cause)

    }

    @Test
    fun rustTurnLimitFailureReachesPublicException(): Unit = runBlocking {

        val agent = Agent(
            model = configuredModel(ModelRef("fake", "loop"), AlwaysCallsToolModel("weather")),
            tools = listOf(tool(
                name = "weather",
                description = "Get the current weather for a city.",
                function = ::weather,
            )),
            output = WeatherAnswer.serializer(),
            maxTurns = 1,
        )

        val error = assertFailsWith<OrionException> { agent.run("Weather?") }

        assertEquals(AgentErrorCode.TURN_LIMIT_EXCEEDED, error.code)
        assertEquals("agent reached its configured model turn limit", error.message)
        assertEquals(false, error.retryable)
        assertNull(error.retryAfterMilliseconds)

    }

    @Test
    fun unsupportedCapabilityFailsWithStableCode(): Unit = runBlocking {

        val agent = Agent(
            model = configuredModel(ModelRef("fake", "unsupported"), UnsupportedModel()),
            output = WeatherAnswer.serializer(),
        )

        val error = assertFailsWith<OrionException> { agent.run("Weather?") }

        assertEquals(AgentErrorCode.UNSUPPORTED_CAPABILITY, error.code)

    }

    @Test
    fun modelConfigurationFailureUsesConfigurationCode(): Unit = runBlocking {

        val agent = Agent(
            model = configuredModel(ModelRef("fake", "misconfigured"), MisconfiguredModel()),
            output = WeatherAnswer.serializer(),
        )

        val error = assertFailsWith<OrionException> { agent.run("Weather?") }

        assertEquals(AgentErrorCode.CONFIGURATION, error.code)

    }

    @Test
    fun coroutineCancellationRemainsCancellationException(): Unit = runBlocking {

        val enteredProvider = CompletableDeferred<Unit>()
        val agent = Agent(
            model = configuredModel(
                ModelRef("fake", "waiting"),
                WaitingModel(enteredProvider),
            ),
            output = WeatherAnswer.serializer(),
        )
        val running = async { agent.run("Weather?") }

        enteredProvider.await()
        running.cancel()

        assertFailsWith<CancellationException> { running.await() }

    }

    private class FakeModel : ModelAdapter {

        override val provider: String = "fake"

        var calls: Int = 0
            private set

        override fun profile(model: ModelRef): ModelProfile = ModelProfile(
            toolCalling = CapabilitySupport.NATIVE,
            structuredOutput = CapabilitySupport.NATIVE,
        )

        override suspend fun complete(request: ModelRequest): ModelResponse {

            calls += 1

            return if (calls == 1) {
                ModelResponse(
                    toolCalls = listOf(ToolCall(
                        id = "c1",
                        name = "weather",
                        arguments = buildJsonObject { put("city", "Delhi") },
                    )),
                )
            } else {
                ModelResponse(
                    content = """{"city":"Delhi","temperatureC":31,"summary":"Delhi is 31 C."}""",
                )
            }

        }

    }

    private class RateLimitedModel : ModelAdapter {

        override val provider: String = "fake"

        override fun profile(model: ModelRef): ModelProfile = supportedProfile()

        override suspend fun complete(request: ModelRequest): ModelResponse = throw OrionException(
            message = "model provider rate limit exceeded",
            code = AgentErrorCode.RATE_LIMITED,
            retryable = true,
            retryAfterMilliseconds = 1_250,
        )

    }

    private class AlwaysCallsToolModel(private val toolName: String) : ModelAdapter {

        override val provider: String = "fake"

        override fun profile(model: ModelRef): ModelProfile = supportedProfile()

        override suspend fun complete(request: ModelRequest): ModelResponse = ModelResponse(
            toolCalls = listOf(ToolCall(
                id = "c1",
                name = toolName,
                arguments = buildJsonObject { put("city", "Delhi") },
            )),
        )

    }

    private class UnsupportedModel : ModelAdapter {

        override val provider: String = "fake"

        override fun profile(model: ModelRef): ModelProfile = ModelProfile(
            structuredOutput = CapabilitySupport.UNSUPPORTED,
        )

        override suspend fun complete(request: ModelRequest): ModelResponse =
            error("unsupported model must fail before execution")

    }

    private class MisconfiguredModel : ModelAdapter {

        override val provider: String = "fake"

        override fun profile(model: ModelRef): ModelProfile = throw OrionException(
            message = "model configuration is invalid",
            code = AgentErrorCode.CONFIGURATION,
        )

        override suspend fun complete(request: ModelRequest): ModelResponse =
            error("misconfigured model must fail before execution")

    }

    private class WaitingModel(private val entered: CompletableDeferred<Unit>) : ModelAdapter {

        override val provider: String = "fake"

        override fun profile(model: ModelRef): ModelProfile = supportedProfile()

        override suspend fun complete(request: ModelRequest): ModelResponse {

            entered.complete(Unit)

            CompletableDeferred<Unit>().await()

            error("unreachable")

        }

    }

}

private fun supportedProfile(): ModelProfile = ModelProfile(
    toolCalling = CapabilitySupport.NATIVE,
    structuredOutput = CapabilitySupport.NATIVE,
)
