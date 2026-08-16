package dev.orion.sdk

/**
 * Value emitted by [Agent.stream].
 *
 * @param Output terminal structured-output type configured on the agent.
 */
public sealed interface AgentStreamItem<out Output>
