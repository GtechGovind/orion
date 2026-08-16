package dev.orion.sdk.runtime

import dev.orion.sdk.AgentEventKind

/** Ordered immutable observation emitted during a run. */
internal data class RunEvent(
    /** Identifier of the observed run. */
    public val runId: String,
    /** Zero-based monotonic run-local sequence number. */
    public val sequence: Long,
    /** Strongly typed event payload. */
    public val kind: AgentEventKind,
) : RunItem {

    init {
        require(runId.isNotBlank()) { "run identifier must not be blank" }
        require(sequence >= 0) { "run event sequence must not be negative" }
    }

}
