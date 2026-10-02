@file:OptIn(ExperimentalSerializationApi::class)

package com.kitakkun.jetwhale.host.sdk

import com.kitakkun.jetwhale.annotations.McpDescription
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.capturedKClass
import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.descriptors.nonNullOriginal
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleCollector
import kotlin.reflect.KClass

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
 * carrying the class discriminator as a `const`; under [ClassDiscriminatorMode.NONE], where nothing
 * tells the variants apart, it is an `anyOf`. Open polymorphic types are advertised as an
 * unconstrained `object`, since their subclasses are only known at runtime.
 *
 * Where the descriptor does not say what is written, the schema admits any value rather than guess:
 * a contextual type is described by the serializer [json]'s module registers for it, and is
 * unconstrained when none is registered, or only a provider that needs the type arguments'
 * serializers is; a `JsonElement`, `JsonPrimitive` or `JsonNull` is unconstrained, so a `JsonObject`
 * is an object and a `JsonArray` an array of any values.
 *
 * The schema follows [json]'s configuration — its class discriminator, naming strategy and module —
 * so the shape advertised to the caller is the shape the same format reads and writes. The one place
 * the two directions part is [ClassDiscriminatorMode.ALL_JSON_OBJECTS]: the format writes the
 * discriminator into every class, but does not read it back, and a format that does not ignore
 * unknown keys rejects it. So only a schema that [describesOutput] pins it on every class.
 */
internal fun SerialDescriptor.toJsonSchema(json: Json, describesOutput: Boolean): JsonObject = buildSchema(
    SchemaContext(
        classDiscriminator = json.configuration.classDiscriminator,
        writesClassDiscriminator = json.configuration.classDiscriminatorMode != ClassDiscriminatorMode.NONE,
        pinsClassDiscriminatorOnEveryClass = describesOutput && json.configuration.classDiscriminatorMode == ClassDiscriminatorMode.ALL_JSON_OBJECTS,
        namingStrategy = json.configuration.namingStrategy,
        explicitNulls = json.configuration.explicitNulls,
        serializersModule = json.serializersModule,
    ),
    mutableSetOf(),
)

private class SchemaContext(
    val classDiscriminator: String,
    val writesClassDiscriminator: Boolean,
    val pinsClassDiscriminatorOnEveryClass: Boolean,
    val namingStrategy: JsonNamingStrategy?,
    val explicitNulls: Boolean,
    val serializersModule: SerializersModule,
)

// A nullable wrapper's serial name ends in "?", but Json writes the non-null type's name, a class
// discriminator included.
private fun SerialDescriptor.buildSchema(context: SchemaContext, enclosingTypes: MutableSet<String>): JsonObject {
    val schema = nonNullOriginal.nonNullSchema(context, enclosingTypes)
    return if (isNullable) schema.allowingNull() else schema
}

private fun SerialDescriptor.nonNullSchema(context: SchemaContext, enclosingTypes: MutableSet<String>): JsonObject {
    // A value class is transparent on the wire: it encodes as its single underlying element.
    if (isInline) return getElementDescriptor(0).buildSchema(context, enclosingTypes)
    if (serialName in UNCONSTRAINED_JSON_ELEMENTS) return ANY_VALUE

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

        is PolymorphicKind.OPEN -> typeOnly("object")

        is SerialKind.CONTEXTUAL -> context.serializersModule.plainContextualDescriptor(this)?.buildSchema(context, enclosingTypes) ?: ANY_VALUE
    }

    val classDescription = annotations.mcpDescription() ?: return schema
    return schema.withDescription(classDescription)
}

/**
 * The descriptor of the serializer this module registers as is for [contextual]'s class. A class
 * registered through a provider is left unresolved: the provider needs the serializers of the type
 * arguments, which a contextual descriptor does not carry.
 */
private fun SerializersModule.plainContextualDescriptor(contextual: SerialDescriptor): SerialDescriptor? {
    val contextualClass = contextual.capturedKClass ?: return null
    var registered: SerialDescriptor? = null
    dumpTo(
        object : SerializersModuleCollector {
            override fun <T : Any> contextual(kClass: KClass<T>, serializer: KSerializer<T>) {
                if (kClass == contextualClass) registered = serializer.descriptor
            }

            override fun <T : Any> contextual(kClass: KClass<T>, provider: (typeArgumentsSerializers: List<KSerializer<*>>) -> KSerializer<*>) = Unit

            override fun <Base : Any, Sub : Base> polymorphic(baseClass: KClass<Base>, actualClass: KClass<Sub>, actualSerializer: KSerializer<Sub>) = Unit

            override fun <Base : Any> polymorphicDefaultSerializer(baseClass: KClass<Base>, defaultSerializerProvider: (value: Base) -> SerializationStrategy<Base>?) = Unit

            override fun <Base : Any> polymorphicDefaultDeserializer(baseClass: KClass<Base>, defaultDeserializerProvider: (className: String?) -> DeserializationStrategy<Base>?) = Unit
        },
    )
    return registered
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
    if (!context.pinsClassDiscriminatorOnEveryClass) return schema
    return schema.withDiscriminator(annotations.classDiscriminatorOr(context.classDiscriminator), serialName)
}

/**
 * A sealed serializer's descriptor holds two elements: the discriminator and a contextual holder
 * whose elements are the subclasses, named by their serial names. `Json` writes the discriminator
 * flattened into the value's own object, so each variant is that subclass' object schema with the
 * discriminator pinned to a constant; exactly one variant matches a value, and they form a `oneOf`.
 * A format that writes no discriminator leaves two variants of the same shape matching the same
 * value, so there they form an `anyOf`. A sealed descriptor of another shape, such as a
 * `JsonContentPolymorphicSerializer`'s, which lists no subclasses, says nothing of what is written.
 */
private fun SerialDescriptor.sealedSchema(context: SchemaContext, enclosingTypes: MutableSet<String>): JsonObject {
    if (elementsCount != 2 || getElementDescriptor(1).kind != SerialKind.CONTEXTUAL) return ANY_VALUE
    val discriminator = annotations.classDiscriminatorOr(context.classDiscriminator)
    val subclasses = getElementDescriptor(1)
    return buildJsonObject {
        putJsonArray(if (context.writesClassDiscriminator) "oneOf" else "anyOf") {
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
    if (!context.pinsClassDiscriminatorOnEveryClass) return schema.withDiscriminator(discriminator, serialName)
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
 * gains `"null"`, an `enum` gains the `null` entry, and a `oneOf` or `anyOf` gains a null variant.
 */
private fun JsonObject.allowingNull(): JsonObject {
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

private fun typeOnly(type: String): JsonObject = buildJsonObject { put("type", type) }

private val ANY_VALUE = JsonObject(emptyMap())

private val UNCONSTRAINED_JSON_ELEMENTS = setOf(
    JsonElement.serializer().descriptor.serialName,
    JsonPrimitive.serializer().descriptor.serialName,
    JsonNull.serializer().descriptor.serialName,
)
