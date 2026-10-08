package com.kitakkun.jetwhale.plugins.mirror.host

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ShellTest {
    @Test
    fun `an argument with a double quote is quoted with its quote and the backslashes before it escaped`() {
        assertEquals(""""say%s\\\"hi\\\""""", windowsCommandLineArgument("""say%s\"hi\""""))
        assertEquals(""""a\"b\\"""", windowsCommandLineArgument("""a"b\"""))
        assertEquals(""""\""""", windowsCommandLineArgument("\""))
    }

    @Test
    fun `an argument without a double quote is passed as it is`() {
        assertEquals("--time-limit", windowsCommandLineArgument("--time-limit"))
        assertEquals("""C:\Program Files\ffmpeg\bin\""", windowsCommandLineArgument("""C:\Program Files\ffmpeg\bin\"""))
    }

    @Test
    fun `a launched process receives every argument as it was given`() {
        val arguments = listOf("""say%s\"hi\"""", "plain", "with space", """a"b\""", """\\"""", """trailing\""", "")
        val classpath = listOf(EchoArguments::class.java, Unit::class.java)
            .joinToString(File.pathSeparator) { File(it.protectionDomain.codeSource.location.toURI()).path }
        val java = File(System.getProperty("java.home"), "bin/java").path

        val process = SystemProcessLauncher.start(listOf(java, "-cp", classpath, EchoArguments::class.java.name) + arguments)
        val received = process.inputStream.bufferedReader().readText()
        process.waitFor()

        assertEquals(arguments, received.split(EchoArguments.SEPARATOR))
    }

    @Test
    fun `a process is launched with launchedProcessPathVariable as its PATH`() {
        assumeShellScriptsLaunch()
        SystemProcessLauncher.launchedProcessPathVariable = "/opt/login/bin:/usr/bin:/bin"
        try {
            val process = SystemProcessLauncher.start(listOf("/bin/sh", "-c", "printf %s \"\$PATH\""))

            assertEquals("/opt/login/bin:/usr/bin:/bin", process.inputStream.bufferedReader().readText())
        } finally {
            SystemProcessLauncher.launchedProcessPathVariable = null
        }
    }

    @Test
    fun `a process is launched with the host's PATH while launchedProcessPathVariable is null`() {
        assumeShellScriptsLaunch()

        val process = SystemProcessLauncher.start(listOf("/bin/sh", "-c", "printf %s \"\$PATH\""))

        assertEquals(System.getenv("PATH"), process.inputStream.bufferedReader().readText())
    }

    @Test
    fun `a command that finishes gives its exit code and both of its outputs`() = runBlocking {
        assumeShellScriptsLaunch()

        val result = runCommand(10.seconds, "/bin/sh", "-c", "printf out; printf err >&2; exit 3")

        assertEquals(3, result.exitCode)
        assertEquals("out", result.stdoutText)
        assertEquals("err", result.stderr)
    }

    @Test
    fun `a command still running when its time is up is ended and fails`() = runBlocking {
        assumeShellScriptsLaunch()
        val childrenBefore = ProcessHandle.current().children().toList().toSet()

        val failure = assertFailsWith<DeviceControlException> { runCommand(500.milliseconds, "/bin/sh", "-c", "exec sleep 30") }

        assertContains(failure.message.orEmpty(), "did not finish within 500ms")
        assertChildProcessesEnded(childrenBefore)
    }

    @Test
    fun `a command whose caller is cancelled is ended`() = runBlocking {
        assumeShellScriptsLaunch()
        val childrenBefore = ProcessHandle.current().children().toList().toSet()

        assertFailsWith<TimeoutCancellationException> { withTimeout(1.seconds) { runCommand(1.minutes, "/bin/sh", "-c", "exec sleep 30") } }

        assertChildProcessesEnded(childrenBefore)
    }

    @Test
    fun `a command that ignores the request to exit is killed`() = runBlocking {
        assumeShellScriptsLaunch()
        val childrenBefore = ProcessHandle.current().children().toList().toSet()

        // A signal the shell ignores stays ignored across exec, so sleep itself ignores SIGTERM.
        assertFailsWith<DeviceControlException> { runCommand(500.milliseconds, "/bin/sh", "-c", "trap '' TERM; exec sleep 30") }

        assertChildProcessesEnded(childrenBefore)
    }

    /** Fails, killing them, when processes started since [childrenBefore] are still running five seconds on. */
    private fun assertChildProcessesEnded(childrenBefore: Set<ProcessHandle>) {
        val stillRunningChildren = ProcessHandle.current().children().toList().filter { child ->
            child !in childrenBefore && try {
                child.onExit().get(5, TimeUnit.SECONDS)
                false
            } catch (_: TimeoutException) {
                true
            }
        }
        stillRunningChildren.forEach(ProcessHandle::destroyForcibly)
        assertEquals(emptyList(), stillRunningChildren.map(ProcessHandle::pid), "these processes were still running")
    }
}

/** Prints its arguments, so a test can see what a launched program read from its command line. */
internal object EchoArguments {
    const val SEPARATOR = "\u0000"

    @JvmStatic
    fun main(args: Array<String>) {
        print(args.joinToString(SEPARATOR))
    }
}
