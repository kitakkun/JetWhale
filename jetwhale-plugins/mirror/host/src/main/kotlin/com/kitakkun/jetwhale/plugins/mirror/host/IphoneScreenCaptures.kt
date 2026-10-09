package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.annotation.VisibleForTesting
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * The captures of iPhones' screens: one run per iPhone of the capture helper at [helperExecutable],
 * which is built when first awaited, shared by everything that reads the iPhone's screen. Starting
 * or stopping a capture switches the iPhone's USB connection over, which takes seconds, drops every
 * other connection to it, iproxy's among them, and turns its status bar to the 9:41 one, so a
 * capture nobody uses keeps running for [idleTimeout] before it stops.
 *
 * A run that failed is not started again for [failureReuse], measured by [timeSource]: its failure
 * is thrown to whoever asks meanwhile, so callers asking one after the other wait for the iPhone
 * once.
 *
 * The helper ends when its stdin closes, which also happens when the host exits, however it exits.
 */
internal class IphoneScreenCaptures(
    private val launcher: ProcessLauncher,
    private val idleTimeout: Duration,
    private val failureReuse: Duration,
    private val timeSource: TimeSource,
    private val scope: CoroutineScope,
    private val helperExecutable: Deferred<File>,
) {
    private class Held(val udid: String, val capture: IphoneScreenCapture) {
        var users = 0
        var idleStop: Job? = null
    }

    private val lock = Any()

    /** The runs by UDID, including one that ended, until the next run replaces it or it is let go. */
    private val held = HashMap<String, Held>()

    /**
     * The running capture of the iPhone [udid], called [name], or a new one; pair every call with
     * [release].
     *
     * @throws DeviceControlException when the helper cannot be built or started, or when the last
     * run failed within [failureReuse].
     */
    suspend fun acquire(udid: String, name: String): IphoneScreenCapture {
        synchronized(lock) { held[udid]?.capture?.failureWithin(failureReuse)?.let { throw it } }
        val executable = helperExecutable.await()
        return synchronized(lock) {
            val existing = held[udid]
            existing?.capture?.failureWithin(failureReuse)?.let { throw it }
            val current = existing?.takeUnless { it.capture.hasEnded } ?: Held(udid, startCapture(udid, name, executable)).also {
                existing?.idleStop?.cancel()
                held[udid] = it
            }
            current.idleStop?.cancel()
            current.idleStop = null
            current.users++
            current.capture
        }
    }

    fun release(capture: IphoneScreenCapture): Unit = synchronized(lock) {
        val current = held.values.firstOrNull { it.capture === capture } ?: return
        current.users--
        if (current.users > 0) return
        current.idleStop = scope.launch {
            delay(idleTimeout)
            synchronized(lock) {
                if (current.users > 0 || held[current.udid] !== current) return@launch
                held.remove(current.udid)
                current.capture.stop()
            }
        }
    }

    /** Whether the iPhone [udid]'s screen is being captured, its frames arriving. */
    fun isCapturing(udid: String): Boolean = synchronized(lock) { held[udid]?.capture?.takeUnless(IphoneScreenCapture::hasEnded)?.frameSize != null }

    /** Stops the capture of an iPhone that is gone, whoever still uses it: it has nothing left to show. */
    fun stopCaptureEvenIfInUse(udid: String): Unit = synchronized(lock) {
        val current = held.remove(udid) ?: return
        current.idleStop?.cancel()
        current.capture.stop()
    }

    /** Stops every capture, whoever still uses it; for when the plugin goes away. */
    fun stopAll(): Unit = synchronized(lock) {
        held.values.forEach {
            it.idleStop?.cancel()
            it.capture.stop()
        }
        held.clear()
    }

    private fun startCapture(udid: String, name: String, executable: File): IphoneScreenCapture = IphoneScreenCapture(launcher.start(listOf(executable.path, "--udid", udid, "--name", name)), timeSource)
}

/**
 * One run of the capture helper, showing one iPhone's screen to everyone reading it. Its stdout is
 * read as access units and handed to each [H264Subscription]; its stderr says when the capture
 * started, the frames' size, and why it failed. When it ended is told by [timeSource].
 */
