package com.kitakkun.jetwhale.plugins.nav3.host

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavBackStackSnapshot
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavKeyFieldDescriptor
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavKeySnapshot
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavKeyTypeDescriptor
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Preview
@Composable
private fun Nav3NavigatorScreenPreview() {
    JwTheme(darkTheme = false) {
        Nav3NavigatorScreen(
            stacks = listOf(
                NavBackStackSnapshot(
                    stackId = "main",
                    entries = listOf(
                        NavKeySnapshot(typeName = "Home", display = "Home", key = buildJsonObject { put("type", "Home") }),
                        NavKeySnapshot(
                            typeName = "Detail",
                            display = "Detail(id=42)",
                            key = buildJsonObject {
                                put("type", "Detail")
                                put("id", "42")
                            },
                        ),
                    ),
                ),
            ),
            keyTypes = listOf(
                NavKeyTypeDescriptor(
                    serialName = "Detail",
                    fields = listOf(NavKeyFieldDescriptor(name = "id", type = "String", optional = false, nullable = false)),
                    template = buildJsonObject {
                        put("type", "Detail")
                        put("id", "")
                    },
                ),
            ),
            selectedStackId = "main",
            status = Nav3Status(message = "Pushed Detail(id=42)", isError = false),
            draft = """{"type": "Detail", "id": "42"}""",
            onSelectStack = {},
            onApplyOperation = { _, _ -> },
            onRefresh = {},
            onDraftChange = {},
        )
    }
}
