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
    private val terminalBefore = System.out
    private val terminal = ByteArrayOutputStream()

    @AfterTest
    fun restoreTerminal() {
        service.stopCapture()
        System.setOut(terminalBefore)
    }

    @Test
    fun `lines printed from several threads at once are all kept`() {
        System.setOut(PrintStream(terminal, true))
        service.startCapture()
        val start = CountDownLatch(1)

        val writers = List(WRITERS) { writer ->
            thread {
                start.await()
                repeat(LINES_PER_WRITER) { line -> println("writer $writer line $line") }
            }
        }
        start.countDown()
        writers.forEach(Thread::join)

        assertEquals(WRITERS * LINES_PER_WRITER, service.logs.value.size)
    }

    @Test
    fun `a character whose bytes arrive in separate writes is kept whole`() {
        System.setOut(PrintStream(terminal, true))
        service.startCapture()
        val bytes = "プラグイン\n".toByteArray(Charset.defaultCharset())

        System.out.write(bytes, 0, 2)
        System.out.write(bytes, 2, bytes.size - 2)

        assertEquals("プラグイン", service.logs.value.single().message)
    }

    @Test
    fun `a line printed in pieces is captured as the one line the terminal shows`() {
        System.setOut(PrintStream(terminal, true))
        service.startCapture()

        print("a")
        System.out.write("b".toByteArray(Charset.defaultCharset()))
        println("c")

        assertEquals(listOf("abc"), service.logs.value.map(LogEntry::message))
        assertEquals("abc" + System.lineSeparator(), terminal.toString(Charset.defaultCharset()))
    }

    @Test
    fun `bytes written as whole lines reach the terminal with their line breaks`() {
        System.setOut(PrintStream(terminal, true))
        service.startCapture()

        System.out.write("first\nsecond\n".toByteArray(Charset.defaultCharset()))
        System.out.flush()

        assertEquals("first\nsecond\n", terminal.toString(Charset.defaultCharset()))
    }
}

private const val WRITERS = 8

private const val LINES_PER_WRITER = 500
