package dev.orion.sdk.runtime

import dev.orion.sdk.model.ModelRef
import dev.orion.sdk.model.ModelSettings
import kotlinx.serialization.json.JsonObject

/** Internal immutable configuration used to start a native Orion run. */
internal data class AgentDefinition(
    /** Stable application-defined agent identifier. */
    val id: String,
    /** Human-readable agent name. */
    val name: String,
    /** System-level behavior instructions. */
    val instructions: String,
    /** Model selected for this agent. */
    val model: ModelRef,
    /** Application tools available during a run. */
    val tools: List<HostTool> = emptyList(),
    /** Optional JSON Schema constraining the terminal output. */
    val outputSchema: JsonObject,
    /** Portable and provider-specific model controls. */
    val modelSettings: ModelSettings = ModelSettings(),
    /** Maximum model turns permitted for one run. */
    val maxTurns: Int = 8,
) {

    init {
        require(id.isNotBlank()) { "agent identifier must not be blank" }
        require(name.isNotBlank()) { "agent name must not be blank" }
        require(instructions.isNotBlank()) { "agent instructions must not be blank" }
        require(maxTurns > 0) { "maximum turns must be positive" }
        require(tools.map(HostTool::name).toSet().size == tools.size) { "tool names must be unique" }
    }

}
