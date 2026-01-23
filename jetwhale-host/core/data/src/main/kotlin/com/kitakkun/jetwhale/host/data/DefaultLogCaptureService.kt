package com.kitakkun.jetwhale.host.data

import com.kitakkun.jetwhale.host.model.LogCaptureService
import com.kitakkun.jetwhale.host.model.LogEntry
import com.kitakkun.jetwhale.host.model.LogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import java.io.OutputStream
import java.io.PrintStream

class DefaultLogCaptureService : LogCaptureService {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    override val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private var originalOut: PrintStream? = null
    private var originalErr: PrintStream? = null
    private var isCapturing = false

    private val maxLogEntries = 10000 // Limit to prevent memory issues

    override fun startCapture() {
        if (isCapturing) return

        originalOut = System.out
        originalErr = System.err

        val captureOut = CapturingPrintStream(originalOut!!, LogLevel.INFO)
        val captureErr = CapturingPrintStream(originalErr!!, LogLevel.ERROR)

        System.setOut(captureOut)
        System.setErr(captureErr)

        isCapturing = true
    }

    override fun stopCapture() {
        if (!isCapturing) return

        originalOut?.let { System.setOut(it) }
        originalErr?.let { System.setErr(it) }

        isCapturing = false
    }

    override fun clearLogs() {
        _logs.value = emptyList()
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
        }
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
}
