package dev.orion.sdk.internal

import dev.orion.sdk.OrionException

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

internal object NativeKernel {

    init {
        NativeLibraryLoader.load()
    }

    @JvmStatic
    external fun create(command: Map<String, Any?>): Long

    @JvmStatic
    external fun takeStep(handle: Long): Map<String, Any?>

    @JvmStatic
    external fun resume(handle: Long, result: Map<String, Any?>): Map<String, Any?>

    @JvmStatic
    external fun cancel(handle: Long): Map<String, Any?>

    @JvmStatic
    external fun fail(handle: Long, error: Map<String, Any?>): Map<String, Any?>

    @JvmStatic
    external fun close(handle: Long)

}

internal class NativeRun(command: JsonObject) : AutoCloseable {

    private var handle: Long = NativeKernel.create(command.toNativeMap())

    internal fun takeStep(): JsonObject = NativeKernel.takeStep(openHandle()).toJson().asObject("native step")

    internal fun resume(result: JsonObject): JsonObject = NativeKernel.resume(openHandle(), result.toNativeMap()).toJson().asObject("native step")

    internal fun cancel() { NativeKernel.cancel(openHandle()) }

    internal fun fail(error: JsonObject): JsonObject =
        NativeKernel.fail(openHandle(), error.toNativeMap()).toJson().asObject("native step")

    private fun openHandle(): Long = handle.takeIf { it != CLOSED_HANDLE }
        ?: throw OrionException("native run is already closed")

    override fun close() {

        if (handle != CLOSED_HANDLE) {
            NativeKernel.close(handle)
            handle = CLOSED_HANDLE
        }

    }

    private companion object {
        private const val CLOSED_HANDLE: Long = 0
    }
}

private fun JsonElement.toNative(): Any? = when (this) {
    JsonNull -> null
    is JsonObject -> mapValues { (_, value) -> value.toNative() }
    is JsonArray -> map(JsonElement::toNative)
    is JsonPrimitive -> booleanOrNull ?: longOrNull ?: doubleOrNull ?: content
}

private fun JsonObject.toNativeMap(): Map<String, Any?> =
    mapValues { (_, value) -> value.toNative() }

private fun Any?.toJson(): JsonElement = when (this) {
    null -> JsonNull
    is Boolean -> JsonPrimitive(this)
    is Byte -> JsonPrimitive(toLong())
    is Short -> JsonPrimitive(toLong())
    is Int -> JsonPrimitive(toLong())
    is Long -> JsonPrimitive(this)
    is Float -> JsonPrimitive(toDouble())
    is Double -> JsonPrimitive(this)
    is String -> JsonPrimitive(this)
    is List<*> -> JsonArray(map { it.toJson() })
    is Map<*, *> -> JsonObject(entries.associate { (key, value) ->
        val stringKey = key as? String
            ?: throw OrionException("native map key is not a string")
        stringKey to value.toJson()
    })
    else -> throw OrionException("native kernel returned an unsupported value")
}
