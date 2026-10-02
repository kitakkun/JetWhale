package com.kitakkun.jetwhale.host.data

import com.kitakkun.jetwhale.host.model.LogEntry
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.Charset
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultLogCaptureServiceTest {
    private val service = DefaultLogCaptureService()
    private val outBefore = System.out
    private val errBefore = System.err
    private val encodingsBefore = ENCODING_PROPERTIES.associateWith(System::getProperty)
    private val terminal = ByteArrayOutputStream()

    init {
        ENCODING_PROPERTIES.forEach { System.setProperty(it, Charsets.UTF_8.name()) }
    }

    @AfterTest
    fun restoreTerminal() {
        service.stopCapture()
        System.setOut(outBefore)
        System.setErr(errBefore)
        encodingsBefore.forEach { (property, value) -> if (value == null) System.clearProperty(property) else System.setProperty(property, value) }
    }

    @Test
    fun `lines printed on stdout and stderr from several threads at once are all kept with an id each`() {
        System.setOut(PrintStream(terminal, true))
        System.setErr(PrintStream(ByteArrayOutputStream(), true))
        service.startCapture()
        val start = CountDownLatch(1)

        val writers = List(WRITERS) { writer ->
            thread {
                val stream = if (writer % 2 == 0) System.out else System.err
                start.await()
                repeat(LINES_PER_WRITER) { line -> stream.println("writer $writer line $line") }
            }
        }
        start.countDown()
        writers.forEach(Thread::join)

        val logs = service.logs.value
        assertEquals(WRITERS * LINES_PER_WRITER, logs.size)
        assertEquals(logs.size, logs.map(LogEntry::id).toSet().size)
    }

    @Test
    fun `the terminal gets the bytes of its own charset and the captured line reads back the same`() {
        val windowsConsole = Charset.forName("windows-31j")
        System.setProperty("stdout.encoding", windowsConsole.name())
        System.setOut(PrintStream(terminal, true, windowsConsole))
        service.startCapture()

        println("プラグイン")

        assertEquals("プラグイン" + System.lineSeparator(), terminal.toString(windowsConsole))
        assertEquals("プラグイン", service.logs.value.single().message)
    }

    @Test
    fun `a character whose bytes arrive in separate writes is kept whole`() {
        System.setOut(PrintStream(terminal, true))
        service.startCapture()
        val bytes = "プラグイン\n".toByteArray(Charsets.UTF_8)

        System.out.write(bytes, 0, 2)
        System.out.write(bytes, 2, bytes.size - 2)

        assertEquals("プラグイン", service.logs.value.single().message)
    }

    @Test
    fun `a line printed in pieces is captured as the one line the terminal shows`() {
        System.setOut(PrintStream(terminal, true))
        service.startCapture()

        print("a")
        System.out.write("b".toByteArray(Charsets.UTF_8))
        println("c")

        assertEquals(listOf("abc"), service.logs.value.map(LogEntry::message))
        assertEquals("abc" + System.lineSeparator(), terminal.toString(Charsets.UTF_8))
    }

    @Test
    fun `bytes written as whole lines reach the terminal with their line breaks`() {
        System.setOut(PrintStream(terminal, true))
        service.startCapture()

        System.out.write("first\nsecond\n".toByteArray(Charsets.UTF_8))
        System.out.flush()

        assertEquals("first\nsecond\n", terminal.toString(Charsets.UTF_8))
        assertEquals(listOf("first", "second"), service.logs.value.map(LogEntry::message))
    }
}

private const val WRITERS = 8

private const val LINES_PER_WRITER = 500

private val ENCODING_PROPERTIES = listOf("stdout.encoding", "stderr.encoding")
