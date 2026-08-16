package dev.orion.sdk

/**
 * Normalized, machine-readable SDK failure shared with the Rust protocol.
 *
 * @param message safe diagnostic suitable for application logs.
 * @param code stable failure category used for exhaustive recovery policy.
 * @param retryable whether retry policy may repeat the operation unchanged.
 * @param retryAfterMilliseconds provider-suggested retry delay in milliseconds.
 * @param cause original provider, tool, protocol, or native failure.
 */
public class OrionException @JvmOverloads constructor(
    message: String,
    public val code: AgentErrorCode = AgentErrorCode.INVALID_STATE,
    public val retryable: Boolean = false,
    public val retryAfterMilliseconds: Long? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {

    init {
        require(retryAfterMilliseconds == null || retryAfterMilliseconds >= 0) {
            "retry delay must not be negative"
        }
    }

}
