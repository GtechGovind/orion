package dev.orion.sdk

/**
 * Successful typed output with usage and lifecycle metadata.
 *
 * @param Output terminal structured-output type configured on the agent.
 */
public data class AgentResult<Output>(
    /** Terminal value decoded through the configured serializer. */
    public val output: Output,
    /** Identifier of the completed run. */
    public val runId: String,
    /** Aggregate provider-neutral token counts. */
    public val usage: TokenUsage,
    /** Number of completed model turns. */
    public val turns: Int,
    /** Complete ordered lifecycle trace. */
    public val events: List<AgentEvent>,
) : AgentStreamItem<Output>
