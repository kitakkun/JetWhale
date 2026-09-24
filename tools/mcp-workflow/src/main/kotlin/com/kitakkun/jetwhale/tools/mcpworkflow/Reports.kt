package com.kitakkun.jetwhale.tools.mcpworkflow

import kotlinx.serialization.json.JsonElement
import java.io.File
import kotlin.time.DurationUnit

internal fun consoleLine(step: StepOutcome): String {
    val mark = when (step.status) {
        StepStatus.PASSED -> "✓"
        StepStatus.FAILED -> "✗"
        StepStatus.SKIPPED -> "-"
    }
    val attempts = if (step.attempts > 1) " after ${step.attempts} attempts" else ""
    val detail = step.message?.let { "\n    $it" }.orEmpty()
    return "$mark ${step.label} (${step.duration.inWholeMilliseconds} ms$attempts)$detail"
}

internal fun consoleSummary(run: RunOutcome): String {
    val counts = StepStatus.entries.joinToString { status -> "${run.steps.count { it.status == status }} ${status.name.lowercase()}" }
    val verdict = if (run.passed) "PASSED" else "FAILED"
    val outputs = run.outputs.entries.joinToString("") { (name, value) -> "\n  $name = $value" }
    return "${run.workflow}: $verdict ($counts) in ${run.duration.inWholeMilliseconds} ms$outputs"
}

/** One `<testsuite>` per workflow, one `<testcase>` per step, as CI test-report parsers expect. */
fun writeJUnitReport(runs: List<RunOutcome>, file: File) {
    file.parentFile?.mkdirs()
    file.writeText(
        buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("<testsuites>")
            runs.forEach { run ->
                val failures = run.steps.count { it.status == StepStatus.FAILED }
                val skipped = run.steps.count { it.status == StepStatus.SKIPPED }
                appendLine(
                    """  <testsuite name="${run.workflow.xml()}" tests="${run.steps.size}" failures="$failures" skipped="$skipped" """ +
                        """time="${run.duration.toDouble(DurationUnit.SECONDS)}">""",
                )
                run.steps.forEach { step ->
                    append("""    <testcase classname="${run.workflow.xml()}" name="${step.label.xml()}" time="${step.duration.toDouble(DurationUnit.SECONDS)}"""")
                    when (step.status) {
                        StepStatus.PASSED -> appendLine("/>")

                        StepStatus.FAILED -> {
                            appendLine(">")
                            appendLine("""      <failure message="${step.message.orEmpty().xml()}">${step.failureDetail().xml()}</failure>""")
                            appendLine("    </testcase>")
                        }

                        StepStatus.SKIPPED -> {
                            appendLine(">")
                            appendLine("""      <skipped message="${step.message.orEmpty().xml()}"/>""")
                            appendLine("    </testcase>")
                        }
                    }
                }
                appendLine("  </testsuite>")
            }
            appendLine("</testsuites>")
        },
    )
}

/** A readable report with each step's arguments, result and images, for a person going through a failed run. */
fun writeMarkdownReport(runs: List<RunOutcome>, file: File) {
    file.parentFile?.mkdirs()
    file.writeText(
        buildString {
            runs.forEach { run ->
                appendLine("# ${run.workflow} — ${if (run.passed) "passed" else "failed"}")
                appendLine()
                appendLine("| # | Step | Result | Time |")
                appendLine("|---|------|--------|------|")
                run.steps.forEach { step ->
                    appendLine("| ${step.index + 1} | ${step.label.md()} | ${step.status.name.lowercase()} | ${step.duration.inWholeMilliseconds} ms |")
                }
                appendLine()
                run.steps.filter { it.status != StepStatus.SKIPPED }.forEach { step ->
                    appendLine("## ${step.index + 1}. ${step.label}")
                    appendLine()
                    appendLine("`${step.server}` → `${step.tool}`")
                    step.message?.let { appendLine("\n**${it.md()}**") }
                    step.arguments?.let { appendLine("\nArguments:\n\n```json\n${it.pretty()}\n```") }
                    step.result?.let { appendLine("\nResult:\n\n```json\n${it.pretty().take(MARKDOWN_RESULT_LIMIT)}\n```") }
                    step.artifacts.forEach { artifact -> appendLine("\n![${artifact.name}](${artifact.relativeTo(file.parentFile ?: File(".")).path})") }
                    appendLine()
                }
                if (run.outputs.isNotEmpty()) {
                    appendLine("## Outputs\n")
                    run.outputs.forEach { (name, value) -> appendLine("- `$name`: `$value`") }
                    appendLine()
                }
            }
        },
    )
}

private const val MARKDOWN_RESULT_LIMIT = 4000

private fun StepOutcome.failureDetail(): String = buildString {
    appendLine("$server: $tool")
    arguments?.let { appendLine("arguments: $it") }
    result?.let { appendLine("result: ${it.render()}") }
}

private fun JsonElement.pretty(): String = WorkflowJson.encodeToString(JsonElement.serializer(), this)

private fun String.xml(): String = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

private fun String.md(): String = replace("|", "\\|")
