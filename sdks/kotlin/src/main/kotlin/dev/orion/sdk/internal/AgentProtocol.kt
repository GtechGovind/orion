package dev.orion.sdk.internal

import dev.orion.sdk.runtime.AgentDefinition
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

internal fun startCommand(
    agent: AgentDefinition,
    input: String,
    runId: String,
): JsonObject = buildJsonObject {
    put("run_id", runId)
    put("agent", agent.toProtocolJson())
    put("input", input)
}

private fun AgentDefinition.toProtocolJson(): JsonObject = buildJsonObject {
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
    put("output_schema", outputSchema)
    putJsonObject("model_settings") {
        put("temperature", modelSettings.temperature?.let(::JsonPrimitive) ?: JsonNull)
        put("max_output_tokens", modelSettings.maxOutputTokens?.let(::JsonPrimitive) ?: JsonNull)
        putJsonObject("provider_options") {
            modelSettings.providerOptions.forEach(::put)
        }
    }
    put("max_turns", maxTurns)
}
