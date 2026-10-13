package com.kitakkun.jetwhale.host.sdk

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

internal fun JsonObject.withoutProperty(name: String): JsonObject {
    val properties = (this["properties"] as? JsonObject).orEmpty() - name
    val required = (this["required"] as? JsonArray).orEmpty().filterNot { (it as JsonPrimitive).content == name }
    return buildJsonObject {
        for ((key, value) in this@withoutProperty) {
            when (key) {
                "properties" -> put(key, JsonObject(properties))
                "required" -> if (required.isNotEmpty()) put(key, JsonArray(required))
                else -> put(key, value)
            }
        }
    }
}

internal fun JsonObject.withDiscriminator(discriminator: String, serialName: String): JsonObject {
    val properties = this["properties"] as? JsonObject ?: JsonObject(emptyMap())
    val required = (this["required"] as? JsonArray).orEmpty().map { (it as JsonPrimitive).content }
    val discriminatorSchema = buildJsonObject {
        put("type", "string")
        put("const", serialName)
    }
    return buildJsonObject {
        put("type", "object")
        put("properties", JsonObject(mapOf(discriminator to discriminatorSchema) + properties))
        putJsonArray("required") { (listOf(discriminator) + required).forEach { add(it) } }
        this@withDiscriminator["description"]?.let { put("description", it) }
    }
}

internal fun JsonObject.withDescription(description: String): JsonObject = JsonObject(this + ("description" to JsonPrimitive(description)))

/**
 * Widens a schema so that JSON `null` validates against it, in the form each shape supports: `type`
 * gains `"null"`, an `enum` gains the `null` entry, and a `oneOf` or `anyOf` gains a null variant.
 */
internal fun JsonObject.allowingNull(): JsonObject {
    val type = this["type"]
    val variantsKeyword = listOf("oneOf", "anyOf").firstOrNull { it in this }
    return when {
        type is JsonPrimitive -> JsonObject(
            mapValues { (key, value) ->
                when (key) {
                    "type" -> JsonArray(listOf(type, JsonPrimitive("null")))
                    "enum" -> JsonArray(value as JsonArray + JsonNull)
                    else -> value
                }
            },
        )

        variantsKeyword != null -> JsonObject(this + (variantsKeyword to JsonArray(getValue(variantsKeyword) as JsonArray + typeOnly("null"))))

        else -> this
    }
}

internal fun typeOnly(type: String): JsonObject = buildJsonObject { put("type", type) }
