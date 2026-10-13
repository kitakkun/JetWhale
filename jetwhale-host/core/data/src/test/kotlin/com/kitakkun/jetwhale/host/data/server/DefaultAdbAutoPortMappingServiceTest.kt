package com.kitakkun.jetwhale.host.data.server

import com.kitakkun.jetwhale.host.data.util.AdbLocator
import com.kitakkun.jetwhale.host.model.HostOs
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeFalse
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains

class DefaultAdbAutoPortMappingServiceTest {
    private val folder: File = Files.createTempDirectory("adb-port-mapping").toFile()
    private val adbCallsFile = File(folder, "adb-calls")

    // Reports one emulator and stays attached, as `adb track-devices` does. On TERM it takes half a
    // second to exit, so a stop that does not wait for it returns before it has written its last
    // line. The sleep runs in the background because sh runs a trap only after a foreground command
    // finishes, whereas `wait` returns on the signal; it also bounds the script's life if nothing
    // stops it.
    private val fakeAdb = File(folder, "adb").apply {
        writeText(
            """
            #!/bin/sh
            printf '%s\n' "$*" >> '${adbCallsFile.path}'
            [ "$1" = track-devices ] || exit 0
            sleep 30 > /dev/null 2>&1 &
            trap "kill $!; sleep 0.5; echo 'track-devices ended' >> '${adbCallsFile.path}'; exit 0" TERM
            printf '0015emulator-5554\tdevice\n'
            wait
            """.trimIndent() + "\n",
        )
        setExecutable(true)
    }

    private val service = DefaultAdbAutoPortMappingService(
        AdbLocator(environment = mapOf("PATH" to folder.path), userHome = null, isWindows = false, wellKnownDirectories = emptyList(), loginShellPathVariableResolver = null),
    )

    @AfterTest
    fun deleteFolder() {
        folder.deleteRecursively()
    }

    // The mapping shows only in what the fake adb wrote: the service gives no signal when a device
    // has been mapped, so the test watches the file for it.
    @Suppress("KOTRAIL_TEST_REAL_TIME_WAIT")
    @Test
    fun `stopping the last port unmaps it from each device and ends device tracking before returning`() = runBlocking {
        assumeFalse("the fake adb is a /bin/sh script, which Windows cannot launch", HostOs.current == HostOs.WINDOWS)
        service.startPortMapping(PORT)
        withTimeout(MAPPING_TIMEOUT_MILLIS) {
            while ("-s emulator-5554 reverse tcp:$PORT tcp:$PORT" !in readAdbCallLines()) delay(20)
        }

        service.stopPortMapping(PORT)

        assertContains(readAdbCallLines(), "-s emulator-5554 reverse --remove tcp:$PORT")
        assertContains(readAdbCallLines(), "track-devices ended")
    }

    private fun readAdbCallLines(): List<String> = if (adbCallsFile.exists()) adbCallsFile.readLines() else emptyList()

    private companion object {
        const val PORT = 5103
        const val MAPPING_TIMEOUT_MILLIS = 10_000L
    }
}
