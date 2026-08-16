package dev.orion.sdk.model

import kotlinx.serialization.json.JsonObject

/** Portable model controls plus explicitly namespaced provider extensions. */
internal data class ModelSettings(
    /** Sampling temperature, when supported by the selected model. */
    public val temperature: Double? = null,
    /** Maximum number of output tokens requested. */
    public val maxOutputTokens: Int? = null,
    /** Dynamic options keyed by provider, excluding credentials. */
    public val providerOptions: Map<String, JsonObject> = emptyMap(),
) {

    init {
        require(temperature == null || temperature.isFinite()) { "temperature must be finite" }
        require(maxOutputTokens == null || maxOutputTokens > 0) { "maximum output tokens must be positive" }
        require(providerOptions.keys.none(String::isBlank)) { "provider option keys must not be blank" }
    }

}
