package dev.orion.sdk.internal

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Derives the supported JSON Schema subset from a Kotlin serializer descriptor. */
@OptIn(ExperimentalSerializationApi::class)
internal fun schemaFor(descriptor: SerialDescriptor): JsonObject {

    val schema = descriptorSchema(descriptor)

    require(schema["type"]?.let(JsonElement::asSchemaType) == "object") {
        "typed tool arguments must serialize as a JSON object"
    }

    return schema

}

@OptIn(ExperimentalSerializationApi::class)
private fun descriptorSchema(descriptor: SerialDescriptor): JsonObject {

    val baseSchema = when {
        descriptor.isInline -> descriptorSchema(descriptor.getElementDescriptor(0))
        descriptor.kind is PrimitiveKind -> primitiveSchema(descriptor.kind as PrimitiveKind)
        descriptor.kind == SerialKind.ENUM -> enumSchema(descriptor)
        descriptor.kind == StructureKind.CLASS || descriptor.kind == StructureKind.OBJECT ->
            classSchema(descriptor)
        descriptor.kind == StructureKind.LIST -> listSchema(descriptor)
        descriptor.kind == StructureKind.MAP -> mapSchema(descriptor)
        descriptor.kind is PolymorphicKind || descriptor.kind == SerialKind.CONTEXTUAL ->
            unsupportedDescriptor(descriptor)
        else -> unsupportedDescriptor(descriptor)
    }

    return if (descriptor.isNullable) nullableSchema(baseSchema) else baseSchema

}

private fun primitiveSchema(kind: PrimitiveKind): JsonObject = buildJsonObject {

    put("type", when (kind) {
        PrimitiveKind.BOOLEAN -> "boolean"
        PrimitiveKind.BYTE,
        PrimitiveKind.SHORT,
        PrimitiveKind.INT,
        PrimitiveKind.LONG,
        -> "integer"
        PrimitiveKind.FLOAT,
        PrimitiveKind.DOUBLE,
        -> "number"
        PrimitiveKind.CHAR,
        PrimitiveKind.STRING,
        -> "string"
    })

}

@OptIn(ExperimentalSerializationApi::class)
private fun enumSchema(descriptor: SerialDescriptor): JsonObject = buildJsonObject {

    put("type", "string")
    putJsonArray("enum") {
        repeat(descriptor.elementsCount) { index -> add(descriptor.getElementName(index)) }
    }

}

@OptIn(ExperimentalSerializationApi::class)
private fun classSchema(descriptor: SerialDescriptor): JsonObject = buildJsonObject {

    put("type", "object")
    putJsonObject("properties") {
        repeat(descriptor.elementsCount) { index ->
            put(
                descriptor.getElementName(index),
                descriptorSchema(descriptor.getElementDescriptor(index)),
            )
        }
    }

    val requiredFields = buildJsonArray {
        repeat(descriptor.elementsCount) { index ->
            if (!descriptor.isElementOptional(index)) {
                add(descriptor.getElementName(index))
            }
        }
    }
    if (requiredFields.isNotEmpty()) put("required", requiredFields)

    put("additionalProperties", false)

}

private fun listSchema(descriptor: SerialDescriptor): JsonObject = buildJsonObject {

    put("type", "array")
    put("items", descriptorSchema(descriptor.getElementDescriptor(0)))

}

private fun mapSchema(descriptor: SerialDescriptor): JsonObject {

    val keyDescriptor = descriptor.getElementDescriptor(0)
    require(keyDescriptor.kind == PrimitiveKind.STRING) {
        "JSON object maps require string keys: ${descriptor.serialName}"
    }

    return buildJsonObject {
        put("type", "object")
        put("additionalProperties", descriptorSchema(descriptor.getElementDescriptor(1)))
    }

}

private fun nullableSchema(baseSchema: JsonObject): JsonObject = buildJsonObject {

    put("anyOf", JsonArray(listOf(baseSchema, buildJsonObject { put("type", "null") })))

}

private fun unsupportedDescriptor(descriptor: SerialDescriptor): Nothing = throw IllegalArgumentException(
    "typed tool schema does not support ${descriptor.kind} descriptor ${descriptor.serialName}",
)

private fun JsonElement.asSchemaType(): String? = (this as? JsonPrimitive)?.content
