package com.kitakkun.jetwhale.host.data.adb

import com.kitakkun.jetwhale.host.data.util.findAdbPath
import com.kitakkun.jetwhale.host.sdk.JetWhaleAdb
import com.kitakkun.jetwhale.host.sdk.JetWhaleAdbResult
import com.kitakkun.jetwhale.host.sdk.JetWhaleAdbUnavailableException
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.io.InputStream
import kotlin.time.Duration

/**
 * The host's single adb runner: every adb invocation in the debug tool — its own port wiring and
 * anything a plugin asks for through [com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginContext] —
 * goes through this one executable, resolved once by [findAdbPath].
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultJetWhaleAdb : JetWhaleAdb {
    override val executable: String by lazy { findAdbPath() }

    override suspend fun run(vararg args: String, timeout: Duration): JetWhaleAdbResult = withProcess(args, timeout) { process ->
        // stderr is merged into stdout, so a diagnostic adb printed there is part of the result
        // rather than being lost.
        val output = process.inputStream.bufferedReader().readText()
        JetWhaleAdbResult(exitCode = process.waitFor(), output = output.trim())
    }

    override suspend fun <T> runStreaming(vararg args: String, timeout: Duration, consume: suspend (InputStream) -> T): T = withProcess(args, timeout) { process ->
        consume(process.inputStream)
    }

    /**
     * Starts adb, runs [body] against the process, and makes sure the process is gone afterwards.
     *
     * A read from the process' stream blocks and does not observe cancellation, so a cancelled or
     * timed-out call is ended by destroying the process — which is what unblocks the read. The
     * guard child does that as soon as the scope is cancelled, while [body] is still blocked.
     */
    private suspend fun <T> withProcess(args: Array<out String>, timeout: Duration, body: suspend (Process) -> T): T = withContext(Dispatchers.IO) {
        val process = try {
            ProcessBuilder(listOf(executable) + args)
                .redirectErrorStream(true)
                .start()
        } catch (e: IOException) {
            throw JetWhaleAdbUnavailableException("adb could not be launched from \"$executable\": ${e.message}", e)
        }
        try {
            withTimeout(timeout) {
                val guard = launch {
                    try {
                        awaitCancellation()
                    } finally {
                        process.destroyForcibly()
                    }
                }
                try {
                    body(process)
                } finally {
                    guard.cancel()
                }
            }
        } finally {
            process.destroyForcibly()
        }
    }
}
