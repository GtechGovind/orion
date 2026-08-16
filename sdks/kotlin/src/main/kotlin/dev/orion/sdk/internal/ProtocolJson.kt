package dev.orion.sdk.internal

import dev.orion.sdk.OrionException

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

internal fun JsonObject.requiredElement(field: String): JsonElement = this[field]
    ?: throw OrionException("protocol field '$field' is missing")

internal fun JsonObject.requiredObject(field: String): JsonObject =
    requiredElement(field).asObject(field)

internal fun JsonObject.requiredArray(field: String): JsonArray =
    requiredElement(field).asArray(field)

internal fun JsonObject.requiredString(field: String): String {

    val value = requiredElement(field)

    return (value as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.contentOrNull
        ?: throw OrionException("protocol field '$field' must be a string")

}

internal fun JsonObject.requiredLong(field: String): Long =
    (requiredElement(field) as? JsonPrimitive)?.longOrNull
        ?: throw OrionException("protocol field '$field' must be an integer")

internal fun JsonObject.requiredInt(field: String): Int =
    (requiredElement(field) as? JsonPrimitive)?.intOrNull
        ?: throw OrionException("protocol field '$field' must be a 32-bit integer")

internal fun JsonElement.asObject(context: String): JsonObject = this as? JsonObject
    ?: throw OrionException("protocol value '$context' must be an object")

internal fun JsonElement.asArray(context: String): JsonArray = this as? JsonArray
    ?: throw OrionException("protocol value '$context' must be an array")

internal fun JsonObject.nullableElement(field: String): JsonElement? =
    this[field]?.takeUnless { it is JsonNull }

internal fun JsonObject.nullableArray(field: String): JsonArray? = nullableElement(field)?.asArray(field)

internal fun JsonObject.nullableString(field: String): String? = nullableElement(field)?.let { value ->
    (value as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content
        ?: throw OrionException("protocol field '$field' must be a string or null")
}

internal fun JsonObject.nullableDouble(field: String): Double? = nullableElement(field)?.let { value ->
    (value as? JsonPrimitive)?.doubleOrNull
        ?: throw OrionException("protocol field '$field' must be a number or null")
}

internal fun JsonObject.nullableInt(field: String): Int? = nullableElement(field)?.let { value ->
    (value as? JsonPrimitive)?.content?.toIntOrNull()
        ?: throw OrionException("protocol field '$field' must be a 32-bit integer or null")
}

internal fun JsonObject.nullableLong(field: String): Long? = nullableElement(field)?.let { value ->
    (value as? JsonPrimitive)?.content?.toLongOrNull()
        ?: throw OrionException("protocol field '$field' must be an integer or null")
}

internal fun JsonObject.requiredBoolean(field: String): Boolean =
    (requiredElement(field) as? JsonPrimitive)?.booleanOrNull
        ?: throw OrionException("protocol field '$field' must be a boolean")
