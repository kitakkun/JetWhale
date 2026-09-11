package com.kitakkun.jetwhale.plugins.semantics.host

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kitakkun.jetwhale.plugins.semantics.protocol.HighlightNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.HighlightResult
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Which node the device should be drawing a box over, given what the user is doing.
 *
 * Hover wins over selection while the pointer is on a row, because that is the question being asked
 * at that moment — "which one is this?" — and the selection is still there when the pointer leaves.
 * The toggle is checked here rather than at every call site so that turning it off is one answer:
 * nothing.
 */
internal fun highlightTarget(enabled: Boolean, selected: NodeKey?, hovered: NodeKey?): NodeKey? = when {
    !enabled -> null
    else -> hovered ?: selected
}

/**
 * Keeps the device's highlight in step with the host's tree view.
 *
 * It owns the one fact the screen cannot recompute — which root is currently showing a box — because
 * a root only ever clears its own overlay: moving the highlight from a dialog to the screen behind it
 * has to tell the dialog to stop showing one.
 */
internal class NodeHighlightController(
    /** Outlives the tree view, so leaving the screen can still take the box down. */
    private val scope: CoroutineScope,
    private val send: suspend (HighlightNode) -> HighlightResult,
) {
    private var shownIn: String? = null

    /** Keeps the current target alive; a new target replaces it. */
    private var holding: Job? = null

    /** Why the app is not showing what was asked for, for the screen's status line. */
    var statusMessage: String? by mutableStateOf(null)
        private set

    /**
     * Points the device at [target], or takes the box down when it is `null`.
     *
     * The waiting lives here rather than in the screen that reported the target. A pointer crossing
     * rows on its way somewhere must not send a request per row, and the app drops a highlight it
     * has not heard about for [HIGHLIGHT_TTL_MILLIS], so one left standing has to be renewed for as
     * long as it stands — neither is something a composition should be holding open.
     */
    fun setTarget(target: NodeKey?) {
        holding?.cancel()
        holding = scope.launch {
            // Only a target waits: taking the box down is an answer the user already committed to.
            if (target != null) delay(HIGHLIGHT_HOVER_DEBOUNCE_MILLIS)
            if (!show(target)) return@launch
            while (true) {
                delay(HIGHLIGHT_RENEWAL_MILLIS)
                if (!show(target)) return@launch
            }
        }
    }

    /**
     * Draws [target] on the device, or clears the highlight when it is `null`.
     *
     * @return whether the app is showing it — `false` for a root that refused, so a caller renewing
     *   the highlight stops asking rather than repeating a request that will not start working.
     */
    suspend fun show(target: NodeKey?): Boolean {
        val leaving = shownIn
        if (leaving != null && leaving != target?.rootId) {
            clearIn(leaving)
        }
        if (target == null) {
            statusMessage = null
            return false
        }
        // Recorded before the answer comes back, not after: a new target cancels this call, and a
        // request cancelled after the app drew the box would otherwise leave the controller thinking
        // that root has nothing to clear — stranding a box there until its own TTL runs out.
        shownIn = target.rootId
        val result = try {
            send(HighlightNode(rootId = target.rootId, nodeId = target.nodeId, ttlMs = HIGHLIGHT_TTL_MILLIS))
        } catch (e: JetWhaleMessagingException) {
            shownIn = null
            statusMessage = "Highlight failed: ${e.message}"
            return false
        }
        shownIn = if (result.shown) target.rootId else null
        statusMessage = if (result.shown) null else result.message
        return result.shown
    }

    /** Takes the box down, wherever it is. Safe to call with nothing showing. */
    suspend fun clear() {
        show(null)
    }

    /**
     * Takes the box down without waiting for the app to answer, for the callers that cannot suspend:
     * the tree view leaving the composition and the plugin instance being disposed.
     *
     * Started in place and shielded from cancellation: the runtime cancels the instance's scope
     * right after `onDispose` returns, which would otherwise stop the clear before its request left,
     * leaving the box up until the app's own TTL. A peer closed in the meantime fails the send, and
     * that is caught where the send is.
     */
    fun clearAsync() {
        holding?.cancel()
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) { clear() }
        }
    }

    private suspend fun clearIn(rootId: String) {
        shownIn = null
        try {
            send(HighlightNode(rootId = rootId, nodeId = null, ttlMs = HIGHLIGHT_TTL_MILLIS))
        } catch (e: JetWhaleMessagingException) {
            // The window that was showing the box is unreachable, which is also how it stops showing
            // one: the overlay went away with it, and the agent's own TTL covers the rest.
            statusMessage = "Clearing the highlight failed: ${e.message}"
        }
    }
}

/**
 * How long the app keeps a highlight without hearing from the host again.
 *
 * Long enough that a user reading a row is never left without the box, short enough that a host that
 * crashed does not leave one in the app for the rest of the session.
 */
internal const val HIGHLIGHT_TTL_MILLIS: Long = 30_000

/** Renewed well inside the TTL so a slow round trip cannot let it lapse while the row is still selected. */
internal const val HIGHLIGHT_RENEWAL_MILLIS: Long = HIGHLIGHT_TTL_MILLIS / 3

/**
 * How long the pointer has to rest on a row before it is highlighted.
 *
 * Without it, running the pointer down the tree would send a request per row it crossed, each one a
 * hop to the app's main thread.
 */
internal const val HIGHLIGHT_HOVER_DEBOUNCE_MILLIS: Long = 80
