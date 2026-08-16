package dev.orion.sdk

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.future.await
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

public data class ModelRef(public val provider: String, public val model: String) {
    public companion object {
        public fun parse(value: String): ModelRef {
            val separator = value.indexOf(':')
            require(separator > 0 && separator < value.lastIndex) {
                "model reference must use provider:model notation"
            }
            return ModelRef(value.substring(0, separator), value.substring(separator + 1))
        }
    }
}

public data class Usage(public val inputTokens: Long = 0, public val outputTokens: Long = 0)
public data class ModelProfile(
    val streaming: String = "unknown",
    val toolCalling: String = "unknown",
    val structuredOutput: String = "unknown",
    val parallelToolCalls: String = "unknown",
    val maxContextTokens: Long? = null,
)
public data class ToolCall(public val id: String, public val name: String, public val arguments: JsonElement)
public data class ModelResponse(
    val content: String = "",
    val toolCalls: List<ToolCall> = emptyList(),
    val finishReason: String = if (toolCalls.isEmpty()) "stop" else "tool_calls",
    val usage: Usage = Usage(),
    val providerState: JsonObject = buildJsonObject {},
)

public interface ModelAdapter {
    public val provider: String
    public fun profile(model: ModelRef): ModelProfile
    public suspend fun complete(request: JsonObject): ModelResponse
    public suspend fun close(): Unit = Unit
}

public class ModelRegistry(adapters: List<ModelAdapter>) {
    private val adapters = adapters.associateBy { it.provider }.also {
        require(it.size == adapters.size) { "model adapter providers must be unique" }
    }

    public fun resolve(model: ModelRef): ModelAdapter = adapters[model.provider]
        ?: throw OrionException("no model adapter registered for provider ${model.provider}")

    public suspend fun close(): Unit = adapters.values.forEach { it.close() }
}

public data class Tool(
    val name: String,
    val description: String,
    val inputSchema: JsonObject,
    val execute: suspend (JsonElement) -> JsonElement,
)

public data class Agent(
    val id: String,
    val name: String,
    val instructions: String,
    val model: ModelRef,
    val tools: List<Tool> = emptyList(),
    val outputSchema: JsonObject? = null,
    val temperature: Double? = null,
    val maxOutputTokens: Int? = null,
    val providerOptions: JsonObject = buildJsonObject {},
    val maxTurns: Int = 8,
) {
    public constructor(
        id: String,
        name: String,
        instructions: String,
        model: String,
        tools: List<Tool> = emptyList(),
    ) : this(id, name, instructions, ModelRef.parse(model), tools)

    internal fun toWire(): JsonObject = buildJsonObject {
        put("id", id)
        put("name", name)
        put("instructions", instructions)
        putJsonObject("model") {
            put("provider", model.provider)
            put("model", model.model)
        }
        putJsonArray("tools") {
            tools.forEach { tool ->
                add(buildJsonObject {
                    put("name", tool.name)
                    put("description", tool.description)
                    put("input_schema", tool.inputSchema)
                })
            }
        }
        put("output_schema", outputSchema ?: JsonNull)
        putJsonObject("model_settings") {
            put("temperature", temperature?.let(::JsonPrimitive) ?: JsonNull)
            put("max_output_tokens", maxOutputTokens?.let(::JsonPrimitive) ?: JsonNull)
            put("provider_options", providerOptions)
        }
        put("max_turns", maxTurns)
    }
}

public sealed interface RunItem
public data class RunEvent(
    val runId: String,
    val sequence: Long,
    val type: String,
    val data: JsonObject,
) : RunItem

public data class RunResult(
    val runId: String,
    val output: String,
    val usage: Usage,
    val turns: Int,
    val events: List<RunEvent>,
) : RunItem

public class OrionException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

internal object NativeKernel {
    init {
        System.loadLibrary("orion_kotlin")
    }

