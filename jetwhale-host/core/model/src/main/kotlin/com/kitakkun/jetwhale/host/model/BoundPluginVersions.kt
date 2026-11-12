package com.kitakkun.jetwhale.host.model

/**
 * The version of each plugin a session's instance was created from, keyed by session id and then
 * plugin id. A plugin with no instance in a session is absent.
 */
@JvmInline
value class BoundPluginVersions(val versionsBySession: Map<String, Map<String, String>>) {
    fun versionOf(sessionId: String?, pluginId: String): String? = sessionId?.let { versionsBySession[it]?.get(pluginId) }

    companion object {
        val Empty = BoundPluginVersions(emptyMap())
    }
}
