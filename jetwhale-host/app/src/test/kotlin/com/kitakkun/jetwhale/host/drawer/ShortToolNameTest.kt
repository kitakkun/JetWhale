package com.kitakkun.jetwhale.host.drawer

import com.kitakkun.jetwhale.host.model.McpToolInvocation
import kotlinx.collections.immutable.persistentListOf
import kotlin.test.Test
import kotlin.test.assertEquals

class ShortToolNameTest {
    @Test
    fun `a plugin tool keeps the last segment of its plugin id`() {
        assertEquals(
            "mirror.tap",
            invocation("com.kitakkun.jetwhale.mirror.tap", pluginId = "com.kitakkun.jetwhale.mirror").shortToolName(),
        )
    }

    @Test
    fun `a host tool and a tool outside its plugin's namespace keep their names`() {
        assertEquals("jetwhale.click", invocation("jetwhale.click", pluginId = null).shortToolName())
        assertEquals("custom_tool", invocation("custom_tool", pluginId = "com.example.plugin").shortToolName())
    }

    private fun invocation(toolName: String, pluginId: String?) = McpToolInvocation(
        id = 1,
        toolName = toolName,
        pluginId = pluginId,
        sessionId = null,
        arguments = persistentListOf(),
    )
}
