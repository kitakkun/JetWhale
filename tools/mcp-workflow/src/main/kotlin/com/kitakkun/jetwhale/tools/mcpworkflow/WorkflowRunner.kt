package com.kitakkun.jetwhale.tools.mcpworkflow

import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import kotlin.io.encoding.Base64
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Longest a call may take when its step names no `timeout`: long enough for a slow UI action. */
private val DEFAULT_STEP_TIMEOUT: Duration = 30.seconds

enum class StepStatus { PASSED, FAILED, SKIPPED }

/**
 * What one step did. [result] is the result document of the last attempt, kept for reports and for
 * the recorder's value tracking.
 */
data class StepOutcome(
    val index: Int,
    val label: String,
    val server: String,
    val tool: String,
    val status: StepStatus,
    val message: String?,
    val attempts: Int,
    val duration: Duration,
    val arguments: JsonObject?,
    val result: JsonElement?,
    val artifacts: List<File>,
)

data class RunOutcome(
    val workflow: String,
    val steps: List<StepOutcome>,
    val outputs: Map<String, JsonElement>,
    val duration: Duration,
) {
    val passed: Boolean get() = steps.none { it.status == StepStatus.FAILED }
}

/**
 * Runs workflows against [caller].
 *
 * @param servers Names of the servers the workflow may call; a step without `server` goes to the only
 *   one when there is exactly one.
 * @param artifactDirectory Where images returned by tools are written; null keeps none.
 * @param onStep Told about each step as it finishes, for live progress.
 */
