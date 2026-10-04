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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
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
    override suspend fun run(vararg args: String, timeout: Duration): JetWhaleAdbResult = withProcess(args, timeout) { process, errorOutput ->
        val output = process.inputStream.bufferedReader().readText().trim()
        JetWhaleAdbResult(exitCode = process.waitFor(), output = output, errorOutput = errorOutput.await())
    }

    override suspend fun <T> runStreaming(vararg args: String, timeout: Duration, consume: suspend (InputStream) -> T): T = withProcess(args, timeout) { process, errorOutput ->
        val stdout = EndTrackingInputStream(process.inputStream)

        // The consumer is the plugin's code and may throw anything on what a failed command left in
        // stdout; adb's own failure is what the caller is told, with the consumer's as its cause.
        @Suppress("KOTRAIL_CATCH_TOO_BROAD")
        val consumed = try {
            Result.success(consume(stdout))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
        if (stdout.reachedEnd) {
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                val stderr = errorOutput.await()
                throw JetWhaleAdbCommandException(exitCode, stderr, "adb ${args.joinToString(" ")} exited with code $exitCode: $stderr", consumed.exceptionOrNull())
            }
        }
        consumed.getOrThrow()
    }

    private suspend fun <T> withProcess(
        args: Array<out String>,
        timeout: Duration,
        body: suspend CoroutineScope.(process: Process, errorOutput: Deferred<String>) -> T,
    ): T = withContext(Dispatchers.IO) {
        val executable = locator.find()
            ?: throw JetWhaleAdbUnavailableException("no adb found: set ANDROID_HOME to an Android SDK, or put adb on PATH", null)
        val process = try {
            ProcessBuilder(listOf(executable.path) + args).start()
        } catch (e: IOException) {
            throw JetWhaleAdbUnavailableException("adb could not be launched from ${executable.path}: ${e.message}", e)
        }
        val timedOut = AtomicBoolean(false)
        // A blocking read of adb's output ignores cancellation, so adb is ended from this
        // coroutine. A coroutine cancelled before it is dispatched never runs its block, so this
        // one starts undispatched to always reach its finally. It resumes on Default because the
        // reads it ends can hold every IO thread.
        val lifetime = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            try {
                delay(timeout)
                timedOut.set(true)
            } finally {
                process.destroyForcibly()
            }
        }
        val errorOutput = async {
            try {
                process.errorStream.bufferedReader().readText().trim()
            } catch (_: IOException) {
                // Ending adb closes stderr under this read, and stderr is not used once adb has
                // been ended.
                ""
            }
        }

        // Once adb has been ended, by a cancellation or at the deadline, whatever the body throws on
        // the cut-off output is that ending's doing, so it is reported as the cancellation or the
        // timeout.
        @Suppress("KOTRAIL_CATCH_TOO_BROAD")
        val value = try {
            body(process, errorOutput)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ensureActive()
            if (timedOut.get()) throw timeoutException(args, timeout, e)
            throw e
        } finally {
            lifetime.cancel()
        }
        ensureActive()
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
