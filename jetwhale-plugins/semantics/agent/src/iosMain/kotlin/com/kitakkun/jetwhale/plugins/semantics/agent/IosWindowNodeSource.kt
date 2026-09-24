package com.kitakkun.jetwhale.plugins.semantics.agent

import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeRoot
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeCaptureOptions
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.objcPtr
import platform.UIKit.UIView
import platform.UIKit.UIWindow
import platform.darwin.NSObject

/**
 * Reads one iOS **window** — its views and everything SwiftUI or Compose publishes inside them — as
 * a single root.
 *
 * A window is the unit a user sees, and on iOS it is nearly always the only one: a presented
 * controller, an alert and a Compose `Dialog` all stay inside the window that showed them. So a
 * normal app has one root, and what the Android agent reports as a second root for a dialog is here
 * a subtree of the first — which `isHittable` and `obscuredBy` still account for, since the walk
 * sees the dimming view and the alert on top of the content.
 *
 * The window is held strongly: a weak Kotlin reference to an Objective-C object does not survive a
 * collection of its wrapper (see [AppleNodeIds]), and the probe that registered this source
 * unregisters it when the window hides, which is when the reference goes.
 */
@OptIn(ExperimentalForeignApi::class)
internal class IosWindowNodeSource(private val window: UIWindow) : ComposeNodeSource {
    override val sourceId: String = "ios-window-${window.objcPtr().toLong().toString(16)}"

    override suspend fun capture(options: NodeTreeCaptureOptions): ComposeRoot? = IosUiThread.await {
        val window = visibleWindow() ?: return@await null
        val frame = window.frame.toNodeBounds()
        ComposeRoot(
            rootId = sourceId,
            label = window.describe(),
            // Bounds are reported in points, which are already density-independent: a point is
            // what every iOS tool takes, so nothing is gained by scaling to pixels and back.
            density = 1f,
            windowOffsetX = frame.left,
            windowOffsetY = frame.top,
            // Modality on iOS lives inside the window — a presented controller over the content —
            // so no window claims the touches that land outside it.
            isTouchModal = false,
            node = AppleNodeIds.trackingCapture(window) {
                window.toAppleNode(options = options, window = window, depth = 0)
            },
        )
    }

    // A window that has gone off screen has nothing readable to report, so the check gates every
    // call rather than only the registration.
    private fun visibleWindow(): UIWindow? = window.takeIf { !it.hidden }

    override suspend fun performAction(request: PerformNodeAction): NodeActionResult = IosUiThread.await {
        val window = visibleWindow()
            ?: return@await NodeActionResult(performed = false, message = "the window is no longer readable")
        val node = AppleNodeIds.objectOf(request.nodeId, window)?.takeIf(window::stillHolds)
            ?: return@await NodeActionResult(
                performed = false,
                message = "unknown nodeId: ${request.nodeId} (the node may have left this window; capture the tree again)",
            )
        node.performAppleNodeAction(request)
    }
}

/**
 * Whether [node] is in this window's tree right now, not only in its last capture. The registry
 * keeps the last capture's objects alive, so a dismissed dialog's button still resolves by id; a
 * view answers through its `window`, a bare element only by being found again under the window.
 * Objects are compared by address: a Kotlin reference to an Objective-C object is a wrapper, and
 * UIKit may hand back a different wrapper for the same object.
 */
@OptIn(ExperimentalForeignApi::class)
private fun UIWindow.stillHolds(node: NSObject): Boolean {
    val address = node.objcPtr().toLong()
    if (node is UIView) return node.window?.objcPtr()?.toLong() == objcPtr().toLong()
    return anyDescendant { it.objcPtr().toLong() == address }
}

private fun NSObject.anyDescendant(predicate: (NSObject) -> Boolean): Boolean = accessibilityChildren().any { child ->
    predicate(child) || child.anyDescendant(predicate)
}

/** Names the window by what it shows — its root view controller — since a window has no name of its own. */
private fun UIWindow.describe(): String {
    val controller = rootViewController ?: return className()
    val name = (controller as NSObject).className()
    return if (isKeyWindow()) name else "$name / ${className()}"
}
