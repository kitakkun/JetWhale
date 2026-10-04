package com.kitakkun.jetwhale.plugins.deeplinks.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeclaredDeepLink
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeepLinkTemplate
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalJetWhaleApi::class)
class DeepLinksMcpCommandsTest {
    private val demoLink = DeclaredDeepLink(
        handler = "com.example.app.MainActivity",
        schemes = listOf("demo"),
        hosts = emptyList(),
        paths = emptyList(),
        browsable = true,
        autoVerify = false,
    )
    private val client = FakeDeepLinkClient(
        declared = listOf(demoLink),
        templates = listOf(DeepLinkTemplate(name = "Item", template = "demo://item/{id}", description = null)),
    )

    @Test
    fun `listDeepLinks gives every declaration a sample link`() {
        val result = ListDeepLinksCommand(client).run()

        assertEquals("demo://example", result.getValue("declared").jsonArray.single().jsonObject.getValue("sampleUrl").jsonPrimitive.content)
        assertEquals("Item", result.getValue("templates").jsonArray.single().jsonObject.getValue("name").jsonPrimitive.content)
    }

    @Test
    fun `openDeepLink reports what opened it and which declarations match`() {
        val result = OpenDeepLinkCommand(client).run(buildJsonObject { put("url", "demo://item/7") })

        assertEquals("true", result.getValue("opened").jsonPrimitive.content)
        assertEquals(listOf("com.example.app.MainActivity"), result.getValue("matchesDeclared").jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("demo://item/7"), client.opened)
    }

    @Test
    fun `openDeepLink still asks the app about a link no declaration covers`() {
        val result = OpenDeepLinkCommand(client).run(buildJsonObject { put("url", "not a url") })

        assertEquals("false", result.getValue("opened").jsonPrimitive.content)
        assertEquals(listOf("not a url"), client.opened)
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
private fun JetWhaleMcpCommand.run(arguments: JsonObject = buildJsonObject { }): JsonObject = runBlocking {
    Json.parseToJsonElement(execute(JetWhaleMcpArguments(arguments))).jsonObject
}
