package com.kitakkun.jetwhale.host.model

/**
 * What a running host records about itself, so the next start can tell that it did not shut down
 * cleanly and what it was doing.
 *
 * @property workingDirectory Where the JVM writes its fatal error log when no `-XX:ErrorFile` was given.
 * @property startupCompleted The run has been up long enough that a crash is no longer a startup crash.
 * @property consecutiveStartupCrashes How many runs in a row died during startup before this one.
 */
data class RunMarker(
    val pid: Long,
    val startedAtMillis: Long,
    val workingDirectory: String,
    val startupCompleted: Boolean,
    val consecutiveStartupCrashes: Int,
)

interface RunMarkerRepository {
    fun read(): RunMarker?

    fun write(marker: RunMarker)

    fun delete()
}
