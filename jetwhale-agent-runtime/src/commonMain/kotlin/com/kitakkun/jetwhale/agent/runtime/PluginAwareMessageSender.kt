package com.kitakkun.jetwhale.agent.runtime

/**
 * A functional interface for sending messages to a specific plugin.
 * Used internally by [JetWhaleAgentPluginService] to delegate message sending.
 */
internal fun interface PluginAwareMessageSender {
    /**
     * Sends messages to the specified plugin.
     *
     * @param pluginId The ID of the target plugin.
     * @param messages The messages to send.
     */
    fun send(pluginId: String, vararg messages: String)
}
