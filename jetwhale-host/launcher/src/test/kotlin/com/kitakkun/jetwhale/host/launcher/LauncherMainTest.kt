package com.kitakkun.jetwhale.host.launcher

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LauncherMainTest {
    @Test
    fun `logs an error it did not expect to launcher log and exits with status 1`() {
        val appData = Files.createTempDirectory("launcher-main")
        Files.createDirectories(appData.resolve("host/launch.lock"))

        val launcher = ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-cp",
            System.getProperty("java.class.path"),
            "-D$APP_DATA_DIR_PROPERTY=$appData",
            "com.kitakkun.jetwhale.host.launcher.LauncherMainKt",
            "--headless",
        ).redirectErrorStream(true).redirectOutput(appData.resolve("launcher-output.txt").toFile()).start()

        assertTrue(launcher.waitFor(60, TimeUnit.SECONDS), "the launcher did not exit")
        assertEquals(1, launcher.exitValue())
        assertContains(Files.readString(appData.resolve("logs/launcher.log")), "launch.lock")
    }
}
