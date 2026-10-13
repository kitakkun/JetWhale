package com.kitakkun.jetwhale.plugins.nav3.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.plugins.nav3.protocol.MutationResult
import com.kitakkun.jetwhale.plugins.nav3.protocol.NAV3_PLUGIN_ID
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavBackStackOperation
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavBackStackSnapshot
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavKeyTypeDescriptor
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

internal const val TOOL_PREFIX = NAV3_PLUGIN_ID

/** What the MCP commands are allowed to see and do; the host plugin is the only implementation. */
internal interface Nav3BackStackController {
    fun stacks(): List<NavBackStackSnapshot>

    fun keyTypes(): List<NavKeyTypeDescriptor>

    suspend fun mutate(stackId: String, operations: List<NavBackStackOperation>): MutationResult
}

/**
 * Picks the stack a tool call targets: the one the caller named, or the app's only stack when it
 * has just one — which is the common case, and lets a caller omit the argument entirely.
 */
@OptIn(ExperimentalJetWhaleApi::class)
internal fun resolveStackId(requested: String?, available: List<String>): String = when {
    requested != null -> requested.also {
        if (it !in available) {
            throw JetWhaleMcpArgumentException("unknown stackId '$it'; the app has ${describeAvailable(available)}")
        }
    }

    available.size == 1 -> available.single()

    available.isEmpty() -> throw JetWhaleMcpArgumentException(
        "the app has no Navigation 3 back stack registered; it must call TrackNavBackStack (or registerBackStack) first",
    )

    else -> throw JetWhaleMcpArgumentException("the app has ${describeAvailable(available)}; pass stackId to say which one")
}

private fun describeAvailable(available: List<String>): String = when {
    available.isEmpty() -> "none registered"
    else -> "${available.size} back stack(s): ${available.joinToString()}"
}

/** The wire snapshot as a tool result: indices are spelled out, since every operation takes one. */
internal fun NavBackStackSnapshot.toMcpJson(): JsonObject = buildJsonObject {
    put("stackId", stackId)
    put("size", entries.size)
    putJsonArray("entries") {
        entries.forEachIndexed { index, entry ->
            addJsonObject {
                put("index", index)
                put("typeName", entry.typeName)
                put("display", entry.display)
                put("isCurrent", index == entries.lastIndex)
                entry.key?.let { put("key", it) }
            }
        }
    }
}

/** The outcome of a mutation as a tool result, with the resulting stack so the caller can verify it. */
internal fun MutationResult.toMcpJson(): String = buildJsonObject {
    put("applied", error == null)
    error?.let { put("error", it) }
    snapshot?.let { put("stack", it.toMcpJson()) }
}.toString()

/** Short, human-facing label used for the status line after a UI-triggered operation. */
internal fun NavBackStackOperation.describe(): String = when (this) {
    is NavBackStackOperation.Push -> if (index == null) "Push" else "Insert at $index"
    is NavBackStackOperation.Pop -> "Pop $count"
    is NavBackStackOperation.PopTo -> if (inclusive) "Pop to below $index" else "Pop to $index"
    is NavBackStackOperation.RemoveAt -> "Remove $index"
    is NavBackStackOperation.MoveToTop -> "Move $index to top"
    is NavBackStackOperation.ReplaceAll -> "Replace stack"
}
