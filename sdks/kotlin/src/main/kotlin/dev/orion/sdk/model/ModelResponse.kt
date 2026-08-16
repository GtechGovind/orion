package dev.orion.sdk.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/** Normalized result returned by a [ModelAdapter]. */
internal data class ModelResponse(
    /** Assistant text, which can be empty when tools are requested. */
    public val content: String = "",
    /** Tool calls in provider order. */
    public val toolCalls: List<ToolCall> = emptyList(),
    /** Provider-neutral reason generation stopped. */
    public val finishReason: FinishReason = if (toolCalls.isEmpty()) FinishReason.STOP else FinishReason.TOOL_CALLS,
    /** Token accounting reported by the provider. */
    public val usage: Usage = Usage(),
    /** Opaque continuation state owned by the adapter. */
    public val providerState: JsonObject = buildJsonObject {},
)
