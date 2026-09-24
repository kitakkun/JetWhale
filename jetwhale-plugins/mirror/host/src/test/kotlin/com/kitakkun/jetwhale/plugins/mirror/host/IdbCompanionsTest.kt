package com.kitakkun.jetwhale.plugins.mirror.host

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

class IdbCompanionsTest {
    private val started = mutableListOf<FakeProcess>()
    private val commands = mutableListOf<List<String>>()
    private var nextPort = 10_000
    private var companionsReport = true

    private val companions = IdbCompanions(
        idbCompanion = "idb_companion",
        idb = "idb",
        launcher = { command ->
            FakeProcess(command, readyLine = if (companionsReport) """{"grpc_port":${command.last()}}""" else null).also(started::add)
        },
        commands = { command -> commands += command },
        ports = { nextPort++ },
    )

    @Test
    fun `the first user starts the device's companion and tells idb about it`() = runTest {
        companions.acquire("udid-1")

        assertEquals(listOf("idb_companion", "--udid", "udid-1", "--grpc-port", "10000"), started.single().command)
        assertEquals(listOf(listOf("idb", "connect", "localhost", "10000")), commands)
        assertTrue(companions.isRunning("udid-1"))
    }

    @Test
    fun `a second user shares the running companion`() = runTest {
        companions.acquire("udid-1")
        companions.acquire("udid-1")

        assertEquals(1, started.size)
    }

    @Test
    fun `the companion stops when its last user releases it and not before`() = runTest {
        companions.acquire("udid-1")
        companions.acquire("udid-1")

        companions.release("udid-1")
        assertFalse(started.single().destroyed)

        companions.release("udid-1")
        assertTrue(started.single().destroyed)
        assertEquals(listOf("idb", "disconnect", "localhost", "10000"), commands.last())
        assertFalse(companions.isRunning("udid-1"))
    }

    @Test
    fun `a companion that never reports its port is killed and the failure explained`() = runTest {
        companionsReport = false

        assertFailsWith<DeviceControlException> { companions.acquire("udid-1") }

        assertTrue(started.single().destroyed)
        assertFalse(companions.isRunning("udid-1"))
        assertTrue(commands.isEmpty())
    }

    @Test
    fun `releasing everything stops every device's companion`() = runTest {
        companions.acquire("udid-1")
        companions.acquire("udid-2")

        companions.releaseAll()

        assertTrue(started.all(FakeProcess::destroyed))
    }
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
