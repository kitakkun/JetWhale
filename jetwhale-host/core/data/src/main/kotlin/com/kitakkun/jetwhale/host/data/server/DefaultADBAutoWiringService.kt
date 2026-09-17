package com.kitakkun.jetwhale.host.data.server

import com.kitakkun.jetwhale.host.model.ADBAutoWiringService
import com.kitakkun.jetwhale.host.sdk.JetWhaleAdb
import com.kitakkun.jetwhale.host.sdk.JetWhaleAdbUnavailableException
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.util.collections.ConcurrentSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// How long to wait before re-attaching `adb track-devices` after the tracking stream ends or errors
// (e.g. another tool ran `adb kill-server`, or adb restarted because of a client/server version mismatch).
private const val RECONNECT_DELAY_MS = 2_000L

/** Ample for a local `adb reverse`, which either answers at once or is not going to answer at all. */
private val WIRING_TIMEOUT = 10.seconds

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class DefaultADBAutoWiringService(
    private val adb: JetWhaleAdb,
) : ADBAutoWiringService {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private val wiredDevices = ConcurrentSet<String>()

    // Every port currently subject to auto wiring (ws and wss are wired independently). A single
    // device-tracking job serves all of them.
    private val wiredPorts = ConcurrentSet<Int>()
    private var wiringJob: Job? = null

    override fun startAutoWiring(port: Int) {
        if (wiredPorts.add(port)) {
            // Devices that connected before this port was registered still need the new forwarding.
            val serials = wiredDevices.toList()
            coroutineScope.launch {
                serials.forEach { serial -> wire(serial, port) }
            }
        }

        // A job that has finished — it stood down over a missing adb — must not read as running, or
        // a port that is already registered would never get tracking back. Restarting the server
        // calls this again, which is the retry.
        if (wiringJob?.isActive == true) return

        wiringJob = coroutineScope.launch {
            // `adb track-devices` can end at any time (adb server restart/crash, USB hiccup, version
            // mismatch). When it does, the call returns; without this loop the service would silently
            // stop wiring until the host is restarted. Re-attach with a small backoff instead.
            while (isActive) {
                try {
                    trackDevices()
                    System.err.println("ADB device tracking ended; re-attaching in ${RECONNECT_DELAY_MS}ms")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: JetWhaleAdbUnavailableException) {
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

    /** Reads `adb track-devices` until the stream ends, wiring and unwiring devices as they appear. */
    private suspend fun trackDevices() {
        adb.runStreaming("track-devices", timeout = Duration.INFINITE) { stream ->
            stream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    val serial = line.substringBefore("\t")
                        // Remove 4-digit hex prefix
                        .replaceFirst(Regex("^[0-9a-fA-F]{4}"), "")
                    when (line.substringAfter("\t")) {
                        "device" -> wiredPorts.forEach { port -> wire(serial, port) }
                        "offline" -> wiredPorts.forEach { port -> unwire(serial, port) }
                    }
                }
            }
        }
    }

    private suspend fun wire(serial: String, port: Int) {
        println("Wiring ADB reverse for device $serial on port $port")
        val result = runAdb("-s", serial, "reverse", "tcp:$port", "tcp:$port")
        if (result != null && result.exitCode == 0) {
            wiredDevices.add(serial)
        } else {
            // e.g. "device offline" right after it turns ready, or an adb server version mismatch.
            // Surface it instead of recording a false success.
            System.err.println("Failed to wire ADB reverse for device $serial on port $port: ${result?.let { "exit=${it.exitCode}: ${it.output}" } ?: "adb unavailable"}")
        }
    }

    private suspend fun unwire(serial: String, port: Int) {
        println("Unwiring ADB reverse for device $serial on port $port")
        val result = runAdb("-s", serial, "reverse", "--remove", "tcp:$port")
        if (result == null || result.exitCode != 0) {
            System.err.println("Failed to unwire ADB reverse for device $serial on port $port: ${result?.let { "exit=${it.exitCode}: ${it.output}" } ?: "adb unavailable"}")
        }
        wiredDevices.remove(serial)
    }

    override fun stopAutoWiring(port: Int) {
        wiredPorts.remove(port)
        val serials = wiredDevices.toList()
        coroutineScope.launch {
            serials.forEach { serial -> runAdb("-s", serial, "reverse", "--remove", "tcp:$port") }
        }
        // Keep tracking as long as any port is still wired (e.g. only wss was turned off).
        if (wiredPorts.isEmpty()) {
            wiringJob?.cancel()
            wiringJob = null
            wiredDevices.clear()
        }
    }

    /**
     * Runs an adb command to completion, reporting an adb that cannot be launched as `null` rather
     * than throwing: teardown ([stopAutoWiring], [unwire]) runs outside the wiring job, where an adb
     * that has been uninstalled or unmounted since wiring would otherwise throw into a shutdown path
     * with nothing waiting to handle it.
     */
    private suspend fun runAdb(vararg args: String) = try {
        adb.run(*args, timeout = WIRING_TIMEOUT)
    } catch (e: JetWhaleAdbUnavailableException) {
        System.err.println(e.message)
        null
    }
}
