package dev.orion.sdk.model

/** Provider-neutral token accounting. */
internal data class Usage(
    /** Number of input tokens consumed. */
    public val inputTokens: Long = 0,
    /** Number of output tokens generated. */
    public val outputTokens: Long = 0,
) {

    init {
        require(inputTokens >= 0) { "input token count must not be negative" }
        require(outputTokens >= 0) { "output token count must not be negative" }
    }

}
