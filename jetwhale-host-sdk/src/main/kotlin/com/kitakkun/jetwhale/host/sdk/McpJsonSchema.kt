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
 * kotlinx.serialization decodes it. A sealed hierarchy becomes a `oneOf` over its subclasses, each
 * carrying the class discriminator as a `const`. Open polymorphic types are advertised as an
 * unconstrained `object`, since their subclasses are only known at runtime.
 *
 * The schema follows [json]'s configuration — its class discriminator and naming strategy — so the
 * shape advertised to the caller is the shape the same format decodes.
 */
internal fun SerialDescriptor.toJsonSchema(json: Json): JsonObject = buildSchema(
    SchemaContext(
        classDiscriminator = json.configuration.classDiscriminator,
        writesClassDiscriminator = json.configuration.classDiscriminatorMode != ClassDiscriminatorMode.NONE,
        namingStrategy = json.configuration.namingStrategy,
    ),
    mutableSetOf(),
)

private class SchemaContext(
    val classDiscriminator: String,
    val writesClassDiscriminator: Boolean,
    val namingStrategy: JsonNamingStrategy?,
)

private fun SerialDescriptor.buildSchema(context: SchemaContext, enclosingTypes: MutableSet<String>): JsonObject {
    // A value class is transparent on the wire: it encodes as its single underlying element.
    if (isInline) return getElementDescriptor(0).buildSchema(context, enclosingTypes)

    val schema = when (kind) {
        PrimitiveKind.STRING, PrimitiveKind.CHAR -> typeOnly("string")

        PrimitiveKind.BYTE, PrimitiveKind.SHORT, PrimitiveKind.INT, PrimitiveKind.LONG -> typeOnly("integer")

        PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> typeOnly("number")

        PrimitiveKind.BOOLEAN -> typeOnly("boolean")

        SerialKind.ENUM -> buildJsonObject {
            put("type", "string")
            putJsonArray("enum") { elementNames.forEach { add(it) } }
        }

        StructureKind.LIST -> buildJsonObject {
            put("type", "array")
            put("items", getElementDescriptor(0).buildSchema(context, enclosingTypes))
        }

        // Element 0 is the key descriptor, element 1 the value descriptor.
        StructureKind.MAP -> buildJsonObject {
            put("type", "object")
            put("additionalProperties", getElementDescriptor(1).buildSchema(context, enclosingTypes))
        }

        // Only these kinds can contain themselves, so only these are guarded against recursion.
        // Collections repeat their serial name at every nesting level
        // ("kotlin.collections.ArrayList"), so guarding them too would cut List<List<T>> short.
        StructureKind.CLASS, StructureKind.OBJECT -> guarded(enclosingTypes) { classSchema(context, enclosingTypes) }

        PolymorphicKind.SEALED -> guarded(enclosingTypes) { sealedSchema(context, enclosingTypes) }

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

private fun SerialDescriptor.classSchema(context: SchemaContext, enclosingTypes: MutableSet<String>): JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        for (index in 0 until elementsCount) {
            put(context.jsonNameOf(this@classSchema, index), elementSchema(index, context, enclosingTypes))
        }
    }
    val required = (0 until elementsCount)
        .filterNot { isElementOptional(it) }
        .map { context.jsonNameOf(this@classSchema, it) }
    if (required.isNotEmpty()) putJsonArray("required") { required.forEach { add(it) } }
}

/**
 * A sealed serializer's descriptor holds two elements: the discriminator and a contextual holder
 * whose elements are the subclasses, named by their serial names. `Json` writes the discriminator
 * flattened into the value's own object, so each variant is that subclass' object schema with the
 * discriminator pinned to a constant.
 */
private fun SerialDescriptor.sealedSchema(context: SchemaContext, enclosingTypes: MutableSet<String>): JsonObject {
    // A type-level annotation overrides the format-wide discriminator, the same way Json resolves it.
    val discriminator = annotations.filterIsInstance<JsonClassDiscriminator>().firstOrNull()?.discriminator
        ?: context.classDiscriminator
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
    val properties = schema["properties"] as? JsonObject ?: JsonObject(emptyMap())
    val required = (schema["required"] as? JsonArray).orEmpty().map { (it as JsonPrimitive).content }
    // ClassDiscriminatorMode.NONE writes no discriminator, so advertising one would describe input
    // this format cannot produce; the variant shapes are still worth showing.
    if (discriminator == null) return schema
    val discriminatorSchema = buildJsonObject {
        put("type", "string")
        put("const", serialName)
    }
    return buildJsonObject {
        put("type", "object")
        put("properties", JsonObject(mapOf(discriminator to discriminatorSchema) + properties))
        putJsonArray("required") { (listOf(discriminator) + required).forEach { add(it) } }
        schema["description"]?.let { put("description", it) }
    }
}

private fun SerialDescriptor.elementSchema(index: Int, context: SchemaContext, enclosingTypes: MutableSet<String>): JsonObject {
    val schema = getElementDescriptor(index).buildSchema(context, enclosingTypes)
    // The property's own annotation wins over one inherited from the property type's class.
    val description = getElementAnnotations(index).mcpDescription() ?: return schema
    return schema.withDescription(description)
}

private fun SchemaContext.jsonNameOf(descriptor: SerialDescriptor, index: Int): String {
    val serialName = descriptor.getElementName(index)
    return namingStrategy?.serialNameForJson(descriptor, index, serialName) ?: serialName
}

private fun List<Annotation>.mcpDescription(): String? = filterIsInstance<McpDescription>().firstOrNull()?.value

private fun JsonObject.withDescription(description: String): JsonObject = JsonObject(this + ("description" to JsonPrimitive(description)))

private fun typeOnly(type: String): JsonObject = buildJsonObject { put("type", type) }