    @JvmStatic external fun create(command: Map<String, Any?>): Long
    @JvmStatic external fun takeStep(handle: Long): Map<String, Any?>
    @JvmStatic external fun resume(handle: Long, result: Map<String, Any?>): Map<String, Any?>
    @JvmStatic external fun cancel(handle: Long): Map<String, Any?>
    @JvmStatic external fun fail(handle: Long, error: Map<String, Any?>): Map<String, Any?>
    @JvmStatic external fun close(handle: Long)
}

private class NativeRun(command: JsonObject) : AutoCloseable {
    private var handle = NativeKernel.create(command.toNativeMap())

    fun takeStep(): JsonObject = NativeKernel.takeStep(openHandle()).toJson().jsonObject
    fun resume(result: JsonObject): JsonObject =
        NativeKernel.resume(openHandle(), result.toNativeMap()).toJson().jsonObject
    fun cancel(): JsonObject = NativeKernel.cancel(openHandle()).toJson().jsonObject
    fun fail(error: JsonObject): JsonObject =
        NativeKernel.fail(openHandle(), error.toNativeMap()).toJson().jsonObject

    private fun openHandle(): Long = handle.takeIf { it != 0L }
        ?: throw OrionException("native run is already closed")

    override fun close() {
        if (handle != 0L) {
            NativeKernel.close(handle)
            handle = 0L
        }
    }
}

private fun JsonElement.toNative(): Any? = when (this) {
    JsonNull -> null
    is JsonObject -> mapValues { (_, value) -> value.toNative() }
    is JsonArray -> map { it.toNative() }
    is JsonPrimitive -> booleanOrNull ?: longOrNull ?: doubleOrNull ?: content
}

private fun JsonObject.toNativeMap(): Map<String, Any?> = mapValues { (_, value) -> value.toNative() }

private fun Any?.toJson(): JsonElement = when (this) {
    null -> JsonNull
    is Boolean -> JsonPrimitive(this)
    is Byte -> JsonPrimitive(toLong())
    is Short -> JsonPrimitive(toLong())
    is Int -> JsonPrimitive(toLong())
    is Long -> JsonPrimitive(this)
    is Float -> JsonPrimitive(toDouble())
    is Double -> JsonPrimitive(this)
    is String -> JsonPrimitive(this)
    is List<*> -> JsonArray(map { it.toJson() })
    is Map<*, *> -> JsonObject(entries.associate { (key, value) ->
        (key as? String ?: throw OrionException("native map key is not a string")) to value.toJson()
    })
    else -> throw OrionException("native kernel returned an unsupported value")
}

public class Runner(private val models: ModelRegistry) {

    public suspend fun run(
        agent: Agent,
        input: String,
        runId: String = "run-${UUID.randomUUID()}",
    ): RunResult = runStream(agent, input, runId).toList().filterIsInstance<RunResult>().single()

