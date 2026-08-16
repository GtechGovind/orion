package dev.orion.sdk.model

/**
 * Connects the Orion runtime to one model-provider namespace.
 *
 * Implementations own provider authentication, request translation, transport,
 * and response normalization. They must not make kernel transition decisions.
 */
internal interface ModelAdapter {

    /** Stable provider key used by [ModelRef.provider] and [ModelRegistry]. */
    public val provider: String

    /** Returns the capabilities available for [model] without executing it. */
    public fun profile(model: ModelRef): ModelProfile

    /**
     * Executes one fully validated, provider-neutral model [request].
     *
     * @throws dev.orion.sdk.OrionException when the provider request fails or
     * returns a response that cannot be normalized safely.
     */
    public suspend fun complete(request: ModelRequest): ModelResponse

    /** Releases provider resources. The default implementation owns none. */
    public suspend fun close(): Unit = Unit

}
