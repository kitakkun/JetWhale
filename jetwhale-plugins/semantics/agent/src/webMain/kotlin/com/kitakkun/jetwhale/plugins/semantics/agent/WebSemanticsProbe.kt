package com.kitakkun.jetwhale.plugins.semantics.agent

import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeRoot
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeCaptureOptions
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction
import kotlinx.browser.document
import org.w3c.dom.Element
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.MutationObserver
import org.w3c.dom.asList
import org.w3c.dom.get
import kotlin.js.toList

/**
 * Registers every Compose viewport in the page with [ComposeNodeSourceRegistry], so the Compose
 * Semantics Inspector can read a Compose for Web app, on JS and Wasm alike.
 *
 * Call it once at startup, before or after `ComposeViewport` and `startJetWhale`. It registers the
 * viewports already in the document and then watches the document, so a viewport created later is
 * registered as it appears and one whose element leaves the document is unregistered.
 *
 * Compose for Web hands out no `SemanticsOwner`, so the probe reads what Compose puts in the page
 * instead: the accessibility DOM a `ComposeViewport` keeps in its shadow root for screen readers,
 * one element per node of the merged semantics tree. That DOM carries a node's text, content
 * description, test tag, role, bounds and click, and nothing more, so that is what a capture
 * reports. `Click` runs through the element. `LongClick` and `ScrollBy` are a mouse press and a
 * wheel turned on the canvas at the node's center, and `SetText` and `InsertText` are typed into
 * the input Compose opens for a focused field. A viewport created with `isA11YEnabled = false`
 * keeps no such DOM; it is registered all the same, and its captures say why they come back empty.
 *
 * The install is process-wide and idempotent: calling it twice returns the same handle. Closing it
 * unregisters every viewport and stops watching the document.
 */
fun installJetWhaleSemanticsProbe(): AutoCloseable = WebSemanticsProbe.install()

private object WebSemanticsProbe {
    private var installation: Installation? = null
    private var issuedSourceIdCount = 0

    fun install(): AutoCloseable = installation ?: Installation().also { installation = it }

    fun uninstalled(installation: Installation) {
        if (this.installation === installation) this.installation = null
    }

    /** Ids stay unique across installations, so a viewport registered again is never taken for one still registered. */
    fun nextSourceId(): String = "web-viewport-${++issuedSourceIdCount}"

    class Installation : AutoCloseable {
        private class TrackedViewport(val shadowHost: Element, val registration: ComposeNodeSourceRegistry.Registration)

        private val trackedViewports = mutableListOf<TrackedViewport>()

        @OptIn(ExperimentalWasmJsInterop::class)
        private val observer = MutationObserver { mutationRecords, _ ->
            untrackDetachedViewports()
            mutationRecords.toList().forEach { mutationRecord ->
                mutationRecord.addedNodes.asList().forEach { node -> (node as? Element)?.let(::trackViewportsIn) }
            }
        }

        init {
            document.documentElement?.let { documentElement ->
                observer.observe(documentElement, subtreeMutationObserverInit(includeAttributeAndTextChanges = false))
                trackViewportsIn(documentElement)
            }
        }

        /**
         * Registers every viewport at or under [element]. `ComposeViewport` builds the whole of its
         * DOM before the document hears of it, so by the time [observer] reports the element added,
         * the shadow root and what Compose puts in it are already there to recognize. An element
         * removed again before the observer heard of it is reported added all the same, and left
         * alone here.
         */
        private fun trackViewportsIn(element: Element) {
            if (!element.isConnected) return
            val candidates = listOf(element) + element.querySelectorAll("div").asList().mapNotNull { it as? Element }
            candidates.forEach { candidate ->
                if (trackedViewports.none { it.shadowHost == candidate }) {
                    candidate.createViewportNodeSource()?.let { source ->
                        trackedViewports += TrackedViewport(candidate, ComposeNodeSourceRegistry.register(source))
                    }
                }
            }
        }

        private fun untrackDetachedViewports() {
            val detachedViewports = trackedViewports.filter { !it.shadowHost.isConnected }
            detachedViewports.forEach { it.registration.close() }
            trackedViewports -= detachedViewports.toSet()
        }

        override fun close() {
            observer.disconnect()
            trackedViewports.forEach { it.registration.close() }
            trackedViewports.clear()
            WebSemanticsProbe.uninstalled(this)
        }
    }
}

/**
 * A new source, under a new source id, for the `ComposeViewport` this element hosts, or `null` when
 * it hosts none.
 *
 * `ComposeViewport` builds `viewportContainer > div > shadow host`, and the host's open shadow root
 * holds a `div` with the canvas, followed by the accessibility DOM when accessibility is on.
 */
private fun Element.createViewportNodeSource(): ComposeNodeSource? {
    val shadowRoot = shadowRoot ?: return null
    val canvasContainer = shadowRoot.children.asList().firstOrNull { child ->
        child.tagName == "DIV" && child.children[0] is HTMLCanvasElement
    } ?: return null
    val canvas = canvasContainer.children[0] as? HTMLElement ?: return null
    val sourceId = WebSemanticsProbe.nextSourceId()
    val accessibilityRoot = canvasContainer.querySelector("#$ACCESSIBILITY_ROOT_ID") as? HTMLElement
        ?: return ViewportWithoutAccessibilityDomNodeSource(sourceId)
    return ComposeViewportNodeSource(
        sourceId = sourceId,
        label = viewportLabel(),
        canvas = canvas,
        accessibilityRoot = accessibilityRoot,
    )
}

/** Names the viewport after the element the app handed `ComposeViewport`, the one thing in it the app chose. */
private fun Element.viewportLabel(): String {
    val viewportContainer = parentElement?.parentElement ?: return "ComposeViewport"
    return if (viewportContainer.id.isNotEmpty()) "ComposeViewport #${viewportContainer.id}" else "ComposeViewport <${viewportContainer.tagName.lowercase()}>"
}

/** The id Compose gives the accessibility DOM's root element. */
private const val ACCESSIBILITY_ROOT_ID: String = "cmp_a11y_root"

/** A viewport Compose keeps no accessibility DOM for: it is there to say so on every capture. */
private class ViewportWithoutAccessibilityDomNodeSource(override val sourceId: String) : ComposeNodeSource {
    override suspend fun capture(options: NodeTreeCaptureOptions): ComposeRoot? = error(NO_ACCESSIBILITY_DOM)

    override suspend fun performAction(request: PerformNodeAction): NodeActionResult = NodeActionResult(performed = false, message = NO_ACCESSIBILITY_DOM)
}

private const val NO_ACCESSIBILITY_DOM: String =
    "this ComposeViewport keeps no accessibility DOM, so there is nothing to read: it was created with " +
        "isA11YEnabled = false. Leave that at its default to inspect it"
