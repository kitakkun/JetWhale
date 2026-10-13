package com.kitakkun.jetwhale.plugins.semantics.host

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwCheckbox
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwHorizontalDivider
import com.kitakkun.jetwhale.host.ui.JwKeyValueRow
import com.kitakkun.jetwhale.host.ui.JwProgressIndicator
import com.kitakkun.jetwhale.host.ui.JwSearchField
import com.kitakkun.jetwhale.host.ui.JwSectionHeader
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwSplitPane
import com.kitakkun.jetwhale.host.ui.JwStatusLine
import com.kitakkun.jetwhale.host.ui.JwTag
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTextField
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.host.ui.JwTreeRow
import com.kitakkun.jetwhale.host.ui.LocalJwContentColor
import com.kitakkun.jetwhale.host.ui.rememberJwSplitPaneState
import com.kitakkun.jetwhale.plugins.semantics.protocol.AppleNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeAction
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeSnapshot
import com.kitakkun.jetwhale.plugins.semantics.protocol.PerformNodeAction
import com.kitakkun.jetwhale.plugins.semantics.protocol.UiNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttribute
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.advertisedAs
import kotlinx.coroutines.launch
import java.awt.datatransfer.StringSelection
import kotlin.math.roundToInt

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun NodeDetail(
    rootId: String?,
    node: UiNode?,
    viewAttributes: ViewAttributesUiState,
    onPerformAction: (PerformNodeAction) -> Unit,
    onCommitViewAttribute: (ViewAttribute, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (node == null || rootId == null) {
        JwEmptyState(title = "Select a node to see its semantics and the actions it exposes.")
        return
    }

    val scope = rememberCoroutineScope()
    var textInput by remember(rootId, node.id) { mutableStateOf(node.editableText ?: "") }
    var indexInput by remember(rootId, node.id) { mutableStateOf("") }

    val clipboard = LocalClipboard.current
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(JwSpacing.large),
        verticalArrangement = Arrangement.spacedBy(JwSpacing.medium),
    ) {
        JwText(
            text = node.displayLabel(),
            style = JwTheme.textStyles.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        NodeProperties(rootId = rootId, node = node)

        JwHorizontalDivider()

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
            ScrollStepButtons(rootId = rootId, node = node, onPerformAction = onPerformAction)
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
                            onPerformAction(
                                PerformNodeAction(rootId = rootId, nodeId = node.id, action = NodeAction.ScrollToIndex, index = index),
                            )
                        }
                    },
                    enabled = indexInput.toIntOrNull() != null,
                )
            }
        }

        // idb's `ui tap` takes the same points the node reports, so no conversion is needed.
        val tapTool = if (node is AppleNode) "idb ui tap" else "adb shell input tap"
        JwButton(
            text = "Copy `$tapTool` for these bounds",
            onClick = {
                val command = "$tapTool ${node.boundsInScreen.centerX.roundToInt()} ${node.boundsInScreen.centerY.roundToInt()}"
                scope.launch { clipboard.setClipEntry(ClipEntry(StringSelection(command))) }
            },
            enabled = !node.boundsInScreen.isEmpty,
            style = JwButtonStyle.Text,
        )

        if (node is ViewNode) {
            JwHorizontalDivider()
            ViewAttributesPanel(state = viewAttributes, onCommit = onCommitViewAttribute)
        }
    }
}

private const val SCROLL_STEP_PX = 400f

@Composable
private fun NodeProperties(rootId: String, node: UiNode) {
    Column {
        PropertyRow("id", node.id.toString())
        PropertyRow("rootId", rootId)
        when (node) {
            is ViewNode -> {
                PropertyRow("viewClass", node.viewClass)
                node.resourceId?.let { PropertyRow("resourceId", "@id/$it") }
            }

            is ComposeNode -> {
                node.role?.let { PropertyRow("role", it) }
                node.testTag?.let { PropertyRow("testTag", it) }
                node.stateDescription?.let { PropertyRow("stateDescription", it, wrap = true) }
            }

            is AppleNode -> {
                PropertyRow("className", node.className)
                node.accessibilityIdentifier?.let { PropertyRow("accessibilityIdentifier", it) }
                node.accessibilityValue?.let { PropertyRow("accessibilityValue", it, wrap = true) }
                if (node.traits.isNotEmpty()) PropertyRow("traits", node.traits.joinToString(", "), wrap = true)
            }
        }
        node.text?.let { PropertyRow("text", it, wrap = true) }
        node.editableText?.let { PropertyRow("editableText", it, wrap = true) }
        node.contentDescription?.let { PropertyRow("contentDescription", it, wrap = true) }
        node.toggleableState?.let { PropertyRow("toggleableState", it) }
        PropertyRow("bounds (root)", node.bounds.formatted())
        PropertyRow("bounds (screen)", node.boundsInScreen.formatted())
        PropertyRow(
            "flags",
            buildList {
                if (!node.isEnabled) add("disabled")
                if (!node.isVisible) add("invisible")
                if (node.isFocused) add("focused")
                if (node.isSelected) add("selected")
                if (node.isClickable) add("clickable")
                if (node.isEditable) add("editable")
                if (node.isScrollable) add("scrollable")
            }.joinToString(", ").ifEmpty { "—" },
            wrap = true,
        )
        PropertyRow("actions", node.actions.joinToString(", ").ifEmpty { "—" }, wrap = true)
        if (node.isInteractive && !node.isOperable) {
            PropertyRow("operable", "no — ${node.whyNotOperable()}", wrap = true)
        }
    }
}

@Composable
private fun ScrollStepButtons(
    rootId: String,
    node: UiNode,
    onPerformAction: (PerformNodeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium)) {
        JwButton(
            text = "Scroll down",
            onClick = {
                onPerformAction(
                    PerformNodeAction(rootId = rootId, nodeId = node.id, action = NodeAction.ScrollBy, scrollY = SCROLL_STEP_PX),
                )
            },
        )
        JwButton(
            text = "Scroll up",
            onClick = {
                onPerformAction(
                    PerformNodeAction(rootId = rootId, nodeId = node.id, action = NodeAction.ScrollBy, scrollY = -SCROLL_STEP_PX),
                )
            },
        )
    }
}

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

/** Fits "contentDescription", the longest property name. */
private val PropertyKeyWidth = 120.dp

/**
 * @param wrap `true` for values worth reading in full at a glance — the action list, the app's own
 *   strings. The rest stay on one scrollable line so the columns line up and a long id cannot push
 *   the properties below it off the pane.
 */
@Composable
private fun PropertyRow(label: String, value: String, wrap: Boolean = false) {
    JwKeyValueRow(key = label, value = value, keyWidth = PropertyKeyWidth, monospace = true, wrap = wrap)
}

private fun UiNode.whyNotOperable(): String {
    if (!isEnabled) return "disabled"
    return obscuredBy?.let { "#${it.nodeId} takes the touch (${it.rootId})" } ?: "nothing to aim at"
}
