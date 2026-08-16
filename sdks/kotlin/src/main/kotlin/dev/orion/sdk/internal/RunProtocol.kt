package dev.orion.sdk.internal

import dev.orion.sdk.AgentErrorCode
import dev.orion.sdk.AgentEventKind
import dev.orion.sdk.OrionException
import dev.orion.sdk.model.Usage
import dev.orion.sdk.runtime.RunEvent
import dev.orion.sdk.runtime.RunResult
import kotlinx.serialization.json.JsonObject

internal fun JsonObject.toRunEvents(): List<RunEvent> = requiredArray("events").map { rawValue ->
    val raw = rawValue.asObject("event")
    RunEvent(
        runId = raw.requiredString("run_id"),
        sequence = raw.requiredLong("sequence"),
        kind = raw.requiredObject("kind").toAgentEventKind(),
    )
}

internal fun JsonObject.toRunResult(events: List<RunEvent>): RunResult? {

    val value = nullableElement("result")?.asObject("result") ?: return null
    val usage = value.requiredObject("usage")

    return RunResult(
        runId = value.requiredString("run_id"),
        output = value.requiredString("output"),
        usage = Usage(
            inputTokens = usage.requiredLong("input_tokens"),
            outputTokens = usage.requiredLong("output_tokens"),
        ),
        turns = value.requiredInt("turns"),
        events = events.toList(),
    )

}

private fun JsonObject.toAgentEventKind(): AgentEventKind = when (val type = requiredString("type")) {
    "run_started" -> AgentEventKind.RunStarted(requiredString("agent_id"))
    "model_requested" -> AgentEventKind.ModelRequested(
        turn = requiredInt("turn"),
        provider = requiredString("provider"),
        model = requiredString("model"),
    )
    "model_completed" -> AgentEventKind.ModelCompleted(
        turn = requiredInt("turn"),
        output = requiredString("output"),
        toolCallCount = requiredInt("tool_call_count"),
    )
    "tool_requested" -> AgentEventKind.ToolRequested(
        actionId = requiredString("action_id"),
        callId = requiredString("call_id"),
        name = requiredString("name"),
    )
    "tool_completed" -> AgentEventKind.ToolCompleted(
        actionId = requiredString("action_id"),
        callId = requiredString("call_id"),
        name = requiredString("name"),
    )
    "run_completed" -> AgentEventKind.RunCompleted(requiredString("output"))
    "run_failed" -> requiredObject("error").let { error ->
        AgentEventKind.RunFailed(
            code = error.requiredString("code").toAgentErrorCode(),
            message = error.requiredString("message"),
            retryable = error.requiredBoolean("retryable"),
            retryAfterMilliseconds = error.nullableLong("retry_after_ms"),
        )
    }
    "run_cancelled" -> AgentEventKind.RunCancelled
    else -> throw OrionException("protocol event type '$type' is not supported")
}

private fun String.toAgentErrorCode(): AgentErrorCode = when (this) {
    "invalid_command" -> AgentErrorCode.INVALID_COMMAND
    "invalid_state" -> AgentErrorCode.INVALID_STATE
    "configuration" -> AgentErrorCode.CONFIGURATION
    "authentication" -> AgentErrorCode.AUTHENTICATION
    "rate_limited" -> AgentErrorCode.RATE_LIMITED
    "timeout" -> AgentErrorCode.TIMEOUT
    "network" -> AgentErrorCode.NETWORK
    "unsupported_capability" -> AgentErrorCode.UNSUPPORTED_CAPABILITY
    "content_safety" -> AgentErrorCode.CONTENT_SAFETY
    "malformed_response" -> AgentErrorCode.MALFORMED_RESPONSE
    "provider" -> AgentErrorCode.PROVIDER
    "tool" -> AgentErrorCode.TOOL
    "cancelled" -> AgentErrorCode.CANCELLED
    "turn_limit_exceeded" -> AgentErrorCode.TURN_LIMIT_EXCEEDED
    else -> throw OrionException("protocol error code '$this' is not supported")
}
