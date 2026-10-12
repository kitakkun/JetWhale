package com.kitakkun.jetwhale.protocol.gradle

import java.security.MessageDigest

/**
 * A protocol module's wire schema: its messages, keyed by serial name, and every type they reach,
 * keyed by serial name. It is what `wire/<project>.wire` holds, as sorted text meant to be read in a
 * diff.
 */
internal data class WireSchema(
    val revision: Int,
    val messages: Map<String, WireMessage>,
    val shapes: Map<String, WireShape>,
) {
    fun render(): String = buildString {
        appendLine("revision $revision")
        appendLine()
        messages.toSortedMap().values.forEach { message ->
            when (message.kind) {
                WireMessageKind.EVENT -> appendLine("event ${message.serialName}")
                WireMessageKind.REQUEST -> appendLine("request ${message.serialName} -> ${message.reply}")
            }
        }
        shapes.toSortedMap().values.forEach { shape ->
            appendLine()
            when (shape) {
                is ClassShape -> {
                    appendLine("class ${shape.serialName}")
                    shape.fields.toSortedMap().forEach { (name, field) -> appendLine("  $name: ${field.render()}") }
                }

                is ObjectShape -> appendLine("object ${shape.serialName}")

                is EnumShape -> {
                    appendLine("enum ${shape.serialName}")
                    shape.entries.sorted().forEach { appendLine("  $it") }
                }

                is SealedShape -> {
                    appendLine("sealed ${shape.serialName} by ${shape.discriminator}")
                    shape.subtypes.sorted().forEach { appendLine("  $it") }
                }
            }
        }
    }

    /**
     * The SHA-256 of the rendered dump without its revision line, as lowercase hex. Two builds have
     * the same hash when they dump the same schema, whatever revision each declares.
     */
    fun hash(): String = MessageDigest.getInstance("SHA-256")
        .digest(render().substringAfter('\n').toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    companion object {
        fun parse(text: String): WireSchema {
            var revision: Int? = null
            val messages = mutableMapOf<String, WireMessage>()
            val shapes = mutableMapOf<String, WireShape>()
            var current: WireShape? = null

            fun closeCurrentShape() {
                current?.let { shapes[it.serialName] = it }
                current = null
            }

            text.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                if (line.startsWith("  ")) {
                    val member = line.trim()
                    current = when (val shape = current) {
                        is ClassShape -> shape.copy(
                            fields = shape.fields + (member.substringBefore(": ") to WireField.parse(member.substringAfter(": "))),
                        )

                        is EnumShape -> shape.copy(entries = shape.entries + member)

                        is SealedShape -> shape.copy(subtypes = shape.subtypes + member)

                        is ObjectShape, null -> throw IllegalArgumentException("A member line outside a class, enum or sealed type: $line")
                    }
                    return@forEach
                }
                closeCurrentShape()
                val keyword = line.substringBefore(' ')
                val rest = line.substringAfter(' ')
                when (keyword) {
                    "revision" -> revision = rest.toIntOrNull() ?: throw IllegalArgumentException("Not a revision: $line")

                    "event" -> messages[rest] = WireMessage(rest, WireMessageKind.EVENT, reply = null)

                    "request" -> {
                        val serialName = rest.substringBefore(" -> ")
                        messages[serialName] = WireMessage(serialName, WireMessageKind.REQUEST, WireTypeRef.parse(rest.substringAfter(" -> ")))
                    }

                    "class" -> current = ClassShape(rest, emptyMap())

                    "object" -> shapes[rest] = ObjectShape(rest)

                    "enum" -> current = EnumShape(rest, emptySet())

                    "sealed" -> current = SealedShape(rest.substringBefore(" by "), rest.substringAfter(" by "), emptySet())

                    else -> throw IllegalArgumentException("Not a line of a wire schema dump: $line")
                }
            }
            closeCurrentShape()
            return WireSchema(
                revision = revision ?: throw IllegalArgumentException("A wire schema dump starts with its revision line."),
                messages = messages,
                shapes = shapes,
            )
        }
    }
}

internal enum class WireMessageKind { EVENT, REQUEST }

