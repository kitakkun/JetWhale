package com.kitakkun.jetwhale.plugins.semantics.host

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeBounds

@Preview
@Composable
private fun NodeActionsPreview() {
    JwTheme(darkTheme = false) {
        NodeActions(
            rootId = "MainActivity",
            node = ComposeNode(
                id = 3,
                role = "TextField",
                editableText = "milk",
                bounds = NodeBounds(left = 40f, top = 120f, right = 1040f, bottom = 240f),
                boundsInScreen = NodeBounds(left = 40f, top = 120f, right = 1040f, bottom = 240f),
                actions = listOf("OnClick", "RequestFocus", "SetText"),
                isClickable = true,
                isEditable = true,
            ),
            onPerformAction = {},
        )
    }
}
