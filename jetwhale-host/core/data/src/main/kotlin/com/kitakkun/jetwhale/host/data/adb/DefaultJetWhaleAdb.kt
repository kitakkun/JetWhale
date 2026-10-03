package com.kitakkun.jetwhale.host.data.adb

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.data.util.AdbLocator
import com.kitakkun.jetwhale.host.sdk.JetWhaleAdb
import com.kitakkun.jetwhale.host.sdk.JetWhaleAdbCommandException
import com.kitakkun.jetwhale.host.sdk.JetWhaleAdbResult
import com.kitakkun.jetwhale.host.sdk.JetWhaleAdbTimeoutException
import com.kitakkun.jetwhale.host.sdk.JetWhaleAdbUnavailableException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration

@OptIn(ExperimentalJetWhaleApi::class)
internal class DefaultJetWhaleAdb(private val locator: AdbLocator) : JetWhaleAdb {
    override suspend fun run(vararg args: String, timeout: Duration): JetWhaleAdbResult = withProcess(args, timeout, mergeErrorStream = true) { process ->
        val output = process.inputStream.bufferedReader().readText()
        JetWhaleAdbResult(exitCode = process.waitFor(), output = output.trim())
    }

    override suspend fun <T> runStreaming(vararg args: String, timeout: Duration, consume: suspend (InputStream) -> T): T = withProcess(args, timeout, mergeErrorStream = false) { process ->
        val errorOutput = async {
            try {
                process.errorStream.bufferedReader().readText().trim()
            } catch (_: IOException) {
                // The read fails only once the process has been ended, and stderr is not used after
                // that.
                ""
            }
        }
        val stdout = EndTrackingInputStream(process.inputStream)
        val value = consume(stdout)
        if (stdout.reachedEnd) {
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                val stderr = errorOutput.await()
                throw JetWhaleAdbCommandException(exitCode, stderr, "adb ${args.joinToString(" ")} exited with code $exitCode: $stderr")
            }
        }
        value
    }

    private suspend fun <T> withProcess(
        args: Array<out String>,
        timeout: Duration,
        mergeErrorStream: Boolean,
        body: suspend CoroutineScope.(Process) -> T,
    ): T = withContext(Dispatchers.IO) {
        val executable = locator.find()
            ?: throw JetWhaleAdbUnavailableException("no adb found: set ANDROID_HOME to an Android SDK, or put adb on PATH", null)
        val process = try {
            ProcessBuilder(listOf(executable.path) + args).redirectErrorStream(mergeErrorStream).start()
        } catch (e: IOException) {
            throw JetWhaleAdbUnavailableException("adb could not be launched from ${executable.path}: ${e.message}", e)
        }
        val timedOut = AtomicBoolean(false)
        // A read from adb's output blocks without noticing cancellation, so adb is ended from here:
        // at the deadline, when the caller is cancelled, and once the body is done.
        val lifetime = launch {
            try {
                delay(timeout)
                timedOut.set(true)
            } finally {
                process.destroyForcibly()
            }
        }

        // Once adb has been ended, by a cancellation or at the deadline, whatever the body throws on
        // the cut-off output is that ending's doing, so it is reported as the cancellation or the
        // timeout.
        @Suppress("KOTRAIL_CATCH_TOO_BROAD")
        val value = try {
            body(process)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ensureActive()
            if (timedOut.get()) throw timeoutException(args, timeout, e)
            throw e
        } finally {
            lifetime.cancel()
        }
        if (timedOut.get()) throw timeoutException(args, timeout, null)
        value
    }

    private fun timeoutException(args: Array<out String>, timeout: Duration, cause: Exception?) = JetWhaleAdbTimeoutException("adb ${args.joinToString(" ")} did not finish within $timeout", cause)
}

/** adb's stdout, noting whether its reader got to the end of it. */
private class EndTrackingInputStream(input: InputStream) : FilterInputStream(input) {
    @Volatile
    var reachedEnd: Boolean = false
        private set

    override fun read(): Int = super.read().also { if (it == -1) reachedEnd = true }

    override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it == -1) reachedEnd = true }
}
