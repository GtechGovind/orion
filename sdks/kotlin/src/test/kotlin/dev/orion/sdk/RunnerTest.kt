package dev.orion.sdk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class RunnerTest {
    @Test
    fun toolLoopExecutesThroughRustKernel() = runBlocking {
        var calls = 0
        val model = object : ModelAdapter {
            override val provider = "fake"
            override fun profile(model: ModelRef) = ModelProfile(toolCalling = "native")
            override suspend fun complete(request: JsonObject): ModelResponse =
                if (++calls == 1) {
                    ModelResponse(toolCalls = listOf(
                        ToolCall("c1", "weather", buildJsonObject { put("city", "Delhi") }),
                    ))
                } else {
                    ModelResponse(content = "Delhi is 31 C")
                }
        }
        val agent = Agent(
            "weather", "Weather", "Be concise", "fake:test",
            listOf(Tool(
                "weather", "Get weather", buildJsonObject { put("type", "object") },
            ) { buildJsonObject { put("temperature", 31) } }),
        )
        val result = Runner(ModelRegistry(listOf(model))).run(agent, "Weather?")
        assertEquals("Delhi is 31 C", result.output)
        assertEquals(2, result.turns)
        assertEquals("run_completed", result.events.last().type)
    }
}
