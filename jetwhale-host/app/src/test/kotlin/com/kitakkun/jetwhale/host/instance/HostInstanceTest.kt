package com.kitakkun.jetwhale.host.instance

import com.kitakkun.jetwhale.host.release.HeldLock
import com.kitakkun.jetwhale.host.release.HostInstanceRecord
import com.kitakkun.jetwhale.host.release.HostVersionsDirectory
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
import kotlin.time.Duration.Companion.seconds

class HostInstanceTest {
    private val versions = HostVersionsDirectory(Files.createTempDirectory("host-instance").resolve("host"))
    private val locks = InProcessLocks()

    @Test
    fun `publishes its record only when it is up`() {
        val instance = assertIs<HostInstanceClaim.Claimed>(HostInstance.claim(versions, locks)).instance

        assertNull(HostInstanceRecord.read(versions))

        instance.publish()

        assertEquals(ProcessHandle.current().pid(), assertNotNull(HostInstanceRecord.read(versions)).pid)
    }

    @Test
    fun `comes forward for a request with its token`() = runBlocking {
        val instance = assertIs<HostInstanceClaim.Claimed>(HostInstance.claim(versions, locks)).instance
        instance.publish()
        val request = async(start = CoroutineStart.UNDISPATCHED) { instance.activationRequests.first() }

        assertEquals(true, HostInstanceRecord.requestActivation(versions, 2.seconds))
        assertEquals(Unit, withTimeout(5.seconds) { request.await() })
    }

    @Test
    fun `a second host asks the first to come forward instead of claiming`() = runBlocking {
        val first = assertIs<HostInstanceClaim.Claimed>(HostInstance.claim(versions, locks)).instance
        first.publish()
        val request = async(start = CoroutineStart.UNDISPATCHED) { first.activationRequests.first() }

        assertEquals(HostInstanceClaim.HeldByAnother(activated = true), HostInstance.claim(versions, locks))
        assertEquals(Unit, withTimeout(5.seconds) { request.await() })
    }

    @Test
    fun `refuses a request with another token`() {
        assertIs<HostInstanceClaim.Claimed>(HostInstance.claim(versions, locks)).instance.publish()
        val record = assertNotNull(HostInstanceRecord.read(versions))

        val answer = Socket(InetAddress.getLoopbackAddress(), record.port).use { socket ->
            socket.getOutputStream().write("activate ${record.token.reversed()}\n".toByteArray())
            socket.getInputStream().bufferedReader().readLine()
        }

        assertEquals("denied", answer)
    }

    @Test
    fun `refuses a request longer than any it takes, and still answers the next`() {
        assertIs<HostInstanceClaim.Claimed>(HostInstance.claim(versions, locks)).instance.publish()
        val record = assertNotNull(HostInstanceRecord.read(versions))

        val answer = Socket(InetAddress.getLoopbackAddress(), record.port).use { socket ->
            socket.getOutputStream().write("activate ${"x".repeat(4096)}".toByteArray())
            socket.getInputStream().bufferedReader().readLine()
        }

        assertEquals("denied", answer)
        assertEquals(true, HostInstanceRecord.requestActivation(versions, 2.seconds))
    }

    @Test
    fun `keeps the instance lock when it stops taking requests`() {
        val instance = assertIs<HostInstanceClaim.Claimed>(HostInstance.claim(versions, locks)).instance
        instance.publish()
        val server = HostInstance::class.java.getDeclaredField("server").apply { isAccessible = true }.get(instance) as ServerSocket

        server.close()

        assertEquals(false, HostInstanceRecord.requestActivation(versions, 1.seconds))
        assertNull(locks.tryLock(versions.instanceLock))
    }

    /** OS file locks as two processes would see them, for two hosts in one test JVM. */
    private class InProcessLocks : LockFiles {
        private val held: MutableSet<Path> = ConcurrentHashMap.newKeySet()

        override fun lock(path: Path): HeldLock = checkNotNull(tryLock(path))

        override fun tryLock(path: Path): HeldLock? = if (held.add(path)) HeldLock { held.remove(path) } else null
    }
}
