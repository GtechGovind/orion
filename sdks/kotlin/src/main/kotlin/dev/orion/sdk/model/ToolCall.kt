package dev.orion.sdk.model

import kotlinx.serialization.json.JsonObject

/** A model-requested invocation of an application tool. */
internal data class ToolCall(
    /** Provider/model supplied call identifier. */
    public val id: String,
    /** Registered tool name. */
    public val name: String,
    /** Object-shaped JSON arguments whose domain fields are validated by the tool. */
    public val arguments: JsonObject,
) {

    init {
        require(id.isNotBlank()) { "tool call identifier must not be blank" }
        require(name.isNotBlank()) { "tool call name must not be blank" }
    }

}
