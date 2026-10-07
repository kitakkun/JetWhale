package com.kitakkun.jetwhale.plugins.nav3.host

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavBackStackSnapshot
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavKeyFieldDescriptor
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavKeySnapshot
import com.kitakkun.jetwhale.plugins.nav3.protocol.NavKeyTypeDescriptor
import com.kitakkun.jetwhale.tools.docsscreenshots.DocsShot
import com.kitakkun.jetwhale.tools.docsscreenshots.DocsShotRecorder
import com.kitakkun.jetwhale.tools.docsscreenshots.InMemoryPluginStorage
import com.kitakkun.jetwhale.tools.docsscreenshots.PluginSceneSurface
import com.kitakkun.jetwhale.tools.docsscreenshots.onSurface
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class Nav3NavigatorDocsScreenshots {
    private val recorder = DocsShotRecorder.forImagesDirectoryProperty()

    @Test
    fun `the back stack beside a filtered list of key types`() = recorder.record(
        DocsShot(page = "nav3-navigator", name = "back-stack", surfaceSize = DpSize(860.dp, 440.dp), density = 1.6f, displayWidth = 688),
    ) { darkTheme ->
        setContent {
            PluginSceneSurface(darkTheme = darkTheme, storage = InMemoryPluginStorage(mapOf("push-draft" to JsonPrimitive(DRAFT).toString()))) {
                Nav3NavigatorScreenRoot(
                    stacks = listOf(BACK_STACK),
                    keyTypes = KEY_TYPES,
                    selectedStackId = BACK_STACK.stackId,
                    status = null,
                    onSelectStack = {},
                    onApplyOperation = { _, _ -> },
                    onRefresh = {},
                )
            }
        }
        onAllNodes(hasSetTextAction())[KEY_TYPE_FILTER_INDEX].performTextInput("Product")
        onSurface()
    }
}

/** The editor comes first in the push pane, then the filter above the key types. */
private const val KEY_TYPE_FILTER_INDEX = 1

private const val DRAFT = "{\n    \"type\": \"ProductDetail\",\n    \"id\": \"42\"\n}"

private val BACK_STACK = NavBackStackSnapshot(
    stackId = "main",
    entries = listOf(
        NavKeySnapshot(typeName = "Home", display = "Home", key = buildJsonObject { put("type", "Home") }),
        NavKeySnapshot(
            typeName = "ProductList",
            display = "ProductList(category=mugs)",
            key = buildJsonObject {
                put("type", "ProductList")
                put("category", "mugs")
            },
        ),
        NavKeySnapshot(
            typeName = "ProductDetail",
            display = "ProductDetail(id=41)",
            key = buildJsonObject {
                put("type", "ProductDetail")
                put("id", "41")
            },
        ),
    ),
)

private val KEY_TYPES = listOf(
    keyType(serialName = "Cart", fields = emptyList()),
    keyType(serialName = "Home", fields = emptyList()),
    keyType(serialName = "ProductDetail", fields = listOf(NavKeyFieldDescriptor(name = "id", type = "String", optional = false, nullable = false))),
    keyType(serialName = "ProductList", fields = listOf(NavKeyFieldDescriptor(name = "category", type = "String", optional = false, nullable = false))),
    keyType(serialName = "Settings", fields = listOf(NavKeyFieldDescriptor(name = "section", type = "String", optional = true, nullable = true))),
)

/** A key type with the template the agent derives: the type name, and an empty value per field. */
private fun keyType(serialName: String, fields: List<NavKeyFieldDescriptor>) = NavKeyTypeDescriptor(
    serialName = serialName,
    fields = fields,
    template = buildJsonObject {
        put("type", serialName)
        fields.forEach { put(it.name, "") }
    },
)
