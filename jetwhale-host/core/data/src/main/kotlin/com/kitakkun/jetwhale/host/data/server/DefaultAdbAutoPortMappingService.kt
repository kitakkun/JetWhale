package com.kitakkun.jetwhale.host.data.server

import com.kitakkun.jetwhale.host.data.util.AdbLocator
import com.kitakkun.jetwhale.host.model.AdbAutoPortMappingService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.util.collections.ConcurrentSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
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
class DefaultAdbAutoPortMappingService(
    private val adbLocator: AdbLocator,
) : AdbAutoPortMappingService {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val mappedDeviceSerials = ConcurrentSet<String>()

    private val mappedPorts = ConcurrentSet<Int>()
    private var deviceTrackingJob: Job? = null

    // Without an adb found, the bare name still lets the launch fail with the OS's own error, which
    // the port mapping reports.
    private val adbPath: String by lazy { adbLocator.find()?.path ?: adbLocator.executableName }

    override fun startPortMapping(port: Int) {
        if (mappedPorts.add(port)) {
            mappedDeviceSerials.forEach { serial -> mapPort(serial, port) }
        }

        if (deviceTrackingJob?.isActive == true) return

        deviceTrackingJob = coroutineScope.launch {
            // adb track-devices can end at any time (adb server restart or crash, kill-server from
            // another tool, version mismatch), which completes the flow, so re-attach after a
            // delay.
            while (isActive) {
                // The tracking loop has to outlive any failure of one adb session: it logs it and
                // re-attaches.
                @Suppress("KOTRAIL_CATCH_TOO_BROAD")
                try {
                    deviceEventFlow().collect { event ->
                        when (event) {
                            is DeviceEvent.Connected -> mappedPorts.forEach { mapPort(event.serial, it) }
                            is DeviceEvent.Disconnected -> mappedPorts.forEach { unmapPort(event.serial, it) }
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

        awaitClose {
            deviceTrackingProcess.destroy()
            deviceTrackingProcess.waitFor()
        }
    }

    private fun mapPort(serial: String, port: Int) {
        println("Mapping port $port for device $serial with adb reverse")
        val (exitCode, output) = runAdb("-s", serial, "reverse", "tcp:$port", "tcp:$port")
        if (exitCode == 0) {
            mappedDeviceSerials.add(serial)
        } else {
            System.err.println("Failed to map port $port for device $serial with adb reverse (exit=$exitCode): $output")
        }
    }

    private fun unmapPort(serial: String, port: Int) {
        println("Unmapping port $port for device $serial with adb reverse")
        val (exitCode, output) = runAdb("-s", serial, "reverse", "--remove", "tcp:$port")
        if (exitCode != 0) {
            System.err.println("Failed to unmap port $port for device $serial with adb reverse (exit=$exitCode): $output")
        }
        mappedDeviceSerials.remove(serial)
    }

    override suspend fun stopPortMapping(port: Int) {
        mappedPorts.remove(port)
        val noPortsRemainMapped = mappedPorts.isEmpty()
        if (noPortsRemainMapped) {
            // Joined before the removal below, so that a device the tracking is mapping right now
            // is already among the serials it unmaps.
            deviceTrackingJob?.cancelAndJoin()
            deviceTrackingJob = null
        }
        mappedDeviceSerials.forEach { serial ->
            runAdb("-s", serial, "reverse", "--remove", "tcp:$port")
        }
        if (noPortsRemainMapped) mappedDeviceSerials.clear()
    }

    /**
     * Runs an adb command to completion and returns its exit code together with its merged output.
     *
     * A failure to launch adb at all is reported as a non-zero exit rather than thrown. The device
     * tracking job catches everything, but teardown does not run inside it — [stopPortMapping] and
     * [unmapPort] call this directly, and an adb that has been uninstalled or unmounted since mapping
     * would otherwise throw IOException out of a shutdown path, where nothing is waiting to handle it.
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
