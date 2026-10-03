package com.kitakkun.jetwhale.host.data

import com.kitakkun.jetwhale.host.model.LogCaptureService
import com.kitakkun.jetwhale.host.model.LogEntry
import com.kitakkun.jetwhale.host.model.LogLevel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.nio.charset.Charset
import kotlin.time.Clock

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultLogCaptureService : LogCaptureService {
    override val logs: StateFlow<List<LogEntry>>
        field = MutableStateFlow<List<LogEntry>>(emptyList())

    // Lines arrive from stdout and stderr on any thread; the lock keeps every one of them.
    private val entries = ArrayDeque<LogEntry>()
    private var nextEntryId = 0L

    private var originalOut: PrintStream? = null
    private var originalErr: PrintStream? = null
    private var isCapturing = false

    private val maxLogEntries = 10000

    override fun startCapture() {
        if (isCapturing) return

        val previousOut = System.out
        val previousErr = System.err
        originalOut = previousOut
        originalErr = previousErr

        System.setOut(CapturingPrintStream(previousOut, LogLevel.INFO))
        System.setErr(CapturingPrintStream(previousErr, LogLevel.ERROR))

        isCapturing = true
    }

    override fun stopCapture() {
        if (!isCapturing) return

        originalOut?.let(System::setOut)
        originalErr?.let(System::setErr)

        isCapturing = false
    }

    override fun clearLogs() {
        synchronized(entries) {
            entries.clear()
            logs.value = emptyList()
        }
    }

    // Logback writes bytes in the default charset, and so does this stream for every print, so one
    // decoder reads every line; the terminal then gets each line through the original stream, which
    // encodes it in the console's own charset.
    private inner class CapturingPrintStream(
        original: PrintStream,
        level: LogLevel,
    ) : PrintStream(
        object : OutputStream() {
            private val line = ByteArrayOutputStream()

            override fun write(b: Int) {
                if (b == '\n'.code) endLine() else line.write(b)
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                var start = off
                for (i in off until off + len) {
                    if (b[i] == '\n'.code.toByte()) {
                        line.write(b, start, i - start)
                        endLine()
                        start = i + 1
                    }
                }
                line.write(b, start, off + len - start)
            }

            override fun flush() {
                original.flush()
            }

            // A line is decoded only once it is whole, so a character split across writes survives.
            private fun endLine() {
                val text = line.toString(Charset.defaultCharset())
                line.reset()
                original.print(text)
                original.print('\n')
                addLogEntry(text, level)
            }
        },
        true,
        Charset.defaultCharset(),
    )

    private fun addLogEntry(message: String, level: LogLevel) {
        if (message.isBlank()) return

        synchronized(entries) {
            if (entries.size == maxLogEntries) entries.removeFirst()
            entries.addLast(
                LogEntry(
                    id = nextEntryId++,
                    timestamp = Clock.System.now(),
                    message = message.trim(),
                    level = level,
                ),
            )
            logs.value = entries.toList()
        }
    }
}
