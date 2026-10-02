@file:OptIn(ExperimentalSerializationApi::class)

package com.kitakkun.jetwhale.host.sdk

import com.kitakkun.jetwhale.annotations.McpDescription
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Derives a JSON Schema fragment describing the values this descriptor accepts, so a serializable
 * type advertises its own shape to MCP clients instead of it being restated in prose.
 *
 * A property is listed in `required` when it has no default value; a nullable property without a
 * default is therefore required (it must be present, and may be `null`), which matches how
 * kotlinx.serialization decodes it. A format with `explicitNulls = false` is the exception: it reads
 * a missing nullable property as `null` and leaves a `null` one out when writing, so there a nullable
 * property is never required. A nullable property's schema admits JSON `null` next to its type, since
 * that is what the format writes for it. A sealed hierarchy becomes a `oneOf` over its subclasses, each
 * carrying the class discriminator as a `const`. Open polymorphic types are advertised as an
 * unconstrained `object`, since their subclasses are only known at runtime.
 *
 * The schema follows [json]'s configuration — its class discriminator and naming strategy — so the
 * shape advertised to the caller is the shape the same format decodes. Under
 * [ClassDiscriminatorMode.ALL_JSON_OBJECTS] every class schema carries the discriminator too, pinned
 * to the class's serial name, since that is what the format writes.
 */
internal fun SerialDescriptor.toJsonSchema(json: Json): JsonObject = buildSchema(
    SchemaContext(
        classDiscriminator = json.configuration.classDiscriminator,
        writesClassDiscriminator = json.configuration.classDiscriminatorMode != ClassDiscriminatorMode.NONE,
        writesClassDiscriminatorOnEveryClass = json.configuration.classDiscriminatorMode == ClassDiscriminatorMode.ALL_JSON_OBJECTS,
        namingStrategy = json.configuration.namingStrategy,
        explicitNulls = json.configuration.explicitNulls,
    ),
    mutableSetOf(),
)

private class SchemaContext(
    val classDiscriminator: String,
    val writesClassDiscriminator: Boolean,
    val writesClassDiscriminatorOnEveryClass: Boolean,
    val namingStrategy: JsonNamingStrategy?,
    val explicitNulls: Boolean,
)

private fun SerialDescriptor.buildSchema(context: SchemaContext, enclosingTypes: MutableSet<String>): JsonObject {
    val schema = nonNullSchema(context, enclosingTypes)
    return if (isNullable) schema.allowingNull() else schema
}

private fun SerialDescriptor.nonNullSchema(context: SchemaContext, enclosingTypes: MutableSet<String>): JsonObject {
    // A value class is transparent on the wire: it encodes as its single underlying element.
    if (isInline) return getElementDescriptor(0).buildSchema(context, enclosingTypes)

    val schema = when (kind) {
        is PrimitiveKind.STRING, is PrimitiveKind.CHAR -> typeOnly("string")

        is PrimitiveKind.BYTE, is PrimitiveKind.SHORT, is PrimitiveKind.INT, is PrimitiveKind.LONG -> typeOnly("integer")

        is PrimitiveKind.FLOAT, is PrimitiveKind.DOUBLE -> typeOnly("number")

        is PrimitiveKind.BOOLEAN -> typeOnly("boolean")

        is SerialKind.ENUM -> buildJsonObject {
            put("type", "string")
            putJsonArray("enum") { elementNames.forEach { add(it) } }
        }

        is StructureKind.LIST -> buildJsonObject {
            put("type", "array")
            put("items", getElementDescriptor(0).buildSchema(context, enclosingTypes))
        }

        // Element 0 is the key descriptor, element 1 the value descriptor.
        is StructureKind.MAP -> buildJsonObject {
            put("type", "object")
            put("additionalProperties", getElementDescriptor(1).buildSchema(context, enclosingTypes))
        }

        // Only these kinds can contain themselves, so only these are guarded against recursion.
        // Collections repeat their serial name at every nesting level
        // ("kotlin.collections.ArrayList"), so guarding them too would cut List<List<T>> short.
        is StructureKind.CLASS, is StructureKind.OBJECT -> guarded(enclosingTypes) { classSchema(context, enclosingTypes) }

        is PolymorphicKind.SEALED -> guarded(enclosingTypes) { sealedSchema(context, enclosingTypes) }

        else -> typeOnly("object")
    }

    val classDescription = annotations.mcpDescription() ?: return schema
    return schema.withDescription(classDescription)
}

