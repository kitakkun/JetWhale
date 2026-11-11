package com.kitakkun.jetwhale.host.sdk

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalJetWhaleApi::class, InternalJetWhaleHostApi::class)
class JetWhaleMcpCommandTest {
    @Test
    fun `a marked block outside the host runs its work and returns the work's result`() = runBlocking {
        val command = MarkingCommand(work = { "done" })

        assertEquals("done", command.execute(JetWhaleMcpArguments(JsonObject(emptyMap()))))
    }

    @Test
    fun `a marked block reports its targets as started before its work and as ended after it`() = runBlocking {
        val reporter = RecordingReporter()
        val command = MarkingCommand(
            work = {
                reporter.reports.add("work")
                "done"
            },
        )

        withContext(reporter) { command.execute(JetWhaleMcpArguments(JsonObject(emptyMap()))) }

        assertEquals(listOf("started [Screen, Button(id=home)]", "work", "ended [Screen, Button(id=home)]"), reporter.reports)
    }

    @Test
    fun `a marked block reports its targets as ended when its work throws`() = runBlocking {
        val reporter = RecordingReporter()
        val command = MarkingCommand(work = { throw JetWhaleMcpArgumentException("no such button") })

        assertFailsWith<JetWhaleMcpArgumentException> {
            withContext(reporter) { command.execute(JetWhaleMcpArguments(JsonObject(emptyMap()))) }
        }

        assertEquals(listOf("started [Screen, Button(id=home)]", "ended [Screen, Button(id=home)]"), reporter.reports)
    }

    private sealed interface TestUiTarget {
        data object Screen : TestUiTarget

        data class Button(val id: String) : TestUiTarget
    }

    private class MarkingCommand(private val work: suspend () -> String) : JetWhaleMcpCommand() {
        override val name = "com.example.mark"
        override val description = "Marks the screen and a button while it works."

        override suspend fun execute(arguments: JetWhaleMcpArguments): String = markingAiOperationOn(TestUiTarget.Screen, TestUiTarget.Button("home")) { work() }
    }

    private class RecordingReporter : AiOperatedUiTargetsReporter() {
        val reports = mutableListOf<String>()

        override fun reportOperationStarted(targets: Set<Any>) {
            reports.add("started $targets")
        }

        override fun reportOperationEnded(targets: Set<Any>) {
            reports.add("ended $targets")
        }
    }
}