    public fun runStream(
        agent: Agent,
        input: String,
        runId: String = "run-${UUID.randomUUID()}",
    ): Flow<RunItem> = flow {
        val profile = models.resolve(agent.model).profile(agent.model)
        require(agent.tools.isEmpty() || profile.toolCalling != "unsupported") {
            "model ${agent.model.provider}:${agent.model.model} does not support tool calling"
        }
        require(agent.outputSchema == null || profile.structuredOutput != "unsupported") {
            "model ${agent.model.provider}:${agent.model.model} does not support structured output"
        }
        val native = NativeRun(buildJsonObject {
            put("run_id", runId)
            put("agent", agent.toWire())
            put("input", input)
        })
        var step = native.takeStep()
        val events = mutableListOf<RunEvent>()
        try {
          while (true) {
            step["events"]!!.jsonArray.forEach { value ->
                val raw = value.jsonObject
                val kind = raw["kind"]!!.jsonObject
                val event = RunEvent(
                    raw["run_id"]!!.jsonPrimitive.content,
                    raw["sequence"]!!.jsonPrimitive.long,
                    kind["type"]!!.jsonPrimitive.content,
                    JsonObject(kind.filterKeys { it != "type" }),
                )
                events += event
                emit(event)
            }
            val terminal = step["result"]
            if (terminal != null && terminal !is JsonNull) {
                val value = terminal.jsonObject
                val usage = value["usage"]!!.jsonObject
                emit(RunResult(
                    value["run_id"]!!.jsonPrimitive.content,
                    value["output"]!!.jsonPrimitive.content,
                    Usage(
                        usage["input_tokens"]!!.jsonPrimitive.long,
                        usage["output_tokens"]!!.jsonPrimitive.long,
                    ),
                    value["turns"]!!.jsonPrimitive.int,
                    events.toList(),
                ))
                return@flow
            }
            val effect = step["effect"]?.takeUnless { it is JsonNull }?.jsonObject
                ?: throw OrionException("run terminated without a successful result")
            try {
                val result = executeEffect(agent, effect)
                step = native.resume(result)
            } catch (error: CancellationException) {
                withContext(NonCancellable) { native.cancel() }
                throw error
            } catch (error: Exception) {
                native.fail(buildJsonObject {
                    put("code", if (effect["type"]?.jsonPrimitive?.content == "call_model") "provider" else "tool")
                    put("message", (error.message ?: error::class.simpleName.orEmpty()).take(4096))
                    put("retryable", false)
                    put("retry_after_ms", JsonNull)
                })
                throw OrionException("run failed", error)
            }
          }
        } finally {
            native.close()
        }
    }

    private suspend fun executeEffect(agent: Agent, effect: JsonObject): JsonObject {
        if (effect["type"]!!.jsonPrimitive.content == "call_model") {
            val request = effect["request"]!!.jsonObject
            val model = request["model"]!!.jsonObject
            val response = models.resolve(ModelRef(
                model["provider"]!!.jsonPrimitive.content,
                model["model"]!!.jsonPrimitive.content,
            )).complete(request)
            return buildJsonObject {
                put("type", "model")
                putJsonObject("value") {
                    put("content", response.content)
                    putJsonArray("tool_calls") {
                        response.toolCalls.forEach { call ->
                            add(buildJsonObject {
                                put("id", call.id)
                                put("name", call.name)
                                put("arguments", call.arguments)
                            })
                        }
                    }
                    put("finish_reason", response.finishReason)
                    putJsonObject("usage") {
                        put("input_tokens", response.usage.inputTokens)
                        put("output_tokens", response.usage.outputTokens)
                    }
                    put("provider_state", response.providerState)
                }
            }
        }
        val call = effect["call"]!!.jsonObject
        val tool = agent.tools.find { it.name == call["name"]!!.jsonPrimitive.content }
            ?: throw OrionException("model requested an unregistered tool")
        val content = tool.execute(call["arguments"]!!)
        return buildJsonObject {
            put("type", "tool")
            putJsonObject("value") { put("content", content) }
        }
    }

}

