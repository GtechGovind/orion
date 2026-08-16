package dev.orion.sdk.model

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Complete provider-neutral request delivered to a [ModelAdapter]. */
internal data class ModelRequest(
    /** Selected provider and model. */
    public val model: ModelRef,
    /** Canonical transcript in execution order. */
    public val messages: List<ModelMessage>,
    /** Tools visible to the model for this request. */
    public val tools: List<ToolDefinition>,
    /** Optional dynamic JSON Schema constraining the output. */
    public val outputSchema: JsonElement? = null,
    /** Portable and provider-specific model controls. */
    public val settings: ModelSettings = ModelSettings(),
    /** Opaque continuation state owned by the selected adapter. */
    public val providerState: JsonObject = JsonObject(emptyMap()),
) {

    init {
        require(messages.isNotEmpty()) { "model request transcript must not be empty" }
        require(tools.map(ToolDefinition::name).toSet().size == tools.size) {
            "model request tool names must be unique"
        }
    }

}
