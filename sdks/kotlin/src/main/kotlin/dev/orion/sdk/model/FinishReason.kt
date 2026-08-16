package dev.orion.sdk.model

/** Provider-neutral reason that model generation stopped. */
internal enum class FinishReason {

    /** The model completed normally. */
    STOP,

    /** The model requested one or more tools. */
    TOOL_CALLS,

    /** The configured output limit was reached. */
    LENGTH,

    /** Provider safety policy stopped generation. */
    CONTENT_FILTER,

    /** The provider returned another recognized terminal condition. */
    OTHER,

}
