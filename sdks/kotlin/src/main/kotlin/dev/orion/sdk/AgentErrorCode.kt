package dev.orion.sdk

/** Stable machine-readable category for a failed agent run. */
public enum class AgentErrorCode {

    /** The start command failed validation. */
    INVALID_COMMAND,

    /** A transition was attempted from an invalid state. */
    INVALID_STATE,

    /** Agent or model configuration is invalid. */
    CONFIGURATION,

    /** Provider authentication failed. */
    AUTHENTICATION,

    /** The provider rejected the request because of rate limits. */
    RATE_LIMITED,

    /** An operation exceeded its deadline. */
    TIMEOUT,

    /** Provider transport failed. */
    NETWORK,

    /** The selected model lacks a required capability. */
    UNSUPPORTED_CAPABILITY,

    /** Provider safety policy rejected the content. */
    CONTENT_SAFETY,

    /** Provider output could not be normalized safely. */
    MALFORMED_RESPONSE,

    /** A model-provider operation failed. */
    PROVIDER,

    /** An application tool failed. */
    TOOL,

    /** The caller cancelled the run. */
    CANCELLED,

    /** The configured model-turn limit was reached. */
    TURN_LIMIT_EXCEEDED,

}
