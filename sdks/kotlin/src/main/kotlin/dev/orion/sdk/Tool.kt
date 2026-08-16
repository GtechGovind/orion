package dev.orion.sdk

import dev.orion.sdk.runtime.HostTool
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/** Typed application tool created only through [tool]. */
public class Tool @PublishedApi internal constructor(internal val definition: HostTool)

/**
 * Converts a typed function reference into an Orion suspending tool.
 *
 * Kotlin serializers derive the argument schema and validate both arguments
 * and results. The explicit name is the stable model-visible identifier and
 * does not change when the Kotlin function is renamed.
 *
 * @param name stable model-visible tool identifier.
 * @param function typed application handler invoked after Rust validation.
 * @param description concise model-visible capability description.
 * @return typed tool accepted directly by [Agent].
 */
public inline fun <reified Arguments, reified Result> tool(
    name: String,
    description: String,
    noinline function: suspend (Arguments) -> Result,
): Tool {

    return Tool(
        createHostTool(
            name = name,
            description = description,
            argumentSerializer = serializer<Arguments>(),
            resultSerializer = serializer<Result>(),
            function = function,
        ),
    )

}

@PublishedApi
internal fun <Arguments, Result> createHostTool(
    name: String,
    description: String,
    argumentSerializer: KSerializer<Arguments>,
    resultSerializer: KSerializer<Result>,
    function: suspend (Arguments) -> Result,
): HostTool = HostTool.typed(
    name = name,
    description = description,
    argumentSerializer = argumentSerializer,
    resultSerializer = resultSerializer,
    json = Json.Default,
    execute = function,
)
