package com.kitakkun.jetwhale.plugins.xctestrunner

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class FileLocksTest {
    private val folder: File = Files.createTempDirectory("file-locks").toFile()
    private val lockFile = File(folder, "SIM-1.lock")

    @AfterTest
    fun deleteFolder() {
        folder.deleteRecursively()
    }

    @Test
    fun `a second holder in this JVM gets the lock once the first lets go`() = runBlocking {
        val events = mutableListOf<String>()
        val firstHolds = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = async(Dispatchers.Default) {
            withFileLock(lockFile, 5.seconds) {
                synchronized(events) { events += "first in" }
                firstHolds.complete(Unit)
                release.await()
                synchronized(events) { events += "first out" }
            }
        }
        firstHolds.await()
        val second = async(Dispatchers.Default) { withFileLock(lockFile, 5.seconds) { synchronized(events) { events += "second in" } } }
        release.complete(Unit)
        first.await()
        second.await()

        assertEquals(listOf("first in", "first out", "second in"), events)
    }

    @Test
    fun `a lock held past the timeout fails with a reason`() = runBlocking {
        val holds = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val holder = async(Dispatchers.Default) {
            withFileLock(lockFile, 5.seconds) {
                holds.complete(Unit)
                release.await()
            }
        }
        holds.await()

        val failure = assertFailsWith<XcTestRunnerStartException> { withFileLock(lockFile, 300.milliseconds) { } }

        assertEquals("another plugin has been starting the XCTest runner for over 300ms; SIM-1.lock stays locked", failure.message)
        release.complete(Unit)
        holder.await()
    }
}
