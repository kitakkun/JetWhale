package com.kitakkun.jetwhale.host.launcher

import com.kitakkun.jetwhale.host.release.HostVersion
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class HostCommandLineTest {
    private val jarBytes = "host".toByteArray()
    private val start = HostStart(
        name = "1.0.0-alpha15",
        version = assertNotNull(HostVersion.parse("1.0.0-alpha15")),
        metadata = hostMetadata("1.0.0-alpha15", jarBytes),
        jar = Path.of("/data/host/1.0.0-alpha15/jetwhale-host-1.0.0-alpha15-macos-arm64.jar"),
        isBundled = false,
    )

    @Test
    fun `passes the version's arguments, then the launcher contract, then the host's own arguments`() {
        val commandLine = HostCommandLine(
            javaExecutable = Path.of("/app/runtime/bin/java"),
            platformKey = PLATFORM,
            logsDirectory = Path.of("/data/logs"),
            launcherExecutable = "/app/MacOS/JetWhale Debugger",
            hostDirectory = Path.of("/data/host"),
            appDataDirectoryOverride = "/data",
            hostArguments = listOf("--server-port", "5103"),
        )

        assertEquals(
            listOf(
                "/app/runtime/bin/java",
                "-Dcompose.application.configure.swing.globals=true",
                "-Xdock:name=JetWhale Debugger",
                "-XX:ErrorFile=/data/logs/hs_err_pid%p.log",
                "-Djetwhale.launcher.contract=1",
                "-Djetwhale.launcher.executable=/app/MacOS/JetWhale Debugger",
                "-Djetwhale.launcher.hostDir=/data/host",
                "-Djetwhale.appDataDir=/data",
                "-Djetwhale.launcher.setAside=1.0.0-alpha16",
                "-cp",
                "/data/host/1.0.0-alpha15/jetwhale-host-1.0.0-alpha15-macos-arm64.jar",
                "com.kitakkun.jetwhale.host.MainKt",
                "--server-port",
                "5103",
            ),
            commandLine.build(start, setAside = "1.0.0-alpha16"),
        )
    }

    @Test
    fun `leaves out what it does not know`() {
        val commandLine = HostCommandLine(
            javaExecutable = Path.of("/app/runtime/bin/java"),
            platformKey = PLATFORM,
            logsDirectory = Path.of("/data/logs"),
            launcherExecutable = null,
            hostDirectory = Path.of("/data/host"),
            appDataDirectoryOverride = null,
            hostArguments = emptyList(),
        )

        val command = commandLine.build(start, setAside = null)

        assertEquals(listOf("-cp", start.jar.toString(), "com.kitakkun.jetwhale.host.MainKt"), command.takeLast(3))
        assertEquals(emptyList(), command.filter { it.startsWith("-Djetwhale.launcher.executable") || it.startsWith("-Djetwhale.appDataDir") || it.startsWith("-Djetwhale.launcher.setAside") })
    }
}
