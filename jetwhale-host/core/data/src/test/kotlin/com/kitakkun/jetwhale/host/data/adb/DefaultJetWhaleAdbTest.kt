package com.kitakkun.jetwhale.host.data.adb

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.data.util.AdbLocator
import com.kitakkun.jetwhale.host.sdk.JetWhaleAdbCommandException
import com.kitakkun.jetwhale.host.sdk.JetWhaleAdbResult
import com.kitakkun.jetwhale.host.sdk.JetWhaleAdbTimeoutException
import com.kitakkun.jetwhale.host.sdk.JetWhaleAdbUnavailableException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The fake adb's `hang` holds its stdout open for a minute, so a call against it that returns
 * within seconds shows that adb was ended rather than left to finish.
 */
@OptIn(ExperimentalJetWhaleApi::class)
class DefaultJetWhaleAdbTest {
    private val folder: File = Files.createTempDirectory("jetwhale-adb").toFile()
    private val bin = File(folder, "bin").apply { mkdirs() }

    private val adb = DefaultJetWhaleAdb(
        AdbLocator(
            environment = mapOf("PATH" to bin.path),
            userHome = null,
            isWindows = false,
            fixedDirectories = emptyList(),
        ),
    )

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    @Test
    fun `run reports the exit code and keeps stdout and stderr apart`() = runBlocking {
        installFakeAdb()

        assertEquals(JetWhaleAdbResult(exitCode = 1, output = "out", errorOutput = "error: device offline"), adb.run("fail", timeout = 1.minutes))
    }

    @Test
    fun `run fails with a timeout and ends adb when it does not finish in time`() = runBlocking {
        installFakeAdb()
        val started = TimeSource.Monotonic.markNow()

        assertFailsWith<JetWhaleAdbTimeoutException> { adb.run("hang", timeout = 200.milliseconds) }

        assertTrue(started.elapsedNow() < 10.seconds)
    }

    @Test
    fun `runStreaming fails with a timeout and ends adb when its consumer is still reading`() = runBlocking {
        installFakeAdb()
        val started = TimeSource.Monotonic.markNow()

        assertFailsWith<JetWhaleAdbTimeoutException> { adb.runStreaming("hang", timeout = 200.milliseconds) { it.readBytes() } }

        assertTrue(started.elapsedNow() < 10.seconds)
    }

    @Test
    fun `a consumer failing on the output a timeout cut short reports the timeout`() = runBlocking {
        installFakeAdb()

        assertFailsWith<JetWhaleAdbTimeoutException> {
            adb.runStreaming("hang", timeout = 200.milliseconds) { stream ->
                stream.readBytes()
                throw IllegalStateException("not a PNG")
            }
        }
        Unit
    }

    @Test
    fun `a cancelled call ends adb`() = runBlocking {
        installFakeAdb()
        val reading = CompletableDeferred<Unit>()
        val call = launch {
            adb.runStreaming("hang", timeout = 1.minutes) { stream ->
                reading.complete(Unit)
                stream.readBytes()
            }
        }
        reading.await()
        val cancelled = TimeSource.Monotonic.markNow()

        call.cancelAndJoin()

        assertTrue(cancelled.elapsedNow() < 10.seconds)
    }

    @Test
    fun `a call cancelled while adb is being looked up ends adb once it has started`() = runBlocking {
        installFakeAdb()
        val lookingUp = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val environment = mapOf("PATH" to bin.path)
        val environmentReadAfterCancellation = object : Map<String, String> by environment {
            override fun get(key: String): String? {
                lookingUp.countDown()
                cancelled.await()
                return environment[key]
            }
        }
        val adb = DefaultJetWhaleAdb(
            AdbLocator(
                environment = environmentReadAfterCancellation,
                userHome = null,
                isWindows = false,
                fixedDirectories = emptyList(),
            ),
        )
        val call = launch(Dispatchers.IO) { adb.run("hang", timeout = 1.minutes) }
        lookingUp.await()

        call.cancel()
        cancelled.countDown()
        val cancelledAt = TimeSource.Monotonic.markNow()
        call.join()

        assertTrue(cancelledAt.elapsedNow() < 10.seconds)
    }

