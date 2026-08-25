package com.kitakkun.jetwhale.plugins.androiddevice.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleAdb
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult
import kotlinx.serialization.json.put

/** Log priorities, lowest first, as `logcat` spells them in a `TAG:PRIORITY` filter. */
internal enum class LogPriority { V, D, I, W, E, F }

/** Bounded by default so a tool result stays something an AI agent can actually read. */
private const val DEFAULT_LOG_LINES = 200

@OptIn(ExperimentalJetWhaleApi::class)
internal class LogcatCommand(adb: JetWhaleAdb) : AndroidDeviceCommand(adb) {
    override val name = "$TOOL_PREFIX.logcat"
    override val description =
        "Reads the device log and returns it as text, newest last. Narrow it with packageName (the " +
            "app's current process), tag, and priority; the buffer is never streamed, so the call " +
            "always terminates."

    private val packageName by stringOrNull("Only show lines from this app's running process. The app has to be running for its pid to exist.")
    private val tag by stringOrNull("Only show lines with this log tag.")
    private val priority by enumOrNull("Lowest priority to show: V, D, I, W, E or F.", LogPriority.entries)
    private val lines by intOrNull("How many of the most recent lines to return. Defaults to $DEFAULT_LOG_LINES, which keeps the payload something an agent can read. Ignored when since is given.")
    private val since by stringOrNull("Only show lines after this time, as logcat writes it: \"MM-DD hh:mm:ss.mmm\".")

    override suspend fun executeOnDevice(arguments: JetWhaleMcpArguments, target: DeviceTarget): JetWhaleMcpResult {
        val packageName = arguments[packageName]?.let(::requirePackageName)
        val tag = arguments[tag]
        val priority = arguments[priority]
        val lines = arguments[lines] ?: DEFAULT_LOG_LINES
        if (lines <= 0) throw JetWhaleMcpArgumentException("invalid lines: $lines (expected a positive integer)")
        val since = arguments[since]

        val pid = if (packageName == null) {
            null
        } else {
            val pidof = target.shell("pidof", singleQuoteForShell(packageName), timeout = AdbTimeouts.QUICK)
            parsePid(pidof.output)
                ?: return target.failureResult(pidof, "$packageName has no running process, so its log lines cannot be selected by pid; drop packageName to read the whole buffer")
        }

        val args = buildList {
            add("logcat")
            add("-d")
            add("-t")
            add(if (since != null) singleQuoteForShell(since) else lines.toString())
            if (pid != null) {
                add("--pid")
                add(pid.toString())
            }
            // A tag filter only takes effect together with a `*:S` default that silences everything else.
            if (tag != null) {
                add(singleQuoteForShell("$tag:${(priority ?: LogPriority.V).name}"))
                add(singleQuoteForShell("*:S"))
            } else if (priority != null) {
                add(singleQuoteForShell("*:${priority.name}"))
            }
        }

        val result = target.shell(*args.toTypedArray(), timeout = AdbTimeouts.TRANSFER)
        target.requireSuccess(result, "logcat was refused")?.let { return it }

        val log = result.output.trim()
        return target.successResult {
            put("pid", pid)
            put("lineCount", if (log.isEmpty()) 0 else log.lines().size)
            put("log", log)
        }
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
internal class ClearLogcatCommand(adb: JetWhaleAdb) : AndroidDeviceCommand(adb) {
    override val name = "$TOOL_PREFIX.clearLogcat"
    override val description = "Empties the device log buffers, so the next logcat call shows only what happened after this point."

    override suspend fun executeOnDevice(arguments: JetWhaleMcpArguments, target: DeviceTarget): JetWhaleMcpResult {
        val result = target.shell("logcat", "-c", timeout = AdbTimeouts.SHELL)
        target.requireSuccess(result, "logcat -c was refused")?.let { return it }
        return target.successResult()
    }
}
