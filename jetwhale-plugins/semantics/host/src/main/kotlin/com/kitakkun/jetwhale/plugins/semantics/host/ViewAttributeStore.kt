package com.kitakkun.jetwhale.plugins.semantics.host

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.plugins.semantics.protocol.GetViewAttributes
import com.kitakkun.jetwhale.plugins.semantics.protocol.SetViewAttribute
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttribute
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeResponse
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeValue
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The attributes of the one `View` node the panel is showing, and every write that reaches them.
 *
 * Owned by the plugin instance rather than by the composition for three reasons: the attributes
 * survive the panel being closed and reopened, a write outlives the panel that started it — the
 * device applies it either way, so the outcome is worth keeping — and an agent's write goes through
 * here too, so the panel cannot be left showing a value the app no longer has.
 *
 * Attributes are fetched per node rather than carried by the capture — a tree of two hundred nodes
 * would otherwise haul thirty attributes each — so a selection change is a read.
 */
internal class ViewAttributeStore(
    /** Outlives the panel, so a write started there is still recorded after it closes. */
    private val scope: CoroutineScope,
    private val read: suspend (GetViewAttributes) -> ViewAttributeResponse,
    private val write: suspend (SetViewAttribute) -> ViewAttributeResult,
) {
    /** The node whose attributes are held, or `null` when the selection has none. */
    var node: NodeKey? by mutableStateOf(null)
        private set

    /** What the panel draws for [node]: one value, so the composition takes data rather than this store. */
    var state: ViewAttributesUiState by mutableStateOf(ViewAttributesUiState.Empty)
        private set

    // Writes run one at a time. Each is its own coroutine, so two edits made in quick succession —
    // a switch toggled twice, a field committed and then a dropdown picked, a user and an agent
    // writing at once — would otherwise race, and the row and the status line would settle on
    // whichever answer happened to arrive last rather than on the last edit made.
    private val writeLock = Mutex()

    /**
     * Holds the attributes of [rootId]/[nodeId], reading them if they are not already held.
     *
     * Selecting the node that is already held is nothing: the panel leaving and re-entering the
     * composition must not spend a round trip to the app re-reading what is already on screen.
     */
    fun select(rootId: String, nodeId: Int) {
        val key = NodeKey(rootId = rootId, nodeId = nodeId)
        if (key == node) return
        node = key
        state = ViewAttributesUiState.Empty
        scope.launch {
            val response = try {
                read(GetViewAttributes(rootId = rootId, nodeId = nodeId))
            } catch (e: JetWhaleMessagingException) {
                // A read that comes back after the selection moved on describes a node nobody is
                // looking at any more, so it is dropped rather than shown against the new one.
                if (node == key) {
                    state = state.copy(attributes = null, message = "The app did not answer: ${e.message}")
                }
                return@launch
            }
            if (node != key) return@launch
            state = state.copy(attributes = response.snapshot?.attributes, message = response.message)
        }
    }

    /** Holds nothing — the selection is a Compose node, or there is no selection. */
    fun clear() {
        node = null
        state = ViewAttributesUiState.Empty
    }

    /**
     * Writes [text] to [attribute] on the held node, and records what came back.
     *
     * Fire-and-forget for the panel's editors, which have no scope of their own to wait in; the
     * write itself runs on the plugin's scope, so navigating away mid-write still records the
     * outcome.
     */
    @OptIn(ExperimentalJetWhaleApi::class)
    fun commit(attribute: ViewAttribute, text: String) {
        val key = node ?: return
        scope.launch {
            writeLock.withLock {
                val value = try {
                    parseViewAttributeValue(attribute.id, attribute.value, text)
                } catch (e: JetWhaleMcpArgumentException) {
                    state = state.copy(writeStatus = e.message, writeFailed = true)
                    return@withLock
                }
                try {
                    apply(SetViewAttribute(rootId = key.rootId, nodeId = key.nodeId, attributeId = attribute.id, value = value))
                } catch (e: JetWhaleMessagingException) {
                    state = state.copy(writeStatus = "${attribute.id} failed: ${e.message}", writeFailed = true)
                }
            }
        }
    }

    /** Reads any node's attributes, for the MCP tool that asks about one the panel is not showing. */
    suspend fun readAttributes(request: GetViewAttributes): ViewAttributeResponse = read(request)

    /**
     * Writes one attribute of any node and returns what came back, for the MCP tool — which has a
     * caller waiting on the answer, and so cannot go through [commit].
     */
    suspend fun writeAttribute(request: SetViewAttribute): ViewAttributeResult = writeLock.withLock { apply(request) }

    /** The write itself, already serialised by [writeLock]. */
    private suspend fun apply(request: SetViewAttribute): ViewAttributeResult {
        val result = write(request)
        // Only the node the panel is holding: an agent may write to one the user is not looking at,
        // and the panel must go on showing the node it is showing.
        if (node == NodeKey(rootId = request.rootId, nodeId = request.nodeId)) {
            record(attributeId = request.attributeId, result = result)
        }
        return result
    }

    private fun record(attributeId: String, result: ViewAttributeResult) {
        // The row shows what came back, not what was asked for: an app may clamp a value or ignore
        // it, and the difference is exactly what the panel is for.
        val attributes = when (val written = result.attribute) {
            null -> state.attributes
            else -> state.attributes?.map { if (it.id == written.id) written else it }
        }
        state = state.copy(
            attributes = attributes,
            writeFailed = !result.applied,
            writeStatus = when {
                result.applied -> "$attributeId: ${result.attribute?.value?.asText() ?: "written"}"
                else -> "$attributeId: ${result.message ?: "not applied"}"
            },
        )
    }
}
