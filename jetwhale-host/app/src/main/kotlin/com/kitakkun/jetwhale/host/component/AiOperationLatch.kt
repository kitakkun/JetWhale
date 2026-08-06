package com.kitakkun.jetwhale.host.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/** How long an MCP tool call keeps showing after it completes. */
private val AI_OPERATION_INDICATOR_LINGER = 1500.milliseconds

/**
 * Whether an AI agent should read as operating right now, given [startedCount] from
 * [com.kitakkun.jetwhale.host.model.McpActivity].
 *
 * A tool call can start and finish faster than the UI samples the running-invocation list, so
 * watching that list drops fast calls entirely. This latches "operating" on whenever [startedCount]
 * changes and holds it briefly instead: a fast call still registers, and a burst reads as one
 * continuous operation because each new call restarts the hold. Keying the effect on the monotonic
 * counter means a skipped intermediate value still re-fires, since the value differs across frames.
 */
@Composable
fun rememberAiOperating(startedCount: Long): Boolean {
    var operating by remember { mutableStateOf(false) }
    LaunchedEffect(startedCount) {
        if (startedCount > 0L) {
            operating = true
            delay(AI_OPERATION_INDICATOR_LINGER)
            operating = false
        }
    }
    return operating
}
