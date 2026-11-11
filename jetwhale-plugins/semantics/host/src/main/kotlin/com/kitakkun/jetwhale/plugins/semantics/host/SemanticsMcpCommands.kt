package com.kitakkun.jetwhale.plugins.semantics.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.semantics.protocol.GetViewAttributes
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeActionResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeCaptureOptions
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeSnapshot
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction
import com.kitakkun.jetwhale.plugins.semantics.protocol.SetViewAttribute
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeResponse
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeResult

/**
 * Each of the inspector's MCP commands, built once, so the UI can ring the pane or controls that show or do
 * what a running command reads or does.
 */
@OptIn(ExperimentalJetWhaleApi::class)
internal class SemanticsMcpCommands(
    capture: suspend (NodeTreeCaptureOptions) -> NodeTreeSnapshot,
    lastSnapshot: () -> NodeTreeSnapshot?,
    perform: suspend (PerformNodeAction) -> NodeActionResult,
    getAttributes: suspend (GetViewAttributes) -> ViewAttributeResponse,
    setAttribute: suspend (SetViewAttribute) -> ViewAttributeResult,
) {
    val getNodeTree = GetNodeTreeCommand(capture = capture)
    val findNodes = FindNodesCommand(capture = capture)
    val nodeAt = NodeAtCommand(capture = capture)
    val performNodeAction = PerformNodeActionCommand(lastSnapshot = lastSnapshot, capture = capture, perform = perform)
    val getViewAttributes = GetViewAttributesCommand(getAttributes = getAttributes)
    val setViewAttribute = SetViewAttributeCommand(getAttributes = getAttributes, setAttribute = setAttribute)

    val all: List<JetWhaleMcpCommand> = listOf(getNodeTree, findNodes, nodeAt, performNodeAction, getViewAttributes, setViewAttribute)
}
