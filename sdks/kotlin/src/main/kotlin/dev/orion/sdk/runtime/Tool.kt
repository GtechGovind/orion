package dev.orion.sdk.runtime

import dev.orion.sdk.internal.schemaFor
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer

/** Model-visible tool definition paired with its suspending host implementation. */
internal data class HostTool(
    /** Stable tool name exposed to the model. */
    val name: String,
    /** Human-readable instructions for the model. */
    val description: String,
    /** JSON Schema used to validate dynamic tool arguments. */
    val inputSchema: JsonObject,
    /**
     * Executes the tool after validating its application-specific JSON fields.
     *
     * The handler inherits coroutine cancellation from the active run.
     */
    val execute: suspend (JsonObject) -> JsonElement,
) {

    init {
        require(name.isNotBlank()) { "tool name must not be blank" }
        require(description.isNotBlank()) { "tool description must not be blank" }
    }

    /** Factories for tools backed by typed Kotlin serialization contracts. */
    companion object {

        /**
         * Creates a tool whose schema, argument decoding, and result encoding are
         * derived from `@Serializable` Kotlin types.
         *
         * [Arguments] must serialize as a JSON object, normally a data class.
         * Invalid model arguments fail before [execute] is invoked. Applications
         * can retain the primary constructor when a schema is dynamic or supplied
         * by another system.
         *
         * @param name stable tool name exposed to the model.
         * @param description human-readable model guidance.
         * @param json serialization configuration used at execution time.
         * @param execute typed suspending application handler.
         * @return a host tool ready for the internal agent runtime.
         * @throws IllegalArgumentException when [Arguments] is not object-shaped.
         * @throws kotlinx.serialization.SerializationException when arguments or
         * results do not satisfy their serializers.
         */
        inline fun <reified Arguments, reified Result> typed(
            name: String,
            description: String,
            json: Json = Json.Default,
            noinline execute: suspend (Arguments) -> Result,
        ): HostTool = typed(
            name = name,
            description = description,
            argumentSerializer = serializer<Arguments>(),
            resultSerializer = serializer<Result>(),
            json = json,
            execute = execute,
        )

        /**
         * Creates a typed tool from explicit serializers.
         *
         * This overload supports serializer instances configured or provided at
         * runtime while preserving the same derived-schema behavior.
         *
         * @param name stable tool name exposed to the model.
         * @param description human-readable model guidance.
         * @param argumentSerializer serializer defining the object-shaped input.
         * @param resultSerializer serializer defining the JSON-compatible result.
         * @param json serialization configuration used at execution time.
         * @param execute typed suspending application handler.
         * @return a host tool ready for the internal agent runtime.
         * @throws IllegalArgumentException when the argument serializer is not
         * object-shaped.
         */
        fun <Arguments, Result> typed(
            name: String,
            description: String,
            argumentSerializer: KSerializer<Arguments>,
            resultSerializer: KSerializer<Result>,
            json: Json = Json.Default,
            execute: suspend (Arguments) -> Result,
        ): HostTool {

            val inputSchema = schemaFor(argumentSerializer.descriptor)

            return HostTool(
                name = name,
                description = description,
                inputSchema = inputSchema,
                execute = { rawArguments ->

                    val arguments = json.decodeFromJsonElement(argumentSerializer, rawArguments)
                    val result = execute(arguments)

                    json.encodeToJsonElement(resultSerializer, result)

                }

            )

        }

    }

}
