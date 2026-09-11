package com.kitakkun.jetwhale.plugins.semantics.agent

import com.kitakkun.jetwhale.plugins.semantics.protocol.HighlightNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.HighlightResult
import kotlinx.coroutines.CancellationException
import kotlin.time.Duration.Companion.milliseconds

// Answers the highlight request, kept apart from the capture and action handlers because the
// capability is optional: a root can be pointed at on the device only when its source implements
// NodeHighlightSource.

/** Shows one node on the device, or clears the root's highlight, saying why when it cannot. */
internal suspend fun showHighlight(request: HighlightNode): HighlightResult {
    val source = sourceOf(request.rootId)
        ?: return HighlightResult(shown = false, message = unknownRoot(request.rootId))
    val highlightSource = source as? NodeHighlightSource
        ?: return HighlightResult(shown = false, message = ROOT_WITHOUT_HIGHLIGHT)
    // A box that is shown has to be able to expire: the TTL is what stops a host that dies without
    // saying so from leaving one on the app's screen. A clear needs none, so only a show is checked.
    if (request.nodeId != null && request.ttlMs <= 0) {
        return HighlightResult(shown = false, message = "ttlMs must be positive to show a highlight, but was ${request.ttlMs}")
    }
    return try {
        highlightSource.highlight(nodeId = request.nodeId, ttl = request.ttlMs.milliseconds)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        HighlightResult(shown = false, message = "highlighting failed: ${e.describeFailure()}")
    }
}

/**
 * Takes every highlight down.
 *
 * Called when the host disconnects or disables the plugin: the box belongs to a host that is looking
 * at the tree, so it must not outlive one that has stopped. The per-root TTL stays regardless — it is
 * the net for a host that dies without saying so.
 */
internal suspend fun clearAllHighlights() {
    for (source in ComposeNodeSourceRegistry.sources) {
        val highlightSource = source as? NodeHighlightSource ?: continue
        try {
            highlightSource.highlight(nodeId = null, ttl = CLEAR_TTL)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // A root that cannot be reached has nothing left to clear: its window is gone, and with
            // it the overlay. One unreachable root must not stop the others from being cleared.
        }
    }
}

/** Nothing is left up by a clear, so the TTL a clear carries is only there to satisfy the signature. */
private val CLEAR_TTL = 0.milliseconds

private const val ROOT_WITHOUT_HIGHLIGHT: String = "this root cannot be highlighted (it is a composition read through its SemanticsOwner, which has no window to draw in)"
