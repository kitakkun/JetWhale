package com.kitakkun.jetwhale.plugins.androiddevice.host

import com.kitakkun.jetwhale.host.sdk.JetWhaleAdb
import com.kitakkun.jetwhale.host.sdk.JetWhaleAdbResult
import java.io.InputStream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * How long each class of adb command is allowed to take. Every call this plugin makes names one of
 * these, so a device that stops answering fails the tool instead of hanging the MCP server.
 */
internal object AdbTimeouts {
    /** `devices -l`, `getprop`, `wm size` — answered from the daemon or a single property read. */
    val QUICK = 10.seconds

    /** `input`, `am start`, `settings put`, `dumpsys` — one round trip into a running system server. */
    val SHELL = 30.seconds

    /** `screencap`, `logcat -d`, `push`, `pull` — bounded but proportional to a payload. */
    val TRANSFER = 2.minutes

    /** `install`, `pm clear`, `uninstall` — the package manager rewrites storage. */
    val PACKAGE = 5.minutes
}

/**
 * Runs adb commands and records the exact argument vectors it ran, in order, so every tool result
 * can report what actually happened rather than only its own summary of it.
 */
internal class AdbRun(private val adb: JetWhaleAdb) {
    private val recorded = mutableListOf<List<String>>()

    /** The argument vectors passed to adb so far, oldest first. */
    val invocations: List<List<String>> get() = recorded.toList()

    suspend fun exec(vararg args: String, timeout: Duration): JetWhaleAdbResult {
        recorded += args.toList()
        return adb.run(*args, timeout = timeout)
    }

    suspend fun <T> stream(vararg args: String, timeout: Duration, consume: suspend (InputStream) -> T): T {
        recorded += args.toList()
        return adb.runStreaming(*args, timeout = timeout, consume = consume)
    }
}
