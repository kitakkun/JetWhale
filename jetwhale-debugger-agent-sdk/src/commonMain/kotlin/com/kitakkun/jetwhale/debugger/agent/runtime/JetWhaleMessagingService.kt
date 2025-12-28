package com.kitakkun.jetwhale.debugger.agent.runtime

/**
 * An interface representing the messaging service for communicating with the JetWhale debugger server.
 */
public interface JetWhaleMessagingService {
    /**
     * Starts the messaging service to connect to the JetWhale debugger server.
     *
     * @param host The hostname or IP address of the JetWhale debugger server.
     * @param port The port number of the JetWhale debugger server.
     */
    public fun startService(host: String, port: Int)
}
