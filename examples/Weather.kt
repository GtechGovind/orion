import dev.orion.sdk.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

fun main() = runBlocking {
    val weather = Tool("weather", "Get temperature", buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { putJsonObject("city") { put("type", "string") } }
        putJsonArray("required") { add("city") }
    }) { arguments -> buildJsonObject {
        put("city", arguments.jsonObject["city"]!!)
        put("temperature_c", 31)
    } }
    val agent = Agent("weather", "Weather", "Use the weather tool and answer briefly.",
        "openai:gpt-5-mini", listOf(weather))
    val result = Runner(ModelRegistry(listOf(OpenAICompatibleAdapter()))).run(agent, "Weather in Delhi?")
    println(result.output)
}
