package dev.orion.sdk.model

import kotlinx.serialization.json.JsonObject

/** Model-visible tool metadata without an executable host callback. */
internal data class ToolDefinition(
    /** Stable tool name. */
    public val name: String,
    /** Human-readable instructions for the model. */
    public val description: String,
    /** JSON Schema used to validate tool arguments. */
    public val inputSchema: JsonObject,
) {

    init {
        require(name.isNotBlank()) { "tool name must not be blank" }
        require(description.isNotBlank()) { "tool description must not be blank" }
    }

}
