package com.kitakkun.jetwhale.host.mcp.tools.host

import com.kitakkun.jetwhale.host.mcp.HostMcpCommand
import com.kitakkun.jetwhale.host.mcp.JetWhaleMcpTool
import com.kitakkun.jetwhale.host.model.LogCaptureService
import com.kitakkun.jetwhale.host.model.LogEntry
import com.kitakkun.jetwhale.host.model.LogLevel
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Keeps a full log dump from blowing up a tool result; the host retains up to 10,000 entries. */
private const val DEFAULT_LIMIT = 200
private const val MAX_LIMIT = 1000
private const val MAX_MESSAGE_LENGTH = 2000

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<JetWhaleMcpTool>())
class GetLogsCommand(
    private val logCaptureService: LogCaptureService,
) : HostMcpCommand() {
    override val name: String = "jetwhale.getLogs"
    override val description: String =
        "Host-wide: reads the debug tool's own captured stdout/stderr, oldest first. Use it to diagnose JetWhale itself — plugin load failures, server errors — not the debugged app's logs."

    private val limit by intOrNull("How many of the most recent matching entries to return. Default $DEFAULT_LIMIT, maximum $MAX_LIMIT.")
    private val level by enumOrNull("Only return entries logged at this level.", LogLevel.entries)
    private val contains by stringOrNull("Only return entries whose message contains this substring, case-insensitively.")

    override suspend fun executeText(arguments: JetWhaleMcpArguments): String {
        val requestedLimit = arguments[limit] ?: DEFAULT_LIMIT
        if (requestedLimit !in 1..MAX_LIMIT) {
            throw JetWhaleMcpArgumentException("invalid limit: expected 1..$MAX_LIMIT but was $requestedLimit")
        }
        val requestedLevel = arguments[level]
        val requestedSubstring = arguments[contains]

        val matching = logCaptureService.logs.value.filter { entry ->
            (requestedLevel == null || entry.level == requestedLevel) &&
                (requestedSubstring == null || entry.message.contains(requestedSubstring, ignoreCase = true))
        }
        val returned = matching.takeLast(requestedLimit)

        return Json.encodeToString(
            GetLogsResult(
                logs = returned.map { it.toJson() },
                returned = returned.size,
                total = matching.size,
            ),
        )
    }
}

@Inject
@ContributesIntoSet(AppScope::class, binding = binding<JetWhaleMcpTool>())
class ClearLogsCommand(
    private val logCaptureService: LogCaptureService,
) : HostMcpCommand() {
    override val name: String = "jetwhale.clearLogs"
    override val description: String =
        "Host-wide: discards every captured host log entry. Clear before reproducing an issue so that jetwhale.getLogs afterwards shows only what the reproduction produced."

    override suspend fun executeText(arguments: JetWhaleMcpArguments): String {
        val cleared = logCaptureService.logs.value.size
        logCaptureService.clearLogs()
        return Json.encodeToString(ClearLogsResult(cleared = cleared))
    }
}

private fun LogEntry.toJson() = LogEntryJson(
    timestamp = timestamp.toString(),
    level = level.name,
    message = if (message.length > MAX_MESSAGE_LENGTH) message.take(MAX_MESSAGE_LENGTH) + "…(truncated)" else message,
)

@Serializable
data class GetLogsResult(
    val logs: List<LogEntryJson>,
    val returned: Int,
    val total: Int,
)

@Serializable
data class LogEntryJson(
    val timestamp: String,
    val level: String,
    val message: String,
)

@Serializable
data class ClearLogsResult(val cleared: Int)
