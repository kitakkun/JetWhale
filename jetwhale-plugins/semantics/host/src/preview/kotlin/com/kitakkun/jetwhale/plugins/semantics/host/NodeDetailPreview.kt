package com.kitakkun.jetwhale.plugins.semantics.host

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeBounds

@Preview
@Composable
private fun NodeDetailPreview() {
    JwTheme(darkTheme = false) {
        NodeDetail(
            rootId = "MainActivity",
            node = ComposeNode(
                id = 3,
                role = "List",
                testTag = "messages",
                bounds = NodeBounds(left = 0f, top = 240f, right = 1080f, bottom = 2100f),
                boundsInScreen = NodeBounds(left = 0f, top = 240f, right = 1080f, bottom = 2100f),
                actions = listOf("ScrollBy", "ScrollToIndex"),
                isScrollable = true,
            ),
            viewAttributes = ViewAttributesUiState.Empty,
            onPerformAction = {},
            onCommitViewAttribute = { _, _ -> },
        )
    }
}
