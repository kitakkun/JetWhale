package com.kitakkun.jetwhale.plugins.xctestrunner

import kotlinx.coroutines.delay
import java.io.File
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.StandardOpenOption
import kotlin.time.Duration
import kotlin.time.TimeSource

private const val LOCK_POLL_MILLIS = 100L

/**
 * Runs [block] holding an exclusive lock on [lockFile], waiting up to [timeout] for whoever holds it.
 *
 * The lock is the OS's, so it keeps out other processes and other plugins in this one: a JVM holds a
 * file lock for all of its class loaders, and a second lock on the same file from this JVM is refused
 * with [OverlappingFileLockException] rather than blocked, which counts here as held.
 */
internal suspend fun <T> withFileLock(lockFile: File, timeout: Duration, block: suspend () -> T): T {
    lockFile.parentFile.mkdirs()
    FileChannel.open(lockFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
        val waitingSince = TimeSource.Monotonic.markNow()
        var lock: FileLock? = null
        while (lock == null) {
            lock = try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            }
            if (lock == null) {
                if (waitingSince.elapsedNow() > timeout) throw XcTestRunnerStartException("another plugin has been starting the XCTest runner for over $timeout; ${lockFile.name} stays locked", null)
                delay(LOCK_POLL_MILLIS)
            }
        }
        try {
            return block()
        } finally {
            lock.release()
        }
    }
}
