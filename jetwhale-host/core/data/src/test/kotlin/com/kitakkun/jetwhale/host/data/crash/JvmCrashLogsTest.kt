package com.kitakkun.jetwhale.host.data.crash

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JvmCrashLogsTest {
    private val skikoCrashLog = parseJvmCrashLog("hs_err_pid65669.log", readFixture("crash/hs_err_skiko.log"))

    @Test
    fun `java frames are read from compiled and interpreted lines innermost first`() {
        assertEquals(
            listOf(
                "com.kitakkun.jetwhale.plugins.mirror.host.MirrorSurface.writeFrame",
                "com.kitakkun.jetwhale.plugins.mirror.host.H264DecodingKt.writeFrame",
                "com.kitakkun.jetwhale.plugins.mirror.host.H264DecodingKt.decodeH264Into",
                "kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith",
                "kotlinx.coroutines.DispatchedTask.run",
            ),
            skikoCrashLog.javaFrames,
        )
    }

    @Test
    fun `the crash is blamed on the plugin whose package is on the stack`() {
        val suspectedPluginId = skikoCrashLog.findSuspectedPluginId(
            mapOf(
                "com.kitakkun.jetwhale.network" to "com.kitakkun.jetwhale.plugins.network.host",
                "com.kitakkun.jetwhale.mirror" to "com.kitakkun.jetwhale.plugins.mirror.host",
            ),
        )

        assertEquals("com.kitakkun.jetwhale.mirror", suspectedPluginId)
    }

    @Test
    fun `a crash with no plugin code on the stack blames no plugin`() {
        assertNull(skikoCrashLog.findSuspectedPluginId(mapOf("com.kitakkun.jetwhale.network" to "com.kitakkun.jetwhale.plugins.network.host")))
    }

    @Test
    fun `a package shared by several plugins blames none of them`() {
        val suspectedPluginId = skikoCrashLog.findSuspectedPluginId(
            mapOf(
                "com.kitakkun.jetwhale.mirror" to "com.kitakkun.jetwhale.plugins.mirror.host",
                "com.kitakkun.jetwhale.mirror.headless" to "com.kitakkun.jetwhale.plugins.mirror.host",
            ),
        )

        assertNull(suspectedPluginId)
    }

    @Test
    fun `a package prefix that is not a whole segment does not match`() {
        assertNull(skikoCrashLog.findSuspectedPluginId(mapOf("com.example.mirr" to "com.kitakkun.jetwhale.plugins.mirr")))
    }

    @Test
    fun `a log cut short before the frames yields none`() {
        val truncated = parseJvmCrashLog("hs_err_pid1.log", "#\n#  SIGBUS (0xa) at pc=0x1, pid=1, tid=2\n#\n")

        assertEquals(emptyList(), truncated.javaFrames)
    }

    @Test
    fun `the crash log is found by pid in the first directory that has it`() {
        val first = Files.createTempDirectory("logs").toFile().apply { deleteOnExit() }
        val second = Files.createTempDirectory("cwd").toFile().apply { deleteOnExit() }
        File(second, "hs_err_pid42.log").apply {
            writeText("x")
            deleteOnExit()
        }

        assertEquals(File(second, "hs_err_pid42.log"), findJvmCrashLog(42, startedAtMillis = 0, listOf(first, second)))
        assertNull(findJvmCrashLog(43, startedAtMillis = 0, listOf(first, second)))
    }

    @Test
    fun `a crash log older than the run is one an earlier process with the same pid left`() {
        val logsDirectory = Files.createTempDirectory("logs").toFile().apply { deleteOnExit() }
        val leftOver = File(logsDirectory, "hs_err_pid42.log").apply {
            writeText("x")
            setLastModified(1_000_000)
            deleteOnExit()
        }

        assertNull(findJvmCrashLog(42, startedAtMillis = 2_000_000, listOf(logsDirectory)))
        assertEquals(leftOver, findJvmCrashLog(42, startedAtMillis = 1_000_000, listOf(logsDirectory)))
    }

    private fun readFixture(path: String): String = checkNotNull(javaClass.classLoader.getResource(path)).readText()
}
