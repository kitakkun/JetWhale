package com.kitakkun.jetwhale.host.data.crash

import com.kitakkun.jetwhale.host.data.AppDataDirectoryProvider
import com.kitakkun.jetwhale.host.model.RunMarker
import com.kitakkun.jetwhale.host.model.RunMarkerRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.time.Duration.Companion.minutes

private const val MARKER_EXTENSION = "properties"
private const val TEMPORARY_EXTENSION = "tmp"

/**
 * A temporary file this old was left by a write that never finished: a live host moves its temporary
 * file into place within milliseconds of creating it.
 */
private val ABANDONED_TEMPORARY_FILE_AGE = 1.minutes

/**
 * Keeps each run's marker in a small properties file of its own, named by run id. It is read and
 * written synchronously at startup, before the host's DataStores are up, and it must survive the
 * process dying at any moment, so each write goes to a temporary file that is then moved over the
 * marker: a reader sees the old marker or the new one, never half of one.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultRunMarkerRepository(
    private val appDataDirectoryProvider: AppDataDirectoryProvider,
) : RunMarkerRepository {
    private val logger = Logger.getLogger(DefaultRunMarkerRepository::class.java.name)

    override fun readAll(): List<RunMarker> {
        val files = appDataDirectoryProvider.getRunMarkerDirectory().listFiles().orEmpty()
        files.filter { it.extension == TEMPORARY_EXTENSION && System.currentTimeMillis() - it.lastModified() > ABANDONED_TEMPORARY_FILE_AGE.inWholeMilliseconds }
            .forEach(File::delete)
        return files.filter { it.extension == MARKER_EXTENSION }.mapNotNull { file ->
            val marker = read(file)
            if (marker == null) file.delete()
            marker
        }
    }

    private fun read(file: File): RunMarker? = try {
        val properties = Properties().apply { file.inputStream().use(::load) }
        RunMarker(
            runId = file.nameWithoutExtension,
            pid = properties.getProperty(PID_KEY)?.toLongOrNull() ?: return null,
            startedAtMillis = properties.getProperty(STARTED_AT_MILLIS_KEY)?.toLongOrNull() ?: return null,
            workingDirectory = properties.getProperty(WORKING_DIRECTORY_KEY).orEmpty(),
            startupCompleted = properties.getProperty(STARTUP_COMPLETED_KEY).toBoolean(),
            consecutiveStartupCrashes = properties.getProperty(CONSECUTIVE_STARTUP_CRASHES_KEY)?.toIntOrNull() ?: 0,
        )
    } catch (e: IOException) {
        logger.log(Level.WARNING, "Could not read the run marker at ${file.path}", e)
        null
    } catch (e: IllegalArgumentException) {
        logger.log(Level.WARNING, "The run marker at ${file.path} is malformed", e)
        null
    }

    override fun write(marker: RunMarker) {
        val file = markerFile(marker.runId)
        val temporaryFile = File(file.parentFile, "${marker.runId}.$TEMPORARY_EXTENSION")
        val properties = Properties().apply {
            setProperty(PID_KEY, marker.pid.toString())
            setProperty(STARTED_AT_MILLIS_KEY, marker.startedAtMillis.toString())
            setProperty(WORKING_DIRECTORY_KEY, marker.workingDirectory)
            setProperty(STARTUP_COMPLETED_KEY, marker.startupCompleted.toString())
            setProperty(CONSECUTIVE_STARTUP_CRASHES_KEY, marker.consecutiveStartupCrashes.toString())
        }
        try {
            file.parentFile.mkdirs()
            temporaryFile.outputStream().use { properties.store(it, "JetWhale run marker") }
            try {
                Files.move(temporaryFile.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporaryFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: IOException) {
            logger.log(Level.WARNING, "Could not write the run marker at ${file.path}", e)
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Unexpected failure writing the run marker at ${file.path}", e)
        }
    }

    override fun delete(runId: String) {
        markerFile(runId).delete()
    }

    private fun markerFile(runId: String) = File(appDataDirectoryProvider.getRunMarkerDirectory(), "$runId.$MARKER_EXTENSION")
}

private const val PID_KEY = "pid"
private const val STARTED_AT_MILLIS_KEY = "startedAtMillis"
private const val WORKING_DIRECTORY_KEY = "workingDirectory"
private const val STARTUP_COMPLETED_KEY = "startupCompleted"
private const val CONSECUTIVE_STARTUP_CRASHES_KEY = "consecutiveStartupCrashes"
