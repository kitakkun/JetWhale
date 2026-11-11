package com.kitakkun.jetwhale.plugins.semantics.host

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeRoot
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeBounds
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeCaptureOptions
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeSnapshot

@Preview
@Composable
private fun ComposeSemanticsInspectorScreenPreview() {
    JwTheme(darkTheme = false) {
        ComposeSemanticsInspectorScreen(
            snapshot = NodeTreeSnapshot(
                capturedAtMs = 0L,
                captureDurationMs = 12L,
                options = NodeTreeCaptureOptions(),
                roots = listOf(
                    ComposeRoot(
                        rootId = PREVIEW_ROOT_ID,
                        label = PREVIEW_ROOT_ID,
                        density = 2.75f,
                        windowOffsetX = 0f,
                        windowOffsetY = 0f,
                        node = ComposeNode(
                            id = 1,
                            bounds = NodeBounds(left = 0f, top = 0f, right = 1080f, bottom = 2100f),
                            boundsInScreen = NodeBounds(left = 0f, top = 0f, right = 1080f, bottom = 2100f),
                            children = listOf(
                                ComposeNode(
                                    id = PREVIEW_SELECTED_NODE_ID,
                                    role = "Button",
                                    text = "Send",
                                    testTag = "send",
                                    bounds = NodeBounds(left = 40f, top = 120f, right = 320f, bottom = 240f),
                                    boundsInScreen = NodeBounds(left = 40f, top = 120f, right = 320f, bottom = 240f),
                                    actions = listOf("OnClick"),
                                    isClickable = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            capturing = false,
            roundTripMs = 34L,
            errorMessage = null,
            actionStatus = null,
            highlightStatus = null,
            viewAttributes = ViewAttributesUiState.Empty,
            flags = SemanticsInspectorFlags(
                merged = true,
                interactiveOnly = false,
                includeInvisible = false,
                autoRefresh = false,
                highlightOnDevice = false,
            ),
            selectedKey = NodeKey(rootId = PREVIEW_ROOT_ID, nodeId = PREVIEW_SELECTED_NODE_ID),
            onRefresh = {},
            onFlagsChange = {},
            onSelectKey = {},
            onHoverChange = { _, _ -> },
            onPerformAction = {},
            onCommitViewAttribute = { _, _ -> },
        )
    }
}

private const val PREVIEW_ROOT_ID = "MainActivity"

/** The clickable node, so the preview also shows what the detail pane makes of one. */
private const val PREVIEW_SELECTED_NODE_ID = 2
