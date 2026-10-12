@file:OptIn(ExperimentalSerializationApi::class)

package com.kitakkun.jetwhale.protocol.gradle

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementDescriptors
import kotlinx.serialization.descriptors.nonNullOriginal
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.serializer
import java.io.File
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * Reads the wire schema of a protocol module's compiled JVM classes from their serial descriptors.
 *
 * It runs in a class loader that holds the module's classes, the module's runtime classpath and
 * this class, so the kotlinx.serialization that builds the descriptors is the one the module
 * ships; [ExtractWireSchemaWorkAction] calls [renderWireSchema] by reflection.
 */
internal class WireSchemaReader {
    private val classLoader: ClassLoader = WireSchemaReader::class.java.classLoader
    private val eventMarker = loadMarker("com.kitakkun.jetwhale.protocol.messaging.JetWhaleEvent")
    private val requestMarker = loadMarker("com.kitakkun.jetwhale.protocol.messaging.JetWhaleRequest")

    fun renderWireSchema(moduleClassDirectories: List<File>, revision: Int): String {
        val moduleClasses = moduleClassDirectories.flatMap(::classNamesIn).map { Class.forName(it, false, classLoader) }
        return DescriptorWalk(neverEncodedElementNamesBySerialName(moduleClasses)).readSchema(moduleClasses, revision).render()
    }

    private fun loadMarker(className: String): Class<*> = try {
        classLoader.loadClass(className)
    } catch (e: ClassNotFoundException) {
        throw WireSchemaRefusal("$className is not on the module's runtime classpath. A protocol module depends on jetwhale-protocol-core.")
    }

    private fun classNamesIn(directory: File): List<String> = directory.walkTopDown()
        .filter { it.isFile && it.extension == "class" && it.name != "module-info.class" }
        .map { it.relativeTo(directory).invariantSeparatorsPath.removeSuffix(".class").replace('/', '.') }
        .filterNot { it.startsWith("META-INF.") }
        .sorted()
        .toList()

    /**
     * The elements under `@EncodeDefault(NEVER)` of each class, by the class's serial name. The
     * descriptor does not carry that annotation: the serialization plugin compiles it into the
     * serializer. Kotlin keeps it on the property's synthetic `<getter>$annotations` method.
     */
    private fun neverEncodedElementNamesBySerialName(moduleClasses: List<Class<*>>): Map<String, Set<String>> = moduleClasses
        .filter { it.isAnnotationPresent(Serializable::class.java) }
        .mapNotNull { serializableClass ->
            val serialName = serializableClass.getAnnotation(SerialName::class.java)?.value ?: serializableClass.canonicalName ?: return@mapNotNull null
            val neverEncodedElementNames = generateSequence(serializableClass) { it.superclass }.flatMap(::neverEncodedElementNamesDeclaredIn).toSet()
            (serialName to neverEncodedElementNames).takeIf { neverEncodedElementNames.isNotEmpty() }
        }
        .toMap()

    private fun neverEncodedElementNamesDeclaredIn(declaringClass: Class<*>): Sequence<String> {
        val propertyNames = declaringClass.declaredFields.map { it.name }
        return declaringClass.declaredMethods.asSequence()
            .filter { it.name.endsWith(ANNOTATIONS_METHOD_SUFFIX) }
            .filter { it.getAnnotation(EncodeDefault::class.java)?.mode == EncodeDefault.Mode.NEVER }
            .map { annotationsMethod ->
                val getterName = annotationsMethod.name.removeSuffix(ANNOTATIONS_METHOD_SUFFIX)
                // The getter of a property whose type is a value class has a mangled name: the
                // getter name, a '-' and a hash.
                val propertyName = propertyNames.firstOrNull { kotlinGetterName(it) == getterName || getterName.startsWith(kotlinGetterName(it) + "-") }
                    ?: throw WireSchemaRefusal(
                        "${declaringClass.name}: cannot tell which property ${annotationsMethod.name} belongs to, so its @EncodeDefault(NEVER) cannot be recorded.",
                    )
                annotationsMethod.getAnnotation(SerialName::class.java)?.value ?: propertyName
            }
    }

    private fun kotlinGetterName(propertyName: String): String {
        val startsWithIsPrefix = propertyName.length > 2 && propertyName.startsWith("is") && propertyName[2] !in 'a'..'z'
        return if (startsWithIsPrefix) propertyName else "get" + propertyName.replaceFirstChar { if (it in 'a'..'z') it.uppercaseChar() else it }
    }

