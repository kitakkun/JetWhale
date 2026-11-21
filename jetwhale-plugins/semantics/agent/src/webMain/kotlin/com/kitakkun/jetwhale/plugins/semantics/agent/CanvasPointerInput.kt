package com.kitakkun.jetwhale.plugins.semantics.agent

import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeBounds
import kotlinx.coroutines.delay
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Pointer input for one viewport, synthesized on its canvas at a node's position: the route to the
 * actions the accessibility DOM has no listener for.
 *
 * Compose reads the canvas's `pointer*` and `wheel` events and finds what they hit from their offset
 * into the canvas, so an event dispatched to the canvas reaches the composition whatever the page
 * lays over it. The point is the center of the node's bounds, which Compose has clipped to the
 * node's ancestors. What takes the event is whatever Compose draws at that point: the node, a child
 * of it that handles the input itself, or a container around it that handles what the node does not.
 */
internal class CanvasPointerInput(private val canvas: HTMLElement, private val accessibilityDom: AccessibilityDom) {
    /**
     * Turns the mouse wheel at the node's center by [scrollX] and [scrollY] CSS pixels, which
     * Compose for Web scrolls by as many dp, the same length. Each axis takes an event of its own,
     * because Compose reads a wheel event along its larger delta only, and the second waits out
     * [WHEEL_LATCH]: until then Compose would hand it to whatever the first one scrolled.
     */
    suspend fun scrollBy(element: HTMLElement, scrollX: Float, scrollY: Float): NodeActionResult {
        if (scrollX == 0f && scrollY == 0f) return NodeActionResult(performed = false, message = "ScrollBy needs a scrollX or scrollY other than 0")
        val bounds = accessibilityDom.boundsOf(element)?.takeUnless(NodeBounds::isEmpty) ?: return NODE_WITHOUT_AREA
        if (scrollY != 0f) dispatchAtCenterOf(bounds) { clientX, clientY -> pixelWheelEvent(clientX, clientY, deltaX = 0.0, deltaY = scrollY.toDouble()) }
        if (scrollX != 0f && scrollY != 0f) delay(WHEEL_LATCH)
        if (scrollX != 0f) dispatchAtCenterOf(bounds) { clientX, clientY -> pixelWheelEvent(clientX, clientY, deltaX = scrollX.toDouble(), deltaY = 0.0) }
        return NodeActionResult(
            performed = true,
            message = "the page does not say what scrolls: the wheel turned at this node's center and moved whatever scrolls there, the node or a container around it, if anything does",
        )
    }

    /**
     * Presses the primary mouse button at the node's center, holds it for [LONG_PRESS_HOLD], and
     * releases it. The release is sent even when the hold is cancelled: until Compose sees one, it
     * drops every move of the real mouse, whose button is up.
     */
    suspend fun longClick(element: HTMLElement): NodeActionResult {
        val bounds = accessibilityDom.boundsOf(element)?.takeUnless(NodeBounds::isEmpty) ?: return NODE_WITHOUT_AREA
        dispatchAtCenterOf(bounds) { clientX, clientY -> primaryMousePointerEvent("pointerdown", clientX, clientY, buttons = 1) }
        try {
            delay(LONG_PRESS_HOLD)
        } finally {
            dispatchAtCenterOf(bounds) { clientX, clientY -> primaryMousePointerEvent("pointerup", clientX, clientY, buttons = 0) }
        }
        return NodeActionResult(
            performed = true,
            message = "the page does not say whether this node takes a long click: a press was held at its center, which a node with only OnClick takes as a click",
        )
    }

    /**
     * Dispatches to the canvas the event [createEvent] builds for the center of [bounds], in the
     * browser viewport's coordinates as of now, so the event lands at the same point in the
     * composition however the page has scrolled since an earlier one.
     */
    private fun dispatchAtCenterOf(bounds: NodeBounds, createEvent: (clientX: Double, clientY: Double) -> Event) {
        val canvasRect = canvas.getBoundingClientRect()
        canvas.dispatchEvent(createEvent(canvasRect.left + bounds.centerX, canvasRect.top + bounds.centerY))
    }
}

private val NODE_WITHOUT_AREA: NodeActionResult = NodeActionResult(
    performed = false,
    message = "the node has no area in its viewport to put a pointer on (it is invisible, unmeasured, or clipped away)",
)

/**
 * How long Compose keeps handing every wheel event to the scrollable the last one moved, whatever
 * the event's axis: for as long as that scroll animates, at most 100 ms, and no less than 50 ms.
 * The rest is margin for slow frames.
 */
private val WHEEL_LATCH: Duration = 300.milliseconds

/** Compose for Web takes a press for a long one after 500 ms; the rest is margin for the timers of a busy page. */
private val LONG_PRESS_HOLD: Duration = 750.milliseconds

@OptIn(ExperimentalWasmJsInterop::class)
private fun pixelWheelEvent(clientX: Double, clientY: Double, deltaX: Double, deltaY: Double): Event = js("new WheelEvent('wheel', { clientX: clientX, clientY: clientY, deltaX: deltaX, deltaY: deltaY, deltaMode: 0, cancelable: true })")

/** A mouse's primary button: a touch pointer would switch the whole app to Compose's touch input mode. */
@OptIn(ExperimentalWasmJsInterop::class)
private fun primaryMousePointerEvent(type: String, clientX: Double, clientY: Double, buttons: Int): Event = js("new PointerEvent(type, { pointerId: 1, pointerType: 'mouse', isPrimary: true, clientX: clientX, clientY: clientY, button: 0, buttons: buttons, cancelable: true })")
