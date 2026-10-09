package com.kitakkun.jetwhale.plugins.xctestrunner

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class ProcessCommandOutputRunnerTest {
    @Test
    fun `a command that exits hands back what it printed and how it exited`() = runTest {
        val process = FakeToolProcess(listOf("xcodebuild"), pid = 1, output = "Build version 27A266a\n", exitsAtOnce = true)

        val output = ProcessCommandOutputRunner(processLauncher = { process }, timeout = 1.minutes).run(listOf("xcrun", "xcodebuild", "-version"))

        assertEquals(0, output.exitCode)
        assertTrue("Build version 27A266a" in output.text)
    }

    @Test
    fun `a command still running after the timeout is ended, and its start fails`() = runTest {
        val process = FakeToolProcess(listOf("xcodebuild"), pid = 1, output = "Building\n", exitsAtOnce = false)

        assertFailsWith<XcTestRunnerStartException> {
            ProcessCommandOutputRunner(processLauncher = { process }, timeout = 100.milliseconds).run(listOf("xcrun", "xcodebuild", "build-for-testing"))
        }

        assertTrue(process.destroyed)
    }

    @Test
    fun `a command whose caller is cancelled is ended`() = runTest {
        val process = FakeToolProcess(listOf("xcodebuild"), pid = 1, output = "Building\n", exitsAtOnce = false)
        val launched = CompletableDeferred<Unit>()
        val runner = ProcessCommandOutputRunner(
            processLauncher = {
                launched.complete(Unit)
                process
            },
            timeout = 1.minutes,
        )

        val running = launch { runner.run(listOf("xcrun", "xcodebuild", "build-for-testing")) }
        launched.await()
        running.cancelAndJoin()

        assertTrue(process.destroyed)
    }
}
