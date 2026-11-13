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
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import java.util.zip.ZipFile

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

    fun renderWireSchema(moduleClassDirectories: List<File>, runtimeClasspath: List<File>, revision: Int): String {
        val moduleClasses = moduleClassDirectories.flatMap(::classNamesIn).map { Class.forName(it, false, classLoader) }
        return DescriptorWalk(neverEncodedElementNamesBySerialName(moduleClasses), runtimeClasspath).readSchema(moduleClasses, revision).render()
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
     * The `@Serializable` classes on [runtimeClasspath], found by the annotation's name in their
     * class files before any is loaded. Kotlin's and kotlinx.serialization's own classes are left
     * out: none of them has a property under `@EncodeDefault`.
     */
    private fun serializableClassesOn(runtimeClasspath: List<File>): List<Class<*>> = runtimeClasspath
        .flatMap { classpathEntry ->
            when {
                classpathEntry.isDirectory -> classNamesIn(classpathEntry).filter { className ->
                    classpathEntry.resolve(className.replace('.', '/') + ".class").readBytes().mentionsSerializable()
                }

                classpathEntry.isFile && classpathEntry.extension == "jar" -> ZipFile(classpathEntry).use { jar ->
                    jar.entries().asSequence()
                        .filter { it.name.endsWith(".class") && !it.name.startsWith("META-INF/") && !it.name.endsWith("module-info.class") }
                        .filter { jar.getInputStream(it).use { input -> input.readBytes() }.mentionsSerializable() }
                        .map { it.name.removeSuffix(".class").replace('/', '.') }
                        .toList()
                }

                else -> emptyList()
            }
        }
        .filterNot { it.startsWith("kotlin.") || it.startsWith("kotlinx.serialization.") }
        // A class that cannot load is one no descriptor can reach either.
        .mapNotNull { runCatching { Class.forName(it, false, classLoader) }.getOrNull() }
        .filter { it.isAnnotationPresent(Serializable::class.java) }

    private fun ByteArray.mentionsSerializable(): Boolean = String(this, Charsets.ISO_8859_1).contains(SERIALIZABLE_TYPE_DESCRIPTOR)

    /**
     * The elements under `@EncodeDefault(NEVER)` of each `@Serializable` class, by the class's
     * serial name. The descriptor does not carry that annotation: the serialization plugin compiles
     * it into the serializer. Kotlin keeps it on the property's synthetic `<getter>$annotations`
     * method.
     */
    private fun neverEncodedElementNamesBySerialName(classes: List<Class<*>>): Map<String, Set<String>> = classes
        .filter { it.isAnnotationPresent(Serializable::class.java) }
        .mapNotNull { serializableClass ->
            // kotlinx.serialization's default serial name is the Kotlin qualified name, which joins
            // a nested class with '.' as canonicalName does; Class.name uses '$'.
            val serialName = serializableClass.getAnnotation(SerialName::class.java)?.value ?: serializableClass.canonicalName ?: return@mapNotNull null
            serialName to generateSequence(serializableClass) { it.superclass }.flatMap(::neverEncodedElementNamesDeclaredIn).toSet()
        }
        .toMap()

    private fun neverEncodedElementNamesDeclaredIn(declaringClass: Class<*>): Sequence<String> {
        val propertyNames = declaringClass.declaredFields.map { it.name }
        return declaringClass.declaredMethods.asSequence()
            .filter { it.name.endsWith(ANNOTATIONS_METHOD_SUFFIX) }
            .filter { it.getAnnotation(EncodeDefault::class.java)?.mode == EncodeDefault.Mode.NEVER }
            .map { annotationsMethod ->
                val getterName = annotationsMethod.name.removeSuffix(ANNOTATIONS_METHOD_SUFFIX)
                // Kotlin mangles the getter of a property whose type is a value class, and with it
                // the $annotations method: the getter name, a '-' and a hash.
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

    private inner class DescriptorWalk(
        private val moduleNeverEncodedElementNamesBySerialName: Map<String, Set<String>>,
        private val runtimeClasspath: List<File>,
    ) {
        /** Scanned only when the walk reaches a class from outside the module, which is rare. */
        private val dependencyNeverEncodedElementNamesBySerialName: Map<String, Set<String>> by lazy {
            neverEncodedElementNamesBySerialName(serializableClassesOn(runtimeClasspath))
        }

        private val shapes = mutableMapOf<String, WireShape>()
        private val descriptorsBySerialName = mutableMapOf<String, SerialDescriptor>()

        fun readSchema(moduleClasses: List<Class<*>>, revision: Int): WireSchema {
            val messages = moduleClasses
                .filter { !it.isInterface && !Modifier.isAbstract(it.modifiers) }
                .filter { eventMarker.isAssignableFrom(it) || requestMarker.isAssignableFrom(it) }
                .map { messageClass ->
                    val descriptor = serializerDescriptorOf(messageClass)
                    if (descriptor.isInline || descriptor.kind !in MESSAGE_SERIAL_KINDS) {
                        throw WireSchemaRefusal(
                            "${messageClass.name}: a message is routed by its serial name, so the dump records it as a class, an object " +
                                "or an enum of that name, but its serializer describes a ${if (descriptor.isInline) "value class" else descriptor.kind}.",
                        )
                    }
                    val messageSerialName = descriptor.serialName
                    recordShape(descriptor, path = messageSerialName)
                    if (requestMarker.isAssignableFrom(messageClass)) {
                        val replyPath = "$messageSerialName (reply)"
                        WireMessage(messageSerialName, WireMessageKind.REQUEST, typeRefRecordingShapesOf(serializerDescriptorOf(replyTypeOf(messageClass)), replyPath))
                    } else {
                        WireMessage(messageSerialName, WireMessageKind.EVENT, replyType = null)
                    }
                }
            messages.groupBy { it.serialName }.filterValues { it.size > 1 }.keys.firstOrNull()?.let { serialName ->
                throw WireSchemaRefusal("Two messages have the serial name $serialName, and a message is routed by it.")
            }
            val schema = WireSchema(revision, messages.associateBy { it.serialName }, shapes.toMap())
            val readBack = try {
                WireSchema.parse(schema.render())
            } catch (e: IllegalArgumentException) {
                throw WireSchemaRefusal("$UNREADABLE_NAME_REFUSAL ${e.message}")
            }
            if (readBack != schema) {
                val serialNameNotReadBack = (schema.messages.keys + schema.shapes.keys).sorted()
                    .firstOrNull { readBack.messages[it] != schema.messages[it] || readBack.shapes[it] != schema.shapes[it] }
                throw WireSchemaRefusal("${serialNameNotReadBack ?: "A serial name"}: $UNREADABLE_NAME_REFUSAL")
            }
            return schema
        }

        private fun serializerDescriptorOf(type: Type): SerialDescriptor = try {
            serializer(type).descriptor
        } catch (e: Exception) {
            throw WireSchemaRefusal("$type has no serializer: ${e.message}")
        }

        /**
         * The `R` of the request's `JetWhaleRequest<R>` supertype, with the type arguments of every
         * generic supertype between them substituted, as in `Ping : BaseRequest<Pong>` where
         * `BaseRequest<R> : JetWhaleRequest<R>`.
         */
        private fun replyTypeOf(requestClass: Class<*>): Type {
            fun replyTypeIn(supertype: Type, typeArgumentsByVariable: Map<TypeVariable<*>, Type>): Type? {
                val rawClass = (if (supertype is ParameterizedType) supertype.rawType else supertype) as? Class<*> ?: return null
                val typeArguments = (supertype as? ParameterizedType)?.actualTypeArguments.orEmpty().map { it.substitutedBy(typeArgumentsByVariable) }
                if (rawClass == requestMarker) return typeArguments.single()
                val rawClassTypeArgumentsByVariable = rawClass.typeParameters.zip(typeArguments).toMap<TypeVariable<*>, Type>()
                return (rawClass.genericInterfaces.asSequence() + listOfNotNull(rawClass.genericSuperclass))
                    .firstNotNullOfOrNull { replyTypeIn(it, rawClassTypeArgumentsByVariable) }
            }
            val replyType = replyTypeIn(requestClass, emptyMap())
                ?: throw WireSchemaRefusal("${requestClass.name}: no JetWhaleRequest<R> supertype names its reply type.")
            if (replyType is TypeVariable<*>) {
                throw WireSchemaRefusal("${requestClass.name}: its reply type is the type variable ${replyType.name}, which no supertype binds.")
            }
            return replyType
        }

        private fun Type.substitutedBy(typeArgumentsByVariable: Map<TypeVariable<*>, Type>): Type = when (this) {
            is TypeVariable<*> -> typeArgumentsByVariable[this] ?: this

            is ParameterizedType -> SubstitutedParameterizedType(rawType, actualTypeArguments.map { it.substitutedBy(typeArgumentsByVariable) }, ownerType)

            // Kotlin writes `List<R>` inside a supertype's type arguments as `List<? extends R>`,
            // and serializer(Type) reads a wildcard as its first upper bound.
            is WildcardType -> upperBounds.first().substitutedBy(typeArgumentsByVariable)

            else -> this
        }

        private fun typeRefRecordingShapesOf(descriptor: SerialDescriptor, path: String): WireTypeRef {
            val isNullable = descriptor.isNullable
            val nonNullDescriptor = descriptor.nonNullOriginal
            JSON_ELEMENT_HEADS[nonNullDescriptor.serialName]?.let { return WireTypeRef(it, emptyList(), isNullable) }
            return when (val kind = nonNullDescriptor.kind) {
                is PrimitiveKind -> WireTypeRef(primitiveHeadOf(kind), emptyList(), isNullable)

                StructureKind.LIST -> WireTypeRef("list", listOf(typeRefRecordingShapesOf(nonNullDescriptor.getElementDescriptor(0), "$path[]")), isNullable)

                StructureKind.MAP -> {
                    val keyTypeRef = typeRefRecordingShapesOf(nonNullDescriptor.getElementDescriptor(0), "$path{key}")
                    if (keyTypeRef.head !in MAP_KEY_HEADS && shapes[keyTypeRef.head] !is EnumShape) {
                        throw WireSchemaRefusal(
                            "$path{key}: $keyTypeRef cannot key a JSON object. The plugin payload format leaves allowStructuredMapKeys off, " +
                                "so a map's key is a primitive, an enum or a value class over one.",
                        )
                    }
                    WireTypeRef("map", listOf(keyTypeRef, typeRefRecordingShapesOf(nonNullDescriptor.getElementDescriptor(1), "$path{value}")), isNullable)
                }

                StructureKind.CLASS -> if (nonNullDescriptor.isInline) {
                    typeRefRecordingShapesOf(nonNullDescriptor.getElementDescriptor(0), path).let { it.copy(isNullable = it.isNullable || isNullable) }
                } else {
                    shapeTypeRefRecordingShapesOf(nonNullDescriptor, path, isNullable)
                }

                StructureKind.OBJECT, SerialKind.ENUM, PolymorphicKind.SEALED -> shapeTypeRefRecordingShapesOf(nonNullDescriptor, path, isNullable)

                PolymorphicKind.OPEN, SerialKind.CONTEXTUAL -> throw WireSchemaRefusal(
                    "$path: ${nonNullDescriptor.serialName} is ${if (kind == SerialKind.CONTEXTUAL) "contextual" else "open polymorphic"}. " +
                        "The plugin payload format has no serializers module, so it cannot be sent.",
                )
            }
        }

        /**
         * A type ref names a shape by its serial name, so a shape that a field, a reply or a type
         * argument refers to cannot have a built-in's name. A message or a sealed subtype may: the
         * dump lists each by name, never as a type.
         */
        private fun shapeTypeRefRecordingShapesOf(descriptor: SerialDescriptor, path: String, isNullable: Boolean): WireTypeRef {
            if (descriptor.serialName in WireTypeRef.BUILT_IN_HEADS) {
                throw WireSchemaRefusal(
                    "$path: its type is named ${descriptor.serialName}, which is what the wire schema dump calls a built-in type. " +
                        "Give the class another @SerialName.",
                )
            }
            recordShape(descriptor, path)
            return WireTypeRef(descriptor.serialName, emptyList(), isNullable)
        }

        private fun classShapeOf(descriptor: SerialDescriptor, path: String): ClassShape {
            val neverEncodedElementNames = moduleNeverEncodedElementNamesBySerialName[descriptor.serialName]
                ?: dependencyNeverEncodedElementNamesBySerialName[descriptor.serialName].orEmpty()
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

        /** Records the shape of [descriptor], and every shape it reaches, the first time its serial name is reached. */
        private fun recordShape(descriptor: SerialDescriptor, path: String) {
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
            shapes[descriptor.serialName] = when (descriptor.kind) {
                StructureKind.CLASS -> classShapeOf(descriptor, path)

                StructureKind.OBJECT -> ObjectShape(descriptor.serialName)

                SerialKind.ENUM -> EnumShape(descriptor.serialName, (0 until descriptor.elementsCount).map(descriptor::getElementName).toSet())

                PolymorphicKind.SEALED -> {
                    val discriminator = descriptor.annotations.filterIsInstance<JsonClassDiscriminator>().firstOrNull()?.discriminator ?: DEFAULT_CLASS_DISCRIMINATOR
                    val subtypeDescriptors = descriptor.getElementDescriptor(SEALED_SUBTYPES_ELEMENT_INDEX).elementDescriptors.toList()
                    subtypeDescriptors.forEach { subtype -> recordShape(subtype, "$path<${subtype.serialName}>") }
                    SealedShape(descriptor.serialName, discriminator, subtypeDescriptors.map { it.serialName }.toSet())
                }

                else -> throw WireSchemaRefusal("$path: ${descriptor.serialName} is a ${descriptor.kind}, which the dump does not record as a shape.")
            }
        }
    }

    private companion object {
        const val ANNOTATIONS_METHOD_SUFFIX = "\$annotations"

        const val SERIALIZABLE_TYPE_DESCRIPTOR = "Lkotlinx/serialization/Serializable;"

        val MESSAGE_SERIAL_KINDS: Set<SerialKind> = setOf(StructureKind.CLASS, StructureKind.OBJECT, SerialKind.ENUM)

        /** A sealed descriptor's elements are the discriminator and then one whose elements are the subtypes. */
        const val SEALED_SUBTYPES_ELEMENT_INDEX = 1

        /** The types kotlinx.serialization's JSON writes as an object key without `allowStructuredMapKeys`. */
        val MAP_KEY_HEADS: Set<String> = setOf("boolean", "byte", "char", "double", "float", "int", "long", "short", "string", "json-primitive")

        const val UNREADABLE_NAME_REFUSAL: String =
            "A name uses what the wire schema dump uses as syntax, so the dump would not read back. A type's serial name " +
                "cannot contain '<', '>', ',' or '?', a field's cannot contain ': ', an @JsonNames name cannot contain ', ' or ' | ', " +
                "and no name can start or end with a space or hold a line break."

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

private class SubstitutedParameterizedType(
    private val rawType: Type,
    private val typeArguments: List<Type>,
    private val ownerType: Type?,
) : ParameterizedType {
    override fun getRawType(): Type = rawType

    override fun getActualTypeArguments(): Array<Type> = typeArguments.toTypedArray()

    override fun getOwnerType(): Type? = ownerType

    override fun toString(): String = "${rawType.typeName}<${typeArguments.joinToString(", ") { it.typeName }}>"
}

/** A protocol module whose wire schema cannot be dumped, with what is wrong and where. */
internal class WireSchemaRefusal(message: String) : Exception(message)
