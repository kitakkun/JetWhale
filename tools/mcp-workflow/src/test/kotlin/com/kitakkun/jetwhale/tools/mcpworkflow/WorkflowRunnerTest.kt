package com.kitakkun.jetwhale.tools.mcpworkflow

import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Files
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

// currentTime is how these tests check that waiting took virtual, not real, time.
@OptIn(ExperimentalCoroutinesApi::class)
class WorkflowRunnerTest {
    @Test
    fun `a saved value is passed to a later step with its type`() = runTest {
        val caller = FakeToolCaller { _, tool, arguments ->
            when (tool) {
                "create" -> textResult("""{"id":1234}""", isError = false)
                else -> textResult("""{"echo":${arguments["id"]}}""", isError = false)
            }
        }

        val outcome = runner(caller, setOf("app")).run(
            workflow(
                Step(call = "create", save = mapOf("id" to "$.id")),
                Step(call = "get", args = buildJsonObject { put("id", "\${id}") }),
                outputs = mapOf("id" to JsonPrimitive("\${id}")),
            ),
            inputs = emptyMap(),
        )

        assertTrue(outcome.passed)
        assertEquals(JsonPrimitive(1234), caller.calls[1].third["id"])
        assertEquals(JsonPrimitive(1234), outcome.outputs["id"])
    }

    @Test
    fun `a failed expectation stops the run and skips the rest`() = runTest {
        val caller = FakeToolCaller { _, _, _ -> textResult("""{"screen":"Login"}""", isError = false) }

        val outcome = runner(caller, setOf("app")).run(
            workflow(
                Step(call = "look", expect = listOf(Expectation(path = "$.screen", equals = JsonPrimitive("Home")))),
                Step(call = "next"),
            ),
            inputs = emptyMap(),
        )

        assertFalse(outcome.passed)
        assertEquals(listOf(StepStatus.FAILED, StepStatus.SKIPPED), outcome.steps.map(StepOutcome::status))
        assertEquals(1, caller.calls.size)
    }

    @Test
    fun `continueOnFailure goes on to the next step`() = runTest {
        val caller = FakeToolCaller { _, _, _ -> textResult("{}", isError = true) }

        val outcome = runner(caller, setOf("app")).run(workflow(Step(call = "a", continueOnFailure = true), Step(call = "b")), inputs = emptyMap())

        assertEquals(listOf(StepStatus.FAILED, StepStatus.FAILED), outcome.steps.map(StepOutcome::status))
    }

    @Test
    fun `a tool error passes when the step expects one`() = runTest {
        val caller = FakeToolCaller { _, _, _ -> textResult("""{"error":"denied"}""", isError = true) }

        val outcome = runner(caller, setOf("app")).run(workflow(Step(call = "a", expect = listOf(Expectation(error = true)))), inputs = emptyMap())

        assertTrue(outcome.passed)
    }

    @Test
    fun `wait polls until the condition holds`() = runTest {
        var polls = 0
        val caller = FakeToolCaller { _, _, _ -> textResult("""{"matches":${if (++polls >= 4) 1 else 0}}""", isError = false) }

        val outcome = runner(caller, setOf("app")).run(
            workflow(Step(call = "find", wait = WaitSpec(timeout = "5s", interval = "500ms"), expect = listOf(Expectation(path = "$.matches", gte = 1.0)))),
            inputs = emptyMap(),
        )

        assertTrue(outcome.passed)
        assertEquals(4, outcome.steps.single().attempts)
        assertEquals(1500, testScheduler.currentTime)
    }

    @Test
    fun `wait gives up at its timeout`() = runTest {
        val caller = FakeToolCaller { _, _, _ -> textResult("""{"matches":0}""", isError = false) }

        val outcome = runner(caller, setOf("app")).run(
            workflow(Step(call = "find", wait = WaitSpec(timeout = "2s", interval = "500ms"), expect = listOf(Expectation(path = "$.matches", gte = 1.0)))),
            inputs = emptyMap(),
        )

        assertFalse(outcome.passed)
        assertEquals(4, outcome.steps.single().attempts)
        assertTrue(testScheduler.currentTime < 2000)
    }

    @Test
    fun `default expectations apply to every step except one expecting an error`() = runTest {
        val caller = FakeToolCaller { _, tool, _ ->
            when (tool) {
                "mistaken" -> textResult("""{"error":"invalid action"}""", isError = false)
                else -> textResult("""{"error":"denied"}""", isError = true)
            }
        }
        val flow = Workflow(
            version = WORKFLOW_FORMAT_VERSION,
            name = "Defaults",
            defaults = StepDefaults(expect = listOf(Expectation(path = "$.error", exists = false))),
            steps = listOf(Step(call = "refused", expect = listOf(Expectation(error = true))), Step(call = "mistaken")),
        )

        val outcome = runner(caller, setOf("app")).run(flow, inputs = emptyMap())

        assertEquals(listOf(StepStatus.PASSED, StepStatus.FAILED), outcome.steps.map(StepOutcome::status))
    }

