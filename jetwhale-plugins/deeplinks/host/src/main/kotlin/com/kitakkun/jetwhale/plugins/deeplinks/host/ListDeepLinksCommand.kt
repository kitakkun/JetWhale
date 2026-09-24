package com.kitakkun.jetwhale.plugins.deeplinks.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DEEP_LINKS_PLUGIN_ID
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeclaredDeepLink
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeepLinkTemplate
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

@OptIn(ExperimentalJetWhaleApi::class)
internal class ListDeepLinksCommand(
    private val client: DeepLinkClient,
) : JetWhaleMcpCommand() {
    override val name = "$DEEP_LINKS_PLUGIN_ID.listDeepLinks"
    override val description =
        "Lists the deep links the app handles: each declaration's handler (Android activity or iOS URL type), schemes, hosts (with App Links verification where Android reports it), path matchers, whether a browser may follow it, and a sampleUrl to start from; example links the app registered as templates with {name} placeholders; and notes on what the platform could not list, such as iOS universal links."

    override suspend fun execute(arguments: JetWhaleMcpArguments): String {
        val catalog = client.catalog()
        return buildJsonObject {
            putJsonArray("declared") {
                catalog.declared.forEach { link ->
                    val fields = Json.encodeToJsonElement(DeclaredDeepLink.serializer(), link).jsonObject
                    add(JsonObject(fields + ("sampleUrl" to JsonPrimitive(sampleUrlOf(link)))))
                }
            }
            put("templates", Json.encodeToJsonElement(ListSerializer(DeepLinkTemplate.serializer()), catalog.templates))
            put("canOpen", catalog.canOpen)
            put("notes", Json.encodeToJsonElement(ListSerializer(String.serializer()), catalog.notes))
        }.toString()
    }
}