    @Test
    fun `a cancelled call whose consumer fails on the cut-off output ends as cancelled`() = runBlocking {
        installFakeAdb()
        supervisorScope {
            val reading = CompletableDeferred<Unit>()
            val call = async {
                adb.runStreaming("hang", timeout = 1.minutes) { stream ->
                    reading.complete(Unit)
                    stream.readBytes()
                    throw IllegalStateException("not a PNG")
                }
            }
            reading.await()

            call.cancel()

            assertFailsWith<CancellationException> { call.await() }
        }
        Unit
    }

    @Test
    fun `runStreaming hands its consumer stdout without what adb printed to stderr`() = runBlocking {
        installFakeAdb()

        assertEquals("PNGDATA", adb.runStreaming("screencap", timeout = 1.minutes) { it.readBytes().decodeToString() })
    }

    @Test
    fun `runStreaming fails with adb's stderr when adb exits with an error after its stdout ends`() = runBlocking {
        installFakeAdb()

        val failure = assertFailsWith<JetWhaleAdbCommandException> { adb.runStreaming("offline", timeout = 1.minutes) { it.readBytes() } }

        assertEquals(1, failure.exitCode)
        assertEquals("error: device 'emulator-1' not found", failure.errorOutput)
    }

    @Test
    fun `a consumer failing on what a failed command left reports adb's failure with its own as the cause`() = runBlocking {
        installFakeAdb()

        val failure = assertFailsWith<JetWhaleAdbCommandException> {
            adb.runStreaming("offline", timeout = 1.minutes) { stream -> check(stream.readBytes().isNotEmpty()) { "not a PNG" } }
        }

        assertEquals("error: device 'emulator-1' not found", failure.errorOutput)
        assertEquals("not a PNG", failure.cause?.message)
    }

    @Test
    fun `a consumer failing on what a successful command printed fails with its own exception`() = runBlocking {
        installFakeAdb()

        val failure = assertFailsWith<IllegalStateException> {
            adb.runStreaming("screencap", timeout = 1.minutes) { stream -> check(stream.readBytes().isEmpty()) { "not empty" } }
        }

        assertEquals("not empty", failure.message)
    }

    @Test
    fun `runStreaming drains stderr while its consumer is still reading stdout`() = runBlocking {
        installFakeAdb()

        assertEquals("PNGDATA", adb.runStreaming("noisy", timeout = 1.minutes) { it.readBytes().decodeToString() })
    }

    @Test
    fun `runStreaming returns what its consumer read and ends adb when the consumer stops early`() = runBlocking {
        installFakeAdb()
        val started = TimeSource.Monotonic.markNow()

        assertEquals("line", adb.runStreaming("endless", timeout = 1.minutes) { it.bufferedReader().readLine() })

        assertTrue(started.elapsedNow() < 10.seconds)
    }

    @Test
    fun `a call fails as unavailable when no adb is found`() = runBlocking {
        assertFailsWith<JetWhaleAdbUnavailableException> { adb.run("version", timeout = 1.minutes) }
        Unit
    }

    @Test
    fun `a call fails as unavailable when adb cannot be launched`() = runBlocking {
        File(bin, "adb").apply {
            writeText("#!/nonexistent/interpreter\n")
            setExecutable(true)
        }

        assertFailsWith<JetWhaleAdbUnavailableException> { adb.runStreaming("version", timeout = 1.minutes) { it.readBytes() } }
        Unit
    }

    private fun installFakeAdb() {
        File(bin, "adb").apply {
            writeText(
                """
                #!/bin/sh
                case "$1" in
                  fail) echo out; echo 'error: device offline' >&2; exit 1 ;;
                  hang) exec sleep 60 ;;
                  screencap) echo '* daemon started successfully' >&2; printf PNGDATA ;;
                  noisy) head -c 200000 /dev/zero | tr '\0' e >&2; printf PNGDATA ;;
                  offline) echo "error: device 'emulator-1' not found" >&2; exit 1 ;;
                  endless) exec yes line ;;
                esac
                """.trimIndent() + "\n",
            )
            setExecutable(true)
        }
    }
}