internal class IphoneScreenCapture(private val process: Process, private val timeSource: TimeSource) {
    private class Ending(val failure: DeviceControlException?, val at: TimeMark)

    private val started = CompletableDeferred<Unit>()
    private val firstFrame = CompletableDeferred<IntSize>()
    private val subscriptions = mutableListOf<H264Subscription>()
    private var accessUnitsEnded = false
    private val stdinLock = Any()
    private val stopRequested = AtomicBoolean(false)
    private val stdinClosed = AtomicBoolean(false)

    @Volatile
    private var unreadableOutput: IOException? = null

    @Volatile
    private var ending: Ending? = null

    /** Whether the helper has exited, failed or stopped. */
    val hasEnded: Boolean get() = ending != null

    /** The size of the frames sent now; null until the first frame. */
    @Volatile
    var frameSize: IntSize? = null
        private set

    /**
     * The size of the first frame, once it has been sent. It fails as the run ends without one,
     * with what ended it.
     */
    val firstFrameSize: Deferred<IntSize> get() = firstFrame

    init {
        thread(isDaemon = true, name = "mirror-iphone-capture-frames", block = ::distributeAccessUnits)
        thread(isDaemon = true, name = "mirror-iphone-capture-log", block = ::readLogUntilExit)
    }

    /** The failure that ended this run, if it ended on one less than [window] ago. */
    fun failureWithin(window: Duration): DeviceControlException? = ending?.takeIf { it.at.elapsedNow() < window }?.failure

    /**
     * Waits until the capture session runs, which includes waiting for the user to answer macOS's
     * Camera prompt.
     *
     * @throws DeviceControlException when the run ends first, saying why.
     */
    suspend fun awaitStarted() {
        started.await()
    }

    /**
     * A new reader of the iPhone's H.264, starting at the next key frame, which the helper is asked
     * for so the reader starts from the current screen.
     */
    fun subscribe(): H264Subscription {
        val subscription = H264Subscription(MAX_QUEUED_BYTES)
        synchronized(subscriptions) { if (accessUnitsEnded) subscription.end() else subscriptions += subscription }
        writeCommand("keyframe")
        return subscription
    }

    /** Ends the run; see [closeStdinThenEscalate]. Returns at once. */
    fun stop() {
        stopRequested.set(true)
        closeStdinThenEscalate()
    }

    /**
     * Closes the helper's stdin, which ends its capture session and hands the iPhone's USB
     * connection back; a helper still running after a grace period is terminated, then killed.
     */
    private fun closeStdinThenEscalate() {
        if (!stdinClosed.compareAndSet(false, true)) return
        synchronized(stdinLock) {
            try {
                process.outputStream.close()
            } catch (_: IOException) {
                // The helper has already exited, so its stdin is closed anyway.
            }
        }
        thread(isDaemon = true, name = "mirror-iphone-capture-stop") {
            if (process.waitFor(STOP_GRACE_MILLIS, TimeUnit.MILLISECONDS)) return@thread
            process.destroy()
            if (!process.waitFor(STOP_GRACE_MILLIS, TimeUnit.MILLISECONDS)) process.destroyForcibly()
        }
    }

    private fun writeCommand(command: String) = synchronized(stdinLock) {
        try {
            process.outputStream.write("$command\n".toByteArray())
            process.outputStream.flush()
        } catch (_: IOException) {
            // The helper has exited or is stopping; its readers' streams end with it.
        }
    }

    private fun distributeAccessUnits() {
        val reader = AccessUnitReader(process.inputStream)
        try {
            while (true) {
                val unit = reader.readAccessUnit() ?: break
                synchronized(subscriptions) { subscriptions.removeAll { !it.offer(unit) } }
            }
        } catch (e: IOException) {
            unreadableOutput = e
            closeStdinThenEscalate()
        } finally {
            synchronized(subscriptions) {
                accessUnitsEnded = true
                subscriptions.forEach(H264Subscription::end)
                subscriptions.clear()
            }
        }
    }