    private inner class DescriptorWalk(private val neverEncodedElementNamesBySerialName: Map<String, Set<String>>) {
        private val shapes = mutableMapOf<String, WireShape>()
        private val descriptorsBySerialName = mutableMapOf<String, SerialDescriptor>()

        fun readSchema(moduleClasses: List<Class<*>>, revision: Int): WireSchema {
            val messages = moduleClasses
                .filter { !it.isInterface && !Modifier.isAbstract(it.modifiers) }
                .filter { eventMarker.isAssignableFrom(it) || requestMarker.isAssignableFrom(it) }
                .map { messageClass ->
                    val descriptor = serializerDescriptorOf(messageClass)
                    val messageTypeRef = typeRefRecordingShapesOf(descriptor, path = descriptor.serialName)
                    if (requestMarker.isAssignableFrom(messageClass)) {
                        val replyPath = "${messageTypeRef.head} (reply)"
                        WireMessage(messageTypeRef.head, WireMessageKind.REQUEST, typeRefRecordingShapesOf(serializerDescriptorOf(replyTypeOf(messageClass)), replyPath))
                    } else {
                        WireMessage(messageTypeRef.head, WireMessageKind.EVENT, replyType = null)
                    }
                }
            messages.groupBy { it.serialName }.filterValues { it.size > 1 }.keys.firstOrNull()?.let { serialName ->
                throw WireSchemaRefusal("Two messages have the serial name $serialName, and a message is routed by it.")
            }
            return WireSchema(revision, messages.associateBy { it.serialName }, shapes.toMap())
        }

        private fun serializerDescriptorOf(type: Type): SerialDescriptor = try {
            serializer(type).descriptor
        } catch (e: Exception) {
            throw WireSchemaRefusal("$type has no serializer: ${e.message}")
        }

        private fun replyTypeOf(requestClass: Class<*>): Type {
            fun replyTypeIn(type: Type?): Type? = when (type) {
                is ParameterizedType -> if (type.rawType == requestMarker) type.actualTypeArguments.single() else replyTypeIn(type.rawType)
                is Class<*> -> (type.genericInterfaces.asSequence() + sequenceOf(type.genericSuperclass)).firstNotNullOfOrNull(::replyTypeIn)
                else -> null
            }
            return replyTypeIn(requestClass) ?: throw WireSchemaRefusal("${requestClass.name}: no JetWhaleRequest<R> supertype names its reply type.")
        }

        private fun typeRefRecordingShapesOf(descriptor: SerialDescriptor, path: String): WireTypeRef {
            val isNullable = descriptor.isNullable
            val nonNullDescriptor = descriptor.nonNullOriginal
            JSON_ELEMENT_HEADS[nonNullDescriptor.serialName]?.let { return WireTypeRef(it, emptyList(), isNullable) }
            return when (val kind = nonNullDescriptor.kind) {
                is PrimitiveKind -> WireTypeRef(primitiveHeadOf(kind), emptyList(), isNullable)

                StructureKind.LIST -> WireTypeRef("list", listOf(typeRefRecordingShapesOf(nonNullDescriptor.getElementDescriptor(0), "$path[]")), isNullable)

                StructureKind.MAP -> WireTypeRef(
                    "map",
                    listOf(typeRefRecordingShapesOf(nonNullDescriptor.getElementDescriptor(0), "$path{key}"), typeRefRecordingShapesOf(nonNullDescriptor.getElementDescriptor(1), "$path{value}")),
                    isNullable,
                )

                StructureKind.CLASS -> if (nonNullDescriptor.isInline) {
                    typeRefRecordingShapesOf(nonNullDescriptor.getElementDescriptor(0), path).copy(isNullable = isNullable)
                } else {
                    recordShape(nonNullDescriptor, path) { classShapeOf(nonNullDescriptor, path) }
                    WireTypeRef(nonNullDescriptor.serialName, emptyList(), isNullable)
                }

                StructureKind.OBJECT -> {
                    recordShape(nonNullDescriptor, path) { ObjectShape(nonNullDescriptor.serialName) }
                    WireTypeRef(nonNullDescriptor.serialName, emptyList(), isNullable)
                }

                SerialKind.ENUM -> {
                    recordShape(nonNullDescriptor, path) { EnumShape(nonNullDescriptor.serialName, (0 until nonNullDescriptor.elementsCount).map(nonNullDescriptor::getElementName).toSet()) }
                    WireTypeRef(nonNullDescriptor.serialName, emptyList(), isNullable)
                }

                PolymorphicKind.SEALED -> {
                    recordShape(nonNullDescriptor, path) {
                        val discriminator = nonNullDescriptor.annotations.filterIsInstance<JsonClassDiscriminator>().firstOrNull()?.discriminator ?: DEFAULT_CLASS_DISCRIMINATOR
                        val subtypesDescriptor = nonNullDescriptor.getElementDescriptor(SEALED_SUBTYPES_ELEMENT_INDEX)
                        val subtypeSerialNames = subtypesDescriptor.elementDescriptors.map { subtype -> typeRefRecordingShapesOf(subtype, "$path<${subtype.serialName}>").head }
                        SealedShape(nonNullDescriptor.serialName, discriminator, subtypeSerialNames.toSet())
                    }
                    WireTypeRef(nonNullDescriptor.serialName, emptyList(), isNullable)
                }

                PolymorphicKind.OPEN, SerialKind.CONTEXTUAL -> throw WireSchemaRefusal(
                    "$path: ${nonNullDescriptor.serialName} is ${if (kind == SerialKind.CONTEXTUAL) "contextual" else "open polymorphic"}. " +
                        "The plugin payload format has no serializers module, so it cannot be sent.",
                )
            }
        }

        private fun classShapeOf(descriptor: SerialDescriptor, path: String): ClassShape {
            val neverEncodedElementNames = neverEncodedElementNamesBySerialName[descriptor.serialName].orEmpty()
            val fields = (0 until descriptor.elementsCount).associate { index ->
                val elementName = descriptor.getElementName(index)
                val isOptional = descriptor.isElementOptional(index)
                elementName to WireField(
                    type = typeRefRecordingShapesOf(descriptor.getElementDescriptor(index), "$path.$elementName"),
                    isOptional = isOptional,
                    isOmittedAtDefault = isOptional && elementName in neverEncodedElementNames,
                    alsoReadAs = descriptor.getElementAnnotations(index).filterIsInstance<JsonNames>().flatMap { it.names.asList() }.toSet(),
                )
            }
            return ClassShape(descriptor.serialName, fields)
        }

        /** Records the shape of [descriptor] the first time its serial name is reached. */
        private fun recordShape(descriptor: SerialDescriptor, path: String, shapeOf: () -> WireShape) {
            val recordedDescriptor = descriptorsBySerialName[descriptor.serialName]
            if (recordedDescriptor != null) {
                if (recordedDescriptor != descriptor) {
                    throw WireSchemaRefusal(
                        "$path: ${descriptor.serialName} is reached with two different shapes: a generic class with two type arguments, " +
                            "or two classes with one serial name. The dump cannot tell them apart.",
                    )
                }
                return
            }
            descriptorsBySerialName[descriptor.serialName] = descriptor
            shapes[descriptor.serialName] = shapeOf()
        }
    }

