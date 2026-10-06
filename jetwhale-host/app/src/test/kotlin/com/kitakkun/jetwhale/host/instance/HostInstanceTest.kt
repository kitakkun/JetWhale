package com.kitakkun.jetwhale.host.instance

import com.kitakkun.jetwhale.host.release.BringToFrontClient
import com.kitakkun.jetwhale.host.release.HeldLock
import com.kitakkun.jetwhale.host.release.HostDirectory
import com.kitakkun.jetwhale.host.release.LockFiles
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class HostInstanceTest {
    private val hostDirectory = HostDirectory(Files.createTempDirectory("host-instance").resolve("host"))
    private val bringToFrontClient = BringToFrontClient(hostDirectory)
    private val lockFiles = InProcessLockFiles()

    @Test
    fun `publishes its instance JSON only when it is up`() {
        val instance = assertIs<HostInstanceClaim.Claimed>(HostInstance.claim(hostDirectory, lockFiles)).instance

        assertNull(hostDirectory.readInstanceJson())

        instance.publishInstanceJson()

        assertEquals(ProcessHandle.current().pid(), assertNotNull(hostDirectory.readInstanceJson()).pid)
    }

    @Test
    fun `comes forward for a request with its token`() = runBlocking {
        val instance = assertIs<HostInstanceClaim.Claimed>(HostInstance.claim(hostDirectory, lockFiles)).instance
        instance.publishInstanceJson()
        val request = async(start = CoroutineStart.UNDISPATCHED) { instance.bringToFrontRequests.first() }

        assertEquals(true, bringToFrontClient.requestBringToFront(2.seconds))
        assertEquals(Unit, withTimeout(5.seconds) { request.await() })
    }

    @Test
    fun `keeps a request that arrives before the window collects them`() = runBlocking {
        val instance = assertIs<HostInstanceClaim.Claimed>(HostInstance.claim(hostDirectory, lockFiles)).instance
        instance.publishInstanceJson()

        assertEquals(true, bringToFrontClient.requestBringToFront(2.seconds))
        assertEquals(Unit, withTimeout(5.seconds) { instance.bringToFrontRequests.first() })
    }

    @Test
    fun `a second host asks the first to come forward instead of claiming`() = runBlocking {
        val first = assertIs<HostInstanceClaim.Claimed>(HostInstance.claim(hostDirectory, lockFiles)).instance
        first.publishInstanceJson()
        val request = async(start = CoroutineStart.UNDISPATCHED) { first.bringToFrontRequests.first() }

        assertEquals(HostInstanceClaim.HeldByAnother(broughtToFront = true), HostInstance.claim(hostDirectory, lockFiles))
        assertEquals(Unit, withTimeout(5.seconds) { request.await() })
    }

    @Test
    fun `refuses a request with another token`() {
        assertIs<HostInstanceClaim.Claimed>(HostInstance.claim(hostDirectory, lockFiles)).instance.publishInstanceJson()
        val instanceJson = assertNotNull(hostDirectory.readInstanceJson())

        val answer = Socket(InetAddress.getLoopbackAddress(), instanceJson.port).use { socket ->
            socket.getOutputStream().write("bring-to-front ${instanceJson.token.reversed()}\n".toByteArray())
            socket.getInputStream().bufferedReader().readLine()
        }

        assertEquals("denied", answer)
    }

    @Test
    fun `refuses a request longer than any it takes, and still answers the next`() {
        assertIs<HostInstanceClaim.Claimed>(HostInstance.claim(hostDirectory, lockFiles)).instance.publishInstanceJson()
        val instanceJson = assertNotNull(hostDirectory.readInstanceJson())

        val answer = Socket(InetAddress.getLoopbackAddress(), instanceJson.port).use { socket ->
            socket.getOutputStream().write("bring-to-front ${"x".repeat(4096)}".toByteArray())
            socket.getInputStream().bufferedReader().readLine()
        }

        assertEquals("denied", answer)
        assertEquals(true, bringToFrontClient.requestBringToFront(2.seconds))
    }

    @Test
    fun `keeps the instance lock when it stops taking requests`() {
        val bringToFrontThreadsOfEarlierTests = bringToFrontThreads()
        val instance = assertIs<HostInstanceClaim.Claimed>(HostInstance.claim(hostDirectory, lockFiles)).instance
        val bringToFrontThread = (bringToFrontThreads() - bringToFrontThreadsOfEarlierTests).single()
        val server = HostInstance::class.java.getDeclaredField("server").apply { isAccessible = true }.get(instance) as ServerSocket

        server.close()

        assertTrue(bringToFrontThread.join(5.seconds.toJavaDuration()))
        assertNull(lockFiles.tryLock(hostDirectory.instanceLockFile))
    }

    private fun bringToFrontThreads(): Set<Thread> = Thread.getAllStackTraces().keys.filterTo(mutableSetOf()) { it.name == "host-instance-bring-to-front" }

    /** OS file locks as two processes would see them, for two hosts in one test JVM. */
    private class InProcessLockFiles : LockFiles {
        private val held: MutableSet<Path> = ConcurrentHashMap.newKeySet()

        override fun lock(path: Path): HeldLock = checkNotNull(tryLock(path))

        override fun tryLock(path: Path): HeldLock? = if (held.add(path)) HeldLock { held.remove(path) } else null
    }
}
