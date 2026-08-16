package dev.orion.sdk

import dev.orion.sdk.internal.schemaFor
import dev.orion.sdk.model.ModelRegistry
import dev.orion.sdk.runtime.AgentDefinition
import dev.orion.sdk.runtime.RunEvent
import dev.orion.sdk.runtime.RunResult
import dev.orion.sdk.runtime.Runner
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Low-ceremony typed agent backed by the Rust runtime.
 *
 * @param model provider model used by every run.
 * @param tools typed application functions available to the model.
 * @param output serializer defining and decoding terminal structured output.
 * @param instructions system-level behavior instructions.
 * @param id stable application-defined identifier.
 * @param name human-readable display name.
 * @param maxTurns positive model-turn limit.
 */
public class Agent<Output>(
    model: Model,
    tools: List<Tool> = emptyList(),
    private val output: KSerializer<Output>,
    instructions: String = "You are a helpful assistant.",
    id: String = "assistant",
    name: String = "Assistant",
    maxTurns: Int = 8,
) {

    private val json: Json = Json.Default
    private val definition: AgentDefinition = AgentDefinition(
        id = id,
        name = name,
        instructions = instructions,
        model = model.ref,
        tools = tools.map(Tool::definition),
        outputSchema = schemaFor(output.descriptor),
        maxTurns = maxTurns,
    )
    private val runner: Runner = Runner(ModelRegistry(listOf(model.adapter)))

    /**
     * Executes this agent to completion and returns typed structured output.
     *
     * @throws OrionException with stable error and retry metadata when execution fails.
     * @throws kotlinx.coroutines.CancellationException when the calling coroutine is cancelled.
     */
    public suspend fun run(
        input: String,
        runId: String = "run-${UUID.randomUUID()}",
    ): AgentResult<Output> = runner.run(definition, input, runId).toAgentResult()

    /**
     * Returns a cold flow of lifecycle events followed by one typed result.
     *
     * Failed native runs emit their terminal [AgentEventKind.RunFailed] event
     * before throwing the matching [OrionException]. Cancelling collection
     * cancels the native run and preserves coroutine cancellation.
     *
     * @throws OrionException with the terminal event's stable failure metadata.
     */
    public fun stream(
        input: String,
        runId: String = "run-${UUID.randomUUID()}",
    ): Flow<AgentStreamItem<Output>> = flow {

        runner.runStream(definition, input, runId).collect { item ->
            when (item) {
                is RunEvent -> emit(item.toAgentEvent())
                is RunResult -> emit(item.toAgentResult())
            }
        }

    }

    private fun RunResult.toAgentResult(): AgentResult<Output> = AgentResult(
        output = json.decodeFromString(this@Agent.output, output),
        runId = runId,
        usage = TokenUsage(
            inputTokens = usage.inputTokens,
            outputTokens = usage.outputTokens,
        ),
        turns = turns,
        events = events.map { it.toAgentEvent() },
    )

    private fun RunEvent.toAgentEvent(): AgentEvent = AgentEvent(
        runId = runId,
        sequence = sequence,
        kind = kind,
    )

}
