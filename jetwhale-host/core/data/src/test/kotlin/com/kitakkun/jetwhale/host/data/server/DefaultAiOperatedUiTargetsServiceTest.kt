package com.kitakkun.jetwhale.host.data.server

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.model.AI_OPERATION_INDICATOR_LINGER
import com.kitakkun.jetwhale.host.sdk.AiOperatedUiTargetsReporter
import com.kitakkun.jetwhale.host.sdk.InternalJetWhaleHostApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalJetWhaleApi::class, InternalJetWhaleHostApi::class)
class DefaultAiOperatedUiTargetsServiceTest {
    @Test
    fun `a target is operated from the start of its block until the hold after its end`() = runTest {
        val service = newService()
        val emissions = collectOperatedUiTargets(service)

        launchMarkedWork(service.reporterFor(PLUGIN_ID, SESSION_ID), listOf(TestUiTarget.Screen)) { delay(1_000) }
        runCurrent()
        assertEquals(setOf<Any>(TestUiTarget.Screen), emissions.last())

        advanceTimeBy(1_000 + LINGER_MILLIS - 1)
        assertEquals(setOf<Any>(TestUiTarget.Screen), emissions.last())

        advanceTimeBy(2)
        assertEquals(emptySet(), emissions.last())
    }

    @Test
    fun `a target stays operated while any of its overlapping blocks runs, and for the hold after the last one ends`() = runTest {
        val service = newService()
        val emissions = collectOperatedUiTargets(service)
        val reporter = service.reporterFor(PLUGIN_ID, SESSION_ID)

        launchMarkedWork(reporter, listOf(TestUiTarget.Screen)) { delay(100) }
        advanceTimeBy(50)
        launchMarkedWork(reporter, listOf(TestUiTarget.Screen)) { delay(2_950) }

        advanceTimeBy(100 + LINGER_MILLIS + 100)
        assertEquals(setOf<Any>(TestUiTarget.Screen), emissions.last(), "the first block's hold ran out while the second still runs")

        advanceTimeBy(3_000 + LINGER_MILLIS - 1 - testScheduler.currentTime)
        assertEquals(setOf<Any>(TestUiTarget.Screen), emissions.last())

        advanceTimeBy(2)
        assertEquals(emptySet(), emissions.last())
    }

    @Test
    fun `separate targets drop when their own holds run out`() = runTest {
        val service = newService()
        val emissions = collectOperatedUiTargets(service)
        val reporter = service.reporterFor(PLUGIN_ID, SESSION_ID)

        launchMarkedWork(reporter, listOf(TestUiTarget.Screen)) { delay(1_000) }
        launchMarkedWork(reporter, listOf(TestUiTarget.Button("home"))) { delay(2_000) }
        runCurrent()
        assertEquals(setOf(TestUiTarget.Screen, TestUiTarget.Button("home")), emissions.last())

        advanceTimeBy(1_000 + LINGER_MILLIS + 1)
        assertEquals(setOf<Any>(TestUiTarget.Button("home")), emissions.last())

        advanceTimeBy(1_000)
        assertEquals(emptySet(), emissions.last())
    }

    @Test
    fun `a target operated in another plugin instance is not reported`() = runTest {
        val service = newService()
        val emissions = collectOperatedUiTargets(service)

        launchMarkedWork(service.reporterFor(PLUGIN_ID, "other-session"), listOf(TestUiTarget.Screen)) { delay(1_000) }
        launchMarkedWork(service.reporterFor("com.example.other", SESSION_ID), listOf(TestUiTarget.Screen)) { delay(1_000) }
        advanceTimeBy(500)

        assertEquals(listOf(emptySet()), emissions)
    }

    @Test
    fun `a target's hold starts when its block throws`() = runTest {
        val service = newService()
        val emissions = collectOperatedUiTargets(service)
        val reporter = service.reporterFor(PLUGIN_ID, SESSION_ID)

        launch {
            assertFailsWith<IllegalStateException> {
                withContext(reporter) {
                    MarkingCommand(
                        targets = listOf(TestUiTarget.Screen),
                        work = {
                            delay(1_000)
                            error("The device went away")
                        },
                    ).execute(NO_ARGUMENTS)
                }
            }
        }

        advanceTimeBy(1_000 + LINGER_MILLIS - 1)
        assertEquals(setOf<Any>(TestUiTarget.Screen), emissions.last())

        advanceTimeBy(2)
        assertEquals(emptySet(), emissions.last())
    }

    @Test
    fun `a target's hold starts when its block is cancelled`() = runTest {
        val service = newService()
        val emissions = collectOperatedUiTargets(service)

        val workJob = launchMarkedWork(service.reporterFor(PLUGIN_ID, SESSION_ID), listOf(TestUiTarget.Screen)) { delay(10_000) }
        advanceTimeBy(1_000)
        workJob.cancel()

        advanceTimeBy(LINGER_MILLIS - 1)
        assertEquals(setOf<Any>(TestUiTarget.Screen), emissions.last())

        advanceTimeBy(2)
        assertEquals(emptySet(), emissions.last())
    }

    private fun TestScope.newService() = DefaultAiOperatedUiTargetsService(
        clock = object : Clock {
            override fun now(): Instant = Instant.fromEpochMilliseconds(START_EPOCH_MILLIS + testScheduler.currentTime)
        },
    )

    private fun TestScope.collectOperatedUiTargets(service: DefaultAiOperatedUiTargetsService): List<Set<Any>> {
        val emissions = mutableListOf<Set<Any>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            service.operatedUiTargetsFlow(pluginId = PLUGIN_ID, sessionId = SESSION_ID).toList(emissions)
        }
        return emissions
    }

    /** Runs [work] in a command's block that marks [targets], the way the host runs a plugin's command. */
    private fun TestScope.launchMarkedWork(reporter: AiOperatedUiTargetsReporter, targets: List<Any>, work: suspend () -> Unit): Job = launch {
        withContext(reporter) { MarkingCommand(targets, work).execute(NO_ARGUMENTS) }
    }

    private sealed interface TestUiTarget {
        data object Screen : TestUiTarget

        data class Button(val id: String) : TestUiTarget
    }

    private class MarkingCommand(private val targets: List<Any>, private val work: suspend () -> Unit) : JetWhaleMcpCommand() {
        override val name = "com.example.mark"
        override val description = "Marks its targets while it works."

        override suspend fun execute(arguments: JetWhaleMcpArguments): String = markingAiOperationOn(*targets.toTypedArray()) {
            work()
            ""
        }
    }

    private companion object {
        const val PLUGIN_ID = "com.example.plugin"
        const val SESSION_ID = "session-1"
        const val START_EPOCH_MILLIS = 1_700_000_000_000L
        val LINGER_MILLIS = AI_OPERATION_INDICATOR_LINGER.inWholeMilliseconds

        val NO_ARGUMENTS = JetWhaleMcpArguments(JsonObject(emptyMap()))
    }
}
