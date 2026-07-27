package com.kitakkun.jetwhale.host.sdk

import com.kitakkun.jetwhale.annotations.McpDescription
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** The class discriminator `Json` writes for polymorphic values unless the type overrides it. */
private const val DEFAULT_CLASS_DISCRIMINATOR = "type"

/**
 * Derives a JSON Schema fragment describing the values this descriptor accepts, so a serializable
 * type advertises its own shape to MCP clients instead of it being restated in prose.
 *
 * A property is listed in `required` when it has no default value; a nullable property without a
 * default is therefore required (it must be present, and may be `null`), which matches how
 * kotlinx.serialization decodes it. A sealed hierarchy becomes a `oneOf` over its subclasses, each
 * carrying the class discriminator as a `const`. Open polymorphic types are advertised as an
 * unconstrained `object`, since their subclasses are only known at runtime.
 */
internal fun SerialDescriptor.toJsonSchema(): JsonObject = buildSchema(mutableSetOf())

private fun SerialDescriptor.buildSchema(enclosingTypes: MutableSet<String>): JsonObject {
    // A value class is transparent on the wire: it encodes as its single underlying element.
    if (isInline) return getElementDescriptor(0).buildSchema(enclosingTypes)

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
            put("items", getElementDescriptor(0).buildSchema(enclosingTypes))
        }

        // Element 0 is the key descriptor, element 1 the value descriptor.
        StructureKind.MAP -> buildJsonObject {
            put("type", "object")
            put("additionalProperties", getElementDescriptor(1).buildSchema(enclosingTypes))
        }

        // Only these kinds can contain themselves, so only these are guarded against recursion.
        // Collections repeat their serial name at every nesting level
        // ("kotlin.collections.ArrayList"), so guarding them too would cut List<List<T>> short.
        StructureKind.CLASS, StructureKind.OBJECT -> guarded(enclosingTypes) { classSchema(enclosingTypes) }

        PolymorphicKind.SEALED -> guarded(enclosingTypes) { sealedSchema(enclosingTypes) }

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

private fun SerialDescriptor.classSchema(enclosingTypes: MutableSet<String>): JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        for (index in 0 until elementsCount) {
            put(getElementName(index), elementSchema(index, enclosingTypes))
        }
    }
    val required = (0 until elementsCount).filterNot { isElementOptional(it) }.map { getElementName(it) }
    if (required.isNotEmpty()) putJsonArray("required") { required.forEach { add(it) } }
}

/**
 * A sealed serializer's descriptor holds two elements: the discriminator ("type") and a contextual
 * holder whose elements are the subclasses, named by their serial names. `Json` writes the
 * discriminator flattened into the value's own object, so each variant is that subclass' object
 * schema with the discriminator pinned to a constant.
 */
@OptIn(ExperimentalSerializationApi::class)
private fun SerialDescriptor.sealedSchema(enclosingTypes: MutableSet<String>): JsonObject {
    val discriminator = annotations.filterIsInstance<JsonClassDiscriminator>().firstOrNull()?.discriminator
        ?: DEFAULT_CLASS_DISCRIMINATOR
    val subclasses = getElementDescriptor(1)
    return buildJsonObject {
        putJsonArray("oneOf") {
            for (index in 0 until subclasses.elementsCount) {
                add(
                    subclasses.getElementDescriptor(index).variantSchema(
                        discriminator = discriminator,
                        serialName = subclasses.getElementName(index),
                        enclosingTypes = enclosingTypes,
                    ),
                )
            }
        }
    }
}

private fun SerialDescriptor.variantSchema(discriminator: String, serialName: String, enclosingTypes: MutableSet<String>): JsonObject {
    val schema = buildSchema(enclosingTypes)
    val discriminatorSchema = buildJsonObject {
        put("type", "string")
        put("const", serialName)
    }
    val properties = schema["properties"] as? JsonObject ?: JsonObject(emptyMap())
    val required = (schema["required"] as? JsonArray).orEmpty().map { (it as JsonPrimitive).content }
    return buildJsonObject {
        put("type", "object")
        put("properties", JsonObject(mapOf(discriminator to discriminatorSchema) + properties))
        putJsonArray("required") { (listOf(discriminator) + required).forEach { add(it) } }
        schema["description"]?.let { put("description", it) }
    }
}

private fun SerialDescriptor.elementSchema(index: Int, enclosingTypes: MutableSet<String>): JsonObject {
    val schema = getElementDescriptor(index).buildSchema(enclosingTypes)
    // The property's own annotation wins over one inherited from the property type's class.
    val description = getElementAnnotations(index).mcpDescription() ?: return schema
    return schema.withDescription(description)
}

private fun List<Annotation>.mcpDescription(): String? = filterIsInstance<McpDescription>().firstOrNull()?.value

private fun JsonObject.withDescription(description: String): JsonObject = JsonObject(this + ("description" to JsonPrimitive(description)))

private fun typeOnly(type: String): JsonObject = buildJsonObject { put("type", type) }
