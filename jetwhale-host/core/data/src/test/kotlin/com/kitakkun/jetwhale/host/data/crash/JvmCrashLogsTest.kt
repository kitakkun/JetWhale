package com.kitakkun.jetwhale.host.data.crash

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JvmCrashLogsTest {
    private val skikoCrash = parseJvmCrashLog("hs_err_pid65669.log", fixture("crash/hs_err_skiko.log"))

    @Test
    fun `the signal the problematic frame and the crashing thread are read`() {
        assertEquals("SIGSEGV (0xb) at pc=0x000000014957d3d0, pid=65669, tid=130819", skikoCrash.errorLine)
        assertEquals("C  [libskiko-macos-arm64.dylib+0x1053d0]  SkBitmap::notifyPixelsChanged() const+0x0", skikoCrash.problematicFrame)
        assertEquals("JavaThread \"DefaultDispatcher-worker-10\" daemon [_thread_in_native, id=130819, stack(0x000000030aeac000,0x000000030b0af000) (2060K)]", skikoCrash.crashingThread)
    }

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
            skikoCrash.javaFrames,
        )
    }

    @Test
    fun `the crash is blamed on the plugin whose package is on the stack`() {
        val suspect = skikoCrash.suspectPlugin(
            mapOf(
                "com.kitakkun.jetwhale.network" to "com.kitakkun.jetwhale.plugins.network.host",
                "com.kitakkun.jetwhale.mirror" to "com.kitakkun.jetwhale.plugins.mirror.host",
            ),
        )

        assertEquals("com.kitakkun.jetwhale.mirror", suspect)
    }

    @Test
    fun `a crash with no plugin code on the stack blames no plugin`() {
        assertNull(skikoCrash.suspectPlugin(mapOf("com.kitakkun.jetwhale.network" to "com.kitakkun.jetwhale.plugins.network.host")))
    }

    @Test
    fun `a package shared by several plugins blames none of them`() {
        val suspect = skikoCrash.suspectPlugin(
            mapOf(
                "com.kitakkun.jetwhale.mirror" to "com.kitakkun.jetwhale.plugins.mirror.host",
                "com.kitakkun.jetwhale.mirror.headless" to "com.kitakkun.jetwhale.plugins.mirror.host",
            ),
        )

        assertNull(suspect)
    }

    @Test
    fun `a package prefix that is not a whole segment does not match`() {
        assertNull(skikoCrash.suspectPlugin(mapOf("com.example.mirr" to "com.kitakkun.jetwhale.plugins.mirr")))
    }

    @Test
    fun `a log cut short still yields what it has`() {
        val truncated = parseJvmCrashLog("hs_err_pid1.log", "#\n#  SIGBUS (0xa) at pc=0x1, pid=1, tid=2\n#\n")

        assertEquals("SIGBUS (0xa) at pc=0x1, pid=1, tid=2", truncated.errorLine)
        assertNull(truncated.problematicFrame)
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

        assertEquals(File(second, "hs_err_pid42.log"), findJvmCrashLog(42, listOf(first, second)))
        assertNull(findJvmCrashLog(43, listOf(first, second)))
    }

    private fun fixture(path: String): String = checkNotNull(javaClass.classLoader.getResource(path)).readText()
}
