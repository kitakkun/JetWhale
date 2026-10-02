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
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.nio.charset.Charset
import kotlin.time.Clock

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultLogCaptureService : LogCaptureService {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    override val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

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
            _logs.value = emptyList()
        }
    }

    private inner class CapturingPrintStream(
        private val original: PrintStream,
        private val level: LogLevel,
    ) : PrintStream(
        object : OutputStream() {
            private val line = ByteArrayOutputStream()

            // A line is decoded only once it is whole, so a character split across writes survives.
            override fun write(b: Int) {
                original.write(b)
                if (b == '\n'.code) {
                    addLogEntry(line.toString(Charset.defaultCharset()), level)
                    line.reset()
                } else {
                    line.write(b)
                }
            }

            override fun flush() {
                original.flush()
            }
        },
    ) {
        override fun println(x: String?) {
            x?.let { addLogEntry(it, level) }
            original.println(x)
        }

        override fun println(x: Any?) {
            x?.toString()?.let { addLogEntry(it, level) }
            original.println(x)
        }

        override fun print(x: String?) {
            original.print(x)
        }

        override fun print(x: Any?) {
            original.print(x)
        }
    }

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
            _logs.value = entries.toList()
        }
    }
}
