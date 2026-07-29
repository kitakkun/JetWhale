package com.kitakkun.jetwhale.plugins.nav3.agent

import androidx.navigation3.runtime.NavKey
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavKeyFieldDescriptor
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavKeyTypeDescriptor
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementDescriptors
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleCollector
import kotlin.reflect.KClass

// How deep a nested @Serializable field is expanded in a key template. Past this the placeholder is
// an empty object: templates are a starting point for a human or an agent, not a schema.
private const val MAX_TEMPLATE_DEPTH = 3

/**
 * Derives the catalog of constructible key types from the app's serializers — the sealed hierarchy
 * the key serializer describes, the subtypes registered against `NavKey` in [serializersModule], or
 * both.
 */
@OptIn(ExperimentalSerializationApi::class)
internal fun describeNavKeyTypes(
    keySerializer: KSerializer<*>,
    serializersModule: SerializersModule,
): List<NavKeyTypeDescriptor> {
    val descriptors = LinkedHashMap<String, SerialDescriptor>()

    // Closed polymorphism: a sealed hierarchy enumerates its subclasses in its own descriptor,
    // whose second element holds one child descriptor per subclass.
    val root = keySerializer.descriptor
    if (root.kind == PolymorphicKind.SEALED && root.elementsCount > 1) {
        root.getElementDescriptor(1).elementDescriptors.forEach { descriptors.getOrPut(it.serialName) { it } }
    }
    // Open polymorphism: the subclasses exist only in the module the app registered them in.
    serializersModule.dumpTo(
        NavKeyRegistrationCollector { descriptor -> descriptors.getOrPut(descriptor.serialName) { descriptor } },
    )

    return descriptors.values.map { it.toNavKeyTypeDescriptor() }
}

/** Collects the `polymorphic(NavKey::class, ...)` registrations of a module and ignores the rest. */
@OptIn(ExperimentalSerializationApi::class)
private class NavKeyRegistrationCollector(
    private val onNavKeySubtype: (SerialDescriptor) -> Unit,
) : SerializersModuleCollector {
    override fun <T : Any> contextual(
        kClass: KClass<T>,
        provider: (typeArgumentsSerializers: List<KSerializer<*>>) -> KSerializer<*>,
    ) = Unit

    override fun <Base : Any, Sub : Base> polymorphic(
        baseClass: KClass<Base>,
        actualClass: KClass<Sub>,
        actualSerializer: KSerializer<Sub>,
    ) {
        if (baseClass == NavKey::class) onNavKeySubtype(actualSerializer.descriptor)
    }

    override fun <Base : Any> polymorphicDefaultSerializer(
        baseClass: KClass<Base>,
        defaultSerializerProvider: (value: Base) -> SerializationStrategy<Base>?,
    ) = Unit

    override fun <Base : Any> polymorphicDefaultDeserializer(
        baseClass: KClass<Base>,
        defaultDeserializerProvider: (className: String?) -> DeserializationStrategy<Base>?,
    ) = Unit
}

@OptIn(ExperimentalSerializationApi::class)
private fun SerialDescriptor.toNavKeyTypeDescriptor(): NavKeyTypeDescriptor = NavKeyTypeDescriptor(
    serialName = serialName,
    fields = (0 until elementsCount).map { index ->
        val element = getElementDescriptor(index)
        NavKeyFieldDescriptor(
            name = getElementName(index),
            type = readableTypeName(element),
            optional = isElementOptional(index),
            nullable = element.isNullable,
        )
    },
    template = objectTemplate(this, typeName = serialName, visited = emptySet(), depth = 0),
)

/**
 * A fill-in-the-blanks JSON skeleton for [descriptor]: the discriminator (only at the top level,
 * where the value is polymorphic) plus one placeholder per field.
 */
private fun objectTemplate(
    descriptor: SerialDescriptor,
    typeName: String?,
    visited: Set<String>,
    depth: Int,
): JsonObject = buildJsonObject {
    if (typeName != null) put(NAV_KEY_DISCRIMINATOR, typeName)
    for (index in 0 until descriptor.elementsCount) {
        put(
            descriptor.getElementName(index),
            placeholderFor(descriptor.getElementDescriptor(index), visited + descriptor.serialName, depth),
        )
    }
}

private fun placeholderFor(descriptor: SerialDescriptor, visited: Set<String>, depth: Int): JsonElement {
    // A nullable field takes null: it is always a valid value, and it reads as "nothing here yet".
    if (descriptor.isNullable) return JsonNull
    return when (descriptor.kind) {
        PrimitiveKind.STRING, PrimitiveKind.CHAR -> JsonPrimitive("")

        PrimitiveKind.BOOLEAN -> JsonPrimitive(false)

        PrimitiveKind.BYTE, PrimitiveKind.SHORT, PrimitiveKind.INT, PrimitiveKind.LONG -> JsonPrimitive(0)

        PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> JsonPrimitive(0.0)

        SerialKind.ENUM -> if (descriptor.elementsCount > 0) JsonPrimitive(descriptor.getElementName(0)) else JsonNull

        StructureKind.LIST -> JsonArray(emptyList())

        StructureKind.MAP -> JsonObject(emptyMap())

        StructureKind.CLASS, StructureKind.OBJECT ->
            if (depth >= MAX_TEMPLATE_DEPTH || descriptor.serialName in visited) {
                JsonObject(emptyMap())
            } else {
                objectTemplate(descriptor, typeName = null, visited = visited, depth = depth + 1)
            }

        // Contextual or polymorphic fields have no single shape to suggest.
        else -> JsonNull
    }
}

private fun readableTypeName(descriptor: SerialDescriptor): String {
    val base = when (descriptor.kind) {
        StructureKind.LIST -> "List<${readableTypeName(descriptor.getElementDescriptor(0))}>"
        StructureKind.MAP -> "Map<${readableTypeName(descriptor.getElementDescriptor(0))}, ${readableTypeName(descriptor.getElementDescriptor(1))}>"
        SerialKind.ENUM -> (0 until descriptor.elementsCount).joinToString("|", prefix = "enum(", postfix = ")") { descriptor.getElementName(it) }
        else -> descriptor.serialName.removeSuffix("?").substringAfterLast('.')
    }
    return if (descriptor.isNullable) "$base?" else base
}