    private fun readLogUntilExit() {
        val logTail = ArrayDeque<String>()
        var reportedFailure: String? = null
        try {
            process.errorStream.bufferedReader().forEachLine { line ->
                when (val event = parseIphoneCaptureEvent(line)) {
                    is IphoneCaptureEvent.Started -> started.complete(Unit)

                    is IphoneCaptureEvent.Format -> {
                        frameSize = event.size
                        firstFrame.complete(event.size)
                    }

                    is IphoneCaptureEvent.Failed -> reportedFailure = event.message

                    null -> {
                        logTail.addLast(line)
                        if (logTail.size > KEPT_LOG_LINES) logTail.removeFirst()
                    }
                }
            }
        } catch (_: IOException) {
            // The pipe closed as the helper exited; its exit code says the rest.
        }
        val exitCode = process.waitFor()
        val unreadable = unreadableOutput
        val failure = when {
            unreadable != null -> DeviceControlException("the iPhone capture helper sent H.264 that Device Mirror cannot read: ${unreadable.message}", unreadable)
            stopRequested.get() -> null
            else -> DeviceControlException(iphoneCaptureFailureMessage(exitCode, reportedFailure, logTail.toList()), null)
        }
        // Recorded before anyone waiting on this run hears, so a caller that tries again at once
        // finds the failure rather than a run that seems to be starting.
        ending = Ending(failure, timeSource.markNow())
        val cause = failure ?: deviceControlError("the iPhone's screen capture was stopped")
        started.completeExceptionally(cause)
        firstFrame.completeExceptionally(cause)
    }
}

/**
 * One reader of a capture: its access units from the first key frame on, as a stream of H.264 for
 * ffmpeg. At most [maxQueuedBytes] wait to be read; a reader that falls further behind has its stream
 * ended, and a new subscription starts it again from a key frame.
 */
internal class H264Subscription(private val maxQueuedBytes: Int) : Closeable {
    private val queue = LinkedBlockingQueue<ByteArray>()
    private val queuedBytes = AtomicInteger()
    private val ended = AtomicBoolean(false)

    /** Touched only by the thread that offers access units. */
    private var reachedKeyFrame = false

    /** The H.264 in Annex B form; closing it closes the subscription. */
    val stream: InputStream = object : InputStream() {
        private var current = ByteArray(0)
        private var position = 0

        override fun read(): Int {
            val single = ByteArray(1)
            return if (read(single, 0, 1) < 0) -1 else single[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (position == current.size) {
                val next = queue.take()
                if (next === END_OF_STREAM) {
                    queue.put(END_OF_STREAM)
                    return -1
                }
                queuedBytes.addAndGet(-next.size)
                current = next
                position = 0
            }
            val count = minOf(len, current.size - position)
            current.copyInto(b, destinationOffset = off, startIndex = position, endIndex = position + count)
            position += count
            return count
        }

        override fun available(): Int = current.size - position

        override fun close() = this@H264Subscription.close()
    }

    /** Queues [unit] for the reader; false once this subscription has ended, and should be dropped. */
    fun offer(unit: AccessUnit): Boolean {
        if (ended.get()) return false
        if (!reachedKeyFrame) {
            if (!unit.isKeyFrame) return true
            reachedKeyFrame = true
        }
        if (queuedBytes.get() + unit.bytes.size > maxQueuedBytes) {
            end()
            return false
        }
        queuedBytes.addAndGet(unit.bytes.size)
        queue.put(unit.bytes)
        return true
    }

    /** Ends the stream after what is queued. */
    fun end() {
        if (ended.compareAndSet(false, true)) queue.put(END_OF_STREAM)
    }

    /** Ends the stream at once, dropping what is queued. */
    override fun close() {
        if (!ended.compareAndSet(false, true)) return
        queue.clear()
        queue.put(END_OF_STREAM)
    }
}

/** What the capture helper reports on stderr, one JSON object per line. */
internal sealed interface IphoneCaptureEvent {
    /** The capture session runs. */
    data object Started : IphoneCaptureEvent

