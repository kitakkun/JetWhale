package com.kitakkun.jetwhale.plugins.mirror.host

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
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
    private val started = mutableListOf<FakeCompanionProcess>()
    private val commands = mutableListOf<List<String>>()
    private var nextPort = 10_000
    private var companionsReport = true

    /** Set to make `idb connect` run until its caller is cancelled, completing [connectReached] once it starts. */
    private var connectHangs = false
    private val connectReached = CompletableDeferred<Unit>()

    private var disconnectLaunchFails = false

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

        companions.stopCompanionEvenIfInUse("udid-1")

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
    fun `the host's exit kills every companion and has idb forget each one`() = runTest {
        val companions = companions()
        companions.acquire("udid-1")
        companions.acquire("udid-2")

        companions.destroyAllNow()

        assertTrue(started.filter { it.command.first() == "idb_companion" }.all(FakeCompanionProcess::destroyed))
        val disconnects = started.filter { it.command.getOrNull(1) == "disconnect" }
        assertEquals(setOf(listOf("idb", "disconnect", "localhost", "10000"), listOf("idb", "disconnect", "localhost", "10001")), disconnects.map(FakeCompanionProcess::command).toSet())
        assertTrue(disconnects.all(FakeCompanionProcess::destroyed), "a disconnect still running at the deadline is ended")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a caller cancelled while the companion starts leaves no companion running`() = runTest {
        val companions = companions()
        companionsReport = false
        val acquiring = launch { companions.acquire("udid-1") }
        runCurrent()

        acquiring.cancelAndJoin()

        assertTrue(started.single().destroyed)
        assertFalse(companions.isRunning("udid-1"))
    }

    @Test
    fun `the host's exit also kills a companion still starting, and starts none after it`() = runTest {
        val companions = companions()
        companionsReport = false
        val acquiring = launch { companions.acquire("udid-1") }
        while (started.isEmpty()) yield()

        companions.destroyAllNow()
        acquiring.cancelAndJoin()

        val (companionProcess, disconnectProcess) = started
        assertTrue(companionProcess.destroyed)
        assertEquals(listOf("idb", "disconnect", "localhost", "10000"), disconnectProcess.command)
        assertFailsWith<DeviceControlException> { companions.acquire("udid-2") }
        assertEquals(2, started.size)
    }

    @Test
    fun `an idb that cannot be launched to disconnect does not stop the exit from killing every companion`() = runTest {
        val companions = companions()
        companions.acquire("udid-1")
        companions.acquire("udid-2")
        disconnectLaunchFails = true

        companions.destroyAllNow()

        assertTrue(started.all(FakeCompanionProcess::destroyed))
    }

    @Test
    fun `a caller cancelled while idb connects to the companion has idb forget it`() = runTest {
        val companions = companions()
        connectHangs = true
        val acquiring = launch { companions.acquire("udid-1") }
        connectReached.await()

        acquiring.cancelAndJoin()

        val (companionProcess, disconnectProcess) = started
        assertTrue(companionProcess.destroyed)
        assertEquals(listOf("idb", "disconnect", "localhost", "10000"), disconnectProcess.command)
        assertFalse(companions.isRunning("udid-1"))
    }

    @Test
    fun `releasing everything stops every device's companion`() = runTest {
        val companions = companions()
        companions.acquire("udid-1")
        companions.acquire("udid-2")

        companions.releaseAll()

        assertTrue(started.all(FakeCompanionProcess::destroyed))
    }

    private fun TestScope.companions() = IdbCompanions(
        idbCompanionPath = "idb_companion",
        idbPath = "idb",
        launcher = { command ->
            if (disconnectLaunchFails && command[1] == "disconnect") throw deviceControlError("idb could not be launched")
            FakeCompanionProcess(command, readyLine = if (companionsReport) """{"grpc_port":${command.last()}}""" else null).also(started::add)
        },
        commands = { command ->
            commands += command
            if (connectHangs && command[1] == "connect") {
                connectReached.complete(Unit)
                awaitCancellation()
            }
        },
        ports = { nextPort++ },
        idleTimeout = idleTimeout,
        scope = backgroundScope,
    )
}

/**
 * A process whose output is [readyLine], if any, and that then stays open until destroyed, the
 * way idb_companion does.
 */
internal class FakeCompanionProcess(val command: List<String>, readyLine: String?) : Process() {
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