private inline fun SerialDescriptor.guarded(enclosingTypes: MutableSet<String>, build: () -> JsonObject): JsonObject {
    if (!enclosingTypes.add(serialName)) return typeOnly("object")
    try {
        return build()
    } finally {
        enclosingTypes.remove(serialName)
    }
}

private fun SerialDescriptor.classSchema(context: SchemaContext, enclosingTypes: MutableSet<String>): JsonObject {
    val schema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            for (index in 0 until elementsCount) {
                put(context.jsonNameOf(this@classSchema, index), elementSchema(index, context, enclosingTypes))
            }
        }
        val required = (0 until elementsCount)
            .filterNot(::isElementOptional)
            .filterNot { !context.explicitNulls && getElementDescriptor(it).isNullable }
            .map { context.jsonNameOf(this@classSchema, it) }
        if (required.isNotEmpty()) putJsonArray("required") { required.forEach { add(it) } }
    }
    if (!context.writesClassDiscriminatorOnEveryClass) return schema
    return schema.withDiscriminator(annotations.classDiscriminatorOr(context.classDiscriminator), serialName)
}

/**
 * A sealed serializer's descriptor holds two elements: the discriminator and a contextual holder
 * whose elements are the subclasses, named by their serial names. `Json` writes the discriminator
 * flattened into the value's own object, so each variant is that subclass' object schema with the
 * discriminator pinned to a constant.
 */
private fun SerialDescriptor.sealedSchema(context: SchemaContext, enclosingTypes: MutableSet<String>): JsonObject {
    val discriminator = annotations.classDiscriminatorOr(context.classDiscriminator)
    val subclasses = getElementDescriptor(1)
    return buildJsonObject {
        putJsonArray("oneOf") {
            for (index in 0 until subclasses.elementsCount) {
                add(
                    subclasses.getElementDescriptor(index).variantSchema(
                        discriminator = discriminator.takeIf { context.writesClassDiscriminator },
                        serialName = subclasses.getElementName(index),
                        context = context,
                        enclosingTypes = enclosingTypes,
                    ),
                )
            }
        }
    }
}

private fun SerialDescriptor.variantSchema(discriminator: String?, serialName: String, context: SchemaContext, enclosingTypes: MutableSet<String>): JsonObject {
    val schema = buildSchema(context, enclosingTypes)
    if (discriminator == null) return schema
    if (!context.writesClassDiscriminatorOnEveryClass) return schema.withDiscriminator(discriminator, serialName)
    // Inside a sealed value Json writes the base's discriminator key, not the subclass's own.
    val ownDiscriminator = annotations.classDiscriminatorOr(context.classDiscriminator)
    if (ownDiscriminator == discriminator) return schema
    return schema.withoutProperty(ownDiscriminator).withDiscriminator(discriminator, serialName)
}

private fun JsonObject.withoutProperty(name: String): JsonObject {
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

private fun JsonObject.withDiscriminator(discriminator: String, serialName: String): JsonObject {
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

// A type-level annotation overrides the format-wide discriminator, the same way Json resolves it.
private fun List<Annotation>.classDiscriminatorOr(default: String): String = filterIsInstance<JsonClassDiscriminator>().firstOrNull()?.discriminator ?: default

private fun SerialDescriptor.elementSchema(index: Int, context: SchemaContext, enclosingTypes: MutableSet<String>): JsonObject {
    val schema = getElementDescriptor(index).buildSchema(context, enclosingTypes)
    val description = getElementAnnotations(index).mcpDescription() ?: return schema
    return schema.withDescription(description)
}

private fun SchemaContext.jsonNameOf(descriptor: SerialDescriptor, index: Int): String {
    val serialName = descriptor.getElementName(index)
    return namingStrategy?.serialNameForJson(descriptor, index, serialName) ?: serialName
}

private fun List<Annotation>.mcpDescription(): String? = filterIsInstance<McpDescription>().firstOrNull()?.value

private fun JsonObject.withDescription(description: String): JsonObject = JsonObject(this + ("description" to JsonPrimitive(description)))

/**
 * Widens a schema so that JSON `null` validates against it, in the form each shape supports: `type`
 * gains `"null"`, an `enum` gains the `null` entry, and a `oneOf` gains a null variant.
 */
private fun JsonObject.allowingNull(): JsonObject {
    val type = this["type"]
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

        "oneOf" in this -> JsonObject(this + ("oneOf" to JsonArray(getValue("oneOf") as JsonArray + typeOnly("null"))))

        else -> this
    }
}

private fun typeOnly(type: String): JsonObject = buildJsonObject { put("type", type) }
