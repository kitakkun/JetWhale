package com.kitakkun.jetwhale.plugins.semantics.host

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttribute
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeValue

@Preview
@Composable
private fun ViewAttributesPanelPreview() {
    JwTheme(darkTheme = false) {
        ViewAttributesPanel(
            state = ViewAttributesUiState(
                attributes = listOf(
                    ViewAttribute(
                        id = "visibility",
                        label = "visibility",
                        group = "State",
                        value = ViewAttributeValue.EnumValue(value = "VISIBLE", options = listOf("VISIBLE", "INVISIBLE", "GONE")),
                        editable = true,
                    ),
                    ViewAttribute(
                        id = "enabled",
                        label = "enabled",
                        group = "State",
                        value = ViewAttributeValue.BooleanValue(true),
                        editable = true,
                    ),
                    ViewAttribute(
                        id = "layout.width",
                        label = "layout.width",
                        group = "Layout",
                        value = ViewAttributeValue.LayoutSizeValue(
                            constant = "MATCH_PARENT",
                            px = null,
                            dp = null,
                            constants = listOf("MATCH_PARENT", "WRAP_CONTENT"),
                        ),
                        editable = true,
                    ),
                    ViewAttribute(
                        id = "backgroundColor",
                        label = "backgroundColor",
                        group = "Appearance",
                        value = ViewAttributeValue.ColorValue(argb = 0xFF2196F3.toInt()),
                        editable = true,
                    ),
                    ViewAttribute(
                        id = "text",
                        label = "text",
                        group = "Text",
                        value = ViewAttributeValue.TextValue("Send"),
                        editable = false,
                    ),
                ),
                message = null,
                writeStatus = "visibility: VISIBLE",
                writeFailed = false,
            ),
            onCommit = { _, _ -> },
        )
    }
}
