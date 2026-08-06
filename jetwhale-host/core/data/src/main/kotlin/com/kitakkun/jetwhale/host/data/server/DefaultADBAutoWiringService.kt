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

// How long to wait before re-attaching `adb track-devices` after the tracking stream ends or errors
// (e.g. another tool ran `adb kill-server`, or adb restarted because of a client/server version mismatch).
private const val RECONNECT_DELAY_MS = 2_000L

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class DefaultADBAutoWiringService : ADBAutoWiringService {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val wiredDevices = ConcurrentSet<String>()

    // Every port currently subject to auto wiring (ws and wss are wired independently). A single
    // device-tracking job serves all of them.
    private val wiredPorts = ConcurrentSet<Int>()
    private var wiringJob: Job? = null
    private val adbPath: String by lazy { findAdbPath() }

    override fun startAutoWiring(port: Int) {
        if (wiredPorts.add(port)) {
            // Devices that connected before this port was registered still need the new forwarding.
            wiredDevices.forEach { serial -> wire(serial, port) }
        }

        // A job that has finished — it stood down over a missing adb — must not read as running, or
        // a port that is already registered would never get tracking back. Restarting the server
        // calls this again, which is the retry.
        if (wiringJob?.isActive == true) return

        wiringJob = coroutineScope.launch {
            // `adb track-devices` can end at any time (adb server restart/crash, USB hiccup, version
            // mismatch). When it does, the flow completes; without this loop the service would silently
            // stop wiring until the host is restarted. Re-attach with a small backoff instead.
            while (isActive) {
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
                    // Auto wiring is on by default, so a machine with no Android SDK would otherwise
                    // retry a binary that will never appear, every RECONNECT_DELAY_MS, forever.
                    // Report once and stand down until the next startAutoWiring call.
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
                            // Remove 4-digit hex prefix
                            .replaceFirst(Regex("^[0-9a-fA-F]{4}"), "")
                        val event = line.substringAfter("\t")

                        when (event) {
                            "device" -> trySend(DeviceEvent.Connected(serial))
                            "offline" -> trySend(DeviceEvent.Disconnected(serial))
                        }
                    }
                }
            // track-devices reached EOF (the process exited): complete the flow so startAutoWiring re-attaches.
            close()
        }

        awaitClose {
            deviceTrackingProcess.destroy()
        }
    }

    private fun wire(serial: String, port: Int) {
        println("Wiring ADB reverse for device $serial on port $port")
        val (exitCode, output) = runAdb("-s", serial, "reverse", "tcp:$port", "tcp:$port")
        if (exitCode == 0) {
            wiredDevices.add(serial)
        } else {
            // e.g. "device offline" right after it turns ready, or an adb server version mismatch.
            // Surface it instead of recording a false success.
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
        // Keep tracking as long as any port is still wired (e.g. only wss was turned off).
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
// Distinct from any exit code adb itself returns, which are small positive integers.
private const val ADB_LAUNCH_FAILED = -1

private class AdbUnavailableException(adbPath: String, cause: IOException) : Exception("adb could not be launched from \"$adbPath\": ${cause.message}", cause)

private sealed interface DeviceEvent {
    data class Connected(val serial: String) : DeviceEvent
    data class Disconnected(val serial: String) : DeviceEvent
}
