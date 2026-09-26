package com.kitakkun.jetwhale.host.model

import soil.query.SubscriptionKey

/** An exception a plugin instance let escape from its own coroutines. */
data class PluginFailure(
    val pluginId: String,
    val sessionId: String,
    val message: String,
    val stackTrace: String,
    val occurredAtMillis: Long,
)

/** The latest escaped exception of each plugin instance, keyed by session, then plugin. */
@JvmInline
value class PluginFailures(val bySession: Map<String, Map<String, PluginFailure>>) {
    fun latestFor(sessionId: String?, pluginId: String): PluginFailure? = sessionId?.let { bySession[it]?.get(pluginId) }

    companion object {
        val Empty: PluginFailures = PluginFailures(emptyMap())
    }
}

typealias PluginFailuresSubscriptionKey = SubscriptionKey<PluginFailures>
