package com.kitakkun.jetwhale.plugins.semantics.agent

import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeRoot
import com.kitakkun.jetwhale.plugins.semantics.protocol.HighlightResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeAction
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeBounds
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeCaptureOptions
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import kotlin.time.Duration

/**
 * Reads one `ComposeViewport` as a single root, through the [AccessibilityDom] Compose keeps for it.
 *
 * The tree is the merged semantics tree, the only one Compose writes into the page, so
 * [NodeTreeCaptureOptions.merged] has no effect. Bounds are CSS pixels, the unit every browser tool
 * takes, and the root's density is `1`: a CSS pixel is what Compose for Web calls a dp. On screen
 * means in the browser's viewport.
 *
 * Everything runs on the page's one thread, so no call hops anywhere.
 */
internal class ComposeViewportNodeSource(
    override val sourceId: String,
    private val label: String,
    private val canvas: HTMLElement,
    accessibilityRoot: HTMLElement,
) : RegistryAwareNodeSource,
    NodeHighlightSource {
    private val accessibilityDom = AccessibilityDom(accessibilityRoot)
    private val elementIds = AccessibilityElementIds()
    private val changeWatcher = AccessibilityDomChangeWatcher(accessibilityDom)
    private val highlightBox = NodeHighlightBox(accessibilityDom)
    private var unregistered = false

    override fun onUnregistered() {
        unregistered = true
        changeWatcher.close()
        highlightBox.clear()
    }

    override suspend fun capture(options: NodeTreeCaptureOptions): ComposeRoot? {
        if (!accessibilityDom.isConnected) return null
        changeWatcher.awaitSettled()
        if (!accessibilityDom.isConnected) return null

        val canvasRect = canvas.getBoundingClientRect()
        val canvasLeft = canvasRect.left.toFloat()
        val canvasTop = canvasRect.top.toFloat()
        val capture = AccessibilityDomCapture(
            options = options,
            accessibilityDom = accessibilityDom,
            elementIds = elementIds,
            canvasLeft = canvasLeft,
            canvasTop = canvasTop,
            pageViewport = NodeBounds(left = 0f, top = 0f, right = window.innerWidth.toFloat(), bottom = window.innerHeight.toFloat()),
        )
        return ComposeRoot(
            rootId = sourceId,
            label = label,
            density = 1f,
            windowOffsetX = canvasLeft,
            windowOffsetY = canvasTop,
            node = accessibilityDom.semanticsRootElement?.let { semanticsRootElement ->
                elementIds.runCaptureReleasingUnseenElements { capture.composeNodeOf(semanticsRootElement, depth = 0) }
            },
        )
    }

    override suspend fun performAction(request: PerformNodeAction): NodeActionResult {
        if (!accessibilityDom.isConnected) return NodeActionResult(performed = false, message = VIEWPORT_GONE)
        val element = elementIds.elementOf(request.nodeId)?.takeIf(accessibilityDom::contains)
            ?: return NodeActionResult(
                performed = false,
                message = "unknown nodeId: ${request.nodeId} (the node may have left this viewport; capture the tree again)",
            )
        if (request.action != NodeAction.Click) {
            return NodeActionResult.notSupported("${request.action} is not available on the web: Compose's accessibility DOM carries a node's click and no other action")
        }
        if (!element.isClickableNode) return NodeActionResult.notSupported("the node is not clickable")
        // Compose's click listener runs the node's OnClick without checking whether the node is
        // enabled, and the page does not say, so a disabled node is clicked too.
        element.click()
        changeWatcher.expectChangeFromAction()
        return NodeActionResult(performed = true)
    }

    override suspend fun highlight(nodeId: Int?, ttl: Duration): HighlightResult {
        if (nodeId == null || unregistered || !accessibilityDom.isConnected) {
            highlightBox.clear()
            return HighlightResult(shown = false, message = VIEWPORT_GONE.takeIf { nodeId != null })
        }
        val resolveBounds = { elementIds.elementOf(nodeId)?.takeIf(accessibilityDom::contains)?.let(accessibilityDom::boundsOf) }
        val bounds = resolveBounds()
        if (bounds == null) {
            highlightBox.clear()
            return HighlightResult(
                shown = false,
                message = "node $nodeId is not in this viewport right now (it may have left the composition, or not have been captured yet)",
                retryLater = true,
            )
        }
        if (bounds.isEmpty) {
            highlightBox.clear()
            return HighlightResult(
                shown = false,
                message = "node $nodeId has no area in this viewport (it is invisible, unmeasured, or fully clipped)",
                retryLater = true,
            )
        }
        highlightBox.show(ttl = ttl, resolveBounds = resolveBounds)
        return HighlightResult(shown = true)
    }
}

private const val VIEWPORT_GONE: String = "the viewport is no longer in the page"
