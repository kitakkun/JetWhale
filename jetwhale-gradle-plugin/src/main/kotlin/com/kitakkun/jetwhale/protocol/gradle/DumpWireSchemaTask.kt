package com.kitakkun.jetwhale.protocol.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Writes the wire schema of the compiled classes to `wire/<project>.wire`, unless
 * [ProtocolRevisionRule] refuses it after the dump already there.
 */
@DisableCachingByDefault(because = "Its output is a committed file it reads before writing, which a cache entry would overwrite unchecked.")
internal abstract class DumpWireSchemaTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val extractedWireSchemaFile: RegularFileProperty

    @get:OutputFile
    abstract val committedWireSchemaFile: RegularFileProperty

    @TaskAction
    fun dumpWireSchema() {
        val committedWireSchemaFile = committedWireSchemaFile.get().asFile
        val extracted = WireSchema.parse(extractedWireSchemaFile.get().asFile.readText())
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