    /** The frames sent from now on are [size]. */
    data class Format(val size: IntSize) : IphoneCaptureEvent

    /** The helper is ending on a failure of [reason], which [message] explains. */
    data class Failed(val reason: String, val message: String) : IphoneCaptureEvent
}

/** The event on [line], or null for a line of the helper's log or an event this does not know. */
@VisibleForTesting
internal fun parseIphoneCaptureEvent(line: String): IphoneCaptureEvent? {
    if (!line.startsWith("{")) return null
    val event = try {
        Json.parseToJsonElement(line) as? JsonObject
    } catch (_: IllegalArgumentException) {
        null
    } ?: return null
    fun text(key: String) = (event[key] as? JsonPrimitive)?.contentOrNull
    fun number(key: String) = (event[key] as? JsonPrimitive)?.intOrNull
    return when (text("event")) {
        "started" -> IphoneCaptureEvent.Started

        "format" -> {
            val width = number("width") ?: return null
            val height = number("height") ?: return null
            IphoneCaptureEvent.Format(IntSize(width, height))
        }

        "error" -> IphoneCaptureEvent.Failed(reason = text("reason").orEmpty(), message = text("message").orEmpty())

        else -> null
    }
}

/** The capture helper's exit codes, as its `HelperExit` defines them. */
internal enum class IphoneCaptureExit(val code: Int) {
    Ended(0),
    Usage(2),
    DeviceNotFound(3),
    PermissionDenied(4),
    CaptureFailed(5),
}

/**
 * What to tell the user about a helper that exited with [exitCode] without being asked to, given the
 * failure it [reported] and the last lines of its log.
 */
@VisibleForTesting
internal fun iphoneCaptureFailureMessage(exitCode: Int, reported: String?, logTail: List<String>): String {
    val detail = reported?.takeIf(String::isNotBlank) ?: logTail.joinToString(" / ").takeLast(MAX_DETAIL_CHARS)
    return when (exitCode) {
        IphoneCaptureExit.DeviceNotFound.code -> "The iPhone's screen was not found among this Mac's capture devices ($detail). Connect it by USB, unlock it and trust this Mac."
        IphoneCaptureExit.PermissionDenied.code -> "macOS has not allowed Camera access, which reading an iPhone's screen over USB needs. Allow it for JetWhale Debugger, or for the terminal or IDE that started the host, in System Settings → Privacy & Security → Camera."
        IphoneCaptureExit.CaptureFailed.code -> "The iPhone's screen could not be captured: $detail"
        IphoneCaptureExit.Usage.code -> "The iPhone capture helper refused its arguments: $detail"
        IphoneCaptureExit.Ended.code -> "The iPhone capture helper ended on its own${if (detail.isEmpty()) "" else ": $detail"}"
        SIGABRT_EXIT, SIGKILL_EXIT -> "The iPhone capture helper was stopped by signal ${exitCode - SIGNAL_EXIT_BASE}. If it was asking for the Camera, the app that started the host declares no Camera usage, and macOS stops what asks on its behalf: reinstall JetWhale Debugger, or start the host from a terminal or IDE that declares one."
        else -> "The iPhone capture helper ended with exit code $exitCode${if (detail.isEmpty()) "" else ": $detail"}"
    }
}

/** A process the JVM saw end on a signal exits with this plus the signal's number. */
private const val SIGNAL_EXIT_BASE = 128

private const val SIGABRT_EXIT = SIGNAL_EXIT_BASE + 6

private const val SIGKILL_EXIT = SIGNAL_EXIT_BASE + 9

private val END_OF_STREAM = ByteArray(0)

/** Seconds of a scrolling screen's H.264: a reader further behind than this is not keeping up. */
private const val MAX_QUEUED_BYTES = 16 * 1024 * 1024

/** How long the helper gets to stop its capture session after each step of stopping it. */
private const val STOP_GRACE_MILLIS = 3_000L

private const val KEPT_LOG_LINES = 20

private const val MAX_DETAIL_CHARS = 500
