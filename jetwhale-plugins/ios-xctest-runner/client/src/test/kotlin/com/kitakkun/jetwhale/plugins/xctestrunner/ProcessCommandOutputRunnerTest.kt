package com.kitakkun.jetwhale.plugins.xctestrunner

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CompletableFuture
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
    fun `a command whose output a child keeps open still fails at its timeout`() = runTest {
        val process = ProcessWithInheritedOutput()

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

/**
 * A process whose output pipe stays open after it is ended, as when a child process it started
 * inherited the pipe and is still running.
 */
private class ProcessWithInheritedOutput : Process() {
    private val exitFuture = CompletableFuture<Process>()

    private val output = PipedInputStream(PipedOutputStream())

    var destroyed = false
        private set

    override fun pid(): Long = 1

    override fun onExit(): CompletableFuture<Process> = exitFuture

    override fun isAlive(): Boolean = !destroyed

    override fun getInputStream(): InputStream = output

    override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))

    override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()

    override fun waitFor(): Int = error("waits on onExit instead")

    override fun exitValue(): Int = if (destroyed) 137 else throw IllegalThreadStateException("still running")

    override fun destroy() {
        destroyed = true
        exitFuture.complete(this)
    }

    override fun destroyForcibly(): Process = apply { destroy() }
}
