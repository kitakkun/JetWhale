package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class IphoneScreenCapturesTest {
    private val launched = CopyOnWriteArrayList<FakeHelperProcess>()
    private val launcher = ProcessLauncher { command -> FakeHelperProcess(command).also(launched::add) }
    private val realScope = CoroutineScope(Job())

    private val idleTimeout = 30.seconds
    private val timeSource = TestTimeSource()

    @AfterTest
    fun cleanUp() {
        launched.forEach { it.exit(0) }
        realScope.cancel()
    }

    @Test
    fun `the first user starts the helper for the iPhone and a second shares it`() = runTest {
        val captures = captures(this, failureReuse = 5.seconds)

        val first = captures.acquire("udid-1", "Test iPhone")
        val second = captures.acquire("udid-1", "Test iPhone")

        assertSame(first, second)
        assertEquals(listOf(HELPER.path, "--udid", "udid-1", "--name", "Test iPhone"), launched.single().command)
    }

    @Test
    fun `a capture keeps running for the idle timeout after its last user and then closes the helper's stdin`() = runTest {
        val captures = captures(this, failureReuse = 5.seconds)
        val capture = captures.acquire("udid-1", "Test iPhone")
        captures.acquire("udid-1", "Test iPhone")

        captures.release(capture)
        captures.release(capture)
        delay(idleTimeout - 1.seconds)
        assertFalse(launched.single().stdinClosed)

        delay(2.seconds)
        assertTrue(launched.single().stdinClosed)
    }

    @Test
    fun `a user who comes back within the idle timeout keeps the running capture`() = runTest {
        val captures = captures(this, failureReuse = 5.seconds)
        val capture = captures.acquire("udid-1", "Test iPhone")
        captures.release(capture)
        delay(idleTimeout / 2)

        assertSame(capture, captures.acquire("udid-1", "Test iPhone"))
        delay(idleTimeout * 2)

        assertFalse(launched.single().stdinClosed)
    }

    @Test
    fun `an iPhone that is gone has its capture stopped at once even while in use`() = runTest {
        val captures = captures(this, failureReuse = 5.seconds)
        captures.acquire("udid-1", "Test iPhone")

        captures.stopCaptureEvenIfInUse("udid-1")

        assertTrue(launched.single().stdinClosed)
    }

    @Test
    fun `stopping everything stops every iPhone's capture`() = runTest {
        val captures = captures(this, failureReuse = 5.seconds)
        captures.acquire("udid-1", "Test iPhone")
        captures.acquire("udid-2", "Other iPhone")

        captures.stopAll()

        assertEquals(listOf(true, true), launched.map(FakeHelperProcess::stdinClosed))
    }

    @Test
    fun `a started capture says so, and the frames' size once the first is sent`() = runBlocking {
        val capture = captures(realScope, failureReuse = 5.seconds).acquire("udid-1", "Test iPhone")
        val helper = launched.single()

        helper.report("""{"event":"started","matchedBy":"name","name":"Test iPhone","uniqueId":"capture-1"}""")
        withTimeout(TEST_TIMEOUT) { capture.awaitStarted() }
        helper.report("""{"event":"format","height":2532,"passthrough":true,"width":1170}""")

        assertEquals(IntSize(1170, 2532), withTimeout(TEST_TIMEOUT) { capture.firstFrameSize.await() })
        assertEquals(IntSize(1170, 2532), capture.frameSize)
    }

    @Test
    fun `a reader asks the helper for a key frame and reads the access units from it on`() = runBlocking {
        val capture = captures(realScope, failureReuse = 5.seconds).acquire("udid-1", "Test iPhone")
        val helper = launched.single()

        val subscription = capture.subscribe()
        helper.send(DELTA_FRAME + KEY_FRAME + DELTA_FRAME)
        helper.exit(0)

        assertEquals("keyframe\n", helper.stdinText)
        assertContentEquals(KEY_FRAME + DELTA_FRAME, subscription.stream.readAllBytes())
    }

    @Test
    fun `a helper that fails tells whoever waits for it to start what to do`() = runBlocking {
        val capture = captures(realScope, failureReuse = 5.seconds).acquire("udid-1", "Test iPhone")
        val helper = launched.single()

        helper.report("""{"event":"error","message":"no capture device showed the iPhone within 15 s","reason":"deviceNotFound"}""")
        helper.exit(IphoneCaptureExit.DeviceNotFound.code)

        val failure = assertFailsWith<DeviceControlException> { withTimeout(TEST_TIMEOUT) { capture.awaitStarted() } }
        assertContains(failure.message.orEmpty(), "Connect it by USB")
    }

    @Test
    fun `after a failure the iPhone is not tried again until the failure reuse has passed`() = runBlocking {
        val captures = captures(realScope, failureReuse = 5.seconds)
        val capture = captures.acquire("udid-1", "Test iPhone")
        launched.single().exit(IphoneCaptureExit.PermissionDenied.code)
        val failure = assertFailsWith<DeviceControlException> { withTimeout(TEST_TIMEOUT) { capture.awaitStarted() } }

        val reused = assertFailsWith<DeviceControlException> { captures.acquire("udid-1", "Test iPhone") }
        assertEquals(failure.message, reused.message)
        assertEquals(1, launched.size)

        timeSource += 6.seconds
        captures.acquire("udid-1", "Test iPhone")
        assertEquals(2, launched.size)
    }

    @Test
    fun `a stopped capture ends its readers and is no failure to try again after`() = runBlocking {
        val captures = captures(realScope, failureReuse = 5.seconds)
        val capture = captures.acquire("udid-1", "Test iPhone")
        val subscription = capture.subscribe()

        captures.stopCaptureEvenIfInUse("udid-1")

        assertFailsWith<DeviceControlException> { withTimeout(TEST_TIMEOUT) { capture.awaitStarted() } }
        assertEquals(-1, subscription.stream.read())
        captures.acquire("udid-1", "Test iPhone")
        assertEquals(2, launched.size)
    }

    private fun captures(scope: CoroutineScope, failureReuse: Duration) = IphoneScreenCaptures(launcher, idleTimeout, failureReuse, timeSource, scope) { HELPER }
}

