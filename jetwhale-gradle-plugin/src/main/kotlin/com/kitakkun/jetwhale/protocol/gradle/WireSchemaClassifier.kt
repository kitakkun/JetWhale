package com.kitakkun.jetwhale.protocol.gradle

/**
 * One difference between two wire schemas, at [path] from the message it is reached through.
 * [isBreaking] means a peer built before the change, or after it, could fail to decode or route what
 * the other one sends.
 */
internal data class WireSchemaChange(
    val isBreaking: Boolean,
    val path: String,
    val description: String,
) {
    override fun toString(): String = "${if (isBreaking) "breaking" else "additive"}  $path: $description"
}

/**
 * Decides which differences between [previous] and [next] break the wire, under the payload format
 * every plugin uses: unknown keys ignored, defaults encoded, `coerceInputValues` off.
 *
 * Messages are matched by serial name, which is what the wire routes by. From each message the two
 * schemas are walked side by side, so a type the wire never names, such as a renamed class that is
 * not a sealed subtype, is compared by its shape rather than its name.
 */
internal class WireSchemaClassifier(
    private val previous: WireSchema,
    private val next: WireSchema,
) {
    fun classifyChanges(): List<WireSchemaChange> = SideBySideWalk().classifyMessageChanges()

    private inner class SideBySideWalk {
        private val changes = mutableListOf<WireSchemaChange>()
        private val comparedShapePairs = mutableSetOf<Pair<String, String>>()

        fun classifyMessageChanges(): List<WireSchemaChange> {
            (previous.messages.keys + next.messages.keys).sorted().forEach { serialName ->
                val previousMessage = previous.messages[serialName]
                val nextMessage = next.messages[serialName]
                when {
                    nextMessage == null && previousMessage!!.kind == WireMessageKind.EVENT ->
                        additive(serialName, "event removed; an older peer's sends are skipped")

                    nextMessage == null -> breaking(serialName, "request removed; an older peer may still send it and gets a failure")

                    previousMessage == null && nextMessage.kind == WireMessageKind.EVENT -> additive(serialName, "event added; an older peer skips it")

                    previousMessage == null -> breaking(serialName, "request added; an older peer answers it with a failure, which request throws")

                    previousMessage.kind != nextMessage.kind ->
                        breaking(serialName, "changed from ${previousMessage.kind.name.lowercase()} to ${nextMessage.kind.name.lowercase()}")

                    else -> {
                        compareShapes(serialName, serialName, serialName)
                        if (previousMessage.reply != null && nextMessage.reply != null) {
                            compareTypes(previousMessage.reply, nextMessage.reply, "$serialName (reply)")
                        }
                    }
                }
            }
            return changes
        }

        private fun compareShapes(previousName: String, nextName: String, path: String) {
            if (!comparedShapePairs.add(previousName to nextName)) return
            val previousShape = previous.shapes.getValue(previousName)
            val nextShape = next.shapes.getValue(nextName)
            when {
                previousShape is ClassShape && nextShape is ClassShape -> compareFields(previousShape, nextShape, path)

                previousShape is ObjectShape && nextShape is ObjectShape -> Unit

                previousShape is EnumShape && nextShape is EnumShape -> {
                    (nextShape.entries - previousShape.entries).sorted().forEach { breaking(path, "enum entry $it added; an older peer cannot decode it") }
                    (previousShape.entries - nextShape.entries).sorted().forEach { breaking(path, "enum entry $it removed; an older peer may still send it") }
                }

                previousShape is SealedShape && nextShape is SealedShape -> {
                    if (previousShape.discriminator != nextShape.discriminator) {
                        breaking(path, "class discriminator changed from ${previousShape.discriminator} to ${nextShape.discriminator}")
                    }
                    (nextShape.subtypes - previousShape.subtypes).sorted().forEach { breaking(path, "sealed subtype $it added; an older peer cannot decode it") }
                    (previousShape.subtypes - nextShape.subtypes).sorted().forEach { breaking(path, "sealed subtype $it removed; an older peer may still send it") }
                    (previousShape.subtypes intersect nextShape.subtypes).sorted().forEach { compareShapes(it, it, "$path<$it>") }
                }

                else -> breaking(path, "changed from ${previousShape.kindName} to ${nextShape.kindName}")
            }
        }

        private fun compareFields(previousShape: ClassShape, nextShape: ClassShape, path: String) {
            (previousShape.fields.keys + nextShape.fields.keys).sorted().forEach { name ->
                val previousField = previousShape.fields[name]
                val nextField = nextShape.fields[name]
                val fieldPath = "$path.$name"
                when {
                    nextField == null && previousField!!.isOptional -> additive(fieldPath, "optional field removed; an older peer falls back to its default")

                    nextField == null -> breaking(fieldPath, "required field removed; an older peer still requires it")

                    previousField == null && nextField.isOptional -> additive(fieldPath, "optional field added; an older peer ignores it")

                    previousField == null -> breaking(fieldPath, "required field added; an older peer does not send it")

                    else -> {
                        when {
                            previousField.isOptional && !nextField.isOptional -> breaking(fieldPath, "became required; an older peer may leave it out")

                            !previousField.isOptional && nextField.isOptional && nextField.isOmittedAtDefault ->
                                breaking(fieldPath, "became optional but is omitted at its default; an older peer requires it")

                            !previousField.isOptional && nextField.isOptional -> additive(fieldPath, "became optional; it is still sent at its default")

                            previousField.isOmittedAtDefault != nextField.isOmittedAtDefault ->
                                additive(fieldPath, if (nextField.isOmittedAtDefault) "now omitted at its default" else "now sent at its default")
                        }
                        if (previousField.alsoReadAs != nextField.alsoReadAs) {
                            additive(fieldPath, "also read as ${nextField.alsoReadAs.sorted().ifEmpty { listOf("nothing else") }.joinToString(", ")}")
                        }
                        compareTypes(previousField.type, nextField.type, fieldPath)
                    }
                }
            }
        }

        private fun compareTypes(previousType: WireTypeRef, nextType: WireTypeRef, path: String) {
            when {
                previousType.isNullable != nextType.isNullable -> breaking(path, "nullability changed from $previousType to $nextType")

                previousType.isShape && nextType.isShape -> compareShapes(previousType.head, nextType.head, path)

                previousType.head != nextType.head || previousType.arguments.size != nextType.arguments.size ->
                    breaking(path, "type changed from $previousType to $nextType")

                else -> previousType.arguments.zip(nextType.arguments).forEachIndexed { index, (previousArgument, nextArgument) ->
                    val argumentPath = when {
                        previousType.head != "map" -> "$path[]"
                        index == 0 -> "$path{key}"
                        else -> "$path{value}"
                    }
                    compareTypes(previousArgument, nextArgument, argumentPath)
                }
            }
        }

        private fun breaking(path: String, description: String) {
            changes += WireSchemaChange(isBreaking = true, path, description)
        }

        private fun additive(path: String, description: String) {
            changes += WireSchemaChange(isBreaking = false, path, description)
        }

        private val WireShape.kindName: String
            get() = when (this) {
                is ClassShape -> "class"
                is ObjectShape -> "object"
                is EnumShape -> "enum"
                is SealedShape -> "sealed"
            }
    }
}