    private companion object {
        const val ANNOTATIONS_METHOD_SUFFIX = "\$annotations"

        /** A sealed descriptor's elements are the discriminator and then one whose elements are the subtypeSerialNames. */
        const val SEALED_SUBTYPES_ELEMENT_INDEX = 1

        /** The payload format leaves `classDiscriminator` at kotlinx.serialization's default. */
        const val DEFAULT_CLASS_DISCRIMINATOR = "type"

        val JSON_ELEMENT_HEADS: Map<String, String> = mapOf(
            "kotlinx.serialization.json.JsonElement" to "json",
            "kotlinx.serialization.json.JsonArray" to "json-array",
            "kotlinx.serialization.json.JsonNull" to "json-null",
            "kotlinx.serialization.json.JsonObject" to "json-object",
            "kotlinx.serialization.json.JsonPrimitive" to "json-primitive",
            "kotlinx.serialization.json.JsonLiteral" to "json-primitive",
        )

        fun primitiveHeadOf(kind: PrimitiveKind): String = when (kind) {
            PrimitiveKind.BOOLEAN -> "boolean"
            PrimitiveKind.BYTE -> "byte"
            PrimitiveKind.CHAR -> "char"
            PrimitiveKind.DOUBLE -> "double"
            PrimitiveKind.FLOAT -> "float"
            PrimitiveKind.INT -> "int"
            PrimitiveKind.LONG -> "long"
            PrimitiveKind.SHORT -> "short"
            PrimitiveKind.STRING -> "string"
        }
    }
}

/** A protocol module whose wire schema cannot be dumped, with what is wrong and where. */
internal class WireSchemaRefusal(message: String) : Exception(message)
