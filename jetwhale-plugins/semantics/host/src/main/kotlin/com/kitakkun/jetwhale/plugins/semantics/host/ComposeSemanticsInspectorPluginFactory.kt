package com.kitakkun.jetwhale.plugins.semantics.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginUi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMessagingHostPlugin
import com.kitakkun.jetwhale.plugins.semantics.protocol.CaptureNodeTree
import com.kitakkun.jetwhale.plugins.semantics.protocol.GetViewAttributes
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeCaptureOptions
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeSnapshot
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction
import com.kitakkun.jetwhale.plugins.semantics.protocol.SetViewAttribute
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeResponse
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeResult
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessagingException
import com.kitakkun.jetwhale.protocol.messaging.request
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource

// Instantiated by the host via the fully-qualified name declared in plugin-manifest.json.
@Suppress("UNUSED")
class ComposeSemanticsInspectorPluginFactory : JetWhaleHostPluginFactory {
    override fun createPlugin(): JetWhaleHostPlugin = ComposeNodeInspectorHostPlugin()
}

@OptIn(ExperimentalJetWhaleApi::class)
private class ComposeNodeInspectorHostPlugin :
    JetWhaleMessagingHostPlugin(),
    JetWhaleHostPluginUi,
    JetWhaleMcpCapablePlugin {

    private var snapshot by mutableStateOf<NodeTreeSnapshot?>(null)
    private var capturing by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)
    private var actionStatus by mutableStateOf<String?>(null)

    // The snapshot reports what the capture cost on the device; this is what it cost from here,
    // transport included. Shown side by side because the point of reading the tree this way rather
    // than through `adb` is the latency, and the two numbers say where any of it went.
    private var roundTripMs by mutableStateOf<Long?>(null)

    // A capture reads the app's semantics on its main thread, so overlapping captures would queue
    // work there rather than finish sooner. Serialising them keeps auto-refresh and MCP calls from
    // compounding into UI jank in the app being debugged.
    private val captureLock = Mutex()

    private suspend fun capture(options: NodeTreeCaptureOptions): NodeTreeSnapshot = captureLock.withLock {
        capturing = true
        val startedAt = TimeSource.Monotonic.markNow()
        try {
            messenger.request(CaptureNodeTree(options)).also {
                snapshot = it
                roundTripMs = startedAt.elapsedNow().inWholeMilliseconds
                errorMessage = null
            }
        } finally {
            capturing = false
        }
    }

    private suspend fun performAction(request: PerformNodeAction): NodeActionResult = messenger.request(request)

    // Attributes are read per node, on demand, so they stay out of the capture and out of the
    // capture lock: a selection change must not queue behind an auto-refresh.
    private suspend fun loadViewAttributes(request: GetViewAttributes): ViewAttributeResponse = messenger.request(request)

    private suspend fun setViewAttribute(request: SetViewAttribute): ViewAttributeResult = messenger.request(request)

    // Owned by the plugin rather than by the panel: the attributes outlive the panel being closed,
    // a write outlives the editor that started it, and an agent's write goes through here too, so
    // an open panel is never left showing a value the app no longer has. Built lazily because
    // pluginScope is bound by the runtime after the instance is constructed.
    private val viewAttributes by lazy {
        ViewAttributeStore(
            scope = pluginScope,
            read = ::loadViewAttributes,
            write = ::setViewAttribute,
        )
    }

    @Composable
    override fun Content() {
        ComposeSemanticsInspectorScreen(
            snapshot = snapshot,
            capturing = capturing,
            roundTripMs = roundTripMs,
            errorMessage = errorMessage,
            actionStatus = actionStatus,
            viewAttributes = viewAttributes.state,
            onCapture = { options ->
                try {
                    capture(options)
                } catch (e: JetWhaleMessagingException) {
                    errorMessage = "Capture failed: ${e.message}"
                }
            },
            onPerformAction = { request ->
                pluginScope.launch {
                    actionStatus = try {
                        val result = performAction(request)
                        // Re-read straight away: an action changes the very tree the user is
                        // looking at, and a stale tree next to a "done" message reads as a failure.
                        capture(snapshot?.options ?: NodeTreeCaptureOptions())
                        when {
                            result.performed -> "${request.action} on #${request.nodeId}: done"
                            else -> "${request.action} on #${request.nodeId}: ${result.message ?: "not performed"}"
                        }
                    } catch (e: JetWhaleMessagingException) {
                        "${request.action} on #${request.nodeId} failed: ${e.message}"
                    }
                }
            },
            onSelectedNodeChange = { key ->
                // Only an Android View has attributes to read; anything else the tree lands on —
                // a Compose node, nothing at all — leaves the store holding nothing.
                when (val attributeKey = snapshot.viewAttributeNode(key)) {
                    null -> viewAttributes.clear()
                    else -> viewAttributes.select(rootId = attributeKey.rootId, nodeId = attributeKey.nodeId)
                }
            },
            onCommitViewAttribute = viewAttributes::commit,
        )
    }

    // Lazy for the same reason the store is: reading this list builds the store, and the store needs
    // pluginScope. The runtime asks for the commands well after it has bound one.
    override val mcpCommands: List<JetWhaleMcpCommand> by lazy {
        listOf(
            GetNodeTreeCommand(capture = ::capture),
            FindNodesCommand(capture = ::capture),
            NodeAtCommand(capture = ::capture),
            PerformNodeActionCommand(
                lastSnapshot = { snapshot },
                capture = ::capture,
                perform = ::performAction,
            ),
            GetViewAttributesCommand(getAttributes = viewAttributes::readAttributes),
            SetViewAttributeCommand(
                getAttributes = viewAttributes::readAttributes,
                setAttribute = viewAttributes::writeAttribute,
            ),
        )
    }
}
