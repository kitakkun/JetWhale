package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

/**
 * The iPhone capture helper as a test plays it: what it reports and sends is the test's to say, and
 * each line written to its stdin goes to [onCommand]. It ends when its stdin closes, as the helper
 * does, or when the test makes it [exit].
 */
internal class FakeCaptureHelperProcess(val command: List<String>, private val onCommand: FakeCaptureHelperProcess.(String) -> Unit) : Process() {
    private val stdout = QueueInputStream()
    private val stderr = QueueInputStream()
    private val exited = CountDownLatch(1)
    private val stdinClosedLatch = CountDownLatch(1)

    @Volatile
    private var exitCode = 0

    /** The lines written to the helper's stdin. */
    val commands: MutableList<String> = CopyOnWriteArrayList()

    val stdinClosed: Boolean get() = stdinClosedLatch.count == 0L

    private val stdin = object : OutputStream() {
        private val line = StringBuilder()

        override fun write(b: Int) {
            if (b == '\n'.code) {
                val command = line.toString()
                line.clear()
                commands += command
                onCommand(command)
            } else {
                line.append(b.toChar())
            }
        }

        override fun close() {
            stdinClosedLatch.countDown()
            exit(IphoneCaptureExit.Ended.code)
        }
    }

    fun awaitStdinClosed(timeout: Duration): Boolean = stdinClosedLatch.await(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)

    fun report(line: String) = stderr.add("$line\n".toByteArray())

    /** Reports that the capture session runs and sends frames of [size]. */
    fun reportCapturing(size: IntSize) {
        report("""{"event":"started","matchedBy":"usbSerialNumber","name":"Test iPhone","uniqueId":"capture-1"}""")
        report("""{"event":"format","height":${size.height},"passthrough":true,"width":${size.width}}""")
    }

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

    override fun destroy() = exit(SIGTERM_EXIT)
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

/** What the JVM reports for a process ended by SIGTERM. */
private const val SIGTERM_EXIT = 143
