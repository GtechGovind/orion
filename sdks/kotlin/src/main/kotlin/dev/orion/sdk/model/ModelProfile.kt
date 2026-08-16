package dev.orion.sdk.model

/**
 * Capabilities reported by a model adapter before a run starts.
 *
 * Unknown capabilities permit execution; unsupported capabilities fail
 * preflight when the agent explicitly requires them.
 */
internal data class ModelProfile(
    /** Whether token streaming is supported. */
    public val streaming: CapabilitySupport = CapabilitySupport.UNKNOWN,
    /** Whether the model can request tools. */
    public val toolCalling: CapabilitySupport = CapabilitySupport.UNKNOWN,
    /** Whether the model can produce schema-constrained output. */
    public val structuredOutput: CapabilitySupport = CapabilitySupport.UNKNOWN,
    /** Whether the model can request multiple tools in one turn. */
    public val parallelToolCalls: CapabilitySupport = CapabilitySupport.UNKNOWN,
    /** Maximum context size reported by the provider, in tokens. */
    public val maxContextTokens: Long? = null,
) {

    init {
        require(maxContextTokens == null || maxContextTokens > 0) {
            "maximum context tokens must be positive"
        }
    }

}
