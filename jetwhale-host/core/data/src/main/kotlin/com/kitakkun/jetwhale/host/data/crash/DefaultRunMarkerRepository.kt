package com.kitakkun.jetwhale.host.data.crash

import com.kitakkun.jetwhale.host.data.AppDataDirectoryProvider
import com.kitakkun.jetwhale.host.model.RunMarker
import com.kitakkun.jetwhale.host.model.RunMarkerRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.io.IOException
import java.util.Properties
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Keeps the marker in a small properties file. It is read and written synchronously at startup,
 * before the host's DataStores are up, and it must survive the process dying at any moment, so a
 * plain file written in one go is the whole store.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultRunMarkerRepository(
    private val appDataDirectoryProvider: AppDataDirectoryProvider,
) : RunMarkerRepository {
    private val logger = Logger.getLogger(DefaultRunMarkerRepository::class.java.name)

    override fun read(): RunMarker? {
        val file = appDataDirectoryProvider.getRunMarkerFile()
        if (!file.isFile) return null
        return try {
            val properties = Properties().apply { file.inputStream().use(::load) }
            RunMarker(
                pid = properties.getProperty(PID)?.toLongOrNull() ?: return null,
                startedAtMillis = properties.getProperty(STARTED_AT)?.toLongOrNull() ?: 0,
                workingDirectory = properties.getProperty(WORKING_DIRECTORY).orEmpty(),
                startupCompleted = properties.getProperty(STARTUP_COMPLETED).toBoolean(),
                consecutiveStartupCrashes = properties.getProperty(CONSECUTIVE_STARTUP_CRASHES)?.toIntOrNull() ?: 0,
            )
        } catch (e: IOException) {
            logger.log(Level.WARNING, "Could not read the run marker at ${file.path}", e)
            null
        }
    }

    override fun write(marker: RunMarker) {
        val file = appDataDirectoryProvider.getRunMarkerFile()
        val properties = Properties().apply {
            setProperty(PID, marker.pid.toString())
            setProperty(STARTED_AT, marker.startedAtMillis.toString())
            setProperty(WORKING_DIRECTORY, marker.workingDirectory)
            setProperty(STARTUP_COMPLETED, marker.startupCompleted.toString())
            setProperty(CONSECUTIVE_STARTUP_CRASHES, marker.consecutiveStartupCrashes.toString())
        }
        try {
            file.parentFile?.mkdirs()
            file.outputStream().use { properties.store(it, "JetWhale run marker") }
        } catch (e: IOException) {
            logger.log(Level.WARNING, "Could not write the run marker at ${file.path}", e)
        }
    }

    override fun delete() {
        appDataDirectoryProvider.getRunMarkerFile().delete()
    }
}

private const val PID = "pid"
private const val STARTED_AT = "startedAtMillis"
private const val WORKING_DIRECTORY = "workingDirectory"
private const val STARTUP_COMPLETED = "startupCompleted"
private const val CONSECUTIVE_STARTUP_CRASHES = "consecutiveStartupCrashes"
