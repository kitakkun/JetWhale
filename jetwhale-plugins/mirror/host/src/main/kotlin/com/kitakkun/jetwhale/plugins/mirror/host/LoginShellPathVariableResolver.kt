package com.kitakkun.jetwhale.plugins.mirror.host

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/**
 * Reads the PATH that the user's login shell sets up. An app started from Finder, the Dock or a
 * desktop entry does not inherit it, so it misses the directories that Homebrew, asdf and the like add
 * in the shell's startup files.
 *
 * The shell runs as an interactive login shell, as a terminal starts it, so that a PATH set only in
 * `.zshrc` or `.bashrc` counts too. It gets no input, so a startup file that asks for some reads the
 * end of input instead of waiting.
 *
 * The host reads the PATH the same way for adb with its own copy of this class, since a plugin sees
 * only the published host SDK.
 */
internal class LoginShellPathVariableResolver(
    private val shellPath: String,
    private val timeout: Duration,
) {
    /**
     * The PATH the shell at [shellPath] sets up, or null when the shell does not start, exits
     * non-zero or prints no PATH. A shell still running after [timeout] is killed together with what
     * it started, and gives null too.
     */
    fun resolveLoginShellPathVariable(): String? {
        // A file rather than a pipe: a process that a startup file leaves running in the background
        // keeps the shell's output open, and reading a pipe would then never reach its end.
        val outputFile = try {
            File.createTempFile("login-shell-path", null)
        } catch (_: IOException) {
            return null
        }
        try {
            val shell = ProcessBuilder(shellPath, "-l", "-i", "-c", PRINT_PATH_COMMAND)
                .redirectInput(File("/dev/null"))
                .redirectOutput(outputFile)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            try {
                if (!shell.waitFor(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS) || shell.exitValue() != 0) return null
            } finally {
                if (shell.isAlive) {
                    // Listed while the shell lives: once it dies, what it started is no longer
                    // among its descendants.
                    val shellDescendants = shell.descendants().toList()
                    shell.destroyForcibly()
                    shellDescendants.forEach(ProcessHandle::destroyForcibly)
                }
            }
            return PATH_BETWEEN_MARKERS.find(outputFile.readText())?.groupValues?.get(1)?.takeIf(String::isNotEmpty)
        } catch (_: IOException) {
            return null
        } finally {
            outputFile.delete()
        }
    }
}

// Startup files may print banners and other output of their own; the PATH is what lies between
// these markers.
private const val MARKER = "__JETWHALE_LOGIN_SHELL_PATH__"

private const val PRINT_PATH_COMMAND = "printf '%s%s%s' $MARKER \"\$PATH\" $MARKER"

private val PATH_BETWEEN_MARKERS = Regex("$MARKER(.*?)$MARKER")