class WorkflowRunner(
    private val caller: ToolCaller,
    private val servers: Set<String>,
    private val timeSource: TimeSource,
    private val environment: Map<String, String>,
    private val artifactDirectory: File?,
    private val onStep: (StepOutcome) -> Unit,
) {
    suspend fun run(workflow: Workflow, inputs: Map<String, JsonElement>): RunOutcome {
        val started = timeSource.markNow()
        val variables = initialVariables(workflow, inputs)
        val outcomes = mutableListOf<StepOutcome>()
        var stopped = false
        workflow.steps.forEachIndexed { index, step ->
            val outcome = if (stopped) skipped(index, step) else runStep(index, step, variables)
            outcomes += outcome
            onStep(outcome)
            if (outcome.status == StepStatus.FAILED && !step.continueOnFailure) stopped = true
        }
        val outputs = workflow.outputs.mapValues { (_, template) ->
            try {
                renderTemplate(template, variables, environment)
            } catch (e: TemplateException) {
                JsonPrimitive("<unavailable: ${e.message}>")
            }
        }
        return RunOutcome(workflow.name, outcomes, outputs, started.elapsedNow())
    }

    private fun initialVariables(workflow: Workflow, inputs: Map<String, JsonElement>): MutableMap<String, JsonElement> {
        val unknown = inputs.keys - workflow.inputs.keys
        if (unknown.isNotEmpty()) throw WorkflowFormatException("unknown inputs: ${unknown.joinToString()}; declared: ${workflow.inputs.keys.joinToString().ifEmpty { "none" }}")
        val variables = mutableMapOf<String, JsonElement>()
        workflow.inputs.forEach { (name, spec) ->
            variables[name] = inputs[name] ?: spec.default ?: throw WorkflowFormatException("input '$name' is required")
        }
        workflow.vars.forEach { (name, template) -> variables[name] = renderTemplate(template, variables, environment) }
        return variables
    }

    private suspend fun runStep(index: Int, step: Step, variables: MutableMap<String, JsonElement>): StepOutcome {
        val started = timeSource.markNow()
        val server = step.server ?: servers.singleOrNull() ?: ""
        val (last, attempts) = attemptUntilSettled(step, server, variables, started)
        if (last.failure == null) variables.putAll(last.saved)
        return StepOutcome(
            index = index,
            label = stepLabel(index, step),
            server = server,
            tool = step.call,
            status = if (last.failure == null) StepStatus.PASSED else StepStatus.FAILED,
            message = last.failure,
            attempts = attempts,
            duration = started.elapsedNow(),
            arguments = last.arguments,
            result = last.document,
            artifacts = last.result?.let { writeImages(index, step, it) }.orEmpty(),
        )
    }

    /**
     * Calls until the step passes, fails in a way repetition cannot fix, or runs out of `wait` time
     * and `retries`. Returns the last attempt and how many were made.
     */
    private suspend fun attemptUntilSettled(
        step: Step,
        server: String,
        variables: Map<String, JsonElement>,
        started: TimeMark,
    ): Pair<Attempt, Int> {
        var attempts = 0
        while (true) {
            attempts++
            val last = attempt(step, server, variables)
            if (last.failure == null || last.fatal) return last to attempts
            val pause = step.wait?.let { nextPause(it, started) }
            when {
                pause != null -> delay(pause)
                attempts > step.retries -> return last to attempts
            }
        }
    }

    /** How long to wait before polling again, or null when another poll would end past the wait's timeout. */
    private fun nextPause(wait: WaitSpec, started: TimeMark): Duration? {
        val interval = Duration.parse(wait.interval)
        return interval.takeIf { started.elapsedNow() + interval < Duration.parse(wait.timeout) }
    }

    /** @property fatal A failure no repetition can fix, such as a reference to an undefined variable. */
    private class Attempt(
        val arguments: JsonObject?,
        val result: CallToolResult?,
        val document: JsonElement?,
        val saved: Map<String, JsonElement>,
        val failure: String?,
        val fatal: Boolean,
    )

    private suspend fun attempt(step: Step, server: String, variables: Map<String, JsonElement>): Attempt {
        // Expected values are templated like arguments, so a step can check the value it just sent.
        val (arguments, expectations) = try {
            renderTemplate(step.args, variables, environment) as JsonObject to step.expect.map { it.rendered(variables) }
        } catch (e: TemplateException) {
            return Attempt(null, null, null, emptyMap(), e.message, fatal = true)
        }
        if (server.isEmpty()) {
            return Attempt(arguments, null, null, emptyMap(), "the step names no `server` and the workflow has ${servers.size} servers", fatal = true)
        }
        val timeout = step.timeout?.let(Duration::parse) ?: DEFAULT_STEP_TIMEOUT
        val result = try {
            withTimeout(timeout) { caller.callTool(server, step.call, arguments) }
        } catch (_: TimeoutCancellationException) {
            return Attempt(arguments, null, null, emptyMap(), "no result within $timeout", fatal = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: UnknownServerException) {
            return Attempt(arguments, null, null, emptyMap(), e.message, fatal = true)
        } catch (e: Exception) {
            return Attempt(arguments, null, null, emptyMap(), "call failed: ${e.message ?: e::class.simpleName}", fatal = false)
        }
        val document = resultDocument(result)
        val isError = result.isError == true
        val failure = evaluate(expectations, document, isError)
        if (failure != null) return Attempt(arguments, result, document, emptyMap(), failure, fatal = false)
        val saved = mutableMapOf<String, JsonElement>()
        step.save.forEach { (name, path) ->
            saved[name] = JsonPath.parse(path).select(document)
                ?: return Attempt(arguments, result, document, emptyMap(), "save '$name': $path matched nothing in ${document.render()}", fatal = false)
        }
        return Attempt(arguments, result, document, saved, null, fatal = false)
    }

    private fun evaluate(expectations: List<Expectation>, document: JsonElement, isError: Boolean): String? {
        if (isError && expectations.none { it.error != null }) return "the tool reported an error: ${document.render()}"
        return expectations.firstNotNullOfOrNull { failureOf(it, document, isError) }
    }

    private fun Expectation.rendered(variables: Map<String, JsonElement>): Expectation = copy(
        equals = equals?.let { renderTemplate(it, variables, environment) },
        notEquals = notEquals?.let { renderTemplate(it, variables, environment) },
        contains = contains?.let { renderTemplate(it, variables, environment) },
        matches = matches?.let { (renderTemplate(JsonPrimitive(it), variables, environment) as JsonPrimitive).content },
    )

    private fun writeImages(index: Int, step: Step, result: CallToolResult): List<File> {
        val directory = artifactDirectory ?: return emptyList()
        return resultImages(result).mapIndexed { imageIndex, image ->
            val extension = image.mimeType.substringAfter('/').substringBefore('+').ifEmpty { "img" }
            val name = "%02d-%s-%d.%s".format(index + 1, stepSlug(step), imageIndex + 1, extension)
            File(directory, name).apply {
                parentFile.mkdirs()
                writeBytes(Base64.decode(image.data))
            }
        }
    }

    private fun skipped(index: Int, step: Step) = StepOutcome(
        index = index,
        label = stepLabel(index, step),
        server = step.server ?: servers.singleOrNull().orEmpty(),
        tool = step.call,
        status = StepStatus.SKIPPED,
        message = "an earlier step failed",
        attempts = 0,
        duration = Duration.ZERO,
        arguments = null,
        result = null,
        artifacts = emptyList(),
    )
}

internal fun stepLabel(index: Int, step: Step): String = step.name ?: step.id ?: "${index + 1}. ${step.call}"

private fun stepSlug(step: Step): String = (step.id ?: step.call.substringAfterLast('.')).replace(Regex("[^A-Za-z0-9_-]"), "_")
