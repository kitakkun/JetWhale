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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource

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

    private var roundTripMs by mutableStateOf<Long?>(null)

    private val captureLock = Mutex()

    private val viewAttributes by lazy {
        ViewAttributeStore(
            scope = pluginScope,
            read = ::loadViewAttributes,
            write = ::setViewAttribute,
        )
    }

    private suspend fun loadViewAttributes(request: GetViewAttributes): ViewAttributeResponse = messenger.request(request)

    private suspend fun setViewAttribute(request: SetViewAttribute): ViewAttributeResult = messenger.request(request)

    private val highlightController by lazy {
        NodeHighlightController(
            scope = pluginScope,
            send = { request -> messenger.request(request) },
        )
    }

    override fun onDispose() {
        highlightController.clearAsync()
    }

    @Composable
    override fun Content() {
        ComposeSemanticsInspectorScreenRoot(
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
                        delay(ACTION_SETTLE_MS)
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
                when (val attributeKey = snapshot.viewAttributeNode(key)) {
                    null -> viewAttributes.clear()
                    else -> viewAttributes.select(rootId = attributeKey.rootId, nodeId = attributeKey.nodeId)
                }
            },
            onCommitViewAttribute = viewAttributes::commit,
            highlightStatus = highlightController.statusMessage,
            onHighlightTargetChange = highlightController::setTarget,
        )
    }

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

/** Comfortably more than one frame at 60 Hz, and still below what a user notices. */
private const val ACTION_SETTLE_MS = 50L