/** A message, routed by its [serialName]; a request also names the type of its reply. */
internal data class WireMessage(
    val serialName: String,
    val kind: WireMessageKind,
    val reply: WireTypeRef?,
)

/**
 * A type as an element refers to it: a built-in such as `string`, `list` or `map` with its
 * [arguments], or the serial name of a shape in the schema.
 */
internal data class WireTypeRef(
    val head: String,
    val arguments: List<WireTypeRef>,
    val isNullable: Boolean,
) {
    val isShape: Boolean get() = head !in BUILT_IN_HEADS

    override fun toString(): String = buildString {
        append(head)
        if (arguments.isNotEmpty()) append(arguments.joinToString(", ", "<", ">"))
        if (isNullable) append('?')
    }

    companion object {
        val BUILT_IN_HEADS: Set<String> = setOf(
            "boolean", "byte", "char", "double", "float", "int", "long", "short", "string",
            "list", "map",
            "json", "json-array", "json-null", "json-object", "json-primitive",
        )

        fun parse(text: String): WireTypeRef {
            var position = 0

            fun parseAt(): WireTypeRef {
                val start = position
                while (position < text.length && text[position] !in "<>,?") position++
                val head = text.substring(start, position).trim()
                val arguments = mutableListOf<WireTypeRef>()
                if (position < text.length && text[position] == '<') {
                    position++
                    while (true) {
                        arguments += parseAt()
                        require(position < text.length) { "Unclosed type arguments: $text" }
                        val separator = text[position++]
                        if (separator == '>') break
                        require(separator == ',') { "Not a type: $text" }
                    }
                }
                val isNullable = position < text.length && text[position] == '?'
                if (isNullable) position++
                return WireTypeRef(head, arguments, isNullable)
            }

            return parseAt().also { require(position == text.length) { "Not a type: $text" } }
        }
    }
}

internal sealed interface WireShape {
    val serialName: String
}

internal data class ClassShape(override val serialName: String, val fields: Map<String, WireField>) : WireShape

internal data class ObjectShape(override val serialName: String) : WireShape

internal data class EnumShape(override val serialName: String, val entries: Set<String>) : WireShape

internal data class SealedShape(override val serialName: String, val discriminator: String, val subtypes: Set<String>) : WireShape

/**
 * An element of a class. [isOptional] means it has a default; [isOmittedAtDefault] means
 * `@EncodeDefault(NEVER)` leaves it out of the JSON at that default. [alsoReadAs] holds its
 * `@JsonNames`.
 */
internal data class WireField(
    val type: WireTypeRef,
    val isOptional: Boolean,
    val isOmittedAtDefault: Boolean,
    val alsoReadAs: Set<String>,
) {
    fun render(): String = buildString {
        append(type)
        if (isOptional) append(OPTIONAL_MARK)
        if (isOmittedAtDefault) append(OMITTED_AT_DEFAULT_MARK)
        if (alsoReadAs.isNotEmpty()) append(alsoReadAs.sorted().joinToString(", ", prefix = ALSO_READ_AS_MARK))
    }

    companion object {
        private const val OPTIONAL_MARK = " = …"
        private const val OMITTED_AT_DEFAULT_MARK = ", omitted at default"
        private const val ALSO_READ_AS_MARK = " | also read as "

        fun parse(text: String): WireField {
            val alsoReadAs = text.substringAfter(ALSO_READ_AS_MARK, "").split(", ").filter { it.isNotEmpty() }.toSet()
            val withoutAlsoReadAs = text.substringBefore(ALSO_READ_AS_MARK)
            val isOmittedAtDefault = withoutAlsoReadAs.endsWith(OMITTED_AT_DEFAULT_MARK)
            val withoutOmitted = withoutAlsoReadAs.removeSuffix(OMITTED_AT_DEFAULT_MARK)
            val isOptional = withoutOmitted.endsWith(OPTIONAL_MARK)
            return WireField(WireTypeRef.parse(withoutOmitted.removeSuffix(OPTIONAL_MARK)), isOptional, isOmittedAtDefault, alsoReadAs)
        }
    }
}
