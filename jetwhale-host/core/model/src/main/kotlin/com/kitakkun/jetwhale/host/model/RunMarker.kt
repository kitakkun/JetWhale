package com.kitakkun.jetwhale.host.model

import kotlin.time.Duration

/**
 * What a running host records about itself, so a later start can tell that it did not shut down
 * cleanly and what it was doing. Each run keeps its own marker, so hosts sharing an app-data
 * directory never touch each other's.
 *
 * @property runId Identifies this run's marker; unlike the pid, it is never reused.
 * @property workingDirectory Where the JVM writes its fatal error log when no `-XX:ErrorFile` was given.
 * @property startupCompleted The run has been up long enough that a crash is no longer a startup crash.
 * @property consecutiveStartupCrashes How many runs in a row died during startup before this one.
 */
data class RunMarker(
    val runId: String,
    val pid: Long,
    val startedAtMillis: Long,
    val workingDirectory: String,
    val startupCompleted: Boolean,
    val consecutiveStartupCrashes: Int,
)

interface RunMarkerRepository {
    /** The markers of every run that has not removed its own, this one's included. */
    fun readAll(): List<RunMarker>

    fun write(marker: RunMarker)

    fun delete(runId: String)
}

/** How long a run has to stay up before a crash no longer counts as a startup crash. */
@JvmInline
value class StartupGrace(val duration: Duration)
