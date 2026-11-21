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
    private val canvasPointerInput = CanvasPointerInput(canvas, accessibilityDom)
    private val fieldTextInput = FieldTextInput(accessibilityDom)
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
        // A pointer lands where the DOM last placed the node, which right after an earlier action
        // is where the node was before that action took effect; and until a scroll the action
        // started has finished, Compose hands every wheel event to the scrollable it moved.
        if (request.action == NodeAction.LongClick || request.action == NodeAction.ScrollBy) changeWatcher.awaitSettled()
        if (!accessibilityDom.isConnected) return NodeActionResult(performed = false, message = VIEWPORT_GONE)
        val element = elementIds.elementOf(request.nodeId)
            ?: return NodeActionResult(
                performed = false,
                message = "unknown nodeId: ${request.nodeId} (the node may have left this viewport; capture the tree again)",
            )
        val result = when (request.action) {
            NodeAction.Click -> {
                val roleHidingClick = element.getAttribute("role")?.takeIf { it in ROLES_HIDING_CLICK }
                if (!element.isClickableNode && roleHidingClick == null) return NodeActionResult.notSupported("the node is not clickable")
                // Compose's click listener runs the node's OnClick without checking whether the node
                // is enabled, and the page does not say, so a disabled node is clicked too.
                accessibilityDom.dispatchNonBubblingClick(element)
                NodeActionResult(
                    performed = true,
                    message = roleHidingClick?.let { "the page marks this node `$it`, which hides whether it takes a click: the click was sent, and does nothing if the node has no OnClick" },
                )
            }
            NodeAction.LongClick -> canvasPointerInput.longClick(element)
            NodeAction.ScrollBy -> canvasPointerInput.scrollBy(element, scrollX = request.scrollX, scrollY = request.scrollY)
            NodeAction.SetText -> request.text?.let { fieldTextInput.replaceText(element, it) } ?: NodeActionResult.missingArgument(NodeAction.SetText, "text")
            NodeAction.InsertText -> request.text?.let { fieldTextInput.insertTextAtCursor(element, it) } ?: NodeActionResult.missingArgument(NodeAction.InsertText, "text")
            NodeAction.BringIntoView -> NodeActionResult.notSupported(
                "BringIntoView is not available on the web: the page gives a node's bounds only as far as they show, which does not tell how far its containers " +
                    "would have to scroll. Scroll a container with ScrollBy and capture again",
            )
            NodeAction.ImeAction,
            NodeAction.ScrollToIndex,
            NodeAction.RequestFocus,
            NodeAction.Dismiss,
            NodeAction.Expand,
            NodeAction.Collapse,
            -> NodeActionResult.notSupported("${request.action} is not available on the web: the page carries a node's click alone, and no input event stands in for this action")
        }
        if (result.performed) changeWatcher.expectChangeFromAction()
        return result
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
 * The roles Compose writes over `button` for a node that also has `OnClick`, still wiring the click
 * listener into its element: a clickable heading or collection reads as one of these alone.
 */
private val ROLES_HIDING_CLICK: Set<String> = setOf("heading", "list", "grid")

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
        area = area.clippedByOverflowOf(ancestor)
        ancestor = ancestor.parentElementOrShadowHost()
    }
    return area
}

/**
 * These bounds cut down to what [element] lets its overflow show, axis by axis: an axis whose
 * overflow is `visible` is not clipped, which `overflow: clip` allows on one axis alone. The clip is
 * the element's padding box, inside its borders and scrollbars.
 */
private fun NodeBounds.clippedByOverflowOf(element: Element): NodeBounds {
    val style = window.getComputedStyle(element)
    val clipsX = style.getPropertyValue("overflow-x") != "visible"
    val clipsY = style.getPropertyValue("overflow-y") != "visible"
    if (!clipsX && !clipsY) return this
    val rect = element.getBoundingClientRect()
    val paddingBoxLeft = (rect.left + element.clientLeft).toFloat()
    val paddingBoxTop = (rect.top + element.clientTop).toFloat()
    return intersect(
        NodeBounds(
            left = if (clipsX) paddingBoxLeft else left,
            top = if (clipsY) paddingBoxTop else top,
            right = if (clipsX) paddingBoxLeft + element.clientWidth else right,
            bottom = if (clipsY) paddingBoxTop + element.clientHeight else bottom,
        ),
    )
}

private fun Element.parentElementOrShadowHost(): Element? = parentElement ?: (parentNode as? ShadowRoot)?.host

private const val VIEWPORT_GONE: String = "the viewport is no longer in the page"
