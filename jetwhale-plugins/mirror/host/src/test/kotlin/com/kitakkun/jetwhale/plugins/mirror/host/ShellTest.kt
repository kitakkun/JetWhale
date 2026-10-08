package com.kitakkun.jetwhale.plugins.mirror.host

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

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
}

/** Prints its arguments, so a test can see what a launched program read from its command line. */
internal object EchoArguments {
    const val SEPARATOR = "\u0000"

    @JvmStatic
    fun main(args: Array<String>) {
        print(args.joinToString(SEPARATOR))
    }
}
