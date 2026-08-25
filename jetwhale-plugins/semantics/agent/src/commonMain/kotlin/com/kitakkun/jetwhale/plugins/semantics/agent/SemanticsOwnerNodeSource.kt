package com.kitakkun.jetwhale.plugins.semantics.agent

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.getAllSemanticsNodes
import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeRoot
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeCaptureOptions
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction

/**
 * Reads one composition through its [SemanticsOwner].
 *
 * Everything here is platform-independent: [SemanticsOwner] and [SemanticsNode] both live in
 * Compose's common source set. What differs per platform is only how you get hold of an owner —
 * `AndroidComposeView` on Android, `ComposeWindow.semanticsOwners` on desktop — which is why the
 * owner arrives as a lambda rather than being looked up here.
 *
 * The owner is the narrowest handle that supports the whole job, so a platform that can only reach
 * a `SemanticsOwner` (desktop does; it never exposes a `RootForTest`) is served by exactly the same
 * code as one that has more.
 *
 * @param owner looked up per call rather than held, so a caller can keep a weak reference and let
 *   the composition be collected; returning `null` reports the root as currently unreadable.
 * @param density px per dp of this root, for converting the pixel bounds it reports back to dp.
 * @param windowOffset where this root's window sits on screen. Node bounds are reported relative to
 *   the root **and** to the screen, and the screen figure is this offset plus the in-window bounds —
 *   so a root whose window does not start at the screen origin (a dialog, a popup) must supply it.
 */
class SemanticsOwnerNodeSource(
    override val sourceId: String,
    private val owner: () -> SemanticsOwner?,
    private val label: () -> String,
    private val density: () -> Float,
    private val windowOffset: () -> Offset = { Offset.Zero },
    private val uiThread: ComposeUiThread = MainDispatcherComposeUiThread,
) : ComposeNodeSource {

    override suspend fun capture(options: NodeTreeCaptureOptions): ComposeRoot? = uiThread.await {
        val owner = owner() ?: return@await null
        val offset = windowOffset()
        val semanticsRoot = if (options.merged) owner.rootSemanticsNode else owner.unmergedRootSemanticsNode
        ComposeRoot(
            rootId = sourceId,
            label = label(),
            density = density(),
            windowOffsetX = offset.x,
            windowOffsetY = offset.y,
            node = semanticsRoot.toComposeNode(
                options = options,
                windowOffsetX = offset.x,
                windowOffsetY = offset.y,
                rootOffset = Offset.Zero,
                depth = 0,
                // This source reads a composition through its owner alone, which carries no route to
                // any foreign UI embedded in it; the Android window source is the one that has one.
                interopChildren = { _, _ -> emptyList() },
            ),
        )
    }

    override suspend fun performAction(request: PerformNodeAction): NodeActionResult = uiThread.await {
        val owner = owner()
            ?: return@await NodeActionResult(performed = false, message = "the root is no longer readable")
        val node = owner.findNodeById(request.nodeId)
            ?: return@await NodeActionResult(
                performed = false,
                message = "unknown nodeId: ${request.nodeId} (the node may have left the composition; capture the tree again)",
            )
        node.performSemanticsAction(request)
    }
}

/**
 * A node id addresses the same layout node in both trees, but a node merged into its parent is only
 * present in the unmerged one — so a lookup that missed in the merged tree still has somewhere to
 * look. The merged tree comes first because its config carries the actions a caller saw advertised.
 */
private fun SemanticsOwner.findNodeById(id: Int): SemanticsNode? = getAllSemanticsNodes(mergingEnabled = true, skipDeactivatedNodes = true).firstOrNull { it.id == id }
    ?: getAllSemanticsNodes(mergingEnabled = false, skipDeactivatedNodes = true).firstOrNull { it.id == id }

/**
 * Registers a [SemanticsOwner] the caller already holds and keeps alive itself.
 *
 * The Android and desktop probes find owners for you; reach for this when you host a `ComposeScene`
 * yourself and so already hold one. iOS and web have no probe because Compose Multiplatform exposes
 * no owner for their entry points — see the Compose Semantics Inspector guide.
 *
 * @see SemanticsOwnerNodeSource
 */
fun ComposeNodeSourceRegistry.registerSemanticsOwner(
    owner: SemanticsOwner,
    sourceId: String,
    label: String,
    density: Float,
    windowOffset: () -> Offset = { Offset.Zero },
    uiThread: ComposeUiThread = MainDispatcherComposeUiThread,
): ComposeNodeSourceRegistry.Registration = register(
    SemanticsOwnerNodeSource(
        sourceId = sourceId,
        owner = { owner },
        label = { label },
        density = { density },
        windowOffset = windowOffset,
        uiThread = uiThread,
    ),
)