private val HELPER = File("/builds/jetwhale-iphone-capture")

private val TEST_TIMEOUT = 10.seconds

/** An IDR slice, then the delimiter that ends its access unit. */
private val KEY_FRAME = byteArrayOf(0, 0, 0, 1, 0x65, 0x11, 0, 0, 0, 1, 0x09, 0xF0.toByte())

/** A slice of a frame that refers to others, then the delimiter. */
private val DELTA_FRAME = byteArrayOf(0, 0, 0, 1, 0x41, 0x22, 0, 0, 0, 1, 0x09, 0xF0.toByte())

/**
 * A capture helper that writes what the test sends, and ends when its stdin closes, as the helper
 * does, or when the test makes it exit.
 */
private class FakeHelperProcess(val command: List<String>) : Process() {
    private val stdout = QueueInputStream()
    private val stderr = QueueInputStream()
    private val exited = CountDownLatch(1)

    @Volatile
    private var exitCode = 0

    @Volatile
    var stdinClosed = false
        private set

    private val stdin = object : ByteArrayOutputStream() {
        override fun close() {
            stdinClosed = true
            exit(0)
        }
    }

    val stdinText: String get() = synchronized(stdin) { stdin.toString() }

    fun report(line: String) = stderr.add("$line\n".toByteArray())

    fun send(bytes: ByteArray) = stdout.add(bytes)

    fun exit(code: Int) {
        synchronized(exited) {
            if (exited.count == 0L) return
            exitCode = code
            stdout.end()
            stderr.end()
            exited.countDown()
        }
    }

    override fun getOutputStream(): OutputStream = stdin

    override fun getInputStream(): InputStream = stdout

    override fun getErrorStream(): InputStream = stderr

    override fun waitFor(): Int {
        exited.await()
        return exitCode
    }

    override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = exited.await(timeout, unit)

    override fun exitValue(): Int = exitCode

    override fun destroy() = exit(143)
}

/** A pipe the test writes chunks into, read until [end]. */
private class QueueInputStream : InputStream() {
    private val chunks = LinkedBlockingQueue<ByteArray>()
    private var current = ByteArray(0)
    private var position = 0

    fun add(bytes: ByteArray) = chunks.put(bytes)

    fun end() = chunks.put(END)

    override fun read(): Int {
        val single = ByteArray(1)
        return if (read(single, 0, 1) < 0) -1 else single[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (position == current.size) {
            val next = chunks.take()
            if (next === END) {
                chunks.put(END)
                return -1
            }
            current = next
            position = 0
        }
        val count = minOf(len, current.size - position)
        current.copyInto(b, destinationOffset = off, startIndex = position, endIndex = position + count)
        position += count
        return count
    }

    private companion object {
        val END = ByteArray(0)
    }
}
