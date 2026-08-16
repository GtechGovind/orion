package dev.orion.sdk

/** Strongly typed lifecycle payload carried by [AgentEvent]. */
public sealed interface AgentEventKind {

    /** A run session was created for [agentId]. */
    public data class RunStarted(
        /** Stable identifier of the configured agent. */
        public val agentId: String,
    ) : AgentEventKind

    /** A model request started for [provider] and [model]. */
    public data class ModelRequested(
        /** One-based model-turn number. */
        public val turn: Int,
        /** Registered provider key. */
        public val provider: String,
        /** Provider-specific model identifier. */
        public val model: String,
    ) : AgentEventKind

    /** A model request completed with [toolCallCount] proposed tool calls. */
    public data class ModelCompleted(
        /** One-based model-turn number. */
        public val turn: Int,
        /** Assistant text produced in this turn. */
        public val output: String,
        /** Number of tool calls proposed in provider order. */
        public val toolCallCount: Int,
    ) : AgentEventKind

    /** The model requested execution of an application tool. */
    public data class ToolRequested(
        /** Kernel-assigned action identifier. */
        public val actionId: String,
        /** Model/provider-assigned call identifier. */
        public val callId: String,
        /** Registered tool name. */
        public val name: String,
    ) : AgentEventKind

    /** An application tool completed successfully. */
    public data class ToolCompleted(
        /** Kernel-assigned action identifier. */
        public val actionId: String,
        /** Model/provider-assigned call identifier. */
        public val callId: String,
        /** Registered tool name. */
        public val name: String,
    ) : AgentEventKind

    /** The run completed successfully with [output]. */
    public data class RunCompleted(
        /** Final assistant text. */
        public val output: String,
    ) : AgentEventKind

    /** The run failed with a stable [code] and safe [message]. */
    public data class RunFailed(
        /** Stable protocol error code. */
        public val code: AgentErrorCode,
        /** Safe human-readable diagnostic. */
        public val message: String,
        /** Whether retrying may succeed without changing the request. */
        public val retryable: Boolean,
        /** Provider-requested retry delay, in milliseconds. */
        public val retryAfterMilliseconds: Long?,
    ) : AgentEventKind

    /** The caller cancelled the run. */
    public data object RunCancelled : AgentEventKind

}
