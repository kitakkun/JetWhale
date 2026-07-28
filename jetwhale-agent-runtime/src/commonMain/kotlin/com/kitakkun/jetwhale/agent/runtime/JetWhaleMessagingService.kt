package com.kitakkun.jetwhale.agent.runtime

/**
 * An interface representing the messaging service for communicating with the JetWhale debugger server.
 */
internal interface JetWhaleMessagingService {
    /**
     * Starts the messaging service to connect to the JetWhale debugger server.
     *
     * @param host The hostname or IP address of the JetWhale debugger server.
     * @param port The port number of the JetWhale debugger server.
     */
    fun startService(host: String, port: Int)

    /**
     * Stops the messaging service: the reconnect loop is torn down, the current connection is closed
     * and every plugin peer is dropped.
     *
     * Terminal — the service is not restartable afterwards. Returns as soon as the teardown is
     * scheduled, and repeated calls are ignored.
     */
    fun stopService()
}
