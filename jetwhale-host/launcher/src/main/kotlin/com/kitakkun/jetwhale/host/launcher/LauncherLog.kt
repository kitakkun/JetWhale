package com.kitakkun.jetwhale.host.launcher

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.OffsetDateTime

/** Where the launcher writes its decisions. */
fun interface LauncherLog {
    fun write(message: String)
}

/**
 * Appends to `logs/launcher.log`, and echoes to stderr for a `--headless` launch, whose terminal is
 * where its user looks.
 */
class FileLauncherLog(
    private val file: Path,
    private val echoToStandardError: Boolean,
) : LauncherLog {
    override fun write(message: String) {
        val line = "${OffsetDateTime.now()} $message\n"
        if (echoToStandardError) System.err.print(line)
        try {
            Files.createDirectories(file.parent)
            Files.writeString(file, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        } catch (e: IOException) {
            System.err.print("Could not write to $file (${e.message}): $line")
        }
    }
}
