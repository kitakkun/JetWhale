package com.kitakkun.jetwhale.host.sdk

/**
 * Optional capability interface for plugins that need to exchange messages with the debuggee.
 *
 * Implement this interface alongside [JetWhaleRawHostPlugin] to receive events from the debuggee
 * and dispatch method calls back to it.
 *
 * The framework calls [onConnect] when a debuggee session becomes active for this plugin,
 * and [onDisconnect] when the session ends or the plugin is deactivated.
 */
public interface JetWhaleMessagingCapablePlugin {
    /**
     * Called when the plugin is connected to an active debuggee session.
     * Use [connection] to register event handlers and dispatch methods.
     *
     * @param connection The connection to the debuggee session.
     */
    public fun onConnect(connection: JetWhaleRawConnection)

    /**
     * Called when the connection to the debuggee session is closed.
     */
    public fun onDisconnect()
}
