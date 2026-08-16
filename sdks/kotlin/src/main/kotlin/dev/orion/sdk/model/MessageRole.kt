package dev.orion.sdk.model

/** Role of a message in the provider-neutral transcript. */
internal enum class MessageRole {

    /** System-authored instructions. */
    SYSTEM,

    /** Application-user input. */
    USER,

    /** Model-authored output. */
    ASSISTANT,

    /** Application tool output. */
    TOOL,

}
