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
import java.io.OutputStream
import java.io.PrintStream
import kotlin.time.Clock

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultLogCaptureService : LogCaptureService {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    override val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

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
        _logs.value = emptyList()
    }

    private inner class CapturingPrintStream(
        private val original: PrintStream,
        private val level: LogLevel,
    ) : PrintStream(
        object : OutputStream() {
            private val buffer = StringBuilder()

            override fun write(b: Int) {
                if (b == '\n'.code) {
                    flush()
                } else {
                    buffer.append(b.toChar())
                }
            }

            override fun flush() {
                if (buffer.isNotEmpty()) {
                    val message = buffer.toString()
                    buffer.clear()
                    addLogEntry(message, level)
                    original.print(message)
                    original.flush()
                }
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

        val entry = LogEntry(
            timestamp = Clock.System.now(),
            message = message.trim(),
            level = level,
        )

        _logs.value = (_logs.value + entry).takeLast(maxLogEntries)
    }
}
