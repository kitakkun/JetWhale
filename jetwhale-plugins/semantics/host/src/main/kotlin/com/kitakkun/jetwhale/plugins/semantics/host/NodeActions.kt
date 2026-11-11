package com.kitakkun.jetwhale.plugins.semantics.host

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwSectionHeader
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwTextField
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeAction
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction
import com.kitakkun.jetwhale.plugins.semantics.protocol.UiNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.advertisedAs

/** What the app can be asked to do with [node]: a button for each action it exposes, and the input the text and index actions take. */
@Composable
internal fun NodeActions(
    rootId: String,
    node: UiNode,
    onPerformAction: (PerformNodeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var textInput by remember(rootId, node.id) { mutableStateOf(node.editableText ?: "") }
    var indexInput by remember(rootId, node.id) { mutableStateOf("") }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(JwSpacing.medium)) {
        JwSectionHeader(title = "Run an action on the app", contentPadding = PaddingValues(0.dp))
        NodeActionButtons(rootId = rootId, node = node, onPerformAction = onPerformAction)

        if (node.actions.contains("SetText")) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium)) {
                JwTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = "Text",
                    modifier = Modifier.weight(1f),
                )
                JwButton(
                    text = "Set text",
                    onClick = {
                        onPerformAction(PerformNodeAction(rootId = rootId, nodeId = node.id, action = NodeAction.SetText, text = textInput))
                    },
                    style = JwButtonStyle.Primary,
                )
            }
        }

        if (node.isScrollable) {
            Row(horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium)) {
                JwButton(
                    text = "Scroll down",
                    onClick = {
                        onPerformAction(PerformNodeAction(rootId = rootId, nodeId = node.id, action = NodeAction.ScrollBy, scrollY = SCROLL_STEP_PX))
                    },
                )
                JwButton(
                    text = "Scroll up",
                    onClick = {
                        onPerformAction(PerformNodeAction(rootId = rootId, nodeId = node.id, action = NodeAction.ScrollBy, scrollY = -SCROLL_STEP_PX))
                    },
                )
            }
        }

        if (node.actions.contains("ScrollToIndex")) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium)) {
                JwTextField(
                    value = indexInput,
                    onValueChange = { indexInput = it },
                    placeholder = "Item index",
                    modifier = Modifier.weight(1f),
                )
                JwButton(
                    text = "Scroll to index",
                    onClick = {
                        indexInput.toIntOrNull()?.let { index ->
                            onPerformAction(PerformNodeAction(rootId = rootId, nodeId = node.id, action = NodeAction.ScrollToIndex, index = index))
                        }
                    },
                    enabled = indexInput.toIntOrNull() != null,
                )
            }
        }
    }
}

private const val SCROLL_STEP_PX = 400f

@Composable
private fun NodeActionButtons(
    rootId: String,
    node: UiNode,
    onPerformAction: (PerformNodeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(JwSpacing.extraSmall),
    ) {
        ActionButton("Click", node, NodeAction.Click, rootId, onPerformAction)
        ActionButton("Long click", node, NodeAction.LongClick, rootId, onPerformAction)
        ActionButton("Focus", node, NodeAction.RequestFocus, rootId, onPerformAction)
        ActionButton("Dismiss", node, NodeAction.Dismiss, rootId, onPerformAction)
        ActionButton("Expand", node, NodeAction.Expand, rootId, onPerformAction)
        ActionButton("Collapse", node, NodeAction.Collapse, rootId, onPerformAction)
        // Offered even for a node whose reported bounds are empty: those are the clipped bounds,
        // and a node clipped away entirely is exactly the one this brings back.
        JwButton(
            text = "Bring into view",
            onClick = { onPerformAction(PerformNodeAction(rootId = rootId, nodeId = node.id, action = NodeAction.BringIntoView)) },
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    node: UiNode,
    action: NodeAction,
    rootId: String,
    onPerformAction: (PerformNodeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val exposed = action.advertisedAs?.let(node.actions::contains) ?: true
    if (!exposed) return
    JwButton(
        text = label,
        onClick = { onPerformAction(PerformNodeAction(rootId = rootId, nodeId = node.id, action = action)) },
        modifier = modifier,
        enabled = node.isEnabled,
    )
}
