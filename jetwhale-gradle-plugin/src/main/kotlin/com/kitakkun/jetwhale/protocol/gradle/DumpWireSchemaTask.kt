package com.kitakkun.jetwhale.protocol.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Writes the wire schema of the compiled classes to `wire/<project>.wire`, unless
 * [ProtocolRevisionRule] refuses it after the dump already there.
 */
internal abstract class DumpWireSchemaTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val extractedWireSchema: RegularFileProperty

    @get:OutputFile
    abstract val committedWireSchema: RegularFileProperty

    @TaskAction
    fun dumpWireSchema() {
        val committedWireSchemaFile = committedWireSchema.get().asFile
        val extracted = WireSchema.parse(extractedWireSchema.get().asFile.readText())
        val committed = committedWireSchemaFile.takeIf { it.isFile }?.let { WireSchema.parse(it.readText()) }
        ProtocolRevisionRule.refusalOf(committed, extracted)?.let { refusal ->
            throw GradleException("Not writing ${committedWireSchemaFile.name}. $refusal")
        }
        if (committed != null) {
            val changes = WireSchemaClassifier(committed, extracted).classifyChanges()
            if (changes.isNotEmpty()) {
                logger.lifecycle(
                    buildString {
                        appendLine("Wire schema changes since the dump at protocol revision ${committed.revision}:")
                        changes.forEach { appendLine("  $it") }
                        append(ProtocolRevisionRule.UNSEEN_CHANGES_NOTE)
                    },
                )
            }
        }
        committedWireSchemaFile.parentFile.mkdirs()
        committedWireSchemaFile.writeText(extracted.render())
    }
}
