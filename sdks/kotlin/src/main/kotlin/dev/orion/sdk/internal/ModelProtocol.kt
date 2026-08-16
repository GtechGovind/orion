package dev.orion.sdk.internal

import dev.orion.sdk.OrionException
import dev.orion.sdk.model.FinishReason
import dev.orion.sdk.model.MessageRole
import dev.orion.sdk.model.ModelMessage
import dev.orion.sdk.model.ModelRef
import dev.orion.sdk.model.ModelRequest
import dev.orion.sdk.model.ModelResponse
import dev.orion.sdk.model.ModelSettings
import dev.orion.sdk.model.ToolCall
import dev.orion.sdk.model.ToolDefinition
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

internal fun JsonObject.toModelRequest(): ModelRequest {

    val model = requiredObject("model")
    val settings = requiredObject("settings")

    return ModelRequest(
        model = ModelRef(model.requiredString("provider"), model.requiredString("model")),
        messages = requiredArray("messages").map(JsonElement::toModelMessage),
        tools = requiredArray("tools").map(JsonElement::toToolDefinition),
        outputSchema = nullableElement("output_schema"),
        settings = ModelSettings(
            temperature = settings.nullableDouble("temperature"),
            maxOutputTokens = settings.nullableInt("max_output_tokens"),
            providerOptions = settings.requiredObject("provider_options").mapValues { (provider, value) ->
                value.asObject("provider options for '$provider'")
            },
        ),
        providerState = nullableElement("provider_state")?.asObject("provider_state")
            ?: JsonObject(emptyMap()),
    )

}

internal fun ModelResponse.toEffectResult(): JsonObject = buildJsonObject {
    put("type", "model")
    putJsonObject("value") {
        put("content", content)
        putJsonArray("tool_calls") {
            toolCalls.forEach { call ->
                add(buildJsonObject {
                    put("id", call.id)
                    put("name", call.name)
                    put("arguments", call.arguments)
                })
            }
        }
        put("finish_reason", finishReason.toWireName())
        putJsonObject("usage") {
            put("input_tokens", usage.inputTokens)
            put("output_tokens", usage.outputTokens)
        }
        put("provider_state", providerState)
    }
}

private fun JsonElement.toModelMessage(): ModelMessage {

    val message = asObject("message")

    return ModelMessage(
        role = message.requiredString("role").toMessageRole(),
        content = message.requiredString("content"),
        toolCallId = message.nullableString("tool_call_id"),
        toolCalls = message.nullableArray("tool_calls")?.map(JsonElement::toToolCall).orEmpty(),
    )

}

private fun JsonElement.toToolCall(): ToolCall {

    val call = asObject("tool call")

    return ToolCall(
        id = call.requiredString("id"),
        name = call.requiredString("name"),
        arguments = call.requiredObject("arguments"),
    )

}

private fun JsonElement.toToolDefinition(): ToolDefinition {

    val tool = asObject("tool definition")

    return ToolDefinition(
        name = tool.requiredString("name"),
        description = tool.requiredString("description"),
        inputSchema = tool.requiredObject("input_schema"),
    )

}

private fun String.toMessageRole(): MessageRole = when (this) {
    "system" -> MessageRole.SYSTEM
    "user" -> MessageRole.USER
    "assistant" -> MessageRole.ASSISTANT
    "tool" -> MessageRole.TOOL
    else -> throw OrionException("protocol message role '$this' is not supported")
}

private fun FinishReason.toWireName(): String = name.lowercase()
