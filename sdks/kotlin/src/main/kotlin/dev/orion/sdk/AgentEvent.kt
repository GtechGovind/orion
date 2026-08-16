package dev.orion.sdk

/** Ordered lifecycle observation emitted while an agent is running. */
public data class AgentEvent(
    /** Identifier of the observed run. */
    public val runId: String,
    /** Zero-based monotonic sequence number. */
    public val sequence: Long,
    /** Strongly typed lifecycle payload. */
    public val kind: AgentEventKind,
) : AgentStreamItem<Nothing>
