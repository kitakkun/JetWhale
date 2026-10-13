package com.kitakkun.jetwhale.host.data.util

import com.kitakkun.jetwhale.host.model.HostOs
import org.junit.Assume.assumeFalse
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class LoginShellPathVariableResolverTest {
    private val folder: File = Files.createTempDirectory("login-shell-path").toFile()
    private val fakeShell = File(folder, "shell")
    private val sleeperPidFile = File(folder, "sleeper-pid")

    @AfterTest
    fun cleanUp() {
        if (sleeperPidFile.exists()) ProcessHandle.of(sleeperPidFile.readText().trim().toLong()).ifPresent(ProcessHandle::destroyForcibly)
        folder.deleteRecursively()
    }

    @Test
    fun `the PATH the login shell sets up is read`() {
        assumeShellScriptsLaunch()
        shellRuns("PATH='/opt/login/bin:/usr/bin:/bin'", "eval \"\$4\"")

        assertEquals("/opt/login/bin:/usr/bin:/bin", LoginShellPathVariableResolver(fakeShell.path, TIMEOUT).resolveLoginShellPathVariable())
    }

    @Test
    fun `what startup files print around the PATH is left out`() {
        assumeShellScriptsLaunch()
        shellRuns("echo 'Last login: Tue Oct  6 on ttys001'", "printf 'a prompt without a line break'", "PATH='/opt/login/bin'", "eval \"\$4\"", "echo 'Saving session...'")

        assertEquals("/opt/login/bin", LoginShellPathVariableResolver(fakeShell.path, TIMEOUT).resolveLoginShellPathVariable())
    }

    @Test
    fun `the shell runs as an interactive login shell with nothing to read`() {
        assumeShellScriptsLaunch()
        val argumentsFile = File(folder, "arguments")
        // cat returns at once only when its input is already at its end.
        shellRuns("echo \"\$1 \$2 \$3\" > '${argumentsFile.path}'", "cat > /dev/null", "PATH='/opt/login/bin'", "eval \"\$4\"")

        assertEquals("/opt/login/bin", LoginShellPathVariableResolver(fakeShell.path, TIMEOUT).resolveLoginShellPathVariable())
        assertEquals("-l -i -c", argumentsFile.readText().trim())
    }

    @Test
    fun `a shell still running after the timeout is killed with what it started, and gives no PATH`() {
        assumeShellScriptsLaunch()
        shellRuns("sleep 30 &", "echo \$! > '${sleeperPidFile.path}'", "wait")
        val startedAt = TimeSource.Monotonic.markNow()

        val pathVariable = LoginShellPathVariableResolver(fakeShell.path, 2.seconds).resolveLoginShellPathVariable()

        assertNull(pathVariable)
        assertTrue(startedAt.elapsedNow() < 10.seconds)
        ProcessHandle.of(sleeperPidFile.readText().trim().toLong()).ifPresent { sleeper -> sleeper.onExit().get(5, TimeUnit.SECONDS) }
    }

    @Test
    fun `a shell that exits non-zero gives no PATH, even one it printed`() {
        assumeShellScriptsLaunch()
        shellRuns("PATH='/opt/login/bin'", "eval \"\$4\"", "exit 3")

        assertNull(LoginShellPathVariableResolver(fakeShell.path, TIMEOUT).resolveLoginShellPathVariable())
    }

    @Test
    fun `a shell that prints nothing gives no PATH`() {
        assumeShellScriptsLaunch()
        shellRuns("exit 0")

        assertNull(LoginShellPathVariableResolver(fakeShell.path, TIMEOUT).resolveLoginShellPathVariable())
    }

    @Test
    fun `a shell whose PATH is empty gives no PATH`() {
        assumeShellScriptsLaunch()
        shellRuns("PATH=''", "eval \"\$4\"")

        assertNull(LoginShellPathVariableResolver(fakeShell.path, TIMEOUT).resolveLoginShellPathVariable())
    }

    @Test
    fun `a shell that cannot be started gives no PATH`() {
        assertNull(LoginShellPathVariableResolver(File(folder, "no-such-shell").path, TIMEOUT).resolveLoginShellPathVariable())
    }

    private fun assumeShellScriptsLaunch() = assumeFalse("the shells stand in as /bin/sh scripts, which Windows cannot launch", HostOs.current == HostOs.WINDOWS)

    /** Makes [fakeShell] a script that runs [lines]; the command the resolver passes is its fourth argument. */
    private fun shellRuns(vararg lines: String) {
        fakeShell.writeText(lines.joinToString(separator = "\n", prefix = "#!/bin/sh\n", postfix = "\n"))
        fakeShell.setExecutable(true)
    }
}

private val TIMEOUT = 5.seconds
