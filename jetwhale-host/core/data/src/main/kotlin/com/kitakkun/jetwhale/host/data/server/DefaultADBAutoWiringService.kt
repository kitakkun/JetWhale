package com.kitakkun.jetwhale.host.data.server

import com.kitakkun.jetwhale.host.data.util.findAdbPath
import com.kitakkun.jetwhale.host.model.ADBAutoWiringService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.util.collections.ConcurrentSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

private const val RECONNECT_DELAY_MS = 2_000L

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class DefaultADBAutoWiringService : ADBAutoWiringService {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val wiredDevices = ConcurrentSet<String>()

    private val wiredPorts = ConcurrentSet<Int>()
    private var wiringJob: Job? = null
    private val adbPath: String by lazy(::findAdbPath)

    override fun startAutoWiring(port: Int) {
        if (wiredPorts.add(port)) {
            wiredDevices.forEach { serial -> wire(serial, port) }
        }

        if (wiringJob?.isActive == true) return

        wiringJob = coroutineScope.launch {
            // adb track-devices can end at any time (adb server restart or crash, kill-server from
            // another tool, version mismatch), which completes the flow, so re-attach after a
            // delay.
            while (isActive) {
                @Suppress("KOTRAIL_CATCH_TOO_BROAD")
                try {
                    deviceEventFlow().collect { event ->
                        when (event) {
                            is DeviceEvent.Connected -> wiredPorts.forEach { wire(event.serial, it) }
                            is DeviceEvent.Disconnected -> wiredPorts.forEach { unwire(event.serial, it) }
                        }
                    }
                    System.err.println("ADB device tracking ended; re-attaching in ${RECONNECT_DELAY_MS}ms")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: AdbUnavailableException) {
                    System.err.println("ADB auto port mapping is inactive: ${e.message}")
                    return@launch
                } catch (e: Exception) {
                    System.err.println("ADB device tracking failed; re-attaching in ${RECONNECT_DELAY_MS}ms: ${e.message}")
                }
                delay(RECONNECT_DELAY_MS.milliseconds)
            }
        }
    }

    private fun deviceEventFlow(): Flow<DeviceEvent> = callbackFlow {
        val deviceTrackingProcess = try {
            ProcessBuilder(adbPath, "track-devices")
                .redirectErrorStream(true)
                .start()
        } catch (e: IOException) {
            throw AdbUnavailableException(adbPath, e)
        }

        launch {
            deviceTrackingProcess.inputStream
                .bufferedReader()
                .useLines { lines ->
                    lines.forEach { line ->
                        val serial = line.substringBefore("\t")
                            // track-devices prefixes each message with its length as 4 hex digits.
                            .replaceFirst(Regex("^[0-9a-fA-F]{4}"), "")
                        val event = line.substringAfter("\t")

                        when (event) {
                            "device" -> trySend(DeviceEvent.Connected(serial))
                            "offline" -> trySend(DeviceEvent.Disconnected(serial))
                        }
                    }
                }
            close()
        }

        awaitClose(deviceTrackingProcess::destroy)
    }

    private fun wire(serial: String, port: Int) {
        println("Wiring ADB reverse for device $serial on port $port")
        val (exitCode, output) = runAdb("-s", serial, "reverse", "tcp:$port", "tcp:$port")
        if (exitCode == 0) {
            wiredDevices.add(serial)
        } else {
            System.err.println("Failed to wire ADB reverse for device $serial on port $port (exit=$exitCode): $output")
        }
    }

    private fun unwire(serial: String, port: Int) {
        println("Unwiring ADB reverse for device $serial on port $port")
        val (exitCode, output) = runAdb("-s", serial, "reverse", "--remove", "tcp:$port")
        if (exitCode != 0) {
            System.err.println("Failed to unwire ADB reverse for device $serial on port $port (exit=$exitCode): $output")
        }
        wiredDevices.remove(serial)
    }

    override fun stopAutoWiring(port: Int) {
        wiredPorts.remove(port)
        wiredDevices.forEach { serial ->
            runAdb("-s", serial, "reverse", "--remove", "tcp:$port")
        }
        if (wiredPorts.isEmpty()) {
            wiringJob?.cancel()
            wiringJob = null
            wiredDevices.clear()
        }
    }

    /**
     * Runs an adb command to completion and returns its exit code together with its merged output.
     *
     * A failure to launch adb at all is reported as a non-zero exit rather than thrown. The wiring
     * job catches everything, but teardown does not run inside it — [stopAutoWiring] and [unwire]
     * call this directly, and an adb that has been uninstalled or unmounted since wiring would
     * otherwise throw IOException out of a shutdown path, where nothing is waiting to handle it.
     * Callers already treat a non-zero exit as a failure to report.
     */
    private fun runAdb(vararg args: String): Pair<Int, String> = try {
        val process = ProcessBuilder(adbPath, *args)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor() to output
    } catch (e: IOException) {
        ADB_LAUNCH_FAILED to "adb could not be launched from \"$adbPath\": ${e.message}"
    }
}

/** The adb executable itself could not be launched — no amount of retrying will bring it back. */
private const val ADB_LAUNCH_FAILED = -1

private class AdbUnavailableException(adbPath: String, cause: IOException) : Exception("adb could not be launched from \"$adbPath\": ${cause.message}", cause)

private sealed interface DeviceEvent {
    data class Connected(val serial: String) : DeviceEvent

    data class Disconnected(val serial: String) : DeviceEvent
}