public class OpenAICompatibleAdapter(
    override val provider: String = "openai",
    private val apiKey: String? = System.getenv("OPENAI_API_KEY"),
    baseUrl: String = "https://api.openai.com/v1",
    timeout: Duration = Duration.ofSeconds(60),
) : ModelAdapter {
    private val endpoint = "${baseUrl.trimEnd('/')}/chat/completions"
    private val timeout = timeout.also { require(!it.isZero && !it.isNegative) }
    private val client = HttpClient.newBuilder().connectTimeout(timeout).build()

    override fun profile(model: ModelRef): ModelProfile {
        require(model.provider == provider) { "model provider does not match adapter" }
        return ModelProfile(
            streaming = "unsupported",
            toolCalling = "native",
            structuredOutput = "native",
        )
    }

    override suspend fun complete(request: JsonObject): ModelResponse {
        val settings = request["settings"]!!.jsonObject
        val payload = buildJsonObject {
            put("model", request["model"]!!.jsonObject["model"]!!)
            putJsonArray("messages") {
                request["messages"]!!.jsonArray.forEach { raw ->
                    val message = raw.jsonObject
                    add(buildJsonObject {
                        put("role", message["role"]!!)
                        put("content", message["content"]!!)
                        message["tool_call_id"]?.takeUnless { it is JsonNull }
                            ?.let { put("tool_call_id", it) }
                        if (message["tool_calls"]?.jsonArray?.isNotEmpty() == true) {
                            putJsonArray("tool_calls") {
                                message["tool_calls"]!!.jsonArray.forEach { rawCall ->
                                    val call = rawCall.jsonObject
                                    add(buildJsonObject {
                                        put("id", call["id"]!!)
                                        put("type", "function")
                                        putJsonObject("function") {
                                            put("name", call["name"]!!)
                                            put("arguments", call["arguments"]!!.toString())
                                        }
                                    })
                                }
                            }
                        }
                    })
                }
            }
            if (request["tools"]!!.jsonArray.isNotEmpty()) {
                putJsonArray("tools") {
                    request["tools"]!!.jsonArray.forEach { raw ->
                        val tool = raw.jsonObject
                        add(buildJsonObject {
                            put("type", "function")
                            putJsonObject("function") {
                                put("name", tool["name"]!!)
                                put("description", tool["description"]!!)
                                put("parameters", tool["input_schema"]!!)
                            }
                        })
                    }
                }
            }
            settings["temperature"]?.takeUnless { it is JsonNull }?.let { put("temperature", it) }
            settings["max_output_tokens"]?.takeUnless { it is JsonNull }
                ?.let { put("max_tokens", it) }
            request["output_schema"]?.takeUnless { it is JsonNull }?.let { schema ->
                putJsonObject("response_format") {
                    put("type", "json_schema")
                    putJsonObject("json_schema") {
                        put("name", "orion_output")
                        put("schema", schema)
                        put("strict", true)
                    }
                }
            }
            val providerOptions = settings["provider_options"]?.jsonObject?.get(provider)?.jsonObject
            val protectedFields = setOf("model", "messages", "tools", "response_format")
            val overridden = providerOptions?.keys?.intersect(protectedFields).orEmpty()
            require(overridden.isEmpty()) {
                "provider options cannot override protected fields: ${overridden.sorted().joinToString()}"
            }
            providerOptions?.forEach { (key, value) -> put(key, value) }
        }
        val builder = HttpRequest.newBuilder(URI(endpoint))
            .timeout(timeout)
            .header("content-type", "application/json")
        apiKey?.let { builder.header("authorization", "Bearer $it") }
        val response = client.sendAsync(
            builder.POST(HttpRequest.BodyPublishers.ofString(payload.toString())).build(),
            HttpResponse.BodyHandlers.ofString(),
        ).await()
        if (response.statusCode() !in 200..299) {
            throw OrionException(
                "model provider returned HTTP ${response.statusCode()}: ${response.body().take(4096)}",
            )
        }
        val data = Json.parseToJsonElement(response.body()).jsonObject
        val choice = data["choices"]!!.jsonArray.first().jsonObject
        val message = choice["message"]!!.jsonObject
        val calls = message["tool_calls"]?.jsonArray?.map { raw ->
            val call = raw.jsonObject
            val function = call["function"]!!.jsonObject
            ToolCall(
                call["id"]!!.jsonPrimitive.content,
                function["name"]!!.jsonPrimitive.content,
                Json.parseToJsonElement(function["arguments"]!!.jsonPrimitive.content),
            )
        } ?: emptyList()
        val usage = data["usage"]?.jsonObject
        return ModelResponse(
            message["content"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content ?: "",
            calls,
            if (calls.isEmpty()) choice["finish_reason"]?.jsonPrimitive?.content ?: "other" else "tool_calls",
            Usage(
                usage?.get("prompt_tokens")?.jsonPrimitive?.long ?: 0,
                usage?.get("completion_tokens")?.jsonPrimitive?.long ?: 0,
            ),
        )
    }
}
