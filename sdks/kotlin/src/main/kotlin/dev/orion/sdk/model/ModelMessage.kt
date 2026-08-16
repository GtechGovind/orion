package dev.orion.sdk.model

/** One immutable entry in a provider-neutral model transcript. */
internal data class ModelMessage(
    /** Author role. */
    public val role: MessageRole,
    /** Text content. */
    public val content: String,
    /** Matching call identifier for tool-result messages. */
    public val toolCallId: String? = null,
    /** Calls proposed by an assistant message. */
    public val toolCalls: List<ToolCall> = emptyList(),
) {

    init {
        require(role == MessageRole.TOOL || toolCallId == null) {
            "only tool-result messages may reference a tool call identifier"
        }
        require(role == MessageRole.ASSISTANT || toolCalls.isEmpty()) {
            "only assistant messages may propose tool calls"
        }
    }

}
