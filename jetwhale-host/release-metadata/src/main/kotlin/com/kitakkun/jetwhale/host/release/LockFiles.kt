package com.kitakkun.jetwhale.host.release

import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * The OS file locks the launcher and the host coordinate through. The OS releases a lock when its
 * process ends, so a crash leaves none behind.
 */
interface LockFiles {
    /** Takes the lock on [path], waiting while another process holds it. */
    fun lock(path: Path): HeldLock

    /** Takes the lock on [path], or returns null when another process holds it. */
    fun tryLock(path: Path): HeldLock?

    companion object {
        val Os: LockFiles = object : LockFiles {
            override fun lock(path: Path): HeldLock {
                val channel = open(path)
                channel.lock()
                return HeldLock(channel::close)
            }

            override fun tryLock(path: Path): HeldLock? {
                val channel = open(path)
                if (channel.tryLock() == null) {
                    channel.close()
                    return null
                }
                return HeldLock(channel::close)
            }

            private fun open(path: Path): FileChannel {
                Files.createDirectories(path.parent)
                return FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            }
        }
    }
}

fun interface HeldLock : AutoCloseable {
    /** Releases the lock. */
    override fun close()
}
