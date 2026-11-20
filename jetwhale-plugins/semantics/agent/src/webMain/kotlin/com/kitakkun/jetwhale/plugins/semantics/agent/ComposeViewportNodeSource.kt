package com.kitakkun.jetwhale.plugins.semantics.agent

import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeRoot
import com.kitakkun.jetwhale.plugins.semantics.protocol.HighlightResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeAction
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeBounds
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeCaptureOptions
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.ShadowRoot
import org.w3c.dom.events.Event
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
    private val elementIds = AccessibilityElementIds(accessibilityDom)
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
            canvasClipBounds = canvas.clipBounds(),
        )
        elementIds.releaseElementsRemovedFromDom()
        return ComposeRoot(
            rootId = sourceId,
            label = label,
            density = 1f,
            windowOffsetX = canvasLeft,
            windowOffsetY = canvasTop,
            node = accessibilityDom.semanticsRootElement?.let { capture.composeNodeOf(it, depth = 0) },
        )
    }

    override suspend fun performAction(request: PerformNodeAction): NodeActionResult {
        if (!accessibilityDom.isConnected) return NodeActionResult(performed = false, message = VIEWPORT_GONE)
        val element = elementIds.elementOf(request.nodeId)
            ?: return NodeActionResult(
                performed = false,
                message = "unknown nodeId: ${request.nodeId} (the node may have left this viewport; capture the tree again)",
            )
        if (request.action != NodeAction.Click) {
            return NodeActionResult.notSupported("${request.action} is not available on the web: Compose's accessibility DOM carries a node's click and no other action")
        }
        val roleHidingClick = element.getAttribute("role")?.takeIf { it in ROLES_HIDING_CLICK }
        if (!element.isClickableNode && roleHidingClick == null) return NodeActionResult.notSupported("the node is not clickable")
        // Compose's click listener runs the node's OnClick without checking whether the node is
        // enabled, and the page does not say, so a disabled node is clicked too.
        element.dispatchEvent(nonBubblingClickEvent())
        changeWatcher.expectChangeFromAction()
        return NodeActionResult(
            performed = true,
            message = roleHidingClick?.let { "the page marks this node `$it`, which hides whether it takes a click: the click was sent, and does nothing if the node has no OnClick" },
        )
    }

    override suspend fun highlight(nodeId: Int?, ttl: Duration): HighlightResult {
        if (nodeId == null || unregistered || !accessibilityDom.isConnected) {
            highlightBox.clear()
            return HighlightResult(shown = false, message = VIEWPORT_GONE.takeIf { nodeId != null })
        }
        val resolveBounds = { elementIds.elementOf(nodeId)?.let(accessibilityDom::boundsOf) }
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

/**
 * Where this element can be seen right now, in CSS pixels from the browser viewport's corner: the
 * browser viewport, cut down by every ancestor that clips its overflow, through the viewport's
 * shadow root and up the app's own elements. Empty while CSS hides the element.
 *
 * The walk stops below `<body>`: the document's own overflow is the browser viewport's, which a
 * `<body>` with `overflow: hidden` hands on to it rather than clipping itself.
 */
private fun HTMLElement.clipBounds(): NodeBounds {
    if (window.getComputedStyle(this).visibility != "visible") return NodeBounds(left = 0f, top = 0f, right = 0f, bottom = 0f)
    var area = NodeBounds(left = 0f, top = 0f, right = window.innerWidth.toFloat(), bottom = window.innerHeight.toFloat())
    var ancestor = parentElementOrShadowHost()
    while (ancestor != null && ancestor != document.body && ancestor != document.documentElement) {
        val style = window.getComputedStyle(ancestor)
        if (style.getPropertyValue("overflow-x") != "visible" || style.getPropertyValue("overflow-y") != "visible") {
            val rect = ancestor.getBoundingClientRect()
            area = area.intersect(NodeBounds(left = rect.left.toFloat(), top = rect.top.toFloat(), right = rect.right.toFloat(), bottom = rect.bottom.toFloat()))
        }
        ancestor = ancestor.parentElementOrShadowHost()
    }
    return area
}

private fun Element.parentElementOrShadowHost(): Element? = parentElement ?: (parentNode as? ShadowRoot)?.host

private const val VIEWPORT_GONE: String = "the viewport is no longer in the page"

/**
 * The roles Compose writes over `button` for a node that also has `OnClick`, still wiring the click
 * listener into its element: a clickable heading or collection reads as one of these alone.
 */
private val ROLES_HIDING_CLICK: Set<String> = setOf("heading", "list", "grid")

/**
 * A click for one element alone. `HTMLElement.click()` bubbles, and Compose nests the elements as
 * it nests the nodes and puts a listener on every clickable one, so a click on a button inside a
 * clickable row would run the row's `OnClick` as well.
 */
@OptIn(ExperimentalWasmJsInterop::class)
private fun nonBubblingClickEvent(): Event = js("new MouseEvent('click', { bubbles: false, cancelable: true })")
