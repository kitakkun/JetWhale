package com.kitakkun.jetwhale.plugins.semantics.agent

import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeBounds
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import kotlin.time.Duration

/**
 * The box the host draws over one node of a viewport, as an element over the canvas.
 *
 * It goes next to the canvas in the viewport's shadow root, positioned from the composition's
 * origin, so the bounds a capture reports place it as they are and it scrolls with the canvas. In
 * the shadow root it stays out of the document the app's own code walks, and it is not part of the
 * accessibility DOM, so a capture never reports it.
 *
 * The invariant it holds is that a box on screen is in the right place. Compose moves a node by
 * rewriting the accessibility DOM, and an embedded HTML element moves with what changes or scrolls
 * in [htmlInteropContainer], so the box is placed again on each of those, and taken down once its
 * node is gone.
 */
internal class NodeHighlightBox(
    private val accessibilityDom: AccessibilityDom,
    private val htmlInteropContainer: HtmlInteropContainer?,
) {
    private var box: HTMLElement? = null
    private var resolveBounds: (() -> NodeBounds?)? = null
    private var watches: List<AutoCloseable> = emptyList()
    private var expiryTimeoutId: Int? = null

    /**
     * Draws the box over the node [resolveBounds] reports, `null` once the node is gone, replacing
     * whatever was showing, and schedules it to clear itself after [ttl].
     */
    @OptIn(ExperimentalWasmJsInterop::class)
    fun show(ttl: Duration, resolveBounds: () -> NodeBounds?) {
        if (box == null) {
            val created = document.createElement("div") as HTMLElement
            created.style.cssText = BOX_STYLE
            accessibilityDom.canvasContainer?.appendChild(created)
            watches = listOfNotNull(
                accessibilityDom.watchChanges(::moveToCurrentBounds),
                htmlInteropContainer?.watchChangesAndScrolls(::moveToCurrentBounds),
            )
            box = created
        }
        this.resolveBounds = resolveBounds
        moveToCurrentBounds()

        expiryTimeoutId?.let(window::clearTimeout)
        expiryTimeoutId = window.setTimeout(
            {
                clear()
                null
            },
            ttl.inWholeMilliseconds.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        )
    }

    /** Takes the box down and stops following the DOM. Doing this with nothing showing is a no-op. */
    fun clear() {
        expiryTimeoutId?.let(window::clearTimeout)
        expiryTimeoutId = null
        watches.forEach(AutoCloseable::close)
        watches = emptyList()
        box?.remove()
        box = null
        resolveBounds = null
    }

    /** Puts the box where the node is now, or takes it down when the node is gone or has no area. */
    private fun moveToCurrentBounds() {
        val shownBox = box ?: return
        val bounds = resolveBounds?.invoke()
        if (bounds == null || bounds.isEmpty) {
            clear()
            return
        }
        shownBox.style.left = "${bounds.left}px"
        shownBox.style.top = "${bounds.top}px"
        shownBox.style.width = "${bounds.width}px"
        shownBox.style.height = "${bounds.height}px"
    }
}

/**
 * The magenta the Android agent draws with, which reads against the greys, blues and whites of
 * ordinary app chrome. Positioned against the canvas's container, whose origin is the composition's,
 * and above the HTML elements Compose lays over the canvas.
 */
private const val BOX_STYLE: String = "position: absolute; box-sizing: border-box; pointer-events: none; z-index: 2147483647; " +
    "border: 2px solid rgb(255, 61, 113); background: rgba(255, 61, 113, 0.25);"
