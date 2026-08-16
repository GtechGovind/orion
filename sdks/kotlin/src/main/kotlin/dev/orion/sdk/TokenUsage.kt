package dev.orion.sdk

/** Provider-neutral token counts accumulated by the Rust runtime. */
public data class TokenUsage(
    /** Tokens consumed by provider input. */
    public val inputTokens: Long,
    /** Tokens produced by provider output. */
    public val outputTokens: Long,
)
