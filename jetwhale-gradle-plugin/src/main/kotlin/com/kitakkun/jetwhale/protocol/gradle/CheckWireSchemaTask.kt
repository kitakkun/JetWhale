package com.kitakkun.jetwhale.protocol.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Fails when `wire/<project>.wire` is not the wire schema of the compiled classes at the declared
 * protocol revision. Given the dump of a baseline, such as the base branch's, it also fails when the
 * committed dump could not have followed it under [ProtocolRevisionRule], which catches a dump edited
 * by hand.
 */
@DisableCachingByDefault(because = "A verification with no output: comparing two small files costs less than a cache lookup.")
internal abstract class CheckWireSchemaTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val extractedWireSchemaFile: RegularFileProperty

    /** `wire/<project>.wire`, which may not exist yet. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val committedWireSchemaFile: ConfigurableFileCollection

    /** The baseline's dump of this module, which may not exist when the module is new. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val baselineWireSchemaFile: ConfigurableFileCollection

    @TaskAction
    fun checkWireSchema() {
        val extracted = WireSchema.parse(extractedWireSchemaFile.get().asFile.readText())
        val committedFile = committedWireSchemaFile.files.singleOrNull { it.isFile }
            ?: throw GradleException("This module has no wire schema dump. Run dumpWireSchema and commit the wire directory.")
        val committed = WireSchema.parse(committedFile.readText())

        if (committed != extracted) {
            throw GradleException(
                buildString {
                    append("${committedFile.name} is not the wire schema of the compiled classes at protocol revision ${extracted.revision}.")
                    if (committed.revision != extracted.revision) {
                        append(" It records protocol revision ${committed.revision}.")
                    }
                    appendLine()
                    val changes = WireSchemaClassifier(committed, extracted).classifyChanges()
                    if (changes.isEmpty() && committed.revision == extracted.revision) {
                        appendLine("  No change reaches the wire: a type the wire does not name was renamed.")
                    }
                    changes.forEach { appendLine("  $it") }
                    val refusal = ProtocolRevisionRule.refusalOf(committed, extracted)
                    if (refusal != null) append(refusal) else append("Run dumpWireSchema and commit the result.")
                },
            )
        }

        val baselineFile = baselineWireSchemaFile.files.singleOrNull { it.isFile } ?: return
        ProtocolRevisionRule.refusalOf(WireSchema.parse(baselineFile.readText()), committed)?.let { refusal ->
            throw GradleException("${committedFile.name} cannot follow the baseline's dump at ${baselineFile.path}. $refusal")
        }
    }
}
