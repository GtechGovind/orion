package dev.orion.sdk.model

/** Identifies a model within a registered provider namespace. */
internal data class ModelRef(
    /** Stable provider key resolved by `ModelRegistry`. */
    public val provider: String,
    /** Provider-specific model identifier. */
    public val model: String,
) {

    init {
        require(provider.isNotBlank()) { "model provider must not be blank" }
        require(model.isNotBlank()) { "model identifier must not be blank" }
    }

    /** Factories for model references. */
    public companion object {

        /**
         * Parses `provider:model` notation.
         *
         * @throws IllegalArgumentException when either component is missing.
         */
        public fun parse(value: String): ModelRef {

            val separator = value.indexOf(':')

            require(separator > 0 && separator < value.lastIndex) {
                "model reference must use provider:model notation"
            }

            return ModelRef(value.substring(0, separator), value.substring(separator + 1))

        }

    }

}