    @Test
    fun `retries repeat a failed call without waiting`() = runTest {
        var calls = 0
        val caller = FakeToolCaller { _, _, _ -> textResult("{}", isError = ++calls < 3) }

        val outcome = runner(caller, setOf("app")).run(workflow(Step(call = "flaky", retries = 2)), inputs = emptyMap())

        assertTrue(outcome.passed)
        assertEquals(3, outcome.steps.single().attempts)
    }

    @Test
    fun `a call past its timeout fails the step`() = runTest {
        val caller = FakeToolCaller { _, _, _ ->
            delay(1.minutes)
            textResult("{}", isError = false)
        }

        val outcome = runner(caller, setOf("app")).run(workflow(Step(call = "slow", timeout = "2s")), inputs = emptyMap())

        assertEquals("no result within 2s", outcome.steps.single().message)
    }

    @Test
    fun `an undefined variable fails at once instead of being retried`() = runTest {
        val caller = FakeToolCaller { _, _, _ -> textResult("{}", isError = false) }

        val outcome = runner(caller, setOf("app")).run(
            workflow(Step(call = "a", retries = 5, args = buildJsonObject { put("id", "\${nope}") })),
            inputs = emptyMap(),
        )

        assertEquals(1, outcome.steps.single().attempts)
        assertTrue(caller.calls.isEmpty())
    }

    @Test
    fun `reconnect opens a new connection before the step`() = runTest {
        val caller = FakeToolCaller { _, _, _ -> textResult("{}", isError = false) }

        runner(caller, setOf("app")).run(workflow(Step(call = "enable"), Step(call = "useNewTool", reconnect = true)), inputs = emptyMap())

        assertEquals(listOf("enable", RECONNECT, "useNewTool"), caller.calls.map { it.second })
    }

    @Test
    fun `a step goes to the server it names`() = runTest {
        val caller = FakeToolCaller { _, _, _ -> textResult("{}", isError = false) }

        runner(caller, setOf("app", "api")).run(workflow(Step(call = "seed", server = "api"), Step(call = "open", server = "app")), inputs = emptyMap())

        assertEquals(listOf("api", "app"), caller.calls.map { it.first })
    }

    @Test
    fun `inputs take their defaults and unknown inputs are refused`() = runTest {
        val caller = FakeToolCaller { _, _, arguments -> textResult(JsonObject(arguments).toString(), isError = false) }
        val flow = workflow(
            Step(call = "login", args = buildJsonObject { put("email", "\${email}") }),
            inputs = mapOf("email" to InputSpec(default = JsonPrimitive("qa@example.com"))),
        )

        runner(caller, setOf("app")).run(flow, inputs = emptyMap())

        assertEquals(JsonPrimitive("qa@example.com"), caller.calls.single().third["email"])
        val failure = runCatching { runner(caller, setOf("app")).run(flow, inputs = mapOf("mail" to JsonPrimitive("x"))) }.exceptionOrNull()
        assertTrue(failure is WorkflowFormatException)
    }

    @Test
    fun `an image in a result is written as an artifact`() = runTest {
        val directory = Files.createTempDirectory("mcp-workflow-artifacts").toFile()
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val caller = FakeToolCaller { _, _, _ ->
            CallToolResult(content = listOf(TextContent("{}"), ImageContent(data = Base64.encode(png), mimeType = "image/png")))
        }
        val runner = WorkflowRunner(caller, setOf("app"), testScheduler.timeSource, emptyMap(), directory) {}

        val outcome = runner.run(workflow(Step(id = "shot", call = "screenshot")), inputs = emptyMap())

        val artifact = outcome.steps.single().artifacts.single()
        assertEquals("01-shot-1.png", artifact.name)
        assertTrue(png.contentEquals(artifact.readBytes()))
        directory.deleteRecursively()
    }

    private fun TestScope.runner(caller: ToolCaller, servers: Set<String>) = WorkflowRunner(caller, servers, testScheduler.timeSource, emptyMap(), artifactDirectory = null) {}

    private fun workflow(vararg steps: Step, inputs: Map<String, InputSpec> = emptyMap(), outputs: Map<String, JsonElement> = emptyMap()) = Workflow(version = WORKFLOW_FORMAT_VERSION, name = "Test", inputs = inputs, steps = steps.toList(), outputs = outputs)
}
