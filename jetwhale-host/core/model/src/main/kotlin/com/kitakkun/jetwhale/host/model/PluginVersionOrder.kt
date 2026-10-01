package com.kitakkun.jetwhale.host.model

import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginManifest

/**
 * Orders plugin versions numerically, dot by dot (`1.10.0` after `1.9.2`). A component that is not a
 * number counts as 0, and a missing one as 0, so `1.2` equals `1.2.0`.
 */
object PluginVersionOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        val left = a.versionParts()
        val right = b.versionParts()
        for (i in 0 until maxOf(left.size, right.size)) {
            val diff = left.getOrElse(i) { 0 }.compareTo(right.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }

    private fun String.versionParts(): List<Int> = split(".").map { it.toIntOrNull() ?: 0 }
}

/** Whether an agent plugin at [agentVersion] falls inside this range; open ends are unbounded. */
fun JetWhaleHostPluginManifest.AgentVersionRange.accepts(agentVersion: String): Boolean = (min?.let { PluginVersionOrder.compare(agentVersion, it) >= 0 } ?: true) &&
    (max?.let { PluginVersionOrder.compare(agentVersion, it) <= 0 } ?: true)
