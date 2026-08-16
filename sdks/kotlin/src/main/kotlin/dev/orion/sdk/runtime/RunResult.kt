package dev.orion.sdk.runtime

import dev.orion.sdk.model.Usage

/** Terminal successful value returned by a run. */
internal data class RunResult(
    /** Identifier of the completed run. */
    public val runId: String,
    /** Final assistant text. */
    public val output: String,
    /** Aggregate normalized token usage. */
    public val usage: Usage,
    /** Number of completed model turns. */
    public val turns: Int,
    /** Immutable event trace in sequence order. */
    public val events: List<RunEvent>,
) : RunItem
