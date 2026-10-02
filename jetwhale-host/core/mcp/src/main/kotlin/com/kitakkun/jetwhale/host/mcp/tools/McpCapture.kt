package com.kitakkun.jetwhale.host.mcp.tools

import androidx.compose.runtime.snapshots.Snapshot
import com.kitakkun.jetwhale.host.model.PluginComposeScene

/**
 * Runs [block] with [PluginComposeScene.isMcpCapture] raised, so the plugin renders what it shows
 * an agent rather than the user, and lowers it again afterwards.
 *
 * Must be called on the UI thread: no interactive frame can observe the raised flag there.
 */
internal inline fun <T> PluginComposeScene.whileCapturingForMcp(block: () -> T): T {
    isMcpCapture.value = true
    try {
        // render() flushes snapshot apply notifications only at its end, before drawing, so without
        // this the scene's recomposer would see the raised flag one frame too late.
        Snapshot.sendApplyNotifications()
        return block()
    } finally {
        isMcpCapture.value = false
        Snapshot.sendApplyNotifications()
    }
}
