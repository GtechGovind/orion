package dev.orion.sdk.model

/** Describes how a model capability is implemented. */
internal enum class CapabilitySupport {

    /** The provider implements the capability directly. */
    NATIVE,

    /** The adapter safely emulates the capability. */
    EMULATED,

    /** The model cannot provide the capability. */
    UNSUPPORTED,

    /** The adapter cannot determine support before execution. */
    UNKNOWN,

}
