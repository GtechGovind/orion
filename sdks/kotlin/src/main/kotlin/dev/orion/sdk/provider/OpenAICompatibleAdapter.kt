package dev.orion.sdk.provider

import dev.orion.sdk.AgentErrorCode
import dev.orion.sdk.OrionException
import dev.orion.sdk.internal.asArray
import dev.orion.sdk.internal.asObject
import dev.orion.sdk.internal.requiredArray
import dev.orion.sdk.internal.requiredObject
import dev.orion.sdk.internal.requiredString
import dev.orion.sdk.model.CapabilitySupport
import dev.orion.sdk.model.FinishReason
import dev.orion.sdk.model.ModelAdapter
import dev.orion.sdk.model.ModelMessage
import dev.orion.sdk.model.ModelProfile
import dev.orion.sdk.model.ModelRef
import dev.orion.sdk.model.ModelRequest
import dev.orion.sdk.model.ModelResponse
import dev.orion.sdk.model.MessageRole
import dev.orion.sdk.model.ToolCall
import dev.orion.sdk.model.ToolDefinition
import dev.orion.sdk.model.Usage
import java.net.URI
import java.net.http.HttpTimeoutException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.future.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Calls an OpenAI-compatible Chat Completions endpoint.
 *
 * The adapter owns its HTTP client and performs no kernel state transitions.
 *
 * @param provider registry key accepted by this adapter.
 * @param apiKey bearer credential; `null` omits authorization for local endpoints.
 * @param baseUrl API root containing `/chat/completions`.
 * @param timeout connection and request timeout.
 */
internal class OpenAICompatibleAdapter(
    override val provider: String = "openai",
    private val apiKey: String? = System.getenv("OPENAI_API_KEY"),
    baseUrl: String = "https://api.openai.com/v1",
    timeout: Duration = Duration.ofSeconds(60),
) : ModelAdapter {

    private val endpoint: URI = URI("${baseUrl.trimEnd('/')}/chat/completions")
    private val timeout: Duration = timeout.also { require(!it.isZero && !it.isNegative) }
    private val client: HttpClient = HttpClient.newBuilder().connectTimeout(timeout).build()

    override fun profile(model: ModelRef): ModelProfile {

        if (model.provider != provider) {
            throw OrionException(
                message = "model provider does not match adapter",
                code = AgentErrorCode.CONFIGURATION,
            )
        }

        return ModelProfile(
            streaming = CapabilitySupport.UNSUPPORTED,
            toolCalling = CapabilitySupport.NATIVE,
            structuredOutput = CapabilitySupport.NATIVE,
        )

    }

    override suspend fun complete(request: ModelRequest): ModelResponse {

        if (request.model.provider != provider) {
            throw OrionException(
                message = "model provider does not match adapter",
                code = AgentErrorCode.CONFIGURATION,
            )
        }

        val payload = buildPayload(request)
        val requestBuilder = HttpRequest.newBuilder(endpoint)
            .timeout(timeout)
            .header("content-type", "application/json")
        apiKey?.let { requestBuilder.header("authorization", "Bearer $it") }

        val response = try {
            client.sendAsync(
                requestBuilder.POST(HttpRequest.BodyPublishers.ofString(payload.toString())).build(),
                HttpResponse.BodyHandlers.ofString(),
            ).await()
        } catch (error: CancellationException) {
            throw error
        } catch (error: HttpTimeoutException) {
            throw OrionException(
                message = "model provider request timed out",
                code = AgentErrorCode.TIMEOUT,
                retryable = true,
                cause = error,
            )
        } catch (error: Exception) {
            throw OrionException(
                message = "model provider network request failed",
                code = AgentErrorCode.NETWORK,
                retryable = true,
                cause = error,
            )
        }

        if (response.statusCode() !in SUCCESSFUL_HTTP_STATUS) {
            throw response.toProviderException()
        }

        return try {
            parseResponse(Json.parseToJsonElement(response.body()).asObject("provider response"))
        } catch (error: Exception) {
            throw OrionException(
                message = "model provider returned a malformed response",
                code = AgentErrorCode.MALFORMED_RESPONSE,
                cause = error,
            )
        }

    }

    private fun buildPayload(request: ModelRequest): JsonObject {

        val providerOptions = request.settings.providerOptions[provider]

        rejectProtectedOverrides(providerOptions)

        return buildJsonObject {
            put("model", request.model.model)
            put("messages", buildMessages(request.messages))
            if (request.tools.isNotEmpty()) put("tools", buildTools(request.tools))
            request.settings.temperature?.let { put("temperature", it) }
            request.settings.maxOutputTokens?.let { put("max_tokens", it) }
            request.outputSchema?.let { schema ->
                putJsonObject("response_format") {
                    put("type", "json_schema")
                    putJsonObject("json_schema") {
                        put("name", "orion_output")
                        put("schema", schema)
                        put("strict", true)
                    }
                }
            }
            providerOptions?.forEach { (key, value) -> put(key, value) }
        }

    }

    private fun parseResponse(data: JsonObject): ModelResponse {

        val choice = data.requiredArray("choices").firstOrNull()?.asObject("choice")
            ?: throw OrionException("model provider returned no choices")
        val message = choice.requiredObject("message")
        val calls = message["tool_calls"]?.takeUnless { it is JsonNull }?.asArray("tool_calls")
            ?.map(::parseToolCall)
            .orEmpty()
        val usage = data["usage"]?.takeUnless { it is JsonNull }?.asObject("usage")

        return ModelResponse(
            content = message["content"]?.takeUnless { it is JsonNull }
                ?.let { (it as? JsonPrimitive)?.content }
                ?: "",
            toolCalls = calls,
            finishReason = if (calls.isEmpty()) {
                choice.requiredString("finish_reason").toFinishReason()
            } else {
                FinishReason.TOOL_CALLS
            },
            usage = Usage(
                inputTokens = usage?.get("prompt_tokens")?.jsonPrimitive?.longOrNull ?: 0,
                outputTokens = usage?.get("completion_tokens")?.jsonPrimitive?.longOrNull ?: 0,
            ),
        )

    }

    private fun parseToolCall(rawValue: JsonElement): ToolCall {

        val raw = rawValue.asObject("tool call")
        val function = raw.requiredObject("function")

        return ToolCall(
            id = raw.requiredString("id"),
            name = function.requiredString("name"),
            arguments = Json.parseToJsonElement(function.requiredString("arguments"))
                .asObject("tool arguments"),
        )

    }

    private fun rejectProtectedOverrides(providerOptions: JsonObject?) {

        val overridden = providerOptions?.keys?.intersect(PROTECTED_FIELDS).orEmpty()

        if (overridden.isNotEmpty()) {
            throw OrionException(
                message = "provider options cannot override protected fields: ${overridden.sorted().joinToString()}",
                code = AgentErrorCode.CONFIGURATION,
            )
        }

    }

    private companion object {

        private val SUCCESSFUL_HTTP_STATUS: IntRange = 200..299

        private val PROTECTED_FIELDS: Set<String> =
            setOf("model", "messages", "tools", "response_format")

    }

}

