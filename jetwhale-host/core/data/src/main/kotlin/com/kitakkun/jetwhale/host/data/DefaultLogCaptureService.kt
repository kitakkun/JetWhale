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
import java.util.logging.Level
import kotlin.time.Clock

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultLogCaptureService : LogCaptureService {
    override val logs: StateFlow<List<LogEntry>>
        field = MutableStateFlow<List<LogEntry>>(emptyList())

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

        System.setOut(CapturingPrintStream(previousOut, LineSink(LogLevel.INFO)))
        System.setErr(CapturingPrintStream(previousErr, LineSink(LogLevel.ERROR)))

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

    // Logback writes bytes in the JVM default charset, and this stream encodes print in it too, so
    // every line decodes with that one charset; the original stream re-encodes the text in the
    // console's charset, which can differ.
    private inner class CapturingPrintStream(
        original: PrintStream,
        sink: LineSink,
    ) : PrintStream(
        object : OutputStream() {
            private val pendingLineBytes = ByteArrayOutputStream()

            override fun write(b: Int) {
                if (b == '\n'.code) endLine() else pendingLineBytes.write(b)
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                var start = off
                for (i in off until off + len) {
                    if (b[i] == '\n'.code.toByte()) {
                        pendingLineBytes.write(b, start, i - start)
                        endLine()
                        start = i + 1
                    }
                }
                pendingLineBytes.write(b, start, off + len - start)
            }

            override fun flush() {
                original.flush()
            }

            private fun endLine() {
                val text = pendingLineBytes.toString(Charset.defaultCharset())
                pendingLineBytes.reset()
                // Not println: on Windows the text still ends in the '\r' of its line break, so
                // only the '\n' is added back.
                original.print(text)
                original.print('\n')
                sink.accept(text)
            }
        },
        true,
        Charset.defaultCharset(),
    )

    /**
     * Files each line at [streamLevel], unless it is a java.util.logging record, which states its
     * own level: libraries log through JUL to stderr at every level, so stderr alone does not mean
     * an error. JUL's default format writes a record as a header line (time and source) followed
     * by `LEVEL: message`, so a header is held back until the next line shows whether it is one.
     * A `LEVEL: ` prefix counts only after such a header: a line printed as `INFO: …` on its own
     * is not a JUL record and keeps its stream's level.
     */
    private inner class LineSink(private val streamLevel: LogLevel) {
        private var pendingHeader: String? = null

        fun accept(text: String) {
            val line = text.removeSuffix("\r")
            val header = pendingHeader
            pendingHeader = null
            val recordLevel = header?.let { levelOfRecordLine(line) }
            when {
                recordLevel != null -> addLogEntry("$header\n$line", recordLevel)

                else -> {
                    header?.let { addLogEntry(it, streamLevel) }
                    if (JAVA_UTIL_LOGGING_HEADER.matches(line)) pendingHeader = line else addLogEntry(line, streamLevel)
                }
            }
        }

        private fun levelOfRecordLine(line: String): LogLevel? {
            val name = line.substringBefore(": ", missingDelimiterValue = "")
            // JUL prints the level name localized for the default locale at the moment it formats
            // the record, and the App Language setting changes that locale while the host runs.
            val level = JAVA_UTIL_LOGGING_LEVELS.firstOrNull { name.isNotEmpty() && (name == it.name || name == it.localizedName) } ?: return null
            return if (level.intValue() >= Level.WARNING.intValue()) LogLevel.ERROR else LogLevel.INFO
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
            logs.value = entries.toList()
        }
    }
}

private val JAVA_UTIL_LOGGING_LEVELS = listOf(Level.SEVERE, Level.WARNING, Level.INFO, Level.CONFIG, Level.FINE, Level.FINER, Level.FINEST)

// The first line of JUL's default format, "%1$tb %1$td, %1$tY %1$tl:%1$tM:%1$tS %1$Tp %2$s"; the
// month and AM/PM names are localized.
private val JAVA_UTIL_LOGGING_HEADER = Regex("""^\S+ \d{1,2}, \d{4} \d{1,2}:\d{2}:\d{2} \S+ .+$""")
