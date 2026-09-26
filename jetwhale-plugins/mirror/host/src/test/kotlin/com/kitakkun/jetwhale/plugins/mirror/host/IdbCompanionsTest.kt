package com.kitakkun.jetwhale.plugins.mirror.host

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class IdbCompanionsTest {
    private val started = mutableListOf<FakeProcess>()
    private val commands = mutableListOf<List<String>>()
    private var nextPort = 10_000
    private var companionsReport = true

    private val idleTimeout = 3.minutes

    @Test
    fun `the first user starts the device's companion and tells idb about it`() = runTest {
        val companions = companions()
        companions.acquire("udid-1")

        assertEquals(listOf("idb_companion", "--udid", "udid-1", "--grpc-port", "10000"), started.single().command)
        assertEquals(listOf(listOf("idb", "connect", "localhost", "10000")), commands)
        assertTrue(companions.isRunning("udid-1"))
    }

    @Test
    fun `a second user shares the running companion`() = runTest {
        val companions = companions()
        companions.acquire("udid-1")
        companions.acquire("udid-1")

        assertEquals(1, started.size)
    }

    @Test
    fun `the companion stops once its last user has been gone for the idle timeout`() = runTest {
        val companions = companions()
        companions.acquire("udid-1")
        companions.acquire("udid-1")

        companions.release("udid-1")
        companions.release("udid-1")
        delay(idleTimeout - 1.seconds)
        assertFalse(started.single().destroyed)

        delay(2.seconds)
        assertTrue(started.single().destroyed)
        assertEquals(listOf("idb", "disconnect", "localhost", "10000"), commands.last())
        assertFalse(companions.isRunning("udid-1"))
    }

    @Test
    fun `switching back within the idle timeout reuses the running companion`() = runTest {
        val companions = companions()
        companions.acquire("udid-1")
        companions.release("udid-1")
        delay(idleTimeout / 2)

        companions.acquire("udid-1")
        delay(idleTimeout * 2)

        assertEquals(1, started.size)
        assertFalse(started.single().destroyed)
        assertEquals(1, commands.count { it.getOrNull(1) == "connect" })
    }

    @Test
    fun `a device that disappears has its companion stopped at once even while in use`() = runTest {
        val companions = companions()
        companions.acquire("udid-1")

        companions.forget("udid-1")

        assertTrue(started.single().destroyed)
        assertFalse(companions.isRunning("udid-1"))
    }

    @Test
    fun `a companion that never reports its port is killed and the failure explained`() = runTest {
        val companions = companions()
        companionsReport = false

        assertFailsWith<DeviceControlException> { companions.acquire("udid-1") }

        assertTrue(started.single().destroyed)
        assertFalse(companions.isRunning("udid-1"))
        assertTrue(commands.isEmpty())
    }

    @Test
    fun `releasing everything stops every device's companion`() = runTest {
        val companions = companions()
        companions.acquire("udid-1")
        companions.acquire("udid-2")

        companions.releaseAll()

        assertTrue(started.all(FakeProcess::destroyed))
    }

    private fun TestScope.companions() = IdbCompanions(
        idbCompanion = "idb_companion",
        idb = "idb",
        launcher = { command ->
            FakeProcess(command, readyLine = if (companionsReport) """{"grpc_port":${command.last()}}""" else null).also(started::add)
        },
        commands = { command -> commands += command },
        ports = { nextPort++ },
        idleTimeout = idleTimeout,
        scope = backgroundScope,
    )
}

/**
 * A process whose output is [readyLine], if any, and that then stays open until destroyed, the
 * way idb_companion does.
 */
private class FakeProcess(val command: List<String>, readyLine: String?) : Process() {
    private val pipe = PipedOutputStream()
    private val output = PipedInputStream(pipe)
    var destroyed = false
        private set

    init {
        readyLine?.let { pipe.write("$it\n".toByteArray()) }
        pipe.flush()
    }

    override fun getInputStream(): InputStream = output

    override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))

    override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()

    override fun waitFor(): Int = 0

    override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = destroyed

    override fun exitValue(): Int = 0

    override fun destroy() {
        destroyed = true
        pipe.close()
    }

    override fun destroyForcibly(): Process = apply { destroy() }
}