private fun buildMessages(messages: List<ModelMessage>): JsonArray = buildJsonArray {
    messages.forEach { message ->
        add(buildJsonObject {
            put("role", message.role.toWireName())
            put("content", message.content)
            message.toolCallId?.let { put("tool_call_id", it) }
            if (message.toolCalls.isNotEmpty()) {
                put("tool_calls", buildProviderToolCalls(message.toolCalls))
            }
        })
    }
}

private fun HttpResponse<String>.toProviderException(): OrionException {

    val code = when (statusCode()) {
        401, 403 -> AgentErrorCode.AUTHENTICATION
        429 -> AgentErrorCode.RATE_LIMITED
        else -> AgentErrorCode.PROVIDER
    }

    return OrionException(
        message = "model provider returned HTTP ${statusCode()}",
        code = code,
        retryable = statusCode() == 429 || statusCode() >= 500,
        retryAfterMilliseconds = parseRetryAfter(headers().firstValue("retry-after").orElse(null)),
    )

}

private fun parseRetryAfter(value: String?): Long? {

    if (value == null) return null

    value.toLongOrNull()?.takeIf { it >= 0 }?.let { seconds ->
        return seconds.takeIf { it <= Long.MAX_VALUE / 1_000L }?.times(1_000L)
    }

    return runCatching {
        val target = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
        Duration.between(Instant.now(), target).toMillis().coerceAtLeast(0)
    }.getOrNull()

}

private fun buildProviderToolCalls(toolCalls: List<ToolCall>): JsonArray = buildJsonArray {
    toolCalls.forEach { call ->
        add(buildJsonObject {
            put("id", call.id)
            put("type", "function")
            putJsonObject("function") {
                put("name", call.name)
                put("arguments", call.arguments.toString())
            }
        })
    }
}

private fun buildTools(tools: List<ToolDefinition>): JsonArray = buildJsonArray {
    tools.forEach { tool ->
        add(buildJsonObject {
            put("type", "function")
            putJsonObject("function") {
                put("name", tool.name)
                put("description", tool.description)
                put("parameters", tool.inputSchema)
            }
        })
    }
}

private fun MessageRole.toWireName(): String = name.lowercase()

private fun String.toFinishReason(): FinishReason = when (this) {
    "stop" -> FinishReason.STOP
    "tool_calls" -> FinishReason.TOOL_CALLS
    "length" -> FinishReason.LENGTH
    "content_filter" -> FinishReason.CONTENT_FILTER
    else -> FinishReason.OTHER
}
