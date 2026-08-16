package dev.orion.sdk.model

import dev.orion.sdk.AgentErrorCode
import dev.orion.sdk.OrionException

/**
 * Resolves model references to explicitly registered provider adapters.
 *
 * @param adapters adapters with unique, stable provider keys.
 */
internal class ModelRegistry(adapters: List<ModelAdapter>) {

    private val adaptersByProvider: Map<String, ModelAdapter> =
        adapters.associateBy(ModelAdapter::provider).also {
            require(it.size == adapters.size) { "model adapter providers must be unique" }
        }

    /** Resolves [model] or fails when its provider is not registered. */
    fun resolve(model: ModelRef): ModelAdapter = adaptersByProvider[model.provider]
        ?: throw OrionException(
            message = "no model adapter registered for provider ${model.provider}",
            code = AgentErrorCode.CONFIGURATION,
        )

    /** Closes every adapter, preserving additional failures as suppressed causes. */
    suspend fun close() {

        var firstFailure: Exception? = null

        adaptersByProvider.values.forEach { adapter ->
            try {
                adapter.close()
            } catch (error: Exception) {
                firstFailure?.addSuppressed(error) ?: run { firstFailure = error }
            }
        }

        firstFailure?.let { throw it }

    }

}
