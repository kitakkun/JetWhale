package com.kitakkun.jetwhale.plugins.semantics.protocol

import com.kitakkun.jetwhale.protocol.messaging.JetWhaleRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Pointing at one node on the device itself, so that reading a row in the host and finding the thing
// it names on screen is one step rather than a hunt through the layout.
//
// Only a root that has somewhere to draw can answer this — an Android window. A composition read
// through its `SemanticsOwner` alone reports `shown = false` with a message saying so.

/**
 * Draws a box over one node on the device, or clears it.
 *
 * @param nodeId `null` clears whatever this root is showing.
 * @param ttlMs the highlight clears itself after this long without being renewed, so a host that
 *   goes away cannot leave a box on the app's screen for the rest of the process's life.
 */
@SerialName("node/highlight")
@Serializable
data class HighlightNode(
    val rootId: String,
    val nodeId: Int?,
    val ttlMs: Long,
) : JetWhaleRequest<HighlightResult>

/**
 * Separate from the request so a node that has gone away is an answer, not a transport failure.
 *
 * @param retryLater whether a refusal is expected to lift on its own — the node is there but has no
 *   area right now, as one scrolled out of a lazy list has — so a host keeping the highlight alive
 *   should keep asking rather than give up on it.
 */
@Serializable
data class HighlightResult(
    val shown: Boolean,
    val message: String? = null,
    val retryLater: Boolean = false,
)
