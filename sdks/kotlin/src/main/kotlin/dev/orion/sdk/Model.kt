package dev.orion.sdk

import dev.orion.sdk.model.ModelAdapter
import dev.orion.sdk.model.ModelRef

/**
 * Provider model used directly by the application-facing [Agent].
 *
 * Applications choose a concrete model such as [OpenAI]; registry and adapter
 * details remain internal.
 */
public abstract class Model internal constructor(
    internal val ref: ModelRef,
    internal val adapter: ModelAdapter,
)

private class ConfiguredModel(ref: ModelRef, adapter: ModelAdapter) : Model(ref, adapter)

internal fun configuredModel(ref: ModelRef, adapter: ModelAdapter): Model =
    ConfiguredModel(ref, adapter)
