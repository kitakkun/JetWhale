package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
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

class IphoneScreenCapturePoolTest {
    private val helpers = CopyOnWriteArrayList<FakeCaptureHelperProcess>()
    private val launcher = ProcessLauncher { command -> FakeCaptureHelperProcess(command) {}.also(helpers::add) }
    private val realScope = CoroutineScope(Job())

    private val idleTimeout = 30.seconds
    private val timeSource = TestTimeSource()

    @AfterTest
    fun exitHelpersAndCancelRealScope() {
        helpers.forEach { it.exit(0) }
        realScope.cancel()
    }

    @Test
    fun `the first user starts the helper for the iPhone and a second shares it`() = runTest {
        val pool = pool(this, failureRetentionPeriod = 5.seconds)

        val first = pool.acquire("udid-1", "Test iPhone")
        val second = pool.acquire("udid-1", "Test iPhone")

        assertSame(first, second)
        assertEquals(listOf(HELPER_EXECUTABLE.path, "--udid", "udid-1", "--name", "Test iPhone"), helpers.single().launchCommand)
    }

    @Test
    fun `a capture keeps running for the idle timeout after its last user and then closes the helper's stdin`() = runTest {
        val pool = pool(this, failureRetentionPeriod = 5.seconds)
        val capture = pool.acquire("udid-1", "Test iPhone")
        pool.acquire("udid-1", "Test iPhone")

        pool.release(capture)
        pool.release(capture)
        delay(idleTimeout - 1.seconds)
        assertFalse(helpers.single().stdinClosed)

        delay(2.seconds)
        assertTrue(helpers.single().stdinClosed)
    }

    @Test
    fun `a user who comes back within the idle timeout keeps the running capture`() = runTest {
        val pool = pool(this, failureRetentionPeriod = 5.seconds)
        val capture = pool.acquire("udid-1", "Test iPhone")
        pool.release(capture)
        delay(idleTimeout / 2)

        assertSame(capture, pool.acquire("udid-1", "Test iPhone"))
        delay(idleTimeout * 2)

        assertFalse(helpers.single().stdinClosed)
    }

    @Test
    fun `an iPhone that is gone has its capture stopped at once even while in use`() = runTest {
        val pool = pool(this, failureRetentionPeriod = 5.seconds)
        pool.acquire("udid-1", "Test iPhone")

        pool.stopCaptureEvenIfInUse("udid-1")

        assertTrue(helpers.single().stdinClosed)
    }

    @Test
    fun `stopping everything stops every iPhone's capture`() = runTest {
        val pool = pool(this, failureRetentionPeriod = 5.seconds)
        pool.acquire("udid-1", "Test iPhone")
        pool.acquire("udid-2", "Other iPhone")

        pool.stopAll()

        assertEquals(listOf(true, true), helpers.map(FakeCaptureHelperProcess::stdinClosed))
    }

    @Test
    fun `a started capture says so, and the frames' size once the first is sent`() = runBlocking {
        val capture = pool(realScope, failureRetentionPeriod = 5.seconds).acquire("udid-1", "Test iPhone")
        val helper = helpers.single()

        helper.reportCapturing(IntSize(1170, 2532))
        withTimeout(TEST_TIMEOUT) { capture.awaitStarted() }

        assertEquals(IntSize(1170, 2532), withTimeout(TEST_TIMEOUT) { capture.firstFrameSize.await() })
        assertEquals(IntSize(1170, 2532), capture.frameSize)
    }

    @Test
    fun `a reader asks the helper for a key frame and reads the access units from it on`() = runBlocking {
        val capture = pool(realScope, failureRetentionPeriod = 5.seconds).acquire("udid-1", "Test iPhone")
        val helper = helpers.single()

        val subscription = capture.subscribe()
        helper.send(DELTA_FRAME + KEY_FRAME + DELTA_FRAME)
        helper.exit(0)

        assertEquals(listOf("keyframe"), helper.commands)
        assertContentEquals(KEY_FRAME + DELTA_FRAME, subscription.stream.readAllBytes())
    }

    @Test
    fun `a helper that fails tells whoever waits for it to start what to do`() = runBlocking {
        val capture = pool(realScope, failureRetentionPeriod = 5.seconds).acquire("udid-1", "Test iPhone")
        val helper = helpers.single()

        helper.report("""{"event":"error","message":"no capture device showed the iPhone within 15 s","reason":"deviceNotFound"}""")
        helper.exit(IphoneCaptureHelperExit.DeviceNotFound.code)

        val failure = assertFailsWith<DeviceControlException> { withTimeout(TEST_TIMEOUT) { capture.awaitStarted() } }
        assertContains(failure.message.orEmpty(), "Connect it by USB")
    }

    @Test
    fun `after a failure the iPhone is not tried again until the failure retention period has passed`() = runBlocking {
        val pool = pool(realScope, failureRetentionPeriod = 5.seconds)
        val capture = pool.acquire("udid-1", "Test iPhone")
        helpers.single().exit(IphoneCaptureHelperExit.PermissionDenied.code)
        val failure = assertFailsWith<DeviceControlException> { withTimeout(TEST_TIMEOUT) { capture.awaitStarted() } }

        val retainedFailure = assertFailsWith<DeviceControlException> { pool.acquire("udid-1", "Test iPhone") }
        assertEquals(failure.message, retainedFailure.message)
        assertEquals(1, helpers.size)

        timeSource += 6.seconds
        pool.acquire("udid-1", "Test iPhone")
        assertEquals(2, helpers.size)
    }

    @Test
    fun `a stopped capture ends its readers and is no failure to try again after`() = runBlocking {
        val pool = pool(realScope, failureRetentionPeriod = 5.seconds)
        val capture = pool.acquire("udid-1", "Test iPhone")
        val subscription = capture.subscribe()

        pool.stopCaptureEvenIfInUse("udid-1")

        assertFailsWith<DeviceControlException> { withTimeout(TEST_TIMEOUT) { capture.awaitStarted() } }
        assertEquals(-1, subscription.stream.read())
        pool.acquire("udid-1", "Test iPhone")
        assertEquals(2, helpers.size)
    }

    private fun pool(scope: CoroutineScope, failureRetentionPeriod: Duration) = IphoneScreenCapturePool(launcher, idleTimeout, failureRetentionPeriod, timeSource, scope, CompletableDeferred(HELPER_EXECUTABLE))
}

private val HELPER_EXECUTABLE = File("/builds/jetwhale-iphone-capture")

private val TEST_TIMEOUT = 10.seconds

/** An IDR slice, then the delimiter that ends its access unit. */
private val KEY_FRAME = byteArrayOf(0, 0, 0, 1, 0x65, 0x11, 0, 0, 0, 1, 0x09, 0xF0.toByte())

/** A slice of a frame that refers to others, then the delimiter. */
private val DELTA_FRAME = byteArrayOf(0, 0, 0, 1, 0x41, 0x22, 0, 0, 0, 1, 0x09, 0xF0.toByte())
